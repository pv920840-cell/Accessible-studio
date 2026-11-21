package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.EqPreset
import com.example.ui.theme.LocalHighContrast
import kotlin.math.abs

data class PanelPresetOption<T>(
    val id: String,
    val label: String,
    val summary: String,
    val values: T
)

data class PresetLabelResolution(
    val label: String,
    val isCustom: Boolean,
    val matchedPresetId: String?
)

object EffectPresetResolver {

    fun approxEqual(a: Float, b: Float, eps: Float = 0.02f): Boolean = abs(a - b) <= eps

    fun <T> resolveLabel(
        current: T,
        lastChosenPresetId: String?,
        presets: List<PanelPresetOption<T>>,
        defaultOption: PanelPresetOption<T>,
        equals: (T, T) -> Boolean
    ): PresetLabelResolution {
        if (lastChosenPresetId != null && lastChosenPresetId != defaultOption.id) {
            val chosen = presets.find { it.id == lastChosenPresetId }
            if (chosen != null) {
                return if (equals(current, chosen.values)) {
                    PresetLabelResolution(label = chosen.label, isCustom = false, matchedPresetId = chosen.id)
                } else {
                    PresetLabelResolution(
                        label = "Custom (${chosen.label} Modified)",
                        isCustom = true,
                        matchedPresetId = null
                    )
                }
            }
        }

        if (equals(current, defaultOption.values)) {
            return PresetLabelResolution(label = defaultOption.label, isCustom = false, matchedPresetId = defaultOption.id)
        }

        val matchingFactory = presets.firstOrNull { equals(current, it.values) }
        if (matchingFactory != null) {
            return PresetLabelResolution(
                label = matchingFactory.label,
                isCustom = false,
                matchedPresetId = matchingFactory.id
            )
        }

        return PresetLabelResolution(label = "Custom", isCustom = true, matchedPresetId = null)
    }
}

@Composable
fun <T> EffectPresetDropdown(
    effectId: String,
    effectTitle: String,
    currentValues: T,
    presets: List<PanelPresetOption<T>>,
    defaultOption: PanelPresetOption<T>,
    valuesEqual: (T, T) -> Boolean,
    onApplyPreset: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val isHighContrast = LocalHighContrast.current
    var expanded by remember { mutableStateOf(false) }
    var lastChosenPresetId by remember { mutableStateOf<String?>(null) }

    val resolution = EffectPresetResolver.resolveLabel(
        current = currentValues,
        lastChosenPresetId = lastChosenPresetId,
        presets = presets,
        defaultOption = defaultOption,
        equals = valuesEqual
    )

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("dropdown_preset_$effectId")
                .semantics {
                    contentDescription = "$effectTitle Presets menu. Current preset is ${resolution.label}. Tap to choose a preset or reset."
                },
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = when {
                    isHighContrast -> Color(0xFF1F1F1F)
                    resolution.isCustom -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                    else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                }
            ),
            border = BorderStroke(
                if (isHighContrast) 1.5.dp else 1.dp,
                when {
                    isHighContrast -> MaterialTheme.colorScheme.secondary
                    resolution.isCustom -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.primary
                }
            )
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
                        Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (resolution.isCustom) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Presets",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = resolution.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .width(290.dp)
                .heightIn(max = 380.dp)
                .testTag("menu_preset_$effectId")
        ) {
            presets.forEach { preset ->
                val isSelected = resolution.matchedPresetId == preset.id
                DropdownMenuItem(
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = preset.label,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = preset.summary,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    trailingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = "Selected", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) }
                    } else null,
                    onClick = {
                        lastChosenPresetId = preset.id
                        onApplyPreset(preset.values)
                        expanded = false
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("preset_${effectId}_${preset.id}")
                )
            }

            HorizontalDivider()

            val isDefaultSelected = resolution.matchedPresetId == defaultOption.id
            DropdownMenuItem(
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = defaultOption.label,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = defaultOption.summary,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                leadingIcon = {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                },
                trailingIcon = if (isDefaultSelected) {
                    { Icon(Icons.Default.Check, contentDescription = "Selected", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary) }
                } else null,
                onClick = {
                    lastChosenPresetId = defaultOption.id
                    onApplyPreset(defaultOption.values)
                    expanded = false
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("preset_${effectId}_reset")
            )
        }
    }
}

// ==================== EFFECT PARAMETER BUNDLES & FACTORY PRESETS ====================

data class EchoParams(val delayMs: Long, val feedback: Float, val mix: Float)
data class ReverbParams(val roomSize: Float, val damping: Float, val mix: Float)
data class ToneEqParams(val bassBoost: Float, val trebleBoost: Float, val eqPreset: EqPreset)
data class SpeedReverseParams(val speedMultiplier: Float, val isReversed: Boolean)
data class CompressorParams(val thresholdDb: Float, val ratio: Float, val makeupGainDb: Float)
data class FlangerParams(val rateHz: Float, val depthMs: Float, val feedback: Float, val mix: Float)
data class ChorusParams(val rateHz: Float, val depthMs: Float, val mix: Float)
data class DistortionParams(val drive: Float, val tone: Float)
data class Eq7BandParams(
    val b60: Float, val b150: Float, val b400: Float,
    val b1k: Float, val b2_5k: Float, val b6k: Float, val b15k: Float
)
data class PitchPolarityParams(val semitones: Int, val invertPhase: Boolean)
data class ParametricEqParams(val hz: Float, val q: Float, val gainDb: Float)

object EffectPanelPresetsCatalog {

    val echoDefault = PanelPresetOption("default_reset", "Default / Reset", "280 ms • Feedback 45% • Mix 35%", EchoParams(280L, 0.45f, 0.35f))
    val echoPresets = listOf(
        PanelPresetOption("slapback_vocal", "Slapback Vocal", "110 ms • Feedback 20% • Mix 30%", EchoParams(110L, 0.20f, 0.30f)),
        PanelPresetOption("tight_doubler", "Tight Studio Doubler", "65 ms • Feedback 15% • Mix 25%", EchoParams(65L, 0.15f, 0.25f)),
        PanelPresetOption("warm_ballad", "Warm Ballad Echo", "380 ms • Feedback 45% • Mix 35%", EchoParams(380L, 0.45f, 0.35f)),
        PanelPresetOption("canyon_delay", "Concert Canyon", "650 ms • Feedback 70% • Mix 50%", EchoParams(650L, 0.70f, 0.50f))
    )

    val reverbDefault = PanelPresetOption("default_reset", "Default / Reset", "Room 65% • Damp 40% • Mix 35%", ReverbParams(0.65f, 0.40f, 0.35f))
    val reverbPresets = listOf(
        PanelPresetOption("small_room", "Small Room", "Room 25% • Damp 65% • Mix 20%", ReverbParams(0.25f, 0.65f, 0.20f)),
        PanelPresetOption("plate", "Plate", "Room 55% • Damp 40% • Mix 30%", ReverbParams(0.55f, 0.40f, 0.30f)),
        PanelPresetOption("concert_hall", "Concert Hall", "Room 85% • Damp 30% • Mix 45%", ReverbParams(0.85f, 0.30f, 0.45f)),
        PanelPresetOption("vocal_booth", "Vocal Booth", "Room 20% • Damp 70% • Mix 15%", ReverbParams(0.20f, 0.70f, 0.15f))
    )

    val noiseReductionDefault = PanelPresetOption("default_reset", "Default / Reset", "Suppression 60%", 0.60f)
    val noiseReductionPresets = listOf(
        PanelPresetOption("light_hiss", "Light Hiss Removal", "Gentle 35% suppression", 0.35f),
        PanelPresetOption("studio_clean", "Studio Clean", "Balanced 60% fan & room cleanup", 0.60f),
        PanelPresetOption("podcast_fan", "Podcast Fan & Motor Cleaner", "Strong 80% fan/motor clean", 0.80f),
        PanelPresetOption("studio_ultra_fan_motor", "Studio Ultra Clean (Fan & Motor)", "100% Max offline fan, motor & all noise", 1.00f)
    )

    val noiseGateDefault = PanelPresetOption("default_reset", "Default / Reset", "Threshold 8%", 0.08f)
    val noiseGatePresets = listOf(
        PanelPresetOption("soft_breath", "Soft Breath Gate", "Threshold 3% (gentle cut)", 0.03f),
        PanelPresetOption("studio_dialogue", "Studio Dialogue Gate", "Threshold 8% (voice isolation)", 0.08f),
        PanelPresetOption("aggressive_gate", "Aggressive Gate", "Threshold 18% (tight silence)", 0.18f)
    )

    val toneEqDefault = PanelPresetOption("default_reset", "Default / Reset", "Flat 0% Bass • 0% Treble • Normal", ToneEqParams(0.0f, 0.0f, EqPreset.NORMAL))
    val toneEqPresets = listOf(
        PanelPresetOption("vocal_presence", "Vocal Presence", "0% Bass • 45% Treble • Vocal Boost", ToneEqParams(0.0f, 0.45f, EqPreset.VOCAL_BOOST)),
        PanelPresetOption("bass_boost", "Bass Boost", "70% Bass • 10% Treble • Warm Bass", ToneEqParams(0.70f, 0.10f, EqPreset.BASS_BOOST)),
        PanelPresetOption("warmth", "Warmth", "45% Bass • 15% Treble • Smooth", ToneEqParams(0.45f, 0.15f, EqPreset.NORMAL)),
        PanelPresetOption("crisp_air", "Crisp Treble Air", "10% Bass • 70% Treble • Crisp Highs", ToneEqParams(0.10f, 0.70f, EqPreset.TREBLE_BOOST)),
        PanelPresetOption("retro_radio", "Retro Radio", "0% Bass • 30% Treble • Lo-Fi Radio", ToneEqParams(0.0f, 0.30f, EqPreset.RADIO_LOFI))
    )

    val speedReverseDefault = PanelPresetOption("default_reset", "Default / Reset", "Speed 1.0x • Forward", SpeedReverseParams(1.0f, false))
    val speedReversePresets = listOf(
        PanelPresetOption("half_speed", "Half Speed (Slow Motion)", "0.50x • Normal direction", SpeedReverseParams(0.50f, false)),
        PanelPresetOption("podcast_125", "Broadcast Fast (1.25x)", "1.25x • Normal direction", SpeedReverseParams(1.25f, false)),
        PanelPresetOption("double_speed", "Double Time (2.0x)", "2.00x • Normal direction", SpeedReverseParams(2.00f, false)),
        PanelPresetOption("reverse_tape", "Reverse Tape", "1.00x • Play backwards", SpeedReverseParams(1.0f, true))
    )

    val compressorDefault = PanelPresetOption("default_reset", "Default / Reset", "-12 dB • 4.0:1 • +3.0 dB", CompressorParams(-12f, 4.0f, 3.0f))
    val compressorPresets = listOf(
        PanelPresetOption("vocal_leveler", "Vocal Leveler", "-16 dB • 2.5:1 • +2.5 dB", CompressorParams(-16f, 2.5f, 2.5f)),
        PanelPresetOption("podcast_broadcast", "Podcast Broadcast", "-18 dB • 4.0:1 • +4.5 dB", CompressorParams(-18f, 4.0f, 4.5f)),
        PanelPresetOption("heavy_punch", "Heavy Punch", "-24 dB • 7.0:1 • +6.5 dB", CompressorParams(-24f, 7.0f, 6.5f)),
        PanelPresetOption("master_glue", "Master Bus Glue", "-10 dB • 2.0:1 • +1.5 dB", CompressorParams(-10f, 2.0f, 1.5f))
    )

    val peakLimiterDefault = PanelPresetOption("default_reset", "Default / Reset", "Ceiling -1.0 dBFS", -1.0f)
    val peakLimiterPresets = listOf(
        PanelPresetOption("transparent_safety", "Transparent Safety", "Ceiling -1.5 dBFS", -1.5f),
        PanelPresetOption("broadcast_ceiling", "Broadcast Ceiling", "Ceiling -1.0 dBFS", -1.0f),
        PanelPresetOption("loud_master", "Loud Master Max", "Ceiling -0.3 dBFS", -0.3f),
        PanelPresetOption("soft_cushion", "Soft Cushion", "Ceiling -3.0 dBFS", -3.0f)
    )

    val flangerDefault = PanelPresetOption("default_reset", "Default / Reset", "0.5 Hz • 3.0 ms • 40% FB • 50% Mix", FlangerParams(0.5f, 3.0f, 0.40f, 0.50f))
    val flangerPresets = listOf(
        PanelPresetOption("subtle_jet", "Subtle Jet Sweep", "0.3 Hz • 2.5 ms • 35% FB • 35% Mix", FlangerParams(0.3f, 2.5f, 0.35f, 0.35f)),
        PanelPresetOption("metallic_sci_fi", "Sci-Fi Metallic", "1.4 Hz • 5.5 ms • 75% FB • 65% Mix", FlangerParams(1.4f, 5.5f, 0.75f, 0.65f)),
        PanelPresetOption("deep_psychedelic", "Deep Psychedelic", "0.8 Hz • 7.0 ms • 60% FB • 60% Mix", FlangerParams(0.8f, 7.0f, 0.60f, 0.60f))
    )

    val chorusDefault = PanelPresetOption("default_reset", "Default / Reset", "1.2 Hz • 6.0 ms • 45% Mix", ChorusParams(1.2f, 6.0f, 0.45f))
    val chorusPresets = listOf(
        PanelPresetOption("vocal_double", "Acoustic Vocal Double", "0.8 Hz • 4.0 ms • 30% Mix", ChorusParams(0.8f, 4.0f, 0.30f)),
        PanelPresetOption("shimmer_80s", "Lush 80s Shimmer", "1.6 Hz • 8.0 ms • 55% Mix", ChorusParams(1.6f, 8.0f, 0.55f)),
        PanelPresetOption("choir_detune", "Rich Choir Detune", "2.4 Hz • 10.0 ms • 65% Mix", ChorusParams(2.4f, 10.0f, 0.65f))
    )

    val distortionDefault = PanelPresetOption("default_reset", "Default / Reset", "Drive 2.5x • Tone 60%", DistortionParams(2.5f, 0.60f))
    val distortionPresets = listOf(
        PanelPresetOption("warm_tape", "Warm Analog Tape", "Drive 1.5x • Tone 75%", DistortionParams(1.5f, 0.75f)),
        PanelPresetOption("blues_crunch", "Blues Crunch", "Drive 3.5x • Tone 60%", DistortionParams(3.5f, 0.60f)),
        PanelPresetOption("heavy_overdrive", "Heavy Overdrive", "Drive 6.5x • Tone 50%", DistortionParams(6.5f, 0.50f)),
        PanelPresetOption("megaphone_grit", "Megaphone Grit", "Drive 5.0x • Tone 30%", DistortionParams(5.0f, 0.30f))
    )

    val eq7BandDefault = PanelPresetOption("default_reset", "Default / Reset (Flat)", "All bands 0 dB", Eq7BandParams(0f, 0f, 0f, 0f, 0f, 0f, 0f))
    val eq7BandPresets = listOf(
        PanelPresetOption("vocal_presence", "Vocal Presence", "-2/-1/+1/+4/+4/+2/+1 dB", Eq7BandParams(-2f, -1f, 1f, 4f, 4f, 2f, 1f)),
        PanelPresetOption("bass_boost", "Bass Boost", "+6/+5/+2/0/0/0/0 dB", Eq7BandParams(6f, 5f, 2f, 0f, 0f, 0f, 0f)),
        PanelPresetOption("warmth", "Warmth", "+4/+3/+2/0/-1/-2/-2 dB", Eq7BandParams(4f, 3f, 2f, 0f, -1f, -2f, -2f)),
        PanelPresetOption("treble_boost", "Treble Boost", "0/0/0/0/+2/+5/+6 dB", Eq7BandParams(0f, 0f, 0f, 0f, 2f, 5f, 6f)),
        PanelPresetOption("podcast", "Podcast Clarity", "-4/+1/+2/+4/+3/+1/-1 dB", Eq7BandParams(-4f, 1f, 2f, 4f, 3f, 1f, -1f)),
        PanelPresetOption("acoustic", "Acoustic Sparkle", "+3/+2/0/+1/+3/+4/+3 dB", Eq7BandParams(3f, 2f, 0f, 1f, 3f, 4f, 3f))
    )

    val pitchPolarityDefault = PanelPresetOption("default_reset", "Default / Reset", "0 st • Normal Polarity", PitchPolarityParams(0, false))
    val pitchPolarityPresets = listOf(
        PanelPresetOption("warm_baritone", "Warm Baritone (-2 st)", "-2 semitones", PitchPolarityParams(-2, false)),
        PanelPresetOption("deep_voice", "Deep Voice (-6 st)", "-6 semitones", PitchPolarityParams(-6, false)),
        PanelPresetOption("bright_lift", "Bright Vocal (+2 st)", "+2 semitones", PitchPolarityParams(2, false)),
        PanelPresetOption("high_voice", "Chipmunk High (+6 st)", "+6 semitones", PitchPolarityParams(6, false)),
        PanelPresetOption("invert_phase", "Invert Phase 180°", "0 st • Flipped polarity", PitchPolarityParams(0, true))
    )

    val parametricEqDefault = PanelPresetOption("default_reset", "Default / Reset (Flat)", "1000 Hz • Q 1.40 • +0.0 dB", ParametricEqParams(1000f, 1.4f, 0f))
    val parametricEqPresets = listOf(
        PanelPresetOption("vocal_presence", "Vocal Presence", "2500 Hz • Q 1.50 • +4.5 dB", ParametricEqParams(2500f, 1.5f, 4.5f)),
        PanelPresetOption("bass_punch", "Sub-Bass Punch", "100 Hz • Q 1.25 • +5.0 dB", ParametricEqParams(100f, 1.25f, 5.0f)),
        PanelPresetOption("mud_scoop", "De-Mud Scoop", "350 Hz • Q 2.00 • -4.0 dB", ParametricEqParams(350f, 2.0f, -4.0f)),
        PanelPresetOption("crisp_air", "Crisp Air Shelf", "8000 Hz • Q 1.00 • +4.0 dB", ParametricEqParams(8000f, 1.0f, 4.0f))
    )
}
