package com.novastats.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.novastats.app.ui.theme.prideOnFlag
import com.novastats.app.ui.theme.prideFlagForKey
import com.novastats.app.ui.theme.prideChip
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalTextStyle
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.graphics.graphicsLayer
import coil.compose.AsyncImage
import com.novastats.app.domain.Period
import com.novastats.app.ui.navigation.NovaTab
import com.novastats.app.ui.theme.Nova
import com.novastats.app.ui.theme.MiniFlag
import com.novastats.app.ui.theme.PrideRibbon
import com.novastats.app.ui.theme.isPride
import com.novastats.app.ui.theme.prideBorder
import com.novastats.app.ui.theme.prideBrushFor
import com.novastats.app.ui.theme.prideFlagFor
import com.novastats.app.ui.theme.prideStripe
import com.novastats.app.ui.theme.prideWash
import com.novastats.app.ui.theme.rememberPrideSweep
import com.novastats.app.ui.theme.goldShimmer
import com.novastats.app.ui.theme.NovaColors
import java.util.Locale

/* ---------- Helpers de formatage ---------- */

fun formatDuration(ms: Long): String {
    val totalMin = ms / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}h ${String.format(Locale.FRANCE, "%02d", m)}min" else "${m}min"
}

fun formatCount(n: Int): String = formatCount(n.toLong())
fun formatCount(n: Long): String = String.format(Locale.FRANCE, "%,d", n).replace('\u00A0', ' ').replace('\u202F', ' ')

/**
 * « en 3 jours » — temps mis par un titre / album / artiste pour atteindre un palier de certification
 * ou un statut du Panthéon (colonne `time_to_certify_ms` / `time_to_reach_ms`).
 *
 * [now] sert de repli quand la durée n'a pas été mémorisée (anciennes données) : on la recalcule
 * depuis la date d'obtention. Renvoie `null` quand aucune des deux informations n'existe.
 */
fun formatElapsed(ms: Long?, certifiedAt: Long? = null, now: Long = System.currentTimeMillis()): String? {
    val duration = ms ?: certifiedAt?.let { now - it } ?: return null
    if (duration < 0) return null
    val minutes = duration / 60_000L
    return when {
        minutes < 1 -> "en moins d'une minute"
        minutes < 60 -> "en $minutes min"
        minutes < 24 * 60 -> "en ${minutes / 60} h"
        else -> {
            val days = duration / 86_400_000L
            when {
                days < 14 -> "en $days jour${if (days > 1) "s" else ""}"
                days < 60 -> "en ${days / 7} semaines"
                days < 365 -> "en ${days / 30} mois"
                else -> {
                    val years = days / 365
                    val rest = (days % 365) / 30
                    if (rest > 0) "en $years an${if (years > 1) "s" else ""} et $rest mois" else "en $years an${if (years > 1) "s" else ""}"
                }
            }
        }
    }
}

/**
 * Affichage des positions (Stats) :
 * #1 🥇 · #2 🥈 · #3 🥉 · #4→#10 🔥 · #11→#300 nombre seul · non classé — · hors top 300 ▼ 300+
 */
fun positionLabel(position: Int?, limit: Int = 300): String = when {
    position == null -> "—"
    position > limit -> "▼ $limit+"
    position == 1 -> "🥇 #1"
    position == 2 -> "🥈 #2"
    position == 3 -> "🥉 #3"
    position <= 10 -> "🔥 #$position"
    else -> "#$position"
}

/* ---------- Composants réutilisables ---------- */

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    val theme = Nova.theme
    val rainbow = Nova.rainbowBrush
    val style = MaterialTheme.typography.labelSmall
    Text(
        text = text.uppercase(),
        style = if (rainbow != null) style.copy(brush = rainbow) else style,
        color = when {
            rainbow != null -> Color.Unspecified
            theme.signature == com.novastats.app.ui.theme.Signature.DUAL_CONTRAST -> Nova.dualAccent.copy(alpha = 0.85f)
            else -> theme.textSecondary
        },
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
    // Survivor : mini-drapeau sous chaque titre de section (tourne : arc-en-ciel, trans, bi, gay…)
    if (Nova.isPride) {
        val idx = remember(text) { text.hashCode().let { if (it < 0) -it else it } % NovaColors.PrideFlags.size }
        MiniFlag(NovaColors.PrideFlags[idx], width = 36.dp, height = 3.dp, modifier = Modifier.padding(start = 16.dp).offset(y = (-6).dp))
    }
}

@Composable
fun NovaCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // Survivor : bordure tournante aux couleurs de tous les drapeaux + chevron Progress discret à gauche
    val prideMod = if (Nova.isPride) Modifier.prideBorder(Nova.cardShape, rememberPrideSweep()) else Modifier
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).then(prideMod),
        shape = Nova.cardShape,
        colors = CardDefaults.cardColors(containerColor = Nova.theme.surface)
    ) { content() }
}

@Composable
fun StatPill(value: String, label: String, modifier: Modifier = Modifier, accent: Color = Nova.theme.primary) {
    val pride = Nova.rainbowBrush
    Column(modifier = modifier.goldShimmer(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (pride != null) Text(value, style = MaterialTheme.typography.titleLarge.copy(brush = pride), fontWeight = FontWeight.Black)
        else Text(value, style = MaterialTheme.typography.titleLarge, color = accent, fontWeight = FontWeight.Black)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Nova.theme.textSecondary, textAlign = TextAlign.Center)
    }
}

/** Pochette (ou dégradé de secours avec initiale). */
@Composable
fun CoverArt(url: String?, fallbackText: String, size: Int = 48, circle: Boolean = false, zoomable: Boolean = false) {
    val shape = if (circle) RoundedCornerShape(50) else RoundedCornerShape(8.dp)
    val theme = Nova.theme
    var full by remember { mutableStateOf(false) }
    if (full && url != null) FullscreenImage(url, fallbackText) { full = false }
    Box(
        modifier = Modifier.size(size.dp).clip(shape)
            .background(Brush.linearGradient(listOf(theme.primary.copy(alpha = 0.6f), theme.glowSecondary.copy(alpha = 0.6f))))
            .then(if (zoomable && url != null) Modifier.clickable { full = true } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Text(fallbackText.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = (size / 2.2).sp)
        }
    }
}

/** Image plein écran (appui sur une pochette / photo dans un popup) : fond noir, pincer pour zoomer, appui pour fermer. */
@Composable
fun FullscreenImage(url: String, title: String, onDismiss: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val state = androidx.compose.foundation.gestures.rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale <= 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.96f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)) {
            AsyncImage(
                model = url, contentDescription = title, contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .transformable(state)
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
            )
            Text(title, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp, start = 24.dp, end = 24.dp))
            Text("✕", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.15f)).clickable(onClick = onDismiss).padding(horizontal = 12.dp, vertical = 4.dp))
        }
    }
}

/** Ligne de classement générique (position, pochette, titre, sous-titre, écoutes). Appui long → popup de détail. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RankRow(
    position: Int, title: String, subtitle: String?, plays: Int, durationMs: Long, coverUrl: String?, circle: Boolean = false,
    onClick: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null
) {
    val theme = Nova.theme
    val posColor = when (position) { 1 -> NovaColors.Gold; 2 -> NovaColors.Silver; 3 -> Color(0xFFCD7F32); else -> theme.textSecondary }
    val gesture = if (onClick != null || onLongClick != null) Modifier.combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick) else Modifier
    val pride = Nova.isPride
    val flag = prideFlagFor(position)
    Row(
        modifier = Modifier.fillMaxWidth().then(gesture)
            // Survivor : chaque ligne porte le drapeau de sa position (liseré gauche + lavis)
            .then(if (pride) Modifier.prideWash(flag, alpha = if (position <= 3) 0.14f else 0.07f).prideStripe(flag) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (pride) Text(
            positionLabel(position), fontWeight = FontWeight.Black,
            modifier = Modifier.width(56.dp), style = MaterialTheme.typography.bodyMedium.copy(brush = prideBrushFor(position))
        ) else Text(
            positionLabel(position), color = posColor, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(56.dp), style = MaterialTheme.typography.bodyMedium
        )
        CoverArt(coverUrl, title, circle = circle)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = theme.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            if (pride) Text("${formatCount(plays)} ▶", fontWeight = FontWeight.Black, style = LocalTextStyle.current.copy(brush = Brush.horizontalGradient(listOf(theme.primary, theme.secondary, theme.accent))))
            else Text("${formatCount(plays)} ▶", color = theme.primary, fontWeight = FontWeight.Bold)
            Text(formatDuration(durationMs), color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun EmptyState(emoji: String, title: String, message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(emoji, fontSize = 56.sp)
        Spacer(Modifier.height(12.dp))
        if (Nova.isPride) { PrideRibbon(Modifier.fillMaxWidth(0.6f).clip(RoundedCornerShape(3.dp)), height = 5.dp); Spacer(Modifier.height(10.dp)) }
        Text(title, style = MaterialTheme.typography.titleLarge, color = Nova.theme.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, color = Nova.theme.textSecondary, textAlign = TextAlign.Center)
    }
}

/** Écran temporaire pour les onglets pas encore implémentés. */
@Composable
fun PlaceholderScreen(tab: NovaTab, subtitle: String) {
    EmptyState(tab.emoji, tab.label, "$subtitle\n\nÀ venir — la base de données et les règles sont déjà prêtes.")
}

/* ===================== En-tête « maquette » : sélecteur de période + bandeau résumé ===================== */

/**
 * Sélecteur de période : 5 boutons de largeur égale, coins 14 dp, libellés FR (Jour · Semaine · Mois · Année · Global).
 * Le bouton actif est plein (primary du thème), les autres sur surface.
 */
@Composable
fun PeriodSegment(selected: Period, modifier: Modifier = Modifier, periods: List<Period> = Period.entries, onSelect: (Period) -> Unit) {
    val theme = Nova.theme
    val pride = Nova.isPride
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        periods.forEachIndexed { i, p ->
            val on = selected == p
            Box(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp))
                    // Survivor : chaque période a son drapeau ; la période active l'affiche en plein, les autres en liseré bas
                    .then(
                        when {
                            pride && on -> Modifier.background(Brush.verticalGradient(prideFlagFor(i + 1)))
                            pride -> Modifier.background(theme.surface).prideWash(prideFlagFor(i + 1), alpha = 0.12f)
                            on -> Modifier.background(theme.primary)
                            else -> Modifier.background(theme.surface)
                        }
                    )
                    .clickable { onSelect(p) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    p.frLabel, color = if (on) (if (pride) Color.Black else MaterialTheme.colorScheme.onPrimary) else theme.text,
                    style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Clip
                )
            }
        }
    }
}

/**
 * Puce de filtre de l'app : Material FilterChip partout, sauf en Survivor où la puce active affiche son
 * drapeau en plein et les autres le montrent en lavis ([flagKey] choisit le drapeau : passe l'élément filtré).
 */
@Composable
fun NovaFilterChip(
    selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, flagKey: Any? = null, colors: SelectableChipColors? = null, border: BorderStroke? = null
) {
    if (Nova.isPride) {
        val flag = prideFlagForKey(flagKey)
        val shape = RoundedCornerShape(10.dp)
        Box(
            modifier.height(32.dp).clip(shape).prideChip(selected, flag, Nova.theme.surface)
                .border(1.dp, if (selected) Color.White.copy(alpha = 0.7f) else flag.first().copy(alpha = 0.5f), shape)
                .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(
                LocalContentColor provides (if (selected) prideOnFlag(flag) else Nova.theme.text),
                LocalTextStyle provides MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.Black else FontWeight.Medium)
            ) { label() }
        }
    } else FilterChip(
        selected = selected, onClick = onClick, label = label, modifier = modifier, enabled = enabled,
        colors = colors ?: FilterChipDefaults.filterChipColors(), border = border ?: FilterChipDefaults.filterChipBorder(enabled = enabled, selected = selected)
    )
}

/** Cellule du bandeau résumé : valeur (primary par défaut) + libellé. */
data class StripCell(val value: String, val label: String, val color: Color? = null)

/** Bandeau résumé : carte arrondie 20 dp sur surface, cellules de largeur égale séparées par de fins traits verticaux. */
@Composable
fun SummaryStrip(cells: List<StripCell>, modifier: Modifier = Modifier, content: (@Composable () -> Unit)? = null) {
    val theme = Nova.theme
    val pride = Nova.isPride
    val prideMod = if (pride) Modifier.prideBorder(RoundedCornerShape(20.dp), rememberPrideSweep()) else Modifier
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(20.dp)).background(theme.surface).then(prideMod)) {
        if (pride) PrideRibbon(height = 4.dp)
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            cells.forEachIndexed { i, c ->
                if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 6.dp).background(if (pride) Brush.verticalGradient(prideFlagFor(i)) else Brush.verticalGradient(listOf(theme.textSecondary.copy(alpha = 0.35f), theme.textSecondary.copy(alpha = 0.35f)))))
                Column(Modifier.weight(1f).goldShimmer(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (pride && c.color == null) Text(c.value, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium.copy(brush = prideBrushFor(i + 1)), maxLines = 1, softWrap = false)
                    else Text(c.value, color = c.color ?: theme.primary, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
                    Text(c.label, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                }
            }
        }
        if (content != null) Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp).padding(top = 6.dp)) { content() }
        }
    }
}

/** Moyenne par jour au format FR : « 136,6 » (ou « 12 » si entière). */
fun formatAverage(avg: Float): String = String.format(Locale.FRANCE, if (avg == avg.toInt().toFloat()) "%.0f" else "%.1f", avg)

/* ---------- Listes « Top 25 + Voir plus » & libellés de période ---------- */

/** Taille initiale des classements Stats / Billboard, et pas du bouton « Voir plus ». */
const val TOP_INITIAL = 25
const val TOP_STEP = 20

/** Bouton « Voir plus » placé en fin de liste : déroule [TOP_STEP] lignes supplémentaires. */
@Composable
fun LoadMoreButton(remaining: Int, onClick: () -> Unit) {
    val theme = Nova.theme
    val step = minOf(TOP_STEP, remaining)
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(
            "Voir plus  ▾  (+$step · $remaining restant${if (remaining > 1) "s" else ""})",
            color = theme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clip(Nova.chipShape).background(theme.primary.copy(alpha = 0.12f)).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }
}

/** Libellé lisible d'une période Stats (Daily = aujourd'hui, Weekly = 7 jours glissants, …). */
fun periodCaption(period: Period, range: com.novastats.app.domain.DateRange): String {
    val f = java.time.format.DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE)
    return when (period) {
        Period.DAILY -> "Aujourd'hui · ${range.to.format(f)}"
        Period.WEEKLY -> "7 derniers jours · ${range.from.format(f)} → ${range.to.format(f)}"
        Period.MONTHLY -> "Mois en cours · ${range.from.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRANCE))}"
        Period.YEARLY -> "Année ${range.to.year}"
        Period.GLOBAL -> "Depuis le début"
    }
}
