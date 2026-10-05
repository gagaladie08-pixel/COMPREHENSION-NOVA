package com.novastats.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import com.novastats.app.data.db.dao.YearEndRow
import com.novastats.app.data.repository.YearEndData
import com.novastats.app.data.repository.YearEndRepository
import com.novastats.app.ui.theme.Nova
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Locale

/* ==================================================================== */
/*  🏆 Year-End Charts — le bilan de chaque année civile                  */
/* ==================================================================== */

private const val YE_LIMIT = 100

private val YE_TABS = listOf("🎵 Titres", "🎤 Artistes", "💿 Albums")
private val YE_MEDALS = listOf("🥇", "🥈", "🥉")

private val YE_MONTH_FMT = SimpleDateFormat("dd MMMM", Locale.FRANCE)
private val YE_ISO_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE)

@Composable
fun YearEndScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as NovaStatsApp
    val theme = Nova.theme
    val repo = remember { YearEndRepository(app.database) }

    var years by remember { mutableStateOf<List<Int>>(emptyList()) }
    var year by remember { mutableStateOf<Int?>(null) }
    var data by remember { mutableStateOf<YearEndData?>(null) }
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        years = runCatching { repo.years() }.getOrDefault(emptyList())
        if (year == null) year = years.firstOrNull()
    }
    LaunchedEffect(year) {
        val y = year ?: return@LaunchedEffect
        data = runCatching { repo.load(y, YE_LIMIT) }.getOrNull()
    }

    val artUrl by produceState<String?>(null, data?.topTrack?.imageUrl) {
        value = runCatching {
            data?.topTrack?.imageUrl ?: app.database.trackDao().topAllTime(1).first().firstOrNull()?.track?.coverUrl
        }.getOrNull()
    }

    ScreenBackdrop(artUrl) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Billboard", color = theme.primary, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.weight(1f))
                Text("🏆 Year-End Charts", color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(12.dp))
            }

            /* ---------- Sélecteur d'année ---------- */
            if (years.size > 1) {
                Appear(delay = 0) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        years.forEach { y ->
                            val selected = y == year
                            Box(
                                Modifier.clip(CircleShape)
                                    .background(if (selected) Brush.horizontalGradient(listOf(theme.primary, theme.secondary)) else Brush.horizontalGradient(listOf(theme.surface.copy(alpha = 0.9f), theme.surface.copy(alpha = 0.7f))))
                                    .clickable { year = y }
                                    .padding(horizontal = 18.dp, vertical = 10.dp)
                            ) {
                                Text("$y", color = if (selected) theme.background else theme.text, fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            val d = data
            if (d == null || d.summary.playCount == 0) {
                Appear(delay = 60) {
                    Column(Modifier.padding(horizontal = 30.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🏆", fontSize = 46.sp)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (years.isEmpty()) "Pas encore d'écoutes enregistrées : les classements de fin d'année se construisent avec tes écoutes."
                            else "Aucune écoute enregistrée sur ${year ?: ""}.",
                            color = theme.textSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                /* ---------- Héros : l'année ---------- */
                Appear(delay = 40) {
                    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        StaggerTitle(
                            "${d.year}",
                            MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Black, fontSize = 74.sp, letterSpacing = (-4).sp),
                            theme.text
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "YEAR-END CHARTS", color = theme.primary,
                            style = MaterialTheme.typography.labelLarge, letterSpacing = 6.sp, fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Ton année musicale, du 1er janvier au 31 décembre ${d.year}",
                            color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center
                        )
                    }
                }

                /* ---------- Chiffres de l'année ---------- */
                Appear(delay = 90) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            YeValue("Écoutes", formatCount(d.summary.playCount), theme.primary, Modifier.weight(1f))
                            YeValue("Temps", formatDuration(d.summary.totalDurationMs), theme.secondary, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            YeValue("Titres", formatCount(d.summary.distinctTracks), theme.accent, Modifier.weight(1f))
                            YeValue("Artistes", formatCount(d.summary.distinctArtists), theme.glowSecondary, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            YeValue("Jours actifs", formatCount(d.summary.activeDays), theme.primary, Modifier.weight(1f))
                            YeValue("Moyenne / jour", String.format(Locale.FRANCE, "%.1f", d.avgPerDay), theme.secondary, Modifier.weight(1f))
                        }
                        if (d.previous.playCount > 0) {
                            Spacer(Modifier.height(10.dp))
                            val sign = if (d.playsDelta >= 0) "+" else ""
                            Text(
                                "$sign${formatCount(d.playsDelta)} écoutes (${if (d.playsDelta >= 0) "+" else ""}${d.playsDeltaPct} %) par rapport à ${d.year - 1}",
                                color = if (d.playsDelta >= 0) Color(0xFF4ADE80) else Color(0xFFFF6B6B),
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                /* ---------- Faits marquants ---------- */
                Appear(delay = 140) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        SectionTitle("✨ L'année en bref")
                        GlassCard {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                d.topTrack?.let { YeFact("🏆", "Titre de l'année", it.name, it.subtitle, "${formatCount(it.plays)} écoutes") }
                                d.topArtist?.let { YeFact("👑", "Artiste de l'année", it.name, null, "${formatCount(it.plays)} écoutes") }
                                d.topAlbum?.let { YeFact("💿", "Album de l'année", it.name, it.subtitle, "${formatCount(it.plays)} écoutes") }
                                d.bestDay?.let { YeFact("🔥", "Meilleure journée", prettyDay(it.date), null, "${formatCount(it.playCount)} écoutes") }
                                YeFact("✨", "Artistes découverts", "${d.newArtists}", null, "première écoute en ${d.year}")
                                YeFact("📊", "Albums distincts", "${d.summary.distinctAlbums}", null, "sur l'année")
                            }
                        }
                    }
                }

                /* ---------- Onglets ---------- */
                Appear(delay = 180) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        YE_TABS.forEachIndexed { i, label ->
                            val selected = tab == i
                            Box(
                                Modifier.weight(1f).clip(CircleShape)
                                    .background(if (selected) Brush.horizontalGradient(listOf(theme.primary, theme.secondary)) else Brush.horizontalGradient(listOf(theme.surface.copy(alpha = 0.9f), theme.surface.copy(alpha = 0.7f))))
                                    .clickable { tab = i }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, color = if (selected) theme.background else theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                val rows = when (tab) { 0 -> d.tracks; 1 -> d.artists; else -> d.albums }
                val top = rows.firstOrNull()?.plays ?: 1

                /* ---------- Podium ---------- */
                if (rows.size >= 3) {
                    Appear(delay = 210) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            SectionTitle("🥇 Podium")
                            rows.take(3).forEachIndexed { i, r ->
                                PodiumCard(rank = i + 1, row = r, top = top, circle = tab == 1)
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                    }
                }

                /* ---------- Le reste du classement ---------- */
                if (rows.size > 3) {
                    Appear(delay = 250) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            SectionTitle("📋 Classement — top ${rows.size}")
                            GlassCard {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    rows.drop(3).forEachIndexed { i, r ->
                                        YeRow(rank = i + 4, row = r, top = top, circle = tab == 1)
                                    }
                                }
                            }
                        }
                    }
                }

                Appear(delay = 290) {
                    Text(
                        "Classements calculés sur l'année civile complète, avec les mêmes règles que tes charts hebdomadaires. " +
                            "Les remix sont rattachés à leur original et les versions avec invité comptent pour le titre principal.",
                        color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}

/* ============================== briques ============================== */

@Composable
private fun YeValue(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val theme = Nova.theme
    GlassCard(modifier = modifier, glow = color) {
        Column(Modifier.padding(14.dp)) {
            Text(label.uppercase(), color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, color = color, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
        }
    }
}

@Composable
private fun YeFact(emoji: String, label: String, value: String, subtitle: String?, extra: String) {
    val theme = Nova.theme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
            Text(value, color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            if (!subtitle.isNullOrBlank()) Text(subtitle, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        Text(extra, color = theme.primary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PodiumCard(rank: Int, row: YearEndRow, top: Int, circle: Boolean) {
    val theme = Nova.theme
    val shape = RoundedCornerShape(if (circle) 50.dp else 16.dp)
    val glow = when (rank) { 1 -> Color(0xFFFFD700); 2 -> Color(0xFFC0C0C0); else -> Color(0xFFCD7F32) }
    val share by animateFloatAsState(row.plays.toFloat() / top.coerceAtLeast(1), tween(900, easing = FastOutSlowInEasing), label = "pod")
    GlassCard(glow = glow) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(YE_MEDALS.getOrElse(rank - 1) { "$rank" }, fontSize = 26.sp)
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.size(64.dp).clip(shape)
                        .background(Brush.linearGradient(listOf(glow.copy(alpha = 0.6f), theme.glowSecondary.copy(alpha = 0.5f)))),
                    contentAlignment = Alignment.Center
                ) {
                    if (row.imageUrl != null) AsyncImage(model = row.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
                    else Text(rank.toString(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("#$rank", color = glow, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                    Text(row.name, color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    if (!row.subtitle.isNullOrBlank()) Text(row.subtitle, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatCount(row.plays), color = theme.primary, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                    Text("écoutes", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                    Text(formatDuration(row.durationMs), color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))) {
                Box(Modifier.fillMaxWidth(share.coerceIn(0.02f, 1f)).height(7.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(glow, theme.primary))))
            }
        }
    }
}

@Composable
private fun YeRow(rank: Int, row: YearEndRow, top: Int, circle: Boolean) {
    val theme = Nova.theme
    val share by animateFloatAsState(row.plays.toFloat() / top.coerceAtLeast(1), tween(900, easing = FastOutSlowInEasing), label = "yerow")
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$rank", color = theme.textSecondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(30.dp))
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(if (circle) 50.dp else 8.dp))
                    .background(Brush.linearGradient(listOf(theme.primary.copy(alpha = 0.5f), theme.glowSecondary.copy(alpha = 0.5f)))),
                contentAlignment = Alignment.Center
            ) {
                if (row.imageUrl != null) AsyncImage(model = row.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(row.name, color = theme.text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                if (!row.subtitle.isNullOrBlank()) Text(row.subtitle, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatCount(row.plays), color = theme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(formatDuration(row.durationMs), color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.07f))) {
            Box(Modifier.fillMaxWidth(share.coerceIn(0.02f, 1f)).height(4.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(theme.primary.copy(alpha = 0.9f), theme.secondary.copy(alpha = 0.5f)))))
        }
    }
}

private fun prettyDay(iso: String): String = runCatching {
    YE_MONTH_FMT.format(YE_ISO_FMT.parse(iso)!!)
}.getOrDefault(iso)
