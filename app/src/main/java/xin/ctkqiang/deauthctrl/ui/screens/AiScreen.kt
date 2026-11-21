package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.LocalLlmManager
import xin.ctkqiang.deauthctrl.ui.components.ActionButton
import xin.ctkqiang.deauthctrl.ui.components.BorderDim
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.GhostButton
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.PulseStar
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.SelectChip
import xin.ctkqiang.deauthctrl.ui.components.Star
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TermPanel
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.ui.components.hud
import xin.ctkqiang.deauthctrl.ui.components.lumiaTilt
import xin.ctkqiang.deauthctrl.viewmodel.AiViewModel

/**
 * 离线 AI 终端 —— 战术通讯台形态
 *
 * ## 对话页(电台频道式)
 * 全高暗舱:顶部频道条(模型名/状态/TOK)+ 气泡消息流(带时间戳)+
 * 底部常驻多行输入条 —— 聊天应用形态,没有面板套面板。
 *
 * ## 模型库页(机库式)
 * 全宽纵向模型卡列表:状态灯 + 名称 + 性质徽章 + 体积 + 选中指示,
 * 下方下载/删除/导入/直链/系统提示词管理区。
 */
@Composable
fun AiScreen(vm: AiViewModel, back: () -> Unit) {
    val state by vm.state.collectAsState()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val models = vm.availableModels()
    var tab by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) { vm.ensureSession() }
    // 新消息/流式增量时滚动到底部
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text?.length) {
        delay(50)
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    // 本地模型导入(SAF 文件选择器)—— .litertlm 任意来源
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
                }
            }.getOrNull()
            vm.importModel(uri, name)
        }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.ai_title), back)
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().imePadding().padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SelectChip(tab == 0, stringResource(R.string.ai_tab_chat)) { tab = 0 }
                SelectChip(tab == 1, stringResource(R.string.ai_tab_models)) { tab = 1 }
            }
            Spacer(Modifier.height(8.dp))
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { it / 4 } + fadeIn(tween(180)))
                        .togetherWith(slideOutHorizontally(tween(200)) { -it / 6 } + fadeOut(tween(140)))
                },
            ) { t ->
                if (t == 0) {
                    AiChatTab(vm, state, models, listState, haptic, onOpenModels = { tab = 1 })
                } else {
                    AiModelBayTab(vm, state, models, haptic, onImport = { importLauncher.launch(arrayOf("*/*")) })
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 对话页 —— 电台频道式
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun AiChatTab(
    vm: AiViewModel,
    state: AiViewModel.State,
    models: List<LocalLlmManager.ModelSpec>,
    listState: LazyListState,
    haptic: HapticFeedback,
    onOpenModels: () -> Unit,
) {
    val cur = models.firstOrNull { it.id == state.modelId }
    val consoleShape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)
    Column(
        Modifier.fillMaxSize().clip(consoleShape).background(SurfaceBg).border(1.dp, BorderDim, consoleShape),
    ) {
        // ── 频道条:状态脉冲 + 模型名 + 频道参数 + 状态 + 模型库入口 ──
        Row(
            Modifier.fillMaxWidth().background(SurfaceAlt).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PulseStar(active = state.streaming || state.initializing, size = 6.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    cur?.name?.take(26) ?: "--",
                    fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.ai_channel_line, state.maxTokens),
                    fontFamily = Mono, fontSize = 7.sp, color = Gray,
                )
            }
            Text(
                when {
                    state.initializing -> stringResource(R.string.ai_status_loading)
                    state.ready -> stringResource(R.string.ai_status_ready)
                    state.downloaded -> stringResource(R.string.ai_status_downloaded)
                    else -> stringResource(R.string.ai_status_none)
                },
                fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                color = when {
                    state.initializing -> Gold
                    state.ready -> TacGreen
                    else -> Gray
                },
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                stringResource(R.string.ai_tab_models) + " ▸",
                fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Gold,
                modifier = Modifier.clickable { haptic.hud(); onOpenModels() }.padding(vertical = 4.dp),
            )
        }

        // ── 消息流 ──
        if (state.messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Star(Gold.copy(alpha = 0.7f), 12.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.ai_empty_chat), fontFamily = Sans, fontSize = 10.sp, color = Gray)
                    Spacer(Modifier.height(3.dp))
                    Text(stringResource(R.string.ai_empty_hint), fontFamily = Mono, fontSize = 7.5.sp, color = Gray.copy(alpha = 0.6f))
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            ) {
                items(state.messages) { msg ->
                    ChatBubble(msg)
                }
            }
        }

        // ── 参数行(单行小字,不占视觉) ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.ai_param_line, state.temperature?.let { "%.1f".format(it) } ?: stringResource(R.string.ai_temp_default)),
                fontFamily = Mono, fontSize = 7.sp, color = Gray,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.ai_btn_clear) + " ▸",
                fontFamily = Mono, fontSize = 7.sp, color = Gray,
                modifier = Modifier.clickable { haptic.hud(); vm.clearChat() }.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(BorderDim))

        // ── 输入条:多行输入 + 发送/停止,常驻底部 ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            BasicTextField(
                value = state.input,
                onValueChange = vm::setInput,
                textStyle = TextStyle(fontFamily = Sans, fontSize = 12.sp, color = White, lineHeight = 17.sp),
                cursorBrush = SolidColor(Gold),
                maxLines = 4,
                modifier = Modifier
                    .weight(1f)
                    .clip(CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
                    .background(SurfaceAlt)
                    .border(1.dp, BorderDim, CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                decorationBox = { inner ->
                    if (state.input.isEmpty()) {
                        Text(stringResource(R.string.ai_ph_input), fontFamily = Sans, fontSize = 11.sp, color = Gray.copy(alpha = 0.5f))
                    }
                    inner()
                },
            )
            Spacer(Modifier.width(6.dp))
            Surface(
                modifier = Modifier
                    .height(42.dp)
                    .width(76.dp)
                    .clickable(enabled = !state.initializing && (state.streaming || (state.ready && state.input.isNotBlank()))) {
                        haptic.hud()
                        if (state.streaming) vm.stop() else vm.send()
                    },
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = if (state.streaming) Danger.copy(alpha = 0.16f) else Gold.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, if (state.streaming) Danger.copy(alpha = 0.7f) else Gold.copy(alpha = 0.6f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        if (state.streaming) stringResource(R.string.ai_btn_stop) else stringResource(R.string.ai_btn_send),
                        fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = if (state.streaming) Danger else Gold,
                    )
                }
            }
        }
    }
}

/** 聊天气泡 —— 你右对齐金色调 / AI 左对齐面板色,头部带角色与时间戳 */
@Composable
private fun ChatBubble(msg: AiViewModel.ChatMsg) {
    val user = msg.role == "user"
    val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(msg.timeMs))
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = if (user) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
            color = if (user) Gold.copy(alpha = 0.09f) else SurfaceAlt,
            border = BorderStroke(1.dp, if (user) Gold.copy(alpha = 0.4f) else BorderDim),
            modifier = Modifier.widthIn(max = 292.dp),
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (user) "▸ " + stringResource(R.string.ai_role_you) else "◆ " + stringResource(R.string.ai_role_ai) + if (msg.streaming) " " + stringResource(R.string.ai_recv) else "",
                        fontFamily = Mono, fontSize = 7.5.sp, fontWeight = FontWeight.Bold,
                        color = if (user) Gold else TacGreen,
                        modifier = Modifier.weight(1f),
                    )
                    Text(time, fontFamily = Mono, fontSize = 6.5.sp, color = Gray.copy(alpha = 0.6f))
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    if (msg.streaming) msg.text + " ▊" else msg.text,
                    fontFamily = Sans, fontSize = 12.sp, color = White, lineHeight = 17.sp,
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// 模型库页 —— 机库式
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun AiModelBayTab(
    vm: AiViewModel,
    state: AiViewModel.State,
    models: List<LocalLlmManager.ModelSpec>,
    haptic: HapticFeedback,
    onImport: () -> Unit,
) {
    // 模型状态(选中模型显示实时状态,其他模型显示是否已下载)
    @Composable
    fun statusOf(m: LocalLlmManager.ModelSpec): Pair<String, Color> = when {
        state.modelId == m.id && state.downloading -> stringResource(R.string.ai_status_downloading) to Warn
        state.modelId == m.id && state.initializing -> stringResource(R.string.ai_status_loading) to Gold
        state.modelId == m.id && state.ready -> stringResource(R.string.ai_status_ready) to TacGreen
        vm.isModelDownloaded(m.id) -> stringResource(R.string.ai_status_downloaded) to Gold
        else -> stringResource(R.string.ai_status_none) to Gray
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        // ── 机库头:已就绪 n/m ──
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Star(Gold, 7.dp)
            Spacer(Modifier.width(7.dp))
            Text(
                stringResource(R.string.ai_models_ready, models.count { vm.isModelDownloaded(it.id) }, models.size),
                fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Gray,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(6.dp))

        // ── 全宽纵向模型卡:状态灯 + 名称 + 徽章 + 体积 + 选中指示 ──
        models.forEach { m ->
            val (statusText, statusColor) = statusOf(m)
            AiModelRow(
                spec = m,
                selected = state.modelId == m.id,
                statusText = statusText,
                statusColor = statusColor,
                onClick = { haptic.hud(); vm.selectModel(m.id) },
            )
            Spacer(Modifier.height(5.dp))
        }
        Spacer(Modifier.height(6.dp))

        // ── 下载进度 ──
        if (state.downloading) {
            LinearProgressIndicator(
                progress = { (state.downloadBytes.toFloat() / state.downloadTotal.coerceAtLeast(1L)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(5.dp),
            )
            Text(
                if (state.downloadTotal > 0)
                    "%.0f / %.0f MB".format(state.downloadBytes / 1024.0 / 1024.0, state.downloadTotal / 1024.0 / 1024.0)
                else "%.0f MB".format(state.downloadBytes / 1024.0 / 1024.0),
                fontFamily = Mono, fontSize = 8.sp, color = Gold, modifier = Modifier.padding(top = 3.dp),
            )
            Spacer(Modifier.height(6.dp))
        }

        // ── 动作行:下载/删除 + 导入 ──
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (state.downloaded) {
                GhostButton(stringResource(R.string.ai_btn_delete_model), Modifier.weight(1f)) {
                    haptic.hud()
                    vm.deleteModel()
                }
            } else {
                ActionButton(
                    running = state.downloading,
                    runLabel = stringResource(R.string.ai_btn_download),
                    stopLabel = stringResource(R.string.ai_btn_cancel),
                    modifier = Modifier.weight(1f),
                ) {
                    haptic.hud()
                    if (state.downloading) vm.cancelDownload() else vm.startDownload()
                }
            }
            GhostButton(stringResource(R.string.ai_btn_import), Modifier.weight(1f)) {
                haptic.hud()
                onImport()
            }
        }
        Spacer(Modifier.height(6.dp))

        // ── URL 直链下载 ──
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = state.downloadUrl,
                onValueChange = vm::setDownloadUrl,
                textStyle = TextStyle(fontFamily = Mono, fontSize = 9.sp, color = White),
                cursorBrush = SolidColor(Gold),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .clip(CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp))
                    .background(SurfaceAlt)
                    .border(1.dp, BorderDim, CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp))
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                decorationBox = { inner ->
                    if (state.downloadUrl.isEmpty()) {
                        Text(stringResource(R.string.ai_ph_url), fontFamily = Mono, fontSize = 8.sp, color = Gray.copy(alpha = 0.5f))
                    }
                    inner()
                },
            )
            Spacer(Modifier.width(6.dp))
            Surface(
                modifier = Modifier.clickable(enabled = !state.downloading && state.downloadUrl.isNotBlank()) {
                    haptic.hud()
                    vm.startUrlDownload()
                },
                shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                color = Gold.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, Gold.copy(alpha = 0.55f)),
            ) {
                Text(
                    stringResource(R.string.ai_btn_url),
                    fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    color = Gold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
        if (!state.archSupported) {
            Text("[!] ${stringResource(R.string.ai_err_arch)}", fontFamily = Sans, fontSize = 8.sp, color = Danger, lineHeight = 12.sp, modifier = Modifier.padding(top = 5.dp))
        } else if (!state.ramSufficient) {
            Text(stringResource(R.string.ai_warn_ram), fontFamily = Sans, fontSize = 8.sp, color = Warn, lineHeight = 12.sp, modifier = Modifier.padding(top = 5.dp))
        }
        state.error?.let {
            Text("[!] $it", fontFamily = Sans, fontSize = 8.sp, color = Danger, lineHeight = 12.sp, modifier = Modifier.padding(top = 5.dp))
        }

        Spacer(Modifier.height(8.dp))

        // ── 系统提示词 ──
        TermPanel(stringResource(R.string.ai_panel_system)) {
            BasicTextField(
                value = state.systemPrompt,
                onValueChange = vm::setSystemPrompt,
                textStyle = TextStyle(fontFamily = Sans, fontSize = 10.sp, color = White),
                cursorBrush = SolidColor(Gold),
                maxLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp))
                    .background(SurfaceAlt)
                    .border(1.dp, BorderDim, CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                decorationBox = { inner ->
                    if (state.systemPrompt.isEmpty()) {
                        Text(stringResource(R.string.ai_ph_system), fontFamily = Sans, fontSize = 10.sp, color = Gray.copy(alpha = 0.5f))
                    }
                    inner()
                },
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.ai_limit_offline), fontFamily = Sans, fontSize = 8.sp, color = TacGreen, lineHeight = 12.sp)
        Spacer(Modifier.height(3.dp))
        Text(stringResource(R.string.ai_note_uncensored), fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 12.sp)
        Spacer(Modifier.height(10.dp))
    }
}

/**
 * 机库模型行 —— 全宽纵向卡:状态灯 + 名称 + 性质徽章 + 体积 + 状态。
 * 选中态金框 + ◆;点选即切换(已下载则自动加载)。
 */
@Composable
private fun AiModelRow(
    spec: LocalLlmManager.ModelSpec,
    selected: Boolean,
    statusText: String,
    statusColor: Color,
    onClick: () -> Unit,
) {
    val tagColor = when (spec.tag) {
        "uncensored" -> FRIENDLY_BLIP
        "reasoning" -> Warn
        "custom" -> Gold
        else -> Gray
    }
    val tagLabel = when (spec.tag) {
        "uncensored" -> stringResource(R.string.ai_tag_uncensored)
        "reasoning" -> stringResource(R.string.ai_tag_reasoning)
        "custom" -> stringResource(R.string.ai_tag_custom)
        else -> stringResource(R.string.ai_tag_standard)
    }
    Surface(
        modifier = Modifier.fillMaxWidth().lumiaTilt().clickable { onClick() },
        shape = CutCornerShape(topStart = 9.dp, bottomEnd = 9.dp),
        color = if (selected) TacGreen.copy(alpha = 0.07f) else SurfaceBg,
        border = BorderStroke(1.dp, if (selected) Gold.copy(alpha = 0.8f) else BorderDim),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Star(statusColor, 6.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        spec.name.take(22),
                        fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp),
                        color = tagColor.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, tagColor.copy(alpha = 0.6f)),
                    ) {
                        Text(tagLabel, fontFamily = Mono, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = tagColor, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(statusText, fontFamily = Mono, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = statusColor)
                    Spacer(Modifier.width(8.dp))
                    Text("%.1fG".format(spec.sizeBytes / 1024.0 / 1024.0 / 1024.0), fontFamily = Mono, fontSize = 7.5.sp, color = Gray)
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (selected) "◆" else "·",
                fontFamily = Mono, fontSize = 11.sp,
                color = if (selected) Gold else Gray.copy(alpha = 0.5f),
            )
        }
    }
}
