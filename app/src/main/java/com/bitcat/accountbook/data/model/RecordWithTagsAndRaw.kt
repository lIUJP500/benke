package com.bitcat.accountbook.data.model

import com.bitcat.accountbook.data.entity.RawInputEntity
import com.bitcat.accountbook.data.entity.RecordEntity
import com.bitcat.accountbook.data.entity.TagEntity

data class RecordWithTagsAndRaw(
    val record: RecordEntity,
    val tags: List<TagEntity>,
    val raw: RawInputEntity?
)
