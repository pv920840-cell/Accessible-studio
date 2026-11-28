package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.accessibility.AccessibilityHelper
import com.example.audio.SpectralDenoiseParams
import com.example.ui.theme.LocalHighContrast
import com.example.video.MusicLayerTrack
import com.example.video.NarrationSegment
import com.example.video.SoundEffectOverlay
import com.example.video.SoundEffectPreset
import com.example.video.VideoAudioOption
import com.example.video.VideoStudioFeatureButton
import com.example.video.VideoToolSection
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoAudioMusicPanel(
    currentPositionMs: Long,
    isRecordingNarration: Boolean,
    recordingDurationMs: Long,
    recordingAmplitude: Float,
    narrations: List<NarrationSegment>,
    onToggleRecordNarration: () -> Unit,
    onDeleteNarration: (String) -> Unit,
    musicTracks: List<MusicLayerTrack>,
    smartDucking: Boolean,
    onAddMusicTrack: (Uri) -> Unit,
    onUpdateMusicVolume: (String, Float) -> Unit,
    onRemoveMusicTrack: (String) -> Unit,
    onToggleSmartDucking: (Boolean) -> Unit,
    soundEffects: List<SoundEffectOverlay>,
    onAddSoundEffect: (SoundEffectPreset, Uri?, Long, Boolean) -> Unit,
    onRemoveSoundEffect: (String) -> Unit,
    audioOption: VideoAudioOption,
    replacedAudioUri: Uri?,
    audioVolume: Float,
    onSelectAudioOption: (VideoAudioOption) -> Unit,
    onPickReplacementAudio: (Uri) -> Unit,
    onChangeAudioVolume: (Float) -> Unit,
    noiseReduceEnabled: Boolean,
    noiseReduceStrength: Float,
    onToggleNoiseReduce: (Boolean) -> Unit,
    onChangeNoiseReduceStrength: (Float) -> Unit,
    noiseDenoiseParams: SpectralDenoiseParams = SpectralDenoiseParams.fromLegacyIntensity(noiseReduceStrength),
    onChangeNoiseDenoiseParams: (SpectralDenoiseParams) -> Unit = { p ->
        onChangeNoiseReduceStrength((p.reductionDb / 30f).coerceIn(0.1f, 1f))
    },
    onExtractVideoToAudio: (format: String) -> Unit,
    onToggleMusicLoop: (String, Boolean) -> Unit = { _, _ -> },
    defaultLoopNewTracks: Boolean = true,
    onToggleDefaultLoopNewTracks: (Boolean) -> Unit = {},
    previewingMusicTrackId: String? = null,
    onTogglePreviewMusicTrack: (MusicLayerTrack) -> Unit = {},
    videoDurationMs: Long = 0L,
    isPlaying: Boolean = false,
    positionFlow: StateFlow<Long>? = null,
    initialFocusedFeature: VideoStudioFeatureButton? = null,
    showSectionFeatureButtons: Boolean = true
) {
    val context = LocalContext.current
    val isHighContrast = LocalHighContrast.current
    var focusedFeature by remember(initialFocusedFeature) { mutableStateOf(initialFocusedFeature) }
    var guideTopic by remember { mutableStateOf<FeatureGuideTopic?>(null) }

    var selectedPreset by remember { mutableStateOf(SoundEffectPreset.CHIME) }
    var sfxDurationMs by remember { mutableLongStateOf(1500L) }
    var sfxFreezeFrame by remember { mutableStateOf(false) }
    var extractAudioFormat by remember { mutableStateOf("MP3") }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onToggleRecordNarration()
    }

    val musicPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) onAddMusicTrack(uri)
    }

    val replaceAudioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) onPickReplacementAudio(uri)
    }

    val customSfxPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onAddSoundEffect(SoundEffectPreset.CUSTOM_FILE, uri, sfxDurationMs, sfxFreezeFrame)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

        if (showSectionFeatureButtons) {
            VideoFeatureButtonStrip(
                features = VideoStudioFeatureButton.forSection(VideoToolSection.AUDIO_MUSIC),
                selectedFeature = focusedFeature,
                onSelectFeature = { focusedFeature = it },
                isHighContrast = isHighContrast
            )
        }

        // 1. Narration Recorder
        if (focusedFeature == null || focusedFeature == VideoStudioFeatureButton.NARRATION) {
        SectionToolCard(
            title = "Narration Recorder",
            subtitle = "Record voiceover segments directly over your video timeline",
            isHighContrast = isHighContrast,
            onFeatureGuide = { guideTopic = FeatureGuideTopic.VIDEO_NARRATION },
            featureGuideTag = "btn_feature_guide_narration"
        ) {
            Button(
                onClick = {
                    val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    if (hasPerm || isRecordingNarration) {
                        onToggleRecordNarration()
                    } else {
                        micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecordingNarration) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_video_record_narration")
            ) {
                Icon(if (isRecordingNarration) Icons.Default.Stop else Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                if (isRecordingNarration) {
                    Text(
                        "Stop Narration (${AccessibilityHelper.formatTimeVisual(recordingDurationMs)})",
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    PlayheadActionButtonText(
                        prefix = "Record Narration at",
                        playingFallback = "Record Narration at Playhead",
                        currentPositionMs = currentPositionMs,
                        isPlaying = isPlaying,
                        positionFlow = positionFlow
                    )
                }
            }

            if (isRecordingNarration) {
                LinearProgressIndicator(
                    progress = { recordingAmplitude.coerceIn(0.05f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                )
            }

            narrations.forEach { seg ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${seg.label} • starts ${AccessibilityHelper.formatTimeVisual(seg.startMs)} (${seg.durationMs / 1000}s)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onDeleteNarration(seg.id) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete ${seg.label}")
                    }
                }
            }
        }
        }

        // 2. Add Multi Music with Smart Ducking & Repeat Loop
        if (focusedFeature == null || focusedFeature == VideoStudioFeatureButton.MULTI_MUSIC) {
        SectionToolCard(
            title = "Add Multi Music",
            subtitle = "Layer multiple background music tracks with Smart Ducking & Repeat Loop",
            isHighContrast = isHighContrast,
            onFeatureGuide = { guideTopic = FeatureGuideTopic.VIDEO_MULTI_MUSIC },
            featureGuideTag = "btn_feature_guide_multi_music"
        ) {
            OutlinedButton(
                onClick = { musicPicker.launch("audio/*") },
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_add_multi_music")
            ) {
                Icon(Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("+ Add Background Music Track (${musicTracks.size})", fontWeight = FontWeight.Bold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Smart Ducking", fontWeight = FontWeight.Bold)
                    Text("Auto-lowers music volume when voice or narration plays", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = smartDucking,
                    onCheckedChange = onToggleSmartDucking,
                    modifier = Modifier.testTag("switch_smart_ducking").semantics {
                        contentDescription = "Smart Ducking"
                        stateDescription = if (smartDucking) "On" else "Off"
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Repeat / Loop Music", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Continuously repeats shorter music so it plays across the full video length",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = defaultLoopNewTracks,
                    onCheckedChange = onToggleDefaultLoopNewTracks,
                    modifier = Modifier.testTag("switch_multi_music_repeat_all").semantics {
                        contentDescription = "Repeat and loop music for full video"
                        stateDescription = if (defaultLoopNewTracks) "On" else "Off"
                    }
                )
            }

            musicTracks.forEach { track ->
                val isPreviewing = previewingMusicTrackId == track.id
                val trackDurLabel = if (track.durationMs > 0L) {
                    " • ${AccessibilityHelper.formatTimeVisual(track.durationMs)}"
                } else {
                    ""
                }
                val loopHint = if (track.loop) {
                    if (videoDurationMs > 0L && track.durationMs > 0L && videoDurationMs > track.durationMs) {
                        "Repeat Loop ON (repeats to fill ${AccessibilityHelper.formatTimeVisual(videoDurationMs)})"
                    } else {
                        "Repeat Loop ON"
                    }
                } else {
                    "Play Once (no repeat)"
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_music_track_${track.id}"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isHighContrast) Color(0xFF141414) else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${track.title}$trackDurLabel",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = loopHint,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (track.loop) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { onRemoveMusicTrack(track.id) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("btn_remove_music_${track.id}")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove ${track.title}")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { onTogglePreviewMusicTrack(track) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("btn_preview_music_${track.id}")
                                    .semantics {
                                        contentDescription = if (isPreviewing) "Stop preview of ${track.title}" else "Play preview of ${track.title}"
                                    }
                            ) {
                                Icon(
                                    imageVector = if (isPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (isPreviewing) "Stop Preview" else "Play Music", fontWeight = FontWeight.Bold)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Repeat Loop", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                Switch(
                                    checked = track.loop,
                                    onCheckedChange = { onToggleMusicLoop(track.id, it) },
                                    modifier = Modifier
                                        .testTag("switch_music_loop_${track.id}")
                                        .semantics {
                                            contentDescription = "Repeat loop for ${track.title}"
                                            stateDescription = if (track.loop) "On" else "Off"
                                        }
                                )
                            }
                        }

                        AccessibleEffectSlider(
                            title = "Track Volume",
                            value = track.volume,
                            onValueChange = { onUpdateMusicVolume(track.id, it) },
                            valueRange = 0f..1.5f,
                            step = 0.05f,
                            displayValue = "${(track.volume * 100).toInt()}%",
                            spokenDescription = "${track.title} volume ${(track.volume * 100).toInt()} percent",
                            testTag = "slider_music_${track.id}"
                        )
                    }
                }
            }
        }
        }

        // 3. Sound Effects & Freeze Frame
        if (focusedFeature == null || focusedFeature == VideoStudioFeatureButton.SOUND_EFFECTS) {
        SectionToolCard(
            title = "Sound Effects & Freeze Frame",
            subtitle = "Insert sound effects at timestamps with custom durations & optional freeze frame",
            isHighContrast = isHighContrast,
            onFeatureGuide = { guideTopic = FeatureGuideTopic.VIDEO_SOUND_EFFECTS },
            featureGuideTag = "btn_feature_guide_sfx"
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SoundEffectPreset.values().filter { it != SoundEffectPreset.CUSTOM_FILE }.forEach { preset ->
                    FilterChip(
                        selected = selectedPreset == preset,
                        onClick = { selectedPreset = preset },
                        label = { Text(preset.displayName) }
                    )
                }
            }

            AccessibleEffectSlider(
                title = "Effect / Freeze Duration",
                value = sfxDurationMs / 1000f,
                onValueChange = { sfxDurationMs = (it * 1000L).toLong().coerceIn(500L, 5000L) },
                valueRange = 0.5f..5.0f,
                step = 0.5f,
                displayValue = "${String.format("%.1f", sfxDurationMs / 1000f)}s",
                spokenDescription = "Duration ${String.format("%.1f", sfxDurationMs / 1000f)} seconds",
                testTag = "slider_sfx_duration"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Freeze Frame at Timestamp", fontWeight = FontWeight.Bold)
                Switch(checked = sfxFreezeFrame, onCheckedChange = { sfxFreezeFrame = it }, modifier = Modifier.testTag("switch_sfx_freeze_frame"))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onAddSoundEffect(selectedPreset, null, sfxDurationMs, sfxFreezeFrame) },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("btn_insert_sfx_preset")
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    PlayheadActionButtonText(
                        prefix = "Insert SFX @",
                        playingFallback = "Insert SFX @ Playhead",
                        currentPositionMs = currentPositionMs,
                        isPlaying = isPlaying,
                        positionFlow = positionFlow
                    )
                }
                OutlinedButton(
                    onClick = { customSfxPicker.launch("audio/*") },
                    modifier = Modifier.height(48.dp).testTag("btn_pick_custom_sfx")
                ) {
                    Text("Import SFX", fontWeight = FontWeight.Bold)
                }
            }

            soundEffects.forEach { sfx ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${sfx.name} @ ${AccessibilityHelper.formatTimeVisual(sfx.timestampMs)} (${sfx.durationMs}ms${if (sfx.freezeFrameAtTimestamp) ", Freeze" else ""})", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onRemoveSoundEffect(sfx.id) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove ${sfx.name}")
                    }
                }
            }
        }
        }

        // 4. Audio Track Manager
        if (focusedFeature == null || focusedFeature == VideoStudioFeatureButton.AUDIO_MANAGER) {
        SectionToolCard(
            title = "Audio Track Manager",
            subtitle = "Keep, mute, or replace video audio track and set master volume",
            isHighContrast = isHighContrast,
            modifier = Modifier.testTag("section_video_audio_manager"),
            onFeatureGuide = { guideTopic = FeatureGuideTopic.VIDEO_AUDIO_MANAGER },
            featureGuideTag = "btn_feature_guide_audio_manager"
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                VideoAudioOption.values().forEach { opt ->
                    FilterChip(
                        selected = audioOption == opt,
                        onClick = { onSelectAudioOption(opt) },
                        label = { Text(opt.label) }
                    )
                }
            }

            if (audioOption == VideoAudioOption.REPLACE_WITH_STUDIO_AUDIO) {
                OutlinedButton(
                    onClick = { replaceAudioPicker.launch("audio/*") },
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_replace_video_audio")
                ) {
                    Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (replacedAudioUri != null) "Replacement Audio Selected (Tap to Change)" else "Pick Replacement Audio File", fontWeight = FontWeight.Bold)
                }
            }

            AccessibleEffectSlider(
                title = "Master Audio Volume",
                value = audioVolume,
                onValueChange = onChangeAudioVolume,
                valueRange = 0f..2.0f,
                step = 0.1f,
                displayValue = "${(audioVolume * 100).toInt()}%",
                spokenDescription = "${(audioVolume * 100).toInt()} percent volume",
                testTag = "slider_video_audio_volume"
            )
        }
        }

        // 5. Adaptive Noise Reducer
        if (focusedFeature == null || focusedFeature == VideoStudioFeatureButton.NOISE_REDUCER) {
        SectionToolCard(
            title = "Adaptive Noise Reducer",
            subtitle = "Remove background hiss, hum, or fan noise cleanly with studio de-noise",
            isHighContrast = isHighContrast,
            modifier = Modifier.testTag("section_video_noise_reducer"),
            onFeatureGuide = { guideTopic = FeatureGuideTopic.VIDEO_NOISE_REDUCER },
            featureGuideTag = "btn_feature_guide_video_denoise"
        ) {
            VideoAdaptiveNoiseReductionSection(
                enabled = noiseReduceEnabled,
                onToggleEnabled = onToggleNoiseReduce,
                params = noiseDenoiseParams,
                onParamsChange = onChangeNoiseDenoiseParams
            )
        }
        }

        // 6. Video to Audio Extractor
        if (focusedFeature == null || focusedFeature == VideoStudioFeatureButton.VIDEO_TO_AUDIO) {
        SectionToolCard(
            title = "Video to Audio",
            subtitle = "Extract the audio track directly into Music/Audio Studio",
            isHighContrast = isHighContrast,
            onFeatureGuide = { guideTopic = FeatureGuideTopic.VIDEO_TO_AUDIO },
            featureGuideTag = "btn_feature_guide_video_to_audio"
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("MP3", "AAC", "M4A", "WAV", "FLAC").forEach { fmt ->
                    FilterChip(
                        selected = extractAudioFormat == fmt,
                        onClick = { extractAudioFormat = fmt },
                        label = { Text(fmt) }
                    )
                }
            }
            Button(
                onClick = { onExtractVideoToAudio(extractAudioFormat) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_video_to_audio")
            ) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Extract Audio as $extractAudioFormat", fontWeight = FontWeight.Bold)
            }
        }
        }
    }

    guideTopic?.let { topic ->
        SingleFeatureGuideDialog(
            topic = topic,
            onDismiss = { guideTopic = null }
        )
    }
}

@Composable
private fun PlayheadActionButtonText(
    prefix: String,
    playingFallback: String,
    currentPositionMs: Long,
    isPlaying: Boolean,
    positionFlow: StateFlow<Long>?
) {
    if (isPlaying) {
        Text(playingFallback, fontWeight = FontWeight.Bold)
    } else {
        val livePosMs = if (positionFlow != null) positionFlow.collectAsState().value else currentPositionMs
        Text("$prefix ${AccessibilityHelper.formatTimeVisual(livePosMs)}", fontWeight = FontWeight.Bold)
    }
}
