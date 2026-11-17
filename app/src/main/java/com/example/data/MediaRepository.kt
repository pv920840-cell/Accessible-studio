package com.example.data

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
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

    /**
     * Dedicated storage directory for exported Audio files:
     * Internal Storage / Music / Accessible Studio/Audio
     */
    fun getAudioExportDirectory(): File {
        return com.example.storage.StorageExportManager.getAudioExportDirectory(context)
    }

    /**
     * Dedicated storage directory for exported Video files:
     * Internal Storage / Movies / Accessible Video Studio
     */
    fun getVideoExportDirectory(): File {
        return com.example.storage.StorageExportManager.getVideoExportDirectory(context)
    }

    /**
     * Generic fallback export directory
     */
    fun getExportDirectory(): File = getAudioExportDirectory()

    /**
     * Notifies Android media scanner so files appear immediately in user's system libraries
     */
    fun scanMediaFile(file: File, mimeType: String? = null) {
        if (context != null) {
            com.example.storage.StorageExportManager.scanFile(context, file, mimeType)
        }
    }

    suspend fun refreshExports() = withContext(Dispatchers.IO) {
        val audioDir = getAudioExportDirectory()
        val videoDir = getVideoExportDirectory()
        val imageDir = com.example.storage.StorageExportManager.getImageExportDirectory(context)
        val legacyAudioDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Accessible Studio")
        val legacySubAudioDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Accessible Studio/Audio")
        val altVideoDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Accessible Video Studio")
        val appExportsDir = File(context.getExternalFilesDir(null), "Exports")

        val directoriesToScan = listOf(audioDir, videoDir, imageDir, legacyAudioDir, legacySubAudioDir, altVideoDir, appExportsDir).distinctBy { it.absolutePath }
        val allFiles = mutableListOf<File>()

        for (dir in directoriesToScan) {
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.let { files ->
                    allFiles.addAll(files.filter { it.isFile && isSupportedMediaFile(it.name) })
                }
            }
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
                    try {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(file.absolutePath)
                        val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        duration = durStr?.toLongOrNull() ?: 0L
                        retriever.release()
                    } catch (_: Exception) {}
                }

                val uri: Uri? = try {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                } catch (_: Exception) {
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
        val file = File(media.filePath)
        val deleted = if (file.exists()) file.delete() else false
        refreshExports()
        deleted
    }

    fun shareExport(media: ExportedMedia) {
        val uri = media.contentUri ?: run {
            val file = File(media.filePath)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
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
    }
}
