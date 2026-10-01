package com.novastats.app.domain

import java.time.LocalDate

/**
 * 🏆 Nova Awards — catalogue des 9 récompenses annuelles (cahier des charges DEBUT §Nova Awards).
 * Les 3 premières sont des cartes « Hero » (plus grandes), couleur or.
 */
enum class AwardCategory(val dbName: String, val emoji: String, val title: String, val colorHex: String, val hero: Boolean, val logic: String) {
    SONG_OF_YEAR("SONG_OF_YEAR", "🎵", "Chanson de l'année", "#FFD700", true, "#1 du classement annuel"),
    ARTIST_OF_YEAR("ARTIST_OF_YEAR", "🎤", "Artiste de l'année", "#FFD700", true, "#1 du classement annuel"),
    ALBUM_OF_YEAR("ALBUM_OF_YEAR", "💿", "Album de l'année", "#FFD700", true, "#1 du classement annuel"),
    BIGGEST_RISE("BIGGEST_RISE", "📈", "Plus grosse progression", "#2ECC71", false, "Ratio écoutes des 30 derniers jours vs avant"),
    REVELATION("REVELATION", "🆕", "Révélation de l'année", "#9B59B6", false, "Artiste découvert il y a moins de 6 mois le plus écouté"),
    LOYALTY("LOYALTY", "🤝", "Meilleure fidélité", "#3498DB", false, "Artiste présent le plus grand nombre de mois distincts"),
    BEST_CERTIFICATION("BEST_CERTIFICATION", "💎", "Meilleure certification", "#00FFFF", false, "Chanson certifiée avec le plus d'écoutes"),
    LONGEST_STREAK("LONGEST_STREAK", "🔥", "Plus long streak", "#E74C3C", false, "Max de jours consécutifs avec au moins 1 écoute"),
    LONGEST_SESSION("LONGEST_SESSION", "⏱️", "Session la plus longue", "#E67E22", false, "Session continue la plus longue (pause max 15 min)");

    companion object {
        fun fromDb(name: String?) = entries.firstOrNull { it.dbName == name }
    }
}

object AwardRules {
    /** Déblocage : 2 mois d'utilisation. */
    const val UNLOCK_DAYS = 60L

    fun unlockDate(firstPlay: LocalDate): LocalDate = firstPlay.plusDays(UNLOCK_DAYS)
    fun isUnlocked(firstPlay: LocalDate?, today: LocalDate): Boolean = firstPlay != null && !today.isBefore(unlockDate(firstPlay))

    /** Plus longue suite de jours consécutifs dans une liste de dates ISO triées. */
    fun longestStreak(dates: List<String>): Pair<Int, String?> {
        var best = 0; var bestEnd: String? = null; var run = 0; var prev: LocalDate? = null
        for (iso in dates) {
            val d = LocalDate.parse(iso)
            run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
            if (run > best) { best = run; bestEnd = iso }
            prev = d
        }
        return best to bestEnd
    }

    /** Ratio de progression : écoutes récentes / écoutes anciennes (anciennes = 0 → récentes × 2 pour primer les vraies découvertes). */
    fun riseScore(recent: Int, earlier: Int): Double = if (recent < 5) 0.0 else if (earlier == 0) recent * 2.0 else recent.toDouble() / earlier
}
