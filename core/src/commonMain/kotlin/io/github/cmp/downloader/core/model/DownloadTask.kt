package io.github.cmp.downloader.core.model

/**
 * Complete runtime and persistent representation of an enqueued or finished download task.
 */
data class DownloadTask(
    val id: String,
    val request: DownloadRequest,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val destinationPath: String,
    val tempPath: String,
    val totalBytes: Long = -1L,
    val downloadedBytes: Long = 0L,
    val retryCount: Int = 0,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val errorMessage: String? = null,
    val eTag: String? = null,
    val supportsRanges: Boolean = false,
    val chunks: List<DownloadChunk> = emptyList()
) {
    val progressPercentage: Float
        get() = if (totalBytes > 0L) {
            ((downloadedBytes.toDouble() / totalBytes.toDouble()) * 100.0).toFloat().coerceIn(0f, 100f)
        } else {
            0.0f
        }

    fun toProgress(speedBytesPerSec: Long = 0L, etaSeconds: Long? = null): DownloadProgress {
        return DownloadProgress(
            taskId = id,
            url = request.url,
            bytesDownloaded = downloadedBytes,
            totalBytes = totalBytes,
            progressPercentage = progressPercentage,
            speedBytesPerSec = speedBytesPerSec,
            estimatedTimeRemainingSeconds = etaSeconds,
            activeSegments = chunks.count { it.status == DownloadStatus.RUNNING }.coerceAtLeast(1),
            status = status
        )
    }
}
