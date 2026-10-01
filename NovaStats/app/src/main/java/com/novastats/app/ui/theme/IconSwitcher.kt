package com.novastats.app.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * Icône dynamique du launcher — cahier des charges « Icône » : l'icône change selon le thème actif.
 *
 * 15 `activity-alias` dans le manifeste (`.icon.<themeId>`), chacun avec son adaptive icon ; un seul est activé.
 * Au changement de thème : on active d'abord le nouvel alias, puis on désactive les autres (jamais zéro alias actif).
 *
 * Limites connues : rafraîchissement immédiat sur Android stock / Pixel ; différé sur certains launchers OEM
 * (One UI, MIUI, ColorOS, HiOS…) — un retour à l'écran d'accueil suffit en général.
 */
object IconSwitcher {
    private const val TAG = "IconSwitcher"

    fun aliasFor(context: Context, themeId: String) = ComponentName(context, "${context.packageName}.icon.$themeId")

    fun apply(context: Context, themeId: String?) {
        val target = NovaThemes.byId(themeId).id
        val pm = context.packageManager
        val ids = NovaThemes.ALL.map { it.id }
        runCatching {
            val wanted = aliasFor(context, target)
            if (pm.getComponentEnabledSetting(wanted) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                pm.setComponentEnabledSetting(wanted, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            }
            ids.filter { it != target }.forEach { id ->
                val c = aliasFor(context, id)
                val state = pm.getComponentEnabledSetting(c)
                val defaultEnabled = id == NovaThemes.DEFAULT.id // seul alias activé par défaut dans le manifeste
                val enabled = state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && defaultEnabled)
                if (enabled) pm.setComponentEnabledSetting(c, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
        }.onFailure { Log.w(TAG, "Changement d'icône impossible", it) }
    }
}
