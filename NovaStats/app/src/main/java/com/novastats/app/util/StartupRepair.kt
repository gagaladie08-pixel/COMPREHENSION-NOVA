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
                val context = app.applicationContext
                val pending = runCatching { detectInterrupted(DetectionState.state.value.log) }.getOrNull()
                if (pending != null) {
                    RebuildAudit.write(context, "🩹 Recalcul interrompu à l'étape « $pending » : réparation au lancement")
                    val rebuilt = runCatching { app.rebuilder.rebuildAll() }
                    if (rebuilt.isFailure) {
                        val t = rebuilt.exceptionOrNull()!!
                        // rebuildAll journalise déjà son échec ; on ajoute seulement l'origine.
                        RebuildAudit.write(context, "🩹 Réparation au lancement en échec (${t.javaClass.simpleName})")
                        return@withLock
                    }
                }

                // La migration v11 crée un historique Fastest distinct. Le remplir une fois sans
                // lancer de recalcul global : certifications, écoutes et records sans rapport restent intacts.
                val prefs = context.getSharedPreferences("nova_startup_repair", android.content.Context.MODE_PRIVATE)
                if (!prefs.getBoolean("fastest_pantheon_v11_done", false)) {
                    try {
                        RebuildAudit.write(context, "⚡ Réparation ciblée Fastest Panthéon : recalcul des dates et de la voie d'accès")
                        app.rebuilder.rebuildFastestRecordsOnly()
                        RebuildAudit.write(context, "✅ Fastest Panthéon et ses deux caches recalculés")
                        prefs.edit().putBoolean("fastest_pantheon_v11_done", true).apply()
                    } catch (t: Throwable) {
                        RebuildAudit.write(context, "⚠️ Réparation ciblée Fastest Panthéon en échec (${t.javaClass.simpleName}) — nouvel essai au prochain lancement")
                    }
                }

                // Le seuil Most Successive #1 passe à deux éléments distincts. Remplacer uniquement
                // son cache existant afin d'éliminer les entrées héritées à un seul titre / album.
                if (!prefs.getBoolean("successive_no1_threshold_v1_done", false)) {
                    try {
                        RebuildAudit.write(context, "🔁 Réparation ciblée Most Successive #1 : séries continues de deux éléments distincts minimum")
                        app.rebuilder.rebuildSuccessiveRecordsOnly()
                        RebuildAudit.write(context, "✅ Cache Most Successive #1 recalculé pour les quatre périodes")
                        prefs.edit().putBoolean("successive_no1_threshold_v1_done", true).apply()
                    } catch (t: Throwable) {
                        RebuildAudit.write(context, "⚠️ Réparation ciblée Most Successive #1 en échec (${t.javaClass.simpleName}) — nouvel essai au prochain lancement")
                    }
                }
            }
        }
    }
}
