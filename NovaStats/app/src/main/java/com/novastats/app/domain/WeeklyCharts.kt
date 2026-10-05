package com.novastats.app.domain

import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Règles calendaires du récapitulatif des charts envoyé le lundi matin. */
object WeeklyChartsSchedule {
    val notificationTime: LocalTime = LocalTime.of(9, 0)

    /** Prochaine exécution du lundi à 9 h, dans le fuseau local de l'appareil. */
    fun nextRun(now: ZonedDateTime): ZonedDateTime {
        val monday = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
        val candidate = at(monday, now.zone)
        return if (candidate.isAfter(now)) candidate else at(monday.plusWeeks(1), now.zone)
    }

    /** Date d'exécution suivante après un worker, sans rattraper en rafale les semaines manquées. */
    fun nextMondayAfter(currentMonday: LocalDate, now: ZonedDateTime): LocalDate {
        require(currentMonday.dayOfWeek == DayOfWeek.MONDAY) { "La date d'exécution doit être un lundi" }
        return maxOf(currentMonday.plusWeeks(1), nextRun(now).toLocalDate())
    }

    fun at(monday: LocalDate, zone: ZoneId): ZonedDateTime {
        require(monday.dayOfWeek == DayOfWeek.MONDAY) { "La date d'exécution doit être un lundi" }
        return ZonedDateTime.of(monday, notificationTime, zone)
    }

    /** Semaine complète terminée juste avant le lundi planifié. */
    fun previousClosedWeek(scheduledMonday: LocalDate): DateRange {
        require(scheduledMonday.dayOfWeek == DayOfWeek.MONDAY) { "La date d'exécution doit être un lundi" }
        return DateRange(scheduledMonday.minusWeeks(1), scheduledMonday.minusDays(1))
    }
}

data class WeeklyChartHighlight(
    val chart: Chart,
    val entityId: Long,
    val name: String,
    val plays: Int,
    val previousLeaderId: Long?
)

data class WeeklyChartsDigest(
    val title: String,
    val summary: String,
    val body: String
)

/** Texte pur, fondé uniquement sur les classements locaux de la semaine écoulée. */
object WeeklyChartsDigestBuilder {
    const val TITLE = "Cette semaine dans tes charts"
    private val dateFormat = DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE)

    fun build(range: DateRange, highlights: List<WeeklyChartHighlight>): WeeklyChartsDigest? {
        val valid = highlights.filter { it.name.isNotBlank() && it.plays > 0 }
        if (valid.isEmpty()) return null

        val summary = valid.joinToString(" · ") { "${it.chart.emoji} ${it.name}" }
        val lines = valid.map { highlight ->
            val plays = NumberFormat.getIntegerInstance(Locale.FRANCE).format(highlight.plays)
            val count = "$plays ${if (highlight.plays == 1) "écoute" else "écoutes"}"
            val movement = when (highlight.previousLeaderId) {
                null -> ""
                highlight.entityId -> " · conserve la tête"
                else -> " · prend la tête"
            }
            "${highlight.chart.emoji} ${highlight.chart.label} : « ${highlight.name} » — $count$movement"
        }
        val dates = "Du ${range.from.format(dateFormat)} au ${range.to.format(dateFormat)}"
        return WeeklyChartsDigest(
            title = TITLE,
            summary = summary,
            body = "$dates\n\n${lines.joinToString("\n")}"
        )
    }
}
