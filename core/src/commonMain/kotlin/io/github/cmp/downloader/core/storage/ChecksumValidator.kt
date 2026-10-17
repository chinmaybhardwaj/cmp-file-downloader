package io.github.cmp.downloader.core.storage

import io.github.cmp.downloader.core.model.ChecksumAlgorithm

/**
 * Exception thrown when downloaded file hash does not match expected checksum.
 */
class ChecksumMismatchException(
    val expectedChecksum: String,
    val actualChecksum: String,
    val algorithm: ChecksumAlgorithm,
    message: String = "Checksum mismatch ($algorithm): expected '$expectedChecksum', got '$actualChecksum'"
) : Exception(message)

/**
 * Validates cryptographic checksums of completed files on disk.
 */
interface IChecksumValidator {
    /**
     * Computes the hash of the file at [filePath] using [algorithm] and returns hex string.
     */
    fun computeChecksum(filePath: String, algorithm: ChecksumAlgorithm): String

    /**
     * Validates that [filePath] matches [expectedChecksum].
     * @throws ChecksumMismatchException if hashes do not match.
     */
    fun validate(filePath: String, expectedChecksum: String, algorithm: ChecksumAlgorithm): Boolean {
        if (algorithm == ChecksumAlgorithm.NONE || expectedChecksum.isBlank()) {
            return true
        }
        val actual = computeChecksum(filePath, algorithm)
        if (!actual.equals(expectedChecksum.trim(), ignoreCase = true)) {
            throw ChecksumMismatchException(
                expectedChecksum = expectedChecksum.trim(),
                actualChecksum = actual,
                algorithm = algorithm
            )
        }
        return true
    }
}

expect fun createChecksumValidator(): IChecksumValidator
