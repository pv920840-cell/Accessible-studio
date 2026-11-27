package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.LocalHighContrast
import com.example.update.UpdateCheckState
import java.io.File
import java.util.Locale

sealed interface InAppUpdateFlowState {
    data object Idle : InAppUpdateFlowState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : InAppUpdateFlowState
    data class NeedsUnknownAppsPermission(val apkFile: File) : InAppUpdateFlowState
    data class ReadyToInstall(val apkFile: File) : InAppUpdateFlowState
    data class PackageConflict(val message: String, val apkFile: File? = null) : InAppUpdateFlowState
    data class Error(val message: String) : InAppUpdateFlowState
}

@Composable
fun AppUpdateAvailableDialog(
    update: UpdateCheckState.UpdateAvailable,
    flowState: InAppUpdateFlowState,
    onUpdateNow: () -> Unit,
    onOpenInstallPermissionSettings: () -> Unit,
    onInstallDownloadedApk: (File) -> Unit,
    onLater: () -> Unit,
    onUninstallCurrentApp: () -> Unit = {},
    onSaveApkToDownloads: (File) -> Unit = {},
    onShareApkLink: () -> Unit = {},
    onOpenReleasesPage: () -> Unit = {},
    onOpenAppInfoForUninstall: () -> Unit = {}
) {
    val isHighContrast = LocalHighContrast.current
    val isDownloading = flowState is InAppUpdateFlowState.Downloading

    AlertDialog(
        onDismissRequest = { if (!isDownloading) onLater() },
        properties = DialogProperties(
            dismissOnBackPress = !isDownloading,
            dismissOnClickOutside = false
        ),
        icon = {
            Icon(
                imageVector = when (flowState) {
                    is InAppUpdateFlowState.NeedsUnknownAppsPermission -> Icons.Default.Security
                    is InAppUpdateFlowState.ReadyToInstall -> Icons.Default.InstallMobile
                    else -> Icons.Default.SystemUpdate
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Update Available — v${update.latestVersion}",
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .testTag("dialog_update_title")
                    .semantics { heading() }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Current version: v${update.currentVersion}  •  New version: v${update.latestVersion}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("txt_update_versions")
                )

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isHighContrast) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = if (isHighContrast) BorderStroke(1.5.dp, Color.White) else null,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Changelog / What's New",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = update.releaseNotes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("txt_update_changelog")
                        )
                    }
                }

                when (flowState) {
                    is InAppUpdateFlowState.Downloading -> {
                        val hasTotal = flowState.totalBytes > 0L
                        val progressFraction = if (hasTotal) {
                            (flowState.downloadedBytes.toFloat() / flowState.totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        val downloadedMb = String.format(Locale.US, "%.1f", flowState.downloadedBytes / (1024.0 * 1024.0))
                        val progressLabel = if (hasTotal) {
                            val totalMb = String.format(Locale.US, "%.1f", flowState.totalBytes / (1024.0 * 1024.0))
                            val pct = (progressFraction * 100f).toInt().coerceIn(0, 100)
                            "Downloading APK... $pct% ($downloadedMb / $totalMb MB)"
                        } else {
                            "Downloading APK... $downloadedMb MB"
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics {
                                    liveRegion = LiveRegionMode.Polite
                                    contentDescription = progressLabel
                                },
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = progressLabel,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("txt_update_download_progress")
                            )
                            if (hasTotal) {
                                LinearProgressIndicator(
                                    progress = { progressFraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .testTag("progress_update_download")
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .testTag("progress_update_download")
                                )
                            }
                        }
                    }

                    is InAppUpdateFlowState.NeedsUnknownAppsPermission -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .testTag("card_unknown_apps_guidance"),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Permission Required to Install APK",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "Your update APK is downloaded. Allow \"Install unknown apps\" for Accessible Studio in Android Settings, then return to install.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    is InAppUpdateFlowState.ReadyToInstall -> {
                        Text(
                            text = "APK downloaded and verified. Tap Install Update to complete installation.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("txt_update_ready_install")
                        )
                    }

                    is InAppUpdateFlowState.PackageConflict -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("card_update_package_conflict")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Package Conflict Detected",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = flowState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.testTag("txt_update_package_conflict")
                                )
                                if (flowState.apkFile != null) {
                                    OutlinedButton(
                                        onClick = { onSaveApkToDownloads(flowState.apkFile) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .testTag("btn_dialog_save_apk_downloads")
                                            .semantics {
                                                contentDescription = "Save update APK to Downloads folder before uninstalling the old build"
                                            },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Save APK to Downloads", fontWeight = FontWeight.Bold)
                                    }
                                }
                                OutlinedButton(
                                    onClick = onOpenAppInfoForUninstall,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("btn_dialog_open_app_info_uninstall")
                                        .semantics {
                                            contentDescription = "Open Android App Info to uninstall the older build manually"
                                        },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Open App Info to Uninstall", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    is InAppUpdateFlowState.Error -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = flowState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.testTag("txt_update_download_error")
                                )
                                OutlinedButton(
                                    onClick = onOpenReleasesPage,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("btn_dialog_open_releases_page")
                                        .semantics {
                                            contentDescription = "Open Releases Page in browser to download Accessible Studio APK manually."
                                        },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Open Releases Page", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    InAppUpdateFlowState.Idle -> Unit
                }

                OutlinedButton(
                    onClick = onShareApkLink,
                    enabled = !isDownloading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("btn_dialog_share_apk_link")
                        .semantics {
                            contentDescription = "Share APK Direct Link and full Accessible Studio feature article for version ${update.latestVersion}"
                        },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share APK & App Article", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onLater,
                enabled = !isDownloading,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("btn_dialog_update_later")
                    .semantics {
                        contentDescription = "Later. Dismiss this update prompt for now."
                    }
            ) {
                Text("Later", fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = {
            when (flowState) {
                is InAppUpdateFlowState.NeedsUnknownAppsPermission -> {
                    Button(
                        onClick = onOpenInstallPermissionSettings,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("btn_dialog_open_install_settings")
                            .semantics {
                                contentDescription = "Open Settings to allow installing unknown apps for Accessible Studio."
                            }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open Settings", fontWeight = FontWeight.Black)
                        }
                    }
                }

                is InAppUpdateFlowState.PackageConflict -> {
                    Button(
                        onClick = onUninstallCurrentApp,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("btn_dialog_uninstall_existing")
                            .semantics {
                                contentDescription = "Uninstall Old Build. Removes the older conflicting version so you can install the new update."
                            }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Uninstall Old Build", fontWeight = FontWeight.Black)
                        }
                    }
                }

                is InAppUpdateFlowState.ReadyToInstall -> {
                    Button(
                        onClick = { onInstallDownloadedApk(flowState.apkFile) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("btn_dialog_install_apk")
                            .semantics {
                                contentDescription = "Install downloaded Accessible Studio version ${update.latestVersion} APK."
                            }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Install Update", fontWeight = FontWeight.Black)
                        }
                    }
                }

                else -> {
                    Button(
                        onClick = onUpdateNow,
                        enabled = !isDownloading,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("btn_dialog_update_now")
                            .semantics {
                                contentDescription = "Update Now. Download and install Accessible Studio version ${update.latestVersion}."
                            }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isDownloading) "Downloading..." else "Update Now",
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    )
}
