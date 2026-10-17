package io.github.cmp.downloader.ios

import io.github.cmp.downloader.core.model.DownloadProgress
import io.github.cmp.downloader.core.model.DownloadStatus
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDownloadDelegateProtocol
import platform.Foundation.NSURLSessionDownloadTask
import platform.Foundation.NSURLSessionTask
import platform.darwin.NSObject

/**
 * Native iOS background session delegate implementing [NSURLSessionDownloadDelegateProtocol]
 * to receive callbacks from Apple's background transfer daemon (`nsurlsessiond`).
 */
@OptIn(ExperimentalForeignApi::class)
class IosBackgroundSessionDelegate(
    private val onProgressUpdate: (taskId: String, bytesDownloaded: Long, totalBytes: Long) -> Unit,
    private val onDownloadFinished: (taskId: String, tempFileUrl: NSURL) -> Unit,
    private val onDownloadError: (taskId: String, errorMessage: String?) -> Unit,
    private val onAllBackgroundEventsFinished: (sessionIdentifier: String) -> Unit
) : NSObject(), NSURLSessionDownloadDelegateProtocol {

    override fun URLSession(
        session: NSURLSession,
        downloadTask: NSURLSessionDownloadTask,
        didWriteData: Long,
        totalBytesWritten: Long,
        totalBytesExpectedToWrite: Long
    ) {
        val taskId = downloadTask.taskDescription ?: return
        onProgressUpdate(taskId, totalBytesWritten, totalBytesExpectedToWrite)
    }

    override fun URLSession(
        session: NSURLSession,
        downloadTask: NSURLSessionDownloadTask,
        didFinishDownloadingToURL: NSURL
    ) {
        val taskId = downloadTask.taskDescription ?: return
        onDownloadFinished(taskId, didFinishDownloadingToURL)
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didCompleteWithError: NSError?
    ) {
        val taskId = task.taskDescription ?: return
        if (didCompleteWithError != null) {
            onDownloadError(taskId, didCompleteWithError.localizedDescription)
        }
    }

    override fun URLSessionDidFinishEventsForBackgroundURLSession(session: NSURLSession) {
        val identifier = session.configuration.identifier ?: "default"
        onAllBackgroundEventsFinished(identifier)
    }
}
