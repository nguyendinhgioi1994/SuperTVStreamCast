package com.tuntech.supertvstreamcast.platform

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.media3.cast.Cast
import androidx.media3.cast.MediaRouteButton
import androidx.media3.cast.rememberMediaRouteButtonState
import androidx.media3.common.util.UnstableApi
import com.tuntech.supertvstreamcast.shared.R
import kotlinx.coroutines.CompletableDeferred

@Composable actual fun rememberPictureInPicture(): PictureInPicture? {
    val activity = LocalActivity.current as? ComponentActivity ?: return null
    if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return null
    var active by remember(activity) { mutableStateOf(activity.isInPictureInPictureMode) }
    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { active = it.isInPictureInPictureMode }
        activity.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity.removeOnPictureInPictureModeChangedListener(listener) }
    }
    return PictureInPicture(active) {
        // Refused by the system (e.g. the activity is not resumed): the player simply stays as it is.
        runCatching { activity.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) }
    }
}

private var castChecked = false
private var castReady = false
/** Starts the Cast framework once per process; false on devices without Google Play services. */
@OptIn(UnstableApi::class)
internal fun castAvailable(context: Context): Boolean {
    if (!castChecked) {
        castChecked = true
        castReady = runCatching { Cast.getSingletonInstance(context.applicationContext).initialize() }.isSuccess
    }
    return castReady
}
@OptIn(UnstableApi::class)
@Composable actual fun CastButton(modifier: Modifier) {
    val context = LocalContext.current
    if (remember(context) { castAvailable(context) }) MediaRouteButton(modifier = modifier, state = rememberMediaRouteButtonState())
}

@Composable actual fun rememberReminderScheduler(): ReminderScheduler {
    val context = LocalContext.current.applicationContext
    var pending by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> pending?.complete(granted); pending = null }
    return remember(context, launcher) {
        ReminderScheduler(schedule = { reminder, title, body ->
            val allowed = Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ||
                CompletableDeferred<Boolean>().also { pending = it; launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }.await()
            if (allowed) ReminderAlarms.arm(context, reminder.id, reminder.at, title, body)
            allowed
        }, cancel = { id -> ReminderAlarms.disarm(context, id) })
    }
}

/**
 * Alarms do not survive a device restart, so every armed reminder is also kept in a small private
 * preferences file (id → time, programme title, notification text) and armed again by
 * [ReminderReceiver] after boot or an app update. An entry is dropped when it fires or is cancelled.
 */
internal object ReminderAlarms {
    private const val PREFS = "tv_guide_reminders"
    private const val SEPARATOR = '\u0000'
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun arm(context: Context, id: Int, at: Long, title: String, body: String) {
        // Inexact on purpose: exact alarms need a restricted permission, and a minute either way is fine here.
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at * 1000, reminderIntent(context, id, title, body))
        prefs(context).edit().putString(id.toString(), listOf(at.toString(), title, body).joinToString(SEPARATOR.toString())).apply()
    }
    fun disarm(context: Context, id: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(reminderIntent(context, id, "", ""))
        forget(context, id)
    }
    fun forget(context: Context, id: Int) = prefs(context).edit().remove(id.toString()).apply()
    /** Arms the stored reminders again; ones whose time passed while the device was off are dropped. */
    fun rearm(context: Context, nowSeconds: Long = System.currentTimeMillis() / 1000) {
        for ((key, value) in prefs(context).all) {
            val id = key.toIntOrNull()
            val parts = (value as? String)?.split(SEPARATOR).orEmpty()
            val at = parts.getOrNull(0)?.toLongOrNull()
            if (id == null || parts.size != 3 || at == null || at <= nowSeconds) { prefs(context).edit().remove(key).apply(); continue }
            arm(context, id, at, parts[1], parts[2])
        }
    }
}
private fun reminderIntent(context: Context, id: Int, title: String, body: String): PendingIntent =
    PendingIntent.getBroadcast(context, id, Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_ID, id).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_BODY, body),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
private const val EXTRA_ID = "id"
private const val EXTRA_TITLE = "title"
private const val EXTRA_BODY = "body"
private const val CHANNEL = "tv_guide"

/** Posts the reminder when its alarm fires (tapping it opens the app), and re-arms stored reminders after boot or an update. Declared in the app manifest. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            ReminderAlarms.rearm(context)
            return
        }
        ReminderAlarms.forget(context, intent.getIntExtra(EXTRA_ID, 0))
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        if (title.isEmpty()) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.applicationInfo.loadLabel(context.packageManager), NotificationManager.IMPORTANCE_DEFAULT))
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE) }
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title).setContentText(intent.getStringExtra(EXTRA_BODY).orEmpty()).setAutoCancel(true).setContentIntent(open).build()
        // Permission may have been withdrawn since the reminder was set.
        runCatching { manager.notify(intent.getIntExtra(EXTRA_ID, 0), notification) }
    }
}
