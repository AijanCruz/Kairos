package com.campusflow.app

import android.app.Application
import com.campusflow.app.data.PreferencesRepository
import com.campusflow.app.data.CampusDatabase
import com.campusflow.app.data.CampusRepository
import com.campusflow.app.reminders.*
import kotlinx.coroutines.*

class CampusApp : Application() {
    val preferences by lazy { PreferencesRepository(this) }
    val database by lazy { CampusDatabase.create(this) }
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val reminders by lazy { ReminderScheduler(this, database, preferences) }
    val repository by lazy { CampusRepository(database, ::refreshReminders) }
    private val refreshRequests = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            reminders.createChannel()
            ReminderWork.maintain(this@CampusApp)
            for (request in refreshRequests) {
                delay(150)
                while (refreshRequests.tryReceive().isSuccess) { /* Coalesce a batch import. */ }
                try { reminders.reconcile() }
                catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) { android.util.Log.e("CampusReminders", "Se reintentará la programación", error) }
                ReminderWork.request(this@CampusApp)
            }
        }
    }
    fun refreshReminders() {
        refreshRequests.trySend(Unit)
    }
}
