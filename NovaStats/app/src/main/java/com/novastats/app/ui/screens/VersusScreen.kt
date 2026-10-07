package com.novastats.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.novastats.app.NovaStatsApp
import com.novastats.app.data.db.NovaDatabase
import com.novastats.app.data.db.entity.ScrobbleEntity
import com.novastats.app.domain.CertLevel
import com.novastats.app.ui.theme.Nova
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/* ==================================================================== */
/*  ⚔️ Comparer — 20 critères, du plus simple au plus pointu              */
/* ==================================================================== */

private const val DAY_MS = 24 * 60 * 60 * 1000L

private data class VsPick(val id: Long, val name: String, val subtitle: String?, val imageUrl: String?)

/** Toutes les mesures d'un camp, calculées à partir de ses écoutes. */
private data class VsStats(
    val plays: Int = 0,
    val durationMs: Long = 0,
    val activeDays: Int = 0,
    val firstAt: Long = 0L,
    val lastAt: Long = 0L,
    val hours: List<Int> = List(24) { 0 },
    val bestHour: Int = -1,
    val peakHourCount: Int = 0,
    val bestDay: Int = 0,
    val days3Plus: Int = 0,
    val maxStreak: Int = 0,
    val months: Int = 0,
    val night: Int = 0,
    val morning: Int = 0,
    val afternoon: Int = 0,
    val evening: Int = 0,
    val weekend: Int = 0,
    val longestMs: Long = 0,
    val certLabel: String = "—",
    val certScore: Float = 0f
) {
    val perDay: Float get() = if (activeDays > 0) plays.toFloat() / activeDays else 0f
    val perMonth: Float get() = if (months > 0) plays.toFloat() / months else 0f
    val spanDays: Int get() = if (firstAt > 0 && lastAt >= firstAt) ((lastAt - firstAt) / DAY_MS).toInt() + 1 else 0
    /** Écoutes par jour écoulé depuis la première : favorise les titres récents et explosifs. */
    val growth: Float get() {
        if (firstAt <= 0L) return 0f
        val days = ((System.currentTimeMillis() - firstAt) / DAY_MS).toInt() + 1
        return plays.toFloat() / days.coerceAtLeast(1)
    }
    val peakHourLabel: String get() = if (bestHour < 0) "—" else String.format(Locale.FRANCE, "%02d h", bestHour)
}

/** Un critère : 0 = les bases, 1 = la régularité, 2 = les moments, 3 = l'endurance. */
private data class VsRow(val tier: Int, val label: String, val aText: String, val bText: String, val aWin: Boolean?, val aShare: Float)

private val TIER_LABELS = listOf("🥉 Les bases", "🥈 La régularité", "🌙 Les moments", "💎 L'endurance")
private val TIER_COLORS = listOf(0xFFC0C0C0L, 0xFFFFD700L, 0xFF7C6BFFL, 0xFF4FF0FFFFL)

private val VS_DATE_FMT = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)

@Composable
fun VersusScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as NovaStatsApp
    val db = app.database
    val theme = Nova.theme

    var mode by remember { mutableIntStateOf(0) } // 0 = titres, 1 = artistes
    var pickA by remember { mutableStateOf<VsPick?>(null) }
    var pickB by remember { mutableStateOf<VsPick?>(null) }
    var side by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<VsPick>>(emptyList()) }

    LaunchedEffect(query, mode, side) {
        if (side == 0) { results = emptyList(); return@LaunchedEffect }
        results = withContext(Dispatchers.IO) {
            runCatching {
                if (mode == 0) {
                    val list = if (query.isBlank()) db.trackDao().topAllTime(20).first() else db.trackDao().search(query, 25)
                    list.map { VsPick(it.track.trackId, it.track.title, it.artistName, it.track.coverUrl) }
                } else {
                    if (query.isBlank()) db.artistDao().topAllTime(20).first().map { VsPick(it.artist.artistId, it.artist.name, null, it.artist.photoUrl) }
                    else db.artistDao().search(query, 25).map { VsPick(it.artistId, it.name, null, it.photoUrl) }
                }
            }.getOrDefault(emptyList())
        }
    }

    var statsA by remember { mutableStateOf<VsStats?>(null) }
    var statsB by remember { mutableStateOf<VsStats?>(null) }
    LaunchedEffect(pickA?.id, pickB?.id, mode) {
        val a = pickA; val b = pickB
        statsA = null; statsB = null
        if (a == null && b == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            if (a != null) statsA = runCatching { loadStats(db, a.id, mode) }.getOrDefault(VsStats())
            if (b != null) statsB = runCatching { loadStats(db, b.id, mode) }.getOrDefault(VsStats())
        }
    }

    val artUrl by produceState<String?>(null, pickA?.imageUrl, pickB?.imageUrl) {
        value = runCatching {
            pickA?.imageUrl ?: pickB?.imageUrl ?: db.trackDao().topAllTime(1).first().firstOrNull()?.track?.coverUrl
        }.getOrNull()
    }

    ScreenBackdrop(artUrl) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Stats", color = theme.primary, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.weight(1f))
                Text("⚔️ Comparer", color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(12.dp))
            }

            Appear(delay = 0) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ModeChip("🎵 Deux titres", mode == 0, Modifier.weight(1f)) { mode = 0; pickA = null; pickB = null; statsA = null; statsB = null; side = 0; query = "" }
                    ModeChip("🎤 Deux artistes", mode == 1, Modifier.weight(1f)) { mode = 1; pickA = null; pickB = null; statsA = null; statsB = null; side = 0; query = "" }
                }
            }

            Appear(delay = 60) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    FighterCard(pickA, "Choisir à gauche", theme.primary, Modifier.weight(1f)) { side = if (side == 1) 0 else 1 }
                    Box(Modifier.width(46.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(42.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(theme.primary, theme.secondary, theme.accent))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("VS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 0.5.sp)
                        }
                    }
                    FighterCard(pickB, "Choisir à droite", theme.secondary, Modifier.weight(1f)) { side = if (side == 2) 0 else 2 }
                }
            }

            if (side != 0) {
                Appear(delay = 90) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        SectionTitle(if (side == 1) "🔍 Choisir le camp de gauche" else "🔍 Choisir le camp de droite")
                        GlassCard {
                            Column(Modifier.padding(14.dp)) {
                                OutlinedTextField(
                                    value = query, onValueChange = { query = it }, singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text(if (mode == 0) "Rechercher un titre…" else "Rechercher un artiste…", color = theme.textSecondary) },
                                    trailingIcon = {
                                        if (query.isNotEmpty()) Text(
                                            "✕", color = theme.textSecondary, fontSize = 16.sp,
                                            modifier = Modifier.clickable { query = "" }.padding(4.dp)
                                        )
                                    },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = theme.text),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = theme.primary,
                                        unfocusedBorderColor = theme.textSecondary.copy(alpha = 0.4f),
                                        cursorColor = theme.primary
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Spacer(Modifier.height(10.dp))
                                if (results.isEmpty()) {
                                    Text("Aucun résultat.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                                } else {
                                    results.take(12).forEach { r ->
                                        Row(
                                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                                .clickable { if (side == 1) pickA = r else pickB = r; side = 0; query = "" }
                                                .padding(vertical = 8.dp, horizontal = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                Modifier.size(38.dp).clip(RoundedCornerShape(8.dp))
                                                    .background(Brush.linearGradient(listOf(theme.primary.copy(alpha = 0.5f), theme.glowSecondary.copy(alpha = 0.5f)))),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (r.imageUrl != null) AsyncImage(model = r.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
                                            }
                                            Spacer(Modifier.width(10.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(r.name, color = theme.text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                                if (!r.subtitle.isNullOrBlank()) Text(r.subtitle, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                            }
                                            Text("›", color = theme.primary, fontSize = 20.sp)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Button(
                                    onClick = { side = 0; query = "" }, modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = theme.surface, contentColor = theme.text)
                                ) { Text("Fermer") }
                            }
                        }
                    }
                }
            }

            val a = statsA
            val b = statsB
            if (pickA != null && pickB != null && a != null && b != null) {
                val rows = remember(a, b) { buildRows(a, b) }
                val winsA = rows.count { it.aWin == true }
                val winsB = rows.count { it.aWin == false }
                val leader = when {
                    winsA == winsB -> null
                    winsA > winsB -> pickA?.name
                    else -> pickB?.name
                }

                /* ---------- Verdict ---------- */
                Appear(delay = 120) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        SectionTitle("🏆 Verdict — 20 critères")
                        GlassCard(glow = if (leader == null) theme.accent else if (winsA > winsB) theme.primary else theme.secondary) {
                            Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CountUp(
                                        winsA,
                                        MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Black, fontSize = 46.sp, letterSpacing = (-2).sp),
                                        if (winsA >= winsB) theme.primary else theme.textSecondary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text("—", color = theme.textSecondary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                                    CountUp(
                                        winsB,
                                        MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Black, fontSize = 46.sp, letterSpacing = (-2).sp),
                                        if (winsB >= winsA) theme.secondary else theme.textSecondary,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    if (leader == null) "Égalité parfaite" else "🏆 $leader mène",
                                    color = theme.text, fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    when {
                                        leader == null -> "$winsA partout : impossible de les départager."
                                        kotlin.math.abs(winsA - winsB) <= 1 -> "Photo-finish ! Un seul critère les sépare."
                                        else -> "Victoire nette sur 20 critères."
                                    },
                                    color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                /* ---------- Scores par famille ---------- */
                Appear(delay = 160) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        GlassCard {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                TIER_LABELS.forEachIndexed { tier, label ->
                                    val ta = rows.count { it.tier == tier && it.aWin == true }
                                    val tb = rows.count { it.tier == tier && it.aWin == false }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "$ta", color = if (ta > tb) theme.primary else theme.textSecondary,
                                            fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.width(28.dp), textAlign = TextAlign.Start
                                        )
                                        Text(label, color = theme.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                        Text(
                                            "$tb", color = if (tb > ta) theme.secondary else theme.textSecondary,
                                            fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.width(28.dp), textAlign = TextAlign.End
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                /* ---------- Les 20 critères, famille par famille ---------- */
                TIER_LABELS.forEachIndexed { tier, label ->
                    Appear(delay = 200 + tier * 60) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            SectionTitle(label)
                            GlassCard(glow = Color(TIER_COLORS[tier])) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                                    rows.filter { it.tier == tier }.forEachIndexed { i, row ->
                                        DuelRow(row, index = i)
                                    }
                                }
                            }
                        }
                    }
                }

                /* ---------- Courbes d'heures ---------- */
                Appear(delay = 460) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        SectionTitle("🕐 Répartition sur la journée")
                        GlassCard {
                            Column(Modifier.padding(18.dp)) {
                                Row(Modifier.fillMaxWidth()) {
                                    Column(Modifier.weight(1f)) {
                                        Text(pickA?.name ?: "", color = theme.primary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                        HourChart(a.hours, theme.primary, Modifier.fillMaxWidth())
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(pickB?.name ?: "", color = theme.secondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                        HourChart(b.hours, theme.secondary, Modifier.fillMaxWidth())
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "Pic de ${pickA?.name ?: "gauche"} : ${a.peakHourLabel} · Pic de ${pickB?.name ?: "droite"} : ${b.peakHourLabel}",
                                    color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                Appear(delay = 500) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { pickA = null; pickB = null; statsA = null; statsB = null },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = theme.surface, contentColor = theme.text)
                            ) { Text("🔄 Nouveau duel") }
                            Button(
                                onClick = {
                                    val t = pickA; pickA = pickB; pickB = t
                                    val s = statsA; statsA = statsB; statsB = s
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = theme.surface, contentColor = theme.text)
                            ) { Text("⇄ Inverser") }
                        }
                    }
                }
            } else {
                Appear(delay = 120) {
                    Column(Modifier.padding(horizontal = 28.dp, vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⚔️", fontSize = 46.sp)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Choisis deux " + (if (mode == 0) "titres" else "artistes") + " : NovaStats les départage sur 20 critères — du plus évident au plus pointu — pour que chacun ait sa chance.",
                            color = theme.textSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/* ============================== briques ============================== */

@Composable
private fun ModeChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val theme = Nova.theme
    Box(
        modifier.clip(CircleShape)
            .background(
                if (selected) Brush.horizontalGradient(listOf(theme.primary, theme.secondary))
                else Brush.horizontalGradient(listOf(theme.surface.copy(alpha = 0.9f), theme.surface.copy(alpha = 0.7f)))
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (selected) theme.background else theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FighterCard(pick: VsPick?, placeholder: String, glow: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val theme = Nova.theme
    GlassCard(modifier = modifier, glow = glow) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(if (pick == null) 64.dp else 88.dp).clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(glow.copy(alpha = 0.6f), theme.glowSecondary.copy(alpha = 0.5f)))),
                contentAlignment = Alignment.Center
            ) {
                if (pick?.imageUrl != null) AsyncImage(model = pick.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
                else Text(if (pick == null) "＋" else "♪", color = Color.White.copy(alpha = 0.9f), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                pick?.name ?: placeholder, color = theme.text, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, maxLines = 2
            )
            if (pick?.subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(pick.subtitle, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun DuelRow(row: VsRow, index: Int) {
    val theme = Nova.theme
    val tierColor = Color(TIER_COLORS[row.tier])
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay((120L + index * 90L).coerceAtMost(900L)); shown = true }
    val target = if (shown) row.aShare else 0.5f
    val anim by animateFloatAsState(target, tween(1000, easing = FastOutSlowInEasing), label = "duel")
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (row.aWin == true) "🏅 " else "") + row.aText,
                color = if (row.aWin == true) tierColor else theme.textSecondary,
                fontWeight = if (row.aWin == true) FontWeight.Black else FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Start, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(row.label, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, letterSpacing = 0.8.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.width(8.dp))
            Text(
                row.bText + (if (row.aWin == false) " 🏅" else ""),
                color = if (row.aWin == false) tierColor else theme.textSecondary,
                fontWeight = if (row.aWin == false) FontWeight.Black else FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End, modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(7.dp))
        Box(Modifier.fillMaxWidth().height(9.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))) {
            Box(
                Modifier.fillMaxWidth(anim.coerceIn(0.02f, 0.98f)).height(9.dp).clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(theme.primary, tierColor)))
            )
        }
    }
}

/* ============================== données ============================== */

private suspend fun loadStats(db: NovaDatabase, id: Long, mode: Int): VsStats {
    val list = if (mode == 0) db.scrobbleDao().allOfTrack(id) else db.scrobbleDao().allOfArtist(id)
    val (label, score) = if (mode == 0) {
        val cert = runCatching { db.certificationDao().current(id, "TRACK") }.getOrNull()
        if (cert == null) "—" to 0f
        else {
            val lvl = runCatching { CertLevel.valueOf(cert.level) }.getOrNull()
            "${lvl?.emoji ?: "🏅"} ${lvl?.label ?: cert.level}" + (if (cert.multiplier > 1) " ×${cert.multiplier}" else "") to
                (((lvl?.ordinal ?: -1) + 1) * 1000f + cert.multiplier)
        }
    } else {
        val certs = runCatching { db.certificationDao().certsOfArtist(id) }.getOrDefault(emptyList())
        if (certs.isEmpty()) "—" to 0f
        else {
            val best = certs.mapNotNull { runCatching { CertLevel.valueOf(it.level) }.getOrNull() }.maxByOrNull { it.ordinal }
            "${certs.size} certifié${if (certs.size > 1) "s" else ""} · ${best?.label ?: ""}" to
                (certs.size * 1000f + ((best?.ordinal ?: 0) + 1) * 100f)
        }
    }
    return buildStats(list, label, score)
}

private fun buildStats(list: List<ScrobbleEntity>, certLabel: String, certScore: Float): VsStats {
    if (list.isEmpty()) return VsStats(certLabel = certLabel, certScore = certScore)
    val hours = IntArray(24)
    val dayCounts = HashMap<String, Int>()
    val dayEpochs = HashSet<Long>()
    val months = HashSet<String>()
    val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE)
    val monthFmt = SimpleDateFormat("yyyy-MM", Locale.FRANCE)
    val cal = Calendar.getInstance()
    var first = Long.MAX_VALUE
    var last = 0L
    var duration = 0L
    var longest = 0L
    var night = 0
    var morning = 0
    var afternoon = 0
    var evening = 0
    var weekend = 0

    for (s in list) {
        duration += s.durationListenedMs
        if (s.durationListenedMs > longest) longest = s.durationListenedMs
        if (s.startedAt < first) first = s.startedAt
        if (s.startedAt > last) last = s.startedAt
        cal.timeInMillis = s.startedAt
        val h = cal.get(Calendar.HOUR_OF_DAY)
        hours[h]++
        when (h) {
            in 0..5 -> night++
            in 6..11 -> morning++
            in 12..17 -> afternoon++
            else -> evening++
        }
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) weekend++
        val date = Date(s.startedAt)
        val key = dayFmt.format(date)
        dayCounts[key] = (dayCounts[key] ?: 0) + 1
        dayEpochs.add(s.startedAt / DAY_MS)
        months.add(monthFmt.format(date))
    }

    // Plus longue série de jours consécutifs
    var streak = 0
    var best = 0
    val sorted = dayEpochs.sorted()
    for (i in sorted.indices) {
        if (i == 0) streak = 1
        else if (sorted[i] - sorted[i - 1] == 1L) streak++
        else streak = 1
        if (streak > best) best = streak
    }

    val peakHour = hours.indices.filter { hours[it] > 0 }.maxByOrNull { hours[it] } ?: -1
    return VsStats(
        plays = list.size,
        durationMs = duration,
        activeDays = dayCounts.size,
        firstAt = if (first == Long.MAX_VALUE) 0L else first,
        lastAt = last,
        hours = hours.toList(),
        bestHour = peakHour,
        peakHourCount = hours.maxOrNull() ?: 0,
        bestDay = dayCounts.values.maxOrNull() ?: 0,
        days3Plus = dayCounts.values.count { it >= 3 },
        maxStreak = best,
        months = months.size,
        night = night,
        morning = morning,
        afternoon = afternoon,
        evening = evening,
        weekend = weekend,
        longestMs = longest,
        certLabel = certLabel,
        certScore = certScore
    )
}

/** Construit un critère : [higher] = la valeur la plus haute gagne. */
private fun row(tier: Int, label: String, aText: String, bText: String, aVal: Float, bVal: Float, higher: Boolean = true): VsRow {
    val av = if (higher) aVal else -aVal
    val bv = if (higher) bVal else -bVal
    val win = if (av == bv) null else av > bv
    val p = maxOf(av, 0f)
    val q = maxOf(bv, 0f)
    val share = if (p + q <= 0f) 0.5f else (p / (p + q)).coerceIn(0.06f, 0.94f)
    return VsRow(tier, label, aText, bText, win, share)
}

private fun fmt1(value: Float): String = String.format(Locale.FRANCE, "%.1f", value)

private fun buildRows(a: VsStats, b: VsStats): List<VsRow> {
    val rows = ArrayList<VsRow>(20)

    /* 🥉 Les bases */
    rows.add(row(0, "écoutes", formatCount(a.plays), formatCount(b.plays), a.plays.toFloat(), b.plays.toFloat()))
    rows.add(row(0, "temps d'écoute", formatDuration(a.durationMs), formatDuration(b.durationMs), a.durationMs.toFloat(), b.durationMs.toFloat()))
    rows.add(row(0, "jours actifs", formatCount(a.activeDays), formatCount(b.activeDays), a.activeDays.toFloat(), b.activeDays.toFloat()))
    rows.add(row(0, "moyenne / jour actif", fmt1(a.perDay), fmt1(b.perDay), a.perDay, b.perDay))
    rows.add(row(0, "certification", a.certLabel, b.certLabel, a.certScore, b.certScore))

    /* 🥈 La régularité */
    rows.add(row(1, "série max", "${a.maxStreak} j", "${b.maxStreak} j", a.maxStreak.toFloat(), b.maxStreak.toFloat()))
    rows.add(row(1, "mois actifs", formatCount(a.months), formatCount(b.months), a.months.toFloat(), b.months.toFloat()))
    rows.add(row(1, "record sur un jour", formatCount(a.bestDay), formatCount(b.bestDay), a.bestDay.toFloat(), b.bestDay.toFloat()))
    rows.add(row(1, "jours à 3 écoutes +", formatCount(a.days3Plus), formatCount(b.days3Plus), a.days3Plus.toFloat(), b.days3Plus.toFloat()))
    rows.add(row(1, "écoutes / mois", fmt1(a.perMonth), fmt1(b.perMonth), a.perMonth, b.perMonth))

    /* 🌙 Les moments */
    rows.add(row(2, "soirées 18 h – 23 h", formatCount(a.evening), formatCount(b.evening), a.evening.toFloat(), b.evening.toFloat()))
    rows.add(row(2, "nuits 0 h – 5 h", formatCount(a.night), formatCount(b.night), a.night.toFloat(), b.night.toFloat()))
    rows.add(row(2, "matinées 6 h – 11 h", formatCount(a.morning), formatCount(b.morning), a.morning.toFloat(), b.morning.toFloat()))
    rows.add(row(2, "après-midis 12 h – 17 h", formatCount(a.afternoon), formatCount(b.afternoon), a.afternoon.toFloat(), b.afternoon.toFloat()))
    rows.add(row(2, "week-ends", formatCount(a.weekend), formatCount(b.weekend), a.weekend.toFloat(), b.weekend.toFloat()))

    /* 💎 L'endurance */
    rows.add(row(3, "pic horaire", formatCount(a.peakHourCount), formatCount(b.peakHourCount), a.peakHourCount.toFloat(), b.peakHourCount.toFloat()))
    rows.add(row(3, "écoute la plus longue", formatDuration(a.longestMs), formatDuration(b.longestMs), a.longestMs.toFloat(), b.longestMs.toFloat()))
    rows.add(row(3, "vitesse de croissance", fmt1(a.growth), fmt1(b.growth), a.growth, b.growth))
    val refFirst = maxOf(a.firstAt, b.firstAt)
    val aa = if (a.firstAt > 0) (refFirst - a.firstAt).toFloat() / DAY_MS + 1f else 0f
    val ab = if (b.firstAt > 0) (refFirst - b.firstAt).toFloat() / DAY_MS + 1f else 0f
    rows.add(row(3, "dans ta vie depuis", dateOrDash(a.firstAt), dateOrDash(b.firstAt), aa, ab))
    val refLast = minOf(if (a.lastAt > 0) a.lastAt else Long.MAX_VALUE, if (b.lastAt > 0) b.lastAt else Long.MAX_VALUE)
    val la = if (a.lastAt > 0 && refLast != Long.MAX_VALUE) (a.lastAt - refLast).toFloat() / DAY_MS + 1f else 0f
    val lb = if (b.lastAt > 0 && refLast != Long.MAX_VALUE) (b.lastAt - refLast).toFloat() / DAY_MS + 1f else 0f
    rows.add(row(3, "dernière écoute", dateOrDash(a.lastAt), dateOrDash(b.lastAt), la, lb))

    return rows
}

private fun dateOrDash(ts: Long): String = if (ts <= 0L) "—" else VS_DATE_FMT.format(Date(ts))
