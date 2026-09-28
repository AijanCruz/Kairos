package com.campusflow.app.reminders

import android.Manifest
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import com.campusflow.app.CampusApp
import com.campusflow.app.MainActivity
import com.campusflow.app.R
import com.campusflow.app.data.Status
import com.campusflow.app.domain.CalendarRules
import com.campusflow.app.domain.clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import androidx.core.net.toUri

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as CampusApp
        app.applicationScope.launch {
            try {
                withTimeout(8000) {
                    val id = intent.getLongExtra("id", -1)
                    val trigger = intent.getLongExtra("trigger", -1)
                    val allowed = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    if (!allowed) return@withTimeout
                    app.reminders.deliver(id, trigger) { item ->
                        val launch = Intent(context, MainActivity::class.java).apply {
                            data = "campusflow://activity/$id".toUri()
                            putExtra("occurrenceId", id)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        val click = PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                        val minutes = ((CalendarRules.instant(item.occurrence.day, item.occurrence.minute) - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
                        val text = if (minutes > 0) "En $minutes minutos · ${item.occurrence.minute.clock()}" else "Es hora · ${item.occurrence.minute.clock()}"
                        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL)
                            .setSmallIcon(R.drawable.ic_notification).setContentTitle(item.schedule.title)
                            .setContentText("$text · ${item.schedule.durationMinutes} min")
                            .setStyle(NotificationCompat.BigTextStyle().bigText("$text · ${item.schedule.durationMinutes} min\n${item.schedule.notes}"))
                            .setContentIntent(click).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setCategory(NotificationCompat.CATEGORY_REMINDER).build()
                        NotificationManagerCompat.from(context).notify("activity-$id", 0, notification)
                    }
                }
            } catch (error: Exception) {
                android.util.Log.e("CampusReminders", "No se pudo entregar el recordatorio", error)
            } finally {
                ReminderWork.request(context)
                pending.finish()
            }
        }
    }
}

class RestoreRemindersReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED")) return
        val pending = goAsync()
        val app = context.applicationContext as CampusApp
        app.applicationScope.launch {
            try { withTimeout(8000) { app.reminders.reconcile() } }
            catch (error: Exception) { android.util.Log.w("CampusReminders", "La restauración continuará con WorkManager", error) }
            finally { ReminderWork.request(context); pending.finish() }
        }
    }
}
