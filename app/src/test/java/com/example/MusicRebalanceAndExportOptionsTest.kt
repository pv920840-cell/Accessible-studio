package com.example

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.example.audio.MusicRebalanceProcessor
import com.example.audio.StemSeparationMode
import com.example.ui.components.AccessibleExportDialog
import com.example.ui.components.AccessiblePreviewPlaybackSlider
import com.example.ui.components.ActiveEffectChipData
import com.example.ui.components.ActiveEffectsLongPressBar
import com.example.ui.components.MusicRebalancePanel
import com.example.ui.components.StudioFeatureGuideDialog
import com.example.ui.theme.AccessibleStudioTheme
import com.example.video.CompressorQuality
import com.example.video.VideoFormatOption
import com.example.video.VideoResolutionPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MusicRebalanceAndExportOptionsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun generateTestMusicPcm(sampleRate: Int = 22050, durationSec: Float = 0.8f): ShortArray {
        val count = (sampleRate * durationSec).toInt()
        return ShortArray(count) { idx ->
            val t = idx.toDouble() / sampleRate
            val sig = 0.35 * sin(2.0 * Math.PI * 110.0 * t) + // Bass
                0.35 * sin(2.0 * Math.PI * 660.0 * t) + // Vocal harmonic
                0.15 * sin(2.0 * Math.PI * 2400.0 * t) // Instruments
            (sig * 32767.0).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    @Test
    fun musicRebalanceProcessor_producesRebalanceAndAllStemModes() {
        val sampleRate = 22050
        val pcm = generateTestMusicPcm(sampleRate = sampleRate)

        val rebalanced = MusicRebalanceProcessor.rebalanceMix(
            samples = pcm,
            sampleRate = sampleRate,
            vocalsGain = 1.4f,
            bassGain = 0.7f,
            percussionGain = 1.1f,
            otherGain = 0.9f
        )
        assertEquals(pcm.size, rebalanced.size)
        assertTrue(rebalanced.any { it != 0.toShort() })

        StemSeparationMode.entries.forEach { mode ->
            val stems = MusicRebalanceProcessor.separateStems(pcm, sampleRate, mode)
            assertEquals("Expected ${mode.stemCount} stems for ${mode.name}", mode.stemCount, stems.size)
            assertEquals(mode.stemNames, stems.keys.toList())
            stems.values.forEach { stemPcm ->
                assertEquals(pcm.size, stemPcm.size)
            }
        }
    }

    @Test
    fun musicRebalancePanel_dropdownShowsExactFiveOptions_andTriggersSeparate() {
        var separatedMode: StemSeparationMode? = null
        composeRule.setContent {
            var mode by remember { mutableStateOf(StemSeparationMode.FOUR_STEMS) }
            var stems by remember { mutableStateOf<List<String>>(emptyList()) }
            AccessibleStudioTheme(isHighContrast = false) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    MusicRebalancePanel(
                        hasAudioLoaded = true,
                        isProcessing = false,
                        selectedStemMode = mode,
                        onSelectStemMode = { mode = it },
                        separatedStemNames = stems,
                        onPreviewRebalance = { _, _, _, _ -> },
                        onApplyRebalance = { _, _, _, _ -> },
                        onSeparateStems = {
                            separatedMode = it
                            stems = it.stemNames
                        }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("card_music_rebalance").assertIsDisplayed()
        composeRule.onNodeWithTag("slider_rebalance_vocals").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("slider_rebalance_bass").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("slider_rebalance_percussion").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("slider_rebalance_other").performScrollTo().assertIsDisplayed()

        // Open dropdown and check all 5 exact modes
        composeRule.onNodeWithTag("dropdown_stem_selection").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dropdown_stem_option_2").assertIsDisplayed()
        composeRule.onNodeWithTag("dropdown_stem_option_3").assertIsDisplayed()
        composeRule.onNodeWithTag("dropdown_stem_option_4").assertIsDisplayed()
        composeRule.onNodeWithTag("dropdown_stem_option_5").assertIsDisplayed()
        composeRule.onNodeWithTag("dropdown_stem_option_6").assertIsDisplayed()

        composeRule.onNodeWithText("2 Stems").assertIsDisplayed()
        composeRule.onNodeWithText("3 Stems (Vocals, Backing Vocals, Instruments)").assertIsDisplayed()
        composeRule.onAllNodesWithText("4 Stems (Vocal, Other, Bass, Piano)")[1].assertIsDisplayed()
        composeRule.onNodeWithText("5 Stems (Guitar and other elements)").assertIsDisplayed()
        composeRule.onNodeWithText("6 Stems (Drums and other elements)").assertIsDisplayed()

        composeRule.onNodeWithTag("dropdown_stem_option_5").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("btn_separate_stems").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(StemSeparationMode.FIVE_STEMS, separatedMode)
        composeRule.onNodeWithTag("card_separated_stem_0").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun exportDialog_audioShowsSixFormats_withoutSaveFastOrSlow() {
        var confirmedFormat = ""
        composeRule.setContent {
            AccessibleStudioTheme(isHighContrast = false) {
                AccessibleExportDialog(
                    defaultFileName = "Track01",
                    isVideo = false,
                    isExporting = false,
                    exportProgress = 0f,
                    exportedFile = null,
                    initialFormat = "WAV",
                    onDismiss = {},
                    onConfirmExport = { _, fmt -> confirmedFormat = fmt },
                    onPlayExported = {},
                    onShareExported = {}
                )
            }
        }

        listOf("wav", "mp3", "flac", "aac", "opus", "m4a").forEach { ext ->
            composeRule.onNodeWithTag("btn_format_$ext").assertIsDisplayed()
        }
        assertTrue(composeRule.onAllNodesWithText("Save fast", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Save exact, slow", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithTag("btn_format_opus").performClick()
        composeRule.onNodeWithTag("btn_confirm_export").performClick()
        assertEquals("OPUS", confirmedFormat)
    }

    @Test
    fun exportDialog_videoShowsFormatAndQualityOptions_withoutSaveFastOrSlow() {
        var chosenFmt: VideoFormatOption? = null
        var chosenRes: VideoResolutionPreset? = null
        var chosenQuality: CompressorQuality? = null

        composeRule.setContent {
            AccessibleStudioTheme(isHighContrast = false) {
                AccessibleExportDialog(
                    defaultFileName = "Video01",
                    isVideo = true,
                    isExporting = false,
                    exportProgress = 0f,
                    exportedFile = null,
                    onDismiss = {},
                    onConfirmExport = { _, _ -> },
                    onConfirmVideoExport = { _, fmt, res, q ->
                        chosenFmt = fmt
                        chosenRes = res
                        chosenQuality = q
                    },
                    onPlayExported = {},
                    onShareExported = {}
                )
            }
        }

        composeRule.onNodeWithTag("btn_video_format_mp4").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("btn_video_format_mkv").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("btn_video_format_mov").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("btn_video_quality_original").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("btn_video_quality_1080p").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("btn_video_quality_720p").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("btn_video_quality_480p").performScrollTo().assertIsDisplayed()

        assertTrue(composeRule.onAllNodesWithText("Save fast", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Save exact, slow", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithTag("btn_video_format_mkv").performScrollTo().performClick()
        composeRule.onNodeWithTag("btn_video_quality_720p").performScrollTo().performClick()
        composeRule.onNodeWithTag("btn_confirm_export").performClick()

        assertEquals(VideoFormatOption.MKV, chosenFmt)
        assertEquals(VideoResolutionPreset.RES_720P, chosenRes)
        assertEquals(CompressorQuality.MEDIUM, chosenQuality)
    }

    @Test
    fun previewPlaybackSlider_longPressEffectRemoval_andFeatureGuide_workCleanly() {
        var removedId: String? = null
        var guideDismissed = false

        composeRule.setContent {
            var posMs by remember { mutableLongStateOf(2000L) }
            var chips by remember {
                mutableStateOf(
                    listOf(
                        ActiveEffectChipData("echo", "Echo", "280ms"),
                        ActiveEffectChipData("reverb", "Reverb", "35%")
                    )
                )
            }
            var showGuide by remember { mutableStateOf(true) }

            AccessibleStudioTheme(isHighContrast = false) {
                androidx.compose.foundation.layout.Column {
                    AccessiblePreviewPlaybackSlider(
                        currentPositionMs = posMs,
                        durationMs = 10_000L,
                        onSeekTo = { posMs = it },
                        testTag = "slider_test_preview"
                    )
                    ActiveEffectsLongPressBar(
                        effects = chips,
                        onRemoveEffect = { removed ->
                            removedId = removed.id
                            chips = chips.filterNot { it.id == removed.id }
                        }
                    )
                    if (showGuide) {
                        StudioFeatureGuideDialog(
                            onDismiss = {
                                showGuide = false
                                guideDismissed = true
                            }
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        // Feature guide shows Studio tips & closes cleanly
        composeRule.onNodeWithTag("dialog_feature_guide").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_close_feature_guide").performClick()
        composeRule.waitForIdle()
        assertTrue(guideDismissed)

        // Preview playback slider is visible
        composeRule.onNodeWithTag("slider_test_preview").assertIsDisplayed()

        // Long-press active effect chip -> confirmation dialog -> Remove removes chip
        composeRule.onNodeWithTag("chip_active_effect_echo").performTouchInput {
            longClick(center)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dialog_confirm_remove_effect").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_confirm_remove_effect").performClick()
        composeRule.waitForIdle()

        assertEquals("echo", removedId)
        composeRule.onNodeWithTag("chip_active_effect_echo").assertDoesNotExist()
        composeRule.onNodeWithTag("chip_active_effect_reverb").assertIsDisplayed()
    }
}
