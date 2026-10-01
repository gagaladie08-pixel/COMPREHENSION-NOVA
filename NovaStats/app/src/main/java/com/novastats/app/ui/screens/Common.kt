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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.novastats.app.domain.Period
import com.novastats.app.ui.navigation.NovaTab
import com.novastats.app.ui.theme.Nova
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

fun formatCount(n: Int): String = String.format(Locale.FRANCE, "%,d", n).replace('\u00A0', ' ').replace('\u202F', ' ')

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
}

@Composable
fun NovaCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = Nova.cardShape,
        colors = CardDefaults.cardColors(containerColor = Nova.theme.surface)
    ) { content() }
}

@Composable
fun StatPill(value: String, label: String, modifier: Modifier = Modifier, accent: Color = Nova.theme.primary) {
    Column(modifier = modifier.goldShimmer(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = accent, fontWeight = FontWeight.Black)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Nova.theme.textSecondary, textAlign = TextAlign.Center)
    }
}

/** Pochette (ou dégradé de secours avec initiale). */
@Composable
fun CoverArt(url: String?, fallbackText: String, size: Int = 48, circle: Boolean = false) {
    val shape = if (circle) RoundedCornerShape(50) else RoundedCornerShape(8.dp)
    val theme = Nova.theme
    Box(
        modifier = Modifier.size(size.dp).clip(shape)
            .background(Brush.linearGradient(listOf(theme.primary.copy(alpha = 0.6f), theme.glowSecondary.copy(alpha = 0.6f)))),
        contentAlignment = Alignment.Center
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Text(fallbackText.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = (size / 2.2).sp)
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
    Row(
        modifier = Modifier.fillMaxWidth().then(gesture).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
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
            Text("${formatCount(plays)} ▶", color = theme.primary, fontWeight = FontWeight.Bold)
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
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        periods.forEach { p ->
            val on = selected == p
            Box(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (on) theme.primary else theme.surface)
                    .clickable { onSelect(p) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    p.frLabel, color = if (on) MaterialTheme.colorScheme.onPrimary else theme.text,
                    style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Clip
                )
            }
        }
    }
}

/** Cellule du bandeau résumé : valeur (primary par défaut) + libellé. */
data class StripCell(val value: String, val label: String, val color: Color? = null)

/** Bandeau résumé : carte arrondie 20 dp sur surface, cellules de largeur égale séparées par de fins traits verticaux. */
@Composable
fun SummaryStrip(cells: List<StripCell>, modifier: Modifier = Modifier, content: (@Composable () -> Unit)? = null) {
    val theme = Nova.theme
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(20.dp)).background(theme.surface).padding(vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            cells.forEachIndexed { i, c ->
                if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 6.dp).background(theme.textSecondary.copy(alpha = 0.35f)))
                Column(Modifier.weight(1f).goldShimmer(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(c.value, color = c.color ?: theme.primary, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
                    Text(c.label, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                }
            }
        }
        if (content != null) Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp).padding(top = 6.dp)) { content() }
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
