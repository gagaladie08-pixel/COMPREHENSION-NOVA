package com.novastats.app

import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.novastats.app.data.repository.UserImages
import kotlinx.coroutines.launch
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.novastats.app.ui.onboarding.OnboardingFlow
import com.novastats.app.ui.theme.Nova
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novastats.app.ui.navigation.NovaApp
import com.novastats.app.ui.theme.NovaStatsTheme
import com.novastats.app.ui.theme.NovaThemes

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        com.novastats.app.ui.navigation.PendingNav.handle(intent)
        handleSharedImage(intent)
    }

    /** Image partagée vers NovaStats → appliquée à l'artiste / l'album en attente (bouton 🌐). */
    private fun handleSharedImage(intent: android.content.Intent?) {
        if (intent?.action != android.content.Intent.ACTION_SEND) return
        val app = application as NovaStatsApp
        lifecycleScope.launch { UserImages.handleShare(app, intent)?.let { Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show() } }
    }

    override fun onResume() {
        super.onResume()
        // L'app est visible : on peut toujours (re)lancer le service premier plan et réveiller le listener si besoin
        runCatching { com.novastats.app.service.Watchdog.check(this, "ouverture de l'app", fromForeground = true) }
        // Retour du navigateur après 🌐 : la dernière image téléchargée devient la photo / pochette en attente
        if (UserImages.pending(this) != null) {
            val app = application as NovaStatsApp
            lifecycleScope.launch { runCatching { UserImages.checkDownloaded(app) }.getOrNull()?.let { Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show() } }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash « N animé » (Android 12+ : animated-vector ; avant : icône statique), enchaîne sur l'app / la bienvenue
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.novastats.app.ui.navigation.PendingNav.handle(intent)
        handleSharedImage(intent)
        val app = application as NovaStatsApp
        setContent {
            val themeId by app.settings.themeId.collectAsStateWithLifecycle(initialValue = NovaThemes.DEFAULT.id)
            val firstLaunchDone by app.settings.firstLaunchDone.collectAsStateWithLifecycle<Boolean?>(initialValue = null)
            var onboardingJustFinished by remember { mutableStateOf(false) }
            when {
                firstLaunchDone == null -> NovaStatsTheme(theme = NovaThemes.byId(themeId)) { Box(Modifier.fillMaxSize().background(Nova.theme.background)) }
                firstLaunchDone == false && !onboardingJustFinished -> OnboardingFlow { onboardingJustFinished = true }
                else -> NovaStatsTheme(theme = NovaThemes.byId(themeId)) { NovaApp() }
            }
        }
    }
}
