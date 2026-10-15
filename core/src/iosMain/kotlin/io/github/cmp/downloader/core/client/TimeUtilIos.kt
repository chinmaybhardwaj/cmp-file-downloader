package io.github.cmp.downloader.core.client

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual fun currentTimeEpochMs(): Long {
    return (NSDate().timeIntervalSince1970 * 1000.0).toLong()
}
