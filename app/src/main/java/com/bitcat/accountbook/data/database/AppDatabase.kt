package com.bitcat.accountbook.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.bitcat.accountbook.data.dao.RawInputDao
import com.bitcat.accountbook.data.dao.RecordDao
import com.bitcat.accountbook.data.dao.TagDao
import com.bitcat.accountbook.data.entity.RecordEntity
import com.bitcat.accountbook.data.entity.RawInputEntity
import com.bitcat.accountbook.data.entity.TagEntity
import com.bitcat.accountbook.data.entity.RecordTagCrossRef

@Database(entities = [RecordEntity::class, RawInputEntity::class,TagEntity::class,
    RecordTagCrossRef::class], version = 3, exportSchema = true)

abstract class AppDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun rawInputDao(): RawInputDao
    abstract fun tagDao(): TagDao


}

