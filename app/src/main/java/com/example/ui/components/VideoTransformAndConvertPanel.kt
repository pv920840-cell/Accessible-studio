package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalHighContrast
import com.example.video.AspectRatioFitMode
import com.example.video.CompressorQuality
import com.example.video.VideoAspectRatio
import com.example.video.VideoFormatOption
import com.example.video.VideoMetadataDetails
import com.example.video.VideoResolutionPreset
import com.example.video.VideoRotation

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoTransformPanel(
    speed: Float,
    pitchCorrect: Boolean,
    onChangeSpeed: (Float) -> Unit,
    onTogglePitchCorrect: (Boolean) -> Unit,
    resolutionPreset: VideoResolutionPreset,
    customWidth: Int,
    customHeight: Int,
    lockAspectRatio: Boolean,
    onSelectResolution: (VideoResolutionPreset) -> Unit,
    onChangeCustomDimensions: (Int, Int) -> Unit,
    onToggleLockAspect: (Boolean) -> Unit,
    aspectRatio: VideoAspectRatio,
    aspectFitMode: AspectRatioFitMode,
    onSelectAspectRatio: (VideoAspectRatio) -> Unit,
    onSelectAspectFitMode: (AspectRatioFitMode) -> Unit,
    rotation: VideoRotation,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
    onSelectRotation: (VideoRotation) -> Unit,
    onToggleFlipH: (Boolean) -> Unit,
    onToggleFlipV: (Boolean) -> Unit,
    reverseVideo: Boolean,
    onToggleReverseVideo: (Boolean) -> Unit
) {
    val isHighContrast = LocalHighContrast.current
    var widthStr by remember(customWidth) { mutableStateOf(customWidth.toString()) }
    var heightStr by remember(customHeight) { mutableStateOf(customHeight.toString()) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

        // 1. Speed Changer (0.25x - 4.0x + Pitch Correction)
        SectionToolCard(
            title = "Speed Changer",
            subtitle = "Adjust playback speed (0.25x–4.0x) with optional pitch correction",
            isHighContrast = isHighContrast
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.5f, 2.0f, 3.0f, 4.0f).forEach { spd ->
                    FilterChip(
                        selected = kotlin.math.abs(speed - spd) < 0.05f,
                        onClick = { onChangeSpeed(spd) },
                        label = { Text("${spd}x") }
                    )
                }
            }
            AccessibleEffectSlider(
                title = "Fine Speed Multiplier",
                value = speed,
                onValueChange = onChangeSpeed,
                valueRange = 0.25f..4.0f,
                step = 0.05f,
                displayValue = "${String.format("%.2f", speed)}x",
                spokenDescription = "${String.format("%.2f", speed)} times speed",
                testTag = "slider_video_speed"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pitch Correction", fontWeight = FontWeight.Bold)
                    Text("Keep voices natural without chipmunk/slow distortion", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = pitchCorrect, onCheckedChange = onTogglePitchCorrect, modifier = Modifier.testTag("switch_speed_pitch_correct"))
            }
        }

        // 2. Resize & Resolution
        SectionToolCard(
            title = "Resize & Resolution",
            subtitle = "Downscale to 1080p, 720p, 480p, 360p or Custom with aspect ratio lock",
            isHighContrast = isHighContrast
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                VideoResolutionPreset.values().forEach { res ->
                    FilterChip(
                        selected = resolutionPreset == res,
                        onClick = { onSelectResolution(res) },
                        label = { Text(res.label) }
                    )
                }
            }
            if (resolutionPreset == VideoResolutionPreset.CUSTOM) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Lock Aspect Ratio (16:9)", fontWeight = FontWeight.Bold)
                    Switch(checked = lockAspectRatio, onCheckedChange = onToggleLockAspect, modifier = Modifier.testTag("switch_lock_aspect"))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = widthStr,
                        onValueChange = { v ->
                            widthStr = v.filter { it.isDigit() }
                            val w = widthStr.toIntOrNull()?.coerceIn(160, 1920) ?: 1280
                            val h = if (lockAspectRatio) ((w * 9) / 16) else (heightStr.toIntOrNull()?.coerceIn(160, 1920) ?: 720)
                            if (lockAspectRatio) heightStr = h.toString()
                            onChangeCustomDimensions(w, h)
                        },
                        label = { Text("Width (px)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = heightStr,
                        onValueChange = { v ->
                            heightStr = v.filter { it.isDigit() }
                            val h = heightStr.toIntOrNull()?.coerceIn(160, 1920) ?: 720
                            val w = if (lockAspectRatio) ((h * 16) / 9) else (widthStr.toIntOrNull()?.coerceIn(160, 1920) ?: 1280)
                            if (lockAspectRatio) widthStr = w.toString()
                            onChangeCustomDimensions(w, h)
                        },
                        label = { Text("Height (px)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 3. Aspect Ratio Changer (Letterbox vs Crop)
        SectionToolCard(
            title = "Aspect Ratio Changer",
            subtitle = "Letterbox or Center-Crop videos to fit 16:9, 1:1, or 9:16",
            isHighContrast = isHighContrast
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                VideoAspectRatio.values().forEach { ratio ->
                    FilterChip(
                        selected = aspectRatio == ratio,
                        onClick = { onSelectAspectRatio(ratio) },
                        label = { Text(ratio.label) }
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AspectRatioFitMode.values().forEach { mode ->
                    FilterChip(
                        selected = aspectFitMode == mode,
                        onClick = { onSelectAspectFitMode(mode) },
                        label = { Text(mode.label) }
                    )
                }
            }
        }

        // 4. Rotate & Flip + Reverse Video
        SectionToolCard(
            title = "Rotate, Flip & Reverse Video",
            subtitle = "Fix orientation (90°/180°/270°), mirror horizontally/vertically, or play backwards",
            isHighContrast = isHighContrast
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                VideoRotation.values().forEach { rot ->
                    FilterChip(
                        selected = rotation == rot,
                        onClick = { onSelectRotation(rot) },
                        label = { Text(rot.label) }
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Mirror Horizontal", fontWeight = FontWeight.Bold)
                Switch(checked = flipHorizontal, onCheckedChange = onToggleFlipH, modifier = Modifier.testTag("switch_flip_h"))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Mirror Vertical", fontWeight = FontWeight.Bold)
                Switch(checked = flipVertical, onCheckedChange = onToggleFlipV, modifier = Modifier.testTag("switch_flip_v"))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Reverse Video & Audio", fontWeight = FontWeight.Bold)
                    Text("Play frames and audio completely backwards on export", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = reverseVideo, onCheckedChange = onToggleReverseVideo, modifier = Modifier.testTag("switch_reverse_video"))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoConvertAndMetaPanel(
    metadata: VideoMetadataDetails,
    compressorQuality: CompressorQuality,
    customBitrateMbps: Float,
    onSelectCompressor: (CompressorQuality) -> Unit,
    onChangeCustomBitrateMbps: (Float) -> Unit,
    onCompressAndExport: () -> Unit,
    outputFormat: VideoFormatOption,
    onSelectOutputFormat: (VideoFormatOption) -> Unit,
    onConvertFormatNow: () -> Unit,
    onRemoveSilenceNow: (thresholdDb: Float) -> Unit,
    onExportGifNow: (fps: Int) -> Unit,
    onExtractFrameAtPlayhead: () -> Unit,
    onBatchExtractFrames: (intervalSec: Int) -> Unit,
    onSaveMetadataTags: (title: String, artist: String, album: String, genre: String) -> Unit
) {
    val isHighContrast = LocalHighContrast.current
    var silenceThresholdDb by remember { mutableFloatStateOf(-35f) }
    var gifFps by remember { mutableIntStateOf(8) }
    var frameIntervalSec by remember { mutableIntStateOf(2) }

    var metaTitle by remember(metadata.title) { mutableStateOf(metadata.title) }
    var metaArtist by remember(metadata.artist) { mutableStateOf(metadata.artist) }
    var metaAlbum by remember(metadata.album) { mutableStateOf(metadata.album) }
    var metaGenre by remember(metadata.genre) { mutableStateOf(metadata.genre) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

        // 1. Silence Remover
        SectionToolCard(
            title = "Silence Remover",
            subtitle = "Auto-detect silent pauses and jump-cut both video and audio",
            isHighContrast = isHighContrast
        ) {
            AccessibleEffectSlider(
                title = "Silence Threshold",
                value = silenceThresholdDb,
                onValueChange = { silenceThresholdDb = it },
                valueRange = -48f..-20f,
                step = 2f,
                displayValue = "${silenceThresholdDb.toInt()} dB",
                spokenDescription = "${silenceThresholdDb.toInt()} decibels silence cutoff",
                testTag = "slider_silence_threshold"
            )
            Button(
                onClick = { onRemoveSilenceNow(silenceThresholdDb) },
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_silence_remover")
            ) {
                Icon(Icons.Default.VolumeOff, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Auto-Cut Silent Pauses & Export", fontWeight = FontWeight.Bold)
            }
        }

        // 2. Video Compressor
        SectionToolCard(
            title = "Video Compressor (H.264 CRF)",
            subtitle = "Reduce file size with High / Medium / Low / Custom bitrate presets",
            isHighContrast = isHighContrast
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CompressorQuality.values().forEach { q ->
                    FilterChip(
                        selected = compressorQuality == q,
                        onClick = { onSelectCompressor(q) },
                        label = { Text("${q.label} (${q.crfLabel})") }
                    )
                }
            }
            if (compressorQuality == CompressorQuality.CUSTOM) {
                AccessibleEffectSlider(
                    title = "Target Bitrate",
                    value = customBitrateMbps,
                    onValueChange = onChangeCustomBitrateMbps,
                    valueRange = 0.4f..8.0f,
                    step = 0.2f,
                    displayValue = "${String.format("%.1f", customBitrateMbps)} Mbps",
                    spokenDescription = "${String.format("%.1f", customBitrateMbps)} megabits per second",
                    testTag = "slider_compressor_bitrate"
                )
            }
            Button(
                onClick = onCompressAndExport,
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_compress_video")
            ) {
                Icon(Icons.Default.Compress, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Compress & Export (${compressorQuality.label})", fontWeight = FontWeight.Bold)
            }
        }

        // 3. Format Converter
        SectionToolCard(
            title = "Format Converter",
            subtitle = "Convert videos across MP4, MKV, MOV, AVI, and WEBM",
            isHighContrast = isHighContrast
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                VideoFormatOption.values().forEach { fmt ->
                    FilterChip(
                        selected = outputFormat == fmt,
                        onClick = { onSelectOutputFormat(fmt) },
                        label = { Text(fmt.label) }
                    )
                }
            }
            OutlinedButton(
                onClick = onConvertFormatNow,
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_convert_video_format")
            ) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Convert & Save as .${outputFormat.extension.uppercase()}", fontWeight = FontWeight.Bold)
            }
        }

        // 4. Video to GIF & Frame Extractor
        SectionToolCard(
            title = "Video to GIF & Frame Extractor",
            subtitle = "Convert short clips to animated GIF or extract JPEG frames to Pictures/Accessible Studio",
            isHighContrast = isHighContrast
        ) {
            AccessibleEffectSlider(
                title = "GIF Frame Rate",
                value = gifFps.toFloat(),
                onValueChange = { gifFps = it.toInt().coerceIn(4, 15) },
                valueRange = 4f..15f,
                step = 1f,
                displayValue = "$gifFps FPS",
                spokenDescription = "$gifFps frames per second",
                testTag = "slider_gif_fps"
            )
            Button(
                onClick = { onExportGifNow(gifFps) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_video_to_gif")
            ) {
                Icon(Icons.Default.Gif, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Convert Selection to Animated GIF", fontWeight = FontWeight.Bold)
            }

            AccessibleEffectSlider(
                title = "Batch Frame Interval",
                value = frameIntervalSec.toFloat(),
                onValueChange = { frameIntervalSec = it.toInt().coerceIn(1, 15) },
                valueRange = 1f..15f,
                step = 1f,
                displayValue = "Every ${frameIntervalSec}s",
                spokenDescription = "Extract 1 frame every $frameIntervalSec seconds",
                testTag = "slider_frame_interval"
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onExtractFrameAtPlayhead,
                    modifier = Modifier.weight(1f).height(48.dp).testTag("btn_extract_single_frame")
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("1 Frame JPEG", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { onBatchExtractFrames(frameIntervalSec) },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("btn_extract_batch_frames")
                ) {
                    Text("Every ${frameIntervalSec}s JPEG", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 5. Video Metadata Viewer & Tag Editor
        SectionToolCard(
            title = "Video Metadata & Tags",
            subtitle = "Inspect technical specifications and edit Title, Artist, Album & Genre tags",
            isHighContrast = isHighContrast
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = "${metadata.width}x${metadata.height} • ${metadata.fps}fps • ${metadata.bitrateKbps} kbps • ${metadata.videoCodec}/${metadata.audioCodec} • ${metadata.fileSizeBytes / 1024} KB",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
            OutlinedTextField(value = metaTitle, onValueChange = { metaTitle = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("input_meta_title"))
            OutlinedTextField(value = metaArtist, onValueChange = { metaArtist = it }, label = { Text("Artist / Creator") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("input_meta_artist"))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = metaAlbum, onValueChange = { metaAlbum = it }, label = { Text("Album") }, singleLine = true, modifier = Modifier.weight(1f).testTag("input_meta_album"))
                OutlinedTextField(value = metaGenre, onValueChange = { metaGenre = it }, label = { Text("Genre") }, singleLine = true, modifier = Modifier.weight(1f).testTag("input_meta_genre"))
            }
            Button(
                onClick = { onSaveMetadataTags(metaTitle, metaArtist, metaAlbum, metaGenre) },
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_save_video_metadata")
            ) {
                Text("Save Video Metadata Tags", fontWeight = FontWeight.Bold)
            }
        }
    }
}
