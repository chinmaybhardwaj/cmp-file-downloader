package io.github.cmp.downloader.core.model

import kotlin.math.min
import kotlin.math.pow

/**
 * Exponential backoff retry configuration for transient network failures.
 */
data class RetryPolicy(
    val maxRetries: Int = 3,
    val initialBackoffMs: Long = 1000L,
    val backoffMultiplier: Double = 2.0,
    val maxBackoffMs: Long = 30000L,
    val retryOnNetworkErrorsOnly: Boolean = true
) {
    /**
     * Calculates delay in milliseconds before attempt [attemptNumber] (1-indexed).
     */
    fun calculateDelayMs(attemptNumber: Int): Long {
        if (attemptNumber <= 0) return 0L
        val exponential = initialBackoffMs * (backoffMultiplier.pow((attemptNumber - 1).toDouble())).toLong()
        return min(exponential, maxBackoffMs)
    }

    companion object {
        val DEFAULT = RetryPolicy()
        val AGGRESSIVE = RetryPolicy(maxRetries = 5, initialBackoffMs = 500L, backoffMultiplier = 1.5)
        val NO_RETRY = RetryPolicy(maxRetries = 0)
    }
}
