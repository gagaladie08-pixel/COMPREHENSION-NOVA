package com.novastats.app.domain

/**
 * ⚠️ À corriger — règles d'entrée dans la file de révision (par écoute).
 *
 * Score par source : MediaSession 100 · Mixte 80 · Notification complète 70 · incomplète 50.
 * Titre ou artiste « Unknown / Inconnu » → 50 max. Score < 70 → 🔴 À corriger.
 * (Les scores API 70-89 → 🟡 À vérifier sont gérés au niveau du titre par l'enrichissement.)
 */
object ReviewRules {

    data class Verdict(val score: Int, val reason: String?) {
        val needsReview: Boolean get() = score < 70
    }

    private val unknownWords = setOf(
        "unknown", "unknown artist", "unknown title", "unknown track", "inconnu", "artiste inconnu", "titre inconnu",
        "untitled", "track 1", "track 01", "audio", "video", "<unknown>", "n/a", "na", "none", "null"
    )

    fun isUnknown(value: String?): Boolean {
        if (value.isNullOrBlank()) return true
        val k = TitleNormalizer.normalizeKey(value)
        return k.isBlank() || k in unknownWords || k.startsWith("unknown ") || k.startsWith("track ") && k.length <= 9
    }

    fun sourceScore(detectionSource: String?, complete: Boolean): Int = when (detectionSource) {
        "MEDIA_SESSION" -> 100
        "MIXED" -> 80
        "IMPORT" -> 100
        else -> if (complete) 70 else 50
    }

    /** Évalue une écoute à l'enregistrement. [title] non vide (titre vide + artiste vide = jamais enregistré en amont). */
    fun evaluate(title: String?, artist: String?, detectionSource: String?): Verdict {
        val titleBad = isUnknown(title)
        val artistBad = isUnknown(artist)
        val base = sourceScore(detectionSource, complete = !artistBad && !titleBad)
        return when {
            titleBad && artistBad -> Verdict(minOf(base, 30), "Titre et artiste inconnus")
            titleBad -> Verdict(minOf(base, 50), "Titre inconnu")
            artistBad -> Verdict(minOf(base, 50), "Artiste manquant")
            base < 70 -> Verdict(base, "Score de confiance $base %")
            else -> Verdict(base, null)
        }
    }

    /** Libellé lisible d'une source de détection. */
    fun sourceLabel(detectionSource: String?): String = when (detectionSource) {
        "MEDIA_SESSION" -> "MediaSession"; "NOTIFICATION" -> "Notification"; "MIXED" -> "Mixte"; "IMPORT" -> "Import"; null -> "—"; else -> detectionSource
    }
}
