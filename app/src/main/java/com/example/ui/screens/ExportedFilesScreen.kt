package com.example.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.accessibility.AccessibilityHelper
import com.example.accessibility.VibrationType
import com.example.audio.AudioPlayerManager
import com.example.data.ExportedMedia
import com.example.data.MediaRepository
import com.example.ui.theme.LocalHighContrast
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExportedFilesScreen(
    mediaRepository: MediaRepository,
    hapticsEnabled: Boolean,
    onNavigateToAudio: () -> Unit,
    onNavigateToVideo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isHighContrast = LocalHighContrast.current

    val files by mediaRepository.exportedFiles.collectAsState()
    val playerManager = remember { AudioPlayerManager(context) }

    DisposableEffect(Unit) {
        onDispose {
            playerManager.release()
        }
    }

    LaunchedEffect(Unit) {
        mediaRepository.refreshExports()
    }

    val isPlaying by playerManager.isPlaying.collectAsState()
    var currentPlayingPath by remember { mutableStateOf<String?>(null) }
    var itemToDelete by remember { mutableStateOf<ExportedMedia?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Library Header Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("exports_header_card"),
            colors = CardDefaults.cardColors(
                containerColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(
                if (isHighContrast) 2.dp else 1.dp,
                if (isHighContrast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isHighContrast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "SINGLE-FILE EXPORTS",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${files.size} Exported File${if (files.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        if (files.isEmpty()) {
            // Empty State
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
                    .testTag("empty_exports_card"),
                colors = CardDefaults.cardColors(
                    containerColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surfaceVariant
                ),
                border = BorderStroke(if (isHighContrast) 2.dp else 1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "No Exported Files Yet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "When you finish editing audio or video, export it as a single file. It will show up right here for instant playback and sharing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onNavigateToAudio,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_empty_go_audio"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.Audiotrack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Audio Studio")
                        }

                        OutlinedButton(
                            onClick = onNavigateToVideo,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_empty_go_video"),
                            border = BorderStroke(if (isHighContrast) 2.dp else 1.dp, MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Video Studio")
                        }
                    }
                }
            }
        } else {
            // List of exported media files
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(files, key = { it.id }) { item ->
                    val isCurrentPlaying = isPlaying && currentPlayingPath == item.filePath
                    val dateFormatted = remember(item.createdAtMillis) {
                        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US).format(Date(item.createdAtMillis))
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_item_${item.title}")
                            .semantics(mergeDescendants = true) {
                                contentDescription = "${if (item.isVideo) "Video" else "Audio"} file ${item.title}, duration ${item.durationSpoken}, size ${item.sizeFormatted}, created on $dateFormatted"
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            if (isHighContrast) 2.dp else 1.dp,
                            if (isCurrentPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Play/Pause button for previewing
                                    Button(
                                        onClick = {
                                            if (isCurrentPlaying) {
                                                playerManager.pause()
                                            } else {
                                                currentPlayingPath = item.filePath
                                                playerManager.loadUri(Uri.fromFile(File(item.filePath)))
                                                playerManager.play()
                                                if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.CLICK)
                                            }
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("btn_play_item_${item.title}")
                                            .semantics {
                                                contentDescription = if (isCurrentPlaying) "Pause playback of ${item.title}" else "Play preview of ${item.title}"
                                            },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isHighContrast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isCurrentPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                color = if (item.isVideo) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = if (item.isVideo) "VIDEO MP4" else "AUDIO WAV",
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp
                                                )
                                            }
                                            Text(
                                                text = "${item.durationFormatted}  •  ${item.sizeFormatted}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                // Actions: Share & Delete
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            mediaRepository.shareExport(item)
                                            if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.CLICK)
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("btn_share_${item.title}")
                                            .semantics {
                                                contentDescription = "Share file ${item.title}"
                                            }
                                    ) {
                                        Icon(
                                            Icons.Default.Share,
                                            contentDescription = null,
                                            tint = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    IconButton(
                                        onClick = { itemToDelete = item },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("btn_delete_${item.title}")
                                            .semantics {
                                                contentDescription = "Delete file ${item.title}"
                                            }
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    itemToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete Exported File?") },
            text = { Text("Are you sure you want to delete '${target.title}'? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            mediaRepository.deleteExport(target)
                            itemToDelete = null
                            if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.HEAVY_CLICK)
                            AccessibilityHelper.announceForAccessibility(context, "File deleted: ${target.title}")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { itemToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
