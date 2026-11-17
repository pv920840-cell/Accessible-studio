package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.audio.StudioAudioProcessor
import com.example.audio.WavAudioProcessor
import com.example.ui.components.AudioStudioCommand
import com.example.ui.components.StudioDawTopBar
import com.example.ui.components.WaveformViewportMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MuteAndDurationAndViewTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun multi_minute_decode_and_resample_preserves_full_6_minute_plus_duration() {
        // Simulate 375 seconds (6 min 15 sec) of 48 kHz stereo PCM split into chunks
        val sourceRate = 48000
        val totalSeconds = 375
        val chunkFrames = sourceRate * 15 // 15s stereo chunks
        val chunks = mutableListOf<ShortArray>()

        // Downmix a single 1-second stereo PCM16 byte chunk to verify stereo averaging
        val testFrames = 4800
        val byteBuf = ByteBuffer.allocate(testFrames * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until testFrames) {
            byteBuf.putShort(2000)
            byteBuf.putShort(4000)
        }
        val monoSampleChunk = WavAudioProcessor.downmixPcm16ToMono(
            pcmBytes = byteBuf.array(),
            byteCount = testFrames * 4,
            channelCount = 2
        )
        assertEquals(testFrames, monoSampleChunk.size)
        assertEquals(3000.toShort(), monoSampleChunk[0])

        // Assemble 25 chunks * 15s = 375s at 48 kHz -> resampled to 44.1 kHz mono
        repeat(totalSeconds / 15) {
            chunks.add(ShortArray(chunkFrames) { 1200 })
        }
        val targetPcm = WavAudioProcessor.assembleAndResampleMonoChunks(
            monoChunks = chunks,
            sourceSampleRate = sourceRate,
            targetSampleRate = 44100
        )

        val decodedDurationSec = targetPcm.size.toDouble() / 44100.0
        // Must be ~375s, definitely NOT truncated to 300s (5 minutes)!
        assertTrue("Expected > 360s, got $decodedDurationSec", decodedDurationSec > 370.0)
        assertEquals(375 * 44100, targetPcm.size)
    }

    @Test
    fun mute_selection_silences_only_selected_range_and_preserves_total_duration() {
        val sr = StudioAudioProcessor.SAMPLE_RATE
        val durationSec = 4
        val original = ShortArray(sr * durationSec) { 9000 }

        // Mute between 1000ms and 2500ms
        val muted = StudioAudioProcessor.muteSelection(original, startMs = 1000L, endMs = 2500L, sampleRate = sr)

        // Duration / total sample count must remain completely unchanged
        assertEquals(original.size, muted.size)

        // Before selection (e.g., 500ms) remains untouched
        assertEquals(9000.toShort(), muted[(0.5 * sr).toInt()])
        // Inside selection (e.g., 1500ms and 2400ms) is zeroed
        assertEquals(0.toShort(), muted[(1.5 * sr).toInt()])
        assertEquals(0.toShort(), muted[(2.4 * sr).toInt()])
        // After selection (e.g., 3000ms) remains untouched
        assertEquals(9000.toShort(), muted[(3.0 * sr).toInt()])
    }

    @Test
    fun waveform_viewport_math_resolves_windows_and_follow_cursor_correctly() {
        val durationMs = 600_000L // 10 minutes

        // Follow Cursor OFF keeps window anchored even when playhead moves outside
        val anchored = WaveformViewportMath.resolveWindow(
            durationMs = durationMs,
            viewStartMs = 60_000L,
            viewSpanMs = 10_000L,
            currentMs = 250_000L,
            followCursor = false
        )
        assertEquals(60_000L, anchored.startMs)
        assertEquals(10_000L, anchored.spanMs)

        // Follow Cursor ON recenters window around playhead when cursor exits view
        val following = WaveformViewportMath.resolveWindow(
            durationMs = durationMs,
            viewStartMs = 60_000L,
            viewSpanMs = 10_000L,
            currentMs = 250_000L,
            followCursor = true
        )
        assertEquals(245_000L, following.startMs)
        assertEquals(255_000L, following.endMs)
        assertEquals(60.0f, following.zoomFactor, 0.01f)

        // Format labels match scales
        assertEquals("All", WaveformViewportMath.formatWindowLabel(durationMs, durationMs))
        assertEquals("10.0s", WaveformViewportMath.formatWindowLabel(10_000L, durationMs))
        assertEquals("1.0m", WaveformViewportMath.formatWindowLabel(60_000L, durationMs))
    }

    @Test
    fun edit_and_view_dropdown_menus_expose_mute_selection_and_view_options() {
        val dispatched = mutableListOf<AudioStudioCommand>()

        composeRule.setContent {
            StudioDawTopBar(
                currentTab = StudioTab.AUDIO,
                isHighContrast = false,
                followCursor = true,
                onToggleHighContrast = {},
                onOpenAbout = {},
                onOpenSettings = {},
                onOpenHelp = {},
                onSelectTab = {},
                onOpenBatchProcessing = {},
                onAudioCommand = { dispatched.add(it) }
            )
        }
        composeRule.waitForIdle()

        // Open Edit menu & tap Mute Selection
        composeRule.onNodeWithTag("menu_bar_edit").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("menu_edit_mute_selection").assertExists().performClick()
        composeRule.waitForIdle()
        assertTrue(dispatched.contains(AudioStudioCommand.MuteSelection))

        // Open View menu & verify all requested View / Time Zoom / Go To / Amplitude Zoom entries exist
        composeRule.onNodeWithTag("menu_bar_view").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("menu_view_all").assertExists()
        composeRule.onNodeWithTag("menu_view_selection").assertExists()
        composeRule.onNodeWithTag("menu_view_specify").assertExists()
        composeRule.onNodeWithTag("menu_view_previous").assertExists()
        composeRule.onNodeWithTag("menu_view_preset").assertExists()
        composeRule.onNodeWithTag("menu_view_follow_cursor").assertExists()
        composeRule.onNodeWithTag("menu_view_zoom_10_1").assertExists()
        composeRule.onNodeWithTag("menu_view_zoom_1_1").assertExists()
        composeRule.onNodeWithTag("menu_view_1_minute").assertExists()
        composeRule.onNodeWithTag("menu_view_10_minutes").assertExists()
        composeRule.onNodeWithTag("menu_view_1_hour").assertExists()
        composeRule.onNodeWithTag("menu_view_goto_start").assertExists()
        composeRule.onNodeWithTag("menu_view_goto_finish").assertExists()
        composeRule.onNodeWithTag("menu_view_goto_beginning").assertExists()
        composeRule.onNodeWithTag("menu_view_goto_end").assertExists()
        composeRule.onNodeWithTag("menu_view_vzoom_in").assertExists()
        composeRule.onNodeWithTag("menu_view_vzoom_out").assertExists()
        composeRule.onNodeWithTag("menu_view_vzoom_all").assertExists().performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(dispatched.contains(AudioStudioCommand.VerticalZoomAll))
    }
}
