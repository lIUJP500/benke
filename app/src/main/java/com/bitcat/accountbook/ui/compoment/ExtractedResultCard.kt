package com.bitcat.accountbook.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ExtractedResultCard(
    dateText: String,
    onDateTextChange: (String) -> Unit,
    title: String,
    onTitleChange: (String) -> Unit,
    amountText: String,
    onAmountTextChange: (String) -> Unit,
    tags: List<String>,
    onTagsChange: (List<String>) -> Unit,
    rawPreview: String,
    onReparse: () -> Unit,
    onSave: () -> Unit
) {
    var showRaw by remember { mutableStateOf(false) }
    var showTagSheet by remember { mutableStateOf(false) }

    if (showTagSheet) {
        TagSelectorBottomSheet(
            selected = tags,
            onDismiss = { showTagSheet = false },
            onConfirm = { newTags ->
                onTagsChange(newTags)
                showTagSheet = false
            }
        )
    }

    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("解析结果（可核对修改）", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = dateText,
                onValueChange = onDateTextChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("消费时间") },
                supportingText = { Text("后续可替换为日期/时间选择器") }
            )

            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("消费事项") },
                singleLine = true
            )

            OutlinedTextField(
                value = amountText,
                onValueChange = onAmountTextChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("消费金额") },
                prefix = { Text("¥") }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { showTagSheet = true },
                    label = { Text("选择/新增标签") }
                )
                if (tags.isNotEmpty()) {
                    Text("已选：${tags.joinToString("、")}", style = MaterialTheme.typography.bodySmall)
                }
            }

            TextButton(onClick = { showRaw = !showRaw }) {
                Text(if (showRaw) "收起原始输入" else "查看原始输入")
            }
            if (showRaw) {
                Text(rawPreview, style = MaterialTheme.typography.bodySmall)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onReparse,
                    modifier = Modifier.weight(1f)
                ) { Text("重新解析") }

                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f)
                ) { Text("保存") }
            }
        }
    }
}
