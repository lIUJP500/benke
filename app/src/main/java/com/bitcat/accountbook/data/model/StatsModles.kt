package com.bitcat.accountbook.data.model

data class DayTotal(
    val day: String,     // "yyyy-MM-dd"
    val total: Double
)

data class WeekTotal(
    val yw: String,      // "yyyy-WW"（SQLite %Y-%W）
    val total: Double
)

data class TagTotal(
    val tagId: Long,
    val tagName: String,
    val total: Double
)
