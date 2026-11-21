package com.example.video

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.view.SurfaceHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class VideoPlayerManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var mediaPlayer: MediaPlayer? = null
    private var pollJob: Job? = null
    private var surfaceHolder: SurfaceHolder? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(8000L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _isPreparedDuration = MutableStateFlow(false)
    val isPreparedDuration: StateFlow<Boolean> = _isPreparedDuration.asStateFlow()

    private val _videoAspectRatio = MutableStateFlow(1.0f)
    val videoAspectRatio: StateFlow<Float> = _videoAspectRatio.asStateFlow()

    fun attachSurface(holder: SurfaceHolder) {
        surfaceHolder = holder
        mediaPlayer?.setDisplay(holder)
    }

    fun loadUri(uri: Uri, onPrepared: () -> Unit = {}) {
        stop()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                surfaceHolder?.let { setDisplay(it) }
                setOnPreparedListener { mp ->
                    _durationMs.value = mp.duration.toLong().coerceAtLeast(1000L)
                    _isPreparedDuration.value = true
                    val w = mp.videoWidth
                    val h = mp.videoHeight
                    if (w > 0 && h > 0) {
                        _videoAspectRatio.value = w.toFloat() / h.toFloat()
                    }
                    onPrepared()
                }
                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentPositionMs.value = 0L
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun play(startMs: Long? = null, endLimitMs: Long? = null) {
        val mp = mediaPlayer ?: return
        if (startMs != null) {
            mp.seekTo(startMs.toInt())
            _currentPositionMs.value = startMs
        }
        mp.start()
        _isPlaying.value = true

        pollJob?.cancel()
        pollJob = scope.launch {
            var lastEmittedPosMs = _currentPositionMs.value
            while (isActive && mp.isPlaying) {
                val pos = mp.currentPosition.toLong()
                if (endLimitMs != null && pos >= endLimitMs) {
                    _currentPositionMs.value = pos
                    mp.pause()
                    _isPlaying.value = false
                    break
                }
                if (kotlin.math.abs(pos - lastEmittedPosMs) >= 120L) {
                    lastEmittedPosMs = pos
                    _currentPositionMs.value = pos
                }
                val nearEnd = endLimitMs != null && (endLimitMs - pos) in 1L..300L
                delay(if (nearEnd) 45L else 120L)
            }
            _isPlaying.value = false
        }
    }

    fun pause() {
        _isPlaying.value = false
        pollJob?.cancel()
        pollJob = null
        try {
            mediaPlayer?.pause()
        } catch (_: Exception) {}
    }

    fun seekTo(positionMs: Long) {
        val safe = positionMs.coerceIn(0L, _durationMs.value)
        _currentPositionMs.value = safe
        try {
            mediaPlayer?.seekTo(safe.toInt())
        } catch (_: Exception) {}
    }

    fun setMute(muted: Boolean) {
        val volume = if (muted) 0f else 1f
        try {
            mediaPlayer?.setVolume(volume, volume)
        } catch (_: Exception) {}
    }

    fun stop() {
        _isPlaying.value = false
        _isPreparedDuration.value = false
        pollJob?.cancel()
        pollJob = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    fun release() {
        stop()
        surfaceHolder = null
    }
}
