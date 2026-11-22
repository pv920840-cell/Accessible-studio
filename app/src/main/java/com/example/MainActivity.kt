package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.accessibility.AccessibilityHelper
import com.example.accessibility.VibrationType
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.MediaRepository
import com.example.data.StudioPreferences
import com.example.data.StudioSettingsState
import com.example.ui.components.AccessibilitySettingsSheet
import com.example.ui.components.AppUpdateAvailableDialog
import com.example.ui.components.AudioStudioCommand
import com.example.ui.components.InAppUpdateFlowState
import com.example.ui.components.StudioDawTopBar
import com.example.ui.components.StudioHelpUserGuideDialog
import com.example.ui.components.StudioWelcomeDialog
import com.example.ui.components.StudioWhatsNewDialog
import com.example.ui.screens.BatchProcessingDialog
import com.example.ui.studio.StudioRootCoordinator
import com.example.ui.theme.AccessibleStudioTheme
import com.example.update.ApkDownloadResult
import com.example.update.AppUpdateChecker
import com.example.update.UpdateCheckState

enum class StudioTab(val title: String) {
    AUDIO("Audio Studio"),
    VIDEO("Video Studio"),
    EXPORTS("Single Exports")
}

class MainActivity : ComponentActivity() {

    private lateinit var mediaRepository: MediaRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaRepository = MediaRepository(applicationContext)
        enableEdgeToEdge()

        setContent {
            val savedPrefs = remember { StudioPreferences.loadSettings(applicationContext) }
            val publishConfig = remember { AppUpdateChecker.loadPublishConfig(applicationContext) }
            val installedAppInfo = remember { AppUpdateChecker.getInstalledAppInfo(applicationContext) }
            var studioSettings by remember { mutableStateOf(savedPrefs) }
            var isHighContrast by remember { mutableStateOf(savedPrefs.isHighContrast) }
            var isHapticsEnabled by remember { mutableStateOf(savedPrefs.isHapticsEnabled) }
            var defaultExportFormat by remember { mutableStateOf(savedPrefs.defaultExportFormat) }
            var autoCheckUpdates by remember { mutableStateOf(savedPrefs.autoCheckUpdates) }
            var updateProvider by remember { mutableStateOf(savedPrefs.updateProvider) }
            var githubOwner by remember { mutableStateOf(publishConfig.owner) }
            var githubRepo by remember { mutableStateOf(publishConfig.repo) }
            var lastUpdateCheckMs by remember {
                mutableStateOf(StudioPreferences.loadLastUpdateCheckMs(applicationContext))
            }
            var updateCheckState by remember { mutableStateOf<UpdateCheckState>(UpdateCheckState.Idle) }
            var activeUpdateDialog by remember { mutableStateOf<UpdateCheckState.UpdateAvailable?>(null) }
            var updateFlowState by remember { mutableStateOf<InAppUpdateFlowState>(InAppUpdateFlowState.Idle) }
            var dismissedUpdateVersion by remember { mutableStateOf<String?>(null) }
            var hasAutoCheckedThisSession by remember { mutableStateOf(false) }
            var showSettingsSheet by remember { mutableStateOf(false) }
            var showHelpMenuDialog by remember { mutableStateOf(false) }
            var showWhatsNewDialog by remember { mutableStateOf(false) }
            var showWelcomeDialog by remember { mutableStateOf(!StudioPreferences.isWelcomeHidden(applicationContext)) }
            var showBatchProcessingModal by remember { mutableStateOf(false) }
            var followCursor by remember { mutableStateOf(true) }

            val activityContext = this@MainActivity
            val appCoroutineScope = rememberCoroutineScope()

            fun launchApkInstallWithPermissionCheck(apkFile: java.io.File) {
                if (AppUpdateChecker.canInstallUnknownApps(activityContext)) {
                    updateFlowState = InAppUpdateFlowState.ReadyToInstall(apkFile)
                    val launched = AppUpdateChecker.installDownloadedApk(activityContext, apkFile)
                    if (!launched) {
                        updateFlowState = InAppUpdateFlowState.Error(
                            "Could not launch Android package installer for this APK."
                        )
                    }
                } else {
                    updateFlowState = InAppUpdateFlowState.NeedsUnknownAppsPermission(apkFile)
                    AccessibilityHelper.announceForAccessibility(
                        activityContext,
                        "Permission required: allow installing unknown apps for Accessible Studio in Settings."
                    )
                }
            }

            val unknownAppsPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
            ) {
                val flow = updateFlowState
                if (flow is InAppUpdateFlowState.NeedsUnknownAppsPermission) {
                    if (AppUpdateChecker.canInstallUnknownApps(activityContext)) {
                        launchApkInstallWithPermissionCheck(flow.apkFile)
                    } else {
                        AccessibilityHelper.announceForAccessibility(
                            activityContext,
                            "Install unknown apps permission is still disabled. Tap Open Settings to enable it."
                        )
                    }
                }
            }

            fun startApkDownloadAndInstall(update: UpdateCheckState.UpdateAvailable) {
                if (updateFlowState is InAppUpdateFlowState.Downloading) return
                activeUpdateDialog = update
                updateFlowState = InAppUpdateFlowState.Downloading(0L, -1L)
                var lastProgressPostMs = 0L
                appCoroutineScope.launch {
                    val downloadUrl = update.apkUrl.ifBlank { update.downloadUrl }
                    when (val result = AppUpdateChecker.downloadApkToCache(
                        context = applicationContext,
                        apkUrl = downloadUrl,
                        versionName = update.latestVersion,
                        onProgress = { downloaded, total ->
                            val nowMs = System.currentTimeMillis()
                            if (nowMs - lastProgressPostMs >= 120L || (total > 0L && downloaded >= total)) {
                                lastProgressPostMs = nowMs
                                activityContext.runOnUiThread {
                                    if (updateFlowState is InAppUpdateFlowState.Downloading) {
                                        updateFlowState = InAppUpdateFlowState.Downloading(downloaded, total)
                                    }
                                }
                            }
                        }
                    )) {
                        is ApkDownloadResult.Success -> {
                            if (isHapticsEnabled) AccessibilityHelper.vibrate(activityContext, VibrationType.SUCCESS)
                            launchApkInstallWithPermissionCheck(result.apkFile)
                        }
                        is ApkDownloadResult.OpenInBrowserFallback -> {
                            updateFlowState = InAppUpdateFlowState.Idle
                            activeUpdateDialog = null
                            AppUpdateChecker.openUpdateDownload(activityContext, result.url)
                        }
                        is ApkDownloadResult.Error -> {
                            updateFlowState = InAppUpdateFlowState.Error(result.message)
                            AccessibilityHelper.announceForAccessibility(activityContext, result.message)
                        }
                    }
                }
            }

            fun persistAndApplySettings(updated: StudioSettingsState) {
                val normalized = updated.copy(
                    defaultExportFormat = StudioPreferences.normalizeExportFormat(updated.defaultExportFormat),
                    updateProvider = StudioPreferences.normalizeUpdateProvider(updated.updateProvider),
                    recordingSampleRateHz = StudioPreferences.normalizeSampleRateHz(updated.recordingSampleRateHz),
                    recordingBitrateKbps = StudioPreferences.normalizeBitrateKbps(updated.recordingBitrateKbps),
                    recordingInputGain = StudioPreferences.normalizeInputGain(updated.recordingInputGain),
                    audioScrubSpeed = StudioPreferences.normalizeScrubSpeed(updated.audioScrubSpeed)
                )
                studioSettings = normalized
                isHighContrast = normalized.isHighContrast
                isHapticsEnabled = normalized.isHapticsEnabled
                defaultExportFormat = normalized.defaultExportFormat
                autoCheckUpdates = normalized.autoCheckUpdates
                updateProvider = normalized.updateProvider
                StudioPreferences.saveSettings(applicationContext, normalized)
            }

            fun runUpdateCheck(
                isAutomatic: Boolean,
                providerOverride: String = updateProvider,
                ownerOverride: String = githubOwner,
                repoOverride: String = githubRepo
            ) {
                if (updateCheckState is UpdateCheckState.Checking) return
                updateCheckState = UpdateCheckState.Checking(isAutomatic = isAutomatic)
                appCoroutineScope.launch {
                    val result = AppUpdateChecker.checkForUpdates(
                        metadataUrl = publishConfig.fallbackMetadataUrl,
                        currentVersionName = installedAppInfo.versionName,
                        currentVersionCode = installedAppInfo.versionCode,
                        provider = providerOverride,
                        githubOwner = ownerOverride,
                        githubRepo = repoOverride
                    )
                    updateCheckState = result
                    when (result) {
                        is UpdateCheckState.UpToDate -> {
                            lastUpdateCheckMs = result.checkedAtMs
                            StudioPreferences.saveLastUpdateCheck(applicationContext, result.checkedAtMs, result.latestVersion)
                            if (!isAutomatic) {
                                if (isHapticsEnabled) AccessibilityHelper.vibrate(activityContext, VibrationType.SUCCESS)
                                AccessibilityHelper.announceForAccessibility(
                                    activityContext,
                                    "Accessible Studio is up to date, version ${result.currentVersion}."
                                )
                            }
                        }
                        is UpdateCheckState.UpdateAvailable -> {
                            lastUpdateCheckMs = result.checkedAtMs
                            StudioPreferences.saveLastUpdateCheck(applicationContext, result.checkedAtMs, result.latestVersion)
                            if (!isAutomatic || dismissedUpdateVersion != result.latestVersion) {
                                if (updateFlowState !is InAppUpdateFlowState.Downloading) {
                                    updateFlowState = InAppUpdateFlowState.Idle
                                }
                                activeUpdateDialog = result
                            }
                            if (isHapticsEnabled) AccessibilityHelper.vibrate(activityContext, VibrationType.DOUBLE_CLICK)
                            AccessibilityHelper.announceForAccessibility(
                                activityContext,
                                "Update available: Accessible Studio version ${result.latestVersion}."
                            )
                        }
                        is UpdateCheckState.Error -> {
                            if (!isAutomatic) {
                                AccessibilityHelper.announceForAccessibility(activityContext, result.message)
                            }
                        }
                        else -> Unit
                    }
                }
            }

            // Modern Storage Permission Handler adhering to Scoped Storage guidelines
            val storagePermissions = remember {
                com.example.storage.StorageExportManager.getRequiredStoragePermissions()
            }
            val storagePermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
            ) { permissionsMap ->
                val allGranted = permissionsMap.values.all { it }
                if (allGranted) {
                    AccessibilityHelper.announceForAccessibility(
                        activityContext,
                        "Storage access granted. Exports will be saved directly to Accessible Studio folders."
                    )
                }
            }

            LaunchedEffect(Unit) {
                if (storagePermissions.isNotEmpty() && !com.example.storage.StorageExportManager.hasStoragePermissions(activityContext)) {
                    storagePermissionLauncher.launch(storagePermissions.toTypedArray())
                }
                if (autoCheckUpdates && !hasAutoCheckedThisSession) {
                    hasAutoCheckedThisSession = true
                    runUpdateCheck(isAutomatic = true)
                }
            }

            LaunchedEffect(showSettingsSheet, autoCheckUpdates) {
                if (showSettingsSheet && autoCheckUpdates && !hasAutoCheckedThisSession) {
                    hasAutoCheckedThisSession = true
                    runUpdateCheck(isAutomatic = true)
                }
            }

            AccessibleStudioTheme(isHighContrast = isHighContrast) {
                StudioRootCoordinator(
                    mediaRepository = mediaRepository,
                    isHighContrast = isHighContrast,
                    hapticsEnabled = isHapticsEnabled,
                    defaultExportFormat = defaultExportFormat,
                    followCursor = followCursor,
                    studioSettings = studioSettings,
                    onToggleHighContrast = {
                        persistAndApplySettings(studioSettings.copy(isHighContrast = !isHighContrast))
                        if (isHapticsEnabled) AccessibilityHelper.vibrate(activityContext, VibrationType.DOUBLE_CLICK)
                        AccessibilityHelper.announceForAccessibility(
                            activityContext,
                            if (isHighContrast) "High Contrast Mode enabled" else "High Contrast Mode disabled"
                        )
                    },
                    onOpenSettings = {
                        showSettingsSheet = true
                        if (isHapticsEnabled) AccessibilityHelper.vibrate(activityContext, VibrationType.CLICK)
                    },
                    onOpenHelp = {
                        showHelpMenuDialog = true
                        if (isHapticsEnabled) AccessibilityHelper.vibrate(activityContext, VibrationType.CLICK)
                    },
                    onOpenWhatsNew = {
                        showWhatsNewDialog = true
                        if (isHapticsEnabled) AccessibilityHelper.vibrate(activityContext, VibrationType.CLICK)
                        AccessibilityHelper.announceForAccessibility(activityContext, "Opening What's New dialog")
                    },
                    onOpenBatchProcessing = {
                        showBatchProcessingModal = true
                        if (isHapticsEnabled) AccessibilityHelper.vibrate(activityContext, VibrationType.CLICK)
                        AccessibilityHelper.announceForAccessibility(activityContext, "Opening Batch Processing Studio")
                    }
                )

                if (showSettingsSheet) {
                    AccessibilitySettingsSheet(
                        isHighContrast = isHighContrast,
                        isHapticsEnabled = isHapticsEnabled,
                        defaultExportFormat = defaultExportFormat,
                        autoCheckUpdates = autoCheckUpdates,
                        updateCheckState = updateCheckState,
                        lastUpdateCheckMs = lastUpdateCheckMs,
                        updateProvider = updateProvider,
                        githubOwner = githubOwner,
                        githubRepo = githubRepo,
                        currentSettings = studioSettings,
                        onImmediateSettingsUpdate = { updated ->
                            persistAndApplySettings(updated)
                            if (updated.isHapticsEnabled) {
                                AccessibilityHelper.vibrate(activityContext, VibrationType.CLICK)
                            }
                        },
                        onSaveFullSettings = { finalSettings ->
                            persistAndApplySettings(finalSettings)
                        },
                        onToggleAutoCheckUpdates = { enabled ->
                            autoCheckUpdates = enabled
                            studioSettings = studioSettings.copy(autoCheckUpdates = enabled)
                            StudioPreferences.setAutoCheckUpdatesEnabled(applicationContext, enabled)
                            if (isHapticsEnabled) AccessibilityHelper.vibrate(activityContext, VibrationType.CLICK)
                            AccessibilityHelper.announceForAccessibility(
                                activityContext,
                                if (enabled) "Automatic update check enabled" else "Automatic update check disabled"
                            )
                            if (enabled) {
                                hasAutoCheckedThisSession = true
                                runUpdateCheck(isAutomatic = true)
                            }
                        },
                        onSaveGitHubSource = { newProvider, newOwner, newRepo ->
                            val cleanProvider = StudioPreferences.normalizeUpdateProvider(newProvider)
                            val cleanOwner = newOwner.trim()
                            val cleanRepo = newRepo.trim()
                            updateProvider = cleanProvider
                            githubOwner = cleanOwner
                            githubRepo = cleanRepo
                            studioSettings = studioSettings.copy(
                                updateProvider = cleanProvider,
                                githubOwner = cleanOwner,
                                githubRepo = cleanRepo
                            )
                            StudioPreferences.saveGitHubUpdateSource(
                                applicationContext,
                                cleanProvider,
                                cleanOwner,
                                cleanRepo
                            )
                        },
                        onCheckForUpdates = {
                            if (isHapticsEnabled) AccessibilityHelper.vibrate(activityContext, VibrationType.CLICK)
                            AccessibilityHelper.announceForAccessibility(activityContext, "Checking for updates")
                            runUpdateCheck(isAutomatic = false)
                        },
                        onDownloadUpdate = { url ->
                            if (isHapticsEnabled) AccessibilityHelper.vibrate(activityContext, VibrationType.CLICK)
                            val currentUpdate = updateCheckState as? UpdateCheckState.UpdateAvailable
                            val candidateApkUrl = currentUpdate?.apkUrl?.takeIf { it.isNotBlank() } ?: url
                            if (currentUpdate != null && AppUpdateChecker.isDirectApkUrl(candidateApkUrl)) {
                                startApkDownloadAndInstall(currentUpdate.copy(apkUrl = candidateApkUrl))
                            } else {
                                val opened = AppUpdateChecker.openUpdateDownload(activityContext, url)
                                if (!opened) {
                                    android.widget.Toast.makeText(
                                        activityContext,
                                        "Could not open download link in browser.",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        onSaveSettings = { newHighContrast, newHaptics, newExportFormat ->
                            val cleanFormat = StudioPreferences.normalizeExportFormat(newExportFormat)
                            persistAndApplySettings(
                                studioSettings.copy(
                                    isHighContrast = newHighContrast,
                                    isHapticsEnabled = newHaptics,
                                    defaultExportFormat = cleanFormat,
                                    autoCheckUpdates = autoCheckUpdates,
                                    updateProvider = updateProvider,
                                    githubOwner = githubOwner,
                                    githubRepo = githubRepo
                                )
                            )
                            showSettingsSheet = false
                            if (newHaptics) AccessibilityHelper.vibrate(activityContext, VibrationType.SUCCESS)
                            val msg = "All settings saved. Export format: $cleanFormat"
                            android.widget.Toast.makeText(activityContext, msg, android.widget.Toast.LENGTH_SHORT).show()
                            AccessibilityHelper.announceForAccessibility(activityContext, msg)
                        },
                        onOpenUserGuide = {
                            if (isHapticsEnabled) AccessibilityHelper.vibrate(activityContext, VibrationType.CLICK)
                            showHelpMenuDialog = true
                        },
                        onOpenWhatsNew = {
                            if (isHapticsEnabled) AccessibilityHelper.vibrate(activityContext, VibrationType.CLICK)
                            showWhatsNewDialog = true
                        },
                        onDismiss = { showSettingsSheet = false }
                    )
                }

                // BATCH PROCESSING MODAL
                if (showBatchProcessingModal) {
                    Dialog(
                        onDismissRequest = { showBatchProcessingModal = false },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        BatchProcessingDialog(
                            mediaRepository = mediaRepository,
                            hapticsEnabled = isHapticsEnabled,
                            onDismiss = { showBatchProcessingModal = false },
                            onNavigateToExports = {
                                showBatchProcessingModal = false
                            }
                        )
                    }
                }

                // HELP MENU USER GUIDE MODAL (ENGLISH + HINDI)
                if (showHelpMenuDialog) {
                    StudioHelpUserGuideDialog(
                        onDismiss = { showHelpMenuDialog = false }
                    )
                }

                // WHAT'S NEW MODAL (FROM HELP MENU)
                if (showWhatsNewDialog) {
                    StudioWhatsNewDialog(
                        onDismiss = { showWhatsNewDialog = false }
                    )
                }

                // WELCOME ONBOARDING MODAL ON INSTALL / LAUNCH
                if (showWelcomeDialog) {
                    StudioWelcomeDialog(
                        onOk = {
                            if (isHapticsEnabled) AccessibilityHelper.vibrate(activityContext, VibrationType.CLICK)
                            showWelcomeDialog = false
                        },
                        onDontShowAgain = {
                            StudioPreferences.setWelcomeHidden(applicationContext, true)
                            if (isHapticsEnabled) AccessibilityHelper.vibrate(activityContext, VibrationType.SUCCESS)
                            showWelcomeDialog = false
                        }
                    )
                }

                // IN-APP UPDATE AVAILABLE & APK INSTALL DIALOG
                val dialogUpdate = activeUpdateDialog
                if (!showWelcomeDialog && dialogUpdate != null) {
                    AppUpdateAvailableDialog(
                        update = dialogUpdate,
                        flowState = updateFlowState,
                        onUpdateNow = {
                            if (isHapticsEnabled) AccessibilityHelper.vibrate(activityContext, VibrationType.CLICK)
                            startApkDownloadAndInstall(dialogUpdate)
                        },
                        onOpenInstallPermissionSettings = {
                            if (isHapticsEnabled) AccessibilityHelper.vibrate(activityContext, VibrationType.CLICK)
                            runCatching {
                                unknownAppsPermissionLauncher.launch(
                                    AppUpdateChecker.createManageUnknownAppSourcesIntent(activityContext)
                                )
                            }
                        },
                        onInstallDownloadedApk = { apkFile ->
                            if (isHapticsEnabled) AccessibilityHelper.vibrate(activityContext, VibrationType.CLICK)
                            launchApkInstallWithPermissionCheck(apkFile)
                        },
                        onLater = {
                            dismissedUpdateVersion = dialogUpdate.latestVersion
                            activeUpdateDialog = null
                            updateFlowState = InAppUpdateFlowState.Idle
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun StudioBottomNavigationBar(
    currentTab: StudioTab,
    isHighContrast: Boolean,
    onSelectTab: (StudioTab) -> Unit
) {
    NavigationBar(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("studio_bottom_nav_bar")
            .border(
                if (isHighContrast) 2.dp else 0.dp,
                if (isHighContrast) Color.White else Color.Transparent
            ),
        containerColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surface,
        windowInsets = WindowInsets.navigationBars
    ) {
        // Tab 1: Audio Studio
        NavigationBarItem(
            selected = currentTab == StudioTab.AUDIO,
            onClick = { onSelectTab(StudioTab.AUDIO) },
            icon = {
                Icon(
                    imageVector = if (currentTab == StudioTab.AUDIO) Icons.Filled.Audiotrack else Icons.Outlined.Audiotrack,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp)
                )
            },
            label = {
                Text(
                    text = "Audio Studio",
                    fontWeight = if (currentTab == StudioTab.AUDIO) FontWeight.Bold else FontWeight.Normal
                )
            },
            modifier = Modifier
                .testTag("nav_tab_audio")
                .semantics {
                    contentDescription = "Audio Studio tab. Edit audio, voice recording, effects, and export."
                },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = if (isHighContrast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                selectedIconColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.onPrimaryContainer,
                selectedTextColor = if (isHighContrast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
                unselectedIconColor = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        // Tab 2: Video Studio
        NavigationBarItem(
            selected = currentTab == StudioTab.VIDEO,
            onClick = { onSelectTab(StudioTab.VIDEO) },
            icon = {
                Icon(
                    imageVector = if (currentTab == StudioTab.VIDEO) Icons.Filled.Movie else Icons.Outlined.Movie,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp)
                )
            },
            label = {
                Text(
                    text = "Video Studio",
                    fontWeight = if (currentTab == StudioTab.VIDEO) FontWeight.Bold else FontWeight.Normal
                )
            },
            modifier = Modifier
                .testTag("nav_tab_video")
                .semantics {
                    contentDescription = "Video Studio tab. Trim video, replace soundtrack, and single-file MP4 export."
                },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondaryContainer,
                selectedIconColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.onSecondaryContainer,
                selectedTextColor = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondary,
                unselectedIconColor = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        // Tab 3: Single-File Exports
        NavigationBarItem(
            selected = currentTab == StudioTab.EXPORTS,
            onClick = { onSelectTab(StudioTab.EXPORTS) },
            icon = {
                Icon(
                    imageVector = if (currentTab == StudioTab.EXPORTS) Icons.Filled.FolderOpen else Icons.Outlined.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp)
                )
            },
            label = {
                Text(
                    text = "Exported Files",
                    fontWeight = if (currentTab == StudioTab.EXPORTS) FontWeight.Bold else FontWeight.Normal
                )
            },
            modifier = Modifier
                .testTag("nav_tab_exports")
                .semantics {
                    contentDescription = "Exported Files tab. View, play, share, or manage single output media files."
                },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = if (isHighContrast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiaryContainer,
                selectedIconColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.onTertiaryContainer,
                selectedTextColor = if (isHighContrast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                unselectedIconColor = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@Composable
fun StudioNavigationRail(
    currentTab: StudioTab,
    isHighContrast: Boolean,
    onSelectTab: (StudioTab) -> Unit
) {
    NavigationRail(
        containerColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surface,
        modifier = Modifier.border(
            if (isHighContrast) 2.dp else 0.dp,
            if (isHighContrast) Color.White else Color.Transparent
        )
    ) {
        Spacer(Modifier.height(16.dp))
        NavigationRailItem(
            selected = currentTab == StudioTab.AUDIO,
            onClick = { onSelectTab(StudioTab.AUDIO) },
            icon = { Icon(Icons.Filled.Audiotrack, contentDescription = "Audio Studio") },
            label = { Text("Audio") }
        )
        NavigationRailItem(
            selected = currentTab == StudioTab.VIDEO,
            onClick = { onSelectTab(StudioTab.VIDEO) },
            icon = { Icon(Icons.Filled.Movie, contentDescription = "Video Studio") },
            label = { Text("Video") }
        )
        NavigationRailItem(
            selected = currentTab == StudioTab.EXPORTS,
            onClick = { onSelectTab(StudioTab.EXPORTS) },
            icon = { Icon(Icons.Filled.FolderOpen, contentDescription = "Exports") },
            label = { Text("Exports") }
        )
    }
}
