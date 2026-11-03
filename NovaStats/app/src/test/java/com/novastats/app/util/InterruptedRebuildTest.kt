package com.novastats.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * La règle qui déclenche la réparation au lancement. Sans elle, un recalcul tué en route laissait
 * les classements vides jusqu'à la prochaine écoute — le symptôme « l'app se vide ».
 *
 * Les lignes reproduisent le format réel écrit par `DetectionState.log` :
 * `« dd/MM HH:mm:ss  <message> »`, journal trié du plus récent au plus ancien.
 */
class InterruptedRebuildTest {

    @Test
    fun `etape en suspens = recalcul a reparer`() {
        val log = listOf(
            "07/10 13:02:11  🔎 État au lancement — écoutes=10980/11434 · daily=10978",
            "07/10 12:40:03  🧮 3/8 Sessions — écoutes=10980/11434 · daily=0",
            "07/10 12:40:01  🧮 2/8 Streaks — écoutes=10980/11434 · daily=0",
            "07/10 12:40:00  🧮 Recalcul lancé (complet=true)"
        )
        assertEquals("3/8 Sessions", InterruptedRebuild.detect(log))
    }

    @Test
    fun `premiere etape interrompue = le cas reel vu sur appareil en 0_22_15`() {
        // Le journal appareil de la 0.22.15 s'arrêtait exactement ici : process tué pendant l'étape 1.
        val log = listOf(
            "07/10 09:15:44  🧮 1/8 Versions + agrégats + écoutes quotidiennes — écoutes=10979/11433 · daily=10978",
            "07/10 09:15:40  🧮 Recalcul lancé (complet=true)"
        )
        assertEquals("1/8 Versions + agrégats + écoutes quotidiennes", InterruptedRebuild.detect(log))
    }

    @Test
    fun `recalcul termine = rien a reparer`() {
        val log = listOf(
            "07/10 13:05:00  🔎 État au lancement — écoutes=10980/11434",
            "07/10 12:41:10  ✅ Recalcul terminé (complet=true) — écoutes=10980/11434",
            "07/10 12:40:03  🧮 3/8 Sessions — écoutes=10980/11434"
        )
        assertNull(InterruptedRebuild.detect(log))
    }

    @Test
    fun `recalcul en erreur = deja signale, on ne boucle pas`() {
        // rebuildAll journalise l'échec : relancer en boucle à chaque ouverture n'aiderait pas.
        val log = listOf(
            "07/10 12:41:10  ❌ Recalcul interrompu (SQLiteFullException: database is full) — écoutes=10980/11434",
            "07/10 12:40:03  🧮 3/8 Sessions — écoutes=10980/11434"
        )
        assertNull(InterruptedRebuild.detect(log))
    }

    @Test
    fun `lancement sans etape atteinte = rien a reparer`() {
        // « 🧮 Recalcul lancé » précède toute étape : il ne dit pas où l'on s'est arrêté,
        // et le traiter comme une étape déclencherait une réparation à tort.
        val log = listOf("07/10 12:40:00  🧮 Recalcul lancé (complet=true)")
        assertNull(InterruptedRebuild.detect(log))
    }

    @Test
    fun `journal vide ou sans recalcul = rien a reparer`() {
        assertNull(InterruptedRebuild.detect(emptyList()))
        assertNull(
            InterruptedRebuild.detect(
                listOf(
                    "07/10 13:02:11  🔎 État au lancement — écoutes=10980/11434",
                    "07/10 13:01:02  🔗 Reliason terminée : 0 déplacée(s), 0 doublon(s) supprimé(s)"
                )
            )
        )
    }

    @Test
    fun `seul le dernier evenement de recalcul compte`() {
        // Un recalcul interrompu PUIS un recalcul terminé : la réparation n'a plus lieu d'être.
        val log = listOf(
            "07/10 14:00:00  ✅ Recalcul terminé (complet=true)",
            "07/10 13:00:00  🧮 5/8 Panthéon (vide pantheon_status puis recalcule) — écoutes=10980/11434"
        )
        assertNull(InterruptedRebuild.detect(log))
    }

    @Test
    fun `le comptage apres le tiret est retire du libelle`() {
        val log = listOf("07/10 12:40:07  🧮 6/8 Billboard — écoutes=10980/11434 · titres=1091 · orphelines=0")
        assertEquals("6/8 Billboard", InterruptedRebuild.detect(log))
    }
}
