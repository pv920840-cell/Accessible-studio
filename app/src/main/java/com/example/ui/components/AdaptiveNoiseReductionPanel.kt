package com.example.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SpectralDenoiseParams
import com.example.audio.SpectralNoiseProfile
import com.example.ui.theme.LocalHighContrast
import kotlin.math.abs

data class AdaptiveDenoisePresetItem(
    val id: String,
    val label: String,
    val testTag: String,
    val summary: String,
    val params: SpectralDenoiseParams
)

object AdaptiveNoiseReductionPresets {
    val presets: List<AdaptiveDenoisePresetItem> = listOf(
        AdaptiveDenoisePresetItem(
            id = "voice_clean",
            label = "Voice Clean",
            testTag = "chip_dn_preset_voice",
            summary = "-32 dB • 18 dB Red • 65% Smooth • Voice Focus",
            params = SpectralDenoiseParams(
                thresholdDb = -32f,
                reductionDb = 18f,
                smoothing = 0.65f,
                voiceDialogueFocus = true,
                fftSize = 2048
            )
        ),
        AdaptiveDenoisePresetItem(
            id = "fan_motor",
            label = "Fan & Motor",
            testTag = "chip_dn_preset_fan_motor",
            summary = "-20 dB • 27 dB Red • 72% Smooth • Voice Focus",
            params = SpectralDenoiseParams(
                thresholdDb = -20f,
                reductionDb = 27f,
                smoothing = 0.72f,
                voiceDialogueFocus = true,
                fftSize = 2048
            )
        ),
        AdaptiveDenoisePresetItem(
            id = "studio_ultra",
            label = "Studio Ultra",
            testTag = "chip_dn_preset_ultra",
            summary = "-14 dB • 30 dB Red • 74% Smooth • Voice Focus",
            params = SpectralDenoiseParams(
                thresholdDb = -14f,
                reductionDb = 30f,
                smoothing = 0.74f,
                voiceDialogueFocus = true,
                fftSize = 2048
            )
        ),
        AdaptiveDenoisePresetItem(
            id = "gentle_hiss",
            label = "Gentle De-Hiss",
            testTag = "chip_dn_preset_hiss",
            summary = "-40 dB • 11 dB Red • 58% Smooth • Voice Focus",
            params = SpectralDenoiseParams(
                thresholdDb = -40f,
                reductionDb = 11f,
                smoothing = 0.58f,
                voiceDialogueFocus = true,
                fftSize = 2048
            )
        ),
        AdaptiveDenoisePresetItem(
            id = "music_spectral",
            label = "Music De-Noise",
            testTag = "chip_dn_preset_music",
            summary = "-36 dB • 14 dB Red • 72% Smooth • Full Bandwidth",
            params = SpectralDenoiseParams(
                thresholdDb = -36f,
                reductionDb = 14f,
                smoothing = 0.72f,
                voiceDialogueFocus = false,
                fftSize = 4096
            )
        )
    )

    fun matchesPreset(current: SpectralDenoiseParams, target: SpectralDenoiseParams): Boolean {
        return abs(current.thresholdDb - target.thresholdDb) <= 1.0f &&
            abs(current.reductionDb - target.reductionDb) <= 1.0f &&
            abs(current.smoothing - target.smoothing) <= 0.04f &&
            current.voiceDialogueFocus == target.voiceDialogueFocus
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdaptiveNoiseReductionPanel(
    params: SpectralDenoiseParams,
    onParamsChange: (SpectralDenoiseParams) -> Unit,
    learnedProfile: SpectralNoiseProfile?,
    isPlayingLivePreview: Boolean,
    onToggleLivePreview: () -> Unit,
    onLearnNoiseProfile: () -> Unit,
    onClearNoiseProfile: () -> Unit,
    onApplyOfflineClean: () -> Unit,
    onApplyDeClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    onEnabledChange: ((Boolean) -> Unit)? = null,
    onOpenGuide: (() -> Unit)? = null
) {
    val isHighContrast = LocalHighContrast.current
    val safeParams = params.clamped()
    var showLocalGuide by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("panel_adaptive_denoise"),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighContrast) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(
            width = if (isHighContrast) 1.5.dp else 1.dp,
            color = if (isHighContrast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header + Optional Enable Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .let { base ->
                        if (onEnabledChange != null) {
                            base.clickable { onEnabledChange(!isEnabled) }
                        } else {
                            base
                        }
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Adaptive Spectral & Voice De-Noise",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "Offline ${safeParams.fftSize}-pt 75% Overlap STFT • Two-Step Wiener & Comb Suppressor",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (onEnabledChange != null) {
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = onEnabledChange,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("switch_noise_reduction_enabled")
                            .semantics {
                                contentDescription = "Enable Adaptive Spectral De-Noise"
                                stateDescription = if (isEnabled) "On" else "Off"
                            }
                    )
                }
            }

            FeatureGuideButton(
                featureTitle = "Adaptive Spectral De-Noise",
                onClick = {
                    if (onOpenGuide != null) onOpenGuide() else showLocalGuide = true
                },
                testTag = "btn_feature_guide_adaptive_denoise"
            )

            AnimatedVisibility(visible = isEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Preset Selector Chips
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AdaptiveNoiseReductionPresets.presets.forEach { preset ->
                            val isSelected = AdaptiveNoiseReductionPresets.matchesPreset(safeParams, preset.params)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    onParamsChange(
                                        preset.params.copy(
                                            bypass = safeParams.bypass,
                                            adaptiveMode = safeParams.adaptiveMode
                                        )
                                    )
                                },
                                label = {
                                    Text(
                                        text = preset.label,
                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag(preset.testTag)
                                    .semantics {
                                        contentDescription = "${preset.label} preset. ${preset.summary}"
                                    }
                            )
                        }
                    }

                    // Learn Mode / Spectral Noise Profiling Box
                    Surface(
                        color = if (isHighContrast) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (learnedProfile != null) {
                                        "Noise Print Active (Learn Mode)"
                                    } else {
                                        "Auto Adaptive Noise-Floor Mode"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Black,
                                    color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            Text(
                                text = if (learnedProfile != null) {
                                    "Profile Locked (${"%.1f".format(learnedProfile.durationMs / 1000f)}s • ${learnedProfile.noiseFloorDbfs.toInt()} dBFS): ${learnedProfile.bandSummary}"
                                } else {
                                    "Continuously tracks fan, motor, hum & hiss live without selection—or mark a 0.2s–4s noise-only gap using Trim Start/End and tap Learn Noise Profile."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("txt_dn_profile_status")
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onLearnNoiseProfile,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("btn_dn_learn_profile")
                                        .semantics {
                                            contentDescription = "Learn Noise Profile from selected noise-only audio region"
                                        }
                                ) {
                                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Learn Noise Profile", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                if (learnedProfile != null) {
                                    OutlinedButton(
                                        onClick = onClearNoiseProfile,
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("btn_dn_clear_profile")
                                            .semantics {
                                                contentDescription = "Clear learned noise profile and switch back to Auto Adaptive noise tracking"
                                            }
                                    ) {
                                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Auto Adaptive", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Real-Time Live Preview & Instant A/B Compare Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onToggleLivePreview,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPlayingLivePreview) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                }
                            ),
                            modifier = Modifier
                                .weight(1.1f)
                                .heightIn(min = 48.dp)
                                .testTag("btn_dn_live_preview")
                                .semantics {
                                    contentDescription = if (isPlayingLivePreview) {
                                        "Stop real-time live de-noise preview"
                                    } else {
                                        "Play real-time live de-noise preview with instant slider updates"
                                    }
                                }
                        ) {
                            Icon(
                                imageVector = if (isPlayingLivePreview) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isPlayingLivePreview) "Stop Live" else "Live Preview",
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp
                            )
                        }

                        val isBypassed = safeParams.bypass
                        OutlinedButton(
                            onClick = { onParamsChange(safeParams.copy(bypass = !isBypassed)) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isBypassed) {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                } else {
                                    Color.Transparent
                                }
                            ),
                            border = BorderStroke(
                                width = if (isBypassed) 2.dp else 1.dp,
                                color = if (isBypassed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("btn_dn_bypass_ab")
                                .semantics {
                                    contentDescription = "A/B Compare Bypass toggle. Adjusts live between original audio and de-noised audio."
                                    stateDescription = if (isBypassed) "Bypassed Original Audio" else "De-Noise Active"
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Default.CompareArrows,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isBypassed) "A/B: Original" else "A/B: De-Noised",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    HorizontalDivider()

                    // 1. Threshold (dB): -60 dB to 0 dB
                    AccessibleEffectSlider(
                        title = "Threshold (Detection Sensitivity)",
                        value = safeParams.thresholdDb,
                        onValueChange = { v -> onParamsChange(safeParams.copy(thresholdDb = v)) },
                        valueRange = -60f..0f,
                        step = 1f,
                        displayValue = "${safeParams.thresholdDb.toInt()} dB",
                        spokenDescription = "${safeParams.thresholdDb.toInt()} decibels noise detection threshold",
                        testTag = "slider_dn_threshold"
                    )

                    // 2. Reduction (dB): 0 dB to 30 dB
                    AccessibleEffectSlider(
                        title = "Reduction (Attenuation Depth)",
                        value = safeParams.reductionDb,
                        onValueChange = { v -> onParamsChange(safeParams.copy(reductionDb = v)) },
                        valueRange = 0f..30f,
                        step = 1f,
                        displayValue = "${safeParams.reductionDb.toInt()} dB",
                        spokenDescription = "${safeParams.reductionDb.toInt()} decibels noise reduction depth",
                        testTag = "slider_dn_reduction"
                    )

                    // 3. Smoothing / Musical Noise Suppression: 0% to 100%
                    AccessibleEffectSlider(
                        title = "Smoothing (Musical Noise Suppression)",
                        value = safeParams.smoothing,
                        onValueChange = { v -> onParamsChange(safeParams.copy(smoothing = v)) },
                        valueRange = 0f..1f,
                        step = 0.05f,
                        displayValue = "${(safeParams.smoothing * 100).toInt()}%",
                        spokenDescription = "${(safeParams.smoothing * 100).toInt()} percent spectral and temporal smoothing",
                        testTag = "slider_dn_smoothing"
                    )

                    // 4. Voice / Dialogue Focus Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable {
                                onParamsChange(safeParams.copy(voiceDialogueFocus = !safeParams.voiceDialogueFocus))
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Voice / Dialogue Focus",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (safeParams.voiceDialogueFocus) {
                                    "Protects vocal formants while eliminating fan, motor & AC hum"
                                } else {
                                    "Full-spectrum transparent De-Noise for music & instruments"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = safeParams.voiceDialogueFocus,
                            onCheckedChange = { checked ->
                                onParamsChange(safeParams.copy(voiceDialogueFocus = checked))
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("switch_dn_voice_focus")
                                .semantics {
                                    contentDescription = "Voice and Dialogue Focus"
                                    stateDescription = if (safeParams.voiceDialogueFocus) "On" else "Off"
                                }
                        )
                    }

                    // 5. STFT Window Size Resolution (2048 Low Latency vs 4096 High Precision)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "STFT Resolution:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = safeParams.fftSize == 2048,
                            onClick = { onParamsChange(safeParams.copy(fftSize = 2048)) },
                            label = { Text("2048 Fast", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("chip_dn_fft_2048")
                                .semantics { contentDescription = "2048 point low latency STFT window" }
                        )
                        FilterChip(
                            selected = safeParams.fftSize == 4096,
                            onClick = { onParamsChange(safeParams.copy(fftSize = 4096)) },
                            label = { Text("4096 Studio", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("chip_dn_fft_4096")
                                .semantics { contentDescription = "4096 point high precision STFT window" }
                        )
                    }

                    HorizontalDivider()

                    // Offline Render & Optional De-Click Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onApplyDeClick != null) {
                            OutlinedButton(
                                onClick = onApplyDeClick,
                                modifier = Modifier
                                    .weight(0.85f)
                                    .heightIn(min = 48.dp)
                                    .testTag("btn_dn_declick")
                                    .semantics { contentDescription = "Apply De-Click filter with peak limiter" },
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(Icons.Default.Waves, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("De-Click", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        Button(
                            onClick = onApplyOfflineClean,
                            modifier = Modifier
                                .weight(1.3f)
                                .heightIn(min = 48.dp)
                                .testTag("btn_dn_apply_offline")
                                .semantics {
                                    contentDescription = "Apply offline Studio Adaptive De-Noise to track with zero clipping"
                                }
                        ) {
                            Icon(
                                imageVector = if (safeParams.voiceDialogueFocus) Icons.Default.Mic else Icons.Default.Hearing,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Apply Studio De-Noise", fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
    if (showLocalGuide) {
        SingleFeatureGuideDialog(
            topic = FeatureGuideTopic.AUDIO_EFFECT_ADAPTIVE_DENOISE,
            onDismiss = { showLocalGuide = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoAdaptiveNoiseReductionSection(
    enabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    params: SpectralDenoiseParams,
    onParamsChange: (SpectralDenoiseParams) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeParams = params.clamped()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("section_video_adaptive_denoise"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { onToggleEnabled(!enabled) },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Studio Adaptive Video De-Noise", fontWeight = FontWeight.Bold)
                Text(
                    text = "Offline 75% Overlap STFT Wiener engine removes fan, motor, wind & AC hum cleanly",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggleEnabled,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("switch_video_noise_reducer")
                    .semantics {
                        contentDescription = "Studio Adaptive Video De-Noise"
                        stateDescription = if (enabled) "On" else "Off"
                    }
            )
        }

        AnimatedVisibility(visible = enabled) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AdaptiveNoiseReductionPresets.presets.forEach { preset ->
                        val isSelected = AdaptiveNoiseReductionPresets.matchesPreset(safeParams, preset.params)
                        FilterChip(
                            selected = isSelected,
                            onClick = { onParamsChange(preset.params) },
                            label = { Text(preset.label, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("chip_video_dn_${preset.id}")
                        )
                    }
                }

                AccessibleEffectSlider(
                    title = "Threshold",
                    value = safeParams.thresholdDb,
                    onValueChange = { v -> onParamsChange(safeParams.copy(thresholdDb = v)) },
                    valueRange = -60f..0f,
                    step = 1f,
                    displayValue = "${safeParams.thresholdDb.toInt()} dB",
                    spokenDescription = "Video de-noise threshold ${safeParams.thresholdDb.toInt()} decibels",
                    testTag = "slider_video_dn_threshold"
                )

                AccessibleEffectSlider(
                    title = "Reduction Depth",
                    value = safeParams.reductionDb,
                    onValueChange = { v -> onParamsChange(safeParams.copy(reductionDb = v)) },
                    valueRange = 0f..30f,
                    step = 1f,
                    displayValue = "${safeParams.reductionDb.toInt()} dB",
                    spokenDescription = "Video noise reduction ${safeParams.reductionDb.toInt()} decibels",
                    testTag = "slider_video_dn_reduction"
                )

                AccessibleEffectSlider(
                    title = "Smoothing",
                    value = safeParams.smoothing,
                    onValueChange = { v -> onParamsChange(safeParams.copy(smoothing = v)) },
                    valueRange = 0f..1f,
                    step = 0.05f,
                    displayValue = "${(safeParams.smoothing * 100).toInt()}%",
                    spokenDescription = "Video noise smoothing ${(safeParams.smoothing * 100).toInt()} percent",
                    testTag = "slider_video_dn_smoothing"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable {
                            onParamsChange(safeParams.copy(voiceDialogueFocus = !safeParams.voiceDialogueFocus))
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Voice / Dialogue Focus", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Preserves vocal clarity & removes fan/motor hum",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = safeParams.voiceDialogueFocus,
                        onCheckedChange = { checked ->
                            onParamsChange(safeParams.copy(voiceDialogueFocus = checked))
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("switch_video_dn_voice_focus")
                            .semantics {
                                contentDescription = "Video Voice and Dialogue Focus"
                                stateDescription = if (safeParams.voiceDialogueFocus) "On" else "Off"
                            }
                    )
                }
            }
        }
    }
}
