package com.example.ui.components

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.accessibility.AccessibilityHelper
import com.example.audio.CuePoint
import com.example.ui.theme.LocalHighContrast
import java.util.UUID

data class AudioProjectTab(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val baseFileName: String?,
    val pcm: ShortArray,
    val sampleRate: Int,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val currentPositionMs: Long,
    val cuePoints: List<CuePoint>,
    val isBlankProject: Boolean,
    val sourceUri: Uri? = null
)

@Composable
fun OpenProjectsTabBar(
    projects: List<AudioProjectTab>,
    activeIndex: Int,
    onSelectProject: (Int) -> Unit,
    onCloseProject: (Int) -> Unit,
    onNewBlankProject: () -> Unit,
    onOpenMixDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isHighContrast = LocalHighContrast.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("open_projects_tab_bar"),
        shape = RoundedCornerShape(12.dp),
        color = if (isHighContrast) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            if (isHighContrast) 1.5.dp else 1.dp,
            if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Open Files (${projects.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onNewBlankProject,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("btn_quick_new_blank")
                            .semantics { contentDescription = "Create a new blank project tab" }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Blank Tab", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onOpenMixDialog,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("btn_quick_mix_audio")
                            .semantics { contentDescription = "Open Mix Audio studio to blend another file or clipboard track" }
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Mix", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(projects, key = { _, item -> item.id }) { idx, proj ->
                    val isSelected = idx == activeIndex
                    val durMs = if (proj.sampleRate > 0) ((proj.pcm.size.toDouble() / proj.sampleRate) * 1000.0).toLong() else 0L
                    Surface(
                        onClick = { onSelectProject(idx) },
                        shape = RoundedCornerShape(10.dp),
                        color = when {
                            isSelected && isHighContrast -> MaterialTheme.colorScheme.primary
                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                            isHighContrast -> Color.Black
                            else -> MaterialTheme.colorScheme.surface
                        },
                        border = BorderStroke(
                            if (isSelected) 2.dp else 1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("project_tab_$idx")
                            .semantics {
                                contentDescription = "File tab ${idx + 1} of ${projects.size}: ${proj.title}, duration ${AccessibilityHelper.formatTimeSpoken(durMs)}" +
                                    if (isSelected) ", active" else ", tap to switch"
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 10.dp, end = if (projects.size > 1) 4.dp else 10.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Audiotrack,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isSelected && isHighContrast) Color.Black else MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = proj.title,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isSelected && isHighContrast) Color.Black else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = AccessibilityHelper.formatTimeVisual(durMs),
                                    fontSize = 10.sp,
                                    color = if (isSelected && isHighContrast) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (projects.size > 1) {
                                IconButton(
                                    onClick = { onCloseProject(idx) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("btn_close_project_tab_$idx")
                                        .semantics { contentDescription = "Close tab ${proj.title}" }
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isSelected && isHighContrast) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioMixAudioDialog(
    currentTrackTitle: String,
    clipboardPcm: ShortArray?,
    otherProjects: List<AudioProjectTab>,
    pickedMixTitle: String?,
    pickedMixPcm: ShortArray?,
    sampleRate: Int,
    currentCursorMs: Long,
    onPickFileToMix: () -> Unit,
    onConfirmMix: (
        overlayPcm: ShortArray,
        sourceName: String,
        startMs: Long,
        mainVol: Float,
        overlayVol: Float,
        applyLimiter: Boolean
    ) -> Unit,
    onDismiss: () -> Unit
) {
    // Source mode: 0 = Clipboard, 1 = Open Tab, 2 = Picked File
    val hasClipboard = clipboardPcm != null && clipboardPcm.isNotEmpty()
    val hasOtherTabs = otherProjects.isNotEmpty()
    val hasPickedFile = pickedMixPcm != null && pickedMixPcm.isNotEmpty()

    var sourceMode by remember(hasClipboard, hasOtherTabs, hasPickedFile) {
        mutableIntStateOf(
            when {
                hasPickedFile -> 2
                hasClipboard -> 0
                hasOtherTabs -> 1
                else -> 2
            }
        )
    }
    var selectedTabIdx by remember { mutableIntStateOf(0) }
    var mainVolume by remember { mutableFloatStateOf(1.0f) }
    var overlayVolume by remember { mutableFloatStateOf(0.85f) }
    var mixAtCursor by remember { mutableStateOf(false) }
    var applyPeakLimiter by remember { mutableStateOf(true) }

    val activeOverlayPair: Pair<String, ShortArray?> = when (sourceMode) {
        0 -> "Clipboard" to clipboardPcm
        1 -> {
            val chosen = otherProjects.getOrNull(selectedTabIdx.coerceIn(0, (otherProjects.size - 1).coerceAtLeast(0)))
            (chosen?.title ?: "Tab") to chosen?.pcm
        }
        else -> (pickedMixTitle ?: "Selected File") to pickedMixPcm
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mix Audio Tracks", fontWeight = FontWeight.Black) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Blend audio from your Clipboard, another open tab, or any audio file on your device into \"$currentTrackTitle\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("1. Choose Second Audio Source:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = sourceMode == 0,
                        enabled = hasClipboard,
                        onClick = { sourceMode = 0 },
                        label = {
                            val sec = (clipboardPcm?.size ?: 0).toFloat() / sampleRate.coerceAtLeast(8000)
                            Text(if (hasClipboard) "Clipboard (%.1fs)".format(sec) else "Clipboard (Empty)")
                        },
                        modifier = Modifier.testTag("chip_mix_source_clipboard")
                    )
                    if (hasOtherTabs) {
                        FilterChip(
                            selected = sourceMode == 1,
                            onClick = { sourceMode = 1 },
                            label = { Text("Open Tab (${otherProjects.size})") },
                            modifier = Modifier.testTag("chip_mix_source_tab")
                        )
                    }
                    FilterChip(
                        selected = sourceMode == 2,
                        onClick = { sourceMode = 2 },
                        label = { Text(if (hasPickedFile) "File Ready" else "Audio File") },
                        modifier = Modifier.testTag("chip_mix_source_file")
                    )
                }

                if (sourceMode == 1 && hasOtherTabs) {
                    Text("Select Open Project Tab to Mix:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        otherProjects.forEachIndexed { idx, tab ->
                            FilterChip(
                                selected = selectedTabIdx == idx,
                                onClick = { selectedTabIdx = idx },
                                label = { Text(tab.title, maxLines = 1, fontSize = 11.sp) },
                                modifier = Modifier.testTag("chip_mix_tab_$idx")
                            )
                        }
                    }
                }

                if (sourceMode == 2) {
                    OutlinedButton(
                        onClick = onPickFileToMix,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_pick_file_to_mix")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (pickedMixTitle != null) "Change File ($pickedMixTitle)" else "Pick Audio File to Mix...",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider()

                Text("2. Mix Volume Balance:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                AccessibleEffectSlider(
                    title = "Current Track Level",
                    value = mainVolume,
                    onValueChange = { mainVolume = it },
                    valueRange = 0.0f..1.5f,
                    step = 0.05f,
                    displayValue = "${(mainVolume * 100).toInt()}%",
                    spokenDescription = "Current track mix volume ${(mainVolume * 100).toInt()} percent",
                    testTag = "slider_mix_main_volume"
                )
                AccessibleEffectSlider(
                    title = "Added Audio Level",
                    value = overlayVolume,
                    onValueChange = { overlayVolume = it },
                    valueRange = 0.1f..1.5f,
                    step = 0.05f,
                    displayValue = "${(overlayVolume * 100).toInt()}%",
                    spokenDescription = "Added audio mix volume ${(overlayVolume * 100).toInt()} percent",
                    testTag = "slider_mix_overlay_volume"
                )

                Text("3. Mix Start Position:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !mixAtCursor,
                        onClick = { mixAtCursor = false },
                        label = { Text("Start (0:00)") },
                        modifier = Modifier.testTag("chip_mix_pos_start")
                    )
                    FilterChip(
                        selected = mixAtCursor,
                        onClick = { mixAtCursor = true },
                        label = { Text("At Cursor (${AccessibilityHelper.formatTimeVisual(currentCursorMs)})") },
                        modifier = Modifier.testTag("chip_mix_pos_cursor")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Peak Limiter (-0.5 dB)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Prevents overload clipping when blending", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = applyPeakLimiter,
                        onCheckedChange = { applyPeakLimiter = it },
                        modifier = Modifier.testTag("switch_mix_limiter")
                    )
                }
            }
        },
        confirmButton = {
            val readyPcm = activeOverlayPair.second
            Button(
                enabled = readyPcm != null && readyPcm.isNotEmpty(),
                onClick = {
                    val pcm = readyPcm ?: return@Button
                    onConfirmMix(
                        pcm,
                        activeOverlayPair.first,
                        if (mixAtCursor) currentCursorMs else 0L,
                        mainVolume,
                        overlayVolume,
                        applyPeakLimiter
                    )
                },
                modifier = Modifier.testTag("btn_confirm_mix_audio")
            ) {
                Text("Mix Audio Now", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
