package com.example

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
import com.example.update.AppUpdateChecker
import com.example.update.UpdateCheckState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
              "apkUrl": "${AppUpdateChecker.DEFAULT_APK_URL}"
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
                        downloadUrl = AppUpdateChecker.DEFAULT_APK_URL,
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
        assertEquals(AppUpdateChecker.DEFAULT_APK_URL, downloadedUrl)
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
              "body": "GitHub Release with Vocal Remover speed boost.",
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
            "https://github.com/accessible-dev/accessible-studio/releases/download/v1.2.20261119.120000/AccessibleStudio_V1.2.apk",
            update.downloadUrl
        )
        assertEquals("GitHub Release with Vocal Remover speed boost.", update.releaseNotes)
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
}
