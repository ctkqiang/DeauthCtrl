package xin.ctkqiang.deauthctrl.ui.screens

import android.annotation.SuppressLint
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
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
import androidx.compose.ui.viewinterop.AndroidView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.WantedManager
import xin.ctkqiang.deauthctrl.manager.WorldLandmass
import xin.ctkqiang.deauthctrl.ui.components.ActionButton
import xin.ctkqiang.deauthctrl.ui.components.BorderDim
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.EmptyHint
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.SelectChip
import xin.ctkqiang.deauthctrl.ui.components.Star
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TacticalStatStrip
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.ui.components.hud
import xin.ctkqiang.deauthctrl.viewmodel.WantedViewModel

/**
 * 通缉令 —— 独立功能,与情报中心零耦合:公开执法通缉数据
 * (FBI / Interpol Red Notices / ICE / NCA,官方发布)。
 * 名单页签 + 世界地图页签(国籍/出生地打点),内置阅读器看官方详情。
 */
@Composable
fun WantedScreen(vm: WantedViewModel, back: () -> Unit) {
    val state by vm.state.collectAsState()
    val haptic = LocalHapticFeedback.current
    var tab by remember { mutableStateOf(0) }
    var reader by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(300_000L)
            vm.refresh()
        }
    }

    reader?.let { (url, title) ->
        WantedReader(title = title, url = url, onClose = { reader = null })
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.wanted_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SelectChip(tab == 0, stringResource(R.string.wanted_tab_list)) { tab = 0 }
                SelectChip(tab == 1, stringResource(R.string.wanted_tab_map)) { tab = 1 }
            }
            Spacer(Modifier.height(8.dp))
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { it / 4 } + fadeIn(tween(180)))
                        .togetherWith(slideOutHorizontally(tween(200)) { -it / 6 } + fadeOut(tween(140)))
                },
            ) { t ->
                if (t == 0) WantedListTab(vm, state, onOpen = { item -> reader = item.link to item.title })
                else WantedMapTab(state, onOpen = { item -> reader = item.link to item.title })
            }
        }
    }
}

// ─── 名单页签 ────────────────────────────────────────────────────────────────

/** 源 id → 展示名(如 hkgov → HKPF) */
private fun sourceLabel(id: String): String =
    WantedManager.SOURCES.firstOrNull { it.id == id }?.name ?: id.uppercase()

@Composable
private fun WantedListTab(vm: WantedViewModel, state: WantedViewModel.State, onOpen: (WantedManager.WantedItem) -> Unit) {
    val haptic = LocalHapticFeedback.current
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
                    Text(stringResource(R.string.wanted_ph_search), fontFamily = Mono, fontSize = 9.sp, color = Gray.copy(alpha = 0.5f))
                }
                inner()
            },
        )
        Spacer(Modifier.height(6.dp))
        if (state.pulls.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                state.pulls.forEach { p ->
                    val label = if (p.ok) stringResource(R.string.wanted_src_ok, sourceLabel(p.sourceId), p.count)
                    else stringResource(R.string.wanted_src_fail, sourceLabel(p.sourceId), (p.error ?: "ERR").take(24))
                    Text(label, fontFamily = Mono, fontSize = 8.sp, color = if (p.ok) TacGreen else Danger)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        TacticalStatStrip(
            listOf(
                "${state.items.size}" to stringResource(R.string.wanted_stat_total),
                "${state.items.count { it.sourceId == "hkgov" }}" to "HKPF",
                "${state.items.count { it.sourceId == "os" }}" to "OS",
                "${state.items.count { it.lat != null }}" to stringResource(R.string.wanted_stat_geo),
            ),
        )
        if (state.loading) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp))
        }
        state.error?.let {
            Spacer(Modifier.height(4.dp))
            Text("[!] $it", fontFamily = Sans, fontSize = 9.sp, color = Danger)
        }
        Spacer(Modifier.height(6.dp))
        val filtered = state.items.filter {
            state.search.isBlank() || it.title.contains(state.search, ignoreCase = true) || it.desc.contains(state.search, ignoreCase = true)
        }
        if (filtered.isEmpty()) {
            EmptyHint(stringResource(R.string.wanted_empty))
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered, key = { it.id }) { item ->
                    Surface(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable {
                            haptic.hud()
                            onOpen(item)
                        },
                        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                        color = SurfaceBg,
                        border = BorderStroke(1.dp, BorderDim),
                    ) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp),
                                    color = Color(0xFFEC407A).copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, Color(0xFFEC407A).copy(alpha = 0.6f)),
                                ) {
                                    Text(sourceLabel(item.sourceId), fontFamily = Mono, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEC407A), modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(item.title.take(48), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                if (item.locName != null) {
                                    Spacer(Modifier.width(6.dp))
                                    Text("◎ ${item.locName}", fontFamily = Mono, fontSize = 7.sp, color = Color(0xFF00E5FF))
                                }
                            }
                            if (item.desc.isNotBlank()) {
                                Spacer(Modifier.height(3.dp))
                                Text(item.desc.take(160), fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp, maxLines = 3)
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

// ─── 世界地图页签 ────────────────────────────────────────────────────────────

@Composable
private fun WantedMapTab(state: WantedViewModel.State, onOpen: (WantedManager.WantedItem) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val geotagged = state.items.filter { it.lat != null && it.lon != null }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var fitTick by remember { mutableStateOf(0) }
    var view by remember { mutableStateOf<FloatArray?>(null) }
    var fittedSignature by remember { mutableStateOf("") }
    /** 用户手动缩放/平移后置真:数据刷新不再自动重取景,直到点 FIT */
    var userZoomed by remember { mutableStateOf(false) }
    val latestView = rememberUpdatedState(view)
    val selected = selectedId?.let { id -> geotagged.firstOrNull { it.id == id } }
    val measurer = rememberTextMeasurer()
    val selLabel = selected?.let { measurer.measure("◎ ${it.locName ?: ""} ${it.title.take(20)}", TextStyle(fontFamily = Mono, fontSize = 8.sp, color = Gold)) }

    /** 按钮缩放:窗口中心为焦点,跨度乘 factor(<1 放大 / >1 缩小),钳位世界范围 */
    fun zoomBy(factor: Float) {
        val v = view ?: floatArrayOf(-180f, 180f, -85f, 85f)
        val cLon = (v[0] + v[1]) / 2.0
        val cLat = (v[2] + v[3]) / 2.0
        val nLonSpan = ((v[1] - v[0]) * factor).coerceIn(0.5f, 360f)
        val nLatSpan = ((v[3] - v[2]) * factor).coerceIn(0.5f, 170f)
        val nMinLon = (cLon - nLonSpan / 2.0).coerceIn(-180.0, 180.0 - nLonSpan)
        val nMaxLat = (cLat + nLatSpan / 2.0).coerceIn(-85.0 + nLatSpan, 85.0)
        view = floatArrayOf(nMinLon.toFloat(), (nMinLon + nLonSpan).toFloat(), (nMaxLat - nLatSpan).toFloat(), nMaxLat.toFloat())
        userZoomed = true
    }

    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().weight(1f)
                .clip(CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp))
                .background(Color(0xFF02090F))
                .border(1.dp, BorderDim, CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp))
                .pointerInput(geotagged) {
                    // 自定义手势:单指点选 / 单指拖动平移 / 双指缩放。
                    // 键不包含 view —— 手势过程中窗口更新不会重启手势检测。
                    fun window(): FloatArray = latestView.value ?: floatArrayOf(-180f, 180f, -85f, 85f)
                    fun proj(v: FloatArray): Triple<Float, Float, Float> {
                        val geoW = v[1] - v[0]
                        val geoH = v[3] - v[2]
                        val scale = minOf(size.width / geoW, size.height / geoH)
                        return Triple(scale, (size.width - geoW * scale) / 2f, (size.height - geoH * scale) / 2f)
                    }
                    fun applyPan(pan: Offset) {
                        val v = window()
                        val (scale, _, _) = proj(v)
                        val lonSpan = (v[1] - v[0]).toDouble()
                        val latSpan = (v[3] - v[2]).toDouble()
                        val nMinLon = (v[0].toDouble() - pan.x / scale).coerceIn(-180.0, 180.0 - lonSpan)
                        val nMaxLat = (v[3].toDouble() + pan.y / scale).coerceIn(-85.0 + latSpan, 85.0)
                        view = floatArrayOf(nMinLon.toFloat(), (nMinLon + lonSpan).toFloat(), (nMaxLat - latSpan).toFloat(), nMaxLat.toFloat())
                        userZoomed = true
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var moved = false
                        var multiTouch = false
                        var pendingPan = Offset.Zero
                        val slop = 8.dp.toPx()
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                multiTouch = true
                                moved = true
                                val v = window()
                                val (scale, offX, offY) = proj(v)
                                val minLon = v[0].toDouble(); val maxLon = v[1].toDouble()
                                val minLat = v[2].toDouble(); val maxLat = v[3].toDouble()
                                val oldLonSpan = maxLon - minLon
                                val oldLatSpan = maxLat - minLat
                                val c = event.calculateCentroid()
                                // 焦点地理坐标:缩放围绕指尖中心,而不是窗口中心
                                val gx = minLon + (c.x - offX) / scale
                                val gy = maxLat - (c.y - offY) / scale
                                val fx = ((gx - minLon) / oldLonSpan).coerceIn(0.0, 1.0)
                                val fy = ((maxLat - gy) / oldLatSpan).coerceIn(0.0, 1.0)
                                val z = event.calculateZoom()
                                val p = event.calculatePan()
                                val nLonSpan = (oldLonSpan / z).coerceIn(0.5, 360.0)
                                val nLatSpan = (oldLatSpan / z).coerceIn(0.5, 170.0)
                                val nMinLon = (gx - fx * nLonSpan - p.x / scale).coerceIn(-180.0, 180.0 - nLonSpan)
                                val nMaxLat = (gy + fy * nLatSpan + p.y / scale).coerceIn(-85.0 + nLatSpan, 85.0)
                                view = floatArrayOf(nMinLon.toFloat(), (nMinLon + nLonSpan).toFloat(), (nMaxLat - nLatSpan).toFloat(), nMaxLat.toFloat())
                                userZoomed = true
                            } else if (pressed.size == 1) {
                                val change = pressed[0].positionChange()
                                if (moved) {
                                    applyPan(change)
                                } else {
                                    pendingPan += change
                                    if (pendingPan.getDistance() > slop) {
                                        moved = true
                                        applyPan(pendingPan)
                                        pendingPan = Offset.Zero
                                    }
                                }
                            }
                            if (pressed.isEmpty()) {
                                if (!moved && !multiTouch) {
                                    // 单指无移动 = 点选圆点
                                    val tap = down.position
                                    val v = window()
                                    val (scale, offX, offY) = proj(v)
                                    var best: WantedManager.WantedItem? = null
                                    var bestD = Float.MAX_VALUE
                                    for (it in geotagged) {
                                        val x = offX + (it.lon!! - v[0]).toFloat() * scale
                                        val y = offY + (v[3] - it.lat!!).toFloat() * scale
                                        val d = (tap - Offset(x, y)).getDistance()
                                        if (d <= 24.dp.toPx() && d < bestD) { bestD = d; best = it }
                                    }
                                    if (best != null) { haptic.hud(); selectedId = best!!.id } else selectedId = null
                                }
                                break
                            }
                        }
                    }
                },
        ) {
            // 等比例投影:统一缩放 + 居中 —— 竖屏画布不再把大陆纵向拉伸
            val aspect = (maxWidth / maxHeight).toFloat()
            val signature = "${geotagged.size}|${geotagged.firstOrNull()?.id.orEmpty()}|$aspect"
            if (!userZoomed && fittedSignature != signature) {
                view = wantedAutoView(geotagged, aspect)
                fittedSignature = signature
            }
            if (fitTick > 0) {
                view = wantedAutoView(geotagged, aspect)
                fittedSignature = signature
                fitTick = 0
                userZoomed = false
            }
            Canvas(Modifier.fillMaxSize()) {
                val v = view ?: floatArrayOf(-180f, 180f, -85f, 85f)
                val minLon = v[0].toDouble(); val maxLon = v[1].toDouble()
                val minLat = v[2].toDouble(); val maxLat = v[3].toDouble()
                val geoW = maxLon - minLon
                val geoH = maxLat - minLat
                val scale = minOf(size.width / geoW.toFloat(), size.height / geoH.toFloat())
                val offX = (size.width - geoW.toFloat() * scale) / 2f
                val offY = (size.height - geoH.toFloat() * scale) / 2f
                fun xOf(lon: Double): Float = offX + (lon - minLon).toFloat() * scale
                fun yOf(lat: Double): Float = offY + (maxLat - lat).toFloat() * scale

                var lon = floor(minLon / 30.0) * 30.0
                while (lon <= maxLon) {
                    val x = xOf(lon)
                    val major = (lon % 90.0) == 0.0
                    drawLine(TacGreen.copy(alpha = if (major) 0.18f else 0.07f), Offset(x, 0f), Offset(x, size.height), if (major) 1.2f else 0.6f)
                    lon += 10.0
                }
                var lat = floor(minLat / 30.0) * 30.0
                while (lat <= maxLat) {
                    val y = yOf(lat)
                    val major = (lat % 90.0) == 0.0
                    drawLine(TacGreen.copy(alpha = if (major) 0.18f else 0.07f), Offset(0f, y), Offset(size.width, y), if (major) 1.2f else 0.6f)
                    lat += 10.0
                }

                WorldLandmass.RINGS.forEach { ring ->
                    val path = Path()
                    var first = true
                    var prevLon = 0f
                    var jump = false
                    ring.forEach { (plon, plat) ->
                        val x = xOf(plon.toDouble())
                        val y = yOf(plat.toDouble())
                        if (first) { path.moveTo(x, y); first = false }
                        else if (kotlin.math.abs(plon - prevLon) > 90f) {
                            // 跨日期变更线的环(南极洲):断开子路径,否则一条线横穿整幅地图
                            jump = true
                            path.moveTo(x, y)
                        } else path.lineTo(x, y)
                        prevLon = plon
                    }
                    if (!jump) path.close()
                    if (!jump) drawPath(path, TacGreen.copy(alpha = 0.13f))
                    drawPath(path, Color.Black.copy(alpha = 0.25f), style = Stroke(2.5f))
                    drawPath(path, TacGreen.copy(alpha = 0.55f), style = Stroke(0.9f))
                }

                geotagged.forEach { item ->
                    val x = xOf(item.lon!!)
                    val y = yOf(item.lat!!)
                    val isSel = item.id == selectedId
                    drawCircle(Color(0xFFEC407A).copy(alpha = 0.18f), (if (isSel) 12f else 8f) * density, Offset(x, y))
                    drawCircle(Color(0xFFEC407A).copy(alpha = 0.95f), (if (isSel) 5f else 3.5f) * density, Offset(x, y))
                    if (isSel) {
                        drawCircle(Gold, 9f * density, Offset(x, y), style = Stroke(2f))
                        drawLine(Gold.copy(alpha = 0.8f), Offset(x - 12f * density, y), Offset(x + 12f * density, y), 1.4f)
                        drawLine(Gold.copy(alpha = 0.8f), Offset(x, y - 12f * density), Offset(x, y + 12f * density), 1.4f)
                    }
                }
                selected?.let { item ->
                    val x = xOf(item.lon!!)
                    val y = yOf(item.lat!!)
                    selLabel?.let { tag ->
                        drawText(tag, topLeft = Offset((x + 14f * density).coerceAtMost(size.width - tag.size.width - 4f), (y - tag.size.height / 2f).coerceIn(0f, size.height - tag.size.height)))
                    }
                }
            }
            if (geotagged.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Star(Gold.copy(alpha = 0.6f), 10.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.wanted_map_empty), fontFamily = Sans, fontSize = 10.sp, color = Gray)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.wanted_map_hint, geotagged.size), fontFamily = Sans, fontSize = 9.sp, color = Gray, modifier = Modifier.weight(1f))
            MapZoomBtn("-") { haptic.hud(); zoomBy(1.4f) }
            Spacer(Modifier.width(6.dp))
            MapZoomBtn("+") { haptic.hud(); zoomBy(0.7f) }
            Spacer(Modifier.width(6.dp))
            Surface(
                modifier = Modifier.clickable { haptic.hud(); fitTick++ },
                shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                color = TacGreen.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, TacGreen.copy(alpha = 0.6f)),
            ) {
                Text(stringResource(R.string.wanted_btn_fit), fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TacGreen, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
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
                        stringResource(R.string.wanted_open_link),
                        fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Gold,
                        modifier = Modifier.clickable { haptic.hud(); onOpen(item) },
                    )
                }
            }
        }
    }
}

/** 自动窗口:事件点包络 + 20% 边距,按画布宽高比扩展较短方向(窗口与画布同比例,满幅不变形) */
private fun wantedAutoView(points: List<WantedManager.WantedItem>, aspect: Float): FloatArray? {
    if (points.isEmpty()) return null
    val lons = points.map { it.lon!! }
    val lats = points.map { it.lat!! }
    var minLon = lons.minOrNull()!!.toDouble()
    var maxLon = lons.maxOrNull()!!.toDouble()
    var minLat = lats.minOrNull()!!.toDouble()
    var maxLat = lats.maxOrNull()!!.toDouble()
    val dLon = (maxLon - minLon).coerceAtLeast(10.0) * 0.20
    val dLat = (maxLat - minLat).coerceAtLeast(10.0) * 0.20
    minLon = (minLon - dLon).coerceIn(-180.0, 180.0)
    maxLon = (maxLon + dLon).coerceIn(-180.0, 180.0)
    minLat = (minLat - dLat).coerceIn(-85.0, 85.0)
    maxLat = (maxLat + dLat).coerceIn(-85.0, 85.0)
    if (maxLon - minLon < 6.0) { minLon = (minLon - 3.0).coerceIn(-180.0, 180.0); maxLon = (maxLon + 3.0).coerceIn(-180.0, 180.0) }
    if (maxLat - minLat < 6.0) { minLat = (minLat - 3.0).coerceIn(-85.0, 85.0); maxLat = (maxLat + 3.0).coerceIn(-85.0, 85.0) }
    var lonSpan = maxLon - minLon
    var latSpan = maxLat - minLat
    val centerLon = (minLon + maxLon) / 2.0
    val centerLat = (minLat + maxLat) / 2.0
    // 等比扩展较短方向;超出世界范围则保持几何窗口(画布侧留边补偿,不拉伸)
    if (lonSpan / latSpan < aspect) {
        val want = latSpan * aspect
        if (want <= 360.0) lonSpan = want
    } else {
        val want = lonSpan / aspect
        if (want <= 170.0) latSpan = want
    }
    minLon = (centerLon - lonSpan / 2.0).coerceIn(-180.0, 180.0 - lonSpan)
    maxLon = minLon + lonSpan
    minLat = (centerLat - latSpan / 2.0).coerceIn(-85.0, 85.0 - latSpan)
    maxLat = minLat + latSpan
    return floatArrayOf(minLon.toFloat(), maxLon.toFloat(), minLat.toFloat(), maxLat.toFloat())
}

/** 地图缩放按钮:ASCII 标签,与 FIT 同排 */
@Composable
private fun MapZoomBtn(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
        color = SurfaceAlt,
        border = BorderStroke(1.dp, BorderDim),
    ) {
        Text(label, fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

/** 内置阅读器(弹窗,同情报中心风格) */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WantedReader(title: String, url: String, onClose: () -> Unit) {
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
