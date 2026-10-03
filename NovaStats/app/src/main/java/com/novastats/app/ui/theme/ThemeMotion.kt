package com.novastats.app.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.random.Random

/* ===================================== Transitions par thème ===================================== */

/** Rebond (Pink Y2K). */
val EaseOutBounce: Easing = Easing { x ->
    val n1 = 7.5625f; val d1 = 2.75f
    when {
        x < 1f / d1 -> n1 * x * x
        x < 2f / d1 -> { val t = x - 1.5f / d1; n1 * t * t + 0.75f }
        x < 2.5f / d1 -> { val t = x - 2.25f / d1; n1 * t * t + 0.9375f }
        else -> { val t = x - 2.625f / d1; n1 * t * t + 0.984375f }
    }
}
/** Léger dépassement élégant (Slay Queen). */
val EaseInOutBack: Easing = CubicBezierEasing(0.68f, -0.55f, 0.27f, 1.55f)
/** Rapide et sec (Pink Venom / Pop Revolution). */
val EaseOutExpo: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

object NovaMotion {
    fun easing(theme: NovaTheme): Easing = when (theme.easing) {
        MotionEasing.EASE_IN_OUT -> FastOutSlowInEasing
        MotionEasing.EASE_OUT -> LinearOutSlowInEasing
        MotionEasing.CUT -> LinearEasing
        MotionEasing.EASE_IN_OUT_BACK -> EaseInOutBack
        MotionEasing.EASE_OUT_BOUNCE -> EaseOutBounce
        MotionEasing.EASE_OUT_EXPO -> EaseOutExpo
        MotionEasing.RANDOM -> if (Random.nextBoolean()) LinearEasing else FastOutSlowInEasing
    }

    /** Durée effective (Chaos Born : 100-400 ms imprévisible). */
    fun duration(theme: NovaTheme): Int = if (theme.easing == MotionEasing.RANDOM) Random.nextInt(100, 401) else theme.transitionMs

    /** Spec générique pour animate*AsState / Animatable ; Villain Era = snap (cut sec). */
    fun <T> spec(theme: NovaTheme): FiniteAnimationSpec<T> =
        if (theme.easing == MotionEasing.CUT) snap() else tween(duration(theme), easing = easing(theme))

    /** Transition d'entrée d'un onglet. */
    fun enter(theme: NovaTheme): EnterTransition = when (theme.signature) {
        Signature.JUMP_CUT -> EnterTransition.None
        Signature.CLOUD_FADE -> fadeIn(tween(theme.transitionMs, easing = LinearOutSlowInEasing))
        Signature.CURTAIN -> fadeIn(tween(theme.transitionMs / 2, delayMillis = theme.transitionMs / 3))
        Signature.RISING_BUBBLES -> fadeIn(tween(theme.transitionMs)) + scaleIn(tween(theme.transitionMs, easing = EaseOutBounce), initialScale = 0.9f)
        Signature.GOLD_SHIMMER -> fadeIn(tween(theme.transitionMs)) + scaleIn(tween(theme.transitionMs, easing = EaseInOutBack), initialScale = 0.96f)
        Signature.BW_FLASH, Signature.BLUE_FLASH -> fadeIn(tween(theme.transitionMs, easing = EaseOutExpo)) + slideInHorizontally(tween(theme.transitionMs, easing = EaseOutExpo)) { it / 12 }
        Signature.RANDOM_GLITCH -> fadeIn(snap(Random.nextInt(0, 120)))
        else -> fadeIn(tween(duration(theme), easing = easing(theme))) + scaleIn(tween(duration(theme), easing = easing(theme)), initialScale = 0.97f)
    }

    /** Transition de sortie d'un onglet. */
    fun exit(theme: NovaTheme): ExitTransition = when (theme.signature) {
        Signature.JUMP_CUT, Signature.RANDOM_GLITCH -> ExitTransition.None
        Signature.CLOUD_FADE -> fadeOut(tween(theme.transitionMs / 2))
        Signature.BW_FLASH, Signature.BLUE_FLASH -> fadeOut(tween(theme.transitionMs / 2)) + slideOutHorizontally(tween(theme.transitionMs)) { -it / 12 }
        Signature.CURTAIN -> fadeOut(tween(theme.transitionMs / 4))
        else -> fadeOut(tween(duration(theme) / 2)) + scaleOut(tween(duration(theme) / 2), targetScale = 0.98f)
    }
}

/* ===================================== Événements d'effets signature ===================================== */

/**
 * Bus d'événements UI minimal : les effets signature se déclenchent sur ces compteurs
 * (changement d'onglet, tap, déblocage de palier) plutôt qu'en continu — performance.
 */
object ThemeEvents {
    var tabTick by mutableIntStateOf(0); private set
    var tapTick by mutableIntStateOf(0); private set
    var unlockTick by mutableIntStateOf(0); private set
    /** Bad Angel : phase claire (true) / sombre (false), bascule à chaque onglet. */
    var dualPhase by mutableStateOf(false); private set

    fun tabChanged() { tabTick++; dualPhase = !dualPhase }
    fun tapped() { tapTick++ }
    /** Déblocage d'un palier (certification, statut Panthéon, award révélé…). */
    fun unlocked() { unlockTick++ }
}

/* ===================================== Arc-en-ciel rotatif ===================================== */

/** Hue-rotate continu : liste de couleurs pride décalée en continu. */
@Composable
fun rememberRainbowBrush(): Brush {
    val shift by rememberInfiniteTransition(label = "rainbow").animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart), label = "rs")
    return Brush.linearGradient(rainbowColors(shift))
}

/** Palette arc-en-ciel décalée de [shift] ∈ [0,1[ (interpolation circulaire). */
fun rainbowColors(shift: Float, steps: Int = 12): List<Color> {
    val base = NovaColors.PrideCycle
    val n = base.size
    return List(steps) { i ->
        val pos = ((i.toFloat() / (steps - 1)) + shift) * n
        val a = pos.toInt() % n
        val b = (a + 1) % n
        lerp(base[a], base[b], pos - pos.toInt())
    }
}
