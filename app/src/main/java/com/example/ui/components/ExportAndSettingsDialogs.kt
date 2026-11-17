package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalHighContrast
import java.io.File

@Composable
fun AccessibleExportDialog(
    defaultFileName: String,
    isVideo: Boolean,
    isExporting: Boolean,
    exportProgress: Float,
    exportedFile: File?,
    onDismiss: () -> Unit,
    onConfirmExport: (String, String) -> Unit,
    onPlayExported: (File) -> Unit,
    onShareExported: (File) -> Unit,
    initialFormat: String = "WAV"
) {
    val isHighContrast = LocalHighContrast.current
    var fileName by remember(defaultFileName) { mutableStateOf(defaultFileName) }
    var selectedFormat by remember(initialFormat) { mutableStateOf(if (isVideo) "MP4" else initialFormat) }

    AlertDialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("accessible_export_dialog")
            .border(
                if (isHighContrast) 2.dp else 1.dp,
                if (isHighContrast) Color.White else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        containerColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = if (exportedFile != null) "Export Succeeded!" else "Export to Single File",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (exportedFile != null) {
                    // Success View
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                liveRegion = LiveRegionMode.Assertive
                                contentDescription = "Export complete. File saved: ${exportedFile.name}. Tap Play to listen or Share to send."
                            }
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isHighContrast) Color(0xFF00FF66) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Column {
                            Text(
                                text = exportedFile.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Saved in Single Export File",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onPlayExported(exportedFile) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_play_exported_result"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isHighContrast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
                                contentColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Play File")
                        }

                        OutlinedButton(
                            onClick = { onShareExported(exportedFile) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_share_exported_result"),
                            border = BorderStroke(if (isHighContrast) 2.dp else 1.dp, MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share")
                        }
                    }
                } else if (isExporting) {
                    // Exporting in progress
                    val percent = (exportProgress * 100).toInt()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                liveRegion = LiveRegionMode.Polite
                                contentDescription = "Exporting file in progress: $percent percent complete."
                            },
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Merging and writing single file... $percent%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        LinearProgressIndicator(
                            progress = { exportProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp),
                            color = if (isHighContrast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
                            trackColor = if (isHighContrast) Color(0xFF333333) else MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                } else {
                    // Pre-export configuration
                    Text(
                        text = if (isVideo) {
                            "Your edits, soundtrack, and video adjustments will be merged and exported into a single MP4 video file."
                        } else {
                            "Your audio edits, effects, and trimming will be merged and exported. Choose file format below:"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!isVideo) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { selectedFormat = "WAV" },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("btn_format_wav")
                                        .semantics {
                                            contentDescription = "Format WAV Lossless. ${if (selectedFormat == "WAV") "Selected" else "Not selected"}"
                                        },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (selectedFormat == "WAV") MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                    ),
                                    border = BorderStroke(
                                        if (selectedFormat == "WAV") 2.dp else 1.dp,
                                        if (selectedFormat == "WAV") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    )
                                ) {
                                    Text(
                                        text = "WAV",
                                        fontWeight = if (selectedFormat == "WAV") FontWeight.Black else FontWeight.Normal,
                                        color = if (selectedFormat == "WAV") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                OutlinedButton(
                                    onClick = { selectedFormat = "MP3" },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("btn_format_mp3")
                                        .semantics {
                                            contentDescription = "Format MP3 Audio. ${if (selectedFormat == "MP3") "Selected" else "Not selected"}"
                                        },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (selectedFormat == "MP3") MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                                    ),
                                    border = BorderStroke(
                                        if (selectedFormat == "MP3") 2.dp else 1.dp,
                                        if (selectedFormat == "MP3") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
                                    )
                                ) {
                                    Text(
                                        text = "MP3",
                                        fontWeight = if (selectedFormat == "MP3") FontWeight.Black else FontWeight.Normal,
                                        color = if (selectedFormat == "MP3") MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { selectedFormat = "FLAC" },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("btn_format_flac")
                                        .semantics {
                                            contentDescription = "Format FLAC Lossless. ${if (selectedFormat == "FLAC") "Selected" else "Not selected"}"
                                        },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (selectedFormat == "FLAC") MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent
                                    ),
                                    border = BorderStroke(
                                        if (selectedFormat == "FLAC") 2.dp else 1.dp,
                                        if (selectedFormat == "FLAC") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
                                    )
                                ) {
                                    Text(
                                        text = "FLAC",
                                        fontWeight = if (selectedFormat == "FLAC") FontWeight.Black else FontWeight.Normal,
                                        color = if (selectedFormat == "FLAC") MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                OutlinedButton(
                                    onClick = { selectedFormat = "M4A" },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("btn_format_m4a")
                                        .semantics {
                                            contentDescription = "Format M4A AAC. ${if (selectedFormat == "M4A") "Selected" else "Not selected"}"
                                        },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (selectedFormat == "M4A") MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
                                    ),
                                    border = BorderStroke(
                                        if (selectedFormat == "M4A") 2.dp else 1.dp,
                                        if (selectedFormat == "M4A") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    )
                                ) {
                                    Text(
                                        text = "M4A",
                                        fontWeight = if (selectedFormat == "M4A") FontWeight.Black else FontWeight.Normal,
                                        color = if (selectedFormat == "M4A") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        label = { Text("Output File Name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_export_filename")
                            .semantics {
                                contentDescription = "Output File Name text input. Current name is $fileName"
                            },
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            if (exportedFile != null) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("btn_export_done")
                ) {
                    Text("Close")
                }
            } else if (!isExporting) {
                Button(
                    onClick = {
                        val base = fileName.trim().ifEmpty { defaultFileName }
                        onConfirmExport(base, selectedFormat)
                    },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("btn_confirm_export"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        contentColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isVideo) "Save MP4 File" else "Save $selectedFormat File",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            if (!isExporting && exportedFile == null) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("btn_cancel_export")
                ) {
                    Text("Cancel")
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilitySettingsSheet(
    isHighContrast: Boolean,
    isHapticsEnabled: Boolean,
    defaultExportFormat: String,
    onSaveSettings: (highContrast: Boolean, hapticsEnabled: Boolean, exportFormat: String) -> Unit,
    onOpenUserGuide: () -> Unit = {},
    onOpenWhatsNew: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var draftHighContrast by remember(isHighContrast) { mutableStateOf(isHighContrast) }
    var draftHapticsEnabled by remember(isHapticsEnabled) { mutableStateOf(isHapticsEnabled) }
    var draftExportFormat by remember(defaultExportFormat) { mutableStateOf(defaultExportFormat) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (draftHighContrast) Color.Black else MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("accessibility_settings_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STUDIO & ACCESSIBILITY SETTINGS",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = if (draftHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp).testTag("btn_close_settings_sheet")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close Settings")
                }
            }

            // 1. Export Selection Format (WAV / MP3 / FLAC / M4A)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_export_selection_format"),
                colors = CardDefaults.cardColors(
                    containerColor = if (draftHighContrast) Color(0xFF161616) else MaterialTheme.colorScheme.surfaceVariant
                ),
                border = BorderStroke(
                    if (draftHighContrast) 2.dp else 1.dp,
                    if (draftHighContrast) Color.White else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            tint = if (draftHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Column {
                            Text(
                                text = "Export Selection Format",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (draftHighContrast) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Default audio format used when saving or exporting ($draftExportFormat selected)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val formatsRow1 = listOf("WAV", "MP3")
                    val formatsRow2 = listOf("FLAC", "M4A")
                    listOf(formatsRow1, formatsRow2).forEach { rowFormats ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowFormats.forEach { fmt ->
                                val isSelected = draftExportFormat == fmt
                                OutlinedButton(
                                    onClick = { draftExportFormat = fmt },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("btn_settings_format_${fmt.lowercase()}")
                                        .semantics {
                                            contentDescription = "Export Selection Format $fmt. ${if (isSelected) "Selected" else "Tap to select"}"
                                        },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                    ),
                                    border = BorderStroke(
                                        if (isSelected) 2.dp else 1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    )
                                ) {
                                    Text(
                                        text = if (isSelected) "✓ $fmt" else fmt,
                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else if (draftHighContrast) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. High Contrast Mode Toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (draftHighContrast) Color(0xFF161616) else MaterialTheme.colorScheme.surfaceVariant
                ),
                border = BorderStroke(
                    if (draftHighContrast) 2.dp else 1.dp,
                    if (draftHighContrast) Color.White else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Visibility,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = "High Contrast Mode",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (draftHighContrast) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Pure black OLED theme, neon borders, and max readability for TalkBack and low-vision",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = draftHighContrast,
                        onCheckedChange = { draftHighContrast = it },
                        modifier = Modifier.testTag("switch_high_contrast"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = MaterialTheme.colorScheme.secondary
                        )
                    )
                }
            }

            // 3. Haptic Feedback Toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (draftHighContrast) Color(0xFF161616) else MaterialTheme.colorScheme.surfaceVariant
                ),
                border = BorderStroke(
                    if (draftHighContrast) 2.dp else 1.dp,
                    if (draftHighContrast) Color.White else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Vibration,
                            contentDescription = null,
                            tint = if (draftHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = "Tactile Haptic Feedback",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (draftHighContrast) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Vibrate on play/pause, marker cuts, step nudges, and export completion",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = draftHapticsEnabled,
                        onCheckedChange = { draftHapticsEnabled = it },
                        modifier = Modifier.testTag("switch_haptics"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = MaterialTheme.colorScheme.secondary
                        )
                    )
                }
            }

            // Primary Save All Settings Button
            Button(
                onClick = {
                    onSaveSettings(draftHighContrast, draftHapticsEnabled, draftExportFormat)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_save_settings")
                    .semantics {
                        contentDescription = "Save all settings. Export format $draftExportFormat, High contrast ${if (draftHighContrast) "On" else "Off"}, Haptics ${if (draftHapticsEnabled) "On" else "Off"}"
                    },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (draftHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    contentColor = if (draftHighContrast) Color.Black else MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Save All Settings ($draftExportFormat)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
            }

            // ABOUT ACCESSIBLE STUDIO SECTION
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_about_accessible_studio"),
                colors = CardDefaults.cardColors(
                    containerColor = if (draftHighContrast) Color(0xFF121212) else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    if (draftHighContrast) 2.dp else 1.dp,
                    if (draftHighContrast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "ABOUT ACCESSIBLE STUDIO",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Accessible Studio v1.2 is made for everyone. It is simple, fast and easy to use. It works with TalkBack and touch. Everyone can create, edit and share easily.",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Storage & Format Features:",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "• Preserves your exact opened file name when saving\n" +
                                        "• Default Export Selection Format (WAV, MP3, FLAC, M4A)\n" +
                                        "• Audio saved to: Music/Audio Studio/\n" +
                                        "• Video saved to: Movies/Accessible Studio/\n" +
                                        "• Anti-crackle Mic Recording & 7-Band Equalizer",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Text(
                        text = "⚡ 100% OFFLINE CAPABILITY: All audio processing, filters, DSP effects, trimming, video rendering, and single-file exports are performed 100% on-device without requiring internet access.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (draftHighContrast) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Version 1.2 • 100% Free & Open Accessible Studio • 100% Offline",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onOpenUserGuide,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_about_user_guide")
                            .semantics {
                                contentDescription = "User Guide in English and Hindi. Tap to read complete app instructions."
                            },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("User Guide (English & हिंदी)", fontWeight = FontWeight.Black)
                    }
                    OutlinedButton(
                        onClick = onOpenWhatsNew,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_about_whats_new")
                            .semantics {
                                contentDescription = "What's New. Tap to view recent updates and fixes in Version 1.2."
                            },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            if (draftHighContrast) 1.5.dp else 1.dp,
                            if (draftHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.NewReleases, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("What's New (नया क्या है)", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}
