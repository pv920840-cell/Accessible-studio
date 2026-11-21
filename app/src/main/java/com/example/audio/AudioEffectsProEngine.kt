package com.example.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Accessible Studio Pro DSP Engine
 * Provides comprehensive on-device Audio Effects, Channel Processing,
 * Precision Filters, and Volume Mastering tools completely free & offline.
 */
object AudioEffectsProEngine {

    const val SAMPLE_RATE = 44100

    enum class CensorMode(val displayName: String) {
        BLEEP_1KHZ("1 kHz Tone Bleep"),
        REVERSE_SCRUB("Reverse Scramble"),
        SILENCE("Mute / Silence")
    }

    enum class VolumeShapeType(val displayName: String) {
        ARCH_PEAK("Arch Peak"),
        DUCK_VALLEY("Duck Valley (Dip)"),
        RAMP_UP("Ramp Up"),
        RAMP_DOWN("Ramp Down"),
        PULSE_SWELL("Pulse Swell")
    }

    private fun rangeIndices(size: Int, startMs: Long, endMs: Long, sampleRate: Int = SAMPLE_RATE): Pair<Int, Int> {
        val start = ((startMs.toDouble() / 1000.0) * sampleRate).toInt().coerceIn(0, size)
        val end = ((endMs.toDouble() / 1000.0) * sampleRate).toInt().coerceIn(start, size)
        return if (end - start >= 64) Pair(start, end) else Pair(0, size)
    }

    // ==================== EFFECT CATEGORY ====================

    /**
     * Replaces selected segment with 1000 Hz censor bleep, scrambled audio, or clean silence.
     */
    fun censorSelection(
        samples: ShortArray,
        startMs: Long,
        endMs: Long,
        mode: CensorMode = CensorMode.BLEEP_1KHZ,
        sampleRate: Int = SAMPLE_RATE
    ): ShortArray {
        if (samples.isEmpty()) return samples
        val (startIdx, endIdx) = rangeIndices(samples.size, startMs, endMs, sampleRate)
        val out = samples.clone()
        val fadeSamples = min(256, (endIdx - startIdx) / 4)

        when (mode) {
            CensorMode.BLEEP_1KHZ -> {
                for (i in startIdx until endIdx) {
                    val t = (i - startIdx).toDouble() / sampleRate
                    val env = when {
                        i - startIdx < fadeSamples -> (i - startIdx).toFloat() / fadeSamples
                        endIdx - i < fadeSamples -> (endIdx - i).toFloat() / fadeSamples
                        else -> 1f
                    }
                    out[i] = (sin(2.0 * PI * 1000.0 * t) * 18000.0 * env).toInt().coerceIn(-32767, 32767).toShort()
                }
            }
            CensorMode.REVERSE_SCRUB -> {
                val len = endIdx - startIdx
                val chunk = min(1400, len / 2).coerceAtLeast(1)
                var pos = startIdx
                while (pos < endIdx) {
                    val blockEnd = min(pos + chunk, endIdx)
                    for (k in pos until blockEnd) {
                        val mirrored = blockEnd - 1 - (k - pos)
                        out[k] = (samples[mirrored] * 0.9f).toInt().toShort()
                    }
                    pos += chunk
                }
            }
            CensorMode.SILENCE -> {
                for (i in startIdx until endIdx) {
                    out[i] = 0
                }
            }
        }
        return out
    }

    /**
     * Simulates a Doppler pitch + proximity shift across selection or track.
     */
    fun applyDoppler(
        samples: ShortArray,
        startMs: Long,
        endMs: Long,
        intensity: Float = 0.7f,
        sampleRate: Int = SAMPLE_RATE
    ): ShortArray {
        if (samples.size < 256) return samples
        val (startIdx, endIdx) = rangeIndices(samples.size, startMs, endMs, sampleRate)
        val out = samples.clone()
        val span = max(1, endIdx - startIdx)
        val maxDelaySamples = (0.045f * intensity * sampleRate).toInt().coerceIn(64, 4096)

        for (i in startIdx until endIdx) {
            val norm = (i - startIdx).toFloat() / span.toFloat() // 0..1
            // Delay sweeps smoothly like object passing listener (closer at 0.5)
            val delayOffset = ((norm - 0.5f) * (norm - 0.5f) * 4.0f) * maxDelaySamples
            val srcPos = (i - delayOffset).coerceIn(0f, (samples.size - 2).toFloat())
            val idx0 = srcPos.toInt()
            val frac = srcPos - idx0
            val interp = samples[idx0] * (1f - frac) + samples[idx0 + 1] * frac
            val proximityGain = 0.55f + 0.55f * (1f - abs(norm - 0.5f) * 2f)
            out[i] = (interp * proximityGain).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    /**
     * Envelope dynamics curve: lifts quiet details and gently tames peaks.
     */
    fun applyDynamicsCurve(
        samples: ShortArray,
        quietLift: Float = 0.45f,
        peakCompression: Float = 0.5f
    ): ShortArray {
        if (samples.isEmpty()) return samples
        val out = ShortArray(samples.size)
        var env = 0f
        for (i in samples.indices) {
            val s = samples[i].toFloat()
            val mag = abs(s) / 32767f
            env = if (mag > env) 0.15f * mag + 0.85f * env else 0.002f * mag + 0.998f * env
            val gain = when {
                env < 0.12f && env > 0.004f -> 1f + quietLift * 1.4f
                env > 0.55f -> 1f - peakCompression * ((env - 0.55f) / env) * 0.65f
                else -> 1f
            }
            out[i] = (s * gain).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    /**
     * Frequency Blend: fuses warm low-harmonic octave body with crisp upper presence.
     */
    fun applyFrequencyBlend(samples: ShortArray, blendRatio: Float = 0.35f): ShortArray {
        if (samples.size < 4) return samples
        val out = ShortArray(samples.size)
        var lp = 0f
        for (i in samples.indices) {
            val dry = samples[i].toFloat()
            lp = 0.08f * dry + 0.92f * lp
            val subOctave = samples[(i / 2)].toFloat() * 0.6f
            val blended = dry * (1f - blendRatio * 0.35f) + (lp * 0.5f + subOctave * 0.5f) * blendRatio
            out[i] = blended.toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    /**
     * Smoothly interpolates waveform samples across selection boundary points to heal harsh dropouts.
     */
    fun interpolateSelection(
        samples: ShortArray,
        startMs: Long,
        endMs: Long,
        sampleRate: Int = SAMPLE_RATE
    ): ShortArray {
        if (samples.size < 64) return samples
        val (startIdx, endIdx) = rangeIndices(samples.size, startMs, endMs, sampleRate)
        val out = samples.clone()
        val sVal = samples[max(0, startIdx - 1)].toFloat()
        val eVal = samples[min(samples.size - 1, endIdx)].toFloat()
        val len = max(1, endIdx - startIdx)
        for (i in startIdx until endIdx) {
            val t = (i - startIdx).toFloat() / len.toFloat()
            // Cosine interpolation for smooth zero-slope joins
            val smoothT = (1f - cos(t * PI.toFloat())) * 0.5f
            out[i] = (sVal * (1f - smoothT) + eVal * smoothT).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    /**
     * Inverts waveform phase (-sample) over selection or track.
     */
    fun invertPhase(
        samples: ShortArray,
        startMs: Long,
        endMs: Long,
        sampleRate: Int = SAMPLE_RATE
    ): ShortArray {
        if (samples.isEmpty()) return samples
        val (startIdx, endIdx) = rangeIndices(samples.size, startMs, endMs, sampleRate)
        val out = samples.clone()
        for (i in startIdx until endIdx) {
            out[i] = (-out[i].toInt()).coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    /**
     * Adds an explicit vertical DC offset (-30%..+30%) or centers to 0 when offsetPercent == 0f.
     */
    fun applyVerticalOffset(samples: ShortArray, offsetPercent: Float): ShortArray {
        if (abs(offsetPercent) < 0.005f) return StudioAudioProcessor.removeDcOffset(samples)
        val delta = (offsetPercent * 32767f).toInt()
        val out = ShortArray(samples.size)
        for (i in samples.indices) {
            out[i] = (samples[i] + delta).coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    /**
     * WSOLA/SOLA Overlap-Add Time-Stretch keeping original pitch intact.
     */
    fun applyTimeStretchKeepPitch(samples: ShortArray, timeRate: Float): ShortArray {
        if (samples.size < 2048 || abs(timeRate - 1.0f) < 0.03f) return samples
        val safeRate = timeRate.coerceIn(0.4f, 2.5f)
        val winSize = 1024
        val hopOut = winSize / 2
        val hopIn = max(64, (hopOut * safeRate).toInt())

        val numFrames = max(1, (samples.size - winSize) / hopIn)
        val outLen = numFrames * hopOut + winSize
        val accum = FloatArray(outLen)
        val norm = FloatArray(outLen)
        val window = FloatArray(winSize) { i ->
            (0.5 * (1.0 - cos(2.0 * PI * i / (winSize - 1)))).toFloat()
        }

        var inPos = 0
        var outPos = 0
        for (f in 0 until numFrames) {
            if (inPos + winSize >= samples.size || outPos + winSize >= outLen) break
            for (k in 0 until winSize) {
                val w = window[k]
                accum[outPos + k] += samples[inPos + k] * w
                norm[outPos + k] += w
            }
            inPos += hopIn
            outPos += hopOut
        }

        val out = ShortArray(outLen)
        for (i in 0 until outLen) {
            val v = if (norm[i] > 0.001f) accum[i] / norm[i] else 0f
            out[i] = v.toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    /**
     * Voice Over: Overdubs narration/voice PCM at insertMs while smart-ducking background track volume.
     */
    fun applyVoiceOverDuck(
        mainTrack: ShortArray,
        voiceClip: ShortArray,
        insertAtMs: Long,
        musicDuckFactor: Float = 0.30f,
        voiceGain: Float = 1.15f,
        sampleRate: Int = SAMPLE_RATE
    ): ShortArray {
        if (voiceClip.isEmpty()) return mainTrack
        val rawInsertSample = ((insertAtMs.toDouble() / 1000.0) * sampleRate).toInt()
        val skipStart = if (rawInsertSample < 0) (-rawInsertSample).coerceAtMost(voiceClip.size) else 0
        val effectiveStart = if (rawInsertSample >= 0) rawInsertSample else 0
        val remainingLen = (voiceClip.size - skipStart).coerceAtLeast(0)
        val outLen = max(mainTrack.size, effectiveStart + remainingLen)
        val out = ShortArray(outLen)
        val fadeSamples = min(4096, remainingLen / 4)

        for (i in 0 until outLen) {
            val base = if (i < mainTrack.size) mainTrack[i].toFloat() else 0f
            val relIdx = i - effectiveStart
            val voiceIdx = relIdx + skipStart
            if (relIdx >= 0 && voiceIdx in voiceClip.indices) {
                val edgeT = when {
                    relIdx < fadeSamples -> relIdx.toFloat() / fadeSamples.coerceAtLeast(1)
                    remainingLen - relIdx < fadeSamples -> (remainingLen - relIdx).toFloat() / fadeSamples.coerceAtLeast(1)
                    else -> 1f
                }
                val duck = 1f - edgeT * (1f - musicDuckFactor.coerceIn(0.05f, 1f))
                val mixed = base * duck + voiceClip[voiceIdx] * voiceGain
                out[i] = mixed.toInt().coerceIn(-32767, 32767).toShort()
            } else {
                out[i] = base.toInt().coerceIn(-32767, 32767).toShort()
            }
        }
        return out
    }

    enum class VoiceOverPreset(
        val displayName: String,
        val subtitle: String,
        val voiceGain: Float,
        val musicVolume: Float,
        val duckFactor: Float,
        val autoDuckEnabled: Boolean
    ) {
        TUTORIAL(
            displayName = "Tutorial",
            subtitle = "Clear voice over gentle ducked music",
            voiceGain = 1.25f,
            musicVolume = 0.50f,
            duckFactor = 0.22f,
            autoDuckEnabled = true
        ),
        PODCAST(
            displayName = "Podcast",
            subtitle = "Balanced speech with deep background dip",
            voiceGain = 1.20f,
            musicVolume = 0.45f,
            duckFactor = 0.18f,
            autoDuckEnabled = true
        ),
        VLOG(
            displayName = "Vlog / YouTube",
            subtitle = "Lively upbeat music with crisp ducking",
            voiceGain = 1.15f,
            musicVolume = 0.70f,
            duckFactor = 0.32f,
            autoDuckEnabled = true
        ),
        STORY(
            displayName = "Story / Calm",
            subtitle = "Soft ambient music bed behind narration",
            voiceGain = 1.10f,
            musicVolume = 0.38f,
            duckFactor = 0.15f,
            autoDuckEnabled = true
        ),
        STEADY_BED(
            displayName = "Steady Music",
            subtitle = "Even background music without auto-ducking",
            voiceGain = 1.00f,
            musicVolume = 0.35f,
            duckFactor = 1.00f,
            autoDuckEnabled = false
        )
    }

    /**
     * Smart Music-Bed Auto-Ducking:
     * Treats [voiceTrack] as foreground narration and mixes [musicPcm] underneath.
     * When [autoDuckEnabled] is true, detects vocal activity in 20ms RMS frames and
     * smoothly dips background music down to [duckLevel] during speech while swelling
     * back smoothly during pauses.
     */
    fun applySmartMusicBedAutoDuck(
        voiceTrack: ShortArray,
        musicPcm: ShortArray,
        duckLevel: Float = 0.22f,
        voiceGain: Float = 1.25f,
        musicBaseVolume: Float = 0.50f,
        autoDuckEnabled: Boolean = true,
        loopMusic: Boolean = true,
        fadeOutTailMs: Long = 800L,
        sampleRate: Int = SAMPLE_RATE,
        startOffsetMs: Long = 0L
    ): ShortArray {
        if (voiceTrack.isEmpty() || musicPcm.isEmpty()) return voiceTrack

        val hop = (sampleRate * 0.020).toInt().coerceAtLeast(32)
        val numFrames = ((voiceTrack.size + hop - 1) / hop).coerceAtLeast(1)
        val frameRms = DoubleArray(numFrames)
        var maxFrameRms = 0.0
        for (f in 0 until numFrames) {
            val start = f * hop
            val end = min(start + hop, voiceTrack.size)
            var sumSq = 0.0
            for (i in start until end) {
                val s = voiceTrack[i].toDouble()
                sumSq += s * s
            }
            val r = sqrt(sumSq / max(1, end - start))
            frameRms[f] = r
            if (r > maxFrameRms) maxFrameRms = r
        }

        val speechThreshold = max(220.0, maxFrameRms * 0.06)
        val clampedDuck = duckLevel.coerceIn(0.05f, 1.0f)
        val duckCurve = FloatArray(numFrames) { 1.0f }
        if (autoDuckEnabled) {
            var env = 1.0f
            for (f in 0 until numFrames) {
                val windowActive = (max(0, f - 1)..min(numFrames - 1, f + 2)).any { frameRms[it] >= speechThreshold }
                val target = if (windowActive) clampedDuck else 1.0f
                val alpha = if (target < env) 0.45f else 0.08f
                env += alpha * (target - env)
                duckCurve[f] = env
            }
        }

        val offsetSamples = kotlin.math.round((startOffsetMs.toDouble() / 1000.0) * sampleRate).toInt()
        val tailFadeSamples = ((fadeOutTailMs.toDouble() / 1000.0) * sampleRate).toInt().coerceIn(0, voiceTrack.size / 4)
        val out = ShortArray(voiceTrack.size)
        for (f in 0 until numFrames) {
            val start = f * hop
            val end = min(start + hop, voiceTrack.size)
            val d0 = duckCurve[f]
            val d1 = duckCurve[min(f + 1, numFrames - 1)]
            val span = max(1, end - start).toFloat()
            for (i in start until end) {
                val t = (i - start) / span
                val duckFactor = d0 + (d1 - d0) * t
                val musicPos = i - offsetSamples
                val musicSample = when {
                    musicPos < 0 -> 0f
                    musicPos < musicPcm.size -> musicPcm[musicPos].toFloat()
                    loopMusic -> musicPcm[musicPos % musicPcm.size].toFloat()
                    else -> 0f
                }
                val tailScale = if (tailFadeSamples > 0 && voiceTrack.size - i < tailFadeSamples) {
                    (voiceTrack.size - i).toFloat() / tailFadeSamples.toFloat()
                } else {
                    1.0f
                }
                val vPart = voiceTrack[i].toFloat() * voiceGain
                val mPart = musicSample * musicBaseVolume * duckFactor * tailScale
                out[i] = (vPart + mPart).toInt().coerceIn(-32767, 32767).toShort()
            }
        }
        return StudioAudioProcessor.applyTruePeakLimiter(out, -0.5f)
    }

    /**
     * Resamples PCM cleanly through a target rate (anti-aliased decimation/interpolation) and restores 44.1kHz stream.
     */
    fun applyResampleProfile(samples: ShortArray, targetQualityRateHz: Int): ShortArray {
        if (samples.size < 64 || targetQualityRateHz == SAMPLE_RATE) return samples
        val ratio = targetQualityRateHz.toDouble() / SAMPLE_RATE.toDouble()
        val tempLen = max(16, (samples.size * ratio).toInt())
        val downsampled = FloatArray(tempLen)
        for (i in 0 until tempLen) {
            val src = (i / ratio).coerceIn(0.0, (samples.size - 2).toDouble())
            val idx = src.toInt()
            val frac = (src - idx).toFloat()
            downsampled[i] = samples[idx] * (1f - frac) + samples[idx + 1] * frac
        }
        // Reconstruct back onto 44.1kHz studio timeline
        val out = ShortArray(samples.size)
        for (i in samples.indices) {
            val src = (i * ratio).coerceIn(0.0, (tempLen - 2).toDouble())
            val idx = src.toInt()
            val frac = (src - idx).toFloat()
            out[i] = (downsampled[idx] * (1f - frac) + downsampled[idx + 1] * frac)
                .toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    // ==================== CHANNELS CATEGORY ====================

    /**
     * Channel Mixer: adjusts virtual Left & Right channel weights and crossfeed warmth.
     */
    fun applyChannelMixer(samples: ShortArray, leftGain: Float, rightGain: Float): ShortArray {
        if (samples.isEmpty()) return samples
        val avg = ((leftGain + rightGain) * 0.5f).coerceIn(0.05f, 2.0f)
        val imbalance = abs(leftGain - rightGain) * 0.15f
        val out = ShortArray(samples.size)
        var prev = 0f
        for (i in samples.indices) {
            val s = samples[i].toFloat()
            val hp = s - prev * imbalance
            prev = s
            out[i] = (hp * avg).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    /**
     * MaxMatch: Balances amplitude level between Left/first-half and Right/second-half sections so levels match evenly.
     */
    fun applyMaxMatch(samples: ShortArray, startMs: Long, endMs: Long): ShortArray {
        if (samples.size < 128) return samples
        val (startIdx, endIdx) = rangeIndices(samples.size, startMs, endMs)
        val mid = (startIdx + endIdx) / 2
        var maxA = 1
        var maxB = 1
        for (i in startIdx until mid) maxA = max(maxA, abs(samples[i].toInt()))
        for (i in mid until endIdx) maxB = max(maxB, abs(samples[i].toInt()))
        val targetPeak = max(maxA, maxB).toFloat().coerceAtMost(31000f)
        val gainA = (targetPeak / maxA).coerceIn(0.4f, 3.0f)
        val gainB = (targetPeak / maxB).coerceIn(0.4f, 3.0f)
        val out = samples.clone()
        val xfade = min(1024, (endIdx - startIdx) / 8).coerceAtLeast(1)
        for (i in startIdx until endIdx) {
            val g = if (i < mid - xfade) gainA
            else if (i > mid + xfade) gainB
            else {
                val t = (i - (mid - xfade)).toFloat() / (2f * xfade)
                gainA * (1f - t) + gainB * t
            }
            out[i] = (samples[i] * g).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    /**
     * Multichannel Mixer: mixes Main Bus + Voice/Clipboard Bus + Warm Sub-Bass Bus.
     */
    fun applyMultichannelMix(
        mainPcm: ShortArray,
        bus2Pcm: ShortArray?,
        mainGain: Float = 1.0f,
        bus2Gain: Float = 0.8f,
        subBusGain: Float = 0.2f
    ): ShortArray {
        if (mainPcm.isEmpty() && (bus2Pcm == null || bus2Pcm.isEmpty())) return ShortArray(0)
        val len = max(mainPcm.size, bus2Pcm?.size ?: 0)
        val out = ShortArray(len)
        var subLp = 0f
        for (i in 0 until len) {
            val m = if (i < mainPcm.size) mainPcm[i].toFloat() else 0f
            val b2 = if (bus2Pcm != null && i < bus2Pcm.size) bus2Pcm[i].toFloat() else 0f
            subLp = 0.03f * m + 0.97f * subLp
            val mix = m * mainGain + b2 * bus2Gain + subLp * subBusGain
            out[i] = mix.toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    /**
     * Reduce Vocals: attenuates central speech band (300 Hz - 3400 Hz) while preserving bass & air harmonics.
     */
    fun applyReduceVocals(samples: ShortArray, strength: Float = 0.75f): ShortArray {
        if (samples.size < 64) return samples
        val midBand = StudioAudioProcessor.applyBandPassFilter(samples, 1300f, 2400f)
        val s = strength.coerceIn(0.1f, 0.98f)
        val out = ShortArray(samples.size)
        for (i in samples.indices) {
            val v = samples[i].toFloat() - midBand[i].toFloat() * s
            out[i] = v.toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    /**
     * Pan & Stereo Center: shapes spatial modulation or center presence depth.
     */
    fun applyPanAndStereoCenter(
        samples: ShortArray,
        panValue: Float = 0f,       // -1.0 (Left) .. 0 (Center) .. +1.0 (Right)
        centerWidth: Float = 0.5f,  // 0..1 stereo width emphasis
        autoPanHz: Float = 0f
    ): ShortArray {
        if (samples.size < 128) return samples
        val out = ShortArray(samples.size)
        val delay = (0.012f * SAMPLE_RATE).toInt().coerceIn(32, 1024)
        for (i in samples.indices) {
            val t = i.toDouble() / SAMPLE_RATE
            val dynPan = if (autoPanHz > 0.05f) {
                (0.55f + 0.45f * sin(2.0 * PI * autoPanHz * t).toFloat())
            } else {
                1f - abs(panValue) * 0.35f
            }
            val delayedSample = if (i >= delay) samples[i - delay].toFloat() else 0f
            val wide = samples[i].toFloat() * (1f - centerWidth * 0.2f) + delayedSample * (centerWidth * 0.28f)
            out[i] = (wide * dynPan).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    // ==================== FILTER CATEGORY ====================

    /**
     * Precision RBJ Biquad Parametric EQ (Peaking EQ: Center Hz, Q Factor, Gain dB).
     */
    fun applyParametricEq(
        samples: ShortArray,
        centerHz: Float = 1000f,
        qFactor: Float = 1.4f,
        gainDb: Float = 4.0f,
        sampleRate: Int = SAMPLE_RATE
    ): ShortArray {
        if (samples.size < 8 || abs(gainDb) < 0.1f) return samples
        val a = 10.0.pow(gainDb / 40.0)
        val omega = 2.0 * PI * centerHz.coerceIn(40f, 18000f) / sampleRate
        val sn = sin(omega)
        val cs = cos(omega)
        val alpha = sn / (2.0 * qFactor.coerceIn(0.3f, 12.0f))

        val b0 = 1.0 + alpha * a
        val b1 = -2.0 * cs
        val b2 = 1.0 - alpha * a
        val a0 = 1.0 + alpha / a
        val a1 = -2.0 * cs
        val a2 = 1.0 - alpha / a

        val out = ShortArray(samples.size)
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0

        for (i in samples.indices) {
            val x0 = samples[i].toDouble()
            val y0 = (b0 / a0) * x0 + (b1 / a0) * x1 + (b2 / a0) * x2 - (a1 / a0) * y1 - (a2 / a0) * y2
            x2 = x1; x1 = x0
            y2 = y1; y1 = y0
            out[i] = y0.toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    /**
     * Combined Low-Pass + High-Pass cutoff filter.
     */
    fun applyLowAndHighPass(samples: ShortArray, highPassHz: Float, lowPassHz: Float): ShortArray {
        val step1 = if (highPassHz > 30f) StudioAudioProcessor.applyHighPassFilter(samples, highPassHz) else samples
        return if (lowPassHz < 16000f) StudioAudioProcessor.applyLowPassFilter(step1, lowPassHz) else step1
    }

    /**
     * Silence Reduction: shortens long silent gaps below thresholdDb down to maxKeepSilenceMs.
     */
    fun applySilenceReduction(
        samples: ShortArray,
        thresholdDb: Float = -38f,
        maxKeepSilenceMs: Int = 250,
        sampleRate: Int = SAMPLE_RATE
    ): ShortArray {
        if (samples.isEmpty()) return samples
        val ampThresh = (32767.0 * 10.0.pow(thresholdDb / 20.0)).toInt().coerceAtLeast(30)
        val maxKeepSamples = ((maxKeepSilenceMs / 1000.0) * sampleRate).toInt().coerceAtLeast(100)
        val block = 441 // 10 ms frame

        val kept = ArrayList<Short>(samples.size)
        var silentSamplesRun = 0
        var idx = 0
        while (idx < samples.size) {
            val end = min(idx + block, samples.size)
            var framePeak = 0
            for (k in idx until end) {
                val a = abs(samples[k].toInt())
                if (a > framePeak) framePeak = a
            }
            if (framePeak < ampThresh) {
                if (silentSamplesRun < maxKeepSamples) {
                    for (k in idx until end) kept.add(samples[k])
                    silentSamplesRun += (end - idx)
                }
            } else {
                silentSamplesRun = 0
                for (k in idx until end) kept.add(samples[k])
            }
            idx = end
        }
        return if (kept.size < 441) samples else kept.toShortArray()
    }

    /**
     * Waveform Smoother: moving-average FIR low-pass filter to reduce harsh digital grit.
     */
    fun applySmoother(samples: ShortArray, strength: Int = 3): ShortArray {
        if (samples.size < 16) return samples
        val radius = strength.coerceIn(1, 6)
        val out = ShortArray(samples.size)
        val denom = (radius * 2 + 1).toFloat()
        for (i in samples.indices) {
            var sum = 0f
            for (k in -radius..radius) {
                val p = (i + k).coerceIn(0, samples.size - 1)
                sum += samples[p]
            }
            out[i] = (sum / denom).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    /**
     * 3-Region Spectrum Filter: applies Low Tilt, Mid Presence, and High Air spectral contour.
     */
    fun applySpectrumFilter(
        samples: ShortArray,
        lowTiltDb: Float = 2f,
        midTiltDb: Float = 0f,
        highTiltDb: Float = 3f
    ): ShortArray {
        var proc = applyParametricEq(samples, 140f, 0.8f, lowTiltDb)
        proc = applyParametricEq(proc, 1200f, 1.0f, midTiltDb)
        proc = applyParametricEq(proc, 6500f, 0.8f, highTiltDb)
        return StudioAudioProcessor.applyTruePeakLimiter(proc, -0.5f)
    }

    // ==================== VOLUME CATEGORY ====================

    /**
     * Auto Gain Control (AGC): balances quiet and loud sections toward targetRmsDb smoothly.
     */
    fun applyAutoGain(
        samples: ShortArray,
        targetRmsDb: Float = -16f,
        sampleRate: Int = SAMPLE_RATE
    ): ShortArray {
        if (samples.isEmpty()) return samples
        val targetRms = 32767.0 * 10.0.pow(targetRmsDb / 20.0)
        val win = (sampleRate * 0.25).toInt().coerceAtLeast(1024)
        val out = ShortArray(samples.size)
        var runningGain = 1.0

        var start = 0
        while (start < samples.size) {
            val end = min(start + win, samples.size)
            var sumSq = 0.0
            for (i in start until end) {
                val s = samples[i].toDouble()
                sumSq += s * s
            }
            val rms = sqrt(sumSq / max(1, end - start))
            val desiredGain = if (rms > 80.0) (targetRms / rms).coerceIn(0.3, 4.0) else 1.0
            for (i in start until end) {
                runningGain = runningGain * 0.9992 + desiredGain * 0.0008
                out[i] = (samples[i] * runningGain).toInt().coerceIn(-32767, 32767).toShort()
            }
            start = end
        }
        return StudioAudioProcessor.applyTruePeakLimiter(out, -0.5f)
    }

    /**
     * Loudness Contour: boosts perceived richness (low warmth + presence) with true-peak limiter.
     */
    fun applyLoudnessContour(samples: ShortArray, boostDb: Float = 4.0f): ShortArray {
        var proc = applyParametricEq(samples, 100f, 0.7f, boostDb * 0.8f)
        proc = applyParametricEq(proc, 4200f, 0.9f, boostDb * 0.65f)
        proc = StudioAudioProcessor.applyGainDb(proc, boostDb * 0.4f)
        return StudioAudioProcessor.applyTruePeakLimiter(proc, -0.3f)
    }

    /**
     * Shape Volume: sculpts amplitude envelope across selection using named volume curves.
     */
    fun applyShapeVolume(
        samples: ShortArray,
        startMs: Long,
        endMs: Long,
        shapeType: VolumeShapeType,
        depth: Float = 0.75f,
        sampleRate: Int = SAMPLE_RATE
    ): ShortArray {
        if (samples.isEmpty()) return samples
        val (startIdx, endIdx) = rangeIndices(samples.size, startMs, endMs, sampleRate)
        val out = samples.clone()
        val span = max(1, endIdx - startIdx).toFloat()
        val d = depth.coerceIn(0.1f, 1.0f)

        for (i in startIdx until endIdx) {
            val t = (i - startIdx).toFloat() / span
            val env = when (shapeType) {
                VolumeShapeType.ARCH_PEAK -> {
                    val swell = sin(t * PI).toFloat()
                    (1f - d * 0.5f) + swell * d * 0.75f
                }
                VolumeShapeType.DUCK_VALLEY -> {
                    val dip = sin(t * PI).toFloat()
                    1f - dip * d * 0.75f
                }
                VolumeShapeType.RAMP_UP -> (1f - d) + t * d
                VolumeShapeType.RAMP_DOWN -> 1f - t * d
                VolumeShapeType.PULSE_SWELL -> {
                    0.5f + 0.5f * abs(sin(t * 4.0 * PI)).toFloat() * d
                }
            }
            out[i] = (samples[i] * env).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }
}
