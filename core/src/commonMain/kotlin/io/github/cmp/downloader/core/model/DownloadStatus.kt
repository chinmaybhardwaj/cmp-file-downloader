package io.github.cmp.downloader.core.model

/**
 * Lifecycle states of a download task.
 */
enum class DownloadStatus {
    /** Task has been created and queued, awaiting available slot or network conditions. */
    QUEUED,

    /** File headers are being probed or file is being prepared. */
    CONNECTING,

    /** Data is actively being transferred. */
    RUNNING,

    /** Download was paused by user or system. Can be resumed. */
    PAUSED,

    /** Download completed successfully and checksum (if provided) was verified. */
    COMPLETED,

    /** Download encountered an error and cannot continue without retry. */
    FAILED,

    /** Download was cancelled by user. Partial files may be deleted. */
    CANCELLED;

    val isTerminal: Boolean
        get() = this == COMPLETED || this == FAILED || this == CANCELLED

    val isActive: Boolean
        get() = this == RUNNING || this == CONNECTING

    val isPausable: Boolean
        get() = this == RUNNING || this == CONNECTING || this == QUEUED
}
