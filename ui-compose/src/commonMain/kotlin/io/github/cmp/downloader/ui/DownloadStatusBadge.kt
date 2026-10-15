package io.github.cmp.downloader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cmp.downloader.core.model.DownloadStatus

@Composable
fun DownloadStatusBadge(
    status: DownloadStatus,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor, label) = when (status) {
        DownloadStatus.RUNNING -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "Downloading")
        DownloadStatus.CONNECTING -> Triple(Color(0xFFE3F2FD), Color(0xFF1565C0), "Connecting")
        DownloadStatus.QUEUED -> Triple(Color(0xFFEDE7F6), Color(0xFF512DA8), "Queued")
        DownloadStatus.PAUSED -> Triple(Color(0xFFFFF8E1), Color(0xFFF57F17), "Paused")
        DownloadStatus.COMPLETED -> Triple(Color(0xFFE0F2F1), Color(0xFF00695C), "Completed")
        DownloadStatus.FAILED -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "Failed")
        DownloadStatus.CANCELLED -> Triple(Color(0xFFEEEEEE), Color(0xFF616161), "Cancelled")
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
