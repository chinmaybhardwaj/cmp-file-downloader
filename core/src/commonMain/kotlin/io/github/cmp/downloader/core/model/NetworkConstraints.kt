package io.github.cmp.downloader.core.model

/**
 * Hardware and network constraints required before a task can start downloading.
 */
data class NetworkConstraints(
    /** If true, task will only run over unmetered connections (Wi-Fi/Ethernet). Cellular will be blocked. */
    val requiresUnmetered: Boolean = false,

    /** If true, task will only run when the device is plugged in and charging. */
    val requiresCharging: Boolean = false,

    /** If true, task will pause when device battery drops into low power mode. */
    val requiresBatteryNotLow: Boolean = true
) {
    companion object {
        val DEFAULT = NetworkConstraints()
        val WIFI_ONLY = NetworkConstraints(requiresUnmetered = true)
    }
}
