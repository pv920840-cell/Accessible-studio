package com.example.data

import android.content.Context
import android.media.MediaRecorder

enum class RecordingQuality(val label: String, val description: String) {
    LOW("Low", "Compact voice capture (22.05 kHz, 64 kbps)"),
    MEDIUM("Medium", "Balanced speech & podcast (44.1 kHz, 128 kbps)"),
    HIGH("High", "Studio fidelity capture (44.1–48 kHz, 256 kbps)");

    companion object {
        fun fromStored(raw: String?): RecordingQuality {
            return entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: HIGH
        }
    }
}

enum class RecordingFormat(
    val label: String,
    val extension: String,
    val mimeType: String,
    val isLossless: Boolean
) {
    AUTO("Auto (Recommended)", "wav", "audio/wav", true),
    WAV("WAV", "wav", "audio/wav", true),
    M4A("M4A (AAC)", "m4a", "audio/mp4", false),
    MP3("MP3", "mp3", "audio/mpeg", false),
    OGG("OGG", "ogg", "audio/ogg", false),
    FLAC("FLAC", "flac", "audio/flac", true);

    companion object {
        fun fromStored(raw: String?): RecordingFormat {
            return entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: AUTO
        }
    }
}

enum class RecordingChannel(val label: String, val channelCount: Int) {
    AUTO("Auto", 0),
    MONO("Mono", 1),
    STEREO("Stereo", 2);

    companion object {
        fun fromStored(raw: String?): RecordingChannel {
            return entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: AUTO
        }
    }
}

enum class RecordingAudioSource(val label: String, val androidSource: Int) {
    DEFAULT("Default", MediaRecorder.AudioSource.DEFAULT),
    MIC("Microphone", MediaRecorder.AudioSource.MIC),
    CAMCORDER("Camcorder", MediaRecorder.AudioSource.CAMCORDER),
    VOICE_RECOGNITION("Voice Recognition", MediaRecorder.AudioSource.VOICE_RECOGNITION);

    companion object {
        fun fromStored(raw: String?): RecordingAudioSource {
            return entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: DEFAULT
        }
    }
}

data class StudioSettingsState(
    val isHighContrast: Boolean = false,
    val isHapticsEnabled: Boolean = true,
    val defaultExportFormat: String = "WAV",
    val autoCheckUpdates: Boolean = true,
    val updateProvider: String = "auto",
    val githubOwner: String = "",
    val githubRepo: String = "",
    // Display & Visual Controls
    val visualModeEnabled: Boolean = true,
    val showZoomControls: Boolean = true,
    val showWaveformDisplay: Boolean = true,
    // Recording Settings
    val recordingQuality: RecordingQuality = RecordingQuality.HIGH,
    val recordingFormat: RecordingFormat = RecordingFormat.AUTO,
    val recordingSampleRateHz: Int = StudioPreferences.SAMPLE_RATE_AUTO,
    val recordingChannel: RecordingChannel = RecordingChannel.AUTO,
    val recordingBitrateKbps: Int = StudioPreferences.BITRATE_AUTO,
    val recordingAudioSource: RecordingAudioSource = RecordingAudioSource.DEFAULT,
    val recordingAutoGainEnabled: Boolean = true,
    val recordingInputGain: Float = 1.0f
) {
    fun withResetRecordingDefaults(): StudioSettingsState {
        return copy(
            recordingQuality = RecordingQuality.HIGH,
            recordingFormat = RecordingFormat.AUTO,
            recordingSampleRateHz = StudioPreferences.SAMPLE_RATE_AUTO,
            recordingChannel = RecordingChannel.AUTO,
            recordingBitrateKbps = StudioPreferences.BITRATE_AUTO,
            recordingAudioSource = RecordingAudioSource.DEFAULT,
            recordingAutoGainEnabled = true,
            recordingInputGain = 1.0f
        )
    }
}

object StudioPreferences {
    private const val PREFS_NAME = "accessible_studio_prefs"
    private const val KEY_HIGH_CONTRAST = "high_contrast"
    private const val KEY_HAPTICS_ENABLED = "haptics_enabled"
    private const val KEY_DEFAULT_EXPORT_FORMAT = "default_export_format"
    private const val KEY_HIDE_WELCOME_DIALOG = "hide_welcome_dialog"
    private const val KEY_AUTO_CHECK_UPDATES = "auto_check_updates"
    private const val KEY_LAST_UPDATE_CHECK_MS = "last_update_check_ms"
    private const val KEY_LAST_KNOWN_REMOTE_VERSION = "last_known_remote_version"
    private const val KEY_UPDATE_PROVIDER = "update_provider"
    private const val KEY_GITHUB_OWNER = "github_owner"
    private const val KEY_GITHUB_REPO = "github_repo"

    // Display & Visual Controls Keys
    private const val KEY_VISUAL_MODE_ENABLED = "visual_mode_enabled"
    private const val KEY_SHOW_ZOOM_CONTROLS = "show_zoom_controls"
    private const val KEY_SHOW_WAVEFORM_DISPLAY = "show_waveform_display"

    // Recording Settings Keys
    private const val KEY_RECORDING_QUALITY = "recording_quality"
    private const val KEY_RECORDING_FORMAT = "recording_format"
    private const val KEY_RECORDING_SAMPLE_RATE = "recording_sample_rate_hz"
    private const val KEY_RECORDING_CHANNEL = "recording_channel"
    private const val KEY_RECORDING_BITRATE = "recording_bitrate_kbps"
    private const val KEY_RECORDING_AUDIO_SOURCE = "recording_audio_source"
    private const val KEY_RECORDING_AUTO_GAIN = "recording_auto_gain_enabled"
    private const val KEY_RECORDING_INPUT_GAIN = "recording_input_gain"

    const val UPDATE_PROVIDER_AUTO = "auto"
    const val UPDATE_PROVIDER_GITHUB = "github"
    const val UPDATE_PROVIDER_SERVER = "server"

    const val SAMPLE_RATE_AUTO = 0
    val SUPPORTED_SAMPLE_RATE_OPTIONS = listOf(0, 16000, 22050, 44100, 48000)

    const val BITRATE_AUTO = 0
    val SUPPORTED_BITRATE_OPTIONS = listOf(0, 64, 128, 192, 256, 320)

    const val MIN_INPUT_GAIN = 0.25f
    const val MAX_INPUT_GAIN = 2.0f

    val SUPPORTED_AUDIO_EXPORT_FORMATS = listOf("WAV", "MP3", "FLAC", "M4A")

    private val KNOWN_MEDIA_EXTENSIONS = setOf(
        "wav", "mp3", "flac", "m4a", "aac", "ogg", "opus", "wma", "amr", "3gp",
        "mp4", "mkv", "mov", "avi", "webm"
    )

    fun normalizeExportFormat(format: String?): String {
        val upper = format?.trim()?.uppercase().orEmpty()
        return if (upper in SUPPORTED_AUDIO_EXPORT_FORMATS) upper else "WAV"
    }

    fun normalizeSampleRateHz(sampleRateHz: Int): Int {
        return if (sampleRateHz in SUPPORTED_SAMPLE_RATE_OPTIONS) sampleRateHz else SAMPLE_RATE_AUTO
    }

    fun normalizeBitrateKbps(bitrateKbps: Int): Int {
        return if (bitrateKbps in SUPPORTED_BITRATE_OPTIONS) bitrateKbps else BITRATE_AUTO
    }

    fun normalizeInputGain(gain: Float): Float {
        if (!gain.isFinite()) return 1.0f
        return gain.coerceIn(MIN_INPUT_GAIN, MAX_INPUT_GAIN)
    }

    fun formatSampleRateLabel(sampleRateHz: Int): String {
        return if (sampleRateHz == SAMPLE_RATE_AUTO) "Auto" else "$sampleRateHz Hz"
    }

    fun formatBitrateLabel(bitrateKbps: Int): String {
        return if (bitrateKbps == BITRATE_AUTO) "Auto" else "$bitrateKbps kbps"
    }

    fun normalizeUpdateProvider(provider: String?): String {
        return when (provider?.trim()?.lowercase()) {
            UPDATE_PROVIDER_GITHUB -> UPDATE_PROVIDER_GITHUB
            UPDATE_PROVIDER_SERVER -> UPDATE_PROVIDER_SERVER
            else -> UPDATE_PROVIDER_AUTO
        }
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
            autoCheckUpdates = prefs.getBoolean(KEY_AUTO_CHECK_UPDATES, true),
            updateProvider = normalizeUpdateProvider(prefs.getString(KEY_UPDATE_PROVIDER, UPDATE_PROVIDER_AUTO)),
            githubOwner = prefs.getString(KEY_GITHUB_OWNER, "").orEmpty().trim(),
            githubRepo = prefs.getString(KEY_GITHUB_REPO, "").orEmpty().trim(),
            visualModeEnabled = prefs.getBoolean(KEY_VISUAL_MODE_ENABLED, true),
            showZoomControls = prefs.getBoolean(KEY_SHOW_ZOOM_CONTROLS, true),
            showWaveformDisplay = prefs.getBoolean(KEY_SHOW_WAVEFORM_DISPLAY, true),
            recordingQuality = RecordingQuality.fromStored(prefs.getString(KEY_RECORDING_QUALITY, RecordingQuality.HIGH.name)),
            recordingFormat = RecordingFormat.fromStored(prefs.getString(KEY_RECORDING_FORMAT, RecordingFormat.AUTO.name)),
            recordingSampleRateHz = normalizeSampleRateHz(prefs.getInt(KEY_RECORDING_SAMPLE_RATE, SAMPLE_RATE_AUTO)),
            recordingChannel = RecordingChannel.fromStored(prefs.getString(KEY_RECORDING_CHANNEL, RecordingChannel.AUTO.name)),
            recordingBitrateKbps = normalizeBitrateKbps(prefs.getInt(KEY_RECORDING_BITRATE, BITRATE_AUTO)),
            recordingAudioSource = RecordingAudioSource.fromStored(prefs.getString(KEY_RECORDING_AUDIO_SOURCE, RecordingAudioSource.DEFAULT.name)),
            recordingAutoGainEnabled = prefs.getBoolean(KEY_RECORDING_AUTO_GAIN, true),
            recordingInputGain = normalizeInputGain(prefs.getFloat(KEY_RECORDING_INPUT_GAIN, 1.0f))
        )
    }

    fun saveSettings(context: Context, settings: StudioSettingsState) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_HIGH_CONTRAST, settings.isHighContrast)
            .putBoolean(KEY_HAPTICS_ENABLED, settings.isHapticsEnabled)
            .putString(KEY_DEFAULT_EXPORT_FORMAT, normalizeExportFormat(settings.defaultExportFormat))
            .putBoolean(KEY_AUTO_CHECK_UPDATES, settings.autoCheckUpdates)
            .putString(KEY_UPDATE_PROVIDER, normalizeUpdateProvider(settings.updateProvider))
            .putString(KEY_GITHUB_OWNER, settings.githubOwner.trim())
            .putString(KEY_GITHUB_REPO, settings.githubRepo.trim())
            .putBoolean(KEY_VISUAL_MODE_ENABLED, settings.visualModeEnabled)
            .putBoolean(KEY_SHOW_ZOOM_CONTROLS, settings.showZoomControls)
            .putBoolean(KEY_SHOW_WAVEFORM_DISPLAY, settings.showWaveformDisplay)
            .putString(KEY_RECORDING_QUALITY, settings.recordingQuality.name)
            .putString(KEY_RECORDING_FORMAT, settings.recordingFormat.name)
            .putInt(KEY_RECORDING_SAMPLE_RATE, normalizeSampleRateHz(settings.recordingSampleRateHz))
            .putString(KEY_RECORDING_CHANNEL, settings.recordingChannel.name)
            .putInt(KEY_RECORDING_BITRATE, normalizeBitrateKbps(settings.recordingBitrateKbps))
            .putString(KEY_RECORDING_AUDIO_SOURCE, settings.recordingAudioSource.name)
            .putBoolean(KEY_RECORDING_AUTO_GAIN, settings.recordingAutoGainEnabled)
            .putFloat(KEY_RECORDING_INPUT_GAIN, normalizeInputGain(settings.recordingInputGain))
            .apply()
    }

    fun resetRecordingDefaults(context: Context): StudioSettingsState {
        val reset = loadSettings(context).withResetRecordingDefaults()
        saveSettings(context, reset)
        return reset
    }

    fun saveGitHubUpdateSource(context: Context, provider: String, owner: String, repo: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_UPDATE_PROVIDER, normalizeUpdateProvider(provider))
            .putString(KEY_GITHUB_OWNER, owner.trim())
            .putString(KEY_GITHUB_REPO, repo.trim())
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
