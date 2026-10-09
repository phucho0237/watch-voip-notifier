package com.luca.watchvoipnotifier

import android.app.Notification
import android.service.notification.NotificationListenerService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurveyNotificationListenerServiceTest {

    @Test
    fun targetPackages_containsOnlyZaloAndMessenger() {
        assertEquals(2, SurveyNotificationListenerService.TARGET_PACKAGES.size)
        assertTrue(SurveyNotificationListenerService.TARGET_PACKAGES.contains("com.zing.zalo"))
        assertTrue(SurveyNotificationListenerService.TARGET_PACKAGES.contains("com.facebook.orca"))

        assertTrue(SurveyNotificationListenerService.isTargetPackage("com.zing.zalo"))
        assertTrue(SurveyNotificationListenerService.isTargetPackage("com.facebook.orca"))

        assertFalse(SurveyNotificationListenerService.isTargetPackage("com.whatsapp"))
        assertFalse(SurveyNotificationListenerService.isTargetPackage("com.google.android.dialer"))
        assertFalse(SurveyNotificationListenerService.isTargetPackage(null))
    }

    @Test
    fun describePendingIntentType_handlesNull() {
        assertEquals("none", SurveyNotificationListenerService.describePendingIntentType(null))
    }

    @Test
    fun describeFlags_containsDecodedFlagNames() {
        val flags = Notification.FLAG_ONGOING_EVENT or Notification.FLAG_NO_CLEAR
        val description = SurveyNotificationListenerService.describeFlags(flags)

        assertTrue(description.contains("FLAG_ONGOING_EVENT"))
        assertTrue(description.contains("FLAG_NO_CLEAR"))
        assertTrue(description.contains("0x"))
    }

    @Test
    fun describeRemovalReason_mapsExpectedReasons() {
        assertEquals(
            "APP_CANCEL (${NotificationListenerService.REASON_APP_CANCEL})",
            SurveyNotificationListenerService.describeRemovalReason(NotificationListenerService.REASON_APP_CANCEL)
        )
        assertEquals(
            "CLICK (${NotificationListenerService.REASON_CLICK})",
            SurveyNotificationListenerService.describeRemovalReason(NotificationListenerService.REASON_CLICK)
        )
        assertEquals(
            "CANCEL (${NotificationListenerService.REASON_CANCEL})",
            SurveyNotificationListenerService.describeRemovalReason(NotificationListenerService.REASON_CANCEL)
        )
    }

    @Test
    fun formatSurveyLog_includesAllRequiredFields() {
        val actions = listOf(
            SurveyActionInfo(
                title = "Decline",
                hasRemoteInput = false,
                pendingIntentType = "activity"
            ),
            SurveyActionInfo(
                title = "Answer",
                hasRemoteInput = false,
                pendingIntentType = "broadcast"
            ),
            SurveyActionInfo(
                title = "Quick Reply",
                hasRemoteInput = true,
                pendingIntentType = "service"
            )
        )

        val log = SurveyNotificationListenerService.formatSurveyLog(
            event = "POSTED",
            packageName = "com.zing.zalo",
            category = "call",
            flags = Notification.FLAG_ONGOING_EVENT,
            channelId = "zalo_call_channel",
            title = "Test Caller",
            text = "Incoming audio call",
            hasFullScreenIntent = true,
            actions = actions,
            ongoing = true,
            timestamp = "2026-10-10T02:00:00Z",
            postTimeMs = 1700000000000L,
            postTimestamp = "2026-10-10T02:00:00Z",
            removalReason = null
        )

        assertTrue(log.contains("[POSTED Notification]"))
        assertTrue(log.contains("package: com.zing.zalo"))
        assertTrue(log.contains("category: call"))
        assertTrue(log.contains("flags:"))
        assertTrue(log.contains("channelId: zalo_call_channel"))
        assertTrue(log.contains("title: Test Caller"))
        assertTrue(log.contains("text: Incoming audio call"))
        assertTrue(log.contains("fullScreenIntent: YES (present)"))
        assertTrue(log.contains("actions:"))
        assertTrue(log.contains("title=\"Decline\""))
        assertTrue(log.contains("hasRemoteInput=false"))
        assertTrue(log.contains("pendingIntentType=activity"))
        assertTrue(log.contains("title=\"Quick Reply\""))
        assertTrue(log.contains("hasRemoteInput=true"))
        assertTrue(log.contains("pendingIntentType=service"))
        assertTrue(log.contains("ongoing: true"))
        assertTrue(log.contains("timestamp: 2026-10-10T02:00:00Z"))
    }
}
