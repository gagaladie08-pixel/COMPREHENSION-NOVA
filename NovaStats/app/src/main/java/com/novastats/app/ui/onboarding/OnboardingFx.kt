package com.novastats.app.ui.onboarding

import android.content.Context
import android.graphics.Matrix
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novastats.app.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/* ===================================== Palette onboarding ===================================== */

object ObColors {
    val Gold = Color(0xFFFFD700)
    val GoldGlow = Color(0xFFFFA500)
    val Silver = Color(0xFFC0C0C0)
    val Violet = Color(0xFFBD00FF)
    val Cyan = Color(0xFF00B4FF)
    val DeepViolet = Color(0xFF05000F)
    val Green = Color(0xFF2ECC71)
    val Red = Color(0xFFE74C3C)
    val Blue = Color(0xFF3498DB)
    val OrbOff = Color(0xFF333333)
    val welcomeParticles = listOf(Gold, Violet, Cyan)
}

/* ===================================== Typographies =====================================
 * Cinzel Decorative / Cormorant Garamond / Raleway / Rajdhani ne sont pas embarquées (pas de réseau au build) :
 * on utilise les familles système les plus proches, avec les mêmes graisses / espacements que le cahier des charges.
 */
object ObFonts {
    /** Cinzel Decorative → serif gras, espacement large */
    val cinzel: FontFamily = FontFamily.Serif
    /** Cormorant Garamond → serif italique léger */
    val cormorant: FontFamily = FontFamily.Serif
    /** Raleway → sans-serif light */
    val raleway: FontFamily = runCatching { FontFamily(Typeface.create("sans-serif-light", Typeface.NORMAL)) }.getOrDefault(FontFamily.SansSerif)
    /** Rajdhani → sans-serif condensé gras, majuscules */
    val rajdhani: FontFamily = runCatching { FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD)) }.getOrDefault(FontFamily.SansSerif)
}

/* ===================================== Audio + haptique ===================================== */

/** Sons de l'onboarding (générés dans res/raw) + vibrations. */
class ObAudio(private val context: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(4)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val ids = mapOf(
        "heartbeat" to R.raw.ob_heartbeat, "chime" to R.raw.ob_chime, "chord" to R.raw.ob_chord, "low" to R.raw.ob_low,
        "whoosh" to R.raw.ob_whoosh, "scan" to R.raw.ob_scan, "boom" to R.raw.ob_boom, "crystal" to R.raw.ob_crystal
    ).mapValues { runCatching { pool.load(context, it.value, 1) }.getOrDefault(0) }
    private var ambient: MediaPlayer? = null
    private val vibrator: Vibrator? = runCatching { context.getSystemService(Vibrator::class.java) }.getOrNull()

    fun play(name: String, volume: Float = 1f) { ids[name]?.takeIf { it != 0 }?.let { runCatching { pool.play(it, volume, volume, 1, 0, 1f) } } }

    fun startAmbient() {
        if (ambient != null) return
        ambient = runCatching { MediaPlayer.create(context, R.raw.ob_ambient)?.apply { isLooping = true; setVolume(0.35f, 0.35f); start() } }.getOrNull()
    }
    fun stopAmbient() { runCatching { ambient?.stop(); ambient?.release() }; ambient = null }

    fun vibrate(pattern: LongArray) { runCatching { vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1)) } }
    fun tap() = vibrate(longArrayOf(0, 25))
    fun soft() = vibrate(longArrayOf(0, 60))
    fun doublePulse() = vibrate(longArrayOf(0, 80, 90, 80))
    fun triplePulse() = vibrate(longArrayOf(0, 70, 80, 70, 80, 140))
    fun impact() = vibrate(longArrayOf(0, 260))
    fun heartbeat() = vibrate(longArrayOf(0, 60, 180, 90))

    fun release() { stopAmbient(); runCatching { pool.release() } }
}

@Composable
fun rememberObAudio(): ObAudio {
    val ctx = LocalContext.current
    val audio = remember { ObAudio(ctx) }
    DisposableEffect(Unit) { onDispose { audio.release() } }
    return audio
}

/* ===================================== Particules ===================================== */

enum class ParticleMode { BURST, RAIN, FLOAT, CONVERGE }

private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val maxLife: Float, val color: Color, val size: Float)

/**
 * Champ de particules temps réel. [emitting] contrôle l'émission ; [intensity] 0..1 règle le débit.
 * BURST : du centre vers l'extérieur · RAIN : pluie depuis le haut · FLOAT : flottement ascendant · CONVERGE : vers le centre.
 */
@Composable
fun ParticleField(
    modifier: Modifier = Modifier,
    colors: List<Color>,
    mode: ParticleMode = ParticleMode.FLOAT,
    emitting: Boolean = true,
    intensity: Float = 1f,
    center: Offset? = null
) {
    val particles = remember { mutableListOf<Particle>() }
    var frame by remember { mutableIntStateOf(0) }
    var sizePx by remember { mutableStateOf(Offset(1f, 1f)) }
    LaunchedEffect(mode, emitting, intensity) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                val w = sizePx.x; val h = sizePx.y
                val c = center ?: Offset(w / 2, h / 2)
                if (emitting && w > 1f) {
                    val count = (intensity * when (mode) { ParticleMode.BURST -> 10; ParticleMode.RAIN -> 6; ParticleMode.FLOAT -> 2; ParticleMode.CONVERGE -> 6 }).toInt().coerceAtLeast(if (Random.nextFloat() < intensity) 1 else 0)
                    repeat(count) {
                        val col = colors[Random.nextInt(colors.size)]
                        particles += when (mode) {
                            ParticleMode.BURST -> { val a = Random.nextFloat() * 2 * PI.toFloat(); val sp = 80f + Random.nextFloat() * 320f; Particle(c.x, c.y, cos(a) * sp, sin(a) * sp, 0f, 0.8f + Random.nextFloat() * 1.2f, col, 2f + Random.nextFloat() * 4f) }
                            ParticleMode.RAIN -> Particle(Random.nextFloat() * w, -10f, (Random.nextFloat() - 0.5f) * 40f, 150f + Random.nextFloat() * 250f, 0f, 1.5f + Random.nextFloat() * 1.5f, col, 2f + Random.nextFloat() * 3f)
                            ParticleMode.FLOAT -> Particle(Random.nextFloat() * w, h + 10f, (Random.nextFloat() - 0.5f) * 30f, -(30f + Random.nextFloat() * 60f), 0f, 3f + Random.nextFloat() * 3f, col, 1.5f + Random.nextFloat() * 3f)
                            ParticleMode.CONVERGE -> { val a = Random.nextFloat() * 2 * PI.toFloat(); val r = maxOf(w, h) * 0.7f; val x = c.x + cos(a) * r; val y = c.y + sin(a) * r; Particle(x, y, (c.x - x) / 1.2f, (c.y - y) / 1.2f, 0f, 1.2f, col, 2f + Random.nextFloat() * 3f) }
                        }
                    }
                }
                val iter = particles.iterator()
                while (iter.hasNext()) {
                    val p = iter.next()
                    p.life += dt; p.x += p.vx * dt; p.y += p.vy * dt
                    if (mode == ParticleMode.RAIN) p.vy += 120f * dt
                    if (mode == ParticleMode.BURST) { p.vx *= 0.985f; p.vy *= 0.985f }
                    if (p.life >= p.maxLife || p.y > h + 20 || p.y < -40 || p.x < -20 || p.x > w + 20) iter.remove()
                }
                if (particles.size > 900) repeat(particles.size - 900) { particles.removeAt(0) }
                frame++
            }
        }
    }
    Canvas(modifier) {
        if (sizePx.x != size.width || sizePx.y != size.height) sizePx = Offset(size.width, size.height)
        @Suppress("UNUSED_EXPRESSION") frame
        particles.forEach { p ->
            val t = (p.life / p.maxLife).coerceIn(0f, 1f)
            val alpha = if (t < 0.15f) t / 0.15f else 1f - (t - 0.15f) / 0.85f
            drawCircle(p.color.copy(alpha = alpha.coerceIn(0f, 1f) * 0.9f), radius = p.size * (1f - t * 0.4f), center = Offset(p.x, p.y))
        }
    }
}

/* ===================================== Bordure animée qui tourne ===================================== */

/** Bordure dégradée en rotation continue (sweep gradient tourné via la matrice locale du shader). */
fun Modifier.rotatingBorder(colors: List<Color>, width: Dp = 2.dp, cornerRadius: Dp = 16.dp, periodMs: Int = 2400): Modifier = composed {
    val angle by rememberInfiniteTransition(label = "rb").animateFloat(0f, 360f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "rba")
    drawBehind { drawRotatingBorder(colors, angle, width.toPx(), cornerRadius.toPx()) }
}

fun DrawScope.drawRotatingBorder(colors: List<Color>, angle: Float, strokePx: Float, radiusPx: Float) {
    val cx = size.width / 2; val cy = size.height / 2
    val shader = android.graphics.SweepGradient(cx, cy, (colors + colors.first()).map { it.toArgb() }.toIntArray(), null)
    shader.setLocalMatrix(Matrix().apply { setRotate(angle, cx, cy) })
    val paint = android.graphics.Paint().apply { isAntiAlias = true; style = android.graphics.Paint.Style.STROKE; strokeWidth = strokePx; this.shader = shader }
    val half = strokePx / 2
    drawContext.canvas.nativeCanvas.drawRoundRect(half, half, size.width - half, size.height - half, radiusPx, radiusPx, paint)
}

/** Halo pulsant derrière un composant. */
fun Modifier.pulsingGlow(color: Color, min: Float = 0.25f, max: Float = 0.7f, periodMs: Int = 1400, radiusDp: Dp = 24.dp): Modifier = composed {
    val a by rememberInfiniteTransition(label = "pg").animateFloat(min, max, infiniteRepeatable(tween(periodMs), androidx.compose.animation.core.RepeatMode.Reverse), label = "pga")
    drawBehind {
        val r = radiusDp.toPx()
        drawRoundRect(
            androidx.compose.ui.graphics.Brush.radialGradient(listOf(color.copy(alpha = a), Color.Transparent), center = Offset(size.width / 2, size.height / 2), radius = maxOf(size.width, size.height) / 1.4f + r),
            topLeft = Offset(-r, -r), size = androidx.compose.ui.geometry.Size(size.width + 2 * r, size.height + 2 * r)
        )
    }
}

/* ===================================== Éléments communs ===================================== */

/** « Étape X sur 4 » + dots ● ○ ○ ○ animés. */
@Composable
fun StepIndicator(step: Int, accent: Color, textColor: Color) {
    val pulse by rememberInfiniteTransition(label = "si").animateFloat(0.8f, 1.2f, infiniteRepeatable(tween(900), androidx.compose.animation.core.RepeatMode.Reverse), label = "sia")
    Column(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Étape $step sur 4", color = textColor.copy(alpha = 0.8f), fontFamily = ObFonts.rajdhani, letterSpacing = 3.sp, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            (1..4).forEach { i ->
                val active = i == step
                Box(
                    Modifier.size(if (active) (8 * pulse).dp else 7.dp).clip(RoundedCornerShape(50))
                        .drawBehind { if (active) drawCircle(accent) else drawCircle(textColor.copy(alpha = 0.35f), style = Stroke(1.5.dp.toPx())) }
                )
            }
        }
    }
}

/** Texte qui s'écrit lettre par lettre. */
@Composable
fun TypewriterText(text: String, charDelayMs: Long = 45, modifier: Modifier = Modifier, style: @Composable (String) -> Unit) {
    var shown by remember(text) { mutableIntStateOf(0) }
    LaunchedEffect(text) { for (i in 1..text.length) { kotlinx.coroutines.delay(charDelayMs); shown = i } }
    Box(modifier) { style(text.take(shown) + if (shown < text.length) "▏" else "") }
}

/** Bouton capsule onboarding : bordure animée + glow + texte Rajdhani majuscules. */
@Composable
fun ObButton(text: String, colors: List<Color>, enabled: Boolean = true, big: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scale by rememberInfiniteTransition(label = "ob").animateFloat(1f, if (big) 1.03f else 1.01f, infiniteRepeatable(tween(1100), androidx.compose.animation.core.RepeatMode.Reverse), label = "oba")
    Box(
        modifier
            .then(if (enabled) Modifier.scale(scale).pulsingGlow(colors.first(), radiusDp = if (big) 30.dp else 16.dp) else Modifier)
            .clip(RoundedCornerShape(50))
            .then(if (enabled) Modifier.rotatingBorder(colors, width = if (big) 3.dp else 2.dp, cornerRadius = 50.dp) else Modifier.drawBehind { drawRoundRect(Color.Gray.copy(alpha = 0.4f), style = Stroke(2.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(100f)) })
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = if (big) 36.dp else 26.dp, vertical = if (big) 18.dp else 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text.uppercase(), color = if (enabled) Color.White else Color.Gray, fontFamily = ObFonts.rajdhani, fontWeight = FontWeight.Bold, letterSpacing = 3.sp, fontSize = if (big) 20.sp else 15.sp)
    }
}

/** Style helper : texte poétique Cormorant italique. */
@Composable
fun PoeticText(text: String, color: Color, size: Int = 16, modifier: Modifier = Modifier, align: androidx.compose.ui.text.style.TextAlign = androidx.compose.ui.text.style.TextAlign.Center) {
    Text(text, color = color, fontFamily = ObFonts.cormorant, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Light, fontSize = size.sp, textAlign = align, modifier = modifier, lineHeight = (size * 1.4).sp)
}

/** Titre Cinzel : serif gras, espacement large. */
@Composable
fun CinzelTitle(text: String, color: Color, size: Int = 26, modifier: Modifier = Modifier, letterSpacing: Int = 4) {
    Text(text, color = color, fontFamily = ObFonts.cinzel, fontWeight = FontWeight.Bold, fontSize = size.sp, letterSpacing = letterSpacing.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = modifier, lineHeight = (size * 1.25).sp)
}
