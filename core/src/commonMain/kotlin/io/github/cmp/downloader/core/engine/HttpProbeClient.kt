package io.github.cmp.downloader.core.engine

import io.github.cmp.downloader.core.model.DownloadTask
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

/**
 * Probes remote server to determine file size, range support, ETag, and suggested filename.
 */
class HttpProbeClient(
    private val httpClient: HttpClient = HttpClient()
) {
    suspend fun probe(task: DownloadTask): Result<EngineProbeResult> {
        return runCatching {
            var response = executeProbeRequest(task.request.url, task.request.headers, HttpMethod.Head)

            // If HEAD is rejected with 405 Method Not Allowed or 501 Not Implemented, fallback to GET range probe
            if (response.status == HttpStatusCode.MethodNotAllowed || response.status == HttpStatusCode.NotImplemented) {
                val rangeHeaders = task.request.headers + (HttpHeaders.Range to "bytes=0-0")
                response = executeProbeRequest(task.request.url, rangeHeaders, HttpMethod.Get)
            }

            if (!response.status.isSuccess() && response.status != HttpStatusCode.PartialContent) {
                throw IllegalStateException("Server returned HTTP ${response.status.value}: ${response.status.description}")
            }

            parseProbeResult(response)
        }
    }

    private suspend fun executeProbeRequest(
        url: String,
        customHeaders: Map<String, String>,
        method: HttpMethod
    ): HttpResponse {
        return httpClient.request(url) {
            this.method = method
            headers {
                customHeaders.forEach { (key, value) ->
                    append(key, value)
                }
            }
        }
    }

    private fun parseProbeResult(response: HttpResponse): EngineProbeResult {
        val headers = response.headers
        val acceptRanges = headers[HttpHeaders.AcceptRanges]?.lowercase() ?: ""
        val contentRange = headers[HttpHeaders.ContentRange] // e.g. "bytes 0-0/52428800"

        var totalBytes = headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L
        var supportsRanges = acceptRanges.contains("bytes")

        if (contentRange != null && contentRange.startsWith("bytes")) {
            supportsRanges = true
            val totalFromRange = contentRange.substringAfterLast('/').toLongOrNull()
            if (totalFromRange != null && totalFromRange > 0L) {
                totalBytes = totalFromRange
            }
        }

        if (response.status == HttpStatusCode.PartialContent) {
            supportsRanges = true
        }

        val eTag = headers[HttpHeaders.ETag]?.trim('"', ' ')
        val contentDisposition = headers[HttpHeaders.ContentDisposition]
        val suggestedFileName = extractFileNameFromContentDisposition(contentDisposition)

        val headerMap = mutableMapOf<String, String>()
        headers.forEach { name, values ->
            headerMap[name] = values.joinToString(", ")
        }

        return EngineProbeResult(
            contentLength = totalBytes,
            supportsByteRanges = supportsRanges,
            suggestedFileName = suggestedFileName,
            eTag = eTag,
            finalResolvedUrl = response.request.url.toString(),
            responseHeaders = headerMap
        )
    }

    private fun extractFileNameFromContentDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        // Match filename*="utf-8''name.ext" or filename="name.ext"
        val regex = Regex("""filename\*?=['"]?(?:UTF-\d['"]*)?([^'";\r\n]+)['"]?""", RegexOption.IGNORE_CASE)
        val match = regex.find(header)
        return match?.groupValues?.get(1)?.trim()
    }
}
