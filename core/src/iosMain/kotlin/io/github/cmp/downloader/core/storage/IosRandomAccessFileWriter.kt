package io.github.cmp.downloader.core.storage

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import platform.posix.O_CREAT
import platform.posix.O_RDWR
import platform.posix.close
import platform.posix.fsync
import platform.posix.open
import platform.posix.pwrite

/**
 * iOS implementation of [IRandomAccessFileWriter] using POSIX [pwrite]
 * for thread-safe concurrent segmented writing at absolute byte offsets.
 */
@OptIn(ExperimentalForeignApi::class)
class IosRandomAccessFileWriter(filePath: String) : IRandomAccessFileWriter {
    private val fd: Int

    init {
        // Ensure parent directories exist
        createDefaultFileSystem().createDirectories(filePath)
        fd = open(filePath, O_RDWR or O_CREAT, 420 /* 0644 octal */)
        if (fd < 0) {
            throw IllegalStateException("Failed to open file for random-access writing at: $filePath")
        }
    }

    override fun write(fileOffset: Long, buffer: ByteArray, bufferOffset: Int, length: Int) {
        if (length <= 0) return
        var remaining = length
        var currentOffset = fileOffset
        var currentBufOffset = bufferOffset

        while (remaining > 0) {
            val written = pwrite(
                fd,
                buffer.refTo(currentBufOffset),
                remaining.toULong(),
                currentOffset
            )
            if (written <= 0L) {
                throw IllegalStateException("pwrite failed to write bytes to file offset: $currentOffset")
            }
            remaining -= written.toInt()
            currentOffset += written
            currentBufOffset += written.toInt()
        }
    }

    override fun flush() {
        if (fd >= 0) {
            fsync(fd)
        }
    }

    override fun close() {
        if (fd >= 0) {
            flush()
            close(fd)
        }
    }
}

actual fun createRandomAccessFileWriter(filePath: String): IRandomAccessFileWriter {
    return IosRandomAccessFileWriter(filePath)
}
