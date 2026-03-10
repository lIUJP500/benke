package com.bitcat.accountbook.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

object SettingsKeys {
    val RAW_RETENTION_DAYS = intPreferencesKey("raw_retention_days")
    val MONTHLY_BUDGET = doublePreferencesKey("monthly_budget")
    val WARNED_NEAR_MONTH = intPreferencesKey("warned_near_month")
    val WARNED_OVER_MONTH = intPreferencesKey("warned_over_month")
}

class SettingsDataStore(private val context: Context) {

    // 默认保留 30 天
    val rawRetentionDays: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[SettingsKeys.RAW_RETENTION_DAYS] ?: 30
    }
    val monthlyBudget: Flow<Double> = context.dataStore.data.map { prefs ->
        prefs[SettingsKeys.MONTHLY_BUDGET] ?: 500.0
    }
    val warnedNearMonth: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[SettingsKeys.WARNED_NEAR_MONTH] ?: 0
    }
    val warnedOverMonth: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[SettingsKeys.WARNED_OVER_MONTH] ?: 0
    }

    suspend fun setRawRetentionDays(days: Int) {
        context.dataStore.edit { prefs ->
            prefs[SettingsKeys.RAW_RETENTION_DAYS] = days
        }
    }

    suspend fun setMonthlyBudget(amount: Double) {
        context.dataStore.edit { prefs ->
            prefs[SettingsKeys.MONTHLY_BUDGET] = amount
        }
    }

    suspend fun markWarnedNear(monthId: Int) {
        context.dataStore.edit { prefs ->
            prefs[SettingsKeys.WARNED_NEAR_MONTH] = monthId
        }
    }

    suspend fun markWarnedOver(monthId: Int) {
        context.dataStore.edit { prefs ->
            prefs[SettingsKeys.WARNED_OVER_MONTH] = monthId
        }
    }
}
