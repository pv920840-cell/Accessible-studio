package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.BuildConfig
import com.example.data.StudioPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PublishConfig(
    val appId: String = BuildConfig.APPLICATION_ID,
    val provider: String = "github",
    val owner: String = "",
    val repo: String = "",
    val fallbackMetadataUrl: String = AppUpdateChecker.DEFAULT_METADATA_URL
)

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState

    data class Checking(val isAutomatic: Boolean = false) : UpdateCheckState

    data class UpToDate(
        val currentVersion: String,
        val latestVersion: String,
        val checkedAtMs: Long,
        val sourceLabel: String = ""
    ) : UpdateCheckState

    data class UpdateAvailable(
        val currentVersion: String,
        val latestVersion: String,
        val latestCode: Int,
        val downloadUrl: String,
        val releaseNotes: String,
        val checkedAtMs: Long,
        val sourceLabel: String = ""
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
    const val ASSET_UPDATE_CONFIG = "update_config.json"

    private const val GITHUB_API_BASE = "https://api.github.com"
    private const val CONNECT_TIMEOUT_MS = 6000
    private const val READ_TIMEOUT_MS = 6000

    private val PLACEHOLDER_OWNERS = setOf(
        "", "your-github-username", "your_github_username", "username", "owner", "your-username"
    )
    private val PLACEHOLDER_REPOS = setOf(
        "", "your-repo-name", "your_repo_name", "repo", "repository", "your-repo"
    )
    private val VALID_GITHUB_SEGMENT = Regex("""^[A-Za-z0-9._-]+$""")

    fun isValidGitHubRepo(owner: String?, repo: String?): Boolean {
        val cleanOwner = owner?.trim().orEmpty()
        val cleanRepo = repo?.trim().orEmpty()
        if (cleanOwner.lowercase(Locale.ROOT) in PLACEHOLDER_OWNERS) return false
        if (cleanRepo.lowercase(Locale.ROOT) in PLACEHOLDER_REPOS) return false
        return VALID_GITHUB_SEGMENT.matches(cleanOwner) && VALID_GITHUB_SEGMENT.matches(cleanRepo)
    }

    fun buildGitHubLatestReleaseUrl(owner: String, repo: String): String {
        return "$GITHUB_API_BASE/repos/${owner.trim()}/${repo.trim()}/releases/latest"
    }

    fun parsePublishConfigJson(jsonBody: String): PublishConfig? {
        return try {
            val root = JSONObject(jsonBody)
            val buildObj = root.optJSONObject("build") ?: root
            val publishObj = buildObj.optJSONObject("publish") ?: JSONObject()
            val appId = buildObj.optString("appId", BuildConfig.APPLICATION_ID)
                .trim()
                .ifEmpty { BuildConfig.APPLICATION_ID }
            val provider = publishObj.optString("provider", "github")
                .trim()
                .lowercase(Locale.ROOT)
                .ifEmpty { "github" }
            val owner = publishObj.optString("owner", "").trim()
            val repo = publishObj.optString("repo", "").trim()
            val fallbackUrl = publishObj.optString("fallbackMetadataUrl", DEFAULT_METADATA_URL)
                .trim()
                .ifEmpty { DEFAULT_METADATA_URL }

            PublishConfig(
                appId = appId,
                provider = provider,
                owner = if (owner.lowercase(Locale.ROOT) in PLACEHOLDER_OWNERS) "" else owner,
                repo = if (repo.lowercase(Locale.ROOT) in PLACEHOLDER_REPOS) "" else repo,
                fallbackMetadataUrl = fallbackUrl
            )
        } catch (_: Exception) {
            null
        }
    }

    fun loadPublishConfig(context: Context): PublishConfig {
        val assetConfig = try {
            context.assets.open(ASSET_UPDATE_CONFIG).bufferedReader(Charsets.UTF_8).use {
                parsePublishConfigJson(it.readText())
            }
        } catch (_: Exception) {
            null
        } ?: PublishConfig()

        val prefs = StudioPreferences.loadSettings(context)
        val mergedOwner = prefs.githubOwner.ifBlank { assetConfig.owner }
        val mergedRepo = prefs.githubRepo.ifBlank { assetConfig.repo }
        val mergedProvider = when (prefs.updateProvider) {
            StudioPreferences.UPDATE_PROVIDER_GITHUB -> StudioPreferences.UPDATE_PROVIDER_GITHUB
            StudioPreferences.UPDATE_PROVIDER_SERVER -> StudioPreferences.UPDATE_PROVIDER_SERVER
            else -> if (isValidGitHubRepo(mergedOwner, mergedRepo)) {
                StudioPreferences.UPDATE_PROVIDER_GITHUB
            } else {
                StudioPreferences.UPDATE_PROVIDER_SERVER
            }
        }

        return assetConfig.copy(
            provider = mergedProvider,
            owner = mergedOwner,
            repo = mergedRepo
        )
    }

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
        checkedAtMs: Long = System.currentTimeMillis(),
        sourceLabel: String = "Server"
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
                    checkedAtMs = checkedAtMs,
                    sourceLabel = sourceLabel
                )
            } else {
                UpdateCheckState.UpToDate(
                    currentVersion = currentVersionName,
                    latestVersion = effectiveRemoteName,
                    checkedAtMs = checkedAtMs,
                    sourceLabel = sourceLabel
                )
            }
        } catch (_: Exception) {
            UpdateCheckState.Error("Invalid update response format.", checkedAtMs)
        }
    }

    fun parseGitHubReleaseJson(
        jsonBody: String,
        currentVersionName: String = BuildConfig.VERSION_NAME,
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
        checkedAtMs: Long = System.currentTimeMillis()
    ): UpdateCheckState {
        return try {
            val json = JSONObject(jsonBody)
            if (json.has("message") && !json.has("tag_name")) {
                val msg = json.optString("message", "GitHub release not found").trim()
                return UpdateCheckState.Error("GitHub Releases: $msg", checkedAtMs)
            }

            val rawTag = json.optString("tag_name", "").trim()
            val releaseName = json.optString("name", "").trim()
            val cleanRemoteVersion = extractVersionFromGitHubRelease(rawTag, releaseName)
            if (cleanRemoteVersion.isEmpty()) {
                return UpdateCheckState.Error("GitHub release tag is missing a valid version.", checkedAtMs)
            }

            val htmlUrl = json.optString("html_url", "").trim()
            val assets = json.optJSONArray("assets") ?: JSONArray()
            var apkAssetUrl = ""
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val assetName = asset.optString("name", "")
                val downloadUrl = asset.optString("browser_download_url", "").trim()
                if (downloadUrl.isNotEmpty() && assetName.endsWith(".apk", ignoreCase = true)) {
                    apkAssetUrl = downloadUrl
                    break
                }
            }
            val downloadUrl = apkAssetUrl.ifEmpty { htmlUrl }.ifEmpty { DEFAULT_APK_URL }
            val notes = json.optString("body", "").trim()
                .ifEmpty { releaseName }
                .ifEmpty { "A newer GitHub release is available." }

            if (isRemoteVersionNewer(currentVersionName, currentVersionCode, cleanRemoteVersion, currentVersionCode)) {
                UpdateCheckState.UpdateAvailable(
                    currentVersion = currentVersionName,
                    latestVersion = cleanRemoteVersion,
                    latestCode = currentVersionCode,
                    downloadUrl = downloadUrl,
                    releaseNotes = notes,
                    checkedAtMs = checkedAtMs,
                    sourceLabel = "GitHub Releases"
                )
            } else {
                UpdateCheckState.UpToDate(
                    currentVersion = currentVersionName,
                    latestVersion = cleanRemoteVersion,
                    checkedAtMs = checkedAtMs,
                    sourceLabel = "GitHub Releases"
                )
            }
        } catch (_: Exception) {
            UpdateCheckState.Error("Invalid GitHub Releases response format.", checkedAtMs)
        }
    }

    fun extractGitHubVersionJsonAssetUrl(jsonBody: String): String? {
        return try {
            val json = JSONObject(jsonBody)
            val assets = json.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                if (asset.optString("name", "").equals("version.json", ignoreCase = true)) {
                    val url = asset.optString("browser_download_url", "").trim()
                    if (url.isNotEmpty()) return url
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun extractVersionFromGitHubRelease(tagName: String, releaseName: String): String {
        val candidates = listOf(tagName, releaseName)
        for (candidate in candidates) {
            val match = Regex("""v?(\d+(?:\.\d+)+)""", RegexOption.IGNORE_CASE).find(candidate.trim())
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return tagName.removePrefix("v").removePrefix("V").trim()
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
                checkedAtMs = checkedAtMs,
                sourceLabel = "Download Page"
            )
        } else {
            UpdateCheckState.UpToDate(
                currentVersion = currentVersionName,
                latestVersion = remoteName,
                checkedAtMs = checkedAtMs,
                sourceLabel = "Download Page"
            )
        }
    }

    suspend fun checkForUpdates(
        metadataUrl: String = DEFAULT_METADATA_URL,
        fallbackHtmlUrl: String = DEFAULT_FALLBACK_HTML_URL,
        currentVersionName: String = BuildConfig.VERSION_NAME,
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
        provider: String = StudioPreferences.UPDATE_PROVIDER_AUTO,
        githubOwner: String = "",
        githubRepo: String = ""
    ): UpdateCheckState = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val normalizedProvider = StudioPreferences.normalizeUpdateProvider(provider)
        val hasValidGitHub = isValidGitHubRepo(githubOwner, githubRepo)

        if (normalizedProvider == StudioPreferences.UPDATE_PROVIDER_GITHUB && !hasValidGitHub) {
            return@withContext UpdateCheckState.Error(
                "Enter your GitHub username and repository name in Update Source settings to check GitHub Releases.",
                now
            )
        }

        try {
            // 1. Check GitHub Releases if selected or configured
            if (normalizedProvider == StudioPreferences.UPDATE_PROVIDER_GITHUB ||
                (normalizedProvider == StudioPreferences.UPDATE_PROVIDER_AUTO && hasValidGitHub)
            ) {
                val releaseApiUrl = buildGitHubLatestReleaseUrl(githubOwner, githubRepo)
                val releaseResult = fetchHttpText(
                    targetUrl = releaseApiUrl,
                    acceptHeader = "application/vnd.github+json"
                )
                if (releaseResult.code in 200..299 && releaseResult.body != null) {
                    val assetVersionJsonUrl = extractGitHubVersionJsonAssetUrl(releaseResult.body)
                    if (assetVersionJsonUrl != null) {
                        val assetJsonResult = fetchHttpText(appendCacheBuster(assetVersionJsonUrl, now))
                        if (assetJsonResult.code in 200..299 && assetJsonResult.body != null) {
                            val parsedAsset = parseUpdateResponseJson(
                                assetJsonResult.body,
                                currentVersionName,
                                currentVersionCode,
                                now,
                                sourceLabel = "GitHub Releases"
                            )
                            if (parsedAsset !is UpdateCheckState.Error) {
                                return@withContext parsedAsset
                            }
                        }
                    }
                    return@withContext parseGitHubReleaseJson(
                        releaseResult.body,
                        currentVersionName,
                        currentVersionCode,
                        now
                    )
                } else if (normalizedProvider == StudioPreferences.UPDATE_PROVIDER_GITHUB) {
                    val errorHint = when (releaseResult.code) {
                        404 -> "No published GitHub releases found for ${githubOwner.trim()}/${githubRepo.trim()}."
                        403 -> "GitHub API rate limit reached. Try again shortly."
                        in 400..599 -> "GitHub returned HTTP ${releaseResult.code}."
                        else -> "Could not reach GitHub Releases. Check your internet connection."
                    }
                    return@withContext UpdateCheckState.Error(errorHint, now)
                }
            }

            // 2. Fallback / Default: Hosted version.json + index.html
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
            val resolved = if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
                url
            } else {
                resolveDownloadUrl(url)
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(resolved)).apply {
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

    private data class HttpFetchResult(val code: Int, val body: String?)

    private fun fetchText(targetUrl: String): String? {
        val res = fetchHttpText(targetUrl)
        return if (res.code in 200..299) res.body else null
    }

    private fun fetchHttpText(
        targetUrl: String,
        acceptHeader: String = "application/json, text/html"
    ): HttpFetchResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                useCaches = false
                setRequestProperty("Accept", acceptHeader)
                setRequestProperty("User-Agent", "AccessibleStudio/${BuildConfig.VERSION_NAME}")
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { reader -> reader.readText() }
            }
            HttpFetchResult(code, body)
        } catch (_: Exception) {
            HttpFetchResult(-1, null)
        } finally {
            connection?.disconnect()
        }
    }
}
