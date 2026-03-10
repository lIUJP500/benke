package com.bitcat.accountbook.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagSelectorBottomSheet(
    selected: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    val defaultTags = listOf("餐饮", "交通", "购物", "学习", "娱乐", "住房", "医疗")

    var current by remember { mutableStateOf(selected.toSet()) }
    var showCreateTag by remember { mutableStateOf(false) }
    var newTag by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("选择标签", style = MaterialTheme.typography.titleMedium)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                defaultTags.forEach { tag ->
                    FilterChip(
                        selected = current.contains(tag),
                        onClick = {
                            current = if (current.contains(tag)) current - tag else current + tag
                        },
                        label = { Text(tag) }
                    )
                }

                AssistChip(
                    onClick = { showCreateTag = true },
                    label = { Text("+") }
                )
            }

            if (showCreateTag) {
                OutlinedTextField(
                    value = newTag,
                    onValueChange = { newTag = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("标签名") },
                    placeholder = { Text("输入新标签") }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            showCreateTag = false
                            newTag = ""
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("取消") }

                    Button(
                        onClick = {
                            val name = newTag.trim()
                            if (name.isNotEmpty()) {
                                current = current + name
                                showCreateTag = false
                                newTag = ""
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("添加") }
                }
            }

            if (current.isNotEmpty()) {
                Text("已选：${current.joinToString("、")}", style = MaterialTheme.typography.bodySmall)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(onClick = { onConfirm(current.toList()) }, modifier = Modifier.weight(1f)) { Text("确定") }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
