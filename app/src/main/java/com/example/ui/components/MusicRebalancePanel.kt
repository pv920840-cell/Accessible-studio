package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.example.audio.StemSeparationMode
import com.example.ui.theme.LocalHighContrast
import kotlin.math.roundToInt

/**
 * iZotope RX-inspired Music Rebalance & Stem Separation UI panel.
 * Supports independent Vocals / Bass / Percussion / Instruments rebalance gains,
 * 2-6 Stem Selection dropdown, auditioning, applying rebalance to project audio,
 * and exporting separated stem files.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicRebalancePanel(
    hasAudioLoaded: Boolean,
    isProcessing: Boolean,
    progress: Float = 0f,
    selectedStemMode: StemSeparationMode = StemSeparationMode.FOUR_STEMS,
    onSelectStemMode: (StemSeparationMode) -> Unit = {},
    separatedStemNames: List<String> = emptyList(),
    onPreviewRebalance: (vocalsGain: Float, bassGain: Float, percussionGain: Float, otherGain: Float) -> Unit,
    onApplyRebalance: (vocalsGain: Float, bassGain: Float, percussionGain: Float, otherGain: Float) -> Unit,
    onSeparateStems: (StemSeparationMode) -> Unit,
    onAuditionStem: (stemName: String) -> Unit = {},
    onExportStems: (StemSeparationMode) -> Unit = {},
    onOpenGuide: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isHighContrast = LocalHighContrast.current
    var vocalsGain by remember { mutableFloatStateOf(1.0f) }
    var bassGain by remember { mutableFloatStateOf(1.0f) }
    var percussionGain by remember { mutableFloatStateOf(1.0f) }
    var otherGain by remember { mutableFloatStateOf(1.0f) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_music_rebalance"),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            if (isHighContrast) 2.dp else 1.dp,
            if (isHighContrast) Color.White else MaterialTheme.colorScheme.outlineVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Music Rebalance & Stem Separation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Adjust vocals, bass, percussion & instruments or split into 2–6 stems",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onOpenGuide,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_music_rebalance_guide")
                        .semantics { contentDescription = "Open Music Rebalance Feature Guide" }
                ) {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null)
                }
            }

            if (isProcessing) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                )
            }

            // Rebalance Sliders (0% .. 200%)
            AccessibleEffectSlider(
                title = "Vocals",
                value = vocalsGain,
                onValueChange = { vocalsGain = it },
                valueRange = 0f..2.0f,
                step = 0.05f,
                displayValue = "${(vocalsGain * 100).roundToInt()}%",
                spokenDescription = "${(vocalsGain * 100).roundToInt()} percent",
                testTag = "slider_rebalance_vocals"
            )
            AccessibleEffectSlider(
                title = "Bass",
                value = bassGain,
                onValueChange = { bassGain = it },
                valueRange = 0f..2.0f,
                step = 0.05f,
                displayValue = "${(bassGain * 100).roundToInt()}%",
                spokenDescription = "${(bassGain * 100).roundToInt()} percent",
                testTag = "slider_rebalance_bass"
            )
            AccessibleEffectSlider(
                title = "Percussion / Drums",
                value = percussionGain,
                onValueChange = { percussionGain = it },
                valueRange = 0f..2.0f,
                step = 0.05f,
                displayValue = "${(percussionGain * 100).roundToInt()}%",
                spokenDescription = "${(percussionGain * 100).roundToInt()} percent",
                testTag = "slider_rebalance_percussion"
            )
            AccessibleEffectSlider(
                title = "Instruments / Other",
                value = otherGain,
                onValueChange = { otherGain = it },
                valueRange = 0f..2.0f,
                step = 0.05f,
                displayValue = "${(otherGain * 100).roundToInt()}%",
                spokenDescription = "${(otherGain * 100).roundToInt()} percent",
                testTag = "slider_rebalance_other"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onPreviewRebalance(vocalsGain, bassGain, percussionGain, otherGain) },
                    enabled = hasAudioLoaded && !isProcessing,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_preview_music_rebalance")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Preview Mix")
                }
                Button(
                    onClick = { onApplyRebalance(vocalsGain, bassGain, percussionGain, otherGain) },
                    enabled = hasAudioLoaded && !isProcessing,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_apply_music_rebalance")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Apply Rebalance")
                }
            }

            // Stem Selection Dropdown
            Text(
                text = "Stem Separation Mode",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )

            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = !dropdownExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedStemMode.dropdownLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Select Stems to Separate") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .testTag("dropdown_stem_selection")
                        .semantics {
                            contentDescription = "Stem separation dropdown. Selected: ${selectedStemMode.dropdownLabel}"
                        }
                )
                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    StemSeparationMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.dropdownLabel) },
                            onClick = {
                                onSelectStemMode(mode)
                                dropdownExpanded = false
                            },
                            modifier = Modifier.testTag("dropdown_stem_option_${mode.stemCount}")
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onSeparateStems(selectedStemMode) },
                    enabled = hasAudioLoaded && !isProcessing,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_separate_stems")
                ) {
                    Icon(Icons.Default.CallSplit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Separate ${selectedStemMode.stemCount} Stems")
                }

                if (separatedStemNames.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { onExportStems(selectedStemMode) },
                        enabled = !isProcessing,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_export_separated_stems")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save Stem Files")
                    }
                }
            }

            if (separatedStemNames.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Separated Stems (${separatedStemNames.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    separatedStemNames.forEachIndexed { idx, stemName ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("card_separated_stem_$idx"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stemName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedButton(
                                    onClick = { onAuditionStem(stemName) },
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("btn_audition_stem_$idx")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Play Stem")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
