package com.novastats.app.data.repository

import com.novastats.app.data.db.NovaDatabase
import com.novastats.app.data.db.dao.DayCount
import com.novastats.app.data.db.dao.PeriodSummary
import com.novastats.app.domain.Chart
import com.novastats.app.domain.Period
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Une ligne d'un classement annuel, prête à afficher. */
data class YearEndRow(
    val id: Long,
    val name: String,
    val subtitle: String?,
    val imageUrl: String?,
    val plays: Int,
    val durationMs: Long,
    /** Titres distincts (artistes) ou 0. */
    val extra: Int = 0
)

/** Tout ce qu'affiche l'écran 🏆 Year-End Charts pour une année civile. */
data class YearEndData(
    val year: Int,
    val summary: PeriodSummary,
    /** Année précédente (pour les écarts) — synthèse vide si pas de données. */
    val previous: PeriodSummary,
    val tracks: List<YearEndRow>,
    val artists: List<YearEndRow>,
    val albums: List<YearEndRow>,
    val bestDay: DayCount?,
    /** Artistes découverts cette année-là (première écoute). */
    val newArtists: Int,
    /** L'année est-elle en cours ? (le Billboard ne publie que des périodes closes) */
    val inProgress: Boolean
) {
    val topTrack: YearEndRow? get() = tracks.firstOrNull()
    val topArtist: YearEndRow? get() = artists.firstOrNull()
    val topAlbum: YearEndRow? get() = albums.firstOrNull()
    val playsDelta: Int get() = summary.playCount - previous.playCount
    val playsDeltaPct: Int get() =
        if (previous.playCount > 0) ((summary.playCount - previous.playCount) * 100 / previous.playCount) else 0
    val avgPerDay: Float get() = if (summary.activeDays > 0) summary.playCount.toFloat() / summary.activeDays else 0f
}

/**
 * Classements de fin d'année — **exactement les mêmes règles que le Billboard** :
 *
 *  - **Titres** : regroupés par `root_id` → un remix compte pour son original (Nova Hot 100, limite 100).
 *  - **Artistes** : chaque artiste crédité via `track_artists` reçoit l'écoute (duos inclus),
 *    artistes fusionnés exclus (Nova Artist 50, limite 50).
 *  - **Albums** : compilations exclues (`is_compilation = 0`), albums partagés affichés « Artistes variés »
 *    (Nova 75 Albums, limite 75).
 *  - Même ordre : écoutes puis durée. Mêmes limites que le Billboard en période YEARLY.
 *
 * Seule différence assumée : l'année **en cours** est affichée (marquée « en cours »), alors que le
 * Billboard ne publie que des périodes closes.
 */
class YearEndRepository(private val db: NovaDatabase) {

    private val yearFmt = SimpleDateFormat("yyyy", Locale.FRANCE)

    /** Années disponibles (de la plus récente à la plus ancienne). */
    suspend fun years(): List<Int> =
        db.dailyPlayDao().allDates().mapNotNull { it.take(4).toIntOrNull() }.distinct().sortedDescending()

    suspend fun load(year: Int): YearEndData {
        val from = "$year-01-01"
        val to = "$year-12-31"
        val currentYear = yearFmt.format(Date(System.currentTimeMillis())).toIntOrNull() ?: year

        // 🏆 Les trois classements, calculés par les requêtes du Billboard
        val rankedTracks = runCatching { db.billboardDao().rankTracks(from, to, Chart.HOT_100.limit(Period.YEARLY)) }.getOrDefault(emptyList())
        val rankedArtists = runCatching { db.billboardDao().rankArtists(from, to, Chart.ARTIST_50.limit(Period.YEARLY)) }.getOrDefault(emptyList())
        val rankedAlbums = runCatching { db.billboardDao().rankAlbums(from, to, Chart.ALBUMS_75.limit(Period.YEARLY)) }.getOrDefault(emptyList())

        val trackMap = runCatching { db.trackDao().byIds(rankedTracks.map { it.entityId }) }.getOrDefault(emptyList()).associateBy { it.trackId }
        val artistMap = runCatching { db.artistDao().byIds(rankedArtists.map { it.entityId }) }.getOrDefault(emptyList()).associateBy { it.artistId }
        val albumMap = runCatching { db.albumDao().byIds(rankedAlbums.map { it.entityId }) }.getOrDefault(emptyList()).associateBy { it.albumId }
        val artistNames = artistMap.values.associate { it.artistId to it.name }

        val tracks = rankedTracks.mapNotNull { r ->
            val t = trackMap[r.entityId] ?: return@mapNotNull null
            YearEndRow(r.entityId, t.title, artistNames[t.artistId], t.coverUrl, r.plays, r.durationMs)
        }
        val artists = rankedArtists.mapNotNull { r ->
            val a = artistMap[r.entityId] ?: return@mapNotNull null
            YearEndRow(r.entityId, a.name, plural(r.distinctTracks), a.photoUrl, r.plays, r.durationMs, r.distinctTracks)
        }
        val albums = rankedAlbums.mapNotNull { r ->
            val al = albumMap[r.entityId] ?: return@mapNotNull null
            // §12 : album partagé (artist_id NULL) → « Artistes variés »
            val sub = al.artistId?.let { artistNames[it] } ?: "Artistes variés"
            YearEndRow(r.entityId, al.title, sub, al.coverUrl, r.plays, r.durationMs, r.distinctTracks)
        }

        val summary = runCatching { db.dailyPlayDao().yearSummary(from, to) }.getOrDefault(EMPTY)
        val previous = runCatching { db.dailyPlayDao().yearSummary("${year - 1}-01-01", "${year - 1}-12-31") }.getOrDefault(EMPTY)
        val bestDay = runCatching { db.dailyPlayDao().bestDayBetween(from, to) }.getOrNull()

        val newArtists = runCatching {
            db.artistDao().allByPlays().first().count { a ->
                val first = a.firstPlayedAt ?: return@count false
                yearFmt.format(Date(first)).toIntOrNull() == year
            }
        }.getOrDefault(0)

        return YearEndData(year, summary, previous, tracks, artists, albums, bestDay, newArtists, inProgress = year >= currentYear)
    }

    private fun plural(n: Int): String = if (n > 1) "$n titres" else if (n == 1) "1 titre" else ""

    private companion object {
        val EMPTY = PeriodSummary(0, 0, 0, 0, 0, 0)
    }
}
