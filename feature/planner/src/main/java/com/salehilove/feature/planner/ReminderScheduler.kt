package com.salehilove.feature.planner

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.salehilove.core.database.RawDatabase
import com.salehilove.core.database.Reminder
import com.salehilove.core.database.dao.PlannerDao
import java.util.Calendar

/**
 * Local reminders via AlarmManager exact alarms + NotificationManager. No
 * server, no Google services. The receivers re-schedule on boot, package
 * replace, time change and timezone change so reminders survive reboots and
 * clock changes.
 */
object ReminderScheduler {

    const val CHANNEL_ID = "salehilove_reminders"
    const val EXTRA_LABEL = "label"
    const val EXTRA_TYPE = "type"
    const val EXTRA_HOUR = "hour"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "یادآورهای برنامه روزانه",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "یادآورهای صبح، ظهر و شب"
                enableVibration(true)
            },
        )
    }

    /** (Re)schedules every enabled reminder; drops past occurrences to tomorrow. */
    fun scheduleAll(context: Context, dao: PlannerDao) {
        ensureChannel(context)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        dao.enabledReminders().forEach { reminder ->
            scheduleOne(context, am, reminder)
        }
    }

    fun scheduleOne(context: Context, am: AlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager, reminder: Reminder) {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, reminder.hour)
            set(Calendar.MINUTE, reminder.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
        }
        val pending = pendingIntent(context, reminder)
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pending)
        } catch (security: SecurityException) {
            // SCHEDULE_EXACT_ALARM not granted (API 31+): fall back to inexact.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pending)
        }
    }

    fun cancel(context: Context, reminder: Reminder) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, reminder))
    }

    private fun pendingIntent(context: Context, reminder: Reminder): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_LABEL, reminder.label)
            putExtra(EXTRA_TYPE, reminder.type)
            putExtra(EXTRA_HOUR, reminder.hour)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(reminder),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Stable request code per reminder type+time so each has its own slot. */
    fun requestCodeFor(reminder: Reminder): Int =
        (reminder.type.hashCode() * 31 + reminder.hour * 100 + reminder.minute) and 0x7FFFFFFF
}

/** Fires the notification and schedules tomorrow's alarm. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra(ReminderScheduler.EXTRA_LABEL) ?: "یادآور"
        val type = intent.getStringExtra(ReminderScheduler.EXTRA_TYPE) ?: ""
        val hour = intent.getIntExtra(ReminderScheduler.EXTRA_HOUR, -1)

        ReminderScheduler.ensureChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(label)
            .setContentText(bodyFor(type))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 400, 200, 400))
            .build()
        val id = (type.hashCode() * 1000 + hour).let { if (it < 0) -it else it }
        runCatching { nm.notify(id, notification) }

        // Schedule this reminder's next occurrence from the DB (keeps repeats alive).
        val provider = context.applicationContext as? ReminderDbProvider
        if (provider != null) {
            provider.plannerDao()?.let { dao ->
                dao.enabledReminders()
                    .find { it.type == type }
                    ?.let { ReminderScheduler.scheduleOne(context, reminder = it) }
            }
        }
    }

    private fun bodyFor(type: String): String = when (type) {
        "morning" -> "صبح بخیر! برنامه امروز را ببین."
        "noon" -> "زمان مرور نیم‌روز — چند واژه مرور کن."
        "night" -> "شب بخیر! خاطرات امروز را بنویس و مرور کن."
        else -> "یادآور SalehiLove ❤️"
    }
}

/** Implemented by the Application so receivers can reach the DB without singletons. */
interface ReminderDbProvider {
    fun plannerDao(): PlannerDao?
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_LOCKED_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
            )
        ) {
            return
        }
        val provider = context.applicationContext as? ReminderDbProvider ?: return
        val dao = provider.plannerDao() ?: return
        ReminderScheduler.ensureChannel(context)
        ReminderScheduler.scheduleAll(context, dao)
    }
}

class TimeChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) return
        val provider = context.applicationContext as? ReminderDbProvider ?: return
        val dao = provider.plannerDao() ?: return
        // Cancel everything, then reschedule relative to the new clock.
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        dao.reminders().forEach { ReminderScheduler.cancel(context, it) }
        ReminderScheduler.scheduleAll(context, dao)
    }
}
