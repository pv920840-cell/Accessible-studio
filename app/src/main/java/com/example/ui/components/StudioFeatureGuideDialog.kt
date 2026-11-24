package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalHighContrast

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
