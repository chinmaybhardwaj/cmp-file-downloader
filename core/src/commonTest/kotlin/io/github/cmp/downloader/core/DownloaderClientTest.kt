package io.github.cmp.downloader.core

import io.github.cmp.downloader.core.client.DownloaderClient
import io.github.cmp.downloader.core.model.DownloadRequest
import io.github.cmp.downloader.core.model.DownloadStatus
import io.github.cmp.downloader.core.model.Priority
import io.github.cmp.downloader.core.repository.InMemoryTaskRepository
import io.github.cmp.downloader.core.storage.IChecksumValidator
import io.github.cmp.downloader.core.storage.IFileSystem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DownloaderClientTest {

    @Test
    fun testEnqueueAndPriorityOrdering() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val repository = InMemoryTaskRepository()
        val client = DownloaderClient.Builder()
            .repository(repository)
            .fileSystem(FakeFileSystem())
            .coroutineScope(testScope)
            .maxConcurrentDownloads(1)
            .build()

        // Enqueue Low priority task
        val lowTaskId = client.enqueue(
            DownloadRequest.Builder("https://example.com/low.bin", "/downloads")
                .priority(Priority.LOW)
                .build()
        )

        // Enqueue Critical priority task
        val criticalTaskId = client.enqueue(
            DownloadRequest.Builder("https://example.com/critical.bin", "/downloads")
                .priority(Priority.CRITICAL)
                .build()
        )

        val allTasks = client.getAllTasks()
        assertEquals(2, allTasks.size)

        // Critical task should be sorted first in the queue due to priority weight
        assertEquals(criticalTaskId, allTasks[0].id)
        assertEquals(lowTaskId, allTasks[1].id)
    }

    @Test
    fun testPauseResumeCancelStateTransitions() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val repository = InMemoryTaskRepository()
        val client = DownloaderClient.Builder()
            .repository(repository)
            .fileSystem(FakeFileSystem())
            .coroutineScope(testScope)
            .build()

        val taskId = client.enqueue(
            DownloadRequest.Builder("https://example.com/movie.mp4", "/downloads").build()
        )

        var task = client.getTask(taskId)
        assertNotNull(task)
        assertEquals(DownloadStatus.QUEUED, task.status)
        assertTrue(task.destinationPath.endsWith("movie.mp4"))
        assertTrue(task.tempPath.contains(".part"))

        // Pause
        client.pause(taskId)
        task = client.getTask(taskId)
        assertNotNull(task)
        assertEquals(DownloadStatus.PAUSED, task.status)

        // Resume
        client.resume(taskId)
        task = client.getTask(taskId)
        assertNotNull(task)
        assertEquals(DownloadStatus.QUEUED, task.status)

        // Cancel
        client.cancel(taskId)
        task = client.getTask(taskId)
        assertNotNull(task)
        assertEquals(DownloadStatus.CANCELLED, task.status)
    }
}
