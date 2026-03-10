package com.bitcat.accountbook.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
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
    onReparse: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
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

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("解析结果", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = dateText,
                onValueChange = onDateTextChange,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                label = { Text("消费时间") },
                placeholder = { Text("例如 2026-03-08 12:30") },
                supportingText = { Text("格式：yyyy-MM-dd HH:mm") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                label = { Text("消费事项") },
                placeholder = { Text("例如 午餐、外卖、打车") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            OutlinedTextField(
                value = amountText,
                onValueChange = onAmountTextChange,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                label = { Text("消费金额") },
                placeholder = { Text("0.00") },
                prefix = { Text("¥") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { showTagSheet = true },
                    label = { Text("选择标签") }
                )
                if (tags.isNotEmpty()) {
                    Text("已选：${tags.joinToString("、")}", style = MaterialTheme.typography.bodySmall)
                }
            }

            OutlinedButton(
                onClick = onReparse,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) { Text("重新解析") }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("保存") }
            }
        }
    }
}
