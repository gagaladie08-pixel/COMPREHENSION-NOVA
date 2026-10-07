package com.novastats.app.data.repository

import com.novastats.app.data.db.NovaDatabase
import com.novastats.app.data.db.dao.CertEvent
import com.novastats.app.data.db.dao.NewArtist
import com.novastats.app.data.db.dao.PantheonEvent
import com.novastats.app.data.db.dao.RankedAlbum
import com.novastats.app.data.db.dao.RankedArtist
import com.novastats.app.data.db.dao.RankedTrack
import com.novastats.app.data.db.dao.RewindTotals
import com.novastats.app.domain.Dates
import com.novastats.app.domain.Period
import com.novastats.app.domain.StreakCalculator
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Période d'un Rewind : un mois (AAAA-MM) ou une année (AAAA). */
data class RewindSpec(val kind: Kind, val from: LocalDate, val to: LocalDate) {
    enum class Kind { MONTH, YEAR }
    val key: String get() = if (kind == Kind.MONTH) from.format(DateTimeFormatter.ofPattern("yyyy-MM")) else from.year.toString()
    /** « Septembre 2026 » / « 2026 » */
    val label: String get() = if (kind == Kind.MONTH) from.month.getDisplayName(TextStyle.FULL, Locale.FRANCE).replaceFirstChar { it.uppercase() } + " " + from.year else from.year.toString()
    /** « de septembre » / « de 2026 » */
    val ofLabel: String get() = if (kind == Kind.MONTH) {
        val m = from.month.getDisplayName(TextStyle.FULL, Locale.FRANCE)
        (if (m.first() in "aeiouyéû") "d'$m" else "de $m")
    } else "de ${from.year}"
    val fromIso: String get() = from.format(Dates.ISO)
    val toIso: String get() = to.format(Dates.ISO)
    val fromMs: Long get() = Dates.startOfDayMs(from)
    val toMs: Long get() = Dates.startOfDayMs(to.plusDays(1)) - 1
    /** Période précédente de même nature (comparaison). */
    fun previous(): RewindSpec = if (kind == Kind.MONTH) month(from.minusMonths(1)) else year(from.year - 1)

    companion object {
        fun month(d: LocalDate): RewindSpec { val r = Dates.monthOf(d); return RewindSpec(Kind.MONTH, r.from, r.to) }
        fun year(y: Int): RewindSpec { val r = Dates.yearOf(LocalDate.of(y, 1, 1)); return RewindSpec(Kind.YEAR, r.from, r.to) }
        fun parse(key: String): RewindSpec = if (key.length == 7) month(LocalDate.parse("$key-01")) else year(key.toInt())
    }
}

data class NumberOne(val title: String, val artistName: String, val cover: String?, val weeks: Int)

/** Tout le contenu d'un Rewind, calculé une fois puis affiché slide par slide. */
data class RewindData(
    val spec: RewindSpec,
    val totals: RewindTotals,
    val previous: RewindTotals?,
    val topTracks: List<RankedTrack>,
    val topArtists: List<RankedArtist>,
    val topAlbums: List<RankedAlbum>,
    val topTrackDays: Int,
    val topArtistDays: Int,
    val bestDay: LocalDate?,
    val bestDayPlays: Int,
    val bestDayTrack: RankedTrack?,
    val longestStreak: Int,
    val favouriteHour: Int?,          // 0-23
    val nightShare: Float,            // part des écoutes entre 22h et 5h
    val favouriteWeekday: Int?,       // 1 = lundi … 7 = dimanche
    val hours: List<Int>,             // 24 valeurs : écoutes par heure locale (index = heure)
    val weekdays: List<Int>,          // 7 valeurs : écoutes par jour (index 0 = lundi)
    val newArtists: List<NewArtist>,
    val newArtistCount: Int,
    val newTrackCount: Int,
    val certifications: List<CertEvent>,
    val pantheon: List<PantheonEvent>,
    val numberOnes: List<NumberOne>,          // titres n°1 hebdo sur la période + nombre de semaines
    val longestSessionMs: Long,
    val previousTopArtistName: String?
) {
    val isEmpty: Boolean get() = totals.plays == 0
    val topTrack: RankedTrack? get() = topTracks.firstOrNull()
    val topArtist: RankedArtist? get() = topArtists.firstOrNull()
    val topAlbum: RankedAlbum? get() = topAlbums.firstOrNull()
    val topArtistShare: Int get() = topArtist?.let {
        if (totals.plays == 0) 0 else (it.periodPlays.toLong() * 100L / totals.plays.toLong()).coerceIn(0L, 100L).toInt()
    } ?: 0
    val playsDeltaPct: Int? get() = previous?.takeIf { it.plays > 0 }?.let {
        (((totals.plays.toLong() - it.plays.toLong()) * 100L / it.plays.toLong())
            .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())).toInt()
    }
}

/**
 * ✨ Nova Rewind — récap d'un mois ou d'une année, façon keynote.
 * Tout dérive des tables existantes (`daily_plays`, `scrobbles`, historiques de certifications / Panthéon, snapshots Billboard).
 */
class RewindEngine(private val db: NovaDatabase) {

    /** Rewinds disponibles : mois complets (le mois en cours exclu) puis années ayant des écoutes. */
    suspend fun available(today: LocalDate = Dates.today()): List<RewindSpec> {
        val dao = db.rewindDao()
        val currentMonth = today.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        val months = dao.monthsWithPlays().filter { it < currentMonth }.map { RewindSpec.parse(it) }
        val years = dao.yearsWithPlays().map { RewindSpec.parse(it) }
        return months + years
    }

    /** Le Rewind « à la une » : le dernier mois complet avec des écoutes, sinon null. */
    suspend fun featured(today: LocalDate = Dates.today()): RewindSpec? =
        available(today).firstOrNull { it.kind == RewindSpec.Kind.MONTH }

    suspend fun build(spec: RewindSpec): RewindData {
        val dao = db.rewindDao()
        val from = spec.fromIso; val to = spec.toIso
        val totals = dao.totals(from, to)
        val prevSpec = spec.previous()
        val previous = dao.totals(prevSpec.fromIso, prevSpec.toIso).takeIf { it.plays > 0 }

        val topTracks = db.trackDao().topForPeriod(from, to, 5).first()
        val topArtists = db.artistDao().topForPeriod(from, to, 5).first()
        val topAlbums = db.albumDao().topForPeriod(from, to, 5).first()
        val topTrackDays = topTracks.firstOrNull()?.let { dao.daysWithTrack(it.track.trackId, from, to) } ?: 0
        val topArtistDays = topArtists.firstOrNull()?.let { dao.daysWithArtist(it.artist.artistId, from, to) } ?: 0

        val best = dao.bestDay(from, to)
        val bestDate = best?.let { Dates.parse(it.date) }
        val bestDayTrack = best?.let { db.trackDao().topForPeriod(it.date, it.date, 1).first().firstOrNull() }

        val series = dao.dailySeries(from, to)
        val streak = StreakCalculator.compute(series.map { Dates.parse(it.date) }, today = spec.to).best

        val hours = dao.hours(spec.fromMs, spec.toMs)
        val totalH = hours.sumOf { it.plays }
        val favouriteHour = hours.maxByOrNull { it.plays }?.hour
        val night = hours.filter { it.hour >= 22 || it.hour < 5 }.sumOf { it.plays }
        val nightShare = if (totalH == 0) 0f else night.toFloat() / totalH
        val wdRaw = dao.weekdays(spec.fromMs, spec.toMs)
        val weekday = wdRaw.maxByOrNull { it.plays }?.weekday?.let { if (it == 0) 7 else it }
        // Lundi d'abord : strftime %w renvoie 0 = dimanche … 6 = samedi
        val weekdayBuckets = (1..7).map { d -> val idx = if (d == 7) 0 else d; wdRaw.firstOrNull { it.weekday == idx }?.plays ?: 0 }

        val newArtists = dao.newArtists(from, to, spec.fromMs, spec.toMs)
        val newArtistCount = dao.newArtistCount(spec.fromMs, spec.toMs)
        val newTrackCount = dao.newTrackCount(spec.fromMs, spec.toMs)
        val certs = dao.certifications(spec.fromMs, spec.toMs)
        val pantheon = dao.pantheon(spec.fromMs, spec.toMs)

        // N°1 hebdomadaires sur la période
        val weekly = db.billboardDao().snapshotDates(Period.WEEKLY.dbName).filter { it in from..to }
        val n1 = HashMap<Long, Int>()
        for (d in weekly) db.billboardDao().numberOneTrack(Period.WEEKLY.dbName, d)?.let { n1[it] = (n1[it] ?: 0) + 1 }
        val numberOnes = n1.entries.sortedByDescending { it.value }.take(5).mapNotNull { (id, weeks) ->
            val t = db.trackDao().getById(id) ?: return@mapNotNull null
            val a = db.artistDao().getById(t.artistId)?.name ?: ""
            NumberOne(t.title, a, t.coverUrl, weeks)
        }

        val previousTopArtist = db.artistDao().topForPeriod(prevSpec.fromIso, prevSpec.toIso, 1).first().firstOrNull()?.artist?.name

        return RewindData(
            spec, totals, previous, topTracks, topArtists, topAlbums, topTrackDays, topArtistDays,
            bestDate, best?.playCount ?: 0, bestDayTrack, streak, favouriteHour, nightShare, weekday,
            (0..23).map { h -> hours.firstOrNull { it.hour == h }?.plays ?: 0 }, weekdayBuckets,
            newArtists, newArtistCount, newTrackCount, certs, pantheon, numberOnes,
            dao.longestSessionMs(spec.fromMs, spec.toMs), previousTopArtist
        )
    }
}
