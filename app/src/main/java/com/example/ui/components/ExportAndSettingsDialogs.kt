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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AndroidRecordingCapabilityProbe
import com.example.audio.RecordingCapabilityProbe
import com.example.data.StudioPreferences
import com.example.data.StudioSettingsState
import com.example.ui.theme.LocalHighContrast
import com.example.update.AppUpdateChecker
import com.example.update.UpdateCheckState
import com.example.video.CompressorQuality
import com.example.video.VideoFormatOption
import com.example.video.VideoResolutionPreset
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
    initialFormat: String = "WAV",
    initialVideoFormat: VideoFormatOption = VideoFormatOption.MP4,
    initialVideoQualityPreset: VideoResolutionPreset = VideoResolutionPreset.ORIGINAL,
    initialCompressorQuality: CompressorQuality = CompressorQuality.HIGH,
    onConfirmVideoExport: ((fileName: String, format: VideoFormatOption, resolution: VideoResolutionPreset, quality: CompressorQuality) -> Unit)? = null
) {
    val isHighContrast = LocalHighContrast.current
    var fileName by remember(defaultFileName) { mutableStateOf(defaultFileName) }
    var selectedFormat by remember(initialFormat) { mutableStateOf(if (isVideo) initialVideoFormat.name else initialFormat) }
    var selectedVideoFormat by remember(initialVideoFormat) { mutableStateOf(initialVideoFormat) }
    var selectedVideoResolution by remember(initialVideoQualityPreset) { mutableStateOf(initialVideoQualityPreset) }
    var selectedCompressorQuality by remember(initialCompressorQuality) { mutableStateOf(initialCompressorQuality) }

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
                            "Your edits, soundtrack, and video adjustments will be merged and exported into a single video file. Choose output format and quality below:"
                        } else {
                            "Your audio edits, effects, and trimming will be merged and exported. Choose output audio format below:"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!isVideo) {
                        val audioFormatRows = listOf(
                            listOf("WAV" to "Lossless WAV", "MP3" to "MP3 Audio", "FLAC" to "Lossless FLAC"),
                            listOf("AAC" to "AAC Audio", "OPUS" to "Opus Audio", "M4A" to "M4A AAC")
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            audioFormatRows.forEach { rowFormats ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    rowFormats.forEach { (fmt, spokenName) ->
                                        val isSelected = selectedFormat.equals(fmt, ignoreCase = true)
                                        OutlinedButton(
                                            onClick = { selectedFormat = fmt },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("btn_format_${fmt.lowercase()}")
                                                .semantics {
                                                    contentDescription = "Format $spokenName. ${if (isSelected) "Selected" else "Not selected"}"
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
                                                text = fmt,
                                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                                maxLines = 1,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Output Video Format",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            val videoFormatRows = VideoFormatOption.entries.chunked(3)
                            videoFormatRows.forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    row.forEach { fmt ->
                                        val isSelected = selectedVideoFormat == fmt
                                        OutlinedButton(
                                            onClick = {
                                                selectedVideoFormat = fmt
                                                selectedFormat = fmt.name
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("btn_video_format_${fmt.extension}")
                                                .semantics {
                                                    contentDescription = "Video format ${fmt.label}. ${if (isSelected) "Selected" else "Not selected"}"
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
                                                text = fmt.extension.uppercase(),
                                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                                maxLines = 1,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                    if (row.size < 3) {
                                        repeat(3 - row.size) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }

                            Text(
                                text = "Video Quality Preset",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            val qualityChoices = listOf(
                                Triple("Original", VideoResolutionPreset.ORIGINAL, CompressorQuality.HIGH),
                                Triple("High (1080p)", VideoResolutionPreset.RES_1080P, CompressorQuality.HIGH),
                                Triple("Medium (720p)", VideoResolutionPreset.RES_720P, CompressorQuality.MEDIUM),
                                Triple("Low (480p)", VideoResolutionPreset.RES_480P, CompressorQuality.LOW)
                            )
                            qualityChoices.chunked(2).forEach { qRow ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    qRow.forEach { (qLabel, resPreset, compQuality) ->
                                        val isSelected = selectedVideoResolution == resPreset && selectedCompressorQuality == compQuality
                                        val qTag = when (resPreset) {
                                            VideoResolutionPreset.ORIGINAL -> "original"
                                            VideoResolutionPreset.RES_1080P -> "1080p"
                                            VideoResolutionPreset.RES_720P -> "720p"
                                            VideoResolutionPreset.RES_480P -> "480p"
                                            else -> resPreset.name.lowercase()
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                selectedVideoResolution = resPreset
                                                selectedCompressorQuality = compQuality
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("btn_video_quality_$qTag")
                                                .semantics {
                                                    contentDescription = "Video quality $qLabel. ${if (isSelected) "Selected" else "Not selected"}"
                                                },
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                                            ),
                                            border = BorderStroke(
                                                if (isSelected) 2.dp else 1.dp,
                                                if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
                                            )
                                        ) {
                                            Text(
                                                text = qLabel,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                                maxLines = 1,
                                                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
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
                        if (isVideo && onConfirmVideoExport != null) {
                            onConfirmVideoExport(
                                base,
                                selectedVideoFormat,
                                selectedVideoResolution,
                                selectedCompressorQuality
                            )
                        } else {
                            onConfirmExport(base, if (isVideo) selectedVideoFormat.extension.uppercase() else selectedFormat)
                        }
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
                        text = if (isVideo) "Save ${selectedVideoFormat.extension.uppercase()} File" else "Save $selectedFormat File",
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
    autoCheckUpdates: Boolean = true,
    updateCheckState: UpdateCheckState = UpdateCheckState.Idle,
    lastUpdateCheckMs: Long = 0L,
    updateProvider: String = StudioPreferences.UPDATE_PROVIDER_AUTO,
    githubOwner: String = "",
    githubRepo: String = "",
    onToggleAutoCheckUpdates: ((Boolean) -> Unit)? = null,
    onSaveGitHubSource: ((provider: String, owner: String, repo: String) -> Unit)? = null,
    onCheckForUpdates: (() -> Unit)? = null,
    onDownloadUpdate: ((String) -> Unit)? = null,
    onShareApkLink: (() -> Unit)? = null,
    currentSettings: StudioSettingsState? = null,
    recordingCapabilityProbe: RecordingCapabilityProbe = AndroidRecordingCapabilityProbe,
    onImmediateSettingsUpdate: ((StudioSettingsState) -> Unit)? = null,
    onSaveFullSettings: ((StudioSettingsState) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val sheetContext = LocalContext.current
    val initialState = remember(
        currentSettings,
        isHighContrast,
        isHapticsEnabled,
        defaultExportFormat,
        autoCheckUpdates,
        updateProvider,
        githubOwner,
        githubRepo
    ) {
        (currentSettings ?: StudioSettingsState()).copy(
            isHighContrast = isHighContrast,
            isHapticsEnabled = isHapticsEnabled,
            defaultExportFormat = defaultExportFormat,
            autoCheckUpdates = autoCheckUpdates,
            updateProvider = updateProvider,
            githubOwner = githubOwner,
            githubRepo = githubRepo
        )
    }
    var draftHighContrast by remember(isHighContrast) { mutableStateOf(isHighContrast) }
    var draftHapticsEnabled by remember(isHapticsEnabled) { mutableStateOf(isHapticsEnabled) }
    var draftExportFormat by remember(defaultExportFormat) { mutableStateOf(defaultExportFormat) }
    var draftAutoCheckUpdates by remember(autoCheckUpdates) { mutableStateOf(autoCheckUpdates) }
    var draftUpdateProvider by remember(updateProvider) { mutableStateOf(updateProvider) }
    var draftGithubOwner by remember(githubOwner) { mutableStateOf(githubOwner) }
    var draftGithubRepo by remember(githubRepo) { mutableStateOf(githubRepo) }
    var draftStudioSettings by remember(initialState) { mutableStateOf(initialState) }

    fun currentMergedSettings(): StudioSettingsState {
        return draftStudioSettings.copy(
            isHighContrast = draftHighContrast,
            isHapticsEnabled = draftHapticsEnabled,
            defaultExportFormat = draftExportFormat,
            autoCheckUpdates = draftAutoCheckUpdates,
            updateProvider = draftUpdateProvider,
            githubOwner = draftGithubOwner,
            githubRepo = draftGithubRepo
        )
    }

    fun applyImmediateChange(updated: StudioSettingsState) {
        draftStudioSettings = updated
        draftHighContrast = updated.isHighContrast
        draftHapticsEnabled = updated.isHapticsEnabled
        draftExportFormat = updated.defaultExportFormat
        onImmediateSettingsUpdate?.invoke(updated)
    }

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

                    val formatsRow1 = listOf("WAV", "MP3", "FLAC")
                    val formatsRow2 = listOf("AAC", "OPUS", "M4A")
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

            // 4. Display & Visual Controls Section
            DisplayVisualControlsCard(
                isHighContrast = draftHighContrast,
                visualModeEnabled = draftStudioSettings.visualModeEnabled,
                showZoomControls = draftStudioSettings.showZoomControls,
                showWaveformDisplay = draftStudioSettings.showWaveformDisplay,
                onVisualModeChange = { enabled ->
                    applyImmediateChange(currentMergedSettings().copy(visualModeEnabled = enabled))
                },
                onZoomControlsChange = { enabled ->
                    applyImmediateChange(currentMergedSettings().copy(showZoomControls = enabled))
                },
                onWaveformDisplayChange = { enabled ->
                    applyImmediateChange(currentMergedSettings().copy(showWaveformDisplay = enabled))
                }
            )

            // 5. Recording Settings Section
            RecordingSettingsCard(
                settings = currentMergedSettings(),
                isHighContrast = draftHighContrast,
                capabilityProbe = recordingCapabilityProbe,
                onUpdateSettings = { updated ->
                    applyImmediateChange(updated)
                },
                onResetRecordingDefaults = {
                    applyImmediateChange(currentMergedSettings().withResetRecordingDefaults())
                }
            )

            // Primary Save All Settings Button
            Button(
                onClick = {
                    val finalSettings = currentMergedSettings()
                    onSaveFullSettings?.invoke(finalSettings)
                    onSaveSettings(draftHighContrast, draftHapticsEnabled, draftExportFormat)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_save_settings")
                    .semantics {
                        contentDescription = "Save all settings. Export format $draftExportFormat, High contrast ${if (draftHighContrast) "On" else "Off"}, Haptics ${if (draftHapticsEnabled) "On" else "Off"}, Visual mode ${if (draftStudioSettings.visualModeEnabled) "On" else "Off"}"
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
                            text = "Accessible Studio v${com.example.BuildConfig.VERSION_NAME} is made for everyone. It is simple, fast and easy to use. It works with TalkBack and touch. Everyone can create, edit and share easily.",
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
                        text = "Version ${com.example.BuildConfig.VERSION_NAME} • 100% Free & Open Accessible Studio • 100% Offline",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // App Updates Controls (Automatic Toggle + Manual Check Button)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_about_updates"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (draftHighContrast) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = BorderStroke(
                            if (draftHighContrast) 1.5.dp else 1.dp,
                            if (draftHighContrast) Color.White else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.SystemUpdate,
                                        contentDescription = null,
                                        tint = if (draftHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Automatic Check for Updates",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (draftHighContrast) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Automatically check for newer Accessible Studio builds when opening the app or About section",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Switch(
                                    checked = draftAutoCheckUpdates,
                                    onCheckedChange = { enabled ->
                                        draftAutoCheckUpdates = enabled
                                        onToggleAutoCheckUpdates?.invoke(enabled)
                                    },
                                    modifier = Modifier
                                        .testTag("switch_auto_check_updates")
                                        .semantics {
                                            contentDescription = "Automatic Check for Updates. Currently ${if (draftAutoCheckUpdates) "enabled" else "disabled"}. Double tap to toggle."
                                        },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.Black,
                                        checkedTrackColor = MaterialTheme.colorScheme.secondary
                                    )
                                )
                            }

                            val isChecking = updateCheckState is UpdateCheckState.Checking
                            Button(
                                onClick = { onCheckForUpdates?.invoke() },
                                enabled = !isChecking && onCheckForUpdates != null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("btn_about_check_updates")
                                    .semantics {
                                        contentDescription = if (isChecking) {
                                            "Checking for updates, please wait."
                                        } else {
                                            "Check for Updates now. Current version ${com.example.BuildConfig.VERSION_NAME}."
                                        }
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (draftHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                    contentColor = if (draftHighContrast) Color.Black else MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                if (isChecking) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = if (draftHighContrast) Color.Black else MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Checking for Updates...", fontWeight = FontWeight.Black)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Check for Updates", fontWeight = FontWeight.Black)
                                }
                            }

                            val statusMessage = when (updateCheckState) {
                                is UpdateCheckState.Checking -> "Checking Accessible Studio server for a newer build..."
                                is UpdateCheckState.UpToDate -> {
                                    val timeStr = AppUpdateChecker.formatCheckedTime(updateCheckState.checkedAtMs)
                                    if (timeStr.isNotEmpty()) {
                                        "You are up to date (v${updateCheckState.currentVersion}). Checked $timeStr."
                                    } else {
                                        "You are up to date (v${updateCheckState.currentVersion})."
                                    }
                                }
                                is UpdateCheckState.UpdateAvailable -> {
                                    "Update available: v${updateCheckState.latestVersion}. ${updateCheckState.releaseNotes}"
                                }
                                is UpdateCheckState.Error -> updateCheckState.message
                                is UpdateCheckState.Idle -> {
                                    val timeStr = AppUpdateChecker.formatCheckedTime(lastUpdateCheckMs)
                                    if (timeStr.isNotEmpty()) {
                                        "Last checked: $timeStr"
                                    } else if (draftAutoCheckUpdates) {
                                        "Automatic update checks are enabled. Tap Check for Updates anytime."
                                    } else {
                                        "Automatic update checks are off. Tap Check for Updates to check manually."
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = when (updateCheckState) {
                                            is UpdateCheckState.UpdateAvailable -> MaterialTheme.colorScheme.primaryContainer
                                            is UpdateCheckState.Error -> MaterialTheme.colorScheme.errorContainer
                                            else -> if (draftHighContrast) Color.Black else MaterialTheme.colorScheme.surface
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .padding(10.dp)
                                    .testTag("card_about_update_status")
                                    .semantics {
                                        liveRegion = LiveRegionMode.Polite
                                        contentDescription = statusMessage
                                    },
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = statusMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (updateCheckState is UpdateCheckState.UpdateAvailable) FontWeight.Bold else FontWeight.Medium,
                                    color = when (updateCheckState) {
                                        is UpdateCheckState.UpdateAvailable -> MaterialTheme.colorScheme.onPrimaryContainer
                                        is UpdateCheckState.Error -> MaterialTheme.colorScheme.onErrorContainer
                                        else -> if (draftHighContrast) Color.White else MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.testTag("txt_about_update_status")
                                )

                                if (updateCheckState is UpdateCheckState.UpdateAvailable) {
                                    Button(
                                        onClick = { onDownloadUpdate?.invoke(updateCheckState.downloadUrl) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("btn_about_download_update")
                                            .semantics {
                                                contentDescription = "Download Accessible Studio version ${updateCheckState.latestVersion} APK update."
                                            },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Download Update (v${updateCheckState.latestVersion})", fontWeight = FontWeight.Black)
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    if (onShareApkLink != null) {
                                        onShareApkLink()
                                    } else {
                                        val url = AppUpdateChecker.resolveShareableApkUrl(sheetContext, updateCheckState)
                                        val version = (updateCheckState as? UpdateCheckState.UpdateAvailable)?.latestVersion
                                            ?: com.example.BuildConfig.VERSION_NAME
                                        AppUpdateChecker.shareApkDirectLink(sheetContext, url, version)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("btn_about_share_apk_link")
                                    .semantics {
                                        contentDescription = "Share APK Direct Link along with a detailed article about what Accessible Studio does and its special features"
                                    },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Share APK & App Article", fontWeight = FontWeight.Bold)
                            }

                            // Update Source Selector (Hosted Server vs GitHub Releases)
                            if (onSaveGitHubSource != null) {
                                Text(
                                    text = "Update Publish Source",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val isServerSelected = draftUpdateProvider != StudioPreferences.UPDATE_PROVIDER_GITHUB
                                    OutlinedButton(
                                        onClick = {
                                            draftUpdateProvider = StudioPreferences.UPDATE_PROVIDER_AUTO
                                            onSaveGitHubSource(
                                                StudioPreferences.UPDATE_PROVIDER_AUTO,
                                                draftGithubOwner,
                                                draftGithubRepo
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("btn_update_source_server")
                                            .semantics {
                                                contentDescription = "Default App Server update source. ${if (isServerSelected) "Selected" else "Tap to select"}"
                                            },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isServerSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                        ),
                                        border = BorderStroke(
                                            if (isServerSelected) 2.dp else 1.dp,
                                            if (isServerSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                        )
                                    ) {
                                        Text(
                                            text = if (isServerSelected) "✓ App Server" else "App Server",
                                            fontWeight = if (isServerSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }

                                    val isGitHubSelected = draftUpdateProvider == StudioPreferences.UPDATE_PROVIDER_GITHUB
                                    OutlinedButton(
                                        onClick = {
                                            draftUpdateProvider = StudioPreferences.UPDATE_PROVIDER_GITHUB
                                            onSaveGitHubSource(
                                                StudioPreferences.UPDATE_PROVIDER_GITHUB,
                                                draftGithubOwner,
                                                draftGithubRepo
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("btn_update_source_github")
                                            .semantics {
                                                contentDescription = "GitHub Releases update source. ${if (isGitHubSelected) "Selected" else "Tap to select"}"
                                            },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isGitHubSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                                        ),
                                        border = BorderStroke(
                                            if (isGitHubSelected) 2.dp else 1.dp,
                                            if (isGitHubSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
                                        )
                                    ) {
                                        Text(
                                            text = if (isGitHubSelected) "✓ GitHub" else "GitHub",
                                            fontWeight = if (isGitHubSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }

                                if (draftUpdateProvider == StudioPreferences.UPDATE_PROVIDER_GITHUB) {
                                    OutlinedTextField(
                                        value = draftGithubOwner,
                                        onValueChange = { draftGithubOwner = it },
                                        label = { Text("GitHub Owner / Username") },
                                        placeholder = { Text("e.g. octocat") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("input_github_owner")
                                            .semantics {
                                                contentDescription = "GitHub repository owner or username. Current value: ${draftGithubOwner.ifBlank { "empty" }}"
                                            }
                                    )
                                    OutlinedTextField(
                                        value = draftGithubRepo,
                                        onValueChange = { draftGithubRepo = it },
                                        label = { Text("GitHub Repository Name") },
                                        placeholder = { Text("e.g. accessible-studio") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("input_github_repo")
                                            .semantics {
                                                contentDescription = "GitHub repository name. Current value: ${draftGithubRepo.ifBlank { "empty" }}"
                                            }
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            onSaveGitHubSource(
                                                StudioPreferences.UPDATE_PROVIDER_GITHUB,
                                                draftGithubOwner,
                                                draftGithubRepo
                                            )
                                            onCheckForUpdates?.invoke()
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("btn_save_github_source")
                                            .semantics {
                                                contentDescription = "Save GitHub repository and check GitHub Releases for updates"
                                            },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Save & Check GitHub Releases", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

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
                                contentDescription = "What's New. Tap to view recent updates and fixes in Version ${com.example.BuildConfig.VERSION_NAME}."
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
