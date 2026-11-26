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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.audio.AudioEffectsProEngine
import com.example.audio.StudioAudioProcessor
import com.example.ui.theme.LocalHighContrast

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccessibleProSuiteSection(
    selectedCategoryIndex: Int,
    onSelectCategoryIndex: (Int) -> Unit,
    hasClipboard: Boolean,
    trimStartMs: Long,
    trimEndMs: Long,
    currentPositionMs: Long,
    currentSampleRate: Int,
    currentPlaybackRate: Float,
    clipboardPcm: ShortArray?,
    onApplyPcmTransform: (announcement: String, transform: (ShortArray) -> ShortArray) -> Unit,
    onSetSampleRate: (Int) -> Unit,
    onSetPlaybackRate: (Float) -> Unit,
    onOpenEchoReverbTab: () -> Unit,
    onOpenNoiseTab: () -> Unit,
    onOpenEqTab: () -> Unit,
    onOpenGainDialog: () -> Unit,
    onApplyEcho: (() -> Unit)? = null,
    onApplyReverb: (() -> Unit)? = null,
    onApplyNoiseGate: (() -> Unit)? = null,
    onApplyNoiseReduction: (() -> Unit)? = null,
    onApplyEqualizer: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isHighContrast = LocalHighContrast.current
    val categories = listOf("Effect", "Channels", "Filter", "Volume")
    var showProGuide by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_pro_audio_suite"),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            if (isHighContrast) 2.dp else 1.dp,
            if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
        ),
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
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "PRO DSP & MASTERING SUITE",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Effect • Channels • Filter • Volume (100% Free & Offline)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "FREE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            FeatureGuideButton(
                featureTitle = "Pro DSP Suite",
                onClick = { showProGuide = true },
                testTag = "btn_feature_guide_pro_suite"
            )

            ScrollableTabRow(
                selectedTabIndex = selectedCategoryIndex.coerceIn(0, 3),
                edgePadding = 4.dp,
                containerColor = if (isHighContrast) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
            ) {
                categories.forEachIndexed { idx, title ->
                    Tab(
                        selected = selectedCategoryIndex == idx,
                        onClick = { onSelectCategoryIndex(idx) },
                        modifier = Modifier.testTag("tab_pro_suite_${title.lowercase().replace(" ", "_")}"),
                        text = { Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    )
                }
            }

            when (selectedCategoryIndex.coerceIn(0, 3)) {
                0 -> ProEffectCategoryContent(
                    hasClipboard = hasClipboard,
                    trimStartMs = trimStartMs,
                    trimEndMs = trimEndMs,
                    currentPositionMs = currentPositionMs,
                    currentSampleRate = currentSampleRate,
                    currentPlaybackRate = currentPlaybackRate,
                    clipboardPcm = clipboardPcm,
                    onApplyPcmTransform = onApplyPcmTransform,
                    onSetSampleRate = onSetSampleRate,
                    onSetPlaybackRate = onSetPlaybackRate,
                    onOpenEchoReverbTab = onOpenEchoReverbTab,
                    onApplyEcho = onApplyEcho,
                    onApplyReverb = onApplyReverb
                )
                1 -> ProChannelsCategoryContent(
                    hasClipboard = hasClipboard,
                    trimStartMs = trimStartMs,
                    trimEndMs = trimEndMs,
                    clipboardPcm = clipboardPcm,
                    onApplyPcmTransform = onApplyPcmTransform
                )
                2 -> ProFilterCategoryContent(
                    onApplyPcmTransform = onApplyPcmTransform,
                    onOpenNoiseTab = onOpenNoiseTab,
                    onOpenEqTab = onOpenEqTab,
                    onApplyNoiseGate = onApplyNoiseGate,
                    onApplyNoiseReduction = onApplyNoiseReduction,
                    onApplyEqualizer = onApplyEqualizer
                )
                3 -> ProVolumeCategoryContent(
                    trimStartMs = trimStartMs,
                    trimEndMs = trimEndMs,
                    onApplyPcmTransform = onApplyPcmTransform,
                    onOpenGainDialog = onOpenGainDialog
                )
            }
        }
    }
    if (showProGuide) {
        SingleFeatureGuideDialog(
            topic = FeatureGuideTopic.AUDIO_PRO_DSP_SUITE,
            onDismiss = { showProGuide = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProEffectCategoryContent(
    hasClipboard: Boolean,
    trimStartMs: Long,
    trimEndMs: Long,
    currentPositionMs: Long,
    currentSampleRate: Int,
    currentPlaybackRate: Float,
    clipboardPcm: ShortArray?,
    onApplyPcmTransform: (String, (ShortArray) -> ShortArray) -> Unit,
    onSetSampleRate: (Int) -> Unit,
    onSetPlaybackRate: (Float) -> Unit,
    onOpenEchoReverbTab: () -> Unit,
    onApplyEcho: (() -> Unit)? = null,
    onApplyReverb: (() -> Unit)? = null
) {
    var censorMode by remember { mutableStateOf(AudioEffectsProEngine.CensorMode.BLEEP_1KHZ) }
    var pitchSemitones by remember { mutableFloatStateOf(2f) }
    var timeStretchRate by remember { mutableFloatStateOf(1.0f) }
    var offsetPct by remember { mutableFloatStateOf(0f) }
    var dopplerIntensity by remember { mutableFloatStateOf(0.7f) }
    var voiceDuckLevel by remember { mutableFloatStateOf(0.30f) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Censor Tool
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Censor Selection", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AudioEffectsProEngine.CensorMode.values().forEach { mode ->
                        FilterChip(
                            selected = censorMode == mode,
                            onClick = { censorMode = mode },
                            label = { Text(mode.displayName, fontSize = 11.sp) }
                        )
                    }
                }
                Button(
                    onClick = {
                        onApplyPcmTransform("Applied Censor (${censorMode.displayName}) across selection") { pcm ->
                            AudioEffectsProEngine.censorSelection(pcm, trimStartMs, trimEndMs, censorMode)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_effect_censor")
                ) {
                    Text("Apply Censor (${censorMode.displayName})", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Time, Pitch & Rate Card
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pitch, Time Stretch & Rate", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                AccessibleEffectSlider(
                    title = "Pitch Shift",
                    value = pitchSemitones,
                    onValueChange = { pitchSemitones = it },
                    valueRange = -12f..12f,
                    step = 1f,
                    displayValue = "${if (pitchSemitones > 0) "+" else ""}${pitchSemitones.toInt()} st",
                    spokenDescription = "Pitch shift ${pitchSemitones.toInt()} semitones",
                    testTag = "slider_pro_pitch"
                )
                AccessibleEffectSlider(
                    title = "Time Stretch (Keep Pitch)",
                    value = timeStretchRate,
                    onValueChange = { timeStretchRate = it },
                    valueRange = 0.5f..2.0f,
                    step = 0.05f,
                    displayValue = "${"%.2f".format(timeStretchRate)}x",
                    spokenDescription = "Time stretch ${"%.2f".format(timeStretchRate)} times keeping pitch",
                    testTag = "slider_pro_time_stretch"
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onApplyPcmTransform("Applied Pitch Shift ${pitchSemitones.toInt()} semitones") { pcm ->
                                StudioAudioProcessor.applyPitchShift(pcm, pitchSemitones.toInt())
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("btn_effect_pitch")
                    ) { Text("Pitch", fontWeight = FontWeight.Bold) }

                    Button(
                        onClick = {
                            onApplyPcmTransform("Applied Time Stretch ${"%.2f".format(timeStretchRate)}x preserving pitch") { pcm ->
                                AudioEffectsProEngine.applyTimeStretchKeepPitch(pcm, timeStretchRate)
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("btn_effect_time")
                    ) { Text("Time", fontWeight = FontWeight.Bold) }
                }

                Text("Sampling Rate:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(22050, 32000, 44100, 48000).forEach { sr ->
                        FilterChip(
                            selected = currentSampleRate == sr,
                            onClick = { onSetSampleRate(sr) },
                            label = { Text("${sr / 1000.0} kHz", fontSize = 11.sp) },
                            modifier = Modifier.testTag("chip_sr_$sr")
                        )
                    }
                }
                Text("Playback Rate:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { r ->
                        FilterChip(
                            selected = kotlin.math.abs(currentPlaybackRate - r) < 0.04f,
                            onClick = { onSetPlaybackRate(r) },
                            label = { Text("${r}x", fontSize = 11.sp) },
                            modifier = Modifier.testTag("chip_pr_$r")
                        )
                    }
                }
            }
        }

        // Voice Over (Ducking Overdub)
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Voice Over (Auto-Ducking Overdub)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                AccessibleEffectSlider(
                    title = "Background Music Duck Level",
                    value = voiceDuckLevel,
                    onValueChange = { voiceDuckLevel = it },
                    valueRange = 0.1f..0.9f,
                    step = 0.05f,
                    displayValue = "${(voiceDuckLevel * 100).toInt()}%",
                    spokenDescription = "Background track ducks to ${(voiceDuckLevel * 100).toInt()} percent during voice over",
                    testTag = "slider_voiceover_duck"
                )
                Button(
                    enabled = hasClipboard && clipboardPcm != null,
                    onClick = {
                        val voice = clipboardPcm ?: return@Button
                        onApplyPcmTransform("Mixed Voice Over with smart background ducking") { pcm ->
                            AudioEffectsProEngine.applyVoiceOverDuck(pcm, voice, currentPositionMs, voiceDuckLevel)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_effect_voice_over")
                ) {
                    Text(
                        if (hasClipboard) "Apply Voice Over at Playhead" else "Copy/Record Clip to Clipboard for Voice Over",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Doppler & Offset Sliders
        AccessibleEffectSlider(
            title = "Doppler Proximity Intensity",
            value = dopplerIntensity,
            onValueChange = { dopplerIntensity = it },
            valueRange = 0.2f..1.0f,
            step = 0.05f,
            displayValue = "${(dopplerIntensity * 100).toInt()}%",
            spokenDescription = "Doppler intensity ${(dopplerIntensity * 100).toInt()} percent",
            testTag = "slider_doppler"
        )
        AccessibleEffectSlider(
            title = "DC Offset Shift",
            value = offsetPct,
            onValueChange = { offsetPct = it },
            valueRange = -0.25f..0.25f,
            step = 0.05f,
            displayValue = "${(offsetPct * 100).toInt()}%",
            spokenDescription = "Vertical waveform offset ${(offsetPct * 100).toInt()} percent",
            testTag = "slider_vertical_offset"
        )

        // Instant Effect Action Grid (all 19 items accessible in 1 tap!)
        Text("All Classic Studio Effects (Tap to Apply):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProActionButton("Compressor/Expander", "btn_effect_comp_exp") {
                onApplyPcmTransform("Applied Compressor and Expander dynamics") { pcm ->
                    val comp = StudioAudioProcessor.applyCompressor(pcm, thresholdDb = -16f, ratio = 3.5f)
                    StudioAudioProcessor.applyExpander(comp, thresholdDb = -30f, ratio = 1.6f)
                }
            }
            ProActionButton("Doppler", "btn_effect_doppler") {
                onApplyPcmTransform("Applied Doppler motion sweep") { pcm ->
                    AudioEffectsProEngine.applyDoppler(pcm, trimStartMs, trimEndMs, dopplerIntensity)
                }
            }
            ProActionButton("Dynamics", "btn_effect_dynamics") {
                onApplyPcmTransform("Applied Dynamics curve shaper") { pcm ->
                    AudioEffectsProEngine.applyDynamicsCurve(pcm)
                }
            }
            ProActionButton("Echo", "btn_effect_echo") {
                onApplyEcho?.invoke() ?: onOpenEchoReverbTab()
            }
            ProActionButton("Reverb", "btn_effect_reverb") {
                onApplyReverb?.invoke() ?: onOpenEchoReverbTab()
            }
            ProActionButton("Flanger", "btn_effect_flanger") {
                onApplyPcmTransform("Applied Flanger sweep effect") { pcm ->
                    StudioAudioProcessor.applyFlanger(pcm, rateHz = 0.4f, depthMs = 4.5f, mix = 0.45f)
                }
            }
            ProActionButton("Frequency Blend", "btn_effect_freq_blend") {
                onApplyPcmTransform("Applied Frequency Blend harmonic warmth") { pcm ->
                    AudioEffectsProEngine.applyFrequencyBlend(pcm, 0.4f)
                }
            }
            ProActionButton("Interpolate", "btn_effect_interpolate") {
                onApplyPcmTransform("Interpolated waveform smoothly across selection") { pcm ->
                    AudioEffectsProEngine.interpolateSelection(pcm, trimStartMs, trimEndMs)
                }
            }
            ProActionButton("Invert", "btn_effect_invert") {
                onApplyPcmTransform("Inverted waveform polarity") { pcm ->
                    AudioEffectsProEngine.invertPhase(pcm, trimStartMs, trimEndMs)
                }
            }
            ProActionButton("Mechanize", "btn_effect_mechanize") {
                onApplyPcmTransform("Applied Mechanize metallic modulation") { pcm ->
                    StudioAudioProcessor.applyMechanize(pcm, 110f, 0.8f)
                }
            }
            ProActionButton("Offset", "btn_effect_offset") {
                onApplyPcmTransform("Applied Vertical Offset ${(offsetPct * 100).toInt()}%") { pcm ->
                    AudioEffectsProEngine.applyVerticalOffset(pcm, offsetPct)
                }
            }
            ProActionButton("Reverse", "btn_effect_reverse") {
                onApplyPcmTransform("Reversed audio waveform") { pcm ->
                    StudioAudioProcessor.reverseAudio(pcm)
                }
            }
            ProActionButton("Resample", "btn_effect_resample") {
                onApplyPcmTransform("Resampled audio through 32kHz-44.1kHz anti-alias filter") { pcm ->
                    AudioEffectsProEngine.applyResampleProfile(pcm, 32000)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProChannelsCategoryContent(
    hasClipboard: Boolean,
    trimStartMs: Long,
    trimEndMs: Long,
    clipboardPcm: ShortArray?,
    onApplyPcmTransform: (String, (ShortArray) -> ShortArray) -> Unit
) {
    var leftGain by remember { mutableFloatStateOf(1.0f) }
    var rightGain by remember { mutableFloatStateOf(1.0f) }
    var panValue by remember { mutableFloatStateOf(0f) }
    var centerWidth by remember { mutableFloatStateOf(0.55f) }
    var bus1Gain by remember { mutableFloatStateOf(1.0f) }
    var bus2Gain by remember { mutableFloatStateOf(0.8f) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AccessibleEffectSlider(
            title = "Channel Mixer: Left Weight",
            value = leftGain,
            onValueChange = { leftGain = it },
            valueRange = 0.1f..1.8f,
            step = 0.05f,
            displayValue = "${(leftGain * 100).toInt()}%",
            spokenDescription = "Left channel weight ${(leftGain * 100).toInt()} percent",
            testTag = "slider_ch_left"
        )
        AccessibleEffectSlider(
            title = "Channel Mixer: Right Weight",
            value = rightGain,
            onValueChange = { rightGain = it },
            valueRange = 0.1f..1.8f,
            step = 0.05f,
            displayValue = "${(rightGain * 100).toInt()}%",
            spokenDescription = "Right channel weight ${(rightGain * 100).toInt()} percent",
            testTag = "slider_ch_right"
        )
        AccessibleEffectSlider(
            title = "Pan Position (L / Center / R)",
            value = panValue,
            onValueChange = { panValue = it },
            valueRange = -1.0f..1.0f,
            step = 0.1f,
            displayValue = when {
                panValue < -0.05f -> "L ${(-panValue * 100).toInt()}%"
                panValue > 0.05f -> "R ${(panValue * 100).toInt()}%"
                else -> "Center"
            },
            spokenDescription = "Pan position",
            testTag = "slider_ch_pan"
        )
        AccessibleEffectSlider(
            title = "Stereo Center / Width",
            value = centerWidth,
            onValueChange = { centerWidth = it },
            valueRange = 0.0f..1.0f,
            step = 0.05f,
            displayValue = "${(centerWidth * 100).toInt()}%",
            spokenDescription = "Stereo center width ${(centerWidth * 100).toInt()} percent",
            testTag = "slider_ch_stereo_center"
        )

        // Multichannel Mixer Bus Sliders
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Multichannel Bus Mixer", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                AccessibleEffectSlider(
                    title = "Bus 1 (Main Track)",
                    value = bus1Gain,
                    onValueChange = { bus1Gain = it },
                    valueRange = 0.0f..1.5f,
                    step = 0.05f,
                    displayValue = "${(bus1Gain * 100).toInt()}%",
                    spokenDescription = "Bus 1 gain ${(bus1Gain * 100).toInt()} percent",
                    testTag = "slider_bus1"
                )
                AccessibleEffectSlider(
                    title = "Bus 2 (Clipboard / Stem)",
                    value = bus2Gain,
                    onValueChange = { bus2Gain = it },
                    valueRange = 0.0f..1.5f,
                    step = 0.05f,
                    displayValue = "${(bus2Gain * 100).toInt()}%",
                    spokenDescription = "Bus 2 gain ${(bus2Gain * 100).toInt()} percent",
                    testTag = "slider_bus2"
                )
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProActionButton("Channel Mixer", "btn_ch_mixer") {
                onApplyPcmTransform("Applied Channel Mixer balance") { pcm ->
                    AudioEffectsProEngine.applyChannelMixer(pcm, leftGain, rightGain)
                }
            }
            ProActionButton("MaxMatch", "btn_ch_maxmatch") {
                onApplyPcmTransform("Applied MaxMatch peak uniformity across channels") { pcm ->
                    AudioEffectsProEngine.applyMaxMatch(pcm, trimStartMs, trimEndMs)
                }
            }
            ProActionButton("Multichannel Mixer", "btn_ch_multichannel") {
                onApplyPcmTransform("Mixed multichannel buses into master track") { pcm ->
                    AudioEffectsProEngine.applyMultichannelMix(pcm, if (hasClipboard) clipboardPcm else null, bus1Gain, bus2Gain)
                }
            }
            ProActionButton("Pan", "btn_ch_pan") {
                onApplyPcmTransform("Applied Pan contour") { pcm ->
                    AudioEffectsProEngine.applyPanAndStereoCenter(pcm, panValue = panValue, centerWidth = 0.2f)
                }
            }
            ProActionButton("Stereo Center", "btn_ch_stereo_center") {
                onApplyPcmTransform("Applied Stereo Center spatial enhancement") { pcm ->
                    AudioEffectsProEngine.applyPanAndStereoCenter(pcm, panValue = panValue, centerWidth = centerWidth)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProFilterCategoryContent(
    onApplyPcmTransform: (String, (ShortArray) -> ShortArray) -> Unit,
    onOpenNoiseTab: () -> Unit,
    onOpenEqTab: () -> Unit,
    onApplyNoiseGate: (() -> Unit)? = null,
    onApplyNoiseReduction: (() -> Unit)? = null,
    onApplyEqualizer: (() -> Unit)? = null
) {
    var isBandStop by remember { mutableStateOf(false) }
    var bandCenterHz by remember { mutableFloatStateOf(1500f) }
    var hpCutoffHz by remember { mutableFloatStateOf(120f) }
    var lpCutoffHz by remember { mutableFloatStateOf(6500f) }
    var paramHz by remember { mutableFloatStateOf(1000f) }
    var paramQ by remember { mutableFloatStateOf(1.4f) }
    var paramGainDb by remember { mutableFloatStateOf(4.5f) }
    var specLowDb by remember { mutableFloatStateOf(2f) }
    var specMidDb by remember { mutableFloatStateOf(0f) }
    var specHighDb by remember { mutableFloatStateOf(3f) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Parametric EQ Card
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Parametric EQ (Biquad Peaking)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                EffectPresetDropdown(
                    effectId = "parametric_eq",
                    effectTitle = "Parametric Peak EQ",
                    currentValues = ParametricEqParams(paramHz, paramQ, paramGainDb),
                    presets = EffectPanelPresetsCatalog.parametricEqPresets,
                    defaultOption = EffectPanelPresetsCatalog.parametricEqDefault,
                    valuesEqual = { a, b ->
                        EffectPresetResolver.approxEqual(a.hz, b.hz, 25f) &&
                            EffectPresetResolver.approxEqual(a.q, b.q, 0.1f) &&
                            EffectPresetResolver.approxEqual(a.gainDb, b.gainDb, 0.25f)
                    },
                    onApplyPreset = { preset ->
                        paramHz = preset.hz
                        paramQ = preset.q
                        paramGainDb = preset.gainDb
                    }
                )
                AccessibleEffectSlider(
                    title = "Center Frequency",
                    value = paramHz,
                    onValueChange = { paramHz = it },
                    valueRange = 80f..10000f,
                    step = 50f,
                    displayValue = "${paramHz.toInt()} Hz",
                    spokenDescription = "Parametric EQ frequency ${paramHz.toInt()} Hertz",
                    testTag = "slider_param_hz"
                )
                AccessibleEffectSlider(
                    title = "Q Factor (Bandwidth)",
                    value = paramQ,
                    onValueChange = { paramQ = it },
                    valueRange = 0.5f..8.0f,
                    step = 0.25f,
                    displayValue = "Q ${"%.2f".format(paramQ)}",
                    spokenDescription = "Q factor ${"%.2f".format(paramQ)}",
                    testTag = "slider_param_q"
                )
                AccessibleEffectSlider(
                    title = "Gain",
                    value = paramGainDb,
                    onValueChange = { paramGainDb = it },
                    valueRange = -12f..12f,
                    step = 0.5f,
                    displayValue = "%+.1f dB".format(paramGainDb),
                    spokenDescription = "Parametric gain %+.1f decibels".format(paramGainDb),
                    testTag = "slider_param_gain"
                )
                Button(
                    onClick = {
                        onApplyPcmTransform("Applied Parametric EQ at ${paramHz.toInt()} Hz (%+.1f dB)".format(paramGainDb)) { pcm ->
                            AudioEffectsProEngine.applyParametricEq(pcm, paramHz, paramQ, paramGainDb)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_filter_parametric_eq")
                ) { Text("Apply Parametric EQ", fontWeight = FontWeight.Bold) }
            }
        }

        // Bandpass/stop & Low/Highpass controls
        AccessibleEffectSlider(
            title = if (isBandStop) "Band-Stop (Notch) Center" else "Band-Pass Center",
            value = bandCenterHz,
            onValueChange = { bandCenterHz = it },
            valueRange = 200f..6000f,
            step = 100f,
            displayValue = "${bandCenterHz.toInt()} Hz",
            spokenDescription = "Band center ${bandCenterHz.toInt()} Hertz",
            testTag = "slider_band_center"
        )
        AccessibleEffectSlider(
            title = "High-Pass Cutoff",
            value = hpCutoffHz,
            onValueChange = { hpCutoffHz = it },
            valueRange = 40f..1500f,
            step = 20f,
            displayValue = "${hpCutoffHz.toInt()} Hz",
            spokenDescription = "High-pass cutoff ${hpCutoffHz.toInt()} Hertz",
            testTag = "slider_hp_cutoff"
        )
        AccessibleEffectSlider(
            title = "Low-Pass Cutoff",
            value = lpCutoffHz,
            onValueChange = { lpCutoffHz = it },
            valueRange = 1200f..14000f,
            step = 200f,
            displayValue = "${lpCutoffHz.toInt()} Hz",
            spokenDescription = "Low-pass cutoff ${lpCutoffHz.toInt()} Hertz",
            testTag = "slider_lp_cutoff"
        )
        AccessibleEffectSlider(
            title = "Spectrum Tilt (High Air)",
            value = specHighDb,
            onValueChange = { specHighDb = it },
            valueRange = -9f..9f,
            step = 1f,
            displayValue = "%+.0f dB".format(specHighDb),
            spokenDescription = "Spectrum high tilt %+.0f decibels".format(specHighDb),
            testTag = "slider_spectrum_high"
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProActionButton("Auto Offset Removal", "btn_filter_offset_removal") {
                onApplyPcmTransform("Removed DC Offset automatically") { pcm ->
                    StudioAudioProcessor.removeDcOffset(pcm)
                }
            }
            ProActionButton(if (isBandStop) "Apply Band-Stop" else "Apply Band-Pass", "btn_filter_bandpass_stop") {
                onApplyPcmTransform("Applied ${if (isBandStop) "Band-Stop" else "Band-Pass"} at ${bandCenterHz.toInt()} Hz") { pcm ->
                    if (isBandStop) StudioAudioProcessor.applyNotchFilter(pcm, bandCenterHz, 400f)
                    else StudioAudioProcessor.applyBandPassFilter(pcm, bandCenterHz, 1600f)
                }
            }
            OutlinedButton(
                onClick = { isBandStop = !isBandStop },
                modifier = Modifier.height(48.dp).testTag("btn_toggle_band_mode")
            ) { Text(if (isBandStop) "Mode: Band-Stop" else "Mode: Band-Pass", fontSize = 12.sp) }

            ProActionButton("Equalizer", "btn_filter_equalizer") {
                onApplyEqualizer?.invoke() ?: onOpenEqTab()
            }
            ProActionButton("Low/Highpass", "btn_filter_low_highpass") {
                onApplyPcmTransform("Applied High-Pass ${hpCutoffHz.toInt()}Hz & Low-Pass ${lpCutoffHz.toInt()}Hz") { pcm ->
                    AudioEffectsProEngine.applyLowAndHighPass(pcm, hpCutoffHz, lpCutoffHz)
                }
            }
            ProActionButton("Noise Gate", "btn_filter_noise_gate") {
                onApplyNoiseGate?.invoke() ?: onOpenNoiseTab()
            }
            ProActionButton("Noise Reduction", "btn_filter_noise_reduction") {
                onApplyNoiseReduction?.invoke() ?: onOpenNoiseTab()
            }
            ProActionButton("Pop/Click", "btn_filter_pop_click") {
                onApplyPcmTransform("Removed pops and clicks smoothly") { pcm ->
                    StudioAudioProcessor.deClickDeCrackle(pcm)
                }
            }
            ProActionButton("Silence Reduction", "btn_filter_silence_reduction") {
                onApplyPcmTransform("Shortened long silent pauses automatically") { pcm ->
                    AudioEffectsProEngine.applySilenceReduction(pcm, thresholdDb = -36f, maxKeepSilenceMs = 250)
                }
            }
            ProActionButton("Smoother", "btn_filter_smoother") {
                onApplyPcmTransform("Applied Waveform Smoother filter") { pcm ->
                    AudioEffectsProEngine.applySmoother(pcm, strength = 2)
                }
            }
            ProActionButton("Spectrum Filter", "btn_filter_spectrum") {
                onApplyPcmTransform("Applied 3-region Spectrum Filter") { pcm ->
                    AudioEffectsProEngine.applySpectrumFilter(pcm, specLowDb, specMidDb, specHighDb)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProVolumeCategoryContent(
    trimStartMs: Long,
    trimEndMs: Long,
    onApplyPcmTransform: (String, (ShortArray) -> ShortArray) -> Unit,
    onOpenGainDialog: () -> Unit
) {
    var selectedShape by remember { mutableStateOf(AudioEffectsProEngine.VolumeShapeType.ARCH_PEAK) }
    var loudnessBoostDb by remember { mutableFloatStateOf(4.0f) }
    var matchTargetDb by remember { mutableFloatStateOf(-16.0f) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AccessibleEffectSlider(
            title = "Match Volume Target (RMS)",
            value = matchTargetDb,
            onValueChange = { matchTargetDb = it },
            valueRange = -24f..-9f,
            step = 1f,
            displayValue = "${matchTargetDb.toInt()} dBFS",
            spokenDescription = "Match volume target ${matchTargetDb.toInt()} decibels full scale",
            testTag = "slider_match_volume"
        )
        AccessibleEffectSlider(
            title = "Loudness Contour Boost",
            value = loudnessBoostDb,
            onValueChange = { loudnessBoostDb = it },
            valueRange = 1f..9f,
            step = 0.5f,
            displayValue = "+${"%.1f".format(loudnessBoostDb)} dB",
            spokenDescription = "Loudness boost ${"%.1f".format(loudnessBoostDb)} decibels",
            testTag = "slider_loudness_boost"
        )

        Text("Shape Volume Envelope:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AudioEffectsProEngine.VolumeShapeType.values().forEach { shape ->
                FilterChip(
                    selected = selectedShape == shape,
                    onClick = { selectedShape = shape },
                    label = { Text(shape.displayName, fontSize = 11.sp) }
                )
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProActionButton("Auto Gain", "btn_vol_auto_gain") {
                onApplyPcmTransform("Applied Auto Gain Control leveling") { pcm ->
                    AudioEffectsProEngine.applyAutoGain(pcm, matchTargetDb)
                }
            }
            ProActionButton("Change Volume", "btn_vol_change") { onOpenGainDialog() }
            ProActionButton("Fade In", "btn_vol_fade_in") {
                onApplyPcmTransform("Applied Fade In across selection") { pcm ->
                    StudioAudioProcessor.fadeInSelection(pcm, trimStartMs, trimEndMs)
                }
            }
            ProActionButton("Fade Out", "btn_vol_fade_out") {
                onApplyPcmTransform("Applied Fade Out across selection") { pcm ->
                    StudioAudioProcessor.fadeOutSelection(pcm, trimStartMs, trimEndMs)
                }
            }
            ProActionButton("Match Volume", "btn_vol_match") {
                onApplyPcmTransform("Matched Volume to ${matchTargetDb.toInt()} dBFS") { pcm ->
                    StudioAudioProcessor.normalizeRms(pcm, matchTargetDb)
                }
            }
            ProActionButton("Maximize Volume", "btn_vol_maximize") {
                onApplyPcmTransform("Maximized peak volume to -0.1 dB") { pcm ->
                    StudioAudioProcessor.normalize(pcm)
                }
            }
            ProActionButton("Loudness", "btn_vol_loudness") {
                onApplyPcmTransform("Applied Loudness perceptual boost") { pcm ->
                    AudioEffectsProEngine.applyLoudnessContour(pcm, loudnessBoostDb)
                }
            }
            ProActionButton("Shape Volume (${selectedShape.displayName})", "btn_vol_shape") {
                onApplyPcmTransform("Applied Shape Volume: ${selectedShape.displayName}") { pcm ->
                    AudioEffectsProEngine.applyShapeVolume(pcm, trimStartMs, trimEndMs, selectedShape)
                }
            }
        }
    }
}

@Composable
private fun ProActionButton(
    label: String,
    tag: String,
    onClick: () -> Unit
) {
    val isHighContrast = LocalHighContrast.current
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .height(48.dp)
            .testTag(tag)
            .semantics { contentDescription = "Apply $label" },
        border = BorderStroke(
            if (isHighContrast) 2.dp else 1.dp,
            if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

