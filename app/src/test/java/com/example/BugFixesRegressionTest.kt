package com.example

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.audio.AudioEditSettings
import com.example.audio.AudioEffectsProEngine
import com.example.audio.AudioPlayerManager
import com.example.audio.ScrubDirection
import com.example.audio.StudioAudioProcessor
import com.example.audio.StudioFxSettings
import com.example.audio.WavAudioProcessor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class BugFixesRegressionTest {

    @Test
    fun emptyPcmCanvas_doesNotCrashAnyDspOrEditOperation() {
        val empty = ShortArray(0)
        val fx = StudioFxSettings(
            flangerEnabled = true,
            chorusEnabled = true,
            distortionEnabled = true,
            compressorEnabled = true,
            peakLimiterEnabled = true,
            eq7BandEnabled = true,
            eqBand1kHz = 4f,
            pitchShiftSemitones = 2,
            invertPhase = true
        )

        assertEquals(0, StudioAudioProcessor.cropToSelection(empty, 0L, 1000L).size)
        assertEquals(0, StudioAudioProcessor.deleteSelection(empty, 0L, 1000L).size)
        assertEquals(0, StudioAudioProcessor.removeDcOffset(empty).size)
        assertEquals(0, StudioAudioProcessor.reverseAudio(empty).size)
        assertEquals(0, StudioAudioProcessor.normalize(empty).size)
        assertEquals(0, StudioAudioProcessor.applyTruePeakLimiter(empty, -1.0f).size)
        assertEquals(0, StudioAudioProcessor.applyStudioRack(empty, fx).size)

        assertEquals(0, AudioEffectsProEngine.invertPhase(empty, 0L, 1000L).size)
        assertEquals(0, AudioEffectsProEngine.applyChannelMixer(empty, 1.0f, 1.0f).size)
        assertEquals(0, AudioEffectsProEngine.applyMultichannelMix(empty, null).size)

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val wavProcessor = WavAudioProcessor(context)
        assertEquals(0, wavProcessor.processPcm(empty, AudioEditSettings(echoEnabled = true, reverbEnabled = true, noiseGateEnabled = true, speedMultiplier = 1.5f)).size)
    }

    @Test
    fun cropToSelection_invertedOrZeroRangeReturnsEmptySafely() {
        val sample = ShortArray(1000) { 1234 }
        assertEquals(0, StudioAudioProcessor.cropToSelection(sample, 500L, 500L).size)
        assertEquals(0, StudioAudioProcessor.cropToSelection(sample, 900L, 200L).size)
    }

    @Test
    fun playerManager_emptyPcmScrubAndPlayDoNotCrash() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val player = AudioPlayerManager(context)
        try {
            player.loadPcmData(ShortArray(0), StudioAudioProcessor.SAMPLE_RATE)
            assertEquals(0L, player.durationMs.value)

            player.play(startFromMs = 0L, endLimitMs = 0L)
            player.startScrub(ScrubDirection.REWIND)
            player.startScrub(ScrubDirection.FORWARD)
            player.toggleScrub(ScrubDirection.REWIND)
            assertFalse(player.isPlaying.value)
        } finally {
            player.release()
        }
    }

    @Test
    fun wavAudioProcessor_exportEmptyPcmThrowsCleanly_andInvertedTrimFallsBackSafely() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val wavProcessor = WavAudioProcessor(context)

            var threwIllegalState = false
            try {
                wavProcessor.exportSingleAudioFile(
                    mainPcm = ShortArray(0),
                    bgTrackConfig = null,
                    bgPcm = null,
                    settings = AudioEditSettings(),
                    outputFileName = "empty_test",
                    format = "WAV",
                    onProgress = {}
                )
            } catch (e: IllegalStateException) {
                threwIllegalState = true
            }
            assertTrue("Expected clean IllegalStateException when exporting empty audio", threwIllegalState)

            // Non-empty audio with out-of-range/inverted trim markers must not throw ArrayIndexOutOfBoundsException
            val nonEmpty = ShortArray(4410) { 8000 }
            val file = wavProcessor.exportSingleAudioFile(
                mainPcm = nonEmpty,
                bgTrackConfig = null,
                bgPcm = null,
                settings = AudioEditSettings(trimStartMs = 5000L, trimEndMs = 5000L),
                outputFileName = "fallback_trim_test",
                format = "WAV",
                onProgress = {}
            )
            assertTrue(file.exists() && file.length() > 44L)
            file.delete()
        }
    }

    @Test
    fun compressorWithPeakLimiterCeiling_respectsCustomCeiling() {
        val loudSignal = ShortArray(4096) { 28000 }
        val compressed = StudioAudioProcessor.applyCompressor(
            input = loudSignal,
            thresholdDb = -18.0f,
            ratio = 4.0f,
            makeupGainDb = 6.0f,
            peakLimiter = false
        )
        val limited = StudioAudioProcessor.applyTruePeakLimiter(compressed, ceilingDb = -6.0f)
        val peak = limited.maxOf { abs(it.toInt()) }
        assertTrue("Expected limited peak <= 16500 (~ -6 dBFS) but was $peak", peak <= 16500)
    }
}
