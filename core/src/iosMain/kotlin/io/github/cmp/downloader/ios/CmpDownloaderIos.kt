package io.github.cmp.downloader.ios

import io.github.cmp.downloader.core.client.DownloaderClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * Idiomatic entrypoint for iOS consumers (Swift / SwiftUI / UIKit)
 * to initialize the Downloader SDK and connect AppDelegate background session events.
 */
object CmpDownloaderIos {

    private var activeEngine: IosDownloadEngine? = null

    /**
     * Initializes the default global [DownloaderClient] with iOS background [NSURLSession] support.
     */
    fun initialize(
        sessionIdentifier: String = "io.github.cmp.downloader.background",
        configure: (DownloaderClient.Builder.() -> Unit)? = null
    ): DownloaderClient {
        val iosEngine = IosDownloadEngine(sessionIdentifier = sessionIdentifier)
        activeEngine = iosEngine

        val defaultDest = defaultDocumentsDirectory()
        val defaultTemp = "$defaultDest/.tmp"

        val builder = DownloaderClient.Builder()
            .engine(iosEngine)
            .defaultDestinationDirectory(defaultDest)
            .tempDirectory(defaultTemp)

        configure?.invoke(builder)

        val client = builder.build()
        runBlocking {
            DownloaderClient.initialize(client)
        }
        return client
    }

    /**
     * Forwards iOS background completion events from `AppDelegate.application(_:handleEventsForBackgroundURLSession:completionHandler:)`
     * or SwiftUI `onBackgroundURLSessionEvents`.
     */
    fun handleEventsForBackgroundURLSession(identifier: String, completionHandler: () -> Unit) {
        activeEngine?.setBackgroundCompletionHandler(identifier, completionHandler)
    }

    /**
     * Swift-friendly helper to observe progress with a trailing callback closure.
     * Returns a [Job] which can be cancelled to stop observing.
     */
    fun observeProgress(
        taskId: String,
        onProgress: (io.github.cmp.downloader.core.model.DownloadProgress) -> Unit
    ): Job {
        val client = DownloaderClient.getInstance()
        return CoroutineScope(Dispatchers.Main).launch {
            client.observeProgress(taskId).collect { progress ->
                onProgress(progress)
            }
        }
    }

    private fun defaultDocumentsDirectory(): String {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true
        )
        return (paths.firstOrNull() as? String) ?: "/tmp"
    }
}
