package xin.ctkqiang.deauthctrl.manager

/**
 * 对讲机信道数据库 —— 全量离线内置,零网络。
 *
 * 列定义(与宝锋等菜单一致):
 *   Ch 旋钮信道 / Freq 频率 MHz / Tone 亚音类型 /
 *   R-DCS(Menu 10 接收数字亚音)/ R-CTCS(Menu 11 接收模拟亚音)/
 *   T-DCS(Menu 12 发射数字亚音)/ T-CTCS(Menu 13 发射模拟亚音)
 *
 * 收录:标准 16 信道 · 中国公众 409(20 信道)· 马来西亚 UHF 477(39)·
 * PMR446 欧/英/新(16)· 美国 FRS(22)+ GMRS 中继(8)· MURS(5)·
 * 海事 VHF 关键信道(9)· 航空应急(2)。发射前务必核实当地法规与功率限制。
 */
object WalkieChannelDb {

    data class WalkieChannel(
        val ch: String,
        val freq: Double,
        val toneType: String = "None",
        val rDcs: String = "OFF",
        val rCtcs: String = "OFF",
        val tDcs: String = "OFF",
        val tCtcs: String = "OFF",
        val note: String = "",
    )

    data class ChannelSection(
        val titleRes: Int,
        val noteRes: Int,
        val channels: List<WalkieChannel>,
    )

    private fun c(ch: String, freq: Double, tone: String = "None", rD: String = "OFF", rC: String = "OFF", tD: String = "OFF", tC: String = "OFF", note: String = "") =
        WalkieChannel(ch, freq, tone, rD, rC, tD, tC, note)

    /** 标准 16 信道(用户提供表,逐字保留) */
    val STANDARD_16: List<WalkieChannel> = listOf(
        c("1", 462.1250, "CTCSS", rC = "69.3", tC = "69.3", note = "FRS/GMRS 段 · 组 1(69.3Hz 过滤)"),
        c("2", 462.2250, note = "FRS/GMRS 段 · 无过滤,任何同频设备互通"),
        c("3", 462.3250, note = "FRS/GMRS 段 · 无过滤,通用直呼"),
        c("4", 462.4250, "CTCSS", rC = "103.5", tC = "103.5", note = "FRS/GMRS 段 · 组 2(103.5Hz 过滤)"),
        c("5", 462.5250, "CTCSS", rC = "114.8", tC = "114.8", note = "FRS/GMRS 段 · 组 3(114.8Hz 过滤)"),
        c("6", 462.6250, "CTCSS", rC = "127.3", tC = "127.3", note = "FRS/GMRS 段 · 组 4(127.3Hz 过滤)"),
        c("7", 462.7250, "CTCSS", rC = "136.5", tC = "136.5", note = "FRS/GMRS 段 · 组 5(136.5Hz 过滤)"),
        c("8", 462.8250, "CTCSS", rC = "162.2", tC = "162.2", note = "FRS/GMRS 段 · 组 6(162.2Hz 过滤)"),
        c("9", 462.9250, "DCS", rD = "D023N/D025N", tD = "D023N/D025N", note = "GMRS 段 · DCS 数字组(常见 D023N,旧机 D025N)"),
        c("10", 463.0250, "DCS", rD = "D051N", tD = "D051N", note = "GMRS 段 · DCS 数字组"),
        c("11", 463.1250, "DCS", rD = "D125N", tD = "D125N", note = "GMRS 段 · DCS 数字组"),
        c("12", 463.2250, "DCS", rD = "D155I", tD = "D155I", note = "GMRS 段 · DCS 数字组(反相码)"),
        c("13", 463.5250, "DCS", rD = "D465I", tD = "D465I", note = "GMRS 段 · DCS 数字组(反相码)"),
        c("14", 450.2250, "DCS", rD = "D023N", tD = "D023N", note = "商业 UHF 低段 · 部分国家需执照,核实本地法规"),
        c("15", 460.3250, note = "商业 UHF 段 · 部分国家需执照,核实本地法规"),
        c("16", 469.9500, "CTCSS", rC = "203.5", tC = "203.5", note = "商业 UHF 段 · 组 7(203.5Hz 过滤)"),
    )

    /** 中国公众对讲机 409 频段:409.7500–409.9875,12.5kHz 步进,0.5W,免执照 */
    val CN_409: List<WalkieChannel> = (0 until 20).map { n ->
        c("${n + 1}", 409.7500 + n * 0.0125,
            note = if (n == 8) "中国免执照公众对讲 0.5W · 应急互助常用" else "中国免执照公众对讲 0.5W")
    }

    /** 马来西亚 UHF 477:477.0125–477.4875,12.5kHz 步进,MCMC Class Assignment */
    val MY_477: List<WalkieChannel> = (0 until 39).map { n ->
        c("${n + 1}", 477.0125 + n * 0.0125,
            note = if (n == 4 || n == 34) "马来西亚类执照 UHF 477 · 应急呼叫常用" else "马来西亚类执照 UHF 477 · 通用直呼")
    }

    /** PMR446(欧盟/英国/新加坡):446.00625–446.19375,12.5kHz,0.5W 免执照 */
    val PMR446: List<WalkieChannel> = (0 until 16).map { n ->
        c("${n + 1}", 446.00625 + n * 0.0125,
            note = if (n == 7) "PMR446 免执照 0.5W · 通用呼叫(欧/英/新)" else "PMR446 免执照 0.5W(欧/英/新)")
    }

    /** 美国 FRS:462/467 MHz,12.5kHz,2W 免执照 */
    val US_FRS: List<WalkieChannel> = listOf(
        c("1", 462.5625, note = "美国 FRS 免执照 2W · 通用直呼"),
        c("2", 462.5875, note = "美国 FRS 免执照 2W"), c("3", 462.6125, note = "美国 FRS 免执照 2W · 常用呼叫"),
        c("4", 462.6375, note = "美国 FRS 免执照 2W"), c("5", 462.6625, note = "美国 FRS 免执照 2W"),
        c("6", 462.6875, note = "美国 FRS 免执照 2W"), c("7", 462.7125, note = "美国 FRS 免执照 2W"),
        c("8", 467.5625, note = "美国 FRS 免执照 2W(467 段)"), c("9", 467.5875, note = "美国 FRS 免执照 2W(467 段)"),
        c("10", 467.6125, note = "美国 FRS 免执照 2W(467 段)"), c("11", 467.6375, note = "美国 FRS 免执照 2W(467 段)"),
        c("12", 467.6625, note = "美国 FRS 免执照 2W(467 段)"), c("13", 467.6875, note = "美国 FRS 免执照 2W(467 段)"),
        c("14", 467.7125, note = "美国 FRS 免执照 2W(467 段)"),
        c("15", 462.5500, note = "美国 FRS 免执照 2W"), c("16", 462.5750, note = "美国 FRS 免执照 2W"),
        c("17", 462.6000, note = "美国 FRS 免执照 2W"), c("18", 462.6250, note = "美国 FRS 免执照 2W"),
        c("19", 462.6500, note = "美国 FRS 免执照 2W"), c("20", 462.6750, note = "美国 FRS 免执照 2W"),
        c("21", 462.7000, note = "美国 FRS 免执照 2W"), c("22", 462.7250, note = "美国 FRS 免执照 2W"),
    )

    /** 美国 GMRS 中继输入(需 GMRS 执照,50W) */
    val US_GMRS_REPEATER_IN: List<WalkieChannel> = listOf(
        c("R1", 467.5500, note = "GMRS 中继输入 · 需 GMRS 执照(与 462.5500 成对)"),
        c("R2", 467.5750, note = "GMRS 中继输入 · 需 GMRS 执照(与 462.5750 成对)"),
        c("R3", 467.6000, note = "GMRS 中继输入 · 需 GMRS 执照(与 462.6000 成对)"),
        c("R4", 467.6250, note = "GMRS 中继输入 · 需 GMRS 执照(与 462.6250 成对)"),
        c("R5", 467.6500, note = "GMRS 中继输入 · 需 GMRS 执照(与 462.6500 成对)"),
        c("R6", 467.6750, note = "GMRS 中继输入 · 需 GMRS 执照(与 462.6750 成对)"),
        c("R7", 467.7000, note = "GMRS 中继输入 · 需 GMRS 执照(与 462.7000 成对)"),
        c("R8", 467.7250, note = "GMRS 中继输入 · 需 GMRS 执照(与 462.7250 成对)"),
    )

    /** 美国 MURS:VHF 151/154 MHz,2W 免执照 */
    val US_MURS: List<WalkieChannel> = listOf(
        c("1", 151.820, note = "美国 MURS 免执照 VHF 2W"), c("2", 151.880, note = "美国 MURS 免执照 VHF 2W"),
        c("3", 151.940, note = "美国 MURS 免执照 VHF 2W · 常用呼叫"),
        c("4", 154.570, note = "美国 MURS 免执照 VHF 2W"), c("5", 154.600, note = "美国 MURS 免执照 VHF 2W"),
    )

    /** 海事 VHF 关键信道(156–162 MHz,25W 船台) */
    val MARINE_VHF: List<WalkieChannel> = listOf(
        c("16", 156.800, note = "遇险/呼叫 DISTRESS"),
        c("13", 156.650, note = "船对船安全"),
        c("6", 156.300, note = "船对船"),
        c("9", 156.450, note = "通用呼叫"),
        c("12", 156.600, note = "港口作业"),
        c("67", 156.375, note = "船对船辅助"),
        c("71", 156.575, note = "船对船辅助"),
        c("77", 156.875, note = "船对船辅助"),
        c("70", 156.525, note = "DSC 数字遇险(勿语音)"),
    )

    /** 航空应急 */
    val AVIATION_EMERGENCY: List<WalkieChannel> = listOf(
        c("GUARD", 121.500, note = "国际航空应急 VHF"),
        c("MIL", 243.000, note = "军用应急 UHF"),
    )

    /** 美国 CB 40 信道:26.965–27.405 MHz,10kHz 步进(AM/SSB,4W) */
    val US_CB_40: List<WalkieChannel> = (0 until 40).map { n ->
        c("${n + 1}", 26.965 + n * 0.010,
            note = when (n) { 8 -> "美国 CB 27MHz AM/SSB · 应急通用频道"; 18 -> "美国 CB 27MHz · 卡车司机常用"; else -> "美国 CB 27MHz AM/SSB · 4W" })
    }

    /** 英国 Simple UK Light:3 信道,0.5W 免执照 */
    val UK_SIMPLE_LIGHT: List<WalkieChannel> = listOf(
        c("1", 449.3125, note = "英国 Simple UK Light 免执照 0.5W"),
        c("2", 449.4000, note = "英国 Simple UK Light 免执照 0.5W"),
        c("3", 449.4750, note = "英国 Simple UK Light 免执照 0.5W"),
    )

    /** 欧盟 LPD433:433.075–434.775,25kHz 步进,69 信道,10mW 免执照 */
    val EU_LPD433: List<WalkieChannel> = (0 until 69).map { n ->
        c("${n + 1}", 433.075 + n * 0.025, note = "欧盟 LPD433 免执照 10mW · 短距通信")
    }

    /** 全部板块(顺序即展示顺序) */
    fun sections(): List<ChannelSection> = listOf(
        ChannelSection(xin.ctkqiang.deauthctrl.R.string.radio_sec_std16, xin.ctkqiang.deauthctrl.R.string.radio_note_std16, STANDARD_16),
        ChannelSection(xin.ctkqiang.deauthctrl.R.string.radio_sec_cn, xin.ctkqiang.deauthctrl.R.string.radio_note_cn, CN_409),
        ChannelSection(xin.ctkqiang.deauthctrl.R.string.radio_sec_my, xin.ctkqiang.deauthctrl.R.string.radio_note_my, MY_477),
        ChannelSection(xin.ctkqiang.deauthctrl.R.string.radio_sec_pmr, xin.ctkqiang.deauthctrl.R.string.radio_note_pmr, PMR446),
        ChannelSection(xin.ctkqiang.deauthctrl.R.string.radio_sec_frs, xin.ctkqiang.deauthctrl.R.string.radio_note_frs, US_FRS),
        ChannelSection(xin.ctkqiang.deauthctrl.R.string.radio_sec_gmrs, xin.ctkqiang.deauthctrl.R.string.radio_note_gmrs, US_GMRS_REPEATER_IN),
        ChannelSection(xin.ctkqiang.deauthctrl.R.string.radio_sec_murs, xin.ctkqiang.deauthctrl.R.string.radio_note_murs, US_MURS),
        ChannelSection(xin.ctkqiang.deauthctrl.R.string.radio_sec_cb, xin.ctkqiang.deauthctrl.R.string.radio_note_cb, US_CB_40),
        ChannelSection(xin.ctkqiang.deauthctrl.R.string.radio_sec_uklight, xin.ctkqiang.deauthctrl.R.string.radio_note_uklight, UK_SIMPLE_LIGHT),
        ChannelSection(xin.ctkqiang.deauthctrl.R.string.radio_sec_lpd, xin.ctkqiang.deauthctrl.R.string.radio_note_lpd, EU_LPD433),
        ChannelSection(xin.ctkqiang.deauthctrl.R.string.radio_sec_marine, xin.ctkqiang.deauthctrl.R.string.radio_note_marine, MARINE_VHF),
        ChannelSection(xin.ctkqiang.deauthctrl.R.string.radio_sec_aviation, xin.ctkqiang.deauthctrl.R.string.radio_note_aviation, AVIATION_EMERGENCY),
    )
}
