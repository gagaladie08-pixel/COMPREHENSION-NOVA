package com.novastats.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FastDurationFormatTest {
    @Test
    fun `garde la precision milliseconde pour les records et les ecarts`() {
        assertEquals("500 ms", RecordCatalog.formatFastDuration(500L))
        assertEquals("42,900 s", RecordCatalog.formatFastDuration(42_900L))
        assertEquals("7 min 08 s", RecordCatalog.formatFastDuration(428_000L))
        assertEquals("7 min 08,500 s", RecordCatalog.formatFastDuration(428_500L))
        assertEquals("2 h 03 min 04,300 s", RecordCatalog.formatFastDuration(7_384_300L))
        assertEquals("1 j 02 h 03 min 04,500 s", RecordCatalog.formatFastDuration(93_784_500L))
    }
}
