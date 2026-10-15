package io.github.cmp.downloader.core.model

import kotlin.math.roundToInt

/**
 * Snapshot of real-time progress for an active or completed download task.
 */
data class DownloadProgress(
    val taskId: String,
    val url: String,
    val bytesDownloaded: Long,
    val totalBytes: Long, // -1L if unknown (e.g. chunked transfer)
    val progressPercentage: Float, // 0.0f - 100.0f
    val speedBytesPerSec: Long = 0L,
    val estimatedTimeRemainingSeconds: Long? = null,
    val activeSegments: Int = 1,
    val status: DownloadStatus = DownloadStatus.QUEUED
) {
    val isIndeterminate: Boolean
        get() = totalBytes <= 0L

    val formattedDownloadedBytes: String
        get() = formatBytes(bytesDownloaded)

    val formattedTotalBytes: String
        get() = if (totalBytes > 0L) formatBytes(totalBytes) else "Unknown"

    val formattedSpeed: String
        get() = if (speedBytesPerSec > 0L) "${formatBytes(speedBytesPerSec)}/s" else "0 B/s"

    val formattedEta: String
        get() = when {
            status == DownloadStatus.COMPLETED -> "Done"
            status == DownloadStatus.PAUSED -> "Paused"
            estimatedTimeRemainingSeconds == null || estimatedTimeRemainingSeconds < 0L -> "--"
            estimatedTimeRemainingSeconds < 60 -> "${estimatedTimeRemainingSeconds}s"
            estimatedTimeRemainingSeconds < 3600 -> {
                val mins = estimatedTimeRemainingSeconds / 60
                val secs = estimatedTimeRemainingSeconds % 60
                "${mins}m ${secs}s"
            }
            else -> {
                val hours = estimatedTimeRemainingSeconds / 3600
                val mins = (estimatedTimeRemainingSeconds % 3600) / 60
                "${hours}h ${mins}m"
            }
        }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0L) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var value = bytes.toDouble()
            var unitIndex = 0
            while (value >= 1024.0 && unitIndex < units.lastIndex) {
                value /= 1024.0
                unitIndex++
            }
            return if (unitIndex == 0) {
                "${bytes} B"
            } else {
                val rounded = (value * 10.0).roundToInt() / 10.0
                if (rounded == rounded.toLong().toDouble()) {
                    "${rounded.toLong()} ${units[unitIndex]}"
                } else {
                    "$rounded ${units[unitIndex]}"
                }
            }
        }

        fun initial(taskId: String, url: String): DownloadProgress {
            return DownloadProgress(
                taskId = taskId,
                url = url,
                bytesDownloaded = 0L,
                totalBytes = -1L,
                progressPercentage = 0.0f,
                speedBytesPerSec = 0L,
                estimatedTimeRemainingSeconds = null,
                activeSegments = 0,
                status = DownloadStatus.QUEUED
            )
        }
    }
}
