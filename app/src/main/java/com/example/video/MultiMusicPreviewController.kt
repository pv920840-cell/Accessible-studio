package com.example.video

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * Pure helper math & synchronized MediaPlayer controller for Multi-Music playback preview
 * (both individual track auditioning and synced playback alongside video + Smart Ducking).
 */
class MultiMusicPreviewController(private val context: Context) {

    private data class TrackPlayerSlot(
        val uri: Uri,
        var player: MediaPlayer? = null,
        var isPrepared: Boolean = false,
        var durationMs: Long = 0L
    )

    private val timelineSlots = mutableMapOf<String, TrackPlayerSlot>()
    private var soloPlayer: MediaPlayer? = null

    private val _previewingTrackId = MutableStateFlow<String?>(null)
    val previewingTrackId: StateFlow<String?> = _previewingTrackId.asStateFlow()

    private var smoothedDuckFactor: Float = 1.0f

    companion object {
        const val DUCKED_GAIN_MULTIPLIER = 0.25f
        const val FULL_GAIN_MULTIPLIER = 1.0f

        /**
         * Resolves the playback position inside a background music track for a given video timestamp.
         * Returns null when the track should not be playing (before startMs or past end with loop = false).
         */
        fun computeTrackPreviewPosition(
            videoPositionMs: Long,
            trackStartMs: Long,
            trackDurationMs: Long,
            loop: Boolean
        ): Long? {
            if (videoPositionMs < trackStartMs) return null
            val relMs = (videoPositionMs - trackStartMs).coerceAtLeast(0L)
            if (trackDurationMs <= 0L) return relMs
            return if (loop) {
                relMs % trackDurationMs
            } else {
                if (relMs < trackDurationMs) relMs else null
            }
        }

        /**
         * Computes effective stereo preview volume with Smart Ducking.
         */
        fun computeEffectivePreviewGain(
            trackVolume: Float,
            smartDucking: Boolean,
            isSpeechActive: Boolean
        ): Float {
            val duck = if (smartDucking && isSpeechActive) DUCKED_GAIN_MULTIPLIER else FULL_GAIN_MULTIPLIER
            return (trackVolume * duck).coerceIn(0f, 1f)
        }

        /**
         * Determines whether voice/narration speech is active at [videoPositionMs] for live preview ducking.
         */
        fun isSpeechActiveAt(
            videoPositionMs: Long,
            narrations: List<NarrationSegment>,
            audioOption: VideoAudioOption,
            baseAudioActiveAtPlayhead: Boolean
        ): Boolean {
            val narrationPlaying = narrations.any { seg ->
                val endMs = seg.startMs + seg.durationMs.coerceAtLeast(500L)
                videoPositionMs in seg.startMs..endMs
            }
            if (narrationPlaying) return true
            return audioOption != VideoAudioOption.MUTE && baseAudioActiveAtPlayhead
        }

        /**
         * Safely reads a picked audio track's display name and duration (ms).
         */
        fun inspectMusicTrackInfo(
            context: Context,
            uri: Uri,
            fallbackTitle: String
        ): Pair<String, Long> {
            var resolvedTitle: String? = null
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) resolvedTitle = c.getString(idx)
                    }
                }
            } catch (_: Throwable) {
            }
            if (resolvedTitle.isNullOrBlank()) {
                resolvedTitle = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
            }
            val finalTitle = resolvedTitle?.takeIf { it.isNotBlank() } ?: fallbackTitle

            var durMs = 0L
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } catch (_: Throwable) {
            } finally {
                try {
                    retriever.release()
                } catch (_: Throwable) {
                }
            }
            return finalTitle to durMs.coerceAtLeast(0L)
        }

        /**
         * Precomputes 50ms voice-activity bins from a decoded video base PCM track
         * so live Smart Ducking responds accurately to original video dialogue during preview.
         */
        fun computeSpeechActivityBins(
            pcm: ShortArray,
            sampleRate: Int = 44100,
            binMs: Long = 50L
        ): BooleanArray {
            if (pcm.isEmpty()) return BooleanArray(0)
            val samplesPerBin = ((sampleRate * binMs) / 1000L).toInt().coerceAtLeast(1)
            val totalBins = (pcm.size + samplesPerBin - 1) / samplesPerBin
            val raw = BooleanArray(totalBins)
            for (b in 0 until totalBins) {
                val start = b * samplesPerBin
                val end = minOf(start + samplesPerBin, pcm.size)
                var peak = 0
                for (i in start until end) {
                    val v = abs(pcm[i].toInt())
                    if (v > peak) peak = v
                }
                raw[b] = peak > 1600
            }
            // Apply 150ms (3-bin) hangover smoothing for natural live ducking release
            val smoothed = BooleanArray(totalBins)
            var hold = 0
            for (b in 0 until totalBins) {
                if (raw[b]) {
                    hold = 3
                    smoothed[b] = true
                } else if (hold > 0) {
                    hold--
                    smoothed[b] = true
                }
            }
            return smoothed
        }

        fun isBinActive(bins: BooleanArray, positionMs: Long, binMs: Long = 50L): Boolean {
            if (bins.isEmpty() || positionMs < 0L) return false
            val idx = (positionMs / binMs).toInt()
            return idx in bins.indices && bins[idx]
        }
    }

    /**
     * Toggles direct auditioning of a single added music track right inside Add Multi Music.
     */
    fun toggleSoloPreview(track: MusicLayerTrack) {
        if (_previewingTrackId.value == track.id) {
            stopSoloPreview()
            return
        }
        pauseTimelineTracks()
        stopSoloPreview()

        _previewingTrackId.value = track.id
        try {
            val vol = track.volume.coerceIn(0f, 1f)
            val mp = MediaPlayer().apply {
                setDataSource(context, track.uri)
                isLooping = track.loop
                setVolume(vol, vol)
                setOnPreparedListener { readyMp ->
                    if (_previewingTrackId.value == track.id) {
                        try {
                            readyMp.isLooping = track.loop
                            readyMp.setVolume(vol, vol)
                            readyMp.start()
                        } catch (_: Throwable) {
                        }
                    }
                }
                setOnCompletionListener {
                    if (!track.loop && _previewingTrackId.value == track.id) {
                        _previewingTrackId.value = null
                    }
                }
                setOnErrorListener { _, _, _ ->
                    if (_previewingTrackId.value == track.id) {
                        _previewingTrackId.value = null
                    }
                    true
                }
                prepareAsync()
            }
            soloPlayer = mp
        } catch (_: Throwable) {
            // Keep preview state safe if platform MediaPlayer isn't backed (e.g., Robolectric fake URIs)
        }
    }

    fun stopSoloPreview() {
        _previewingTrackId.value = null
        try {
            soloPlayer?.stop()
        } catch (_: Throwable) {
        }
        try {
            soloPlayer?.release()
        } catch (_: Throwable) {
        }
        soloPlayer = null
    }

    /**
     * Updates solo preview player volume/loop immediately if user edits sliders/switches while previewing.
     */
    fun refreshTrackSettings(tracks: List<MusicLayerTrack>) {
        val currentSoloId = _previewingTrackId.value
        if (currentSoloId != null) {
            val active = tracks.firstOrNull { it.id == currentSoloId }
            if (active == null) {
                stopSoloPreview()
            } else {
                try {
                    val vol = active.volume.coerceIn(0f, 1f)
                    soloPlayer?.isLooping = active.loop
                    soloPlayer?.setVolume(vol, vol)
                } catch (_: Throwable) {
                }
            }
        }
    }

    private var lastSyncedPlaying: Boolean = false
    private var lastSyncedPosMs: Long = 0L
    private var lastSyncedTracks: Map<String, MusicLayerTrack> = emptyMap()

    /**
     * Synchronizes all layered music tracks with the video playhead, Repeat Loop settings,
     * and live Smart Ducking.
     */
    fun syncTimelinePlayback(
        isVideoPlaying: Boolean,
        videoPositionMs: Long,
        musicTracks: List<MusicLayerTrack>,
        smartDucking: Boolean,
        narrations: List<NarrationSegment>,
        audioOption: VideoAudioOption,
        baseAudioActiveAtPlayhead: Boolean = false
    ) {
        lastSyncedPlaying = isVideoPlaying
        lastSyncedPosMs = videoPositionMs
        lastSyncedTracks = musicTracks.associateBy { it.id }

        // Release players for removed tracks
        val activeIds = musicTracks.map { it.id }.toSet()
        val staleIds = timelineSlots.keys.filter { it !in activeIds }
        for (stale in staleIds) {
            releaseTimelineSlot(stale)
        }

        if (isVideoPlaying && musicTracks.isNotEmpty() && _previewingTrackId.value != null) {
            stopSoloPreview()
        }

        val speechActive = isSpeechActiveAt(
            videoPositionMs = videoPositionMs,
            narrations = narrations,
            audioOption = audioOption,
            baseAudioActiveAtPlayhead = baseAudioActiveAtPlayhead
        )
        val targetDuck = if (smartDucking && speechActive) DUCKED_GAIN_MULTIPLIER else FULL_GAIN_MULTIPLIER
        val alpha = if (targetDuck < smoothedDuckFactor) 0.45f else 0.18f
        smoothedDuckFactor += (targetDuck - smoothedDuckFactor) * alpha

        for (track in musicTracks) {
            val slot = getOrCreateTimelineSlot(track)
            val mp = slot.player ?: continue

            val effectiveGain = (track.volume * smoothedDuckFactor).coerceIn(0f, 1f)
            try {
                mp.isLooping = track.loop
                mp.setVolume(effectiveGain, effectiveGain)
            } catch (_: Throwable) {
            }

            if (!slot.isPrepared) continue

            if (!isVideoPlaying) {
                try {
                    if (mp.isPlaying) mp.pause()
                } catch (_: Throwable) {
                }
                continue
            }

            val knownDur = slot.durationMs.takeIf { it > 0L } ?: track.durationMs
            val targetPos = computeTrackPreviewPosition(
                videoPositionMs = videoPositionMs,
                trackStartMs = track.startMs,
                trackDurationMs = knownDur,
                loop = track.loop
            )
            if (targetPos == null) {
                try {
                    if (mp.isPlaying) mp.pause()
                } catch (_: Throwable) {
                }
            } else {
                try {
                    val curPos = mp.currentPosition.toLong()
                    val diff = if (track.loop && knownDur > 0L) {
                        val direct = abs(curPos - targetPos)
                        minOf(direct, abs(knownDur - direct))
                    } else {
                        abs(curPos - targetPos)
                    }
                    if (diff > 550L) {
                        mp.seekTo(targetPos.toInt())
                    }
                    if (!mp.isPlaying) {
                        mp.start()
                    }
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun pauseTimelineTracks() {
        lastSyncedPlaying = false
        for (slot in timelineSlots.values) {
            try {
                if (slot.player?.isPlaying == true) slot.player?.pause()
            } catch (_: Throwable) {
            }
        }
    }

    private fun getOrCreateTimelineSlot(track: MusicLayerTrack): TrackPlayerSlot {
        val existing = timelineSlots[track.id]
        if (existing != null && existing.uri == track.uri && existing.player != null) {
            return existing
        }
        releaseTimelineSlot(track.id)

        val slot = TrackPlayerSlot(uri = track.uri, durationMs = track.durationMs)
        timelineSlots[track.id] = slot
        try {
            val mp = MediaPlayer().apply {
                setDataSource(context, track.uri)
                isLooping = track.loop
                setOnPreparedListener { readyMp ->
                    slot.isPrepared = true
                    val dur = try { readyMp.duration.toLong() } catch (_: Throwable) { 0L }
                    if (dur > 0L) slot.durationMs = dur
                    val latestTrack = lastSyncedTracks[track.id] ?: track
                    val vol = (latestTrack.volume * smoothedDuckFactor).coerceIn(0f, 1f)
                    try {
                        readyMp.isLooping = latestTrack.loop
                        readyMp.setVolume(vol, vol)
                        if (lastSyncedPlaying) {
                            val targetPos = computeTrackPreviewPosition(
                                videoPositionMs = lastSyncedPosMs,
                                trackStartMs = latestTrack.startMs,
                                trackDurationMs = slot.durationMs,
                                loop = latestTrack.loop
                            )
                            if (targetPos != null) {
                                readyMp.seekTo(targetPos.toInt())
                                readyMp.start()
                            }
                        }
                    } catch (_: Throwable) {
                    }
                }
                setOnErrorListener { _, _, _ -> true }
                prepareAsync()
            }
            slot.player = mp
        } catch (_: Throwable) {
        }
        return slot
    }

    private fun releaseTimelineSlot(trackId: String) {
        val slot = timelineSlots.remove(trackId) ?: return
        try {
            slot.player?.stop()
        } catch (_: Throwable) {
        }
        try {
            slot.player?.release()
        } catch (_: Throwable) {
        }
    }

    fun stopAll() {
        stopSoloPreview()
        for (id in timelineSlots.keys.toList()) {
            releaseTimelineSlot(id)
        }
    }
}
