package com.novastats.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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
    val theme = NovaThemes.byId(themeId)

    BackHandler(enabled = page in 2..3) { page-- }

    NovaStatsTheme(theme = theme) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                if (targetState > initialState) (slideInHorizontally(tween(450)) { it } + fadeIn(tween(300))) togetherWith (slideOutHorizontally(tween(350)) { -it / 3 } + fadeOut(tween(250)))
                else (slideInHorizontally(tween(350)) { -it } + fadeIn(tween(300))) togetherWith (slideOutHorizontally(tween(300)) { it / 3 } + fadeOut(tween(200)))
            }, label = "onboarding"
        ) { p ->
            when (p) {
                0 -> WelcomeScreen(audio) { audio.stopAmbient(); page = 1 }
                1 -> ThemeStepScreen(audio, theme, onThemeSelected = { t -> themeId = t.id; scope.launch { app.settings.setTheme(t.id) } }) { t -> themeId = t.id; scope.launch { app.settings.setTheme(t.id) }; page = 2 }
                2 -> PermissionsStepScreen(audio, theme, onBack = { page = 1 }) { result -> perms = result; page = 3 }
                3 -> GuideStepScreen(audio, theme, onBack = { page = 2 }) { completed -> guideDone = completed; perms = PermissionChecks.all(app); page = 4 }
                else -> FinalStepScreen(audio, theme, perms, guideDone) { scope.launch { app.settings.setFirstLaunchDone() }; onFinished() }
            }
        }
    }
}
