package com.novastats.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.novastats.app.ui.theme.prideFlagFor
import com.novastats.app.ui.theme.isPride
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.novastats.app.NovaStatsApp
import com.novastats.app.data.db.dao.CertCandidate
import com.novastats.app.data.db.dao.DayCount
import com.novastats.app.data.db.entity.CertificationHistoryEntity
import com.novastats.app.data.db.entity.EntityType
import com.novastats.app.domain.CertLevel
import com.novastats.app.domain.Certification
import com.novastats.app.domain.CertificationRules
import com.novastats.app.domain.Dates
import com.novastats.app.domain.Period
import com.novastats.app.ui.theme.Nova
import com.novastats.app.ui.theme.drawNovaCurve
import com.novastats.app.ui.theme.rememberCurveAnim
import java.time.format.DateTimeFormatter
import androidx.compose.runtime.produceState
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.offset
import java.util.Locale

/* Couleurs du cahier des charges */
fun certColor(level: CertLevel?): Color = when (level) {
    CertLevel.SILVER -> Color(0xFFC0C0C0)
    CertLevel.GOLD -> Color(0xFFFFD700)
    CertLevel.PLATINUM -> Color(0xFFE5E4E2)
    CertLevel.DIAMOND -> Color(0xFF00FFFF)
    null -> Color(0xFF888888)
}

private val longFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)

/** Libellé court pour l'axe X des courbes (« 3 oct. »). */
private val axisFmt = DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE)

/** Date ISO (`yyyy-MM-dd`) de `daily_plays` → libellé d'axe ; null si illisible. */
internal fun axisDate(iso: String?): String? = iso?.let { runCatching { Dates.parse(it).format(axisFmt) }.getOrNull() }

/** « depuis 3 jours / 2 semaines / 5 mois / 1 an ». */
fun sinceLabel(ms: Long, now: Long = System.currentTimeMillis()): String {
    val days = ((now - ms) / 86_400_000L).coerceAtLeast(0)
    return when {
        days < 1 -> "depuis aujourd'hui"
        days < 14 -> "depuis $days jour${if (days > 1) "s" else ""}"
        days < 60 -> "depuis ${days / 7} semaines"
        days < 365 -> "depuis ${days / 30} mois"
        else -> "depuis ${days / 365} an${if (days / 365 > 1) "s" else ""}"
    }
}

private fun CertCandidate.cert(): Certification? = level?.let { l -> CertLevel.entries.firstOrNull { it.dbName == l }?.let { Certification(it, multiplier ?: 1) } }

/**
 * 🏅 Certifications — Chansons / Albums, chips par palier avec compteurs, radar des 5 plus proches du prochain palier,
 * classement par niveau puis écoutes, recherche temps réel, fiche détaillée colorée selon le niveau.
 */
@Composable
fun CertificationsScreen() {
    val theme = Nova.theme
    val app = LocalContext.current.applicationContext as NovaStatsApp
    val db = app.database
    var tab by rememberSaveable { mutableStateOf(0) }
    var levelFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var detail by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var sortTracks by rememberSaveable { mutableStateOf("niveau") }
    var sortAlbums by rememberSaveable { mutableStateOf("niveau") }
    val sortMode = if (tab == 0) sortTracks else sortAlbums

    val type = if (tab == 0) EntityType.TRACK else EntityType.ALBUM
    val thresholds = if (tab == 0) CertificationRules.TRACK else CertificationRules.ALBUM
    val trackCands by app.database.certificationDao().trackCandidates().collectAsStateWithLifecycle(initialValue = emptyList())
    val albumCands by app.database.certificationDao().albumCandidates().collectAsStateWithLifecycle(initialValue = emptyList())
    val candidates = if (tab == 0) trackCands else albumCands

    val certified = remember(candidates) { candidates.filter { it.level != null } }
    val counts = remember(certified) { certified.groupingBy { it.level!! }.eachCount() }
    val q = query.trim().lowercase()
    val list = remember(certified, levelFilter, q, sortMode) {
        val base = certified.filter { (levelFilter == null || it.level == levelFilter) && (q.isEmpty() || it.name.lowercase().contains(q) || (it.subtitle ?: "").lowercase().contains(q)) }
        when (sortMode) {
            "recent" -> base.sortedByDescending { it.certifiedAt ?: 0L }
            "plays" -> base.sortedByDescending { it.playCount }
            "radar" -> base.sortedBy { thresholds.required(thresholds.next(it.playCount)) - it.playCount }
            else -> base.sortedWith(compareByDescending<CertCandidate> { it.cert()?.rank ?: 0 }.thenByDescending { it.playCount })
        }
    }
    // Radar : les 5 plus proches du prochain palier (sans limite de distance)
    val radar = remember(candidates, q) {
        candidates.filter { q.isEmpty() || it.name.lowercase().contains(q) }
            .map { c -> val next = thresholds.next(c.playCount); Triple(c, next, thresholds.required(next) - c.playCount) }
            .sortedWith(compareBy({ it.third }, { -it.first.playCount })).take(5)
    }
    val allMax = candidates.isNotEmpty() && candidates.all { it.level == CertLevel.DIAMOND.dbName }

    // 📡 Rythme de la semaine en cours (écoutes par entité) pour les dates estimées du radar
    val weekPlays by produceState<Map<Long, Int>>(initialValue = emptyMap(), key1 = tab) {
        val r = Dates.statsRangeFor(Period.WEEKLY)
        value = runCatching {
            if (tab == 0) db.trackDao().topForPeriod(r.fromIso, r.toIso, 500).first().associate { it.track.trackId to it.periodPlays }
            else db.albumDao().topForPeriod(r.fromIso, r.toIso, 500).first().associate { it.album.albumId to it.periodPlays }
        }.getOrDefault(emptyMap())
    }

    // 🎨 Art du titre le plus écouté (all time) en fond d'écran
    val artUrl by produceState<String?>(initialValue = null) {
        value = runCatching { db.trackDao().topAllTime(1).first().firstOrNull()?.track?.coverUrl }.getOrNull()
    }

    ScreenBackdrop(artUrl) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text("🏅 Certifications", style = MaterialTheme.typography.headlineSmall, color = theme.text, fontWeight = FontWeight.Bold)
                Text(
                    if (tab == 0) "Chansons : 🥉 25 · 🥈 50 · 🥇 100 · 💎 350 écoutes (+350 par Diamant)" else "Albums : 🥉 50 · 🥈 100 · 🥇 200 · 💎 700 écoutes (+700 par Diamant) — somme des titres",
                    color = theme.textSecondary, style = MaterialTheme.typography.bodySmall
                )
            }
            MedalShowcase(trackCands, albumCands)
            Spacer(Modifier.height(10.dp))
            TabRow(
                selectedTabIndex = tab, containerColor = theme.background, contentColor = theme.primary,
                indicator = { pos -> if (Nova.isPride) Box(Modifier.tabIndicatorOffset(pos[tab]).height(5.dp).background(Brush.horizontalGradient(prideFlagFor(tab + 1)))) else TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pos[tab]), color = theme.primary) }
            ) {
                listOf("🎵 Chansons", "💿 Albums").forEachIndexed { i, l ->
                    Tab(selected = tab == i, onClick = { tab = i; levelFilter = null }, text = { Text(l, color = if (tab == i) theme.primary else theme.textSecondary, fontWeight = FontWeight.SemiBold) })
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text(if (tab == 0) "🔍 Rechercher une chanson…" else "🔍 Rechercher un album…") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NovaFilterChip(flagKey = "all", selected = levelFilter == null, onClick = { levelFilter = null }, label = { Text("Tous (${certified.size})") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = theme.primary.copy(alpha = 0.25f), selectedLabelColor = theme.text))
                CertLevel.entries.forEach { l ->
                    val c = certColor(l)
                    NovaFilterChip(
                        flagKey = l, selected = levelFilter == l.dbName, onClick = { levelFilter = if (levelFilter == l.dbName) null else l.dbName },
                        label = { Text("${l.emoji} ${l.label} (${counts[l.dbName] ?: 0})") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.copy(alpha = 0.3f), selectedLabelColor = theme.text),
                        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = levelFilter == l.dbName, borderColor = c.copy(alpha = 0.5f), selectedBorderColor = c)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            CoverageCard(candidates, counts)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("niveau" to "🏅 Niveau", "recent" to "🕰️ Récentes", "plays" to "▶ Écoutes", "radar" to "📡 Proches du palier").forEach { (id, label) ->
                    NovaFilterChip(
                        selected = sortMode == id, onClick = { if (tab == 0) sortTracks = id else sortAlbums = id },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = theme.primary.copy(alpha = 0.25f), selectedLabelColor = theme.text)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        if (list.isEmpty()) item {
            EmptyState("🏅", if (certified.isEmpty()) "Aucune certification pour l'instant" else "Rien ici", if (certified.isEmpty()) "Première certification 🥉 Argent à ${thresholds.silver} écoutes — le radar ci-dessous montre les plus proches." else "Aucun résultat pour ce filtre.")
        }
        items(list, key = { it.entityId }) { c -> CertRow(c, type == EntityType.ALBUM) { detail = type to c.entityId } }
        // Radar en bas de la liste (demande utilisateur)
        item(key = "radar") {
            Spacer(Modifier.height(12.dp))
            RadarCard(radar, allMax, tab == 0, weekPlays) { detail = type to it }
            Spacer(Modifier.height(24.dp))
        }
    }
    }

    detail?.let { (t, id) -> CertificationPopup(t, id) { detail = null } }
}

@Composable
private fun RadarCard(radar: List<Triple<CertCandidate, Certification, Int>>, allMax: Boolean, tracks: Boolean, weekPlays: Map<Long, Int>, onOpen: (Long) -> Unit) {
    val theme = Nova.theme
    NovaCard(Modifier.padding(horizontal = 16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text("📡 Radar — prochains paliers", color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            if (allMax) Text("🏆 Incroyable ! Toute ta bibliothèque est certifiée ! Prochain défi : les multiplicateurs Diamant.", color = theme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            if (radar.isEmpty()) Text(if (tracks) "Aucune chanson écoutée pour l'instant." else "Aucun album écouté pour l'instant.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
            radar.forEach { (c, next, remaining) ->
                val color = certColor(next.level)
                val need = c.playCount + remaining
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).combinedClickableCompat { onOpen(c.entityId) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoverArt(c.imageUrl, c.name, size = 40)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, color = theme.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        Text("${c.subtitle ?: ""} · ${formatCount(c.playCount)} ▶", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        LinearProgressIndicator(progress = { (c.playCount.toFloat() / need).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp).clip(RoundedCornerShape(2.dp)), color = color, trackColor = theme.background)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(next.label(), color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        Text("−$remaining", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                        val playsWeek = weekPlays[c.entityId] ?: 0
                        val daysInWeek = (java.time.temporal.ChronoUnit.DAYS.between(Dates.parse(Dates.statsRangeFor(Period.WEEKLY).fromIso), Dates.today()) + 1).coerceAtLeast(1)
                        val pace = playsWeek.toFloat() / daysInWeek
                        val eta = if (pace > 0f) kotlin.math.ceil(remaining / pace).toInt() else null
                        Text(
                            if (eta != null) "≈ $eta j · vers le ${Dates.today().plusDays(eta.toLong()).format(axisFmt)}" else "rythme 0 cette semaine",
                            color = theme.textSecondary, style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CertRow(c: CertCandidate, album: Boolean, onOpen: () -> Unit) {
    val theme = Nova.theme
    val cert = c.cert() ?: return
    val color = certColor(cert.level)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp)).background(theme.surface)
            .border(1.dp, color.copy(alpha = if (cert.level == CertLevel.DIAMOND) 0.8f else 0.35f), RoundedCornerShape(12.dp))
            .combinedClickableCompat(onOpen).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(Modifier.shadow(if (cert.level == CertLevel.DIAMOND) 10.dp else 0.dp, RoundedCornerShape(8.dp), ambientColor = color, spotColor = color)) { CoverArt(c.imageUrl, c.name, size = 52) }
            // Médaille du niveau, posée sur le coin de la pochette
            MedalBadge(cert.level, cert.multiplier, size = 26.dp, modifier = Modifier.offset(x = 6.dp, y = 6.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, color = theme.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(c.subtitle ?: "", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            c.certifiedAt?.let { Text("Certifié ${cert.label()} le ${Dates.toLocalDate(it).format(longFmt)} · ${sinceLabel(it)}", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(cert.label(), color = color, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
            Text("${formatCount(c.playCount)} ▶", color = theme.text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier = this.combinedClickable(onClick = onClick, onLongClick = onClick)

/* ============================ FICHE CERTIFICATION ============================ */

private data class CertDetail(
    val name: String, val subtitle: String, val imageUrl: String?, val playCount: Int, val firstPlayedAt: Long?,
    val history: List<CertificationHistoryEntity>, val series: List<DayCount>,
    val statsRanks: Map<Period, Int?>, val billboardRanks: Map<Period, Int?>,
    /** Répartition par version (original + versions liées) — vide si le titre n'a pas de version. */
    val versions: List<Pair<String, Int>> = emptyList()
)

@Composable
private fun CertificationPopup(entityType: String, id: Long, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as NovaStatsApp
    val db = app.database
    val theme = Nova.theme
    val thresholds = if (entityType == EntityType.TRACK) CertificationRules.TRACK else CertificationRules.ALBUM
    var d by remember { mutableStateOf<CertDetail?>(null) }

    LaunchedEffect(id) {
        val history = db.certificationDao().history(id, entityType)
        val stats = Period.entries.associateWith { p ->
            val r = Dates.statsRangeFor(p)
            if (entityType == EntityType.TRACK) (if (p == Period.GLOBAL) db.trackDao().rankAllTime(id) else db.trackDao().rankForPeriod(id, r.fromIso, r.toIso))
            else db.albumDao().rankForPeriod(id, r.fromIso, r.toIso)
        }
        val billboard = Period.entries.associateWith { p ->
            val h = if (entityType == EntityType.TRACK) db.billboardDao().trackHistory(p.dbName, id) else db.billboardDao().albumHistory(p.dbName, id)
            h.lastOrNull()?.position
        }
        d = if (entityType == EntityType.TRACK) {
            val t = db.trackDao().getById(id) ?: return@LaunchedEffect
            val ids = db.trackLinkDao().artistIdsForTrack(id).ifEmpty { listOf(t.artistId) }
            val artists = ids.mapNotNull { db.artistDao().getById(it)?.name }.joinToString(", ")
            val linked = db.trackDao().versionsOf(id)
            val versions = if (linked.isEmpty()) emptyList() else listOf("Original" to db.trackDao().ownPlays(id)) + linked.map { v -> v.title.removePrefix(t.title).trim().trim('(', ')').ifBlank { v.title } to v.playCount }
            CertDetail(t.title, artists.ifBlank { db.artistDao().getById(t.artistId)?.name ?: "" }, t.coverUrl, t.playCount, t.firstPlayedAt, history, db.dailyPlayDao().seriesForTrack(id), stats, billboard, versions)
        } else {
            val al = db.albumDao().getById(id) ?: return@LaunchedEffect
            CertDetail(al.title, al.artistId?.let { db.artistDao().getById(it)?.name } ?: "Artistes variés", al.coverUrl, al.playCount, al.firstPlayedAt, history, db.dailyPlayDao().seriesForAlbum(id), stats, billboard)
        }
    }
    val det = d
    val current = det?.let { thresholds.current(it.playCount) }
    val color = certColor(current?.level)
    val diamond = current?.level == CertLevel.DIAMOND
    val glow by rememberInfiniteTransition(label = "dg").animateFloat(0.4f, 1f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "dga")

    // Bordure / dégradé selon le niveau (pas le thème) : Argent · Or · Platine · Diamant + glow
    val glowDp = when (current?.level) { CertLevel.DIAMOND -> 32; CertLevel.PLATINUM -> 20; CertLevel.GOLD -> 14; CertLevel.SILVER -> 8; null -> 4 }
    NovaPopupCard(
        borderColor = if (diamond) color.copy(alpha = 0.6f + 0.4f * glow) else color, onDismiss = onDismiss, glowDp = glowDp, backdropUrl = det?.imageUrl,
        banner = {
            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                BlurredBackdrop(det?.imageUrl, color, Modifier.fillMaxSize())
                Box(Modifier.size(130.dp).shadow(if (diamond) 28.dp else 14.dp, RoundedCornerShape(12.dp), ambientColor = color, spotColor = color).border(3.dp, color, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))) {
                    CoverArt(det?.imageUrl, det?.name ?: "?", size = 130, zoomable = true)
                }
            }
        }
    ) {
        if (det == null) { Text("Chargement…", color = theme.textSecondary); return@NovaPopupCard }
        Text(det.name, color = theme.text, fontWeight = FontWeight.Black, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(det.subtitle, color = theme.primary, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        // Niveau actuel en grand avec emoji
        Text(current?.level?.emoji ?: "🎯", fontSize = 40.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(current?.label()?.uppercase() ?: "PAS ENCORE CERTIFIÉ", color = color, fontWeight = FontWeight.Black, fontSize = 28.sp, letterSpacing = 2.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text("${formatCount(det.playCount)} écoutes", color = theme.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (det.versions.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "🔗 Ce total inclut ${det.versions.size - 1} version${if (det.versions.size > 2) "s" else ""} liée${if (det.versions.size > 2) "s" else ""} à ce titre (remix featuring / version avec invité) : " +
                    det.versions.joinToString(" · ") { (l, n) -> "$l ${formatCount(n)} ▶" },
                color = theme.accent, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        }

        // Progression vers le prochain palier
        val next = thresholds.next(det.playCount)
        val need = thresholds.required(next)
        val base = current?.let { thresholds.required(it) } ?: 0
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(progress = { ((det.playCount - base).toFloat() / (need - base).coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = certColor(next.level), trackColor = theme.background)
        Text("${need - det.playCount} écoute${if (need - det.playCount > 1) "s" else ""} avant ${next.label()} ($need)", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))

        SectionLabel("📜 Frise des paliers")
        if (det.history.isEmpty()) Text("Aucun palier atteint pour l'instant.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
        else Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.clip(RoundedCornerShape(10.dp)).background(theme.surface).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🎧", fontSize = 18.sp)
                Text("1ʳ écoute", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
            }
            det.history.forEachIndexed { i, h ->
                val lvl = CertLevel.entries.firstOrNull { it.dbName == h.level }
                val c = certColor(lvl)
                val stepMs = if (i == 0) h.timeToCertifyMs else h.certifiedAt - det.history[i - 1].certifiedAt
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 2.dp)) {
                    Text("→", color = theme.textSecondary)
                    Text(formatElapsed(stepMs, h.certifiedAt), color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                }
                Column(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(c.copy(alpha = 0.15f)).border(1.dp, c.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(lvl?.emoji ?: "🏅", fontSize = 18.sp)
                    Text(lvl?.let { Certification(it, h.multiplier).label() } ?: h.level, color = c, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    Text(Dates.toLocalDate(h.certifiedAt).format(axisFmt), color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        SectionLabel("📈 Courbe d'écoutes (cumul)")
        val cumul = remember(det.series) { var acc = 0; det.series.map { acc += it.playCount; acc.toFloat() } }
        /* Deux lignes seulement : le palier le plus haut atteint et le prochain. Dessiner tous les paliers
         * de l'historique les empilait en bas du graphique (25 / 100 / 500 face à un cumul de plusieurs
         * milliers) et leurs libellés se recouvraient. Les autres paliers sont listés au-dessus. */
        val levelLines = remember(det.history, next) {
            // Le palier le plus haut atteint, avec son multiplicateur réel (un Diamant 3× n'est pas au seuil du Diamant 1×).
            val reached = det.history
                .mapNotNull { h -> CertLevel.entries.firstOrNull { it.dbName == h.level }?.let { h to it } }
                .maxByOrNull { (h, lvl) -> lvl.ordinal * 100 + h.multiplier }
            listOfNotNull(reached?.let { (h, lvl) -> thresholds.required(Certification(lvl, h.multiplier)).toFloat() to certColor(lvl) }) +
                (need.toFloat() to certColor(next.level))
        }
        val axisDates = remember(det.series) { det.series.map { axisDate(it.date) } }
        if (cumul.isEmpty()) Text("Pas encore de données.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
        else NovaCurveChart(cumul, Modifier.fillMaxWidth().height(140.dp), color = color, thresholds = levelLines, xLabels = axisDates)

        SectionLabel("📊 Positions Stats")
        RanksRow(det.statsRanks)
        SectionLabel("🏆 Positions Billboard (dernier classement)")
        RanksRow(det.billboardRanks)
        Spacer(Modifier.height(4.dp))
        Text("Première écoute : ${formatDate(det.firstPlayedAt)}", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Nova.theme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
private fun RanksRow(ranks: Map<Period, Int?>) {
    val theme = Nova.theme
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Period.entries.forEach { p ->
            val r = ranks[p]
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(p.label, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                Text(positionLabel(r), fontWeight = FontWeight.Bold, color = when (r) { null -> theme.textSecondary.copy(alpha = 0.6f); 1 -> Color(0xFFFFD700); else -> theme.text })
            }
        }
    }
}

/** 🏅 Vitrine globale (chansons + albums) : compteurs par niveau + prochaine certification. */
@Composable
private fun MedalShowcase(tracks: List<CertCandidate>, albums: List<CertCandidate>) {
    val theme = Nova.theme
    val all = tracks + albums
    val counts = CertLevel.entries.associateWith { l -> all.count { it.level == l.dbName } }
    val total = all.count { it.level != null }
    var nextLine: String? = null
    var best = Long.MAX_VALUE
    for ((cands, rules) in listOf(tracks to CertificationRules.TRACK, albums to CertificationRules.ALBUM)) {
        for (e in cands) {
            val rem = rules.required(rules.next(e.playCount)) - e.playCount
            if (rem < best) { best = rem.toLong(); nextLine = "Prochaine médaille : « ${e.name} », à $rem écoute${if (rem > 1) "s" else ""} de ${rules.next(e.playCount).label()}." }
        }
    }
    NovaCard(Modifier.padding(horizontal = 16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text("🏅 Ma vitrine — chansons + albums", color = theme.text, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CertLevel.entries.forEach { l ->
                    val c = certColor(l)
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = 0.14f))
                            .border(1.dp, c.copy(alpha = 0.45f), RoundedCornerShape(12.dp)).padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(l.emoji, fontSize = 22.sp)
                        Text("${counts[l] ?: 0}", color = c, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Text(l.label, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (total == 0) "Aucune certification pour l'instant — première médaille 🥉 à 25 ▶ (chanson) ou 50 ▶ (album)."
                else nextLine ?: "🏆 Toute ta bibliothèque est au sommet !",
                color = theme.textSecondary, style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/** 📚 Couverture de la bibliothèque de l'onglet : % certifié + jauges par niveau + palier symbolique. */
@Composable
private fun CoverageCard(candidates: List<CertCandidate>, counts: Map<String, Int>) {
    val theme = Nova.theme
    if (candidates.isEmpty()) return
    val total = candidates.size
    val certified = candidates.count { it.level != null }
    val pct = (certified * 100) / total
    val target = CertLevel.entries.firstOrNull { l -> (counts[l.dbName] ?: 0) < 10 }
    NovaCard(Modifier.padding(horizontal = 16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text("📚 Couverture de ta bibliothèque", color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Text("$pct % de tes éléments sont certifiés ($certified / $total)", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
            CertLevel.entries.forEach { l ->
                val n = counts[l.dbName] ?: 0
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(l.emoji, fontSize = 14.sp)
                    Text("${l.label} · $n", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    Text("${(n * 100) / total} %", color = certColor(l), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                }
                LinearProgressIndicator(progress = { (n.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = certColor(l), trackColor = theme.background)
            }
            target?.let { l ->
                Spacer(Modifier.height(8.dp))
                Text("🎯 Encore ${10 - (counts[l.dbName] ?: 0)} certification${if (10 - (counts[l.dbName] ?: 0) > 1) "s" else ""} pour atteindre 10 ${l.label} ${l.emoji}", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
