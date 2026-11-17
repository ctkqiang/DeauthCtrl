package xin.ctkqiang.deauthctrl.manager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

/**
 * 会议视频协议层单元测试 —— 生产代码与测试共用 ConfProtocol 同一份实现。
 *
 * 每个用例对应一次真实修复(由审查确认的必崩根因),防回归:
 * - 8B 定宽源 ID 写读往返(arraycopy 越界修复)
 * - IDR 多分片重组不再互相清空
 * - CONFIG 独立成帧、整帧 flags 路由
 * - NUL 填充归一化(槽位键错位修复)
 * - 组播+单播重复帧去重、缺片超时整帧丢弃
 */
class ConferenceProtocolTest {

    private fun wireIdOf(id: String): ByteArray =
        id.padEnd(8, '\u0000').take(8).toByteArray(StandardCharsets.US_ASCII)

    @Test
    fun `header round trip - 6 char id packed into 8B field`() {
        val id = "ABC123"
        val dst = ByteArray(ConfProtocol.HEADER_LEN)
        ConfProtocol.writeHeader(dst, wireIdOf(id), frameId = 0x1234, fragIdx = 3, fragCount = 12, flags = 0x02 or 0x04)

        // 源 ID 8B + 两个 NUL 填充
        assertEquals("ABC123\u0000\u0000", String(dst, 0, 8, StandardCharsets.US_ASCII))
        // frameId 2B BE
        assertEquals(0x12, dst[8].toInt() and 0xFF)
        assertEquals(0x34, dst[9].toInt() and 0xFF)
        assertEquals(3, dst[10].toInt() and 0xFF)
        assertEquals(12, dst[11].toInt() and 0xFF)
        assertEquals(0x06, dst[12].toInt() and 0xFF)
        assertEquals(0, dst[13].toInt())
    }

    @Test
    fun `normalizeId strips NUL and whitespace padding`() {
        assertEquals("ABC123", ConfProtocol.normalizeId("ABC123\u0000\u0000"))
        assertEquals("ABC123", ConfProtocol.normalizeId("ABC123  "))
        assertEquals("ABC123", ConfProtocol.normalizeId("ABC123"))
        assertEquals("", ConfProtocol.normalizeId("\u0000\u0000"))
    }

    @Test
    fun `24-fragment IDR reassembles in order`() {
        val assembler = ConfProtocol.FrameAssembler(timeoutMs = 1500L)
        val payloads = (0 until 24).map { i -> ByteArray(1200) { ((i * 31 + it) % 127).toByte() } }
        var delivered: ConfProtocol.DeliveredFrame? = null
        for (i in 0 until 24) {
            val r = assembler.onFragment(
                frameId = 7, fragIdx = i, fragCount = 24,
                flags = ConfProtocol.FLAG_IDR or (if (i == 0) ConfProtocol.FLAG_FRAME_START else 0) or (if (i == 23) ConfProtocol.FLAG_LAST else 0),
                payload = payloads[i],
            )
            if (r != null) delivered = r
        }
        assertNotNull("24 片齐后必须交付完整帧", delivered)
        assertTrue(delivered!!.isIdr)
        assertFalse(delivered!!.isConfig)
        val expected = ByteArray(24 * 1200)
        payloads.forEachIndexed { i, p -> System.arraycopy(p, 0, expected, i * 1200, 1200) }
        assertArrayEquals(expected, delivered!!.data)
        assertEquals(0L, assembler.missingFragments)
    }

    @Test
    fun `out-of-order fragments still deliver - IDR slices no longer wipe each other`() {
        val assembler = ConfProtocol.FrameAssembler(timeoutMs = 1500L)
        val order = intArrayOf(4, 0, 2, 3, 1) // 乱序 + 首片非先到
        val payloads = (0 until 5).map { i -> ByteArray(100) { ((i + it) % 100).toByte() } }
        var delivered: ConfProtocol.DeliveredFrame? = null
        for (idx in order) {
            val r = assembler.onFragment(
                frameId = 3, fragIdx = idx, fragCount = 5,
                flags = ConfProtocol.FLAG_IDR or (if (idx == 0) ConfProtocol.FLAG_FRAME_START else 0) or (if (idx == 4) ConfProtocol.FLAG_LAST else 0),
                payload = payloads[idx],
            )
            if (r != null) delivered = r
        }
        assertNotNull(delivered)
        val expected = ByteArray(500)
        payloads.forEachIndexed { i, p -> System.arraycopy(p, 0, expected, i * 100, 100) }
        assertArrayEquals(expected, delivered!!.data)
    }

    @Test
    fun `CONFIG frame routes independently - SPS or PPS not dropped by frameId mismatch`() {
        val assembler = ConfProtocol.FrameAssembler(timeoutMs = 1500L)
        // 编码器启动后首个 CONFIG 帧 frameId 从 0 开始,先于任何 IDR
        val delivered = assembler.onFragment(
            frameId = 0, fragIdx = 0, fragCount = 1,
            flags = ConfProtocol.FLAG_CONFIG or ConfProtocol.FLAG_FRAME_START or ConfProtocol.FLAG_LAST,
            payload = ByteArray(64) { it.toByte() },
        )
        assertNotNull(delivered)
        assertTrue(delivered!!.isConfig)
        assertArrayEquals(ByteArray(64) { it.toByte() }, delivered!!.data)
    }

    @Test
    fun `duplicate fragment and duplicate complete frame are ignored`() {
        val assembler = ConfProtocol.FrameAssembler(timeoutMs = 1500L)
        fun send(id: Int, frag: Int) = assembler.onFragment(
            frameId = id, fragIdx = frag, fragCount = 2,
            flags = ConfProtocol.FLAG_IDR or (if (frag == 0) ConfProtocol.FLAG_FRAME_START else ConfProtocol.FLAG_LAST),
            payload = ByteArray(50),
        )
        assertNull(send(9, 0))
        assertNull("重复分片应被忽略", send(9, 0))
        val delivered = send(9, 1)
        assertNotNull(delivered)
        assertNull("同一帧第二次齐片(组播+单播双路)应被去重", send(9, 1))
    }

    @Test
    fun `missing fragment times out and counts as missing`() {
        val assembler = ConfProtocol.FrameAssembler(timeoutMs = 60L)
        assembler.onFragment(
            frameId = 11, fragIdx = 0, fragCount = 4,
            flags = ConfProtocol.FLAG_IDR or ConfProtocol.FLAG_FRAME_START,
            payload = ByteArray(30),
        )
        assembler.onFragment(
            frameId = 11, fragIdx = 2, fragCount = 4,
            flags = ConfProtocol.FLAG_IDR,
            payload = ByteArray(30),
        )
        Thread.sleep(1300) // 超过 timeout 且超过 1s 剪枝周期
        assembler.onFragment(
            frameId = 12, fragIdx = 0, fragCount = 1,
            flags = ConfProtocol.FLAG_LAST or ConfProtocol.FLAG_FRAME_START,
            payload = ByteArray(10),
        )
        assertEquals(2L, assembler.missingFragments)
    }

    @Test
    fun `fragCount change rebuilds buffer instead of corrupting frame`() {
        val assembler = ConfProtocol.FrameAssembler(timeoutMs = 1500L)
        assembler.onFragment(
            frameId = 5, fragIdx = 0, fragCount = 3,
            flags = ConfProtocol.FLAG_FRAME_START, payload = ByteArray(10),
        )
        // 同 frameId 但新 fragCount(发送端参数变化):旧缓冲作废,新帧从头重组;
        // 单分片帧 fragIdx=0 即齐片,立即交付
        val delivered = assembler.onFragment(
            frameId = 5, fragIdx = 0, fragCount = 1,
            flags = ConfProtocol.FLAG_LAST or ConfProtocol.FLAG_FRAME_START, payload = ByteArray(10),
        )
        assertNotNull("fragCount=1 的单分片帧在 fragIdx=0 即完整交付", delivered)
        assertArrayEquals(ByteArray(10), delivered!!.data)
        // 越界分片拒绝
        assertNull(assembler.onFragment(5, 1, 1, ConfProtocol.FLAG_LAST, ByteArray(10)))
    }

    @Test
    fun `invalid fragments rejected`() {
        val assembler = ConfProtocol.FrameAssembler(timeoutMs = 1500L)
        assertNull(assembler.onFragment(1, -1, 2, 0, ByteArray(4)))
        assertNull(assembler.onFragment(1, 2, 2, 0, ByteArray(4)))
        assertNull(assembler.onFragment(1, 0, 0, 0, ByteArray(4)))
    }
}
