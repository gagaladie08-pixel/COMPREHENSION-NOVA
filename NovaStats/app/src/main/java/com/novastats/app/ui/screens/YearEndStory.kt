package com.novastats.app.ui.screens

import com.novastats.app.domain.Chart
import com.novastats.app.domain.Period

/** Deux phrases éditoriales dédiées à une ligne Year-End. */
internal data class YearEndStory(
    val anecdote: String,
    val achievement: String
)

/**
 * Compose des phrases propres à chaque entrée : le nom, le chart, le rang et les faits locaux
 * changent le texte. La tournure varie aussi selon le rang annuel pour éviter un texte passe-partout.
 * Aucune anecdote biographique ou information externe n'est inventée.
 */
internal fun yearEndStory(entry: YearEndPopupEntry): YearEndStory = YearEndStory(
    anecdote = anecdoteFor(entry),
    achievement = achievementFor(entry)
)

private fun anecdoteFor(entry: YearEndPopupEntry): String {
    val row = entry.row
    val name = "«${row.name}»"
    val peak = row.peak.takeIf { it > 0 }?.let { "#$it" } ?: "—"
    val chart = chartName(entry.chart)
    val threshold = row.recurrentThreshold?.takeIf { entry.chart == Chart.HOT_100 && row.recurrent }
    val fact = when {
        threshold != null -> {
            val weeksBeforeStop = row.recurrentAfterWeeks ?: row.weeks
            val triggerRank = row.recurrentAtPosition?.let { "#$it" } ?: "—"
            "dans le Hot 100, après ${weeksBeforeStop.asWeeks()} déjà créditées, la semaine au rang $triggerRank a franchi le seuil protégé #${threshold.belowPosition}; les points antérieurs sont restés acquis"
        }

        entry.chart == Chart.HOT_100 && row.weeksAt1 > 0 ->
            "${row.weeksAt1.asWeeks()} au sommet du Hot 100, au fil de ${row.weeks.asWeeks()} créditées"

        entry.chart == Chart.HOT_100 && entry.weeksCounted > 0 && row.weeks == entry.weeksCounted ->
            "une présence dans le Hot 100 à chacun des ${entry.weeksCounted} relevés hebdomadaires de la fenêtre"

        entry.chart == Chart.HOT_100 ->
            "${row.weeks.asWeeks()} créditées dans le Hot 100 et un meilleur rang hebdomadaire de $peak"

        entry.chart == Chart.ARTIST_50 && row.extra > 0 ->
            "jusqu'à ${row.extra} titres distincts associés à l'artiste dans une même semaine de l'Artist 50, sur ${row.weeks.asWeeks()} créditées"

        entry.chart == Chart.ALBUMS_75 && row.extra > 0 ->
            "jusqu'à ${row.extra} titres distincts de l'album réunis dans un relevé des Albums 75, sur ${row.weeks.asWeeks()} créditées"

        else -> "${row.weeks.asWeeks()} créditées dans le classement $chart et un meilleur rang hebdomadaire de $peak"
    }

    return when (entry.chart) {
        Chart.HOT_100 -> when (variant(entry, offset = 0)) {
            0 -> "Anecdote du Hot 100 : le parcours de $name garde ce repère — $fact."
            1 -> "Petite note d'archives pour $name : $fact."
            2 -> "Dans le Hot 100, ce qui rend la ligne de $name singulière, c'est ceci : $fact."
            3 -> "Le détail que le rang annuel de $name ne raconte pas tout seul : $fact."
            4 -> "La saison Nova de $name tient aussi à ce moment : $fact."
            5 -> "En feuilletant le Hot 100 de cette fenêtre, $name laisse cette trace : $fact."
            6 -> "Le passage marquant de $name dans le Hot 100 ? $fact."
            else -> "Pour $name, la petite histoire derrière les points est simple : $fact."
        }

        Chart.ARTIST_50 -> when (variant(entry, offset = 0)) {
            0 -> "Dans l'Artist 50, la particularité de $name tient à ceci : $fact."
            1 -> "Détail peu visible sur la ligne de $name : $fact."
            2 -> "L'Artist 50 a enregistré ce petit repère pour $name : $fact."
            3 -> "Le portrait chiffré de $name commence par ce détail : $fact."
            4 -> "Au relevé hebdomadaire le plus riche de $name, le détail est le suivant : $fact."
            5 -> "Anecdote d'Artist 50 pour $name : $fact."
            6 -> "Ce qui distingue la campagne de $name dans l'Artist 50 : $fact."
            else -> "La ligne artiste de $name a sa propre histoire : $fact."
        }

        Chart.ALBUMS_75 -> when (variant(entry, offset = 0)) {
            0 -> "Dans les Albums 75, la petite archive de $name : $fact."
            1 -> "Le détail de fabrication du score de $name se cache ici : $fact."
            2 -> "Anecdote de classement pour $name : $fact."
            3 -> "La ligne de $name a ce relief particulier dans les Albums 75 : $fact."
            4 -> "Un détail donne sa couleur au parcours de $name : $fact."
            5 -> "Pour $name, le relevé le plus parlant raconte ceci : $fact."
            6 -> "Dans cette fenêtre, $name laisse une trace singulière : $fact."
            else -> "Le petit fait marquant de $name dans les Albums 75 : $fact."
        }
    }
}

private fun achievementFor(entry: YearEndPopupEntry): String {
    val row = entry.row
    val name = "«${row.name}»"
    val chart = chartName(entry.chart)
    val points = formatCount(row.points)
    val annualRank = "#${entry.rank}"
    val peak = row.peak.takeIf { it > 0 }?.let { "#$it" } ?: "—"
    val threshold = row.recurrentThreshold?.takeIf { entry.chart == Chart.HOT_100 && row.recurrent }
    val weeklyLimit = entry.chart.limit(Period.WEEKLY)

    val fact = when {
        threshold != null -> {
            val before = row.recurrentAfterWeeks ?: row.weeks
            val trigger = row.recurrentAtPosition?.let { "#$it" } ?: "—"
            "$points points conservés et une place annuelle $annualRank, malgré l'arrêt au rang déclencheur $trigger après ${before.asWeeks()} " +
                "(seuil : ${threshold.minimumWeeks} semaines, sortie sous le Top ${threshold.belowPosition})"
        }

        row.weeksAt1 > 0 -> {
            val firstPlacePoints = formatCount(row.weeksAt1 * weeklyLimit)
            val pronoun = if (row.weeksAt1 == 1) "elle vaut" else "elles valent"
            "${row.weeksAt1.asWeeks()} au #1 du classement $chart; $pronoun $firstPlacePoints points " +
                "au barème Nova, pour $points cumulés et le rang annuel $annualRank"
        }

        row.peak in 1..5 ->
            "un pic hebdomadaire à $peak dans le Top 5 du classement $chart, $points points cumulés et la place annuelle $annualRank"

        row.peak in 6..10 ->
            "un passage dans le Top 10 du classement $chart, jusqu'au rang $peak, $points points au total et la place annuelle $annualRank"

        row.weeks >= 10 ->
            "${row.weeks.asWeeks()} créditées dans le classement $chart, $points points récoltés et la place annuelle $annualRank"

        else ->
            "un meilleur rang hebdomadaire de $peak, $points points cumulés et la place annuelle $annualRank"
    }

    val style = variant(entry, offset = 3)
    return when (entry.chart) {
        Chart.HOT_100 -> when (style) {
            0 -> "Exploit Hot 100 de $name : $fact."
            1 -> "La marque laissée par $name dans le Hot 100 — $fact."
            2 -> "Cette édition retiendra ceci pour $name : $fact."
            3 -> "Au palmarès Hot 100, le fait d'armes de $name est le suivant : $fact."
            4 -> "Le résultat qui distingue $name cette année, dans le Hot 100 : $fact."
            5 -> "Pour $name, l'exploit de la fenêtre se lit ainsi : $fact."
            6 -> "La ligne Year-End de $name signe ce repère dans le Hot 100 : $fact."
            else -> "En une ligne, la performance de $name au Hot 100 : $fact."
        }

        Chart.ARTIST_50 -> when (style) {
            0 -> "Exploit Artist 50 de $name : $fact."
            1 -> "La marque laissée par $name dans l'Artist 50 — $fact."
            2 -> "Cette édition retiendra ceci pour $name : $fact."
            3 -> "Au palmarès Artist 50, le fait d'armes de $name est le suivant : $fact."
            4 -> "Le résultat qui distingue $name cette année, dans l'Artist 50 : $fact."
            5 -> "Pour $name, l'exploit de la fenêtre se lit ainsi : $fact."
            6 -> "La ligne Year-End de $name signe ce repère dans l'Artist 50 : $fact."
            else -> "En une ligne, la performance de $name à l'Artist 50 : $fact."
        }

        Chart.ALBUMS_75 -> when (style) {
            0 -> "Exploit Albums 75 de $name : $fact."
            1 -> "La marque laissée par $name dans les Albums 75 — $fact."
            2 -> "Cette édition retiendra ceci pour $name : $fact."
            3 -> "Au palmarès Albums 75, le fait d'armes de $name est le suivant : $fact."
            4 -> "Le résultat qui distingue $name cette année, dans les Albums 75 : $fact."
            5 -> "Pour $name, l'exploit de la fenêtre se lit ainsi : $fact."
            6 -> "La ligne Year-End de $name signe ce repère dans les Albums 75 : $fact."
            else -> "En une ligne, la performance de $name dans les Albums 75 : $fact."
        }
    }
}

private fun chartName(chart: Chart): String = when (chart) {
    Chart.HOT_100 -> "Hot 100"
    Chart.ARTIST_50 -> "Artist 50"
    Chart.ALBUMS_75 -> "Albums 75"
}

/** Le rang annuel fait alterner les tournures des entrées voisines, de manière stable. */
private fun variant(entry: YearEndPopupEntry, offset: Int): Int =
    Math.floorMod(entry.rank - 1 + offset, 8)

private fun Int.asWeeks(): String = "$this ${if (this == 1) "semaine" else "semaines"}"
