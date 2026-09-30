package com.novastats.app.domain

/*
 * 🏅 Les 24 Records — catalogue (cahier des charges DEBUT, "ONGLET RECORDS").
 * Kotlin pur : définitions + moteur de calcul sur des séries de positions, testable en JVM.
 */

/** Catégorie d'entité d'un record. */
enum class RecordCategory(val dbName: String, val label: String, val emoji: String) {
    TRACK("TRACK", "Titres", "🎵"), ARTIST("ARTIST", "Artistes", "🎤"), ALBUM("ALBUM", "Albums", "💿")
}

/** Sous-section (2e dimension) d'un record. */
data class RecordSub(val dbName: String, val label: String)

/** Type d'unité pour l'affichage de la valeur. */
enum class RecordUnit { PERIODS, COUNT, PLAYS, POSITIONS, DURATION_MS, TIMES }

/**
 * Définition d'un record.
 * @property periods   périodes disponibles (vide = pas de sélecteur de période)
 * @property subs      sous-sections par catégorie (vide = pas de sélecteur)
 * @property ascending true = la plus petite valeur gagne (temps le plus court…)
 */
data class RecordDef(
    val number: Int,
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val periods: List<Period>,
    val categories: List<RecordCategory>,
    val subs: Map<RecordCategory, List<RecordSub>> = emptyMap(),
    val unit: RecordUnit = RecordUnit.PERIODS,
    val ascending: Boolean = false,
    /** Plusieurs records partagent un même écran (onglets principaux). */
    val screen: String = id
)

object RecordCatalog {
    private val chartPeriods = listOf(Period.DAILY, Period.WEEKLY, Period.MONTHLY, Period.YEARLY)
    private val all3 = listOf(RecordCategory.TRACK, RecordCategory.ARTIST, RecordCategory.ALBUM)
    private val artistAlbum = listOf(RecordCategory.ARTIST, RecordCategory.ALBUM)
    private val songsAlbums = listOf(RecordSub("SONGS", "Songs"), RecordSub("ALBUMS", "Albums"))
    private val artistSongsAlbums = mapOf(RecordCategory.ARTIST to songsAlbums)
    val certLevels = CertLevel.entries.map { RecordSub(it.dbName, "${it.emoji} ${it.label}") }
    val pantheonLevels = PantheonStatus.entries.map { RecordSub(it.dbName, "${it.emoji} ${it.label}") }
    val zones = listOf(RecordSub("TOP5", "🔝 Top 5"), RecordSub("TOP10", "🔥 Top 10"), RecordSub("TOP20", "📊 Top 20"), RecordSub("TOP50", "📈 Top 50"), RecordSub("ALL", "🌍 Top Global"))
    val consistencySubs = listOf(RecordSub("TOP5", "🏆 Top 5"), RecordSub("TOP10", "🔥 Top 10"), RecordSub("CHART", "📊 Total Chart"))
    val multiChartLevels = listOf(RecordSub("DWMY", "🌍 D+W+M+Y"), RecordSub("DWM", "🔥 D+W+M"), RecordSub("DW", "⚡ D+W"), RecordSub("WM", "⚡ W+M"), RecordSub("MY", "⚡ M+Y"))
    val globalSubs = listOf(RecordSub("TRIPLE_DEBUT", "🌍 Triple Debut"), RecordSub("LEGENDARY_RUN", "🏅 Legendary Run"), RecordSub("ALL", "🌍 All Global"))

    val ALL: List<RecordDef> = listOf(
        RecordDef(1, "MOST_CUMULATIVE", "📆", "Most Cumulative", "Top 10 des éléments avec le plus de jours / semaines / mois dans le chart", chartPeriods, all3),
        RecordDef(2, "MOST_CUMULATIVE_TOP10", "🔟", "Most Cumulative in Top 10", "Le plus de périodes passées dans le Top 10", chartPeriods, all3),
        RecordDef(3, "MOST_TIME_AT_1", "👑", "Most Time in #1", "Le plus de périodes passées à la première place", chartPeriods, all3),
        RecordDef(4, "MOST_SONGS_IN_CHART", "🎼", "Most Songs in the Charts", "Artistes / albums avec le plus de titres distincts classés", chartPeriods, artistAlbum, artistSongsAlbums, RecordUnit.COUNT),
        RecordDef(5, "MOST_SONGS_TOP10", "🔥", "Most Songs in Top 10", "Le plus de titres distincts dans le Top 10", chartPeriods, artistAlbum, artistSongsAlbums, RecordUnit.COUNT),
        RecordDef(6, "MOST_SONGS_AT_1", "🥇", "Most Songs in #1", "Le plus de titres distincts arrivés au #1", chartPeriods, artistAlbum, artistSongsAlbums, RecordUnit.COUNT),
        RecordDef(7, "MOST_DEBUT_1", "🚀", "Most Debut #1", "Le plus de titres entrés directement au #1", chartPeriods, artistAlbum, artistSongsAlbums, RecordUnit.COUNT),
        RecordDef(8, "MOST_DEBUT_TOP10", "✨", "Most Debut Top 10", "Le plus de titres entrés directement dans le Top 10", chartPeriods, artistAlbum, artistSongsAlbums, RecordUnit.COUNT),
        RecordDef(9, "FASTEST_CERT", "⚡", "Fastest Certification", "Certification obtenue le plus vite depuis la première écoute", emptyList(), listOf(RecordCategory.TRACK, RecordCategory.ALBUM),
            mapOf(RecordCategory.TRACK to certLevels, RecordCategory.ALBUM to certLevels), RecordUnit.DURATION_MS, ascending = true, screen = "FASTEST"),
        RecordDef(9, "FASTEST_PANTHEON", "👑", "Fastest Panthéon", "Statut Panthéon atteint le plus vite depuis la première écoute", emptyList(), listOf(RecordCategory.ARTIST),
            mapOf(RecordCategory.ARTIST to pantheonLevels), RecordUnit.DURATION_MS, ascending = true, screen = "FASTEST"),
        RecordDef(10, "BIGGEST_PERIOD", "📈", "Biggest Day / Week / Month", "Le plus d'écoutes en une seule journée / semaine / mois", listOf(Period.DAILY, Period.WEEKLY, Period.MONTHLY), all3, unit = RecordUnit.PLAYS),
        RecordDef(11, "BIGGEST_DEBUT", "💥", "Biggest Debut", "Le plus d'écoutes lors de la première apparition dans le chart", chartPeriods, all3, unit = RecordUnit.PLAYS),
        RecordDef(12, "MOST_CERTIFICATIONS", "💎", "Most Certifications", "Le plus de certifications obtenues, par niveau", emptyList(), artistAlbum,
            mapOf(RecordCategory.ARTIST to certLevels.flatMap { l -> songsAlbums.map { s -> RecordSub("${s.dbName}_${l.dbName}", "${s.label} · ${l.label}") } }, RecordCategory.ALBUM to certLevels), RecordUnit.COUNT),
        RecordDef(13, "MOST_HOF", "🏛️", "Most Hall of Fame Entries", "Le plus d'entrées au Hall of Fame (hebdo + mensuel)", emptyList(), all3, unit = RecordUnit.COUNT, screen = "HOF_GLOBAL"),
        RecordDef(14, "MOST_GLOBAL", "🌍", "Most Global Entries", "Le plus d'entrées Global (Triple Debut, Legendary Run)", emptyList(), all3,
            mapOf(RecordCategory.TRACK to globalSubs, RecordCategory.ARTIST to globalSubs, RecordCategory.ALBUM to globalSubs), RecordUnit.COUNT, screen = "HOF_GLOBAL"),
        RecordDef(15, "BIGGEST_COMEBACK", "🔄", "Biggest Comeback", "Plus grande remontée lors d'un retour après absence (5 j / 3 sem. / 2 mois / 1 an)", chartPeriods, all3, unit = RecordUnit.POSITIONS),
        RecordDef(16, "FASTEST_RISE", "🏎️", "Fastest Rise", "Top 3 atteint le plus vite depuis l'entrée dans le chart", chartPeriods, all3, ascending = true),
        RecordDef(17, "MOST_CONSISTENT", "🧱", "Most Consistent", "Plus longue série consécutive dans le Top 5 / Top 10 / chart", chartPeriods, all3,
            mapOf(RecordCategory.TRACK to consistencySubs, RecordCategory.ARTIST to consistencySubs, RecordCategory.ALBUM to consistencySubs)),
        RecordDef(18, "BIGGEST_JUMP", "⬆️", "Biggest Jump", "Plus grande progression en une seule période", chartPeriods, all3, unit = RecordUnit.POSITIONS, screen = "JUMP_FALL"),
        RecordDef(19, "BIGGEST_FALL", "⬇️", "Biggest Fall", "Plus grande chute en une seule période", chartPeriods, all3, unit = RecordUnit.POSITIONS, screen = "JUMP_FALL"),
        RecordDef(20, "SLEEPER_HIT", "😴", "Sleeper Hit", "Entré très bas puis monté très haut après plusieurs périodes", chartPeriods, all3, unit = RecordUnit.POSITIONS, screen = "CLIMB"),
        RecordDef(20, "LONGEST_ROAD", "🛣️", "Longest Road to #1", "Le plus de périodes avant d'atteindre le #1", chartPeriods, all3, screen = "CLIMB"),
        RecordDef(20, "BIGGEST_CLIMBER", "🧗", "Biggest Climber", "Plus grand écart entre position d'entrée et peak", chartPeriods, all3, unit = RecordUnit.POSITIONS, screen = "CLIMB"),
        RecordDef(21, "MULTI_CHART", "🌐", "Multi-Chart Domination", "Présent simultanément dans plusieurs charts", emptyList(), all3,
            mapOf(RecordCategory.TRACK to multiChartLevels, RecordCategory.ARTIST to multiChartLevels, RecordCategory.ALBUM to multiChartLevels), RecordUnit.TIMES),
        RecordDef(22, "MOST_BLOCKED_TOP5", "🚧", "Most Weeks Blocked at Top 5", "Le plus de périodes dans le Top 5 sans jamais atteindre le #1", chartPeriods, all3),
        RecordDef(23, "MOST_SIMULTANEOUS", "🎯", "Most Simultaneous Songs", "Le plus de titres en même temps dans une zone du chart", chartPeriods, artistAlbum,
            mapOf(RecordCategory.ARTIST to songsAlbums.flatMap { s -> zones.map { z -> RecordSub("${s.dbName}_${z.dbName}", "${s.label} · ${z.label}") } }, RecordCategory.ALBUM to zones), RecordUnit.COUNT),
        RecordDef(24, "MOST_SUCCESSIVE_1", "🔁", "Most Successive #1", "Le plus de #1 successifs avec des titres différents, sans interruption", chartPeriods, artistAlbum, artistSongsAlbums, RecordUnit.COUNT)
    )

    fun byId(id: String): RecordDef? = ALL.firstOrNull { it.id == id }

    /** Écrans (un écran peut regrouper plusieurs records : Fastest, HoF/Global, Jump/Fall, Climb). */
    val screens: List<List<RecordDef>> = ALL.groupBy { it.screen }.values.toList()

    fun unitLabel(period: Period?, plural: Boolean): String = when (period) {
        Period.DAILY -> if (plural) "jours" else "jour"
        Period.WEEKLY -> if (plural) "semaines" else "semaine"
        Period.MONTHLY -> "mois"
        Period.YEARLY -> if (plural) "années" else "année"
        else -> if (plural) "périodes" else "période"
    }

    /** Unité adaptative : < 24 h → heures · 1-29 jours → jours · ≥ 30 jours → mois. */
    fun formatDurationAdaptive(ms: Long): String {
        val h = ms / 3_600_000.0
        return when {
            h < 24 -> "${h.toInt().coerceAtLeast(1)} h"
            h < 24 * 30 -> "${(h / 24).toInt()} j"
            else -> String.format(java.util.Locale.FRANCE, "%.1f mois", h / 24 / 30.44)
        }
    }

    /** Absence minimale pour un comeback (en nombre de périodes). */
    fun comebackMinAbsence(period: Period): Int = when (period) {
        Period.DAILY -> 5; Period.WEEKLY -> 3; Period.MONTHLY -> 2; Period.YEARLY -> 1; Period.GLOBAL -> 1
    }
}

/* =========================== MOTEUR (séries de positions) =========================== */

/** Une apparition dans un chart : index de période (0,1,2… consécutif dans le calendrier), position, écoutes. */
data class ChartAppearance(val periodIndex: Int, val date: String, val position: Int, val plays: Int)

/** Résultat d'un record pour une entité. */
data class RecordResult(val entityId: Long, val value: Double, val date: String? = null, val extra: String? = null)

/** Calculs purs à partir des séries de positions d'une entité (triées par periodIndex). */
object RecordMath {

    fun cumulative(series: List<ChartAppearance>, maxPosition: Int = Int.MAX_VALUE): Int = series.count { it.position <= maxPosition }

    fun timesAt1(series: List<ChartAppearance>): Int = series.count { it.position == 1 }

    /** Plus longue série d'apparitions consécutives (periodIndex contigus) à une position ≤ [maxPosition]. */
    fun longestStreak(series: List<ChartAppearance>, maxPosition: Int = Int.MAX_VALUE): Pair<Int, String?> {
        var best = 0; var bestEnd: String? = null
        var cur = 0; var prevIdx = Int.MIN_VALUE
        for (a in series) {
            cur = if (a.position <= maxPosition && a.periodIndex == prevIdx + 1) cur + 1 else if (a.position <= maxPosition) 1 else 0
            prevIdx = a.periodIndex
            if (cur > best) { best = cur; bestEnd = a.date }
        }
        return best to bestEnd
    }

    /** Plus grande remontée lors d'un retour après une absence ≥ [minAbsence] périodes : (gain, date de retour, "avant→après"). */
    fun biggestComeback(series: List<ChartAppearance>, minAbsence: Int): RecordResult? {
        var best: RecordResult? = null
        for (i in 1 until series.size) {
            val prev = series[i - 1]; val cur = series[i]
            val gap = cur.periodIndex - prev.periodIndex - 1
            if (gap >= minAbsence) {
                val gain = prev.position - cur.position
                if (gain > 0 && (best == null || gain > best.value)) best = RecordResult(0, gain.toDouble(), cur.date, "#${prev.position} → #${cur.position} après $gap périodes d'absence")
            }
        }
        return best
    }

    /** Nombre de périodes entre l'entrée et la première position ≤ [target] (null si jamais atteint). */
    fun periodsToReach(series: List<ChartAppearance>, target: Int): Pair<Int, String>? {
        if (series.isEmpty()) return null
        val entry = series.first().periodIndex
        val hit = series.firstOrNull { it.position <= target } ?: return null
        return (hit.periodIndex - entry) to hit.date
    }

    /** Plus grande progression (jump > 0) ou chute (fall > 0) entre deux périodes consécutives. */
    fun biggestMove(series: List<ChartAppearance>, jump: Boolean): RecordResult? {
        var best: RecordResult? = null
        for (i in 1 until series.size) {
            val prev = series[i - 1]; val cur = series[i]
            if (cur.periodIndex != prev.periodIndex + 1) continue
            val delta = if (jump) prev.position - cur.position else cur.position - prev.position
            if (delta > 0 && (best == null || delta > best.value)) best = RecordResult(0, delta.toDouble(), cur.date, "#${prev.position} → #${cur.position}")
        }
        return best
    }

    /** Sleeper hit : entré au-delà de la position 20, peak ≤ 10 atteint ≥ 3 périodes plus tard → écart entrée-peak. */
    fun sleeperHit(series: List<ChartAppearance>): RecordResult? {
        if (series.isEmpty()) return null
        val entry = series.first()
        if (entry.position <= 20) return null
        val peak = series.minByOrNull { it.position } ?: return null
        if (peak.position > 10 || peak.periodIndex - entry.periodIndex < 3) return null
        return RecordResult(0, (entry.position - peak.position).toDouble(), peak.date, "entré #${entry.position} → peak #${peak.position} en ${peak.periodIndex - entry.periodIndex} périodes")
    }

    fun biggestClimber(series: List<ChartAppearance>): RecordResult? {
        if (series.isEmpty()) return null
        val entry = series.first()
        val peak = series.minByOrNull { it.position } ?: return null
        val climb = entry.position - peak.position
        if (climb <= 0) return null
        return RecordResult(0, climb.toDouble(), peak.date, "entré #${entry.position} → peak #${peak.position}")
    }

    /** Périodes dans le Top 5 pour une entité n'ayant jamais atteint le #1 : (n, meilleure position). */
    fun blockedTop5(series: List<ChartAppearance>): Pair<Int, Int>? {
        val peak = series.minOfOrNull { it.position } ?: return null
        if (peak == 1) return null
        val n = series.count { it.position <= 5 }
        return if (n > 0) n to peak else null
    }

    /**
     * #1 successifs : plus longue suite de périodes consécutives où le #1 appartient au même propriétaire
     * (artiste / album) — valeur = nombre de titres DIFFÉRENTS au #1 dans cette suite.
     * @param numberOnes liste (periodIndex, date, entityId du #1, ownerIds du #1)
     */
    fun successiveNumberOnes(numberOnes: List<NumberOne>): Map<Long, RecordResult> {
        val best = HashMap<Long, RecordResult>()
        val owners = numberOnes.flatMap { it.ownerIds }.toSet()
        for (owner in owners) {
            var runTitles = LinkedHashSet<Long>(); var prevIdx = Int.MIN_VALUE; var runStart: String? = null
            fun flush(endDate: String?) {
                if (runTitles.size > 1 || (runTitles.size == 1 && best[owner] == null)) {
                    val cur = best[owner]
                    if (cur == null || runTitles.size > cur.value) best[owner] = RecordResult(owner, runTitles.size.toDouble(), endDate, "${runTitles.size} #1 différents · ${runStart ?: ""} → ${endDate ?: ""}")
                }
            }
            var lastDate: String? = null
            for (n in numberOnes) {
                if (owner in n.ownerIds && n.periodIndex == prevIdx + 1) {
                    runTitles.add(n.entityId)
                } else if (owner in n.ownerIds) {
                    flush(lastDate); runTitles = linkedSetOf(n.entityId); runStart = n.date
                } else {
                    flush(lastDate); runTitles = LinkedHashSet(); runStart = null
                }
                prevIdx = n.periodIndex; lastDate = n.date
            }
            flush(lastDate)
        }
        return best
    }

    data class NumberOne(val periodIndex: Int, val date: String, val entityId: Long, val ownerIds: Set<Long>)
}
