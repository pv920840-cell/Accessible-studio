package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.StudioTab

private enum class TopDropdownMenuKey {
    NONE,
    FILE,
    EDIT,
    SELECTION,
    EFFECT,
    VIEW,
    TOOLS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioDawTopBar(
    currentTab: StudioTab,
    isHighContrast: Boolean,
    defaultExportFormat: String = "WAV",
    followCursor: Boolean = true,
    onToggleHighContrast: () -> Unit,
    onOpenAbout: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenWhatsNew: () -> Unit = {},
    onSelectTab: (StudioTab) -> Unit,
    onOpenBatchProcessing: () -> Unit,
    onAudioCommand: (AudioStudioCommand) -> Unit
) {
    var openMenu by remember { mutableStateOf(TopDropdownMenuKey.NONE) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surface)
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "Accessible Studio",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${currentTab.title} • v${com.example.BuildConfig.VERSION_NAME} Offline",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = onToggleHighContrast,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_top_contrast_toggle")
                        .border(
                            if (isHighContrast) 2.dp else 0.dp,
                            if (isHighContrast) Color.Yellow else Color.Transparent,
                            CircleShape
                        )
                        .semantics {
                            contentDescription = if (isHighContrast) "High Contrast Mode is On. Tap to turn Off." else "High Contrast Mode is Off. Tap to turn On."
                        }
                ) {
                    Icon(
                        Icons.Default.InvertColors,
                        contentDescription = null,
                        tint = if (isHighContrast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Standard DAW Dropdown Menu Bar: File | Edit | Selection | Effect | View | Tools | Help
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            color = if (isHighContrast) Color(0xFF141414) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(
                if (isHighContrast) 2.dp else 1.dp,
                if (isHighContrast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("menu_bar_row")
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. FILE DROPDOWN
                item {
                    MenuDropdownAnchor(
                        label = "File",
                        tag = "menu_bar_file",
                        expanded = openMenu == TopDropdownMenuKey.FILE,
                        isHighContrast = isHighContrast,
                        onOpen = { openMenu = TopDropdownMenuKey.FILE },
                        onDismiss = { openMenu = TopDropdownMenuKey.NONE }
                    ) {
                        StudioMenuItem("New Blank Project...", Icons.Default.Add, "menu_file_new") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.NewBlankProject)
                        }
                        StudioMenuItem("Open Audio File...", Icons.Default.FolderOpen, "menu_file_open") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.OpenAudioModal)
                        }
                        StudioMenuItem("Mix With Audio File / Tab...", Icons.Default.Layers, "menu_file_mix") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.OpenMixAudioDialog)
                        }
                        StudioMenuItem("Save ($defaultExportFormat)", Icons.Default.Save, "menu_file_save") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.SaveWav)
                        }
                        StudioMenuItem("Save As (Choose Format)", Icons.Default.Download, "menu_file_save_as") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.SaveAsMp3)
                        }
                        StudioMenuItem("Export ($defaultExportFormat)", Icons.Default.Download, "menu_file_export") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.ExportFile)
                        }
                        HorizontalDivider()
                        StudioMenuItem("Properties", Icons.Default.Info, "menu_file_properties") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.OpenProperties)
                        }
                        StudioMenuItem("Close", Icons.Default.Close, "menu_file_close") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.CloseProject)
                        }
                    }
                }

                // 2. EDIT DROPDOWN
                item {
                    MenuDropdownAnchor(
                        label = "Edit",
                        tag = "menu_bar_edit",
                        expanded = openMenu == TopDropdownMenuKey.EDIT,
                        isHighContrast = isHighContrast,
                        onOpen = { openMenu = TopDropdownMenuKey.EDIT },
                        onDismiss = { openMenu = TopDropdownMenuKey.NONE }
                    ) {
                        StudioMenuItem("Undo", Icons.AutoMirrored.Filled.Undo, "menu_edit_undo") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.Undo)
                        }
                        StudioMenuItem("Redo", Icons.AutoMirrored.Filled.Redo, "menu_edit_redo") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.Redo)
                        }
                        HorizontalDivider()
                        StudioMenuItem("Cut", Icons.Default.ContentCut, "menu_edit_cut") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.CutSelection)
                        }
                        StudioMenuItem("Copy", Icons.Default.ContentCopy, "menu_edit_copy") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.CopySelection)
                        }
                        StudioMenuItem("Mute Selection", Icons.AutoMirrored.Filled.VolumeOff, "menu_edit_mute_selection") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.MuteSelection)
                        }
                        StudioMenuItem("Delete Selection", Icons.Default.Delete, "menu_edit_delete") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.DeleteSelection)
                        }
                        StudioMenuItem("Paste at Cursor", Icons.Default.ContentPaste, "menu_edit_paste_cursor") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.PasteAtCursor)
                        }
                        StudioMenuItem("Paste at End", Icons.Default.ContentPaste, "menu_edit_paste_end") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.PasteAtEnd)
                        }
                        StudioMenuItem("Mix Paste (Instant Overdub)", Icons.Default.Layers, "menu_edit_mix_paste") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.MixPaste)
                        }
                        StudioMenuItem("Mix Audio Studio (Balance & File)...", Icons.Default.Layers, "menu_edit_mix_dialog") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.OpenMixAudioDialog)
                        }
                        HorizontalDivider()
                        StudioMenuItem("Insert Silence", Icons.Default.Waves, "menu_edit_silence") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.OpenSilenceDialog)
                        }
                        StudioMenuItem("Select All", Icons.Default.SelectAll, "menu_edit_select_all") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.SelectAll)
                        }
                    }
                }

                // 3. SELECTION DROPDOWN
                item {
                    MenuDropdownAnchor(
                        label = "Selection",
                        tag = "menu_bar_selection",
                        expanded = openMenu == TopDropdownMenuKey.SELECTION,
                        isHighContrast = isHighContrast,
                        onOpen = { openMenu = TopDropdownMenuKey.SELECTION },
                        onDismiss = { openMenu = TopDropdownMenuKey.NONE }
                    ) {
                        StudioMenuItem("Set Trim Start Marker", Icons.Default.Bookmark, "menu_sel_start") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.SetSelStart)
                        }
                        StudioMenuItem("Set Trim End Marker", Icons.Default.Bookmark, "menu_sel_end") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.SetSelEnd)
                        }
                        HorizontalDivider()
                        StudioMenuItem("Crop to Selection", Icons.Default.Crop, "menu_sel_crop") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.CropSelection)
                        }
                        StudioMenuItem("Play Selection", Icons.Default.PlayArrow, "menu_sel_play") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.Play1Selection)
                        }
                        HorizontalDivider()
                        StudioMenuItem("Cue Points & Markers", Icons.Default.Bookmark, "menu_sel_cues") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.OpenCuePointsDialog)
                        }
                    }
                }

                // 4. EFFECT DROPDOWN (Categorized: Repair/Cleaning, Dynamics, Creative DSP)
                item {
                    MenuDropdownAnchor(
                        label = "Effect",
                        tag = "menu_bar_effect",
                        expanded = openMenu == TopDropdownMenuKey.EFFECT,
                        isHighContrast = isHighContrast,
                        onOpen = { openMenu = TopDropdownMenuKey.EFFECT },
                        onDismiss = { openMenu = TopDropdownMenuKey.NONE }
                    ) {
                        MenuCategoryHeader("REPAIR / CLEANING")
                        StudioMenuItem("De-Hum (60 Hz Mains)", Icons.Default.Waves, "menu_eff_dehum") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.RemoveHum60Hz)
                        }
                        StudioMenuItem("High-Pass Filter (300 Hz)", Icons.Default.Equalizer, "menu_eff_highpass") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.HighPassFilter)
                        }
                        StudioMenuItem("Low-Pass Filter (3 kHz)", Icons.Default.Equalizer, "menu_eff_lowpass") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.LowPassFilter)
                        }
                        StudioMenuItem("Band-Pass Voice Filter", Icons.Default.Equalizer, "menu_eff_bandpass") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.BandPassFilter)
                        }
                        StudioMenuItem("DC Offset Removal", Icons.Default.Tune, "menu_eff_dc") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.RemoveDcOffset)
                        }
                        StudioMenuItem("Noise Reduction & De-Click Cleaner", Icons.Default.GraphicEq, "menu_eff_noise_cleaner") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.OpenEffectsTab(1))
                        }

                        HorizontalDivider()
                        MenuCategoryHeader("DYNAMICS")
                        StudioMenuItem("Auto Gain (Level Volume)", Icons.AutoMirrored.Filled.VolumeUp, "menu_eff_auto_gain") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.ApplyAutoGain)
                        }
                        StudioMenuItem("Compressor & Limiter (Quick Apply)", Icons.Default.GraphicEq, "menu_eff_compressor") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.ApplyCompressor)
                        }
                        StudioMenuItem("Compressor & Peak Limiter Sliders...", Icons.Default.Tune, "menu_eff_comp_rack") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.OpenEffectsTab(4))
                        }
                        StudioMenuItem("Dynamic Expander", Icons.Default.GraphicEq, "menu_eff_expander") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.ApplyExpander)
                        }
                        StudioMenuItem("Normalize Peak (-0.1 dB)", Icons.AutoMirrored.Filled.VolumeUp, "menu_eff_norm_peak") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.NormalizePeak)
                        }
                        StudioMenuItem("Normalize RMS (-16 dBFS)", Icons.AutoMirrored.Filled.VolumeUp, "menu_eff_norm_rms") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.NormalizeRms)
                        }
                        StudioMenuItem("Fade In (Selection)", Icons.Default.Tune, "menu_eff_fade_in") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.FadeIn)
                        }
                        StudioMenuItem("Fade Out (Selection)", Icons.Default.Tune, "menu_eff_fade_out") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.FadeOut)
                        }
                        StudioMenuItem("Gain (+/- dB)", Icons.AutoMirrored.Filled.VolumeUp, "menu_eff_gain") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.OpenGainDialog)
                        }

                        HorizontalDivider()
                        MenuCategoryHeader("CREATIVE DSP & PRO RACK")
                        StudioMenuItem("Echo (Delay)", Icons.Default.Waves, "menu_eff_echo") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.OpenSingleEffect(StudioSingleEffectKey.ECHO))
                        }
                        StudioMenuItem("Reverb (Acoustic Space)", Icons.Default.Waves, "menu_eff_reverb") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.OpenSingleEffect(StudioSingleEffectKey.REVERB))
                        }
                        StudioMenuItem("Dynamics Compressor", Icons.Default.Tune, "menu_eff_comp_single") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.OpenSingleEffect(StudioSingleEffectKey.COMPRESSOR))
                        }
                        StudioMenuItem("Peak Limiter (Anti-Clip)", Icons.Default.Tune, "menu_eff_limiter_single") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.OpenSingleEffect(StudioSingleEffectKey.PEAK_LIMITER))
                        }
                        StudioMenuItem("Flanger", Icons.Default.Waves, "menu_eff_flanger_single") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.OpenSingleEffect(StudioSingleEffectKey.FLANGER))
                        }
                        StudioMenuItem("Chorus (Multi-Voice)", Icons.Default.Waves, "menu_eff_chorus_single") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.OpenSingleEffect(StudioSingleEffectKey.CHORUS))
                        }
                        StudioMenuItem("Overdrive & Distortion", Icons.Default.GraphicEq, "menu_eff_distortion_single") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.OpenSingleEffect(StudioSingleEffectKey.DISTORTION))
                        }
                        StudioMenuItem("7-Band Graphic Equalizer", Icons.Default.Equalizer, "menu_eff_eq7band_single") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.OpenSingleEffect(StudioSingleEffectKey.EQ_7_BAND))
                        }
                        StudioMenuItem("Pitch Shifter & Polarity", Icons.Default.AutoAwesome, "menu_eff_pitch_single") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.OpenSingleEffect(StudioSingleEffectKey.PITCH_POLARITY))
                        }
                        StudioMenuItem("Pitch / Time Stretch / Censor", Icons.Default.AutoAwesome, "menu_eff_pitch_censor") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.OpenProSuiteTab(0))
                        }
                        StudioMenuItem("Mechanize (Robot Ring Mod)", Icons.Default.AutoAwesome, "menu_eff_mechanize") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.ApplyMechanize)
                        }
                        StudioMenuItem("Reverse Audio", Icons.Default.Waves, "menu_eff_reverse") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.ReverseTrack)
                        }
                        StudioMenuItem("Tone & EQ Presets", Icons.Default.Equalizer, "menu_eff_eq") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.OpenEffectsTab(2))
                        }
                    }
                }

                // 5. VIEW DROPDOWN
                item {
                    MenuDropdownAnchor(
                        label = "View",
                        tag = "menu_bar_view",
                        expanded = openMenu == TopDropdownMenuKey.VIEW,
                        isHighContrast = isHighContrast,
                        onOpen = { openMenu = TopDropdownMenuKey.VIEW },
                        onDismiss = { openMenu = TopDropdownMenuKey.NONE }
                    ) {
                        StudioMenuItem("All (Fit Track)", Icons.Default.ZoomOutMap, "menu_view_all") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.ViewAll)
                        }
                        StudioMenuItem("Selection (Zoom to Selection)", Icons.Default.Crop, "menu_view_selection") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.ViewSelection)
                        }
                        StudioMenuItem("Specify...", Icons.Default.Tune, "menu_view_specify") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.OpenSpecifyViewDialog)
                        }
                        StudioMenuItem("Previous", Icons.Default.History, "menu_view_previous") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.ViewPrevious)
                        }
                        StudioMenuItem("Preset (Default 10s Window)", Icons.Default.Schedule, "menu_view_preset") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.ViewPresetDefault)
                        }
                        StudioMenuItem(
                            if (followCursor) "Follow Cursor: On" else "Follow Cursor: Off",
                            Icons.Default.PlayArrow,
                            "menu_view_follow_cursor"
                        ) {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.ToggleFollowCursor)
                        }

                        HorizontalDivider()
                        MenuCategoryHeader("TIME ZOOM")
                        StudioMenuItem("Zoom In", Icons.Default.ZoomIn, "menu_view_zoom_in") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.ZoomIn)
                        }
                        StudioMenuItem("Zoom Out", Icons.Default.ZoomOut, "menu_view_zoom_out") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.ZoomOut)
                        }
                        StudioMenuItem("Zoom 10:1", Icons.Default.ZoomIn, "menu_view_zoom_10_1") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.Zoom10To1)
                        }
                        StudioMenuItem("Zoom 1:1", Icons.Default.ZoomIn, "menu_view_zoom_1_1") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.Zoom1To1)
                        }
                        StudioMenuItem("1 Second", Icons.Default.Schedule, "menu_view_1_second") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.ViewTimeSpan(1_000L))
                        }
                        StudioMenuItem("10 Seconds", Icons.Default.Schedule, "menu_view_10_seconds") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.ViewTimeSpan(10_000L))
                        }
                        StudioMenuItem("1 Minute", Icons.Default.Schedule, "menu_view_1_minute") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.ViewTimeSpan(60_000L))
                        }
                        StudioMenuItem("10 Minutes", Icons.Default.Schedule, "menu_view_10_minutes") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.ViewTimeSpan(600_000L))
                        }
                        StudioMenuItem("1 Hour", Icons.Default.Schedule, "menu_view_1_hour") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.ViewTimeSpan(3_600_000L))
                        }

                        HorizontalDivider()
                        MenuCategoryHeader("GO TO")
                        StudioMenuItem("Start (Selection Start)", Icons.Default.SkipPrevious, "menu_view_goto_start") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.GoToSelectionStart)
                        }
                        StudioMenuItem("Finish (Selection End)", Icons.Default.SkipNext, "menu_view_goto_finish") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.GoToSelectionFinish)
                        }
                        StudioMenuItem("Beginning (0:00)", Icons.Default.FirstPage, "menu_view_goto_beginning") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.GoToBeginning)
                        }
                        StudioMenuItem("End (Track Finish)", Icons.AutoMirrored.Filled.LastPage, "menu_view_goto_end") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.GoToEnd)
                        }

                        HorizontalDivider()
                        MenuCategoryHeader("AMPLITUDE ZOOM")
                        StudioMenuItem("Vertical Zoom In", Icons.Default.Height, "menu_view_vzoom_in") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.VerticalZoomIn)
                        }
                        StudioMenuItem("Vertical Zoom Out", Icons.Default.Height, "menu_view_vzoom_out") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.VerticalZoomOut)
                        }
                        StudioMenuItem("Vertical All (1.0x)", Icons.Default.ZoomOutMap, "menu_view_vzoom_all") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.VerticalZoomAll)
                        }

                        HorizontalDivider()
                        StudioMenuItem("Visual Mode (Waveform / Spectrum / Bars)", Icons.Default.Visibility, "menu_view_visual") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.ToggleVisualMode)
                        }
                        StudioMenuItem(
                            if (isHighContrast) "High Contrast: On (Turn Off)" else "High Contrast: Off (Turn On)",
                            Icons.Default.InvertColors,
                            "menu_view_contrast"
                        ) {
                            openMenu = TopDropdownMenuKey.NONE
                            onToggleHighContrast()
                        }
                        HorizontalDivider()
                        MenuCategoryHeader("WORKSPACES")
                        StudioMenuItem("Audio Studio", Icons.Default.Audiotrack, "menu_view_tab_audio") {
                            openMenu = TopDropdownMenuKey.NONE
                            onSelectTab(StudioTab.AUDIO)
                        }
                        StudioMenuItem("Video Studio", Icons.Default.Movie, "menu_view_tab_video") {
                            openMenu = TopDropdownMenuKey.NONE
                            onSelectTab(StudioTab.VIDEO)
                        }
                        StudioMenuItem("Exported Files", Icons.Default.FolderOpen, "menu_view_tab_exports") {
                            openMenu = TopDropdownMenuKey.NONE
                            onSelectTab(StudioTab.EXPORTS)
                        }
                    }
                }

                // 6. TOOLS DROPDOWN
                item {
                    MenuDropdownAnchor(
                        label = "Tools",
                        tag = "menu_bar_tools",
                        expanded = openMenu == TopDropdownMenuKey.TOOLS,
                        isHighContrast = isHighContrast,
                        onOpen = { openMenu = TopDropdownMenuKey.TOOLS },
                        onDismiss = { openMenu = TopDropdownMenuKey.NONE }
                    ) {
                        StudioMenuItem("Voice Over Auto-Ducking", Icons.Default.Mic, "menu_tools_voiceover") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.OpenVoiceOverDialog)
                        }
                        StudioMenuItem("Tone / Noise Generator", Icons.Default.Waves, "menu_tools_tone") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.OpenToneGenerator)
                        }
                        StudioMenuItem("Studio Pro Rack (Flanger / Chorus / EQ)", Icons.Default.Tune, "menu_tools_rack") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.OpenEffectsTab(4))
                        }
                        HorizontalDivider()
                        StudioMenuItem("File Statistics & Peak/RMS", Icons.Default.Analytics, "menu_tools_stats") {
                            openMenu = TopDropdownMenuKey.NONE
                            onAudioCommand(AudioStudioCommand.OpenStatsDialog)
                        }
                        StudioMenuItem("Batch Processing (Multi-File)", Icons.Default.Build, "menu_tools_batch") {
                            openMenu = TopDropdownMenuKey.NONE
                            onOpenBatchProcessing()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuDropdownAnchor(
    label: String,
    tag: String,
    expanded: Boolean,
    isHighContrast: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Box {
        OutlinedButton(
            onClick = onOpen,
            modifier = Modifier
                .height(48.dp)
                .testTag(tag)
                .semantics { contentDescription = "$label dropdown menu" },
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (expanded) {
                    MaterialTheme.colorScheme.primaryContainer
                } else if (isHighContrast) {
                    Color.Black
                } else {
                    MaterialTheme.colorScheme.surface
                }
            ),
            border = BorderStroke(
                if (expanded || isHighContrast) 1.5.dp else 1.dp,
                if (expanded) MaterialTheme.colorScheme.primary else if (isHighContrast) Color.White else MaterialTheme.colorScheme.outlineVariant
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.width(2.dp))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            modifier = Modifier
                .width(268.dp)
                .heightIn(max = 460.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun MenuCategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
    )
}

@Composable
private fun StudioMenuItem(
    title: String,
    icon: ImageVector,
    tag: String,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag(tag)
    )
}
