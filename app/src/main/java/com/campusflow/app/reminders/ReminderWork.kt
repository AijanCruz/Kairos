package com.campusflow.app.reminders

import android.content.Context
import androidx.room.withTransaction
import androidx.work.*
import com.campusflow.app.CampusApp
import com.campusflow.app.data.Categories
import java.util.concurrent.TimeUnit

class ReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CampusApp
        return try {
            app.database.withTransaction {
                app.database.dao().insertCategories(Categories.defaults)
                app.repository.generate()
            }
            app.reminders.reconcile()
            Result.success()
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (_: Exception) { Result.retry() }
    }
}

object ReminderWork {
    fun request(context: Context) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
        // Before Android 12 expedited work needs a foreground service. Regular
        // work is sufficient here because the app schedules alarms immediately.
        if (android.os.Build.VERSION.SDK_INT >= 31) request.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        WorkManager.getInstance(context).enqueueUniqueWork("refresh-reminders", ExistingWorkPolicy.APPEND_OR_REPLACE,
            request.build())
    }
    fun maintain(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("maintain-calendar", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderWorker>(6, TimeUnit.HOURS).setInitialDelay(6, TimeUnit.HOURS).build())
    }
}
