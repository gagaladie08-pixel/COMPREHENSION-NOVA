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
/*  ⚔️ Comparer — deux titres (ou deux artistes) face à face              */
/* ==================================================================== */

private data class VsPick(val id: Long, val name: String, val subtitle: String?, val imageUrl: String?)

private data class VsStats(
    val plays: Int = 0,
    val durationMs: Long = 0,
    val activeDays: Int = 0,
    val firstAt: Long = 0L,
    val lastAt: Long = 0L,
    val hours: List<Int> = List(24) { 0 },
    val certOrdinal: Int = -1,
    val certLabel: String = "—",
    val certScore: Float = 0f,
    val bestHour: Int = -1
) {
    val perDay: Float get() = if (activeDays > 0) plays.toFloat() / activeDays else 0f
    val peakHourLabel: String get() = if (bestHour < 0) "—" else String.format(Locale.FRANCE, "%02d h", bestHour)
}

private data class VsRow(val label: String, val aText: String, val bText: String, val aWin: Boolean?, val aShare: Float)

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
    var side by remember { mutableIntStateOf(0) } // 0 = aucun, 1 = gauche, 2 = droite
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<VsPick>>(emptyList()) }

    // Recherche (ou top 20 si le champ est vide)
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

    // Statistiques des deux camps
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

    val artUrl by produceState<String?>(initialValue = null, pickA?.imageUrl, pickB?.imageUrl) {
        value = runCatching {
            pickA?.imageUrl ?: pickB?.imageUrl ?: db.trackDao().topAllTime(1).first().firstOrNull()?.track?.coverUrl
        }.getOrNull()
    }

    ScreenBackdrop(artUrl) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Stats", color = theme.primary, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.weight(1f))
                Text("⚔️ Comparer", color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(12.dp))
            }

            /* ---------- Sélecteur de mode ---------- */
            Appear(delay = 0) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ModeChip("🎵 Deux titres", mode == 0, Modifier.weight(1f)) { mode = 0; pickA = null; pickB = null; statsA = null; statsB = null; side = 0; query = "" }
                    ModeChip("🎤 Deux artistes", mode == 1, Modifier.weight(1f)) { mode = 1; pickA = null; pickB = null; statsA = null; statsB = null; side = 0; query = "" }
                }
            }

            /* ---------- Les deux camps ---------- */
            Appear(delay = 60) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FighterCard(pickA, "Choisir à gauche", theme.primary, Modifier.weight(1f)) { side = if (side == 1) 0 else 1 }
                    Box(Modifier.width(46.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(theme.primary, theme.secondary))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("VS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 0.5.sp)
                        }
                    }
                    FighterCard(pickB, "Choisir à droite", theme.secondary, Modifier.weight(1f)) { side = if (side == 2) 0 else 2 }
                }
            }

            /* ---------- Panneau de recherche ---------- */
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
                                                .clickable {
                                                    if (side == 1) pickA = r else pickB = r
                                                    side = 0; query = ""
                                                }
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

            /* ---------- Le duel ---------- */
            val a = statsA
            val b = statsB
            if (pickA != null && pickB != null && a != null && b != null) {
                val rows = remember(a, b) { buildRows(a, b) }
                val winsA = rows.count { it.aWin == true }
                val winsB = rows.count { it.aWin == false }

                Appear(delay = 120) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        SectionTitle("🏆 Verdict")
                        GlassCard(glow = if (winsA == winsB) theme.accent else if (winsA > winsB) theme.primary else theme.secondary) {
                            Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                val leader = when {
                                    winsA == winsB -> null
                                    winsA > winsB -> pickA?.name
                                    else -> pickB?.name
                                }
                                Text(
                                    if (leader == null) "Égalité parfaite" else "🏆 $leader mène",
                                    color = theme.text, fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    if (leader == null) "$winsA partout sur ${rows.size} critères : impossible de les départager."
                                    else "$winsA à $winsB sur ${rows.size} critères.",
                                    color = theme.textSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Appear(delay = 180) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        SectionTitle("📊 Critère par critère")
                        GlassCard {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                rows.forEach { row -> DuelRow(row) }
                            }
                        }
                    }
                }

                Appear(delay = 240) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        SectionTitle("🕐 Heures d'écoute")
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

                Appear(delay = 280) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { pickA = null; pickB = null; statsA = null; statsB = null },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = theme.surface, contentColor = theme.text)
                            ) { Text("🔄 Nouveau duel") }
                            Button(
                                onClick = { val t = pickA; pickA = pickB; pickB = t; val s = statsA; statsA = statsB; statsB = s },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = theme.surface, contentColor = theme.text)
                            ) { Text("⇄ Inverser") }
                        }
                    }
                }
            } else {
                Appear(delay = 120) {
                    Column(Modifier.padding(horizontal = 28.dp, vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "⚔️",
                            fontSize = 46.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Choisis deux " + (if (mode == 0) "titres" else "artistes") + " et NovaStats les met face à face : écoutes, temps, régularité, certification, fraîcheur… et un verdict.",
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
            .background(if (selected) Brush.horizontalGradient(listOf(theme.primary, theme.secondary)) else Brush.horizontalGradient(listOf(theme.surface.copy(alpha = 0.9f), theme.surface.copy(alpha = 0.7f))))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) theme.background else theme.text,
            fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun FighterCard(pick: VsPick?, placeholder: String, glow: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val theme = Nova.theme
    GlassCard(modifier = modifier, glow = glow) {
        Column(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(if (pick == null) 64.dp else 86.dp).clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(glow.copy(alpha = 0.6f), theme.glowSecondary.copy(alpha = 0.5f)))),
                contentAlignment = Alignment.Center
            ) {
                if (pick?.imageUrl != null) AsyncImage(model = pick.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
                else Text(if (pick == null) "＋" else "♪", color = Color.White.copy(alpha = 0.9f), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                pick?.name ?: placeholder,
                color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center, maxLines = 2
            )
            if (pick?.subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(pick.subtitle, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun DuelRow(row: VsRow) {
    val theme = Nova.theme
    val anim by animateFloatAsState(row.aShare, tween(900, easing = FastOutSlowInEasing), label = "duel")
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (row.aWin == true) "🏅 " else "") + row.aText,
                color = if (row.aWin == true) theme.primary else theme.textSecondary,
                fontWeight = if (row.aWin == true) FontWeight.Black else FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Start, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(row.label, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                row.bText + (if (row.aWin == false) " 🏅" else ""),
                color = if (row.aWin == false) theme.secondary else theme.textSecondary,
                fontWeight = if (row.aWin == false) FontWeight.Black else FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End, modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(7.dp))
        Box(
            Modifier.fillMaxWidth().height(9.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))
        ) {
            Box(
                Modifier.fillMaxWidth(anim.coerceIn(0.02f, 0.98f)).height(9.dp).clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(theme.primary, theme.secondary)))
            )
        }
    }
}

/* ============================== données ============================== */

private fun share(a: Float, b: Float): Float =
    if (a <= 0f && b <= 0f) 0.5f else (a / (a + b)).coerceIn(0.05f, 0.95f)

private suspend fun loadStats(db: NovaDatabase, id: Long, mode: Int): VsStats {
    val list = if (mode == 0) db.scrobbleDao().allOfTrack(id) else db.scrobbleDao().allOfArtist(id)
    val (label, ordinal, score) = if (mode == 0) {
        val cert = runCatching { db.certificationDao().current(id, "TRACK") }.getOrNull()
        if (cert == null) Triple("—", -1, 0f)
        else {
            val lvl = runCatching { CertLevel.valueOf(cert.level) }.getOrNull()
            Triple(
                "${lvl?.emoji ?: "🏅"} ${lvl?.label ?: cert.level}" + if (cert.multiplier > 1) " ×${cert.multiplier}" else "",
                lvl?.ordinal ?: -1,
                ((lvl?.ordinal ?: -1) + 1) * 1000f + cert.multiplier
            )
        }
    } else {
        val certs = runCatching { db.certificationDao().certsOfArtist(id) }.getOrDefault(emptyList())
        if (certs.isEmpty()) Triple("—", -1, 0f)
        else {
            val best = certs.mapNotNull { runCatching { CertLevel.valueOf(it.level) }.getOrNull() }.maxByOrNull { it.ordinal }
            Triple(
                "${certs.size} certifié${if (certs.size > 1) "s" else ""} · ${best?.label ?: ""}",
                best?.ordinal ?: -1,
                certs.size * 1000f + ((best?.ordinal ?: 0) + 1) * 100f
            )
        }
    }
    return buildStats(list, label, ordinal, score)
}

private fun buildStats(list: List<ScrobbleEntity>, certLabel: String, certOrdinal: Int, certScore: Float): VsStats {
    if (list.isEmpty()) return VsStats(certLabel = certLabel, certOrdinal = certOrdinal, certScore = certScore)
    val hours = IntArray(24)
    val days = HashSet<String>()
    val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE)
    val cal = Calendar.getInstance()
    var first = Long.MAX_VALUE
    var last = 0L
    var duration = 0L
    for (s in list) {
        duration += s.durationListenedMs
        if (s.startedAt < first) first = s.startedAt
        if (s.startedAt > last) last = s.startedAt
        cal.timeInMillis = s.startedAt
        hours[cal.get(Calendar.HOUR_OF_DAY)]++
        days.add(dayFmt.format(Date(s.startedAt)))
    }
    val best = hours.indices.filter { hours[it] > 0 }.maxByOrNull { hours[it] } ?: -1
    return VsStats(
        plays = list.size,
        durationMs = duration,
        activeDays = days.size,
        firstAt = if (first == Long.MAX_VALUE) 0L else first,
        lastAt = last,
        hours = hours.toList(),
        certOrdinal = certOrdinal,
        certLabel = certLabel,
        certScore = certScore,
        bestHour = best
    )
}

private fun buildRows(a: VsStats, b: VsStats): List<VsRow> {
    val rows = ArrayList<VsRow>(8)

    rows.add(
        VsRow("écoutes", formatCount(a.plays), formatCount(b.plays),
            winner(a.plays.toFloat(), b.plays.toFloat()), share(a.plays.toFloat(), b.plays.toFloat()))
    )
    rows.add(
        VsRow("temps d'écoute", formatDuration(a.durationMs), formatDuration(b.durationMs),
            winner(a.durationMs.toFloat(), b.durationMs.toFloat()), share(a.durationMs.toFloat(), b.durationMs.toFloat()))
    )
    rows.add(
        VsRow("jours actifs", formatCount(a.activeDays), formatCount(b.activeDays),
            winner(a.activeDays.toFloat(), b.activeDays.toFloat()), share(a.activeDays.toFloat(), b.activeDays.toFloat()))
    )
    rows.add(
        VsRow("moyenne / jour actif", String.format(Locale.FRANCE, "%.1f", a.perDay), String.format(Locale.FRANCE, "%.1f", b.perDay),
            winner(a.perDay, b.perDay), share(a.perDay, b.perDay))
    )
    rows.add(
        VsRow("certification", a.certLabel, b.certLabel,
            winner(a.certScore, b.certScore), share(a.certScore, b.certScore))
    )
    // Fraîcheur : l'écoute la plus récente gagne
    val refLast = minOf(if (a.lastAt > 0) a.lastAt else Long.MAX_VALUE, if (b.lastAt > 0) b.lastAt else Long.MAX_VALUE)
    val fa = if (a.lastAt > 0 && refLast != Long.MAX_VALUE) (a.lastAt - refLast).toFloat() / 86_400_000f + 1f else 0f
    val fb = if (b.lastAt > 0 && refLast != Long.MAX_VALUE) (b.lastAt - refLast).toFloat() / 86_400_000f + 1f else 0f
    rows.add(
        VsRow("dernière écoute", dateOrDash(a.lastAt), dateOrDash(b.lastAt), winner(fa, fb), share(fa, fb))
    )
    // Ancienneté : la première écoute la plus ancienne gagne
    val refFirst = maxOf(a.firstAt, b.firstAt)
    val aa = if (a.firstAt > 0) (refFirst - a.firstAt).toFloat() / 86_400_000f + 1f else 0f
    val ab = if (b.firstAt > 0) (refFirst - b.firstAt).toFloat() / 86_400_000f + 1f else 0f
    rows.add(
        VsRow("dans ta vie depuis", dateOrDash(a.firstAt), dateOrDash(b.firstAt), winner(aa, ab), share(aa, ab))
    )
    rows.add(
        VsRow("heure de prédilection", a.peakHourLabel, b.peakHourLabel, null, 0.5f)
    )
    return rows
}

private fun winner(a: Float, b: Float): Boolean? =
    if (a == b) null else a > b

private fun dateOrDash(ts: Long): String = if (ts <= 0L) "—" else VS_DATE_FMT.format(Date(ts))
