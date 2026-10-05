package com.novastats.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novastats.app.NovaStatsApp
import com.novastats.app.data.db.dao.ArtistCertRow
import com.novastats.app.data.db.dao.PeriodEntityStats
import com.novastats.app.data.db.dao.RankedTrack
import com.novastats.app.data.db.dao.RankedTrackPos
import com.novastats.app.util.CrashJournal
import androidx.compose.runtime.mutableIntStateOf
import com.novastats.app.data.db.entity.AlbumEntity
import com.novastats.app.data.db.entity.ArtistEntity
import com.novastats.app.data.repository.UserImages
import com.novastats.app.data.db.entity.CertificationEntity
import com.novastats.app.data.db.entity.EntityType
import com.novastats.app.data.db.entity.HallOfFameEntity
import com.novastats.app.data.db.entity.PantheonHistoryEntity
import com.novastats.app.data.db.entity.TrackEntity
import com.novastats.app.domain.TitleNormalizer
import com.novastats.app.domain.BillboardDates
import com.novastats.app.domain.CertLevel
import com.novastats.app.domain.Certification
import com.novastats.app.domain.CertificationRules
import com.novastats.app.domain.ChartAppearance
import com.novastats.app.domain.ChartHistory
import com.novastats.app.domain.ChartHistoryStats
import com.novastats.app.domain.Dates
import com.novastats.app.domain.HallOfFameRules
import com.novastats.app.domain.PantheonRules
import com.novastats.app.domain.PantheonStatus
import com.novastats.app.domain.Period
import com.novastats.app.domain.ScoredCandidate
import kotlinx.coroutines.launch
import com.novastats.app.data.db.dao.DayCount
import com.novastats.app.data.db.dao.PriorRow
import com.novastats.app.ui.theme.Nova
import com.novastats.app.ui.theme.NovaColors
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * POPUPS.md — popups de détail déclenchés par appui long :
 *   1. 🎵 Chanson (bordure primary)        2. 🎤 Artiste (glowSecondary)      3. 💿 Album (secondary)
 *   6. 👑 Panthéon (bordure = statut)      7. 🏛️ Hall of Fame (bordure = badge le plus prestigieux)
 * Tous reposent sur NovaPopupCard (overlay 85 %, 92 %, 16 dp, scroll interne, FERMER, fade, dégradé bordure → fond).
 */

/** Cible d'un popup de détail. */
sealed interface DetailTarget {
    /** [period] = période choisie dans Stats : les chiffres du popup s'y rapportent (GLOBAL = cumul all-time). */
    data class Track(val id: Long, val period: Period = Period.GLOBAL) : DetailTarget
    data class Artist(val id: Long, val period: Period = Period.GLOBAL) : DetailTarget
    data class Album(val id: Long, val period: Period = Period.GLOBAL) : DetailTarget
    /** Fiche artiste Panthéon (bordure selon le statut). */
    data class Pantheon(val artistId: Long) : DetailTarget
    /** Historique complet d'une entrée Hall of Fame. */
    data class HallOfFame(val entityId: Long, val entityType: String) : DetailTarget
}

private val dateFmt = SimpleDateFormat("d MMM yyyy", Locale.FRANCE)
private val ceremonyFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)
fun formatDate(ms: Long?): String = ms?.let { dateFmt.format(it) } ?: "—"
private fun formatIso(iso: String?): String = iso?.let { runCatching { Dates.parse(it).format(ceremonyFmt) }.getOrNull() } ?: "—"

fun certificationLabel(c: CertificationEntity?): String? {
    c ?: return null
    val level = CertLevel.entries.firstOrNull { it.dbName == c.level } ?: return null
    return Certification(level, c.multiplier).label()
}

/** Badge certification (emoji + libellé) coloré selon le niveau. */
@Composable
fun CertBadge(level: CertLevel?, multiplier: Int = 1, fallback: String = "Non certifié") {
    val theme = Nova.theme
    val color = if (level == null) theme.textSecondary else certColor(level)
    val text = level?.let { Certification(it, multiplier).label() } ?: fallback
    Text(
        text, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.10f)).padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
fun DetailPopupHost(target: DetailTarget?, onDismiss: () -> Unit) {
    if (target == null) return
    when (target) {
        is DetailTarget.Track -> TrackPopup(target.id, target.period, onDismiss)
        is DetailTarget.Artist -> ArtistPopup(target.id, target.period, onDismiss)
        is DetailTarget.Album -> AlbumPopup(target.id, target.period, onDismiss)
        is DetailTarget.Pantheon -> PantheonPopup(target.artistId, onDismiss)
        is DetailTarget.HallOfFame -> HallOfFamePopup(target.entityId, target.entityType, onDismiss)
    }
}

/** Positions Billboard (dernier classement) par période pour une entité. */
private suspend fun billboardRanks(app: NovaStatsApp, entityType: String, id: Long): Map<Period, Int?> {
    val dao = app.database.billboardDao()
    return Period.entries.associateWith { p ->
        val h = when (entityType) {
            EntityType.TRACK -> dao.trackHistory(p.dbName, id)
            EntityType.ALBUM -> dao.albumHistory(p.dbName, id)
            else -> dao.artistHistory(p.dbName, id)
        }
        h.lastOrNull()?.position
    }
}

/** Pastille « période choisie » affichée sous le titre des popups Stats (rien en Global). */
@Composable
private fun PeriodChip(period: Period, color: Color) {
    if (period == Period.GLOBAL) return
    val range = Dates.statsRangeFor(period)
    Text(
        "📅 ${period.frLabel} · ${periodCaption(period, range)}", color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.10f)).padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

private fun periodWord(period: Period) = when (period) {
    Period.DAILY -> "aujourd'hui"; Period.WEEKLY -> "sur 7 jours"; Period.MONTHLY -> "ce mois"; Period.YEARLY -> "cette année"; Period.GLOBAL -> "all time"
}

/* ================================ 1. 🎵 CHANSON ================================ */

@Composable
private fun TrackPopup(trackId: Long, period: Period, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as NovaStatsApp
    val db = app.database
    val theme = Nova.theme
    val scoped = period != Period.GLOBAL
    val range = remember(period) { Dates.statsRangeFor(period) }
    var ps by remember { mutableStateOf<PeriodEntityStats?>(null) }
    var track by remember { mutableStateOf<TrackEntity?>(null) }
    var artists by remember { mutableStateOf("") }
    var album by remember { mutableStateOf<AlbumEntity?>(null) }
    var ranks by remember { mutableStateOf<Map<Period, Int?>>(emptyMap()) }
    /** Répartition par version du groupe (original + remix feat. / versions avec invité) : titre → écoutes propres. */
    var versions by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var original by remember { mutableStateOf<TrackEntity?>(null) }
    /** Série quotidienne (prévision du prochain palier). */
    var series by remember { mutableStateOf<List<DayCount>>(emptyList()) }
    val cert by remember { db.certificationDao().observe(trackId, EntityType.TRACK) }.collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(trackId, period) {
        val t = db.trackDao().getById(trackId) ?: return@LaunchedEffect
        track = t
        original = t.originalTrackId?.let { db.trackDao().getById(it) }
        val linked = db.trackDao().versionsOf(trackId)
        versions = if (linked.isEmpty()) emptyList() else listOf("Original" to db.trackDao().ownPlays(trackId)) + linked.map { v -> v.title.removePrefix(t.title).trim().trim('(', ')').ifBlank { v.title } to v.playCount }
        if (scoped) ps = db.trackDao().periodStats(trackId, range.fromIso, range.toIso)
        val ids = db.trackLinkDao().artistIdsForTrack(trackId).ifEmpty { listOf(t.artistId) }
        artists = ids.mapNotNull { db.artistDao().getById(it)?.name }.joinToString(", ")
        album = t.albumId?.let { db.albumDao().getById(it) }
        series = runCatching { db.dailyPlayDao().seriesForTrack(trackId) }.getOrDefault(emptyList())
        ranks = Period.entries.associateWith { p ->
            if (p == Period.GLOBAL) db.trackDao().rankAllTime(trackId)
            else Dates.statsRangeFor(p).let { r -> db.trackDao().rankForPeriod(trackId, r.fromIso, r.toIso) }
        }
    }
    val t = track
    NovaPopupCard(
        borderColor = theme.primary, onDismiss = onDismiss, backdropUrl = t?.coverUrl,
        banner = {
            // 180 dp — pochette carrée centrée uniquement
            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(132.dp).shadow(16.dp, RoundedCornerShape(12.dp), spotColor = theme.primary, ambientColor = theme.primary).border(2.dp, theme.primary, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))) {
                    CoverArt(t?.coverUrl, t?.title ?: "?", size = 132, zoomable = true)
                }
            }
        }
    ) {
        if (t == null) { Text("Chargement…", color = theme.textSecondary); return@NovaPopupCard }
        Text(t.title, color = theme.text, fontWeight = FontWeight.Black, fontSize = 20.sp)
        Text(artists, color = theme.primary, fontWeight = FontWeight.SemiBold)
        album?.let { Text(it.title, color = theme.textSecondary, style = MaterialTheme.typography.bodyMedium) }
        val o = original
        if (o != null) Text("Version liée à « ${o.title} » — ses écoutes comptent dans le total de l'original (classements, records, certifications)", color = theme.accent, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        else if (t.isRemix) Text("Version featuring — original pas encore écouté", color = theme.accent, style = MaterialTheme.typography.labelSmall)
        PeriodChip(period, theme.primary)
        Spacer(Modifier.height(8.dp))
        if (scoped) {
            StatGlass(
                plays = ps?.plays ?: 0,
                durationMs = ps?.durationMs ?: 0L,
                extras = listOf("${ps?.activeDays ?: 0}" to "jour${if ((ps?.activeDays ?: 0) > 1) "s" else ""} actif${if ((ps?.activeDays ?: 0) > 1) "s" else ""}"),
                playLabel = "écoutes ${periodWord(period)}"
            )
            Text("Cumul all time : ${formatCount(t.playCount)} écoutes · ${formatDuration(t.totalDurationMs)}", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        } else StatGlass(plays = t.playCount, durationMs = t.totalDurationMs, playLabel = "écoutes")
        if (versions.isNotEmpty()) {
            PopupSection("🔗 Versions fusionnées")
            Text(
                "Ce total inclut ${versions.size - 1} version${if (versions.size > 2) "s" else ""} liée${if (versions.size > 2) "s" else ""} à ce titre (remix featuring / version avec invité). Répartition des ${formatCount(t.playCount)} écoutes :",
                color = theme.textSecondary, style = MaterialTheme.typography.bodySmall
            )
            versions.forEach { (label, n) -> PopupInfoRow(label, "${formatCount(n)} ▶" + if (t.playCount > 0) "  (${100 * n / t.playCount} %)" else "") }
        }
        PopupSection(if (scoped) "🏅 Certification (cumul all time)" else "🏅 Certification")
        val lvl = cert?.let { c -> CertLevel.entries.firstOrNull { it.dbName == c.level } }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (lvl != null) MedalBadge(lvl, cert?.multiplier ?: 1, size = 58.dp)
            CertBadge(lvl, cert?.multiplier ?: 1)
            if (lvl != null) Text("obtenue le ${formatDate(cert?.certifiedAt)}", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
            else {
                val next = CertificationRules.TRACK.next(t.playCount)
                val need = CertificationRules.TRACK.required(next)
                Text("${next.label()} dans ${(need - t.playCount).coerceAtLeast(0)} écoutes", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        PopupSection("📊 Positions par période")
        PositionsTable(ranks)
        ChartRunSection(EntityType.TRACK, trackId, theme.primary)
        val nextCert = CertificationRules.TRACK.next(t.playCount)
        ForecastSection(t.playCount, CertificationRules.TRACK.required(nextCert), nextCert.label(), series, theme.primary, "🏅")
        PopupSection("📅 Historique")
        if (scoped) {
            PopupInfoRow("Première écoute (${period.frLabel.lowercase()})", formatIso(ps?.firstDate))
            PopupInfoRow("Dernière écoute (${period.frLabel.lowercase()})", formatIso(ps?.lastDate))
        }
        PopupInfoRow("Première écoute all time", formatDate(t.firstPlayedAt))
        PopupInfoRow("Dernière écoute all time", formatDate(t.lastPlayedAt))
        PopupInfoRow("Streak", "${t.currentStreak} j (record ${t.bestStreak} j)")
        PopupInfoRow("Rang de découverte", t.discoveryRank?.let { "#$it" } ?: "—")
    }
}

/* ================================ 2. 🎤 ARTISTE ================================ */

/** Bannière artiste 180 dp : fond flou + dégradé noir, photo en cercle 90 dp + bordure glow, nom en MAJUSCULES, statut Panthéon coloré. */
@Composable
private fun ArtistBanner(a: ArtistEntity?, ring: Color, status: PantheonStatus?, statusColor: Color, holographic: Boolean = false) {
    val theme = Nova.theme
    Box(Modifier.fillMaxWidth().height(180.dp)) {
        BlurredBackdrop(a?.photoUrl, ring, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            val ringBrush = if (holographic) Brush.sweepGradient(HoloColors) else Brush.linearGradient(listOf(ring, ring))
            Box(Modifier.size(90.dp).shadow(22.dp, RoundedCornerShape(50), ambientColor = ring, spotColor = ring).border(3.dp, ringBrush, RoundedCornerShape(50)).clip(RoundedCornerShape(50))) {
                CoverArt(a?.photoUrl, a?.name ?: "?", size = 90, circle = true, zoomable = true)
            }
            Spacer(Modifier.height(8.dp))
            Text((a?.name ?: "").uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 16.dp))
            if (status != null) Text("${status.emoji} ${status.label.uppercase()}", color = statusColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, letterSpacing = 1.5.sp)
            else Text("Pas encore au Panthéon", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ArtistPopup(artistId: Long, period: Period, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as NovaStatsApp
    val db = app.database
    val theme = Nova.theme
    val scoped = period != Period.GLOBAL
    val range = remember(period) { Dates.statsRangeFor(period) }
    var ps by remember { mutableStateOf<PeriodEntityStats?>(null) }
    var artist by remember { mutableStateOf<ArtistEntity?>(null) }
    /** Toutes les chansons de l'artiste sur la période, avec leur position dans le classement titres. */
    var songs by remember { mutableStateOf<List<RankedTrackPos>>(emptyList()) }
    var songsShown by remember(artistId, period) { mutableIntStateOf(5) }
    var loadError by remember { mutableStateOf<String?>(null) }
    /** Albums (titre, écoutes) — all time ou sur la période. */
    var albums by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var totalAllTime by remember { mutableStateOf(0) }
    var periodPlays by remember { mutableStateOf(0) }
    var periodTotal by remember { mutableStateOf(0) }
    var ranks by remember { mutableStateOf<Map<Period, Int?>>(emptyMap()) }
    var history by remember { mutableStateOf<List<PantheonHistoryEntity>>(emptyList()) }
    var hof by remember { mutableStateOf<List<HallOfFameEntity>>(emptyList()) }
    /** Série quotidienne (prévision du prochain statut Panthéon). */
    var series by remember { mutableStateOf<List<DayCount>>(emptyList()) }

    val imgVersion by UserImages.version.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    LaunchedEffect(artistId, period, imgVersion) {
        // Chaque bloc est isolé : une requête en échec n'empêche ni l'affichage du reste, ni ne ferme l'app (trace dans le journal des plantages)
        suspend fun safe(tag: String, block: suspend () -> Unit) {
            try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Throwable) { loadError = "$tag : ${e.message ?: e.javaClass.simpleName}"; CrashJournal.note(ctx, "ArtistPopup/$tag (${period.dbName})", e) }
        }
        safe("artiste") { artist = db.artistDao().getById(artistId) }
        safe("panthéon") { history = db.pantheonDao().history(artistId) }
        safe("hall of fame") { hof = db.hallOfFameDao().ofEntity(artistId, EntityType.ARTIST) }
        if (scoped) {
            safe("stats période") { ps = db.artistDao().periodStats(artistId, range.fromIso, range.toIso) }
            safe("chansons") { songs = db.trackDao().allOfArtistForPeriod(artistId, range.fromIso, range.toIso) }
            safe("albums") { albums = db.albumDao().ofArtistForPeriod(artistId, range.fromIso, range.toIso).map { it.album.title to it.periodPlays } }
        } else {
            safe("chansons") { songs = db.trackDao().allOfArtistAllTime(artistId) }
            safe("albums") { albums = db.albumDao().ofArtist(artistId).map { it.title to it.playCount } }
        }
        safe("total") { totalAllTime = db.scrobbleDao().countConfirmed() }
        // Part d'écoute : sur la période choisie (ou 7 derniers jours en Global)
        val share = if (scoped) range else Dates.statsRangeFor(Period.WEEKLY)
        safe("part") { periodPlays = db.artistDao().playsForPeriod(artistId, share.fromIso, share.toIso); periodTotal = db.dailyPlayDao().playsBetween(share.fromIso, share.toIso) }
        safe("positions") { ranks = Period.entries.associateWith { p -> val r = Dates.statsRangeFor(p); db.artistDao().rankForPeriod(artistId, r.fromIso, r.toIso) } }
        safe("série") { series = db.dailyPlayDao().seriesForArtist(artistId) }
    }
    val a = artist
    val status = PantheonStatus.fromDb(a?.pantheonStatus)
    val statusColor = status?.let { pantheonColor(it) } ?: theme.glowSecondary
    NovaPopupCard(
        borderColor = theme.glowSecondary, onDismiss = onDismiss, backdropUrl = a?.photoUrl,
        banner = { ArtistBanner(a, theme.glowSecondary, status, statusColor) }
    ) {
        if (a == null) { Text("Chargement…", color = theme.textSecondary); return@NovaPopupCard }
        if (scoped) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { PeriodChip(period, theme.glowSecondary) }
            Spacer(Modifier.height(6.dp))
            StatGlass(
                plays = ps?.plays ?: 0,
                durationMs = ps?.durationMs ?: 0L,
                extras = listOf("${ps?.distinctTracks ?: 0}" to "titres", "${ps?.distinctAlbums ?: 0}" to "albums"),
                color = theme.glowSecondary
            )
            Text("Cumul all time : ${formatCount(a.playCount)} écoutes · ${formatDuration(a.totalDurationMs)} · ${a.distinctTracks} titres · ${a.distinctAlbums} albums", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        } else StatGlass(
            plays = a.playCount,
            durationMs = a.totalDurationMs,
            extras = listOf("${a.distinctTracks}" to "titres", "${a.distinctAlbums}" to "albums"),
            color = theme.glowSecondary
        )
        // 🖼️ Autres photos : propositions de toutes les sources, vérifiées avec ta bibliothèque ; un appui = photo 👤 USER
        ImagePickerBar(EntityType.ARTIST, a.artistId, a.name, null, a.photoUrl, a.photoSource, theme.glowSecondary, circle = true) { artist = db.artistDao().getById(artistId) }
        PopupSection("👑 Statut Panthéon", theme.glowSecondary)
        PopupInfoRow("Statut actuel", status?.let { "${it.emoji} ${it.label}" } ?: "Aucun (Star à 425 écoutes)", valueColor = statusColor)
        history.forEach { h ->
            val st = PantheonStatus.fromDb(h.status)
            PopupInfoRow("${st?.emoji ?: "•"} ${st?.label ?: h.status}", "${formatDate(h.dateReached)} · ${formatCount(h.playCountAtStatus)} ▶", valueColor = st?.let { pantheonColor(it) } ?: theme.text)
        }
        if (hof.isNotEmpty()) {
            PopupSection("🏛️ Hall of Fame", theme.glowSecondary)
            hof.forEach { e -> PopupInfoRow(hofBadgeLabel(e.entryType) + " · " + e.periodType.lowercase().replaceFirstChar { it.uppercase() }, formatIso(e.entryDate)) }
        }
        PopupSection("📊 Positions par période", theme.glowSecondary)
        PositionsTable(ranks)
        ChartRunSection(EntityType.ARTIST, artistId, theme.glowSecondary)
        PantheonRules.next(status)?.let { next ->
            ForecastSection(a.playCount, next.playsThreshold, "${next.emoji} ${next.label}", series, statusColor, "👑")
        }
        loadError?.let { Text("⚠️ Données partielles — $it", color = NovaColors.Gold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 4.dp)) }
        PopupSection(if (scoped) "🎵 Chansons ${periodWord(period)} (${songs.size})" else "🎵 Chansons all time (${songs.size})", theme.glowSecondary)
        Text(
            if (scoped) "Position dans le classement titres ${periodWord(period)} — même au-delà du Top 300." else "Position dans le classement général des titres.",
            color = theme.textSecondary, style = MaterialTheme.typography.labelSmall
        )
        if (songs.isEmpty()) Text("—", color = theme.textSecondary)
        songs.take(songsShown).forEach { s -> ArtistSongRow(s, theme.glowSecondary) }
        if (songs.size > 5) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            if (songs.size > songsShown) TextButton(onClick = { songsShown += 5 }) { Text("Voir plus (+${minOf(5, songs.size - songsShown)}) · ${songs.size - songsShown} restantes", color = theme.glowSecondary, fontWeight = FontWeight.Bold) }
            if (songsShown > 5) TextButton(onClick = { songsShown = 5 }) { Text("Réduire", color = theme.textSecondary) }
        }
        PopupSection(if (scoped) "💿 Albums écoutés ${periodWord(period)}" else "💿 Albums en bibliothèque", theme.glowSecondary)
        if (albums.isEmpty()) Text(if (scoped) "Aucun album crédité sur la période" else "Aucun album crédité (titres en featuring uniquement)", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
        albums.forEach { (title, plays) -> PopupInfoRow(title, "${formatCount(plays)} ▶") }
        PopupSection("📅 Historique & parts d'écoute", theme.glowSecondary)
        if (scoped) {
            PopupInfoRow("Première écoute (${period.frLabel.lowercase()})", formatIso(ps?.firstDate))
            PopupInfoRow("Dernière écoute (${period.frLabel.lowercase()})", formatIso(ps?.lastDate))
            PopupInfoRow("Jours actifs", "${ps?.activeDays ?: 0}")
        }
        PopupInfoRow("Première écoute all time", formatDate(a.firstPlayedAt))
        PopupInfoRow("Part All Time", if (totalAllTime > 0) String.format(Locale.FRANCE, "%.1f %%", 100.0 * a.playCount / totalAllTime) else "—")
        PopupInfoRow(if (scoped) "Part ${periodWord(period)}" else "Part 7 derniers jours", if (periodTotal > 0) String.format(Locale.FRANCE, "%.1f %%", 100.0 * periodPlays / periodTotal) else "—")
    }
}

/** Ligne « position · titre (album) · écoutes » du popup artiste. Hors Top 300 = position grisée. */
@Composable
private fun ArtistSongRow(s: RankedTrackPos, color: Color) {
    val theme = Nova.theme
    val r = s.periodRank
    val posColor = when { r == 1 -> NovaColors.Gold; r == 2 -> NovaColors.Silver; r == 3 -> Color(0xFFCD7F32); r <= 10 -> color; r <= 300 -> theme.text; else -> theme.textSecondary.copy(alpha = 0.7f) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(positionLabel(r, Int.MAX_VALUE), color = posColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(62.dp))
        Column(Modifier.weight(1f)) {
            Text(s.track.title, color = theme.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            s.albumTitle?.let { Text(it, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Text("${formatCount(s.periodPlays)} ▶", color = theme.text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Barre image d'un popup artiste / album :
 *  🖼️ galerie · 🌐 web (recherche d'images dans le navigateur, image téléchargée appliquée au retour) ·
 *  💡 Autres photos (propositions des APIs, vérifiées avec ta bibliothèque) · 🔑 mots-clés (orientent web + APIs).
 */
@Composable
private fun ImagePickerBar(
    type: String, id: Long, name: String, artistName: String?, currentUrl: String?, currentSource: String?,
    color: Color, circle: Boolean, onChanged: suspend () -> Unit
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as NovaStatsApp
    val theme = Nova.theme
    val scope = rememberCoroutineScope()
    var open by remember(type, id) { mutableStateOf(false) }
    var props by remember(type, id) { mutableStateOf<List<ScoredCandidate>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var keywords by remember(type, id) { mutableStateOf(UserImages.keywords(ctx, type, id)) }
    var editKeywords by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val what = if (type == EntityType.ARTIST) "photos" else "pochettes"

    fun applyUrl(url: String) {
        busy = true
        scope.launch {
            val ok = UserImages.apply(app, type, id, url)
            message = if (ok) "✅ Image enregistrée (👤 toi)" else "⚠️ Échec de l'enregistrement"
            onChanged(); busy = false
        }
    }
    // 🖼️ Galerie (sélecteur de photos système, sans permission)
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val url = UserImages.importUri(ctx, uri, "${type.lowercase()}_$id")
            if (url == null) { message = "⚠️ Image illisible"; busy = false } else applyUrl(url)
        }
    }
    // 🌐 Web : permission photos (pour détecter le téléchargement au retour), puis navigateur
    fun launchWeb() {
        val q = UserImages.webQuery(type, name, artistName, keywords)
        if (!UserImages.openWebSearch(ctx, type, id, name, q)) message = "⚠️ Aucun navigateur disponible"
        else message = "🌐 Télécharge l'image choisie puis reviens : elle sera appliquée. Sinon « Partager l'image » → NovaStats."
    }
    val askPhotos = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { launchWeb() }

    LaunchedEffect(open, keywords) {
        if (open) {
            props = null
            props = runCatching {
                if (type == EntityType.ARTIST) app.enricher.proposeArtist(id, name, keywords)
                else app.enricher.proposeAlbum(id, name, artistName ?: "", keywords)
            }.getOrDefault(emptyList())
        }
    }

    @Composable
    fun Chip(label: String, filled: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
        Text(
            label, color = if (filled) Color.White else color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                .background(if (filled) color else Color.Transparent)
                .border(1.dp, color.copy(alpha = if (enabled) 0.6f else 0.25f), RoundedCornerShape(12.dp))
                .clickable(enabled = enabled && !busy) { onClick() }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            maxLines = 1
        )
    }

    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        Chip("🖼️") { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        Chip("🌐") { if (UserImages.canReadImages(ctx)) launchWeb() else askPhotos.launch(UserImages.readImagesPermission) }
        Chip(if (open) "💡 Autres $what ▴" else "💡 Autres $what ▾", filled = open) { open = !open }
        Chip(if (keywords.isBlank()) "🔑" else "🔑 •") { editKeywords = true }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
        Text(
            buildString {
                append("source : "); append(when (currentSource) { null -> "—"; "USER" -> "👤 toi"; else -> currentSource })
                if (keywords.isNotBlank()) append("  ·  🔑 $keywords")
            },
            color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
    message?.let { Text(it, color = if (it.startsWith("⚠️")) Color(0xFFE67E22) else theme.textSecondary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) }

    if (editKeywords) {
        var draft by remember { mutableStateOf(keywords) }
        AlertDialog(
            onDismissRequest = { editKeywords = false },
            containerColor = theme.surface, titleContentColor = theme.text, textContentColor = theme.textSecondary,
            title = { Text("🔑 Mots-clés pour $name") },
            text = {
                Column {
                    Text("Ajoutés à la recherche web et aux requêtes des APIs (ex. « 2024 live », « deluxe », « kpop »). Un candidat qui contient un mot-clé gagne +10.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = draft, onValueChange = { draft = it }, singleLine = true, placeholder = { Text("mots-clés…") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { UserImages.setKeywords(ctx, type, id, draft); keywords = draft.trim(); editKeywords = false }) { Text("Enregistrer", color = color) } },
            dismissButton = { TextButton(onClick = { editKeywords = false }) { Text("Annuler", color = theme.textSecondary) } }
        )
    }

    if (!open) return
    val list = props
    when {
        list == null -> Text("Recherche en cours…", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(6.dp), textAlign = TextAlign.Center)
        list.isEmpty() -> Text("Aucune autre image trouvée — essaie 🔑 des mots-clés, 🌐 le web ou 🖼️ la galerie.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(6.dp), textAlign = TextAlign.Center)
        else -> Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { p ->
                val c = p.candidate
                val current = c.imageUrl == currentUrl
                val note = p.reasons.lastOrNull { it.contains("bibliothèque") || it.contains("non vérifiable") }
                val good = note?.contains("en commun") == true
                Column(
                    Modifier.width(96.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (current) color.copy(alpha = 0.18f) else theme.surface)
                        .border(1.dp, if (current) color else Color.Transparent, RoundedCornerShape(12.dp))
                        .clickable(enabled = !busy && !current) { c.imageUrl?.let { applyUrl(it) } }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CoverArt(c.imageUrl, c.name, size = 72, circle = circle)
                    Spacer(Modifier.height(4.dp))
                    Text("${c.source.emoji} ${c.source.label}", color = theme.text, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (type == EntityType.ALBUM) Text(c.name, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${p.score} %", color = if (p.score >= 90) Color(0xFF2ECC71) else if (p.score >= 70) Color(0xFFF1C40F) else theme.textSecondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    if (note != null) Text(
                        if (good) "✅ " + note.substringBefore(" titre").trim() + " en commun" else if (note.contains("pas assez")) "❔ pas assez de titres" else if (note.contains("non vérifiable")) "❔ non vérifiable" else "⚠️ aucun en commun",
                        color = if (good) Color(0xFF2ECC71) else theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 2, textAlign = TextAlign.Center
                    )
                    if (current) Text("actuelle", color = color, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/* ================================ 3. 💿 ALBUM ================================ */

@Composable
private fun AlbumPopup(albumId: Long, period: Period, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as NovaStatsApp
    val db = app.database
    val theme = Nova.theme
    val scoped = period != Period.GLOBAL
    val range = remember(period) { Dates.statsRangeFor(period) }
    var ps by remember { mutableStateOf<PeriodEntityStats?>(null) }
    var album by remember { mutableStateOf<AlbumEntity?>(null) }
    var artistName by remember { mutableStateOf("") }
    var artistCount by remember { mutableStateOf(0) }
    var tracks by remember { mutableStateOf<List<RankedTrack>>(emptyList()) }
    var ranks by remember { mutableStateOf<Map<Period, Int?>>(emptyMap()) }
    /** Série quotidienne (prévision du prochain palier). */
    var series by remember { mutableStateOf<List<DayCount>>(emptyList()) }
    val cert by remember { db.certificationDao().observe(albumId, EntityType.ALBUM) }.collectAsStateWithLifecycle(initialValue = null)
    // Fiche d'un titre ouverte depuis la liste (album partagé : chaque ligne est cliquable)
    var openTrack by remember { mutableStateOf<Long?>(null) }
    openTrack?.let { TrackPopup(it, period) { openTrack = null } }

    val imgVersion by UserImages.version.collectAsStateWithLifecycle()
    LaunchedEffect(albumId, period, imgVersion) {
        suspend fun safe(tag: String, block: suspend () -> Unit) {
            try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Throwable) { CrashJournal.note(ctx, "AlbumPopup/$tag (${period.dbName})", e) }
        }
        val al = db.albumDao().getById(albumId) ?: return@LaunchedEffect
        album = al
        safe("artiste") {
            artistName = al.artistId?.let { db.artistDao().getById(it)?.name } ?: TitleNormalizer.SHARED_ALBUM_LABEL
            if (al.artistId == null) artistCount = db.albumDao().distinctArtistCount(al.albumId)
        }
        if (scoped) {
            safe("stats période") { ps = db.albumDao().periodStats(albumId, range.fromIso, range.toIso) }
            safe("chansons") { tracks = db.trackDao().ofAlbumForPeriod(albumId, range.fromIso, range.toIso) }
        } else safe("chansons") { tracks = db.trackDao().ofAlbum(albumId) }
        safe("positions") { ranks = Period.entries.associateWith { p -> val r = Dates.statsRangeFor(p); db.albumDao().rankForPeriod(albumId, r.fromIso, r.toIso) } }
        safe("série") { series = db.dailyPlayDao().seriesForAlbum(albumId) }
    }
    val al = album
    NovaPopupCard(
        borderColor = theme.secondary, onDismiss = onDismiss, backdropUrl = al?.coverUrl,
        banner = {
            // Pochette 120 × 120 dp sur fond flou
            Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
                BlurredBackdrop(al?.coverUrl, theme.secondary, Modifier.fillMaxSize())
                Box(Modifier.size(120.dp).shadow(16.dp, RoundedCornerShape(12.dp), spotColor = theme.secondary, ambientColor = theme.secondary).border(2.dp, theme.secondary, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))) {
                    CoverArt(al?.coverUrl, al?.title ?: "?", size = 120, zoomable = true)
                }
            }
        }
    ) {
        if (al == null) { Text("Chargement…", color = theme.textSecondary); return@NovaPopupCard }
        Text(al.title, color = theme.text, fontWeight = FontWeight.Black, fontSize = 20.sp)
        Text(artistName, color = theme.secondary, fontWeight = FontWeight.SemiBold)
        if (al.isShared) Text("💿 Album multi-artistes · $artistCount artiste${if (artistCount > 1) "s" else ""} — total de tous ses titres, chaque artiste est crédité de ses propres titres", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        PeriodChip(period, theme.secondary)
        // 🖼️ 🌐 💡 🔑 : pochette choisie à la main / web / propositions des APIs / mots-clés
        ImagePickerBar(EntityType.ALBUM, al.albumId, al.title, artistName, al.coverUrl, al.coverSource, theme.secondary, circle = false) { album = db.albumDao().getById(albumId) }
        Spacer(Modifier.height(8.dp))
        if (scoped) {
            StatGlass(
                plays = ps?.plays ?: 0,
                durationMs = ps?.durationMs ?: 0L,
                extras = listOf("${ps?.distinctTracks ?: 0}" to "titres écoutés"),
                playLabel = "écoutes ${periodWord(period)}",
                color = theme.secondary
            )
            Text("Cumul all time : ${formatCount(al.playCount)} écoutes · ${formatDuration(al.totalDurationMs)} · ${al.distinctTracksPlayed} titres", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        } else StatGlass(
            plays = al.playCount,
            durationMs = al.totalDurationMs,
            extras = listOf("${al.distinctTracksPlayed}" to "titres écoutés"),
            color = theme.secondary
        )
        PopupSection(if (scoped) "🏅 Certification album (cumul all time)" else "🏅 Certification album", theme.secondary)
        val lvl = cert?.let { c -> CertLevel.entries.firstOrNull { it.dbName == c.level } }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (lvl != null) MedalBadge(lvl, cert?.multiplier ?: 1, size = 58.dp)
            CertBadge(lvl, cert?.multiplier ?: 1)
            if (lvl != null) Text("obtenue le ${formatDate(cert?.certifiedAt)}", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
            else {
                val next = CertificationRules.ALBUM.next(al.playCount)
                val need = CertificationRules.ALBUM.required(next)
                Text("${next.label()} dans ${(need - al.playCount).coerceAtLeast(0)} écoutes", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        PopupSection("📊 Positions par période", theme.secondary)
        PositionsTable(ranks)
        ChartRunSection(EntityType.ALBUM, albumId, theme.secondary)
        val nextCert = CertificationRules.ALBUM.next(al.playCount)
        ForecastSection(al.playCount, CertificationRules.ALBUM.required(nextCert), nextCert.label(), series, theme.secondary, "🏅")

        PopupSection(if (scoped) "🎵 Chansons ${periodWord(period)} (${tracks.size})" else "🎵 Chansons (${tracks.size})", theme.secondary)
        if (tracks.isEmpty()) Text("—", color = theme.textSecondary)
        tracks.forEachIndexed { i, t ->
            Row(Modifier.fillMaxWidth().clickable { openTrack = t.track.trackId }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${i + 1}. ${t.track.title}", color = theme.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (al.isShared || t.artistName.contains(',')) Text(t.artistName, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("${formatCount(t.periodPlays)} ▶", color = theme.text, fontWeight = FontWeight.SemiBold)
            }
        }
        PopupSection("📅 Historique", theme.secondary)
        if (scoped) {
            PopupInfoRow("Première écoute (${period.frLabel.lowercase()})", formatIso(ps?.firstDate))
            PopupInfoRow("Dernière écoute (${period.frLabel.lowercase()})", formatIso(ps?.lastDate))
            PopupInfoRow("Jours actifs", "${ps?.activeDays ?: 0}")
        }
        PopupInfoRow("Première écoute all time", formatDate(al.firstPlayedAt))
        PopupInfoRow("Dernière écoute all time", formatDate(al.lastPlayedAt))
    }
}

/* ================================ 6. 👑 PANTHÉON ================================ */

private data class PantheonDetail(
    val artist: ArtistEntity, val history: List<PantheonHistoryEntity>, val certs: List<ArtistCertRow>,
    val series: List<DayCount>, val statsRanks: Map<Period, Int?>, val billboardRanks: Map<Period, Int?>
)

@Composable
private fun PantheonPopup(artistId: Long, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as NovaStatsApp
    val db = app.database
    val theme = Nova.theme
    var d by remember { mutableStateOf<PantheonDetail?>(null) }
    LaunchedEffect(artistId) {
        val a = db.artistDao().getById(artistId) ?: return@LaunchedEffect
        d = PantheonDetail(
            a, db.pantheonDao().history(artistId), db.certificationDao().certsOfArtist(artistId), db.dailyPlayDao().seriesForArtist(artistId),
            Period.entries.associateWith { p -> val r = Dates.statsRangeFor(p); db.artistDao().rankForPeriod(artistId, r.fromIso, r.toIso) },
            billboardRanks(app, EntityType.ARTIST, artistId)
        )
    }
    val det = d
    val status = PantheonStatus.fromDb(det?.artist?.pantheonStatus)
    val mythic = status == PantheonStatus.MYTHIQUE
    val color = status?.let { pantheonColor(it) } ?: theme.glowSecondary
    // Glow léger / moyen / doré / intense / holographique
    val glow = when (status) { PantheonStatus.STAR -> 8; PantheonStatus.SUPERSTAR -> 14; PantheonStatus.MEGASTAR -> 20; PantheonStatus.LEGENDE -> 28; PantheonStatus.MYTHIQUE -> 32; null -> 6 }

    NovaPopupCard(
        borderColor = color, onDismiss = onDismiss, glowDp = glow, holographic = mythic, backdropUrl = det?.artist?.photoUrl,
        banner = { ArtistBanner(det?.artist, color, status, if (mythic) Color.White else color, holographic = mythic) }
    ) {
        if (det == null) { Text("Chargement…", color = theme.textSecondary); return@NovaPopupCard }
        val a = det.artist
        // Statut en grand
        Text(status?.let { "${it.emoji} ${it.label.uppercase()}" } ?: "EN ROUTE VERS LE PANTHÉON", color = if (mythic) Color.White else color, fontWeight = FontWeight.Black, fontSize = 26.sp, textAlign = TextAlign.Center, letterSpacing = 2.sp, modifier = Modifier.fillMaxWidth())
        Text("${formatCount(a.playCount)} écoutes · ${formatDuration(a.totalDurationMs)}", color = theme.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        // Progression vers le statut suivant
        val summary = summaryOf(det.certs)
        nextStatusProgress(status, a.playCount, summary)?.let { p ->
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { p.fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = pantheonColor(p.next), trackColor = theme.background)
            Text(p.message, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
        }

        PopupSection("📜 Historique des statuts", color)
        if (det.history.isEmpty()) Text("Aucun statut atteint pour l'instant.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
        det.history.forEach { h ->
            val st = PantheonStatus.fromDb(h.status)
            PopupInfoRow("${st?.emoji ?: "•"} ${st?.label ?: h.status}", "${formatDate(h.dateReached)} · ${formatCount(h.playCountAtStatus)} ▶", valueColor = st?.let { pantheonColor(it) } ?: theme.text)
        }

        val certTracks = det.certs.filter { it.entityType == EntityType.TRACK }.sortedByDescending { certRank(it) }
        val certAlbums = det.certs.filter { it.entityType == EntityType.ALBUM }.sortedByDescending { certRank(it) }
        PopupSection("🎵 Chansons certifiées (${certTracks.size})", color)
        if (certTracks.isEmpty()) Text("Aucune chanson certifiée.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
        certTracks.forEach { c -> CertLine(c) }
        PopupSection("💿 Albums certifiés (${certAlbums.size})", color)
        if (certAlbums.isEmpty()) Text("Aucun album certifié.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
        certAlbums.forEach { c -> CertLine(c) }

        PopupSection("📈 Courbe d'écoutes (cumul)", color)
        val cumul = remember(det.series) { var acc = 0; det.series.map { acc += it.playCount; acc.toFloat() } }
        val thresholds = PantheonStatus.entries.filter { it.playsThreshold <= (a.playCount * 1.6f) || it == (status?.let { s -> PantheonStatus.entries.getOrNull(s.ordinal + 1) } ?: PantheonStatus.STAR) }
            .map { it.playsThreshold.toFloat() to pantheonColor(it) }
        if (cumul.isEmpty()) Text("Pas encore de données.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
        else NovaCurveChart(cumul, Modifier.fillMaxWidth().height(130.dp), color = if (mythic) HoloColors[3] else color, thresholds = thresholds)

        PopupSection("📊 Positions Stats", color)
        PositionsTable(det.statsRanks)
        PopupSection("🏆 Positions Billboard (dernier classement)", color)
        PositionsTable(det.billboardRanks)
        Spacer(Modifier.height(4.dp))
        Text("Première écoute : ${formatDate(a.firstPlayedAt)}", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
    }
}

private fun certRank(c: ArtistCertRow): Int = (CertLevel.entries.firstOrNull { it.dbName == c.level }?.ordinal ?: -1) * 100 + c.multiplier

@Composable
private fun CertLine(c: ArtistCertRow) {
    val lvl = CertLevel.entries.firstOrNull { it.dbName == c.level }
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(c.name ?: "—", color = Nova.theme.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 8.dp))
        CertBadge(lvl, c.multiplier)
    }
}

/* ================================ 7. 🏛️ HALL OF FAME ================================ */

private val HofDirectDebut = Color(0xFF7B2FBE)
private val HofGlobal = Color(0xFF0D1BFF)

fun hofBadgeLabel(type: String) = when (type) {
    HallOfFameRules.DIRECT_DEBUT -> "🚀 DIRECT DEBUT"; HallOfFameRules.LONG_RUN -> "👑 LONG RUN"
    HallOfFameRules.TRIPLE_DEBUT -> "🌍 TRIPLE DEBUT"; HallOfFameRules.LEGENDARY_RUN -> "🏅 LEGENDARY RUN"; else -> type
}
fun hofBadgeColor(type: String): Color = when (type) {
    HallOfFameRules.DIRECT_DEBUT -> HofDirectDebut; HallOfFameRules.LONG_RUN -> NovaColors.Gold; else -> HofGlobal
}
fun hofBadgePrestige(type: String) = when (type) {
    HallOfFameRules.LEGENDARY_RUN -> 4; HallOfFameRules.TRIPLE_DEBUT -> 3; HallOfFameRules.LONG_RUN -> 2; else -> 1
}

private data class HofDetail(
    val name: String, val subtitle: String?, val imageUrl: String?, val entries: List<HallOfFameEntity>,
    /** Courbe de toutes les positions hebdo (null = hors chart) + ancres + stats. */
    val positions: List<Int?>, val anchors: List<LocalDate>, val stats: ChartHistoryStats, val chartPeriod: Period,
    val cert: CertificationEntity?, val playCount: Int
)

@Composable
private fun HallOfFamePopup(entityId: Long, entityType: String, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as NovaStatsApp
    val db = app.database
    val theme = Nova.theme
    var d by remember { mutableStateOf<HofDetail?>(null) }
    LaunchedEffect(entityId, entityType) {
        val entries = db.hallOfFameDao().ofEntity(entityId, entityType)
        var name: String? = null; var subtitle: String? = null; var image: String? = null; var plays = 0
        when (entityType) {
            EntityType.ARTIST -> db.artistDao().getById(entityId)?.let { name = it.name; image = it.photoUrl; plays = it.playCount }
            EntityType.ALBUM -> db.albumDao().getById(entityId)?.let { name = it.title; subtitle = it.artistId?.let { id -> db.artistDao().getById(id)?.name } ?: TitleNormalizer.SHARED_ALBUM_LABEL; image = it.coverUrl; plays = it.playCount }
            else -> db.trackDao().getById(entityId)?.let { name = it.title; subtitle = db.artistDao().getById(it.artistId)?.name; image = it.coverUrl; plays = it.playCount }
        }
        // Historique Billboard complet — la période du chart = celle de l'entrée la plus prestigieuse (Global → hebdo)
        val mainPeriod = entries.maxByOrNull { hofBadgePrestige(it.entryType) }?.periodType?.let { pt -> Period.entries.firstOrNull { it.dbName == pt } } ?: Period.WEEKLY
        val chartPeriod = if (mainPeriod == Period.GLOBAL || mainPeriod == Period.DAILY) Period.WEEKLY else mainPeriod
        val dao = db.billboardDao()
        val rows = when (entityType) {
            EntityType.ARTIST -> dao.artistHistory(chartPeriod.dbName, entityId)
            EntityType.ALBUM -> dao.albumHistory(chartPeriod.dbName, entityId)
            else -> dao.trackHistory(chartPeriod.dbName, entityId)
        }
        val appearances = rows.map { ChartAppearance(Dates.parse(it.date), it.position, it.playCount) }
        val anchors = if (appearances.isEmpty()) emptyList() else BillboardDates.allAnchors(chartPeriod, appearances.minOf { it.date }, Dates.today())
        val byDate = appearances.associateBy { it.date }
        d = HofDetail(
            name ?: "Inconnu", subtitle, image, entries,
            anchors.map { byDate[it]?.position }, anchors, ChartHistory.stats(appearances, anchors), chartPeriod,
            db.certificationDao().current(entityId, entityType), plays
        )
    }
    val det = d
    val main = det?.entries?.maxByOrNull { hofBadgePrestige(it.entryType) }?.entryType ?: HallOfFameRules.DIRECT_DEBUT
    val color = hofBadgeColor(main)
    val isGlobal = main == HallOfFameRules.TRIPLE_DEBUT || main == HallOfFameRules.LEGENDARY_RUN

    NovaPopupCard(
        borderColor = color, onDismiss = onDismiss, glowDp = if (isGlobal) 28 else 14, backdropUrl = det?.imageUrl,
        banner = {
            // Style cérémonie : pochette / photo grand format sur fond flou
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                BlurredBackdrop(det?.imageUrl, color, Modifier.fillMaxSize())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (isGlobal) "✦ ✧ ✦   G L O B A L   ✦ ✧ ✦" else "🏛️  H A L L   O F   F A M E", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp)
                    Spacer(Modifier.height(8.dp))
                    val circle = entityType == EntityType.ARTIST
                    Box(Modifier.size(110.dp).shadow(22.dp, RoundedCornerShape(if (circle) 50 else 12), ambientColor = color, spotColor = color).border(3.dp, color, RoundedCornerShape(if (circle) 50 else 12)).clip(RoundedCornerShape(if (circle) 50 else 12))) {
                        CoverArt(det?.imageUrl, det?.name ?: "?", size = 110, circle = circle, zoomable = true)
                    }
                }
            }
        }
    ) {
        if (det == null) { Text("Chargement…", color = theme.textSecondary); return@NovaPopupCard }
        Text(det.name, color = theme.text, fontWeight = FontWeight.Black, fontSize = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        det.subtitle?.let { Text(it, color = theme.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        Spacer(Modifier.height(8.dp))
        // Tous les badges côte à côte
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
            det.entries.map { it.entryType }.distinct().sortedByDescending { hofBadgePrestige(it) }.forEach { b ->
                Text(hofBadgeLabel(b), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(hofBadgeColor(b).copy(alpha = 0.85f)).padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
        val s = det.stats
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            PopupBadge("⭐", s.peak?.let { "#${it.position}" } ?: "—", "Peak", color)
            PopupBadge("📅", "${s.periodsInChart}", BillboardDates.unitLabel(det.chartPeriod, s.periodsInChart).replaceFirstChar { it.uppercase() }, color)
            PopupBadge("👑", "${s.longestRunAt1}", "Série #1", color)
            PopupBadge("🔟", "${s.longestRunTop10}", "Série Top 10", color)
        }

        PopupSection("📈 Montée au sommet & règne (${det.chartPeriod.label})", color)
        if (det.positions.isEmpty()) Text("Pas d'historique Billboard disponible.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
        else {
            val maxPos = (det.positions.filterNotNull().maxOrNull() ?: 10).coerceAtLeast(5).toFloat()
            val peakIdx = s.peak?.let { pk -> det.anchors.indexOf(pk.date).takeIf { it >= 0 } }
            NovaCurveChart(det.positions.map { it?.toFloat() }, Modifier.fillMaxWidth().height(170.dp), color = color, invertY = true, minY = 1f, maxY = maxPos,
                gridValues = listOf(1f, ((maxPos + 1) / 2).toInt().toFloat(), maxPos), peakIndex = peakIdx, showPoints = true)
        }

        PopupSection("🎊 Historique complet de l'entrée", color)
        det.entries.sortedBy { it.entryDate }.forEach { e ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("${hofBadgeLabel(e.entryType)} · ${e.periodType.lowercase().replaceFirstChar { it.uppercase() }}", color = hofBadgeColor(e.entryType), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Text("Consacré le ${formatIso(e.entryDate)}", color = theme.text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                val reign = e.reignStart?.let { rs -> "du ${formatIso(rs)} au ${formatIso(e.reignEnd ?: rs)}" }
                if (e.weeksAt1 > 0 || reign != null) Text("👑 ${if (e.weeksAt1 > 0) "${e.weeksAt1} semaine${if (e.weeksAt1 > 1) "s" else ""} au #1" else "Règne"}${reign?.let { " · $it" } ?: ""}", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                Text("▶ ${formatCount(e.playCountAtEntry)} écoutes au moment de l'entrée", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }

        PopupSection("📊 Aujourd'hui", color)
        PopupInfoRow("Écoutes actuelles", "${formatCount(det.playCount)} ▶")
        PopupInfoRow("Première entrée", s.firstEntry?.let { "#${it.position} · ${it.date.format(ceremonyFmt)}" } ?: "—")
        PopupInfoRow("Peak", s.peak?.let { "#${it.position} · ${it.date.format(ceremonyFmt)}" } ?: "—")
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Certification actuelle", color = theme.textSecondary, style = MaterialTheme.typography.bodyMedium)
            CertBadge(det.cert?.let { c -> CertLevel.entries.firstOrNull { it.dbName == c.level } }, det.cert?.multiplier ?: 1)
        }
    }
}

/* ============================ 8. 📈 Parcours & ⏱ Prévisions ============================ */

private fun chartLabel(type: String): String = when (type) {
    EntityType.TRACK -> "Hot 100"
    EntityType.ARTIST -> "Artist 50"
    else -> "Albums 75"
}

/**
 * Parcours hebdomadaire dans un chart (Billboard) : meilleur rang, semaines classées, série n°1,
 * puis les 14 dernières semaines en bandeau horizontal avec la variation ▲ / ▼.
 */
@Composable
private fun ChartRunSection(type: String, id: Long, color: Color) {
    val app = LocalContext.current.applicationContext as NovaStatsApp
    val db = app.database
    val theme = Nova.theme
    val rows by produceState<List<PriorRow>?>(initialValue = null, type, id) {
        value = runCatching {
            when (type) {
                EntityType.TRACK -> db.billboardDao().trackHistory(Period.WEEKLY.dbName, id)
                EntityType.ARTIST -> db.billboardDao().artistHistory(Period.WEEKLY.dbName, id)
                else -> db.billboardDao().albumHistory(Period.WEEKLY.dbName, id)
            }
        }.getOrNull()
    }
    val r = rows?.takeIf { it.isNotEmpty() } ?: return
    val appearances = r.mapNotNull { row -> runCatching { ChartAppearance(Dates.parse(row.date), row.position, row.playCount) }.getOrNull() }
    if (appearances.isEmpty()) return
    val stats = com.novastats.app.domain.ChartHistory.stats(appearances)
    val peak = stats.peak ?: return

    PopupSection("📈 Parcours ${chartLabel(type)} (hebdo)", color)
    GlassCard(glow = color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        StatPill("#${peak.position}", "meilleur rang", accent = color)
        StatPill("${stats.periodsInChart}", "semaines classé", accent = theme.secondary)
        StatPill("${stats.longestRunAt1}", if (stats.longestRunAt1 > 1) "semaines n°1" else "semaine n°1", accent = theme.accent)
    }
    Spacer(Modifier.height(6.dp))
    Text(
        "Entrée le ${formatIso(appearances.first().date.format(Dates.ISO))} · pic le ${formatIso(peak.date.format(Dates.ISO))}",
        color = theme.textSecondary, style = MaterialTheme.typography.labelSmall
    )
    Spacer(Modifier.height(8.dp))
    val tail = r.takeLast(14)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        tail.forEachIndexed { i, row ->
            val prev = if (i > 0) tail[i - 1].position else null
            val delta = prev?.let { it - row.position } // > 0 : gagne des places
            val dColor = when { delta == null -> theme.textSecondary; delta > 0 -> theme.accent; delta < 0 -> NovaColors.Gold; else -> theme.textSecondary }
            Column(
                Modifier.padding(end = 8.dp).width(58.dp).clip(RoundedCornerShape(10.dp))
                    .background(theme.surface.copy(alpha = 0.85f)).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("#${row.position}", color = theme.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    runCatching { val dt = Dates.parse(row.date); "${dt.dayOfMonth}/${String.format(Locale.FRANCE, "%02d", dt.monthValue)}" }.getOrDefault("—"),
                    color = theme.textSecondary, fontSize = 11.sp
                )
                Text(
                    when { delta == null -> "·"; delta > 0 -> "▲$delta"; delta < 0 -> "▼${-delta}"; else -> "=" },
                    color = dColor, fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
    }
}

/**
 * Prévision du prochain palier : écoutes restantes, barre de progression et estimation en jours
 * calculée sur le rythme des 14 derniers jours (aucune estimation si le rythme est nul).
 */
@Composable
private fun ForecastSection(
    current: Int, target: Int, targetLabel: String, series: List<DayCount>, color: Color, emoji: String = "⏱"
) {
    if (target <= current) return
    val theme = Nova.theme
    val remaining = target - current
    val from = Dates.today().minusDays(13).format(Dates.ISO)
    val recent = series.filter { it.date >= from }
    val rate = if (recent.isEmpty()) 0f else recent.sumOf { it.playCount }.toFloat() / 14f
    val progress = (current.toFloat() / target.toFloat()).coerceIn(0f, 1f)

    PopupSection("$emoji Vers $targetLabel", color)
    GlassCard(glow = color) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        color = color, trackColor = theme.background
    )
    Spacer(Modifier.height(6.dp))
    Text("${formatCount(remaining)} écoutes restantes (${formatCount(current)} / ${formatCount(target)})", color = theme.text, style = MaterialTheme.typography.bodyMedium)
    if (rate > 0.05f) {
        val days = kotlin.math.ceil(remaining.toDouble() / rate.toDouble()).toInt().coerceAtLeast(1)
        val eta = Dates.today().plusDays(days.toLong())
        Text(
            "≈ $days jour${if (days > 1) "s" else ""} au rythme des 14 derniers jours (${String.format(Locale.FRANCE, "%.1f", rate)}/jour) → vers le ${eta.dayOfMonth}/${String.format(Locale.FRANCE, "%02d", eta.monthValue)}",
            color = color, style = MaterialTheme.typography.bodySmall
        )
    } else {
        Text("Rythme récent trop faible pour une estimation.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
    }
    }
}
