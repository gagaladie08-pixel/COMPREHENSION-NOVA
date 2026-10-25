package com.novastats.app.domain

/** Type connu d'une sortie candidate. Sert uniquement à ordonner les propositions, jamais à deviner une version. */
enum class AlbumReleaseKind(val label: String, val priority: Int) {
    ALBUM("Album", 0),
    EP("EP", 1),
    SINGLE("Single", 2),
    COMPILATION("Compilation", 3),
    UNKNOWN("Type inconnu", 4)
}

object AlbumReleasePolicy {
    private val audioVersionMarkers = listOf(
        "slowed and reverb", "slowed + reverb", "slowed & reverb", "slowed reverb", "radio edit", "extended mix",
        "club mix", "club edit", "edm remix", "dj remix", "dj mix", "solo version", "member version",
        "acoustic", "instrumental", "remastered", "remaster", "remix", "live", "extended", "edit",
        "slowed", "sped up", "reverb", "lo fi", "bass boosted"
    )

    /** Normalise les libellés hétérogènes renvoyés par les catalogues musicaux. */
    fun classify(rawType: String?, albumTitle: String? = null): AlbumReleaseKind {
        val value = rawType.orEmpty().lowercase()
        return when {
            value.contains("compilation") || value.contains("compile") || TitleNormalizer.isCompilation(albumTitle, null) -> AlbumReleaseKind.COMPILATION
            value == "ep" || value.contains("extended play") || value.contains("mini-album") -> AlbumReleaseKind.EP
            value.contains("single") -> AlbumReleaseKind.SINGLE
            value.contains("album") || value == "lp" || value.contains("long play") -> AlbumReleaseKind.ALBUM
            else -> AlbumReleaseKind.UNKNOWN
        }
    }

    /**
     * Compare les marqueurs de version audio explicites. Une version « live » ne peut donc pas confirmer
     * l'originale, un remix différent, ni une reprise (cover); les suffixes vidéo/lyrics sont ignorés.
     */
    fun trackVersionsCompatible(wantedRaw: String, candidateRaw: String, isRemix: Boolean = false): Boolean {
        val wantedTags = audioVersionTags(wantedRaw)
        val candidateTags = audioVersionTags(candidateRaw)
        val wantedIsCover = hasCoverMarker(wantedRaw)
        val candidateIsCover = hasCoverMarker(candidateRaw)
        if (wantedIsCover != candidateIsCover) return false
        if (isRemix && wantedTags.isEmpty()) return false
        return wantedTags == candidateTags
    }

    /** Seuls les types sûrs sont éligibles à l'automatisme; compilation et type inconnu restent soumis à confirmation. */
    fun isAutoLinkable(kind: AlbumReleaseKind): Boolean = kind in setOf(AlbumReleaseKind.ALBUM, AlbumReleaseKind.EP, AlbumReleaseKind.SINGLE)

    private val versionBrackets = Regex("""[\(\[\{]([^\)\]\}]*)[\)\]\}]""")
    private val versionDashSuffix = Regex("""\s+[-–—]\s+(.+)$""")
    private val interfaceMarkers = listOf("official music video", "official video", "official audio", "lyric video", "lyrics", "visualizer", "video", "audio", "hd", "4k", "mv")

    private fun audioVersionTags(raw: String): Set<String> {
        if (!TitleNormalizer.normalizeTitle(raw).isVersion) return emptySet()
        val segments = buildList {
            versionBrackets.findAll(raw).forEach { match ->
                val segment = match.groupValues[1]
                if (isVersionSegment(segment)) add(segment)
            }
            versionDashSuffix.find(raw)?.groupValues?.getOrNull(1)?.let { segment ->
                if (isVersionSegment(segment)) add(segment)
            }
            if (isEmpty()) {
                val key = TitleNormalizer.normalizeKey(raw)
                TitleNormalizer.VERSION_KEYWORDS.sortedByDescending { it.length }
                    .firstOrNull { key.endsWith(" ${TitleNormalizer.normalizeKey(it)}") }
                    ?.let { add(it) }
            }
        }
        return segments.flatMap { segment ->
            val key = TitleNormalizer.normalizeKey(segment)
            val cleaned = interfaceMarkers.fold(key) { value, marker ->
                value.replace(Regex("(?:^| )${Regex.escape(TitleNormalizer.normalizeKey(marker))}(?: |$)"), " ")
            }.replace(Regex("\\s+"), " ").trim()
            val found = audioVersionMarkers.filter { marker ->
                val normalized = TitleNormalizer.normalizeKey(marker)
                Regex("(?:^| )${Regex.escape(normalized)}(?: |$)").containsMatchIn(cleaned)
            }
            val mostSpecific = found.filterNot { marker ->
                val normalized = TitleNormalizer.normalizeKey(marker)
                found.any { other ->
                    val otherNormalized = TitleNormalizer.normalizeKey(other)
                    otherNormalized.length > normalized.length && otherNormalized.contains(normalized)
                }
            }
            mostSpecific.map { marker ->
                val normalized = TitleNormalizer.normalizeKey(marker)
                val tail = cleaned.substringAfter(normalized, "").trim().split(' ')
                    .filter { it.isNotBlank() && it !in setOf("version", "track") }
                (listOf(normalized) + tail).joinToString(" ")
            }
        }.toSet()
    }

    private fun isVersionSegment(segment: String): Boolean =
        TitleNormalizer.VERSION_KEYWORDS.any { segment.contains(it, ignoreCase = true) }

    private fun hasCoverMarker(raw: String): Boolean =
        Regex("(?:^| )cover(?: |$)").containsMatchIn(TitleNormalizer.normalizeKey(raw))
}
