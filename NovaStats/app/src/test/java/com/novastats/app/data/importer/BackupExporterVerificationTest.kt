package com.novastats.app.data.importer

import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BackupExporterVerificationTest {
    @Test
    fun verifiesReadableNovaExportAndRejectsCorruptFile() {
        val file = File.createTempFile("nova-editor-backup", ".json")
        try {
            val payload = NovaExport(
                export_date = "2026-10-14T00:00:00+0000",
                device = "test device",
                app_version = "test",
                songs = emptyList(),
                plays = emptyList()
            )
            file.writeText(Json { encodeDefaults = true }.encodeToString(NovaExport.serializer(), payload))
            assertTrue(BackupExporter.verify(file))

            file.writeText("{ definitely not valid JSON")
            assertFalse(BackupExporter.verify(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun backupVerificationCanRequireEveryTrashedPlayAndParserKeepsTrashSeparate() {
        val file = File.createTempFile("nova-editor-trash-backup", ".json")
        try {
            val payload = NovaExport(
                export_date = "2026-10-14T00:00:00+0000",
                device = "test device",
                app_version = "test",
                songs = listOf(LegacySong(id = 7, title = "Track", artistNames = "Artist")),
                plays = emptyList(),
                trashed_plays = listOf(ExportTrashedPlay(songId = 7, playedAt = 1234L, listenedDuration = 5000L))
            )
            file.writeText(Json { encodeDefaults = true }.encodeToString(NovaExport.serializer(), payload))

            assertTrue(BackupExporter.verify(file, expectedTrashedPlays = 1))
            assertFalse(BackupExporter.verify(file, expectedTrashedPlays = 2))
            val parsed = LegacyBackupImporter.parse(file.inputStream())
            assertTrue(parsed.plays.isEmpty())
            assertTrue(parsed.trashed_plays.single().songId == 7L)
        } finally {
            file.delete()
        }
    }
}
