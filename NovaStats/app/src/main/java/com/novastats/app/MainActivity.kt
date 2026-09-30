package com.novastats.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novastats.app.ui.navigation.NovaApp
import com.novastats.app.ui.theme.NovaStatsTheme
import com.novastats.app.ui.theme.NovaThemes

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as NovaStatsApp
        setContent {
            val themeId by app.settings.themeId.collectAsStateWithLifecycle(initialValue = NovaThemes.DEFAULT.id)
            NovaStatsTheme(theme = NovaThemes.byId(themeId)) {
                NovaApp()
            }
        }
    }
}
