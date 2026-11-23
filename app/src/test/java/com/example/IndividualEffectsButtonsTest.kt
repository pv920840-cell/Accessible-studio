package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioPlayerManager
import com.example.audio.AudioRecorderManager
import com.example.audio.EqPreset
import com.example.audio.StudioAudioProcessor
import com.example.audio.StudioFxSettings
import com.example.audio.WavAudioProcessor
import com.example.data.MediaRepository
import com.example.data.StudioPreferences
import com.example.ui.components.StudioEffectsRackDialog
import com.example.ui.components.StudioSingleEffectKey
import com.example.ui.studio.AudioFeatureTool
import com.example.ui.studio.AudioFeatureWorkspaceScreen
import com.example.ui.studio.AudioProjectSessionState
import com.example.ui.theme.AccessibleStudioTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IndividualEffectsButtonsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun effectsStudioDialog_exposesEchoReverbAndAllProRackAsSeparateButtons() {
        var auditionCalls = 0
        var dismissCalls = 0

        composeRule.setContent {
            var selectedTab by mutableIntStateOf(0)
            var echoEnabled by mutableStateOf(false)
            var echoDelayMs by mutableLongStateOf(250L)
            var echoFeedback by mutableFloatStateOf(0.4f)
            var echoMix by mutableFloatStateOf(0.35f)
            var reverbEnabled by mutableStateOf(false)
            var reverbRoomSize by mutableFloatStateOf(0.5f)
            var reverbDamping by mutableFloatStateOf(0.5f)
            var reverbMix by mutableFloatStateOf(0.35f)
            var nrEnabled by mutableStateOf(false)
            var nrIntensity by mutableFloatStateOf(0.4f)
            var gateEnabled by mutableStateOf(false)
            var gateThresh by mutableFloatStateOf(0.05f)
            var bass by mutableFloatStateOf(0f)
            var treble by mutableFloatStateOf(0f)
            var eq by mutableStateOf(EqPreset.NORMAL)
            var speed by mutableFloatStateOf(1.0f)
            var reversed by mutableStateOf(false)
            var studioFx by mutableStateOf(StudioFxSettings())

            AccessibleStudioTheme {
                StudioEffectsRackDialog(
                    selectedTabIndex = selectedTab,
                    onSelectTabIndex = { selectedTab = it },
                    echoEnabled = echoEnabled,
                    onEchoEnabledChange = { echoEnabled = it },
                    echoDelayMs = echoDelayMs,
                    onEchoDelayChange = { echoDelayMs = it },
                    echoFeedback = echoFeedback,
                    onEchoFeedbackChange = { echoFeedback = it },
                    echoMix = echoMix,
                    onEchoMixChange = { echoMix = it },
                    reverbEnabled = reverbEnabled,
                    onReverbEnabledChange = { reverbEnabled = it },
                    reverbRoomSize = reverbRoomSize,
                    onReverbRoomSizeChange = { reverbRoomSize = it },
                    reverbDamping = reverbDamping,
                    onReverbDampingChange = { reverbDamping = it },
                    reverbMix = reverbMix,
                    onReverbMixChange = { reverbMix = it },
                    noiseReductionEnabled = nrEnabled,
                    onNoiseReductionEnabledChange = { nrEnabled = it },
                    noiseReductionIntensity = nrIntensity,
                    onNoiseReductionIntensityChange = { nrIntensity = it },
                    noiseGateEnabled = gateEnabled,
                    onNoiseGateEnabledChange = { gateEnabled = it },
                    noiseGateThreshold = gateThresh,
                    onNoiseGateThresholdChange = { gateThresh = it },
                    bassBoost = bass,
                    onBassBoostChange = { bass = it },
                    trebleBoost = treble,
                    onTrebleBoostChange = { treble = it },
                    selectedEq = eq,
                    onSelectEq = { eq = it },
                    speedMultiplier = speed,
                    onSpeedMultiplierChange = { speed = it },
                    isReversed = reversed,
                    onReversedChange = { reversed = it },
                    studioFxSettings = studioFx,
                    onStudioFxSettingsChange = { studioFx = it },
                    onAudition = { auditionCalls++ },
                    onApplyDeClick = {},
                    onDismiss = { dismissCalls++ }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("row_effects_individual_buttons").assertExists()

        // Verify all 9 individual buttons focus their corresponding effect card and keep Audition intact
        StudioSingleEffectKey.entries.forEach { key ->
            val btnTag = "btn_effects_item_${key.tag}"
            val cardTag = "card_single_fx_${key.tag}"
            composeRule.onNodeWithTag(btnTag).performScrollTo().assertIsDisplayed().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(cardTag).performScrollTo().assertIsDisplayed()
        }

        composeRule.onNodeWithTag("btn_audition_effects").assertExists().performClick()
        composeRule.onNodeWithTag("btn_done_effects_dialog").assertExists().performClick()
        composeRule.waitForIdle()
        assertEquals(1, auditionCalls)
        assertEquals(1, dismissCalls)
    }

    @Test
    fun audioEffectsWorkspace_individualProRackButtons_focusAndSaveSettingsCleanly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = AudioPlayerManager(context)
        val recorder = AudioRecorderManager(context)
        val processor = WavAudioProcessor(context)
        val mediaRepo = MediaRepository(context)
        val sinePcm = ShortArray(22050) { i ->
            (sin(2.0 * Math.PI * 440.0 * i / 44100.0) * 8000.0).toInt().toShort()
        }
        val session = AudioProjectSessionState(
            projectId = "fx_pro_rack_individual",
            title = "Voice Track",
            baseFileName = "VoiceTrack",
            pcm = sinePcm,
            sampleRate = 44100,
            trimStartMs = 0L,
            trimEndMs = 500L
        )
        var savedSession: AudioProjectSessionState? = null

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
                        onSaveAndReturn = { savedSession = it },
                        onCancelBack = {},
                        recordingSettings = StudioPreferences.loadSettings(context)
                    )
                }
            }
            composeRule.waitForIdle()

            // Verify opening Dynamics Compressor focused card, enabling it, and verifying other cards hide
            composeRule.onNodeWithTag("btn_fx_tab_compressor").performScrollTo().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("card_single_fx_compressor").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("card_fx_echo").assertDoesNotExist()
            composeRule.onNodeWithTag("switch_compressor_enabled").performScrollTo().performClick()
            composeRule.waitForIdle()

            // Switch through Chorus and Distortion single effect buttons cleanly
            composeRule.onNodeWithTag("btn_fx_tab_chorus").performScrollTo().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("card_single_fx_chorus").performScrollTo().assertIsDisplayed()

            composeRule.onNodeWithTag("btn_fx_tab_distortion").performScrollTo().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("card_single_fx_distortion").performScrollTo().assertIsDisplayed()

            // Save project state back to Hub and confirm compressor setting survived alongside export DSP compatibility
            composeRule.onNodeWithTag("btn_workspace_save").performClick()
            composeRule.waitForIdle()

            val saved = savedSession
            assertNotNull(saved)
            assertTrue(saved!!.editSettings.studioFx.compressorEnabled)

            // Confirm export DSP pipeline produces valid non-empty PCM for active individual rack settings
            val rendered = StudioAudioProcessor.applyStudioRack(
                rawPcm = saved.pcm,
                settings = saved.editSettings.studioFx.copy(chorusEnabled = true)
            )
            assertEquals(saved.pcm.size, rendered.size)
        } finally {
            player.release()
        }
    }
}
