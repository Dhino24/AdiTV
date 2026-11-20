package com.aditv.data.sport

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.aditv.AditvApp
import com.aditv.MainActivity
import com.aditv.R
import com.aditv.core.AppLog
import com.aditv.data.db.AditvDatabase
import com.aditv.data.db.ReminderEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Rappel déclenché pendant que l'application est ouverte : bandeau affiché par-dessus l'écran courant, même en lecture. */
object ReminderBus {
    private val _current = MutableStateFlow<ReminderEntity?>(null)
    val current: StateFlow<ReminderEntity?> = _current
    fun show(r: ReminderEntity) { _current.value = r }
    fun clear() { _current.value = null }
}

/** Chaîne à ouvrir demandée depuis une notification de rappel. */
object PendingLaunch {
    val channel = MutableStateFlow<String?>(null)
}

/**
 * Alarmes des rappels : AlarmManager (survit à la fermeture de l'application), reprogrammées au démarrage
 * de l'appareil et à chaque lancement. Application ouverte : bandeau ; sinon : notification.
 */
class ReminderScheduler(private val context: Context, private val db: AditvDatabase) {
    private val alarms get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun intentFor(id: Long) = PendingIntent.getBroadcast(
        context, id.toInt(), Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    suspend fun rescheduleAll() {
        val now = System.currentTimeMillis()
        db.sport().pending(now).forEach { r -> set(r.id, r.notifyAt) }
    }

    private fun set(id: Long, at: Long) = runCatching {
        val pi = intentFor(id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms()) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }.onFailure { AppLog.w("Alarme de rappel non programmée", it) }

    fun cancel(id: Long) { runCatching { alarms.cancel(intentFor(id)) } }

    suspend fun fire(id: Long) {
        val r = db.sport().reminder(id) ?: return
        if (r.dismissed || r.firedAt != null) return
        db.sport().markFired(id, System.currentTimeMillis())
        if (AditvApp.inForeground) ReminderBus.show(r) else notify(r)
    }

    private fun notify(r: ReminderEntity) = runCatching {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Rappels de matchs", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(
            context, r.id.toInt(), Intent(context, MainActivity::class.java).putExtra(EXTRA_CHANNEL, r.channelItemId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val minutes = ((r.startUtc - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
        @Suppress("DEPRECATION")
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) android.app.Notification.Builder(context, CHANNEL) else android.app.Notification.Builder(context).setPriority(android.app.Notification.PRIORITY_HIGH)
        val n = b.setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(r.title)
            .setContentText((if (minutes > 0) "Dans $minutes min" else "Commence maintenant") + " sur ${r.channelName}")
            .setContentIntent(open).setAutoCancel(true).setCategory(android.app.Notification.CATEGORY_REMINDER).build()
        nm.notify(r.id.toInt(), n)
    }.onFailure { AppLog.w("Notification de rappel impossible", it) }

    companion object {
        const val EXTRA_ID = "reminder_id"
        const val EXTRA_CHANNEL = "reminder_channel"
        private const val CHANNEL = "rappels"
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1)
        if (id < 0) return
        val app = context.applicationContext as AditvApp
        val pending = goAsync()
        app.container.appScope.launch {
            try { app.container.reminders.fire(id) } finally { pending.finish() }
        }
    }
}

/** Redémarrage de l'appareil : les alarmes sont perdues, on les reprogramme depuis la base. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as AditvApp
        val pending = goAsync()
        app.container.appScope.launch {
            try { app.container.reminders.rescheduleAll() } finally { pending.finish() }
        }
    }
}
