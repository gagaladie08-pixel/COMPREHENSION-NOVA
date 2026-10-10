package com.novastats.app.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Les 3 charts du Billboard. */
enum class Chart(val label: String, val emoji: String, val entityType: String) {
    HOT_100("Nova Hot 100", "🎵", "TRACK"),
    ARTIST_50("Nova Artist 50", "🎤", "ARTIST"),
    ALBUMS_75("Nova 75 Albums", "💿", "ALBUM");

    /** Limite par période (Daily plus courte). */
    fun limit(period: Period): Int = when (this) {
        HOT_100 -> BillboardLimits.hot100(period)
        ARTIST_50 -> BillboardLimits.artist50(period)
        ALBUMS_75 -> BillboardLimits.albums75(period)
    }
}

/**
 * Dates d'ancrage des snapshots : une date canonique par période.
 *  DAILY → le jour · WEEKLY / GLOBAL → lundi ISO · MONTHLY → 1er du mois · YEARLY → 1er janvier
 */
object BillboardDates {

    fun anchor(period: Period, date: LocalDate): LocalDate = when (period) {
        Period.DAILY -> date
        Period.WEEKLY, Period.GLOBAL -> Dates.weekOf(date).from
        Period.MONTHLY -> date.withDayOfMonth(1)
        Period.YEARLY -> date.withDayOfYear(1)
    }

    /** Intervalle des écoutes prises en compte pour un snapshot ancré sur [anchor]. */
    fun range(period: Period, anchor: LocalDate, today: LocalDate = Dates.today()): DateRange = when (period) {
        Period.DAILY -> DateRange(anchor, anchor)
        Period.WEEKLY -> DateRange(anchor, anchor.plusDays(6))
        Period.MONTHLY -> Dates.monthOf(anchor)
        Period.YEARLY -> Dates.yearOf(anchor)
        // Global = total all-time arrêté à la fin de la semaine (ou aujourd'hui si semaine en cours)
        Period.GLOBAL -> DateRange(LocalDate.of(1970, 1, 1), minOf(anchor.plusDays(6), today))
    }

    fun previous(period: Period, anchor: LocalDate): LocalDate = when (period) {
        Period.DAILY -> anchor.minusDays(1)
        Period.WEEKLY, Period.GLOBAL -> anchor.minusWeeks(1)
        Period.MONTHLY -> anchor.minusMonths(1)
        Period.YEARLY -> anchor.minusYears(1)
    }

    fun next(period: Period, anchor: LocalDate): LocalDate = when (period) {
        Period.DAILY -> anchor.plusDays(1)
        Period.WEEKLY, Period.GLOBAL -> anchor.plusWeeks(1)
        Period.MONTHLY -> anchor.plusMonths(1)
        Period.YEARLY -> anchor.plusYears(1)
    }

    /**
     * Dernière période PUBLIÉE : la période en cours n'a pas de snapshot (comme le vrai Billboard).
     * Daily → hier · Weekly/Global → semaine dernière · Monthly → mois dernier · Yearly → année dernière.
     */
    fun latest(period: Period, today: LocalDate = Dates.today()): LocalDate = previous(period, anchor(period, today))

    /** [anchor] est-elle la dernière période publiée ? (→ badge LIVE, flèche → grisée) */
    fun isCurrent(period: Period, anchor: LocalDate, today: LocalDate = Dates.today()) = latest(period, today) == anchor

    /** La période est-elle terminée (snapshot figé) ? */
    fun isClosed(period: Period, anchor: LocalDate, today: LocalDate = Dates.today()) = range(period, anchor, today).to < today

    /** Toutes les ancres publiées entre la première écoute et la dernière période close, dans l'ordre chronologique. */
    fun allAnchors(period: Period, firstDate: LocalDate, today: LocalDate = Dates.today()): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        var a = anchor(period, firstDate)
        val last = latest(period, today)
        while (!a.isAfter(last)) { out += a; a = next(period, a) }
        return out
    }

    /** Unité du compteur de périodes. */
    fun unitLabel(period: Period, n: Int): String = when (period) {
        Period.DAILY, Period.GLOBAL -> if (n > 1) "jours" else "jour"
        Period.WEEKLY -> if (n > 1) "semaines" else "semaine"
        Period.MONTHLY -> "mois"
        Period.YEARLY -> if (n > 1) "années" else "année"
    }

    private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.FRANCE)
    private val shortFmt = DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE)
    private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRANCE)

    fun label(period: Period, anchor: LocalDate): String = when (period) {
        Period.DAILY -> anchor.format(dayFmt).replaceFirstChar { it.uppercase() }
        Period.WEEKLY -> "Semaine ${Dates.isoWeekNumber(anchor)} · ${anchor.format(shortFmt)} – ${anchor.plusDays(6).format(shortFmt)}"
        Period.MONTHLY -> anchor.format(monthFmt).replaceFirstChar { it.uppercase() }
        Period.YEARLY -> anchor.year.toString()
        Period.GLOBAL -> "All-time · à la semaine du ${anchor.format(shortFmt)}"
    }
}

/** Mouvement d'une entrée de chart. */
sealed interface Movement {
    data object New : Movement
    data object Reentry : Movement
    data object Same : Movement
    data class Up(val places: Int) : Movement
    data class Down(val places: Int) : Movement

    /** Texte compact : "▲4", "▼2", "=", "NEW", "RE" */
    fun label(): String = when (this) {
        New -> "NEW"; Reentry -> "RE"; Same -> "="
        is Up -> "▲$places"; is Down -> "▼$places"
    }

    companion object {
        fun of(isNew: Boolean, isReentry: Boolean, previousPosition: Int?, position: Int): Movement = when {
            isNew -> New
            isReentry || previousPosition == null -> Reentry
            previousPosition == position -> Same
            previousPosition > position -> Up(previousPosition - position)
            else -> Down(position - previousPosition)
        }
    }
}

/** Une apparition d'une entité dans un snapshot (pour la fiche historique et les stats cumulées). */
data class ChartAppearance(val date: LocalDate, val position: Int, val playCount: Int)

/** Statistiques calculées à partir de l'historique des apparitions d'une entité dans un chart. */
data class ChartHistoryStats(
    val periodsInChart: Int,
    val firstEntry: ChartAppearance?,
    val peak: ChartAppearance?,
    val timesAtPeak: Int,
    val longestRunAt1: Int,
    val longestRunTop10: Int,
    val currentRunAt1: Int,
    val maxPlays: Int
) {
    companion object {
        val EMPTY = ChartHistoryStats(0, null, null, 0, 0, 0, 0, 0)
    }
}

object ChartHistory {

    /**
     * [appearances] : toutes les apparitions (n'importe quel ordre), [allAnchors] : la suite complète des ancres
     * du chart (pour savoir si deux apparitions sont consécutives). Si [allAnchors] est null, les apparitions
     * sont considérées consécutives quand elles se suivent dans la liste.
     */
    fun stats(appearances: List<ChartAppearance>, allAnchors: List<LocalDate>? = null): ChartHistoryStats {
        if (appearances.isEmpty()) return ChartHistoryStats.EMPTY
        val sorted = appearances.sortedBy { it.date }
        val peakPos = sorted.minOf { it.position }
        val peakFirst = sorted.first { it.position == peakPos }
        val timesAtPeak = sorted.count { it.position == peakPos }
        val index = allAnchors?.withIndex()?.associate { it.value to it.index }

        fun consecutive(a: ChartAppearance, b: ChartAppearance): Boolean =
            if (index == null) true else (index[b.date] ?: Int.MIN_VALUE) - (index[a.date] ?: Int.MIN_VALUE) == 1

        fun longestRun(predicate: (ChartAppearance) -> Boolean): Int {
            var best = 0; var run = 0; var prev: ChartAppearance? = null
            for (a in sorted) {
                run = if (predicate(a) && prev != null && predicate(prev) && consecutive(prev, a)) run + 1 else if (predicate(a)) 1 else 0
                if (run > best) best = run
                prev = a
            }
            return best
        }

        // Série #1 en cours : on remonte depuis la dernière apparition
        var currentRun = 0
        for (i in sorted.indices.reversed()) {
            val a = sorted[i]
            if (a.position != 1) break
            if (i < sorted.lastIndex && !consecutive(a, sorted[i + 1])) break
            currentRun++
        }

        return ChartHistoryStats(
            periodsInChart = sorted.size,
            firstEntry = sorted.first(),
            peak = peakFirst,
            timesAtPeak = timesAtPeak,
            longestRunAt1 = longestRun { it.position == 1 },
            longestRunTop10 = longestRun { it.position <= 10 },
            currentRunAt1 = currentRun,
            maxPlays = sorted.maxOf { it.playCount }
        )
    }
}

/** Règles d'entrée au Hall of Fame (alimenté par le Billboard). */
object HallOfFameRules {
    const val DIRECT_DEBUT = "DIRECT_DEBUT"
    const val LONG_RUN = "LONG_RUN"
    const val TRIPLE_DEBUT = "TRIPLE_DEBUT"
    const val LEGENDARY_RUN = "LEGENDARY_RUN"

    const val LONG_RUN_WEEKS = 3
    const val LONG_RUN_MONTHS = 2
    const val LEGENDARY_WEEKS_AT_1 = 10

    fun isDirectDebut(period: Period, position: Int, isNew: Boolean) =
        (period == Period.WEEKLY || period == Period.MONTHLY) && position == 1 && isNew

    fun isLongRun(period: Period, currentRunAt1: Int) = when (period) {
        Period.WEEKLY -> currentRunAt1 >= LONG_RUN_WEEKS
        Period.MONTHLY -> currentRunAt1 >= LONG_RUN_MONTHS
        else -> false
    }

    fun isLegendaryRun(period: Period, totalAt1: Int) = period == Period.WEEKLY && totalAt1 >= LEGENDARY_WEEKS_AT_1
}
