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
    fun android13AndLaterRequireRuntimePermission() {
        for (apiLevel in 33..35) {
            assertFalse(NotificationPermissionRules.canPost(apiLevel, runtimePermissionGranted = false, appNotificationsEnabled = true))
            assertTrue(NotificationPermissionRules.canPost(apiLevel, runtimePermissionGranted = true, appNotificationsEnabled = true))
        }
    }

    @Test
    fun disabledAppNotificationsAlwaysBlockPosting() {
        assertFalse(NotificationPermissionRules.canPost(apiLevel = 32, runtimePermissionGranted = true, appNotificationsEnabled = false))
        assertFalse(NotificationPermissionRules.canPost(apiLevel = 33, runtimePermissionGranted = true, appNotificationsEnabled = false))
    }
}
