package io.github.cmp.downloader.core.storage

/**
 * Thread-safe random-access file writer interface for concurrent multi-segmented chunk writing.
 * Allows multiple coroutines/threads to write into different byte ranges of the same file simultaneously.
 */
interface IRandomAccessFileWriter : AutoCloseable {
    /**
     * Writes [length] bytes from [buffer] starting at [bufferOffset] into the file at [fileOffset].
     */
    fun write(fileOffset: Long, buffer: ByteArray, bufferOffset: Int, length: Int)

    /**
     * Flushes buffered bytes to persistent storage.
     */
    fun flush()
}

/**
 * Factory for creating platform-specific random access file writer.
 */
expect fun createRandomAccessFileWriter(filePath: String): IRandomAccessFileWriter
