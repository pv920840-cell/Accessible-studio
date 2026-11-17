package com.example.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.accessibility.AccessibilityHelper
import com.example.audio.CuePoint
import com.example.audio.StudioFxSettings
import com.example.ui.theme.LocalHighContrast

/**
 * Accessible Studio Edit & Selection Suite:
 * Cut, Copy, Paste, Paste at End, Mute Selection, Delete Selection, Crop Selection, and Maximize Volume.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioEditSection(
    hasSelection: Boolean,
    clipboardSeconds: Float?,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onUndo: (() -> Unit)? = null,
    onRedo: (() -> Unit)? = null,
    onSelectAll: (() -> Unit)? = null,
    onNormalize: () -> Unit,
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onPasteAtCursor: () -> Unit,
    onPasteAtEnd: () -> Unit,
    onMixPaste: () -> Unit,
    onFadeIn: () -> Unit,
    onFadeOut: () -> Unit,
    onOpenGainDialog: () -> Unit,
    onOpenStatsDialog: () -> Unit,
    onRemoveDcOffset: () -> Unit,
    onHighPassFilter: (() -> Unit)? = null,
    onLowPassFilter: (() -> Unit)? = null,
    onReverseTrack: (() -> Unit)? = null,
    onNormalizeRms: (() -> Unit)? = null,
    onBandPassFilter: (() -> Unit)? = null,
    onHumRemoval: (() -> Unit)? = null,
    onMechanize: (() -> Unit)? = null,
    onExpander: (() -> Unit)? = null,
    onMuteSelection: () -> Unit,
    onDeleteSelection: () -> Unit,
    onCropSelection: () -> Unit,
    onOpenSilenceDialog: () -> Unit,
    onOpenToneGeneratorDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isHighContrast = LocalHighContrast.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("studio_edit_section_card"),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            if (isHighContrast) 2.dp else 1.dp,
            if (isHighContrast) Color(0xFFFFD600) else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = if (isHighContrast) Color(0xFFFFD600) else MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "ACCESSIBLE STUDIO EDIT & TOOLS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Cut, Copy, Paste, Mute, Crop, Undo, Redo & Filters",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Clipboard Status Pill
                val clipText = if (clipboardSeconds != null && clipboardSeconds > 0) {
                    "Clipboard: %.2fs".format(clipboardSeconds)
                } else {
                    "Clip: Empty"
                }
                Box(
                    modifier = Modifier
                        .background(
                            if (clipboardSeconds != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .semantics { contentDescription = "Studio clipboard status: $clipText" }
                ) {
                    Text(
                        clipText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (clipboardSeconds != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // UNDO, REDO & SELECT ALL ROW (Primary Editing Controls)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Undo
                Button(
                    onClick = { onUndo?.invoke() },
                    enabled = canUndo,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("btn_studio_undo")
                        .semantics { contentDescription = if (canUndo) "Undo last edit action" else "Undo unavailable" }
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Undo", fontWeight = FontWeight.Bold)
                }

                // Redo
                Button(
                    onClick = { onRedo?.invoke() },
                    enabled = canRedo,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("btn_studio_redo")
                        .semantics { contentDescription = if (canRedo) "Redo last undone action" else "Redo unavailable" }
                ) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Redo", fontWeight = FontWeight.Bold)
                }

                // Select All
                OutlinedButton(
                    onClick = { onSelectAll?.invoke() },
                    modifier = Modifier
                        .weight(1.2f)
                        .height(44.dp)
                        .testTag("btn_studio_select_all")
                        .semantics { contentDescription = "Select entire audio track between 0 and end" }
                ) {
                    Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Select All", fontWeight = FontWeight.Bold)
                }
            }

            // MAXIMIZE / NORMALIZE (-0.1 dB)
            Button(
                onClick = onNormalize,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isHighContrast) Color(0xFFFFD600) else MaterialTheme.colorScheme.primary,
                    contentColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_studio_maximize")
                    .semantics { contentDescription = "Studio Maximize Volume. Normalizes peak audio amplitude to minus zero point one decibels" }
            ) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Maximize Volume (Normalize -0.1 dB)", fontWeight = FontWeight.Bold)
            }

            // CLIPBOARD & SELECTION EDIT ACTIONS
            Text(
                "Selection Operations (Between Trim Markers):",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Cut
                OutlinedButton(
                    onClick = onCut,
                    enabled = hasSelection,
                    modifier = Modifier
                        .testTag("btn_studio_cut")
                        .semantics { contentDescription = "Cut selected audio to clipboard and delete from track" }
                ) {
                    Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Cut")
                }

                // Copy
                OutlinedButton(
                    onClick = onCopy,
                    enabled = hasSelection,
                    modifier = Modifier
                        .testTag("btn_studio_copy")
                        .semantics { contentDescription = "Copy selected audio range to clipboard" }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy")
                }

                // Paste at Cursor
                OutlinedButton(
                    onClick = onPasteAtCursor,
                    enabled = clipboardSeconds != null,
                    modifier = Modifier
                        .testTag("btn_studio_paste_cursor")
                        .semantics { contentDescription = "Paste clipboard audio at current playhead position" }
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Paste at Cursor")
                }

                // Paste at End
                OutlinedButton(
                    onClick = onPasteAtEnd,
                    enabled = clipboardSeconds != null,
                    modifier = Modifier
                        .testTag("btn_studio_paste_end")
                        .semantics { contentDescription = "Paste clipboard audio at the end of track" }
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Paste at End")
                }

                // Mix Paste (Overdub)
                OutlinedButton(
                    onClick = onMixPaste,
                    enabled = clipboardSeconds != null,
                    modifier = Modifier
                        .testTag("btn_studio_mix_paste")
                        .semantics { contentDescription = "Mix Paste. Blends clipboard audio over existing audio at cursor" }
                ) {
                    Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Mix Paste")
                }

                // Fade In
                OutlinedButton(
                    onClick = onFadeIn,
                    enabled = hasSelection,
                    modifier = Modifier
                        .testTag("btn_studio_fade_in")
                        .semantics { contentDescription = "Fade In selection. Smooth linear ramp from silence to full volume" }
                ) {
                    Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Fade In")
                }

                // Fade Out
                OutlinedButton(
                    onClick = onFadeOut,
                    enabled = hasSelection,
                    modifier = Modifier
                        .testTag("btn_studio_fade_out")
                        .semantics { contentDescription = "Fade Out selection. Smooth linear ramp down to silence" }
                ) {
                    Icon(Icons.Default.TrendingDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Fade Out")
                }

                // Change Gain / Volume dB
                OutlinedButton(
                    onClick = onOpenGainDialog,
                    modifier = Modifier
                        .testTag("btn_studio_change_gain")
                        .semantics { contentDescription = "Adjust Gain in decibels across selection or entire track" }
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Gain (+/- dB)")
                }

                // Mute Selection
                OutlinedButton(
                    onClick = onMuteSelection,
                    enabled = hasSelection,
                    modifier = Modifier
                        .testTag("btn_studio_mute_selection")
                        .semantics { contentDescription = "Mute selection. Silences the selected section while keeping timing" }
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeMute, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Mute Selection")
                }

                // Delete Selection
                OutlinedButton(
                    onClick = onDeleteSelection,
                    enabled = hasSelection,
                    modifier = Modifier
                        .testTag("btn_studio_delete_selection")
                        .semantics { contentDescription = "Delete selected audio segment and close gap" }
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete")
                }

                // Crop Selection
                OutlinedButton(
                    onClick = onCropSelection,
                    enabled = hasSelection,
                    modifier = Modifier
                        .testTag("btn_studio_crop_selection")
                        .semantics { contentDescription = "Crop to selection. Discards everything outside the trim markers" }
                ) {
                    Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Crop Selection")
                }

                // Audio Statistics Inspection
                OutlinedButton(
                    onClick = onOpenStatsDialog,
                    modifier = Modifier
                        .testTag("btn_studio_audio_stats")
                        .semantics { contentDescription = "Inspect Audio Statistics: Peak dBFS, RMS Level, Duration, Clipping" }
                ) {
                    Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Stats / Level")
                }

                // Remove DC Offset
                OutlinedButton(
                    onClick = onRemoveDcOffset,
                    modifier = Modifier
                        .testTag("btn_studio_remove_dc_offset")
                        .semantics { contentDescription = "Remove DC Offset. Re-centers electrical waveform to zero" }
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Remove DC")
                }

                // High-Pass Filter (300 Hz)
                if (onHighPassFilter != null) {
                    OutlinedButton(
                        onClick = onHighPassFilter,
                        modifier = Modifier
                            .testTag("btn_studio_high_pass")
                            .semantics { contentDescription = "Apply High-Pass filter to cut low rumble below 300 Hertz" }
                    ) {
                        Icon(Icons.Default.Waves, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("High-Pass")
                    }
                }

                // Low-Pass Filter (3000 Hz)
                if (onLowPassFilter != null) {
                    OutlinedButton(
                        onClick = onLowPassFilter,
                        modifier = Modifier
                            .testTag("btn_studio_low_pass")
                            .semantics { contentDescription = "Apply Low-Pass filter to soften harsh treble above 3000 Hertz" }
                    ) {
                        Icon(Icons.Default.Waves, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Low-Pass")
                    }
                }

                // Reverse Audio
                if (onReverseTrack != null) {
                    OutlinedButton(
                        onClick = onReverseTrack,
                        modifier = Modifier
                            .testTag("btn_studio_reverse_track")
                            .semantics { contentDescription = "Reverse audio track backward" }
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reverse")
                    }
                }

                // RMS Normalization
                if (onNormalizeRms != null) {
                    OutlinedButton(
                        onClick = onNormalizeRms,
                        modifier = Modifier
                            .testTag("btn_studio_rms_norm")
                            .semantics { contentDescription = "RMS Normalization: scale loudness to -16 dBFS" }
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("RMS Norm")
                    }
                }

                // Band-Pass Filter (Voice Band: 300 - 3400 Hz)
                if (onBandPassFilter != null) {
                    OutlinedButton(
                        onClick = onBandPassFilter,
                        modifier = Modifier
                            .testTag("btn_studio_band_pass")
                            .semantics { contentDescription = "Apply Band-Pass voice clarity filter (300 to 3400 Hertz)" }
                    ) {
                        Icon(Icons.Default.Waves, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Band-Pass")
                    }
                }

                // Mains Hum Removal (60 Hz Notch)
                if (onHumRemoval != null) {
                    OutlinedButton(
                        onClick = onHumRemoval,
                        modifier = Modifier
                            .testTag("btn_studio_de_hum")
                            .semantics { contentDescription = "Remove 60 Hertz electrical ground loop mains hum and harmonics" }
                    ) {
                        Icon(Icons.Default.Waves, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("De-Hum")
                    }
                }

                // Mechanize / Ring Modulation
                if (onMechanize != null) {
                    OutlinedButton(
                        onClick = onMechanize,
                        modifier = Modifier
                            .testTag("btn_studio_mechanize")
                            .semantics { contentDescription = "Apply Mechanize ring modulation for metallic robot timbre" }
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Mechanize")
                    }
                }

                // Dynamic Expander
                if (onExpander != null) {
                    OutlinedButton(
                        onClick = onExpander,
                        modifier = Modifier
                            .testTag("btn_studio_expander")
                            .semantics { contentDescription = "Apply Dynamic Expander for enhanced acoustic dynamic range" }
                    ) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Expander")
                    }
                }
            }

            // SYNTHESIS & GENERATION TOOLS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenSilenceDialog,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("btn_studio_insert_silence")
                        .semantics { contentDescription = "Insert digital silence at current playhead" }
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeMute, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Insert Silence", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onOpenToneGeneratorDialog,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("btn_studio_generate_tone")
                        .semantics { contentDescription = "Synthesize tone or noise with studio generator" }
                ) {
                    Icon(Icons.Default.Waves, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tone / Noise", fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Accessible Studio Cue Points & Markers Manager
 */
@Composable
fun StudioCuePointsSection(
    cuePoints: List<CuePoint>,
    currentMs: Long,
    onDropCue: () -> Unit,
    onJumpPrevCue: () -> Unit,
    onJumpNextCue: () -> Unit,
    onSeekToCue: (CuePoint) -> Unit,
    onDeleteCue: (CuePoint) -> Unit,
    modifier: Modifier = Modifier
) {
    val isHighContrast = LocalHighContrast.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("studio_cue_points_card"),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(if (isHighContrast) 2.dp else 1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Column {
                        Text(
                            text = "ACCESSIBLE STUDIO CUE POINTS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${cuePoints.size} cue markers placed on track",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Drop Cue Button
                Button(
                    onClick = onDropCue,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier
                        .testTag("btn_studio_drop_cue")
                        .semantics { contentDescription = "Drop cue marker at current position: ${AccessibilityHelper.formatTimeSpoken(currentMs)}" }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Drop Cue", fontWeight = FontWeight.Bold)
                }
            }

            // Fast Jump Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onJumpPrevCue,
                    enabled = cuePoints.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("btn_jump_prev_cue")
                        .semantics { contentDescription = "Jump to previous cue marker" }
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Prev Cue")
                }

                OutlinedButton(
                    onClick = onJumpNextCue,
                    enabled = cuePoints.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("btn_jump_next_cue")
                        .semantics { contentDescription = "Jump to next cue marker" }
                ) {
                    Text("Next Cue")
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }

            // Cue list
            if (cuePoints.isEmpty()) {
                Text(
                    "No cue markers yet. Press 'Drop Cue' while listening to mark important beats, verses, or edit points.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    cuePoints.forEachIndexed { idx, cue ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isHighContrast) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("cue_item_${cue.id}")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "${idx + 1}",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column {
                                    Text(cue.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(
                                        AccessibilityHelper.formatTimeMMSS(cue.positionMs),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(
                                    onClick = { onSeekToCue(cue) },
                                    modifier = Modifier
                                        .height(36.dp)
                                        .semantics { contentDescription = "Jump playhead to ${cue.name} at ${AccessibilityHelper.formatTimeSpoken(cue.positionMs)}" }
                                ) {
                                    Text("Seek", fontSize = 12.sp)
                                }

                                IconButton(
                                    onClick = { onDeleteCue(cue) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .semantics { contentDescription = "Delete ${cue.name}" }
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Accessible Studio Pro FX Rack (Flanger, Chorus, Distortion, Compressor/Limiter, 7-Band Graphic EQ, Pitch Shift)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioFxRackSection(
    settings: StudioFxSettings,
    onSettingsChanged: (StudioFxSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    val isHighContrast = LocalHighContrast.current
    var pitchExpanded by remember { mutableStateOf(true) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // DYNAMICS COMPRESSOR
        Card(
            colors = CardDefaults.cardColors(containerColor = if (isHighContrast) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSettingsChanged(settings.copy(compressorEnabled = !settings.compressorEnabled)) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Dynamics Compressor", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Studio Master peak leveling & punch", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = settings.compressorEnabled,
                        onCheckedChange = { onSettingsChanged(settings.copy(compressorEnabled = it)) },
                        modifier = Modifier.testTag("switch_compressor_enabled")
                    )
                }

                AnimatedVisibility(visible = settings.compressorEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        EffectPresetDropdown(
                            effectId = "compressor",
                            effectTitle = "Compressor",
                            currentValues = CompressorParams(
                                settings.compressorThresholdDb,
                                settings.compressorRatio,
                                settings.compressorMakeupGainDb
                            ),
                            presets = EffectPanelPresetsCatalog.compressorPresets,
                            defaultOption = EffectPanelPresetsCatalog.compressorDefault,
                            valuesEqual = { a, b ->
                                EffectPresetResolver.approxEqual(a.thresholdDb, b.thresholdDb, 0.2f) &&
                                    EffectPresetResolver.approxEqual(a.ratio, b.ratio, 0.1f) &&
                                    EffectPresetResolver.approxEqual(a.makeupGainDb, b.makeupGainDb, 0.1f)
                            },
                            onApplyPreset = { preset ->
                                onSettingsChanged(
                                    settings.copy(
                                        compressorThresholdDb = preset.thresholdDb,
                                        compressorRatio = preset.ratio,
                                        compressorMakeupGainDb = preset.makeupGainDb
                                    )
                                )
                            }
                        )
                        AccessibleEffectSlider(
                            title = "Threshold",
                            value = settings.compressorThresholdDb,
                            onValueChange = { onSettingsChanged(settings.copy(compressorThresholdDb = it)) },
                            valueRange = -30f..0f,
                            step = 1f,
                            displayValue = "%.0f dB".format(settings.compressorThresholdDb),
                            spokenDescription = "%.0f decibels compression threshold".format(settings.compressorThresholdDb),
                            testTag = "slider_compressor_thresh"
                        )
                        AccessibleEffectSlider(
                            title = "Compression Ratio",
                            value = settings.compressorRatio,
                            onValueChange = { onSettingsChanged(settings.copy(compressorRatio = it)) },
                            valueRange = 1.5f..10.0f,
                            step = 0.5f,
                            displayValue = "%.1f : 1".format(settings.compressorRatio),
                            spokenDescription = "%.1f to 1 compression ratio".format(settings.compressorRatio),
                            testTag = "slider_compressor_ratio"
                        )
                        AccessibleEffectSlider(
                            title = "Makeup Gain",
                            value = settings.compressorMakeupGainDb,
                            onValueChange = { onSettingsChanged(settings.copy(compressorMakeupGainDb = it)) },
                            valueRange = 0f..12.0f,
                            step = 0.5f,
                            displayValue = "+%.1f dB".format(settings.compressorMakeupGainDb),
                            spokenDescription = "Plus %.1f decibels makeup gain".format(settings.compressorMakeupGainDb),
                            testTag = "slider_compressor_makeup"
                        )
                    }
                }
            }
        }

        // PEAK LIMITER (Dedicated Panel with Ceiling Slider & Presets)
        Card(
            colors = CardDefaults.cardColors(containerColor = if (isHighContrast) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSettingsChanged(settings.copy(peakLimiterEnabled = !settings.peakLimiterEnabled)) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Peak Limiter (Anti-Clip)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Prevents digital clipping with brickwall ceiling", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = settings.peakLimiterEnabled,
                        onCheckedChange = { onSettingsChanged(settings.copy(peakLimiterEnabled = it)) },
                        modifier = Modifier.testTag("switch_limiter_enabled")
                    )
                }

                AnimatedVisibility(visible = settings.peakLimiterEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        EffectPresetDropdown(
                            effectId = "peak_limiter",
                            effectTitle = "Peak Limiter",
                            currentValues = settings.peakLimiterCeilingDb,
                            presets = EffectPanelPresetsCatalog.peakLimiterPresets,
                            defaultOption = EffectPanelPresetsCatalog.peakLimiterDefault,
                            valuesEqual = { a, b -> EffectPresetResolver.approxEqual(a, b, 0.05f) },
                            onApplyPreset = { preset ->
                                onSettingsChanged(settings.copy(peakLimiterCeilingDb = preset))
                            }
                        )
                        AccessibleEffectSlider(
                            title = "Limiting Ceiling (dBFS)",
                            value = settings.peakLimiterCeilingDb,
                            onValueChange = { onSettingsChanged(settings.copy(peakLimiterCeilingDb = it)) },
                            valueRange = -12.0f..-0.1f,
                            step = 0.1f,
                            displayValue = "%.1f dBFS".format(settings.peakLimiterCeilingDb),
                            spokenDescription = "%.1f decibels full scale peak limiter ceiling".format(settings.peakLimiterCeilingDb),
                            testTag = "slider_limiter_ceiling"
                        )
                    }
                }
            }
        }

        // FLANGER
        Card(
            colors = CardDefaults.cardColors(containerColor = if (isHighContrast) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSettingsChanged(settings.copy(flangerEnabled = !settings.flangerEnabled)) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Flanger", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Sweeping jet comb filter effect", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = settings.flangerEnabled,
                        onCheckedChange = { onSettingsChanged(settings.copy(flangerEnabled = it)) },
                        modifier = Modifier.testTag("switch_flanger_enabled")
                    )
                }

                AnimatedVisibility(visible = settings.flangerEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        EffectPresetDropdown(
                            effectId = "flanger",
                            effectTitle = "Flanger",
                            currentValues = FlangerParams(
                                settings.flangerRateHz,
                                settings.flangerDepthMs,
                                settings.flangerFeedback,
                                settings.flangerMix
                            ),
                            presets = EffectPanelPresetsCatalog.flangerPresets,
                            defaultOption = EffectPanelPresetsCatalog.flangerDefault,
                            valuesEqual = { a, b ->
                                EffectPresetResolver.approxEqual(a.rateHz, b.rateHz, 0.05f) &&
                                    EffectPresetResolver.approxEqual(a.depthMs, b.depthMs, 0.15f) &&
                                    EffectPresetResolver.approxEqual(a.feedback, b.feedback) &&
                                    EffectPresetResolver.approxEqual(a.mix, b.mix)
                            },
                            onApplyPreset = { preset ->
                                onSettingsChanged(
                                    settings.copy(
                                        flangerRateHz = preset.rateHz,
                                        flangerDepthMs = preset.depthMs,
                                        flangerFeedback = preset.feedback,
                                        flangerMix = preset.mix
                                    )
                                )
                            }
                        )
                        AccessibleEffectSlider(
                            title = "Sweep Rate",
                            value = settings.flangerRateHz,
                            onValueChange = { onSettingsChanged(settings.copy(flangerRateHz = it)) },
                            valueRange = 0.1f..3.0f,
                            step = 0.1f,
                            displayValue = "%.1f Hz".format(settings.flangerRateHz),
                            spokenDescription = "%.1f Hertz flanger sweep rate".format(settings.flangerRateHz),
                            testTag = "slider_flanger_rate"
                        )
                        AccessibleEffectSlider(
                            title = "Sweep Depth",
                            value = settings.flangerDepthMs,
                            onValueChange = { onSettingsChanged(settings.copy(flangerDepthMs = it)) },
                            valueRange = 1.0f..10.0f,
                            step = 0.5f,
                            displayValue = "%.1f ms".format(settings.flangerDepthMs),
                            spokenDescription = "%.1f milliseconds sweep depth".format(settings.flangerDepthMs),
                            testTag = "slider_flanger_depth"
                        )
                        AccessibleEffectSlider(
                            title = "Feedback Resonator",
                            value = settings.flangerFeedback,
                            onValueChange = { onSettingsChanged(settings.copy(flangerFeedback = it)) },
                            valueRange = 0.1f..0.85f,
                            step = 0.05f,
                            displayValue = "${(settings.flangerFeedback * 100).toInt()}%",
                            spokenDescription = "${(settings.flangerFeedback * 100).toInt()} percent feedback",
                            testTag = "slider_flanger_feedback"
                        )
                        AccessibleEffectSlider(
                            title = "Flanger Mix",
                            value = settings.flangerMix,
                            onValueChange = { onSettingsChanged(settings.copy(flangerMix = it)) },
                            valueRange = 0.1f..0.9f,
                            step = 0.05f,
                            displayValue = "${(settings.flangerMix * 100).toInt()}%",
                            spokenDescription = "${(settings.flangerMix * 100).toInt()} percent mix",
                            testTag = "slider_flanger_mix"
                        )
                    }
                }
            }
        }

        // CHORUS
        Card(
            colors = CardDefaults.cardColors(containerColor = if (isHighContrast) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSettingsChanged(settings.copy(chorusEnabled = !settings.chorusEnabled)) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Chorus (Multi-Voice)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Rich stereo vocal & acoustic doubling", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = settings.chorusEnabled,
                        onCheckedChange = { onSettingsChanged(settings.copy(chorusEnabled = it)) },
                        modifier = Modifier.testTag("switch_chorus_enabled")
                    )
                }

                AnimatedVisibility(visible = settings.chorusEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        EffectPresetDropdown(
                            effectId = "chorus",
                            effectTitle = "Chorus",
                            currentValues = ChorusParams(
                                settings.chorusRateHz,
                                settings.chorusDepthMs,
                                settings.chorusMix
                            ),
                            presets = EffectPanelPresetsCatalog.chorusPresets,
                            defaultOption = EffectPanelPresetsCatalog.chorusDefault,
                            valuesEqual = { a, b ->
                                EffectPresetResolver.approxEqual(a.rateHz, b.rateHz, 0.05f) &&
                                    EffectPresetResolver.approxEqual(a.depthMs, b.depthMs, 0.25f) &&
                                    EffectPresetResolver.approxEqual(a.mix, b.mix)
                            },
                            onApplyPreset = { preset ->
                                onSettingsChanged(
                                    settings.copy(
                                        chorusRateHz = preset.rateHz,
                                        chorusDepthMs = preset.depthMs,
                                        chorusMix = preset.mix
                                    )
                                )
                            }
                        )
                        AccessibleEffectSlider(
                            title = "Chorus Speed",
                            value = settings.chorusRateHz,
                            onValueChange = { onSettingsChanged(settings.copy(chorusRateHz = it)) },
                            valueRange = 0.2f..4.0f,
                            step = 0.1f,
                            displayValue = "%.1f Hz".format(settings.chorusRateHz),
                            spokenDescription = "%.1f Hertz chorus rate".format(settings.chorusRateHz),
                            testTag = "slider_chorus_rate"
                        )
                        AccessibleEffectSlider(
                            title = "Chorus Depth",
                            value = settings.chorusDepthMs,
                            onValueChange = { onSettingsChanged(settings.copy(chorusDepthMs = it)) },
                            valueRange = 2.0f..14.0f,
                            step = 0.5f,
                            displayValue = "%.1f ms".format(settings.chorusDepthMs),
                            spokenDescription = "%.1f milliseconds chorus depth".format(settings.chorusDepthMs),
                            testTag = "slider_chorus_depth"
                        )
                        AccessibleEffectSlider(
                            title = "Chorus Mix",
                            value = settings.chorusMix,
                            onValueChange = { onSettingsChanged(settings.copy(chorusMix = it)) },
                            valueRange = 0.1f..0.9f,
                            step = 0.05f,
                            displayValue = "${(settings.chorusMix * 100).toInt()}%",
                            spokenDescription = "${(settings.chorusMix * 100).toInt()} percent mix",
                            testTag = "slider_chorus_mix"
                        )
                    }
                }
            }
        }

        // DISTORTION / OVERDRIVE
        Card(
            colors = CardDefaults.cardColors(containerColor = if (isHighContrast) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSettingsChanged(settings.copy(distortionEnabled = !settings.distortionEnabled)) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Overdrive & Distortion", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Warm analog saturation and crunch", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = settings.distortionEnabled,
                        onCheckedChange = { onSettingsChanged(settings.copy(distortionEnabled = it)) },
                        modifier = Modifier.testTag("switch_distortion_enabled")
                    )
                }

                AnimatedVisibility(visible = settings.distortionEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        EffectPresetDropdown(
                            effectId = "distortion",
                            effectTitle = "Overdrive & Distortion",
                            currentValues = DistortionParams(settings.distortionDrive, settings.distortionTone),
                            presets = EffectPanelPresetsCatalog.distortionPresets,
                            defaultOption = EffectPanelPresetsCatalog.distortionDefault,
                            valuesEqual = { a, b ->
                                EffectPresetResolver.approxEqual(a.drive, b.drive, 0.2f) &&
                                    EffectPresetResolver.approxEqual(a.tone, b.tone)
                            },
                            onApplyPreset = { preset ->
                                onSettingsChanged(
                                    settings.copy(
                                        distortionDrive = preset.drive,
                                        distortionTone = preset.tone
                                    )
                                )
                            }
                        )
                        AccessibleEffectSlider(
                            title = "Drive Intensity",
                            value = settings.distortionDrive,
                            onValueChange = { onSettingsChanged(settings.copy(distortionDrive = it)) },
                            valueRange = 1.0f..8.0f,
                            step = 0.5f,
                            displayValue = "%.1fx".format(settings.distortionDrive),
                            spokenDescription = "%.1f times drive".format(settings.distortionDrive),
                            testTag = "slider_distortion_drive"
                        )
                        AccessibleEffectSlider(
                            title = "Tone Brightness",
                            value = settings.distortionTone,
                            onValueChange = { onSettingsChanged(settings.copy(distortionTone = it)) },
                            valueRange = 0.2f..0.9f,
                            step = 0.05f,
                            displayValue = "${(settings.distortionTone * 100).toInt()}%",
                            spokenDescription = "${(settings.distortionTone * 100).toInt()} percent tone",
                            testTag = "slider_distortion_tone"
                        )
                    }
                }
            }
        }

        // 7-BAND GRAPHIC EQUALIZER
        Card(
            colors = CardDefaults.cardColors(containerColor = if (isHighContrast) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSettingsChanged(settings.copy(eq7BandEnabled = !settings.eq7BandEnabled)) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("7-Band Graphic Equalizer", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Precise frequency carving (-12 to +12 dB)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = settings.eq7BandEnabled,
                        onCheckedChange = { onSettingsChanged(settings.copy(eq7BandEnabled = it)) },
                        modifier = Modifier.testTag("switch_eq7band_enabled")
                    )
                }

                AnimatedVisibility(visible = settings.eq7BandEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        EffectPresetDropdown(
                            effectId = "eq_7band",
                            effectTitle = "7-Band Equalizer",
                            currentValues = Eq7BandParams(
                                settings.eqBand60Hz,
                                settings.eqBand150Hz,
                                settings.eqBand400Hz,
                                settings.eqBand1kHz,
                                settings.eqBand2_5kHz,
                                settings.eqBand6kHz,
                                settings.eqBand15kHz
                            ),
                            presets = EffectPanelPresetsCatalog.eq7BandPresets,
                            defaultOption = EffectPanelPresetsCatalog.eq7BandDefault,
                            valuesEqual = { a, b ->
                                EffectPresetResolver.approxEqual(a.b60, b.b60, 0.2f) &&
                                    EffectPresetResolver.approxEqual(a.b150, b.b150, 0.2f) &&
                                    EffectPresetResolver.approxEqual(a.b400, b.b400, 0.2f) &&
                                    EffectPresetResolver.approxEqual(a.b1k, b.b1k, 0.2f) &&
                                    EffectPresetResolver.approxEqual(a.b2_5k, b.b2_5k, 0.2f) &&
                                    EffectPresetResolver.approxEqual(a.b6k, b.b6k, 0.2f) &&
                                    EffectPresetResolver.approxEqual(a.b15k, b.b15k, 0.2f)
                            },
                            onApplyPreset = { preset ->
                                onSettingsChanged(
                                    settings.copy(
                                        eqBand60Hz = preset.b60,
                                        eqBand150Hz = preset.b150,
                                        eqBand400Hz = preset.b400,
                                        eqBand1kHz = preset.b1k,
                                        eqBand2_5kHz = preset.b2_5k,
                                        eqBand6kHz = preset.b6k,
                                        eqBand15kHz = preset.b15k
                                    )
                                )
                            }
                        )

                        AccessibleEffectSlider(
                            title = "Sub-Bass (60 Hz)",
                            value = settings.eqBand60Hz,
                            onValueChange = { onSettingsChanged(settings.copy(eqBand60Hz = it)) },
                            valueRange = -12f..12f,
                            step = 1f,
                            displayValue = "%+.0f dB".format(settings.eqBand60Hz),
                            spokenDescription = "%+.0f decibels at 60 Hertz".format(settings.eqBand60Hz),
                            testTag = "slider_eq_60"
                        )
                        AccessibleEffectSlider(
                            title = "Bass Punch (150 Hz)",
                            value = settings.eqBand150Hz,
                            onValueChange = { onSettingsChanged(settings.copy(eqBand150Hz = it)) },
                            valueRange = -12f..12f,
                            step = 1f,
                            displayValue = "%+.0f dB".format(settings.eqBand150Hz),
                            spokenDescription = "%+.0f decibels at 150 Hertz".format(settings.eqBand150Hz),
                            testTag = "slider_eq_150"
                        )
                        AccessibleEffectSlider(
                            title = "Low Mid (400 Hz)",
                            value = settings.eqBand400Hz,
                            onValueChange = { onSettingsChanged(settings.copy(eqBand400Hz = it)) },
                            valueRange = -12f..12f,
                            step = 1f,
                            displayValue = "%+.0f dB".format(settings.eqBand400Hz),
                            spokenDescription = "%+.0f decibels at 400 Hertz".format(settings.eqBand400Hz),
                            testTag = "slider_eq_400"
                        )
                        AccessibleEffectSlider(
                            title = "Mid Presence (1 kHz)",
                            value = settings.eqBand1kHz,
                            onValueChange = { onSettingsChanged(settings.copy(eqBand1kHz = it)) },
                            valueRange = -12f..12f,
                            step = 1f,
                            displayValue = "%+.0f dB".format(settings.eqBand1kHz),
                            spokenDescription = "%+.0f decibels at 1 kiloHertz".format(settings.eqBand1kHz),
                            testTag = "slider_eq_1k"
                        )
                        AccessibleEffectSlider(
                            title = "Upper Mid Clarity (2.5 kHz)",
                            value = settings.eqBand2_5kHz,
                            onValueChange = { onSettingsChanged(settings.copy(eqBand2_5kHz = it)) },
                            valueRange = -12f..12f,
                            step = 1f,
                            displayValue = "%+.0f dB".format(settings.eqBand2_5kHz),
                            spokenDescription = "%+.0f decibels at 2.5 kiloHertz".format(settings.eqBand2_5kHz),
                            testTag = "slider_eq_2_5k"
                        )
                        AccessibleEffectSlider(
                            title = "Presence & Crisp (6 kHz)",
                            value = settings.eqBand6kHz,
                            onValueChange = { onSettingsChanged(settings.copy(eqBand6kHz = it)) },
                            valueRange = -12f..12f,
                            step = 1f,
                            displayValue = "%+.0f dB".format(settings.eqBand6kHz),
                            spokenDescription = "%+.0f decibels at 6 kiloHertz".format(settings.eqBand6kHz),
                            testTag = "slider_eq_6k"
                        )
                        AccessibleEffectSlider(
                            title = "Air & Brilliance (15 kHz)",
                            value = settings.eqBand15kHz,
                            onValueChange = { onSettingsChanged(settings.copy(eqBand15kHz = it)) },
                            valueRange = -12f..12f,
                            step = 1f,
                            displayValue = "%+.0f dB".format(settings.eqBand15kHz),
                            spokenDescription = "%+.0f decibels at 15 kiloHertz".format(settings.eqBand15kHz),
                            testTag = "slider_eq_15k"
                        )
                    }
                }
            }
        }

        // PITCH SHIFT & PHASE INVERT
        Card(
            colors = CardDefaults.cardColors(containerColor = if (isHighContrast) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { pitchExpanded = !pitchExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pitch Shifter & Polarity", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Transpose pitch semitones & phase invert", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = pitchExpanded,
                        onCheckedChange = { pitchExpanded = it },
                        modifier = Modifier.testTag("switch_pitch_expanded")
                    )
                }

                AnimatedVisibility(visible = pitchExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        EffectPresetDropdown(
                            effectId = "pitch_polarity",
                            effectTitle = "Pitch Shifter",
                            currentValues = PitchPolarityParams(settings.pitchShiftSemitones, settings.invertPhase),
                            presets = EffectPanelPresetsCatalog.pitchPolarityPresets,
                            defaultOption = EffectPanelPresetsCatalog.pitchPolarityDefault,
                            valuesEqual = { a, b -> a.semitones == b.semitones && a.invertPhase == b.invertPhase },
                            onApplyPreset = { preset ->
                                onSettingsChanged(
                                    settings.copy(
                                        pitchShiftSemitones = preset.semitones,
                                        invertPhase = preset.invertPhase
                                    )
                                )
                            }
                        )

                        AccessibleEffectSlider(
                            title = "Pitch Semitones",
                            value = settings.pitchShiftSemitones.toFloat(),
                            onValueChange = { onSettingsChanged(settings.copy(pitchShiftSemitones = it.toInt())) },
                            valueRange = -12f..12f,
                            step = 1f,
                            displayValue = "%+d st".format(settings.pitchShiftSemitones),
                            spokenDescription = "%+d semitones pitch shift".format(settings.pitchShiftSemitones),
                            testTag = "slider_pitch_shift"
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Invert Phase (Polarity)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Flips waveform 180 degrees", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = settings.invertPhase,
                                onCheckedChange = { onSettingsChanged(settings.copy(invertPhase = it)) },
                                modifier = Modifier.testTag("switch_invert_phase")
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Accessible Studio Insert Silence Dialog
 */
@Composable
fun StudioInsertSilenceDialog(
    currentPositionMs: Long,
    onDismiss: () -> Unit,
    onConfirmInsert: (seconds: Int) -> Unit
) {
    var selectedSeconds by remember { mutableIntStateOf(2) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Insert Digital Silence", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Insert digital silence at current playhead: ${AccessibilityHelper.formatTimeMMSS(currentPositionMs)}.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text("Silence Duration:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(1, 2, 3, 5, 10).forEach { sec ->
                        FilterChip(
                            selected = selectedSeconds == sec,
                            onClick = { selectedSeconds = sec },
                            label = { Text("${sec}s") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmInsert(selectedSeconds) },
                modifier = Modifier.testTag("btn_confirm_insert_silence")
            ) {
                Text("Insert ${selectedSeconds}s Silence")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Accessible Studio Tone & Noise Generator Dialog
 */
@Composable
fun StudioToneGeneratorDialog(
    onDismiss: () -> Unit,
    onConfirmGenerate: (type: String, frequencyHz: Float, durationSec: Int) -> Unit
) {
    var toneType by remember { mutableStateOf("Sine") }
    var frequencyHz by remember { mutableFloatStateOf(440f) }
    var durationSec by remember { mutableIntStateOf(5) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Synthesize Tone / Noise", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Waveform Type:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Sine", "Square", "Noise").forEach { type ->
                        FilterChip(
                            selected = toneType == type,
                            onClick = { toneType = type },
                            label = { Text(type) }
                        )
                    }
                }

                if (toneType != "Noise") {
                    AccessibleEffectSlider(
                        title = "Frequency",
                        value = frequencyHz,
                        onValueChange = { frequencyHz = it },
                        valueRange = 100f..2000f,
                        step = 20f,
                        displayValue = "%.0f Hz".format(frequencyHz),
                        spokenDescription = "%.0f Hertz frequency".format(frequencyHz),
                        testTag = "slider_tone_freq"
                    )

                    // Quick Frequency Presets
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(220f, 440f, 880f, 1000f).forEach { freq ->
                            OutlinedButton(
                                onClick = { frequencyHz = freq },
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("%.0fHz".format(freq), fontSize = 11.sp)
                            }
                        }
                    }
                }

                AccessibleEffectSlider(
                    title = "Duration",
                    value = durationSec.toFloat(),
                    onValueChange = { durationSec = it.toInt() },
                    valueRange = 1f..15f,
                    step = 1f,
                    displayValue = "${durationSec}s",
                    spokenDescription = "$durationSec seconds duration",
                    testTag = "slider_tone_duration"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmGenerate(toneType, frequencyHz, durationSec) },
                modifier = Modifier.testTag("btn_confirm_generate_tone")
            ) {
                Text("Synthesize $toneType")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Accessible Studio Blank Project Dialog:
 * Allows user to create an empty audio workspace with clean digital silence canvas.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioBlankProjectDialog(
    onDismiss: () -> Unit,
    onConfirmCreate: (durationSec: Int) -> Unit
) {
    var selectedSeconds by remember { mutableIntStateOf(10) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Create Blank Project", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Opens a new blank audio project tab while keeping your existing files open. Choose 0s (Empty) to paste right away, or pick a silent canvas length.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text("Canvas Duration:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(0, 5, 10, 30, 60, 120).forEach { sec ->
                        val labelText = when {
                            sec == 0 -> "0s (Empty)"
                            sec >= 60 -> "${sec / 60}m"
                            else -> "${sec}s"
                        }
                        FilterChip(
                            selected = selectedSeconds == sec,
                            onClick = { selectedSeconds = sec },
                            label = { Text(labelText) },
                            modifier = Modifier.testTag("chip_blank_duration_${sec}s")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmCreate(selectedSeconds) },
                modifier = Modifier.testTag("btn_confirm_blank_project")
            ) {
                Text(if (selectedSeconds == 0) "Create Empty (0s)" else "Create Blank (${selectedSeconds}s)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Accessible Studio Change Gain / Volume Dialog (+/- dB)
 */
@Composable
fun StudioGainDialog(
    onDismiss: () -> Unit,
    onConfirmGain: (gainDb: Float) -> Unit
) {
    var gainDb by remember { mutableFloatStateOf(3.0f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Adjust Gain / Volume", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Amplify or attenuate audio volume by precise decibels. Applied to selection if active, or whole track.",
                    style = MaterialTheme.typography.bodyMedium
                )

                AccessibleEffectSlider(
                    title = "Gain",
                    value = gainDb,
                    onValueChange = { gainDb = it },
                    valueRange = -24f..24f,
                    step = 1f,
                    displayValue = "%+.1f dB".format(gainDb),
                    spokenDescription = "%+.1f decibels gain".format(gainDb),
                    testTag = "slider_adjust_gain_db"
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(-6f, -3f, 0f, 3f, 6f, 12f).forEach { g ->
                        OutlinedButton(
                            onClick = { gainDb = g },
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("%+.0fdB".format(g), fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmGain(gainDb) },
                modifier = Modifier.testTag("btn_confirm_gain_adjust")
            ) {
                Text("Apply %+.1f dB".format(gainDb))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Accessible Studio Audio Statistics & Level Inspection Dialog
 */
@Composable
fun StudioAudioStatsDialog(
    stats: com.example.audio.StudioAudioProcessor.AudioStats,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Audio Statistics & Level", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Detailed waveform inspection metrics for TalkBack and mastering:", style = MaterialTheme.typography.bodySmall)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Peak Level: %.1f dBFS (%d / 32767)".format(stats.peakDb, stats.peakValue), fontWeight = FontWeight.Bold)
                        Text("RMS Power: %.1f dBFS".format(stats.rmsDb), fontWeight = FontWeight.SemiBold)
                        Text("Total Duration: %.2f seconds (%d samples)".format(stats.durationSeconds, stats.sampleCount))
                        Text("Sample Rate: 44,100 Hz (16-bit Mono)")
                        Text(
                            text = if (stats.isClipped) "WARNING: Peak Clipped (> 0 dBFS)" else "Headroom: Safe (No Clipping)",
                            color = if (stats.isClipped) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

/**
 * Accessible Studio Audio File Properties Dialog (Sample Rate, Channels, Length, Format, Bit Depth)
 */
@Composable
fun StudioAudioPropertiesDialog(
    fileName: String,
    durationMs: Long,
    sampleRate: Int = 44100,
    channels: String = "Mono (1 Channel)",
    format: String = "WAV PCM 16-bit",
    fileSizeBytes: Long = 0L,
    onDismiss: () -> Unit
) {
    val isHighContrast = LocalHighContrast.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Audio File Properties", fontWeight = FontWeight.Black)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Technical details and audio specifications:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("• File Name: $fileName", fontWeight = FontWeight.Bold)
                        Text("• Duration: ${AccessibilityHelper.formatTimeVisual(durationMs)} (${"%.2f".format(durationMs / 1000f)} seconds)")
                        Text("• Sample Rate: $sampleRate Hz")
                        Text("• Channels: $channels")
                        Text("• Bit Depth: 16-bit Signed Integer")
                        Text("• Format: $format")
                        if (fileSizeBytes > 0) {
                            val kb = fileSizeBytes / 1024
                            Text("• File Size: $kb KB ($fileSizeBytes bytes)")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_close_audio_properties")
            ) {
                Text("Close")
            }
        }
    )
}


