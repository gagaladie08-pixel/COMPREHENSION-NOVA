package com.novastats.app.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.novastats.app.ui.theme.prideFlagFor
import com.novastats.app.ui.theme.isPride
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novastats.app.NovaStatsApp
import com.novastats.app.data.db.dao.PeriodSummary
import com.novastats.app.domain.Dates
import com.novastats.app.domain.PantheonStatus
import com.novastats.app.domain.Period
import com.novastats.app.domain.TitleNormalizer
import androidx.compose.runtime.produceState
import kotlinx.coroutines.flow.first
import com.novastats.app.ui.theme.Nova

private val statsTabs = listOf("Titres", "Artistes", "Albums")
private const val TOP_LIMIT = 300

/**
 * 📊 Stats — 3 sous-onglets Titres / Artistes / Albums + icône recherche (temps réel, portée = sous-onglet actif).
 * Sélecteur de période commun : Daily = aujourd'hui · Weekly = 7 derniers jours (glissant) · Monthly = mois
 * calendaire · Yearly = année calendaire · Global. Top 300, tri écoutes puis temps cumulé.
 * Positions : 🥇🥈🥉 #1-3 · 🔥 #4-#10 · "#11" · "—" non classé · "▼ 300+".
 * Appui long sur une ligne → popup de détail (chanson / artiste / album) **sur la période choisie**.
 *
 * Mise en page : périodes + bandeau défilent avec la liste ; la rangée Titres / Artistes / Albums reste collée en haut
 * (stickyHeader) dès qu'on fait défiler → elle vient se coller sous les onglets principaux.
 * Classement : Top 25 affiché, bouton « Voir plus » = +20 à chaque appui (jusqu'à 300).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StatsScreen() {
    val app = LocalContext.current.applicationContext as NovaStatsApp
    val db = app.database
    val theme = Nova.theme

    var tab by remember { mutableIntStateOf(0) }
    var period by remember { mutableStateOf(Period.WEEKLY) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<DetailTarget?>(null) }
    var versus by remember { mutableStateOf(false) }
    val range = remember(period) { Dates.statsRangeFor(period) }

    val summary by remember(range) { db.scrobbleDao().summary(range.fromMs, range.toMs) }
        .collectAsStateWithLifecycle(initialValue = PeriodSummary(0L, 0L, 0, 0, 0, 0))

    val tracks by remember(period) {
        if (period == Period.GLOBAL) db.trackDao().topAllTime(TOP_LIMIT) else db.trackDao().topForPeriod(range.fromIso, range.toIso, TOP_LIMIT)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val artists by remember(period) {
        if (period == Period.GLOBAL) db.artistDao().topAllTime(TOP_LIMIT) else db.artistDao().topForPeriod(range.fromIso, range.toIso, TOP_LIMIT)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val albums by remember(period) {
        if (period == Period.GLOBAL) db.albumDao().topAllTime(TOP_LIMIT) else db.albumDao().topForPeriod(range.fromIso, range.toIso, TOP_LIMIT)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    // 📈 Premium : la période précédente (même longueur, juste avant) sert de référence —
    // variation d'écoutes du bandeau et mouvements de classement (▲ / ▼ / nouveau).
    // Global n'a pas d'« avant » : tout y est désactivé.
    val prevRange = remember(period) {
        if (period == Period.GLOBAL) null else {
            val len = java.time.temporal.ChronoUnit.DAYS.between(range.from, range.to) + 1
            com.novastats.app.domain.DateRange(range.from.minusDays(len), range.from.minusDays(1))
        }
    }
    val prevSummary by remember(prevRange) {
        val r = prevRange
        if (r == null) kotlinx.coroutines.flow.flowOf(PeriodSummary(0L, 0L, 0, 0, 0, 0))
        else db.scrobbleDao().summary(r.fromMs, r.toMs)
    }.collectAsStateWithLifecycle(initialValue = PeriodSummary(0L, 0L, 0, 0, 0, 0))
    val prevTracks by remember(prevRange) {
        val r = prevRange
        if (r == null) kotlinx.coroutines.flow.flowOf(emptyList()) else db.trackDao().topForPeriod(r.fromIso, r.toIso, TOP_LIMIT)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val prevArtists by remember(prevRange) {
        val r = prevRange
        if (r == null) kotlinx.coroutines.flow.flowOf(emptyList()) else db.artistDao().topForPeriod(r.fromIso, r.toIso, TOP_LIMIT)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val prevAlbums by remember(prevRange) {
        val r = prevRange
        if (r == null) kotlinx.coroutines.flow.flowOf(emptyList()) else db.albumDao().topForPeriod(r.fromIso, r.toIso, TOP_LIMIT)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val prevTrackPos = remember(prevTracks) { prevTracks.mapIndexed { i, t -> t.track.trackId to (i + 1) }.toMap() }
    val prevArtistPos = remember(prevArtists) { prevArtists.mapIndexed { i, a -> a.artist.artistId to (i + 1) }.toMap() }
    val prevAlbumPos = remember(prevAlbums) { prevAlbums.mapIndexed { i, al -> al.album.albumId to (i + 1) }.toMap() }

    val q = TitleNormalizer.normalizeKey(query)
    fun matches(vararg fields: String?) = q.isBlank() || fields.any { it != null && TitleNormalizer.normalizeKey(it).contains(q) }

    DetailPopupHost(detail) { detail = null }

    // ⚔️ Comparer deux titres / artistes (écran plein, retour = Stats)
    if (versus) {
        BackHandler { versus = false }
        VersusScreen { versus = false }
        return
    }

    val filteredTracks = remember(tracks, q) { tracks.mapIndexed { i, t -> i + 1 to t }.filter { (_, t) -> matches(t.track.title, t.artistName, t.albumTitle) } }
    val filteredArtists = remember(artists, q) { artists.mapIndexed { i, a -> i + 1 to a }.filter { (_, a) -> matches(a.artist.name) } }
    val filteredAlbums = remember(albums, q) { albums.mapIndexed { i, al -> i + 1 to al }.filter { (_, al) -> matches(al.album.title, al.artistName) } }

    val total = when (tab) { 0 -> filteredTracks.size; 1 -> filteredArtists.size; else -> filteredAlbums.size }
    // Le podium met en avant le top 3 : la liste démarre alors à la 4ᵉ place (pas de doublon).
    val podiumOn = q.isBlank() && total >= 3
    val listTotal = if (podiumOn) total - 3 else total
    // Top 25 → « Voir plus » (+20) ; remis à 25 à chaque changement de sous-onglet / période / recherche
    var visible by remember(tab, period, q) { mutableIntStateOf(TOP_INITIAL) }
    val shown = minOf(visible, listTotal)

    val listState = rememberLazyListState()
    // Changement de période : on remonte en haut pour revoir le bandeau
    LaunchedEffect(period) { listState.scrollToItem(0) }

    // 🎨 Photo de ton artiste n°1 sur la période, en fond d'écran
    val artUrl by produceState<String?>(initialValue = null, period) {
        val r = Dates.statsRangeFor(period)
        value = runCatching { db.artistDao().topForPeriod(r.fromIso, r.toIso, 1).first().firstOrNull()?.artist?.photoUrl }.getOrNull()
    }

    ScreenBackdrop(artUrl) {
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        /* ---------- En-tête défilant : périodes → bandeau → légende ---------- */
        item(key = "header") {
            Column(Modifier.fillMaxWidth()) {
                PeriodSegment(period) { period = it }
                val days = if (period == Period.GLOBAL) summary.activeDays else elapsedDays(range)
                val avg = if (days > 0) summary.playCount.toFloat() / days else 0f
                SummaryStrip(
                    listOf(
                        StripCell(formatCount(summary.playCount), "Écoutes"),
                        StripCell(formatDuration(summary.totalDurationMs), "Temps"),
                        StripCell("${summary.distinctTracks}", "Titres"),
                        StripCell("${summary.distinctArtists}", "Artistes"),
                        StripCell(formatAverage(avg), "Moy/jour")
                    )
                )
                Text(
                    periodCaption(period, range) + if (period == Period.GLOBAL) " · Top $TOP_LIMIT" else "",
                    color = theme.textSecondary.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), textAlign = TextAlign.Center
                )
                // 📈 Variation factuelle vs la période précédente (même longueur, juste avant).
                if (prevRange != null && prevSummary.playCount > 0) {
                    val pct = ((summary.playCount - prevSummary.playCount).toFloat() / prevSummary.playCount * 100).toInt()
                    val up = summary.playCount >= prevSummary.playCount
                    Text(
                        "${if (up) "▲ +$pct %" else "▼ $pct %"} d'écoutes vs ${prevPeriodName(period)}",
                        color = if (up) Color(0xFF4CAF50) else Color(0xFFE74C3C),
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                    )
                }
                // 🎯 Dominance du n°1 : quand un titre écrase la période, on le dit.
                if (q.isBlank() && summary.playCount > 0) {
                    val t1 = tracks.firstOrNull()
                    val share = if (t1 != null) t1.periodPlays * 100 / summary.playCount else 0
                    if (t1 != null && share >= 10) Text(
                        "🎯 « ${t1.track.title} » pèse $share % de tes écoutes sur la période",
                        color = theme.textSecondary.copy(alpha = 0.9f), style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp), textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(listOf(theme.primary.copy(alpha = 0.30f), theme.secondary.copy(alpha = 0.22f), Color.Transparent)))
                        .clickable { versus = true }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚔️", fontSize = 20.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Comparer deux titres ou artistes", color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text("Face à face : écoutes, temps, régularité, certification…", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("›", color = theme.primary, fontSize = 22.sp)
                }
            }
        }

        /* ---------- Sous-onglets collants : Titres / Artistes / Albums + loupe (+ champ de recherche) ---------- */
        stickyHeader(key = "tabs") {
            Column(Modifier.fillMaxWidth().background(theme.background)) {
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    TabRow(
                        selectedTabIndex = tab,
                        modifier = Modifier.weight(1f),
                        containerColor = theme.background,
                        contentColor = theme.primary,
                        indicator = { positions ->
                            Box(Modifier.tabIndicatorOffset(positions[tab]).padding(horizontal = 28.dp).height(if (Nova.isPride) 5.dp else 3.dp).clip(RoundedCornerShape(2.dp)).background(if (Nova.isPride) Brush.horizontalGradient(prideFlagFor(tab + 1)) else SolidColor(theme.primary)))
                        },
                        divider = {}
                    ) {
                        statsTabs.forEachIndexed { i, label ->
                            Tab(
                                selected = tab == i, onClick = { tab = i },
                                text = { Text(label, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.titleSmall) },
                                selectedContentColor = theme.primary, unselectedContentColor = theme.textSecondary
                            )
                        }
                    }
                    Box(Modifier.padding(end = 4.dp)) {
                        IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                            Icon(if (searching) CloseIcon else SearchIcon, contentDescription = "Rechercher", tint = if (searching || query.isNotBlank()) theme.primary else theme.textSecondary)
                        }
                    }
                }
                if (searching) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        singleLine = true,
                        placeholder = { Text("Rechercher dans ${statsTabs[tab].lowercase()}…", color = theme.textSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.primary, unfocusedBorderColor = theme.textSecondary.copy(alpha = 0.4f),
                            focusedTextColor = theme.text, unfocusedTextColor = theme.text, cursorColor = theme.primary
                        )
                    )
                }
            }
        }

        /* ---------- Podium 🥇🥈🥉 (masqué pendant une recherche) ---------- */
        if (podiumOn) {
            item(key = "podium") {
                PodiumRow(
                    when (tab) {
                        0 -> filteredTracks.take(3).map { (pos, t) ->
                            PodiumItem(pos, t.track.title, t.artistName, t.periodPlays, t.track.coverUrl, false) { detail = DetailTarget.Track(t.track.trackId, period) }
                        }
                        1 -> filteredArtists.take(3).map { (pos, a) ->
                            PodiumItem(pos, a.artist.name, null, a.periodPlays, a.artist.photoUrl, true) { detail = DetailTarget.Artist(a.artist.artistId, period) }
                        }
                        else -> filteredAlbums.take(3).map { (pos, al) ->
                            PodiumItem(pos, al.album.title, al.artistName, al.periodPlays, al.album.coverUrl, false) { detail = DetailTarget.Album(al.album.albumId, period) }
                        }
                    }
                )
            }
        }

        /* ---------- Classement ---------- */
        if (total == 0) {
            item(key = "empty") {
                Box(Modifier.fillParentMaxHeight(0.6f)) {
                    if (q.isNotBlank()) EmptyState("🔍", "Aucun résultat pour « $query »", "dans le Top $TOP_LIMIT ${statsTabs[tab].lowercase()} de la période")
                    else EmptyState("📊", "Aucune écoute sur cette période", "Ton classement s'enrichit à chaque écoute")
                }
            }
        } else when (tab) {
            // Liste démarrée après le podium (drop 3) quand il est affiché ; les positions restent réelles.
            0 -> itemsIndexed((if (podiumOn) filteredTracks.drop(3) else filteredTracks).take(shown), key = { _, (_, t) -> "t" + t.track.trackId }) { _, (pos, t) ->
                val prev = prevTrackPos[t.track.trackId]
                RankRow(
                    pos, t.track.title, listOfNotNull(t.artistName, t.albumTitle).joinToString(" · "), t.periodPlays, t.periodDurationMs, t.track.coverUrl,
                    delta = prev?.let { it - pos }, isNew = prev == null && period != Period.GLOBAL,
                    onLongClick = { detail = DetailTarget.Track(t.track.trackId, period) }
                )
            }
            1 -> itemsIndexed((if (podiumOn) filteredArtists.drop(3) else filteredArtists).take(shown), key = { _, (_, a) -> "a" + a.artist.artistId }) { _, (pos, a) ->
                val sub = PantheonStatus.fromDb(a.artist.pantheonStatus)?.let { "${it.emoji} ${it.label.uppercase()}" }
                val prev = prevArtistPos[a.artist.artistId]
                RankRow(
                    pos, a.artist.name, sub, a.periodPlays, a.periodDurationMs, a.artist.photoUrl, circle = true,
                    delta = prev?.let { it - pos }, isNew = prev == null && period != Period.GLOBAL,
                    onLongClick = { detail = DetailTarget.Artist(a.artist.artistId, period) }
                )
            }
            else -> itemsIndexed((if (podiumOn) filteredAlbums.drop(3) else filteredAlbums).take(shown), key = { _, (_, al) -> "al" + al.album.albumId }) { _, (pos, al) ->
                val prev = prevAlbumPos[al.album.albumId]
                RankRow(
                    pos, al.album.title, al.artistName, al.periodPlays, al.periodDurationMs, al.album.coverUrl,
                    delta = prev?.let { it - pos }, isNew = prev == null && period != Period.GLOBAL,
                    onLongClick = { detail = DetailTarget.Album(al.album.albumId, period) }
                )
            }
        }
        if (listTotal > shown) item(key = "more") { LoadMoreButton(listTotal - shown) { visible += TOP_STEP } }
        item(key = "bottom") { Box(Modifier.height(24.dp)) }
    }
    }

}

private fun prevPeriodName(p: Period) = when (p) {
    Period.DAILY -> "hier"
    Period.WEEKLY -> "la semaine dernière"
    Period.MONTHLY -> "le mois dernier"
    Period.YEARLY -> "l'an dernier"
    Period.GLOBAL -> ""
}

private data class PodiumItem(
    val pos: Int, val title: String, val subtitle: String?, val plays: Int,
    val coverUrl: String?, val circle: Boolean, val onClick: () -> Unit
)

/** 🥇🥈🥉 Le top 3 en cartes, ordre podium [2ᵉ, 1ᵉʳ, 3ᵉ] — le n°1 plus grand au centre. */
@Composable
private fun PodiumRow(items: List<PodiumItem>) {
    val theme = Nova.theme
    if (items.isEmpty()) return
    val order = listOfNotNull(items.getOrNull(1), items.getOrNull(0), items.getOrNull(2))
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom
    ) {
        order.forEach { it ->
            val first = it.pos == 1
            Column(
                Modifier.weight(1f).clickable { it.onClick() },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(if (first) "🥇" else if (it.pos == 2) "🥈" else "🥉", fontSize = if (first) 26.sp else 20.sp)
                Spacer(Modifier.height(4.dp))
                CoverArt(it.coverUrl, it.title, circle = it.circle, size = if (first) 84 else 64)
                Spacer(Modifier.height(6.dp))
                Text(
                    it.title, color = theme.text, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = if (first) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                )
                Text("${formatCount(it.plays)} ▶", color = theme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun elapsedDays(range: com.novastats.app.domain.DateRange): Int {
    val end = minOf(range.to, Dates.today())
    return (java.time.temporal.ChronoUnit.DAYS.between(range.from, end) + 1).toInt().coerceAtLeast(1)
}

/* Icônes vectorielles minimalistes (pas de dépendance material-icons-extended) */
private val SearchIcon: ImageVector by lazy {
    ImageVector.Builder("search", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.White)) {
            moveTo(15.5f, 14f); horizontalLineToRelative(-0.79f); lineToRelative(-0.28f, -0.27f)
            curveTo(15.41f, 12.59f, 16f, 11.11f, 16f, 9.5f); curveTo(16f, 5.91f, 13.09f, 3f, 9.5f, 3f); reflectiveCurveTo(3f, 5.91f, 3f, 9.5f)
            reflectiveCurveTo(5.91f, 16f, 9.5f, 16f); curveToRelative(1.61f, 0f, 3.09f, -0.59f, 4.23f, -1.57f); lineToRelative(0.27f, 0.28f); verticalLineToRelative(0.79f)
            lineToRelative(5f, 4.99f); lineTo(20.49f, 19f); lineToRelative(-4.99f, -5f); close()
            moveTo(9.5f, 14f); curveTo(7.01f, 14f, 5f, 11.99f, 5f, 9.5f); reflectiveCurveTo(7.01f, 5f, 9.5f, 5f); reflectiveCurveTo(14f, 7.01f, 14f, 9.5f); reflectiveCurveTo(11.99f, 14f, 9.5f, 14f); close()
        }
    }.build()
}

private val CloseIcon: ImageVector by lazy {
    ImageVector.Builder("close", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.White)) {
            moveTo(19f, 6.41f); lineTo(17.59f, 5f); lineTo(12f, 10.59f); lineTo(6.41f, 5f); lineTo(5f, 6.41f); lineTo(10.59f, 12f)
            lineTo(5f, 17.59f); lineTo(6.41f, 19f); lineTo(12f, 13.41f); lineTo(17.59f, 19f); lineTo(19f, 17.59f); lineTo(13.41f, 12f); close()
        }
    }.build()
}
