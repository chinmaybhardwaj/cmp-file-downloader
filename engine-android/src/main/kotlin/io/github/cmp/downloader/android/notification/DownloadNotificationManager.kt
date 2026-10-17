package io.github.cmp.downloader.android.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import io.github.cmp.downloader.core.model.DownloadProgress
import io.github.cmp.downloader.core.model.DownloadStatus

/**
 * Manages system notification channels and builds ongoing foreground notifications
 * with real-time progress bars, download speed, ETA, and Pause/Resume/Cancel actions.
 */
class DownloadNotificationManager(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW // Low importance avoids alerting sound on every progress tick
            ).apply {
                description = "Shows real-time progress for active file downloads"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Builds [ForegroundInfo] required by WorkManager to promote worker to a Foreground Service.
     */
    fun createForegroundInfo(
        notificationId: Int,
        taskId: String,
        fileName: String,
        progress: DownloadProgress
    ): ForegroundInfo {
        val notification = buildProgressNotification(taskId, fileName, progress)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    /**
     * Builds ongoing foreground notification with real-time progress and actions.
     */
    fun buildProgressNotification(
        taskId: String,
        fileName: String,
        progress: DownloadProgress
    ): Notification {
        val contentText = buildString {
            append("${progress.formattedDownloadedBytes} / ${progress.formattedTotalBytes}")
            if (progress.speedBytesPerSec > 0L) {
                append(" • ${progress.formattedSpeed}")
            }
            if (progress.estimatedTimeRemainingSeconds != null && progress.estimatedTimeRemainingSeconds!! > 0L) {
                append(" • ETA: ${progress.formattedEta}")
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(fileName)
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(
                100,
                progress.progressPercentage.toInt(),
                progress.isIndeterminate
            )

        // Interactive action buttons
        if (progress.status == DownloadStatus.RUNNING) {
            val pauseIntent = createActionPendingIntent(taskId, DownloadActionReceiver.ACTION_PAUSE)
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", pauseIntent)
        } else if (progress.status == DownloadStatus.PAUSED) {
            val resumeIntent = createActionPendingIntent(taskId, DownloadActionReceiver.ACTION_RESUME)
            builder.addAction(android.R.drawable.ic_media_play, "Resume", resumeIntent)
        }

        val cancelIntent = createActionPendingIntent(taskId, DownloadActionReceiver.ACTION_CANCEL)
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent)

        return builder.build()
    }

    /**
     * Posts completion notification.
     */
    fun notifyCompleted(notificationId: Int, fileName: String, totalBytes: Long) {
        val bytesStr = DownloadProgress.formatBytes(totalBytes)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download Complete")
            .setContentText("$fileName ($bytesStr)")
            .setAutoCancel(true)
            .setOngoing(false)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    /**
     * Posts failure notification with Retry action.
     */
    fun notifyFailed(notificationId: Int, taskId: String, fileName: String, reason: String?) {
        val retryIntent = createActionPendingIntent(taskId, DownloadActionReceiver.ACTION_RETRY)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download Failed")
            .setContentText(reason ?: "Could not complete $fileName")
            .setAutoCancel(true)
            .setOngoing(false)
            .addAction(android.R.drawable.ic_menu_rotate, "Retry", retryIntent)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    private fun createActionPendingIntent(taskId: String, action: String): PendingIntent {
        val intent = Intent(context, DownloadActionReceiver::class.java).apply {
            this.action = action
            putExtra(DownloadActionReceiver.EXTRA_TASK_ID, taskId)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(
            context,
            (taskId + action).hashCode(),
            intent,
            flags
        )
    }

    companion object {
        const val CHANNEL_ID = "cmp_file_downloader_channel"
        const val CHANNEL_NAME = "File Downloads"
    }
}
