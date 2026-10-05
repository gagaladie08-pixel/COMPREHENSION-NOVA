package com.novastats.app.service

/** Règle pure qui centralise le verrou Android 13+ et le réglage global de l'app. */
object NotificationPermissionRules {
    fun canPost(apiLevel: Int, runtimePermissionGranted: Boolean, appNotificationsEnabled: Boolean): Boolean =
        appNotificationsEnabled && (apiLevel < 33 || runtimePermissionGranted)
}
