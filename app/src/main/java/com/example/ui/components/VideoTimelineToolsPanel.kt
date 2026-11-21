package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.accessibility.AccessibilityHelper
import com.example.ui.theme.LocalHighContrast
import com.example.video.SnapshotGlimpseItem
import com.example.video.VideoCutSegment
import com.example.video.VideoMetadataDetails

@Composable
fun VideoTimelineToolsPanel(
    currentPositionMs: Long,
    durationMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    streamCopyTrim: Boolean,
    onUpdateTrimRange: (Long, Long) -> Unit,
    onToggleStreamCopyTrim: (Boolean) -> Unit,
    onExportTrimmed: (() -> Unit)? = null,
    onCutMiddleSegment: ((Long, Long) -> Unit)? = null,
    mergeClipUris: List<Uri>,
    onAddMergeClips: (List<Uri>) -> Unit,
    onMoveMergeClip: (Int, Int) -> Unit,
    onRemoveMergeClip: (Int) -> Unit,
    onExecuteMergeClips: () -> Unit,
    slideshowImages: List<Uri>,
    slideshowSecPerSlide: Int,
    slideshowMusicUri: Uri?,
    onPickSlideshowImages: (List<Uri>) -> Unit,
    onChangeSlideshowSec: (Int) -> Unit,
    onPickSlideshowMusic: (Uri?) -> Unit,
    onCreateSlideshow: () -> Unit,
    onCreateBlankClip: () -> Unit,
    snapshots: List<SnapshotGlimpseItem>,
    onCaptureSnapshot: () -> Unit,
    onRemoveSnapshot: (String) -> Unit,
    onExportSnapshots: (prependToVideo: Boolean) -> Unit,
    videoFileName: String = "",
    videoMetadata: VideoMetadataDetails = VideoMetadataDetails(),
    isPlaying: Boolean = false,
    onPlayPause: () -> Unit = {},
    onSeekTo: (Long) -> Unit = {},
    onReplayFromStart: () -> Unit = {},
    cutSegments: List<VideoCutSegment> = emptyList(),
    onAddCutSegment: (Long, Long) -> Unit = { _, _ -> },
    onRemoveCutSegment: (String) -> Unit = {},
    onClearCutSegments: () -> Unit = {},
    showHeaderMetadataAndScrubber: Boolean = true,
    showTrimmerExportButton: Boolean = true
) {
    val isHighContrast = LocalHighContrast.current

    var trimStartSecText by remember(trimStartMs) { mutableStateOf((trimStartMs / 1000).toString()) }
    var trimEndSecText by remember(trimEndMs) { mutableStateOf((trimEndMs / 1000).toString()) }

    val multiVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris ->
        if (uris.isNotEmpty()) onAddMergeClips(uris)
    }

    val multiPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        if (uris.isNotEmpty()) onPickSlideshowImages(uris)
    }

    val slideshowMusicPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) onPickSlideshowMusic(uri)
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

        if (showHeaderMetadataAndScrubber) {
            VideoMetadataBanner(
                fileName = videoFileName,
                durationMs = durationMs,
                metadata = videoMetadata
            )
            VideoTimelineScrubber(
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
                cutSegments = cutSegments,
                onSeekTo = onSeekTo
            )
        }

        // 1. Video Trimmer (Direct Time Entry & Stream Copy vs Re-encode)
        SectionToolCard(
            title = "Video Trimmer",
            subtitle = "Direct time entry or markers • Re-encode or Fast Stream Copy",
            isHighContrast = isHighContrast,
            modifier = Modifier.testTag("section_video_trimmer")
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = trimStartSecText,
                    onValueChange = { v ->
                        trimStartSecText = v.filter { it.isDigit() }
                        val s = (trimStartSecText.toLongOrNull() ?: 0L) * 1000L
                        onUpdateTrimRange(s.coerceIn(0L, (trimEndMs - 500L).coerceAtLeast(0L)), trimEndMs)
                    },
                    label = { Text("Start (sec)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("input_trim_start_sec")
                )
                OutlinedTextField(
                    value = trimEndSecText,
                    onValueChange = { v ->
                        trimEndSecText = v.filter { it.isDigit() }
                        val e = (trimEndSecText.toLongOrNull() ?: 1L) * 1000L
                        onUpdateTrimRange(trimStartMs, e.coerceIn(trimStartMs + 500L, durationMs.coerceAtLeast(1000L)))
                    },
                    label = { Text("End (sec)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("input_trim_end_sec")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (streamCopyTrim) "Mode: Stream Copy (Instant)" else "Mode: Re-encode (With Effects)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text("Stream copy skips re-encoding for lossless speed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = streamCopyTrim,
                    onCheckedChange = onToggleStreamCopyTrim,
                    modifier = Modifier.testTag("switch_trim_stream_copy").semantics {
                        contentDescription = "Toggle Stream Copy trim mode. Currently ${if (streamCopyTrim) "Stream Copy" else "Re-encode"}"
                    }
                )
            }

            Text(
                text = "Active Trim Range: ${AccessibilityHelper.formatTimeVisual(trimStartMs)} - ${AccessibilityHelper.formatTimeVisual(trimEndMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("txt_active_trim_summary")
            )

            if (showTrimmerExportButton && onExportTrimmed != null) {
                Button(
                    onClick = onExportTrimmed,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_trim_export_now")
                ) {
                    Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Export Trimmed Clip (${AccessibilityHelper.formatTimeVisual(trimStartMs)} - ${AccessibilityHelper.formatTimeVisual(trimEndMs)})", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 2. Non-Destructive Video Cutter (Playback, Step, Markers, Readouts & Cut List — no export button here)
        VideoCutterSection(
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            isPlaying = isPlaying,
            cutSegments = cutSegments,
            onPlayPause = onPlayPause,
            onSeekTo = onSeekTo,
            onReplayFromStart = onReplayFromStart,
            onAddCutSegment = onAddCutSegment,
            onRemoveCutSegment = onRemoveCutSegment,
            onClearCutSegments = onClearCutSegments
        )

        // 3. Snapshot & Glimpse Creator
        SectionToolCard(title = "Snapshot & Glimpse Creator", subtitle = "Capture clip highlights at playhead, extract separately or prepend", isHighContrast = isHighContrast) {
            Button(
                onClick = onCaptureSnapshot,
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_capture_glimpse")
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Capture 2s Glimpse at ${AccessibilityHelper.formatTimeVisual(currentPositionMs)}", fontWeight = FontWeight.Bold)
            }

            if (snapshots.isNotEmpty()) {
                snapshots.forEach { snap ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${snap.label} (${AccessibilityHelper.formatTimeVisual(snap.startMs)} - ${AccessibilityHelper.formatTimeVisual(snap.endMs)})", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRemoveSnapshot(snap.id) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove ${snap.label}")
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onExportSnapshots(false) },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("btn_extract_glimpses")
                    ) {
                        Text("Extract Only", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onExportSnapshots(true) },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("btn_prepend_glimpses")
                    ) {
                        Text("Prepend Highlight", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 4. Video Merger
        SectionToolCard(title = "Video Merger", subtitle = "Pick multiple videos, reorder sequence, then merge into one clip", isHighContrast = isHighContrast) {
            OutlinedButton(
                onClick = { multiVideoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_pick_merge_videos")
            ) {
                Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add Video Clips to Merge (${mergeClipUris.size})", fontWeight = FontWeight.Bold)
            }

            mergeClipUris.forEachIndexed { idx, uri ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Clip ${idx + 1}: ${uri.lastPathSegment ?: "Video"}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Row {
                        IconButton(onClick = { if (idx > 0) onMoveMergeClip(idx, idx - 1) }, enabled = idx > 0, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move clip ${idx + 1} up")
                        }
                        IconButton(onClick = { if (idx < mergeClipUris.lastIndex) onMoveMergeClip(idx, idx + 1) }, enabled = idx < mergeClipUris.lastIndex, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move clip ${idx + 1} down")
                        }
                        IconButton(onClick = { onRemoveMergeClip(idx) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove clip ${idx + 1}")
                        }
                    }
                }
            }

            if (mergeClipUris.size >= 2) {
                Button(
                    onClick = onExecuteMergeClips,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_execute_video_merge")
                ) {
                    Icon(Icons.Default.MergeType, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Merge ${mergeClipUris.size} Clips Into One Video", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 5. Slideshow Creator & Blank Clip Generator
        SectionToolCard(title = "Slideshow & Blank Clip Creator", subtitle = "Assemble multiple images with custom duration and background music", isHighContrast = isHighContrast) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { multiPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("btn_slideshow_pick_images")
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Photos (${slideshowImages.size})", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { slideshowMusicPicker.launch("audio/*") },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("btn_slideshow_pick_music")
                ) {
                    Text(if (slideshowMusicUri != null) "Music Added" else "+ Add Music", fontWeight = FontWeight.Bold)
                }
            }

            AccessibleEffectSlider(
                title = "Seconds Per Photo",
                value = slideshowSecPerSlide.toFloat(),
                onValueChange = { onChangeSlideshowSec(it.toInt().coerceIn(1, 10)) },
                valueRange = 1f..10f,
                step = 1f,
                displayValue = "${slideshowSecPerSlide}s",
                spokenDescription = "$slideshowSecPerSlide seconds per photo",
                testTag = "slider_slideshow_duration"
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onCreateSlideshow,
                    enabled = slideshowImages.isNotEmpty(),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("btn_build_slideshow")
                ) {
                    Icon(Icons.Default.Slideshow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Build Slideshow", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onCreateBlankClip,
                    modifier = Modifier.weight(1f).height(48.dp).testTag("btn_generate_blank_clip")
                ) {
                    Text("Blank Clip (5s)", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SectionToolCard(
    title: String,
    subtitle: String,
    isHighContrast: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighContrast) Color(0xFF101010) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(
            if (isHighContrast) 2.dp else 1.dp,
            if (isHighContrast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}
