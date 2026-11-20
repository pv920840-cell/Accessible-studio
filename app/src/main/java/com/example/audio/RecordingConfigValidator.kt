package com.example.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import com.example.data.RecordingAudioSource
import com.example.data.RecordingChannel
import com.example.data.RecordingFormat
import com.example.data.RecordingQuality
import com.example.data.StudioPreferences
import com.example.data.StudioSettingsState

data class ResolvedRecordingConfig(
    val quality: RecordingQuality,
    val requestedFormat: RecordingFormat,
    val effectiveFormat: RecordingFormat,
    val sampleRateHz: Int,
    val channelCount: Int,
    val channelMask: Int,
    val bitrateKbps: Int,
    val audioSource: RecordingAudioSource,
    val autoGainEnabled: Boolean,
    val inputGain: Float,
    val minBufferBytes: Int,
    val fallbackNotes: List<String> = emptyList()
) {
    val hasFallback: Boolean get() = fallbackNotes.isNotEmpty()

    fun summaryLabel(): String {
        val chText = if (channelCount >= 2) "Stereo" else "Mono"
        val bitrateText = if (effectiveFormat.isLossless) "Lossless" else "${bitrateKbps} kbps"
        return "${effectiveFormat.label} • ${sampleRateHz / 1000.0} kHz • $chText • $bitrateText • ${audioSource.label}"
    }
}

interface RecordingCapabilityProbe {
    fun getMinBufferBytes(sampleRateHz: Int, channelMask: Int): Int
    fun isFormatSupported(format: RecordingFormat): Boolean
    fun isAudioSourceSupported(source: RecordingAudioSource): Boolean
}

object AndroidRecordingCapabilityProbe : RecordingCapabilityProbe {

    override fun getMinBufferBytes(sampleRateHz: Int, channelMask: Int): Int {
        return try {
            AudioRecord.getMinBufferSize(
                sampleRateHz,
                channelMask,
                AudioFormat.ENCODING_PCM_16BIT
            )
        } catch (_: Throwable) {
            AudioRecord.ERROR_BAD_VALUE
        }
    }

    override fun isFormatSupported(format: RecordingFormat): Boolean {
        return when (format) {
            RecordingFormat.AUTO, RecordingFormat.WAV -> true
            RecordingFormat.M4A -> hasAudioEncoder(MediaFormat.MIMETYPE_AUDIO_AAC)
            RecordingFormat.FLAC -> hasAudioEncoder(MediaFormat.MIMETYPE_AUDIO_FLAC)
            RecordingFormat.OGG -> {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    (hasAudioEncoder(MediaFormat.MIMETYPE_AUDIO_OPUS) || hasAudioEncoder(MediaFormat.MIMETYPE_AUDIO_VORBIS))
            }
            RecordingFormat.MP3 -> hasAudioEncoder(MediaFormat.MIMETYPE_AUDIO_MPEG)
        }
    }

    override fun isAudioSourceSupported(source: RecordingAudioSource): Boolean {
        // Android standard MediaRecorder audio sources DEFAULT, MIC, CAMCORDER, VOICE_RECOGNITION
        // are available across Android API 24+; runtime AudioRecord initialization still verifies HAL state.
        return when (source) {
            RecordingAudioSource.DEFAULT,
            RecordingAudioSource.MIC,
            RecordingAudioSource.CAMCORDER,
            RecordingAudioSource.VOICE_RECOGNITION -> true
        }
    }

    private fun hasAudioEncoder(mimeType: String): Boolean {
        return try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            codecList.codecInfos.any { info ->
                info.isEncoder && info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
            }
        } catch (_: Throwable) {
            false
        }
    }
}

object RecordingConfigValidator {

    private val FALLBACK_SAMPLE_RATES = listOf(44100, 48000, 22050, 16000)

    fun supportedFormatsForDevice(
        probe: RecordingCapabilityProbe = AndroidRecordingCapabilityProbe
    ): List<RecordingFormat> {
        return RecordingFormat.entries.filter { fmt ->
            fmt == RecordingFormat.AUTO || fmt == RecordingFormat.WAV || probe.isFormatSupported(fmt)
        }
    }

    fun resolve(
        settings: StudioSettingsState,
        probe: RecordingCapabilityProbe = AndroidRecordingCapabilityProbe
    ): ResolvedRecordingConfig {
        val fallbackNotes = mutableListOf<String>()

        // 1. Resolve Recording Format (AUTO chooses lossless WAV by default for studio editing, or valid fallback)
        val requestedFormat = settings.recordingFormat
        val effectiveFormat = when {
            requestedFormat == RecordingFormat.AUTO -> RecordingFormat.WAV
            probe.isFormatSupported(requestedFormat) -> requestedFormat
            requestedFormat == RecordingFormat.MP3 && probe.isFormatSupported(RecordingFormat.M4A) -> {
                fallbackNotes.add("MP3 hardware encoder unavailable on this device; falling back to M4A (AAC).")
                RecordingFormat.M4A
            }
            requestedFormat == RecordingFormat.OGG && probe.isFormatSupported(RecordingFormat.M4A) -> {
                fallbackNotes.add("OGG encoding requires Android 10+ Opus/Vorbis encoder; falling back to M4A (AAC).")
                RecordingFormat.M4A
            }
            else -> {
                fallbackNotes.add("${requestedFormat.label} encoder unavailable on this device; falling back to lossless WAV.")
                RecordingFormat.WAV
            }
        }

        // 2. Resolve Bitrate (explicit or Auto from Quality)
        val normalizedBitrate = StudioPreferences.normalizeBitrateKbps(settings.recordingBitrateKbps)
        val effectiveBitrate = if (normalizedBitrate != StudioPreferences.BITRATE_AUTO) {
            normalizedBitrate
        } else {
            when (settings.recordingQuality) {
                RecordingQuality.LOW -> 64
                RecordingQuality.MEDIUM -> 128
                RecordingQuality.HIGH -> 256
            }
        }

        // 3. Resolve Target Channel (Auto: Low/Med -> Mono, High -> Mono unless Stereo requested/supported cleanly)
        val requestedChannelCount = when (settings.recordingChannel) {
            RecordingChannel.MONO -> 1
            RecordingChannel.STEREO -> 2
            RecordingChannel.AUTO -> 1 // Cleanest phase-coherent voice/studio default on mobile microphones
        }

        // 4. Resolve Target Sample Rate (explicit or Auto from Quality)
        val normalizedRate = StudioPreferences.normalizeSampleRateHz(settings.recordingSampleRateHz)
        val targetRate = if (normalizedRate != StudioPreferences.SAMPLE_RATE_AUTO) {
            normalizedRate
        } else {
            when (settings.recordingQuality) {
                RecordingQuality.LOW -> 22050
                RecordingQuality.MEDIUM -> 44100
                RecordingQuality.HIGH -> 48000
            }
        }

        // 5. Probe Hardware AudioRecord Capability with safe Channel + Sample Rate Fallback
        val candidateChannelCounts = if (requestedChannelCount == 2) listOf(2, 1) else listOf(1)
        val candidateRates = (listOf(targetRate) + FALLBACK_SAMPLE_RATES).distinct()

        var resolvedRate: Int? = null
        var resolvedChannels: Int? = null
        var resolvedMinBuf = 0

        // Detect if device/test runtime exposes any positive minBufferSize at all
        val baselineMonoMinBuf = probe.getMinBufferBytes(44100, AudioFormat.CHANNEL_IN_MONO)
        val anyRateSupported = candidateRates.any { rate ->
            probe.getMinBufferBytes(rate, AudioFormat.CHANNEL_IN_MONO) > 0 ||
                (requestedChannelCount == 2 && probe.getMinBufferBytes(rate, AudioFormat.CHANNEL_IN_STEREO) > 0)
        }

        if (anyRateSupported || baselineMonoMinBuf > 0) {
            loop@ for (chCount in candidateChannelCounts) {
                val mask = if (chCount == 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
                for (rate in candidateRates) {
                    val minBuf = probe.getMinBufferBytes(rate, mask)
                    if (minBuf > 0) {
                        resolvedRate = rate
                        resolvedChannels = chCount
                        resolvedMinBuf = minBuf
                        break@loop
                    }
                }
            }
        } else {
            // Unshadowed JVM / sandbox runtime fallback: keep baseline sane values without crashing
            resolvedRate = if (normalizedRate != StudioPreferences.SAMPLE_RATE_AUTO) targetRate else 44100
            resolvedChannels = requestedChannelCount
            resolvedMinBuf = (resolvedRate * resolvedChannels * 2) / 10
        }

        val finalChannels = resolvedChannels ?: 1
        val finalRate = resolvedRate ?: 44100
        val finalChannelMask = if (finalChannels == 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO

        if (settings.recordingChannel == RecordingChannel.STEREO && finalChannels != 2) {
            fallbackNotes.add("Stereo mic input unsupported on this device; safely using Mono.")
        }
        if (normalizedRate != StudioPreferences.SAMPLE_RATE_AUTO && finalRate != targetRate) {
            fallbackNotes.add("Sample rate $targetRate Hz unsupported; safely using $finalRate Hz.")
        }

        // 6. Validate Audio Source
        val requestedSource = settings.recordingAudioSource
        val effectiveSource = if (probe.isAudioSourceSupported(requestedSource)) {
            requestedSource
        } else {
            fallbackNotes.add("${requestedSource.label} audio source unavailable; safely using Microphone.")
            RecordingAudioSource.MIC
        }

        return ResolvedRecordingConfig(
            quality = settings.recordingQuality,
            requestedFormat = requestedFormat,
            effectiveFormat = effectiveFormat,
            sampleRateHz = finalRate,
            channelCount = finalChannels,
            channelMask = finalChannelMask,
            bitrateKbps = effectiveBitrate,
            audioSource = effectiveSource,
            autoGainEnabled = settings.recordingAutoGainEnabled,
            inputGain = StudioPreferences.normalizeInputGain(settings.recordingInputGain),
            minBufferBytes = resolvedMinBuf.coerceAtLeast(1024),
            fallbackNotes = fallbackNotes
        )
    }

    fun sourceFallbackChain(primary: RecordingAudioSource): List<RecordingAudioSource> {
        return listOf(
            primary,
            RecordingAudioSource.MIC,
            RecordingAudioSource.DEFAULT
        ).distinct()
    }
}
