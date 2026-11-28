package com.example

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.MediaRepository
import com.example.ui.studio.StudioRootCoordinator
import com.example.ui.theme.AccessibleStudioTheme
import com.example.video.VideoStudioFeatureButton
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
class HomeCategoryNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeScreen_hasCreateProjectAudioEditorAndVideoEditorButtons_andAudioEditorRevealsSeparateTools() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = MediaRepository(context)
        var batchOpenedCount = 0

        composeRule.setContent {
            AccessibleStudioTheme(isHighContrast = false) {
                StudioRootCoordinator(
                    mediaRepository = repo,
                    isHighContrast = false,
                    hapticsEnabled = false,
                    defaultExportFormat = "WAV",
                    followCursor = true,
                    onToggleHighContrast = {},
                    onOpenAbout = {},
                    onOpenSettings = {},
                    onOpenHelp = {},
                    onOpenWhatsNew = {},
                    onOpenBatchProcessing = { batchOpenedCount++ }
                )
            }
        }
        composeRule.waitForIdle()

        // Home screen has Create Project, Audio Editor, Video Editor, and button-styled Existing Projects
        composeRule.onNodeWithTag("btn_home_create_project").assertIsDisplayed()
        composeRule.onNodeWithText("Create Project").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_home_audio_editor").assertIsDisplayed()
        composeRule.onNodeWithText("Audio Editor").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_home_video_editor").assertIsDisplayed()
        composeRule.onNodeWithText("Video Editor").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_home_existing_projects").assertIsDisplayed()

        // Tap Audio Editor -> clean linear Audio Editor screen with separate accessible feature buttons
        composeRule.onNodeWithTag("btn_home_audio_editor").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("audio_editor_title").assertIsDisplayed()
        composeRule.onNodeWithTag("audio_editor_tools_list").performScrollToNode(hasTestTag("btn_audio_editor_trim"))
        composeRule.onNodeWithTag("btn_audio_editor_trim").assertExists()
        composeRule.onNodeWithTag("audio_editor_tools_list").performScrollToNode(hasTestTag("btn_audio_editor_mix"))
        composeRule.onNodeWithTag("btn_audio_editor_mix").assertExists()
        composeRule.onNodeWithTag("audio_editor_tools_list").performScrollToNode(hasTestTag("btn_audio_editor_effects"))
        composeRule.onNodeWithTag("btn_audio_editor_effects").assertExists()
        composeRule.onNodeWithTag("audio_editor_tools_list").performScrollToNode(hasTestTag("btn_audio_editor_batch"))
        composeRule.onNodeWithTag("btn_audio_editor_batch").assertExists().performClick()
        assertEquals(1, batchOpenedCount)

        composeRule.onNodeWithTag("audio_editor_tools_list").performScrollToNode(hasTestTag("btn_audio_editor_voice_over"))
        composeRule.onNodeWithTag("btn_audio_editor_voice_over").assertExists()
        composeRule.onNodeWithTag("audio_editor_tools_list").performScrollToNode(hasTestTag("btn_audio_editor_music_rebalance"))
        composeRule.onNodeWithTag("btn_audio_editor_music_rebalance").assertExists()
        composeRule.onNodeWithTag("audio_editor_tools_list").performScrollToNode(hasTestTag("btn_audio_editor_record"))
        composeRule.onNodeWithTag("btn_audio_editor_record").assertExists()
        composeRule.onNodeWithTag("audio_editor_tools_list").performScrollToNode(hasTestTag("btn_audio_editor_tone_generator"))
        composeRule.onNodeWithTag("btn_audio_editor_tone_generator").assertExists()
        composeRule.onNodeWithTag("audio_editor_tools_list").performScrollToNode(hasTestTag("btn_audio_editor_insert_silence"))
        composeRule.onNodeWithTag("btn_audio_editor_insert_silence").assertExists()
        composeRule.onNodeWithTag("audio_editor_tools_list").performScrollToNode(hasTestTag("btn_audio_editor_cue_points"))
        composeRule.onNodeWithTag("btn_audio_editor_cue_points").assertExists()
        composeRule.onNodeWithTag("audio_editor_tools_list").performScrollToNode(hasTestTag("btn_audio_editor_stats"))
        composeRule.onNodeWithTag("btn_audio_editor_stats").assertExists()
        composeRule.onNodeWithTag("btn_audio_editor_tools").assertDoesNotExist()
        assertTrue(composeRule.onAllNodesWithText("Tone, Silence, Cues & Stats", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())

        // Verify negative constraints: NO heavy desktop DAW, NO Full Video Workspace, NO Save fast/exact, NO Format/Size/Length file info
        assertTrue(composeRule.onAllNodesWithText("Full DAW Timeline", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Save fast", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Save exact, slow", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Format:", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("File Size:", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Length:", substring = true).fetchSemanticsNodes().isEmpty())

        // Navigate Home -> Video Editor and verify flat non-nested 31 features, no Full Video Workspace, no Export, and zero "Open" text
        composeRule.onNodeWithTag("btn_audio_editor_back").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("btn_home_video_editor").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("video_editor_title").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_video_editor_full_video_studio").assertDoesNotExist()
        composeRule.onNodeWithTag("btn_video_editor_trim_timeline").assertDoesNotExist()
        assertTrue(composeRule.onAllNodesWithText("Full Video Workspace", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Open", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        VideoStudioFeatureButton.entries.forEach { feat ->
            val tag = "btn_video_editor_feature_${feat.tag}"
            composeRule.onNodeWithTag("video_editor_tools_list").performScrollToNode(hasTestTag(tag))
            val node = composeRule.onNodeWithTag(tag).assertExists().fetchSemanticsNode()
            val textJoined = node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString(" ") { it.text }
            val descJoined = node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.joinToString(" ")
            assertFalse("Video button $tag text must not contain 'open': $textJoined", textJoined.contains("open", ignoreCase = true))
            assertFalse("Video button $tag description must not contain 'open': $descJoined", descJoined.contains("open", ignoreCase = true))
            assertFalse("Video feature list must not include Export: ${feat.title}", feat.title.equals("Export", ignoreCase = true))
        }
    }

    @Test
    fun audioEditorEffects_startsBlankCanvas_savesProjectAtAnyTime_andOpensExportDialog() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = MediaRepository(context)

        composeRule.setContent {
            AccessibleStudioTheme(isHighContrast = false) {
                StudioRootCoordinator(
                    mediaRepository = repo,
                    isHighContrast = false,
                    hapticsEnabled = false,
                    defaultExportFormat = "WAV",
                    followCursor = true,
                    onToggleHighContrast = {},
                    onOpenAbout = {},
                    onOpenSettings = {},
                    onOpenHelp = {},
                    onOpenWhatsNew = {},
                    onOpenBatchProcessing = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Navigate Home -> Audio Editor -> Effects -> Start Blank Audio Canvas
        composeRule.onNodeWithTag("btn_home_audio_editor").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("audio_editor_tools_list").performScrollToNode(hasTestTag("btn_audio_editor_effects"))
        composeRule.onNodeWithTag("btn_audio_editor_effects").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("dialog_audio_source_prompt").assertExists()
        composeRule.onNodeWithTag("btn_prompt_blank_audio").performClick()
        composeRule.waitForIdle()

        // Directly inside Effects workspace! Tap Save to return to ProjectHub
        composeRule.onNodeWithTag("workspace_title").assertExists()
        composeRule.onNodeWithText("Effects").assertExists()
        composeRule.onNodeWithTag("btn_workspace_save").performClick()
        composeRule.waitForIdle()

        // On ProjectHub: Save Project is always visible before AND after saving, Batch button is present, Full DAW is absent
        composeRule.onNodeWithTag("btn_hub_save").assertExists().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Saved").assertExists()
        // Crucial: Save Project button must STILL exist after already Saved!
        composeRule.onNodeWithTag("btn_hub_save").assertExists()
        composeRule.onNodeWithTag("hub_features_grid").performScrollToNode(hasTestTag("hub_feature_audio_batch"))
        composeRule.onNodeWithTag("hub_feature_audio_batch").assertExists()
        composeRule.onNodeWithTag("hub_features_grid").performScrollToNode(hasTestTag("hub_feature_audio_voice_over"))
        composeRule.onNodeWithTag("hub_feature_audio_voice_over").assertExists()
        composeRule.onNodeWithTag("hub_features_grid").performScrollToNode(hasTestTag("hub_feature_audio_tone_generator"))
        composeRule.onNodeWithTag("hub_feature_audio_tone_generator").assertExists()
        composeRule.onNodeWithTag("hub_features_grid").performScrollToNode(hasTestTag("hub_feature_audio_insert_silence"))
        composeRule.onNodeWithTag("hub_feature_audio_insert_silence").assertExists()
        composeRule.onNodeWithTag("hub_features_grid").performScrollToNode(hasTestTag("hub_feature_audio_cue_points"))
        composeRule.onNodeWithTag("hub_feature_audio_cue_points").assertExists()
        composeRule.onNodeWithTag("hub_features_grid").performScrollToNode(hasTestTag("hub_feature_audio_stats"))
        composeRule.onNodeWithTag("hub_feature_audio_stats").assertExists()
        composeRule.onNodeWithTag("hub_feature_audio_tools").assertDoesNotExist()
        assertTrue(composeRule.onAllNodesWithText("Tone, Silence, Cues & Stats", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Full DAW Timeline", substring = true).fetchSemanticsNodes().isEmpty())

        // Final audio export dialog still opens and stays intact
        composeRule.onNodeWithTag("btn_hub_export").assertExists().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("accessible_export_dialog").assertExists()
    }

    @Test
    fun restoredAudioWorkspaces_exposeClipboardCuesFiltersRecorderAndProToolsWithoutDawClutter() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = MediaRepository(context)
        val player = com.example.audio.AudioPlayerManager(context)
        val recorder = com.example.audio.AudioRecorderManager(context)
        val processor = com.example.audio.WavAudioProcessor(context)
        val activeToolState = androidx.compose.runtime.mutableStateOf(com.example.ui.studio.AudioFeatureTool.TRIM_CUT)
        val session = com.example.ui.studio.AudioProjectSessionState(
            projectId = "restored_tools_test",
            title = "Restored Audio Track",
            baseFileName = "RestoredAudio",
            pcm = ShortArray(44100) { 300 },
            sampleRate = 44100,
            trimStartMs = 0L,
            trimEndMs = 1000L
        )

        try {
            composeRule.setContent {
                AccessibleStudioTheme(isHighContrast = false) {
                    com.example.ui.studio.AudioFeatureWorkspaceScreen(
                        tool = activeToolState.value,
                        session = session,
                        audioProcessor = processor,
                        playerManager = player,
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
                        onCancelBack = {}
                    )
                }
            }
            composeRule.waitForIdle()

            // 1. TRIM_CUT places transport buttons together in exact order: Rewind -> Play/Pause -> Forward -> Stop
            composeRule.onNodeWithTag("row_workspace_transport_controls").assertIsDisplayed()
            val rewBounds = composeRule.onNodeWithTag("btn_workspace_rewind_scrub").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val playBounds = composeRule.onNodeWithTag("btn_workspace_play_pause").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val fwdBounds = composeRule.onNodeWithTag("btn_workspace_forward_scrub").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val stopBounds = composeRule.onNodeWithTag("btn_workspace_stop").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val orderedSingleRow = rewBounds.top == fwdBounds.top &&
                rewBounds.left < playBounds.left &&
                playBounds.left < fwdBounds.left &&
                fwdBounds.left < stopBounds.left
            val orderedTwoRows = rewBounds.left < playBounds.left &&
                fwdBounds.left < stopBounds.left &&
                playBounds.bottom <= fwdBounds.top
            assertTrue("Expected transport order Rewind -> Play/Pause -> Forward -> Stop", orderedSingleRow || orderedTwoRows)

            // TRIM_CUT also exposes Cut, Copy, Paste Cursor/End, Mix Paste, Insert Silence, Cue Points, Play/Loop Selection
            listOf(
                "btn_workspace_play_selection",
                "btn_workspace_loop_selection",
                "btn_workspace_cut",
                "btn_workspace_copy",
                "btn_workspace_paste_cursor",
                "btn_workspace_paste_end",
                "btn_workspace_mix_paste",
                "btn_workspace_insert_silence",
                "btn_workspace_cue_points"
            ).forEach { tag ->
                composeRule.onNodeWithTag(tag).assertExists()
            }

            // 2. VOICE_OVER exposes browse, auto-duck switch, preview and apply
            composeRule.runOnUiThread { activeToolState.value = com.example.ui.studio.AudioFeatureTool.VOICE_OVER }
            composeRule.waitForIdle()
            listOf(
                "btn_vo_workspace_pick_audio",
                "switch_vo_workspace_auto_duck",
                "btn_vo_workspace_preview",
                "btn_vo_workspace_apply"
            ).forEach { tag ->
                composeRule.onNodeWithTag(tag).assertExists()
            }

            // 3. EFFECTS_CLEANING exposes Fade In/Out, Custom Gain, Normalize RMS, Filters, Mechanize & all Pro DSP categories
            composeRule.runOnUiThread { activeToolState.value = com.example.ui.studio.AudioFeatureTool.EFFECTS_CLEANING }
            composeRule.waitForIdle()
            listOf(
                "btn_fx_fade_in",
                "btn_fx_fade_out",
                "btn_fx_custom_gain",
                "btn_fx_normalize_rms",
                "btn_fx_highpass",
                "btn_fx_lowpass",
                "btn_fx_bandpass",
                "btn_fx_mechanize",
                "btn_fx_tab_pro_suite",
                "btn_fx_tab_pro_channels",
                "btn_fx_tab_pro_filter",
                "btn_fx_tab_pro_volume"
            ).forEach { tag ->
                composeRule.onNodeWithTag(tag).assertExists()
            }
            composeRule.onNodeWithTag("btn_fx_tab_pro_suite").performScrollTo().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("card_pro_audio_suite").performScrollTo().assertExists()
            composeRule.onNodeWithTag("btn_effect_censor").assertExists()
            composeRule.onNodeWithTag("btn_effect_pitch").assertExists()
            composeRule.onNodeWithTag("btn_effect_time").assertExists()

            composeRule.onNodeWithTag("btn_fx_tab_pro_channels").performScrollTo().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("btn_ch_mixer").assertExists()

            composeRule.onNodeWithTag("btn_fx_tab_pro_filter").performScrollTo().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("btn_filter_parametric_eq").assertExists()

            composeRule.onNodeWithTag("btn_fx_tab_pro_volume").performScrollTo().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("btn_vol_shape").assertExists()

            // 4. RECORD_VOICE exposes Replace, Append, Punch-In, Buffer chips & Mic Gain slider
            composeRule.runOnUiThread { activeToolState.value = com.example.ui.studio.AudioFeatureTool.RECORD_VOICE }
            composeRule.waitForIdle()
            listOf(
                "btn_workspace_start_record",
                "btn_workspace_append_record",
                "btn_workspace_punch_in_record",
                "chip_workspace_buffer_2048",
                "slider_workspace_mic_gain"
            ).forEach { tag ->
                composeRule.onNodeWithTag(tag).assertExists()
            }

            // 5. Separated utilities: TONE_GENERATOR, INSERT_SILENCE, CUE_POINTS, AUDIO_STATS (each with dedicated workspace & no bundled Pro DSP)
            composeRule.runOnUiThread { activeToolState.value = com.example.ui.studio.AudioFeatureTool.TONE_GENERATOR }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("card_workspace_tone_generator").assertExists()
            composeRule.onNodeWithTag("btn_tools_tone_generator").assertExists()
            composeRule.onNodeWithTag("card_pro_audio_suite").assertDoesNotExist()

            composeRule.runOnUiThread { activeToolState.value = com.example.ui.studio.AudioFeatureTool.INSERT_SILENCE }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("card_workspace_insert_silence").assertExists()
            composeRule.onNodeWithTag("btn_tools_insert_silence").assertExists()
            composeRule.onNodeWithTag("card_pro_audio_suite").assertDoesNotExist()

            composeRule.runOnUiThread { activeToolState.value = com.example.ui.studio.AudioFeatureTool.CUE_POINTS }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("card_workspace_cue_points").assertExists()
            composeRule.onNodeWithTag("btn_tools_drop_cue").assertExists()
            composeRule.onNodeWithTag("btn_tools_prev_cue").assertExists()
            composeRule.onNodeWithTag("btn_tools_next_cue").assertExists()
            composeRule.onNodeWithTag("btn_tools_cue_points").assertExists()
            composeRule.onNodeWithTag("card_pro_audio_suite").assertDoesNotExist()

            composeRule.runOnUiThread { activeToolState.value = com.example.ui.studio.AudioFeatureTool.AUDIO_STATS }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("card_workspace_audio_stats").assertExists()
            composeRule.onNodeWithTag("btn_tools_audio_stats").assertExists()
            composeRule.onNodeWithTag("card_pro_audio_suite").assertDoesNotExist()

            assertTrue(composeRule.onAllNodesWithText("Full DAW Timeline", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
            assertTrue(composeRule.onAllNodesWithText("Save fast", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
            assertTrue(composeRule.onAllNodesWithText("Format:", substring = true).fetchSemanticsNodes().isEmpty())
            assertTrue(composeRule.onAllNodesWithText("File Size:", substring = true).fetchSemanticsNodes().isEmpty())
            assertTrue(composeRule.onAllNodesWithText("Length:", substring = true).fetchSemanticsNodes().isEmpty())
        } finally {
            player.release()
        }
    }
}
