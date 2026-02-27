package com.bitcat.accountbook.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

//标签选择/新增
@OptIn(
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun TagSelectorBottomSheet(
    selected: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    // UI 演示：本地假数据（后面你再从数据库读）
    val allTags = listOf("餐饮", "交通", "购物", "学习", "娱乐", "房租", "医疗")

    var current by remember { mutableStateOf(selected.toSet()) }
    var newTag by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("选择标签", style = MaterialTheme.typography.titleMedium)

            // 选择已有标签
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                allTags.forEach { tag ->
                    FilterChip(
                        selected = current.contains(tag),
                        onClick = {
                            current = if (current.contains(tag)) current - tag else current + tag
                        },
                        label = { Text(tag) }
                    )
                }
            }

            // 新增标签（UI）
            OutlinedTextField(
                value = newTag,
                onValueChange = { newTag = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("新增标签（可选）") }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val t = newTag.trim()
                        if (t.isNotEmpty()) {
                            current = current + t
                            newTag = ""
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("加入") }

                Button(
                    onClick = { onConfirm(current.toList()) },
                    modifier = Modifier.weight(1f)
                ) { Text("确定") }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
