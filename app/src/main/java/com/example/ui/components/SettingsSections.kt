package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.audio.AndroidRecordingCapabilityProbe
import com.example.audio.RecordingCapabilityProbe
import com.example.audio.RecordingConfigValidator
import com.example.data.RecordingAudioSource
import com.example.data.RecordingChannel
import com.example.data.RecordingFormat
import com.example.data.RecordingQuality
import com.example.data.StudioPreferences
import com.example.data.StudioSettingsState
import kotlin.math.roundToInt

@Composable
fun DisplayVisualControlsCard(
    isHighContrast: Boolean,
    visualModeEnabled: Boolean,
    showZoomControls: Boolean,
    showWaveformDisplay: Boolean,
    onVisualModeChange: (Boolean) -> Unit,
    onZoomControlsChange: (Boolean) -> Unit,
    onWaveformDisplayChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_display_visual_controls"),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighContrast) Color(0xFF161616) else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(
            if (isHighContrast) 2.dp else 1.dp,
            if (isHighContrast) Color.White else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "DISPLAY & VISUAL CONTROLS",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() }
            )

            SettingsToggleRow(
                title = "Visual Mode",
                subtitle = "Enable visual workspace canvases and display modes; turn off for audio-first focus",
                icon = Icons.Default.Visibility,
                checked = visualModeEnabled,
                isHighContrast = isHighContrast,
                testTag = "switch_visual_mode",
                onCheckedChange = onVisualModeChange
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            SettingsToggleRow(
                title = "Zoom In / Zoom Out Controls",
                subtitle = "Show Zoom +, Zoom -, and Fit timeline controls in editor workspaces",
                icon = Icons.Default.ZoomIn,
                checked = showZoomControls,
                isHighContrast = isHighContrast,
                testTag = "switch_zoom_controls",
                onCheckedChange = onZoomControlsChange
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            SettingsToggleRow(
                title = "Waveform Display",
                subtitle = "Show interactive audio waveform view in Trim and Audio Studio screens",
                icon = Icons.Default.GraphicEq,
                checked = showWaveformDisplay,
                isHighContrast = isHighContrast,
                testTag = "switch_waveform_display",
                onCheckedChange = onWaveformDisplayChange
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    isHighContrast: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    val stateText = if (checked) "On" else "Off"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(
                role = Role.Switch,
                onClick = { onCheckedChange(!checked) }
            )
            .semantics(mergeDescendants = true) {
                stateDescription = stateText
                contentDescription = "$title. $subtitle. Currently $stateText. Double tap to toggle."
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier
                .testTag(testTag)
                .semantics {
                    stateDescription = stateText
                    contentDescription = "$title switch"
                },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = MaterialTheme.colorScheme.secondary
            )
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordingSettingsCard(
    settings: StudioSettingsState,
    isHighContrast: Boolean,
    capabilityProbe: RecordingCapabilityProbe = AndroidRecordingCapabilityProbe,
    onUpdateSettings: (StudioSettingsState) -> Unit,
    onResetRecordingDefaults: () -> Unit
) {
    val resolvedConfig = remember(settings, capabilityProbe) {
        RecordingConfigValidator.resolve(settings, capabilityProbe)
    }
    val formatOptions = remember(capabilityProbe) {
        buildList {
            add(RecordingFormat.AUTO)
            add(RecordingFormat.WAV)
            add(RecordingFormat.M4A)
            add(RecordingFormat.MP3)
            if (capabilityProbe.isFormatSupported(RecordingFormat.OGG)) {
                add(RecordingFormat.OGG)
            }
            if (capabilityProbe.isFormatSupported(RecordingFormat.FLAC)) {
                add(RecordingFormat.FLAC)
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_recording_settings"),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighContrast) Color(0xFF161616) else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(
            if (isHighContrast) 2.dp else 1.dp,
            if (isHighContrast) Color.White else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Column {
                    Text(
                        text = "RECORDING SETTINGS",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "Applied immediately to Voice Studio & Punch-In recordings",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 1. Recording Quality
            SelectorGroupHeader(
                title = "Recording Quality",
                subtitle = settings.recordingQuality.description,
                isHighContrast = isHighContrast
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RecordingQuality.entries.forEach { quality ->
                    val selected = settings.recordingQuality == quality
                    AccessibleSettingChip(
                        label = quality.label,
                        selected = selected,
                        isHighContrast = isHighContrast,
                        testTag = "chip_rec_quality_${quality.name.lowercase()}",
                        spokenLabel = "Recording Quality ${quality.label}. ${quality.description}",
                        onClick = { onUpdateSettings(settings.copy(recordingQuality = quality)) }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            // 2. Recording Format
            SelectorGroupHeader(
                title = "Recording Format",
                subtitle = if (settings.recordingFormat == RecordingFormat.AUTO) {
                    "Auto selects lossless ${resolvedConfig.effectiveFormat.label} for clean editing"
                } else {
                    "Selected: ${settings.recordingFormat.label} (Effective: ${resolvedConfig.effectiveFormat.label})"
                },
                isHighContrast = isHighContrast
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                formatOptions.forEach { format ->
                    val selected = settings.recordingFormat == format
                    AccessibleSettingChip(
                        label = format.label,
                        selected = selected,
                        isHighContrast = isHighContrast,
                        testTag = "chip_rec_format_${format.name.lowercase()}",
                        spokenLabel = "Recording Format ${format.label}",
                        onClick = { onUpdateSettings(settings.copy(recordingFormat = format)) }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            // 3. Sample Rate
            SelectorGroupHeader(
                title = "Sample Rate",
                subtitle = "Configured: ${StudioPreferences.formatSampleRateLabel(settings.recordingSampleRateHz)} • Active: ${resolvedConfig.sampleRateHz} Hz",
                isHighContrast = isHighContrast
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StudioPreferences.SUPPORTED_SAMPLE_RATE_OPTIONS.forEach { rateHz ->
                    val selected = settings.recordingSampleRateHz == rateHz
                    val label = StudioPreferences.formatSampleRateLabel(rateHz)
                    AccessibleSettingChip(
                        label = label,
                        selected = selected,
                        isHighContrast = isHighContrast,
                        testTag = "chip_rec_rate_$rateHz",
                        spokenLabel = "Recording Sample Rate $label",
                        onClick = { onUpdateSettings(settings.copy(recordingSampleRateHz = rateHz)) }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            // 4. Recording Channel
            SelectorGroupHeader(
                title = "Recording Channel",
                subtitle = "Active hardware input: ${if (resolvedConfig.channelCount >= 2) "Stereo (2 ch)" else "Mono (1 ch)"}",
                isHighContrast = isHighContrast
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RecordingChannel.entries.forEach { channel ->
                    val selected = settings.recordingChannel == channel
                    AccessibleSettingChip(
                        label = channel.label,
                        selected = selected,
                        isHighContrast = isHighContrast,
                        testTag = "chip_rec_channel_${channel.name.lowercase()}",
                        spokenLabel = "Recording Channel ${channel.label}",
                        onClick = { onUpdateSettings(settings.copy(recordingChannel = channel)) }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            // 5. Audio Bitrate
            SelectorGroupHeader(
                title = "Audio Bitrate",
                subtitle = if (resolvedConfig.effectiveFormat.isLossless) {
                    "Lossless ${resolvedConfig.effectiveFormat.label} selected; compressed bitrate (${resolvedConfig.bitrateKbps} kbps) applies to M4A/MP3/OGG"
                } else {
                    "Target bitrate: ${resolvedConfig.bitrateKbps} kbps (${StudioPreferences.formatBitrateLabel(settings.recordingBitrateKbps)})"
                },
                isHighContrast = isHighContrast
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StudioPreferences.SUPPORTED_BITRATE_OPTIONS.forEach { kbps ->
                    val selected = settings.recordingBitrateKbps == kbps
                    val label = StudioPreferences.formatBitrateLabel(kbps)
                    AccessibleSettingChip(
                        label = label,
                        selected = selected,
                        isHighContrast = isHighContrast,
                        testTag = "chip_rec_bitrate_$kbps",
                        spokenLabel = "Audio Bitrate $label",
                        onClick = { onUpdateSettings(settings.copy(recordingBitrateKbps = kbps)) }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            // 6. Audio Source / Input
            SelectorGroupHeader(
                title = "Audio Source / Input",
                subtitle = "Active hardware source: ${resolvedConfig.audioSource.label} (with safe fallback)",
                isHighContrast = isHighContrast
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RecordingAudioSource.entries.forEach { source ->
                    val selected = settings.recordingAudioSource == source
                    AccessibleSettingChip(
                        label = source.label,
                        selected = selected,
                        isHighContrast = isHighContrast,
                        testTag = "chip_rec_source_${source.name.lowercase()}",
                        spokenLabel = "Audio Source ${source.label}",
                        onClick = { onUpdateSettings(settings.copy(recordingAudioSource = source)) }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            // 7. Auto Gain / Input Boost
            SettingsToggleRow(
                title = "Auto Gain / Anti-Crackle",
                subtitle = "Automatically levels quiet speech and protects peaks against clipping",
                icon = Icons.Default.Mic,
                checked = settings.recordingAutoGainEnabled,
                isHighContrast = isHighContrast,
                testTag = "switch_rec_auto_gain",
                onCheckedChange = { enabled ->
                    onUpdateSettings(settings.copy(recordingAutoGainEnabled = enabled))
                }
            )

            val gainPct = (settings.recordingInputGain * 100f).roundToInt()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Input Gain / Trim Boost",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$gainPct%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = settings.recordingInputGain,
                    onValueChange = { raw ->
                        val quantized = ((raw * 20f).roundToInt() / 20f)
                            .coerceIn(StudioPreferences.MIN_INPUT_GAIN, StudioPreferences.MAX_INPUT_GAIN)
                        onUpdateSettings(settings.copy(recordingInputGain = quantized))
                    },
                    valueRange = StudioPreferences.MIN_INPUT_GAIN..StudioPreferences.MAX_INPUT_GAIN,
                    steps = 34,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("slider_rec_input_gain")
                        .semantics {
                            stateDescription = "$gainPct percent"
                            contentDescription = "Input Gain / Trim Boost $gainPct percent"
                        },
                    colors = SliderDefaults.colors(
                        thumbColor = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        activeTrackColor = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    )
                )
            }

            // Active Validated Summary + Fallback Notes
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(12.dp)
                    .testTag("txt_recording_validation_summary")
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = "Validated recording profile: ${resolvedConfig.summaryLabel()}. " +
                            resolvedConfig.fallbackNotes.joinToString(" ")
                    },
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Active Profile: ${resolvedConfig.summaryLabel()}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                )
                if (resolvedConfig.fallbackNotes.isNotEmpty()) {
                    resolvedConfig.fallbackNotes.forEach { note ->
                        Text(
                            text = "• $note",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // 8. Reset Recording Defaults Action
            OutlinedButton(
                onClick = onResetRecordingDefaults,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("btn_settings_reset_recording")
                    .semantics {
                        contentDescription = "Reset Recording Defaults. Restores High quality, Auto format, Auto sample rate, Auto channel, Auto bitrate, Default audio source, Auto Gain On, and 100% input gain."
                    },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reset Recording Defaults", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SelectorGroupHeader(
    title: String,
    subtitle: String,
    isHighContrast: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AccessibleSettingChip(
    label: String,
    selected: Boolean,
    isHighContrast: Boolean,
    testTag: String,
    spokenLabel: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = if (selected) "✓ $label" else label,
                fontWeight = if (selected) FontWeight.Black else FontWeight.Medium
            )
        },
        modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag(testTag)
            .semantics {
                stateDescription = if (selected) "Selected" else "Not selected"
                contentDescription = "$spokenLabel. ${if (selected) "Selected" else "Tap to select"}"
            },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}
