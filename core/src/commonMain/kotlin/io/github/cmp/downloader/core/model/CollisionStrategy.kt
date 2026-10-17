package io.github.cmp.downloader.core.model

/**
 * Strategy to apply when a file with the target name already exists at the destination path.
 */
enum class CollisionStrategy {
    /** Overwrites existing destination file completely upon completion. */
    OVERWRITE,

    /** Appends an incremental number to the filename (e.g. `file (1).ext`) to preserve existing file. */
    AUTO_RENAME,

    /** Aborts the download task immediately with a FileCollisionException. */
    FAIL_IF_EXISTS,

    /** If file already exists and matches expected size/checksum, mark as completed immediately without re-downloading. */
    RESUME_OR_USE_EXISTING
}
