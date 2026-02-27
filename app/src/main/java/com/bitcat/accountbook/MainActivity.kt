package com.bitcat.accountbook

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.bitcat.accountbook.data.database.DatabaseProvider
import com.bitcat.accountbook.data.entity.RecordEntity
import com.bitcat.accountbook.ui.AccountBookApp
import com.bitcat.accountbook.ui.theme.AccountBookTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.bitcat.accountbook.data.worker.RawCleanupWorker
import java.util.concurrent.TimeUnit
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        scheduleRawCleanup(this)

        setContent {
            AccountBookTheme {
                AccountBookApp()
            }
        }
    }
    private fun scheduleRawCleanup(context: android.content.Context) {
        val request = PeriodicWorkRequestBuilder<RawCleanupWorker>(
            1, TimeUnit.DAYS // 每天跑一次
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "raw_cleanup",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
