package com.example.ui.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalHighContrast

@Composable
fun AudioEditorScreen(
    activeAudioProject: StudioProjectSummary?,
    hasUnsavedChanges: Boolean,
    audioProjects: List<StudioProjectSummary>,
    onBack: () -> Unit,
    onCreateOrPickAudioProject: () -> Unit,
    onOpenExistingAudioProject: (StudioProjectSummary) -> Unit,
    onOpenAudioTool: (AudioFeatureTool) -> Unit,
    onOpenBatchProcessing: () -> Unit,
    onSaveProject: () -> Unit,
    onExportAudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isHighContrast = LocalHighContrast.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (isHighContrast) Color.Black else MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_audio_editor_back")
                        .semantics { contentDescription = "Go back to Home Screen" }
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Audio Editor",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .testTag("audio_editor_title")
                            .semantics { heading() }
                    )
                    Text(
                        text = "Choose an audio feature or manage your audio project",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isHighContrast) Color(0xFFE2E8F0) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("audio_editor_tools_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (activeAudioProject != null) {
                    item(key = "active_audio_project_card") {
                        ActiveCategoryProjectCard(
                            title = activeAudioProject.title,
                            statusText = when {
                                hasUnsavedChanges -> "Edited"
                                activeAudioProject.statusBadge == "Saved" -> "Saved"
                                else -> "Ready"
                            },
                            isHighContrast = isHighContrast,
                            saveTag = "btn_audio_editor_save_project",
                            exportTag = "btn_audio_editor_export",
                            exportLabel = "Export Audio",
                            onSaveProject = onSaveProject,
                            onExport = onExportAudio
                        )
                    }
                }

                item(key = "btn_audio_pick_or_create") {
                    Button(
                        onClick = onCreateOrPickAudioProject,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("btn_audio_editor_pick_project")
                            .semantics {
                                contentDescription = "Create or Open Audio Project. Pick an audio file or start a blank audio canvas."
                            },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            contentColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (activeAudioProject == null) "Create or Pick Audio Project" else "Open or Create Another Audio Project",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                item(key = "audio_tool_trim") {
                    CategoryFeatureButton(
                        title = "Trim",
                        subtitle = "Set start and end markers, preview, crop, cut & mute selection",
                        icon = Icons.Default.ContentCut,
                        isHighContrast = isHighContrast,
                        tag = "btn_audio_editor_trim",
                        onClick = { onOpenAudioTool(AudioFeatureTool.TRIM_CUT) }
                    )
                }

                item(key = "audio_tool_mix") {
                    CategoryFeatureButton(
                        title = "Mix",
                        subtitle = "Blend a second audio track with volume balance & peak limiter",
                        icon = Icons.Default.Layers,
                        isHighContrast = isHighContrast,
                        tag = "btn_audio_editor_mix",
                        onClick = { onOpenAudioTool(AudioFeatureTool.MIX_TRACKS) }
                    )
                }

                item(key = "audio_tool_effects") {
                    CategoryFeatureButton(
                        title = "Effects",
                        subtitle = "Auto Gain, Adaptive De-Noise, EQ, Compressor, Echo & Reverb",
                        icon = Icons.Default.GraphicEq,
                        isHighContrast = isHighContrast,
                        tag = "btn_audio_editor_effects",
                        onClick = { onOpenAudioTool(AudioFeatureTool.EFFECTS_CLEANING) }
                    )
                }

                item(key = "audio_tool_batch") {
                    CategoryFeatureButton(
                        title = "Batch Processing",
                        subtitle = "Normalize, trim & apply effects to multiple audio files at once",
                        icon = Icons.Default.PlaylistAdd,
                        isHighContrast = isHighContrast,
                        tag = "btn_audio_editor_batch",
                        onClick = onOpenBatchProcessing
                    )
                }

                item(key = "audio_tool_rebalance") {
                    CategoryFeatureButton(
                        title = "Music Rebalance & Stems",
                        subtitle = "Adjust vocals, bass, percussion & instruments or separate 2–6 stems",
                        icon = Icons.Default.Tune,
                        isHighContrast = isHighContrast,
                        tag = "btn_audio_editor_music_rebalance",
                        onClick = { onOpenAudioTool(AudioFeatureTool.MUSIC_REBALANCE) }
                    )
                }

                item(key = "audio_tool_record") {
                    CategoryFeatureButton(
                        title = "Voice Recorder",
                        subtitle = "Record microphone audio or punch-in over a selected region",
                        icon = Icons.Default.Mic,
                        isHighContrast = isHighContrast,
                        tag = "btn_audio_editor_record",
                        onClick = { onOpenAudioTool(AudioFeatureTool.RECORD_VOICE) }
                    )
                }

                if (audioProjects.isNotEmpty()) {
                    item(key = "saved_audio_projects_header") {
                        Text(
                            text = "Saved Audio Projects",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier
                                .padding(top = 6.dp, bottom = 2.dp)
                                .semantics { heading() }
                        )
                    }
                    items(audioProjects, key = { "audio_proj_${it.id}" }) { proj ->
                        OutlinedButton(
                            onClick = { onOpenExistingAudioProject(proj) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("btn_audio_editor_saved_${proj.id}"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Audiotrack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = proj.title,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = proj.statusBadge,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoEditorScreen(
    activeVideoProject: StudioProjectSummary?,
    hasUnsavedChanges: Boolean,
    videoProjects: List<StudioProjectSummary>,
    onBack: () -> Unit,
    onCreateOrPickVideoProject: () -> Unit,
    onOpenExistingVideoProject: (StudioProjectSummary) -> Unit,
    onOpenVideoTool: (VideoFeatureTool) -> Unit,
    onSaveProject: () -> Unit,
    onExportVideo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isHighContrast = LocalHighContrast.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (isHighContrast) Color.Black else MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_video_editor_back")
                        .semantics { contentDescription = "Go back to Home Screen" }
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Video Editor",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .testTag("video_editor_title")
                            .semantics { heading() }
                    )
                    Text(
                        text = "Choose a video editing feature or manage your video project",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isHighContrast) Color(0xFFE2E8F0) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("video_editor_tools_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (activeVideoProject != null) {
                    item(key = "active_video_project_card") {
                        ActiveCategoryProjectCard(
                            title = activeVideoProject.title,
                            statusText = when {
                                hasUnsavedChanges -> "Edited"
                                activeVideoProject.statusBadge == "Saved" -> "Saved"
                                else -> "Ready"
                            },
                            isHighContrast = isHighContrast,
                            saveTag = "btn_video_editor_save_project",
                            exportTag = "btn_video_editor_export",
                            exportLabel = "Export Video",
                            onSaveProject = onSaveProject,
                            onExport = onExportVideo
                        )
                    }
                }

                item(key = "btn_video_pick_or_create") {
                    Button(
                        onClick = onCreateOrPickVideoProject,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("btn_video_editor_pick_project")
                            .semantics {
                                contentDescription = "Create or Pick Video Project. Select a video clip or generate a sample demo clip."
                            },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            contentColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (activeVideoProject == null) "Create or Pick Video Project" else "Open or Create Another Video Project",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                items(VideoFeatureTool.entries, key = { it.name }) { tool ->
                    CategoryFeatureButton(
                        title = tool.title,
                        subtitle = tool.subtitle,
                        icon = videoCategoryIcon(tool),
                        isHighContrast = isHighContrast,
                        tag = "btn_video_editor_${tool.name.lowercase()}",
                        onClick = { onOpenVideoTool(tool) }
                    )
                }

                if (videoProjects.isNotEmpty()) {
                    item(key = "saved_video_projects_header") {
                        Text(
                            text = "Saved Video Projects",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier
                                .padding(top = 6.dp, bottom = 2.dp)
                                .semantics { heading() }
                        )
                    }
                    items(videoProjects, key = { "video_proj_${it.id}" }) { proj ->
                        OutlinedButton(
                            onClick = { onOpenExistingVideoProject(proj) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("btn_video_editor_saved_${proj.id}"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = proj.title,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = proj.statusBadge,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AudioToolSourcePromptDialog(
    selectedTool: AudioFeatureTool,
    onPickAudioFile: () -> Unit,
    onStartBlankAudio: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("dialog_audio_source_prompt"),
        title = {
            Text(
                text = "Open Audio for ${selectedTool.title}",
                fontWeight = FontWeight.Black
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Pick an audio file from your phone or start a blank canvas to open ${selectedTool.title}.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = onPickAudioFile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("btn_prompt_pick_audio_file"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Pick Audio File", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onStartBlankAudio,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("btn_prompt_blank_audio"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Start Blank Audio Canvas", fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun VideoToolSourcePromptDialog(
    selectedTool: VideoFeatureTool,
    onPickVideoFile: () -> Unit,
    onStartDemoVideo: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("dialog_video_source_prompt"),
        title = {
            Text(
                text = "Open Video for ${selectedTool.title}",
                fontWeight = FontWeight.Black
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Pick a video file from your device or start with a sample demo video clip to open ${selectedTool.title}.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = onPickVideoFile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("btn_prompt_pick_video_file"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Pick Video File", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onStartDemoVideo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("btn_prompt_demo_video"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Start Sample Demo Video", fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ActiveCategoryProjectCard(
    title: String,
    statusText: String,
    isHighContrast: Boolean,
    saveTag: String,
    exportTag: String,
    exportLabel: String,
    onSaveProject: () -> Unit,
    onExport: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ),
        border = BorderStroke(
            if (isHighContrast) 2.dp else 1.dp,
            if (isHighContrast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSaveProject,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag(saveTag)
                        .semantics { contentDescription = "Save Project" },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save Project", fontWeight = FontWeight.Black, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onExport,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag(exportTag)
                        .semantics { contentDescription = exportLabel },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(exportLabel, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun CategoryFeatureButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isHighContrast: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .testTag(tag)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $subtitle. Tap to open."
            },
        shape = RoundedCornerShape(16.dp),
        color = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (isHighContrast) 2.dp else 1.dp,
            if (isHighContrast) Color.White else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun videoCategoryIcon(tool: VideoFeatureTool): ImageVector = when (tool) {
    VideoFeatureTool.TRIM_TIMELINE -> Icons.Default.ContentCut
    VideoFeatureTool.AUDIO_MUSIC -> Icons.Default.Mic
    VideoFeatureTool.OVERLAYS_VFX -> Icons.Default.AutoAwesome
    VideoFeatureTool.TRANSFORM_SPEED -> Icons.Default.Speed
    VideoFeatureTool.CONVERT_TOOLS -> Icons.Default.Transform
    VideoFeatureTool.FULL_VIDEO_STUDIO -> Icons.Default.Movie
}
