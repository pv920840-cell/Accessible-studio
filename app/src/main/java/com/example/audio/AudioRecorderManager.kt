package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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

class AudioRecorderManager(private val context: Context) {

    val sampleRate = 44100
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

    // Configurable Mic Parameters
    var bufferSizeOption: Int = 2048 // 1024, 2048, 4096 (Default 2048)
    var autoGainEnabled: Boolean = true
    var micGainVolume: Float = 1.0f // 0.2f to 2.0f

    private val recordedByteStream = ByteArrayOutputStream()

    @SuppressLint("MissingPermission")
    fun startRecording(onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (_isRecording.value) return

        try {
            val minBufSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            if (minBufSize <= 0) {
                onError("Microphone format not supported on this device")
                return
            }

            // Ensure buffer size is at least minBufSize and user chosen frames
            val hardwareBufferSize = kotlin.math.max(minBufSize * 2, bufferSizeOption * 4)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                hardwareBufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                onError("Could not initialize microphone recorder")
                return
            }

            audioRecord?.startRecording()
            _isRecording.value = true
            _recordedDurationMs.value = 0L
            _isTooLoudWarning.value = false
            recordedByteStream.reset()

            recordJob = scope.launch {
                val chunkSize = bufferSizeOption.coerceAtLeast(512)
                val buffer = ShortArray(chunkSize)
                val byteBuffer = ByteBuffer.allocate(chunkSize * 2).order(ByteOrder.LITTLE_ENDIAN)
                val startTime = System.currentTimeMillis()

                var runningAgcGain = 1.0f

                while (isActive && _isRecording.value) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        byteBuffer.clear()
                        var maxAmp = 0

                        // 1. Calculate peak in current buffer
                        for (i in 0 until read) {
                            val a = kotlin.math.abs(buffer[i].toInt())
                            if (a > maxAmp) maxAmp = a
                        }

                        // 2. Auto Mic Gain (AGC): If signal is near clipping (> 28000), back off smoothly
                        if (autoGainEnabled) {
                            if (maxAmp > 28000) {
                                val target = 26000f / maxAmp.toFloat()
                                runningAgcGain = (runningAgcGain * 0.7f) + (target * 0.3f)
                                _isTooLoudWarning.value = true
                            } else if (maxAmp < 6000 && maxAmp > 300) {
                                // Gentle boost for quiet voice without blowing floor
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

                        // 3. Apply safe soft-limiting and write
                        for (i in 0 until read) {
                            var scaled = buffer[i] * effectiveGain
                            // Soft-knee limiter at ~ -1.0 dB (30000) to prevent crackle
                            if (kotlin.math.abs(scaled) > 30000f) {
                                val excess = kotlin.math.abs(scaled) - 30000f
                                val limited = 30000f + (kotlin.math.atan(excess / 2500f) * 1500f)
                                scaled = if (scaled > 0) limited else -limited
                            }
                            val safeShort = scaled.toInt().coerceIn(-32767, 32767).toShort()
                            byteBuffer.putShort(safeShort)
                        }

                        recordedByteStream.write(byteBuffer.array(), 0, read * 2)
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
        val shorts = ShortArray(shortCount)
        shortBuffer.get(shorts)
        return shorts
    }
}
