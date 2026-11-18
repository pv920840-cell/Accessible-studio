package com.example.data

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

class MediaRepository(private val context: Context) {

    private val _exportedFiles = MutableStateFlow<List<ExportedMedia>>(emptyList())
    val exportedFiles: StateFlow<List<ExportedMedia>> = _exportedFiles.asStateFlow()

    fun getAudioExportDirectory(): File {
        return com.example.storage.StorageExportManager.getAudioExportDirectory(context)
    }

    fun getVideoExportDirectory(): File {
        return com.example.storage.StorageExportManager.getVideoExportDirectory(context)
    }

    fun getExportDirectory(): File = getAudioExportDirectory()

    fun scanMediaFile(file: File, mimeType: String? = null) {
        com.example.storage.StorageExportManager.scanFile(context, file, mimeType)
    }

    suspend fun refreshExports() = withContext(Dispatchers.IO) {
        val audioDir = getAudioExportDirectory()
        val videoDir = getVideoExportDirectory()
        val imageDir = com.example.storage.StorageExportManager.getImageExportDirectory(context)
        val legacyAudioDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Accessible Studio")
        val legacySubAudioDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Accessible Studio/Audio")
        val altVideoDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Accessible Video Studio")
        val appExtRoot = try { context.getExternalFilesDir(null) } catch (_: Throwable) { null }
        val appExportsDir = appExtRoot?.let { File(it, "Exports") }
        val internalExportsDir = File(context.filesDir, "Exports")

        val directoriesToScan = listOfNotNull(
            audioDir,
            videoDir,
            imageDir,
            legacyAudioDir,
            legacySubAudioDir,
            altVideoDir,
            appExportsDir,
            appExtRoot?.let { File(it, "Exports/Audio") },
            appExtRoot?.let { File(it, "Exports/Video") },
            appExtRoot?.let { File(it, "Exports/Images") },
            internalExportsDir,
            File(internalExportsDir, "Audio"),
            File(internalExportsDir, "Video"),
            File(internalExportsDir, "Images")
        ).distinctBy { it.absolutePath }

        val allFiles = mutableListOf<File>()

        for (dir in directoriesToScan) {
            try {
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.let { files ->
                        allFiles.addAll(files.filter { it.isFile && it.length() > 0L && isSupportedMediaFile(it.name) })
                    }
                }
            } catch (_: Throwable) {}
        }

        val list = allFiles.distinctBy { it.absolutePath }
            .sortedByDescending { it.lastModified() }
            .map { file ->
                val isVideo = file.name.endsWith(".mp4", true) ||
                    file.name.endsWith(".mkv", true) ||
                    file.name.endsWith(".mov", true) ||
                    file.name.endsWith(".avi", true) ||
                    file.name.endsWith(".webm", true)
                var duration = 0L
                if (isVideo || file.name.endsWith(".wav", true) || file.name.endsWith(".mp3", true) ||
                    file.name.endsWith(".m4a", true) || file.name.endsWith(".aac", true) || file.name.endsWith(".flac", true)
                ) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(file.absolutePath)
                        val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        duration = durStr?.toLongOrNull() ?: 0L
                    } catch (_: Throwable) {
                    } finally {
                        try { retriever.release() } catch (_: Throwable) {}
                    }
                }

                val uri: Uri? = try {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                } catch (_: Throwable) {
                    null
                }

                ExportedMedia(
                    id = file.absolutePath,
                    title = file.name,
                    filePath = file.absolutePath,
                    contentUri = uri,
                    isVideo = isVideo,
                    durationMillis = duration,
                    fileSizeBytes = file.length(),
                    createdAtMillis = file.lastModified()
                )
            }

        _exportedFiles.value = list
    }

    private fun isSupportedMediaFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".wav") || lower.endsWith(".mp3") ||
                lower.endsWith(".flac") || lower.endsWith(".m4a") ||
                lower.endsWith(".aac") || lower.endsWith(".mp4") ||
                lower.endsWith(".mkv") || lower.endsWith(".mov") ||
                lower.endsWith(".avi") || lower.endsWith(".webm") ||
                lower.endsWith(".gif") || lower.endsWith(".jpg") ||
                lower.endsWith(".jpeg") || lower.endsWith(".png")
    }

    suspend fun deleteExport(media: ExportedMedia): Boolean = withContext(Dispatchers.IO) {
        val deleted = try {
            val file = File(media.filePath)
            if (file.exists()) file.delete() else false
        } catch (_: Throwable) {
            false
        }
        refreshExports()
        deleted
    }

    fun shareExport(media: ExportedMedia) {
        try {
            val file = File(media.filePath)
            val uri = media.contentUri ?: FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val lower = media.title.lowercase()
            val mimeType = when {
                lower.endsWith(".gif") -> "image/gif"
                lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") -> "image/*"
                media.isVideo -> "video/*"
                else -> "audio/*"
            }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share exported media").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (t: Throwable) {
            try {
                Toast.makeText(
                    context,
                    "Unable to share file: ${t.localizedMessage ?: "saved in ${media.filePath}"}",
                    Toast.LENGTH_LONG
                ).show()
            } catch (_: Throwable) {}
        }
    }
}
