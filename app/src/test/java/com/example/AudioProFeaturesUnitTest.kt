package com.example

import com.example.audio.AudioExportOptions
import com.example.audio.StudioAudioProcessor
import com.example.audio.WavAudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class AudioProFeaturesUnitTest {

    @Test
    fun zeroCrossingSnap_snapsBoundaryNearZeroAmplitude() {
        val sr = 44100
        val pcm = ShortArray(sr / 10) { i ->
            (sin(2.0 * PI * 100.0 * i / sr) * 18000.0).toInt().toShort()
        }
        val rawIdx = ((12.0 / 1000.0) * sr).toInt()
        val snappedIdx = StudioAudioProcessor.findNearestZeroCrossingIndex(pcm, rawIdx, 1024)
        assertTrue("Sample zero-crossing amplitude should be near 0", abs(pcm[snappedIdx].toInt()) < 1500)

        val (snappedStart, snappedEnd) = StudioAudioProcessor.snapRangeToZeroCrossings(
            samples = pcm,
            startMs = 12L,
            endMs = 68L,
            sampleRate = sr
        )
        assertTrue(snappedStart < snappedEnd)
        val sIdx = ((snappedStart / 1000.0) * sr).toInt().coerceIn(0, pcm.lastIndex)
        val eIdx = ((snappedEnd / 1000.0) * sr).toInt().coerceIn(0, pcm.lastIndex)
        assertTrue("Snapped start ms should land near zero crossing (${pcm[sIdx]})", abs(pcm[sIdx].toInt()) < 2500)
        assertTrue("Snapped end ms should land near zero crossing (${pcm[eIdx]})", abs(pcm[eIdx].toInt()) < 2500)
    }

    @Test
    fun trimAndTruncateSilence_removesDeadAirWhilePreservingSignal() {
        val sr = 44100
        val silence = ShortArray(sr / 2) { 0 } // 500ms silence
        val tone = ShortArray(sr / 4) { i -> (sin(2.0 * PI * 500.0 * i / sr) * 12000.0).toInt().toShort() } // 250ms tone
        val combined = silence + tone + silence + tone + silence

        val edgeTrimmed = StudioAudioProcessor.trimLeadingTrailingSilence(combined, thresholdDb = -40f, padMs = 20L, sampleRate = sr)
        assertTrue("Edge trim should shorten dead air at start/end", edgeTrimmed.size < combined.size - (sr / 2))

        val truncated = StudioAudioProcessor.truncateSilence(combined, thresholdDb = -40f, minSilenceMs = 250L, targetGapMs = 100L, sampleRate = sr)
        assertTrue("Middle silence gap should also be shortened", truncated.size < edgeTrimmed.size)
        assertTrue(truncated.isNotEmpty())
    }

    @Test
    fun crossfadeJoinAndNormalizeLufs_produceSmoothExpectedOutput() {
        val sr = 44100
        val clipA = ShortArray(sr / 2) { i -> (sin(2.0 * PI * 330.0 * i / sr) * 5000.0).toInt().toShort() }
        val clipB = ShortArray(sr / 2) { i -> (sin(2.0 * PI * 440.0 * i / sr) * 5000.0).toInt().toShort() }

        val joined = StudioAudioProcessor.crossfadeJoin(clipA, clipB, crossfadeMs = 50L, sampleRate = sr)
        assertTrue("Crossfade overlap makes joined shorter than raw sum", joined.size < clipA.size + clipB.size)

        val lufsNormalized = StudioAudioProcessor.normalizeLufs(joined, targetLufs = -16.0f, sampleRate = sr)
        val measuredLufs = StudioAudioProcessor.computeIntegratedLufs(lufsNormalized, sr)
        assertTrue("Measured LUFS ($measuredLufs) should be close to -16 LUFS", measuredLufs in -18.5f..-13.5f)
    }

    @Test
    fun writeWavFile_24BitStereoWithRiffMetadata_writesValidHeaderAndInfoChunk() {
        val processor = WavAudioProcessor(null)
        val tempWav = File.createTempFile("pro_wav_test_", ".wav")
        try {
            val sr = 48000
            val mono = ShortArray(2400) { i -> (sin(2.0 * PI * 440.0 * i / sr) * 8000.0).toInt().toShort() }
            val stereoInterleaved = ShortArray(mono.size * 2).apply {
                for (i in mono.indices) {
                    this[i * 2] = mono[i]
                    this[i * 2 + 1] = mono[i]
                }
            }
            val opts = AudioExportOptions(
                sampleRate = sr,
                bitDepth = 24,
                stereoOutput = true,
                title = "Studio Episode",
                artist = "Creator",
                album = "Podcast Season"
            )
            processor.writeWavFile(tempWav, stereoInterleaved, sr, 2, 24, opts)

            val bytes = tempWav.readBytes()
            val header = ByteBuffer.wrap(bytes, 0, 44).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
            assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
            assertEquals(2.toShort(), header.getShort(22)) // channels = 2
            assertEquals(sr, header.getInt(24)) // sampleRate = 48000
            assertEquals(24.toShort(), header.getShort(34)) // bitsPerSample = 24

            val allAscii = String(bytes, Charsets.ISO_8859_1)
            assertTrue(allAscii.contains("LIST"))
            assertTrue(allAscii.contains("INFO"))
            assertTrue(allAscii.contains("INAM"))
            assertTrue(allAscii.contains("Studio Episode"))
            assertTrue(allAscii.contains("IART"))
            assertTrue(allAscii.contains("Creator"))
        } finally {
            tempWav.delete()
        }
    }
}
