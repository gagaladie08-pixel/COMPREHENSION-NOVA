package com.novastats.app.domain

/*
 * Règles métier de NovaStats — Kotlin pur, sans dépendance Android, testable en JVM.
 * Source : cahier des charges (DEBUT) — Certifications, Panthéon, Détection.
 */

/* =========================== CERTIFICATIONS =========================== */

enum class CertLevel(val emoji: String, val label: String, val dbName: String) {
    SILVER("🥉", "Argent", "SILVER"),
    GOLD("🥈", "Or", "GOLD"),
    PLATINUM("🥇", "Platine", "PLATINUM"),
    DIAMOND("💎", "Diamant", "DIAMOND")
}

/** Une certification = niveau + multiplicateur (le multiplicateur ne concerne que Diamant). */
data class Certification(val level: CertLevel, val multiplier: Int = 1) : Comparable<Certification> {
    val rank: Int get() = level.ordinal * 1000 + multiplier
    override fun compareTo(other: Certification) = rank.compareTo(other.rank)
    fun label(): String = if (level == CertLevel.DIAMOND && multiplier > 1) "${multiplier}x ${level.emoji} ${level.label}" else "${level.emoji} ${level.label}"
}

object CertificationRules {
    /** Seuils Chansons : Argent 25 / Or 50 / Platine 100 / Diamant 350 (+350 par multiplicateur) */
    val TRACK = Thresholds(silver = 25, gold = 50, platinum = 100, diamond = 350)

    /** Seuils Albums : Argent 50 / Or 100 / Platine 200 / Diamant 700 (+700 par multiplicateur) */
    val ALBUM = Thresholds(silver = 50, gold = 100, platinum = 200, diamond = 700)

    data class Thresholds(val silver: Int, val gold: Int, val platinum: Int, val diamond: Int) {

        /** Écoutes nécessaires pour une certification donnée. */
        fun required(cert: Certification): Int = when (cert.level) {
            CertLevel.SILVER -> silver
            CertLevel.GOLD -> gold
            CertLevel.PLATINUM -> platinum
            CertLevel.DIAMOND -> diamond * cert.multiplier
        }

        /** Certification actuelle pour un nombre d'écoutes, ou null si en dessous d'Argent. */
        fun current(playCount: Int): Certification? = when {
            playCount >= diamond -> Certification(CertLevel.DIAMOND, playCount / diamond)
            playCount >= platinum -> Certification(CertLevel.PLATINUM)
            playCount >= gold -> Certification(CertLevel.GOLD)
            playCount >= silver -> Certification(CertLevel.SILVER)
            else -> null
        }

        /** Prochain palier à atteindre (Radar) — le Diamant continue à l'infini. */
        fun next(playCount: Int): Certification = when (val c = current(playCount)) {
            null -> Certification(CertLevel.SILVER)
            else -> when (c.level) {
                CertLevel.SILVER -> Certification(CertLevel.GOLD)
                CertLevel.GOLD -> Certification(CertLevel.PLATINUM)
                CertLevel.PLATINUM -> Certification(CertLevel.DIAMOND)
                CertLevel.DIAMOND -> Certification(CertLevel.DIAMOND, c.multiplier + 1)
            }
        }

        fun remainingToNext(playCount: Int): Int = required(next(playCount)) - playCount

        /** Tous les paliers atteints, dans l'ordre (sert aux dates rétroactives). */
        fun allReached(playCount: Int): List<Certification> {
            val out = mutableListOf<Certification>()
            if (playCount >= silver) out += Certification(CertLevel.SILVER)
            if (playCount >= gold) out += Certification(CertLevel.GOLD)
            if (playCount >= platinum) out += Certification(CertLevel.PLATINUM)
            var m = 1
            while (playCount >= diamond * m) { out += Certification(CertLevel.DIAMOND, m); m++ }
            return out
        }
    }
}

/* =========================== PANTHÉON =========================== */

enum class PantheonStatus(val emoji: String, val label: String, val dbName: String, val playsThreshold: Int, val colorHex: String) {
    STAR("⭐", "Star", "STAR", 425, "#4A90E2"),
    SUPERSTAR("🌟", "Superstar", "SUPERSTAR", 650, "#9B59B6"),
    MEGASTAR("👑", "Megastar", "MEGASTAR", 1250, "#FFD700"),
    LEGENDE("🏛️", "Légende", "LEGENDE", 3650, "#C0392B"),
    MYTHIQUE("✨", "Mythique", "MYTHIQUE", 7000, "#HOLOGRAPHIC");

    companion object {
        fun fromDb(name: String?): PantheonStatus? = entries.firstOrNull { it.dbName == name }
    }
}

/** Résumé des certifications d'un artiste, nécessaire à l'Option A. */
data class ArtistCertSummary(
    val trackCerts: List<Certification>,
    val albumCerts: List<Certification>
) {
    fun tracksAtLeast(level: CertLevel) = trackCerts.count { it.level >= level }
    fun albumsAtLeast(level: CertLevel) = albumCerts.count { it.level >= level }
    fun tracksExactly(level: CertLevel) = trackCerts.count { it.level == level }
    fun albumsExactly(level: CertLevel) = albumCerts.count { it.level == level }
}

data class PantheonEvaluation(val status: PantheonStatus?, val viaPlays: Boolean)

object PantheonRules {

    /**
     * Statut le plus élevé atteint.
     * Option A : certifications (5 chansons ≥ niveau + 2 albums ≥ niveau) — OU — Option B : écoutes totales.
     * Mythique : Légende + 2 titres ET 2 albums à CHAQUE niveau — OU — 7000 écoutes.
     */
    fun evaluate(totalPlays: Int, certs: ArtistCertSummary): PantheonEvaluation {
        val optionA = highestViaCertifications(certs)
        val optionB = highestViaPlays(totalPlays)
        return when {
            optionA == null && optionB == null -> PantheonEvaluation(null, false)
            optionA == null -> PantheonEvaluation(optionB, true)
            optionB == null -> PantheonEvaluation(optionA, false)
            optionB.ordinal > optionA.ordinal -> PantheonEvaluation(optionB, true)
            else -> PantheonEvaluation(optionA, false)
        }
    }

    fun highestViaPlays(totalPlays: Int): PantheonStatus? =
        PantheonStatus.entries.lastOrNull { totalPlays >= it.playsThreshold }

    fun highestViaCertifications(c: ArtistCertSummary): PantheonStatus? {
        fun meets(level: CertLevel) = c.tracksAtLeast(level) >= 5 && c.albumsAtLeast(level) >= 2
        val legende = meets(CertLevel.DIAMOND)
        val mythique = legende && CertLevel.entries.all { c.tracksExactly(it) >= 2 && c.albumsExactly(it) >= 2 }
        return when {
            mythique -> PantheonStatus.MYTHIQUE
            legende -> PantheonStatus.LEGENDE
            meets(CertLevel.PLATINUM) -> PantheonStatus.MEGASTAR
            meets(CertLevel.GOLD) -> PantheonStatus.SUPERSTAR
            meets(CertLevel.SILVER) -> PantheonStatus.STAR
            else -> null
        }
    }

    /** Statut suivant (pour la barre de progression), null si Mythique. */
    fun next(current: PantheonStatus?): PantheonStatus? = when (current) {
        null -> PantheonStatus.STAR
        PantheonStatus.MYTHIQUE -> null
        else -> PantheonStatus.entries[current.ordinal + 1]
    }
}

/* =========================== DÉTECTION — Normalisation =========================== */

object TitleNormalizer {

    /** Mots-clés de versions fusionnées automatiquement et invisiblement avec le titre original. */
    val VERSION_KEYWORDS = listOf(
        "remix", "acoustic", "live", "instrumental", "extended", "extended mix", "edit", "radio edit",
        "remaster", "remastered", "dj mix", "dj remix", "club mix", "club edit", "edm remix", "deluxe",
        "bonus track", "slowed", "sped up", "reverb", "slowed + reverb", "slowed & reverb", "bass boosted", "lofi", "lo-fi",
        "official video", "official audio", "official music video", "lyrics", "lyric video", "visualizer",
        "audio", "video", "mv", "m/v", "hd", "4k"
    )

    /** Éditions d'album fusionnées avec l'album principal (Deluxe, Expanded, Japan/UK Edition, Platinum, International…). */
    val ALBUM_EDITION_KEYWORDS = listOf(
        "deluxe", "expanded", "edition", "édition", "version", "remaster", "remastered", "anniversary", "bonus",
        "international", "japan", "japanese", "korean", "uk", "us", "eu", "platinum", "special", "complete", "explicit",
        "clean", "standard", "tour", "repackage", "reissue", "extended", "super deluxe", "digital", "limited", "collector"
    )

    /**
     * Normalise un titre d'album : "Born Pink (Japan Edition)" / "Born Pink - Deluxe" → "Born Pink".
     * Les suffixes qui NE sont PAS des marqueurs d'édition ("Vol. 2", "Part 1") sont conservés.
     */
    fun normalizeAlbumTitle(raw: String): String {
        var title = raw.trim()
        title = bracketRegex.replace(title) { m ->
            val inner = m.groupValues[1].lowercase()
            if (ALBUM_EDITION_KEYWORDS.any { inner.contains(it) }) "" else m.value
        }
        dashSuffixRegex.find(title)?.let { m ->
            val suffix = m.groupValues[1].lowercase()
            if (ALBUM_EDITION_KEYWORDS.any { suffix.contains(it) }) title = title.removeSuffix(m.value)
        }
        // Suffixe sans séparateur : "Born Pink Deluxe Edition", "Thriller 25th Anniversary"
        val words = title.split(' ').toMutableList()
        while (words.size > 1) {
            val last = words.last().lowercase().trim(',', '.')
            if (last in ALBUM_EDITION_KEYWORDS || Regex("""\d+(th|st|nd|rd)""").matches(last)) words.removeAt(words.lastIndex) else break
        }
        title = spaceRegex.replace(words.joinToString(" "), " ").trim()
        return title.ifBlank { raw.trim() }
    }

    private val junkWords = listOf("™", "®", "official", "Official")

    private val bracketRegex = Regex("""\s*[\(\[\{]([^\)\]\}]*)[\)\]\}]""")
    private val dashSuffixRegex = Regex("""\s+[-–—]\s+(.+)$""")
    /**
     * Featuring : "(feat. X)", "[ft. X]", "(with X)" entre parenthèses/crochets, ou "feat./ft./featuring X" nu
     * jusqu'à la fin du titre / prochaine parenthèse. "with" nu n'est pas traité ("Stay With Me" reste un titre).
     */
    private val featRegex = Regex("""(?i)(?:\s*[\(\[]\s*(?:feat\.?|ft\.?|featuring|with)\s+([^\)\]]+)[\)\]]|\s+(?:feat\.?|ft\.?|featuring)\s+(.+?)(?=\s*[\(\[]|\s+[-–—]\s|$))""")
    private val spaceRegex = Regex("""\s+""")

    /**
     * Nettoie un titre brut : retire les suffixes de version connus, les "feat.", les mots indésirables.
     * Conserve les emojis. Ne touche pas au sens si rien n'est reconnu.
     * Retourne le titre nettoyé + la liste des artistes "featured" extraits.
     */
    fun normalizeTitle(raw: String): NormalizedTitle {
        var title = raw.trim()
        val featured = mutableListOf<String>()
        var isVersion = false

        // Toutes les occurrences : "(feat. A) [ft. B]", "with C"…
        featRegex.findAll(title).toList().forEach { m ->
            featured += splitArtists(m.groupValues[1].ifEmpty { m.groupValues[2] })
        }
        title = featRegex.replace(title, "")

        // Parenthèses / crochets contenant un mot-clé de version → supprimés
        title = bracketRegex.replace(title) { m ->
            val inner = m.groupValues[1].lowercase()
            if (VERSION_KEYWORDS.any { inner.contains(it) }) { isVersion = true; "" } else m.value
        }
        // Suffixe " - Remastered 2011", " - Live" …
        dashSuffixRegex.find(title)?.let { m ->
            val suffix = m.groupValues[1].lowercase()
            if (VERSION_KEYWORDS.any { suffix.contains(it) }) { isVersion = true; title = title.removeSuffix(m.value) }
        }
        junkWords.forEach { title = title.replace(it, "") }
        title = spaceRegex.replace(title, " ").trim()
        return NormalizedTitle(title = title.ifBlank { raw.trim() }, featuredArtists = featured.distinctBy { normalizeKey(it) }, isVersion = isVersion)
    }

    /** Sépare "A, B & C feat. D" en liste d'artistes. */
    /** Compilations ("Various Artists", "NOW That's What I Call Music"…) : jamais de crédit album. */
    private val compilationRegex = Regex("""(?i)(now that'?s what i call|nrj music awards|nrj hits|hits? 20\d\d|top hits|compilation|\bvol(ume)?\.? \d+\b.*(hits|party|dance))""")

    /** Vraies compilations (jamais de crédit album). « Various Artists » seul ne suffit plus : c'est un album partagé. */
    fun isCompilation(albumTitle: String?, albumArtist: String?): Boolean {
        if (albumArtist != null && compilationRegex.containsMatchIn(albumArtist)) return true
        return albumTitle != null && compilationRegex.containsMatchIn(albumTitle)
    }

    /** Étiquette d'affichage d'un album partagé — PAS un artiste. */
    const val SHARED_ALBUM_LABEL = "Artistes variés"
    private val sharedAlbumRegex = Regex("""(soundtrack|motion picture|music from|the album|world cup|bande originale)""")
    private val variousArtistsRegex = Regex("""(?i)^(various artists|artistes divers|multi-interpr[eè]tes?|va)$""")

    /**
     * Album multi-artistes (BO, album d'événement) : un seul album partagé, sans artiste propriétaire, auquel tous les
     * titres se rattachent quel que soit leur artiste principal. Déclencheurs : mots-clés du titre (insensible à la casse
     * et aux accents) ou artiste d'album « Various Artists ». Le choix manuel de l'éditeur (correction ALBUM_SHARED) prime.
     */
    fun isSharedAlbum(albumTitle: String?, albumArtist: String? = null): Boolean {
        if (albumArtist != null && variousArtistsRegex.containsMatchIn(albumArtist.trim())) return true
        return albumTitle != null && sharedAlbumRegex.containsMatchIn(normalizeKey(albumTitle))
    }

    /** Décision finale : correction manuelle (« 1 » / « 0 ») sinon détection automatique. */
    fun sharedAlbumDecision(override: String?, albumTitle: String?, albumArtist: String?): Boolean = when (override) {
        "1" -> true
        "0" -> false
        else -> isSharedAlbum(albumTitle, albumArtist)
    }

    /**
     * 🔒 Noms d'artistes à ne jamais découper (« HUNTR/X », « AC/DC », « Tyler, The Creator »…), par clé normalisée.
     * Alimenté depuis la table `artist_exceptions` au démarrage et après chaque édition ([setNeverSplit]).
     */
    @Volatile private var neverSplit: Map<String, String> = DEFAULT_NEVER_SPLIT.associateBy { normalizeKey(it) }
    val DEFAULT_NEVER_SPLIT: List<String> get() = listOf("HUNTR/X", "AC/DC", "Tyler, The Creator", "Simon & Garfunkel", "Earth, Wind & Fire", "Florence + the Machine", "Of Monsters and Men")
    fun setNeverSplit(names: Collection<String>) { neverSplit = names.filter { it.isNotBlank() }.associateBy { normalizeKey(it) } }
    fun neverSplitNames(): Collection<String> = neverSplit.values
    fun isNeverSplit(name: String): Boolean = normalizeKey(name) in neverSplit

    private val splitRegex = Regex("""(?i)\s*(,|&|;|/|\+| x | feat\.? | ft\.? | featuring | with | and )\s*""")

    fun splitArtists(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return emptyList()
        // 1) Le champ entier est un nom protégé → un seul artiste, conservé tel quel
        neverSplit[normalizeKey(trimmed)]?.let { return listOf(trimmed) }
        // 2) Un nom protégé apparaît dans une liste (« HUNTR/X feat. Future ») → remplacé par un jeton avant le découpage
        var work = trimmed
        val tokens = ArrayList<String>()
        for (name in neverSplit.values) {
            val rx = Regex("(?i)(?<![\\p{L}\\p{N}])" + Regex.escape(name) + "(?![\\p{L}\\p{N}])")
            if (rx.containsMatchIn(work)) {
                work = rx.replace(work) { m -> tokens.add(m.value); "\u0001${tokens.size - 1}\u0001" }
            }
        }
        return work.split(splitRegex)
            .map { it.trim().trim('(', ')', '[', ']') }
            .filter { it.isNotBlank() }
            .map { part -> Regex("\u0001(\\d+)\u0001").replace(part) { m -> tokens[m.groupValues[1].toInt()] }.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { normalizeKey(it) }
    }

    /** Clé de comparaison : minuscules, sans accents, sans ponctuation, espaces réduits. */
    fun normalizeKey(s: String): String {
        val noAccents = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
        return noAccents.lowercase()
            .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            .replace(spaceRegex, " ")
            .trim()
    }

    /** Distance de Levenshtein (stratégie #4 — tolérance aux fautes). */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            cur.copyInto(prev)
        }
        return prev[b.length]
    }
}

/**
 * @property title           titre nettoyé (versions fusionnées, feat. extraits)
 * @property featuredArtists artistes extraits du titre ("feat.", "ft.", "with"…)
 * @property isVersion       le titre brut portait un marqueur de version (remix, edit, live…)
 */
data class NormalizedTitle(val title: String, val featuredArtists: List<String>, val isVersion: Boolean = false)

/* =========================== DÉTECTION — Validation d'écoute =========================== */

object ScrobbleRules {
    val ALLOWED_THRESHOLDS_SEC = listOf(15, 30, 60, 90)
    const val DEFAULT_THRESHOLD_SEC = 30

    /** Pause courte (< 10 min) : même écoute, timer cumulé. Pause longue (≥ 10 min) : nouvelle écoute. */
    const val LONG_PAUSE_MS = 10 * 60 * 1000L

    /** Crossfade : titre A clôturé si titre B détecté en < 10 s. */
    const val CROSSFADE_MS = 10_000L

    /** Session : gap de 15 min → nouvelle session (Nova Award "Session la plus longue"). */
    const val SESSION_GAP_MS = 15 * 60 * 1000L

    /** Titres > 10 min → notification de confirmation à la première détection (filtre podcast). */
    const val LONG_TRACK_MS = 10 * 60 * 1000L

    /** Anti-gel : si le service n'a reçu aucun tick / événement pendant plus de ce délai, le temps intermédiaire n'est PAS compté. */
    const val FREEZE_GAP_MS = 8_000L
    /** Marge au-dessus de la durée du morceau (crossfade, latence) pour le plafond du temps écouté. */
    const val DURATION_MARGIN_MS = 5_000L
    /** Marge au-dessus de l'avancée réelle de position (lecteur) pour le plafond du temps écouté. */
    const val POSITION_MARGIN_MS = 15_000L
    /** Plafond absolu d'une écoute quand le lecteur ne donne pas la durée. */
    const val MAX_LISTEN_UNKNOWN_DURATION_MS = 20 * 60 * 1000L

    /** Plafond du temps écouté : durée du morceau (+ marge) ou plafond absolu si inconnue. */
    fun capListened(listenedMs: Long, durationMs: Long?): Long =
        listenedMs.coerceAtMost(if (durationMs != null && durationMs > 0) durationMs + DURATION_MARGIN_MS else MAX_LISTEN_UNKNOWN_DURATION_MS)

    fun isValidated(listenedMs: Long, thresholdSec: Int = DEFAULT_THRESHOLD_SEC) = listenedMs >= thresholdSec * 1000L
}

/* =========================== SCORE DE CONFIANCE =========================== */

object ConfidenceScore {
    const val ACCEPT = 90      // ≥ 90 : accepté + cache 6 mois
    const val FLAG = 70        // 70-89 : accepté + flag + notif
    // < 70 : rejeté → API suivante

    data class Criteria(
        val exactTitle: Boolean = false,        // +30
        val exactArtist: Boolean = false,       // +30
        val twoApisAgree: Boolean = false,      // +20
        val durationMatches: Boolean = false,   // +10 (±5 s)
        val albumRecognized: Boolean = false,   // +10
        val titleInAlbum: Boolean = false,      // +10
        val artistOfAlbum: Boolean = false,     // +10
        val releaseDateCoherent: Boolean = false, // +5
        val genreCoherent: Boolean = false      // +5
    )

    fun compute(c: Criteria): Int = (
        (if (c.exactTitle) 30 else 0) + (if (c.exactArtist) 30 else 0) + (if (c.twoApisAgree) 20 else 0) +
            (if (c.durationMatches) 10 else 0) + (if (c.albumRecognized) 10 else 0) + (if (c.titleInAlbum) 10 else 0) +
            (if (c.artistOfAlbum) 10 else 0) + (if (c.releaseDateCoherent) 5 else 0) + (if (c.genreCoherent) 5 else 0)
        ).coerceAtMost(100)

    /** Durée de cache selon le score (stratégie #6). null = pas de cache. */
    fun cacheDurationMs(score: Int): Long? = when {
        score >= ACCEPT -> 182L * 24 * 3600 * 1000  // 6 mois
        score >= FLAG -> 30L * 24 * 3600 * 1000     // 1 mois
        else -> null
    }
}
