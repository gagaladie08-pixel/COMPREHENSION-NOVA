package com.novastats.app.data.importer

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyPlayTest {
    @Test
    fun `nova backup preserves confirmed play when current threshold has changed`() {
        val play = LegacyPlay(
            songId = 7,
            playedAt = 1_000_000,
            listenedDuration = 40_000,
            isConfirmed = true,
            validatedAt = 1_030_000
        )

        assertTrue(play.isConfirmedAtImport(thresholdSec = 60))
        assertEquals(1_030_000L, play.validatedAtForImport(thresholdSec = 60))
    }

    @Test
    fun `legacy backup without status still uses the selected threshold`() {
        val play = LegacyPlay(songId = 7, playedAt = 1_000_000, listenedDuration = 40_000)

        assertTrue(play.isConfirmedAtImport(thresholdSec = 30))
        assertFalse(play.isConfirmedAtImport(thresholdSec = 60))
        assertNull(play.validatedAtForImport(thresholdSec = 60))
    }

    @Test
    fun `fallback validation time cannot be later than the end of the listen`() {
        val play = LegacyPlay(
            songId = 7,
            playedAt = 1_000_000,
            listenedDuration = 10_000,
            isConfirmed = true
        )

        assertEquals(1_010_000L, play.validatedAtForImport(thresholdSec = 60))
    }

    @Test
    fun `legacy json defaults new version and confirmation fields`() {
        val legacy = """{"version":1,"songs":[{"id":1,"title":"Song"}],"plays":[{"songId":1,"playedAt":1000,"listenedDuration":40000}]}"""
        val parsed = LegacyBackupImporter.parse(legacy.byteInputStream())

        assertNull(parsed.songs.single().originalSongId)
        assertNull(parsed.plays.single().isConfirmed)
        assertNull(parsed.plays.single().validatedAt)
    }

    @Test
    fun `new version and confirmation fields survive backup json round trip`() {
        val backup = LegacyBackup(
            version = 2,
            songs = listOf(
                LegacySong(id = 1, title = "Original"),
                LegacySong(id = 2, title = "Version", originalSongId = 1)
            ),
            plays = listOf(
                LegacyPlay(songId = 2, playedAt = 1_000_000, listenedDuration = 40_000, isConfirmed = true, validatedAt = 1_030_000)
            )
        )
        val json = Json { encodeDefaults = true }
        val restored = json.decodeFromString(LegacyBackup.serializer(), json.encodeToString(LegacyBackup.serializer(), backup))

        assertEquals(1L, restored.songs[1].originalSongId)
        assertTrue(restored.plays.single().isConfirmedAtImport(thresholdSec = 60))
        assertEquals(1_030_000L, restored.plays.single().validatedAt)
    }
}
