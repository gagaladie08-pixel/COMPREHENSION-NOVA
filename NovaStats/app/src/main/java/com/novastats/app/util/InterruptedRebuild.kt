package com.novastats.app.util

/**
 * Détecte, à la lecture du journal, un recalcul global qui n'est pas allé au bout.
 *
 * Ce fichier ne référence aucun type Android : la règle est ainsi couverte par un test unitaire
 * JVM (`InterruptedRebuildTest`), exécuté par la CI à chaque build.
 *
 * Contrat de `StatsRebuilder` :
 *  - chaque étape est journalisée AVANT d'être exécutée → `🧮 k/8 <libellé>` ;
 *  - `✅ Recalcul terminé` n'est écrit qu'une fois les 8 étapes passées ;
 *  - une exception produit `❌ Recalcul interrompu`.
 *
 * Un process tué en route laisse donc une étape `🧮` comme dernier événement, sans `✅` ni `❌`.
 */
object InterruptedRebuild {

    const val STEP_MARK = "🧮"
    const val DONE_MARK = "✅ Recalcul terminé"
    const val FAILED_MARK = "❌ Recalcul interrompu"
    const val START_MARK = "🧮 Recalcul lancé"

    /**
     * @param log journal trié du plus récent au plus ancien (`DetectionState.Snapshot.log`).
     * @return le libellé de l'étape laissée en suspens (« 3/8 Sessions »), ou null si le dernier
     *         recalcul connu s'est terminé — ou s'il n'y en a jamais eu.
     *
     * La première ligne portant une marque de recalcul est le dernier événement connu. On ignore
     * la ligne `🧮 Recalcul lancé` : elle précède toute étape et ne dit pas où l'on s'est arrêté.
     */
    fun detect(log: List<String>): String? {
        for (line in log) {
            if (line.contains(DONE_MARK) || line.contains(FAILED_MARK)) return null
            if (!line.contains(STEP_MARK) || line.contains(START_MARK)) continue
            val label = line.substringAfter(STEP_MARK).trim()
            // « 3/8 Sessions — écoutes=10980/11434 · … » → « 3/8 Sessions »
            return label.substringBefore("—").trim().ifEmpty { label }
        }
        return null
    }
}
