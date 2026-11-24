package com.example.ui.components

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.accessibility.AccessibilityHelper
import com.example.video.VideoCutMath
import com.example.video.VideoCutSegment

@Composable
internal fun MovePlayMoveRow(
    onMoveStartNow: () -> Unit,
    onPlay: () -> Unit,
    onMoveFinishNow: () -> Unit,
    tagPrefix: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onMoveStartNow,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("btn_${tagPrefix}_move_start_now")
        ) {
            Text(
                text = "Move Start slider to now playing",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2
            )
        }
        Button(
            onClick = onPlay,
            modifier = Modifier
                .height(48.dp)
                .testTag("btn_${tagPrefix}_play")
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Play")
        }
        OutlinedButton(
            onClick = onMoveFinishNow,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("btn_${tagPrefix}_move_finish_now")
        ) {
            Text(
                text = "Move Finish slider to now playing",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2
            )
        }
    }
}

@Composable
internal fun PrecisionMarkerSliderRow(
    label: String,
    timeLabel: String,
    valueMs: Long,
    stepMs: Long,
    minMs: Long,
    maxMs: Long,
    onValueChange: (Long) -> Unit,
    testTagPrefix: String
) {
    val safeMin = minMs.coerceAtLeast(0L)
    val safeMax = maxMs.coerceAtLeast(safeMin + 1L)
    val clamped = valueMs.coerceIn(safeMin, safeMax)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$timeLabel: ${AccessibilityHelper.formatTimePrecise(clamped)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("text_${testTagPrefix}_time")
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = {
                    onValueChange(VideoCutMath.nudgeTimestamp(clamped, -1, stepMs, safeMin, safeMax))
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("btn_${testTagPrefix}_minus_step")
                    .semantics { contentDescription = "Move $label earlier by step" }
            ) {
                Icon(Icons.Default.Remove, contentDescription = null)
            }
            Slider(
                value = clamped.toFloat(),
                onValueChange = { raw ->
                    onValueChange(raw.toLong().coerceIn(safeMin, safeMax))
                },
                valueRange = safeMin.toFloat()..safeMax.toFloat(),
                modifier = Modifier
                    .weight(1f)
                    .testTag("slider_${testTagPrefix}")
                    .semantics {
                        contentDescription = "$label slider. $timeLabel is ${AccessibilityHelper.formatTimeSpoken(clamped)}"
                    }
            )
            IconButton(
                onClick = {
                    onValueChange(VideoCutMath.nudgeTimestamp(clamped, 1, stepMs, safeMin, safeMax))
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("btn_${testTagPrefix}_plus_step")
                    .semantics { contentDescription = "Move $label later by step" }
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    }
}

@Composable
internal fun PrecisionSummaryRow(
    startMs: Long,
    finishMs: Long,
    clipDurationMs: Long,
    tagPrefix: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Start time: ${AccessibilityHelper.formatTimePrecise(startMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("readout_${tagPrefix}_start_time")
                )
                Text(
                    text = "Finish time: ${AccessibilityHelper.formatTimePrecise(finishMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("readout_${tagPrefix}_finish_time")
                )
            }
            Text(
                text = "Clip duration: ${AccessibilityHelper.formatTimePrecise(clipDurationMs)}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("readout_${tagPrefix}_clip_duration")
            )
        }
    }
}

/**
 * Updated Video Trimmer panel integrating Move Start/Finish to now playing, Play, Start, Step, Finish,
 * Start time, Finish time, and Clip duration (without any file-info card or Save fast/Save exact buttons).
 */
@Composable
fun VideoTrimmerPrecisionPanel(
    durationMs: Long,
    currentPositionMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    streamCopyTrim: Boolean,
    onUpdateTrim: (startMs: Long, endMs: Long, streamCopy: Boolean) -> Unit,
    onPlaySelection: (Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeDuration = durationMs.coerceAtLeast(1000L)
    var stepMs by remember { mutableStateOf(1000L) }
    val clampedStart = trimStartMs.coerceIn(0L, safeDuration)
    val clampedEnd = trimEndMs.coerceIn((clampedStart + 250L).coerceAtMost(safeDuration), safeDuration)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("panel_video_trimmer"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Video Trimmer",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Set Start and Finish around the part you want to keep, then save using your project save or export.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        MovePlayMoveRow(
            onMoveStartNow = {
                val (s, e) = VideoCutMath.clampStartCutMarker(currentPositionMs, clampedEnd, safeDuration)
                onUpdateTrim(s, e, streamCopyTrim)
            },
            onPlay = { onPlaySelection(clampedStart, clampedEnd) },
            onMoveFinishNow = {
                val (s, e) = VideoCutMath.clampEndCutMarker(clampedStart, currentPositionMs, safeDuration)
                onUpdateTrim(s, e, streamCopyTrim)
            },
            tagPrefix = "trimmer"
        )

        PrecisionMarkerSliderRow(
            label = "Start",
            timeLabel = "Start time",
            valueMs = clampedStart,
            stepMs = stepMs,
            minMs = 0L,
            maxMs = (clampedEnd - 250L).coerceAtLeast(0L),
            onValueChange = { newStart ->
                val (s, e) = VideoCutMath.clampStartCutMarker(newStart, clampedEnd, safeDuration)
                onUpdateTrim(s, e, streamCopyTrim)
            },
            testTagPrefix = "trimmer_start"
        )

        StandardizedStepSlider(
            stepMs = stepMs,
            onStepChange = { stepMs = it },
            testTagPrefix = "trimmer_step"
        )

        PrecisionMarkerSliderRow(
            label = "Finish",
            timeLabel = "Finish time",
            valueMs = clampedEnd,
            stepMs = stepMs,
            minMs = (clampedStart + 250L).coerceAtMost(safeDuration),
            maxMs = safeDuration,
            onValueChange = { newEnd ->
                val (s, e) = VideoCutMath.clampEndCutMarker(clampedStart, newEnd, safeDuration)
                onUpdateTrim(s, e, streamCopyTrim)
            },
            testTagPrefix = "trimmer_finish"
        )

        PrecisionSummaryRow(
            startMs = clampedStart,
            finishMs = clampedEnd,
            clipDurationMs = (clampedEnd - clampedStart).coerceAtLeast(0L),
            tagPrefix = "trimmer"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Lossless Stream-Copy Trim",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Keeps original keyframe packets without quality loss",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = streamCopyTrim,
                onCheckedChange = { onUpdateTrim(clampedStart, clampedEnd, it) },
                modifier = Modifier.testTag("switch_trim_stream_copy")
            )
        }
    }
}

/**
 * Separate Feature — Video Multi trim
 */
@Composable
fun VideoMultiTrimPanel(
    durationMs: Long,
    currentPositionMs: Long,
    multiTrimClips: List<VideoCutSegment>,
    onUpdateMultiTrimClips: (List<VideoCutSegment>) -> Unit,
    onPlaySelection: (Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeDuration = durationMs.coerceAtLeast(1000L)
    var stepMs by remember { mutableStateOf(1000L) }
    var startMs by remember(durationMs) { mutableStateOf(0L) }
    var finishMs by remember(durationMs) { mutableStateOf((safeDuration / 2L).coerceAtLeast(1000L)) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("panel_video_multi_trim"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Video Multi trim",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Set Start and Finish around each clip you want to keep, then press Add clip.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        MovePlayMoveRow(
            onMoveStartNow = {
                val (s, e) = VideoCutMath.clampStartCutMarker(currentPositionMs, finishMs, safeDuration)
                startMs = s; finishMs = e
            },
            onPlay = { onPlaySelection(startMs, finishMs) },
            onMoveFinishNow = {
                val (s, e) = VideoCutMath.clampEndCutMarker(startMs, currentPositionMs, safeDuration)
                startMs = s; finishMs = e
            },
            tagPrefix = "multi_trim"
        )

        PrecisionMarkerSliderRow(
            label = "Start",
            timeLabel = "Start time",
            valueMs = startMs,
            stepMs = stepMs,
            minMs = 0L,
            maxMs = (finishMs - 250L).coerceAtLeast(0L),
            onValueChange = { val (s, e) = VideoCutMath.clampStartCutMarker(it, finishMs, safeDuration); startMs = s; finishMs = e },
            testTagPrefix = "multi_trim_start"
        )

        StandardizedStepSlider(
            stepMs = stepMs,
            onStepChange = { stepMs = it },
            testTagPrefix = "multi_trim_step"
        )

        PrecisionMarkerSliderRow(
            label = "Finish",
            timeLabel = "Finish time",
            valueMs = finishMs,
            stepMs = stepMs,
            minMs = (startMs + 250L).coerceAtMost(safeDuration),
            maxMs = safeDuration,
            onValueChange = { val (s, e) = VideoCutMath.clampEndCutMarker(startMs, it, safeDuration); startMs = s; finishMs = e },
            testTagPrefix = "multi_trim_finish"
        )

        PrecisionSummaryRow(
            startMs = startMs,
            finishMs = finishMs,
            clipDurationMs = (finishMs - startMs).coerceAtLeast(0L),
            tagPrefix = "multi_trim"
        )

        Button(
            onClick = {
                if (finishMs > startMs) {
                    val updated = (multiTrimClips + VideoCutSegment(startMs = startMs, endMs = finishMs))
                        .sortedBy { it.startMs }
                    onUpdateMultiTrimClips(updated)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_multi_trim_add_clip")
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Add clip")
        }

        Text(
            text = "Clip list (${multiTrimClips.size})",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("text_multi_trim_clip_list")
        )

        multiTrimClips.forEachIndexed { idx, clip ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_multi_trim_clip_$idx"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Clip ${idx + 1}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = "${AccessibilityHelper.formatTimePrecise(clip.startMs)} → ${AccessibilityHelper.formatTimePrecise(clip.endMs)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    IconButton(
                        onClick = { onUpdateMultiTrimClips(multiTrimClips.filterNot { it.id == clip.id }) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("btn_remove_multi_trim_clip_$idx")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove clip ${idx + 1}")
                    }
                }
            }
        }
    }
}

/**
 * Separate Feature — Video Delete part
 */
@Composable
fun VideoDeletePartPanel(
    durationMs: Long,
    currentPositionMs: Long,
    cutSegments: List<VideoCutSegment>,
    onUpdateCutSegments: (List<VideoCutSegment>) -> Unit,
    onPlaySelection: (Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeDuration = durationMs.coerceAtLeast(1000L)
    var stepMs by remember { mutableStateOf(1000L) }
    val existing = cutSegments.firstOrNull()
    var startMs by remember(durationMs, existing?.id) { mutableStateOf(existing?.startMs ?: 0L) }
    var finishMs by remember(durationMs, existing?.id) {
        mutableStateOf(existing?.endMs ?: (safeDuration / 3L).coerceAtLeast(1000L))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("panel_video_delete_part"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Video Delete part",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Set Start and Finish around the part you want to remove from the video.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        MovePlayMoveRow(
            onMoveStartNow = {
                val (s, e) = VideoCutMath.clampStartCutMarker(currentPositionMs, finishMs, safeDuration)
                startMs = s; finishMs = e
                onUpdateCutSegments(listOf(VideoCutSegment(startMs = s, endMs = e)))
            },
            onPlay = { onPlaySelection(startMs, finishMs) },
            onMoveFinishNow = {
                val (s, e) = VideoCutMath.clampEndCutMarker(startMs, currentPositionMs, safeDuration)
                startMs = s; finishMs = e
                onUpdateCutSegments(listOf(VideoCutSegment(startMs = s, endMs = e)))
            },
            tagPrefix = "delete_part"
        )

        PrecisionMarkerSliderRow(
            label = "Start",
            timeLabel = "Start time",
            valueMs = startMs,
            stepMs = stepMs,
            minMs = 0L,
            maxMs = (finishMs - 250L).coerceAtLeast(0L),
            onValueChange = {
                val (s, e) = VideoCutMath.clampStartCutMarker(it, finishMs, safeDuration)
                startMs = s; finishMs = e
                onUpdateCutSegments(listOf(VideoCutSegment(startMs = s, endMs = e)))
            },
            testTagPrefix = "delete_part_start"
        )

        StandardizedStepSlider(
            stepMs = stepMs,
            onStepChange = { stepMs = it },
            testTagPrefix = "delete_part_step"
        )

        PrecisionMarkerSliderRow(
            label = "Finish",
            timeLabel = "Finish time",
            valueMs = finishMs,
            stepMs = stepMs,
            minMs = (startMs + 250L).coerceAtMost(safeDuration),
            maxMs = safeDuration,
            onValueChange = {
                val (s, e) = VideoCutMath.clampEndCutMarker(startMs, it, safeDuration)
                startMs = s; finishMs = e
                onUpdateCutSegments(listOf(VideoCutSegment(startMs = s, endMs = e)))
            },
            testTagPrefix = "delete_part_finish"
        )

        PrecisionSummaryRow(
            startMs = startMs,
            finishMs = finishMs,
            clipDurationMs = (finishMs - startMs).coerceAtLeast(0L),
            tagPrefix = "delete_part"
        )
    }
}

/**
 * Separate Feature — Video Multi delete
 */
@Composable
fun VideoMultiDeletePanel(
    durationMs: Long,
    currentPositionMs: Long,
    cutSegments: List<VideoCutSegment>,
    onUpdateCutSegments: (List<VideoCutSegment>) -> Unit,
    onPlaySelection: (Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeDuration = durationMs.coerceAtLeast(1000L)
    var stepMs by remember { mutableStateOf(1000L) }
    var startMs by remember(durationMs) { mutableStateOf(0L) }
    var finishMs by remember(durationMs) { mutableStateOf((safeDuration / 3L).coerceAtLeast(1000L)) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("panel_video_multi_delete"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Video Multi delete",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Set Start and Finish around each part to remove, then press Add part.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        MovePlayMoveRow(
            onMoveStartNow = {
                val (s, e) = VideoCutMath.clampStartCutMarker(currentPositionMs, finishMs, safeDuration)
                startMs = s; finishMs = e
            },
            onPlay = { onPlaySelection(startMs, finishMs) },
            onMoveFinishNow = {
                val (s, e) = VideoCutMath.clampEndCutMarker(startMs, currentPositionMs, safeDuration)
                startMs = s; finishMs = e
            },
            tagPrefix = "multi_delete"
        )

        PrecisionMarkerSliderRow(
            label = "Start",
            timeLabel = "Start time",
            valueMs = startMs,
            stepMs = stepMs,
            minMs = 0L,
            maxMs = (finishMs - 250L).coerceAtLeast(0L),
            onValueChange = { val (s, e) = VideoCutMath.clampStartCutMarker(it, finishMs, safeDuration); startMs = s; finishMs = e },
            testTagPrefix = "multi_delete_start"
        )

        StandardizedStepSlider(
            stepMs = stepMs,
            onStepChange = { stepMs = it },
            testTagPrefix = "multi_delete_step"
        )

        PrecisionMarkerSliderRow(
            label = "Finish",
            timeLabel = "Finish time",
            valueMs = finishMs,
            stepMs = stepMs,
            minMs = (startMs + 250L).coerceAtMost(safeDuration),
            maxMs = safeDuration,
            onValueChange = { val (s, e) = VideoCutMath.clampEndCutMarker(startMs, it, safeDuration); startMs = s; finishMs = e },
            testTagPrefix = "multi_delete_finish"
        )

        PrecisionSummaryRow(
            startMs = startMs,
            finishMs = finishMs,
            clipDurationMs = (finishMs - startMs).coerceAtLeast(0L),
            tagPrefix = "multi_delete"
        )

        Button(
            onClick = {
                if (finishMs > startMs) {
                    val updated = (cutSegments + VideoCutSegment(startMs = startMs, endMs = finishMs))
                        .sortedBy { it.startMs }
                    onUpdateCutSegments(updated)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_multi_delete_add_part")
        ) {
            Icon(Icons.Default.ContentCut, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Add part")
        }

        Text(
            text = "Part list (${cutSegments.size})",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("text_multi_delete_part_list")
        )

        cutSegments.forEachIndexed { idx, part ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_multi_delete_part_$idx"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Part ${idx + 1}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = "${AccessibilityHelper.formatTimePrecise(part.startMs)} → ${AccessibilityHelper.formatTimePrecise(part.endMs)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    IconButton(
                        onClick = { onUpdateCutSegments(cutSegments.filterNot { it.id == part.id }) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("btn_remove_multi_delete_part_$idx")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove part ${idx + 1}")
                    }
                }
            }
        }
    }
}
