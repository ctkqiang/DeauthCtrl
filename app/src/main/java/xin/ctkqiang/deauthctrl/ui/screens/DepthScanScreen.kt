package xin.ctkqiang.deauthctrl.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.DepthCapability
import xin.ctkqiang.deauthctrl.manager.DepthMath
import xin.ctkqiang.deauthctrl.manager.DepthRenderer
import xin.ctkqiang.deauthctrl.ui.components.BorderDim
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.ui.components.hud
import xin.ctkqiang.deauthctrl.viewmodel.DepthScanViewModel

/**
 * 深度扫视(Depth / LiDAR-Style Scan)—— ARCore Depth API。
 *
 * - 相机由 ARCore 独占:预览是 GL 渲染器画的相机纹理,不走 CameraX。
 * - 深度图单位毫米;AUTOMATIC 模式在无 ToF 设备上走运动深度 ——
 *   不假设设备有专用 LiDAR,UI 明确标注 0.5–5 m 最佳精度区间。
 * - 无深度能力时回退:横幅说明 + 夜视光学扫视入口,不阻塞用户。
 */
@Composable
fun DepthScanScreen(vm: DepthScanViewModel, back: () -> Unit, onOpenNightVision: () -> Unit) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val activity = remember(context) { context as? Activity }
    val haptic = LocalHapticFeedback.current

    val glView = remember {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)
            preserveEGLContextOnPause = true
        }
    }
    val renderer = remember(vm) {
        DepthRenderer(
            managerProvider = { vm.manager },
            onGlReady = { vm.onGlReady(it) },
            onFrame = { vm.onDepthFrame(it) },
        )
    }
    LaunchedEffect(Unit) {
        activity?.let { vm.attach(it) }
        glView.setRenderer(renderer)
        vm.check()
    }

    // CAMERA 运行时权限
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.onPermissionResult(it)
    }
    fun ensurePermission() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    LaunchedEffect(Unit) { ensurePermission() }

    // 生命周期:ON_RESUME/ON_PAUSE 驱动 Session 与 GLSurfaceView
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> vm.onResume(glView)
                Lifecycle.Event.ON_PAUSE -> vm.onPause(glView)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.dispose()
        }
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.depth_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)) {
            CapabilityBanner(state.capability, onInstall = { vm.requestInstallAr() }, onGrant = { ensurePermission() })
            Spacer(Modifier.height(6.dp))

            // 扫描视图:GL 相机预览 + 热力图 + 准星 + 读数
            Box(
                Modifier.fillMaxWidth().weight(1f)
                    .clip(CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp))
                    .background(Color.Black)
                    .border(1.dp, BorderDim, CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)),
            ) {
                AndroidView(
                    factory = { glView },
                    modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                        detectTapGestures { tap ->
                            val nx = (tap.x / size.width).coerceIn(0f, 1f)
                            val ny = (tap.y / size.height).coerceIn(0f, 1f)
                            haptic.hud()
                            vm.requestTap(nx, ny)
                        }
                    },
                )

                // 距离热力图覆盖层(数据来自每帧深度图降采样,纯 Compose 绘制)
                if (state.heatmapOn) {
                    state.heatmap?.let { grid ->
                        Canvas(Modifier.fillMaxSize()) {
                            val gw = DepthMath.HEAT_GRID_W
                            val gh = DepthMath.HEAT_GRID_H
                            val cw = size.width / gw
                            val ch = size.height / gh
                            for (i in grid.indices) {
                                val c = grid[i]
                                if (c == 0) continue
                                val x = (i % gw) * cw
                                val y = (i / gw) * ch
                                drawRect(Color(c).copy(alpha = 0.45f), topLeft = Offset(x, y), size = Size(cw + 0.5f, ch + 0.5f))
                            }
                        }
                    }
                }

                // 准星(中心采样点)
                Box(Modifier.align(Alignment.Center).size(84.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val s = size.minDimension
                        drawCircle(Gold.copy(alpha = 0.85f), radius = s / 2f, center = center, style = Stroke(1.5.dp.toPx()))
                        drawLine(Gold.copy(alpha = 0.6f), Offset(center.x, 0f), Offset(center.x, s), 1.dp.toPx())
                        drawLine(Gold.copy(alpha = 0.6f), Offset(0f, center.y), Offset(s, center.y), 1.dp.toPx())
                    }
                }
                // 距离读数
                Column(
                    Modifier.align(Alignment.Center).padding(top = 96.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.distanceText, fontFamily = Mono, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Gold)
                    state.tapText?.let {
                        Spacer(Modifier.height(3.dp))
                        Text(stringResource(R.string.depth_tap_fmt, it), fontFamily = Mono, fontSize = 10.sp, color = Color(0xFF00E5FF))
                    }
                }

                // 回退面板:无深度能力 / 缺 AR 服务 / 缺权限
                if (state.capability == DepthCapability.NOT_SUPPORTED || state.capability == DepthCapability.AR_MISSING) {
                    FallbackPanel(
                        capability = state.capability,
                        onInstall = { vm.requestInstallAr() },
                        onGrant = { ensurePermission() },
                        onNightVision = onOpenNightVision,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))

            // 控制行
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                DepthHudButton(
                    label = if (state.heatmapOn) stringResource(R.string.depth_btn_heatmap_on) else stringResource(R.string.depth_btn_heatmap_off),
                    active = state.heatmapOn,
                    onClick = { haptic.hud(); vm.toggleHeatmap() },
                )
                DepthHudButton(
                    label = if (state.facingFront) stringResource(R.string.depth_btn_cam_front) else stringResource(R.string.depth_btn_cam_back),
                    onClick = { haptic.hud(); vm.toggleFacing(glView, renderer) },
                )
                Spacer(Modifier.weight(1f))
                DepthHudButton(
                    label = stringResource(R.string.depth_btn_optical),
                    onClick = { haptic.hud(); onOpenNightVision() },
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                stringResource(R.string.depth_note_accuracy),
                fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 11.sp,
            )
            Text(
                stringResource(R.string.depth_tap_hint),
                fontFamily = Mono, fontSize = 7.5.sp, color = Gray.copy(alpha = 0.7f),
            )
        }
    }
}

/** 能力横幅:状态色 + 对应动作(安装 AR 服务 / 授权) */
@Composable
private fun CapabilityBanner(cap: DepthCapability, onInstall: () -> Unit, onGrant: () -> Unit) {
    val (text, color) = when (cap) {
        DepthCapability.AVAILABLE -> stringResource(R.string.depth_cap_available) to TacGreen
        DepthCapability.INITIALIZING -> stringResource(R.string.depth_cap_init) to Warn
        DepthCapability.NOT_SUPPORTED -> stringResource(R.string.depth_cap_unsupported) to Danger
        DepthCapability.AR_MISSING -> stringResource(R.string.depth_cap_ar_missing) to Danger
        DepthCapability.PERMISSION_REQUIRED -> stringResource(R.string.depth_cap_permission) to Danger
        else -> stringResource(R.string.depth_cap_checking) to Gray
    }
    Surface(
        Modifier.fillMaxWidth(),
        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, fontFamily = Sans, fontSize = 8.5.sp, color = color, modifier = Modifier.weight(1f))
            if (cap == DepthCapability.AR_MISSING) {
                Text(
                    stringResource(R.string.depth_btn_install_ar),
                    fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Gold,
                    modifier = Modifier.clickable(onClick = onInstall).padding(horizontal = 4.dp),
                )
            }
            if (cap == DepthCapability.PERMISSION_REQUIRED) {
                Text(
                    stringResource(R.string.depth_btn_grant),
                    fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Gold,
                    modifier = Modifier.clickable(onClick = onGrant).padding(horizontal = 4.dp),
                )
            }
        }
    }
}

/** 无深度能力时的回退面板:说明 + 光学扫视入口(不阻塞用户) */
@Composable
private fun FallbackPanel(capability: DepthCapability, onInstall: () -> Unit, onGrant: () -> Unit, onNightVision: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
            color = SurfaceBg,
            border = BorderStroke(1.dp, Danger.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth(0.86f),
        ) {
            Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (capability == DepthCapability.AR_MISSING) stringResource(R.string.depth_cap_ar_missing)
                    else stringResource(R.string.depth_cap_unsupported),
                    fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Danger,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.depth_fallback_note),
                    fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 13.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (capability == DepthCapability.AR_MISSING) {
                        DepthHudButton(stringResource(R.string.depth_btn_install_ar), onClick = onInstall)
                    }
                    if (capability == DepthCapability.PERMISSION_REQUIRED) {
                        DepthHudButton(stringResource(R.string.depth_btn_grant), onClick = onGrant)
                    }
                    DepthHudButton(stringResource(R.string.depth_btn_optical), active = true, onClick = onNightVision)
                }
            }
        }
    }
}

/** 战术风格小按钮(ASCII 标签) */
@Composable
private fun DepthHudButton(label: String, active: Boolean = false, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
        color = if (active) TacGreen.copy(alpha = 0.18f) else SurfaceAlt,
        border = BorderStroke(1.dp, if (active) TacGreen.copy(alpha = 0.7f) else BorderDim),
    ) {
        Text(
            label,
            fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            color = if (active) TacGreen else White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
        )
    }
}
