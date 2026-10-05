package com.novastats.app.ui.screens

import com.novastats.app.domain.Chart
import com.novastats.app.domain.Period

/** Deux phrases éditoriales calculées à partir du parcours réel de l'entrée dans les charts Nova. */
internal data class YearEndStory(
    val anecdote: String,
    val achievement: String
)

/**
 * Produit une anecdote de chart et une phrase d'exploit propres à l'entrée.
 * Toutes les affirmations viennent des statistiques déjà calculées par NovaStats ; aucune
 * information biographique ou anecdote externe n'est inventée.
 */
internal fun yearEndStory(entry: YearEndPopupEntry): YearEndStory {
    val row = entry.row
    val chartName = when (entry.chart) {
        Chart.HOT_100 -> "Hot 100"
        Chart.ARTIST_50 -> "Artist 50"
        Chart.ALBUMS_75 -> "Albums 75"
    }
    val recurrentThreshold = row.recurrentThreshold?.takeIf {
        entry.chart == Chart.HOT_100 && row.recurrent
    }
    val subject = when (entry.chart) {
        Chart.HOT_100 -> "Le titre «${row.name}»"
        Chart.ARTIST_50 -> "L'artiste «${row.name}»"
        Chart.ALBUMS_75 -> "L'album «${row.name}»"
    }
    val peakLabel = row.peak.takeIf { it > 0 }?.let { "#$it" } ?: "—"
    val weekText = row.weeks.asWeeks()

    val anecdote = when {
        recurrentThreshold != null -> {
            val weeksBeforeStop = row.recurrentAfterWeeks ?: row.weeks
            val triggerRank = row.recurrentAtPosition?.let { "#$it" } ?: "un rang hors du seuil protégé"
            "$subject a connu un vrai point de bascule : après ${weeksBeforeStop.asWeeks()} déjà créditées, " +
                "il a rejoint le rang $triggerRank, au-delà du rang protégé #${recurrentThreshold.belowPosition}. " +
                "Les points inscrits avant cette semaine sont restés acquis."
        }

        (entry.chart == Chart.ARTIST_50 || entry.chart == Chart.ALBUMS_75) && row.extra > 0 -> {
            val entity = if (entry.chart == Chart.ARTIST_50) "de son catalogue" else "de cet album"
            "$subject porte aussi un détail que le total ne raconte pas seul : au meilleur relevé hebdomadaire, " +
                "${row.extra} titres distincts $entity ont nourri sa présence dans le classement $chartName. " +
                "C'est un pic sur une semaine, pas un cumul annuel."
        }

        entry.weeksCounted > 0 && row.weeks == entry.weeksCounted ->
            "$subject n'a manqué aucun relevé hebdomadaire de cette fenêtre : " +
                "${weekText} de présence sur ${entry.weeksCounted} semaines examinées. " +
                "Une régularité parfaite, du premier au dernier relevé disponible."

        row.weeks >= 6 && row.peak > 0 ->
            "$subject a installé son histoire dans la durée : ${weekText} créditées, " +
                "avec un meilleur rang hebdomadaire de $peakLabel. C'est ce parcours, semaine après semaine, " +
                "qui donne sa couleur à sa place annuelle."

        row.peak > 0 ->
            "$subject a signé son meilleur pic hebdomadaire au rang $peakLabel dans le classement $chartName, " +
                "sur ${weekText} créditées. La place annuelle #${entry.rank} rassemble ce moment et tous les autres relevés."

        else ->
            "$subject a inscrit ${formatCount(row.points)} points dans le classement $chartName. " +
                "Son rang annuel #${entry.rank} garde la trace de cette fenêtre d'écoute."
    }

    val achievement = when {
        recurrentThreshold != null -> {
            val weeksBeforeStop = row.recurrentAfterWeeks ?: row.weeks
            val triggerRank = row.recurrentAtPosition?.let { "#$it" } ?: "—"
            "$subject a signé un exploit : conserver ${formatCount(row.points)} points et la place annuelle #${entry.rank} " +
                "malgré l'arrêt du cumul au rang déclencheur $triggerRank. Après ${weeksBeforeStop.asWeeks()}, " +
                "la règle des ${recurrentThreshold.minimumWeeks} semaines hors Top ${recurrentThreshold.belowPosition} " +
                "a stoppé la suite sans annuler ses acquis."
        }

        row.weeksAt1 > 0 ->
            "$subject a pris la tête du classement $chartName pendant ${row.weeksAt1.asWeeks()}. " +
                "Chaque première place rapporte ${entry.chart.limit(Period.WEEKLY)} points au barème Nova ; " +
                "son total de ${formatCount(row.points)} points l'installe au rang annuel #${entry.rank}."

        row.peak in 1..5 ->
            "$subject a poussé jusqu'au Top 5 du classement $chartName avec un pic au rang $peakLabel. " +
                "Ses ${formatCount(row.points)} points cumulés signent la place annuelle #${entry.rank}."

        row.peak in 6..10 ->
            "$subject a franchi le Top 10 du classement $chartName, jusqu'au rang $peakLabel. " +
                "Ce sommet hebdomadaire a contribué à ses ${formatCount(row.points)} points et à sa place annuelle #${entry.rank}."

        row.weeks >= 10 ->
            "$subject a transformé ${weekText} créditées dans le classement $chartName en " +
                "${formatCount(row.points)} points et en une place annuelle #${entry.rank} : " +
                "un résultat construit dans la durée."

        else ->
            "Dans le classement $chartName, son meilleur rang hebdomadaire est $peakLabel ; " +
                "son total de ${formatCount(row.points)} points lui vaut la place annuelle #${entry.rank}. " +
                "C'est la signature chiffrée de son parcours cette année."
    }

    return YearEndStory(anecdote = anecdote, achievement = achievement)
}

private fun Int.asWeeks(): String = "$this ${if (this == 1) "semaine" else "semaines"}"
