package com.tuntech.supertvstreamcast.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tuntech.supertvstreamcast.domain.Reminder

/**
 * Picture-in-picture of the playing video. [active] is true while the app is shown in the small
 * system window, where only the picture should be drawn; [enter] asks the system to switch to it.
 */
class PictureInPicture(val active: Boolean, val enter: () -> Unit)
/** Null where the player's own controls offer picture-in-picture (iOS) or the device has none. */
@Composable expect fun rememberPictureInPicture(): PictureInPicture?

/**
 * Button that hands the playing stream to a Chromecast. Draws nothing when casting is not
 * available (no Google Play services) and on iOS, where the player shows its own AirPlay button.
 */
@Composable expect fun CastButton(modifier: Modifier)

/**
 * Local notifications for programme reminders. [schedule] asks for notification permission when
 * needed and returns false if it was refused; the notification fires at [Reminder.at]. Reminders
 * do not survive a device restart on Android.
 */
class ReminderScheduler(val schedule: suspend (reminder: Reminder, title: String, body: String) -> Boolean, val cancel: (id: Int) -> Unit)
@Composable expect fun rememberReminderScheduler(): ReminderScheduler
