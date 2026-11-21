package com.example

import android.content.Context
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioRecorderManager
import com.example.data.MediaRepository
import com.example.ui.components.VideoTimelineToolsPanel
import com.example.ui.studio.VideoFeatureTool
import com.example.ui.studio.VideoFeatureWorkspaceScreen
import com.example.ui.studio.VideoProjectSessionState
import com.example.ui.theme.AccessibleStudioTheme
import com.example.video.VideoAudioMixer
import com.example.video.VideoCutMath
import com.example.video.VideoCutSegment
import com.example.video.VideoMetadataDetails
import com.example.video.VideoPlayerManager
import com.example.video.VideoRenderEngine
import com.example.video.VideoToolkitProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VideoCutterTimelineTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun videoCutMath_mergesOverlaps_computesDeletedOutputAndKeepRanges() {
        val cuts = listOf(
            VideoCutSegment(id = "c1", startMs = 2000L, endMs = 5000L),
            VideoCutSegment(id = "c2", startMs = 4000L, endMs = 7000L),
            VideoCutSegment(id = "c3", startMs = 10000L, endMs = 12000L)
        )
        val merged = VideoCutMath.mergeCutSegments(cuts, rangeStartMs = 0L, rangeEndMs = 20000L)
        assertEquals(listOf(2000L to 7000L, 10000L to 12000L), merged)

        val deleted = VideoCutMath.computeDeletedDurationMs(cuts, 0L, 20000L)
        assertEquals(7000L, deleted)

        val outputLen = VideoCutMath.computeOutputLengthMs(
            trimStartMs = 1000L,
            trimEndMs = 18000L,
            durationMs = 20000L,
            cutSegments = cuts
        )
        // Active trim span = 17,000 ms; deleted inside [1000, 18000] = 7,000 ms -> 10,000 ms
        assertEquals(10000L, outputLen)

        val keepRanges = VideoCutMath.computeKeepRangesMs(
            trimStartMs = 1000L,
            trimEndMs = 18000L,
            cutSegments = cuts
        )
        assertEquals(
            listOf(1000L to 2000L, 7000L to 10000L, 12000L to 18000L),
            keepRanges
        )
    }

    @Test
    fun videoCutMath_stepCyclingAndMarkerClampingBehaveCleanly() {
        assertEquals(2000L, VideoCutMath.nextStepOption(1000L).stepMs)
        assertEquals(5000L, VideoCutMath.nextStepOption(2000L).stepMs)
        assertEquals(500L, VideoCutMath.nextStepOption(5000L).stepMs)
        assertEquals(1000L, VideoCutMath.nextStepOption(500L).stepMs)

        // Setting start marker past end pushes end forward within duration
        val (s1, e1) = VideoCutMath.clampStartCutMarker(desiredStartMs = 7500L, currentEndMs = 4000L, durationMs = 10000L)
        assertEquals(7500L, s1)
        assertEquals(7750L, e1)

        // Setting end marker before start shifts start earlier cleanly
        val (s2, e2) = VideoCutMath.clampEndCutMarker(currentStartMs = 6000L, desiredEndMs = 3000L, durationMs = 10000L)
        assertEquals(2750L, s2)
        assertEquals(3000L, e2)
    }

    @Test
    fun videoCutterSection_rendersAllControls_updatesLiveState_andHasNoExportButton() {
        var replayedCount = 0

        composeRule.setContent {
            var currentPosMs by remember { mutableLongStateOf(6000L) }
            var isPlaying by remember { mutableStateOf(false) }
            var trimStartMs by remember { mutableLongStateOf(0L) }
            var trimEndMs by remember { mutableLongStateOf(20000L) }
            var streamCopy by remember { mutableStateOf(false) }
            var cuts by remember { mutableStateOf<List<VideoCutSegment>>(emptyList()) }

            AccessibleStudioTheme(isHighContrast = false) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    VideoTimelineToolsPanel(
                        currentPositionMs = currentPosMs,
                        durationMs = 20000L,
                        trimStartMs = trimStartMs,
                        trimEndMs = trimEndMs,
                        streamCopyTrim = streamCopy,
                        onUpdateTrimRange = { s, e -> trimStartMs = s; trimEndMs = e },
                        onToggleStreamCopyTrim = { streamCopy = it },
                        onExportTrimmed = null,
                        onCutMiddleSegment = null,
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
                        videoFileName = "SampleTutorial.mp4",
                        videoMetadata = VideoMetadataDetails(
                            durationMs = 20000L,
                            width = 1920,
                            height = 1080,
                            fps = 30
                        ),
                        isPlaying = isPlaying,
                        onPlayPause = { isPlaying = !isPlaying },
                        onSeekTo = { currentPosMs = it },
                        onReplayFromStart = {
                            replayedCount++
                            currentPosMs = 0L
                            isPlaying = true
                        },
                        cutSegments = cuts,
                        onAddCutSegment = { s, e ->
                            cuts = cuts + VideoCutSegment(startMs = s, endMs = e)
                        },
                        onRemoveCutSegment = { id ->
                            cuts = cuts.filterNot { it.id == id }
                        },
                        onClearCutSegments = { cuts = emptyList() },
                        showHeaderMetadataAndScrubber = true,
                        showTrimmerExportButton = false
                    )
                }
            }
        }

        composeRule.waitForIdle()

        // Metadata banner, scrubber, and Stream Copy switch preserved
        composeRule.onNodeWithTag("video_metadata_banner").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("SampleTutorial.mp4").assertIsDisplayed()
        composeRule.onNodeWithTag("slider_video_timeline_scrubber").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("switch_trim_stream_copy").performScrollTo().assertIsOff()
        composeRule.onNodeWithTag("switch_trim_stream_copy").performClick()
        composeRule.onNodeWithTag("switch_trim_stream_copy").assertIsOn()

        // Verify NO Export and save video button inside Video Cutter section
        assertTrue(composeRule.onAllNodesWithText("Export and save video", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Remove Middle & Join", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())

        // Verify all Playback controls
        composeRule.onNodeWithTag("btn_cutter_play_pause").performScrollTo().assertTextContains("Play")
        composeRule.onNodeWithTag("btn_cutter_play_pause").performClick()
        composeRule.onNodeWithTag("btn_cutter_play_pause").assertTextContains("Pause")

        composeRule.onNodeWithTag("btn_cutter_rewind_5s").performScrollTo().performClick() // 6000 -> 1000
        composeRule.onNodeWithTag("btn_cutter_forward_5s").performScrollTo().performClick() // 1000 -> 6000

        // Step controls: Seek forward 1 s -> 7000, set Start marker at playhead -> 00:07.0
        composeRule.onNodeWithTag("btn_cutter_step_toggle").performScrollTo().assertTextContains("Step: 1 s")
        composeRule.onNodeWithTag("btn_cutter_step_forward").performScrollTo().performClick() // 6000 -> 7000
        composeRule.onNodeWithTag("btn_cutter_start_playhead").performScrollTo().performClick()
        composeRule.onNodeWithTag("card_cutter_readout").performScrollTo()
        composeRule.onNodeWithTag("txt_cutter_sel_start", useUnmergedTree = true).assertTextContains("00:07.0")

        // Seek back 1 s -> 6000, toggle step -> 2 s, forward -> 8000, forward -> 10000, set End marker at playhead -> 00:10.0
        composeRule.onNodeWithTag("btn_cutter_step_back").performScrollTo().performClick() // 7000 -> 6000
        composeRule.onNodeWithTag("btn_cutter_step_toggle").performScrollTo().performClick()
        composeRule.onNodeWithTag("btn_cutter_step_toggle").assertTextContains("Step: 2 s")
        composeRule.onNodeWithTag("btn_cutter_step_forward").performScrollTo().performClick() // 6000 -> 8000
        composeRule.onNodeWithTag("btn_cutter_step_forward").performScrollTo().performClick() // 8000 -> 10000
        composeRule.onNodeWithTag("btn_cutter_end_playhead").performScrollTo().performClick()
        composeRule.onNodeWithTag("card_cutter_readout").performScrollTo()
        composeRule.onNodeWithTag("txt_cutter_sel_end", useUnmergedTree = true).assertTextContains("00:10.0")

        // Fine-tune Start (-2s -> 00:05.0) and Increase (+2s -> 00:07.0)
        composeRule.onNodeWithTag("btn_cutter_start_dec").performScrollTo().performClick()
        composeRule.onNodeWithTag("txt_cutter_sel_start", useUnmergedTree = true).assertTextContains("00:05.0")
        composeRule.onNodeWithTag("btn_cutter_start_inc").performScrollTo().performClick()
        composeRule.onNodeWithTag("txt_cutter_sel_start", useUnmergedTree = true).assertTextContains("00:07.0")

        // Fine-tune End (+2s -> 00:12.0) and Decrease (-2s -> 00:10.0)
        composeRule.onNodeWithTag("btn_cutter_end_inc").performScrollTo().performClick()
        composeRule.onNodeWithTag("txt_cutter_sel_end", useUnmergedTree = true).assertTextContains("00:12.0")
        composeRule.onNodeWithTag("btn_cutter_end_dec").performScrollTo().performClick()
        composeRule.onNodeWithTag("txt_cutter_sel_end", useUnmergedTree = true).assertTextContains("00:10.0")

        // Verify Beginning & End of Video quick buttons
        composeRule.onNodeWithTag("btn_cutter_start_beginning").performScrollTo().performClick()
        composeRule.onNodeWithTag("txt_cutter_sel_start", useUnmergedTree = true).assertTextContains("00:00.0")
        composeRule.onNodeWithTag("btn_cutter_end_finish").performScrollTo().performClick()
        composeRule.onNodeWithTag("txt_cutter_sel_end", useUnmergedTree = true).assertTextContains("00:20.0")

        // Reset Start = 00:02.0 and End = 00:10.0 via +2s / End adjustments, then Add Selected Region to Cut List (8s cut)
        composeRule.onNodeWithTag("btn_cutter_start_inc").performScrollTo().performClick() // Start = 00:02.0
        composeRule.onNodeWithTag("btn_cutter_end_playhead").performScrollTo().performClick() // Playhead is at 10s -> End = 00:10.0
        composeRule.onNodeWithTag("card_cutter_readout").performScrollTo()
        composeRule.onNodeWithTag("txt_cutter_sel_start", useUnmergedTree = true).assertTextContains("00:02.0")
        composeRule.onNodeWithTag("txt_cutter_sel_end", useUnmergedTree = true).assertTextContains("00:10.0")

        composeRule.onNodeWithTag("btn_cutter_add_cut").performScrollTo().performClick()
        composeRule.waitForIdle()

        // Deleted duration = 00:08.0, Output length = 00:12.0, View Added Cuts (1)
        composeRule.onNodeWithTag("card_cutter_readout").performScrollTo()
        composeRule.onNodeWithTag("txt_cutter_deleted_dur", useUnmergedTree = true).assertTextContains("00:08.0")
        composeRule.onNodeWithTag("txt_cutter_output_len", useUnmergedTree = true).assertTextContains("00:12.0")
        composeRule.onNodeWithTag("btn_cutter_view_cuts").performScrollTo().assertTextContains("View Added Cuts (1)")

        // Open Cuts List dialog and remove Cut #1
        composeRule.onNodeWithTag("btn_cutter_view_cuts").performClick()
        composeRule.onNodeWithTag("dialog_cutter_cuts_list").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_remove_cut_0").performClick()
        composeRule.onNodeWithTag("btn_close_cuts_list").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("btn_cutter_view_cuts").performScrollTo().assertTextContains("View Added Cuts (0)")
        composeRule.onNodeWithTag("card_cutter_readout").performScrollTo()
        composeRule.onNodeWithTag("txt_cutter_deleted_dur", useUnmergedTree = true).assertTextContains("00:00.0")
        composeRule.onNodeWithTag("txt_cutter_output_len", useUnmergedTree = true).assertTextContains("00:20.0")

        // Replay from start fires callback
        composeRule.onNodeWithTag("btn_cutter_replay_start").performScrollTo().performClick()
        assertEquals(1, replayedCount)
    }

    @Test
    fun videoFeatureWorkspace_trimTimeline_preservesStreamCopyMetadataAndSavesCutListWithoutFinalExport() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val videoPlayer = VideoPlayerManager(context)
        val renderEngine = VideoRenderEngine(context)
        val toolkit = VideoToolkitProcessor(context)
        val audioMixer = VideoAudioMixer(context)
        val recorder = AudioRecorderManager(context)
        val mediaRepo = MediaRepository(context)

        val initialSession = VideoProjectSessionState(
            projectId = "vid_cutter_proj",
            title = "LectureClip.mp4",
            baseFileName = "LectureClip",
            durationMs = 12000L,
            metadata = VideoMetadataDetails(durationMs = 12000L, width = 1280, height = 720, fps = 30),
            trimStartMs = 0L,
            trimEndMs = 12000L
        )

        var savedSession: VideoProjectSessionState? = null

        composeRule.setContent {
            AccessibleStudioTheme(isHighContrast = false) {
                VideoFeatureWorkspaceScreen(
                    tool = VideoFeatureTool.TRIM_TIMELINE,
                    session = initialSession,
                    videoPlayer = videoPlayer,
                    renderEngine = renderEngine,
                    toolkit = toolkit,
                    audioMixer = audioMixer,
                    recorder = recorder,
                    mediaRepository = mediaRepo,
                    hapticsEnabled = false,
                    onSaveAndReturn = { savedSession = it },
                    onCancelBack = {},
                    onNavigateToExports = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Metadata, timeline scrubber, and Stream Copy switch are displayed
        composeRule.onNodeWithTag("video_metadata_banner").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("slider_video_timeline_scrubber").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("switch_trim_stream_copy").performScrollTo().performClick()

        // Add default selected region (00:00.0 - 00:04.0) to cut list inside Video Cutter
        composeRule.onNodeWithTag("btn_cutter_add_cut").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("btn_cutter_view_cuts").performScrollTo().assertTextContains("View Added Cuts (1)")
        composeRule.onNodeWithTag("card_cutter_readout").performScrollTo()
        composeRule.onNodeWithTag("txt_cutter_deleted_dur", useUnmergedTree = true).assertTextContains("00:04.0")
        composeRule.onNodeWithTag("txt_cutter_output_len", useUnmergedTree = true).assertTextContains("00:08.0")

        // Ensure no Export button exists inside Video Cutter / Trim workspace
        assertTrue(composeRule.onAllNodesWithText("Export and save video", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Export Trimmed Clip", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())

        // Save project draft back to Project Hub
        composeRule.onNodeWithTag("btn_workspace_save").performClick()
        composeRule.waitForIdle()

        val saved = savedSession!!
        assertTrue(saved.hasUnsavedChanges)
        assertTrue(saved.streamCopyTrim)
        assertEquals(1, saved.cutSegments.size)
        assertEquals(0L, saved.cutSegments.first().startMs)
        assertEquals(4000L, saved.cutSegments.first().endMs)
    }
}
