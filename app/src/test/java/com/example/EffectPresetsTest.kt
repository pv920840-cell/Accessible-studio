package com.example

import com.example.audio.StudioAudioProcessor
import com.example.audio.StudioFxSettings
import com.example.ui.components.CompressorParams
import com.example.ui.components.EffectPanelPresetsCatalog
import com.example.ui.components.EffectPresetResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

class EffectPresetsTest {

    private fun compressorEquals(a: CompressorParams, b: CompressorParams): Boolean {
        return EffectPresetResolver.approxEqual(a.thresholdDb, b.thresholdDb, 0.2f) &&
            EffectPresetResolver.approxEqual(a.ratio, b.ratio, 0.1f) &&
            EffectPresetResolver.approxEqual(a.makeupGainDb, b.makeupGainDb, 0.1f)
    }

    @Test
    fun `selecting factory preset resolves exact label`() {
        val vocalLeveler = EffectPanelPresetsCatalog.compressorPresets.first { it.id == "vocal_leveler" }
        val resolution = EffectPresetResolver.resolveLabel(
            current = vocalLeveler.values,
            lastChosenPresetId = vocalLeveler.id,
            presets = EffectPanelPresetsCatalog.compressorPresets,
            defaultOption = EffectPanelPresetsCatalog.compressorDefault,
            equals = ::compressorEquals
        )
        assertEquals("Vocal Leveler", resolution.label)
        assertFalse(resolution.isCustom)
        assertEquals("vocal_leveler", resolution.matchedPresetId)
    }

    @Test
    fun `modifying slider after selecting preset switches label to Custom Modified`() {
        val vocalLeveler = EffectPanelPresetsCatalog.compressorPresets.first { it.id == "vocal_leveler" }
        val modified = vocalLeveler.values.copy(thresholdDb = -21.0f)
        val resolution = EffectPresetResolver.resolveLabel(
            current = modified,
            lastChosenPresetId = vocalLeveler.id,
            presets = EffectPanelPresetsCatalog.compressorPresets,
            defaultOption = EffectPanelPresetsCatalog.compressorDefault,
            equals = ::compressorEquals
        )
        assertEquals("Custom (Vocal Leveler Modified)", resolution.label)
        assertTrue(resolution.isCustom)
        assertNull(resolution.matchedPresetId)
    }

    @Test
    fun `default reset option restores neutral baseline state`() {
        val resetOption = EffectPanelPresetsCatalog.compressorDefault
        val resolution = EffectPresetResolver.resolveLabel(
            current = resetOption.values,
            lastChosenPresetId = resetOption.id,
            presets = EffectPanelPresetsCatalog.compressorPresets,
            defaultOption = EffectPanelPresetsCatalog.compressorDefault,
            equals = ::compressorEquals
        )
        assertEquals("Default / Reset", resolution.label)
        assertFalse(resolution.isCustom)
        assertEquals("default_reset", resolution.matchedPresetId)
    }

    @Test
    fun `arbitrary custom values without prior preset resolve Custom`() {
        val customValues = CompressorParams(thresholdDb = -13.0f, ratio = 5.5f, makeupGainDb = 2.0f)
        val resolution = EffectPresetResolver.resolveLabel(
            current = customValues,
            lastChosenPresetId = null,
            presets = EffectPanelPresetsCatalog.compressorPresets,
            defaultOption = EffectPanelPresetsCatalog.compressorDefault,
            equals = ::compressorEquals
        )
        assertEquals("Custom", resolution.label)
        assertTrue(resolution.isCustom)
    }

    @Test
    fun `peak limiter preset ceiling strictly limits signal peaks`() {
        val loudInput = ShortArray(2048) { idx ->
            if (idx % 2 == 0) 30000 else -30000
        }
        val settings = StudioFxSettings(
            compressorEnabled = false,
            peakLimiterEnabled = true,
            peakLimiterCeilingDb = -6.0f
        )
        val processed = StudioAudioProcessor.applyStudioRack(loudInput, settings)
        val maxAbs = processed.maxOf { abs(it.toInt()) }
        val expectedMaxLinear = (32767.0 * 10.0.pow(-6.0 / 20.0)).toInt() + 5
        assertTrue("Expected max peak <= $expectedMaxLinear but was $maxAbs", maxAbs <= expectedMaxLinear)
    }
}
