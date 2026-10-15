package io.github.cmp.downloader.core

import io.github.cmp.downloader.core.engine.ChunkPartitioner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChunkPartitionerTest {

    @Test
    fun testPartitionSingleChunkWhenNoRangeSupport() {
        val chunks = ChunkPartitioner.partition(
            taskId = "task-1",
            totalBytes = 100L * 1024L * 1024L, // 100 MB
            maxSegments = 4,
            supportsByteRanges = false
        )

        assertEquals(1, chunks.size)
        assertEquals(0L, chunks[0].startByte)
        assertEquals((100L * 1024L * 1024L) - 1L, chunks[0].endByte)
        assertEquals(0L, chunks[0].currentByte)
    }

    @Test
    fun testPartitionSingleChunkWhenIndeterminateSize() {
        val chunks = ChunkPartitioner.partition(
            taskId = "task-1",
            totalBytes = -1L,
            maxSegments = 4,
            supportsByteRanges = true
        )

        assertEquals(1, chunks.size)
        assertEquals(0L, chunks[0].startByte)
        assertEquals(Long.MAX_VALUE, chunks[0].endByte)
    }

    @Test
    fun testPartitionBelowThresholdReturnsSingleChunk() {
        // 2 MB file with 5 MB default threshold -> should not segment
        val chunks = ChunkPartitioner.partition(
            taskId = "task-1",
            totalBytes = 2L * 1024L * 1024L,
            maxSegments = 4,
            supportsByteRanges = true
        )

        assertEquals(1, chunks.size)
        assertEquals(0L, chunks[0].startByte)
        assertEquals((2L * 1024L * 1024L) - 1L, chunks[0].endByte)
    }

    @Test
    fun testPartitionDividesEquallyAndCoversFullRange() {
        val totalBytes = 100L * 1024L * 1024L // 100 MB
        val maxSegments = 4

        val chunks = ChunkPartitioner.partition(
            taskId = "task-multi",
            totalBytes = totalBytes,
            maxSegments = maxSegments,
            supportsByteRanges = true
        )

        assertEquals(4, chunks.size)

        // Verify contiguous byte coverage without overlaps or gaps
        var expectedNextByte = 0L
        for (chunk in chunks) {
            assertEquals(expectedNextByte, chunk.startByte)
            assertTrue(chunk.endByte >= chunk.startByte)
            expectedNextByte = chunk.endByte + 1L
        }

        // Verify last chunk ends at totalBytes - 1
        assertEquals(totalBytes, expectedNextByte)
    }
}
