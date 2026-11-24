package xin.ctkqiang.deauthctrl.manager

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/**
 * AppShark 静态体检 —— PackageManager 级应用风险审计(免 root):
 * 危险权限 / 导出组件 / 明文流量 / 可调试标志 / 低 targetSdk,加权评分。
 */
class AppSharkManager(private val context: Context? = null) {

    data class AppRisk(
        val pkg: String,
        val label: String,
        val score: Int,          // 0-100,越高越危险
        val findings: List<String>,
    )

    data class State(
        val apps: List<AppRisk> = emptyList(),
        val scanned: Boolean = false,
    )

    private val pm = context?.packageManager
    private var cache: State = State()

    private val DANGEROUS = setOf(
        "android.permission.READ_SMS", "android.permission.SEND_SMS", "android.permission.RECEIVE_SMS",
        "android.permission.RECORD_AUDIO", "android.permission.CAMERA", "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.READ_CONTACTS", "android.permission.READ_CALL_LOG", "android.permission.CALL_PHONE",
        "android.permission.READ_PHONE_STATE", "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.REQUEST_INSTALL_PACKAGES",
    )

    /** 单应用评分(纯逻辑,可单测) */
    fun scoreApp(
        permissions: List<String>,
        exportedCount: Int,
        cleartext: Boolean,
        debuggable: Boolean,
        lowTargetSdk: Boolean,
    ): Pair<Int, List<String>> {
        var score = 0
        val findings = mutableListOf<String>()
        val dng = permissions.count { it in DANGEROUS }
        if (dng > 0) {
            score += (dng * 5).coerceAtMost(30)
            findings += "危险权限 ×$dng"
        }
        if (exportedCount > 0) {
            score += (exportedCount * 4).coerceAtMost(25)
            findings += "导出组件 ×$exportedCount"
        }
        if (cleartext) { score += 15; findings += "明文 HTTP 流量" }
        if (debuggable) { score += 15; findings += "可调试(可被附加调试)" }
        if (lowTargetSdk) { score += 10; findings += "targetSdk 过低(少防护)" }
        return score.coerceIn(0, 100) to findings
    }

    /** 扫描全部已装应用 */
    fun scan() {
        val pManager = pm ?: return
        val out = mutableListOf<AppRisk>()
        for (ai in pManager.getInstalledApplications(0)) {
            runCatching {
                val pkg = ai.packageName
                val pi = pManager.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
                val perms = pi.requestedPermissions?.toList() ?: emptyList()
                val exported = pi.activities?.count { it.exported } ?: 0
                val exportedCount = exported +
                    (pi.services?.count { it.exported } ?: 0) +
                    (pi.receivers?.count { it.exported } ?: 0) +
                    (pi.providers?.count { it.exported } ?: 0)
                val cleartext = (ai.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) != 0 ||
                    perms.contains("android.permission.INTERNET") && pi.applicationInfo.targetSdkVersion < 28
                val debuggable = (ai.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
                val lowSdk = pi.applicationInfo.targetSdkVersion in 1..25
                val (score, findings) = scoreApp(perms, exportedCount, cleartext, debuggable, lowSdk)
                if (score > 0) {
                    out += AppRisk(pkg, ai.loadLabel(pManager).toString(), score, findings)
                }
            }
        }
        cache = State(out.sortedByDescending { it.score }, scanned = true)
    }

    fun state(): State = cache
}
