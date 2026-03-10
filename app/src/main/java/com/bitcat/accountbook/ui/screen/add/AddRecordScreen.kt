package com.bitcat.accountbook.ui.screen.add

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.bitcat.accountbook.ai.AiParserRepository
import com.bitcat.accountbook.ai.OcrHelper
import com.bitcat.accountbook.ai.SherpaOnnxRecognizer
import com.bitcat.accountbook.data.database.DatabaseProvider
import com.bitcat.accountbook.data.datastore.SettingsDataStore
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

@OptIn(ExperimentalMaterial3Api::class)
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
    val settings = remember { SettingsDataStore(appContext) }

    val allTags by tagDao.observeAllTags().collectAsStateWithLifecycle(initialValue = emptyList())
    val monthlyBudget by settings.monthlyBudget.collectAsStateWithLifecycle(initialValue = 500.0)

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
    var voiceLevel by remember { mutableStateOf(0f) }
    var stopVoiceRequested by remember { mutableStateOf(false) }

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
        if (selectedMethod == InputMethod.VOICE && recognizingVoice) {
            stopVoiceRequested = true
        }

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
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (cameraIntent.resolveActivity(context.packageManager) == null) {
            Toast.makeText(context, "当前模拟器没有可用相机应用，请用“上传图片”", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = createImageUri()
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "未找到相机应用，请使用上传图片", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "打开相机失败：${e.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
        }
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
            stopVoiceRequested = false
            recognizingVoice = true
            rawType = "voice"
            scope.launch {
                val result = sherpaRecognizer.recognizeUntilStopped(
                    shouldStop = { stopVoiceRequested },
                    onLevel = { level -> voiceLevel = level },
                    onPartialText = { partial ->
                        rawType = "voice"
                        rawText = partial
                        rawTextToSave = partial
                        rawPreview = partial
                    }
                )
                recognizingVoice = false
                voiceLevel = 0f
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
            else -> rawText
        }.trim()
    }

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("记一笔") }) }) { innerPadding ->
        if (showBudgetSetting) {
            BudgetSettingDialog(
                currentBudget = monthlyBudget,
                onDismiss = { showBudgetSetting = false },
                onSave = { newBudget ->
                    scope.launch {
                        settings.setMonthlyBudget(newBudget)
                        warnedNear = false
                        warnedOver = false
                        showBudgetSetting = false
                    }
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                BudgetPill(spent = monthlySpent, budget = monthlyBudget, onClick = { showBudgetSetting = true })
            }

            InputMethodRow(selected = selectedMethod, onSelect = { applyMethod(it) })

            RawInputCard(
                method = selectedMethod,
                rawText = rawText,
                rawUri = rawUri,
                rawPreview = rawPreview,
                voiceRecognizing = recognizingVoice,
                voiceLevel = voiceLevel,
                onRawTextChange = {
                    rawText = it
                    if (selectedMethod == InputMethod.VOICE) {
                        rawTextToSave = it
                        rawPreview = it
                    }
                },
                onSmartParse = {
                    parseState = ParseState.Parsing
                    scope.launch {
                        try {
                            val now = LocalDateTime.now()
                            val (parseText, ai) = when (rawType) {
                                "text" -> {
                                    val text = rawText.trim()
                                    if (text.isBlank()) {
                                        parseState = ParseState.Error("没有识别到可解析文本")
                                        return@launch
                                    }
                                    text to AiParserRepository.parseExpenseLocal(
                                        text = text,
                                        inputType = "text",
                                        rawUri = null
                                    )
                                }
                                "voice" -> {
                                    val text = (rawTextToSave ?: rawText).trim()
                                    if (text.isBlank()) {
                                        parseState = ParseState.Error("没有识别到可解析文本")
                                        return@launch
                                    }
                                    val cloud = withContext(Dispatchers.IO) {
                                        AiParserRepository.parseExpenseCloud(
                                            context = null,
                                            text = text,
                                            inputType = "voice",
                                            rawUri = null
                                        )
                                    }
                                    text to cloud
                                }
                                "photo", "camera" -> {
                                    val u = rawUri?.let { Uri.parse(it) } ?: run {
                                        parseState = ParseState.Error("未找到图片")
                                        return@launch
                                    }
                                    val ocrText = withContext(Dispatchers.IO) {
                                        OcrHelper.recognizeTextSmart(context, u)
                                    }.trim()
                                    if (ocrText.isBlank()) {
                                        parseState = ParseState.Error("未识别到可用文本")
                                        return@launch
                                    }
                                    rawTextToSave = ocrText
                                    rawPreview = ocrText.take(120)

                                    val cloud = withContext(Dispatchers.IO) {
                                        AiParserRepository.parseExpenseCloud(
                                            context = context,
                                            text = ocrText,
                                            inputType = rawType,
                                            rawUri = rawUri
                                        )
                                    }
                                    ocrText to cloud
                                }
                                else -> {
                                    val text = buildParseText()
                                    text to AiParserRepository.parseExpenseLocal(text, inputType = rawType, rawUri = rawUri)
                                }
                            }

                            val amount = chooseBestAmount(ai.amount, parseText)
                            val title = normalizeTitle(
                                sanitizeTitle(
                                    ai.title?.takeIf { it.isNotBlank() } ?: extractTitle(parseText).ifBlank { "消费" }
                                )
                            )
                            val tags = if (ai.tags.isNotEmpty()) ai.tags else inferTags(parseText, title)
                            val occurredAtText = ai.occurredAt ?: now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

                            val parsed = ParsedRecordUi(
                                dateText = occurredAtText,
                                title = title,
                                amountText = amount?.toString() ?: "",
                                tags = tags,
                                rawPreview = rawPreview.ifBlank { parseText.take(120) }
                            )

                            parseState = ParseState.Parsed(parsed)
                            parsedAiMeta = ParsedAiMeta(
                                occurredAtMillis = ai.occurredAtMillis,
                                inputType = ai.inputType ?: rawType,
                                rawText = ai.rawText ?: parseText,
                                rawUri = ai.rawUri ?: rawUri
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
                    if (recognizingVoice) {
                        stopVoiceRequested = true
                        return@RawInputCard
                    }
                    if (hasRecordAudioPermission()) {
                        stopVoiceRequested = false
                        recognizingVoice = true
                        rawType = "voice"
                        scope.launch {
                            val result = sherpaRecognizer.recognizeUntilStopped(
                                shouldStop = { stopVoiceRequested },
                                onLevel = { level -> voiceLevel = level },
                                onPartialText = { partial ->
                                    rawType = "voice"
                                    rawText = partial
                                    rawTextToSave = partial
                                    rawPreview = partial
                                }
                            )
                            recognizingVoice = false
                            voiceLevel = 0f
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
                    Card(shape = RoundedCornerShape(20.dp)) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("请稍后....")
                        }
                    }
                }
                is ParseState.Error -> {
                    Card(
                        shape = RoundedCornerShape(20.dp),
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
                        onReparse = { parseState = ParseState.Parsing },
                        onCancel = { parseState = ParseState.Idle },
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

private fun extractAmount(text: String): Double? {
    extractPreferredPaidAmount(text)?.let { return it }
    extractHeadlineAmount(text)?.let { return it }

    val arabic = extractFirstNumber(text)
    if (arabic != null) return arabic

    // e.g. "二十五元" / "三块五" / "十二块钱"
    val cnAmount = Regex("""([零一二两三四五六七八九十百千万点]+)\s*(元|块|块钱|人民币)?""")
        .findAll(text)
        .mapNotNull { it.groups[1]?.value }
        .mapNotNull { parseChineseNumber(it) }
        .firstOrNull()
    return cnAmount
}

private fun chooseBestAmount(aiAmount: Double?, text: String): Double? {
    val normalizedAi = aiAmount
        ?.let { kotlin.math.abs(it) }
        ?.takeIf(::isReasonableAmount)

    val localAmount = extractAmount(text)?.takeIf(::isReasonableAmount)

    return when {
        localAmount == null -> normalizedAi
        normalizedAi == null -> localAmount
        localAmount <= 9999 && normalizedAi > localAmount * 10 -> localAmount
        else -> normalizedAi
    }
}

private fun isReasonableAmount(amount: Double): Boolean {
    return amount > 0.0 && amount < 1_000_000.0
}

private fun extractHeadlineAmount(text: String): Double? {
    val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
    return lines
        .take(8)
        .mapNotNull { line ->
            if (looksLikeDateOrTime(line) || looksLikeLongId(line)) return@mapNotNull null
            Regex("""(?<!\d)[-+]?\d+(?:\.\d+)?(?![:\d])""")
                .find(line)
                ?.value
                ?.toDoubleOrNull()
                ?.let { kotlin.math.abs(it) }
        }
        .sortedBy { it }
        .firstOrNull()
}

private fun looksLikeDateOrTime(line: String): Boolean {
    return Regex("""\d{1,2}:\d{2}(:\d{2})?""").containsMatchIn(line) ||
        Regex("""\d{4}[-/年]\d{1,2}[-/月]\d{1,2}""").containsMatchIn(line)
}

private fun looksLikeLongId(line: String): Boolean {
    return Regex("""\d{8,}""").containsMatchIn(line)
}

private fun extractFirstNumber(text: String): Double? {
    return Regex("""(?<!\d)(\d+(?:\.\d+)?)(?![:\d])""")
        .findAll(text)
        .mapNotNull { it.groups[1]?.value?.toDoubleOrNull() }
        .map { kotlin.math.abs(it) }
        .firstOrNull { isReasonableAmount(it) && it < 100000 }
}

private fun extractPreferredPaidAmount(text: String): Double? {
    val normalized = text.replace("\n", " ")
    val preferredPatterns = listOf(
        """(?:实付|实付款|支付金额|实收|合计|应付|应支付|已支付|订单金额|付款金额)\D{0,8}(\d+(?:\.\d+)?)""",
        """(\d+(?:\.\d+)?)\D{0,4}(?:元)?\D{0,4}(?:实付|实付款|支付金额|实收|合计|应付|应支付|已支付)"""
    )
    preferredPatterns.forEach { pattern ->
        Regex(pattern, RegexOption.IGNORE_CASE)
            .find(normalized)
            ?.groups
            ?.get(1)
            ?.value
            ?.toDoubleOrNull()
            ?.let { return it }
    }
    return null
}


private fun parseChineseNumber(raw: String): Double? {
    if (raw.isBlank()) return null
    val s = raw.replace("两", "二")
    if (s.contains("点")) {
        val parts = s.split("点", limit = 2)
        val intPart = parseChineseInteger(parts[0]) ?: return null
        val frac = parts.getOrNull(1).orEmpty()
            .mapNotNull { ch ->
                when (ch) {
                    '零' -> '0'
                    '一' -> '1'
                    '二' -> '2'
                    '三' -> '3'
                    '四' -> '4'
                    '五' -> '5'
                    '六' -> '6'
                    '七' -> '7'
                    '八' -> '8'
                    '九' -> '9'
                    else -> null
                }
            }.joinToString("")
        return if (frac.isBlank()) intPart.toDouble() else "$intPart.$frac".toDoubleOrNull()
    }
    return parseChineseInteger(s)?.toDouble()
}

private fun parseChineseInteger(text: String): Int? {
    if (text.isBlank()) return null
    val digits = mapOf(
        '零' to 0, '一' to 1, '二' to 2, '三' to 3, '四' to 4,
        '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9
    )
    val units = mapOf('十' to 10, '百' to 100, '千' to 1000, '万' to 10000)

    var result = 0
    var section = 0
    var number = 0
    for (ch in text) {
        val digit = digits[ch]
        if (digit != null) {
            number = digit
            continue
        }
        val unit = units[ch] ?: return null
        if (unit == 10000) {
            section = (section + number).coerceAtLeast(1) * unit
            result += section
            section = 0
            number = 0
        } else {
            val n = if (number == 0) 1 else number
            section += n * unit
            number = 0
        }
    }
    return result + section + number
}

private fun extractTitle(text: String): String {
    extractLabeledTitle(text)?.let { return it }

    return text
        .replace(Regex("""[#＃]+"""), "")
        .replace(Regex("""[0-9]+(\.[0-9]+)?"""), "")
        .replace(Regex("""[零一二两三四五六七八九十百千万点]+(元|块|块钱|人民币)?"""), "")
        .replace("元", "")
        .replace("块", "")
        .replace("块钱", "")
        .replace("人民币", "")
        .trim()
        .take(12)
}

private fun extractLabeledTitle(text: String): String? {
    val normalized = text
        .replace("\r", "")
        .replace(Regex("""\s+"""), " ")

    val labelPatterns = listOf(
        """商品[:：]?\s*([^\n]+)""",
        """商户(?:名称|名)?[:：]?\s*([^\n]+)""",
        """店铺(?:名称|名)?[:：]?\s*([^\n]+)"""
    )

    labelPatterns.forEach { pattern ->
        Regex(pattern).find(normalized)?.groups?.get(1)?.value?.let { raw ->
            cleanExtractedTitle(raw)?.let { return it }
        }
    }
    return null
}

private fun cleanExtractedTitle(raw: String): String? {
    return raw
        .replace(Regex("""\b\d{8,}\b"""), "")
        .replace(Regex("""\b[A-Za-z0-9]{10,}\b"""), "")
        .replace(Regex("""(订单号|交易号|商户单号|支付时间|当前状态|收单机构|支付方式).*$"""), "")
        .replace(Regex("""[-_/]{2,}"""), "-")
        .replace(Regex("""\s+"""), " ")
        .replace(Regex("""\s*-\s*"""), " ")
        .trim()
        .take(20)
        .ifBlank { null }
}

private fun sanitizeTitle(title: String): String {
    return title
        .replace(Regex("""^[#＃\s\p{Punct}]+"""), "")
        .replace(Regex("""[#＃]"""), "")
        .trim()
}

private fun inferTags(rawText: String, title: String): List<String> {
    val text = "$rawText $title"
    return when {
        Regex("""(外卖|午餐|早餐|晚餐|餐|吃饭|奶茶|咖啡|饮料|水果|宵夜)""").containsMatchIn(text) -> listOf("餐饮")
        Regex("""(地铁|公交|打车|滴滴|加油|停车|过路费|交通)""").containsMatchIn(text) -> listOf("交通")
        Regex("""(超市|购物|买菜|网购|淘宝|京东|拼多多)""").containsMatchIn(text) -> listOf("购物")
        else -> emptyList()
    }
}

private fun normalizeTitle(title: String, maxChars: Int = 20): String =
    title.trim().take(maxChars)

private fun Double.format2(): String =
    String.format(Locale.getDefault(), "%.2f", this)
