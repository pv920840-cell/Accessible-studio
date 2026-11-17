package com.example.data

import android.content.Context

data class StudioSettingsState(
    val isHighContrast: Boolean = false,
    val isHapticsEnabled: Boolean = true,
    val defaultExportFormat: String = "WAV"
)

object StudioPreferences {
    private const val PREFS_NAME = "accessible_studio_prefs"
    private const val KEY_HIGH_CONTRAST = "high_contrast"
    private const val KEY_HAPTICS_ENABLED = "haptics_enabled"
    private const val KEY_DEFAULT_EXPORT_FORMAT = "default_export_format"
    private const val KEY_HIDE_WELCOME_DIALOG = "hide_welcome_dialog"

    val SUPPORTED_AUDIO_EXPORT_FORMATS = listOf("WAV", "MP3", "FLAC", "M4A")

    private val KNOWN_MEDIA_EXTENSIONS = setOf(
        "wav", "mp3", "flac", "m4a", "aac", "ogg", "opus", "wma", "amr", "3gp",
        "mp4", "mkv", "mov", "avi", "webm"
    )

    fun normalizeExportFormat(format: String?): String {
        val upper = format?.trim()?.uppercase().orEmpty()
        return if (upper in SUPPORTED_AUDIO_EXPORT_FORMATS) upper else "WAV"
    }

    /**
     * Extracts the clean base file name (without path segments or known media extension)
     * while preserving the exact opened/chosen name.
     */
    fun extractBaseFileName(rawName: String?): String {
        val cleaned = rawName
            ?.trim()
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.trim()
            .orEmpty()
        if (cleaned.isEmpty()) return ""

        val dotIdx = cleaned.lastIndexOf('.')
        if (dotIdx > 0 && dotIdx < cleaned.length - 1) {
            val ext = cleaned.substring(dotIdx + 1).lowercase()
            if (ext in KNOWN_MEDIA_EXTENSIONS) {
                return cleaned.substring(0, dotIdx).trim()
            }
        }
        return cleaned
    }

    fun loadSettings(context: Context): StudioSettingsState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return StudioSettingsState(
            isHighContrast = prefs.getBoolean(KEY_HIGH_CONTRAST, false),
            isHapticsEnabled = prefs.getBoolean(KEY_HAPTICS_ENABLED, true),
            defaultExportFormat = normalizeExportFormat(prefs.getString(KEY_DEFAULT_EXPORT_FORMAT, "WAV"))
        )
    }

    fun saveSettings(context: Context, settings: StudioSettingsState) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_HIGH_CONTRAST, settings.isHighContrast)
            .putBoolean(KEY_HAPTICS_ENABLED, settings.isHapticsEnabled)
            .putString(KEY_DEFAULT_EXPORT_FORMAT, normalizeExportFormat(settings.defaultExportFormat))
            .apply()
    }

    fun isWelcomeHidden(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_HIDE_WELCOME_DIALOG, false)
    }

    fun setWelcomeHidden(context: Context, hidden: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_HIDE_WELCOME_DIALOG, hidden)
            .apply()
    }
}
