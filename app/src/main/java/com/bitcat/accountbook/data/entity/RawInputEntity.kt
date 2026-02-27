package com.bitcat.accountbook.data.entity
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
@Entity(
    tableName = "raw_inputs",
    indices = [Index("created_at"), Index("record_id")],
    foreignKeys = [
        ForeignKey(
            entity = RecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["record_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class RawInputEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,

    @ColumnInfo(name = "record_id") val recordId: Long,
    @ColumnInfo(name = "input_type") val inputType: String, // text/voice/photo/image

    @ColumnInfo(name = "raw_text") val rawText: String? = null,
    @ColumnInfo(name = "raw_uri") val rawUri: String? = null,

    @ColumnInfo(name = "created_at") val createdAt: Long
)
