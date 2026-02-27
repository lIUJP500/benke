package com.bitcat.accountbook.data.database

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bitcat.accountbook.data.database.MIGRATION_1_2

object DatabaseProvider {
    private const val TAG = "DB"

    @Volatile private var INSTANCE: AppDatabase? = null

    fun get(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: buildDb(context).also { db ->
                INSTANCE = db
            }
        }

    }

    private fun buildDb(context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "account_book.db"
        )
            .fallbackToDestructiveMigration()
            .build()

    }
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
            CREATE TABLE IF NOT EXISTS raw_inputs (
              id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              record_id INTEGER NOT NULL,
              input_type TEXT NOT NULL,
              raw_text TEXT,
              raw_uri TEXT,
              created_at INTEGER NOT NULL,
              FOREIGN KEY(record_id) REFERENCES records(id) ON DELETE CASCADE
            )
        """.trimIndent())

            db.execSQL("CREATE INDEX IF NOT EXISTS idx_raw_inputs_created_at ON raw_inputs(created_at)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_raw_inputs_record_id ON raw_inputs(record_id)")
        }
    }

}
