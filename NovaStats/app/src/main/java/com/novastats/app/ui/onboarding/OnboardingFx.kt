package com.novastats.app.ui.onboarding

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novastats.app.R
import com.novastats.app.ui.theme.NovaFonts
import kotlinx.coroutines.delay

/* =====================================================================================================
 * Kit « Cinéma » de l'onboarding : noir profond, UNE couleur d'accent, deux polices, lumière et profondeur.
 * ===================================================================================================== */

/** Palette réduite : noir + ivoire. Après le choix du thème, l'accent devient la couleur primaire du thème. */
object ObColors {
    val Black = Color(0xFF000000)
    val Ivory = Color(0xFFF3EDE2)
    val IvoryDim = Color(0xB3F3EDE2)
    val Line = Color(0x33F3EDE2)
    /* Compatibilité avec l'ancien code (Guide constructeur embarqué) : tout est ramené à l'ivoire. */
    val Gold get() = Ivory
    val Green get() = Ivory
    val Red = Color(0xFFE0564B)
    val OrbOff = Color(0xFF2A2A2A)
}

/** Deux polices : une d'affichage (Cinzel) et une de corps (Inter). Les écrans thémés utilisent la police titre du thème. */
object ObFonts {
    val display: FontFamily get() = NovaFonts.family("Cinzel")
    val body: FontFamily get() = NovaFonts.family("Inter")
    /* Compatibilité : les anciens noms pointent sur les deux polices retenues. */
    val cinzel: FontFamily get() = display
    val cormorant: FontFamily get() = body
    val raleway: FontFamily get() = body
    val rajdhani: FontFamily get() = body
}

/** Une seule courbe pour tout l'onboarding : décélération emphatique (Material « emphasized decelerate »). */
val ObEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/* ===================================== Audio + haptique (son A) ===================================== */

/** Trois sons seulement : souffle (étape), tick cristallin (succès), boom + shimmer (final). Volume 20 %. */
class ObAudio(private val context: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(3)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val whoosh = runCatching { pool.load(context, R.raw.ob_whoosh, 1) }.getOrDefault(0)
    private val tick = runCatching { pool.load(context, R.raw.ob_tick, 1) }.getOrDefault(0)
    private val boom = runCatching { pool.load(context, R.raw.ob_boom, 1) }.getOrDefault(0)
    private val vibrator: Vibrator? = runCatching { context.getSystemService(Vibrator::class.java) }.getOrNull()

    private fun playId(id: Int, volume: Float) { if (id != 0) runCatching { pool.play(id, volume, volume, 1, 0, 1f) } }
    fun whoosh(volume: Float = 0.2f) = playId(whoosh, volume)
    fun tick(volume: Float = 0.22f) = playId(tick, volume)
    fun boom(volume: Float = 0.3f) = playId(boom, volume)

    /** Compatibilité : les anciens noms sont ramenés aux trois sons. */
    fun play(name: String, volume: Float = 1f) = when (name) {
        "chime", "crystal", "chord" -> tick(0.22f * volume)
        "boom", "heartbeat" -> boom(0.3f * volume)
        else -> whoosh(0.2f * volume)
    }
    fun startAmbient() {}
    fun stopAmbient() {}

    private fun vibrate(pattern: LongArray) { runCatching { vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1)) } }
    /** Trois retours haptiques : tap, succès, impact final. */
    fun tap() = vibrate(longArrayOf(0, 18))
    fun success() = vibrate(longArrayOf(0, 40, 70, 60))
    fun impact() = vibrate(longArrayOf(0, 220))
    fun soft() = tap()
    fun doublePulse() = success()
    fun triplePulse() = success()
    fun heartbeat() = tap()

    fun release() { runCatching { pool.release() } }
}

@Composable
fun rememberObAudio(): ObAudio {
    val ctx = LocalContext.current
    val audio = remember { ObAudio(ctx) }
    DisposableEffect(Unit) { onDispose { audio.release() } }
    return audio
}

/* ===================================== Parallaxe gyroscopique ===================================== */

/** Inclinaison du téléphone (−1..1 sur x et y), lissée — pour la parallaxe des titres. */
@Composable
fun rememberTilt(): State<Offset> {
    val ctx = LocalContext.current
    val tilt = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val x = (e.values[0] / 9.81f).coerceIn(-1f, 1f); val y = (e.values[1] / 9.81f).coerceIn(-1f, 1f)
                val cur = tilt.value
                tilt.value = Offset(cur.x + (x - cur.x) * 0.08f, cur.y + ((y - 0.6f) - cur.y) * 0.08f)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm?.unregisterListener(listener) }
    }
    return tilt
}

/** Déplace le contenu selon l'inclinaison ([depth] en dp de course maximale). */
fun Modifier.parallax(tilt: State<Offset>, depth: Dp): Modifier = graphicsLayer {
    translationX = -tilt.value.x * depth.toPx()
    translationY = tilt.value.y * depth.toPx()
}

/* ===================================== Lumière ===================================== */

/** Faisceau de lumière qui balaie l'écran une fois (de gauche à droite), puis disparaît. */
@Composable
fun LightSweep(color: Color, trigger: Boolean, durationMs: Int = 1600, modifier: Modifier = Modifier) {
    val p by animateFloatAsState(if (trigger) 1f else 0f, tween(durationMs, easing = ObEasing), label = "sweep")
    if (p <= 0f || p >= 1f) return
    Canvas(modifier.fillMaxSize()) {
        val x = -size.width * 0.4f + (size.width * 1.8f) * p
        val w = size.width * 0.35f
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, color.copy(alpha = 0.18f), color.copy(alpha = 0.55f), color.copy(alpha = 0.18f), Color.Transparent), x - w, x + w), Offset(x - w, 0f), Size(w * 2, size.height))
    }
}

/** Respiration lumineuse très lente derrière un élément (le seul effet en boucle autorisé). */
fun Modifier.breathingGlow(color: Color, min: Float = 0.10f, max: Float = 0.28f, periodMs: Int = 3600, radiusDp: Dp = 80.dp): Modifier = composed {
    val a by rememberInfiniteTransition(label = "breath").animateFloat(min, max, infiniteRepeatable(tween(periodMs, easing = ObEasing), RepeatMode.Reverse), label = "ba")
    drawBehind {
        val r = radiusDp.toPx()
        drawRect(Brush.radialGradient(listOf(color.copy(alpha = a), Color.Transparent), center = Offset(size.width / 2, size.height / 2), radius = maxOf(size.width, size.height) / 1.6f + r), topLeft = Offset(-r, -r), size = Size(size.width + 2 * r, size.height + 2 * r))
    }
}

/** Compatibilité ancien nom. */
fun Modifier.pulsingGlow(color: Color, min: Float = 0.1f, max: Float = 0.28f, periodMs: Int = 3600, radiusDp: Dp = 40.dp): Modifier = breathingGlow(color, min, max, periodMs, radiusDp)

/* ===================================== Typographie animée ===================================== */

/**
 * Titre d'affichage dont les lettres apparaissent une à une, flou → net, avec un léger recul « caméra ».
 * [start] déclenche l'animation ; [perLetterMs] règle la cadence.
 */
@Composable
fun BlurInTitle(
    text: String, color: Color, fontSize: Int, modifier: Modifier = Modifier, start: Boolean = true,
    perLetterMs: Long = 90, letterSpacing: Int = 10, fontFamily: FontFamily = ObFonts.display, onDone: () -> Unit = {}
) {
    var shown by remember(text) { mutableIntStateOf(0) }
    LaunchedEffect(start, text) { if (start) { for (i in 1..text.length) { delay(perLetterMs); shown = i }; delay(300); onDone() } }
    Row(modifier, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        text.forEachIndexed { i, ch ->
            val visible = i < shown
            val a by animateFloatAsState(if (visible) 1f else 0f, tween(700, easing = ObEasing), label = "la")
            val blurDp by animateFloatAsState(if (visible) 0f else 14f, tween(900, easing = ObEasing), label = "lb")
            val sc by animateFloatAsState(if (visible) 1f else 1.25f, tween(900, easing = ObEasing), label = "ls")
            Text(
                ch.toString(), color = color, fontFamily = fontFamily, fontWeight = FontWeight.SemiBold, fontSize = fontSize.sp,
                letterSpacing = letterSpacing.sp, modifier = Modifier.alpha(a).scale(sc).blur(blurDp.dp)
            )
        }
    }
}

/** Ligne de texte qui monte doucement en fondu. */
@Composable
fun RiseIn(visible: Boolean, delayMs: Int = 0, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(visible) { if (visible) { delay(delayMs.toLong()); go = true } else go = false }
    val a by animateFloatAsState(if (go) 1f else 0f, tween(900, easing = ObEasing), label = "ra")
    val dy by animateFloatAsState(if (go) 0f else 18f, tween(1100, easing = ObEasing), label = "rd")
    Box(modifier.graphicsLayer { alpha = a; translationY = dy * density }) { content() }
}

/* ===================================== Éléments communs ===================================== */

/** Fine ligne de progression en haut (4 étapes), remplace les points. */
@Composable
fun StepIndicator(step: Int, accent: Color, textColor: Color) {
    val p by animateFloatAsState(step / 4f, tween(900, easing = ObEasing), label = "sp")
    Column(Modifier.fillMaxWidth().padding(top = 14.dp, start = 28.dp, end = 28.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(textColor.copy(alpha = 0.18f))) {
            Box(Modifier.fillMaxWidth(p).height(1.dp).background(accent))
        }
        Text("ÉTAPE $step / 4", color = textColor.copy(alpha = 0.55f), fontFamily = ObFonts.body, fontSize = 10.sp, letterSpacing = 3.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

/** Bouton principal : capsule pleine (accent) ou contour fin (ghost). Aucune animation en boucle. */
@Composable
fun ObButton(text: String, colors: List<Color>, enabled: Boolean = true, big: Boolean = false, modifier: Modifier = Modifier, ghost: Boolean = false, onClick: () -> Unit) {
    val accent = colors.first()
    val onAccent = if (accent.luminanceApprox() > 0.5f) Color.Black else Color.White
    val shape = RoundedCornerShape(50)
    var pressed by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(if (pressed) 0.97f else 1f, tween(160, easing = ObEasing), label = "bs")
    Box(
        modifier.scale(sc).clip(shape)
            .then(if (ghost || !enabled) Modifier.drawBehind { drawRoundRect(if (enabled) accent.copy(alpha = 0.8f) else accent.copy(alpha = 0.25f), style = Stroke(1.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(100f)) } else Modifier.background(accent))
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null) { pressed = true; onClick() }
            .padding(horizontal = if (big) 40.dp else 30.dp, vertical = if (big) 17.dp else 14.dp),
        contentAlignment = Alignment.Center
    ) {
        LaunchedEffect(pressed) { if (pressed) { delay(180); pressed = false } }
        Text(
            text.uppercase(), color = if (!enabled) accent.copy(alpha = 0.4f) else if (ghost) accent else onAccent,
            fontFamily = ObFonts.body, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp, fontSize = if (big) 15.sp else 13.sp
        )
    }
}

private fun Color.luminanceApprox(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/** Texte de corps, fin, interligne large. */
@Composable
fun PoeticText(text: String, color: Color, size: Int = 16, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Center) {
    Text(text, color = color, fontFamily = ObFonts.body, fontWeight = FontWeight.Light, fontSize = size.sp, textAlign = align, modifier = modifier, lineHeight = (size * 1.55).sp, letterSpacing = 0.3.sp)
}

/** Titre d'affichage : Cinzel, espacement large, graisse moyenne. */
@Composable
fun CinzelTitle(text: String, color: Color, size: Int = 26, modifier: Modifier = Modifier, letterSpacing: Int = 4) {
    Text(text, color = color, fontFamily = ObFonts.display, fontWeight = FontWeight.Medium, fontSize = size.sp, letterSpacing = letterSpacing.sp, textAlign = TextAlign.Center, modifier = modifier, lineHeight = (size * 1.3).sp)
}

/** Compatibilité : texte lettre par lettre (sans curseur). */
@Composable
fun TypewriterText(text: String, charDelayMs: Long = 45, modifier: Modifier = Modifier, style: @Composable (String) -> Unit) {
    var shown by remember(text) { mutableIntStateOf(if (charDelayMs == 0L) text.length else 0) }
    LaunchedEffect(text) { if (charDelayMs > 0L) for (i in 1..text.length) { delay(charDelayMs); shown = i } }
    Box(modifier) { style(text.take(shown)) }
}

/** Anneau d'état d'une étape de checklist : vide → se remplit → coche. */
@Composable
fun StatusRing(done: Boolean, active: Boolean, accent: Color, dim: Color, size: Dp = 26.dp) {
    val p by animateFloatAsState(if (done) 1f else 0f, tween(900, easing = ObEasing), label = "ring")
    val pulse by rememberInfiniteTransition(label = "rp").animateFloat(0.4f, 1f, infiniteRepeatable(tween(1600, easing = ObEasing), RepeatMode.Reverse), label = "rpa")
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2 - 1.dp.toPx()
        drawCircle(dim.copy(alpha = 0.35f), r, style = Stroke(1.2.dp.toPx()))
        if (active && !done) drawCircle(accent.copy(alpha = 0.9f * pulse), r, style = Stroke(1.6.dp.toPx()))
        if (p > 0f) {
            drawArc(accent, -90f, 360f * p, false, Offset(center.x - r, center.y - r), Size(r * 2, r * 2), style = Stroke(2.dp.toPx()))
            if (p >= 0.99f) {
                val s = r * 0.5f
                drawLine(accent, Offset(center.x - s * 0.9f, center.y), Offset(center.x - s * 0.2f, center.y + s * 0.7f), 2.dp.toPx())
                drawLine(accent, Offset(center.x - s * 0.2f, center.y + s * 0.7f), Offset(center.x + s, center.y - s * 0.6f), 2.dp.toPx())
            }
        }
    }
}

/** Voile noir qui se dissout (révèle ce qu'il y a dessous). */
@Composable
fun Veil(visible: Boolean, durationMs: Int = 1400, color: Color = Color.Black, modifier: Modifier = Modifier) {
    val a by animateFloatAsState(if (visible) 1f else 0f, tween(durationMs, easing = ObEasing), label = "veil")
    if (a > 0f) Box(modifier.fillMaxSize().alpha(a).background(color))
}

/** Séparateur hairline. */
@Composable
fun Hairline(color: Color, modifier: Modifier = Modifier) { Box(modifier.fillMaxWidth().height(1.dp).background(color.copy(alpha = 0.18f))) }

/** Espaceur horizontal. */
@Composable
fun HSpace(w: Dp) = Spacer(Modifier.width(w))

/** Style texte de corps réutilisable. */
val ObBodyStyle: TextStyle @Composable get() = TextStyle(fontFamily = ObFonts.body, fontSize = 14.sp, lineHeight = 21.sp)
