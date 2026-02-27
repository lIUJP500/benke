package com.bitcat.accountbook.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

//结构化结果表单+标签+原始预览
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
                label = { Text("消费事项") }
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

            // 原始输入预览（可折叠）
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
