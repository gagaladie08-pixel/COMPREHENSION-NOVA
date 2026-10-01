package com.novastats.app.ui.screens

import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novastats.app.NovaStatsApp
import com.novastats.app.data.db.dao.PeriodSummary
import com.novastats.app.domain.Dates
import com.novastats.app.domain.PantheonStatus
import com.novastats.app.domain.Period
import com.novastats.app.domain.TitleNormalizer
import com.novastats.app.ui.theme.Nova

private val statsTabs = listOf("Titres", "Artistes", "Albums")
private const val TOP_LIMIT = 300

/**
 * 📊 Stats — 3 sous-onglets Titres / Artistes / Albums + icône recherche (temps réel, portée = sous-onglet actif).
 * Sélecteur de période commun : Daily = aujourd'hui · Weekly = 7 derniers jours (glissant) · Monthly = mois
 * calendaire · Yearly = année calendaire · Global. Top 300, tri écoutes puis temps cumulé.
 * Positions : 🥇🥈🥉 #1-3 · 🔥 #4-#10 · "#11" · "—" non classé · "▼ 300+".
 * Appui long sur une ligne → popup de détail (chanson / artiste / album).
 */
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
    val range = remember(period) { Dates.statsRangeFor(period) }

    val summary by remember(range) { db.scrobbleDao().summary(range.fromMs, range.toMs) }
        .collectAsStateWithLifecycle(initialValue = PeriodSummary(0, 0, 0, 0, 0, 0))

    val tracks by remember(period) {
        if (period == Period.GLOBAL) db.trackDao().topAllTime(TOP_LIMIT) else db.trackDao().topForPeriod(range.fromIso, range.toIso, TOP_LIMIT)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val artists by remember(period) {
        if (period == Period.GLOBAL) db.artistDao().topAllTime(TOP_LIMIT) else db.artistDao().topForPeriod(range.fromIso, range.toIso, TOP_LIMIT)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val albums by remember(period) {
        if (period == Period.GLOBAL) db.albumDao().topAllTime(TOP_LIMIT) else db.albumDao().topForPeriod(range.fromIso, range.toIso, TOP_LIMIT)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val q = TitleNormalizer.normalizeKey(query)
    fun matches(vararg fields: String?) = q.isBlank() || fields.any { it != null && TitleNormalizer.normalizeKey(it).contains(q) }

    DetailPopupHost(detail) { detail = null }

    Column(Modifier.fillMaxSize()) {
        /* ---------- En-tête (maquette utilisateur) : périodes → bandeau → onglets Titres / Artistes / Albums + 🔍 ---------- */

        // 1. Sélecteur de période
        PeriodSegment(period) { period = it }

        // 2. Bandeau résumé
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
            periodCaption(period, range), color = theme.textSecondary.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), textAlign = TextAlign.Center
        )

        // 3. Onglets Titres / Artistes / Albums + loupe
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            TabRow(
                selectedTabIndex = tab,
                modifier = Modifier.weight(1f),
                containerColor = theme.background,
                contentColor = theme.primary,
                indicator = { positions ->
                    Box(Modifier.tabIndicatorOffset(positions[tab]).padding(horizontal = 28.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(theme.primary))
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

        val filteredTracks = remember(tracks, q) { tracks.mapIndexed { i, t -> i + 1 to t }.filter { (_, t) -> matches(t.track.title, t.artistName, t.albumTitle) } }
        val filteredArtists = remember(artists, q) { artists.mapIndexed { i, a -> i + 1 to a }.filter { (_, a) -> matches(a.artist.name) } }
        val filteredAlbums = remember(albums, q) { albums.mapIndexed { i, al -> i + 1 to al }.filter { (_, al) -> matches(al.album.title, al.artistName) } }

        val isEmpty = when (tab) { 0 -> filteredTracks.isEmpty(); 1 -> filteredArtists.isEmpty(); else -> filteredAlbums.isEmpty() }
        if (isEmpty) {
            if (q.isNotBlank()) EmptyState("🔍", "Aucun résultat pour « $query »", "dans le Top $TOP_LIMIT ${statsTabs[tab].lowercase()} de la période")
            else EmptyState("📊", "Aucune écoute sur cette période", "Ton classement s'enrichit à chaque écoute")
            return
        }

        LazyColumn(Modifier.fillMaxSize()) {
            when (tab) {
                0 -> itemsIndexed(filteredTracks, key = { _, (_, t) -> t.track.trackId }) { _, (pos, t) ->
                    RankRow(
                        pos, t.track.title, listOfNotNull(t.artistName, t.albumTitle).joinToString(" · "), t.periodPlays, t.periodDurationMs, t.track.coverUrl,
                        onLongClick = { detail = DetailTarget.Track(t.track.trackId) }
                    )
                }
                1 -> itemsIndexed(filteredArtists, key = { _, (_, a) -> a.artist.artistId }) { _, (pos, a) ->
                    val sub = PantheonStatus.fromDb(a.artist.pantheonStatus)?.let { "${it.emoji} ${it.label.uppercase()}" }
                    RankRow(
                        pos, a.artist.name, sub, a.periodPlays, a.periodDurationMs, a.artist.photoUrl, circle = true,
                        onLongClick = { detail = DetailTarget.Artist(a.artist.artistId) }
                    )
                }
                else -> itemsIndexed(filteredAlbums, key = { _, (_, al) -> al.album.albumId }) { _, (pos, al) ->
                    RankRow(
                        pos, al.album.title, al.artistName, al.periodPlays, al.periodDurationMs, al.album.coverUrl,
                        onLongClick = { detail = DetailTarget.Album(al.album.albumId) }
                    )
                }
            }
        }
    }
}

private fun elapsedDays(range: com.novastats.app.domain.DateRange): Int {
    val end = minOf(range.to, Dates.today())
    return (java.time.temporal.ChronoUnit.DAYS.between(range.from, end) + 1).toInt().coerceAtLeast(1)
}

private fun periodCaption(period: Period, range: com.novastats.app.domain.DateRange): String {
    val f = java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.FRANCE)
    return when (period) {
        Period.DAILY -> "Aujourd'hui · ${range.to.format(f)}"
        Period.WEEKLY -> "7 derniers jours · ${range.from.format(f)} → ${range.to.format(f)}"
        Period.MONTHLY -> "Mois en cours · ${range.from.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.FRANCE))}"
        Period.YEARLY -> "Année ${range.to.year}"
        Period.GLOBAL -> "Depuis le début · Top $TOP_LIMIT"
    }
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
