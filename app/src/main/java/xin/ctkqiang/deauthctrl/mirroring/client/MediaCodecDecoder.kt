package xin.ctkqiang.deauthctrl.mirroring.client

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer

/**
 * 客户端 H.264 解码器。
 *
 * ## 协议
 * 每帧格式: [4B 大端序长度][1B 类型(0x01=CSD, 0x00=帧)][NAL 数据]
 * - CSD 帧: SPS/PPS 以 0x00000001 起始码分隔，queueInputBuffer 带 BUFFER_FLAG_CODEC_CONFIG
 * - 普通帧: 直接送入解码器
 */
class MediaCodecDecoder {

    companion object {
        private const val TAG = "MediaCodecDecoder"
        const val VIDEO_WIDTH = 1280
        const val VIDEO_HEIGHT = 720
        const val MARKER_CSD = 0x01.toByte()
        const val MARKER_FRAME = 0x00.toByte()
    }

    private var codec: MediaCodec? = null
    private var configured = false
    private var csdReceived = false

    @Volatile var isRunning = false
        private set

    fun configure(surface: Surface) {
        if (isRunning) return
        try {
            val format = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC, VIDEO_WIDTH, VIDEO_HEIGHT
            )
            val c = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            c.configure(format, surface, null, 0)
            c.start()
            codec = c
            configured = true
            isRunning = true
            Log.d(TAG, "解码器就绪")
        } catch (e: Exception) {
            Log.e(TAG, "解码器配置失败", e)
        }
    }

    /**
     * 喂入一帧数据。
     *
     * @param packet [4B length][1B marker][NAL data]
     */
    fun feedFrame(packet: ByteArray) {
        val c = codec ?: return
        if (!configured || packet.size < 6) return

        // 跳过 4 字节长度前缀，读取 marker
        val marker = packet[4]
        val dataLen = packet.size - 5
        val data = ByteArray(dataLen)
        System.arraycopy(packet, 5, data, 0, dataLen)

        val flags = if (marker == MARKER_CSD) {
            csdReceived = true
            Log.d(TAG, "收到 CSD: ${data.size}B")
            MediaCodec.BUFFER_FLAG_CODEC_CONFIG
        } else 0

        try {
            val idx = c.dequeueInputBuffer(0) // 非阻塞，低延迟
            if (idx < 0) return
            val buf = c.getInputBuffer(idx) ?: return
            buf.clear()
            buf.put(data)
            c.queueInputBuffer(idx, 0, data.size, System.nanoTime() / 1000, flags)
        } catch (e: Exception) {
            Log.w(TAG, "feedFrame", e)
        }

        // 取出解码完成的帧渲染
        drainOutput(c)
    }

    private fun drainOutput(c: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val idx = c.dequeueOutputBuffer(info, 0)
            if (idx < 0) break
            c.releaseOutputBuffer(idx, true)
        }
    }

    fun release() {
        isRunning = false; configured = false; csdReceived = false
        try { codec?.stop() } catch (_: Exception) {}
        try { codec?.release() } catch (_: Exception) {}
        codec = null
    }
}
