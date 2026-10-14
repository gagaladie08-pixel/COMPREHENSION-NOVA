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
}
