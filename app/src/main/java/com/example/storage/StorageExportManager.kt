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

    private fun resolveWritableDirectory(candidates: List<File?>): File {
        for (dir in candidates) {
            if (dir == null) continue
            try {
                if (!dir.exists()) dir.mkdirs()
                if (dir.exists() && dir.isDirectory && dir.canWrite()) {
                    return dir
                }
            } catch (_: Throwable) {}
        }
        val ultimateFallback = File(candidates.lastOrNull()?.parentFile ?: File("."), "Exports")
        try {
            if (!ultimateFallback.exists()) ultimateFallback.mkdirs()
        } catch (_: Throwable) {}
        return ultimateFallback
    }

    /**
     * Returns the physical File folder for Audio exports:
     * Public Music / Audio Studio (with safe app-external and internal fallbacks on Realme/ColorOS)
     */
    fun getAudioExportDirectory(context: Context): File {
        val publicDir = try {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), AUDIO_SUBDIRECTORY)
        } catch (_: Throwable) { null }
        val appExtMusic = try {
            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.let { File(it, AUDIO_SUBDIRECTORY) }
        } catch (_: Throwable) { null }
        val appExtRoot = try {
            context.getExternalFilesDir(null)?.let { File(it, "Exports/Audio") }
        } catch (_: Throwable) { null }
        val appInternal = File(context.filesDir, "Exports/Audio")
        return resolveWritableDirectory(listOf(publicDir, appExtMusic, appExtRoot, appInternal))
    }

    /**
     * Returns the physical File folder for Video exports:
     * Public Movies / Accessible Studio (with safe app-external and internal fallbacks on Realme/ColorOS)
     */
    fun getVideoExportDirectory(context: Context): File {
        val publicDir = try {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), VIDEO_SUBDIRECTORY)
        } catch (_: Throwable) { null }
        val appExtMovies = try {
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.let { File(it, VIDEO_SUBDIRECTORY) }
        } catch (_: Throwable) { null }
        val appExtRoot = try {
            context.getExternalFilesDir(null)?.let { File(it, "Exports/Video") }
        } catch (_: Throwable) { null }
        val appInternal = File(context.filesDir, "Exports/Video")
        return resolveWritableDirectory(listOf(publicDir, appExtMovies, appExtRoot, appInternal))
    }

    /**
     * Returns the physical File folder for Frame Extractor & Video-to-GIF exports:
     * Public Pictures / Accessible Studio (with safe app-external and internal fallbacks on Realme/ColorOS)
     */
    fun getImageExportDirectory(context: Context): File {
        val publicDir = try {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), IMAGE_SUBDIRECTORY)
        } catch (_: Throwable) { null }
        val appExtPics = try {
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.let { File(it, IMAGE_SUBDIRECTORY) }
        } catch (_: Throwable) { null }
        val appExtRoot = try {
            context.getExternalFilesDir(null)?.let { File(it, "Exports/Images") }
        } catch (_: Throwable) { null }
        val appInternal = File(context.filesDir, "Exports/Images")
        return resolveWritableDirectory(listOf(publicDir, appExtPics, appExtRoot, appInternal))
    }

    private fun getAppPrivateFallbackDir(context: Context, envType: String, subDir: String, fallbackSubDir: String): File {
        val appExtTyped = try {
            context.getExternalFilesDir(envType)?.let { File(it, subDir) }
        } catch (_: Throwable) { null }
        val appExtRoot = try {
            context.getExternalFilesDir(null)?.let { File(it, "Exports/$fallbackSubDir") }
        } catch (_: Throwable) { null }
        val appInternal = File(context.filesDir, "Exports/$fallbackSubDir")
        return resolveWritableDirectory(listOf(appExtTyped, appExtRoot, appInternal))
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

    private fun saveViaMediaStoreQ(
        context: Context,
        collectionUri: Uri,
        sourceFile: File,
        targetFileName: String,
        mimeType: String,
        relativePath: String,
        subDirForDelete: String,
        extraValues: ContentValues.() -> Unit = {}
    ): String? {
        if (!sourceFile.exists() || sourceFile.length() <= 0L) return null
        val resolver = context.contentResolver
        try {
            resolver.delete(
                collectionUri,
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                arrayOf(targetFileName, "%$subDirForDelete%")
            )
        } catch (_: Throwable) {}

        var pendingUri: Uri? = null
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, targetFileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
                extraValues()
            }
            val uri = resolver.insert(collectionUri, values) ?: return null
            pendingUri = uri
            val wrote = resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(out)
                }
                true
            } ?: false
            if (!wrote) {
                try { resolver.delete(uri, null, null) } catch (_: Throwable) {}
                return null
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            pendingUri = null

            var resolvedPath: String? = null
            try {
                resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                        if (idx >= 0) resolvedPath = c.getString(idx)
                    }
                }
            } catch (_: Throwable) {}
            resolvedPath ?: ""
        } catch (_: Throwable) {
            pendingUri?.let { uri ->
                try { resolver.delete(uri, null, null) } catch (_: Throwable) {}
            }
            null
        }
    }

    private fun ensureReadableDestinationFile(
        context: Context,
        sourceFile: File,
        preferredFile: File,
        mediaStoreDataPath: String?,
        envType: String,
        subDir: String,
        fallbackSubDir: String,
        targetFileName: String
    ): File {
        if (!mediaStoreDataPath.isNullOrBlank()) {
            val msFile = File(mediaStoreDataPath)
            if (msFile.exists() && msFile.length() > 0L) return msFile
        }
        if (preferredFile.exists() && preferredFile.length() > 0L) return preferredFile

        try {
            copyFile(sourceFile, preferredFile)
            if (preferredFile.exists() && preferredFile.length() > 0L) return preferredFile
        } catch (_: Throwable) {}

        val privateDir = getAppPrivateFallbackDir(context, envType, subDir, fallbackSubDir)
        val privateFile = File(privateDir, targetFileName)
        try {
            copyFile(sourceFile, privateFile)
            if (privateFile.exists() && privateFile.length() > 0L) return privateFile
        } catch (_: Throwable) {}

        return if (preferredFile.exists()) preferredFile else privateFile
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

        if (destinationFile.exists() && sourceFile.absolutePath != destinationFile.absolutePath) {
            try { destinationFile.delete() } catch (_: Throwable) {}
        }

        val mediaStorePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStoreQ(
                context = context,
                collectionUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                sourceFile = sourceFile,
                targetFileName = targetFileName,
                mimeType = mimeType,
                relativePath = "${Environment.DIRECTORY_MUSIC}/$AUDIO_SUBDIRECTORY",
                subDirForDelete = AUDIO_SUBDIRECTORY
            ) {
                put(MediaStore.Audio.Media.TITLE, targetFileName.substringBeforeLast('.'))
                put(MediaStore.Audio.Media.ALBUM, "Audio Studio")
            }
        } else null

        val resultFile = ensureReadableDestinationFile(
            context = context,
            sourceFile = sourceFile,
            preferredFile = destinationFile,
            mediaStoreDataPath = mediaStorePath,
            envType = Environment.DIRECTORY_MUSIC,
            subDir = AUDIO_SUBDIRECTORY,
            fallbackSubDir = "Audio",
            targetFileName = targetFileName
        )

        scanFile(context, resultFile, mimeType)
        return resultFile
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

        if (destinationFile.exists() && sourceFile.absolutePath != destinationFile.absolutePath) {
            try { destinationFile.delete() } catch (_: Throwable) {}
        }

        val mediaStorePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStoreQ(
                context = context,
                collectionUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                sourceFile = sourceFile,
                targetFileName = targetFileName,
                mimeType = mimeType,
                relativePath = "${Environment.DIRECTORY_MOVIES}/$VIDEO_SUBDIRECTORY",
                subDirForDelete = VIDEO_SUBDIRECTORY
            ) {
                if (!titleTag.isNullOrBlank()) put(MediaStore.Video.Media.TITLE, titleTag)
                if (!artistTag.isNullOrBlank()) put(MediaStore.Video.Media.ARTIST, artistTag)
                if (!albumTag.isNullOrBlank()) put(MediaStore.Video.Media.ALBUM, albumTag)
            }
        } else null

        val resultFile = ensureReadableDestinationFile(
            context = context,
            sourceFile = sourceFile,
            preferredFile = destinationFile,
            mediaStoreDataPath = mediaStorePath,
            envType = Environment.DIRECTORY_MOVIES,
            subDir = VIDEO_SUBDIRECTORY,
            fallbackSubDir = "Video",
            targetFileName = targetFileName
        )

        scanFile(context, resultFile, mimeType)
        return resultFile
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

        if (destinationFile.exists() && sourceFile.absolutePath != destinationFile.absolutePath) {
            try { destinationFile.delete() } catch (_: Throwable) {}
        }

        val mediaStorePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStoreQ(
                context = context,
                collectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                sourceFile = sourceFile,
                targetFileName = targetFileName,
                mimeType = mimeType,
                relativePath = "${Environment.DIRECTORY_PICTURES}/$IMAGE_SUBDIRECTORY",
                subDirForDelete = IMAGE_SUBDIRECTORY
            )
        } else null

        val resultFile = ensureReadableDestinationFile(
            context = context,
            sourceFile = sourceFile,
            preferredFile = destinationFile,
            mediaStoreDataPath = mediaStorePath,
            envType = Environment.DIRECTORY_PICTURES,
            subDir = IMAGE_SUBDIRECTORY,
            fallbackSubDir = "Images",
            targetFileName = targetFileName
        )

        scanFile(context, resultFile, mimeType)
        return resultFile
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
        } catch (_: Throwable) {}
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
