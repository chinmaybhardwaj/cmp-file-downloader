package io.github.cmp.downloader.core.repository

import io.github.cmp.downloader.core.model.DownloadChunk
import io.github.cmp.downloader.core.model.DownloadStatus
import io.github.cmp.downloader.core.model.DownloadTask
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing persistent state of download tasks and segmented chunks.
 */
interface DownloadTaskRepository {
    /**
     * Inserts a newly enqueued task into the database.
     */
    suspend fun insertTask(task: DownloadTask)

    /**
     * Updates complete task record.
     */
    suspend fun updateTask(task: DownloadTask)

    /**
     * Updates downloaded bytes and total bytes for a task.
     */
    suspend fun updateTaskProgress(taskId: String, downloadedBytes: Long, totalBytes: Long)

    /**
     * Updates execution status of a task.
     */
    suspend fun updateTaskStatus(taskId: String, status: DownloadStatus, errorMessage: String? = null)

    /**
     * Retrieves task by unique identifier, or null if not found.
     */
    suspend fun getTaskById(taskId: String): DownloadTask?

    /**
     * Retrieves all tasks currently stored.
     */
    suspend fun getAllTasks(): List<DownloadTask>

    /**
     * Retrieves tasks with the specified status, ordered by Priority (descending) and createdAt (ascending).
     */
    suspend fun getTasksByStatus(status: DownloadStatus): List<DownloadTask>

    /**
     * Deletes a task and its associated chunks from persistence.
     */
    suspend fun deleteTask(taskId: String)

    /**
     * Inserts or replaces segmented chunks for a task.
     */
    suspend fun saveChunks(chunks: List<DownloadChunk>)

    /**
     * Updates progress of a specific chunk.
     */
    suspend fun updateChunkProgress(chunkId: String, currentByte: Long, status: DownloadStatus)

    /**
     * Retrieves all chunks belonging to a task.
     */
    suspend fun getChunksForTask(taskId: String): List<DownloadChunk>

    /**
     * Observes real-time updates for a single task as a [Flow].
     */
    fun observeTask(taskId: String): Flow<DownloadTask?>

    /**
     * Observes real-time list of all tasks.
     */
    fun observeAllTasks(): Flow<List<DownloadTask>>
}
