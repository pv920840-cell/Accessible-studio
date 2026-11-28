package com.example.ui.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.accessibility.AccessibilityHelper
import com.example.ui.theme.LocalHighContrast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StudioHomeScreen(
    projects: List<StudioProjectSummary>,
    onCreateNewProject: () -> Unit,
    onOpenExistingProject: (StudioProjectSummary) -> Unit,
    onOpenExports: () -> Unit,
    onOpenAbout: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenWhatsNew: () -> Unit,
    onOpenHelp: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenAudioEditor: () -> Unit = {},
    onOpenVideoEditor: () -> Unit = {},
    hasActiveProject: Boolean = false,
    activeProjectTitle: String? = null,
    onSaveActiveProject: () -> Unit = {},
    onResumeActiveProject: () -> Unit = {}
) {
    val isHighContrast = LocalHighContrast.current
    var showExistingProjects by remember { mutableStateOf(true) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isHighContrast) Color.Black else MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .testTag("home_projects_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "home_hero") {
                // Hero Studio Banner + Prominent Create Project CTA
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_hero_card"),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(
                        if (isHighContrast) 2.dp else 1.dp,
                        if (isHighContrast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    ),
                    color = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surface
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isHighContrast) {
                                    Brush.linearGradient(listOf(Color.Black, Color(0xFF101010)))
                                } else {
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                                            MaterialTheme.colorScheme.surface
                                        )
                                    )
                                }
                            )
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "ACCESSIBLE STUDIO • 100% OFFLINE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Create & Edit Audio or Video",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.semantics { heading() }
                                )
                            }

                            // Prominent Create Project Primary CTA
                            Button(
                                onClick = onCreateNewProject,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("btn_home_create_project")
                                    .semantics {
                                        contentDescription = "Create Project. Start a new audio or video editing project."
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                    contentColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Create Project",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }
            }

            item(key = "home_categories") {
                // Accessible Category Buttons: Audio Editor | Video Editor
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HomeCategoryButton(
                        title = "Audio Editor",
                        subtitle = "Trim, Mix, Batch, Effects",
                        icon = Icons.Default.Audiotrack,
                        tag = "btn_home_audio_editor",
                        isHighContrast = isHighContrast,
                        spokenDescription = "Audio Editor. Open accessible buttons for Trim, Mix, Effects, Batch Processing, Music Rebalance, and Voice Recorder.",
                        onClick = onOpenAudioEditor,
                        modifier = Modifier.weight(1f)
                    )
                    HomeCategoryButton(
                        title = "Video Editor",
                        subtitle = "Trim, Overlays, MP4",
                        icon = Icons.Default.Movie,
                        tag = "btn_home_video_editor",
                        isHighContrast = isHighContrast,
                        spokenDescription = "Video Editor. Access video editing tools and projects.",
                        onClick = onOpenVideoEditor,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (hasActiveProject) {
                item(key = "home_active_project_actions") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onSaveActiveProject,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("btn_home_save_project")
                                .semantics {
                                    contentDescription = "Save Project ${activeProjectTitle ?: ""}".trim()
                                },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save Project", fontWeight = FontWeight.Black, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = onResumeActiveProject,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("btn_home_resume_project")
                                .semantics {
                                    contentDescription = "Resume active project ${activeProjectTitle ?: ""}".trim()
                                },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = activeProjectTitle ?: "Resume Active Project",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            item(key = "home_secondary_nav") {
                // Secondary Navigation Row: Exports | Settings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HomeSecondaryNavButton(
                        label = "Exports",
                        icon = Icons.Default.FolderOpen,
                        tag = "btn_home_exports",
                        isHighContrast = isHighContrast,
                        onClick = onOpenExports,
                        modifier = Modifier.weight(1f)
                    )
                    HomeSecondaryNavButton(
                        label = "Settings",
                        icon = Icons.Default.Settings,
                        tag = "btn_home_settings",
                        isHighContrast = isHighContrast,
                        onClick = onOpenSettings,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item(key = "home_existing_projects_toggle") {
                // Button-Based Existing Projects Toggle Header (Not Plain Heading Alone)
                Surface(
                    onClick = { showExistingProjects = !showExistingProjects },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("btn_home_existing_projects")
                        .semantics {
                            contentDescription = "Existing Projects, ${projects.size} items. ${if (showExistingProjects) "Expanded, double tap to collapse" else "Collapsed, double tap to expand"}"
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(
                        1.dp,
                        if (isHighContrast) Color.White else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Existing Projects (${projects.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onBackground
                        )
                        Icon(
                            imageVector = if (showExistingProjects) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }
                }
            }

            if (showExistingProjects) {
                if (projects.isEmpty()) {
                    item(key = "home_empty_projects") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_empty_projects_card"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isHighContrast) Color(0xFF121212) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isHighContrast) Color.White else MaterialTheme.colorScheme.outlineVariant
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Audiotrack,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "No Existing Projects Yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Tap Create Project or Audio Editor / Video Editor above to get started.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(projects, key = { it.id }) { project ->
                        ProjectSummaryCard(
                            project = project,
                            isHighContrast = isHighContrast,
                            onClick = { onOpenExistingProject(project) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeCategoryButton(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tag: String,
    isHighContrast: Boolean,
    spokenDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 56.dp)
            .testTag(tag)
            .semantics(mergeDescendants = true) {
                contentDescription = spokenDescription
            },
        shape = RoundedCornerShape(14.dp),
        color = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        border = BorderStroke(
            if (isHighContrast) 2.dp else 1.5.dp,
            if (isHighContrast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isHighContrast) Color(0xFFE2E8F0) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HomeSecondaryNavButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tag: String,
    isHighContrast: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag(tag)
            .semantics { contentDescription = label },
        shape = RoundedCornerShape(12.dp),
        color = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(
            if (isHighContrast) 1.5.dp else 1.dp,
            if (isHighContrast) Color.White else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProjectSummaryCard(
    project: StudioProjectSummary,
    isHighContrast: Boolean,
    onClick: () -> Unit
) {
    val isVideo = project.mediaType == ProjectMediaType.VIDEO
    val formattedDate = SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(Date(project.lastModifiedMillis))
    val formattedDur = if (project.durationMillis > 0L) {
        AccessibilityHelper.formatTimeVisual(project.durationMillis)
    } else {
        "Ready"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .testTag("project_card_${project.id}")
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "${project.mediaType.label} project ${project.title}, duration $formattedDur, status ${project.statusBadge}. Tap to open."
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            if (isHighContrast) 2.dp else 1.dp,
            if (isHighContrast) Color.White else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (isVideo) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isVideo) Icons.Default.Movie else Icons.Default.Audiotrack,
                        contentDescription = null,
                        tint = if (isVideo) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = if (isVideo) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = project.mediaType.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp,
                            color = if (isVideo) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "$formattedDur • $formattedDate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = project.statusBadge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun CreateProjectScreen(
    selectedType: ProjectMediaType?,
    projectNameDraft: String,
    isLoadingMedia: Boolean,
    loadingMessage: String,
    onSelectType: (ProjectMediaType) -> Unit,
    onProjectNameChange: (String) -> Unit,
    onBrowseAndSelectFile: () -> Unit,
    onStartQuickBlankOrDemo: () -> Unit,
    onBack: () -> Unit,
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
                .widthIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Wizard Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_create_back")
                        .semantics { contentDescription = "Go back to Home Screen" }
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
                Column {
                    Text(
                        text = "STEP 1 OF 2 • NEW PROJECT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Create New Project",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.semantics { heading() }
                    )
                }
            }

            // Step 1: Choose Audio or Video Editing
            Text(
                text = "What would you like to edit?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MediaTypeChoiceCard(
                    title = "Audio Editing",
                    description = "Trim, mix, clean noise, EQ, record voice & export",
                    icon = Icons.Default.Audiotrack,
                    selected = selectedType == ProjectMediaType.AUDIO,
                    isHighContrast = isHighContrast,
                    tag = "card_create_type_audio",
                    onClick = { onSelectType(ProjectMediaType.AUDIO) },
                    modifier = Modifier.weight(1f)
                )
                MediaTypeChoiceCard(
                    title = "Video Editing",
                    description = "Trim clips, narration, overlays, speed & render MP4",
                    icon = Icons.Default.Movie,
                    selected = selectedType == ProjectMediaType.VIDEO,
                    isHighContrast = isHighContrast,
                    tag = "card_create_type_video",
                    onClick = { onSelectType(ProjectMediaType.VIDEO) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Step 2: File Selection Interface
            if (selectedType != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_file_selection_panel"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        if (isHighContrast) 2.dp else 1.dp,
                        if (isHighContrast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "STEP 2 OF 2 • SELECT ${selectedType.label.uppercase()} SOURCE",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedTextField(
                            value = projectNameDraft,
                            onValueChange = onProjectNameChange,
                            label = { Text("Project Name (Optional)") },
                            placeholder = { Text("My ${selectedType.label} Project") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_create_project_name")
                        )

                        if (isLoadingMedia) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("create_loading_indicator"),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = loadingMessage.ifBlank { "Preparing selected file..." },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        } else {
                            Button(
                                onClick = onBrowseAndSelectFile,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("btn_create_browse_file")
                                    .semantics {
                                        contentDescription = "Browse and Select ${selectedType.label} File from your device"
                                    },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Browse and Select File",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = onStartQuickBlankOrDemo,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("btn_create_quick_start"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (selectedType == ProjectMediaType.AUDIO) {
                                        "Start Blank Audio Canvas (Record or Paste)"
                                    } else {
                                        "Start With Sample Demo Video Clip"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaTypeChoiceCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    isHighContrast: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .heightIn(min = 148.dp)
            .testTag(tag)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $description. ${if (selected) "Selected" else "Double tap to select"}"
            },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                selected && isHighContrast -> MaterialTheme.colorScheme.primary
                selected -> MaterialTheme.colorScheme.primaryContainer
                isHighContrast -> Color.Black
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            }
        ),
        border = BorderStroke(
            if (selected || isHighContrast) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = if (selected && isHighContrast) Color.Black else MaterialTheme.colorScheme.primary
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = if (selected && isHighContrast) Color.Black else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected && isHighContrast) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
