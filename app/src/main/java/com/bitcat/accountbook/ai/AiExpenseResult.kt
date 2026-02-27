package com.bitcat.accountbook.ai

import kotlinx.serialization.Serializable

@Serializable
data class AiExpenseResult(
    val occurredAt: String? = null,        // "yyyy-MM-dd HH:mm"
    val title: String? = null,
    val amount: Double? = null,
    val tags: List<String> = emptyList(),
    val confidence: Double? = null,
    val evidence: String? = null
)
