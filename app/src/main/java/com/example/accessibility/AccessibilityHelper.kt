package com.example.accessibility

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import java.util.Locale

object AccessibilityHelper {

    fun vibrate(context: Context, type: VibrationType) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                when (type) {
                    VibrationType.CLICK -> vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                    VibrationType.TICK -> vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                    VibrationType.HEAVY_CLICK -> vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                    VibrationType.DOUBLE_CLICK -> vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
                    VibrationType.SUCCESS -> {
                        val timings = longArrayOf(0, 80, 50, 120)
                        val amplitudes = intArrayOf(0, 180, 0, 255)
                        vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                when (type) {
                    VibrationType.CLICK, VibrationType.TICK -> vibrator?.vibrate(25)
                    VibrationType.HEAVY_CLICK -> vibrator?.vibrate(60)
                    VibrationType.DOUBLE_CLICK -> vibrator?.vibrate(longArrayOf(0, 30, 40, 30), -1)
                    VibrationType.SUCCESS -> vibrator?.vibrate(longArrayOf(0, 50, 40, 100), -1)
                }
            }
        } catch (_: Throwable) {
            // Safe fallback if device does not support vibrator or permission
        }
    }

    /**
     * Announces a message to screen readers (TalkBack) via accessibility event
     */
    fun announceForAccessibility(context: Context, message: String) {
        try {
            val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            if (accessibilityManager?.isEnabled == true) {
                val event = AccessibilityEvent.obtain().apply {
                    eventType = AccessibilityEvent.TYPE_ANNOUNCEMENT
                    className = context.javaClass.name
                    packageName = context.packageName
                    text.add(message)
                }
                accessibilityManager.sendAccessibilityEvent(event)
            }
        } catch (_: Throwable) {
            // Ignore
        }
    }

    /**
     * Formats milliseconds into high-precision visual time string: "MM:SS.s" (e.g., 01:23.4)
     */
    fun formatTimeVisual(millis: Long): String {
        val safeMillis = millis.coerceAtLeast(0)
        val minutes = (safeMillis / 1000) / 60
        val seconds = (safeMillis / 1000) % 60
        val tenths = (safeMillis % 1000) / 100
        return String.format(Locale.US, "%02d:%02d.%d", minutes, seconds, tenths)
    }

    /**
     * Formats milliseconds into standard "MM:SS" (e.g. 01:23)
     */
    fun formatTimeMMSS(millis: Long): String {
        val safeMillis = millis.coerceAtLeast(0)
        val minutes = (safeMillis / 1000) / 60
        val seconds = (safeMillis / 1000) % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    /**
     * Formats milliseconds into clear spoken words for TalkBack/screen readers:
     * e.g. "1 minute 4 point 5 seconds"
     */
    fun formatTimeSpoken(millis: Long): String {
        val safeMillis = millis.coerceAtLeast(0)
        val totalSecs = safeMillis / 1000
        val minutes = totalSecs / 60
        val seconds = totalSecs % 60
        val tenths = (safeMillis % 1000) / 100

        return buildString {
            if (minutes > 0) {
                append("$minutes minute${if (minutes > 1) "s" else ""} ")
            }
            if (tenths > 0) {
                append("$seconds point $tenths second${if (seconds != 1L || tenths > 0) "s" else ""}")
            } else {
                append("$seconds second${if (seconds != 1L) "s" else ""}")
            }
        }
    }

    /**
     * Formats bytes to readable size: "1.4 MB", "750 KB"
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, index.toDouble())
        return String.format(Locale.US, "%.1f %s", value, units[index])
    }
}

enum class VibrationType {
    CLICK,
    TICK,
    HEAVY_CLICK,
    DOUBLE_CLICK,
    SUCCESS
}
