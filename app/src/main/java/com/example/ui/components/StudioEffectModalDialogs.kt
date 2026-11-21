package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.accessibility.AccessibilityHelper
import com.example.audio.CuePoint
import com.example.audio.EqPreset
import com.example.audio.StudioFxSettings
import com.example.audio.VocalIsolationPreset
import com.example.ui.theme.LocalHighContrast

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioEffectsRackDialog(
    selectedTabIndex: Int,
    onSelectTabIndex: (Int) -> Unit,
    echoEnabled: Boolean,
    onEchoEnabledChange: (Boolean) -> Unit,
    echoDelayMs: Long,
    onEchoDelayChange: (Long) -> Unit,
    echoFeedback: Float,
    onEchoFeedbackChange: (Float) -> Unit,
    echoMix: Float,
    onEchoMixChange: (Float) -> Unit,
    reverbEnabled: Boolean,
    onReverbEnabledChange: (Boolean) -> Unit,
    reverbRoomSize: Float,
    onReverbRoomSizeChange: (Float) -> Unit,
    reverbDamping: Float,
    onReverbDampingChange: (Float) -> Unit,
    reverbMix: Float,
    onReverbMixChange: (Float) -> Unit,
    noiseReductionEnabled: Boolean,
    onNoiseReductionEnabledChange: (Boolean) -> Unit,
    noiseReductionIntensity: Float,
    onNoiseReductionIntensityChange: (Float) -> Unit,
    noiseGateEnabled: Boolean,
    onNoiseGateEnabledChange: (Boolean) -> Unit,
    noiseGateThreshold: Float,
    onNoiseGateThresholdChange: (Float) -> Unit,
    bassBoost: Float,
    onBassBoostChange: (Float) -> Unit,
    trebleBoost: Float,
    onTrebleBoostChange: (Float) -> Unit,
    selectedEq: EqPreset,
    onSelectEq: (EqPreset) -> Unit,
    speedMultiplier: Float,
    onSpeedMultiplierChange: (Float) -> Unit,
    isReversed: Boolean,
    onReversedChange: (Boolean) -> Unit,
    studioFxSettings: StudioFxSettings,
    onStudioFxSettingsChange: (StudioFxSettings) -> Unit,
    onAudition: () -> Unit,
    onApplyDeClick: () -> Unit,
    onApplyVocalIsolation: (
        preset: VocalIsolationPreset,
        subtractionStrength: Float,
        humRemovalStrength: Float,
        vocalClarityBoost: Float
    ) -> Unit = { _, _, _, _ -> },
    noisePrintStatus: String? = null,
    onCaptureNoisePrint: (() -> Unit)? = null,
    onClearNoisePrint: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val isHighContrast = LocalHighContrast.current
    val effectTabs = listOf("Echo & Reverb", "Noise Reduction", "Tone & EQ", "Speed & Reverse", "Studio Pro Rack")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BackHandler { onDismiss() }
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 650.dp)
                .testTag("dialog_effects_studio"),
            shape = RoundedCornerShape(20.dp),
            color = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.background,
            border = if (isHighContrast) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = effectTabs[selectedTabIndex.coerceIn(0, 4)],
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Offline DSP Studio",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = onAudition,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("btn_audition_effects")
                            .semantics { contentDescription = "Audition and apply effects live" }
                    ) {
                        Icon(Icons.Default.Hearing, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Audition", fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.width(4.dp))

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp).testTag("btn_close_effects_dialog")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Effects Studio")
                    }
                }

                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex.coerceIn(0, 4),
                    edgePadding = 12.dp,
                    containerColor = if (isHighContrast) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(10.dp))
                ) {
                    effectTabs.forEachIndexed { idx, title ->
                        Tab(
                            selected = selectedTabIndex == idx,
                            onClick = { onSelectTabIndex(idx) },
                            text = { Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (selectedTabIndex.coerceIn(0, 4)) {
                        0 -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = if (isHighContrast) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onEchoEnabledChange(!echoEnabled) },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Echo (Delay)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                        Switch(
                                            checked = echoEnabled,
                                            onCheckedChange = onEchoEnabledChange,
                                            modifier = Modifier.testTag("switch_echo_enabled")
                                        )
                                    }
                                    AnimatedVisibility(visible = echoEnabled) {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            EffectPresetDropdown(
                                                effectId = "echo",
                                                effectTitle = "Echo",
                                                currentValues = EchoParams(echoDelayMs, echoFeedback, echoMix),
                                                presets = EffectPanelPresetsCatalog.echoPresets,
                                                defaultOption = EffectPanelPresetsCatalog.echoDefault,
                                                valuesEqual = { a, b ->
                                                    a.delayMs == b.delayMs &&
                                                        EffectPresetResolver.approxEqual(a.feedback, b.feedback) &&
                                                        EffectPresetResolver.approxEqual(a.mix, b.mix)
                                                },
                                                onApplyPreset = { preset ->
                                                    onEchoDelayChange(preset.delayMs)
                                                    onEchoFeedbackChange(preset.feedback)
                                                    onEchoMixChange(preset.mix)
                                                }
                                            )
                                            AccessibleEffectSlider(
                                                title = "Echo Delay Time",
                                                value = echoDelayMs.toFloat(),
                                                onValueChange = { onEchoDelayChange(it.toLong()) },
                                                valueRange = 50f..800f,
                                                step = 25f,
                                                displayValue = "$echoDelayMs ms",
                                                spokenDescription = "$echoDelayMs milliseconds delay",
                                                testTag = "slider_echo_delay"
                                            )
                                            AccessibleEffectSlider(
                                                title = "Echo Feedback Repeat",
                                                value = echoFeedback,
                                                onValueChange = onEchoFeedbackChange,
                                                valueRange = 0.1f..0.85f,
                                                step = 0.05f,
                                                displayValue = "${(echoFeedback * 100).toInt()}%",
                                                spokenDescription = "${(echoFeedback * 100).toInt()} percent feedback",
                                                testTag = "slider_echo_feedback"
                                            )
                                            AccessibleEffectSlider(
                                                title = "Echo Wet Mix",
                                                value = echoMix,
                                                onValueChange = onEchoMixChange,
                                                valueRange = 0.1f..0.9f,
                                                step = 0.05f,
                                                displayValue = "${(echoMix * 100).toInt()}%",
                                                spokenDescription = "${(echoMix * 100).toInt()} percent wet mix",
                                                testTag = "slider_echo_mix"
                                            )
                                        }
                                    }
                                }
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = if (isHighContrast) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onReverbEnabledChange(!reverbEnabled) },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Reverb (Acoustic Space)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                        Switch(
                                            checked = reverbEnabled,
                                            onCheckedChange = onReverbEnabledChange,
                                            modifier = Modifier.testTag("switch_reverb_enabled")
                                        )
                                    }
                                    AnimatedVisibility(visible = reverbEnabled) {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            EffectPresetDropdown(
                                                effectId = "reverb",
                                                effectTitle = "Reverb",
                                                currentValues = ReverbParams(reverbRoomSize, reverbDamping, reverbMix),
                                                presets = EffectPanelPresetsCatalog.reverbPresets,
                                                defaultOption = EffectPanelPresetsCatalog.reverbDefault,
                                                valuesEqual = { a, b ->
                                                    EffectPresetResolver.approxEqual(a.roomSize, b.roomSize) &&
                                                        EffectPresetResolver.approxEqual(a.damping, b.damping) &&
                                                        EffectPresetResolver.approxEqual(a.mix, b.mix)
                                                },
                                                onApplyPreset = { preset ->
                                                    onReverbRoomSizeChange(preset.roomSize)
                                                    onReverbDampingChange(preset.damping)
                                                    onReverbMixChange(preset.mix)
                                                }
                                            )
                                            AccessibleEffectSlider(
                                                title = "Room Size / Space",
                                                value = reverbRoomSize,
                                                onValueChange = onReverbRoomSizeChange,
                                                valueRange = 0.1f..0.95f,
                                                step = 0.05f,
                                                displayValue = "${(reverbRoomSize * 100).toInt()}%",
                                                spokenDescription = "${(reverbRoomSize * 100).toInt()} percent room size",
                                                testTag = "slider_reverb_room"
                                            )
                                            AccessibleEffectSlider(
                                                title = "Reverb Damping",
                                                value = reverbDamping,
                                                onValueChange = onReverbDampingChange,
                                                valueRange = 0.1f..0.8f,
                                                step = 0.05f,
                                                displayValue = "${(reverbDamping * 100).toInt()}%",
                                                spokenDescription = "${(reverbDamping * 100).toInt()} percent damping",
                                                testTag = "slider_reverb_damping"
                                            )
                                            AccessibleEffectSlider(
                                                title = "Reverb Wet Mix",
                                                value = reverbMix,
                                                onValueChange = onReverbMixChange,
                                                valueRange = 0.1f..0.9f,
                                                step = 0.05f,
                                                displayValue = "${(reverbMix * 100).toInt()}%",
                                                spokenDescription = "${(reverbMix * 100).toInt()} percent reverb mix",
                                                testTag = "slider_reverb_mix"
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = if (isHighContrast) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onNoiseReductionEnabledChange(!noiseReductionEnabled) },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Smooth Noise Reduction", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                            Text("Soft suppression without robotic distortion", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(
                                            checked = noiseReductionEnabled,
                                            onCheckedChange = onNoiseReductionEnabledChange,
                                            modifier = Modifier.testTag("switch_noise_reduction_enabled")
                                        )
                                    }

                                    AnimatedVisibility(visible = noiseReductionEnabled) {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            EffectPresetDropdown(
                                                effectId = "noise_reduction",
                                                effectTitle = "Noise Reduction",
                                                currentValues = noiseReductionIntensity,
                                                presets = EffectPanelPresetsCatalog.noiseReductionPresets,
                                                defaultOption = EffectPanelPresetsCatalog.noiseReductionDefault,
                                                valuesEqual = { a, b -> EffectPresetResolver.approxEqual(a, b) },
                                                onApplyPreset = { preset -> onNoiseReductionIntensityChange(preset) }
                                            )
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                listOf(
                                                    "Low" to 0.35f,
                                                    "Medium" to 0.60f,
                                                    "High" to 0.80f,
                                                    "Max Fan/Motor" to 1.00f
                                                ).forEach { (label, valFloat) ->
                                                    FilterChip(
                                                        selected = kotlin.math.abs(noiseReductionIntensity - valFloat) < 0.08f,
                                                        onClick = { onNoiseReductionIntensityChange(valFloat) },
                                                        label = { Text(label, fontSize = 11.sp) }
                                                    )
                                                }
                                            }
                                            AccessibleEffectSlider(
                                                title = "Noise Suppression Level",
                                                value = noiseReductionIntensity,
                                                onValueChange = onNoiseReductionIntensityChange,
                                                valueRange = 0.1f..1.0f,
                                                step = 0.05f,
                                                displayValue = "${(noiseReductionIntensity * 100).toInt()}%",
                                                spokenDescription = "${(noiseReductionIntensity * 100).toInt()} percent reduction",
                                                testTag = "slider_noise_reduction"
                                            )
                                        }
                                    }

                                    var selectedIsolationPreset by remember { mutableStateOf(VocalIsolationPreset.MEDIUM) }
                                    var isolationSubtraction by remember { mutableFloatStateOf(VocalIsolationPreset.MEDIUM.subtractionStrength) }
                                    var isolationHum by remember { mutableFloatStateOf(VocalIsolationPreset.MEDIUM.humRemovalStrength) }
                                    var isolationClarity by remember { mutableFloatStateOf(VocalIsolationPreset.MEDIUM.vocalClarityBoost) }

                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("section_noise_reduction_vocal_isolation"),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Isolate Spoken Audio (Adaptive Spectral Subtraction)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Black,
                                            color = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Removes ceiling/table fans, motors, AC, ambient noise & hum offline with broadcast clarity.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(
                                                VocalIsolationPreset.LIGHT to "btn_isolation_preset_light",
                                                VocalIsolationPreset.MEDIUM to "btn_isolation_preset_medium",
                                                VocalIsolationPreset.STRONG to "btn_isolation_preset_strong",
                                                VocalIsolationPreset.STUDIO_DEEP_CLEAN to "btn_isolation_preset_studio_ultra"
                                            ).forEach { (preset, tag) ->
                                                FilterChip(
                                                    selected = selectedIsolationPreset == preset,
                                                    onClick = {
                                                        selectedIsolationPreset = preset
                                                        isolationSubtraction = preset.subtractionStrength
                                                        isolationHum = preset.humRemovalStrength
                                                        isolationClarity = preset.vocalClarityBoost
                                                    },
                                                    label = { Text(preset.displayName, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                                    modifier = Modifier
                                                        .heightIn(min = 48.dp)
                                                        .testTag(tag)
                                                        .semantics {
                                                            contentDescription = "${preset.displayName}. ${preset.shortDescription}"
                                                        }
                                                )
                                            }
                                        }
                                        if (onCaptureNoisePrint != null) {
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = onCaptureNoisePrint,
                                                    modifier = Modifier
                                                        .heightIn(min = 48.dp)
                                                        .testTag("btn_rack_capture_noise_print")
                                                ) {
                                                    Text("Learn Noise Print (Selection)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                }
                                                if (noisePrintStatus != null && onClearNoisePrint != null) {
                                                    OutlinedButton(
                                                        onClick = onClearNoisePrint,
                                                        modifier = Modifier
                                                            .heightIn(min = 48.dp)
                                                            .testTag("btn_rack_clear_noise_print")
                                                    ) {
                                                        Text("Clear Print", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    }
                                                }
                                            }
                                            if (noisePrintStatus != null) {
                                                Text(
                                                    text = noisePrintStatus,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isHighContrast) Color(0xFF1F1F1F) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = selectedIsolationPreset.shortDescription,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(10.dp)
                                            )
                                        }

                                        AccessibleEffectSlider(
                                            title = "Adaptive Spectral Subtraction",
                                            value = isolationSubtraction,
                                            onValueChange = { isolationSubtraction = it },
                                            valueRange = 0.1f..1.0f,
                                            step = 0.05f,
                                            displayValue = "${(isolationSubtraction * 100).toInt()}%",
                                            spokenDescription = "Adaptive spectral subtraction ${(isolationSubtraction * 100).toInt()} percent",
                                            testTag = "slider_isolation_subtraction"
                                        )
                                        AccessibleEffectSlider(
                                            title = "50/60 Hz Hum & Harmonic Suppression",
                                            value = isolationHum,
                                            onValueChange = { isolationHum = it },
                                            valueRange = 0f..1.0f,
                                            step = 0.05f,
                                            displayValue = "${(isolationHum * 100).toInt()}%",
                                            spokenDescription = "Hum suppression ${(isolationHum * 100).toInt()} percent",
                                            testTag = "slider_isolation_hum"
                                        )
                                        AccessibleEffectSlider(
                                            title = "Vocal Clarity Preservation",
                                            value = isolationClarity,
                                            onValueChange = { isolationClarity = it },
                                            valueRange = 0f..1.0f,
                                            step = 0.05f,
                                            displayValue = "${(isolationClarity * 100).toInt()}%",
                                            spokenDescription = "Vocal clarity boost ${(isolationClarity * 100).toInt()} percent",
                                            testTag = "slider_isolation_clarity"
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = onApplyDeClick,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .testTag("btn_apply_declick_filter"),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
                                            ) {
                                                Icon(Icons.Default.Waves, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Apply De-Click", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                            Button(
                                                onClick = {
                                                    onApplyVocalIsolation(
                                                        selectedIsolationPreset,
                                                        isolationSubtraction,
                                                        isolationHum,
                                                        isolationClarity
                                                    )
                                                },
                                                modifier = Modifier
                                                    .weight(1.25f)
                                                    .height(48.dp)
                                                    .testTag("btn_confirm_vocal_isolation")
                                                    .semantics {
                                                        contentDescription = "Apply ${selectedIsolationPreset.displayName} adaptive spectral vocal isolation inside Noise Reduction"
                                                    }
                                            ) {
                                                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Isolate Speech", fontWeight = FontWeight.Black, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = if (isHighContrast) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onNoiseGateEnabledChange(!noiseGateEnabled) },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Noise Gate", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                            Text("Silences room noise during pauses", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(
                                            checked = noiseGateEnabled,
                                            onCheckedChange = onNoiseGateEnabledChange,
                                            modifier = Modifier.testTag("switch_noise_gate_enabled")
                                        )
                                    }
                                    AnimatedVisibility(visible = noiseGateEnabled) {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            EffectPresetDropdown(
                                                effectId = "noise_gate",
                                                effectTitle = "Noise Gate",
                                                currentValues = noiseGateThreshold,
                                                presets = EffectPanelPresetsCatalog.noiseGatePresets,
                                                defaultOption = EffectPanelPresetsCatalog.noiseGateDefault,
                                                valuesEqual = { a, b -> EffectPresetResolver.approxEqual(a, b, 0.008f) },
                                                onApplyPreset = { preset -> onNoiseGateThresholdChange(preset) }
                                            )
                                            AccessibleEffectSlider(
                                                title = "Gate Cutoff Threshold",
                                                value = noiseGateThreshold,
                                                onValueChange = onNoiseGateThresholdChange,
                                                valueRange = 0.02f..0.25f,
                                                step = 0.01f,
                                                displayValue = "${(noiseGateThreshold * 100).toInt()}%",
                                                spokenDescription = "${(noiseGateThreshold * 100).toInt()} percent threshold",
                                                testTag = "slider_noise_gate"
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        2 -> {
                            var toneExpanded by remember { mutableStateOf(true) }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = if (isHighContrast) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { toneExpanded = !toneExpanded },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Equalizer & Tone Shaping", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                            Text("Bass boost, treble boost & voice curves", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(
                                            checked = toneExpanded,
                                            onCheckedChange = { toneExpanded = it },
                                            modifier = Modifier.testTag("switch_tone_eq_expanded")
                                        )
                                    }
                                    AnimatedVisibility(visible = toneExpanded) {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            EffectPresetDropdown(
                                                effectId = "tone_eq",
                                                effectTitle = "Equalizer",
                                                currentValues = ToneEqParams(bassBoost, trebleBoost, selectedEq),
                                                presets = EffectPanelPresetsCatalog.toneEqPresets,
                                                defaultOption = EffectPanelPresetsCatalog.toneEqDefault,
                                                valuesEqual = { a, b ->
                                                    a.eqPreset == b.eqPreset &&
                                                        EffectPresetResolver.approxEqual(a.bassBoost, b.bassBoost) &&
                                                        EffectPresetResolver.approxEqual(a.trebleBoost, b.trebleBoost)
                                                },
                                                onApplyPreset = { preset ->
                                                    onBassBoostChange(preset.bassBoost)
                                                    onTrebleBoostChange(preset.trebleBoost)
                                                    onSelectEq(preset.eqPreset)
                                                }
                                            )
                                            AccessibleEffectSlider(
                                                title = "Warm Bass Boost",
                                                value = bassBoost,
                                                onValueChange = onBassBoostChange,
                                                valueRange = 0f..1.0f,
                                                step = 0.05f,
                                                displayValue = "${(bassBoost * 100).toInt()}%",
                                                spokenDescription = "${(bassBoost * 100).toInt()} percent bass boost",
                                                testTag = "slider_bass_boost"
                                            )
                                            AccessibleEffectSlider(
                                                title = "Crisp Treble Boost",
                                                value = trebleBoost,
                                                onValueChange = onTrebleBoostChange,
                                                valueRange = 0f..1.0f,
                                                step = 0.05f,
                                                displayValue = "${(trebleBoost * 100).toInt()}%",
                                                spokenDescription = "${(trebleBoost * 100).toInt()} percent treble boost",
                                                testTag = "slider_treble_boost"
                                            )
                                            Text("Equalizer Profile:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                EqPreset.values().forEach { eq ->
                                                    FilterChip(
                                                        selected = selectedEq == eq,
                                                        onClick = { onSelectEq(eq) },
                                                        label = { Text(eq.displayName) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        3 -> {
                            var speedExpanded by remember { mutableStateOf(true) }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = if (isHighContrast) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { speedExpanded = !speedExpanded },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Playback Speed & Reverse", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                            Text("Tempo multiplier and backward playback", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(
                                            checked = speedExpanded,
                                            onCheckedChange = { speedExpanded = it },
                                            modifier = Modifier.testTag("switch_speed_expanded")
                                        )
                                    }
                                    AnimatedVisibility(visible = speedExpanded) {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            EffectPresetDropdown(
                                                effectId = "speed_reverse",
                                                effectTitle = "Speed & Reverse",
                                                currentValues = SpeedReverseParams(speedMultiplier, isReversed),
                                                presets = EffectPanelPresetsCatalog.speedReversePresets,
                                                defaultOption = EffectPanelPresetsCatalog.speedReverseDefault,
                                                valuesEqual = { a, b ->
                                                    a.isReversed == b.isReversed &&
                                                        EffectPresetResolver.approxEqual(a.speedMultiplier, b.speedMultiplier)
                                                },
                                                onApplyPreset = { preset ->
                                                    onSpeedMultiplierChange(preset.speedMultiplier)
                                                    onReversedChange(preset.isReversed)
                                                }
                                            )
                                            AccessibleEffectSlider(
                                                title = "Playback Speed Multiplier",
                                                value = speedMultiplier,
                                                onValueChange = onSpeedMultiplierChange,
                                                valueRange = 0.5f..2.0f,
                                                step = 0.05f,
                                                displayValue = "${"%.2f".format(speedMultiplier)}x",
                                                spokenDescription = "Playback speed ${"%.2f".format(speedMultiplier)} times",
                                                testTag = "slider_speed_multiplier"
                                            )
                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                                                    FilterChip(
                                                        selected = kotlin.math.abs(speedMultiplier - spd) < 0.02f,
                                                        onClick = { onSpeedMultiplierChange(spd) },
                                                        label = { Text("${spd}x") }
                                                    )
                                                }
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text("Reverse Audio", fontWeight = FontWeight.Bold)
                                                    Text("Play sound samples backwards", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                Switch(
                                                    checked = isReversed,
                                                    onCheckedChange = onReversedChange,
                                                    modifier = Modifier.testTag("switch_reverse_audio")
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        4 -> {
                            StudioFxRackSection(
                                settings = studioFxSettings,
                                onSettingsChanged = onStudioFxSettingsChange
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.height(48.dp).testTag("btn_done_effects_dialog")
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

enum class VoiceOverSourceChoice {
    ADD_AUDIO_AS_MUSIC,
    ADD_AUDIO_AS_CLIP,
    CLIPBOARD_CLIP
}

data class VoiceOverApplyConfig(
    val sourceChoice: VoiceOverSourceChoice,
    val preset: com.example.audio.AudioEffectsProEngine.VoiceOverPreset,
    val autoDuckEnabled: Boolean,
    val duckFactor: Float,
    val voiceGain: Float,
    val musicVolume: Float,
    val loopMusic: Boolean,
    val syncOffsetMs: Long = 0L
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioVoiceOverDialog(
    hasClipboard: Boolean,
    trimStartMs: Long,
    trimEndMs: Long,
    currentPositionMs: Long,
    pickedAudioTitle: String? = null,
    pickedAudioDurationMs: Long = 0L,
    isDecodingAudio: Boolean = false,
    onPickAudioFile: () -> Unit = {},
    onClearPickedAudio: () -> Unit = {},
    onApplyVoiceOver: (VoiceOverApplyConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val hasPickedAudio = !pickedAudioTitle.isNullOrBlank()
    var selectedPreset by remember { mutableStateOf(com.example.audio.AudioEffectsProEngine.VoiceOverPreset.TUTORIAL) }
    var sourceChoice by remember(hasPickedAudio, hasClipboard) {
        mutableStateOf(
            when {
                hasPickedAudio -> VoiceOverSourceChoice.ADD_AUDIO_AS_MUSIC
                hasClipboard -> VoiceOverSourceChoice.CLIPBOARD_CLIP
                else -> VoiceOverSourceChoice.ADD_AUDIO_AS_MUSIC
            }
        )
    }
    var autoDuckEnabled by remember { mutableStateOf(selectedPreset.autoDuckEnabled) }
    var duckFactor by remember { mutableFloatStateOf(selectedPreset.duckFactor) }
    var voiceGain by remember { mutableFloatStateOf(selectedPreset.voiceGain) }
    var musicVolume by remember { mutableFloatStateOf(selectedPreset.musicVolume) }
    var loopMusic by remember { mutableStateOf(true) }
    var syncOffsetMs by remember { mutableStateOf(0L) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Voice Over & Auto-Ducking", fontWeight = FontWeight.Black)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Add Audio / Music File or Use Clipboard
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Audio Source", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)

                        Button(
                            onClick = onPickAudioFile,
                            enabled = !isDecodingAudio,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_vo_add_audio")
                                .semantics {
                                    contentDescription = "Add Audio or Music File from your device for Voice Over"
                                },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Waves, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = when {
                                    isDecodingAudio -> "Loading Audio File..."
                                    hasPickedAudio -> "Change Audio / Music File"
                                    else -> "Add Audio / Music File"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (hasPickedAudio) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("vo_picked_file_badge"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pickedAudioTitle.orEmpty(),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Duration: ${AccessibilityHelper.formatTimeVisual(pickedAudioDurationMs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = onClearPickedAudio,
                                    modifier = Modifier.size(48.dp).testTag("btn_vo_clear_picked")
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove picked audio file")
                                }
                            }

                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = sourceChoice == VoiceOverSourceChoice.ADD_AUDIO_AS_MUSIC,
                                    onClick = { sourceChoice = VoiceOverSourceChoice.ADD_AUDIO_AS_MUSIC },
                                    label = { Text("As Background Music") },
                                    modifier = Modifier.testTag("chip_vo_role_music")
                                )
                                FilterChip(
                                    selected = sourceChoice == VoiceOverSourceChoice.ADD_AUDIO_AS_CLIP,
                                    onClick = { sourceChoice = VoiceOverSourceChoice.ADD_AUDIO_AS_CLIP },
                                    label = { Text("Insert Voice Clip") },
                                    modifier = Modifier.testTag("chip_vo_role_clip")
                                )
                            }
                        }

                        // Clipboard source button preserved as requested
                        OutlinedButton(
                            onClick = { sourceChoice = VoiceOverSourceChoice.CLIPBOARD_CLIP },
                            enabled = hasClipboard,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_vo_use_clipboard"),
                            shape = RoundedCornerShape(12.dp),
                            border = if (sourceChoice == VoiceOverSourceChoice.CLIPBOARD_CLIP && hasClipboard) {
                                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                            } else null
                        ) {
                            Text(
                                text = if (hasClipboard) {
                                    if (sourceChoice == VoiceOverSourceChoice.CLIPBOARD_CLIP) {
                                        "Using Clipboard Audio (Selected)"
                                    } else {
                                        "Use Clipboard Audio"
                                    }
                                } else {
                                    "Clipboard Empty (Copy audio first or Add File above)"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // 2. Voice-Over Presets
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Voice-Over Presets", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        com.example.audio.AudioEffectsProEngine.VoiceOverPreset.values().forEach { preset ->
                            FilterChip(
                                selected = selectedPreset == preset,
                                onClick = {
                                    selectedPreset = preset
                                    voiceGain = preset.voiceGain
                                    musicVolume = preset.musicVolume
                                    duckFactor = preset.duckFactor
                                    autoDuckEnabled = preset.autoDuckEnabled
                                },
                                label = { Text(preset.displayName, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.testTag("chip_vo_preset_${preset.name}")
                            )
                        }
                    }
                    Text(
                        text = "${selectedPreset.displayName}: ${selectedPreset.subtitle}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("vo_preset_subtitle")
                    )
                }

                // 3. Auto-Ducking Option Switch + Sliders
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Ducking", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Automatically lowers music when voice speaks",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoDuckEnabled,
                        onCheckedChange = { autoDuckEnabled = it },
                        modifier = Modifier.testTag("switch_vo_auto_duck")
                    )
                }

                if (autoDuckEnabled) {
                    AccessibleEffectSlider(
                        title = "Background Duck Level",
                        value = duckFactor,
                        onValueChange = { duckFactor = it },
                        valueRange = 0.10f..0.85f,
                        step = 0.05f,
                        displayValue = "${(duckFactor * 100).toInt()}%",
                        spokenDescription = "Background volume dipped to ${(duckFactor * 100).toInt()} percent during speech",
                        testTag = "slider_vo_duck_level"
                    )
                }

                AccessibleEffectSlider(
                    title = "Voice Gain",
                    value = voiceGain,
                    onValueChange = { voiceGain = it },
                    valueRange = 0.70f..1.80f,
                    step = 0.05f,
                    displayValue = "${(voiceGain * 100).toInt()}%",
                    spokenDescription = "Voice gain ${(voiceGain * 100).toInt()} percent",
                    testTag = "slider_vo_gain"
                )

                if (sourceChoice == VoiceOverSourceChoice.ADD_AUDIO_AS_MUSIC) {
                    AccessibleEffectSlider(
                        title = "Background Music Volume",
                        value = musicVolume,
                        onValueChange = { musicVolume = it },
                        valueRange = 0.10f..1.00f,
                        step = 0.05f,
                        displayValue = "${(musicVolume * 100).toInt()}%",
                        spokenDescription = "Background music volume ${(musicVolume * 100).toInt()} percent",
                        testTag = "slider_vo_music_vol"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Loop Music to Match Voice Length", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Switch(
                            checked = loopMusic,
                            onCheckedChange = { loopMusic = it },
                            modifier = Modifier.testTag("switch_vo_loop_music")
                        )
                    }
                }

                if (hasPickedAudio || (hasClipboard && sourceChoice == VoiceOverSourceChoice.CLIPBOARD_CLIP)) {
                    MixSyncOffsetControl(
                        offsetMs = syncOffsetMs,
                        onOffsetChange = { syncOffsetMs = it },
                        baseStartMs = if (sourceChoice == VoiceOverSourceChoice.ADD_AUDIO_AS_MUSIC) 0L else currentPositionMs,
                        title = "Added Audio Sync Timing (Increase / Decrease)"
                    )
                }

                Text(
                    text = when {
                        hasPickedAudio && sourceChoice == VoiceOverSourceChoice.ADD_AUDIO_AS_MUSIC ->
                            "Mixes added music behind your Voice track with ${if (autoDuckEnabled) "Smart Auto-Ducking" else "steady volume"}."
                        hasPickedAudio && sourceChoice == VoiceOverSourceChoice.ADD_AUDIO_AS_CLIP ->
                            "Overdubs added clip at ${AccessibilityHelper.formatTimeVisual((currentPositionMs + syncOffsetMs).coerceAtLeast(0L))} while ducking backing track."
                        hasClipboard && sourceChoice == VoiceOverSourceChoice.CLIPBOARD_CLIP ->
                            "Overdubs clipboard clip at ${AccessibilityHelper.formatTimeVisual((currentPositionMs + syncOffsetMs).coerceAtLeast(0L))} while ducking backing track."
                        else ->
                            "No file or clipboard picked — applies ducking curve across selection (${AccessibilityHelper.formatTimeVisual(trimStartMs)} – ${AccessibilityHelper.formatTimeVisual(trimEndMs)})."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApplyVoiceOver(
                        VoiceOverApplyConfig(
                            sourceChoice = sourceChoice,
                            preset = selectedPreset,
                            autoDuckEnabled = autoDuckEnabled,
                            duckFactor = duckFactor,
                            voiceGain = voiceGain,
                            musicVolume = musicVolume,
                            loopMusic = loopMusic,
                            syncOffsetMs = syncOffsetMs
                        )
                    )
                },
                enabled = !isDecodingAudio,
                modifier = Modifier.height(48.dp).testTag("btn_confirm_voice_over")
            ) {
                Text("Apply Voice Over", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun StudioCuePointsDialog(
    cuePoints: List<CuePoint>,
    currentMs: Long,
    onDropCue: () -> Unit,
    onJumpPrevCue: () -> Unit,
    onJumpNextCue: () -> Unit,
    onSeekToCue: (CuePoint) -> Unit,
    onDeleteCue: (CuePoint) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BackHandler { onDismiss() }
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 580.dp)
                .testTag("dialog_cue_points"),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StudioCuePointsSection(
                    cuePoints = cuePoints,
                    currentMs = currentMs,
                    onDropCue = onDropCue,
                    onJumpPrevCue = onJumpPrevCue,
                    onJumpNextCue = onJumpNextCue,
                    onSeekToCue = onSeekToCue,
                    onDeleteCue = onDeleteCue
                )
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_close_cues_dialog")
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

