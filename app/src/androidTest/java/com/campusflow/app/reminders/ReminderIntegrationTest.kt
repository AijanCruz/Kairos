package com.campusflow.app.reminders

import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.campusflow.app.CampusApp
import com.campusflow.app.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class ReminderIntegrationTest {
    @Test fun realAlarmDeliversOnceAndRescheduleRejectsObsoleteTrigger() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<CampusApp>()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.executeShellCommand("pm grant ${app.packageName} android.permission.POST_NOTIFICATIONS").close()
        automation.executeShellCommand("appops set ${app.packageName} SCHEDULE_EXACT_ALARM allow").close()
        val previous = app.preferences.flow.first().notifications
        app.preferences.setNotifications(true)
        val start = LocalDateTime.now().plusMinutes(2).withSecond(0).withNano(0)
        val scheduleId = app.repository.saveSchedule(Schedule(title = "Alarma de verificación", category = Categories.STUDY, startDay = start.toLocalDate().toEpochDay(), startMinute = start.hour * 60 + start.minute, durationMinutes = 30, reminderMinutes = 5))
        val item = app.repository.activities.first().first { it.schedule.id == scheduleId }
        val manager = app.getSystemService(NotificationManager::class.java)
        try {
            app.reminders.reconcile()
            // Reminder is already due while the activity is in the future: catch-up fires now.
            withTimeout(30_000) {
                while (manager.activeNotifications.none { it.tag == "activity-${item.occurrence.id}" }) delay(250)
            }
            val state = app.database.dao().reminderStates().first { it.occurrenceId == item.occurrence.id }
            assertTrue(state.delivered)
            var duplicate = false
            app.reminders.deliver(item.occurrence.id, state.triggerAt) { duplicate = true }
            assertFalse(duplicate)
            app.repository.move(item.occurrence.id, start.toLocalDate().plusDays(1).toEpochDay(), start.hour * 60 + start.minute)
            app.reminders.reconcile()
            val moved = app.database.dao().reminderStates().first { it.occurrenceId == item.occurrence.id }
            assertTrue(moved.scheduled)
            assertTrue(moved.triggerAt > state.triggerAt)
            var stale = false
            app.reminders.deliver(item.occurrence.id, state.triggerAt) { stale = true }
            assertFalse(stale)
            app.repository.complete(item.occurrence.id)
            app.reminders.reconcile()
            assertFalse(app.database.dao().reminderStates().first { it.occurrenceId == item.occurrence.id }.scheduled)
        } finally {
            app.repository.cancel(item.occurrence.id)
            app.reminders.reconcile()
            manager.cancel("activity-${item.occurrence.id}", 0)
            withContext(Dispatchers.IO) { app.database.openHelper.writableDatabase.execSQL("DELETE FROM schedules WHERE id = ?", arrayOf(scheduleId)) }
            app.preferences.setNotifications(previous)
        }
    }
}
