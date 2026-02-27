package com.bitcat.accountbook.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagFilterSheet(
    selected: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    val allTags = listOf("餐饮", "交通", "购物", "学习", "娱乐", "房租", "医疗")
    var current by remember { mutableStateOf(selected.toSet()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("选择标签", style = MaterialTheme.typography.titleMedium)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(onClick = { onConfirm(current.toList()) }, modifier = Modifier.weight(1f)) { Text("确定") }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
