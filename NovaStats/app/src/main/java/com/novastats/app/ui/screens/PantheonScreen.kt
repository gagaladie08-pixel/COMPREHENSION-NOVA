package com.novastats.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novastats.app.NovaStatsApp
import com.novastats.app.data.db.dao.ArtistCertRow
import com.novastats.app.data.db.dao.PantheonRow
import com.novastats.app.data.db.entity.ArtistEntity
import com.novastats.app.data.db.entity.EntityType
import com.novastats.app.domain.ArtistCertSummary
import com.novastats.app.domain.CertLevel
import com.novastats.app.domain.Certification
import com.novastats.app.domain.PantheonRules
import com.novastats.app.domain.PantheonStatus
import com.novastats.app.ui.theme.Nova
import androidx.compose.runtime.produceState
import kotlinx.coroutines.flow.first
import com.novastats.app.ui.theme.NovaColors
import com.novastats.app.domain.Period
import com.novastats.app.domain.Dates

/** Couleur d'un statut (Mythique = dégradé holographique animé, on renvoie la base). */
fun pantheonColor(s: PantheonStatus): Color = when (s) {
    PantheonStatus.STAR -> Color(0xFF4A90E2)
    PantheonStatus.SUPERSTAR -> Color(0xFF9B59B6)
    PantheonStatus.MEGASTAR -> NovaColors.Gold
    PantheonStatus.LEGENDE -> Color(0xFFC0392B)
    PantheonStatus.MYTHIQUE -> Color(0xFFE0B0FF)
}

private val holo = listOf(Color(0xFFFF6EC7), Color(0xFFFFD86E), Color(0xFF6EFFB8), Color(0xFF6EC1FF), Color(0xFFC96EFF), Color(0xFFFF6EC7))

/** Progression vers le statut suivant : (fraction 0..1, message « Il manque … »). */
data class NextStatusProgress(val next: PantheonStatus, val fraction: Float, val message: String)

fun nextStatusProgress(current: PantheonStatus?, plays: Int, certs: ArtistCertSummary): NextStatusProgress? {
    val next = PantheonRules.next(current) ?: return null
    val playsFraction = (plays.toFloat() / next.playsThreshold).coerceIn(0f, 1f)
    val missingPlays = (next.playsThreshold - plays).coerceAtLeast(0)
    // Option A : niveau de certification requis
    val level = when (next) { PantheonStatus.STAR -> CertLevel.SILVER; PantheonStatus.SUPERSTAR -> CertLevel.GOLD; PantheonStatus.MEGASTAR -> CertLevel.PLATINUM; else -> CertLevel.DIAMOND }
    val needT = 5; val needA = 2
    val haveT = certs.tracksAtLeast(level).coerceAtMost(needT); val haveA = certs.albumsAtLeast(level).coerceAtMost(needA)
    val certFraction = (haveT + haveA).toFloat() / (needT + needA)
    val parts = mutableListOf<String>()
    if (needT - haveT > 0) parts += "${needT - haveT} chanson${if (needT - haveT > 1) "s" else ""} ${level.label}"
    if (needA - haveA > 0) parts += "${needA - haveA} album${if (needA - haveA > 1) "s" else ""} ${level.label}"
    val certMsg = if (next == PantheonStatus.MYTHIQUE) "2 titres et 2 albums à chaque niveau" else parts.joinToString(" + ")
    val msg = when {
        playsFraction >= certFraction -> "Il manque $missingPlays écoute${if (missingPlays > 1) "s" else ""} (ou $certMsg) pour passer ${next.label}"
        else -> "Il manque $certMsg (ou $missingPlays écoutes) pour passer ${next.label}"
    }
    return NextStatusProgress(next, maxOf(playsFraction, certFraction), msg)
}

fun summaryOf(rows: List<ArtistCertRow>): ArtistCertSummary {
    fun toCert(r: ArtistCertRow) = CertLevel.entries.firstOrNull { it.dbName == r.level }?.let { Certification(it, r.multiplier) }
    return ArtistCertSummary(
        trackCerts = rows.filter { it.entityType == EntityType.TRACK }.mapNotNull(::toCert),
        albumCerts = rows.filter { it.entityType == EntityType.ALBUM }.mapNotNull(::toCert)
    )
}

/**
 * 👑 Panthéon — artistes classés par statut (Mythique en tête), recherche, progression vers le statut suivant,
 * section « Bientôt dans le Panthéon ». Alimenté automatiquement par les certifications et les écoutes.
 */
@Composable
fun PantheonScreen() {
    val theme = Nova.theme
    val app = LocalContext.current.applicationContext as NovaStatsApp
    val db = app.database
    var query by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<DetailTarget?>(null) }

    val rows by app.database.pantheonDao().rows().collectAsStateWithLifecycle(initialValue = emptyList())
    val certRows by app.database.certificationDao().artistCertRows().collectAsStateWithLifecycle(initialValue = emptyList())
    val artists by app.database.artistDao().allForEditor().collectAsStateWithLifecycle(initialValue = emptyList())
    val certsByArtist = remember(certRows) { certRows.groupBy { it.artistId } }
    val news by db.pantheonDao().latestHistory(30).collectAsStateWithLifecycle(initialValue = emptyList())
    val recentNews = remember(news) { news.filter { System.currentTimeMillis() - it.h.dateReached <= 30L * 86_400_000L } }
    val totalAllTime by produceState(initialValue = 0) { value = runCatching { db.scrobbleDao().countConfirmed() }.getOrDefault(0) }
    val weekArtistPlays by produceState<Map<Long, Int>>(initialValue = emptyMap()) {
        val r = Dates.statsRangeFor(Period.WEEKLY)
        value = runCatching { db.artistDao().topForPeriod(r.fromIso, r.toIso, 500).first().associate { it.artist.artistId to it.periodPlays } }.getOrDefault(emptyMap())
    }

    val q = query.trim().lowercase()
    val sorted = remember(rows, q, statusFilter) {
        rows.filter { (q.isEmpty() || it.name.lowercase().contains(q)) && (statusFilter == null || it.s.currentStatus == statusFilter) }
            .sortedWith(compareByDescending<PantheonRow> { PantheonStatus.fromDb(it.s.currentStatus)?.ordinal ?: -1 }.thenByDescending { it.playCount })
    }
    // Bientôt dans le Panthéon : pas encore Star mais ≥ 50 % du chemin
    val inPantheon = remember(rows) { rows.map { it.s.artistId }.toSet() }
    val soon = remember(artists, certsByArtist, inPantheon, q) {
        artists.filter { it.artistId !in inPantheon && (q.isEmpty() || it.name.lowercase().contains(q)) }
            .mapNotNull { a -> nextStatusProgress(null, a.playCount, summaryOf(certsByArtist[a.artistId].orEmpty()))?.takeIf { it.fraction >= 0.5f }?.let { a to it } }
            .sortedByDescending { it.second.fraction }.take(10)
    }
    val counts = remember(rows) { rows.groupingBy { it.s.currentStatus }.eachCount() }

    // 🎨 Photo de l'artiste le plus écouté (all time) en fond d'écran
    val artUrl by produceState<String?>(initialValue = null) {
        value = runCatching { db.artistDao().topAllTime(1).first().firstOrNull()?.artist?.photoUrl }.getOrNull()
    }

    ScreenBackdrop(artUrl) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text("👑 Panthéon", style = MaterialTheme.typography.headlineSmall, color = theme.text, fontWeight = FontWeight.Bold)
                Text(
                    if (rows.isEmpty()) "Aucun artiste n'a encore atteint le statut Star (425 écoutes ou 5 chansons + 2 albums certifiés)."
                    else PantheonStatus.entries.reversed().mapNotNull { s -> counts[s.dbName]?.let { "${s.emoji} $it" } }.joinToString("   "),
                    color = theme.textSecondary, style = MaterialTheme.typography.bodySmall
                )
            }
            AscentFrieze(counts)
            Spacer(Modifier.height(10.dp))
            WeightCard(rows, totalAllTime)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true, placeholder = { Text("🔍 Rechercher un artiste…") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
            // Chips de filtre par statut (scrollables) avec compteur — Mythique en tête
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NovaFilterChip(
                    flagKey = "all", selected = statusFilter == null, onClick = { statusFilter = null }, label = { Text("Tous (${rows.size})") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = theme.primary.copy(alpha = 0.25f), selectedLabelColor = theme.text)
                )
                PantheonStatus.entries.reversed().forEach { st ->
                    val c = pantheonColor(st)
                    val on = statusFilter == st.dbName
                    NovaFilterChip(
                        flagKey = st, selected = on, onClick = { statusFilter = if (on) null else st.dbName },
                        label = { Text("${st.emoji} ${st.label} (${counts[st.dbName] ?: 0})") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.copy(alpha = 0.3f), selectedLabelColor = theme.text),
                        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = on, borderColor = c.copy(alpha = 0.5f), selectedBorderColor = c)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        if (recentNews.isNotEmpty()) item(key = "intron") {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp))
                    .background(NovaColors.Gold.copy(alpha = 0.12f)).padding(12.dp)
            ) {
                Text("🎉 Intronsations récentes (30 derniers jours)", color = NovaColors.Gold, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
                recentNews.take(3).forEach { n ->
                    val st = PantheonStatus.fromDb(n.h.status)
                    Row(
                        Modifier.fillMaxWidth().clickable { detail = DetailTarget.Pantheon(n.h.artistId) }.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${st?.emoji ?: "👑"} « ${n.name ?: "?"} » → ${st?.label ?: n.h.status} · ${sinceLabel(n.h.dateReached)}",
                            color = theme.text, style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        if (rows.isNotEmpty() && sorted.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🕳️", fontSize = 40.sp)
                Text("Aucun artiste pour ce filtre", color = theme.text, fontWeight = FontWeight.Bold)
            }
        }
        if (rows.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🏛️", fontSize = 64.sp)
                Text("Le Panthéon attend ses premières légendes", color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("⭐ Star → 🌟 Superstar → 👑 Megastar → 🏛️ Légende → ✨ Mythique. Un statut acquis ne se perd jamais.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        items(sorted, key = { it.s.artistId }) { row ->
            val status = PantheonStatus.fromDb(row.s.currentStatus) ?: PantheonStatus.STAR
            val certs = summaryOf(certsByArtist[row.s.artistId].orEmpty())
            val progress = nextStatusProgress(status, row.playCount, certs)?.takeIf { it.fraction >= 0.6f }
            PantheonCard(row, status, certs, progress) { detail = DetailTarget.Pantheon(row.s.artistId) }
        }
        if (soon.isNotEmpty()) {
            item { SectionTitle("🔜 Bientôt dans le Panthéon", Modifier.padding(top = 12.dp)) }
            items(soon, key = { "soon" + it.first.artistId }) { (a, p) -> SoonRow(a, p, weekArtistPlays) { detail = DetailTarget.Artist(a.artistId) } }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    }

    DetailPopupHost(detail) { detail = null }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PantheonCard(row: PantheonRow, status: PantheonStatus, certs: ArtistCertSummary, progress: NextStatusProgress?, onOpen: () -> Unit) {
    val theme = Nova.theme
    val mythic = status == PantheonStatus.MYTHIQUE
    val glowAlpha by rememberInfiniteTransition(label = "p").animateFloat(
        initialValue = 0.15f, targetValue = when (status) { PantheonStatus.STAR -> 0.3f; PantheonStatus.SUPERSTAR -> 0.45f; PantheonStatus.MEGASTAR -> 0.6f; else -> 0.8f },
        animationSpec = infiniteRepeatable(tween(if (mythic) 1200 else 2000), RepeatMode.Reverse), label = "pa"
    )
    val color = pantheonColor(status)
    val borderBrush = if (mythic) Brush.sweepGradient(holo) else Brush.linearGradient(listOf(color.copy(alpha = glowAlpha + 0.2f), color.copy(alpha = 0.3f)))
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(if (mythic) 16.dp else 8.dp, RoundedCornerShape(16.dp), ambientColor = color, spotColor = color)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(color.copy(alpha = glowAlpha * 0.5f), theme.surface)))
            .border(2.dp, borderBrush, RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onOpen)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.shadow(10.dp, CircleShape, ambientColor = color, spotColor = color)) { CoverArt(row.photoUrl, row.name, size = 64, circle = true) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(row.name, color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${status.emoji} ${status.label.uppercase()}", color = if (mythic) Color.White else color, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = if (mythic) Modifier.clip(RoundedCornerShape(6.dp)).background(Brush.horizontalGradient(holo)).padding(horizontal = 6.dp, vertical = 2.dp) else Modifier
                )
                Text(
                    "🎵 ${certs.trackCerts.size} chanson${if (certs.trackCerts.size > 1) "s" else ""} certifiée${if (certs.trackCerts.size > 1) "s" else ""} · 💿 ${certs.albumCerts.size} album${if (certs.albumCerts.size > 1) "s" else ""} · ▶ ${formatCount(row.playCount)}",
                    color = theme.textSecondary, style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Statut obtenu le ${formatDate(row.s.statusDate)}" + if (row.s.reachedViaPlays) " · via les écoutes" else " · via les certifications",
                    color = theme.textSecondary, style = MaterialTheme.typography.labelSmall
                )
            }
        }
        if (progress != null) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = pantheonColor(progress.next), trackColor = theme.background)
            Text("${progress.next.emoji} ${progress.message}", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun SoonRow(a: ArtistEntity, p: NextStatusProgress, weekPlays: Map<Long, Int>, onOpen: () -> Unit) {
    val theme = Nova.theme
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(theme.surface).combinedClickableCompat(onOpen).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverArt(a.photoUrl, a.name, size = 40, circle = true)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(a.name, color = theme.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("▶ ${formatCount(a.playCount)} · ${(p.fraction * 100).toInt()} % vers ⭐ Star", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                val remaining = p.next.playsThreshold - a.playCount
                val daysInWeek = (java.time.temporal.ChronoUnit.DAYS.between(Dates.parse(Dates.statsRangeFor(Period.WEEKLY).fromIso), Dates.today()) + 1).coerceAtLeast(1)
                val pace = (weekPlays[a.artistId] ?: 0).toFloat() / daysInWeek
                val eta = if (pace > 0f) kotlin.math.ceil(remaining / pace).toInt() else null
                if (eta != null) Text("≈ $eta j · vers le ${Dates.today().plusDays(eta.toLong()).format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))}", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
        LinearProgressIndicator(progress = { p.fraction }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp).clip(RoundedCornerShape(2.dp)), color = pantheonColor(PantheonStatus.STAR), trackColor = theme.background)
        Text(p.message, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 2.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier = this.combinedClickable(onClick = onClick, onLongClick = onClick)

/** 👑 Frise de l'ascension : les 5 statuts avec seuils et compteurs. */
@Composable
private fun AscentFrieze(counts: Map<String, Int>) {
    val theme = Nova.theme
    NovaCard(Modifier.padding(horizontal = 16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text("👑 L'ascension", color = theme.text, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                PantheonStatus.entries.forEachIndexed { i, st ->
                    val c = pantheonColor(st)
                    if (i > 0) Text("→", color = theme.textSecondary)
                    Column(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = 0.14f))
                            .border(1.dp, c.copy(alpha = 0.45f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(st.emoji, fontSize = 20.sp)
                        Text(st.label, color = c, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        Text("${formatCount(st.playsThreshold)} ▶", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                        Text("×${counts[st.dbName] ?: 0}", color = theme.text, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text("Chaque statut s'obtient aussi par les certifications (titres + albums à chaque niveau). Un statut acquis ne se perd jamais.", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** 🏛️ Poids du Panthéon : part des écoutes all time portée par les artistes intronisés. */
@Composable
private fun WeightCard(rows: List<PantheonRow>, totalAllTime: Int) {
    val theme = Nova.theme
    if (totalAllTime <= 0 || rows.isEmpty()) return
    val weight = rows.sumOf { it.playCount.toLong() }
    val pct = ((weight * 100) / totalAllTime).toInt().coerceIn(0, 100)
    NovaCard(Modifier.padding(horizontal = 16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text("🏛️ Le poids de ton Panthéon", color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Text(
                "${rows.size} artiste${if (rows.size > 1) "s" else ""} intronisé${if (rows.size > 1) "s" else ""} — ${pct} % de toutes tes écoutes all time",
                color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp)
            )
            LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).padding(top = 6.dp), color = NovaColors.Gold, trackColor = theme.background)
        }
    }
}
