package io.github.cmp.downloader.core.storage

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSURL
import platform.Foundation.NSURLVolumeAvailableCapacityForImportantUsageKey
import platform.posix.truncate

@OptIn(ExperimentalForeignApi::class)
class IosFileSystem : IFileSystem {
    private val fileManager = NSFileManager.defaultManager

    override fun getAvailableDiskSpace(path: String): Long {
        return try {
            val url = NSURL.fileURLWithPath(path)
            val values = url.resourceValuesForKeys(
                listOf(NSURLVolumeAvailableCapacityForImportantUsageKey),
                error = null
            )
            val space = values?.get(NSURLVolumeAvailableCapacityForImportantUsageKey) as? Long
            space ?: -1L
        } catch (_: Exception) {
            -1L
        }
    }

    override fun exists(path: String): Boolean {
        return fileManager.fileExistsAtPath(path)
    }

    override fun getFileSize(path: String): Long {
        return try {
            val attributes = fileManager.attributesOfItemAtPath(path, error = null)
            val size = attributes?.get(NSFileSize) as? Long
            size ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    override fun createDirectories(path: String): Boolean {
        val url = NSURL.fileURLWithPath(path)
        val dirUrl = if (fileManager.fileExistsAtPath(path)) url else url.URLByDeletingLastPathComponent
        val dirPath = dirUrl?.path ?: return false

        return fileManager.createDirectoryAtPath(
            path = dirPath,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
    }

    override fun atomicMove(sourcePath: String, targetPath: String): Boolean {
        createDirectories(targetPath)
        if (fileManager.fileExistsAtPath(targetPath)) {
            fileManager.removeItemAtPath(targetPath, error = null)
        }
        return fileManager.moveItemAtPath(sourcePath, toPath = targetPath, error = null)
    }

    override fun delete(path: String): Boolean {
        return if (fileManager.fileExistsAtPath(path)) {
            fileManager.removeItemAtPath(path, error = null)
        } else {
            true
        }
    }

    override fun preAllocate(path: String, sizeBytes: Long): Boolean {
        if (sizeBytes <= 0L) return true
        createDirectories(path)
        return truncate(path, sizeBytes) == 0
    }
}

actual fun createDefaultFileSystem(): IFileSystem = IosFileSystem()
