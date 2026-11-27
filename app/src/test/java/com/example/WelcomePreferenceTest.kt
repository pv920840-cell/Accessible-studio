package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.StudioPreferences
import com.example.ui.components.AccessibilitySettingsSheet
import com.example.ui.components.StudioDawTopBar
import com.example.ui.components.StudioHelpUserGuideDialog
import com.example.ui.components.StudioUserGuideContent
import com.example.ui.components.StudioWhatsNewDialog
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
class WelcomePreferenceTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun welcomeDialog_showsOnFreshInstall_andHidesAfterDontShowAgain() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        StudioPreferences.setWelcomeHidden(context, false)
        assertFalse(StudioPreferences.isWelcomeHidden(context))

        StudioPreferences.setWelcomeHidden(context, true)
        assertTrue(StudioPreferences.isWelcomeHidden(context))
    }

    @Test
    fun userGuideWelcomeAndWhatsNew_containBilingualContentWithoutForbiddenNames() {
        val combined = listOf(
            StudioUserGuideContent.HELP_ENGLISH_OVERVIEW,
            StudioUserGuideContent.HELP_ENGLISH_GUIDE,
            StudioUserGuideContent.HELP_HINDI_OVERVIEW,
            StudioUserGuideContent.HELP_HINDI_GUIDE,
            StudioUserGuideContent.WELCOME_ENGLISH,
            StudioUserGuideContent.WELCOME_HINDI,
            StudioUserGuideContent.WHATS_NEW_ENGLISH,
            StudioUserGuideContent.WHATS_NEW_HINDI
        ).joinToString("\n")

        assertTrue(combined.contains("Accessible Studio"))
        assertTrue(combined.contains("Mute Selection"))
        assertTrue(combined.contains("Follow Cursor"))
        assertTrue(combined.contains("Auto Gain"))
        assertTrue(combined.contains("Voice Over"))
        assertTrue(combined.contains("हिंदी") || combined.contains("कैसे"))
        assertFalse(combined.contains("goldwave", ignoreCase = true))
        assertFalse(combined.contains("gurudev", ignoreCase = true))

        // What's New inside About must show ONLY the current version's changes (older versions removed)
        val whatsNewCombined = "${StudioUserGuideContent.WHATS_NEW_ENGLISH}\n${StudioUserGuideContent.WHATS_NEW_HINDI}"
        assertTrue(whatsNewCombined.contains(com.example.BuildConfig.VERSION_NAME))
        assertTrue(whatsNewCombined.contains("Audio Editor"))
        assertTrue(whatsNewCombined.contains("Batch Processing"))
        assertFalse(whatsNewCombined.contains("Version 1.1"))
        assertFalse(whatsNewCombined.contains("Version 1.2 —"))
        assertFalse(whatsNewCombined.contains("Version 1.2.5"))
        assertFalse(whatsNewCombined.contains("Version 1.2.6"))
        assertFalse(whatsNewCombined.contains("Version 1.2.7"))
        assertFalse(whatsNewCombined.contains("Version 1.2.8"))
        assertFalse(whatsNewCombined.contains("Version 1.2.9"))
        assertFalse(whatsNewCombined.contains("Version 1.4.0"))
        assertFalse(whatsNewCombined.contains("Version 1.5.0"))
        assertFalse(whatsNewCombined.contains("Version 1.5.1"))
        assertFalse(whatsNewCombined.contains("Version 1.5.2"))
        assertFalse(whatsNewCombined.contains("Version 1.5.3"))
    }

    @Test
    fun helpDialog_keepsOriginalBilingualGuideWithoutDuplicateWhatsNewButton() {
        composeRule.setContent {
            StudioHelpUserGuideDialog(onDismiss = {})
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("help_dialog_title").assertExists()
        composeRule.onNodeWithTag("help_guide_english").assertExists()
        composeRule.onNodeWithTag("help_guide_hindi").assertExists()
        composeRule.onNodeWithTag("btn_help_whats_new").assertDoesNotExist()
        composeRule.onNodeWithTag("btn_close_help_dialog").assertExists().performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun topBar_removesDuplicateHelpAndWhatsNewMenusAndIcons() {
        composeRule.setContent {
            StudioDawTopBar(
                currentTab = StudioTab.AUDIO,
                isHighContrast = false,
                onToggleHighContrast = {},
                onOpenAbout = {},
                onOpenSettings = {},
                onOpenHelp = {},
                onOpenWhatsNew = {},
                onSelectTab = {},
                onOpenBatchProcessing = {},
                onAudioCommand = {}
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("btn_top_about_dialog").assertDoesNotExist()
        composeRule.onNodeWithTag("btn_top_accessibility_settings").assertDoesNotExist()
        composeRule.onNodeWithTag("btn_top_help").assertDoesNotExist()
        composeRule.onNodeWithTag("menu_bar_help").assertDoesNotExist()
    }

    @Test
    fun settingsSheet_containsAboutWithUserGuideFirstAndWhatsNewInsideAbout() {
        var userGuideOpened = false
        var whatsNewOpened = false

        composeRule.setContent {
            var showHelp by remember { mutableStateOf(false) }
            var showWhatsNew by remember { mutableStateOf(false) }
            AccessibilitySettingsSheet(
                isHighContrast = false,
                isHapticsEnabled = true,
                defaultExportFormat = "WAV",
                onSaveSettings = { _, _, _ -> },
                onOpenUserGuide = {
                    userGuideOpened = true
                    showHelp = true
                },
                onOpenWhatsNew = {
                    whatsNewOpened = true
                    showWhatsNew = true
                },
                onDismiss = {}
            )
            if (showHelp) {
                StudioHelpUserGuideDialog(onDismiss = { showHelp = false })
            }
            if (showWhatsNew) {
                StudioWhatsNewDialog(onDismiss = { showWhatsNew = false })
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("accessibility_settings_sheet").assertExists()
        composeRule.onNodeWithTag("card_about_accessible_studio").performScrollTo().assertExists()
        assertEquals("1.5.4", com.example.BuildConfig.VERSION_NAME)
        composeRule.onNodeWithText("Version ${com.example.BuildConfig.VERSION_NAME} • 100% Free & Open Accessible Studio • 100% Offline").performScrollTo().assertExists()

        // First button inside About: User Guide
        composeRule.onNodeWithTag("btn_about_user_guide").performScrollTo().assertExists().performClick()
        composeRule.waitForIdle()
        assertTrue(userGuideOpened)
        composeRule.onNodeWithTag("help_dialog_title").assertExists()
        composeRule.onNodeWithTag("btn_close_help_dialog").performClick()
        composeRule.waitForIdle()

        // Second button inside About: What's New
        composeRule.onNodeWithTag("btn_about_whats_new").performScrollTo().assertExists().performClick()
        composeRule.waitForIdle()
        assertTrue(whatsNewOpened)
        composeRule.onNodeWithTag("whats_new_dialog_title").assertExists()
        composeRule.onNodeWithTag("whats_new_version_badge").assertExists()
        composeRule.onNodeWithText("Accessible Studio v${com.example.BuildConfig.VERSION_NAME} • Recent Features & Bug Fixes").assertExists()
        composeRule.onNodeWithTag("btn_close_whats_new").performClick()
        composeRule.waitForIdle()

        assertFalse(com.example.ui.studio.AudioFeatureTool.values().any {
            it.name.contains("VOCAL", ignoreCase = true) || it.title.contains("Karaoke", ignoreCase = true)
        })
    }
}
