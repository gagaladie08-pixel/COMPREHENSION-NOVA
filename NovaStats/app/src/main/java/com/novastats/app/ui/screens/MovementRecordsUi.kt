package com.novastats.app.ui.screens

import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novastats.app.data.repository.RecordExplainer
import com.novastats.app.domain.Dates
import com.novastats.app.domain.Period
import com.novastats.app.domain.RecordCatalog
import com.novastats.app.domain.RecordDef
import com.novastats.app.ui.theme.Nova
import com.novastats.app.ui.theme.NovaColors
import java.util.Locale
import kotlin.math.floor

/** Identifiants de la famille, utilisés uniquement pour brancher sa présentation dédiée. */
internal val MOVEMENT_RECORD_IDS = setOf(
    "BIGGEST_JUMP", "BIGGEST_FALL", "BIGGEST_COMEBACK", "BIGGEST_CLIMBER", "SLEEPER_HIT",
    "FASTEST_RISE", "LONGEST_ROAD", "MOST_REENTRIES", "LONGEST_ABSENCE_RETURN"
)

internal fun movementAccent(recordId: String, fallback: Color): Color = when (recordId) {
    "BIGGEST_JUMP", "BIGGEST_CLIMBER" -> Color(0xFF61D7C2)
    "BIGGEST_FALL" -> Color(0xFFFF7D83)
    "BIGGEST_COMEBACK", "LONGEST_ABSENCE_RETURN" -> Color(0xFFB99BFF)
    "SLEEPER_HIT" -> Color(0xFFFFC86B)
    "FASTEST_RISE" -> Color(0xFF63C9FF)
    "LONGEST_ROAD" -> NovaColors.Gold
    "MOST_REENTRIES" -> Color(0xFF8EE0A1)
    else -> fallback
}

/** Carte de galerie : le tracé est un emblème du record, jamais une donnée historique. */
@Composable
internal fun MovementRecordCard(def: RecordDef, color: Color, onClick: () -> Unit) {
    val theme = Nova.theme
    val accent = movementAccent(def.id, color)
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(10.dp, shape, ambientColor = accent.copy(alpha = 0.12f), spotColor = accent.copy(alpha = 0.16f))
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.18f), theme.surface.copy(alpha = 0.98f), theme.surface)))
            .border(1.dp, accent.copy(alpha = 0.36f), shape)
            .clickable(onClick = onClick)
            .padding(13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(58.dp).clip(RoundedCornerShape(17.dp))
                    .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.26f), theme.background.copy(alpha = 0.92f))))
                    .border(1.dp, accent.copy(alpha = 0.42f), RoundedCornerShape(17.dp)),
                contentAlignment = Alignment.Center
            ) {
                MovementGlyph(def.id, accent, Modifier.size(43.dp))
                Text(def.emoji, fontSize = 14.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "ARCHIVE DE TRAJECTOIRE",
                    color = accent.copy(alpha = 0.92f), fontWeight = FontWeight.Black,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.1.sp
                )
                Text(def.title, color = theme.text, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(def.description, color = theme.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
            }
            Text("›", color = accent, fontWeight = FontWeight.Light, fontSize = 30.sp, modifier = Modifier.padding(start = 5.dp))
        }
        Spacer(Modifier.height(11.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            def.periods.take(4).forEach { period -> MovementTag(period.frLabel.uppercase(Locale.FRANCE), accent) }
            Spacer(Modifier.weight(1f))
            Text("OUVRIR LE DOSSIER  ↗", color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 0.45.sp)
        }
    }
}

@Composable
private fun MovementTag(label: String, accent: Color) {
    val theme = Nova.theme
    Text(
        label, color = theme.textSecondary, fontWeight = FontWeight.Bold,
        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(theme.background.copy(alpha = 0.65f))
            .border(1.dp, accent.copy(alpha = 0.20f), RoundedCornerShape(7.dp)).padding(horizontal = 6.dp, vertical = 3.dp)
    )
}

@Composable
private fun MovementGlyph(recordId: String, accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val unit = w / 100f
        val coords = when (recordId) {
            "BIGGEST_JUMP" -> listOf(Offset(7f, 78f), Offset(34f, 71f), Offset(59f, 38f), Offset(91f, 17f))
            "BIGGEST_FALL" -> listOf(Offset(7f, 18f), Offset(35f, 30f), Offset(61f, 63f), Offset(91f, 80f))
            "BIGGEST_COMEBACK" -> listOf(Offset(7f, 55f), Offset(35f, 55f), Offset(65f, 55f), Offset(91f, 22f))
            "BIGGEST_CLIMBER" -> listOf(Offset(7f, 84f), Offset(27f, 66f), Offset(47f, 72f), Offset(69f, 34f), Offset(91f, 13f))
            "SLEEPER_HIT" -> listOf(Offset(7f, 82f), Offset(33f, 81f), Offset(54f, 66f), Offset(73f, 38f), Offset(91f, 14f))
            "FASTEST_RISE" -> listOf(Offset(7f, 84f), Offset(43f, 56f), Offset(70f, 32f), Offset(91f, 14f))
            "LONGEST_ROAD" -> listOf(Offset(7f, 82f), Offset(28f, 64f), Offset(47f, 66f), Offset(67f, 35f), Offset(91f, 14f))
            "MOST_REENTRIES" -> listOf(Offset(7f, 68f), Offset(27f, 31f), Offset(47f, 68f), Offset(68f, 31f), Offset(91f, 16f))
            else -> listOf(Offset(7f, 30f), Offset(31f, 72f), Offset(55f, 72f), Offset(78f, 30f), Offset(91f, 17f))
        }.map { Offset(it.x * unit, it.y * h / 100f) }
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(coords.first().x, coords.first().y)
            coords.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, accent.copy(alpha = 0.18f), style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round))
        drawPath(path, Brush.linearGradient(listOf(accent.copy(alpha = 0.56f), accent)), style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round))
        coords.forEachIndexed { index, point ->
            drawCircle(accent.copy(alpha = if (index == coords.lastIndex) 1f else 0.72f), radius = if (index == coords.lastIndex) 3.2.dp.toPx() else 2.dp.toPx(), center = point)
        }
    }
}

/** Scène premium et frise interactive, alimentées exclusivement par le récit déjà calculé. */
@Composable
internal fun MovementArchiveSpotlight(
    def: RecordDef,
    name: String,
    subtitle: String?,
    imageUrl: String?,
    isArtist: Boolean,
    metric: String,
    rank: Int?,
    period: Period?,
    zoneLimit: Int?,
    zoneLabel: String?,
    context: String,
    story: RecordExplainer.Story,
    color: Color,
    sharing: Boolean,
    shareError: Boolean,
    onShare: () -> Unit
) {
    val theme = Nova.theme
    val accent = movementAccent(def.id, color)
    val white = Color.White
    var selectedIndex by remember(story.timeline) {
        mutableStateOf(story.timeline.indexOfLast { it.highlight }.coerceAtLeast(0))
    }
    var replayToken by remember(story.timeline) { mutableStateOf(0) }
    val reveal = remember(story.timeline) { Animatable(0f) }
    LaunchedEffect(story.timeline, replayToken) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(durationMillis = 1250))
    }
    val selectedPoint = story.timeline.getOrNull(selectedIndex)
    val heroTitle = if (story.headline.isBlank() || story.headline == def.title) metric else story.headline
    val proofFacts = movementProofFacts(def.id, story.facts)
    val rule = movementRuleText(def.id)
    val shape = RoundedCornerShape(27.dp)

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
        Text(
            "✦  ARCHIVES DE TRAJECTOIRE",
            color = accent, fontWeight = FontWeight.Black,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 3.dp, bottom = 8.dp)
        )
        Column(
            Modifier.fillMaxWidth().shadow(14.dp, shape, ambientColor = accent.copy(alpha = 0.14f), spotColor = accent.copy(alpha = 0.18f))
                .clip(shape).background(theme.surface).border(1.3.dp, accent.copy(alpha = 0.46f), shape)
        ) {
            Box(Modifier.fillMaxWidth()) {
                BlurredBackdrop(imageUrl, accent, Modifier.matchParentSize())
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 17.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("NOVA  /  DOSSIER DE MOUVEMENT", color = white.copy(alpha = 0.78f), fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 1.1.sp)
                            Text(context.ifBlank { period?.frLabel ?: "Historique du chart" }, color = white.copy(alpha = 0.68f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (rank != null) {
                            Column(
                                Modifier.clip(RoundedCornerShape(13.dp)).background((if (rank == 1) NovaColors.Gold else accent).copy(alpha = 0.18f))
                                    .border(1.dp, (if (rank == 1) NovaColors.Gold else accent).copy(alpha = 0.55f), RoundedCornerShape(13.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("RANG", color = white.copy(alpha = 0.68f), fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                                Text("#$rank", color = if (rank == 1) NovaColors.Gold else white, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CoverArt(imageUrl, name, size = 64, circle = isArtist)
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(def.title.uppercase(Locale.FRANCE), color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(name, color = white, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            subtitle?.takeIf { it.isNotBlank() }?.let { Text(it, color = white.copy(alpha = 0.70f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                    Spacer(Modifier.height(17.dp))
                    Text("LE MOMENT DÉCISIF", color = white.copy(alpha = 0.68f), fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 1.3.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(heroTitle, color = white, fontWeight = FontWeight.Black, fontSize = 30.sp, lineHeight = 35.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (heroTitle != metric && !heroTitle.contains(metric, ignoreCase = true)) {
                        Text(metric, color = accent, fontWeight = FontWeight.Black, fontSize = 21.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.8f), white.copy(alpha = 0.05f)))))
                        Text("  TRAJECTOIRE CERTIFIÉE  ", color = white.copy(alpha = 0.65f), fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 0.9.sp)
                        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(white.copy(alpha = 0.05f), accent.copy(alpha = 0.8f)))))
                    }
                }
            }

            Column(Modifier.fillMaxWidth().background(theme.surface).padding(horizontal = 14.dp, vertical = 13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("CARTE DU MOUVEMENT", color = theme.text, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, letterSpacing = 0.7.sp)
                        Text("#1 au sommet · dates réelles du chart", color = theme.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        if (reveal.value >= 0.99f) "↻ REJOUER" else "● EN COURS",
                        color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(accent.copy(alpha = 0.10f))
                            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(9.dp))
                            .clickable { replayToken++ }.padding(horizontal = 9.dp, vertical = 7.dp)
                    )
                }
                Spacer(Modifier.height(7.dp))
                MovementRouteGraph(def.id, story.timeline, period, accent, selectedIndex, reveal.value, zoneLimit)
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("#1 · MEILLEUR RANG", color = theme.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                    Text(
                        if (zoneLimit != null && zoneLimit < Int.MAX_VALUE) "SEUIL · ${zoneLabel ?: "ZONE"} · #$zoneLimit" else "⋯ · PÉRIODE SANS APPARITION",
                        color = theme.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, textAlign = TextAlign.End
                    )
                }
                selectedPoint?.let { point ->
                    Spacer(Modifier.height(9.dp))
                    MovementSelectedMoment(def.id, story.timeline, selectedIndex, point, accent, zoneLimit)
                }
                Spacer(Modifier.height(8.dp))
                if (story.timeline.isEmpty()) {
                    Text("Aucun détail de trajectoire n'est disponible pour cette période.", color = theme.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                } else {
                    LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(story.timeline, key = { index, point -> "movement-${def.id}-${point.date}-$index" }) { index, point ->
                            if (index > 0) {
                                val previous = story.timeline[index - 1]
                                val gap = movementIndex(point.date, period, index) - movementIndex(previous.date, period, index - 1) - 1
                                if (gap > 0) MovementGapMarker(gap, period, accent)
                            }
                            MovementPointCard(def.id, story.timeline, index, point, index == selectedIndex, accent, zoneLimit) {
                                selectedIndex = index
                            }
                        }
                    }
                }
                if (proofFacts.isNotEmpty()) {
                    Spacer(Modifier.height(13.dp))
                    Text("REPÈRES VÉRIFIÉS", color = theme.textSecondary, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 0.9.sp)
                    Spacer(Modifier.height(6.dp))
                    proofFacts.chunked(2).forEach { rowFacts ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rowFacts.forEach { (label, value) ->
                                MovementFactCard(label, value, accent, Modifier.weight(1f))
                            }
                            if (rowFacts.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(rule, color = theme.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, lineHeight = 15.sp)
                Spacer(Modifier.height(9.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                        .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.17f), accent.copy(alpha = 0.08f))))
                        .border(1.dp, accent.copy(alpha = 0.30f), RoundedCornerShape(13.dp))
                        .clickable(enabled = !sharing, onClick = onShare).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (sharing) "◌" else "↗", color = accent, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (sharing) "Préparation de l'affiche…" else "Partager l'exploit", color = theme.text, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                        Text("Carte verticale · pochette · rangs · dates", color = theme.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                    }
                    Text("NOVA", color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                }
                if (shareError) Text("L'affiche n'a pas pu être créée. Réessaie dans un instant.", color = Color(0xFFFF7D83), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 5.dp))
                Spacer(Modifier.height(2.dp))
                Text("Les écoutes visibles sont un contexte, pas une causalité attribuée au mouvement.", color = theme.textSecondary.copy(alpha = 0.85f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun MovementRouteGraph(recordId: String, points: List<RecordExplainer.Point>, period: Period?, accent: Color, selectedIndex: Int, progress: Float, zoneLimit: Int?) {
    val theme = Nova.theme
    val nodes = remember(points, period) {
        points.mapIndexedNotNull { index, point ->
            point.position?.let { MotionNode(index, point, it, movementIndex(point.date, period, index)) }
        }
    }
    val finalHighlightIndex = remember(points) { points.indexOfLast { it.highlight } }
    Canvas(
        Modifier.fillMaxWidth().height(164.dp).clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.08f), theme.background.copy(alpha = 0.58f))))
            .border(1.dp, accent.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
    ) {
        val left = 18.dp.toPx()
        val right = size.width - 16.dp.toPx()
        val top = 15.dp.toPx()
        val bottom = size.height - 14.dp.toPx()
        val maxPosition = maxOf(
            10,
            nodes.maxOfOrNull { it.position } ?: 10,
            zoneLimit?.takeIf { it < Int.MAX_VALUE } ?: 10
        )
        val maxPeriodIndex = nodes.maxOfOrNull { it.periodIndex } ?: 0
        val minPeriodIndex = nodes.minOfOrNull { it.periodIndex } ?: 0
        val useOrdinal = nodes.size < 2 || maxPeriodIndex == minPeriodIndex
        val span = if (useOrdinal) (nodes.size - 1).coerceAtLeast(1).toFloat() else (maxPeriodIndex - minPeriodIndex).coerceAtLeast(1).toFloat()
        fun x(node: MotionNode): Float = if (useOrdinal) left + (right - left) * node.index / (nodes.size - 1).coerceAtLeast(1).toFloat()
        else left + (right - left) * (node.periodIndex - minPeriodIndex) / span
        fun y(position: Int): Float = top + ((position - 1).toFloat() / (maxPosition - 1).coerceAtLeast(1)).coerceIn(0f, 1f) * (bottom - top)

        listOf(1, (maxPosition / 2).coerceAtLeast(2), maxPosition).distinct().forEach { rankMark ->
            val yy = y(rankMark)
            drawLine(theme.textSecondary.copy(alpha = 0.13f), Offset(left, yy), Offset(right, yy), strokeWidth = 1.dp.toPx())
        }
        zoneLimit?.takeIf { it < Int.MAX_VALUE }?.let { targetRank ->
            val targetY = y(targetRank)
            drawLine(
                NovaColors.Gold.copy(alpha = 0.70f), Offset(left, targetY), Offset(right, targetY),
                strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
            )
        }
        val segmentProgress = progress.coerceIn(0f, 1f) * (nodes.size - 1).coerceAtLeast(0)
        nodes.zipWithNext().forEachIndexed { index, (from, to) ->
            val fraction = (segmentProgress - index).coerceIn(0f, 1f)
            if (fraction <= 0f) return@forEachIndexed
            val start = Offset(x(from), y(from.position))
            val fullEnd = Offset(x(to), y(to.position))
            val end = Offset(start.x + (fullEnd.x - start.x) * fraction, start.y + (fullEnd.y - start.y) * fraction)
            val missingPeriods = to.periodIndex - from.periodIndex > 1
            val outsideZone = zoneLimit != null && from.position > zoneLimit && to.position > zoneLimit
            if (!missingPeriods && !outsideZone) drawLine(accent.copy(alpha = 0.22f), start, end, strokeWidth = 10.dp.toPx(), cap = StrokeCap.Round)
            drawLine(
                color = when { missingPeriods -> accent.copy(alpha = 0.76f); outsideZone -> theme.textSecondary.copy(alpha = 0.62f); else -> accent },
                start = start,
                end = end,
                strokeWidth = if (missingPeriods) 2.dp.toPx() else 3.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = if (missingPeriods) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())) else null
            )
        }
        val completedNodes = if (nodes.size < 2) if (progress > 0f) 0 else -1 else floor(segmentProgress).toInt().coerceAtMost(nodes.lastIndex)
        nodes.forEach { node ->
            if (node.index > completedNodes) return@forEach
            val center = Offset(x(node), y(node.position))
            val finalMarker = node.point.highlight && (recordId == "MOST_REENTRIES" || node.index == finalHighlightIndex)
            val nodeColor = when {
                finalMarker -> NovaColors.Gold
                node.point.highlight -> accent
                zoneLimit != null && node.position > zoneLimit -> theme.textSecondary.copy(alpha = 0.65f)
                else -> accent.copy(alpha = 0.88f)
            }
            if (node.index == selectedIndex) drawCircle(NovaColors.Gold.copy(alpha = 0.18f), 11.dp.toPx(), center)
            drawCircle(nodeColor, if (node.index == selectedIndex) 5.dp.toPx() else 3.5.dp.toPx(), center)
            drawCircle(Color.White.copy(alpha = 0.95f), 1.5.dp.toPx(), center)
        }
        if (nodes.size > 1 && segmentProgress < nodes.size - 1) {
            val segment = floor(segmentProgress).toInt().coerceIn(0, nodes.lastIndex - 1)
            val fraction = (segmentProgress - segment).coerceIn(0f, 1f)
            val from = nodes[segment]
            val to = nodes[segment + 1]
            val start = Offset(x(from), y(from.position))
            val end = Offset(x(to), y(to.position))
            val head = Offset(start.x + (end.x - start.x) * fraction, start.y + (end.y - start.y) * fraction)
            drawCircle(accent.copy(alpha = 0.22f), 11.dp.toPx(), head)
            drawCircle(Color.White, 4.dp.toPx(), head)
        }
    }
}

@Composable
private fun MovementSelectedMoment(recordId: String, points: List<RecordExplainer.Point>, index: Int, point: RecordExplainer.Point, accent: Color, zoneLimit: Int?) {
    val theme = Nova.theme
    val label = movementMomentLabel(recordId, points, index, point, zoneLimit)
    val labelColor = if (movementHeroMarker(recordId, points, index)) NovaColors.Gold else accent
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(accent.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.24f), RoundedCornerShape(13.dp)).padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = labelColor, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(point.label, color = theme.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        }
        Text(point.position?.let { if (it == 1) "👑 #1" else "#$it" } ?: "—", color = if (point.position == 1) NovaColors.Gold else theme.text, fontWeight = FontWeight.Black, fontSize = 23.sp)
        point.plays?.let { Text("  ·  ${formatCount(it)} ▶", color = theme.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun MovementPointCard(recordId: String, points: List<RecordExplainer.Point>, index: Int, point: RecordExplainer.Point, selected: Boolean, accent: Color, zoneLimit: Int?, onClick: () -> Unit) {
    val theme = Nova.theme
    val color = when {
        selected -> accent
        movementHeroMarker(recordId, points, index) -> NovaColors.Gold
        point.highlight -> accent.copy(alpha = 0.78f)
        else -> theme.textSecondary
    }
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.width(if (point.note != null) 104.dp else 78.dp).clip(shape)
            .background(if (selected) accent.copy(alpha = 0.15f) else theme.background.copy(alpha = 0.52f))
            .border(1.dp, color.copy(alpha = if (selected) 0.62f else 0.20f), shape)
            .clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(movementMomentLabel(recordId, points, index, point, zoneLimit), color = color, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 8.sp)
        Text(point.label, color = theme.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(point.position?.let { if (it == 1) "👑" else "#$it" } ?: "—", color = if (point.position == 1) NovaColors.Gold else theme.text, fontWeight = FontWeight.Black, fontSize = 19.sp)
        point.note?.let { Text(it, color = accent, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, fontSize = 8.sp) }
        if (point.note == null) point.plays?.let { Text("${formatCount(it)} ▶", color = theme.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, fontSize = 9.sp) }
    }
}

@Composable
private fun MovementGapMarker(gap: Int, period: Period?, accent: Color) {
    val theme = Nova.theme
    val unit = period?.let { RecordCatalog.unitLabel(it, gap > 1) } ?: "période${if (gap > 1) "s" else ""}"
    Column(Modifier.width(48.dp).height(96.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("⋯", color = theme.textSecondary, fontWeight = FontWeight.Black, fontSize = 21.sp)
        Text("+$gap", color = accent, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        Text("$unit\nmanquée${if (gap > 1) "s" else ""}", color = theme.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, fontSize = 8.sp, lineHeight = 9.sp)
    }
}

@Composable
private fun MovementFactCard(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    val theme = Nova.theme
    Column(
        modifier.clip(RoundedCornerShape(11.dp)).background(accent.copy(alpha = 0.07f))
            .border(1.dp, accent.copy(alpha = 0.18f), RoundedCornerShape(11.dp)).padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(label.uppercase(Locale.FRANCE), color = theme.textSecondary, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 8.sp)
        Text(value, color = theme.text, fontWeight = FontWeight.Black, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private data class MotionNode(val index: Int, val point: RecordExplainer.Point, val position: Int, val periodIndex: Int)

private fun movementIndex(isoDate: String, period: Period?, fallback: Int): Int {
    if (period == null || period == Period.GLOBAL) return fallback
    return runCatching { RecordCatalog.periodIndex(Dates.parse(isoDate), period) }.getOrDefault(fallback)
}

private fun movementHeroMarker(recordId: String, points: List<RecordExplainer.Point>, index: Int): Boolean =
    points.getOrNull(index)?.highlight == true && (recordId == "MOST_REENTRIES" || index == points.indexOfLast { it.highlight })

private fun movementMomentLabel(recordId: String, points: List<RecordExplainer.Point>, index: Int, point: RecordExplainer.Point, zoneLimit: Int?): String {
    val marked = points.indices.filter { points[it].highlight }
    val isFirst = index == marked.firstOrNull()
    val isLast = index == marked.lastOrNull()
    fun zoneStatus(): String = when {
        zoneLimit == null || point.position == null -> "APPARITION"
        point.position <= zoneLimit -> "DANS LA ZONE"
        else -> "HORS ZONE"
    }
    return when (recordId) {
        "BIGGEST_JUMP", "BIGGEST_FALL" -> if (point.highlight) "MOMENT DÉCISIF" else "PASSAGE"
        "BIGGEST_COMEBACK" -> when { isFirst -> "AVANT L'ABSENCE"; isLast -> "RETOUR"; else -> "APPARITION" }
        "BIGGEST_CLIMBER", "SLEEPER_HIT" -> when { isFirst -> "ENTRÉE"; isLast -> "SOMMET"; else -> "ÉTAPE" }
        "FASTEST_RISE" -> when { isFirst -> "DÉPART"; isLast -> "OBJECTIF"; else -> zoneStatus() }
        "LONGEST_ROAD" -> when { isFirst -> "DÉPART"; isLast -> "OBJECTIF"; else -> "ÉTAPE" }
        "MOST_REENTRIES" -> if (point.highlight) "RETOUR ${points.take(index + 1).count { it.highlight }}" else zoneStatus()
        "LONGEST_ABSENCE_RETURN" -> when { isFirst -> "DÉPART DE L'ABSENCE"; isLast -> "RETOUR"; else -> zoneStatus() }
        else -> if (point.highlight) "JALON" else "APPARITION"
    }
}

internal fun movementProofFacts(recordId: String, facts: List<Pair<String, String>>): List<Pair<String, String>> {
    val predicates: List<(String) -> Boolean> = when (recordId) {
        "BIGGEST_JUMP", "BIGGEST_FALL" -> listOf({ it == "Avant" }, { it == "Après" })
        "BIGGEST_COMEBACK" -> listOf({ it == "Départ" }, { it == "Absence" }, { it == "Retour" })
        "BIGGEST_CLIMBER", "SLEEPER_HIT" -> listOf({ it == "Entrée dans le chart" }, { it == "Ascension" }, { it == "Meilleure position" })
        "FASTEST_RISE", "LONGEST_ROAD" -> listOf({ it == "Entrée dans le chart" }, { it == "Objectif atteint" }, { it == "Délai" })
        "MOST_REENTRIES" -> listOf({ it.startsWith("Apparitions dans ") }, { it == "Retours après absence" }, { it.startsWith("Première fois dans ") }, { it.startsWith("Dernière fois dans ") })
        "LONGEST_ABSENCE_RETURN" -> listOf({ it == "Plus longue absence" }, { it.startsWith("Première fois dans ") }, { it.startsWith("Dernière fois dans ") }, { it == "Retours après absence" })
        else -> emptyList()
    }
    val chosen = predicates.mapNotNull { predicate -> facts.firstOrNull { predicate(it.first) } }.distinctBy { it.first }
    return chosen.ifEmpty { facts.take(4) }.take(4)
}

internal fun movementRuleText(recordId: String): String = when (recordId) {
    "BIGGEST_JUMP", "BIGGEST_FALL" -> "Le record mesure le déplacement entre deux périodes consécutives. Les écoutes sont affichées comme contexte, sans causalité déduite."
    "BIGGEST_COMEBACK" -> "Le gain compare le rang avant l'absence au rang du retour; les seuils d'absence actuels restent ceux du record."
    "BIGGEST_CLIMBER" -> "L'écart va du rang d'entrée au meilleur rang observé : ce n'est pas un bond en une seule période."
    "SLEEPER_HIT" -> "Révélation tardive : entrée au-delà du Top 20 puis sommet dans le Top 10 au moins trois périodes plus tard."
    "FASTEST_RISE" -> "Le temps va de l'entrée au premier passage dans la zone choisie; les entrées directes restent exclues."
    "LONGEST_ROAD" -> "Le délai va de l'entrée au chart jusqu'au premier #1, périodes calendaires comprises."
    "MOST_REENTRIES" -> "Chaque retour compte après au moins une période d'absence; la cadence du chart reste distincte."
    "LONGEST_ABSENCE_RETURN" -> "Le score porte sur la durée d'absence avant le retour, pas sur le nombre de places gagnées."
    else -> "La trajectoire reprend les apparitions observées dans le chart sélectionné."
}
