package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import xin.ctkqiang.deauthctrl.ui.components.GlitchText
import xin.ctkqiang.deauthctrl.ui.components.ScanlineOverlay
import xin.ctkqiang.deauthctrl.ui.components.BlinkingCursor
import kotlinx.coroutines.delay

/**
 * 关于页面 — 开发者信息、项目来源、技术栈
 *
 * 设计理念：中国红客风格 — 黑底红字，终端美学，
 * 搭配逐行显现的动画模拟黑客系统启动过程。
 */
@Composable
fun AboutScreen() {
    // 逐行动画计数器
    var visibleLines by remember { mutableIntStateOf(0) }
    val totalLines = 24

    LaunchedEffect(Unit) {
        while (visibleLines < totalLines) {
            delay(80)
            visibleLines++
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // CRT 扫描线覆盖层
        ScanlineOverlay()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {

        // 开发者信息
        item {
            AnimatedLine(visible = visibleLines >= 3) {
                SectionHeader("开发者信息")
            }
        }

        item {
            AnimatedLine(visible = visibleLines >= 4) {
                InfoRow("姓名", "钟智强")
            }
        }

        item {
            AnimatedLine(visible = visibleLines >= 5) {
                InfoRow("代号", "哪吒网络安全 / ctkqiang")
            }
        }

        item {
            AnimatedLine(visible = visibleLines >= 6) {
                InfoRow("邮箱", "ctkqiang@dingtalk.com")
            }
        }

        item { Spacer(Modifier.height(8.dp)) }

        // 项目信息
        item {
            AnimatedLine(visible = visibleLines >= 7) {
                SectionHeader("项目信息")
            }
        }

        item {
            AnimatedLine(visible = visibleLines >= 8) {
                InfoRow("仓库", "gitcode.com/ctkqiang_sr/DeauthCtrl")
            }
        }

        item {
            AnimatedLine(visible = visibleLines >= 9) {
                InfoRow("协议", "仅供授权安全研究使用")
            }
        }

        item {
            AnimatedLine(visible = visibleLines >= 10) {
                InfoRow("平台", "Android 6.0+ (API 23+)")
            }
        }

        item { Spacer(Modifier.height(8.dp)) }

        // 技术栈
        item {
            AnimatedLine(visible = visibleLines >= 11) {
                SectionHeader("技术栈")
            }
        }

        item {
            AnimatedLine(visible = visibleLines >= 12) {
                InfoRow("语言", "Kotlin 2.0")
            }
        }

        item {
            AnimatedLine(visible = visibleLines >= 13) {
                InfoRow("UI", "Jetpack Compose + Material 3")
            }
        }

        item {
            AnimatedLine(visible = visibleLines >= 14) {
                InfoRow("架构", "MVVM + StateFlow")
            }
        }

        item {
            AnimatedLine(visible = visibleLines >= 15) {
                InfoRow("异步", "Kotlin Coroutines")
            }
        }

        item { Spacer(Modifier.height(8.dp)) }

        // 功能
        item {
            AnimatedLine(visible = visibleLines >= 16) {
                SectionHeader("核心功能")
            }
        }

        item {
            AnimatedLine(visible = visibleLines >= 17) {
                InfoRow("BLE", "低功耗蓝牙泛洪攻击 (6种协议)")
            }
        }

        item {
            AnimatedLine(visible = visibleLines >= 18) {
                InfoRow("Wi-Fi", "邪恶双子热点 Beacon 泛洪")
            }
        }

        item { Spacer(Modifier.height(8.dp)) }

        // 警告
        item {
            AnimatedLine(visible = visibleLines >= 19) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    shape = RoundedCornerShape(2.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "⚠ 法律声明",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "本工具仅供授权安全研究与教育用途。\n" +
                                    "攻击非自有设备或网络属于违法行为。\n" +
                                    "开发者不对任何滥用行为承担责任。",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }

        // ASCII 艺术
        item {
            AnimatedLine(visible = visibleLines >= 20) {
                Text(
                    text = """
██████╗ ███████╗ █████╗ ██╗   ██╗████████╗██╗  ██╗
██╔══██╗██╔════╝██╔══██╗██║   ██║╚══██╔══╝██║  ██║
██║  ██║█████╗  ███████║██║   ██║   ██║   ███████║
██║  ██║██╔══╝  ██╔══██║██║   ██║   ██║   ██╔══██║
██████╔╝███████╗██║  ██║╚██████╔╝   ██║   ██║  ██║
╚═════╝ ╚══════╝╚═╝  ╚═╝ ╚═════╝    ╚═╝   ╚═╝  ╚═╝
                    """.trimIndent(),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
            }
        }

        item {
            AnimatedLine(visible = visibleLines >= 21) {
                GlitchText(
                    text = "中国红客 · 哪吒网络安全",
                    glitchIntervalMs = 4000,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            AnimatedLine(visible = visibleLines >= 22) {
                Text(
                    text = "国产自主 · 安全可控",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    } // end Box
}

/**
 * 带动画的逐行显现容器
 */
@Composable
private fun AnimatedLine(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
    ) {
        content()
    }
}

/**
 * 段落标题 — 红色高亮分割线风格
 */
@Composable
private fun SectionHeader(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp),
    ) {
        Text(
            text = "> ",
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = title,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = " <",
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * 键值对信息行 — 终端风格 Key: Value
 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
    ) {
        Text(
            text = "  $label: ",
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
