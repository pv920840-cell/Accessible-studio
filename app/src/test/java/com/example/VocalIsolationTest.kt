package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioEffectPresetsCatalog
import com.example.audio.AudioPlayerManager
import com.example.audio.AudioRecorderManager
import com.example.audio.EqPreset
import com.example.audio.SpectralDenoiseParams
import com.example.audio.StudioAudioProcessor
import com.example.audio.StudioFxSettings
import com.example.audio.VocalIsolationPreset
import com.example.audio.VocalIsolationProcessor
import com.example.audio.WavAudioProcessor
import com.example.data.MediaRepository
import com.example.ui.components.AudioStudioCommand
import com.example.ui.components.StudioDawTopBar
import com.example.ui.components.StudioEffectsRackDialog
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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VocalIsolationTest {

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
    fun fftRoundTrip_reconstructsSignalAccurately() {
        val n = 1024
        val re = FloatArray(n) { i ->
            (0.6 * sin(2.0 * PI * 5.0 * i / n) + 0.3 * cos(2.0 * PI * 19.0 * i / n)).toFloat()
        }
        val orig = re.clone()
        val im = FloatArray(n)
        VocalIsolationProcessor.fft(re, im, inverse = false)
        VocalIsolationProcessor.fft(re, im, inverse = true)

        var maxErr = 0f
        for (i in 0 until n) {
            val e = abs(re[i] - orig[i])
            if (e > maxErr) maxErr = e
        }
        assertTrue("FFT -> IFFT round-trip error should be < 1e-4 (actual $maxErr)", maxErr < 1e-4f)
    }

    @Test
    fun adaptiveSpectralSubtraction_removesHumAndAmbientNoiseWhileKeepingSpeechPristine() {
        val sr = StudioAudioProcessor.SAMPLE_RATE
        val total = 3 * sr
        val rng = Random(42)

        val noisy = ShortArray(total) { i ->
            val t = i.toDouble() / sr
            val hum60 = 2600.0 * sin(2.0 * PI * 60.0 * t) + 1100.0 * sin(2.0 * PI * 120.0 * t)
            val hiss = (rng.nextDouble() * 2.0 - 1.0) * 2200.0
            val voice = if (i in sr until (2 * sr)) {
                9500.0 * sin(2.0 * PI * 220.0 * t) +
                    5500.0 * sin(2.0 * PI * 660.0 * t) +
                    2500.0 * sin(2.0 * PI * 2400.0 * t)
            } else {
                0.0
            }
            (voice + hum60 + hiss).toInt().coerceIn(-32767, 32767).toShort()
        }

        val inputNoiseRms = rmsRange(noisy, 0, sr)
        val inputSpeechRms = rmsRange(noisy, sr, 2 * sr)

        val light = VocalIsolationProcessor.isolateSpokenAudio(noisy, VocalIsolationPreset.LIGHT, sr)
        val medium = VocalIsolationProcessor.isolateSpokenAudio(noisy, VocalIsolationPreset.MEDIUM, sr)
        val strong = VocalIsolationProcessor.isolateSpokenAudio(noisy, VocalIsolationPreset.STRONG, sr)

        assertEquals(noisy.size, light.size)
        assertEquals(noisy.size, medium.size)
        assertEquals(noisy.size, strong.size)

        val lightNoiseRms = rmsRange(light, sr / 5, (4 * sr) / 5)
        val mediumNoiseRms = rmsRange(medium, sr / 5, (4 * sr) / 5)
        val strongNoiseRms = rmsRange(strong, sr / 5, (4 * sr) / 5)
        val strongSpeechRms = rmsRange(strong, sr + sr / 5, 2 * sr - sr / 5)

        assertTrue("Light should reduce ambient noise+hum ($lightNoiseRms vs $inputNoiseRms)", lightNoiseRms < inputNoiseRms * 0.65)
        assertTrue("Medium should suppress noise more than Light ($mediumNoiseRms vs $lightNoiseRms)", mediumNoiseRms < lightNoiseRms)
        assertTrue("Strong should suppress noise more than Medium ($strongNoiseRms vs $mediumNoiseRms)", strongNoiseRms < mediumNoiseRms)
        assertTrue("Strong should suppress >80% of noise floor ($strongNoiseRms vs $inputNoiseRms)", strongNoiseRms < inputNoiseRms * 0.20)
        assertTrue("Speech clarity & level should remain strong ($strongSpeechRms vs noise $strongNoiseRms)", strongSpeechRms > strongNoiseRms * 6.0 && strongSpeechRms > inputSpeechRms * 0.55)
    }

    @Test
    fun presetCatalog_containsLightMediumStrongVocalIsolation() {
        val group = AudioEffectPresetsCatalog.groups.first { it.effectId == "noise_gate_reduction" }
        val ids = group.presets.map { it.id }
        assertTrue(ids.contains("vocal_iso_light"))
        assertTrue(ids.contains("vocal_iso_medium"))
        assertTrue(ids.contains("vocal_iso_strong"))
    }

    @Test
    fun noiseReductionRackTab_usesUnifiedAdaptivePanelWithAllRequiredControls() {
        var appliedParams: SpectralDenoiseParams? = null

        composeRule.setContent {
            var currentParams by remember { mutableStateOf(SpectralDenoiseParams()) }
            AccessibleStudioTheme(isHighContrast = false) {
                StudioEffectsRackDialog(
                    selectedTabIndex = 1,
                    onSelectTabIndex = {},
                    echoEnabled = false,
                    onEchoEnabledChange = {},
                    echoDelayMs = 280L,
                    onEchoDelayChange = {},
                    echoFeedback = 0.45f,
                    onEchoFeedbackChange = {},
                    echoMix = 0.35f,
                    onEchoMixChange = {},
                    reverbEnabled = false,
                    onReverbEnabledChange = {},
                    reverbRoomSize = 0.65f,
                    onReverbRoomSizeChange = {},
                    reverbDamping = 0.40f,
                    onReverbDampingChange = {},
                    reverbMix = 0.35f,
                    onReverbMixChange = {},
                    noiseReductionEnabled = true,
                    onNoiseReductionEnabledChange = {},
                    noiseReductionIntensity = 0.60f,
                    onNoiseReductionIntensityChange = {},
                    denoiseParams = currentParams,
                    onDenoiseParamsChange = { currentParams = it },
                    onApplyAdaptiveNoiseReduction = { appliedParams = it },
                    noiseGateEnabled = false,
                    onNoiseGateEnabledChange = {},
                    noiseGateThreshold = 0.08f,
                    onNoiseGateThresholdChange = {},
                    bassBoost = 0f,
                    onBassBoostChange = {},
                    trebleBoost = 0f,
                    onTrebleBoostChange = {},
                    selectedEq = EqPreset.NORMAL,
                    onSelectEq = {},
                    speedMultiplier = 1f,
                    onSpeedMultiplierChange = {},
                    isReversed = false,
                    onReversedChange = {},
                    studioFxSettings = StudioFxSettings(),
                    onStudioFxSettingsChange = {},
                    onAudition = {},
                    onApplyDeClick = {},
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Old duplicated NR cards must be completely removed
        assertTrue(composeRule.onAllNodes(hasTestTag("section_noise_reduction_vocal_isolation")).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodes(hasTestTag("slider_noise_reduction")).fetchSemanticsNodes().isEmpty())

        // Unified Adaptive De-Noise panel + all 5 core controls exist
        composeRule.onNodeWithTag("panel_adaptive_denoise").performScrollTo().assertExists()
        composeRule.onNodeWithTag("btn_dn_learn_profile").performScrollTo().assertExists()
        composeRule.onNodeWithTag("btn_dn_live_preview").performScrollTo().assertExists()
        composeRule.onNodeWithTag("btn_dn_bypass_ab").performScrollTo().assertExists()
        composeRule.onNodeWithTag("slider_dn_threshold").performScrollTo().assertExists()
        composeRule.onNodeWithTag("slider_dn_reduction").performScrollTo().assertExists()
        composeRule.onNodeWithTag("slider_dn_smoothing").performScrollTo().assertExists()
        composeRule.onNodeWithTag("switch_dn_voice_focus").performScrollTo().assertExists()

        composeRule.onNodeWithTag("chip_dn_preset_fan_motor").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("btn_dn_apply_offline").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(27f, appliedParams?.reductionDb ?: 0f, 0.5f)
        assertEquals(-20f, appliedParams?.thresholdDb ?: 0f, 0.5f)
        assertTrue(appliedParams?.voiceDialogueFocus == true)
    }

    @Test
    fun topBarEffectMenu_keepsIsolateSpeechInsideNoiseReductionWithoutSeparateEntries() {
        var receivedCmd: AudioStudioCommand? = null
        composeRule.setContent {
            StudioDawTopBar(
                currentTab = StudioTab.AUDIO,
                isHighContrast = false,
                onToggleHighContrast = {},
                onOpenSettings = {},
                onOpenHelp = {},
                onSelectTab = {},
                onOpenBatchProcessing = {},
                onAudioCommand = { receivedCmd = it }
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("menu_bar_row").performScrollToNode(hasTestTag("menu_bar_effect"))
        composeRule.onNodeWithTag("menu_bar_effect").performClick()
        composeRule.waitForIdle()

        assertTrue(composeRule.onAllNodes(hasTestTag("menu_eff_isolate_speech")).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodes(hasTestTag("menu_eff_isolate_light")).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodes(hasTestTag("menu_eff_isolate_medium")).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodes(hasTestTag("menu_eff_isolate_strong")).fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithTag("menu_eff_noise_cleaner").assertExists().performClick()
        composeRule.waitForIdle()

        assertEquals(AudioStudioCommand.OpenEffectsTab(1), receivedCmd)
    }

    @Test
    fun workspaceEffects_learnModeBypassAbAndOfflineCleanWorkWithoutOldWidgets() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sr = StudioAudioProcessor.SAMPLE_RATE
        val samplePcm = ShortArray(sr * 2) { i ->
            val t = i.toDouble() / sr
            val fan = 1800.0 * sin(2.0 * PI * 95.0 * t) + 1200.0 * sin(2.0 * PI * 850.0 * t)
            val speech = if (i > sr) 8000.0 * sin(2.0 * PI * 320.0 * t) else 0.0
            (speech + fan).toInt().coerceIn(-32767, 32767).toShort()
        }
        val session = AudioProjectSessionState(
            projectId = "adaptive_dn_ws",
            title = "Fan Test Clip",
            pcm = samplePcm,
            sampleRate = sr,
            trimStartMs = 0L,
            trimEndMs = 600L
        )
        val audioProcessor = WavAudioProcessor(context)
        val playerManager = AudioPlayerManager(context)
        val recorderManager = AudioRecorderManager(context)
        val repo = MediaRepository(context)
        var saved: AudioProjectSessionState? = null

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
                    onSaveAndReturn = { saved = it },
                    onCancelBack = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Old duplicated cards must not exist
        assertTrue(composeRule.onAllNodes(hasTestTag("card_workspace_vocal_isolation")).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodes(hasTestTag("slider_fx_noise")).fetchSemanticsNodes().isEmpty())

        // Unified Adaptive De-Noise panel + Learn Mode + A/B Compare + FFT resolution + Offline Render
        composeRule.onNodeWithTag("panel_adaptive_denoise").performScrollTo().assertExists()
        composeRule.onNodeWithTag("chip_dn_preset_ultra").performScrollTo().performClick()
        composeRule.onNodeWithTag("btn_dn_learn_profile").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("txt_dn_profile_status").performScrollTo().assertExists()
        composeRule.onNodeWithTag("btn_dn_clear_profile").performScrollTo().assertExists()

        // Toggle A/B Compare Bypass on and off
        composeRule.onNodeWithTag("btn_dn_bypass_ab").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("A/B: Original", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("btn_dn_bypass_ab").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("A/B: De-Noised", substring = true).assertIsDisplayed()

        // Apply offline clean
        composeRule.onNodeWithTag("btn_dn_apply_offline").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 6_000L) {
            composeRule.onAllNodes(hasTestTag("btn_workspace_undo")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("btn_workspace_save").performClick()
        composeRule.waitForIdle()

        assertTrue(saved?.hasUnsavedChanges == true)
        playerManager.release()
    }
}
