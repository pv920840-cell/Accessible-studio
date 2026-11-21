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
 * Configuration for [StudioNoiseReducer], supporting transparent studio noise reduction
 * as well as stronger spoken-word isolation without metallic ringing ("musical noise"),
 * chopped syllables, or muffled high frequencies.
 */
data class StudioNoiseReductionConfig(
    val strength: Float = 0.60f,
    val humRemovalStrength: Float = 0.65f,
    val spectralFloorOverride: Float? = null,
    val pauseAttenuationFloor: Float = 0.72f,
    val lowCutHz: Float = 55f,
    val lowCutAttenuation: Float = 0.35f,
    val preserveHighFrequencies: Boolean = true,
    val vocalClarityBoost: Float = 0.0f,
    val deHumTargetHz: FloatArray = floatArrayOf(50f, 60f, 100f, 120f, 150f, 180f)
)

/**
 * Pure-Kotlin Studio-Grade Spectral Noise Reduction Engine.
 *
 * Uses a multi-rate Hann-windowed STFT with 50% overlap-add, non-silent noise floor
 * estimation with continuous-speech protection, Decision-Directed a priori SNR estimation
 * (Ephraim-Malah / power-domain Wiener filter), 5-bin spectral + asymmetric temporal
 * gain smoothing to eliminate "musical noise" / metallic chirps, adaptive 50/60 Hz harmonic
 * hum suppression, high-frequency air/consonant preservation, and linked-channel stereo
 * processing so stereo imaging remains stable.
 */
object StudioNoiseReducer {

    private class FftSpec(val size: Int) {
        val hopSize: Int = size / 2
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
    }

    private val spec512 by lazy { FftSpec(512) }
    private val spec1024 by lazy { FftSpec(1024) }
    private val spec2048 by lazy { FftSpec(2048) }

    private fun selectFftSpec(sampleRate: Int): FftSpec {
        return when {
            sampleRate <= 24_000 -> spec512
            sampleRate <= 56_000 -> spec1024
            else -> spec2048
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
        val pre = preConditionChannel(samples, sampleRate, config.lowCutHz)
        var work = runStftDenoise(arrayOf(pre), sampleRate, config)[0]
        if (config.vocalClarityBoost > 0.05f) {
            work = applyVocalClarityPolish(work, config.vocalClarityBoost, sampleRate)
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

        val preL = preConditionChannel(left, sampleRate, config.lowCutHz)
        val preR = preConditionChannel(right, sampleRate, config.lowCutHz)

        val cleanedChannels = runStftDenoise(arrayOf(preL, preR), sampleRate, config)
        var outL = cleanedChannels[0]
        var outR = cleanedChannels[1]

        if (config.vocalClarityBoost > 0.05f) {
            outL = applyVocalClarityPolish(outL, config.vocalClarityBoost, sampleRate)
            outR = applyVocalClarityPolish(outR, config.vocalClarityBoost, sampleRate)
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

    private fun preConditionChannel(
        samples: ShortArray,
        sampleRate: Int,
        lowCutHz: Float
    ): ShortArray {
        if (samples.isEmpty()) return samples
        // 1. Remove electrical DC offset
        var mean = 0.0
        for (s in samples) mean += s.toDouble()
        val dcOffset = mean / samples.size.toDouble()

        // 2. Gentle sample-rate-accurate 1st-order sub-sonic rumble high-pass filter
        val cutoff = lowCutHz.coerceIn(25f, 160f)
        val rc = 1.0f / (2.0f * PI.toFloat() * cutoff)
        val dt = 1.0f / sampleRate.toFloat()
        val hpAlpha = rc / (rc + dt)

        val out = ShortArray(samples.size)
        var prevIn = 0f
        var prevOut = 0f
        for (i in samples.indices) {
            val centered = (samples[i].toDouble() - dcOffset).toFloat()
            val hp = hpAlpha * (prevOut + centered - prevIn)
            prevIn = centered
            prevOut = hp
            out[i] = hp.toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun runStftDenoise(
        channelsPcm: Array<ShortArray>,
        sampleRate: Int,
        config: StudioNoiseReductionConfig
    ): Array<ShortArray> {
        val numCh = channelsPcm.size
        val len = channelsPcm[0].size
        val spec = selectFftSpec(sampleRate)
        val fftSize = spec.size
        val hopSize = spec.hopSize
        val numBins = spec.numBins
        val pad = hopSize
        val paddedLen = len + 2 * pad
        val frameCount = (((paddedLen - fftSize + hopSize - 1) / hopSize) + 1).coerceAtLeast(1)

        val sr = sampleRate.toFloat()
        val binHz = sr / fftSize.toFloat()
        val speechMinBin = max(1, (160f / binHz).toInt())
        val speechMaxBin = min(numBins - 1, (4200f / binHz).toInt())
        val lowCutBin = max(1, (config.lowCutHz / binHz).toInt())
        val airStartBin = min(numBins - 1, (9000f / binHz).toInt())
        val ultraHighBin = min(numBins - 1, (13500f / binHz).toInt())

        val strength = config.strength.coerceIn(0.05f, 1.0f)
        val spectralFloor = (config.spectralFloorOverride ?: (0.28f - strength * 0.22f))
            .coerceIn(0.015f, 0.35f)
        val pauseFloor = config.pauseAttenuationFloor.coerceIn(spectralFloor.coerceAtMost(0.95f), 1.0f)

        val re = Array(numCh) { FloatArray(fftSize) }
        val im = Array(numCh) { FloatArray(fftSize) }
        val powY = FloatArray(numBins)

        val frameSpeechBandPow = FloatArray(frameCount)
        val frameTotalPow = FloatArray(frameCount)

        // Pass 1: Measure per-frame speech-band & total energy (with zero allocations per frame)
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
            for (b in 1 until numBins) {
                var p = 0f
                for (c in 0 until numCh) {
                    val rv = re[c][b]
                    val iv = im[c][b]
                    p += rv * rv + iv * iv
                }
                p *= invCh
                totalPow += p
                if (b in speechMinBin..speechMaxBin) {
                    speechPow += p
                }
            }
            frameSpeechBandPow[f] = speechPow / max(1, speechMaxBin - speechMinBin + 1)
            frameTotalPow[f] = totalPow / max(1, numBins - 1)
        }

        // Ignore completely silent digital-zero frames so leading/trailing silence never zeros noise profile
        val nonZeroFrames = frameTotalPow.indices.filter { frameTotalPow[it] > 1e-10f }
        if (nonZeroFrames.isEmpty()) {
            return Array(numCh) { c -> channelsPcm[c].clone() }
        }

        val sortedByPow = nonZeroFrames.sortedBy { frameSpeechBandPow[it] }
        val quietCount = max(1, (sortedByPow.size * 0.18f).toInt().coerceAtMost(min(sortedByPow.size, 24)))
        val noisePow = FloatArray(numBins)

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

        val bottomQuietPow = frameSpeechBandPow[sortedByPow[(quietCount - 1).coerceAtLeast(0)]]
        val medianSpeechPow = frameSpeechBandPow[sortedByPow[sortedByPow.size / 2]]
        val ratioQuietToMedian = if (medianSpeechPow > 1e-9f) bottomQuietPow / medianSpeechPow else 1f

        // Protect continuous speech/music (recordings with few/no silent gaps) from vocal hollowing:
        // speech harmonics create sharp peaks across frequency whereas stationary hiss/room noise is smooth,
        // so extract the local spectral valley floor before scaling down.
        val protectedBottomPow: Float
        val effectivePauseFloor: Float
        if (ratioQuietToMedian > 0.35f) {
            val valRadius = max(4, (220f / binHz).toInt())
            val rawNoiseCopy = noisePow.clone()
            val valleyMix = ((ratioQuietToMedian - 0.35f) / 0.35f).coerceIn(0f, 1f)
            val continuousProtection = (1.0f - (ratioQuietToMedian - 0.35f) * 0.90f).coerceIn(0.18f, 1.0f)
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
            val contBlend = ((ratioQuietToMedian - 0.42f) / 0.45f).coerceIn(0f, 1f)
            effectivePauseFloor = pauseFloor + (0.98f - pauseFloor) * contBlend
        } else {
            protectedBottomPow = bottomQuietPow
            effectivePauseFloor = pauseFloor
        }

        // Adaptive tonal hum weighting: detect narrow hum spikes <= 185 Hz vs clean 210-480 Hz floor
        // Keep width tight so 200-250 Hz vocal chest fundamentals are never touched.
        val humStr = config.humRemovalStrength.coerceIn(0f, 1f)
        val humBinAttenFloor = FloatArray(numBins) { 1.0f }
        if (humStr > 0.05f && ratioQuietToMedian < 0.72f) {
            val refStart = max(1, (210f / binHz).toInt())
            val refEnd = min(numBins - 1, (480f / binHz).toInt())
            val refCount = (refEnd - refStart + 1).coerceAtLeast(1)
            val refSlice = FloatArray(refCount) { idx -> noisePow[refStart + idx] }
            refSlice.sort()
            val refBasePow = refSlice[refCount / 2].coerceAtLeast(1e-12f)
            val humHalfWidthHz = max(binHz * 0.58f, 16f)

            val maxHumBin = min(numBins - 1, (188f / binHz).toInt())
            for (b in 1..maxHumBin) {
                val freq = b * binHz
                val isHumBin = freq <= 188f && config.deHumTargetHz.any { abs(freq - it) <= humHalfWidthHz }
                if (isHumBin) {
                    val peakRatio = noisePow[b] / refBasePow
                    if (peakRatio > 1.35f) {
                        val excessWeight = ((peakRatio - 1.35f) / 3.0f).coerceIn(0f, 1f)
                        noisePow[b] *= (1.0f + humStr * (0.90f + excessWeight * 1.4f))
                        humBinAttenFloor[b] = (1.0f - humStr * (0.50f + 0.38f * excessWeight))
                            .coerceIn(0.10f, 1.0f)
                    }
                }
            }
        }

        val speechFloorRef = max(protectedBottomPow * 1.65f, 1e-10f)

        // Decision-Directed Ephraim-Malah + Power-Domain Wiener Pass 2 + OLA
        val outAccum = Array(numCh) { FloatArray(fftSize) }
        val olaNorm = Array(numCh) { FloatArray(fftSize) }
        val output = Array(numCh) { ShortArray(len) }

        val prevGain = FloatArray(numBins) { 1.0f }
        val prevMag = FloatArray(numBins) { 0f }
        val rawBinGain = FloatArray(numBins)
        val freqSmoothedGain = FloatArray(numBins)
        var speechActivitySmoothed = if (ratioQuietToMedian > 0.65f) 1.0f else 0f

        val ddBeta = 0.88f
        val alphaBase = 0.48f + strength * 1.15f

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
            var speechPowSum = 0f
            var speechNoisePowSum = 1e-10f
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
                    speechPowSum += p
                    speechNoisePowSum += noisePow[b]
                }
            }

            // Power-domain SNR handles both dense formants and sparse voiced harmonics accurately
            val framePowerSnr = speechPowSum / speechNoisePowSum
            val instantSpeechProb = when {
                ratioQuietToMedian > 0.72f -> 1.0f
                frameSpeechBandPow[f] < speechFloorRef * 0.45f -> 0f
                framePowerSnr <= 1.45f -> 0f
                framePowerSnr >= 3.20f -> 1f
                else -> ((framePowerSnr - 1.45f) / 1.75f).coerceIn(0f, 1f)
            }
            // Fast onset tracking + slow hangover decay avoids chopping soft consonants/endings
            val actAlpha = if (instantSpeechProb > speechActivitySmoothed) 0.78f else 0.14f
            speechActivitySmoothed += actAlpha * (instantSpeechProb - speechActivitySmoothed)

            // Conservative minimum/pause tracking of stationary background noise
            for (b in 0 until numBins) {
                val curP = powY[b]
                if (curP < noisePow[b] && curP > 1e-12f && ratioQuietToMedian < 0.60f) {
                    noisePow[b] = (0.88f * noisePow[b] + 0.12f * curP).coerceAtLeast(1e-12f)
                } else if (speechActivitySmoothed < 0.18f && curP < noisePow[b] * 2.1f && ratioQuietToMedian < 0.60f) {
                    noisePow[b] = (0.95f * noisePow[b] + 0.05f * curP).coerceAtLeast(1e-12f)
                }
            }

            // Decision-Directed a priori SNR + Power-Domain Wiener gain
            val adaptiveAlpha = alphaBase * (1.15f - 0.42f * speechActivitySmoothed)
            for (b in 0 until numBins) {
                val py = powY[b]
                val pn = noisePow[b] + 1e-12f
                val curMag = sqrt(py)

                val gamma = (py / pn).coerceIn(0f, 120f)
                val prevCleanMag = prevGain[b] * prevMag[b]
                val prevSnr = (prevCleanMag * prevCleanMag) / pn
                val xi = ddBeta * prevSnr + (1f - ddBeta) * max(gamma - 1f, 0f)
                prevMag[b] = curMag

                val wiener = (xi / (xi + adaptiveAlpha)).coerceIn(spectralFloor, 1.0f)

                // Preserve vocal presence & high-frequency consonant air cleanly (no heavy HF muddiness)
                val freqWeight = when {
                    b < lowCutBin -> (1.0f - config.lowCutAttenuation * 0.85f).coerceIn(0.10f, 1.0f)
                    config.preserveHighFrequencies && b <= ultraHighBin -> 1.0f
                    config.preserveHighFrequencies -> 0.88f + 0.12f * speechActivitySmoothed
                    b <= airStartBin -> 0.94f + 0.06f * speechActivitySmoothed
                    else -> 0.82f + 0.16f * speechActivitySmoothed
                }

                // Hum notch deepens during pauses while yielding cleanly to active voice energy
                val dynamicHumFloor = 1.0f - (1.0f - humBinAttenFloor[b]) * (1.0f - 0.65f * speechActivitySmoothed)
                rawBinGain[b] = (wiener * freqWeight * dynamicHumFloor).coerceIn(spectralFloor, 1.0f)
            }

            // 5-bin spectral smoothing eliminates isolated narrow-band chirps ("musical noise")
            for (b in 0 until numBins) {
                val g0 = rawBinGain[max(0, b - 2)]
                val g1 = rawBinGain[max(0, b - 1)]
                val g2 = rawBinGain[b]
                val g3 = rawBinGain[min(numBins - 1, b + 1)]
                val g4 = rawBinGain[min(numBins - 1, b + 2)]
                freqSmoothedGain[b] = 0.10f * g0 + 0.22f * g1 + 0.36f * g2 + 0.22f * g3 + 0.10f * g4
            }

            val pauseMult = effectivePauseFloor + (1.0f - effectivePauseFloor) * speechActivitySmoothed
            val minTarget = min(spectralFloor, effectivePauseFloor)

            for (b in 0 until numBins) {
                val targetGain = (freqSmoothedGain[b] * pauseMult).coerceIn(minTarget, 1.0f)
                val tAlpha = if (targetGain > prevGain[b]) 0.78f else 0.32f
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
                        val s = (acc[k] / norm) * 32767f
                        outCh[sigIdx] = s.toInt().coerceIn(-32767, 32767).toShort()
                    }
                }
                System.arraycopy(acc, hopSize, acc, 0, hopSize)
                System.arraycopy(nrm, hopSize, nrm, 0, hopSize)
                Arrays.fill(acc, hopSize, fftSize, 0f)
                Arrays.fill(nrm, hopSize, fftSize, 0f)
            }
        }

        // Flush remaining OLA samples
        val finalOffset = frameCount * hopSize
        for (c in 0 until numCh) {
            val acc = outAccum[c]
            val nrm = olaNorm[c]
            val outCh = output[c]
            for (k in 0 until hopSize) {
                val sigIdx = finalOffset + k - pad
                if (sigIdx in outCh.indices) {
                    val norm = nrm[k].coerceAtLeast(1e-4f)
                    val s = (acc[k] / norm) * 32767f
                    outCh[sigIdx] = s.toInt().coerceIn(-32767, 32767).toShort()
                }
            }
        }

        return output
    }

    private fun applyVocalClarityPolish(
        samples: ShortArray,
        clarityBoost: Float,
        sampleRate: Int
    ): ShortArray {
        val c = clarityBoost.coerceIn(0f, 1f)
        if (c < 0.05f || samples.size < 32) return samples
        // Gentle presence & upper-air clarity polish preserving sibilance brightness
        val gLowTrim = 1f - 0.10f * c
        val gPresence = 1f + 0.16f * c
        val gAir = 1f + 0.12f * c

        val dt = 1f / sampleRate.toFloat()
        val alphaLow = dt / (1f / (2f * PI.toFloat() * 250f) + dt)
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

        var len = 2
        val sign = if (inverse) 1.0 else -1.0
        while (len <= n) {
            val halfLen = len ushr 1
            val angle = sign * (2.0 * PI / len.toDouble())
            val wStepRe = cos(angle).toFloat()
            val wStepIm = sin(angle).toFloat()

            var start = 0
            while (start < n) {
                var wRe = 1.0f
                var wIm = 0.0f
                for (k in 0 until halfLen) {
                    val i1 = start + k
                    val i2 = i1 + halfLen
                    val tRe = wRe * real[i2] - wIm * imag[i2]
                    val tIm = wRe * imag[i2] + wIm * real[i2]

                    real[i2] = real[i1] - tRe
                    imag[i2] = imag[i1] - tIm
                    real[i1] += tRe
                    imag[i1] += tIm

                    val nextRe = wRe * wStepRe - wIm * wStepIm
                    wIm = wRe * wStepIm + wIm * wStepRe
                    wRe = nextRe
                }
                start += len
            }
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
