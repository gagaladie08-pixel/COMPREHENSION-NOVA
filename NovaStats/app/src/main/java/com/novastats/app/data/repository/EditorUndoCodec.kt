package com.novastats.app.data.repository

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Encodage versionné et délimité pour les instantanés compacts stockés dans `edit_history.extra_data`.
 * Les champs sont URL-encodés afin que noms, URL et raisons contenant `;`, `,` ou `=` ne cassent pas le format.
 */
internal object EditorUndoCodec {
    private const val VERSION = "v1"

    fun encodeRows(rows: List<List<String?>>): String = buildString {
        append(VERSION)
        rows.forEach { row ->
            append('|')
            append(row.joinToString(",") { value ->
                if (value == null) "N" else "V" + URLEncoder.encode(value, "UTF-8")
            })
        }
    }

    fun decodeRows(encoded: String?): List<List<String?>> {
        if (encoded.isNullOrBlank()) return emptyList()
        val parts = encoded.split('|')
        if (parts.firstOrNull() != VERSION) return emptyList()
        return parts.drop(1).filter(String::isNotEmpty).map { row ->
            row.split(',').map { field ->
                when {
                    field == "N" -> null
                    field.startsWith('V') -> runCatching { URLDecoder.decode(field.drop(1), "UTF-8") }.getOrNull()
                    else -> null
                }
            }
        }
    }
}
