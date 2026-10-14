package com.novastats.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novastats.app.data.db.dao.RecordRow
import com.novastats.app.data.repository.RecordExplainer
import com.novastats.app.domain.CertLevel
import com.novastats.app.domain.Dates
import com.novastats.app.domain.Period
import com.novastats.app.domain.RecordCatalog
import com.novastats.app.domain.RecordCategory
import com.novastats.app.domain.RecordDef
import com.novastats.app.ui.theme.Nova
import com.novastats.app.ui.theme.NovaColors
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

internal fun certificationAccent(sub: String?, fallback: Color): Color = when {
    sub?.substringAfterLast('_') == CertLevel.SILVER.dbName || sub?.contains(CertLevel.SILVER.label, ignoreCase = true) == true -> Color(0xFFC8D1E3)
    sub?.substringAfterLast('_') == CertLevel.GOLD.dbName || sub?.contains(CertLevel.GOLD.label, ignoreCase = true) == true -> Color(0xFFFFC85A)
    sub?.substringAfterLast('_') == CertLevel.PLATINUM.dbName || sub?.contains(CertLevel.PLATINUM.label, ignoreCase = true) == true -> Color(0xFFB4D7FF)
    sub?.substringAfterLast('_') == CertLevel.DIAMOND.dbName || sub?.contains(CertLevel.DIAMOND.label, ignoreCase = true) == true -> Color(0xFF61E3F5)
    else -> fallback
}

internal fun certificationLevel(sub: String?): CertLevel? =
    CertLevel.entries.firstOrNull { it.dbName == sub?.substringAfterLast('_') }

internal fun biggestPeriodAccent(period: Period?, fallback: Color): Color = when (period) {
    Period.DAILY -> Color(0xFFFFA34D)
    Period.WEEKLY -> Color(0xFF63C9FF)
    Period.MONTHLY -> Color(0xFFD38BFF)
    else -> fallback
}

private fun biggestPeriodName(period: Period?): String = when (period) {
    Period.DAILY -> "JOUR"
    Period.WEEKLY -> "SEMAINE"
    Period.MONTHLY -> "MOIS"
    else -> period?.frLabel?.uppercase(Locale.FRANCE) ?: "PÉRIODE"
}

private fun premiumCategoryLabel(category: RecordCategory): String = when (category) {
    RecordCategory.TRACK -> "Titres"
    RecordCategory.ARTIST -> "Artistes"
    RecordCategory.ALBUM -> "Albums"
}

private fun certificationScope(category: RecordCategory, subLabel: String?): String =
    listOfNotNull(premiumCategoryLabel(category), subLabel).joinToString(" · ")

internal fun biggestPeriodDateLabel(period: Period?, iso: String?): String {
    if (iso.isNullOrBlank()) return "Date d'archive indisponible"
    val date = runCatching { Dates.parse(iso) }.getOrNull() ?: return iso
    val full = date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE))
    return when (period) {
        Period.DAILY -> "Le $full"
        Period.WEEKLY -> "Semaine du $full"
        Period.MONTHLY -> date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRANCE)).replaceFirstChar { it.uppercase(Locale.FRANCE) }
        else -> full
    }
}

internal fun biggestPeriodChartRank(extraData: String?): String? = extraData
    ?.substringBefore(' ')
    ?.takeIf { it.startsWith("#") && it.drop(1).toIntOrNull() != null }
    ?.let { "rang $it" }

@Composable
internal fun CertificationRecordCard(def: RecordDef, color: Color, onClick: () -> Unit) {
    val theme = Nova.theme
    val shape = RoundedCornerShape(25.dp)
    val accent = Color(0xFFB99AFF)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(12.dp, shape, ambientColor = accent.copy(alpha = 0.15f), spotColor = color.copy(alpha = 0.16f))
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF20142C), theme.surface.copy(alpha = 0.98f), theme.surface)))
            .border(1.dp, accent.copy(alpha = 0.38f), shape)
            .clickable(onClick = onClick).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(63.dp).clip(RoundedCornerShape(20.dp))
                    .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.38f), Color(0xFF17101E))))
                    .border(1.dp, accent.copy(alpha = 0.53f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("💎", fontSize = 29.sp)
                Text("✦", color = Color.White, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text("NOVA  /  LE TRÉSOR DES CERTIFICATIONS", color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 0.7.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(def.title, color = theme.text, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Text("Chaque niveau. Chaque pièce. Une collection qui s'impose.", color = theme.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("↗", color = accent, fontSize = 27.sp, fontWeight = FontWeight.Light)
        }
        Spacer(Modifier.height(13.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "ARGENT" to Color(0xFFC8D1E3),
                "OR" to Color(0xFFFFC85A),
                "PLATINE" to Color(0xFFB4D7FF),
                "DIAMANT" to Color(0xFF61E3F5)
            ).forEach { (label, tierColor) ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(tierColor.copy(alpha = 0.075f))
                        .border(1.dp, tierColor.copy(alpha = 0.22f), RoundedCornerShape(11.dp)).padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("✦", color = tierColor, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    Text(label, color = tierColor, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
internal fun BiggestPeriodRecordCard(def: RecordDef, color: Color, onClick: () -> Unit) {
    val theme = Nova.theme
    val accent = Color(0xFFFFA34D)
    val shape = RoundedCornerShape(25.dp)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(12.dp, shape, ambientColor = accent.copy(alpha = 0.13f), spotColor = color.copy(alpha = 0.15f))
            .clip(shape).background(Brush.horizontalGradient(listOf(Color(0xFF26160E), theme.surface.copy(alpha = 0.98f), theme.surface)))
            .border(1.dp, accent.copy(alpha = 0.37f), shape).clickable(onClick = onClick).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(63.dp).clip(RoundedCornerShape(20.dp))
                    .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.39f), Color(0xFF1D100A))))
                    .border(1.dp, accent.copy(alpha = 0.50f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("✹", color = accent, fontSize = 37.sp, fontWeight = FontWeight.Black)
                Text("⚡", fontSize = 15.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text("NOVA  /  L'INSTANT ABSOLU", color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 0.85.sp)
                Text(def.title, color = theme.text, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Text("Un pic. Une date. Le moment où tout s'embrase.", color = theme.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("↗", color = accent, fontSize = 27.sp, fontWeight = FontWeight.Light)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(
                Triple("☀", "JOUR", Color(0xFFFFA34D)),
                Triple("◷", "SEMAINE", Color(0xFF63C9FF)),
                Triple("▦", "MOIS", Color(0xFFD38BFF))
            ).forEach { (glyph, label, tierColor) ->
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(tierColor.copy(alpha = 0.09f))
                        .border(1.dp, tierColor.copy(alpha = 0.24f), RoundedCornerShape(11.dp)).padding(horizontal = 7.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
                ) {
                    Text(glyph, color = tierColor, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(4.dp))
                    Text(label, color = tierColor, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}

@Composable
internal fun CertificationLeaderboardStage(
    rows: List<RecordRow>,
    category: RecordCategory,
    subLabel: String?,
    color: Color,
    onEntry: (RecordRow, Int) -> Unit
) {
    if (rows.isEmpty()) return
    val theme = Nova.theme
    val accent = certificationAccent(subLabel, color)
    val shape = RoundedCornerShape(27.dp)
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp)) {
        Box(Modifier.fillMaxWidth().clip(shape).background(Color(0xFF100D18)).border(1.dp, accent.copy(alpha = 0.35f), shape)) {
            PremiumStageCanvas(accent, 0.62f, Modifier.matchParentSize())
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("LA SALLE DES TROPHÉES", color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 1.1.sp)
                        Text(certificationScope(category, subLabel), color = Color.White.copy(alpha = 0.68f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("TOP ${minOf(rows.size, 3)}", color = Color.White.copy(alpha = 0.75f), fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(12.dp))
                CertificationChampion(rows.first(), 1, accent, onClick = { onEntry(rows.first(), 1) })
                if (rows.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rows.drop(1).take(2).forEachIndexed { index, row ->
                            CertificationRunner(row, index + 2, accent, Modifier.weight(1f)) { onEntry(row, index + 2) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BiggestPeriodLeaderboardStage(
    period: Period?,
    category: RecordCategory,
    rows: List<RecordRow>,
    color: Color,
    onEntry: (RecordRow, Int) -> Unit
) {
    if (rows.isEmpty()) return
    val theme = Nova.theme
    val accent = biggestPeriodAccent(period, color)
    val shape = RoundedCornerShape(27.dp)
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp)) {
        Box(Modifier.fillMaxWidth().clip(shape).background(Color(0xFF110D0A)).border(1.dp, accent.copy(alpha = 0.36f), shape)) {
            PremiumStageCanvas(accent, 0.67f, Modifier.matchParentSize())
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${biggestPeriodName(period)}  /  MOMENTS LÉGENDAIRES", color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 0.85.sp)
                        Text("${period?.frLabel ?: "Période"} · ${premiumCategoryLabel(category)} · pics classés séparément", color = Color.White.copy(alpha = 0.70f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                    }
                    Text("TOP ${minOf(rows.size, 3)}", color = Color.White.copy(alpha = 0.74f), fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(12.dp))
                PeakChampion(period, category, rows.first(), accent, onClick = { onEntry(rows.first(), 1) })
                if (rows.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rows.drop(1).take(2).forEachIndexed { index, row ->
                            PeakRunner(period, category, row, index + 2, accent, Modifier.weight(1f)) { onEntry(row, index + 2) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CertificationChampion(row: RecordRow, rank: Int, accent: Color, onClick: () -> Unit) {
    val theme = Nova.theme
    val shape = RoundedCornerShape(19.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Color.White.copy(alpha = 0.075f)).border(1.dp, accent.copy(alpha = 0.40f), shape)
            .clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(78.dp)) { drawCircle(accent.copy(alpha = 0.14f), radius = size.minDimension * 0.48f); drawCircle(accent.copy(alpha = 0.7f), radius = size.minDimension * 0.48f, style = Stroke(1.5.dp.toPx())) }
            CoverArt(row.imageUrl, row.name ?: "?", 62, circle = row.r.category == RecordCategory.ARTIST.dbName)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("✦  COLLECTION N°$rank", color = NovaColors.Gold, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 0.8.sp)
            Text(row.name ?: "Inconnu", color = Color.White, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${formatCount(row.r.value.toLong())} certification${if (row.r.value.toLong() == 1L) "" else "s"}", color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
            row.subtitle?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color.White.copy(alpha = 0.65f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Text("🏆", fontSize = 20.sp)
    }
}

@Composable
private fun CertificationRunner(row: RecordRow, rank: Int, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val theme = Nova.theme
    Row(
        modifier.clip(RoundedCornerShape(15.dp)).background(theme.surface.copy(alpha = 0.74f)).border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(15.dp))
            .clickable(onClick = onClick).padding(9.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        CoverArt(row.imageUrl, row.name ?: "?", 43, circle = row.r.category == RecordCategory.ARTIST.dbName)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("#$rank · ${row.name ?: "Inconnu"}", color = theme.text, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatCount(row.r.value.toLong()), color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PeakChampion(period: Period?, category: RecordCategory, row: RecordRow, accent: Color, onClick: () -> Unit) {
    val theme = Nova.theme
    val shape = RoundedCornerShape(19.dp)
    val date = biggestPeriodDateLabel(period, row.r.valueDate)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Color.White.copy(alpha = 0.075f)).border(1.dp, accent.copy(alpha = 0.42f), shape)
            .clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(82.dp)) { drawCircle(accent.copy(alpha = 0.16f), radius = size.minDimension * 0.48f); drawCircle(accent.copy(alpha = 0.72f), radius = size.minDimension * 0.48f, style = Stroke(1.5.dp.toPx())) }
            CoverArt(row.imageUrl, row.name ?: "?", 66, circle = category == RecordCategory.ARTIST)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("✦  PIC ABSOLU · #1", color = NovaColors.Gold, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 0.8.sp)
            Text(row.name ?: "Inconnu", color = Color.White, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(formatRecordValue(RecordCatalog.byId("BIGGEST_PERIOD")!!, period, row.r.value), color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
            Text(date, color = Color.White.copy(alpha = 0.68f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("✹", color = accent, fontSize = 25.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun PeakRunner(period: Period?, category: RecordCategory, row: RecordRow, rank: Int, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val theme = Nova.theme
    Row(
        modifier.clip(RoundedCornerShape(15.dp)).background(theme.surface.copy(alpha = 0.74f)).border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(15.dp))
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        CoverArt(row.imageUrl, row.name ?: "?", 42, circle = category == RecordCategory.ARTIST)
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Text("#$rank · ${row.name ?: "Inconnu"}", color = theme.text, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${formatCount(row.r.value.toLong())} · ${biggestPeriodDateLabel(period, row.r.valueDate)}", color = accent, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun CertificationArchiveSpotlight(
    name: String,
    subtitle: String?,
    imageUrl: String?,
    isArtist: Boolean,
    score: Int,
    rank: Int?,
    category: RecordCategory,
    sub: String?,
    context: String,
    story: RecordExplainer.Story,
    sharing: Boolean,
    shareError: Boolean,
    onShare: () -> Unit,
    onItemClick: (RecordExplainer.Item) -> Unit
) {
    val theme = Nova.theme
    val accent = certificationAccent(sub, Color(0xFFB99AFF))
    val level = certificationLevel(sub)
    val collectionScope = when {
        category == RecordCategory.ARTIST && sub?.startsWith("SONGS_") == true -> "TITRES DE L'ARTISTE"
        category == RecordCategory.ARTIST && sub?.startsWith("ALBUMS_") == true -> "ALBUMS DE L'ARTISTE"
        category == RecordCategory.ALBUM -> "TITRES DE L'ALBUM"
        else -> premiumCategoryLabel(category).uppercase(Locale.FRANCE)
    }
    val shape = RoundedCornerShape(30.dp)
    var replayToken by remember(name, context) { mutableStateOf(0) }
    val reveal = remember(name, context) { Animatable(0f) }
    LaunchedEffect(name, context, replayToken) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(1350))
    }
    val count = remember(name, context) { Animatable(0f) }
    LaunchedEffect(name, context, score, replayToken) {
        count.snapTo(0f)
        count.animateTo(score.toFloat(), tween(1450))
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 6.dp)) {
        Text("✦  LE TRÉSOR NOVA", color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, letterSpacing = 1.25.sp, modifier = Modifier.padding(start = 3.dp, bottom = 8.dp))
        Box(Modifier.fillMaxWidth().shadow(20.dp, shape, ambientColor = accent.copy(alpha = 0.19f), spotColor = accent.copy(alpha = 0.20f)).clip(shape).background(Color(0xFF0D0B13)).border(1.4.dp, accent.copy(alpha = 0.45f), shape)) {
            BlurredBackdrop(imageUrl, accent, Modifier.matchParentSize())
            PremiumStageCanvas(accent, reveal.value, Modifier.matchParentSize(), overlayOnly = true)
            Column(
                Modifier.fillMaxWidth().graphicsLayer {
                    alpha = reveal.value
                    val scale = 0.96f + 0.04f * reveal.value
                    scaleX = scale; scaleY = scale
                    translationY = (1f - reveal.value) * 20.dp.toPx()
                }.padding(horizontal = 16.dp, vertical = 17.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("NOVA  /  CHAMBRE DES DISTINCTIONS", color = Color.White.copy(alpha = 0.82f), fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 0.9.sp)
                        Text(context, color = Color.White.copy(alpha = 0.68f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    rank?.let { PremiumRankSeal(it, accent) }
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(103.dp)) {
                            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.34f), Color.Transparent)), radius = size.minDimension / 2f)
                            drawCircle(accent.copy(alpha = 0.86f), radius = size.minDimension * 0.43f, style = Stroke(1.8.dp.toPx()))
                        }
                        CoverArt(imageUrl, name, 78, circle = isArtist, zoomable = true)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("COLLECTION CERTIFIÉE", color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 0.8.sp)
                        Text(name, color = Color.White, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        subtitle?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color.White.copy(alpha = 0.68f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
                Spacer(Modifier.height(17.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${level?.emoji ?: "💎"} ${level?.label?.uppercase(Locale.FRANCE) ?: "NIVEAU"}  ·  $collectionScope", color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 0.7.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatCount(count.value.toInt().coerceAtLeast(0)), color = Color.White, fontWeight = FontWeight.Black, fontSize = 39.sp, lineHeight = 42.sp)
                        Text("PIÈCES CERTIFIÉES", color = Color.White.copy(alpha = 0.66f), fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 1.0.sp)
                    }
                    Box(contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(77.dp)) {
                            drawCircle(accent.copy(alpha = 0.10f), style = Stroke(width = 8.dp.toPx()))
                            drawArc(accent.copy(alpha = 0.92f), -90f, 360f * reveal.value, useCenter = false, style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round))
                        }
                        Text("✦", color = accent, fontSize = 25.sp, fontWeight = FontWeight.Black)
                    }
                }
                if (story.facts.isNotEmpty()) {
                    Spacer(Modifier.height(13.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        story.facts.take(2).forEach { (label, value) -> PremiumMiniFact(label, value, accent, Modifier.weight(1f)) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (reveal.value >= 0.99f) "↻ REJOUER LA RÉVÉLATION" else "✦ OUVERTURE DU COFFRE", color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.10f)).border(1.dp, accent.copy(alpha = 0.27f), RoundedCornerShape(10.dp)).clickable { replayToken++ }.padding(horizontal = 10.dp, vertical = 8.dp))
                    Spacer(Modifier.weight(1f))
                    Text("NOVA  /  ARCHIVES", color = Color.White.copy(alpha = 0.56f), fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 0.7.sp)
                }
            }
        }
        if (story.items.isNotEmpty()) CertificationCollectionGallery(story.items, accent, onItemClick)
        if (story.narrative.isNotBlank()) {
            Column(Modifier.fillMaxWidth().padding(top = 11.dp).clip(RoundedCornerShape(18.dp)).background(theme.surface.copy(alpha = 0.93f)).border(1.dp, accent.copy(alpha = 0.23f), RoundedCornerShape(18.dp)).padding(14.dp)) {
                Text("LE REGISTRE DU TRÉSOR", color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 0.9.sp)
                Spacer(Modifier.height(5.dp))
                Text(
                    "$name réunit ${formatCount(score)} pièce${if (score == 1) "" else "s"} certifiée${if (score == 1) "" else "s"} au niveau ${level?.label ?: "sélectionné"}. Chaque pochette de la galerie en apporte la preuve.",
                    color = theme.text, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis
                )
            }
        }
        PremiumShareButton("Créer l'affiche du Trésor Nova", "Une collection certifiée · prête à partager", sharing, shareError, accent, onShare)
    }
}

@Composable
private fun CertificationCollectionGallery(items: List<RecordExplainer.Item>, accent: Color, onItemClick: (RecordExplainer.Item) -> Unit) {
    val theme = Nova.theme
    Column(Modifier.fillMaxWidth().padding(top = 13.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 3.dp, bottom = 7.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("GALERIE DES PIÈCES RARES", color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, letterSpacing = 0.9.sp)
                Text("Chaque pochette prouve une certification de ce niveau.", color = theme.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            }
            Text("${items.size} PIÈCES", color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            itemsIndexed(items, key = { index, item -> "cert-piece-${item.type}-${item.id}-$index" }) { index, item ->
                Column(
                    Modifier.width(204.dp).clip(RoundedCornerShape(20.dp)).background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.16f), theme.surface, theme.surface)))
                        .border(1.dp, accent.copy(alpha = 0.29f), RoundedCornerShape(20.dp)).clickable { onItemClick(item) }.padding(11.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("PIÈCE  ${index + 1}".uppercase(Locale.FRANCE), color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 0.55.sp)
                        Spacer(Modifier.weight(1f)); Text("✦", color = NovaColors.Gold, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(116.dp).clip(RoundedCornerShape(18.dp)).background(accent.copy(alpha = 0.12f)).border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                            CoverArt(item.imageUrl, item.name, 102, circle = item.type == RecordCategory.ARTIST.dbName)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(item.name, color = theme.text, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.titleSmall, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
                    Text(item.detail, color = theme.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("OUVRIR LA PIÈCE  ›", color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
internal fun BiggestPeriodSpotlight(
    name: String,
    subtitle: String?,
    imageUrl: String?,
    isArtist: Boolean,
    category: RecordCategory,
    score: Int,
    rank: Int?,
    period: Period?,
    peakDate: String?,
    context: String,
    story: RecordExplainer.Story,
    sharing: Boolean,
    shareError: Boolean,
    onShare: () -> Unit
) {
    val theme = Nova.theme
    val accent = biggestPeriodAccent(period, theme.primary)
    val shape = RoundedCornerShape(30.dp)
    var replayToken by remember(name, period, peakDate, score) { mutableStateOf(0) }
    val reveal = remember(name, period, peakDate, score) { Animatable(0f) }
    var selectedIndex by remember(story.timeline, peakDate) {
        mutableStateOf(
            story.timeline.indexOfFirst { it.date == peakDate }.takeIf { it >= 0 }
                ?: story.timeline.indexOfFirst { it.highlight }.coerceAtLeast(0)
        )
    }
    LaunchedEffect(name, period, peakDate, score, replayToken) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(1350))
    }
    val selectedPoint = story.timeline.getOrNull(selectedIndex)
    val displayDate = biggestPeriodDateLabel(period, peakDate)
    val peakPosition = story.timeline.firstOrNull { it.date == peakDate }?.position
        ?: story.timeline.firstOrNull { it.highlight }?.position

    Column(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 6.dp)) {
        Text("✦  L'INSTANT ABSOLU", color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, letterSpacing = 1.2.sp, modifier = Modifier.padding(start = 3.dp, bottom = 8.dp))
        Box(Modifier.fillMaxWidth().shadow(21.dp, shape, ambientColor = accent.copy(alpha = 0.18f), spotColor = accent.copy(alpha = 0.23f)).clip(shape).background(Color(0xFF0C0B0C)).border(1.4.dp, accent.copy(alpha = 0.48f), shape)) {
            BlurredBackdrop(imageUrl, accent, Modifier.matchParentSize())
            PremiumStageCanvas(accent, reveal.value, Modifier.matchParentSize(), overlayOnly = true)
            Column(
                Modifier.fillMaxWidth().graphicsLayer {
                    alpha = reveal.value
                    val scale = 0.96f + 0.04f * reveal.value
                    scaleX = scale; scaleY = scale
                    translationY = (1f - reveal.value) * 20.dp.toPx()
                }.padding(horizontal = 16.dp, vertical = 17.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("NOVA  /  ${biggestPeriodName(period)} HISTORIQUE", color = Color.White.copy(alpha = 0.83f), fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 0.95.sp)
                        Text(context, color = Color.White.copy(alpha = 0.69f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    rank?.let { PremiumRankSeal(it, accent) }
                }
                Spacer(Modifier.height(15.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(100.dp)) {
                            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent)), radius = size.minDimension / 2f)
                            drawCircle(accent.copy(alpha = 0.83f), radius = size.minDimension * 0.43f, style = Stroke(1.8.dp.toPx()))
                        }
                        CoverArt(imageUrl, name, 76, circle = isArtist, zoomable = true)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${biggestPeriodName(period)}  /  ${if (rank == 1) "PIC N°1" else if (rank == null) "ARCHIVE DE PIC" else "MOMENT CLASSÉ"}",
                            color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 0.7.sp
                        )
                        Text(name, color = Color.White, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        subtitle?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color.White.copy(alpha = 0.69f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(displayDate.uppercase(Locale.FRANCE), color = Color.White.copy(alpha = 0.72f), fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, letterSpacing = 0.8.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(formatCount((score * reveal.value).toLong()), color = Color.White, fontWeight = FontWeight.Black, fontSize = 45.sp, lineHeight = 48.sp, maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.padding(bottom = 5.dp)) {
                        Text("ÉCOUTES", color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, letterSpacing = 1.sp)
                        peakPosition?.let { Text("RANG  #$it", color = Color.White.copy(alpha = 0.73f), fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.labelSmall) }
                    }
                }
                Text("Un pic d'écoute enregistré dans cette cadence.", color = Color.White.copy(alpha = 0.71f), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(11.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (reveal.value >= 0.99f) "↻ REJOUER LE MOMENT" else "✦ RÉVÉLATION EN COURS", color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.10f)).border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(10.dp)).clickable { replayToken++ }.padding(horizontal = 10.dp, vertical = 8.dp))
                    Spacer(Modifier.weight(1f))
                    Text("SANS SON  ·  ${biggestPeriodName(period)}", color = Color.White.copy(alpha = 0.55f), fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (story.timeline.isNotEmpty()) {
            PeakTrajectoryPanel(story.timeline, selectedIndex, accent, period) { selectedIndex = it }
            selectedPoint?.let { point ->
                val selectedEvent = point.date == peakDate
                PremiumMiniFact(
                    when {
                        selectedEvent -> "MOMENT CLASSÉ"
                        point.highlight -> "PIC PERSONNEL DE L'ENTITÉ"
                        else -> "AUTRE POINT D'ARCHIVE"
                    },
                    "${point.label}  ·  ${formatCount((point.plays ?: 0).toLong())} écoutes" + (point.position?.let { "  ·  #$it" } ?: ""),
                    if (selectedEvent || point.highlight) NovaColors.Gold else accent,
                    Modifier.fillMaxWidth().padding(top = 7.dp)
                )
            }
        }
        if (story.facts.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(18.dp)).background(theme.surface.copy(alpha = 0.93f)).border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(18.dp)).padding(13.dp)) {
                Text("LES PREUVES DU MOMENT", color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 0.9.sp)
                story.facts.filterNot { it.first == "Pic" }.take(3).forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Top) {
                        Text(label, color = theme.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.46f))
                        Text(value, color = theme.text, fontWeight = FontWeight.SemiBold, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, modifier = Modifier.weight(0.54f))
                    }
                }
            }
        }
        PremiumShareButton("Créer l'affiche du pic", "${biggestPeriodName(period)} · moment prêt à partager", sharing, shareError, accent, onShare)
    }
}

@Composable
private fun PeakTrajectoryPanel(points: List<RecordExplainer.Point>, selectedIndex: Int, accent: Color, period: Period?, onSelect: (Int) -> Unit) {
    val theme = Nova.theme
    val shape = RoundedCornerShape(22.dp)
    Column(Modifier.fillMaxWidth().padding(top = 13.dp).clip(shape).background(theme.surface.copy(alpha = 0.97f)).border(1.dp, accent.copy(alpha = 0.29f), shape).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("LA COURBE DU MOMENT", color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, letterSpacing = 0.85.sp)
                Text("${points.size} apparitions archivées · ${period?.frLabel ?: "période"} uniquement", color = theme.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("✹", color = accent, fontWeight = FontWeight.Black, fontSize = 22.sp)
        }
        Spacer(Modifier.height(8.dp))
        PeakTrajectoryChart(points, selectedIndex, accent)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(points, key = { index, point -> "peak-period-${point.date}-$index" }) { index, point ->
                val selected = index == selectedIndex
                Column(
                    Modifier.width(92.dp).clip(RoundedCornerShape(12.dp)).background(if (selected) accent.copy(alpha = 0.16f) else theme.background.copy(alpha = 0.60f))
                        .border(1.dp, (if (point.highlight) NovaColors.Gold else accent).copy(alpha = if (selected) 0.69f else 0.22f), RoundedCornerShape(12.dp))
                        .clickable { onSelect(index) }.padding(8.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(if (point.date == points.getOrNull(selectedIndex)?.date) "✦ PIC SÉLECTIONNÉ" else point.label, color = if (point.highlight) NovaColors.Gold else accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(point.label, color = theme.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatCount((point.plays ?: 0).toLong()), color = theme.text, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text(point.position?.let { "RANG #$it" } ?: "RANG —", color = theme.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun PeakTrajectoryChart(points: List<RecordExplainer.Point>, selectedIndex: Int, accent: Color) {
    val theme = Nova.theme
    val indices = remember(points, selectedIndex) {
        if (points.size <= 54) points.indices.toList() else {
            val stride = ceil(points.size / 52.0).toInt().coerceAtLeast(1)
            buildSet {
                add(0); add(points.lastIndex); add(selectedIndex.coerceIn(0, points.lastIndex))
                points.forEachIndexed { index, point -> if (point.highlight || index % stride == 0) add(index) }
            }.sorted()
        }
    }
    val peakPlays = remember(points, indices) { max(1, indices.mapNotNull { points[it].plays }.maxOrNull() ?: 1) }
    Canvas(Modifier.fillMaxWidth().height(147.dp).clip(RoundedCornerShape(16.dp)).background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.12f), theme.background.copy(alpha = 0.70f)))).border(1.dp, accent.copy(alpha = 0.16f), RoundedCornerShape(16.dp))) {
        val left = 22.dp.toPx(); val right = size.width - 14.dp.toPx(); val top = 14.dp.toPx(); val bottom = size.height - 14.dp.toPx()
        fun x(index: Int) = if (points.size <= 1) (left + right) / 2f else left + (right - left) * index / points.lastIndex.toFloat()
        fun y(plays: Int) = bottom - (plays.toFloat() / peakPlays.coerceAtLeast(1)) * (bottom - top)
        drawLine(Color.White.copy(alpha = 0.13f), Offset(left, bottom), Offset(right, bottom), strokeWidth = 1.dp.toPx())
        drawLine(Color.White.copy(alpha = 0.10f), Offset(left, (top + bottom) / 2f), Offset(right, (top + bottom) / 2f), strokeWidth = 1.dp.toPx())
        val nodes = indices.mapNotNull { index -> points[index].plays?.let { Triple(index, points[index], it) } }
        if (nodes.size > 1) {
            val line = Path().apply { nodes.forEachIndexed { i, node -> if (i == 0) moveTo(x(node.first), y(node.third)) else lineTo(x(node.first), y(node.third)) } }
            drawPath(line, accent.copy(alpha = 0.94f), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            val fill = Path().apply { moveTo(x(nodes.first().first), bottom); nodes.forEach { lineTo(x(it.first), y(it.third)) }; lineTo(x(nodes.last().first), bottom); close() }
            drawPath(fill, Brush.verticalGradient(listOf(accent.copy(alpha = 0.20f), Color.Transparent), startY = top, endY = bottom))
        }
        nodes.forEach { (index, point, plays) ->
            val marker = if (index == selectedIndex || point.highlight) NovaColors.Gold else accent
            val center = Offset(x(index), y(plays))
            drawCircle(marker.copy(alpha = 0.22f), radius = if (index == selectedIndex) 12.dp.toPx() else 7.dp.toPx(), center = center)
            drawCircle(marker, radius = if (index == selectedIndex) 5.dp.toPx() else 3.dp.toPx(), center = center)
        }
    }
}

@Composable
private fun PremiumRankSeal(rank: Int, accent: Color) {
    val color = if (rank == 1) NovaColors.Gold else accent
    Column(Modifier.clip(RoundedCornerShape(13.dp)).background(color.copy(alpha = 0.14f)).border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(13.dp)).padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (rank == 1) "N°1" else "RANG", color = Color.White.copy(alpha = 0.68f), fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        Text(if (rank == 1) "✦ #1" else "#$rank", color = color, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun PremiumMiniFact(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    val theme = Nova.theme
    Column(modifier.clip(RoundedCornerShape(13.dp)).background(accent.copy(alpha = 0.075f)).border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(13.dp)).padding(horizontal = 9.dp, vertical = 8.dp)) {
        Text(label.uppercase(Locale.FRANCE), color = theme.textSecondary, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = theme.text, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PremiumShareButton(title: String, subtitle: String, sharing: Boolean, shareError: Boolean, accent: Color, onClick: () -> Unit) {
    val theme = Nova.theme
    Column(Modifier.fillMaxWidth().padding(top = 11.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.19f), accent.copy(alpha = 0.07f))))
                .border(1.dp, accent.copy(alpha = 0.34f), RoundedCornerShape(16.dp)).clickable(enabled = !sharing, onClick = onClick).padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (sharing) "◌" else "✦", color = accent, fontWeight = FontWeight.Black, fontSize = 22.sp)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(if (sharing) "L'affiche se prépare…" else title, color = theme.text, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                Text(subtitle, color = theme.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            }
            Text("NOVA", color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
        }
        if (shareError) Text("L'affiche n'a pas pu être créée. Réessaie dans un instant.", color = Color(0xFFFF7D83), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun PremiumStageCanvas(accent: Color, phase: Float, modifier: Modifier = Modifier, overlayOnly: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "premium-stage-glow")
    val drift by transition.animateFloat(0.16f, 0.84f, infiniteRepeatable(tween(5400), RepeatMode.Reverse), label = "premium-stage-drift")
    Canvas(modifier) {
        if (overlayOnly) drawRect(Color.Black.copy(alpha = 0.36f)) else drawRect(Brush.verticalGradient(listOf(Color(0xFF141018), Color(0xFF07080D))))
        val center = Offset(size.width * (0.22f + 0.55f * drift), size.height * 0.05f)
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.31f), accent.copy(alpha = 0.07f), Color.Transparent), center = center, radius = max(size.width, size.height) * 0.78f), radius = max(size.width, size.height) * 0.78f, center = center)
        val phaseValue = phase.coerceIn(0f, 1f)
        val beam = Path().apply {
            moveTo(size.width * (0.45f + 0.12f * phaseValue), 0f)
            lineTo(size.width * (0.61f + 0.18f * phaseValue), 0f)
            lineTo(size.width * (1.02f + 0.05f * phaseValue), size.height * 0.84f)
            lineTo(size.width * (0.05f + 0.08f * phaseValue), size.height * 0.84f)
            close()
        }
        drawPath(beam, Brush.linearGradient(listOf(Color.Transparent, accent.copy(alpha = 0.045f), accent.copy(alpha = 0.11f), Color.Transparent), start = Offset(size.width * 0.5f, 0f), end = Offset(size.width * 0.6f, size.height)))
        drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, size.height * 0.84f), Offset(size.width, size.height * 0.84f), strokeWidth = 1.dp.toPx())
    }
}
