package com.novastats.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorUndoCodecTest {
    @Test
    fun roundTripPreservesNullsUnicodeAndDelimiters() {
        val rows = listOf(
            listOf("TITLE", "Titre; avec=virgules, | plus + accents é🎵", null, ""),
            listOf("ALBUM", "Album % déjà encodé", "123", "0")
        )

        assertEquals(rows, EditorUndoCodec.decodeRows(EditorUndoCodec.encodeRows(rows)))
    }

    @Test
    fun emptyAndUnknownPayloadsAreIgnored() {
        assertEquals(emptyList<List<String?>>(), EditorUndoCodec.decodeRows(null))
        assertEquals(emptyList<List<String?>>(), EditorUndoCodec.decodeRows(""))
        assertEquals(emptyList<List<String?>>(), EditorUndoCodec.decodeRows("future-format"))
    }
}
