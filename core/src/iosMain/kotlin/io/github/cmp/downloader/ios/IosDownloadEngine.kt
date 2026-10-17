package io.github.cmp.downloader.ios

import io.github.cmp.downloader.core.engine.EngineProbeResult
import io.github.cmp.downloader.core.engine.HttpProbeClient
import io.github.cmp.downloader.core.engine.IDownloadEngine
import io.github.cmp.downloader.core.engine.ProgressThrottler
import io.github.cmp.downloader.core.engine.SpeedCalculator
import io.github.cmp.downloader.core.model.ChecksumAlgorithm
import io.github.cmp.downloader.core.model.CollisionStrategy
import io.github.cmp.downloader.core.model.DownloadProgress
import io.github.cmp.downloader.core.model.DownloadStatus
import io.github.cmp.downloader.core.model.DownloadTask
import io.github.cmp.downloader.core.repository.DownloadTaskRepository
import io.github.cmp.downloader.core.storage.DiskSpaceValidator
import io.github.cmp.downloader.core.storage.IChecksumValidator
import io.github.cmp.downloader.core.storage.IFileSystem
import io.github.cmp.downloader.core.storage.createChecksumValidator
import io.github.cmp.downloader.core.storage.createDefaultFileSystem
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDownloadTask
import platform.Foundation.setValue

/**
 * iOS-native download engine utilizing [NSURLSession] with background configuration
 * to run transfers out-of-process via Apple's `nsurlsessiond` daemon.
 */
@OptIn(ExperimentalForeignApi::class)
class IosDownloadEngine(
    private val sessionIdentifier: String = "io.github.cmp.downloader.background",
    private val repository: DownloadTaskRepository? = null,
    private val fileSystem: IFileSystem = createDefaultFileSystem(),
    private val probeClient: HttpProbeClient = HttpProbeClient(),
    private val diskSpaceValidator: DiskSpaceValidator = DiskSpaceValidator(fileSystem),
    private val checksumValidator: IChecksumValidator = createChecksumValidator()
) : IDownloadEngine {

    private val session: NSURLSession
    private val activeTasks = mutableMapOf<String, NSURLSessionDownloadTask>()
    private val resumeDataMap = mutableMapOf<String, NSData>()
    private val completionHandlers = mutableMapOf<String, () -> Unit>()
    private val progressListeners = mutableMapOf<String, (DownloadProgress) -> Unit>()
    private val taskMetadataMap = mutableMapOf<String, DownloadTask>()
    private val speedCalculators = mutableMapOf<String, SpeedCalculator>()
    private val throttlers = mutableMapOf<String, ProgressThrottler>()
    private val mutex = Mutex()

    init {
        val config = NSURLSessionConfiguration.backgroundSessionConfigurationWithIdentifier(sessionIdentifier).apply {
            discretionary = false
            sessionSendsLaunchEvents = true
            waitsForConnectivity = true
        }

        val delegate = IosBackgroundSessionDelegate(
            onProgressUpdate = { taskId, bytesDownloaded, totalBytes ->
                handleProgressUpdate(taskId, bytesDownloaded, totalBytes)
            },
            onDownloadFinished = { taskId, tempFileUrl ->
                handleDownloadFinished(taskId, tempFileUrl)
            },
            onDownloadError = { taskId, errorMessage ->
                handleDownloadError(taskId, errorMessage)
            },
            onAllBackgroundEventsFinished = { identifier ->
                handleBackgroundEventsFinished(identifier)
            }
        )

        session = NSURLSession.sessionWithConfiguration(
            configuration = config,
            delegate = delegate,
            delegateQueue = NSOperationQueue.mainQueue
        )
    }

    override suspend fun probe(task: DownloadTask): Result<EngineProbeResult> {
        return probeClient.probe(task)
    }

    override fun start(task: DownloadTask): Flow<DownloadProgress> = callbackFlow {
        mutex.withLock {
            taskMetadataMap[task.id] = task
            speedCalculators[task.id] = SpeedCalculator()
            throttlers[task.id] = ProgressThrottler()
            progressListeners[task.id] = { progress ->
                trySend(progress)
            }
        }

        // Emit initial Connecting
        trySend(task.copy(status = DownloadStatus.CONNECTING).toProgress())

        val resumeData = mutex.withLock { resumeDataMap.remove(task.id) }
        val downloadTask: NSURLSessionDownloadTask = if (resumeData != null) {
            session.downloadTaskWithResumeData(resumeData)
        } else {
            val url = NSURL.URLWithString(task.request.url)
                ?: throw IllegalArgumentException("Invalid URL: ${task.request.url}")
            val request = NSMutableURLRequest.requestWithURL(url)
            task.request.headers.forEach { (key, value) ->
                request.setValue(value, forHTTPHeaderField = key)
            }
            session.downloadTaskWithRequest(request)
        }

        downloadTask.taskDescription = task.id
        mutex.withLock {
            activeTasks[task.id] = downloadTask
        }

        downloadTask.resume()

        awaitClose {
            // Flow cleanup
            progressListeners.remove(task.id)
        }
    }

    override suspend fun pause(taskId: String): Result<Unit> = runCatching {
        mutex.withLock {
            val task = activeTasks.remove(taskId) ?: return@runCatching
            task.cancelByProducingResumeData { data ->
                if (data != null) {
                    resumeDataMap[taskId] = data
                }
            }
            val meta = taskMetadataMap[taskId]
            if (meta != null) {
                progressListeners[taskId]?.invoke(meta.copy(status = DownloadStatus.PAUSED).toProgress())
            }
        }
    }

    override fun resume(task: DownloadTask): Flow<DownloadProgress> {
        return start(task)
    }

    override suspend fun cancel(taskId: String): Result<Unit> = runCatching {
        mutex.withLock {
            val task = activeTasks.remove(taskId)
            task?.cancel()
            resumeDataMap.remove(taskId)
            val meta = taskMetadataMap.remove(taskId)
            if (meta != null) {
                fileSystem.delete(meta.destinationPath)
                fileSystem.delete(meta.tempPath)
                progressListeners[taskId]?.invoke(meta.copy(status = DownloadStatus.CANCELLED).toProgress())
            }
            progressListeners.remove(taskId)
        }
    }

    /**
     * Called by iOS AppDelegate in `application(_:handleEventsForBackgroundURLSession:completionHandler:)`.
     */
    fun setBackgroundCompletionHandler(identifier: String, handler: () -> Unit) {
        completionHandlers[identifier] = handler
    }

    private fun handleProgressUpdate(taskId: String, bytesDownloaded: Long, totalBytes: Long) {
        val meta = taskMetadataMap[taskId] ?: return
        val speedCalc = speedCalculators.getOrPut(taskId) { SpeedCalculator() }
        val throttler = throttlers.getOrPut(taskId) { ProgressThrottler() }
        val now = currentTimeEpochMs()

        val (speed, eta) = speedCalc.update(bytesDownloaded, totalBytes, now)
        val progress = meta.toProgress(speed, eta).copy(
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes,
            status = DownloadStatus.RUNNING
        )

        if (throttler.shouldEmit(progress, now)) {
            progressListeners[taskId]?.invoke(progress)
        }
    }

    private fun handleDownloadFinished(taskId: String, tempFileUrl: NSURL) {
        val meta = taskMetadataMap[taskId] ?: return
        val sourcePath = tempFileUrl.path ?: return

        try {
            // Checksum verification
            if (meta.request.expectedChecksum != null && meta.request.checksumAlgorithm != ChecksumAlgorithm.NONE) {
                checksumValidator.validate(
                    filePath = sourcePath,
                    expectedChecksum = meta.request.expectedChecksum,
                    algorithm = meta.request.checksumAlgorithm
                )
            }

            // Move to final destination
            fileSystem.createDirectories(meta.destinationPath)
            if (fileSystem.exists(meta.destinationPath)) {
                if (meta.request.collisionStrategy == CollisionStrategy.OVERWRITE) {
                    fileSystem.delete(meta.destinationPath)
                }
            }
            fileSystem.atomicMove(sourcePath, meta.destinationPath)

            val completedProgress = meta.copy(
                status = DownloadStatus.COMPLETED,
                downloadedBytes = meta.totalBytes
            ).toProgress()

            progressListeners[taskId]?.invoke(completedProgress)
        } catch (e: Exception) {
            handleDownloadError(taskId, e.message)
        } finally {
            activeTasks.remove(taskId)
            resumeDataMap.remove(taskId)
        }
    }

    private fun handleDownloadError(taskId: String, errorMessage: String?) {
        val meta = taskMetadataMap[taskId] ?: return
        val failedProgress = meta.copy(
            status = DownloadStatus.FAILED,
            errorMessage = errorMessage
        ).toProgress()

        progressListeners[taskId]?.invoke(failedProgress)
        activeTasks.remove(taskId)
    }

    private fun handleBackgroundEventsFinished(identifier: String) {
        val handler = completionHandlers.remove(identifier)
        handler?.invoke()
    }

    private fun currentTimeEpochMs(): Long {
        return io.github.cmp.downloader.core.client.currentTimeEpochMs()
    }
}
