package xin.ctkqiang.deauthctrl.manager

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.thread

/** 一个已接入的 USB 串口设备 */
data class UsbSerialDevice(
    val deviceId: Int,
    val vendorId: Int,
    val productId: Int,
    val name: String,
    val chip: String, // 芯片推断:FTDI/CH340/CP210x/CDC-ACM/未知
)

/** 一行收发记录 */
data class SerialLogLine(val timeMs: Long, val direction: Char, val text: String, val hex: String)

/** 串口监视状态 */
data class SerialMonitorState(
    val devices: List<UsbSerialDevice> = emptyList(),
    val connected: Boolean = false,
    val connectedDevice: UsbSerialDevice? = null,
    val baudRate: Int = 9600,
    val dataBits: Int = 8,
    val stopBits: Int = 1,
    val parity: Int = 0, // 0=None 1=Odd 2=Even
    val log: List<SerialLogLine> = emptyList(),
    val hexMode: Boolean = false,
    val error: String? = null,
)

/**
 * USB 串口监视器 —— Arduino IDE 风格
 *
 * 通过 USB OTG 枚举串口设备(FTDI/CH340/CP210x/CDC-ACM),按所选波特率/数据位/
 * 停止位/奇偶配置后双向收发。Android 没有系统级串口 API,CDC-ACM 设备用
 * controlTransfer(0x21, 0x22)设置线路编码后走 bulk 端点;FTDI/CH340 需要
 * 厂商驱动,系统未内置时只能枚举、无法通讯 —— 诚实标注兼容性。
 */
class SerialMonitorManager(private val context: Context) {

    private val _state = MutableStateFlow(SerialMonitorState())
    val state: StateFlow<SerialMonitorState> = _state

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
    private var connection: UsbDeviceConnection? = null
    private var deviceRef: UsbDevice? = null
    private var inEndpoint: UsbEndpoint? = null
    private var outEndpoint: UsbEndpoint? = null

    @Volatile private var stopRead = false

    private val attachReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) refreshDevices()
        }
    }

    fun start() {
        val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        runCatching { context.registerReceiver(attachReceiver, filter) }
        refreshDevices()
    }

    fun stop() {
        runCatching { context.unregisterReceiver(attachReceiver) }
        disconnect()
    }

    /** 枚举当前 USB 设备中的串口类设备 */
    fun refreshDevices() {
        val list = usbManager?.deviceList?.values.orEmpty().filter { isSerialLike(it) }.map { d ->
            UsbSerialDevice(
                deviceId = d.deviceId,
                vendorId = d.vendorId,
                productId = d.productId,
                name = d.productName ?: "USB 设备",
                chip = chipName(d.vendorId, d.productId),
            )
        }
        _state.value = _state.value.copy(devices = list)
    }

    /** 连接设备并开始收发(CDC-ACM:设置线路编码后 bulk 收发) */
    fun connect(device: UsbSerialDevice) {
        disconnect()
        val dev = usbManager?.deviceList?.values?.firstOrNull { it.deviceId == device.deviceId } ?: run {
            _state.value = _state.value.copy(error = "设备已拔出")
            return
        }
        // 请求 USB 权限:系统会弹确认框
        val perm = usbManager?.hasPermission(dev) == true
        if (!perm) {
            val pi = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION).setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE)
            usbManager?.requestPermission(dev, pi)
            _state.value = _state.value.copy(error = "请在系统弹窗中允许 USB 访问,然后重新点连接")
            return
        }
        val conn = usbManager?.openDevice(dev) ?: run {
            _state.value = _state.value.copy(error = "打开 USB 设备失败")
            return
        }
        // 找 bulk 端点(优先带数据接口的 bulk in/out)
        var foundIfc: UsbInterface? = null
        for (i in 0 until dev.interfaceCount) {
            val ifc = dev.getInterface(i)
            val inE = ifc.getEndpoint(0)?.takeIf { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_IN }
            val outE = ifc.getEndpoint(1)?.takeIf { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_OUT }
            if (inE != null && outE != null) { foundIfc = ifc; inEndpoint = inE; outEndpoint = outE; break }
        }
        if (foundIfc == null) {
            conn.close()
            _state.value = _state.value.copy(error = "未找到 bulk 端点(该芯片需厂商驱动,系统未内置)")
            return
        }
        runCatching { conn.claimInterface(foundIfc, true) }
        // CDC-ACM 线路编码:0x21=host-to-device class request, 0x22=SET_LINE_CODING
        val s = _state.value
        val lineCoding = byteArrayOf(
            (s.baudRate and 0xFF).toByte(), ((s.baudRate shr 8) and 0xFF).toByte(),
            ((s.baudRate shr 16) and 0xFF).toByte(), ((s.baudRate shr 24) and 0xFF).toByte(),
            0, // stop bits (0=1, 2=2)
            s.parity.toByte(),
            s.dataBits.toByte(),
        )
        runCatching { conn.controlTransfer(0x21, 0x22, 0, 0, lineCoding, lineCoding.size, 1000) }

        connection = conn
        deviceRef = dev
        stopRead = false
        _state.value = _state.value.copy(connected = true, connectedDevice = device, error = null)
        appendLog('S', "已连接 ${device.name} (${device.chip}) @ ${s.baudRate}bps", "")
        // 读循环
        thread(name = "serial-read") {
            val buf = ByteArray(256)
            while (!stopRead) {
                val n = runCatching { connection?.bulkTransfer(inEndpoint, buf, buf.size, 200) }.getOrNull() ?: -1
                if (n > 0) {
                    val bytes = buf.copyOf(n)
                    val text = String(bytes, Charsets.US_ASCII).replace("\r\n", "\n").replace("\r", "\n")
                    appendLog('R', text, bytes.joinToString(" ") { "%02X".format(it) })
                }
            }
        }
    }

    /** 发送一行(自动补 CR+LF,Arduino Serial.println 风格) */
    fun sendLine(text: String) {
        if (connection == null) return
        val payload = (text + "\r\n").toByteArray(Charsets.US_ASCII)
        val sent = runCatching { connection?.bulkTransfer(outEndpoint, payload, payload.size, 1000) }.getOrNull() ?: -1
        if (sent > 0) appendLog('T', text + "\n", payload.joinToString(" ") { "%02X".format(it) })
    }

    /** 应用参数变更(波特率等) */
    fun setParams(baud: Int, dataBits: Int, stopBits: Int, parity: Int) {
        _state.value = _state.value.copy(baudRate = baud, dataBits = dataBits, stopBits = stopBits, parity = parity)
        if (_state.value.connected) {
            // 重连以应用新参数
            val dev = _state.value.connectedDevice
            if (dev != null) { disconnect(); connect(dev) }
        }
    }

    fun setHexMode(on: Boolean) { _state.value = _state.value.copy(hexMode = on) }
    fun clearLog() { _state.value = _state.value.copy(log = emptyList()) }

    fun disconnect() {
        stopRead = true
        runCatching { connection?.close() }
        connection = null
        deviceRef = null
        inEndpoint = null
        outEndpoint = null
        _state.value = _state.value.copy(connected = false, connectedDevice = null)
    }

    private fun appendLog(dir: Char, text: String, hex: String) {
        val s = _state.value
        _state.value = s.copy(log = (s.log + SerialLogLine(System.currentTimeMillis(), dir, text, hex)).takeLast(500))
    }

    private fun isSerialLike(d: UsbDevice): Boolean {
        // 设备类 2 = Communications(CDC),或已知串口芯片 VID
        if (d.deviceClass == 2) return true
        return d.vendorId in setOf(0x0403, 0x1A86, 0x10C4, 0x2341, 0x2A03) // FTDI/CH340/Silabs/Arduino
    }

    private fun chipName(vid: Int, pid: Int): String = when (vid) {
        0x0403 -> "FTDI FT232/FT4232"
        0x1A86 -> "WCH CH340/CH341"
        0x10C4 -> "Silabs CP210x"
        0x2341 -> "Arduino CDC"
        else -> "USB 串口类设备(需驱动支持)"
    }

    companion object {
        const val ACTION_USB_PERMISSION = "xin.ctkqiang.deauthctrl.USB_PERMISSION"
    }
}
