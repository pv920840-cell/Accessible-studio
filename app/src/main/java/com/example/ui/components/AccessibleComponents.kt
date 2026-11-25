package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.example.audio.ScrubDirection
import kotlin.math.abs
import kotlin.math.roundToLong
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.accessibility.AccessibilityHelper
import com.example.accessibility.VibrationType
import com.example.data.StudioPreferences
import com.example.ui.components.AudioVisualMode
import com.example.ui.theme.LocalHighContrast
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

@Composable
fun AccessibleTimeReadout(
    currentMs: Long,
    durationMs: Long,
    label: String = "Current Playhead",
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    positionFlow: StateFlow<Long>? = null
) {
    val isHighContrast = LocalHighContrast.current
    val livePosMs = if (positionFlow != null) positionFlow.collectAsState().value else currentMs
    val spokenTotal = remember(durationMs) { AccessibilityHelper.formatTimeSpoken(durationMs) }

    val cardSemantics = if (isPlaying) {
        Modifier.clearAndSetSemantics {
            contentDescription = "$label, playing, total $spokenTotal"
        }
    } else {
        val spokenCurrent = AccessibilityHelper.formatTimeSpoken(livePosMs)
        Modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label: $spokenCurrent of total $spokenTotal"
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("time_readout_card")
            .then(cardSemantics),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(
            if (isHighContrast) 2.dp else 1.dp,
            if (isHighContrast) Color.White else MaterialTheme.colorScheme.outlineVariant
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = AccessibilityHelper.formatTimeVisual(livePosMs),
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Black
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "TOTAL DURATION",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = AccessibilityHelper.formatTimeVisual(durationMs),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

data class WaveformViewportSnapshot(
    val startMs: Long,
    val spanMs: Long?, // null means Fit All (full duration)
    val verticalZoom: Float
)

data class ResolvedWaveformWindow(
    val startMs: Long,
    val endMs: Long,
    val spanMs: Long,
    val zoomFactor: Float
)

object WaveformViewportMath {
    fun resolveWindow(
        durationMs: Long,
        viewStartMs: Long,
        viewSpanMs: Long?,
        currentMs: Long,
        followCursor: Boolean
    ): ResolvedWaveformWindow {
        val safeDuration = durationMs.coerceAtLeast(100L)
        val effectiveSpan = (viewSpanMs ?: safeDuration).coerceIn(50L, safeDuration)
        val maxStart = (safeDuration - effectiveSpan).coerceAtLeast(0L)
        val clampedStart = if (viewSpanMs == null) {
            0L
        } else if (followCursor && (currentMs < viewStartMs || currentMs > viewStartMs + effectiveSpan)) {
            (currentMs - effectiveSpan / 2L).coerceIn(0L, maxStart)
        } else {
            viewStartMs.coerceIn(0L, maxStart)
        }
        val endMs = (clampedStart + effectiveSpan).coerceAtMost(safeDuration)
        val zoomFactor = (safeDuration.toDouble() / effectiveSpan.toDouble()).toFloat().coerceAtLeast(1.0f)
        return ResolvedWaveformWindow(
            startMs = clampedStart,
            endMs = endMs,
            spanMs = effectiveSpan,
            zoomFactor = zoomFactor
        )
    }

    fun formatWindowLabel(spanMs: Long, durationMs: Long): String {
        return when {
            durationMs > 0L && spanMs >= durationMs -> "All"
            spanMs >= 3_600_000L -> "${"%.1f".format(java.util.Locale.US, spanMs / 3_600_000.0)}h"
            spanMs >= 60_000L -> "${"%.1f".format(java.util.Locale.US, spanMs / 60_000.0)}m"
            else -> "${"%.1f".format(java.util.Locale.US, spanMs / 1000.0)}s"
        }
    }
}

@Composable
fun AccessibleAudioWaveform(
    currentMs: Long,
    durationMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    onSeek: (Long) -> Unit,
    samples: ShortArray? = null,
    visualMode: AudioVisualMode = AudioVisualMode.WAVEFORM,
    onToggleVisualMode: (() -> Unit)? = null,
    zoomLevel: Float? = null,
    onZoomChange: ((Float) -> Unit)? = null,
    windowStartMsOverride: Long? = null,
    windowSpanMsOverride: Long? = null,
    verticalZoom: Float? = null,
    followCursor: Boolean = true,
    onVerticalZoomChange: ((Float) -> Unit)? = null,
    onZoomInAction: (() -> Unit)? = null,
    onZoomOutAction: (() -> Unit)? = null,
    onZoomFitAction: (() -> Unit)? = null,
    visualModeEnabled: Boolean = true,
    showWaveformDisplay: Boolean = true,
    showZoomControls: Boolean = true,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    positionFlow: StateFlow<Long>? = null
) {
    val isHighContrast = LocalHighContrast.current
    val livePosMs = if (positionFlow != null) positionFlow.collectAsState().value else currentMs
    val safeDuration = durationMs.coerceAtLeast(100L)
    val numBars = 64
    var internalZoom by remember { mutableFloatStateOf(1.0f) }
    var internalVerticalZoom by remember { mutableFloatStateOf(1.0f) }

    val activeVerticalZoom = (verticalZoom ?: internalVerticalZoom).coerceIn(0.25f, 8.0f)
    val updateVerticalZoom: (Float) -> Unit = { newV ->
        val clamped = newV.coerceIn(0.25f, 8.0f)
        if (onVerticalZoomChange != null) onVerticalZoomChange(clamped) else internalVerticalZoom = clamped
    }

    val fallbackZoom = (zoomLevel ?: internalZoom).coerceAtLeast(1.0f)
    val resolvedWindow = if (windowStartMsOverride != null || windowSpanMsOverride != null) {
        WaveformViewportMath.resolveWindow(
            durationMs = safeDuration,
            viewStartMs = windowStartMsOverride ?: 0L,
            viewSpanMs = windowSpanMsOverride,
            currentMs = livePosMs,
            followCursor = followCursor
        )
    } else {
        val span = (safeDuration.toDouble() / fallbackZoom.toDouble()).toLong().coerceAtLeast(50L)
        WaveformViewportMath.resolveWindow(
            durationMs = safeDuration,
            viewStartMs = (livePosMs - span / 2L).coerceAtLeast(0L),
            viewSpanMs = if (fallbackZoom <= 1.01f) null else span,
            currentMs = livePosMs,
            followCursor = followCursor
        )
    }

    val activeZoom = resolvedWindow.zoomFactor
    val windowSpanMs = resolvedWindow.spanMs
    val windowStartMs = resolvedWindow.startMs
    val windowEndMs = resolvedWindow.endMs

    val updateFallbackZoom: (Float) -> Unit = { newZ ->
        val clamped = newZ.coerceIn(1.0f, 128.0f)
        if (onZoomChange != null) onZoomChange(clamped) else internalZoom = clamped
    }

    // Real PCM envelope amplitudes sliced over the visible zoomed window
    val barAmplitudes = remember(safeDuration, samples?.size, visualMode, windowStartMs, windowEndMs) {
        if (samples != null && samples.isNotEmpty()) {
            val startFrac = (windowStartMs.toDouble() / safeDuration.toDouble()).coerceIn(0.0, 1.0)
            val endFrac = (windowEndMs.toDouble() / safeDuration.toDouble()).coerceIn(startFrac, 1.0)
            val sliceStart = (startFrac * samples.size).toInt().coerceIn(0, (samples.size - 1).coerceAtLeast(0))
            val sliceEnd = (endFrac * samples.size).toInt().coerceIn(sliceStart + 1, samples.size)
            val sliceLen = (sliceEnd - sliceStart).coerceAtLeast(1)

            FloatArray(numBars) { i ->
                val s0 = sliceStart + ((i.toLong() * sliceLen) / numBars).toInt()
                val s1 = sliceStart + (((i + 1).toLong() * sliceLen) / numBars).toInt().coerceAtLeast(s0 + 1)
                val safeS1 = s1.coerceAtMost(samples.size)
                var peak = 0
                val innerStep = ((safeS1 - s0) / 16).coerceAtLeast(1)
                var k = s0
                while (k < safeS1) {
                    val a = kotlin.math.abs(samples[k].toInt())
                    if (a > peak) peak = a
                    k += innerStep
                }
                val raw = (peak / 32767f).coerceIn(0.06f, 1.0f)
                if (visualMode == AudioVisualMode.SPECTRUM) {
                    (raw * (0.65f + 0.35f * kotlin.math.abs(kotlin.math.sin(i * 0.35).toFloat()))).coerceIn(0.08f, 1.0f)
                } else {
                    raw
                }
            }
        } else if (samples != null && samples.isEmpty()) {
            FloatArray(numBars) { 0.06f }
        } else {
            FloatArray(numBars) { i ->
                val angle = i.toDouble() * 0.4
                (0.12f + 0.35f * Math.abs(Math.sin(angle) * Math.cos(angle * 1.5))).toFloat()
            }
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val spectrumColor = if (isHighContrast) Color.Yellow else MaterialTheme.colorScheme.secondary
    val barsColor = if (isHighContrast) Color.Cyan else MaterialTheme.colorScheme.tertiary
    val activeTrimColor = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
    val inactiveColor = if (isHighContrast) Color(0xFF333333) else MaterialTheme.colorScheme.surfaceVariant
    val baselineColor = (if (isHighContrast) Color.White else MaterialTheme.colorScheme.outlineVariant).copy(alpha = 0.45f)

    val canRenderWaveform = visualModeEnabled && showWaveformDisplay

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (canRenderWaveform) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surface)
                    .border(
                        if (isHighContrast) 2.dp else 1.dp,
                        if (isHighContrast) Color.White else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(14.dp)
                    )
                    .testTag("audio_waveform_view")
                    .semantics {
                        contentDescription = if (isPlaying) {
                            "Interactive audio ${visualMode.displayName}, playing. Selection from ${AccessibilityHelper.formatTimeSpoken(trimStartMs)} to ${AccessibilityHelper.formatTimeSpoken(trimEndMs)}. View window ${WaveformViewportMath.formatWindowLabel(windowSpanMs, safeDuration)}, amplitude scale ${"%.1f".format(Locale.US, activeVerticalZoom)}x."
                        } else {
                            "Interactive audio ${visualMode.displayName}. Playhead at ${AccessibilityHelper.formatTimeSpoken(livePosMs)}. Selection from ${AccessibilityHelper.formatTimeSpoken(trimStartMs)} to ${AccessibilityHelper.formatTimeSpoken(trimEndMs)}. View window ${WaveformViewportMath.formatWindowLabel(windowSpanMs, safeDuration)}, amplitude scale ${"%.1f".format(Locale.US, activeVerticalZoom)}x."
                        }
                    }
                    .pointerInput(windowStartMs, windowSpanMs) {
                        detectTapGestures { offset ->
                            val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            val targetMs = windowStartMs + (fraction * windowSpanMs).toLong()
                            onSeek(targetMs.coerceIn(0L, safeDuration))
                        }
                    }
                    .pointerInput(windowStartMs, windowSpanMs) {
                        detectHorizontalDragGestures { change, _ ->
                            val fraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            val targetMs = windowStartMs + (fraction * windowSpanMs).toLong()
                            onSeek(targetMs.coerceIn(0L, safeDuration))
                        }
                    }
            ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 12.dp)
            ) {
                val w = size.width
                val h = size.height
                val slotW = w / numBars
                val barWidth = (slotW * 0.65f).coerceAtLeast(2f)
                val span = windowSpanMs.coerceAtLeast(1L).toFloat()

                // Center baseline guide
                drawLine(
                    color = baselineColor,
                    start = Offset(0f, h / 2f),
                    end = Offset(w, h / 2f),
                    strokeWidth = 1.5f
                )

                val trimStartRatio = ((trimStartMs - windowStartMs).toFloat() / span).coerceIn(0f, 1f)
                val trimEndRatio = ((trimEndMs - windowStartMs).toFloat() / span).coerceIn(trimStartRatio, 1f)

                // Draw trim zone background highlight and clear handle lines
                val trimStartX = trimStartRatio * w
                val trimEndX = trimEndRatio * w
                if (trimEndX > trimStartX) {
                    drawRoundRect(
                        color = activeTrimColor.copy(alpha = if (isHighContrast) 0.35f else 0.16f),
                        topLeft = Offset(trimStartX, 0f),
                        size = Size(trimEndX - trimStartX, h),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                }
                if (trimStartMs in windowStartMs..windowEndMs) {
                    drawLine(
                        color = activeTrimColor,
                        start = Offset(trimStartX, 0f),
                        end = Offset(trimStartX, h),
                        strokeWidth = 3f
                    )
                }
                if (trimEndMs in windowStartMs..windowEndMs) {
                    drawLine(
                        color = activeTrimColor,
                        start = Offset(trimEndX, 0f),
                        end = Offset(trimEndX, h),
                        strokeWidth = 3f
                    )
                }

                // Draw bars based on selected Visual Mode
                for (i in 0 until numBars) {
                    val barTimeMs = windowStartMs + ((i.toFloat() / numBars.toFloat()) * span).toLong()
                    val isInsideTrim = barTimeMs in trimStartMs..trimEndMs
                    val barColor = when {
                        visualMode == AudioVisualMode.SPECTRUM && isInsideTrim -> spectrumColor
                        visualMode == AudioVisualMode.BARS && isInsideTrim -> barsColor
                        isInsideTrim -> if (isHighContrast) Color.White else primaryColor
                        else -> inactiveColor
                    }

                    val barH = (h * 0.88f * barAmplitudes[i] * activeVerticalZoom).coerceIn(6f, h * 0.98f)
                    val x = i * slotW + (slotW - barWidth) / 2f
                    val y = when (visualMode) {
                        AudioVisualMode.WAVEFORM -> (h - barH) / 2f
                        AudioVisualMode.SPECTRUM, AudioVisualMode.BARS -> h - barH
                    }

                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barH),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }

                // Draw current playhead indicator only when inside visible window
                if (livePosMs in windowStartMs..windowEndMs) {
                    val playheadRatio = ((livePosMs - windowStartMs).toFloat() / span).coerceIn(0f, 1f)
                    val playheadX = playheadRatio * w
                    drawLine(
                        color = if (isHighContrast) Color(0xFF00FF66) else Color(0xFFE53935),
                        start = Offset(playheadX, 0f),
                        end = Offset(playheadX, h),
                        strokeWidth = if (isHighContrast) 6f else 4f
                    )
                }
            }
            }
        } else {
            val hiddenReason = if (!visualModeEnabled) {
                "Visual Mode is Off — audio-first accessible controls active."
            } else {
                "Waveform Display is hidden in Settings."
            }
            Surface(
                color = if (isHighContrast) Color(0xFF121212) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (isHighContrast) Color.White else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("audio_visual_hidden_notice")
                    .semantics {
                        contentDescription = if (isPlaying) {
                            "$hiddenReason Playing. Selection from ${AccessibilityHelper.formatTimeSpoken(trimStartMs)} to ${AccessibilityHelper.formatTimeSpoken(trimEndMs)}."
                        } else {
                            "$hiddenReason Playhead at ${AccessibilityHelper.formatTimeSpoken(livePosMs)}. Selection from ${AccessibilityHelper.formatTimeSpoken(trimStartMs)} to ${AccessibilityHelper.formatTimeSpoken(trimEndMs)}."
                        }
                    }
            ) {
                Text(
                    text = hiddenReason,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
        }

        // View / Zoom / Visual Controls
        if (canRenderWaveform || showZoomControls) {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    border = if (isHighContrast) BorderStroke(1.dp, Color.White) else null
                ) {
                    val winText = WaveformViewportMath.formatWindowLabel(windowSpanMs, safeDuration)
                    val modeSuffix = if (canRenderWaveform) "  •  ${visualMode.displayName}" else "  •  Audio-First"
                    Text(
                        text = "Win:$winText (${"%.1f".format(activeZoom)}x)  •  V:${"%.1f".format(activeVerticalZoom)}x$modeSuffix",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }

                if (showZoomControls || (canRenderWaveform && onToggleVisualMode != null)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (showZoomControls) {
                            OutlinedButton(
                                onClick = { onZoomInAction?.invoke() ?: updateFallbackZoom(activeZoom * 2f) },
                                modifier = Modifier.height(48.dp).testTag("btn_zoom_in")
                                    .semantics { contentDescription = "Zoom In waveform view horizontally" },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)
                            ) { Text("Zoom +", fontWeight = FontWeight.Bold, fontSize = 11.sp) }

                            OutlinedButton(
                                onClick = { onZoomOutAction?.invoke() ?: updateFallbackZoom(activeZoom / 2f) },
                                modifier = Modifier.height(48.dp).testTag("btn_zoom_out")
                                    .semantics { contentDescription = "Zoom Out waveform view horizontally" },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)
                            ) { Text("Zoom -", fontWeight = FontWeight.Bold, fontSize = 11.sp) }

                            OutlinedButton(
                                onClick = {
                                    if (onZoomFitAction != null) {
                                        onZoomFitAction()
                                    } else {
                                        updateFallbackZoom(1.0f)
                                        updateVerticalZoom(1.0f)
                                    }
                                },
                                modifier = Modifier.height(48.dp).testTag("btn_zoom_full")
                                    .semantics { contentDescription = "Fit All. Resets horizontal and vertical zoom" },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)
                            ) { Text("Fit", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                        }

                        if (canRenderWaveform && onToggleVisualMode != null) {
                            OutlinedButton(
                                onClick = onToggleVisualMode,
                                modifier = Modifier.height(48.dp).testTag("btn_visual_mode")
                                    .semantics { contentDescription = "Visual mode: ${visualMode.displayName}. Tap to switch." },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)
                            ) { Text(visualMode.displayName, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccessibleTransportControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onStop: (() -> Unit)? = null,
    onRewind5s: (() -> Unit)? = null,
    onForward5s: (() -> Unit)? = null,
    isLooping: Boolean = false,
    onToggleLoop: (() -> Unit)? = null,
    onNudge: (Long) -> Unit,
    onJumpStart: () -> Unit,
    onJumpEnd: () -> Unit,
    onPlaySelection: (() -> Unit)? = null,
    onPlayContinuous: (() -> Unit)? = null,
    onPlayLoop: (() -> Unit)? = null,
    activeScrubDirection: ScrubDirection = ScrubDirection.NONE,
    scrubSpeed: Float = 3.5f,
    onStartScrubRewind: (() -> Unit)? = null,
    onStartScrubForward: (() -> Unit)? = null,
    onStopScrub: (() -> Unit)? = null,
    onToggleScrubRewind: (() -> Unit)? = null,
    onToggleScrubForward: (() -> Unit)? = null,
    onChangeScrubSpeed: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isHighContrast = LocalHighContrast.current
    var selectedJumpIntervalSec by remember { mutableIntStateOf(5) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Active Audible Scrubbing Status Banner
        AnimatedVisibility(visible = activeScrubDirection != ScrubDirection.NONE) {
            Surface(
                color = if (activeScrubDirection == ScrubDirection.REWIND) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val bannerSpeedLabel = StudioPreferences.formatScrubSpeedLabel(scrubSpeed)
                    Text(
                        text = if (activeScrubDirection == ScrubDirection.REWIND) {
                            "<< REWINDING IN REVERSE (-$bannerSpeedLabel) • Playing Live"
                        } else {
                            "FAST FORWARDING (+$bannerSpeedLabel) >> • Playing Live"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = if (activeScrubDirection == ScrubDirection.REWIND) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Main tactile transport row: JumpStart, Rewind (-5s + hold for audible reverse rewind), Play/Pause, Stop, Forward (+5s + hold for audible fast forward), JumpEnd
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AccessibleIconButton(
                onClick = onJumpStart,
                icon = { Icon(Icons.Default.SkipPrevious, contentDescription = "Jump to beginning of track", modifier = Modifier.size(28.dp)) },
                contentDescription = "Jump to beginning of track",
                testTag = "btn_jump_start"
            )

            val speedLabel = StudioPreferences.formatScrubSpeedLabel(scrubSpeed)
            NudgeButton(
                label = if (activeScrubDirection == ScrubDirection.REWIND) "<< -${selectedJumpIntervalSec}s" else "-${selectedJumpIntervalSec}s",
                spokenDesc = if (activeScrubDirection == ScrubDirection.REWIND) {
                    "Rewinding in reverse at $speedLabel speed. Press again to return to normal playback."
                } else {
                    "Rewind at $speedLabel speed. Press once for continuous audible reverse rewind, press again for normal playback."
                },
                onClick = {
                    if (selectedJumpIntervalSec == 5 && onRewind5s != null) {
                        onRewind5s()
                    } else {
                        onNudge(-selectedJumpIntervalSec * 1000L)
                    }
                },
                testTag = "btn_rewind_5s",
                isActiveScrub = activeScrubDirection == ScrubDirection.REWIND,
                onToggleScrub = onToggleScrubRewind,
                jumpActionLabel = "Jump back $selectedJumpIntervalSec seconds once"
            )

            // Large Center Play / Pause button (minimum 68dp for accessibility)
            Button(
                onClick = onPlayPause,
                modifier = Modifier
                    .size(68.dp)
                    .testTag("btn_play_pause")
                    .semantics {
                        contentDescription = if (isPlaying) "Pause playback" else "Start playback"
                    },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.onPrimary
                ),
                border = if (isHighContrast) BorderStroke(3.dp, Color.White) else null,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
            }

            if (onStop != null) {
                Button(
                    onClick = onStop,
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("btn_transport_stop")
                        .semantics {
                            contentDescription = "Stop playback and reset to beginning"
                        },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isHighContrast) Color.White else MaterialTheme.colorScheme.secondary,
                        contentColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.onSecondary
                    ),
                    border = if (isHighContrast) BorderStroke(2.dp, Color.Black) else null
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            val fwdSpeedLabel = StudioPreferences.formatScrubSpeedLabel(scrubSpeed)
            NudgeButton(
                label = if (activeScrubDirection == ScrubDirection.FORWARD) "+${selectedJumpIntervalSec}s >>" else "+${selectedJumpIntervalSec}s",
                spokenDesc = if (activeScrubDirection == ScrubDirection.FORWARD) {
                    "Fast forwarding at $fwdSpeedLabel speed. Press again to return to normal playback."
                } else {
                    "Forward at $fwdSpeedLabel speed. Press once for continuous audible fast forward, press again for normal playback."
                },
                onClick = {
                    if (selectedJumpIntervalSec == 5 && onForward5s != null) {
                        onForward5s()
                    } else {
                        onNudge(selectedJumpIntervalSec * 1000L)
                    }
                },
                testTag = "btn_forward_5s",
                isActiveScrub = activeScrubDirection == ScrubDirection.FORWARD,
                onToggleScrub = onToggleScrubForward,
                jumpActionLabel = "Jump forward $selectedJumpIntervalSec seconds once"
            )

            AccessibleIconButton(
                onClick = onJumpEnd,
                icon = { Icon(Icons.Default.SkipNext, contentDescription = "Jump to end of track", modifier = Modifier.size(28.dp)) },
                contentDescription = "Jump to end of track",
                testTag = "btn_jump_end"
            )
        }

        // Play 1 (Selection) vs Play 2 (Continuous) vs Play 3 (Loop) & Loop Toggle Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onPlaySelection != null) {
                OutlinedButton(
                    onClick = onPlaySelection,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_play_1_selection")
                        .semantics {
                            contentDescription = "Play 1: Play Selection only. Auditions trimmed audio segment between start and end markers."
                        },
                    border = BorderStroke(
                        if (isHighContrast) 2.dp else 1.dp,
                        if (isHighContrast) Color(0xFFFFD600) else MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Play 1", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            if (onPlayContinuous != null) {
                OutlinedButton(
                    onClick = onPlayContinuous,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_play_2_continuous")
                        .semantics {
                            contentDescription = "Play 2: Play Continuous. Plays audio continuously from current playhead through to the end."
                        },
                    border = BorderStroke(
                        if (isHighContrast) 2.dp else 1.dp,
                        MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Play 2", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            if (onPlayLoop != null) {
                OutlinedButton(
                    onClick = onPlayLoop,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_play_3_loop")
                        .semantics {
                            contentDescription = "Play 3: Loop Selection Playback continuously."
                        },
                    border = BorderStroke(
                        if (isHighContrast) 2.dp else 1.dp,
                        MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Play 3", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            if (onToggleLoop != null) {
                OutlinedButton(
                    onClick = onToggleLoop,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_toggle_loop")
                        .semantics {
                            contentDescription = if (isLooping) "Repeat Loop is On. Tap to turn Off." else "Repeat Loop is Off. Tap to turn On."
                        },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isLooping) (if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primaryContainer) else Color.Transparent
                    ),
                    border = BorderStroke(
                        if (isLooping) 2.dp else 1.dp,
                        if (isLooping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                ) {
                    Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Loop", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // Seek Step Interval Chips
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Jump: ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterVertically).padding(end = 2.dp)
            )
            listOf(1, 5, 10, 30).forEach { sec ->
                val selected = selectedJumpIntervalSec == sec
                FilterChip(
                    selected = selected,
                    onClick = { selectedJumpIntervalSec = sec },
                    label = { Text("${sec}s", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .heightIn(min = 48.dp)
                        .testTag("chip_seek_interval_${sec}s")
                        .semantics {
                            stateDescription = if (selected) "Selected" else "Not selected"
                            contentDescription = "Jump step $sec seconds"
                        },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        // Rewind & Fast-Forward Scrub Speed Selector
        if (onChangeScrubSpeed != null) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("row_scrub_speed_selector"),
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "REW/FF Speed: ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterVertically).padding(end = 2.dp)
                )
                StudioPreferences.SUPPORTED_SCRUB_SPEED_OPTIONS.forEach { spd ->
                    val selected = kotlin.math.abs(scrubSpeed - spd) < 0.04f
                    val label = StudioPreferences.formatScrubSpeedLabel(spd)
                    FilterChip(
                        selected = selected,
                        onClick = { onChangeScrubSpeed(spd) },
                        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .heightIn(min = 48.dp)
                            .testTag("chip_scrub_speed_$label")
                            .semantics {
                                stateDescription = if (selected) "Selected" else "Not selected"
                                contentDescription = "Rewind and fast forward speed $label"
                            },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun NudgeButton(
    label: String,
    spokenDesc: String,
    onClick: () -> Unit,
    testTag: String,
    isActiveScrub: Boolean = false,
    onToggleScrub: (() -> Unit)? = null,
    jumpActionLabel: String? = null
) {
    val isHighContrast = LocalHighContrast.current
    val primaryPressAction: () -> Unit = onToggleScrub ?: onClick

    val containerColor = when {
        isActiveScrub -> if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
        isHighContrast -> Color.Black
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        isActiveScrub -> if (isHighContrast) Color.Black else MaterialTheme.colorScheme.onPrimary
        isHighContrast -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val borderStroke = BorderStroke(
        if (isActiveScrub || isHighContrast) 2.dp else 1.dp,
        when {
            isActiveScrub -> MaterialTheme.colorScheme.primary
            isHighContrast -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.outline
        }
    )

    Surface(
        modifier = Modifier
            .height(48.dp)
            .widthIn(min = 54.dp)
            .clip(RoundedCornerShape(10.dp))
            .testTag(testTag)
            .semantics {
                role = Role.Button
                contentDescription = spokenDesc
                if (onToggleScrub != null && jumpActionLabel != null) {
                    customActions = listOf(
                        CustomAccessibilityAction(jumpActionLabel) {
                            onClick()
                            true
                        }
                    )
                }
            }
            .clickable(onClick = primaryPressAction),
        shape = RoundedCornerShape(10.dp),
        border = borderStroke,
        color = containerColor,
        contentColor = contentColor
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun AccessibleIconButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    contentDescription: String,
    testTag: String
) {
    val isHighContrast = LocalHighContrast.current
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .testTag(testTag)
            .border(
                if (isHighContrast) 2.dp else 0.dp,
                if (isHighContrast) Color.White else Color.Transparent,
                CircleShape
            )
            .semantics { this.contentDescription = contentDescription }
    ) {
        icon()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccessibleTrimSection(
    currentMs: Long,
    durationMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    onSetTrimStart: (Long) -> Unit,
    onSetTrimEnd: (Long) -> Unit,
    onFineTuneStart: (Long) -> Unit,
    onFineTuneEnd: (Long) -> Unit,
    onPreviewTrim: () -> Unit,
    modifier: Modifier = Modifier,
    currentPositionMsProvider: (() -> Long)? = null
) {
    val isHighContrast = LocalHighContrast.current
    val trimmedDuration = (trimEndMs - trimStartMs).coerceAtLeast(0L)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("accessible_trim_card"),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            if (isHighContrast) 2.dp else 1.dp,
            if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ACCESSIBLE TRIM CONTROLS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Set cut points using current playhead position",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Export: ${AccessibilityHelper.formatTimeVisual(trimmedDuration)}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Start Trim Marker Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onSetTrimStart(currentPositionMsProvider?.invoke() ?: currentMs) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_set_trim_start")
                        .semantics {
                            contentDescription = "Set Trim Start Marker to playhead. Currently ${AccessibilityHelper.formatTimeSpoken(trimStartMs)}"
                        },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isHighContrast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    border = if (isHighContrast) BorderStroke(2.dp, Color.White) else null
                ) {
                    Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Set Start: ${AccessibilityHelper.formatTimeVisual(trimStartMs)}", fontWeight = FontWeight.Bold)
                }

                // Fine adjust buttons for start marker
                NudgeButton(label = "-1s", spokenDesc = "Move trim start earlier by 1 second", onClick = { onFineTuneStart(-1000L) }, testTag = "btn_start_minus_1s")
                NudgeButton(label = "+1s", spokenDesc = "Move trim start later by 1 second", onClick = { onFineTuneStart(1000L) }, testTag = "btn_start_plus_1s")
            }

            // End Trim Marker Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onSetTrimEnd(currentPositionMsProvider?.invoke() ?: currentMs) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_set_trim_end")
                        .semantics {
                            contentDescription = "Set Trim End Marker to playhead. Currently ${AccessibilityHelper.formatTimeSpoken(trimEndMs)}"
                        },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    border = if (isHighContrast) BorderStroke(2.dp, Color.White) else null
                ) {
                    Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Set End: ${AccessibilityHelper.formatTimeVisual(trimEndMs)}", fontWeight = FontWeight.Bold)
                }

                // Fine adjust buttons for end marker
                NudgeButton(label = "-1s", spokenDesc = "Move trim end earlier by 1 second", onClick = { onFineTuneEnd(-1000L) }, testTag = "btn_end_minus_1s")
                NudgeButton(label = "+1s", spokenDesc = "Move trim end later by 1 second", onClick = { onFineTuneEnd(1000L) }, testTag = "btn_end_plus_1s")
            }

            // Preview Trimmed Section Button
            OutlinedButton(
                onClick = onPreviewTrim,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_preview_trim")
                    .semantics {
                        contentDescription = "Play Selection. Preview only the trimmed range from ${AccessibilityHelper.formatTimeSpoken(trimStartMs)} to ${AccessibilityHelper.formatTimeSpoken(trimEndMs)}"
                    },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(if (isHighContrast) 2.dp else 1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Play Selection (${AccessibilityHelper.formatTimeVisual(trimmedDuration)})", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AccessibleEffectSlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    displayValue: String,
    spokenDescription: String,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val isHighContrast = LocalHighContrast.current

    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title: $spokenDescription"
            },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurface
            )
            Surface(
                color = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = displayValue,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Decrement button
            IconButton(
                onClick = {
                    val newVal = (value - step).coerceIn(valueRange.start, valueRange.endInclusive)
                    onValueChange(newVal)
                    AccessibilityHelper.vibrate(context, VibrationType.TICK)
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("${testTag}_decrement")
                    .border(
                        if (isHighContrast) 2.dp else 1.dp,
                        if (isHighContrast) Color.White else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape
                    )
                    .semantics { contentDescription = "Decrease $title by $step" }
            ) {
                Icon(
                    androidx.compose.material.icons.Icons.Default.Remove,
                    contentDescription = null,
                    tint = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                )
            }

            // Slider
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                modifier = Modifier.weight(1f)
            )

            // Increment button
            IconButton(
                onClick = {
                    val newVal = (value + step).coerceIn(valueRange.start, valueRange.endInclusive)
                    onValueChange(newVal)
                    AccessibilityHelper.vibrate(context, VibrationType.TICK)
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("${testTag}_increment")
                    .border(
                        if (isHighContrast) 2.dp else 1.dp,
                        if (isHighContrast) Color.White else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape
                    )
                    .semantics { contentDescription = "Increase $title by $step" }
            ) {
                Icon(
                    androidx.compose.material.icons.Icons.Default.Add,
                    contentDescription = null,
                    tint = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Responsive 0% / Preview Playback Slider with real-time position readout, vertical swipe up/down
 * scrubbing with brief preview pulse, and double-tap Play/Pause toggle.
 */
@Composable
fun AccessiblePreviewPlaybackSlider(
    currentPositionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Preview Playback",
    testTag: String = "slider_preview_playback",
    onTogglePlayPause: (() -> Unit)? = null,
    onScrubPreviewPulse: ((Long) -> Unit)? = null,
    isPlaying: Boolean = false
) {
    val isHighContrast = LocalHighContrast.current
    val safeDuration = durationMs.coerceAtLeast(1000L)
    var isDragging by remember { mutableStateOf(false) }
    var dragPosMs by remember { mutableLongStateOf(currentPositionMs.coerceIn(0L, safeDuration)) }
    val displayPosMs = if (isDragging) dragPosMs else currentPositionMs.coerceIn(0L, safeDuration)
    val percent = ((displayPosMs.toFloat() / safeDuration.toFloat()) * 100f).toInt().coerceIn(0, 100)

    val latestPos by rememberUpdatedState(displayPosMs)
    val latestOnSeekTo by rememberUpdatedState(onSeekTo)
    val latestOnTogglePlayPause by rememberUpdatedState(onTogglePlayPause)
    val latestOnScrubPreviewPulse by rememberUpdatedState(onScrubPreviewPulse)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("${testTag}_container")
            .pointerInput(safeDuration) {
                var accumulatedDragY = 0f
                val thresholdPx = 22f
                val stepMs = maxOf(250L, (safeDuration * 0.05f).roundToLong())
                detectVerticalDragGestures(
                    onDragStart = {
                        accumulatedDragY = 0f
                    },
                    onDragEnd = {
                        accumulatedDragY = 0f
                        isDragging = false
                    },
                    onDragCancel = {
                        accumulatedDragY = 0f
                        isDragging = false
                    }
                ) { _, dragAmount ->
                    accumulatedDragY += dragAmount
                    if (accumulatedDragY <= -thresholdPx) {
                        // Swipe UP -> move forward toward end + brief preview
                        val next = (latestPos + stepMs).coerceIn(0L, safeDuration)
                        isDragging = true
                        dragPosMs = next
                        latestOnSeekTo(next)
                        latestOnScrubPreviewPulse?.invoke(next)
                        accumulatedDragY = 0f
                    } else if (accumulatedDragY >= thresholdPx) {
                        // Swipe DOWN -> move backward toward start + brief preview
                        val next = (latestPos - stepMs).coerceIn(0L, safeDuration)
                        isDragging = true
                        dragPosMs = next
                        latestOnSeekTo(next)
                        latestOnScrubPreviewPulse?.invoke(next)
                        accumulatedDragY = 0f
                    }
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isHighContrast) Color(0xFF101010) else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            if (isHighContrast) 2.dp else 1.dp,
            if (isHighContrast) Color.White else MaterialTheme.colorScheme.outlineVariant
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
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { latestOnTogglePlayPause?.invoke() }
                        )
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$percent% • ${AccessibilityHelper.formatTimeVisual(displayPosMs)} / ${AccessibilityHelper.formatTimeVisual(durationMs.coerceAtLeast(0L))}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("${testTag}_readout")
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
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
                                                latestOnTogglePlayPause?.invoke()
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
                val scrubNudgeMs = maxOf(500L, (safeDuration * 0.05f).roundToLong())
                Slider(
                    value = displayPosMs.toFloat(),
                    onValueChange = { raw ->
                        val target = raw.toLong().coerceIn(0L, safeDuration)
                        isDragging = true
                        dragPosMs = target
                        onSeekTo(target)
                        onScrubPreviewPulse?.invoke(target)
                    },
                    onValueChangeFinished = {
                        val committed = dragPosMs.coerceIn(0L, safeDuration)
                        isDragging = false
                        onSeekTo(committed)
                    },
                    valueRange = 0f..safeDuration.toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(testTag)
                        .semantics {
                            val stateText = if (isPlaying) "Playing" else "Paused"
                            contentDescription = "$label slider, $percent percent, $stateText. Position ${AccessibilityHelper.formatTimeSpoken(displayPosMs)} of ${AccessibilityHelper.formatTimeSpoken(durationMs.coerceAtLeast(0L))}. Swipe up to move toward end with preview, swipe down to move toward start, double tap to play or pause."
                            customActions = buildList {
                                if (onTogglePlayPause != null) {
                                    add(
                                        CustomAccessibilityAction(if (isPlaying) "Pause preview" else "Play preview") {
                                            onTogglePlayPause.invoke()
                                            true
                                        }
                                    )
                                }
                                add(
                                    CustomAccessibilityAction("Move forward toward end with preview") {
                                        val target = (displayPosMs + scrubNudgeMs).coerceIn(0L, safeDuration)
                                        onSeekTo(target)
                                        onScrubPreviewPulse?.invoke(target)
                                        true
                                    }
                                )
                                add(
                                    CustomAccessibilityAction("Move backward toward start with preview") {
                                        val target = (displayPosMs - scrubNudgeMs).coerceIn(0L, safeDuration)
                                        onSeekTo(target)
                                        onScrubPreviewPulse?.invoke(target)
                                        true
                                    }
                                )
                            }
                        }
                )
            }
        }
    }
}


