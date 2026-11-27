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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.LocalHighContrast

object StudioUserGuideContent {
    const val HELP_ENGLISH_OVERVIEW =
        "Accessible Studio is a 100% offline, TalkBack-friendly Audio & Video Editing Studio. Edit voice recordings, songs, podcasts, and videos directly on your phone without internet."

    const val HELP_ENGLISH_GUIDE =
        "1. Open, Record & Blank Projects:\n" +
            "• Use File -> Open Audio File to load audio from your phone.\n" +
            "• Use File -> New Blank Project to create a clean empty or silent project tab.\n" +
            "• Open multiple files in separate tabs and switch tracks anytime.\n" +
            "• Tap Record on the transport dock to capture live microphone audio.\n\n" +
            "2. Select, Delete, Mute, Paste & Mix Audio:\n" +
            "• Use Selection -> Select All (or [ Start / ] End markers) to mark any region.\n" +
            "• Use Edit -> Copy, Cut, Mute Selection, Delete, Paste, Undo, and Redo.\n" +
            "• Clean Replace & Mix workflow: open a blank or main tab, run Select All -> Delete if clearing old audio, Paste your new track, and open Mix Audio or Voice Over (File, Edit, or Tools menu) to blend clipboard, open tabs, or phone audio files with Auto-Ducking.\n\n" +
            "3. Audio Effects, Adaptive De-Noise & Auto Gain:\n" +
            "• Open Effect for Repair, Cleaning, Adaptive Noise Reduction (with Real-Time Live Preview, Learn Mode, and A/B Compare), Auto Gain, Compressor, Peak Limiter, 7-Band EQ, Echo, Reverb, Speed/Pitch, Flanger, and Chorus.\n" +
            "• Inside each effect panel, use the Presets menu at the top to apply studio presets, fine-tune with custom sliders, or choose Default / Reset.\n\n" +
            "4. Playback, View, Scrubbing & Rewind/Forward Speed:\n" +
            "• Play 1 plays the active selection; Play 2 plays continuously; toggle Follow Cursor and Time/Amplitude Zoom in View.\n" +
            "• Tap -5s / +5s for audible reverse rewind or fast forward (adjust rewind/forward speed in Audio Editing); tap again to resume.\n\n" +
            "5. Save, Original File Names & Export Settings:\n" +
            "• Saving preserves the exact file name you opened.\n" +
            "• Open Settings from the Home screen to choose your default Export Selection Format (WAV, MP3, FLAC, M4A), High Contrast mode, and Haptic vibration, then tap Save All Settings.\n\n" +
            "6. Video Studio & Batch Processing:\n" +
            "• Switch to the Video Studio tab to trim video, reduce fan/room noise, replace audio tracks, apply effects, and export MP4.\n" +
            "• Use Tools -> Batch Processing to convert or process multiple files at once.\n" +
            "• Exports save offline to Music/Audio Studio/ and Movies/Accessible Studio/."

    const val HELP_HINDI_OVERVIEW =
        "Accessible Studio एक 100% ऑफ़लाइन, TalkBack और टच-फ्रेंडली ऑडियो और वीडियो एडिटिंग स्टूडियो ऐप है। बिना इंटरनेट के आप अपने फ़ोन पर ही आवाज़, गाने, पॉडकास्ट और वीडियो एडिट कर सकते हैं।"

    const val HELP_HINDI_GUIDE =
        "1. फ़ाइल खोलें, रिकॉर्ड करें और ब्लैंक प्रोजेक्ट बनाएं:\n" +
            "• फ़ोन से ऑडियो खोलने के लिए File -> Open Audio File दबाएं।\n" +
            "• खाली या साइलेंस प्रोजेक्ट बनाने के लिए File -> New Blank Project चुनें।\n" +
            "• एक साथ कई फ़ाइलें अलग-अलग टैब्स में खोलें और कभी भी स्विच करें।\n" +
            "• लाइव आवाज़ रिकॉर्ड करने के लिए नीचे Record बटन का उपयोग करें।\n\n" +
            "2. Select All, Delete, Mute Selection, Paste और Audio Mix:\n" +
            "• किसी भी हिस्से को चुनने के लिए Selection -> Select All (या [ Start / ] End) का उपयोग करें।\n" +
            "• Edit मेनू से Copy, Cut, Mute Selection, Delete, Paste, Undo और Redo करें।\n" +
            "• नया ऑडियो जोड़ने या मिक्स करने के लिए: Select All -> Delete से पुराना ऑडियो हटाकर नई फ़ाइल Paste करें, या File / Edit -> Mix Audio व Tools -> Voice Over (Auto-Ducking के साथ) का उपयोग करें।\n\n" +
            "3. ऑडियो इफ़ेक्ट्स, Adaptive Noise Reduction और Auto Gain:\n" +
            "• Effect मेनू में Adaptive Noise Reduction (Live Preview, Learn Mode व A/B Compare के साथ), Auto Gain, Compressor, Peak Limiter, 7-Band EQ, Echo, Reverb, Speed/Pitch, Flanger और Chorus उपलब्ध हैं।\n" +
            "• हर इफ़ेक्ट पैनल के ऊपर Presets मेनू दिया गया है — प्रीसेट चुनते ही स्लाइडर्स अपने आप सेट हो जाते हैं, और आप चाहें तो स्लाइडर्स से कस्टम बदलाव या Default / Reset कर सकते हैं।\n\n" +
            "4. प्लेबैक, View, नेविगेशन और Rewind/Forward स्पीड:\n" +
            "• Play 1 चुने हुए हिस्से को सुनाता है और Play 2 लगातार प्ले करता है; View में Follow Cursor व Time/Amplitude Zoom चुनें।\n" +
            "• -5s / +5s बटन से सुनते हुए तेज़ी से पीछे या आगे जाएं (ऑडियो एडिटिंग में रिवाइंड/फॉरवर्ड स्पीड बदल सकते हैं)।\n\n" +
            "5. फ़ाइल सेव नाम और एक्सपोर्ट सेटिंग्स:\n" +
            "• सेव करते समय आपकी ओपन की गई फ़ाइल का वही मूल नाम सुरक्षित रहता है।\n" +
            "• होम स्क्रीन पर Settings खोलकर Export Selection Format (WAV, MP3, FLAC, M4A), High Contrast और Haptics चुनें और Save All Settings दबाएं।\n\n" +
            "6. वीडियो स्टूडियो और बैच प्रोसेसिंग:\n" +
            "• Video Studio टैब से वीडियो ट्रिम, Adaptive Noise Reduction, ऑडियो रिप्लेस और MP4 एक्सपोर्ट करें।\n" +
            "• Tools -> Batch Processing से कई फ़ाइलों को एक साथ प्रोसेस करें। सभी फ़ाइलें फ़ोन में Music/Audio Studio/ और Movies/Accessible Studio/ में सेव होती हैं।"

    const val WELCOME_ENGLISH =
        "Welcome to Accessible Studio — a fast, 100% offline Audio & Video Editor built for everyone with full TalkBack support.\n\n" +
            "How to use:\n" +
            "• Open audio files, record voice, or start a Blank Project from the top File menu.\n" +
            "• Use Edit & Selection to Cut, Copy, Delete, Paste, or Mix multiple audio tracks.\n" +
            "• Open Effect to apply Compressor, EQ, Reverb, Noise Reduction, and Peak Limiter with built-in Presets & sliders.\n" +
            "• Open Settings -> About Accessible Studio anytime to view User Guide and What's New.\n" +
            "• Choose your export format (WAV, MP3, FLAC, M4A) inside Settings."

    const val WELCOME_HINDI =
        "Accessible Studio में आपका स्वागत है! यह एक 100% ऑफ़लाइन और TalkBack-फ्रेंडली ऑडियो-वीडियो एडिटिंग ऐप है।\n\n" +
            "कैसे इस्तेमाल करें:\n" +
            "• ऊपर File मेनू से ऑडियो फ़ाइल खोलें, आवाज़ रिकॉर्ड करें या New Blank Project बनाएं।\n" +
            "• Edit और Selection से ऑडियो काटें (Cut/Delete), Paste करें या Mix Audio से ट्रैक मिक्स करें।\n" +
            "• Effect मेनू में हर इफ़ेक्ट के Presets और स्लाइडर्स से आवाज़ को बेहतर बनाएं।\n" +
            "• Settings में अपना Export Format (WAV, MP3, FLAC, M4A) सेव करें और Settings -> About में पूरी User Guide व What's New देखें।"

    const val WHATS_NEW_ENGLISH =
        "What's New in Version 1.5.1 — Uninstall Old Build Fix, App Info Fallback & Update Reliability:\n\n" +
            "1. Fixed \"Uninstall Old Build\" Button on Package Conflict:\n" +
            "• Added Android's REQUEST_DELETE_PACKAGES permission so tapping Uninstall Old Build opens the system uninstall prompt properly without silent failure.\n" +
            "• Saving the update APK to Downloads now runs smoothly in the background before launching uninstall.\n\n" +
            "2. New \"Open App Info to Uninstall\" Button:\n" +
            "• Added a direct Open App Info to Uninstall button inside the Package Conflict card for guaranteed 1-tap access on any device.\n\n" +
            "3. GitHub Releases Download & 404 Recovery:\n" +
            "• Automatic asset fallback lookup, Open Releases Page fallback button on download errors, and persistent CI signing keys for smoother future upgrades."

    const val WHATS_NEW_HINDI =
        "Version 1.5.1 में नया क्या है — Uninstall Old Build बटन फ़िक्स और अपडेट सुधार:\n\n" +
            "1. \"Uninstall Old Build\" बटन अब सही से काम करता है:\n" +
            "• Android की REQUEST_DELETE_PACKAGES परमिशन जोड़ी गई है ताकि पुराने बिल्ड को अनइंस्टॉल करने का सिस्टम प्रॉम्प्ट तुरंत खुले।\n" +
            "• अनइंस्टॉल से पहले नया APK बैकग्राउंड में Downloads फ़ोल्डर में सेव हो जाता है।\n\n" +
            "2. नया \"Open App Info to Uninstall\" बटन:\n" +
            "• Package Conflict कार्ड में सीधे Android App Info खोलने का बटन जोड़ा गया है ताकि किसी भी फ़ोन पर एक टैप में अनइंस्टॉल किया जा सके।\n\n" +
            "3. GitHub Releases डाउनलोड और 404 रिकवरी:\n" +
            "• अपडेट डाउनलोड में ऑटोमैटिक 404 फ़ॉलबैक, Open Releases Page बटन और लगातार सही साइन किए गए अपडेट बिल्ड शामिल हैं।"
}

@Composable
fun StudioHelpUserGuideDialog(
    onDismiss: () -> Unit
) {
    var showWhatsNewDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Help,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "User Guide (English & हिंदी)",
                fontWeight = FontWeight.Black,
                modifier = Modifier.testTag("help_dialog_title")
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // English User Guide Section (First)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "User Guide (English)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = StudioUserGuideContent.HELP_ENGLISH_OVERVIEW,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = StudioUserGuideContent.HELP_ENGLISH_GUIDE,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(12.dp)
                            .testTag("help_guide_english")
                    )
                }

                // Hindi User Guide Section (Second)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "यूज़र गाइड (हिंदी)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = StudioUserGuideContent.HELP_HINDI_OVERVIEW,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = StudioUserGuideContent.HELP_HINDI_GUIDE,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(12.dp)
                            .testTag("help_guide_hindi")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("btn_close_help_dialog")
                    .semantics { contentDescription = "Close User Guide" }
            ) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun StudioWhatsNewDialog(
    onDismiss: () -> Unit
) {
    val isHighContrast = LocalHighContrast.current
    val cardBorder = if (isHighContrast) BorderStroke(1.5.dp, Color.White) else null

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        ),
        icon = {
            Icon(
                imageVector = Icons.Default.NewReleases,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "What's New (नया क्या है)",
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .testTag("whats_new_dialog_title")
                    .semantics { heading() }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = if (isHighContrast) Color.Black else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        if (isHighContrast) 1.5.dp else 1.dp,
                        if (isHighContrast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Accessible Studio v${com.example.BuildConfig.VERSION_NAME} • Recent Features & Bug Fixes",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isHighContrast) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("whats_new_version_badge")
                    )
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    border = cardBorder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "What's New in Version ${com.example.BuildConfig.VERSION_NAME} (English)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = StudioUserGuideContent.WHATS_NEW_ENGLISH,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.testTag("whats_new_english")
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    border = cardBorder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Version ${com.example.BuildConfig.VERSION_NAME} में नया क्या है (हिंदी)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = StudioUserGuideContent.WHATS_NEW_HINDI,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.testTag("whats_new_hindi")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("btn_close_whats_new")
                    .semantics { contentDescription = "Close What's New dialog" }
            ) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun StudioWelcomeDialog(
    onOk: () -> Unit,
    onDontShowAgain: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onOk,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        ),
        icon = {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Welcome to Accessible Studio",
                fontWeight = FontWeight.Black,
                modifier = Modifier.testTag("welcome_dialog_title")
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "How It Works (English)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = StudioUserGuideContent.WELCOME_ENGLISH,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.testTag("welcome_text_english")
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "कैसे उपयोग करें (हिंदी)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = StudioUserGuideContent.WELCOME_HINDI,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.testTag("welcome_text_hindi")
                        )
                    }
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDontShowAgain,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("btn_welcome_dont_show")
                    .semantics {
                        contentDescription = "Don't Show Again. Hide this welcome message on future launches."
                    }
            ) {
                Text("Don't Show Again", fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = {
            Button(
                onClick = onOk,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("btn_welcome_ok")
                    .semantics {
                        contentDescription = "OK. Close welcome message and start using Accessible Studio."
                    }
            ) {
                Text("OK", fontWeight = FontWeight.Bold)
            }
        }
    )
}
