package io.github.cmp.downloader.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.cmp.downloader.android.CmpDownloader

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Android Downloader Engine with WorkManager and Notifications
        val client = CmpDownloader.initialize(this) {
            maxConcurrentDownloads(3)
            maxSegmentsPerFile(4)
        }

        setContent {
            App(downloaderClient = client)
        }
    }
}
