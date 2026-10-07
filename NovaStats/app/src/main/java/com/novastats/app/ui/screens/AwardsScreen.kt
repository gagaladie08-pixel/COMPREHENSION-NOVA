package com.novastats.app.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novastats.app.NovaStatsApp
import com.novastats.app.data.db.entity.EntityType
import com.novastats.app.data.db.entity.NovaAwardEntity
import com.novastats.app.domain.AwardCategory
import com.novastats.app.domain.AwardRules
import com.novastats.app.domain.Dates
import com.novastats.app.ui.theme.Nova
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import androidx.compose.runtime.produceState
import kotlinx.coroutines.flow.first
import java.util.Locale

private fun hex(h: String) = Color(android.graphics.Color.parseColor(h))
private val longFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)

private data class Winner(val name: String, val subtitle: String?, val imageUrl: String?, val circle: Boolean)

/**
 * 🏆 Nova Awards — 9 récompenses annuelles. Déblocage après 2 mois d'utilisation. Année en cours LIVE (recalculée à
 * chaque ouverture), années passées FINAL (archivées). Révélation une par une au premier accès, cérémonie le 31 décembre.
 */
/**
 * 🎉 Ton année — la cérémonie des Nova Awards, le Rewind et les Year-End Charts réunis.
 *
 * Ces trois expériences racontaient « ton année musicale » à trois endroits différents :
 * l'onglet Awards, une carte de l'Accueil, une sous-section du Billboard. Elles vivent
 * désormais côte à côte, en trois segments. Les autres onglets ne bougent pas ; le Rewind
 * reste aussi accessible depuis l'Accueil, qui sert de vitrine.
 */
@Composable
fun AwardsScreen() {
    var segment by rememberSaveable { mutableIntStateOf(0) }
    var rewindKey by remember { mutableStateOf<String?>(null) }
    var yearEndOpen by remember { mutableStateOf(false) }

    // Pleins écrans prioritaires, comme sur l'Accueil : ils recouvrent tout, retour = l'onglet.
    if (rewindKey != null) { RewindScreen(rewindKey) { rewindKey = null }; return }
    if (yearEndOpen) { BackHandler { yearEndOpen = false }; YearEndScreen { yearEndOpen = false }; return }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("🏆 Awards", "🎬 Rewind", "📊 Year-End").forEachIndexed { i, label ->
                NovaFilterChip(
                    flagKey = "tonannee$i", selected = segment == i, onClick = { segment = i },
                    label = { Text(label, fontWeight = FontWeight.SemiBold) }
                )
            }
        }
        Box(Modifier.weight(1f)) {
            when (segment) {
                0 -> AwardsCeremony()
                1 -> RewindSegment { rewindKey = it }
                else -> YearEndSegment { yearEndOpen = true }
            }
        }
    }
}

/** Segment 🎬 : le Rewind — une période en images, et sa carte à partager. */
@Composable
private fun RewindSegment(onOpen: (String) -> Unit) {
    val theme = Nova.theme
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        item {
            Text("🎬 Nova Rewind", style = MaterialTheme.typography.headlineSmall, color = theme.text, fontWeight = FontWeight.Bold)
            Text("Une période en images : écoutes, artistes, records, récompenses — et une carte à partager.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            RewindEntryCard(onOpen)
            Spacer(Modifier.height(10.dp))
            Text("Un Rewind apparaît dès qu'une période a assez d'écoutes pour raconter quelque chose.", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Segment 📊 : le bilan complet de chaque année civile (venu du Billboard). */
@Composable
private fun YearEndSegment(onOpen: () -> Unit) {
    val theme = Nova.theme
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        item {
            Text("📊 Year-End Charts", style = MaterialTheme.typography.headlineSmall, color = theme.text, fontWeight = FontWeight.Bold)
            Text("Le bilan complet de chaque année civile : classements titres, artistes et albums.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            NovaCard {
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen() }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🏆", fontSize = 20.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Ouvrir les Year-End Charts", color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text("Choisis une année, retrouve ses n°1", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("›", color = theme.primary, fontSize = 22.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 🏆 Cérémonie des Nova Awards — segment « Awards » de l'onglet Ton année. */
@Composable
private fun AwardsCeremony() {
    val theme = Nova.theme
    val ctx = LocalContext.current
    val app = ctx.applicationContext as NovaStatsApp
    val db = app.database
    val scope = rememberCoroutineScope()
    val today = Dates.today()

    val firstPlayMs by app.database.scrobbleDao().firstScrobbleAt().collectAsStateWithLifecycle(initialValue = null)
    val firstPlay = firstPlayMs?.let { Dates.toLocalDate(it) }
    val unlocked = AwardRules.isUnlocked(firstPlay, today)

    var year by rememberSaveable { mutableIntStateOf(today.year) }
    val awards by remember(year) { app.database.novaAwardDao().forYear(year) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val revealedYears by app.settings.awardsRevealedYears.collectAsStateWithLifecycle<Set<String>?>(initialValue = null)
    var detail by remember { mutableStateOf<DetailTarget?>(null) }
    var refreshing by remember { mutableStateOf(true) }

    LaunchedEffect(unlocked) { if (unlocked) { runCatching { app.awards.refreshAll(today) }; refreshing = false } }

    if (!unlocked) {
        LockedAwards(firstPlay?.let { AwardRules.unlockDate(it) }?.let { it to java.time.temporal.ChronoUnit.DAYS.between(today, it) }, firstPlay != null)
        return
    }

    val years = ((firstPlay?.year ?: today.year)..today.year).toList().reversed()
    val byCat = remember(awards) { awards.associateBy { it.category } }
    val isLive = year == today.year
    val ceremony = isLive && today.monthValue == 12 && today.dayOfMonth == 31

    // Révélation une par une au premier accès de l'année
    val alreadyRevealed = revealedYears?.contains(year.toString())
    var revealedCount by remember(year) { mutableIntStateOf(0) }
    LaunchedEffect(year, alreadyRevealed, awards.isEmpty()) {
        if (alreadyRevealed == null || awards.isEmpty()) return@LaunchedEffect
        if (alreadyRevealed) { revealedCount = AwardCategory.entries.size; return@LaunchedEffect }
        for (i in 1..AwardCategory.entries.size) { delay(if (i == 1) 400 else 800); revealedCount = i; com.novastats.app.ui.theme.ThemeEvents.unlocked() }
        app.settings.markAwardsRevealed(year)
    }

    // 🎨 Art du titre le plus écouté (all time) en fond d'écran
    val artUrl by produceState<String?>(initialValue = null) {
        value = runCatching { db.trackDao().topAllTime(1).first().firstOrNull()?.track?.coverUrl }.getOrNull()
    }

    ScreenBackdrop(artUrl) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🏆 Nova Awards", style = MaterialTheme.typography.headlineSmall, color = theme.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { shareAwards(ctx, year, awards, byCat, app, scope) }) { Text("📤 Partager", color = theme.primary) }
                }
                Text(if (isLive) "Année en cours — recalculé en temps réel · cérémonie le 31 décembre 🎊" else "Palmarès $year — figé le 31 décembre", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                years.forEach { y ->
                    val live = y == today.year
                    NovaFilterChip(
                        flagKey = y, selected = year == y, onClick = { year = y },
                        label = { Text("$y  ${if (live) "● LIVE" else "FINAL"}", fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = (if (live) Color(0xFFE74C3C) else Color(0xFFFFD700)).copy(alpha = 0.25f), selectedLabelColor = theme.text)
                    )
                }
            }
            if (ceremony) CeremonyBanner(year)
            Spacer(Modifier.height(8.dp))
        }
        if (awards.isEmpty()) item {
            EmptyState("🏆", if (refreshing) "Calcul des récompenses…" else "Pas encore de données pour $year", "Les 9 Nova Awards apparaîtront dès les premières écoutes de l'année.")
        }
        items(AwardCategory.entries.toList(), key = { it.dbName }) { cat ->
            val index = cat.ordinal
            val a = byCat[cat.dbName]
            AnimatedVisibility(visible = index < revealedCount, enter = fadeIn(tween(500)) + scaleIn(tween(500), initialScale = 0.85f)) {
                AwardCard(cat, a, year) { target -> detail = target }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    }

    DetailPopupHost(detail) { detail = null }
}

@Composable
private fun LockedAwards(unlock: Pair<java.time.LocalDate, Long>?, hasPlays: Boolean) {
    val theme = Nova.theme
    Column(Modifier.fillMaxSize().background(theme.background).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("🔒", fontSize = 64.sp)
        Text("Nova Awards verrouillés", color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Text(
            if (!hasPlays) "Les Nova Awards se débloquent après 2 mois d'utilisation. Commence à écouter de la musique !"
            else "Les Nova Awards se débloquent après 2 mois d'utilisation.\nOuverture le ${unlock!!.first.format(longFmt)} — dans ${unlock.second} jour${if (unlock.second > 1) "s" else ""} 🎁",
            color = theme.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp)
        )
        if (unlock != null) {
            val progress = (1f - unlock.second.toFloat() / AwardRules.UNLOCK_DAYS).coerceIn(0f, 1f)
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(8.dp).clip(RoundedCornerShape(4.dp)), color = Color(0xFFFFD700), trackColor = theme.surface)
        }
    }
}

@Composable
private fun CeremonyBanner(year: Int) {
    val pulse by rememberInfiniteTransition(label = "c").animateFloat(0.5f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "ca")
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFFFFD700).copy(alpha = pulse), Color(0xFFE74C3C).copy(alpha = pulse), Color(0xFF9B59B6).copy(alpha = pulse))))
            .padding(16.dp), contentAlignment = Alignment.Center
    ) {
        Text("🎊 CÉRÉMONIE DES NOVA AWARDS $year 🎊\nLes résultats sont définitifs ce soir à minuit !", color = Color.White, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AwardCard(cat: AwardCategory, a: NovaAwardEntity?, year: Int, onOpen: (DetailTarget) -> Unit) {
    val theme = Nova.theme
    val app = LocalContext.current.applicationContext as NovaStatsApp
    val color = hex(cat.colorHex)
    var winner by remember(a?.winnerId, a?.winnerType) { mutableStateOf<Winner?>(null) }
    LaunchedEffect(a?.winnerId, a?.winnerType) {
        val id = a?.winnerId ?: return@LaunchedEffect
        val db = app.database
        winner = when (a.winnerType) {
            EntityType.TRACK -> db.trackDao().getById(id)?.let { t -> Winner(t.title, db.artistDao().getById(t.artistId)?.name, t.coverUrl, false) }
            EntityType.ARTIST -> db.artistDao().getById(id)?.let { Winner(it.name, null, it.photoUrl, true) }
            EntityType.ALBUM -> db.albumDao().getById(id)?.let { al -> Winner(al.title, al.artistId?.let { db.artistDao().getById(it)?.name } ?: "Artistes variés", al.coverUrl, false) }
            else -> null
        }
    }
    val hero = cat.hero
    val coverSize = if (hero) 108 else 60
    val target: DetailTarget? = a?.winnerId?.let { id ->
        when (a.winnerType) { EntityType.TRACK -> DetailTarget.Track(id); EntityType.ARTIST -> DetailTarget.Artist(id); EntityType.ALBUM -> DetailTarget.Album(id); else -> null }
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(if (hero) 16.dp else 6.dp, RoundedCornerShape(16.dp), ambientColor = color, spotColor = color)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(color.copy(alpha = if (hero) 0.35f else 0.2f), theme.surface, theme.surface)))
            .border(if (hero) 2.dp else 1.dp, color.copy(alpha = if (hero) 0.9f else 0.5f), RoundedCornerShape(16.dp))
            .combinedClickable(onClick = { target?.let(onOpen) }, onLongClick = { target?.let(onOpen) })
            .padding(if (hero) 16.dp else 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${cat.emoji} ${cat.title.uppercase()}", color = color, fontWeight = FontWeight.Black, style = if (hero) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            Text("$year", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(if (hero) 12.dp else 8.dp))
        if (a == null) {
            Text("Pas encore attribué — ${cat.logic.lowercase()}", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val w = winner
                if (a.winnerId != null && a.winnerType != "SESSION") {
                    Box(Modifier.shadow(if (hero) 14.dp else 6.dp, RoundedCornerShape(if (w?.circle == true) 50 else 12), ambientColor = color, spotColor = color)) {
                        CoverArt(w?.imageUrl, w?.name ?: "?", size = coverSize, circle = w?.circle == true)
                    }
                    Spacer(Modifier.width(14.dp))
                } else {
                    Box(Modifier.width(coverSize.dp).height(coverSize.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                        Text(cat.emoji, fontSize = (coverSize / 2).sp)
                    }
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    val headline = when (cat) {
                        AwardCategory.LONGEST_STREAK -> "${a.value.toInt()} jours"
                        AwardCategory.LONGEST_SESSION -> formatDuration(a.value.toLong())
                        else -> w?.name ?: "…"
                    }
                    Text(headline, color = theme.text, fontWeight = FontWeight.Black, style = if (hero) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    w?.subtitle?.let { Text(it, color = theme.textSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    a.message?.let { Text(it, color = theme.text.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
                }
            }
        }
    }
}

private fun shareAwards(ctx: android.content.Context, year: Int, awards: List<NovaAwardEntity>, byCat: Map<String, NovaAwardEntity>, app: NovaStatsApp, scope: kotlinx.coroutines.CoroutineScope) {
    if (awards.isEmpty()) return
    scope.launch {
        val db = app.database
        val lines = AwardCategory.entries.mapNotNull { cat ->
            val a = byCat[cat.dbName] ?: return@mapNotNull null
            val name = when (a.winnerType) {
                EntityType.TRACK -> db.trackDao().getById(a.winnerId ?: 0)?.title
                EntityType.ARTIST -> db.artistDao().getById(a.winnerId ?: 0)?.name
                EntityType.ALBUM -> db.albumDao().getById(a.winnerId ?: 0)?.title
                else -> null
            }
            val value = when (cat) {
                AwardCategory.LONGEST_STREAK -> "${a.value.toInt()} jours"
                AwardCategory.LONGEST_SESSION -> formatDuration(a.value.toLong())
                else -> name ?: "—"
            }
            "${cat.emoji} ${cat.title} : $value"
        }
        val text = "🏆 Mes Nova Awards $year\n\n" + lines.joinToString("\n") + "\n\n#NovaAwards$year · NovaStats"
        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        runCatching { ctx.startActivity(Intent.createChooser(intent, "Partager mes Nova Awards").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
