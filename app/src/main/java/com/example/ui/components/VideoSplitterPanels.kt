package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.accessibility.AccessibilityHelper
import com.example.ui.theme.LocalHighContrast
import com.example.video.AutoSplitMode
import com.example.video.VideoCutMath
import kotlin.math.roundToInt

/**
 * Standardized Step Slider across all video trimming/editing/splitting tools.
 * Swipe/move UP to increase step into seconds and minutes; swipe/move DOWN to decrease into milliseconds.
 * Exposed consistently regardless of total video duration (seconds, minutes, or hours).
 */
@Composable
fun StandardizedStepSlider(
    stepMs: Long,
    onStepChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "step"
) {
    val isHighContrast = LocalHighContrast.current
    val options = VideoCutMath.STANDARDIZED_STEP_OPTIONS
    val currentIndex = VideoCutMath.standardizedStepIndexFor(stepMs)
    val currentOption = VideoCutMath.standardizedStepAt(currentIndex)
    var accumulatedDragY by remember { mutableFloatStateOf(0f) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("${testTagPrefix}_slider_container")
            .pointerInput(currentIndex) {
                detectVerticalDragGestures(
                    onDragEnd = { accumulatedDragY = 0f },
                    onDragCancel = { accumulatedDragY = 0f }
                ) { _, dragAmount ->
                    accumulatedDragY += dragAmount
                    val thresholdPx = 28f
                    if (accumulatedDragY <= -thresholdPx) {
                        // Swiping UP -> increase step (ms -> s -> min)
                        val stepped = VideoCutMath.stepUp(VideoCutMath.standardizedStepAt(currentIndex).stepMs)
                        if (stepped.stepMs != stepMs) {
                            onStepChange(stepped.stepMs)
                        }
                        accumulatedDragY = 0f
                    } else if (accumulatedDragY >= thresholdPx) {
                        // Swiping DOWN -> decrease step (min -> s -> ms)
                        val stepped = VideoCutMath.stepDown(VideoCutMath.standardizedStepAt(currentIndex).stepMs)
                        if (stepped.stepMs != stepMs) {
                            onStepChange(stepped.stepMs)
                        }
                        accumulatedDragY = 0f
                    }
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isHighContrast) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            if (isHighContrast) 2.dp else 1.dp,
            if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Step",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Swipe up: seconds/minutes • Swipe down: milliseconds",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${currentOption.shortLabel} (${currentOption.unitCategory.label})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("${testTagPrefix}_value_label")
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = { onStepChange(VideoCutMath.stepDown(stepMs).stepMs) },
                    enabled = currentIndex > 0,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("${testTagPrefix}_btn_down")
                        .semantics { contentDescription = "Decrease step toward milliseconds" }
                ) {
                    Icon(Icons.Default.Remove, contentDescription = null)
                }

                Slider(
                    value = currentIndex.toFloat(),
                    onValueChange = { raw ->
                        val idx = raw.roundToInt().coerceIn(0, options.lastIndex)
                        onStepChange(options[idx].stepMs)
                    },
                    valueRange = 0f..options.lastIndex.toFloat(),
                    steps = (options.size - 2).coerceAtLeast(0),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("${testTagPrefix}_slider")
                        .semantics {
                            contentDescription = "Step slider. Swipe up for seconds or minutes, swipe down for milliseconds."
                            stateDescription = "${currentOption.spokenLabel}, ${currentOption.unitCategory.label}"
                            customActions = listOf(
                                CustomAccessibilityAction("Step up toward minutes") {
                                    onStepChange(VideoCutMath.stepUp(stepMs).stepMs)
                                    true
                                },
                                CustomAccessibilityAction("Step down toward milliseconds") {
                                    onStepChange(VideoCutMath.stepDown(stepMs).stepMs)
                                    true
                                }
                            )
                        }
                )

                IconButton(
                    onClick = { onStepChange(VideoCutMath.stepUp(stepMs).stepMs) },
                    enabled = currentIndex < options.lastIndex,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("${testTagPrefix}_btn_up")
                        .semantics { contentDescription = "Increase step toward seconds and minutes" }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                }
            }
        }
    }
}

@Composable
fun VideoSplitterPanel(
    durationMs: Long,
    currentPositionMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    splitPointsMs: List<Long>,
    onUpdateSplitPoints: (List<Long>) -> Unit,
    onPlayFrom: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeDuration = durationMs.coerceAtLeast(1000L)
    var stepMs by remember { mutableStateOf(1000L) }
    var pointMs by remember(durationMs) {
        mutableStateOf((currentPositionMs.coerceIn(trimStartMs, trimEndMs.coerceAtLeast(trimStartMs))))
    }

    val resultingParts = remember(trimStartMs, trimEndMs, splitPointsMs) {
        VideoCutMath.splitPointsToRanges(trimStartMs, trimEndMs.coerceAtLeast(trimStartMs), splitPointsMs)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("panel_video_splitter"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Video Splitter",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Move Point to each split position and tap Add split point.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { pointMs = currentPositionMs.coerceIn(0L, safeDuration) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("btn_splitter_move_point_now")
            ) {
                Text("Move Point slider to now playing", maxLines = 2)
            }
            Button(
                onClick = { onPlayFrom(pointMs) },
                modifier = Modifier
                    .height(48.dp)
                    .testTag("btn_splitter_play")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Play")
            }
        }

        StandardizedStepSlider(
            stepMs = stepMs,
            onStepChange = { stepMs = it },
            testTagPrefix = "splitter_step"
        )

        PrecisionMarkerSliderRow(
            label = "Point",
            timeLabel = "Point time",
            valueMs = pointMs,
            stepMs = stepMs,
            minMs = 0L,
            maxMs = safeDuration,
            onValueChange = { pointMs = it },
            testTagPrefix = "splitter_point"
        )

        Button(
            onClick = {
                val clamped = pointMs.coerceIn(1L, (safeDuration - 1L).coerceAtLeast(1L))
                val updated = (splitPointsMs + clamped).distinct().sorted()
                onUpdateSplitPoints(updated)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_splitter_add_point")
        ) {
            Icon(Icons.Default.CallSplit, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Add split point")
        }

        Text(
            text = "Point list (${splitPointsMs.size} points • ${resultingParts.size} parts)",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("text_splitter_point_list_header")
        )

        if (splitPointsMs.isEmpty()) {
            Text(
                text = "No split points added yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("text_splitter_point_list_empty")
            )
        } else {
            splitPointsMs.sorted().forEachIndexed { idx, pt ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_split_point_$idx"),
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
                            Text(
                                text = "Split point ${idx + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Point time: ${AccessibilityHelper.formatTimePrecise(pt)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(
                            onClick = { onUpdateSplitPoints(splitPointsMs.filterNot { it == pt }) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("btn_remove_split_point_$idx")
                                .semantics { contentDescription = "Remove split point ${idx + 1}" }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoAutoSplitterPanel(
    durationMs: Long,
    currentPositionMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    autoSplitMode: AutoSplitMode,
    autoSplitIntervalMs: Long,
    autoSplitFileCount: Int,
    autoSplitTargetSizeMb: Float,
    estimatedTotalBytes: Long,
    onUpdateBounds: (startMs: Long, endMs: Long) -> Unit,
    onUpdateAutoSplitConfig: (AutoSplitMode, Long, Int, Float) -> Unit,
    onPlaySelection: (Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeDuration = durationMs.coerceAtLeast(1000L)
    var stepMs by remember { mutableStateOf(1000L) }
    var showCustomDialog by remember { mutableStateOf(false) }

    val clampedStart = trimStartMs.coerceIn(0L, safeDuration)
    val clampedEnd = trimEndMs.coerceIn((clampedStart + 250L).coerceAtMost(safeDuration), safeDuration)
    val clipDurationMs = (clampedEnd - clampedStart).coerceAtLeast(0L)

    val previewSegments = remember(
        clampedStart,
        clampedEnd,
        autoSplitMode,
        autoSplitIntervalMs,
        autoSplitFileCount,
        autoSplitTargetSizeMb,
        safeDuration,
        estimatedTotalBytes
    ) {
        when (autoSplitMode) {
            AutoSplitMode.BY_LENGTH -> VideoCutMath.autoSplitRangesByLength(clampedStart, clampedEnd, autoSplitIntervalMs)
            AutoSplitMode.BY_FILE_COUNT -> VideoCutMath.autoSplitRangesByCount(clampedStart, clampedEnd, autoSplitFileCount)
            AutoSplitMode.BY_FILE_SIZE -> VideoCutMath.autoSplitRangesByTargetSize(
                clampedStart,
                clampedEnd,
                autoSplitTargetSizeMb,
                safeDuration,
                estimatedTotalBytes
            )
        }
    }

    val splitEverySummary = when (autoSplitMode) {
        AutoSplitMode.BY_LENGTH -> "${autoSplitIntervalMs / 1000}s interval"
        AutoSplitMode.BY_FILE_COUNT -> "$autoSplitFileCount equal files"
        AutoSplitMode.BY_FILE_SIZE -> "${autoSplitTargetSizeMb.roundToInt()} MB max"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("panel_video_auto_splitter"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Video Auto splitter",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        MovePlayMoveRow(
            onMoveStartNow = {
                val (s, e) = VideoCutMath.clampStartCutMarker(currentPositionMs, clampedEnd, safeDuration)
                onUpdateBounds(s, e)
            },
            onPlay = { onPlaySelection(clampedStart, clampedEnd) },
            onMoveFinishNow = {
                val (s, e) = VideoCutMath.clampEndCutMarker(clampedStart, currentPositionMs, safeDuration)
                onUpdateBounds(s, e)
            },
            tagPrefix = "auto_splitter"
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
                onUpdateBounds(s, e)
            },
            testTagPrefix = "auto_splitter_start"
        )

        StandardizedStepSlider(
            stepMs = stepMs,
            onStepChange = { stepMs = it },
            testTagPrefix = "auto_splitter_step"
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
                onUpdateBounds(s, e)
            },
            testTagPrefix = "auto_splitter_finish"
        )

        PrecisionSummaryRow(
            startMs = clampedStart,
            finishMs = clampedEnd,
            clipDurationMs = clipDurationMs,
            tagPrefix = "auto_splitter"
        )

        // Split every button: tap cycles preset intervals; double-tap opens custom dialog (length / count / size)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("btn_auto_split_every")
                .combinedClickable(
                    onClick = {
                        val presets = listOf(10_000L, 30_000L, 60_000L, 120_000L, 300_000L)
                        val idx = presets.indexOf(autoSplitIntervalMs)
                        val nextInterval = presets[(idx + 1) % presets.size]
                        onUpdateAutoSplitConfig(
                            AutoSplitMode.BY_LENGTH,
                            nextInterval,
                            autoSplitFileCount,
                            autoSplitTargetSizeMb
                        )
                    },
                    onDoubleClick = { showCustomDialog = true }
                )
                .semantics {
                    contentDescription = "Split every: $splitEverySummary. Tap to cycle interval, double tap for custom length, file count, or size."
                    customActions = listOf(
                        CustomAccessibilityAction("Custom split length, count, or size") {
                            showCustomDialog = true
                            true
                        }
                    )
                },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null)
                    Column {
                        Text(
                            text = "Split every: $splitEverySummary",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Double-tap Split every for custom length, files, or size",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Text(
                    text = "${previewSegments.size} parts",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (showCustomDialog) {
            AutoSplitCustomDialog(
                initialMode = autoSplitMode,
                initialIntervalMs = autoSplitIntervalMs,
                initialFileCount = autoSplitFileCount,
                initialTargetSizeMb = autoSplitTargetSizeMb,
                onDismiss = { showCustomDialog = false },
                onConfirm = { mode, intervalMs, count, sizeMb ->
                    onUpdateAutoSplitConfig(mode, intervalMs, count, sizeMb)
                    showCustomDialog = false
                }
            )
        }
    }
}

@Composable
private fun AutoSplitCustomDialog(
    initialMode: AutoSplitMode,
    initialIntervalMs: Long,
    initialFileCount: Int,
    initialTargetSizeMb: Float,
    onDismiss: () -> Unit,
    onConfirm: (AutoSplitMode, Long, Int, Float) -> Unit
) {
    var mode by remember { mutableStateOf(initialMode) }
    var lengthSecondsText by remember { mutableStateOf((initialIntervalMs / 1000L).coerceAtLeast(1L).toString()) }
    var countText by remember { mutableStateOf(initialFileCount.coerceAtLeast(1).toString()) }
    var sizeMbText by remember { mutableStateOf(initialTargetSizeMb.roundToInt().coerceAtLeast(1).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("dialog_auto_split_custom"),
        title = { Text("Custom Split Every", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AutoSplitMode.entries.forEach { entry ->
                    OutlinedButton(
                        onClick = { mode = entry },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_auto_split_mode_${entry.name.lowercase()}"),
                        border = BorderStroke(
                            if (mode == entry) 2.dp else 1.dp,
                            if (mode == entry) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Text(
                            text = if (mode == entry) "✓ ${entry.label}" else entry.label,
                            fontWeight = if (mode == entry) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                when (mode) {
                    AutoSplitMode.BY_LENGTH -> OutlinedTextField(
                        value = lengthSecondsText,
                        onValueChange = { lengthSecondsText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Custom length (seconds)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_auto_split_seconds")
                    )
                    AutoSplitMode.BY_FILE_COUNT -> OutlinedTextField(
                        value = countText,
                        onValueChange = { countText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Number of files") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_auto_split_count")
                    )
                    AutoSplitMode.BY_FILE_SIZE -> OutlinedTextField(
                        value = sizeMbText,
                        onValueChange = { sizeMbText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Target file size (MB)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_auto_split_size_mb")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sec = (lengthSecondsText.toLongOrNull() ?: 30L).coerceIn(1L, 36_000L)
                    val cnt = (countText.toIntOrNull() ?: 2).coerceIn(1, 200)
                    val mb = (sizeMbText.toFloatOrNull() ?: 25f).coerceIn(1f, 4096f)
                    onConfirm(mode, sec * 1000L, cnt, mb)
                },
                modifier = Modifier.testTag("btn_confirm_auto_split_custom")
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
