package com.bitcat.accountbook.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.min

@Composable
fun BudgetPill(
    spent: Double,
    budget: Double,
    modifier: Modifier = Modifier,
    warningRatio: Double = 0.9,
    onClick: () -> Unit
) {
    val progress = if (budget <= 0) 0f else (spent / budget).toFloat()
    val status = when {
        budget <= 0 -> "未设置"
        progress >= 1f -> "已超标"
        progress >= warningRatio.toFloat() -> "接近"
        else -> "正常"
    }

    val display = if (budget > 0) "¥${spent} / ¥${budget}" else "设置预算"

    Card(
        modifier = modifier
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column {
                Text("本月预算", style = MaterialTheme.typography.labelLarge)
                Text(display, style = MaterialTheme.typography.bodySmall)
            }

            // 一个小的进度条（可选但很直观）
            if (budget > 0) {
                LinearProgressIndicator(
                    progress = min(progress, 1f),
                    modifier = Modifier.width(64.dp)
                )
            }

            AssistChip(
                onClick = onClick,
                label = { Text(status) }
            )
        }
    }
}
