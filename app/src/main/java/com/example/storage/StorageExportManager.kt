package com.example.storage

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Storage and MediaStore manager adhering strictly to modern Android Scoped Storage guidelines.
 *
 * Folder Requirements:
 * 1. Audio files: "Accessible Studio/Audio" inside public Music / Internal Storage directory
 *    (Relative path: "Music/Accessible Studio/Audio")
 * 2. Video files: "Accessible Video Studio" inside public Movies / Internal Storage directory
 *    (Relative path: "Movies/Accessible Video Studio")
 */
object StorageExportManager {

    const val AUDIO_SUBDIRECTORY = "Audio Studio"
    const val VIDEO_SUBDIRECTORY = "Accessible Studio"
    const val IMAGE_SUBDIRECTORY = "Accessible Studio"
    const val AUDIO_LEGACY_SUBDIRECTORY = "Accessible Studio"

    /**
     * Required storage runtime permissions based on device Android version
     */
    fun getRequiredStoragePermissions(): List<String> {
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            // Android 10+ (API 29+) uses zero-permission Scoped Storage (MediaStore + SAF / Photo Picker)
            emptyList()
        }
    }

    /**
     * Checks if all required storage permissions are currently granted
     */
    fun hasStoragePermissions(context: Context): Boolean {
        val permissions = getRequiredStoragePermissions()
        if (permissions.isEmpty()) return true
        return permissions.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Returns the physical File folder for Audio exports:
     * Public Music / Audio Studio
     */
    fun getAudioExportDirectory(context: Context): File {
        val musicBase = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val audioDir = File(musicBase, AUDIO_SUBDIRECTORY)
        return try {
            if (!audioDir.exists()) audioDir.mkdirs()
            if (audioDir.canWrite()) audioDir else {
                val appMusic = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), AUDIO_SUBDIRECTORY)
                if (!appMusic.exists()) appMusic.mkdirs()
                appMusic
            }
        } catch (_: Exception) {
            val fallback = File(context.getExternalFilesDir(null), "Exports/Audio")
            if (!fallback.exists()) fallback.mkdirs()
            fallback
        }
    }

    /**
     * Returns the physical File folder for Video exports:
     * Public Movies / Accessible Studio
     */
    fun getVideoExportDirectory(context: Context): File {
        val moviesBase = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val videoDir = File(moviesBase, VIDEO_SUBDIRECTORY)
        return try {
            if (!videoDir.exists()) videoDir.mkdirs()
            if (videoDir.canWrite()) videoDir else {
                val appMovies = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), VIDEO_SUBDIRECTORY)
                if (!appMovies.exists()) appMovies.mkdirs()
                appMovies
            }
        } catch (_: Exception) {
            val fallback = File(context.getExternalFilesDir(null), "Exports/Video")
            if (!fallback.exists()) fallback.mkdirs()
            fallback
        }
    }

    /**
     * Returns the physical File folder for Frame Extractor & Video-to-GIF exports:
     * Public Pictures / Accessible Studio
     */
    fun getImageExportDirectory(context: Context): File {
        val picsBase = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val imageDir = File(picsBase, IMAGE_SUBDIRECTORY)
        return try {
            if (!imageDir.exists()) imageDir.mkdirs()
            if (imageDir.canWrite()) imageDir else {
                val appPics = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), IMAGE_SUBDIRECTORY)
                if (!appPics.exists()) appPics.mkdirs()
                appPics
            }
        } catch (_: Exception) {
            val fallback = File(context.getExternalFilesDir(null), "Exports/Images")
            if (!fallback.exists()) fallback.mkdirs()
            fallback
        }
    }

    /**
     * Resolves the MIME type for an audio format
     */
    fun getAudioMimeType(fileNameOrFormat: String): String {
        val lower = fileNameOrFormat.lowercase()
        return when {
            lower.endsWith(".mp3") || lower == "mp3" -> "audio/mpeg"
            lower.endsWith(".flac") || lower == "flac" -> "audio/flac"
            lower.endsWith(".m4a") || lower == "m4a" -> "audio/mp4"
            lower.endsWith(".aac") || lower == "aac" -> "audio/aac"
            lower.endsWith(".ogg") || lower == "ogg" -> "audio/ogg"
            else -> "audio/wav"
        }
    }

    /**
     * Resolves the MIME type for a video format
     */
    fun getVideoMimeType(fileNameOrFormat: String): String {
        val lower = fileNameOrFormat.lowercase()
        return when {
            lower.endsWith(".mkv") || lower == "mkv" -> "video/x-matroska"
            lower.endsWith(".mov") || lower == "mov" -> "video/quicktime"
            lower.endsWith(".avi") || lower == "avi" -> "video/x-msvideo"
            lower.endsWith(".webm") || lower == "webm" -> "video/webm"
            else -> "video/mp4"
        }
    }

    /**
     * Resolves the MIME type for an image or animated GIF format
     */
    fun getImageMimeType(fileNameOrFormat: String): String {
        val lower = fileNameOrFormat.lowercase()
        return when {
            lower.endsWith(".gif") || lower == "gif" -> "image/gif"
            lower.endsWith(".png") || lower == "png" -> "image/png"
            else -> "image/jpeg"
        }
    }

    /**
     * Saves or copies an audio file into public Music / Audio Studio
     * using MediaStore on Android 10+ (API 29+) or direct public File I/O + MediaScanner.
     */
    fun saveAudioFile(
        context: Context,
        sourceFile: File,
        targetFileName: String,
        mimeType: String = getAudioMimeType(targetFileName)
    ): File {
        val targetDir = getAudioExportDirectory(context)
        val destinationFile = File(targetDir, targetFileName)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var mediaStoreSaved = false
            try {
                val resolver = context.contentResolver
                try {
                    resolver.delete(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?",
                        arrayOf(targetFileName, "%$AUDIO_SUBDIRECTORY%")
                    )
                } catch (_: Exception) {}

                if (destinationFile.exists() && sourceFile.absolutePath != destinationFile.absolutePath) {
                    try {
                        destinationFile.delete()
                    } catch (_: Exception) {}
                }

                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, targetFileName)
                    put(MediaStore.Audio.Media.TITLE, targetFileName.substringBeforeLast('.'))
                    put(MediaStore.Audio.Media.ALBUM, "Audio Studio")
                    put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$AUDIO_SUBDIRECTORY")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(sourceFile).use { input ->
                            input.copyTo(out)
                        }
                    }
                    values.clear()
                    values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    mediaStoreSaved = true
                }
            } catch (_: Exception) {}

            if (!mediaStoreSaved || !destinationFile.exists()) {
                try {
                    copyFile(sourceFile, destinationFile)
                } catch (_: Exception) {}
            }
        } else {
            try {
                copyFile(sourceFile, destinationFile)
            } catch (_: Exception) {}
        }

        scanFile(context, destinationFile, mimeType)
        return destinationFile
    }

    /**
     * Saves or copies a video file into public Movies / Accessible Studio
     * using MediaStore on Android 10+ (API 29+) or direct public File I/O + MediaScanner.
     */
    fun saveVideoFile(
        context: Context,
        sourceFile: File,
        targetFileName: String,
        mimeType: String = getVideoMimeType(targetFileName),
        titleTag: String? = null,
        artistTag: String? = null,
        albumTag: String? = null
    ): File {
        val targetDir = getVideoExportDirectory(context)
        val destinationFile = File(targetDir, targetFileName)

        try {
            copyFile(sourceFile, destinationFile)
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, targetFileName)
                    put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$VIDEO_SUBDIRECTORY")
                    if (!titleTag.isNullOrBlank()) put(MediaStore.Video.Media.TITLE, titleTag)
                    if (!artistTag.isNullOrBlank()) put(MediaStore.Video.Media.ARTIST, artistTag)
                    if (!albumTag.isNullOrBlank()) put(MediaStore.Video.Media.ALBUM, albumTag)
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(destinationFile).use { input ->
                            input.copyTo(out)
                        }
                    }
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
            } catch (_: Exception) {}
        }

        scanFile(context, destinationFile, mimeType)
        return destinationFile
    }

    /**
     * Saves an extracted frame (JPEG/PNG) or animated GIF into public Pictures / Accessible Studio
     */
    fun saveImageOrGifFile(
        context: Context,
        sourceFile: File,
        targetFileName: String,
        mimeType: String = getImageMimeType(targetFileName)
    ): File {
        val targetDir = getImageExportDirectory(context)
        val destinationFile = File(targetDir, targetFileName)

        try {
            copyFile(sourceFile, destinationFile)
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, targetFileName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$IMAGE_SUBDIRECTORY")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(destinationFile).use { input ->
                            input.copyTo(out)
                        }
                    }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
            } catch (_: Exception) {}
        }

        scanFile(context, destinationFile, mimeType)
        return destinationFile
    }

    /**
     * MediaScanner connection helper
     */
    fun scanFile(context: Context, file: File, mimeType: String? = null) {
        try {
            MediaScannerConnection.scanFile(
                context.applicationContext,
                arrayOf(file.absolutePath),
                if (mimeType != null) arrayOf(mimeType) else null,
                null
            )
        } catch (_: Exception) {}
    }

    private fun copyFile(source: File, destination: File) {
        if (source.absolutePath == destination.absolutePath) return
        val parent = destination.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()
        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
    }
}
