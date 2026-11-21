package com.example

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioRecorderManager
import com.example.audio.WavAudioProcessor
import com.example.data.MediaRepository
import com.example.ui.components.VideoAudioMusicPanel
import com.example.ui.studio.VideoFeatureTool
import com.example.ui.studio.VideoFeatureWorkspaceScreen
import com.example.ui.studio.VideoProjectSessionState
import com.example.ui.theme.AccessibleStudioTheme
import com.example.video.MultiMusicPreviewController
import com.example.video.MusicLayerTrack
import com.example.video.NarrationSegment
import com.example.video.VideoAudioMixer
import com.example.video.VideoAudioOption
import com.example.video.VideoMetadataDetails
import com.example.video.VideoPlayerManager
import com.example.video.VideoRenderEngine
import com.example.video.VideoToolkitProcessor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MultiMusicPlaybackAndLoopTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun computeTrackPreviewPosition_loopsThreeMinuteMusicAcrossFiveMinuteVideo() {
        val threeMinutesMs = 180_000L

        // Repeat Loop ON: 3m music continues seamlessly at 3m30s -> 0m30s inside track
        assertEquals(
            30_000L,
            MultiMusicPreviewController.computeTrackPreviewPosition(
                videoPositionMs = 210_000L,
                trackStartMs = 0L,
                trackDurationMs = threeMinutesMs,
                loop = true
            )
        )
        assertEquals(
            0L,
            MultiMusicPreviewController.computeTrackPreviewPosition(
                videoPositionMs = 360_000L,
                trackStartMs = 0L,
                trackDurationMs = threeMinutesMs,
                loop = true
            )
        )

        // Repeat Loop OFF: plays once up to 180s, then stops (returns null)
        assertEquals(
            120_000L,
            MultiMusicPreviewController.computeTrackPreviewPosition(
                videoPositionMs = 120_000L,
                trackStartMs = 0L,
                trackDurationMs = threeMinutesMs,
                loop = false
            )
        )
        assertNull(
            MultiMusicPreviewController.computeTrackPreviewPosition(
                videoPositionMs = 210_000L,
                trackStartMs = 0L,
                trackDurationMs = threeMinutesMs,
                loop = false
            )
        )

        // Before startMs -> null
        assertNull(
            MultiMusicPreviewController.computeTrackPreviewPosition(
                videoPositionMs = 2_000L,
                trackStartMs = 5_000L,
                trackDurationMs = threeMinutesMs,
                loop = true
            )
        )
    }

    @Test
    fun smartDucking_detectsVoiceAndDucksPreviewGain() {
        val narration = listOf(
            NarrationSegment(
                id = "n1",
                label = "Narration #1",
                startMs = 2_000L,
                pcmSamples = ShortArray(4410),
                durationMs = 1_500L
            )
        )

        val speechDuringNarration = MultiMusicPreviewController.isSpeechActiveAt(
            videoPositionMs = 2_600L,
            narrations = narration,
            audioOption = VideoAudioOption.ORIGINAL_AUDIO,
            baseAudioActiveAtPlayhead = false
        )
        assertTrue(speechDuringNarration)

        val duckedGain = MultiMusicPreviewController.computeEffectivePreviewGain(
            trackVolume = 0.8f,
            smartDucking = true,
            isSpeechActive = speechDuringNarration
        )
        val fullGain = MultiMusicPreviewController.computeEffectivePreviewGain(
            trackVolume = 0.8f,
            smartDucking = true,
            isSpeechActive = false
        )
        assertEquals(0.20f, duckedGain, 0.001f)
        assertEquals(0.80f, fullGain, 0.001f)

        // Base dialogue when muted does NOT trigger ducking unless narration is present
        assertFalse(
            MultiMusicPreviewController.isSpeechActiveAt(
                videoPositionMs = 500L,
                narrations = narration,
                audioOption = VideoAudioOption.MUTE,
                baseAudioActiveAtPlayhead = true
            )
        )
    }

    @Test
    fun audioMixer_repeatsShortMusicAcrossLongVideo_andRespectsLoopToggle() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mixer = VideoAudioMixer(context)
        val wavProcessor = WavAudioProcessor(context)

        // Create a real 1-second 440Hz WAV music file in cache
        val sr = 44100
        val oneSecMusic = ShortArray(sr) { i ->
            (sin(2.0 * PI * 440.0 * i / sr) * 12000.0).toInt().toShort()
        }
        val wavFile = File(context.cacheDir, "test_short_music_loop.wav")
        wavProcessor.writeWavFile(wavFile, oneSecMusic, sr, 1)
        val uri = Uri.fromFile(wavFile)

        try {
            // Mix across a 3-second video with loop = true
            val loopedMix = mixer.buildMixedMasterPcm(
                basePcm = ShortArray(0),
                targetDurationMs = 3000L,
                audioOption = VideoAudioOption.MUTE,
                replacedAudioUri = null,
                baseVolume = 1.0f,
                noiseReduceEnabled = false,
                noiseReduceIntensity = 0f,
                musicTracks = listOf(
                    MusicLayerTrack(
                        id = "m_loop",
                        title = "Short Music",
                        uri = uri,
                        startMs = 0L,
                        volume = 0.8f,
                        loop = true,
                        durationMs = 1000L
                    )
                ),
                smartDuckingEnabled = true,
                narrations = emptyList(),
                soundEffects = emptyList(),
                speed = 1.0f,
                keepPitch = true,
                reverse = false
            )

            // Peak energy in the 3rd second (2.2s - 2.9s) must be loud because loop = true
            val tailStart = (2.2 * sr).toInt()
            val tailEnd = (2.9 * sr).toInt()
            val tailPeakLooped = (tailStart until tailEnd).maxOf { abs(loopedMix[it].toInt()) }
            assertTrue("Expected looped music audio in 3rd second, peak was $tailPeakLooped", tailPeakLooped > 3000)

            // Mix across 3-second video with loop = false
            val onceMix = mixer.buildMixedMasterPcm(
                basePcm = ShortArray(0),
                targetDurationMs = 3000L,
                audioOption = VideoAudioOption.MUTE,
                replacedAudioUri = null,
                baseVolume = 1.0f,
                noiseReduceEnabled = false,
                noiseReduceIntensity = 0f,
                musicTracks = listOf(
                    MusicLayerTrack(
                        id = "m_once",
                        title = "Short Music",
                        uri = uri,
                        startMs = 0L,
                        volume = 0.8f,
                        loop = false,
                        durationMs = 1000L
                    )
                ),
                smartDuckingEnabled = true,
                narrations = emptyList(),
                soundEffects = emptyList(),
                speed = 1.0f,
                keepPitch = true,
                reverse = false
            )
            val tailPeakOnce = (tailStart until tailEnd).maxOf { abs(onceMix[it].toInt()) }
            assertEquals("Expected silence after track ends when loop=false", 0, tailPeakOnce)
        } finally {
            wavFile.delete()
        }
    }

    @Test
    fun videoAudioMusicPanel_rendersPlayMusicButton_andRepeatLoopSwitches() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = MultiMusicPreviewController(context)
        val track = MusicLayerTrack(
            id = "track_1",
            title = "Relaxing Beat.mp3",
            uri = Uri.parse("content://media/external/audio/1"),
            startMs = 0L,
            volume = 0.6f,
            loop = true,
            durationMs = 180_000L
        )

        composeRule.setContent {
            var tracks by remember { mutableStateOf(listOf(track)) }
            var smartDucking by remember { mutableStateOf(true) }
            var defaultLoop by remember { mutableStateOf(true) }
            var previewId by remember { mutableStateOf<String?>(null) }

            AccessibleStudioTheme(isHighContrast = false) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    VideoAudioMusicPanel(
                        currentPositionMs = 0L,
                        isRecordingNarration = false,
                        recordingDurationMs = 0L,
                        recordingAmplitude = 0f,
                        narrations = emptyList(),
                        onToggleRecordNarration = {},
                        onDeleteNarration = {},
                        musicTracks = tracks,
                        smartDucking = smartDucking,
                        onAddMusicTrack = {},
                        onUpdateMusicVolume = { id, v ->
                            tracks = tracks.map { if (it.id == id) it.copy(volume = v) else it }
                        },
                        onRemoveMusicTrack = { id -> tracks = tracks.filterNot { it.id == id } },
                        onToggleSmartDucking = { smartDucking = it },
                        soundEffects = emptyList(),
                        onAddSoundEffect = { _, _, _, _ -> },
                        onRemoveSoundEffect = {},
                        audioOption = VideoAudioOption.ORIGINAL_AUDIO,
                        replacedAudioUri = null,
                        audioVolume = 1.0f,
                        onSelectAudioOption = {},
                        onPickReplacementAudio = {},
                        onChangeAudioVolume = {},
                        noiseReduceEnabled = false,
                        noiseReduceStrength = 0.75f,
                        onToggleNoiseReduce = {},
                        onChangeNoiseReduceStrength = {},
                        onExtractVideoToAudio = {},
                        onToggleMusicLoop = { id, loopOn ->
                            tracks = tracks.map { if (it.id == id) it.copy(loop = loopOn) else it }
                        },
                        defaultLoopNewTracks = defaultLoop,
                        onToggleDefaultLoopNewTracks = { loopOn ->
                            defaultLoop = loopOn
                            tracks = tracks.map { it.copy(loop = loopOn) }
                        },
                        previewingMusicTrackId = previewId,
                        onTogglePreviewMusicTrack = { clicked ->
                            controller.toggleSoloPreview(clicked)
                            previewId = controller.previewingTrackId.value
                        },
                        videoDurationMs = 300_000L
                    )
                }
            }
        }

        // Smart Ducking & Repeat/Loop Music switches are both ON by default
        composeRule.onNodeWithTag("switch_smart_ducking").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("switch_multi_music_repeat_all").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("switch_music_loop_track_1").performScrollTo().assertIsOn()

        // Shows repeat hint for 3m music in 5m video
        composeRule.onNodeWithText("Repeat Loop ON (repeats to fill 05:00.0)", substring = true, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()

        // Play Music button auditions the added track & toggles to Stop Preview
        composeRule.onNodeWithTag("btn_preview_music_track_1")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Play Music")
            .performClick()
        assertEquals("track_1", controller.previewingTrackId.value)
        composeRule.onNodeWithTag("btn_preview_music_track_1").assertTextContains("Stop Preview")

        // Clicking again stops preview
        composeRule.onNodeWithTag("btn_preview_music_track_1").performClick()
        assertNull(controller.previewingTrackId.value)
        composeRule.onNodeWithTag("btn_preview_music_track_1").assertTextContains("Play Music")

        // Turning Repeat / Loop Music OFF updates track loop & card hint
        composeRule.onNodeWithTag("switch_multi_music_repeat_all").performScrollTo().performClick()
        composeRule.onNodeWithTag("switch_multi_music_repeat_all").assertIsOff()
        composeRule.onNodeWithTag("switch_music_loop_track_1").assertIsOff()
        composeRule.onNodeWithText("Play Once (no repeat)", substring = true, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()

        // Turning per-track loop back ON enables loop for that track
        composeRule.onNodeWithTag("switch_music_loop_track_1").performScrollTo().performClick()
        composeRule.onNodeWithTag("switch_music_loop_track_1").assertIsOn()

        controller.stopAll()
    }

    @Test
    fun videoFeatureWorkspace_audioMusicTool_savesMusicLoopAndSmartDuckingSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val videoPlayer = VideoPlayerManager(context)
        val renderEngine = VideoRenderEngine(context)
        val toolkit = VideoToolkitProcessor(context)
        val audioMixer = VideoAudioMixer(context)
        val recorder = AudioRecorderManager(context)
        val mediaRepo = MediaRepository(context)
        val musicController = MultiMusicPreviewController(context)

        val seededTrack = MusicLayerTrack(
            id = "m_ws_1",
            title = "Background Groove",
            uri = Uri.parse("content://media/external/audio/42"),
            startMs = 0L,
            volume = 0.55f,
            loop = true,
            durationMs = 180_000L
        )
        val session = VideoProjectSessionState(
            projectId = "vid_music_ws",
            title = "Five Min Vlog.mp4",
            durationMs = 300_000L,
            metadata = VideoMetadataDetails(durationMs = 300_000L),
            trimStartMs = 0L,
            trimEndMs = 300_000L,
            musicTracks = listOf(seededTrack),
            smartDucking = true,
            defaultLoopMusicTracks = true
        )

        var savedState: VideoProjectSessionState? = null

        composeRule.setContent {
            AccessibleStudioTheme(isHighContrast = false) {
                VideoFeatureWorkspaceScreen(
                    tool = VideoFeatureTool.AUDIO_MUSIC,
                    session = session,
                    videoPlayer = videoPlayer,
                    renderEngine = renderEngine,
                    toolkit = toolkit,
                    audioMixer = audioMixer,
                    recorder = recorder,
                    mediaRepository = mediaRepo,
                    hapticsEnabled = false,
                    onSaveAndReturn = { savedState = it },
                    onCancelBack = {},
                    onNavigateToExports = {},
                    multiMusicPreview = musicController
                )
            }
        }

        composeRule.onNodeWithTag("btn_add_multi_music").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("switch_smart_ducking").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("switch_multi_music_repeat_all").performScrollTo().assertIsOn()

        // Toggle per-track Repeat Loop OFF then save back to hub
        composeRule.onNodeWithTag("switch_music_loop_m_ws_1").performScrollTo().performClick()
        composeRule.onNodeWithTag("switch_music_loop_m_ws_1").assertIsOff()

        composeRule.onNodeWithTag("btn_workspace_save").performClick()

        val result = savedState
        assertNotNull(result)
        assertTrue(result!!.smartDucking)
        assertEquals(1, result.musicTracks.size)
        assertFalse(result.musicTracks.first().loop)

        videoPlayer.release()
        musicController.stopAll()
    }
}
