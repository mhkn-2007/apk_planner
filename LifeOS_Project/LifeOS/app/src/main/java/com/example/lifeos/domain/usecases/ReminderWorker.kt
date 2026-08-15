package com.example.lifeos.domain.usecases

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.lifeos.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val title = inputData.getString("TITLE") ?: "Reminder"
        val message = inputData.getString("MESSAGE") ?: "You have a task to do."
        val notificationId = inputData.getInt("NOTIFICATION_ID", 0)

        NotificationHelper.showNotification(appContext, notificationId, title, message)

        return Result.success()
    }
}
