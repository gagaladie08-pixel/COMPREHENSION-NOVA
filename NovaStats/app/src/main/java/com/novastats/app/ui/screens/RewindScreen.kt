package com.novastats.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.novastats.app.NovaStatsApp
import com.novastats.app.data.repository.RewindData
import com.novastats.app.data.repository.RewindEngine
import com.novastats.app.data.repository.RewindSpec
import com.novastats.app.domain.CertLevel
import com.novastats.app.domain.PantheonStatus
import com.novastats.app.ui.onboarding.ObStage
import com.novastats.app.ui.onboarding.rememberObAudio
import com.novastats.app.ui.share.ShareCards
import com.novastats.app.ui.theme.Nova
import com.novastats.app.ui.theme.NovaColors
import com.novastats.app.ui.theme.NovaTheme
import com.novastats.app.ui.theme.PrideVeil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/**
 * ✨ Nova Rewind — récap d'un mois ou d'une année, façon keynote :
 * scène animée (halo qui dérive, particules), entrée en cascade de chaque élément, compteurs qui défilent,
 * défilement au doigt, lecture automatique type story, petit son et retour haptique à chaque glissade.
 */
@Composable
fun RewindScreen(startKey: String? = null, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as NovaStatsApp
    val theme = Nova.theme
    val scope = rememberCoroutineScope()
    val engine = remember { RewindEngine(app.database) }
    val audio = rememberObAudio()
    val haptics = LocalHapticFeedback.current
    val hapticsOn by app.settings.haptics.collectAsStateWithLifecycle(initialValue = true)

    var specs by remember { mutableStateOf<List<RewindSpec>>(emptyList()) }
    var key by remember { mutableStateOf(startKey) }
    val data by produceState<RewindData?>(initialValue = null, key) {
        value = null
        val k = key
        if (k != null) value = runCatching { engine.build(RewindSpec.parse(k)) }.getOrNull()
    }
    var index by remember(key) { mutableStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var forward by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) { specs = runCatching { engine.available() }.getOrDefault(emptyList()) }

    ObStage(base = theme.background, accent = theme.primary, ambient = theme, ambientAlpha = 0.6f) {
        RewindParticles(theme, Modifier.fillMaxSize())
        if (theme.id == "survivor") PrideVeil(Modifier.fillMaxSize().graphicsLayer { alpha = 0.35f })

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            /* ---------- Barre haute : périodes + fermer ---------- */
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("✕", color = theme.textSecondary, fontSize = 20.sp, modifier = Modifier.clickable { onClose() }.padding(8.dp))
                Spacer(Modifier.width(6.dp))
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                    specs.take(18).forEach { s ->
                        val on = s.key == key
                        val sc by animateFloatAsState(if (on) 1.06f else 1f, tween(260), label = "chip")
                        Column(
                            Modifier.padding(end = 8.dp).graphicsLayer { scaleX = sc; scaleY = sc }
                                .clip(CircleShape)
                                .background(if (on) Brush.horizontalGradient(listOf(theme.primary, theme.secondary)) else Brush.horizontalGradient(listOf(theme.surface.copy(alpha = 0.75f), theme.surface.copy(alpha = 0.75f))))
                                .clickable { if (!on) { key = s.key; index = 0; playing = true } }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                s.label, color = if (on) Color.White else theme.textSecondary,
                                style = MaterialTheme.typography.labelLarge, maxLines = 1
                            )
                            if (on) Box(Modifier.padding(top = 3.dp).width(18.dp).height(2.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f)))
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

                /* ---------- Progression « story » + lecture auto ---------- */
                val progress = remember { Animatable(0f) }
                LaunchedEffect(index, playing, slides.size) {
                    progress.snapTo(0f)
                    if (!playing) return@LaunchedEffect
                    progress.animateTo(1f, tween(SLIDE_MS, easing = LinearEasing))
                    if (index < slides.lastIndex) { forward = true; index++ } else playing = false
                }
                LaunchedEffect(index) {
                    if (index > 0) {
                        if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        audio.tick(0.1f)
                    }
                }

                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    slides.forEachIndexed { i, _ ->
                        val f = when { i < index -> 1f; i == index -> progress.value; else -> 0f }
                        Box(
                            Modifier.weight(1f).height(3.dp).clip(CircleShape).background(theme.textSecondary.copy(alpha = 0.25f))
                        ) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(f).clip(CircleShape).background(Brush.horizontalGradient(listOf(theme.primary, theme.secondary))))
                        }
                    }
                }

                /* ---------- Slide (défilement au doigt) ---------- */
                val drag = remember { Animatable(0f) }
                Box(
                    Modifier.weight(1f).fillMaxWidth()
                        .pointerInput(index, slides.size) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    val v = drag.value
                                    scope.launch {
                                        when {
                                            v < -90f && index < slides.lastIndex -> { forward = true; index++ }
                                            v > 90f && index > 0 -> { forward = false; index-- }
                                        }
                                        drag.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                                    }
                                },
                                onDragCancel = { scope.launch { drag.animateTo(0f, spring()) } },
                                onHorizontalDrag = { _, amount -> scope.launch { drag.snapTo((drag.value + amount).coerceIn(-420f, 420f)) } }
                            )
                        }
                        .graphicsLayer {
                            translationX = drag.value * 0.55f
                            alpha = 1f - (kotlin.math.abs(drag.value) / 900f).coerceIn(0f, 0.45f)
                        }
                ) {
                    AnimatedContent(
                        targetState = slide,
                        transitionSpec = {
                            val dir = if (forward) 1 else -1
                            (fadeIn(tween(300, 90)) + slideInHorizontally(tween(360, 90, easing = FastOutSlowInEasing)) { dir * it / 4 }) togetherWith
                                (fadeOut(tween(200)) + slideOutHorizontally(tween(260)) { -dir * it / 4 })
                        },
                        label = "rewind"
                    ) { s ->
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp, vertical = 6.dp),
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
                    // Zones tactiles : gauche = reculer, droite = avancer (le glisser est géré par le parent)
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(0.28f).fillMaxHeight().clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null
                        ) { if (index > 0) { forward = false; index-- } })
                        Box(Modifier.weight(0.72f).fillMaxHeight().clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null
                        ) { if (index < slides.lastIndex) { forward = true; index++ } else onClose() })
                    }
                }

                /* ---------- Barre basse ---------- */
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Précédent", color = theme.textSecondary, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clickable { if (index > 0) { forward = false; index-- } }.padding(8.dp)
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (playing) "⏸" else "▶", color = theme.textSecondary, fontSize = 18.sp,
                            modifier = Modifier.clickable { playing = !playing }.padding(8.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = theme.primary, strokeWidth = 2.dp)
                        else if (index == slides.lastIndex) {
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
                            ActionPill("Suivant") { forward = true; index++ }
                        }
                    }
                }
            }
        }
    }
}

/** Durée d'affichage d'une slide en lecture automatique. */
private const val SLIDE_MS = 6500

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

/* ============================== animations ============================== */

/** Particules qui montent lentement — couleurs du thème (ou des drapeaux pour Survivor). */
@Composable
private fun RewindParticles(theme: NovaTheme, modifier: Modifier = Modifier) {
    val palette = if (theme.id == "survivor") NovaColors.PrideConfetti else listOf(theme.primary, theme.secondary, theme.accent, theme.glowSecondary)
    val seeds = remember(palette) {
        List(46) { i ->
            floatArrayOf(
                (i * 37 % 100) / 100f,
                (i * 61 % 100) / 100f,
                0.35f + (i * 13 % 50) / 100f,     // vitesse
                0.5f + (i * 7 % 60) / 100f,       // taille relative
                (i % palette.size).toFloat()
            )
        }
    }
    val t by rememberInfiniteTransition(label = "rwp").animateFloat(0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart), label = "rwp2")
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width; val h = size.height
        seeds.forEach { s ->
            val speed = s[2]
            val y = h - ((t * speed + s[1]) % 1f) * h
            val x = (s[0] * w + sin((t * 6.28f + s[1] * 6.28f)) * 26f)
            val r = 1.6f + s[3] * 3.4f
            val a = 0.06f + s[3] * 0.16f
            drawCircle(color = palette[(s[4].toInt() % palette.size)].copy(alpha = a), radius = r, center = Offset(x, y))
        }
    }
}

/**
 * Entrée en cascade : l'élément monte, se dévoile et grandit légèrement.
 * [distance] en dp, [delay] en millisecondes.
 */
@Composable
private fun Appear(delay: Int = 0, distance: Dp = 26.dp, from: Float = 0.94f, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(delay.toLong()); shown = true }
    val a by animateFloatAsState(if (shown) 1f else 0f, tween(620, easing = FastOutSlowInEasing), label = "appear")
    Box(
        Modifier.graphicsLayer {
            alpha = a
            translationY = (1f - a) * distance.toPx()
            scaleX = from + (1f - from) * a
            scaleY = from + (1f - from) * a
        }
    ) { content() }
}

/** Compteur qui défile de 0 jusqu'à [value]. */
@Composable
private fun CountUp(
    value: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    durationMs: Int = 1200,
    suffix: String = ""
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(value) {
        anim.snapTo(0f)
        anim.animateTo(value.toFloat(), tween(durationMs, easing = EaseOutCubic))
    }
    Text(formatCount(anim.value.toInt()) + suffix, style = style, color = color, modifier = modifier)
}

/** Anneau lumineux qui tourne autour d'une pochette (dégradé balayage). */
@Composable
private fun GlowRing(url: String?, fallback: String, size: Dp, circle: Boolean, modifier: Modifier = Modifier) {
    val theme = Nova.theme
    val palette = if (theme.id == "survivor") NovaColors.PrideFlags.flatten().take(8) else listOf(theme.primary, theme.secondary, theme.accent, theme.primary)
    val t by rememberInfiniteTransition(label = "ring").animateFloat(0f, 360f, infiniteRepeatable(tween(11000, easing = LinearEasing), RepeatMode.Restart), label = "ringa")
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(0.92f, 1.06f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulsea")
    val shape = if (circle) CircleShape else RoundedCornerShape(28.dp)
    val inner = if (circle) CircleShape else RoundedCornerShape(22.dp)
    Box(modifier, contentAlignment = Alignment.Center) {
        // halo diffus
        Box(
            Modifier.size(size * 1.28f).graphicsLayer { scaleX = pulse; scaleY = pulse; alpha = 0.5f }
                .background(Brush.radialGradient(listOf(theme.primary.copy(alpha = 0.5f), Color.Transparent)), shape)
        )
        // anneau qui tourne
        Box(
            Modifier.size(size + 20.dp).graphicsLayer { rotationZ = t }
                .background(Brush.sweepGradient(palette + palette.first()), shape)
        )
        Box(Modifier.size(size + 9.dp).background(theme.background.copy(alpha = 0.92f), shape))
        if (url != null) {
            AsyncImage(
                model = url, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(inner)
            )
        } else {
            Box(Modifier.size(size).clip(inner).background(Brush.linearGradient(listOf(theme.primary, theme.glowSecondary))), contentAlignment = Alignment.Center) {
                Text(fallback.take(1).uppercase(), color = Color.White, fontSize = (size.value / 3).sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

/* ------------------------------ slides ------------------------------ */

@Composable
private fun CoverSlide(d: RewindData) {
    val theme = Nova.theme
    val float by rememberInfiniteTransition(label = "cv").animateFloat(-10f, 10f, infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "cvf")
    Spacer(Modifier.height(56.dp))
    Appear(delay = 60) { RwEyebrow("NOVA REWIND") }
    Spacer(Modifier.height(12.dp))
    Appear(delay = 220) {
        Text(
            d.spec.label, color = theme.text, style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Black, textAlign = TextAlign.Center, lineHeight = 46.sp
        )
    }
    Spacer(Modifier.height(10.dp))
    Appear(delay = 380) {
        Text(
            if (d.spec.kind == RewindSpec.Kind.MONTH) "ton mois, en musique" else "ton année, en musique",
            color = theme.textSecondary, style = MaterialTheme.typography.titleMedium
        )
    }
    Spacer(Modifier.height(46.dp))
    Appear(delay = 560, distance = 40.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { translationY = float }) {
            CountUp(d.totals.plays, MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Black), theme.primary, durationMs = 1600)
            Text("écoutes", color = theme.text, style = MaterialTheme.typography.titleLarge)
        }
    }
    Spacer(Modifier.height(26.dp))
    Appear(delay = 1000) {
        Text("glisse pour avancer →", color = theme.textSecondary, style = MaterialTheme.typography.labelLarge)
    }
    Spacer(Modifier.height(50.dp))
}

@Composable
private fun NumbersSlide(d: RewindData) {
    val theme = Nova.theme
    Spacer(Modifier.height(34.dp))
    Appear { RwTitle("Les chiffres") }
    Spacer(Modifier.height(22.dp))
    Appear(delay = 160) { RwBigStat(d.totals.plays.toLong(), "écoutes", theme) }
    Spacer(Modifier.height(12.dp))
    Appear(delay = 300) { RwBigStat(d.totals.durationMs, "de musique", theme, duration = true) }
    Spacer(Modifier.height(22.dp))
    val rows = listOf(
        "📅" to "${d.totals.days} jour${if (d.totals.days > 1) "s" else ""} d'écoute",
        "🎤" to "${formatCount(d.totals.artists)} artistes",
        "🎵" to "${formatCount(d.totals.tracks)} titres",
        "💿" to "${formatCount(d.totals.albums)} albums"
    )
    rows.forEachIndexed { i, (e, t) ->
        Appear(delay = 420 + i * 110) { RwRowStat(e, t) }
    }
    d.playsDeltaPct?.let { p ->
        Appear(delay = 900) {
            Text(
                if (p >= 0) "▲ $p % de plus que la période précédente" else "▼ ${-p} % de moins que la période précédente",
                color = theme.accent, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
    Spacer(Modifier.height(34.dp))
}

@Composable
private fun TopArtistSlide(d: RewindData) {
    val theme = Nova.theme
    val a = d.topArtist ?: return
    Spacer(Modifier.height(24.dp))
    Appear { RwEyebrow("ARTISTE N°1") }
    Spacer(Modifier.height(16.dp))
    Appear(delay = 180, distance = 34.dp) {
        GlowRing(a.artist.photoUrl ?: d.topTrack?.track?.coverUrl, a.artist.name, 200.dp, circle = true)
    }
    Spacer(Modifier.height(22.dp))
    Appear(delay = 420) {
        Text(a.artist.name, color = theme.text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(10.dp))
    Appear(delay = 560) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CountUp(a.periodPlays, MaterialTheme.typography.titleMedium, theme.primary, suffix = " écoutes")
            Text("soit ${d.topArtistShare} % de ${if (d.spec.kind == RewindSpec.Kind.MONTH) "ton mois" else "ton année"}", color = theme.textSecondary, style = MaterialTheme.typography.bodyLarge)
            if (d.topArtistDays > 0) Text("présent ${d.topArtistDays} jour${if (d.topArtistDays > 1) "s" else ""} sur ${d.totals.days}", color = theme.textSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
    d.previousTopArtistName?.takeIf { it != a.artist.name }?.let {
        Appear(delay = 900) { Text("tu écoutais surtout $it avant", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp)) }
    }
    Spacer(Modifier.height(34.dp))
}

@Composable
private fun TopTrackSlide(d: RewindData) {
    val theme = Nova.theme
    val t = d.topTrack ?: return
    Spacer(Modifier.height(24.dp))
    Appear { RwEyebrow("TITRE N°1") }
    Spacer(Modifier.height(16.dp))
    Appear(delay = 180, distance = 34.dp) { GlowRing(t.track.coverUrl, t.track.title, 200.dp, circle = false) }
    Spacer(Modifier.height(22.dp))
    Appear(delay = 420) {
        Text(t.track.title, color = theme.text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
    }
    Spacer(Modifier.height(6.dp))
    Appear(delay = 520) { Text(t.artistName, color = theme.textSecondary, style = MaterialTheme.typography.titleMedium) }
    Spacer(Modifier.height(12.dp))
    Appear(delay = 640) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CountUp(t.periodPlays, MaterialTheme.typography.titleLarge, theme.primary, suffix = " écoutes")
            if (d.topTrackDays > 0) Text("dans tes oreilles ${d.topTrackDays} jour${if (d.topTrackDays > 1) "s" else ""} différents", color = theme.textSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
    d.topAlbum?.let { al ->
        Appear(delay = 880) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 16.dp)) {
                RwEyebrow("ALBUM N°1")
                Spacer(Modifier.height(6.dp))
                Text(al.album.title, color = theme.text, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, maxLines = 2)
                Text(al.artistName, color = theme.textSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    Spacer(Modifier.height(34.dp))
}

@Composable
private fun Top5Slide(d: RewindData) {
    val theme = Nova.theme
    Spacer(Modifier.height(30.dp))
    Appear { RwTitle("Ton top 5") }
    Spacer(Modifier.height(18.dp))
    val max = d.topTracks.take(5).maxOf { it.periodPlays }.coerceAtLeast(1)
    d.topTracks.take(5).forEachIndexed { i, t ->
        Appear(delay = 200 + i * 140) {
            val w = remember { Animatable(0f) }
            LaunchedEffect(Unit) { w.animateTo(t.periodPlays.toFloat() / max, tween(900, delayMillis = 200 + i * 120, easing = FastOutSlowInEasing)) }
            Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", color = theme.primary, fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.width(26.dp))
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(theme.surface)) {
                        if (t.track.coverUrl != null) AsyncImage(model = t.track.coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(t.track.title.take(1).uppercase(), color = theme.textSecondary, fontWeight = FontWeight.Bold) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.track.title, color = theme.text, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Text(t.artistName, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    Text(formatCount(t.periodPlays), color = theme.textSecondary, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(theme.textSecondary.copy(alpha = 0.14f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(w.value.coerceIn(0f, 1f)).clip(CircleShape).background(Brush.horizontalGradient(listOf(theme.primary, theme.secondary))))
                }
            }
        }
    }
    Spacer(Modifier.height(28.dp))
}

@Composable
private fun RhythmSlide(d: RewindData) {
    Spacer(Modifier.height(30.dp))
    Appear { RwTitle("Ton rythme") }
    Spacer(Modifier.height(18.dp))
    val items = buildList {
        d.bestDay?.let { add("🔥" to "record le ${it.dayOfMonth} ${it.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.FRANCE)} : ${formatCount(d.bestDayPlays)} écoutes") }
        if (d.longestStreak > 1) add("📆" to "${d.longestStreak} jours d'affilée au maximum")
        d.favouriteHour?.let { add("🕒" to "ton heure de prédilection : ${String.format(Locale.FRANCE, "%02d", it)}h") }
        if (d.nightShare >= 0.2f) add("🌙" to "${(d.nightShare * 100).toInt()} % de tes écoutes entre 22h et 5h")
        d.favouriteWeekday?.let { add("🗓️" to "ton jour fort : ${java.time.DayOfWeek.of(it).getDisplayName(java.time.format.TextStyle.FULL, Locale.FRANCE)}") }
        if (d.longestSessionMs > 0) add("🎧" to "plus longue session : ${formatDuration(d.longestSessionMs)}")
    }
    items.forEachIndexed { i, (e, t) -> Appear(delay = 180 + i * 130) { RwRowStat(e, t) } }
    Spacer(Modifier.height(28.dp))
}

@Composable
private fun DiscoverySlide(d: RewindData) {
    val theme = Nova.theme
    Spacer(Modifier.height(30.dp))
    Appear { RwTitle("Tes découvertes") }
    Spacer(Modifier.height(8.dp))
    Appear(delay = 160) {
        Text("${formatCount(d.newArtistCount)} nouveaux artistes · ${formatCount(d.newTrackCount)} nouveaux titres", color = theme.textSecondary, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(16.dp))
    d.newArtists.take(8).forEachIndexed { i, a ->
        Appear(delay = 320 + i * 110) {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    if (a.photoUrl != null) AsyncImage(model = a.photoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(46.dp).clip(CircleShape).background(theme.surface))
                    else Box(Modifier.size(46.dp).clip(CircleShape).background(theme.surface), contentAlignment = Alignment.Center) { Text(a.name.take(1).uppercase(), color = theme.text, fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.width(12.dp))
                Text(a.name, color = theme.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
                Text("${formatCount(a.plays)} éc.", color = theme.textSecondary, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    Spacer(Modifier.height(28.dp))
}

@Composable
private fun AchievementsSlide(d: RewindData) {
    val theme = Nova.theme
    Spacer(Modifier.height(30.dp))
    Appear { RwTitle("Tes récompenses") }
    Spacer(Modifier.height(16.dp))
    val lines = buildList {
        d.certifications.take(6).forEach { c ->
            val level = CertLevel.entries.firstOrNull { it.dbName == c.level }
            add((level?.emoji ?: "🏅") to "${c.name ?: "Titre"} — ${level?.label ?: c.level}${if (c.multiplier > 1) " ×${c.multiplier}" else ""}")
        }
        d.pantheon.take(4).forEach { p ->
            val st = PantheonStatus.fromDb(p.status)
            add((st?.emoji ?: "✨") to "${p.name ?: "Artiste"} entre au Panthéon — ${st?.label ?: p.status}")
        }
    }
    lines.forEachIndexed { i, (e, t) -> Appear(delay = 180 + i * 130) { RwRowStat(e, t) } }
    if (d.numberOnes.isNotEmpty()) {
        Appear(delay = 180 + lines.size * 130) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 14.dp)) {
                RwEyebrow("N°1 AU BILLBOARD")
                Spacer(Modifier.height(8.dp))
                d.numberOnes.take(2).forEach { n ->
                    Text("« ${n.title} » · ${n.weeks} semaine${if (n.weeks > 1) "s" else ""} en tête", color = theme.text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }
            }
        }
    }
    Spacer(Modifier.height(28.dp))
}

@Composable
private fun FinaleSlide(d: RewindData) {
    val theme = Nova.theme
    val t by rememberInfiniteTransition(label = "fin").animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "fina")
    Spacer(Modifier.height(48.dp))
    Appear { RwEyebrow("MERCI POUR CETTE ÉCOUTE") }
    Spacer(Modifier.height(14.dp))
    Appear(delay = 200) {
        Text(
            "Voilà ${if (d.spec.kind == RewindSpec.Kind.MONTH) "ton mois" else "ton année"} ${d.spec.ofLabel}",
            color = theme.text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
        )
    }
    Spacer(Modifier.height(26.dp))
    Appear(delay = 420, distance = 34.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { scaleX = 1f + t * 0.03f; scaleY = 1f + t * 0.03f }) {
            CountUp(d.totals.plays, MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Black), theme.primary, durationMs = 1800)
            Text("écoutes · ${formatDuration(d.totals.durationMs)}", color = theme.text, style = MaterialTheme.typography.titleLarge)
        }
    }
    Spacer(Modifier.height(20.dp))
    Appear(delay = 900) {
        Text("Partage ta carte pour montrer ${if (d.spec.kind == RewindSpec.Kind.MONTH) "ton mois" else "ton année"}.", color = theme.textSecondary, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(40.dp))
}

/* ------------------------------ briques ------------------------------ */

@Composable
private fun RwEyebrow(text: String) {
    Text(text, color = Nova.theme.accent, style = MaterialTheme.typography.labelMedium, letterSpacing = 3.sp, textAlign = TextAlign.Center)
}

@Composable
private fun RwTitle(text: String) {
    Text(text, color = Nova.theme.text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
}

@Composable
private fun RwBigStat(value: Long, label: String, theme: NovaTheme, duration: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (duration) Text(formatDuration(value), color = theme.text, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
        else CountUp(value.toInt(), MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black), theme.text, durationMs = 1400)
        Text(label, color = theme.textSecondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        val fill = remember { Animatable(0f) }
        LaunchedEffect(Unit) { fill.animateTo(1f, tween(1000, easing = FastOutSlowInEasing)) }
        Box(Modifier.width(160.dp).height(4.dp).clip(CircleShape).background(theme.textSecondary.copy(alpha = 0.18f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fill.value).clip(CircleShape).background(Brush.horizontalGradient(listOf(theme.primary, theme.secondary))))
        }
    }
}

@Composable
private fun RwRowStat(emoji: String, text: String) {
    val theme = Nova.theme
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp)).background(theme.surface.copy(alpha = 0.72f))
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.width(14.dp))
        Text(text, color = theme.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ActionPill(text: String, onClick: () -> Unit) {
    val theme = Nova.theme
    val press = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Box(
        Modifier.graphicsLayer { scaleX = press.value; scaleY = press.value }
            .clip(CircleShape).background(Brush.horizontalGradient(listOf(theme.primary, theme.secondary)))
            .clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null
            ) {
                scope.launch { press.animateTo(0.94f, tween(80)); press.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                onClick()
            }
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
    val shimmer by rememberInfiniteTransition(label = "rwcard").animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "rwcarda")
    NovaCard {
        Box(
            Modifier.fillMaxWidth().clickable { onOpen(s.key) }
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            theme.primary.copy(alpha = 0.06f + 0.10f * kotlin.math.abs(sin(shimmer * PI.toFloat()))),
                            theme.secondary.copy(alpha = 0.05f),
                            theme.surface
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column {
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
}
