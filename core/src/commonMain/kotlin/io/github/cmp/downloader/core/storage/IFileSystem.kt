package io.github.cmp.downloader.core.storage

/**
 * File system abstraction for checking storage capacity, staging temporary files,
 * and performing atomic moves.
 */
interface IFileSystem {
    /**
     * Returns available storage in bytes on the volume hosting the given path.
     */
    fun getAvailableDiskSpace(path: String): Long

    /**
     * Checks if a file or directory exists at the given path.
     */
    fun exists(path: String): Boolean

    /**
     * Returns size in bytes of the file at [path]. Returns 0L if not found.
     */
    fun getFileSize(path: String): Long

    /**
     * Ensures parent directories exist for the specified file path.
     */
    fun createDirectories(path: String): Boolean

    /**
     * Performs an atomic move/rename of [sourcePath] to [targetPath].
     */
    fun atomicMove(sourcePath: String, targetPath: String): Boolean

    /**
     * Deletes the file at [path]. Returns true if deleted or non-existent.
     */
    fun delete(path: String): Boolean

    /**
     * Pre-allocates disk space for a large file to avoid fragmentation and ensure
     * continuous random access write capability across parallel chunks.
     */
    fun preAllocate(path: String, sizeBytes: Long): Boolean
}

/**
 * Factory to create platform-specific default file system.
 */
expect fun createDefaultFileSystem(): IFileSystem
