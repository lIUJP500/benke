package com.bitcat.accountbook.ai

import kotlinx.serialization.Serializable

@Serializable
data class AiExpenseResult(
    val occurredAt: String? = null,        // "yyyy-MM-dd HH:mm"
    val occurredAtMillis: Long? = null,    // epochMillis（可选，便于直接入库）
    val title: String? = null,
    val amount: Double? = null,
    val currency: String? = "CNY",
    val tags: List<String> = emptyList(),
    val inputType: String? = null,         // text/voice/photo/camera
    val rawText: String? = null,
    val rawUri: String? = null,
    val confidence: Double? = null,
    val evidence: String? = null
)
