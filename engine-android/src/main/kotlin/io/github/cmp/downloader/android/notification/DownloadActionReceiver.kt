package io.github.cmp.downloader.android.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.cmp.downloader.core.client.DownloaderClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver triggered by user interaction on ongoing download notifications
 * (e.g. Pause, Resume, Cancel, Retry).
 */
class DownloadActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val action = intent.action ?: return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val client = try {
                    DownloaderClient.getInstance()
                } catch (_: Exception) {
                    null
                }

                if (client != null) {
                    when (action) {
                        ACTION_PAUSE -> client.pause(taskId)
                        ACTION_RESUME, ACTION_RETRY -> client.resume(taskId)
                        ACTION_CANCEL -> client.cancel(taskId)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "io.github.cmp.downloader.EXTRA_TASK_ID"
        const val ACTION_PAUSE = "io.github.cmp.downloader.ACTION_PAUSE"
        const val ACTION_RESUME = "io.github.cmp.downloader.ACTION_RESUME"
        const val ACTION_CANCEL = "io.github.cmp.downloader.ACTION_CANCEL"
        const val ACTION_RETRY = "io.github.cmp.downloader.ACTION_RETRY"
    }
}
