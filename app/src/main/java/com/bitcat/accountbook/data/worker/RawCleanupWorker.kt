package com.bitcat.accountbook.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bitcat.accountbook.data.database.DatabaseProvider
import com.bitcat.accountbook.data.datastore.SettingsDataStore
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class RawCleanupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val settings = SettingsDataStore(applicationContext)
            val retentionDays = settings.rawRetentionDays.first().coerceAtLeast(1)

            val now = System.currentTimeMillis()
            val threshold = now - TimeUnit.DAYS.toMillis(retentionDays.toLong())

            val db = DatabaseProvider.get(applicationContext)
            val deleted = db.rawInputDao().deleteBefore(threshold)

            Log.d("CLEANUP", "Raw cleanup done. retentionDays=$retentionDays deleted=$deleted")
            Result.success()
        } catch (e: Exception) {
            Log.e("CLEANUP", "Raw cleanup failed", e)
            Result.retry()
        }
    }
}
