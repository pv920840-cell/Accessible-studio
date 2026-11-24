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

    // ── Standardized Step Slider (Milliseconds <-> Seconds <-> Minutes) ──

    enum class StepUnitCategory(val label: String) {
        MILLISECONDS("Milliseconds"),
        SECONDS("Seconds"),
        MINUTES("Minutes")
    }

    data class StandardizedStepOption(
        val stepMs: Long,
        val shortLabel: String,
        val spokenLabel: String,
        val unitCategory: StepUnitCategory
    )

    val STANDARDIZED_STEP_OPTIONS: List<StandardizedStepOption> = listOf(
        StandardizedStepOption(10L, "10 ms", "10 milliseconds", StepUnitCategory.MILLISECONDS),
        StandardizedStepOption(50L, "50 ms", "50 milliseconds", StepUnitCategory.MILLISECONDS),
        StandardizedStepOption(100L, "100 ms", "100 milliseconds", StepUnitCategory.MILLISECONDS),
        StandardizedStepOption(250L, "250 ms", "250 milliseconds", StepUnitCategory.MILLISECONDS),
        StandardizedStepOption(500L, "500 ms", "500 milliseconds", StepUnitCategory.MILLISECONDS),
        StandardizedStepOption(1_000L, "1 s", "1 second", StepUnitCategory.SECONDS),
        StandardizedStepOption(2_000L, "2 s", "2 seconds", StepUnitCategory.SECONDS),
        StandardizedStepOption(5_000L, "5 s", "5 seconds", StepUnitCategory.SECONDS),
        StandardizedStepOption(10_000L, "10 s", "10 seconds", StepUnitCategory.SECONDS),
        StandardizedStepOption(30_000L, "30 s", "30 seconds", StepUnitCategory.SECONDS),
        StandardizedStepOption(60_000L, "1 min", "1 minute", StepUnitCategory.MINUTES),
        StandardizedStepOption(120_000L, "2 min", "2 minutes", StepUnitCategory.MINUTES),
        StandardizedStepOption(300_000L, "5 min", "5 minutes", StepUnitCategory.MINUTES)
    )

    fun standardizedStepIndexFor(stepMs: Long): Int {
        val exact = STANDARDIZED_STEP_OPTIONS.indexOfFirst { it.stepMs == stepMs }
        if (exact >= 0) return exact
        return STANDARDIZED_STEP_OPTIONS.indices.minByOrNull { idx ->
            kotlin.math.abs(STANDARDIZED_STEP_OPTIONS[idx].stepMs - stepMs)
        } ?: 5 // default 1 s
    }

    fun standardizedStepAt(index: Int): StandardizedStepOption {
        val clamped = index.coerceIn(0, STANDARDIZED_STEP_OPTIONS.lastIndex)
        return STANDARDIZED_STEP_OPTIONS[clamped]
    }

    /**
     * Swipe UP action: increases step size toward seconds and then minutes.
     */
    fun stepUp(currentStepMs: Long): StandardizedStepOption {
        val idx = standardizedStepIndexFor(currentStepMs)
        return standardizedStepAt((idx + 1).coerceAtMost(STANDARDIZED_STEP_OPTIONS.lastIndex))
    }

    /**
     * Swipe DOWN action: decreases step size toward fine millisecond precision.
     */
    fun stepDown(currentStepMs: Long): StandardizedStepOption {
        val idx = standardizedStepIndexFor(currentStepMs)
        return standardizedStepAt((idx - 1).coerceAtLeast(0))
    }

    fun nudgeTimestamp(
        currentMs: Long,
        deltaSteps: Int,
        stepMs: Long,
        minMs: Long,
        maxMs: Long
    ): Long {
        val safeMin = minMs.coerceAtLeast(0L)
        val safeMax = maxMs.coerceAtLeast(safeMin)
        return (currentMs + deltaSteps * stepMs).coerceIn(safeMin, safeMax)
    }

    // ── Splitter, Auto-Splitter & Multi-Trim Range Derivation ──

    fun splitPointsToRanges(
        trimStartMs: Long,
        trimEndMs: Long,
        splitPointsMs: List<Long>
    ): List<Pair<Long, Long>> {
        val safeStart = trimStartMs.coerceAtLeast(0L)
        val safeEnd = trimEndMs.coerceAtLeast(safeStart)
        if (safeEnd <= safeStart) return emptyList()
        val interiorPoints = splitPointsMs
            .map { it.coerceIn(safeStart, safeEnd) }
            .filter { it > safeStart && it < safeEnd }
            .distinct()
            .sorted()
        if (interiorPoints.isEmpty()) return listOf(safeStart to safeEnd)
        val result = mutableListOf<Pair<Long, Long>>()
        var cursor = safeStart
        for (pt in interiorPoints) {
            if (pt - cursor >= 50L) {
                result.add(cursor to pt)
            }
            cursor = pt
        }
        if (safeEnd - cursor >= 50L) {
            result.add(cursor to safeEnd)
        }
        return result
    }

    fun autoSplitRangesByLength(
        trimStartMs: Long,
        trimEndMs: Long,
        intervalMs: Long
    ): List<Pair<Long, Long>> {
        val safeStart = trimStartMs.coerceAtLeast(0L)
        val safeEnd = trimEndMs.coerceAtLeast(safeStart)
        if (safeEnd <= safeStart) return emptyList()
        val safeInterval = intervalMs.coerceAtLeast(500L)
        val ranges = mutableListOf<Pair<Long, Long>>()
        var cursor = safeStart
        while (cursor < safeEnd) {
            val nextEnd = (cursor + safeInterval).coerceAtMost(safeEnd)
            if (nextEnd - cursor >= 50L) {
                ranges.add(cursor to nextEnd)
            }
            cursor = nextEnd
        }
        return ranges
    }

    fun autoSplitRangesByCount(
        trimStartMs: Long,
        trimEndMs: Long,
        fileCount: Int
    ): List<Pair<Long, Long>> {
        val safeStart = trimStartMs.coerceAtLeast(0L)
        val safeEnd = trimEndMs.coerceAtLeast(safeStart)
        if (safeEnd <= safeStart) return emptyList()
        val safeCount = fileCount.coerceIn(1, 200)
        val span = safeEnd - safeStart
        val chunk = (span / safeCount).coerceAtLeast(50L)
        val ranges = mutableListOf<Pair<Long, Long>>()
        var cursor = safeStart
        for (i in 0 until safeCount) {
            val nextEnd = if (i == safeCount - 1) safeEnd else (cursor + chunk).coerceAtMost(safeEnd)
            if (nextEnd > cursor) {
                ranges.add(cursor to nextEnd)
            }
            cursor = nextEnd
            if (cursor >= safeEnd) break
        }
        return ranges
    }

    fun autoSplitRangesByTargetSize(
        trimStartMs: Long,
        trimEndMs: Long,
        targetSizeMb: Float,
        totalDurationMs: Long,
        estimatedTotalBytes: Long
    ): List<Pair<Long, Long>> {
        val safeStart = trimStartMs.coerceAtLeast(0L)
        val safeEnd = trimEndMs.coerceAtLeast(safeStart)
        if (safeEnd <= safeStart) return emptyList()
        val safeTargetBytes = (targetSizeMb.coerceAtLeast(0.5f) * 1024f * 1024f).toDouble()
        val bytesPerMs = if (totalDurationMs > 0L && estimatedTotalBytes > 0L) {
            estimatedTotalBytes.toDouble() / totalDurationMs.toDouble()
        } else {
            // Reasonable default fallback (~2.5 Mbps -> ~312.5 bytes/ms)
            312.5
        }
        val intervalMs = (safeTargetBytes / bytesPerMs).toLong().coerceAtLeast(1_000L)
        return autoSplitRangesByLength(safeStart, safeEnd, intervalMs)
    }

    fun computeMultiTrimKeepRanges(
        multiTrimClips: List<VideoCutSegment>,
        cutSegments: List<VideoCutSegment>,
        fallbackStartMs: Long,
        fallbackEndMs: Long
    ): List<Pair<Long, Long>> {
        if (multiTrimClips.isEmpty()) {
            return computeKeepRangesMs(fallbackStartMs, fallbackEndMs, cutSegments)
        }
        val mergedClips = mergeCutSegments(multiTrimClips, 0L, Long.MAX_VALUE)
        val result = mutableListOf<Pair<Long, Long>>()
        for ((clipStart, clipEnd) in mergedClips) {
            result.addAll(computeKeepRangesMs(clipStart, clipEnd, cutSegments))
        }
        return result
    }
}
