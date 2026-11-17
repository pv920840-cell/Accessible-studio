package com.example.video

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.math.abs

/**
 * Pure-Kotlin on-device GIF89a animated encoder with 216-color web-safe palette quantization
 * and standard LZW raster compression (zero external native dependencies).
 */
class AnimatedGifEncoder(
    private val delayMs: Int = 100,
    private val loopCount: Int = 0
) {
    private val palette: IntArray = IntArray(256)
    private val paletteR = IntArray(256)
    private val paletteG = IntArray(256)
    private val paletteB = IntArray(256)

    init {
        var idx = 0
        for (r in 0..5) {
            for (g in 0..5) {
                for (b in 0..5) {
                    val rv = r * 51
                    val gv = g * 51
                    val bv = b * 51
                    paletteR[idx] = rv
                    paletteG[idx] = gv
                    paletteB[idx] = bv
                    palette[idx] = (0xFF shl 24) or (rv shl 16) or (gv shl 8) or bv
                    idx++
                }
            }
        }
        // Fill remaining 40 entries with grayscale ramp
        for (k in 0 until 40) {
            val gray = (k * 255) / 39
            paletteR[idx] = gray
            paletteG[idx] = gray
            paletteB[idx] = gray
            palette[idx] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
            idx++
        }
    }

    fun encode(frames: List<Bitmap>, out: OutputStream) {
        if (frames.isEmpty()) return
        val width = frames.first().width
        val height = frames.first().height

        // GIF89a Header
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShort(out, width)
        writeShort(out, height)
        // Global Color Table Flag (1), Color Res (7), Sort (0), Size of GCT (7 -> 256 colors)
        out.write(0xF7)
        out.write(0x00) // Background color index
        out.write(0x00) // Pixel aspect ratio

        // Global Color Table (256 * 3 bytes)
        for (i in 0 until 256) {
            out.write(paletteR[i])
            out.write(paletteG[i])
            out.write(paletteB[i])
        }

        // Netscape 2.0 Application Extension for looping
        out.write(0x21)
        out.write(0xFF)
        out.write(11)
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(3)
        out.write(1)
        writeShort(out, loopCount)
        out.write(0)

        val delayCs = (delayMs / 10).coerceAtLeast(2)
        val pixels = IntArray(width * height)
        val indexed = ByteArray(width * height)

        for (bitmap in frames) {
            val scaled = if (bitmap.width != width || bitmap.height != height) {
                Bitmap.createScaledBitmap(bitmap, width, height, true)
            } else bitmap

            scaled.getPixels(pixels, 0, width, 0, 0, width, height)
            for (i in pixels.indices) {
                val c = pixels[i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                // Fast uniform cube quantize to 6x6x6 indices (0..215)
                val ri = (r * 5 + 127) / 255
                val gi = (g * 5 + 127) / 255
                val bi = (b * 5 + 127) / 255
                val cubeIdx = ri * 36 + gi * 6 + bi
                // Check if near neutral grayscale for smoother skin/monochrome gradients
                val maxCh = maxOf(r, g, b)
                val minCh = minOf(r, g, b)
                if (maxCh - minCh < 12) {
                    val grayIdx = 216 + ((r + g + b) / 3 * 39) / 255
                    val dr1 = r - paletteR[cubeIdx]
                    val dr2 = r - paletteR[grayIdx]
                    indexed[i] = (if (abs(dr2) < abs(dr1)) grayIdx else cubeIdx).toByte()
                } else {
                    indexed[i] = cubeIdx.toByte()
                }
            }
            if (scaled !== bitmap) scaled.recycle()

            // Graphic Control Extension
            out.write(0x21)
            out.write(0xF9)
            out.write(4)
            out.write(0x04) // disposal method: do not dispose
            writeShort(out, delayCs)
            out.write(0x00) // transparent index
            out.write(0x00)

            // Image Descriptor
            out.write(0x2C)
            writeShort(out, 0)
            writeShort(out, 0)
            writeShort(out, width)
            writeShort(out, height)
            out.write(0x00) // No local color table

            // LZW compressed raster data
            encodeLzw(indexed, out)
        }

        // GIF Trailer
        out.write(0x3B)
        out.flush()
    }

    private fun writeShort(out: OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }

    private fun encodeLzw(pixels: ByteArray, out: OutputStream) {
        val minCodeSize = 8
        out.write(minCodeSize)

        val clearCode = 1 shl minCodeSize // 256
        val eofCode = clearCode + 1 // 257

        val table = HashMap<Int, Int>(4096)
        var codeSize = minCodeSize + 1
        var nextCode = eofCode + 1

        var curAccum = 0
        var curBits = 0
        val block = ByteArray(256)
        var blockLen = 0

        fun flushBlock() {
            if (blockLen > 0) {
                out.write(blockLen)
                out.write(block, 0, blockLen)
                blockLen = 0
            }
        }

        fun emit(code: Int) {
            curAccum = curAccum or (code shl curBits)
            curBits += codeSize
            while (curBits >= 8) {
                block[blockLen++] = (curAccum and 0xFF).toByte()
                if (blockLen == 254) flushBlock()
                curAccum = curAccum ushr 8
                curBits -= 8
            }
        }

        emit(clearCode)

        var prefix = pixels[0].toInt() and 0xFF
        for (i in 1 until pixels.size) {
            val k = pixels[i].toInt() and 0xFF
            val key = (prefix shl 12) or k
            val existing = table[key]
            if (existing != null) {
                prefix = existing
            } else {
                emit(prefix)
                if (nextCode < 4096) {
                    table[key] = nextCode++
                    if (nextCode > (1 shl codeSize) && codeSize < 12) {
                        codeSize++
                    }
                } else {
                    emit(clearCode)
                    table.clear()
                    codeSize = minCodeSize + 1
                    nextCode = eofCode + 1
                }
                prefix = k
            }
        }

        emit(prefix)
        emit(eofCode)
        if (curBits > 0) {
            block[blockLen++] = (curAccum and 0xFF).toByte()
        }
        flushBlock()
        out.write(0) // Block terminator
    }
}
