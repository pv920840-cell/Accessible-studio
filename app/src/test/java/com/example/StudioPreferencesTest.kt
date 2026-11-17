package com.example

import com.example.data.StudioPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class StudioPreferencesTest {

    @Test
    fun extractBaseFileName_preservesOpenedNameWithoutExtension() {
        assertEquals("My Favorite Song", StudioPreferences.extractBaseFileName("My Favorite Song.mp3"))
        assertEquals("Interview_2026.final", StudioPreferences.extractBaseFileName("Interview_2026.final.WAV"))
        assertEquals("Bhajan_Track", StudioPreferences.extractBaseFileName("/storage/emulated/0/Music/Bhajan_Track.m4a"))
        assertEquals("Podcast Clip", StudioPreferences.extractBaseFileName("Podcast Clip.flac"))
        assertEquals("NoExtensionFile", StudioPreferences.extractBaseFileName("NoExtensionFile"))
        assertEquals("", StudioPreferences.extractBaseFileName("   "))
    }

    @Test
    fun normalizeExportFormat_supportsWavMp3FlacM4a() {
        assertEquals("MP3", StudioPreferences.normalizeExportFormat("mp3"))
        assertEquals("MP3", StudioPreferences.normalizeExportFormat(" MP3 "))
        assertEquals("WAV", StudioPreferences.normalizeExportFormat("wav"))
        assertEquals("FLAC", StudioPreferences.normalizeExportFormat("FLAC"))
        assertEquals("M4A", StudioPreferences.normalizeExportFormat("m4a"))
        assertEquals("WAV", StudioPreferences.normalizeExportFormat("unknown"))
        assertEquals("WAV", StudioPreferences.normalizeExportFormat(null))
    }
}
