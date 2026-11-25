package com.example.ui.components

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.accessibility.AccessibilityHelper
import com.example.data.ExportedMedia
import com.example.storage.StorageExportManager
import com.example.ui.theme.LocalHighContrast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class AudioPickerBrowseTab(val label: String) {
    ALL_FILES("All files"),
    FOLDERS("Folders")
}

enum class AudioPickerSortOrder(val label: String) {
    NEWEST_FIRST("Newest first"),
    OLDEST_FIRST("Oldest first"),
    NAME_ASC("Name A–Z"),
    LARGEST_FIRST("Largest first")
}

data class AccessibleAudioItem(
    val id: String,
    val title: String,
    val uri: Uri,
    val folderName: String,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val modifiedMs: Long = 0L
)

/**
 * Accessible zero-permission Audio Picker Screen ("Choose audio").
 * Matches the Video Studio picker: never includes a "Navigate up" button; provides All files,
 * Folders, Sort (default Newest first), Search by name, and Open file manager (SAF / GetContent).
 */
@Composable
fun AccessibleAudioPickerDialog(
    exportedAudios: List<ExportedMedia>,
    onSelectAudio: (uri: Uri, title: String) -> Unit,
    onDismiss: () -> Unit,
    preloadedAudios: List<AccessibleAudioItem>? = null
) {
    val context = LocalContext.current
    val isHighContrast = LocalHighContrast.current

    var activeTab by remember { mutableStateOf(AudioPickerBrowseTab.ALL_FILES) }
    var sortOrder by remember { mutableStateOf(AudioPickerSortOrder.NEWEST_FIRST) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var discoveredAudios by remember { mutableStateOf(preloadedAudios ?: emptyList()) }
    var isScanning by remember { mutableStateOf(preloadedAudios == null) }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Throwable) {}
            val resolvedName = resolveAudioDisplayNameFromUri(context, uri) ?: "Selected Audio"
            onSelectAudio(uri, resolvedName)
        }
    }

    val getContentAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val resolvedName = resolveAudioDisplayNameFromUri(context, uri) ?: "Selected Audio"
            onSelectAudio(uri, resolvedName)
        }
    }

    LaunchedEffect(preloadedAudios, exportedAudios) {
        if (preloadedAudios != null) {
            discoveredAudios = preloadedAudios
            isScanning = false
        } else {
            isScanning = true
            discoveredAudios = withContext(Dispatchers.IO) {
                loadAccessibleAudios(context, exportedAudios)
            }
            isScanning = false
        }
    }

    val filteredAudios = remember(discoveredAudios, searchQuery, sortOrder, activeTab, selectedFolder) {
        val q = searchQuery.trim().lowercase()
        val base = discoveredAudios.filter { item ->
            val matchesQuery = q.isEmpty() ||
                item.title.lowercase().contains(q) ||
                item.folderName.lowercase().contains(q)
            val matchesFolder = if (activeTab == AudioPickerBrowseTab.FOLDERS && selectedFolder != null) {
                item.folderName == selectedFolder
            } else {
                true
            }
            matchesQuery && matchesFolder
        }
        when (sortOrder) {
            AudioPickerSortOrder.NEWEST_FIRST -> base.sortedByDescending { it.modifiedMs }
            AudioPickerSortOrder.OLDEST_FIRST -> base.sortedBy { it.modifiedMs }
            AudioPickerSortOrder.NAME_ASC -> base.sortedBy { it.title.lowercase() }
            AudioPickerSortOrder.LARGEST_FIRST -> base.sortedByDescending { it.sizeBytes }
        }
    }

    val folderGroups = remember(discoveredAudios, searchQuery) {
        val q = searchQuery.trim().lowercase()
        discoveredAudios
            .filter { q.isEmpty() || it.title.lowercase().contains(q) || it.folderName.lowercase().contains(q) }
            .groupBy { it.folderName.ifBlank { "Music" } }
            .entries
            .sortedBy { it.key.lowercase() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BackHandler {
            if (activeTab == AudioPickerBrowseTab.FOLDERS && selectedFolder != null) {
                selectedFolder = null
            } else {
                onDismiss()
            }
        }
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
                .testTag("dialog_accessible_audio_picker"),
            shape = RoundedCornerShape(20.dp),
            color = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.background,
            border = if (isHighContrast) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header: "Choose audio" + Close (strictly NO "Navigate up" button)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Choose audio",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.testTag("txt_audio_picker_title")
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("btn_audio_picker_close")
                            .semantics { contentDescription = "Close Choose audio" }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }

                // Search by name
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by name") },
                    label = { Text("Search by name") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_audio_picker_search")
                )

                // All files / Folders tabs + Sort dropdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = activeTab == AudioPickerBrowseTab.ALL_FILES,
                        onClick = {
                            activeTab = AudioPickerBrowseTab.ALL_FILES
                            selectedFolder = null
                        },
                        label = { Text("All files", fontWeight = FontWeight.Bold) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("tab_audio_picker_all_files")
                    )
                    FilterChip(
                        selected = activeTab == AudioPickerBrowseTab.FOLDERS,
                        onClick = { activeTab = AudioPickerBrowseTab.FOLDERS },
                        label = { Text("Folders", fontWeight = FontWeight.Bold) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("tab_audio_picker_folders")
                    )

                    Spacer(Modifier.weight(1f))

                    Box {
                        OutlinedButton(
                            onClick = { sortMenuExpanded = true },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("btn_audio_picker_sort")
                                .semantics {
                                    contentDescription = "Sort audio files. Current order: ${sortOrder.label}"
                                }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Sort: ${sortOrder.label}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            AudioPickerSortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(order.label) },
                                    onClick = {
                                        sortOrder = order
                                        sortMenuExpanded = false
                                    },
                                    modifier = Modifier.testTag("menu_audio_sort_${order.name.lowercase()}")
                                )
                            }
                        }
                    }
                }

                // Open file manager (zero-permission SAF / GetContent system picker)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            try {
                                openDocumentLauncher.launch(arrayOf("audio/*", "application/ogg"))
                            } catch (_: Throwable) {
                                getContentAudioLauncher.launch("audio/*")
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_audio_picker_open_file_manager")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open file manager", fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider()

                if (isScanning) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator()
                            Text("Loading accessible audio files...")
                        }
                    }
                } else if (activeTab == AudioPickerBrowseTab.FOLDERS && selectedFolder == null) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("list_audio_picker_folders"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (folderGroups.isEmpty()) {
                            item {
                                Text(
                                    text = "No folders found yet. Tap Open file manager to choose any audio file on your device.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        } else {
                            itemsIndexed(folderGroups, key = { _, g -> g.key }) { idx, group ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("card_audio_folder_$idx")
                                        .clickable { selectedFolder = group.key },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 56.dp)
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(group.key, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = "${group.value.size} audio file(s)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (activeTab == AudioPickerBrowseTab.FOLDERS && selectedFolder != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Folder: $selectedFolder",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedButton(
                                onClick = { selectedFolder = null },
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("All folders")
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("list_audio_picker_files"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (filteredAudios.isEmpty()) {
                            item {
                                Text(
                                    text = "No matching audio files shown here. Use Open file manager to browse all device audio.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        } else {
                            itemsIndexed(filteredAudios, key = { _, item -> item.id }) { idx, item ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("card_audio_picker_item_$idx")
                                        .clickable { onSelectAudio(item.uri, item.title) },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 56.dp)
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(Icons.Default.Audiotrack, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.title,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val durPart = if (item.durationMs > 0L) {
                                                " • ${AccessibilityHelper.formatTimeVisual(item.durationMs)}"
                                            } else ""
                                            Text(
                                                text = "${item.folderName}$durPart",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    }
}

private fun resolveAudioDisplayNameFromUri(context: Context, uri: Uri): String? {
    try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    val name = c.getString(idx)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
    } catch (_: Throwable) {}
    return uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
}

private fun loadAccessibleAudios(
    context: Context,
    exportedAudios: List<ExportedMedia>
): List<AccessibleAudioItem> {
    val items = LinkedHashMap<String, AccessibleAudioItem>()

    // 1. Studio exported & tracked audio files
    exportedAudios.filter { !it.isVideo }.forEach { media ->
        val file = File(media.filePath)
        val uri = media.contentUri ?: Uri.fromFile(file)
        val folder = file.parentFile?.name ?: "Accessible Studio"
        items[file.absolutePath] = AccessibleAudioItem(
            id = file.absolutePath,
            title = media.title,
            uri = uri,
            folderName = folder,
            durationMs = media.durationMillis,
            sizeBytes = media.fileSizeBytes,
            modifiedMs = media.createdAtMillis
        )
    }

    // 2. App & accessible storage directories (zero broad storage permissions needed)
    val dirs = listOfNotNull(
        StorageExportManager.getAudioExportDirectory(context),
        try { File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Accessible Studio") } catch (_: Throwable) { null },
        try { File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Accessible Audio Studio") } catch (_: Throwable) { null },
        try { context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) } catch (_: Throwable) { null },
        File(context.filesDir, "Exports/Audio"),
        File(context.cacheDir, "stems")
    ).distinctBy { it.absolutePath }

    val audioExts = setOf("wav", "mp3", "m4a", "flac", "aac", "ogg", "opus", "wma", "amr")
    for (dir in dirs) {
        try {
            if (!dir.exists() || !dir.isDirectory) continue
            dir.listFiles()?.forEach { f ->
                val ext = f.extension.lowercase()
                if (f.isFile && f.length() > 0L && ext in audioExts && !items.containsKey(f.absolutePath)) {
                    val uri = try {
                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                    } catch (_: Throwable) {
                        Uri.fromFile(f)
                    }
                    items[f.absolutePath] = AccessibleAudioItem(
                        id = f.absolutePath,
                        title = f.name,
                        uri = uri,
                        folderName = dir.name,
                        durationMs = 0L,
                        sizeBytes = f.length(),
                        modifiedMs = f.lastModified()
                    )
                }
            }
        } catch (_: Throwable) {}
    }

    // 3. Zero-permission MediaStore audio query (returns app-owned / accessible MediaStore entries)
    try {
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
            val nameIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
            val durIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val sizeIdx = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
            val modIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
            val relIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
            } else -1

            while (cursor.moveToNext()) {
                val id = if (idIdx >= 0) cursor.getLong(idIdx) else continue
                val title = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "Audio $id" else "Audio $id"
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                val folder = if (relIdx >= 0) {
                    cursor.getString(relIdx)?.trimEnd('/')?.substringAfterLast('/')?.ifBlank { "Music" } ?: "Music"
                } else {
                    "Music"
                }
                val dur = if (durIdx >= 0) cursor.getLong(durIdx) else 0L
                val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                val mod = if (modIdx >= 0) cursor.getLong(modIdx) * 1000L else 0L
                val key = contentUri.toString()
                if (items.values.none { it.title == title && kotlin.math.abs(it.sizeBytes - size) < 1024L }) {
                    items[key] = AccessibleAudioItem(
                        id = key,
                        title = title,
                        uri = contentUri,
                        folderName = folder,
                        durationMs = dur,
                        sizeBytes = size,
                        modifiedMs = mod
                    )
                }
            }
        }
    } catch (_: Throwable) {}

    return items.values.toList()
}
