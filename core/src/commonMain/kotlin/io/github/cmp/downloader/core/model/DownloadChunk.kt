package io.github.cmp.downloader.core.model

/**
 * Represents a single segmented byte-range chunk within a larger file download.
 * Enables multi-threaded parallel downloads and byte-level pause & resume.
 */
data class DownloadChunk(
    val chunkId: String,
    val taskId: String,
    val chunkIndex: Int,
    val startByte: Long,
    val endByte: Long,
    val currentByte: Long,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val errorMessage: String? = null
) {
    val totalChunkBytes: Long
        get() = (endByte - startByte) + 1L

    val downloadedBytes: Long
        get() = (currentByte - startByte).coerceAtLeast(0L)

    val isCompleted: Boolean
        get() = currentByte > endByte

    val remainingBytes: Long
        get() = (endByte - currentByte + 1L).coerceAtLeast(0L)
}
