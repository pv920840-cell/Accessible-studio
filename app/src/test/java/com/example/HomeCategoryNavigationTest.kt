package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.MediaRepository
import com.example.ui.studio.StudioRootCoordinator
import com.example.ui.theme.AccessibleStudioTheme
import org.junit.Assert.assertEquals
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

        composeRule.onNodeWithTag("audio_editor_tools_list").performScrollToNode(hasTestTag("btn_audio_editor_music_rebalance"))
        composeRule.onNodeWithTag("btn_audio_editor_music_rebalance").assertExists()
        composeRule.onNodeWithTag("audio_editor_tools_list").performScrollToNode(hasTestTag("btn_audio_editor_record"))
        composeRule.onNodeWithTag("btn_audio_editor_record").assertExists()

        // Verify negative constraints: NO heavy desktop DAW, NO Save fast/exact, NO Format/Size/Length file info
        assertTrue(composeRule.onAllNodesWithText("Full DAW Timeline", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Save fast", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Save exact, slow", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Format:", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("File Size:", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Length:", substring = true).fetchSemanticsNodes().isEmpty())
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

        // Directly inside Effects workspace! Tap Auto Gain, then Save to return to ProjectHub
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
        assertTrue(composeRule.onAllNodesWithText("Full DAW Timeline", substring = true).fetchSemanticsNodes().isEmpty())

        // Final audio export dialog still opens and stays intact
        composeRule.onNodeWithTag("btn_hub_export").assertExists().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("accessible_export_dialog").assertExists()
    }
}
