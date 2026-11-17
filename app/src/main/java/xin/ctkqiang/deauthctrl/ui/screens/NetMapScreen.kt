package xin.ctkqiang.deauthctrl.ui.screens

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import xin.ctkqiang.deauthctrl.manager.RadarTarget
import xin.ctkqiang.deauthctrl.ui.components.ActionButton
import xin.ctkqiang.deauthctrl.ui.components.BorderDim
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.Red
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.ui.components.hud
import xin.ctkqiang.deauthctrl.viewmodel.RadarViewModel
import xin.ctkqiang.deauthctrl.viewmodel.TacticalMapViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * 网络测绘地图 —— 雷达数据叠加 OSM
 *
 * 实时扫描 WiFi/蓝牙/BLE 设备,把每台设备绘制为 OSM 地图上的战术标记:
 * - 标记颜色与尺寸随 RSSI 缩放(强信号=大红、中=琥珀、弱=青)
 * - 点击标记弹出底部详情面板(全量技术字段 + 打开档案)
 * - 错误处理:定位关闭 / WiFi 关闭 / 蓝牙关闭 均给出横幅与快捷入口
 *
 * ## 位置估算的诚实说明
 * 手机硬件无 WiFi/BT 到达角(AoA)能力,设备坐标由
 * 本机 GPS 位置 + RSSI 距离估算 + 雷达方位估计合成,
 * 是**估算值**而非测量值 —— 用于态势感知,不能当作精确定位。
 */
@Composable
fun NetMapScreen(
    radarVm: RadarViewModel,
    mapVm: TacticalMapViewModel,
    back: () -> Unit,
    onOpenDossier: (String) -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val targets by radarVm.targets.collectAsState()
    val radarRunning by radarVm.isRunning.collectAsState()
    val location by mapVm.manager.location.collectAsState()
    val gpsOn by mapVm.manager.gpsOn.collectAsState()
    var selectedId by remember { mutableStateOf<String?>(null) }
    var recenterRequested by remember { mutableStateOf(0) }

    // 无线电状态轮询(2s)
    val radios by produceState(initialValue = RadiosState(), context) {
        while (true) {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val bt = context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            value = RadiosState(
                wifiOn = runCatching { wifi.isWifiEnabled }.getOrDefault(false),
                btOn = bt?.adapter?.isEnabled == true,
            )
            delay(2000)
        }
    }

    // 雷达是共享扫描器:页面级引用计数持有 —— 与雷达页/档案页过渡时扫描不断流
    DisposableEffect(Unit) {
        mapVm.manager.start()
        radarVm.acquireScan()
        onDispose {
            mapVm.manager.stop()
            radarVm.releaseScan()
        }
    }

    val selected = selectedId?.let { id -> targets.firstOrNull { it.id == id } }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader("网络测绘地图", back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(10.dp)) {
            // ── 状态条 ──
            Surface(
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = SurfaceBg,
                border = BorderStroke(1.dp, BorderDim),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("目标 ${targets.size} · 扫描${if (radarRunning) "中" else "停"}", fontFamily = Mono, fontSize = 10.sp, color = TacGreen)
                    Text(location?.let { "%.5f,%.5f".format(it.latitude, it.longitude) } ?: "定位中…", fontFamily = Mono, fontSize = 9.sp, color = if (location != null) TacGreen else Gray)
                    Text(if (gpsOn) "GPS 在线" else "GPS 离线", fontFamily = Mono, fontSize = 10.sp, color = if (gpsOn) TacGreen else Warn)
                }
            }

            // ── 错误横幅:定位 / WiFi / 蓝牙 ──
            val issues = buildList {
                if (!gpsOn) add("定位关闭 —— 无法估算设备位置" to Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                if (!radios.wifiOn) add("WiFi 关闭 —— 无法扫描无线网络" to Intent(Settings.ACTION_WIFI_SETTINGS))
                if (!radios.btOn) add("蓝牙关闭 —— 无法扫描蓝牙设备" to Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }
            issues.forEach { (msg, settingsIntent) ->
                Spacer(Modifier.height(5.dp))
                Surface(
                    shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
                    color = Warn.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Warn.copy(alpha = 0.5f)),
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(msg, fontFamily = Sans, fontSize = 10.sp, color = Warn, modifier = Modifier.weight(1f))
                        Text(
                            "打开设置 ▸",
                            fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Gold,
                            modifier = Modifier.clickable {
                                haptic.hud()
                                runCatching { context.startActivity(settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // ── 操作行:启动扫描 + 瓦片源 + 定位我 ──
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.clickable {
                        haptic.hud()
                        if (radarRunning) radarVm.stop() else radarVm.start()
                    },
                    shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                    color = if (radarRunning) TacGreen.copy(alpha = 0.10f) else Red,
                    border = BorderStroke(1.dp, if (radarRunning) TacGreen.copy(alpha = 0.6f) else Gold.copy(alpha = 0.7f)),
                ) {
                    Text(if (radarRunning) "■ 停止扫描" else "▶ 启动扫描", fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (radarRunning) TacGreen else White, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
                Text("OSM 公共瓦片 · 无需 API Key", fontFamily = Mono, fontSize = 8.sp, color = Gray, modifier = Modifier.weight(1f))
                Surface(
                    modifier = Modifier.clickable {
                        haptic.hud()
                        recenterRequested++
                    },
                    shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                    color = TacGreen.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, TacGreen.copy(alpha = 0.6f)),
                ) {
                    Text("◎ 定位我", fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TacGreen, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                }
            }

            Spacer(Modifier.height(6.dp))

            // ── 地图 + 设备标记 ──
            Box(
                Modifier.fillMaxWidth().weight(1f)
                    .clip(CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp))
                    .border(1.dp, BorderDim, CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)),
            ) {
                AndroidView(
                    factory = { ctx ->
                        val cfg = org.osmdroid.config.Configuration.getInstance()
                        cfg.load(ctx.applicationContext, ctx.getSharedPreferences("osmdroid", 0))
                        cfg.userAgentValue = "DeauthCtrl/1.0 (tactical)"
                        cfg.osmdroidBasePath = java.io.File(ctx.filesDir, "osmdroid")
                        cfg.osmdroidTileCache = java.io.File(ctx.filesDir, "osmdroid/tiles")
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            // 最大拉近起步:用户要能看到自己半径内的街道细节,而不是一个点
                            controller.setZoom(19.0)
                            mapVm.manager.location.value?.let {
                                controller.setCenter(GeoPoint(it.latitude, it.longitude))
                            }
                        }
                    },
                    update = { map ->
                        // 快速定位
                        if (recenterRequested > 0) {
                            mapVm.manager.location.value?.let { loc ->
                                map.controller.animateTo(GeoPoint(loc.latitude, loc.longitude))
                                map.controller.setZoom(19.0)
                                recenterRequested = 0
                            }
                        }
                        syncDeviceMarkers(map, targets, location, selectedId) { id -> selectedId = id }
                        map.invalidate()
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // ── 选中设备底部详情面板 ──
            selected?.let { t ->
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = CutCornerShape(topStart = 9.dp, bottomEnd = 9.dp),
                    color = SurfaceBg,
                    border = BorderStroke(1.dp, Gold.copy(alpha = 0.6f)),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("◆ ${t.name.take(18)}", fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = White, modifier = Modifier.weight(1f))
                            Text(if (t.type == "wifi") "WIFI" else "BLE", fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (t.type == "wifi") Danger else TacGreen)
                            Spacer(Modifier.width(8.dp))
                            Text("✕", fontFamily = Mono, fontSize = 12.sp, color = Gray, modifier = Modifier.clickable { haptic.hud(); selectedId = null })
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("RSSI ${t.rssi}dBm · SNR ${t.snrDb}dB", fontFamily = Mono, fontSize = 9.sp, color = if (t.rssi > -50) Danger else if (t.rssi > -70) Warn else TacGreen)
                            Text("≈${"%.1f".format(t.distanceM)}m · ${t.angleDeg.toInt()}°", fontFamily = Mono, fontSize = 9.sp, color = Gray)
                        }
                        Text(if (t.type == "wifi") "${t.id} · ${t.vendor} · ${t.channel} · ${t.security}" else "${t.address} · ${t.vendor} · ${t.deviceClassMajor}", fontFamily = Mono, fontSize = 8.sp, color = Gray)
                        Text("位置为 GPS+RSSI 估算值(无 AoA 硬件),仅作态势参考", fontFamily = Sans, fontSize = 8.sp, color = Gray)
                        Spacer(Modifier.height(5.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                haptic.hud()
                                onOpenDossier(t.id)
                            },
                            shape = CutCornerShape(topStart = 7.dp, bottomEnd = 7.dp),
                            color = Red.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, Red.copy(alpha = 0.5f)),
                        ) {
                            Text("打开完整档案 ▸", fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** 无线电开关状态快照 */
private data class RadiosState(val wifiOn: Boolean = true, val btOn: Boolean = true)

/**
 * 同步设备标记到地图:
 * 每次重组移除旧标记并重建(数量上限 50,按信号强度优先)。
 * 设备位置 = 本机 GPS + RSSI 距离 + 雷达方位估算。
 */
private fun syncDeviceMarkers(
    map: MapView,
    targets: List<RadarTarget>,
    selfLocation: android.location.Location?,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    map.overlays.removeAll { it is Marker && it.id != "self" && it.id != "selffallback" }
    val self = selfLocation ?: return
    // 本机标记
    if (map.overlays.none { it is Marker && it.id == "self" }) {
        map.overlays.add(
            Marker(map).apply {
                id = "self"
                position = GeoPoint(self.latitude, self.longitude)
                title = "本机"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = osmdroidDot(0xFF45E0FF.toInt(), 30)
            },
        )
    } else {
        map.overlays.filterIsInstance<Marker>().firstOrNull { it.id == "self" }?.position =
            GeoPoint(self.latitude, self.longitude)
    }

    targets.sortedByDescending { it.rssi }.take(50).forEach { t ->
        // 估算坐标:距离按 RSSI,方位用雷达稳定估算角
        val bearingRad = Math.toRadians(t.angleDeg.toDouble())
        val dLat = t.distanceM * cos(bearingRad) / 111_320.0
        val dLon = t.distanceM * sin(bearingRad) / (111_320.0 * cos(Math.toRadians(self.latitude)))
        val marker = Marker(map).apply {
            id = "dev_" + t.id
            position = GeoPoint(self.latitude + dLat, self.longitude + dLon)
            title = t.name
            snippet = "${t.rssi}dBm · ${"%.1f".format(t.distanceM)}m"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = DeviceMarkerDrawable(t.rssi, t.type == "wifi", selected = t.id == selectedId)
            setOnMarkerClickListener { _, _ ->
                onSelect(t.id)
                true
            }
        }
        map.overlays.add(marker)
    }
}

private fun osmdroidDot(color: Int, sizePx: Int): Drawable =
    android.graphics.drawable.ShapeDrawable().apply {
        shape = android.graphics.drawable.shapes.OvalShape()
        paint.color = color
        setIntrinsicWidth(sizePx)
        setIntrinsicHeight(sizePx)
    }

/**
 * 设备标记 —— 颜色与尺寸随 RSSI 缩放:
 * 强信号(-50 以上)= 大红 64px;中(-50..-70)= 琥珀 56px;弱 = 青 48px。
 * 选中态叠加金色四角括号。
 */
private class DeviceMarkerDrawable(
    private val rssi: Int,
    private val isWifi: Boolean,
    private val selected: Boolean,
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val coreColor: Int = when {
        rssi > -50 -> 0xFFFF4D5E.toInt()
        rssi > -70 -> 0xFFFFB547.toInt()
        else -> 0xFF45E0FF.toInt()
    }
    private val sizePx: Int = when {
        rssi > -50 -> 64
        rssi > -70 -> 56
        else -> 48
    }

    override fun draw(canvas: Canvas) {
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val r = sizePx / 2f

        // 光晕
        paint.color = coreColor
        paint.style = Paint.Style.FILL
        paint.alpha = 60
        canvas.drawCircle(cx, cy, r, paint)
        paint.alpha = 130
        canvas.drawCircle(cx, cy, r * 0.62f, paint)
        // 核心
        paint.alpha = 255
        canvas.drawCircle(cx, cy, r * 0.34f, paint)
        // 白芯
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(cx, cy, r * 0.13f, paint)

        // 类型字母
        paint.color = 0xFFFFFFFF.toInt()
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = sizePx * 0.30f
        paint.isFakeBoldText = true
        canvas.drawText(if (isWifi) "W" else "B", cx, cy + paint.textSize * 0.35f, paint)

        // 选中:金色四角括号
        if (selected) {
            paint.color = 0xFF9BD7FF.toInt()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            val c = r * 1.25f
            val l = c * 0.35f
            // 左上
            canvas.drawLine(cx - c, cy - c + l, cx - c, cy - c, paint)
            canvas.drawLine(cx - c, cy - c, cx - c + l, cy - c, paint)
            // 右上
            canvas.drawLine(cx + c - l, cy - c, cx + c, cy - c, paint)
            canvas.drawLine(cx + c, cy - c, cx + c, cy - c + l, paint)
            // 左下
            canvas.drawLine(cx - c, cy + c - l, cx - c, cy + c, paint)
            canvas.drawLine(cx - c, cy + c, cx - c + l, cy + c, paint)
            // 右下
            canvas.drawLine(cx + c - l, cy + c, cx + c, cy + c, paint)
            canvas.drawLine(cx + c, cy + c, cx + c, cy + c - l, paint)
        }
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int = sizePx
    override fun getIntrinsicHeight(): Int = sizePx
}
