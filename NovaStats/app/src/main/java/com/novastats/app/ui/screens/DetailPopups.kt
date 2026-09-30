package com.novastats.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.novastats.app.NovaStatsApp
import com.novastats.app.data.db.dao.RankedTrack
import com.novastats.app.data.db.entity.AlbumEntity
import com.novastats.app.data.db.entity.ArtistEntity
import com.novastats.app.data.db.entity.CertificationEntity
import com.novastats.app.data.db.entity.TrackEntity
import com.novastats.app.domain.CertLevel
import com.novastats.app.domain.Certification
import com.novastats.app.domain.Dates
import com.novastats.app.domain.PantheonStatus
import com.novastats.app.data.db.entity.PantheonHistoryEntity
import com.novastats.app.data.db.entity.HallOfFameEntity
import com.novastats.app.domain.Period
import com.novastats.app.ui.theme.Nova
import com.novastats.app.ui.theme.NovaColors
import java.text.SimpleDateFormat
import java.util.Locale

/*
 * Popups de détail (appui long dans Stats, clic dans l'Accueil) — cahier des charges :
 * overlay noir 85 %, carte 92 % de largeur, coins 16 dp, fermeture par clic extérieur OU bouton FERMER,
 * scroll interne, animation fade. Bordures : chanson = primary, artiste = glowSecondary, album = secondary.
 */

/** Cible d'un popup de détail. */
sealed interface DetailTarget {
    data class Track(val id: Long) : DetailTarget
    data class Artist(val id: Long) : DetailTarget
    data class Album(val id: Long) : DetailTarget
}

private val dateFmt = SimpleDateFormat("d MMM yyyy", Locale.FRANCE)
fun formatDate(ms: Long?): String = ms?.let { dateFmt.format(it) } ?: "—"

fun certificationLabel(c: CertificationEntity?): String? {
    c ?: return null
    val level = CertLevel.entries.firstOrNull { it.dbName == c.level } ?: return null
    return Certification(level, c.multiplier).label()
}

@Composable
fun DetailPopupHost(target: DetailTarget?, onDismiss: () -> Unit) {
    if (target == null) return
    when (target) {
        is DetailTarget.Track -> TrackPopup(target.id, onDismiss)
        is DetailTarget.Artist -> ArtistPopup(target.id, onDismiss)
        is DetailTarget.Album -> AlbumPopup(target.id, onDismiss)
    }
}

/** Squelette commun : overlay, carte, bandeau, scroll interne, bouton FERMER, fade. */
@Composable
internal fun PopupScaffold(
    borderColor: Color,
    onDismiss: () -> Unit,
    heightFraction: Float = 0.86f,
    fixedHeight: Boolean = false,
    banner: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val theme = Nova.theme
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)) {
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(160))) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                val maxH = (LocalConfiguration.current.screenHeightDp * heightFraction).dp
                Column(
                    (if (fixedHeight) Modifier.fillMaxWidth(0.92f).height(maxH) else Modifier.fillMaxWidth(0.92f).heightIn(max = maxH))
                        .shadow(24.dp, RoundedCornerShape(16.dp), ambientColor = borderColor, spotColor = borderColor)
                        .clip(RoundedCornerShape(16.dp))
                        .background(theme.surface)
                        .border(2.dp, borderColor, RoundedCornerShape(16.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                ) {
                    banner()
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) { content() }
                    HorizontalDivider(color = borderColor.copy(alpha = 0.3f))
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("FERMER", color = borderColor, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color = Nova.theme.text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Nova.theme.textSecondary, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = valueColor, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
private fun PopupSectionTitle(text: String) {
    Text(text, color = Nova.theme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

/** Tableau des positions par période : Daily · Weekly · Monthly · Yearly · Global. */
@Composable
private fun PositionsTable(ranks: Map<Period, Int?>) {
    val theme = Nova.theme
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Period.entries.forEach { p ->
            val r = ranks[p]
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(p.label, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                Text(
                    positionLabel(r), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium,
                    color = when (r) { null -> theme.textSecondary.copy(alpha = 0.6f); 1 -> NovaColors.Gold; 2 -> NovaColors.Silver; 3 -> Color(0xFFCD7F32); else -> theme.text }
                )
            }
        }
    }
}

private fun blurredBg(): Modifier = Modifier.fillMaxSize().blur(24.dp).alpha(0.55f)

/* ================================ CHANSON ================================ */

@Composable
private fun TrackPopup(trackId: Long, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as NovaStatsApp
    val db = app.database
    val theme = Nova.theme
    var track by remember { mutableStateOf<TrackEntity?>(null) }
    var artists by remember { mutableStateOf("") }
    var album by remember { mutableStateOf<AlbumEntity?>(null) }
    var ranks by remember { mutableStateOf<Map<Period, Int?>>(emptyMap()) }
    val cert by remember { db.certificationDao().observe(trackId, "TRACK") }.collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(trackId) {
        val t = db.trackDao().getById(trackId) ?: return@LaunchedEffect
        track = t
        val ids = db.trackLinkDao().artistIdsForTrack(trackId).ifEmpty { listOf(t.artistId) }
        artists = ids.mapNotNull { db.artistDao().getById(it)?.name }.joinToString(", ")
        album = t.albumId?.let { db.albumDao().getById(it) }
        ranks = Period.entries.associateWith { p ->
            if (p == Period.GLOBAL) db.trackDao().rankAllTime(trackId)
            else Dates.statsRangeFor(p).let { r -> db.trackDao().rankForPeriod(trackId, r.fromIso, r.toIso) }
        }
    }
    val t = track
    PopupScaffold(
        borderColor = theme.primary, onDismiss = onDismiss,
        banner = {
            Box(Modifier.fillMaxWidth().height(180.dp).background(Brush.verticalGradient(listOf(theme.primary.copy(alpha = 0.35f), theme.surface))), contentAlignment = Alignment.Center) {
                if (t?.coverUrl != null) AsyncImage(model = t.coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = blurredBg())
                Box(Modifier.size(132.dp).shadow(16.dp, RoundedCornerShape(12.dp), spotColor = theme.primary).border(2.dp, theme.primary, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))) {
                    CoverArt(t?.coverUrl, t?.title ?: "?", size = 132)
                }
            }
        }
    ) {
        if (t == null) { Text("Chargement…", color = theme.textSecondary); return@PopupScaffold }
        Text(t.title, color = theme.text, fontWeight = FontWeight.Black, fontSize = 20.sp)
        Text(artists, color = theme.primary, fontWeight = FontWeight.SemiBold)
        album?.let { Text(it.title, color = theme.textSecondary, style = MaterialTheme.typography.bodyMedium) }
        if (t.isRemix) Text("Version featuring — liée au titre original", color = theme.accent, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            StatPill(formatCount(t.playCount), "écoutes")
            StatPill(formatDuration(t.totalDurationMs), "temps", accent = theme.secondary)
        }
        PopupSectionTitle("🏅 Certification")
        val label = certificationLabel(cert)
        if (label != null) InfoRow(label, "obtenue le ${formatDate(cert?.certifiedAt)}", valueColor = NovaColors.Gold)
        else {
            val next = com.novastats.app.domain.CertificationRules.TRACK.next(t.playCount)
            val need = com.novastats.app.domain.CertificationRules.TRACK.required(next)
            InfoRow("Aucune", "${next.label()} dans ${(need - t.playCount).coerceAtLeast(0)} écoutes")
        }
        PopupSectionTitle("📊 Positions actuelles")
        PositionsTable(ranks)
        PopupSectionTitle("📅 Historique")
        InfoRow("Première écoute", formatDate(t.firstPlayedAt))
        InfoRow("Dernière écoute", formatDate(t.lastPlayedAt))
        InfoRow("Streak", "${t.currentStreak} j (record ${t.bestStreak} j)")
        InfoRow("Rang de découverte", t.discoveryRank?.let { "#$it" } ?: "—")
    }
}

/* ================================ ARTISTE ================================ */

@Composable
private fun ArtistPopup(artistId: Long, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as NovaStatsApp
    val db = app.database
    val theme = Nova.theme
    var artist by remember { mutableStateOf<ArtistEntity?>(null) }
    var topTracks by remember { mutableStateOf<List<RankedTrack>>(emptyList()) }
    var albums by remember { mutableStateOf<List<AlbumEntity>>(emptyList()) }
    var totalAllTime by remember { mutableStateOf(0) }
    var periodPlays by remember { mutableStateOf(0) }
    var periodTotal by remember { mutableStateOf(0) }
    var ranks by remember { mutableStateOf<Map<Period, Int?>>(emptyMap()) }

    var history by remember { mutableStateOf<List<PantheonHistoryEntity>>(emptyList()) }
    var hof by remember { mutableStateOf<List<HallOfFameEntity>>(emptyList()) }
    LaunchedEffect(artistId) {
        artist = db.artistDao().getById(artistId)
        history = db.pantheonDao().history(artistId)
        hof = db.hallOfFameDao().ofEntity(artistId, "ARTIST")
        topTracks = db.trackDao().topOfArtist(artistId, 5)
        albums = db.albumDao().ofArtist(artistId)
        totalAllTime = db.scrobbleDao().countConfirmed()
        val week = Dates.statsRangeFor(Period.WEEKLY)
        periodPlays = db.artistDao().playsForPeriod(artistId, week.fromIso, week.toIso)
        periodTotal = db.dailyPlayDao().playsBetween(week.fromIso, week.toIso)
        ranks = Period.entries.associateWith { p ->
            val r = Dates.statsRangeFor(p)
            db.artistDao().rankForPeriod(artistId, r.fromIso, r.toIso)
        }
    }
    val a = artist
    val status = PantheonStatus.fromDb(a?.pantheonStatus)
    val statusColor = status?.let { runCatching { Color(android.graphics.Color.parseColor(it.colorHex)) }.getOrNull() } ?: theme.glowSecondary
    PopupScaffold(
        borderColor = theme.glowSecondary, onDismiss = onDismiss,
        banner = {
            Box(Modifier.fillMaxWidth().height(170.dp).background(Brush.verticalGradient(listOf(theme.glowSecondary.copy(alpha = 0.4f), theme.surface))), contentAlignment = Alignment.Center) {
                if (a?.photoUrl != null) AsyncImage(model = a.photoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = blurredBg())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(90.dp).shadow(20.dp, RoundedCornerShape(50), ambientColor = theme.glowSecondary, spotColor = theme.glowSecondary).border(3.dp, theme.glowSecondary, RoundedCornerShape(50)).clip(RoundedCornerShape(50))) {
                        CoverArt(a?.photoUrl, a?.name ?: "?", size = 90, circle = true)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text((a?.name ?: "").uppercase(), color = theme.text, fontWeight = FontWeight.Black, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, letterSpacing = 1.sp)
                }
            }
        }
    ) {
        if (a == null) { Text("Chargement…", color = theme.textSecondary); return@PopupScaffold }
        Text(status?.let { "${it.emoji} ${it.label.uppercase()}" } ?: "Pas encore au Panthéon (Star à 425 écoutes)", color = statusColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            StatPill(formatCount(a.playCount), "écoutes")
            StatPill(formatDuration(a.totalDurationMs), "temps", accent = theme.secondary)
            StatPill("${a.distinctTracks}", "titres", accent = theme.accent)
            StatPill("${a.distinctAlbums}", "albums", accent = theme.glowSecondary)
        }
        if (history.isNotEmpty()) {
            PopupSectionTitle("👑 Parcours au Panthéon")
            history.forEach { h ->
                val st = PantheonStatus.fromDb(h.status)
                InfoRow("${st?.emoji ?: "•"} ${st?.label ?: h.status}", "${formatDate(h.dateReached)} · ${formatCount(h.playCountAtStatus)} ▶")
            }
        }
        if (hof.isNotEmpty()) {
            PopupSectionTitle("🏛️ Hall of Fame")
            hof.forEach { e -> InfoRow("${e.entryType.replace('_', ' ')} · ${e.periodType.lowercase().replaceFirstChar { it.uppercase() }}", formatDate(java.time.LocalDate.parse(e.entryDate).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())) }
        }
        PopupSectionTitle("📊 Positions actuelles")
        PositionsTable(ranks)
        PopupSectionTitle("🎵 Top chansons")
        if (topTracks.isEmpty()) Text("—", color = theme.textSecondary)
        topTracks.forEachIndexed { i, t -> InfoRow("${i + 1}. ${t.track.title}", "${formatCount(t.periodPlays)} ▶") }
        PopupSectionTitle("💿 Albums dans la bibliothèque")
        if (albums.isEmpty()) Text("Aucun album crédité (titres en featuring uniquement)", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
        albums.forEach { al -> InfoRow(al.title, "${formatCount(al.playCount)} ▶") }
        PopupSectionTitle("📅 Historique & parts d'écoute")
        InfoRow("Première écoute", formatDate(a.firstPlayedAt))
        InfoRow("Part All Time", if (totalAllTime > 0) String.format(Locale.FRANCE, "%.1f %%", 100.0 * a.playCount / totalAllTime) else "—")
        InfoRow("Part 7 derniers jours", if (periodTotal > 0) String.format(Locale.FRANCE, "%.1f %%", 100.0 * periodPlays / periodTotal) else "—")
    }
}

/* ================================ ALBUM ================================ */

@Composable
private fun AlbumPopup(albumId: Long, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as NovaStatsApp
    val db = app.database
    val theme = Nova.theme
    var album by remember { mutableStateOf<AlbumEntity?>(null) }
    var artistName by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf<List<RankedTrack>>(emptyList()) }
    var ranks by remember { mutableStateOf<Map<Period, Int?>>(emptyMap()) }
    val cert by remember { db.certificationDao().observe(albumId, "ALBUM") }.collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(albumId) {
        val al = db.albumDao().getById(albumId) ?: return@LaunchedEffect
        album = al
        artistName = db.artistDao().getById(al.artistId)?.name ?: ""
        tracks = db.trackDao().ofAlbum(albumId)
        ranks = Period.entries.associateWith { p ->
            val r = Dates.statsRangeFor(p)
            db.albumDao().rankForPeriod(albumId, r.fromIso, r.toIso)
        }
    }
    val al = album
    PopupScaffold(
        borderColor = theme.secondary, onDismiss = onDismiss,
        banner = {
            Box(Modifier.fillMaxWidth().height(170.dp).background(Brush.verticalGradient(listOf(theme.secondary.copy(alpha = 0.35f), theme.surface))), contentAlignment = Alignment.Center) {
                if (al?.coverUrl != null) AsyncImage(model = al.coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = blurredBg())
                Box(Modifier.size(120.dp).shadow(16.dp, RoundedCornerShape(12.dp), spotColor = theme.secondary).border(2.dp, theme.secondary, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))) {
                    CoverArt(al?.coverUrl, al?.title ?: "?", size = 120)
                }
            }
        }
    ) {
        if (al == null) { Text("Chargement…", color = theme.textSecondary); return@PopupScaffold }
        Text(al.title, color = theme.text, fontWeight = FontWeight.Black, fontSize = 20.sp)
        Text(artistName, color = theme.secondary, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            StatPill(formatCount(al.playCount), "écoutes")
            StatPill(formatDuration(al.totalDurationMs), "temps", accent = theme.secondary)
            StatPill("${al.distinctTracksPlayed}", "titres écoutés", accent = theme.accent)
        }
        PopupSectionTitle("🏅 Certification")
        val label = certificationLabel(cert)
        if (label != null) InfoRow(label, "obtenue le ${formatDate(cert?.certifiedAt)}", valueColor = NovaColors.Gold)
        else {
            val next = com.novastats.app.domain.CertificationRules.ALBUM.next(al.playCount)
            val need = com.novastats.app.domain.CertificationRules.ALBUM.required(next)
            InfoRow("Aucune", "${next.label()} dans ${(need - al.playCount).coerceAtLeast(0)} écoutes")
        }
        PopupSectionTitle("📊 Positions actuelles")
        PositionsTable(ranks)
        PopupSectionTitle("🎵 Titres (${tracks.size})")
        tracks.forEachIndexed { i, t -> InfoRow("${i + 1}. ${t.track.title}", "${formatCount(t.periodPlays)} ▶") }
        PopupSectionTitle("📅 Historique")
        InfoRow("Première écoute", formatDate(al.firstPlayedAt))
        InfoRow("Dernière écoute", formatDate(al.lastPlayedAt))
    }
}
