package com.novastats.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novastats.app.NovaStatsApp
import com.novastats.app.data.db.dao.HallOfFameRow
import com.novastats.app.data.db.entity.CertificationEntity
import com.novastats.app.data.db.entity.EntityType
import com.novastats.app.domain.Dates
import com.novastats.app.domain.HallOfFameRules
import com.novastats.app.domain.Period
import com.novastats.app.ui.theme.Nova
import com.novastats.app.ui.theme.NovaColors
import java.time.format.DateTimeFormatter
import androidx.compose.runtime.produceState
import kotlinx.coroutines.flow.first
import java.util.Locale

/* Couleurs du cahier des charges */
private val DirectDebutColor = Color(0xFF7B2FBE)
private val LongRunColor = NovaColors.Gold
private val GlobalColor = Color(0xFF0D1BFF)

private val ceremonyFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)
private val shortFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRANCE)

/** Une carte = une entité avec tous ses badges pour le filtre courant. */
private data class HofCard(
    val entityId: Long, val entityType: String, val name: String, val subtitle: String?, val imageUrl: String?,
    val entries: List<HallOfFameRow>
) {
    val badges get() = entries.map { it.h.entryType }.distinct()
    val firstDate get() = entries.minOf { it.h.entryDate }
    val weeksAt1 get() = entries.maxOf { it.h.weeksAt1 }
    val playsAtEntry get() = entries.maxOf { it.h.playCountAtEntry }
    val reign: Pair<String, String>? get() = entries.mapNotNull { e -> e.h.reignStart?.let { it to (e.h.reignEnd ?: it) } }.minByOrNull { it.first }
    val prestige get() = badges.maxOf { badgePrestige(it) }
    /** Durée de règne (en jours) pour départager. */
    val reignDays get() = reign?.let { (a, b) -> Dates.parse(b).toEpochDay() - Dates.parse(a).toEpochDay() + 1 } ?: 0L
}

private fun badgePrestige(type: String) = when (type) {
    HallOfFameRules.LEGENDARY_RUN -> 4; HallOfFameRules.TRIPLE_DEBUT -> 3; HallOfFameRules.LONG_RUN -> 2; else -> 1
}
private fun badgeLabel(type: String) = when (type) {
    HallOfFameRules.DIRECT_DEBUT -> "🚀 DIRECT DEBUT"; HallOfFameRules.LONG_RUN -> "👑 LONG RUN"
    HallOfFameRules.TRIPLE_DEBUT -> "🌍 TRIPLE DEBUT"; HallOfFameRules.LEGENDARY_RUN -> "🏅 LEGENDARY RUN"; else -> type
}
private fun badgeColor(type: String) = when (type) {
    HallOfFameRules.DIRECT_DEBUT -> DirectDebutColor; HallOfFameRules.LONG_RUN -> LongRunColor; else -> GlobalColor
}

/**
 * 🏛️ Hall of Fame — filtre Weekly / Monthly / Global, onglets Chansons / Artistes / Albums.
 * Alimenté 100 % automatiquement par les snapshots Billboard (aucune entrée manuelle).
 */
@Composable
fun HallOfFameScreen() {
    val theme = Nova.theme
    val app = LocalContext.current.applicationContext as NovaStatsApp
    val db = app.database
    var period by rememberSaveable { mutableStateOf(Period.WEEKLY.dbName) }
    var category by rememberSaveable { mutableStateOf(EntityType.TRACK) }
    var detail by remember { mutableStateOf<DetailTarget?>(null) }

    val rows by remember(period, category) { app.database.hallOfFameDao().rows(period, category) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val certs by app.database.certificationDao().allCurrentFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val total by app.database.hallOfFameDao().countFlow().collectAsStateWithLifecycle(initialValue = 0)
    val certByEntity = remember(certs) { certs.associateBy { it.entityType to it.entityId } }

    val cards = remember(rows) {
        rows.groupBy { it.h.entityId }.map { (id, list) ->
            val f = list.first()
            HofCard(id, f.h.entityType, f.name ?: "Inconnu", f.subtitle, f.imageUrl, list)
        }.sortedWith(compareByDescending<HofCard> { it.prestige }.thenByDescending { it.reignDays }.thenByDescending { it.weeksAt1 }.thenByDescending { it.firstDate })
    }
    val isGlobal = period == Period.GLOBAL.dbName

    // 🎨 Art du titre le plus écouté (all time) en fond d'écran
    val artUrl by produceState<String?>(initialValue = null) {
        value = runCatching { db.trackDao().topAllTime(1).first().firstOrNull()?.track?.coverUrl }.getOrNull()
    }

    ScreenBackdrop(artUrl) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text("🏛️ Hall of Fame", style = MaterialTheme.typography.headlineSmall, color = theme.text, fontWeight = FontWeight.Bold)
                Text("$total intronisation${if (total > 1) "s" else ""} · alimenté par le Billboard", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Period.WEEKLY, Period.MONTHLY, Period.GLOBAL).forEach { p ->
                    NovaFilterChip(flagKey = p, selected = period == p.dbName, onClick = { period = p.dbName }, label = { Text(p.label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = theme.primary.copy(alpha = 0.25f), selectedLabelColor = theme.text))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(EntityType.TRACK to "🎵 Chansons", EntityType.ARTIST to "🎤 Artistes", EntityType.ALBUM to "💿 Albums").forEach { (t, l) ->
                    NovaFilterChip(flagKey = t, selected = category == t, onClick = { category = t }, label = { Text(l) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when (period) {
                    Period.WEEKLY.dbName -> "🚀 Direct Debut : entrée directe au #1 hebdo · 👑 Long Run : 3 semaines consécutives au #1"
                    Period.MONTHLY.dbName -> "🚀 Direct Debut : entrée directe au #1 mensuel · 👑 Long Run : 2 mois consécutifs au #1"
                    else -> "🌍 Triple Debut : #1 Daily + Weekly + Monthly le même jour · 🏅 Legendary Run : 10× #1 hebdo"
                },
                color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
        }
        if (cards.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🏛️", fontSize = 64.sp)
                Text("Le temple est encore vide", color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Les intronisations arrivent automatiquement : un #1 direct, un règne de 3 semaines, un triple #1 le même jour… Continue d'écouter !",
                    color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        items(cards, key = { it.entityId }) { card ->
            HofCardView(card, isGlobal, certByEntity[card.entityType to card.entityId]) {
                // 7. Popup Hall of Fame : historique complet d'entrée + courbe de TOUTES les positions Billboard
                detail = DetailTarget.HallOfFame(card.entityId, card.entityType)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    }

    DetailPopupHost(detail) { detail = null }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HofCardView(card: HofCard, global: Boolean, cert: CertificationEntity?, onOpen: () -> Unit) {
    val theme = Nova.theme
    val main = card.badges.maxBy { badgePrestige(it) }
    val color = badgeColor(main)
    // Glow pulsant discret (Direct Debut) / intense (Global)
    val pulse by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = if (global) 0.35f else 0.15f, targetValue = if (global) 0.7f else 0.3f,
        animationSpec = infiniteRepeatable(tween(if (global) 1400 else 2200), RepeatMode.Reverse), label = "glowAlpha"
    )
    val coverSize = if (global) 96 else 72
    // Global → carte 100 % de la largeur (pas de marge), glow intense + particules étoilées
    val cardShape = RoundedCornerShape(if (global) 0.dp else 16.dp)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = if (global) 0.dp else 16.dp, vertical = 6.dp)
            .shadow(if (global) 22.dp else 8.dp, cardShape, ambientColor = color, spotColor = color)
            .clip(cardShape)
            .background(Brush.linearGradient(listOf(color.copy(alpha = pulse * 0.6f), theme.surface, theme.surface)))
            .border(if (global) 2.dp else 1.dp, color.copy(alpha = pulse + 0.3f), cardShape)
            .then(if (global) Modifier.starParticles(pulse) else Modifier)
            .combinedClickable(onClick = onOpen, onLongClick = onOpen)
            .padding(14.dp)
    ) {
        if (global) {
            Text("✦ ✧ ✦   G L O B A L   ✦ ✧ ✦", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.shadow(12.dp, RoundedCornerShape(if (card.entityType == EntityType.ARTIST) 50 else 12), ambientColor = color, spotColor = color)) {
                CoverArt(card.imageUrl, card.name, size = coverSize, circle = card.entityType == EntityType.ARTIST)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(card.name, color = theme.text, fontWeight = FontWeight.Bold, style = if (global) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                card.subtitle?.let { Text(it, color = theme.textSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    card.badges.sortedByDescending { badgePrestige(it) }.forEach { b ->
                        Text(
                            badgeLabel(b), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(badgeColor(b).copy(alpha = 0.85f)).padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("🎊 Consacré le ${Dates.parse(card.firstDate).format(ceremonyFmt)}", color = theme.text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        val reign = card.reign
        val reignText = when {
            card.weeksAt1 > 0 && reign != null -> "${card.weeksAt1} semaine${if (card.weeksAt1 > 1) "s" else ""} au #1 · du ${Dates.parse(reign.first).format(shortFmt)} au ${Dates.parse(reign.second).format(shortFmt)}"
            reign != null && reign.first != reign.second -> "Règne du ${Dates.parse(reign.first).format(shortFmt)} au ${Dates.parse(reign.second).format(shortFmt)}"
            card.weeksAt1 > 0 -> "${card.weeksAt1} semaine${if (card.weeksAt1 > 1) "s" else ""} au #1"
            else -> null
        }
        reignText?.let { Text("👑 $it", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall) }
        Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("▶ ${formatCount(card.playsAtEntry)} écoutes à l'entrée", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
            certificationLabel(cert)?.let { Text(it, color = theme.primary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** Particules étoilées (carte Global, bleu cosmique) : petites étoiles qui scintillent au rythme du glow. */
private fun Modifier.starParticles(pulse: Float): Modifier = this.then(
    Modifier.drawWithContent {
        drawContent()
        val stars = listOf(0.05f to 0.2f, 0.12f to 0.7f, 0.22f to 0.35f, 0.33f to 0.85f, 0.41f to 0.15f, 0.55f to 0.6f, 0.63f to 0.25f, 0.72f to 0.9f, 0.81f to 0.4f, 0.9f to 0.75f, 0.96f to 0.12f, 0.48f to 0.95f)
        stars.forEachIndexed { i, (fx, fy) ->
            val a = ((pulse * 2f + i * 0.17f) % 1f)
            val alpha = if (a < 0.5f) a * 2f else (1f - a) * 2f
            val r = 1.2f + (i % 3) * 0.9f
            drawCircle(Color.White.copy(alpha = alpha * 0.9f), radius = r * density, center = androidx.compose.ui.geometry.Offset(size.width * fx, size.height * fy))
        }
    }
)
