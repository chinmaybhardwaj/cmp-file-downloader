package io.github.cmp.downloader.core.storage

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Android implementation of [IRandomAccessFileWriter] using NIO [FileChannel.write]
 * with absolute position for thread-safe concurrent segmented writing.
 */
class AndroidRandomAccessFileWriter(filePath: String) : IRandomAccessFileWriter {
    private val raf: RandomAccessFile
    private val channel: FileChannel

    init {
        val file = File(filePath)
        file.parentFile?.mkdirs()
        raf = RandomAccessFile(file, "rw")
        channel = raf.channel
    }

    override fun write(fileOffset: Long, buffer: ByteArray, bufferOffset: Int, length: Int) {
        val byteBuffer = ByteBuffer.wrap(buffer, bufferOffset, length)
        var bytesWritten = 0
        var currentPosition = fileOffset
        while (byteBuffer.hasRemaining()) {
            val written = channel.write(byteBuffer, currentPosition)
            bytesWritten += written
            currentPosition += written
        }
    }

    override fun flush() {
        try {
            channel.force(false)
        } catch (_: Exception) {}
    }

    override fun close() {
        try {
            flush()
            channel.close()
            raf.close()
        } catch (_: Exception) {}
    }
}

actual fun createRandomAccessFileWriter(filePath: String): IRandomAccessFileWriter {
    return AndroidRandomAccessFileWriter(filePath)
}
