package com.example.update

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
import java.security.MessageDigest
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

sealed interface ApkInstallCompatibility {
    data object Compatible : ApkInstallCompatibility
    data class SignatureConflict(
        val message: String,
        val installedSha256: String,
        val downloadedSha256: String
    ) : ApkInstallCompatibility
    data class PackageIdMismatch(
        val expectedPackageId: String,
        val downloadedPackageId: String,
        val message: String
    ) : ApkInstallCompatibility
    data class VersionDowngrade(
        val installedVersionCode: Long,
        val downloadedVersionCode: Long,
        val message: String
    ) : ApkInstallCompatibility
    data class UnreadableArchive(val message: String) : ApkInstallCompatibility
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

    fun extractGitHubReleaseApkUrl(jsonBody: String): String? {
        return try {
            val json = JSONObject(jsonBody)
            val assets = json.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val assetName = asset.optString("name", "")
                val url = asset.optString("browser_download_url", "").trim()
                if (url.isNotEmpty() && assetName.endsWith(".apk", ignoreCase = true)) {
                    return resolveDownloadUrl(url)
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

    /**
     * Selects the highest-priority GitHub update check result across `main/version.json`
     * and GitHub `releases/latest`, ensuring an `UpToDate` response from one source never
     * hides a newer `UpdateAvailable` published on the other.
     */
    fun selectBestGitHubUpdateCandidate(
        rawState: UpdateCheckState?,
        releaseState: UpdateCheckState?
    ): UpdateCheckState? {
        val rawUpdate = rawState as? UpdateCheckState.UpdateAvailable
        val releaseUpdate = releaseState as? UpdateCheckState.UpdateAvailable
        if (rawUpdate != null && releaseUpdate != null) {
            return if (isRemoteVersionNewer(
                    currentVersionName = rawUpdate.latestVersion,
                    currentVersionCode = rawUpdate.latestCode.toLong(),
                    remoteVersionName = releaseUpdate.latestVersion,
                    remoteVersionCode = releaseUpdate.latestCode.toLong()
                )
            ) {
                releaseUpdate
            } else {
                rawUpdate
            }
        }
        if (releaseUpdate != null) return releaseUpdate
        if (rawUpdate != null) return rawUpdate
        if (releaseState is UpdateCheckState.UpToDate) return releaseState
        if (rawState is UpdateCheckState.UpToDate) return rawState
        return releaseState ?: rawState
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
            // 1. Check both GitHub main branch raw version.json AND GitHub Releases latest
            if (normalizedProvider == StudioPreferences.UPDATE_PROVIDER_GITHUB ||
                (normalizedProvider == StudioPreferences.UPDATE_PROVIDER_AUTO && hasValidGitHub)
            ) {
                val rawMainUrl = appendCacheBuster(buildGitHubRawVersionUrl(githubOwner, githubRepo, DEFAULT_GITHUB_BRANCH), now)
                val rawMainResult = fetchHttpText(rawMainUrl, acceptHeader = "application/json, text/plain")
                val parsedRaw: UpdateCheckState? = if (rawMainResult.code in 200..299 && !rawMainResult.body.isNullOrBlank()) {
                    parseUpdateResponseJson(
                        jsonBody = rawMainResult.body,
                        currentVersionName = currentVersionName,
                        currentVersionCode = currentVersionCode,
                        checkedAtMs = now,
                        sourceLabel = "GitHub (main)"
                    )
                } else {
                    null
                }

                val releaseApiUrl = buildGitHubLatestReleaseUrl(githubOwner, githubRepo)
                val releaseResult = fetchHttpText(
                    targetUrl = releaseApiUrl,
                    acceptHeader = "application/vnd.github+json"
                )
                val parsedRelease: UpdateCheckState? = if (releaseResult.code in 200..299 && releaseResult.body != null) {
                    val attachedApkUrl = extractGitHubReleaseApkUrl(releaseResult.body)
                    val assetVersionJsonUrl = extractGitHubVersionJsonAssetUrl(releaseResult.body)
                    val assetParsed = if (assetVersionJsonUrl != null) {
                        val assetJsonResult = fetchHttpText(appendCacheBuster(assetVersionJsonUrl, now))
                        if (assetJsonResult.code in 200..299 && assetJsonResult.body != null) {
                            parseUpdateResponseJson(
                                jsonBody = assetJsonResult.body,
                                currentVersionName = currentVersionName,
                                currentVersionCode = currentVersionCode,
                                checkedAtMs = now,
                                sourceLabel = "GitHub Releases"
                            )
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                    when {
                        assetParsed is UpdateCheckState.UpdateAvailable -> {
                            val verifiedApk = attachedApkUrl ?: assetParsed.apkUrl
                            assetParsed.copy(apkUrl = verifiedApk)
                        }
                        assetParsed != null && assetParsed !is UpdateCheckState.Error -> assetParsed
                        else -> parseGitHubReleaseJson(
                            jsonBody = releaseResult.body,
                            currentVersionName = currentVersionName,
                            currentVersionCode = currentVersionCode,
                            checkedAtMs = now
                        )
                    }
                } else {
                    null
                }

                val bestGitHub = selectBestGitHubUpdateCandidate(parsedRaw, parsedRelease)
                if (bestGitHub is UpdateCheckState.UpdateAvailable) {
                    val primaryApk = bestGitHub.apkUrl.ifBlank { bestGitHub.downloadUrl }
                    val isFromRawMain = bestGitHub.sourceLabel == "GitHub (main)"
                    if (!isFromRawMain || verifyApkUrlReachable(primaryApk)) {
                        return@withContext bestGitHub
                    }
                    // raw main version.json points to an APK not yet published on GitHub Releases (404).
                    // Prefer a published GitHub Release whose APK asset actually exists.
                    if (parsedRelease is UpdateCheckState.UpdateAvailable) {
                        val relApk = parsedRelease.apkUrl.ifBlank { parsedRelease.downloadUrl }
                        if (verifyApkUrlReachable(relApk)) {
                            return@withContext parsedRelease
                        }
                    }
                    val publishedRelease = findReachableGitHubReleaseUpdate(
                        owner = githubOwner,
                        repo = githubRepo,
                        currentVersionName = currentVersionName,
                        currentVersionCode = currentVersionCode,
                        checkedAtMs = now
                    )
                    if (publishedRelease != null) {
                        return@withContext publishedRelease
                    }
                    if (parsedRelease is UpdateCheckState.UpToDate) {
                        return@withContext parsedRelease
                    }
                    return@withContext UpdateCheckState.UpToDate(
                        currentVersion = currentVersionName,
                        latestVersion = currentVersionName,
                        checkedAtMs = now,
                        sourceLabel = "Pending Release APK"
                    )
                }
                if (bestGitHub != null && bestGitHub !is UpdateCheckState.Error) {
                    return@withContext bestGitHub
                }

                if (normalizedProvider == StudioPreferences.UPDATE_PROVIDER_GITHUB) {
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

    fun extractSigningCertSha256Set(pkgInfo: PackageInfo?): Set<String> {
        if (pkgInfo == null) return emptySet()
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pkgInfo.signingInfo
            if (signingInfo == null) {
                emptyArray()
            } else {
                val combined = LinkedHashSet<android.content.pm.Signature>()
                signingInfo.apkContentsSigners?.let { combined.addAll(it) }
                if (!signingInfo.hasMultipleSigners()) {
                    signingInfo.signingCertificateHistory?.let { combined.addAll(it) }
                }
                combined.toTypedArray()
            }
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.signatures
        } ?: return emptySet()

        return signatures.mapNotNull { sig ->
            runCatching {
                val digest = MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
                digest.joinToString("") { "%02X".format(it) }
            }.getOrNull()
        }.toSet()
    }

    fun checkPackageInfoCompatibility(
        expectedPackageName: String,
        installedVersionCode: Long,
        installedCertSha256Set: Set<String>,
        downloadedPackageName: String?,
        downloadedVersionCode: Long,
        downloadedCertSha256Set: Set<String>
    ): ApkInstallCompatibility {
        if (downloadedPackageName.isNullOrBlank()) {
            return ApkInstallCompatibility.UnreadableArchive(
                "Downloaded APK archive could not be parsed. Please retry download or share the APK link."
            )
        }
        if (downloadedPackageName != expectedPackageName) {
            return ApkInstallCompatibility.PackageIdMismatch(
                expectedPackageId = expectedPackageName,
                downloadedPackageId = downloadedPackageName,
                message = "Package ID mismatch ($downloadedPackageName vs $expectedPackageName). Uninstall the older conflicting app or verify the release APK."
            )
        }
        if (downloadedVersionCode > 0L && installedVersionCode > 0L && downloadedVersionCode < installedVersionCode) {
            return ApkInstallCompatibility.VersionDowngrade(
                installedVersionCode = installedVersionCode,
                downloadedVersionCode = downloadedVersionCode,
                message = "Downloaded APK version code ($downloadedVersionCode) is lower than installed version code ($installedVersionCode), which Android rejects. Uninstall the existing build first if you want to install an older build."
            )
        }
        if (installedCertSha256Set.isNotEmpty() &&
            downloadedCertSha256Set.isNotEmpty() &&
            installedCertSha256Set.intersect(downloadedCertSha256Set).isEmpty()
        ) {
            val installedShort = installedCertSha256Set.first().take(8)
            val downloadedShort = downloadedCertSha256Set.first().take(8)
            return ApkInstallCompatibility.SignatureConflict(
                message = "Package conflicts with the existing installation because the installed app (key $installedShort…) and this update APK (key $downloadedShort…) were signed with different keys. Android requires a one-time uninstall of the old build before installing this APK; future updates using the same signing key will overwrite in place automatically.",
                installedSha256 = installedCertSha256Set.first(),
                downloadedSha256 = downloadedCertSha256Set.first()
            )
        }
        return ApkInstallCompatibility.Compatible
    }

    fun checkDownloadedApkCompatibility(context: Context, apkFile: File): ApkInstallCompatibility {
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            return ApkInstallCompatibility.UnreadableArchive("Downloaded APK file is missing or empty.")
        }
        return try {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val installedPkg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, flags)
            }
            val archivePkg = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
            }) ?: @Suppress("DEPRECATION") pm.getPackageArchiveInfo(apkFile.absolutePath, 0)

            val installedCode = PackageInfoCompat.getLongVersionCode(installedPkg)
            val archiveCode = archivePkg?.let { PackageInfoCompat.getLongVersionCode(it) } ?: -1L
            checkPackageInfoCompatibility(
                expectedPackageName = context.packageName,
                installedVersionCode = installedCode,
                installedCertSha256Set = extractSigningCertSha256Set(installedPkg),
                downloadedPackageName = archivePkg?.packageName,
                downloadedVersionCode = archiveCode,
                downloadedCertSha256Set = extractSigningCertSha256Set(archivePkg)
            )
        } catch (_: Exception) {
            ApkInstallCompatibility.Compatible
        }
    }

    fun createUninstallCurrentAppIntent(context: Context): Intent {
        return Intent(
            Intent.ACTION_DELETE,
            Uri.fromParts("package", context.packageName, null)
        ).apply {
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    fun createAppDetailsSettingsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).apply {
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    fun openAppInfoForUninstall(context: Context): Boolean {
        return try {
            context.startActivity(createAppDetailsSettingsIntent(context))
            true
        } catch (_: Exception) {
            false
        }
    }

    fun saveApkToDownloads(context: Context, apkFile: File): String? {
        if (!apkFile.exists() || apkFile.length() <= 0L) return null
        val displayName = apkFile.name.ifBlank { "AccessibleStudio_Update.apk" }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                try {
                    resolver.openOutputStream(uri)?.use { out ->
                        apkFile.inputStream().use { input -> input.copyTo(out) }
                    } ?: return null
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    "Downloads/$displayName"
                } catch (_: Exception) {
                    runCatching { resolver.delete(uri, null, null) }
                    null
                }
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val outFile = File(downloadsDir, displayName)
                apkFile.inputStream().use { input ->
                    FileOutputStream(outFile).use { out -> input.copyTo(out) }
                }
                outFile.absolutePath
            }
        } catch (_: Exception) {
            null
        }
    }

    fun uninstallCurrentApp(context: Context): Boolean {
        return try {
            val deleteIntent = createUninstallCurrentAppIntent(context)
            if (deleteIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(deleteIntent)
                true
            } else {
                openAppInfoForUninstall(context)
            }
        } catch (_: Exception) {
            openAppInfoForUninstall(context)
        }
    }

    fun buildDirectApkDownloadUrl(
        owner: String,
        repo: String,
        versionName: String = BuildConfig.VERSION_NAME
    ): String {
        val cleanVersion = versionName.trim().removePrefix("v").removePrefix("V").ifEmpty { BuildConfig.VERSION_NAME }
        return if (isValidGitHubRepo(owner, repo)) {
            "https://github.com/${owner.trim()}/${repo.trim()}/releases/download/v$cleanVersion/AccessibleStudio_V$cleanVersion.apk"
        } else {
            "$BASE_HOST_URL/AccessibleStudio_V$cleanVersion.apk"
        }
    }

    fun resolveShareableApkUrl(
        context: Context,
        updateState: UpdateCheckState = UpdateCheckState.Idle,
        versionNameOverride: String? = null
    ): String {
        if (updateState is UpdateCheckState.UpdateAvailable) {
            val direct = updateState.apkUrl.trim()
            if (isDirectApkUrl(direct)) return resolveDownloadUrl(direct)
            val dl = updateState.downloadUrl.trim()
            if (isDirectApkUrl(dl)) return resolveDownloadUrl(dl)
        }
        val config = loadPublishConfig(context)
        val version = when {
            !versionNameOverride.isNullOrBlank() -> versionNameOverride.trim()
            updateState is UpdateCheckState.UpdateAvailable -> updateState.latestVersion
            else -> BuildConfig.VERSION_NAME
        }
        return buildDirectApkDownloadUrl(config.owner, config.repo, version)
    }

    fun buildShareApkArticleText(
        apkUrl: String,
        versionName: String = BuildConfig.VERSION_NAME
    ): String {
        val resolvedUrl = resolveDownloadUrl(apkUrl)
        val cleanVersion = versionName.trim().removePrefix("v").removePrefix("V").ifEmpty { BuildConfig.VERSION_NAME }
        return buildString {
            appendLine("Accessible Studio (v$cleanVersion) — 100% Free & Offline Audio & Video Editing Studio for Everyone")
            appendLine()
            appendLine("What Accessible Studio Does:")
            appendLine("Accessible Studio is a complete mobile Audio & Video Workstation engineered from the ground up for blind, low-vision (TalkBack), and touch creators alike—working 100% offline with zero ads, zero tracking, and no internet needed for editing or exporting.")
            appendLine()
            appendLine("What Makes Accessible Studio Special:")
            appendLine("• TalkBack & Low-Vision First: Full screen-reader announcements, High Contrast OLED black-and-neon mode, tactile haptic vibration feedback, and large 48dp+ controls.")
            appendLine("• Complete Multi-Track Audio Studio: Record clean anti-crackle voice, open multiple project tabs, Cut/Copy/Mute/Delete/Paste, Mix Audio, Voice Over with Auto-Ducking, and custom Slow & Fast Rewind/Forward speeds (0.25x to 8.0x).")
            appendLine("• Individual Pro Audio Effects & Studio Cleaning: Dedicated one-tap buttons for Echo (Delay), Reverb (Acoustic Space), Dynamics Compressor, Peak Limiter, 7-Band Graphic EQ, Flanger, Chorus, Overdrive & Distortion, Pitch Shifter & Polarity, Studio Adaptive De-Noise, and Auto Gain.")
            appendLine("• Full Video Studio: Separate one-tap buttons for Video Trimmer, Non-Destructive Cutter, Snapshot & Glimpse, Video Merger, Slideshow Creator, Narration & Music Layers, Color Filters & VFX, and single-file MP4 export.")
            appendLine("• Preserves Original File Names & Multi-Format Export: Export crisp WAV, MP3, FLAC, M4A, or MP4 straight to Music/Audio Studio/ and Movies/Accessible Studio/.")
            appendLine()
            appendLine("खास बात (Hindi): Accessible Studio बिना इंटरनेट 100% ऑफ़लाइन काम करता है और TalkBack व टच दोनों के लिए बना है — इसमें ऑडियो रिकॉर्डिंग, मिक्सिंग, अलग-अलग प्रो इफ़ेक्ट्स (Echo, Reverb, 7-Band EQ, Compressor, De-Noise), स्लो/फ़ास्ट रिवाइंड-फॉरवर्ड और पूरा Video Studio एक ही ऐप में मिलता है।")
            appendLine()
            appendLine("Download Accessible Studio v$cleanVersion (Direct Android APK Link):")
            append(resolvedUrl)
        }
    }

    fun createShareApkLinkIntent(
        apkUrl: String,
        versionName: String = BuildConfig.VERSION_NAME
    ): Intent {
        val cleanVersion = versionName.trim().removePrefix("v").removePrefix("V").ifEmpty { BuildConfig.VERSION_NAME }
        val shareText = buildShareApkArticleText(apkUrl = apkUrl, versionName = cleanVersion)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Accessible Studio v$cleanVersion — App Guide & APK Download")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        return Intent.createChooser(sendIntent, "Share Accessible Studio Guide & APK Link").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun shareApkDirectLink(
        context: Context,
        apkUrl: String,
        versionName: String = BuildConfig.VERSION_NAME
    ): Boolean {
        return try {
            context.startActivity(createShareApkLinkIntent(apkUrl, versionName))
            true
        } catch (_: Exception) {
            false
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
     * with redirect following, GitHub Release 404 asset fallback resolution, and progress callbacks.
     * If the endpoint returns an HTML page instead of an APK binary, returns [ApkDownloadResult.OpenInBrowserFallback].
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

        val primaryAttempt = attemptSingleApkDownload(
            resolvedUrl = resolvedUrl,
            tempFile = tempFile,
            targetFile = targetFile,
            onProgress = onProgress
        )
        when (primaryAttempt) {
            is SingleDownloadAttempt.Success -> return@withContext ApkDownloadResult.Success(primaryAttempt.apkFile)
            is SingleDownloadAttempt.BrowserFallback -> return@withContext ApkDownloadResult.OpenInBrowserFallback(primaryAttempt.url)
            is SingleDownloadAttempt.HttpFailure -> {
                if (primaryAttempt.code == HttpURLConnection.HTTP_NOT_FOUND || primaryAttempt.code == 410) {
                    val fallbackCandidates = resolveGitHub404FallbackApkUrls(
                        context = context,
                        failedUrl = resolvedUrl,
                        versionName = versionName
                    )
                    for (candidate in fallbackCandidates) {
                        runCatching { tempFile.delete() }
                        when (val retry = attemptSingleApkDownload(candidate, tempFile, targetFile, onProgress)) {
                            is SingleDownloadAttempt.Success -> return@withContext ApkDownloadResult.Success(retry.apkFile)
                            is SingleDownloadAttempt.BrowserFallback -> return@withContext ApkDownloadResult.OpenInBrowserFallback(retry.url)
                            else -> Unit
                        }
                    }
                    return@withContext ApkDownloadResult.Error(
                        "Download failed (HTTP 404): Release APK was not found on GitHub Releases yet. Wait for the release build to finish or tap Open Releases Page."
                    )
                }
                return@withContext ApkDownloadResult.Error("Download failed (HTTP ${primaryAttempt.code}).")
            }
            is SingleDownloadAttempt.Failure -> return@withContext ApkDownloadResult.Error(primaryAttempt.message)
        }
    }

    private sealed interface SingleDownloadAttempt {
        data class Success(val apkFile: File) : SingleDownloadAttempt
        data class BrowserFallback(val url: String) : SingleDownloadAttempt
        data class HttpFailure(val code: Int) : SingleDownloadAttempt
        data class Failure(val message: String) : SingleDownloadAttempt
    }

    private fun attemptSingleApkDownload(
        resolvedUrl: String,
        tempFile: File,
        targetFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): SingleDownloadAttempt {
        var connection: HttpURLConnection? = null
        return try {
            var currentUrl = resolvedUrl
            var redirects = 0
            while (redirects < 8) {
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
                        return SingleDownloadAttempt.Failure("Blocked non-HTTPS redirect.")
                    }
                    redirects++
                    continue
                }
                connection = conn
                break
            }

            val activeConn = connection
                ?: return SingleDownloadAttempt.Failure("Too many redirects while downloading update.")
            val code = activeConn.responseCode
            if (code !in 200..299) {
                return SingleDownloadAttempt.HttpFailure(code)
            }

            val contentType = activeConn.contentType.orEmpty().lowercase(Locale.ROOT)
            if ("text/html" in contentType) {
                return SingleDownloadAttempt.BrowserFallback(resolvedUrl)
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
                                return if (isDirectApkUrl(resolvedUrl)) {
                                    SingleDownloadAttempt.Failure("Downloaded file is not a valid Android APK package.")
                                } else {
                                    SingleDownloadAttempt.BrowserFallback(resolvedUrl)
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
                return SingleDownloadAttempt.Failure("Downloaded APK file was empty.")
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            SingleDownloadAttempt.Success(targetFile)
        } catch (e: Exception) {
            runCatching { tempFile.delete() }
            val msg = e.localizedMessage?.takeIf { it.isNotBlank() } ?: "Network error during download"
            SingleDownloadAttempt.Failure("Update download failed: $msg")
        } finally {
            connection?.disconnect()
        }
    }

    internal fun extractGitHubOwnerRepoFromUrl(url: String): Pair<String, String>? {
        val match = Regex(
            """^https://github\.com/([^/]+)/([^/]+)(?:/|$)""",
            RegexOption.IGNORE_CASE
        ).find(url.trim()) ?: return null
        val owner = match.groupValues[1]
        val repo = match.groupValues[2]
        return if (isValidGitHubRepo(owner, repo)) owner to repo else null
    }

    private fun resolveGitHub404FallbackApkUrls(
        context: Context,
        failedUrl: String,
        versionName: String
    ): List<String> {
        val cleanVersion = versionName.trim().removePrefix("v").removePrefix("V")
        val repoFromUrl = extractGitHubOwnerRepoFromUrl(failedUrl)
        val config = runCatching { loadPublishConfig(context) }.getOrNull()
        val ownerRepo = repoFromUrl ?: config?.takeIf { isValidGitHubRepo(it.owner, it.repo) }?.let { it.owner to it.repo }
        val candidates = LinkedHashSet<String>()

        if (ownerRepo != null) {
            val (owner, repo) = ownerRepo
            if (cleanVersion.isNotEmpty()) {
                // 1. Check exact tag endpoints (`v1.5.0` or `1.5.0`) on GitHub Releases API to find whatever APK asset filename was uploaded
                listOf(
                    "$GITHUB_API_BASE/repos/$owner/$repo/releases/tags/v$cleanVersion",
                    "$GITHUB_API_BASE/repos/$owner/$repo/releases/tags/$cleanVersion"
                ).forEach { tagUrl ->
                    val tagRes = fetchHttpText(tagUrl, acceptHeader = "application/vnd.github+json")
                    if (tagRes.code in 200..299 && tagRes.body != null) {
                        extractGitHubReleaseApkUrl(tagRes.body)?.let { candidates.add(it) }
                    }
                }
                candidates.add("https://github.com/$owner/$repo/releases/download/$cleanVersion/AccessibleStudio_V$cleanVersion.apk")
            }

            // 2. Check GitHub releases/latest if not older than installed version
            val installed = runCatching { getInstalledAppInfo(context) }.getOrNull()
            val latestRes = fetchHttpText(buildGitHubLatestReleaseUrl(owner, repo), acceptHeader = "application/vnd.github+json")
            if (latestRes.code in 200..299 && latestRes.body != null) {
                val parsed = parseGitHubReleaseJson(
                    jsonBody = latestRes.body,
                    currentVersionName = installed?.versionName ?: BuildConfig.VERSION_NAME,
                    currentVersionCode = installed?.versionCode ?: BuildConfig.VERSION_CODE.toLong()
                )
                if (parsed is UpdateCheckState.UpdateAvailable && isDirectApkUrl(parsed.apkUrl)) {
                    candidates.add(parsed.apkUrl)
                } else {
                    extractGitHubReleaseApkUrl(latestRes.body)?.takeIf {
                        installed == null || parsed !is UpdateCheckState.UpToDate
                    }?.let { candidates.add(it) }
                }
            }
        }

        if (cleanVersion.isNotEmpty()) {
            candidates.add("$BASE_HOST_URL/AccessibleStudio_V$cleanVersion.apk")
        }
        candidates.remove(failedUrl)
        return candidates.filter { it.startsWith("https://", ignoreCase = true) }
    }

    private fun verifyApkUrlReachable(rawUrl: String): Boolean {
        val resolved = resolveDownloadUrl(rawUrl)
        if (!resolved.startsWith("https://", ignoreCase = true)) return false
        return try {
            var currentUrl = resolved
            var redirects = 0
            while (redirects < 8) {
                val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "HEAD"
                    connectTimeout = 5000
                    readTimeout = 5000
                    instanceFollowRedirects = false
                    useCaches = false
                    setRequestProperty("User-Agent", "AccessibleStudio/${BuildConfig.VERSION_NAME}")
                }
                val code = try {
                    conn.responseCode
                } finally {
                    conn.disconnect()
                }
                if (code in listOf(
                        HttpURLConnection.HTTP_MOVED_PERM,
                        HttpURLConnection.HTTP_MOVED_TEMP,
                        HttpURLConnection.HTTP_SEE_OTHER,
                        307,
                        308
                    )
                ) {
                    val next = conn.getHeaderField("Location")?.trim().orEmpty()
                    if (next.isEmpty()) return false
                    currentUrl = if (next.startsWith("http", ignoreCase = true)) next else resolveDownloadUrl(next)
                    if (!currentUrl.startsWith("https://", ignoreCase = true)) return false
                    redirects++
                    continue
                }
                if (code in 200..299) {
                    val contentType = conn.contentType.orEmpty().lowercase(Locale.ROOT)
                    return "text/html" !in contentType
                }
                if (code == 405 || code == 403) {
                    // Fallback tiny range GET when server disallows HEAD
                    val getConn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 5000
                        readTimeout = 5000
                        instanceFollowRedirects = false
                        useCaches = false
                        setRequestProperty("Range", "bytes=0-0")
                        setRequestProperty("User-Agent", "AccessibleStudio/${BuildConfig.VERSION_NAME}")
                    }
                    return try {
                        val getCode = getConn.responseCode
                        getCode in 200..299 || getCode == 206
                    } finally {
                        getConn.disconnect()
                    }
                }
                return false
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun findReachableGitHubReleaseUpdate(
        owner: String,
        repo: String,
        currentVersionName: String,
        currentVersionCode: Long,
        checkedAtMs: Long
    ): UpdateCheckState.UpdateAvailable? {
        val listUrl = "$GITHUB_API_BASE/repos/${owner.trim()}/${repo.trim()}/releases?per_page=5"
        val res = fetchHttpText(listUrl, acceptHeader = "application/vnd.github+json")
        if (res.code !in 200..299 || res.body.isNullOrBlank()) return null
        return try {
            val arr = JSONArray(res.body)
            for (i in 0 until arr.length()) {
                val rel = arr.optJSONObject(i) ?: continue
                if (rel.optBoolean("draft", false)) continue
                val parsed = parseGitHubReleaseJson(
                    jsonBody = rel.toString(),
                    currentVersionName = currentVersionName,
                    currentVersionCode = currentVersionCode,
                    checkedAtMs = checkedAtMs
                )
                if (parsed is UpdateCheckState.UpdateAvailable &&
                    isDirectApkUrl(parsed.apkUrl) &&
                    verifyApkUrlReachable(parsed.apkUrl)
                ) {
                    return parsed
                }
            }
            null
        } catch (_: Exception) {
            null
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
