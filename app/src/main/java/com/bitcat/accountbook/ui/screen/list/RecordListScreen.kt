package com.bitcat.accountbook.ui.screen.list

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.bitcat.accountbook.data.database.DatabaseProvider
import com.bitcat.accountbook.data.entity.TagEntity
import com.bitcat.accountbook.data.model.RecordWithTagsAndRaw
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class RangeMode(val label: String) {
    WEEK("本周"),
    MONTH("本月"),
    CUSTOM("自定义")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordListScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val db = remember { DatabaseProvider.get(appContext) }
    val dao = remember { db.recordDao() }
    val rawDao = remember { db.rawInputDao() }
    val tagDao = remember { db.tagDao() }
    val scope = rememberCoroutineScope()

    val allTags by tagDao.observeAllTags().collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedTagId by rememberSaveable { mutableStateOf<Long?>(null) }

    var rangeMode by rememberSaveable { mutableStateOf(RangeMode.MONTH) }
    var customStartMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var customEndMillis by rememberSaveable { mutableStateOf<Long?>(null) }

    var minText by rememberSaveable { mutableStateOf("") }
    var maxText by rememberSaveable { mutableStateOf("") }
    val minAmount = minText.trim().toDoubleOrNull()
    val maxAmount = maxText.trim().toDoubleOrNull()

    val (startMillis, endMillis) = remember(rangeMode, customStartMillis, customEndMillis) {
        when (rangeMode) {
            RangeMode.WEEK -> currentWeekRangeMillis()
            RangeMode.MONTH -> currentMonthRangeMillis()
            RangeMode.CUSTOM -> {
                val fallback = currentMonthRangeMillis()
                val s = customStartMillis ?: fallback.first
                val e = customEndMillis ?: fallback.second
                s to e
            }
        }
    }

    val recordsWithTags by dao.observeRecordsWithTagsFiltered(
        startMillis, endMillis, minAmount, maxAmount, selectedTagId
    ).collectAsStateWithLifecycle(initialValue = emptyList())

    val recordsFull by produceState(
        initialValue = emptyList<RecordWithTagsAndRaw>(),
        key1 = recordsWithTags
    ) {
        value = withContext(Dispatchers.IO) {
            val recordIds = recordsWithTags.map { it.record.id }
            val latestRawByRecordId = if (recordIds.isEmpty()) {
                emptyMap()
            } else {
                rawDao.getLatestByRecordIds(recordIds).associateBy { it.recordId }
            }
            recordsWithTags.map { item ->
                RecordWithTagsAndRaw(
                    record = item.record,
                    tags = item.tags,
                    raw = latestRawByRecordId[item.record.id]
                )
            }
        }
    }

    val total by dao.observeTotalWithTagFiltered(
        startMillis, endMillis, minAmount, maxAmount, selectedTagId
    ).collectAsStateWithLifecycle(initialValue = 0.0)

    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    var showDateSheet by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记录") },
                actions = {
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(Icons.Filled.FilterList, contentDescription = "筛选")
                    }
                }
            )
        },
        bottomBar = {
            TotalBar(
                count = recordsFull.size,
                total = total,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) { innerPadding ->

        if (showDateSheet) {
            DateRangePickerSheet(
                onDismiss = { showDateSheet = false },
                onConfirm = { startUtcMillis, endUtcMillisInclusive ->
                    customStartMillis = startUtcMillis
                    customEndMillis = endUtcMillisInclusive
                    showDateSheet = false
                }
            )
        }

        if (showFilterSheet) {
            ModalBottomSheet(onDismissRequest = { showFilterSheet = false }) {
                FilterSheetContent(
                    rangeMode = rangeMode,
                    allTags = allTags,
                    selectedTagId = selectedTagId,
                    onSelectTagId = { selectedTagId = it },
                    onRangeModeChange = { rangeMode = it },
                    customLabel = buildCustomRangeLabel(customStartMillis, customEndMillis),
                    onPickCustomRange = {
                        showFilterSheet = false
                        showDateSheet = true
                    },
                    minText = minText,
                    onMinChange = { minText = it.filterAmount() },
                    maxText = maxText,
                    onMaxChange = { maxText = it.filterAmount() },
                    onReset = {
                        rangeMode = RangeMode.MONTH
                        customStartMillis = null
                        customEndMillis = null
                        selectedTagId = null
                        minText = ""
                        maxText = ""
                    },
                    onDone = { showFilterSheet = false }
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        Box(
            modifier = modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (recordsFull.isEmpty()) {
                EmptyState(
                    text = "暂无记录\n点右上角可筛选",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(recordsFull, key = { it.record.id }) { item ->
                        RecordItem(
                            item = item,
                            onOpenDetail = { id -> navController.navigate("detail/$id") }
                        )
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheetContent(
    rangeMode: RangeMode,
    onRangeModeChange: (RangeMode) -> Unit,
    customLabel: String,
    onPickCustomRange: () -> Unit,
    minText: String,
    onMinChange: (String) -> Unit,
    maxText: String,
    onMaxChange: (String) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
    allTags: List<TagEntity>,
    selectedTagId: Long?,
    onSelectTagId: (Long?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("筛选", style = MaterialTheme.typography.titleMedium)

        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("时间范围", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RangeMode.values().forEach { m ->
                        FilterChip(
                            selected = (rangeMode == m),
                            onClick = { onRangeModeChange(m) },
                            label = { Text(m.label) }
                        )
                    }
                }
                if (rangeMode == RangeMode.CUSTOM) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            customLabel,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = onPickCustomRange) { Text("选日期") }
                    }
                }
            }
        }

        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("标签", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = (selectedTagId == null),
                        onClick = { onSelectTagId(null) },
                        label = { Text("全部") }
                    )
                    allTags.forEach { tag ->
                        FilterChip(
                            selected = (selectedTagId == tag.id),
                            onClick = { onSelectTagId(tag.id) },
                            label = { Text(tag.name) }
                        )
                    }
                }
            }
        }

        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("金额区间", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = minText,
                        onValueChange = onMinChange,
                        modifier = Modifier.weight(1f),
                        label = { Text("最低") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = maxText,
                        onValueChange = onMaxChange,
                        modifier = Modifier.weight(1f),
                        label = { Text("最高") },
                        singleLine = true
                    )
                }
                Text("留空表示不限制", style = MaterialTheme.typography.bodySmall)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text("重置") }
            Button(onClick = onDone, modifier = Modifier.weight(1f)) { Text("完成") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RecordItem(
    item: RecordWithTagsAndRaw,
    onOpenDetail: (Long) -> Unit
) {
    val record = item.record
    val tags = item.tags
    val rawPreview = item.raw?.rawText?.trim().orEmpty()
    val dt = remember(record.occurredAt) { formatDateTime(record.occurredAt) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenDetail(record.id) }
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(dt, style = MaterialTheme.typography.bodySmall)
                }
                Text("¥${record.amount.format2()}", style = MaterialTheme.typography.titleMedium)
            }

            if (tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { t -> AssistChip(onClick = {}, label = { Text(t.name) }) }
                }
            }

            if (rawPreview.isNotBlank()) {
                Text(
                    text = "原始：${rawPreview.take(60)}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TotalBar(count: Int, total: Double, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("共 $count 条", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Text("合计：¥${total.format2()}", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerSheet(
    onDismiss: () -> Unit,
    onConfirm: (startUtcMillis: Long, endUtcMillisInclusive: Long) -> Unit
) {
    val state = rememberDateRangePickerState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text("选择日期范围", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            DateRangePicker(
                state = state,
                showModeToggle = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(
                    enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
                    onClick = {
                        val start = state.selectedStartDateMillis!!
                        val end = state.selectedEndDateMillis!!
                        val endInclusive = end + (24L * 60 * 60 * 1000) - 1
                        onConfirm(start, endInclusive)
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("确定") }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun String.filterAmount(): String {
    val filtered = this.filter { it.isDigit() || it == '.' }
    val firstDot = filtered.indexOf('.')
    return if (firstDot == -1) filtered else {
        val head = filtered.substring(0, firstDot + 1)
        val tail = filtered.substring(firstDot + 1).replace(".", "")
        head + tail
    }
}

private fun Double.format2(): String =
    String.format(Locale.getDefault(), "%.2f", this)

private fun formatDateTime(epochMillis: Long): String {
    val z = ZoneId.systemDefault()
    val dt = Instant.ofEpochMilli(epochMillis).atZone(z).toLocalDateTime()
    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())
    return dt.format(fmt)
}

private fun currentMonthRangeMillis(): Pair<Long, Long> {
    val zone = ZoneId.systemDefault()
    val now = LocalDateTime.now(zone)
    val start = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
    val endExclusive = start.plusMonths(1)
    val startMillis = start.atZone(zone).toInstant().toEpochMilli()
    val endMillis = endExclusive.atZone(zone).toInstant().toEpochMilli() - 1
    return startMillis to endMillis
}

private fun currentWeekRangeMillis(): Pair<Long, Long> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val start = today.with(DayOfWeek.MONDAY).atStartOfDay()
    val endExclusive = start.plusDays(7)
    val startMillis = start.atZone(zone).toInstant().toEpochMilli()
    val endMillis = endExclusive.atZone(zone).toInstant().toEpochMilli() - 1
    return startMillis to endMillis
}

private fun buildCustomRangeLabel(startMillis: Long?, endMillis: Long?): String {
    if (startMillis == null || endMillis == null) return "未选择（当前使用本月范围）"
    val zone = ZoneId.systemDefault()
    val startDate = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
    val endDate = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate()
    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
    return "已选：${startDate.format(fmt)} ~ ${endDate.format(fmt)}"
}
