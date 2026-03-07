package com.bitcat.accountbook.ai

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.bitcat.accountbook.BuildConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object OcrHelper {

    private const val OCR_ENDPOINT = "https://open.bigmodel.cn/api/paas/v4/layout_parsing"
    private const val OCR_MODEL = "glm-ocr"
    private const val MAX_IMAGE_BYTES = 10 * 1024 * 1024

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    suspend fun recognizeText(context: Context, uri: Uri): String {
        val key = BuildConfig.ZHIPU_API_KEY.trim()
        if (key.isNotBlank()) {
            val cloudResult = runCatching {
                recognizeWithZhipuOcr(context, uri, key)
            }.getOrNull()

            if (!cloudResult.isNullOrBlank()) {
                return cloudResult
            }
        }

        return recognizeWithMlKit(context, uri)
    }

    private suspend fun recognizeWithMlKit(context: Context, uri: Uri): String {
        val image = InputImage.fromFilePath(context, uri)
        return suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    cont.resume(result.text ?: "")
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }
        }
    }

    private suspend fun recognizeWithZhipuOcr(
        context: Context,
        uri: Uri,
        apiKey: String
    ): String? = withContext(Dispatchers.IO) {
        val mimeType = context.contentResolver.getType(uri)?.lowercase() ?: "image/jpeg"
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes()
        } ?: return@withContext null

        if (bytes.isEmpty() || bytes.size > MAX_IMAGE_BYTES) return@withContext null

        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val dataUrl = "data:$mimeType;base64,$base64"

        val body = buildJsonObject {
            put("model", OCR_MODEL)
            put("file", dataUrl)
        }
        val bodyText = json.encodeToString(JsonObject.serializer(), body)

        val request = Request.Builder()
            .url(OCR_ENDPOINT)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(bodyText.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val raw = response.body?.string().orEmpty()
            if (raw.isBlank()) return@withContext null
            return@withContext extractTextFromLayoutResponse(raw)
        }
    }

    private fun extractTextFromLayoutResponse(raw: String): String? {
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return null

        val markdown = extractMarkdownText(root)
        if (!markdown.isNullOrBlank()) {
            return markdown
        }

        val textCandidates = mutableListOf<String>()
        collectTextFields(root, textCandidates)
        return textCandidates
            .joinToString("\n")
            .trim()
            .ifBlank { null }
    }

    private fun extractMarkdownText(node: JsonElement): String? {
        if (node !is JsonObject) return null

        val mdResults = node["md_results"]
        if (mdResults is JsonArray) {
            val lines = mdResults.mapNotNull { item ->
                when (item) {
                    is JsonPrimitive -> item.contentOrNull
                    is JsonObject -> (
                        item["text"]?.jsonPrimitive?.contentOrNull
                            ?: item["content"]?.jsonPrimitive?.contentOrNull
                            ?: item["markdown"]?.jsonPrimitive?.contentOrNull
                    )
                    else -> null
                }
            }.filter { it.isNotBlank() }

            if (lines.isNotEmpty()) return lines.joinToString("\n").trim()
        }

        return (node["data"] as? JsonObject)?.let { extractMarkdownText(it) }
    }

    private fun collectTextFields(node: JsonElement, output: MutableList<String>) {
        when (node) {
            is JsonObject -> {
                node.forEach { (key, value) ->
                    if (key in TEXT_KEYS && value is JsonPrimitive) {
                        value.contentOrNull
                            ?.takeIf { it.isNotBlank() }
                            ?.let(output::add)
                    }
                    collectTextFields(value, output)
                }
            }
            is JsonArray -> node.forEach { collectTextFields(it, output) }
            else -> Unit
        }
    }

    private val TEXT_KEYS = setOf("text", "content", "markdown", "md")
}
