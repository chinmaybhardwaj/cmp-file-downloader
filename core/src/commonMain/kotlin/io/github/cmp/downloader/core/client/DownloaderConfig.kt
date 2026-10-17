package io.github.cmp.downloader.core.client

import io.github.cmp.downloader.core.model.CollisionStrategy
import io.github.cmp.downloader.core.model.NetworkConstraints
import io.github.cmp.downloader.core.model.RetryPolicy

/**
 * Global configuration options for [DownloaderClient].
 */
data class DownloaderConfig(
    val maxConcurrentDownloads: Int = 3,
    val maxSegmentsPerFile: Int = 4,
    val progressThrottleIntervalMs: Long = 150L,
    val defaultCollisionStrategy: CollisionStrategy = CollisionStrategy.AUTO_RENAME,
    val defaultRetryPolicy: RetryPolicy = RetryPolicy.DEFAULT,
    val defaultNetworkConstraints: NetworkConstraints = NetworkConstraints.DEFAULT,
    val tempDirectory: String? = null,
    val defaultDestinationDirectory: String? = null
) {
    init {
        require(maxConcurrentDownloads in 1..20) { "maxConcurrentDownloads must be between 1 and 20" }
        require(maxSegmentsPerFile in 1..16) { "maxSegmentsPerFile must be between 1 and 16" }
        require(progressThrottleIntervalMs >= 0L) { "progressThrottleIntervalMs cannot be negative" }
    }
}
