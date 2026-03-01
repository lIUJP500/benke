package com.bitcat.accountbook.ai

import com.bitcat.accountbook.BuildConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object AiParserRepository {

    private const val ENDPOINT = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
    private const val MODEL = "glm-5"

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

    /**
     * 统一入口：传入文本（文本/语音转写/OCR文本），返回结构化结果
     * 失败时会 fallback 本地规则，保证功能可用。
     */
    suspend fun parseExpense(
        text: String,
        inputType: String = "text",
        rawUri: String? = null
    ): AiExpenseResult {
        val cleaned = text.trim()
        if (cleaned.isBlank()) return AiExpenseResult(evidence = "")

        val key = BuildConfig.ZHIPU_API_KEY.trim()
        if (key.isBlank()) {
            // 没配 key 就直接走本地兜底
            return parseWithLocalFallback(cleaned, inputType = inputType, rawUri = rawUri)
        }

        // 先远程 LLM
        val remote = try {
            parseWithGlm5(cleaned, key, inputType = inputType, rawUri = rawUri)
        } catch (_: Exception) {
            null
        }

        // 远程失败/输出不合法 => 本地兜底
        return remote ?: parseWithLocalFallback(cleaned, inputType = inputType, rawUri = rawUri)
    }

    /**
     * GLM-5：通用端点 chat/completions
     * 要求模型只输出 JSON（AiExpenseResult 格式）
     */
    private fun parseWithGlm5(
        text: String,
        apiKey: String,
        inputType: String,
        rawUri: String?
    ): AiExpenseResult? {
        val sys = buildSystemPrompt()
        val user = buildUserPrompt(text = text, inputType = inputType, rawUri = rawUri)

        val bodyObj = GlmChatRequest(
            model = MODEL,
            messages = listOf(
                GlmMessage(role = "system", content = sys),
                GlmMessage(role = "user", content = user)
            ),
            temperature = 0.1,
            topP = 0.9,
            maxTokens = 512
        )

        val bodyJson = json.encodeToString(GlmChatRequest.serializer(), bodyObj)
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

            val parsedResp = try {
                json.decodeFromString(GlmChatResponse.serializer(), raw)
            } catch (_: Exception) {
                return null
            }

            val content = parsedResp.choices
                .firstOrNull()
                ?.message
                ?.content
                ?.trim()
                .orEmpty()

            if (content.isBlank()) return null

            // 模型可能会包一层 ```json ... ```，这里剥掉
            val jsonOnly = extractJsonObject(content) ?: return null

            return try {
                json.decodeFromString(AiExpenseResult.serializer(), jsonOnly)
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * prompt：强制只输出 JSON（不要任何解释、markdown）
     */
    private fun buildSystemPrompt(): String {
        return """
你是一个“记账信息抽取器”。你只能输出 **一个 JSON 对象**，不要输出任何多余文本、不要 markdown、不要代码块。
从用户输入中抽取并输出以下字段（字段必须存在，值可以为 null 或空数组）：
{
  "occurredAt": "yyyy-MM-dd HH:mm" 或 null,
  "occurredAtMillis": number(epochMillis) 或 null,
  "title": string 或 null,
  "amount": number 或 null,
  "currency": "CNY" 或 null,
  "tags": string[]（可以空数组）,
  "inputType": "text"/"voice"/"photo"/"camera" 或 null,
  "rawText": string 或 null,
  "rawUri": string 或 null,
  "confidence": number(0~1) 或 null,
  "evidence": string 或 null
}
要求：
1) occurredAt 必须严格是 "yyyy-MM-dd HH:mm"；如果用户没说时间就输出 null（不要猜日期）。
2) amount 必须是数字（Double）；如果无法确定金额就 null。
3) title 是消费事项（如“晚餐/打车/咖啡”等）；不要超过 20 个字，能提取则提取。
4) tags：若用户文本里出现明显类别词（餐饮/交通/购物/娱乐/学习/医疗/住房/通讯/旅行等）可给 1~3 个标签；否则空数组。
5) evidence：截取原文中最关键的一小段（<=120字）。
6) confidence：你对抽取结果的把握（0~1）。
7) 需要尽量生成可直接入库信息：currency 默认 CNY；inputType 优先使用用户提供来源；rawText 尽量保留用户原文。
8) occurredAt 与 occurredAtMillis 可二选一提供，若都无法确定则都为 null。
        """.trim()
    }

    private fun buildUserPrompt(text: String, inputType: String, rawUri: String?): String {
        return buildString {
            appendLine("输入来源: $inputType")
            if (!rawUri.isNullOrBlank()) appendLine("图片URI: $rawUri")
            append("用户输入：$text")
        }
    }

    /**
     * 本地兜底解析：简单可用，保证系统不因网络/模型问题不可用
     */
    private fun parseWithLocalFallback(text: String, inputType: String, rawUri: String?): AiExpenseResult {
        val amount = Regex("""(\d+(\.\d+)?)""").find(text)?.value?.toDoubleOrNull()

        val title = text
            .replace(Regex("""[0-9]+(\.[0-9]+)?"""), "")
            .replace("￥", "")
            .replace("¥", "")
            .replace("元", "")
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

    /**
     * 把模型返回里可能出现的 ```json ...``` 或前后闲话剥掉，尽量抽出 JSON 对象
     */
    private fun extractJsonObject(s: String): String? {
        var t = s.trim()

        // 去掉 ```json ``` 包裹
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

/* ---------------- 网络请求/响应 DTO ---------------- */

@Serializable
private data class GlmChatRequest(
    val model: String,
    val messages: List<GlmMessage>,
    val temperature: Double? = null,
    @SerialName("top_p") val topP: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null
)

@Serializable
private data class GlmMessage(
    val role: String,
    val content: String
)

@Serializable
private data class GlmChatResponse(
    val choices: List<GlmChoice> = emptyList()
)

@Serializable
private data class GlmChoice(
    val message: GlmMessage = GlmMessage(role = "assistant", content = "")
)
