package xin.ctkqiang.deauthctrl.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.theme.DeauthCtrlTheme
import xin.ctkqiang.deauthctrl.viewmodel.ArpScanViewModel
import xin.ctkqiang.deauthctrl.viewmodel.BleScannerViewModel
import xin.ctkqiang.deauthctrl.viewmodel.BleSpamViewModel
import xin.ctkqiang.deauthctrl.viewmodel.BluetoothJammerViewModel
import xin.ctkqiang.deauthctrl.viewmodel.CVESearchViewModel
import xin.ctkqiang.deauthctrl.viewmodel.DirBruteViewModel
import xin.ctkqiang.deauthctrl.viewmodel.FileTransferViewModel
import xin.ctkqiang.deauthctrl.viewmodel.HttpClientViewModel
import xin.ctkqiang.deauthctrl.viewmodel.PayloadViewModel
import xin.ctkqiang.deauthctrl.viewmodel.PingViewModel
import xin.ctkqiang.deauthctrl.viewmodel.PortScannerViewModel
import xin.ctkqiang.deauthctrl.viewmodel.RadarViewModel
import xin.ctkqiang.deauthctrl.viewmodel.RevShellViewModel
import xin.ctkqiang.deauthctrl.viewmodel.SecureMediaViewModel
import xin.ctkqiang.deauthctrl.viewmodel.SettingsViewModel
import xin.ctkqiang.deauthctrl.viewmodel.TerminalViewModel
import xin.ctkqiang.deauthctrl.viewmodel.WalkieTalkieViewModel
import xin.ctkqiang.deauthctrl.viewmodel.WebServerViewModel
import xin.ctkqiang.deauthctrl.viewmodel.WifiDisruptViewModel
import xin.ctkqiang.deauthctrl.viewmodel.WifiJammerViewModel
import xin.ctkqiang.deauthctrl.mirroring.ui.MirroringViewModel

/**
 * 预览环境构造 ViewModel
 *
 * 静态预览（Layoutlib）没有真实 Application，构造 AndroidViewModel
 * 会失败；真机预览模式下返回真实实例。失败返回 null，由调用方降级渲染。
 */
@Composable
internal fun <T : ViewModel> previewViewModel(factory: () -> T): T? =
    remember { runCatching { factory() }.getOrNull() }

/**
 * 预览降级提示
 *
 * 静态预览无法初始化系统服务时展示，引导用户切换到真机预览模式
 * （Android Studio 预览面板右上角的下拉菜单里选设备）。
 */
@Composable
internal fun PreviewFallback(name: String) {
    Column(
        Modifier.fillMaxSize().background(Color(0xFF08080A)).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("★", color = Gold, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text("$name 需要系统服务", fontFamily = Sans, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEDEDEF))
        Spacer(Modifier.height(4.dp))
        Text("请切换到真机预览（Preview on device）\n即可查看完整界面", fontFamily = Sans, fontSize = 11.sp, color = Gray, lineHeight = 17.sp)
    }
}

/** 预览统一主题包装 —— 强制暗色红军主题 */
@Composable
internal fun PreviewTheme(content: @Composable () -> Unit) {
    DeauthCtrlTheme(darkTheme = true) { content() }
}

/** 全量 ViewModel 集合 —— 主页与主路由预览共用 */
internal class PreviewVms(
    val bleVm: BleSpamViewModel,
    val wifiVm: WifiDisruptViewModel,
    val btVm: BluetoothJammerViewModel,
    val wjVm: WifiJammerViewModel,
    val wsVm: WebServerViewModel,
    val arpVm: ArpScanViewModel,
    val httpVm: HttpClientViewModel,
    val pingVm: PingViewModel,
    val blescanVm: BleScannerViewModel,
    val portscanVm: PortScannerViewModel,
    val walkieVm: WalkieTalkieViewModel,
    val radarVm: RadarViewModel,
    val ftVm: FileTransferViewModel,
    val vaultVm: SecureMediaViewModel,
    val revVm: RevShellViewModel,
    val payloadVm: PayloadViewModel,
    val bruteVm: DirBruteViewModel,
    val cveVm: CVESearchViewModel,
    val mirrorVm: MirroringViewModel,
    val terminalVm: TerminalViewModel,
    val settingsVm: SettingsViewModel,
)

/** 一次性构造全部 ViewModel；任一构造失败返回 null（静态预览降级） */
internal fun createPreviewVms(app: Application): PreviewVms? = runCatching {
    PreviewVms(
        bleVm = BleSpamViewModel(app),
        wifiVm = WifiDisruptViewModel(app),
        btVm = BluetoothJammerViewModel(app),
        wjVm = WifiJammerViewModel(app),
        wsVm = WebServerViewModel(app),
        arpVm = ArpScanViewModel(),
        httpVm = HttpClientViewModel(),
        pingVm = PingViewModel(),
        blescanVm = BleScannerViewModel(app),
        portscanVm = PortScannerViewModel(),
        walkieVm = WalkieTalkieViewModel(app),
        radarVm = RadarViewModel(app),
        ftVm = FileTransferViewModel(app),
        vaultVm = SecureMediaViewModel(app),
        revVm = RevShellViewModel(),
        payloadVm = PayloadViewModel(),
        bruteVm = DirBruteViewModel(),
        cveVm = CVESearchViewModel(),
        mirrorVm = MirroringViewModel(app),
        terminalVm = TerminalViewModel(app),
        settingsVm = SettingsViewModel(app),
    )
}.getOrNull()
