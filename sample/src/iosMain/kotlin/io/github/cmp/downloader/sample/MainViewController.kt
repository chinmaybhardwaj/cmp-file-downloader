package io.github.cmp.downloader.sample

import androidx.compose.ui.window.ComposeUIViewController
import io.github.cmp.downloader.ios.CmpDownloaderIos
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    val client = CmpDownloaderIos.initialize(sessionIdentifier = "io.github.cmp.downloader.sample") {
        maxConcurrentDownloads(3)
        maxSegmentsPerFile(4)
    }
    return ComposeUIViewController {
        App(downloaderClient = client)
    }
}
