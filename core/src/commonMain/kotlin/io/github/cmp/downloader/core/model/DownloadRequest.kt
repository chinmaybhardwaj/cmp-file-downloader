package io.github.cmp.downloader.core.model

/**
 * Immutable configuration request specifying a file to be downloaded.
 */
data class DownloadRequest(
    val url: String,
    val destinationDirectory: String,
    val fileName: String? = null,
    val tag: String? = null,
    val priority: Priority = Priority.NORMAL,
    val headers: Map<String, String> = emptyMap(),
    val collisionStrategy: CollisionStrategy = CollisionStrategy.AUTO_RENAME,
    val expectedChecksum: String? = null,
    val checksumAlgorithm: ChecksumAlgorithm = ChecksumAlgorithm.NONE,
    val maxSegments: Int = 4,
    val networkConstraints: NetworkConstraints = NetworkConstraints.DEFAULT,
    val retryPolicy: RetryPolicy = RetryPolicy.DEFAULT
) {
    init {
        require(url.isNotBlank()) { "Download URL cannot be blank" }
        require(destinationDirectory.isNotBlank()) { "Destination directory cannot be blank" }
        require(maxSegments in 1..16) { "maxSegments must be between 1 and 16" }
    }

    class Builder(private val url: String, private val destinationDirectory: String) {
        private var fileName: String? = null
        private var tag: String? = null
        private var priority: Priority = Priority.NORMAL
        private val headers = mutableMapOf<String, String>()
        private var collisionStrategy: CollisionStrategy = CollisionStrategy.AUTO_RENAME
        private var expectedChecksum: String? = null
        private var checksumAlgorithm: ChecksumAlgorithm = ChecksumAlgorithm.NONE
        private var maxSegments: Int = 4
        private var networkConstraints: NetworkConstraints = NetworkConstraints.DEFAULT
        private var retryPolicy: RetryPolicy = RetryPolicy.DEFAULT

        fun fileName(fileName: String) = apply { this.fileName = fileName }
        fun tag(tag: String) = apply { this.tag = tag }
        fun priority(priority: Priority) = apply { this.priority = priority }
        fun addHeader(key: String, value: String) = apply { this.headers[key] = value }
        fun headers(headers: Map<String, String>) = apply { this.headers.putAll(headers) }
        fun collisionStrategy(strategy: CollisionStrategy) = apply { this.collisionStrategy = strategy }
        fun checksum(expectedChecksum: String, algorithm: ChecksumAlgorithm) = apply {
            this.expectedChecksum = expectedChecksum
            this.checksumAlgorithm = algorithm
        }
        fun maxSegments(maxSegments: Int) = apply { this.maxSegments = maxSegments }
        fun networkConstraints(constraints: NetworkConstraints) = apply { this.networkConstraints = constraints }
        fun retryPolicy(policy: RetryPolicy) = apply { this.retryPolicy = policy }

        fun build(): DownloadRequest {
            return DownloadRequest(
                url = url,
                destinationDirectory = destinationDirectory,
                fileName = fileName,
                tag = tag,
                priority = priority,
                headers = headers.toMap(),
                collisionStrategy = collisionStrategy,
                expectedChecksum = expectedChecksum,
                checksumAlgorithm = checksumAlgorithm,
                maxSegments = maxSegments,
                networkConstraints = networkConstraints,
                retryPolicy = retryPolicy
            )
        }
    }
}
