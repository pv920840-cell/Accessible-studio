package com.example.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.example.BuildConfig
import com.example.data.StudioPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
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

data class InstalledAppInfo(
    val versionName: String,
    val versionCode: Long
)

sealed interface ApkDownloadResult {
    data class Success(val apkFile: File) : ApkDownloadResult
    data class OpenInBrowserFallback(val url: String) : ApkDownloadResult
    data class Error(val message: String) : ApkDownloadResult
}

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
        val sourceLabel: String = "",
        val apkUrl: String = downloadUrl
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
    const val DEFAULT_DOWNLOAD_PAGE_URL = "$BASE_HOST_URL/"
    const val ASSET_UPDATE_CONFIG = "update_config.json"
    const val DEFAULT_GITHUB_BRANCH = "main"

    private const val GITHUB_RAW_BASE = "https://raw.githubusercontent.com"
    private const val GITHUB_API_BASE = "https://api.github.com"
    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 12000
    private const val UPDATES_CACHE_DIR = "updates"

    private val PLACEHOLDER_OWNERS = setOf(
        "", "your-github-username", "your_github_username", "username", "owner", "your-username"
    )
    private val PLACEHOLDER_REPOS = setOf(
        "", "your-repo-name", "your_repo_name", "repo", "repository", "your-repo"
    )
    private val VALID_GITHUB_SEGMENT = Regex("""^[A-Za-z0-9._-]+$""")

    fun getInstalledAppInfo(context: Context): InstalledAppInfo {
        return try {
            val pm = context.packageManager
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            val code = PackageInfoCompat.getLongVersionCode(pkgInfo)
            val name = pkgInfo.versionName?.trim().orEmpty().ifEmpty { BuildConfig.VERSION_NAME }
            InstalledAppInfo(
                versionName = name,
                versionCode = if (code > 0L) code else BuildConfig.VERSION_CODE.toLong()
            )
        } catch (_: Exception) {
            InstalledAppInfo(
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE.toLong()
            )
        }
    }

    fun isValidGitHubRepo(owner: String?, repo: String?): Boolean {
        val cleanOwner = owner?.trim().orEmpty()
        val cleanRepo = repo?.trim().orEmpty()
        if (cleanOwner.lowercase(Locale.ROOT) in PLACEHOLDER_OWNERS) return false
        if (cleanRepo.lowercase(Locale.ROOT) in PLACEHOLDER_REPOS) return false
        return VALID_GITHUB_SEGMENT.matches(cleanOwner) && VALID_GITHUB_SEGMENT.matches(cleanRepo)
    }

    fun buildGitHubRawVersionUrl(
        owner: String,
        repo: String,
        branch: String = DEFAULT_GITHUB_BRANCH
    ): String {
        return "$GITHUB_RAW_BASE/${owner.trim()}/${repo.trim()}/${branch.trim().ifEmpty { DEFAULT_GITHUB_BRANCH }}/version.json"
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

    fun isRemoteVersionNewer(
        currentVersionName: String,
        currentVersionCode: Int,
        remoteVersionName: String?,
        remoteVersionCode: Int
    ): Boolean {
        return isRemoteVersionNewer(
            currentVersionName = currentVersionName,
            currentVersionCode = currentVersionCode.toLong(),
            remoteVersionName = remoteVersionName,
            remoteVersionCode = remoteVersionCode.toLong()
        )
    }

    /**
     * Compares remote versionCode against installed versionCode first;
     * if versionCode is equal or unused (<= 0), falls back safely to dotted semantic versionName comparison.
     */
    fun isRemoteVersionNewer(
        currentVersionName: String,
        currentVersionCode: Long,
        remoteVersionName: String?,
        remoteVersionCode: Long
    ): Boolean {
        if (remoteVersionCode > currentVersionCode) return true
        if (remoteVersionCode in 1L until currentVersionCode) return false

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
                return false
            }
        }
        return false
    }

    fun resolveDownloadUrl(rawUrl: String?): String {
        val trimmed = rawUrl?.trim().orEmpty()
        if (trimmed.isEmpty()) return DEFAULT_DOWNLOAD_PAGE_URL
        if (trimmed.startsWith("https://", ignoreCase = true)) {
            return trimmed
        }
        if (trimmed.startsWith("http://", ignoreCase = true)) {
            return "https://" + trimmed.substring(7)
        }
        if (trimmed.contains("://") || trimmed.startsWith("javascript:", ignoreCase = true)) {
            return DEFAULT_DOWNLOAD_PAGE_URL
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
        return parseUpdateResponseJson(
            jsonBody = jsonBody,
            currentVersionName = currentVersionName,
            currentVersionCode = currentVersionCode.toLong(),
            checkedAtMs = checkedAtMs,
            sourceLabel = sourceLabel
        )
    }

    fun parseUpdateResponseJson(
        jsonBody: String,
        currentVersionName: String,
        currentVersionCode: Long,
        checkedAtMs: Long = System.currentTimeMillis(),
        sourceLabel: String = "Server"
    ): UpdateCheckState {
        return try {
            val json = JSONObject(jsonBody)
            val remoteVersionName = json.optString("versionName", "").trim()
            val remoteVersionCode = json.optLong("versionCode", currentVersionCode)
            if (remoteVersionName.isEmpty() && remoteVersionCode <= 0L) {
                return UpdateCheckState.Error("Update metadata is incomplete.", checkedAtMs)
            }
            val effectiveRemoteName = remoteVersionName.ifEmpty { currentVersionName }
            val rawApkUrl = json.optString("apkUrl", "").trim()
            val rawPageUrl = json.optString("pageUrl", "").trim()
            val downloadUrl = resolveDownloadUrl(rawPageUrl.ifBlank { rawApkUrl })
            val directApkUrl = resolveDownloadUrl(rawApkUrl.ifBlank { rawPageUrl })
            val releaseNotes = json.optString("changelog", "").trim()
                .ifEmpty { json.optString("releaseNotes", "").trim() }

            if (isRemoteVersionNewer(currentVersionName, currentVersionCode, effectiveRemoteName, remoteVersionCode)) {
                UpdateCheckState.UpdateAvailable(
                    currentVersion = currentVersionName,
                    latestVersion = effectiveRemoteName,
                    latestCode = remoteVersionCode.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    downloadUrl = downloadUrl,
                    releaseNotes = releaseNotes.ifEmpty { "Bug fixes and improvements in version $effectiveRemoteName." },
                    checkedAtMs = checkedAtMs,
                    sourceLabel = sourceLabel,
                    apkUrl = directApkUrl
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
        return parseGitHubReleaseJson(
            jsonBody = jsonBody,
            currentVersionName = currentVersionName,
            currentVersionCode = currentVersionCode.toLong(),
            checkedAtMs = checkedAtMs
        )
    }

    fun parseGitHubReleaseJson(
        jsonBody: String,
        currentVersionName: String,
        currentVersionCode: Long,
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
                val assetDownloadUrl = asset.optString("browser_download_url", "").trim()
                if (assetDownloadUrl.isNotEmpty() && assetName.endsWith(".apk", ignoreCase = true)) {
                    apkAssetUrl = assetDownloadUrl
                    break
                }
            }
            val downloadUrl = resolveDownloadUrl(htmlUrl.ifEmpty { apkAssetUrl })
            val directApkUrl = resolveDownloadUrl(apkAssetUrl.ifEmpty { htmlUrl })
            val notes = json.optString("body", "").trim()
                .ifEmpty { releaseName }
                .ifEmpty { "A newer GitHub release is available." }

            if (isRemoteVersionNewer(currentVersionName, currentVersionCode, cleanRemoteVersion, currentVersionCode)) {
                UpdateCheckState.UpdateAvailable(
                    currentVersion = currentVersionName,
                    latestVersion = cleanRemoteVersion,
                    latestCode = currentVersionCode.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    downloadUrl = downloadUrl,
                    releaseNotes = notes,
                    checkedAtMs = checkedAtMs,
                    sourceLabel = "GitHub Releases",
                    apkUrl = directApkUrl
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
        currentVersionCode: Long = BuildConfig.VERSION_CODE.toLong(),
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
                latestCode = currentVersionCode.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                downloadUrl = DEFAULT_DOWNLOAD_PAGE_URL,
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
        currentVersionCode: Long = BuildConfig.VERSION_CODE.toLong(),
        provider: String = StudioPreferences.UPDATE_PROVIDER_AUTO,
        githubOwner: String = "",
        githubRepo: String = ""
    ): UpdateCheckState = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val normalizedProvider = StudioPreferences.normalizeUpdateProvider(provider)
        val hasValidGitHub = isValidGitHubRepo(githubOwner, githubRepo)

        if (normalizedProvider == StudioPreferences.UPDATE_PROVIDER_GITHUB && !hasValidGitHub) {
            return@withContext UpdateCheckState.Error(
                "Enter your GitHub username and repository name in Update Source settings to check GitHub Updates.",
                now
            )
        }

        try {
            // 1. Check GitHub main branch raw version.json + GitHub Releases when configured
            if (normalizedProvider == StudioPreferences.UPDATE_PROVIDER_GITHUB ||
                (normalizedProvider == StudioPreferences.UPDATE_PROVIDER_AUTO && hasValidGitHub)
            ) {
                val rawMainUrl = appendCacheBuster(buildGitHubRawVersionUrl(githubOwner, githubRepo, DEFAULT_GITHUB_BRANCH), now)
                val rawMainResult = fetchHttpText(rawMainUrl, acceptHeader = "application/json, text/plain")
                if (rawMainResult.code in 200..299 && !rawMainResult.body.isNullOrBlank()) {
                    val parsedRaw = parseUpdateResponseJson(
                        jsonBody = rawMainResult.body,
                        currentVersionName = currentVersionName,
                        currentVersionCode = currentVersionCode,
                        checkedAtMs = now,
                        sourceLabel = "GitHub (main)"
                    )
                    if (parsedRaw !is UpdateCheckState.Error) {
                        return@withContext parsedRaw
                    }
                }

                // Fallback to GitHub latest release API if main/version.json is absent or malformed
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
                                jsonBody = assetJsonResult.body,
                                currentVersionName = currentVersionName,
                                currentVersionCode = currentVersionCode,
                                checkedAtMs = now,
                                sourceLabel = "GitHub Releases"
                            )
                            if (parsedAsset !is UpdateCheckState.Error) {
                                return@withContext parsedAsset
                            }
                        }
                    }
                    return@withContext parseGitHubReleaseJson(
                        jsonBody = releaseResult.body,
                        currentVersionName = currentVersionName,
                        currentVersionCode = currentVersionCode,
                        checkedAtMs = now
                    )
                } else if (normalizedProvider == StudioPreferences.UPDATE_PROVIDER_GITHUB) {
                    val code = if (rawMainResult.code in 400..599 && releaseResult.code <= 0) rawMainResult.code else releaseResult.code
                    val errorHint = when (code) {
                        404 -> "No version.json or GitHub releases found for ${githubOwner.trim()}/${githubRepo.trim()}."
                        403 -> "GitHub rate limit reached. Try again shortly."
                        in 400..599 -> "GitHub returned HTTP $code."
                        else -> "Could not reach GitHub. Check your internet connection."
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

    fun isDirectApkUrl(url: String): Boolean {
        val clean = url.substringBefore('?').substringBefore('#').trim()
        return clean.endsWith(".apk", ignoreCase = true)
    }

    fun canInstallUnknownApps(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                context.packageManager.canRequestPackageInstalls()
            } catch (_: Exception) {
                false
            }
        } else {
            true
        }
    }

    fun createManageUnknownAppSourcesIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    fun createInstallApkIntent(context: Context, apkFile: File): Intent {
        val authority = "${context.packageName}.fileprovider"
        val apkUri = FileProvider.getUriForFile(context, authority, apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun installDownloadedApk(context: Context, apkFile: File): Boolean {
        return try {
            if (!apkFile.exists() || apkFile.length() <= 0L) return false
            val intent = createInstallApkIntent(context, apkFile)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Downloads the APK from [apkUrl] into app-specific cache storage (`cacheDir/updates`)
     * with redirect following and progress callbacks. If the endpoint returns an HTML page
     * instead of an APK binary, returns [ApkDownloadResult.OpenInBrowserFallback].
     */
    suspend fun downloadApkToCache(
        context: Context,
        apkUrl: String,
        versionName: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): ApkDownloadResult = withContext(Dispatchers.IO) {
        val resolvedUrl = resolveDownloadUrl(apkUrl)
        if (!resolvedUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext ApkDownloadResult.Error("Invalid update download URL.")
        }
        val updatesDir = File(context.cacheDir, UPDATES_CACHE_DIR).apply {
            if (!exists()) mkdirs()
        }
        // Clean stale APKs in updates cache
        updatesDir.listFiles()?.forEach { oldFile ->
            if (oldFile.isFile && oldFile.name.endsWith(".apk", ignoreCase = true)) {
                runCatching { oldFile.delete() }
            }
        }

        val safeVersion = versionName.replace(Regex("""[^A-Za-z0-9._-]"""), "_").ifBlank { "latest" }
        val tempFile = File(updatesDir, "AccessibleStudio_V$safeVersion.apk.tmp")
        val targetFile = File(updatesDir, "AccessibleStudio_V$safeVersion.apk")

        var connection: HttpURLConnection? = null
        try {
            var currentUrl = resolvedUrl
            var redirects = 0
            while (redirects < 6) {
                val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = 30_000
                    instanceFollowRedirects = false
                    useCaches = false
                    setRequestProperty("Accept", "application/vnd.android.package-archive, application/octet-stream, */*")
                    setRequestProperty("User-Agent", "AccessibleStudio/${BuildConfig.VERSION_NAME}")
                }
                val code = conn.responseCode
                if (code in listOf(
                        HttpURLConnection.HTTP_MOVED_PERM,
                        HttpURLConnection.HTTP_MOVED_TEMP,
                        HttpURLConnection.HTTP_SEE_OTHER,
                        307,
                        308
                    )
                ) {
                    val next = conn.getHeaderField("Location")?.trim().orEmpty()
                    conn.disconnect()
                    if (next.isEmpty()) break
                    currentUrl = if (next.startsWith("http", ignoreCase = true)) next else resolveDownloadUrl(next)
                    if (!currentUrl.startsWith("https://", ignoreCase = true)) {
                        return@withContext ApkDownloadResult.Error("Blocked non-HTTPS redirect.")
                    }
                    redirects++
                    continue
                }
                connection = conn
                break
            }

            val activeConn = connection ?: return@withContext ApkDownloadResult.Error("Too many redirects while downloading update.")
            val code = activeConn.responseCode
            if (code !in 200..299) {
                return@withContext ApkDownloadResult.Error("Download failed (HTTP $code).")
            }

            val contentType = activeConn.contentType.orEmpty().lowercase(Locale.ROOT)
            if ("text/html" in contentType) {
                return@withContext ApkDownloadResult.OpenInBrowserFallback(resolvedUrl)
            }

            val totalBytes = activeConn.contentLengthLong.coerceAtLeast(-1L)
            var downloaded = 0L
            val buffer = ByteArray(16 * 1024)

            BufferedInputStream(activeConn.inputStream).use { input ->
                FileOutputStream(tempFile).use { output ->
                    var firstRead = true
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        if (firstRead) {
                            firstRead = false
                            // Verify ZIP/APK signature ('PK') cleanly
                            if (read >= 2 && (buffer[0] != 0x50.toByte() || buffer[1] != 0x4B.toByte())) {
                                return@withContext if (isDirectApkUrl(resolvedUrl)) {
                                    ApkDownloadResult.Error("Downloaded file is not a valid Android APK package.")
                                } else {
                                    ApkDownloadResult.OpenInBrowserFallback(resolvedUrl)
                                }
                            }
                        }
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, totalBytes)
                    }
                    output.flush()
                }
            }

            if (downloaded <= 0L) {
                runCatching { tempFile.delete() }
                return@withContext ApkDownloadResult.Error("Downloaded APK file was empty.")
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            ApkDownloadResult.Success(targetFile)
        } catch (e: Exception) {
            runCatching { tempFile.delete() }
            val msg = e.localizedMessage?.takeIf { it.isNotBlank() } ?: "Network error during download"
            ApkDownloadResult.Error("Update download failed: $msg")
        } finally {
            connection?.disconnect()
        }
    }

    fun openUpdateDownload(context: Context, url: String): Boolean {
        return try {
            val resolved = resolveDownloadUrl(url)
            if (!resolved.startsWith("https://", ignoreCase = true)) return false
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

    fun appendCacheBuster(url: String, timestampMs: Long = System.currentTimeMillis()): String {
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
