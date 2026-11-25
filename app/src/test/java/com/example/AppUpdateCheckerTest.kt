package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.StudioPreferences
import com.example.data.StudioSettingsState
import com.example.ui.components.AccessibilitySettingsSheet
import com.example.ui.components.AppUpdateAvailableDialog
import com.example.ui.components.InAppUpdateFlowState
import com.example.update.ApkInstallCompatibility
import com.example.update.AppUpdateChecker
import com.example.update.UpdateCheckState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppUpdateCheckerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun preferences_persistAutoCheckUpdatesAndLastCheckMetadata() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Default is enabled
        StudioPreferences.saveSettings(
            context,
            StudioSettingsState(isHighContrast = false, isHapticsEnabled = true, defaultExportFormat = "WAV")
        )
        assertTrue(StudioPreferences.loadSettings(context).autoCheckUpdates)
        assertTrue(StudioPreferences.isAutoCheckUpdatesEnabled(context))

        // Toggle off and verify persistence
        StudioPreferences.setAutoCheckUpdatesEnabled(context, false)
        assertFalse(StudioPreferences.isAutoCheckUpdatesEnabled(context))
        assertFalse(StudioPreferences.loadSettings(context).autoCheckUpdates)

        // Last check metadata persistence
        val checkedAt = 1763457600000L
        StudioPreferences.saveLastUpdateCheck(context, checkedAt, "1.2.20261118.101500")
        assertEquals(checkedAt, StudioPreferences.loadLastUpdateCheckMs(context))
        assertEquals("1.2.20261118.101500", StudioPreferences.loadLastKnownRemoteVersion(context))
    }

    @Test
    fun versionComparison_correctlyDetectsNewerEqualAndOlderVersions() {
        assertTrue(
            AppUpdateChecker.isRemoteVersionNewer(
                currentVersionName = "1.2.20261118.091803",
                currentVersionCode = 16,
                remoteVersionName = "1.2.20261118.101500",
                remoteVersionCode = 16
            )
        )
        assertTrue(
            AppUpdateChecker.isRemoteVersionNewer(
                currentVersionName = "1.2.20261118.091803",
                currentVersionCode = 16,
                remoteVersionName = "1.3.0",
                remoteVersionCode = 17
            )
        )
        assertFalse(
            AppUpdateChecker.isRemoteVersionNewer(
                currentVersionName = "1.2.20261118.091803",
                currentVersionCode = 16,
                remoteVersionName = "1.2.20261118.091803",
                remoteVersionCode = 16
            )
        )
        assertFalse(
            AppUpdateChecker.isRemoteVersionNewer(
                currentVersionName = "1.2.20261118.091803",
                currentVersionCode = 16,
                remoteVersionName = "1.2.20261118.080000",
                remoteVersionCode = 16
            )
        )
        assertTrue(
            AppUpdateChecker.isRemoteVersionNewer(
                currentVersionName = "1.2.20261118.091803",
                currentVersionCode = 16,
                remoteVersionName = "1.2.20261118.091803",
                remoteVersionCode = 17
            )
        )
    }

    @Test
    fun parseUpdateResponseJson_handlesAvailableUpToDateAndInvalidJson() {
        val newerJson = """
            {
              "versionCode": 17,
              "versionName": "1.2.20261119.120000",
              "apkUrl": "/AccessibleStudio_V1.2.apk",
              "releaseNotes": "Faster export and TalkBack enhancements."
            }
        """.trimIndent()
        val available = AppUpdateChecker.parseUpdateResponseJson(
            jsonBody = newerJson,
            currentVersionName = "1.2.20261118.091803",
            currentVersionCode = 16,
            checkedAtMs = 1000L
        )
        assertTrue(available is UpdateCheckState.UpdateAvailable)
        val update = available as UpdateCheckState.UpdateAvailable
        assertEquals("1.2.20261119.120000", update.latestVersion)
        assertEquals(
            "${AppUpdateChecker.BASE_HOST_URL}/AccessibleStudio_V1.2.apk",
            update.downloadUrl
        )
        assertEquals("Faster export and TalkBack enhancements.", update.releaseNotes)

        val sameJson = """
            {
              "versionCode": 16,
              "versionName": "1.2.20261118.091803",
              "pageUrl": "${AppUpdateChecker.DEFAULT_DOWNLOAD_PAGE_URL}"
            }
        """.trimIndent()
        val upToDate = AppUpdateChecker.parseUpdateResponseJson(
            jsonBody = sameJson,
            currentVersionName = "1.2.20261118.091803",
            currentVersionCode = 16,
            checkedAtMs = 2000L
        )
        assertTrue(upToDate is UpdateCheckState.UpToDate)

        val invalid = AppUpdateChecker.parseUpdateResponseJson(
            jsonBody = "not-json",
            currentVersionName = "1.2.20261118.091803",
            currentVersionCode = 16,
            checkedAtMs = 3000L
        )
        assertTrue(invalid is UpdateCheckState.Error)
    }

    @Test
    fun settingsAbout_autoCheckToggleCheckButtonAndDownloadActionWork() {
        var toggledStates = mutableListOf<Boolean>()
        var checkClicks = 0
        var downloadedUrl: String? = null

        composeRule.setContent {
            var autoCheck by remember { mutableStateOf(true) }
            var state by remember { mutableStateOf<UpdateCheckState>(UpdateCheckState.Idle) }

            AccessibilitySettingsSheet(
                isHighContrast = false,
                isHapticsEnabled = true,
                defaultExportFormat = "WAV",
                autoCheckUpdates = autoCheck,
                updateCheckState = state,
                onToggleAutoCheckUpdates = { enabled ->
                    autoCheck = enabled
                    toggledStates.add(enabled)
                },
                onCheckForUpdates = {
                    checkClicks++
                    state = UpdateCheckState.UpdateAvailable(
                        currentVersion = "1.2.20261118.091803",
                        latestVersion = "1.2.20261119.010101",
                        latestCode = 17,
                        downloadUrl = AppUpdateChecker.DEFAULT_DOWNLOAD_PAGE_URL,
                        releaseNotes = "New update ready.",
                        checkedAtMs = 5000L
                    )
                },
                onDownloadUpdate = { url -> downloadedUrl = url },
                onSaveSettings = { _, _, _ -> },
                onDismiss = {}
            )
        }
        composeRule.waitForIdle()

        // Toggle switch inside Settings -> About
        composeRule.onNodeWithTag("switch_auto_check_updates")
            .performScrollTo()
            .assertExists()
            .assertIsOn()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("switch_auto_check_updates").assertIsOff()
        assertEquals(listOf(false), toggledStates)

        // Manual Check for Updates button
        composeRule.onNodeWithTag("btn_about_check_updates")
            .performScrollTo()
            .assertExists()
            .performClick()
        composeRule.waitForIdle()
        assertEquals(1, checkClicks)

        // Status updates and exposes Download Update button
        composeRule.onNodeWithTag("txt_about_update_status")
            .performScrollTo()
            .assertTextContains("Update available: v1.2.20261119.010101", substring = true)
        composeRule.onNodeWithTag("btn_about_download_update")
            .performScrollTo()
            .assertExists()
            .performClick()
        composeRule.waitForIdle()
        assertEquals(AppUpdateChecker.DEFAULT_DOWNLOAD_PAGE_URL, downloadedUrl)
    }

    @Test
    fun settingsAbout_showsUpToDateAndErrorStatesCleanly() {
        composeRule.setContent {
            var state by remember {
                mutableStateOf<UpdateCheckState>(
                    UpdateCheckState.UpToDate(
                        currentVersion = "1.2.20261118.091803",
                        latestVersion = "1.2.20261118.091803",
                        checkedAtMs = 0L
                    )
                )
            }
            AccessibilitySettingsSheet(
                isHighContrast = false,
                isHapticsEnabled = true,
                defaultExportFormat = "WAV",
                autoCheckUpdates = true,
                updateCheckState = state,
                onCheckForUpdates = {
                    state = UpdateCheckState.Error("No internet connection.", checkedAtMs = 123L)
                },
                onSaveSettings = { _, _, _ -> },
                onDismiss = {}
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("txt_about_update_status")
            .performScrollTo()
            .assertTextContains("You are up to date (v1.2.20261118.091803).", substring = true)
        composeRule.onNodeWithTag("btn_about_download_update").assertDoesNotExist()

        composeRule.onNodeWithTag("btn_about_check_updates").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("txt_about_update_status")
            .performScrollTo()
            .assertTextContains("No internet connection.", substring = true)
    }

    @Test
    fun publishConfig_parsesBuildPublishBlockAndRejectsPlaceholders() {
        assertFalse(AppUpdateChecker.isValidGitHubRepo("your-github-username", "your-repo-name"))
        assertFalse(AppUpdateChecker.isValidGitHubRepo("", "accessible-studio"))
        assertFalse(AppUpdateChecker.isValidGitHubRepo("octocat/bad", "accessible-studio"))
        assertTrue(AppUpdateChecker.isValidGitHubRepo("octocat", "accessible-studio"))

        val placeholderConfig = AppUpdateChecker.parsePublishConfigJson(
            """
            {
              "build": {
                "appId": "com.aistudio.accessiblestudio.qmztxp",
                "publish": {
                  "provider": "github",
                  "owner": "your-github-username",
                  "repo": "your-repo-name"
                }
              }
            }
            """.trimIndent()
        )
        assertNotNull(placeholderConfig)
        assertEquals("com.aistudio.accessiblestudio.qmztxp", placeholderConfig?.appId)
        assertEquals("github", placeholderConfig?.provider)
        assertEquals("", placeholderConfig?.owner)
        assertEquals("", placeholderConfig?.repo)

        val realConfig = AppUpdateChecker.parsePublishConfigJson(
            """
            {
              "build": {
                "appId": "com.aistudio.accessiblestudio.qmztxp",
                "publish": {
                  "provider": "github",
                  "owner": "accessible-dev",
                  "repo": "accessible-studio-android"
                }
              }
            }
            """.trimIndent()
        )
        assertEquals("accessible-dev", realConfig?.owner)
        assertEquals("accessible-studio-android", realConfig?.repo)
        assertEquals(
            "https://api.github.com/repos/accessible-dev/accessible-studio-android/releases/latest",
            AppUpdateChecker.buildGitHubLatestReleaseUrl("accessible-dev", "accessible-studio-android")
        )
    }

    @Test
    fun parseGitHubReleaseJson_extractsApkAssetTagVersionAndReleaseNotes() {
        val releaseJson = """
            {
              "tag_name": "v1.2.20261119.120000",
              "name": "Accessible Studio 1.2.20261119.120000",
              "body": "GitHub Release with Audio Effects speed boost.",
              "html_url": "https://github.com/accessible-dev/accessible-studio/releases/tag/v1.2.20261119.120000",
              "assets": [
                {
                  "name": "version.json",
                  "browser_download_url": "https://github.com/accessible-dev/accessible-studio/releases/download/v1.2.20261119.120000/version.json"
                },
                {
                  "name": "AccessibleStudio_V1.2.apk",
                  "browser_download_url": "https://github.com/accessible-dev/accessible-studio/releases/download/v1.2.20261119.120000/AccessibleStudio_V1.2.apk"
                }
              ]
            }
        """.trimIndent()

        assertEquals(
            "https://github.com/accessible-dev/accessible-studio/releases/download/v1.2.20261119.120000/version.json",
            AppUpdateChecker.extractGitHubVersionJsonAssetUrl(releaseJson)
        )

        val state = AppUpdateChecker.parseGitHubReleaseJson(
            jsonBody = releaseJson,
            currentVersionName = "1.2.20261118.092951",
            currentVersionCode = 16,
            checkedAtMs = 9999L
        )
        assertTrue(state is UpdateCheckState.UpdateAvailable)
        val update = state as UpdateCheckState.UpdateAvailable
        assertEquals("1.2.20261119.120000", update.latestVersion)
        assertEquals(
            "https://github.com/accessible-dev/accessible-studio/releases/tag/v1.2.20261119.120000",
            update.downloadUrl
        )
        assertEquals("GitHub Release with Audio Effects speed boost.", update.releaseNotes)
    }

    @Test
    fun resolveDownloadUrl_enforcesHttpsAndRejectsUnsafeSchemes() {
        assertEquals(
            "https://example.com/release",
            AppUpdateChecker.resolveDownloadUrl("http://example.com/release")
        )
        assertEquals(
            AppUpdateChecker.DEFAULT_DOWNLOAD_PAGE_URL,
            AppUpdateChecker.resolveDownloadUrl("javascript:alert(1)")
        )
        assertEquals(
            AppUpdateChecker.DEFAULT_DOWNLOAD_PAGE_URL,
            AppUpdateChecker.resolveDownloadUrl("file:///sdcard/evil.apk")
        )
    }

    @Test
    fun settingsAbout_githubPublishSourceSavesOwnerAndRepo() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var savedProvider = ""
        var savedOwner = ""
        var savedRepo = ""
        var checked = false

        composeRule.setContent {
            var provider by remember { mutableStateOf(StudioPreferences.UPDATE_PROVIDER_AUTO) }
            var owner by remember { mutableStateOf("") }
            var repo by remember { mutableStateOf("") }

            AccessibilitySettingsSheet(
                isHighContrast = false,
                isHapticsEnabled = true,
                defaultExportFormat = "WAV",
                autoCheckUpdates = true,
                updateProvider = provider,
                githubOwner = owner,
                githubRepo = repo,
                onSaveGitHubSource = { p, o, r ->
                    provider = p
                    owner = o
                    repo = r
                    savedProvider = p
                    savedOwner = o
                    savedRepo = r
                    StudioPreferences.saveGitHubUpdateSource(context, p, o, r)
                },
                onCheckForUpdates = { checked = true },
                onSaveSettings = { _, _, _ -> },
                onDismiss = {}
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("btn_update_source_github").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("input_github_owner").performScrollTo().performTextInput("accessible-dev")
        composeRule.onNodeWithTag("input_github_repo").performScrollTo().performTextInput("accessible-studio")
        composeRule.onNodeWithTag("btn_save_github_source").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(StudioPreferences.UPDATE_PROVIDER_GITHUB, savedProvider)
        assertEquals("accessible-dev", savedOwner)
        assertEquals("accessible-studio", savedRepo)
        assertTrue(checked)

        val loaded = StudioPreferences.loadSettings(context)
        assertEquals(StudioPreferences.UPDATE_PROVIDER_GITHUB, loaded.updateProvider)
        assertEquals("accessible-dev", loaded.githubOwner)
        assertEquals("accessible-studio", loaded.githubRepo)
    }

    @Test
    fun githubRawMainVersionUrl_andCacheBuster_andChangelogField_workCorrectly() {
        val rawUrl = AppUpdateChecker.buildGitHubRawVersionUrl("accessible-dev", "accessible-studio")
        assertEquals(
            "https://raw.githubusercontent.com/accessible-dev/accessible-studio/main/version.json",
            rawUrl
        )
        val cacheBusted = AppUpdateChecker.appendCacheBuster(rawUrl, 1763800000000L)
        assertEquals(
            "https://raw.githubusercontent.com/accessible-dev/accessible-studio/main/version.json?t=1763800000000",
            cacheBusted
        )

        val changelogJson = """
            {
              "versionName": "1.2.8",
              "versionCode": 21,
              "apkUrl": "https://github.com/accessible-dev/accessible-studio/releases/download/v1.2.8/AccessibleStudio_V1.2.8.apk",
              "changelog": "Bug fixes and faster export."
            }
        """.trimIndent()
        val parsed = AppUpdateChecker.parseUpdateResponseJson(
            jsonBody = changelogJson,
            currentVersionName = "1.2.7",
            currentVersionCode = 20L,
            checkedAtMs = 1000L,
            sourceLabel = "GitHub (main)"
        )
        assertTrue(parsed is UpdateCheckState.UpdateAvailable)
        val update = parsed as UpdateCheckState.UpdateAvailable
        assertEquals("1.2.8", update.latestVersion)
        assertEquals(21, update.latestCode)
        assertEquals("Bug fixes and faster export.", update.releaseNotes)
        assertEquals(
            "https://github.com/accessible-dev/accessible-studio/releases/download/v1.2.8/AccessibleStudio_V1.2.8.apk",
            update.apkUrl
        )
    }

    @Test
    fun installedAppInfo_andLongVersionCodeComparison_workSafely() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val installed = AppUpdateChecker.getInstalledAppInfo(context)
        assertTrue(installed.versionName.isNotBlank())
        assertTrue(installed.versionCode > 0L)

        // Higher remote versionCode always wins
        assertTrue(
            AppUpdateChecker.isRemoteVersionNewer(
                currentVersionName = "1.2.7",
                currentVersionCode = 20L,
                remoteVersionName = "1.2.7",
                remoteVersionCode = 21L
            )
        )
        // Lower positive remote versionCode is never newer
        assertFalse(
            AppUpdateChecker.isRemoteVersionNewer(
                currentVersionName = "1.2.7",
                currentVersionCode = 20L,
                remoteVersionName = "9.9.9",
                remoteVersionCode = 19L
            )
        )
        // Equal versionCode or <= 0 falls back cleanly to semantic versionName comparison
        assertTrue(
            AppUpdateChecker.isRemoteVersionNewer(
                currentVersionName = "1.2.7",
                currentVersionCode = 20L,
                remoteVersionName = "1.2.8",
                remoteVersionCode = 0L
            )
        )
        assertFalse(
            AppUpdateChecker.isRemoteVersionNewer(
                currentVersionName = "1.2.7",
                currentVersionCode = 20L,
                remoteVersionName = "1.2.7",
                remoteVersionCode = 20L
            )
        )
    }

    @Test
    fun manifestPermissions_andFileProviderInstallIntent_areProperlyConfigured() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val perms = pkgInfo.requestedPermissions?.toSet().orEmpty()
        assertTrue(Manifest.permission.INTERNET in perms)
        assertTrue(Manifest.permission.REQUEST_INSTALL_PACKAGES in perms)

        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val dummyApk = File(updatesDir, "AccessibleStudio_V1.2.8.apk").apply {
            writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
        }
        try {
            val installIntent = AppUpdateChecker.createInstallApkIntent(context, dummyApk)
            assertEquals(Intent.ACTION_VIEW, installIntent.action)
            assertEquals("application/vnd.android.package-archive", installIntent.type)
            assertTrue((installIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
            assertEquals("${context.packageName}.fileprovider", installIntent.data?.authority)
        } finally {
            dummyApk.delete()
        }
    }

    @Test
    fun appUpdateDialog_rendersChangelogProgressPermissionAndInstallStates() {
        val sampleUpdate = UpdateCheckState.UpdateAvailable(
            currentVersion = "1.2.7",
            latestVersion = "1.2.8",
            latestCode = 21,
            downloadUrl = "https://github.com/accessible-dev/accessible-studio/releases/download/v1.2.8/AccessibleStudio_V1.2.8.apk",
            releaseNotes = "Studio polish and bug fixes.",
            checkedAtMs = 5000L,
            sourceLabel = "GitHub (main)",
            apkUrl = "https://github.com/accessible-dev/accessible-studio/releases/download/v1.2.8/AccessibleStudio_V1.2.8.apk"
        )
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dummyApk = File(context.cacheDir, "AccessibleStudio_V1.2.8.apk")
        var updateNowClicked = 0
        var laterClicked = 0
        var settingsClicked = 0
        var installedApkFile: File? = null

        var flowState by mutableStateOf<InAppUpdateFlowState>(InAppUpdateFlowState.Idle)

        composeRule.setContent {
            AppUpdateAvailableDialog(
                update = sampleUpdate,
                flowState = flowState,
                onUpdateNow = { updateNowClicked++ },
                onOpenInstallPermissionSettings = { settingsClicked++ },
                onInstallDownloadedApk = { installedApkFile = it },
                onLater = { laterClicked++ }
            )
        }
        composeRule.waitForIdle()

        // 1. Idle state displays versionName, changelog, Update Now & Later
        composeRule.onNodeWithTag("dialog_update_title").assertTextContains("v1.2.8", substring = true)
        composeRule.onNodeWithTag("txt_update_changelog").assertTextContains("Studio polish and bug fixes.")
        composeRule.onNodeWithTag("btn_dialog_update_now").assertExists().performClick()
        composeRule.onNodeWithTag("btn_dialog_update_later").assertExists().performClick()
        composeRule.waitForIdle()
        assertEquals(1, updateNowClicked)
        assertEquals(1, laterClicked)

        // 2. Downloading progress state
        flowState = InAppUpdateFlowState.Downloading(
            downloadedBytes = 5L * 1024L * 1024L,
            totalBytes = 10L * 1024L * 1024L
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("txt_update_download_progress").assertTextContains("50%", substring = true)
        composeRule.onNodeWithTag("progress_update_download").assertExists()

        // 3. Unknown Apps Permission guidance state
        flowState = InAppUpdateFlowState.NeedsUnknownAppsPermission(dummyApk)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("card_unknown_apps_guidance").assertExists()
        composeRule.onNodeWithTag("btn_dialog_open_install_settings").assertExists().performClick()
        composeRule.waitForIdle()
        assertEquals(1, settingsClicked)

        // 4. Ready To Install state
        flowState = InAppUpdateFlowState.ReadyToInstall(dummyApk)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("txt_update_ready_install").assertExists()
        composeRule.onNodeWithTag("btn_dialog_install_apk").assertExists().performClick()
        composeRule.waitForIdle()
        assertEquals(dummyApk, installedApkFile)
    }

    @Test
    fun checkPackageInfoCompatibility_detectsSignatureConflictPackageMismatchAndDowngrade() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val compatible = AppUpdateChecker.checkPackageInfoCompatibility(
            expectedPackageName = context.packageName,
            installedVersionCode = 21L,
            installedCertSha256Set = setOf("AABBCC"),
            downloadedPackageName = context.packageName,
            downloadedVersionCode = 22L,
            downloadedCertSha256Set = setOf("AABBCC")
        )
        assertTrue(compatible is ApkInstallCompatibility.Compatible)

        val sigConflict = AppUpdateChecker.checkPackageInfoCompatibility(
            expectedPackageName = context.packageName,
            installedVersionCode = 21L,
            installedCertSha256Set = setOf("AABBCC"),
            downloadedPackageName = context.packageName,
            downloadedVersionCode = 22L,
            downloadedCertSha256Set = setOf("DDEEFF")
        )
        assertTrue(sigConflict is ApkInstallCompatibility.SignatureConflict)

        val pkgMismatch = AppUpdateChecker.checkPackageInfoCompatibility(
            expectedPackageName = context.packageName,
            installedVersionCode = 21L,
            installedCertSha256Set = setOf("AABBCC"),
            downloadedPackageName = "com.other.app",
            downloadedVersionCode = 22L,
            downloadedCertSha256Set = setOf("AABBCC")
        )
        assertTrue(pkgMismatch is ApkInstallCompatibility.PackageIdMismatch)

        val downgrade = AppUpdateChecker.checkPackageInfoCompatibility(
            expectedPackageName = context.packageName,
            installedVersionCode = 22L,
            installedCertSha256Set = setOf("AABBCC"),
            downloadedPackageName = context.packageName,
            downloadedVersionCode = 20L,
            downloadedCertSha256Set = setOf("AABBCC")
        )
        assertTrue(downgrade is ApkInstallCompatibility.VersionDowngrade)
    }

    @Test
    fun uninstallIntent_andShareApkDirectLinkIntent_areConstructedProperly() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val uninstallIntent = AppUpdateChecker.createUninstallCurrentAppIntent(context)
        assertEquals(Intent.ACTION_DELETE, uninstallIntent.action)
        assertEquals("package:${context.packageName}", uninstallIntent.dataString)

        val directUrl = AppUpdateChecker.buildDirectApkDownloadUrl(
            owner = "pv920840-cell",
            repo = "Accessible-studio",
            versionName = "1.3.0"
        )
        assertEquals(
            "https://github.com/pv920840-cell/Accessible-studio/releases/download/v1.3.0/AccessibleStudio_V1.3.0.apk",
            directUrl
        )

        val chooserIntent = AppUpdateChecker.createShareApkLinkIntent(directUrl, "1.3.0")
        assertEquals(Intent.ACTION_CHOOSER, chooserIntent.action)
        val innerIntent = androidx.core.content.IntentCompat.getParcelableExtra(
            chooserIntent,
            Intent.EXTRA_INTENT,
            Intent::class.java
        )
        assertNotNull(innerIntent)
        assertEquals(Intent.ACTION_SEND, innerIntent?.action)
        assertEquals("text/plain", innerIntent?.type)
        val sharedText = innerIntent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        assertTrue(sharedText.contains(directUrl))
        assertTrue(sharedText.contains("v1.3.0"))
        assertTrue(sharedText.contains("What Accessible Studio Does:"))
        assertTrue(sharedText.contains("What Makes Accessible Studio Special:"))
        assertTrue(sharedText.contains("TalkBack"))
        assertTrue(sharedText.contains("Video Studio"))
        assertFalse(sharedText.contains("Karaoke", ignoreCase = true))
        assertFalse(sharedText.contains("Vocal Remover", ignoreCase = true))
    }

    @Test
    fun appUpdateDialog_packageConflictAndSettingsSheet_exposeUninstallAndShareApkButtons() {
        val sampleUpdate = UpdateCheckState.UpdateAvailable(
            currentVersion = "1.2.8",
            latestVersion = "1.2.9",
            latestCode = 22,
            downloadUrl = "https://github.com/pv920840-cell/Accessible-studio/releases/download/v1.2.9/AccessibleStudio_V1.2.9.apk",
            releaseNotes = "Conflict fix and share APK link.",
            checkedAtMs = 6000L,
            sourceLabel = "GitHub (main)",
            apkUrl = "https://github.com/pv920840-cell/Accessible-studio/releases/download/v1.2.9/AccessibleStudio_V1.2.9.apk"
        )
        val tempConflictApk = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "conflict_update.apk").apply {
            writeText("apk-bytes")
        }
        var uninstallClicks = 0
        var saveApkClicks = 0
        var dialogShareClicks = 0
        var settingsShareClicks = 0
        var showDialog by mutableStateOf(true)

        composeRule.setContent {
            if (showDialog) {
                AppUpdateAvailableDialog(
                    update = sampleUpdate,
                    flowState = InAppUpdateFlowState.PackageConflict(
                        message = "Installed build uses a different signing key.",
                        apkFile = tempConflictApk
                    ),
                    onUpdateNow = {},
                    onOpenInstallPermissionSettings = {},
                    onInstallDownloadedApk = {},
                    onLater = { showDialog = false },
                    onUninstallCurrentApp = { uninstallClicks++ },
                    onSaveApkToDownloads = { saveApkClicks++ },
                    onShareApkLink = { dialogShareClicks++ }
                )
            } else {
                AccessibilitySettingsSheet(
                    isHighContrast = false,
                    isHapticsEnabled = true,
                    defaultExportFormat = "WAV",
                    onSaveSettings = { _, _, _ -> },
                    onShareApkLink = { settingsShareClicks++ },
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("card_update_package_conflict").assertExists()
        composeRule.onNodeWithTag("txt_update_package_conflict")
            .assertTextContains("different signing key", substring = true)
        composeRule.onNodeWithTag("btn_dialog_save_apk_downloads").performScrollTo().assertExists().performClick()
        composeRule.onNodeWithTag("btn_dialog_uninstall_existing").assertExists().performClick()
        composeRule.onNodeWithTag("btn_dialog_share_apk_link").performScrollTo().assertExists().performClick()
        composeRule.waitForIdle()
        assertEquals(1, saveApkClicks)
        assertEquals(1, uninstallClicks)
        assertEquals(1, dialogShareClicks)

        // Dismiss dialog to switch to Settings sheet and test About Share APK Direct Link button
        composeRule.onNodeWithTag("btn_dialog_update_later").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("btn_about_share_apk_link").performScrollTo().assertExists().performClick()
        composeRule.waitForIdle()
        assertEquals(1, settingsShareClicks)
    }

    @Test
    fun selectBestGitHubUpdateCandidate_prefersNewerReleaseOverMainUpToDate() {
        val rawUpToDate = UpdateCheckState.UpToDate(
            currentVersion = "1.3.0",
            latestVersion = "1.3.0",
            checkedAtMs = 1000L,
            sourceLabel = "GitHub (main)"
        )
        val releaseUpdate = UpdateCheckState.UpdateAvailable(
            currentVersion = "1.3.0",
            latestVersion = "1.4.0",
            latestCode = 24,
            downloadUrl = "https://github.com/pv920840-cell/Accessible-studio/releases/download/v1.4.0/AccessibleStudio_V1.4.0.apk",
            releaseNotes = "v1.4.0 release",
            checkedAtMs = 1000L,
            sourceLabel = "GitHub Releases"
        )
        val olderRawUpdate = releaseUpdate.copy(
            latestVersion = "1.3.1",
            latestCode = 23,
            sourceLabel = "GitHub (main)"
        )

        val chosenOverUpToDate = AppUpdateChecker.selectBestGitHubUpdateCandidate(rawUpToDate, releaseUpdate)
        assertTrue(chosenOverUpToDate is UpdateCheckState.UpdateAvailable)
        assertEquals("1.4.0", (chosenOverUpToDate as UpdateCheckState.UpdateAvailable).latestVersion)

        val chosenNewerRelease = AppUpdateChecker.selectBestGitHubUpdateCandidate(olderRawUpdate, releaseUpdate)
        assertTrue(chosenNewerRelease is UpdateCheckState.UpdateAvailable)
        assertEquals("1.4.0", (chosenNewerRelease as UpdateCheckState.UpdateAvailable).latestVersion)
    }
}
