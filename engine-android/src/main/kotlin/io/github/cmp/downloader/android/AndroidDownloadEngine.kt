package io.github.cmp.downloader.android

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.cmp.downloader.android.worker.DownloadWorker
import io.github.cmp.downloader.core.engine.EngineProbeResult
import io.github.cmp.downloader.core.engine.IDownloadEngine
import io.github.cmp.downloader.core.engine.SegmentedDownloadEngine
import io.github.cmp.downloader.core.model.DownloadProgress
import io.github.cmp.downloader.core.model.DownloadTask
import io.github.cmp.downloader.core.model.NetworkConstraints
import kotlinx.coroutines.flow.Flow

/**
 * Android-specific download engine that integrates Android [WorkManager]
 * with Foreground Service progress notifications.
 */
class AndroidDownloadEngine(
    private val context: Context,
    private val directEngine: SegmentedDownloadEngine = SegmentedDownloadEngine()
) : IDownloadEngine {

    private val workManager = WorkManager.getInstance(context)

    override suspend fun probe(task: DownloadTask): Result<EngineProbeResult> {
        return directEngine.probe(task)
    }

    override fun start(task: DownloadTask): Flow<DownloadProgress> {
        scheduleWorkManager(task)
        return directEngine.start(task)
    }

    override suspend fun pause(taskId: String): Result<Unit> {
        workManager.cancelUniqueWork(workTag(taskId))
        return directEngine.pause(taskId)
    }

    override fun resume(task: DownloadTask): Flow<DownloadProgress> {
        scheduleWorkManager(task)
        return directEngine.resume(task)
    }

    override suspend fun cancel(taskId: String): Result<Unit> {
        workManager.cancelUniqueWork(workTag(taskId))
        return directEngine.cancel(taskId)
    }

    private fun scheduleWorkManager(task: DownloadTask) {
        val constraints = buildWorkConstraints(task.request.networkConstraints)
        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(DownloadWorker.KEY_TASK_ID to task.id))
            .addTag(workTag(task.id))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        workManager.enqueueUniqueWork(
            workTag(task.id),
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    private fun buildWorkConstraints(constraints: NetworkConstraints): Constraints {
        val networkType = if (constraints.requiresUnmetered) {
            NetworkType.UNMETERED
        } else {
            NetworkType.CONNECTED
        }

        return Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .setRequiresCharging(constraints.requiresCharging)
            .setRequiresBatteryNotLow(constraints.requiresBatteryNotLow)
            .build()
    }

    private fun workTag(taskId: String): String = "download_$taskId"
}
