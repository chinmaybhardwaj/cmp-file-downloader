package io.github.cmp.downloader.core.engine

import io.github.cmp.downloader.core.model.ChecksumAlgorithm
import io.github.cmp.downloader.core.model.CollisionStrategy
import io.github.cmp.downloader.core.model.DownloadChunk
import io.github.cmp.downloader.core.model.DownloadProgress
import io.github.cmp.downloader.core.model.DownloadStatus
import io.github.cmp.downloader.core.model.DownloadTask
import io.github.cmp.downloader.core.repository.DownloadTaskRepository
import io.github.cmp.downloader.core.storage.DiskSpaceValidator
import io.github.cmp.downloader.core.storage.IChecksumValidator
import io.github.cmp.downloader.core.storage.IFileSystem
import io.github.cmp.downloader.core.storage.IRandomAccessFileWriter
import io.github.cmp.downloader.core.storage.createChecksumValidator
import io.github.cmp.downloader.core.storage.createDefaultFileSystem
import io.github.cmp.downloader.core.storage.createRandomAccessFileWriter
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Production-ready segmented parallel download engine with random-access file I/O,
 * direct disk streaming (64KB buffer), pre-flight storage verification, and atomic staging.
 */
class SegmentedDownloadEngine(
    private val httpClient: HttpClient = HttpClient(),
    private val probeClient: HttpProbeClient = HttpProbeClient(httpClient),
    private val fileSystem: IFileSystem = createDefaultFileSystem(),
    private val checksumValidator: IChecksumValidator = createChecksumValidator(),
    private val repository: DownloadTaskRepository? = null,
    private val diskSpaceValidator: DiskSpaceValidator = DiskSpaceValidator(fileSystem)
) : IDownloadEngine {

    private val activeDownloadJobs = mutableMapOf<String, Job>()
    private val cancellationMutex = Mutex()

    override suspend fun probe(task: DownloadTask): Result<EngineProbeResult> {
        return probeClient.probe(task)
    }

    override fun start(task: DownloadTask): Flow<DownloadProgress> = flow {
        val speedCalculator = SpeedCalculator()
        val throttler = ProgressThrottler()

        emit(task.copy(status = DownloadStatus.CONNECTING).toProgress())

        try {
            // Step 1: Probe remote URL if not yet probed
            var currentTask = task
            if (currentTask.totalBytes <= 0L) {
                val probeResult = probe(currentTask).getOrThrow()
                currentTask = currentTask.copy(
                    totalBytes = probeResult.contentLength,
                    supportsRanges = probeResult.supportsByteRanges,
                    eTag = probeResult.eTag
                )
                repository?.updateTask(currentTask)
            }

            // Step 2: Pre-flight disk storage check
            if (currentTask.totalBytes > 0L) {
                diskSpaceValidator.validateStorage(currentTask.destinationPath, currentTask.totalBytes)
            }

            // Step 3: File collision handling
            val finalDestination = resolveDestinationCollision(currentTask)
            currentTask = currentTask.copy(destinationPath = finalDestination)

            // Step 4: Retrieve existing or partition new chunks
            var chunks = repository?.getChunksForTask(currentTask.id) ?: emptyList()
            if (chunks.isEmpty()) {
                chunks = ChunkPartitioner.partition(
                    taskId = currentTask.id,
                    totalBytes = currentTask.totalBytes,
                    maxSegments = currentTask.request.maxSegments,
                    supportsByteRanges = currentTask.supportsRanges
                )
                repository?.saveChunks(chunks)
            }
            currentTask = currentTask.copy(chunks = chunks)

            // Step 5: Pre-allocate space on disk for parallel chunk alignment
            if (currentTask.totalBytes > 0L) {
                fileSystem.preAllocate(currentTask.tempPath, currentTask.totalBytes)
            }

            // Step 6: Stream chunks concurrently
            val writer = createRandomAccessFileWriter(currentTask.tempPath)
            val progressMutex = Mutex()
            var currentDownloadedBytes = chunks.sumOf { it.downloadedBytes }

            emit(currentTask.copy(status = DownloadStatus.RUNNING, downloadedBytes = currentDownloadedBytes).toProgress())

            try {
                coroutineScope {
                    val deferredList = chunks.filter { !it.isCompleted }.map { chunk ->
                        async {
                            downloadChunkWithRetry(
                                task = currentTask,
                                chunk = chunk,
                                writer = writer
                            ) { bytesReceived ->
                                progressMutex.withLock {
                                    currentDownloadedBytes += bytesReceived
                                    val now = currentTimeEpochMs()
                                    val (speed, eta) = speedCalculator.update(
                                        currentBytes = currentDownloadedBytes,
                                        totalBytes = currentTask.totalBytes,
                                        currentTimeMs = now
                                    )
                                    val progress = currentTask.toProgress(speed, eta).copy(
                                        bytesDownloaded = currentDownloadedBytes,
                                        status = DownloadStatus.RUNNING
                                    )
                                    if (throttler.shouldEmit(progress, now)) {
                                        emit(progress)
                                    }
                                }
                            }
                        }
                    }
                    deferredList.awaitAll()
                }
            } finally {
                writer.close()
            }

            // Step 7: File integrity verification (SHA-256 / MD5)
            if (currentTask.request.expectedChecksum != null && currentTask.request.checksumAlgorithm != ChecksumAlgorithm.NONE) {
                checksumValidator.validate(
                    filePath = currentTask.tempPath,
                    expectedChecksum = currentTask.request.expectedChecksum,
                    algorithm = currentTask.request.checksumAlgorithm
                )
            }

            // Step 8: Atomic staging move from .part to final destination
            fileSystem.atomicMove(currentTask.tempPath, currentTask.destinationPath)

            // Step 9: Final completed emission
            val completedTask = currentTask.copy(
                status = DownloadStatus.COMPLETED,
                downloadedBytes = if (currentTask.totalBytes > 0L) currentTask.totalBytes else currentDownloadedBytes
            )
            repository?.updateTaskStatus(completedTask.id, DownloadStatus.COMPLETED)
            emit(completedTask.toProgress())

        } catch (e: CancellationException) {
            repository?.updateTaskStatus(task.id, DownloadStatus.PAUSED)
            emit(task.copy(status = DownloadStatus.PAUSED).toProgress())
            throw e
        } catch (e: Exception) {
            repository?.updateTaskStatus(task.id, DownloadStatus.FAILED, errorMessage = e.message)
            emit(task.copy(status = DownloadStatus.FAILED, errorMessage = e.message).toProgress())
        }
    }

    override suspend fun pause(taskId: String): Result<Unit> {
        return cancellationMutex.withLock {
            runCatching {
                activeDownloadJobs.remove(taskId)?.cancel()
                repository?.updateTaskStatus(taskId, DownloadStatus.PAUSED)
                Unit
            }
        }
    }

    override fun resume(task: DownloadTask): Flow<DownloadProgress> {
        return start(task)
    }

    override suspend fun cancel(taskId: String): Result<Unit> {
        return cancellationMutex.withLock {
            runCatching {
                activeDownloadJobs.remove(taskId)?.cancel()
                val task = repository?.getTaskById(taskId)
                if (task != null) {
                    fileSystem.delete(task.tempPath)
                    repository.updateTaskStatus(taskId, DownloadStatus.CANCELLED)
                }
                Unit
            }
        }
    }

    private suspend fun downloadChunkWithRetry(
        task: DownloadTask,
        chunk: DownloadChunk,
        writer: IRandomAccessFileWriter,
        onBytesRead: suspend (Int) -> Unit
    ) {
        var currentChunk = chunk
        var attempts = 0
        val retryPolicy = task.request.retryPolicy

        while (true) {
            try {
                downloadChunkStream(task, currentChunk, writer) { bytesRead ->
                    currentChunk = currentChunk.copy(currentByte = currentChunk.currentByte + bytesRead)
                    onBytesRead(bytesRead)
                }
                currentChunk = currentChunk.copy(status = DownloadStatus.COMPLETED)
                repository?.updateChunkProgress(currentChunk.chunkId, currentChunk.currentByte, DownloadStatus.COMPLETED)
                return
            } catch (e: CancellationException) {
                repository?.updateChunkProgress(currentChunk.chunkId, currentChunk.currentByte, DownloadStatus.PAUSED)
                throw e
            } catch (e: Exception) {
                attempts++
                if (attempts > retryPolicy.maxRetries) {
                    repository?.updateChunkProgress(currentChunk.chunkId, currentChunk.currentByte, DownloadStatus.FAILED)
                    throw e
                }
                val delayMs = retryPolicy.calculateDelayMs(attempts)
                delay(delayMs)
            }
        }
    }

    private suspend fun downloadChunkStream(
        task: DownloadTask,
        chunk: DownloadChunk,
        writer: IRandomAccessFileWriter,
        onBufferChunk: suspend (Int) -> Unit
    ) {
        if (chunk.isCompleted) return

        val request = task.request
        httpClient.prepareGet(request.url) {
            headers {
                request.headers.forEach { (k, v) -> append(k, v) }
                if (task.supportsRanges && chunk.endByte < Long.MAX_VALUE) {
                    append(HttpHeaders.Range, "bytes=${chunk.currentByte}-${chunk.endByte}")
                }
                task.eTag?.let { eTag ->
                    append(HttpHeaders.IfRange, "\"$eTag\"")
                }
            }
        }.execute { response: HttpResponse ->
            if (!response.status.isSuccess() && response.status != HttpStatusCode.PartialContent) {
                throw IllegalStateException("HTTP ${response.status.value}: ${response.status.description}")
            }

            val channel: ByteReadChannel = response.bodyAsChannel()
            val buffer = ByteArray(64 * 1024) // 64 KB fixed buffer for OOM protection
            var offset = chunk.currentByte

            while (!channel.isClosedForRead) {
                val bytesRead = channel.readAvailable(buffer, 0, buffer.size)
                if (bytesRead <= 0) break

                writer.write(offset, buffer, 0, bytesRead)
                offset += bytesRead
                onBufferChunk(bytesRead)
            }
            writer.flush()
        }
    }

    private fun resolveDestinationCollision(task: DownloadTask): String {
        val dest = task.destinationPath
        if (!fileSystem.exists(dest)) return dest

        return when (task.request.collisionStrategy) {
            CollisionStrategy.OVERWRITE, CollisionStrategy.RESUME_OR_USE_EXISTING -> dest
            CollisionStrategy.FAIL_IF_EXISTS -> throw IllegalStateException("File already exists at: $dest")
            CollisionStrategy.AUTO_RENAME -> {
                var counter = 1
                val dotIndex = dest.lastIndexOf('.')
                val nameWithoutExt = if (dotIndex > 0) dest.substring(0, dotIndex) else dest
                val ext = if (dotIndex > 0) dest.substring(dotIndex) else ""
                var candidate = "$nameWithoutExt ($counter)$ext"
                while (fileSystem.exists(candidate)) {
                    counter++
                    candidate = "$nameWithoutExt ($counter)$ext"
                }
                candidate
            }
        }
    }

    private fun currentTimeEpochMs(): Long {
        return io.github.cmp.downloader.core.client.currentTimeEpochMs()
    }
}
