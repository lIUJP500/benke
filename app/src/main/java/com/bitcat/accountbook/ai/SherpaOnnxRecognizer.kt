package com.bitcat.accountbook.ai

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
}

private class SherpaReflector(private val context: Context) {
    private val pkg = "com.k2fsa.sherpa.onnx"
    private val recognizer: Any

    init {
        val helpers = Class.forName("$pkg.OnlineRecognizerKt")
        val getFeatureConfig = helpers.getMethod("getFeatureConfig", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        val getModelConfig = helpers.getMethod("getModelConfig", Int::class.javaPrimitiveType, android.content.res.AssetManager::class.java)
        val getOnlineLmConfig = helpers.getMethod("getOnlineLMConfig", Int::class.javaPrimitiveType)
        val getEndpointConfig = helpers.getMethod("getEndpointConfig")

        val featureConfig = getFeatureConfig.invoke(null, 16_000, 80)
        val modelConfig = getModelConfig.invoke(null, 0, context.assets)
            ?: throw IllegalStateException("缺少 sherpa-onnx 模型文件，请先放入 assets")
        val lmConfig = getOnlineLmConfig.invoke(null, 0)
        val endpointConfig = getEndpointConfig.invoke(null)

        val configClass = Class.forName("$pkg.OnlineRecognizerConfig")
        val configCtor = configClass.constructors.firstOrNull { it.parameterTypes.size == 14 }
            ?: throw IllegalStateException("sherpa-onnx 版本不匹配：OnlineRecognizerConfig 构造函数已变化")

        val config = configCtor.newInstance(
            featureConfig,
            modelConfig,
            lmConfig,
            endpointConfig,
            true,
            "greedy_search",
            "cpu",
            "",
            1.5f,
            0f,
            "",
            "",
            "",
            ""
        )

        val recognizerClass = Class.forName("$pkg.OnlineRecognizer")
        val ctor = recognizerClass.constructors.firstOrNull { it.parameterTypes.size == 2 }
            ?: throw IllegalStateException("sherpa-onnx 版本不匹配：OnlineRecognizer 构造函数已变化")
        recognizer = ctor.newInstance(context.assets, config)
    }

    fun createStream(): Any {
        return recognizer.javaClass.getMethod("createStream").invoke(recognizer)
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
        val textField = result.javaClass.getField("text")
        return textField.get(result)?.toString().orEmpty()
    }
}

private fun SherpaOnnxRecognizer.newReflectorOrThrow(): SherpaReflector {
    return try {
        SherpaReflector(context)
    } catch (e: ClassNotFoundException) {
        throw IllegalStateException("未找到 sherpa-onnx AAR，请将官方 AAR 放入 app/libs 并重新构建", e)
    } catch (e: Throwable) {
        throw IllegalStateException("sherpa-onnx 初始化失败：${e.message ?: "未知错误"}", e)
    }
}
