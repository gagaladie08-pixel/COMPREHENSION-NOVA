package com.novastats.app.data.repository

import com.novastats.app.data.db.NovaDatabase
import com.novastats.app.data.db.dao.PriorRow
import com.novastats.app.data.db.entity.EntityType
import com.novastats.app.domain.CertLevel
import com.novastats.app.domain.Dates
import com.novastats.app.domain.PantheonStatus
import com.novastats.app.domain.Period
import com.novastats.app.domain.RecordAppearance
import com.novastats.app.domain.RecordCatalog
import com.novastats.app.domain.RecordCategory
import com.novastats.app.domain.RecordDef
import kotlinx.coroutines.flow.first
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 🏅 « Pourquoi cet élément est là ? » — construit, pour chaque record, une fiche spécifique :
 * un titre, un récit en français, des faits chiffrés, une frise des périodes concernées et les éléments impliqués
 * (titres d'un artiste, certifications, entrées Hall of Fame…). Tout est recalculé à partir des snapshots Billboard,
 * exactement comme le moteur des records, pour que l'explication colle à la valeur affichée.
 */
class RecordExplainer(private val db: NovaDatabase) {

    data class Point(val date: String, val label: String, val position: Int?, val plays: Int?, val highlight: Boolean = false, val note: String? = null)
    data class Item(val type: String, val id: Long, val name: String, val detail: String, val imageUrl: String?, val highlight: Boolean = false)
    /** Un classement de record détenu (#1, ex æquo compris) — fiche Most Records. */
    data class Held(val def: RecordDef, val period: Period?, val category: RecordCategory, val sub: String?, val row: com.novastats.app.data.db.entity.RecordCacheEntity, val tied: Int)
    data class Story(
        val headline: String,
        val narrative: String,
        val facts: List<Pair<String, String>> = emptyList(),
        val timelineTitle: String? = null,
        val timeline: List<Point> = emptyList(),
        val itemsTitle: String? = null,
        val items: List<Item> = emptyList(),
        /** Most Records : tous les classements détenus, groupés par famille dans la fiche. */
        val held: List<Held> = emptyList(),
        /** Paragraphes de contexte propres à CET élément (place dans le classement, profil, autres records) : titre → texte. */
        val context: List<Pair<String, String>> = emptyList()
    )

    private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRANCE)
    private val shortFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE)
    private val monthFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRANCE)
    private val monthShortFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yy", Locale.FRANCE)

    /** Libellé long d'une période : « le 12 mai 2026 », « la semaine du 12 mai 2026 », « en mai 2026 », « en 2026 ». */
    fun on(period: Period?, iso: String?): String {
        if (iso == null) return "—"
        val d = runCatching { Dates.parse(iso) }.getOrNull() ?: return iso
        return when (period) {
            Period.WEEKLY -> "la semaine du ${d.format(dayFmt)}"
            Period.MONTHLY -> "en ${d.format(monthFmt)}"
            Period.YEARLY -> "en ${d.year}"
            else -> "le ${d.format(dayFmt)}"
        }
    }

    /** Libellé court pour la frise. */
    fun short(period: Period?, iso: String): String {
        val d = runCatching { Dates.parse(iso) }.getOrNull() ?: return iso
        return when (period) {
            Period.WEEKLY -> "sem. ${d.format(shortFmt)}"
            Period.MONTHLY -> d.format(monthShortFmt)
            Period.YEARLY -> d.year.toString()
            else -> d.format(shortFmt)
        }
    }

    private fun unit(p: Period?, n: Int) = RecordCatalog.unitLabel(p, n > 1)
    private fun plural(n: Int, one: String, many: String = one + "s") = if (n > 1) many else one
    private fun pos(p: Int) = "#$p"

    /* ------------------------------------------------------------------ données ------------------------------------------------------------------ */

    private fun toSeries(rows: List<PriorRow>, p: Period): List<RecordAppearance> =
        rows.map { RecordAppearance(RecordCatalog.periodIndex(Dates.parse(it.date), p), it.date, it.position, it.playCount) }.sortedBy { it.periodIndex }

    private suspend fun series(cat: RecordCategory, p: Period, id: Long): List<RecordAppearance> = toSeries(
        when (cat) {
            RecordCategory.TRACK -> db.billboardDao().trackHistory(p.dbName, id)
            RecordCategory.ARTIST -> db.billboardDao().artistHistory(p.dbName, id)
            RecordCategory.ALBUM -> db.billboardDao().albumHistory(p.dbName, id)
        }, p
    )

    private suspend fun allSeries(cat: RecordCategory, p: Period): Map<Long, List<RecordAppearance>> {
        val rows = when (cat) {
            RecordCategory.TRACK -> db.billboardDao().allTrackRows(p.dbName)
            RecordCategory.ARTIST -> db.billboardDao().allArtistRows(p.dbName)
            RecordCategory.ALBUM -> db.billboardDao().allAlbumRows(p.dbName)
        }
        return rows.groupBy { it.entityId }.mapValues { (_, r) -> toSeries(r, p) }
    }

    private suspend fun nameOf(type: String, id: Long): Pair<String, String?> = when (type) {
        EntityType.TRACK -> db.trackDao().getById(id)?.let { t -> (t.title) to t.coverUrl } ?: ("Titre #$id" to null)
        EntityType.ALBUM -> db.albumDao().getById(id)?.let { it.title to it.coverUrl } ?: ("Album #$id" to null)
        else -> db.artistDao().getById(id)?.let { it.name to it.photoUrl } ?: ("Artiste #$id" to null)
    }

    /** Éléments « possédés » : titres d'un artiste (SONGS), albums d'un artiste (ALBUMS), titres d'un album. */
    private suspend fun ownedIds(cat: RecordCategory, sub: String?, id: Long): Pair<RecordCategory, Set<Long>> = when {
        cat == RecordCategory.ARTIST && sub?.startsWith("ALBUMS") == true -> RecordCategory.ALBUM to db.albumDao().ofArtist(id).map { it.albumId }.toSet()
        cat == RecordCategory.ARTIST -> RecordCategory.TRACK to db.trackLinkDao().trackIdsForArtist(id).toSet()
        else -> RecordCategory.TRACK to db.trackDao().ofAlbum(id).map { it.track.trackId }.toSet()
    }

    private fun points(p: Period?, s: List<RecordAppearance>, highlight: (RecordAppearance) -> Boolean, note: (RecordAppearance) -> String? = { null }) =
        s.map { Point(it.date, short(p, it.date), it.position, it.plays, highlight(it), note(it)) }

    private fun longestRun(s: List<RecordAppearance>, ok: (RecordAppearance) -> Boolean): List<RecordAppearance> {
        var best = emptyList<RecordAppearance>(); var cur = ArrayList<RecordAppearance>(); var prev = Int.MIN_VALUE
        for (a in s) {
            if (ok(a) && a.periodIndex == prev + 1) cur.add(a) else if (ok(a)) cur = arrayListOf(a) else cur = ArrayList()
            if (cur.size > best.size) best = ArrayList(cur)
            prev = a.periodIndex
        }
        return best
    }

    /* ------------------------------------------------------------------ fiche ------------------------------------------------------------------ */

    suspend fun explain(def: RecordDef, period: Period?, cat: RecordCategory, sub: String?, id: Long, name: String, value: Double): Story {
        val story = explainCore(def, period, cat, sub, id, name, value)
        val ctx = runCatching { context(def, period, cat, sub, id, name, value) }.getOrDefault(emptyList())
        val lede = runCatching { lede(def, cat, id, name) }.getOrNull()
        val narrative = if (lede == null) story.narrative else lede + "\n\n" + rephrase(def, cat, id, name, story.narrative)
        return story.copy(narrative = narrative, context = ctx)
    }

    /* ---------------- accroche unique à l'élément ---------------- */

    /** Choix déterministe d'une formulation parmi plusieurs, propre au couple (record, élément) : deux fiches ne se ressemblent pas. */
    private fun <T> pick(def: RecordDef, id: Long, vararg variants: T): T {
        val h = (def.id.hashCode() * 31 + id.hashCode()).let { if (it < 0) -it else it }
        return variants[h % variants.size]
    }

    private fun dateOf(ms: Long?): String = ms?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(dayFmt) } ?: "—"
    private fun daysSince(ms: Long?): Long = ms?.let { java.time.temporal.ChronoUnit.DAYS.between(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate(), Dates.today()) } ?: 0L

    /** Portrait de l'élément (écoutes, découverte, rang général, certification / Panthéon, série, fraîcheur) — différent pour chaque titre / artiste / album. */
    private suspend fun lede(def: RecordDef, cat: RecordCategory, id: Long, name: String): String? {
        val sb = StringBuilder()
        when (cat) {
            RecordCategory.TRACK -> {
                val t = db.trackDao().getById(id) ?: return null
                val artist = db.artistDao().getById(t.artistId)?.name ?: "?"
                val all = db.trackDao().allPlayed().filter { it.originalTrackId == null }.sortedByDescending { it.playCount }
                val rank = all.indexOfFirst { it.trackId == id } + 1
                val days = daysSince(t.firstPlayedAt).coerceAtLeast(1)
                val perWeek = String.format(Locale.FRANCE, "%.1f", t.playCount * 7.0 / days)
                val rankTxt = if (rank > 0) " (${ordinal(rank)} titre le plus écouté de ta bibliothèque sur ${all.size})" else ""
                sb.append(
                    pick(
                        def, id,
                        "« $name » de $artist : ${t.playCount} écoutes depuis sa découverte le ${dateOf(t.firstPlayedAt)}$rankTxt.",
                        "Découvert le ${dateOf(t.firstPlayedAt)}, « $name » ($artist) en est à ${t.playCount} écoutes$rankTxt.",
                        "${t.playCount} écoutes en $days jours : « $name » de $artist tourne en moyenne $perWeek fois par semaine depuis le ${dateOf(t.firstPlayedAt)}$rankTxt.",
                        "Côté chiffres, « $name » ($artist) pèse ${t.playCount} écoutes depuis le ${dateOf(t.firstPlayedAt)}$rankTxt."
                    )
                )
                val linked = db.trackDao().versionsOf(id)
                if (linked.isNotEmpty()) {
                    val own = db.trackDao().ownPlays(id)
                    sb.append(" Ce total inclut ${linked.size} version${if (linked.size > 1) "s" else ""} liée${if (linked.size > 1) "s" else ""} : original $own ▶, " + linked.joinToString(", ") { v -> "${v.title.removePrefix(t.title).trim().trim('(', ')')} ${v.playCount} ▶" } + ".")
                }
                db.certificationDao().current(id, EntityType.TRACK)?.let { c ->
                    val lvl = CertLevel.entries.firstOrNull { it.dbName == c.level }
                    sb.append(" Il est certifié ${lvl?.emoji ?: ""} ${lvl?.label ?: c.level}${if (c.multiplier > 1) " ×${c.multiplier}" else ""}.")
                }
                if (t.bestStreak >= 3) sb.append(" Sa plus longue série : ${t.bestStreak} jours d'écoute d'affilée.")
                val since = daysSince(t.lastPlayedAt)
                sb.append(if (since <= 1) " Tu l'as encore écouté aujourd'hui ou hier." else if (since <= 7) " Dernière écoute il y a $since jours." else " Plus écouté depuis $since jours.")
            }
            RecordCategory.ARTIST -> {
                val a = db.artistDao().getById(id) ?: return null
                val all = db.artistDao().allPlayedList()
                val rank = all.indexOfFirst { it.artistId == id } + 1
                val tracks = db.trackLinkDao().trackIdsForArtist(id).size
                val days = daysSince(a.firstPlayedAt).coerceAtLeast(1)
                val rankTxt = if (rank > 0) ", ${ordinal(rank)} artiste le plus écouté sur ${all.size}" else ""
                sb.append(
                    pick(
                        def, id,
                        "$name, c'est ${a.playCount} écoutes réparties sur $tracks titres depuis le ${dateOf(a.firstPlayedAt)}$rankTxt.",
                        "Présent dans ta bibliothèque depuis le ${dateOf(a.firstPlayedAt)}, $name cumule ${a.playCount} écoutes sur $tracks titres$rankTxt.",
                        "${a.playCount} écoutes, $tracks titres, $days jours de présence : voilà le poids de $name dans tes stats$rankTxt.",
                        "Avec $tracks titres écoutés et ${a.playCount} écoutes depuis le ${dateOf(a.firstPlayedAt)}, $name est ${if (rank in 1..3) "l'un de tes artistes majeurs" else "un artiste bien installé"}$rankTxt."
                    )
                )
                db.pantheonDao().forArtist(id)?.let { ps ->
                    val st = PantheonStatus.entries.firstOrNull { it.dbName == ps.currentStatus }
                    if (st != null) sb.append(" Au Panthéon, il est ${st.emoji} ${st.label} depuis le ${dateOf(ps.statusDate)}.")
                }
                val since = daysSince(a.lastPlayedAt)
                sb.append(if (since <= 1) " Encore écouté ces dernières 24 h." else if (since <= 7) " Dernière écoute il y a $since jours." else " Plus écouté depuis $since jours.")
            }
            RecordCategory.ALBUM -> {
                val al = db.albumDao().getById(id) ?: return null
                val artist = db.artistDao().getById(al.artistId)?.name ?: "?"
                val tracks = db.trackDao().ofAlbum(id)
                val all = db.albumDao().all().filter { it.playCount > 0 }.sortedByDescending { it.playCount }
                val rank = all.indexOfFirst { it.albumId == id } + 1
                val top = tracks.maxByOrNull { it.track.playCount }
                val rankTxt = if (rank > 0) " (${ordinal(rank)} album le plus écouté sur ${all.size})" else ""
                sb.append(
                    pick(
                        def, id,
                        "« $name » de $artist : ${al.playCount} écoutes sur ${tracks.size} titres depuis le ${dateOf(al.firstPlayedAt)}$rankTxt.",
                        "Depuis le ${dateOf(al.firstPlayedAt)}, l'album « $name » ($artist) totalise ${al.playCount} écoutes réparties sur ${tracks.size} titres$rankTxt.",
                        "${al.playCount} écoutes pour ${tracks.size} titres : « $name » de $artist est un disque que tu reviens chercher$rankTxt."
                    )
                )
                if (top != null && al.playCount > 0) sb.append(" Son titre moteur : « ${top.track.title} » (${top.track.playCount} ▶, ${100 * top.track.playCount / al.playCount} % des écoutes de l'album).")
                db.certificationDao().current(id, EntityType.ALBUM)?.let { c ->
                    val lvl = CertLevel.entries.firstOrNull { it.dbName == c.level }
                    sb.append(" Certifié ${lvl?.emoji ?: ""} ${lvl?.label ?: c.level}${if (c.multiplier > 1) " ×${c.multiplier}" else ""}.")
                }
            }
        }
        return sb.toString().ifBlank { null }
    }

    /** Évite de répéter le nom en tête du récit juste après l'accroche : « Ce titre… », « Le morceau… », « Il… ». */
    private fun rephrase(def: RecordDef, cat: RecordCategory, id: Long, name: String, narrative: String): String {
        if (!narrative.startsWith(name)) return narrative
        val subject = when (cat) {
            RecordCategory.TRACK -> pick(def, id + 7, "Ce titre", "Le morceau", "Il", "Ce même titre")
            RecordCategory.ARTIST -> pick(def, id + 7, "L'artiste", "Il", "Ce même artiste")
            RecordCategory.ALBUM -> pick(def, id + 7, "Cet album", "Le disque", "Il", "Ce même album")
        }
        return subject + narrative.removePrefix(name)
    }

    private suspend fun explainCore(def: RecordDef, period: Period?, cat: RecordCategory, sub: String?, id: Long, name: String, value: Double): Story = runCatching {
        when (def.id) {
            "MOST_CUMULATIVE", "MOST_CUMULATIVE_TOP10", "MOST_TIME_AT_1", "MOST_CONSISTENT", "MOST_BLOCKED_TOP5",
            "BIGGEST_JUMP", "BIGGEST_FALL", "BIGGEST_COMEBACK", "BIGGEST_CLIMBER", "SLEEPER_HIT", "FASTEST_RISE", "LONGEST_ROAD",
            "BIGGEST_PERIOD", "BIGGEST_DEBUT", "LONGEST_LIFESPAN", "MOST_REENTRIES", "LONGEST_ABSENCE_RETURN" -> chartStory(def, period ?: Period.WEEKLY, cat, sub, id, name)
            "PODIUM_SWEEP" -> podiumSweep(period ?: Period.WEEKLY, cat, sub, id, name)
            "LONGEST_LISTENING_STREAK" -> listeningStreak(cat, id, name)
            "MOST_RECORDS" -> mostRecords(cat, id, name)
            "MOST_SONGS_IN_CHART", "MOST_SONGS_TOP10", "MOST_SONGS_AT_1", "MOST_DEBUT_1", "MOST_DEBUT_TOP10", "MOST_SIMULTANEOUS", "MOST_SUCCESSIVE_1" ->
                ownerStory(def, period ?: Period.WEEKLY, cat, sub, id, name)
            "FASTEST_CERT" -> fastestCert(cat, sub, id, name)
            "FASTEST_PANTHEON" -> fastestPantheon(sub, id, name)
            "MOST_CERTIFICATIONS" -> mostCertifications(cat, sub, id, name)
            "MOST_HOF", "MOST_GLOBAL" -> hallOfFame(def, cat, sub, id, name)
            "MULTI_CHART" -> multiChart(cat, sub, id, name)
            else -> Story(def.title, def.description)
        }
    }.getOrElse { e -> Story(def.title, "Impossible de reconstruire l'explication : ${e.message ?: e.javaClass.simpleName}") }

    /* ---------------- contexte unique à l'élément (ajouté à chaque fiche) ---------------- */

    private fun fmtValue(def: RecordDef, p: Period?, v: Double): String {
        val n = v.toInt()
        return when (def.unit) {
            com.novastats.app.domain.RecordUnit.PERIODS -> "$n ${unit(p, n)}"
            com.novastats.app.domain.RecordUnit.DAYS -> "$n ${plural(n, "jour")}"
            com.novastats.app.domain.RecordUnit.POSITIONS -> "$n ${plural(n, "place")}"
            com.novastats.app.domain.RecordUnit.PLAYS -> "$n ${plural(n, "écoute")}"
            com.novastats.app.domain.RecordUnit.TIMES -> "$n fois"
            com.novastats.app.domain.RecordUnit.DURATION_MS -> RecordCatalog.formatDurationAdaptive(v.toLong())
            else -> "$n"
        }
    }

    private fun catWord(cat: RecordCategory, n: Int = 1) = when (cat) { RecordCategory.TRACK -> plural(n, "titre"); RecordCategory.ARTIST -> plural(n, "artiste"); RecordCategory.ALBUM -> plural(n, "album") }
    private fun ordinal(n: Int) = if (n == 1) "1er" else "${n}e"
    private fun rankingLabel(def: RecordDef, period: Period?, cat: RecordCategory, sub: String?): String {
        val subLabel = def.subs[cat]?.firstOrNull { it.dbName == sub }?.label
        return listOfNotNull(def.title, period?.frLabel, subLabel).joinToString(" · ")
    }

    /**
     * Trois paragraphes calculés pour CET élément précis :
     *  📊 sa place dans ce classement (écart au #1, au précédent, au suivant, égalités),
     *  🧬 son profil dans le chart concerné (entrée, sommet, régularité, statut actuel, part de ses écoutes…),
     *  🏆 ailleurs dans les records (même record autres périodes, autres #1, nombre de classements).
     */
    private suspend fun context(def: RecordDef, period: Period?, cat: RecordCategory, sub: String?, id: Long, name: String, value: Double): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        val asc = def.ascending
        val better = if (asc) "plus court" else "plus grand"

        // 📊 Place dans ce classement
        val rows = db.recordDao().top10(def.id, period?.dbName, cat.dbName, sub, asc).first()
        val idx = rows.indexOfFirst { it.r.entityId == id }
        if (idx >= 0) {
            val me = rows[idx]
            val ties = rows.filter { it.r.value == me.r.value && it.r.entityId != id }
            val sb = StringBuilder()
            if (rows.size == 1) sb.append("$name est seul dans ce classement : aucun autre ${catWord(cat)} n'a (encore) rempli les conditions de ce record. ")
            else if (idx == 0) {
                val second = rows.firstOrNull { it.r.value != me.r.value }
                if (ties.isNotEmpty()) sb.append("$name partage la première place avec ${ties.joinToString(", ") { it.name ?: "?" }} (${fmtValue(def, period, me.r.value)} chacun). ")
                else sb.append("$name domine ce classement avec ${fmtValue(def, period, me.r.value)}. ")
                if (second != null) {
                    val gap = kotlin.math.abs(me.r.value - second.r.value)
                    val pct = if (second.r.value > 0) (100 * gap / second.r.value).toInt() else 0
                    sb.append((if (ties.isNotEmpty()) "Le suivant, " else "Son dauphin, ") + "${second.name}, est à ${fmtValue(def, period, second.r.value)} : " +
                        (if (asc) "$name a été ${fmtValue(def, period, gap)} plus rapide" else "une avance de ${fmtValue(def, period, gap)}") + (if (pct > 0) " ($pct %)" else "") + ". ")
                }
            } else {
                val leader = rows.first()
                val gapLead = kotlin.math.abs(leader.r.value - me.r.value)
                sb.append("$name est ${ordinal(idx + 1)} sur ${rows.size} dans ce classement" + (if (ties.isNotEmpty()) ", à égalité avec ${ties.joinToString(", ") { it.name ?: "?" }}" else "") + ". ")
                sb.append("Le #1, ${leader.name}, est à ${fmtValue(def, period, leader.r.value)}" + (if (gapLead > 0) " — il lui manque ${fmtValue(def, period, gapLead)} pour le rejoindre. " else ". "))
                val prev = rows.subList(0, idx).lastOrNull { it.r.value != me.r.value }
                val next = rows.drop(idx + 1).firstOrNull { it.r.value != me.r.value }
                if (prev != null && prev.r.entityId != leader.r.entityId) sb.append("Juste devant lui : ${prev.name} (${fmtValue(def, period, prev.r.value)}). ")
                if (next != null) sb.append("Juste derrière : ${next.name} (${fmtValue(def, period, next.r.value)})" + (if (kotlin.math.abs(me.r.value - next.r.value) <= 1.0) " — à une unité près, sa place est menacée." else ".") + " ")
                else if (idx == rows.size - 1) sb.append("Il ferme le Top 10 : le prochain ${catWord(cat)} à faire mieux l'en sortira. ")
            }
            if (asc) sb.append("Ici, $better = mieux.")
            out += "📊 Sa place dans ce classement" to sb.toString().trim()
        }

        // 🧬 Profil dans le chart concerné (records basés sur une trajectoire)
        val chartBased = period != null && def.id !in setOf("FASTEST_CERT", "FASTEST_PANTHEON", "MOST_CERTIFICATIONS", "MOST_HOF", "MOST_GLOBAL", "MULTI_CHART")
        if (chartBased) {
            val p = period!!
            val s = series(cat, p, id)
            if (s.isNotEmpty()) {
                val first = s.first(); val last = s.last(); val peak = s.minBy { it.position }
                val span = last.periodIndex - first.periodIndex + 1
                val rate = 100 * s.size / span
                val ones = s.count { it.position == 1 }
                val todayIdx = RecordCatalog.periodIndex(Dates.today(), p)
                val chart = "chart ${p.frLabel.lowercase()}"
                val sb = StringBuilder()
                sb.append(
                    when {
                        first.position == 1 -> "Dans le $chart, $name est entré directement au #1 ${on(p, first.date)}"
                        first.position <= 10 -> "Dans le $chart, $name est entré directement dans le Top 10 (${pos(first.position)}) ${on(p, first.date)}"
                        first.position <= 30 -> "Dans le $chart, $name est entré en milieu de tableau (${pos(first.position)}) ${on(p, first.date)}"
                        else -> "Dans le $chart, $name est entré discrètement (${pos(first.position)}) ${on(p, first.date)}"
                    }
                )
                sb.append(
                    if (peak.periodIndex == first.periodIndex) ", qui reste à ce jour sa meilleure position. "
                    else " avant de grimper jusqu'au ${pos(peak.position)} ${on(p, peak.date)} (${peak.plays} ▶ cette période-là). "
                )
                if (ones > 0 && def.id != "MOST_TIME_AT_1") sb.append("Il compte $ones ${unit(p, ones)} au #1. ")
                sb.append(
                    "Présent ${s.size} ${unit(p, s.size)} sur $span possibles depuis son entrée ($rate %) : " +
                        when { rate >= 85 -> "une régularité remarquable. "; rate >= 55 -> "une présence solide. "; rate >= 30 -> "une présence par vagues. "; else -> "des apparitions ponctuelles. " }
                )
                if (last.periodIndex >= todayIdx - 1) sb.append("Il est toujours classé en ce moment (${pos(last.position)} ${on(p, last.date)}), ce record peut donc encore évoluer. ")
                else { val gone = todayIdx - last.periodIndex; sb.append("Il a quitté le $chart depuis $gone ${unit(p, gone)} (dernier passage ${pos(last.position)} ${on(p, last.date)}). ") }
                val inChart = s.sumOf { it.plays }
                when (cat) {
                    RecordCategory.TRACK -> db.trackDao().getById(id)?.let { t ->
                        if (t.playCount > 0) sb.append("Sur ses ${t.playCount} écoutes au total, $inChart (${(100 * inChart / t.playCount).coerceAtMost(100)} %) ont eu lieu pendant ses périodes classées. ")
                        if (t.bestStreak >= 2) sb.append("Sa meilleure série d'écoute quotidienne : ${t.bestStreak} jours. ")
                    }
                    RecordCategory.ARTIST -> {
                        val owned = db.trackLinkDao().trackIdsForArtist(id).toSet()
                        val charted = allSeries(RecordCategory.TRACK, p).count { it.key in owned }
                        db.artistDao().getById(id)?.let { a -> if (a.playCount > 0) sb.append("${a.playCount} écoutes au total, dont $inChart pendant ses périodes classées. ") }
                        if (charted > 0) sb.append("$charted de ses titres sont passés par le $chart des titres. ")
                    }
                    RecordCategory.ALBUM -> {
                        val owned = db.trackDao().ofAlbum(id).map { it.track.trackId }.toSet()
                        val charted = allSeries(RecordCategory.TRACK, p).count { it.key in owned }
                        db.albumDao().getById(id)?.let { a -> if (a.playCount > 0) sb.append("${a.playCount} écoutes au total, dont $inChart pendant ses périodes classées. ") }
                        if (owned.isNotEmpty()) sb.append("$charted de ses ${owned.size} titres sont passés par le $chart des titres. ")
                    }
                }
                out += "🧬 Son profil dans ce chart" to sb.toString().trim()
            }
        }

        // 🏆 Ailleurs dans les records
        val all = db.recordDao().rowsForEntity(cat.dbName, id).filter { it.recordType != "MOST_RECORDS" }
        val isCurrent = { r: com.novastats.app.data.db.entity.RecordCacheEntity -> r.recordType == def.id && r.periodType == period?.dbName && r.subcategory == sub }
        val others = all.filterNot(isCurrent)
        if (others.isNotEmpty()) {
            val sb = StringBuilder()
            val same = others.filter { it.recordType == def.id }
            if (same.isNotEmpty()) {
                val parts = same.take(6).map { r ->
                    val rank = db.recordDao().betterCount(r.recordType, r.periodType, r.category, r.subcategory, r.value, asc) + 1
                    val pl = Period.entries.firstOrNull { it.dbName == r.periodType }
                    val sl = def.subs[cat]?.firstOrNull { it.dbName == r.subcategory }?.label
                    "#$rank en ${listOfNotNull(pl?.frLabel, sl).joinToString(" ")} (${fmtValue(def, pl, r.value)})"
                }
                sb.append("Sur ce même record, il est aussi " + parts.joinToString(", ") + (if (same.size > 6) " et ${same.size - 6} autres" else "") + ". ")
            }
            val held = db.recordDao().heldNumberOnes(cat.dbName, id).filterNot(isCurrent)
            val otherTypes = others.map { it.recordType }.toSet() - def.id
            val n = others.size
            sb.append("Au total, $name figure dans $n ${plural(n, "autre classement", "autres classements")} de records (${otherTypes.size} ${plural(otherTypes.size, "record différent", "records différents")})")
            if (held.isNotEmpty()) {
                val ex = held.take(3).joinToString(", ") { r -> RecordCatalog.byId(r.recordType)?.let { d -> rankingLabel(d, Period.entries.firstOrNull { it.dbName == r.periodType }, cat, r.subcategory) } ?: r.recordType }
                sb.append(", dont ${held.size} où il est #1 : $ex" + (if (held.size > 3) "…" else "") + ".")
            } else sb.append(", sans autre première place pour l'instant.")
            out += "🏆 Ailleurs dans les records" to sb.toString().trim()
        } else if (idx >= 0) {
            out += "🏆 Ailleurs dans les records" to "C'est le seul classement de records où $name apparaît pour le moment : ce record est sa signature."
        }
        return out
    }

    /* ---------------- records sur la trajectoire de l'élément ---------------- */

    private suspend fun chartStory(def: RecordDef, p: Period, cat: RecordCategory, sub: String?, id: Long, name: String): Story {
        val s = series(cat, p, id)
        if (s.isEmpty()) return Story(def.title, "$name n'a plus d'historique dans le chart ${p.frLabel.lowercase()} (les records seront recalculés à la prochaine écoute).")
        val chart = "chart ${p.frLabel.lowercase()}"
        val first = s.first(); val last = s.last()
        val peak = s.minBy { it.position }
        val span = last.periodIndex - first.periodIndex + 1
        val totalPlays = s.sumOf { it.plays }
        val base = listOf(
            "Entrée dans le chart" to "${on(p, first.date)} · ${pos(first.position)} · ${first.plays} ▶",
            "Meilleure position" to "${pos(peak.position)} · ${on(p, peak.date)}",
            "Dernière apparition" to "${on(p, last.date)} · ${pos(last.position)}",
            "Écoutes cumulées dans le chart" to "$totalPlays ▶"
        )
        return when (def.id) {
            "MOST_CUMULATIVE" -> {
                val n = s.size
                Story(
                    "📆 $n ${unit(p, n)} dans le chart",
                    "$name est apparu $n fois dans le $chart : depuis son entrée ${on(p, first.date)} (${pos(first.position)}) jusqu'à ${on(p, last.date)}. " +
                        "Sur les $span ${unit(p, span)} écoulées depuis son entrée, il était classé $n fois (${100 * n / span} %). Il a culminé ${pos(peak.position)} ${on(p, peak.date)}.",
                    base + ("Taux de présence" to "${100 * n / span} % des ${unit(p, span)} depuis l'entrée"),
                    "Toutes ses apparitions (le peak en surbrillance)", points(p, s, { it.position == peak.position })
                )
            }
            "MOST_CUMULATIVE_TOP10" -> {
                val top = s.filter { it.position <= 10 }
                val run = longestRun(s) { it.position <= 10 }
                Story(
                    "🔟 ${top.size} ${unit(p, top.size)} dans le Top 10",
                    "$name a passé ${top.size} ${unit(p, top.size)} dans le Top 10 du $chart (sur ${s.size} apparitions au total). " +
                        "Première fois ${on(p, top.first().date)} (${pos(top.first().position)}), dernière fois ${on(p, top.last().date)}. " +
                        (if (run.size > 1) "Plus longue série sans sortir du Top 10 : ${run.size} ${unit(p, run.size)} (${short(p, run.first().date)} → ${short(p, run.last().date)})." else ""),
                    base + ("Apparitions hors Top 10" to "${s.size - top.size}"),
                    "Trajectoire (Top 10 en surbrillance)", points(p, s, { it.position <= 10 })
                )
            }
            "MOST_TIME_AT_1" -> {
                val ones = s.filter { it.position == 1 }
                val run = longestRun(s) { it.position == 1 }
                Story(
                    "👑 ${ones.size} ${unit(p, ones.size)} au #1",
                    "$name a occupé la première place du $chart ${ones.size} fois : " + ones.joinToString(", ") { short(p, it.date) } + ". " +
                        (if (run.size > 1) "Son plus long règne : ${run.size} ${unit(p, run.size)} consécutives, de ${short(p, run.first().date)} à ${short(p, run.last().date)}. " else "Chaque passage au #1 a été isolé (pas deux périodes de suite). ") +
                        "Pendant ces périodes de domination, il a cumulé ${ones.sumOf { it.plays }} écoutes.",
                    base + ("Premier #1" to on(p, ones.first().date)) + ("Dernier #1" to on(p, ones.last().date)) + ("Plus long règne" to "${run.size} ${unit(p, run.size)}"),
                    "Trajectoire (#1 en surbrillance)", points(p, s, { it.position == 1 })
                )
            }
            "MOST_CONSISTENT" -> {
                val max = when (sub) { "TOP5" -> 5; "TOP10" -> 10; else -> Int.MAX_VALUE }
                val zone = when (sub) { "TOP5" -> "le Top 5"; "TOP10" -> "le Top 10"; else -> "le chart" }
                val run = longestRun(s) { it.position <= max }
                val runPositions = run.joinToString(" → ") { pos(it.position) }
                Story(
                    "🧱 ${run.size} ${unit(p, run.size)} d'affilée dans $zone",
                    "$name est resté dans $zone du $chart ${run.size} ${unit(p, run.size)} de suite, sans jamais en sortir, de ${short(p, run.firstOrNull()?.date ?: first.date)} à ${short(p, run.lastOrNull()?.date ?: last.date)}. " +
                        "Ses positions pendant la série : $runPositions. Meilleure position sur la série : ${pos(run.minOfOrNull { it.position } ?: peak.position)}.",
                    base + ("Série" to "${run.size} ${unit(p, run.size)} consécutives"),
                    "Trajectoire (la série en surbrillance)", points(p, s, { a -> run.any { it.periodIndex == a.periodIndex } })
                )
            }
            "MOST_BLOCKED_TOP5" -> {
                val top5 = s.filter { it.position <= 5 }
                val all = allSeries(cat, p)
                val blockers = HashMap<Long, Int>()
                for (a in top5) all.forEach { (oid, os) -> if (oid != id && os.any { it.date == a.date && it.position == 1 }) blockers[oid] = (blockers[oid] ?: 0) + 1 }
                val items = blockers.entries.sortedByDescending { it.value }.take(5).map { (oid, n) ->
                    val (nm, img) = nameOf(cat.dbName, oid); Item(cat.dbName, oid, nm, "#1 pendant $n de ses ${top5.size} passages en Top 5", img)
                }
                Story(
                    "🚧 ${top5.size} ${unit(p, top5.size)} dans le Top 5, jamais #1",
                    "$name a passé ${top5.size} ${unit(p, top5.size)} dans le Top 5 du $chart sans jamais décrocher la première place : sa meilleure position reste ${pos(peak.position)} (${on(p, peak.date)}). " +
                        (if (items.isNotEmpty()) "Celui qui lui a barré la route le plus souvent : ${items.first().name}." else ""),
                    base + ("Passages en Top 5" to "${top5.size}") + ("Passages à la #2" to "${s.count { it.position == 2 }}"),
                    "Trajectoire (Top 5 en surbrillance)", points(p, s, { it.position <= 5 }),
                    "Ceux qui occupaient le #1 pendant ce temps", items
                )
            }
            "BIGGEST_JUMP", "BIGGEST_FALL" -> {
                val jump = def.id == "BIGGEST_JUMP"
                var bi = -1; var bestDelta = 0
                for (i in 1 until s.size) {
                    if (s[i].periodIndex != s[i - 1].periodIndex + 1) continue
                    val d = if (jump) s[i - 1].position - s[i].position else s[i].position - s[i - 1].position
                    if (d > bestDelta) { bestDelta = d; bi = i }
                }
                if (bi < 0) return Story(def.title, "Aucun mouvement entre deux périodes consécutives trouvé pour $name.")
                val a = s[bi - 1]; val b = s[bi]
                Story(
                    (if (jump) "⬆️ +" else "⬇️ −") + "$bestDelta ${plural(bestDelta, "place")} d'un coup",
                    "Entre ${short(p, a.date)} et ${short(p, b.date)}, $name est passé de la ${pos(a.position)} à la ${pos(b.position)} du $chart : ${if (jump) "un bond de" else "une chute de"} $bestDelta ${plural(bestDelta, "place")} en une seule période. " +
                        "Ses écoutes sont passées de ${a.plays} à ${b.plays} ▶ (${if (b.plays >= a.plays) "+" else ""}${b.plays - a.plays}).",
                    base + ("Avant" to "${on(p, a.date)} · ${pos(a.position)} · ${a.plays} ▶") + ("Après" to "${on(p, b.date)} · ${pos(b.position)} · ${b.plays} ▶"),
                    "Trajectoire (les deux périodes du mouvement en surbrillance)", points(p, s, { it.periodIndex == a.periodIndex || it.periodIndex == b.periodIndex })
                )
            }
            "BIGGEST_COMEBACK" -> {
                val minGap = RecordCatalog.comebackMinAbsence(p)
                var bi = -1; var bestGain = 0
                for (i in 1 until s.size) {
                    val gap = s[i].periodIndex - s[i - 1].periodIndex - 1
                    val gain = s[i - 1].position - s[i].position
                    if (gap >= minGap && gain > bestGain) { bestGain = gain; bi = i }
                }
                if (bi < 0) return Story(def.title, "Aucun retour après absence trouvé pour $name.")
                val a = s[bi - 1]; val b = s[bi]; val gap = b.periodIndex - a.periodIndex - 1
                Story(
                    "🔄 +$bestGain ${plural(bestGain, "place")} au retour",
                    "$name avait quitté le $chart après ${short(p, a.date)} (${pos(a.position)}). Après $gap ${unit(p, gap)} d'absence, il est revenu ${on(p, b.date)} directement à la ${pos(b.position)} — $bestGain ${plural(bestGain, "place")} plus haut que là où il s'était arrêté, porté par ${b.plays} écoutes.",
                    base + ("Départ" to "${on(p, a.date)} · ${pos(a.position)}") + ("Absence" to "$gap ${unit(p, gap)}") + ("Retour" to "${on(p, b.date)} · ${pos(b.position)} · ${b.plays} ▶"),
                    "Trajectoire (départ et retour en surbrillance)", points(p, s, { it.periodIndex == a.periodIndex || it.periodIndex == b.periodIndex })
                )
            }
            "BIGGEST_CLIMBER", "SLEEPER_HIT" -> {
                val climb = first.position - peak.position
                val took = peak.periodIndex - first.periodIndex
                val sleeper = def.id == "SLEEPER_HIT"
                Story(
                    (if (sleeper) "😴 " else "🧗 ") + "+$climb ${plural(climb, "place")} entre l'entrée et le peak",
                    "$name est entré ${if (sleeper) "discrètement " else ""}dans le $chart ${on(p, first.date)} à la ${pos(first.position)} avec ${first.plays} écoutes. " +
                        "Il a ensuite grimpé jusqu'à la ${pos(peak.position)} ${on(p, peak.date)}, soit $climb ${plural(climb, "place")} gagnées en $took ${unit(p, took)}. " +
                        (if (sleeper) "C'est la définition d'un sleeper hit : une entrée au-delà du Top 20, puis un peak dans le Top 10 au moins 3 périodes plus tard." else "Un vrai grimpeur : l'écart entre sa position d'entrée et son sommet est le plus grand du chart."),
                    base + ("Ascension" to "+$climb ${plural(climb, "place")} en $took ${unit(p, took)}"),
                    "Trajectoire (entrée et peak en surbrillance)", points(p, s, { it.periodIndex == first.periodIndex || it.periodIndex == peak.periodIndex })
                )
            }
            "FASTEST_RISE", "LONGEST_ROAD" -> {
                val target = if (def.id == "FASTEST_RISE") RecordCatalog.zoneLimit(sub ?: "TOP3") else 1
                val goal = if (def.id == "FASTEST_RISE") RecordCatalog.zoneLabel(sub ?: "TOP3") else "le #1"
                val hit = s.firstOrNull { it.position <= target } ?: return Story(def.title, "$name n'a pas (encore) atteint $goal dans ce chart.")
                val took = hit.periodIndex - first.periodIndex
                Story(
                    (if (def.id == "FASTEST_RISE") "🏎️ " else "🛣️ ") + (if (took == 0) "$goal dès l'entrée" else "$goal après $took ${unit(p, took)}"),
                    "$name est entré dans le $chart ${on(p, first.date)} à la ${pos(first.position)}. " +
                        (if (took == 0) "Il a atteint $goal immédiatement, dès sa première apparition (les entrées directes ne sont pas classées dans Fastest Rise : voir Debut #1 / Debut Top 10)." else "Il lui a fallu $took ${unit(p, took)} (périodes calendaires, absences comprises) pour atteindre $goal : c'est chose faite ${on(p, hit.date)} (${pos(hit.position)}, ${hit.plays} ▶). ") +
                        (if (took > 1) "Positions sur le chemin : " + s.filter { it.periodIndex in first.periodIndex..hit.periodIndex }.joinToString(" → ") { pos(it.position) } + "." else ""),
                    base + ("Objectif atteint" to "${on(p, hit.date)} · ${pos(hit.position)}") + ("Délai" to "$took ${unit(p, took)}"),
                    "Trajectoire (entrée et objectif en surbrillance)", points(p, s, { it.periodIndex == first.periodIndex || it.periodIndex == hit.periodIndex })
                )
            }
            "LONGEST_LIFESPAN", "MOST_REENTRIES", "LONGEST_ABSENCE_RETURN" -> {
                val max = RecordCatalog.zoneLimit(sub); val zone = RecordCatalog.zoneLabel(sub)
                val z = s.filter { it.position <= max }
                if (z.size < 2) return Story(def.title, "$name n'a pas assez d'apparitions dans $zone du $chart pour ce record.")
                val gaps = (1 until z.size).map { i -> Triple(z[i - 1], z[i], z[i].periodIndex - z[i - 1].periodIndex - 1) }
                val returns = gaps.filter { it.third >= 1 }
                val span = z.last().periodIndex - z.first().periodIndex + 1
                val zoneFacts = base + ("Première fois dans $zone" to "${on(p, z.first().date)} · ${pos(z.first().position)}") + ("Dernière fois dans $zone" to "${on(p, z.last().date)} · ${pos(z.last().position)}") +
                    ("Apparitions dans $zone" to "${z.size} ${unit(p, z.size)}") + ("Durée de vie dans $zone" to "$span ${unit(p, span)}") + ("Retours après absence" to "${returns.size}")
                val note: (RecordAppearance) -> String? = { a -> returns.firstOrNull { it.second.periodIndex == a.periodIndex }?.let { "retour après ${it.third} ${unit(p, it.third)}" } }
                when (def.id) {
                    "LONGEST_LIFESPAN" -> Story(
                        "⏱️ $span ${unit(p, span)} de vie dans $zone",
                        "Entre sa première apparition dans $zone du $chart (${on(p, z.first().date)}, ${pos(z.first().position)}) et sa dernière (${on(p, z.last().date)}, ${pos(z.last().position)}), il s'est écoulé $span ${unit(p, span)}, absences comprises. " +
                            "Sur cette durée, $name était effectivement dans $zone ${z.size} fois (${100 * z.size / span} %)" + (if (returns.isNotEmpty()) ", avec ${returns.size} ${plural(returns.size, "retour")} après absence (la plus longue : ${returns.maxOf { it.third }} ${unit(p, returns.maxOf { it.third })})." else ", sans jamais en sortir."),
                        zoneFacts, "Trajectoire (première et dernière apparition dans $zone en surbrillance)", points(p, s, { it.periodIndex == z.first().periodIndex || it.periodIndex == z.last().periodIndex }, note)
                    )
                    "MOST_REENTRIES" -> Story(
                        "↩️ ${returns.size} ${plural(returns.size, "retour")} dans $zone",
                        "$name est revenu ${returns.size} fois dans $zone du $chart après au moins une période d'absence (un jour sans chart compte comme une absence). " +
                            "Détail des retours : " + returns.joinToString(" ; ") { "${short(p, it.second.date)} (${pos(it.second.position)}) après ${it.third} ${unit(p, it.third)}" } + ".",
                        zoneFacts, "Trajectoire (retours en surbrillance)", points(p, s, { a -> returns.any { it.second.periodIndex == a.periodIndex } }, note)
                    )
                    else -> {
                        val best = returns.maxByOrNull { it.third } ?: return Story(def.title, "$name n'a jamais quitté $zone du $chart entre deux apparitions.")
                        Story(
                            "🕰️ De retour après ${best.third} ${unit(p, best.third)} d'absence",
                            "$name avait quitté $zone du $chart après ${on(p, best.first.date)} (${pos(best.first.position)}). Il y est revenu ${on(p, best.second.date)}, à la ${pos(best.second.position)} avec ${best.second.plays} ▶ — soit ${best.third} ${unit(p, best.third)} sans y apparaître. " +
                                "Contrairement à Biggest Comeback, seule la durée de l'absence compte ici, pas la remontée en places.",
                            zoneFacts + ("Plus longue absence" to "${best.third} ${unit(p, best.third)} · ${short(p, best.first.date)} → ${short(p, best.second.date)}"),
                            "Trajectoire (départ et retour en surbrillance)", points(p, s, { it.periodIndex == best.first.periodIndex || it.periodIndex == best.second.periodIndex }, note)
                        )
                    }
                }
            }
            "BIGGEST_PERIOD" -> {
                val best = s.maxBy { it.plays }
                val others = s.filter { it !== best }
                val avg = if (others.isEmpty()) 0 else others.sumOf { it.plays } / others.size
                val word = when (p) { Period.DAILY -> "une seule journée"; Period.WEEKLY -> "une seule semaine"; else -> "un seul mois" }
                Story(
                    "📈 ${best.plays} écoutes en $word",
                    "${on(p, best.date).replaceFirstChar { it.uppercase() }}, $name a été écouté ${best.plays} fois en $word — ce qui le plaçait ${pos(best.position)} du $chart. " +
                        (if (others.isNotEmpty()) "Sur ses ${others.size} autres apparitions, sa moyenne est de $avg ▶ : ce pic représente ${if (avg > 0) "${best.plays / avg}×" else "bien plus que"} son rythme habituel." else "C'est pour l'instant sa seule apparition dans ce chart."),
                    base + ("Pic" to "${on(p, best.date)} · ${best.plays} ▶ · ${pos(best.position)}") + ("Moyenne des autres apparitions" to "$avg ▶"),
                    "Écoutes par période (le pic en surbrillance)", points(p, s, { it === best })
                )
            }
            "BIGGEST_DEBUT" -> {
                val next = s.getOrNull(1)
                Story(
                    "💥 ${first.plays} écoutes dès l'entrée",
                    "Pour sa toute première apparition dans le $chart, ${on(p, first.date)}, $name a cumulé ${first.plays} écoutes et est entré directement à la ${pos(first.position)}. " +
                        (if (next != null) "La période suivante (${short(p, next.date)}), il était ${pos(next.position)} avec ${next.plays} ▶." else "Il n'y a pas encore de période suivante dans ce chart."),
                    base,
                    "Trajectoire (l'entrée en surbrillance)", points(p, s, { it.periodIndex == first.periodIndex })
                )
            }
            else -> Story(def.title, def.description, base)
        }
    }

    /* ---------------- records « propriétaire » : titres / albums d'un artiste, titres d'un album ---------------- */

    private suspend fun ownerStory(def: RecordDef, p: Period, cat: RecordCategory, sub: String?, id: Long, name: String): Story {
        val (itemCat, ids) = ownedIds(cat, sub, id)
        val all = allSeries(itemCat, p)
        val mine = all.filterKeys { it in ids }
        val chart = "chart ${p.frLabel.lowercase()}"
        val what = if (itemCat == RecordCategory.ALBUM) "album" else "titre"
        suspend fun item(iid: Long, detail: String, hl: Boolean = false): Item { val (nm, img) = nameOf(itemCat.dbName, iid); return Item(itemCat.dbName, iid, nm, detail, img, hl) }
        fun peakOf(s: List<RecordAppearance>) = s.minBy { it.position }

        return when (def.id) {
            "MOST_SONGS_IN_CHART", "MOST_SONGS_TOP10", "MOST_SONGS_AT_1" -> {
                val limit = when (def.id) { "MOST_SONGS_TOP10" -> 10; "MOST_SONGS_AT_1" -> 1; else -> Int.MAX_VALUE }
                val zone = when (limit) { 10 -> "dans le Top 10"; 1 -> "au #1"; else -> "dans le chart" }
                val hits = mine.filter { (_, s) -> s.any { it.position <= limit } }
                val items = hits.entries.sortedBy { peakOf(it.value).position }.map { (iid, s) ->
                    val pk = peakOf(s); item(iid, "peak ${pos(pk.position)} ${on(p, pk.date)} · ${s.size} ${unit(p, s.size)} classé", pk.position <= limit)
                }
                Story(
                    "${def.emoji} ${hits.size} ${plural(hits.size, what)} différents $zone",
                    "$name a placé ${hits.size} ${plural(hits.size, what)} différents $zone du $chart" + (if (ids.size > hits.size) " (sur ${ids.size} ${plural(ids.size, what)} en bibliothèque)" else "") + ". " +
                        (items.firstOrNull()?.let { "Le plus haut : ${it.name} (${it.detail.substringBefore(" ·")})." } ?: ""),
                    listOf("$what".replaceFirstChar { it.uppercase() } + "s concernés" to "${hits.size}", "Total classés dans ce chart" to "${mine.size}"),
                    itemsTitle = "Les ${plural(hits.size, what)} qui comptent", items = items
                )
            }
            "MOST_DEBUT_1", "MOST_DEBUT_TOP10" -> {
                val limit = if (def.id == "MOST_DEBUT_1") 1 else 10
                val hits = mine.filter { (_, s) -> s.first().position <= limit }
                val items = hits.entries.sortedBy { it.value.first().date }.map { (iid, s) -> val e = s.first(); item(iid, "entré ${pos(e.position)} ${on(p, e.date)} · ${e.plays} ▶", true) }
                Story(
                    "${def.emoji} ${hits.size} ${plural(hits.size, what)} entrés directement ${if (limit == 1) "au #1" else "dans le Top 10"}",
                    "${hits.size} ${plural(hits.size, what)} de $name sont entrés dans le $chart directement ${if (limit == 1) "à la première place" else "dans le Top 10"}, dès leur première apparition : " +
                        items.joinToString(", ") { it.name } + ".",
                    listOf("Débuts ${if (limit == 1) "#1" else "Top 10"}" to "${hits.size}", "Total classés dans ce chart" to "${mine.size}"),
                    itemsTitle = "Les débuts en question", items = items
                )
            }
            "MOST_SIMULTANEOUS" -> {
                val zoneName = sub?.substringAfterLast('_') ?: "ALL"
                val limit = when (zoneName) { "TOP5" -> 5; "TOP10" -> 10; "TOP20" -> 20; "TOP50" -> 50; else -> Int.MAX_VALUE }
                val zone = when (zoneName) { "TOP5" -> "le Top 5"; "TOP10" -> "le Top 10"; "TOP20" -> "le Top 20"; "TOP50" -> "le Top 50"; else -> "le chart" }
                val byDate = HashMap<String, MutableList<Pair<Long, RecordAppearance>>>()
                mine.forEach { (iid, s) -> s.forEach { a -> if (a.position <= limit) byDate.getOrPut(a.date) { ArrayList() }.add(iid to a) } }
                val best = byDate.entries.maxWithOrNull(compareBy<Map.Entry<String, List<Pair<Long, RecordAppearance>>>> { it.value.size }.thenBy { it.key })
                    ?: return Story(def.title, "Aucune période trouvée où $name avait plusieurs ${plural(2, what)} dans $zone.")
                val items = best.value.sortedBy { it.second.position }.map { (iid, a) -> item(iid, "${pos(a.position)} · ${a.plays} ▶", true) }
                Story(
                    "🎯 ${items.size} ${plural(items.size, what)} en même temps dans $zone",
                    "${on(p, best.key).replaceFirstChar { it.uppercase() }}, $name occupait $zone du $chart avec ${items.size} ${plural(items.size, what)} à la fois : " +
                        items.joinToString(", ") { "${it.name} (${it.detail.substringBefore(" ·")})" } + ".",
                    listOf("Période record" to on(p, best.key), "Périodes avec ≥ 2 ${plural(2, what)} dans $zone" to "${byDate.values.count { it.size >= 2 }}"),
                    itemsTitle = "Le chart ce jour-là", items = items
                )
            }
            "MOST_SUCCESSIVE_1" -> {
                // #1 du chart appartenant à cet artiste / album, triés par période ; plus longue suite consécutive
                val ones = all.flatMap { (iid, s) -> s.filter { it.position == 1 }.map { iid to it } }.sortedBy { it.second.periodIndex }
                var best = emptyList<Pair<Long, RecordAppearance>>(); var cur = ArrayList<Pair<Long, RecordAppearance>>(); var prev = Int.MIN_VALUE
                for (n in ones) {
                    val mineOne = n.first in ids
                    if (mineOne && n.second.periodIndex == prev + 1) cur.add(n) else if (mineOne) cur = arrayListOf(n) else cur = ArrayList()
                    if (cur.map { it.first }.toSet().size > best.map { it.first }.toSet().size) best = ArrayList(cur)
                    prev = n.second.periodIndex
                }
                val distinct = best.map { it.first }.distinct()
                val items = distinct.map { iid -> val dates = best.filter { it.first == iid }.map { short(p, it.second.date) }; item(iid, "#1 : " + dates.joinToString(", "), true) }
                Story(
                    "🔁 ${distinct.size} #1 différents à la suite",
                    if (best.isEmpty()) "Aucune suite de #1 trouvée pour $name." else
                        "De ${short(p, best.first().second.date)} à ${short(p, best.last().second.date)}, la première place du $chart n'a pas quitté $name pendant ${best.size} ${unit(p, best.size)} — avec ${distinct.size} ${plural(distinct.size, what)} différents qui se sont relayés au sommet : " +
                            items.joinToString(", ") { it.name } + ".",
                    listOf("Durée de la série" to "${best.size} ${unit(p, best.size)}", "${what.replaceFirstChar { it.uppercase() }}s différents" to "${distinct.size}"),
                    itemsTitle = "Les #1 qui se sont relayés", items = items
                )
            }
            else -> Story(def.title, def.description)
        }
    }

    /* ---------------- certifications, Panthéon, Hall of Fame, multi-chart ---------------- */

    private suspend fun fastestCert(cat: RecordCategory, sub: String?, id: Long, name: String): Story {
        val type = if (cat == RecordCategory.ALBUM) EntityType.ALBUM else EntityType.TRACK
        val history = db.certificationDao().history(id, type)
        val level = CertLevel.entries.firstOrNull { it.dbName == sub }
        val row = history.filter { it.level == sub && (it.timeToCertifyMs ?: 0L) > 0 }.minByOrNull { it.timeToCertifyMs!! }
            ?: return Story("⚡ Fastest Certification", "Aucune certification ${level?.label ?: sub} trouvée pour $name.")
        val ms = row.timeToCertifyMs!!
        val firstPlay = row.certifiedAt - ms
        val items = history.sortedBy { it.certifiedAt }.map { h ->
            val l = CertLevel.entries.firstOrNull { it.dbName == h.level }
            Item(type, id, "${l?.emoji ?: "🏅"} ${l?.label ?: h.level}${if (h.multiplier > 1) " ×${h.multiplier}" else ""}", "${on(Period.DAILY, Dates.toIso(h.certifiedAt))} · ${h.playCountAtCert} ▶ · ${RecordCatalog.formatDurationAdaptive(h.timeToCertifyMs ?: 0L)} après la 1re écoute", null, h.id == row.id)
        }
        return Story(
            "⚡ ${level?.emoji ?: ""} ${level?.label ?: sub} en ${RecordCatalog.formatDurationAdaptive(ms)}",
            "$name a décroché la certification ${level?.label ?: sub} en seulement ${RecordCatalog.formatDurationAdaptive(ms)} : première écoute ${on(Period.DAILY, Dates.toIso(firstPlay))}, certifié ${on(Period.DAILY, Dates.toIso(row.certifiedAt))} avec ${row.playCountAtCert} écoutes" +
                (if (row.multiplier > 1) " (×${row.multiplier})" else "") + ". Personne n'a atteint ce palier plus vite dans ta bibliothèque.",
            listOf("Première écoute" to on(Period.DAILY, Dates.toIso(firstPlay)), "Certifié" to on(Period.DAILY, Dates.toIso(row.certifiedAt)), "Écoutes à la certification" to "${row.playCountAtCert} ▶", "Rythme" to "${String.format(Locale.FRANCE, "%.1f", row.playCountAtCert / (ms / 86_400_000.0).coerceAtLeast(1.0))} écoutes / jour"),
            itemsTitle = "Son parcours de certifications", items = items
        )
    }

    private suspend fun fastestPantheon(sub: String?, id: Long, name: String): Story {
        val history = db.pantheonDao().history(id)
        val status = PantheonStatus.fromDb(sub)
        val row = history.firstOrNull { it.status == sub && (it.timeToReachMs ?: 0L) > 0 }
            ?: return Story("👑 Fastest Panthéon", "Aucun statut ${status?.label ?: sub} trouvé pour $name.")
        val ms = row.timeToReachMs!!
        val firstPlay = row.dateReached - ms
        val items = history.sortedBy { it.dateReached }.map { h ->
            val st = PantheonStatus.fromDb(h.status)
            Item(EntityType.ARTIST, id, "${st?.emoji ?: "•"} ${st?.label ?: h.status}", "${on(Period.DAILY, Dates.toIso(h.dateReached))} · ${h.playCountAtStatus} ▶ · ${RecordCatalog.formatDurationAdaptive(h.timeToReachMs ?: 0L)} après la 1re écoute", null, h.id == row.id)
        }
        return Story(
            "👑 ${status?.emoji ?: ""} ${status?.label ?: sub} en ${RecordCatalog.formatDurationAdaptive(ms)}",
            "$name est devenu ${status?.label ?: sub} du Panthéon en ${RecordCatalog.formatDurationAdaptive(ms)} seulement : première écoute ${on(Period.DAILY, Dates.toIso(firstPlay))}, statut atteint ${on(Period.DAILY, Dates.toIso(row.dateReached))} avec ${row.playCountAtStatus} écoutes. Aucun autre artiste n'a franchi ce palier aussi vite.",
            listOf("Première écoute" to on(Period.DAILY, Dates.toIso(firstPlay)), "Statut atteint" to on(Period.DAILY, Dates.toIso(row.dateReached)), "Écoutes au statut" to "${row.playCountAtStatus} ▶", "Rythme" to "${String.format(Locale.FRANCE, "%.1f", row.playCountAtStatus / (ms / 86_400_000.0).coerceAtLeast(1.0))} écoutes / jour"),
            itemsTitle = "Son ascension au Panthéon", items = items
        )
    }

    private suspend fun mostCertifications(cat: RecordCategory, sub: String?, id: Long, name: String): Story {
        val levelName = sub?.substringAfterLast('_') ?: sub
        val level = CertLevel.entries.firstOrNull { it.dbName == levelName }
        val (itemCat, ids) = ownedIds(cat, sub, id)
        val type = if (itemCat == RecordCategory.ALBUM) EntityType.ALBUM else EntityType.TRACK
        val rows = db.certificationDao().allHistory().filter { it.entityType == type && it.level == levelName && it.entityId in ids }
            .groupBy { it.entityId }.mapValues { (_, r) -> r.minBy { it.certifiedAt } }
        val items = rows.entries.sortedBy { it.value.certifiedAt }.map { (iid, h) -> val (nm, img) = nameOf(type, iid); Item(type, iid, nm, "${level?.emoji ?: ""} certifié ${on(Period.DAILY, Dates.toIso(h.certifiedAt))} · ${h.playCountAtCert} ▶", img, true) }
        val what = if (type == EntityType.ALBUM) "album" else "titre"
        return Story(
            "💎 ${items.size} ${plural(items.size, what)} certifiés ${level?.label ?: levelName}",
            "$name compte ${items.size} ${plural(items.size, what)} ayant atteint la certification ${level?.emoji ?: ""} ${level?.label ?: levelName}" + (if (ids.size > items.size) " (sur ${ids.size} en bibliothèque)" else "") + " : " + items.joinToString(", ") { it.name } + ".",
            listOf("Certifiés ${level?.label ?: levelName}" to "${items.size}", "${what.replaceFirstChar { it.uppercase() }}s en bibliothèque" to "${ids.size}"),
            itemsTitle = "Les certifiés", items = items
        )
    }

    /* ---------------- 29. Podium Sweep ---------------- */

    private suspend fun podiumSweep(p: Period, cat: RecordCategory, sub: String?, id: Long, name: String): Story {
        val z = RecordCatalog.sweepZoneOf(sub); val solo = RecordCatalog.sweepSolo(sub)
        val chart = "chart ${p.frLabel.lowercase()}"
        val all = allSeries(RecordCategory.TRACK, p)
        val owned = if (cat == RecordCategory.ARTIST) db.trackLinkDao().trackIdsForArtist(id).toSet() else db.trackDao().ofAlbum(id).map { it.track.trackId }.toSet()
        // Solo = un seul artiste (principal + liens de featuring), même règle que le moteur
        val artistsOf = HashMap<Long, HashSet<Long>>()
        db.trackDao().allPlayed().forEach { t -> artistsOf.getOrPut(t.trackId) { HashSet() }.add(t.artistId) }
        db.trackLinkDao().allTrackArtists().forEach { artistsOf.getOrPut(it.trackId) { HashSet() }.add(it.artistId) }
        fun isSolo(t: Long) = (artistsOf[t]?.size ?: 1) <= 1
        val byDate = HashMap<String, HashMap<Int, Long>>()
        for ((tid, s) in all) for (a in s) if (a.position <= z) byDate.getOrPut(a.date) { HashMap() }[a.position] = tid
        val sweeps = byDate.entries.filter { (_, posMap) -> (1..z).all { pos -> posMap[pos]?.let { it in owned && (!solo || isSolo(it)) } == true } }.sortedBy { it.key }
        if (sweeps.isEmpty()) return Story("🧹 Podium Sweep", "$name n'occupe plus l'intégralité du Top $z du $chart (les records seront recalculés à la prochaine écoute).")
        val last = sweeps.last()
        val items = (1..z).mapNotNull { place -> last.value[place]?.let { tid -> val (nm, img) = nameOf(EntityType.TRACK, tid); Item(EntityType.TRACK, tid, nm, "${pos(place)} ${on(p, last.key)}" + (if (isSolo(tid)) " · solo" else " · feat."), img, place == 1) } }
        val mode = if (solo) "uniquement avec des titres solo (sans featuring)" else "feat. compris"
        return Story(
            "🧹 Top $z complet ${sweeps.size} fois",
            "$name a occupé les $z premières places du $chart en même temps, $mode, pendant ${sweeps.size} ${unit(p, sweeps.size)} : " + sweeps.joinToString(", ") { short(p, it.key) } + ". " +
                "Un balayage ne compte que si TOUTES les places de la zone lui appartiennent la même période — c'est voulu rare.",
            listOf("Zone" to "Top $z · ${if (solo) "Solo" else "Standard"}", "Premier balayage" to on(p, sweeps.first().key), "Dernier balayage" to on(p, last.key), "Titres différents utilisés" to "${sweeps.flatMap { it.value.values }.toSet().size}"),
            "Les périodes de balayage", sweeps.map { Point(it.key, short(p, it.key), 1, null, true, "Top $z complet") },
            "Le dernier Top $z complet", items
        )
    }

    /* ---------------- 28. Longest Listening Streak ---------------- */

    private suspend fun listeningStreak(cat: RecordCategory, id: Long, name: String): Story {
        val days = when (cat) {
            RecordCategory.TRACK -> db.dailyPlayDao().seriesForTrack(id)
            RecordCategory.ALBUM -> db.dailyPlayDao().seriesForAlbum(id)
            RecordCategory.ARTIST -> db.dailyPlayDao().seriesForArtist(id)
        }
        val epoch = days.associate { Dates.parse(it.date).toEpochDay().toInt() to it }
        val (len, start, end) = com.novastats.app.domain.RecordMath.longestDayStreak(epoch.keys) ?: return Story("🎧 Série d'écoute", "Aucune écoute enregistrée pour $name.")
        val streakDays = (start..end).mapNotNull { epoch[it] }
        val plays = streakDays.sumOf { it.playCount }
        val today = Dates.today().toEpochDay().toInt()
        val ongoing = end >= today - 1
        val startIso = java.time.LocalDate.ofEpochDay(start.toLong()); val endIso = java.time.LocalDate.ofEpochDay(end.toLong())
        val totalDays = days.size
        return Story(
            "🎧 $len jours d'affilée" + (if (ongoing) " (en cours)" else ""),
            "$name a été écouté chaque jour, sans exception, du ${startIso.format(dayFmt)} au ${endIso.format(dayFmt)} : $len jours consécutifs, $plays écoutes sur la série (${if (len > 0) plays / len else 0} par jour en moyenne). " +
                (if (ongoing) "La série est toujours en cours — une journée sans écoute la rompra." else "Elle s'est arrêtée le lendemain, ${endIso.plusDays(1).format(dayFmt)}, première journée sans écoute.") +
                " Au total, $name a été écouté $totalDays jours différents.",
            listOf("Début de la série" to startIso.format(dayFmt), "Fin de la série" to endIso.format(dayFmt), "Écoutes pendant la série" to "$plays", "Meilleure journée de la série" to (streakDays.maxByOrNull { it.playCount }?.let { "${Dates.parse(it.date).format(dayFmt)} · ${it.playCount} ▶" } ?: "—"), "Jours d'écoute au total" to "$totalDays"),
            "Les jours de la série (écoutes par jour)", streakDays.map { Point(it.date, Dates.parse(it.date).format(shortFmt), null, it.playCount, true) }
        )
    }

    /* ---------------- 30. Most Records (fiche détaillée) ---------------- */

    private suspend fun mostRecords(cat: RecordCategory, id: Long, name: String): Story {
        val rows = db.recordDao().heldNumberOnes(cat.dbName, id)
        val held = rows.mapNotNull { r ->
            val def = RecordCatalog.byId(r.recordType) ?: return@mapNotNull null
            val period = Period.entries.firstOrNull { it.dbName == r.periodType }
            val tied = db.recordDao().tiedAt(r.recordType, r.periodType, r.category, r.subcategory, r.value)
            Held(def, period, cat, r.subcategory, r, tied)
        }
        if (held.isEmpty()) return Story("🏆 Palmarès", "$name ne détient actuellement aucun #1 de record.")
        val byFamily = held.groupBy { RecordCatalog.groupOf[it.def.id] ?: com.novastats.app.domain.RecordGroup.TOTALS }
        val byRecord = held.groupBy { it.def }.entries.sortedByDescending { it.value.size }
        val byPeriod = held.groupBy { it.period }.entries.sortedByDescending { it.value.size }
        val shared = held.count { it.tied > 1 }
        val facts = ArrayList<Pair<String, String>>()
        facts += "Records détenus" to "${held.size} (dont $shared ${plural(shared, "partagé")})"
        facts += "Records différents" to "${byRecord.size} sur ${RecordCatalog.ALL.size - 1}"
        byFamily.entries.sortedByDescending { it.value.size }.forEach { (g, l) -> facts += "${g.emoji} ${g.label}" to "${l.size}" }
        byPeriod.forEach { (p, l) -> facts += "Période ${p?.frLabel ?: "sans période"}" to "${l.size}" }
        val top = byRecord.first()
        return Story(
            "🏆 ${held.size} ${plural(held.size, "record")} détenus",
            "$name est #1 de ${held.size} ${plural(held.size, "classement")} de records (chaque combinaison record × période × zone compte 1 ; les ex æquo comptent aussi). " +
                "Son point fort : ${top.key.emoji} ${top.key.title} avec ${top.value.size} ${plural(top.value.size, "classement")}. " +
                "Répartition par famille : " + byFamily.entries.sortedByDescending { it.value.size }.joinToString(", ") { "${it.value.size} ${it.key.label.lowercase()}" } + ". " +
                (if (shared > 0) "$shared de ces records sont partagés avec au moins un autre élément à égalité. " else "") +
                "La liste complète, groupée par famille, est ci-dessous — appuie sur un record pour ouvrir son classement.",
            facts, held = held
        )
    }

    private suspend fun hallOfFame(def: RecordDef, cat: RecordCategory, sub: String?, id: Long, name: String): Story {
        val all = db.hallOfFameDao().ofEntity(id, cat.dbName)
        val global = def.id == "MOST_GLOBAL"
        val rows = all.filter { (it.periodType == "GLOBAL") == global }.filter { !global || sub == null || sub == "ALL" || it.entryType == sub }.sortedBy { it.entryDate }
        val label = { t: String -> t.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() } }
        val items = rows.map { h ->
            Item(cat.dbName, id, "${label(h.entryType)} · ${h.periodType.lowercase().replaceFirstChar { it.uppercase() }}", on(Period.DAILY, h.entryDate) + (if (h.weeksAt1 > 0) " · ${h.weeksAt1} sem. au #1" else "") + (if (h.playCountAtEntry > 0) " · ${h.playCountAtEntry} ▶" else ""), null, true)
        }
        val byType = rows.groupBy { it.entryType }.map { (t, l) -> "${l.size}× ${label(t)}" }.joinToString(", ")
        return Story(
            "${def.emoji} ${rows.size} ${plural(rows.size, "entrée")} ${if (global) "Global" else "au Hall of Fame"}",
            "$name a été intronisé ${rows.size} fois ${if (global) "au niveau Global (toutes périodes confondues)" else "au Hall of Fame (hebdo + mensuel)"} : $byType. " +
                (rows.firstOrNull()?.let { "Première entrée ${on(Period.DAILY, it.entryDate)}" } ?: "") + (rows.lastOrNull()?.takeIf { rows.size > 1 }?.let { ", dernière ${on(Period.DAILY, it.entryDate)}." } ?: "."),
            listOf("Entrées" to "${rows.size}", "Détail" to byType.ifBlank { "—" }),
            itemsTitle = "Chaque intronisation", items = items
        )
    }

    private suspend fun multiChart(cat: RecordCategory, sub: String?, id: Long, name: String): Story {
        fun idx(s: List<RecordAppearance>) = s.associate { it.date to it.position }
        val d = idx(series(cat, Period.DAILY, id)); val w = idx(series(cat, Period.WEEKLY, id)); val m = idx(series(cat, Period.MONTHLY, id)); val y = idx(series(cat, Period.YEARLY, id))
        val days = db.billboardDao().snapshotDates(Period.DAILY.dbName)
        val pts = ArrayList<Point>()
        for (iso in days) {
            val date = Dates.parse(iso)
            val pd = d[iso]; val pw = w[Dates.weekOf(date).fromIso]; val pm = m[Dates.monthOf(date).fromIso]; val py = y[Dates.yearOf(date).fromIso]
            val ok = when (sub) {
                "DWMY" -> pd != null && pw != null && pm != null && py != null
                "DWM" -> pd != null && pw != null && pm != null
                "DW" -> pd != null && pw != null
                "WM" -> pw != null && pm != null
                "MY" -> pm != null && py != null
                else -> false
            }
            if (ok) pts += Point(iso, short(Period.DAILY, iso), pd ?: pw ?: pm, null, (pd ?: pw ?: pm ?: 99) <= 3, listOfNotNull(pd?.let { "D#$it" }, pw?.let { "W#$it" }, pm?.let { "M#$it" }, py?.let { "Y#$it" }).joinToString(" · "))
        }
        val combo = when (sub) { "DWMY" -> "quotidien + hebdo + mensuel + annuel"; "DWM" -> "quotidien + hebdo + mensuel"; "DW" -> "quotidien + hebdo"; "WM" -> "hebdo + mensuel"; "MY" -> "mensuel + annuel"; else -> sub ?: "" }
        val bestDay = pts.minByOrNull { it.position ?: 99 }
        return Story(
            "🌐 ${pts.size} ${plural(pts.size, "jour")} dans $combo à la fois",
            "Pendant ${pts.size} ${plural(pts.size, "jour")}, $name était classé simultanément dans les charts $combo" + (if (pts.isNotEmpty()) " — de ${short(Period.DAILY, pts.first().date)} à ${short(Period.DAILY, pts.last().date)}" else "") + ". " +
                (bestDay?.let { "Sa meilleure journée combinée : ${on(Period.DAILY, it.date)} (${it.note})." } ?: ""),
            listOf("Jours" to "${pts.size}", "Première fois" to (pts.firstOrNull()?.let { on(Period.DAILY, it.date) } ?: "—"), "Dernière fois" to (pts.lastOrNull()?.let { on(Period.DAILY, it.date) } ?: "—")),
            "Les jours concernés (positions D / W / M / Y)", pts
        )
    }
}
