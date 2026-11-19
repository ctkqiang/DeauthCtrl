package xin.ctkqiang.deauthctrl.manager

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CancellationException

/**
 * 离线 AI 终端 —— LiteRT-LM 本地大模型运行时
 *
 * 收录 AI Edge Gallery 目录中**无需 HF 认证**即可下载的两款模型:
 * - Qwen2.5-1.5B-Instruct(默认,目录中最轻的开放对话模型,1.5GB)
 * - DeepSeek-R1-Distill-Qwen-1.5B(推理模型,1.7GB)
 * Gemma3-1B 更小但仓库 gated,需要 HF 令牌,故未收录。
 *
 * 首次经 Hugging Face 下载一次,之后推理 100% 离线 —— 提示词与
 * 输出不出设备。运行时只有 arm64-v8a / x86_64 原生库。
 */
class LocalLlmManager(private val context: Context) {

    data class ModelSpec(
        val id: String,
        val name: String,
        val fileName: String,
        val url: String,
        val sizeBytes: Long,
        val topK: Int,
        val topP: Double,
        val temperature: Double,
    )

    companion object {
        const val DIR = "llm"

        val MODELS = listOf(
            ModelSpec(
                id = "qwen",
                name = "Qwen2.5-1.5B-Instruct (q8)",
                fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
                url = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/" +
                    "19edb84c69a0212f29a6ef17ba0d6f278b6a1614/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm?download=true",
                sizeBytes = 1_597_931_520L,
                topK = 20,
                topP = 0.8,
                temperature = 0.7,
            ),
            ModelSpec(
                id = "deepseek",
                name = "DeepSeek-R1-Distill-Qwen-1.5B",
                fileName = "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
                url = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/" +
                    "e34bb88632342d1f9640bad579a45134eb1cf988/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm?download=true",
                sizeBytes = 1_833_451_520L,
                topK = 64,
                topP = 0.95,
                temperature = 1.0,
            ),
        )
    }

    fun modelFile(spec: ModelSpec): File = File(context.filesDir, "$DIR/${spec.fileName}")

    fun isDownloaded(spec: ModelSpec): Boolean =
        modelFile(spec).exists() && modelFile(spec).length() >= spec.sizeBytes - 4096L

    val isArchSupported: Boolean
        get() = android.os.Build.SUPPORTED_ABIS.any { it == "arm64-v8a" || it == "x86_64" }

    /** 设备总内存是否达到模型建议的 6GB */
    val isRamSufficient: Boolean
        get() {
            val am = context.getSystemService(android.app.ActivityManager::class.java)
            val info = android.app.ActivityManager.MemoryInfo()
            am?.getMemoryInfo(info)
            return info.totalMem >= 5L * 1024 * 1024 * 1024
        }

    /**
     * 从 Hugging Face 流式下载模型到 .part 再改名。
     * 回调 (已下载字节, 总字节);失败抛异常,调用方处理。
     */
    fun download(spec: ModelSpec, onProgress: (Long, Long) -> Unit) {
        val part = File(modelFile(spec).parentFile, "${spec.fileName}.part")
        modelFile(spec).parentFile?.mkdirs()
        val conn = URL(spec.url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 20_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "DeauthCtrl/${android.os.Build.VERSION.RELEASE}")
            val total = conn.contentLengthLong.coerceAtLeast(spec.sizeBytes)
            conn.inputStream.use { input ->
                FileOutputStream(part).use { out ->
                    val buf = ByteArray(256 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        onProgress(read, total)
                    }
                }
            }
            check(part.length() >= spec.sizeBytes - 4096L) { "download incomplete: ${part.length()} bytes" }
            part.renameTo(modelFile(spec))
        } finally {
            runCatching { conn.disconnect() }
            if (part.exists() && part != modelFile(spec)) part.delete()
        }
    }

    fun delete(spec: ModelSpec) {
        modelFile(spec).delete()
        File(modelFile(spec).parentFile, "${spec.fileName}.part").delete()
    }

    /**
     * 创建推理会话:加载引擎(阻塞,IO 线程调用)+ 建会话。
     * 采样参数取自模型出厂默认,温度可在 UI 上覆盖。
     */
    @OptIn(ExperimentalApi::class)
    fun createSession(spec: ModelSpec, maxTokens: Int, temperatureOverride: Double?): LlmSession {
        val engine = Engine(
            EngineConfig(
                modelPath = modelFile(spec).absolutePath,
                backend = Backend.CPU(),
                visionBackend = null,
                audioBackend = null,
                maxNumTokens = maxTokens,
                cacheDir = null,
            ),
        )
        try {
            engine.initialize()
            val conversation = engine.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(
                        topK = spec.topK,
                        topP = spec.topP,
                        temperature = temperatureOverride ?: spec.temperature,
                    ),
                    systemInstruction = null,
                    tools = emptyList(),
                    initialMessages = emptyList(),
                ),
            )
            return LlmSession(engine, conversation)
        } catch (e: Exception) {
            runCatching { engine.close() }
            throw e
        }
    }
}

/**
 * 一次已初始化的推理会话 —— 封装 LiteRT-LM 的 Engine + Conversation。
 * 会话内部维护对话历史;clear 由上层重建会话实现。
 */
class LlmSession(private val engine: Engine, private val conversation: Conversation) {

    /** 发送一条用户消息,流式回调部分输出;回调可能来自工作线程 */
    fun send(prompt: String, onPartial: (String) -> Unit, onDone: () -> Unit, onError: (String) -> Unit) {
        conversation.sendMessageAsync(
            Contents.of(listOf(Content.Text(prompt))),
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    onPartial(message.toString())
                }

                override fun onDone() {
                    onDone()
                }

                override fun onError(throwable: Throwable) {
                    if (throwable is CancellationException) {
                        onDone()
                    } else {
                        onError(throwable.message ?: "inference error")
                    }
                }
            },
            emptyMap(),
        )
    }

    /** 中断当前生成 */
    fun stop() {
        runCatching { conversation.cancelProcess() }
    }

    /** 释放引擎与会话 */
    fun close() {
        runCatching { conversation.close() }
        runCatching { engine.close() }
    }
}
