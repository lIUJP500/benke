package com.bitcat.accountbook.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "records",
    indices = [
        Index(value = ["occurred_at"]),
        Index(value = ["amount"])
    ]
)
data class RecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,

    @ColumnInfo(name = "occurred_at") val occurredAt: Long, // epochMillis
    val title: String,
    val amount: Double,

    val currency: String = "CNY",

    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
