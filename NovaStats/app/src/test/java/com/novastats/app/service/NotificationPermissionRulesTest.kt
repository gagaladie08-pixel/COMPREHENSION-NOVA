package com.novastats.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionRulesTest {

    @Test
    fun preAndroid13DoesNotRequireRuntimePermission() {
        assertTrue(NotificationPermissionRules.canPost(apiLevel = 32, runtimePermissionGranted = false, appNotificationsEnabled = true))
    }

    @Test
    fun android13RequiresRuntimePermission() {
        assertFalse(NotificationPermissionRules.canPost(apiLevel = 33, runtimePermissionGranted = false, appNotificationsEnabled = true))
        assertTrue(NotificationPermissionRules.canPost(apiLevel = 33, runtimePermissionGranted = true, appNotificationsEnabled = true))
    }

    @Test
    fun disabledAppNotificationsAlwaysBlockPosting() {
        assertFalse(NotificationPermissionRules.canPost(apiLevel = 32, runtimePermissionGranted = true, appNotificationsEnabled = false))
        assertFalse(NotificationPermissionRules.canPost(apiLevel = 33, runtimePermissionGranted = true, appNotificationsEnabled = false))
    }
}
