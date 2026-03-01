package com.bitcat.accountbook.ai

import android.content.Context
import android.net.Uri
import com.bitcat.accountbook.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object GlmOcrRepository {

    private const val ENDPOINT = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
    private const val MODEL = "glm-ocr"
    private const val MAX_IMAGE_BYTES = 8 * 1024 * 1024

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun recognizeText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.ZHIPU_API_KEY.trim()
        require(apiKey.isNotBlank()) { "未配置 ZHIPU_API_KEY" }

        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取图片")

        require(bytes.size <= MAX_IMAGE_BYTES) { "图片超过 8MB 限制" }

        val resolvedType = context.contentResolver.getType(uri).orEmpty().lowercase()
        val mimeType = if (resolvedType.startsWith("image/")) resolvedType else "image/jpeg"
        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        val dataUrl = "data:$mimeType;base64,$base64"

        val bodyJson = buildRequestBody(dataUrl)
        val req = Request.Builder()
            .url(ENDPOINT)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val detail = raw.take(240).ifBlank { "无响应体" }
                val failureMessage = buildFailureMessage(raw)
                error("GLM-OCR 请求失败(${resp.code})：$failureMessage")
            }
            if (raw.isBlank()) return@use ""

            val root = json.parseToJsonElement(raw).jsonObject
            val content = root["choices"]
            val contentNode = root["choices"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("message")
                ?.jsonObject
                ?.get("content")
            val content = extractContentText(contentNode)
            content
        }
    }
    private fun extractErrorMessage(raw: String): String {
        if (raw.isBlank()) return ""
        return try {
            val root = json.parseToJsonElement(raw).jsonObject
            root["error"]
                ?.jsonObject
                ?.get("message")
                ?.jsonPrimitive
                ?.contentOrNull
                .orEmpty()
        } catch (_: Exception) {
            ""
        }
    }
    private fun extractContentText(node: kotlinx.serialization.json.JsonElement?): String {
        if (node == null) return ""

        return when (node) {
            is JsonPrimitive -> node.contentOrNull.orEmpty().trim()
            is JsonArray -> node.joinToString("\n") { item ->
                val obj = item as? JsonObject
                when {
                    obj == null -> (item as? JsonPrimitive)?.contentOrNull.orEmpty()
                    obj["type"]?.jsonPrimitive?.contentOrNull == "text" ->
                        obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    obj["text"] != null ->
                        obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    else -> ""
                }
            }.trim()
            else -> node.toString().trim()
        }
    }

    private fun buildFailureMessage(raw: String): String {
        if (raw.isBlank()) return "无响应体"

        val parsedMessage = try {
            val root = json.parseToJsonElement(raw).jsonObject
            root["error"]
                ?.jsonObject
                ?.get("message")
                ?.jsonPrimitive
                ?.contentOrNull
                .orEmpty()
        } catch (_: Exception) {
            ""
        }

        return parsedMessage.ifBlank { raw.take(240).ifBlank { "无响应体" } }
    }
    private fun buildRequestBody(dataUrl: String): String {
        val payload = buildJsonObject {
            put("model", JsonPrimitive(MODEL))
            put("messages", JsonArray(listOf(
                buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", JsonPrimitive("text"))
                            put("text", JsonPrimitive("请提取这张图片中的全部文字，仅输出纯文本。"))
                        })
                        add(buildJsonObject {
                            put("type", JsonPrimitive("image_url"))
                            put("image_url", buildJsonObject {
                                put("url", JsonPrimitive(dataUrl))
                            })
                        })
                    })
                }
            )))
            put("temperature", JsonPrimitive(0.1))
            put("max_tokens", JsonPrimitive(2048))
        }
        return json.encodeToString(JsonObject.serializer(), payload)
    }
}