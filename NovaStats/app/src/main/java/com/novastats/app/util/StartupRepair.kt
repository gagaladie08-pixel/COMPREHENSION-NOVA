package com.novastats.app.util

import com.novastats.app.NovaStatsApp
import com.novastats.app.service.DetectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 🩹 Répare au lancement un recalcul qui n'est pas allé au bout.
 *
 * Chaque étape du recalcul est journalisée AVANT d'être exécutée (`StatsRebuilder.audit`), et une
 * ligne `✅ Recalcul terminé` n'est écrite qu'à la fin. Si le process est tué en route (batterie,
 * mémoire, arrêt forcé), la dernière ligne du journal nomme donc une étape `🧮 k/8` sans `✅`.
 *
 * Sans cette vérification, les tables dérivées restaient partiellement vides — classements,
 * certifications, records — **jusqu'à la prochaine écoute**, ce qui ressemble exactement au
 * symptôme « l'app se vide ». Le journal d'audit de la 0.22.15 l'a montré sur appareil :
 * `🧮 1/8` puis plus rien, alors qu'aucune écoute n'avait été supprimée.
 *
 * La réparation relance un recalcul complet. Elle est déclenchée au plus une fois par process :
 * si le recalcul échoue à nouveau, on ne boucle pas à chaque lancement — l'utilisateur garde la
 * main via Réglages → Données → Recalculer.
 */
object StartupRepair {

    /** Une tentative par process, même si l'application est recréée (changement de thème, etc.). */
    @Volatile
    private var attempted = false

    /** Garde-fou supplémentaire : une seule réparation à la fois, quel que soit l'appelant. */
    private val mutex = Mutex()

    /**
     * @return le libellé de l'étape laissée en suspens, ou null si rien à réparer.
     *
     * Délègue à [InterruptedRebuild.detect], qui ne dépend pas d'Android et est donc testé sur JVM.
     */
    fun detectInterrupted(log: List<String>): String? = InterruptedRebuild.detect(log)

    /** Vérifie le journal et relance un recalcul complet si besoin. À appeler sur Dispatchers.IO. */
    suspend fun run(app: NovaStatsApp) {
        withContext(Dispatchers.IO) {
            if (attempted) return@withContext
            attempted = true
            mutex.withLock {
                val pending = runCatching { detectInterrupted(DetectionState.state.value.log) }.getOrNull()
                    ?: return@withLock

                val context = app.applicationContext
                RebuildAudit.write(context, "🩹 Recalcul interrompu à l'étape « $pending » : réparation au lancement")
                runCatching { app.rebuilder.rebuildAll() }
                    .onFailure { t ->
                        // rebuildAll journalise déjà son échec ; on ajoute seulement l'origine.
                        RebuildAudit.write(context, "🩹 Réparation au lancement en échec (${t.javaClass.simpleName})")
                    }
            }
        }
    }
}
