package xin.ctkqiang.deauthctrl.manager

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

/**
 * Whois 查询 —— TCP 43 直连注册局/注册商数据库
 *
 * 内置常见 TLD 的 whois 服务器表;未收录的 TLD 先走 IANA 拿到
 * 权威服务器再跳转查询。响应原样返回(谁家的库谁说了算),
 * [extractKeyLines] 只做关键字段的粗筛展示。
 */
class WhoisClient {

    data class Result(val domain: String, val server: String, val raw: String, val elapsedMs: Long)

    private val servers = mapOf(
        "com" to "whois.verisign-grs.com",
        "net" to "whois.verisign-grs.com",
        "org" to "whois.pir.org",
        "info" to "whois.afilias.net",
        "biz" to "whois.biz",
        "cn" to "whois.cnnic.cn",
        "com.cn" to "whois.cnnic.cn",
        "io" to "whois.nic.io",
        "co" to "whois.nic.co",
        "me" to "whois.nic.me",
        "de" to "whois.denic.de",
        "uk" to "whois.nic.uk",
        "ru" to "whois.tcinet.ru",
        "jp" to "whois.jprs.jp",
        "fr" to "whois.nic.fr",
        "tw" to "whois.twnic.net.tw",
        "hk" to "whois.hkirc.hk",
        "xyz" to "whois.nic.xyz",
        "top" to "whois.nic.top",
        "app" to "whois.nic.google",
        "dev" to "whois.nic.google",
    )

    /** 按最右标签选服务器;未知 TLD 回退 IANA */
    fun serverFor(domain: String): String {
        val labels = domain.trim().trimEnd('.').lowercase().split('.')
        for (i in labels.indices.reversed()) {
            val suffix = labels.subList(i, labels.size).joinToString(".")
            servers[suffix]?.let { return it }
        }
        return "whois.iana.org"
    }

    /** 同步查询;调用方需在 IO 线程执行 */
    fun querySync(domain: String, timeoutMs: Int = 8000): Result {
        val host = domain.trim().trimEnd('.')
        val server = serverFor(host)
        val started = System.currentTimeMillis()
        val raw = Socket().use { socket ->
            socket.connect(java.net.InetSocketAddress(server, 43), timeoutMs)
            socket.soTimeout = timeoutMs
            val out = PrintWriter(socket.getOutputStream(), false)
            out.print("$host\r\n")
            out.flush()
            val sb = StringBuilder()
            BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    sb.append(line).append('\n')
                    line = reader.readLine()
                }
            }
            sb.toString().trimEnd()
        }
        return Result(host, server, raw, System.currentTimeMillis() - started)
    }

    companion object {
        /** 粗筛关键字段 —— 只挑注册人/注册时间/到期/状态/服务器这几类行 */
        fun extractKeyLines(raw: String): List<String> {
            val keys = listOf(
                "registrar", "creation date", "registry expiry", "expiry date", "expiration date",
                "updated date", "name server", "domain status", "status", "organization", "registrant",
                "country", "whois server", "referral",
            )
            return raw.lines().filter { line ->
                val before = line.substringBefore(':').trim().lowercase()
                keys.any { before.contains(it) }
            }.take(24)
        }
    }
}
