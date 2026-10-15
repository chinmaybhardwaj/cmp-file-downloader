package io.github.cmp.downloader.android.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.cmp.downloader.android.notification.DownloadNotificationManager
import io.github.cmp.downloader.core.client.DownloaderClient
import io.github.cmp.downloader.core.engine.SegmentedDownloadEngine
import io.github.cmp.downloader.core.model.DownloadStatus
import kotlinx.coroutines.CancellationException

/**
 * Background WorkManager CoroutineWorker that promotes to a Foreground Service with persistent notifications.
 */
class DownloadWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val notificationManager = DownloadNotificationManager(context)

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()

        val client = try {
            DownloaderClient.getInstance()
        } catch (_: Exception) {
            return Result.failure()
        }

        val task = client.getTask(taskId) ?: return Result.failure()
        val notificationId = (taskId.hashCode() and 0x7FFFFFFF).coerceAtLeast(1)
        val fileName = task.request.fileName ?: task.destinationPath.substringAfterLast('/')

        // Promote to Foreground Service immediately to ensure execution continuity across app kills
        val initialProgress = task.toProgress()
        val foregroundInfo = notificationManager.createForegroundInfo(
            notificationId = notificationId,
            taskId = taskId,
            fileName = fileName,
            progress = initialProgress
        )
        setForeground(foregroundInfo)

        val engine = SegmentedDownloadEngine()

        return try {
            var completed = false
            var failureMessage: String? = null

            engine.start(task).collect { progress ->
                when (progress.status) {
                    DownloadStatus.CONNECTING, DownloadStatus.RUNNING -> {
                        val progressForegroundInfo = notificationManager.createForegroundInfo(
                            notificationId = notificationId,
                            taskId = taskId,
                            fileName = fileName,
                            progress = progress
                        )
                        setForeground(progressForegroundInfo)
                    }
                    DownloadStatus.COMPLETED -> {
                        completed = true
                        notificationManager.notifyCompleted(
                            notificationId = notificationId,
                            fileName = fileName,
                            totalBytes = progress.totalBytes
                        )
                    }
                    DownloadStatus.FAILED -> {
                        failureMessage = "Download failed"
                        notificationManager.notifyFailed(
                            notificationId = notificationId,
                            taskId = taskId,
                            fileName = fileName,
                            reason = failureMessage
                        )
                    }
                    DownloadStatus.PAUSED -> {
                        notificationManager.cancelNotification(notificationId)
                    }
                    DownloadStatus.CANCELLED -> {
                        notificationManager.cancelNotification(notificationId)
                    }
                    DownloadStatus.QUEUED -> {}
                }
            }

            if (completed) {
                Result.success()
            } else if (failureMessage != null) {
                Result.retry()
            } else {
                Result.failure()
            }
        } catch (e: CancellationException) {
            notificationManager.cancelNotification(notificationId)
            Result.success()
        } catch (e: Exception) {
            notificationManager.notifyFailed(
                notificationId = notificationId,
                taskId = taskId,
                fileName = fileName,
                reason = e.message
            )
            Result.retry()
        }
    }

    companion object {
        const val KEY_TASK_ID = "KEY_TASK_ID"
    }
}
