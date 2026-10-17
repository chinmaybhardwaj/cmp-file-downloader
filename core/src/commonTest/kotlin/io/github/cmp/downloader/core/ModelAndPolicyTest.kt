package io.github.cmp.downloader.core

import io.github.cmp.downloader.core.model.ChecksumAlgorithm
import io.github.cmp.downloader.core.model.CollisionStrategy
import io.github.cmp.downloader.core.model.DownloadProgress
import io.github.cmp.downloader.core.model.DownloadRequest
import io.github.cmp.downloader.core.model.DownloadStatus
import io.github.cmp.downloader.core.model.Priority
import io.github.cmp.downloader.core.model.RetryPolicy
import io.github.cmp.downloader.core.storage.DiskSpaceValidator
import io.github.cmp.downloader.core.storage.IFileSystem
import io.github.cmp.downloader.core.storage.InsufficientStorageException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ModelAndPolicyTest {

    @Test
    fun testRetryPolicyExponentialBackoff() {
        val policy = RetryPolicy(
            maxRetries = 4,
            initialBackoffMs = 1000L,
            backoffMultiplier = 2.0,
            maxBackoffMs = 10000L
        )

        assertEquals(0L, policy.calculateDelayMs(0))
        assertEquals(1000L, policy.calculateDelayMs(1))
        assertEquals(2000L, policy.calculateDelayMs(2))
        assertEquals(4000L, policy.calculateDelayMs(3))
        assertEquals(8000L, policy.calculateDelayMs(4))
        // Capped at maxBackoffMs (10,000)
        assertEquals(10000L, policy.calculateDelayMs(5))
    }

    @Test
    fun testDownloadProgressFormatting() {
        assertEquals("0 B", DownloadProgress.formatBytes(0L))
        assertEquals("512 B", DownloadProgress.formatBytes(512L))
        assertEquals("1.5 KB", DownloadProgress.formatBytes(1536L))
        assertEquals("2.5 GB", DownloadProgress.formatBytes((2.5 * 1024 * 1024 * 1024).toLong()))

        val progress = DownloadProgress(
            taskId = "task-1",
            url = "https://example.com/file.zip",
            bytesDownloaded = 1024 * 1024 * 50,
            totalBytes = 1024 * 1024 * 100,
            progressPercentage = 50.0f,
            speedBytesPerSec = 1024 * 1024 * 5, // 5 MB/s
            estimatedTimeRemainingSeconds = 10L,
            status = DownloadStatus.RUNNING
        )

        assertEquals("50 MB", progress.formattedDownloadedBytes)
        assertEquals("100 MB", progress.formattedTotalBytes)
        assertEquals("5 MB/s", progress.formattedSpeed)
        assertEquals("10s", progress.formattedEta)
    }

    @Test
    fun testDiskSpaceValidatorPassesWhenSpaceIsAdequate() {
        val fakeFs = object : FakeFileSystem(availableSpace = 500L * 1024 * 1024) {} // 500 MB
        val validator = DiskSpaceValidator(fakeFs, safetyMarginBytes = 50L * 1024 * 1024) // 50 MB margin

        // 200 MB file + 50 MB margin = 250 MB required. 500 MB available -> should pass without throwing
        validator.validateStorage("/downloads", contentLength = 200L * 1024 * 1024)
    }

    @Test
    fun testDiskSpaceValidatorThrowsWhenSpaceIsInsufficient() {
        val fakeFs = object : FakeFileSystem(availableSpace = 100L * 1024 * 1024) {} // 100 MB
        val validator = DiskSpaceValidator(fakeFs, safetyMarginBytes = 50L * 1024 * 1024) // 50 MB margin

        // 80 MB file + 50 MB margin = 130 MB required > 100 MB available -> throws InsufficientStorageException
        assertFailsWith<InsufficientStorageException> {
            validator.validateStorage("/downloads", contentLength = 80L * 1024 * 1024)
        }
    }

    @Test
    fun testDownloadRequestBuilderValidation() {
        assertFailsWith<IllegalArgumentException> {
            DownloadRequest.Builder("", "/downloads").build()
        }

        assertFailsWith<IllegalArgumentException> {
            DownloadRequest.Builder("https://example.com/file", "").build()
        }

        val request = DownloadRequest.Builder("https://example.com/file.zip", "/downloads")
            .priority(Priority.HIGH)
            .collisionStrategy(CollisionStrategy.OVERWRITE)
            .checksum("abc123hash", ChecksumAlgorithm.SHA256)
            .maxSegments(8)
            .build()

        assertEquals("https://example.com/file.zip", request.url)
        assertEquals("/downloads", request.destinationDirectory)
        assertEquals(Priority.HIGH, request.priority)
        assertEquals(CollisionStrategy.OVERWRITE, request.collisionStrategy)
        assertEquals(ChecksumAlgorithm.SHA256, request.checksumAlgorithm)
        assertEquals(8, request.maxSegments)
    }
}

open class FakeFileSystem(var availableSpace: Long = 1000L * 1024 * 1024) : IFileSystem {
    override fun getAvailableDiskSpace(path: String): Long = availableSpace
    override fun exists(path: String): Boolean = false
    override fun getFileSize(path: String): Long = 0L
    override fun createDirectories(path: String): Boolean = true
    override fun atomicMove(sourcePath: String, targetPath: String): Boolean = true
    override fun delete(path: String): Boolean = true
    override fun preAllocate(path: String, sizeBytes: Long): Boolean = true
}
