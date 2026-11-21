package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioEffectsProEngine
import com.example.audio.AudioPlayerManager
import com.example.audio.AudioRecorderManager
import com.example.audio.StudioAudioProcessor
import com.example.audio.WavAudioProcessor
import com.example.data.MediaRepository
import com.example.ui.components.StudioMixAudioDialog
import com.example.ui.studio.AudioFeatureTool
import com.example.ui.studio.AudioFeatureWorkspaceScreen
import com.example.ui.studio.AudioProjectSessionState
import com.example.ui.theme.AccessibleStudioTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MixSyncTimingTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mixAudioTracks_positiveAndNegativeStartMs_shiftOverlayAccurately() {
        val sr = 10000 // 10 samples per millisecond for exact index math
        val main = ShortArray(20000) { 0 }
        val overlay = ShortArray(10000) { i ->
            if (i < 500) 1000.toShort() else 12000.toShort()
        }

        // 1. Positive startMs = +400 ms -> delayed to sample 4000
        val delayed = StudioAudioProcessor.mixAudioTracks(
            main = main,
            overlay = overlay,
            startMs = 400L,
            mainVolume = 1.0f,
            overlayVolume = 1.0f,
            applyPeakLimiter = false,
            sampleRate = sr
        )
        assertEquals(0, delayed[3999].toInt())
        assertEquals(1000, delayed[4000].toInt())
        assertEquals(12000, delayed[4500].toInt())

        // 2. Negative startMs = -50 ms (-500 samples) -> skips first 500 intro samples immediately at index 0
        val advanced = StudioAudioProcessor.mixAudioTracks(
            main = main,
            overlay = overlay,
            startMs = -50L,
            mainVolume = 1.0f,
            overlayVolume = 1.0f,
            applyPeakLimiter = false,
            sampleRate = sr
        )
        // Entry taper prevents sudden click at index 0 (5ms = 50 samples), then reaches 12000 right away!
        assertEquals(0, advanced[0].toInt())
        assertTrue("After 5ms fade-in, sample should match post-intro 12000", abs(advanced[100].toInt() - 12000) <= 5)

        // 3. Edge case: negative shift exceeding overlay length should never crash and preserve main track
        val mainVoice = ShortArray(5000) { 4000 }
        val overflowShift = StudioAudioProcessor.mixAudioTracks(
            main = mainVoice,
            overlay = overlay,
            startMs = -2000L, // -20,000 samples > overlay length 10,000
            mainVolume = 1.0f,
            overlayVolume = 1.0f,
            applyPeakLimiter = false,
            sampleRate = sr
        )
        assertEquals(mainVoice.size, overflowShift.size)
        assertEquals(4000, overflowShift[2500].toInt())

        // 4. Empty edge case
        assertEquals(0, StudioAudioProcessor.mixAudioTracks(ShortArray(0), ShortArray(0)).size)
    }

    @Test
    fun resamplePcmLinear_keepsTimeDurationAlignedAcrossRates() {
        val sr48 = 48000
        val sr44 = 44100
        val durSec = 1.0
        val src48 = ShortArray((sr48 * durSec).toInt()) { i ->
            (10000.0 * sin(2.0 * PI * 440.0 * i / sr48)).toInt().toShort()
        }

        val resampled44 = StudioAudioProcessor.resamplePcmLinear(src48, sr48, sr44)
        assertEquals(sr44, resampled44.size)

        val resampled16 = StudioAudioProcessor.resamplePcmLinear(resampled44, sr44, 16000)
        assertEquals(16000, resampled16.size)

        assertEquals(0, StudioAudioProcessor.resamplePcmLinear(ShortArray(0), sr48, sr44).size)
        assertEquals(1, StudioAudioProcessor.resamplePcmLinear(ShortArray(1) { 500 }, sr48, sr44).size)
    }

    @Test
    fun voiceOverAndSmartMusicBed_respectSignedSyncOffsets() {
        val sr = 10000
        val main = ShortArray(20000) { 2000 }
        val voice = ShortArray(10000) { i ->
            if (i < 1000) 0 else 10000
        }

        // Negative insertAtMs (-100ms = skip first 1000 samples) puts active narration right near start
        val voResult = AudioEffectsProEngine.applyVoiceOverDuck(
            mainTrack = main,
            voiceClip = voice,
            insertAtMs = -100L,
            musicDuckFactor = 0.25f,
            voiceGain = 1.0f,
            sampleRate = sr
        )
        assertTrue("Active voice should start after skipped 100ms intro", voResult[2500] > 8000)

        // SmartMusicBedAutoDuck delays music when startOffsetMs > 0
        val pureVoice = ShortArray(20000) { 0 }
        val music = ShortArray(10000) { 10000 }
        val duckMix = AudioEffectsProEngine.applySmartMusicBedAutoDuck(
            voiceTrack = pureVoice,
            musicPcm = music,
            duckLevel = 0.2f,
            voiceGain = 1.0f,
            musicBaseVolume = 0.8f,
            autoDuckEnabled = false,
            loopMusic = false,
            fadeOutTailMs = 0L,
            sampleRate = sr,
            startOffsetMs = 500L // delayed 500ms = 5000 samples
        )
        assertEquals(0, duckMix[2000].toInt())
        assertTrue("Delayed music should enter cleanly after 500ms offset", duckMix[6000] > 7000)
    }

    @Test
    fun studioMixAudioDialog_increaseDecreaseResetPreviewAndConfirmPassExactSignedStartMs() {
        val clipboard = ShortArray(44100) { 5000 }
        var previewCalls = 0
        var lastPreviewStartMs: Long? = null
        var stopPreviewCalls = 0
        var confirmedStartMs: Long? = null
        var isPreviewPlayingState = false

        composeRule.setContent {
            AccessibleStudioTheme(isHighContrast = false) {
                StudioMixAudioDialog(
                    currentTrackTitle = "Podcast Main",
                    clipboardPcm = clipboard,
                    otherProjects = emptyList(),
                    pickedMixTitle = null,
                    pickedMixPcm = null,
                    sampleRate = 44100,
                    currentCursorMs = 2000L,
                    isPreviewPlaying = isPreviewPlayingState,
                    onPreviewMix = { _, startMs, _, _, _ ->
                        previewCalls++
                        lastPreviewStartMs = startMs
                        isPreviewPlayingState = true
                    },
                    onStopPreview = {
                        stopPreviewCalls++
                        isPreviewPlayingState = false
                    },
                    onPickFileToMix = {},
                    onConfirmMix = { _, _, startMs, _, _, _ ->
                        confirmedStartMs = startMs
                    },
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Default step is 250ms; choose 1s chip and press Increase (+1000ms)
        composeRule.onNodeWithTag("chip_mix_step_1000").performScrollTo().performClick()
        composeRule.onNodeWithTag("btn_mix_time_increase").performScrollTo().performClick()
        composeRule.waitForIdle()

        // Switch step to 50ms and press Decrease 3 times (+1000 - 150 = +850ms)
        composeRule.onNodeWithTag("chip_mix_step_50").performScrollTo().performClick()
        repeat(3) {
            composeRule.onNodeWithTag("btn_mix_time_decrease").performScrollTo().performClick()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("text_mix_sync_offset_value")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("+0.85 s (+850 ms", substring = true)

        // Reset back to 0 ms
        composeRule.onNodeWithTag("btn_mix_time_reset").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("text_mix_sync_offset_value")
            .assertTextContains("0.00 s (0 ms", substring = true)

        // Switch step to 500ms and decrease once (-500ms), then switch start base to Cursor (2000ms) => 1500ms
        composeRule.onNodeWithTag("chip_mix_step_500").performScrollTo().performClick()
        composeRule.onNodeWithTag("btn_mix_time_decrease").performScrollTo().performClick()
        composeRule.onNodeWithTag("chip_mix_pos_cursor").performScrollTo().performClick()
        composeRule.waitForIdle()

        // Preview Mix Sync should pass exact signed effectiveStartMs = 2000 - 500 = 1500ms
        composeRule.onNodeWithTag("btn_mix_preview_sync").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(1, previewCalls)
        assertEquals(1500L, lastPreviewStartMs)

        // Confirm Mix Audio should pass exact effectiveStartMs = 1500ms
        composeRule.onNodeWithTag("btn_confirm_mix_audio").performClick()
        composeRule.waitForIdle()
        assertNotNull(confirmedStartMs)
        assertEquals(1500L, confirmedStartMs)
    }

    @Test
    fun audioFeatureWorkspaceMixTracks_displaysIncreaseDecreaseSyncControlsAndUpdatesState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val audioProcessor = WavAudioProcessor(context)
        val playerManager = AudioPlayerManager(context)
        val recorderManager = AudioRecorderManager(context)
        val mediaRepo = MediaRepository(context)
        val session = AudioProjectSessionState(
            projectId = "test_mix_sync_ws",
            title = "Voice Recording",
            pcm = ShortArray(44100 * 3) { 3000 },
            sampleRate = 44100
        )

        try {
            composeRule.setContent {
                AccessibleStudioTheme(isHighContrast = false) {
                    AudioFeatureWorkspaceScreen(
                        tool = AudioFeatureTool.MIX_TRACKS,
                        session = session,
                        audioProcessor = audioProcessor,
                        playerManager = playerManager,
                        recorderManager = recorderManager,
                        mediaRepository = mediaRepo,
                        hapticsEnabled = false,
                        isHighContrast = false,
                        defaultExportFormat = "WAV",
                        followCursor = true,
                        onToggleHighContrast = {},
                        onOpenAbout = {},
                        onOpenSettings = {},
                        onOpenHelp = {},
                        onOpenWhatsNew = {},
                        onOpenBatchProcessing = {},
                        onSaveAndReturn = {},
                        onCancelBack = {}
                    )
                }
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("chip_mix_step_1000").performScrollTo().performClick()
            composeRule.onNodeWithTag("btn_mix_time_increase").performScrollTo().performClick()
            composeRule.onNodeWithTag("btn_mix_time_increase").performScrollTo().performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("text_mix_sync_offset_value")
                .performScrollTo()
                .assertTextContains("+2.00 s (+2000 ms", substring = true)

            composeRule.onNodeWithTag("chip_mix_step_250").performScrollTo().performClick()
            composeRule.onNodeWithTag("btn_mix_time_decrease").performScrollTo().performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("text_mix_sync_offset_value")
                .performScrollTo()
                .assertTextContains("+1.75 s (+1750 ms", substring = true)

            composeRule.onNodeWithTag("btn_workspace_mix_preview").performScrollTo().assertExists()
            composeRule.onNodeWithTag("slider_mix_offset").performScrollTo().assertExists()
        } finally {
            playerManager.release()
        }
    }
}
