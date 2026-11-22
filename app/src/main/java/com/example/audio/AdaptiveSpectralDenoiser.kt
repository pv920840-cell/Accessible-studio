package com.example.audio

import java.util.Arrays
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Parameters for the offline iZotope RX–inspired Adaptive Spectral De-Noise & Voice De-Noise engine.
 *
 * @property thresholdDb Detection threshold sensitivity (-60 dB to 0 dB). Higher values detect and suppress stronger noise floors.
 * @property reductionDb Target noise attenuation depth (0 dB to 30 dB).
 * @property smoothing Spectral & temporal smoothing factor (0.0f to 1.0f) to eliminate metallic "musical noise".
 * @property voiceDialogueFocus When true, enables rumble HPF, harmonic fan/motor comb hunting, and vocal formant protection.
 * @property bypass A/B comparison toggle; when true returns time-aligned original signal smoothly without attenuation.
 * @property fftSize STFT window size (2048 for low-latency real-time preview, 4096 for high-precision spectral profiling).
 * @property adaptiveMode When true, continuously tracks live ambient noise floor shifts via Minimum Statistics / MCRA even without manual Learn selection.
 */
data class SpectralDenoiseParams(
    val thresholdDb: Float = -32.0f,
    val reductionDb: Float = 18.0f,
    val smoothing: Float = 0.65f,
    val voiceDialogueFocus: Boolean = true,
    val bypass: Boolean = false,
    val fftSize: Int = 2048,
    val adaptiveMode: Boolean = true
) {
    fun clamped(): SpectralDenoiseParams = copy(
        thresholdDb = thresholdDb.coerceIn(-60.0f, 0.0f),
        reductionDb = reductionDb.coerceIn(0.0f, 30.0f),
        smoothing = smoothing.coerceIn(0.0f, 1.0f),
        fftSize = if (fftSize >= 4096) 4096 else 2048
    )

    companion object {
        /**
         * Maps a legacy 0..1 intensity scalar smoothly onto calibrated dB-domain RX parameters.
         */
        fun fromLegacyIntensity(
            intensity: Float,
            voiceFocus: Boolean = true
        ): SpectralDenoiseParams {
            val s = intensity.coerceIn(0.0f, 1.0f)
            return when {
                s < 0.02f -> SpectralDenoiseParams(thresholdDb = -60f, reductionDb = 0f, smoothing = 0.50f, voiceDialogueFocus = voiceFocus)
                s >= 0.94f -> SpectralDenoiseParams(thresholdDb = -14f, reductionDb = 30f, smoothing = 0.74f, voiceDialogueFocus = voiceFocus)
                else -> SpectralDenoiseParams(
                    thresholdDb = (-50f + s * 36f).coerceIn(-60f, 0f),
                    reductionDb = (4f + s * 26f).coerceIn(0f, 30f),
                    smoothing = (0.48f + s * 0.28f).coerceIn(0.30f, 0.90f),
                    voiceDialogueFocus = voiceFocus
                )
            }
        }
    }
}

/**
 * Multi-band FFT Noise Print captured from a user-selected "noise-only" segment (Learn Mode).
 */
class SpectralNoiseProfile(
    val sampleRate: Int,
    val fftSize: Int,
    val meanPow: FloatArray,
    val stdPow: FloatArray,
    val tonalMask: FloatArray,
    val noiseFloorDbfs: Float,
    val durationMs: Long,
    val bandSummary: String
) {
    val numBins: Int get() = meanPow.size

    fun resampledMeanPlusStdFor(targetFftSize: Int): FloatArray {
        val targetBins = targetFftSize / 2 + 1
        val out = FloatArray(targetBins)
        if (targetBins == numBins) {
            for (b in 0 until targetBins) {
                out[b] = (meanPow[b] + 0.45f * stdPow[b]).coerceAtLeast(1e-12f)
            }
            return out
        }
        val scalePow = (targetFftSize.toFloat() / fftSize.toFloat())
        for (b in 0 until targetBins) {
            val srcPos = (b.toFloat() / (targetBins - 1).coerceAtLeast(1)) * (numBins - 1)
            val i0 = srcPos.toInt().coerceIn(0, numBins - 1)
            val i1 = min(numBins - 1, i0 + 1)
            val frac = srcPos - i0
            val m = meanPow[i0] + frac * (meanPow[i1] - meanPow[i0])
            val s = stdPow[i0] + frac * (stdPow[i1] - stdPow[i0])
            out[b] = ((m + 0.45f * s) * scalePow).coerceAtLeast(1e-12f)
        }
        return out
    }
}

internal class StftSpec(val size: Int) {
    val hopSize: Int = size / 4 // 75% overlap
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

    fun fft(real: FloatArray, imag: FloatArray, inverse: Boolean) {
        val n = size
        for (i in 0 until n) {
            val j = bitReversed[i]
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }

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
                    val wRe = twiddleCos[tOffset + k]
                    val wIm = if (inverse) -twiddleNegSin[tOffset + k] else twiddleNegSin[tOffset + k]

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

/**
 * Pure-Kotlin Offline Studio Adaptive Spectral & Voice De-Noise DSP Engine.
 *
 * Implements:
 * - Multi-resolution 75% Overlap-Add Hann-windowed STFT (1024 / 2048 / 4096-pt)
 * - Learn Mode spectral profiling with mean + variance envelope & tonal hum mask
 * - Adaptive Minimum Statistics / MCRA live noise-floor tracking
 * - Two-Step Decision-Directed Wiener filtering + spectral gating controlled by Threshold (dB) & Reduction (dB)
 * - Frequency-dependent Bark-inspired spectral & temporal smoothing to suppress musical noise
 * - Optional Voice/Dialogue Focus: 4th-order rumble HPF + Adaptive Fan/Motor Comb Hunter + formant protection
 * - Clickless real-time streaming processor for low-latency live preview and A/B Bypass comparison
 */
object AdaptiveSpectralDenoiser {

    private val spec1024 by lazy { StftSpec(1024) }
    private val spec2048 by lazy { StftSpec(2048) }
    private val spec4096 by lazy { StftSpec(4096) }

    internal fun getSpec(fftSize: Int, sampleRate: Int): StftSpec {
        return when {
            sampleRate <= 20_000 && fftSize < 4096 -> spec1024
            fftSize >= 4096 || sampleRate > 56_000 -> spec4096
            else -> spec2048
        }
    }

    /**
     * Captures a multi-band FFT noise profile from a user-selected "noise-only" segment (Learn Mode).
     */
    fun captureNoiseProfile(
        noiseOnlyPcm: ShortArray,
        sampleRate: Int = StudioAudioProcessor.SAMPLE_RATE,
        fftSize: Int = 2048
    ): SpectralNoiseProfile? {
        val sr = sampleRate.coerceIn(8000, 96000)
        val spec = getSpec(fftSize, sr)
        if (noiseOnlyPcm.size < 256) return null

        val n = spec.size
        val hop = spec.hopSize
        val numBins = spec.numBins
        val re = FloatArray(n)
        val im = FloatArray(n)
        val meanPow = FloatArray(numBins)
        val m2Pow = FloatArray(numBins)

        var sumSq = 0.0
        for (s in noiseOnlyPcm) {
            val f = s.toDouble() / 32767.0
            sumSq += f * f
        }
        val rms = sqrt(sumSq / noiseOnlyPcm.size.coerceAtLeast(1)).coerceAtLeast(1e-7)
        val floorDbfs = (20.0 * log10(rms)).toFloat().coerceIn(-96f, 0f)

        var frames = 0
        var start = 0
        while (start < noiseOnlyPcm.size) {
            for (k in 0 until n) {
                val idx = start + k
                val s = if (idx < noiseOnlyPcm.size) noiseOnlyPcm[idx].toFloat() / 32767f else 0f
                re[k] = s * spec.sqrtHann[k]
                im[k] = 0f
            }
            spec.fft(re, im, inverse = false)
            frames++
            val invN = 1f / frames.toFloat()
            for (b in 0 until numBins) {
                val p = re[b] * re[b] + im[b] * im[b]
                val delta = p - meanPow[b]
                meanPow[b] += delta * invN
                val delta2 = p - meanPow[b]
                m2Pow[b] += delta * delta2
            }
            start += hop
            if (start + hop > noiseOnlyPcm.size && frames >= 1) break
        }

        val stdPow = FloatArray(numBins)
        for (b in 0 until numBins) {
            val variance = if (frames > 1) m2Pow[b] / (frames - 1).toFloat() else 0f
            stdPow[b] = sqrt(max(0f, variance))
            meanPow[b] = meanPow[b].coerceAtLeast(1e-12f)
        }

        val binHz = sr.toFloat() / n.toFloat()
        val tonalMask = detectNarrowbandTonalMask(meanPow, binHz, numBins, enabled = true, strength = 1.0f)

        fun bandPowDb(lowHz: Float, highHz: Float): Int {
            val b0 = max(1, (lowHz / binHz).toInt())
            val b1 = min(numBins - 1, (highHz / binHz).toInt())
            var sum = 0.0
            var cnt = 0
            for (b in b0..b1) {
                sum += meanPow[b]
                cnt++
            }
            val avg = (sum / max(1, cnt)).coerceAtLeast(1e-12)
            // Normalize roughly relative to FFT size energy
            val relDb = 10.0 * log10(avg / (n.toDouble() * 0.25))
            return relDb.toInt().coerceIn(-96, 0)
        }

        val durMs = ((noiseOnlyPcm.size.toDouble() / sr) * 1000.0).toLong()
        val summary = "Floor ${floorDbfs.toInt()} dBFS • Hum/Low ${bandPowDb(40f, 220f)}dB • Mid ${bandPowDb(250f, 3000f)}dB • Hiss ${bandPowDb(3500f, 12000f)}dB"

        return SpectralNoiseProfile(
            sampleRate = sr,
            fftSize = n,
            meanPow = meanPow,
            stdPow = stdPow,
            tonalMask = tonalMask,
            noiseFloorDbfs = floorDbfs,
            durationMs = durMs,
            bandSummary = summary
        )
    }

    /**
     * Offline zero-phase-latency padded STFT De-Noise with Pass-1 adaptive noise lock or Learn profile.
     * Prevents clipping via -0.8 dBFS True-Peak Limiting.
     */
    fun processOffline(
        samples: ShortArray,
        sampleRate: Int = StudioAudioProcessor.SAMPLE_RATE,
        channels: Int = 1,
        params: SpectralDenoiseParams = SpectralDenoiseParams(),
        noiseProfile: SpectralNoiseProfile? = null
    ): ShortArray {
        val cleanParams = params.clamped()
        if (samples.isEmpty() || cleanParams.bypass || cleanParams.reductionDb < 0.25f) return samples
        val sr = sampleRate.coerceIn(8000, 96000)
        val ch = channels.coerceIn(1, 2)
        if (ch == 1 && samples.size < 128) return samples
        if (ch == 2 && samples.size < 256) return samples

        return if (ch == 2) {
            processStereoOffline(samples, sr, cleanParams, noiseProfile)
        } else {
            processMonoOffline(samples, sr, cleanParams, noiseProfile)
        }
    }

    private fun processMonoOffline(
        samples: ShortArray,
        sampleRate: Int,
        params: SpectralDenoiseParams,
        noiseProfile: SpectralNoiseProfile?
    ): ShortArray {
        val pre = preConditionChannel(samples, sampleRate, params)
        val processed = runOfflineStft(arrayOf(pre), sampleRate, params, noiseProfile)[0]
        val polished = if (params.voiceDialogueFocus && params.reductionDb >= 14f) {
            val clarityAmount = ((params.reductionDb - 14f) / 16f * 0.48f).coerceIn(0f, 0.55f)
            applyVocalClarityPolish(polishedInput = processed, clarityBoost = clarityAmount, sampleRate = sampleRate)
        } else {
            processed
        }
        return StudioAudioProcessor.applyTruePeakLimiter(polished, -0.8f)
    }

    private fun processStereoOffline(
        interleaved: ShortArray,
        sampleRate: Int,
        params: SpectralDenoiseParams,
        noiseProfile: SpectralNoiseProfile?
    ): ShortArray {
        val frames = interleaved.size / 2
        val left = ShortArray(frames)
        val right = ShortArray(frames)
        for (i in 0 until frames) {
            left[i] = interleaved[i * 2]
            right[i] = interleaved[i * 2 + 1]
        }
        val preL = preConditionChannel(left, sampleRate, params)
        val preR = preConditionChannel(right, sampleRate, params)

        val cleaned = runOfflineStft(arrayOf(preL, preR), sampleRate, params, noiseProfile)
        var outL = cleaned[0]
        var outR = cleaned[1]

        if (params.voiceDialogueFocus && params.reductionDb >= 14f) {
            val clarityAmount = ((params.reductionDb - 14f) / 16f * 0.48f).coerceIn(0f, 0.55f)
            outL = applyVocalClarityPolish(outL, clarityAmount, sampleRate)
            outR = applyVocalClarityPolish(outR, clarityAmount, sampleRate)
        }

        outL = StudioAudioProcessor.applyTruePeakLimiter(outL, -0.8f)
        outR = StudioAudioProcessor.applyTruePeakLimiter(outR, -0.8f)

        val result = ShortArray(interleaved.size)
        for (i in 0 until frames) {
            result[i * 2] = outL[i]
            result[i * 2 + 1] = outR[i]
        }
        if (interleaved.size % 2 != 0) result[interleaved.lastIndex] = interleaved[interleaved.lastIndex]
        return result
    }

    internal fun preConditionChannel(
        samples: ShortArray,
        sampleRate: Int,
        params: SpectralDenoiseParams
    ): ShortArray {
        if (samples.isEmpty()) return samples
        var sum = 0.0
        for (s in samples) sum += s.toDouble()
        val dc = sum / samples.size.toDouble()

        val strengthNorm = (params.reductionDb / 30f).coerceIn(0f, 1f)
        val cutoff = if (params.voiceDialogueFocus) {
            (55f + strengthNorm * 35f).coerceIn(45f, 95f).toDouble()
        } else {
            30.0
        }
        val stages = if (params.voiceDialogueFocus && params.reductionDb >= 16f) 2 else 1

        val w0 = 2.0 * PI * cutoff / sampleRate.toDouble()
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val q = 0.70710678
        val alpha = sinW0 / (2.0 * q)
        val a0 = 1.0 + alpha
        val b0 = ((1.0 + cosW0) * 0.5) / a0
        val b1 = (-(1.0 + cosW0)) / a0
        val b2 = ((1.0 + cosW0) * 0.5) / a0
        val a1 = (-2.0 * cosW0) / a0
        val a2 = (1.0 - alpha) / a0

        val work = DoubleArray(samples.size) { i -> samples[i].toDouble() - dc }
        val wetMix = if (params.voiceDialogueFocus) (0.45 + 0.55 * strengthNorm).coerceIn(0.45, 1.0) else 0.55

        for (st in 0 until stages) {
            var z1 = 0.0
            var z2 = 0.0
            for (i in work.indices) {
                val x = work[i]
                val y = b0 * x + z1
                z1 = b1 * x - a1 * y + z2
                z2 = b2 * x - a2 * y
                work[i] = if (stages == 1) x * (1.0 - wetMix) + y * wetMix else y
            }
        }

        val out = ShortArray(samples.size)
        for (i in samples.indices) {
            out[i] = work[i].toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun runOfflineStft(
        channelsPcm: Array<ShortArray>,
        sampleRate: Int,
        params: SpectralDenoiseParams,
        noiseProfile: SpectralNoiseProfile?
    ): Array<ShortArray> {
        val numCh = channelsPcm.size
        val len = channelsPcm[0].size
        val spec = getSpec(params.fftSize, sampleRate)
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
        val lowCutHz = if (params.voiceDialogueFocus) 82f else 32f
        val lowCutBin = max(1, (lowCutHz / binHz).toInt())
        val fanBodyEndBin = min(numBins - 1, (1800f / binHz).toInt())
        val presenceStartBin = min(numBins - 1, (2200f / binHz).toInt())
        val ultraHighBin = min(numBins - 1, (13500f / binHz).toInt())

        // Derive physical attenuation & oversubtraction cleanly from RX parameters
        val redDb = params.reductionDb.coerceIn(0f, 30f)
        val redNorm = (redDb / 30f).coerceIn(0f, 1f)
        val threshOffsetDb = (params.thresholdDb.coerceIn(-60f, 0f) + 32f) * 0.28f
        val threshScale = 10.0.pow(threshOffsetDb / 10.0).toFloat().coerceIn(0.18f, 5.5f)
        val maxAttenLinear = 10.0.pow(-redDb / 20.0).toFloat().coerceIn(0.0316f, 1.0f)

        val spectralFloor = if (redDb >= 24f) {
            min(maxAttenLinear, 0.015f - (redDb - 24f) / 6f * 0.008f).coerceIn(0.006f, 0.025f)
        } else {
            (maxAttenLinear * 0.72f).coerceIn(0.012f, 1.0f)
        }

        val pauseFloor = when {
            redDb <= 1f -> 1.0f
            redDb >= 25f -> (0.014f - (redDb - 25f) / 5f * 0.010f).coerceIn(0.004f, 0.018f)
            else -> (maxAttenLinear * (1.15f - redNorm * 0.65f)).coerceIn(0.012f, 1.0f)
        }

        val re = Array(numCh) { FloatArray(fftSize) }
        val im = Array(numCh) { FloatArray(fftSize) }
        val powY = FloatArray(numBins)

        val frameSpeechBandPow = FloatArray(frameCount)
        val frameTotalPow = FloatArray(frameCount)
        val numBlocks = min(16, max(1, frameCount / 6))
        val blockMinBinPow = Array(numBlocks) { FloatArray(numBins) { Float.MAX_VALUE } }
        val blockHasValidFrame = BooleanArray(numBlocks)

        // Pass 1: Measure frame energy & per-block spectral minima
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
                spec.fft(r, m, inverse = false)
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
                if (b in speechMinBin..speechMaxBin) speechPow += p
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

        val nonZeroFrames = frameTotalPow.indices.filter { frameTotalPow[it] > 1e-10f }
        if (nonZeroFrames.isEmpty()) return Array(numCh) { c -> channelsPcm[c].clone() }

        val sortedByPow = nonZeroFrames.sortedBy { frameSpeechBandPow[it] }
        val quietCount = max(1, (sortedByPow.size * 0.16f).toInt().coerceAtMost(min(sortedByPow.size, 40)))
        val noisePow = FloatArray(numBins)

        val usingLearnProfile = noiseProfile != null && noiseProfile.numBins >= 16
        if (usingLearnProfile && noiseProfile != null) {
            val mapped = noiseProfile.resampledMeanPlusStdFor(fftSize)
            System.arraycopy(mapped, 0, noisePow, 0, numBins)
        } else {
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
                    spec.fft(r, m, inverse = false)
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

        val stationaryMinPow = FloatArray(numBins)
        val validBlocks = blockHasValidFrame.indices.filter { blockHasValidFrame[it] }
        if (validBlocks.isNotEmpty()) {
            val tmpVals = FloatArray(validBlocks.size)
            for (b in 1 until numBins) {
                for (idx in validBlocks.indices) tmpVals[idx] = blockMinBinPow[validBlocks[idx]][b]
                tmpVals.sort()
                stationaryMinPow[b] = (tmpVals[tmpVals.size / 2] * 1.85f).coerceAtLeast(1e-12f)
            }
        }

        val bottomQuietPow = frameSpeechBandPow[sortedByPow[(quietCount - 1).coerceAtLeast(0)]]
        val activeTopIdx = ((sortedByPow.size - 1) * 0.88f).toInt().coerceIn(0, sortedByPow.lastIndex)
        val activeSpeechPow = frameSpeechBandPow[sortedByPow[activeTopIdx]]
        val ratioQuietToTop = when {
            usingLearnProfile -> 0.05f
            activeSpeechPow > 1e-9f -> bottomQuietPow / activeSpeechPow
            else -> 1.0f
        }

        val sparsityCount = (sparsityEndBin - sparsityStartBin + 1).coerceAtLeast(1)
        val sparsitySlice = FloatArray(sparsityCount) { i -> noisePow[sparsityStartBin + i] }
        var sparsitySum = 0.0
        for (v in sparsitySlice) sparsitySum += v
        sparsitySlice.sort()
        val medianNoiseBinPow = sparsitySlice[sparsityCount / 2].coerceAtLeast(1e-12f)
        val meanNoiseBinPow = (sparsitySum / sparsityCount.toDouble()).toFloat()
        val spectralSparsity = meanNoiseBinPow / medianNoiseBinPow

        val isContinuousSparseTones = !usingLearnProfile && ratioQuietToTop > 0.55f && spectralSparsity > 6.5f
        val isContinuousBroadband = !usingLearnProfile && ratioQuietToTop > 0.55f && spectralSparsity <= 6.5f

        val protectedBottomPow: Float
        val effectivePauseFloor: Float

        if (isContinuousSparseTones) {
            val valRadius = max(5, (200f / binHz).toInt())
            val rawNoiseCopy = noisePow.clone()
            val valleyMix = ((ratioQuietToTop - 0.50f) / 0.30f).coerceIn(0f, 1f)
            val continuousProtection = (1.0f - (ratioQuietToTop - 0.50f) * 0.90f).coerceIn(0.18f, 1.0f)
            for (b in 1 until numBins) {
                var localMin = rawNoiseCopy[b]
                val s = max(1, b - valRadius)
                val e = min(numBins - 1, b + valRadius)
                for (k in s..e) if (rawNoiseCopy[k] < localMin) localMin = rawNoiseCopy[k]
                val blended = rawNoiseCopy[b] * (1f - valleyMix) + localMin * valleyMix
                noisePow[b] = (blended * continuousProtection).coerceAtLeast(1e-12f)
            }
            protectedBottomPow = bottomQuietPow * continuousProtection * (1f - 0.55f * valleyMix)
            val contBlend = ((ratioQuietToTop - 0.55f) / 0.35f).coerceIn(0f, 1f)
            effectivePauseFloor = pauseFloor + (0.98f - pauseFloor) * contBlend
        } else {
            if (!usingLearnProfile && validBlocks.size >= 3) {
                for (b in 1 until numBins) {
                    val statP = stationaryMinPow[b]
                    if (isContinuousBroadband) {
                        noisePow[b] = min(noisePow[b], statP * 1.15f).coerceAtLeast(1e-12f)
                    } else if (params.voiceDialogueFocus && statP > noisePow[b] * 0.6f && statP < noisePow[b] * 2.2f) {
                        noisePow[b] = (0.72f * noisePow[b] + 0.28f * statP).coerceAtLeast(1e-12f)
                    }
                }
            }
            protectedBottomPow = bottomQuietPow
            effectivePauseFloor = pauseFloor
        }

        // Fan / Motor / Hum Tonal Comb Mask (active strongly in Voice/Dialogue Focus mode)
        val tonalAttenFloor = if (!isContinuousSparseTones && (params.voiceDialogueFocus || redNorm >= 0.4f)) {
            val tonalStrength = if (params.voiceDialogueFocus) max(0.75f, redNorm) else redNorm * 0.55f
            computeTonalAttenuationMask(noisePow, binHz, numBins, tonalStrength, redDb >= 25f)
        } else {
            FloatArray(numBins) { 1.0f }
        }

        val speechFloorRef = max(protectedBottomPow * 1.55f, 1e-10f)

        // Pass 2: Decision-Directed Wiener Filtering + Frequency-Dependent Smoothing + 4x OLA
        val outAccum = Array(numCh) { FloatArray(fftSize) }
        val olaNorm = Array(numCh) { FloatArray(fftSize) }
        val output = Array(numCh) { ShortArray(len) }

        val prevGain = FloatArray(numBins) { 1.0f }
        val prevMag = FloatArray(numBins) { 0f }
        val rawBinGain = FloatArray(numBins)
        val freqSmoothedGain = FloatArray(numBins)
        var speechActivitySmoothed = if (isContinuousSparseTones || isContinuousBroadband) 1.0f else 0f

        val smoothNorm = params.smoothing.coerceIn(0f, 1f)
        val ddBeta = 0.72f + smoothNorm * 0.22f
        val alphaBase = (0.42f + redNorm * 2.05f) * (0.75f + 0.35f * sqrt(threshScale))
        val releaseRate = (0.36f - smoothNorm * 0.22f).coerceIn(0.10f, 0.40f)
        val smoothMaxRadius = when {
            smoothNorm < 0.20f -> 1
            smoothNorm < 0.55f -> 2
            smoothNorm < 0.85f -> 3
            else -> 4
        }

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
                spec.fft(r, m, inverse = false)
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
                    val vadWeight = if (tonalAttenFloor[b] < 0.35f) 0.15f else 1.0f
                    speechNoisePowSum += noisePow[b] * vadWeight
                    totalSpeechPowSum += p * vadWeight
                }
            }

            val noiseBiasComp = if (!usingLearnProfile && !isContinuousSparseTones) 1.08f else 1.0f
            val framePowerSnr = totalSpeechPowSum / (speechNoisePowSum * noiseBiasComp * (0.75f + 0.25f * threshScale))

            val instantSpeechProb = when {
                isContinuousSparseTones -> 1.0f
                frameSpeechBandPow[f] < speechFloorRef * 0.55f -> 0f
                framePowerSnr <= 1.50f -> 0f
                framePowerSnr >= 3.20f -> 1f
                else -> ((framePowerSnr - 1.50f) / 1.70f).coerceIn(0f, 1f)
            }

            val actAlpha = if (instantSpeechProb > speechActivitySmoothed) 0.85f else 0.11f
            speechActivitySmoothed += actAlpha * (instantSpeechProb - speechActivitySmoothed)

            if (params.adaptiveMode && !usingLearnProfile && !isContinuousSparseTones && ratioQuietToTop < 0.55f) {
                for (b in 1 until numBins) {
                    val curP = powY[b]
                    if (curP < noisePow[b] && curP > 1e-12f) {
                        noisePow[b] = (0.90f * noisePow[b] + 0.10f * curP).coerceAtLeast(1e-12f)
                    } else if (speechActivitySmoothed < 0.22f && curP < noisePow[b] * 2.2f) {
                        noisePow[b] = (0.94f * noisePow[b] + 0.06f * curP).coerceAtLeast(1e-12f)
                    }
                }
            }

            val adaptiveAlpha = alphaBase * (1.20f - 0.44f * speechActivitySmoothed)
            for (b in 0 until numBins) {
                val py = powY[b]
                val pn = (noisePow[b] * threshScale + 1e-12f)
                val curMag = sqrt(py)

                val gamma = (py / pn).coerceIn(0f, 200f)
                val prevCleanMag = prevGain[b] * prevMag[b]
                val prevSnr = (prevCleanMag * prevCleanMag) / pn

                val xi1 = ddBeta * prevSnr + (1f - ddBeta) * max(gamma - 1f, 0f)
                val bandAlpha = when {
                    params.voiceDialogueFocus && b <= fanBodyEndBin -> adaptiveAlpha * 1.18f
                    params.voiceDialogueFocus && b >= presenceStartBin && speechActivitySmoothed > 0.30f -> adaptiveAlpha * 0.45f
                    else -> adaptiveAlpha
                }
                val g1 = (xi1 / (xi1 + bandAlpha)).coerceIn(spectralFloor, 1.0f)

                val powStep1 = py * g1 * g1
                val xi2 = 0.72f * prevSnr +
                    0.28f * max((powStep1 / pn) - 1f, 0f) +
                    0.16f * max(gamma - 1.4f, 0f)
                prevMag[b] = curMag

                val wiener = (xi2 / (xi2 + bandAlpha)).coerceIn(spectralFloor, 1.0f)

                val freqWeight = if (params.voiceDialogueFocus) {
                    when {
                        b < lowCutBin -> (0.25f - 0.20f * redNorm).coerceIn(0.04f, 0.30f)
                        b <= ultraHighBin -> 1.0f
                        else -> 0.88f + 0.12f * speechActivitySmoothed
                    }
                } else {
                    if (b < lowCutBin) 0.55f else 1.0f
                }

                val voiceOverTone = if (gamma > 4.2f) ((gamma - 4.2f) / 6.0f).coerceIn(0f, 1f) else 0f
                val voiceRelief = (if (params.voiceDialogueFocus) 0.58f else 0.38f) * speechActivitySmoothed * voiceOverTone
                val dynamicTonalFloor = 1.0f - (1.0f - tonalAttenFloor[b]) * (1.0f - voiceRelief)

                rawBinGain[b] = (wiener * freqWeight * dynamicTonalFloor).coerceIn(spectralFloor, 1.0f)
            }

            // Frequency-dependent Bark-scaled spectral smoothing (tighter at low Hz, broader at high Hz)
            applyFrequencyDependentSmoothing(
                rawBinGain = rawBinGain,
                tonalAttenFloor = tonalAttenFloor,
                outSmoothed = freqSmoothedGain,
                numBins = numBins,
                binHz = binHz,
                maxRadius = smoothMaxRadius
            )

            val s = speechActivitySmoothed.coerceIn(0f, 1f)
            val smoothCurve = s * s * (3f - 2f * s)
            val pauseMult = effectivePauseFloor + (1.0f - effectivePauseFloor) * smoothCurve
            val minTarget = min(spectralFloor, effectivePauseFloor)

            for (b in 0 until numBins) {
                val targetGain = (freqSmoothedGain[b] * pauseMult).coerceIn(minTarget, 1.0f)
                val tAlpha = if (targetGain > prevGain[b]) 0.82f else releaseRate
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
                spec.fft(r, m, inverse = true)

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

    internal fun applyFrequencyDependentSmoothing(
        rawBinGain: FloatArray,
        tonalAttenFloor: FloatArray,
        outSmoothed: FloatArray,
        numBins: Int,
        binHz: Float,
        maxRadius: Int
    ) {
        if (maxRadius <= 0) {
            System.arraycopy(rawBinGain, 0, outSmoothed, 0, numBins)
            return
        }
        for (b in 0 until numBins) {
            val center = rawBinGain[b]
            if (tonalAttenFloor[b] < 0.22f || center >= 0.78f) {
                // Keep narrow tonal notch sharp and never smear high-SNR speech formants/sibilants
                outSmoothed[b] = center
                continue
            }
            val freq = b * binHz
            // Bark-inspired frequency-dependent radius: narrower at low frequencies, wider above 1.8 kHz
            val r = when {
                freq < 450f -> min(1, maxRadius)
                freq < 1800f -> min(2, maxRadius)
                else -> maxRadius
            }
            val s = max(0, b - r)
            val e = min(numBins - 1, b + r)
            var sum = 0f
            var wSum = 0f
            for (k in s..e) {
                val dist = abs(k - b)
                val w = (r + 1 - dist).toFloat()
                sum += rawBinGain[k] * w
                wSum += w
            }
            val smoothed = if (wSum > 0f) sum / wSum else center
            outSmoothed[b] = if (center > 0.58f) {
                // Preserve clean harmonic ridges near SNR transition without blurring them down
                max(center, smoothed)
            } else {
                smoothed
            }
        }
    }

    internal fun detectNarrowbandTonalMask(
        psd: FloatArray,
        binHz: Float,
        numBins: Int,
        enabled: Boolean,
        strength: Float
    ): FloatArray {
        return computeTonalAttenuationMask(psd.clone(), binHz, numBins, strength, studioMax = enabled && strength >= 0.85f)
    }

    internal fun computeTonalAttenuationMask(
        noisePow: FloatArray,
        binHz: Float,
        numBins: Int,
        tonalStrength: Float,
        studioMax: Boolean
    ): FloatArray {
        val tonalAttenFloor = FloatArray(numBins) { 1.0f }
        if (tonalStrength < 0.05f) return tonalAttenFloor

        val mainsHumHz = floatArrayOf(50f, 60f, 100f, 120f, 150f, 180f, 240f, 300f)
        val maxMotorBin = min(numBins - 1, (4500f / binHz).toInt())
        val bgRadius = max(5, (140f / binHz).toInt())
        val localBg = FloatArray(numBins)
        val tmpSlice = FloatArray(bgRadius * 2 + 1)

        for (b in 1..maxMotorBin) {
            val s = max(1, b - bgRadius)
            val e = min(numBins - 1, b + bgRadius)
            val count = e - s + 1
            for (idx in 0 until count) tmpSlice[idx] = noisePow[s + idx]
            Arrays.sort(tmpSlice, 0, count)
            localBg[b] = tmpSlice[count / 2].coerceAtLeast(1e-12f)
        }

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

            val isMainsHumFreq = freq <= 315f && mainsHumHz.any { abs(freq - it) <= humHalfWidthHz }
            val localRatio = noisePow[b] / localBg[b].coerceAtLeast(1e-12f)
            val mainsRatio = if (isMainsHumFreq) noisePow[b] / refBasePow else 1.0f

            var hasHarmonicPartner = false
            val b2 = b * 2
            val b3 = b * 3
            val bHalf = b / 2
            if (b2 <= maxMotorBin && (noisePow[b2] / localBg[b2].coerceAtLeast(1e-12f)) > 1.35f) hasHarmonicPartner = true
            if (b3 <= maxMotorBin && (noisePow[b3] / localBg[b3].coerceAtLeast(1e-12f)) > 1.35f) hasHarmonicPartner = true
            if (bHalf >= 2 && (noisePow[bHalf] / localBg[bHalf].coerceAtLeast(1e-12f)) > 1.35f) hasHarmonicPartner = true

            val narrowPeakThreshold = if (hasHarmonicPartner) 1.38f else 1.65f
            val isDetectedTone = localRatio > narrowPeakThreshold || (isMainsHumFreq && mainsRatio > 1.32f)

            if (isDetectedTone) {
                val peakStrength = max(
                    (localRatio - narrowPeakThreshold) / 2.8f,
                    if (isMainsHumFreq) (mainsRatio - 1.32f) / 2.6f else 0f
                ).coerceIn(0f, 1f)
                val combBoost = if (hasHarmonicPartner) 1.25f else 1.0f
                noisePow[b] *= (1.0f + tonalStrength * combBoost * (0.95f + peakStrength * 1.75f))

                val targetFloor = if (studioMax) {
                    (0.06f - peakStrength * 0.052f).coerceIn(0.005f, 0.08f)
                } else {
                    (1.0f - tonalStrength * (0.62f + 0.34f * peakStrength)).coerceIn(0.015f, 1.0f)
                }
                tonalAttenFloor[b] = min(tonalAttenFloor[b], targetFloor)
                if (b - 1 >= 1) tonalAttenFloor[b - 1] = min(tonalAttenFloor[b - 1], 0.35f + 0.65f * targetFloor)
                if (b + 1 <= maxMotorBin) tonalAttenFloor[b + 1] = min(tonalAttenFloor[b + 1], 0.35f + 0.65f * targetFloor)
            }
        }
        return tonalAttenFloor
    }

    internal fun applyVocalClarityPolish(
        polishedInput: ShortArray,
        clarityBoost: Float,
        sampleRate: Int
    ): ShortArray {
        val c = clarityBoost.coerceIn(0f, 1f)
        if (c < 0.05f || polishedInput.size < 32) return polishedInput
        val gLowTrim = 1f - 0.12f * c
        val gPresence = 1f + 0.18f * c
        val gAir = 1f + 0.12f * c

        val dt = 1f / sampleRate.toFloat()
        val alphaLow = dt / (1f / (2f * PI.toFloat() * 240f) + dt)
        val alphaPres = dt / (1f / (2f * PI.toFloat() * 2200f) + dt)
        val alphaAir = dt / (1f / (2f * PI.toFloat() * 6500f) + dt)

        val out = ShortArray(polishedInput.size)
        var lpLow = 0f
        var lpPres = 0f
        var lpAir = 0f

        for (i in polishedInput.indices) {
            val x = polishedInput[i].toFloat()
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
}

/**
 * Real-Time, Allocation-Free Streaming 75% Overlap-Add STFT Noise Reduction Processor.
 *
 * Designed for low-latency live preview inside [AudioPlayerManager]:
 * - Processes arbitrary PCM chunk sizes sample-by-sample via circular input FIFO & hop-sized STFT frames (~11.6 ms hop @ 44.1 kHz / 2048 FFT).
 * - Maintains a time-aligned dry delay buffer (latency = fftSize - hopSize) so toggling [SpectralDenoiseParams.bypass]
 *   (A/B Compare) performs a sample-accurate, click-free crossfade with zero dropouts or timing shift.
 * - Responds instantaneously on the very next hop when Threshold, Reduction, Smoothing, Voice Focus, or Learn Profile changes.
 */
class RealtimeSpectralDenoiseProcessor(
    sampleRate: Int = StudioAudioProcessor.SAMPLE_RATE,
    fftSize: Int = 2048
) {
    private val safeSr = sampleRate.coerceIn(8000, 96000)
    private val spec = AdaptiveSpectralDenoiser.getSpec(fftSize, safeSr)
    val n: Int = spec.size
    val hopSize: Int = spec.hopSize
    val numBins: Int = spec.numBins
    val latencySamples: Int = n - hopSize

    @Volatile
    var params: SpectralDenoiseParams = SpectralDenoiseParams()

    @Volatile
    private var activeProfile: SpectralNoiseProfile? = null
    private val profileLock = Any()

    // STFT Analysis/Synthesis buffers (pre-allocated)
    private val inputRing = FloatArray(n)
    private var hopFillCount = 0
    private val re = FloatArray(n)
    private val im = FloatArray(n)
    private val powY = FloatArray(numBins)
    private val noisePow = FloatArray(numBins) { 1e-7f }
    private val smoothedPow = FloatArray(numBins)
    private val prevGain = FloatArray(numBins) { 1.0f }
    private val prevMag = FloatArray(numBins)
    private val rawBinGain = FloatArray(numBins)
    private val freqSmoothedGain = FloatArray(numBins)
    private val tonalAttenFloor = FloatArray(numBins) { 1.0f }

    // 4-subblock Minimum Statistics tracker for real-time Adaptive Noise-Floor estimation
    private val numMinBlocks = 4
    private val minSubBlocks = Array(numMinBlocks) { FloatArray(numBins) { Float.MAX_VALUE } }
    private var activeBlockIdx = 0
    private var framesInBlock = 0
    private val framesPerBlock = max(8, (0.35f * safeSr / hopSize.toFloat()).toInt())

    // Output overlap-add buffers
    private val outAccum = FloatArray(n)
    private val olaNorm = FloatArray(n)
    private val readyWetSamples = FloatArray(hopSize)
    private var readyReadPos = hopSize // empty initially

    // Time-aligned dry delay buffer for clickless A/B Bypass compare
    private val dryDelayRing = FloatArray(latencySamples + hopSize)
    private var dryWriteIdx = latencySamples
    private var dryReadIdx = 0
    private var currentBypassMix = 0f

    // Real-time streaming 2nd-order Butterworth rumble HPF state + adaptive VAD
    private var hpZ1 = 0.0
    private var hpZ2 = 0.0
    private var speechActivitySmoothed = 0f
    private var initializedAdaptiveFrames = 0

    private val binHz = safeSr.toFloat() / n.toFloat()
    private val speechMinBin = max(1, (190f / binHz).toInt())
    private val speechMaxBin = min(numBins - 1, (4200f / binHz).toInt())
    private val presenceStartBin = min(numBins - 1, (2200f / binHz).toInt())
    private val fanBodyEndBin = min(numBins - 1, (1800f / binHz).toInt())

    fun setNoiseProfile(profile: SpectralNoiseProfile?) {
        synchronized(profileLock) {
            activeProfile = profile
            if (profile != null) {
                val mapped = profile.resampledMeanPlusStdFor(n)
                System.arraycopy(mapped, 0, noisePow, 0, numBins)
                val tonal = profile.tonalMask
                if (tonal.size == numBins) {
                    System.arraycopy(tonal, 0, tonalAttenFloor, 0, numBins)
                }
                initializedAdaptiveFrames = 24
            } else {
                resetAdaptiveFloorOnly()
            }
        }
    }

    fun reset() {
        Arrays.fill(inputRing, 0f)
        hopFillCount = 0
        Arrays.fill(outAccum, 0f)
        Arrays.fill(olaNorm, 0f)
        Arrays.fill(readyWetSamples, 0f)
        readyReadPos = hopSize
        Arrays.fill(dryDelayRing, 0f)
        dryWriteIdx = latencySamples
        dryReadIdx = 0
        Arrays.fill(prevGain, 1.0f)
        Arrays.fill(prevMag, 0f)
        hpZ1 = 0.0
        hpZ2 = 0.0
        speechActivitySmoothed = 0f
        synchronized(profileLock) {
            val p = activeProfile
            if (p != null) {
                val mapped = p.resampledMeanPlusStdFor(n)
                System.arraycopy(mapped, 0, noisePow, 0, numBins)
            } else {
                resetAdaptiveFloorOnly()
            }
        }
        currentBypassMix = if (params.bypass || params.reductionDb < 0.25f) 1f else 0f
    }

    private fun resetAdaptiveFloorOnly() {
        Arrays.fill(noisePow, 1e-7f)
        Arrays.fill(smoothedPow, 0f)
        Arrays.fill(tonalAttenFloor, 1.0f)
        for (b in minSubBlocks) Arrays.fill(b, Float.MAX_VALUE)
        activeBlockIdx = 0
        framesInBlock = 0
        initializedAdaptiveFrames = 0
    }

    /**
     * Processes [count] mono PCM-16 samples from [src] starting at [srcOffset] into [dst].
     * Safe for repeated real-time AudioTrack chunk writes with zero heap allocation.
     */
    fun processChunk(
        src: ShortArray,
        srcOffset: Int,
        count: Int,
        dst: ShortArray
    ) {
        val p = params.clamped()
        val wantBypass = p.bypass || p.reductionDb < 0.25f
        val targetBypassMix = if (wantBypass) 1f else 0f

        // 2nd-order Butterworth rumble highpass (82 Hz for Voice Focus, 30 Hz otherwise)
        val hpCutoff = if (p.voiceDialogueFocus) 82.0 else 30.0
        val w0 = 2.0 * PI * hpCutoff / safeSr.toDouble()
        val cosW0 = cos(w0)
        val alphaHp = sin(w0) / (2.0 * 0.70710678)
        val a0 = 1.0 + alphaHp
        val b0 = ((1.0 + cosW0) * 0.5) / a0
        val b1 = (-(1.0 + cosW0)) / a0
        val b2 = b0
        val a1 = (-2.0 * cosW0) / a0
        val a2 = (1.0 - alphaHp) / a0

        for (i in 0 until count) {
            val rawSample = src[srcOffset + i]
            val rawFloat = rawSample.toFloat()

            // Write dry sample into latency-aligned circular buffer
            dryDelayRing[dryWriteIdx] = rawFloat
            dryWriteIdx = (dryWriteIdx + 1) % dryDelayRing.size
            val dryAligned = dryDelayRing[dryReadIdx]
            dryReadIdx = (dryReadIdx + 1) % dryDelayRing.size

            // Push pre-conditioned sample into shifting input STFT frame
            val xNorm = rawFloat / 32767.0
            val hpY = (b0 * xNorm + hpZ1).toFloat()
            hpZ1 = b1 * xNorm - a1 * hpY + hpZ2
            hpZ2 = b2 * xNorm - a2 * hpY

            val shiftDstIdx = n - hopSize + hopFillCount
            inputRing[shiftDstIdx] = hpY
            hopFillCount++

            if (hopFillCount >= hopSize) {
                processSingleStftHop(p)
                // Slide inputRing left by hopSize
                System.arraycopy(inputRing, hopSize, inputRing, 0, n - hopSize)
                hopFillCount = 0
                readyReadPos = 0
            }

            val wetSample = if (readyReadPos < hopSize) {
                readyWetSamples[readyReadPos++]
            } else {
                0f
            }

            // Smooth ~6 ms clickless A/B bypass ramp
            currentBypassMix += (targetBypassMix - currentBypassMix) * 0.008f
            val blended = dryAligned * currentBypassMix + wetSample * (1f - currentBypassMix)
            dst[i] = blended.toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    private fun processSingleStftHop(p: SpectralDenoiseParams) {
        for (k in 0 until n) {
            re[k] = inputRing[k] * spec.sqrtHann[k]
            im[k] = 0f
        }
        spec.fft(re, im, inverse = false)

        for (b in 0 until numBins) {
            powY[b] = re[b] * re[b] + im[b] * im[b]
        }

        val hasProfile = activeProfile != null
        val redDb = p.reductionDb.coerceIn(0f, 30f)
        val redNorm = (redDb / 30f).coerceIn(0f, 1f)
        val threshOffsetDb = (p.thresholdDb.coerceIn(-60f, 0f) + 32f) * 0.28f
        val threshScale = 10.0.pow(threshOffsetDb / 10.0).toFloat().coerceIn(0.18f, 5.5f)
        val maxAttenLinear = 10.0.pow(-redDb / 20.0).toFloat().coerceIn(0.0316f, 1.0f)
        val spectralFloor = (maxAttenLinear * 0.72f).coerceIn(0.008f, 1.0f)
        val pauseFloor = if (redDb >= 24f) 0.014f else (maxAttenLinear * 0.90f).coerceIn(0.012f, 1.0f)

        // Adaptive noise floor update when adaptiveMode is ON and no manual profile locked
        if (!hasProfile && p.adaptiveMode) {
            if (initializedAdaptiveFrames == 0) {
                for (b in 0 until numBins) {
                    val pInit = powY[b].coerceAtLeast(1e-10f)
                    noisePow[b] = pInit
                    smoothedPow[b] = pInit
                    minSubBlocks[0][b] = pInit
                }
            }
            initializedAdaptiveFrames++

            framesInBlock++
            if (framesInBlock >= framesPerBlock) {
                framesInBlock = 0
                activeBlockIdx = (activeBlockIdx + 1) % numMinBlocks
                Arrays.fill(minSubBlocks[activeBlockIdx], Float.MAX_VALUE)
            }
            val curBlk = minSubBlocks[activeBlockIdx]
            for (b in 1 until numBins) {
                val py = powY[b]
                val sp = 0.72f * smoothedPow[b] + 0.28f * py
                smoothedPow[b] = sp
                if (sp < curBlk[b]) curBlk[b] = sp

                var minStat = sp
                for (m in 0 until numMinBlocks) {
                    val v = minSubBlocks[m][b]
                    if (v < minStat) minStat = v
                }
                // Compensate sub-block minimum downward bias (~0.45x mean for smoothed periodogram)
                val minFloor = (minStat * 2.10f).coerceAtLeast(1e-11f)
                if (initializedAdaptiveFrames <= 10) {
                    val w = 1f / initializedAdaptiveFrames.toFloat()
                    noisePow[b] = (noisePow[b] * (1f - w) + py * w).coerceAtLeast(1e-11f)
                } else if (speechActivitySmoothed < 0.28f || sp < noisePow[b] * 1.65f) {
                    // Track smoothed PSD cleanly during pauses / stationary floor shifts without ratcheting down
                    val target = max(sp, minFloor * 0.82f)
                    noisePow[b] = (noisePow[b] * 0.88f + target * 0.12f).coerceAtLeast(1e-11f)
                } else if (minFloor > noisePow[b]) {
                    noisePow[b] = (noisePow[b] * 0.94f + minFloor * 0.06f).coerceAtLeast(1e-11f)
                }
            }

            if (p.voiceDialogueFocus && (initializedAdaptiveFrames == 6 || initializedAdaptiveFrames % 24 == 0)) {
                val mask = AdaptiveSpectralDenoiser.computeTonalAttenuationMask(
                    noisePow = noisePow.clone(),
                    binHz = binHz,
                    numBins = numBins,
                    tonalStrength = max(0.75f, redNorm),
                    studioMax = redDb >= 24f
                )
                System.arraycopy(mask, 0, tonalAttenFloor, 0, numBins)
            }
        }

        // Voice Activity Detection
        var speechNoiseSum = 1e-10f
        var speechPowSum = 0f
        for (b in speechMinBin..speechMaxBin) {
            val w = if (tonalAttenFloor[b] < 0.35f) 0.15f else 1.0f
            speechNoiseSum += noisePow[b] * w
            speechPowSum += powY[b] * w
        }
        val framePowerSnr = speechPowSum / (speechNoiseSum * max(1.0f, threshScale * 0.85f))
        val instantSpeechProb = when {
            framePowerSnr <= 1.65f -> 0f
            framePowerSnr >= 3.40f -> 1f
            else -> ((framePowerSnr - 1.65f) / 1.75f).coerceIn(0f, 1f)
        }
        val actAlpha = if (instantSpeechProb > speechActivitySmoothed) 0.85f else 0.14f
        speechActivitySmoothed += actAlpha * (instantSpeechProb - speechActivitySmoothed)

        val smoothNorm = p.smoothing.coerceIn(0f, 1f)
        val ddBeta = 0.72f + smoothNorm * 0.22f
        val alphaBase = (0.42f + redNorm * 2.05f) * (0.75f + 0.35f * sqrt(threshScale))
        val adaptiveAlpha = alphaBase * (1.20f - 0.44f * speechActivitySmoothed)
        val releaseRate = (0.36f - smoothNorm * 0.22f).coerceIn(0.10f, 0.40f)
        val smoothMaxRadius = when {
            smoothNorm < 0.20f -> 1
            smoothNorm < 0.55f -> 2
            smoothNorm < 0.85f -> 3
            else -> 4
        }

        for (b in 0 until numBins) {
            val py = powY[b]
            val pn = noisePow[b] * threshScale + 1e-12f
            val curMag = sqrt(py)

            val gamma = (py / pn).coerceIn(0f, 200f)
            val prevCleanMag = prevGain[b] * prevMag[b]
            val prevSnr = (prevCleanMag * prevCleanMag) / pn

            val xi1 = ddBeta * prevSnr + (1f - ddBeta) * max(gamma - 1f, 0f)
            val bandAlpha = when {
                p.voiceDialogueFocus && b <= fanBodyEndBin -> adaptiveAlpha * 1.18f
                p.voiceDialogueFocus && b >= presenceStartBin && speechActivitySmoothed > 0.30f -> adaptiveAlpha * 0.45f
                else -> adaptiveAlpha
            }
            val g1 = (xi1 / (xi1 + bandAlpha)).coerceIn(spectralFloor, 1.0f)
            val xi2 = 0.72f * prevSnr +
                0.28f * max((py * g1 * g1 / pn) - 1f, 0f) +
                0.16f * max(gamma - 1.4f, 0f)
            prevMag[b] = curMag

            val wiener = (xi2 / (xi2 + bandAlpha)).coerceIn(spectralFloor, 1.0f)
            val dynamicTonal = if (p.voiceDialogueFocus) {
                val relief = 0.52f * speechActivitySmoothed * if (gamma > 4.2f) ((gamma - 4.2f) / 6f).coerceIn(0f, 1f) else 0f
                1.0f - (1.0f - tonalAttenFloor[b]) * (1.0f - relief)
            } else 1.0f

            rawBinGain[b] = (wiener * dynamicTonal).coerceIn(spectralFloor, 1.0f)
        }

        AdaptiveSpectralDenoiser.applyFrequencyDependentSmoothing(
            rawBinGain = rawBinGain,
            tonalAttenFloor = tonalAttenFloor,
            outSmoothed = freqSmoothedGain,
            numBins = numBins,
            binHz = binHz,
            maxRadius = smoothMaxRadius
        )

        val s = speechActivitySmoothed.coerceIn(0f, 1f)
        val smoothCurve = s * s * (3f - 2f * s)
        val pauseMult = pauseFloor + (1.0f - pauseFloor) * smoothCurve
        val minTarget = min(spectralFloor, pauseFloor)

        for (b in 0 until numBins) {
            val targetGain = (freqSmoothedGain[b] * pauseMult).coerceIn(minTarget, 1.0f)
            val tAlpha = if (targetGain > prevGain[b]) 0.82f else releaseRate
            val finalGain = prevGain[b] + tAlpha * (targetGain - prevGain[b])
            prevGain[b] = finalGain

            re[b] *= finalGain
            im[b] *= finalGain
            if (b in 1 until (n / 2)) {
                val mirror = n - b
                re[mirror] *= finalGain
                im[mirror] *= finalGain
            }
        }

        spec.fft(re, im, inverse = true)
        for (k in 0 until n) {
            val w = spec.sqrtHann[k]
            outAccum[k] += re[k] * w
            olaNorm[k] += w * w
        }
        for (k in 0 until hopSize) {
            val norm = olaNorm[k].coerceAtLeast(1e-4f)
            val sampleOut = (outAccum[k] / norm) * 32767f
            readyWetSamples[k] = sampleOut.coerceIn(-32767f, 32767f)
        }
        val keep = n - hopSize
        System.arraycopy(outAccum, hopSize, outAccum, 0, keep)
        System.arraycopy(olaNorm, hopSize, olaNorm, 0, keep)
        Arrays.fill(outAccum, keep, n, 0f)
        Arrays.fill(olaNorm, keep, n, 0f)
    }
}
