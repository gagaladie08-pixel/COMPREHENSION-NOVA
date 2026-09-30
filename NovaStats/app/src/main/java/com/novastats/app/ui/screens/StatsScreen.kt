package com.novastats.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novastats.app.NovaStatsApp
import com.novastats.app.data.db.dao.PeriodSummary
import com.novastats.app.domain.Dates
import com.novastats.app.domain.Period
import com.novastats.app.ui.theme.Nova

private val statsTabs = listOf("Titres", "Artistes", "Albums")

/**
 * 📊 Stats — Titres / Artistes / Albums × Daily / Weekly / Monthly / Yearly / Global.
 * Période calendaire appliquée simultanément aux 3 onglets. Top 300, tri écoutes puis temps.
 */
@Composable
fun StatsScreen() {
    val app = LocalContext.current.applicationContext as NovaStatsApp
    val db = app.database
    val theme = Nova.theme

    var tab by remember { mutableIntStateOf(0) }
    var period by remember { mutableStateOf(Period.WEEKLY) }
    val range = remember(period) { Dates.rangeFor(period) }

    val summary by remember(range) { db.scrobbleDao().summary(range.fromMs, range.toMs) }
        .collectAsStateWithLifecycle(initialValue = PeriodSummary(0, 0, 0, 0, 0, 0))

    val tracks by remember(period) {
        if (period == Period.GLOBAL) db.trackDao().topAllTime() else db.trackDao().topForPeriod(range.fromIso, range.toIso)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val artists by remember(period) {
        if (period == Period.GLOBAL) db.artistDao().topAllTime() else db.artistDao().topForPeriod(range.fromIso, range.toIso)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val albums by remember(period) {
        if (period == Period.GLOBAL) db.albumDao().topAllTime() else db.albumDao().topForPeriod(range.fromIso, range.toIso)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    Column(Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = tab,
            containerColor = theme.surface,
            contentColor = theme.primary,
            indicator = { positions -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[tab]), color = theme.primary) }
        ) {
            statsTabs.forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) }, selectedContentColor = theme.primary, unselectedContentColor = theme.textSecondary)
            }
        }

        // Sélecteur de période
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Period.entries.forEach { p ->
                FilterChip(
                    selected = period == p,
                    onClick = { period = p },
                    label = { Text(p.label, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = theme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        labelColor = theme.textSecondary, containerColor = theme.surface
                    )
                )
            }
        }

        // Bandeau résumé
        NovaCard {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceAround) {
                StatPill(formatCount(summary.playCount), "écoutes")
                StatPill(formatDuration(summary.totalDurationMs), "temps", accent = theme.secondary)
                StatPill("${summary.distinctTracks}", "titres", accent = theme.accent)
                StatPill("${summary.distinctArtists}", "artistes", accent = theme.glowSecondary)
                val avg = if (summary.activeDays > 0) summary.playCount / summary.activeDays else 0
                StatPill("$avg", "moy./jour")
            }
        }

        val isEmpty = when (tab) { 0 -> tracks.isEmpty(); 1 -> artists.isEmpty(); else -> albums.isEmpty() }
        if (isEmpty) {
            EmptyState("📊", "Aucune écoute sur cette période", "Ton classement s'enrichit à chaque écoute")
            return
        }

        LazyColumn(Modifier.fillMaxSize()) {
            when (tab) {
                0 -> itemsIndexed(tracks, key = { _, t -> t.track.trackId }) { i, t ->
                    RankRow(i + 1, t.track.title, listOfNotNull(t.artistName, t.albumTitle).joinToString(" · "), t.periodPlays, t.periodDurationMs, t.track.coverUrl)
                }
                1 -> itemsIndexed(artists, key = { _, a -> a.artist.artistId }) { i, a ->
                    val sub = a.artist.pantheonStatus?.let { com.novastats.app.domain.PantheonStatus.fromDb(it) }?.let { "${it.emoji} ${it.label.uppercase()}" }
                    RankRow(i + 1, a.artist.name, sub, a.periodPlays, a.periodDurationMs, a.artist.photoUrl, circle = true)
                }
                else -> itemsIndexed(albums, key = { _, al -> al.album.albumId }) { i, al ->
                    RankRow(i + 1, al.album.title, al.artistName, al.periodPlays, al.periodDurationMs, al.album.coverUrl)
                }
            }
        }
    }
}
