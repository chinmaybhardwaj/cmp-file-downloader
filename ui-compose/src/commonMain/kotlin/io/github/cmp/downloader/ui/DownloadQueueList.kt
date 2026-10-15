package io.github.cmp.downloader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cmp.downloader.core.client.DownloaderClient
import io.github.cmp.downloader.core.model.DownloadTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun DownloadQueueList(
    downloaderClient: DownloaderClient,
    coroutineScope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    val tasks by downloaderClient.observeAllTasks().collectAsState(initial = emptyList())

    if (tasks.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = DownloaderIcons.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Active Downloads",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Enqueued downloads will appear here",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(tasks, key = { it.id }) { task ->
                DownloadItemRow(
                    task = task,
                    downloaderClient = downloaderClient,
                    coroutineScope = coroutineScope
                )
            }
        }
    }
}

@Composable
private fun DownloadItemRow(
    task: DownloadTask,
    downloaderClient: DownloaderClient,
    coroutineScope: CoroutineScope
) {
    val progress by downloaderClient.observeProgress(task.id).collectAsState(initial = task.toProgress())

    DownloadItemCard(
        task = task,
        progress = progress,
        onPause = { id ->
            coroutineScope.launch { downloaderClient.pause(id) }
        },
        onResume = { id ->
            coroutineScope.launch { downloaderClient.resume(id) }
        },
        onCancel = { id ->
            coroutineScope.launch { downloaderClient.cancel(id) }
        },
        onDelete = { id ->
            coroutineScope.launch { downloaderClient.delete(id) }
        }
    )
}
