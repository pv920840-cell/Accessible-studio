package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.audio.AudioEffectsProEngine
import com.example.ui.components.StudioVoiceOverDialog
import com.example.ui.components.VoiceOverApplyConfig
import com.example.ui.components.VoiceOverSourceChoice
import com.example.ui.theme.AccessibleStudioTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VoiceOverFeatureTest {

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
    fun smartMusicBedAutoDuck_lowersMusicDuringSpeechAndSwellsDuringPause() {
        val sr = AudioEffectsProEngine.SAMPLE_RATE
        // 1s voice active + 1s pause
        val voice = ShortArray(sr * 2) { i ->
            if (i < sr) (sin(2.0 * PI * 220.0 * i / sr) * 9000.0).toInt().toShort() else 0
        }
        // Steady music across entire 2 seconds
        val music = ShortArray(sr * 2) { i ->
            (sin(2.0 * PI * 440.0 * i / sr) * 14000.0).toInt().toShort()
        }

        val duckedMix = AudioEffectsProEngine.applySmartMusicBedAutoDuck(
            voiceTrack = voice,
            musicPcm = music,
            duckLevel = 0.20f,
            voiceGain = 1.0f,
            musicBaseVolume = 0.60f,
            autoDuckEnabled = true,
            fadeOutTailMs = 0L,
            sampleRate = sr
        )

        // Isolate music component during speech window vs pause window
        val speechZoneMusicResidual = ShortArray(sr / 2) { k ->
            val idx = sr / 4 + k
            (duckedMix[idx] - voice[idx]).toShort()
        }
        val speechMusicRms = rmsRange(speechZoneMusicResidual, 0, speechZoneMusicResidual.size)
        val pauseMusicRms = rmsRange(duckedMix, sr + sr / 3, sr + (3 * sr) / 4)

        assertTrue(
            "Music should be clearly louder in pause ($pauseMusicRms) than while speaking ($speechMusicRms)",
            pauseMusicRms > speechMusicRms * 2.5
        )
    }

    @Test
    fun studioVoiceOverDialog_showsAddAudioButtonClipboardOptionAutoDuckAndPresets() {
        var pickedClicked = 0
        var appliedConfig: VoiceOverApplyConfig? = null

        composeRule.setContent {
            AccessibleStudioTheme(isHighContrast = false) {
                StudioVoiceOverDialog(
                    hasClipboard = true,
                    trimStartMs = 0L,
                    trimEndMs = 4000L,
                    currentPositionMs = 500L,
                    pickedAudioTitle = "lofi_music.mp3",
                    pickedAudioDurationMs = 45_000L,
                    onPickAudioFile = { pickedClicked++ },
                    onApplyVoiceOver = { appliedConfig = it },
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("btn_vo_add_audio").performScrollTo().assertExists().performClick()
        assertEquals(1, pickedClicked)

        composeRule.onNodeWithTag("vo_picked_file_badge").performScrollTo().assertExists()
        composeRule.onNodeWithTag("btn_vo_use_clipboard").performScrollTo().assertExists()

        // Verify Tutorial preset + Vlog preset selection
        composeRule.onNodeWithTag("chip_vo_preset_TUTORIAL").performScrollTo().assertExists()
        composeRule.onNodeWithTag("chip_vo_preset_VLOG").performScrollTo().assertExists().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("switch_vo_auto_duck").performScrollTo().assertExists()
        composeRule.onNodeWithTag("btn_confirm_voice_over").performClick()
        composeRule.waitForIdle()

        val cfg = appliedConfig
        assertNotNull(cfg)
        assertEquals(VoiceOverSourceChoice.ADD_AUDIO_AS_MUSIC, cfg!!.sourceChoice)
        assertEquals(AudioEffectsProEngine.VoiceOverPreset.VLOG, cfg.preset)
        assertTrue(cfg.autoDuckEnabled)
        assertFalse(cfg.preset.displayName.contains("gold", ignoreCase = true))
    }
}
