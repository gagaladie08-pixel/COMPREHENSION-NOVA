package com.novastats.app.ui.navigation

import android.content.Context
import android.content.Intent
import com.novastats.app.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Navigation différée déclenchée hors Compose (notification « 🟡 À vérifier », raccourci Paramètres…).
 * MainActivity dépose la cible, NovaApp ouvre l'onglet Réglages, SettingsScreen ouvre l'Éditeur sur ⚠️ À corriger.
 */
object PendingNav {
    const val EXTRA_OPEN = "nova_open"
    const val EXTRA_TRACK = "nova_track"
    const val TARGET_REVIEW = "review"
    const val TARGET_TAB = "tab"
    const val EXTRA_TAB = "nova_tab"

    data class Target(val name: String, val trackId: Long? = null, val tabRoute: String? = null, val stamp: Long = System.currentTimeMillis())

    val target = MutableStateFlow<Target?>(null)

    fun handle(intent: Intent?) {
        val name = intent?.getStringExtra(EXTRA_OPEN) ?: return
        val trackId = intent.getLongExtra(EXTRA_TRACK, -1L).takeIf { it > 0 }
        val tabRoute = if (name == TARGET_TAB) intent.getStringExtra(EXTRA_TAB) else null
        target.value = Target(name, trackId, tabRoute)
        intent.removeExtra(EXTRA_OPEN)
        intent.removeExtra(EXTRA_TRACK)
        intent.removeExtra(EXTRA_TAB)
    }

    fun tabIntent(context: Context, route: String): Intent = Intent(context, MainActivity::class.java).apply {
        putExtra(EXTRA_OPEN, TARGET_TAB)
        putExtra(EXTRA_TAB, route)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }

    fun consume() { target.value = null }
}
