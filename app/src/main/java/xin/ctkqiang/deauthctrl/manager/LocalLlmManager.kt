package xin.ctkqiang.deauthctrl.manager

import android.content.Context
import android.net.Uri
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CancellationException

/**
 * 离线 AI 终端 —— LiteRT-LM 本地大模型运行时
 *
 * ## 内置模型库(全部 HuggingFace 免登录直链,一键下载自动装好)
 * - Nanbeige-4.1-3B abliterated(DuoNeural 转换,~2.0GB int4,无审查)
 * - Gemma-4-E2B Uncensored-MAX(PeppX 转换,~2.55GB int4,无审查)
 * - DeepSeek-R1-Distill-Qwen-1.5B(推理模型,~1.7GB)
 * - Qwen2.5-1.5B-Instruct(标准,~1.5GB)
 * 每款带性质标签(无审查/推理/标准),菜单里全部可见、任意切换。
 *
 * ## 模型导入(自由通道)
 * 任何 .litertlm 模型文件都可以从本地存储导入或直链下载并注册 ——
 * 本机推理没有内容过滤层,模型行为完全取决于权重本身。
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
        /** 模型性质标签:"uncensored" 无审查 / "reasoning" 推理 / "standard" 标准 —— 界面徽章用 */
        val tag: String = "standard",
    )

    private val prefs = context.getSharedPreferences("deauthctrl_prefs", Context.MODE_PRIVATE)

    companion object {
        const val DIR = "llm"
        /** 导入模型注册表偏好键 —— JSON 数组 [{id,name,file,size}] */
        private const val KEY_IMPORTED = "llm_imported"

        val MODELS = listOf(
            ModelSpec(
                id = "nanbeige",
                name = "Nanbeige-4.1-3B abliterated",
                fileName = "model.litertlm",
                url = "https://huggingface.co/DuoNeural/Nanbeige-4.1-3B-LiteRT/resolve/main/model.litertlm?download=true",
                sizeBytes = 2_000_000_000L, // 实际 ~2.0GB;完整性校验以 Content-Length 为准
                topK = 20,
                topP = 0.8,
                temperature = 0.7,
                tag = "uncensored",
            ),
            ModelSpec(
                id = "gemma4u",
                name = "Gemma-4-E2B Uncensored-MAX",
                fileName = "gemma-4-E2B-it-Uncensored-MAX.litertlm",
                url = "https://huggingface.co/PeppX/gemma-4-e2b-uncensored-litertlm/resolve/main/" +
                    "gemma-4-E2B-it-Uncensored-MAX.litertlm?download=true",
                sizeBytes = 2_500_000_000L, // 实际 ~2.55GB;完整性校验以 Content-Length 为准
                topK = 40,
                topP = 0.95,
                temperature = 0.7,
                tag = "uncensored",
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
                tag = "reasoning",
            ),
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
                tag = "standard",
            ),
        )
    }

    /** 内置 + 已导入模型的全集 —— 所有选择逻辑都以此为准 */
    fun allModels(): List<ModelSpec> = MODELS + importedModels()

    /** 读取导入模型注册表(文件缺失的条目自动剔除) */
    @Synchronized
    fun importedModels(): List<ModelSpec> {
        val raw = prefs.getString(KEY_IMPORTED, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val spec = ModelSpec(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        fileName = o.getString("file"),
                        url = "",
                        sizeBytes = o.getLong("size"),
                        topK = 20,
                        topP = 0.8,
                        temperature = 0.7,
                    )
                    if (modelFile(spec).exists()) add(spec)
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * 从本地存储导入 .litertlm 模型:拷贝到应用模型目录并注册。
     * 导入后模型离线可用;文件本身由调用方(SAF)提供读权限。
     */
    @Synchronized
    fun importModel(uri: Uri, displayName: String?): ModelSpec {
        val base = (displayName?.substringAfterLast('/') ?: "imported").trim()
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val fileName = if (base.endsWith(".litertlm")) base else "$base.litertlm"
        val out = File(context.filesDir, "$DIR/$fileName")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(out).use { input.copyTo(it) }
        } ?: throw IllegalStateException("cannot open $uri")
        if (out.length() < 4096L) {
            out.delete()
            throw IllegalStateException("imported file too small")
        }
        val spec = ModelSpec(
            id = "import_${System.currentTimeMillis()}",
            name = fileName.removeSuffix(".litertlm").take(32),
            fileName = fileName,
            url = "",
            sizeBytes = out.length(),
            topK = 20,
            topP = 0.8,
            temperature = 0.7,
            tag = "custom",
        )
        registerImported(spec)
        return spec
    }

    /** 移除导入模型:删文件 + 摘注册表 */
    @Synchronized
    fun removeImportedModel(spec: ModelSpec) {
        modelFile(spec).delete()
        val arr = runCatching { JSONArray(prefs.getString(KEY_IMPORTED, "[]")) }.getOrDefault(JSONArray())
        val merged = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("file") == spec.fileName) continue
            merged.put(o)
        }
        prefs.edit().putString(KEY_IMPORTED, merged.toString()).apply()
    }

    /** 把导入模型写进注册表(同名文件替换旧条目) */
    @Synchronized
    private fun registerImported(spec: ModelSpec) {
        val arr = runCatching { JSONArray(prefs.getString(KEY_IMPORTED, "[]")) }.getOrDefault(JSONArray())
        val merged = JSONArray()
        merged.put(JSONObject().apply {
            put("id", spec.id); put("name", spec.name); put("file", spec.fileName); put("size", spec.sizeBytes)
        })
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("file") == spec.fileName) continue
            merged.put(o)
        }
        prefs.edit().putString(KEY_IMPORTED, merged.toString()).apply()
    }

    /**
     * 从直链下载 .litertlm 并注册为可用模型 —— HuggingFace resolve 链接即可。
     * 文件名取 URL 最后一段,总大小未知时 onProgress 的第二参数为 0(界面只显示已下载字节)。
     */
    @Synchronized
    fun downloadFromUrl(url: String, onProgress: (Long, Long) -> Unit): ModelSpec {
        val base = url.substringAfterLast('/').substringBefore('?')
            .replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "downloaded" }
        val fileName = if (base.endsWith(".litertlm")) base else "$base.litertlm"
        val out = File(context.filesDir, "$DIR/$fileName")
        val part = File(out.parentFile, "$fileName.part")
        out.parentFile?.mkdirs()
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 20_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "DeauthCtrl/${android.os.Build.VERSION.RELEASE}")
            val total = conn.contentLengthLong.coerceAtLeast(0L)
            conn.inputStream.use { input ->
                FileOutputStream(part).use { output ->
                    val buf = ByteArray(256 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        read += n
                        onProgress(read, total)
                    }
                }
            }
            check(part.length() >= 4096L) { "download incomplete or not a model file: ${part.length()} bytes" }
            part.renameTo(out)
        } finally {
            runCatching { conn.disconnect() }
            if (part.exists() && part != out) part.delete()
        }
        val spec = ModelSpec(
            id = "import_${System.currentTimeMillis()}",
            name = fileName.removeSuffix(".litertlm").take(32),
            fileName = fileName,
            url = "",
            sizeBytes = out.length(),
            topK = 20,
            topP = 0.8,
            temperature = 0.7,
            tag = "custom",
        )
        registerImported(spec)
        return spec
    }

    fun modelFile(spec: ModelSpec): File = File(context.filesDir, "$DIR/${spec.fileName}")

    /** 删除模型目录中不属于任何已注册模型的遗留文件(旧版内置模型等,释放战备存储) */
    fun sweepOrphaned() {
        val known = allModels().flatMap { listOf(it.fileName, "${it.fileName}.part") }.toSet()
        File(context.filesDir, DIR).listFiles()?.forEach { f ->
            if (f.name !in known) f.delete()
        }
    }

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
     * 完整性校验以服务器 Content-Length 为准(缺失时回退 spec.sizeBytes),
     * spec.sizeBytes 只是界面展示的下限估计。
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
            val expected = conn.contentLengthLong.coerceAtLeast(spec.sizeBytes)
            conn.inputStream.use { input ->
                FileOutputStream(part).use { out ->
                    val buf = ByteArray(256 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        onProgress(read, expected)
                    }
                }
            }
            check(part.length() >= expected - 4096L) { "download incomplete: ${part.length()} bytes" }
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
     * 采样参数取自模型出厂默认,温度可在 UI 上覆盖;
     * 系统提示词由操作员在设置里自定义(留空 = 模型默认行为)。
     */
    @OptIn(ExperimentalApi::class)
    fun createSession(spec: ModelSpec, maxTokens: Int, temperatureOverride: Double?, systemInstruction: String?): LlmSession {
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
                    systemInstruction = systemInstruction?.takeIf { it.isNotBlank() }?.let { Contents.of(listOf(Content.Text(it))) },
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
