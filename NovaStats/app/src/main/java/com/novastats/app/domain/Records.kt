package com.novastats.app.domain

/*
 * 🏅 Les 30 Records — catalogue (cahier des charges DEBUT, "ONGLET RECORDS").
 * Kotlin pur : définitions + moteur de calcul sur des séries de positions, testable en JVM.
 */

/** Catégorie d'entité d'un record. */
enum class RecordCategory(val dbName: String, val label: String, val emoji: String) {
    TRACK("TRACK", "Titres", "🎵"), ARTIST("ARTIST", "Artistes", "🎤"), ALBUM("ALBUM", "Albums", "💿")
}

/** Sous-section (2e dimension) d'un record. */
data class RecordSub(val dbName: String, val label: String)

/** Type d'unité pour l'affichage de la valeur. */
enum class RecordUnit { PERIODS, COUNT, PLAYS, POSITIONS, DURATION_MS, TIMES, DAYS }

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

/** Familles de records (onglet Records : puces horizontales façon niveaux de certification). */
enum class RecordGroup(val emoji: String, val label: String, val description: String) {
    PALMARES("🏆", "Palmarès", "La synthèse : qui détient le plus de #1 dans tous les classements de records"),
    DURATION("⏳", "Durée dans le chart", "Combien de temps un élément est resté classé, dans le Top 10, au #1…"),
    MOVEMENT("📈", "Mouvements de position", "Les plus grands bonds, chutes, retours et ascensions"),
    DEBUT("🚀", "Débuts", "Les entrées les plus fortes dans le chart"),
    SPEED("⚡", "Vitesse", "Les certifications et statuts Panthéon atteints le plus vite"),
    TOTALS("🧮", "Cumuls", "Les plus gros volumes : écoutes, certifications, Hall of Fame, Global, multi-charts"),
    DOMINATION("🎼", "Domination", "Les artistes / albums qui occupent le chart avec plusieurs titres à la fois"),
    LISTENING("🎧", "Écoute", "Les séries d'écoute, indépendantes des charts : jours consécutifs avec au moins une écoute")
}

object RecordCatalog {
    /** Groupe de chaque record (ordre d'affichage dans l'onglet). */
    val groupOf: Map<String, RecordGroup> = mapOf(
        "MOST_CUMULATIVE" to RecordGroup.DURATION, "MOST_CUMULATIVE_TOP10" to RecordGroup.DURATION, "MOST_TIME_AT_1" to RecordGroup.DURATION,
        "MOST_CONSISTENT" to RecordGroup.DURATION, "MOST_BLOCKED_TOP5" to RecordGroup.DURATION,
        "BIGGEST_JUMP" to RecordGroup.MOVEMENT, "BIGGEST_FALL" to RecordGroup.MOVEMENT, "BIGGEST_COMEBACK" to RecordGroup.MOVEMENT, "BIGGEST_CLIMBER" to RecordGroup.MOVEMENT,
        "SLEEPER_HIT" to RecordGroup.MOVEMENT, "FASTEST_RISE" to RecordGroup.MOVEMENT, "LONGEST_ROAD" to RecordGroup.MOVEMENT,
        "MOST_DEBUT_1" to RecordGroup.DEBUT, "MOST_DEBUT_TOP10" to RecordGroup.DEBUT, "BIGGEST_DEBUT" to RecordGroup.DEBUT,
        "FASTEST_CERT" to RecordGroup.SPEED, "FASTEST_PANTHEON" to RecordGroup.SPEED,
        "BIGGEST_PERIOD" to RecordGroup.TOTALS, "MOST_CERTIFICATIONS" to RecordGroup.TOTALS, "MOST_HOF" to RecordGroup.TOTALS, "MOST_GLOBAL" to RecordGroup.TOTALS, "MULTI_CHART" to RecordGroup.TOTALS,
        "MOST_SONGS_IN_CHART" to RecordGroup.DOMINATION, "MOST_SONGS_TOP10" to RecordGroup.DOMINATION, "MOST_SONGS_AT_1" to RecordGroup.DOMINATION,
        "MOST_SIMULTANEOUS" to RecordGroup.DOMINATION, "MOST_SUCCESSIVE_1" to RecordGroup.DOMINATION,
        // 25-30 (extension)
        "LONGEST_LIFESPAN" to RecordGroup.DURATION,
        "MOST_REENTRIES" to RecordGroup.MOVEMENT, "LONGEST_ABSENCE_RETURN" to RecordGroup.MOVEMENT,
        "LONGEST_LISTENING_STREAK" to RecordGroup.LISTENING,
        "PODIUM_SWEEP" to RecordGroup.DOMINATION,
        "MOST_RECORDS" to RecordGroup.PALMARES
    )
    fun inGroup(g: RecordGroup): List<RecordDef> = groupOf.filterValues { it == g }.keys.mapNotNull { id -> ALL.firstOrNull { it.id == id } }

    /** Index de période (jours epoch / semaines ISO / mois / années) — partagé moteur + explications. */
    fun periodIndex(date: java.time.LocalDate, p: Period): Int = when (p) {
        Period.DAILY -> date.toEpochDay().toInt()
        Period.WEEKLY -> Math.floorDiv(date.toEpochDay() + 3, 7L).toInt()
        Period.MONTHLY -> date.year * 12 + date.monthValue
        Period.YEARLY -> date.year
        Period.GLOBAL -> 0
    }
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
    /** Zones des records de longévité / retours (25-27) : Top 10, Top 20, Top 50, chart complet. */
    val lifespanZones = listOf(RecordSub("TOP10", "🔥 Top 10"), RecordSub("TOP20", "📊 Top 20"), RecordSub("TOP50", "📈 Top 50"), RecordSub("CHART", "🌍 Chart"))
    fun zoneLimit(sub: String?): Int = when (sub) { "TOP1" -> 1; "TOP3" -> 3; "TOP5" -> 5; "TOP10" -> 10; "TOP20" -> 20; "TOP50" -> 50; else -> Int.MAX_VALUE }
    fun zoneLabel(sub: String?): String = when (sub) { "TOP1" -> "le #1"; "TOP3" -> "le Top 3"; "TOP5" -> "le Top 5"; "TOP10" -> "le Top 10"; "TOP20" -> "le Top 20"; "TOP50" -> "le Top 50"; else -> "le chart" }
    /** Sections de Fastest Rise (16) : la zone à atteindre. */
    val riseZones = listOf(RecordSub("TOP1", "👑 Top 1"), RecordSub("TOP3", "🥉 Top 3"), RecordSub("TOP5", "🔝 Top 5"), RecordSub("TOP10", "🔥 Top 10"), RecordSub("TOP20", "📊 Top 20"))
    /** Podium Sweep (29) : zone × mode (Solo = uniquement des titres sans featuring ; Standard = feat. compris). */
    val sweepZones = listOf("TOP3" to "Top 3", "TOP5" to "Top 5", "TOP10" to "Top 10")
    val sweepSubs = sweepZones.flatMap { (z, l) -> listOf(RecordSub("${z}_SOLO", "🎯 $l · Solo"), RecordSub("${z}_STD", "🎼 $l · Standard")) }
    fun sweepZoneOf(sub: String?): Int = zoneLimit(sub?.substringBefore("_"))
    fun sweepSolo(sub: String?): Boolean = sub?.endsWith("_SOLO") == true
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
        RecordDef(16, "FASTEST_RISE", "🏎️", "Fastest Rise", "Zone (Top 1 / 3 / 5 / 10 / 20) atteinte le plus vite après l'entrée dans le chart — entrées directes exclues", chartPeriods, all3,
            mapOf(RecordCategory.TRACK to riseZones, RecordCategory.ARTIST to riseZones, RecordCategory.ALBUM to riseZones), ascending = true),
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
        RecordDef(24, "MOST_SUCCESSIVE_1", "🔁", "Most Successive #1", "Le plus de #1 successifs avec des titres différents, sans interruption", chartPeriods, artistAlbum, artistSongsAlbums, RecordUnit.COUNT),
        // ---- Extension 25-30 ----
        RecordDef(25, "LONGEST_LIFESPAN", "⏱️", "Longest Lifespan", "Plus longue durée entre la première et la dernière apparition dans une zone du chart, absences comprises", chartPeriods, all3,
            mapOf(RecordCategory.TRACK to lifespanZones, RecordCategory.ARTIST to lifespanZones, RecordCategory.ALBUM to lifespanZones)),
        RecordDef(26, "MOST_REENTRIES", "↩️", "Most Re-Entries", "Le plus de retours dans une zone du chart après au moins une période d'absence", chartPeriods, all3,
            mapOf(RecordCategory.TRACK to lifespanZones, RecordCategory.ARTIST to lifespanZones, RecordCategory.ALBUM to lifespanZones), RecordUnit.COUNT),
        RecordDef(27, "LONGEST_ABSENCE_RETURN", "🕰️", "Longest Absence Return", "Retour dans une zone du chart après la plus longue absence (en périodes)", chartPeriods, all3,
            mapOf(RecordCategory.TRACK to lifespanZones, RecordCategory.ARTIST to lifespanZones, RecordCategory.ALBUM to lifespanZones)),
        RecordDef(28, "LONGEST_LISTENING_STREAK", "🎧", "Longest Listening Streak", "Plus longue série de jours consécutifs avec au moins une écoute (séries en cours comprises)", emptyList(), all3, unit = RecordUnit.DAYS),
        RecordDef(29, "PODIUM_SWEEP", "🧹", "Podium Sweep", "Un même artiste / album occupe TOUTES les places du Top 3 / 5 / 10 des titres en même temps — nombre de périodes", chartPeriods, artistAlbum,
            mapOf(RecordCategory.ARTIST to sweepSubs, RecordCategory.ALBUM to sweepSubs)),
        RecordDef(30, "MOST_RECORDS", "🏆", "Most Records", "Le plus de #1 détenus dans l'ensemble des classements de records (record × période × zone) — ex æquo compris", emptyList(), all3, unit = RecordUnit.COUNT)
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

    /** Durée en jours, heures et minutes : « 12 j 5 h 32 min », « 5 h 32 min », « 32 min ». */
    fun formatDurationAdaptive(ms: Long): String {
        val totalMin = (ms / 60_000).coerceAtLeast(0)
        val d = totalMin / 1440; val h = (totalMin % 1440) / 60; val m = totalMin % 60
        return when {
            d > 0 -> "$d j $h h $m min"
            h > 0 -> "$h h $m min"
            else -> "$m min"
        }
    }

    /** Absence minimale pour un comeback (en nombre de périodes). */
    fun comebackMinAbsence(period: Period): Int = when (period) {
        Period.DAILY -> 5; Period.WEEKLY -> 3; Period.MONTHLY -> 2; Period.YEARLY -> 1; Period.GLOBAL -> 1
    }
}

/* =========================== MOTEUR (séries de positions) =========================== */

/** Une apparition dans un chart : index de période (0,1,2… consécutif dans le calendrier), position, écoutes. */
data class RecordAppearance(val periodIndex: Int, val date: String, val position: Int, val plays: Int)

/** Résultat d'un record pour une entité. */
data class RecordResult(val entityId: Long, val value: Double, val date: String? = null, val extra: String? = null)

/** Calculs purs à partir des séries de positions d'une entité (triées par periodIndex). */
object RecordMath {

    fun cumulative(series: List<RecordAppearance>, maxPosition: Int = Int.MAX_VALUE): Int = series.count { it.position <= maxPosition }

    fun timesAt1(series: List<RecordAppearance>): Int = series.count { it.position == 1 }

    /** Plus longue série d'apparitions consécutives (periodIndex contigus) à une position ≤ [maxPosition]. */
    fun longestStreak(series: List<RecordAppearance>, maxPosition: Int = Int.MAX_VALUE): Pair<Int, String?> {
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
    fun biggestComeback(series: List<RecordAppearance>, minAbsence: Int): RecordResult? {
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
    fun periodsToReach(series: List<RecordAppearance>, target: Int): Pair<Int, String>? {
        if (series.isEmpty()) return null
        val entry = series.first().periodIndex
        val hit = series.firstOrNull { it.position <= target } ?: return null
        return (hit.periodIndex - entry) to hit.date
    }

    /** Plus grande progression (jump > 0) ou chute (fall > 0) entre deux périodes consécutives. */
    fun biggestMove(series: List<RecordAppearance>, jump: Boolean): RecordResult? {
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
    fun sleeperHit(series: List<RecordAppearance>): RecordResult? {
        if (series.isEmpty()) return null
        val entry = series.first()
        if (entry.position <= 20) return null
        val peak = series.minByOrNull { it.position } ?: return null
        if (peak.position > 10 || peak.periodIndex - entry.periodIndex < 3) return null
        return RecordResult(0, (entry.position - peak.position).toDouble(), peak.date, "entré #${entry.position} → peak #${peak.position} en ${peak.periodIndex - entry.periodIndex} périodes")
    }

    fun biggestClimber(series: List<RecordAppearance>): RecordResult? {
        if (series.isEmpty()) return null
        val entry = series.first()
        val peak = series.minByOrNull { it.position } ?: return null
        val climb = entry.position - peak.position
        if (climb <= 0) return null
        return RecordResult(0, climb.toDouble(), peak.date, "entré #${entry.position} → peak #${peak.position}")
    }

    /** Périodes dans le Top 5 pour une entité n'ayant jamais atteint le #1 : (n, meilleure position). */
    fun blockedTop5(series: List<RecordAppearance>): Pair<Int, Int>? {
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

    /* ---------------- 25-28 : longévité, retours, absences, séries d'écoute ---------------- */

    /** Apparitions dans la zone (position ≤ [maxPosition]). */
    fun inZone(series: List<RecordAppearance>, maxPosition: Int) = series.filter { it.position <= maxPosition }

    /**
     * 25. Longest Lifespan : périodes écoulées (inclusif, absences comprises) entre la première et la dernière apparition
     * dans la zone. Minimum 2 apparitions (une apparition isolée = 1 période, non classée).
     */
    fun lifespan(series: List<RecordAppearance>, maxPosition: Int): RecordResult? {
        val z = inZone(series, maxPosition)
        if (z.size < 2) return null
        val span = z.last().periodIndex - z.first().periodIndex + 1
        return RecordResult(0, span.toDouble(), z.last().date, "du ${z.first().date} au ${z.last().date} · ${z.size} apparitions")
    }

    /** 26. Most Re-Entries : nombre de retours dans la zone après ≥ 1 période manquée (jour sans chart = absence). */
    fun reentries(series: List<RecordAppearance>, maxPosition: Int): RecordResult? {
        val z = inZone(series, maxPosition)
        var n = 0; var lastReturn: String? = null; var longest = 0
        for (i in 1 until z.size) {
            val gap = z[i].periodIndex - z[i - 1].periodIndex - 1
            if (gap >= 1) { n++; lastReturn = z[i].date; if (gap > longest) longest = gap }
        }
        return if (n > 0) RecordResult(0, n.toDouble(), lastReturn, "dernier retour le $lastReturn · plus longue absence $longest") else null
    }

    /** 27. Longest Absence Return : plus longue pause (en périodes) entre deux apparitions consécutives dans la zone. */
    fun longestAbsence(series: List<RecordAppearance>, maxPosition: Int): RecordResult? {
        val z = inZone(series, maxPosition)
        var best: RecordResult? = null
        for (i in 1 until z.size) {
            val gap = z[i].periodIndex - z[i - 1].periodIndex - 1
            if (gap >= 1 && (best == null || gap > best.value)) best = RecordResult(0, gap.toDouble(), z[i].date, "#${z[i - 1].position} → #${z[i].position} après $gap périodes d'absence")
        }
        return best
    }

    /**
     * 16. Fastest Rise (nouvelle règle) : périodes calendaires entre l'entrée dans le chart et la première atteinte de la
     * zone ; les entrées directes (0) sont exclues.
     */
    fun fastestRise(series: List<RecordAppearance>, target: Int): RecordResult? {
        val (n, d) = periodsToReach(series, target) ?: return null
        return if (n >= 1) RecordResult(0, n.toDouble(), d, "entré #${series.first().position} → #${series.first { it.position <= target }.position}") else null
    }

    /** 28. Plus longue série de jours consécutifs (jours epoch triés, dédoublonnés) : (longueur, jour de début, jour de fin). */
    fun longestDayStreak(days: Collection<Int>): Triple<Int, Int, Int>? {
        val sorted = days.toSortedSet()
        if (sorted.isEmpty()) return null
        var best = 1; var bestStart = sorted.first(); var bestEnd = sorted.first()
        var cur = 1; var start = sorted.first(); var prev = sorted.first()
        for (d in sorted.drop(1)) {
            if (d == prev + 1) cur++ else { cur = 1; start = d }
            if (cur > best) { best = cur; bestStart = start; bestEnd = d }
            prev = d
        }
        return Triple(best, bestStart, bestEnd)
    }

    /**
     * 29. Podium Sweep — pour une zone complète (liste des titres aux places 1..N) : propriétaires qui balaient la zone.
     * Standard = chaque titre appartient au propriétaire (feat. compris) ; Solo = en plus, chaque titre est sans featuring.
     * @return (propriétaires standard, propriétaires solo)
     */
    fun sweepOwners(tracks: List<Long>, owners: (Long) -> Set<Long>, solo: (Long) -> Boolean): Pair<Set<Long>, Set<Long>> {
        if (tracks.isEmpty()) return emptySet<Long>() to emptySet()
        var common: Set<Long> = owners(tracks.first())
        for (t in tracks.drop(1)) { common = common intersect owners(t); if (common.isEmpty()) break }
        val allSolo = tracks.all(solo)
        return common to (if (allSolo) common else emptySet())
    }
}
