package io.github.cmp.downloader.core.engine

import io.github.cmp.downloader.core.model.DownloadProgress
import io.github.cmp.downloader.core.model.DownloadStatus
import kotlin.math.abs

/**
 * Throttles progress emission frequency to protect UI rendering loops and database transactions
 * from being overwhelmed by high-speed streaming transfers (e.g. 100MB/s).
 */
class ProgressThrottler(
    private val throttleIntervalMs: Long = 150L,
    private val minimumPercentageDelta: Float = 0.1f
) {
    private var lastEmittedTimeMs: Long = 0L
    private var lastEmittedPercentage: Float = -1f
    private var lastEmittedStatus: DownloadStatus? = null

    /**
     * Determines whether the current progress update should be emitted to subscribers.
     * Guaranteed to return true on status transitions (e.g. COMPLETED, FAILED, PAUSED).
     */
    fun shouldEmit(progress: DownloadProgress, currentTimeMs: Long): Boolean {
        // Immediate pass-through on state transitions
        if (progress.status != lastEmittedStatus) {
            recordEmission(progress, currentTimeMs)
            return true
        }

        // Terminal states always emit
        if (progress.status.isTerminal) {
            recordEmission(progress, currentTimeMs)
            return true
        }

        val elapsedMs = currentTimeMs - lastEmittedTimeMs
        val percentageDelta = abs(progress.progressPercentage - lastEmittedPercentage)

        if (elapsedMs >= throttleIntervalMs || percentageDelta >= minimumPercentageDelta) {
            recordEmission(progress, currentTimeMs)
            return true
        }

        return false
    }

    private fun recordEmission(progress: DownloadProgress, currentTimeMs: Long) {
        lastEmittedTimeMs = currentTimeMs
        lastEmittedPercentage = progress.progressPercentage
        lastEmittedStatus = progress.status
    }

    fun reset() {
        lastEmittedTimeMs = 0L
        lastEmittedPercentage = -1f
        lastEmittedStatus = null
    }
}
