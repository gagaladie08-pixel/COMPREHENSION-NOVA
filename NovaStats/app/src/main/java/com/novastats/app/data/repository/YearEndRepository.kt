package com.novastats.app.data.repository

import com.novastats.app.data.db.NovaDatabase
import com.novastats.app.data.db.dao.DayCount
import com.novastats.app.data.db.dao.PeriodSummary
import com.novastats.app.data.db.dao.YearEndRow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val newArtists: Int
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
 * Classements de fin d'année : le top de l'année civile, calculé depuis `daily_plays`
 * (donc exactement les mêmes règles que les charts hebdomadaires).
 */
class YearEndRepository(private val db: NovaDatabase) {

    private val yearFmt = SimpleDateFormat("yyyy", Locale.FRANCE)

    /** Années disponibles (de la plus récente à la plus ancienne). */
    suspend fun years(): List<Int> =
        db.dailyPlayDao().allDates().mapNotNull { it.take(4).toIntOrNull() }.distinct().sortedDescending()

    suspend fun load(year: Int, limit: Int = 100, artistsForNew: Boolean = true): YearEndData {
        val dao = db.dailyPlayDao()
        val from = "$year-01-01"
        val to = "$year-12-31"
        val summary = runCatching { dao.summaryBetween(from, to) }.getOrDefault(EMPTY)
        val previous = runCatching { dao.summaryBetween("${year - 1}-01-01", "${year - 1}-12-31") }.getOrDefault(EMPTY)
        val tracks = runCatching { dao.yearEndTracks(from, to, limit) }.getOrDefault(emptyList())
            .map { if (it.subtitle.isNullOrBlank()) it else it }
        val artists = runCatching { dao.yearEndArtists(from, to, limit) }.getOrDefault(emptyList())
            .map { it.copy(subtitle = it.subtitle?.takeIf { s -> s.isNotBlank() }) }
        val albums = runCatching { dao.yearEndAlbums(from, to, limit) }.getOrDefault(emptyList())
        val bestDay = runCatching { dao.bestDayBetween(from, to) }.getOrNull()

        val newArtists = if (artistsForNew) runCatching {
            db.artistDao().allByPlays().first().count { a ->
                val first = a.firstPlayedAt ?: return@count false
                yearFmt.format(Date(first)).toIntOrNull() == year
            }
        }.getOrDefault(0) else 0

        return YearEndData(year, summary, previous, tracks, artists, albums, bestDay, newArtists)
    }

    private companion object {
        val EMPTY = PeriodSummary(0, 0, 0, 0, 0, 0)
    }
}
