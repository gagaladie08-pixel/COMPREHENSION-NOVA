package com.novastats.app.service

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.util.Log

/**
 * Relance auto au redémarrage du téléphone.
 * Le NotificationListenerService est (re)lié par le système ; on force un rebind par sécurité
 * (utile sur Xiaomi / Huawei / Oppo où le service est parfois "oublié").
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != "android.intent.action.QUICKBOOT_POWERON") return
        Log.i("NovaBoot", "Boot détecté → rebind du service de détection")
        val component = ComponentName(context, NovaListenerService::class.java)
        runCatching {
            // Toggle du composant : astuce standard pour forcer Android à reconnecter le listener
            val pm = context.packageManager
            pm.setComponentEnabledSetting(component, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(component, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, android.content.pm.PackageManager.DONT_KILL_APP)
            NotificationListenerService.requestRebind(component)
        }.onFailure { Log.w("NovaBoot", "Rebind impossible", it) }
    }
}
