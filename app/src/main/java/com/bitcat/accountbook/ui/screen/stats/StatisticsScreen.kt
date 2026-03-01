package com.bitcat.accountbook.ui.screen.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitcat.accountbook.data.database.DatabaseProvider
import com.bitcat.accountbook.data.model.DayTotal
import com.bitcat.accountbook.data.model.TagTotal
import com.bitcat.accountbook.data.model.WeekTotal
import kotlin.math.*

private enum class StatMode(val label: String) { WEEK("周"), MONTH("月"), TAG("标签") }
private val DEFAULT_TAG_NAMES = listOf("餐饮", "交通", "购物", "学习", "娱乐", "房租", "医疗")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen() {
    var mode by rememberSaveable { mutableStateOf(StatMode.WEEK) }

    val ctx = LocalContext.current
    val appCtx = remember(ctx) { ctx.applicationContext }
    val db = remember { DatabaseProvider.get(appCtx) }
    val recordDao = remember { db.recordDao() }
    val tagDao = remember { db.tagDao() }

    // 真实标签（用于 TAG 模式选择）
    val allTagEntities by tagDao.observeAllTags().collectAsStateWithLifecycle(initialValue = emptyList())
    val allTags = remember(allTagEntities) {
        (DEFAULT_TAG_NAMES + allTagEntities.map { it.name }).distinct()
    }
    var selectedTagId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedTagName by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(allTagEntities, allTags) {
        if (selectedTagName.isBlank() && allTags.isNotEmpty()) {
            selectedTagName = allTags.first()
        }
        if (selectedTagId == null && selectedTagName.isNotBlank()) {
            selectedTagId = allTagEntities.firstOrNull { it.name == selectedTagName }?.id
        }
    }
    val (weekStartMillis, weekEndMillis) = remember { currentWeekRangeMillis() }
    val (monthStartMillis, monthEndMillis) = remember { currentMonthRangeMillis() }
    val (tagStartMillis, tagEndMillis) = remember { last12WeeksRangeMillis() }

    // 根据模式决定统计时间范围
    val (startMillis, endMillis) = when (mode) {
        StatMode.WEEK -> weekStartMillis to weekEndMillis
        StatMode.MONTH -> monthStartMillis to monthEndMillis
        StatMode.TAG -> tagStartMillis to tagEndMillis

    }

    // ====== 1) 折线数据（真数据） ======
    val weekDaily by recordDao.observeDailyTotals(weekStartMillis, weekEndMillis)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val monthDaily by recordDao.observeDailyTotals(monthStartMillis, monthEndMillis)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val tagWeekly by if (selectedTagId != null) {
        recordDao.observeWeeklyTotalsByTag(tagStartMillis, tagEndMillis, selectedTagId!!)
            .collectAsStateWithLifecycle(initialValue = emptyList())
    } else {
        remember { mutableStateOf(emptyList()) }
    }

    val currentTrend: List<Double> = remember(mode, weekDaily, monthDaily, tagWeekly, weekStartMillis, monthStartMillis, monthEndMillis, tagStartMillis) {
        when (mode) {
            StatMode.WEEK -> fillDailySeries7Days(weekDaily, weekStartMillis)
            StatMode.MONTH -> fillDailySeriesMonth(monthDaily, monthStartMillis, monthEndMillis)
            StatMode.TAG -> fillWeeklySeries12Weeks(tagWeekly, tagStartMillis)
        }
    }

    // ====== 2) 饼图数据（真数据） ======
    val tagTotals by recordDao.observeTagTotalsInRange(startMillis, endMillis)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // KPI
    val total = currentTrend.sum()
    val avg = if (currentTrend.isNotEmpty()) total / currentTrend.size else 0.0
    val maxOne = currentTrend.maxOrNull() ?: 0.0

    val scroll = rememberScrollState()

    Scaffold(topBar = { TopAppBar(title = { Text("统计") }) }) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1) 维度切换：周 / 月 / 标签
            StatModeSwitcher(mode = mode, onModeChange = { mode = it })

            // 2) 标签模式：选择标签（用你数据库 tags）
            if (mode == StatMode.TAG) {
                TagPickerRow(
                    tags = allTags,
                    selected = selectedTagName,
                    onSelect = { name ->
                        selectedTagName = name
                        selectedTagId = allTagEntities.firstOrNull { it.name == name }?.id
                    }
                )
            }

            // 3) KPI
            KpiRow(total = total, avg = avg, maxOne = maxOne, mode = mode)

            // 4) 折线图
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = when (mode) {
                            StatMode.WEEK -> "本周支出趋势（按天）"
                            StatMode.MONTH -> "本月支出趋势（按天）"
                            StatMode.TAG -> "标签「$selectedTagName」趋势（近12周）"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )

                    LineChart(
                        values = currentTrend,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }
            }

            // 5) 饼图：按标签（同一时间范围）
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = when (mode) {
                            StatMode.WEEK -> "消费分布（本周按标签）"
                            StatMode.MONTH -> "消费分布（本月按标签）"
                            StatMode.TAG -> "消费分布（近12周按标签）"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PieChart(
                            pairs = tagTotals.map { it.tagName to it.total },
                            modifier = Modifier.size(160.dp)
                        )
                        PieLegend(
                            pairs = tagTotals.map { it.tagName to it.total },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/* ================== 顶部：维度切换 ================== */

@Composable
private fun StatModeSwitcher(
    mode: StatMode,
    onModeChange: (StatMode) -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("统计维度", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatMode.values().forEach { m ->
                    FilterChip(
                        selected = mode == m,
                        onClick = { onModeChange(m) },
                        label = { Text(m.label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TagPickerRow(
    tags: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("选择标签", style = MaterialTheme.typography.titleMedium)
            // 不用 FlowRow 避免 ExperimentalLayoutApi：用横向可滚动
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tags.forEach { tag ->
                    FilterChip(
                        selected = selected == tag,
                        onClick = { onSelect(tag) },
                        label = { Text(tag) }
                    )
                }
            }
        }
    }
}

/* ================== KPI ================== */

@Composable
private fun KpiRow(
    total: Double,
    avg: Double,
    maxOne: Double,
    mode: StatMode
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        KpiCard(
            title = when (mode) {
                StatMode.WEEK -> "本周总额"
                StatMode.MONTH -> "本月总额"
                StatMode.TAG -> "累计"
            },
            value = "¥${total.round2()}",
            modifier = Modifier.weight(1f)
        )
        KpiCard(title = "均值", value = "¥${avg.round2()}", modifier = Modifier.weight(1f))
        KpiCard(title = "峰值", value = "¥${maxOne.round2()}", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun KpiCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodySmall)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

/* ================== 折线图 ================== */

@Composable
private fun LineChart(values: List<Double>, modifier: Modifier = Modifier) {
    if (values.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("暂无数据") }
        return
    }

    val maxV = max(1.0, values.maxOrNull() ?: 1.0)
    val minV = values.minOrNull() ?: 0.0

    val axisColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val primary = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val pl = 24f
        val pt = 12f
        val pb = 24f
        val pr = 12f

        val chartW = w - pl - pr
        val chartH = h - pt - pb

        drawLine(axisColor, Offset(pl, pt), Offset(pl, pt + chartH), 2f)
        drawLine(axisColor, Offset(pl, pt + chartH), Offset(pl + chartW, pt + chartH), 2f)

        val step = if (values.size == 1) 0f else chartW / (values.size - 1)
        fun yOf(v: Double): Float {
            val norm = if (maxV == minV) 0.0 else (v - minV) / (maxV - minV)
            return (pt + chartH - norm * chartH).toFloat()
        }

        val path = Path()
        values.forEachIndexed { i, v ->
            val x = pl + step * i
            val y = yOf(v)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            drawCircle(color = primary, radius = 5f, center = Offset(x, y))
        }

        drawPath(path = path, color = primary, style = Stroke(width = 3f))
    }
}

/* ================== 饼图 ================== */

@Composable
private fun PieChart(pairs: List<Pair<String, Double>>, modifier: Modifier = Modifier) {
    val total = pairs.sumOf { it.second }
    if (pairs.isEmpty() || total <= 0) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("暂无数据") }
        return
    }

    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f),
    )

    Canvas(modifier = modifier) {
        val diameter = min(size.width, size.height)
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val pieSize = Size(diameter, diameter)

        var startAngle = -90f
        pairs.forEachIndexed { index, pair ->
            val sweep = (pair.second / total * 360f).toFloat()
            drawArc(
                color = colors[index % colors.size],
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = true,
                topLeft = topLeft,
                size = pieSize
            )
            startAngle += sweep
        }
    }
}

@Composable
private fun PieLegend(pairs: List<Pair<String, Double>>, modifier: Modifier = Modifier) {
    val total = pairs.sumOf { it.second }
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f),
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        pairs.forEachIndexed { index, (label, value) ->
            val ratio = if (total <= 0) 0 else ((value / total) * 100).roundToInt()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(modifier = Modifier.size(10.dp)) { drawRect(colors[index % colors.size]) }
                Spacer(Modifier.width(8.dp))
                Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text(
                    text = "¥${value.round2()}  $ratio%",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

/* ================== 把数据库 groupBy 的结果补齐成固定长度序列 ================== */

// WEEK：7天（从周一开始）
private fun fillDailySeries7Days(raw: List<DayTotal>, weekStartMillis: Long): List<Double> {
    val map = raw.associate { it.day to it.total }
    val days = daysFromMillis(weekStartMillis, 7)
    return days.map { d -> map[d].orZero() }
}

// MONTH：按当月天数补齐
private fun fillDailySeriesMonth(raw: List<DayTotal>, startMillis: Long, endMillis: Long): List<Double> {
    val map = raw.associate { it.day to it.total }
    val days = daysBetweenMillisInclusive(startMillis, endMillis)
    return days.map { d -> map[d].orZero() }
}

// TAG：近12周（按周补齐）
private fun fillWeeklySeries12Weeks(raw: List<WeekTotal>, startMillis: Long): List<Double> {
    val map = raw.associate { it.yw to it.total }
    val weeks = yearWeeksFromStart12(startMillis) // 12个 "yyyy-WW"
    return weeks.map { w -> map[w].orZero() }
}

private fun Double?.orZero() = this ?: 0.0

/* ================== 时间范围工具（毫秒） ================== */

// 本周（周一 00:00 - 周日 23:59:59）
private fun currentWeekRangeMillis(): Pair<Long, Long> {
    val now = java.time.ZonedDateTime.now()
    val start = now.with(java.time.DayOfWeek.MONDAY).toLocalDate().atStartOfDay(now.zone)
    val endExclusive = start.plusDays(7)
    return start.toInstant().toEpochMilli() to (endExclusive.toInstant().toEpochMilli() - 1)
}

// 本月
private fun currentMonthRangeMillis(): Pair<Long, Long> {
    val now = java.time.ZonedDateTime.now()
    val start = now.withDayOfMonth(1).toLocalDate().atStartOfDay(now.zone)
    val endExclusive = start.plusMonths(1)
    return start.toInstant().toEpochMilli() to (endExclusive.toInstant().toEpochMilli() - 1)
}

// 近12周（从当前周往前推11周的周一）
private fun last12WeeksRangeMillis(): Pair<Long, Long> {
    val now = java.time.ZonedDateTime.now()
    val thisWeekStart = now.with(java.time.DayOfWeek.MONDAY).toLocalDate().atStartOfDay(now.zone)
    val start = thisWeekStart.minusWeeks(11)
    val endExclusive = thisWeekStart.plusWeeks(1)
    return start.toInstant().toEpochMilli() to (endExclusive.toInstant().toEpochMilli() - 1)
}

/* ================== 日期/周序列生成（用于补齐0值） ================== */

private fun daysFromMillis(startMillis: Long, count: Int): List<String> {
    val zone = java.time.ZoneId.systemDefault()
    val start = java.time.Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
    return (0 until count).map { i -> start.plusDays(i.toLong()).toString() } // yyyy-MM-dd
}

private fun daysBetweenMillisInclusive(startMillis: Long, endMillis: Long): List<String> {
    val zone = java.time.ZoneId.systemDefault()
    val start = java.time.Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
    val end = java.time.Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate()
    val days = java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt()
    return (0..days).map { i -> start.plusDays(i.toLong()).toString() }
}

// 生成12个 year-week（匹配 SQLite strftime('%Y-%W')）
private fun yearWeeksFromStart12(startMillis: Long): List<String> {
    val zone = java.time.ZoneId.systemDefault()
    val startDate = java.time.Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
    return (0 until 12).map { i ->
        val d = startDate.plusWeeks(i.toLong())
        val week = d.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())
        val year = d.get(java.time.temporal.WeekFields.ISO.weekBasedYear())
        // SQLite %W 是 00-53（周一作为一周开始），这里用两位补齐
        "%04d-%02d".format(year, week)
    }
}

/* ================== 工具函数 ================== */
private fun Double.round2(): String {
    val v = (this * 100).roundToInt() / 100.0
    return String.format("%.2f", v)
}
