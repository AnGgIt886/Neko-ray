package com.neko.v2ray

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.neko.appupdater.AppUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateCheckWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "periodic_update_check"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val appUpdater = AppUpdater(applicationContext)
            appUpdater.configUrl = AppConfig.UWU_UPDATE_URL
            appUpdater.checkForUpdate(useNotificationIfAvailable = true)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
