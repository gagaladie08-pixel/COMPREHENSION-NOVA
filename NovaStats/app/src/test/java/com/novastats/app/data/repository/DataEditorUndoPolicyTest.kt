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
        assertTrue(DataEditorManager.canUndo(entry("DELETE_PLAY", ScrobbleStatus.TRASHED)))
        assertTrue(DataEditorManager.canUndo(entry("TRASH_RESTORE", ScrobbleStatus.CONFIRMED)))

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

    private fun entry(type: String, after: String? = null) = EditHistoryEntity(
        id = 1L, type = type, entityType = "SCROBBLE", entityId = 42L,
        before = "42", after = after
    )
}
