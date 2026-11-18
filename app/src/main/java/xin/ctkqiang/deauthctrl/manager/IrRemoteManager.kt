package xin.ctkqiang.deauthctrl.manager

import android.content.Context
import android.hardware.ConsumerIrManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/**
 * 红外遥控 —— ConsumerIrManager
 *
 * 走系统红外发射 API（小米/Mi Remote、华为、三星等带红外头的机型）。
 * 设备无红外发射器时 hasIrEmitter() = false，界面降级为只读说明。
 *
 * 协议支持：
 * - NEC：38kHz 载波，地址码+命令码（电视机最通用）
 * - RC-5 / RC-6：飞利浦系
 * - 原始脉冲：任意频率 + 微秒级 pattern 数组，可粘贴 Pronto/自定义码
 */
class IrRemoteManager(context: Context) {

    private val ir = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    val hasEmitter: Boolean get() = ir?.hasIrEmitter() == true

    data class IrInfo(
        val available: Boolean,
        val carrierFreqs: List<Int>,
    )

    val info: IrInfo
        get() {
            val emitter = ir ?: return IrInfo(false, emptyList())
            if (!emitter.hasIrEmitter()) return IrInfo(false, emptyList())
            val freqs = runCatching {
                emitter.carrierFrequencies?.flatMap { r ->
                    listOf(r.minFrequency, r.maxFrequency)
                } ?: emptyList()
            }.getOrDefault(emptyList())
            return IrInfo(true, freqs)
        }

    private val _lastTransmit = MutableStateFlow<String?>(null)
    val lastTransmit: StateFlow<String?> = _lastTransmit

    /** 最近一次发射的脉冲序列(µs,交替开/关)—— 逻辑示波器红外通道的数据源 */
    private val _lastPattern = MutableStateFlow<IntArray?>(null)
    val lastPattern: StateFlow<IntArray?> = _lastPattern

    /**
     * 发射 NEC 码。
     *
     * @param address 地址码（常见 TV 为 0x00 或品牌码）
     * @param command 命令码（电源/音量等）
     * @param repeat  重复次数（墙壁反射弱时增加）
     */
    fun transmitNec(address: Int, command: Int, repeat: Int = 1) {
        val carrier = 38_000
        val pattern = buildNecPattern(address, command)
        transmitRaw(carrier, pattern, repeat, "NEC 0x%02X%02X".format(address, command))
    }

    /** 发射 RC-5 码（飞利浦系设备） */
    fun transmitRc5(address: Int, command: Int, repeat: Int = 1) {
        val carrier = 36_000
        val pattern = buildRc5Pattern(address, command)
        transmitRaw(carrier, pattern, repeat, "RC-5 0x%02X%02X".format(address, command))
    }

    /** 发射 Sony SIRC 码(12bit 标准 / 15/20bit 扩展;按位数自动) */
    fun transmitSony(command: Int, device: Int, bits: Int = 12, repeat: Int = 1) {
        val carrier = 40_000
        val pattern = buildSonyPattern(command, device, bits)
        transmitRaw(carrier, pattern, repeat, "Sony SIRC $bits-bit 0x%02X%02X".format(device, command))
    }

    /**
     * Sony SIRC:引导 2400µs 开 + 600µs 关;
     * 逻辑 1 = 1200µs 开 + 600µs 关;逻辑 0 = 600µs 开 + 600µs 关。
     * 12bit = 7 命令 + 5 设备(LSB 先发);15/20bit 为扩展设备码。
     */
    private fun buildSonyPattern(command: Int, device: Int, bits: Int): IntArray {
        val n = bits.coerceIn(12, 20)
        val values = mutableListOf<Int>()
        values.add(2400); values.add(600) // 引导
        for (i in 0 until n) {
            val bit = (command shr i) and 1
            if (bit == 1) { values.add(1200); values.add(600) } else { values.add(600); values.add(600) }
        }
        // 15/20bit 的设备扩展位(高位在命令之后,同样 LSB 先发)
        if (n >= 15) {
            val devBits = n - 12
            for (i in 0 until devBits) {
                val bit = (device shr i) and 1
                if (bit == 1) { values.add(1200); values.add(600) } else { values.add(600); values.add(600) }
            }
        }
        return values.toIntArray()
    }

    /** 发射 NECext(NEC 时序, 16bit 地址 LSB 先发 + 8bit 命令, 无逆码 —— Flipper 固件文档) */
    fun transmitNecExtended(address: Int, command: Int, repeat: Int = 1) {
        val pattern = buildNecExtendedPattern(address, command)
        transmitRaw(38_000, pattern, repeat, "NECext 0x%04X:%02X".format(address and 0xFFFF, command and 0xFF))
    }

    /** 发射 Samsung32(38kHz, 4500/4500 引导, 32bit LSB 先发, 0=560/560 1=560/1690) */
    fun transmitSamsung32(address: Int, command: Int, repeat: Int = 1) {
        val pattern = buildSamsung32Pattern(address, command)
        transmitRaw(38_000, pattern, repeat, "Samsung32 0x%04X:%04X".format(address and 0xFFFF, command and 0xFFFF))
    }

    /** 发射 RC-6 mode 0(36kHz, 2666/889 引导, 444µs 曼彻斯特, 8bit 地址 + 8bit 命令 MSB 先发) */
    fun transmitRc6(address: Int, command: Int, repeat: Int = 1) {
        val pattern = buildRc6Pattern(address, command)
        transmitRaw(36_000, pattern, repeat, "RC-6 0x%02X:%02X".format(address and 0xFF, command and 0xFF))
    }

    /** 发射 RC5X(RC-5 14bit: 起始位+字段位扩展命令第 7 位) */
    fun transmitRc5x(address: Int, command: Int, repeat: Int = 1) {
        val pattern = buildRc5xPattern(address, command)
        transmitRaw(36_000, pattern, repeat, "RC-5X 0x%02X:%02X".format(address and 0x1F, command and 0x7F))
    }

    /** 发射 SIRC20(Sony 20bit: 7 命令 + 5 设备 + 8 扩展设备, 40kHz, LSB 先发) */
    fun transmitSirc20(address: Int, command: Int, repeat: Int = 1) {
        val pattern = buildSony20Pattern(address, command)
        transmitRaw(40_000, pattern, repeat, "SIRC20 0x%04X:%02X".format(address and 0x1FFF, command and 0x7F))
    }

    /** Sony SIRC20: 7bit 命令 + 13bit 设备(LSB 先发),时序同 SIRC(2400/600 引导) */
    private fun buildSony20Pattern(address: Int, command: Int): IntArray {
        val v = mutableListOf<Int>()
        v.add(2400); v.add(600)
        for (i in 0 until 7) { val b = (command shr i) and 1; v.add(if (b == 1) 1200 else 600); v.add(600) }
        for (i in 0 until 13) { val b = (address shr i) and 1; v.add(if (b == 1) 1200 else 600); v.add(600) }
        return v.toIntArray()
    }

    private fun buildNecExtendedPattern(address: Int, command: Int): IntArray {
        val v = mutableListOf<Int>()
        v.add(9000); v.add(4500) // 引导
        for (i in 0 until 16) { val b = (address shr i) and 1; v.add(560); v.add(if (b == 1) 1690 else 560) }
        for (i in 0 until 8) { val b = (command shr i) and 1; v.add(560); v.add(if (b == 1) 1690 else 560) }
        v.add(560)
        return v.toIntArray()
    }

    private fun buildSamsung32Pattern(address: Int, command: Int): IntArray {
        val v = mutableListOf<Int>()
        v.add(4500); v.add(4500) // 引导
        for (i in 0 until 16) { val b = (address shr i) and 1; v.add(560); v.add(if (b == 1) 1690 else 560) }
        for (i in 0 until 16) { val b = (command shr i) and 1; v.add(560); v.add(if (b == 1) 1690 else 560) }
        v.add(560)
        return v.toIntArray()
    }

    private fun buildRc6Pattern(address: Int, command: Int): IntArray {
        // RC-6 mode 0: 位周期 444µs 半位曼彻斯特(1 = 关→开, 0 = 开→关)
        // 数据: 起始位 1 + 模式 000 + 尾随位 0 + 地址 8bit MSB + 命令 8bit MSB
        val halves = mutableListOf<Boolean>() // true=on, false=off, 每半位 444µs
        fun manchester(b: Int) {
            if (b == 1) { halves.add(false); halves.add(true) } else { halves.add(true); halves.add(false) }
        }
        repeat(6) { halves.add(true) }   // leader on 2666µs
        repeat(2) { halves.add(false) }  // leader off 889µs
        manchester(1)                    // 起始位
        manchester(0); manchester(0); manchester(0) // 模式 000
        manchester(0)                    // 尾随位
        for (i in 7 downTo 0) manchester((address shr i) and 1)
        for (i in 7 downTo 0) manchester((command shr i) and 1)
        repeat(2) { halves.add(false) }  // 尾随 off 889µs
        return compressStates(halves, 444)
    }

    private fun buildRc5xPattern(address: Int, command: Int): IntArray {
        // RC5X: 起始位 + 字段位(命令第 7 位) + 切换位 + 5bit 地址 + 6bit 命令
        val states = mutableListOf<Boolean>()
        fun rc5bit(b: Int) {
            if (b == 1) { states.add(false); states.add(true) } else { states.add(true); states.add(false) }
        }
        rc5bit(1)                              // 起始位
        rc5bit((command shr 6) and 1)          // 字段位 = 扩展命令位
        rc5bit(0)                              // 切换位
        for (i in 4 downTo 0) rc5bit((address shr i) and 1)
        for (i in 5 downTo 0) rc5bit((command shr i) and 1)
        return compressStates(states, 889)
    }

    /** 相位布尔序列 → 交替 on/off 脉冲(首段必须为 on;首段为 off 时补 1µs) */
    private fun compressStates(states: List<Boolean>, unitUs: Int): IntArray {
        val out = mutableListOf<Int>()
        var prev = true
        var run = 0
        for (s in states) {
            if (s == prev) run++ else { out.add(run * unitUs); prev = s; run = 1 }
        }
        if (run > 0) out.add(run * unitUs)
        if (out.first() == 0) out[0] = 1
        return out.toIntArray()
    }

    /** 发射原始脉冲：pattern 以微秒为单位交替 开/关，首个值为开启时长 */
    fun transmitRaw(carrierHz: Int, pattern: IntArray, repeat: Int = 1, label: String) {
        val emitter = ir ?: run { _lastTransmit.value = "无红外发射器"; return }
        if (!emitter.hasIrEmitter()) {
            _lastTransmit.value = "无红外发射器"
            return
        }
        try {
            repeat(repeat.coerceIn(1, 8)) {
                emitter.transmit(carrierHz, pattern)
            }
            _lastPattern.value = pattern
            _lastTransmit.value = "已发射 · $label · ${carrierHz}Hz × $repeat"
        } catch (t: Throwable) {
            _lastTransmit.value = "发射失败: ${t.message}"
        }
    }

    /**
     * NEC 编码：逻辑 1 = 560µs 开 + 1690µs 关；逻辑 0 = 560µs 开 + 560µs 关；
     * 引导码 9000µs 开 + 4500µs 关；结尾 560µs 开。
     */
    private fun buildNecPattern(address: Int, command: Int): IntArray {
        val out = mutableListOf<Int>()
        fun mark(us: Int) = out.add(us)
        fun space(us: Int) = out.add(us)

        mark(9000); space(4500)
        // 标准 NEC 发送地址、反码、命令、反码共 32 位
        val full = ((address and 0xFF).toLong() shl 24) or
            ((address.inv() and 0xFF).toLong() shl 16) or
            ((command and 0xFF).toLong() shl 8) or
            (command.inv() and 0xFF).toLong()
        var value = full
        for (i in 0 until 32) {
            mark(560)
            space(if (value and 0x80000000L != 0L) 1690 else 560)
            value = value shl 1
        }
        mark(560)
        return out.toIntArray()
    }

    /**
     * RC-5 编码：14 位（起始 2 位 1 + 地址 5 位 + 命令 6 位），889µs 半比特，
     * 曼彻斯特编码 —— 逻辑 1 = 空后载波，逻辑 0 = 载波后空。
     * ConsumerIrManager 的 pattern 必须以 on 段开头，因此先构建状态流再压缩。
     */
    private fun buildRc5Pattern(address: Int, command: Int): IntArray {
        val bits = (1 shl 14) or ((address and 0x1F) shl 6) or (command and 0x3F)
        val states = mutableListOf<Boolean>()
        for (i in 13 downTo 0) {
            val bit = (bits shr i) and 1
            if (bit == 1) { states.add(false); states.add(true) }
            else { states.add(true); states.add(false) }
        }
        // 压缩连续同相段为 pattern（首个元素必须为 on 段）
        val out = mutableListOf<Int>()
        var prev = true
        var run = 0
        for (s in states) {
            if (s == prev) run++ else { out.add(run * 889); prev = s; run = 1 }
        }
        if (run > 0) out.add(run * 889)
        // 首位是 off 时压缩出的首段为 0µs on，补 1µs 保持 pattern 语义
        if (out.first() == 0) out[0] = 1
        return out.toIntArray()
    }

    /** 常用按键 NEC 码表（地址 0x00 通用码；各品牌数字键码差异大，建议用原始码输入精确控制） */
    companion object {
        /** 电视电源：NEC(0x00, 0x45) */
        val TV_POWER = 0x45
        val TV_VOL_UP = 0x15
        val TV_VOL_DOWN = 0x09
        val TV_CH_UP = 0x46
        val TV_CH_DOWN = 0x47
        val TV_MUTE = 0x11
        val TV_INPUT = 0x49
        /** 数字键 0-9（NEC 0x00 地址的通用映射，不同品牌可能有偏差） */
        val DIGITS = intArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09)
    }
}
