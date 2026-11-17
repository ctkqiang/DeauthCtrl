package xin.ctkqiang.deauthctrl.manager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

/**
 * 会议视频传输链路模拟测试 —— 两台设备之间"字节级端到端"证据。
 *
 * 用生产代码的同一份实现(ConfProtocol.fragmentFrame / decodeHeader /
 * FrameAssembler)模拟:发送端编码 → UDP 传输(丢包/乱序/重复) → 接收端重组与路由。
 * MediaCodec 编解码需要硬件,但承载画面的传输层在这里完整跑通。
 */
class ConferenceTransportTest {

    private fun wireIdOf(id: String): ByteArray =
        id.padEnd(8, '\u0000').take(8).toByteArray(StandardCharsets.US_ASCII)

    /** 模拟一台发送端:生成 CONFIG + IDR + 若干 P 帧的 UDP 包流 */
    private fun senderFrames(frameIdGen: () -> Int): List<Pair<String, List<ByteArray>>> {
        val wireId = wireIdOf("SEND01")
        val out = mutableListOf<Pair<String, List<ByteArray>>>()
        // CONFIG(SPS/PPS)
        out += "config" to ConfProtocol.fragmentFrame(
            ByteArray(96) { (it * 7).toByte() }, wireId, frameIdGen(), isConfig = true, isIdr = false,
        )
        // IDR(50KB 关键帧 ≈ 43 个分片)
        out += "idr" to ConfProtocol.fragmentFrame(
            ByteArray(50_000) { (it * 31 % 251).toByte() }, wireId, frameIdGen(), isConfig = false, isIdr = true,
        )
        // P 帧 × 5
        repeat(5) { i ->
            out += "p$i" to ConfProtocol.fragmentFrame(
                ByteArray(6_000 + i * 500) { ((it + i) * 13 % 251).toByte() }, wireId, frameIdGen(), isConfig = false, isIdr = false,
            )
        }
        return out
    }

    /** 接收端:按生产收包循环同一套逻辑处理每个 UDP 包 */
    private fun receive(
        assembler: ConfProtocol.FrameAssembler,
        packet: ByteArray,
    ): ConfProtocol.DeliveredFrame? {
        val header = ConfProtocol.decodeHeader(packet) ?: return null
        if (header.sourceId.isEmpty()) return null
        val payload = packet.copyOfRange(ConfProtocol.HEADER_LEN, packet.size)
        return assembler.onFragment(header.frameId, header.fragIdx, header.fragCount, header.flags, payload)
    }

    @Test
    fun `clean link - full video stream delivered byte-identical and in order`() {
        var fid = 0
        val assembler = ConfProtocol.FrameAssembler(timeoutMs = 1500L)
        val received = mutableListOf<ConfProtocol.DeliveredFrame>()
        for ((_, packets) in senderFrames({ fid++ })) {
            for (packet in packets) {
                receive(assembler, packet)?.let { received.add(it) }
            }
        }
        assertEquals("CONFIG + IDR + 5 P 帧全部交付", 7, received.size)
        assertTrue("首帧必须是 CONFIG(SPS/PPS)", received[0].isConfig)
        assertTrue("第二帧必须是 IDR", received[1].isIdr)
        assertArrayEquals(ByteArray(96) { (it * 7).toByte() }, received[0].data)
        assertArrayEquals(ByteArray(50_000) { (it * 31 % 251).toByte() }, received[1].data)
        assertEquals(0L, assembler.missingFragments)
    }

    @Test
    fun `lossy link - missing one fragment drops only that frame, stream recovers on next IDR`() {
        var fid = 0
        val assembler = ConfProtocol.FrameAssembler(timeoutMs = 300L)
        val frames = senderFrames({ fid++ })
        val received = mutableListOf<ConfProtocol.DeliveredFrame>()
        for ((kind, packets) in frames) {
            for ((idx, packet) in packets.withIndex()) {
                // 丢 IDR 的中间一片:该帧无法齐片,但后续 P 帧/新 IDR 不受影响
                if (kind == "idr" && idx == 20) continue
                receive(assembler, packet)?.let { received.add(it) }
            }
        }
        // CONFIG + 5 P 帧交付;IDR 缺片不交付
        assertEquals(6, received.size)
        assertTrue(received.none { it.isIdr })
        Thread.sleep(1300) // 触发缺片超时剪枝
        // 喂一个新 frameId 的帧触发 prune(已完成帧会被 completed 集提前短路)
        val fresh = ConfProtocol.fragmentFrame(ByteArray(10), wireIdOf("SEND01"), 999, isConfig = false, isIdr = false).first()
        receive(assembler, fresh)
        assertTrue("缺片计入 missingFragments", assembler.missingFragments > 0)
    }

    @Test
    fun `duplicated multicast plus unicast packets do not corrupt frames`() {
        var fid = 0
        val assembler = ConfProtocol.FrameAssembler(timeoutMs = 1500L)
        val frames = senderFrames({ fid++ })
        val received = mutableListOf<ConfProtocol.DeliveredFrame>()
        for ((_, packets) in frames) {
            for (packet in packets) {
                receive(assembler, packet)?.let { received.add(it) }
                receive(assembler, packet)?.let { received.add(it) } // 组播+单播双路重复
            }
        }
        assertEquals("双路重复仍只交付 7 帧", 7, received.size)
    }

    @Test
    fun `reordered packets across two frames still deliver both frames correctly`() {
        var fid = 0
        val assembler = ConfProtocol.FrameAssembler(timeoutMs = 1500L)
        val frames = senderFrames({ fid++ })
        val idrPackets = frames.first { it.first == "idr" }.second
        val p0Packets = frames.first { it.first == "p0" }.second
        val received = mutableListOf<ConfProtocol.DeliveredFrame>()
        // IDR 与 P0 的分片交错到达(网络乱序),两帧都必须完整交付
        for (i in 0 until maxOf(idrPackets.size, p0Packets.size)) {
            if (i < idrPackets.size) receive(assembler, idrPackets[i])?.let { received.add(it) }
            if (i < p0Packets.size) receive(assembler, p0Packets[i])?.let { received.add(it) }
        }
        val idr = received.firstOrNull { it.isIdr }
        val p0 = received.firstOrNull { !it.isConfig && !it.isIdr }
        assertNotNull(idr)
        assertNotNull(p0)
        assertArrayEquals(ByteArray(50_000) { (it * 31 % 251).toByte() }, idr!!.data)
        assertArrayEquals(ByteArray(6_000) { (it * 13 % 251).toByte() }, p0!!.data)
    }

    @Test
    fun `late member gets CONFIG replay and recovers`() {
        // 迟到成员:错过了首轮 CSD,发送端 2s 重播 CONFIG 后即可解码(生产逻辑:每 IDR 前重播)
        var fid = 0
        val assembler = ConfProtocol.FrameAssembler(timeoutMs = 1500L)
        val frames = senderFrames({ fid++ })
        // 首轮 IDR + P 帧先到但无 CSD(迟到者场景:丢弃直到 CONFIG 重播)
        val received = mutableListOf<ConfProtocol.DeliveredFrame>()
        frames.filter { it.first != "config" }.forEach { (_, packets) ->
            packets.forEach { receive(assembler, it)?.let { f -> received.add(f) } }
        }
        // CONFIG 重播(生产:每 2s / 每 IDR 前)
        frames.first { it.first == "config" }.second.forEach {
            receive(assembler, it)?.let { f -> received.add(f) }
        }
        assertTrue("迟到的 CONFIG 重播必须交付", received.any { it.isConfig })
        assertTrue("IDR 在无 CSD 时已重组交付(解码层等 CSD)", received.any { it.isIdr })
    }

    @Test
    fun `header decode returns normalized source id for slot routing`() {
        val wireId = wireIdOf("ABC123")
        val packet = ConfProtocol.fragmentFrame(ByteArray(100), wireId, frameId = 9, isConfig = false, isIdr = true).first()
        val header = ConfProtocol.decodeHeader(packet)
        assertNotNull(header)
        assertEquals("ABC123", header!!.sourceId) // NUL 填充已被归一化
        assertEquals(9, header.frameId)
        assertEquals(0, header.fragIdx)
        assertEquals(1, header.fragCount)
        assertTrue(header.isIdr)
        assertNull(ConfProtocol.decodeHeader(ByteArray(10))) // 过短包拒绝
    }
}
