package com.novastats.app.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/** Périodes de Stats / Billboard — calendaires (pas glissantes). */
enum class Period(val label: String, val dbName: String, val frLabel: String) {
    DAILY("Daily", "DAILY", "Jour"),
    WEEKLY("Weekly", "WEEKLY", "Semaine"),
    MONTHLY("Monthly", "MONTHLY", "Mois"),
    YEARLY("Yearly", "YEARLY", "Année"),
    GLOBAL("Global", "GLOBAL", "Global")
}

/** Intervalle de dates ISO inclusif [from, to]. */
data class DateRange(val from: LocalDate, val to: LocalDate) {
    val fromIso: String get() = from.format(Dates.ISO)
    val toIso: String get() = to.format(Dates.ISO)
    val fromMs: Long get() = Dates.startOfDayMs(from)
    val toMs: Long get() = Dates.startOfDayMs(to.plusDays(1)) - 1
}

object Dates {
    val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    val zone: ZoneId get() = ZoneId.systemDefault()

    fun today(): LocalDate = LocalDate.now(zone)
    fun toLocalDate(epochMs: Long): LocalDate = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
    fun toIso(epochMs: Long): String = toLocalDate(epochMs).format(ISO)
    fun startOfDayMs(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()
    fun parse(iso: String): LocalDate = LocalDate.parse(iso, ISO)

    /** Semaine ISO (lundi → dimanche). */
    fun weekOf(date: LocalDate): DateRange {
        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return DateRange(monday, monday.plusDays(6))
    }

    fun monthOf(date: LocalDate) = DateRange(date.withDayOfMonth(1), date.with(TemporalAdjusters.lastDayOfMonth()))
    fun yearOf(date: LocalDate) = DateRange(date.withDayOfYear(1), date.with(TemporalAdjusters.lastDayOfYear()))
    fun isoWeekNumber(date: LocalDate): Int = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

    /**
     * Intervalle de l'onglet Stats : Daily = aujourd'hui, Weekly = 7 derniers jours (glissant),
     * Monthly = mois calendaire, Yearly = année calendaire, Global = tout.
     */
    fun statsRangeFor(period: Period, anchor: LocalDate = today()): DateRange = when (period) {
        Period.WEEKLY -> DateRange(anchor.minusDays(6), anchor)
        else -> rangeFor(period, anchor)
    }

    /** Intervalle calendaire couvert par une période (Billboard), ancré sur [anchor] (aujourd'hui par défaut). */
    fun rangeFor(period: Period, anchor: LocalDate = today()): DateRange = when (period) {
        Period.DAILY -> DateRange(anchor, anchor)
        Period.WEEKLY -> weekOf(anchor)
        Period.MONTHLY -> monthOf(anchor)
        Period.YEARLY -> yearOf(anchor)
        Period.GLOBAL -> DateRange(LocalDate.of(1970, 1, 1), anchor)
    }

    /** Période précédente (référence des mouvements du Billboard). */
    fun previous(period: Period, anchor: LocalDate): LocalDate = when (period) {
        Period.DAILY -> anchor.minusDays(1)
        Period.WEEKLY -> anchor.minusWeeks(1)
        Period.MONTHLY -> anchor.minusMonths(1)
        Period.YEARLY -> anchor.minusYears(1)
        Period.GLOBAL -> anchor.minusWeeks(1) // Global : mouvements calculés chaque semaine
    }
}

/** Limites des charts Billboard par période. */
object BillboardLimits {
    fun hot100(period: Period) = if (period == Period.DAILY) 75 else 100
    fun artist50(period: Period) = if (period == Period.DAILY) 25 else 50
    fun albums75(period: Period) = if (period == Period.DAILY) 50 else 75
}

/** Calcul de streak (jours consécutifs avec ≥ 1 écoute) à partir d'une liste triée de dates actives. */
object StreakCalculator {
    data class Result(val current: Int, val best: Int, val bestEndDate: LocalDate?)

    fun compute(activeDatesSorted: List<LocalDate>, today: LocalDate = Dates.today()): Result {
        if (activeDatesSorted.isEmpty()) return Result(0, 0, null)
        var best = 0; var bestEnd: LocalDate? = null
        var run = 0; var prev: LocalDate? = null
        for (d in activeDatesSorted) {
            run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
            if (run > best) { best = run; bestEnd = d }
            prev = d
        }
        val last = activeDatesSorted.last()
        val current = if (last == today || last == today.minusDays(1)) run else 0
        return Result(current, best, bestEnd)
    }
}
