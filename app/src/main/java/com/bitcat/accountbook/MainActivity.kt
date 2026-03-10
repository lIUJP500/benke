package com.bitcat.accountbook

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.bitcat.accountbook.data.worker.BudgetAlertWorker
import com.bitcat.accountbook.data.worker.RawCleanupWorker
import com.bitcat.accountbook.ui.AccountBookApp
import com.bitcat.accountbook.ui.theme.AccountBookTheme
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ensureNotificationPermission()
        scheduleRawCleanup()
        scheduleBudgetAlerts()

        setContent {
            AccountBookTheme {
                AccountBookApp()
            }
        }
    }

    private fun scheduleRawCleanup() {
        val request = PeriodicWorkRequestBuilder<RawCleanupWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "raw_cleanup",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun scheduleBudgetAlerts() {
        val request = PeriodicWorkRequestBuilder<BudgetAlertWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "budget_alert",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1002
            )
        }
    }
}
