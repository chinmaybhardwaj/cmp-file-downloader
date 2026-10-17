package io.github.cmp.downloader.core.model

/**
 * Supported cryptographic hash algorithms for file integrity verification.
 */
enum class ChecksumAlgorithm {
    NONE,
    MD5,
    SHA256
}
