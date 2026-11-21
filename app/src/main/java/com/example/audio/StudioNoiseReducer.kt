package com.example.audio

import java.util.Arrays
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Configuration for [StudioNoiseReducer], supporting transparent studio noise reduction,
 * aggressive stationary fan/motor/compressor harmonic removal, custom Noise Print sampling,
 * and spoken-word studio isolation without metallic ringing ("musical noise") or chopped syllables.
 */
data class StudioNoiseReductionConfig(
    val strength: Float = 0.60f,
    val humRemovalStrength: Float = 0.75f,
    val fanMotorRemovalStrength: Float = 0.78f,
    val spectralFloorOverride: Float? = null,
    val pauseAttenuationFloor: Float = 0.25f,
    val lowCutHz: Float = 62f,
    val lowCutAttenuation: Float = 0.55f,
    val preserveHighFrequencies: Boolean = true,
    val vocalClarityBoost: Float = 0.0f,
    val studioMaxClean: Boolean = false,
    val customNoisePrintPcm: ShortArray? = null,
    val deHumTargetHz: FloatArray = floatArrayOf(50f, 60f, 100f, 120f, 150f, 180f, 240f, 300f)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StudioNoiseReductionConfig) return false
        return strength == other.strength &&
            humRemovalStrength == other.humRemovalStrength &&
            fanMotorRemovalStrength == other.fanMotorRemovalStrength &&
            spectralFloorOverride == other.spectralFloorOverride &&
            pauseAttenuationFloor == other.pauseAttenuationFloor &&
            lowCutHz == other.lowCutHz &&
            lowCutAttenuation == other.lowCutAttenuation &&
            preserveHighFrequencies == other.preserveHighFrequencies &&
            vocalClarityBoost == other.vocalClarityBoost &&
            studioMaxClean == other.studioMaxClean &&
            customNoisePrintPcm.contentEquals(other.customNoisePrintPcm) &&
            deHumTargetHz.contentEquals(other.deHumTargetHz)
    }

    override fun hashCode(): Int {
        var result = strength.hashCode()
        result = 31 * result + humRemovalStrength.hashCode()
        result = 31 * result + fanMotorRemovalStrength.hashCode()
        result = 31 * result + (spectralFloorOverride?.hashCode() ?: 0)
        result = 31 * result + pauseAttenuationFloor.hashCode()
        result = 31 * result + lowCutHz.hashCode()
        result = 31 * result + lowCutAttenuation.hashCode()
        result = 31 * result + preserveHighFrequencies.hashCode()
        result = 31 * result + vocalClarityBoost.hashCode()
        result = 31 * result + studioMaxClean.hashCode()
        result = 31 * result + (customNoisePrintPcm?.contentHashCode() ?: 0)
        result = 31 * result + deHumTargetHz.contentHashCode()
        return result
    }
}

/**
 * Pure-Kotlin 100% Offline Studio-Grade Spectral + Harmonic Noise Reduction Engine.
 *
 * Features:
 * - Zero-allocation precomputed-twiddle radix-2 STFT with 2048-pt resolution (44.1/48 kHz)
 *   and 75% (4x) sqrt-Hann overlap-add for high low-frequency resolution and transient accuracy.
 * - Cascaded 2nd/4th-order Butterworth high-pass rumble cut to strip fan thump & motor vibration.
 * - Optional custom Noise Print profiling from user-selected background noise clips.
 * - P88 active-speech quantile vs quiet-frame noise profiling + stationary block-minimum tracking
 *   (accurately cleans tracks with long pauses as well as steady fan/motor noise).
 * - Adaptive Fan & Motor Harmonic Comb Hunter (40 Hz – 4,500 Hz) detecting both mains hum and
 *   arbitrary RPM-dependent motor/fan tonal peaks & integer harmonics.
 * - Two-Step Decision-Directed Wiener/MMSE gain estimation + 5-bin spectral smoothing +
 *   Hermite-curve studio pause gate to silence fans/motors completely between words without
 *   robotic musical noise or muffled consonants.
 */
object StudioNoiseReducer {

    private class FftSpec(val size: Int) {
        val hopSize: Int = size / 4
        val numBins: Int = size / 2 + 1
        val sqrtHann: FloatArray = FloatArray(size) { i ->
            sin(PI * i.toDouble() / size.toDouble()).toFloat()
        }
        val bitReversed: IntArray = IntArray(size).apply {
            var logN = 0
            var temp = size
            while (temp > 1) {
                logN++
                temp = temp ushr 1
            }
            for (i in 0 until size) {
                var rev = 0
                var x = i
                for (b in 0 until logN) {
                    rev = (rev shl 1) or (x and 1)
                    x = x ushr 1
                }
                this[i] = rev
            }
        }
        // Precomputed twiddle tables for all butterfly stages (total size = size - 1)
        val stageOffsets: IntArray
        val twiddleCos: FloatArray
        val twiddleNegSin: FloatArray

        init {
            var stages = 0
            var s = 2
            while (s <= size) {
                stages++
                s = s shl 1
            }
            stageOffsets = IntArray(stages)
            twiddleCos = FloatArray(size - 1)
            twiddleNegSin = FloatArray(size - 1)
            var idx = 0
            var stage = 0
            var len = 2
            while (len <= size) {
                stageOffsets[stage] = idx
                val half = len ushr 1
                val step = -2.0 * PI / len.toDouble()
                for (k in 0 until half) {
                    val angle = step * k
                    twiddleCos[idx] = cos(angle).toFloat()
                    twiddleNegSin[idx] = sin(angle).toFloat()
                    idx++
                }
                stage++
                len = len shl 1
            }
        }
    }

    private val spec1024 by lazy { FftSpec(1024) }
    private val spec2048 by lazy { FftSpec(2048) }
    private val spec4096 by lazy { FftSpec(4096) }

    private fun selectFftSpec(sampleRate: Int): FftSpec {
        return when {
            sampleRate <= 24_000 -> spec1024
            sampleRate <= 56_000 -> spec2048
            else -> spec4096
        }
    }

    fun reduceNoise(
        samples: ShortArray,
        sampleRate: Int = StudioAudioProcessor.SAMPLE_RATE,
        channels: Int = 1,
        config: StudioNoiseReductionConfig = StudioNoiseReductionConfig()
    ): ShortArray {
        val sr = sampleRate.coerceIn(8000, 96000)
        val ch = channels.coerceIn(1, 2)
        val strength = config.strength.coerceIn(0f, 1f)
        if (samples.isEmpty() || strength < 0.02f) return samples
        if (ch == 1 && samples.size < 128) return samples
        if (ch == 2 && samples.size < 256) return samples

        return if (ch == 2) {
            processStereoLinked(samples, sr, config)
        } else {
            processMonoInternal(samples, sr, config)
        }
    }

    private fun processMonoInternal(
        samples: ShortArray,
        sampleRate: Int,
        config: StudioNoiseReductionConfig
    ): ShortArray {
        val pre = preConditionChannel(samples, sampleRate, config.lowCutHz, config.lowCutAttenuation, config.studioMaxClean)
        val prePrint = config.customNoisePrintPcm?.let {
            if (it.size >= 256) preConditionChannel(it, sampleRate, config.lowCutHz, config.lowCutAttenuation, config.studioMaxClean) else null
        }
        var work = runStftDenoise(arrayOf(pre), sampleRate, config, prePrint)[0]
        if (config.vocalClarityBoost > 0.05f || config.studioMaxClean) {
            val boost = if (config.studioMaxClean) max(config.vocalClarityBoost, 0.42f) else config.vocalClarityBoost
            work = applyVocalClarityPolish(work, boost, sampleRate)
        }
        return StudioAudioProcessor.applyTruePeakLimiter(work, -0.8f)
    }

    private fun processStereoLinked(
        interleaved: ShortArray,
        sampleRate: Int,
        config: StudioNoiseReductionConfig
    ): ShortArray {
        val frameCount = interleaved.size / 2
        val left = ShortArray(frameCount)
        val right = ShortArray(frameCount)
        for (i in 0 until frameCount) {
            left[i] = interleaved[i * 2]
            right[i] = interleaved[i * 2 + 1]
        }

        val preL = preConditionChannel(left, sampleRate, config.lowCutHz, config.lowCutAttenuation, config.studioMaxClean)
        val preR = preConditionChannel(right, sampleRate, config.lowCutHz, config.lowCutAttenuation, config.studioMaxClean)
        val prePrint = config.customNoisePrintPcm?.let {
            if (it.size >= 256) preConditionChannel(it, sampleRate, config.lowCutHz, config.lowCutAttenuation, config.studioMaxClean) else null
        }

        val cleanedChannels = runStftDenoise(arrayOf(preL, preR), sampleRate, config, prePrint)
        var outL = cleanedChannels[0]
        var outR = cleanedChannels[1]

        if (config.vocalClarityBoost > 0.05f || config.studioMaxClean) {
            val boost = if (config.studioMaxClean) max(config.vocalClarityBoost, 0.42f) else config.vocalClarityBoost
            outL = applyVocalClarityPolish(outL, boost, sampleRate)
            outR = applyVocalClarityPolish(outR, boost, sampleRate)
        }

        outL = StudioAudioProcessor.applyTruePeakLimiter(outL, -0.8f)
        outR = StudioAudioProcessor.applyTruePeakLimiter(outR, -0.8f)

        val result = ShortArray(interleaved.size)
        for (i in 0 until frameCount) {
            result[i * 2] = outL[i]
            result[i * 2 + 1] = outR[i]
        }
        if (interleaved.size % 2 != 0) {
            result[interleaved.lastIndex] = interleaved[interleaved.lastIndex]
        }
        return result
    }

    /**
     * Removes electrical DC offset and applies a 2nd-order (or cascaded 4th-order in Studio Max / heavy rumble modes)
     * Butterworth biquad high-pass filter so sub-sonic fan thump & motor chassis rumble are cleanly stripped
     * while preserving natural voice chest warmth above cutoff.
     */
    private fun preConditionChannel(
        samples: ShortArray,
        sampleRate: Int,
        lowCutHz: Float,
        lowCutAttenuation: Float,
        studioMaxClean: Boolean
    ): ShortArray {
        if (samples.isEmpty()) return samples

        // 1. Remove electrical DC offset
        var mean = 0.0
        for (s in samples) mean += s.toDouble()
        val dcOffset = mean / samples.size.toDouble()

        val cutoff = lowCutHz.coerceIn(25f, 150f).toDouble()
        val useSteep4thOrder = studioMaxClean || lowCutAttenuation >= 0.42f
        val stages = if (useSteep4thOrder) 2 else 1
        val work = DoubleArray(samples.size) { i -> samples[i].toDouble() - dcOffset }

        // Biquad Butterworth HPF (-12 dB/oct per stage, Q = 0.7071)
        val w0 = 2.0 * PI * cutoff / sampleRate.toDouble()
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val q = if (stages == 2) 0.70710678 else 0.70710678
        val alpha = sinW0 / (2.0 * q)
        val a0 = 1.0 + alpha
        val b0 = ((1.0 + cosW0) * 0.5) / a0
        val b1 = (-(1.0 + cosW0)) / a0
        val b2 = ((1.0 + cosW0) * 0.5) / a0
        val a1 = (-2.0 * cosW0) / a0
        val a2 = (1.0 - alpha) / a0

        val wetMix = if (useSteep4thOrder) 1.0 else lowCutAttenuation.coerceIn(0.15f, 1.0f).toDouble()

        for (stage in 0 until stages) {
            var z1 = 0.0
            var z2 = 0.0
            for (i in work.indices) {
                val x = work[i]
                val y = b0 * x + z1
                z1 = b1 * x - a1 * y + z2
                z2 = b2 * x - a2 * y
                work[i] = if (stage == 0 && stages == 1) {
                    x * (1.0 - wetMix) + y * wetMix
                } else {
                    y
                }
            }
        }

        val out = ShortArray(samples.size)
        for (i in samples.indices) {
            out[i] = work[i].toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun runStftDenoise(
        channelsPcm: Array<ShortArray>,
        sampleRate: Int,
        config: StudioNoiseReductionConfig,
        preConditionedNoisePrint: ShortArray?
    ): Array<ShortArray> {
        val numCh = channelsPcm.size
        val len = channelsPcm[0].size
        val spec = selectFftSpec(sampleRate)
        val fftSize = spec.size
        val hopSize = spec.hopSize
        val numBins = spec.numBins
        val pad = fftSize - hopSize
        val paddedLen = len + 2 * pad
        val frameCount = (((paddedLen - fftSize + hopSize - 1) / hopSize) + 1).coerceAtLeast(1)

        val sr = sampleRate.toFloat()
        val binHz = sr / fftSize.toFloat()
        val speechMinBin = max(1, (190f / binHz).toInt())
        val speechMaxBin = min(numBins - 1, (4200f / binHz).toInt())
        val sparsityStartBin = max(1, (100f / binHz).toInt())
        val sparsityEndBin = min(numBins - 1, (2600f / binHz).toInt())
        val lowCutBin = max(1, (config.lowCutHz / binHz).toInt())
        val fanBodyEndBin = min(numBins - 1, (1800f / binHz).toInt())
        val airStartBin = min(numBins - 1, (6500f / binHz).toInt())
        val ultraHighBin = min(numBins - 1, (13500f / binHz).toInt())

        val strength = config.strength.coerceIn(0.05f, 1.0f)
        val studioMax = config.studioMaxClean
        val spectralFloor = (config.spectralFloorOverride ?: if (studioMax) 0.007f else (0.24f - strength * 0.22f))
            .coerceIn(0.004f, 0.35f)
        val pauseFloor = (if (studioMax) min(config.pauseAttenuationFloor, 0.005f) else config.pauseAttenuationFloor)
            .coerceIn(min(spectralFloor, 0.004f), 1.0f)
        val fanMotorStr = (if (studioMax) max(config.fanMotorRemovalStrength, 0.96f) else config.fanMotorRemovalStrength)
            .coerceIn(0f, 1.0f)
        val humStr = max(config.humRemovalStrength.coerceIn(0f, 1f), fanMotorStr * 0.85f)

        val re = Array(numCh) { FloatArray(fftSize) }
        val im = Array(numCh) { FloatArray(fftSize) }
        val powY = FloatArray(numBins)

        val frameSpeechBandPow = FloatArray(frameCount)
        val frameTotalPow = FloatArray(frameCount)

        // Block-minimum statistics across up to 16 equal time blocks for tracking continuous stationary fan/motor
        val numBlocks = min(16, max(1, frameCount / 6))
        val blockMinBinPow = Array(numBlocks) { FloatArray(numBins) { Float.MAX_VALUE } }
        val blockHasValidFrame = BooleanArray(numBlocks)

        // Pass 1: Measure per-frame speech-band & total energy + per-block spectral minima
        for (f in 0 until frameCount) {
            val offset = f * hopSize
            for (c in 0 until numCh) {
                val src = channelsPcm[c]
                val r = re[c]
                val m = im[c]
                for (k in 0 until fftSize) {
                    val sIdx = offset + k - pad
                    val s = if (sIdx in src.indices) src[sIdx].toFloat() / 32767f else 0f
                    r[k] = s * spec.sqrtHann[k]
                    m[k] = 0f
                }
                fftWithSpec(r, m, spec, inverse = false)
            }

            var speechPow = 0f
            var totalPow = 0f
            val invCh = 1f / numCh.toFloat()
            val blkIdx = (f * numBlocks) / frameCount
            val blkMins = blockMinBinPow[blkIdx]

            for (b in 1 until numBins) {
                var p = 0f
                for (c in 0 until numCh) {
                    val rv = re[c][b]
                    val iv = im[c][b]
                    p += rv * rv + iv * iv
                }
                p *= invCh
                powY[b] = p
                totalPow += p
                if (b in speechMinBin..speechMaxBin) {
                    speechPow += p
                }
            }
            val avgTotal = totalPow / max(1, numBins - 1)
            frameSpeechBandPow[f] = speechPow / max(1, speechMaxBin - speechMinBin + 1)
            frameTotalPow[f] = avgTotal

            if (avgTotal > 1e-10f) {
                blockHasValidFrame[blkIdx] = true
                for (b in 0 until numBins) {
                    val p = if (b == 0) 0f else powY[b]
                    if (p < blkMins[b]) blkMins[b] = p
                }
            }
        }

        // Ignore completely silent digital-zero frames so leading/trailing silence never zeros noise profile
        val nonZeroFrames = frameTotalPow.indices.filter { frameTotalPow[it] > 1e-10f }
        if (nonZeroFrames.isEmpty()) {
            return Array(numCh) { c -> channelsPcm[c].clone() }
        }

        val sortedByPow = nonZeroFrames.sortedBy { frameSpeechBandPow[it] }
        val quietCount = max(1, (sortedByPow.size * 0.16f).toInt().coerceAtMost(min(sortedByPow.size, 40)))
        val noisePow = FloatArray(numBins)

        val usingCustomNoisePrint = preConditionedNoisePrint != null && preConditionedNoisePrint.size >= hopSize * 2
        if (usingCustomNoisePrint && preConditionedNoisePrint != null) {
            measureNoisePrintPsd(preConditionedNoisePrint, spec, noisePow)
        } else {
            // Automatic quiet-frame spectral noise estimation
            for (q in 0 until quietCount) {
                val offset = sortedByPow[q] * hopSize
                for (c in 0 until numCh) {
                    val src = channelsPcm[c]
                    val r = re[c]
                    val m = im[c]
                    for (k in 0 until fftSize) {
                        val sIdx = offset + k - pad
                        val s = if (sIdx in src.indices) src[sIdx].toFloat() / 32767f else 0f
                        r[k] = s * spec.sqrtHann[k]
                        m[k] = 0f
                    }
                    fftWithSpec(r, m, spec, inverse = false)
                }
                val invCh = 1f / numCh.toFloat()
                for (b in 0 until numBins) {
                    var p = 0f
                    for (c in 0 until numCh) {
                        val rv = re[c][b]
                        val iv = im[c][b]
                        p += rv * rv + iv * iv
                    }
                    noisePow[b] += p * invCh
                }
            }
            val invQuiet = 1f / quietCount.toFloat()
            for (b in 0 until numBins) {
                noisePow[b] = (noisePow[b] * invQuiet).coerceAtLeast(1e-12f)
            }
        }

        // Estimate block-median of block-minimums (tracks stationary fan/motor floor even when quiet gaps are tiny)
        val stationaryMinPow = FloatArray(numBins)
        val validBlocks = blockHasValidFrame.indices.filter { blockHasValidFrame[it] }
        if (validBlocks.isNotEmpty()) {
            val tmpVals = FloatArray(validBlocks.size)
            for (b in 1 until numBins) {
                for (idx in validBlocks.indices) {
                    tmpVals[idx] = blockMinBinPow[validBlocks[idx]][b]
                }
                tmpVals.sort()
                // Median of per-block minimums compensated for minimum-order bias (~1.85x)
                val medMin = tmpVals[tmpVals.size / 2]
                stationaryMinPow[b] = (medMin * 1.85f).coerceAtLeast(1e-12f)
            }
        }

        // Compare quietest noise floor against P88 active speech power (NOT P50 median, which fails on pause-heavy audio!)
        val bottomQuietPow = frameSpeechBandPow[sortedByPow[(quietCount - 1).coerceAtLeast(0)]]
        val activeTopIdx = ((sortedByPow.size - 1) * 0.88f).toInt().coerceIn(0, sortedByPow.lastIndex)
        val activeSpeechPow = frameSpeechBandPow[sortedByPow[activeTopIdx]]
        val ratioQuietToTop = if (usingCustomNoisePrint) {
            0.05f
        } else if (activeSpeechPow > 1e-9f) {
            bottomQuietPow / activeSpeechPow
        } else {
            1.0f
        }

        // Measure spectral sparsity in quiet PSD:
        // Pure synthetic/singing sustained harmonic tones have sharp spikes against low broadband valleys (mean/median >> 6);
        // Stationary broadband fan/motor airflow fills adjacent bins densely (mean/median <= 5).
        val sparsityCount = (sparsityEndBin - sparsityStartBin + 1).coerceAtLeast(1)
        val sparsitySlice = FloatArray(sparsityCount) { i -> noisePow[sparsityStartBin + i] }
        var sparsitySum = 0.0
        for (v in sparsitySlice) sparsitySum += v
        sparsitySlice.sort()
        val medianNoiseBinPow = sparsitySlice[sparsityCount / 2].coerceAtLeast(1e-12f)
        val meanNoiseBinPow = (sparsitySum / sparsityCount.toDouble()).toFloat()
        val spectralSparsity = meanNoiseBinPow / medianNoiseBinPow

        val isContinuousSparseTones = !usingCustomNoisePrint && ratioQuietToTop > 0.55f && spectralSparsity > 6.5f
        val isContinuousBroadband = !usingCustomNoisePrint && ratioQuietToTop > 0.55f && spectralSparsity <= 6.5f

        val protectedBottomPow: Float
        val effectivePauseFloor: Float

        if (isContinuousSparseTones) {
            // Protect sustained singing/synthetic pure tones without silent gaps from hollowing
            val valRadius = max(5, (200f / binHz).toInt())
            val rawNoiseCopy = noisePow.clone()
            val valleyMix = ((ratioQuietToTop - 0.50f) / 0.30f).coerceIn(0f, 1f)
            val continuousProtection = (1.0f - (ratioQuietToTop - 0.50f) * 0.90f).coerceIn(0.18f, 1.0f)
            for (b in 1 until numBins) {
                var localMin = rawNoiseCopy[b]
                val s = max(1, b - valRadius)
                val e = min(numBins - 1, b + valRadius)
                for (k in s..e) {
                    if (rawNoiseCopy[k] < localMin) localMin = rawNoiseCopy[k]
                }
                val blended = rawNoiseCopy[b] * (1f - valleyMix) + localMin * valleyMix
                noisePow[b] = (blended * continuousProtection).coerceAtLeast(1e-12f)
            }
            protectedBottomPow = bottomQuietPow * continuousProtection * (1f - 0.55f * valleyMix)
            val contBlend = ((ratioQuietToTop - 0.55f) / 0.35f).coerceIn(0f, 1f)
            effectivePauseFloor = pauseFloor + (0.98f - pauseFloor) * contBlend
        } else {
            // Merge stationary minimum-statistics conservatively on quiet speech dips
            if (!usingCustomNoisePrint && validBlocks.size >= 3) {
                for (b in 1 until numBins) {
                    val statP = stationaryMinPow[b]
                    if (isContinuousBroadband) {
                        noisePow[b] = min(noisePow[b], statP * 1.15f).coerceAtLeast(1e-12f)
                    } else if (fanMotorStr > 0.4f && statP > noisePow[b] * 0.6f && statP < noisePow[b] * 2.2f) {
                        // Slightly reinforce persistent fan bins
                        noisePow[b] = (0.72f * noisePow[b] + 0.28f * statP).coerceAtLeast(1e-12f)
                    }
                }
            }
            protectedBottomPow = bottomQuietPow
            effectivePauseFloor = pauseFloor
        }

        // Adaptive Fan & Motor Harmonic / Narrowband Tonal Hunter (40 Hz – 4,500 Hz)
        // Detects both configured 50/60 Hz harmonics AND arbitrary motor/fan blade RPM tones & integer harmonics.
        val tonalAttenFloor = FloatArray(numBins) { 1.0f }
        if ((humStr > 0.05f || fanMotorStr > 0.05f) && !isContinuousSparseTones) {
            val maxMotorBin = min(numBins - 1, (4500f / binHz).toInt())
            val bgRadius = max(5, (140f / binHz).toInt())
            val localBg = FloatArray(numBins)
            val tmpSlice = FloatArray(bgRadius * 2 + 1)

            for (b in 1..maxMotorBin) {
                val s = max(1, b - bgRadius)
                val e = min(numBins - 1, b + bgRadius)
                val count = e - s + 1
                for (idx in 0 until count) {
                    tmpSlice[idx] = noisePow[s + idx]
                }
                Arrays.sort(tmpSlice, 0, count)
                localBg[b] = tmpSlice[count / 2].coerceAtLeast(1e-12f)
            }

            // Reference baseline for low-frequency 50/60/100/120/150/180 Hz hum vs 220-520 Hz floor
            val refStart = max(1, (220f / binHz).toInt())
            val refEnd = min(numBins - 1, (520f / binHz).toInt())
            val refCount = (refEnd - refStart + 1).coerceAtLeast(1)
            val refSlice = FloatArray(refCount) { idx -> noisePow[refStart + idx] }
            refSlice.sort()
            val refBasePow = refSlice[refCount / 2].coerceAtLeast(1e-12f)
            val humHalfWidthHz = max(binHz * 0.72f, 18f)

            for (b in 1..maxMotorBin) {
                val freq = b * binHz
                if (freq < 35f) continue

                val isMainsHumFreq = freq <= 315f && config.deHumTargetHz.any { abs(freq - it) <= humHalfWidthHz }
                val localRatio = noisePow[b] / localBg[b].coerceAtLeast(1e-12f)
                val mainsRatio = if (isMainsHumFreq) noisePow[b] / refBasePow else 1.0f

                // Check harmonic comb support (2*f or 3*f or f/2 also narrowband peak)
                var hasHarmonicPartner = false
                val b2 = b * 2
                val b3 = b * 3
                val bHalf = b / 2
                if (b2 <= maxMotorBin && (noisePow[b2] / localBg[b2].coerceAtLeast(1e-12f)) > 1.35f) hasHarmonicPartner = true
                if (b3 <= maxMotorBin && (noisePow[b3] / localBg[b3].coerceAtLeast(1e-12f)) > 1.35f) hasHarmonicPartner = true
                if (bHalf >= 2 && (noisePow[bHalf] / localBg[bHalf].coerceAtLeast(1e-12f)) > 1.35f) hasHarmonicPartner = true

                val narrowPeakThreshold = if (hasHarmonicPartner) 1.38f else 1.65f
                val isDetectedFanMotorTone = localRatio > narrowPeakThreshold || (isMainsHumFreq && mainsRatio > 1.32f)

                if (isDetectedFanMotorTone) {
                    val peakStrength = max(
                        (localRatio - narrowPeakThreshold) / 2.8f,
                        if (isMainsHumFreq) (mainsRatio - 1.32f) / 2.6f else 0f
                    ).coerceIn(0f, 1f)
                    val combBoost = if (hasHarmonicPartner) 1.25f else 1.0f
                    val effStr = max(humStr, fanMotorStr)

                    noisePow[b] *= (1.0f + effStr * combBoost * (0.95f + peakStrength * 1.75f))
                    val targetFloor = if (studioMax) {
                        (0.06f - peakStrength * 0.052f).coerceIn(0.005f, 0.08f)
                    } else {
                        (1.0f - effStr * (0.62f + 0.34f * peakStrength)).coerceIn(0.015f, 1.0f)
                    }
                    tonalAttenFloor[b] = min(tonalAttenFloor[b], targetFloor)

                    // Widen slightly to immediate neighbor leakage bin (+/- 1 bin) for Hann window mainlobe
                    if (b - 1 >= 1) tonalAttenFloor[b - 1] = min(tonalAttenFloor[b - 1], 0.35f + 0.65f * targetFloor)
                    if (b + 1 <= maxMotorBin) tonalAttenFloor[b + 1] = min(tonalAttenFloor[b + 1], 0.35f + 0.65f * targetFloor)
                }
            }
        }

        // Compute clean speech floor reference (robust to pause-heavy and noisy fan recordings)
        val speechFloorRef = max(protectedBottomPow * 1.55f, 1e-10f)

        // Pass 2: Two-Step Decision-Directed Wiener/MMSE Filter + 4x Overlap-Add
        val outAccum = Array(numCh) { FloatArray(fftSize) }
        val olaNorm = Array(numCh) { FloatArray(fftSize) }
        val output = Array(numCh) { ShortArray(len) }

        val prevGain = FloatArray(numBins) { 1.0f }
        val prevMag = FloatArray(numBins) { 0f }
        val rawBinGain = FloatArray(numBins)
        val freqSmoothedGain = FloatArray(numBins)
        var speechActivitySmoothed = if (isContinuousSparseTones || isContinuousBroadband) 1.0f else 0f

        val ddBeta = 0.88f
        val alphaBase = 0.55f + strength * 1.45f + (if (studioMax) 0.45f else 0f)

        for (f in 0 until frameCount) {
            val offset = f * hopSize
            for (c in 0 until numCh) {
                val src = channelsPcm[c]
                val r = re[c]
                val m = im[c]
                for (k in 0 until fftSize) {
                    val sIdx = offset + k - pad
                    val s = if (sIdx in src.indices) src[sIdx].toFloat() / 32767f else 0f
                    r[k] = s * spec.sqrtHann[k]
                    m[k] = 0f
                }
                fftWithSpec(r, m, spec, inverse = false)
            }

            val invCh = 1f / numCh.toFloat()
            var speechNoisePowSum = 1e-10f
            var totalSpeechPowSum = 0f

            for (b in 0 until numBins) {
                var p = 0f
                for (c in 0 until numCh) {
                    val rv = re[c][b]
                    val iv = im[c][b]
                    p += rv * rv + iv * iv
                }
                p *= invCh
                powY[b] = p
                if (b in speechMinBin..speechMaxBin) {
                    // Weight down strong stationary motor-tone bins in VAD so motor buzz never triggers voice gate
                    val vadWeight = if (tonalAttenFloor[b] < 0.35f) 0.15f else 1.0f
                    speechNoisePowSum += noisePow[b] * vadWeight
                    totalSpeechPowSum += p * vadWeight
                }
            }

            // Compensate quiet-16%-quantile downward order-statistic bias in averaged speech band
            val noiseBiasComp = if (!usingCustomNoisePrint && !isContinuousSparseTones) 1.08f else 1.0f
            val framePowerSnr = totalSpeechPowSum / (speechNoisePowSum * noiseBiasComp)

            val instantSpeechProb = when {
                isContinuousSparseTones -> 1.0f
                frameSpeechBandPow[f] < speechFloorRef * 0.55f -> 0f
                framePowerSnr <= 1.52f -> 0f
                framePowerSnr >= 3.20f -> 1f
                else -> ((framePowerSnr - 1.52f) / 1.68f).coerceIn(0f, 1f)
            }

            // Very fast onset (0.85) preserves initial consonants ('t', 'k', 's'); smooth hangover release (0.11 for 4x OLA)
            val actAlpha = if (instantSpeechProb > speechActivitySmoothed) 0.85f else 0.11f
            speechActivitySmoothed += actAlpha * (instantSpeechProb - speechActivitySmoothed)

            // Conservative minimum tracking of stationary background noise in pure pause frames
            if (!usingCustomNoisePrint && !isContinuousSparseTones && ratioQuietToTop < 0.55f) {
                for (b in 1 until numBins) {
                    val curP = powY[b]
                    if (curP < noisePow[b] && curP > 1e-12f) {
                        noisePow[b] = (0.90f * noisePow[b] + 0.10f * curP).coerceAtLeast(1e-12f)
                    } else if (speechActivitySmoothed < 0.22f && curP < noisePow[b] * 2.2f) {
                        noisePow[b] = (0.94f * noisePow[b] + 0.06f * curP).coerceAtLeast(1e-12f)
                    }
                }
            }

            // Two-Step Decision-Directed Wiener Spectral Gain
            val adaptiveAlpha = alphaBase * (1.22f - 0.46f * speechActivitySmoothed)
            for (b in 0 until numBins) {
                val py = powY[b]
                val pn = noisePow[b] + 1e-12f
                val curMag = sqrt(py)

                val gamma = (py / pn).coerceIn(0f, 200f)
                val prevCleanMag = prevGain[b] * prevMag[b]
                val prevSnr = (prevCleanMag * prevCleanMag) / pn

                // Step 1 a priori SNR & provisional gain
                val xi1 = ddBeta * prevSnr + (1f - ddBeta) * max(gamma - 1f, 0f)
                // Fan/motor body frequencies (<1.8 kHz) receive slightly firmer oversubtraction than crisp vocal upper air
                val bandAlpha = when {
                    b <= fanBodyEndBin && fanMotorStr > 0.35f -> adaptiveAlpha * (1.0f + 0.24f * fanMotorStr)
                    b >= airStartBin && config.preserveHighFrequencies && speechActivitySmoothed > 0.30f -> adaptiveAlpha * 0.82f
                    else -> adaptiveAlpha
                }
                val g1 = (xi1 / (xi1 + bandAlpha)).coerceIn(spectralFloor, 1.0f)

                // Step 2 (TSNR refinement): removes transient delay while pushing stationary noise deeper
                val powStep1 = py * g1 * g1
                val xi2 = 0.72f * prevSnr +
                    0.28f * max((powStep1 / pn) - 1f, 0f) +
                    0.16f * max(gamma - 1.4f, 0f)
                prevMag[b] = curMag

                val wiener = (xi2 / (xi2 + bandAlpha)).coerceIn(spectralFloor, 1.0f)

                // Preserve vocal presence & high-frequency consonant air cleanly
                val freqWeight = when {
                    b < lowCutBin -> (1.0f - config.lowCutAttenuation * 0.90f).coerceIn(0.04f, 1.0f)
                    config.preserveHighFrequencies && b <= ultraHighBin -> 1.0f
                    config.preserveHighFrequencies -> 0.88f + 0.12f * speechActivitySmoothed
                    b <= airStartBin -> 0.94f + 0.06f * speechActivitySmoothed
                    else -> 0.82f + 0.16f * speechActivitySmoothed
                }

                // Tonal/motor notch locks down stationary motor hum during pauses and yields ONLY when clear voice peak dominates
                val voiceOverTone = if (gamma > 4.2f) ((gamma - 4.2f) / 6.0f).coerceIn(0f, 1f) else 0f
                val voiceRelief = 0.58f * speechActivitySmoothed * voiceOverTone
                val dynamicTonalFloor = 1.0f - (1.0f - tonalAttenFloor[b]) * (1.0f - voiceRelief)

                rawBinGain[b] = (wiener * freqWeight * dynamicTonalFloor).coerceIn(spectralFloor, 1.0f)
            }

            // 5-bin spectral smoothing eliminates isolated narrow-band chirps ("musical noise")
            // while avoiding smearing sharp motor notch bins back up
            for (b in 0 until numBins) {
                if (tonalAttenFloor[b] < 0.20f) {
                    freqSmoothedGain[b] = rawBinGain[b]
                } else {
                    val g0 = rawBinGain[max(0, b - 2)]
                    val g1 = rawBinGain[max(0, b - 1)]
                    val g2 = rawBinGain[b]
                    val g3 = rawBinGain[min(numBins - 1, b + 1)]
                    val g4 = rawBinGain[min(numBins - 1, b + 2)]
                    freqSmoothedGain[b] = 0.10f * g0 + 0.22f * g1 + 0.36f * g2 + 0.22f * g3 + 0.10f * g4
                }
            }

            // Smooth Hermite S-curve on VAD avoids abrupt pumping while driving pauses into studio silence
            val s = speechActivitySmoothed.coerceIn(0f, 1f)
            val smoothCurve = s * s * (3f - 2f * s)
            val pauseMult = effectivePauseFloor + (1.0f - effectivePauseFloor) * smoothCurve
            val minTarget = min(spectralFloor, effectivePauseFloor)

            for (b in 0 until numBins) {
                val targetGain = (freqSmoothedGain[b] * pauseMult).coerceIn(minTarget, 1.0f)
                val tAlpha = if (targetGain > prevGain[b]) 0.84f else 0.26f
                val finalGain = prevGain[b] + tAlpha * (targetGain - prevGain[b])
                prevGain[b] = finalGain

                for (c in 0 until numCh) {
                    re[c][b] *= finalGain
                    im[c][b] *= finalGain
                    if (b in 1 until (fftSize / 2)) {
                        val mirror = fftSize - b
                        re[c][mirror] *= finalGain
                        im[c][mirror] *= finalGain
                    }
                }
            }

            for (c in 0 until numCh) {
                val r = re[c]
                val m = im[c]
                fftWithSpec(r, m, spec, inverse = true)

                val acc = outAccum[c]
                val nrm = olaNorm[c]
                for (k in 0 until fftSize) {
                    val w = spec.sqrtHann[k]
                    acc[k] += r[k] * w
                    nrm[k] += w * w
                }

                val outCh = output[c]
                for (k in 0 until hopSize) {
                    val sigIdx = offset + k - pad
                    if (sigIdx in outCh.indices) {
                        val norm = nrm[k].coerceAtLeast(1e-4f)
                        val sampleOut = (acc[k] / norm) * 32767f
                        outCh[sigIdx] = sampleOut.toInt().coerceIn(-32767, 32767).toShort()
                    }
                }
                val keepCount = fftSize - hopSize
                System.arraycopy(acc, hopSize, acc, 0, keepCount)
                System.arraycopy(nrm, hopSize, nrm, 0, keepCount)
                Arrays.fill(acc, keepCount, fftSize, 0f)
                Arrays.fill(nrm, keepCount, fftSize, 0f)
            }
        }

        // Flush remaining OLA samples
        val finalOffset = frameCount * hopSize
        val flushCount = fftSize - hopSize
        for (c in 0 until numCh) {
            val acc = outAccum[c]
            val nrm = olaNorm[c]
            val outCh = output[c]
            for (k in 0 until flushCount) {
                val sigIdx = finalOffset + k - pad
                if (sigIdx in outCh.indices) {
                    val norm = nrm[k].coerceAtLeast(1e-4f)
                    val sampleOut = (acc[k] / norm) * 32767f
                    outCh[sigIdx] = sampleOut.toInt().coerceIn(-32767, 32767).toShort()
                }
            }
        }

        return output
    }

    /**
     * Measures exact mean + variance PSD from a user-selected background noise print clip.
     */
    private fun measureNoisePrintPsd(
        printPcm: ShortArray,
        spec: FftSpec,
        outNoisePow: FloatArray
    ) {
        val fftSize = spec.size
        val hopSize = spec.hopSize
        val numBins = spec.numBins
        val re = FloatArray(fftSize)
        val im = FloatArray(fftSize)
        val meanPow = FloatArray(numBins)
        val m2Pow = FloatArray(numBins)
        var frames = 0

        var start = 0
        while (start < printPcm.size) {
            for (k in 0 until fftSize) {
                val idx = start + k
                val s = if (idx < printPcm.size) printPcm[idx].toFloat() / 32767f else 0f
                re[k] = s * spec.sqrtHann[k]
                im[k] = 0f
            }
            fftWithSpec(re, im, spec, inverse = false)
            frames++
            val invN = 1f / frames.toFloat()
            for (b in 0 until numBins) {
                val p = re[b] * re[b] + im[b] * im[b]
                val delta = p - meanPow[b]
                meanPow[b] += delta * invN
                val delta2 = p - meanPow[b]
                m2Pow[b] += delta * delta2
            }
            start += hopSize
            if (start + hopSize > printPcm.size && frames >= 1) break
        }

        for (b in 0 until numBins) {
            val variance = if (frames > 1) m2Pow[b] / (frames - 1).toFloat() else 0f
            val std = sqrt(max(0f, variance))
            // Capture mean + 0.55 std so fluctuating fan blades in the noise print are fully covered
            outNoisePow[b] = (meanPow[b] + 0.55f * std).coerceAtLeast(1e-12f)
        }
    }

    private fun applyVocalClarityPolish(
        samples: ShortArray,
        clarityBoost: Float,
        sampleRate: Int
    ): ShortArray {
        val c = clarityBoost.coerceIn(0f, 1f)
        if (c < 0.05f || samples.size < 32) return samples
        // Gentle low-mud trim, vocal presence lift (2.2-6.5 kHz) and smooth upper air
        val gLowTrim = 1f - 0.12f * c
        val gPresence = 1f + 0.18f * c
        val gAir = 1f + 0.12f * c

        val dt = 1f / sampleRate.toFloat()
        val alphaLow = dt / (1f / (2f * PI.toFloat() * 240f) + dt)
        val alphaPres = dt / (1f / (2f * PI.toFloat() * 2200f) + dt)
        val alphaAir = dt / (1f / (2f * PI.toFloat() * 6500f) + dt)

        val out = ShortArray(samples.size)
        var lpLow = 0f
        var lpPres = 0f
        var lpAir = 0f

        for (i in samples.indices) {
            val x = samples[i].toFloat()
            lpLow += alphaLow * (x - lpLow)
            lpPres += alphaPres * (x - lpPres)
            lpAir += alphaAir * (x - lpAir)

            val lowBand = lpLow
            val midBand = lpPres - lpLow
            val presBand = lpAir - lpPres
            val airBand = x - lpAir

            val shaped = lowBand * gLowTrim + midBand + presBand * gPresence + airBand * gAir
            out[i] = shaped.toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    internal fun fftWithSize1024(real: FloatArray, imag: FloatArray, inverse: Boolean) {
        fftWithSpec(real, imag, spec1024, inverse)
    }

    private fun fftWithSpec(real: FloatArray, imag: FloatArray, spec: FftSpec, inverse: Boolean) {
        val n = spec.size
        val bitRev = spec.bitReversed
        for (i in 0 until n) {
            val j = bitRev[i]
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }

        val cosTbl = spec.twiddleCos
        val negSinTbl = spec.twiddleNegSin
        val stageOffsets = spec.stageOffsets
        var stage = 0
        var len = 2
        while (len <= n) {
            val halfLen = len ushr 1
            val tOffset = stageOffsets[stage]

            var start = 0
            while (start < n) {
                for (k in 0 until halfLen) {
                    val i1 = start + k
                    val i2 = i1 + halfLen
                    val wRe = cosTbl[tOffset + k]
                    val wIm = if (inverse) -negSinTbl[tOffset + k] else negSinTbl[tOffset + k]

                    val r2 = real[i2]
                    val m2 = imag[i2]
                    val tRe = wRe * r2 - wIm * m2
                    val tIm = wRe * m2 + wIm * r2

                    real[i2] = real[i1] - tRe
                    imag[i2] = imag[i1] - tIm
                    real[i1] += tRe
                    imag[i1] += tIm
                }
                start += len
            }
            stage++
            len = len shl 1
        }

        if (inverse) {
            val invN = 1.0f / n.toFloat()
            for (i in 0 until n) {
                real[i] *= invN
                imag[i] *= invN
            }
        }
    }
}
