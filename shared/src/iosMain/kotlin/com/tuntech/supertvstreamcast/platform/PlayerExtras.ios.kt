package com.tuntech.supertvstreamcast.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** AVPlayerViewController has its own picture-in-picture button. */
@Composable actual fun rememberPictureInPicture(): PictureInPicture? = null
/** AVPlayerViewController shows the AirPlay button itself. */
@Composable actual fun CastButton(modifier: Modifier) = Unit

@Composable actual fun rememberReminderScheduler(): ReminderScheduler = remember {
    val center = UNUserNotificationCenter.currentNotificationCenter()
    ReminderScheduler(schedule = { reminder, title, body ->
        val allowed = suspendCoroutine { continuation ->
            center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { granted, _ -> continuation.resume(granted) }
        }
        if (allowed) {
            val content = UNMutableNotificationContent().apply { setTitle(title); setBody(body); setSound(UNNotificationSound.defaultSound) }
            val delay = (reminder.at - NSDate().timeIntervalSince1970).coerceAtLeast(1.0)
            center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(identifier(reminder.id), content,
                UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(delay, repeats = false)), withCompletionHandler = null)
        }
        allowed
    }, cancel = { id -> center.removePendingNotificationRequestsWithIdentifiers(listOf(identifier(id))) })
}
private fun identifier(id: Int) = "tv-guide-$id"
