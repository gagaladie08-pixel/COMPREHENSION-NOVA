package com.novastats.app.ui.screens

import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import com.novastats.app.NovaStatsApp
import com.novastats.app.data.db.dao.RankedAlbum
import com.novastats.app.data.db.entity.TrackEntity
import com.novastats.app.domain.CertLevel
import com.novastats.app.domain.Certification
import com.novastats.app.domain.CertificationRules
import com.novastats.app.domain.Dates
import com.novastats.app.domain.Period
import com.novastats.app.domain.PantheonStatus
import com.novastats.app.domain.ScrobbleRules
import com.novastats.app.ui.navigation.NovaTab
import com.novastats.app.ui.theme.Nova
import com.novastats.app.ui.theme.NovaColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 🏠 Accueil — 8 sections (scroll vertical), cahier des charges :
 * 1 En cours de lecture (pochette, barre de progression, app source, statut PENDING/VALIDATED)
 * 2 Aujourd'hui · 3 Top du moment (#1 titre / artiste / album du jour)
 * 4 Dernières actualités (certifications, statuts Panthéon, entrées Hall of Fame — "il y a Xh")
 * 5 Prochaines certifications (5 titres/albums les plus proches du palier suivant, barre + écoutes restantes)
 * 6 Records récents · 7 Récemment écouté (pochette + titre + heure) · 8 Streak (actuel + record)
 */
@Composable
fun HomeScreen(onOpenTab: (NovaTab) -> Unit) {
    val app = LocalContext.current.applicationContext as NovaStatsApp
    val db = app.database
    val theme = Nova.theme
    val today = Dates.today()
    val todayIso = today.format(Dates.ISO)

    val nowPlaying by db.nowPlayingDao().observe().collectAsStateWithLifecycle(initialValue = null)
    val thresholdSec by app.settings.thresholdSec.collectAsStateWithLifecycle(initialValue = ScrobbleRules.DEFAULT_THRESHOLD_SEC)
    val todayStats by db.dailyStatsDao().forDate(todayIso).collectAsStateWithLifecycle(initialValue = null)
    val topTrack by db.trackDao().topForPeriod(todayIso, todayIso, 1).collectAsStateWithLifecycle(initialValue = emptyList())
    val topArtist by db.artistDao().topForPeriod(todayIso, todayIso, 1).collectAsStateWithLifecycle(initialValue = emptyList())
    val topAlbum by db.albumDao().topForPeriod(todayIso, todayIso, 1).collectAsStateWithLifecycle(initialValue = emptyList())
    val certNews by db.certificationDao().latestHistory(10).collectAsStateWithLifecycle(initialValue = emptyList())
    val pantheonNews by db.pantheonDao().latestHistory(10).collectAsStateWithLifecycle(initialValue = emptyList())
    val hofNews by db.hallOfFameDao().latest(10).collectAsStateWithLifecycle(initialValue = emptyList())
    val mostPlayedTracks by db.trackDao().mostPlayed(200).collectAsStateWithLifecycle(initialValue = emptyList())
    val mostPlayedAlbums by db.albumDao().mostPlayed(100).collectAsStateWithLifecycle(initialValue = emptyList())
    val bestDay by db.dailyStatsDao().bestDay().collectAsStateWithLifecycle(initialValue = null)
    val bestTrackDay by db.dailyPlayDao().bestTrackDay().collectAsStateWithLifecycle(initialValue = null)
    val recent by db.scrobbleDao().recent(10).collectAsStateWithLifecycle(initialValue = emptyList())
    val streak by db.dailyStreakDao().latest().collectAsStateWithLifecycle(initialValue = null)
    val totalScrobbles by db.scrobbleDao().countConfirmedFlow().collectAsStateWithLifecycle(initialValue = 0)

    var detail by remember { mutableStateOf<DetailTarget?>(null) }
    DetailPopupHost(detail) { detail = null }

    // 🎨 Fond artistique : photo de ton artiste du moment (7 derniers jours), sinon pochette de ton titre du jour
    val week = remember { Dates.statsRangeFor(Period.WEEKLY) }
    val artUrl by produceState<String?>(initialValue = null) {
        value = runCatching {
            val a = db.artistDao().topForPeriod(week.fromIso, week.toIso, 1).first().firstOrNull()
            a?.artist?.photoUrl ?: db.trackDao().topForPeriod(week.fromIso, week.toIso, 1).first().firstOrNull()?.track?.coverUrl
        }.getOrNull()
    }

    var rewindKey by remember { mutableStateOf<String?>(null) }
    if (rewindKey != null) {
        RewindScreen(rewindKey) { rewindKey = null }
        return
    }

    if (totalScrobbles == 0 && nowPlaying?.rawTitle == null) {
        FirstContactHome()
        return
    }

    Box(Modifier.fillMaxSize()) {
        // Fond plein écran, très assombri pour garder la liste lisible
        RewindBackdrop(artUrl, theme, Modifier.fillMaxSize(), artAlpha = 0.42f, scrim = 1.18f)
        RewindParticles(theme, Modifier.fillMaxSize())
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        /* ---------- 0. Nova Rewind ---------- */
        item { RewindEntryCard { rewindKey = it } }

        /* ---------- 1. En cours de lecture ---------- */
        item {
            SectionTitle("En cours de lecture")
            NovaCard {
                val np = nowPlaying
                val track by produceState<TrackEntity?>(initialValue = null, np?.trackId) { value = np?.trackId?.let { db.nowPlayingDao().track(it) } }
                val active = np?.rawTitle != null && np.scrobbleStatus != "IDLE"
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CoverArt(track?.coverUrl, np?.rawTitle ?: "♪", size = 64)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            if (active && np != null) {
                                Text(np.rawTitle ?: "", color = theme.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(np.rawArtist ?: "", color = theme.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                np.rawAlbum?.takeIf { it.isNotBlank() }?.let { Text(it, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                Spacer(Modifier.height(4.dp))
                                val validated = np.scrobbleStatus == "VALIDATED"
                                val statusText = when {
                                    validated -> "✅ Écoute validée"
                                    !np.isPlaying -> "⏸ En pause · ${np.progressMs / 1000}s / ${thresholdSec}s"
                                    else -> "⏳ En attente · ${np.progressMs / 1000}s / ${thresholdSec}s"
                                }
                                Text(
                                    "${sourceAppName(np.sourceApp)} · $statusText",
                                    color = if (validated) theme.accent else theme.textSecondary, style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Text("Rien en lecture", color = theme.textSecondary)
                                Text("Le service capte automatiquement ta musique", style = MaterialTheme.typography.bodySmall, color = theme.textSecondary)
                            }
                        }
                    }
                    if (active && np != null) {
                        Spacer(Modifier.height(10.dp))
                        val duration = np.durationMs
                        if (duration != null && duration > 0) {
                            LinearProgressIndicator(
                                progress = { (np.positionMs.toFloat() / duration).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = theme.primary, trackColor = theme.primary.copy(alpha = 0.15f)
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(formatClock(np.positionMs), color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                                Text(formatClock(duration), color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                            }
                        } else {
                            // Durée inconnue (notification sans MediaSession) : progression vers le seuil de validation
                            LinearProgressIndicator(
                                progress = { (np.progressMs.toFloat() / (thresholdSec * 1000f)).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = theme.primary, trackColor = theme.primary.copy(alpha = 0.15f)
                            )
                            Text("Durée inconnue · progression vers le seuil de validation", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        /* ---------- 2. Aujourd'hui ---------- */
        item {
            SectionTitle("Aujourd'hui")
            // Bloc « verre » : le compteur du jour en très gros
            StatGlass(
                plays = todayStats?.playCount ?: 0,
                durationMs = todayStats?.totalDurationMs ?: 0L,
                extras = listOf("${todayStats?.distinctTracks ?: 0}" to "titres", "${todayStats?.distinctArtists ?: 0}" to "artistes"),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            // Même bandeau que l'onglet Stats
            SummaryStrip(
                listOf(
                    StripCell(formatCount(todayStats?.playCount ?: 0), "Écoutes"),
                    StripCell(formatDuration(todayStats?.totalDurationMs ?: 0), "Temps"),
                    StripCell("${todayStats?.distinctTracks ?: 0}", "Titres"),
                    StripCell("${todayStats?.distinctArtists ?: 0}", "Artistes"),
                    StripCell("${todayStats?.distinctAlbums ?: 0}", "Albums")
                )
            )
        }

        /* ---------- 3. Top du moment ---------- */
        item {
            SectionTitle("Top du moment")
            NovaCard {
                Column(Modifier.padding(vertical = 8.dp)) {
                    topTrack.firstOrNull()?.let {
                        RankRow(1, it.track.title, "Titre #1 du jour · ${it.artistName}", it.periodPlays, it.periodDurationMs, it.track.coverUrl, onClick = { detail = DetailTarget.Track(it.track.trackId) })
                    }
                    topArtist.firstOrNull()?.let {
                        RankRow(1, it.artist.name, "Artiste #1 du jour", it.periodPlays, it.periodDurationMs, it.artist.photoUrl, circle = true, onClick = { detail = DetailTarget.Artist(it.artist.artistId) })
                    }
                    topAlbum.firstOrNull()?.let {
                        RankRow(1, it.album.title, "Album #1 du jour · ${it.artistName}", it.periodPlays, it.periodDurationMs, it.album.coverUrl, onClick = { detail = DetailTarget.Album(it.album.albumId) })
                    }
                    if (topTrack.isEmpty()) Text("Pas encore d'écoute aujourd'hui", color = theme.textSecondary, modifier = Modifier.padding(16.dp))
                }
            }
        }

        /* ---------- 4. Dernières actualités ---------- */
        item {
            SectionTitle("Dernières actualités")
            val now = System.currentTimeMillis()
            val news = remember(certNews, pantheonNews, hofNews) {
                buildList {
                    certNews.forEach { n ->
                        val lvl = CertLevel.entries.firstOrNull { it.dbName == n.h.level }
                        val label = lvl?.let { Certification(it, n.h.multiplier).label() } ?: n.h.level
                        val kind = if (n.h.entityType == "ALBUM") "Album" else "Titre"
                        add(NewsItem(n.h.certifiedAt, "🏅", "$kind certifié $label", n.name ?: "—", if (n.h.entityType == "ALBUM") DetailTarget.Album(n.h.entityId) else DetailTarget.Track(n.h.entityId), NovaTab.CERTIFICATIONS))
                    }
                    pantheonNews.forEach { n ->
                        val st = PantheonStatus.fromDb(n.h.status)
                        add(NewsItem(n.h.dateReached, st?.emoji ?: "🏛️", "Panthéon · ${st?.label ?: n.h.status}", n.name ?: "—", DetailTarget.Artist(n.h.artistId), NovaTab.PANTHEON))
                    }
                    hofNews.forEach { n ->
                        val target = when (n.h.entityType) { "ARTIST" -> DetailTarget.Artist(n.h.entityId); "ALBUM" -> DetailTarget.Album(n.h.entityId); else -> DetailTarget.Track(n.h.entityId) }
                        add(NewsItem(n.h.createdAt, "🏆", "Hall of Fame · ${hofEntryLabel(n.h.entryType)} (${n.h.periodType.lowercase()})", n.name ?: "—", target, NovaTab.HALL_OF_FAME))
                    }
                }.sortedByDescending { it.at }.take(8)
            }
            NovaCard {
                Column(Modifier.padding(vertical = 6.dp)) {
                    if (news.isEmpty()) Text("Aucune actualité pour l'instant — certifications, Panthéon et Hall of Fame apparaîtront ici.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
                    news.forEach { n ->
                        Row(
                            Modifier.fillMaxWidth().clickable { detail = n.target }.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(n.emoji, fontSize = 22.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(n.subject, color = theme.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(n.headline, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(elapsedLabel(now - n.at), color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        /* ---------- 5. Prochaines certifications ---------- */
        item {
            SectionTitle("Prochaines certifications")
            val upcoming = remember(mostPlayedTracks, mostPlayedAlbums) {
                val tracks = mostPlayedTracks.map { t ->
                    val next = CertificationRules.TRACK.next(t.playCount)
                    val need = CertificationRules.TRACK.required(next)
                    val base = CertificationRules.TRACK.current(t.playCount)?.let { CertificationRules.TRACK.required(it) } ?: 0
                    Upcoming(t.title, "Titre", next, t.playCount, need, base, t.coverUrl, DetailTarget.Track(t.trackId))
                }
                val albums = mostPlayedAlbums.map { al: RankedAlbum ->
                    val next = CertificationRules.ALBUM.next(al.album.playCount)
                    val need = CertificationRules.ALBUM.required(next)
                    val base = CertificationRules.ALBUM.current(al.album.playCount)?.let { CertificationRules.ALBUM.required(it) } ?: 0
                    Upcoming(al.album.title, "Album · ${al.artistName}", next, al.album.playCount, need, base, al.album.coverUrl, DetailTarget.Album(al.album.albumId))
                }
                (tracks + albums).sortedWith(compareBy<Upcoming> { it.remaining }.thenByDescending { it.ratio }).take(5)
            }
            NovaCard {
                Column(Modifier.padding(vertical = 6.dp)) {
                    if (upcoming.isEmpty()) Text("Écoute encore un peu : le radar des certifications s'allume dès les premières écoutes.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
                    upcoming.forEach { u ->
                        Column(Modifier.fillMaxWidth().clickable { detail = u.target }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CoverArt(u.coverUrl, u.title, size = 40)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(u.title, color = theme.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(u.kind, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(u.next.label(), color = NovaColors.Gold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    Text("encore ${u.remaining} écoute${if (u.remaining > 1) "s" else ""}", color = theme.primary, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { u.ratio }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                                color = NovaColors.Gold, trackColor = NovaColors.Gold.copy(alpha = 0.15f)
                            )
                            Text("${u.plays} / ${u.need}", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        /* ---------- 6. Records récents ---------- */
        item {
            SectionTitle("Records récents")
            val records = buildList {
                bestDay?.let { add(RecordItem("📅", "Journée record", "${formatCount(it.playCount)} écoutes · ${formatDuration(it.totalDurationMs)}", it.date)) }
                bestTrackDay?.let { add(RecordItem("🔁", "Titre le plus joué en un jour", "${it.title} · ${it.daily.playCount} écoutes", it.daily.date)) }
                streak?.takeIf { it.bestStreak > 0 }?.let { add(RecordItem("🔥", "Meilleur streak", "${it.bestStreak} jours consécutifs", it.bestStreakDate ?: it.date)) }
            }.sortedByDescending { it.date }
            NovaCard {
                Column(Modifier.padding(vertical = 6.dp)) {
                    if (records.isEmpty()) Text("Tes records personnels apparaîtront ici.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
                    records.forEach { r ->
                        Row(Modifier.fillMaxWidth().clickable { onOpenTab(NovaTab.RECORDS) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(r.emoji, fontSize = 22.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.title, color = theme.text, fontWeight = FontWeight.SemiBold)
                                Text(r.detail, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(shortDate(r.date), color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        /* ---------- 7. Récemment écouté ---------- */
        item { SectionTitle("Récemment écouté") }
        items(recent, key = { it.scrobble.scrobbleId }) { s ->
            val time = SimpleDateFormat("HH:mm · dd MMM", Locale.FRANCE).format(Date(s.scrobble.startedAt))
            Row(
                Modifier.fillMaxWidth().clickable { detail = DetailTarget.Track(s.scrobble.trackId) }.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverArt(s.coverUrl, s.title, size = 40)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.title, color = theme.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(s.artistName, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(time, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }

        /* ---------- 8. Streak ---------- */
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
                        Text(streak?.bestStreakDate?.let { "record personnel · ${shortDate(it)}" } ?: "record personnel", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
    }
}

/* ---------- Modèles d'affichage ---------- */

private data class NewsItem(val at: Long, val emoji: String, val headline: String, val subject: String, val target: DetailTarget, val tab: NovaTab)

private data class Upcoming(
    val title: String, val kind: String, val next: Certification, val plays: Int, val need: Int, val base: Int,
    val coverUrl: String?, val target: DetailTarget
) {
    val remaining: Int get() = (need - plays).coerceAtLeast(0)
    val ratio: Float get() = if (need > base) ((plays - base).toFloat() / (need - base)).coerceIn(0f, 1f) else 0f
}

private data class RecordItem(val emoji: String, val title: String, val detail: String, val date: String)

/* ---------- Helpers ---------- */

private fun formatClock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.FRANCE, "%d:%02d", s / 60, s % 60)
}

/** "il y a 3h", "il y a 2 j", "à l'instant". */
fun elapsedLabel(deltaMs: Long): String {
    val m = deltaMs / 60_000
    return when {
        m < 1 -> "à l'instant"
        m < 60 -> "il y a ${m} min"
        m < 60 * 24 -> "il y a ${m / 60}h"
        m < 60 * 24 * 30 -> "il y a ${m / (60 * 24)} j"
        else -> "il y a ${m / (60 * 24 * 30)} mois"
    }
}

private fun shortDate(iso: String): String = runCatching {
    Dates.parse(iso).format(java.time.format.DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE))
}.getOrDefault(iso)

private fun hofEntryLabel(type: String) = when (type) {
    "DIRECT_DEBUT" -> "Direct #1"
    "LONG_RUN" -> "Long run"
    "TRIPLE_DEBUT" -> "Triple début"
    "LEGENDARY_RUN" -> "Run légendaire"
    else -> type
}

/** Nom lisible de l'application source (package → nom). */
fun sourceAppName(pkg: String?): String = when (pkg) {
    null -> "—"
    "com.spotify.music" -> "Spotify"
    "com.google.android.apps.youtube.music" -> "YouTube Music"
    "com.google.android.youtube" -> "YouTube"
    "com.apple.android.music" -> "Apple Music"
    "deezer.android.app" -> "Deezer"
    "com.soundcloud.android" -> "SoundCloud"
    "com.amazon.mp3" -> "Amazon Music"
    "com.aspiro.tidal" -> "TIDAL"
    "com.shazam.android" -> "Shazam"
    "com.maxmpz.audioplayer" -> "Poweramp"
    else -> pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}


/* ---------- Accueil vide — premier contact (avant la première écoute) ---------- */
@Composable
private fun FirstContactHome() {
    val theme = Nova.theme
    val context = LocalContext.current
    var listenerOk by remember { mutableStateOf(com.novastats.app.service.NovaListenerService.isEnabled(context)) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { listenerOk = com.novastats.app.service.NovaListenerService.isEnabled(context); onPauseOrDispose { } }
    val float by androidx.compose.animation.core.rememberInfiniteTransition(label = "fc").animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(2400), androidx.compose.animation.core.RepeatMode.Reverse), label = "fca")
    val beat by androidx.compose.animation.core.rememberInfiniteTransition(label = "hb").animateFloat(0.3f, 0.9f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "hba")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        SectionTitle("🎵 En cours de lecture")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("🎧 En attente de ta première écoute…", color = theme.text, fontWeight = FontWeight.SemiBold)
                        Text("Lance ta musique et regarde la magie opérer", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    listOf("♪", "♫", "♬").forEachIndexed { i, n -> Text(n, color = theme.primary.copy(alpha = 0.4f + 0.6f * ((float + i * 0.33f) % 1f)), fontSize = (16 + 6 * ((float + i * 0.5f) % 1f)).sp, modifier = Modifier.padding(start = 6.dp, bottom = (10 * ((float + i * 0.33f) % 1f)).dp)) }
                }
            }
        }
        SectionTitle("📊 Aujourd'hui")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    listOf("🎵 0", "⏱️ 0:00", "🎤 0", "💿 0").forEach { Text(it, color = theme.textSecondary.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                }
                Text("Tout commence ici ✨", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp).fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        SectionTitle("🎯 Première mission")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                Text("🎵 Écoute ta première chanson", color = theme.text, fontWeight = FontWeight.SemiBold)
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFE74C3C).copy(alpha = beat * 0.35f)))
                    Spacer(Modifier.width(10.dp))
                    Text("0/1", color = theme.textSecondary, fontWeight = FontWeight.Bold)
                }
                Text("Récompense : 🏆 Premier Scrobble", color = Color(0xFFFFD700), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                Text("📎 Spotify · YouTube Music · Deezer · Apple Music · ou autre 🎶", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
        SectionTitle("🔥 Streak")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                Text("🔥 0 jour", color = Color(0xFFE74C3C), fontWeight = FontWeight.Black, fontSize = 22.sp)
                Text("Ton premier jour commence dès ta première écoute", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (listenerOk) "🟢 Service actif — En écoute" else "🔴 Détection inactive — autorise l'accès aux notifications dans ⚙️",
            color = if (listenerOk) Color(0xFF2ECC71) else Color(0xFFE74C3C), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text("Tu as déjà un historique ? Importe ton backup JSON depuis ⚙️ → Données.", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
