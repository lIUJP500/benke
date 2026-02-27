package com.bitcat.accountbook.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SummaryCard(
    count: Int,
    totalAmountText: String
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("查询结果", style = MaterialTheme.typography.titleMedium)
                Text("共 $count 笔", style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("消费总额", style = MaterialTheme.typography.bodySmall)
                Text(totalAmountText, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}
