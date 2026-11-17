package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalHighContrast
import com.example.video.BlurRegionConfig
import com.example.video.ImageOverlayConfig
import com.example.video.SubtitleCue
import com.example.video.TextAnimationStyle
import com.example.video.TextOverlayPosition
import com.example.video.VideoFilter
import com.example.video.VideoVfx

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoOverlaysVfxPanel(
    durationMs: Long,
    textOverlay: String,
    textPosition: TextOverlayPosition,
    textAnimStyle: TextAnimationStyle,
    onChangeTextOverlay: (String) -> Unit,
    onSelectTextPosition: (TextOverlayPosition) -> Unit,
    onSelectTextAnimStyle: (TextAnimationStyle) -> Unit,
    blurConfig: BlurRegionConfig,
    onUpdateBlurConfig: (BlurRegionConfig) -> Unit,
    imageOverlay: ImageOverlayConfig,
    onPickOverlayImage: (Uri) -> Unit,
    onUpdateImageOverlay: (ImageOverlayConfig) -> Unit,
    subtitleCues: List<SubtitleCue>,
    onImportSubtitleFile: (Uri) -> Unit,
    onLoadInlineSrtText: (String) -> Unit,
    onClearSubtitles: () -> Unit,
    selectedFilter: VideoFilter,
    selectedVfx: VideoVfx,
    onSelectFilter: (VideoFilter) -> Unit,
    onSelectVfx: (VideoVfx) -> Unit
) {
    val isHighContrast = LocalHighContrast.current
    var showInlineSrtBox by remember { mutableStateOf(false) }
    var inlineSrtDraft by remember {
        mutableStateOf(
            "1\n00:00:00,500 --> 00:00:03,000\nWelcome to Accessible Studio\n\n2\n00:00:03,200 --> 00:00:06,000\nOn-device captions enabled"
        )
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onPickOverlayImage(uri)
    }

    val subtitleFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportSubtitleFile(uri)
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

        // 1. Text Overlay Studio (Static, Scrolling News Ticker, Typing Animation)
        SectionToolCard(
            title = "Text Overlay Studio",
            subtitle = "Burn custom text, scrolling news tickers, or typing animations onto video",
            isHighContrast = isHighContrast
        ) {
            OutlinedTextField(
                value = textOverlay,
                onValueChange = onChangeTextOverlay,
                label = { Text("Overlay Text / News Ticker") },
                placeholder = { Text("e.g. Breaking News • Accessible Studio") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("input_video_text_overlay")
            )

            Text("Animation Style:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TextAnimationStyle.values().forEach { style ->
                    FilterChip(
                        selected = textAnimStyle == style,
                        onClick = { onSelectTextAnimStyle(style) },
                        label = { Text(style.label) }
                    )
                }
            }

            Text("Vertical Position:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextOverlayPosition.values().forEach { pos ->
                    FilterChip(
                        selected = textPosition == pos,
                        onClick = { onSelectTextPosition(pos) },
                        label = { Text(pos.label) }
                    )
                }
            }
        }

        // 2. Video Blur Editor
        SectionToolCard(
            title = "Video Blur & Mosaic Editor",
            subtitle = "Apply customizable blur or pixel mosaic over a selected timeline duration",
            isHighContrast = isHighContrast
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.BlurOn, contentDescription = null)
                    Text("Enable Video Blur Effect", fontWeight = FontWeight.Bold)
                }
                Switch(
                    checked = blurConfig.enabled,
                    onCheckedChange = { onUpdateBlurConfig(blurConfig.copy(enabled = it)) },
                    modifier = Modifier.testTag("switch_video_blur")
                )
            }

            if (blurConfig.enabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (blurConfig.isMosaic) "Style: Pixelated Mosaic" else "Style: Soft Gaussian Blur", fontWeight = FontWeight.Bold)
                    Switch(
                        checked = blurConfig.isMosaic,
                        onCheckedChange = { onUpdateBlurConfig(blurConfig.copy(isMosaic = it)) },
                        modifier = Modifier.testTag("switch_blur_mosaic_mode")
                    )
                }

                val maxSec = (durationMs / 1000f).coerceAtLeast(2f)
                AccessibleEffectSlider(
                    title = "Blur Start Time",
                    value = (blurConfig.startMs / 1000f).coerceIn(0f, maxSec),
                    onValueChange = { s ->
                        val sMs = (s * 1000L).toLong().coerceIn(0L, blurConfig.endMs)
                        onUpdateBlurConfig(blurConfig.copy(startMs = sMs))
                    },
                    valueRange = 0f..maxSec,
                    step = 0.5f,
                    displayValue = "${String.format("%.1f", blurConfig.startMs / 1000f)}s",
                    spokenDescription = "Blur start at ${blurConfig.startMs / 1000} seconds",
                    testTag = "slider_blur_start"
                )
                AccessibleEffectSlider(
                    title = "Blur End Time",
                    value = (blurConfig.endMs / 1000f).coerceIn(0.5f, maxSec),
                    onValueChange = { e ->
                        val eMs = (e * 1000L).toLong().coerceIn(blurConfig.startMs + 200L, durationMs.coerceAtLeast(1000L))
                        onUpdateBlurConfig(blurConfig.copy(endMs = eMs))
                    },
                    valueRange = 0.5f..maxSec,
                    step = 0.5f,
                    displayValue = "${String.format("%.1f", blurConfig.endMs / 1000f)}s",
                    spokenDescription = "Blur end at ${blurConfig.endMs / 1000} seconds",
                    testTag = "slider_blur_end"
                )
                AccessibleEffectSlider(
                    title = "Blur Intensity",
                    value = blurConfig.intensity,
                    onValueChange = { onUpdateBlurConfig(blurConfig.copy(intensity = it)) },
                    valueRange = 0.2f..1.0f,
                    step = 0.1f,
                    displayValue = "${(blurConfig.intensity * 100).toInt()}%",
                    spokenDescription = "${(blurConfig.intensity * 100).toInt()} percent blur",
                    testTag = "slider_blur_intensity"
                )
            }
        }

        // 3. Image Overlay Adder with Aspect Ratio Mismatch Alert
        SectionToolCard(
            title = "Image Overlay Adder",
            subtitle = "Superimpose an image over video for custom duration with aspect mismatch checks",
            isHighContrast = isHighContrast
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("btn_pick_image_overlay")
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (imageOverlay.uri != null) "Change Image" else "Pick Overlay Image", fontWeight = FontWeight.Bold)
                }
                if (imageOverlay.uri != null) {
                    OutlinedButton(
                        onClick = { onUpdateImageOverlay(ImageOverlayConfig()) },
                        modifier = Modifier.height(48.dp).testTag("btn_clear_image_overlay")
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Remove image overlay")
                    }
                }
            }

            if (imageOverlay.mismatchWarning != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().testTag("card_aspect_mismatch_alert")
                ) {
                    Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text(imageOverlay.mismatchWarning, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            if (imageOverlay.uri != null) {
                AccessibleEffectSlider(
                    title = "Overlay Size",
                    value = imageOverlay.scalePercent,
                    onValueChange = { onUpdateImageOverlay(imageOverlay.copy(scalePercent = it)) },
                    valueRange = 0.15f..0.85f,
                    step = 0.05f,
                    displayValue = "${(imageOverlay.scalePercent * 100).toInt()}%",
                    spokenDescription = "${(imageOverlay.scalePercent * 100).toInt()} percent overlay width",
                    testTag = "slider_image_overlay_scale"
                )
                val maxSec = (durationMs / 1000f).coerceAtLeast(2f)
                AccessibleEffectSlider(
                    title = "Display Until",
                    value = (imageOverlay.endMs / 1000f).coerceIn(0.5f, maxSec),
                    onValueChange = { e -> onUpdateImageOverlay(imageOverlay.copy(endMs = (e * 1000L).toLong())) },
                    valueRange = 0.5f..maxSec,
                    step = 0.5f,
                    displayValue = "${String.format("%.1f", imageOverlay.endMs / 1000f)}s",
                    spokenDescription = "Show image overlay until ${imageOverlay.endMs / 1000} seconds",
                    testTag = "slider_image_overlay_duration"
                )
            }
        }

        // 4. Burn Subtitles (.srt / .vtt)
        SectionToolCard(
            title = "Burn Subtitles (.SRT / .VTT)",
            subtitle = "Permanently burn timed caption cues onto the exported video track",
            isHighContrast = isHighContrast
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { subtitleFilePicker.launch(arrayOf("text/*", "application/x-subrip", "*/*")) },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("btn_import_srt_vtt")
                ) {
                    Icon(Icons.Default.Subtitles, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Import .SRT / .VTT", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { showInlineSrtBox = !showInlineSrtBox },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("btn_toggle_inline_srt")
                ) {
                    Text(if (showInlineSrtBox) "Hide Editor" else "Edit Captions", fontWeight = FontWeight.Bold)
                }
            }

            if (showInlineSrtBox) {
                OutlinedTextField(
                    value = inlineSrtDraft,
                    onValueChange = { inlineSrtDraft = it },
                    label = { Text("SRT / VTT Caption Cues") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth().testTag("input_inline_srt")
                )
                Button(
                    onClick = { onLoadInlineSrtText(inlineSrtDraft); showInlineSrtBox = false },
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_apply_inline_srt")
                ) {
                    Text("Load Caption Cues", fontWeight = FontWeight.Bold)
                }
            }

            if (subtitleCues.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${subtitleCues.size} timed subtitle cues loaded for burn-in", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    OutlinedButton(onClick = onClearSubtitles, modifier = Modifier.height(48.dp)) {
                        Text("Clear")
                    }
                }
            }
        }

        // 5. Color Grading Filters & VFX Overlays
        SectionToolCard(
            title = "Color Filters & VFX",
            subtitle = "Apply cinema color grading and letterbox / scanline / watermark VFX",
            isHighContrast = isHighContrast
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                VideoFilter.values().forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { onSelectFilter(filter) },
                        label = { Text(filter.displayName) }
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                VideoVfx.values().forEach { vfx ->
                    FilterChip(
                        selected = selectedVfx == vfx,
                        onClick = { onSelectVfx(vfx) },
                        label = { Text(vfx.displayName) }
                    )
                }
            }
        }
    }
}
