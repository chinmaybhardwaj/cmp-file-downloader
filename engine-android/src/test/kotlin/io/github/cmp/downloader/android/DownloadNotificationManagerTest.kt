package io.github.cmp.downloader.android

import io.github.cmp.downloader.android.notification.DownloadNotificationManager
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadNotificationManagerTest {

    @Test
    fun testNotificationChannelConstants() {
        assertEquals("cmp_file_downloader_channel", DownloadNotificationManager.CHANNEL_ID)
        assertEquals("File Downloads", DownloadNotificationManager.CHANNEL_NAME)
    }
}
