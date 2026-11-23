package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.accessibility.AccessibilityHelper
import com.example.ui.theme.LocalHighContrast
import com.example.video.SnapshotGlimpseItem
import com.example.video.VideoCutSegment
import com.example.video.VideoMetadataDetails
import com.example.video.VideoStudioFeatureButton
import com.example.video.VideoToolSection
import kotlinx.coroutines.flow.StateFlow

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
    showTrimmerExportButton: Boolean = true,
    positionFlow: StateFlow<Long>? = null,
    currentPositionMsProvider: (() -> Long)? = null,
    initialFocusedFeature: VideoStudioFeatureButton? = null,
    onFocusedFeatureChange: ((VideoStudioFeatureButton?) -> Unit)? = null
) {
    val isHighContrast = LocalHighContrast.current
    var focusedFeature by remember(initialFocusedFeature) { mutableStateOf(initialFocusedFeature) }
    val selectFeature: (VideoStudioFeatureButton?) -> Unit = { next ->
        focusedFeature = next
        onFocusedFeatureChange?.invoke(next)
    }

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
                onSeekTo = onSeekTo,
                isPlaying = isPlaying,
                positionFlow = positionFlow
            )
        }

        // Top Timeline Feature Buttons (clicking any button opens that tool in its own dedicated dialog)
        VideoFeatureButtonStrip(
            features = VideoStudioFeatureButton.forSection(VideoToolSection.TIMELINE),
            selectedFeature = focusedFeature,
            onSelectFeature = { next ->
                selectFeature(if (next == focusedFeature) null else next)
            },
            isHighContrast = isHighContrast,
            showAllButton = false,
            titlePrefix = "Open "
        )
    }

    val activeFeature = focusedFeature
    if (activeFeature != null) {
        Dialog(
            onDismissRequest = { selectFeature(null) },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            BackHandler { selectFeature(null) }
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .heightIn(max = 660.dp)
                    .testTag("dialog_video_timeline_feature"),
                shape = RoundedCornerShape(20.dp),
                color = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.background,
                border = if (isHighContrast) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeFeature.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.testTag("txt_timeline_dialog_title")
                            )
                            Text(
                                text = "Timeline Tool",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { selectFeature(null) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("btn_close_timeline_feature_dialog")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close ${activeFeature.title}")
                        }
                    }

                    HorizontalDivider()

                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (activeFeature) {
                            VideoStudioFeatureButton.TRIMMER -> {
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
                            }

                            VideoStudioFeatureButton.CUTTER -> {
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
                                    onClearCutSegments = onClearCutSegments,
                                    currentPositionMsProvider = currentPositionMsProvider
                                )
                            }

                            VideoStudioFeatureButton.SNAPSHOT_GLIMPSE -> {
                                SectionToolCard(title = "Snapshot & Glimpse Creator", subtitle = "Capture clip highlights at playhead, extract separately or prepend", isHighContrast = isHighContrast) {
                                    Button(
                                        onClick = onCaptureSnapshot,
                                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_capture_glimpse")
                                    ) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = if (currentPositionMsProvider != null || isPlaying) {
                                                "Capture 2s Glimpse at Playhead"
                                            } else {
                                                "Capture 2s Glimpse at ${AccessibilityHelper.formatTimeVisual(currentPositionMs)}"
                                            },
                                            fontWeight = FontWeight.Bold
                                        )
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
                            }

                            VideoStudioFeatureButton.MERGER -> {
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
                            }

                            VideoStudioFeatureButton.SLIDESHOW_BLANK -> {
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

                            else -> Unit
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { selectFeature(null) },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("btn_done_timeline_feature_dialog")
                        ) {
                            Text("Done", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoFeatureButtonStrip(
    features: List<VideoStudioFeatureButton>,
    selectedFeature: VideoStudioFeatureButton?,
    onSelectFeature: (VideoStudioFeatureButton?) -> Unit,
    isHighContrast: Boolean,
    showAllButton: Boolean = true,
    titlePrefix: String = ""
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_video_feature_buttons"),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighContrast) Color(0xFF0A0A0A) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Feature Buttons",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showAllButton) {
                    if (selectedFeature == null) {
                        Button(
                            onClick = { onSelectFeature(null) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("btn_video_feature_all")
                                .semantics { contentDescription = "Show all video features in this section" }
                        ) {
                            Text("All Features", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onSelectFeature(null) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("btn_video_feature_all")
                                .semantics { contentDescription = "Show all video features in this section" }
                        ) {
                            Text("All Features", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                features.forEach { feat ->
                    val isSelected = selectedFeature == feat
                    val buttonText = if (titlePrefix.isNotEmpty()) "$titlePrefix${feat.title}" else feat.title
                    if (isSelected) {
                        Button(
                            onClick = { onSelectFeature(null) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("btn_video_feature_${feat.tag}")
                                .semantics { contentDescription = "${feat.title} button, selected" }
                        ) {
                            Text(buttonText, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onSelectFeature(feat) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("btn_video_feature_${feat.tag}")
                                .semantics { contentDescription = "Open ${feat.title} feature" }
                        ) {
                            Text(buttonText, fontWeight = FontWeight.SemiBold)
                        }
                    }
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
