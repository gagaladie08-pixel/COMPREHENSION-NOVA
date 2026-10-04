package com.novastats.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.novastats.app.NovaStatsApp
import com.novastats.app.data.repository.RewindData
import com.novastats.app.data.repository.RewindEngine
import com.novastats.app.data.repository.RewindSpec
import com.novastats.app.domain.CertLevel
import com.novastats.app.domain.PantheonStatus
import com.novastats.app.ui.share.ShareCards
import com.novastats.app.ui.theme.Nova
import com.novastats.app.ui.theme.NovaColors
import com.novastats.app.ui.theme.PrideVeil
import com.novastats.app.ui.theme.ThemeAmbient
import kotlinx.coroutines.launch
import java.time.format.TextStyle
import java.util.Locale

/**
 * ✨ Nova Rewind — récap d'un mois ou d'une année, façon keynote (fondu + glissé, sons discrets).
 * Chaque slide ne montre qu'une idée ; on avance en touchant la zone droite, on recule à gauche.
 */
@Composable
fun RewindScreen(startKey: String? = null, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as NovaStatsApp
    val theme = Nova.theme
    val scope = rememberCoroutineScope()
    val engine = remember { RewindEngine(app.database) }

    var specs by remember { mutableStateOf<List<RewindSpec>>(emptyList()) }
    var key by remember { mutableStateOf(startKey) }
    val data by produceState<RewindData?>(initialValue = null, key) {
        value = null
        val k = key
        if (k != null) value = runCatching { engine.build(RewindSpec.parse(k)) }.getOrNull()
    }
    var index by remember(key) { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { specs = runCatching { engine.available() }.getOrDefault(emptyList()) }

    val isPride = theme.id == "survivor"

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(theme.background, Color.Black)))) {
        // Ambiance du thème (animée) + voile Survivor, comme sur les onglets principaux
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = 0.5f }) { ThemeAmbient(theme, Modifier.fillMaxSize()) }
        if (isPride) PrideVeil(Modifier.fillMaxSize().graphicsLayer { alpha = 0.35f })

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            /* ---------- Barre haute : périodes + fermer ---------- */
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("✕", color = theme.textSecondary, fontSize = 20.sp, modifier = Modifier.clickable { onClose() }.padding(8.dp))
                Spacer(Modifier.width(8.dp))
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                    specs.take(18).forEach { s ->
                        val on = s.key == key
                        Box(
                            Modifier.padding(end = 8.dp).clip(CircleShape)
                                .background(if (on) theme.primary else theme.surface.copy(alpha = 0.7f))
                                .clickable { if (!on) { key = s.key; index = 0 } }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                s.label, color = if (on) Color.White else theme.textSecondary,
                                style = MaterialTheme.typography.labelLarge, maxLines = 1
                            )
                        }
                    }
                }
            }

            val d = data
            if (d == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (key == null && specs.isEmpty()) Text("Aucun Rewind disponible pour l'instant", color = theme.textSecondary)
                    else CircularProgressIndicator(color = theme.primary)
                }
            } else {
                val slides = remember(d) { slidesFor(d) }
                val slide = slides.getOrNull(index) ?: Slide.FINALE

                /* ---------- Slide ---------- */
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AnimatedContent(
                        targetState = slide,
                        transitionSpec = {
                            (fadeIn(tween(320, 60)) + slideInHorizontally(tween(320, 60)) { it / 6 }) togetherWith
                                (fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { -it / 6 })
                        },
                        label = "rewind"
                    ) { s ->
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            when (s) {
                                Slide.COVER -> CoverSlide(d)
                                Slide.NUMBERS -> NumbersSlide(d)
                                Slide.TOP_ARTIST -> TopArtistSlide(d)
                                Slide.TOP_TRACK -> TopTrackSlide(d)
                                Slide.TOP5 -> Top5Slide(d)
                                Slide.RHYTHM -> RhythmSlide(d)
                                Slide.DISCOVERY -> DiscoverySlide(d)
                                Slide.ACHIEVEMENTS -> AchievementsSlide(d)
                                Slide.FINALE -> FinaleSlide(d)
                            }
                        }
                    }

                    // Zones tactiles : gauche = reculer, droite = avancer
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(0.28f).fillMaxHeight().clickable(
                            interactionSource = remember { androidx.compose.runtime.MutableInteractionSource() },
                            indication = null
                        ) { if (index > 0) index-- })
                        Box(Modifier.weight(0.72f).fillMaxHeight().clickable(
                            interactionSource = remember { androidx.compose.runtime.MutableInteractionSource() },
                            indication = null
                        ) { if (index < slides.lastIndex) index++ else onClose() })
                    }
                }

                /* ---------- Barre basse : points + action ---------- */
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        slides.forEachIndexed { i, _ ->
                            val on = i == index
                            Box(
                                Modifier.padding(horizontal = 3.dp).height(4.dp)
                                    .width(if (on) 22.dp else 10.dp)
                                    .clip(CircleShape)
                                    .background(if (on) theme.primary else theme.textSecondary.copy(alpha = 0.28f))
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Précédent", color = theme.textSecondary, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clickable { if (index > 0) index-- }.padding(8.dp)
                        )
                        Spacer(Modifier.weight(1f))
                        if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = theme.primary, strokeWidth = 2.dp)
                        else if (slide == Slide.FINALE || index == slides.lastIndex) {
                            ActionPill("Partager la carte") {
                                busy = true
                                scope.launch {
                                    runCatching {
                                        val f = ShareCards.renderRewind(ctx, d, theme)
                                        ShareCards.share(ctx, f, "Partager mon Rewind")
                                    }
                                    busy = false
                                }
                            }
                        } else {
                            ActionPill("Suivant") { index++ }
                        }
                    }
                }
            }
        }
    }
}

private enum class Slide { COVER, NUMBERS, TOP_ARTIST, TOP_TRACK, TOP5, RHYTHM, DISCOVERY, ACHIEVEMENTS, FINALE }

private fun slidesFor(d: RewindData): List<Slide> = buildList {
    add(Slide.COVER)
    if (d.totals.plays > 0) add(Slide.NUMBERS)
    if (d.topArtist != null) add(Slide.TOP_ARTIST)
    if (d.topTrack != null) add(Slide.TOP_TRACK)
    if (d.topTracks.size > 1) add(Slide.TOP5)
    add(Slide.RHYTHM)
    if (d.newArtistCount > 0 || d.newTrackCount > 0) add(Slide.DISCOVERY)
    if (d.certifications.isNotEmpty() || d.pantheon.isNotEmpty()) add(Slide.ACHIEVEMENTS)
    add(Slide.FINALE)
}

/* ------------------------------ slides ------------------------------ */

@Composable
private fun CoverSlide(d: RewindData) {
    val theme = Nova.theme
    val t by rememberInfiniteTransition(label = "rw").animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse), label = "rwf")
    Spacer(Modifier.height(70.dp))
    RwEyebrow("NOVA REWIND")
    Spacer(Modifier.height(14.dp))
    Text(
        d.spec.label, color = theme.text, style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.Black, textAlign = TextAlign.Center, lineHeight = 46.sp
    )
    Spacer(Modifier.height(16.dp))
    Text(
        if (d.spec.kind == RewindSpec.Kind.MONTH) "ton mois, en musique" else "ton année, en musique",
        color = theme.textSecondary, style = MaterialTheme.typography.titleMedium
    )
    Spacer(Modifier.height(48.dp))
    Text(
        formatCount(d.totals.plays), color = theme.primary,
        style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black,
        modifier = Modifier.graphicsLayer { scaleX = 1f + t * 0.03f; scaleY = 1f + t * 0.03f }
    )
    Text("écoutes", color = theme.text, style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(24.dp))
    Text("touche l'écran pour avancer →", color = theme.textSecondary, style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(60.dp))
}

@Composable
private fun NumbersSlide(d: RewindData) {
    Spacer(Modifier.height(40.dp))
    RwTitle("Les chiffres")
    Spacer(Modifier.height(24.dp))
    RwBigStat(formatCount(d.totals.plays), "écoutes")
    Spacer(Modifier.height(10.dp))
    RwBigStat(formatDuration(d.totals.durationMs), "de musique")
    Spacer(Modifier.height(24.dp))
    RwRowStat("📅", "${d.totals.days} jour${if (d.totals.days > 1) "s" else ""} d'écoute")
    RwRowStat("🎤", "${formatCount(d.totals.artists)} artistes")
    RwRowStat("🎵", "${formatCount(d.totals.tracks)} titres")
    RwRowStat("💿", "${formatCount(d.totals.albums)} albums")
    d.playsDeltaPct?.let { p ->
        Spacer(Modifier.height(20.dp))
        Text(
            if (p >= 0) "▲ $p % de plus que la période précédente" else "▼ ${-p} % de moins que la période précédente",
            color = Nova.theme.accent, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center
        )
    }
    Spacer(Modifier.height(40.dp))
}

@Composable
private fun TopArtistSlide(d: RewindData) {
    val theme = Nova.theme
    val a = d.topArtist ?: return
    Spacer(Modifier.height(30.dp))
    RwEyebrow("ARTISTE N°1")
    Spacer(Modifier.height(18.dp))
    RwCover(a.artist.photoUrl ?: d.topTrack?.track?.coverUrl, a.artist.name, 210.dp, circle = true)
    Spacer(Modifier.height(22.dp))
    Text(a.artist.name, color = theme.text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(10.dp))
    Text("${formatCount(a.periodPlays)} écoutes · ${formatDuration(a.periodDurationMs)}", color = theme.primary, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Text("soit ${d.topArtistShare} % de ${if (d.spec.kind == RewindSpec.Kind.MONTH) "ton mois" else "ton année"}", color = theme.textSecondary, style = MaterialTheme.typography.bodyLarge)
    if (d.topArtistDays > 0) {
        Spacer(Modifier.height(6.dp))
        Text("présent ${d.topArtistDays} jour${if (d.topArtistDays > 1) "s" else ""} sur ${d.totals.days}", color = theme.textSecondary, style = MaterialTheme.typography.bodyMedium)
    }
    d.previousTopArtistName?.takeIf { it != a.artist.name }?.let {
        Spacer(Modifier.height(14.dp))
        Text("tu écoutais surtout $it avant", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(40.dp))
}

@Composable
private fun TopTrackSlide(d: RewindData) {
    val theme = Nova.theme
    val t = d.topTrack ?: return
    Spacer(Modifier.height(30.dp))
    RwEyebrow("TITRE N°1")
    Spacer(Modifier.height(18.dp))
    RwCover(t.track.coverUrl, t.track.title, 210.dp, circle = false)
    Spacer(Modifier.height(22.dp))
    Text(t.track.title, color = theme.text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
    Spacer(Modifier.height(6.dp))
    Text(t.artistName, color = theme.textSecondary, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(12.dp))
    Text("${formatCount(t.periodPlays)} écoutes", color = theme.primary, style = MaterialTheme.typography.titleLarge)
    if (d.topTrackDays > 0) {
        Spacer(Modifier.height(6.dp))
        Text("dans tes oreilles ${d.topTrackDays} jour${if (d.topTrackDays > 1) "s" else ""} différents", color = theme.textSecondary, style = MaterialTheme.typography.bodyMedium)
    }
    d.topAlbum?.let { al ->
        Spacer(Modifier.height(18.dp))
        RwEyebrow("ALBUM N°1")
        Spacer(Modifier.height(10.dp))
        Text(al.album.title, color = theme.text, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, maxLines = 2)
        Text(al.artistName, color = theme.textSecondary, style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(40.dp))
}

@Composable
private fun Top5Slide(d: RewindData) {
    val theme = Nova.theme
    Spacer(Modifier.height(30.dp))
    RwTitle("Ton top 5")
    Spacer(Modifier.height(20.dp))
    d.topTracks.take(5).forEachIndexed { i, t ->
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${i + 1}", color = theme.primary, fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.width(26.dp))
            if (t.track.coverUrl != null) {
                AsyncImage(
                    model = t.track.coverUrl, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(theme.surface)
                )
            } else {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(theme.surface))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.track.title, color = theme.text, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Text(t.artistName, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            Text(formatCount(t.periodPlays), color = theme.textSecondary, style = MaterialTheme.typography.labelLarge)
        }
    }
    Spacer(Modifier.height(30.dp))
}

@Composable
private fun RhythmSlide(d: RewindData) {
    val theme = Nova.theme
    Spacer(Modifier.height(30.dp))
    RwTitle("Ton rythme")
    Spacer(Modifier.height(20.dp))
    d.bestDay?.let {
        RwRowStat("🔥", "record le ${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.FULL, Locale.FRANCE)} : ${formatCount(d.bestDayPlays)} écoutes")
        d.bestDayTrack?.let { t -> Text("avec « ${t.track.title} »", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
    }
    if (d.longestStreak > 1) RwRowStat("📆", "${d.longestStreak} jours d'affilée au maximum")
    d.favouriteHour?.let { RwRowStat("🕒", "ton heure de prédilection : ${String.format(Locale.FRANCE, "%02d", it)}h") }
    if (d.nightShare >= 0.2f) RwRowStat("🌙", "${(d.nightShare * 100).toInt()} % de tes écoutes entre 22h et 5h")
    d.favouriteWeekday?.let {
        val name = java.time.DayOfWeek.of(it).getDisplayName(TextStyle.FULL, Locale.FRANCE)
        RwRowStat("🗓️", "ton jour fort : $name")
    }
    if (d.longestSessionMs > 0) RwRowStat("🎧", "plus longue session : ${formatDuration(d.longestSessionMs)}")
    Spacer(Modifier.height(30.dp))
}

@Composable
private fun DiscoverySlide(d: RewindData) {
    val theme = Nova.theme
    Spacer(Modifier.height(30.dp))
    RwTitle("Tes découvertes")
    Spacer(Modifier.height(10.dp))
    Text("${formatCount(d.newArtistCount)} nouveaux artistes · ${formatCount(d.newTrackCount)} nouveaux titres", color = theme.textSecondary, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    Spacer(Modifier.height(20.dp))
    d.newArtists.take(8).forEach { a ->
        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            if (a.photoUrl != null) {
                AsyncImage(
                    model = a.photoUrl, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(46.dp).clip(CircleShape).background(theme.surface)
                )
            } else {
                Box(Modifier.size(46.dp).clip(CircleShape).background(theme.surface), contentAlignment = Alignment.Center) {
                    Text(a.name.take(1).uppercase(), color = theme.text, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(a.name, color = theme.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
            Text("${formatCount(a.plays)} éc.", color = theme.textSecondary, style = MaterialTheme.typography.labelMedium)
        }
    }
    Spacer(Modifier.height(30.dp))
}

@Composable
private fun AchievementsSlide(d: RewindData) {
    val theme = Nova.theme
    Spacer(Modifier.height(30.dp))
    RwTitle("Tes récompenses")
    Spacer(Modifier.height(18.dp))
    d.certifications.take(6).forEach { c ->
        val level = CertLevel.entries.firstOrNull { it.dbName == c.level }
        RwRowStat(level?.emoji ?: "🏅", "${c.name ?: "Titre"} — ${level?.label ?: c.level}${if (c.multiplier > 1) " ×${c.multiplier}" else ""}")
    }
    d.pantheon.take(4).forEach { p ->
        val st = PantheonStatus.fromDb(p.status)
        RwRowStat(st?.emoji ?: "✨", "${p.name ?: "Artiste"} entre au Panthéon — ${st?.label ?: p.status}")
    }
    if (d.numberOnes.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        RwEyebrow("N°1 AU BILLBOARD")
        Spacer(Modifier.height(10.dp))
        d.numberOnes.take(2).forEach { n ->
            Text("« ${n.title} » · ${n.weeks} semaine${if (n.weeks > 1) "s" else ""} en tête", color = theme.text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }
    }
    Spacer(Modifier.height(30.dp))
}

@Composable
private fun FinaleSlide(d: RewindData) {
    val theme = Nova.theme
    val t by rememberInfiniteTransition(label = "rwf2").animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Reverse), label = "rwf2a")
    Spacer(Modifier.height(60.dp))
    RwEyebrow("MERCI POUR CETTE ÉCOUTE")
    Spacer(Modifier.height(16.dp))
    Text(
        "Voilà ${if (d.spec.kind == RewindSpec.Kind.MONTH) "ton mois" else "ton année"} ${d.spec.ofLabel}",
        color = theme.text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(28.dp))
    Text(
        "${formatCount(d.totals.plays)}", color = theme.primary, style = MaterialTheme.typography.displayLarge,
        fontWeight = FontWeight.Black, modifier = Modifier.graphicsLayer { scaleX = 1f + t * 0.04f; scaleY = 1f + t * 0.04f }
    )
    Text("écoutes · ${formatDuration(d.totals.durationMs)}", color = theme.text, style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(18.dp))
    Text("Partage ta carte pour montrer ton année.", color = theme.textSecondary, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    Spacer(Modifier.height(50.dp))
}

/* ------------------------------ briques ------------------------------ */

@Composable
private fun RwEyebrow(text: String) {
    val theme = Nova.theme
    Text(
        text, color = theme.accent, style = MaterialTheme.typography.labelMedium,
        letterSpacing = 3.sp, textAlign = TextAlign.Center
    )
}

@Composable
private fun RwTitle(text: String) {
    Text(text, color = Nova.theme.text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
}

@Composable
private fun RwBigStat(value: String, label: String) {
    val theme = Nova.theme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = theme.text, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
        Text(label, color = theme.textSecondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { 1f },
            modifier = Modifier.width(160.dp).height(4.dp).clip(CircleShape),
            color = theme.primary, trackColor = theme.primary.copy(alpha = 0.15f)
        )
    }
}

@Composable
private fun RwRowStat(emoji: String, text: String) {
    val theme = Nova.theme
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp)
            .clip(RoundedCornerShape(16.dp)).background(theme.surface.copy(alpha = 0.75f))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.width(14.dp))
        Text(text, color = theme.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RwCover(url: String?, fallback: String, size: androidx.compose.ui.unit.Dp, circle: Boolean) {
    val theme = Nova.theme
    val shape = if (circle) CircleShape else RoundedCornerShape(22.dp)
    val glow = if (theme.id == "survivor") NovaColors.PrideFlags.flatten().take(3) else listOf(theme.primary, theme.secondary)
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size + 16.dp).clip(shape)
                .background(Brush.linearGradient(glow.map { it.copy(alpha = 0.55f) }))
        )
        if (url != null) {
            AsyncImage(
                model = url, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(shape)
            )
        } else {
            Box(Modifier.size(size).clip(shape).background(Brush.linearGradient(listOf(theme.primary, theme.glowSecondary))), contentAlignment = Alignment.Center) {
                Text(fallback.take(1).uppercase(), color = Color.White, fontSize = (size.value / 3).sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun ActionPill(text: String, onClick: () -> Unit) {
    val theme = Nova.theme
    Box(
        Modifier.clip(CircleShape).background(theme.primary)
            .clickable { onClick() }
            .padding(horizontal = 26.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
}

/** Carte d'entrée affichée sur l'Accueil quand un Rewind est disponible. */
@Composable
fun RewindEntryCard(onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as NovaStatsApp
    val theme = Nova.theme
    val spec by produceState<RewindSpec?>(initialValue = null) {
        value = runCatching { RewindEngine(app.database).featured() }.getOrNull()
    }
    val s = spec ?: return
    NovaCard {
        Column(Modifier.fillMaxWidth().clickable { onOpen(s.key) }.padding(16.dp)) {
            RwEyebrow("NOVA REWIND")
            Spacer(Modifier.height(6.dp))
            Text("Ton mois ${s.ofLabel} en images", color = theme.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Écoutes, artistes, records, récompenses… et une carte à partager.", color = theme.textSecondary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Text("Revivre ${s.label} →", color = theme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}
