package com.novastats.app.ui.screens

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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.novastats.app.ui.theme.Nova
import com.novastats.app.ui.theme.NovaColors
import com.novastats.app.ui.theme.NovaTheme
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/*
 * Briques visuelles « premium » partagées : fond d'art flouté, particules, confettis,
 * entrées en cascade, cartes de verre, compteurs qui défilent, graphiques, pochettes cerclées.
 * Utilisées par Nova Rewind, l'Accueil et les popups de détail.
 */

/* ============================== fonds ============================== */

/**
 * Fond plein écran : la pochette (ou la photo) du moment, chargée en tout petit puis étirée → flou naturel,
 * voile de dégradé par-dessus, nappes de couleur qui dérivent.
 */
@Composable
fun RewindBackdrop(
    url: String?,
    theme: NovaTheme,
    modifier: Modifier = Modifier,
    /** Opacité de l'image floutée. */
    artAlpha: Float = 0.95f,
    /** Force du voile sombre par-dessus (1 = cinéma, >1 = encore plus sombre, <1 = plus clair). */
    scrim: Float = 0.82f,
    /** Nappes de couleur qui dérivent. */
    aurora: Boolean = true
) {
    val ctx = LocalContext.current
    val ken by rememberInfiniteTransition(label = "bk").animateFloat(1f, 1.14f, infiniteRepeatable(tween(20000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bk2")
    val palette = if (theme.id == "survivor") NovaColors.PrideFlags.flatten().take(5) else listOf(theme.primary, theme.secondary, theme.accent, theme.glowSecondary)
    val t by rememberInfiniteTransition(label = "aur").animateFloat(0f, 1f, infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Restart), label = "aur2")

    Box(modifier.background(Brush.verticalGradient(listOf(theme.background, Color.Black)))) {
        if (!url.isNullOrBlank()) {
            // Image chargée en 64 px puis étirée : flou doux, sans coût de rendu
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(url).size(96).crossfade(700).allowHardware(false).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.colorMatrix(remember { artBoost() }),
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = ken; scaleY = ken; alpha = artAlpha }
            )
        }
        // Voile : dégradé bas + assombrissement général pour que le texte reste lisible
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        theme.background.copy(alpha = (0.40f * scrim).coerceIn(0f, 1f)),
                        theme.background.copy(alpha = (0.62f * scrim).coerceIn(0f, 1f)),
                        Color.Black.copy(alpha = (0.88f * scrim).coerceIn(0f, 1f))
                    )
                )
            )
        )
        // Nappes de couleur
        if (aurora) androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            palette.forEachIndexed { i, c ->
                val a = t * 2f * PI.toFloat() + i * 1.35f
                val cx = size.width * (0.5f + 0.36f * cos(a))
                val cy = size.height * (0.34f + 0.30f * sin(a * 0.8f + i))
                val r = size.width * (0.58f + 0.14f * sin(a * 1.3f))
                drawCircle(
                    brush = Brush.radialGradient(listOf(c.copy(alpha = 0.36f), Color.Transparent), center = Offset(cx, cy), radius = r),
                    radius = r, center = Offset(cx, cy)
                )
            }
        }
    }
}

/** Sature et éclaircit la pochette du fond : sinon une pochette sombre reste une tache grise à l'écran. */
private fun artBoost(): ColorMatrix = ColorMatrix(
    floatArrayOf(
        1.760f, -0.418f, -0.042f, 0f, 0.050f,
        -0.125f, 1.467f, -0.042f, 0f, 0.050f,
        -0.125f, -0.418f, 1.843f, 0f, 0.050f,
        0f, 0f, 0f, 1f, 0f
    )
)

/** Particules qui montent lentement. */
@Composable
fun RewindParticles(theme: NovaTheme, modifier: Modifier = Modifier) {
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
            drawCircle(color = palette[(s[4].toInt() % palette.size)].copy(alpha = 0.10f + s[3] * 0.26f), radius = 1.8f + s[3] * 3.8f, center = Offset(x, y))
        }
    }
}

/** Pluie de confettis (or / drapeaux). */
@Composable
fun Celebration(modifier: Modifier = Modifier, pieces: Int = 80) {
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
fun Appear(delay: Int = 0, distance: Dp = 26.dp, from: Float = 0.94f, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
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
fun StaggerTitle(text: String, style: TextStyle, color: Color, stepMs: Int = 42, startMs: Int = 120) {
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
fun sweepBrush(theme: NovaTheme, periodMs: Int = 2600): Brush {
    val t by rememberInfiniteTransition(label = "sweep").animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart), label = "sweep2")
    val palette = if (theme.id == "survivor") NovaColors.PrideFlags.flatten().take(7) else listOf(theme.primary, theme.secondary, theme.accent, theme.primary)
    val x = -700f + t * 2100f
    return Brush.linearGradient(palette + palette.first(), start = Offset(x, 0f), end = Offset(x + 700f, 0f))
}

/** Chiffre géant qui défile, en dégradé balayant. */
@Composable
fun CountUp(
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
fun GlassCard(
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
fun GlassChip(text: String, emoji: String = "", glow: Color = Nova.theme.primary, modifier: Modifier = Modifier) {
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
fun GlowRing(url: String?, fallback: String, size: Dp, circle: Boolean, modifier: Modifier = Modifier) {
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
fun HourChart(hours: List<Int>, accent: Color, modifier: Modifier = Modifier) {
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
fun WeekdayChart(values: List<Int>, accent: Color, modifier: Modifier = Modifier) {
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


/** Bouton d'action : dégradé balayant + ressort au pressé. */
@Composable
fun ShimmerPill(text: String, onClick: () -> Unit) {
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

/** Bloc de statistiques « verre » : le premier chiffre en très gros (compteur animé), les autres en lignes. */
@Composable
fun StatGlass(
    plays: Int,
    durationMs: Long,
    extras: List<Pair<String, String>> = emptyList(),
    playLabel: String = "écoutes",
    color: Color = Nova.theme.primary,
    modifier: Modifier = Modifier
) {
    val theme = Nova.theme
    val brush = sweepBrush(theme, 3000)
    GlassCard(modifier = modifier, glow = color) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                CountUp(
                    plays,
                    MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black, fontSize = 52.sp, letterSpacing = (-2.5).sp),
                    color, brush = brush
                )
                Text(playLabel, color = theme.textSecondary, style = MaterialTheme.typography.bodyMedium, letterSpacing = 2.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatDuration(durationMs), color = theme.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("de musique", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                extras.forEach { (v, l) ->
                    Spacer(Modifier.height(6.dp))
                    Text(v, color = theme.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(l, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Médaille de certification : dégradé métallique + emoji du niveau. */
@Composable
fun MedalBadge(level: com.novastats.app.domain.CertLevel?, multiplier: Int = 1, size: Dp = 64.dp, modifier: Modifier = Modifier) {
    val theme = Nova.theme
    val colors = when (level) {
        com.novastats.app.domain.CertLevel.SILVER -> listOf(Color(0xFFD7D7D7), Color(0xFF8A8A8A))
        com.novastats.app.domain.CertLevel.GOLD -> listOf(Color(0xFFFFE082), Color(0xFFB8860B))
        com.novastats.app.domain.CertLevel.PLATINUM -> listOf(Color(0xFFE5E4E2), Color(0xFF8E9AA6))
        com.novastats.app.domain.CertLevel.DIAMOND -> listOf(Color(0xFFB9F2FF), Color(0xFF4FC3F7))
        else -> listOf(theme.surface, theme.background)
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size * 1.22f).graphicsLayer { alpha = 0.5f }
                .background(Brush.radialGradient(listOf(colors.first().copy(alpha = 0.65f), Color.Transparent)), CircleShape)
        )
        Box(
            Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(colors))
                .border(2.dp, Color.White.copy(alpha = 0.32f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(level?.emoji ?: "—", fontSize = (size.value / 2.4f).sp)
        }
        if (multiplier > 1) {
            Box(
                Modifier.align(Alignment.BottomEnd).clip(CircleShape).background(theme.background)
                    .border(1.dp, colors.first(), CircleShape).padding(horizontal = 7.dp, vertical = 3.dp)
            ) { Text("×$multiplier", color = theme.text, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

/**
 * Fond d'onglet : art du moment flouté (très discret) + particules, derrière le contenu.
 * [artUrl] : pochette ou photo mise en avant ; null = simple dégradé du thème.
 */
@Composable
fun ScreenBackdrop(artUrl: String?, content: @Composable BoxScope.() -> Unit) {
    val theme = Nova.theme
    Box(Modifier.fillMaxSize()) {
        RewindBackdrop(artUrl, theme, Modifier.fillMaxSize(), artAlpha = 0.98f, scrim = 0.76f)
        RewindParticles(theme, Modifier.fillMaxSize())
        content()
    }
}

