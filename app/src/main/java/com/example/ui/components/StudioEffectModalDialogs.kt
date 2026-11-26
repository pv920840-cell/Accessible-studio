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
import com.example.audio.SpectralDenoiseParams
import com.example.audio.SpectralNoiseProfile
import com.example.audio.StudioFxSettings
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
    denoiseParams: SpectralDenoiseParams = SpectralDenoiseParams.fromLegacyIntensity(noiseReductionIntensity),
    onDenoiseParamsChange: (SpectralDenoiseParams) -> Unit = { p ->
        onNoiseReductionIntensityChange((p.reductionDb / 30f).coerceIn(0.1f, 1f))
    },
    learnedNoiseProfile: SpectralNoiseProfile? = null,
    isPlayingLivePreview: Boolean = false,
    onToggleLivePreview: () -> Unit = {},
    onApplyAdaptiveNoiseReduction: (SpectralDenoiseParams) -> Unit = {},
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
    onCaptureNoisePrint: (() -> Unit)? = null,
    onClearNoisePrint: (() -> Unit)? = null,
    initialSingleEffect: StudioSingleEffectKey? = null,
    onDismiss: () -> Unit
) {
    val isHighContrast = LocalHighContrast.current
    val effectTabs = listOf("Echo & Reverb", "Noise Reduction", "Tone & EQ", "Speed & Reverse", "Studio Pro Rack")
    var selectedSingleEffect by remember(initialSingleEffect) { mutableStateOf(initialSingleEffect) }
    var activeRackGuideTopic by remember { mutableStateOf<FeatureGuideTopic?>(null) }

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
                            text = selectedSingleEffect?.title ?: effectTabs[selectedTabIndex.coerceIn(0, 4)],
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.testTag("txt_effects_dialog_title")
                        )
                        Text(
                            text = if (selectedSingleEffect != null) "Individual Effect Control" else "Offline DSP Studio",
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
                            selected = selectedSingleEffect == null && selectedTabIndex == idx,
                            onClick = {
                                selectedSingleEffect = null
                                onSelectTabIndex(idx)
                            },
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
                    StudioSingleEffectButtonsGrid(
                        selectedEffect = selectedSingleEffect,
                        onSelectEffect = { next ->
                            selectedSingleEffect = next
                            if (next != null) {
                                onSelectTabIndex(next.rackTabIndex)
                            }
                        }
                    )

                    val activeSingleEffect = selectedSingleEffect
                    if (activeSingleEffect != null) {
                        when (activeSingleEffect) {
                            StudioSingleEffectKey.ECHO -> EchoEffectCard(
                                echoEnabled = echoEnabled,
                                onEchoEnabledChange = onEchoEnabledChange,
                                echoDelayMs = echoDelayMs,
                                onEchoDelayChange = onEchoDelayChange,
                                echoFeedback = echoFeedback,
                                onEchoFeedbackChange = onEchoFeedbackChange,
                                echoMix = echoMix,
                                onEchoMixChange = onEchoMixChange
                            )
                            StudioSingleEffectKey.REVERB -> ReverbEffectCard(
                                reverbEnabled = reverbEnabled,
                                onReverbEnabledChange = onReverbEnabledChange,
                                reverbRoomSize = reverbRoomSize,
                                onReverbRoomSizeChange = onReverbRoomSizeChange,
                                reverbDamping = reverbDamping,
                                onReverbDampingChange = onReverbDampingChange,
                                reverbMix = reverbMix,
                                onReverbMixChange = onReverbMixChange
                            )
                            StudioSingleEffectKey.COMPRESSOR -> CompressorEffectCard(
                                settings = studioFxSettings,
                                onSettingsChanged = onStudioFxSettingsChange
                            )
                            StudioSingleEffectKey.PEAK_LIMITER -> PeakLimiterEffectCard(
                                settings = studioFxSettings,
                                onSettingsChanged = onStudioFxSettingsChange
                            )
                            StudioSingleEffectKey.FLANGER -> FlangerEffectCard(
                                settings = studioFxSettings,
                                onSettingsChanged = onStudioFxSettingsChange
                            )
                            StudioSingleEffectKey.CHORUS -> ChorusEffectCard(
                                settings = studioFxSettings,
                                onSettingsChanged = onStudioFxSettingsChange
                            )
                            StudioSingleEffectKey.DISTORTION -> DistortionEffectCard(
                                settings = studioFxSettings,
                                onSettingsChanged = onStudioFxSettingsChange
                            )
                            StudioSingleEffectKey.EQ_7_BAND -> Eq7BandEffectCard(
                                settings = studioFxSettings,
                                onSettingsChanged = onStudioFxSettingsChange
                            )
                            StudioSingleEffectKey.PITCH_POLARITY -> PitchPolarityEffectCard(
                                settings = studioFxSettings,
                                onSettingsChanged = onStudioFxSettingsChange
                            )
                        }
                    } else when (selectedTabIndex.coerceIn(0, 4)) {
                        0 -> {
                            EchoEffectCard(
                                echoEnabled = echoEnabled,
                                onEchoEnabledChange = onEchoEnabledChange,
                                echoDelayMs = echoDelayMs,
                                onEchoDelayChange = onEchoDelayChange,
                                echoFeedback = echoFeedback,
                                onEchoFeedbackChange = onEchoFeedbackChange,
                                echoMix = echoMix,
                                onEchoMixChange = onEchoMixChange
                            )
                            ReverbEffectCard(
                                reverbEnabled = reverbEnabled,
                                onReverbEnabledChange = onReverbEnabledChange,
                                reverbRoomSize = reverbRoomSize,
                                onReverbRoomSizeChange = onReverbRoomSizeChange,
                                reverbDamping = reverbDamping,
                                onReverbDampingChange = onReverbDampingChange,
                                reverbMix = reverbMix,
                                onReverbMixChange = onReverbMixChange
                            )
                        }

                        1 -> {
                            AdaptiveNoiseReductionPanel(
                                params = denoiseParams,
                                onParamsChange = { next ->
                                    if (!noiseReductionEnabled) onNoiseReductionEnabledChange(true)
                                    onDenoiseParamsChange(next)
                                },
                                learnedProfile = learnedNoiseProfile,
                                isPlayingLivePreview = isPlayingLivePreview,
                                onToggleLivePreview = onToggleLivePreview,
                                onLearnNoiseProfile = { onCaptureNoisePrint?.invoke() },
                                onClearNoiseProfile = { onClearNoisePrint?.invoke() },
                                onApplyOfflineClean = { onApplyAdaptiveNoiseReduction(denoiseParams) },
                                onApplyDeClick = onApplyDeClick,
                                isEnabled = noiseReductionEnabled,
                                onEnabledChange = onNoiseReductionEnabledChange
                            )

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
                                    FeatureGuideButton(
                                        featureTitle = "Noise Gate",
                                        onClick = { activeRackGuideTopic = FeatureGuideTopic.AUDIO_EFFECT_NOISE_GATE },
                                        testTag = "btn_feature_guide_noise_gate"
                                    )
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
                                    FeatureGuideButton(
                                        featureTitle = "Equalizer & Tone Shaping",
                                        onClick = { activeRackGuideTopic = FeatureGuideTopic.AUDIO_EFFECT_TONE_EQ },
                                        testTag = "btn_feature_guide_tone_eq"
                                    )
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
                                    FeatureGuideButton(
                                        featureTitle = "Playback Speed & Reverse",
                                        onClick = { activeRackGuideTopic = FeatureGuideTopic.AUDIO_EFFECT_SPEED_REVERSE },
                                        testTag = "btn_feature_guide_speed_reverse"
                                    )
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

    activeRackGuideTopic?.let { topic ->
        SingleFeatureGuideDialog(
            topic = topic,
            onDismiss = { activeRackGuideTopic = null }
        )
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
    var showVoiceOverGuide by remember { mutableStateOf(false) }

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
                FeatureGuideButton(
                    featureTitle = "Voice Over & Auto-Ducking",
                    onClick = { showVoiceOverGuide = true },
                    testTag = "btn_feature_guide_voice_over"
                )
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

    if (showVoiceOverGuide) {
        SingleFeatureGuideDialog(
            topic = FeatureGuideTopic.AUDIO_VOICE_OVER,
            onDismiss = { showVoiceOverGuide = false }
        )
    }
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
    var showCueGuide by remember { mutableStateOf(false) }

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
                FeatureGuideButton(
                    featureTitle = "Cue Points & Bookmarks",
                    onClick = { showCueGuide = true },
                    testTag = "btn_feature_guide_cue_points"
                )
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

    if (showCueGuide) {
        SingleFeatureGuideDialog(
            topic = FeatureGuideTopic.AUDIO_CUE_POINTS,
            onDismiss = { showCueGuide = false }
        )
    }
}

