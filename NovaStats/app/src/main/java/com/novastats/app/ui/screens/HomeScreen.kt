package com.novastats.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novastats.app.NovaStatsApp
import com.novastats.app.domain.Dates
import com.novastats.app.ui.navigation.NovaTab
import com.novastats.app.ui.theme.Nova
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 🏠 Accueil — 8 sections (scroll vertical).
 * Implémentées : 1 (En cours), 2 (Aujourd'hui), 3 (Top du moment), 7 (Récemment écouté), 8 (Streak).
 * Sections 4/5/6 (actualités, prochaines certifs, records) arrivent avec leurs modules.
 */
@Composable
fun HomeScreen(onOpenTab: (NovaTab) -> Unit) {
    val app = LocalContext.current.applicationContext as NovaStatsApp
    val db = app.database
    val theme = Nova.theme
    val today = Dates.today()
    val todayIso = today.format(Dates.ISO)

    val nowPlaying by db.nowPlayingDao().observe().collectAsStateWithLifecycle(initialValue = null)
    val todayStats by db.dailyStatsDao().forDate(todayIso).collectAsStateWithLifecycle(initialValue = null)
    val topTrack by db.trackDao().topForPeriod(todayIso, todayIso, 1).collectAsStateWithLifecycle(initialValue = emptyList())
    val topArtist by db.artistDao().topForPeriod(todayIso, todayIso, 1).collectAsStateWithLifecycle(initialValue = emptyList())
    val topAlbum by db.albumDao().topForPeriod(todayIso, todayIso, 1).collectAsStateWithLifecycle(initialValue = emptyList())
    val recent by db.scrobbleDao().recent(10).collectAsStateWithLifecycle(initialValue = emptyList())
    val streak by db.dailyStreakDao().latest().collectAsStateWithLifecycle(initialValue = null)
    val totalScrobbles by db.scrobbleDao().countConfirmedFlow().collectAsStateWithLifecycle(initialValue = 0)

    if (totalScrobbles == 0 && nowPlaying?.rawTitle == null) {
        Column(Modifier.fillMaxSize()) {
            EmptyState("🎵", "Bienvenue sur NovaStats", "Lance de la musique ou importe ton backup JSON depuis ⚙️ Réglages → Données.\nTon classement s'enrichit à chaque écoute.")
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        // Section 1 — En cours de lecture
        item {
            SectionTitle("En cours de lecture")
            NovaCard {
                val np = nowPlaying
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoverArt(null, np?.rawTitle ?: "♪", size = 56)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        if (np?.rawTitle != null) {
                            Text(np.rawTitle, color = theme.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(np.rawArtist ?: "", color = theme.textSecondary, maxLines = 1)
                            Text(
                                "${np.sourceApp ?: ""} · ${np.scrobbleStatus}",
                                color = if (np.scrobbleStatus == "VALIDATED") theme.accent else theme.textSecondary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        } else {
                            Text("Rien en lecture", color = theme.textSecondary)
                            Text("Le service capte automatiquement ta musique", style = MaterialTheme.typography.bodySmall, color = theme.textSecondary)
                        }
                    }
                }
            }
        }

        // Section 2 — Aujourd'hui
        item {
            SectionTitle("Aujourd'hui")
            NovaCard {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceAround) {
                    StatPill(formatCount(todayStats?.playCount ?: 0), "écoutes")
                    StatPill(formatDuration(todayStats?.totalDurationMs ?: 0), "temps", accent = theme.secondary)
                    StatPill("${todayStats?.distinctArtists ?: 0}", "artistes", accent = theme.accent)
                    StatPill("${todayStats?.distinctAlbums ?: 0}", "albums", accent = theme.glowSecondary)
                    StatPill("${todayStats?.distinctTracks ?: 0}", "titres")
                }
            }
        }

        // Section 3 — Top du moment (aujourd'hui)
        item {
            SectionTitle("Top du moment")
            NovaCard {
                Column(Modifier.padding(vertical = 8.dp)) {
                    topTrack.firstOrNull()?.let { RankRow(1, it.track.title, it.artistName, it.periodPlays, it.periodDurationMs, it.track.coverUrl) }
                    topArtist.firstOrNull()?.let { RankRow(1, it.artist.name, "Artiste #1", it.periodPlays, it.periodDurationMs, it.artist.photoUrl, circle = true) }
                    topAlbum.firstOrNull()?.let { RankRow(1, it.album.title, it.artistName, it.periodPlays, it.periodDurationMs, it.album.coverUrl) }
                    if (topTrack.isEmpty()) Text("Pas encore d'écoute aujourd'hui", color = theme.textSecondary, modifier = Modifier.padding(16.dp))
                }
            }
        }

        // Section 8 — Streak (remontée ici en attendant les sections 4-6)
        item {
            SectionTitle("Streak")
            NovaCard {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔥 ${streak?.currentStreak ?: 0}", fontSize = 36.sp, fontWeight = FontWeight.Black, color = theme.primary)
                        Text("jours consécutifs", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🏆 ${streak?.bestStreak ?: 0}", style = MaterialTheme.typography.titleMedium, color = theme.textSecondary)
                        Text(streak?.bestStreakDate?.let { "record · $it" } ?: "record", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Section 7 — Récemment écouté
        item { SectionTitle("Récemment écouté") }
        items(recent, key = { it.scrobble.scrobbleId }) { s ->
            val time = SimpleDateFormat("HH:mm · dd MMM", Locale.FRANCE).format(Date(s.scrobble.startedAt))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                CoverArt(s.coverUrl, s.title, size = 40)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.title, color = theme.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(s.artistName, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                Text(time, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}
