package xin.ctkqiang.deauthctrl.ui.screens

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import xin.ctkqiang.deauthctrl.manager.TacticalMapManager
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.IntelFeedManager
import xin.ctkqiang.deauthctrl.ui.components.ActionButton
import xin.ctkqiang.deauthctrl.ui.components.BorderDim
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.EmptyHint
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.Red
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.SelectChip
import xin.ctkqiang.deauthctrl.ui.components.Star
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TacticalStatStrip
import xin.ctkqiang.deauthctrl.ui.components.TermPanel
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.ui.components.hud
import xin.ctkqiang.deauthctrl.viewmodel.IntelFeedViewModel

/** 类别 → 颜色(情报页固定色:CVE 红 / 公告琥珀 / 新闻绿 / 中文金 / 世界青) */
private fun intelCategoryColor(category: String): Color = when (category) {
    "CVE" -> Danger
    "公告" -> Warn
    "中文" -> Gold
    "世界" -> Color(0xFF00E5FF)
    else -> TacGreen
}

/**
 * 情报中心 —— GitHub Advisory / HackerNews / CISA KEV / 安全新闻 / 世界局势
 * 零 API Key:订阅源自选、搜索过滤、15 分钟本地推送、只收英中、地理打点。
 */
@Composable
fun IntelFeedScreen(vm: IntelFeedViewModel, back: () -> Unit) {
    val state by vm.state.collectAsState()
    var tab by remember { mutableStateOf(0) }
    // 内置阅读器:(url, title) —— 本风格弹窗,不跳浏览器、不换页面
    var reader by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(Unit) {
        vm.refresh()
        vm.rearmAlarmIfEnabled()
        // 常驻自动刷新:情报页打开期间每 60s 拉一次,内容自动更新 + 自动缓存
        while (true) {
            kotlinx.coroutines.delay(60_000L)
            vm.refresh()
        }
    }

    val notifyPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.setNotify(true)
    }

    reader?.let { (url, title) ->
        IntelWebReader(title = title, url = url, onClose = { reader = null })
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.intel_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SelectChip(tab == 0, stringResource(R.string.intel_tab_feed)) { tab = 0 }
                SelectChip(tab == 1, stringResource(R.string.intel_tab_sources)) { tab = 1 }
                SelectChip(tab == 2, stringResource(R.string.intel_tab_map)) { tab = 2 }
            }
            Spacer(Modifier.height(8.dp))
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { it / 4 } + fadeIn(tween(180)))
                        .togetherWith(slideOutHorizontally(tween(200)) { -it / 6 } + fadeOut(tween(140)))
                },
            ) { t ->
                when (t) {
                    0 -> FeedTab(vm, state, onOpenItem = { item -> reader = item.link to item.title })
                    1 -> SourcesTab(vm, state, onRequestNotifyPermission = { notifyPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) })
                    else -> MapTab(state, onOpenItem = { item -> reader = item.link to item.title })
                }
            }
        }
    }
}

// ─── 情报流页签 ──────────────────────────────────────────────────────────────

@Composable
private fun FeedTab(vm: IntelFeedViewModel, state: IntelFeedViewModel.State, onOpenItem: (IntelFeedManager.FeedItem) -> Unit) {
    val haptic = LocalHapticFeedback.current
    // 排序模式:0 最新 / 1 类别 / 2 来源
    var sortMode by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        BasicTextField(
            value = state.search,
            onValueChange = vm::setSearch,
            textStyle = TextStyle(fontFamily = Mono, fontSize = 10.sp, color = White),
            cursorBrush = SolidColor(Gold),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .clip(CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp))
                .background(SurfaceAlt)
                .border(1.dp, BorderDim, CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            decorationBox = { inner ->
                if (state.search.isEmpty()) {
                    Text(stringResource(R.string.intel_ph_search), fontFamily = Mono, fontSize = 9.sp, color = Gray.copy(alpha = 0.5f))
                }
                inner()
            },
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(
                "all" to stringResource(R.string.intel_cat_all),
                "archived" to stringResource(R.string.intel_cat_archived),
                "CVE" to "CVE",
                "公告" to stringResource(R.string.intel_cat_advisory),
                "新闻" to stringResource(R.string.intel_cat_news),
                "中文" to stringResource(R.string.intel_cat_cn),
                "世界" to stringResource(R.string.intel_cat_world),
            ).forEach { (key, label) ->
                SelectChip(state.category == key, label) { vm.setCategory(key) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(
                0 to stringResource(R.string.intel_sort_time),
                1 to stringResource(R.string.intel_sort_cat),
                2 to stringResource(R.string.intel_sort_src),
            ).forEach { (mode, label) ->
                SelectChip(sortMode == mode, label) { sortMode = mode }
            }
        }
        if (state.loading) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp))
        }
        state.error?.let {
            Spacer(Modifier.height(4.dp))
            Text("[!] $it", fontFamily = Sans, fontSize = 9.sp, color = Danger)
        }
        Spacer(Modifier.height(6.dp))

        val cveCount = state.items.count { IntelFeedManager.SOURCES.firstOrNull { s -> s.id == it.sourceId }?.category == "CVE" }
        val geoCount = state.items.count { it.lat != null }
        val cnCount = state.items.count { it.lang == "zh" }
        TacticalStatStrip(
            listOf(
                "${state.items.size}" to stringResource(R.string.intel_stat_items),
                "$cveCount" to stringResource(R.string.intel_stat_cve),
                "$geoCount" to stringResource(R.string.intel_stat_geo),
                "$cnCount" to stringResource(R.string.intel_stat_cn),
            ),
        )
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.intel_cache_hint), fontFamily = Mono, fontSize = 7.sp, color = Gray.copy(alpha = 0.6f))
        Spacer(Modifier.height(4.dp))

        val filtered0 = state.items.filter { itm ->
            val catOk = when (state.category) {
                "archived" -> itm.archived
                "all" -> true
                else -> IntelFeedManager.SOURCES.firstOrNull { s -> s.id == itm.sourceId }?.category == state.category
            }
            catOk && (state.search.isBlank() || itm.title.contains(state.search, ignoreCase = true) || itm.desc.contains(state.search, ignoreCase = true))
        }
        // 自动排序:最新(默认)/ 类别聚合 / 来源聚合
        val filtered = when (sortMode) {
            1 -> filtered0.sortedWith(compareBy({ IntelFeedManager.SOURCES.firstOrNull { s -> s.id == it.sourceId }?.category ?: "" }, { -it.timeMs }))
            2 -> filtered0.sortedWith(compareBy({ IntelFeedManager.SOURCES.firstOrNull { s -> s.id == it.sourceId }?.name ?: "" }, { -it.timeMs }))
            else -> filtered0.sortedByDescending { it.timeMs }
        }
        // 自动推荐:基于存档(★)条目关键词,给未存档条目打分,推 top5
        val recommended = remember(state.items, state.search, state.category) {
            if (state.search.isNotBlank() || state.category != "all") emptyList()
            else recommendItems(state.items)
        }
        if (filtered.isEmpty() && recommended.isEmpty()) {
            EmptyHint(stringResource(R.string.intel_empty))
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                if (recommended.isNotEmpty()) {
                    item(key = "rec_header") {
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Star(Gold, 6.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.intel_rec_header), fontFamily = Mono, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Gold)
                        }
                    }
                    items(recommended.take(5), key = { "rec_" + it.id }) { item ->
                        val src = IntelFeedManager.SOURCES.firstOrNull { it.id == item.sourceId }
                        val catColor = intelCategoryColor(src?.category ?: "新闻")
                        Surface(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable {
                                haptic.hud()
                                onOpenItem(item)
                            },
                            shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                            color = Gold.copy(alpha = 0.05f),
                            border = BorderStroke(1.dp, Gold.copy(alpha = 0.35f)),
                        ) {
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                                Text("★ " + item.title, fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White, lineHeight = 15.sp, maxLines = 2)
                                Spacer(Modifier.height(2.dp))
                                Text((src?.name ?: item.sourceId) + " · " + SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(item.timeMs)), fontFamily = Mono, fontSize = 7.5.sp, color = Gray)
                            }
                        }
                    }
                    item(key = "rec_gap") { Spacer(Modifier.height(6.dp)) }
                }
                items(filtered.take(200), key = { it.id }) { item ->
                    val src = IntelFeedManager.SOURCES.firstOrNull { it.id == item.sourceId }
                    val catColor = intelCategoryColor(src?.category ?: "新闻")
                    Surface(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable {
                            haptic.hud()
                            onOpenItem(item)
                        },
                        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                        color = SurfaceBg,
                        border = BorderStroke(1.dp, BorderDim),
                    ) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp),
                                    color = catColor.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, catColor.copy(alpha = 0.6f)),
                                ) {
                                    Text(src?.category ?: "?", fontFamily = Mono, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = catColor, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(src?.name ?: item.sourceId, fontFamily = Mono, fontSize = 8.sp, color = Gray, modifier = Modifier.weight(1f))
                                if (item.locName != null) {
                                    Surface(
                                        shape = CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp),
                                        color = Color(0xFF00E5FF).copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
                                    ) {
                                        Text("◎ ${item.locName}", fontFamily = Mono, fontSize = 7.sp, color = Color(0xFF00E5FF), modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                    }
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(if (item.lang == "zh") "中" else "EN", fontFamily = Mono, fontSize = 7.sp, color = if (item.lang == "zh") Gold else Gray)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (item.archived) "★" else "☆",
                                    fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    color = if (item.archived) Gold else Gray.copy(alpha = 0.55f),
                                    modifier = Modifier.clickable {
                                        haptic.hud()
                                        vm.toggleArchive(item.id)
                                    }.padding(horizontal = 3.dp),
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(item.title, fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White, lineHeight = 15.sp)
                            if (item.desc.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(item.desc.take(140), fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp, maxLines = 2)
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(item.timeMs)), fontFamily = Mono, fontSize = 7.5.sp, color = Gray.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }
}

// ─── 订阅源页签 ──────────────────────────────────────────────────────────────

@Composable
private fun SourcesTab(
    vm: IntelFeedViewModel,
    state: IntelFeedViewModel.State,
    onRequestNotifyPermission: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    Column(Modifier.fillMaxSize()) {
        TermPanel(stringResource(R.string.intel_panel_notify)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.intel_notify_title), fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = White)
                    Text(stringResource(R.string.intel_notify_desc), fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp)
                }
                Spacer(Modifier.width(10.dp))
                Switch(
                    checked = state.notifyOn,
                    onCheckedChange = { on ->
                        haptic.hud()
                        if (on && Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            onRequestNotifyPermission()
                        } else {
                            vm.setNotify(on)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Red.copy(alpha = 0.7f),
                        uncheckedThumbColor = Gray,
                        uncheckedTrackColor = SurfaceBg,
                        uncheckedBorderColor = Gray.copy(alpha = 0.4f),
                    ),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ActionButton(
                running = state.loading,
                runLabel = stringResource(R.string.intel_btn_refresh),
                stopLabel = stringResource(R.string.intel_btn_refreshing),
                modifier = Modifier.weight(1f),
            ) {
                haptic.hud()
                vm.refresh()
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (state.lastRefreshMs > 0) stringResource(R.string.intel_last_refresh, SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(state.lastRefreshMs))) else stringResource(R.string.intel_never_refreshed),
                fontFamily = Mono, fontSize = 8.sp, color = Gray,
            )
            if (state.lastNewCount > 0) {
                Text(stringResource(R.string.intel_new_count, state.lastNewCount), fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TacGreen)
            }
        }
        Spacer(Modifier.height(8.dp))
        TermPanel(stringResource(R.string.intel_panel_sources, state.subscribed.size, vm.sources.size), Modifier.weight(1f)) {
            Text(stringResource(R.string.intel_sources_hint), fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp)
            Spacer(Modifier.height(4.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(vm.sources, key = { it.id }) { src ->
                    val on = src.id in state.subscribed
                    val catColor = intelCategoryColor(src.category)
                    val pull = state.sourcePulls.firstOrNull { it.sourceId == src.id }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { haptic.hud(); vm.toggleSource(src.id) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Star(catColor, 6.dp)
                        Spacer(Modifier.width(7.dp))
                        Column(Modifier.weight(1f)) {
                            Text(src.name, fontFamily = Sans, fontSize = 11.sp, color = if (on) White else Gray)
                            Text("${src.category} · ${if (src.lang == "zh") "中文" else if (src.lang == "en") "EN" else "EN/中"}", fontFamily = Mono, fontSize = 7.5.sp, color = catColor)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(if (on) stringResource(R.string.intel_subscribed) else stringResource(R.string.intel_unsubscribed), fontFamily = Mono, fontSize = 7.5.sp, color = if (on) TacGreen else Gray.copy(alpha = 0.6f))
                            Text(
                                when {
                                    pull == null -> stringResource(R.string.intel_src_pending)
                                    pull.ok -> stringResource(R.string.intel_src_ok, pull.count)
                                    else -> stringResource(R.string.intel_src_fail)
                                },
                                fontFamily = Mono, fontSize = 7.sp,
                                color = when {
                                    pull == null -> Gray.copy(alpha = 0.6f)
                                    pull.ok -> TacGreen.copy(alpha = 0.85f)
                                    else -> Danger.copy(alpha = 0.85f)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── 战术世界地图页签(纯 Canvas 投影,无瓦片依赖,离线可用) ──────────────────

@Composable
private fun MapTab(state: IntelFeedViewModel.State, onOpenItem: (IntelFeedManager.FeedItem) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val geotagged = state.items.filter { it.lat != null && it.lon != null }.take(120)
    var selectedId by remember { mutableStateOf<String?>(null) }
    var fitRequested by remember { mutableStateOf(0) }
    var fittedSignature by remember { mutableStateOf("") }
    val mapMgr = remember { TacticalMapManager(context) }
    val selected = selectedId?.let { id -> geotagged.firstOrNull { it.id == id } }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxWidth().weight(1f)
                .clip(CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp))
                .border(1.dp, BorderDim, CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)),
        ) {
            AndroidView(
                factory = { ctx ->
                    val cfg = org.osmdroid.config.Configuration.getInstance()
                    cfg.load(ctx.applicationContext, ctx.getSharedPreferences("osmdroid", 0))
                    cfg.userAgentValue = "DeauthCtrl/1.0 (intel)"
                    cfg.osmdroidBasePath = java.io.File(ctx.filesDir, "osmdroid")
                    cfg.osmdroidTileCache = java.io.File(ctx.filesDir, "osmdroid/tiles")
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        // 三件套:禁重复铺满 + 最小缩放 2 + 负片滤镜
                        isHorizontalMapRepetitionEnabled = false
                        isVerticalMapRepetitionEnabled = false
                        minZoomLevel = 2.0
                        overlayManager.tilesOverlay.setColorFilter(mapMgr.monoNegativeFilter())
                        overlayManager.tilesOverlay.loadingBackgroundColor = 0xFF0A0F14.toInt()
                        overlayManager.tilesOverlay.loadingLineColor = 0xFF2A3440.toInt()
                    }
                },
                update = { map ->
                    // 重建地理标记(类别色圆点)
                    map.overlays.removeAll { it is Marker }
                    val pts = geotagged.map { item ->
                        GeoPoint(item.lat!!, item.lon!!).also { gp ->
                            val srcCat = IntelFeedManager.SOURCES.firstOrNull { it.id == item.sourceId }?.category ?: "新闻"
                            val color = intelCategoryColor(srcCat)
                            map.overlays.add(
                                Marker(map).apply {
                                    id = item.id
                                    position = gp
                                    title = item.title
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    icon = intelDot(color, selected = item.id == selectedId)
                                    setOnMarkerClickListener { _, _ ->
                                        selectedId = item.id
                                        haptic.hud()
                                        true
                                    }
                                },
                            )
                        }
                    }
                    // 自动包络:新批次首次(或手动 FIT)框进视野
                    val signature = "${geotagged.size}|${geotagged.firstOrNull()?.id.orEmpty()}"
                    if (pts.size >= 2 && (fitRequested > 0 || fittedSignature != signature)) {
                        val box = BoundingBox.fromGeoPoints(pts)
                        runCatching { map.zoomToBoundingBox(box, false, 24) }
                        fittedSignature = signature
                    }
                    if (fitRequested > 0) fitRequested = 0
                    map.invalidate()
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (geotagged.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Star(Gold.copy(alpha = 0.6f), 10.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.intel_map_empty), fontFamily = Sans, fontSize = 10.sp, color = Gray)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.intel_map_hint, geotagged.size), fontFamily = Sans, fontSize = 9.sp, color = Gray, modifier = Modifier.weight(1f))
            Surface(
                modifier = Modifier.clickable { haptic.hud(); fitRequested++ },
                shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                color = TacGreen.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, TacGreen.copy(alpha = 0.6f)),
            ) {
                Text(stringResource(R.string.intel_btn_fit), fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TacGreen, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                "CVE" to Danger,
                stringResource(R.string.intel_cat_advisory) to Warn,
                stringResource(R.string.intel_cat_news) to TacGreen,
                stringResource(R.string.intel_cat_cn) to Gold,
                stringResource(R.string.intel_cat_world) to Color(0xFF00E5FF),
            ).forEach { (label, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(6.dp).height(6.dp).clip(CutCornerShape(topStart = 2.dp, bottomEnd = 2.dp)).background(color))
                    Spacer(Modifier.width(3.dp))
                    Text(label, fontFamily = Mono, fontSize = 7.sp, color = Gray)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.intel_map_tap_hint), fontFamily = Mono, fontSize = 7.sp, color = Gray.copy(alpha = 0.6f))
        }
        selected?.let { item ->
            Spacer(Modifier.height(5.dp))
            Surface(
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = SurfaceBg,
                border = BorderStroke(1.dp, Gold.copy(alpha = 0.6f)),
            ) {
                Column(Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("◎ ${item.locName ?: ""}", fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                        Spacer(Modifier.width(8.dp))
                        Text(item.title.take(40), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White, maxLines = 1, modifier = Modifier.weight(1f))
                        Text("✕", fontFamily = Mono, fontSize = 12.sp, color = Gray, modifier = Modifier.clickable { selectedId = null }.padding(horizontal = 4.dp))
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(item.desc.take(120), fontFamily = Sans, fontSize = 8.5.sp, color = Gray, maxLines = 2)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.intel_open_link),
                        fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Gold,
                        modifier = Modifier.clickable {
                            haptic.hud()
                            onOpenItem(item)
                        },
                    )
                }
            }
        }
    }
}

/** 事件点标记:类别色圆点,选中态金环 */
private fun intelDot(color: Color, selected: Boolean): android.graphics.drawable.Drawable =
    android.graphics.drawable.ShapeDrawable().apply {
        shape = android.graphics.drawable.shapes.OvalShape()
        paint.color = android.graphics.Color.argb(if (selected) 255 else 210, (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
        setIntrinsicWidth(if (selected) 34 else 26)
        setIntrinsicHeight(if (selected) 34 else 26)
    }

/** 自动推荐:存档(★)条目的关键词频次打分,给未读条目排序 */
private fun recommendItems(items: List<IntelFeedManager.FeedItem>): List<IntelFeedManager.FeedItem> {
    val archived = items.filter { it.archived }
    if (archived.size < 2) return emptyList()
    val stop = setOf("the", "and", "for", "with", "from", "that", "this", "into", "over", "after", "under", "between", "about", "across", "through", "cyber", "attack", "hackers", "hacker", "security", "china", "russia", "ukraine", "us", "u.s.", "new", "after", "says", "report", "year", "2025", "2026", "的", "了", "在", "是", "与", "被", "及", "等", "中", "对", "将")
    val freq = HashMap<String, Int>()
    archived.forEach { itm ->
        (itm.title + " " + itm.desc).lowercase()
            .split(Regex("[^a-z0-9\u4e00-\u9fff]+"))
            .filter { w -> w.length >= 4 && w !in stop }
            .distinct()
            .forEach { w -> freq[w] = (freq[w] ?: 0) + 1 }
    }
    if (freq.isEmpty()) return emptyList()
    val keywords = freq.entries.sortedByDescending { it.value }.take(12).map { it.key }
    return items.filter { !it.archived }
        .map { itm ->
            val text = (itm.title + " " + itm.desc).lowercase()
            itm to keywords.count { text.contains(it) }
        }
        .filter { it.second >= 2 }
        .sortedByDescending { it.second * 100 - (System.currentTimeMillis() - it.first.timeMs) / 86_400_000L }
        .map { it.first }
}

// ─── 内置阅读器(本风格弹窗) ─────────────────────────────────────────────────

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun IntelWebReader(title: String, url: String, onClose: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Surface(
            shape = CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp),
            color = Dark,
            border = BorderStroke(1.dp, Gold.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.88f),
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().background(SurfaceBg).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "✕",
                        fontFamily = Mono, fontSize = 14.sp, color = Gray,
                        modifier = Modifier.clickable { haptic.hud(); onClose() }.padding(end = 10.dp),
                    )
                    Text(title.take(44), fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.intel_reader_inapp), fontFamily = Mono, fontSize = 7.sp, color = TacGreen)
                }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                if (Build.VERSION.SDK_INT >= 29) {
                                    runCatching { settings.forceDark = WebSettings.FORCE_DARK_ON }
                                }
                                webViewClient = WebViewClient()
                                loadUrl(url)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
