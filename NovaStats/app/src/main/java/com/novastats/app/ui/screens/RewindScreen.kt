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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import coil.request.ImageRequest
import com.novastats.app.NovaStatsApp
import com.novastats.app.data.repository.RewindData
import com.novastats.app.data.repository.RewindEngine
import com.novastats.app.data.repository.RewindSpec
import com.novastats.app.domain.CertLevel
import com.novastats.app.domain.PantheonStatus
import com.novastats.app.ui.onboarding.rememberObAudio
import com.novastats.app.ui.share.ShareCards
import com.novastats.app.ui.theme.Nova
import com.novastats.app.ui.theme.NovaColors
import com.novastats.app.ui.theme.NovaTheme
import com.novastats.app.ui.theme.PrideVeil
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * ✨ Nova Rewind — récap d'un mois ou d'une année, façon keynote « poster » :
 * fond plein écran flouté à partir de la pochette/photo du moment, aurores qui dérivent, particules,
 * typographie XXL, cartes de verre bordées de lumière, graphiques (24 h / jours de la semaine),
 * compteurs en dégradé balayant, confettis à la finale, défilement au doigt en 3D, lecture auto.
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

    val d = data
    val artUrl = when {
        d == null -> null
        d.topArtist?.artist?.photoUrl != null -> d.topArtist?.artist?.photoUrl
        else -> d?.topTrack?.track?.coverUrl
    }

    Box(Modifier.fillMaxSize().background(theme.background)) {
        // 1. Fond plein écran (pochette floutée) + nappes + particules
        RewindBackdrop(artUrl, theme, Modifier.fillMaxSize())
        RewindParticles(theme, Modifier.fillMaxSize())
        if (theme.id == "survivor") PrideVeil(Modifier.fillMaxSize().graphicsLayer { alpha = 0.3f })

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            /* ---------- Barre haute ---------- */
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
                                .background(
                                    if (on) Brush.horizontalGradient(listOf(theme.primary, theme.secondary))
                                    else Brush.horizontalGradient(listOf(theme.surface.copy(alpha = 0.6f), theme.surface.copy(alpha = 0.6f)))
                                )
                                .clickable { if (!on) { key = s.key; index = 0; playing = true } }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(s.label, color = if (on) Color.White else theme.textSecondary, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                            if (on) Box(Modifier.padding(top = 3.dp).width(18.dp).height(2.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f)))
                        }
                    }
                }
            }

            if (d == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (key == null && specs.isEmpty()) Text("Aucun Rewind disponible pour l'instant", color = theme.textSecondary)
                    else CircularProgressIndicator(color = theme.primary)
                }
            } else {
                val slides = remember(d) { slidesFor(d) }
                val slide = slides.getOrNull(index) ?: Slide.FINALE

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
                        if (slide == Slide.FINALE) audio.boom(0.16f)
                        else if (forward) audio.whoosh(0.1f) else audio.tick(0.12f)
                    }
                }

                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    slides.forEachIndexed { i, _ ->
                        val f = when { i < index -> 1f; i == index -> progress.value; else -> 0f }
                        Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(theme.textSecondary.copy(alpha = 0.22f))) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(f).clip(CircleShape).background(Brush.horizontalGradient(listOf(theme.primary, theme.secondary))))
                        }
                    }
                }

                /* ---------- Slide ---------- */
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
                            rotationY = (drag.value / 24f).coerceIn(-9f, 9f)
                            cameraDistance = 14f * density
                            alpha = 1f - (abs(drag.value) / 900f).coerceIn(0f, 0.45f)
                        }
                ) {
                    AnimatedContent(
                        targetState = slide,
                        transitionSpec = {
                            val dir = if (forward) 1 else -1
                            (fadeIn(tween(340, 100)) + slideInHorizontally(tween(450, 100, easing = FastOutSlowInEasing)) { dir * it / 3 } + scaleIn(tween(450, 100), initialScale = 0.88f)) togetherWith
                                (fadeOut(tween(220)) + slideOutHorizontally(tween(320)) { -dir * it / 4 } + scaleOut(tween(320), targetScale = 1.10f))
                        },
                        label = "rewind"
                    ) { s ->
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 4.dp),
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
                            ShimmerPill("Partager la carte") {
                                busy = true
                                scope.launch {
                                    runCatching {
                                        val f = ShareCards.renderRewind(ctx, d, theme)
                                        ShareCards.share(ctx, f, "Partager mon Rewind")
                                    }
                                    busy = false
                                }
                            }
                        } else ShimmerPill("Suivant") { forward = true; index++ }
                    }
                }
            }
        }
    }
}

/** Durée d'affichage d'une slide en lecture automatique. */
private const val SLIDE_MS = 7000

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

/* ============================== fonds ============================== */

/**
 * Fond plein écran : la pochette (ou la photo) du moment, chargée en tout petit puis étirée → flou naturel,
 * voile de dégradé par-dessus, nappes de couleur qui dérivent.
 */
@Composable
private fun RewindBackdrop(url: String?, theme: NovaTheme, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val ken by rememberInfiniteTransition(label = "bk").animateFloat(1f, 1.14f, infiniteRepeatable(tween(20000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bk2")
    val palette = if (theme.id == "survivor") NovaColors.PrideFlags.flatten().take(5) else listOf(theme.primary, theme.secondary, theme.accent, theme.glowSecondary)
    val t by rememberInfiniteTransition(label = "aur").animateFloat(0f, 1f, infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Restart), label = "aur2")

    Box(modifier.background(Brush.verticalGradient(listOf(theme.background, Color.Black)))) {
        if (!url.isNullOrBlank()) {
            // Image chargée en 64 px puis étirée : flou doux, sans coût de rendu
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(url).size(64).crossfade(700).allowHardware(false).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = ken; scaleY = ken; alpha = 0.85f }
            )
        }
        // Voile : dégradé bas + assombrissement général pour que le texte reste lisible
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(theme.background.copy(alpha = 0.55f), theme.background.copy(alpha = 0.80f), Color.Black.copy(alpha = 0.94f)))))
        // Nappes de couleur
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            palette.forEachIndexed { i, c ->
                val a = t * 2f * PI.toFloat() + i * 1.35f
                val cx = size.width * (0.5f + 0.36f * cos(a))
                val cy = size.height * (0.34f + 0.30f * sin(a * 0.8f + i))
                val r = size.width * (0.58f + 0.14f * sin(a * 1.3f))
                drawCircle(
                    brush = Brush.radialGradient(listOf(c.copy(alpha = 0.18f), Color.Transparent), center = Offset(cx, cy), radius = r),
                    radius = r, center = Offset(cx, cy)
                )
            }
        }
    }
}

/** Particules qui montent lentement. */
@Composable
private fun RewindParticles(theme: NovaTheme, modifier: Modifier = Modifier) {
    val palette = if (theme.id == "survivor") NovaColors.PrideConfetti else listOf(theme.primary, theme.secondary, theme.accent, theme.glowSecondary)
    val seeds = remember(palette) {
        List(46) { i ->
            floatArrayOf((i * 37 % 100) / 100f, (i * 61 % 100) / 100f, 0.35f + (i * 13 % 50) / 100f, 0.5f + (i * 7 % 60) / 100f, (i % palette.size).toFloat())
        }
    }
    val t by rememberInfiniteTransition(label = "rwp").animateFloat(0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart), label = "rwp2")
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width; val h = size.height
        seeds.forEach { s ->
            val y = h - ((t * s[2] + s[1]) % 1f) * h
            val x = s[0] * w + sin((t * 6.28f + s[1] * 6.28f)) * 26f
            drawCircle(color = palette[(s[4].toInt() % palette.size)].copy(alpha = 0.06f + s[3] * 0.16f), radius = 1.6f + s[3] * 3.4f, center = Offset(x, y))
        }
    }
}

/** Pluie de confettis (or / drapeaux). */
@Composable
private fun Celebration(modifier: Modifier = Modifier, pieces: Int = 80) {
    val theme = Nova.theme
    val gold = listOf(Color(0xFFFFD700), Color(0xFFFFF3B0), Color(0xFFFFB347), Color(0xFFFFFFFF), Color(0xFFFFE9A8))
    val palette = if (theme.id == "survivor") NovaColors.PrideConfetti else gold
    val seeds = remember(palette) {
        val rnd = Random(7)
        List(pieces) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat(), rnd.nextInt(palette.size).toFloat(), rnd.nextFloat(), rnd.nextFloat()) }
    }
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { anim.animateTo(1f, tween(5000, easing = LinearEasing)) }
    val p = anim.value
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width; val h = size.height
        seeds.forEach { s ->
            val t = ((p * (0.6f + s[2] * 0.8f)) + s[5]) % 1f
            val x = s[0] * w + sin(t * 6.28f + s[4] * 6.28f) * 70f
            val y = -60f + t * (h + 120f)
            val spin = abs(sin(t * 18.84f + s[4] * 6.28f))
            val a = ((1f - t) * 0.95f).coerceIn(0f, 1f)
            val col = palette[(s[3].toInt() % palette.size)]
            drawRoundRect(
                color = col.copy(alpha = a), topLeft = Offset(x, y),
                size = Size((5f + s[1] * 7f) * (0.35f + 0.65f * spin) + 1.5f, 9f + s[1] * 13f),
                cornerRadius = CornerRadius(2f, 2f)
            )
        }
    }
}

/* ============================== animations ============================== */

/** Entrée en cascade avec léger rebond. */
@Composable
private fun Appear(delay: Int = 0, distance: Dp = 26.dp, from: Float = 0.94f, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(delay.toLong()); shown = true }
    val a by animateFloatAsState(
        if (shown) 1f else 0f,
        spring(dampingRatio = 0.72f, stiffness = 140f, visibilityThreshold = 0.001f),
        label = "appear"
    )
    Box(
        modifier.graphicsLayer {
            alpha = a.coerceIn(0f, 1f)
            translationY = (1f - a) * distance.toPx()
            scaleX = from + (1f - from) * a
            scaleY = from + (1f - from) * a
        }
    ) { content() }
}

/** Titre qui s'écrit lettre par lettre. */
@Composable
private fun StaggerTitle(text: String, style: TextStyle, color: Color, stepMs: Int = 42, startMs: Int = 120) {
    val chars = remember(text) { text.toList() }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        chars.forEachIndexed { i, ch ->
            val a = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay((startMs + i * stepMs).toLong())
                a.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            }
            val v = a.value
            Text(
                if (ch == ' ') "\u00A0" else ch.toString(),
                style = style, color = color.copy(alpha = v),
                modifier = Modifier.graphicsLayer {
                    translationY = (1f - v) * 30f
                    scaleX = 0.84f + 0.16f * v
                    scaleY = 0.84f + 0.16f * v
                }
            )
        }
    }
}

/** Dégradé qui balaie le texte en boucle. */
@Composable
private fun sweepBrush(theme: NovaTheme, periodMs: Int = 2600): Brush {
    val t by rememberInfiniteTransition(label = "sweep").animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart), label = "sweep2")
    val palette = if (theme.id == "survivor") NovaColors.PrideFlags.flatten().take(7) else listOf(theme.primary, theme.secondary, theme.accent, theme.primary)
    val x = -700f + t * 2100f
    return Brush.linearGradient(palette + palette.first(), start = Offset(x, 0f), end = Offset(x + 700f, 0f))
}

/** Chiffre géant qui défile, en dégradé balayant. */
@Composable
private fun CountUp(
    value: Int, style: TextStyle, color: Color,
    modifier: Modifier = Modifier, brush: Brush? = null, durationMs: Int = 1400, suffix: String = ""
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(value) {
        anim.snapTo(0f)
        anim.animateTo(value.toFloat(), tween(durationMs, easing = EaseOutCubic))
    }
    Text(
        formatCount(anim.value.toInt()) + suffix,
        style = if (brush != null) style.copy(brush = brush) else style,
        color = if (brush == null) color else Color.Unspecified,
        modifier = modifier
    )
}

/* ============================== briques « verre » ============================== */

/** Carte de verre : surface translucide, liseré lumineux, ombre colorée. */
@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    glow: Color = Nova.theme.primary,
    content: @Composable ColumnScope.() -> Unit
) {
    val theme = Nova.theme
    Box(
        modifier.fillMaxWidth()
            .shadow(26.dp, RoundedCornerShape(26.dp), spotColor = glow.copy(alpha = 0.35f), ambientColor = glow.copy(alpha = 0.18f))
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.verticalGradient(listOf(theme.surface.copy(alpha = 0.92f), theme.surface.copy(alpha = 0.62f))))
            .border(1.dp, Brush.horizontalGradient(listOf(glow.copy(alpha = 0.65f), glow.copy(alpha = 0.08f), theme.secondary.copy(alpha = 0.45f))), RoundedCornerShape(26.dp))
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), content = content)
    }
}

/** Pastille d'info (verre, plus petite). */
@Composable
private fun GlassChip(text: String, emoji: String = "", glow: Color = Nova.theme.primary, modifier: Modifier = Modifier) {
    val theme = Nova.theme
    Box(
        modifier.clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(glow.copy(alpha = 0.22f), theme.surface.copy(alpha = 0.75f))))
            .border(1.dp, glow.copy(alpha = 0.30f), CircleShape)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(if (emoji.isEmpty()) text else "$emoji  $text", color = theme.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}

/** Pochette cerclée d'un anneau qui tourne, avec travelling interne. */
@Composable
private fun GlowRing(url: String?, fallback: String, size: Dp, circle: Boolean, modifier: Modifier = Modifier) {
    val theme = Nova.theme
    val palette = if (theme.id == "survivor") NovaColors.PrideFlags.flatten().take(8) else listOf(theme.primary, theme.secondary, theme.accent, theme.primary)
    val t by rememberInfiniteTransition(label = "ring").animateFloat(0f, 360f, infiniteRepeatable(tween(11000, easing = LinearEasing), RepeatMode.Restart), label = "ringa")
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulsea")
    val ken by rememberInfiniteTransition(label = "ken").animateFloat(1f, 1.12f, infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "kena")
    val shape = if (circle) CircleShape else RoundedCornerShape(30.dp)
    val inner = if (circle) CircleShape else RoundedCornerShape(24.dp)
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size * 1.34f).graphicsLayer { scaleX = pulse; scaleY = pulse; alpha = 0.6f }
                .background(Brush.radialGradient(listOf(theme.primary.copy(alpha = 0.6f), Color.Transparent)), shape)
        )
        Box(Modifier.size(size + 20.dp).graphicsLayer { rotationZ = t }.background(Brush.sweepGradient(palette + palette.first()), shape))
        Box(Modifier.size(size + 9.dp).background(Color.Black.copy(alpha = 0.55f), shape))
        Box(Modifier.size(size).clip(inner)) {
            if (url != null) {
                AsyncImage(
                    model = url, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = ken; scaleY = ken }
                )
            } else {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(theme.primary, theme.glowSecondary))), contentAlignment = Alignment.Center) {
                    Text(fallback.take(1).uppercase(), color = Color.White, fontSize = (size.value / 3).sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

/** Graphique des 24 heures d'écoute. */
@Composable
private fun HourChart(hours: List<Int>, accent: Color, modifier: Modifier = Modifier) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(hours) { anim.animateTo(1f, tween(1400, easing = FastOutSlowInEasing)) }
    val theme = Nova.theme
    Column(modifier) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(92.dp)) {
            val max = (hours.maxOrNull() ?: 0).coerceAtLeast(1)
            val slot = size.width / hours.size
            hours.forEachIndexed { i, v ->
                val h = (v.toFloat() / max) * (size.height - 6f) * anim.value
                val x = slot * i + slot / 2f
                val w = slot * 0.56f
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.22f))),
                    topLeft = Offset(x - w / 2f, size.height - h), size = Size(w, h.coerceAtLeast(2f)),
                    cornerRadius = CornerRadius(w / 2f, w / 2f)
                )
            }
            drawLine(color = theme.textSecondary.copy(alpha = 0.18f), start = Offset(0f, size.height), end = Offset(size.width, size.height), strokeWidth = 1.5f)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0h", "6h", "12h", "18h", "23h").forEach {
                Text(it, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
            }
        }
    }
}

/** Barres des 7 jours de la semaine (lundi → dimanche). */
@Composable
private fun WeekdayChart(values: List<Int>, accent: Color, modifier: Modifier = Modifier) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(values) { anim.animateTo(1f, tween(1400, delayMillis = 250, easing = FastOutSlowInEasing)) }
    val theme = Nova.theme
    val labels = listOf("L", "M", "M", "J", "V", "S", "D")
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        values.take(7).forEachIndexed { i, v ->
            val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Box(
                    Modifier.width(22.dp).height(((v.toFloat() / max) * 62f * anim.value).coerceAtLeast(3f).dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.30f))))
                )
                Spacer(Modifier.height(6.dp))
                Text(labels.getOrElse(i) { "" }, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
            }
        }
    }
}

/* ------------------------------ slides ------------------------------ */

@Composable
private fun CoverSlide(d: RewindData) {
    val theme = Nova.theme
    val brush = sweepBrush(theme)
    val float by rememberInfiniteTransition(label = "cv").animateFloat(-8f, 8f, infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "cvf")
    Spacer(Modifier.height(64.dp))
    Appear(delay = 80) { RwEyebrow("NOVA REWIND") }
    Spacer(Modifier.height(18.dp))
    StaggerTitle(
        if (d.spec.kind == RewindSpec.Kind.MONTH) monthName(d.spec.from.monthValue) else d.spec.label,
        MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Black, fontSize = 44.sp, letterSpacing = (-1.5).sp, textAlign = TextAlign.Center),
        theme.text
    )
    if (d.spec.kind == RewindSpec.Kind.MONTH) {
        Spacer(Modifier.height(4.dp))
        Appear(delay = 900) {
            Text(
                d.spec.from.year.toString(),
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Black, fontSize = 68.sp, letterSpacing = (-3).sp, brush = brush),
                color = Color.Unspecified
            )
        }
    }
    Spacer(Modifier.height(46.dp))
    Appear(delay = 700, distance = 40.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { translationY = float }) {
            CountUp(
                d.totals.plays,
                MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Black, fontSize = 86.sp, letterSpacing = (-4).sp),
                theme.primary, brush = brush, durationMs = 1900
            )
            Text("écoutes", color = theme.text, style = MaterialTheme.typography.titleLarge, letterSpacing = 3.sp)
        }
    }
    Spacer(Modifier.height(26.dp))
    Appear(delay = 1500) {
        GlassChip(if (d.spec.kind == RewindSpec.Kind.MONTH) "ton mois, en musique" else "ton année, en musique", glow = theme.secondary)
    }
    Spacer(Modifier.height(50.dp))
}

@Composable
private fun NumbersSlide(d: RewindData) {
    val theme = Nova.theme
    val brush = sweepBrush(theme, 2800)
    Spacer(Modifier.height(38.dp))
    Appear { RwTitle("Les chiffres") }
    Spacer(Modifier.height(22.dp))
    Appear(delay = 180, distance = 34.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CountUp(
                d.totals.plays,
                MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Black, fontSize = 76.sp, letterSpacing = (-3.5).sp),
                theme.primary, brush = brush, durationMs = 1700
            )
            Text("écoutes", color = theme.text, style = MaterialTheme.typography.titleMedium, letterSpacing = 3.sp)
        }
    }
    Spacer(Modifier.height(12.dp))
    Appear(delay = 340) { Text(formatDuration(d.totals.durationMs) + " de musique", color = theme.textSecondary, style = MaterialTheme.typography.titleLarge) }
    Spacer(Modifier.height(22.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MiniStat("${d.totals.days}", "jours d'écoute", "📅", Modifier.weight(1f), 0)
        MiniStat(formatCount(d.totals.artists), "artistes", "🎤", Modifier.weight(1f), 120)
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MiniStat(formatCount(d.totals.tracks), "titres", "🎵", Modifier.weight(1f), 240)
        MiniStat(formatCount(d.totals.albums), "albums", "💿", Modifier.weight(1f), 360)
    }
    d.playsDeltaPct?.let { p ->
        Appear(delay = 1000) {
            Row(Modifier.padding(top = 16.dp)) {
                GlassChip(
                    if (p >= 0) "▲ $p % de plus que la période précédente" else "▼ ${-p} % de moins que la période précédente",
                    glow = theme.accent
                )
            }
        }
    }
    Spacer(Modifier.height(34.dp))
}

@Composable
private fun MiniStat(value: String, label: String, emoji: String, modifier: Modifier = Modifier, delay: Int = 0) {
    val theme = Nova.theme
    Appear(delay = delay, modifier = modifier) {
        GlassCard(glow = theme.secondary) {
            Text(emoji, fontSize = 20.sp)
            Spacer(Modifier.height(6.dp))
            Text(value, color = theme.text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(label, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TopArtistSlide(d: RewindData) {
    val theme = Nova.theme
    val a = d.topArtist ?: return
    val brush = sweepBrush(theme, 2400)
    Spacer(Modifier.height(26.dp))
    Appear { RwEyebrow("ARTISTE N°1") }
    Spacer(Modifier.height(18.dp))
    Appear(delay = 200, distance = 36.dp) {
        GlowRing(a.artist.photoUrl ?: d.topTrack?.track?.coverUrl, a.artist.name, 210.dp, circle = true)
    }
    Spacer(Modifier.height(24.dp))
    Appear(delay = 480) {
        Text(
            a.artist.name, color = theme.text,
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
            textAlign = TextAlign.Center
        )
    }
    Spacer(Modifier.height(14.dp))
    Appear(delay = 640) {
        CountUp(
            a.periodPlays,
            MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black, fontSize = 46.sp, letterSpacing = (-2).sp),
            theme.primary, brush = brush, suffix = " écoutes"
        )
    }
    Spacer(Modifier.height(16.dp))
    Appear(delay = 820) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassChip("${d.topArtistShare} % de ${if (d.spec.kind == RewindSpec.Kind.MONTH) "ton mois" else "ton année"}", "🔥", theme.accent, Modifier.weight(1f))
        }
    }
    if (d.topArtistDays > 0) {
        Spacer(Modifier.height(10.dp))
        Appear(delay = 940) {
            GlassCard(glow = theme.primary) {
                Text("présent ${d.topArtistDays} jour${if (d.topArtistDays > 1) "s" else ""} sur ${d.totals.days}", color = theme.text, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                val fill = remember { Animatable(0f) }
                LaunchedEffect(Unit) { fill.animateTo(d.topArtistDays.toFloat() / d.totals.days.coerceAtLeast(1), tween(1200, easing = FastOutSlowInEasing)) }
                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(theme.textSecondary.copy(alpha = 0.15f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fill.value.coerceIn(0f, 1f)).clip(CircleShape).background(Brush.horizontalGradient(listOf(theme.primary, theme.secondary))))
                }
            }
        }
    }
    d.previousTopArtistName?.takeIf { it != a.artist.name }?.let {
        Spacer(Modifier.height(10.dp))
        Appear(delay = 1100) { Text("tu écoutais surtout $it avant", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall) }
    }
    Spacer(Modifier.height(30.dp))
}

@Composable
private fun TopTrackSlide(d: RewindData) {
    val theme = Nova.theme
    val t = d.topTrack ?: return
    val brush = sweepBrush(theme, 2200)
    Spacer(Modifier.height(26.dp))
    Appear { RwEyebrow("TITRE N°1") }
    Spacer(Modifier.height(18.dp))
    Appear(delay = 200, distance = 36.dp) { GlowRing(t.track.coverUrl, t.track.title, 210.dp, circle = false) }
    Spacer(Modifier.height(24.dp))
    Appear(delay = 480) {
        Text(
            t.track.title, color = theme.text,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
            textAlign = TextAlign.Center, maxLines = 2
        )
    }
    Spacer(Modifier.height(6.dp))
    Appear(delay = 560) { Text(t.artistName, color = theme.textSecondary, style = MaterialTheme.typography.titleMedium) }
    Spacer(Modifier.height(14.dp))
    Appear(delay = 700) {
        CountUp(
            t.periodPlays,
            MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black, fontSize = 44.sp, letterSpacing = (-2).sp),
            theme.primary, brush = brush, suffix = " écoutes"
        )
    }
    if (d.topTrackDays > 0) {
        Spacer(Modifier.height(12.dp))
        Appear(delay = 880) { GlassChip("dans tes oreilles ${d.topTrackDays} jour${if (d.topTrackDays > 1) "s" else ""} différents", "🎧", theme.accent) }
    }
    d.topAlbum?.let { al ->
        Spacer(Modifier.height(20.dp))
        Appear(delay = 1000) {
            GlassCard(glow = theme.secondary) {
                RwEyebrow("ALBUM N°1")
                Spacer(Modifier.height(6.dp))
                Text(al.album.title, color = theme.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
                Text(al.artistName, color = theme.textSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    Spacer(Modifier.height(30.dp))
}

@Composable
private fun Top5Slide(d: RewindData) {
    val theme = Nova.theme
    Spacer(Modifier.height(28.dp))
    Appear { RwTitle("Ton top 5") }
    Spacer(Modifier.height(18.dp))
    val max = d.topTracks.take(5).maxOf { it.periodPlays }.coerceAtLeast(1)
    d.topTracks.take(5).forEachIndexed { i, t ->
        val big = i == 0
        Appear(delay = 200 + i * 150) {
            val w = remember { Animatable(0f) }
            LaunchedEffect(Unit) { w.animateTo(t.periodPlays.toFloat() / max, tween(1000, delayMillis = 200 + i * 130, easing = FastOutSlowInEasing)) }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${i + 1}", color = if (big) theme.primary else theme.textSecondary,
                    fontWeight = FontWeight.Black, fontSize = if (big) 30.sp else 22.sp,
                    modifier = Modifier.width(if (big) 38.dp else 30.dp)
                )
                val artSize = if (big) 74.dp else 52.dp
                Box(Modifier.size(artSize).clip(RoundedCornerShape(if (big) 18.dp else 13.dp)).background(theme.surface)) {
                    if (t.track.coverUrl != null) AsyncImage(
                        model = t.track.coverUrl, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(artSize).clip(RoundedCornerShape(if (big) 18.dp else 13.dp))
                    ) else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(t.track.title.take(1).uppercase(), color = theme.textSecondary, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        t.track.title, color = theme.text,
                        style = if (big) MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyLarge,
                        maxLines = 1
                    )
                    Text(t.artistName, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(if (big) 6.dp else 4.dp).clip(CircleShape).background(theme.textSecondary.copy(alpha = 0.14f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(w.value.coerceIn(0f, 1f)).clip(CircleShape).background(Brush.horizontalGradient(listOf(theme.primary, theme.secondary))))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(formatCount(t.periodPlays), color = theme.text, fontWeight = FontWeight.Bold, fontSize = if (big) 20.sp else 15.sp)
            }
        }
    }
    Spacer(Modifier.height(26.dp))
}

@Composable
private fun RhythmSlide(d: RewindData) {
    val theme = Nova.theme
    Spacer(Modifier.height(26.dp))
    Appear { RwTitle("Ton rythme") }
    Spacer(Modifier.height(14.dp))
    Appear(delay = 180) {
        GlassCard(glow = theme.accent) {
            RwEyebrow("TES HEURES D'ÉCOUTE")
            Spacer(Modifier.height(12.dp))
            HourChart(d.hours, theme.accent)
            d.favouriteHour?.let {
                Spacer(Modifier.height(8.dp))
                Text("ton heure de prédilection : ${String.format(Locale.FRANCE, "%02d", it)}h", color = theme.text, style = MaterialTheme.typography.bodyMedium)
            }
            if (d.nightShare >= 0.2f) Text("${(d.nightShare * 100).toInt()} % de tes écoutes entre 22h et 5h", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
    Spacer(Modifier.height(12.dp))
    d.bestDay?.let {
        Appear(delay = 340) {
            GlassCard(glow = theme.primary) {
                RwEyebrow("TON RECORD")
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    CountUp(
                        d.bestDayPlays,
                        MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black, fontSize = 40.sp, letterSpacing = (-2).sp),
                        theme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("écoutes", color = theme.textSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text("le ${it.dayOfMonth} ${monthName(it.monthValue)}", color = theme.text, style = MaterialTheme.typography.bodyLarge)
                d.bestDayTrack?.let { t -> Text("avec « ${t.track.title} »", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Appear(delay = 520) {
        GlassCard(glow = theme.secondary) {
            RwEyebrow("TES JOURS")
            Spacer(Modifier.height(12.dp))
            WeekdayChart(d.weekdays, theme.secondary)
            if (d.longestStreak > 1) {
                Spacer(Modifier.height(10.dp))
                Text("📆 ${d.longestStreak} jours d'affilée au maximum", color = theme.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    if (d.longestSessionMs > 0) {
        Spacer(Modifier.height(12.dp))
        Appear(delay = 700) { GlassChip("plus longue session : ${formatDuration(d.longestSessionMs)}", "🎧", theme.accent) }
    }
    Spacer(Modifier.height(28.dp))
}

@Composable
private fun DiscoverySlide(d: RewindData) {
    val theme = Nova.theme
    Spacer(Modifier.height(26.dp))
    Appear { RwTitle("Tes découvertes") }
    Spacer(Modifier.height(8.dp))
    Appear(delay = 160) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassChip("${formatCount(d.newArtistCount)} nouveaux artistes", "✨", theme.primary, Modifier.weight(1f))
            GlassChip("${formatCount(d.newTrackCount)} titres", "🎵", theme.secondary, Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(16.dp))
    val artists = d.newArtists.take(6)
    artists.chunked(2).forEachIndexed { rowIdx, pair ->
        Spacer(if (rowIdx == 0) Modifier.height(0.dp) else Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pair.forEachIndexed { colIdx, a ->
                Appear(delay = 340 + (rowIdx * 2 + colIdx) * 120, modifier = Modifier.weight(1f)) {
                    GlassCard(glow = theme.accent) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Box(contentAlignment = Alignment.Center) {
                                val pulse by rememberInfiniteTransition(label = "disc").animateFloat(1f, 1.05f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "disc2")
                                Box(Modifier.size(72.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }.clip(CircleShape).background(Brush.radialGradient(listOf(theme.primary.copy(alpha = 0.5f), Color.Transparent))))
                                if (a.photoUrl != null) AsyncImage(
                                    model = a.photoUrl, contentDescription = null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(64.dp).clip(CircleShape)
                                ) else Box(Modifier.size(64.dp).clip(CircleShape).background(theme.surface), contentAlignment = Alignment.Center) {
                                    Text(a.name.take(1).uppercase(), color = theme.text, fontWeight = FontWeight.Black, fontSize = 22.sp)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(a.name, color = theme.text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, textAlign = TextAlign.Center)
                            Text("${formatCount(a.plays)} écoutes", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(28.dp))
}

@Composable
private fun AchievementsSlide(d: RewindData) {
    val theme = Nova.theme
    Spacer(Modifier.height(26.dp))
    Appear { RwTitle("Tes récompenses") }
    Spacer(Modifier.height(16.dp))
    if (d.certifications.isNotEmpty()) {
        Appear(delay = 180) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                d.certifications.take(8).forEach { c ->
                    val level = CertLevel.entries.firstOrNull { it.dbName == c.level }
                    val medalColors = when (level) {
                        CertLevel.SILVER -> listOf(Color(0xFFC0C0C0), Color(0xFF8A8A8A))
                        CertLevel.GOLD -> listOf(Color(0xFFFFD700), Color(0xFFB8860B))
                        CertLevel.PLATINUM -> listOf(Color(0xFFE5E4E2), Color(0xFF9AA0A6))
                        CertLevel.DIAMOND -> listOf(Color(0xFFB9F2FF), Color(0xFF4FC3F7))
                        else -> listOf(theme.primary, theme.secondary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(84.dp)) {
                        Box(
                            Modifier.size(64.dp).clip(CircleShape)
                                .background(Brush.linearGradient(medalColors))
                                .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(level?.emoji ?: "🏅", fontSize = 26.sp)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(level?.label ?: c.level, color = theme.text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        Text(c.name ?: "", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    d.pantheon.take(3).forEachIndexed { i, p ->
        val st = PantheonStatus.fromDb(p.status)
        Spacer(if (i == 0) Modifier.height(0.dp) else Modifier.height(10.dp))
        Appear(delay = 360 + i * 140) {
            GlassCard(glow = theme.glowSecondary) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(st?.emoji ?: "✨", fontSize = 26.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name ?: "Artiste", color = theme.text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text("entre au Panthéon — ${st?.label ?: p.status}", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if (d.numberOnes.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Appear(delay = 360 + d.pantheon.take(3).size * 140) {
            GlassCard(glow = theme.primary) {
                RwEyebrow("N°1 AU BILLBOARD")
                Spacer(Modifier.height(8.dp))
                d.numberOnes.take(2).forEach { n ->
                    Text("« ${n.title} » · ${n.weeks} semaine${if (n.weeks > 1) "s" else ""} en tête", color = theme.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    Spacer(Modifier.height(28.dp))
}

@Composable
private fun FinaleSlide(d: RewindData) {
    val theme = Nova.theme
    val brush = sweepBrush(theme, 3000)
    val float by rememberInfiniteTransition(label = "fin").animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "fina")
    Box(Modifier.fillMaxSize()) {
        Celebration(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(44.dp))
            Appear { RwEyebrow("MERCI POUR CETTE ÉCOUTE") }
            Spacer(Modifier.height(12.dp))
            Appear(delay = 220) {
                Text(
                    "Voilà ${if (d.spec.kind == RewindSpec.Kind.MONTH) "ton mois" else "ton année"} ${d.spec.ofLabel}",
                    color = theme.text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(26.dp))
            Appear(delay = 460, distance = 34.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { scaleX = 1f + float * 0.035f; scaleY = 1f + float * 0.035f }) {
                    CountUp(
                        d.totals.plays,
                        MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Black, fontSize = 88.sp, letterSpacing = (-4).sp),
                        theme.primary, brush = brush, durationMs = 2000
                    )
                    Text("écoutes · ${formatDuration(d.totals.durationMs)}", color = theme.text, style = MaterialTheme.typography.titleLarge)
                }
            }
            Spacer(Modifier.height(22.dp))
            val chips = buildList {
                d.topArtist?.let { add("👑 ${it.artist.name}") }
                d.topTrack?.let { add("🎵 ${it.track.title}") }
                if (d.certifications.isNotEmpty()) add("🏅 ${d.certifications.size} certification${if (d.certifications.size > 1) "s" else ""}")
                if (d.longestStreak > 1) add("📆 ${d.longestStreak} jours d'affilée")
            }
            chips.forEachIndexed { i, c ->
                Spacer(if (i == 0) Modifier.height(0.dp) else Modifier.height(10.dp))
                Appear(delay = 1000 + i * 140) { GlassChip(c, glow = theme.accent) }
            }
            Spacer(Modifier.height(22.dp))
            Appear(delay = 1700) {
                Text("Partage ta carte pour montrer ${if (d.spec.kind == RewindSpec.Kind.MONTH) "ton mois" else "ton année"}.", color = theme.textSecondary, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/* ------------------------------ briques texte ------------------------------ */

private fun monthName(m: Int): String = java.time.Month.of(m).getDisplayName(java.time.format.TextStyle.FULL, Locale.FRANCE).replaceFirstChar { it.uppercase() }

@Composable
private fun RwEyebrow(text: String) {
    Text(text, color = Nova.theme.accent, style = MaterialTheme.typography.labelMedium, letterSpacing = 4.sp, textAlign = TextAlign.Center)
}

@Composable
private fun RwTitle(text: String) {
    Text(
        text, color = Nova.theme.text,
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
        textAlign = TextAlign.Center
    )
}

/** Bouton d'action : dégradé balayant + ressort au pressé. */
@Composable
private fun ShimmerPill(text: String, onClick: () -> Unit) {
    val theme = Nova.theme
    val press = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val t by rememberInfiniteTransition(label = "pill").animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "pill2")
    val x = -420f + t * 1300f
    Box(
        Modifier.graphicsLayer { scaleX = press.value; scaleY = press.value }
            .shadow(18.dp, CircleShape, spotColor = theme.primary.copy(alpha = 0.5f))
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(theme.primary, theme.secondary, theme.primary), start = Offset(x, 0f), end = Offset(x + 620f, 0f)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
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
                            theme.primary.copy(alpha = 0.06f + 0.10f * abs(sin(shimmer * PI.toFloat()))),
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
