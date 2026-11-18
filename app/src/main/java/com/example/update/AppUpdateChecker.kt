package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState

    data class Checking(val isAutomatic: Boolean = false) : UpdateCheckState

    data class UpToDate(
        val currentVersion: String,
        val latestVersion: String,
        val checkedAtMs: Long
    ) : UpdateCheckState

    data class UpdateAvailable(
        val currentVersion: String,
        val latestVersion: String,
        val latestCode: Int,
        val downloadUrl: String,
        val releaseNotes: String,
        val checkedAtMs: Long
    ) : UpdateCheckState

    data class Error(
        val message: String,
        val checkedAtMs: Long
    ) : UpdateCheckState
}

object AppUpdateChecker {
    const val BASE_HOST_URL = "https://ais-pre-3czeatab7cb5he5dvgdk6n-875513992455.asia-east1.run.app"
    const val DEFAULT_METADATA_URL = "$BASE_HOST_URL/version.json"
    const val DEFAULT_FALLBACK_HTML_URL = "$BASE_HOST_URL/index.html"
    const val DEFAULT_APK_URL = "$BASE_HOST_URL/AccessibleStudio_V1.2.apk"

    private const val CONNECT_TIMEOUT_MS = 6000
    private const val READ_TIMEOUT_MS = 6000

    /**
     * Compares dotted numeric version strings (such as "1.2.20261118.091803") and versionCode.
     * Returns true strictly when remote is newer than current.
     */
    fun isRemoteVersionNewer(
        currentVersionName: String,
        currentVersionCode: Int,
        remoteVersionName: String?,
        remoteVersionCode: Int
    ): Boolean {
        val cleanRemote = remoteVersionName?.trim()?.removePrefix("v")?.removePrefix("V").orEmpty()
        val cleanCurrent = currentVersionName.trim().removePrefix("v").removePrefix("V")

        if (cleanRemote.isNotEmpty() && cleanCurrent.isNotEmpty()) {
            val remoteParts = cleanRemote.split('.').mapNotNull { it.toLongOrNull() }
            val currentParts = cleanCurrent.split('.').mapNotNull { it.toLongOrNull() }
            if (remoteParts.isNotEmpty() && currentParts.isNotEmpty()) {
                val maxLen = maxOf(remoteParts.size, currentParts.size)
                for (i in 0 until maxLen) {
                    val r = remoteParts.getOrElse(i) { 0L }
                    val c = currentParts.getOrElse(i) { 0L }
                    if (r > c) return true
                    if (r < c) return false
                }
            } else if (cleanRemote != cleanCurrent) {
                return remoteVersionCode > currentVersionCode
            }
        }

        return remoteVersionCode > currentVersionCode
    }

    fun resolveDownloadUrl(rawUrl: String?): String {
        val trimmed = rawUrl?.trim().orEmpty()
        if (trimmed.isEmpty()) return DEFAULT_APK_URL
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return trimmed
        }
        return if (trimmed.startsWith("/")) {
            "$BASE_HOST_URL$trimmed"
        } else {
            "$BASE_HOST_URL/$trimmed"
        }
    }

    fun parseUpdateResponseJson(
        jsonBody: String,
        currentVersionName: String = BuildConfig.VERSION_NAME,
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
        checkedAtMs: Long = System.currentTimeMillis()
    ): UpdateCheckState {
        return try {
            val json = JSONObject(jsonBody)
            val remoteVersionName = json.optString("versionName", "").trim()
            val remoteVersionCode = json.optInt("versionCode", currentVersionCode)
            if (remoteVersionName.isEmpty() && remoteVersionCode <= 0) {
                return UpdateCheckState.Error("Update metadata is incomplete.", checkedAtMs)
            }
            val effectiveRemoteName = remoteVersionName.ifEmpty { currentVersionName }
            val downloadUrl = resolveDownloadUrl(
                json.optString("apkUrl").ifBlank { json.optString("pageUrl") }
            )
            val releaseNotes = json.optString("releaseNotes", "").trim()

            if (isRemoteVersionNewer(currentVersionName, currentVersionCode, effectiveRemoteName, remoteVersionCode)) {
                UpdateCheckState.UpdateAvailable(
                    currentVersion = currentVersionName,
                    latestVersion = effectiveRemoteName,
                    latestCode = remoteVersionCode,
                    downloadUrl = downloadUrl,
                    releaseNotes = releaseNotes.ifEmpty { "A newer build of Accessible Studio is available." },
                    checkedAtMs = checkedAtMs
                )
            } else {
                UpdateCheckState.UpToDate(
                    currentVersion = currentVersionName,
                    latestVersion = effectiveRemoteName,
                    checkedAtMs = checkedAtMs
                )
            }
        } catch (e: Exception) {
            UpdateCheckState.Error("Invalid update response format.", checkedAtMs)
        }
    }

    fun parseFallbackHtml(
        htmlBody: String,
        currentVersionName: String = BuildConfig.VERSION_NAME,
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
        checkedAtMs: Long = System.currentTimeMillis()
    ): UpdateCheckState {
        val timestampedMatch = Regex("""\b(\d+\.\d+\.\d{8}\.\d{6})\b""").find(htmlBody)
        val shortMatch = Regex("""AccessibleStudio_V(\d+\.\d+(?:\.\d+)*)\.apk""", RegexOption.IGNORE_CASE).find(htmlBody)
        val remoteName = timestampedMatch?.groupValues?.getOrNull(1)
            ?: shortMatch?.groupValues?.getOrNull(1)
            ?: return UpdateCheckState.Error("Could not verify latest version from download page.", checkedAtMs)

        return if (isRemoteVersionNewer(currentVersionName, currentVersionCode, remoteName, currentVersionCode)) {
            UpdateCheckState.UpdateAvailable(
                currentVersion = currentVersionName,
                latestVersion = remoteName,
                latestCode = currentVersionCode,
                downloadUrl = DEFAULT_APK_URL,
                releaseNotes = "A newer build of Accessible Studio is available on the download page.",
                checkedAtMs = checkedAtMs
            )
        } else {
            UpdateCheckState.UpToDate(
                currentVersion = currentVersionName,
                latestVersion = remoteName,
                checkedAtMs = checkedAtMs
            )
        }
    }

    suspend fun checkForUpdates(
        metadataUrl: String = DEFAULT_METADATA_URL,
        fallbackHtmlUrl: String = DEFAULT_FALLBACK_HTML_URL,
        currentVersionName: String = BuildConfig.VERSION_NAME,
        currentVersionCode: Int = BuildConfig.VERSION_CODE
    ): UpdateCheckState = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        try {
            val cacheBustedJsonUrl = appendCacheBuster(metadataUrl, now)
            val jsonResponse = fetchText(cacheBustedJsonUrl)
            if (jsonResponse != null) {
                val parsed = parseUpdateResponseJson(jsonResponse, currentVersionName, currentVersionCode, now)
                if (parsed !is UpdateCheckState.Error) {
                    return@withContext parsed
                }
            }

            val htmlResponse = fetchText(appendCacheBuster(fallbackHtmlUrl, now))
            if (htmlResponse != null) {
                return@withContext parseFallbackHtml(htmlResponse, currentVersionName, currentVersionCode, now)
            }

            UpdateCheckState.Error("Update server did not respond. Check your internet connection.", now)
        } catch (e: Exception) {
            val detail = e.localizedMessage?.takeIf { it.isNotBlank() } ?: "No internet connection"
            UpdateCheckState.Error("Unable to check for updates ($detail).", now)
        }
    }

    fun openUpdateDownload(context: Context, url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(resolveDownloadUrl(url))).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun formatCheckedTime(timestampMs: Long): String {
        if (timestampMs <= 0L) return ""
        return try {
            SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestampMs))
        } catch (_: Exception) {
            ""
        }
    }

    private fun appendCacheBuster(url: String, timestampMs: Long): String {
        return if ('?' in url) "$url&t=$timestampMs" else "$url?t=$timestampMs"
    }

    private fun fetchText(targetUrl: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                useCaches = false
                setRequestProperty("Accept", "application/json, text/html")
                setRequestProperty("User-Agent", "AccessibleStudio/${BuildConfig.VERSION_NAME}")
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                null
            } else {
                BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            }
        } finally {
            connection?.disconnect()
        }
    }
}
