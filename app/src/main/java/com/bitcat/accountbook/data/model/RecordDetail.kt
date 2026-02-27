package com.bitcat.accountbook.data.model

import androidx.room.Embedded
import androidx.room.Relation
import com.bitcat.accountbook.data.entity.RecordEntity
import com.bitcat.accountbook.data.entity.RawInputEntity
import com.bitcat.accountbook.data.entity.TagEntity

data class RecordDetail(
    @Embedded val record: RecordEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "record_id",
        entity = RawInputEntity::class
    )
    val raws: List<RawInputEntity>,

    // tags 你已有 crossRef 的话，通常用 Junction 方式写（如果你已有 RecordWithTags 就复用）
    // 这里建议：详情页直接用 recordDao.getRecordWithTags(...) + rawDao.getByRecordId(...) 拼起来（最稳）
)