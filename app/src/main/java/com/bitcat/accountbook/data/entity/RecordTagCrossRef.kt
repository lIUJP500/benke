package com.bitcat.accountbook.data.entity

import androidx.room.*

@Entity(
    tableName = "record_tag",
    primaryKeys = ["record_id", "tag_id"],
    indices = [Index("tag_id"), Index("record_id")],
    foreignKeys = [
        ForeignKey(
            entity = RecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["record_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class RecordTagCrossRef(
    @ColumnInfo(name = "record_id") val recordId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long
)
