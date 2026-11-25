package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

enum class ScrubDirection {
    NONE,
    FORWARD,
    REWIND
}

class AudioPlayerManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main)
    private val trackLock = Any()
    private var mediaPlayer: MediaPlayer? = null
    @Volatile
    private var customAudioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private var previewPulseJob: Job? = null
    private var pcmData: ShortArray? = null
    private val playbackGeneration = AtomicLong(0L)
    private var activeAudioFocusRequest: AudioFocusRequest? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(10000L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _playbackRate = MutableStateFlow(1.0f)
    val playbackRate: StateFlow<Float> = _playbackRate.asStateFlow()

    private val _volumeLevel = MutableStateFlow(1.0f)
    val volumeLevel: StateFlow<Float> = _volumeLevel.asStateFlow()

    private val _sampleRate = MutableStateFlow(44100)
    val sampleRate: StateFlow<Int> = _sampleRate.asStateFlow()

    private val _scrubDirection = MutableStateFlow(ScrubDirection.NONE)
    val scrubDirection: StateFlow<ScrubDirection> = _scrubDirection.asStateFlow()

    @Volatile
    var scrubSpeedMultiplier: Float = 3.5f

    // Real-time Adaptive Spectral De-Noise Live Preview state (zero-latency toggle & parameter updates)
    @Volatile
    private var realtimeDenoiseEnabled: Boolean = false

    @Volatile
    private var realtimeDenoiseParams: SpectralDenoiseParams = SpectralDenoiseParams()

    @Volatile
    private var realtimeNoiseProfile: SpectralNoiseProfile? = null

    private var realtimeDenoiseProcessor: RealtimeSpectralDenoiseProcessor? = null
    private val realtimeProcessorLock = Any()

    fun setRealtimeDenoiseEnabled(enabled: Boolean) {
        realtimeDenoiseEnabled = enabled
    }

    fun setRealtimeDenoiseParams(params: SpectralDenoiseParams) {
        val clean = params.clamped()
        realtimeDenoiseParams = clean
        synchronized(realtimeProcessorLock) {
            val proc = realtimeDenoiseProcessor
            if (proc != null) {
                if (proc.n != clean.fftSize) {
                    val sr = _sampleRate.value.coerceIn(8000, 96000)
                    realtimeDenoiseProcessor = RealtimeSpectralDenoiseProcessor(sr, clean.fftSize).apply {
                        this.params = clean
                        setNoiseProfile(realtimeNoiseProfile)
                    }
                } else {
                    proc.params = clean
                }
            }
        }
    }

    fun setRealtimeNoiseProfile(profile: SpectralNoiseProfile?) {
        realtimeNoiseProfile = profile
        synchronized(realtimeProcessorLock) {
            realtimeDenoiseProcessor?.setNoiseProfile(profile)
        }
    }

    private fun getOrCreateRealtimeProcessor(sr: Int): RealtimeSpectralDenoiseProcessor {
        val targetFft = realtimeDenoiseParams.fftSize
        synchronized(realtimeProcessorLock) {
            val existing = realtimeDenoiseProcessor
            if (existing != null && existing.n == targetFft) {
                existing.params = realtimeDenoiseParams
                return existing
            }
            val created = RealtimeSpectralDenoiseProcessor(sr, targetFft).apply {
                params = realtimeDenoiseParams
                setNoiseProfile(realtimeNoiseProfile)
            }
            realtimeDenoiseProcessor = created
            return created
        }
    }

    companion object {
        internal fun resolvePlayStartMs(
            requestedStartMs: Long?,
            endLimitMs: Long?,
            currentPositionMs: Long,
            durationMs: Long
        ): Long {
            val safeDur = durationMs.coerceAtLeast(0L)
            if (safeDur <= 0L) return 0L
            val candidate = requestedStartMs ?: currentPositionMs
            if (endLimitMs == null && candidate >= (safeDur - 40L).coerceAtLeast(0L)) {
                return 0L
            }
            if (endLimitMs != null && candidate >= endLimitMs) {
                return 0L
            }
            return candidate.coerceIn(0L, safeDur)
        }
    }

    fun hasPcmData(): Boolean = pcmData?.isNotEmpty() == true

    fun hasLoadedAudio(): Boolean = hasPcmData() || mediaPlayer != null

    fun setPlaybackRate(rate: Float) {
        val safeRate = rate.coerceIn(0.25f, 3.0f)
        _playbackRate.value = safeRate
        try {
            customAudioTrack?.playbackRate = (_sampleRate.value * safeRate).toInt().coerceIn(8000, 192000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        mp.playbackParams = mp.playbackParams.setSpeed(safeRate)
                    }
                }
            }
        } catch (_: Throwable) {}
    }

    fun setVolumeLevel(volume: Float) {
        val safeVol = volume.coerceIn(0f, 1.0f)
        _volumeLevel.value = safeVol
        try {
            customAudioTrack?.setVolume(safeVol)
            mediaPlayer?.setVolume(safeVol, safeVol)
        } catch (_: Throwable) {}
    }

    fun setWorkingSampleRate(rate: Int) {
        val safeSr = rate.coerceIn(8000, 96000)
        _sampleRate.value = safeSr
        val pcm = pcmData
        if (pcm != null && pcm.isNotEmpty()) {
            val dur = ((pcm.size.toDouble() / safeSr) * 1000.0).toLong()
            _durationMs.value = dur
        }
    }

    /**
     * Starts continuous audible Forward or Reverse Rewind scrubbing without stopping audio.
     */
    fun startScrub(direction: ScrubDirection) {
        if (direction == ScrubDirection.NONE) {
            stopScrub()
            return
        }
        val pcm = pcmData
        if ((pcm == null || pcm.isEmpty()) && mediaPlayer == null) {
            stopScrub()
            return
        }
        _scrubDirection.value = direction
        if (!_isPlaying.value) {
            val startPos = if (direction == ScrubDirection.REWIND && _currentPositionMs.value <= 50L) {
                (_durationMs.value - 500L).coerceAtLeast(0L)
            } else {
                _currentPositionMs.value.coerceIn(0L, _durationMs.value.coerceAtLeast(0L))
            }
            _currentPositionMs.value = startPos
            play(startFromMs = startPos, endLimitMs = null)
        }
    }

    /**
     * Releases scrub back to seamless 1x forward playback (audio keeps playing!).
     */
    fun stopScrub() {
        _scrubDirection.value = ScrubDirection.NONE
    }

    fun setScrubSpeed(speed: Float) {
        scrubSpeedMultiplier = speed.coerceIn(0.25f, 8.0f)
    }

    /**
     * Toggles continuous audible Forward or Reverse Rewind scrubbing (for tap & TalkBack).
     */
    fun toggleScrub(direction: ScrubDirection) {
        if (_scrubDirection.value == direction && _isPlaying.value) {
            _scrubDirection.value = ScrubDirection.NONE
        } else {
            startScrub(direction)
        }
    }

    fun loadPcmData(samples: ShortArray, sampleRate: Int = _sampleRate.value) {
        stop()
        pcmData = samples
        val safeSr = sampleRate.coerceIn(8000, 96000)
        _sampleRate.value = safeSr
        val dur = ((samples.size.toDouble() / safeSr) * 1000.0).toLong()
        _durationMs.value = dur
        _currentPositionMs.value = 0L
    }

    fun loadUri(uri: Uri) {
        stop()
        pcmData = null
        val gen = playbackGeneration.incrementAndGet()
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(context, uri)
                prepare()
                setVolume(_volumeLevel.value, _volumeLevel.value)
                _durationMs.value = duration.toLong().coerceAtLeast(0L)
                _currentPositionMs.value = 0L
                setOnCompletionListener {
                    if (playbackGeneration.get() == gen) {
                        _isPlaying.value = false
                        _scrubDirection.value = ScrubDirection.NONE
                        _currentPositionMs.value = 0L
                    }
                }
                setOnErrorListener { _, _, _ ->
                    if (playbackGeneration.get() == gen) {
                        _isPlaying.value = false
                        _scrubDirection.value = ScrubDirection.NONE
                    }
                    true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            mediaPlayer = null
        }
    }

    @Volatile
    private var seekRequestSample: Int? = null

    fun play(startFromMs: Long? = null, endLimitMs: Long? = null, loopSegment: Boolean = false) {
        val pcm = pcmData
        if (pcm != null && pcm.isNotEmpty()) {
            val durMs = ((pcm.size.toDouble() / _sampleRate.value.coerceIn(8000, 96000)) * 1000.0).toLong()
            _durationMs.value = durMs
            val effectiveStartMs = if (_scrubDirection.value == ScrubDirection.REWIND) {
                (startFromMs ?: _currentPositionMs.value).coerceIn(0L, durMs)
            } else {
                val resolved = resolvePlayStartMs(startFromMs, endLimitMs, _currentPositionMs.value, durMs)
                if (startFromMs == null && resolved == 0L && _currentPositionMs.value >= (durMs - 40L).coerceAtLeast(0L)) {
                    _currentPositionMs.value = 0L
                }
                resolved
            }
            playPcm(pcm, effectiveStartMs, endLimitMs, loopSegment)
        } else if (mediaPlayer != null) {
            val durMs = _durationMs.value.coerceAtLeast(0L)
            val resolved = resolvePlayStartMs(startFromMs, endLimitMs, _currentPositionMs.value, durMs)
            playMediaPlayer(resolved, endLimitMs)
        }
    }

    private fun requestPlaybackAudioFocus() {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = activeAudioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAcceptsDelayedFocusGain(true)
                    .build()
                    .also { activeAudioFocusRequest = it }
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }
        } catch (_: Throwable) {}
    }

    private fun abandonPlaybackAudioFocus() {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activeAudioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        } catch (_: Throwable) {}
    }

    private fun buildInitializedAudioTrack(sr: Int): AudioTrack {
        val minBufSize = AudioTrack.getMinBufferSize(
            sr,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val rawBufSize = if (minBufSize > 0) (minBufSize * 3).coerceAtLeast(8192) else 8192
        val streamBufferSize = if (rawBufSize % 2 != 0) rawBufSize + 1 else rawBufSize

        val primary = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sr)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(streamBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        if (primary.state == AudioTrack.STATE_INITIALIZED) {
            return primary
        }
        try { primary.release() } catch (_: Throwable) {}

        val fallbackBuf = streamBufferSize.coerceAtLeast(16384)
        val fallback = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sr)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(fallbackBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        if (fallback.state != AudioTrack.STATE_INITIALIZED) {
            try { fallback.release() } catch (_: Throwable) {}
            throw IllegalStateException("AudioTrack failed to initialize (sampleRate=$sr)")
        }
        return fallback
    }

    private fun playPcm(samples: ShortArray, startMs: Long, endLimitMs: Long?, loopSegment: Boolean) {
        val myGen = playbackGeneration.incrementAndGet()
        playbackJob?.cancel()
        playbackJob = null
        seekRequestSample = null
        stopAudioTrack()

        if (samples.isEmpty()) {
            _isPlaying.value = false
            _scrubDirection.value = ScrubDirection.NONE
            return
        }
        val sr = _sampleRate.value.coerceIn(8000, 96000)
        var startSample = ((startMs.toDouble() / 1000.0) * sr).toInt().coerceIn(0, samples.size)
        var endSample = if (endLimitMs != null) {
            ((endLimitMs.toDouble() / 1000.0) * sr).toInt().coerceIn(startSample, samples.size)
        } else {
            samples.size
        }

        if (endSample - startSample <= 0 && _scrubDirection.value != ScrubDirection.REWIND) {
            startSample = 0
            endSample = samples.size
            _currentPositionMs.value = 0L
        }

        requestPlaybackAudioFocus()

        val track: AudioTrack
        try {
            track = buildInitializedAudioTrack(sr)
            track.setVolume(_volumeLevel.value.coerceIn(0f, 1.0f))
            try {
                track.playbackRate = (sr * _playbackRate.value).toInt().coerceIn(8000, 192000)
            } catch (_: Throwable) {}
            customAudioTrack = track
            track.play()
            _isPlaying.value = true
        } catch (e: Throwable) {
            e.printStackTrace()
            _isPlaying.value = false
            _scrubDirection.value = ScrubDirection.NONE
            return
        }

        playbackJob = scope.launch(Dispatchers.Default) {
            val normalChunkSize = 1024
            val processedChunkBuf = ShortArray(normalChunkSize)
            val scrubChunkSize = 1024 // ~23ms low-latency grain for instant scrub response
            val scrubBuf = ShortArray(scrubChunkSize)
            var currentIdx = startSample
            var scrubSamplePos = startSample.toDouble()
            var scrubActivated = false
            var lastEmittedPosMs = ((startSample.toDouble() / sr) * 1000.0).toLong()
            val minEmitDeltaMs = 120L

            synchronized(realtimeProcessorLock) {
                realtimeDenoiseProcessor?.reset()
            }

            while (isActive && _isPlaying.value && playbackGeneration.get() == myGen) {
                while (isActive && _isPlaying.value && playbackGeneration.get() == myGen) {
                    val pending = seekRequestSample
                    if (pending != null) {
                        seekRequestSample = null
                        currentIdx = pending.coerceIn(0, samples.size)
                        scrubSamplePos = currentIdx.toDouble()
                        synchronized(realtimeProcessorLock) {
                            realtimeDenoiseProcessor?.reset()
                        }
                        try {
                            track.pause()
                            track.flush()
                            track.play()
                        } catch (_: Throwable) {}
                        val seekPosMs = ((currentIdx.toDouble() / sr) * 1000.0).toLong()
                        lastEmittedPosMs = seekPosMs
                        _currentPositionMs.value = seekPosMs
                    }

                    val activeScrub = _scrubDirection.value
                    if (activeScrub != ScrubDirection.NONE) {
                        scrubActivated = true
                    }
                    when (activeScrub) {
                        ScrubDirection.NONE -> {
                            val activeLimit = if (scrubActivated) samples.size else endSample
                            if (currentIdx >= activeLimit) break
                            val writeCount = Math.min(normalChunkSize, activeLimit - currentIdx)

                            val useRealtimeDenoise = realtimeDenoiseEnabled
                            val srcBuffer: ShortArray
                            val srcOffset: Int
                            if (useRealtimeDenoise) {
                                val proc = getOrCreateRealtimeProcessor(sr)
                                proc.processChunk(samples, currentIdx, writeCount, processedChunkBuf)
                                srcBuffer = processedChunkBuf
                                srcOffset = 0
                            } else {
                                srcBuffer = samples
                                srcOffset = currentIdx
                            }

                            val written = synchronized(trackLock) {
                                if (customAudioTrack === track && playbackGeneration.get() == myGen) {
                                    try {
                                        track.write(srcBuffer, srcOffset, writeCount)
                                    } catch (_: Throwable) {
                                        -1
                                    }
                                } else {
                                    -1
                                }
                            }
                            if (written > 0 && playbackGeneration.get() == myGen) {
                                currentIdx += written
                                scrubSamplePos = currentIdx.toDouble()
                                val posMs = ((currentIdx.toDouble() / sr) * 1000.0).toLong()
                                if (kotlin.math.abs(posMs - lastEmittedPosMs) >= minEmitDeltaMs || currentIdx >= activeLimit) {
                                    lastEmittedPosMs = posMs
                                    _currentPositionMs.value = posMs
                                }
                            } else {
                                break
                            }
                        }
                        ScrubDirection.FORWARD -> {
                            if (currentIdx >= samples.size - 1) {
                                _currentPositionMs.value = _durationMs.value
                                _scrubDirection.value = ScrubDirection.NONE
                                break
                            }
                            val speed = scrubSpeedMultiplier.coerceIn(0.25f, 8.0f)
                            val pitchStep = if (speed < 1.0f) speed else 1.45f
                            val fadeLen = 32
                            val maxSampleIdx = (samples.size - 1).toDouble()
                            for (k in 0 until scrubChunkSize) {
                                val exactPos = (scrubSamplePos + k * pitchStep).coerceIn(0.0, maxSampleIdx)
                                val idx0 = exactPos.toInt().coerceIn(0, samples.size - 1)
                                val idx1 = (idx0 + 1).coerceAtMost(samples.size - 1)
                                val frac = (exactPos - idx0).toFloat()
                                val interp = samples[idx0] * (1f - frac) + samples[idx1] * frac
                                val edge = when {
                                    k < fadeLen -> k.toFloat() / fadeLen
                                    scrubChunkSize - k < fadeLen -> (scrubChunkSize - k).toFloat() / fadeLen
                                    else -> 1f
                                }
                                scrubBuf[k] = (interp * edge).toInt().coerceIn(-32767, 32767).toShort()
                            }
                            val written = synchronized(trackLock) {
                                if (customAudioTrack === track && playbackGeneration.get() == myGen) {
                                    try {
                                        track.write(scrubBuf, 0, scrubChunkSize)
                                    } catch (_: Throwable) {
                                        -1
                                    }
                                } else {
                                    -1
                                }
                            }
                            if (written > 0 && playbackGeneration.get() == myGen) {
                                scrubSamplePos = (scrubSamplePos + scrubChunkSize * speed.toDouble()).coerceAtMost(samples.size.toDouble())
                                currentIdx = scrubSamplePos.toInt().coerceIn(0, samples.size)
                                val posMs = ((scrubSamplePos / sr) * 1000.0).toLong()
                                if (kotlin.math.abs(posMs - lastEmittedPosMs) >= minEmitDeltaMs || currentIdx >= samples.size) {
                                    lastEmittedPosMs = posMs
                                    _currentPositionMs.value = posMs
                                }
                            } else {
                                break
                            }
                        }
                        ScrubDirection.REWIND -> {
                            val speed = scrubSpeedMultiplier.coerceIn(0.25f, 8.0f)
                            if (currentIdx <= 0 && scrubSamplePos <= 0.5) {
                                scrubSamplePos = 0.0
                                currentIdx = 0
                                if (lastEmittedPosMs != 0L) {
                                    lastEmittedPosMs = 0L
                                    _currentPositionMs.value = 0L
                                }
                                scrubBuf.fill(0)
                                val written = synchronized(trackLock) {
                                    if (customAudioTrack === track && playbackGeneration.get() == myGen) {
                                        try {
                                            track.write(scrubBuf, 0, scrubChunkSize)
                                        } catch (_: Throwable) {
                                            -1
                                        }
                                    } else {
                                        -1
                                    }
                                }
                                if (written <= 0) break
                                continue
                            }
                            val pitchStep = if (speed < 1.0f) speed else 1.45f
                            val fadeLen = 32
                            val maxSampleIdx = (samples.size - 1).toDouble()
                            for (k in 0 until scrubChunkSize) {
                                val exactPos = (scrubSamplePos - k * pitchStep).coerceIn(0.0, maxSampleIdx)
                                val idx0 = exactPos.toInt().coerceIn(0, samples.size - 1)
                                val idx1 = (idx0 + 1).coerceAtMost(samples.size - 1)
                                val frac = (exactPos - idx0).toFloat()
                                val interp = samples[idx0] * (1f - frac) + samples[idx1] * frac
                                val edge = when {
                                    k < fadeLen -> k.toFloat() / fadeLen
                                    scrubChunkSize - k < fadeLen -> (scrubChunkSize - k).toFloat() / fadeLen
                                    else -> 1f
                                }
                                scrubBuf[k] = (interp * edge).toInt().coerceIn(-32767, 32767).toShort()
                            }
                            val written = synchronized(trackLock) {
                                if (customAudioTrack === track && playbackGeneration.get() == myGen) {
                                    try {
                                        track.write(scrubBuf, 0, scrubChunkSize)
                                    } catch (_: Throwable) {
                                        -1
                                    }
                                } else {
                                    -1
                                }
                            }
                            if (written > 0 && playbackGeneration.get() == myGen) {
                                scrubSamplePos = (scrubSamplePos - scrubChunkSize * speed.toDouble()).coerceAtLeast(0.0)
                                currentIdx = scrubSamplePos.toInt().coerceIn(0, samples.size)
                                val posMs = ((scrubSamplePos / sr) * 1000.0).toLong()
                                if (kotlin.math.abs(posMs - lastEmittedPosMs) >= minEmitDeltaMs || currentIdx <= 0) {
                                    lastEmittedPosMs = posMs
                                    _currentPositionMs.value = posMs
                                }
                            } else {
                                break
                            }
                        }
                    }
                }

                if (loopSegment && isActive && _isPlaying.value && _scrubDirection.value == ScrubDirection.NONE && playbackGeneration.get() == myGen) {
                    currentIdx = startSample
                    scrubSamplePos = startSample.toDouble()
                    lastEmittedPosMs = startMs
                    _currentPositionMs.value = startMs
                } else {
                    break
                }
            }

            if (isActive && playbackGeneration.get() == myGen) {
                launch(Dispatchers.Main) {
                    if (playbackGeneration.get() == myGen) {
                        _isPlaying.value = false
                        _scrubDirection.value = ScrubDirection.NONE
                        if (currentIdx >= endSample && !scrubActivated) {
                            _currentPositionMs.value = if (endLimitMs != null) startMs else 0L
                        } else if (currentIdx >= samples.size - 1) {
                            _currentPositionMs.value = 0L
                        }
                    }
                }
            }
        }
    }

    private fun playMediaPlayer(startMs: Long, endLimitMs: Long?) {
        val mp = mediaPlayer ?: return
        val myGen = playbackGeneration.incrementAndGet()
        playbackJob?.cancel()
        playbackJob = null

        val maxDur = mp.duration.toLong().coerceAtLeast(0L)
        val safeStart = if (startMs >= (maxDur - 40L).coerceAtLeast(0L)) 0L else startMs.coerceIn(0L, maxDur)
        try {
            mp.seekTo(safeStart.toInt())
            mp.setVolume(_volumeLevel.value, _volumeLevel.value)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    mp.playbackParams = mp.playbackParams.setSpeed(_playbackRate.value)
                } catch (_: Throwable) {}
            }
            requestPlaybackAudioFocus()
            mp.start()
            _isPlaying.value = true
        } catch (e: Throwable) {
            e.printStackTrace()
            _isPlaying.value = false
            return
        }

        playbackJob = scope.launch {
            delay(80L)
            var lastAppliedScrub = ScrubDirection.NONE
            var lastAppliedForwardSpeed = scrubSpeedMultiplier
            var lastEmittedPosMs = safeStart
            while (isActive && playbackGeneration.get() == myGen && (mp.isPlaying || _scrubDirection.value != ScrubDirection.NONE)) {
                val activeScrub = _scrubDirection.value
                val targetForwardSpeed = scrubSpeedMultiplier.coerceIn(0.25f, 4.0f)
                if (activeScrub != lastAppliedScrub || (activeScrub == ScrubDirection.FORWARD && lastAppliedForwardSpeed != targetForwardSpeed)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try {
                            val targetSpeed = if (activeScrub == ScrubDirection.FORWARD) {
                                targetForwardSpeed
                            } else {
                                _playbackRate.value
                            }
                            mp.playbackParams = mp.playbackParams.setSpeed(targetSpeed)
                        } catch (_: Throwable) {}
                    }
                    lastAppliedScrub = activeScrub
                    lastAppliedForwardSpeed = targetForwardSpeed
                }
                val stepMs = (180f * scrubSpeedMultiplier.coerceIn(0.25f, 8.0f)).toInt().coerceAtLeast(35)
                if (activeScrub == ScrubDirection.FORWARD && Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    val next = (mp.currentPosition + stepMs).coerceAtMost(mp.duration)
                    mp.seekTo(next)
                } else if (activeScrub == ScrubDirection.REWIND) {
                    val prev = (mp.currentPosition - stepMs).coerceAtLeast(0)
                    mp.seekTo(prev)
                }
                val current = mp.currentPosition.toLong()
                if (kotlin.math.abs(current - lastEmittedPosMs) >= 120L || activeScrub != ScrubDirection.NONE) {
                    lastEmittedPosMs = current
                    _currentPositionMs.value = current
                }
                if (endLimitMs != null && current >= endLimitMs && activeScrub == ScrubDirection.NONE && lastAppliedScrub == ScrubDirection.NONE) {
                    _currentPositionMs.value = current
                    try { mp.pause() } catch (_: Throwable) {}
                    break
                }
                delay(if (activeScrub == ScrubDirection.REWIND) 160L else 110L)
            }
            if (isActive && playbackGeneration.get() == myGen) {
                _isPlaying.value = false
                _scrubDirection.value = ScrubDirection.NONE
            }
        }
    }

    fun pause() {
        playbackGeneration.incrementAndGet()
        _scrubDirection.value = ScrubDirection.NONE
        _isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
        try {
            customAudioTrack?.pause()
            mediaPlayer?.pause()
        } catch (_: Throwable) {}
    }

    fun seekTo(positionMs: Long) {
        val safePos = positionMs.coerceIn(0L, _durationMs.value.coerceAtLeast(0L))
        _currentPositionMs.value = safePos
        val wasPlaying = _isPlaying.value
        val pcm = pcmData
        if (pcm != null && pcm.isNotEmpty()) {
            val sr = _sampleRate.value.coerceIn(8000, 96000)
            val targetSample = ((safePos.toDouble() / 1000.0) * sr).toInt().coerceIn(0, pcm.size)
            if (wasPlaying && playbackJob?.isActive == true && customAudioTrack != null) {
                seekRequestSample = targetSample
                try {
                    customAudioTrack?.pause()
                    customAudioTrack?.flush()
                    customAudioTrack?.play()
                } catch (_: Throwable) {}
            } else if (wasPlaying) {
                play(startFromMs = safePos)
            }
        } else if (mediaPlayer != null) {
            try {
                mediaPlayer?.seekTo(safePos.toInt())
            } catch (_: Throwable) {}
        }
    }

    /**
     * Seeks to [positionMs] and plays a brief preview snippet if paused,
     * keeping continuous playback if already playing.
     */
    fun scrubPreviewAt(positionMs: Long, previewDurationMs: Long = 220L) {
        val safePos = positionMs.coerceIn(0L, _durationMs.value.coerceAtLeast(0L))
        if (_isPlaying.value && previewPulseJob?.isActive != true) {
            seekTo(safePos)
            return
        }
        if (!hasLoadedAudio()) {
            _currentPositionMs.value = safePos
            return
        }
        previewPulseJob?.cancel()
        _currentPositionMs.value = safePos
        val endLimit = (safePos + previewDurationMs).coerceAtMost(_durationMs.value.coerceAtLeast(safePos))
        play(startFromMs = safePos, endLimitMs = endLimit)
        previewPulseJob = scope.launch {
            delay(previewDurationMs)
            pause()
            _currentPositionMs.value = safePos
        }
    }

    fun stop() {
        playbackGeneration.incrementAndGet()
        _scrubDirection.value = ScrubDirection.NONE
        _isPlaying.value = false
        seekRequestSample = null
        playbackJob?.cancel()
        playbackJob = null
        stopAudioTrack()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Throwable) {}
        mediaPlayer = null
        abandonPlaybackAudioFocus()
    }

    private fun stopAudioTrack() {
        synchronized(trackLock) {
            val track = customAudioTrack
            customAudioTrack = null
            if (track != null) {
                try { track.pause() } catch (_: Throwable) {}
                try { track.flush() } catch (_: Throwable) {}
                try { track.stop() } catch (_: Throwable) {}
                try { track.release() } catch (_: Throwable) {}
            }
        }
    }

    fun release() {
        stop()
    }
}
