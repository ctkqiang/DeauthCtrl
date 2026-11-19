package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.LocalLlmManager
import xin.ctkqiang.deauthctrl.ui.components.ActionButton
import xin.ctkqiang.deauthctrl.ui.components.BorderDim
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.EmptyHint
import xin.ctkqiang.deauthctrl.ui.components.GhostButton
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.PanelStat
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.SelectChip
import xin.ctkqiang.deauthctrl.ui.components.Star
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TermPanel
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.ui.components.hud
import xin.ctkqiang.deauthctrl.viewmodel.AiViewModel

/**
 * 离线 AI 终端 —— Gemma3-1B 本地大模型对话
 *
 * 首装需联网下载一次模型(557MB,int4 量化),之后推理 100% 离线:
 * 提示词与输出不出设备。运行时仅支持 arm64-v8a / x86_64。
 */
@Composable
fun AiScreen(back: () -> Unit) {
    val vm: AiViewModel = viewModel()
    val state by vm.state.collectAsState()
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { vm.ensureSession() }
    // 新消息时滚动到底部
    LaunchedEffect(state.messages.size) {
        delay(50)
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.ai_title), back)
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(12.dp),
        ) {
            // ── 模型管理 ──────────────────────────────────────────
            TermPanel(stringResource(R.string.ai_panel_model)) {
                val spec = LocalLlmManager.MODELS.first { it.id == state.modelId }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LocalLlmManager.MODELS.forEach { m ->
                        SelectChip(state.modelId == m.id, if (m.id == "qwen") stringResource(R.string.ai_model_qwen) else stringResource(R.string.ai_model_deepseek)) { vm.selectModel(m.id) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                PanelStat(stringResource(R.string.ai_label_model), spec.name, White)
                PanelStat(
                    stringResource(R.string.ai_label_storage),
                    "%.1f GB".format(spec.sizeBytes / 1024.0 / 1024.0 / 1024.0),
                    Gray,
                )
                PanelStat(
                    stringResource(R.string.ai_label_status),
                    when {
                        state.downloading -> stringResource(R.string.ai_status_downloading)
                        state.initializing -> stringResource(R.string.ai_status_loading)
                        state.ready -> stringResource(R.string.ai_status_ready)
                        state.downloaded -> stringResource(R.string.ai_status_downloaded)
                        else -> stringResource(R.string.ai_status_none)
                    },
                    when {
                        state.initializing -> Gold
                        state.ready -> TacGreen
                        else -> Gray
                    },
                )
                if (state.downloading) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (state.downloadBytes.toFloat() / state.downloadTotal.coerceAtLeast(1L)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                    )
                    Text(
                        "%.0f / %.0f MB".format(state.downloadBytes / 1024.0 / 1024.0, state.downloadTotal / 1024.0 / 1024.0),
                        fontFamily = Mono,
                        fontSize = 9.sp,
                        color = Gold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (state.downloaded) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton(stringResource(R.string.ai_btn_delete_model), Modifier.weight(1f)) {
                            haptic.hud()
                            vm.deleteModel()
                        }
                    }
                } else {
                    ActionButton(
                        running = state.downloading,
                        runLabel = stringResource(R.string.ai_btn_download),
                        stopLabel = stringResource(R.string.ai_btn_cancel),
                    ) {
                        haptic.hud()
                        if (state.downloading) vm.cancelDownload() else vm.startDownload()
                    }
                }
                if (!state.archSupported) {
                    Text("[!] ${stringResource(R.string.ai_err_arch)}", fontFamily = Sans, fontSize = 9.sp, color = Danger, lineHeight = 13.sp, modifier = Modifier.padding(top = 6.dp))
                } else if (!state.ramSufficient) {
                    Text(stringResource(R.string.ai_warn_ram), fontFamily = Sans, fontSize = 9.sp, color = Warn, lineHeight = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
                state.error?.let {
                    Text("[!] $it", fontFamily = Sans, fontSize = 9.sp, color = Danger, lineHeight = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }

            // ── 对话 ──────────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.ai_panel_chat)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ai_label_params), fontFamily = Sans, fontSize = 10.sp, color = Gray, modifier = Modifier.weight(1f))
                    SelectChip(state.temperature == null, stringResource(R.string.ai_temp_default)) { vm.setTemperature(null) }
                    Spacer(Modifier.width(5.dp))
                    SelectChip(state.temperature == 1.0, "T 1.0") { vm.setTemperature(1.0) }
                    Spacer(Modifier.width(5.dp))
                    SelectChip(state.maxTokens == 512, "512") { vm.setMaxTokens(512) }
                    Spacer(Modifier.width(5.dp))
                    SelectChip(state.maxTokens == 1024, "1024") { vm.setMaxTokens(1024) }
                }
                Spacer(Modifier.height(8.dp))
                if (state.messages.isEmpty()) {
                    EmptyHint(stringResource(R.string.ai_empty_chat))
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp, max = 280.dp),
                    ) {
                        items(state.messages) { msg ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Star(if (msg.role == "user") Gold else TacGreen, 6.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (msg.role == "user") stringResource(R.string.ai_role_you) else stringResource(R.string.ai_role_ai),
                                        fontFamily = Sans,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (msg.role == "user") Gold else TacGreen,
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    if (msg.streaming) msg.text + " ▊" else msg.text,
                                    fontFamily = Sans,
                                    fontSize = 11.sp,
                                    color = White,
                                    lineHeight = 16.sp,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = state.input,
                    onValueChange = vm::setInput,
                    textStyle = TextStyle(fontFamily = Sans, fontSize = 12.sp, color = White),
                    cursorBrush = SolidColor(Gold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
                        .background(SurfaceAlt)
                        .border(1.dp, BorderDim, CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    decorationBox = { inner ->
                        if (state.input.isEmpty()) {
                            Text(stringResource(R.string.ai_ph_input), fontFamily = Sans, fontSize = 11.sp, color = Gray.copy(alpha = 0.5f))
                        }
                        inner()
                    },
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.streaming) {
                        ActionButton(
                            running = true,
                            runLabel = stringResource(R.string.ai_btn_send),
                            stopLabel = stringResource(R.string.ai_btn_stop),
                            modifier = Modifier.weight(1f),
                        ) {
                            haptic.hud()
                            vm.stop()
                        }
                    } else {
                        ActionButton(
                            running = false,
                            runLabel = stringResource(R.string.ai_btn_send),
                            stopLabel = stringResource(R.string.ai_btn_stop),
                            modifier = Modifier.weight(1f),
                        ) {
                            haptic.hud()
                            vm.send()
                        }
                    }
                    GhostButton(stringResource(R.string.ai_btn_clear), Modifier.weight(1f)) {
                        haptic.hud()
                        vm.clearChat()
                    }
                }
            }

            // ── 边界与隐私 ────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            TermPanel(stringResource(R.string.ai_panel_limits)) {
                Text(stringResource(R.string.ai_limit_download), fontFamily = Sans, fontSize = 8.sp, color = White, lineHeight = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.ai_limit_offline), fontFamily = Sans, fontSize = 8.sp, color = TacGreen, lineHeight = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.ai_limit_device), fontFamily = Sans, fontSize = 8.sp, color = Warn, lineHeight = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.ai_limit_quality), fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.ai_limit_gated), fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
