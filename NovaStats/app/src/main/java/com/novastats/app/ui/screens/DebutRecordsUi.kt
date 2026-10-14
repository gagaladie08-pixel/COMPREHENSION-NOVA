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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novastats.app.data.db.dao.RecordRow
import com.novastats.app.data.repository.RecordExplainer
import com.novastats.app.domain.Period
import com.novastats.app.domain.RecordDef
import com.novastats.app.domain.RecordCategory
import com.novastats.app.ui.theme.Nova
import com.novastats.app.ui.theme.NovaColors
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

/** Identifiants de la seule famille dédiée à la cérémonie « La Nuit des Premières ». */
internal val DEBUT_RECORD_IDS = setOf("MOST_DEBUT_1", "MOST_DEBUT_TOP5", "MOST_DEBUT_TOP10", "BIGGEST_DEBUT")

internal fun debutAccent(recordId: String, fallback: Color): Color = when (recordId) {
    "MOST_DEBUT_1" -> NovaColors.Gold
    "MOST_DEBUT_TOP5" -> Color(0xFF83B9FF)
    "MOST_DEBUT_TOP10" -> Color(0xFFC49BFF)
    "BIGGEST_DEBUT" -> Color(0xFFFF9568)
    else -> fallback
}

private fun debutBadge(recordId: String): String = when (recordId) {
    "MOST_DEBUT_1" -> "01"
    "MOST_DEBUT_TOP5" -> "05"
    "MOST_DEBUT_TOP10" -> "10"
    else -> "✦"
}

private fun debutMoment(recordId: String): String = when (recordId) {
    "MOST_DEBUT_1" -> "PREMIÈRE AU SOMMET"
    "MOST_DEBUT_TOP5" -> "ACCÈS AU PREMIER CERCLE"
    "MOST_DEBUT_TOP10" -> "LANCEMENT REMARQUÉ"
    else -> "IMPACT DÈS L'ENTRÉE"
}

@Composable
internal fun DebutFamilyBanner(color: Color) {
    val theme = Nova.theme
    val transition = rememberInfiniteTransition()
    val beam by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(tween(6200), repeatMode = RepeatMode.Reverse)
    )
    val shape = RoundedCornerShape(26.dp)

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp)) {
        Text(
            "NOVA  /  CÉRÉMONIE DES DÉBUTS",
            color = debutAccent("MOST_DEBUT_1", color),
            fontWeight = FontWeight.Black,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(start = 3.dp, bottom = 8.dp)
        )
        Box(
            Modifier.fillMaxWidth().clip(shape)
                .background(Color(0xFF0B0C14))
                .border(1.dp, color.copy(alpha = 0.38f), shape)
        ) {
            DebutStageLightCanvas(color, beam, Modifier.matchParentSize())
            Column(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 17.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("LA NUIT DES", color = Color.White.copy(alpha = 0.70f), fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 2.sp)
                        Text("PREMIÈRES", color = Color.White, fontWeight = FontWeight.Black, fontSize = 29.sp, letterSpacing = 1.1.sp, lineHeight = 31.sp)
                    }
                    Box(
                        Modifier.size(62.dp).clip(CircleShape)
                            .background(Brush.radialGradient(listOf(NovaColors.Gold.copy(alpha = 0.35f), NovaColors.Gold.copy(alpha = 0.04f))))
                            .border(1.dp, NovaColors.Gold.copy(alpha = 0.58f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✦", color = NovaColors.Gold, fontSize = 31.sp, fontWeight = FontWeight.Black)
                    }
                }
                Text(
                    "Un premier rang. Une première nuit. Une entrée qui reste dans les archives.",
                    color = Color.White.copy(alpha = 0.76f), style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp)
                )
                Spacer(Modifier.height(13.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    DebutTierPill("#1", NovaColors.Gold)
                    DebutTierPill("TOP 5", Color(0xFF83B9FF))
                    DebutTierPill("TOP 10", Color(0xFFC49BFF))
                    Spacer(Modifier.weight(1f))
                    Text("4 CADENCES", color = Color.White.copy(alpha = 0.65f), fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 0.5.sp)
                }
            }
        }
    }
}

@Composable
private fun DebutTierPill(label: String, accent: Color) {
    Text(
        label, color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.34f), RoundedCornerShape(9.dp)).padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

@Composable
internal fun DebutRecordCard(def: RecordDef, color: Color, onClick: () -> Unit) {
    val theme = Nova.theme
    val accent = debutAccent(def.id, color)
    val shape = RoundedCornerShape(23.dp)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(10.dp, shape, ambientColor = accent.copy(alpha = 0.10f), spotColor = accent.copy(alpha = 0.13f))
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.18f), theme.surface.copy(alpha = 0.98f), theme.surface)))
            .border(1.dp, accent.copy(alpha = 0.38f), shape)
            .clickable(onClick = onClick)
            .padding(13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(61.dp).clip(RoundedCornerShape(18.dp))
                    .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.34f), theme.background.copy(alpha = 0.92f))))
                    .border(1.2.dp, accent.copy(alpha = 0.48f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(debutBadge(def.id), color = accent, fontWeight = FontWeight.Black, fontSize = if (def.id == "BIGGEST_DEBUT") 28.sp else 22.sp, letterSpacing = 0.6.sp)
                Text(def.emoji, fontSize = 13.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "NOVA PREMIÈRES  ·  ${debutMoment(def.id)}",
                    color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    letterSpacing = 0.65.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(def.title, color = theme.text, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(def.description, color = theme.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
            }
            Text("›", color = accent, fontWeight = FontWeight.Light, fontSize = 31.sp, modifier = Modifier.padding(start = 5.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "JOUR  ·  SEMAINE  ·  MOIS  ·  ANNÉE",
                color = theme.textSecondary, fontWeight = FontWeight.Bold,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis, letterSpacing = 0.25.sp,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text("↗", color = accent, fontWeight = FontWeight.Black, fontSize = 20.sp)
        }
    }
}

@Composable
internal fun DebutLeaderboardStage(
    def: RecordDef,
    period: Period?,
    category: RecordCategory,
    subLabel: String?,
    rows: List<RecordRow>,
    color: Color,
    onEntry: (RecordRow, Int) -> Unit
) {
    if (rows.isEmpty()) return
    val theme = Nova.theme
    val accent = debutAccent(def.id, color)
    val shape = RoundedCornerShape(25.dp)
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
        Box(
            Modifier.fillMaxWidth().clip(shape).background(Color(0xFF0B0C14)).border(1.dp, accent.copy(alpha = 0.32f), shape)
        ) {
            DebutStageLightCanvas(accent, 0.58f, Modifier.matchParentSize())
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("OPENING NIGHT  /  ${debutMoment(def.id)}", color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                        Text(
                            listOfNotNull(period?.label, category.label, subLabel).joinToString(" · "),
                            color = Color.White.copy(alpha = 0.68f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                        )
                    }
                    Text("TOP ${minOf(rows.size, 10)}", color = Color.White.copy(alpha = 0.72f), fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(10.dp))
                DebutChampionPanel(def, period, rows.first(), accent) { onEntry(rows.first(), 1) }
                if (rows.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rows.drop(1).take(2).forEachIndexed { index, row ->
                            DebutRunnerPanel(def, period, row, index + 2, accent, Modifier.weight(1f)) { onEntry(row, index + 2) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DebutChampionPanel(def: RecordDef, period: Period?, row: RecordRow, accent: Color, onClick: () -> Unit) {
    val theme = Nova.theme
    val shape = RoundedCornerShape(19.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Color.White.copy(alpha = 0.075f))
            .border(1.dp, accent.copy(alpha = 0.38f), shape).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(82.dp)) {
                drawCircle(accent.copy(alpha = 0.17f), radius = size.minDimension * 0.48f)
                drawCircle(accent.copy(alpha = 0.72f), radius = size.minDimension * 0.48f, style = Stroke(width = 1.5.dp.toPx()))
            }
            CoverArt(row.imageUrl, row.name ?: "?", size = 66, circle = row.r.category == RecordCategory.ARTIST.dbName)
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✦  DÉTENTEUR N°1", color = NovaColors.Gold, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 0.8.sp)
                Spacer(Modifier.weight(1f))
                Text("🏆", fontSize = 19.sp)
            }
            Text(row.name ?: "Inconnu", color = Color.White, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(formatRecordValue(def, period, row.r.value), color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
            row.subtitle?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color.White.copy(alpha = 0.64f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Text("↗", color = accent, fontSize = 22.sp, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun DebutRunnerPanel(def: RecordDef, period: Period?, row: RecordRow, rank: Int, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val theme = Nova.theme
    val shape = RoundedCornerShape(15.dp)
    Row(
        modifier.clip(shape).background(theme.surface.copy(alpha = 0.72f)).border(1.dp, accent.copy(alpha = 0.22f), shape)
            .clickable(onClick = onClick).padding(horizontal = 9.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverArt(row.imageUrl, row.name ?: "?", size = 43, circle = row.r.category == RecordCategory.ARTIST.dbName)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("#${rank}  ${row.name ?: "Inconnu"}", color = theme.text, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatRecordValue(def, period, row.r.value), color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun DebutArchiveSpotlight(
    def: RecordDef,
    name: String,
    subtitle: String?,
    imageUrl: String?,
    isArtist: Boolean,
    metric: String,
    rank: Int?,
    period: Period?,
    context: String,
    story: RecordExplainer.Story,
    color: Color,
    sharing: Boolean,
    shareError: Boolean,
    onShare: () -> Unit,
    onItemClick: (RecordExplainer.Item) -> Unit
) {
    val theme = Nova.theme
    val accent = debutAccent(def.id, color)
    val shape = RoundedCornerShape(29.dp)
    var replayToken by remember(def.id, name, period) { mutableStateOf(0) }
    val reveal = remember(def.id, name, period) { Animatable(0f) }
    var selectedIndex by remember(story.timeline) {
        mutableStateOf(story.timeline.indexOfFirst { it.highlight }.coerceAtLeast(0))
    }
    LaunchedEffect(story.headline, replayToken) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(durationMillis = 1180))
    }
    val selectedPoint = story.timeline.getOrNull(selectedIndex)

    Column(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 6.dp)) {
        Text(
            "✦  LA NUIT DES PREMIÈRES",
            color = accent, fontWeight = FontWeight.Black,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            letterSpacing = 1.25.sp, modifier = Modifier.padding(start = 3.dp, bottom = 8.dp)
        )
        Box(
            Modifier.fillMaxWidth().shadow(17.dp, shape, ambientColor = accent.copy(alpha = 0.15f), spotColor = accent.copy(alpha = 0.2f))
                .clip(shape).background(Color(0xFF090A11)).border(1.3.dp, accent.copy(alpha = 0.46f), shape)
        ) {
            BlurredBackdrop(imageUrl, accent, Modifier.matchParentSize())
            DebutStageLightCanvas(accent, reveal.value, Modifier.matchParentSize(), overlayOnly = true)
            Column(
                Modifier.fillMaxWidth().graphicsLayer {
                    alpha = reveal.value
                    val scale = 0.965f + 0.035f * reveal.value
                    scaleX = scale
                    scaleY = scale
                    translationY = (1f - reveal.value) * 18.dp.toPx()
                }.padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("NOVA  /  PREMIÈRE SCÈNE", color = Color.White.copy(alpha = 0.82f), fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 1.25.sp)
                        Text(context.ifBlank { period?.label ?: "Archive du classement" }, color = Color.White.copy(alpha = 0.68f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    rank?.let {
                        Column(
                            Modifier.clip(RoundedCornerShape(13.dp)).background((if (it == 1) NovaColors.Gold else accent).copy(alpha = 0.17f))
                                .border(1.dp, (if (it == 1) NovaColors.Gold else accent).copy(alpha = 0.52f), RoundedCornerShape(13.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(if (it == 1) "DÉTENTEUR" else "RANG", color = Color.White.copy(alpha = 0.68f), fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            Text(if (it == 1) "✦ #1" else "#$it", color = if (it == 1) NovaColors.Gold else Color.White, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                Spacer(Modifier.height(15.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(100.dp)) {
                            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.28f), Color.Transparent)), radius = size.minDimension / 2f)
                            drawCircle(accent.copy(alpha = 0.78f), radius = size.minDimension * 0.43f, style = Stroke(width = 1.6.dp.toPx()))
                        }
                        CoverArt(imageUrl, name, size = 76, circle = isArtist, zoomable = true)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(debutMoment(def.id), color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 0.75.sp)
                        Text(name, color = Color.White, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        subtitle?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color.White.copy(alpha = 0.70f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
                Spacer(Modifier.height(17.dp))
                Text(def.title.uppercase(Locale.FRANCE), color = Color.White.copy(alpha = 0.67f), fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 1.2.sp)
                Text(story.headline.ifBlank { metric }, color = Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp, lineHeight = 31.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(metric, color = accent, fontWeight = FontWeight.Black, fontSize = 21.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (reveal.value >= 0.99f) "↻ REJOUER" else "● RÉVÉLATION",
                        color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(accent.copy(alpha = 0.10f))
                            .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(9.dp))
                            .clickable { replayToken++ }.padding(horizontal = 9.dp, vertical = 7.dp)
                    )
                }
            }
        }

        if (story.timeline.isNotEmpty()) {
            DebutTrajectoryPanel(story.timelineTitle ?: "La première apparition", story.timeline, selectedIndex, reveal.value, accent) { selectedIndex = it }
            selectedPoint?.let { point ->
                DebutSelectedMoment(point, selectedIndex, story.timeline.size, accent)
            }
        }
        if (story.items.isNotEmpty()) {
            DebutContributorsGallery(story.items, accent, onItemClick)
        }
        if (story.narrative.isNotBlank()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 11.dp).clip(RoundedCornerShape(18.dp))
                    .background(theme.surface.copy(alpha = 0.92f)).border(1.dp, accent.copy(alpha = 0.24f), RoundedCornerShape(18.dp))
                    .padding(14.dp)
            ) {
                Text("LE RÉCIT DU PREMIER SOIR", color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 0.9.sp)
                Spacer(Modifier.height(6.dp))
                Text(story.narrative, color = theme.text, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, lineHeight = 20.sp)
            }
        }
        if (story.facts.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("REPÈRES DE L'ARCHIVE", color = theme.textSecondary, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 1.sp, modifier = Modifier.padding(start = 3.dp, bottom = 6.dp))
            story.facts.chunked(2).forEach { factRow ->
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    factRow.forEach { (label, value) -> DebutFactCard(label, value, accent, Modifier.weight(1f)) }
                    if (factRow.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (story.context.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp))
                    .background(theme.surface.copy(alpha = 0.86f)).border(1.dp, accent.copy(alpha = 0.18f), RoundedCornerShape(16.dp)).padding(12.dp)
            ) {
                Text("EN CONTEXTE", color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 0.8.sp)
                story.context.forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Top) {
                        Text(label, color = theme.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.43f))
                        Text(value, color = theme.text, fontWeight = FontWeight.SemiBold, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.57f), textAlign = TextAlign.End)
                    }
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
                .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.19f), accent.copy(alpha = 0.08f))))
                .border(1.dp, accent.copy(alpha = 0.34f), RoundedCornerShape(15.dp))
                .clickable(enabled = !sharing, onClick = onShare).padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (sharing) "◌" else "✦", color = accent, fontWeight = FontWeight.Black, fontSize = 22.sp)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(if (sharing) "L'affiche se prépare…" else "Créer l'affiche de cérémonie", color = theme.text, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                Text("Une affiche de première · prête à partager", color = theme.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            }
            Text("NOVA", color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
        }
        if (shareError) Text("L'affiche n'a pas pu être créée. Réessaie dans un instant.", color = Color(0xFFFF7D83), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun DebutContributorsGallery(items: List<RecordExplainer.Item>, accent: Color, onItemClick: (RecordExplainer.Item) -> Unit) {
    val theme = Nova.theme
    Column(Modifier.fillMaxWidth().padding(top = 13.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 3.dp, bottom = 7.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("LE WALK OF FIRSTS", color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, letterSpacing = 1.1.sp)
                Text("Chaque entrée, sa date, son rang et son premier impact.", color = theme.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            }
            Text("${items.size} ARCHIVES", color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            itemsIndexed(items, key = { index, item -> "debut-${item.type}-${item.id}-$index" }) { index, item ->
                DebutEntryCard(index + 1, item, accent) { onItemClick(item) }
            }
        }
    }
}

@Composable
private fun DebutEntryCard(index: Int, item: RecordExplainer.Item, accent: Color, onClick: () -> Unit) {
    val theme = Nova.theme
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.width(205.dp).clip(shape).background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.13f), theme.surface, theme.surface)))
            .border(1.dp, accent.copy(alpha = 0.28f), shape).clickable(onClick = onClick).padding(11.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("ARCHIVE  ${index.toString().padStart(2, '0')}", color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 0.55.sp)
            Spacer(Modifier.weight(1f))
            Text("✦", color = NovaColors.Gold, fontSize = 15.sp)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(114.dp).clip(RoundedCornerShape(17.dp)).background(accent.copy(alpha = 0.13f)).border(1.dp, accent.copy(alpha = 0.32f), RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) {
                CoverArt(item.imageUrl, item.name, size = 100, circle = item.type == RecordCategory.ARTIST.dbName)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(item.name, color = theme.text, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.titleSmall, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
        Spacer(Modifier.height(6.dp))
        val position = item.entryPosition
        Text(
            position?.let { "ENTRÉE DIRECTE  ·  ${debutRankLabel(it)}" } ?: "PREMIÈRE ENTRÉE",
            color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        item.entryDateLabel?.let { Text(it, color = theme.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        item.entryPlays?.let { plays ->
            Text("${formatCount(plays)} écoutes", color = theme.text, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } ?: Text(item.detail, color = theme.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DebutTrajectoryPanel(title: String, points: List<RecordExplainer.Point>, selectedIndex: Int, reveal: Float, accent: Color, onSelect: (Int) -> Unit) {
    val theme = Nova.theme
    val shape = RoundedCornerShape(21.dp)
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(shape).background(theme.surface.copy(alpha = 0.96f)).border(1.dp, accent.copy(alpha = 0.27f), shape).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("LA TRAJECTOIRE DU DÉBUT", color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, letterSpacing = 0.85.sp)
                Text(title, color = theme.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("${points.size} PÉRIODES", color = theme.textSecondary, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(8.dp))
        DebutTrajectoryChart(points, selectedIndex, reveal, accent)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(points, key = { index, point -> "debut-point-${point.date}-$index" }) { index, point ->
                val selected = index == selectedIndex
                Column(
                    Modifier.width(88.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (selected) accent.copy(alpha = 0.16f) else theme.background.copy(alpha = 0.54f))
                        .border(1.dp, (if (point.highlight) NovaColors.Gold else accent).copy(alpha = if (selected) 0.66f else 0.22f), RoundedCornerShape(12.dp))
                        .clickable { onSelect(index) }.padding(8.dp)
                ) {
                    Text(if (point.highlight) "✦ PREMIÈRE" else point.label, color = if (point.highlight) NovaColors.Gold else accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(point.position?.let { "#${it}" } ?: "—", color = theme.text, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                    Text(point.plays?.let { "${formatCount(it)} ▶" } ?: "—", color = theme.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun DebutTrajectoryChart(points: List<RecordExplainer.Point>, selectedIndex: Int, progress: Float, accent: Color) {
    val theme = Nova.theme
    val nodes = remember(points, selectedIndex) {
        if (points.size <= 54) points.indices.toList()
        else {
            val stride = ceil(points.size / 52.0).toInt().coerceAtLeast(1)
            (buildSet {
                add(0)
                add(points.lastIndex)
                add(selectedIndex.coerceIn(0, points.lastIndex))
                points.forEachIndexed { index, point -> if (point.highlight || index % stride == 0) add(index) }
            }).sorted()
        }
    }
    val maxPosition = remember(points, nodes) { max(10, nodes.mapNotNull { points[it].position }.maxOrNull() ?: 10) }
    Canvas(
        Modifier.fillMaxWidth().height(145.dp).clip(RoundedCornerShape(15.dp))
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.10f), theme.background.copy(alpha = 0.70f))))
            .border(1.dp, accent.copy(alpha = 0.14f), RoundedCornerShape(15.dp))
    ) {
        val left = 22.dp.toPx()
        val right = size.width - 14.dp.toPx()
        val top = 14.dp.toPx()
        val bottom = size.height - 13.dp.toPx()
        fun x(index: Int) = if (points.size <= 1) (left + right) / 2f else left + (right - left) * index / (points.lastIndex.toFloat())
        fun y(position: Int) = top + (position - 1).coerceAtLeast(0).toFloat() / (maxPosition - 1).coerceAtLeast(1) * (bottom - top)
        listOf(1, maxPosition).distinct().forEach { mark ->
            val yy = y(mark)
            drawLine(Color.White.copy(alpha = 0.13f), Offset(left, yy), Offset(right, yy), strokeWidth = 1.dp.toPx())
        }
        val chartNodes = nodes.mapNotNull { index -> points[index].position?.let { Triple(index, points[index], it) } }
        chartNodes.zipWithNext().forEach { (from, to) ->
            val x1 = x(from.first); val x2 = x(to.first)
            if (x1 <= left + (right - left) * progress.coerceIn(0f, 1f)) {
                drawLine(accent.copy(alpha = 0.96f), Offset(x1, y(from.third)), Offset(x2, y(to.third)), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        chartNodes.forEach { (index, point, position) ->
            if (x(index) <= left + (right - left) * progress.coerceIn(0f, 1f)) {
                val selected = index == selectedIndex
                val marker = if (point.highlight) NovaColors.Gold else if (selected) Color.White else accent
                drawCircle(marker.copy(alpha = 0.22f), radius = if (selected) 13.dp.toPx() else 8.dp.toPx(), center = Offset(x(index), y(position)))
                drawCircle(marker, radius = if (selected) 5.dp.toPx() else 3.dp.toPx(), center = Offset(x(index), y(position)))
            }
        }
        drawContext.canvas.nativeCanvas.drawText("#1", 7.dp.toPx(), y(1) + 5.dp.toPx(), android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.sp.toPx(); color = android.graphics.Color.LTGRAY })
        drawContext.canvas.nativeCanvas.drawText("#$maxPosition", 7.dp.toPx(), y(maxPosition) + 5.dp.toPx(), android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.sp.toPx(); color = android.graphics.Color.LTGRAY })
    }
}

@Composable
private fun DebutSelectedMoment(point: RecordExplainer.Point, index: Int, total: Int, accent: Color) {
    val theme = Nova.theme
    Row(
        Modifier.fillMaxWidth().padding(top = 7.dp).clip(RoundedCornerShape(13.dp))
            .background(Brush.horizontalGradient(listOf(if (point.highlight) NovaColors.Gold.copy(alpha = 0.16f) else accent.copy(alpha = 0.12f), theme.background.copy(alpha = 0.5f))))
            .border(1.dp, (if (point.highlight) NovaColors.Gold else accent).copy(alpha = 0.27f), RoundedCornerShape(13.dp)).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (point.highlight) "✦" else "${index + 1}", color = if (point.highlight) NovaColors.Gold else accent, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(if (point.highlight) "L'ENTRÉE QUI A TOUT LANCÉ" else "ÉTAPE ${index + 1} / $total", color = if (point.highlight) NovaColors.Gold else accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            Text(point.label, color = theme.text, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(point.position?.let { "#${it}" } ?: "—", color = theme.text, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Text(point.plays?.let { "${formatCount(it)} ▶" } ?: "—", color = theme.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DebutFactCard(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    val theme = Nova.theme
    Column(
        modifier.clip(RoundedCornerShape(13.dp)).background(accent.copy(alpha = 0.075f))
            .border(1.dp, accent.copy(alpha = 0.20f), RoundedCornerShape(13.dp)).padding(horizontal = 9.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(label.uppercase(Locale.FRANCE), color = theme.textSecondary, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = theme.text, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DebutStageLightCanvas(accent: Color, phase: Float, modifier: Modifier = Modifier, overlayOnly: Boolean = false) {
    Canvas(modifier) {
        val p = phase.coerceIn(0f, 1f)
        if (overlayOnly) drawRect(Color.Black.copy(alpha = 0.32f))
        else drawRect(Brush.verticalGradient(listOf(Color(0xFF10111D), Color(0xFF07080D))))
        val glowCenter = Offset(size.width * (0.20f + 0.58f * p), size.height * 0.04f)
        drawCircle(
            Brush.radialGradient(listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.07f), Color.Transparent), center = glowCenter, radius = max(size.width, size.height) * 0.78f),
            radius = max(size.width, size.height) * 0.78f, center = glowCenter
        )
        val beam = Path().apply {
            moveTo(size.width * (0.48f + 0.18f * p), 0f)
            lineTo(size.width * (0.70f + 0.18f * p), 0f)
            lineTo(size.width * (1.05f + 0.05f * p), size.height * 0.82f)
            lineTo(size.width * (0.08f + 0.08f * p), size.height * 0.82f)
            close()
        }
        drawPath(
            beam,
            Brush.linearGradient(listOf(Color.Transparent, accent.copy(alpha = 0.055f), accent.copy(alpha = 0.11f), Color.Transparent), start = Offset(size.width * 0.5f, 0f), end = Offset(size.width * 0.6f, size.height))
        )
        drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, size.height * 0.84f), Offset(size.width, size.height * 0.84f), strokeWidth = 1.dp.toPx())
    }
}

private fun debutRankLabel(position: Int): String = when (position) {
    1 -> "#1"
    in 2..5 -> "TOP 5 · #$position"
    in 6..10 -> "TOP 10 · #$position"
    else -> "#$position"
}
