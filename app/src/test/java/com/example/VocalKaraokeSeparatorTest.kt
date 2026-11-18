package com.example

import android.content.Context
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioPlayerManager
import com.example.audio.AudioRecorderManager
import com.example.audio.StudioAudioProcessor
import com.example.audio.VocalKaraokeQuality
import com.example.audio.VocalKaraokeSeparator
import com.example.audio.WavAudioProcessor
import com.example.data.MediaRepository
import com.example.ui.components.AudioStudioCommand
import com.example.ui.components.StudioDawTopBar
import com.example.ui.studio.AudioFeatureTool
import com.example.ui.studio.AudioFeatureWorkspaceScreen
import com.example.ui.studio.AudioProjectSessionState
import com.example.ui.studio.ProjectHubScreen
import com.example.ui.studio.ProjectMediaType
import com.example.ui.studio.StudioProjectSummary
import com.example.ui.theme.AccessibleStudioTheme
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VocalKaraokeSeparatorTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun goertzelMagnitude(samples: ShortArray, targetHz: Double, sampleRate: Int, start: Int = 4096, end: Int = samples.size - 4096): Double {
        val n = (end - start).coerceAtLeast(1)
        val omega = 2.0 * PI * targetHz / sampleRate.toDouble()
        val coeff = 2.0 * cos(omega)
        var q0: Double
        var q1 = 0.0
        var q2 = 0.0
        for (i in start until end) {
            q0 = coeff * q1 - q2 + samples[i].toDouble()
            q2 = q1
            q1 = q0
        }
        val mag = sqrt((q1 * q1 + q2 * q2 - q1 * q2 * coeff).coerceAtLeast(0.0))
        return mag / n
    }

    @Test
    fun splitPcmBytesToStereo_separatesLeftAndRightChannelsAccurately() {
        val leftIn = shortArrayOf(1200, -4500, 15000, -28000)
        val rightIn = shortArrayOf(-3000, 8200, -11000, 24000)
        val bb = ByteBuffer.allocate(leftIn.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in leftIn.indices) {
            bb.putShort(leftIn[i])
            bb.putShort(rightIn[i])
        }
        val bytes = bb.array()

        val (leftOut, rightOut) = WavAudioProcessor.splitPcmBytesToStereo(bytes, bytes.size, channelCount = 2)
        assertArrayEquals(leftIn, leftOut)
        assertArrayEquals(rightIn, rightOut)
    }

    @Test
    fun separateStereo_isolatesCenterVocalsAndRemovesVocalsWhileKeepingSubBassAndSideInstruments() {
        val sr = StudioAudioProcessor.SAMPLE_RATE
        val total = sr * 2 // 2 seconds

        // Build realistic stereo mix:
        // - Center Lead Vocal: 520 Hz + 1400 Hz (same phase & amplitude in L & R)
        // - Center Sub-Bass / Kick: 90 Hz (should stay in Karaoke, be reduced in Vocals)
        // - Hard-panned Left Guitar: 2400 Hz (Left only)
        // - Hard-panned Right Synth: 3600 Hz (Right only)
        val left = ShortArray(total)
        val right = ShortArray(total)
        for (i in 0 until total) {
            val t = i.toDouble() / sr
            val vocal = 9000.0 * sin(2.0 * PI * 520.0 * t) + 5500.0 * sin(2.0 * PI * 1400.0 * t)
            val subBass = 7500.0 * sin(2.0 * PI * 90.0 * t)
            val leftInst = 6500.0 * sin(2.0 * PI * 2400.0 * t)
            val rightInst = 6500.0 * sin(2.0 * PI * 3600.0 * t)

            left[i] = (vocal + subBass + leftInst).toInt().coerceIn(-32767, 32767).toShort()
            right[i] = (vocal + subBass + rightInst).toInt().coerceIn(-32767, 32767).toShort()
        }

        assertTrue(VocalKaraokeSeparator.hasStereoContent(left, right))

        val result = VocalKaraokeSeparator.separateStereo(
            left = left,
            right = right,
            quality = VocalKaraokeQuality.BALANCED,
            sampleRate = sr
        )

        assertTrue("Expected stereo Mid/Side separation path", result.usedStereoSeparation)
        assertEquals(total, result.vocals.size)
        assertEquals(total, result.karaoke.size)

        val vocalCenter520 = goertzelMagnitude(result.vocals, 520.0, sr)
        val vocalSide2400 = goertzelMagnitude(result.vocals, 2400.0, sr)
        val vocalBass90 = goertzelMagnitude(result.vocals, 90.0, sr)

        val karaokeCenter520 = goertzelMagnitude(result.karaoke, 520.0, sr)
        val karaokeSide2400 = goertzelMagnitude(result.karaoke, 2400.0, sr)
        val karaokeBass90 = goertzelMagnitude(result.karaoke, 90.0, sr)

        assertTrue(
            "Vocals stem should isolate center 520 Hz ($vocalCenter520) well above side instrument ($vocalSide2400)",
            vocalCenter520 > vocalSide2400 * 3.0
        )
        assertTrue(
            "Vocals stem should attenuate sub-bass 90 Hz ($vocalBass90 vs vocal $vocalCenter520)",
            vocalBass90 < vocalCenter520 * 0.35
        )
        assertTrue(
            "Karaoke stem should strongly cancel center vocal ($karaokeCenter520 vs vocals $vocalCenter520)",
            karaokeCenter520 < vocalCenter520 * 0.25
        )
        assertTrue(
            "Karaoke stem should preserve stereo accompaniment ($karaokeSide2400 vs $vocalSide2400)",
            karaokeSide2400 > vocalSide2400 * 2.5
        )
        assertTrue(
            "Karaoke stem should preserve sub-bass 90 Hz ($karaokeBass90 vs $vocalBass90)",
            karaokeBass90 > vocalBass90 * 2.5
        )
    }

    @Test
    fun separateMono_hpssSuppressesLeadVocalHarmonicWhilePreservingSubBassAndQualities() {
        val sr = StudioAudioProcessor.SAMPLE_RATE
        val mono = ShortArray(sr * 2) { i ->
            val t = i.toDouble() / sr
            (8500.0 * sin(2.0 * PI * 440.0 * t) + 6000.0 * sin(2.0 * PI * 90.0 * t)).toInt().toShort()
        }

        val bal = VocalKaraokeSeparator.separateMono(mono, quality = VocalKaraokeQuality.BALANCED, sampleRate = sr)
        assertFalse(bal.usedStereoSeparation)
        val v440 = goertzelMagnitude(bal.vocals, 440.0, sr)
        val v90 = goertzelMagnitude(bal.vocals, 90.0, sr)
        val k440 = goertzelMagnitude(bal.karaoke, 440.0, sr)
        val k90 = goertzelMagnitude(bal.karaoke, 90.0, sr)

        assertTrue("Mono Vocals stem should keep 440 Hz ($v440) well above 90 Hz bass ($v90)", v440 > v90 * 3.0)
        assertTrue("Mono Karaoke stem should attenuate 440 Hz vocal ($k440 vs $v440)", k440 < v440 * 0.45)
        assertTrue("Mono Karaoke stem should preserve 90 Hz bass ($k90 vs $k440)", k90 > k440 * 1.5)

        VocalKaraokeQuality.values().forEach { q ->
            val res = VocalKaraokeSeparator.separateMono(mono, quality = q, sampleRate = sr)
            assertFalse(res.usedStereoSeparation)
            assertEquals(mono.size, res.vocals.size)
            assertEquals(mono.size, res.karaoke.size)
            assertTrue(res.vocals.any { abs(it.toInt()) > 50 })
            assertTrue(res.karaoke.any { abs(it.toInt()) > 50 })
        }
    }

    @Test
    fun tryDecodeRiffWavDirectStereo_readsStereoWavFilePreservingChannels() {
        val tmpWav = java.io.File.createTempFile("test_stereo_", ".wav")
        try {
            val sr = 44100
            val frames = 1024
            val dataBytes = frames * 4
            val bb = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
            bb.put("RIFF".toByteArray(Charsets.US_ASCII))
            bb.putInt(36 + dataBytes)
            bb.put("WAVE".toByteArray(Charsets.US_ASCII))
            bb.put("fmt ".toByteArray(Charsets.US_ASCII))
            bb.putInt(16)
            bb.putShort(1) // PCM
            bb.putShort(2) // Stereo
            bb.putInt(sr)
            bb.putInt(sr * 4)
            bb.putShort(4)
            bb.putShort(16)
            bb.put("data".toByteArray(Charsets.US_ASCII))
            bb.putInt(dataBytes)
            for (i in 0 until frames) {
                bb.putShort(8000)
                bb.putShort(-6000)
            }
            tmpWav.writeBytes(bb.array())

            val decoded = WavAudioProcessor.tryDecodeRiffWavDirectStereo(tmpWav, sr)
            assertTrue(decoded != null)
            assertEquals(2, decoded!!.channelCount)
            assertEquals(frames, decoded.left.size)
            assertEquals(8000.toShort(), decoded.left[100])
            assertEquals((-6000).toShort(), decoded.right[100])
            assertTrue(VocalKaraokeSeparator.hasStereoContent(decoded.left, decoded.right))
        } finally {
            tmpWav.delete()
        }
    }

    @Test
    fun projectHubAndWorkspace_exposeVocalKaraokeToolAndApplyExtractedStem() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val audioProj = StudioProjectSummary(
            id = "vk_proj_1",
            title = "Test Song",
            mediaType = ProjectMediaType.AUDIO,
            sourceUri = null,
            filePath = null,
            durationMillis = 1_000L,
            fileSizeBytes = 2048L,
            lastModifiedMillis = 1_700_000_000_000L
        )
        var openedTool: AudioFeatureTool? = null

        composeRule.setContent {
            AccessibleStudioTheme(isHighContrast = false) {
                ProjectHubScreen(
                    project = audioProj,
                    durationMs = 1_000L,
                    currentPositionMs = 0L,
                    isPlaying = false,
                    hasUnsavedChanges = false,
                    onTogglePreviewPlay = {},
                    onOpenAudioTool = { openedTool = it },
                    onOpenVideoTool = {},
                    onExportProject = {},
                    onBackToHome = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("hub_features_grid")
            .performScrollToNode(hasTestTag(AudioFeatureTool.VOCAL_KARAOKE.tag))
        composeRule.onNodeWithTag(AudioFeatureTool.VOCAL_KARAOKE.tag).assertExists().performClick()
        assertEquals(AudioFeatureTool.VOCAL_KARAOKE, openedTool)
    }

    @Test
    fun vocalKaraokeWorkspace_separatesAppliesAndSavesWithUndo() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sr = StudioAudioProcessor.SAMPLE_RATE
        val samplePcm = ShortArray(sr) { i ->
            val t = i.toDouble() / sr
            (8000.0 * sin(2.0 * PI * 440.0 * t) + 4500.0 * sin(2.0 * PI * 100.0 * t)).toInt().toShort()
        }
        val session = AudioProjectSessionState(
            projectId = "vk_ws_1",
            title = "Duet Song",
            pcm = samplePcm,
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
                    tool = AudioFeatureTool.VOCAL_KARAOKE,
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

        composeRule.onNodeWithTag("card_workspace_vocal_karaoke").assertExists()
        composeRule.onNodeWithTag("chip_vk_quality_deep").performScrollTo().performClick()
        composeRule.onNodeWithTag("chip_vk_mode_both").performScrollTo().performClick()
        composeRule.onNodeWithTag("btn_vk_process").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 6_000L) {
            composeRule.onAllNodes(hasTestTag("txt_vk_stems_ready")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("btn_vk_preview_vocals").assertExists()
        composeRule.onNodeWithTag("btn_vk_preview_karaoke").assertExists()
        composeRule.onNodeWithTag("btn_vk_apply_karaoke").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodes(hasTestTag("btn_workspace_undo")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("btn_workspace_save").performClick()
        composeRule.waitForIdle()

        assertTrue(savedSession?.hasUnsavedChanges == true)
        playerManager.release()
    }

    @Test
    fun dawTopBarToolsMenu_triggersVocalKaraokeDialogCommand() {
        var receivedCommand: AudioStudioCommand? = null
        composeRule.setContent {
            AccessibleStudioTheme(isHighContrast = false) {
                StudioDawTopBar(
                    currentTab = StudioTab.AUDIO,
                    isHighContrast = false,
                    onToggleHighContrast = {},
                    onOpenSettings = {},
                    onOpenHelp = {},
                    onSelectTab = {},
                    onOpenBatchProcessing = {},
                    onAudioCommand = { receivedCommand = it }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("menu_bar_row").performScrollToNode(hasTestTag("menu_bar_tools"))
        composeRule.onNodeWithTag("menu_bar_tools").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("menu_tools_vocal_karaoke").assertExists().performClick()
        composeRule.waitForIdle()

        assertEquals(AudioStudioCommand.OpenVocalKaraokeDialog, receivedCommand)
    }

    @Test
    fun separateStereo_correctsInvertedPolarityAndCombFilterLag() {
        val sr = StudioAudioProcessor.SAMPLE_RATE
        val total = sr * 2

        // 1) Inverted polarity stereo mix: Right channel is inverted (-vocal + bass phase inverted + hard-panned right)
        val leftPol = ShortArray(total)
        val rightPol = ShortArray(total)
        for (i in 0 until total) {
            val t = i.toDouble() / sr
            val vocal = 9000.0 * sin(2.0 * PI * 520.0 * t)
            val bass = 6000.0 * sin(2.0 * PI * 90.0 * t)
            val instL = 5500.0 * sin(2.0 * PI * 2400.0 * t)
            val instR = 5500.0 * sin(2.0 * PI * 3600.0 * t)
            leftPol[i] = (vocal + bass + instL).toInt().coerceIn(-32767, 32767).toShort()
            rightPol[i] = (-(vocal + bass) + instR).toInt().coerceIn(-32767, 32767).toShort()
        }

        val alignedResult = VocalKaraokeSeparator.separateStereo(
            left = leftPol,
            right = rightPol,
            quality = VocalKaraokeQuality.BALANCED,
            sampleRate = sr,
            autoAlignPhase = true,
            studioMasteringPolish = true
        )
        assertTrue("Expected phase correction on inverted stereo signal", alignedResult.phaseCorrected)
        assertTrue("Expected negative base correlation before alignment (${alignedResult.detectedCorrelation})", alignedResult.detectedCorrelation < -0.25f)

        val v520 = goertzelMagnitude(alignedResult.vocals, 520.0, sr)
        val v2400 = goertzelMagnitude(alignedResult.vocals, 2400.0, sr)
        val k520 = goertzelMagnitude(alignedResult.karaoke, 520.0, sr)
        val k90 = goertzelMagnitude(alignedResult.karaoke, 90.0, sr)
        assertTrue("After phase fix, vocals should retain 520 Hz ($v520) well above side ($v2400)", v520 > v2400 * 2.5)
        assertTrue("After phase fix, karaoke should suppress 520 Hz ($k520 vs $v520)", k520 < v520 * 0.35)
        assertTrue("After phase fix, karaoke should retain bass 90 Hz ($k90 vs $k520)", k90 > k520 * 1.8)

        // 2) Comb-filter lag stereo mix: Right delayed by 5 samples (large correlation improvement when shifted back)
        val leftLag = ShortArray(total)
        val rightLag = ShortArray(total)
        val lagShift = 5
        for (i in 0 until total) {
            val tL = i.toDouble() / sr
            val tR = (i - lagShift).toDouble() / sr
            val vocalL = 9500.0 * sin(2.0 * PI * 3800.0 * tL) + 4000.0 * sin(2.0 * PI * 750.0 * tL)
            val vocalR = 9500.0 * sin(2.0 * PI * 3800.0 * tR) + 4000.0 * sin(2.0 * PI * 750.0 * tR)
            val sideL = 2500.0 * sin(2.0 * PI * 1900.0 * tL)
            val sideR = 2500.0 * sin(2.0 * PI * 5100.0 * tR)
            leftLag[i] = (vocalL + sideL).toInt().coerceIn(-32767, 32767).toShort()
            rightLag[i] = (vocalR + sideR).toInt().coerceIn(-32767, 32767).toShort()
        }
        val lagInfo = VocalKaraokeSeparator.analyzeAndAlignStereoPhase(
            left = leftLag,
            right = rightLag,
            len = total,
            autoAlignPhase = true,
            manualInvertRight = false
        )
        assertEquals("Expected auto lag compensation of +5 samples", lagShift, lagInfo.lagSamples)
    }

    @Test
    fun separateStereo_manualInvertRightAndMasteringPolishProduceValidBoundedStems() {
        val sr = StudioAudioProcessor.SAMPLE_RATE
        val total = sr
        val left = ShortArray(total) { i ->
            val t = i.toDouble() / sr
            (8500.0 * sin(2.0 * PI * 500.0 * t) + 5000.0 * sin(2.0 * PI * 2200.0 * t)).toInt().toShort()
        }
        val right = ShortArray(total) { i ->
            val t = i.toDouble() / sr
            (-8500.0 * sin(2.0 * PI * 500.0 * t) + 5000.0 * sin(2.0 * PI * 3300.0 * t)).toInt().toShort()
        }

        val manualInverted = VocalKaraokeSeparator.separateStereo(
            left = left,
            right = right,
            quality = VocalKaraokeQuality.DEEP_ISOLATION,
            sampleRate = sr,
            autoAlignPhase = false,
            manualInvertRight = true,
            studioMasteringPolish = true
        )
        assertTrue(manualInverted.phaseCorrected)
        assertTrue(manualInverted.usedStereoSeparation)
        assertEquals(total, manualInverted.vocals.size)
        assertEquals(total, manualInverted.karaoke.size)
        assertTrue(manualInverted.vocals.all { it in Short.MIN_VALUE..Short.MAX_VALUE })
        assertTrue(manualInverted.karaoke.all { it in Short.MIN_VALUE..Short.MAX_VALUE })
        val v500 = goertzelMagnitude(manualInverted.vocals, 500.0, sr)
        val k500 = goertzelMagnitude(manualInverted.karaoke, 500.0, sr)
        assertTrue("Manual Φ flip should align vocals cleanly ($v500 vs $k500)", v500 > k500 * 2.5)
    }

    @Test
    fun vocalKaraokeWorkspace_exposesAccessiblePhaseAndMasteringToggles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sr = StudioAudioProcessor.SAMPLE_RATE
        val samplePcm = ShortArray(sr) { i ->
            val t = i.toDouble() / sr
            (8000.0 * sin(2.0 * PI * 440.0 * t) + 4500.0 * sin(2.0 * PI * 100.0 * t)).toInt().toShort()
        }
        val session = AudioProjectSessionState(
            projectId = "vk_phase_ui_1",
            title = "Studio Vocals",
            pcm = samplePcm,
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
                    tool = AudioFeatureTool.VOCAL_KARAOKE,
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
                    onCancelBack = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("switch_vk_phase_correct")
            .performScrollTo()
            .assertIsOn()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("switch_vk_phase_invert_r")
            .performScrollTo()
            .assertIsOff()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
            .assertIsOn()
        composeRule.onNodeWithTag("switch_vk_master_polish")
            .performScrollTo()
            .assertIsOn()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)

        composeRule.onNodeWithTag("btn_vk_process").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 6_000L) {
            composeRule.onAllNodes(hasTestTag("txt_vk_stems_ready")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("txt_vk_status")
            .performScrollTo()
            .assertTextContains("Studio EQ/Comp", substring = true)

        playerManager.release()
    }
}
