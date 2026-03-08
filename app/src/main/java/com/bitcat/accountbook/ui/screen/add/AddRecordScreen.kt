package com.bitcat.accountbook.ui.screen.add

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
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
import com.bitcat.accountbook.ai.OcrHelper
import com.bitcat.accountbook.ai.SherpaOnnxRecognizer
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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

private data class MethodDraft(
    val rawText: String = "",
    val rawUri: String? = null,
    val rawTextToSave: String? = null,
    val rawPreview: String = "",
    val parseState: ParseState = ParseState.Idle,
    val editDate: String = "",
    val editTitle: String = "",
    val editAmount: String = "",
    val editTags: List<String> = emptyList(),
    val parsedAiMeta: ParsedAiMeta = ParsedAiMeta(),
    val selectedTagIds: Set<Long> = emptySet()
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddRecordScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val sherpaRecognizer = remember(appContext) { SherpaOnnxRecognizer(appContext) }

    val db = remember { DatabaseProvider.get(appContext) }
    val recordDao = remember { db.recordDao() }
    val tagDao = remember { db.tagDao() }
    val rawDao = remember { db.rawInputDao() }

    val allTags by tagDao.observeAllTags().collectAsStateWithLifecycle(initialValue = emptyList())

    var rawType by remember { mutableStateOf("text") }
    var rawUri by remember { mutableStateOf<String?>(null) }
    var rawTextToSave by remember { mutableStateOf<String?>(null) }
    var rawPreview by remember { mutableStateOf("") }

    var selectedMethod by remember { mutableStateOf(InputMethod.TEXT) }
    var rawText by remember { mutableStateOf("") }
    var parseState by remember { mutableStateOf<ParseState>(ParseState.Idle) }

    var editDate by remember { mutableStateOf("") }
    var editTitle by remember { mutableStateOf("") }
    var editAmount by remember { mutableStateOf("") }
    var editTags by remember { mutableStateOf(listOf<String>()) }
    var parsedAiMeta by remember { mutableStateOf(ParsedAiMeta()) }
    var selectedTagIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    var methodDrafts by remember { mutableStateOf<Map<InputMethod, MethodDraft>>(emptyMap()) }

    var monthlyBudget by rememberSaveable { mutableStateOf(500.0) }
    val (monthStart, monthEnd) = remember { currentMonthRangeMillis() }
    val monthlySpent by recordDao.observeSumInRange(monthStart, monthEnd)
        .collectAsStateWithLifecycle(initialValue = 0.0)

    var showBudgetSetting by remember { mutableStateOf(false) }
    var showWarn by remember { mutableStateOf(false) }
    var warnTitle by remember { mutableStateOf("") }
    var warnMsg by remember { mutableStateOf("") }
    var warnedNear by rememberSaveable { mutableStateOf(false) }
    var warnedOver by rememberSaveable { mutableStateOf(false) }
    var recognizingVoice by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(monthlySpent, monthlyBudget) {
        if (monthlyBudget <= 0) return@LaunchedEffect
        val ratio = monthlySpent / monthlyBudget
        if (ratio >= 1.0 && !warnedOver) {
            warnedOver = true
            warnTitle = "本月消费已超预算"
            warnMsg = "已消费 ¥${monthlySpent.format2()}，超过预算 ¥${monthlyBudget.format2()}。"
            showWarn = true
        } else if (ratio >= 0.9 && !warnedNear) {
            warnedNear = true
            warnTitle = "本月消费接近预算"
            warnMsg = "已消费 ¥${monthlySpent.format2()}，接近预算 ¥${monthlyBudget.format2()}（≥90%）。"
            showWarn = true
        }
    }

    fun applyRaw(type: String, text: String? = null, uri: Uri? = null, preview: String = text ?: (uri?.toString() ?: "")) {
        rawType = type
        rawTextToSave = text
        rawUri = uri?.toString()
        rawPreview = preview
        if (type == "text" && text != null) rawText = text
        selectedMethod = when (type) {
            "voice" -> InputMethod.VOICE
            "photo" -> InputMethod.PHOTO
            "camera" -> InputMethod.CAMERA
            else -> InputMethod.TEXT
        }
    }

    fun applyMethod(method: InputMethod) {
        if (method == selectedMethod) return

        methodDrafts = methodDrafts + (
            selectedMethod to MethodDraft(
                rawText = rawText,
                rawUri = rawUri,
                rawTextToSave = rawTextToSave,
                rawPreview = rawPreview,
                parseState = parseState,
                editDate = editDate,
                editTitle = editTitle,
                editAmount = editAmount,
                editTags = editTags,
                parsedAiMeta = parsedAiMeta,
                selectedTagIds = selectedTagIds
            )
        )

        selectedMethod = method
        rawType = when (method) {
            InputMethod.TEXT -> "text"
            InputMethod.VOICE -> "voice"
            InputMethod.PHOTO -> "photo"
            InputMethod.CAMERA -> "camera"
        }

        val draft = methodDrafts[method]
        if (draft == null) {
            if (method != InputMethod.TEXT) rawText = ""
            rawUri = null
            rawTextToSave = null
            rawPreview = ""
            parseState = ParseState.Idle
            editDate = ""
            editTitle = ""
            editAmount = ""
            editTags = emptyList()
            parsedAiMeta = ParsedAiMeta()
            selectedTagIds = emptySet()
        } else {
            rawText = draft.rawText
            rawUri = draft.rawUri
            rawTextToSave = draft.rawTextToSave
            rawPreview = draft.rawPreview
            parseState = draft.parseState
            editDate = draft.editDate
            editTitle = draft.editTitle
            editAmount = draft.editAmount
            editTags = draft.editTags
            parsedAiMeta = draft.parsedAiMeta
            selectedTagIds = draft.selectedTagIds
        }
    }

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
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            val uri = pendingCameraUri ?: return@rememberLauncherForActivityResult
            applyRaw(type = "camera", uri = uri, preview = uri.toString())
        } else {
            Toast.makeText(context, "拍照失败，请重试", Toast.LENGTH_SHORT).show()
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
        if (granted) launchCamera() else Toast.makeText(context, "未授予相机权限", Toast.LENGTH_SHORT).show()
    }

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            applyRaw(type = "photo", uri = uri, preview = uri.toString())
        } else {
            Toast.makeText(context, "未选择图片", Toast.LENGTH_SHORT).show()
        }
    }

    val requestAudioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (recognizingVoice) return@rememberLauncherForActivityResult
            recognizingVoice = true
            scope.launch {
                val result = sherpaRecognizer.recognizeOnce()
                recognizingVoice = false
                result
                    .onSuccess { text -> applyRaw(type = "voice", text = text, preview = text) }
                    .onFailure { e ->
                        Toast.makeText(context, e.message ?: "语音识别失败", Toast.LENGTH_SHORT).show()
                    }
            }
        } else {
            Toast.makeText(context, "未授予麦克风权限", Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun buildParseText(): String {
        return when (rawType) {
            "text" -> rawText
            "voice" -> (rawTextToSave ?: rawText)
            "photo", "camera" -> {
                val u = rawUri?.let { Uri.parse(it) } ?: return ""
                OcrHelper.recognizeText(context, u)
            }
            else -> rawText
        }.trim()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("记一笔") }) }) { innerPadding ->
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                BudgetPill(spent = monthlySpent, budget = monthlyBudget, onClick = { showBudgetSetting = true })
            }

            InputMethodRow(selected = selectedMethod, onSelect = { applyMethod(it) })

            RawInputCard(
                method = selectedMethod,
                rawText = rawText,
                rawUri = rawUri,
                rawPreview = rawPreview,
                voiceRecognizing = recognizingVoice,
                onRawTextChange = { rawText = it },
                onSmartParse = {
                    parseState = ParseState.Parsing
                    scope.launch {
                        try {
                            val parseText = buildParseText()
                            if (parseText.isBlank()) {
                                parseState = ParseState.Error("没有识别到可解析文本")
                                return@launch
                            }

                            if (rawType == "photo" || rawType == "camera") {
                                rawTextToSave = parseText
                                rawPreview = parseText.take(120)
                            }

                            val ai = AiParserRepository.parseExpense(
                                text = parseText,
                                inputType = rawType,
                                rawUri = rawUri
                            )

                            val now = LocalDateTime.now()
                            val amount = ai.amount ?: extractFirstNumber(parseText)
                            val title = normalizeTitle(ai.title?.takeIf { it.isNotBlank() } ?: extractTitle(parseText).ifBlank { "消费" })
                            val occurredAtText = ai.occurredAt ?: now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

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
                            selectedTagIds = allTags
                                .filter { t -> parsed.tags.any { it.trim() == t.name } }
                                .map { it.id }
                                .toSet()
                        } catch (e: Exception) {
                            parseState = ParseState.Error("解析失败：${e.message ?: "未知错误"}")
                        }
                    }
                },
                onVoiceClick = {
                    if (recognizingVoice) return@RawInputCard
                    if (hasRecordAudioPermission()) {
                        recognizingVoice = true
                        scope.launch {
                            val result = sherpaRecognizer.recognizeOnce()
                            recognizingVoice = false
                            result
                                .onSuccess { text -> applyRaw(type = "voice", text = text, preview = text) }
                                .onFailure { e ->
                                    Toast.makeText(context, e.message ?: "语音识别失败", Toast.LENGTH_SHORT).show()
                                }
                        }
                    } else {
                        requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onPickPhotoClick = {
                    pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onTakePhotoClick = {
                    if (hasCameraPermission()) launchCamera()
                    else requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
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
                            Text("正在智能解析（图片会先 OCR）")
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
                                OutlinedButton(onClick = { parseState = ParseState.Idle }) { Text("取消") }
                                Button(onClick = { parseState = ParseState.Parsing }) { Text("重试") }
                            }
                        }
                    }
                }
                is ParseState.Parsed -> {
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Text("标签")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                allTags.forEach { tag ->
                                    FilterChip(
                                        selected = selectedTagIds.contains(tag.id),
                                        onClick = {
                                            selectedTagIds = if (selectedTagIds.contains(tag.id)) {
                                                selectedTagIds - tag.id
                                            } else {
                                                selectedTagIds + tag.id
                                            }
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
                        onTitleChange = { editTitle = normalizeTitle(it) },
                        amountText = editAmount,
                        onAmountTextChange = { editAmount = it },
                        tags = editTags,
                        onTagsChange = { newTags ->
                            editTags = newTags
                            selectedTagIds = allTags
                                .filter { t -> newTags.any { it.trim() == t.name } }
                                .map { it.id }
                                .toSet()
                        },
                        rawPreview = rawPreview.ifBlank { rawText },
                        onReparse = { parseState = ParseState.Parsing },
                        onSave = {
                            val titleToSave = normalizeTitle(editTitle)
                            val amountToSave = editAmount.trim().toDoubleOrNull()
                            if (titleToSave.isBlank() || amountToSave == null) {
                                parseState = ParseState.Error("请确认事项与金额填写正确")
                                return@ExtractedResultCard
                            }

                            val occurredAtMillis = parsedAiMeta.occurredAtMillis
                                ?: parseDateTimeToMillis(editDate)
                                ?: System.currentTimeMillis()

                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        val nowMillis = System.currentTimeMillis()

                                        val normalizedTagNames = editTags
                                            .map { it.trim() }
                                            .filter { it.isNotEmpty() }
                                            .distinct()

                                        val nameMatchedIds = allTags
                                            .filter { t -> normalizedTagNames.contains(t.name) }
                                            .map { it.id }

                                        val missingNames = normalizedTagNames
                                            .filterNot { name -> allTags.any { it.name == name } }

                                        val upsertedIds = missingNames.map { name ->
                                            tagDao.upsertByName(name).id
                                        }

                                        val finalTagIds = (selectedTagIds + nameMatchedIds + upsertedIds).toList()

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

                                        rawDao.insert(
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
                                    }

                                    Toast.makeText(context, "添加成功", Toast.LENGTH_SHORT).show()

                                    rawType = "text"
                                    rawUri = null
                                    rawTextToSave = null
                                    rawPreview = ""
                                    rawText = ""
                                    parseState = ParseState.Idle
                                    editDate = ""
                                    editTitle = ""
                                    editAmount = ""
                                    editTags = emptyList()
                                    parsedAiMeta = ParsedAiMeta()
                                    selectedTagIds = emptySet()
                                } catch (e: Exception) {
                                    parseState = ParseState.Error("保存失败：${e.message ?: "未知错误"}")
                                }
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun currentMonthRangeMillis(): Pair<Long, Long> {
    val zone = ZoneId.systemDefault()
    val now = LocalDateTime.now(zone)
    val start = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
    val endExclusive = start.plusMonths(1)
    return start.atZone(zone).toInstant().toEpochMilli() to (endExclusive.atZone(zone).toInstant().toEpochMilli() - 1)
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
        .replace("元", "")
        .replace("块", "")
        .trim()
        .take(12)
}

private fun normalizeTitle(title: String, maxChars: Int = 20): String =
    title.trim().take(maxChars)

private fun Double.format2(): String =
    String.format(Locale.getDefault(), "%.2f", this)
