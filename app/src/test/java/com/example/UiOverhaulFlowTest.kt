package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioRecorderManager
import com.example.audio.StudioAudioProcessor
import com.example.audio.WavAudioProcessor
import com.example.data.MediaRepository
import com.example.ui.studio.AudioFeatureTool
import com.example.ui.studio.AudioFeatureWorkspaceScreen
import com.example.ui.studio.AudioProjectSessionState
import com.example.ui.studio.CreateProjectScreen
import com.example.ui.studio.ProjectHubScreen
import com.example.ui.studio.ProjectMediaType
import com.example.ui.studio.StudioHomeScreen
import com.example.ui.studio.StudioProjectSummary
import com.example.ui.studio.StudioRootCoordinator
import com.example.ui.studio.VideoFeatureTool
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
class UiOverhaulFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun step1_homeScreen_displaysProjectsCreateButtonAndSecondaryNav() {
        val sampleProjects = listOf(
            StudioProjectSummary(
                id = "proj_audio_1",
                title = "Podcast Episode 1",
                mediaType = ProjectMediaType.AUDIO,
                sourceUri = null,
                filePath = null,
                durationMillis = 12_000L,
                fileSizeBytes = 1024L,
                lastModifiedMillis = 1_700_000_000_000L,
                statusBadge = "Exported"
            ),
            StudioProjectSummary(
                id = "proj_video_2",
                title = "Highlight Reel",
                mediaType = ProjectMediaType.VIDEO,
                sourceUri = null,
                filePath = null,
                durationMillis = 8_000L,
                fileSizeBytes = 2048L,
                lastModifiedMillis = 1_700_000_050_000L,
                statusBadge = "Ready"
            )
        )

        var createClicked = false
        var openedProject: StudioProjectSummary? = null

        composeRule.setContent {
            AccessibleStudioTheme(isHighContrast = false) {
                StudioHomeScreen(
                    projects = sampleProjects,
                    onCreateNewProject = { createClicked = true },
                    onOpenExistingProject = { openedProject = it },
                    onOpenExports = {},
                    onOpenAbout = {},
                    onOpenSettings = {},
                    onOpenWhatsNew = {},
                    onOpenHelp = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("home_hero_card").assertExists()
        composeRule.onNodeWithTag("btn_home_create_project").assertExists()
        composeRule.onNodeWithTag("btn_home_exports").assertExists()
        composeRule.onNodeWithTag("btn_home_settings").assertExists()
        composeRule.onNodeWithTag("btn_home_about").assertDoesNotExist()
        composeRule.onNodeWithTag("btn_home_whats_new").assertDoesNotExist()
        composeRule.onNodeWithTag("btn_home_help").assertDoesNotExist()

        composeRule.onNodeWithTag("project_card_proj_audio_1").assertExists().performClick()
        assertEquals("proj_audio_1", openedProject?.id)

        composeRule.onNodeWithTag("btn_home_create_project").performClick()
        assertTrue(createClicked)
    }

    @Test
    fun step2_createProjectScreen_selectsAudioOrVideo_andInvokesBrowsePicker() {
        var selectedType = ProjectMediaType.AUDIO
        var browseCount = 0

        composeRule.setContent {
            AccessibleStudioTheme(isHighContrast = false) {
                CreateProjectScreen(
                    selectedType = selectedType,
                    projectNameDraft = "",
                    isLoadingMedia = false,
                    loadingMessage = "",
                    onSelectType = { selectedType = it },
                    onProjectNameChange = {},
                    onBrowseAndSelectFile = { browseCount++ },
                    onStartQuickBlankOrDemo = {},
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("card_create_type_audio").assertExists()
        composeRule.onNodeWithTag("card_create_type_video").assertExists().performClick()
        assertEquals(ProjectMediaType.VIDEO, selectedType)

        composeRule.onNodeWithTag("create_file_selection_panel").assertExists()
        composeRule.onNodeWithTag("input_create_project_name").assertExists()
        composeRule.onNodeWithTag("btn_create_browse_file").performScrollTo().assertExists().performClick()
        assertEquals(1, browseCount)
        composeRule.onNodeWithTag("btn_create_quick_start").performScrollTo().assertExists()
    }

    @Test
    fun step3_projectHubScreen_showsTailoredAudioAndVideoToolsAndExportButton() {
        val videoProj = StudioProjectSummary(
            id = "v1",
            title = "Studio Video Clip",
            mediaType = ProjectMediaType.VIDEO,
            sourceUri = null,
            filePath = null,
            durationMillis = 6_000L,
            fileSizeBytes = 4096L,
            lastModifiedMillis = 1_700_000_000_000L
        )
        var exportClicked = false
        var saveClicked = false
        var backClicked = false
        var openedVideoTool: VideoFeatureTool? = null

        composeRule.setContent {
            AccessibleStudioTheme(isHighContrast = false) {
                ProjectHubScreen(
                    project = videoProj,
                    durationMs = 6_000L,
                    currentPositionMs = 0L,
                    isPlaying = false,
                    hasUnsavedChanges = false,
                    onTogglePreviewPlay = {},
                    onOpenAudioTool = {},
                    onOpenVideoTool = { openedVideoTool = it },
                    onExportProject = { exportClicked = true },
                    onBackToHome = { backClicked = true },
                    onSaveProject = { saveClicked = true }
                )
            }
        }
        composeRule.waitForIdle()

        val saveNode = composeRule.onNodeWithTag("btn_hub_save").assertExists()
        val backNode = composeRule.onNodeWithTag("btn_hub_back_home").assertExists()
        assertTrue(
            "Save button must appear before Go Back to Home Screen button",
            saveNode.getUnclippedBoundsInRoot().left < backNode.getUnclippedBoundsInRoot().left
        )
        saveNode.performClick()
        assertTrue(saveClicked)
        backNode.performClick()
        assertTrue(backClicked)

        composeRule.onNodeWithTag("hub_project_title").assertExists()
        VideoFeatureTool.values().forEach { tool ->
            composeRule.onNodeWithTag("hub_features_grid").performScrollToNode(hasTestTag(tool.tag))
            composeRule.onNodeWithTag(tool.tag).assertExists()
        }
        composeRule.onNodeWithTag("hub_features_grid").performScrollToNode(hasTestTag(VideoFeatureTool.OVERLAYS_VFX.tag))
        composeRule.onNodeWithTag(VideoFeatureTool.OVERLAYS_VFX.tag).performClick()
        assertEquals(VideoFeatureTool.OVERLAYS_VFX, openedVideoTool)

        composeRule.onNodeWithTag("btn_hub_export").assertExists().performClick()
        assertTrue(exportClicked)
    }

    @Test
    fun step4_endToEndCoordinatorFlow_createBlankAudio_openTrimWorkspace_editAndSaveReturnsToHub() {
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

        // Step 1: Home Screen -> Create New Project
        composeRule.onNodeWithTag("btn_home_create_project").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        // Step 2: Create Project -> Choose Audio (default) & start blank canvas -> Main Project Screen
        composeRule.onNodeWithTag("card_create_type_audio").assertExists()
        composeRule.onNodeWithTag("btn_create_quick_start").performScrollTo().performClick()
        composeRule.waitForIdle()

        // Step 3: Main Project Screen (Feature Hub) - Save button FIRST, then Go Back to Home Screen
        composeRule.onNodeWithTag("hub_project_title").assertExists()
        val blankSaveBounds = composeRule.onNodeWithTag("btn_hub_save").assertExists().getUnclippedBoundsInRoot()
        val blankBackBounds = composeRule.onNodeWithTag("btn_hub_back_home").assertExists().getUnclippedBoundsInRoot()
        assertTrue("Save button should appear before Go Back to Home Screen on blank project", blankSaveBounds.left < blankBackBounds.left)
        composeRule.onNodeWithTag("btn_hub_save").performClick()
        composeRule.waitForIdle()
        // Clicking Save must save as a project, NOT trigger final audio export dialog
        composeRule.onNodeWithTag("accessible_export_dialog").assertDoesNotExist()
        assertTrue("Final exports should remain empty when saving project", repo.exportedFiles.value.isEmpty())

        composeRule.onNodeWithTag("btn_hub_export").assertExists()
        composeRule.onNodeWithTag("hub_status_badge").assertExists()
        composeRule.onNodeWithText("Saved").assertExists()

        // Verify persisted saved project appears on Home Screen under Existing Projects
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            com.example.data.StudioProjectStorage.loadSavedProjects(context).any { it.statusBadge == "Saved" }
        }

        // Open Step 4: Trim & Cut Workspace
        composeRule.onNodeWithTag("hub_features_grid")
            .performScrollToNode(hasTestTag(AudioFeatureTool.TRIM_CUT.tag))
        composeRule.onNodeWithTag(AudioFeatureTool.TRIM_CUT.tag).performClick()
        composeRule.waitForIdle()

        // Inside dedicated workspace: perform Mute Selection and tap Save to return to Main Project Screen
        composeRule.onNodeWithTag("workspace_title").assertExists()
        composeRule.onNodeWithTag("btn_workspace_mute_selection").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodes(hasTestTag("btn_workspace_undo")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("btn_workspace_save").assertExists().performClick()
        composeRule.waitForIdle()

        // Returned to Project Hub with Edited status
        composeRule.onNodeWithTag("hub_project_title").assertExists()
        composeRule.onNodeWithTag("btn_hub_export").assertExists()
        composeRule.onNodeWithText("Edited").assertExists()
        composeRule.onNodeWithTag("btn_hub_save").assertExists().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("accessible_export_dialog").assertDoesNotExist()
        composeRule.onNodeWithText("Saved").assertExists()

        // Go back to Home Screen and verify saved project is listed
        composeRule.onNodeWithTag("btn_hub_back_home").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_projects_list").assertExists()
        composeRule.onNodeWithText("Blank Audio Project").assertExists()
    }
}
