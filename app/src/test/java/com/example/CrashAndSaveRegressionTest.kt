package com.example

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.audio.AudioEditSettings
import com.example.audio.AudioPlayerManager
import com.example.audio.StudioAudioProcessor
import com.example.audio.VocalIsolationPreset
import com.example.audio.VocalIsolationProcessor
import com.example.audio.WavAudioProcessor
import com.example.data.MediaRepository
import com.example.storage.StorageExportManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class CrashAndSaveRegressionTest {

    @Test
    fun noiseReduction_and_isolateSpokenAudio_handleMultiSecondAudioWithoutCrashOrOom() {
        val sampleRate = StudioAudioProcessor.SAMPLE_RATE
        val numSamples = sampleRate * 5 // 5 seconds (previously allocated large 2D FFT matrices)
        val input = ShortArray(numSamples) { i ->
            val t = i.toDouble() / sampleRate
            // Alternate quiet hum frames and active speech bursts
            val isSpeechBurst = (i / (sampleRate / 2)) % 2 == 1
            val hum = 600.0 * sin(2.0 * PI * 60.0 * t)
            val speech = if (isSpeechBurst) {
                7000.0 * sin(2.0 * PI * 280.0 * t) + 3000.0 * sin(2.0 * PI * 840.0 * t)
            } else {
                0.0
            }
            (hum + speech).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        for (preset in VocalIsolationPreset.values()) {
            val out = VocalIsolationProcessor.isolateSpokenAudio(input, preset, sampleRate)
            assertEquals("Output length must match input for $preset", numSamples, out.size)
            assertTrue("Expected audible speech preserved for $preset", out.maxOf { abs(it.toInt()) } > 500)
        }

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val wavProcessor = WavAudioProcessor(context)
        val nrProcessed = wavProcessor.processPcm(
            input,
            AudioEditSettings(
                noiseReductionEnabled = true,
                noiseReductionIntensity = 0.85f
            )
        )
        assertEquals(numSamples, nrProcessed.size)
        assertTrue(nrProcessed.maxOf { abs(it.toInt()) } > 200)
    }

    @Test
    fun storageExportManager_saveAudioAndVideo_returnsReadableNonEmptyFileOnDuplicate() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val srcAudio = File(context.cacheDir, "crash_fix_src_audio.wav").apply {
            writeBytes(ByteArray(2048) { (it % 127).toByte() })
        }
        val srcVideo = File(context.cacheDir, "crash_fix_src_video.mp4").apply {
            writeBytes(ByteArray(4096) { (it % 97).toByte() })
        }

        try {
            val savedAudio1 = StorageExportManager.saveAudioFile(
                context = context,
                sourceFile = srcAudio,
                targetFileName = "DuplicateTestAudio.wav",
                mimeType = "audio/wav"
            )
            assertTrue("First saved audio must exist", savedAudio1.exists())
            assertTrue("First saved audio must be readable and non-empty", savedAudio1.canRead() && savedAudio1.length() > 0L)

            val savedAudio2 = StorageExportManager.saveAudioFile(
                context = context,
                sourceFile = srcAudio,
                targetFileName = "DuplicateTestAudio.wav",
                mimeType = "audio/wav"
            )
            assertTrue("Second saved audio must exist", savedAudio2.exists())
            assertTrue("Second saved audio must be readable and non-empty", savedAudio2.canRead() && savedAudio2.length() > 0L)

            val savedVideo1 = StorageExportManager.saveVideoFile(
                context = context,
                sourceFile = srcVideo,
                targetFileName = "DuplicateTestVideo.mp4",
                mimeType = "video/mp4"
            )
            assertTrue("Saved video must exist", savedVideo1.exists())
            assertTrue("Saved video must be readable and non-empty", savedVideo1.canRead() && savedVideo1.length() > 0L)

            savedAudio1.delete()
            savedAudio2.delete()
            savedVideo1.delete()
        } finally {
            srcAudio.delete()
            srcVideo.delete()
        }
    }

    @Test
    fun mediaRepository_refreshExports_handlesAppPrivateExportsDirectorySafely() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val repo = MediaRepository(context)
            repo.refreshExports()
            // Should complete cleanly without throwing on Robolectric or Realme-style scoped storage
            assertTrue(repo.exportedFiles.value.size >= 0)
        }
    }

    @Test
    fun audioPlayerManager_rapidLoadAndStop_doesNotThrow() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val player = AudioPlayerManager(context)
        try {
            val samples = ShortArray(4410) { i -> (5000 * sin(2.0 * PI * 440.0 * i / 44100.0)).toInt().toShort() }
            player.loadPcmData(samples, 44100)
            player.stop()
            player.loadPcmData(samples.copyOf(2205), 44100)
            player.seekTo(25L)
            player.pause()
            player.stop()
        } finally {
            player.release()
        }
    }
}
