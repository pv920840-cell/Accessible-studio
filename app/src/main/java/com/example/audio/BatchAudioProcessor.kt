package com.example.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class BatchItemStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    ERROR
}

data class BatchItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val uri: Uri? = null,
    val sourceFile: File? = null,
    val isDemo: Boolean = false,
    val demoType: DemoAudioType? = null,
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val status: BatchItemStatus = BatchItemStatus.PENDING,
    val resultFile: File? = null,
    val errorMessage: String? = null
)

data class BatchChainConfig(
    val normalize: Boolean = true,
    val targetRatio: Float = 0.98f, // -0.1 dB peak
    val trimSilence: Boolean = false,
    val trimStartMs: Long = 0L,
    val maxDurationMs: Long = 0L, // 0 = no limit, otherwise truncate
    val deClick: Boolean = true,
    val noiseReduction: Boolean = false,
    val noiseReductionIntensity: Float = 0.5f,
    val fadeInMs: Long = 100L,
    val fadeOutMs: Long = 200L,
    val highPassFilter: Boolean = false,
    val lowPassFilter: Boolean = false,
    val gainDb: Float = 0f,
    val outputFormat: String = "WAV",
    val filePrefix: String = "batch_"
)

class BatchAudioProcessor(private val context: Context) {

    private val wavProcessor = WavAudioProcessor(context)
    val sampleRate = 44100

    /**
     * Creates a BatchItem from a picked Content Uri
     */
    fun createItemFromUri(uri: Uri): BatchItem {
        val name = wavProcessor.queryAudioDisplayName(uri) ?: "Audio_${System.currentTimeMillis() % 10000}"
        var durationMs = 0L
        var sizeBytes = 0L

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durStr?.toLongOrNull() ?: 0L
            retriever.release()
        } catch (_: Throwable) {}

        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                sizeBytes = pfd.statSize
            }
        } catch (_: Throwable) {}

        return BatchItem(
            name = name,
            uri = uri,
            durationMs = durationMs,
            fileSizeBytes = sizeBytes
        )
    }

    /**
     * Creates a demo BatchItem for instant offline testing
     */
    fun createDemoItem(type: DemoAudioType): BatchItem {
        return BatchItem(
            name = "Demo_${type.displayName.replace(" ", "_")}.wav",
            isDemo = true,
            demoType = type,
            durationMs = 10000L,
            fileSizeBytes = (sampleRate * 2 * 10).toLong()
        )
    }

    /**
     * Processes an entire batch sequentially to preserve memory on Android devices
     */
    suspend fun processBatch(
        items: List<BatchItem>,
        config: BatchChainConfig,
        onProgress: (itemIndex: Int, totalItems: Int, itemProgress: Float, currentMessage: String) -> Unit
    ): List<BatchItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<BatchItem>()
        val total = items.size

        for (index in items.indices) {
            val item = items[index]
            onProgress(index + 1, total, 0.05f, "Decoding ${item.name}...")

            try {
                // 1. Decode PCM audio
                val originalPcm: ShortArray = if (item.isDemo && item.demoType != null) {
                    wavProcessor.generateDemoPcm(item.demoType, 10)
                } else if (item.uri != null) {
                    wavProcessor.decodeUriToPcm(item.uri)
                } else if (item.sourceFile != null && item.sourceFile.exists()) {
                    wavProcessor.decodeUriToPcm(Uri.fromFile(item.sourceFile))
                } else {
                    ShortArray(0)
                }

                if (originalPcm.isEmpty()) {
                    results.add(
                        item.copy(
                            status = BatchItemStatus.ERROR,
                            errorMessage = "Unable to decode audio format"
                        )
                    )
                    continue
                }

                onProgress(index + 1, total, 0.25f, "Applying effects chain on ${item.name}...")

                // 2. Apply Effects Chain
                var pcm = originalPcm

                // Step A: Auto-Trim Silence or Trim Start / Max Duration
                if (config.trimSilence) {
                    pcm = trimSilence(pcm, thresholdDb = -40f)
                }

                if (config.trimStartMs > 0 || config.maxDurationMs > 0) {
                    val startSample = ((config.trimStartMs.toDouble() / 1000.0) * sampleRate).toInt().coerceIn(0, pcm.size)
                    val endSample = if (config.maxDurationMs > 0) {
                        val maxSamples = ((config.maxDurationMs.toDouble() / 1000.0) * sampleRate).toInt()
                        (startSample + maxSamples).coerceIn(startSample, pcm.size)
                    } else {
                        pcm.size
                    }
                    val length = endSample - startSample
                    pcm = if (length > 0) {
                        ShortArray(length).also { trimmed ->
                            System.arraycopy(pcm, startSample, trimmed, 0, length)
                        }
                    } else {
                        ShortArray(0)
                    }
                }

                if (pcm.isEmpty()) {
                    results.add(
                        item.copy(
                            status = BatchItemStatus.ERROR,
                            errorMessage = "Audio became empty after trimming"
                        )
                    )
                    continue
                }

                onProgress(index + 1, total, 0.45f, "Filtering & cleaning ${item.name}...")

                // Step B: High-pass / Low-pass filtering
                if (config.highPassFilter) {
                    pcm = StudioAudioProcessor.applyHighPassFilter(pcm, 300f)
                }
                if (config.lowPassFilter) {
                    pcm = StudioAudioProcessor.applyLowPassFilter(pcm, 3000f)
                }

                // Step C: De-Click & De-Crackle / Noise Reduction
                if (config.deClick) {
                    pcm = StudioAudioProcessor.deClickDeCrackle(pcm)
                }
                if (config.noiseReduction) {
                    pcm = wavProcessor.applyNoiseReduction(pcm, config.noiseReductionIntensity)
                }

                // Step D: Gain adjustment
                if (abs(config.gainDb) > 0.05f) {
                    pcm = StudioAudioProcessor.applyGainDb(pcm, config.gainDb)
                }

                onProgress(index + 1, total, 0.70f, "Normalizing & shaping envelopes for ${item.name}...")

                // Step E: Normalization
                if (config.normalize) {
                    pcm = StudioAudioProcessor.normalize(pcm, config.targetRatio)
                }

                // Step F: Fade In & Fade Out
                if (config.fadeInMs > 0) {
                    val fadeSamples = ((config.fadeInMs.toDouble() / 1000.0) * sampleRate).toInt().coerceIn(1, pcm.size)
                    for (i in 0 until fadeSamples) {
                        val factor = i.toFloat() / fadeSamples.toFloat()
                        pcm[i] = (pcm[i] * factor).toInt().coerceIn(-32767, 32767).toShort()
                    }
                }
                if (config.fadeOutMs > 0) {
                    val fadeSamples = ((config.fadeOutMs.toDouble() / 1000.0) * sampleRate).toInt().coerceIn(1, pcm.size)
                    val startFade = pcm.size - fadeSamples
                    for (i in 0 until fadeSamples) {
                        val factor = (fadeSamples - i).toFloat() / fadeSamples.toFloat()
                        val idx = startFade + i
                        if (idx in pcm.indices) {
                            pcm[idx] = (pcm[idx] * factor).toInt().coerceIn(-32767, 32767).toShort()
                        }
                    }
                }

                // Step G: True-Peak Limiter (-1 dB headroom)
                pcm = StudioAudioProcessor.applyTruePeakLimiter(pcm)

                onProgress(index + 1, total, 0.85f, "Writing ${item.name} (${config.outputFormat})...")

                // 3. Export to Single Output File in Music / Accessible Studio/Audio directory
                val exportDir = com.example.storage.StorageExportManager.getAudioExportDirectory(context)

                val baseCleanName = item.name
                    .removeSuffix(".wav").removeSuffix(".WAV")
                    .removeSuffix(".mp3").removeSuffix(".MP3")
                    .removeSuffix(".flac").removeSuffix(".FLAC")
                    .removeSuffix(".m4a").removeSuffix(".M4A")
                val ext = when (config.outputFormat.uppercase()) {
                    "MP3" -> "mp3"
                    "FLAC" -> "flac"
                    "M4A" -> "m4a"
                    else -> "wav"
                }

                val outFileName = "${config.filePrefix}${baseCleanName}_processed.$ext"
                val destFile = File(exportDir, outFileName)

                wavProcessor.writeWavFile(destFile, pcm, sampleRate, 1)
                com.example.storage.StorageExportManager.saveAudioFile(
                    context = context,
                    sourceFile = destFile,
                    targetFileName = outFileName,
                    mimeType = com.example.storage.StorageExportManager.getAudioMimeType(outFileName)
                )

                results.add(
                    item.copy(
                        status = BatchItemStatus.COMPLETED,
                        resultFile = destFile,
                        durationMs = (pcm.size * 1000L) / sampleRate,
                        fileSizeBytes = destFile.length()
                    )
                )

                onProgress(index + 1, total, 1.0f, "Completed ${item.name}")
            } catch (t: Throwable) {
                t.printStackTrace()
                results.add(
                    item.copy(
                        status = BatchItemStatus.ERROR,
                        errorMessage = t.localizedMessage ?: "Processing failed"
                    )
                )
            }
        }

        results
    }

    /**
     * Trims leading and trailing silence below threshold dB
     */
    private fun trimSilence(samples: ShortArray, thresholdDb: Float = -40f): ShortArray {
        if (samples.isEmpty()) return samples
        // -40 dB relative to 32767 is approx ~328 amplitude
        val thresholdAmp = (32767.0 * Math.pow(10.0, (thresholdDb / 20.0).toDouble())).toInt().coerceAtLeast(150)
        val paddingSamples = (0.05 * sampleRate).toInt() // 50ms smooth pad

        var firstAudible = -1
        for (i in samples.indices) {
            if (abs(samples[i].toInt()) >= thresholdAmp) {
                firstAudible = i
                break
            }
        }

        if (firstAudible == -1) {
            // Entire audio is silence; keep original
            return samples
        }

        var lastAudible = samples.size - 1
        for (i in samples.indices.reversed()) {
            if (abs(samples[i].toInt()) >= thresholdAmp) {
                lastAudible = i
                break
            }
        }

        val start = (firstAudible - paddingSamples).coerceAtLeast(0)
        val end = (lastAudible + paddingSamples).coerceAtMost(samples.size)
        val length = (end - start).coerceAtLeast(1)

        val trimmed = ShortArray(length)
        System.arraycopy(samples, start, trimmed, 0, length)
        return trimmed
    }
}
