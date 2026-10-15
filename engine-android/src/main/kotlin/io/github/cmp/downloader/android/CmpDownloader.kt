package io.github.cmp.downloader.android

import android.content.Context
import io.github.cmp.downloader.core.client.DownloaderClient
import io.github.cmp.downloader.core.client.DownloaderConfig
import kotlinx.coroutines.runBlocking

/**
 * Android initialization helper for setting up [DownloaderClient] with [AndroidDownloadEngine],
 * WorkManager, and background notifications.
 */
object CmpDownloader {

    /**
     * Initializes the default global [DownloaderClient] with Android WorkManager and notification support.
     */
    fun initialize(
        context: Context,
        configure: (DownloaderClient.Builder.() -> Unit)? = null
    ): DownloaderClient {
        val appContext = context.applicationContext
        val androidEngine = AndroidDownloadEngine(appContext)

        val defaultDest = appContext.getExternalFilesDir(null)?.absolutePath
            ?: appContext.filesDir.absolutePath
        val defaultTemp = appContext.cacheDir.absolutePath

        val builder = DownloaderClient.Builder()
            .engine(androidEngine)
            .defaultDestinationDirectory(defaultDest)
            .tempDirectory(defaultTemp)

        configure?.invoke(builder)

        val client = builder.build()
        runBlocking {
            DownloaderClient.initialize(client)
        }
        return client
    }
}
