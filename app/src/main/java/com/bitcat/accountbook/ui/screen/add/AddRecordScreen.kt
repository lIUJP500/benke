package com.bitcat.accountbook.ui.screen.add

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.bitcat.accountbook.ai.AiParserRepository
import com.bitcat.accountbook.ai.GlmOcrRepository
import com.bitcat.accountbook.data.database.DatabaseProvider
import com.bitcat.accountbook.data.entity.RawInputEntity
import com.bitcat.accountbook.data.entity.RecordEntity
import com.bitcat.accountbook.ui.component.BudgetPill
import com.bitcat.accountbook.ui.component.BudgetSettingDialog
import com.bitcat.accountbook.ui.component.BudgetWarningDialog
import com.bitcat.accountbook.ui.component.ExtractedResultCard
import com.bitcat.accountbook.ui.component.InputMethod
import com.bitcat.accountbook.ui.component.InputMethodRow
import com.bitcat.accountbook.ui.component.RawInputCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/* ---------------- 解析状态 ---------------- */

private sealed interface ParseState {
    data object Idle : ParseState
    data object Parsing : ParseState
    data class Parsed(val data: ParsedRecordUi) : ParseState
    data class Error(val message: String) : ParseState
}

private data class ParsedRecordUi(
    val dateText: String,
    val title: String,
    val amountText: String,
    val tags: List<String>,
    val rawPreview: String
)

private data class ParsedAiMeta(
    val occurredAtMillis: Long? = null,
    val inputType: String? = null,
    val rawText: String? = null,
    val rawUri: String? = null
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddRecordScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }

    val db = remember { DatabaseProvider.get(appContext) }
    val recordDao = remember { db.recordDao() }
    val tagDao = remember { db.tagDao() }

    val allTags by tagDao.observeAllTags().collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedTagIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    /* ---------------- raw_inputs 状态 ---------------- */

    var rawType by remember { mutableStateOf("text") }                 // text/voice/photo/camera
    var rawUri by remember { mutableStateOf<String?>(null) }           // 图片/拍照 uri
    var rawTextToSave by remember { mutableStateOf<String?>(null) }    // text/voice/OCR 的文字
    var rawPreview by remember { mutableStateOf("") }                  // UI 预览（文本或 uri/摘要）

    /* ---------------- 预算预警 ---------------- */

    var monthlyBudget by rememberSaveable { mutableStateOf(500.0) }
    val (monthStart, monthEnd) = remember { currentMonthRangeMillis() }

    val monthlySpent by recordDao
        .observeSumInRange(monthStart, monthEnd)
        .collectAsStateWithLifecycle(initialValue = 0.0)

    var showBudgetSetting by remember { mutableStateOf(false) }
    var showWarn by remember { mutableStateOf(false) }
    var warnTitle by remember { mutableStateOf("") }
    var warnMsg by remember { mutableStateOf("") }
    var warnedNear by rememberSaveable { mutableStateOf(false) }
    var warnedOver by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(monthlySpent, monthlyBudget) {
        if (monthlyBudget <= 0) return@LaunchedEffect
        val ratio = monthlySpent / monthlyBudget

        if (ratio >= 1.0 && !warnedOver) {
            warnedOver = true
            warnTitle = "本月消费已超标"
            warnMsg = "你已消费 ¥${monthlySpent.format2()}，超过预算 ¥${monthlyBudget.format2()}。"
            showWarn = true
        } else if (ratio >= 0.9 && !warnedNear) {
            warnedNear = true
            warnTitle = "本月消费接近预算"
            warnMsg = "你已消费 ¥${monthlySpent.format2()}，接近预算 ¥${monthlyBudget.format2()}（≥90%）。"
            showWarn = true
        }
    }

    /* ---------------- 记账状态 ---------------- */

    var selectedMethod by remember { mutableStateOf(InputMethod.TEXT) }
    var rawText by remember { mutableStateOf("") } // 仍然保留：你现在的编辑/解析逻辑吃它
    var parseState by remember { mutableStateOf<ParseState>(ParseState.Idle) }

    var editDate by remember { mutableStateOf("") }
    var editTitle by remember { mutableStateOf("") }
    var editAmount by remember { mutableStateOf("") }
    var editTags by remember { mutableStateOf(listOf<String>()) }
    var parsedAiMeta by remember { mutableStateOf(ParsedAiMeta()) }

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    /* ---------------- 工具：统一设置 raw 状态 ---------------- */

    fun applyRaw(
        type: String,
        text: String? = null,
        uri: Uri? = null,
        preview: String = text ?: (uri?.toString() ?: "")
    ) {
        rawType = type
        rawTextToSave = text
        rawUri = uri?.toString()
        rawPreview = preview

        // 统一“解析入口”：让解析都从 rawText 开始（语音直接塞 text；图像走 OCR 后也会塞）
        rawText = text ?: ""
        selectedMethod = when (type) {
            "voice" -> InputMethod.VOICE
            "photo" -> InputMethod.PHOTO
            "camera" -> InputMethod.CAMERA
            else -> InputMethod.TEXT
        }
    }

    /* ---------------- 相机：FileProvider uri + 权限 ---------------- */

    fun createImageUri(): Uri {
        val dir = File(appContext.cacheDir, "images").apply { mkdirs() }
        val file = File(dir, "cam_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )
    }

    fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val uri = pendingCameraUri ?: return@rememberLauncherForActivityResult
            // 先保存 uri，真正解析时再 OCR（也可此处立刻 OCR，但会影响 UI 响应）
            applyRaw(type = "camera", uri = uri, preview = uri.toString())
        }
    }

    fun launchCamera() {
        val uri = createImageUri()
        pendingCameraUri = uri
        takePictureLauncher.launch(uri)
    }

    val requestCameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera()
        else Log.e("CAMERA", "Camera permission denied")
    }

    /* ---------------- 相册选图 ---------------- */

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            applyRaw(type = "photo", uri = uri, preview = uri.toString())
        }
    }

    /* ---------------- 语音识别 ---------------- */

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            .orEmpty()

        if (text.isNotBlank()) {
            applyRaw(type = "voice", text = text, preview = text)
        }
    }

    /* ---------------- 核心：构建“用于解析的文本” ---------------- */

    suspend fun buildParseText(): String {
        return when (rawType) {
            "text" -> rawText
            "voice" -> (rawTextToSave ?: rawText)
            "photo", "camera" -> {
                val u = rawUri?.let { Uri.parse(it) } ?: return ""
                // ✅ OCR 把图片变成文本
                GlmOcrRepository.recognizeText(context, u)
            }
            else -> rawText
        }.trim()
    }

    /* ---------------- UI ---------------- */
    fun startSmartParse() {
        parseState = ParseState.Parsing
        scope.launch {
            try {
                // 1) OCR / 语音 / 文本 -> 统一得到 parseText
                val parseText = buildParseText()
                if (parseText.isBlank()) {
                    parseState = ParseState.Error("没有识别到可解析的文本（图片可能太糊/无文字）")
                    return@launch
                }

                // 2) 如果是图像，把 OCR 文本写入 rawTextToSave（保证“原始输入文本化”可追溯）
                if (rawType == "photo" || rawType == "camera") {
                    rawTextToSave = parseText
                    rawText = parseText
                    rawPreview = parseText.take(120)
                }

                // 3) 调大模型
                val ai = AiParserRepository.parseExpense(
                    text = parseText,
                    inputType = rawType,
                    rawUri = rawUri
                )

                // 4) fallback：模型缺字段，用本地规则补
                val now = LocalDateTime.now()
                val amount = ai.amount ?: extractFirstNumber(parseText)
                val title = ai.title?.takeIf { it.isNotBlank() }
                    ?: extractTitle(parseText).ifBlank { "消费" }

                val occurredAtText = ai.occurredAt
                    ?: now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

                val parsed = ParsedRecordUi(
                    dateText = occurredAtText,
                    title = title,
                    amountText = amount?.toString() ?: "",
                    tags = ai.tags,
                    rawPreview = rawPreview.ifBlank { parseText.take(120) }
                )

                parseState = ParseState.Parsed(parsed)
                parsedAiMeta = ParsedAiMeta(
                    occurredAtMillis = ai.occurredAtMillis,
                    inputType = ai.inputType,
                    rawText = ai.rawText,
                    rawUri = ai.rawUri
                )
                editDate = parsed.dateText
                editTitle = parsed.title
                editAmount = parsed.amountText
                editTags = parsed.tags

            } catch (e: Exception) {
                parseState = ParseState.Error("解析失败：${e.message ?: "未知错误"}")
            }
        }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("记一笔") }) }
    ) { innerPadding ->

        if (showBudgetSetting) {
            BudgetSettingDialog(
                currentBudget = monthlyBudget,
                onDismiss = { showBudgetSetting = false },
                onSave = { newBudget ->
                    monthlyBudget = newBudget
                    warnedNear = false
                    warnedOver = false
                    showBudgetSetting = false
                }
            )
        }

        if (showWarn) {
            BudgetWarningDialog(
                title = warnTitle,
                message = warnMsg,
                onClose = { showWarn = false }
            )
        }

        Column(
            modifier = modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // 顶部预算
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                BudgetPill(
                    spent = monthlySpent,
                    budget = monthlyBudget,
                    onClick = { showBudgetSetting = true }
                )
            }

            // 输入方式切换（你原本的组件）
            InputMethodRow(
                selected = selectedMethod,
                onSelect = { selectedMethod = it }
            )

            RawInputCard(
                method = selectedMethod,
                rawText = rawText,
                onRawTextChange = { rawText = it },
                onSmartParse = {
                    startSmartParse()
                },
                onVoiceClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "说出你的消费，如：晚餐 40")
                    }
                    voiceLauncher.launch(intent)
                },
                onPickPhotoClick = {
                    pickImageLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onTakePhotoClick = {
                    if (hasCameraPermission()) launchCamera()
                    else requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                },
                photoPreviewUri = rawUri
            )

            when (val s = parseState) {

                is ParseState.Idle -> Unit

                is ParseState.Parsing -> {
                    Card {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("正在智能解析…（图片会先 OCR）")
                        }
                    }
                }

                is ParseState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("解析失败")
                            Text(s.message)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { parseState = ParseState.Idle }) {
                                    Text("取消")
                                }
                                Button(onClick = { startSmartParse() }) {
                                    Text("重试")
                                }
                            }
                        }
                    }
                }

                is ParseState.Parsed -> {

                    // 标签选择（保留）
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Text("标签")

                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                allTags.forEach { tag ->
                                    FilterChip(
                                        selected = selectedTagIds.contains(tag.id),
                                        onClick = {
                                            selectedTagIds =
                                                if (selectedTagIds.contains(tag.id))
                                                    selectedTagIds - tag.id
                                                else
                                                    selectedTagIds + tag.id
                                        },
                                        label = { Text(tag.name) }
                                    )
                                }
                            }
                        }
                    }

                    ExtractedResultCard(
                        dateText = editDate,
                        onDateTextChange = { editDate = it },
                        title = editTitle,
                        onTitleChange = { editTitle = it },
                        amountText = editAmount,
                        onAmountTextChange = { editAmount = it },
                        tags = editTags,
                        onTagsChange = { editTags = it },
                        rawPreview = rawPreview.ifBlank { rawText },
                        onReparse = { startSmartParse() },
                        onSave = {
                            val titleToSave = editTitle.trim()
                            val amountToSave = editAmount.trim().toDoubleOrNull()

                            if (titleToSave.isBlank() || amountToSave == null) {
                                parseState = ParseState.Error("请确认事项与金额填写正确")
                                return@ExtractedResultCard
                            }

                            val occurredAtMillis = parsedAiMeta.occurredAtMillis
                                ?: parseDateTimeToMillis(editDate)
                                ?: System.currentTimeMillis()

                            scope.launch(Dispatchers.IO) {
                                try {
                                    val nowMillis = System.currentTimeMillis()
                                    val parsedTagIds = editTags
                                        .map { it.trim() }
                                        .filter { it.isNotBlank() }
                                        .map { tagName ->
                                            tagDao.upsertByName(tagName).id
                                        }

                                    val finalTagIds = (selectedTagIds + parsedTagIds).toList()
                                    val id = recordDao.insertRecordWithTags(
                                        record = RecordEntity(
                                            occurredAt = occurredAtMillis,
                                            title = titleToSave,
                                            amount = amountToSave,
                                            createdAt = nowMillis,
                                            updatedAt = nowMillis
                                        ),
                                        tagIds = finalTagIds
                                    )

                                    db.rawInputDao().insert(
                                        RawInputEntity(
                                            recordId = id,
                                            inputType = parsedAiMeta.inputType ?: rawType,
                                            rawText = parsedAiMeta.rawText
                                                ?: rawTextToSave
                                                ?: rawText.takeIf { it.isNotBlank() },
                                            rawUri = parsedAiMeta.rawUri ?: rawUri,
                                            createdAt = nowMillis
                                        )
                                    )

                                    Log.d("DB", "Inserted record id=$id, raw type=$rawType")

                                } catch (e: Exception) {
                                    Log.e("DB", "Insert failed", e)
                                }
                            }

                            // UI 重置
                            rawType = "text"
                            rawUri = null
                            rawTextToSave = null
                            rawPreview = ""
                            rawText = ""
                            parsedAiMeta = ParsedAiMeta()
                            parseState = ParseState.Idle
                            selectedTagIds = emptySet()
                        }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

/* ---------------- 工具函数 ---------------- */

private fun currentMonthRangeMillis(): Pair<Long, Long> {
    val zone = ZoneId.systemDefault()
    val now = LocalDateTime.now(zone)

    val start = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
    val endExclusive = start.plusMonths(1)

    val startMillis = start.atZone(zone).toInstant().toEpochMilli()
    val endMillis = endExclusive.atZone(zone).toInstant().toEpochMilli() - 1
    return startMillis to endMillis
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

private fun extractFirstNumber(text: String): Double? {
    val regex = Regex("""(\d+(\.\d+)?)""")
    val m = regex.find(text) ?: return null
    return m.value.toDoubleOrNull()
}

private fun extractTitle(text: String): String {
    return text
        .replace(Regex("""[0-9]+(\.[0-9]+)?"""), "")
        .replace("￥", "")
        .replace("¥", "")
        .replace("元", "")
        .trim()
        .take(12)
}

private fun Double.format2(): String =
    String.format(Locale.getDefault(), "%.2f", this)
