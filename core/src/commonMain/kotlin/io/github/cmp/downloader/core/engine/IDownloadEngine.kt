package io.github.cmp.downloader.core.engine

import io.github.cmp.downloader.core.model.DownloadChunk
import io.github.cmp.downloader.core.model.DownloadProgress
import io.github.cmp.downloader.core.model.DownloadTask
import kotlinx.coroutines.flow.Flow

/**
 * Platform engine interface that abstracts underlying network drivers
 * (Android WorkManager/OkHttp and iOS NSURLSession).
 */
interface IDownloadEngine {
    /**
     * Probes the server using a HEAD request or initial byte probe to discover
     * Content-Length, Accept-Ranges, ETag, and suggested filename.
     */
    suspend fun probe(task: DownloadTask): Result<EngineProbeResult>

    /**
     * Dispatches download execution of the given task.
     * Emits continuous [DownloadProgress] updates until completed, paused, cancelled, or failed.
     */
    fun start(task: DownloadTask): Flow<DownloadProgress>

    /**
     * Pauses the active download task. The engine flushes any pending disk writes
     * and preserves partial chunk offsets.
     */
    suspend fun pause(taskId: String): Result<Unit>

    /**
     * Resumes a previously paused download task using HTTP Range offsets.
     */
    fun resume(task: DownloadTask): Flow<DownloadProgress>

    /**
     * Aborts and cancels an active or queued download. Engine cleans up transient connections.
     */
    suspend fun cancel(taskId: String): Result<Unit>
}
