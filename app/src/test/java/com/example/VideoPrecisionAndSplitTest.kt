package com.example

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.example.ui.components.StandardizedStepSlider
import com.example.ui.components.VideoTimelineToolsPanel
import com.example.ui.theme.AccessibleStudioTheme
import com.example.video.AutoSplitMode
import com.example.video.VideoCutMath
import com.example.video.VideoCutSegment
import com.example.video.VideoMetadataDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VideoPrecisionAndSplitTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun standardizedStepMath_scalesUpToSecondsMinutes_andDownToMilliseconds() {
        val msStart = VideoCutMath.standardizedStepAt(0)
        assertEquals(VideoCutMath.StepUnitCategory.MILLISECONDS, msStart.unitCategory)
        assertEquals(10L, msStart.stepMs)

        // Step up from 500ms -> 1s (Seconds)
        val oneSec = VideoCutMath.stepUp(500L)
        assertEquals(1000L, oneSec.stepMs)
        assertEquals(VideoCutMath.StepUnitCategory.SECONDS, oneSec.unitCategory)

        // Step up from 30s -> 1m (Minutes)
        val oneMin = VideoCutMath.stepUp(30_000L)
        assertEquals(60_000L, oneMin.stepMs)
        assertEquals(VideoCutMath.StepUnitCategory.MINUTES, oneMin.unitCategory)

        // Step down from 1m -> 30s -> ... -> 500ms (Milliseconds)
        assertEquals(30_000L, VideoCutMath.stepDown(60_000L).stepMs)
        assertEquals(500L, VideoCutMath.stepDown(1000L).stepMs)
        assertEquals(VideoCutMath.StepUnitCategory.MILLISECONDS, VideoCutMath.stepDown(1000L).unitCategory)
    }

    @Test
    fun splitAndAutoSplitMath_computesAccurateRanges() {
        val splitRanges = VideoCutMath.splitPointsToRanges(
            trimStartMs = 1000L,
            trimEndMs = 15_000L,
            splitPointsMs = listOf(5000L, 10_000L, 5000L, 200L)
        )
        assertEquals(
            listOf(1000L to 5000L, 5000L to 10_000L, 10_000L to 15_000L),
            splitRanges
        )

        val byLength = VideoCutMath.autoSplitRangesByLength(0L, 25_000L, 10_000L)
        assertEquals(listOf(0L to 10_000L, 10_000L to 20_000L, 20_000L to 25_000L), byLength)

        val byCount = VideoCutMath.autoSplitRangesByCount(0L, 12_000L, 3)
        assertEquals(listOf(0L to 4000L, 4000L to 8000L, 8000L to 12_000L), byCount)

        val bySize = VideoCutMath.autoSplitRangesByTargetSize(
            trimStartMs = 0L,
            trimEndMs = 60_000L,
            targetSizeMb = 20f,
            totalDurationMs = 60_000L,
            estimatedTotalBytes = 60L * 1024L * 1024L
        )
        assertEquals(3, bySize.size)

        val multiKeep = VideoCutMath.computeMultiTrimKeepRanges(
            multiTrimClips = listOf(VideoCutSegment(startMs = 2000L, endMs = 4000L), VideoCutSegment(startMs = 8000L, endMs = 10_000L)),
            cutSegments = emptyList(),
            fallbackStartMs = 0L,
            fallbackEndMs = 15_000L
        )
        assertEquals(listOf(2000L to 4000L, 8000L to 10_000L), multiKeep)
    }

    @Test
    fun standardizedStepSlider_swipesUpForSecondsMinutes_andDownForMilliseconds() {
        var latestStepMs = 500L
        composeRule.setContent {
            var stepMs by remember { mutableLongStateOf(500L) }
            AccessibleStudioTheme(isHighContrast = false) {
                StandardizedStepSlider(
                    stepMs = stepMs,
                    onStepChange = {
                        stepMs = it
                        latestStepMs = it
                    },
                    testTagPrefix = "test_step"
                )
            }
        }

        // Initially 500 ms (Milliseconds)
        composeRule.onNodeWithTag("test_step_value_label").assertTextContains("500 ms", substring = true)

        // Swipe UP increases into Seconds (>= 1000 ms)
        composeRule.onNodeWithTag("test_step_slider_container").performTouchInput {
            down(center)
            moveBy(Offset(0f, -80f))
            up()
        }
        composeRule.waitForIdle()
        assertTrue("Expected step >= 1000ms after swipe UP, was $latestStepMs", latestStepMs >= 1000L)

        // Tap Step Up repeatedly to verify Minutes range is reachable regardless of video duration
        repeat(6) {
            composeRule.onNodeWithTag("test_step_btn_up").performClick()
        }
        composeRule.waitForIdle()
        assertTrue("Expected Minutes range (>= 60_000ms), was $latestStepMs", latestStepMs >= 60_000L)
        composeRule.onNodeWithTag("test_step_value_label").assertTextContains("Minutes", substring = true)

        // Swipe DOWN moves back down toward lower step / Milliseconds
        val beforeDown = latestStepMs
        composeRule.onNodeWithTag("test_step_slider_container").performTouchInput {
            down(center)
            moveBy(Offset(0f, 80f))
            up()
        }
        composeRule.waitForIdle()
        assertTrue("Expected step to decrease after swipe DOWN", latestStepMs < beforeDown)
    }

    @Test
    fun videoTimelineTools_exposesUpdatedTrimmer_andAllFiveSeparateTools_withoutForbiddenButtonsOrFileInfo() {
        composeRule.setContent {
            var trimStartMs by remember { mutableLongStateOf(1000L) }
            var trimEndMs by remember { mutableLongStateOf(7_200_000L) } // 2 hours video
            var streamCopy by remember { mutableStateOf(false) }
            var cutSegments by remember { mutableStateOf<List<VideoCutSegment>>(emptyList()) }
            var multiTrimClips by remember { mutableStateOf<List<VideoCutSegment>>(emptyList()) }
            var splitPoints by remember { mutableStateOf<List<Long>>(emptyList()) }
            var autoMode by remember { mutableStateOf(AutoSplitMode.BY_LENGTH) }
            var autoInterval by remember { mutableLongStateOf(30_000L) }
            var autoCount by remember { mutableIntStateOf(2) }
            var autoSize by remember { mutableFloatStateOf(25f) }

            AccessibleStudioTheme(isHighContrast = false) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    VideoTimelineToolsPanel(
                        currentPositionMs = 5000L,
                        durationMs = 7_200_000L,
                        trimStartMs = trimStartMs,
                        trimEndMs = trimEndMs,
                        streamCopyTrim = streamCopy,
                        onUpdateTrimRange = { s, e -> trimStartMs = s; trimEndMs = e },
                        onToggleStreamCopyTrim = { streamCopy = it },
                        onExportTrimmed = null,
                        mergeClipUris = emptyList(),
                        onAddMergeClips = {},
                        onMoveMergeClip = { _, _ -> },
                        onRemoveMergeClip = {},
                        onExecuteMergeClips = {},
                        slideshowImages = emptyList(),
                        slideshowSecPerSlide = 3,
                        slideshowMusicUri = null,
                        onPickSlideshowImages = {},
                        onChangeSlideshowSec = {},
                        onPickSlideshowMusic = {},
                        onCreateSlideshow = {},
                        onCreateBlankClip = {},
                        snapshots = emptyList(),
                        onCaptureSnapshot = {},
                        onRemoveSnapshot = {},
                        onExportSnapshots = {},
                        showHeaderMetadataAndScrubber = false,
                        showTrimmerExportButton = false,
                        videoMetadata = VideoMetadataDetails(durationMs = 7_200_000L),
                        multiTrimClips = multiTrimClips,
                        onUpdateMultiTrimClips = { multiTrimClips = it },
                        cutSegments = cutSegments,
                        onUpdateCutSegments = { cutSegments = it },
                        splitPointsMs = splitPoints,
                        onUpdateSplitPoints = { splitPoints = it },
                        autoSplitMode = autoMode,
                        autoSplitIntervalMs = autoInterval,
                        autoSplitFileCount = autoCount,
                        autoSplitTargetSizeMb = autoSize,
                        onUpdateAutoSplitConfig = { m, i, c, sz ->
                            autoMode = m; autoInterval = i; autoCount = c; autoSize = sz
                        }
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // All 5 new separate feature buttons + existing Video Trimmer exist
        listOf(
            "btn_video_feature_trimmer",
            "btn_video_feature_multi_trim",
            "btn_video_feature_delete_part",
            "btn_video_feature_multi_delete",
            "btn_video_feature_splitter",
            "btn_video_feature_auto_splitter"
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        }

        // 1. Open Video Trimmer -> verify controls and ZERO Save fast/exact or file-info readouts
        composeRule.onNodeWithTag("btn_video_feature_trimmer").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("panel_video_trimmer").assertIsDisplayed()
        composeRule.onNodeWithText("Move Start slider to now playing", substring = false).assertIsDisplayed()
        composeRule.onNodeWithText("Move Finish slider to now playing", substring = false).assertIsDisplayed()
        composeRule.onNodeWithTag("readout_trimmer_start_time").performScrollTo().assertTextContains("Start time:", substring = true)
        composeRule.onNodeWithTag("readout_trimmer_finish_time").performScrollTo().assertTextContains("Finish time:", substring = true)
        composeRule.onNodeWithTag("readout_trimmer_clip_duration").performScrollTo().assertTextContains("Clip duration:", substring = true)
        assertTrue(composeRule.onAllNodesWithText("Save fast", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Save exact, slow", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Format:", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Length:", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("btn_done_timeline_feature_dialog").performClick()
        composeRule.waitForIdle()

        // 2. Open Video Multi trim -> Add clip -> Clip list increments
        composeRule.onNodeWithTag("btn_video_feature_multi_trim").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("panel_video_multi_trim").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_multi_trim_add_clip").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("text_multi_trim_clip_list").performScrollTo().assertTextContains("Clip list (1)", substring = true)
        composeRule.onNodeWithTag("btn_done_timeline_feature_dialog").performClick()
        composeRule.waitForIdle()

        // 3. Open Video Delete part -> verify Start/Finish/Clip duration controls
        composeRule.onNodeWithTag("btn_video_feature_delete_part").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("panel_video_delete_part").assertIsDisplayed()
        composeRule.onNodeWithTag("readout_delete_part_clip_duration").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("btn_done_timeline_feature_dialog").performClick()
        composeRule.waitForIdle()

        // 4. Open Video Multi delete -> Add part -> Part list increments
        composeRule.onNodeWithTag("btn_video_feature_multi_delete").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("panel_video_multi_delete").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_multi_delete_add_part").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("text_multi_delete_part_list").performScrollTo().assertTextContains("Part list (1)", substring = true)
        composeRule.onNodeWithTag("btn_done_timeline_feature_dialog").performClick()
        composeRule.waitForIdle()

        // 5. Open Video Splitter -> Move Point -> Add split point -> Point list increments
        composeRule.onNodeWithTag("btn_video_feature_splitter").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("panel_video_splitter").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_splitter_move_point_now").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_splitter_add_point").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("text_splitter_point_list_header").performScrollTo().assertTextContains("Point list (1 points", substring = true)
        composeRule.onNodeWithTag("btn_done_timeline_feature_dialog").performClick()
        composeRule.waitForIdle()

        // 6. Open Video Auto splitter -> double-tap Split every opens custom length/files/size dialog
        composeRule.onNodeWithTag("btn_video_feature_auto_splitter").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("panel_video_auto_splitter").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_auto_split_every").performScrollTo().performTouchInput {
            doubleClick(center)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dialog_auto_split_custom").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_auto_split_mode_by_file_count").performClick()
        composeRule.onNodeWithTag("btn_confirm_auto_split_custom").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("btn_auto_split_every").performScrollTo().assertTextContains("2 equal files", substring = true)
    }
}
