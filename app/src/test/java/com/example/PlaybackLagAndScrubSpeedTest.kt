package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioPlayerManager
import com.example.audio.AudioRecorderManager
import com.example.audio.WavAudioProcessor
import com.example.data.MediaRepository
import com.example.data.StudioPreferences
import com.example.data.StudioSettingsState
import com.example.ui.components.AccessibleTimeReadout
import com.example.ui.components.AccessibleTransportControls
import com.example.ui.components.VideoTimelineScrubber
import com.example.ui.studio.AudioFeatureTool
import com.example.ui.studio.AudioFeatureWorkspaceScreen
import com.example.ui.studio.AudioProjectSessionState
import com.example.ui.theme.AccessibleStudioTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaybackLagAndScrubSpeedTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun audioPlayerManager_setScrubSpeed_clampsWithinSupportedBounds() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = AudioPlayerManager(context)
        try {
            assertEquals(3.5f, player.scrubSpeedMultiplier, 0.01f)

            player.setScrubSpeed(5.0f)
            assertEquals(5.0f, player.scrubSpeedMultiplier, 0.01f)

            player.setScrubSpeed(0.5f)
            assertEquals(0.5f, player.scrubSpeedMultiplier, 0.01f)

            player.setScrubSpeed(0.25f)
            assertEquals(0.25f, player.scrubSpeedMultiplier, 0.01f)

            player.setScrubSpeed(0.1f)
            assertEquals(0.25f, player.scrubSpeedMultiplier, 0.01f)

            player.setScrubSpeed(25.0f)
            assertEquals(8.0f, player.scrubSpeedMultiplier, 0.01f)
        } finally {
            player.release()
        }
    }

    @Test
    fun studioPreferences_persistsAudioScrubSpeedAcrossLoads() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        StudioPreferences.saveSettings(context, StudioSettingsState(audioScrubSpeed = 3.5f))
        assertEquals(3.5f, StudioPreferences.loadSettings(context).audioScrubSpeed, 0.01f)

        StudioPreferences.saveAudioScrubSpeed(context, 0.25f)
        assertEquals(0.25f, StudioPreferences.loadSettings(context).audioScrubSpeed, 0.01f)

        StudioPreferences.saveAudioScrubSpeed(context, 0.75f)
        assertEquals(0.75f, StudioPreferences.loadSettings(context).audioScrubSpeed, 0.01f)

        StudioPreferences.saveAudioScrubSpeed(context, 8.0f)
        assertEquals(8.0f, StudioPreferences.loadSettings(context).audioScrubSpeed, 0.01f)

        StudioPreferences.saveAudioScrubSpeed(context, 1.5f)
        assertEquals(1.5f, StudioPreferences.loadSettings(context).audioScrubSpeed, 0.01f)
    }

    @Test
    fun accessibleTransportControls_allowsChangingRewindForwardSpeed_andUpdatesSpokenDescriptions() {
        var callbackSpeed = 0f
        composeRule.setContent {
            AccessibleStudioTheme {
                var speed by remember { mutableFloatStateOf(3.5f) }
                AccessibleTransportControls(
                    isPlaying = false,
                    onPlayPause = {},
                    onNudge = {},
                    onJumpStart = {},
                    onJumpEnd = {},
                    scrubSpeed = speed,
                    onChangeScrubSpeed = { next ->
                        speed = next
                        callbackSpeed = next
                    }
                )
            }
        }

        composeRule.onNodeWithTag("row_scrub_speed_selector").assertIsDisplayed()
        composeRule.onNodeWithTag("chip_scrub_speed_0.25x").assertIsDisplayed()
        composeRule.onNodeWithTag("chip_scrub_speed_0.5x").assertIsDisplayed()
        composeRule.onNodeWithTag("chip_scrub_speed_0.75x").assertIsDisplayed()
        composeRule.onNodeWithTag("chip_scrub_speed_1.0x").assertIsDisplayed()
        composeRule.onNodeWithTag("chip_scrub_speed_1.5x").assertIsDisplayed()
        composeRule.onNodeWithTag("chip_scrub_speed_3.5x").assertIsDisplayed()
        composeRule.onNodeWithTag("chip_scrub_speed_8.0x").assertIsDisplayed()

        composeRule.onNodeWithTag("chip_scrub_speed_0.25x").performClick()
        assertEquals(0.25f, callbackSpeed, 0.01f)

        composeRule.onNodeWithTag("btn_rewind_5s").assert(
            SemanticsMatcher("mentions 0.25x") { node ->
                val desc = node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                    .joinToString(" ")
                desc.contains("0.25x")
            }
        )

        composeRule.onNodeWithTag("chip_scrub_speed_8.0x").performClick()
        assertEquals(8.0f, callbackSpeed, 0.01f)

        composeRule.onNodeWithTag("btn_rewind_5s").assert(
            SemanticsMatcher("mentions 8.0x") { node ->
                val desc = node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                    .joinToString(" ")
                desc.contains("8.0x")
            }
        )
        composeRule.onNodeWithTag("btn_forward_5s").assert(
            SemanticsMatcher("mentions 8.0x") { node ->
                val desc = node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                    .joinToString(" ")
                desc.contains("8.0x")
            }
        )
    }

    @Test
    fun audioFeatureWorkspace_scrubSpeedChips_updatePlayerAndPersistPreference() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        StudioPreferences.saveAudioScrubSpeed(context, 3.5f)
        val player = AudioPlayerManager(context)
        val recorder = AudioRecorderManager(context)
        val processor = WavAudioProcessor(context)
        val mediaRepo = MediaRepository(context)
        val samplePcm = ShortArray(44100) { 100 }
        val session = AudioProjectSessionState(
            projectId = "test_scrub_ws",
            title = "Speed Test Track",
            baseFileName = "SpeedTest",
            pcm = samplePcm,
            sampleRate = 44100,
            trimStartMs = 0L,
            trimEndMs = 1000L
        )

        try {
            composeRule.setContent {
                AccessibleStudioTheme {
                    AudioFeatureWorkspaceScreen(
                        tool = AudioFeatureTool.EFFECTS_CLEANING,
                        session = session,
                        audioProcessor = processor,
                        playerManager = player,
                        recorderManager = recorder,
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
                        onCancelBack = {},
                        recordingSettings = StudioPreferences.loadSettings(context)
                    )
                }
            }

            composeRule.onNodeWithTag("btn_workspace_rewind_scrub").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("btn_workspace_forward_scrub").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("chip_workspace_scrub_speed_0.5x").performScrollTo().performClick()
            composeRule.waitForIdle()

            assertEquals(0.5f, player.scrubSpeedMultiplier, 0.01f)
            assertEquals(0.5f, StudioPreferences.loadSettings(context).audioScrubSpeed, 0.01f)

            composeRule.onNodeWithTag("chip_workspace_scrub_speed_5.0x").performScrollTo().performClick()
            composeRule.waitForIdle()

            assertEquals(5.0f, player.scrubSpeedMultiplier, 0.01f)
            assertEquals(5.0f, StudioPreferences.loadSettings(context).audioScrubSpeed, 0.01f)
        } finally {
            player.release()
        }
    }

    @Test
    fun talkBackFloodSuppression_suppressesLiveTimestampChurnDuringPlayback_andRestoresOnPause() {
        val posFlow = MutableStateFlow(15_000L)
        val playingState = mutableStateOf(true)

        composeRule.setContent {
            AccessibleStudioTheme {
                AccessibleTimeReadout(
                    currentMs = 0L,
                    durationMs = 60_000L,
                    label = "Playhead",
                    isPlaying = playingState.value,
                    positionFlow = posFlow
                )
                VideoTimelineScrubber(
                    currentPositionMs = 0L,
                    durationMs = 60_000L,
                    trimStartMs = 0L,
                    trimEndMs = 60_000L,
                    cutSegments = emptyList(),
                    onSeekTo = {},
                    isPlaying = playingState.value,
                    positionFlow = posFlow
                )
            }
        }

        // While playing: contentDescription stays static even as positionFlow advances
        posFlow.value = 28_000L
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("time_readout_card").assert(
            SemanticsMatcher("static playing semantics without 28 seconds") { node ->
                val desc = node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                    .joinToString(" ")
                desc.contains("playing") && !desc.contains("28")
            }
        )
        composeRule.onNodeWithTag("slider_video_timeline_scrubber").assert(
            SemanticsMatcher("no ProgressBarRangeInfo during playback") { node ->
                val hasRange = SemanticsProperties.ProgressBarRangeInfo in node.config
                val desc = node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                    .joinToString(" ")
                !hasRange && desc.contains("Pause video")
            }
        )

        // When paused: exact spoken playhead timestamp and Slider range semantics return immediately
        playingState.value = false
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("time_readout_card").assert(
            SemanticsMatcher("contains 28 seconds when paused") { node ->
                val desc = node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                    .joinToString(" ")
                desc.contains("28 seconds")
            }
        )
        composeRule.onNodeWithTag("slider_video_timeline_scrubber").assert(
            SemanticsMatcher("restores ProgressBarRangeInfo when paused") { node ->
                SemanticsProperties.ProgressBarRangeInfo in node.config
            }
        )
    }
}
