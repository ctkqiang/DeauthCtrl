package xin.ctkqiang.deauthctrl

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import xin.ctkqiang.deauthctrl.manager.BleSpamManager
import xin.ctkqiang.deauthctrl.manager.ConfProtocol
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 真机运行时验证 —— 在连接的 Android 设备上执行(connectedDebugAndroidTest):
 *
 * 1. [codecRoundTripThroughProtocol]: 真实 MediaCodec 硬件编码 → 生产协议层分片/重组
 *    (含模拟丢包) → 真实硬件解码出画面帧 —— 会议视频「编码→传输→解码」数据路径真机跑通
 * 2. [bleAdvertisingSession]: 真实 BluetoothLeAdvertiser 广播会话启动并产生会话计数
 * 3. [radarScreenScreenshot]: 真实 UI 导航进设备雷达页并截图落盘
 */
@RunWith(AndroidJUnit4::class)
class RuntimeEvidenceTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun grantPermission(perm: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, perm)
    }

    /** 真实硬件 MediaCodec 编解码 + 生产协议层(分片/重组/丢包)端到端 */
    @Test
    fun codecRoundTripThroughProtocol() {
        val width = 640; val height = 480
        val mime = MediaFormat.MIMETYPE_VIDEO_AVC
        val frames = 12

        // ── 编码器(硬件,Surface 输入) ──
        val encoder = MediaCodec.createEncoderByType(mime)
        encoder.configure(
            MediaFormat.createVideoFormat(mime, width, height).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 1_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, 15)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            },
            null, null, MediaCodec.CONFIGURE_FLAG_ENCODE,
        )
        val surface = encoder.createInputSurface()
        encoder.start()

        val wireId = "EVIDEN2".padEnd(8, '\u0000').take(8).toByteArray()
        val assembler = ConfProtocol.FrameAssembler(timeoutMs = 3000L)
        val delivered = ConcurrentLinkedQueue<ConfProtocol.DeliveredFrame>()
        var frameId = 0
        var csd0: ByteArray? = null
        var csd1: ByteArray? = null
        val paint = Paint().apply { color = Color.WHITE }
        val bufInfo = MediaCodec.BufferInfo()

        for (i in 0 until frames) {
            val canvas = surface.lockCanvas(null)
            canvas.drawColor(if (i % 2 == 0) Color.BLUE else Color.YELLOW)
            canvas.drawText("STREAM $i", 60f, 240f, paint)
            surface.unlockCanvasAndPost(canvas)
            while (true) {
                val idx = encoder.dequeueOutputBuffer(bufInfo, 100_000)
                if (idx >= 0) {
                    val bb = encoder.getOutputBuffer(idx)!!
                    val data = ByteArray(bufInfo.size)
                    bb.position(bufInfo.offset); bb.get(data, 0, bufInfo.size)
                    if ((bufInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        val fmt = encoder.outputFormat
                        fmt.getByteBuffer("csd-0")?.let { csd0 = ByteArray(it.remaining()).also { a -> it.get(a) } }
                        fmt.getByteBuffer("csd-1")?.let { csd1 = ByteArray(it.remaining()).also { a -> it.get(a) } }
                    } else {
                        // 生产发送路径:分片构造(与 broadcastFrame 同一份 ConfProtocol.fragmentFrame)
                        val packets = ConfProtocol.fragmentFrame(
                            data, wireId, frameId++,
                            isConfig = false,
                            isIdr = (bufInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0,
                        )
                        // 模拟 UDP 传输:~3% 丢包
                        packets.forEachIndexed { pidx, pkt ->
                            if (pidx % 33 != 0) {
                                val h = ConfProtocol.decodeHeader(pkt)!!
                                assembler.onFragment(
                                    h.frameId, h.fragIdx, h.fragCount, h.flags,
                                    pkt.copyOfRange(ConfProtocol.HEADER_LEN, pkt.size),
                                )?.let { delivered.add(it) }
                            }
                        }
                    }
                    encoder.releaseOutputBuffer(idx, false)
                    if ((bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                } else if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    break
                } else if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // 下一次循环读取 CSD
                }
            }
            if (i % 4 == 0) Thread.sleep(25)
        }
        surface.release(); encoder.stop(); encoder.release()

        assertTrue("重组必须交付完整视频帧(实际 ${delivered.size})", delivered.size >= frames / 2)
        assertTrue("编码器必须产出 CSD(SPS/PPS)", csd0 != null && csd1 != null)

        // ── 真实硬件解码重组帧 ──
        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(
            MediaFormat.createVideoFormat(mime, width, height).apply {
                setByteBuffer("csd-0", java.nio.ByteBuffer.wrap(csd0!!))
                setByteBuffer("csd-1", java.nio.ByteBuffer.wrap(csd1!!))
            },
            null, null, 0,
        )
        decoder.start()
        var outFrames = 0
        val decInfo = MediaCodec.BufferInfo()
        for (frame in delivered) {
            val inIdx = decoder.dequeueInputBuffer(100_000)
            if (inIdx >= 0) {
                val ib = decoder.getInputBuffer(inIdx)!!
                ib.clear(); ib.put(frame.data)
                val flags = if (frame.isIdr) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                decoder.queueInputBuffer(inIdx, 0, frame.data.size, 0, flags)
            }
            while (true) {
                val outIdx = decoder.dequeueOutputBuffer(decInfo, 20_000)
                if (outIdx >= 0) {
                    if (decInfo.size > 0) outFrames++
                    decoder.releaseOutputBuffer(outIdx, false)
                } else break
            }
        }
        decoder.stop(); decoder.release()
        assertTrue("硬件解码器必须产出真实画面帧(实际 $outFrames)", outFrames >= 3)
    }

    /** 真实 BluetoothLeAdvertiser 广播会话 */
    @Test
    fun bleAdvertisingSession() {
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        Assume.assumeTrue("蓝牙未开启,跳过真机广播会话测试", btManager.adapter?.isEnabled == true)
        grantPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
        grantPermission(Manifest.permission.BLUETOOTH_CONNECT)

        val manager = BleSpamManager(context)
        manager.start()
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline && manager.stats.value.sessions == 0L) {
            Thread.sleep(200)
        }
        manager.stop()
        assertTrue(
            "真实广播会话必须已启动(实际 sessions=${manager.stats.value.sessions}, failed=${manager.stats.value.failed})",
            manager.stats.value.sessions > 0,
        )
    }

    /** UI 导航进入设备雷达页并截图落盘 */
    @Test
    fun radarScreenScreenshot() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            },
        )
        val radarEntry = device.wait(Until.findObject(By.text("设备雷达")), 30_000)
        assertNotNull("主页必须出现设备雷达入口", radarEntry)
        radarEntry.click()
        val radarHeader = device.wait(Until.findObject(By.text("设备雷达")), 15_000)
        assertNotNull("雷达页必须打开", radarHeader)
        Thread.sleep(4000) // 等扫描盘动画与首轮扫描
        val shot = File("/sdcard/DeauthCtrl/radar_evidence.png")
        shot.parentFile?.mkdirs()
        val ok = device.takeScreenshot(shot)
        assertTrue("雷达页截图必须落盘", ok && shot.exists() && shot.length() > 10_000)
    }
}
