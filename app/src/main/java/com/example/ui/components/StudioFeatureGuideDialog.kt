package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.studio.AudioFeatureTool
import com.example.ui.theme.LocalHighContrast
import com.example.video.VideoStudioFeatureButton

private data class FeatureGuideEntry(
    val title: String,
    val description: String,
    val recommendedRanges: String,
    val studioTip: String
)

private val STUDIO_FEATURE_GUIDE_ENTRIES = listOf(
    FeatureGuideEntry(
        title = "Standardized Step Slider & Video Trimmer",
        description = "Set Start and Finish around the segment to keep using Move Start/Finish slider to now playing, Play, Start, Step, Finish, and live Start time / Finish time / Clip duration readouts.",
        recommendedRanges = "Step slider: swipe UP for Seconds (1s–30s) & Minutes (1m–5m); swipe DOWN for Milliseconds (10ms–500ms).",
        studioTip = "Use 10–100 ms steps to align cuts precisely on silence gaps or speech consonants."
    ),
    FeatureGuideEntry(
        title = "Multi-Trim, Delete Part, Multi-Delete, Splitter & Auto Splitter",
        description = "Keep multiple highlight ranges (Video Multi trim), remove one or many unwanted sections (Delete part / Multi delete), drop split markers (Video Splitter), or split automatically by interval, file count, or target MB size (double-tap Split every).",
        recommendedRanges = "Minimum span: 250 ms per clip or cut segment.",
        studioTip = "Use Preview Playback Slider to locate each transition before tapping Add clip, Add part, or Add split point."
    ),
    FeatureGuideEntry(
        title = "Music Rebalance & Stem Separation",
        description = "Balance Vocals, Bass, Percussion/Drums, and Instruments/Other independently, or separate songs into 2, 3, 4, 5, or 6 stems for soloing and stem export.",
        recommendedRanges = "Vocals/Bass/Drums/Other: 0% (mute) to 200% (+6 dB). Natural vocal lift: 115%–135%.",
        studioTip = "Start with small ±15%–25% adjustments before muting a stem completely to avoid spectral artifacts."
    ),
    FeatureGuideEntry(
        title = "Studio Pro Effects & Long-Press Removal",
        description = "Apply EQ, Compressor, Echo, Reverb, Noise Reduction, Pitch/Tempo, and Video Color Filters/VFX. Long-press any active effect chip in the stack to remove it cleanly.",
        recommendedRanges = "Voice Compressor Threshold: -20 to -14 dB, Ratio 3:1–4:1 • Echo Mix: 15%–30% • Reverb Mix: 10%–25% • Pitch Shift: ±2 semitones.",
        studioTip = "Audition before applying destructive passes, and use gentle compression before reverb for clear speech."
    ),
    FeatureGuideEntry(
        title = "Audio & Video Export Formats and Quality",
        description = "Export final audio as WAV, MP3, FLAC, AAC, OPUS, or M4A, and export final video as MP4, MKV, MOV, AVI, or WEBM with Original, 1080p, 720p, or 480p quality presets.",
        recommendedRanges = "Archival audio: WAV/FLAC • Compact voice/web: OPUS/M4A/AAC • Video mastering: MP4 Original or High (1080p).",
        studioTip = "Use 720p Medium quality when sharing longer voiceover/tutorial videos to keep file size balanced."
    )
)

@Composable
fun StudioFeatureGuideDialog(
    onDismiss: () -> Unit
) {
    val isHighContrast = LocalHighContrast.current
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dialog_feature_guide")
            .border(
                if (isHighContrast) 2.dp else 1.dp,
                if (isHighContrast) Color.White else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        containerColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Feature Guide & Studio Quality Help", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                STUDIO_FEATURE_GUIDE_ENTRIES.forEachIndexed { idx, entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_guide_entry_$idx"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isHighContrast) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = entry.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = entry.description,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Recommended: ${entry.recommendedRanges}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "Studio Tip: ${entry.studioTip}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_close_feature_guide")
            ) {
                Text("Got It")
            }
        }
    )
}

enum class FeatureGuideTopic {
    // Video Timeline & Splitting
    VIDEO_TRIMMER,
    VIDEO_MULTI_TRIM,
    VIDEO_DELETE_PART,
    VIDEO_MULTI_DELETE,
    VIDEO_SPLITTER,
    VIDEO_AUTO_SPLITTER,
    VIDEO_CUTTER,
    VIDEO_SNAPSHOT_GLIMPSE,
    VIDEO_MERGER,
    VIDEO_SLIDESHOW_BLANK,

    // Video Audio & Mic
    VIDEO_NARRATION,
    VIDEO_MULTI_MUSIC,
    VIDEO_SOUND_EFFECTS,
    VIDEO_AUDIO_MANAGER,
    VIDEO_NOISE_REDUCER,
    VIDEO_TO_AUDIO,

    // Video Overlays & VFX
    VIDEO_TEXT_OVERLAY,
    VIDEO_BLUR_MOSAIC,
    VIDEO_IMAGE_OVERLAY,
    VIDEO_SUBTITLES,
    VIDEO_COLOR_FILTERS,
    VIDEO_VFX_EFFECTS,

    // Video Transform
    VIDEO_SPEED_CHANGER,
    VIDEO_RESIZE_RESOLUTION,
    VIDEO_ASPECT_RATIO,
    VIDEO_ROTATE_FLIP_REVERSE,

    // Video Tools & Conversion
    VIDEO_SILENCE_REMOVER,
    VIDEO_COMPRESSOR,
    VIDEO_FORMAT_CONVERTER,
    VIDEO_GIF_FRAME_EXTRACTOR,
    VIDEO_METADATA_TAGS,

    // Audio Studio & Effects
    AUDIO_TRIM_CUT,
    AUDIO_MIX_OVERDUB,
    AUDIO_EFFECTS_CLEANING,
    AUDIO_MUSIC_REBALANCE,
    AUDIO_VOICE_RECORDER,
    AUDIO_EQ_PRESETS,
    AUDIO_EFFECT_ECHO,
    AUDIO_EFFECT_REVERB,
    AUDIO_EFFECT_COMPRESSOR,
    AUDIO_EFFECT_PEAK_LIMITER,
    AUDIO_EFFECT_FLANGER,
    AUDIO_EFFECT_CHORUS,
    AUDIO_EFFECT_DISTORTION,
    AUDIO_EFFECT_EQ_7BAND,
    AUDIO_EFFECT_PITCH_POLARITY,
    AUDIO_EFFECT_ADAPTIVE_DENOISE,
    AUDIO_EFFECT_NOISE_GATE,
    AUDIO_EFFECT_TONE_EQ,
    AUDIO_EFFECT_SPEED_REVERSE,
    AUDIO_PRO_DSP_SUITE,
    AUDIO_VOICE_OVER,
    AUDIO_TONE_GENERATOR,
    AUDIO_INSERT_SILENCE,
    AUDIO_CUE_POINTS,
    AUDIO_STATS_INSPECTOR,
    AUDIO_BATCH_PROCESSING
}

data class SingleFeatureGuideContent(
    val title: String,
    val overview: String,
    val steps: List<String>,
    val keyControls: String,
    val studioTip: String
)

object FeatureGuideCatalog {

    fun forVideoFeature(feature: VideoStudioFeatureButton): FeatureGuideTopic = when (feature) {
        VideoStudioFeatureButton.TRIMMER -> FeatureGuideTopic.VIDEO_TRIMMER
        VideoStudioFeatureButton.MULTI_TRIM -> FeatureGuideTopic.VIDEO_MULTI_TRIM
        VideoStudioFeatureButton.DELETE_PART -> FeatureGuideTopic.VIDEO_DELETE_PART
        VideoStudioFeatureButton.MULTI_DELETE -> FeatureGuideTopic.VIDEO_MULTI_DELETE
        VideoStudioFeatureButton.SPLITTER -> FeatureGuideTopic.VIDEO_SPLITTER
        VideoStudioFeatureButton.AUTO_SPLITTER -> FeatureGuideTopic.VIDEO_AUTO_SPLITTER
        VideoStudioFeatureButton.CUTTER -> FeatureGuideTopic.VIDEO_CUTTER
        VideoStudioFeatureButton.SNAPSHOT_GLIMPSE -> FeatureGuideTopic.VIDEO_SNAPSHOT_GLIMPSE
        VideoStudioFeatureButton.MERGER -> FeatureGuideTopic.VIDEO_MERGER
        VideoStudioFeatureButton.SLIDESHOW_BLANK -> FeatureGuideTopic.VIDEO_SLIDESHOW_BLANK
        VideoStudioFeatureButton.NARRATION -> FeatureGuideTopic.VIDEO_NARRATION
        VideoStudioFeatureButton.MULTI_MUSIC -> FeatureGuideTopic.VIDEO_MULTI_MUSIC
        VideoStudioFeatureButton.SOUND_EFFECTS -> FeatureGuideTopic.VIDEO_SOUND_EFFECTS
        VideoStudioFeatureButton.AUDIO_MANAGER -> FeatureGuideTopic.VIDEO_AUDIO_MANAGER
        VideoStudioFeatureButton.NOISE_REDUCER -> FeatureGuideTopic.VIDEO_NOISE_REDUCER
        VideoStudioFeatureButton.VIDEO_TO_AUDIO -> FeatureGuideTopic.VIDEO_TO_AUDIO
        VideoStudioFeatureButton.TEXT_OVERLAY -> FeatureGuideTopic.VIDEO_TEXT_OVERLAY
        VideoStudioFeatureButton.BLUR_MOSAIC -> FeatureGuideTopic.VIDEO_BLUR_MOSAIC
        VideoStudioFeatureButton.IMAGE_OVERLAY -> FeatureGuideTopic.VIDEO_IMAGE_OVERLAY
        VideoStudioFeatureButton.SUBTITLES -> FeatureGuideTopic.VIDEO_SUBTITLES
        VideoStudioFeatureButton.COLOR_FILTERS -> FeatureGuideTopic.VIDEO_COLOR_FILTERS
        VideoStudioFeatureButton.VFX_EFFECTS -> FeatureGuideTopic.VIDEO_VFX_EFFECTS
        VideoStudioFeatureButton.SPEED_CHANGER -> FeatureGuideTopic.VIDEO_SPEED_CHANGER
        VideoStudioFeatureButton.RESIZE_RESOLUTION -> FeatureGuideTopic.VIDEO_RESIZE_RESOLUTION
        VideoStudioFeatureButton.ASPECT_RATIO -> FeatureGuideTopic.VIDEO_ASPECT_RATIO
        VideoStudioFeatureButton.ROTATE_FLIP_REVERSE -> FeatureGuideTopic.VIDEO_ROTATE_FLIP_REVERSE
        VideoStudioFeatureButton.SILENCE_REMOVER -> FeatureGuideTopic.VIDEO_SILENCE_REMOVER
        VideoStudioFeatureButton.VIDEO_COMPRESSOR -> FeatureGuideTopic.VIDEO_COMPRESSOR
        VideoStudioFeatureButton.FORMAT_CONVERTER -> FeatureGuideTopic.VIDEO_FORMAT_CONVERTER
        VideoStudioFeatureButton.GIF_FRAME_EXTRACTOR -> FeatureGuideTopic.VIDEO_GIF_FRAME_EXTRACTOR
        VideoStudioFeatureButton.METADATA_TAGS -> FeatureGuideTopic.VIDEO_METADATA_TAGS
    }

    fun forAudioTool(tool: AudioFeatureTool): FeatureGuideTopic = when (tool) {
        AudioFeatureTool.TRIM_CUT -> FeatureGuideTopic.AUDIO_TRIM_CUT
        AudioFeatureTool.MIX_TRACKS -> FeatureGuideTopic.AUDIO_MIX_OVERDUB
        AudioFeatureTool.VOICE_OVER -> FeatureGuideTopic.AUDIO_VOICE_OVER
        AudioFeatureTool.EFFECTS_CLEANING -> FeatureGuideTopic.AUDIO_EFFECTS_CLEANING
        AudioFeatureTool.MUSIC_REBALANCE -> FeatureGuideTopic.AUDIO_MUSIC_REBALANCE
        AudioFeatureTool.RECORD_VOICE -> FeatureGuideTopic.AUDIO_VOICE_RECORDER
        AudioFeatureTool.TONE_GENERATOR -> FeatureGuideTopic.AUDIO_TONE_GENERATOR
        AudioFeatureTool.INSERT_SILENCE -> FeatureGuideTopic.AUDIO_INSERT_SILENCE
        AudioFeatureTool.CUE_POINTS -> FeatureGuideTopic.AUDIO_CUE_POINTS
        AudioFeatureTool.AUDIO_STATS -> FeatureGuideTopic.AUDIO_STATS_INSPECTOR
        AudioFeatureTool.FULL_DAW_STUDIO -> FeatureGuideTopic.AUDIO_TRIM_CUT
    }

    fun forSingleEffect(effect: StudioSingleEffectKey): FeatureGuideTopic = when (effect) {
        StudioSingleEffectKey.ECHO -> FeatureGuideTopic.AUDIO_EFFECT_ECHO
        StudioSingleEffectKey.REVERB -> FeatureGuideTopic.AUDIO_EFFECT_REVERB
        StudioSingleEffectKey.COMPRESSOR -> FeatureGuideTopic.AUDIO_EFFECT_COMPRESSOR
        StudioSingleEffectKey.PEAK_LIMITER -> FeatureGuideTopic.AUDIO_EFFECT_PEAK_LIMITER
        StudioSingleEffectKey.FLANGER -> FeatureGuideTopic.AUDIO_EFFECT_FLANGER
        StudioSingleEffectKey.CHORUS -> FeatureGuideTopic.AUDIO_EFFECT_CHORUS
        StudioSingleEffectKey.DISTORTION -> FeatureGuideTopic.AUDIO_EFFECT_DISTORTION
        StudioSingleEffectKey.EQ_7_BAND -> FeatureGuideTopic.AUDIO_EFFECT_EQ_7BAND
        StudioSingleEffectKey.PITCH_POLARITY -> FeatureGuideTopic.AUDIO_EFFECT_PITCH_POLARITY
    }

    fun forTopic(topic: FeatureGuideTopic): SingleFeatureGuideContent = when (topic) {
        FeatureGuideTopic.VIDEO_TRIMMER -> SingleFeatureGuideContent(
            title = "Video Trimmer",
            overview = "Keeps only the section between Start and Finish while trimming away the beginning and end of your video.",
            steps = listOf(
                "1. Play or swipe UP/DOWN on the playback slider to move the playhead across the full video length.",
                "2. Double-tap \"Move Start slider to now playing\" to snap Start to the current playback position, or slide/nudge Start.",
                "3. Double-tap \"Move Finish slider to now playing\" to snap Finish to the current playback position.",
                "4. Double-tap any Start time or Finish time readout (or slider) to type exact Minutes, Seconds, and Milliseconds.",
                "5. Tap Play to preview Start → Finish, then save your project or export."
            ),
            keyControls = "Move Start/Finish slider to now playing, Step slider (1 ms – 1 min), Start time / Finish time dialogs, Lossless Stream-Copy Trim.",
            studioTip = "Swipe the Step slider DOWN to 10 ms or 100 ms for syllable-accurate trims, and UP to 10 s or 1 min for long videos."
        )

        FeatureGuideTopic.VIDEO_MULTI_TRIM -> SingleFeatureGuideContent(
            title = "Video Multi trim",
            overview = "Selects and collects multiple highlight clips from a single video so they are kept and joined together.",
            steps = listOf(
                "1. Scrub to the starting moment of a clip and double-tap \"Move Start slider to now playing\".",
                "2. Move playback to the clip ending and double-tap \"Move Finish slider to now playing\".",
                "3. Tap Play to verify the clip span, or adjust via the Step slider and +/- buttons.",
                "4. Tap \"Add clip\" to append that range to Clip list, then repeat for every extra clip.",
                "5. Use Delete on any row in Clip list to remove an unwanted clip."
            ),
            keyControls = "Move Start/Finish slider to now playing, Step slider (1 ms – 1 min), Add clip button, Clip list.",
            studioTip = "Double-tap Start time or Finish time to enter timestamps directly when recreating exact clip ranges."
        )

        FeatureGuideTopic.VIDEO_DELETE_PART -> SingleFeatureGuideContent(
            title = "Video Delete part",
            overview = "Removes one unwanted section between Start and Finish and stitches the surrounding video seamlessly.",
            steps = listOf(
                "1. Locate the start of the mistake or pause and double-tap \"Move Start slider to now playing\".",
                "2. Locate the end of the unwanted section and double-tap \"Move Finish slider to now playing\".",
                "3. Adjust Start or Finish with the Step slider or double-tap time readouts for exact ms precision.",
                "4. Tap Play to check the marked segment, then save or export to apply the removal."
            ),
            keyControls = "Move Start/Finish slider to now playing, Step slider (1 ms – 1 min), Start/Finish sliders & time dialogs.",
            studioTip = "Step down to 100 ms or 10 ms around words so deletions sound natural without cutting into the next phrase."
        )

        FeatureGuideTopic.VIDEO_MULTI_DELETE -> SingleFeatureGuideContent(
            title = "Video Multi delete",
            overview = "Marks several unwanted segments across your video in Part list so all marked sections are removed together.",
            steps = listOf(
                "1. Mark an unwanted section using \"Move Start slider to now playing\" and \"Move Finish slider to now playing\" (or Start/Finish sliders).",
                "2. Tap \"Add part\" to add that range to Part list.",
                "3. Repeat for any other pauses or mistakes across the timeline.",
                "4. Review Part list and delete any entry you want to keep before saving or exporting."
            ),
            keyControls = "Move Start/Finish slider to now playing, Step slider, Add part button, Part list.",
            studioTip = "Preview each part with Play before tapping Add part so you only remove what is truly unnecessary."
        )

        FeatureGuideTopic.VIDEO_SPLITTER -> SingleFeatureGuideContent(
            title = "Video Splitter",
            overview = "Places exact split Point markers along your video so you can break one recording into separate sequential files.",
            steps = listOf(
                "1. Play or scrub to where the next part should begin.",
                "2. Double-tap \"Move Point slider to now playing\" to snap the split Point to the current playback position.",
                "3. Fine-tune the Point slider using Step (or double-tap Point time to type Minutes, Seconds, Milliseconds).",
                "4. Tap \"Add split point\" to add it to Point list, then repeat for each boundary."
            ),
            keyControls = "Move Point slider to now playing, Step slider (1 ms – 1 min), Point slider, Add split point, Point list.",
            studioTip = "Tap Play next to Move Point slider to audition playback starting right from the chosen split point."
        )

        FeatureGuideTopic.VIDEO_AUTO_SPLITTER -> SingleFeatureGuideContent(
            title = "Video Auto splitter",
            overview = "Automatically divides your Start-to-Finish selection by equal time interval, exact number of split files, or target MB size.",
            steps = listOf(
                "1. Optionally narrow the range using Move Start/Finish slider to now playing and Start/Finish sliders.",
                "2. Swipe UP or DOWN on the Auto Splitter slider to choose a split interval or equal split files (such as 2 split files).",
                "3. Or tap \"Split every\" to cycle intervals, or double-tap \"Split every\" to enter custom seconds, file count, or target MB size.",
                "4. Check the live parts count preview before saving or exporting."
            ),
            keyControls = "Move Start/Finish slider to now playing, Auto Splitter slider, Split every button (tap/double-tap).",
            studioTip = "Double-tapping \"Split every\" unlocks exact File Count or Max MB size splitting for strict upload limits."
        )

        FeatureGuideTopic.VIDEO_CUTTER -> SingleFeatureGuideContent(
            title = "Non-Destructive Cutter",
            overview = "Queues one or multiple cut-out ranges on the timeline non-destructively so edits only render when you save or export.",
            steps = listOf(
                "1. Use Rewind/Forward 5s, Play/Pause, or the timeline scrubber to find the region to cut.",
                "2. Tap \"Mark Start at Playhead\" and \"Mark End at Playhead\" (or adjust Start/End and Step size).",
                "3. Tap \"Add Cut Segment\" to queue the removal while keeping your original source untouched.",
                "4. Review Queued Cuts List to jump to or remove any cut."
            ),
            keyControls = "Playback controls, Mark Start/End at Playhead, Step size buttons, Add Cut Segment, Cuts List.",
            studioTip = "Non-destructive cuts keep your timeline fluid while testing different pacing edits."
        )

        FeatureGuideTopic.VIDEO_SNAPSHOT_GLIMPSE -> SingleFeatureGuideContent(
            title = "Snapshot & Glimpse",
            overview = "Captures short 2-second highlight clips at the current playhead and lets you extract them or prepend a highlight reel.",
            steps = listOf(
                "1. Move playback to each memorable moment in your video.",
                "2. Tap \"Capture 2s Glimpse at Playhead\" to save that 2-second highlight snippet.",
                "3. Collect multiple glimpses, then tap \"Extract Only\" or \"Prepend Highlight\"."
            ),
            keyControls = "Capture 2s Glimpse at Playhead, Glimpse list remove buttons, Extract Only, Prepend Highlight.",
            studioTip = "Prepend Highlight creates a quick teaser hook at the very start of long tutorials or vlogs."
        )

        FeatureGuideTopic.VIDEO_MERGER -> SingleFeatureGuideContent(
            title = "Video Merger",
            overview = "Combines multiple separate video clips in any chosen order into a single continuous video file.",
            steps = listOf(
                "1. Tap \"Add Video Clips to Merge\" to pick two or more video files.",
                "2. Use Move Up and Move Down arrows beside each clip to arrange playback order.",
                "3. Tap \"Merge Clips Into One Video\" to combine them cleanly."
            ),
            keyControls = "Add Video Clips picker button, Reorder Up/Down arrows, Delete clip button, Merge action button.",
            studioTip = "Order your intro, main segments, and outro before merging so transitions stay chronological."
        )

        FeatureGuideTopic.VIDEO_SLIDESHOW_BLANK -> SingleFeatureGuideContent(
            title = "Slideshow & Blank Clip",
            overview = "Creates a smooth video slideshow from still photos with custom timing and optional music, or generates a blank 5-second spacer clip.",
            steps = listOf(
                "1. Tap \"Photos\" to select one or more images, and optionally tap \"+ Add Music\" for background audio.",
                "2. Set \"Seconds Per Photo\" between 1s and 10s.",
                "3. Tap \"Build Slideshow\" (or tap \"Blank Clip (5s)\" for a clean spacer clip)."
            ),
            keyControls = "Photos picker, + Add Music picker, Seconds Per Photo slider, Build Slideshow, Blank Clip (5s).",
            studioTip = "3 to 4 seconds per photo gives viewers enough time to absorb slides without rushing."
        )

        FeatureGuideTopic.VIDEO_NARRATION -> SingleFeatureGuideContent(
            title = "Narration Recorder",
            overview = "Records voiceover commentary over your video starting at the current playback position.",
            steps = listOf(
                "1. Scrub or play to where your spoken narration should begin.",
                "2. Tap \"Start Voice Narration\" and grant microphone permission if prompted.",
                "3. Speak clearly, then tap \"Stop Narration\" to save the segment into your timeline list."
            ),
            keyControls = "Start/Stop Voice Narration button, Narration segment list, Delete segment button.",
            studioTip = "Pair Narration Recorder with Multi Music Smart Auto-Ducking so background music automatically dips while you speak."
        )

        FeatureGuideTopic.VIDEO_MULTI_MUSIC -> SingleFeatureGuideContent(
            title = "Multi Music & Ducking",
            overview = "Layers background music tracks onto your video with per-track volume sliders, looping, solo preview, and automatic speech ducking.",
            steps = listOf(
                "1. Tap \"Add Background Music File\" to pick one or more audio tracks.",
                "2. Adjust each track's Volume slider (0%–150%) and toggle Loop per track or globally.",
                "3. Tap Play Solo on any track row to audition it, and keep Smart Auto-Ducking ON to lower music under voice/narration."
            ),
            keyControls = "Add Background Music File, Loop toggles, Play/Stop Solo Preview, Track Volume slider, Smart Auto-Ducking switch.",
            studioTip = "Keeping background music around 25%–45% prevents it from overpowering spoken dialogue."
        )

        FeatureGuideTopic.VIDEO_SOUND_EFFECTS -> SingleFeatureGuideContent(
            title = "Sound Effects & Freeze",
            overview = "Inserts built-in sound effects (chime, pop, whoosh, bell, drum hit) or custom SFX files at the playhead, with optional Freeze Frame.",
            steps = listOf(
                "1. Scrub playback to the exact moment you want the sound effect to trigger.",
                "2. Toggle \"Freeze Video Frame While SFX Plays\" if you want a comedic or emphasis pause.",
                "3. Tap any built-in SFX chip or \"Import Audio SFX\" to place the marker."
            ),
            keyControls = "Freeze Video Frame toggle, Built-in SFX preset chips, Import Audio SFX picker, Placed SFX list.",
            studioTip = "Freeze Frame works best on key reactions or tutorial callouts lasting 0.5–1.5 seconds."
        )

        FeatureGuideTopic.VIDEO_AUDIO_MANAGER -> SingleFeatureGuideContent(
            title = "Mute / Replace Audio",
            overview = "Controls the video's original soundtrack: keep & adjust gain, mute it completely, or replace it with another audio track.",
            steps = listOf(
                "1. Choose \"Keep Original\", \"Mute Video Audio\", or \"Replace Audio Track\".",
                "2. If replacing audio, tap \"Pick Replacement Audio Track\" to select the new audio file.",
                "3. Adjust the Volume slider between 0% and 200% (+6 dB)."
            ),
            keyControls = "Keep/Mute/Replace mode buttons, Replacement Audio picker, Track Volume slider.",
            studioTip = "Mute noisy outdoor camera audio when adding clean voiceover narration or studio music."
        )

        FeatureGuideTopic.VIDEO_NOISE_REDUCER -> SingleFeatureGuideContent(
            title = "Adaptive Noise Reducer",
            overview = "Reduces constant hiss, fan hum, and background room noise in your video's audio track while protecting natural speech.",
            steps = listOf(
                "1. Turn ON the Adaptive Noise Reducer toggle.",
                "2. Pick a preset or dial Noise Reduction Strength between 0% and 100%.",
                "3. Enable Voice / Dialogue Focus to preserve speech clarity during strong noise cleanup."
            ),
            keyControls = "Enable switch, Presets, Threshold & Reduction sliders, Voice/Dialogue Focus toggle.",
            studioTip = "Aim for 10–16 dB reduction for natural clarity; overly aggressive settings can sound metallic."
        )

        FeatureGuideTopic.VIDEO_TO_AUDIO -> SingleFeatureGuideContent(
            title = "Video to Audio",
            overview = "Extracts the audio soundtrack directly from your video and saves it as a standalone audio file.",
            steps = listOf(
                "1. Make sure Noise Reducer is toggled ON first if you also want noise cleanup during extraction.",
                "2. Tap \"Extract MP3\" for a universal compressed audio file, or \"Extract WAV\" for uncompressed quality.",
                "3. Find your saved file in the Music / Audio Studio folder and Exports tab."
            ),
            keyControls = "Extract MP3 button, Extract WAV button.",
            studioTip = "Choose WAV when you plan to do further editing or mastering in Audio Studio."
        )

        FeatureGuideTopic.VIDEO_TEXT_OVERLAY -> SingleFeatureGuideContent(
            title = "Text Overlay Studio",
            overview = "Adds styled titles, lower thirds, and animated text captions onto your video at chosen screen positions.",
            steps = listOf(
                "1. Enter your title or caption in the \"Title / Ticker Text\" input field.",
                "2. Select screen placement (Top, Center, or Bottom) using the position chips.",
                "3. Choose an animation style such as Static, Typewriter, Fade In, or Ticker Scroll."
            ),
            keyControls = "Text input field, Position chips (Top/Center/Bottom), Animation style chips.",
            studioTip = "Keep lower-third text concise so it stays clear on vertical mobile screens."
        )

        FeatureGuideTopic.VIDEO_BLUR_MOSAIC -> SingleFeatureGuideContent(
            title = "Blur & Mosaic",
            overview = "Applies a privacy blur or pixelated mosaic region anywhere on the video frame to hide faces, plates, or sensitive details.",
            steps = listOf(
                "1. Turn ON the Blur & Mosaic toggle.",
                "2. Switch between Smooth Blur and Mosaic style.",
                "3. Adjust Blur Position X, Blur Position Y, Box Size, and Blur Strength sliders."
            ),
            keyControls = "Enable switch, Blur style chips, X/Y Position sliders, Box Size, Blur Strength.",
            studioTip = "Keep Box Size slightly larger than the object you want hidden so minor camera motion doesn't uncover it."
        )

        FeatureGuideTopic.VIDEO_IMAGE_OVERLAY -> SingleFeatureGuideContent(
            title = "Image Overlay",
            overview = "Places a logo, watermark, or picture-in-picture PNG/JPEG image cleanly over your video.",
            steps = listOf(
                "1. Tap \"Pick Logo / Sticker / Overlay Image\" to select your image file.",
                "2. Adjust Opacity to set watermark transparency.",
                "3. Adjust Scale / Size slider to fit cleanly without covering main video action."
            ),
            keyControls = "Image picker button, Opacity slider, Scale/Size slider, Remove Overlay button.",
            studioTip = "A 20%–35% size logo at 65%–80% opacity keeps branding visible without distracting viewers."
        )

        FeatureGuideTopic.VIDEO_SUBTITLES -> SingleFeatureGuideContent(
            title = "Burn Subtitles",
            overview = "Imports an SRT/VTT subtitle file or pasted timed cues and burns accessible captions directly onto exported video frames.",
            steps = listOf(
                "1. Tap \"Import .SRT/.VTT\" to load a subtitle file, or tap \"Add Sample Cue\" / paste SRT text.",
                "2. Verify the loaded subtitle cue count readout.",
                "3. Tap \"Clear\" anytime to remove active subtitle cues."
            ),
            keyControls = "Import .SRT/.VTT button, Add Sample Cue button, Clear button.",
            studioTip = "Burning high-contrast captions boosts comprehension for muted feeds and deaf/hard-of-hearing viewers."
        )

        FeatureGuideTopic.VIDEO_COLOR_FILTERS -> SingleFeatureGuideContent(
            title = "Color Filters",
            overview = "Applies visual color grading looks such as Vibrant, Warm, Cool, Vintage, or Black & White to your video.",
            steps = listOf(
                "1. Browse the Color Filter chips.",
                "2. Tap any look to select it and preview the mood shift.",
                "3. Choose Normal / Original to turn off color grading."
            ),
            keyControls = "Color Filter selection chips.",
            studioTip = "Match one consistent filter across merged clips so scenes look unified."
        )

        FeatureGuideTopic.VIDEO_VFX_EFFECTS -> SingleFeatureGuideContent(
            title = "VFX Effects",
            overview = "Adds motion and lens stylization effects like Cinema Zoom, Retro Scanlines, Glow, or Vignette to your video.",
            steps = listOf(
                "1. Review the VFX Effect chips.",
                "2. Tap a VFX preset to apply it to your project.",
                "3. Tap None to return to clean unprocessed frames."
            ),
            keyControls = "VFX Effect selection chips.",
            studioTip = "Use subtle Vignette or Cinema framing for interviews while reserving stronger VFX for transitions."
        )

        FeatureGuideTopic.VIDEO_SPEED_CHANGER -> SingleFeatureGuideContent(
            title = "Speed Changer",
            overview = "Adjusts video playback tempo from slow motion (0.25x) to time-lapse fast speed (4.0x) with optional pitch preservation.",
            steps = listOf(
                "1. Drag the Speed slider or tap quick preset chips (0.5x, 1.0x, 1.5x, 2.0x).",
                "2. Keep \"Lock Original Voice Pitch\" enabled if you want speech pitch to stay natural when speeding up or slowing down."
            ),
            keyControls = "Speed Multiplier slider (0.25x–4.0x), Quick speed preset chips, Lock Original Voice Pitch switch.",
            studioTip = "1.25x with Pitch Lock ON tightens slow tutorials without making voices sound squeaky."
        )

        FeatureGuideTopic.VIDEO_RESIZE_RESOLUTION -> SingleFeatureGuideContent(
            title = "Resize & Resolution",
            overview = "Scales your video output resolution using standard HD/4K presets or exact custom pixel width and height.",
            steps = listOf(
                "1. Choose a Resolution Preset chip (480p, 720p, 1080p, or Custom).",
                "2. If using Custom Dimensions, enter Width and Height in pixels.",
                "3. Keep \"Lock Aspect Ratio\" ON to prevent stretching or distortion."
            ),
            keyControls = "Resolution preset chips, Custom Width/Height inputs, Lock Aspect Ratio switch.",
            studioTip = "Choose 720p or 1080p for the best balance of crisp detail and quick export speed."
        )

        FeatureGuideTopic.VIDEO_ASPECT_RATIO -> SingleFeatureGuideContent(
            title = "Aspect Ratio",
            overview = "Re-frames video proportions for landscape (16:9), portrait/Reels (9:16), square (1:1), or classic (4:5 / 4:3) screens.",
            steps = listOf(
                "1. Pick your target Aspect Ratio chip (Original, 16:9, 9:16, 1:1, 4:5).",
                "2. Pick Fit Mode: Fit (Letterbox bars), Fill (Crop edges), or Blur Background."
            ),
            keyControls = "Aspect Ratio chips, Fit / Crop / Blur Background mode chips.",
            studioTip = "Use \"Blur Background\" when placing horizontal 16:9 footage into vertical 9:16 social frames."
        )

        FeatureGuideTopic.VIDEO_ROTATE_FLIP_REVERSE -> SingleFeatureGuideContent(
            title = "Rotate, Flip & Reverse",
            overview = "Fixes orientation by rotating 0°/90°/180°/270°, mirroring horizontally/vertically, or reversing playback backward.",
            steps = listOf(
                "1. Tap a Rotation degree chip (0°, 90°, 180°, 270°) to orient the frame upright.",
                "2. Tap Mirror H or Flip V if selfie camera text appears backward or upside down.",
                "3. Toggle Reverse Video Playback to play the clip backward from end to start."
            ),
            keyControls = "Rotation chips, Mirror Horizontal / Flip Vertical toggles, Reverse Video switch.",
            studioTip = "Short clips reverse fastest; trim long videos before enabling Reverse Video."
        )

        FeatureGuideTopic.VIDEO_SILENCE_REMOVER -> SingleFeatureGuideContent(
            title = "Silence Remover",
            overview = "Automatically scans video audio for quiet pauses below your dB threshold and jump-cuts them out.",
            steps = listOf(
                "1. Set the Silence Threshold slider between -60 dB and -20 dB.",
                "2. Higher thresholds (closer to -20 dB) cut more pauses; lower (-50 dB) cut only dead silence.",
                "3. Tap \"Detect & Jump-Cut Silence Now\" to create the tightened output."
            ),
            keyControls = "Silence Threshold slider (-60 to -20 dB), Detect & Jump-Cut Silence Now button.",
            studioTip = "Start around -38 dB to remove dead air while preserving natural breaths between sentences."
        )

        FeatureGuideTopic.VIDEO_COMPRESSOR -> SingleFeatureGuideContent(
            title = "Video Compressor",
            overview = "Reduces video file size for faster sharing and lower storage usage using quality presets or a custom bitrate.",
            steps = listOf(
                "1. Pick a compression preset chip (Low/Small File, Balanced, High Quality, or Custom MB/Mbps).",
                "2. Adjust Target Bitrate if using Custom mode.",
                "3. Tap \"Compress & Export Video\" to render the compact output."
            ),
            keyControls = "Compressor preset chips, Custom Bitrate input, Compress & Export Video button.",
            studioTip = "Medium / 4–6 Mbps works well for crisp 1080p voice tutorials at a fraction of camera file size."
        )

        FeatureGuideTopic.VIDEO_FORMAT_CONVERTER -> SingleFeatureGuideContent(
            title = "Format Converter",
            overview = "Transmuxes or converts your video into MP4, MKV, MOV, AVI, or WEBM format for device and app compatibility.",
            steps = listOf(
                "1. Choose your desired container format chip (MP4, MKV, MOV, AVI, or WEBM).",
                "2. Tap \"Convert to Selected Format\" to save the converted file into Exports."
            ),
            keyControls = "Format chips (MP4, MKV, MOV, AVI, WEBM), Convert Format action button.",
            studioTip = "MP4 offers the widest compatibility across Android, iOS, browsers, and messaging apps."
        )

        FeatureGuideTopic.VIDEO_GIF_FRAME_EXTRACTOR -> SingleFeatureGuideContent(
            title = "GIF & Frame Extractor",
            overview = "Turns your trimmed range into an animated looping GIF or extracts still JPEG photos from single or periodic frames.",
            steps = listOf(
                "1. Choose GIF Frame Rate (FPS slider) and tap \"Export Trimmed Range to GIF\" for animated GIFs.",
                "2. Or tap \"Save Single Frame JPEG at Playhead\" to snapshot the exact current frame.",
                "3. Or pick an interval and tap \"Batch Extract Frames\" to export still frames automatically."
            ),
            keyControls = "GIF FPS slider, Export GIF button, Save Single Frame JPEG button, Batch Extract Frames.",
            studioTip = "10–12 FPS keeps animated GIFs smooth while keeping file sizes small enough for quick sharing."
        )

        FeatureGuideTopic.VIDEO_METADATA_TAGS -> SingleFeatureGuideContent(
            title = "Metadata & Tags",
            overview = "Updates embedded media tags (Title, Artist/Creator, Album, Genre) stored with your exported video or audio file.",
            steps = listOf(
                "1. Enter or edit Title, Artist, Album, and Genre fields.",
                "2. Tap \"Save Metadata Tags\" to store them in the project before exporting."
            ),
            keyControls = "Title, Artist, Album, and Genre text fields, Save Metadata Tags button.",
            studioTip = "Filling Title and Artist helps media players and file managers organize finished series clearly."
        )

        FeatureGuideTopic.AUDIO_TRIM_CUT -> SingleFeatureGuideContent(
            title = "Trim & Selection",
            overview = "Sets precise Start and Finish markers to preview, crop, mute, or delete sections of your audio track.",
            steps = listOf(
                "1. Play or swipe UP/DOWN on the Preview Playback Slider to scrub across the entire audio track.",
                "2. Double-tap \"Move Start slider to now playing\" or \"Move Finish slider to now playing\" to mark selection boundaries.",
                "3. Refine boundaries using the Step size buttons/sliders or double-tapping time displays for Minutes, Seconds, and Milliseconds.",
                "4. Tap Preview Trim, then choose Crop Selection, Mute Selection, Delete Selection, or Select All."
            ),
            keyControls = "Waveform view, Preview Playback Slider, Move Start/Finish to now playing, Crop/Mute/Delete/Select All buttons.",
            studioTip = "Use Fine Tune +/- buttons at 10 ms step size to snap cuts right before consonants without clicks."
        )

        FeatureGuideTopic.AUDIO_MIX_OVERDUB -> SingleFeatureGuideContent(
            title = "Mix & Overdub",
            overview = "Blends a second audio file (backing music, interview clip, or second vocal) over your main track with volume & sync control.",
            steps = listOf(
                "1. Tap \"Browse Audio File to Mix\" to pick a second audio file from your library or Exports.",
                "2. Balance \"Main Track Volume\" and \"Mix Track Volume\" sliders.",
                "3. Set exact entry timing using Mix Sync Offset / Fine Start Position Slider, and keep Anti-Clip Peak Limiter ON.",
                "4. Tap \"Preview Mix Sync\" to audition, then tap \"Apply Mix to Track\"."
            ),
            keyControls = "Browse Audio File, Main/Mix Volume sliders, Sync Offset controls, Peak Limiter toggle, Preview & Apply Mix.",
            studioTip = "If the overlay starts slightly early or late, shift the sync offset by ±50 ms to lock in rhythm."
        )

        FeatureGuideTopic.AUDIO_EFFECTS_CLEANING -> SingleFeatureGuideContent(
            title = "Effects & Cleaning",
            overview = "Applies instant studio cleanup (Auto Gain, Normalize, De-Hum, De-Click) alongside EQ, Echo, Reverb, Dynamics, and Pro Rack effects.",
            steps = listOf(
                "1. Tap a quick cleanup button (Auto Gain, Normalize Peak, De-Hum 60Hz, De-Click) for fast fixes.",
                "2. Use the Effect tabs/buttons to open individual DSP cards (De-Noise, EQ, Echo, Reverb, Compressor, Limiter, etc.).",
                "3. Tap \"Audition Effects Preview\" to listen safely, then tap Apply. Long-press active chips to remove an effect."
            ),
            keyControls = "Quick Effect buttons, Category switcher tabs, Individual DSP cards, Audition Preview, Active Effects long-press bar.",
            studioTip = "Clean background hum and clicks first, then apply EQ and Compression before adding Echo or Reverb."
        )

        FeatureGuideTopic.AUDIO_MUSIC_REBALANCE -> SingleFeatureGuideContent(
            title = "Music Rebalance & Stems",
            overview = "Independently boosts or attenuates Vocals, Bass, Percussion, and Instruments, or separates songs into 2 to 6 individual stem WAV files.",
            steps = listOf(
                "1. Drag Vocals, Bass, Percussion, and Instruments sliders from 0% (mute) to 200% (+6 dB), then tap Preview or Apply Mix.",
                "2. Choose a Stem Mode in the Stem Selection dropdown (2, 3, 4, 5, or 6 stems).",
                "3. Tap \"Separate\" to process stems safely in streaming chunks with live progress.",
                "4. Play individual separated stems to inspect them, or tap \"Export Stems\" to save each stem file."
            ),
            keyControls = "Vocals/Bass/Percussion/Instruments sliders, Stem Mode dropdown (2–6 stems), Preview Mix, Apply Mix, Separate, Audition & Export Stems.",
            studioTip = "Gentle +15% to +30% vocal lifts preserve instrument naturalness better than extreme solo cuts."
        )

        FeatureGuideTopic.AUDIO_VOICE_RECORDER -> SingleFeatureGuideContent(
            title = "Voice Recorder",
            overview = "Captures clean microphone voice recordings with Anti-Crackle & Auto-Gain leveling directly into your active audio project.",
            steps = listOf(
                "1. Confirm Auto-Gain & Anti-Crackle is enabled for balanced voice levels.",
                "2. Tap \"Start Recording\" (and allow Microphone permission if asked).",
                "3. Monitor the live level bar, then tap \"Stop Recording\" to capture and clean your take automatically."
            ),
            keyControls = "Auto-Gain & Anti-Crackle toggle, Active Profile readout, Start Recording / Stop Recording button.",
            studioTip = "Hold your phone slightly off-axis from your mouth (about 15 cm) to avoid harsh breath pops."
        )

        FeatureGuideTopic.AUDIO_EQ_PRESETS -> SingleFeatureGuideContent(
            title = "EQ Presets",
            overview = "Shapes tonal character quickly using curated EQ curves for Voice, Warmth, Clarity, Radio, or Music genres.",
            steps = listOf(
                "1. Tap any EQ preset chip to choose a tonal profile.",
                "2. Tap \"Apply Selected EQ\" to render the tonal curve onto your audio."
            ),
            keyControls = "EQ Preset chips, Apply Selected EQ button.",
            studioTip = "Compare presets with short speech phrases; Clarity/Vocal presets cut muddiness while boosting word intelligibility."
        )

        FeatureGuideTopic.AUDIO_EFFECT_ECHO -> SingleFeatureGuideContent(
            title = "Echo (Delay)",
            overview = "Adds timed repeating echoes for spaciousness or rhythmic slapback effects.",
            steps = listOf(
                "1. Enable Echo and pick a preset or adjust Delay Time (50–800 ms).",
                "2. Set Feedback Repeat (number of trails) and Echo Mix blend %.",
                "3. Audition before applying so echoes complement rather than blur speech."
            ),
            keyControls = "Echo toggle, Presets, Delay Time slider, Feedback slider, Mix slider.",
            studioTip = "Short 80–140 ms delays at 15% mix thicken vocals; longer 300–500 ms delays suit dramatic callouts."
        )

        FeatureGuideTopic.AUDIO_EFFECT_REVERB -> SingleFeatureGuideContent(
            title = "Reverb (Acoustic Space)",
            overview = "Simulates natural acoustic environments from small studio rooms to concert halls.",
            steps = listOf(
                "1. Turn ON Reverb and select a preset or adjust Room Size.",
                "2. Set Reverb Damping to tame bright high-frequency reflections and set Reverb Mix %.",
                "3. Audition and apply when ambience sounds natural."
            ),
            keyControls = "Reverb toggle, Presets, Room Size slider, Damping slider, Reverb Mix slider.",
            studioTip = "Keep speech reverb subtle (10%–20% mix, smaller room) so words remain sharp for screen reader & mobile listeners."
        )

        FeatureGuideTopic.AUDIO_EFFECT_COMPRESSOR -> SingleFeatureGuideContent(
            title = "Dynamics Compressor",
            overview = "Evens out volume swings by gently taming loud peaks and bringing up quieter spoken words.",
            steps = listOf(
                "1. Enable Dynamics Compressor and set Threshold (dB) where loud peaks cross.",
                "2. Set Compression Ratio (e.g., 3:1–4:1 for voice) and Makeup Gain to restore overall loudness.",
                "3. Audition or Apply Dynamics Compressor."
            ),
            keyControls = "Compressor toggle, Threshold (dB), Ratio, Makeup Gain sliders, Apply button.",
            studioTip = "Aim for gentle 3:1 compression with -18 dB threshold for warm, consistent podcast narration."
        )

        FeatureGuideTopic.AUDIO_EFFECT_PEAK_LIMITER -> SingleFeatureGuideContent(
            title = "Peak Limiter (Anti-Clip)",
            overview = "Prevents digital distortion by catching sudden loud spikes at a strict ceiling (for example -1.0 dBTP).",
            steps = listOf(
                "1. Enable Peak Limiter and set Max Ceiling between -6.0 dB and -0.1 dB.",
                "2. Audition and apply as the final safety step after all EQ and gain boosts."
            ),
            keyControls = "Peak Limiter toggle, Ceiling (dBFS) slider, Apply button.",
            studioTip = "A ceiling of -1.0 dB leaves clean headroom so lossy MP3/AAC exports won't clip."
        )

        FeatureGuideTopic.AUDIO_EFFECT_FLANGER -> SingleFeatureGuideContent(
            title = "Flanger",
            overview = "Creates sweeping jet-plane comb-filtering motion across guitars, synths, and creative voice transitions.",
            steps = listOf(
                "1. Enable Flanger and adjust Sweep Rate (Hz), Modulation Depth (ms), Feedback, and Wet Mix.",
                "2. Audition the sweep and tap Apply when satisfied."
            ),
            keyControls = "Flanger toggle, Rate, Depth, Feedback, Mix sliders.",
            studioTip = "Use 20%–35% Mix for musical shimmer without losing body."
        )

        FeatureGuideTopic.AUDIO_EFFECT_CHORUS -> SingleFeatureGuideContent(
            title = "Chorus (Multi-Voice)",
            overview = "Doubles and detunes copies slightly to make a single vocal or instrument sound wide and lush.",
            steps = listOf(
                "1. Enable Chorus and adjust Modulation Rate (Hz), Depth (ms), and Mix %.",
                "2. Audition the ensemble width before applying."
            ),
            keyControls = "Chorus toggle, Rate, Depth, Mix sliders.",
            studioTip = "Slow rates (0.5–1.2 Hz) at 25% mix give natural vocal thickening."
        )

        FeatureGuideTopic.AUDIO_EFFECT_DISTORTION -> SingleFeatureGuideContent(
            title = "Overdrive & Distortion",
            overview = "Adds warm harmonic saturation or gritty crunch with tone filtering.",
            steps = listOf(
                "1. Enable Overdrive & Distortion.",
                "2. Dial Drive % for grit and Tone Brightness for dark warmth vs sharp bite.",
                "3. Audition carefully to keep levels controlled."
            ),
            keyControls = "Distortion toggle, Drive slider, Tone slider.",
            studioTip = "Low Drive (10%–20%) adds subtle analog warmth to thin voice or bass recordings."
        )

        FeatureGuideTopic.AUDIO_EFFECT_EQ_7BAND -> SingleFeatureGuideContent(
            title = "7-Band Graphic EQ",
            overview = "Precision 7-band frequency equalizer spanning 60 Hz sub-bass up to 15 kHz air for detailed tonal surgical shaping.",
            steps = listOf(
                "1. Enable 7-Band Graphic EQ.",
                "2. Adjust individual frequency bands: cut 60–150 Hz to reduce rumble/mud, adjust 1–2.5 kHz for presence, 6–15 kHz for brightness.",
                "3. Audition and apply when balanced."
            ),
            keyControls = "7-Band EQ toggle, 60 Hz / 150 Hz / 400 Hz / 1 kHz / 2.5 kHz / 6 kHz / 15 kHz sliders.",
            studioTip = "Cutting 2–4 dB around 400 Hz often clears up boxy room resonance faster than boosting highs."
        )

        FeatureGuideTopic.AUDIO_EFFECT_PITCH_POLARITY -> SingleFeatureGuideContent(
            title = "Pitch & Polarity",
            overview = "Transposes musical/vocal pitch in ±semitones and lets you invert waveform phase polarity (180°).",
            steps = listOf(
                "1. Shift Pitch up or down in semitones (-12 to +12).",
                "2. Toggle Phase Polarity Invert when correcting out-of-phase layered recordings.",
                "3. Audition and tap Apply."
            ),
            keyControls = "Pitch Semitones slider, Invert Polarity switch, Apply button.",
            studioTip = "Keep pitch shifts within ±2 semitones for natural-sounding voices."
        )

        FeatureGuideTopic.AUDIO_EFFECT_ADAPTIVE_DENOISE -> SingleFeatureGuideContent(
            title = "Adaptive Spectral De-Noise",
            overview = "Removes fan noise, AC rumble, mic hiss, and clicks using two-step spectral Wiener filtering and optional Noise Print learning.",
            steps = listOf(
                "1. For best results, mark a short noise-only gap (0.2s–4s) and tap \"Learn Noise Print\" (or rely on Auto Adaptive mode).",
                "2. Pick a preset or tune Threshold (dB), Reduction (dB), Smoothing, and Voice/Dialogue Focus.",
                "3. Toggle \"Live Preview\" to hear changes safely, then tap \"Apply Clean Offline\"."
            ),
            keyControls = "Preset chips, Learn/Clear Noise Print, Threshold/Reduction/Smoothing sliders, Voice Focus, Live Preview & Apply Offline.",
            studioTip = "Always learn a Noise Print from quiet room tone when available for cleaner background removal with zero vocal warble."
        )

        FeatureGuideTopic.AUDIO_EFFECT_NOISE_GATE -> SingleFeatureGuideContent(
            title = "Noise Gate",
            overview = "Automatically silences low-level room noise during silent gaps between spoken phrases.",
            steps = listOf(
                "1. Turn ON the Noise Gate switch.",
                "2. Choose a preset or adjust Gate Cutoff Threshold % just above the background noise floor.",
                "3. Tap Audition to make sure quiet words are not clipped."
            ),
            keyControls = "Noise Gate toggle, Gate Preset selector, Gate Cutoff Threshold slider.",
            studioTip = "Keep cutoff low (around 4%–8%) so soft endings of words fade naturally."
        )

        FeatureGuideTopic.AUDIO_EFFECT_TONE_EQ -> SingleFeatureGuideContent(
            title = "Equalizer & Tone Shaping",
            overview = "Combines Warm Bass Boost, Crisp Treble Boost, and studio Equalizer profiles in one focused tone panel.",
            steps = listOf(
                "1. Pick an Equalizer preset or adjust Warm Bass Boost and Crisp Treble Boost sliders.",
                "2. Choose an Equalizer Profile chip to tailor overall voice/music balance.",
                "3. Tap Audition to hear the tonal contour before clicking Done."
            ),
            keyControls = "Tone Preset dropdown, Warm Bass Boost slider, Crisp Treble Boost slider, EQ Profile chips.",
            studioTip = "Pair mild Bass Boost (+15%) with +20% Treble Boost for a warm broadcast announcer curve."
        )

        FeatureGuideTopic.AUDIO_EFFECT_SPEED_REVERSE -> SingleFeatureGuideContent(
            title = "Playback Speed & Reverse",
            overview = "Adjusts audio tempo between 0.50x and 2.00x and lets you flip audio playback backward.",
            steps = listOf(
                "1. Pick a Speed preset or slide Playback Speed Multiplier between 0.5x and 2.0x.",
                "2. Toggle Reverse Audio ON for backward effects.",
                "3. Tap Audition to verify timing and pitch before applying."
            ),
            keyControls = "Speed presets, Playback Speed Multiplier slider, Reverse Audio switch.",
            studioTip = "Try 1.15x–1.25x for tightening long spoken lectures while staying completely understandable."
        )

        FeatureGuideTopic.AUDIO_PRO_DSP_SUITE -> SingleFeatureGuideContent(
            title = "Pro DSP & Mastering Suite",
            overview = "Provides 100% offline Pro effects across 4 categories: Effect (Modulation & Spaces), Channels (Stereo/Mono), Filter (High/Low/Band/De-Hum/De-Click), and Volume (Gain/Auto Gain/Normalize/Compressor/Fade).",
            steps = listOf(
                "1. Switch between Effect, Channels, Filter, and Volume tabs.",
                "2. Pick an action or configure parameters for your selected DSP tool.",
                "3. Apply the pass offline with full Undo support."
            ),
            keyControls = "Effect / Channels / Filter / Volume tabs, Preset & Action controls, Offline Apply.",
            studioTip = "Order your mastering chain: Filter Cleanup → Volume/Compression → Creative Effect."
        )

        FeatureGuideTopic.AUDIO_VOICE_OVER -> SingleFeatureGuideContent(
            title = "Voice Over & Auto-Ducking",
            overview = "Mixes background music or clipboard audio beneath your main voice with preset curves, loop control, sync offset, and automatic ducking.",
            steps = listOf(
                "1. Pick an audio file or select your Clipboard Clip as the Audio Source.",
                "2. Choose a Voice Over Preset (such as Tutorial or Podcast) and fine-tune Auto-Ducking, Voice Gain, and Music Volume.",
                "3. Adjust Sync Offset and Loop options, then tap Apply Voice Over."
            ),
            keyControls = "Audio Source picker, Presets, Auto-Duck toggle, Voice Gain & Music Volume sliders, Sync Offset.",
            studioTip = "Enabling Auto-Duck lowers background music automatically whenever speech enters."
        )

        FeatureGuideTopic.AUDIO_TONE_GENERATOR -> SingleFeatureGuideContent(
            title = "Tone & Noise Generator",
            overview = "Synthesizes clean Sine, Square, or Noise waveforms offline at your chosen frequency and duration.",
            steps = listOf(
                "1. Pick Sine, Square, or Noise as the waveform type.",
                "2. Adjust Frequency (100–2000 Hz) and Duration (1–15 seconds).",
                "3. Tap \"Synthesize Tone / Noise\" to generate the audio into your project."
            ),
            keyControls = "Waveform chips, Frequency slider & quick presets, Duration slider, Synthesize button.",
            studioTip = "Use a 1000 Hz or 440 Hz tone for test calibration, or noise tracks for ambience beds."
        )

        FeatureGuideTopic.AUDIO_INSERT_SILENCE -> SingleFeatureGuideContent(
            title = "Insert Silence",
            overview = "Inserts clean digital silence at your current playhead position without losing existing audio.",
            steps = listOf(
                "1. Seek the playhead to where you want a pause.",
                "2. Pick a duration (1–15 seconds).",
                "3. Tap \"Insert Digital Silence at Playhead\" to insert cleanly with Undo support."
            ),
            keyControls = "Playhead position readout, Duration chips & slider, Insert Digital Silence button.",
            studioTip = "Use Insert Silence before spoken intros or between podcast chapters to add breathing room."
        )

        FeatureGuideTopic.AUDIO_CUE_POINTS -> SingleFeatureGuideContent(
            title = "Cue Points & Bookmarks",
            overview = "Drops timestamped cue markers across long audio sessions so you can jump backward, forward, or seek cleanly between sections.",
            steps = listOf(
                "1. Play or scrub to an important transition or edit spot.",
                "2. Tap \"Drop Cue Here\" to save a timestamped bookmark.",
                "3. Use Prev Cue, Next Cue, or tap any listed cue to seek straight to that spot."
            ),
            keyControls = "Drop Cue Here, Prev/Next Cue navigation buttons, Cue list seek & delete actions.",
            studioTip = "Drop cues before retakes in long recordings to find edit points instantly."
        )

        FeatureGuideTopic.AUDIO_STATS_INSPECTOR -> SingleFeatureGuideContent(
            title = "Peak & RMS Stats",
            overview = "Inspects Peak Level (dBFS), RMS Power, Duration, Sample Rate, and Clipping Safety for mastering.",
            steps = listOf(
                "1. Read the live Peak, RMS, and Headroom metrics shown for your active track.",
                "2. Tap \"Peak Level & RMS Statistics\" to re-analyze and announce the full summary."
            ),
            keyControls = "Peak dBFS readout, RMS Power readout, Headroom status, Peak Level & RMS Statistics button.",
            studioTip = "If clipping is reported, use Peak Limiter or Normalize -16 LUFS inside Effects."
        )

        FeatureGuideTopic.AUDIO_BATCH_PROCESSING -> SingleFeatureGuideContent(
            title = "Batch Processing Studio",
            overview = "Applies consistent normalization, trimming, cleanup, and format export across multiple audio files in a single queue.",
            steps = listOf(
                "1. Add multiple audio files into the Batch Queue.",
                "2. Configure shared processing presets (gain leveling, fade, noise/EQ, output format).",
                "3. Tap Run Batch Process to process every file sequentially in the background with live progress."
            ),
            keyControls = "Add Files queue controls, Batch DSP options, Start/Cancel Batch Process, Per-file status.",
            studioTip = "Test your settings on a single short file first before running a large batch queue."
        )
    }
}

@Composable
fun FeatureGuideButton(
    featureTitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "btn_feature_guide"
) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag(testTag)
            .semantics {
                contentDescription = "Feature Guide for $featureTitle"
            }
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text("Feature Guide", fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SingleFeatureGuideDialog(
    topic: FeatureGuideTopic,
    onDismiss: () -> Unit
) {
    val isHighContrast = LocalHighContrast.current
    val guide = FeatureGuideCatalog.forTopic(topic)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dialog_single_feature_guide")
            .border(
                if (isHighContrast) 2.dp else 1.dp,
                if (isHighContrast) Color.White else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        containerColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${guide.title} Guide",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = guide.overview,
                    style = MaterialTheme.typography.bodyMedium
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isHighContrast) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "How to Use",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        guide.steps.forEach { step ->
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                Text(
                    text = "Key Controls: ${guide.keyControls}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "Tip: ${guide.studioTip}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("btn_close_single_feature_guide")
            ) {
                Text("Got It")
            }
        }
    )
}

