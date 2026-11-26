package com.example

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.example.audio.SpectralDenoiseParams
import com.example.ui.components.AccessibilitySettingsSheet
import com.example.ui.components.AccessiblePreviewPlaybackSlider
import com.example.ui.components.AdaptiveNoiseReductionPanel
import com.example.ui.components.EchoEffectCard
import com.example.ui.components.FeatureGuideTopic
import com.example.ui.components.MusicRebalancePanel
import com.example.ui.components.SingleFeatureGuideDialog
import com.example.ui.components.VideoAutoSplitterPanel
import com.example.ui.components.VideoCutterSection
import com.example.ui.components.VideoDeletePartPanel
import com.example.ui.components.VideoMultiDeletePanel
import com.example.ui.components.VideoMultiTrimPanel
import com.example.ui.components.VideoSplitterPanel
import com.example.ui.components.VideoTrimmerPrecisionPanel
import com.example.ui.theme.AccessibleStudioTheme
import com.example.video.AutoSplitMode
import com.example.video.VideoCutSegment
import com.example.video.VideoPlayerManager
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
class StudioWorkflowAndPerFeatureGuideTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsSheet_displaysSavedProjectsAndWorkflowLocationDetails() {
        composeRule.setContent {
            AccessibleStudioTheme(isHighContrast = false) {
                AccessibilitySettingsSheet(
                    isHighContrast = false,
                    isHapticsEnabled = true,
                    defaultExportFormat = "WAV",
                    onSaveSettings = { _, _, _ -> },
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("card_saved_projects_location")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Existing Projects", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("studio_projects", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Exports tab", substring = true).assertIsDisplayed()
    }

    @Test
    fun previewPlaybackSlider_coversFullFiveMinuteAndThirtyMinuteDurationsWithoutOneMinuteCap() {
        val fiveMinMs = 300_000L
        var toggleCount = 0
        var lastPulsePos = -1L

        composeRule.setContent {
            var posMs by remember { mutableLongStateOf(0L) }
            var durationMs by remember { mutableLongStateOf(fiveMinMs) }
            var playing by remember { mutableStateOf(false) }

            AccessibleStudioTheme(isHighContrast = false) {
                Column {
                    AccessiblePreviewPlaybackSlider(
                        currentPositionMs = posMs,
                        durationMs = durationMs,
                        onSeekTo = { posMs = it },
                        testTag = "slider_long_preview",
                        isPlaying = playing,
                        onTogglePlayPause = {
                            playing = !playing
                            toggleCount++
                        },
                        onScrubPreviewPulse = { lastPulsePos = it }
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Seek via Semantics SetProgress beyond 1 minute (e.g., 4:00 = 240,000 ms)
        composeRule.onNodeWithTag("slider_long_preview")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(240_000f)
            }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("slider_long_preview_readout")
            .assertTextContains("04:00", substring = true)
            .assertTextContains("05:00", substring = true)

        // Swipe UP moves forward toward end and fires preview pulse beyond 60s
        composeRule.onNodeWithTag("slider_long_preview_container").performTouchInput {
            down(center)
            moveBy(Offset(0f, -80f))
            up()
        }
        composeRule.waitForIdle()
        assertTrue("Expected forward scrub pulse past 240,000ms, got $lastPulsePos", lastPulsePos > 240_000L)

        // Swipe DOWN moves backward toward start
        val posAfterUp = lastPulsePos
        composeRule.onNodeWithTag("slider_long_preview_container").performTouchInput {
            down(center)
            moveBy(Offset(0f, 80f))
            up()
        }
        composeRule.waitForIdle()
        assertTrue("Expected backward scrub pulse < $posAfterUp, got $lastPulsePos", lastPulsePos < posAfterUp)

        // Double tap toggles Play / Pause twice cleanly
        composeRule.onNodeWithTag("slider_long_preview").performTouchInput { doubleClick(center) }
        composeRule.waitForIdle()
        assertEquals(1, toggleCount)
        composeRule.onNodeWithTag("slider_long_preview").performTouchInput { doubleClick(center) }
        composeRule.waitForIdle()
        assertEquals(2, toggleCount)

        // VideoPlayerManager with duration hint >= 5 min also allows seeking beyond 60,000 ms pre-prepare
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = VideoPlayerManager(context)
        player.updateDurationHint(1_800_000L) // 30 min
        player.seekTo(900_000L) // 15 min
        assertEquals(1_800_000L, player.durationMs.value)
        assertEquals(900_000L, player.currentPositionMs.value)
        player.release()
    }

    @Test
    fun moveNowPlayingButtons_readLivePositionProviderAndUpdateStartFinishAndPointMarkers() {
        val durationMs = 300_000L // 5 min video
        var livePlaybackPos = 42_000L

        composeRule.setContent {
            var trimStart by remember { mutableLongStateOf(0L) }
            var trimEnd by remember { mutableLongStateOf(durationMs) }
            var multiClips by remember { mutableStateOf<List<VideoCutSegment>>(emptyList()) }
            var deletePartSegs by remember { mutableStateOf(listOf(VideoCutSegment(startMs = 0L, endMs = 100_000L))) }
            var multiDeleteSegs by remember { mutableStateOf<List<VideoCutSegment>>(emptyList()) }
            var splitPoints by remember { mutableStateOf<List<Long>>(emptyList()) }
            var autoStart by remember { mutableLongStateOf(0L) }
            var autoEnd by remember { mutableLongStateOf(durationMs) }

            AccessibleStudioTheme(isHighContrast = false) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    // Intentionally pass stale currentPositionMs = 0L while provider has live playback pos
                    VideoTrimmerPrecisionPanel(
                        durationMs = durationMs,
                        currentPositionMs = 0L,
                        currentPositionMsProvider = { livePlaybackPos },
                        trimStartMs = trimStart,
                        trimEndMs = trimEnd,
                        streamCopyTrim = false,
                        onUpdateTrim = { s, e, _ ->
                            trimStart = s
                            trimEnd = e
                        },
                        onPlaySelection = { _, _ -> }
                    )
                    VideoMultiTrimPanel(
                        durationMs = durationMs,
                        currentPositionMs = 0L,
                        currentPositionMsProvider = { livePlaybackPos },
                        multiTrimClips = multiClips,
                        onUpdateMultiTrimClips = { multiClips = it },
                        onPlaySelection = { _, _ -> }
                    )
                    VideoDeletePartPanel(
                        durationMs = durationMs,
                        currentPositionMs = 0L,
                        currentPositionMsProvider = { livePlaybackPos },
                        cutSegments = deletePartSegs,
                        onUpdateCutSegments = { deletePartSegs = it },
                        onPlaySelection = { _, _ -> }
                    )
                    VideoMultiDeletePanel(
                        durationMs = durationMs,
                        currentPositionMs = 0L,
                        currentPositionMsProvider = { livePlaybackPos },
                        cutSegments = multiDeleteSegs,
                        onUpdateCutSegments = { multiDeleteSegs = it },
                        onPlaySelection = { _, _ -> }
                    )
                    VideoSplitterPanel(
                        durationMs = durationMs,
                        currentPositionMs = 0L,
                        currentPositionMsProvider = { livePlaybackPos },
                        trimStartMs = trimStart,
                        trimEndMs = trimEnd,
                        splitPointsMs = splitPoints,
                        onUpdateSplitPoints = { splitPoints = it },
                        onPlayFrom = {}
                    )
                    VideoAutoSplitterPanel(
                        durationMs = durationMs,
                        currentPositionMs = 0L,
                        currentPositionMsProvider = { livePlaybackPos },
                        trimStartMs = autoStart,
                        trimEndMs = autoEnd,
                        autoSplitMode = AutoSplitMode.BY_LENGTH,
                        autoSplitIntervalMs = 60_000L,
                        autoSplitFileCount = 2,
                        autoSplitTargetSizeMb = 10f,
                        estimatedTotalBytes = 50L * 1024L * 1024L,
                        onUpdateBounds = { s, e ->
                            autoStart = s
                            autoEnd = e
                        },
                        onUpdateAutoSplitConfig = { _, _, _, _ -> },
                        onPlaySelection = { _, _ -> }
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // 1. Trimmer: Start -> 00:42, Finish -> 01:35 (95_000ms)
        composeRule.onNodeWithTag("btn_trimmer_move_start_now").performScrollTo().performTouchInput { doubleClick(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("readout_trimmer_start_time").performScrollTo().assertTextContains("00:42", substring = true)

        livePlaybackPos = 95_000L
        composeRule.onNodeWithTag("btn_trimmer_move_finish_now").performScrollTo().performTouchInput { doubleClick(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("readout_trimmer_finish_time").performScrollTo().assertTextContains("01:35", substring = true)

        // 2. Multi Trim
        livePlaybackPos = 55_000L
        composeRule.onNodeWithTag("btn_multi_trim_move_start_now").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("readout_multi_trim_start_time").performScrollTo().assertTextContains("00:55", substring = true)

        // 3. Delete Part
        livePlaybackPos = 80_000L
        composeRule.onNodeWithTag("btn_delete_part_move_finish_now").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("readout_delete_part_finish_time").performScrollTo().assertTextContains("01:20", substring = true)

        // 4. Multi Delete
        livePlaybackPos = 25_000L
        composeRule.onNodeWithTag("btn_multi_delete_move_start_now").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("readout_multi_delete_start_time").performScrollTo().assertTextContains("00:25", substring = true)

        // 5. Splitter: Point -> 02:15 (135_000ms)
        livePlaybackPos = 135_000L
        composeRule.onNodeWithTag("btn_splitter_move_point_now").performScrollTo().performTouchInput { doubleClick(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("text_splitter_point_time").performScrollTo().assertTextContains("02:15", substring = true)

        // 6. Auto Splitter
        livePlaybackPos = 180_000L
        composeRule.onNodeWithTag("btn_auto_splitter_move_finish_now").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("readout_auto_splitter_finish_time").performScrollTo().assertTextContains("03:00", substring = true)
    }

    @Test
    fun perFeatureGuideButton_insideEveryFeatureOpensOnlyThatSpecificFeatureGuide() {
        composeRule.setContent {
            var selectedTopic by remember { mutableStateOf<FeatureGuideTopic?>(null) }
            var echoEnabled by remember { mutableStateOf(false) }
            var denoiseParams by remember { mutableStateOf(SpectralDenoiseParams()) }

            AccessibleStudioTheme(isHighContrast = false) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    VideoTrimmerPrecisionPanel(
                        durationMs = 120_000L,
                        currentPositionMs = 0L,
                        trimStartMs = 0L,
                        trimEndMs = 120_000L,
                        streamCopyTrim = false,
                        onUpdateTrim = { _, _, _ -> },
                        onPlaySelection = { _, _ -> }
                    )
                    VideoSplitterPanel(
                        durationMs = 120_000L,
                        currentPositionMs = 0L,
                        trimStartMs = 0L,
                        trimEndMs = 120_000L,
                        splitPointsMs = emptyList(),
                        onUpdateSplitPoints = {},
                        onPlayFrom = {}
                    )
                    VideoCutterSection(
                        currentPositionMs = 0L,
                        durationMs = 120_000L,
                        trimStartMs = 0L,
                        trimEndMs = 120_000L,
                        isPlaying = false,
                        cutSegments = emptyList(),
                        onPlayPause = {},
                        onSeekTo = {},
                        onReplayFromStart = {},
                        onAddCutSegment = { _, _ -> },
                        onRemoveCutSegment = {}
                    )
                    EchoEffectCard(
                        echoEnabled = echoEnabled,
                        onEchoEnabledChange = { echoEnabled = it },
                        echoDelayMs = 280L,
                        onEchoDelayChange = {},
                        echoFeedback = 0.35f,
                        onEchoFeedbackChange = {},
                        echoMix = 0.25f,
                        onEchoMixChange = {}
                    )
                    MusicRebalancePanel(
                        hasAudioLoaded = true,
                        isProcessing = false,
                        onPreviewRebalance = { _, _, _, _ -> },
                        onApplyRebalance = { _, _, _, _ -> },
                        onSeparateStems = {},
                        onOpenGuide = { selectedTopic = FeatureGuideTopic.AUDIO_MUSIC_REBALANCE }
                    )
                    AdaptiveNoiseReductionPanel(
                        params = denoiseParams,
                        onParamsChange = { denoiseParams = it },
                        learnedProfile = null,
                        isPlayingLivePreview = false,
                        onToggleLivePreview = {},
                        onLearnNoiseProfile = {},
                        onClearNoiseProfile = {},
                        onApplyOfflineClean = {}
                    )
                }

                selectedTopic?.let { topic ->
                    SingleFeatureGuideDialog(
                        topic = topic,
                        onDismiss = { selectedTopic = null }
                    )
                }
            }
        }
        composeRule.waitForIdle()

        fun assertNotInOpenGuideDialog(forbiddenText: String) {
            assertTrue(
                "Did not expect '$forbiddenText' inside scoped feature guide dialog",
                composeRule.onAllNodes(
                    hasAnyAncestor(isDialog()) and hasText(forbiddenText, substring = true)
                ).fetchSemanticsNodes().isEmpty()
            )
        }

        // 1. Video Trimmer guide opens ONLY Video Trimmer guide (never Audio / Rebalance / Splitter)
        composeRule.onNodeWithTag("btn_feature_guide_trimmer").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dialog_single_feature_guide").assertIsDisplayed()
        composeRule.onNodeWithText("Video Trimmer Guide").assertIsDisplayed()
        assertNotInOpenGuideDialog("Music Rebalance")
        assertNotInOpenGuideDialog("Adaptive Spectral")
        composeRule.onNodeWithTag("btn_close_single_feature_guide").performClick()
        composeRule.waitForIdle()

        // 2. Video Splitter guide opens ONLY Video Splitter guide
        composeRule.onNodeWithTag("btn_feature_guide_splitter").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dialog_single_feature_guide").assertIsDisplayed()
        composeRule.onNodeWithText("Video Splitter Guide").assertIsDisplayed()
        assertNotInOpenGuideDialog("Batch Processing Studio")
        assertNotInOpenGuideDialog("Video Trimmer Guide")
        composeRule.onNodeWithTag("btn_close_single_feature_guide").performClick()
        composeRule.waitForIdle()

        // 3. Video Cutter guide opens ONLY Non-Destructive Cutter guide
        composeRule.onNodeWithTag("btn_feature_guide_cutter").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dialog_single_feature_guide").assertIsDisplayed()
        composeRule.onNodeWithText("Non-Destructive Cutter Guide").assertIsDisplayed()
        assertNotInOpenGuideDialog("Music Rebalance")
        composeRule.onNodeWithTag("btn_close_single_feature_guide").performClick()
        composeRule.waitForIdle()

        // 4. Echo effect guide opens ONLY Echo (Delay) guide
        composeRule.onNodeWithTag("btn_feature_guide_fx_echo").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dialog_single_feature_guide").assertIsDisplayed()
        composeRule.onNodeWithText("Echo (Delay) Guide").assertIsDisplayed()
        assertNotInOpenGuideDialog("Video Auto Splitter")
        assertNotInOpenGuideDialog("Video Splitter Guide")
        composeRule.onNodeWithTag("btn_close_single_feature_guide").performClick()
        composeRule.waitForIdle()

        // 5. Music Rebalance guide opens ONLY Music Rebalance & Stems guide
        composeRule.onNodeWithTag("btn_music_rebalance_guide").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dialog_single_feature_guide").assertIsDisplayed()
        composeRule.onNodeWithText("Music Rebalance & Stems Guide").assertIsDisplayed()
        assertNotInOpenGuideDialog("Video Trimmer Guide")
        composeRule.onNodeWithTag("btn_close_single_feature_guide").performClick()
        composeRule.waitForIdle()

        // 6. Adaptive Spectral De-Noise guide opens ONLY Adaptive Spectral De-Noise guide
        composeRule.onNodeWithTag("btn_feature_guide_adaptive_denoise").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dialog_single_feature_guide").assertIsDisplayed()
        composeRule.onNodeWithText("Adaptive Spectral De-Noise Guide").assertIsDisplayed()
        assertNotInOpenGuideDialog("Video Splitter Guide")
        composeRule.onNodeWithTag("btn_close_single_feature_guide").performClick()
        composeRule.waitForIdle()
        assertFalse(composeRule.onAllNodesWithText("Adaptive Spectral De-Noise Guide").fetchSemanticsNodes().isNotEmpty())
    }
}
