package com.example.data

import android.net.Uri

data class ExportedMedia(
    val id: String,
    val title: String,
    val filePath: String,
    val contentUri: Uri?,
    val isVideo: Boolean,
    val durationMillis: Long,
    val fileSizeBytes: Long,
    val createdAtMillis: Long
) {
    val durationFormatted: String
        get() = com.example.accessibility.AccessibilityHelper.formatTimeVisual(durationMillis)

    val durationSpoken: String
        get() = com.example.accessibility.AccessibilityHelper.formatTimeSpoken(durationMillis)

    val sizeFormatted: String
        get() = com.example.accessibility.AccessibilityHelper.formatFileSize(fileSizeBytes)
}
