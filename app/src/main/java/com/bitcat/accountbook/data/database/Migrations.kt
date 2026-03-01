package com.bitcat.accountbook.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
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

        db.execSQL("CREATE INDEX IF NOT EXISTS index_raw_inputs_created_at ON raw_inputs(created_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_raw_inputs_record_id ON raw_inputs(record_id)")
    }
}
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tags (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tags_name ON tags(name)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS record_tag (
                record_id INTEGER NOT NULL,
                tag_id INTEGER NOT NULL,
                PRIMARY KEY(record_id, tag_id),
                FOREIGN KEY(record_id) REFERENCES records(id) ON DELETE CASCADE,
                FOREIGN KEY(tag_id) REFERENCES tags(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_record_tag_tag_id ON record_tag(tag_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_record_tag_record_id ON record_tag(record_id)")
    }
}
