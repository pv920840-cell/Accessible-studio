package com.example.ui.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.accessibility.AccessibilityHelper
import com.example.ui.theme.LocalHighContrast
import com.example.video.VideoStudioFeatureButton
import kotlinx.coroutines.flow.StateFlow

@Composable
fun ProjectHubScreen(
    project: StudioProjectSummary,
    durationMs: Long,
    currentPositionMs: Long,
    isPlaying: Boolean,
    hasUnsavedChanges: Boolean,
    onTogglePreviewPlay: () -> Unit,
    onOpenAudioTool: (AudioFeatureTool) -> Unit,
    onOpenVideoTool: (VideoFeatureTool) -> Unit,
    onExportProject: () -> Unit,
    onBackToHome: () -> Unit,
    onSaveProject: () -> Unit = {},
    modifier: Modifier = Modifier,
    positionFlow: StateFlow<Long>? = null,
    onOpenVideoFeature: (VideoStudioFeatureButton) -> Unit = {},
    onOpenBatchProcessing: () -> Unit = {}
) {
    val isHighContrast = LocalHighContrast.current
    val isVideo = project.mediaType == ProjectMediaType.VIDEO

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (isHighContrast) Color.Black else MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("project_hub_header_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    if (isHighContrast) 2.dp else 1.dp,
                    if (isHighContrast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onSaveProject,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("btn_hub_save")
                                .semantics {
                                    contentDescription = "Save Project"
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                contentColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Save Project",
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = onBackToHome,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("btn_hub_back_home")
                                .semantics {
                                    contentDescription = "Go back to Home Screen"
                                },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                if (isHighContrast) 2.dp else 1.dp,
                                if (isHighContrast) Color.White else MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Go Back to Home Screen",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = if (isVideo) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (isVideo) "VIDEO PROJECT" else "AUDIO PROJECT",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Surface(
                                    color = if (hasUnsavedChanges) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.testTag("hub_status_badge")
                                ) {
                                    val statusText = when {
                                        hasUnsavedChanges -> "Edited"
                                        project.statusBadge == "Saved" -> "Saved"
                                        else -> "Ready"
                                    }
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = project.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .testTag("hub_project_title")
                                    .semantics { heading() }
                            )
                            ProjectHubTimeText(
                                currentPositionMs = currentPositionMs,
                                durationMs = durationMs,
                                isPlaying = isPlaying,
                                positionFlow = positionFlow
                            )
                        }

                        Button(
                            onClick = onTogglePreviewPlay,
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("btn_hub_preview_toggle")
                                .semantics {
                                    contentDescription = if (isPlaying) "Pause preview" else "Play project preview"
                                }
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                        }
                    }
                }
            }

            LazyVerticalGrid(
                columns = if (isVideo) GridCells.Adaptive(minSize = 160.dp) else GridCells.Fixed(1),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("hub_features_grid"),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = if (isVideo) "Video Editing Tools" else "Audio Editor Tools",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .semantics { heading() }
                    )
                }

                if (isVideo) {
                    items(VideoFeatureTool.values().toList(), key = { it.name }) { tool ->
                        FeatureHubCard(
                            title = tool.title,
                            subtitle = tool.subtitle,
                            icon = videoToolIcon(tool),
                            tag = tool.tag,
                            isHighContrast = isHighContrast,
                            accentPrimary = false,
                            onClick = { onOpenVideoTool(tool) }
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "Video Studio Feature Buttons",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier
                                .padding(top = 6.dp, bottom = 2.dp)
                                .testTag("heading_hub_video_features")
                                .semantics { heading() }
                        )
                    }
                    items(VideoStudioFeatureButton.entries, key = { "vid_feat_${it.name}" }) { feat ->
                        OutlinedButton(
                            onClick = { onOpenVideoFeature(feat) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                if (isHighContrast) 2.dp else 1.dp,
                                if (isHighContrast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("hub_video_feature_${feat.tag}")
                                .semantics {
                                    contentDescription = "Open ${feat.title} video feature"
                                }
                        ) {
                            Text(
                                text = feat.title,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    items(
                        listOf(
                            AudioFeatureTool.TRIM_CUT,
                            AudioFeatureTool.MIX_TRACKS,
                            AudioFeatureTool.EFFECTS_CLEANING
                        ),
                        key = { it.name }
                    ) { tool ->
                        AudioLinearFeatureButtonCard(
                            title = tool.title,
                            subtitle = tool.subtitle,
                            icon = audioToolIcon(tool),
                            tag = tool.tag,
                            isHighContrast = isHighContrast,
                            onClick = { onOpenAudioTool(tool) }
                        )
                    }
                    item(key = "audio_batch_processing") {
                        AudioLinearFeatureButtonCard(
                            title = "Batch Processing",
                            subtitle = "Apply normalization, trim & effects to multiple files at once",
                            icon = Icons.Default.PlaylistAdd,
                            tag = "hub_feature_audio_batch",
                            isHighContrast = isHighContrast,
                            onClick = onOpenBatchProcessing
                        )
                    }
                    items(
                        listOf(
                            AudioFeatureTool.MUSIC_REBALANCE,
                            AudioFeatureTool.RECORD_VOICE
                        ),
                        key = { it.name }
                    ) { tool ->
                        AudioLinearFeatureButtonCard(
                            title = tool.title,
                            subtitle = tool.subtitle,
                            icon = audioToolIcon(tool),
                            tag = tool.tag,
                            isHighContrast = isHighContrast,
                            onClick = { onOpenAudioTool(tool) }
                        )
                    }
                }
            }

            Button(
                onClick = onExportProject,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("btn_hub_export")
                    .semantics {
                        contentDescription = if (isVideo) "Export edited video as MP4" else "Export edited audio file"
                    },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    contentColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isVideo) "Export Video Project" else "Export Audio Project",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
private fun ProjectHubTimeText(
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    positionFlow: StateFlow<Long>?
) {
    val livePosMs = if (positionFlow != null) positionFlow.collectAsState().value else currentPositionMs
    val timeSemantics = if (isPlaying) {
        Modifier.clearAndSetSemantics {
            contentDescription = "Playing preview, total ${AccessibilityHelper.formatTimeSpoken(durationMs)}"
        }
    } else {
        Modifier
    }
    Text(
        text = "${AccessibilityHelper.formatTimeVisual(livePosMs)} / ${AccessibilityHelper.formatTimeVisual(durationMs)}",
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = timeSemantics
    )
}

@Composable
private fun FeatureHubCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tag: String,
    isHighContrast: Boolean,
    accentPrimary: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 124.dp)
            .testTag(tag)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $subtitle. Tap to open workspace."
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            if (isHighContrast) 2.dp else 1.dp,
            if (isHighContrast) Color.White else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (accentPrimary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (accentPrimary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AudioLinearFeatureButtonCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tag: String,
    isHighContrast: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .testTag(tag)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $subtitle. Tap to open."
            },
        shape = RoundedCornerShape(16.dp),
        color = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (isHighContrast) 2.dp else 1.dp,
            if (isHighContrast) Color.White else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun audioToolIcon(tool: AudioFeatureTool): ImageVector = when (tool) {
    AudioFeatureTool.TRIM_CUT -> Icons.Default.ContentCut
    AudioFeatureTool.MIX_TRACKS -> Icons.Default.Layers
    AudioFeatureTool.EFFECTS_CLEANING -> Icons.Default.GraphicEq
    AudioFeatureTool.MUSIC_REBALANCE -> Icons.Default.Tune
    AudioFeatureTool.RECORD_VOICE -> Icons.Default.Mic
    AudioFeatureTool.FULL_DAW_STUDIO -> Icons.Default.Audiotrack
}

private fun videoToolIcon(tool: VideoFeatureTool): ImageVector = when (tool) {
    VideoFeatureTool.TRIM_TIMELINE -> Icons.Default.ContentCut
    VideoFeatureTool.AUDIO_MUSIC -> Icons.Default.Mic
    VideoFeatureTool.OVERLAYS_VFX -> Icons.Default.AutoAwesome
    VideoFeatureTool.TRANSFORM_SPEED -> Icons.Default.Speed
    VideoFeatureTool.CONVERT_TOOLS -> Icons.Default.Transform
    VideoFeatureTool.FULL_VIDEO_STUDIO -> Icons.Default.Movie
}
