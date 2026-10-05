package com.novastats.app.data.repository

import com.novastats.app.data.db.NovaDatabase
import com.novastats.app.data.db.dao.DayCount
import com.novastats.app.data.db.dao.PeriodSummary
import com.novastats.app.domain.Chart
import com.novastats.app.domain.Dates
import com.novastats.app.domain.Period
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
    /** Titres distincts (affiché en sous-titre pour les artistes). */
    val extra: Int = 0
)

/** Tout ce qu'affiche l'écran 🏆 Year-End Charts. */
data class YearEndData(
    val year: Int,
    /** Fenêtre réellement comptée (année Billboard déc. → nov., ou année civile). */
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
 * Year-End Charts — **les vraies règles du Billboard américain** :
 *
 *  1. **On ne cumule pas les écoutes** : le classement de fin d'année additionne les **points gagnés
 *     semaine après semaine** sur les charts hebdomadaires (les mêmes que l'onglet Billboard).
 *  2. **Barème inversé** : 100 points pour la 1ʳᵉ place, 99 pour la 2ᵉ… 1 point pour la 100ᵉ
 *     (Nova Hot 100). Artist 50 → 50 points pour la 1ʳᵉ place. 75 Albums → 75 points.
 *  3. **Année de référence Billboard** : elle commence début décembre de l'année précédente et se
 *     termine fin novembre (et non au 31 décembre). Bascule possible sur l'année civile.
 *  4. **Règle des récurrents** (Hot 100) : un titre présent depuis 20 semaines et retombé au-delà
 *     de la 50ᵉ place quitte le classement et n'accumule plus de points — comme le vrai Billboard.
 *  5. Mêmes règles d'entités que le Billboard : remix rattachés à l'original (root_id), chaque
 *     artiste crédité reçoit l'écoute, compilations exclues, albums partagés « Artistes variés ».
 *  6. Départages : points, puis écoutes cumulées, puis semaines dans le classement.
 */
class YearEndRepository(private val db: NovaDatabase) {

    /** Années disponibles (du plus récent au plus ancien). */
    suspend fun years(): List<Int> =
        db.dailyPlayDao().allDates().mapNotNull { it.take(4).toIntOrNull() }.distinct().sortedDescending()

    /** Fenêtre comptée : année Billboard (déc. → nov.) ou année civile. */
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

        /* ---------- 1. Les semaines de l'année Billboard ---------- */
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
                // Règle des récurrents : 20 semaines ou plus et retombé au-delà de la 50e place → sort du chart
                if (acc.weeks >= RECURRENT_WEEKS && position > RECURRENT_POSITION) { acc.recurrent = true; return@forEachIndexed }
                if (acc.recurrent) return@forEachIndexed
                acc.add(position, Chart.HOT_100.limit(Period.WEEKLY) + 1 - position, e.plays, e.durationMs)
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
                weeksAt1 = a.weeksAt1, extra = a.distinct
            )
        }
    }

    private fun plural(n: Int): String = when { n > 1 -> "$n titres"; n == 1 -> "1 titre"; else -> "" }

    companion object {
        /** Règle des récurrents du Hot 100 : 20 semaines + retombé au-delà de la 50e place. */
        const val RECURRENT_WEEKS = 20
        const val RECURRENT_POSITION = 50

        fun pointsFor(position: Int, limit: Int): Int = (limit + 1 - position).coerceAtLeast(0)

        val EMPTY = PeriodSummary(0, 0, 0, 0, 0, 0)
    }
}

/** Formatage « 1 234 pts » réutilisé par l'UI. */
fun formatPoints(n: Int): String = String.format(Locale.FRANCE, "%,d", n).replace(' ', ' ')
