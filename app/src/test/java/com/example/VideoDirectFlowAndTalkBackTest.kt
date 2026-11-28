package com.example

import android.content.Context
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioPlayerManager
import com.example.audio.AudioRecorderManager
import com.example.audio.WavAudioProcessor
import com.example.data.MediaRepository
import com.example.data.StudioPreferences
import com.example.ui.studio.AudioFeatureTool
import com.example.ui.studio.AudioFeatureWorkspaceScreen
import com.example.ui.studio.AudioProjectSessionState
import com.example.ui.studio.VideoFeatureWorkspaceScreen
import com.example.ui.studio.VideoProjectSessionState
import com.example.ui.studio.toParentTool
import com.example.ui.theme.AccessibleStudioTheme
import com.example.video.VideoAudioMixer
import com.example.video.VideoPlayerManager
import com.example.video.VideoRenderEngine
import com.example.video.VideoStudioFeatureButton
import com.example.video.VideoToolkitProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VideoDirectFlowAndTalkBackTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun hasButtonRoleWithNonEmptyDescription(): SemanticsMatcher =
        SemanticsMatcher("Has Role.Button and non-empty ContentDescription") { node ->
            val role = node.config.getOrNull(SemanticsProperties.Role)
            val desc = node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.joinToString(" ").trim()
            role == Role.Button && desc.isNotEmpty()
        }

    @Test
    fun videoFeatureWorkspace_opensSingleFeatureDirectly_andDoesNotAutoApplyUntilApplyClicked() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val videoPlayer = VideoPlayerManager(context)
        val renderEngine = VideoRenderEngine(context)
        val toolkit = VideoToolkitProcessor(context)
        val audioMixer = VideoAudioMixer(context)
        val recorder = AudioRecorderManager(context)
        val repo = MediaRepository(context)

        val initialSession = VideoProjectSessionState(
            projectId = "vid_direct_speed",
            title = "Direct Speed Test",
            sourceUri = null,
            durationMs = 10_000L,
            trimStartMs = 0L,
            trimEndMs = 10_000L,
            selectedSpeed = 1.0f
        )

        var appliedSession: VideoProjectSessionState? = null
        var backCancelledCount = 0

        try {
            composeRule.setContent {
                AccessibleStudioTheme {
                    VideoFeatureWorkspaceScreen(
                        tool = VideoStudioFeatureButton.SPEED_CHANGER.toParentTool(),
                        session = initialSession,
                        videoPlayer = videoPlayer,
                        renderEngine = renderEngine,
                        toolkit = toolkit,
                        audioMixer = audioMixer,
                        recorder = recorder,
                        mediaRepository = repo,
                        hapticsEnabled = false,
                        onSaveAndReturn = { appliedSession = it },
                        onCancelBack = { backCancelledCount++ },
                        onNavigateToExports = {},
                        initialFeature = VideoStudioFeatureButton.SPEED_CHANGER,
                        onApplyEdits = { appliedSession = it }
                    )
                }
            }
            composeRule.waitForIdle()

            // Only Speed Changer renders inline; no nested feature buttons or unrelated panels
            composeRule.onNodeWithTag("slider_video_speed").performScrollTo().assertExists()
            composeRule.onNodeWithTag("btn_feature_guide_speed").performScrollTo().assertExists()
            composeRule.onNodeWithTag("btn_feature_guide_resize").assertDoesNotExist()
            composeRule.onNodeWithTag("btn_feature_guide_aspect_ratio").assertDoesNotExist()
            composeRule.onNodeWithTag("btn_video_feature_speed_changer").assertDoesNotExist()
            composeRule.onNodeWithTag("btn_video_feature_trimmer").assertDoesNotExist()
            assertTrue(composeRule.onAllNodesWithText("Open", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())

            // Changing a control updates local draft only; active session is NOT auto-applied
            composeRule.onNodeWithText("1.5x").performScrollTo().performClick()
            composeRule.waitForIdle()
            assertNull("Changing speed must NOT auto-apply before clicking Apply", appliedSession)

            // Back returns without requiring Save Project and without auto-applying
            composeRule.onNodeWithTag("btn_workspace_back").performClick()
            composeRule.waitForIdle()
            assertEquals(1, backCancelledCount)
            assertNull(appliedSession)

            // Explicitly tapping Apply commits the edited speed to active project state
            composeRule.onNodeWithTag("btn_video_workspace_apply")
                .performScrollTo()
                .assert(hasButtonRoleWithNonEmptyDescription())
                .performClick()
            composeRule.waitForIdle()
            assertNotNull("Tapping Apply must commit edits", appliedSession)
            assertEquals(1.5f, appliedSession!!.selectedSpeed, 0.001f)
            assertTrue(appliedSession!!.hasUnsavedChanges)
        } finally {
            videoPlayer.release()
        }
    }

    @Test
    fun videoTrimmerDirectWorkspace_hasTalkBackRoleButtonAndDescriptions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val videoPlayer = VideoPlayerManager(context)
        val renderEngine = VideoRenderEngine(context)
        val toolkit = VideoToolkitProcessor(context)
        val audioMixer = VideoAudioMixer(context)
        val recorder = AudioRecorderManager(context)
        val repo = MediaRepository(context)

        try {
            composeRule.setContent {
                AccessibleStudioTheme {
                    VideoFeatureWorkspaceScreen(
                        tool = VideoStudioFeatureButton.TRIMMER.toParentTool(),
                        session = VideoProjectSessionState(
                            projectId = "vid_tb",
                            title = "TalkBack Video",
                            sourceUri = null,
                            durationMs = 12_000L,
                            trimStartMs = 0L,
                            trimEndMs = 12_000L
                        ),
                        videoPlayer = videoPlayer,
                        renderEngine = renderEngine,
                        toolkit = toolkit,
                        audioMixer = audioMixer,
                        recorder = recorder,
                        mediaRepository = repo,
                        hapticsEnabled = false,
                        onSaveAndReturn = {},
                        onCancelBack = {},
                        onNavigateToExports = {},
                        initialFeature = VideoStudioFeatureButton.TRIMMER
                    )
                }
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("btn_video_workspace_play").assert(hasButtonRoleWithNonEmptyDescription())
            composeRule.onNodeWithTag("btn_trimmer_move_start_now").performScrollTo().assert(hasButtonRoleWithNonEmptyDescription())
            composeRule.onNodeWithTag("btn_trimmer_move_finish_now").performScrollTo().assert(hasButtonRoleWithNonEmptyDescription())
            composeRule.onNodeWithTag("btn_trimmer_play").performScrollTo().assert(hasButtonRoleWithNonEmptyDescription())
            composeRule.onNodeWithTag("btn_video_workspace_apply").performScrollTo().assert(hasButtonRoleWithNonEmptyDescription())
            composeRule.onNodeWithTag("section_video_splitter").assertDoesNotExist()
            composeRule.onNodeWithTag("btn_video_feature_trimmer").assertDoesNotExist()
            assertTrue(composeRule.onAllNodesWithText("Open", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        } finally {
            videoPlayer.release()
        }
    }

    @Test
    fun audioWorkspaceControls_haveTalkBackRoleButtonAndDescriptions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val audioPlayer = AudioPlayerManager(context)
        val recorder = AudioRecorderManager(context)
        val wavProcessor = WavAudioProcessor(context)
        val repo = MediaRepository(context)

        try {
            composeRule.setContent {
                AccessibleStudioTheme {
                    AudioFeatureWorkspaceScreen(
                        tool = AudioFeatureTool.TRIM_CUT,
                        session = AudioProjectSessionState(
                            projectId = "aud_tb",
                            title = "TalkBack Audio",
                            baseFileName = "TalkBackAudio",
                            pcm = ShortArray(44100) { 250 },
                            sampleRate = 44100,
                            trimStartMs = 0L,
                            trimEndMs = 1000L
                        ),
                        audioProcessor = wavProcessor,
                        playerManager = audioPlayer,
                        recorderManager = recorder,
                        mediaRepository = repo,
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
            composeRule.waitForIdle()

            listOf(
                "btn_zoom_in",
                "btn_zoom_out",
                "btn_zoom_full",
                "btn_workspace_play_selection",
                "btn_workspace_loop_selection",
                "btn_workspace_jump_start",
                "btn_workspace_jump_end",
                "btn_workspace_select_all",
                "btn_workspace_snap_zero_crossing",
                "btn_workspace_pre_roll",
                "btn_workspace_truncate_silence"
            ).forEach { tag ->
                val node = composeRule.onNodeWithTag(tag).performScrollTo()
                node.assertIsDisplayed()
                node.assert(hasButtonRoleWithNonEmptyDescription())
            }
        } finally {
            audioPlayer.release()
        }
    }
}
