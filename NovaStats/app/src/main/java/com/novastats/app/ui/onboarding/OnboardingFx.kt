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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novastats.app.R
import com.novastats.app.ui.theme.NovaTheme
import com.novastats.app.ui.theme.ThemeAmbient
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * Kit visuel de l'onboarding — direction « keynote » : fond sombre profond, typographie Inter embarquée (aucun
 * téléchargement, donc jamais de police par défaut), titres monumentaux serrés, gris Apple pour le texte secondaire,
 * boutons pilule pleins, cartes sombres arrondies, appareil 3D qui réagit au gyroscope. Transitions courtes (≤ 500 ms).
 */

/* ===================================== Couleurs ===================================== */

object ObColors {
    val Black = Color(0xFF000000)
    val Ink = Color(0xFF0B0B0F)
    val Surface = Color(0xFF1C1C1E)
    val Surface2 = Color(0xFF2C2C2E)
    val Separator = Color(0xFF3A3A3C)
    val Text = Color(0xFFF5F5F7)
    val Gray = Color(0xFFA1A1A6)
    val Gray2 = Color(0xFF6E6E73)
    val Green = Color(0xFF30D158)
    val Red = Color(0xFFFF453A)
    val Orange = Color(0xFFFF9F0A)
    // compat
    val Ivory = Text; val IvoryDim = Gray; val Line = Separator; val Gold = Text; val OrbOff = Gray2
}

/* ===================================== Polices embarquées ===================================== */

object ObFonts {
    /** Inter variable (res/font/inter.ttf) — l'axe de graisse est piloté par FontWeight sur API 26+. */
    val inter: FontFamily = FontFamily(listOf(300, 400, 500, 600, 700, 800, 900).map { w -> Font(R.font.inter, FontWeight(w)) })
    /** Cinzel variable (res/font/cinzel.ttf) — réservé aux rares touches « sérif » (ex. monogramme). */
    val cinzel: FontFamily = FontFamily(listOf(400, 500, 600, 700, 800, 900).map { w -> Font(R.font.cinzel, FontWeight(w)) })
    val display get() = inter
    val body get() = inter
    // compat
    val cormorant get() = cinzel; val raleway get() = inter; val rajdhani get() = inter
}

/** Courbe unique : décélération emphatique. */
val ObEasing: Easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/* ===================================== Audio + haptique (son A) ===================================== */

/** Trois sons seulement : souffle (étape), tick (succès), boom + shimmer (final). Volume 20 %. */
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
    fun play(name: String, volume: Float = 1f) = when (name) {
        "chime", "crystal", "chord" -> tick(0.22f * volume)
        "boom", "heartbeat" -> boom(0.3f * volume)
        else -> whoosh(0.2f * volume)
    }
    fun startAmbient() {}
    fun stopAmbient() {}

    private fun vibrate(pattern: LongArray) { runCatching { vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1)) } }
    fun tap() = vibrate(longArrayOf(0, 14))
    fun success() = vibrate(longArrayOf(0, 35, 60, 50))
    fun impact() = vibrate(longArrayOf(0, 180))
    fun soft() = tap(); fun doublePulse() = success(); fun triplePulse() = success(); fun heartbeat() = tap()

    fun release() { runCatching { pool.release() } }
}

@Composable
fun rememberObAudio(): ObAudio {
    val ctx = LocalContext.current
    val audio = remember { ObAudio(ctx) }
    DisposableEffect(Unit) { onDispose { audio.release() } }
    return audio
}

/* ===================================== Gyroscope ===================================== */

/** Inclinaison du téléphone (−1..1), lissée. */
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
                tilt.value = Offset(cur.x + (x - cur.x) * 0.1f, cur.y + ((y - 0.6f) - cur.y) * 0.1f)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm?.unregisterListener(listener) }
    }
    return tilt
}

fun Modifier.parallax(tilt: State<Offset>, depth: Dp): Modifier = graphicsLayer {
    translationX = -tilt.value.x * depth.toPx()
    translationY = tilt.value.y * depth.toPx()
}

/* ===================================== Scène ===================================== */

/**
 * Fond de scène : noir profond → [base], halo de couleur [accent] qui dérive lentement derrière le sujet,
 * vignette, et éventuellement l'animation d'ambiance du thème ([ambient], atténuée).
 */
@Composable
fun ObStage(base: Color, accent: Color, ambient: NovaTheme? = null, ambientAlpha: Float = 0.55f, content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "stage")
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart), label = "drift")
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(base, ObColors.Black)))) {
        if (ambient != null) Box(Modifier.fillMaxSize().graphicsLayer { alpha = ambientAlpha }) { ThemeAmbient(ambient, Modifier.fillMaxSize()) }
        Canvas(Modifier.fillMaxSize()) {
            val a = drift * 2f * Math.PI.toFloat()
            val c = Offset(size.width * (0.5f + 0.18f * cos(a)), size.height * (0.42f + 0.08f * sin(a * 1.3f)))
            val r = size.width * 0.75f
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.34f), accent.copy(alpha = 0.10f), Color.Transparent), c, r), r, c)
            // vignette
            drawRect(Brush.radialGradient(listOf(Color.Transparent, ObColors.Black.copy(alpha = 0.55f)), Offset(size.width / 2, size.height / 2), size.height * 0.85f))
        }
        content()
    }
}

/**
 * Mise en page commune : points d'étape, zone de contenu, puis zone basse FIXE pour les boutons
 * (jamais de bouton perdu au milieu d'un scroll).
 */
@Composable
fun ObLayout(step: Int?, accent: Color, bottom: @Composable ColumnScope.() -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) { if (step != null) StepDots(step, accent) }
        Column(Modifier.weight(1f).fillMaxWidth(), content = content)
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, content = bottom)
    }
}

@Composable
fun StepDots(step: Int, accent: Color, total: Int = 4) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..total) {
            val w by animateFloatAsState(if (i == step) 22f else 6f, tween(400, easing = ObEasing), label = "dot")
            Box(Modifier.height(6.dp).width(w.dp).clip(CircleShape).background(if (i <= step) accent else ObColors.Gray2.copy(alpha = 0.5f)))
        }
    }
}

/** Compat : ancien indicateur textuel. */
@Composable fun StepIndicator(step: Int, accent: Color, textColor: Color) = Box(Modifier.fillMaxWidth().statusBarsPadding().height(44.dp), contentAlignment = Alignment.Center) { StepDots(step, accent) }

/* ===================================== Typographie ===================================== */

@Composable
fun ObHeadline(text: String, modifier: Modifier = Modifier, size: Int = 40, color: Color = ObColors.Text, align: TextAlign = TextAlign.Start) {
    Text(text, color = color, fontFamily = ObFonts.inter, fontWeight = FontWeight.ExtraBold, fontSize = size.sp, lineHeight = (size * 1.06f).sp, letterSpacing = (-size * 0.028f).sp, textAlign = align, modifier = modifier)
}

@Composable
fun ObSub(text: String, modifier: Modifier = Modifier, color: Color = ObColors.Gray, size: Int = 17, align: TextAlign = TextAlign.Start) {
    Text(text, color = color, fontFamily = ObFonts.inter, fontWeight = FontWeight.Normal, fontSize = size.sp, lineHeight = (size * 1.4f).sp, letterSpacing = (-0.2).sp, textAlign = align, modifier = modifier)
}

@Composable
fun ObEyebrow(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(text.uppercase(), color = color, fontFamily = ObFonts.inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 1.6.sp, modifier = modifier)
}

// compat
@Composable fun PoeticText(text: String, color: Color, size: Int = 16, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Center) = ObSub(text, modifier, color, size, align)
@Composable fun CinzelTitle(text: String, color: Color, size: Int = 26, modifier: Modifier = Modifier, letterSpacing: Int = 4) = ObHeadline(text, modifier, size, color, TextAlign.Center)
val ObBodyStyle: TextStyle @Composable get() = TextStyle(fontFamily = ObFonts.inter, fontSize = 15.sp, lineHeight = 21.sp)

/* ===================================== Boutons ===================================== */

/** Pilule pleine, 54 dp, texte semi-gras. [fill] blanc par défaut (keynote), ou couleur du thème. */
@Composable
fun ObPrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, fill: Color = ObColors.Text, onClick: () -> Unit) {
    val a by animateFloatAsState(if (enabled) 1f else 0.35f, tween(300), label = "btn")
    val onFill = if (fill.luminanceApprox() > 0.5f) ObColors.Black else ObColors.Text
    var pressed by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (pressed) 0.97f else 1f, tween(120), label = "press")
    LaunchedEffect(pressed) { if (pressed) { delay(140); pressed = false } }
    Box(
        modifier.fillMaxWidth().height(54.dp).graphicsLayer { alpha = a; scaleX = s; scaleY = s }.clip(CircleShape).background(fill)
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null) { pressed = true; onClick() },
        contentAlignment = Alignment.Center
    ) { Text(text, color = onFill, fontFamily = ObFonts.inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, letterSpacing = (-0.2).sp) }
}

/** Bouton texte (lien) — « Retour », « Plus tard ». */
@Composable
fun ObLinkButton(text: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.height(44.dp).clip(CircleShape).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Text(text, color = color, fontFamily = ObFonts.inter, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    }
}

/** Petite pilule d'action dans une ligne (« Activer », « Ouvrir »). */
@Composable
fun ObMiniButton(text: String, color: Color, onClick: () -> Unit) {
    val onFill = if (color.luminanceApprox() > 0.5f) ObColors.Black else ObColors.Text
    Box(Modifier.height(32.dp).clip(CircleShape).background(color).clickable(onClick = onClick).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
        Text(text, color = onFill, fontFamily = ObFonts.inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

// compat
@Composable fun ObButton(text: String, colors: List<Color>, enabled: Boolean = true, big: Boolean = false, modifier: Modifier = Modifier, ghost: Boolean = false, onClick: () -> Unit) =
    if (ghost) ObLinkButton(text, colors.firstOrNull() ?: ObColors.Text, modifier, onClick) else ObPrimaryButton(text, modifier, enabled, colors.firstOrNull() ?: ObColors.Text, onClick)

fun Color.luminanceApprox(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/* ===================================== Cartes & lignes ===================================== */

@Composable
fun ObCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(ObColors.Surface.copy(alpha = 0.92f)).border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(22.dp)), content = content)
}

@Composable
fun ObDivider() = Box(Modifier.fillMaxWidth().padding(start = 68.dp).height(0.6.dp).background(ObColors.Separator))

/** Ligne « réglage » : icône dans un carré coloré, titre + sous-titre, élément de droite. */
@Composable
fun ObRow(icon: ImageVector, iconBg: Color, title: String, subtitle: String?, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(iconBg), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (iconBg.luminanceApprox() > 0.5f) ObColors.Black else ObColors.Text, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = ObColors.Text, fontFamily = ObFonts.inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = (-0.2).sp)
            if (subtitle != null) Text(subtitle, color = ObColors.Gray, fontFamily = ObFonts.inter, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

/** Coche verte (fait) ou pastille d'état. */
@Composable
fun ObCheck(done: Boolean, size: Dp = 26.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(if (done) ObColors.Green else ObColors.Surface2).border(1.dp, if (done) ObColors.Green else ObColors.Gray2, CircleShape), contentAlignment = Alignment.Center) {
        if (done) Icon(Icons.Rounded.Check, null, tint = ObColors.Black, modifier = Modifier.size(size * 0.62f))
    }
}

@Composable
fun ObTag(text: String, color: Color) {
    Text(text.uppercase(), color = color, fontFamily = ObFonts.inter, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.2.sp,
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 4.dp))
}

/* ===================================== Apparition ===================================== */

/** Apparition « keynote » : fondu + montée de 18 dp, 450 ms, décalage optionnel. */
@Composable
fun Appear(visible: Boolean, delayMs: Int = 0, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(visible, modifier, enter = fadeIn(tween(450, delayMs, ObEasing)) + slideInVertically(tween(550, delayMs, ObEasing)) { (it * 0.35f).toInt() }, exit = fadeOut(tween(200))) { content() }
}

// compat
@Composable fun RiseIn(visible: Boolean, delayMs: Int = 0, modifier: Modifier = Modifier, content: @Composable () -> Unit) = Appear(visible, delayMs, modifier, content)

/** Séquenceur : renvoie le nombre de « temps » écoulés (0..n) avec [stepMs] entre chaque, après [startMs]. */
@Composable
fun rememberBeats(n: Int, startMs: Int = 250, stepMs: Int = 160): Int {
    var beats by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { delay(startMs.toLong()); for (i in 1..n) { beats = i; delay(stepMs.toLong()) } }
    return beats
}

/* ===================================== Appareil 3D ===================================== */

/**
 * Smartphone stylisé affichant un aperçu vivant de l'app dans le thème [theme] (barre de marque, bannière,
 * classement, barre d'onglets, fond animé réel du thème). Rotation 3D par [rotY]/[rotX] + gyroscope.
 */
@Composable
fun DeviceMockup(theme: NovaTheme, modifier: Modifier = Modifier, rotY: Float = 0f, rotX: Float = 0f, tilt: State<Offset>? = null, ambient: Boolean = true, glow: Boolean = true) {
    val density = LocalDensity.current.density
    Box(
        modifier.aspectRatio(0.49f).graphicsLayer {
            cameraDistance = 16f * density
            rotationY = rotY + (tilt?.value?.x ?: 0f) * 9f
            rotationX = rotX - (tilt?.value?.y ?: 0f) * 6f
        }
    ) {
        if (glow) Box(Modifier.fillMaxSize().padding(6.dp).graphicsLayer { shadowElevation = 60f; shape = RoundedCornerShape(38.dp); clip = true; ambientShadowColor = theme.primary; spotShadowColor = theme.primary })
        // Châssis
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(38.dp)).background(Color(0xFF151518)).border(1.5.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(38.dp)).padding(7.dp)) {
            // Écran
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(31.dp)).background(theme.background)) {
                if (ambient) Box(Modifier.fillMaxSize().graphicsLayer { alpha = 0.8f }) { ThemeAmbient(theme, Modifier.fillMaxSize()) }
                Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    Spacer(Modifier.height(10.dp))
                    // Barre d'état + encoche
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(Modifier.width(44.dp).height(10.dp).clip(CircleShape).background(Color.Black))
                        Text("9:41", color = theme.text, fontFamily = ObFonts.inter, fontWeight = FontWeight.SemiBold, fontSize = 8.sp, modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    // Barre de marque
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("NOVASTATS", color = theme.primary, fontFamily = ObFonts.inter, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.size(14.dp).clip(CircleShape).background(theme.accent.copy(alpha = 0.6f)))
                    }
                    Spacer(Modifier.height(10.dp))
                    // Bannière
                    Box(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(theme.primary, theme.secondary)))) {
                        Column(Modifier.padding(10.dp)) {
                            Box(Modifier.width(60.dp).height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)))
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.width(96.dp).height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.5f)))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    // Onglets
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(28, 20, 24, 22).forEachIndexed { i, w ->
                            Box(Modifier.width(w.dp).height(10.dp).clip(CircleShape).background(if (i == 0) theme.primary else theme.surface))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    // Classement
                    listOf(0.9f, 0.7f, 0.8f, 0.55f, 0.65f).forEachIndexed { i, w ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", color = if (i == 0) theme.primary else theme.textSecondary, fontFamily = ObFonts.inter, fontWeight = FontWeight.Bold, fontSize = 9.sp, modifier = Modifier.width(12.dp))
                            Box(Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).background(Brush.linearGradient(listOf(listOf(theme.primary, theme.secondary, theme.glowSecondary, theme.accent, theme.secondary)[i], theme.surface))))
                            Spacer(Modifier.width(7.dp))
                            Column(Modifier.weight(1f)) {
                                Box(Modifier.fillMaxWidth(w).height(5.dp).clip(CircleShape).background(theme.text.copy(alpha = 0.85f)))
                                Spacer(Modifier.height(4.dp))
                                Box(Modifier.fillMaxWidth(w * 0.6f).height(4.dp).clip(CircleShape).background(theme.textSecondary.copy(alpha = 0.6f)))
                            }
                            Box(Modifier.width(16.dp).height(5.dp).clip(CircleShape).background(theme.accent.copy(alpha = 0.7f)))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    // Barre de navigation
                    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        repeat(5) { i -> Box(Modifier.size(if (i == 0) 8.dp else 6.dp).clip(CircleShape).background(if (i == 0) theme.primary else theme.textSecondary.copy(alpha = 0.4f))) }
                    }
                }
            }
        }
    }
}

/* ===================================== Divers ===================================== */

/** Rangée de pastilles de couleur (palette du thème). */
@Composable
fun Swatches(theme: NovaTheme, size: Dp = 14.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(theme.primary, theme.secondary, theme.glowSecondary, theme.accent).forEach { c ->
            Box(Modifier.size(size).clip(CircleShape).background(c).border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape))
        }
    }
}

@Composable
fun Hairline(color: Color, modifier: Modifier = Modifier) { Box(modifier.fillMaxWidth().height(1.dp).background(color.copy(alpha = 0.18f))) }

@Composable
fun HSpace(w: Dp) = Spacer(Modifier.width(w))

/** Voile plein écran (fondu). */
@Composable
fun Veil(visible: Boolean, durationMs: Int = 600, color: Color = Color.Black, modifier: Modifier = Modifier) {
    val a by animateFloatAsState(if (visible) 1f else 0f, tween(durationMs, easing = ObEasing), label = "veil")
    if (a > 0f) Box(modifier.fillMaxSize().graphicsLayer { alpha = a }.background(color))
}

fun Float.absClamp(max: Float) = abs(this).coerceAtMost(max)

