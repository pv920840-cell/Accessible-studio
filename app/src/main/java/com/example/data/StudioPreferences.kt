package com.example.data

import android.content.Context

data class StudioSettingsState(
    val isHighContrast: Boolean = false,
    val isHapticsEnabled: Boolean = true,
    val defaultExportFormat: String = "WAV",
    val autoCheckUpdates: Boolean = true
)

object StudioPreferences {
    private const val PREFS_NAME = "accessible_studio_prefs"
    private const val KEY_HIGH_CONTRAST = "high_contrast"
    private const val KEY_HAPTICS_ENABLED = "haptics_enabled"
    private const val KEY_DEFAULT_EXPORT_FORMAT = "default_export_format"
    private const val KEY_HIDE_WELCOME_DIALOG = "hide_welcome_dialog"
    private const val KEY_AUTO_CHECK_UPDATES = "auto_check_updates"
    private const val KEY_LAST_UPDATE_CHECK_MS = "last_update_check_ms"
    private const val KEY_LAST_KNOWN_REMOTE_VERSION = "last_known_remote_version"

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
            defaultExportFormat = normalizeExportFormat(prefs.getString(KEY_DEFAULT_EXPORT_FORMAT, "WAV")),
            autoCheckUpdates = prefs.getBoolean(KEY_AUTO_CHECK_UPDATES, true)
        )
    }

    fun saveSettings(context: Context, settings: StudioSettingsState) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_HIGH_CONTRAST, settings.isHighContrast)
            .putBoolean(KEY_HAPTICS_ENABLED, settings.isHapticsEnabled)
            .putString(KEY_DEFAULT_EXPORT_FORMAT, normalizeExportFormat(settings.defaultExportFormat))
            .putBoolean(KEY_AUTO_CHECK_UPDATES, settings.autoCheckUpdates)
            .apply()
    }

    fun isAutoCheckUpdatesEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_CHECK_UPDATES, true)
    }

    fun setAutoCheckUpdatesEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_AUTO_CHECK_UPDATES, enabled)
            .apply()
    }

    fun saveLastUpdateCheck(context: Context, timestampMs: Long, remoteVersion: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit().putLong(KEY_LAST_UPDATE_CHECK_MS, timestampMs)
        if (!remoteVersion.isNullOrBlank()) {
            editor.putString(KEY_LAST_KNOWN_REMOTE_VERSION, remoteVersion.trim())
        }
        editor.apply()
    }

    fun loadLastUpdateCheckMs(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_UPDATE_CHECK_MS, 0L)
    }

    fun loadLastKnownRemoteVersion(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_KNOWN_REMOTE_VERSION, "").orEmpty()
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
