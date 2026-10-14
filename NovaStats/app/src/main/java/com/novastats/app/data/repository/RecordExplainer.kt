package com.novastats.app.data.repository

import com.novastats.app.data.db.NovaDatabase
import com.novastats.app.data.db.dao.PriorRow
import com.novastats.app.data.db.dao.DayCount
import com.novastats.app.data.db.entity.EntityType
import com.novastats.app.domain.ArtistChartNumberOnes
import com.novastats.app.domain.TitleNormalizer
import com.novastats.app.domain.CertLevel
import com.novastats.app.domain.Dates
import com.novastats.app.domain.HallOfFameRules
import com.novastats.app.domain.PantheonStatus
import com.novastats.app.domain.PantheonRoute
import com.novastats.app.domain.Period
import com.novastats.app.domain.RecordAppearance
import com.novastats.app.domain.RecordCatalog
import com.novastats.app.domain.RecordCategory
import com.novastats.app.domain.RecordDef
import com.novastats.app.domain.RecordMath
import kotlinx.coroutines.flow.first
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 🏅 « Pourquoi cet élément est là ? » — construit, pour chaque record, une fiche spécifique :
 * un titre, un récit en français, des faits chiffrés, une frise des périodes concernées et les éléments impliqués
 * (titres d'un artiste, certifications, entrées Hall of Fame…). Tout est recalculé à partir des snapshots Billboard,
 * exactement comme le moteur des records, pour que l'explication colle à la valeur affichée.
 */
class RecordExplainer(private val db: NovaDatabase) {

    data class Point(val date: String, val label: String, val position: Int?, val plays: Int?, val highlight: Boolean = false, val note: String? = null)
    data class Item(
        val type: String,
        val id: Long,
        val name: String,
        val detail: String,
        val imageUrl: String?,
        val highlight: Boolean = false,
        /** Données structurées du premier passage, utilisées par la galerie premium des débuts. */
        val entryPosition: Int? = null,
        val entryDateLabel: String? = null,
        val entryPlays: Int? = null
    )
    /** Une case de la constellation Multi-Chart, au meilleur jour commun observé. */
    data class ChartRank(val code: String, val label: String, val position: Int?)
    data class MultiChartVisual(val stages: List<ChartRank>, val top2: Boolean, val bestDate: String?)
    data class SpeedStage(
        val key: String,
        val label: String,
        val date: String,
        val elapsedMs: Long,
        val splitMs: Long,
        val playCount: Int,
        val activeDays: Int,
        val route: String? = null
    )
    data class SpeedActivityBucket(val date: String, val label: String, val plays: Int, val activeDays: Int, val padding: Boolean = false)
    enum class SpeedCalendarGrain { DAYS, WEEKS, MONTHS }
    data class SpeedRival(
        val id: Long,
        val type: String,
        val name: String,
        val imageUrl: String?,
        val durationMs: Long,
        val gapMs: Long,
        val percentGap: Double,
        val tied: Boolean
    )
    data class SpeedVisual(
        val startDate: String,
        val finishDate: String,
        val elapsedMs: Long,
        val elapsedDays: Int,
        val activeDays: Int,
        val rank: Int,
        val boardSize: Int,
        val recordHolder: Boolean,
        val tiedAtTop: Int,
        val rival: SpeedRival?,
        val stages: List<SpeedStage>,
        val calendarGrain: SpeedCalendarGrain,
        val calendar: List<SpeedActivityBucket>
    )
    /** Une étape compressée de la frise : mêmes éléments au #1 sur des périodes consécutives. */
    data class SuccessiveSegment(
        val id: Long,
        val type: String,
        val name: String,
        val imageUrl: String?,
        val firstDate: String,
        val lastDate: String,
        val periodCount: Int,
        val durationLabel: String
    )
    /** Cause visible de fin de série; l'élément reste null quand l'archive ne permet pas d'identifier le #1. */
    data class SuccessiveStopper(
        val name: String?,
        val type: String?,
        val id: Long?,
        val imageUrl: String?,
        val ownerName: String?,
        val date: String?,
        val direct: Boolean,
        val explanation: String
    )
    data class SuccessiveRival(
        val id: Long,
        val name: String,
        val imageUrl: String?,
        val score: Int,
        val gap: Int,
        val tied: Boolean,
        val ahead: Boolean
    )
    /** Carte premium dédiée à Most Successive #1. */
    data class SuccessiveVisual(
        val distinctCount: Int,
        val periodCount: Int,
        val durationLabel: String,
        val itemLabel: String,
        val contextLabel: String,
        val startDate: String,
        val endDate: String,
        val startShort: String,
        val endShort: String,
        val continuing: Boolean,
        val statusLine: String,
        val rank: Int,
        val boardSize: Int,
        val recordHolder: Boolean,
        val tiedAtTop: Int,
        val rival: SuccessiveRival?,
        val segments: List<SuccessiveSegment>,
        val stopper: SuccessiveStopper?
    )
    enum class PresenceBand { NUMBER_ONE, TOP_FIVE, TOP_TEN, REST }
    data class PresenceCell(
        val periodIndex: Int,
        val date: String,
        val label: String,
        val position: Int,
        val band: PresenceBand,
        val gapBefore: Int,
        /** 1..N pour les cellules du plus long règne / passage continu, 0 pour les autres. */
        val longestRunStep: Int
    )
    data class PresenceRival(
        val id: Long,
        val name: String,
        val imageUrl: String?,
        val score: Int,
        val gap: Int,
        val tied: Boolean,
        val ahead: Boolean
    )
    data class PresenceMilestone(
        val reachedLabel: String,
        val reachedEmoji: String,
        val nextLabel: String?,
        val nextThreshold: Int?,
        val remaining: Int,
        val progress: Float
    )
    /** Données premium communes aux quatre records de présence dans le chart. */
    data class PresenceVisual(
        val contextLabel: String,
        val recordLabel: String,
        val score: Int,
        val scoreUnit: String,
        val periodUnit: String,
        val chartCount: Int,
        val top10Count: Int,
        val top5Count: Int,
        val numberOneCount: Int,
        val chartCoveragePercent: Int,
        val top10ConversionPercent: Int,
        val top5ConversionPercent: Int,
        val numberOneConversionPercent: Int,
        val lastAppearance: String?,
        val lastTop10: String?,
        val lastTop5: String?,
        val lastNumberOne: String?,
        val latestChartDate: String?,
        val activeAtLatest: Boolean,
        val statusTitle: String,
        val statusDetail: String,
        val currentRun: Int,
        val longestRun: Int,
        val longestRunStart: String?,
        val longestRunEnd: String?,
        val rank: Int?,
        val recordHolder: Boolean,
        val tiedAtTop: Int,
        val rival: PresenceRival?,
        val milestone: PresenceMilestone,
        val isNumberOneRecord: Boolean,
        val crownCeremony: Boolean,
        val cells: List<PresenceCell>
    )
    /** Jalons du meilleur classement d'un titre/album signature pour les records de présence par éléments distincts. */
    data class SongReachTrack(
        val id: Long,
        val name: String,
        val imageUrl: String?,
        val peakPosition: Int,
        val peakDate: String,
        val firstChartDate: String,
        val firstTop10Date: String?,
        val firstTop5Date: String?,
        val firstNumberOneDate: String?,
        val chartPeriods: Int,
        val top10Periods: Int,
        val top5Periods: Int,
        val numberOnePeriods: Int
    )
    data class SongReachRival(
        val id: Long,
        val name: String,
        val imageUrl: String?,
        val rank: Int,
        val score: Int,
        val chartCount: Int,
        val top10Count: Int,
        val top5Count: Int,
        val numberOneCount: Int,
        val tied: Boolean
    )
    /** Échelle cumulative par éléments uniques, face-à-face et trajectoires des titres signatures. */
    data class SongReachVisual(
        val contextLabel: String,
        val recordTitle: String,
        val itemLabel: String,
        val score: Int,
        val chartCount: Int,
        val top10Count: Int,
        val top5Count: Int,
        val numberOneCount: Int,
        val top10ConversionPercent: Int?,
        val top5ConversionPercent: Int?,
        val numberOneConversionPercent: Int?,
        val rank: Int,
        val tiedAtTop: Int,
        val rival: SongReachRival?,
        val featuredTracks: List<SongReachTrack>
    )
    /** Un pilier du record cumulatif #1 — compte, dernier #1 et élément qui l'a porté. */
    data class ArtistNoOnePillar(
        val key: String,
        val label: String,
        val emoji: String,
        val count: Int,
        val latestNumberOne: String?,
        val latestImageUrl: String?,
        val latestDate: String?
    )
    data class ArtistNoOneHeatCell(val date: String, val activeCharts: Set<String>)
    data class ArtistNoOneContributionItem(val id: Long, val name: String, val imageUrl: String?, val creditedArtists: String? = null)
    data class ArtistNoOneContribution(
        val dateIso: String,
        val dateLabel: String,
        val hot100Track: ArtistNoOneContributionItem?,
        val albums75Album: ArtistNoOneContributionItem?
    )
    data class ArtistNoOneRival(
        val id: Long,
        val name: String,
        val imageUrl: String?,
        val rank: Int,
        val score: Int,
        val pillarCounts: Map<String, Int>,
        val tied: Boolean
    )
    /** Trio de charts à l'origine du record, comparaison avec le dauphin et bande chronologique. */
    data class ArtistCumulativeNoOneVisual(
        val period: Period,
        val selectedChart: String,
        val selectedChartLabel: String,
        val score: Int,
        val rank: Int,
        val boardSize: Int,
        val tiedAtTop: Int,
        val pillars: List<ArtistNoOnePillar>,
        val rival: ArtistNoOneRival?,
        val heatmap: List<ArtistNoOneHeatCell>,
        val contributions: List<ArtistNoOneContribution>
    )
    data class PerfectAllKillChartMargin(
        val chartLabel: String,
        val leaderPlays: Int,
        val runnerUpName: String?,
        val runnerUpPlays: Int?,
        val gap: Int?
    )
    data class PerfectAllKillRecordRival(
        val id: Long,
        val name: String,
        val imageUrl: String?,
        val rank: Int,
        val score: Int,
        val tied: Boolean
    )
    /** Classement d'une cadence, frise des dates, duel séparé et derniers événements détaillés. */
    data class PerfectAllKillHeatCell(val dateIso: String, val isPak: Boolean)
    data class PerfectAllKillRecordVisual(
        val period: Period,
        val score: Int,
        val rank: Int,
        val tiedAtTop: Int,
        val rival: PerfectAllKillRecordRival?,
        val dates: List<String>,
        val heatmap: List<PerfectAllKillHeatCell>,
        val featuredEvents: List<PerfectAllKillVisual>
    )
    /** Les trois #1 simultanés réunis autour du même artiste et de la même cadence. */
    data class PerfectAllKillVisual(
        val periodType: String,
        val cadenceLabel: String,
        val dateLabel: String,
        val dateIso: String,
        val artistId: Long,
        val artistName: String,
        val artistImageUrl: String?,
        val trackId: Long,
        val trackName: String,
        val trackImageUrl: String?,
        val albumId: Long,
        val albumName: String,
        val albumImageUrl: String?,
        val margins: List<PerfectAllKillChartMargin> = emptyList()
    )
    /** Une distinction HoF/Global et son contexte temporel — données calculées depuis les intronisations existantes. */
    data class HonorEntryVisual(
        val sealKey: String,
        val title: String,
        val category: String,
        val date: String,
        val periodLabel: String,
        val reign: String?,
        val reignDuration: String?,
        val weeksAtOne: Int,
        val playsAtEntry: Int
    )
    data class HonorSealVisual(
        val key: String,
        val title: String,
        val emoji: String,
        val description: String,
        val earnedDate: String?,
        val detail: String?,
        val distinctAwardees: Int
    )
    data class HonorRivalVisual(
        val id: Long,
        val name: String,
        val imageUrl: String?,
        val rank: Int,
        val score: Int,
        val earnedKeys: List<String>,
        val tied: Boolean
    )
    /** Présentation premium dédiée au Hall of Fame et aux distinctions Global. */
    data class HonorVisual(
        val isGlobal: Boolean,
        val selectedSub: String?,
        val score: Int,
        val maxScore: Int,
        val rank: Int,
        val boardSize: Int,
        val tiedAtTop: Int,
        val rival: HonorRivalVisual?,
        val seals: List<HonorSealVisual>,
        val entries: List<HonorEntryVisual>
    )
    private data class SpeedStageSource(
        val key: String,
        val label: String,
        val at: Long,
        val plays: Int,
        val route: String? = null
    )
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
        val context: List<Pair<String, String>> = emptyList(),
        /** Séries d'écoute : afficher la frise en calendrier de chaleur (un carré par jour, intensité = écoutes). */
        val heat: Boolean = false,
        /** Simultanéité : les sièges de la zone occupés la période record (visual « le chart ce jour-là »). */
        val seats: Seats? = null,
        /** Suffixe des valeurs de la frise (défaut « ▶ ») — ex. « siège(s) » pour la skyline de domination. */
        val timelineValueSuffix: String? = null,
        /** 🌐 Carte premium « constellation » du meilleur jour multi-chart. */
        val multiChartVisual: MultiChartVisual? = null,
        /** ⚡ Fiche premium chrono : rival, splits d'étapes, voie Panthéon et calendrier d'activité. */
        val speedVisual: SpeedVisual? = null,
        /** 🔁 Carte, frise compressée, statut et cause de rupture propres à Most Successive #1. */
        val successiveVisual: SuccessiveVisual? = null,
        /** 👑 Funnel, comparaison, statut, jalons et frise des rangs pour les quatre records de présence. */
        val presenceVisual: PresenceVisual? = null,
        /** 🎼 Échelle chart → Top 10 → Top 5 → #1 pour les records de titres/albums distincts. */
        val songReachVisual: SongReachVisual? = null,
        /** 💎 Deux charts de Most Cumulative #1, éléments contributifs, duel et heatmap par cadence. */
        val artistCumulativeNoOneVisual: ArtistCumulativeNoOneVisual? = null,
        /** 💎 Record Most Perfect All Kill, classement par cadence, rival, frise et événements. */
        val perfectAllKillRecordVisual: PerfectAllKillRecordVisual? = null,
        /** 👑 Galerie chronologique HoF et constellation Global, réservées à MOST_HOF/MOST_GLOBAL. */
        val honorVisual: HonorVisual? = null
    )

    /** 🪑 Sièges d'une zone : [total] places, [occupied] = positions occupées par le propriétaire. */
    data class Seats(val total: Int, val occupied: List<Int>)

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

    /** Résout la trinité d'un événement PAK; les dauphins ne sont chargés que pour les vues premium détaillées. */
    suspend fun perfectAllKillVisual(
        periodType: String,
        date: String,
        artistId: Long,
        includeMargins: Boolean = false
    ): PerfectAllKillVisual? {
        val period = Period.entries.firstOrNull { it.dbName == periodType } ?: return null
        if (period !in listOf(Period.DAILY, Period.WEEKLY, Period.MONTHLY, Period.YEARLY)) return null
        val billboard = db.billboardDao()
        val trackId = billboard.numberOneTrack(periodType, date) ?: return null
        val topArtistId = billboard.numberOneArtist(periodType, date) ?: return null
        val albumId = billboard.numberOneAlbum(periodType, date) ?: return null
        if (topArtistId != artistId) return null
        val track = db.trackDao().getById(trackId) ?: return null
        val artist = db.artistDao().getById(artistId) ?: return null
        val album = db.albumDao().getById(albumId) ?: return null
        val creditedArtistIds = db.trackLinkDao().artistIdsForTrack(trackId).toMutableSet().apply { add(track.artistId) }
        if (artistId !in creditedArtistIds || album.artistId != artistId) return null
        val margins = if (!includeMargins) emptyList() else {
            suspend fun margin(
                label: String,
                leader: com.novastats.app.data.db.dao.RunnerUpRow?,
                runner: com.novastats.app.data.db.dao.RunnerUpRow?
            ): PerfectAllKillChartMargin? {
                val first = leader ?: return null
                return PerfectAllKillChartMargin(
                    chartLabel = label,
                    leaderPlays = first.playCount,
                    runnerUpName = runner?.name,
                    runnerUpPlays = runner?.playCount,
                    gap = runner?.let { second -> (first.playCount - second.playCount).coerceAtLeast(0) }
                )
            }
            listOfNotNull(
                margin("Hot 100", billboard.numberOneTrackDetails(periodType, date), billboard.runnerUpTrack(periodType, date)),
                margin("Artist 50", billboard.numberOneArtistDetails(periodType, date), billboard.runnerUpArtist(periodType, date)),
                margin("Albums 75", billboard.numberOneAlbumDetails(periodType, date), billboard.runnerUpAlbum(periodType, date))
            )
        }
        return PerfectAllKillVisual(
            periodType = periodType,
            cadenceLabel = period.label,
            dateLabel = on(period, date),
            dateIso = date,
            artistId = artist.artistId,
            artistName = artist.name,
            artistImageUrl = artist.photoUrl,
            trackId = track.trackId,
            trackName = track.title,
            trackImageUrl = track.coverUrl,
            albumId = album.albumId,
            albumName = album.title,
            albumImageUrl = album.coverUrl,
            margins = margins
        )
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

    private data class PresenceTierSpec(val threshold: Int, val label: String, val emoji: String)

    private fun presenceMilestone(p: Period, score: Int): PresenceMilestone {
        val tiers = when (p) {
            Period.DAILY -> listOf(
                PresenceTierSpec(7, "Présence installée", "✨"), PresenceTierSpec(30, "Pilier du chart", "🔥"),
                PresenceTierSpec(90, "Dominant", "👑"), PresenceTierSpec(180, "Icône", "💎"), PresenceTierSpec(365, "Légende", "🏆")
            )
            Period.WEEKLY -> listOf(
                PresenceTierSpec(4, "Présence installée", "✨"), PresenceTierSpec(13, "Pilier du chart", "🔥"),
                PresenceTierSpec(26, "Dominant", "👑"), PresenceTierSpec(52, "Icône", "💎"), PresenceTierSpec(104, "Légende", "🏆")
            )
            Period.MONTHLY -> listOf(
                PresenceTierSpec(3, "Présence installée", "✨"), PresenceTierSpec(6, "Pilier du chart", "🔥"),
                PresenceTierSpec(12, "Dominant", "👑"), PresenceTierSpec(24, "Icône", "💎"), PresenceTierSpec(60, "Légende", "🏆")
            )
            Period.YEARLY -> listOf(
                PresenceTierSpec(2, "Présence installée", "✨"), PresenceTierSpec(3, "Pilier du chart", "🔥"),
                PresenceTierSpec(5, "Dominant", "👑"), PresenceTierSpec(10, "Icône", "💎"), PresenceTierSpec(20, "Légende", "🏆")
            )
            Period.GLOBAL -> emptyList()
        }
        val reachedIndex = tiers.indexOfLast { score >= it.threshold }
        val reached = tiers.getOrNull(reachedIndex)
        val next = tiers.getOrNull(reachedIndex + 1)
        val previousThreshold = reached?.threshold ?: 0
        val progress = if (next == null) 1f else
            ((score - previousThreshold).toFloat() / (next.threshold - previousThreshold).coerceAtLeast(1)).coerceIn(0f, 1f)
        return PresenceMilestone(
            reachedLabel = reached?.label ?: "En progression",
            reachedEmoji = reached?.emoji ?: "🌱",
            nextLabel = next?.label,
            nextThreshold = next?.threshold,
            remaining = next?.let { (it.threshold - score).coerceAtLeast(0) } ?: 0,
            progress = progress
        )
    }

    private suspend fun presenceVisual(
        def: RecordDef,
        p: Period,
        cat: RecordCategory,
        id: Long,
        value: Double,
        s: List<RecordAppearance>
    ): PresenceVisual {
        val qualifies: (RecordAppearance) -> Boolean = when (def.id) {
            "MOST_CUMULATIVE_TOP10" -> ({ it.position <= 10 })
            "MOST_CUMULATIVE_TOP5" -> ({ it.position <= 5 })
            "MOST_TIME_AT_1" -> ({ it.position == 1 })
            else -> ({ true })
        }
        val zoneLabel = when (def.id) {
            "MOST_CUMULATIVE_TOP10" -> "Top 10"
            "MOST_CUMULATIVE_TOP5" -> "Top 5"
            "MOST_TIME_AT_1" -> "#1"
            else -> "chart"
        }
        val all = allSeries(cat, p)
        val latest = all.values.asSequence().flatten()
            .maxWithOrNull(compareBy<RecordAppearance> { it.periodIndex }.thenBy { it.date })
        val rows = db.recordDao().top10(def.id, p.dbName, cat.dbName, null, asc = false).first()
        val myScore = rows.firstOrNull { it.r.entityId == id }?.r?.value?.toInt() ?: value.toInt()
        val leaderScore = rows.maxOfOrNull { it.r.value.toInt() } ?: myScore
        val holder = myScore == leaderScore
        val tiedAtTop = rows.count { it.r.value.toInt() == leaderScore }.coerceAtLeast(1)
        val rank = rows.indexOfFirst { it.r.entityId == id }.takeIf { it >= 0 }?.plus(1)
            ?: (rows.count { it.r.value > myScore.toDouble() } + 1)
        val peer = rows.firstOrNull { it.r.entityId != id && it.r.value.toInt() == myScore }
        val rivalRow = peer ?: if (holder) {
            rows.firstOrNull { it.r.value.toInt() < myScore }
        } else {
            rows.lastOrNull { it.r.value > myScore.toDouble() }
        }
        val rival = rivalRow?.let {
            val rivalScore = it.r.value.toInt()
            PresenceRival(
                id = it.r.entityId,
                name = it.name ?: "Inconnu",
                imageUrl = it.imageUrl,
                score = rivalScore,
                gap = kotlin.math.abs(rivalScore - myScore),
                tied = rivalScore == myScore,
                ahead = rivalScore > myScore
            )
        }

        val top10 = s.count { it.position <= 10 }
        val top5 = s.count { it.position <= 5 }
        val numberOnes = s.count { it.position == 1 }
        val span = (s.last().periodIndex - s.first().periodIndex + 1).coerceAtLeast(1)
        val lastAppearance = s.lastOrNull()
        val lastQualified = s.lastOrNull(qualifies)
        val lastTop10 = s.lastOrNull { it.position <= 10 }
        val lastTop5 = s.lastOrNull { it.position <= 5 }
        val lastNumberOne = s.lastOrNull { it.position == 1 }
        val activeAtLatest = latest != null && lastAppearance != null && lastAppearance.periodIndex == latest.periodIndex && qualifies(lastAppearance)
        val bestRun = longestRun(s, qualifies)
        var currentRun = 0
        if (activeAtLatest) {
            var previous: Int? = null
            for (appearance in s.asReversed()) {
                if (!qualifies(appearance) || (previous != null && appearance.periodIndex != previous - 1)) break
                currentRun++
                previous = appearance.periodIndex
            }
        }
        val runSteps = bestRun.mapIndexed { index, appearance -> appearance.periodIndex to index + 1 }.toMap()
        var previousIndex: Int? = null
        val cells = s.map { appearance ->
            val gap = previousIndex?.let { (appearance.periodIndex - it - 1).coerceAtLeast(0) } ?: 0
            previousIndex = appearance.periodIndex
            PresenceCell(
                periodIndex = appearance.periodIndex,
                date = appearance.date,
                label = short(p, appearance.date),
                position = appearance.position,
                band = when {
                    appearance.position == 1 -> PresenceBand.NUMBER_ONE
                    appearance.position <= 5 -> PresenceBand.TOP_FIVE
                    appearance.position <= 10 -> PresenceBand.TOP_TEN
                    else -> PresenceBand.REST
                },
                gapBefore = gap,
                longestRunStep = runSteps[appearance.periodIndex] ?: 0
            )
        }
        val latestDateLabel = latest?.date?.let { on(p, it) }
        val qualifiedDateLabel = lastQualified?.date?.let { on(p, it) }
        val statusDetail = if (activeAtLatest) {
            "Au $zoneLabel dans le dernier relevé${latestDateLabel?.let { " ($it)" } ?: ""} · série active de $currentRun ${unit(p, currentRun)}."
        } else {
            val state = if (lastAppearance?.periodIndex == latest?.periodIndex) "Hors du $zoneLabel au dernier relevé" else "Absent du dernier relevé"
            "$state${latestDateLabel?.let { " ($it)" } ?: ""}. " +
                (qualifiedDateLabel?.let { "Dernier passage au $zoneLabel : $it." } ?: "Aucun passage dans cette zone.")
        }
        val scoreLabel = when (def.id) {
            "MOST_CUMULATIVE_TOP10" -> "Périodes dans le Top 10"
            "MOST_CUMULATIVE_TOP5" -> "Périodes dans le Top 5"
            "MOST_TIME_AT_1" -> "Périodes au #1"
            else -> "Périodes dans le chart"
        }
        val percentages = { numerator: Int, denominator: Int -> if (denominator <= 0) 0 else numerator * 100 / denominator }
        val scoreInt = value.toInt()
        return PresenceVisual(
            contextLabel = "${p.frLabel} · ${cat.label}",
            recordLabel = scoreLabel,
            score = scoreInt,
            scoreUnit = "${scoreInt} ${unit(p, scoreInt)}",
            periodUnit = unit(p, 2),
            chartCount = s.size,
            top10Count = top10,
            top5Count = top5,
            numberOneCount = numberOnes,
            chartCoveragePercent = percentages(s.size, span),
            top10ConversionPercent = percentages(top10, s.size),
            top5ConversionPercent = percentages(top5, top10),
            numberOneConversionPercent = percentages(numberOnes, top5),
            lastAppearance = lastAppearance?.date?.let { on(p, it) },
            lastTop10 = lastTop10?.date?.let { on(p, it) },
            lastTop5 = lastTop5?.date?.let { on(p, it) },
            lastNumberOne = lastNumberOne?.date?.let { on(p, it) },
            latestChartDate = latestDateLabel,
            activeAtLatest = activeAtLatest,
            statusTitle = if (activeAtLatest) "Série active au dernier relevé" else "Série terminée ou hors zone",
            statusDetail = statusDetail,
            currentRun = currentRun,
            longestRun = bestRun.size,
            longestRunStart = bestRun.firstOrNull()?.date?.let { short(p, it) },
            longestRunEnd = bestRun.lastOrNull()?.date?.let { short(p, it) },
            rank = rank,
            recordHolder = holder,
            tiedAtTop = tiedAtTop,
            rival = rival,
            milestone = presenceMilestone(p, scoreInt),
            isNumberOneRecord = def.id == "MOST_TIME_AT_1",
            crownCeremony = def.id == "MOST_TIME_AT_1" && holder,
            cells = cells
        )
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
                val artist = al.artistId?.let { db.artistDao().getById(it)?.name } ?: TitleNormalizer.SHARED_ALBUM_LABEL
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
                if (top != null && al.playCount > 0) {
                    val share = (100L * top.track.playCount.toLong() / al.playCount.toLong()).coerceAtMost(100L)
                    sb.append(" Son titre moteur : « ${top.track.title} » (${top.track.playCount} ▶, $share % des écoutes de l'album).")
                }
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
            "MOST_CUMULATIVE_ARTIST_NO1" -> artistCumulativeNoOne(period ?: Period.WEEKLY, sub, id, name, value)
            "MOST_PERFECT_ALL_KILL" -> perfectAllKillRecord(period ?: Period.DAILY, id, name, value)
            "MOST_CUMULATIVE", "MOST_CUMULATIVE_TOP10", "MOST_CUMULATIVE_TOP5", "MOST_TIME_AT_1", "MOST_CONSISTENT", "MOST_BLOCKED_TOP5",
            "BIGGEST_JUMP", "BIGGEST_FALL", "BIGGEST_COMEBACK", "BIGGEST_CLIMBER", "SLEEPER_HIT", "FASTEST_RISE", "LONGEST_ROAD",
            "BIGGEST_PERIOD", "BIGGEST_DEBUT", "LONGEST_LIFESPAN", "MOST_REENTRIES", "LONGEST_ABSENCE_RETURN" -> chartStory(def, period ?: Period.WEEKLY, cat, sub, id, name, value)
            "PODIUM_SWEEP" -> podiumSweep(period ?: Period.WEEKLY, cat, sub, id, name)
            "LONGEST_LISTENING_STREAK" -> listeningStreak(cat, id, name)
            "MOST_RECORDS" -> mostRecords(cat, id, name)
            "MOST_SONGS_IN_CHART", "MOST_SONGS_TOP10", "MOST_SONGS_TOP5", "MOST_SONGS_AT_1", "MOST_DEBUT_1", "MOST_DEBUT_TOP5", "MOST_DEBUT_TOP10", "MOST_SIMULTANEOUS", "MOST_SUCCESSIVE_1" ->
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
        val chartBased = period != null && def.id !in setOf("FASTEST_CERT", "FASTEST_PANTHEON", "MOST_CERTIFICATIONS", "MOST_HOF", "MOST_GLOBAL", "MULTI_CHART", "MOST_SUCCESSIVE_1", "MOST_CUMULATIVE_ARTIST_NO1", "MOST_PERFECT_ALL_KILL")
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
                        if (t.playCount > 0) {
                            val chartShare = (100L * inChart.toLong() / t.playCount.toLong()).coerceIn(0L, 100L)
                            sb.append("Sur ses ${t.playCount} écoutes au total, $inChart ($chartShare %) ont eu lieu pendant ses périodes classées. ")
                        }
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

    /** Fiche du record PAK : score sur une seule cadence, rang, rival proche, frise et derniers événements. */
    private suspend fun perfectAllKillRecord(
        period: Period,
        id: Long,
        name: String,
        cacheValue: Double
    ): Story {
        val periodEvents = db.hallOfFameDao().ofEntity(id, EntityType.ARTIST)
            .filter { it.entryType == HallOfFameRules.PERFECT_ALL_KILL && it.periodType == period.dbName }
        val dates = periodEvents.map { it.entryDate }.distinct().sorted()
        val pakDateSet = dates.toSet()
        val heatmap = db.billboardDao().snapshotDates(period.dbName)
            .map { date -> PerfectAllKillHeatCell(dateIso = date, isPak = date in pakDateSet) }
        val score = dates.size.takeIf { it > 0 } ?: cacheValue.toInt().coerceAtLeast(0)
        val dao = db.recordDao()
        val type = "MOST_PERFECT_ALL_KILL"
        val category = RecordCategory.ARTIST.dbName
        val rank = dao.betterCount(type, period.dbName, category, null, score.toDouble(), asc = false) + 1
        val tiedAtTop = dao.tiedAt(type, period.dbName, category, null, score.toDouble())
        val topRows = dao.top10(type, period.dbName, category, null, asc = false, limit = 20).first()
        val rivalRow = topRows.filterNot { it.r.entityId == id }
            .minByOrNull { kotlin.math.abs(it.r.value - score.toDouble()) }
        val rival = rivalRow?.let { row ->
            val rivalRank = dao.betterCount(type, period.dbName, category, null, row.r.value, asc = false) + 1
            PerfectAllKillRecordRival(
                id = row.r.entityId,
                name = row.name ?: "Inconnu",
                imageUrl = row.imageUrl,
                rank = rivalRank,
                score = row.r.value.toInt(),
                tied = row.r.value.toInt() == score
            )
        }
        val latestEvents = dates.lastOrNull()?.let { date ->
            perfectAllKillVisual(period.dbName, date, id, includeMargins = true)?.let { listOf(it) }
        }.orEmpty()
        val visual = PerfectAllKillRecordVisual(
            period = period,
            score = score,
            rank = rank,
            tiedAtTop = tiedAtTop,
            rival = rival,
            dates = dates,
            heatmap = heatmap,
            featuredEvents = latestEvents
        )
        val rivalText = rival?.let { "Le rival le plus proche est ${it.name} (#${it.rank}, ${it.score} événement${if (it.score == 1) "" else "s"})." }
            ?: "Aucun rival n'est encore classé sur cette cadence."
        val facts = listOf(
            "Cadence" to period.label,
            "Classement" to "#$rank" + (if (tiedAtTop > 1) " · $tiedAtTop ex æquo en tête" else ""),
            "Dates PAK" to "${dates.size} événement${if (dates.size == 1) "" else "s"}",
            "Rival" to (rival?.let { "${it.name} · #${it.rank} · ${it.score}" } ?: "—")
        )
        return Story(
            headline = "💎 $score Perfect All Kill · ${period.label}",
            narrative = "$name a obtenu $score Perfect All Kill sur la cadence ${period.label.lowercase()}. " +
                "Chaque événement correspond à la simultanéité Hot 100 + Artist 50 + Albums 75, au même snapshot. " + rivalText + " Les cadences restent indépendantes.",
            facts = facts,
            perfectAllKillRecordVisual = visual
        )
    }

    /** Explication dédiée : deux cumuls parallèles, éléments #1, rang, rival et calendrier de présence. */
    private suspend fun artistCumulativeNoOne(
        period: Period,
        sub: String?,
        id: Long,
        name: String,
        cacheValue: Double
    ): Story {
        val selectedChart = sub?.takeIf { it in ArtistChartNumberOnes.cumulativeCharts } ?: ArtistChartNumberOnes.HOT_100
        val activeArtistRows = db.artistDao().allPlayedList()
        val activeArtists = activeArtistRows.mapTo(HashSet()) { it.artistId }
        val artistNames = activeArtistRows.associate { it.artistId to it.name }
        val creditedByTrack = HashMap<Long, MutableSet<Long>>()
        db.trackLinkDao().allTrackArtists().forEach { link ->
            creditedByTrack.getOrPut(link.trackId) { HashSet() }.add(link.artistId)
        }
        val tracks = db.trackDao().allPlayed()
        tracks.forEach { track -> creditedByTrack.getOrPut(track.trackId) { HashSet() }.add(track.artistId) }
        val tracksById = tracks.associateBy { it.trackId }
        val albums = db.albumDao().all()
        val albumOwners = albums.associate { it.albumId to it.artistId }
        val albumsById = albums.associateBy { it.albumId }
        val dao = db.billboardDao()
        val trackRows = dao.allTrackRows(period.dbName)
        val albumRows = dao.allAlbumRows(period.dbName)
        val hot100Leaders = trackRows.filter { it.position == 1 }.associate { it.date to it.entityId }
        val albums75Leaders = albumRows.filter { it.position == 1 }.associate { it.date to it.entityId }
        val dates = dao.snapshotDates(period.dbName)
        val snapshots = dates.map { date ->
            val hot100Artists = hot100Leaders[date]?.let { trackId ->
                creditedByTrack[trackId].orEmpty().filterTo(linkedSetOf()) { it in activeArtists }
            }.orEmpty()
            val albumOwner = albums75Leaders[date]?.let { albumOwners[it] }?.takeIf { it in activeArtists }
            ArtistChartNumberOnes.Snapshot(date, hot100Artists, artist50LeaderId = null, albums75ArtistIds = setOfNotNull(albumOwner))
        }
        val cumulative = ArtistChartNumberOnes.cumulativeDates(snapshots)

        val labels = mapOf(
            ArtistChartNumberOnes.HOT_100 to ("Hot 100" to "🔥"),
            ArtistChartNumberOnes.ALBUMS_75 to ("Albums 75" to "💿")
        )
        suspend fun pillarsFor(artistId: Long): List<ArtistNoOnePillar> = ArtistChartNumberOnes.cumulativeCharts.map { chart ->
            val earnedDates = cumulative[chart]?.get(artistId).orEmpty()
            val latest = earnedDates.maxOrNull()
            val (latestName, latestImage) = when (chart) {
                ArtistChartNumberOnes.HOT_100 -> hot100Leaders[latest]?.let { tracksById[it] }
                    ?.let { it.title to it.coverUrl } ?: (null to null)
                else -> albums75Leaders[latest]?.let { albumsById[it] }
                    ?.let { it.title to it.coverUrl } ?: (null to null)
            }
            val (label, emoji) = labels.getValue(chart)
            ArtistNoOnePillar(chart, label, emoji, earnedDates.size, latestName, latestImage, latest?.let { on(period, it) })
        }

        val pillars = pillarsFor(id)
        val score = pillars.firstOrNull { it.key == selectedChart }?.count ?: cacheValue.toInt()
        val recordDao = db.recordDao()
        val rank = recordDao.betterCount("MOST_CUMULATIVE_ARTIST_NO1", period.dbName, RecordCategory.ARTIST.dbName, selectedChart, score.toDouble(), asc = false) + 1
        val tiedAtTop = recordDao.tiedAt("MOST_CUMULATIVE_ARTIST_NO1", period.dbName, RecordCategory.ARTIST.dbName, selectedChart, score.toDouble())
        val topRows = recordDao.top10("MOST_CUMULATIVE_ARTIST_NO1", period.dbName, RecordCategory.ARTIST.dbName, selectedChart, asc = false).first()
        val rivalRow = topRows.filterNot { it.r.entityId == id }.minByOrNull { kotlin.math.abs(it.r.value - score.toDouble()) }
        val rival = rivalRow?.let { row ->
            val rivalRank = recordDao.betterCount("MOST_CUMULATIVE_ARTIST_NO1", period.dbName, RecordCategory.ARTIST.dbName, selectedChart, row.r.value, asc = false) + 1
            ArtistNoOneRival(
                id = row.r.entityId,
                name = row.name ?: "Inconnu",
                imageUrl = row.imageUrl,
                rank = rivalRank,
                score = row.r.value.toInt(),
                pillarCounts = pillarsFor(row.r.entityId).associate { it.key to it.count },
                tied = row.r.value.toInt() == score
            )
        }
        val heatmap = snapshots.map { snapshot ->
            val activeCharts = buildSet {
                if (id in snapshot.hot100ArtistIds) add(ArtistChartNumberOnes.HOT_100)
                if (id in snapshot.albums75ArtistIds) add(ArtistChartNumberOnes.ALBUMS_75)
            }
            ArtistNoOneHeatCell(snapshot.date, activeCharts)
        }
        val contributions = snapshots.mapNotNull { snapshot ->
            val track = if (id in snapshot.hot100ArtistIds) hot100Leaders[snapshot.date]?.let(tracksById::get) else null
            val album = if (id in snapshot.albums75ArtistIds) albums75Leaders[snapshot.date]?.let(albumsById::get) else null
            val trackItem = track?.let {
                val credits = creditedByTrack[it.trackId].orEmpty().asSequence()
                    .filter { artistId -> artistId in activeArtists }
                    .mapNotNull(artistNames::get)
                    .distinct()
                    .joinToString(", ")
                ArtistNoOneContributionItem(it.trackId, it.title, it.coverUrl, credits.ifBlank { name })
            }
            val albumItem = album?.let {
                val ownerName = albumOwners[it.albumId]?.let(artistNames::get) ?: name
                ArtistNoOneContributionItem(it.albumId, it.title, it.coverUrl, ownerName)
            }
            if (trackItem == null && albumItem == null) null else ArtistNoOneContribution(
                dateIso = snapshot.date,
                dateLabel = on(period, snapshot.date),
                hot100Track = trackItem,
                albums75Album = albumItem
            )
        }
        val selectedLabel = labels.getValue(selectedChart).first
        val facts = pillars.map { pillar ->
            "${pillar.emoji} ${pillar.label}" to "${pillar.count} ${unit(period, pillar.count)}" +
                (pillar.latestDate?.let { " · dernier #1 : $it" } ?: "")
        } + listOf(
            "Rang · ${selectedLabel}" to "#$rank sur ${cumulative[selectedChart].orEmpty().size} artistes avec au moins un #1" +
                (if (tiedAtTop > 1) " · $tiedAtTop ex æquo" else "")
        )
        val separateCounts = pillars.joinToString(" · ") { "${it.label} : ${it.count} ${unit(period, it.count)}" }
        val narrative = "$name a mené le classement « $selectedLabel » pendant $score ${unit(period, score)}. " +
            "Les deux charts restent séparés — $separateCounts : aucun total ne mélange leurs unités. " +
            "La fiche liste les titres du Hot 100 et albums des Albums 75 qui ont contribué au record, avec leur période #1."
        val visual = ArtistCumulativeNoOneVisual(
            period = period,
            selectedChart = selectedChart,
            selectedChartLabel = selectedLabel,
            score = score,
            rank = rank,
            boardSize = cumulative[selectedChart].orEmpty().size,
            tiedAtTop = tiedAtTop,
            pillars = pillars,
            rival = rival,
            heatmap = heatmap,
            contributions = contributions
        )
        return Story(
            headline = "💎 $score ${unit(period, score)} au #1 · $selectedLabel",
            narrative = narrative,
            facts = facts,
            timelineTitle = "Présence au #1 · ${period.label}",
            artistCumulativeNoOneVisual = visual
        )
    }

    /* ---------------- records sur la trajectoire de l'élément ---------------- */

    private suspend fun chartStory(def: RecordDef, p: Period, cat: RecordCategory, sub: String?, id: Long, name: String, value: Double? = null): Story {
        val s = series(cat, p, id)
        if (s.isEmpty()) return Story(def.title, "$name n'a plus d'historique dans le chart ${p.frLabel.lowercase()} (les records seront recalculés à la prochaine écoute).")
        val presence = if (def.id in setOf("MOST_CUMULATIVE", "MOST_CUMULATIVE_TOP10", "MOST_CUMULATIVE_TOP5", "MOST_TIME_AT_1"))
            presenceVisual(def, p, cat, id, value ?: 0.0, s) else null
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
                    "Toutes ses apparitions (le peak en surbrillance)", points(p, s, { it.position == peak.position }),
                    presenceVisual = presence
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
                    "Trajectoire (Top 10 en surbrillance)", points(p, s, { it.position <= 10 }),
                    presenceVisual = presence
                )
            }
            "MOST_CUMULATIVE_TOP5" -> {
                val top = s.filter { it.position <= 5 }
                val run = longestRun(s) { it.position <= 5 }
                Story(
                    "🏅 ${top.size} ${unit(p, top.size)} dans le Top 5",
                    "$name a atteint les places 1 à 5 du $chart pendant ${top.size} ${unit(p, top.size)}, consécutives ou non. " +
                        "C'est un cumul de toutes les périodes Top 5 (${s.size} apparitions au total), distinct de Most Consistent Top 5 qui exige une série continue. " +
                        "Première fois ${on(p, top.first().date)} (${pos(top.first().position)}), dernière fois ${on(p, top.last().date)}.",
                    base + ("Apparitions hors Top 5" to "${s.size - top.size}") +
                        ("Plus longue série continue (info)" to "${run.size} ${unit(p, run.size)}"),
                    "Trajectoire (Top 5 en surbrillance)", points(p, s, { it.position <= 5 }),
                    presenceVisual = presence
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
                    "Trajectoire (#1 en surbrillance)", points(p, s, { it.position == 1 }),
                    presenceVisual = presence
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
                val max = RecordCatalog.zoneLimit(sub)
                val zone = RecordCatalog.zoneLabel(sub).removePrefix("le ")
                val topZ = s.filter { it.position <= max }
                val all = allSeries(cat, p)
                val blockers = HashMap<Long, Int>()
                for (a in topZ) all.forEach { (oid, os) -> if (oid != id && os.any { it.date == a.date && it.position == 1 }) blockers[oid] = (blockers[oid] ?: 0) + 1 }
                val items = blockers.entries.sortedByDescending { it.value }.take(5).map { (oid, n) ->
                    val (nm, img) = nameOf(cat.dbName, oid); Item(cat.dbName, oid, nm, "#1 pendant $n de ses ${topZ.size} passages en $zone", img)
                }
                Story(
                    "🚧 ${topZ.size} ${unit(p, topZ.size)} dans $zone, jamais #1",
                    "$name a passé ${topZ.size} ${unit(p, topZ.size)} dans $zone du $chart sans jamais décrocher la première place : sa meilleure position reste ${pos(peak.position)} (${on(p, peak.date)}). " +
                        (if (items.isNotEmpty()) "Celui qui lui a barré la route le plus souvent : ${items.first().name}." else ""),
                    base + ("Passages en $zone" to "${topZ.size}") + ("Passages à la #2" to "${s.count { it.position == 2 }}"),
                    "Trajectoire ($zone en surbrillance)", points(p, s, { it.position <= max }),
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
                // Une entité peut occuper plusieurs places du classement : raconter la période de la ligne cliquée si identifiable.
                val best = s.firstOrNull { it.plays.toDouble() == value } ?: s.maxBy { it.plays }
                // (value = valeur de la ligne cliquée ; défaut = meilleur pic absolu)
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

    private suspend fun songReachVisual(
        def: RecordDef,
        p: Period,
        cat: RecordCategory,
        sub: String?,
        id: Long,
        value: Double,
        itemCat: RecordCategory,
        mine: Map<Long, List<RecordAppearance>>,
        all: Map<Long, List<RecordAppearance>>,
        hits: Map<Long, List<RecordAppearance>>
    ): SongReachVisual {
        fun counts(series: Map<Long, List<RecordAppearance>>) = listOf(
            series.size,
            series.count { (_, appearances) -> appearances.any { it.position <= 10 } },
            series.count { (_, appearances) -> appearances.any { it.position <= 5 } },
            series.count { (_, appearances) -> appearances.any { it.position == 1 } }
        )
        val currentCounts = counts(mine)
        val rank = db.recordDao().betterCount(def.id, p.dbName, cat.dbName, sub, value, asc = false) + 1
        val tiedAtTop = db.recordDao().tiedAt(def.id, p.dbName, cat.dbName, sub, value)
        val rows = db.recordDao().top10(def.id, p.dbName, cat.dbName, sub, asc = false).first()
        val rivalRow = when (rank) {
            1 -> rows.firstOrNull { it.r.entityId != id }
            2 -> rows.firstOrNull { it.r.entityId != id }
            else -> null
        }
        val rival = rivalRow?.let { row ->
            val (rivalItemCat, rivalIds) = ownedIds(cat, sub, row.r.entityId)
            val rivalAll = if (rivalItemCat == itemCat) all else allSeries(rivalItemCat, p)
            val rivalCounts = counts(rivalAll.filterKeys { it in rivalIds })
            SongReachRival(
                id = row.r.entityId,
                name = row.name ?: "Inconnu",
                imageUrl = row.imageUrl,
                rank = rows.indexOfFirst { it.r.entityId == row.r.entityId } + 1,
                score = row.r.value.toInt(),
                chartCount = rivalCounts[0],
                top10Count = rivalCounts[1],
                top5Count = rivalCounts[2],
                numberOneCount = rivalCounts[3],
                tied = row.r.value == value
            )
        }
        val featured = hits.entries.sortedWith(
            compareBy<Map.Entry<Long, List<RecordAppearance>>> { entry -> entry.value.minOfOrNull { it.position } ?: Int.MAX_VALUE }
                .thenByDescending { entry -> entry.value.count { it.position == 1 } }
                .thenByDescending { entry -> entry.value.size }
        ).take(3).map { (itemId, appearances) ->
            val peak = appearances.minBy { it.position }
            val (itemName, imageUrl) = nameOf(itemCat.dbName, itemId)
            fun milestone(limit: Int) = appearances.firstOrNull { it.position <= limit }?.let { short(p, it.date) }
            SongReachTrack(
                id = itemId,
                name = itemName,
                imageUrl = imageUrl,
                peakPosition = peak.position,
                peakDate = short(p, peak.date),
                firstChartDate = short(p, appearances.first().date),
                firstTop10Date = milestone(10),
                firstTop5Date = milestone(5),
                firstNumberOneDate = appearances.firstOrNull { it.position == 1 }?.let { short(p, it.date) },
                chartPeriods = appearances.size,
                top10Periods = appearances.count { it.position <= 10 },
                top5Periods = appearances.count { it.position <= 5 },
                numberOnePeriods = appearances.count { it.position == 1 }
            )
        }
        val subLabel = def.subs[cat]?.firstOrNull { it.dbName == sub }?.label
        val itemLabel = if (itemCat == RecordCategory.ALBUM) "album" else "titre"
        return SongReachVisual(
            contextLabel = listOfNotNull(p.frLabel, cat.label, subLabel).joinToString(" · "),
            recordTitle = def.title,
            itemLabel = itemLabel,
            score = value.toInt(),
            chartCount = currentCounts[0],
            top10Count = currentCounts[1],
            top5Count = currentCounts[2],
            numberOneCount = currentCounts[3],
            top10ConversionPercent = RecordMath.conversionPercent(currentCounts[1], currentCounts[0]),
            top5ConversionPercent = RecordMath.conversionPercent(currentCounts[2], currentCounts[1]),
            numberOneConversionPercent = RecordMath.conversionPercent(currentCounts[3], currentCounts[2]),
            rank = rank,
            tiedAtTop = tiedAtTop,
            rival = rival,
            featuredTracks = featured
        )
    }

    private suspend fun ownerStory(def: RecordDef, p: Period, cat: RecordCategory, sub: String?, id: Long, name: String): Story {
        val (itemCat, ids) = ownedIds(cat, sub, id)
        val all = allSeries(itemCat, p)
        val mine = all.filterKeys { it in ids }
        val chart = "chart ${p.frLabel.lowercase()}"
        val what = if (itemCat == RecordCategory.ALBUM) "album" else "titre"
        suspend fun item(iid: Long, detail: String, hl: Boolean = false, debut: RecordAppearance? = null): Item {
            val (nm, img) = nameOf(itemCat.dbName, iid)
            return Item(
                itemCat.dbName, iid, nm, detail, img, hl,
                entryPosition = debut?.position,
                entryDateLabel = debut?.date?.let { on(p, it) },
                entryPlays = debut?.plays
            )
        }
        fun peakOf(s: List<RecordAppearance>) = s.minBy { it.position }

        return when (def.id) {
            "MOST_SONGS_IN_CHART", "MOST_SONGS_TOP10", "MOST_SONGS_TOP5", "MOST_SONGS_AT_1" -> {
                val limit = when (def.id) {
                    "MOST_SONGS_TOP10" -> 10
                    "MOST_SONGS_TOP5" -> 5
                    "MOST_SONGS_AT_1" -> 1
                    else -> Int.MAX_VALUE
                }
                val zone = when (limit) {
                    10 -> "dans le Top 10"
                    5 -> "dans le Top 5"
                    1 -> "au #1"
                    else -> "dans le chart"
                }
                val hits = mine.filter { (_, s) -> s.any { it.position <= limit } }
                val items = hits.entries.sortedWith(
                    compareBy<Map.Entry<Long, List<RecordAppearance>>> { peakOf(it.value).position }
                        .thenByDescending { entry -> entry.value.count { it.position == 1 } }
                        .thenByDescending { entry -> entry.value.size }
                ).map { (iid, s) ->
                    val peak = peakOf(s)
                    val detail = "peak ${pos(peak.position)} ${on(p, peak.date)} · ${s.size} ${unit(p, s.size)} en chart · " +
                        "${s.count { it.position <= 10 }} Top 10 · ${s.count { it.position <= 5 }} Top 5 · ${s.count { it.position == 1 }} #1"
                    item(iid, detail, peak.position <= limit)
                }
                val visual = songReachVisual(def, p, cat, sub, id, hits.size.toDouble(), itemCat, mine, all, hits)
                val zoneConversion = when (limit) {
                    10 -> "${visual.top10ConversionPercent?.let { "$it%" } ?: "—"} du chart"
                    5 -> "${visual.top5ConversionPercent?.let { "$it%" } ?: "—"} des titres du Top 10"
                    1 -> "${visual.numberOneConversionPercent?.let { "$it%" } ?: "—"} des titres du Top 5"
                    else -> null
                }
                Story(
                    headline = "${def.emoji} ${hits.size} ${plural(hits.size, what)} distincts $zone",
                    narrative = "$name a placé ${hits.size} ${plural(hits.size, what)} distincts $zone du $chart" +
                        (if (ids.size > hits.size) " (sur ${ids.size} ${plural(ids.size, what)} en bibliothèque)" else "") +
                        ". Chaque élément ne compte qu'une fois, même s'il apparaît sur plusieurs périodes." +
                        (zoneConversion?.let { " Conversion vers ce palier : $it." } ?: "") +
                        (items.firstOrNull()?.let { " Meilleur peak : ${it.name} (${it.detail.substringBefore(" ·")})." } ?: ""),
                    facts = listOf(
                        "Score · éléments distincts" to "${hits.size}",
                        "Classés dans ce chart" to "${visual.chartCount}",
                        "Top 10 · Top 5 · #1" to "${visual.top10Count} · ${visual.top5Count} · ${visual.numberOneCount}"
                    ),
                    itemsTitle = "Les ${plural(hits.size, what)} qui comptent",
                    items = items,
                    songReachVisual = visual
                )
            }
            "MOST_DEBUT_1", "MOST_DEBUT_TOP5", "MOST_DEBUT_TOP10" -> {
                val limit = when (def.id) { "MOST_DEBUT_1" -> 1; "MOST_DEBUT_TOP5" -> 5; else -> 10 }
                val zone = when (limit) { 1 -> "au #1"; 5 -> "dans le Top 5"; else -> "dans le Top 10" }
                val hits = mine.filter { (_, appearances) -> appearances.firstOrNull()?.position?.let { it <= limit } == true }
                val items = hits.entries.sortedBy { it.value.first().date }.map { (iid, appearances) ->
                    val debut = appearances.first()
                    item(iid, "entré ${pos(debut.position)} ${on(p, debut.date)} · ${debut.plays} ▶", true, debut)
                }
                val featuredNames = items.take(3).joinToString(", ") { it.name }
                val remaining = items.size - minOf(items.size, 3)
                val examples = when {
                    featuredNames.isBlank() -> ""
                    remaining > 0 -> " Parmi les premières entrées : $featuredNames et $remaining autre${if (remaining > 1) "s" else ""}."
                    else -> " Première${if (items.size > 1) "s entrées" else " entrée"} : $featuredNames."
                }
                Story(
                    "${def.emoji} ${hits.size} ${plural(hits.size, what)} entrés directement $zone",
                    "$name compte ${hits.size} ${plural(hits.size, what)} entrés directement $zone dans le $chart, dès leur toute première apparition. " +
                        "Chaque élément ne compte qu'une fois; une remontée ultérieure ne transforme pas un début tardif en début direct.$examples",
                    listOf(
                        "Débuts directs · $zone" to "${hits.size}",
                        "Éléments suivis dans ce chart" to "${mine.size}",
                        "Entrées hors zone" to "${mine.size - hits.size}"
                    ),
                    itemsTitle = "Le registre des premières entrées", items = items
                )
            }
            "MOST_SIMULTANEOUS" -> {
                val zoneName = sub?.substringAfterLast('_') ?: "ALL"
                val limit = when (zoneName) { "TOP2" -> 2; "TOP5" -> 5; "TOP10" -> 10; "TOP20" -> 20; "TOP50" -> 50; else -> Int.MAX_VALUE }
                val zone = when (zoneName) { "TOP2" -> "le Top 2"; "TOP5" -> "le Top 5"; "TOP10" -> "le Top 10"; "TOP20" -> "le Top 20"; "TOP50" -> "le Top 50"; else -> "le chart" }
                val byDate = HashMap<String, MutableList<Pair<Long, RecordAppearance>>>()
                mine.forEach { (iid, s) -> s.forEach { a -> if (a.position <= limit) byDate.getOrPut(a.date) { ArrayList() }.add(iid to a) } }
                val best = byDate.entries.maxWithOrNull(compareBy<Map.Entry<String, List<Pair<Long, RecordAppearance>>>> { it.value.size }.thenBy { it.key })
                    ?: return Story(def.title, "Aucune période trouvée où $name avait plusieurs ${plural(2, what)} dans $zone.")
                val n = best.value.size
                val items = best.value.sortedBy { it.second.position }.map { (iid, a) -> item(iid, "${pos(a.position)} · ${a.plays} ▶", true) }
                // ✊ Palier de prise d'otage + saturation de la zone
                val tier = com.novastats.app.domain.RecordCatalog.simultTier(n, limit)
                val saturation = if (limit != Int.MAX_VALUE) n * 100 / limit else null
                // 🆚 Dauphin du jour : le meilleur autre propriétaire dans la zone, cette période-là
                var rivalFact: Pair<String, String>? = null
                var rivalLine = ""
                val rivalEntries = all.filter { (iid, _) -> iid !in ids }
                    .mapNotNull { (iid, s) -> s.firstOrNull { it.date == best.key && it.position <= limit }?.let { iid to it.position } }
                if (rivalEntries.isNotEmpty()) {
                    val ownerOf: Map<Long, Long> = if (itemCat == RecordCategory.ALBUM) {
                        db.albumDao().byIds(rivalEntries.map { it.first }).associate { it.albumId to (it.artistId ?: -1L) }
                    } else {
                        db.trackDao().byIds(rivalEntries.map { it.first }).associate { it.trackId to (if (cat == RecordCategory.ALBUM) (it.albumId ?: -1L) else it.artistId) }
                    }
                    val grouped = rivalEntries.mapNotNull { (iid, p2) -> ownerOf[iid]?.takeIf { it >= 0 }?.let { it to p2 } }.groupBy({ it.first }, { it.second })
                    val top = grouped.maxByOrNull { it.value.size }
                    if (top != null) {
                        val rivalN = top.value.size
                        val rType = if (itemCat == RecordCategory.ALBUM || cat != RecordCategory.ALBUM) "ARTIST" else "ALBUM"
                        val (rName, _) = nameOf(rType, top.key)
                        rivalFact = "Dauphin du jour" to "$rName — $rivalN ${plural(rivalN, what)}"
                        rivalLine = when {
                            n > rivalN -> " Ce jour-là, $name pesait plus lourd dans $zone que n'importe qui d'autre : le dauphin ($rName) n'avait que $rivalN ${plural(rivalN, what)}."
                            n == rivalN -> " Ce jour-là, $rName faisait jeu égal avec $rivalN ${plural(rivalN, what)}."
                            else -> ""
                        }
                    }
                }
                // 📈 Séries de domination : plus longue suite de périodes avec au moins [thr] sièges
                val thr = if (limit >= 3) 3 else 2
                val periodOf = HashMap<String, Int>()
                mine.forEach { (_, s) -> s.forEach { periodOf[it.date] = it.periodIndex } }
                var bestRun = 0; var curRun = 0; var prevIdx = Int.MIN_VALUE
                byDate.entries.filter { it.value.size >= thr }.sortedBy { periodOf[it.key] ?: Int.MIN_VALUE }.forEach { (dt, _) ->
                    val idx = periodOf[dt] ?: return@forEach
                    curRun = if (idx == prevIdx + 1) curRun + 1 else 1
                    prevIdx = idx
                    if (curRun > bestRun) bestRun = curRun
                }
                val facts = buildList {
                    add("Période record" to on(p, best.key))
                    tier?.let { add("Palier" to "${it.emoji} ${it.label}") }
                    saturation?.let { add("Saturation de la zone" to "$it % à lui seul") }
                    add("Périodes avec ≥ 2 ${plural(2, what)} dans $zone" to "${byDate.values.count { it.size >= 2 }}")
                    if (bestRun >= 2) add("Plus longue série avec ≥ $thr" to "$bestRun ${plural(bestRun, "période")} de suite")
                    rivalFact?.let { add(it) }
                }
                val skyline = byDate.entries.sortedBy { periodOf[it.key] ?: 0 }.map { (dt, l) -> Point(dt, short(p, dt), null, l.size, dt == best.key) }
                Story(
                    "🎯 $n ${plural(n, what)} en même temps dans $zone" + (tier?.let { " · ${it.emoji} ${it.label}" } ?: ""),
                    "${on(p, best.key).replaceFirstChar { it.uppercase() }}, $name occupait $zone du $chart avec $n ${plural(n, what)} à la fois : " +
                        items.joinToString(", ") { "${it.name} (${it.detail.substringBefore(" ·")})" } + "." +
                        (saturation?.let { " Soit $it % de $zone à lui seul." } ?: "") + rivalLine,
                    facts,
                    "La skyline de domination (barres = sièges occupés)", skyline,
                    "Le chart ce jour-là", items,
                    seats = if (limit <= 50) Seats(limit, best.value.map { it.second.position }.sorted()) else null,
                    timelineValueSuffix = "sièges"
                )
            }
            "MOST_SUCCESSIVE_1" -> successiveStory(def, p, cat, sub, id, name, itemCat, all)
            else -> Story(def.title, def.description)
        }
    }

    /** Liens d'appartenance identiques à ceux du moteur (artistes principaux + crédits, ou album parent). */
    private suspend fun successiveOwners(
        cat: RecordCategory,
        itemCat: RecordCategory,
        chartItemIds: Set<Long>
    ): Map<Long, Set<Long>> {
        if (chartItemIds.isEmpty()) return emptyMap()
        val owners = HashMap<Long, MutableSet<Long>>()
        fun add(itemId: Long, ownerId: Long) { owners.getOrPut(itemId) { LinkedHashSet() }.add(ownerId) }
        when {
            cat == RecordCategory.ARTIST && itemCat == RecordCategory.TRACK -> {
                db.trackLinkDao().allTrackArtists().asSequence()
                    .filter { it.trackId in chartItemIds }
                    .forEach { add(it.trackId, it.artistId) }
                db.trackDao().allPlayed().asSequence()
                    .filter { it.trackId in chartItemIds }
                    .forEach { add(it.trackId, it.artistId) }
            }
            cat == RecordCategory.ARTIST && itemCat == RecordCategory.ALBUM -> {
                db.albumDao().all().asSequence()
                    .filter { it.albumId in chartItemIds }
                    .forEach { album -> album.artistId?.let { add(album.albumId, it) } }
            }
            cat == RecordCategory.ALBUM && itemCat == RecordCategory.TRACK -> {
                db.trackDao().allPlayed().asSequence()
                    .filter { it.trackId in chartItemIds }
                    .forEach { track -> track.albumId?.let { add(track.trackId, it) } }
            }
        }
        return owners.mapValues { (_, ids) -> ids.toSet() }
    }

    private suspend fun successiveStory(
        def: RecordDef,
        p: Period,
        cat: RecordCategory,
        sub: String?,
        id: Long,
        name: String,
        itemCat: RecordCategory,
        all: Map<Long, List<RecordAppearance>>
    ): Story {
        val ownerMap = successiveOwners(cat, itemCat, all.keys)
        val chart = "chart ${p.frLabel.lowercase()}"
        val numberOnes = all.flatMap { (itemId, appearances) ->
            val owners = ownerMap[itemId].orEmpty()
            appearances.filter { it.position == 1 }.map { appearance ->
                RecordMath.NumberOne(appearance.periodIndex, appearance.date, itemId, owners)
            }
        }.sortedWith(compareBy<RecordMath.NumberOne> { it.periodIndex }.thenBy { it.date }.thenBy { it.entityId })
        val run = RecordMath.bestSuccessiveOneRun(numberOnes, id)
            ?: return Story(
                "🔁 Most Successive #1",
                "Aucune succession admissible de deux ${if (itemCat == RecordCategory.ALBUM) "albums" else "titres"} distincts au #1 n'a été trouvée pour $name dans cette combinaison. Les éléments à un seul #1 sont exclus.",
                listOf("Seuil d'admissibilité" to "2 éléments distincts consécutifs minimum")
            )

        val segments = RecordMath.successiveOneSegments(run).map { segment ->
            val (itemName, image) = nameOf(itemCat.dbName, segment.entityId)
            SuccessiveSegment(
                id = segment.entityId,
                type = itemCat.dbName,
                name = itemName,
                imageUrl = image,
                firstDate = short(p, segment.first.date),
                lastDate = short(p, segment.last.date),
                periodCount = segment.periodCount,
                durationLabel = "${segment.periodCount} ${unit(p, segment.periodCount)}"
            )
        }

        val currentScore = run.distinctEntityIds.size
        val scores = RecordMath.successiveNumberOnes(numberOnes)
        val leaderScore = scores.values.maxOfOrNull { it.value }?.toInt() ?: currentScore
        val recordHolder = currentScore >= leaderScore
        val tiedAtTop = if (recordHolder) scores.values.count { it.value.toInt() == leaderScore } else 0
        val cachedRows = db.recordDao().top10(def.id, p.dbName, cat.dbName, sub, asc = false).first()
        val cachedIndex = cachedRows.indexOfFirst { it.r.entityId == id }
        val rank = if (cachedIndex >= 0) cachedIndex + 1 else scores.values.count { it.value > currentScore.toDouble() } + 1

        val coHolder = scores.entries.asSequence()
            .filter { it.key != id && it.value.value.toInt() == currentScore }
            .minByOrNull { it.value.date ?: "" }
        val rivalEntry = coHolder ?: if (recordHolder) {
            scores.entries.filter { it.key != id && it.value.value < currentScore.toDouble() }
                .maxByOrNull { it.value.value }
        } else {
            scores.entries.filter { it.key != id && it.value.value > currentScore.toDouble() }
                .minByOrNull { it.value.value }
        }
        val rival = rivalEntry?.let { (rivalId, result) ->
            val (rivalName, rivalImage) = nameOf(cat.dbName, rivalId)
            val rivalScore = result.value.toInt()
            SuccessiveRival(
                id = rivalId,
                name = rivalName,
                imageUrl = rivalImage,
                score = rivalScore,
                gap = kotlin.math.abs(rivalScore - currentScore),
                tied = rivalScore == currentScore,
                ahead = rivalScore > currentScore
            )
        }

        val latestObservation = all.values.asSequence().flatten()
            .maxWithOrNull(compareBy<RecordAppearance> { it.periodIndex }.thenBy { it.date })
        val continuing = latestObservation?.periodIndex == run.last.periodIndex
        val nextOne = numberOnes.firstOrNull { it.periodIndex > run.last.periodIndex }
        val nextObserved = all.values.asSequence().flatten()
            .filter { it.periodIndex > run.last.periodIndex }
            .minWithOrNull(compareBy<RecordAppearance> { it.periodIndex }.thenBy { it.date })
        val stopper = if (continuing) null else {
            if (nextOne != null) {
                val (nextName, nextImage) = nameOf(itemCat.dbName, nextOne.entityId)
                val ownerNames = ArrayList<String>()
                for (ownerId in nextOne.ownerIds.filter { it != id }) {
                    val ownerLabel = nameOf(cat.dbName, ownerId).first
                    if (ownerLabel !in ownerNames) ownerNames += ownerLabel
                }
                val ownerName = when {
                    ownerNames.isNotEmpty() -> ownerNames.joinToString(", ")
                    id in nextOne.ownerIds -> "$name (même détenteur)"
                    else -> null
                }
                val nextLabel = short(p, nextOne.date)
                val exactNextPeriod = nextOne.periodIndex == run.last.periodIndex + 1
                val explanation = when {
                    exactNextPeriod -> "Rupture nette au relevé suivant, $nextLabel : « $nextName » prend le #1" +
                        (ownerName?.let { " pour $it" } ?: "") + "."
                    else -> {
                        val missing = (nextOne.periodIndex - run.last.periodIndex - 1).coerceAtLeast(1)
                        "La période suivante n'a pas de #1 documenté; ${missing} ${plural(missing, "période")} ${if (missing == 1) "manque" else "manquent"} avant le prochain #1 relevé, « $nextName » ($nextLabel)" +
                            (ownerName?.let { ", pour $it" } ?: "") + "."
                    }
                }
                SuccessiveStopper(nextName, itemCat.dbName, nextOne.entityId, nextImage, ownerName, nextLabel, exactNextPeriod, explanation)
            } else if (nextObserved != null) {
                val missing = (nextObserved.periodIndex - run.last.periodIndex - 1).coerceAtLeast(1)
                val explanation = if (nextObserved.periodIndex == run.last.periodIndex + 1) {
                    "Le classement existe à ${short(p, nextObserved.date)}, mais aucun #1 identifiable n'est enregistré pour cette période."
                } else {
                    "Il manque ${missing} ${plural(missing, "période")} d'archive après la série; aucun #1 ne permet d'en attribuer la rupture."
                }
                SuccessiveStopper(null, null, null, null, null, short(p, nextObserved.date), false, explanation)
            } else {
                SuccessiveStopper(null, null, null, null, null, null, false, "Aucun relevé plus récent n'est disponible pour établir la cause de fin de série.")
            }
        }
        val statusLine = if (continuing) {
            "Série toujours en cours : le détenteur conserve le #1 dans le dernier relevé (${latestObservation?.let { short(p, it.date) } ?: short(p, run.last.date)})."
        } else {
            "Série terminée après ${on(p, run.last.date)}. ${stopper?.explanation.orEmpty()}"
        }
        val subLabel = def.subs[cat]?.firstOrNull { it.dbName == sub }?.label
        val contextLabel = listOfNotNull(p.label, cat.label, subLabel).joinToString(" · ")
        val duration = "${run.periodCount} ${unit(p, run.periodCount)}"
        val visual = SuccessiveVisual(
            distinctCount = currentScore,
            periodCount = run.periodCount,
            durationLabel = duration,
            itemLabel = if (itemCat == RecordCategory.ALBUM) "albums" else "titres",
            contextLabel = contextLabel,
            startDate = on(p, run.first.date),
            endDate = on(p, run.last.date),
            startShort = short(p, run.first.date),
            endShort = short(p, run.last.date),
            continuing = continuing,
            statusLine = statusLine,
            rank = rank,
            boardSize = scores.size,
            recordHolder = recordHolder,
            tiedAtTop = tiedAtTop,
            rival = rival,
            segments = segments,
            stopper = stopper
        )
        val narrative = if (continuing) {
            "Du ${short(p, run.first.date)} au ${short(p, run.last.date)}, $name a gardé le #1 du $chart avec $currentScore ${plural(currentScore, if (itemCat == RecordCategory.ALBUM) "album" else "titre")} distincts qui se sont relayés. Le score mesure ces éléments distincts; la durée ($duration) est affichée séparément."
        } else {
            "Du ${short(p, run.first.date)} au ${short(p, run.last.date)}, $name a gardé le #1 du chart avec $currentScore ${plural(currentScore, if (itemCat == RecordCategory.ALBUM) "album" else "titre")} distincts. ${stopper?.explanation ?: "La série est terminée."} Le score reste fondé sur les éléments distincts; la durée ($duration) complète le récit sans modifier le classement."
        }
        val itemWord = if (itemCat == RecordCategory.ALBUM) "album" else "titre"
        val facts = buildList {
            add("Score · éléments distincts" to "$currentScore")
            add("Durée · périodes consécutives" to duration)
            add("Début de série" to visual.startDate)
            add("Fin de série" to visual.endDate)
            add("Statut" to if (continuing) "Toujours en cours" else "Terminée")
            add("Classement" to "${ordinal(rank)} sur ${scores.size}")
            rival?.let {
                val label = if (it.tied) "Comparaison" else if (it.ahead) "Écart au rang supérieur" else "Avance sur le suivant"
                val detail = if (it.tied) "égalité · $currentScore" else "${it.gap} ${plural(it.gap, itemWord)} avec ${it.name}"
                add(label to detail)
            }
        }
        return Story(
            headline = "🔁 $currentScore ${plural(currentScore, itemWord)} distincts · $duration",
            narrative = narrative,
            facts = facts,
            successiveVisual = visual
        )
    }

    /* ---------------- certifications, Panthéon, Hall of Fame, multi-chart ---------------- */

    private suspend fun fastestCert(cat: RecordCategory, sub: String?, id: Long, name: String): Story {
        val type = if (cat == RecordCategory.ALBUM) EntityType.ALBUM else EntityType.TRACK
        val history = db.certificationDao().history(id, type)
        val level = CertLevel.entries.firstOrNull { it.dbName == sub }
        val row = history.filter { it.level == sub && (it.timeToCertifyMs ?: 0L) > 0L }
            .minByOrNull { it.timeToCertifyMs!! }
            ?: return Story("⚡ Fastest Certification", "Aucune certification ${level?.label ?: sub} trouvée pour $name.")
        val ms = row.timeToCertifyMs!!
        val firstPlay = row.certifiedAt - ms
        val thresholdHistory = history
            .filter { it.playCountAtCert <= row.playCountAtCert && it.certifiedAt <= row.certifiedAt }
            .distinctBy { it.playCountAtCert }
            .sortedBy { it.playCountAtCert }
        val stages = thresholdHistory.map { h ->
            val l = CertLevel.entries.firstOrNull { it.dbName == h.level }
            SpeedStageSource(
                key = "${h.level}:${h.multiplier}",
                label = "${l?.emoji ?: "🏅"} ${l?.label ?: h.level} ×${h.multiplier}",
                at = h.certifiedAt,
                plays = h.playCountAtCert
            )
        }
        val daily = if (type == EntityType.ALBUM) db.dailyPlayDao().seriesForAlbum(id) else db.dailyPlayDao().seriesForTrack(id)
        val visual = speedVisual("FASTEST_CERT", cat, sub, id, firstPlay, row.certifiedAt, ms, daily, stages)
        val onDate = on(Period.DAILY, Dates.toIso(row.certifiedAt))
        val kind = if (type == EntityType.ALBUM) "album" else "titre"
        val pace = String.format(Locale.FRANCE, "%.1f", row.playCountAtCert / (ms / 86_400_000.0).coerceAtLeast(1.0))
        val outcome = speedOutcome(name, visual)
        val facts = listOf(
            "Première écoute" to on(Period.DAILY, Dates.toIso(firstPlay)),
            "Certification" to onDate,
            "Écoutes au palier" to "${row.playCountAtCert} ▶" + if (row.multiplier > 1) " · ×${row.multiplier}" else "",
            "Temps écoulé" to "${RecordCatalog.formatFastDuration(ms)} · ${visual.elapsedDays} jours calendaires",
            "Jours actifs" to "${visual.activeDays} sur ${visual.elapsedDays}",
            "Rythme moyen" to "$pace écoute${if (pace == "1,0") "" else "s"} / jour calendaire",
            "Rang dans la bibliothèque" to "${visual.rank}${ordinalSuffix(visual.rank)} sur ${visual.boardSize}"
        )
        val selectedCert = CertLevel.entries.firstOrNull { it.dbName == row.level }
        return Story(
            "⚡ ${selectedCert?.emoji ?: ""} ${selectedCert?.label ?: sub} en ${RecordCatalog.formatFastDuration(ms)}",
            "$name, ce $kind, a atteint ${selectedCert?.label ?: sub} le $onDate en ${RecordCatalog.formatFastDuration(ms)} après sa première écoute du ${on(Period.DAILY, Dates.toIso(firstPlay))}, avec ${row.playCountAtCert} écoutes" +
                (if (row.multiplier > 1) " (×${row.multiplier})" else "") + ". $outcome",
            facts,
            speedVisual = visual
        )
    }

    private suspend fun fastestPantheon(sub: String?, id: Long, name: String): Story {
        val history = db.pantheonDao().speedHistory(id)
        val status = PantheonStatus.fromDb(sub)
        val row = history.firstOrNull { it.status == sub && (it.timeToReachMs ?: 0L) > 0L }
            ?: return Story(
                "👑 Fastest Panthéon",
                "Le parcours Fastest Panthéon précis de $name n'est pas encore disponible. Après une mise à jour, il se reconstruit sans toucher aux écoutes ni aux autres records; réouvre cette fiche après le recalcul."
            )
        val ms = row.timeToReachMs!!
        val firstPlay = row.dateReached - ms
        val route = PantheonRoute.fromDb(row.attainmentRoute)
        val stages = history.asSequence()
            .filter { (PantheonStatus.fromDb(it.status)?.ordinal ?: Int.MAX_VALUE) <= (status?.ordinal ?: -1) }
            .filter { (it.timeToReachMs ?: 0L) > 0L }
            .sortedBy { PantheonStatus.fromDb(it.status)?.ordinal ?: Int.MAX_VALUE }
            .map { h ->
                val st = PantheonStatus.fromDb(h.status)
                SpeedStageSource(
                    key = h.status,
                    label = "${st?.emoji ?: "•"} ${st?.label ?: h.status}",
                    at = h.dateReached,
                    plays = h.playCountAtStatus,
                    route = PantheonRoute.fromDb(h.attainmentRoute).label
                )
            }.toList()
        val daily = db.dailyPlayDao().seriesCreditedToArtist(id)
        val visual = speedVisual("FASTEST_PANTHEON", RecordCategory.ARTIST, sub, id, firstPlay, row.dateReached, ms, daily, stages)
        val dateReached = on(Period.DAILY, Dates.toIso(row.dateReached))
        val pace = String.format(Locale.FRANCE, "%.1f", row.playCountAtStatus / (ms / 86_400_000.0).coerceAtLeast(1.0))
        val outcome = speedOutcome(name, visual)
        val facts = listOf(
            "Première écoute" to on(Period.DAILY, Dates.toIso(firstPlay)),
            "Statut atteint" to dateReached,
            "Voie la plus rapide" to route.label,
            "Écoutes au statut" to "${row.playCountAtStatus} ▶",
            "Temps écoulé" to "${RecordCatalog.formatFastDuration(ms)} · ${visual.elapsedDays} jours calendaires",
            "Jours actifs" to "${visual.activeDays} sur ${visual.elapsedDays}",
            "Rythme moyen" to "$pace écoute${if (pace == "1,0") "" else "s"} / jour calendaire",
            "Rang dans le Panthéon" to "${visual.rank}${ordinalSuffix(visual.rank)} sur ${visual.boardSize}"
        )
        return Story(
            "👑 ${status?.emoji ?: ""} ${status?.label ?: sub} en ${RecordCatalog.formatFastDuration(ms)}",
            "$name est devenu ${status?.label ?: sub} le $dateReached en ${RecordCatalog.formatFastDuration(ms)} après sa première écoute du ${on(Period.DAILY, Dates.toIso(firstPlay))}, avec ${row.playCountAtStatus} écoutes. La voie la plus rapide était : ${route.label.lowercase(Locale.FRANCE)}. $outcome",
            facts,
            speedVisual = visual
        )
    }

    private suspend fun speedVisual(
        recordType: String,
        category: RecordCategory,
        sub: String?,
        entityId: Long,
        startAt: Long,
        finishAt: Long,
        durationMs: Long,
        daily: List<DayCount>,
        sourceStages: List<SpeedStageSource>
    ): SpeedVisual {
        val dao = db.recordDao()
        val candidateTimes: Map<Long, Long> = if (recordType == "FASTEST_CERT") {
            val entityType = category.dbName
            db.certificationDao().allHistory()
                .filter { it.entityType == entityType && it.level == sub && (it.timeToCertifyMs ?: 0L) > 0L }
                .groupBy { it.entityId }
                .mapValues { (_, rows) -> rows.minOf { it.timeToCertifyMs!! } }
        } else {
            db.pantheonDao().allSpeedHistory()
                .filter { it.status == sub && (it.timeToReachMs ?: 0L) > 0L }
                .associate { it.artistId to it.timeToReachMs!! }
        }
        val rank = candidateTimes.values.count { it < durationMs } + 1
        val boardSize = candidateTimes.size.coerceAtLeast(rank)
        val recordHolder = rank == 1
        val tiedAtTop = if (recordHolder) candidateTimes.values.count { it == durationMs } else 0
        val rows = dao.top10(recordType, null, category.dbName, sub, asc = true).first()
        val rivalRow = if (recordHolder) {
            rows.asSequence().filter { it.r.entityId != entityId && it.r.value == durationMs.toDouble() }
                .firstOrNull() ?: rows.filter { it.r.entityId != entityId && it.r.value > durationMs.toDouble() }
                .minByOrNull { it.r.value }
        } else {
            rows.filter { it.r.entityId != entityId && it.r.value < durationMs.toDouble() }
                .maxByOrNull { it.r.value }
                ?: rows.filter { it.r.entityId != entityId }.minByOrNull { it.r.value }
        }
        val rival = rivalRow?.let { other ->
            val rivalMs = other.r.value.toLong().coerceAtLeast(0L)
            val gap = kotlin.math.abs(rivalMs - durationMs)
            val denominator = minOf(rivalMs, durationMs).coerceAtLeast(1L)
            SpeedRival(
                id = other.r.entityId,
                type = other.r.category,
                name = other.name ?: "Inconnu",
                imageUrl = other.imageUrl,
                durationMs = rivalMs,
                gapMs = gap,
                percentGap = gap * 100.0 / denominator,
                tied = rivalMs == durationMs
            )
        }

        val startDate = instantDate(startAt)
        val finishDate = instantDate(finishAt)
        val dailyCounts = daily.mapNotNull { row ->
            runCatching { Dates.parse(row.date) }.getOrNull()?.let { it to row.playCount.coerceAtLeast(0) }
        }.toMap()
        val elapsedDaysLong = (ChronoUnit.DAYS.between(startDate, finishDate) + 1L).coerceAtLeast(1L)
        val elapsedDays = elapsedDaysLong.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val activeDays = dailyCounts.count { (date, plays) -> !date.isBefore(startDate) && !date.isAfter(finishDate) && plays > 0 }
        var previousAt = startAt
        val stages = sourceStages.withIndex()
            .filter { it.value.at >= startAt && it.value.at <= finishAt }
            .sortedWith(compareBy<IndexedValue<SpeedStageSource>> { it.value.at }.thenBy { it.index })
            .map { indexed ->
                val source = indexed.value
                val atDate = instantDate(source.at)
                val cumulative = (source.at - startAt).coerceAtLeast(0L)
                val activeToStage = dailyCounts.count { (date, plays) -> !date.isBefore(startDate) && !date.isAfter(atDate) && plays > 0 }
                val split = (source.at - previousAt).coerceAtLeast(0L)
                previousAt = maxOf(previousAt, source.at)
                SpeedStage(
                    key = source.key,
                    label = source.label,
                    date = on(Period.DAILY, Dates.toIso(source.at)),
                    elapsedMs = cumulative,
                    splitMs = split,
                    playCount = source.plays,
                    activeDays = activeToStage,
                    route = source.route
                )
            }
        val grain = when {
            elapsedDaysLong <= 180L -> SpeedCalendarGrain.DAYS
            elapsedDaysLong <= 1_095L -> SpeedCalendarGrain.WEEKS
            else -> SpeedCalendarGrain.MONTHS
        }
        val calendar = speedCalendar(startDate, finishDate, dailyCounts, grain)
        return SpeedVisual(
            startDate = on(Period.DAILY, startDate.format(Dates.ISO)),
            finishDate = on(Period.DAILY, finishDate.format(Dates.ISO)),
            elapsedMs = durationMs,
            elapsedDays = elapsedDays,
            activeDays = activeDays,
            rank = rank,
            boardSize = boardSize,
            recordHolder = recordHolder,
            tiedAtTop = tiedAtTop,
            rival = rival,
            stages = stages,
            calendarGrain = grain,
            calendar = calendar
        )
    }

    private fun speedOutcome(name: String, visual: SpeedVisual): String {
        val rival = visual.rival ?: return if (visual.recordHolder) "Aucun autre élément de ce classement ne le devance." else "Le classement ne contient pas encore de rival exploitable."
        val percent = String.format(Locale.FRANCE, "%.1f", rival.percentGap)
        return when {
            visual.recordHolder && rival.tied -> "$name partage la tête avec ${rival.name} à égalité parfaite."
            visual.recordHolder -> "Record en tête : ${rival.name}, dauphin, est à ${RecordCatalog.formatFastDuration(rival.gapMs)} du chrono de $name, soit ${percent} % plus lent."
            else -> "Le chrono juste devant $name appartient à ${rival.name}, avec ${RecordCatalog.formatFastDuration(rival.gapMs)} d'écart (${percent} % du temps plus rapide)."
        }
    }

    private fun instantDate(ms: Long): LocalDate =
        java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate()

    private fun speedCalendar(
        start: LocalDate,
        finish: LocalDate,
        dailyCounts: Map<LocalDate, Int>,
        grain: SpeedCalendarGrain
    ): List<SpeedActivityBucket> {
        if (finish.isBefore(start)) return emptyList()
        val out = ArrayList<SpeedActivityBucket>()
        when (grain) {
            SpeedCalendarGrain.DAYS -> {
                var day = start.minusDays((start.dayOfWeek.value - 1).toLong())
                while (!day.isAfter(finish)) {
                    val padding = day.isBefore(start)
                    out += SpeedActivityBucket(day.toString(), day.dayOfMonth.toString(), if (padding) 0 else dailyCounts[day] ?: 0, if (!padding && (dailyCounts[day] ?: 0) > 0) 1 else 0, padding)
                    day = day.plusDays(1)
                }
            }
            SpeedCalendarGrain.WEEKS -> {
                var week = start.minusDays((start.dayOfWeek.value - 1).toLong())
                val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE)
                while (!week.isAfter(finish)) {
                    val from = maxOf(start, week)
                    val to = minOf(finish, week.plusDays(6))
                    val active = dailyCounts.filter { (date, count) -> !date.isBefore(from) && !date.isAfter(to) && count > 0 }
                    out += SpeedActivityBucket(week.toString(), from.format(fmt), active.values.sum(), active.size)
                    week = week.plusWeeks(1)
                }
            }
            SpeedCalendarGrain.MONTHS -> {
                var month = YearMonth.from(start)
                val endMonth = YearMonth.from(finish)
                val fmt = DateTimeFormatter.ofPattern("MMM yy", Locale.FRANCE)
                while (!month.isAfter(endMonth)) {
                    val from = maxOf(start, month.atDay(1))
                    val to = minOf(finish, month.atEndOfMonth())
                    val active = dailyCounts.filter { (date, count) -> !date.isBefore(from) && !date.isAfter(to) && count > 0 }
                    out += SpeedActivityBucket(month.atDay(1).toString(), month.format(fmt), active.values.sum(), active.size)
                    month = month.plusMonths(1)
                }
            }
        }
        return out
    }

    private fun ordinalSuffix(rank: Int): String = if (rank == 1) "er" else "e"

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
        val periodOf = HashMap<String, Int>()
        for ((tid, s) in all) for (a in s) {
            // Étendu à z+1 : pour retrouver le premier exclu (la victime du balayage)
            if (a.position <= z + 1) byDate.getOrPut(a.date) { HashMap() }[a.position] = tid
            periodOf[a.date] = a.periodIndex
        }
        val sweeps = byDate.entries.filter { (_, posMap) -> (1..z).all { pos -> posMap[pos]?.let { it in owned && (!solo || isSolo(it)) } == true } }.sortedBy { it.key }
        if (sweeps.isEmpty()) return Story("🧹 Podium Sweep", "$name n'occupe plus l'intégralité du Top $z du $chart (les records seront recalculés à la prochaine écoute).")
        val last = sweeps.last()
        val items = (1..z).mapNotNull { place -> last.value[place]?.let { tid -> val (nm, img) = nameOf(EntityType.TRACK, tid); Item(EntityType.TRACK, tid, nm, "${pos(place)} ${on(p, last.key)}" + (if (isSolo(tid)) " · solo" else " · feat."), img, place == 1) } }
        val mode = if (solo) "uniquement avec des titres solo (sans featuring)" else "feat. compris"
        // 👑 Plus long règne : balayages consécutifs
        var bestRun = 0; var curRun = 0; var prevIdx = Int.MIN_VALUE; var runStart = ""; var runEnd = ""; var bs = ""
        sweeps.forEach { e ->
            val idx = periodOf[e.key] ?: return@forEach
            if (idx == prevIdx + 1) curRun++ else { curRun = 1; bs = e.key }
            prevIdx = idx; runEnd = e.key
            if (curRun > bestRun) { bestRun = curRun; runStart = bs }
        }
        // 🧹 Palier du balayeur
        val tier = RecordCatalog.sweepTier(sweeps.size)
        // 🆚 Victime du dernier balayage : premier titre exclu de la zone
        val victim = last.value[z + 1]?.let { tid -> nameOf(EntityType.TRACK, tid).first }
        // 🎼 Face-à-face Standard vs Solo (même zone, même période, mode opposé)
        val zoneKey = sub?.substringBefore("_")
        val otherSub = if (zoneKey != null) (if (solo) "${zoneKey}_STD" else "${zoneKey}_SOLO") else null
        val otherRow = if (otherSub != null) db.recordDao().rowsForEntity(cat.dbName, id)
            .firstOrNull { it.recordType == "PODIUM_SWEEP" && it.periodType == p.dbName && it.subcategory == otherSub } else null
        val facts = buildList {
            add("Zone" to "Top $z · ${if (solo) "Solo" else "Standard"}")
            tier?.let { add("Palier" to "${it.emoji} ${it.label}") }
            add("Premier balayage" to on(p, sweeps.first().key))
            add("Dernier balayage" to on(p, last.key))
            if (bestRun >= 2) add("Règne le plus long" to "$bestRun ${plural(bestRun, "période")} de suite (${short(p, runStart)} → ${short(p, runEnd)})")
            victim?.let { add("Victime du dernier balayage" to "« $it » poussé à ${pos(z + 1)}") }
            if (otherRow != null) add(if (solo) "En Standard (feat. compris)" to "${otherRow.value.toLong()} balayages" else "En Solo (sans featuring)" to "${otherRow.value.toLong()} balayages")
            add("Titres différents utilisés" to "${sweeps.flatMap { it.value.values }.toSet().size}")
        }
        val stdVsSoloLine = if (otherRow != null && solo && otherRow.value.toLong() > sweeps.size) {
            " En Standard, le compteur monte à ${otherRow.value.toLong()} balayages : ${otherRow.value.toLong() - sweeps.size} ne tenaient que grâce aux featurings."
        } else ""
        return Story(
            "🧹 Top $z complet ${sweeps.size} fois" + (tier?.let { " · ${it.emoji} ${it.label}" } ?: ""),
            "$name a occupé les $z premières places du $chart en même temps, $mode, pendant ${sweeps.size} ${unit(p, sweeps.size)} : " + sweeps.joinToString(", ") { short(p, it.key) } + ". " +
                (if (bestRun >= 2) "Plus long règne : $bestRun ${plural(bestRun, "balayage")} consécutifs, de ${short(p, runStart)} à ${short(p, runEnd)}. " else "") +
                (victim?.let { "Le dernier balayage a poussé « $it » à la place ${z + 1} — il ne lui a manqué qu'un siège. " } ?: "") +
                stdVsSoloLine +
                "Un balayage ne compte que si TOUTES les places de la zone lui appartiennent la même période — c'est voulu rare.",
            facts,
            "Les périodes de balayage", sweeps.map { Point(it.key, short(p, it.key), 1, null, true, "Top $z complet") },
            "Le dernier Top $z complet", items,
            seats = Seats(z, (1..z).toList())
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
        // 🔥 Palier symbolique atteint + prochain cap
        val tier = com.novastats.app.domain.RecordCatalog.tierFor(len)
        val next = com.novastats.app.domain.RecordCatalog.nextTier(len)
        // ⚠️ Journée la plus dangereuse : celle avec le moins d'écoutes — la série a failli mourir là
        val danger = if (streakDays.size > 1) streakDays.minByOrNull { it.playCount } else null
        // Série en cours (peut différer du record historique) et record personnel précédent
        var current = 0
        var d = if (epoch.containsKey(today)) today else if (epoch.containsKey(today - 1)) today - 1 else Int.MIN_VALUE
        while (d != Int.MIN_VALUE && epoch.containsKey(d)) { current++; d-- }
        val previousBest = com.novastats.app.domain.RecordMath.longestDayStreak(epoch.keys.filter { it < start || it > end })?.first ?: 0
        val narrative = "$name a été écouté chaque jour, sans exception, du ${startIso.format(dayFmt)} au ${endIso.format(dayFmt)} : $len jours consécutifs, $plays écoutes sur la série (${if (len > 0) plays / len else 0} par jour en moyenne). " +
            (if (ongoing) {
                "La série est toujours en cours — jour $len, une journée sans écoute la rompra. " +
                    (if (previousBest in 1 until len) "Ton record précédent ($previousBest jours) est déjà battu : chaque jour qui passe l'améliore. "
                    else if (previousBest >= len) "Encore ${previousBest - len + 1} jour(s) pour battre ton record personnel de $previousBest jours. " else "")
            } else {
                "Elle s'est éteinte le ${endIso.plusDays(1).format(dayFmt)}, première journée sans écoute. " +
                    (if (current > 0) "Une nouvelle série est en route : jour $current — encore ${len - current} jour(s) pour égaler ce record. "
                    else "Le compteur est à zéro aujourd'hui : la prochaine écoute relancera une série. ")
            }) +
            (when {
                danger == null -> ""
                danger.playCount <= 1 -> "Le ${Dates.parse(danger.date).format(dayFmt)}, une seule écoute a sauvé la série — il s'en est fallu d'un titre. "
                else -> "Journée la plus dangereuse : le ${Dates.parse(danger.date).format(dayFmt)}, avec seulement ${danger.playCount} ▶. "
            }) +
            "Au total, $name a été écouté $totalDays jours différents."
        val facts = buildList {
            add("Début de la série" to startIso.format(dayFmt))
            add(if (ongoing) "Toujours en cours" to "jour $len" else "Fin de la série" to endIso.format(dayFmt))
            tier?.let { add("Palier" to "${it.emoji} ${it.label} (${it.min} j et +)") }
            next?.let { add("Prochain palier" to "${it.emoji} ${it.label} — encore ${it.min - len} jour(s)") }
            danger?.let { add("Jour le plus dangereux" to "${Dates.parse(it.date).format(dayFmt)} · ${it.playCount} ▶") }
            if (previousBest > 0) add("Record précédent" to "$previousBest jours")
            add("Écoutes pendant la série" to "$plays")
            add("Meilleure journée de la série" to (streakDays.maxByOrNull { it.playCount }?.let { "${Dates.parse(it.date).format(dayFmt)} · ${it.playCount} ▶" } ?: "—"))
            add("Jours d'écoute au total" to "$totalDays")
        }
        return Story(
            "🎧 $len jours d'affilée" + (if (ongoing) " (en cours)" else "") + (tier?.let { " · ${it.emoji} ${it.label}" } ?: ""),
            narrative,
            facts,
            "Le calendrier de chaleur de la série",
            streakDays.map { Point(it.date, Dates.parse(it.date).format(shortFmt), null, it.playCount, it.playCount == danger?.playCount) },
            heat = true
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
        val global = def.id == "MOST_GLOBAL"
        val dao = db.hallOfFameDao()
        val own = dao.ofEntity(id, cat.dbName)
        val inRecordScope = own.filter { (it.periodType == "GLOBAL") == global }
        val selected = if (global && !sub.isNullOrBlank() && sub != "ALL") inRecordScope.filter { it.entryType == sub } else inRecordScope
        val sortedSelected = selected.sortedBy { it.entryDate }
        val timelineRows = inRecordScope.sortedBy { it.entryDate }

        data class SealDefinition(
            val key: String,
            val entryType: String,
            val periodType: String,
            val title: String,
            val emoji: String,
            val description: String
        )
        val sealDefinitions = if (global) listOf(
            SealDefinition("TRIPLE_DEBUT", "TRIPLE_DEBUT", "GLOBAL", "Triple Debut", "🌟", "Première apparition simultanée au #1 Daily, Weekly et Monthly."),
            SealDefinition("ALL_KILL", "ALL_KILL", "GLOBAL", "All Kill", "💥", "#1 simultané dans les trois charts : Daily, Weekly et Monthly."),
            SealDefinition("LEGENDARY_RUN", HallOfFameRules.LEGENDARY_RUN, "GLOBAL", "Legendary Run", "👑", "Au moins ${HallOfFameRules.LEGENDARY_WEEKS_AT_1} semaines cumulées au #1 hebdomadaire.")
        ) else listOf(
            SealDefinition("DIRECT_DEBUT_WEEKLY", HallOfFameRules.DIRECT_DEBUT, "WEEKLY", "Début direct · Hebdo", "🚀", "Première entrée directement à la première place du chart hebdomadaire."),
            SealDefinition("LONG_RUN_WEEKLY", HallOfFameRules.LONG_RUN, "WEEKLY", "Long règne · Hebdo", "🏆", "Règne continu d'au moins ${HallOfFameRules.LONG_RUN_WEEKS} semaines au #1 hebdomadaire."),
            SealDefinition("DIRECT_DEBUT_MONTHLY", HallOfFameRules.DIRECT_DEBUT, "MONTHLY", "Début direct · Mensuel", "🌙", "Première entrée directement à la première place du chart mensuel."),
            SealDefinition("LONG_RUN_MONTHLY", HallOfFameRules.LONG_RUN, "MONTHLY", "Long règne · Mensuel", "✨", "Règne continu d'au moins ${HallOfFameRules.LONG_RUN_MONTHS} mois au #1 mensuel.")
        ) + (if (cat == RecordCategory.ARTIST) listOf(
            SealDefinition("PERFECT_ALL_KILL_DAILY", HallOfFameRules.PERFECT_ALL_KILL, "DAILY", "Perfect All Kill · Daily", "💎", "Même snapshot Daily : artiste crédité du titre n°1 Hot 100, n°1 de l'Artist 50 et propriétaire de l'album n°1 des Albums 75."),
            SealDefinition("PERFECT_ALL_KILL_WEEKLY", HallOfFameRules.PERFECT_ALL_KILL, "WEEKLY", "Perfect All Kill · Weekly", "💎", "Même snapshot Weekly : artiste crédité du titre n°1 Hot 100, n°1 de l'Artist 50 et propriétaire de l'album n°1 des Albums 75."),
            SealDefinition("PERFECT_ALL_KILL_MONTHLY", HallOfFameRules.PERFECT_ALL_KILL, "MONTHLY", "Perfect All Kill · Monthly", "💎", "Même snapshot Monthly : artiste crédité du titre n°1 Hot 100, n°1 de l'Artist 50 et propriétaire de l'album n°1 des Albums 75."),
            SealDefinition("PERFECT_ALL_KILL_YEARLY", HallOfFameRules.PERFECT_ALL_KILL, "YEARLY", "Perfect All Kill · Yearly", "💎", "Même snapshot Yearly : artiste crédité du titre n°1 Hot 100, n°1 de l'Artist 50 et propriétaire de l'album n°1 des Albums 75.")
        ) else emptyList())
        fun sealKey(entryType: String, periodType: String): String? = sealDefinitions.firstOrNull {
            it.entryType == entryType && it.periodType == periodType
        }?.key
        fun entryTitle(entryType: String, periodType: String): String = sealDefinitions.firstOrNull {
            it.entryType == entryType && it.periodType == periodType
        }?.title ?: "${entryType.replace('_', ' ')} · $periodType"
        fun periodLabel(periodType: String): String = when (periodType) {
            "DAILY" -> "Quotidien"
            "WEEKLY" -> "Hebdomadaire"
            "MONTHLY" -> "Mensuel"
            "YEARLY" -> "Annuel"
            "GLOBAL" -> "Global"
            else -> periodType
        }
        fun detailFor(entry: com.novastats.app.data.db.entity.HallOfFameEntity): String? = when {
            entry.entryType == HallOfFameRules.PERFECT_ALL_KILL -> "Même cadence · Hot 100 / Artist 50 / Albums 75 reliés au même artiste"
            entry.entryType == "LEGENDARY_RUN" && entry.weeksAt1 > 0 -> "${entry.weeksAt1} semaines cumulées au #1"
            entry.weeksAt1 > 0 -> "${entry.weeksAt1} semaines au #1 au total"
            entry.reignStart != null && entry.reignEnd != null -> "Règne du ${short(Period.DAILY, entry.reignStart)} au ${short(Period.DAILY, entry.reignEnd)}"
            entry.entryType == "TRIPLE_DEBUT" -> "#1 simultané · Daily / Weekly / Monthly"
            entry.entryType == "ALL_KILL" -> "#1 dans les trois charts · Daily / Weekly / Monthly"
            else -> null
        }
        fun reignDuration(entry: com.novastats.app.data.db.entity.HallOfFameEntity): String? {
            val startIso = entry.reignStart ?: return null
            val endIso = entry.reignEnd ?: return null
            val start = runCatching { Dates.parse(startIso) }.getOrNull() ?: return null
            val end = runCatching { Dates.parse(endIso) }.getOrNull() ?: return null
            val periods = when (entry.periodType) {
                "WEEKLY" -> (java.time.temporal.ChronoUnit.DAYS.between(start, end) / 7L + 1L).toInt()
                "MONTHLY" -> (java.time.temporal.ChronoUnit.MONTHS.between(
                    java.time.YearMonth.from(start), java.time.YearMonth.from(end)
                ) + 1L).toInt()
                else -> return null
            }.coerceAtLeast(1)
            val weekly = entry.periodType == "WEEKLY"
            val unit = if (weekly) plural(periods, "semaine") else plural(periods, "mois", "mois")
            val adjective = if (weekly) "consécutive" else "consécutif"
            return "$periods $unit $adjective${if (periods == 1) "" else "s"}"
        }
        val seals = sealDefinitions.map { definition ->
            val earned = own.firstOrNull {
                it.entryType == definition.entryType && it.periodType == definition.periodType
            }
            val awardees = dao.distinctEntityCount(
                entityType = cat.dbName,
                globalOnly = global,
                entryType = definition.entryType,
                periodType = definition.periodType
            )
            HonorSealVisual(
                key = definition.key,
                title = definition.title,
                emoji = definition.emoji,
                description = definition.description,
                earnedDate = earned?.let { on(Period.DAILY, it.entryDate) },
                detail = earned?.let(::detailFor),
                distinctAwardees = awardees
            )
        }
        val entries = timelineRows.mapNotNull { entry ->
            val key = sealKey(entry.entryType, entry.periodType) ?: return@mapNotNull null
            HonorEntryVisual(
                sealKey = key,
                title = entryTitle(entry.entryType, entry.periodType),
                category = entry.entryType,
                date = on(Period.DAILY, entry.entryDate),
                periodLabel = periodLabel(entry.periodType),
                reign = if (entry.reignStart != null && entry.reignEnd != null)
                    "${short(Period.DAILY, entry.reignStart)} → ${short(Period.DAILY, entry.reignEnd)}" else null,
                reignDuration = reignDuration(entry),
                weeksAtOne = entry.weeksAt1,
                playsAtEntry = entry.playCountAtEntry
            )
        }
        val selectedSub = sub?.takeUnless { it == "ALL" }
        val boardSize = dao.distinctEntityCount(
            entityType = cat.dbName,
            globalOnly = global,
            entryType = if (global) selectedSub else null,
            periodType = if (global) "GLOBAL" else null
        )
        val recordDao = db.recordDao()
        val rank = recordDao.betterCount(def.id, null, cat.dbName, sub, selected.size.toDouble(), asc = false) + 1
        val tiedAtTop = recordDao.tiedAt(def.id, null, cat.dbName, sub, selected.size.toDouble())
        val topRows = recordDao.top10(def.id, null, cat.dbName, sub, asc = false).first()
        val sameScore = topRows.firstOrNull { it.r.entityId != id && it.r.value.toInt() == selected.size }
        val nearestAhead = topRows.lastOrNull { it.r.value > selected.size.toDouble() }
        val nearestBelow = topRows.firstOrNull { it.r.entityId != id && it.r.value < selected.size.toDouble() }
        val rivalRow = sameScore ?: nearestAhead ?: nearestBelow
        val rival = rivalRow?.let { row ->
            val rivalHof = dao.ofEntity(row.r.entityId, cat.dbName)
            val rivalKeys = rivalHof.mapNotNull { sealKey(it.entryType, it.periodType) }.distinct()
            val rivalRank = recordDao.betterCount(def.id, null, cat.dbName, sub, row.r.value, asc = false) + 1
            HonorRivalVisual(
                id = row.r.entityId,
                name = row.name ?: "Inconnu",
                imageUrl = row.imageUrl,
                rank = rivalRank,
                score = row.r.value.toInt(),
                earnedKeys = rivalKeys,
                tied = row.r.value.toInt() == selected.size
            )
        }
        val maxScore = when {
            !global && cat == RecordCategory.ARTIST -> 8 // quatre sceaux historiques + quatre cadences Perfect All Kill
            !global -> 4
            selectedSub != null -> 1
            else -> 3
        }
        val totalSelected = sortedSelected.size
        val byType = sortedSelected.groupBy { it.entryType to it.periodType }.map { (typePeriod, rows) ->
            "${rows.size}× ${entryTitle(typePeriod.first, typePeriod.second)}"
        }.joinToString(", ")
        val firstDate = timelineRows.firstOrNull()?.entryDate?.let { on(Period.DAILY, it) }
        val lastDate = timelineRows.lastOrNull()?.takeIf { timelineRows.size > 1 }?.entryDate?.let { on(Period.DAILY, it) }
        val narrative = if (global) {
            "$name a décroché $totalSelected distinction${if (totalSelected == 1) "" else "s"} Global${if (selectedSub == null) " distincte${if (totalSelected == 1) "" else "s"}" else " de type ${selectedSub.replace('_', ' ')}"}. " +
                "La constellation distingue Triple Debut, All Kill et Legendary Run; All Kill correspond au #1 simultané dans Daily, Weekly et Monthly. " +
                (if (firstDate != null) "Première distinction ${firstDate.lowercase()}${lastDate?.let { ", dernière $it"} ?: ""}." else "Aucune distinction enregistrée pour ce type.")
        } else {
            val pakCount = timelineRows.count { it.entryType == HallOfFameRules.PERFECT_ALL_KILL }
            "$name compte $totalSelected distinction${if (totalSelected == 1) "" else "s"} au Hall of Fame, entre débuts directs, longs règnes et Perfect All Kill inter-charts${if (pakCount > 0) " ($pakCount cadence${if (pakCount == 1) "" else "s"})" else ""}. " +
                (if (firstDate != null) "Première intronisation ${firstDate.lowercase()}${lastDate?.let { ", dernière $it"} ?: ""}. Le détail des dates, des cadences et des règnes figure dans la frise." else "Aucune intronisation n'est enregistrée.")
        }
        val visual = HonorVisual(
            isGlobal = global,
            selectedSub = selectedSub,
            score = totalSelected,
            maxScore = maxScore,
            rank = rank,
            boardSize = boardSize,
            tiedAtTop = tiedAtTop,
            rival = rival,
            seals = seals,
            entries = entries
        )
        return Story(
            headline = if (global) "🌌 $totalSelected distinction${if (totalSelected == 1) "" else "s"} Global" else "🏛️ $totalSelected ${plural(totalSelected, "entrée")} au Hall of Fame",
            narrative = narrative,
            facts = listOf(
                "Distinctions" to "$totalSelected / $maxScore",
                "Rang" to "#$rank sur $boardSize détenteurs${if (tiedAtTop > 1) " · $tiedAtTop ex æquo" else ""}",
                "Répartition" to byType.ifBlank { "—" }
            ),
            honorVisual = visual
        )
    }

    private suspend fun multiChart(cat: RecordCategory, sub: String?, id: Long, name: String): Story {
        val base = RecordCatalog.multiChartBase(sub)
        val required = RecordCatalog.multiChartChartKeys(sub)
        if (base == null || required.isEmpty()) return Story("🌐 Multi-Chart Domination", "Combinaison de charts inconnue.")
        val top2 = RecordCatalog.multiChartIsTop2(sub)
        fun idx(s: List<RecordAppearance>) = s.associate { it.date to it.position }
        val charts = mapOf(
            "D" to idx(series(cat, Period.DAILY, id)),
            "W" to idx(series(cat, Period.WEEKLY, id)),
            "M" to idx(series(cat, Period.MONTHLY, id)),
            "Y" to idx(series(cat, Period.YEARLY, id))
        )
        val dailySnapshots = db.billboardDao().snapshotDates(Period.DAILY.dbName).distinct().sorted()
        val snapshotDates = mapOf(
            "D" to dailySnapshots.toSet(),
            "W" to db.billboardDao().snapshotDates(Period.WEEKLY.dbName).toSet(),
            "M" to db.billboardDao().snapshotDates(Period.MONTHLY.dbName).toSet(),
            "Y" to db.billboardDao().snapshotDates(Period.YEARLY.dbName).toSet()
        )
        fun chartDateKey(code: String, iso: String): String {
            val date = Dates.parse(iso)
            return when (code) {
                "D" -> iso
                "W" -> Dates.weekOf(date).fromIso
                "M" -> Dates.monthOf(date).fromIso
                else -> Dates.yearOf(date).fromIso
            }
        }
        fun ranksOn(iso: String): Map<String, Int?> = charts.mapValues { (code, positions) -> positions[chartDateKey(code, iso)] }
        data class Observation(val date: String, val ranks: Map<String, Int?>)
        val observations = dailySnapshots.map { Observation(it, ranksOn(it)) }
        val qualifying = observations.filter { RecordMath.multiChartQualifies(sub ?: "", it.ranks) }
        val points = qualifying.map { observation ->
            val details = listOf("D", "W", "M", "Y").mapNotNull { code -> observation.ranks[code]?.let { "$code#$it" } }.joinToString(" · ")
            Point(
                observation.date, short(Period.DAILY, observation.date), null, null,
                top2 || required.any { (observation.ranks[it] ?: Int.MAX_VALUE) <= 3 }, details
            )
        }
        val eligibleDays = dailySnapshots.count { day ->
            required.all { code -> snapshotDates[code]?.contains(chartDateKey(code, day)) == true }
        }
        val coverage = if (eligibleDays > 0) (100 * points.size / eligibleDays) else 0
        val combo = when (base) {
            "DWMY" -> "quotidien + hebdomadaire + mensuel + annuel"
            "DWM" -> "quotidien + hebdomadaire + mensuel"
            "DW" -> "quotidien + hebdomadaire"
            "WM" -> "hebdomadaire + mensuel"
            "MY" -> "mensuel + annuel"
            else -> base
        }
        val chartLabels = mapOf("D" to "Quotidien", "W" to "Hebdomadaire", "M" to "Mensuel", "Y" to "Annuel")
        val chartPeriods = mapOf("D" to Period.DAILY, "W" to Period.WEEKLY, "M" to Period.MONTHLY, "Y" to Period.YEARLY)
        val firstByChart = required.mapNotNull { code -> charts[code]?.keys?.minOrNull()?.let { code to it } }
        val route = firstByChart.joinToString(" → ") { (code, date) -> "$code ${on(chartPeriods[code], date)}" }
        val transitions = listOf("D" to "W", "W" to "M", "M" to "Y")
        val conversionFacts = transitions.map { (from, to) ->
            var eligible = 0
            var converted = 0
            for (day in dailySnapshots) {
                val fromKey = chartDateKey(from, day); val toKey = chartDateKey(to, day)
                if (snapshotDates[from]?.contains(fromKey) != true || snapshotDates[to]?.contains(toKey) != true) continue
                val fromRank = charts[from]?.get(fromKey) ?: continue
                if (top2 && fromRank > 2) continue
                eligible++
                val toRank = charts[to]?.get(toKey)
                if (toRank != null && (!top2 || toRank <= 2)) converted++
            }
            val pct = if (eligible > 0) 100 * converted / eligible else 0
            Triple("${chartLabels[from]} → ${chartLabels[to]}", converted, eligible).let { (label, n, total) ->
                label to if (total == 0) "— (aucune fenêtre comparable)" else "$n / $total jours · $pct %"
            }
        }
        val qualifiedEpochs = qualifying.map { Dates.parse(it.date).toEpochDay().toInt() }.sorted()
        val streak = RecordMath.longestDayStreak(qualifiedEpochs)
        val streakStart = streak?.let { java.time.LocalDate.ofEpochDay(it.second.toLong()).toString() }
        val streakEnd = streak?.let { java.time.LocalDate.ofEpochDay(it.third.toLong()).toString() }
        data class WindowResult(val count: Int, val start: String, val end: String)
        fun bestWindow(size: Int): WindowResult? {
            if (dailySnapshots.isEmpty() || qualifiedEpochs.isEmpty()) return null
            var left = 0; var right = 0; var bestCount = 0; var bestStart: java.time.LocalDate? = null
            for (iso in dailySnapshots) {
                val start = Dates.parse(iso)
                val firstEpoch = start.toEpochDay().toInt(); val lastEpoch = firstEpoch + size - 1
                while (left < qualifiedEpochs.size && qualifiedEpochs[left] < firstEpoch) left++
                if (right < left) right = left
                while (right < qualifiedEpochs.size && qualifiedEpochs[right] <= lastEpoch) right++
                val count = right - left
                if (count > bestCount) { bestCount = count; bestStart = start }
            }
            val start = bestStart ?: return null
            return WindowResult(bestCount, start.toString(), start.plusDays((size - 1).toLong()).toString())
        }
        val bestWeek = bestWindow(7)
        val bestMonth = bestWindow(30)
        val bestDay = qualifying.minByOrNull { observation -> required.sumOf { observation.ranks[it] ?: Int.MAX_VALUE } }
        val stages = required.map { code ->
            ChartRank(code, chartLabels[code] ?: code, bestDay?.ranks?.get(code))
        }
        val milestones = listOf("DW", "DWM", "DWMY", "DWMY_TOP2").associateWith { level ->
            observations.count { RecordMath.multiChartQualifies(level, it.ranks) }
        }
        val topRows = db.recordDao().top10("MULTI_CHART", null, cat.dbName, sub, false).first()
        val rivalRow = topRows.filter { it.r.entityId != id }
            .minByOrNull { kotlin.math.abs(it.r.value - points.size.toDouble()) }
        val rival = rivalRow?.let { other ->
            val gap = kotlin.math.abs(other.r.value.toInt() - points.size)
            val detail = when {
                gap == 0 -> "À égalité : ${other.r.value.toInt()} jours synchronisés"
                other.r.value > points.size.toDouble() -> "Devant de $gap ${plural(gap, "jour")} sur cette variante"
                else -> "Derrière de $gap ${plural(gap, "jour")} sur cette variante"
            }
            Item(cat.dbName, other.r.entityId, other.name ?: "Inconnu", detail, other.imageUrl)
        }
        val firstDate = points.firstOrNull()?.date
        val lastDate = points.lastOrNull()?.date
        val runText = if ((streak?.first ?: 0) > 1) {
            "Plus longue chaîne : ${streak!!.first} jours consécutifs, du ${short(Period.DAILY, streakStart!!)} au ${short(Period.DAILY, streakEnd!!)}. "
        } else if (streak != null) "Sa meilleure série est d'un jour. " else ""
        val bestDayText = bestDay?.let { "Meilleure synchronisation : ${on(Period.DAILY, it.date)} (${it.ranks.filterKeys { k -> k in required }.map { (k, v) -> "$k#$v" }.joinToString(" · ")}). " } ?: ""
        val coverageText = if (eligibleDays > 0) "$coverage % des $eligibleDays jours où tous les charts concernés avaient un snapshot" else "aucune fenêtre où tous les charts concernés étaient disponibles"
        val rivalText = rival?.let { "Rival le plus proche : ${it.name} — ${it.detail.lowercase()}. " } ?: ""
        val narrative = "$name a été classé simultanément dans $combo" +
            (if (top2) ", toujours au #1 ou au #2 dans chacun de ces charts" else ", quelle que soit sa position dans ces charts") +
            ", pendant ${points.size} ${plural(points.size, "jour")}. " +
            (if (route.isNotBlank()) "Son parcours entre les échelles : $route. " else "") +
            "Cela représente $coverageText. " + bestDayText + runText + rivalText +
            "La frise montre les positions de chaque échelle aux dates de synchronisation."
        val facts = buildList {
            add("Combinaison" to combo)
            add("Règle de rang" to if (top2) "#1 ou #2 dans chaque chart" else "Présence, quelle que soit la place")
            add("Jours synchronisés" to "${points.size} jours")
            add("Couverture des occasions" to if (eligibleDays > 0) "$coverage % · ${points.size} / $eligibleDays jours" else "Aucune fenêtre comparable")
            firstDate?.let { add("Première synchronisation" to on(Period.DAILY, it)) }
            lastDate?.let { add("Dernière synchronisation" to on(Period.DAILY, it)) }
            firstByChart.forEach { (code, date) -> add("Première entrée · ${chartLabels[code]}" to on(chartPeriods[code], date)) }
            if ((streak?.first ?: 0) > 0) add("Plus longue chaîne" to "${streak!!.first} ${plural(streak.first, "jour")} · ${short(Period.DAILY, streakStart!!)} → ${short(Period.DAILY, streakEnd!!)}")
            bestWeek?.let { add("Meilleure fenêtre · 7 jours" to "${it.count} synchronisations · ${short(Period.DAILY, it.start)} → ${short(Period.DAILY, it.end)}") }
            bestMonth?.let { add("Meilleure fenêtre · 30 jours" to "${it.count} synchronisations · ${short(Period.DAILY, it.start)} → ${short(Period.DAILY, it.end)}") }
            conversionFacts.forEach { add("Conversion · ${it.first}" to it.second) }
            add("Palier · D+W" to "${milestones["DW"]} jours")
            add("Palier · D+W+M" to "${milestones["DWM"]} jours")
            add("Palier · D+W+M+Y" to "${milestones["DWMY"]} jours")
            add("Grand Chelem Top 2" to "${milestones["DWMY_TOP2"]} jours")
            bestDay?.let { add("Meilleure synchronisation" to "${on(Period.DAILY, it.date)} · ${it.ranks.filterKeys { k -> k in required }.map { (k, v) -> "$k#$v" }.joinToString(" · ")}") }
            rival?.let { add("Rival le plus proche" to "${it.name} · ${it.detail}") }
        }
        return Story(
            "🌐 ${points.size} ${plural(points.size, "jour")} · $combo" + if (top2) " · 🥈 Top 2 partout" else "",
            narrative,
            facts,
            "Dates synchronisées · positions D / W / M / Y", points,
            itemsTitle = rival?.let { "Le rival le plus proche" }, items = listOfNotNull(rival),
            multiChartVisual = MultiChartVisual(stages, top2, bestDay?.let { short(Period.DAILY, it.date) })
        )
    }

}
