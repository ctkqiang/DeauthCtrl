package xin.ctkqiang.deauthctrl.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 蜜罐特征分类 / AppShark 评分 / Wi-Fi 审计评分 / 载荷校验 —— JVM 单测 */
class OpsModulesTest {

    // ─── AnyIntruder 蜜罐分类 ───────────────────────────────────────────────

    @Test
    fun honeypot_classifyAttackSignatures() {
        val hp = AttackHoneypotManager()
        assertEquals("SSH 探测", hp.classify("SSH-2.0-OpenSSH_8.9"))
        assertEquals("HTTP 管理路径扫描", hp.classify("GET /admin HTTP/1.1"))
        assertEquals("HTTP 敏感文件扫描", hp.classify("GET /.env HTTP/1.1"))
        assertEquals("HTTP 数据库扫描", hp.classify("GET /phpmyadmin/index.php"))
        assertEquals("HTTP 登录爆破尝试", hp.classify("POST /login HTTP/1.1"))
        assertEquals("WebShell 扫描", hp.classify("GET /shell.php HTTP/1.1"))
        assertEquals("恶意载荷扫描", hp.classify("MSSG evil"))
        assertNull(hp.classify(""))
        assertNull(hp.classify("   "))
    }

    @Test
    fun honeypot_unknownProbe() {
        val hp = AttackHoneypotManager()
        assertEquals("未知探测", hp.classify("hello there"))
    }

    // ─── AppShark 评分 ─────────────────────────────────────────────────────

    @Test
    fun appshark_scoresDangerousPermissions() {
        val m = AppSharkManager()
        val (score, findings) = m.scoreApp(
            permissions = listOf("android.permission.READ_SMS", "android.permission.RECORD_AUDIO"),
            exportedCount = 0, cleartext = false, debuggable = false, lowTargetSdk = false,
        )
        assertEquals(10, score) // 2 危险权限 × 5
        assertTrue(findings.any { it.contains("危险权限") })
    }

    @Test
    fun appshark_maxFlags() {
        val m = AppSharkManager()
        val (score, findings) = m.scoreApp(
            permissions = List(8) { "android.permission.READ_SMS" },
            exportedCount = 8, cleartext = true, debuggable = true, lowTargetSdk = true,
        )
        assertEquals(95, score) // 30+25+15+15+10 各分项封顶
        assertEquals(5, findings.size)
    }

    // ─── Aircrack 审计评分 ─────────────────────────────────────────────────

    @Test
    fun aircrack_securityDetection() {
        val m = AircrackManager()
        assertEquals("WPA2", m.scoreAp("[WPA2-PSK-CCMP][ESS]").first)
        assertEquals("WPA3", m.scoreAp("[WPA3-SAE][ESS]").first)
        assertEquals("OPEN", m.scoreAp("[ESS]").first)
        assertEquals("WEP", m.scoreAp("[WEP][ESS]").first)
        assertTrue(m.scoreAp("[WPA2-PSK-CCMP][WPS][ESS]").second)
    }

    @Test
    fun aircrack_riskScores() {
        val m = AircrackManager()
        assertTrue(m.riskOf("OPEN", false).first >= 90)
        assertTrue(m.riskOf("WEP", false).first >= 85)
        assertTrue(m.riskOf("WPA2", true).first in 30..60)
        assertTrue(m.riskOf("WPA3", false).first < 10)
    }

    // ─── BlueDucky 载荷校验 ────────────────────────────────────────────────

    @Test
    fun blueducky_validatesSyntax() {
        val ok = """
            REM test payload
            DELAY 1000
            GUI r
            STRING notepad
            ENTER
        """.trimIndent()
        assertTrue(BlueDuckyPayloads.validate(ok).isEmpty())
    }

    @Test
    fun blueducky_flagsUnknownCommands() {
        val bad = """
            REM bad payload
            FROBNICATE everything
            DELAY 100
        """.trimIndent()
        val problems = BlueDuckyPayloads.validate(bad)
        assertTrue(problems.any { it.contains("FROBNICATE") })
    }

    @Test
    fun blueducky_allPayloadsValid() {
        BlueDuckyPayloads.payloads().forEach { p ->
            assertTrue("payload ${p.id} has syntax problems", BlueDuckyPayloads.validate(p.script).isEmpty())
        }
    }
}
