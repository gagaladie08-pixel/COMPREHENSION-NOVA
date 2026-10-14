package com.novastats.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novastats.app.data.repository.RecordExplainer
import com.novastats.app.ui.theme.Nova

/** Trois panneaux reliés par une même couronne : titre, artiste et album n°1 sur la même cadence. */
@Composable
fun PerfectAllKillTriadPanel(
    visual: RecordExplainer.PerfectAllKillVisual,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    accent: Color = Color(0xFF9B7BFF),
    sharing: Boolean = false,
    shareError: Boolean = false,
    onShare: (() -> Unit)? = null
) {
    val theme = Nova.theme
    val glow by rememberInfiniteTransition(label = "pak-triad-${visual.periodType}").animateFloat(
        initialValue = 0.18f,
        targetValue = 0.36f,
        animationSpec = infiniteRepeatable(tween(if (compact) 1800 else 1200), RepeatMode.Reverse),
        label = "pak-glow"
    )
    val shape = RoundedCornerShape(if (compact) 14.dp else 20.dp)
    Column(
        modifier.fillMaxWidth().clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = glow), theme.surface.copy(alpha = 0.98f), Color(0xFF3E2A68).copy(alpha = 0.18f))
                )
            )
            .border(if (compact) 1.dp else 1.5.dp, accent.copy(alpha = 0.38f + glow * 0.35f), shape)
            .padding(if (compact) 9.dp else 13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(
                Modifier.size(if (compact) 34.dp else 43.dp).shadow(8.dp, CircleShape, ambientColor = accent, spotColor = accent)
                    .clip(CircleShape).background(Brush.radialGradient(listOf(accent.copy(alpha = 0.42f), Color.Transparent)))
                    .border(1.dp, accent.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("💎", fontSize = if (compact) 18.sp else 23.sp) }
            Column(Modifier.weight(1f)) {
                Text(
                    "PERFECT ALL KILL",
                    color = accent,
                    fontWeight = FontWeight.Black,
                    fontSize = if (compact) 12.sp else 14.sp,
                    letterSpacing = if (compact) 0.7.sp else 1.1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${visual.cadenceLabel.uppercase()} · ${visual.dateLabel}",
                    color = theme.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(if (compact) 8.dp else 11.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PakPillar(
                label = "HOT 100",
                imageUrl = visual.trackImageUrl,
                name = visual.trackName,
                compact = compact,
                accent = Color(0xFF65C7FF),
                modifier = Modifier.weight(1f)
            )
            PakConnector(accent, compact)
            PakPillar(
                label = "ARTIST 50",
                imageUrl = visual.artistImageUrl,
                name = visual.artistName,
                compact = compact,
                accent = accent,
                isArtist = true,
                modifier = Modifier.weight(1f)
            )
            PakConnector(accent, compact)
            PakPillar(
                label = "ALBUMS 75",
                imageUrl = visual.albumImageUrl,
                name = visual.albumName,
                compact = compact,
                accent = Color(0xFFFFC86B),
                modifier = Modifier.weight(1f)
            )
        }
        if (!compact && visual.margins.isNotEmpty()) {
            Spacer(Modifier.height(11.dp))
            Text("DAUPHIN & ÉCART · CHAQUE CHART SÉPARÉMENT", color = accent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                visual.margins.forEach { margin ->
                    val chartAccent = when (margin.chartLabel) {
                        "Hot 100" -> Color(0xFF65C7FF)
                        "Artist 50" -> accent
                        else -> Color(0xFFFFC86B)
                    }
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(chartAccent.copy(alpha = 0.10f))
                            .border(1.dp, chartAccent.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 6.dp, vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(margin.chartLabel.uppercase(), color = chartAccent, fontWeight = FontWeight.Black, fontSize = 8.sp, maxLines = 1)
                        Text("#1 ${formatCount(margin.leaderPlays)}", color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        Text(margin.runnerUpName?.let { "#2 $it" } ?: "Pas de #2", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(margin.runnerUpPlays?.let { "${formatCount(it)} éc. #2" } ?: "—", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        Text(
                            margin.gap?.let { "+${formatCount(it)} d'avance" } ?: "Écart —",
                            color = chartAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(if (compact) 6.dp else 9.dp))
        Text(
            "Trois charts · un artiste · le même snapshot",
            color = theme.text,
            fontWeight = FontWeight.Bold,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (onShare != null) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
                    .background(if (sharing) accent.copy(alpha = 0.12f) else accent.copy(alpha = 0.19f))
                    .border(1.dp, accent.copy(alpha = 0.42f), RoundedCornerShape(11.dp))
                    .clickable(enabled = !sharing, onClick = onShare)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (sharing) "⏳ Préparation de l'affiche…" else "✦ PARTAGER L'AFFICHE PERFECT ALL KILL ✦",
                    color = if (sharing) theme.textSecondary else accent,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            }
            if (shareError) {
                Text(
                    "Impossible de préparer l'affiche. Réessaie dans un instant.",
                    color = Color(0xFFFF6B6B),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun PakPillar(
    label: String,
    imageUrl: String?,
    name: String,
    compact: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    isArtist: Boolean = false
) {
    val theme = Nova.theme
    Column(
        modifier.clip(RoundedCornerShape(if (compact) 10.dp else 13.dp))
            .background(theme.background.copy(alpha = 0.40f))
            .border(1.dp, accent.copy(alpha = 0.30f), RoundedCornerShape(if (compact) 10.dp else 13.dp))
            .padding(horizontal = if (compact) 5.dp else 7.dp, vertical = if (compact) 6.dp else 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = accent, fontWeight = FontWeight.Black, fontSize = if (compact) 8.sp else 10.sp, maxLines = 1)
        Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
        CoverArt(imageUrl, name, size = if (compact) 38 else 52, circle = isArtist)
        Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
        Text(
            name,
            color = theme.text,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = if (compact) 1 else 2,
            minLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis
        )
        Text("#1", color = accent, fontWeight = FontWeight.Black, fontSize = if (compact) 10.sp else 12.sp)
    }
}

@Composable
private fun PakConnector(accent: Color, compact: Boolean) {
    Text("→", color = accent, fontWeight = FontWeight.Black, fontSize = if (compact) 12.sp else 17.sp)
}
