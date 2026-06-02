package xin.ctkqiang.deauthctrl.mirroring.host

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.channels.Channel
import java.nio.ByteBuffer

/**
 * 屏幕采集 + H.264 硬件编码。
 *
 * ## CSD 传递策略
 * 编码器在 [onOutputFormatChanged] 回调中提供 SPS/PPS（csd-0/csd-1）。
 * 这些数据被打包为特殊帧（CSD_MARKER = 0x01）通过 Channel 发送，
 * VideoStreamServer 会缓存并在新客户端连接时优先发送。
 */
class ScreenCaptureManager {

    companion object {
        private const val TAG = "ScreenCaptureMgr"
        const val VIDEO_WIDTH = 1280
        const val VIDEO_HEIGHT = 720
        private const val BITRATE = 2_000_000
        private const val FRAME_RATE = 30
        private const val I_FRAME_INTERVAL_SEC = 1

        /** CSD 帧标记：packet[4] = 0x01 表示 CSD，0x00 表示普通帧 */
        const val CSD_MARKER = 0x01.toByte()
        const val FRAME_MARKER = 0x00.toByte()
    }

    private var mediaProjection: MediaProjection? = null
    private var mediaCodec: MediaCodec? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var codecThread: HandlerThread? = null
    private var csdSent = false

    /** [packetSize][CSD_MARKER/FRAME_MARKER][NAL data...] */
    val encodedDataChannel = Channel<ByteArray>(Channel.UNLIMITED)

    @Volatile var isRunning = false
        private set

    fun start(projection: MediaProjection, densityDpi: Int) {
        if (isRunning) return
        csdSent = false

        mediaProjection = projection.apply {
            registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { release() }
            }, Handler(Looper.getMainLooper()))
        }

        val format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC, VIDEO_WIDTH, VIDEO_HEIGHT
        ).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SEC)
            setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
            setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31)
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_PRIORITY, 0)
        }

        val codec = try {
            MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "创建编码器失败", e)
            return
        }
        mediaCodec = codec

        codecThread = HandlerThread("H264-Enc").apply { start() }
        codec.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {}

            override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                if (info.size <= 0) {
                    codec.releaseOutputBuffer(index, false)
                    return
                }
                val buf = codec.getOutputBuffer(index) ?: run {
                    codec.releaseOutputBuffer(index, false)
                    return
                }
                val data = ByteArray(info.size)
                buf.position(info.offset)
                buf.get(data, 0, info.size)

                // 封装: [4B 长度][1B 类型][NAL 数据]
                val marker = if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    CSD_MARKER
                } else {
                    FRAME_MARKER
                }
                val packet = ByteArray(4 + 1 + data.size)
                writeIntBE(packet, 0, 1 + data.size)
                packet[4] = marker
                System.arraycopy(data, 0, packet, 5, data.size)
                encodedDataChannel.trySend(packet)
                codec.releaseOutputBuffer(index, false)
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                Log.e(TAG, "编码器错误", e)
            }

            override fun onOutputFormatChanged(codec: MediaCodec, fmt: MediaFormat) {
                // 提取 CSD（SPS/PPS）并作为独立帧发送
                try {
                    val csd0 = fmt.getByteBuffer("csd-0") ?: return
                    val csd1 = fmt.getByteBuffer("csd-1") ?: return
                    val sps = ByteArray(csd0.remaining()).also { csd0.get(it); csd0.rewind() }
                    val pps = ByteArray(csd1.remaining()).also { csd1.get(it); csd1.rewind() }
                    val combined = ByteArray(4 + sps.size + 4 + pps.size)
                    // 起始码 0x00000001 + SPS
                    combined[0] = 0; combined[1] = 0; combined[2] = 0; combined[3] = 1
                    System.arraycopy(sps, 0, combined, 4, sps.size)
                    // 起始码 + PPS
                    val ppsOff = 4 + sps.size
                    combined[ppsOff] = 0; combined[ppsOff + 1] = 0; combined[ppsOff + 2] = 0; combined[ppsOff + 3] = 1
                    System.arraycopy(pps, 0, combined, ppsOff + 4, pps.size)
                    val packet = ByteArray(4 + 1 + combined.size)
                    writeIntBE(packet, 0, 1 + combined.size)
                    packet[4] = CSD_MARKER
                    System.arraycopy(combined, 0, packet, 5, combined.size)
                    encodedDataChannel.trySend(packet)
                    csdSent = true
                    Log.d(TAG, "CSD 已发送 — SPS=${sps.size}B PPS=${pps.size}B")
                } catch (e: Exception) {
                    Log.w(TAG, "提取 CSD 失败", e)
                }
            }
        }, Handler(codecThread!!.looper))

        val inputSurface = codec.createInputSurface()
        codec.start()

        virtualDisplay = mediaProjection!!.createVirtualDisplay(
            "ScreenMirror", VIDEO_WIDTH, VIDEO_HEIGHT, densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            inputSurface, null, null,
        )

        isRunning = true
        Log.d(TAG, "采集已启动 — ${VIDEO_WIDTH}x${VIDEO_HEIGHT}")
    }

    fun release() {
        isRunning = false
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        try { mediaCodec?.stop() } catch (_: Exception) {}
        try { mediaCodec?.release() } catch (_: Exception) {}
        mediaCodec = null
        try { mediaProjection?.stop() } catch (_: Exception) {}
        mediaProjection = null
        try { codecThread?.quitSafely() } catch (_: Exception) {}
        codecThread = null
        encodedDataChannel.close()
    }
}

/** 写入 4 字节大端序整数 */
private fun writeIntBE(dst: ByteArray, offset: Int, value: Int) {
    dst[offset] = ((value shr 24) and 0xFF).toByte()
    dst[offset + 1] = ((value shr 16) and 0xFF).toByte()
    dst[offset + 2] = ((value shr 8) and 0xFF).toByte()
    dst[offset + 3] = (value and 0xFF).toByte()
}
