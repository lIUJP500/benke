package com.bitcat.accountbook.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FilterBar(
    startDateText: String,
    endDateText: String,
    minAmountText: String,
    maxAmountText: String,
    selectedTags: List<String>,
    onClickDate: () -> Unit,
    onClickAmount: () -> Unit,
    onClickTags: () -> Unit,
    onClearAll: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("筛选条件", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = onClickDate,
                    label = {
                        Text(
                            if (startDateText.isBlank() && endDateText.isBlank())
                                "时间范围"
                            else "时间：$startDateText ~ $endDateText"
                        )
                    }
                )
                AssistChip(
                    onClick = onClickAmount,
                    label = {
                        Text(
                            if (minAmountText.isBlank() && maxAmountText.isBlank())
                                "金额区间"
                            else "金额：$minAmountText ~ $maxAmountText"
                        )
                    }
                )
                AssistChip(
                    onClick = onClickTags,
                    label = {
                        Text(
                            if (selectedTags.isEmpty())
                                "标签"
                            else "标签：${selectedTags.take(2).joinToString("、")}${if (selectedTags.size > 2) "…" else ""}"
                        )
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onClearAll) { Text("清空筛选") }
            }
        }
    }
}
