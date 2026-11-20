package com.example

import android.content.Context
import android.media.AudioFormat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioPlayerManager
import com.example.audio.AudioRecorderManager
import com.example.audio.RecordingCapabilityProbe
import com.example.audio.RecordingConfigValidator
import com.example.audio.StudioAudioProcessor
import com.example.audio.WavAudioProcessor
import com.example.data.MediaRepository
import com.example.data.RecordingAudioSource
import com.example.data.RecordingChannel
import com.example.data.RecordingFormat
import com.example.data.RecordingQuality
import com.example.data.StudioPreferences
import com.example.data.StudioSettingsState
import com.example.ui.components.AccessibilitySettingsSheet
import com.example.ui.components.AccessibleAudioWaveform
import com.example.ui.studio.AudioFeatureTool
import com.example.ui.studio.AudioFeatureWorkspaceScreen
import com.example.ui.studio.AudioProjectSessionState
import com.example.ui.theme.AccessibleStudioTheme
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
class SettingsDisplayAndRecordingTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun preferences_persistAllDisplayAndRecordingSettings_andResetOnlyRecordingDefaults() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val custom = StudioSettingsState(
            isHighContrast = true,
            isHapticsEnabled = false,
            defaultExportFormat = "M4A",
            visualModeEnabled = false,
            showZoomControls = false,
            showWaveformDisplay = false,
            recordingQuality = RecordingQuality.LOW,
            recordingFormat = RecordingFormat.M4A,
            recordingSampleRateHz = 16000,
            recordingChannel = RecordingChannel.STEREO,
            recordingBitrateKbps = 192,
            recordingAudioSource = RecordingAudioSource.VOICE_RECOGNITION,
            recordingAutoGainEnabled = false,
            recordingInputGain = 1.65f
        )

        StudioPreferences.saveSettings(context, custom)
        val loaded = StudioPreferences.loadSettings(context)

        assertTrue(loaded.isHighContrast)
        assertFalse(loaded.isHapticsEnabled)
        assertEquals("M4A", loaded.defaultExportFormat)
        assertFalse(loaded.visualModeEnabled)
        assertFalse(loaded.showZoomControls)
        assertFalse(loaded.showWaveformDisplay)
        assertEquals(RecordingQuality.LOW, loaded.recordingQuality)
        assertEquals(RecordingFormat.M4A, loaded.recordingFormat)
        assertEquals(16000, loaded.recordingSampleRateHz)
        assertEquals(RecordingChannel.STEREO, loaded.recordingChannel)
        assertEquals(192, loaded.recordingBitrateKbps)
        assertEquals(RecordingAudioSource.VOICE_RECOGNITION, loaded.recordingAudioSource)
        assertFalse(loaded.recordingAutoGainEnabled)
        assertEquals(1.65f, loaded.recordingInputGain, 0.01f)

        val afterReset = StudioPreferences.resetRecordingDefaults(context)
        // Display & General settings remain untouched
        assertTrue(afterReset.isHighContrast)
        assertFalse(afterReset.visualModeEnabled)
        assertFalse(afterReset.showZoomControls)
        assertFalse(afterReset.showWaveformDisplay)
        // Recording settings reset cleanly
        assertEquals(RecordingQuality.HIGH, afterReset.recordingQuality)
        assertEquals(RecordingFormat.AUTO, afterReset.recordingFormat)
        assertEquals(0, afterReset.recordingSampleRateHz)
        assertEquals(RecordingChannel.AUTO, afterReset.recordingChannel)
        assertEquals(0, afterReset.recordingBitrateKbps)
        assertEquals(RecordingAudioSource.DEFAULT, afterReset.recordingAudioSource)
        assertTrue(afterReset.recordingAutoGainEnabled)
        assertEquals(1.0f, afterReset.recordingInputGain, 0.01f)
    }

    @Test
    fun validator_fallsBackSafelyWhenStereoRateSourceOrFormatUnsupported() {
        val restrictedProbe = object : RecordingCapabilityProbe {
            override fun getMinBufferBytes(sampleRateHz: Int, channelMask: Int): Int {
                // Only 44100 Hz Mono works on this mock device
                return if (channelMask == AudioFormat.CHANNEL_IN_MONO && sampleRateHz == 44100) 4096 else -2
            }

            override fun isFormatSupported(format: RecordingFormat): Boolean {
                return format == RecordingFormat.AUTO || format == RecordingFormat.WAV || format == RecordingFormat.M4A
            }

            override fun isAudioSourceSupported(source: RecordingAudioSource): Boolean {
                return source != RecordingAudioSource.CAMCORDER
            }
        }

        val settings = StudioSettingsState(
            recordingQuality = RecordingQuality.HIGH,
            recordingFormat = RecordingFormat.MP3, // Unsupported on restricted device -> should fall back to M4A
            recordingSampleRateHz = 48000, // Unsupported -> falls back to 44100
            recordingChannel = RecordingChannel.STEREO, // Unsupported -> falls back to Mono
            recordingBitrateKbps = 0, // Auto for High -> 256 kbps
            recordingAudioSource = RecordingAudioSource.CAMCORDER // Unsupported -> falls back to MIC
        )

        val resolved = RecordingConfigValidator.resolve(settings, restrictedProbe)

        assertEquals(RecordingFormat.M4A, resolved.effectiveFormat)
        assertEquals(44100, resolved.sampleRateHz)
        assertEquals(1, resolved.channelCount)
        assertEquals(AudioFormat.CHANNEL_IN_MONO, resolved.channelMask)
        assertEquals(256, resolved.bitrateKbps)
        assertEquals(RecordingAudioSource.MIC, resolved.audioSource)
        assertTrue(resolved.hasFallback)
        assertTrue(resolved.fallbackNotes.size >= 4)
    }

    @Test
    fun recorderManager_appliesSettingsAndResamplesCleanlyToStudioRate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val recorder = AudioRecorderManager(context)
        val settings = StudioSettingsState(
            recordingQuality = RecordingQuality.LOW,
            recordingSampleRateHz = 16000,
            recordingAudioSource = RecordingAudioSource.VOICE_RECOGNITION,
            recordingAutoGainEnabled = false,
            recordingInputGain = 1.5f
        )

        recorder.applySettings(settings)
        assertFalse(recorder.autoGainEnabled)
        assertEquals(1.5f, recorder.micGainVolume, 0.01f)
        assertEquals(RecordingAudioSource.VOICE_RECOGNITION, recorder.resolvedConfig.value.audioSource)

        val halfSecAt16k = ShortArray(8000) { idx -> (idx % 200).toShort() }
        val studio44k = AudioRecorderManager.resampleLinear(halfSecAt16k, 16000, 44100)
        assertEquals(22050, studio44k.size)
    }

    @Test
    fun settingsSheet_updatesDisplayAndRecordingControlsImmediately_andResetsRecordingDefaults() {
        val fullProbe = object : RecordingCapabilityProbe {
            override fun getMinBufferBytes(sampleRateHz: Int, channelMask: Int): Int = 4096
            override fun isFormatSupported(format: RecordingFormat): Boolean = true
            override fun isAudioSourceSupported(source: RecordingAudioSource): Boolean = true
        }

        val immediateUpdates = mutableListOf<StudioSettingsState>()
        var savedFull: StudioSettingsState? = null

        composeRule.setContent {
            var state by remember { mutableStateOf(StudioSettingsState()) }
            AccessibleStudioTheme(isHighContrast = state.isHighContrast) {
                AccessibilitySettingsSheet(
                    isHighContrast = state.isHighContrast,
                    isHapticsEnabled = state.isHapticsEnabled,
                    defaultExportFormat = state.defaultExportFormat,
                    currentSettings = state,
                    recordingCapabilityProbe = fullProbe,
                    onImmediateSettingsUpdate = { updated ->
                        state = updated
                        immediateUpdates.add(updated)
                    },
                    onSaveFullSettings = { savedFull = it },
                    onSaveSettings = { _, _, _ -> },
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Toggle Display & Visual Controls off
        composeRule.onNodeWithTag("switch_visual_mode").performScrollTo().assertIsOn().performClick()
        composeRule.onNodeWithTag("switch_zoom_controls").performScrollTo().assertIsOn().performClick()
        composeRule.onNodeWithTag("switch_waveform_display").performScrollTo().assertIsOn().performClick()
        composeRule.waitForIdle()

        assertFalse(immediateUpdates.last().visualModeEnabled)
        assertFalse(immediateUpdates.last().showZoomControls)
        assertFalse(immediateUpdates.last().showWaveformDisplay)

        // Select Recording Quality, Format (OGG / FLAC visible when supported), Sample Rate, Channel, Bitrate, Source
        composeRule.onNodeWithTag("chip_rec_quality_medium").performScrollTo().performClick()
        composeRule.onNodeWithTag("chip_rec_format_ogg").performScrollTo().assertExists().performClick()
        composeRule.onNodeWithTag("chip_rec_format_flac").performScrollTo().assertExists()
        composeRule.onNodeWithTag("chip_rec_rate_48000").performScrollTo().performClick()
        composeRule.onNodeWithTag("chip_rec_channel_stereo").performScrollTo().performClick()
        composeRule.onNodeWithTag("chip_rec_bitrate_320").performScrollTo().performClick()
        composeRule.onNodeWithTag("chip_rec_source_camcorder").performScrollTo().performClick()
        composeRule.onNodeWithTag("switch_rec_auto_gain").performScrollTo().assertIsOn().performClick()
        composeRule.waitForIdle()

        val modified = immediateUpdates.last()
        assertEquals(RecordingQuality.MEDIUM, modified.recordingQuality)
        assertEquals(RecordingFormat.OGG, modified.recordingFormat)
        assertEquals(48000, modified.recordingSampleRateHz)
        assertEquals(RecordingChannel.STEREO, modified.recordingChannel)
        assertEquals(320, modified.recordingBitrateKbps)
        assertEquals(RecordingAudioSource.CAMCORDER, modified.recordingAudioSource)
        assertFalse(modified.recordingAutoGainEnabled)

        // Reset Recording Defaults preserves Display toggles while restoring recording defaults
        composeRule.onNodeWithTag("btn_settings_reset_recording").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("btn_save_settings").performScrollTo().performClick()
        composeRule.waitForIdle()

        val finalState = savedFull!!
        assertFalse(finalState.visualModeEnabled)
        assertFalse(finalState.showZoomControls)
        assertFalse(finalState.showWaveformDisplay)
        assertEquals(RecordingQuality.HIGH, finalState.recordingQuality)
        assertEquals(RecordingFormat.AUTO, finalState.recordingFormat)
        assertEquals(0, finalState.recordingSampleRateHz)
        assertEquals(RecordingChannel.AUTO, finalState.recordingChannel)
        assertEquals(0, finalState.recordingBitrateKbps)
        assertEquals(RecordingAudioSource.DEFAULT, finalState.recordingAudioSource)
        assertTrue(finalState.recordingAutoGainEnabled)
    }

    @Test
    fun waveformAndWorkspace_hideOrShowWaveformAndZoomControlsImmediately() {
        val visualModeState = mutableStateOf(true)
        val showZoomState = mutableStateOf(true)
        val showWaveformState = mutableStateOf(true)

        composeRule.setContent {
            AccessibleStudioTheme(isHighContrast = false) {
                AccessibleAudioWaveform(
                    currentMs = 1000L,
                    durationMs = 5000L,
                    trimStartMs = 0L,
                    trimEndMs = 5000L,
                    onSeek = {},
                    onToggleVisualMode = {},
                    visualModeEnabled = visualModeState.value,
                    showZoomControls = showZoomState.value,
                    showWaveformDisplay = showWaveformState.value
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("audio_waveform_view").assertExists()
        composeRule.onNodeWithTag("btn_zoom_in").assertExists()
        composeRule.onNodeWithTag("btn_zoom_out").assertExists()
        composeRule.onNodeWithTag("btn_zoom_full").assertExists()
        composeRule.onNodeWithTag("btn_visual_mode").assertExists()

        // Hide Zoom controls only
        showZoomState.value = false
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("audio_waveform_view").assertExists()
        composeRule.onNodeWithTag("btn_zoom_in").assertDoesNotExist()
        composeRule.onNodeWithTag("btn_zoom_out").assertDoesNotExist()
        composeRule.onNodeWithTag("btn_zoom_full").assertDoesNotExist()

        // Hide Waveform display only
        showZoomState.value = true
        showWaveformState.value = false
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("audio_waveform_view").assertDoesNotExist()
        composeRule.onNodeWithTag("audio_visual_hidden_notice").assertExists()
        composeRule.onNodeWithTag("btn_zoom_in").assertExists()

        // Disable Visual Mode completely
        visualModeState.value = false
        showWaveformState.value = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("audio_waveform_view").assertDoesNotExist()
        composeRule.onNodeWithTag("btn_visual_mode").assertDoesNotExist()
        composeRule.onNodeWithTag("audio_visual_hidden_notice").assertExists()
    }

    @Test
    fun trimWorkspace_appliesDisplayTogglesCleanly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sr = StudioAudioProcessor.SAMPLE_RATE
        val session = AudioProjectSessionState(
            projectId = "display_toggle_proj",
            title = "Display Toggle Track",
            baseFileName = "DisplayToggleTrack",
            pcm = ShortArray(sr),
            sampleRate = sr,
            trimStartMs = 0L,
            trimEndMs = 1000L
        )
        val audioProcessor = WavAudioProcessor(context)
        val playerManager = AudioPlayerManager(context)
        val recorderManager = AudioRecorderManager(context)
        val repo = MediaRepository(context)

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
                    onSaveAndReturn = {},
                    onCancelBack = {},
                    visualModeEnabled = true,
                    showZoomControls = false,
                    showWaveformDisplay = false
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("audio_waveform_view").assertDoesNotExist()
        composeRule.onNodeWithTag("btn_zoom_in").assertDoesNotExist()
        composeRule.onNodeWithTag("audio_visual_hidden_notice").assertExists()
        playerManager.release()
    }
}
