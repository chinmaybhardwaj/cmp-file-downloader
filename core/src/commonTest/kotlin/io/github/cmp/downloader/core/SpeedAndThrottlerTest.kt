package io.github.cmp.downloader.core

import io.github.cmp.downloader.core.engine.ProgressThrottler
import io.github.cmp.downloader.core.engine.SpeedCalculator
import io.github.cmp.downloader.core.model.DownloadProgress
import io.github.cmp.downloader.core.model.DownloadStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpeedAndThrottlerTest {

    @Test
    fun testSpeedCalculatorInitialUpdateReturnsZeroSpeed() {
        val calculator = SpeedCalculator()
        val (speed, eta) = calculator.update(currentBytes = 1000L, totalBytes = 10000L, currentTimeMs = 1000L)
        assertEquals(0L, speed)
        assertNull(eta)
    }

    @Test
    fun testSpeedCalculatorCalculatesAccurateSpeedAndEta() {
        val calculator = SpeedCalculator(smoothingFactor = 1.0) // 1.0 = immediate response

        // First tick at t = 1000ms with 0 bytes
        calculator.update(currentBytes = 0L, totalBytes = 10000L, currentTimeMs = 1000L)

        // Second tick at t = 2000ms (1 sec later) with 2000 bytes -> speed = 2000 B/s
        val (speed, eta) = calculator.update(currentBytes = 2000L, totalBytes = 10000L, currentTimeMs = 2000L)

        assertEquals(2000L, speed)
        assertNotNull(eta)
        // Remaining bytes = 8000. At 2000 B/s -> ETA = 4 seconds
        assertEquals(4L, eta)
    }

    @Test
    fun testProgressThrottlerSuppressesRapidTicks() {
        val throttler = ProgressThrottler(throttleIntervalMs = 150L, minimumPercentageDelta = 1.0f)

        val progress1 = DownloadProgress.initial("task-1", "https://example.com/file.bin")
            .copy(bytesDownloaded = 100L, totalBytes = 10000L, progressPercentage = 1.0f, status = DownloadStatus.RUNNING)

        // First event at t = 1000ms -> should emit
        assertTrue(throttler.shouldEmit(progress1, currentTimeMs = 1000L))

        // Rapid tick 50ms later with negligible progress -> should NOT emit
        val progress2 = progress1.copy(bytesDownloaded = 105L, progressPercentage = 1.05f)
        assertFalse(throttler.shouldEmit(progress2, currentTimeMs = 1050L))

        // Tick after interval expired (t = 1200ms, elapsed = 200ms) -> should emit
        val progress3 = progress1.copy(bytesDownloaded = 150L, progressPercentage = 1.5f)
        assertTrue(throttler.shouldEmit(progress3, currentTimeMs = 1200L))
    }

    @Test
    fun testProgressThrottlerImmediatelyPassesStateTransitions() {
        val throttler = ProgressThrottler(throttleIntervalMs = 5000L) // high interval

        val running = DownloadProgress.initial("task-1", "https://example.com/file.bin")
            .copy(status = DownloadStatus.RUNNING)
        assertTrue(throttler.shouldEmit(running, currentTimeMs = 1000L))

        // Just 10ms later, state changes to PAUSED -> must emit immediately!
        val paused = running.copy(status = DownloadStatus.PAUSED)
        assertTrue(throttler.shouldEmit(paused, currentTimeMs = 1010L))

        // Another 10ms later, state changes to COMPLETED -> must emit immediately!
        val completed = paused.copy(status = DownloadStatus.COMPLETED)
        assertTrue(throttler.shouldEmit(completed, currentTimeMs = 1020L))
    }
}
