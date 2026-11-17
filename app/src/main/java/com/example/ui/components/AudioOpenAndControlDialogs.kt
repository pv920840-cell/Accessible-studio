package com.example.ui.components

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ExportedMedia
import com.example.ui.theme.LocalHighContrast

enum class AudioVisualMode(val displayName: String) {
    WAVEFORM("Waveform"),
    SPECTRUM("Spectrum"),
    BARS("Level Bars")
}

sealed class AudioStudioCommand {
    // File Menu
    object OpenAudioModal : AudioStudioCommand()
    object NewBlankProject : AudioStudioCommand()
    object SaveWav : AudioStudioCommand()
    object SaveAsMp3 : AudioStudioCommand()
    object ExportFile : AudioStudioCommand()
    object OpenProperties : AudioStudioCommand()
    object CloseProject : AudioStudioCommand()

    // Edit Menu
    object Undo : AudioStudioCommand()
    object Redo : AudioStudioCommand()
    object CutSelection : AudioStudioCommand()
    object CopySelection : AudioStudioCommand()
    object MuteSelection : AudioStudioCommand()
    object DeleteSelection : AudioStudioCommand()
    object PasteAtCursor : AudioStudioCommand()
    object PasteAtEnd : AudioStudioCommand()
    object MixPaste : AudioStudioCommand()
    object OpenMixAudioDialog : AudioStudioCommand()
    object OpenSilenceDialog : AudioStudioCommand()
    object SelectAll : AudioStudioCommand()

    // Selection Menu
    object SetSelStart : AudioStudioCommand()
    object SetSelEnd : AudioStudioCommand()
    object CropSelection : AudioStudioCommand()
    object Play1Selection : AudioStudioCommand()
    object OpenCuePointsDialog : AudioStudioCommand()

    // Effect Menu - Repair / Cleaning
    object RemoveHum60Hz : AudioStudioCommand()
    object HighPassFilter : AudioStudioCommand()
    object LowPassFilter : AudioStudioCommand()
    object BandPassFilter : AudioStudioCommand()
    object RemoveDcOffset : AudioStudioCommand()
    object DeClick : AudioStudioCommand()

    // Effect Menu - Dynamics
    object ApplyAutoGain : AudioStudioCommand()
    object ApplyCompressor : AudioStudioCommand()
    object ApplyExpander : AudioStudioCommand()
    object NormalizePeak : AudioStudioCommand()
    object NormalizeRms : AudioStudioCommand()
    object FadeIn : AudioStudioCommand()
    object FadeOut : AudioStudioCommand()
    object OpenGainDialog : AudioStudioCommand()

    // Effect Menu - Creative DSP
    object ApplyMechanize : AudioStudioCommand()
    object ReverseTrack : AudioStudioCommand()

    // View Menu (Time Zoom, Selection, Specify, Previous, Preset, Follow Cursor, Go To, Amplitude Zoom)
    object ViewAll : AudioStudioCommand()
    object ViewSelection : AudioStudioCommand()
    object OpenSpecifyViewDialog : AudioStudioCommand()
    object ViewPrevious : AudioStudioCommand()
    object ViewPresetDefault : AudioStudioCommand()
    object ToggleFollowCursor : AudioStudioCommand()
    object ZoomIn : AudioStudioCommand()
    object ZoomOut : AudioStudioCommand()
    object ZoomReset : AudioStudioCommand()
    object Zoom10To1 : AudioStudioCommand()
    object Zoom1To1 : AudioStudioCommand()
    data class ViewTimeSpan(val spanMs: Long) : AudioStudioCommand()
    object GoToSelectionStart : AudioStudioCommand()
    object GoToSelectionFinish : AudioStudioCommand()
    object GoToBeginning : AudioStudioCommand()
    object GoToEnd : AudioStudioCommand()
    object VerticalZoomIn : AudioStudioCommand()
    object VerticalZoomOut : AudioStudioCommand()
    object VerticalZoomAll : AudioStudioCommand()
    object ToggleVisualMode : AudioStudioCommand()

    // Tools & Studio Modals
    object OpenVoiceOverDialog : AudioStudioCommand()
    object OpenVocalKaraokeDialog : AudioStudioCommand()
    object OpenToneGenerator : AudioStudioCommand()
    object OpenStatsDialog : AudioStudioCommand()
    data class OpenEffectsTab(val tabIndex: Int) : AudioStudioCommand()
    data class OpenProSuiteTab(val tabIndex: Int) : AudioStudioCommand()

    // Transport Controls
    object Play2Continuous : AudioStudioCommand()
    object Play3Loop : AudioStudioCommand()
    object StopPlayback : AudioStudioCommand()
    object PlayPause : AudioStudioCommand()
    object Rewind : AudioStudioCommand()
    object FastForward : AudioStudioCommand()
    object RecordNew : AudioStudioCommand()
    object RecordSelection : AudioStudioCommand()
}

@Composable
fun AudioOpenDialog(
    recentFiles: List<String>,
    savedExports: List<ExportedMedia>,
    openInNewTab: Boolean = true,
    onOpenInNewTabChange: ((Boolean) -> Unit)? = null,
    isImporting: Boolean = false,
    importingMessage: String = "",
    onImportMultipleFiles: (() -> Unit)? = null,
    onImportFromOs: () -> Unit,
    onImportFile: () -> Unit,
    onBrowseOsFolder: () -> Unit,
    onOpenExportedFile: (ExportedMedia) -> Unit,
    onClearRecent: () -> Unit,
    onGoHome: () -> Unit,
    onHelp: () -> Unit,
    onDismiss: () -> Unit
) {
    val audioExports = savedExports.filter {
        val lower = it.title.lowercase()
        !it.isVideo && (lower.endsWith(".wav") || lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".flac") || lower.endsWith(".aac"))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Open Audio • Accessible Studio", fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isImporting) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth().testTag("card_dialog_importing_audio")
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = importingMessage.ifBlank { "Decoding audio file..." },
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                if (onOpenInNewTabChange != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Open in New Project Tab", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Keep current track open alongside new file", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        androidx.compose.material3.Switch(
                            checked = openInNewTab,
                            onCheckedChange = onOpenInNewTabChange,
                            enabled = !isImporting,
                            modifier = Modifier.testTag("switch_open_in_new_tab")
                        )
                    }
                    HorizontalDivider()
                }
                // Primary OS Import buttons
                Button(
                    onClick = { onImportFromOs() },
                    enabled = !isImporting,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_open_import_from_os")
                        .semantics { contentDescription = "Import From OS document browser" }
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Import From OS", fontWeight = FontWeight.Bold)
                }

                if (onImportMultipleFiles != null) {
                    Button(
                        onClick = { onImportMultipleFiles() },
                        enabled = !isImporting,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_open_import_multiple")
                            .semantics { contentDescription = "Import multiple audio files into separate tabs at once" },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Import Multiple Files (Tabs)", fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = { onImportFile() },
                    enabled = !isImporting,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_open_import_file")
                        .semantics { contentDescription = "Import File from media library" },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Audiotrack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Import File", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { onBrowseOsFolder() },
                    enabled = !isImporting,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_open_os_folders")
                        .semantics { contentDescription = "Browse OS Folders for audio tracks" }
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("OS Folders ➔", fontWeight = FontWeight.Bold)
                }

                HorizontalDivider()

                // Local Studio Library / Music Folder Tracks
                Text("Accessible Studio Folder (${audioExports.size} files):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                if (audioExports.isEmpty()) {
                    Text(
                        "No exported tracks in Music/Audio Studio yet. Record or save a track to see it here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    audioExports.take(6).forEach { file ->
                        OutlinedButton(
                            onClick = { onOpenExportedFile(file) },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(file.title, maxLines = 1, fontSize = 12.sp)
                        }
                    }
                }

                if (recentFiles.isNotEmpty()) {
                    Text("Recent Activity:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    recentFiles.take(4).forEach { title ->
                        Text("• $title", style = MaterialTheme.typography.bodySmall)
                    }
                }

                // Clear, Home, Help row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onClearRecent,
                        modifier = Modifier.weight(1f).height(48.dp).testTag("btn_open_clear")
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clear", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onGoHome,
                        modifier = Modifier.weight(1f).height(48.dp).testTag("btn_open_home")
                    ) {
                        Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Home", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onHelp,
                        modifier = Modifier.weight(1f).height(48.dp).testTag("btn_open_help")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Help", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.height(48.dp).testTag("btn_open_cancel")
            ) {
                Text("Cancel", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioControlDeckCard(
    playbackRate: Float,
    masterVolume: Float,
    sampleRateHz: Int,
    visualMode: AudioVisualMode,
    onPlaybackRateChange: (Float) -> Unit,
    onMasterVolumeChange: (Float) -> Unit,
    onToggleVisualMode: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isHighContrast = LocalHighContrast.current
    Card(
        modifier = modifier.fillMaxWidth().testTag("card_control_deck"),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, if (isHighContrast) Color.White else MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text("Speed & Output Gain", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
                Text(
                    "${"%.2f".format(playbackRate)}x  •  ${sampleRateHz} Hz",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                    FilterChip(
                        selected = kotlin.math.abs(playbackRate - spd) < 0.04f,
                        onClick = { onPlaybackRateChange(spd) },
                        label = { Text("${spd}x", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.height(34.dp).testTag("chip_live_speed_$spd")
                    )
                }
            }

            AccessibleEffectSlider(
                title = "Output Volume",
                value = masterVolume,
                onValueChange = onMasterVolumeChange,
                valueRange = 0.0f..1.0f,
                step = 0.05f,
                displayValue = "${(masterVolume * 100).toInt()}%",
                spokenDescription = "Output volume ${(masterVolume * 100).toInt()} percent",
                testTag = "slider_live_output_volume"
            )
        }
    }
}
