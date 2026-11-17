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
            "2. Select, Delete, Paste & Mix Audio:\n" +
            "• Use Selection -> Select All (or [ Start / ] End markers) to mark any region.\n" +
            "• Use Edit -> Copy, Cut, Delete, Paste, Undo, and Redo.\n" +
            "• Clean Replace & Mix workflow: open a blank or main tab, run Select All -> Delete if clearing old audio, Paste your new track, and open Mix Audio (File or Edit menu) to blend clipboard, open tabs, or phone audio files with custom volume balance.\n\n" +
            "3. Audio Effects & Per-Effect Presets:\n" +
            "• Open Effect for Repair, Cleaning, Compressor, Peak Limiter, Noise Reduction, 7-Band EQ, Echo, Reverb, Speed/Pitch, Flanger, and Chorus.\n" +
            "• Inside each effect panel, use the Presets menu at the top to apply studio presets, fine-tune with custom sliders, or choose Default / Reset.\n\n" +
            "4. Playback & Scrubbing:\n" +
            "• Play 1 plays the active selection; Play 2 plays continuously.\n" +
            "• Tap -5s / +5s for audible reverse rewind or fast forward; tap again to resume.\n\n" +
            "5. Save, Original File Names & Export Settings:\n" +
            "• Saving preserves the exact file name you opened.\n" +
            "• Open Settings from the Home screen to choose your default Export Selection Format (WAV, MP3, FLAC, M4A), High Contrast mode, and Haptic vibration, then tap Save All Settings.\n\n" +
            "6. Video Studio & Batch Processing:\n" +
            "• Switch to the Video Studio tab to trim video, replace audio tracks, apply effects, and export MP4.\n" +
            "• Use Vocal & Karaoke Maker (in Project Hub or Tools menu) to extract singing vocals only or remove vocals to create Karaoke instrumentals.\n" +
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
            "2. Select All, Delete, Paste और Audio Mix:\n" +
            "• किसी भी हिस्से को चुनने के लिए Selection -> Select All (या [ Start / ] End) का उपयोग करें।\n" +
            "• Edit मेनू से Copy, Cut, Delete, Paste, Undo और Redo करें।\n" +
            "• नया ऑडियो जोड़ने या मिक्स करने के लिए: Select All -> Delete से पुराना ऑडियो हटाकर नई फ़ाइल Paste करें, या File / Edit -> Mix Audio खोलकर दो या अधिक ऑडियो को वॉल्यूम बैलेंस के साथ मिक्स करें।\n\n" +
            "3. ऑडियो इफ़ेक्ट्स और हर इफ़ेक्ट में Presets:\n" +
            "• Effect मेनू में Compressor, Peak Limiter, Noise Reduction, 7-Band EQ, Echo, Reverb, Speed/Pitch, Flanger और Chorus उपलब्ध हैं।\n" +
            "• हर इफ़ेक्ट पैनल के ऊपर Presets मेनू दिया गया है — प्रीसेट चुनते ही स्लाइडर्स अपने आप सेट हो जाते हैं, और आप चाहें तो स्लाइडर्स से कस्टम बदलाव या Default / Reset कर सकते हैं।\n\n" +
            "4. प्लेबैक और नेविगेशन:\n" +
            "• Play 1 चुने हुए हिस्से को सुनाता है और Play 2 लगातार प्ले करता है।\n" +
            "• -5s / +5s बटन से सुनते हुए तेज़ी से पीछे या आगे जाएं।\n\n" +
            "5. फ़ाइल सेव नाम और एक्सपोर्ट सेटिंग्स:\n" +
            "• सेव करते समय आपकी ओपन की गई फ़ाइल का वही मूल नाम सुरक्षित रहता है।\n" +
            "• होम स्क्रीन पर Settings खोलकर Export Selection Format (WAV, MP3, FLAC, M4A), High Contrast और Haptics चुनें और Save All Settings दबाएं।\n\n" +
            "6. वीडियो स्टूडियो और बैच प्रोसेसिंग:\n" +
            "• Video Studio टैब से वीडियो ट्रिम, ऑडियो रिप्लेस और MP4 एक्सपोर्ट करें।\n" +
            "• किसी गाने से केवल आवाज़ (Vocals) निकालने या Karaoke (बिना आवाज़ का म्यूज़िक) बनाने के लिए Project Hub या Tools -> Vocal Extractor & Karaoke Maker का उपयोग करें।\n" +
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
        "1. Long Audio Duration & Playback Fix (6+ Minutes):\n" +
            "• Removed the 5-minute (300s) import cutoff and fixed MediaCodec EOS/format handling so 6+ minute and long audio tracks import, play, scrub, and export completely.\n\n" +
            "2. Mute Selection in Edit Menu:\n" +
            "• Added Edit -> Mute Selection to silence audio between Trim Start and Trim End markers while preserving total track duration and full Undo/Redo support.\n\n" +
            "3. Complete View, Time Zoom, Go To & Amplitude Zoom:\n" +
            "• Added All, Selection, Specify..., Previous view history, Preset, Follow Cursor toggle, Time Zoom (Zoom In, Zoom Out, 10:1, 1:1, 1s, 10s, 1m, 10m, 1h), Go To (Start, Finish, Beginning, End), and Amplitude Zoom (Vertical Zoom In/Out/All).\n\n" +
            "4. Blank Canvas, Multi-Tab Editing & Audio Mixing:\n" +
            "• Open multiple project tabs, create 0s or silent Blank Projects, delete entire audio cleanly with Select All -> Delete, paste cleanly, and mix tracks with volume balance & peak limiter.\n\n" +
            "5. Saved Filenames, Export Settings & Effect Presets:\n" +
            "• Original filenames are preserved when saving, Accessibility Settings (Export Format, High Contrast, Haptics) persist properly, and Effects include built-in Presets with Reset.\n\n" +
            "6. Bilingual User Guide & Launch Welcome:\n" +
            "• Added English & Hindi User Guide inside Help, first-launch Welcome modal with Don't Show Again & OK, plus Batch Processing trim and empty-canvas stability fixes.\n\n" +
            "7. Dual-File / Multi-Tab Import & Playback Fix:\n" +
            "• Fixed playback race conditions when opening or switching between multiple audio tabs, added end-of-track auto-rewind on Play, direct RIFF WAV + 24-bit/32-bit Float PCM decoding, and Import Multiple Files (Tabs).\n\n" +
            "8. Help Menu & Direct What's New Modal:\n" +
            "• Added a dedicated Help dropdown menu in the top DAW bar with direct access to What's New and the bilingual User Guide.\n\n" +
            "9. Version 1.1 — Auto Gain & Voice Over Music Presets:\n" +
            "• Added one-tap Auto Gain leveling in Effects and Workspace to bring uneven audio to a clear, balanced loudness with peak protection.\n" +
            "• Upgraded Tools -> Voice Over Auto-Ducking with a separate Add Audio / Music File button alongside Clipboard, Auto-Ducking toggle, and presets (Tutorial, Podcast, Vlog / YouTube, Story / Calm, Steady Music).\n\n" +
            "10. Version 1.1 — Trim → Save → Export Fix & Cleaner UI:\n" +
            "• Fixed Trim & Cut so adjusting Start/End markers and tapping Save crops and commits the trimmed audio before final export.\n" +
            "• Streamlined top & home bars by keeping About Accessible Studio cleanly inside Settings and removing extra About & Open Accessibility Settings icons.\n\n" +
            "11. Version 1.2 — Isolate Spoken Audio (inside Effect -> Noise Reduction):\n" +
            "• Added STFT Adaptive Spectral Subtraction & 50/60 Hz Harmonic Hum Suppression directly inside Effect -> Noise Reduction & De-Click Cleaner.\n" +
            "• Includes Light Filtering, Medium Filtering, and Strong Filtering presets plus fine sliders preserving pristine vocal clarity.\n" +
            "• Consolidated User Guide and What's New cleanly into a single location inside Settings -> About Accessible Studio.\n\n" +
            "12. Vocal Extractor & Karaoke Maker:\n" +
            "• Added offline Vocal Extractor (Vocals Only / Acapella) and Karaoke Maker (Instrumental with bass preservation) in Project Hub and Tools -> Vocal Extractor & Karaoke Maker.\n" +
            "• Supports Stereo Mid/Side + STFT phase separation on stereo songs, Mono spectral harmonic separation, Gentle/Balanced/Deep presets, stem preview, multi-tab splitting, and direct stem export."

    const val WHATS_NEW_HINDI =
        "1. लंबे ऑडियो (6+ मिनट) ड्यूरेशन और प्लेबैक बग फ़िक्स:\n" +
            "• 5 मिनट (300 सेकंड) पर रुकने की सीमा हटा दी गई है और MediaCodec डिकोडिंग को ठीक किया गया है—अब 6 मिनट या उससे लंबे ऑडियो पूरी लंबाई के साथ इम्पोर्ट, प्ले और एक्सपोर्ट होते हैं।\n\n" +
            "2. Edit मेनू में Mute Selection:\n" +
            "• अब Edit -> Mute Selection से Trim Start और Trim End मार्कर के बीच का ऑडियो शांत (0) कर सकते हैं। इससे कुल समय नहीं बदलता और Undo/Redo भी काम करता है।\n\n" +
            "3. नए View, Time Zoom, Go To और Amplitude Zoom विकल्प:\n" +
            "• View मेनू में All, Selection, Specify..., Previous, Preset, Follow Cursor, Time Zoom (Zoom In/Out, 10:1, 1:1, 1s, 10s, 1m, 10m, 1h), Go To (Start, Finish, Beginning, End), और Vertical Amplitude Zoom जोड़े गए हैं।\n\n" +
            "4. मल्टी-टैब, ब्लैंक प्रोजेक्ट और ऑडियो मिक्सिंग:\n" +
            "• कई फ़ाइलें अलग टैब में खोलें, New Blank Project बनाएं, Select All -> Delete से पूरा ट्रैक साफ करके नया ऑडियो Paste और Mix करें।\n\n" +
            "5. ओरिजिनल फ़ाइल नाम, एक्सपोर्ट सेटिंग्स और इफ़ेक्ट Presets:\n" +
            "• सेव करते समय मूल फ़ाइल नाम सुरक्षित रहता है, Export Format व Settings सेव होती हैं, और इफ़ेक्ट्स में Presets व Reset बटन मिलते हैं।\n\n" +
            "6. हिंदी-अंग्रेज़ी हेल्प गाइड और वेलकम स्क्रीन:\n" +
            "• Help में पूरी गाइड, पहली बार ऐप खोलने पर Welcome डायलॉग (Don't Show Again और OK बटन के साथ), तथा Batch Processing क्रैश फ़िक्स शामिल किए गए हैं।\n\n" +
            "7. दो फ़ाइलें / मल्टी-टैब इम्पोर्ट और प्लेबैक बग फ़िक्स:\n" +
            "• दो या अधिक ऑडियो फ़ाइलें खोलने या टैब बदलने पर प्लेबैक न होने की समस्या ठीक कर दी गई है। साथ ही 8/16/24/32-बिट और Float WAV डायरेक्ट डिकोडिंग, अंत से ऑटो-रीस्टार्ट और एक साथ कई फ़ाइलें खोलने का बटन जोड़ा गया है।\n\n" +
            "8. Help मेनू में सीधा What's New विकल्प:\n" +
            "• शीर्ष मेनू बार में नया Help मेनू जोड़ा गया है, जहाँ से आप सीधे What's New डायलॉग खोलकर सभी हालिया बदलाव और सुधार देख सकते हैं।\n\n" +
            "9. Version 1.1 — Auto Gain लेवलिंग और Voice Over म्यूज़िक प्रीसेट्स:\n" +
            "• ऑडियो के उतार-चढ़ाव को एक समान स्पष्ट लेवल पर लाने के लिए Auto Gain फ़ीचर जोड़ा गया है।\n" +
            "• Tools -> Voice Over में Clipboard के साथ अलग से Add Audio / Music File चुनने का बटन, Auto-Ducking ऑन/ऑफ़ टॉगल और प्रीसेट्स (Tutorial, Podcast, Vlog / YouTube, Story / Calm, Steady Music) जोड़े गए हैं।\n\n" +
            "10. Version 1.1 — Trim → Save → Export बग फ़िक्स और साफ नेविगेशन:\n" +
            "• Trim & Cut में Start/End मार्कर सेट करके Save दबाने पर ट्रिम किया हुआ ऑडियो सीधे प्रोजेक्ट में सेव होकर सही अवधि के साथ एक्सपोर्ट होता है।\n" +
            "• About सेक्शन को केवल Settings के अंदर रखा गया है और बाहर के अतिरिक्त About व Open Accessibility Settings बटन हटा दिए गए हैं।\n\n" +
            "11. Version 1.2 — Noise Reduction में Isolate Spoken Audio और साफ़ मेनू:\n" +
            "• अब Effect -> Noise Reduction के अंदर ही Adaptive Spectral Subtraction और 50/60 Hz Hum सप्रेशन इंजन जोड़ा गया है।\n" +
            "• इसमें Light, Medium और Strong फ़िल्टरिंग प्रीसेट्स मिलते हैं जो बैकग्राउंड शोर हटाकर आवाज़ की स्पष्टता को बिल्कुल साफ़ बनाए रखते हैं।\n" +
            "• अब User Guide और What's New दोनों बटन एक ही जगह — Settings -> About Accessible Studio के अंदर उपलब्ध हैं।\n\n" +
            "12. Vocal Extractor और Karaoke Maker फ़ीचर:\n" +
            "• अब Project Hub और Tools -> Vocal Extractor & Karaoke Maker से किसी भी गाने से केवल सिंगिंग वोकल्स (Vocals Only) अलग कर सकते हैं या लीड आवाज़ हटाकर Karaoke (Instrumental) बना सकते हैं।\n" +
            "• स्टीरियो गानों के लिए Stereo Mid/Side + STFT इंजन (बेस सुरक्षित रखते हुए), Gentle/Balanced/Deep प्रीसेट्स, स्टेम प्रिव्यू और अलग-अलग फ़ाइल सेव करने की सुविधा जोड़ी गई है।"
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
                        text = "Accessible Studio v1.2 • Recent Features & Bug Fixes",
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
                            text = "Recent Updates & Fixes (English)",
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
                            text = "ताज़ा बदलाव और सुधार (हिंदी)",
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
