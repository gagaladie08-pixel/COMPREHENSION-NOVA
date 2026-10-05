package com.novastats.app.domain

/**
 * Règles locales du Year-End Nova, inspirées des seuils de récurrence du Hot 100.
 *
 * Les semaines de récurrence sont comptées dans la fenêtre Year-End sélectionnée ; ce n'est pas
 * l'historique de semaines d'un chart Billboard officiel.
 */
object YearEndRules {
    data class RecurrentThreshold(val minimumWeeks: Int, val belowPosition: Int)

    /**
     * Après le nombre de semaines indiqué, un rang numérique supérieur au seuil protégé stoppe
     * le cumul de points pour le reste de cette fenêtre annuelle.
     */
    val recurrentThresholds = listOf(
        RecurrentThreshold(minimumWeeks = 20, belowPosition = 50),
        RecurrentThreshold(minimumWeeks = 26, belowPosition = 25),
        RecurrentThreshold(minimumWeeks = 52, belowPosition = 10)
    )

    fun becomesRecurrent(weeksAlreadyCounted: Int, position: Int): Boolean =
        recurrentThresholds.any { threshold ->
            weeksAlreadyCounted >= threshold.minimumWeeks && position > threshold.belowPosition
        }

    /** Points du barème inversé Nova pour une position valide du chart. */
    fun pointsFor(position: Int, limit: Int): Int =
        if (limit <= 0 || position !in 1..limit) 0 else limit + 1 - position
}
