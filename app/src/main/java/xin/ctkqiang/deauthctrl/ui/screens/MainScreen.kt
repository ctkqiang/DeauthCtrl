package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import xin.ctkqiang.deauthctrl.ui.components.GlitchText
import xin.ctkqiang.deauthctrl.ui.components.ScanlineOverlay
import xin.ctkqiang.deauthctrl.viewmodel.BleSpamViewModel
import xin.ctkqiang.deauthctrl.viewmodel.BluetoothJammerViewModel
import xin.ctkqiang.deauthctrl.viewmodel.WifiDisruptViewModel
import xin.ctkqiang.deauthctrl.viewmodel.WifiJammerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    bleViewModel: BleSpamViewModel,
    wifiViewModel: WifiDisruptViewModel,
    btJammerViewModel: BluetoothJammerViewModel,
    wifiJammerViewModel: WifiJammerViewModel,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("BLE 攻击", "Wi-Fi 攻击", "BT 压制", "WiFi 压制", "关于")

    Scaffold(
        topBar = {
            Column {
                // fsociety 中文标题
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Column {
                        GlitchText(
                            text = "fsociety",
                            glitchIntervalMs = 5000,
                            style = MaterialTheme.typography.headlineLarge,
                        )
                        Text(
                            text = "> 去认证控制系统 v1.0",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outline) },
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                )
                            },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            ScanlineOverlay()
            when (selectedTab) {
                0 -> BleSpamScreen(viewModel = bleViewModel)
                1 -> WifiDisruptScreen(viewModel = wifiViewModel)
                2 -> BluetoothJammerScreen(viewModel = btJammerViewModel)
                3 -> WifiJammerScreen(viewModel = wifiJammerViewModel)
                4 -> AboutScreen()
            }
        }
    }
}
