package com.novastats.app.ui.navigation

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Navigation différée déclenchée hors Compose (notification « 🟡 À vérifier », raccourci Paramètres…).
 * MainActivity dépose la cible, NovaApp ouvre l'onglet Réglages, SettingsScreen ouvre l'Éditeur sur ⚠️ À corriger.
 */
object PendingNav {
    const val EXTRA_OPEN = "nova_open"
    const val EXTRA_TRACK = "nova_track"
    const val TARGET_REVIEW = "review"

    data class Target(val name: String, val trackId: Long? = null, val stamp: Long = System.currentTimeMillis())

    val target = MutableStateFlow<Target?>(null)

    fun handle(intent: Intent?) {
        val name = intent?.getStringExtra(EXTRA_OPEN) ?: return
        val trackId = intent.getLongExtra(EXTRA_TRACK, -1L).takeIf { it > 0 }
        target.value = Target(name, trackId)
        intent.removeExtra(EXTRA_OPEN)
    }

    fun consume() { target.value = null }
}
