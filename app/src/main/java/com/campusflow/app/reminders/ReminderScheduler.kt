package com.campusflow.app.reminders

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import androidx.core.app.NotificationManagerCompat
import com.campusflow.app.data.*
import com.campusflow.app.domain.CalendarRules
import com.campusflow.app.domain.ReminderPolicy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ReminderScheduler(private val context: Context, private val db: CampusDatabase, private val preferences: PreferencesRepository) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val mutex = Mutex()
    fun exactAllowed(): Boolean = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()

    fun createChannel() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Actividades y horarios", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Recordatorios de universidad, estudio, entrenamiento y actividades personales"
            },
        )
    }

    private fun pendingIntent(id: Long, trigger: Long): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, ReminderReceiver::class.java).apply {
            data = "campusflow://reminder/$id".toUri()
            putExtra("id", id); putExtra("trigger", trigger)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    suspend fun reconcile() = mutex.withLock {
        val dao = db.dao()
        val states = dao.reminderStates().associateBy { it.occurrenceId }
        val now = System.currentTimeMillis()
        val enabled = preferences.flow.first().notifications && NotificationManagerCompat.from(context).areNotificationsEnabled()
        val desired = if (!enabled) emptyList() else dao.pending().mapNotNull { item ->
            val offset = item.schedule.reminderMinutes
            val start = CalendarRules.instant(item.occurrence.day, item.occurrence.minute)
            val old = states[item.occurrence.id]
            if (!ReminderPolicy.shouldQueue(start, offset, item.schedule.durationMinutes, now, old != null)) null else {
                val trigger = start - offset * 60_000L
                if (old?.triggerAt == trigger && old.delivered) null else item.occurrence.id to trigger
            }
        }.sortedBy { it.second }.take(80)
        val ids = desired.map { it.first }.toSet()
        states.values.filter { it.scheduled && it.occurrenceId !in ids }.forEach {
            alarmManager.cancel(pendingIntent(it.occurrenceId, it.triggerAt))
            dao.saveReminderState(it.copy(scheduled = false))
        }
        desired.forEach { (id, trigger) ->
            val token = pendingIntent(id, trigger)
            val at = maxOf(trigger, now + 1000)
            try {
                if (exactAllowed()) alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, token)
                else alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, token)
            } catch (_: SecurityException) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, token)
            }
            dao.saveReminderState(ReminderState(id, trigger, delivered = false, scheduled = true))
        }
    }

    suspend fun replaceData(block: suspend () -> Unit) = mutex.withLock {
        val previous = db.dao().reminderStates()
        block()
        // Invalid restores roll back without cancelling the user's existing alarms.
        previous.forEach { alarmManager.cancel(pendingIntent(it.occurrenceId, it.triggerAt)) }
        NotificationManagerCompat.from(context).cancelAll()
    }

    suspend fun deliver(id: Long, trigger: Long, notify: (ActivityItem) -> Unit) = mutex.withLock {
        val dao = db.dao()
        val item = dao.activity(id) ?: return@withLock
        val expected = CalendarRules.instant(item.occurrence.day, item.occurrence.minute) - item.schedule.reminderMinutes * 60_000L
        if (!preferences.flow.first().notifications || !NotificationManagerCompat.from(context).areNotificationsEnabled() || item.occurrence.status != Status.PENDING || item.schedule.reminderMinutes < 0 || trigger != expected) return@withLock
        val now = System.currentTimeMillis()
        if (now < trigger - 1000 || now - trigger > 3 * 60 * 60_000L) return@withLock
        if (now > CalendarRules.instant(item.occurrence.day, item.occurrence.minute) + item.schedule.durationMinutes * 60_000L) return@withLock
        val state = dao.reminderState(id) ?: return@withLock
        if (state.triggerAt != trigger || state.delivered) return@withLock
        createChannel()
        notify(item)
        dao.saveReminderState(state.copy(delivered = true, scheduled = false))
    }

    companion object { const val CHANNEL = "activities_v1" }
}
