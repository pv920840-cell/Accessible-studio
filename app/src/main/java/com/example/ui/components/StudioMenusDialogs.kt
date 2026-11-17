package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun StudioSelectionMenuDialog(
    onCommand: (AudioStudioCommand) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Selection Menu", fontWeight = FontWeight.Black) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MenuActionButton("Select All Track", "menu_sel_all") { onCommand(AudioStudioCommand.SelectAll); onDismiss() }
                MenuActionButton("Set Start Marker to Playhead", "menu_sel_start") { onCommand(AudioStudioCommand.SetSelStart); onDismiss() }
                MenuActionButton("Set End Marker to Playhead", "menu_sel_end") { onCommand(AudioStudioCommand.SetSelEnd); onDismiss() }
                MenuActionButton("Crop to Selection", "menu_sel_crop") { onCommand(AudioStudioCommand.CropSelection); onDismiss() }
                MenuActionButton("Audition Selection (Play 1)", "menu_sel_play1") { onCommand(AudioStudioCommand.Play1Selection); onDismiss() }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun StudioEffectMenuDialog(
    onCommand: (AudioStudioCommand) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Effect Menu (100% Free DSP)", fontWeight = FontWeight.Black) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Select an Effect Suite Category:", style = MaterialTheme.typography.bodySmall)
                MenuActionButton("Effect (Censor, Doppler, Pitch, Time, Voice Over...)", "menu_eff_cat_effect") {
                    onCommand(AudioStudioCommand.OpenProSuiteTab(0)); onDismiss()
                }
                MenuActionButton("Channels (Channel Mixer, MaxMatch, Reduce Vocals, Pan)", "menu_eff_cat_channels") {
                    onCommand(AudioStudioCommand.OpenProSuiteTab(1)); onDismiss()
                }
                MenuActionButton("Filter (Parametric EQ, Bandpass/stop, Pop/Click, Silence)", "menu_eff_cat_filter") {
                    onCommand(AudioStudioCommand.OpenProSuiteTab(2)); onDismiss()
                }
                MenuActionButton("Volume (Auto Gain, Loudness, Match, Shape Volume)", "menu_eff_cat_volume") {
                    onCommand(AudioStudioCommand.OpenProSuiteTab(3)); onDismiss()
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun StudioViewMenuDialog(
    isHighContrast: Boolean,
    followCursor: Boolean = true,
    onToggleHighContrast: () -> Unit,
    onCommand: (AudioStudioCommand) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("View Menu", fontWeight = FontWeight.Black) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MenuActionButton("All (Fit Entire Track)", "menu_view_all") {
                    onCommand(AudioStudioCommand.ViewAll); onDismiss()
                }
                MenuActionButton("Selection (Fit Trim Range)", "menu_view_selection") {
                    onCommand(AudioStudioCommand.ViewSelection); onDismiss()
                }
                MenuActionButton("Specify View Window...", "menu_view_specify") {
                    onCommand(AudioStudioCommand.OpenSpecifyViewDialog); onDismiss()
                }
                MenuActionButton("Previous View", "menu_view_previous") {
                    onCommand(AudioStudioCommand.ViewPrevious); onDismiss()
                }
                MenuActionButton(
                    if (followCursor) "Follow Cursor: On" else "Follow Cursor: Off",
                    "menu_view_follow_cursor"
                ) {
                    onCommand(AudioStudioCommand.ToggleFollowCursor); onDismiss()
                }
                MenuActionButton("Switch Visual Mode (Waveform / Spectrum / Bars)", "menu_view_visual") {
                    onCommand(AudioStudioCommand.ToggleVisualMode); onDismiss()
                }
                MenuActionButton(
                    if (isHighContrast) "Disable High-Contrast Theme" else "Enable High-Contrast Theme",
                    "menu_view_contrast"
                ) {
                    onToggleHighContrast(); onDismiss()
                }
                MenuActionButton("View File Statistics & RMS Analysis", "menu_view_stats") {
                    onCommand(AudioStudioCommand.OpenStatsDialog); onDismiss()
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun StudioSpecifyViewDialog(
    durationMs: Long,
    currentStartMs: Long,
    currentEndMs: Long,
    currentVerticalZoom: Float,
    onConfirmView: (startMs: Long, endMs: Long, verticalZoom: Float) -> Unit,
    onDismiss: () -> Unit
) {
    val safeDurationSec = (durationMs.coerceAtLeast(100L) / 1000.0)
    var startSecText by remember {
        mutableStateOf("%.2f".format(java.util.Locale.US, currentStartMs / 1000.0))
    }
    var endSecText by remember {
        mutableStateOf("%.2f".format(java.util.Locale.US, currentEndMs.coerceAtLeast(currentStartMs + 50L) / 1000.0))
    }
    var vZoomText by remember {
        mutableStateOf("%.2f".format(java.util.Locale.US, currentVerticalZoom))
    }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Specify View Range", fontWeight = FontWeight.Black) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Track duration: ${"%.2f".format(java.util.Locale.US, safeDurationSec)}s. Enter exact start/end seconds and amplitude scale.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = startSecText,
                    onValueChange = { startSecText = it; errorMsg = null },
                    label = { Text("View Start (seconds)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("input_specify_view_start")
                )
                OutlinedTextField(
                    value = endSecText,
                    onValueChange = { endSecText = it; errorMsg = null },
                    label = { Text("View End (seconds)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("input_specify_view_end")
                )
                OutlinedTextField(
                    value = vZoomText,
                    onValueChange = { vZoomText = it; errorMsg = null },
                    label = { Text("Amplitude Zoom (0.25x - 8.0x)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("input_specify_view_vzoom")
                )
                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sSec = startSecText.trim().toDoubleOrNull()
                    val eSec = endSecText.trim().toDoubleOrNull()
                    val vz = vZoomText.trim().toFloatOrNull() ?: currentVerticalZoom
                    if (sSec == null || eSec == null || sSec < 0.0 || eSec <= sSec) {
                        errorMsg = "Enter valid start and end times (End > Start >= 0)."
                    } else {
                        val sMs = (sSec * 1000.0).toLong().coerceIn(0L, durationMs.coerceAtLeast(100L))
                        val eMs = (eSec * 1000.0).toLong().coerceIn(sMs + 50L, durationMs.coerceAtLeast(sMs + 50L))
                        onConfirmView(sMs, eMs, vz.coerceIn(0.25f, 8.0f))
                    }
                },
                modifier = Modifier.height(48.dp).testTag("btn_confirm_specify_view")
            ) {
                Text("Apply View", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.height(48.dp).testTag("btn_cancel_specify_view")
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun StudioControlMenuDialog(
    onCommand: (AudioStudioCommand) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Control Menu", fontWeight = FontWeight.Black) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MenuActionButton("Play 1 (Play Selection)", "menu_ctrl_play1") { onCommand(AudioStudioCommand.Play1Selection); onDismiss() }
                MenuActionButton("Play 2 (Play Continuous)", "menu_ctrl_play2") { onCommand(AudioStudioCommand.Play2Continuous); onDismiss() }
                MenuActionButton("Play 3 (Loop Audition)", "menu_ctrl_play3") { onCommand(AudioStudioCommand.Play3Loop); onDismiss() }
                MenuActionButton("Play / Pause Toggle", "menu_ctrl_play_pause") { onCommand(AudioStudioCommand.PlayPause); onDismiss() }
                MenuActionButton("Play Stop (Return to Start)", "menu_ctrl_stop") { onCommand(AudioStudioCommand.StopPlayback); onDismiss() }
                MenuActionButton("Rewind (-5s)", "menu_ctrl_rewind") { onCommand(AudioStudioCommand.Rewind); onDismiss() }
                MenuActionButton("Fast Forward (+5s)", "menu_ctrl_ff") { onCommand(AudioStudioCommand.FastForward); onDismiss() }
                MenuActionButton("Record New Track", "menu_ctrl_rec_new") { onCommand(AudioStudioCommand.RecordNew); onDismiss() }
                MenuActionButton("Record Selection (Punch-In)", "menu_ctrl_rec_sel") { onCommand(AudioStudioCommand.RecordSelection); onDismiss() }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun MenuActionButton(
    text: String,
    tag: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag(tag)
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}
