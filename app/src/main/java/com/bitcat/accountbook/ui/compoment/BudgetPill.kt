package com.bitcat.accountbook.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
        progress >= 1f -> "已超预算"
        progress >= warningRatio.toFloat() -> "接近预算"
        else -> "正常"
    }

    val display = if (budget > 0) "¥${spent.format2()} / ¥${budget.format2()}" else "点击设置预算"

    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column {
                Text("本月预算", style = MaterialTheme.typography.labelLarge)
                Text(display, style = MaterialTheme.typography.bodySmall)
            }

            if (budget > 0) {
                LinearProgressIndicator(
                    progress = { min(progress, 1f) },
                    modifier = Modifier.width(72.dp)
                )
            }

            AssistChip(onClick = onClick, label = { Text(status) })
        }
    }
}

private fun Double.format2(): String = String.format("%.2f", this)
