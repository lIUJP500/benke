package com.bitcat.accountbook.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {

        // Room/SQLite：外键默认可能未开启，但建表时写上外键约束是正确的
        // IF NOT EXISTS：即使你设备上已经存在 raw_inputs 表，也不会报错
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS raw_inputs (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                record_id INTEGER NOT NULL,
                input_type TEXT NOT NULL,
                raw_text TEXT,
                raw_uri TEXT,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(record_id) REFERENCES records(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        // 索引：与 @Entity(indices = ...) 对齐
        db.execSQL("CREATE INDEX IF NOT EXISTS index_raw_inputs_created_at ON raw_inputs(created_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_raw_inputs_record_id ON raw_inputs(record_id)")
    }
}
