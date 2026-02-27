package com.bitcat.accountbook.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

object SettingsKeys {
    val RAW_RETENTION_DAYS = intPreferencesKey("raw_retention_days")
}

class SettingsDataStore(private val context: Context) {

    // 默认保留 30 天
    val rawRetentionDays: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[SettingsKeys.RAW_RETENTION_DAYS] ?: 30
    }

    suspend fun setRawRetentionDays(days: Int) {
        context.dataStore.edit { prefs ->
            prefs[SettingsKeys.RAW_RETENTION_DAYS] = days
        }
    }
}
