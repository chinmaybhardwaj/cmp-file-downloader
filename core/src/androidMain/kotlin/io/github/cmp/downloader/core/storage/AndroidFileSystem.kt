package io.github.cmp.downloader.core.storage

import android.os.Build
import android.os.StatFs
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class AndroidFileSystem : IFileSystem {
    override fun getAvailableDiskSpace(path: String): Long {
        return try {
            val file = File(path)
            val dir = if (file.isDirectory) file else (file.parentFile ?: file)
            if (!dir.exists()) dir.mkdirs()
            val stat = StatFs(dir.absolutePath)
            stat.availableBytes
        } catch (_: Exception) {
            -1L
        }
    }

    override fun exists(path: String): Boolean {
        return File(path).exists()
    }

    override fun getFileSize(path: String): Long {
        val file = File(path)
        return if (file.exists()) file.length() else 0L
    }

    override fun createDirectories(path: String): Boolean {
        val file = File(path)
        val dir = if (file.isDirectory) file else file.parentFile
        return dir?.mkdirs() ?: false || (dir?.exists() == true)
    }

    override fun atomicMove(sourcePath: String, targetPath: String): Boolean {
        val source = File(sourcePath)
        val target = File(targetPath)
        if (!source.exists()) return false

        target.parentFile?.mkdirs()

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Files.move(
                    source.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
                true
            } else {
                if (target.exists()) target.delete()
                source.renameTo(target)
            }
        } catch (_: Exception) {
            // Fallback to standard rename or copy-and-delete
            if (target.exists()) target.delete()
            source.renameTo(target)
        }
    }

    override fun delete(path: String): Boolean {
        val file = File(path)
        return if (file.exists()) file.delete() else true
    }

    override fun preAllocate(path: String, sizeBytes: Long): Boolean {
        if (sizeBytes <= 0L) return true
        val file = File(path)
        file.parentFile?.mkdirs()
        return try {
            RandomAccessFile(file, "rw").use { raf ->
                raf.setLength(sizeBytes)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}

actual fun createDefaultFileSystem(): IFileSystem = AndroidFileSystem()
