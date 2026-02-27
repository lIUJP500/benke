package com.bitcat.accountbook.data.model

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.bitcat.accountbook.data.entity.RecordEntity
import com.bitcat.accountbook.data.entity.RecordTagCrossRef
import com.bitcat.accountbook.data.entity.TagEntity

data class RecordWithTags(
    @Embedded val record: RecordEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = RecordTagCrossRef::class,
            parentColumn = "record_id",
            entityColumn = "tag_id"
        )
    )
    val tags: List<TagEntity>
)
