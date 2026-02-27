@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.bitcat.accountbook.ui.screen.edit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitcat.accountbook.data.database.DatabaseProvider
import com.bitcat.accountbook.data.entity.RecordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRecordScreen(
    recordId: Long,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val appCtx = remember(ctx) { ctx.applicationContext }
    val db = remember { DatabaseProvider.get(appCtx) }
    val recordDao = remember { db.recordDao() }
    val tagDao = remember { db.tagDao() }
    val rawDao = remember { db.rawInputDao() }

    val scope = rememberCoroutineScope()

    val recordWithTags by recordDao.observeRecordWithTags(recordId)
        .collectAsStateWithLifecycle(initialValue = null)

    val allTags by tagDao.observeAllTags()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // 表单 state（首次灌入一次）
    var inited by rememberSaveable(recordId) { mutableStateOf(false) }
    var editTitle by rememberSaveable(recordId) { mutableStateOf("") }
    var editAmount by rememberSaveable(recordId) { mutableStateOf("") }
    var editDateText by rememberSaveable(recordId) { mutableStateOf("") }
    var selectedTagIds by rememberSaveable(recordId) { mutableStateOf(setOf<Long>()) }

    // UI 状态
    var saving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var savedToast by remember { mutableStateOf(false) }

    // 删除确认
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(recordWithTags) {
        val item = recordWithTags ?: return@LaunchedEffect
        if (!inited) {
            inited = true
            editTitle = item.record.title
            editAmount = item.record.amount.toString()
            editDateText = formatDateTime(item.record.occurredAt)
            selectedTagIds = item.tags.map { it.id }.toSet()
        }
    }

    // 删除确认弹窗
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除这条账单？") },
            text = { Text("删除后无法恢复（标签关联与原始输入也会一并清理）。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        scope.launch {
                            saving = true
                            errorMsg = null
                            try {
                                withContext(Dispatchers.IO) {
                                    // 1) 清理 raw_inputs（如果你没做外键级联）
                                    rawDao.deleteByRecordId(recordId)
                                    // 2) 清理 record_tag（你已有 clearTagsForRecord / replaceTagsForRecord 也行）
                                    tagDao.clearTagsForRecord(recordId)
                                    // 3) 删除 records
                                    recordDao.deleteById(recordId)
                                }
                                saving = false
                                onBack()
                            } catch (e: Exception) {
                                saving = false
                                errorMsg = e.message ?: "删除失败"
                            }
                        }
                    }
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑账单") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除")
                    }
                }
            )
        }
    ) { padding ->
        val item = recordWithTags
        if (item == null) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { Text("记录不存在或已删除") }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (errorMsg != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("操作失败", style = MaterialTheme.typography.titleSmall)
                        Text(errorMsg!!, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (savedToast) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(12.dp)) { Text("已保存") }
                }
            }

            // 基本信息
            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("事项") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editAmount,
                        onValueChange = { editAmount = it.filterAmount() },
                        label = { Text("金额") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = editDateText,
                        onValueChange = { editDateText = it },
                        label = { Text("时间（yyyy-MM-dd HH:mm）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // 标签
            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("标签", style = MaterialTheme.typography.titleMedium)
                    if (allTags.isEmpty()) {
                        Text("暂无标签（去设置页创建）", style = MaterialTheme.typography.bodySmall)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            allTags.forEach { tag ->
                                FilterChip(
                                    selected = selectedTagIds.contains(tag.id),
                                    onClick = {
                                        selectedTagIds =
                                            if (selectedTagIds.contains(tag.id)) selectedTagIds - tag.id
                                            else selectedTagIds + tag.id
                                    },
                                    label = { Text(tag.name) }
                                )
                            }
                        }
                    }
                }
            }

            // 保存
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
                onClick = {
                    errorMsg = null
                    savedToast = false

                    val titleToSave = editTitle.trim()
                    val amountToSave = editAmount.trim().toDoubleOrNull()
                    val occurredAtMillis = parseDateTimeToMillis(editDateText)

                    if (titleToSave.isBlank()) {
                        errorMsg = "事项不能为空"
                        return@Button
                    }
                    if (amountToSave == null) {
                        errorMsg = "金额格式不正确"
                        return@Button
                    }
                    if (occurredAtMillis == null) {
                        errorMsg = "时间格式不正确（示例：2026-02-16 18:30）"
                        return@Button
                    }

                    scope.launch {
                        saving = true
                        try {
                            withContext(Dispatchers.IO) {
                                val now = System.currentTimeMillis()
                                val updated: RecordEntity = item.record.copy(
                                    title = titleToSave,
                                    amount = amountToSave,
                                    occurredAt = occurredAtMillis,
                                    updatedAt = now
                                )
                                recordDao.updateRecord(updated)
                                tagDao.replaceTagsForRecord(recordId, selectedTagIds.toList())
                            }
                            saving = false
                            savedToast = true
                        } catch (e: Exception) {
                            saving = false
                            errorMsg = e.message ?: "保存失败"
                        }
                    }
                }
            ) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("处理中…")
                } else {
                    Text("保存修改")
                }
            }
        }
    }
}

/* ======================== 工具函数 ======================== */

private fun String.filterAmount(): String {
    val filtered = this.filter { it.isDigit() || it == '.' }
    val firstDot = filtered.indexOf('.')
    return if (firstDot == -1) filtered else {
        val head = filtered.substring(0, firstDot + 1)
        val tail = filtered.substring(firstDot + 1).replace(".", "")
        head + tail
    }
}

private fun parseDateTimeToMillis(text: String): Long? {
    return try {
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())
        val ldt = LocalDateTime.parse(text.trim(), fmt)
        ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (_: Exception) {
        null
    }
}

private fun formatDateTime(epochMillis: Long): String {
    val z = ZoneId.systemDefault()
    val dt = Instant.ofEpochMilli(epochMillis).atZone(z).toLocalDateTime()
    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())
    return dt.format(fmt)
}
