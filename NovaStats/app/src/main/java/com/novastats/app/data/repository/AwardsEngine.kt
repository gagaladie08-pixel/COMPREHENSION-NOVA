package com.novastats.app.data.repository

import androidx.room.withTransaction
import com.novastats.app.data.db.NovaDatabase
import com.novastats.app.data.db.dao.IdCount
import com.novastats.app.data.db.entity.CertificationEntity
import com.novastats.app.data.db.entity.EntityType
import com.novastats.app.data.db.entity.NovaAwardEntity
import com.novastats.app.data.db.entity.NovaAwardHistoryEntity
import com.novastats.app.domain.AwardCategory
import com.novastats.app.domain.AwardRules
import com.novastats.app.domain.CertLevel
import com.novastats.app.domain.Certification
import com.novastats.app.domain.DateRange
import com.novastats.app.domain.Dates
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 🏆 Nova Awards — calcule les 9 récompenses d'une année.
 * Année en cours : recalculée à chaque ouverture (LIVE). Années passées : figées dans nova_awards_history (FINAL).
 */
class AwardsEngine(private val db: NovaDatabase) {

    private val monthFmt = DateTimeFormatter.ofPattern("MMMM", Locale.FRANCE)
    private val dayFmt = DateTimeFormatter.ofPattern("d MMMM", Locale.FRANCE)

    /** Recalcule toutes les années depuis la première écoute. Les années passées ne sont calculées qu'une fois. */
    suspend fun refreshAll(today: LocalDate = Dates.today()) {
        val first = db.dailyPlayDao().firstDate()?.let { Dates.parse(it) } ?: return
        for (year in first.year..today.year) {
            val final = year < today.year
            if (final && db.novaAwardDao().historyCount(year) > 0) continue
            compute(year, today)
        }
    }

    /** Force le recalcul complet (import, éditeur de données…). */
    suspend fun rebuildAll(today: LocalDate = Dates.today()) {
        db.novaAwardDao().clear(); db.novaAwardDao().clearHistory()
        refreshAll(today)
    }

    suspend fun compute(year: Int, today: LocalDate = Dates.today()) {
        val final = year < today.year
        val end = if (final) LocalDate.of(year, 12, 31) else today
        val range = DateRange(LocalDate.of(year, 1, 1), end)
        val from = range.fromIso; val to = range.toIso
        val out = mutableListOf<NovaAwardEntity>()
        fun add(cat: AwardCategory, winnerId: Long?, winnerType: String?, value: Double, message: String?) {
            out += NovaAwardEntity(year = year, category = cat.dbName, winnerId = winnerId, winnerType = winnerType, value = value, message = message, isFinal = final)
        }

        val totalPlays = db.dailyPlayDao().playsBetween(from, to)

        // 🎵 🎤 💿 — #1 des classements annuels (égalité départagée par le temps d'écoute, comme Stats)
        db.trackDao().topForPeriod(from, to, 1).first().firstOrNull()?.let { t ->
            add(AwardCategory.SONG_OF_YEAR, t.track.trackId, EntityType.TRACK, t.periodPlays.toDouble(),
                "Tu l'as écoutée ${t.periodPlays} fois — soit ${hm(t.periodDurationMs)} de ta vie 🔥")
        }
        db.artistDao().topForPeriod(from, to, 1).first().firstOrNull()?.let { a ->
            val pct = if (totalPlays > 0) 100.0 * a.periodPlays / totalPlays else 0.0
            add(AwardCategory.ARTIST_OF_YEAR, a.artist.artistId, EntityType.ARTIST, a.periodPlays.toDouble(),
                "${a.artist.name} t'a accompagné ${a.periodPlays} fois — ${String.format(Locale.FRANCE, "%.0f", pct)} % de toutes tes écoutes 👑")
        }
        db.albumDao().topForPeriod(from, to, 1).first().firstOrNull()?.let { al ->
            add(AwardCategory.ALBUM_OF_YEAR, al.album.albumId, EntityType.ALBUM, al.periodPlays.toDouble(),
                "Tu as tourné cet album en boucle — ${al.periodPlays} écoutes cette année 🎵")
        }

        // 📈 Plus grosse progression : 30 derniers jours de la période vs le reste de l'année
        val recentFrom = end.minusDays(29).coerceAtLeast(range.from)
        val recent = db.dailyPlayDao().artistPlaysBetween(recentFrom.format(Dates.ISO), to).associate { it.id to it.plays }
        val earlier = if (recentFrom > range.from) db.dailyPlayDao().artistPlaysBetween(from, recentFrom.minusDays(1).format(Dates.ISO)).associate { it.id to it.plays } else emptyMap()
        val rise = recent.entries.map { (id, r) -> Triple(id, r, AwardRules.riseScore(r, earlier[id] ?: 0)) }.filter { it.third > 0 }.maxWithOrNull(compareBy<Triple<Long, Int, Double>>({ it.third }, { it.second }))
        if (rise != null) {
            val name = db.artistDao().getById(rise.first)?.name ?: "—"
            val before = earlier[rise.first] ?: 0
            val msg = if (before == 0) "Inconnu en début d'année, ${rise.second} écoutes rien que sur les 30 derniers jours 🚀"
            else "$name : ${rise.second} écoutes sur les 30 derniers jours contre $before avant — ×${String.format(Locale.FRANCE, "%.1f", rise.third)} 🚀"
            add(AwardCategory.BIGGEST_RISE, rise.first, EntityType.ARTIST, rise.third, msg)
        }

        // 🆕 Révélation : découvert il y a moins de 6 mois (par rapport à la fin de période) + le plus écouté sur l'année
        val sixMonthsAgo = Dates.startOfDayMs(end.minusMonths(6))
        val yearPlays = db.dailyPlayDao().artistPlaysBetween(from, to).associateBy { it.id }
        val revelation = db.artistDao().allPlayedList()
            .filter { (it.firstPlayedAt ?: 0L) >= sixMonthsAgo && it.firstPlayedAt!! <= range.toMs }
            .mapNotNull { a -> yearPlays[a.artistId]?.let { a to it } }
            .maxWithOrNull(compareBy<Pair<com.novastats.app.data.db.entity.ArtistEntity, com.novastats.app.data.db.dao.IdCount>>({ it.second.plays }, { it.second.durationMs }))
        if (revelation != null) {
            val (a, p) = revelation
            add(AwardCategory.REVELATION, a.artistId, EntityType.ARTIST, p.plays.toDouble(),
                "Découvert en ${Dates.toLocalDate(a.firstPlayedAt!!).format(monthFmt)}, déjà ${p.plays} écoutes à son actif ✨")
        }

        // 🤝 Fidélité : artiste présent le plus de mois distincts
        db.dailyPlayDao().mostLoyalArtist(from, to)?.let { l ->
            val monthsElapsed = if (final) 12 else end.monthValue
            add(AwardCategory.LOYALTY, l.artistId, EntityType.ARTIST, l.months.toDouble(),
                "Présent ${l.months} mois sur $monthsElapsed — une fidélité sans faille 💙")
        }

        // 💎 Meilleure certification : chanson certifiée avec le plus d'écoutes
        val trackYearPlays = db.dailyPlayDao().trackPlaysBetween(from, to).associateBy { it.id }
        val certified = db.certificationDao().allCurrent().filter { it.entityType == EntityType.TRACK }
        val bestInYear: Pair<CertificationEntity, IdCount?>? = certified.mapNotNull { c -> trackYearPlays[c.entityId]?.let { c to it } }
            .maxWithOrNull(compareBy<Pair<CertificationEntity, IdCount>>({ it.second.plays }, { it.second.durationMs }))
        val best: Pair<CertificationEntity, IdCount?>? = bestInYear ?: certified.maxByOrNull { it.playCountAtCert }?.let { it to null }
        if (best != null) {
            val (c, p) = best
            val level = CertLevel.entries.firstOrNull { it.dbName == c.level }
            val label = level?.let { Certification(it, c.multiplier).label() } ?: c.level
            val total = db.trackDao().getById(c.entityId)?.playCount ?: p?.plays ?: 0
            add(AwardCategory.BEST_CERTIFICATION, c.entityId, EntityType.TRACK, (p?.plays ?: total).toDouble(),
                "Ton cheval de bataille — $total écoutes et un $label bien mérité 💎")
        }

        // 🔥 Plus long streak global sur l'année
        val (streak, streakEnd) = AwardRules.longestStreak(db.dailyPlayDao().activeDatesBetween(from, to))
        if (streak > 0) add(AwardCategory.LONGEST_STREAK, null, null, streak.toDouble(),
            "$streak jour${if (streak > 1) "s" else ""} consécutif${if (streak > 1) "s" else ""} sans manquer un seul jour 🔥" + (streakEnd?.let { " (jusqu'au ${Dates.parse(it).format(dayFmt)})" } ?: ""))

        // ⏱️ Session la plus longue
        db.sessionDao().longestBetween(range.fromMs, range.toMs)?.let { s ->
            add(AwardCategory.LONGEST_SESSION, s.sessionId, "SESSION", s.totalDurationMs.toDouble(),
                "Le ${Dates.toLocalDate(s.startedAt).format(dayFmt)} — ${hm(s.totalDurationMs)} de musique non-stop 🎧 (${s.trackCount} titres)")
        }

        // Publication atomique : les observers ne voient jamais l'année disparaître pendant le recalcul.
        db.withTransaction {
            db.novaAwardDao().clearYear(year)
            out.forEach { db.novaAwardDao().upsert(it) }
            if (final) {
                val now = System.currentTimeMillis()
                out.forEach { db.novaAwardDao().insertHistory(NovaAwardHistoryEntity(year = it.year, category = it.category, winnerId = it.winnerId, winnerType = it.winnerType, value = it.value, message = it.message, finalizedAt = now)) }
            }
        }
    }

    private fun hm(ms: Long): String {
        val h = ms / 3_600_000; val m = (ms % 3_600_000) / 60_000
        return if (h > 0) "${h}h ${String.format(Locale.FRANCE, "%02d", m)}min" else "${m} min"
    }
}
