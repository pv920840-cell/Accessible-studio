package com.example

import android.content.Context
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioPlayerManager
import com.example.audio.AudioRecorderManager
import com.example.audio.StudioAudioProcessor
import com.example.audio.WavAudioProcessor
import com.example.data.MediaRepository
import com.example.ui.studio.AudioFeatureTool
import com.example.ui.studio.AudioFeatureWorkspaceScreen
import com.example.ui.studio.AudioProjectSessionState
import com.example.ui.studio.StudioRootCoordinator
import com.example.ui.theme.AccessibleStudioTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrimSaveExportBugFixTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun trimMarkers_thenSave_commitsTrimmedPcmAndExportsTrimmedDuration() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sr = StudioAudioProcessor.SAMPLE_RATE
        // 6 seconds of audio with distinct frequency/amplitude so non-zero everywhere
        val raw = ShortArray(6 * sr) { i ->
            (sin(2.0 * PI * 440.0 * i / sr) * 14000.0).toInt().toShort()
        }
        val session = AudioProjectSessionState(
            projectId = "trim_bug_proj",
            title = "Trim Test Track",
            baseFileName = "TrimTestTrack",
            pcm = raw,
            sampleRate = sr,
            trimStartMs = 0L,
            trimEndMs = 6000L
        )
        val audioProcessor = WavAudioProcessor(context)
        val playerManager = AudioPlayerManager(context)
        val recorderManager = AudioRecorderManager(context)
        val repo = MediaRepository(context)
        var savedSession: AudioProjectSessionState? = null

        composeRule.setContent {
            AccessibleStudioTheme(isHighContrast = false) {
                AudioFeatureWorkspaceScreen(
                    tool = AudioFeatureTool.TRIM_CUT,
                    session = session,
                    audioProcessor = audioProcessor,
                    playerManager = playerManager,
                    recorderManager = recorderManager,
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
                    onSaveAndReturn = { savedSession = it },
                    onCancelBack = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Adjust Start +2s (2000ms) and End -1s (5000ms) -> 3000ms trimmed selection, then press Save
        composeRule.onNodeWithTag("btn_start_plus_1s").performScrollTo().performClick()
        composeRule.onNodeWithTag("btn_start_plus_1s").performClick()
        composeRule.onNodeWithTag("btn_end_minus_1s").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("btn_workspace_save").performClick()
        composeRule.waitForIdle()

        val updated = savedSession!!
        assertTrue("Saved session should be marked modified", updated.hasUnsavedChanges)
        assertEquals("Saved PCM should be trimmed to 3 seconds", 3 * sr, updated.pcm.size)
        assertEquals(0L, updated.trimStartMs)
        assertEquals(3000L, updated.trimEndMs)

        // Final export from saved session must also write 3 seconds (~264,600 bytes PCM + 44 byte WAV header)
        val outFile = audioProcessor.exportSingleAudioFile(
            mainPcm = updated.pcm,
            bgTrackConfig = null,
            bgPcm = null,
            settings = updated.editSettings.copy(
                trimStartMs = updated.trimStartMs,
                trimEndMs = updated.trimEndMs
            ),
            outputFileName = "TrimmedOutput_${System.currentTimeMillis()}",
            format = "WAV",
            onProgress = {}
        )
        assertTrue(outFile.exists())
        val expectedWavBytes = (3 * sr * 2L) + 44L
        assertEquals("Exported WAV file size must equal trimmed 3s audio", expectedWavBytes, outFile.length())
        outFile.delete()
        playerManager.release()
    }

    @Test
    fun muteSelection_thenSave_preservesFullDurationWithoutAutoCropping() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sr = StudioAudioProcessor.SAMPLE_RATE
        val raw = ShortArray(6 * sr) { i ->
            (sin(2.0 * PI * 440.0 * i / sr) * 14000.0).toInt().toShort()
        }
        val session = AudioProjectSessionState(
            projectId = "mute_keep_len",
            title = "Mute Keep Length",
            pcm = raw,
            sampleRate = sr,
            trimStartMs = 0L,
            trimEndMs = 6000L
        )
        val audioProcessor = WavAudioProcessor(context)
        val playerManager = AudioPlayerManager(context)
        val recorderManager = AudioRecorderManager(context)
        val repo = MediaRepository(context)
        var savedSession: AudioProjectSessionState? = null

        composeRule.setContent {
            AccessibleStudioTheme(isHighContrast = false) {
                AudioFeatureWorkspaceScreen(
                    tool = AudioFeatureTool.TRIM_CUT,
                    session = session,
                    audioProcessor = audioProcessor,
                    playerManager = playerManager,
                    recorderManager = recorderManager,
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
                    onSaveAndReturn = { savedSession = it },
                    onCancelBack = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("btn_start_plus_1s").performScrollTo().performClick()
        composeRule.onNodeWithTag("btn_end_minus_1s").performScrollTo().performClick()
        composeRule.onNodeWithTag("btn_workspace_mute_selection").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodes(hasTestTag("btn_workspace_undo")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("btn_workspace_save").performClick()
        composeRule.waitForIdle()

        val updated = savedSession!!
        assertEquals("Muting must preserve full 6s timeline", 6 * sr, updated.pcm.size)
        assertTrue("Selected middle region (2.5s) should be muted to 0", abs(updated.pcm[(2.5 * sr).toInt()].toInt()) == 0)
        playerManager.release()
    }

    @Test
    fun coordinatorFlow_trimAndSave_updatesProjectHubDuration() {
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

        composeRule.onNodeWithTag("btn_home_create_project").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("btn_create_quick_start").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("hub_features_grid").performScrollToNode(hasTestTag(AudioFeatureTool.TRIM_CUT.tag))
        composeRule.onNodeWithTag(AudioFeatureTool.TRIM_CUT.tag).performClick()
        composeRule.waitForIdle()

        // Trim 5s blank track down by 1s from start -> 4s
        composeRule.onNodeWithTag("btn_start_plus_1s").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("btn_workspace_save").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("hub_project_title").assertExists()
        composeRule.onNodeWithText("00:00.0 / 00:04.0").assertExists()
        composeRule.onNodeWithText("Edited").assertExists()
    }
}
