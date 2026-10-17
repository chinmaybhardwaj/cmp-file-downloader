package io.github.cmp.downloader.core.engine

/**
 * Calculates smoothed download transfer speed (bytes/sec) and remaining ETA (seconds)
 * using an Exponential Moving Average (EMA) filter.
 *
 * @param smoothingFactor (alpha) between 0.0 and 1.0. Higher values respond faster to changes,
 * lower values provide smoother stabilization against bursty network packet arrival.
 */
class SpeedCalculator(
    private val smoothingFactor: Double = 0.25
) {
    private var lastCalculationTimeMs: Long = 0L
    private var lastBytesCount: Long = 0L
    private var smoothedSpeedBytesPerSec: Double = 0.0

    /**
     * Updates the calculation with current downloaded bytes and current timestamp.
     * Returns a pair of (smoothedSpeedBytesPerSec, estimatedTimeRemainingSeconds).
     */
    fun update(
        currentBytes: Long,
        totalBytes: Long,
        currentTimeMs: Long
    ): Pair<Long, Long?> {
        if (lastCalculationTimeMs == 0L) {
            lastCalculationTimeMs = currentTimeMs
            lastBytesCount = currentBytes
            return Pair(0L, null)
        }

        val elapsedMs = currentTimeMs - lastCalculationTimeMs
        if (elapsedMs <= 0L) {
            val speed = smoothedSpeedBytesPerSec.toLong()
            return Pair(speed, calculateEta(currentBytes, totalBytes, speed))
        }

        val deltaBytes = (currentBytes - lastBytesCount).coerceAtLeast(0L)
        val instantSpeed = (deltaBytes.toDouble() / elapsedMs.toDouble()) * 1000.0

        smoothedSpeedBytesPerSec = if (smoothedSpeedBytesPerSec == 0.0) {
            instantSpeed
        } else {
            (smoothingFactor * instantSpeed) + ((1.0 - smoothingFactor) * smoothedSpeedBytesPerSec)
        }

        lastCalculationTimeMs = currentTimeMs
        lastBytesCount = currentBytes

        val finalSpeed = smoothedSpeedBytesPerSec.toLong()
        val eta = calculateEta(currentBytes, totalBytes, finalSpeed)

        return Pair(finalSpeed, eta)
    }

    private fun calculateEta(currentBytes: Long, totalBytes: Long, speedBytesPerSec: Long): Long? {
        if (totalBytes <= 0L || speedBytesPerSec <= 0L) return null
        val remainingBytes = (totalBytes - currentBytes).coerceAtLeast(0L)
        return remainingBytes / speedBytesPerSec
    }

    fun reset() {
        lastCalculationTimeMs = 0L
        lastBytesCount = 0L
        smoothedSpeedBytesPerSec = 0.0
    }
}
