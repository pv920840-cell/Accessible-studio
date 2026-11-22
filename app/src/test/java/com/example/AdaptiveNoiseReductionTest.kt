package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AdaptiveSpectralDenoiser
import com.example.audio.AudioPlayerManager
import com.example.audio.RealtimeSpectralDenoiseProcessor
import com.example.audio.SpectralDenoiseParams
import com.example.audio.StudioAudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdaptiveNoiseReductionTest {

    private fun rmsRange(samples: ShortArray, start: Int, end: Int): Double {
        val s = start.coerceIn(0, samples.size)
        val e = end.coerceIn(s + 1, samples.size)
        var sum = 0.0
        for (i in s until e) {
            val v = samples[i].toDouble()
            sum += v * v
        }
        return sqrt(sum / (e - s).coerceAtLeast(1))
    }

    private fun toneMagnitude(samples: ShortArray, start: Int, end: Int, freqHz: Double, sr: Int): Double {
        val s = start.coerceIn(0, samples.size)
        val e = end.coerceIn(s + 1, samples.size)
        val n = e - s
        var re = 0.0
        var im = 0.0
        val w = 2.0 * PI * freqHz / sr.toDouble()
        for (k in 0 until n) {
            val v = samples[s + k].toDouble()
            re += v * cos(w * k)
            im -= v * sin(w * k)
        }
        return (2.0 / n.toDouble()) * sqrt(re * re + im * im)
    }

    @Test
    fun learnMode_captureNoiseProfileCleanlyIsolatesFanAndHissWithZeroClipping() {
        val sr = StudioAudioProcessor.SAMPLE_RATE
        val rng = Random(101)
        val total = 3 * sr

        val clip = ShortArray(total) { i ->
            val t = i.toDouble() / sr
            val fanAndHum = 1800.0 * sin(2.0 * PI * 92.0 * t) +
                1100.0 * sin(2.0 * PI * 860.0 * t) +
                900.0 * sin(2.0 * PI * 60.0 * t)
            val hiss = (rng.nextDouble() * 2.0 - 1.0) * 1600.0
            val speech = if (i in sr until (2 * sr)) {
                8800.0 * sin(2.0 * PI * 280.0 * t) +
                    4800.0 * sin(2.0 * PI * 1450.0 * t) +
                    2200.0 * sin(2.0 * PI * 4800.0 * t)
            } else {
                0.0
            }
            (speech + fanAndHum + hiss).toInt().coerceIn(-32767, 32767).toShort()
        }

        val noiseSelection = clip.copyOfRange(0, (0.75 * sr).toInt())
        val profile = AdaptiveSpectralDenoiser.captureNoiseProfile(noiseSelection, sr, fftSize = 2048)
        assertNotNull("Learn Mode should produce a multi-band spectral profile", profile)
        assertTrue(profile!!.bandSummary.isNotBlank())
        assertTrue(profile.noiseFloorDbfs < 0f)

        val cleaned = AdaptiveSpectralDenoiser.processOffline(
            samples = clip,
            sampleRate = sr,
            channels = 1,
            params = SpectralDenoiseParams(
                thresholdDb = -20f,
                reductionDb = 26f,
                smoothing = 0.72f,
                voiceDialogueFocus = true,
                fftSize = 2048
            ),
            noiseProfile = profile
        )

        assertEquals(clip.size, cleaned.size)
        val inNoiseRms = rmsRange(clip, sr / 5, (4 * sr) / 5)
        val outNoiseRms = rmsRange(cleaned, (2 * sr) + sr / 5, total - sr / 5)
        val inVoice1450 = toneMagnitude(clip, sr + sr / 5, 2 * sr - sr / 5, 1450.0, sr)
        val outVoice1450 = toneMagnitude(cleaned, sr + sr / 5, 2 * sr - sr / 5, 1450.0, sr)
        val peak = cleaned.maxOf { abs(it.toInt()) }

        assertTrue("Learned noise profile must deeply attenuate noise floor (in=$inNoiseRms out=$outNoiseRms)", outNoiseRms < inNoiseRms * 0.12)
        assertTrue("Vocal formant energy at 1450 Hz must stay well preserved (in=$inVoice1450 out=$outVoice1450)", outVoice1450 > inVoice1450 * 0.72)
        assertTrue("Output must never clip ($peak)", peak <= 32767)
    }

    @Test
    fun reductionDb_and_thresholdDb_scaleAttenuationMonotonically() {
        val sr = 44100
        val rng = Random(202)
        val noisy = ShortArray(sr * 2) { i ->
            val t = i.toDouble() / sr
            val speech = if (i > sr) 7500.0 * sin(2.0 * PI * 320.0 * t) else 0.0
            val hiss = (rng.nextDouble() * 2.0 - 1.0) * 1400.0
            (speech + hiss).toInt().coerceIn(-32767, 32767).toShort()
        }

        val bypassOut = AdaptiveSpectralDenoiser.processOffline(
            noisy,
            sr,
            params = SpectralDenoiseParams(reductionDb = 24f, bypass = true)
        )
        assertTrue("Bypass=true must return input samples unchanged", bypassOut.contentEquals(noisy))

        val zeroRed = AdaptiveSpectralDenoiser.processOffline(
            noisy,
            sr,
            params = SpectralDenoiseParams(thresholdDb = -28f, reductionDb = 0f)
        )
        val midRed = AdaptiveSpectralDenoiser.processOffline(
            noisy,
            sr,
            params = SpectralDenoiseParams(thresholdDb = -28f, reductionDb = 12f)
        )
        val highRed = AdaptiveSpectralDenoiser.processOffline(
            noisy,
            sr,
            params = SpectralDenoiseParams(thresholdDb = -18f, reductionDb = 28f)
        )

        val rmsZero = rmsRange(zeroRed, 2000, sr - 2000)
        val rmsMid = rmsRange(midRed, 2000, sr - 2000)
        val rmsHigh = rmsRange(highRed, 2000, sr - 2000)

        assertTrue("0 dB reduction should leave noise near original level", rmsZero > 650.0)
        assertTrue("12 dB reduction ($rmsMid) must attenuate more than 0 dB ($rmsZero)", rmsMid < rmsZero * 0.55)
        assertTrue("28 dB reduction ($rmsHigh) must attenuate more than 12 dB ($rmsMid)", rmsHigh < rmsMid * 0.65)
    }

    @Test
    fun fft4096HighPrecision_and_voiceFocusOffMusicModeWorkWithoutMuffling() {
        val sr = 44100
        val rng = Random(303)
        val total = 2 * sr

        // Instrumental high/low acoustic content + mild broadband hiss
        val music = ShortArray(total) { i ->
            val t = i.toDouble() / sr
            val sig = if (i > sr / 2) {
                3200.0 * sin(2.0 * PI * 72.0 * t) + // warm bass under 82Hz
                    4200.0 * sin(2.0 * PI * 880.0 * t) +
                    2400.0 * sin(2.0 * PI * 6800.0 * t)
            } else 0.0
            val hiss = (rng.nextDouble() * 2.0 - 1.0) * 750.0
            (sig + hiss).toInt().coerceIn(-32767, 32767).toShort()
        }

        val cleanedMusic = AdaptiveSpectralDenoiser.processOffline(
            samples = music,
            sampleRate = sr,
            channels = 1,
            params = SpectralDenoiseParams(
                thresholdDb = -36f,
                reductionDb = 14f,
                smoothing = 0.72f,
                voiceDialogueFocus = false,
                fftSize = 4096
            )
        )

        assertEquals(music.size, cleanedMusic.size)
        val inBass = toneMagnitude(music, sr, total - 2000, 72.0, sr)
        val outBass = toneMagnitude(cleanedMusic, sr, total - 2000, 72.0, sr)
        val outHi = toneMagnitude(cleanedMusic, sr, total - 2000, 6800.0, sr)
        val inHi = toneMagnitude(music, sr, total - 2000, 6800.0, sr)
        val pauseIn = rmsRange(music, 1000, sr / 2 - 1000)
        val pauseOut = rmsRange(cleanedMusic, 1000, sr / 2 - 1000)

        assertTrue("Hiss should drop cleanly in music mode ($pauseOut vs $pauseIn)", pauseOut < pauseIn * 0.45)
        assertTrue("VoiceFocus=false should preserve 72 Hz bass body ($outBass vs $inBass)", outBass > inBass * 0.65)
        assertTrue("HF overtones at 6.8 kHz should stay crisp ($outHi vs $inHi)", outHi > inHi * 0.70)
    }

    @Test
    fun realtimeStreamingProcessor_adaptsLiveAndTogglesBypassCleanly() {
        val sr = 44100
        val rng = Random(404)
        val chunkSize = 1024
        val total = sr * 2

        val source = ShortArray(total) { i ->
            val t = i.toDouble() / sr
            val hum = 1200.0 * sin(2.0 * PI * 60.0 * t)
            val hiss = (rng.nextDouble() * 2.0 - 1.0) * 1400.0
            (hum + hiss).toInt().coerceIn(-32767, 32767).toShort()
        }

        val proc = RealtimeSpectralDenoiseProcessor(sr, fftSize = 2048)
        proc.params = SpectralDenoiseParams(
            thresholdDb = -22f,
            reductionDb = 24f,
            smoothing = 0.68f,
            voiceDialogueFocus = true,
            adaptiveMode = true
        )

        val outActive = ShortArray(total)
        val tmp = ShortArray(chunkSize)
        var offset = 0
        while (offset < total) {
            val count = minOf(chunkSize, total - offset)
            proc.processChunk(source, offset, count, tmp)
            System.arraycopy(tmp, 0, outActive, offset, count)
            offset += count
        }

        val inRms = rmsRange(source, sr, total - chunkSize)
        val activeOutRms = rmsRange(outActive, sr, total - chunkSize)
        assertTrue(
            "Realtime streaming processor must adapt to live hiss+hum and suppress it cleanly (in=$inRms out=$activeOutRms)",
            activeOutRms < inRms * 0.35
        )

        // Now flip bypass=true mid-stream without resetting -> output should return cleanly to dry signal level
        proc.params = proc.params.copy(bypass = true)
        val outBypassed = ShortArray(sr)
        offset = 0
        while (offset < sr) {
            val count = minOf(chunkSize, sr - offset)
            proc.processChunk(source, offset, count, tmp)
            System.arraycopy(tmp, 0, outBypassed, offset, count)
            offset += count
        }
        val bypassedRms = rmsRange(outBypassed, sr / 2, sr - chunkSize)
        assertTrue(
            "Realtime bypass=true must smoothly return to dry signal energy ($bypassedRms vs $inRms)",
            bypassedRms > inRms * 0.80
        )

        // Verify AudioPlayerManager accepts realtime denoise controls safely
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = AudioPlayerManager(context)
        player.setRealtimeDenoiseParams(proc.params)
        player.setRealtimeNoiseProfile(null)
        player.setRealtimeDenoiseEnabled(true)
        player.setRealtimeDenoiseEnabled(false)
        player.release()
    }
}
