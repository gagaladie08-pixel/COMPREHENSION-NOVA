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
import androidx.compose.material3.FilterChip
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
    var tab by rememberSaveable { mutableStateOf(0) }
    var levelFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var detail by remember { mutableStateOf<Pair<String, Long>?>(null) }

    val type = if (tab == 0) EntityType.TRACK else EntityType.ALBUM
    val thresholds = if (tab == 0) CertificationRules.TRACK else CertificationRules.ALBUM
    val candidates by remember(tab) { if (tab == 0) app.database.certificationDao().trackCandidates() else app.database.certificationDao().albumCandidates() }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val certified = remember(candidates) { candidates.filter { it.level != null } }
    val counts = remember(certified) { certified.groupingBy { it.level!! }.eachCount() }
    val q = query.trim().lowercase()
    val list = remember(certified, levelFilter, q) {
        certified.filter { (levelFilter == null || it.level == levelFilter) && (q.isEmpty() || it.name.lowercase().contains(q) || (it.subtitle ?: "").lowercase().contains(q)) }
            .sortedWith(compareByDescending<CertCandidate> { it.cert()?.rank ?: 0 }.thenByDescending { it.playCount })
    }
    // Radar : les 5 plus proches du prochain palier (sans limite de distance)
    val radar = remember(candidates, q) {
        candidates.filter { q.isEmpty() || it.name.lowercase().contains(q) }
            .map { c -> val next = thresholds.next(c.playCount); Triple(c, next, thresholds.required(next) - c.playCount) }
            .sortedWith(compareBy({ it.third }, { -it.first.playCount })).take(5)
    }
    val allMax = candidates.isNotEmpty() && candidates.all { it.level == CertLevel.DIAMOND.dbName }

    LazyColumn(Modifier.fillMaxSize().background(theme.background)) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text("🏅 Certifications", style = MaterialTheme.typography.headlineSmall, color = theme.text, fontWeight = FontWeight.Bold)
                Text(
                    if (tab == 0) "Chansons : 🥉 25 · 🥈 50 · 🥇 100 · 💎 350 écoutes (+350 par Diamant)" else "Albums : 🥉 50 · 🥈 100 · 🥇 200 · 💎 700 écoutes (+700 par Diamant) — somme des titres",
                    color = theme.textSecondary, style = MaterialTheme.typography.bodySmall
                )
            }
            TabRow(
                selectedTabIndex = tab, containerColor = theme.background, contentColor = theme.primary,
                indicator = { pos -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pos[tab]), color = theme.primary) }
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
            RadarCard(radar, allMax, tab == 0) { detail = type to it }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = levelFilter == null, onClick = { levelFilter = null }, label = { Text("Tous (${certified.size})") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = theme.primary.copy(alpha = 0.25f), selectedLabelColor = theme.text))
                CertLevel.entries.forEach { l ->
                    val c = certColor(l)
                    FilterChip(
                        selected = levelFilter == l.dbName, onClick = { levelFilter = if (levelFilter == l.dbName) null else l.dbName },
                        label = { Text("${l.emoji} ${l.label} (${counts[l.dbName] ?: 0})") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.copy(alpha = 0.3f), selectedLabelColor = theme.text),
                        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = levelFilter == l.dbName, borderColor = c.copy(alpha = 0.5f), selectedBorderColor = c)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        if (list.isEmpty()) item {
            EmptyState("🏅", if (certified.isEmpty()) "Aucune certification pour l'instant" else "Rien ici", if (certified.isEmpty()) "Première certification 🥉 Argent à ${thresholds.silver} écoutes — le radar ci-dessus montre les plus proches." else "Aucun résultat pour ce filtre.")
        }
        items(list, key = { it.entityId }) { c -> CertRow(c, type == EntityType.ALBUM) { detail = type to c.entityId } }
        item { Spacer(Modifier.height(24.dp)) }
    }
    detail?.let { (t, id) -> CertificationPopup(t, id) { detail = null } }
}

@Composable
private fun RadarCard(radar: List<Triple<CertCandidate, Certification, Int>>, allMax: Boolean, tracks: Boolean, onOpen: (Long) -> Unit) {
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
        Box(Modifier.shadow(if (cert.level == CertLevel.DIAMOND) 10.dp else 0.dp, RoundedCornerShape(8.dp), ambientColor = color, spotColor = color)) { CoverArt(c.imageUrl, c.name, size = 52) }
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
    val statsRanks: Map<Period, Int?>, val billboardRanks: Map<Period, Int?>
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
            CertDetail(t.title, db.artistDao().getById(t.artistId)?.name ?: "", t.coverUrl, t.playCount, t.firstPlayedAt, history, db.dailyPlayDao().seriesForTrack(id), stats, billboard)
        } else {
            val al = db.albumDao().getById(id) ?: return@LaunchedEffect
            CertDetail(al.title, db.artistDao().getById(al.artistId)?.name ?: "", al.coverUrl, al.playCount, al.firstPlayedAt, history, db.dailyPlayDao().seriesForAlbum(id), stats, billboard)
        }
    }
    val det = d
    val current = det?.let { thresholds.current(it.playCount) }
    val color = certColor(current?.level)
    val diamond = current?.level == CertLevel.DIAMOND
    val glow by rememberInfiniteTransition(label = "dg").animateFloat(0.4f, 1f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "dga")

    PopupScaffold(
        borderColor = if (diamond) color.copy(alpha = glow) else color, onDismiss = onDismiss,
        banner = {
            Box(Modifier.fillMaxWidth().height(180.dp).background(Brush.verticalGradient(listOf(color.copy(alpha = 0.45f), theme.surface))), contentAlignment = Alignment.Center) {
                if (det?.imageUrl != null) AsyncImage(model = det.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(24.dp).alpha(0.5f))
                Box(Modifier.size(130.dp).shadow(if (diamond) 28.dp else 14.dp, RoundedCornerShape(12.dp), ambientColor = color, spotColor = color).border(3.dp, color, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))) {
                    CoverArt(det?.imageUrl, det?.name ?: "?", size = 130)
                }
            }
        }
    ) {
        if (det == null) { Text("Chargement…", color = theme.textSecondary); return@PopupScaffold }
        Text(det.name, color = theme.text, fontWeight = FontWeight.Black, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(det.subtitle, color = theme.primary, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text(current?.label() ?: "Pas encore certifié", color = color, fontWeight = FontWeight.Black, fontSize = 28.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text("${formatCount(det.playCount)} écoutes", color = theme.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

        // Progression vers le prochain palier
        val next = thresholds.next(det.playCount)
        val need = thresholds.required(next)
        val base = current?.let { thresholds.required(it) } ?: 0
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(progress = { ((det.playCount - base).toFloat() / (need - base).coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = certColor(next.level), trackColor = theme.background)
        Text("${need - det.playCount} écoute${if (need - det.playCount > 1) "s" else ""} avant ${next.label()} ($need)", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))

        SectionLabel("📜 Historique des paliers")
        if (det.history.isEmpty()) Text("Aucun palier atteint pour l'instant.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
        det.history.forEach { h ->
            val lvl = CertLevel.entries.firstOrNull { it.dbName == h.level }
            val label = lvl?.let { Certification(it, h.multiplier).label() } ?: h.level
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, color = certColor(lvl), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text("le ${Dates.toLocalDate(h.certifiedAt).format(longFmt)} · ${sinceLabel(h.certifiedAt)}", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }

        SectionLabel("📈 Courbe d'écoutes (cumul)")
        PlaysCurve(det.series, color, det.history, thresholds, Modifier.fillMaxWidth().height(120.dp))

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

/** Courbe cumulée des écoutes avec lignes horizontales aux seuils déjà franchis / suivant. */
@Composable
private fun PlaysCurve(series: List<DayCount>, color: Color, history: List<CertificationHistoryEntity>, thresholds: CertificationRules.Thresholds, modifier: Modifier) {
    val theme = Nova.theme
    val textColor = theme.textSecondary
    val cumul = remember(series) { var acc = 0; series.map { acc += it.playCount; acc } }
    if (cumul.isEmpty()) { Text("Pas encore de données.", color = textColor, style = MaterialTheme.typography.bodySmall); return }
    val total = cumul.last()
    val next = thresholds.required(thresholds.next(total))
    val maxY = maxOf(total, next).toFloat()
    val levels = remember(history, next) { history.map { h -> CertLevel.entries.firstOrNull { it.dbName == h.level }?.let { thresholds.required(Certification(it, h.multiplier)) to certColor(it) } }.filterNotNull() }
    val anim = rememberCurveAnim(theme, series.size)
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val padL = 34.dp.toPx(); val padT = 6.dp.toPx(); val padB = 6.dp.toPx()
        val plotW = w - padL; val plotH = h - padT - padB
        fun x(i: Int) = padL + if (cumul.size == 1) plotW / 2 else plotW * i / (cumul.size - 1)
        fun y(v: Float) = padT + plotH * (1 - v / maxY)
        val paint = android.graphics.Paint().apply {
            this.color = android.graphics.Color.argb(200, (textColor.red * 255).toInt(), (textColor.green * 255).toInt(), (textColor.blue * 255).toInt())
            textSize = 9.sp.toPx(); isAntiAlias = true
        }
        (levels + (next to certColor(thresholds.next(total).level))).forEach { (v, c) ->
            drawLine(c.copy(alpha = 0.5f), Offset(padL, y(v.toFloat())), Offset(w, y(v.toFloat())), strokeWidth = 1.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText("$v", 0f, y(v.toFloat()) + 3.dp.toPx(), paint)
        }
        val pts = cumul.mapIndexed { i, v -> Offset(x(i), y(v.toFloat())) }
        // Courbe thématisée (trait = couleur du palier, style / glow / décor = thème)
        drawNovaCurve(theme, pts, baselineY = padT + plotH, anim = anim, strokeOverride = color)
        drawCircle(color, radius = 4.dp.toPx(), center = pts.last())
    }
}
