package com.luca.watchvoipnotifier

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.time.Instant

/**
 * Data structure representing action details from a surveyed notification.
 */
data class SurveyActionInfo(
    val title: String?,
    val hasRemoteInput: Boolean,
    val pendingIntentType: String
)

/**
 * NotificationListenerService dedicated to surveying VoIP notifications.
 *
 * Filtered packages:
 * - com.zing.zalo
 * - com.facebook.orca
 *
 * Logs notification lifecycle events (posted & removed) to Logcat with tag "SURVEY".
 * No data is forwarded to Wearable / watch devices during this survey phase.
 * No logs are stored on disk / file.
 */
class SurveyNotificationListenerService : NotificationListenerService() {

    private var lastRemovedKey: String? = null
    private var lastRemovedTime: Long = 0L

    companion object {
        const val TAG = "SURVEY"

        val TARGET_PACKAGES: Set<String> = setOf(
            "com.zing.zalo",
            "com.facebook.orca"
        )

        fun isTargetPackage(packageName: String?): Boolean {
            return packageName != null && packageName in TARGET_PACKAGES
        }

        fun describePendingIntentType(pendingIntent: PendingIntent?): String {
            if (pendingIntent == null) return "none"
            return when {
                pendingIntent.isActivity -> "activity"
                pendingIntent.isBroadcast -> "broadcast"
                pendingIntent.isForegroundService -> "service"
                pendingIntent.isService -> "service"
                else -> "unknown"
            }
        }

        fun describeFlags(flags: Int): String {
            val flagList = mutableListOf<String>()
            if (flags and Notification.FLAG_ONGOING_EVENT != 0) flagList.add("FLAG_ONGOING_EVENT")
            if (flags and Notification.FLAG_NO_CLEAR != 0) flagList.add("FLAG_NO_CLEAR")
            if (flags and Notification.FLAG_INSISTENT != 0) flagList.add("FLAG_INSISTENT")
            if (flags and Notification.FLAG_ONLY_ALERT_ONCE != 0) flagList.add("FLAG_ONLY_ALERT_ONCE")
            if (flags and Notification.FLAG_AUTO_CANCEL != 0) flagList.add("FLAG_AUTO_CANCEL")
            if (flags and Notification.FLAG_FOREGROUND_SERVICE != 0) flagList.add("FLAG_FOREGROUND_SERVICE")
            if (flags and Notification.FLAG_LOCAL_ONLY != 0) flagList.add("FLAG_LOCAL_ONLY")
            if (flags and Notification.FLAG_GROUP_SUMMARY != 0) flagList.add("FLAG_GROUP_SUMMARY")
            if (flags and Notification.FLAG_BUBBLE != 0) flagList.add("FLAG_BUBBLE")
            val flagNames = if (flagList.isNotEmpty()) flagList.joinToString("|") else "NONE"
            return "0x${Integer.toHexString(flags)} ($flags) [$flagNames]"
        }

        fun describeRemovalReason(reason: Int): String {
            return when (reason) {
                REASON_CLICK -> "CLICK ($reason)"
                REASON_CANCEL -> "CANCEL ($reason)"
                REASON_CANCEL_ALL -> "CANCEL_ALL ($reason)"
                REASON_ERROR -> "ERROR ($reason)"
                REASON_PACKAGE_CHANGED -> "PACKAGE_CHANGED ($reason)"
                REASON_USER_STOPPED -> "USER_STOPPED ($reason)"
                REASON_PACKAGE_BANNED -> "PACKAGE_BANNED ($reason)"
                REASON_APP_CANCEL -> "APP_CANCEL ($reason)"
                REASON_APP_CANCEL_ALL -> "APP_CANCEL_ALL ($reason)"
                REASON_LISTENER_CANCEL -> "LISTENER_CANCEL ($reason)"
                REASON_LISTENER_CANCEL_ALL -> "LISTENER_CANCEL_ALL ($reason)"
                REASON_GROUP_SUMMARY_CANCELED -> "GROUP_SUMMARY_CANCELED ($reason)"
                REASON_GROUP_OPTIMIZATION -> "GROUP_OPTIMIZATION ($reason)"
                REASON_PACKAGE_SUSPENDED -> "PACKAGE_SUSPENDED ($reason)"
                REASON_PROFILE_TURNED_OFF -> "PROFILE_TURNED_OFF ($reason)"
                REASON_UNAUTOBUNDLED -> "UNAUTOBUNDLED ($reason)"
                REASON_CHANNEL_BANNED -> "CHANNEL_BANNED ($reason)"
                REASON_SNOOZED -> "SNOOZED ($reason)"
                REASON_TIMEOUT -> "TIMEOUT ($reason)"
                REASON_CHANNEL_REMOVED -> "CHANNEL_REMOVED ($reason)"
                REASON_CLEAR_DATA -> "CLEAR_DATA ($reason)"
                REASON_ASSISTANT_CANCEL -> "ASSISTANT_CANCEL ($reason)"
                else -> "UNKNOWN ($reason)"
            }
        }

        fun formatSurveyLog(
            event: String,
            packageName: String,
            category: String?,
            flags: Int,
            channelId: String?,
            title: String?,
            text: String?,
            hasFullScreenIntent: Boolean,
            actions: List<SurveyActionInfo>,
            ongoing: Boolean,
            timestamp: String,
            postTimeMs: Long,
            postTimestamp: String,
            removalReason: String? = null
        ): String {
            val actionsFormatted = if (actions.isEmpty()) {
                "[]"
            } else {
                actions.mapIndexed { index, action ->
                    "Action #$index(title=\"${action.title ?: ""}\", hasRemoteInput=${action.hasRemoteInput}, pendingIntentType=${action.pendingIntentType})"
                }.joinToString(prefix = "[\n    ", separator = ",\n    ", postfix = "\n  ]")
            }

            return buildString {
                appendLine("[$event Notification]")
                appendLine("  package: $packageName")
                appendLine("  category: ${category ?: "null"}")
                appendLine("  flags: ${describeFlags(flags)}")
                appendLine("  channelId: ${channelId ?: "null"}")
                appendLine("  title: ${title ?: "null"}")
                appendLine("  text: ${text ?: "null"}")
                appendLine("  fullScreenIntent: ${if (hasFullScreenIntent) "YES (present)" else "NO (none)"}")
                appendLine("  actions: $actionsFormatted")
                appendLine("  ongoing: $ongoing")
                appendLine("  timestamp: $timestamp (eventTimeMs=${System.currentTimeMillis()}, postTimeMs=$postTimeMs, postTime=$postTimestamp)")
                if (removalReason != null) {
                    appendLine("  removalReason: $removalReason")
                }
            }.trimEnd()
        }

        fun isPermissionGranted(context: Context): Boolean {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val componentName = ComponentName(context, SurveyNotificationListenerService::class.java)

            if (notificationManager?.isNotificationListenerAccessGranted(componentName) == true) {
                return true
            }

            val flatComponentName = componentName.flattenToString()
            val shortComponentName = componentName.flattenToShortString()
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false

            return enabledListeners.split(":").any { item ->
                item.equals(flatComponentName, ignoreCase = true) ||
                        item.equals(shortComponentName, ignoreCase = true) ||
                        item.startsWith("${context.packageName}/", ignoreCase = true)
            }
        }

        fun openSettings(context: Context) {
            val componentName = ComponentName(context, SurveyNotificationListenerService::class.java)
            val detailIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, componentName.flattenToString())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(detailIntent)
            } catch (_: Exception) {
                val generalIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(generalIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open notification listener settings", e)
                }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "SurveyNotificationListenerService connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.i(TAG, "SurveyNotificationListenerService disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val packageName = sbn.packageName
        if (!isTargetPackage(packageName)) return

        val notification = sbn.notification ?: return
        val extras = notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        val actions = notification.actions?.map { action ->
            SurveyActionInfo(
                title = action.title?.toString(),
                hasRemoteInput = !action.remoteInputs.isNullOrEmpty(),
                pendingIntentType = describePendingIntentType(action.actionIntent)
            )
        } ?: emptyList()

        val now = Instant.now()
        val postInstant = Instant.ofEpochMilli(sbn.postTime)

        val logMessage = formatSurveyLog(
            event = "POSTED",
            packageName = packageName,
            category = notification.category,
            flags = notification.flags,
            channelId = notification.channelId,
            title = title,
            text = text,
            hasFullScreenIntent = notification.fullScreenIntent != null,
            actions = actions,
            ongoing = sbn.isOngoing,
            timestamp = now.toString(),
            postTimeMs = sbn.postTime,
            postTimestamp = postInstant.toString()
        )

        Log.i(TAG, logMessage)
        Log.i(TAG, "  extras: keys=${extras?.keySet()?.sorted()} template=${extras?.getString(Notification.EXTRA_TEMPLATE)} callType=${extras?.getInt(Notification.EXTRA_CALL_TYPE, -1)} priority=${notification.priority} visibility=${notification.visibility}")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        if (sbn == null) return
        val packageName = sbn.packageName
        if (!isTargetPackage(packageName)) return

        val key = "${sbn.key}_${sbn.postTime}"
        if (isDuplicateRemoval(key)) return

        val notification = sbn.notification
        val extras = notification?.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        val actions = notification?.actions?.map { action ->
            SurveyActionInfo(
                title = action.title?.toString(),
                hasRemoteInput = !action.remoteInputs.isNullOrEmpty(),
                pendingIntentType = describePendingIntentType(action.actionIntent)
            )
        } ?: emptyList()

        val now = Instant.now()
        val postInstant = Instant.ofEpochMilli(sbn.postTime)

        val logMessage = formatSurveyLog(
            event = "REMOVED",
            packageName = packageName,
            category = notification?.category,
            flags = notification?.flags ?: 0,
            channelId = notification?.channelId,
            title = title,
            text = text,
            hasFullScreenIntent = notification?.fullScreenIntent != null,
            actions = actions,
            ongoing = sbn.isOngoing,
            timestamp = now.toString(),
            postTimeMs = sbn.postTime,
            postTimestamp = postInstant.toString(),
            removalReason = describeRemovalReason(reason)
        )

        Log.i(TAG, logMessage)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val packageName = sbn.packageName
        if (!isTargetPackage(packageName)) return

        val key = "${sbn.key}_${sbn.postTime}"
        if (isDuplicateRemoval(key)) return

        val notification = sbn.notification
        val extras = notification?.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        val actions = notification?.actions?.map { action ->
            SurveyActionInfo(
                title = action.title?.toString(),
                hasRemoteInput = !action.remoteInputs.isNullOrEmpty(),
                pendingIntentType = describePendingIntentType(action.actionIntent)
            )
        } ?: emptyList()

        val now = Instant.now()
        val postInstant = Instant.ofEpochMilli(sbn.postTime)

        val logMessage = formatSurveyLog(
            event = "REMOVED",
            packageName = packageName,
            category = notification?.category,
            flags = notification?.flags ?: 0,
            channelId = notification?.channelId,
            title = title,
            text = text,
            hasFullScreenIntent = notification?.fullScreenIntent != null,
            actions = actions,
            ongoing = sbn.isOngoing,
            timestamp = now.toString(),
            postTimeMs = sbn.postTime,
            postTimestamp = postInstant.toString(),
            removalReason = null
        )

        Log.i(TAG, logMessage)
    }

    private fun isDuplicateRemoval(key: String): Boolean {
        val now = System.currentTimeMillis()
        if (key == lastRemovedKey && (now - lastRemovedTime) < 500) {
            return true
        }
        lastRemovedKey = key
        lastRemovedTime = now
        return false
    }
}
