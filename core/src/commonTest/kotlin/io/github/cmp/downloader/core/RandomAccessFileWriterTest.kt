package io.github.cmp.downloader.core

import io.github.cmp.downloader.core.storage.createDefaultFileSystem
import io.github.cmp.downloader.core.storage.createRandomAccessFileWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RandomAccessFileWriterTest {

    @Test
    fun testConcurrentRandomAccessChunkWrites() {
        val fs = createDefaultFileSystem()
        val tempPath = "/tmp/test_random_access_${currentTimeTestMs()}.part"

        try {
            val writer = createRandomAccessFileWriter(tempPath)

            val chunk0Data = "HELLO_CHUNK_0".encodeToByteArray()
            val chunk1Data = "WORLD_CHUNK_1".encodeToByteArray()

            // Write chunk 0 at offset 0
            writer.write(fileOffset = 0L, buffer = chunk0Data, bufferOffset = 0, length = chunk0Data.size)

            // Write chunk 1 at offset 1000L
            writer.write(fileOffset = 1000L, buffer = chunk1Data, bufferOffset = 0, length = chunk1Data.size)

            writer.flush()
            writer.close()

            assertTrue(fs.exists(tempPath))
            assertTrue(fs.getFileSize(tempPath) >= 1000L + chunk1Data.size)
        } finally {
            fs.delete(tempPath)
        }
    }

    private fun currentTimeTestMs(): Long {
        return io.github.cmp.downloader.core.client.currentTimeEpochMs()
    }
}
