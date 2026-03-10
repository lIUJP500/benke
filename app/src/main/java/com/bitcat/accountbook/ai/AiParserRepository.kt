package com.bitcat.accountbook.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.bitcat.accountbook.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

object AiParserRepository {

    private const val ENDPOINT = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
    private const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
    private const val MAX_IMAGE_EDGE = 960
    private const val JPEG_QUALITY = 72

    private val model: String
        get() = BuildConfig.ZHIPU_MODEL.trim().ifBlank { "glm-4.6v" }

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(40, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    fun parseExpenseLocal(
        text: String,
        inputType: String = "text",
        rawUri: String? = null
    ): AiExpenseResult {
        val cleaned = text.trim()
        if (cleaned.isBlank()) return AiExpenseResult(evidence = "")
        return parseWithLocalFallback(cleaned, inputType = inputType, rawUri = rawUri)
    }

    suspend fun parseExpenseCloud(
        context: Context? = null,
        text: String,
        inputType: String = "text",
        rawUri: String? = null
    ): AiExpenseResult {
        val cleaned = text.trim()
        if (cleaned.isBlank()) return AiExpenseResult(evidence = "")

        val key = BuildConfig.ZHIPU_API_KEY.trim()
        require(key.isNotBlank()) { "未配置云端模型 API Key" }

        val remote = runCatching {
            parseWithGlm(
                context = context,
                text = cleaned,
                apiKey = key,
                inputType = inputType,
                rawUri = rawUri
            )
        }.getOrNull()

        return remote ?: parseWithLocalFallback(cleaned, inputType = inputType, rawUri = rawUri)
    }

    suspend fun parseExpense(
        context: Context? = null,
        text: String,
        inputType: String = "text",
        rawUri: String? = null
    ): AiExpenseResult = parseExpenseCloud(context, text, inputType, rawUri)

    private fun parseWithGlm(
        context: Context?,
        text: String,
        apiKey: String,
        inputType: String,
        rawUri: String?
    ): AiExpenseResult? {
        val bodyJson = json.encodeToString(
            JsonObject.serializer(),
            buildChatRequest(
                text = text,
                inputType = inputType,
                rawUri = rawUri,
                imageDataUrl = buildImageDataUrl(context, rawUri)
            )
        )

        val req = Request.Builder()
            .url(ENDPOINT)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val raw = resp.body?.string().orEmpty()
            if (raw.isBlank()) return null

            val parsedResp = runCatching {
                json.decodeFromString(GlmChatResponse.serializer(), raw)
            }.getOrNull() ?: return null

            val content = parsedResp.choices.firstOrNull()?.message?.content?.trim().orEmpty()
            if (content.isBlank()) return null

            val jsonOnly = extractJsonObject(content) ?: return null
            return runCatching {
                json.decodeFromString(AiExpenseResult.serializer(), jsonOnly)
            }.getOrNull()
        }
    }

    private fun buildChatRequest(
        text: String,
        inputType: String,
        rawUri: String?,
        imageDataUrl: String?
    ): JsonObject {
        return buildJsonObject {
            put("model", model)
            put(
                "messages",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("role", "system")
                            put("content", buildSystemPrompt())
                        },
                        buildJsonObject {
                            put("role", "user")
                            put("content", buildUserContent(text, inputType, rawUri, imageDataUrl))
                        }
                    )
                )
            )
            put("temperature", 0.1)
            put("top_p", 0.9)
            put("max_tokens", 480)
        }
    }

    private fun buildSystemPrompt(): String {
        return """
你是“账单结构化提取器”。
只输出一个 JSON 对象，不要 markdown，不要解释。

输出字段：
{
  "occurredAt": "yyyy-MM-dd HH:mm" 或 null,
  "occurredAtMillis": number 或 null,
  "title": string 或 null,
  "amount": number 或 null,
  "currency": "CNY" 或 null,
  "tags": string[],
  "inputType": "text"/"voice"/"photo"/"camera" 或 null,
  "rawText": string 或 null,
  "rawUri": string 或 null,
  "confidence": number(0~1) 或 null,
  "evidence": string 或 null
}

规则：
1) title 是消费事项，只保留最有辨识度的消费对象，不超过 20 字。
2) 外卖场景：只有平台名时填平台名；有平台名和具体食物/商户时一起填，例如“美团外卖 麻辣烫”。
3) 交通、医疗、教育、外出场景：有具体名称就优先提取具体名称，例如“滴滴快车”“协和医院挂号”“英语网课”“汉庭酒店”。
4) 不要把订单号、手机号尾号、配送费、支付渠道、优惠说明、备注等杂项写进 title。
5) amount 必须是用户最终实际支付的金额。若出现“实付”“支付金额”“合计”“应付”“已支付”，优先取这些数值。
6) 若出现“优惠”“折扣”“满减”“立减”“红包”“券后”“退款”“原价”等词，相关金额不能作为 amount，除非文本明确说那就是最终支付金额。
7) 如果输入包含图片，要结合图片视觉内容和 OCR 文本一起判断商户、实付金额、消费时间。
8) tags 按账单语义补全，常用标签：餐饮/交通/购物/娱乐/学习/医疗/住房/通讯/旅行/日用/其他。
9) 无法确定字段时返回 null，不要编造。
10) evidence 用一句短话说明依据，例如“美团外卖订单中实付 23.8 元”。
""".trimIndent()
    }

    private fun buildUserContent(
        text: String,
        inputType: String,
        rawUri: String?,
        imageDataUrl: String?
    ): JsonArray {
        return buildJsonArray {
            imageDataUrl?.let { dataUrl ->
                add(
                    buildJsonObject {
                        put("type", "image_url")
                        put(
                            "image_url",
                            buildJsonObject {
                                put("url", dataUrl)
                            }
                        )
                    }
                )
            }
            add(
                buildJsonObject {
                    put("type", "text")
                    put(
                        "text",
                        buildString {
                            appendLine("输入来源: $inputType")
                            if (!rawUri.isNullOrBlank()) appendLine("图片URI: $rawUri")
                            appendLine("请按账单格式提取并补全字段。")
                            append("OCR文本/原始内容: $text")
                        }
                    )
                }
            )
        }
    }

    private fun buildImageDataUrl(context: Context?, rawUri: String?): String? {
        if (context == null || rawUri.isNullOrBlank()) return null
        val uri = runCatching { Uri.parse(rawUri) }.getOrNull() ?: return null
        val bytes = compressImage(context, uri) ?: return null
        if (bytes.isEmpty() || bytes.size > MAX_IMAGE_BYTES) return null
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64"
    }

    private fun compressImage(context: Context, uri: Uri): ByteArray? {
        val original = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        } ?: return null

        val resized = resizeBitmap(original)
        val output = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)

        if (resized !== original) resized.recycle()
        original.recycle()

        return output.toByteArray()
    }

    private fun resizeBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxEdge = maxOf(width, height)
        if (maxEdge <= MAX_IMAGE_EDGE) return bitmap

        val scale = MAX_IMAGE_EDGE.toFloat() / maxEdge.toFloat()
        val targetWidth = (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun parseWithLocalFallback(text: String, inputType: String, rawUri: String?): AiExpenseResult {
        val amount = Regex("""(\d+(\.\d+)?)""").find(text)?.value?.toDoubleOrNull()

        val title = text
            .replace(Regex("""[0-9]+(\.[0-9]+)?"""), "")
            .replace("元", "")
            .replace("块", "")
            .replace("#", "")
            .trim()
            .take(20)
            .ifBlank { null }

        return AiExpenseResult(
            occurredAt = null,
            occurredAtMillis = null,
            title = title,
            amount = amount,
            currency = "CNY",
            tags = emptyList(),
            inputType = inputType,
            rawText = text,
            rawUri = rawUri,
            confidence = 0.35,
            evidence = text.take(120)
        )
    }

    private fun extractJsonObject(s: String): String? {
        var t = s.trim()
        if (t.startsWith("```")) {
            t = t.removePrefix("```json").removePrefix("```").trim()
            if (t.endsWith("```")) t = t.removeSuffix("```").trim()
        }

        val start = t.indexOf('{')
        val end = t.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) return null
        return t.substring(start, end + 1)
    }
}

@Serializable
private data class GlmChatMessage(
    val content: String = "",
    val role: String = "assistant"
)

@Serializable
private data class GlmChoice(
    val message: GlmChatMessage = GlmChatMessage()
)

@Serializable
private data class GlmChatResponse(
    val choices: List<GlmChoice> = emptyList()
)
