package io.github.cmp.downloader.core.storage

/**
 * Exception thrown when the target storage volume lacks sufficient free capacity.
 */
class InsufficientStorageException(
    val requiredBytes: Long,
    val availableBytes: Long,
    message: String = "Insufficient storage space: required ${requiredBytes} bytes, but only ${availableBytes} bytes available."
) : Exception(message)

/**
 * Validates target disk capacity before downloading large files to prevent device memory exhaustion.
 */
class DiskSpaceValidator(
    private val fileSystem: IFileSystem,
    private val safetyMarginBytes: Long = 50L * 1024L * 1024L // 50 MB safety margin
) {
    /**
     * Verifies that the destination directory has enough free space to accommodate [contentLength].
     * @throws InsufficientStorageException if available disk space is less than required.
     */
    fun validateStorage(targetDirectoryPath: String, contentLength: Long) {
        if (contentLength <= 0L) return // Unknown length or empty

        val available = fileSystem.getAvailableDiskSpace(targetDirectoryPath)
        // If available is -1 (unknown on some platforms), skip validation
        if (available < 0L) return

        val requiredWithMargin = contentLength + safetyMarginBytes
        if (available < requiredWithMargin) {
            throw InsufficientStorageException(
                requiredBytes = requiredWithMargin,
                availableBytes = available
            )
        }
    }
}
