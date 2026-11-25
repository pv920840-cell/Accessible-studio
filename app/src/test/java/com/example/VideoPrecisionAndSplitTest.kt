package com.example

import android.net.Uri
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
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import com.example.ui.components.AccessibleAudioItem
import com.example.ui.components.AccessibleAudioPickerDialog
import com.example.ui.components.AccessiblePreviewPlaybackSlider
import com.example.ui.components.AccessibleVideoItem
import com.example.ui.components.AccessibleVideoPickerDialog
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
        assertEquals(1L, msStart.stepMs)

        // Default 1s -> 10s -> 1min on stepUp regardless of video length
        val tenSec = VideoCutMath.stepUp(1_000L)
        assertEquals(10_000L, tenSec.stepMs)
        assertEquals(VideoCutMath.StepUnitCategory.SECONDS, tenSec.unitCategory)

        val oneMin = VideoCutMath.stepUp(10_000L)
        assertEquals(60_000L, oneMin.stepMs)
        assertEquals(VideoCutMath.StepUnitCategory.MINUTES, oneMin.unitCategory)

        // Step down: 1min -> 10s -> 1s -> 100ms -> 10ms -> 1ms
        assertEquals(10_000L, VideoCutMath.stepDown(60_000L).stepMs)
        assertEquals(1_000L, VideoCutMath.stepDown(10_000L).stepMs)
        val hundredMs = VideoCutMath.stepDown(1_000L)
        assertEquals(100L, hundredMs.stepMs)
        assertEquals(VideoCutMath.StepUnitCategory.MILLISECONDS, hundredMs.unitCategory)
        assertEquals(10L, VideoCutMath.stepDown(100L).stepMs)
        assertEquals(1L, VideoCutMath.stepDown(10L).stepMs)
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
        var latestStepMs = 1000L
        composeRule.setContent {
            var stepMs by remember { mutableLongStateOf(1000L) }
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

        // Initially defaults to 1 s (Seconds)
        composeRule.onNodeWithTag("test_step_value_label").assertTextContains("1 s", substring = true)

        // Swipe UP increases from 1s -> 10s
        composeRule.onNodeWithTag("test_step_slider_container").performTouchInput {
            down(center)
            moveBy(Offset(0f, -80f))
            up()
        }
        composeRule.waitForIdle()
        assertEquals(10_000L, latestStepMs)

        // Swipe UP again -> 1 min (Minutes)
        composeRule.onNodeWithTag("test_step_slider_container").performTouchInput {
            down(center)
            moveBy(Offset(0f, -80f))
            up()
        }
        composeRule.waitForIdle()
        assertEquals(60_000L, latestStepMs)
        composeRule.onNodeWithTag("test_step_value_label").assertTextContains("Minutes", substring = true)

        // Swipe DOWN moves back: 1min -> 10s -> 1s -> 100ms -> 10ms -> 1ms
        repeat(3) {
            composeRule.onNodeWithTag("test_step_slider_container").performTouchInput {
                down(center)
                moveBy(Offset(0f, 80f))
                up()
            }
            composeRule.waitForIdle()
        }
        assertEquals(100L, latestStepMs)
        composeRule.onNodeWithTag("test_step_value_label").assertTextContains("100 ms", substring = true)

        composeRule.onNodeWithTag("test_step_btn_down").performClick()
        composeRule.waitForIdle()
        assertEquals(10L, latestStepMs)

        composeRule.onNodeWithTag("test_step_btn_down").performClick()
        composeRule.waitForIdle()
        assertEquals(1L, latestStepMs)
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

        // 1. Open Video Trimmer -> verify double-tap Move Start/Finish, TimeEditDialog, and ZERO Save fast/exact or file-info
        composeRule.onNodeWithTag("btn_video_feature_trimmer").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("panel_video_trimmer").assertIsDisplayed()
        composeRule.onNodeWithText("Move Start slider to now playing", substring = false).assertIsDisplayed()
        composeRule.onNodeWithText("Move Finish slider to now playing", substring = false).assertIsDisplayed()

        // Double-tap Move Start slider to now playing sets Start time to currentPositionMs (00:05.000)
        composeRule.onNodeWithTag("btn_trimmer_move_start_now").performTouchInput {
            doubleClick(center)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("readout_trimmer_start_time").performScrollTo().assertTextContains("00:05.000", substring = true)

        // Double-tap Start time opens TimeEditDialog with Minutes, Seconds, Millis, OK, Cancel
        composeRule.onNodeWithTag("readout_trimmer_start_time").performScrollTo().performTouchInput {
            doubleClick(center)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dialog_time_edit").assertIsDisplayed()
        composeRule.onNodeWithTag("input_time_edit_minutes").performTextReplacement("1")
        composeRule.onNodeWithTag("input_time_edit_seconds").performTextReplacement("05")
        composeRule.onNodeWithTag("input_time_edit_millis").performTextReplacement("250")
        composeRule.onNodeWithTag("btn_time_edit_ok").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("readout_trimmer_start_time").performScrollTo().assertTextContains("01:05.250", substring = true)

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

        // 5. Open Video Splitter -> Double-tap Move Point -> Double-tap Point time -> Add split point
        composeRule.onNodeWithTag("btn_video_feature_splitter").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("panel_video_splitter").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_splitter_move_point_now").performTouchInput {
            doubleClick(center)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("text_splitter_point_time").performScrollTo().assertTextContains("00:05.000", substring = true)
        composeRule.onNodeWithTag("text_splitter_point_time").performScrollTo().performTouchInput {
            doubleClick(center)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dialog_time_edit").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_time_edit_cancel").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("btn_splitter_add_point").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("text_splitter_point_list_header").performScrollTo().assertTextContains("Point list (1 points", substring = true)
        composeRule.onNodeWithTag("btn_done_timeline_feature_dialog").performClick()
        composeRule.waitForIdle()

        // 6. Open Video Auto splitter -> swipe Auto Splitter slider UP to 2 split files, and verify double-tap Split every dialog
        composeRule.onNodeWithTag("btn_video_feature_auto_splitter").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("panel_video_auto_splitter").assertIsDisplayed()
        composeRule.onNodeWithTag("auto_splitter_slider_container").performScrollTo()
        repeat(4) {
            composeRule.onNodeWithTag("auto_splitter_slider_container").performTouchInput {
                down(center)
                moveBy(Offset(0f, -80f))
                up()
            }
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithTag("txt_auto_splitter_slider_value").assertTextContains("2 split files", substring = true)

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

    @Test
    fun playbackSlider_verticalSwipeScrubsWithPreview_andDoubleTapTogglesPlayPause() {
        var latestSeekMs = 5000L
        var scrubPulses = 0
        var toggleCount = 0

        composeRule.setContent {
            var posMs by remember { mutableLongStateOf(5000L) }
            var playing by remember { mutableStateOf(false) }
            AccessibleStudioTheme(isHighContrast = false) {
                AccessiblePreviewPlaybackSlider(
                    currentPositionMs = posMs,
                    durationMs = 20_000L,
                    onSeekTo = {
                        posMs = it
                        latestSeekMs = it
                    },
                    testTag = "slider_playback_test",
                    isPlaying = playing,
                    onTogglePlayPause = {
                        playing = !playing
                        toggleCount++
                    },
                    onScrubPreviewPulse = { scrubPulses++ }
                )
            }
        }
        composeRule.waitForIdle()

        // Swipe UP moves toward end with preview pulse
        composeRule.onNodeWithTag("slider_playback_test_container").performTouchInput {
            down(center)
            moveBy(Offset(0f, -80f))
            up()
        }
        composeRule.waitForIdle()
        assertTrue("Expected forward seek > 5000ms, got $latestSeekMs", latestSeekMs > 5000L)
        assertTrue("Expected scrub preview pulse on swipe UP", scrubPulses >= 1)

        // Swipe DOWN moves backward toward beginning with preview pulse
        val afterUp = latestSeekMs
        composeRule.onNodeWithTag("slider_playback_test_container").performTouchInput {
            down(center)
            moveBy(Offset(0f, 80f))
            up()
        }
        composeRule.waitForIdle()
        assertTrue("Expected backward seek < $afterUp, got $latestSeekMs", latestSeekMs < afterUp)
        assertTrue("Expected scrub preview pulse on swipe DOWN", scrubPulses >= 2)

        // Double-tap toggles Play / Pause
        composeRule.onNodeWithTag("slider_playback_test").performTouchInput {
            doubleClick(center)
        }
        composeRule.waitForIdle()
        assertEquals(1, toggleCount)

        composeRule.onNodeWithTag("slider_playback_test").performTouchInput {
            doubleClick(center)
        }
        composeRule.waitForIdle()
        assertEquals(2, toggleCount)
    }

    @Test
    fun accessibleVideoPickerDialog_exposesRequiredControls_withoutNavigateUp() {
        val sampleVideos = listOf(
            AccessibleVideoItem(
                id = "1",
                title = "Camera_Sunset.mp4",
                uri = Uri.parse("content://media/external/video/media/1"),
                folderName = "Camera",
                durationMs = 15_000L,
                sizeBytes = 5_000_000L,
                modifiedMs = 2000L
            ),
            AccessibleVideoItem(
                id = "2",
                title = "Studio_Edit.mp4",
                uri = Uri.parse("content://media/external/video/media/2"),
                folderName = "Accessible Studio",
                durationMs = 45_000L,
                sizeBytes = 12_000_000L,
                modifiedMs = 5000L
            )
        )
        var pickedTitle = ""

        composeRule.setContent {
            AccessibleStudioTheme(isHighContrast = false) {
                AccessibleVideoPickerDialog(
                    exportedVideos = emptyList(),
                    onSelectVideo = { _, title -> pickedTitle = title },
                    onDismiss = {},
                    preloadedVideos = sampleVideos
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("dialog_accessible_video_picker").assertIsDisplayed()
        composeRule.onNodeWithText("Choose video").assertIsDisplayed()
        composeRule.onNodeWithTag("tab_video_picker_all_files").assertIsDisplayed()
        composeRule.onNodeWithTag("tab_video_picker_folders").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_video_picker_sort").assertTextContains("Newest first", substring = true)
        composeRule.onNodeWithTag("btn_video_picker_open_file_manager").assertIsDisplayed()
        composeRule.onNodeWithTag("input_video_picker_search").assertIsDisplayed()

        // Strictly NO "Navigate up" button anywhere
        assertTrue(
            composeRule.onAllNodesWithText("Navigate up", substring = true, ignoreCase = true)
                .fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithContentDescription("Navigate up", substring = true, ignoreCase = true)
                .fetchSemanticsNodes().isEmpty()
        )

        // Search by name filters down to Camera_Sunset.mp4
        composeRule.onNodeWithTag("input_video_picker_search").performTextReplacement("Camera")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("card_video_picker_item_0").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals("Camera_Sunset.mp4", pickedTitle)
    }

    @Test
    fun accessibleAudioPickerDialog_exposesRequiredControls_withoutNavigateUp() {
        val sampleAudios = listOf(
            AccessibleAudioItem(
                id = "1",
                title = "Podcast_Episode_01.wav",
                uri = Uri.parse("content://media/external/audio/media/1"),
                folderName = "Recordings",
                durationMs = 120_000L,
                sizeBytes = 10_000_000L,
                modifiedMs = 2000L
            ),
            AccessibleAudioItem(
                id = "2",
                title = "Studio_Master.mp3",
                uri = Uri.parse("content://media/external/audio/media/2"),
                folderName = "Accessible Studio",
                durationMs = 210_000L,
                sizeBytes = 6_000_000L,
                modifiedMs = 5000L
            )
        )
        var pickedTitle = ""

        composeRule.setContent {
            AccessibleStudioTheme(isHighContrast = false) {
                AccessibleAudioPickerDialog(
                    exportedAudios = emptyList(),
                    onSelectAudio = { _, title -> pickedTitle = title },
                    onDismiss = {},
                    preloadedAudios = sampleAudios
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("dialog_accessible_audio_picker").assertIsDisplayed()
        composeRule.onNodeWithText("Choose audio").assertIsDisplayed()
        composeRule.onNodeWithTag("tab_audio_picker_all_files").assertIsDisplayed()
        composeRule.onNodeWithTag("tab_audio_picker_folders").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_audio_picker_sort").assertTextContains("Newest first", substring = true)
        composeRule.onNodeWithTag("btn_audio_picker_open_file_manager").assertIsDisplayed()
        composeRule.onNodeWithTag("input_audio_picker_search").assertIsDisplayed()

        // Strictly NO "Navigate up" button anywhere
        assertTrue(
            composeRule.onAllNodesWithText("Navigate up", substring = true, ignoreCase = true)
                .fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithContentDescription("Navigate up", substring = true, ignoreCase = true)
                .fetchSemanticsNodes().isEmpty()
        )

        // Search by name filters down to Podcast_Episode_01.wav
        composeRule.onNodeWithTag("input_audio_picker_search").performTextReplacement("Podcast")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("card_audio_picker_item_0").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals("Podcast_Episode_01.wav", pickedTitle)
    }
}

