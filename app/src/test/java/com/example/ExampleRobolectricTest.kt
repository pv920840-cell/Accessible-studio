package com.example

import com.example.accessibility.AccessibilityHelper
import com.example.audio.WavAudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleRobolectricTest {

    @Test
    fun `format visual time accurately`() {
        val formatted = AccessibilityHelper.formatTimeVisual(65400L)
        assertEquals("01:05.4", formatted)
    }

    @Test
    fun `format spoken time for accessibility`() {
        val spoken = AccessibilityHelper.formatTimeSpoken(65400L)
        assertTrue(spoken.contains("1 minute"))
        assertTrue(spoken.contains("5 point 4 seconds"))
    }

    @Test
    fun `format file size accurately`() {
        val sizeFormatted = AccessibilityHelper.formatFileSize(1500000L)
        assertTrue(sizeFormatted.contains("MB"))
    }

    @Test
    fun `test echo and reverb DSP algorithms`() {
        val processor = WavAudioProcessor()
        val input = ShortArray(44100) { (it % 1000).toShort() }

        val echo = processor.applyEcho(input, delayMs = 200L, feedback = 0.4f, mix = 0.5f)
        assertEquals(input.size, echo.size)

        val reverb = processor.applyReverb(input, roomSize = 0.6f, damping = 0.3f, mix = 0.4f)
        assertEquals(input.size, reverb.size)
    }

    @Test
    fun `test noise gate and speed algorithms`() {
        val processor = WavAudioProcessor()
        val quietInput = ShortArray(1000) { 10 }
        val gated = processor.applyNoiseGate(quietInput, threshold = 0.1f)
        assertTrue(gated[500] < quietInput[500])

        val doubleSpeed = processor.applySpeed(quietInput, speed = 2.0f)
        assertEquals(500, doubleSpeed.size)

        val reverse = processor.applyReverse(shortArrayOf(1, 2, 3, 4, 5))
        assertEquals(5.toShort(), reverse[0])
        assertEquals(1.toShort(), reverse[4])
    }

    @Test
    fun `test studio normalize maximizes volume`() {
        val quiet = shortArrayOf(100, -200, 500, -1000)
        val normalized = com.example.audio.StudioAudioProcessor.normalize(quiet, targetRatio = 0.98f)
        assertEquals(quiet.size, normalized.size)
        val peak = normalized.maxOf { kotlin.math.abs(it.toInt()) }
        assertTrue(peak > 30000) // Scaled up near full dynamic range (-0.1 dB)
    }

    @Test
    fun `test studio flanger and chorus`() {
        val input = ShortArray(44100) { (it % 800).toShort() }
        val flanged = com.example.audio.StudioAudioProcessor.applyFlanger(input)
        assertEquals(input.size, flanged.size)

        val chorused = com.example.audio.StudioAudioProcessor.applyChorus(input)
        assertEquals(input.size, chorused.size)
    }

    @Test
    fun `test studio compressor and 7 band eq`() {
        val loud = ShortArray(10000) { 30000.toShort() }
        val compressed = com.example.audio.StudioAudioProcessor.applyCompressor(loud, thresholdDb = -18f, ratio = 6f)
        assertEquals(loud.size, compressed.size)

        val eq = com.example.audio.StudioAudioProcessor.apply7BandEq(
            loud,
            b60 = 3f, b150 = 2f, b400 = 0f, b1k = -2f, b2_5k = 1f, b6k = 4f, b15k = 5f
        )
        assertEquals(loud.size, eq.size)
    }

    @Test
    fun `test studio silence insertion and tone synthesis`() {
        val original = ShortArray(44100) { 1000.toShort() }
        val withSilence = com.example.audio.StudioAudioProcessor.insertSilence(original, positionMs = 500L, durationSec = 1)
        assertEquals(44100 * 2, withSilence.size)

        val sineTone = com.example.audio.StudioAudioProcessor.generateTone("sine", 440f, 2)
        assertEquals(44100 * 2, sineTone.size)

        val noise = com.example.audio.StudioAudioProcessor.generateTone("noise", 0f, 1)
        assertEquals(44100, noise.size)
    }

    @Test
    fun `test studio mute and delete selection`() {
        val original = ShortArray(44100) { 5000.toShort() }
        val muted = com.example.audio.StudioAudioProcessor.muteSelection(original, startMs = 200L, endMs = 400L)
        assertEquals(original.size, muted.size)
        // Check muted zone
        val mutedSample = muted[(0.3 * 44100).toInt()]
        assertEquals(0.toShort(), mutedSample)

        val deleted = com.example.audio.StudioAudioProcessor.deleteSelection(original, startMs = 0L, endMs = 500L)
        assertEquals(22050, deleted.size)
    }
}
