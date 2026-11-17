package com.example

import android.media.AudioFormat
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioPlayerManager
import com.example.audio.WavAudioProcessor
import com.example.ui.components.AudioOpenDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DualImportAndPlaybackFixTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun play_start_resolution_rewinds_from_track_end_and_preserves_mid_position() {
        val dur = 180_000L

        // Mid-track pause resumes cleanly from current position
        assertEquals(45_000L, AudioPlayerManager.resolvePlayStartMs(null, null, 45_000L, dur))

        // At or near end of track rewinds to 0L so Play always plays again
        assertEquals(0L, AudioPlayerManager.resolvePlayStartMs(null, null, 180_000L, dur))
        assertEquals(0L, AudioPlayerManager.resolvePlayStartMs(null, null, 179_970L, dur))
        assertEquals(0L, AudioPlayerManager.resolvePlayStartMs(180_000L, null, 180_000L, dur))

        // Collapsed selection range resets cleanly instead of returning silently
        assertEquals(0L, AudioPlayerManager.resolvePlayStartMs(5000L, 5000L, 5000L, dur))
    }

    @Test
    fun dual_pcm_track_loads_update_duration_and_position_cleanly() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = AudioPlayerManager(ctx)

        val track1 = ShortArray(44100 * 3) { 1000 }
        val track2 = ShortArray(44100 * 7) { 2000 }

        manager.loadPcmData(track1, 44100)
        assertTrue(manager.hasPcmData())
        assertEquals(3000L, manager.durationMs.value)
        assertEquals(0L, manager.currentPositionMs.value)

        // Open second file and load into player
        manager.loadPcmData(track2, 44100)
        assertTrue(manager.hasPcmData())
        assertEquals(7000L, manager.durationMs.value)
        assertEquals(0L, manager.currentPositionMs.value)

        // Switch back to first file tab
        manager.loadPcmData(track1, 44100)
        assertTrue(manager.hasPcmData())
        assertEquals(3000L, manager.durationMs.value)

        manager.release()
    }

    @Test
    fun direct_riff_wav_and_float_pcm_downmix_decode_accurately() {
        // Verify 32-bit float stereo downmix
        val floatBuf = ByteBuffer.allocate(2 * 4 * 2).order(ByteOrder.LITTLE_ENDIAN)
        floatBuf.putFloat(0.5f)
        floatBuf.putFloat(-0.5f) // avg 0.0 -> 0
        floatBuf.putFloat(0.5f)
        floatBuf.putFloat(0.5f)  // avg 0.5 -> ~16383
        val monoFloat = WavAudioProcessor.downmixPcmBytesToMono(
            pcmBytes = floatBuf.array(),
            byteCount = floatBuf.capacity(),
            channelCount = 2,
            pcmEncoding = AudioFormat.ENCODING_PCM_FLOAT
        )
        assertEquals(2, monoFloat.size)
        assertEquals(0.toShort(), monoFloat[0])
        assertTrue(monoFloat[1] > 16000)

        // Verify direct RIFF WAV file decode for 16-bit PCM
        val tmpWav = File.createTempFile("test_direct_", ".wav")
        try {
            val processor = WavAudioProcessor()
            val samples = ShortArray(44100 * 2) { 4000 }
            processor.writeWavFile(tmpWav, samples, 44100, 1)

            val decoded = WavAudioProcessor.tryDecodeRiffWavDirect(tmpWav, 44100)
            assertNotNull(decoded)
            assertEquals(samples.size, decoded!!.size)
            assertEquals(4000.toShort(), decoded[100])
        } finally {
            tmpWav.delete()
        }

        // Verify 32-bit IEEE float RIFF WAV file decode directly
        val tmpFloatWav = File.createTempFile("test_float_", ".wav")
        try {
            val frames = 22050 // 0.5s at 44100 Hz
            val dataBytes = frames * 4
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(36 + dataBytes)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16)
            header.putShort(3) // IEEE Float
            header.putShort(1) // Mono
            header.putInt(44100)
            header.putInt(44100 * 4)
            header.putShort(4)
            header.putShort(32)
            header.put("data".toByteArray())
            header.putInt(dataBytes)

            val pcmPayload = ByteBuffer.allocate(dataBytes).order(ByteOrder.LITTLE_ENDIAN)
            repeat(frames) { pcmPayload.putFloat(0.25f) }

            FileOutputStream(tmpFloatWav).use { fos ->
                fos.write(header.array())
                fos.write(pcmPayload.array())
            }

            val decodedFloat = WavAudioProcessor.tryDecodeRiffWavDirect(tmpFloatWav, 44100)
            assertNotNull(decodedFloat)
            assertEquals(frames, decodedFloat!!.size)
            assertTrue(decodedFloat[50] in 8000..8300)
        } finally {
            tmpFloatWav.delete()
        }
    }

    @Test
    fun open_audio_dialog_displays_multi_file_import_and_progress_banner() {
        composeRule.setContent {
            AudioOpenDialog(
                recentFiles = emptyList(),
                savedExports = emptyList(),
                openInNewTab = true,
                onOpenInNewTabChange = {},
                isImporting = true,
                importingMessage = "Decoding file 1 of 2: track1.mp3...",
                onImportMultipleFiles = {},
                onImportFromOs = {},
                onImportFile = {},
                onBrowseOsFolder = {},
                onOpenExportedFile = {},
                onClearRecent = {},
                onGoHome = {},
                onHelp = {},
                onDismiss = {}
            )
        }

        composeRule.onNodeWithTag("card_dialog_importing_audio").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_open_import_multiple").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_open_import_from_os").assertIsDisplayed()
    }
}
