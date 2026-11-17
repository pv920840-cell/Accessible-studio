package com.example

import android.content.Context
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioPlayerManager
import com.example.audio.AudioRecorderManager
import com.example.audio.StudioAudioProcessor
import com.example.audio.WavAudioProcessor
import com.example.data.MediaRepository
import com.example.ui.components.AudioStudioCommand
import com.example.ui.components.StudioDawTopBar
import com.example.ui.studio.AudioFeatureTool
import com.example.ui.studio.AudioFeatureWorkspaceScreen
import com.example.ui.studio.AudioProjectSessionState
import com.example.ui.theme.AccessibleStudioTheme
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
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AutoGainFeatureTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun rmsRange(samples: ShortArray, start: Int, end: Int): Double {
        var sum = 0.0
        val count = (end - start).coerceAtLeast(1)
        for (i in start until end) {
            val v = samples[i].toDouble()
            sum += v * v
        }
        return sqrt(sum / count)
    }

    @Test
    fun applyAutoGain_levelsQuietAndLoudSectionsToEvenLoudness() {
        val sr = StudioAudioProcessor.SAMPLE_RATE
        val halfLen = sr // 1 second quiet + 1 second loud
        val input = ShortArray(halfLen * 2) { idx ->
            val amp = if (idx < halfLen) 1500.0 else 26000.0
            val s = sin(2.0 * PI * 440.0 * idx / sr) * amp
            s.toInt().toShort()
        }

        val beforeQuietRms = rmsRange(input, sr / 4, (3 * sr) / 4)
        val beforeLoudRms = rmsRange(input, halfLen + sr / 4, halfLen + (3 * sr) / 4)
        val beforeRatio = beforeLoudRms / beforeQuietRms

        val leveled = StudioAudioProcessor.applyAutoGain(input)

        assertEquals(input.size, leveled.size)
        val afterQuietRms = rmsRange(leveled, sr / 4, (3 * sr) / 4)
        val afterLoudRms = rmsRange(leveled, halfLen + sr / 4, halfLen + (3 * sr) / 4)
        val afterRatio = afterLoudRms / afterQuietRms

        assertTrue("Quiet section should be boosted significantly", afterQuietRms > beforeQuietRms * 3.0)
        assertTrue("Loud section should be attenuated smoothly", afterLoudRms < beforeLoudRms * 0.6)
        assertTrue(
            "Level gap ratio should tighten from >15x down below 1.6x (was $beforeRatio, now $afterRatio)",
            afterRatio in 0.65..1.6
        )
        assertTrue("No sample overflows Short range", leveled.all { abs(it.toInt()) <= 32767 })
    }

    @Test
    fun applyAutoGain_keepsDigitalSilenceSilent() {
        val silence = ShortArray(44100) { 0 }
        val result = StudioAudioProcessor.applyAutoGain(silence)
        assertEquals(silence.size, result.size)
        assertTrue(result.all { it == 0.toShort() })
    }

    @Test
    fun effectsWorkspace_showsAutoGainButton_andSavesLeveledAudio() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sr = StudioAudioProcessor.SAMPLE_RATE
        val raw = ShortArray(sr) { i ->
            (sin(2.0 * PI * 440.0 * i / sr) * 1800.0).toInt().toShort()
        }
        val session = AudioProjectSessionState(
            projectId = "auto_gain_proj",
            title = "Voice Clip",
            baseFileName = "VoiceClip",
            pcm = raw,
            sampleRate = sr,
            trimStartMs = 0L,
            trimEndMs = 1000L
        )
        val audioProcessor = WavAudioProcessor(context)
        val playerManager = AudioPlayerManager(context)
        val recorderManager = AudioRecorderManager(context)
        val repo = MediaRepository(context)
        var savedSession: AudioProjectSessionState? = null

        composeRule.setContent {
            AccessibleStudioTheme(isHighContrast = false) {
                AudioFeatureWorkspaceScreen(
                    tool = AudioFeatureTool.EFFECTS_CLEANING,
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

        composeRule.onNodeWithTag("btn_fx_auto_gain").performScrollTo().assertExists().performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodes(hasTestTag("btn_workspace_undo")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("btn_workspace_save").performClick()
        composeRule.waitForIdle()

        val committed = savedSession!!
        assertTrue(committed.hasUnsavedChanges)
        val afterRms = rmsRange(committed.pcm, 1000, sr - 1000)
        val beforeRms = rmsRange(raw, 1000, sr - 1000)
        assertTrue("Saved audio RMS should be boosted by Auto Gain", afterRms > beforeRms * 2.0)
        playerManager.release()
    }

    @Test
    fun topBarEffectMenu_hasAutoGainItem_andEmitsApplyAutoGainCommand() {
        var receivedCmd: AudioStudioCommand? = null
        composeRule.setContent {
            AccessibleStudioTheme(isHighContrast = false) {
                StudioDawTopBar(
                    currentTab = StudioTab.AUDIO,
                    isHighContrast = false,
                    onToggleHighContrast = {},
                    onOpenAbout = {},
                    onOpenSettings = {},
                    onOpenHelp = {},
                    onOpenWhatsNew = {},
                    onSelectTab = {},
                    onOpenBatchProcessing = {},
                    onAudioCommand = { receivedCmd = it }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("menu_bar_row").performScrollToNode(hasTestTag("menu_bar_effect"))
        composeRule.onNodeWithTag("menu_bar_effect").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("menu_eff_auto_gain").assertExists().performClick()
        assertEquals(AudioStudioCommand.ApplyAutoGain, receivedCmd)
    }
}
