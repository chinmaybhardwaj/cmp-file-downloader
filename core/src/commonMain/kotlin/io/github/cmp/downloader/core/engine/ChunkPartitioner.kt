package io.github.cmp.downloader.core.engine

import io.github.cmp.downloader.core.model.DownloadChunk
import io.github.cmp.downloader.core.model.DownloadStatus
import kotlin.math.min

/**
 * Calculates byte range partitions for parallel segmented downloads.
 */
object ChunkPartitioner {
    const val DEFAULT_MIN_CHUNK_SIZE_BYTES: Long = 5L * 1024L * 1024L // 5 MB threshold per parallel chunk

    /**
     * Divides a file into parallel byte-range chunks.
     *
     * @param taskId Unique identifier of the download task.
     * @param totalBytes Total length of the remote file in bytes.
     * @param maxSegments Maximum parallel segments requested.
     * @param supportsByteRanges Whether the remote server accepted HTTP byte range requests.
     * @param minChunkSizeBytes Minimum byte threshold before splitting into an additional segment.
     */
    fun partition(
        taskId: String,
        totalBytes: Long,
        maxSegments: Int,
        supportsByteRanges: Boolean,
        minChunkSizeBytes: Long = DEFAULT_MIN_CHUNK_SIZE_BYTES
    ): List<DownloadChunk> {
        // Fallback to single chunk if server does not support ranges or size is unknown
        if (totalBytes <= 0L || !supportsByteRanges || maxSegments <= 1) {
            val endByte = if (totalBytes > 0L) totalBytes - 1L else Long.MAX_VALUE
            return listOf(
                DownloadChunk(
                    chunkId = "${taskId}_chunk_0",
                    taskId = taskId,
                    chunkIndex = 0,
                    startByte = 0L,
                    endByte = endByte,
                    currentByte = 0L,
                    status = DownloadStatus.QUEUED
                )
            )
        }

        // Calculate appropriate number of segments based on file size and threshold
        val possibleSegments = (totalBytes / minChunkSizeBytes).toInt().coerceAtLeast(1)
        val segmentCount = min(maxSegments, possibleSegments).coerceAtLeast(1)

        if (segmentCount <= 1) {
            return listOf(
                DownloadChunk(
                    chunkId = "${taskId}_chunk_0",
                    taskId = taskId,
                    chunkIndex = 0,
                    startByte = 0L,
                    endByte = totalBytes - 1L,
                    currentByte = 0L,
                    status = DownloadStatus.QUEUED
                )
            )
        }

        val partSize = totalBytes / segmentCount
        val chunks = mutableListOf<DownloadChunk>()

        for (i in 0 until segmentCount) {
            val startByte = i * partSize
            val endByte = if (i == segmentCount - 1) {
                totalBytes - 1L
            } else {
                ((i + 1) * partSize) - 1L
            }

            chunks.add(
                DownloadChunk(
                    chunkId = "${taskId}_chunk_$i",
                    taskId = taskId,
                    chunkIndex = i,
                    startByte = startByte,
                    endByte = endByte,
                    currentByte = startByte,
                    status = DownloadStatus.QUEUED
                )
            )
        }

        return chunks
    }
}
