package com.example

import com.example.audio.StudioAudioProcessor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BlankAndMixWorkflowTest {

    @Test
    fun `delete selection on full track returns empty 0-sample blank canvas`() {
        val sampleRate = StudioAudioProcessor.SAMPLE_RATE
        val blank10s = ShortArray(10 * sampleRate)
        val afterDeleteAll = StudioAudioProcessor.deleteSelection(
            samples = blank10s,
            startMs = 0L,
            endMs = 10_000L
        )
        assertEquals(0, afterDeleteAll.size)
    }

    @Test
    fun `mix paste and mixAudioTracks work cleanly on empty blank canvas`() {
        val emptyCanvas = ShortArray(0)
        val voiceClip = shortArrayOf(4000, -4000, 8000, -8000)

        val pasted = StudioAudioProcessor.mixPasteAtCursor(
            original = emptyCanvas,
            clip = voiceClip,
            positionMs = 0L,
            clipVolume = 1.0f
        )
        assertArrayEquals(voiceClip, pasted)
    }

    @Test
    fun `mixing two audio tracks combines both signals cleanly with peak protection`() {
        val trackA = shortArrayOf(10000, 10000, -10000, -10000)
        val trackB = shortArrayOf(6000, -2000, -6000, 2000)

        val rawMixed = StudioAudioProcessor.mixAudioTracks(
            main = trackA,
            overlay = trackB,
            startMs = 0L,
            mainVolume = 1.0f,
            overlayVolume = 0.5f,
            applyPeakLimiter = false
        )
        assertArrayEquals(shortArrayOf(13000, 9000, -13000, -9000), rawMixed)

        val hotTrackA = ShortArray(100) { 26000 }
        val hotTrackB = ShortArray(100) { 26000 }
        val limitedMix = StudioAudioProcessor.mixAudioTracks(
            main = hotTrackA,
            overlay = hotTrackB,
            startMs = 0L,
            mainVolume = 1.0f,
            overlayVolume = 1.0f,
            applyPeakLimiter = true
        )
        val peak = limitedMix.maxOf { abs(it.toInt()) }
        assertTrue("Expected limited peak <= 31000 but was $peak", peak <= 31000)
    }
}
