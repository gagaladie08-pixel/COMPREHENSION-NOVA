package com.novastats.app.domain

import kotlin.math.abs
import kotlin.math.max

/**
 * Sources de la cascade d'enrichissement (toutes gratuites), avec leur priorité par défaut.
 * Priorité 0 = non utilisée pour ce type ; YouTube = toujours en dernier recours (non ajustable).
 *
 * Retirées (0.8.5) : Spotify (Development Mode réservé aux comptes Premium depuis le 9 mars 2026, flux
 * Client Credentials en voie de suppression) et Google Images (Custom Search JSON API fermée aux nouveaux
 * clients, arrêt le 1er janvier 2027). Les entrées restent dans l'enum pour les anciens `cover_source`.
 */
enum class ApiSource(
    val label: String,
    val emoji: String,
    val artistPriority: Int,
    val albumPriority: Int,
    val trackPriority: Int,
    val requiresKey: Boolean
) {
    ITUNES("iTunes", "🍎", 0, 1, 1, false),
    DEEZER("Deezer", "🎶", 1, 2, 2, false),
    MUSICBRAINZ("MusicBrainz", "🌿", 0, 3, 3, false),
    LASTFM("Last.fm", "🎤", 5, 4, 4, true),
    DISCOGS("Discogs", "💿", 0, 5, 5, true),
    GENIUS("Genius", "🧠", 6, 6, 6, true),
    FANART("Fanart.tv", "🎨", 2, 0, 0, true),
    WIKIDATA("Wikidata", "🌐", 3, 0, 0, false),
    THEAUDIODB("TheAudioDB", "🎸", 4, 7, 0, false),
    YOUTUBE("YouTube", "▶️", 7, 8, 7, true),
    SPOTIFY("Spotify", "🎵", 0, 0, 0, true),
    GOOGLE("Google Images", "🔍", 0, 0, 0, true);

    val isLastResort: Boolean get() = this == YOUTUBE || this == GOOGLE

    /** Source retirée de la cascade (plus jamais interrogée). */
    val retired: Boolean get() = this == SPOTIFY || this == GOOGLE

    /** Raison affichée dans Réglages → APIs pour une source retirée. */
    val retiredReason: String? get() = when (this) {
        SPOTIFY -> "retirée · Premium obligatoire depuis mars 2026"
        GOOGLE -> "retirée · API fermée, arrêt le 01/01/2027"
        else -> null
    }
}

/** Type de donnée enrichie (colonne data_type de api_cache). */
object DataType {
    const val COVER = "COVER"
    const val PHOTO = "PHOTO"
}

/** Résultat brut renvoyé par une source, quel que soit le type d'entité. */
data class MetaCandidate(
    val source: ApiSource,
    /** Titre (piste / album) ou nom (artiste) renvoyé par la source */
    val name: String,
    val artist: String? = null,
    val album: String? = null,
    val imageUrl: String? = null,
    val durationMs: Long? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val bio: String? = null,
    val mbid: String? = null,
    val spotifyId: String? = null
)

/** Candidat évalué. */
data class ScoredCandidate(val candidate: MetaCandidate, val score: Int, val reasons: List<String>)

/**
 * Stratégies de précision (spec) : normalisation, Levenshtein, score de confiance, durée ±5 s,
 * consensus, détection d'anomalies, seuils 90 / 70.
 */
object MetadataMatching {

    const val ACCEPT = 70
    const val TRUSTED = 90
    const val DURATION_TOLERANCE_MS = 5_000L
    const val EXACT_DURATION_MS = 3_000L
    const val WRONG_DURATION_MS = 10_000L

    private val junkTitles = setOf("unknown", "track 01", "track 1", "untitled", "audio", "video", "unknown track")
    private val genericArtists = setOf("various artists", "various", "unknown artist", "unknown")

    /** Similarité 0..1 entre deux chaînes (clés normalisées + Levenshtein). */
    fun similarity(a: String, b: String): Double {
        val ka = TitleNormalizer.normalizeKey(a); val kb = TitleNormalizer.normalizeKey(b)
        if (ka.isEmpty() || kb.isEmpty()) return 0.0
        if (ka == kb) return 1.0
        // Un titre contenu dans l'autre ("SERVE" vs "SERVE (feat. X)") → très proche
        if (ka.length >= 4 && (kb.startsWith(ka) || ka.startsWith(kb))) return 0.9
        val d = TitleNormalizer.levenshtein(ka, kb)
        return 1.0 - d.toDouble() / max(ka.length, kb.length)
    }

    private fun matchPoints(sim: Double, exact: Int): Int = when {
        sim >= 0.999 -> exact
        sim >= 0.85 -> (exact * 2) / 3
        sim >= 0.7 -> exact / 3
        else -> 0
    }

    /**
     * Score d'une piste (max 100) :
     *  titre +30 · artiste +30 · durée ±3 s +20 (±5 s +10, écart > 10 s −15) · album reconnu +10 ·
     *  consensus (2+ APIs d'accord sur l'artiste ET l'album/la durée) +20
     *  anomalies : titre générique → rejet ; artiste générique −10 ; durée 0 ou > 20 min −10 ; pas d'image −15
     *  garde-fou : artiste non confirmé (similarité < 0,85) → score plafonné à 69, jamais accepté sans révision
     */
    fun scoreTrack(
        c: MetaCandidate,
        wantedTitle: String,
        wantedArtist: String,
        wantedAlbum: String?,
        wantedDurationMs: Long?,
        consensus: Boolean
    ): ScoredCandidate {
        val reasons = mutableListOf<String>()
        if (TitleNormalizer.normalizeKey(c.name) in junkTitles) return ScoredCandidate(c, 0, listOf("titre générique"))
        var score = 0
        val t = matchPoints(similarity(c.name, wantedTitle), 30); if (t > 0) reasons += "titre +$t"; score += t
        val artistSim = c.artist?.let { similarity(it, wantedArtist) } ?: 0.0
        val a = matchPoints(artistSim, 30); if (a > 0) reasons += "artiste +$a"; score += a
        if (t == 0 || a == 0) return ScoredCandidate(c, 0, reasons + "titre ou artiste non reconnu")
        if (wantedDurationMs != null && c.durationMs != null) {
            val diff = abs(wantedDurationMs - c.durationMs)
            when {
                diff <= EXACT_DURATION_MS -> { score += 20; reasons += "durée exacte +20" }
                diff <= DURATION_TOLERANCE_MS -> { score += 10; reasons += "durée +10" }
                diff > WRONG_DURATION_MS -> { score -= 15; reasons += "durée différente −15" }
            }
        }
        if (wantedAlbum != null && c.album != null && similarity(wantedAlbum, c.album) >= 0.85) { score += 10; reasons += "album +10" }
        if (consensus) { score += 20; reasons += "consensus +20" }
        score += anomalies(c, reasons)
        if (a < 20 && score >= ACCEPT) { score = ACCEPT - 1; reasons += "artiste non confirmé → max ${ACCEPT - 1}" }
        return ScoredCandidate(c, score.coerceIn(0, 100), reasons)
    }

    /** Score d'un album : titre +45 · artiste +45 · image +10 (− anomalies). */
    fun scoreAlbum(c: MetaCandidate, wantedTitle: String, wantedArtist: String, consensus: Boolean): ScoredCandidate {
        val reasons = mutableListOf<String>()
        var score = 0
        val t = matchPoints(similarity(c.name, wantedTitle), 45); score += t; if (t > 0) reasons += "titre +$t"
        val a = matchPoints(c.artist?.let { similarity(it, wantedArtist) } ?: 0.0, 45); score += a; if (a > 0) reasons += "artiste +$a"
        if (t == 0 || a == 0) return ScoredCandidate(c, 0, reasons + "titre ou artiste non reconnu")
        if (!c.imageUrl.isNullOrBlank()) { score += 10; reasons += "pochette +10" }
        if (consensus) { score += 10; reasons += "consensus +10" }
        score += anomalies(c, reasons)
        if (a < 30 && score >= ACCEPT) { score = ACCEPT - 1; reasons += "artiste non confirmé → max ${ACCEPT - 1}" }
        return ScoredCandidate(c, score.coerceIn(0, 100), reasons)
    }

    /** Score d'un artiste : nom +70 · photo +30 (− anomalies). */
    fun scoreArtist(c: MetaCandidate, wantedName: String): ScoredCandidate {
        val reasons = mutableListOf<String>()
        val n = matchPoints(similarity(c.name, wantedName), 70)
        if (n == 0) return ScoredCandidate(c, 0, listOf("nom non reconnu"))
        var score = n; reasons += "nom +$n"
        if (!c.imageUrl.isNullOrBlank()) { score += 30; reasons += "photo +30" }
        if (TitleNormalizer.normalizeKey(c.name) in genericArtists) { score -= 40; reasons += "artiste générique −40" }
        return ScoredCandidate(c, score.coerceIn(0, 100), reasons)
    }

    private fun anomalies(c: MetaCandidate, reasons: MutableList<String>): Int {
        var delta = 0
        if (c.artist != null && TitleNormalizer.normalizeKey(c.artist) in genericArtists) { delta -= 10; reasons += "artiste générique −10" }
        c.durationMs?.let { if (it <= 0 || it > 20 * 60_000L) { delta -= 10; reasons += "durée suspecte −10" } }
        if (c.imageUrl.isNullOrBlank()) { delta -= 15; reasons += "pas d'image −15" }
        else if (isSuspiciousImage(c.imageUrl)) { delta -= 30; reasons += "image générique −30" }
        return delta
    }

    /** Stratégie 14 : images placeholder connues. */
    fun isSuspiciousImage(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("2a96cbd8b46e442fc41c2b86b821562f") || // étoile grise Last.fm
            u.contains("placeholder") || u.contains("noimage") || u.contains("no-image") || u.contains("default_artist") ||
            u.contains("default_avatar") || u.contains("default_cover")
    }

    /**
     * Consensus (stratégie 10) : au moins deux sources d'accord sur **l'artiste** et sur l'album (ou la durée ±5 s ;
     * ou le titre exact pour des candidats « album » sans durée). Un même titre chez deux artistes différents
     * n'est plus un consensus. Retourne l'ensemble des candidats confirmés par une autre source.
     */
    fun consensusSet(candidates: List<MetaCandidate>): Set<MetaCandidate> {
        val agreed = mutableSetOf<MetaCandidate>()
        for (i in candidates.indices) for (j in i + 1 until candidates.size) {
            val a = candidates[i]; val b = candidates[j]
            if (a.source == b.source) continue
            val sameArtist = a.artist != null && b.artist != null && similarity(a.artist, b.artist) >= 0.85
            if (!sameArtist) continue
            val sameAlbum = a.album != null && b.album != null && similarity(a.album, b.album) >= 0.85
            val sameDuration = a.durationMs != null && b.durationMs != null && abs(a.durationMs - b.durationMs) <= DURATION_TOLERANCE_MS
            val albumCandidates = a.album == null && b.album == null && a.durationMs == null && b.durationMs == null
            val sameName = albumCandidates && similarity(a.name, b.name) >= 0.999
            if (sameAlbum || sameDuration || sameName) { agreed += a; agreed += b }
        }
        return agreed
    }

    /** Stratégie 6 : durée de cache selon le score. Retourne null si le résultat ne doit pas être mis en cache. */
    fun cacheTtlMs(score: Int): Long? = when {
        score >= TRUSTED -> 183L * 24 * 3_600_000
        score >= ACCEPT -> 30L * 24 * 3_600_000
        else -> null
    }

    /** Cache négatif : « rien trouvé » → on réessaie dans 7 jours (stratégie 15, enrichissement progressif). */
    const val NEGATIVE_TTL_MS = 7L * 24 * 3_600_000

    /**
     * Stratégie 13 : ordre effectif de la cascade. Priorité par défaut, mais une source dont le taux de succès
     * tombe sous 50 % (après ≥ 10 tentatives) recule de 3 places ; YouTube reste toujours dernier ; les sources
     * retirées ne sont jamais interrogées.
     */
    fun orderSources(
        basePriority: (ApiSource) -> Int,
        reliability: Map<ApiSource, Pair<Int, Int>>, // source → (succès, échecs)
        hasKey: (ApiSource) -> Boolean
    ): List<ApiSource> = ApiSource.entries
        .filter { !it.retired && basePriority(it) > 0 && (!it.requiresKey || hasKey(it)) }
        .sortedWith(compareBy<ApiSource> { it.isLastResort }.thenBy { s ->
            val (ok, ko) = reliability[s] ?: (0 to 0)
            val penalty = if (ok + ko >= 10 && ok * 100 / (ok + ko) < 50) 3 else 0
            basePriority(s) + penalty
        })
}
