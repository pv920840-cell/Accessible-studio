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
import androidx.test.core.app.ApplicationProvider
import com.example.data.StudioPreferences
import com.example.data.StudioSettingsState
import com.example.ui.components.AccessibilitySettingsSheet
import com.example.update.AppUpdateChecker
import com.example.update.UpdateCheckState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
