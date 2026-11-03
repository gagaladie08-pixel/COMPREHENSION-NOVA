package com.novastats.app.util

/**
 * 🐢 Détection d'un recalcul anormalement lent, à partir du journal déjà écrit par `StatsRebuilder`.
 *
 * La 0.22.15 a failli perdre la bibliothèque à cause d'un `UPDATE` corrélé en O(n²) : 1 091 titres
 * et 11 434 écoutes suffisaient à dépasser dix minutes, et le process était tué avant la fin.
 * Mesuré sur SQLite 3.40.1 : 2 168 ms pour 100 titres, 14 975 ms pour 200, 121 482 ms pour 400,
 * inachevé au-delà de 600 s à taille réelle. Le correctif (CTE + `GROUP BY`) ramène le même
 * travail à 57 ms — mais la bibliothèque grandit tous les jours, et rien ne signalerait une
 * nouvelle régression avant le gel de l'app.
 *
 * `StatsRebuilder` journalise déjà `⏱️ Étape 1 terminée en N ms`. Ce fichier interprète cette
 * ligne. Il ne référence aucun type Android : la règle est couverte par un test unitaire JVM.
 */
object RebuildHealth {

    /**
     * Au-delà, on prévient. L'étape 1 est la seule en O(n) sur les écoutes ; le CTE actuel met
     * ~57 ms pour 11 434 écoutes, donc 15 s laisse une marge de plus de 250× avant l'alerte —
     * assez pour ne jamais crier au loup, assez tôt pour prévenir avant le gel.
     */
    const val SLOW_STEP1_MS = 15_000L

    private val STEP1_PATTERN = Regex("""Étape\s+1\s+terminée\s+en\s+(\d+)\s*ms""")

    /**
     * @param log journal trié du plus récent au plus ancien (`DetectionState.Snapshot.log`).
     * @return la durée de l'étape 1 du **dernier** recalcul connu, ou null si aucune mesure.
     *
     * Repérage par motif plutôt que par préfixe littéral : la ligne commence par un emoji `⏱️`
     * porteur d'un sélecteur de variante, et une simple différence d'encodage entre la source et
     * la chaîne journalisée suffirait à rendre la détection muette.
     */
    fun lastStep1Ms(log: List<String>): Long? {
        for (line in log) {
            val match = STEP1_PATTERN.find(line) ?: continue
            return match.groupValues[1].toLongOrNull()
        }
        return null
    }

    /** Vrai si cette durée justifie d'avertir. Séparé pour pouvoir faire évoluer le seuil sans repérer les lignes. */
    fun isSlow(ms: Long?): Boolean = ms != null && ms >= SLOW_STEP1_MS

    /**
     * Une même ligne ne doit alerter qu'une fois : sans cela, l'app renotifierait à chaque
     * ouverture tant qu'aucun nouveau recalcul n'a eu lieu.
     *
     * @param lastNotified ligne déjà signalée (null si jamais).
     * @return true si cette ligne doit produire une alerte.
     */
    fun shouldAlert(line: String?, lastNotified: String?): Boolean =
        line != null && line != lastNotified && isSlow(lastStep1Ms(listOf(line)))

    /** Formate une durée pour l'alerte : « < 1 s », « 42 s » ou « 1 min 30 s ». */
    fun humanize(ms: Long): String {
        val totalSeconds = ms / 1000  // division entière : 57 ms → 0 s
        return when {
            totalSeconds < 1 -> "< 1 s"
            totalSeconds < 60 -> "$totalSeconds s"
            else -> "${totalSeconds / 60} min ${totalSeconds % 60} s"
        }
    }
}
