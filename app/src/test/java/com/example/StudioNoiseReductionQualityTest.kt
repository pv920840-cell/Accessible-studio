package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.audio.AudioEffectPresetsCatalog
import com.example.audio.AudioEditSettings
import com.example.audio.StudioAudioProcessor
import com.example.audio.StudioNoiseReducer
import com.example.audio.VocalIsolationPreset
import com.example.audio.VocalIsolationProcessor
import com.example.audio.WavAudioProcessor
import com.example.video.VideoAudioMixer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class StudioNoiseReductionQualityTest {

    private fun rmsRange(samples: ShortArray, start: Int, end: Int): Double {
        var sum = 0.0
        val safeStart = start.coerceIn(0, samples.size)
        val safeEnd = end.coerceIn(safeStart, samples.size)
        val count = (safeEnd - safeStart).coerceAtLeast(1)
        for (i in safeStart until safeEnd) {
            val v = samples[i].toDouble()
            sum += v * v
        }
        return sqrt(sum / count)
    }

    /**
     * DFT correlation energy at a specific frequency to measure exact signal harmonic preservation
     * without conflating broadband noise power.
     */
    private fun toneMagnitudeAtFreq(
        samples: ShortArray,
        start: Int,
        end: Int,
        freqHz: Double,
        sampleRate: Int
    ): Double {
        val safeStart = start.coerceIn(0, samples.size)
        val safeEnd = end.coerceIn(safeStart + 1, samples.size)
        val n = safeEnd - safeStart
        var re = 0.0
        var im = 0.0
        val omega = 2.0 * PI * freqHz / sampleRate.toDouble()
        for (k in 0 until n) {
            val s = samples[safeStart + k].toDouble()
            re += s * cos(omega * k)
            im -= s * sin(omega * k)
        }
        return (2.0 / n.toDouble()) * sqrt(re * re + im * im)
    }

    @Test
    fun wavNoiseReduction_cleansHissAndHumWhilePreservingHighFrequencyClarity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val wavProcessor = WavAudioProcessor(context)
        val sr = StudioAudioProcessor.SAMPLE_RATE
        val total = 3 * sr
        val rng = Random(12345)

        // Realistic vocal/speech + high-frequency sibilance/air + fan + 60Hz hum + hiss
        val input = ShortArray(total) { i ->
            val t = i.toDouble() / sr
            val hum60 = 1600.0 * sin(2.0 * PI * 60.0 * t) + 700.0 * sin(2.0 * PI * 120.0 * t)
            val hiss = (rng.nextDouble() * 2.0 - 1.0) * 1800.0
            val voice = if (i in sr until (2 * sr)) {
                7500.0 * sin(2.0 * PI * 240.0 * t) +
                    4500.0 * sin(2.0 * PI * 920.0 * t) +
                    2800.0 * sin(2.0 * PI * 5200.0 * t) +
                    1900.0 * sin(2.0 * PI * 7400.0 * t)
            } else {
                0.0
            }
            (voice + hum60 + hiss).toInt().coerceIn(-32767, 32767).toShort()
        }

        val inputPauseRms = rmsRange(input, sr / 5, (4 * sr) / 5)
        val inputHighAir5200 = toneMagnitudeAtFreq(input, sr + sr / 5, 2 * sr - sr / 5, 5200.0, sr)
        val inputHighAir7400 = toneMagnitudeAtFreq(input, sr + sr / 5, 2 * sr - sr / 5, 7400.0, sr)
        val inputFundamental240 = toneMagnitudeAtFreq(input, sr + sr / 5, 2 * sr - sr / 5, 240.0, sr)

        val light = wavProcessor.applyNoiseReduction(input, 0.35f)
        val balanced = wavProcessor.applyNoiseReduction(input, 0.60f)
        val strong = wavProcessor.applyNoiseReduction(input, 0.85f)

        assertEquals(input.size, light.size)
        assertEquals(input.size, balanced.size)
        assertEquals(input.size, strong.size)

        val lightPauseRms = rmsRange(light, sr / 5, (4 * sr) / 5)
        val balancedPauseRms = rmsRange(balanced, sr / 5, (4 * sr) / 5)
        val strongPauseRms = rmsRange(strong, sr / 5, (4 * sr) / 5)

        assertTrue("Light NR should meaningfully attenuate noise floor ($lightPauseRms vs $inputPauseRms)", lightPauseRms < inputPauseRms * 0.65)
        assertTrue("Balanced NR should clean further than Light ($balancedPauseRms vs $lightPauseRms)", balancedPauseRms < lightPauseRms)
        assertTrue("Strong NR should suppress noise more than Balanced ($strongPauseRms vs $balancedPauseRms)", strongPauseRms < balancedPauseRms)
        assertTrue("Strong NR should remove >75% of noise RMS ($strongPauseRms vs $inputPauseRms)", strongPauseRms < inputPauseRms * 0.25)

        // Verify high-frequency brilliance/sibilance (5.2kHz & 7.4kHz) and voice body (240Hz) are NOT muffled!
        val balFundamental240 = toneMagnitudeAtFreq(balanced, sr + sr / 5, 2 * sr - sr / 5, 240.0, sr)
        val balAir5200 = toneMagnitudeAtFreq(balanced, sr + sr / 5, 2 * sr - sr / 5, 5200.0, sr)
        val balAir7400 = toneMagnitudeAtFreq(balanced, sr + sr / 5, 2 * sr - sr / 5, 7400.0, sr)

        assertTrue("Voice body (240 Hz) must stay >82% intact (actual $balFundamental240 vs $inputFundamental240)", balFundamental240 > inputFundamental240 * 0.82)
        assertTrue("High presence (5.2 kHz) must stay >78% intact, not muffled (actual $balAir5200 vs $inputHighAir5200)", balAir5200 > inputHighAir5200 * 0.78)
        assertTrue("Upper air/sibilance (7.4 kHz) must stay >75% intact, not dull (actual $balAir7400 vs $inputHighAir7400)", balAir7400 > inputHighAir7400 * 0.75)
    }

    @Test
    fun continuousSpeechWithoutSilentPauses_doesNotOverGateOrHollowVoice() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val wavProcessor = WavAudioProcessor(context)
        val sr = 44100
        val total = 2 * sr
        val rng = Random(99)

        // Continuous non-stop singing/talking with gentle background noise
        val continuousVoice = ShortArray(total) { i ->
            val t = i.toDouble() / sr
            val voice = 6800.0 * sin(2.0 * PI * 310.0 * t) + 3400.0 * sin(2.0 * PI * 1400.0 * t)
            val mildNoise = (rng.nextDouble() * 2.0 - 1.0) * 600.0
            (voice + mildNoise).toInt().coerceIn(-32767, 32767).toShort()
        }

        val inRms = rmsRange(continuousVoice, sr / 4, (7 * sr) / 4)
        val processed = wavProcessor.applyNoiseReduction(continuousVoice, 0.70f)
        val outRms = rmsRange(processed, sr / 4, (7 * sr) / 4)

        assertTrue(
            "Continuous voice must never be hollowed/swallowed ($outRms vs $inRms)",
            outRms > inRms * 0.78
        )
    }

    @Test
    fun videoNoiseReducer_cleansRoomNoiseAndHumPreservingNaturalDialogue() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mixer = VideoAudioMixer(context)
        val sr = mixer.sampleRate
        val total = 3 * sr
        val rng = Random(77)

        val videoAudio = ShortArray(total) { i ->
            val t = i.toDouble() / sr
            val hum = 1400.0 * sin(2.0 * PI * 60.0 * t)
            val acNoise = (rng.nextDouble() * 2.0 - 1.0) * 1600.0
            val dialogue = if (i in sr until (2 * sr)) {
                8000.0 * sin(2.0 * PI * 260.0 * t) +
                    4200.0 * sin(2.0 * PI * 1800.0 * t) +
                    2200.0 * sin(2.0 * PI * 5600.0 * t)
            } else {
                0.0
            }
            (dialogue + hum + acNoise).toInt().coerceIn(-32767, 32767).toShort()
        }

        val cleaned = mixer.reduceVideoNoise(videoAudio, strength = 0.75f)
        assertEquals(videoAudio.size, cleaned.size)

        val inNoiseRms = rmsRange(videoAudio, sr / 5, (4 * sr) / 5)
        val outNoiseRms = rmsRange(cleaned, sr / 5, (4 * sr) / 5)
        val outAir5600 = toneMagnitudeAtFreq(cleaned, sr + sr / 5, 2 * sr - sr / 5, 5600.0, sr)
        val inAir5600 = toneMagnitudeAtFreq(videoAudio, sr + sr / 5, 2 * sr - sr / 5, 5600.0, sr)

        assertTrue("Video noise floor should be cleanly suppressed ($outNoiseRms vs $inNoiseRms)", outNoiseRms < inNoiseRms * 0.30)
        assertTrue("Video dialogue HF air (5.6kHz) should stay crisp ($outAir5600 vs $inAir5600)", outAir5600 > inAir5600 * 0.75)
    }

    @Test
    fun stereoAndMultiSampleRates_preserveChannelBalanceLengthsAndBoundaries() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val wavProcessor = WavAudioProcessor(context)
        val rng = Random(314)

        // 1. Stereo 48kHz interleaved buffer with panned L/R speech + stereo room hiss
        val sr48 = 48000
        val frames = sr48 * 2
        val stereo = ShortArray(frames * 2)
        for (i in 0 until frames) {
            val t = i.toDouble() / sr48
            val noiseL = (rng.nextDouble() * 2.0 - 1.0) * 1400.0
            val noiseR = (rng.nextDouble() * 2.0 - 1.0) * 1400.0
            val voice = if (i in (sr48 / 2) until (3 * sr48 / 2)) {
                6000.0 * sin(2.0 * PI * 440.0 * t) + 2500.0 * sin(2.0 * PI * 4800.0 * t)
            } else {
                0.0
            }
            stereo[i * 2] = (voice * 1.0 + noiseL).toInt().coerceIn(-32767, 32767).toShort()
            stereo[i * 2 + 1] = (voice * 0.5 + noiseR).toInt().coerceIn(-32767, 32767).toShort()
        }

        val cleanedStereo = wavProcessor.applyNoiseReduction(stereo, intensity = 0.70f, sampleRate = sr48, channels = 2)
        assertEquals(stereo.size, cleanedStereo.size)

        // Check stereo L:R ratio (~2:1) is preserved cleanly during speech without wandering
        val lSpeech = toneMagnitudeAtFreq(
            ShortArray(frames) { cleanedStereo[it * 2] },
            sr48 / 2 + 4000,
            3 * sr48 / 2 - 4000,
            440.0,
            sr48
        )
        val rSpeech = toneMagnitudeAtFreq(
            ShortArray(frames) { cleanedStereo[it * 2 + 1] },
            sr48 / 2 + 4000,
            3 * sr48 / 2 - 4000,
            440.0,
            sr48
        )
        val lrRatio = lSpeech / rSpeech.coerceAtLeast(1.0)
        assertTrue("Stereo L/R balance (~2.0) must remain stable (actual $lrRatio)", lrRatio in 1.75..2.25)

        // 2. 16kHz voice rate buffer
        val sr16 = 16000
        val voice16 = ShortArray(sr16 * 2) { i ->
            val t = i.toDouble() / sr16
            val hiss = (rng.nextDouble() * 2.0 - 1.0) * 1500.0
            val sig = if (i > sr16 / 2) 5500.0 * sin(2.0 * PI * 1200.0 * t) else 0.0
            (sig + hiss).toInt().coerceIn(-32767, 32767).toShort()
        }
        val cleaned16 = wavProcessor.processPcm(
            voice16,
            AudioEditSettings(noiseReductionEnabled = true, noiseReductionIntensity = 0.65f),
            sampleRate = sr16,
            channels = 1
        )
        assertEquals(voice16.size, cleaned16.size)
        val pause16In = rmsRange(voice16, 1000, sr16 / 2 - 1000)
        val pause16Out = rmsRange(cleaned16, 1000, sr16 / 2 - 1000)
        assertTrue("16kHz audio should have hiss suppressed ($pause16Out vs $pause16In)", pause16Out < pause16In * 0.40)

        // 3. Tiny / empty / all-zero edge boundaries
        assertEquals(0, StudioNoiseReducer.reduceNoise(ShortArray(0)).size)
        assertEquals(32, StudioNoiseReducer.reduceNoise(ShortArray(32) { 500 }).size)
        val zeroOut = StudioNoiseReducer.reduceNoise(ShortArray(4096))
        assertEquals(4096, zeroOut.size)
        assertEquals(0, zeroOut.maxOf { abs(it.toInt()) })
    }

    @Test
    fun allNoiseReductionPresets_inCatalogExecuteCleanly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val wavProcessor = WavAudioProcessor(context)
        val sr = StudioAudioProcessor.SAMPLE_RATE
        val rng = Random(42)
        val pcm = ShortArray(sr * 2) { i ->
            val t = i.toDouble() / sr
            val speech = if (i > sr) 7000.0 * sin(2.0 * PI * 400.0 * t) else 0.0
            val hiss = (rng.nextDouble() * 2.0 - 1.0) * 1200.0
            (speech + hiss).toInt().coerceIn(-32767, 32767).toShort()
        }

        val group = AudioEffectPresetsCatalog.groups.first { it.effectId == "noise_gate_reduction" }
        assertTrue(group.presets.size >= 8)
        for (preset in group.presets) {
            val out = preset.applyTransform(pcm, wavProcessor, 0L, 2000L)
            assertEquals("Preset ${preset.id} must preserve length", pcm.size, out.size)
            assertTrue("Preset ${preset.id} must preserve speech signal", rmsRange(out, sr + 4000, sr * 2 - 4000) > 1500.0)
        }
    }
}
