package com.novastats.app.data.repository

import com.novastats.app.data.db.entity.EditHistoryEntity
import com.novastats.app.data.db.entity.ScrobbleStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataEditorUndoPolicyTest {
    @Test
    fun onlyActionsWithRestorableStateAreMarkedUndoable() {
        assertTrue(DataEditorManager.canUndo(entry("RENAME")))
        assertFalse(DataEditorManager.canUndo(entry("RENAME", before = null, entityType = "TRACK")))
        assertTrue(DataEditorManager.canUndo(entry("ALBUM_CHANGE", before = null, entityType = "TRACK")))
        assertFalse(DataEditorManager.canUndo(entry("ARTIST_CHANGE", before = "Featuring Artist", entityType = "TRACK")))
        assertTrue(DataEditorManager.canUndo(entry("DELETE_PLAY", ScrobbleStatus.TRASHED)))
        assertTrue(DataEditorManager.canUndo(entry("TRASH_RESTORE", ScrobbleStatus.CONFIRMED)))
        val links = EditorUndoCodec.encodeRows(emptyList())
        val rows = EditorUndoCodec.encodeRows(listOf(listOf("10", "20", links)))
        assertTrue(DataEditorManager.canUndo(entry("BATCH_ARTIST_CHANGE", before = "1 titres", entityType = "TRACK_BATCH", extraData = "batchRows=$rows")))
        assertTrue(DataEditorManager.canUndo(entry("BATCH_ALBUM_CHANGE", before = "1 titres", entityType = "TRACK_BATCH", extraData = "batchRows=$rows")))
        assertFalse(DataEditorManager.canUndo(entry("BATCH_ARTIST_CHANGE", before = "2 titres", entityType = "TRACK_BATCH", extraData = "batchRows=$rows")))
        assertFalse(DataEditorManager.canUndo(entry("BATCH_ARTIST_CHANGE", entityType = "TRACK_BATCH")))

        assertFalse(DataEditorManager.canUndo(entry("MERGE")))
        assertFalse(DataEditorManager.canUndo(entry("TRASH_PURGE")))
        assertFalse(DataEditorManager.canUndo(entry("DELETE_PLAY", null)))
        assertFalse(DataEditorManager.canUndo(entry("REVIEWED")))
    }

    @Test
    fun irreversibleHistoryEntriesExplainWhyUndoIsUnavailable() {
        assertTrue(DataEditorManager.undoUnavailableReason(entry("MERGE")).contains("pas annulable"))
        assertTrue(DataEditorManager.undoUnavailableReason(entry("TRASH_PURGE")).contains("définitive"))
    }

    private fun entry(
        type: String,
        after: String? = null,
        before: String? = "42",
        entityType: String = "SCROBBLE",
        extraData: String? = null
    ) = EditHistoryEntity(
        id = 1L, type = type, entityType = entityType, entityId = 42L,
        before = before, after = after, extraData = extraData
    )
}
