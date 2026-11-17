package xin.ctkqiang.deauthctrl.manager

/**
 * 会议视频协议层 —— 纯函数与纯数据结构,无 Android 依赖,可单元测试。
 *
 * 从 ConferenceManager 提取:协议头编解码、源 ID 归一化、分片重组。
 * 生产代码与单元测试共用这一份实现,修复过的协议缺陷由测试锁定防回归。
 */
internal object ConfProtocol {

    /** 最大 UDP 载荷(含 14B 头) */
    const val MAX_FRAG = 1200

    /** 头长度:sourceId(8B ASCII) | frameId(2B BE) | 片号(1B) | 总片数(1B) | 标志(1B) | 保留(1B) */
    const val HEADER_LEN = 14

    /** 标志位:0x01 CONFIG(CSD) 0x02 IDR 0x04 末片 0x08 帧起点 */
    const val FLAG_CONFIG = 0x01
    const val FLAG_IDR = 0x02
    const val FLAG_LAST = 0x04
    const val FLAG_FRAME_START = 0x08

    /** 解析后的协议头(sourceId 已归一化) */
    data class Header(
        val sourceId: String,
        val frameId: Int,
        val fragIdx: Int,
        val fragCount: Int,
        val flags: Int,
    ) {
        val isConfig: Boolean get() = (flags and FLAG_CONFIG) != 0
        val isIdr: Boolean get() = (flags and FLAG_IDR) != 0
    }

    /**
     * 去掉写入端补的 NUL 与空白:成员表/信标/槽位统一使用归一化后的 ID。
     * 6 字符 deviceId 写入 8B 定宽字段后右侧补 NUL,读取端必须归一到同一形式,
     * 否则自流过滤失效、槽位键错位。
     */
    fun normalizeId(raw: String): String =
        raw.trimEnd { it == '\u0000' || it.isWhitespace() }

    /** 写 14B 协议头;srcWireId 必须恰好 8 字节(写入端 padEnd 保证,见 ConferenceManager.wireId) */
    fun writeHeader(
        dst: ByteArray,
        srcWireId: ByteArray,
        frameId: Int,
        fragIdx: Int,
        fragCount: Int,
        flags: Int,
    ) {
        require(dst.size >= HEADER_LEN) { "dst 不足 ${HEADER_LEN}B" }
        require(srcWireId.size == HEADER_LEN - 6) { "srcWireId 必须 8B" }
        srcWireId.copyInto(dst, 0)
        dst[8] = ((frameId shr 8) and 0xFF).toByte()
        dst[9] = (frameId and 0xFF).toByte()
        dst[10] = fragIdx.toByte()
        dst[11] = fragCount.toByte()
        dst[12] = flags.toByte()
        dst[13] = 0
    }

    /** 解析 14B 协议头;包过短返回 null;sourceId 已归一化 */
    fun decodeHeader(packet: ByteArray): Header? {
        if (packet.size < HEADER_LEN) return null
        val rawId = String(packet, 0, 8, Charsets.US_ASCII)
        return Header(
            sourceId = normalizeId(rawId),
            frameId = ((packet[8].toInt() and 0xFF) shl 8) or (packet[9].toInt() and 0xFF),
            fragIdx = packet[10].toInt() and 0xFF,
            fragCount = packet[11].toInt() and 0xFF,
            flags = packet[12].toInt() and 0xFF,
        )
    }

    /**
     * 发送端分片:一帧 → 若干带 14B 头的 UDP 包(纯函数)。
     * 生产发送循环与传输模拟测试共用,保证测试覆盖的就是线上代码路径。
     * 帧超过 255 分片返回空列表(发送端据此丢弃并报错)。
     */
    fun fragmentFrame(
        data: ByteArray,
        wireId: ByteArray,
        frameId: Int,
        isConfig: Boolean,
        isIdr: Boolean,
        maxPayload: Int = MAX_FRAG - HEADER_LEN,
    ): List<ByteArray> {
        val fragCount = ((data.size + maxPayload - 1) / maxPayload).coerceAtLeast(1)
        if (fragCount > 255) return emptyList()
        return buildList(fragCount) {
            for (fragIdx in 0 until fragCount) {
                val offset = fragIdx * maxPayload
                val length = minOf(maxPayload, data.size - offset)
                val packet = ByteArray(HEADER_LEN + length)
                var flags = 0
                if (isConfig) flags = flags or FLAG_CONFIG
                if (isIdr) flags = flags or FLAG_IDR
                if (fragIdx == fragCount - 1) flags = flags or FLAG_LAST
                if (fragIdx == 0) flags = flags or FLAG_FRAME_START
                writeHeader(packet, wireId, frameId, fragIdx, fragCount, flags)
                System.arraycopy(data, offset, packet, HEADER_LEN, length)
                add(packet)
            }
        }
    }

    /** 一帧重组完成后的交付单元 */
    data class DeliveredFrame(val data: ByteArray, val flags: Int) {
        val isConfig: Boolean get() = (flags and FLAG_CONFIG) != 0
        val isIdr: Boolean get() = (flags and FLAG_IDR) != 0
    }

    /**
     * 帧重组器 —— 按 frameId 分桶乱序重组,不因 IDR 分片互相清空:
     *
     * - 同一 frameId 的所有分片进同一个缓冲,齐片才交付(缺片 1.5s 超时整帧丢弃)
     * - 同一分片重复到达忽略;整帧交付一次(completed 去重,组播+单播双路去重)
     * - fragCount 与已缓冲不符时重建缓冲(发送端参数变化)
     * - pending 有界(16 帧),超限丢最旧帧
     */
    class FrameAssembler(private val timeoutMs: Long = 1500L) {

        private class Buffer(val fragCount: Int, val firstSeenAtMs: Long) {
            val parts = arrayOfNulls<ByteArray>(fragCount)
            var received = 0
            var flags = 0
        }

        private val pending = LinkedHashMap<Int, Buffer>()
        private val completed = LinkedHashSet<Int>()
        private var lastPruneAtMs = 0L

        @Volatile var missingFragments: Long = 0L
            private set

        fun onFragment(
            frameId: Int,
            fragIdx: Int,
            fragCount: Int,
            flags: Int,
            payload: ByteArray,
        ): DeliveredFrame? {
            if (fragCount <= 0 || fragIdx < 0 || fragIdx >= fragCount) return null
            if (completed.contains(frameId)) return null // 组播 + 单播的重复帧
            val now = System.currentTimeMillis()
            prune(now)

            var buffer = pending[frameId]
            if (buffer != null && buffer.fragCount != fragCount) {
                pending.remove(frameId)
                buffer = null
            }
            if (buffer == null) {
                buffer = Buffer(fragCount, now)
                buffer.flags = flags
                pending[frameId] = buffer
            }
            if (buffer.parts[fragIdx] != null) return null // 同一分片重复
            buffer.parts[fragIdx] = payload
            buffer.received++
            if (buffer.received < buffer.fragCount) return null

            pending.remove(frameId)
            completed.add(frameId)
            if (completed.size > 64) completed.remove(completed.first())

            var total = 0
            for (part in buffer.parts) total += part?.size ?: 0
            val out = ByteArray(total)
            var offset = 0
            for (i in 0 until buffer.fragCount) {
                val part = buffer.parts[i] ?: return null
                System.arraycopy(part, 0, out, offset, part.size)
                offset += part.size
            }
            return DeliveredFrame(out, buffer.flags)
        }

        private fun prune(now: Long) {
            if (now - lastPruneAtMs > 1000L) {
                lastPruneAtMs = now
                val iterator = pending.entries.iterator()
                while (iterator.hasNext()) {
                    val buffer = iterator.next().value
                    if (now - buffer.firstSeenAtMs > timeoutMs) {
                        missingFragments += (buffer.fragCount - buffer.received).coerceAtLeast(0)
                        iterator.remove()
                    }
                }
            }
            while (pending.size > MAX_PENDING_FRAMES) {
                val oldestId = pending.keys.firstOrNull() ?: break
                pending.remove(oldestId)?.let { buffer ->
                    missingFragments += (buffer.fragCount - buffer.received).coerceAtLeast(0)
                }
            }
        }

        companion object {
            private const val MAX_PENDING_FRAMES = 16
        }
    }
}
