package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.accessibility.AccessibilityHelper
import com.example.ui.theme.LocalHighContrast
import com.example.video.VideoCutMath
import com.example.video.VideoCutSegment
import com.example.video.VideoMetadataDetails
import kotlinx.coroutines.flow.StateFlow

/**
 * Displays non-destructive video clip metadata (file name, duration, resolution/FPS if known).
 */
@Composable
fun VideoMetadataBanner(
    fileName: String,
    durationMs: Long,
    metadata: VideoMetadataDetails = VideoMetadataDetails(),
    modifier: Modifier = Modifier
) {
    val isHighContrast = LocalHighContrast.current
    val resolvedName = fileName.ifBlank { metadata.title.ifBlank { "Untitled Video" } }
    val resolvedDuration = durationMs.coerceAtLeast(metadata.durationMs).coerceAtLeast(0L)
    val detailParts = buildList {
        add("Duration: ${AccessibilityHelper.formatTimeVisual(resolvedDuration)}")
        if (metadata.width > 0 && metadata.height > 0) {
            add("${metadata.width}x${metadata.height}")
        }
        if (metadata.fps > 0 && metadata.width > 0) {
            add("${metadata.fps} fps")
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("video_metadata_banner")
            .semantics(mergeDescendants = true) {
                contentDescription = "Video metadata. File: $resolvedName. ${detailParts.joinToString(", ")}"
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isHighContrast) Color(0xFF121212) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            if (isHighContrast) 2.dp else 1.dp,
            if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Movie,
                contentDescription = null,
                tint = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = resolvedName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = detailParts.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Interactive accessible timeline scrubber synchronized to playback position and duration.
 */
@Composable
fun VideoTimelineScrubber(
    currentPositionMs: Long,
    durationMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    cutSegments: List<VideoCutSegment> = emptyList(),
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    positionFlow: StateFlow<Long>? = null
) {
    val livePosMs = if (positionFlow != null) positionFlow.collectAsState().value else currentPositionMs
    val safeDuration = durationMs.coerceAtLeast(1000L)
    var isDragging by remember { mutableStateOf(false) }
    var dragPosMs by remember { mutableStateOf(livePosMs.coerceIn(0L, safeDuration)) }
    val clampedPos = if (isDragging) dragPosMs.coerceIn(0L, safeDuration) else livePosMs.coerceIn(0L, safeDuration)
    val isHighContrast = LocalHighContrast.current

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            if (isHighContrast) 2.dp else 1.dp,
            if (isHighContrast) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isPlaying && !isDragging) {
                            Modifier.clearAndSetSemantics {
                                contentDescription = "Timeline Scrubber, playing, total ${AccessibilityHelper.formatTimeSpoken(safeDuration)}"
                            }
                        } else {
                            Modifier
                        }
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Timeline Scrubber",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${AccessibilityHelper.formatTimeVisual(clampedPos)} / ${AccessibilityHelper.formatTimeVisual(durationMs.coerceAtLeast(0L))}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Slider(
                value = clampedPos.toFloat(),
                onValueChange = { raw ->
                    val target = raw.toLong().coerceIn(0L, safeDuration)
                    isDragging = true
                    dragPosMs = target
                    onSeekTo(target)
                },
                onValueChangeFinished = {
                    val committed = dragPosMs.coerceIn(0L, safeDuration)
                    isDragging = false
                    onSeekTo(committed)
                },
                valueRange = 0f..safeDuration.toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("slider_video_timeline_scrubber")
                    .then(
                        if (isPlaying && !isDragging) {
                            Modifier.clearAndSetSemantics {
                                contentDescription = "Video timeline scrubber, playing. Pause video to adjust position."
                            }
                        } else {
                            Modifier.semantics {
                                contentDescription = "Video timeline scrubber"
                                stateDescription = "${AccessibilityHelper.formatTimeSpoken(clampedPos)} of ${AccessibilityHelper.formatTimeSpoken(safeDuration)}"
                            }
                        }
                    )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Active Trim: ${AccessibilityHelper.formatTimeVisual(trimStartMs)} - ${AccessibilityHelper.formatTimeVisual(trimEndMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (cutSegments.isNotEmpty()) {
                    Text(
                        text = "${cutSegments.size} cut(s) queued",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

/**
 * Full-featured, accessible non-destructive Video Cutter section.
 * Intentionally omits any "Export and save video" button because final export runs later
 * after project save and downstream audio/noise editing steps.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VideoCutterSection(
    currentPositionMs: Long,
    durationMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    isPlaying: Boolean,
    cutSegments: List<VideoCutSegment>,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onReplayFromStart: () -> Unit,
    onAddCutSegment: (Long, Long) -> Unit,
    onRemoveCutSegment: (String) -> Unit,
    onClearCutSegments: () -> Unit = {},
    modifier: Modifier = Modifier,
    currentPositionMsProvider: (() -> Long)? = null
) {
    val isHighContrast = LocalHighContrast.current
    val safeDuration = durationMs.coerceAtLeast(0L)
    val effectiveSpanDuration = safeDuration.coerceAtLeast(1000L)

    var stepMs by remember { mutableLongStateOf(VideoCutMath.DEFAULT_STEP_MS) }
    val stepOption = VideoCutMath.stepOptionFor(stepMs)

    var cutStartMs by remember(durationMs) {
        mutableLongStateOf(0L)
    }
    var cutEndMs by remember(durationMs) {
        mutableLongStateOf(
            when {
                safeDuration <= 0L -> 0L
                safeDuration >= 4000L -> 4000L
                else -> safeDuration
            }
        )
    }

    LaunchedEffect(durationMs) {
        if (safeDuration > 0L) {
            if (cutEndMs <= cutStartMs || cutEndMs > safeDuration) {
                val defaultEnd = minOf(safeDuration, maxOf(cutStartMs + 1000L, minOf(4000L, safeDuration)))
                val (s, e) = VideoCutMath.clampEndCutMarker(cutStartMs.coerceAtMost(defaultEnd), defaultEnd, safeDuration)
                cutStartMs = s
                cutEndMs = e
            }
        }
    }

    var showCutsListDialog by remember { mutableStateOf(false) }

    val deletedDurationMs = remember(cutSegments, trimStartMs, trimEndMs, safeDuration) {
        if (safeDuration <= 0L) 0L
        else VideoCutMath.computeDeletedDurationMs(cutSegments, 0L, safeDuration)
    }
    val outputLengthMs = remember(trimStartMs, trimEndMs, safeDuration, cutSegments) {
        VideoCutMath.computeOutputLengthMs(trimStartMs, trimEndMs, safeDuration, cutSegments)
    }

    SectionToolCard(
        title = "Video Cutter",
        subtitle = "Mark regions to remove without exporting yet — cuts apply when you save & export later",
        isHighContrast = isHighContrast,
        modifier = modifier.testTag("section_video_cutter")
    ) {
        // 1. Playback Controls
        Text(
            text = "Playback Controls",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CutterTooltipActionButton(
                label = "Rewind 5s",
                tooltip = "Rewind 5 seconds",
                icon = Icons.Default.FastRewind,
                onClick = {
                    val pos = currentPositionMsProvider?.invoke() ?: currentPositionMs
                    onSeekTo((pos - 5_000L).coerceIn(0L, safeDuration))
                },
                testTag = "btn_cutter_rewind_5s"
            )
            CutterTooltipActionButton(
                label = if (isPlaying) "Pause" else "Play",
                tooltip = if (isPlaying) "Pause video" else "Play video",
                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                onClick = onPlayPause,
                emphasized = true,
                stateDesc = if (isPlaying) "Playing" else "Paused",
                testTag = "btn_cutter_play_pause"
            )
            CutterTooltipActionButton(
                label = "Forward 5s",
                tooltip = "Fast forward 5 seconds",
                icon = Icons.Default.FastForward,
                onClick = {
                    val pos = currentPositionMsProvider?.invoke() ?: currentPositionMs
                    onSeekTo((pos + 5_000L).coerceIn(0L, safeDuration))
                },
                testTag = "btn_cutter_forward_5s"
            )
            CutterTooltipActionButton(
                label = "Replay from Start",
                tooltip = "Replay from start",
                icon = Icons.Default.Replay,
                onClick = onReplayFromStart,
                testTag = "btn_cutter_replay_start"
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // 2. Step Controls
        Text(
            text = "Step Controls",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CutterTooltipActionButton(
                label = "Seek Back ${stepOption.shortLabel}",
                tooltip = "Seek backward ${stepOption.spokenLabel}",
                icon = Icons.Default.SkipPrevious,
                onClick = {
                    val pos = currentPositionMsProvider?.invoke() ?: currentPositionMs
                    onSeekTo((pos - stepMs).coerceIn(0L, safeDuration))
                },
                testTag = "btn_cutter_step_back"
            )
            CutterTooltipActionButton(
                label = "Seek Fwd ${stepOption.shortLabel}",
                tooltip = "Seek forward ${stepOption.spokenLabel}",
                icon = Icons.Default.SkipNext,
                onClick = {
                    val pos = currentPositionMsProvider?.invoke() ?: currentPositionMs
                    onSeekTo((pos + stepMs).coerceIn(0L, safeDuration))
                },
                testTag = "btn_cutter_step_forward"
            )
            CutterTooltipActionButton(
                label = "Step: ${stepOption.shortLabel}",
                tooltip = "Change seek and marker step size (currently ${stepOption.spokenLabel})",
                icon = Icons.Default.Timer,
                onClick = { stepMs = VideoCutMath.nextStepOption(stepMs).stepMs },
                tonal = true,
                stateDesc = stepOption.spokenLabel,
                testTag = "btn_cutter_step_toggle"
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // 3. Start Cut Marker Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Start Cut Marker",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = AccessibilityHelper.formatTimeVisual(cutStartMs),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("txt_cutter_start_marker_badge")
                )
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CutterTooltipActionButton(
                label = "-${stepOption.shortLabel}",
                tooltip = "Decrease start cut marker by ${stepOption.spokenLabel}",
                icon = Icons.Default.Remove,
                onClick = {
                    val (s, e) = VideoCutMath.clampStartCutMarker(cutStartMs - stepMs, cutEndMs, safeDuration)
                    cutStartMs = s
                    cutEndMs = e
                },
                testTag = "btn_cutter_start_dec"
            )
            CutterTooltipActionButton(
                label = "+${stepOption.shortLabel}",
                tooltip = "Increase start cut marker by ${stepOption.spokenLabel}",
                icon = Icons.Default.Add,
                onClick = {
                    val (s, e) = VideoCutMath.clampStartCutMarker(cutStartMs + stepMs, cutEndMs, safeDuration)
                    cutStartMs = s
                    cutEndMs = e
                },
                testTag = "btn_cutter_start_inc"
            )
            CutterTooltipActionButton(
                label = "Beginning",
                tooltip = "Set start cut marker to beginning of video",
                icon = Icons.Default.FirstPage,
                onClick = {
                    val (s, e) = VideoCutMath.clampStartCutMarker(0L, cutEndMs, safeDuration)
                    cutStartMs = s
                    cutEndMs = e
                },
                testTag = "btn_cutter_start_beginning"
            )
            CutterTooltipActionButton(
                label = "At Playhead",
                tooltip = if (currentPositionMsProvider != null || isPlaying) {
                    "Set start cut marker to current playhead position"
                } else {
                    "Set start cut marker to current playhead position (${AccessibilityHelper.formatTimeVisual(currentPositionMs)})"
                },
                icon = Icons.Default.Bookmark,
                onClick = {
                    val pos = currentPositionMsProvider?.invoke() ?: currentPositionMs
                    val (s, e) = VideoCutMath.clampStartCutMarker(pos, cutEndMs, safeDuration)
                    cutStartMs = s
                    cutEndMs = e
                },
                tonal = true,
                testTag = "btn_cutter_start_playhead"
            )
        }

        // 4. End Cut Marker Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "End Cut Marker",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = AccessibilityHelper.formatTimeVisual(cutEndMs),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("txt_cutter_end_marker_badge")
                )
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CutterTooltipActionButton(
                label = "-${stepOption.shortLabel}",
                tooltip = "Decrease end cut marker by ${stepOption.spokenLabel}",
                icon = Icons.Default.Remove,
                onClick = {
                    val (s, e) = VideoCutMath.clampEndCutMarker(cutStartMs, cutEndMs - stepMs, safeDuration)
                    cutStartMs = s
                    cutEndMs = e
                },
                testTag = "btn_cutter_end_dec"
            )
            CutterTooltipActionButton(
                label = "+${stepOption.shortLabel}",
                tooltip = "Increase end cut marker by ${stepOption.spokenLabel}",
                icon = Icons.Default.Add,
                onClick = {
                    val (s, e) = VideoCutMath.clampEndCutMarker(cutStartMs, cutEndMs + stepMs, safeDuration)
                    cutStartMs = s
                    cutEndMs = e
                },
                testTag = "btn_cutter_end_inc"
            )
            CutterTooltipActionButton(
                label = "End of Video",
                tooltip = "Set end cut marker to end of video (${AccessibilityHelper.formatTimeVisual(safeDuration)})",
                icon = Icons.AutoMirrored.Filled.LastPage,
                onClick = {
                    val (s, e) = VideoCutMath.clampEndCutMarker(cutStartMs, safeDuration, safeDuration)
                    cutStartMs = s
                    cutEndMs = e
                },
                testTag = "btn_cutter_end_finish"
            )
            CutterTooltipActionButton(
                label = "At Playhead",
                tooltip = if (currentPositionMsProvider != null || isPlaying) {
                    "Set end cut marker to current playhead position"
                } else {
                    "Set end cut marker to current playhead position (${AccessibilityHelper.formatTimeVisual(currentPositionMs)})"
                },
                icon = Icons.Default.Bookmark,
                onClick = {
                    val pos = currentPositionMsProvider?.invoke() ?: currentPositionMs
                    val (s, e) = VideoCutMath.clampEndCutMarker(cutStartMs, pos, safeDuration)
                    cutStartMs = s
                    cutEndMs = e
                },
                tonal = true,
                testTag = "btn_cutter_end_playhead"
            )
        }

        // 5. Live Selection Readout & Output Summary
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_cutter_readout")
                .semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription =
                        "Selection start ${AccessibilityHelper.formatTimeSpoken(cutStartMs)}. " +
                            "Selection end ${AccessibilityHelper.formatTimeSpoken(cutEndMs)}. " +
                            "Deleted duration ${AccessibilityHelper.formatTimeSpoken(deletedDurationMs)}. " +
                            "Output length ${AccessibilityHelper.formatTimeSpoken(outputLengthMs)}."
                },
            colors = CardDefaults.cardColors(
                containerColor = if (isHighContrast) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CutterMetricTile(
                        label = "Selection Start",
                        value = AccessibilityHelper.formatTimeVisual(cutStartMs),
                        testTag = "txt_cutter_sel_start",
                        modifier = Modifier.weight(1f)
                    )
                    CutterMetricTile(
                        label = "Selection End",
                        value = AccessibilityHelper.formatTimeVisual(cutEndMs),
                        testTag = "txt_cutter_sel_end",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CutterMetricTile(
                        label = "Deleted Duration",
                        value = AccessibilityHelper.formatTimeVisual(deletedDurationMs),
                        testTag = "txt_cutter_deleted_dur",
                        modifier = Modifier.weight(1f)
                    )
                    CutterMetricTile(
                        label = "Output Length",
                        value = AccessibilityHelper.formatTimeVisual(outputLengthMs),
                        testTag = "txt_cutter_output_len",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 6. Add Selected Region & View Cuts List Actions (No Export button here)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text("Add selected region to cut list") } },
                state = rememberTooltipState(),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Button(
                    onClick = {
                        if (safeDuration > 0L && cutEndMs > cutStartMs) {
                            onAddCutSegment(cutStartMs, cutEndMs)
                        }
                    },
                    enabled = safeDuration > 0L && cutEndMs > cutStartMs,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .heightIn(min = 48.dp)
                        .testTag("btn_cutter_add_cut")
                        .semantics {
                            contentDescription =
                                "Add selected region from ${AccessibilityHelper.formatTimeSpoken(cutStartMs)} to ${AccessibilityHelper.formatTimeSpoken(cutEndMs)} to cut list"
                        }
                ) {
                    Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Selected Region to Cut List", fontWeight = FontWeight.Bold)
                }
            }

            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text("View added cuts list (${cutSegments.size})") } },
                state = rememberTooltipState(),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                OutlinedButton(
                    onClick = { showCutsListDialog = true },
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .heightIn(min = 48.dp)
                        .testTag("btn_cutter_view_cuts")
                        .semantics {
                            contentDescription = "View added cuts list. ${cutSegments.size} cuts added."
                            stateDescription = "${cutSegments.size} cuts"
                        }
                ) {
                    Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("View Added Cuts (${cutSegments.size})", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showCutsListDialog) {
        AlertDialog(
            onDismissRequest = { showCutsListDialog = false },
            modifier = Modifier.testTag("dialog_cutter_cuts_list"),
            title = {
                Text(
                    text = "Added Cuts List (${cutSegments.size})",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Deleted: ${AccessibilityHelper.formatTimeVisual(deletedDurationMs)} • Output Length: ${AccessibilityHelper.formatTimeVisual(outputLengthMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (cutSegments.isEmpty()) {
                        Text(
                            text = "No cuts added yet. Adjust the Start and End Cut Markers, then tap Add Selected Region to Cut List.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(cutSegments, key = { _, seg -> seg.id }) { idx, seg ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Cut #${idx + 1}: ${AccessibilityHelper.formatTimeVisual(seg.startMs)} - ${AccessibilityHelper.formatTimeVisual(seg.endMs)}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Length: ${AccessibilityHelper.formatTimeVisual(seg.durationMs)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    onSeekTo(seg.startMs.coerceIn(0L, effectiveSpanDuration))
                                                    showCutsListDialog = false
                                                },
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .testTag("btn_jump_cut_$idx")
                                                    .semantics {
                                                        contentDescription = "Jump to Cut ${idx + 1} at ${AccessibilityHelper.formatTimeSpoken(seg.startMs)}"
                                                    }
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                            }
                                            IconButton(
                                                onClick = { onRemoveCutSegment(seg.id) },
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .testTag("btn_remove_cut_$idx")
                                                    .semantics {
                                                        contentDescription = "Remove Cut ${idx + 1}"
                                                    }
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = null)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showCutsListDialog = false },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("btn_close_cuts_list")
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                if (cutSegments.isNotEmpty()) {
                    TextButton(
                        onClick = onClearCutSegments,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("btn_clear_all_cuts")
                    ) {
                        Text("Clear All", fontWeight = FontWeight.Bold)
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CutterTooltipActionButton(
    label: String,
    tooltip: String,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String,
    emphasized: Boolean = false,
    tonal: Boolean = false,
    stateDesc: String? = null
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState()
    ) {
        val mod = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .heightIn(min = 48.dp)
            .testTag(testTag)
            .semantics {
                contentDescription = tooltip
                if (stateDesc != null) stateDescription = stateDesc
            }
        when {
            emphasized -> Button(
                onClick = onClick,
                modifier = mod,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, fontWeight = FontWeight.Bold)
            }
            tonal -> FilledTonalButton(onClick = onClick, modifier = mod) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, fontWeight = FontWeight.Bold)
            }
            else -> OutlinedButton(onClick = onClick, modifier = mod) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CutterMetricTile(
    label: String,
    value: String,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(testTag)
        )
    }
}
