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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.StudioFxSettings
import com.example.ui.theme.LocalHighContrast

/**
 * Enumerates every Echo, Reverb, and Studio Pro Rack effect so each effect
 * can be opened, auditioned, and applied from its own dedicated button.
 */
enum class StudioSingleEffectKey(
    val tag: String,
    val title: String,
    val shortLabel: String,
    val rackTabIndex: Int
) {
    ECHO("echo", "Echo (Delay)", "Echo (Delay)", 0),
    REVERB("reverb", "Reverb (Acoustic Space)", "Reverb", 0),
    COMPRESSOR("compressor", "Dynamics Compressor", "Dynamics Compressor", 4),
    PEAK_LIMITER("peak_limiter", "Peak Limiter (Anti-Clip)", "Peak Limiter", 4),
    FLANGER("flanger", "Flanger", "Flanger", 4),
    CHORUS("chorus", "Chorus (Multi-Voice)", "Chorus", 4),
    DISTORTION("distortion", "Overdrive & Distortion", "Overdrive & Distortion", 4),
    EQ_7_BAND("eq7band", "7-Band Graphic Equalizer", "7-Band EQ", 4),
    PITCH_POLARITY("pitch_polarity", "Pitch Shifter & Polarity", "Pitch & Polarity", 4)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioSingleEffectButtonsGrid(
    selectedEffect: StudioSingleEffectKey?,
    onSelectEffect: (StudioSingleEffectKey?) -> Unit,
    modifier: Modifier = Modifier
) {
    val isHighContrast = LocalHighContrast.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("row_effects_individual_buttons"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Individual Effects (Tap any effect directly)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StudioSingleEffectKey.entries.forEach { effectKey ->
                val isSelected = selectedEffect == effectKey
                val tag = "btn_effects_item_${effectKey.tag}"
                if (isSelected) {
                    Button(
                        onClick = { onSelectEffect(null) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag(tag)
                            .semantics {
                                contentDescription = "${effectKey.title}, currently open. Tap to return to tab view."
                            },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(effectKey.shortLabel, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSelectEffect(effectKey) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag(tag)
                            .semantics {
                                contentDescription = "Open ${effectKey.title} controls"
                            },
                        border = BorderStroke(
                            if (isHighContrast) 2.dp else 1.dp,
                            if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(effectKey.shortLabel, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun EchoEffectCard(
    echoEnabled: Boolean,
    onEchoEnabledChange: (Boolean) -> Unit,
    echoDelayMs: Long,
    onEchoDelayChange: (Long) -> Unit,
    echoFeedback: Float,
    onEchoFeedbackChange: (Float) -> Unit,
    echoMix: Float,
    onEchoMixChange: (Float) -> Unit,
    alwaysExpanded: Boolean = false,
    onApplySingleEffect: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isHighContrast = LocalHighContrast.current
    var showGuide by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = if (isHighContrast) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth().testTag("card_single_fx_echo")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEchoEnabledChange(!echoEnabled) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Echo (Delay)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(
                    checked = echoEnabled,
                    onCheckedChange = onEchoEnabledChange,
                    modifier = Modifier.testTag("switch_echo_enabled")
                )
            }
            FeatureGuideButton(
                featureTitle = "Echo (Delay)",
                onClick = { showGuide = true },
                testTag = "btn_feature_guide_fx_echo"
            )
            AnimatedVisibility(visible = alwaysExpanded || echoEnabled) {
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
                    if (onApplySingleEffect != null) {
                        Spacer(Modifier.height(2.dp))
                        Button(
                            onClick = onApplySingleEffect,
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_apply_single_effect_echo")
                        ) {
                            Text("Apply Echo (Delay)", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    if (showGuide) {
        SingleFeatureGuideDialog(
            topic = FeatureGuideTopic.AUDIO_EFFECT_ECHO,
            onDismiss = { showGuide = false }
        )
    }
}

@Composable
fun ReverbEffectCard(
    reverbEnabled: Boolean,
    onReverbEnabledChange: (Boolean) -> Unit,
    reverbRoomSize: Float,
    onReverbRoomSizeChange: (Float) -> Unit,
    reverbDamping: Float,
    onReverbDampingChange: (Float) -> Unit,
    reverbMix: Float,
    onReverbMixChange: (Float) -> Unit,
    alwaysExpanded: Boolean = false,
    onApplySingleEffect: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isHighContrast = LocalHighContrast.current
    var showGuide by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = if (isHighContrast) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth().testTag("card_single_fx_reverb")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onReverbEnabledChange(!reverbEnabled) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Reverb (Acoustic Space)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(
                    checked = reverbEnabled,
                    onCheckedChange = onReverbEnabledChange,
                    modifier = Modifier.testTag("switch_reverb_enabled")
                )
            }
            FeatureGuideButton(
                featureTitle = "Reverb (Acoustic Space)",
                onClick = { showGuide = true },
                testTag = "btn_feature_guide_fx_reverb"
            )
            AnimatedVisibility(visible = alwaysExpanded || reverbEnabled) {
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
                    if (onApplySingleEffect != null) {
                        Spacer(Modifier.height(2.dp))
                        Button(
                            onClick = onApplySingleEffect,
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_apply_single_effect_reverb")
                        ) {
                            Text("Apply Reverb (Acoustic Space)", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    if (showGuide) {
        SingleFeatureGuideDialog(
            topic = FeatureGuideTopic.AUDIO_EFFECT_REVERB,
            onDismiss = { showGuide = false }
        )
    }
}

@Composable
fun CompressorEffectCard(
    settings: StudioFxSettings,
    onSettingsChanged: (StudioFxSettings) -> Unit,
    alwaysExpanded: Boolean = false,
    onApplySingleEffect: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isHighContrast = LocalHighContrast.current
    var showGuide by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = if (isHighContrast) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth().testTag("card_single_fx_compressor")
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
            FeatureGuideButton(
                featureTitle = "Dynamics Compressor",
                onClick = { showGuide = true },
                testTag = "btn_feature_guide_fx_compressor"
            )

            AnimatedVisibility(visible = alwaysExpanded || settings.compressorEnabled) {
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
                    if (onApplySingleEffect != null) {
                        Button(
                            onClick = onApplySingleEffect,
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_apply_single_effect_compressor")
                        ) {
                            Text("Apply Dynamics Compressor", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    if (showGuide) {
        SingleFeatureGuideDialog(
            topic = FeatureGuideTopic.AUDIO_EFFECT_COMPRESSOR,
            onDismiss = { showGuide = false }
        )
    }
}

@Composable
fun PeakLimiterEffectCard(
    settings: StudioFxSettings,
    onSettingsChanged: (StudioFxSettings) -> Unit,
    alwaysExpanded: Boolean = false,
    onApplySingleEffect: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isHighContrast = LocalHighContrast.current
    var showGuide by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = if (isHighContrast) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth().testTag("card_single_fx_peak_limiter")
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
            FeatureGuideButton(
                featureTitle = "Peak Limiter (Anti-Clip)",
                onClick = { showGuide = true },
                testTag = "btn_feature_guide_fx_peak_limiter"
            )

            AnimatedVisibility(visible = alwaysExpanded || settings.peakLimiterEnabled) {
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
                    if (onApplySingleEffect != null) {
                        Button(
                            onClick = onApplySingleEffect,
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_apply_single_effect_peak_limiter")
                        ) {
                            Text("Apply Peak Limiter", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    if (showGuide) {
        SingleFeatureGuideDialog(
            topic = FeatureGuideTopic.AUDIO_EFFECT_PEAK_LIMITER,
            onDismiss = { showGuide = false }
        )
    }
}

@Composable
fun FlangerEffectCard(
    settings: StudioFxSettings,
    onSettingsChanged: (StudioFxSettings) -> Unit,
    alwaysExpanded: Boolean = false,
    onApplySingleEffect: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isHighContrast = LocalHighContrast.current
    var showGuide by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = if (isHighContrast) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth().testTag("card_single_fx_flanger")
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
            FeatureGuideButton(
                featureTitle = "Flanger",
                onClick = { showGuide = true },
                testTag = "btn_feature_guide_fx_flanger"
            )

            AnimatedVisibility(visible = alwaysExpanded || settings.flangerEnabled) {
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
                    if (onApplySingleEffect != null) {
                        Button(
                            onClick = onApplySingleEffect,
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_apply_single_effect_flanger")
                        ) {
                            Text("Apply Flanger", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    if (showGuide) {
        SingleFeatureGuideDialog(
            topic = FeatureGuideTopic.AUDIO_EFFECT_FLANGER,
            onDismiss = { showGuide = false }
        )
    }
}

@Composable
fun ChorusEffectCard(
    settings: StudioFxSettings,
    onSettingsChanged: (StudioFxSettings) -> Unit,
    alwaysExpanded: Boolean = false,
    onApplySingleEffect: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isHighContrast = LocalHighContrast.current
    var showGuide by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = if (isHighContrast) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth().testTag("card_single_fx_chorus")
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
            FeatureGuideButton(
                featureTitle = "Chorus (Multi-Voice)",
                onClick = { showGuide = true },
                testTag = "btn_feature_guide_fx_chorus"
            )

            AnimatedVisibility(visible = alwaysExpanded || settings.chorusEnabled) {
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
                    if (onApplySingleEffect != null) {
                        Button(
                            onClick = onApplySingleEffect,
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_apply_single_effect_chorus")
                        ) {
                            Text("Apply Chorus (Multi-Voice)", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    if (showGuide) {
        SingleFeatureGuideDialog(
            topic = FeatureGuideTopic.AUDIO_EFFECT_CHORUS,
            onDismiss = { showGuide = false }
        )
    }
}

@Composable
fun DistortionEffectCard(
    settings: StudioFxSettings,
    onSettingsChanged: (StudioFxSettings) -> Unit,
    alwaysExpanded: Boolean = false,
    onApplySingleEffect: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isHighContrast = LocalHighContrast.current
    var showGuide by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = if (isHighContrast) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth().testTag("card_single_fx_distortion")
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
            FeatureGuideButton(
                featureTitle = "Overdrive & Distortion",
                onClick = { showGuide = true },
                testTag = "btn_feature_guide_fx_distortion"
            )

            AnimatedVisibility(visible = alwaysExpanded || settings.distortionEnabled) {
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
                    if (onApplySingleEffect != null) {
                        Button(
                            onClick = onApplySingleEffect,
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_apply_single_effect_distortion")
                        ) {
                            Text("Apply Overdrive & Distortion", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    if (showGuide) {
        SingleFeatureGuideDialog(
            topic = FeatureGuideTopic.AUDIO_EFFECT_DISTORTION,
            onDismiss = { showGuide = false }
        )
    }
}

@Composable
fun Eq7BandEffectCard(
    settings: StudioFxSettings,
    onSettingsChanged: (StudioFxSettings) -> Unit,
    alwaysExpanded: Boolean = false,
    onApplySingleEffect: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isHighContrast = LocalHighContrast.current
    var showGuide by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = if (isHighContrast) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth().testTag("card_single_fx_eq7band")
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
            FeatureGuideButton(
                featureTitle = "7-Band Graphic Equalizer",
                onClick = { showGuide = true },
                testTag = "btn_feature_guide_fx_eq7band"
            )

            AnimatedVisibility(visible = alwaysExpanded || settings.eq7BandEnabled) {
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
                    if (onApplySingleEffect != null) {
                        Button(
                            onClick = onApplySingleEffect,
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_apply_single_effect_eq7band")
                        ) {
                            Text("Apply 7-Band Graphic EQ", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    if (showGuide) {
        SingleFeatureGuideDialog(
            topic = FeatureGuideTopic.AUDIO_EFFECT_EQ_7BAND,
            onDismiss = { showGuide = false }
        )
    }
}

@Composable
fun PitchPolarityEffectCard(
    settings: StudioFxSettings,
    onSettingsChanged: (StudioFxSettings) -> Unit,
    onApplySingleEffect: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isHighContrast = LocalHighContrast.current
    var pitchExpanded by remember { mutableStateOf(true) }
    var showGuide by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = if (isHighContrast) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth().testTag("card_single_fx_pitch_polarity")
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
            FeatureGuideButton(
                featureTitle = "Pitch Shifter & Polarity",
                onClick = { showGuide = true },
                testTag = "btn_feature_guide_fx_pitch_polarity"
            )

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

                    if (onApplySingleEffect != null) {
                        Button(
                            onClick = onApplySingleEffect,
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_apply_single_effect_pitch_polarity")
                        ) {
                            Text("Apply Pitch & Polarity", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    if (showGuide) {
        SingleFeatureGuideDialog(
            topic = FeatureGuideTopic.AUDIO_EFFECT_PITCH_POLARITY,
            onDismiss = { showGuide = false }
        )
    }
}
