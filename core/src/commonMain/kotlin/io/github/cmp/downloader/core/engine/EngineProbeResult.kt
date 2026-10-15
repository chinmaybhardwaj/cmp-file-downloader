package io.github.cmp.downloader.core.engine

/**
 * Metadata received from probing the download target URL before transferring data.
 */
data class EngineProbeResult(
    val contentLength: Long, // -1L if unknown
    val supportsByteRanges: Boolean,
    val suggestedFileName: String?,
    val eTag: String?,
    val finalResolvedUrl: String,
    val responseHeaders: Map<String, String>
)
