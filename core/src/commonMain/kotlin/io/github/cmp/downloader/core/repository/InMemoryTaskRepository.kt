package io.github.cmp.downloader.core.repository

import io.github.cmp.downloader.core.model.DownloadChunk
import io.github.cmp.downloader.core.model.DownloadStatus
import io.github.cmp.downloader.core.model.DownloadTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe reactive in-memory implementation of [DownloadTaskRepository].
 */
class InMemoryTaskRepository : DownloadTaskRepository {
    private val mutex = Mutex()
    private val tasksMap = mutableMapOf<String, DownloadTask>()
    private val chunksMap = mutableMapOf<String, MutableList<DownloadChunk>>()
    private val tasksStateFlow = MutableStateFlow<List<DownloadTask>>(emptyList())

    override suspend fun insertTask(task: DownloadTask) {
        mutex.withLock {
            tasksMap[task.id] = task
            emitUpdatedList()
        }
    }

    override suspend fun updateTask(task: DownloadTask) {
        mutex.withLock {
            tasksMap[task.id] = task
            emitUpdatedList()
        }
    }

    override suspend fun updateTaskProgress(taskId: String, downloadedBytes: Long, totalBytes: Long) {
        mutex.withLock {
            val existing = tasksMap[taskId] ?: return@withLock
            val updated = existing.copy(
                downloadedBytes = downloadedBytes,
                totalBytes = if (totalBytes > 0L) totalBytes else existing.totalBytes
            )
            tasksMap[taskId] = updated
            emitUpdatedList()
        }
    }

    override suspend fun updateTaskStatus(taskId: String, status: DownloadStatus, errorMessage: String?) {
        mutex.withLock {
            val existing = tasksMap[taskId] ?: return@withLock
            val updated = existing.copy(
                status = status,
                errorMessage = errorMessage ?: existing.errorMessage
            )
            tasksMap[taskId] = updated
            emitUpdatedList()
        }
    }

    override suspend fun getTaskById(taskId: String): DownloadTask? {
        return mutex.withLock { tasksMap[taskId] }
    }

    override suspend fun getAllTasks(): List<DownloadTask> {
        return mutex.withLock {
            tasksMap.values
                .sortedWith(compareByDescending<DownloadTask> { it.request.priority.weight }.thenBy { it.createdAtEpochMs })
        }
    }

    override suspend fun getTasksByStatus(status: DownloadStatus): List<DownloadTask> {
        return mutex.withLock {
            tasksMap.values
                .filter { it.status == status }
                .sortedWith(compareByDescending<DownloadTask> { it.request.priority.weight }.thenBy { it.createdAtEpochMs })
        }
    }

    override suspend fun deleteTask(taskId: String) {
        mutex.withLock {
            tasksMap.remove(taskId)
            chunksMap.remove(taskId)
            emitUpdatedList()
        }
    }

    override suspend fun saveChunks(chunks: List<DownloadChunk>) {
        if (chunks.isEmpty()) return
        val taskId = chunks.first().taskId
        mutex.withLock {
            chunksMap[taskId] = chunks.toMutableList()
            val task = tasksMap[taskId]
            if (task != null) {
                tasksMap[taskId] = task.copy(chunks = chunks)
                emitUpdatedList()
            }
        }
    }

    override suspend fun updateChunkProgress(chunkId: String, currentByte: Long, status: DownloadStatus) {
        mutex.withLock {
            for ((taskId, list) in chunksMap) {
                val index = list.indexOfFirst { it.chunkId == chunkId }
                if (index != -1) {
                    val old = list[index]
                    val updated = old.copy(currentByte = currentByte, status = status)
                    list[index] = updated

                    // Recalculate task downloadedBytes from all chunks
                    val totalDownloaded = list.sumOf { it.downloadedBytes }
                    val task = tasksMap[taskId]
                    if (task != null) {
                        tasksMap[taskId] = task.copy(downloadedBytes = totalDownloaded, chunks = list.toList())
                        emitUpdatedList()
                    }
                    break
                }
            }
        }
    }

    override suspend fun getChunksForTask(taskId: String): List<DownloadChunk> {
        return mutex.withLock { chunksMap[taskId]?.toList() ?: emptyList() }
    }

    override fun observeTask(taskId: String): Flow<DownloadTask?> {
        return tasksStateFlow.map { list -> list.firstOrNull { it.id == taskId } }
    }

    override fun observeAllTasks(): Flow<List<DownloadTask>> {
        return tasksStateFlow
    }

    private fun emitUpdatedList() {
        tasksStateFlow.value = tasksMap.values
            .sortedWith(compareByDescending<DownloadTask> { it.request.priority.weight }.thenBy { it.createdAtEpochMs })
    }
}
