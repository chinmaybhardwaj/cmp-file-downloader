package io.github.cmp.downloader.core.client

import io.github.cmp.downloader.core.engine.IDownloadEngine
import io.github.cmp.downloader.core.model.CollisionStrategy
import io.github.cmp.downloader.core.model.DownloadProgress
import io.github.cmp.downloader.core.model.DownloadRequest
import io.github.cmp.downloader.core.model.DownloadStatus
import io.github.cmp.downloader.core.model.DownloadTask
import io.github.cmp.downloader.core.model.NetworkConstraints
import io.github.cmp.downloader.core.model.RetryPolicy
import io.github.cmp.downloader.core.repository.DownloadTaskRepository
import io.github.cmp.downloader.core.repository.InMemoryTaskRepository
import io.github.cmp.downloader.core.storage.IChecksumValidator
import io.github.cmp.downloader.core.storage.IFileSystem
import io.github.cmp.downloader.core.storage.createChecksumValidator
import io.github.cmp.downloader.core.storage.createDefaultFileSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * Main public entry point for enqueuing, controlling, and monitoring file downloads.
 */
class DownloaderClient private constructor(
    val config: DownloaderConfig,
    private val repository: DownloadTaskRepository,
    private val engine: IDownloadEngine?,
    private val fileSystem: IFileSystem,
    private val checksumValidator: IChecksumValidator,
    private val coroutineScope: CoroutineScope
) {
    private val mutex = Mutex()
    private val activeJobs = mutableMapOf<String, Job>()
    private val progressFlow = MutableSharedFlow<DownloadProgress>(extraBufferCapacity = 64)

    /**
     * Enqueues a new download request.
     * @return Unique [taskId] generated for this download.
     */
    suspend fun enqueue(request: DownloadRequest): String {
        val taskId = generateTaskId()
        val now = currentTimeEpochMs()

        val fileName = request.fileName ?: extractFileNameFromUrl(request.url)
        val destinationPath = "${request.destinationDirectory.trimEnd('/')}/$fileName"
        val tempDir = config.tempDirectory ?: request.destinationDirectory
        val tempPath = "${tempDir.trimEnd('/')}/.$fileName.$taskId.part"

        val task = DownloadTask(
            id = taskId,
            request = request,
            status = DownloadStatus.QUEUED,
            destinationPath = destinationPath,
            tempPath = tempPath,
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )

        repository.insertTask(task)
        progressFlow.emit(DownloadProgress.initial(taskId, request.url))

        checkAndScheduleNext()
        return taskId
    }

    /**
     * Pauses an active or queued download task.
     */
    suspend fun pause(taskId: String) {
        mutex.withLock {
            val job = activeJobs.remove(taskId)
            job?.cancel()
            engine?.pause(taskId)
            repository.updateTaskStatus(taskId, DownloadStatus.PAUSED)
            val task = repository.getTaskById(taskId)
            if (task != null) {
                progressFlow.emit(task.toProgress())
            }
        }
        checkAndScheduleNext()
    }

    /**
     * Resumes a paused or failed download task.
     */
    suspend fun resume(taskId: String) {
        mutex.withLock {
            val task = repository.getTaskById(taskId) ?: return
            if (task.status.isPausable || task.status == DownloadStatus.PAUSED || task.status == DownloadStatus.FAILED) {
                repository.updateTaskStatus(taskId, DownloadStatus.QUEUED)
                progressFlow.emit(task.copy(status = DownloadStatus.QUEUED).toProgress())
            }
        }
        checkAndScheduleNext()
    }

    /**
     * Cancels a download task and cleans up temporary partial files if configured.
     */
    suspend fun cancel(taskId: String, deletePartialFiles: Boolean = true) {
        mutex.withLock {
            val job = activeJobs.remove(taskId)
            job?.cancel()
            engine?.cancel(taskId)
            val task = repository.getTaskById(taskId)
            if (task != null) {
                if (deletePartialFiles) {
                    fileSystem.delete(task.tempPath)
                }
                repository.updateTaskStatus(taskId, DownloadStatus.CANCELLED)
                progressFlow.emit(task.copy(status = DownloadStatus.CANCELLED).toProgress())
            }
        }
        checkAndScheduleNext()
    }

    /**
     * Deletes a task record and optionally deletes downloaded/partial files from disk.
     */
    suspend fun delete(taskId: String, deleteFiles: Boolean = true) {
        cancel(taskId, deletePartialFiles = deleteFiles)
        val task = repository.getTaskById(taskId)
        if (deleteFiles && task != null) {
            fileSystem.delete(task.destinationPath)
            fileSystem.delete(task.tempPath)
        }
        repository.deleteTask(taskId)
    }

    /**
     * Observes real-time progress for a specific task ID.
     */
    fun observeProgress(taskId: String): Flow<DownloadProgress> {
        return progressFlow.asSharedFlow().filter { it.taskId == taskId }
    }

    /**
     * Observes a task's full state snapshot.
     */
    fun observeTask(taskId: String): Flow<DownloadTask?> {
        return repository.observeTask(taskId)
    }

    /**
     * Observes all tasks in the system.
     */
    fun observeAllTasks(): Flow<List<DownloadTask>> {
        return repository.observeAllTasks()
    }

    suspend fun getTask(taskId: String): DownloadTask? {
        return repository.getTaskById(taskId)
    }

    suspend fun getAllTasks(): List<DownloadTask> {
        return repository.getAllTasks()
    }

    /**
     * Inspects active jobs and schedules next queued tasks up to [DownloaderConfig.maxConcurrentDownloads].
     */
    private suspend fun checkAndScheduleNext() {
        mutex.withLock {
            val activeCount = activeJobs.size
            val availableSlots = config.maxConcurrentDownloads - activeCount
            if (availableSlots <= 0) return

            val queuedTasks = repository.getTasksByStatus(DownloadStatus.QUEUED)
            val tasksToStart = queuedTasks.take(availableSlots)

            for (task in tasksToStart) {
                if (engine != null) {
                    val job = coroutineScope.launch {
                        repository.updateTaskStatus(task.id, DownloadStatus.RUNNING)
                        engine.start(task).collect { progress ->
                            progressFlow.emit(progress)
                            if (progress.status.isTerminal || progress.status == DownloadStatus.PAUSED) {
                                mutex.withLock { activeJobs.remove(task.id) }
                                checkAndScheduleNext()
                            }
                        }
                    }
                    activeJobs[task.id] = job
                }
            }
        }
    }

    private fun generateTaskId(): String {
        val timestamp = currentTimeEpochMs()
        val random = Random.nextInt(1000, 9999)
        return "task_${timestamp}_$random"
    }

    private fun extractFileNameFromUrl(url: String): String {
        return try {
            val cleanUrl = url.substringBefore('?').substringBefore('#')
            val name = cleanUrl.substringAfterLast('/')
            if (name.isNotBlank()) name else "downloaded_file.bin"
        } catch (_: Exception) {
            "downloaded_file.bin"
        }
    }

    companion object {
        private var singletonInstance: DownloaderClient? = null
        private val initMutex = Mutex()

        /**
         * Returns the global default singleton instance. Throws [IllegalStateException] if not yet initialized.
         */
        fun getInstance(): DownloaderClient {
            return singletonInstance ?: throw IllegalStateException(
                "DownloaderClient has not been initialized. Call DownloaderClient.initialize() first or use DownloaderClient.Builder()."
            )
        }

        /**
         * Initializes the global default singleton instance.
         */
        suspend fun initialize(client: DownloaderClient) {
            initMutex.withLock {
                singletonInstance = client
            }
        }

        /**
         * Convenient factory to create a new [Builder].
         */
        fun builder(): Builder = Builder()
    }

    class Builder {
        private var maxConcurrentDownloads: Int = 3
        private var maxSegmentsPerFile: Int = 4
        private var progressThrottleIntervalMs: Long = 150L
        private var defaultCollisionStrategy: CollisionStrategy = CollisionStrategy.AUTO_RENAME
        private var defaultRetryPolicy: RetryPolicy = RetryPolicy.DEFAULT
        private var defaultNetworkConstraints: NetworkConstraints = NetworkConstraints.DEFAULT
        private var tempDirectory: String? = null
        private var defaultDestinationDirectory: String? = null

        private var repository: DownloadTaskRepository? = null
        private var engine: IDownloadEngine? = null
        private var fileSystem: IFileSystem? = null
        private var checksumValidator: IChecksumValidator? = null
        private var coroutineScope: CoroutineScope? = null

        fun maxConcurrentDownloads(limit: Int) = apply { this.maxConcurrentDownloads = limit }
        fun maxSegmentsPerFile(segments: Int) = apply { this.maxSegmentsPerFile = segments }
        fun progressThrottleIntervalMs(intervalMs: Long) = apply { this.progressThrottleIntervalMs = intervalMs }
        fun defaultCollisionStrategy(strategy: CollisionStrategy) = apply { this.defaultCollisionStrategy = strategy }
        fun defaultRetryPolicy(policy: RetryPolicy) = apply { this.defaultRetryPolicy = policy }
        fun defaultNetworkConstraints(constraints: NetworkConstraints) = apply { this.defaultNetworkConstraints = constraints }
        fun tempDirectory(directory: String) = apply { this.tempDirectory = directory }
        fun defaultDestinationDirectory(directory: String) = apply { this.defaultDestinationDirectory = directory }

        fun repository(repo: DownloadTaskRepository) = apply { this.repository = repo }
        fun engine(engine: IDownloadEngine) = apply { this.engine = engine }
        fun fileSystem(fs: IFileSystem) = apply { this.fileSystem = fs }
        fun checksumValidator(validator: IChecksumValidator) = apply { this.checksumValidator = validator }
        fun coroutineScope(scope: CoroutineScope) = apply { this.coroutineScope = scope }

        fun build(): DownloaderClient {
            val config = DownloaderConfig(
                maxConcurrentDownloads = maxConcurrentDownloads,
                maxSegmentsPerFile = maxSegmentsPerFile,
                progressThrottleIntervalMs = progressThrottleIntervalMs,
                defaultCollisionStrategy = defaultCollisionStrategy,
                defaultRetryPolicy = defaultRetryPolicy,
                defaultNetworkConstraints = defaultNetworkConstraints,
                tempDirectory = tempDirectory,
                defaultDestinationDirectory = defaultDestinationDirectory
            )

            val actualRepo = repository ?: InMemoryTaskRepository()
            val actualFs = fileSystem ?: createDefaultFileSystem()
            val actualValidator = checksumValidator ?: createChecksumValidator()
            val actualEngine = engine ?: io.github.cmp.downloader.core.engine.SegmentedDownloadEngine(
                fileSystem = actualFs,
                checksumValidator = actualValidator,
                repository = actualRepo
            )

            return DownloaderClient(
                config = config,
                repository = actualRepo,
                engine = actualEngine,
                fileSystem = actualFs,
                checksumValidator = actualValidator,
                coroutineScope = coroutineScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
            )
        }
    }
}

/**
 * Returns current system time in milliseconds across multiplatform targets.
 */
expect fun currentTimeEpochMs(): Long
