package xin.ctkqiang.deauthctrl.manager

import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 单次 Ping 探测结果
 *
 * 封装一次 ICMP Echo Request/Reply 的关键指标。
 *
 * @property sequence ICMP 序列号，从 1 开始递增
 * @property ip 目标 IP 地址或主机名
 * @property ttl 生存时间（Time To Live），反映经过的路由跳数
 * @property timeMs 往返时延（RTT），单位毫秒
 * @property bytes 响应数据包大小，单位字节（通常为 64）
 */
data class PingResult(
    val sequence: Int,
    val ip: String,
    val ttl: Int,
    val timeMs: Float,
    val bytes: Int,
)

/**
 * Ping 泛洪/压力测试管理器
 *
 * 通过调用系统 `ping` 命令实现对目标主机的持续 ICMP 探测。
 * 支持无限发包（count=0）或指定次数，可自定义发包间隔。
 *
 * ## 实现原理
 * - 调用 Android/Linux 系统的 `ping -c 1 -W <timeout> <host>` 命令
 * - 逐次执行单次 ping（而非 `ping -c N` 批量模式），以获得逐包粒度的实时回调
 * - 使用 Kotlin 协程在 Dispatchers.IO 上执行 Shell 命令
 * - 通过正则表达式解析 ping 输出中的 time、ttl、bytes 字段
 *
 * ## 使用限制
 * - 需要系统 `ping` 命令可用（Android 默认内置）
 * - 部分网络环境可能禁止 ICMP 流量（防火墙/网关限制）
 * - 高频发包（interval < 100ms）可能被 Android 系统限速
 * - 非 root 设备无法发送原始 ICMP 包（依赖系统 ping）
 */
class PingManager {

    /**
     * 运行状态标志
     *
     * @Volatile 确保 stop() 在任意线程调用后，发包循环能立即退出
     */
    @Volatile var isRunning = false
        private set

    /** 协程 Job 句柄，用于取消正在进行的发包任务 */
    private var job: Job? = null

    /**
     * 开始持续 Ping 探测
     *
     * 在后台协程中循环调用系统 ping 命令，每次 ping 完成后通过回调实时通知 UI。
     * 统计信息（发送数、接收数、丢包率、最小/平均/最大 RTT）在每次探测后更新。
     *
     * @param host 目标主机名或 IP 地址（如 "8.8.8.8" 或 "google.com"）
     * @param count 发包总次数，0 表示无限循环（直到调用 stop()）
     * @param interval 发包间隔（毫秒），默认 200ms。注意：ping 命令本身耗时不计入间隔
     * @param onResult 每次收到成功响应时的回调，在主线程执行
     * @param onStats 每次统计信息更新时的回调，参数依次为：发送数、接收数、丢包率(0-100)、最小RTT、平均RTT、最大RTT
     * @param onComplete 发包结束时的回调（count 达到或 stop() 被调用），在主线程执行
     */
    fun start(
        host: String,
        count: Int = 0,
        interval: Long = 200,
        onResult: (PingResult) -> Unit,
        onStats: (sent: Int, received: Int, loss: Int, min: Float, avg: Float, max: Float) -> Unit,
        onComplete: () -> Unit,
    ) {
        stop()
        isRunning = true
        job = CoroutineScope(Dispatchers.IO).launch {
            var seq = 0
            var sent = 0
            var recv = 0
            val times = mutableListOf<Float>()

            try {
                while (isRunning && (count == 0 || seq < count)) {
                    seq++
                    val result = pingOnce(host, seq, interval)
                    sent++
                    if (result != null) {
                        recv++
                        times.add(result.timeMs)
                        withContext(Dispatchers.Main) { onResult(result) }
                    }
                    withContext(Dispatchers.Main) {
                        onStats(
                            sent, recv,
                            if (sent > 0) ((sent - recv) * 100 / sent) else 0,
                            times.minOrNull() ?: 0f,
                            if (times.isNotEmpty()) times.average().toFloat() else 0f,
                            times.maxOrNull() ?: 0f,
                        )
                    }
                    delay(interval)
                }
            } finally {
                withContext(Dispatchers.Main) { onComplete() }
            }
        }
    }

    /**
     * 执行单次 Ping 探测
     *
     * 通过 Runtime.exec() 调用系统 `ping -c 1 -W <timeout> <host>` 命令。
     * 解析 stdout + stderr 输出，用正则提取：
     * - `time=<float> ms` → 往返时延
     * - `ttl=<int>` → 生存时间
     * - `<int> bytes` → 数据包大小
     *
     * @param host 目标主机
     * @param seq 当前 ICMP 序列号（用于回调标识）
     * @param timeout 超时等待时间（毫秒），同时作为 ping -W 参数
     * @return PingResult 若 ping 成功，null 若超时或目标不可达
     */
    private fun pingOnce(host: String, seq: Int, timeout: Long): PingResult? {
        try {
            val cmd = "ping -c 1 -W ${(timeout / 1000).coerceAtLeast(1)} $host"
            val proc = Runtime.getRuntime().exec(cmd)
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            val errReader = BufferedReader(InputStreamReader(proc.errorStream))
            val output = reader.readText() + errReader.readText()
            reader.close(); errReader.close()
            proc.waitFor()

            val timeMatch = Regex("time[=<](\\d+\\.?\\d*)\\s*ms").find(output)
            val ttlMatch = Regex("ttl[=<](\\d+)").find(output)
            val bytesMatch = Regex("(\\d+)\\s*bytes").find(output)

            if (timeMatch != null) {
                return PingResult(
                    sequence = seq,
                    ip = host,
                    ttl = ttlMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0,
                    timeMs = timeMatch.groupValues[1].toFloat(),
                    bytes = bytesMatch?.groupValues?.get(1)?.toIntOrNull() ?: 64,
                )
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * 停止 Ping 探测
     *
     * 取消正在进行的协程 Job，设置 isRunning = false。
     * 线程安全，可从任意线程调用。不会抛出异常。
     */
    fun stop() {
        isRunning = false
        job?.cancel()
        job = null
    }
}
