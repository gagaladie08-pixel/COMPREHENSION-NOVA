package com.novastats.app.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Vérification unique des deux verrous Android : permission POST_NOTIFICATIONS et notifications d'app activées. */
object NotificationAccess {
    fun canPost(context: Context): Boolean {
        val appContext = context.applicationContext
        val sdkInt = Build.VERSION.SDK_INT
        val runtimePermissionGranted = sdkInt >= 33 &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val appNotificationsEnabled = NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        return NotificationPermissionRules.canPost(sdkInt, runtimePermissionGranted, appNotificationsEnabled)
    }

    fun hasRuntimePermission(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
