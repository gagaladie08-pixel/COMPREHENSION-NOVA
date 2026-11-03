package com.novastats.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novastats.app.NovaStatsApp
import com.novastats.app.ui.theme.NovaStatsTheme
import com.novastats.app.ui.theme.NovaThemes
import kotlinx.coroutines.launch

/**
 * Orchestration : Bienvenue → 1 Thème → 2 Permissions → 3 Guide constructeur → 4 Grand Final.
 * Retour arrière autorisé étapes 1-3, bloqué à l'étape 4. Jamais ré-affiché une fois [onFinished] appelé.
 */
@Composable
fun OnboardingFlow(onFinished: () -> Unit) {
    val app = LocalContext.current.applicationContext as NovaStatsApp
    val scope = rememberCoroutineScope()
    val audio = rememberObAudio()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var themeId by rememberSaveable { mutableStateOf(NovaThemes.DEFAULT.id) }
    var perms by remember { mutableStateOf(Permissions(false, false, false)) }
    var guideDone by rememberSaveable { mutableStateOf(false) }
    val reducedMotion by app.settings.onboardingReducedMotion.collectAsStateWithLifecycle(initialValue = false)
    val soundEffects by app.settings.onboardingSoundEffects.collectAsStateWithLifecycle(initialValue = true)
    val haptics by app.settings.haptics.collectAsStateWithLifecycle(initialValue = true)
    val effects = OnboardingEffects(reducedMotion, soundEffects, haptics)
    val theme = NovaThemes.byId(themeId)
    val updateEffects: (OnboardingEffects) -> Unit = { next ->
        if (next.reducedMotion != reducedMotion) scope.launch { app.settings.setOnboardingReducedMotion(next.reducedMotion) }
        if (next.soundEffects != soundEffects) scope.launch { app.settings.setOnboardingSoundEffects(next.soundEffects) }
        if (next.haptics != haptics) scope.launch { app.settings.setHaptics(next.haptics) }
    }

    SideEffect {
        audio.setSoundEnabled(soundEffects)
        audio.setHapticsEnabled(haptics)
    }
    BackHandler(enabled = page in 2..3) { page-- }

    NovaStatsTheme(theme = theme) {
        CompositionLocalProvider(LocalOnboardingReducedMotion provides reducedMotion) {
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    if (reducedMotion) fadeIn(tween(1)) togetherWith fadeOut(tween(1))
                    else {
                        val forward = targetState > initialState
                        (fadeIn(tween(380, delayMillis = 80, easing = ObEasing)) +
                            slideInVertically(tween(450, delayMillis = 80, easing = ObEasing)) { if (forward) it / 14 else -it / 14 }) togetherWith
                            (fadeOut(tween(220, easing = ObEasing)) +
                                slideOutVertically(tween(260, easing = ObEasing)) { if (forward) -it / 20 else it / 20 })
                    }
                },
                label = "onboarding"
            ) { p ->
                when (p) {
                    0 -> WelcomeScreen(audio, effects, updateEffects) { page = 1 }
                    1 -> ThemeStepScreen(
                        audio,
                        theme,
                        onThemeSelected = { selected ->
                            themeId = selected.id
                            scope.launch { app.settings.setTheme(selected.id) }
                        }
                    ) { selected ->
                        themeId = selected.id
                        scope.launch { app.settings.setTheme(selected.id) }
                        page = 2
                    }
                    2 -> PermissionsStepScreen(audio, theme, onBack = { page = 1 }) { result ->
                        perms = result
                        page = 3
                    }
                    3 -> GuideStepScreen(audio, theme, onBack = { page = 2 }) { completed ->
                        guideDone = completed
                        perms = PermissionChecks.all(app)
                        page = 4
                    }
                    else -> FinalStepScreen(audio, theme, perms, guideDone, effects, updateEffects) {
                        scope.launch { app.settings.setFirstLaunchDone() }
                        onFinished()
                    }
                }
            }
        }
    }
}
