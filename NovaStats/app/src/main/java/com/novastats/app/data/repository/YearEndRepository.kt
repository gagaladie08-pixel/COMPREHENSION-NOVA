package com.novastats.app.data.repository

import com.novastats.app.data.db.NovaDatabase
import com.novastats.app.data.db.dao.DayCount
import com.novastats.app.data.db.dao.PeriodSummary
import com.novastats.app.domain.Chart
import com.novastats.app.domain.Dates
import com.novastats.app.domain.Period
import com.novastats.app.domain.YearEndRules
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Une ligne d'un classement de fin d'année, prête à afficher. */
data class YearEndRow(
    val id: Long,
    val name: String,
    val subtitle: String?,
    val imageUrl: String?,
    /** Points cumulés sur l'année (barème Billboard). */
    val points: Int,
    val plays: Int,
    val durationMs: Long,
    /** Semaines passées dans le classement hebdomadaire. */
    val weeks: Int,
    /** Meilleure position atteinte sur une semaine. */
    val peak: Int,
    /** Semaines passées n°1. */
    val weeksAt1: Int,
    /** Le titre a cessé de gagner des points après avoir atteint un seuil de récurrence. */
    val recurrent: Boolean = false,
    /** Titres distincts (affiché en sous-titre pour les artistes). */
    val extra: Int = 0
)

/** Tout ce qu'affiche l'écran 🏆 Year-End Charts. */
data class YearEndData(
    val year: Int,
    /** Fenêtre locale comptée : déc. → nov., ou année civile. */
    val fromIso: String,
    val toIso: String,
    val calendarYear: Boolean,
    val summary: PeriodSummary,
    val previous: PeriodSummary,
    val tracks: List<YearEndRow>,
    val artists: List<YearEndRow>,
    val albums: List<YearEndRow>,
    val bestDay: DayCount?,
    val newArtists: Int,
    val weeksCounted: Int,
    val inProgress: Boolean
) {
    val topTrack: YearEndRow? get() = tracks.firstOrNull()
    val topArtist: YearEndRow? get() = artists.firstOrNull()
    val topAlbum: YearEndRow? get() = albums.firstOrNull()
    val playsDelta: Int get() = summary.playCount - previous.playCount
    val playsDeltaPct: Int get() =
        if (previous.playCount > 0) ((summary.playCount - previous.playCount) * 100 / previous.playCount) else 0
    val avgPerDay: Float get() = if (summary.activeDays > 0) summary.playCount.toFloat() / summary.activeDays else 0f

    /** Libellé de la fenêtre comptée. */
    val windowLabel: String get() {
        fun pretty(iso: String): String = runCatching {
            val d = LocalDate.parse(iso)
            "${d.dayOfMonth} ${MOIS[d.monthValue - 1]} ${d.year}"
        }.getOrDefault(iso)
        return "${pretty(fromIso)} → ${pretty(toIso)}"
    }
}

private val MOIS = listOf(
    "janv.", "févr.", "mars", "avr.", "mai", "juin",
    "juil.", "août", "sept.", "oct.", "nov.", "déc."
)

/**
 * Year-End Nova, inspiré des classements annuels Billboard mais calculé uniquement avec l'historique
 * d'écoutes local :
 *
 *  1. Chaque semaine ISO (lundi → dimanche), les écoutes enregistrées dans Nova produisent un rang.
 *  2. Le rang hebdomadaire devient un score inversé : limite points pour la 1ʳᵉ place, puis 1 point
 *     pour la dernière position du chart (100 / 50 / 75 selon l'onglet).
 *  3. Fenêtre locale par défaut : 1er décembre → 30 novembre, ou année civile à la demande.
 *  4. Les titres Hot 100 utilisent des seuils de récurrence renforcés, comptés dans cette fenêtre
 *     annuelle. Une sortie arrête les points futurs, sans effacer les points déjà acquis.
 *  5. Identités Nova : remix rattachés à l'original (root_id), artistes crédités, compilations exclues.
 *  6. Départages : points, puis écoutes cumulées, puis semaines créditées.
 *
 * Ce n'est pas le calcul officiel américain : l'application n'a pas les métriques US de ventes,
 * streaming et radio utilisées par Billboard.
 */
class YearEndRepository(private val db: NovaDatabase) {

    /** Années disponibles (du plus récent au plus ancien). */
    suspend fun years(): List<Int> =
        db.dailyPlayDao().allDates().mapNotNull { it.take(4).toIntOrNull() }.distinct().sortedDescending()

    /** Fenêtre locale comptée : déc. → nov. ou année civile. */
    fun window(year: Int, calendarYear: Boolean): Pair<LocalDate, LocalDate> =
        if (calendarYear) LocalDate.of(year, 1, 1) to LocalDate.of(year, 12, 31)
        else LocalDate.of(year - 1, 12, 1) to LocalDate.of(year, 11, 30)

    suspend fun load(year: Int, calendarYear: Boolean = false): YearEndData {
        val (winFrom, winTo) = window(year, calendarYear)
        val fromIso = winFrom.format(Dates.ISO)
        val toIso = winTo.format(Dates.ISO)

        // Bornes réelles : on ne compte pas au-delà d'aujourd'hui
        val today = Dates.today()
        val lastWeekStart = winTo.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        /* ---------- 1. Les semaines de la fenêtre Year-End locale ---------- */
        val weeks = mutableListOf<Pair<String, String>>()
        var monday = winFrom.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        while (!monday.isAfter(lastWeekStart)) {
            val sunday = monday.plusDays(6)
            if (!monday.isAfter(today)) weeks += monday.format(Dates.ISO) to sunday.format(Dates.ISO)
            monday = monday.plusWeeks(1)
        }

        /* ---------- 2. Points cumulés semaine par semaine ---------- */
        val accTracks = HashMap<Long, Acc>()
        val accArtists = HashMap<Long, Acc>()
        val accAlbums = HashMap<Long, Acc>()

        for ((from, to) in weeks) {
            val rankedTracks = runCatching { db.billboardDao().rankTracks(from, to, Chart.HOT_100.limit(Period.WEEKLY)) }.getOrDefault(emptyList())
            rankedTracks.forEachIndexed { i, e ->
                val position = i + 1
                val acc = accTracks.getOrPut(e.entityId) { Acc() }
                // Seuils renforcés : le compteur repart dans la fenêtre Year-End sélectionnée.
                if (acc.recurrent) return@forEachIndexed
                if (YearEndRules.becomesRecurrent(acc.weeks, position)) {
                    acc.recurrent = true
                    return@forEachIndexed
                }
                acc.add(position, YearEndRules.pointsFor(position, Chart.HOT_100.limit(Period.WEEKLY)), e.plays, e.durationMs)
            }
            val rankedArtists = runCatching { db.billboardDao().rankArtists(from, to, Chart.ARTIST_50.limit(Period.WEEKLY)) }.getOrDefault(emptyList())
            rankedArtists.forEachIndexed { i, e ->
                accArtists.getOrPut(e.entityId) { Acc() }.apply {
                    distinct = maxOf(distinct, e.distinctTracks)
                    add(i + 1, Chart.ARTIST_50.limit(Period.WEEKLY) + 1 - (i + 1), e.plays, e.durationMs)
                }
            }
            val rankedAlbums = runCatching { db.billboardDao().rankAlbums(from, to, Chart.ALBUMS_75.limit(Period.WEEKLY)) }.getOrDefault(emptyList())
            rankedAlbums.forEachIndexed { i, e ->
                accAlbums.getOrPut(e.entityId) { Acc() }.apply {
                    distinct = maxOf(distinct, e.distinctTracks)
                    add(i + 1, Chart.ALBUMS_75.limit(Period.WEEKLY) + 1 - (i + 1), e.plays, e.durationMs)
                }
            }
        }

        /* ---------- 3. Résolution des noms / pochettes ---------- */
        val tracks = resolve(accTracks, Chart.HOT_100.limit(Period.WEEKLY)) { ids ->
            if (ids.isEmpty()) return@resolve emptyMap()
            val map = runCatching { db.trackDao().byIds(ids) }.getOrDefault(emptyList()).associateBy { it.trackId }
            val artistIds = map.values.map { it.artistId }.distinct()
            val artistNames = if (artistIds.isEmpty()) emptyMap()
            else runCatching { db.artistDao().byIds(artistIds) }.getOrDefault(emptyList())
                .associate { it.artistId to it.name }
            ids.mapNotNull { id ->
                val t = map[id] ?: return@mapNotNull null
                id to Triple(t.title, artistNames[t.artistId], t.coverUrl)
            }.toMap()
        }
        val artists = resolve(accArtists, Chart.ARTIST_50.limit(Period.WEEKLY)) { ids ->
            if (ids.isEmpty()) return@resolve emptyMap()
            runCatching { db.artistDao().byIds(ids) }.getOrDefault(emptyList())
                .associate { a ->
                    a.artistId to Triple(a.name, plural(accArtists[a.artistId]?.distinct ?: 0), a.photoUrl)
                }
        }
        val albums = resolve(accAlbums, Chart.ALBUMS_75.limit(Period.WEEKLY)) { ids ->
            if (ids.isEmpty()) return@resolve emptyMap()
            val map = runCatching { db.albumDao().byIds(ids) }.getOrDefault(emptyList()).associateBy { it.albumId }
            val artistIds = map.values.mapNotNull { it.artistId }.distinct()
            val names = if (artistIds.isEmpty()) emptyMap()
            else runCatching { db.artistDao().byIds(artistIds) }.getOrDefault(emptyList())
                .associate { it.artistId to it.name }
            ids.mapNotNull { id ->
                val al = map[id] ?: return@mapNotNull null
                // §12 : album partagé (artist_id NULL) → « Artistes variés »
                id to Triple(al.title, al.artistId?.let { names[it] } ?: "Artistes variés", al.coverUrl)
            }.toMap()
        }

        /* ---------- 4. Synthèse, comparaison, faits marquants ---------- */
        val summary = runCatching { db.dailyPlayDao().yearSummary(fromIso, toIso) }.getOrDefault(EMPTY)
        val previous = runCatching {
            val p = window(year - 1, calendarYear)
            db.dailyPlayDao().yearSummary(p.first.format(Dates.ISO), p.second.format(Dates.ISO))
        }.getOrDefault(EMPTY)
        val bestDay = runCatching { db.dailyPlayDao().bestDayBetween(fromIso, toIso) }.getOrNull()
        val newArtists = runCatching {
            db.artistDao().allByPlays().first().count { a ->
                val first = a.firstPlayedAt ?: return@count false
                Dates.toLocalDate(first).year == year
            }
        }.getOrDefault(0)

        return YearEndData(
            year = year,
            fromIso = fromIso,
            toIso = toIso,
            calendarYear = calendarYear,
            summary = summary,
            previous = previous,
            tracks = tracks,
            artists = artists,
            albums = albums,
            bestDay = bestDay,
            newArtists = newArtists,
            weeksCounted = weeks.size,
            inProgress = !winTo.isBefore(today)
        )
    }

    /* ----------------------------- outils ----------------------------- */

    /** Accumulateur d'une entité sur l'année. */
    private class Acc {
        var points: Int = 0
        var plays: Int = 0
        var durationMs: Long = 0
        var weeks: Int = 0
        var peak: Int = Int.MAX_VALUE
        var weeksAt1: Int = 0
        var recurrent: Boolean = false
        var distinct: Int = 0

        fun add(position: Int, pts: Int, weekPlays: Int, weekDuration: Long) {
            points += pts
            plays += weekPlays
            durationMs += weekDuration
            weeks++
            if (position < peak) peak = position
            if (position == 1) weeksAt1++
        }
    }

    private suspend fun resolve(
        acc: HashMap<Long, Acc>,
        limit: Int,
        names: suspend (List<Long>) -> Map<Long, Triple<String, String?, String?>>
    ): List<YearEndRow> {
        val ranked = acc.entries
            .sortedWith(compareByDescending<Map.Entry<Long, Acc>> { it.value.points }
                .thenByDescending { it.value.plays }
                .thenByDescending { it.value.weeks })
            .take(limit)
        if (ranked.isEmpty()) return emptyList()
        // Room refuse IN (:ids) avec une liste vide — on garde donc le garde-fou ci-dessus.
        val meta = names(ranked.map { it.key })
        return ranked.mapNotNull { (id, a) ->
            val (name, sub, img) = meta[id] ?: return@mapNotNull null
            YearEndRow(
                id = id, name = name, subtitle = sub, imageUrl = img,
                points = a.points, plays = a.plays, durationMs = a.durationMs,
                weeks = a.weeks, peak = if (a.peak == Int.MAX_VALUE) 0 else a.peak,
                weeksAt1 = a.weeksAt1, recurrent = a.recurrent, extra = a.distinct
            )
        }
    }

    private fun plural(n: Int): String = when { n > 1 -> "$n titres"; n == 1 -> "1 titre"; else -> "" }

    companion object {
        fun pointsFor(position: Int, limit: Int): Int = YearEndRules.pointsFor(position, limit)

        val EMPTY = PeriodSummary(0, 0, 0, 0, 0, 0)
    }
}

/** Formatage « 1 234 pts » réutilisé par l'UI. */
fun formatPoints(n: Int): String = String.format(Locale.FRANCE, "%,d", n).replace(' ', ' ')
