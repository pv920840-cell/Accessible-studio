package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.accessibility.AccessibilityHelper
import com.example.video.VideoCutMath
import com.example.video.VideoCutSegment

@Composable
fun TimeEditDialog(
    title: String,
    initialMs: Long,
    minMs: Long,
    maxMs: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val safeMin = minMs.coerceAtLeast(0L)
    val safeMax = maxMs.coerceAtLeast(safeMin)
    val clampedInitial = initialMs.coerceIn(safeMin, safeMax)
    val initMinutes = clampedInitial / 60_000L
    val initSeconds = (clampedInitial % 60_000L) / 1_000L
    val initMillis = clampedInitial % 1_000L

    var minutesText by remember(clampedInitial) { mutableStateOf(initMinutes.toString()) }
    var secondsText by remember(clampedInitial) { mutableStateOf(initSeconds.toString()) }
    var millisText by remember(clampedInitial) { mutableStateOf(initMillis.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("dialog_time_edit"),
        title = {
            Text(
                text = "Set $title",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter Minutes, Seconds, and Milliseconds (range ${AccessibilityHelper.formatTimePrecise(safeMin)} to ${AccessibilityHelper.formatTimePrecise(safeMax)}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = { minutesText = it.filter { ch -> ch.isDigit() }.take(4) },
                        label = { Text("Minutes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_time_edit_minutes")
                    )
                    OutlinedTextField(
                        value = secondsText,
                        onValueChange = { secondsText = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("Seconds") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_time_edit_seconds")
                    )
                    OutlinedTextField(
                        value = millisText,
                        onValueChange = { millisText = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("Millis") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_time_edit_millis")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val m = (minutesText.toLongOrNull() ?: 0L).coerceAtLeast(0L)
                    val s = (secondsText.toLongOrNull() ?: 0L).coerceIn(0L, 59_999L)
                    val ms = (millisText.toLongOrNull() ?: 0L).coerceIn(0L, 999L)
                    val total = (m * 60_000L + s * 1_000L + ms).coerceIn(safeMin, safeMax)
                    onConfirm(total)
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("btn_time_edit_ok")
            ) {
                Text("OK", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("btn_time_edit_cancel")
            ) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
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
        Surface(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("btn_${tagPrefix}_move_start_now")
                .combinedClickable(
                    role = Role.Button,
                    onClick = onMoveStartNow,
                    onDoubleClick = onMoveStartNow
                )
                .semantics {
                    contentDescription = "Move Start slider to now playing. Double tap to place Start marker at current playback position."
                },
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Move Start slider to now playing",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
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
        Surface(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("btn_${tagPrefix}_move_finish_now")
                .combinedClickable(
                    role = Role.Button,
                    onClick = onMoveFinishNow,
                    onDoubleClick = onMoveFinishNow
                )
                .semantics {
                    contentDescription = "Move Finish slider to now playing. Double tap to place Finish marker at current playback position."
                },
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Move Finish slider to now playing",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
    var showTimeDialog by remember { mutableStateOf(false) }
    val latestShowDialog by rememberUpdatedState { showTimeDialog = true }

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
                modifier = Modifier
                    .testTag("text_${testTagPrefix}_time")
                    .combinedClickable(
                        onClick = {},
                        onDoubleClick = { showTimeDialog = true }
                    )
                    .semantics {
                        contentDescription = "$timeLabel is ${AccessibilityHelper.formatTimeSpoken(clamped)}. Double tap to enter exact minutes, seconds, and milliseconds."
                        customActions = listOf(
                            CustomAccessibilityAction("Set $timeLabel manually") {
                                showTimeDialog = true
                                true
                            }
                        )
                    }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(Unit) {
                        val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
                        val touchSlop = viewConfiguration.touchSlop
                        var lastTapUpTime = 0L
                        var lastTapUpPos = Offset.Zero
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                val downTime = down.uptimeMillis
                                val downPos = down.position
                                var moved = false
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if ((change.position - downPos).getDistance() > touchSlop) {
                                        moved = true
                                    }
                                    if (!change.pressed) {
                                        val upTime = change.uptimeMillis
                                        if (!moved && (upTime - downTime) < doubleTapTimeout) {
                                            val isDoubleTap = lastTapUpTime > 0L &&
                                                (downTime - lastTapUpTime) <= doubleTapTimeout &&
                                                (downPos - lastTapUpPos).getDistance() <= (touchSlop * 4f)
                                            if (isDoubleTap) {
                                                lastTapUpTime = 0L
                                                latestShowDialog()
                                            } else {
                                                lastTapUpTime = upTime
                                                lastTapUpPos = change.position
                                            }
                                        } else {
                                            lastTapUpTime = 0L
                                        }
                                        break
                                    }
                                }
                            }
                        }
                    }
            ) {
                Slider(
                    value = clamped.toFloat(),
                    onValueChange = { raw ->
                        onValueChange(raw.toLong().coerceIn(safeMin, safeMax))
                    },
                    valueRange = safeMin.toFloat()..safeMax.toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("slider_${testTagPrefix}")
                        .semantics {
                            contentDescription = "$label slider. $timeLabel is ${AccessibilityHelper.formatTimeSpoken(clamped)}. Double tap to set exact time."
                            customActions = listOf(
                                CustomAccessibilityAction("Set exact $timeLabel") {
                                    showTimeDialog = true
                                    true
                                }
                            )
                        }
                )
            }
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

    if (showTimeDialog) {
        TimeEditDialog(
            title = timeLabel,
            initialMs = clamped,
            minMs = safeMin,
            maxMs = safeMax,
            onConfirm = { updated ->
                onValueChange(updated)
                showTimeDialog = false
            },
            onDismiss = { showTimeDialog = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PrecisionSummaryRow(
    startMs: Long,
    finishMs: Long,
    clipDurationMs: Long,
    tagPrefix: String,
    startMinMs: Long = 0L,
    startMaxMs: Long = finishMs,
    finishMinMs: Long = startMs,
    finishMaxMs: Long = Long.MAX_VALUE / 2,
    onEditStartMs: ((Long) -> Unit)? = null,
    onEditFinishMs: ((Long) -> Unit)? = null
) {
    var editingTarget by remember { mutableStateOf<String?>(null) }

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
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .testTag("readout_${tagPrefix}_start_time")
                        .combinedClickable(
                            onClick = {},
                            onDoubleClick = { if (onEditStartMs != null) editingTarget = "start" }
                        )
                        .semantics {
                            contentDescription = "Start time ${AccessibilityHelper.formatTimeSpoken(startMs)}. Double tap to edit minutes, seconds, and milliseconds."
                            if (onEditStartMs != null) {
                                customActions = listOf(
                                    CustomAccessibilityAction("Edit Start time") {
                                        editingTarget = "start"
                                        true
                                    }
                                )
                            }
                        }
                        .padding(vertical = 2.dp)
                )
                Text(
                    text = "Finish time: ${AccessibilityHelper.formatTimePrecise(finishMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .testTag("readout_${tagPrefix}_finish_time")
                        .combinedClickable(
                            onClick = {},
                            onDoubleClick = { if (onEditFinishMs != null) editingTarget = "finish" }
                        )
                        .semantics {
                            contentDescription = "Finish time ${AccessibilityHelper.formatTimeSpoken(finishMs)}. Double tap to edit minutes, seconds, and milliseconds."
                            if (onEditFinishMs != null) {
                                customActions = listOf(
                                    CustomAccessibilityAction("Edit Finish time") {
                                        editingTarget = "finish"
                                        true
                                    }
                                )
                            }
                        }
                        .padding(vertical = 2.dp)
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

    if (editingTarget == "start" && onEditStartMs != null) {
        TimeEditDialog(
            title = "Start time",
            initialMs = startMs,
            minMs = startMinMs,
            maxMs = startMaxMs,
            onConfirm = {
                onEditStartMs(it)
                editingTarget = null
            },
            onDismiss = { editingTarget = null }
        )
    } else if (editingTarget == "finish" && onEditFinishMs != null) {
        TimeEditDialog(
            title = "Finish time",
            initialMs = finishMs,
            minMs = finishMinMs,
            maxMs = finishMaxMs,
            onConfirm = {
                onEditFinishMs(it)
                editingTarget = null
            },
            onDismiss = { editingTarget = null }
        )
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
            tagPrefix = "trimmer",
            startMinMs = 0L,
            startMaxMs = (clampedEnd - 250L).coerceAtLeast(0L),
            finishMinMs = (clampedStart + 250L).coerceAtMost(safeDuration),
            finishMaxMs = safeDuration,
            onEditStartMs = { newStart ->
                val (s, e) = VideoCutMath.clampStartCutMarker(newStart, clampedEnd, safeDuration)
                onUpdateTrim(s, e, streamCopyTrim)
            },
            onEditFinishMs = { newEnd ->
                val (s, e) = VideoCutMath.clampEndCutMarker(clampedStart, newEnd, safeDuration)
                onUpdateTrim(s, e, streamCopyTrim)
            }
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
            tagPrefix = "multi_trim",
            startMinMs = 0L,
            startMaxMs = (finishMs - 250L).coerceAtLeast(0L),
            finishMinMs = (startMs + 250L).coerceAtMost(safeDuration),
            finishMaxMs = safeDuration,
            onEditStartMs = { val (s, e) = VideoCutMath.clampStartCutMarker(it, finishMs, safeDuration); startMs = s; finishMs = e },
            onEditFinishMs = { val (s, e) = VideoCutMath.clampEndCutMarker(startMs, it, safeDuration); startMs = s; finishMs = e }
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
            tagPrefix = "delete_part",
            startMinMs = 0L,
            startMaxMs = (finishMs - 250L).coerceAtLeast(0L),
            finishMinMs = (startMs + 250L).coerceAtMost(safeDuration),
            finishMaxMs = safeDuration,
            onEditStartMs = {
                val (s, e) = VideoCutMath.clampStartCutMarker(it, finishMs, safeDuration)
                startMs = s; finishMs = e
                onUpdateCutSegments(listOf(VideoCutSegment(startMs = s, endMs = e)))
            },
            onEditFinishMs = {
                val (s, e) = VideoCutMath.clampEndCutMarker(startMs, it, safeDuration)
                startMs = s; finishMs = e
                onUpdateCutSegments(listOf(VideoCutSegment(startMs = s, endMs = e)))
            }
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
            tagPrefix = "multi_delete",
            startMinMs = 0L,
            startMaxMs = (finishMs - 250L).coerceAtLeast(0L),
            finishMinMs = (startMs + 250L).coerceAtMost(safeDuration),
            finishMaxMs = safeDuration,
            onEditStartMs = { val (s, e) = VideoCutMath.clampStartCutMarker(it, finishMs, safeDuration); startMs = s; finishMs = e },
            onEditFinishMs = { val (s, e) = VideoCutMath.clampEndCutMarker(startMs, it, safeDuration); startMs = s; finishMs = e }
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
