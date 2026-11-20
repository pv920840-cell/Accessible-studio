package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import com.example.data.RecordingAudioSource
import com.example.data.StudioPreferences
import com.example.data.StudioSettingsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class RecordedAudioResult(
    val studioPcm: ShortArray,
    val rawHardwarePcm: ShortArray,
    val hardwareSampleRateHz: Int,
    val channelCount: Int,
    val resolvedConfig: ResolvedRecordingConfig
)

class AudioRecorderManager(private val context: Context) {

    @Volatile
    var sampleRate: Int = StudioAudioProcessor.SAMPLE_RATE
        private set

    private var audioRecord: AudioRecord? = null
    private var recordJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordedDurationMs = MutableStateFlow(0L)
    val recordedDurationMs: StateFlow<Long> = _recordedDurationMs.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    // Level & Clipping Telemetry
    private val _isTooLoudWarning = MutableStateFlow(false)
    val isTooLoudWarning: StateFlow<Boolean> = _isTooLoudWarning.asStateFlow()

    private var configuredSettings: StudioSettingsState = try {
        StudioPreferences.loadSettings(context)
    } catch (_: Throwable) {
        StudioSettingsState()
    }

    private val _resolvedConfig = MutableStateFlow(RecordingConfigValidator.resolve(configuredSettings))
    val resolvedConfig: StateFlow<ResolvedRecordingConfig> = _resolvedConfig.asStateFlow()

    // Configurable Mic Parameters
    var bufferSizeOption: Int = 2048 // 1024, 2048, 4096 (Default 2048)
    var autoGainEnabled: Boolean = configuredSettings.recordingAutoGainEnabled
    var micGainVolume: Float = configuredSettings.recordingInputGain // 0.25f to 2.0f

    @Volatile
    private var activeHardwareSampleRateHz: Int = StudioAudioProcessor.SAMPLE_RATE

    @Volatile
    private var activeChannelCount: Int = 1

    private val recordedByteStream = ByteArrayOutputStream()

    fun applySettings(settings: StudioSettingsState) {
        configuredSettings = settings
        autoGainEnabled = settings.recordingAutoGainEnabled
        micGainVolume = StudioPreferences.normalizeInputGain(settings.recordingInputGain)
        val resolved = RecordingConfigValidator.resolve(settings)
        _resolvedConfig.value = resolved
        if (!_isRecording.value) {
            sampleRate = resolved.sampleRateHz
        }
    }

    @SuppressLint("MissingPermission")
    private fun tryOpenAudioRecord(
        resolved: ResolvedRecordingConfig
    ): Triple<AudioRecord, ResolvedRecordingConfig, Int>? {
        val sources = RecordingConfigValidator.sourceFallbackChain(resolved.audioSource)
        val channelCandidates = if (resolved.channelCount >= 2) {
            listOf(2 to AudioFormat.CHANNEL_IN_STEREO, 1 to AudioFormat.CHANNEL_IN_MONO)
        } else {
            listOf(1 to AudioFormat.CHANNEL_IN_MONO)
        }
        val rateCandidates = listOf(resolved.sampleRateHz, 44100, 48000, 22050, 16000).distinct()

        for ((chCount, chMask) in channelCandidates) {
            for (rate in rateCandidates) {
                val minBuf = try {
                    AudioRecord.getMinBufferSize(rate, chMask, AudioFormat.ENCODING_PCM_16BIT)
                } catch (_: Throwable) {
                    AudioRecord.ERROR_BAD_VALUE
                }
                if (minBuf <= 0) continue

                val hardwareBufferSize = kotlin.math.max(minBuf * 2, bufferSizeOption * chCount * 2)
                for (source in sources) {
                    var candidate: AudioRecord? = null
                    try {
                        candidate = AudioRecord(
                            source.androidSource,
                            rate,
                            chMask,
                            AudioFormat.ENCODING_PCM_16BIT,
                            hardwareBufferSize
                        )
                        if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                            val notes = resolved.fallbackNotes.toMutableList()
                            if (source != resolved.audioSource) {
                                notes.add("${resolved.audioSource.label} source fell back to ${source.label}.")
                            }
                            if (chCount != resolved.channelCount) {
                                notes.add("Stereo channel fell back to Mono.")
                            }
                            if (rate != resolved.sampleRateHz) {
                                notes.add("Sample rate ${resolved.sampleRateHz} Hz fell back to $rate Hz.")
                            }
                            val updatedResolved = resolved.copy(
                                sampleRateHz = rate,
                                channelCount = chCount,
                                channelMask = chMask,
                                audioSource = source,
                                minBufferBytes = minBuf,
                                fallbackNotes = notes.distinct()
                            )
                            return Triple(candidate, updatedResolved, hardwareBufferSize)
                        } else {
                            candidate.release()
                        }
                    } catch (_: Throwable) {
                        try {
                            candidate?.release()
                        } catch (_: Throwable) {}
                    }
                }
            }
        }
        return null
    }

    @SuppressLint("MissingPermission")
    fun startRecording(onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (_isRecording.value) return

        try {
            val syncedSettings = configuredSettings.copy(
                recordingAutoGainEnabled = autoGainEnabled,
                recordingInputGain = StudioPreferences.normalizeInputGain(micGainVolume)
            )
            val initialResolved = RecordingConfigValidator.resolve(syncedSettings)
            val opened = tryOpenAudioRecord(initialResolved)

            if (opened == null) {
                onError("Could not initialize microphone recorder with device audio inputs.")
                return
            }

            val (recordInstance, effectiveResolved, _) = opened
            audioRecord = recordInstance
            _resolvedConfig.value = effectiveResolved
            activeHardwareSampleRateHz = effectiveResolved.sampleRateHz
            activeChannelCount = effectiveResolved.channelCount
            sampleRate = effectiveResolved.sampleRateHz

            audioRecord?.startRecording()
            _isRecording.value = true
            _recordedDurationMs.value = 0L
            _isTooLoudWarning.value = false
            recordedByteStream.reset()

            recordJob = scope.launch {
                val frameSamples = bufferSizeOption.coerceAtLeast(512)
                val chCount = effectiveResolved.channelCount.coerceIn(1, 2)
                val buffer = ShortArray(frameSamples * chCount)
                val byteBuffer = ByteBuffer.allocate(frameSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
                val startTime = System.currentTimeMillis()

                var runningAgcGain = 1.0f

                while (isActive && _isRecording.value) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        val validFrames = if (chCount == 2) read / 2 else read
                        if (validFrames <= 0) continue

                        byteBuffer.clear()
                        var maxAmp = 0

                        // 1. Calculate peak across frames (downmixing stereo L+R cleanly)
                        for (f in 0 until validFrames) {
                            val sampleVal = if (chCount == 2) {
                                (buffer[f * 2].toInt() + buffer[f * 2 + 1].toInt()) / 2
                            } else {
                                buffer[f].toInt()
                            }
                            val a = kotlin.math.abs(sampleVal)
                            if (a > maxAmp) maxAmp = a
                        }

                        // 2. Auto Mic Gain (AGC): If signal is near clipping (> 28000), back off smoothly
                        if (autoGainEnabled) {
                            if (maxAmp > 28000) {
                                val target = 26000f / maxAmp.toFloat()
                                runningAgcGain = (runningAgcGain * 0.7f) + (target * 0.3f)
                                _isTooLoudWarning.value = true
                            } else if (maxAmp in 301..5999) {
                                runningAgcGain = kotlin.math.min(1.4f, runningAgcGain + 0.02f)
                                _isTooLoudWarning.value = false
                            } else {
                                runningAgcGain = (runningAgcGain * 0.98f) + (1.0f * 0.02f)
                                _isTooLoudWarning.value = false
                            }
                        } else {
                            runningAgcGain = 1.0f
                            _isTooLoudWarning.value = (maxAmp >= 31500)
                        }

                        val effectiveGain = runningAgcGain * micGainVolume

                        // 3. Apply safe soft-limiting and write mono-normalized PCM stream
                        for (f in 0 until validFrames) {
                            val rawSample = if (chCount == 2) {
                                (buffer[f * 2].toFloat() + buffer[f * 2 + 1].toFloat()) * 0.5f
                            } else {
                                buffer[f].toFloat()
                            }
                            var scaled = rawSample * effectiveGain
                            if (kotlin.math.abs(scaled) > 30000f) {
                                val excess = kotlin.math.abs(scaled) - 30000f
                                val limited = 30000f + (kotlin.math.atan(excess / 2500f) * 1500f)
                                scaled = if (scaled > 0) limited else -limited
                            }
                            val safeShort = scaled.toInt().coerceIn(-32767, 32767).toShort()
                            byteBuffer.putShort(safeShort)
                        }

                        recordedByteStream.write(byteBuffer.array(), 0, validFrames * 2)
                        _amplitude.value = (maxAmp.toFloat() / 32767f).coerceIn(0f, 1f)
                        _recordedDurationMs.value = System.currentTimeMillis() - startTime
                    }
                }
            }

            onSuccess()
        } catch (e: Exception) {
            onError(e.message ?: "Failed to record audio")
        }
    }

    fun stopRecording(): ShortArray? {
        return stopRecordingWithDetails()?.studioPcm
    }

    fun stopRecordingWithDetails(): RecordedAudioResult? {
        if (!_isRecording.value) return null
        _isRecording.value = false
        recordJob?.cancel()
        recordJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        _amplitude.value = 0f

        val bytes = recordedByteStream.toByteArray()
        if (bytes.isEmpty()) return null

        val shortCount = bytes.size / 2
        val shortBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val rawHardwarePcm = ShortArray(shortCount)
        shortBuffer.get(rawHardwarePcm)

        val studioPcm = if (activeHardwareSampleRateHz != StudioAudioProcessor.SAMPLE_RATE) {
            resampleLinear(rawHardwarePcm, activeHardwareSampleRateHz, StudioAudioProcessor.SAMPLE_RATE)
        } else {
            rawHardwarePcm
        }

        return RecordedAudioResult(
            studioPcm = studioPcm,
            rawHardwarePcm = rawHardwarePcm,
            hardwareSampleRateHz = activeHardwareSampleRateHz,
            channelCount = activeChannelCount,
            resolvedConfig = _resolvedConfig.value
        )
    }

    companion object {
        fun resampleLinear(input: ShortArray, fromRate: Int, toRate: Int): ShortArray {
            if (input.isEmpty() || fromRate <= 0 || toRate <= 0 || fromRate == toRate) return input
            val targetLen = ((input.size.toDouble() * toRate.toDouble()) / fromRate.toDouble())
                .toInt()
                .coerceAtLeast(1)
            val output = ShortArray(targetLen)
            val ratio = (input.size - 1).toDouble() / (targetLen - 1).coerceAtLeast(1).toDouble()
            for (i in 0 until targetLen) {
                val pos = i * ratio
                val idx0 = pos.toInt().coerceIn(0, input.lastIndex)
                val idx1 = (idx0 + 1).coerceAtMost(input.lastIndex)
                val frac = (pos - idx0).toFloat()
                val interpolated = input[idx0] * (1f - frac) + input[idx1] * frac
                output[i] = interpolated.toInt().coerceIn(-32767, 32767).toShort()
            }
            return output
        }
    }
}
