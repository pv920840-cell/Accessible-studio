package com.example

import android.content.Context
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioPlayerManager
import com.example.audio.AudioRecorderManager
import com.example.audio.WavAudioProcessor
import com.example.data.MediaRepository
import com.example.data.StudioPreferences
import com.example.ui.components.VideoOverlaysVfxPanel
import com.example.ui.components.VideoTimelineToolsPanel
import com.example.ui.studio.AudioFeatureTool
import com.example.ui.studio.AudioFeatureWorkspaceScreen
import com.example.ui.studio.AudioProjectSessionState
import com.example.ui.studio.ProjectHubScreen
import com.example.ui.studio.ProjectMediaType
import com.example.ui.studio.StudioProjectSummary
import com.example.ui.studio.VideoFeatureTool
import com.example.ui.theme.AccessibleStudioTheme
import com.example.video.BlurRegionConfig
import com.example.video.ImageOverlayConfig
import com.example.video.TextAnimationStyle
import com.example.video.TextOverlayPosition
import com.example.video.VideoFilter
import com.example.video.VideoStudioFeatureButton
import com.example.video.VideoVfx
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FeatureButtonsAndSlowScrubTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun projectHubScreen_showsSeparateVideoFeatureButtons_whilePreservingProjectFlow() {
        var openedFeature: VideoStudioFeatureButton? = null
        var openedTool: VideoFeatureTool? = null
        val project = StudioProjectSummary(
            id = "vid_proj_buttons",
            title = "Vlog Episode",
            mediaType = ProjectMediaType.VIDEO,
            sourceUri = null,
            filePath = null,
            durationMillis = 15_000L,
            fileSizeBytes = 4096L,
            lastModifiedMillis = 1_700_000_100_000L,
            statusBadge = "Ready"
        )

        composeRule.setContent {
            AccessibleStudioTheme {
                ProjectHubScreen(
                    project = project,
                    durationMs = 15_000L,
                    currentPositionMs = 0L,
                    isPlaying = false,
                    hasUnsavedChanges = true,
                    onTogglePreviewPlay = {},
                    onOpenAudioTool = {},
                    onOpenVideoTool = { openedTool = it },
                    onExportProject = {},
                    onBackToHome = {},
                    onSaveProject = {},
                    onOpenVideoFeature = { openedFeature = it }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("btn_hub_save").assertExists()
        composeRule.onNodeWithTag("btn_hub_back_home").assertExists()
        composeRule.onNodeWithTag("btn_hub_export").assertExists()
        composeRule.onNodeWithTag("hub_feature_video_trim").assertExists()

        composeRule.onNodeWithTag("hub_features_grid")
            .performScrollToNode(hasTestTag("hub_video_feature_trimmer"))
        composeRule.onNodeWithTag("hub_video_feature_trimmer").assertIsDisplayed().performClick()
        assertEquals(VideoStudioFeatureButton.TRIMMER, openedFeature)

        composeRule.onNodeWithTag("hub_features_grid")
            .performScrollToNode(hasTestTag("hub_video_feature_color_filters"))
        composeRule.onNodeWithTag("hub_video_feature_color_filters").assertIsDisplayed().performClick()
        assertEquals(VideoStudioFeatureButton.COLOR_FILTERS, openedFeature)

        composeRule.onNodeWithTag("hub_features_grid")
            .performScrollToNode(hasTestTag("hub_feature_video_full"))
        composeRule.onNodeWithTag("hub_feature_video_full").performClick()
        assertEquals(VideoFeatureTool.FULL_VIDEO_STUDIO, openedTool)
    }

    @Test
    fun videoOverlaysAndTimelinePanels_provideSeparateFeatureButtons_andColorVfxButtons() {
        var selectedFilter = VideoFilter.NORMAL
        var selectedVfx = VideoVfx.NONE

        composeRule.setContent {
            AccessibleStudioTheme {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    VideoTimelineToolsPanel(
                        currentPositionMs = 0L,
                        durationMs = 10_000L,
                        trimStartMs = 0L,
                        trimEndMs = 8_000L,
                        streamCopyTrim = false,
                        onUpdateTrimRange = { _, _ -> },
                        onToggleStreamCopyTrim = {},
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
                        onExportSnapshots = {}
                    )
                    VideoOverlaysVfxPanel(
                        durationMs = 10_000L,
                        textOverlay = "",
                        textPosition = TextOverlayPosition.BOTTOM_SUBTITLE,
                        textAnimStyle = TextAnimationStyle.STATIC,
                        onChangeTextOverlay = {},
                        onSelectTextPosition = {},
                        onSelectTextAnimStyle = {},
                        blurConfig = BlurRegionConfig(),
                        onUpdateBlurConfig = {},
                        imageOverlay = ImageOverlayConfig(),
                        onPickOverlayImage = {},
                        onUpdateImageOverlay = {},
                        subtitleCues = emptyList(),
                        onImportSubtitleFile = {},
                        onLoadInlineSrtText = {},
                        onClearSubtitles = {},
                        selectedFilter = selectedFilter,
                        selectedVfx = selectedVfx,
                        onSelectFilter = { selectedFilter = it },
                        onSelectVfx = { selectedVfx = it }
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("section_video_trimmer").performScrollTo().assertExists()
        composeRule.onNodeWithTag("btn_video_feature_cutter").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("section_video_trimmer").assertDoesNotExist()
        composeRule.onAllNodesWithTag("btn_video_feature_all").onFirst().performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("section_video_trimmer").performScrollTo().assertExists()

        composeRule.onNodeWithTag("section_video_color_filters").performScrollTo().assertExists()
        composeRule.onNodeWithTag("section_video_vfx_effects").performScrollTo().assertExists()
        composeRule.onNodeWithTag("btn_video_filter_vivid_warm").performScrollTo().performClick()
        composeRule.onNodeWithTag("btn_video_vfx_cinematic_letterbox").performScrollTo().performClick()
        assertEquals(VideoFilter.VIVID_WARM, selectedFilter)
        assertEquals(VideoVfx.CINEMATIC_LETTERBOX, selectedVfx)
    }

    @Test
    fun audioEffectsWorkspace_showsIndividualEffectButtons_andFocusesPanels() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = AudioPlayerManager(context)
        val recorder = AudioRecorderManager(context)
        val processor = WavAudioProcessor(context)
        val mediaRepo = MediaRepository(context)
        val session = AudioProjectSessionState(
            projectId = "fx_buttons_test",
            title = "Effects Buttons Track",
            baseFileName = "FxButtons",
            pcm = ShortArray(22050) { 250 },
            sampleRate = 44100,
            trimStartMs = 0L,
            trimEndMs = 500L
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
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("btn_fx_auto_gain").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("btn_fx_limiter").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("btn_fx_flanger").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("btn_fx_chorus").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("btn_fx_declick").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("btn_fx_reverse").performScrollTo().assertIsDisplayed()

            composeRule.onNodeWithTag("btn_fx_tab_eq").performScrollTo().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("card_fx_eq").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("btn_fx_eq_vocal_boost").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("card_fx_reverb").assertDoesNotExist()

            composeRule.onNodeWithTag("btn_fx_tab_all").performScrollTo().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("card_fx_reverb").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("btn_workspace_apply_fx").performScrollTo().assertIsDisplayed()
        } finally {
            player.release()
        }
    }
}
