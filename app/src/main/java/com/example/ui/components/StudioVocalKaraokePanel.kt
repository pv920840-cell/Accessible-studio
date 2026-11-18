package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.accessibility.AccessibilityHelper
import com.example.accessibility.VibrationType
import com.example.audio.AudioEditSettings
import com.example.audio.AudioPlayerManager
import com.example.audio.StereoPcmData
import com.example.audio.StudioAudioProcessor
import com.example.audio.VocalKaraokeMode
import com.example.audio.VocalKaraokeQuality
import com.example.audio.VocalKaraokeSeparator
import com.example.audio.WavAudioProcessor
import com.example.data.MediaRepository
import com.example.data.StudioPreferences
import com.example.ui.theme.LocalHighContrast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VocalKaraokeMakerCard(
    currentTrackTitle: String,
    currentTrackPcm: ShortArray,
    sampleRate: Int,
    initialStereoSourceUri: Uri? = null,
    audioProcessor: WavAudioProcessor,
    playerManager: AudioPlayerManager,
    mediaRepository: MediaRepository,
    defaultExportFormat: String,
    hapticsEnabled: Boolean,
    onApplySingleStemToTrack: (label: String, stemPcm: ShortArray) -> Unit,
    onOpenBothStemsAsTabs: ((vocalsTitle: String, vocalsPcm: ShortArray, karaokeTitle: String, karaokePcm: ShortArray) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isHighContrast = LocalHighContrast.current

    var selectedMode by remember { mutableStateOf(VocalKaraokeMode.DUAL_STEMS) }
    var selectedQuality by remember { mutableStateOf(VocalKaraokeQuality.BALANCED) }
    var strength by remember { mutableFloatStateOf(VocalKaraokeQuality.BALANCED.separationStrength) }
    var bassPreserveHz by remember { mutableFloatStateOf(VocalKaraokeQuality.BALANCED.bassPreserveHz) }
    var vocalPresence by remember { mutableFloatStateOf(VocalKaraokeQuality.BALANCED.vocalPresenceBoost) }
    var autoAlignPhase by remember { mutableStateOf(true) }
    var manualInvertRight by remember { mutableStateOf(false) }
    var studioMasteringPolish by remember { mutableStateOf(true) }

    var loadedStereoData by remember { mutableStateOf<StereoPcmData?>(null) }
    var loadedStereoTitle by remember { mutableStateOf<String?>(null) }
    var isDecodingStereo by remember { mutableStateOf(false) }

    var isProcessing by remember { mutableStateOf(false) }
    var generatedVocals by remember { mutableStateOf<ShortArray?>(null) }
    var generatedKaraoke by remember { mutableStateOf<ShortArray?>(null) }
    var usedStereoEngine by remember { mutableStateOf(false) }
    var isExportingStem by remember { mutableStateOf(false) }
    var lastStatusMessage by remember { mutableStateOf("") }

    val playerIsPlaying by playerManager.isPlaying.collectAsState()
    var activePreviewTag by remember { mutableStateOf<String?>(null) }
    var didPreviewStem by remember { mutableStateOf(false) }
    var didApplyOrSwitch by remember { mutableStateOf(false) }

    LaunchedEffect(playerIsPlaying) {
        if (!playerIsPlaying) {
            activePreviewTag = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (didPreviewStem && !didApplyOrSwitch && currentTrackPcm.isNotEmpty()) {
                try {
                    playerManager.stop()
                    playerManager.loadPcmData(currentTrackPcm, sampleRate)
                } catch (_: Throwable) {}
            }
        }
    }

    // Auto-inspect original project URI once for true stereo Mid/Side separation if available
    LaunchedEffect(initialStereoSourceUri) {
        if (initialStereoSourceUri != null && loadedStereoData == null) {
            isDecodingStereo = true
            try {
                val decoded = audioProcessor.decodeUriToStereoPcm(initialStereoSourceUri)
                val name = audioProcessor.queryAudioDisplayName(initialStereoSourceUri) ?: currentTrackTitle.ifBlank { "Song" }
                if (decoded != null && VocalKaraokeSeparator.hasStereoContent(decoded.left, decoded.right)) {
                    loadedStereoData = decoded
                    loadedStereoTitle = name
                    if (lastStatusMessage.isBlank()) {
                        lastStatusMessage = "Stereo channels detected from $name — ready for Vocal Removal & Extraction."
                    }
                } else if (decoded != null && lastStatusMessage.isBlank()) {
                    lastStatusMessage = "Loaded Mono audio ($name) — HPSS Spectral Vocal Engine ready."
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Throwable) {
                if (lastStatusMessage.isBlank()) {
                    val reason = e.localizedMessage?.takeIf { it.isNotBlank() } ?: "using active track"
                    lastStatusMessage = "Could not inspect stereo source ($reason)."
                }
            } finally {
                isDecodingStereo = false
            }
        }
    }

    val songFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Throwable) {}
            isDecodingStereo = true
            scope.launch {
                try {
                    val pickedName = audioProcessor.queryAudioDisplayName(uri) ?: "Selected Song"
                    val stereo = audioProcessor.decodeUriToStereoPcm(uri)
                        ?: audioProcessor.decodeUriToPcm(uri).takeIf { it.isNotEmpty() }?.let { monoFallback ->
                            StereoPcmData(
                                left = monoFallback,
                                right = monoFallback,
                                sampleRate = sampleRate,
                                channelCount = 1
                            )
                        }
                    if (stereo != null && stereo.left.isNotEmpty()) {
                        loadedStereoData = stereo
                        loadedStereoTitle = pickedName
                        generatedVocals = null
                        generatedKaraoke = null
                        if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.SUCCESS)
                        val modeInfo = if (VocalKaraokeSeparator.hasStereoContent(stereo.left, stereo.right)) {
                            "Stereo channels ready (Mid/Side Vocal Canceller)"
                        } else {
                            "Mono file ready (HPSS Spectral Separation)"
                        }
                        lastStatusMessage = "Loaded $pickedName: $modeInfo"
                        AccessibilityHelper.announceForAccessibility(context, lastStatusMessage)
                    } else {
                        lastStatusMessage = "Could not decode selected audio file. Try MP3, WAV, or M4A."
                        AccessibilityHelper.announceForAccessibility(context, lastStatusMessage)
                        Toast.makeText(context, lastStatusMessage, Toast.LENGTH_SHORT).show()
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Throwable) {
                    val reason = e.localizedMessage?.takeIf { it.isNotBlank() } ?: "Cannot read audio"
                    lastStatusMessage = "File error: $reason"
                    AccessibilityHelper.announceForAccessibility(context, lastStatusMessage)
                    Toast.makeText(context, lastStatusMessage, Toast.LENGTH_SHORT).show()
                } finally {
                    isDecodingStereo = false
                }
            }
        }
    }

    val activeSourceTitle = loadedStereoTitle ?: currentTrackTitle.ifBlank { "Song" }
    val hasTrueStereo = loadedStereoData?.let { VocalKaraokeSeparator.hasStereoContent(it.left, it.right) } ?: false

    fun runSeparation() {
        if (isProcessing || isDecodingStereo) return
        val stereo = loadedStereoData
        if (stereo == null && currentTrackPcm.isEmpty()) {
            val emptyMsg = "Audio track is empty. Open or pick a song first."
            lastStatusMessage = emptyMsg
            AccessibilityHelper.announceForAccessibility(context, emptyMsg)
            Toast.makeText(context, emptyMsg, Toast.LENGTH_SHORT).show()
            return
        }
        isProcessing = true
        scope.launch {
            try {
                val result = try {
                    withContext(Dispatchers.Default) {
                        if (stereo != null && (VocalKaraokeSeparator.hasStereoContent(stereo.left, stereo.right) || manualInvertRight)) {
                            VocalKaraokeSeparator.separateStereo(
                                left = stereo.left,
                                right = stereo.right,
                                quality = selectedQuality,
                                separationStrength = strength,
                                bassPreserveHz = bassPreserveHz,
                                vocalPresenceBoost = vocalPresence,
                                sampleRate = stereo.sampleRate,
                                autoAlignPhase = autoAlignPhase,
                                manualInvertRight = manualInvertRight,
                                studioMasteringPolish = studioMasteringPolish
                            )
                        } else {
                            val monoInput = stereo?.left ?: currentTrackPcm
                            VocalKaraokeSeparator.separateMono(
                                samples = monoInput,
                                quality = selectedQuality,
                                separationStrength = strength,
                                bassPreserveHz = bassPreserveHz,
                                vocalPresenceBoost = vocalPresence,
                                sampleRate = stereo?.sampleRate ?: sampleRate,
                                studioMasteringPolish = studioMasteringPolish
                            )
                        }
                    }
                } catch (_: OutOfMemoryError) {
                    // Graceful low-memory fallback: release transient buffers & retry lightweight mono path
                    System.gc()
                    withContext(Dispatchers.Default) {
                        val fallbackPcm = stereo?.left ?: currentTrackPcm
                        VocalKaraokeSeparator.separateMono(
                            samples = fallbackPcm,
                            quality = selectedQuality,
                            separationStrength = strength,
                            bassPreserveHz = bassPreserveHz,
                            vocalPresenceBoost = vocalPresence,
                            sampleRate = stereo?.sampleRate ?: sampleRate,
                            studioMasteringPolish = false
                        )
                    }
                }
                generatedVocals = result.vocals
                generatedKaraoke = result.karaoke
                usedStereoEngine = result.usedStereoSeparation
                val engineNote = if (result.usedStereoSeparation) "Stereo Mid/Side + STFT" else "HPSS Spectral"
                val phaseNote = if (result.phaseCorrected) " • Phase Aligned" else ""
                val masterNote = if (studioMasteringPolish) " • Studio EQ/Comp" else ""
                lastStatusMessage = "Extracted Vocals & Karaoke stems ready ($engineNote$phaseNote$masterNote)."
                if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.SUCCESS)
                AccessibilityHelper.announceForAccessibility(context, lastStatusMessage)
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: OutOfMemoryError) {
                val msg = "Separation failed: Device memory low for this track length. Try trimming the audio first."
                lastStatusMessage = msg
                AccessibilityHelper.announceForAccessibility(context, msg)
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            } catch (e: Throwable) {
                val reason = e.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: e::class.java.simpleName
                    ?: "Unexpected separation error"
                val msg = "Separation failed: $reason"
                lastStatusMessage = msg
                AccessibilityHelper.announceForAccessibility(context, msg)
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            } finally {
                isProcessing = false
            }
        }
    }

    fun exportStem(stemPcm: ShortArray, suffix: String) {
        if (stemPcm.isEmpty() || isExportingStem) return
        isExportingStem = true
        scope.launch {
            try {
                val cleanBase = StudioPreferences.extractBaseFileName(activeSourceTitle)
                    .replace(Regex("[^\\p{L}\\p{N}_\\- ]"), "_")
                    .trim('_', ' ')
                    .ifBlank { "Track" }
                val outName = "${cleanBase}_$suffix"
                val outFile = audioProcessor.exportSingleAudioFile(
                    mainPcm = stemPcm,
                    bgTrackConfig = null,
                    bgPcm = null,
                    settings = AudioEditSettings(),
                    outputFileName = outName,
                    format = defaultExportFormat,
                    onProgress = {}
                )
                mediaRepository.refreshExports()
                if (hapticsEnabled) AccessibilityHelper.vibrate(context, VibrationType.SUCCESS)
                val msg = "Exported ${outFile.name} to Exported Files."
                lastStatusMessage = msg
                AccessibilityHelper.announceForAccessibility(context, msg)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Throwable) {
                val reason = e.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: e::class.java.simpleName
                    ?: "Could not write file"
                val msg = "Export failed: $reason"
                lastStatusMessage = msg
                AccessibilityHelper.announceForAccessibility(context, msg)
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            } finally {
                isExportingStem = false
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_workspace_vocal_karaoke"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        border = BorderStroke(
            if (isHighContrast) 2.dp else 1.dp,
            if (isHighContrast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Vocal Extractor & Karaoke Maker",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "Isolate singing vocals (Acapella) or remove vocals for Karaoke instrumental.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (hasTrueStereo) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = if (hasTrueStereo) "STEREO STFT" else "MONO STFT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("badge_vk_engine_mode")
                    )
                }
            }

            // Source song info + Pick Stereo Song button
            OutlinedButton(
                onClick = { songFilePicker.launch(arrayOf("audio/*", "application/ogg")) },
                enabled = !isDecodingStereo && !isProcessing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_vk_pick_stereo_file")
                    .semantics {
                        contentDescription = "Choose a stereo song file from your phone for Vocal Extractor or Vocal Remover. Currently loaded: $activeSourceTitle"
                    }
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (loadedStereoTitle != null) "Song: $loadedStereoTitle (Change)" else "Choose Stereo Song File (Optional)",
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            if (!hasTrueStereo && !isDecodingStereo) {
                Text(
                    text = "Tip: Current track is Mono. Choosing a stereo song (MP3/WAV/M4A) unlocks Stereo Mid/Side center vocal separation for cleaner Karaoke.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isDecodingStereo || isProcessing || isExportingStem) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag("progress_vk"))
                Text(
                    text = when {
                        isDecodingStereo -> "Decoding stereo channels..."
                        isExportingStem -> "Exporting stem audio..."
                        else -> "Separating Vocals & Karaoke stems..."
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Mode Chips
            Text("Select Mode", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    VocalKaraokeMode.EXTRACT_VOCALS to "chip_vk_mode_vocals",
                    VocalKaraokeMode.KARAOKE_MAKER to "chip_vk_mode_karaoke",
                    VocalKaraokeMode.DUAL_STEMS to "chip_vk_mode_both"
                ).forEach { (mode, tag) ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = { selectedMode = mode },
                        label = { Text(mode.displayName, fontWeight = FontWeight.Bold) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag(tag)
                            .semantics {
                                contentDescription = "${mode.displayName}. ${mode.shortDescription}"
                            }
                    )
                }
            }

            // Quality Preset Chips
            Text("Separation Quality Preset", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    VocalKaraokeQuality.GENTLE to "chip_vk_quality_gentle",
                    VocalKaraokeQuality.BALANCED to "chip_vk_quality_balanced",
                    VocalKaraokeQuality.DEEP_ISOLATION to "chip_vk_quality_deep"
                ).forEach { (q, tag) ->
                    FilterChip(
                        selected = selectedQuality == q,
                        onClick = {
                            selectedQuality = q
                            strength = q.separationStrength
                            bassPreserveHz = q.bassPreserveHz
                            vocalPresence = q.vocalPresenceBoost
                        },
                        label = { Text(q.displayName, fontWeight = FontWeight.Bold) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag(tag)
                            .semantics {
                                contentDescription = "${q.displayName} quality. ${q.shortDescription}"
                            }
                    )
                }
            }

            AccessibleEffectSlider(
                title = "Vocal Separation Strength",
                value = strength,
                onValueChange = { strength = it },
                valueRange = 0.25f..1.0f,
                step = 0.05f,
                displayValue = "${(strength * 100).toInt()}%",
                spokenDescription = "Vocal separation strength ${(strength * 100).toInt()} percent",
                testTag = "slider_vk_strength"
            )

            AccessibleEffectSlider(
                title = "Karaoke Bass Preservation",
                value = bassPreserveHz,
                onValueChange = { bassPreserveHz = it },
                valueRange = 80f..260f,
                step = 10f,
                displayValue = "${bassPreserveHz.toInt()} Hz",
                spokenDescription = "Karaoke bass preservation below ${bassPreserveHz.toInt()} Hertz",
                testTag = "slider_vk_bass"
            )

            AccessibleEffectSlider(
                title = "Vocal Presence Boost",
                value = vocalPresence,
                onValueChange = { vocalPresence = it },
                valueRange = 0f..1f,
                step = 0.05f,
                displayValue = "${(vocalPresence * 100).toInt()}%",
                spokenDescription = "Vocal presence clarity ${(vocalPresence * 100).toInt()} percent",
                testTag = "slider_vk_clarity"
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Phase Alignment & Studio Mastering",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Auto Phase Correction", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("Align out-of-phase stereo & anchor bass mono", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("switch_vk_phase_correct")
                                .toggleable(
                                    value = autoAlignPhase,
                                    role = Role.Switch,
                                    onValueChange = { autoAlignPhase = it }
                                )
                                .semantics {
                                    contentDescription = "Auto phase correction and low-end mono alignment"
                                }
                        ) {
                            Switch(
                                checked = autoAlignPhase,
                                onCheckedChange = null,
                                modifier = Modifier.clearAndSetSemantics {}
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Phase Invert Right Channel (Φ)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("Flip Right polarity to fix inverted stereo mics", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("switch_vk_phase_invert_r")
                                .toggleable(
                                    value = manualInvertRight,
                                    role = Role.Switch,
                                    onValueChange = { manualInvertRight = it }
                                )
                                .semantics {
                                    contentDescription = "Manual phase invert right channel phi"
                                }
                        ) {
                            Switch(
                                checked = manualInvertRight,
                                onCheckedChange = null,
                                modifier = Modifier.clearAndSetSemantics {}
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Bone-Dry Cleanup + Mastering EQ/Comp", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("De-hum/de-reverb acapella & corrective 250-400Hz / 2-4kHz EQ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("switch_vk_master_polish")
                                .toggleable(
                                    value = studioMasteringPolish,
                                    role = Role.Switch,
                                    onValueChange = { studioMasteringPolish = it }
                                )
                                .semantics {
                                    contentDescription = "Bone-dry vocal cleanup and studio mastering EQ and compression"
                                }
                        ) {
                            Switch(
                                checked = studioMasteringPolish,
                                onCheckedChange = null,
                                modifier = Modifier.clearAndSetSemantics {}
                            )
                        }
                    }
                }
            }

            Button(
                onClick = { runSeparation() },
                enabled = !isProcessing && !isDecodingStereo,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_vk_process")
                    .semantics {
                        contentDescription = "Run ${selectedMode.displayName} using ${selectedQuality.displayName} preset."
                    },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when (selectedMode) {
                        VocalKaraokeMode.EXTRACT_VOCALS -> "Extract Vocals Only"
                        VocalKaraokeMode.KARAOKE_MAKER -> "Create Karaoke Instrumental"
                        VocalKaraokeMode.DUAL_STEMS -> "Separate Vocals & Karaoke"
                    },
                    fontWeight = FontWeight.Black
                )
            }

            if (lastStatusMessage.isNotBlank()) {
                val isErrorStatus = lastStatusMessage.startsWith("Separation failed") ||
                    lastStatusMessage.startsWith("Export failed") ||
                    lastStatusMessage.startsWith("File error") ||
                    lastStatusMessage.startsWith("Preview error") ||
                    lastStatusMessage.startsWith("Could not decode")
                Text(
                    text = lastStatusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isErrorStatus) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("txt_vk_status")
                )
            }

            val vocalsStem = generatedVocals
            val karaokeStem = generatedKaraoke
            if (vocalsStem != null && karaokeStem != null) {
                HorizontalDivider()
                Text(
                    text = "Extracted Stems Ready (${if (usedStereoEngine) "Stereo Mid/Side" else "Spectral Mono"})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.testTag("txt_vk_stems_ready")
                )

                if (selectedMode == VocalKaraokeMode.EXTRACT_VOCALS || selectedMode == VocalKaraokeMode.DUAL_STEMS) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("Vocals Only (Acapella)", fontWeight = FontWeight.Black)
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val isPreviewingVocals = playerIsPlaying && activePreviewTag == "vocals"
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            if (isPreviewingVocals) {
                                                playerManager.stop()
                                                activePreviewTag = null
                                            } else {
                                                didPreviewStem = true
                                                activePreviewTag = "vocals"
                                                playerManager.stop()
                                                playerManager.loadPcmData(vocalsStem, sampleRate)
                                                playerManager.play()
                                            }
                                        } catch (t: Throwable) {
                                            activePreviewTag = null
                                            val msg = "Preview error: ${t.localizedMessage ?: "Audio playback unavailable"}"
                                            lastStatusMessage = msg
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("btn_vk_preview_vocals")
                                ) {
                                    Icon(if (isPreviewingVocals) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (isPreviewingVocals) "Stop Preview" else "Preview Vocals", fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = {
                                        didApplyOrSwitch = true
                                        try { playerManager.stop() } catch (_: Throwable) {}
                                        onApplySingleStemToTrack("Applied Extracted Vocals to track", vocalsStem)
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("btn_vk_apply_vocals")
                                ) {
                                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Apply to Track", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { exportStem(vocalsStem, "Vocals") },
                                    enabled = !isExportingStem,
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("btn_vk_export_vocals")
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Save Vocals", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (selectedMode == VocalKaraokeMode.KARAOKE_MAKER || selectedMode == VocalKaraokeMode.DUAL_STEMS) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("Karaoke Maker (Vocals Removed)", fontWeight = FontWeight.Black)
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val isPreviewingKaraoke = playerIsPlaying && activePreviewTag == "karaoke"
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            if (isPreviewingKaraoke) {
                                                playerManager.stop()
                                                activePreviewTag = null
                                            } else {
                                                didPreviewStem = true
                                                activePreviewTag = "karaoke"
                                                playerManager.stop()
                                                playerManager.loadPcmData(karaokeStem, sampleRate)
                                                playerManager.play()
                                            }
                                        } catch (t: Throwable) {
                                            activePreviewTag = null
                                            val msg = "Preview error: ${t.localizedMessage ?: "Audio playback unavailable"}"
                                            lastStatusMessage = msg
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("btn_vk_preview_karaoke")
                                ) {
                                    Icon(if (isPreviewingKaraoke) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (isPreviewingKaraoke) "Stop Preview" else "Preview Karaoke", fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = {
                                        didApplyOrSwitch = true
                                        try { playerManager.stop() } catch (_: Throwable) {}
                                        onApplySingleStemToTrack("Applied Karaoke Instrumental (Vocals Removed) to track", karaokeStem)
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("btn_vk_apply_karaoke")
                                ) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Apply to Track", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { exportStem(karaokeStem, "Karaoke") },
                                    enabled = !isExportingStem,
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("btn_vk_export_karaoke")
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Save Karaoke", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (onOpenBothStemsAsTabs != null) {
                    val base = StudioPreferences.extractBaseFileName(activeSourceTitle).ifBlank { "Track" }
                    Button(
                        onClick = {
                            didApplyOrSwitch = true
                            try { playerManager.stop() } catch (_: Throwable) {}
                            onOpenBothStemsAsTabs(
                                "$base (Vocals)",
                                vocalsStem,
                                "$base (Karaoke)",
                                karaokeStem
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_vk_open_both_tabs")
                    ) {
                        Text("Open Both Stems in Separate Project Tabs", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun StudioVocalKaraokeDialog(
    currentTrackTitle: String,
    currentTrackPcm: ShortArray,
    sampleRate: Int,
    initialStereoSourceUri: Uri? = null,
    audioProcessor: WavAudioProcessor,
    playerManager: AudioPlayerManager,
    mediaRepository: MediaRepository,
    defaultExportFormat: String,
    hapticsEnabled: Boolean,
    onApplySingleStem: (label: String, stemPcm: ShortArray) -> Unit,
    onOpenBothStemsAsTabs: (vocalsTitle: String, vocalsPcm: ShortArray, karaokeTitle: String, karaokePcm: ShortArray) -> Unit,
    onDismiss: () -> Unit
) {
    val isHighContrast = LocalHighContrast.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BackHandler { onDismiss() }
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 680.dp)
                .testTag("dialog_vocal_karaoke"),
            shape = RoundedCornerShape(20.dp),
            color = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.background,
            border = if (isHighContrast) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Vocal Extractor & Karaoke Maker",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("btn_close_vocal_karaoke_dialog")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Vocal Extractor and Karaoke Maker")
                    }
                }
                HorizontalDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp)
                ) {
                    VocalKaraokeMakerCard(
                        currentTrackTitle = currentTrackTitle,
                        currentTrackPcm = currentTrackPcm,
                        sampleRate = sampleRate,
                        initialStereoSourceUri = initialStereoSourceUri,
                        audioProcessor = audioProcessor,
                        playerManager = playerManager,
                        mediaRepository = mediaRepository,
                        defaultExportFormat = defaultExportFormat,
                        hapticsEnabled = hapticsEnabled,
                        onApplySingleStemToTrack = { label, stem ->
                            onApplySingleStem(label, stem)
                            onDismiss()
                        },
                        onOpenBothStemsAsTabs = { vTitle, vPcm, kTitle, kPcm ->
                            onOpenBothStemsAsTabs(vTitle, vPcm, kTitle, kPcm)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}
