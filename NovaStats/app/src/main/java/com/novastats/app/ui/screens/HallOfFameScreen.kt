package com.novastats.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.novastats.app.data.db.NovaDatabase
import com.novastats.app.data.db.dao.HallOfFameRow
import com.novastats.app.data.db.dao.RunnerUpRow
import com.novastats.app.data.db.entity.CertificationEntity
import com.novastats.app.data.db.entity.EntityType
import com.novastats.app.data.db.entity.HallOfFameEntity
import com.novastats.app.domain.BillboardDates
import com.novastats.app.domain.Chart
import com.novastats.app.domain.Dates
import com.novastats.app.domain.HallOfFameRules
import com.novastats.app.domain.Period
import com.novastats.app.ui.theme.Nova
import com.novastats.app.ui.theme.NovaColors
import java.time.format.DateTimeFormatter
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.produceState
import com.novastats.app.data.repository.HonorGuest
import com.novastats.app.data.repository.HonorGuests
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
private fun badgeRule(type: String) = when (type) {
    HallOfFameRules.DIRECT_DEBUT -> "Règle : entrer directement #1 d'un chart hebdo ou mensuel, pour la toute première fois."
    HallOfFameRules.LONG_RUN -> "Règle : ${HallOfFameRules.LONG_RUN_WEEKS} semaines consécutives #1 hebdo, ou ${HallOfFameRules.LONG_RUN_MONTHS} mois consécutifs #1 mensuel."
    HallOfFameRules.TRIPLE_DEBUT -> "Règle : #1 des charts Daily, Weekly et Monthly le même jour."
    HallOfFameRules.LEGENDARY_RUN -> "Règle : ${HallOfFameRules.LEGENDARY_WEEKS_AT_1} semaines #1 hebdo au total."
    else -> type
}

/**
 * 🏛️ Hall of Fame — filtre Weekly / Monthly / Global, onglets Chansons / Artistes / Albums.
 * Alimenté 100 % automatiquement par les snapshots Billboard (aucune entrée manuelle).
 * Premium : antichambre du temple, compteurs animés, fraîcheur NEW + confettis,
 * marge de domination (dauphin), badges tapables, parallaxe + habillage or.
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

    // 🏛️ Premium (2) : compteurs par badge + plus long règne, sur tout le temple
    val allHof by produceState(initialValue = emptyList<HallOfFameEntity>(), key1 = total) {
        value = runCatching { db.hallOfFameDao().all() }.getOrDefault(emptyList())
    }
    val counters = remember(allHof) {
        listOf(HallOfFameRules.DIRECT_DEBUT, HallOfFameRules.LONG_RUN, HallOfFameRules.TRIPLE_DEBUT, HallOfFameRules.LEGENDARY_RUN)
            .map { t -> t to allHof.count { it.entryType == t } }
    }
    val longest = remember(allHof) {
        allHof.filter { it.reignStart != null }.maxByOrNull { e ->
            Dates.parse(e.reignEnd ?: e.reignStart!!).toEpochDay() - Dates.parse(e.reignStart!!).toEpochDay()
        }
    }
    val longestName by produceState<String?>(initialValue = null, key1 = longest?.entityId) {
        value = longest?.let { l ->
            runCatching {
                when (l.entityType) {
                    EntityType.TRACK -> db.billboardDao().nameOfTrack(l.entityId)
                    EntityType.ARTIST -> db.billboardDao().nameOfArtist(l.entityId)
                    else -> db.billboardDao().nameOfAlbum(l.entityId)
                }
            }.getOrNull()
        }
    }
    val longestDays = longest?.let { l ->
        (Dates.parse(l.reignEnd ?: l.reignStart!!).toEpochDay() - Dates.parse(l.reignStart!!).toEpochDay() + 1)
    }
    val recentCount = remember(allHof) {
        val cut = Dates.today().toEpochDay() - 7
        allHof.count { Dates.parse(it.entryDate).toEpochDay() > cut }
    }

    // 🏛️ Premium (1) : antichambre du temple — dominations en cours à un pas d'une intronisation
    var watch by remember { mutableStateOf<List<String>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(total) {
        watch = runCatching { computeAntechamber(db) }.getOrDefault(emptyList())
    }

    // 🎨 Art du titre le plus écouté (all time) en fond d'écran + parallaxe légère (8)
    val artUrl by produceState<String?>(initialValue = null) {
        value = runCatching { db.trackDao().topAllTime(1).first().firstOrNull()?.track?.coverUrl }.getOrNull()
    }
    val listState = rememberLazyListState()
    val parallax by remember { derivedStateOf { listState.firstVisibleItemScrollOffset.toFloat() * 0.12f } }

    ScreenBackdrop(artUrl, parallaxY = parallax) {
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
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
            Spacer(Modifier.height(10.dp))
            // 🏛️ Premium (2) : compteurs animés par badge + plus long règne
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                counters.forEach { (t, n) -> CountChip(badgeLabel(t), n, badgeColor(t)) }
            }
            if (longest != null && longestName != null) {
                Text("👑 Plus long règne : $longestName (${longestDays} j)", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            // 🏛️ Premium (4) : cérémonie récente
            if (recentCount > 0) {
                Text("🎊 $recentCount intronisation${if (recentCount > 1) "s" else ""} ces 7 derniers jours — le temple vit", color = NovaColors.Gold, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
        // 🏛️ Premium (1) : antichambre du temple
        if (watch.isNotEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("🚪 Antichambre du temple", color = theme.text, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                watch.forEach { w ->
                    Text("• $w", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 1.dp))
                }
                Spacer(Modifier.height(8.dp))
            }
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

/** 🏛️ Premium (1) : dominations en cours à un pas d'une intronisation (calculé depuis les snapshots). */
private suspend fun computeAntechamber(db: NovaDatabase): List<String> {
    val dao = db.billboardDao()
    val today = Dates.today()
    val todayIso = today.format(Dates.ISO)
    val weekAnchor = BillboardDates.anchor(Period.WEEKLY, today)
    val monthAnchor = BillboardDates.anchor(Period.MONTHLY, today)
    val weekIso = weekAnchor.format(Dates.ISO)
    val monthIso = monthAnchor.format(Dates.ISO)
    val out = ArrayList<String>()
    for (chart in Chart.entries) {
        val t = chart.entityType
        suspend fun top1(from: String, to: String) = when (t) {
            EntityType.TRACK -> dao.rankTracks(from, to, 1).firstOrNull()
            EntityType.ARTIST -> dao.rankArtists(from, to, 1).firstOrNull()
            else -> dao.rankAlbums(from, to, 1).firstOrNull()
        }
        suspend fun name(id: Long) = when (t) {
            EntityType.TRACK -> dao.nameOfTrack(id) ?: "?"
            EntityType.ARTIST -> dao.nameOfArtist(id) ?: "?"
            else -> dao.nameOfAlbum(id) ?: "?"
        }
        suspend fun runAt1(period: Period, anchorIso: String, id: Long): Int {
            val hist = when (t) {
                EntityType.TRACK -> dao.priorTrackRows(period.dbName, anchorIso)
                EntityType.ARTIST -> dao.priorArtistRows(period.dbName, anchorIso)
                else -> dao.priorAlbumRows(period.dbName, anchorIso)
            }.filter { it.entityId == id }.sortedByDescending { it.date }
            var run = 1
            for (r in hist) { if (r.position == 1) run++ else break }
            return run
        }
        // 👑 #1 hebdo en cours : combien de semaines avant Long Run ?
        val wr = BillboardDates.range(Period.WEEKLY, weekAnchor, today)
        val wTop = top1(wr.fromIso, wr.toIso)
        if (wTop != null) {
            val run = runAt1(Period.WEEKLY, weekIso, wTop.entityId)
            if (run < HallOfFameRules.LONG_RUN_WEEKS) {
                out += "${name(wTop.entityId)} : ${run} sem. #1 d'affilée — encore ${HallOfFameRules.LONG_RUN_WEEKS - run} pour 👑 Long Run"
            }
        }
        // 👑 #1 mensuel en cours
        val mr = BillboardDates.range(Period.MONTHLY, monthAnchor, today)
        val mTop = top1(mr.fromIso, mr.toIso)
        if (mTop != null) {
            val run = runAt1(Period.MONTHLY, monthIso, mTop.entityId)
            if (run < HallOfFameRules.LONG_RUN_MONTHS) {
                out += "${name(mTop.entityId)} : #1 ce mois-ci — encore ${HallOfFameRules.LONG_RUN_MONTHS - run} mois pour 👑 Long Run"
            }
        }
        // 🌍 Triple #1 en cours (jour + semaine + mois)
        val dTop = top1(todayIso, todayIso)
        if (dTop != null) {
            val d = dTop.entityId
            when {
                wTop?.entityId == d && mTop?.entityId == d -> out += "${name(d)} : triple #1 en cours — 🌍 intronisation à la clôture du jour"
                wTop?.entityId == d -> out += "${name(d)} : #1 jour + semaine — ne manque que le mois pour 🌍"
                mTop?.entityId == d -> out += "${name(d)} : #1 jour + mois — ne manque que la semaine pour 🌍"
            }
        }
    }
    return out
}

/** 🏛️ Premium (2) : puce compteur animée (compte vers le haut à l'affichage). */
@Composable
private fun CountChip(label: String, target: Int, color: Color) {
    val v by animateFloatAsState(targetValue = target.toFloat(), animationSpec = tween(900), label = "hofCount")
    Text(
        "$label ${v.toInt()}", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.30f)).padding(horizontal = 7.dp, vertical = 3.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HofCardView(card: HofCard, global: Boolean, cert: CertificationEntity?, onOpen: () -> Unit) {
    val theme = Nova.theme
    val mainEntry = card.entries.maxBy { badgePrestige(it.h.entryType) }
    val main = mainEntry.h.entryType
    val color = badgeColor(main)
    // Glow pulsant discret (Direct Debut) / intense (Global)
    val pulse by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = if (global) 0.35f else 0.15f, targetValue = if (global) 0.7f else 0.3f,
        animationSpec = infiniteRepeatable(tween(if (global) 1400 else 2200), RepeatMode.Reverse), label = "glowAlpha"
    )
    // 🏛️ Premium (4) : pluie de confettis sur les intronisations de moins de 7 jours
    val isFresh = runCatching { Dates.parse(card.firstDate).toEpochDay() > Dates.today().toEpochDay() - 7 }.getOrDefault(false)
    val fall by rememberInfiniteTransition(label = "confetti").animateFloat(
        initialValue = 0f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Restart), label = "fall"
    )
    val coverSize = if (global) 96 else 72
    // 🎤 Invités décisifs : sans les écoutes de leur version, cette consécration n'existerait pas
    val hofDb = (LocalContext.current.applicationContext as NovaStatsApp).database
    val decisiveGuests by produceState(initialValue = emptyList<HonorGuest>(), key1 = card.entityId) {
        if (card.entityType != EntityType.TRACK) return@produceState
        value = runCatching {
            card.entries.flatMap { e ->
                when (e.h.periodType) {
                    Period.GLOBAL.dbName -> HonorGuests.decisiveOnDay(hofDb, card.entityId, e.h.entryDate)
                    Period.MONTHLY.dbName -> { val r = Dates.monthOf(Dates.parse(e.h.entryDate)); HonorGuests.decisiveInRange(hofDb, card.entityId, r.fromIso, r.toIso) }
                    else -> { val r = Dates.weekOf(Dates.parse(e.h.entryDate)); HonorGuests.decisiveInRange(hofDb, card.entityId, r.fromIso, r.toIso) }
                }
            }.distinctBy { it.artistId }
        }.getOrDefault(emptyList())
    }
    // 🏛️ Premium (5) : dauphin (#2) du snapshot de l'entrée principale — marge de domination
    val runner by produceState<RunnerUpRow?>(initialValue = null, key1 = "${card.entityId}|${mainEntry.h.entryType}|${mainEntry.h.entryDate}") {
        val snapPeriod = when (mainEntry.h.entryType) {
            HallOfFameRules.LEGENDARY_RUN -> Period.WEEKLY.dbName
            HallOfFameRules.TRIPLE_DEBUT -> Period.DAILY.dbName
            else -> mainEntry.h.periodType
        }
        value = runCatching {
            when (card.entityType) {
                EntityType.TRACK -> hofDb.billboardDao().runnerUpTrack(snapPeriod, mainEntry.h.entryDate)
                EntityType.ARTIST -> hofDb.billboardDao().runnerUpArtist(snapPeriod, mainEntry.h.entryDate)
                else -> hofDb.billboardDao().runnerUpAlbum(snapPeriod, mainEntry.h.entryDate)
            }
        }.getOrNull()
    }
    // 🏛️ Premium (6) : badge tapé → détail règle + chiffres
    var openBadge by remember { mutableStateOf<String?>(null) }
    val badgeEntry = card.entries.firstOrNull { it.h.entryType == openBadge }?.h
    // Global → carte 100 % de la largeur (pas de marge), glow intense + particules étoilées
    val cardShape = RoundedCornerShape(if (global) 0.dp else 16.dp)
    // 🏛️ Premium (8) : dégradé métallique or pour les cartes Long Run
    val bgBrush = if (main == HallOfFameRules.LONG_RUN) {
        Brush.linearGradient(listOf(Color(0xFFD4AF37).copy(alpha = 0.30f + pulse * 0.35f), Color(0xFF8A6D1A).copy(alpha = 0.22f), theme.surface))
    } else {
        Brush.linearGradient(listOf(color.copy(alpha = pulse * 0.6f), theme.surface, theme.surface))
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = if (global) 0.dp else 16.dp, vertical = 6.dp)
            .shadow(if (global) 22.dp else 8.dp, cardShape, ambientColor = color, spotColor = color)
            .clip(cardShape)
            .background(bgBrush)
            .border(if (global) 2.dp else 1.dp, color.copy(alpha = pulse + 0.3f), cardShape)
            .then(if (global) Modifier.starParticles(pulse) else Modifier)
            .then(if (isFresh) Modifier.confettiRain(fall) else Modifier)
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
                // 🏛️ Premium (8) : nom « gravé or » pour les cartes les plus prestigieuses
                Text(
                    card.name, color = if (card.prestige >= 4) NovaColors.Gold else theme.text, letterSpacing = if (card.prestige >= 4) 1.5.sp else 0.sp,
                    fontWeight = FontWeight.Black, style = if (global) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                card.subtitle?.let { Text(it, color = theme.textSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    card.badges.sortedByDescending { badgePrestige(it) }.forEach { b ->
                        Text(
                            badgeLabel(b), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(badgeColor(b).copy(alpha = 0.85f)).padding(horizontal = 6.dp, vertical = 3.dp)
                                .clickable { openBadge = if (openBadge == b) null else b }
                        )
                    }
                    // 🏛️ Premium (4) : badge NEW
                    if (isFresh) {
                        Text("✨ NEW", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(theme.accent.copy(alpha = 0.85f)).padding(horizontal = 6.dp, vertical = 3.dp))
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
        // 🏛️ Premium (5) : contexte de domination — dauphin battu et marge
        runner?.let { r ->
            val margin = card.playsAtEntry - r.playCount
            Text(
                if (margin > 0) "🥈 Dauphin battu : ${r.name} · ${formatCount(r.playCount)} écoutes, ${formatCount(margin)} de retard"
                else "🥈 Dauphin ce jour-là : ${r.name} · ${formatCount(r.playCount)} écoutes",
                color = theme.textSecondary, style = MaterialTheme.typography.labelSmall
            )
        }
        decisiveGuests.forEach { g ->
            Text("🎤 ${g.artistName} (via « ${g.viaTitle} ») — cette consécration n'existerait pas sans ses écoutes", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
        }
        // 🏛️ Premium (6) : détail du badge tapé (règle + chiffres exacts)
        badgeEntry?.let { h ->
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(theme.surface.copy(alpha = 0.9f)).border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(10.dp)).padding(10.dp)) {
                Text(badgeRule(h.entryType), color = theme.text, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(4.dp))
                Text("📅 ${Dates.parse(h.entryDate).format(ceremonyFmt)}", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                h.reignStart?.let { rs ->
                    Text("👑 règne du ${Dates.parse(rs).format(shortFmt)} au ${Dates.parse(h.reignEnd ?: rs).format(shortFmt)}", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                }
                if (h.weeksAt1 > 0) Text("🏅 ${h.weeksAt1} semaine${if (h.weeksAt1 > 1) "s" else ""} #1 au total", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                if (h.playCountAtEntry > 0) Text("▶ ${formatCount(h.playCountAtEntry)} écoutes à l'entrée", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                Text("(touche le badge pour refermer)", color = theme.textSecondary.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** 🏛️ Premium (4) : confettis colorés qui tombent sur les cartes fraîches. */
private fun Modifier.confettiRain(fall: Float) = this.then(
    Modifier.drawWithContent {
        drawContent()
        val cols = listOf(NovaColors.Gold, Color(0xFF7B2FBE), Color(0xFF0D1BFF), Color.White)
        for (i in 0 until 16) {
            val fx = (i * 0.061f + 0.02f) % 1f
            val prog = (fall + i * 0.07f) % 1f
            val alpha = if (prog < 0.15f) prog / 0.15f else if (prog > 0.8f) (1f - prog) / 0.8f else 1f
            drawCircle(cols[i % 4].copy(alpha = alpha * 0.75f), radius = (1.6f + (i % 3)) * density, center = androidx.compose.ui.geometry.Offset(size.width * fx, size.height * prog))
        }
    }
)

/** Particules étoilées (carte Global, bleu cosmique) : petites étoiles qui scintillent au rythme du glow. */
private fun Modifier.starParticles(pulse: Float) = this.then(
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
