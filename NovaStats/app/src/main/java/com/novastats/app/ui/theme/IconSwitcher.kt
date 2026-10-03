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

    /** Thème dont l'icône doit être appliquée au prochain passage en arrière-plan (null = rien à faire). */
    @Volatile private var pending: String? = null
    @Volatile private var started = 0
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val applyRunnable = Runnable { pending?.let { id -> if (started == 0) { apply(appRef, id); pending = null } } }
    private lateinit var appRef: android.app.Application

    /**
     * Branche le report du changement d'icône : désactiver l'activity-alias qui a lancé la tâche en cours fait
     * fermer l'app (Android retire la tâche, HiOS en particulier). On n'applique donc la bascule que lorsque
     * toutes les activités sont arrêtées, avec 1,5 s de délai.
     */
    fun install(app: android.app.Application) {
        appRef = app
        app.registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: android.app.Activity) { started++; handler.removeCallbacks(applyRunnable) }
            override fun onActivityStopped(activity: android.app.Activity) { started = (started - 1).coerceAtLeast(0); if (started == 0 && pending != null) handler.postDelayed(applyRunnable, 1500) }
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })
    }

    /** Demande la bascule vers l'icône de [themeId] : immédiate si aucune activité n'est visible, sinon reportée. */
    fun request(themeId: String?) {
        val target = NovaThemes.byId(themeId).id
        if (!::appRef.isInitialized) return
        if (isCurrent(appRef, target)) { pending = null; return }
        pending = target
        if (started == 0) { handler.removeCallbacks(applyRunnable); handler.postDelayed(applyRunnable, 1500) }
    }

    private fun isCurrent(context: Context, target: String): Boolean = runCatching {
        val pm = context.packageManager
        val state = pm.getComponentEnabledSetting(aliasFor(context, target))
        state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && target == NovaThemes.DEFAULT.id)
    }.getOrDefault(false)

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
