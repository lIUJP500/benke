package com.bitcat.accountbook.ai

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

class SherpaOnnxRecognizer(
    internal val context: Context
) {
    private val sampleRate = 16_000

    @SuppressLint("MissingPermission")
    suspend fun recognizeOnce(maxDurationMs: Long = 6_000L): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val recognizer = newReflectorOrThrow()
            val stream = recognizer.createStream()

            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            require(minBuffer > 0) { "AudioRecord 初始化失败" }

            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer * 2
            )
            require(record.state == AudioRecord.STATE_INITIALIZED) { "麦克风不可用" }

            try {
                val pcm = ShortArray(minBuffer / 2)
                val start = System.currentTimeMillis()

                record.startRecording()
                while (System.currentTimeMillis() - start < maxDurationMs) {
                    val n = record.read(pcm, 0, pcm.size)
                    if (n <= 0) continue

                    val samples = FloatArray(n)
                    for (i in 0 until n) {
                        samples[i] = pcm[i] / 32768f
                    }

                    recognizer.acceptWaveform(stream, samples, sampleRate)
                    while (recognizer.isReady(stream)) {
                        recognizer.decode(stream)
                    }
                }

                recognizer.inputFinished(stream)
                while (recognizer.isReady(stream)) {
                    recognizer.decode(stream)
                }

                val text = recognizer.getResultText(stream).trim()
                require(text.isNotBlank()) { "未识别到语音内容" }
                text
            } finally {
                runCatching { record.stop() }
                runCatching { record.release() }
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun recognizeUntilStopped(
        shouldStop: () -> Boolean,
        onLevel: (Float) -> Unit,
        onPartialText: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val recognizer = newReflectorOrThrow()
            val stream = recognizer.createStream()

            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            require(minBuffer > 0) { "AudioRecord 初始化失败" }

            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer * 2
            )
            require(record.state == AudioRecord.STATE_INITIALIZED) { "麦克风不可用" }

            var lastText = ""
            try {
                val pcm = ShortArray(minBuffer / 2)
                record.startRecording()

                while (!shouldStop()) {
                    val n = record.read(pcm, 0, pcm.size)
                    if (n <= 0) continue

                    val samples = FloatArray(n)
                    var sqSum = 0.0
                    for (i in 0 until n) {
                        val v = pcm[i] / 32768f
                        samples[i] = v
                        sqSum += (v * v)
                    }

                    val rms = sqrt(sqSum / n).toFloat()
                    val level = (rms * 8f).coerceIn(0f, 1f)
                    withContext(Dispatchers.Main) { onLevel(level) }

                    recognizer.acceptWaveform(stream, samples, sampleRate)
                    while (recognizer.isReady(stream)) {
                        recognizer.decode(stream)
                    }

                    val partial = recognizer.getResultText(stream).trim()
                    if (partial.isNotBlank() && partial != lastText) {
                        lastText = partial
                        withContext(Dispatchers.Main) { onPartialText(partial) }
                    }
                }

                recognizer.inputFinished(stream)
                while (recognizer.isReady(stream)) {
                    recognizer.decode(stream)
                }

                val finalText = recognizer.getResultText(stream).trim()
                if (finalText.isNotBlank() && finalText != lastText) {
                    withContext(Dispatchers.Main) { onPartialText(finalText) }
                }
                require(finalText.isNotBlank()) { "未识别到语音内容" }
                finalText
            } finally {
                runCatching { record.stop() }
                runCatching { record.release() }
                withContext(Dispatchers.Main) { onLevel(0f) }
            }
        }
    }
}

private class SherpaReflector(private val context: Context) {
    private val pkg = "com.k2fsa.sherpa.onnx"
    private val recognizer: Any
    private val modelDir = "sherpa-onnx/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20"

    init {
        validateModelAssets(context, modelDir)

        val helpers = Class.forName("$pkg.OnlineRecognizerKt")
        val getModelConfig = helpers.getMethod("getModelConfig", Int::class.javaPrimitiveType)
        val getOnlineLmConfig = helpers.getMethod("getOnlineLMConfig", Int::class.javaPrimitiveType)
        val getEndpointConfig = helpers.getMethod("getEndpointConfig")

        val featureConfig = Class.forName("$pkg.FeatureConfig")
            .getDeclaredConstructor()
            .newInstance()

        val modelConfig = getModelConfig.invoke(null, 0)
            ?: throw IllegalStateException("缺少 sherpa-onnx 模型文件，请先放入 assets")
        val lmConfig = getOnlineLmConfig.invoke(null, 0)
        val endpointConfig = getEndpointConfig.invoke(null)

        val ctcFstConfig = Class.forName("$pkg.OnlineCtcFstDecoderConfig")
            .getDeclaredConstructor()
            .newInstance()
        val hrConfig = Class.forName("$pkg.HomophoneReplacerConfig")
            .getDeclaredConstructor()
            .newInstance()

        // Force model paths to the unpacked bilingual zipformer directory in assets.
        modelConfig.javaClass.getMethod("setModelingUnit", String::class.java)
            .invoke(modelConfig, "cjkchar+bpe")
        modelConfig.javaClass.getMethod("setBpeVocab", String::class.java)
            .invoke(modelConfig, "$modelDir/bpe.vocab")
        modelConfig.javaClass.getMethod("setTokens", String::class.java)
            .invoke(modelConfig, "$modelDir/tokens.txt")

        val transducer = modelConfig.javaClass.getMethod("getTransducer").invoke(modelConfig)
        transducer.javaClass.getMethod("setEncoder", String::class.java)
            .invoke(transducer, "$modelDir/encoder-epoch-99-avg-1.onnx")
        transducer.javaClass.getMethod("setDecoder", String::class.java)
            .invoke(transducer, "$modelDir/decoder-epoch-99-avg-1.onnx")
        transducer.javaClass.getMethod("setJoiner", String::class.java)
            .invoke(transducer, "$modelDir/joiner-epoch-99-avg-1.onnx")

        val configClass = Class.forName("$pkg.OnlineRecognizerConfig")
        val configCtor = configClass.constructors.firstOrNull {
            it.parameterTypes.size == 14 && it.parameterTypes[8] == Int::class.javaPrimitiveType
        } ?: throw IllegalStateException("sherpa-onnx 版本不兼容：OnlineRecognizerConfig 构造函数变化")

        val config = configCtor.newInstance(
            featureConfig,
            modelConfig,
            lmConfig,
            ctcFstConfig,
            hrConfig,
            endpointConfig,
            true,
            "greedy_search",
            4,
            "",
            1.5f,
            "",
            "",
            0f
        )

        val recognizerClass = Class.forName("$pkg.OnlineRecognizer")
        val ctor = recognizerClass.constructors.firstOrNull { it.parameterTypes.size >= 2 }
            ?: throw IllegalStateException("sherpa-onnx 版本不兼容：OnlineRecognizer 构造函数变化")
        recognizer = ctor.newInstance(context.assets, config)
    }

    fun createStream(): Any {
        return recognizer.javaClass
            .getMethod("createStream", String::class.java)
            .invoke(recognizer, "")
            ?: throw IllegalStateException("sherpa-onnx createStream 返回空对象")
    }

    fun isReady(stream: Any): Boolean {
        return recognizer.javaClass.getMethod("isReady", stream.javaClass).invoke(recognizer, stream) as Boolean
    }

    fun decode(stream: Any) {
        recognizer.javaClass.getMethod("decode", stream.javaClass).invoke(recognizer, stream)
    }

    fun acceptWaveform(stream: Any, samples: FloatArray, sampleRate: Int) {
        stream.javaClass
            .getMethod("acceptWaveform", FloatArray::class.java, Int::class.javaPrimitiveType)
            .invoke(stream, samples, sampleRate)
    }

    fun inputFinished(stream: Any) {
        stream.javaClass.getMethod("inputFinished").invoke(stream)
    }

    fun getResultText(stream: Any): String {
        val result = recognizer.javaClass.getMethod("getResult", stream.javaClass).invoke(recognizer, stream)
        return result.javaClass.getMethod("getText").invoke(result)?.toString().orEmpty()
    }
}

private fun SherpaOnnxRecognizer.newReflectorOrThrow(): SherpaReflector {
    return try {
        SherpaReflector(context)
    } catch (e: ClassNotFoundException) {
        throw IllegalStateException("未找到 sherpa-onnx AAR，请将官方 AAR 放入 app/libs 并重新构建", e)
    } catch (e: Throwable) {
        val reason = "${e::class.java.simpleName}: ${e.message ?: "未知错误"}"
        throw IllegalStateException("sherpa-onnx 初始化失败：$reason", e)
    }
}

private fun validateModelAssets(context: Context, modelDir: String) {
    val files = listOf(
        "$modelDir/encoder-epoch-99-avg-1.onnx",
        "$modelDir/decoder-epoch-99-avg-1.onnx",
        "$modelDir/joiner-epoch-99-avg-1.onnx",
        "$modelDir/tokens.txt",
        "$modelDir/bpe.vocab"
    )

    val missing = files.filterNot { path ->
        runCatching { context.assets.open(path).use { } }.isSuccess
    }

    require(missing.isEmpty()) {
        "模型文件缺失: ${missing.joinToString()}"
    }
}
