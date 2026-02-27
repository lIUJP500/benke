package com.bitcat.accountbook.ui.screen.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.bitcat.accountbook.data.database.DatabaseProvider
import com.bitcat.accountbook.data.entity.RecordEntity
import com.bitcat.accountbook.data.entity.TagEntity
import com.bitcat.accountbook.data.model.RecordWithTagsAndRaw
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class RangeMode(val label: String) { WEEK("本周"), MONTH("本月"), CUSTOM("自定义") }

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
    var selectedTagId by rememberSaveable { mutableStateOf<Long?>(null) } // null=全部

    // ====== 筛选条件 ======
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

    // ✅ 你现在已经写了 “带 raw 的列表”，这里保留
    val recordsFull by produceState(
        initialValue = emptyList<RecordWithTagsAndRaw>(),
        key1 = recordsWithTags
    ) {
        value = withContext(Dispatchers.IO) {
            recordsWithTags.map { item ->
                val raw = rawDao.getLatestByRecordId(item.record.id)
                RecordWithTagsAndRaw(
                    record = item.record,
                    tags = item.tags,
                    raw = raw
                )
            }
        }
    }

    val total by dao.observeTotalWithTagFiltered(
        startMillis, endMillis, minAmount, maxAmount, selectedTagId
    ).collectAsStateWithLifecycle(initialValue = 0.0)

    // ====== Sheet/Dialog 控制 ======
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    var showDateSheet by rememberSaveable { mutableStateOf(false) }

    // ====== 删除确认 ======
    var pendingDelete by remember { mutableStateOf<RecordEntity?>(null) }

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

        // ✅ 日期 BottomSheet
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

        // ✅ 筛选 BottomSheet
        if (showFilterSheet) {
            ModalBottomSheet(
                onDismissRequest = { showFilterSheet = false },
            ) {
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

        // ✅ 删除确认
        if (pendingDelete != null) {
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("删除这条记录？") },
                text = { Text("删除后无法恢复（原始输入也会一并清理）。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val target = pendingDelete!!
                            pendingDelete = null
                            scope.launch(Dispatchers.IO) {
                                dao.deleteById(target.id)
                            }
                        }
                    ) { Text("删除") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) { Text("取消") }
                }
            )
        }

        // ✅ 列表
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
                        SwipeRecordItem(
                            item = item,
                            onOpenDetail = { id -> navController.navigate("detail/$id") },
                            onDelete = { pendingDelete = item.record },
                            onEdit = {
                                // TODO: 你做 edit 页后改成 navController.navigate("edit/${item.record.id}")
                            }
                        )
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }
}

/* ======================== BottomSheet 内容 ======================== */

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
                        Text(customLabel, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
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
                Text("留空表示不限制。", style = MaterialTheme.typography.bodySmall)
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

/* ======================== Swipe Item（编辑 / 删除 + 点击进详情） ======================== */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SwipeRecordItem(
    item: RecordWithTagsAndRaw,
    onOpenDetail: (Long) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val record = item.record
    val tags = item.tags
    val rawPreview = item.raw?.rawText?.trim().orEmpty()
    val dt = remember(record.occurredAt) { formatDateTime(record.occurredAt) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> { onEdit(); false }
                SwipeToDismissBoxValue.EndToStart -> { onDelete(); false }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val target = dismissState.targetValue
            val isDelete = target == SwipeToDismissBoxValue.EndToStart
            val isEdit = target == SwipeToDismissBoxValue.StartToEnd

            val bgColor = when {
                isDelete -> MaterialTheme.colorScheme.errorContainer
                isEdit -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isDelete) Arrangement.End else Arrangement.Start
            ) {
                when {
                    isDelete -> {
                        Icon(Icons.Filled.Delete, contentDescription = "删除")
                        Spacer(Modifier.width(8.dp))
                        Text("删除")
                    }
                    isEdit -> {
                        Icon(Icons.Filled.Edit, contentDescription = "编辑")
                        Spacer(Modifier.width(8.dp))
                        Text("编辑")
                    }
                }
            }
        }
    ) {
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

                // 你说不做“带 raw 的列表页”，可以直接把这段删掉
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
}

/* ======================== 其余小组件 / 日期Sheet / 工具函数 ======================== */

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
    return "已选：${startDate.format(fmt)} ～ ${endDate.format(fmt)}"
}
