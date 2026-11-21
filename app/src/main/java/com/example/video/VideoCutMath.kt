package com.example.video

/**
 * Step sizes offered by the Video Cutter step toggle (defaults to 1 second).
 */
data class CutStepOption(
    val stepMs: Long,
    val shortLabel: String,
    val spokenLabel: String
)

/**
 * Pure deterministic math for Video Cutter marker clamping, step cycling,
 * overlapping cut-segment merging, deleted duration, output length, and keep ranges.
 */
object VideoCutMath {

    val STEP_OPTIONS: List<CutStepOption> = listOf(
        CutStepOption(stepMs = 1000L, shortLabel = "1 s", spokenLabel = "1 second"),
        CutStepOption(stepMs = 2000L, shortLabel = "2 s", spokenLabel = "2 seconds"),
        CutStepOption(stepMs = 5000L, shortLabel = "5 s", spokenLabel = "5 seconds"),
        CutStepOption(stepMs = 500L, shortLabel = "0.5 s", spokenLabel = "0.5 seconds")
    )

    const val DEFAULT_STEP_MS: Long = 1000L
    const val MIN_CUT_SPAN_MS: Long = 250L

    fun stepOptionFor(stepMs: Long): CutStepOption {
        return STEP_OPTIONS.firstOrNull { it.stepMs == stepMs } ?: STEP_OPTIONS.first()
    }

    fun nextStepOption(currentStepMs: Long): CutStepOption {
        val idx = STEP_OPTIONS.indexOfFirst { it.stepMs == currentStepMs }
        val nextIdx = if (idx < 0) 0 else (idx + 1) % STEP_OPTIONS.size
        return STEP_OPTIONS[nextIdx]
    }

    /**
     * Merges overlapping or contiguous cut segments clamped within [rangeStartMs, rangeEndMs].
     */
    fun mergeCutSegments(
        segments: List<VideoCutSegment>,
        rangeStartMs: Long = 0L,
        rangeEndMs: Long = Long.MAX_VALUE
    ): List<Pair<Long, Long>> {
        if (segments.isEmpty() || rangeEndMs <= rangeStartMs) return emptyList()
        val sorted = segments.mapNotNull { seg ->
            val s = seg.startMs.coerceIn(rangeStartMs, rangeEndMs)
            val e = seg.endMs.coerceIn(rangeStartMs, rangeEndMs)
            if (e > s) s to e else null
        }.sortedWith(compareBy<Pair<Long, Long>> { it.first }.thenBy { it.second })

        if (sorted.isEmpty()) return emptyList()

        val merged = mutableListOf<Pair<Long, Long>>()
        var curStart = sorted.first().first
        var curEnd = sorted.first().second
        for (i in 1 until sorted.size) {
            val (nextStart, nextEnd) = sorted[i]
            if (nextStart <= curEnd) {
                if (nextEnd > curEnd) curEnd = nextEnd
            } else {
                merged.add(curStart to curEnd)
                curStart = nextStart
                curEnd = nextEnd
            }
        }
        merged.add(curStart to curEnd)
        return merged
    }

    /**
     * Total duration removed across all merged cut segments within [rangeStartMs, rangeEndMs].
     */
    fun computeDeletedDurationMs(
        cutSegments: List<VideoCutSegment>,
        rangeStartMs: Long = 0L,
        rangeEndMs: Long = Long.MAX_VALUE
    ): Long {
        return mergeCutSegments(cutSegments, rangeStartMs, rangeEndMs)
            .sumOf { (s, e) -> (e - s).coerceAtLeast(0L) }
    }

    /**
     * Resulting output duration after active trim bounds [trimStartMs, trimEndMs]
     * and subtracting any non-destructive cuts in [cutSegments].
     */
    fun computeOutputLengthMs(
        trimStartMs: Long,
        trimEndMs: Long,
        durationMs: Long,
        cutSegments: List<VideoCutSegment>
    ): Long {
        val safeDuration = durationMs.coerceAtLeast(0L)
        if (safeDuration <= 0L) return 0L
        val effStart = trimStartMs.coerceIn(0L, safeDuration)
        val effEnd = trimEndMs.coerceIn(effStart, safeDuration)
        val trimmedSpan = (effEnd - effStart).coerceAtLeast(0L)
        val deletedInTrim = computeDeletedDurationMs(cutSegments, effStart, effEnd)
        return (trimmedSpan - deletedInTrim).coerceAtLeast(0L)
    }

    /**
     * Computes ordered keep intervals inside [trimStartMs, trimEndMs] with all [cutSegments] excised.
     */
    fun computeKeepRangesMs(
        trimStartMs: Long,
        trimEndMs: Long,
        cutSegments: List<VideoCutSegment>
    ): List<Pair<Long, Long>> {
        val safeStart = trimStartMs.coerceAtLeast(0L)
        val safeEnd = trimEndMs.coerceAtLeast(safeStart)
        if (safeEnd <= safeStart) return emptyList()

        val cuts = mergeCutSegments(cutSegments, safeStart, safeEnd)
        if (cuts.isEmpty()) return listOf(safeStart to safeEnd)

        val keep = mutableListOf<Pair<Long, Long>>()
        var cursor = safeStart
        for ((cutStart, cutEnd) in cuts) {
            if (cutStart > cursor) {
                keep.add(cursor to cutStart)
            }
            if (cutEnd > cursor) {
                cursor = cutEnd
            }
        }
        if (cursor < safeEnd) {
            keep.add(cursor to safeEnd)
        }
        return keep.filter { it.second - it.first >= 50L }
    }

    /**
     * Adjusts a cut marker range cleanly when setting start/end markers relative to each other.
     */
    fun clampStartCutMarker(
        desiredStartMs: Long,
        currentEndMs: Long,
        durationMs: Long
    ): Pair<Long, Long> {
        val safeDuration = durationMs.coerceAtLeast(0L)
        if (safeDuration <= 0L) return 0L to 0L
        val minGap = MIN_CUT_SPAN_MS.coerceAtMost(safeDuration)
        val newStart = desiredStartMs.coerceIn(0L, (safeDuration - minGap).coerceAtLeast(0L))
        val newEnd = if (currentEndMs - newStart < minGap) {
            (newStart + minGap).coerceAtMost(safeDuration)
        } else {
            currentEndMs.coerceIn(newStart + minGap, safeDuration)
        }
        return newStart to newEnd
    }

    fun clampEndCutMarker(
        currentStartMs: Long,
        desiredEndMs: Long,
        durationMs: Long
    ): Pair<Long, Long> {
        val safeDuration = durationMs.coerceAtLeast(0L)
        if (safeDuration <= 0L) return 0L to 0L
        val minGap = MIN_CUT_SPAN_MS.coerceAtMost(safeDuration)
        val newEnd = desiredEndMs.coerceIn(minGap, safeDuration)
        val newStart = if (newEnd - currentStartMs < minGap) {
            (newEnd - minGap).coerceAtLeast(0L)
        } else {
            currentStartMs.coerceIn(0L, newEnd - minGap)
        }
        return newStart to newEnd
    }
}
