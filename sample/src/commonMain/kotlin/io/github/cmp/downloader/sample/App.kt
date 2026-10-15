package io.github.cmp.downloader.sample

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cmp.downloader.core.client.DownloaderClient
import io.github.cmp.downloader.core.model.CollisionStrategy
import io.github.cmp.downloader.core.model.DownloadRequest
import io.github.cmp.downloader.core.model.Priority
import io.github.cmp.downloader.ui.DownloaderIcons
import io.github.cmp.downloader.ui.DownloadQueueList
import kotlinx.coroutines.launch

private val SampleColorScheme = lightColorScheme(
    primary = Color(0xFF2563EB), // Sleek Royal Blue
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E40AF),
    secondary = Color(0xFF0D9488), // Teal
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF64748B)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(downloaderClient: DownloaderClient = DownloaderClient.getInstance()) {
    val coroutineScope = rememberCoroutineScope()
    var urlInput by remember { mutableStateOf("https://proof.ovh.net/files/10Mb.dat") }
    var fileNameInput by remember { mutableStateOf("test_10mb.dat") }
    var showCustomInput by remember { mutableStateOf(false) }

    MaterialTheme(colorScheme = SampleColorScheme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        brush = Brush.linearGradient(
                                            listOf(Color(0xFF2563EB), Color(0xFF0D9488))
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = DownloaderIcons.Download,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "CMP File Downloader",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = "Multi-segmented • >2GB • Background Resilient",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Quick Action Presets Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Quick Presets & Tests",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        downloaderClient.enqueue(
                                            DownloadRequest.Builder(
                                                url = "https://proof.ovh.net/files/10Mb.dat",
                                                destinationDirectory = "/tmp/downloads"
                                            )
                                                .fileName("sample_10mb.dat")
                                                .tag("Network Test (10 MB)")
                                                .priority(Priority.HIGH)
                                                .maxSegments(4)
                                                .build()
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = ButtonDefaults.TextButtonContentPadding
                            ) {
                                Icon(DownloaderIcons.Flash, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("10 MB (4 Segments)", fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        // Enqueue 3 tasks simultaneously to test parallel concurrency pool
                                        repeat(3) { index ->
                                            downloaderClient.enqueue(
                                                DownloadRequest.Builder(
                                                    url = "https://proof.ovh.net/files/10Mb.dat",
                                                    destinationDirectory = "/tmp/downloads"
                                                )
                                                    .fileName("batch_file_${index + 1}.dat")
                                                    .tag("Parallel Batch #${index + 1}")
                                                    .priority(if (index == 0) Priority.CRITICAL else Priority.NORMAL)
                                                    .maxSegments(2)
                                                    .build()
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = ButtonDefaults.TextButtonContentPadding
                            ) {
                                Icon(DownloaderIcons.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Batch (3 Files)", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { showCustomInput = !showCustomInput },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(DownloaderIcons.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (showCustomInput) "Hide Custom URL" else "Enqueue Custom URL", fontSize = 12.sp)
                        }

                        AnimatedVisibility(visible = showCustomInput) {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                OutlinedTextField(
                                    value = urlInput,
                                    onValueChange = { urlInput = it },
                                    label = { Text("Download URL") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = fileNameInput,
                                    onValueChange = { fileNameInput = it },
                                    label = { Text("Destination Filename") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            downloaderClient.enqueue(
                                                DownloadRequest.Builder(
                                                    url = urlInput,
                                                    destinationDirectory = "/tmp/downloads"
                                                )
                                                    .fileName(fileNameInput)
                                                    .priority(Priority.NORMAL)
                                                    .maxSegments(4)
                                                    .collisionStrategy(CollisionStrategy.AUTO_RENAME)
                                                    .build()
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Start Download")
                                }
                            }
                        }
                    }
                }

                // Download Queue List
                DownloadQueueList(
                    downloaderClient = downloaderClient,
                    coroutineScope = coroutineScope,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
