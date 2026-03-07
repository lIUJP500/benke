package com.bitcat.accountbook.ui.screen.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.bitcat.accountbook.data.database.DatabaseProvider
import com.bitcat.accountbook.data.entity.RawInputEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecordDetailScreen(
    recordId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit
) {
    val ctx = LocalContext.current
    val appCtx = remember(ctx) { ctx.applicationContext }
    val db = remember { DatabaseProvider.get(appCtx) }
    val recordDao = remember { db.recordDao() }
    val rawDao = remember { db.rawInputDao() }

    val recordWithTags by recordDao.observeRecordWithTags(recordId)
        .collectAsStateWithLifecycle(initialValue = null)

    val raws by rawDao.observeByRecordId(recordId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val sortedRaws = remember(raws) { raws.sortedByDescending { it.createdAt } }
    val latestRaw = sortedRaws.firstOrNull()
    val historyRaws = remember(sortedRaws) { if (sortedRaws.size <= 1) emptyList() else sortedRaws.drop(1) }

    var showHistory by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账单详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(recordId) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "编辑")
                    }
                }
            )
        }
    ) { padding ->
        val item = recordWithTags
        if (item == null) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("记录不存在或已删除")
            }
            return@Scaffold
        }

        val record = item.record
        val tags = item.tags

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(record.title, style = MaterialTheme.typography.titleLarge)
                    Text("金额：¥${format2(record.amount)}", style = MaterialTheme.typography.titleMedium)
                    Text("时间：${formatDateTime(record.occurredAt)}", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("标签", style = MaterialTheme.typography.titleMedium)

                    if (tags.isEmpty()) {
                        Text("无标签", style = MaterialTheme.typography.bodySmall)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            tags.forEach { t ->
                                AssistChip(onClick = {}, label = { Text(t.name) })
                            }
                        }
                    }
                }
            }

            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("原始输入", style = MaterialTheme.typography.titleMedium)

                    if (latestRaw == null) {
                        Text("无原始输入", style = MaterialTheme.typography.bodySmall)
                    } else {
                        RawItem(
                            r = latestRaw,
                            onOpenUri = { openUri(ctx, it) }
                        )

                        if (historyRaws.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            TextButton(onClick = { showHistory = !showHistory }) {
                                Text(if (showHistory) "收起历史（${historyRaws.size}）" else "展开历史（${historyRaws.size}）")
                            }

                            if (showHistory) {
                                Spacer(Modifier.height(6.dp))
                                historyRaws.forEachIndexed { index, r ->
                                    HorizontalDivider()
                                    Spacer(Modifier.height(8.dp))
                                    RawItem(r = r, onOpenUri = { openUri(ctx, it) })
                                    if (index == historyRaws.lastIndex) Spacer(Modifier.height(4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RawItem(
    r: RawInputEntity,
    onOpenUri: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "类型：${r.inputType}  时间：${formatDateTime(r.createdAt)}",
            style = MaterialTheme.typography.bodySmall
        )

        r.rawText?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }

        r.rawUri?.takeIf { it.isNotBlank() }?.let { uriStr ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenUri(uriStr) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.OpenInNew, contentDescription = "打开", modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    text = uriStr,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            if (looksLikeImageUri(uriStr)) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(Uri.parse(uriStr))
                        .crossfade(true)
                        .build(),
                    contentDescription = "图片预览",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
            }
        }
    }
}

private fun openUri(ctx: android.content.Context, uriStr: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(uriStr)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(intent)
    }
}

private fun looksLikeImageUri(uriStr: String): Boolean {
    val lower = uriStr.lowercase(Locale.getDefault())
    return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
            lower.endsWith(".webp") || lower.contains("image") || lower.startsWith("content://")
}

private fun format2(v: Double): String =
    String.format(Locale.getDefault(), "%.2f", v)

private fun formatDateTime(epochMillis: Long): String {
    val z = ZoneId.systemDefault()
    val dt = Instant.ofEpochMilli(epochMillis).atZone(z).toLocalDateTime()
    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())
    return dt.format(fmt)
}
