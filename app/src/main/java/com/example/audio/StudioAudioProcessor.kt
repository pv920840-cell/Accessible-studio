package com.example.audio

import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

data class CuePoint(
    val id: String = UUID.randomUUID().toString(),
    val positionMs: Long,
    val name: String
)

data class StudioFxSettings(
    // Flanger / Sweeping filter
    val flangerEnabled: Boolean = false,
    val flangerRateHz: Float = 0.5f,
    val flangerDepthMs: Float = 3.0f,
    val flangerFeedback: Float = 0.4f,
    val flangerMix: Float = 0.5f,

    // Chorus
    val chorusEnabled: Boolean = false,
    val chorusRateHz: Float = 1.2f,
    val chorusDepthMs: Float = 6.0f,
    val chorusMix: Float = 0.45f,

    // Distortion / Overdrive
    val distortionEnabled: Boolean = false,
    val distortionDrive: Float = 2.5f,
    val distortionTone: Float = 0.6f,

    // Compressor & Limiter
    val compressorEnabled: Boolean = false,
    val compressorThresholdDb: Float = -12.0f,
    val compressorRatio: Float = 4.0f,
    val compressorMakeupGainDb: Float = 3.0f,
    val peakLimiterEnabled: Boolean = true,
    val peakLimiterCeilingDb: Float = -1.0f,

    // 7-Band Equalizer (gains in dB from -12dB to +12dB)
    val eq7BandEnabled: Boolean = false,
    val eqBand60Hz: Float = 0f,
    val eqBand150Hz: Float = 0f,
    val eqBand400Hz: Float = 0f,
    val eqBand1kHz: Float = 0f,
    val eqBand2_5kHz: Float = 0f,
    val eqBand6kHz: Float = 0f,
    val eqBand15kHz: Float = 0f,

    // Pitch Shifter (semitones from -12 to +12)
    val pitchShiftSemitones: Int = 0,

    // Stereo Pan (-1.0 Left to +1.0 Right)
    val stereoPan: Float = 0.0f,

    // Phase Invert
    val invertPhase: Boolean = false
)

object StudioAudioProcessor {

    const val SAMPLE_RATE = 44100

    /**
     * Accessible Studio Peak Normalization (Maximize Volume):
     * Scans peak amplitude and scales all samples to target ratio (-0.1 dB = ~32380)
     */
    fun normalize(samples: ShortArray, targetRatio: Float = 0.98f): ShortArray {
        if (samples.isEmpty()) return samples
        var peak = 0
        for (s in samples) {
            val a = abs(s.toInt())
            if (a > peak) peak = a
        }
        if (peak == 0 || peak > 32760) return samples

        val target = (32767f * targetRatio.coerceIn(0.1f, 1.0f)).toInt()
        val factor = target.toFloat() / peak.toFloat()
        val output = ShortArray(samples.size)

        for (i in samples.indices) {
            val scaled = (samples[i] * factor).toInt()
            output[i] = scaled.coerceIn(-32767, 32767).toShort()
        }
        return output
    }

    /**
     * Studio Flanger: Sweeping comb filter with variable delay LFO
     */
    fun applyFlanger(
        input: ShortArray,
        rateHz: Float = 0.5f,
        depthMs: Float = 3.0f,
        feedback: Float = 0.4f,
        mix: Float = 0.5f
    ): ShortArray {
        if (input.isEmpty()) return input
        val maxDelaySamples = ((depthMs + 2.0f) * SAMPLE_RATE / 1000f).toInt().coerceAtLeast(10)
        val delayBuffer = FloatArray(maxDelaySamples)
        var writePos = 0
        val output = ShortArray(input.size)

        val lfoInc = 2.0 * PI * rateHz / SAMPLE_RATE
        var lfoPhase = 0.0

        for (i in input.indices) {
            val inSample = input[i].toFloat()

            val currentDelayMs = 1.0 + (depthMs * 0.5 * (1.0 + sin(lfoPhase)))
            val delaySamples = (currentDelayMs * SAMPLE_RATE / 1000.0).toFloat().coerceIn(1f, maxDelaySamples - 2f)
            lfoPhase += lfoInc

            val intDelay = delaySamples.toInt()
            val frac = delaySamples - intDelay
            val readPos0 = (writePos - intDelay + maxDelaySamples) % maxDelaySamples
            val readPos1 = (readPos0 - 1 + maxDelaySamples) % maxDelaySamples

            val delayed = (delayBuffer[readPos0] * (1.0f - frac)) + (delayBuffer[readPos1] * frac)
            val wet = inSample * (1f - mix) + delayed * mix
            output[i] = wet.toInt().coerceIn(-32767, 32767).toShort()

            delayBuffer[writePos] = inSample + (delayed * feedback.coerceIn(0f, 0.85f))
            writePos = (writePos + 1) % maxDelaySamples
        }
        return output
    }

    /**
     * Studio Chorus: Dual-voice warm spatial shimmer
     */
    fun applyChorus(
        input: ShortArray,
        rateHz: Float = 1.2f,
        depthMs: Float = 6.0f,
        mix: Float = 0.45f
    ): ShortArray {
        if (input.isEmpty()) return input
        val maxDelay = ((depthMs + 10f) * SAMPLE_RATE / 1000f).toInt().coerceAtLeast(20)
        val delayBuffer = FloatArray(maxDelay)
        var writePos = 0
        val output = ShortArray(input.size)

        val lfoInc1 = 2.0 * PI * rateHz / SAMPLE_RATE
        val lfoInc2 = 2.0 * PI * (rateHz * 1.35) / SAMPLE_RATE
        var phase1 = 0.0
        var phase2 = PI * 0.5

        for (i in input.indices) {
            val inSample = input[i].toFloat()

            val d1 = 8.0 + (depthMs * sin(phase1))
            val d2 = 12.0 + (depthMs * 0.8 * sin(phase2))
            phase1 += lfoInc1
            phase2 += lfoInc2

            val s1 = (d1 * SAMPLE_RATE / 1000.0).toInt().coerceIn(1, maxDelay - 1)
            val s2 = (d2 * SAMPLE_RATE / 1000.0).toInt().coerceIn(1, maxDelay - 1)

            val read1 = (writePos - s1 + maxDelay) % maxDelay
            val read2 = (writePos - s2 + maxDelay) % maxDelay
            val chorusVoice = (delayBuffer[read1] + delayBuffer[read2]) * 0.5f

            val out = (inSample * (1f - mix)) + (chorusVoice * mix)
            output[i] = out.toInt().coerceIn(-32767, 32767).toShort()

            delayBuffer[writePos] = inSample
            writePos = (writePos + 1) % maxDelay
        }
        return output
    }

    /**
     * Studio Distortion / Overdrive with soft clipping saturation
     */
    fun applyDistortion(input: ShortArray, drive: Float = 2.5f, tone: Float = 0.6f): ShortArray {
        if (input.isEmpty()) return input
        val output = ShortArray(input.size)
        val driveGain = drive.coerceIn(1.0f, 10.0f)
        var lowpass = 0f
        val toneFilter = tone.coerceIn(0.1f, 0.95f)

        for (i in input.indices) {
            val norm = (input[i].toFloat() / 32767f) * driveGain
            val saturated = (atan(norm * 1.8) / 1.15).toFloat().coerceIn(-1.0f, 1.0f)
            lowpass += toneFilter * (saturated - lowpass)
            output[i] = (lowpass * 31500f).toInt().coerceIn(-32767, 32767).toShort()
        }
        return output
    }

    /**
     * Studio Dynamics Compressor & Peak Limiter
     */
    fun applyCompressor(
        input: ShortArray,
        thresholdDb: Float = -12.0f,
        ratio: Float = 4.0f,
        makeupGainDb: Float = 3.0f,
        peakLimiter: Boolean = true
    ): ShortArray {
        if (input.isEmpty()) return input
        val threshLinear = 10.0.pow(thresholdDb / 20.0).toFloat() * 32767f
        val makeupGain = 10.0.pow(makeupGainDb / 20.0).toFloat()
        val output = ShortArray(input.size)
        var envelope = 0f

        val attackAlpha = 0.1f
        val releaseAlpha = 0.001f

        for (i in input.indices) {
            val s = input[i].toFloat()
            val absSample = abs(s)

            envelope = if (absSample > envelope) {
                envelope * (1f - attackAlpha) + absSample * attackAlpha
            } else {
                envelope * (1f - releaseAlpha) + absSample * releaseAlpha
            }

            var gain = 1.0f
            if (envelope > threshLinear) {
                val excess = envelope - threshLinear
                val compressedExcess = excess / ratio
                val targetLevel = threshLinear + compressedExcess
                gain = targetLevel / envelope
            }

            var out = s * gain * makeupGain

            if (peakLimiter) {
                if (abs(out) > 32000f) {
                    out = (atan(out / 32000f) * 32000f)
                }
            }

            output[i] = out.toInt().coerceIn(-32767, 32767).toShort()
        }
        return output
    }

    /**
     * Studio 7-Band Graphic Equalizer
     */
    fun apply7BandEq(
        input: ShortArray,
        b60: Float,
        b150: Float,
        b400: Float,
        b1k: Float,
        b2_5k: Float,
        b6k: Float,
        b15k: Float
    ): ShortArray {
        val g60 = 10f.pow(b60 / 20f)
        val g150 = 10f.pow(b150 / 20f)
        val g400 = 10f.pow(b400 / 20f)
        val g1k = 10f.pow(b1k / 20f)
        val g2_5k = 10f.pow(b2_5k / 20f)
        val g6k = 10f.pow(b6k / 20f)
        val g15k = 10f.pow(b15k / 20f)

        if (b60 == 0f && b150 == 0f && b400 == 0f && b1k == 0f && b2_5k == 0f && b6k == 0f && b15k == 0f) {
            return input
        }

        val output = ShortArray(input.size)
        var sLow = 0f
        var sMidLow = 0f
        var sMid = 0f
        var sMidHigh = 0f
        var sHigh = 0f

        for (i in input.indices) {
            val x = input[i].toFloat()

            sLow += 0.015f * (x - sLow)       // ~100Hz
            sMidLow += 0.06f * (x - sMidLow)   // ~400Hz
            sMid += 0.15f * (x - sMid)         // ~1kHz
            sMidHigh += 0.35f * (x - sMidHigh) // ~3kHz
            sHigh += 0.65f * (x - sHigh)       // ~8kHz

            val bandSub = sLow
            val bandLow = sMidLow - sLow
            val bandMid = sMid - sMidLow
            val bandHighMid = sMidHigh - sMid
            val bandHigh = sHigh - sMidHigh
            val bandAir = x - sHigh

            val eqOut = (bandSub * ((g60 + g150) * 0.5f)) +
                    (bandLow * g400) +
                    (bandMid * g1k) +
                    (bandHighMid * g2_5k) +
                    (bandHigh * g6k) +
                    (bandAir * g15k)

            output[i] = eqOut.toInt().coerceIn(-32767, 32767).toShort()
        }
        return output
    }

    /**
     * Pitch Shifter by Semitones (-12 to +12)
     */
    fun applyPitchShift(input: ShortArray, semitones: Int): ShortArray {
        if (input.isEmpty() || semitones == 0) return input
        val factor = 2.0.pow(semitones / 12.0).toFloat()
        val newSize = (input.size / factor).toInt().coerceAtLeast(1)
        val output = ShortArray(newSize)

        for (i in 0 until newSize) {
            val origPos = i * factor
            val idx0 = origPos.toInt().coerceIn(0, input.size - 1)
            val idx1 = (idx0 + 1).coerceIn(0, input.size - 1)
            val frac = origPos - idx0
            val s = (input[idx0] * (1.0 - frac) + input[idx1] * frac).toInt()
            output[i] = s.coerceIn(-32767, 32767).toShort()
        }
        return output
    }

    /**
     * Studio Silence Insertion: Inserts durationSec of zeros at cursor position
     */
    fun insertSilence(original: ShortArray, positionMs: Long, durationSec: Int): ShortArray {
        val samplePos = ((positionMs.toDouble() / 1000.0) * SAMPLE_RATE).toInt().coerceIn(0, original.size)
        val silenceSamples = durationSec * SAMPLE_RATE
        val output = ShortArray(original.size + silenceSamples)

        System.arraycopy(original, 0, output, 0, samplePos)
        System.arraycopy(original, samplePos, output, samplePos + silenceSamples, original.size - samplePos)
        return output
    }

    /**
     * Studio Audio Generator: Synthesizes Pure Sine wave, Square, or Noise
     */
    fun generateTone(type: String, frequencyHz: Float, durationSec: Int): ShortArray {
        val count = SAMPLE_RATE * durationSec
        val samples = ShortArray(count)

        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val s = when (type.lowercase()) {
                "square" -> if (sin(2.0 * PI * frequencyHz * t) >= 0) 22000 else -22000
                "noise" -> ((Math.random() * 2.0 - 1.0) * 18000).toInt()
                else -> (sin(2.0 * PI * frequencyHz * t) * 24000).toInt() // sine
            }
            samples[i] = s.coerceIn(-32767, 32767).toShort()
        }
        return samples
    }

    /**
     * Studio Selection Mute (Overwrites PCM samples inside selection with silence without changing track length)
     */
    fun muteSelection(
        samples: ShortArray,
        startMs: Long,
        endMs: Long,
        sampleRate: Int = SAMPLE_RATE
    ): ShortArray {
        if (samples.isEmpty() || endMs <= startMs) return samples
        val output = samples.clone()
        val safeRate = sampleRate.coerceAtLeast(8000)
        val startSample = ((startMs.toDouble() / 1000.0) * safeRate).toInt().coerceIn(0, output.size)
        val endSample = ((endMs.toDouble() / 1000.0) * safeRate).toInt().coerceIn(startSample, output.size)

        for (i in startSample until endSample) {
            output[i] = 0
        }
        return output
    }

    /**
     * Studio Selection Delete (Removes selected segment and closes the gap; returns empty ShortArray(0) if entire track was selected)
     */
    fun deleteSelection(samples: ShortArray, startMs: Long, endMs: Long): ShortArray {
        if (samples.isEmpty()) return samples
        val startSample = ((startMs.toDouble() / 1000.0) * SAMPLE_RATE).toInt().coerceIn(0, samples.size)
        val endSample = ((endMs.toDouble() / 1000.0) * SAMPLE_RATE).toInt().coerceIn(startSample, samples.size)
        val cutCount = endSample - startSample
        val newLength = (samples.size - cutCount).coerceAtLeast(0)
        if (newLength == 0) return ShortArray(0)

        val output = ShortArray(newLength)
        System.arraycopy(samples, 0, output, 0, startSample)
        if (endSample < samples.size) {
            System.arraycopy(samples, endSample, output, startSample, samples.size - endSample)
        }
        return output
    }

    /**
     * Studio Crop / Trim to selection (Keeps only inside selection)
     */
    fun cropToSelection(samples: ShortArray, startMs: Long, endMs: Long, sampleRate: Int = SAMPLE_RATE): ShortArray {
        if (samples.isEmpty()) return ShortArray(0)
        val safeSr = sampleRate.coerceAtLeast(8000)
        val startSample = ((startMs.toDouble() / 1000.0) * safeSr).toInt().coerceIn(0, samples.size)
        val endSample = ((endMs.toDouble() / 1000.0) * safeSr).toInt().coerceIn(startSample, samples.size)
        val len = endSample - startSample
        if (len <= 0) return ShortArray(0)
        val output = ShortArray(len)
        System.arraycopy(samples, startSample, output, 0, len)
        return output
    }

    /**
     * Studio Fade In across selected region
     */
    fun fadeInSelection(samples: ShortArray, startMs: Long, endMs: Long): ShortArray {
        val output = samples.clone()
        val startSample = ((startMs.toDouble() / 1000.0) * SAMPLE_RATE).toInt().coerceIn(0, output.size)
        val endSample = ((endMs.toDouble() / 1000.0) * SAMPLE_RATE).toInt().coerceIn(startSample, output.size)
        val range = (endSample - startSample).coerceAtLeast(1)

        for (i in startSample until endSample) {
            val factor = (i - startSample).toFloat() / range.toFloat()
            output[i] = (output[i] * factor).toInt().coerceIn(-32767, 32767).toShort()
        }
        return output
    }

    /**
     * Studio Fade Out across selected region
     */
    fun fadeOutSelection(samples: ShortArray, startMs: Long, endMs: Long): ShortArray {
        val output = samples.clone()
        val startSample = ((startMs.toDouble() / 1000.0) * SAMPLE_RATE).toInt().coerceIn(0, output.size)
        val endSample = ((endMs.toDouble() / 1000.0) * SAMPLE_RATE).toInt().coerceIn(startSample, output.size)
        val range = (endSample - startSample).coerceAtLeast(1)

        for (i in startSample until endSample) {
            val factor = (endSample - i).toFloat() / range.toFloat()
            output[i] = (output[i] * factor).toInt().coerceIn(-32767, 32767).toShort()
        }
        return output
    }

    /**
     * Studio Gain Adjustment (+/- dB) across selection or entire track
     */
    fun applyGainDb(samples: ShortArray, gainDb: Float, startMs: Long? = null, endMs: Long? = null): ShortArray {
        val output = samples.clone()
        val factor = 10.0.pow(gainDb / 20.0).toFloat()
        val startSample = if (startMs != null) ((startMs.toDouble() / 1000.0) * SAMPLE_RATE).toInt().coerceIn(0, output.size) else 0
        val endSample = if (endMs != null) ((endMs.toDouble() / 1000.0) * SAMPLE_RATE).toInt().coerceIn(startSample, output.size) else output.size

        for (i in startSample until endSample) {
            val amplified = (output[i] * factor).toInt()
            output[i] = amplified.coerceIn(-32767, 32767).toShort()
        }
        return output
    }

    /**
     * Studio Mix Paste (Overdub blend clipboard with current audio at cursor)
     */
    fun mixPasteAtCursor(original: ShortArray, clip: ShortArray, positionMs: Long, clipVolume: Float = 1.0f): ShortArray {
        return mixAudioTracks(
            main = original,
            overlay = clip,
            startMs = positionMs,
            mainVolume = 1.0f,
            overlayVolume = clipVolume,
            applyPeakLimiter = false,
            sampleRate = SAMPLE_RATE
        )
    }

    /**
     * Mixes two PCM audio buffers with independent gain controls and optional anti-clip peak limiting.
     */
    fun mixAudioTracks(
        main: ShortArray,
        overlay: ShortArray,
        startMs: Long = 0L,
        mainVolume: Float = 1.0f,
        overlayVolume: Float = 0.85f,
        applyPeakLimiter: Boolean = true,
        sampleRate: Int = SAMPLE_RATE
    ): ShortArray {
        if (overlay.isEmpty() && main.isEmpty()) return ShortArray(0)
        val safeSr = sampleRate.coerceAtLeast(8000)
        val posSample = ((startMs.toDouble() / 1000.0) * safeSr).toInt().coerceIn(0, main.size)
        val neededLength = max(main.size, posSample + overlay.size)
        val output = ShortArray(neededLength)

        val safeMainVol = mainVolume.coerceIn(0f, 2.0f)
        val safeOverlayVol = overlayVolume.coerceIn(0f, 2.0f)

        for (i in 0 until neededLength) {
            val baseVal = if (i < main.size) main[i] * safeMainVol else 0f
            val overIdx = i - posSample
            val overlayVal = if (overIdx in overlay.indices) overlay[overIdx] * safeOverlayVol else 0f
            output[i] = (baseVal + overlayVal).toInt().coerceIn(-32767, 32767).toShort()
        }

        return if (applyPeakLimiter && output.isNotEmpty()) {
            applyTruePeakLimiter(output, -0.5f)
        } else {
            output
        }
    }

    /**
     * Studio Audio Statistics inspection (Peak dBFS, RMS, Duration, Clipping)
     */
    data class AudioStats(
        val peakDb: Float,
        val rmsDb: Float,
        val peakValue: Int,
        val durationSeconds: Float,
        val sampleCount: Int,
        val isClipped: Boolean
    )

    fun computeStatistics(samples: ShortArray): AudioStats {
        if (samples.isEmpty()) return AudioStats(-100f, -100f, 0, 0f, 0, false)
        var peak = 0
        var sumSquares = 0.0

        for (s in samples) {
            val a = abs(s.toInt())
            if (a > peak) peak = a
            sumSquares += (s.toDouble() * s.toDouble())
        }

        val rms = kotlin.math.sqrt(sumSquares / samples.size)
        val peakDb = if (peak > 0) (20.0 * kotlin.math.log10(peak / 32767.0)).toFloat().coerceAtLeast(-96f) else -96f
        val rmsDb = if (rms > 0) (20.0 * kotlin.math.log10(rms / 32767.0)).toFloat().coerceAtLeast(-96f) else -96f
        val durSec = samples.size.toFloat() / SAMPLE_RATE

        return AudioStats(
            peakDb = peakDb,
            rmsDb = rmsDb,
            peakValue = peak,
            durationSeconds = durSec,
            sampleCount = samples.size,
            isClipped = peak >= 32760
        )
    }

    /**
     * Remove DC Offset (centers waveform back to true electrical zero)
     */
    fun removeDcOffset(samples: ShortArray): ShortArray {
        if (samples.isEmpty()) return samples
        val output = ShortArray(samples.size)
        var mean = 0.0
        for (s in samples) {
            mean += s.toDouble()
        }
        val offset = (mean / samples.size.toDouble()).toInt()
        for (i in samples.indices) {
            output[i] = (samples[i] - offset).coerceIn(-32767, 32767).toShort()
        }
        return output
    }

    /**
     * De-Click & De-Crackle Filter:
     * Detects discontinuous digital impulses, pops, buffer dropouts, and clicks
     * and reconstructs the sample using smooth linear/cubic interpolation.
     */
    fun deClickDeCrackle(samples: ShortArray, sensitivityThreshold: Int = 4200): ShortArray {
        if (samples.size < 4) return samples
        val output = samples.clone()

        for (i in 2 until output.size - 2) {
            val prev = output[i - 1].toInt()
            val curr = output[i].toInt()
            val next = output[i + 1].toInt()

            // Predicted value based on linear slope
            val predicted = (prev + next) / 2
            val diff = abs(curr - predicted)

            // Neighbor trend smoothness check
            val stepBefore = abs(curr - prev)
            val stepAfter = abs(next - curr)
            val backgroundSlope = abs(output[i - 1] - output[i - 2]) + abs(output[i + 2] - output[i + 1])

            // If sample is an isolated spike exceeding slope by threshold
            if (diff > sensitivityThreshold && (stepBefore + stepAfter) > (backgroundSlope * 2 + 3000)) {
                // Smoothly replace click with interpolated value
                output[i] = predicted.coerceIn(-32767, 32767).toShort()
            }
        }
        return output
    }

    /**
     * True Peak Limiter (-1.0 dBFS ceiling):
     * Prevents any clipping or inter-sample digital distortion.
     * Peak maximum ceiling = 32767 * 10^(-1/20) = ~29205.
     */
    fun applyTruePeakLimiter(samples: ShortArray, ceilingDb: Float = -1.0f): ShortArray {
        if (samples.isEmpty()) return samples
        val output = ShortArray(samples.size)
        val maxAllowed = (32767f * 10.0.pow(ceilingDb / 20.0)).toFloat().coerceIn(1000f, 32767f)
        val kneeStart = maxAllowed * 0.90f
        val kneeRange = (maxAllowed - kneeStart).coerceAtLeast(1f)
        val twoOverPi = (2.0 / PI).toFloat()

        for (i in samples.indices) {
            val s = samples[i].toFloat()
            val absVal = abs(s)
            val limited = if (absVal > kneeStart) {
                val excess = absVal - kneeStart
                val compressed = kneeStart + kneeRange * (atan(excess / kneeRange) * twoOverPi)
                if (s > 0) compressed else -compressed
            } else {
                s
            }
            output[i] = limited.toInt().coerceIn(-32767, 32767).toShort()
        }
        return output
    }

    /**
     * Gentle Cleanup (Auto applied after recording):
     * Combines DC offset removal, de-clicking, smooth noise suppression,
     * and -1dB peak limiting while preserving 100% natural vocal timbre.
     */
    fun gentleCleanup(samples: ShortArray): ShortArray {
        if (samples.isEmpty()) return samples
        // 1. Remove DC electrical offset
        val zeroed = removeDcOffset(samples)
        // 2. Remove clicks and crackles
        val declicked = deClickDeCrackle(zeroed)
        // 3. Prevent clipping with -1.0 dB limiter
        return applyTruePeakLimiter(declicked, -1.0f)
    }

    /**
     * Complete Studio FX pipeline application
     */
    fun applyStudioRack(rawPcm: ShortArray, settings: StudioFxSettings): ShortArray {
        if (rawPcm.isEmpty()) return rawPcm
        var pcm = rawPcm

        // Invert phase
        if (settings.invertPhase) {
            val inv = ShortArray(pcm.size)
            for (i in pcm.indices) {
                inv[i] = (-pcm[i]).coerceIn(-32767, 32767).toShort()
            }
            pcm = inv
        }

        // 7-Band Equalizer
        if (settings.eq7BandEnabled) {
            pcm = apply7BandEq(
                pcm,
                settings.eqBand60Hz,
                settings.eqBand150Hz,
                settings.eqBand400Hz,
                settings.eqBand1kHz,
                settings.eqBand2_5kHz,
                settings.eqBand6kHz,
                settings.eqBand15kHz
            )
        }

        // Distortion / Overdrive
        if (settings.distortionEnabled) {
            pcm = applyDistortion(pcm, settings.distortionDrive, settings.distortionTone)
        }

        // Flanger
        if (settings.flangerEnabled) {
            pcm = applyFlanger(pcm, settings.flangerRateHz, settings.flangerDepthMs, settings.flangerFeedback, settings.flangerMix)
        }

        // Chorus
        if (settings.chorusEnabled) {
            pcm = applyChorus(pcm, settings.chorusRateHz, settings.chorusDepthMs, settings.chorusMix)
        }

        // Dynamics Compressor
        if (settings.compressorEnabled) {
            pcm = applyCompressor(
                pcm,
                settings.compressorThresholdDb,
                settings.compressorRatio,
                settings.compressorMakeupGainDb,
                peakLimiter = false
            )
        }

        // Dedicated Peak Limiter
        if (settings.peakLimiterEnabled) {
            pcm = applyTruePeakLimiter(pcm, settings.peakLimiterCeilingDb)
        }

        // Pitch Shift
        if (settings.pitchShiftSemitones != 0) {
            pcm = applyPitchShift(pcm, settings.pitchShiftSemitones)
        }

        return pcm
    }

    /**
     * Studio High-Pass Filter (removes low rumble below cutoffHz)
     */
    fun applyHighPassFilter(input: ShortArray, cutoffHz: Float = 300f): ShortArray {
        if (input.isEmpty()) return input
        val rc = 1.0f / (2.0f * Math.PI.toFloat() * cutoffHz.coerceIn(20f, 10000f))
        val dt = 1.0f / SAMPLE_RATE
        val alpha = rc / (rc + dt)
        val output = ShortArray(input.size)
        var prevIn = 0f
        var prevOut = 0f
        for (i in input.indices) {
            val currIn = input[i].toFloat()
            val currOut = alpha * (prevOut + currIn - prevIn)
            output[i] = currOut.toInt().coerceIn(-32767, 32767).toShort()
            prevIn = currIn
            prevOut = currOut
        }
        return output
    }

    /**
     * Studio Low-Pass Filter (removes harsh high frequencies above cutoffHz)
     */
    fun applyLowPassFilter(input: ShortArray, cutoffHz: Float = 3000f): ShortArray {
        if (input.isEmpty()) return input
        val rc = 1.0f / (2.0f * Math.PI.toFloat() * cutoffHz.coerceIn(100f, 20000f))
        val dt = 1.0f / SAMPLE_RATE
        val alpha = dt / (rc + dt)
        val output = ShortArray(input.size)
        var prevOut = 0f
        for (i in input.indices) {
            val currIn = input[i].toFloat()
            val currOut = prevOut + alpha * (currIn - prevOut)
            output[i] = currOut.toInt().coerceIn(-32767, 32767).toShort()
            prevOut = currOut
        }
        return output
    }

    /**
     * Studio Reverse Audio
     */
    fun reverseAudio(input: ShortArray): ShortArray {
        if (input.isEmpty()) return input
        val output = ShortArray(input.size)
        val lastIdx = input.size - 1
        for (i in input.indices) {
            output[i] = input[lastIdx - i]
        }
        return output
    }

    /**
     * RMS Normalization: Scales audio based on Root Mean Square energy level
     */
    fun normalizeRms(samples: ShortArray, targetRmsDb: Float = -16.0f): ShortArray {
        if (samples.isEmpty()) return samples
        var sumSquares = 0.0
        for (s in samples) {
            val v = s.toDouble()
            sumSquares += v * v
        }
        val currentRms = Math.sqrt(sumSquares / samples.size)
        if (currentRms < 1.0) return samples

        val targetRms = 32767.0 * Math.pow(10.0, targetRmsDb / 20.0)
        val gain = (targetRms / currentRms).toFloat().coerceIn(0.1f, 10.0f)
        val output = ShortArray(samples.size)
        for (i in samples.indices) {
            val scaled = (samples[i] * gain).toInt()
            output[i] = scaled.coerceIn(-32767, 32767).toShort()
        }
        return applyTruePeakLimiter(output, -0.5f)
    }

    /**
     * Auto Gain Leveler:
     * Dynamically tracks short-term RMS energy with lookahead smoothing and adjusts gain
     * smoothly up or down so quiet and loud sections match a consistent loudness level
     * without pumping or amplifying background silence.
     */
    fun applyAutoGain(
        samples: ShortArray,
        targetRmsDb: Float = -14.0f,
        maxBoostDb: Float = 18.0f,
        maxCutDb: Float = 18.0f,
        noiseFloorDb: Float = -50.0f,
        sampleRate: Int = SAMPLE_RATE
    ): ShortArray {
        if (samples.isEmpty()) return samples
        val hop = (sampleRate * 0.020).toInt().coerceIn(16, samples.size.coerceAtLeast(16))
        val numFrames = ((samples.size + hop - 1) / hop).coerceAtLeast(1)

        val frameSqSum = DoubleArray(numFrames)
        val frameCount = IntArray(numFrames)
        for (f in 0 until numFrames) {
            val start = f * hop
            val end = min(start + hop, samples.size)
            var sumSq = 0.0
            for (i in start until end) {
                val v = samples[i].toDouble()
                sumSq += v * v
            }
            frameSqSum[f] = sumSq
            frameCount[f] = end - start
        }

        val targetRms = 32767.0 * 10.0.pow((targetRmsDb / 20.0).toDouble())
        val noiseFloorLinear = 32767.0 * 10.0.pow((noiseFloorDb / 20.0).toDouble())
        val maxGain = 10.0.pow((maxBoostDb / 20.0).toDouble()).toFloat().coerceAtLeast(1.0f)
        val minGain = 10.0.pow((-maxCutDb / 20.0).toDouble()).toFloat().coerceIn(0.05f, 1.0f)

        val windowRadius = 5
        val rawGain = FloatArray(numFrames)
        for (f in 0 until numFrames) {
            val wStart = (f - windowRadius).coerceAtLeast(0)
            val wEnd = (f + windowRadius).coerceAtMost(numFrames - 1)
            var winSq = 0.0
            var winN = 0
            for (k in wStart..wEnd) {
                winSq += frameSqSum[k]
                winN += frameCount[k]
            }
            val rms = if (winN > 0) kotlin.math.sqrt(winSq / winN) else 0.0
            rawGain[f] = if (rms > noiseFloorLinear) {
                (targetRms / rms).toFloat().coerceIn(minGain, maxGain)
            } else {
                1.0f
            }
        }

        // Bidirectional lookahead smoothing so transients lower gain smoothly before peaks arrive
        val smoothed = FloatArray(numFrames)
        var g = rawGain[0]
        for (f in 0 until numFrames) {
            val target = rawGain[f]
            val alpha = if (target < g) 0.55f else 0.18f
            g += alpha * (target - g)
            smoothed[f] = g
        }
        g = smoothed[numFrames - 1]
        for (f in numFrames - 1 downTo 0) {
            val target = smoothed[f]
            val alpha = if (target < g) 0.55f else 0.25f
            g += alpha * (target - g)
            smoothed[f] = g
        }

        val output = ShortArray(samples.size)
        for (f in 0 until numFrames) {
            val start = f * hop
            val end = min(start + hop, samples.size)
            val span = (end - start).coerceAtLeast(1)
            val g0 = smoothed[f]
            val g1 = smoothed[(f + 1).coerceAtMost(numFrames - 1)]
            val step = (g1 - g0) / span.toFloat()
            var curGain = g0
            for (i in start until end) {
                val scaled = (samples[i] * curGain).toInt()
                output[i] = scaled.coerceIn(-32767, 32767).toShort()
                curGain += step
            }
        }
        return applyTruePeakLimiter(output, -0.5f)
    }

    /**
     * Band-Pass Filter: Preserves frequencies within centerHz +/- (bandwidthHz/2)
     */
    fun applyBandPassFilter(input: ShortArray, centerHz: Float = 1000f, bandwidthHz: Float = 800f): ShortArray {
        val lowCut = (centerHz - bandwidthHz / 2f).coerceAtLeast(40f)
        val highCut = (centerHz + bandwidthHz / 2f).coerceAtMost(18000f)
        val hp = applyHighPassFilter(input, lowCut)
        return applyLowPassFilter(hp, highCut)
    }

    /**
     * Bandstop / Notch Filter: Rejects narrow frequency notch (e.g. 50Hz/60Hz mains hum)
     */
    fun applyNotchFilter(input: ShortArray, notchHz: Float = 60f, bandwidthHz: Float = 25f): ShortArray {
        if (input.isEmpty()) return input
        val lowCut = (notchHz - bandwidthHz / 2f).coerceAtLeast(20f)
        val highCut = (notchHz + bandwidthHz / 2f).coerceAtMost(20000f)
        val lowPassed = applyLowPassFilter(input, lowCut)
        val highPassed = applyHighPassFilter(input, highCut)
        val output = ShortArray(input.size)
        for (i in input.indices) {
            val sum = (lowPassed[i].toInt() + highPassed[i].toInt()).coerceIn(-32767, 32767)
            output[i] = sum.toShort()
        }
        return output
    }

    /**
     * Aggressive Hum Removal: Cleans 50Hz/60Hz electrical ground loop hum and harmonics
     */
    fun removeMainsHum(input: ShortArray, is50Hz: Boolean = false): ShortArray {
        val base = if (is50Hz) 50f else 60f
        var pcm = applyNotchFilter(input, base, 15f)
        pcm = applyNotchFilter(pcm, base * 2f, 20f)
        return applyNotchFilter(pcm, base * 3f, 25f)
    }

    /**
     * Mechanize / Ring Modulation: Applies carrier frequency modulation for metallic/robotic timbre
     */
    fun applyMechanize(input: ShortArray, carrierHz: Float = 120f, depth: Float = 0.85f): ShortArray {
        val output = ShortArray(input.size)
        val modDepth = depth.coerceIn(0.1f, 1.0f)
        for (i in input.indices) {
            val t = i.toDouble() / SAMPLE_RATE
            val carrier = sin(2.0 * PI * carrierHz * t).toFloat()
            val modFactor = (1.0f - modDepth) + (modDepth * (0.5f + 0.5f * carrier))
            val s = (input[i] * modFactor).toInt().coerceIn(-32767, 32767)
            output[i] = s.toShort()
        }
        return output
    }

    /**
     * Dynamic Expander: Downward expansion enhancing dynamic contrast
     */
    fun applyExpander(input: ShortArray, thresholdDb: Float = -28.0f, ratio: Float = 2.0f): ShortArray {
        val output = ShortArray(input.size)
        val threshold = (32767.0 * Math.pow(10.0, thresholdDb / 20.0)).toFloat().coerceAtLeast(200f)
        for (i in input.indices) {
            val absVal = abs(input[i].toInt()).toFloat()
            if (absVal < threshold && absVal > 1f) {
                val diff = threshold - absVal
                val expandedDiff = diff * ratio
                val target = (threshold - expandedDiff).coerceAtLeast(0f)
                val gain = (target / absVal).coerceIn(0f, 1f)
                output[i] = (input[i] * gain).toInt().coerceIn(-32767, 32767).toShort()
            } else {
                output[i] = input[i]
            }
        }
        return output
    }
}
