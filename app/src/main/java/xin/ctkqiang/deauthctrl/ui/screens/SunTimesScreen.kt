package xin.ctkqiang.deauthctrl.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.AstroMath
import xin.ctkqiang.deauthctrl.ui.components.BorderDim
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
import java.util.Calendar
import java.util.TimeZone

/** 天文时刻 —— 日出日落 / 月相:夜间行动窗口规划 */
@Composable
fun SunTimesScreen(back: () -> Unit) {
    val context = LocalContext.current
    var latText by remember { mutableStateOf("39.90") }
    var lonText by remember { mutableStateOf("116.41") }
    var useAuto by remember { mutableStateOf(true) }
    var tick by remember { mutableStateOf(0) }

    // 每 30 秒刷新倒计时
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000L)
            tick++
        }
    }
    tick // 参与重组

    // 自动取上次已知位置
    LaunchedEffect(useAuto) {
        if (useAuto && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            runCatching {
                val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? LocationManager
                val loc = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (loc != null) {
                    latText = "%.5f".format(loc.latitude)
                    lonText = "%.5f".format(loc.longitude)
                }
            }
        }
    }

    val lat = latText.toDoubleOrNull()
    val lon = lonText.toDoubleOrNull()
    val now = Calendar.getInstance()
    val tzHours = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3_600_000.0
    val sun = if (lat != null && lon != null) {
        AstroMath.sunTimes(lat, lon, now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH), tzHours)
    } else null
    val phase = AstroMath.moonPhase(System.currentTimeMillis())

    // 下一事件倒计时
    val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60.0 + now.get(Calendar.MINUTE) + now.get(Calendar.SECOND) / 60.0
    val nextText = if (sun != null && sun.sunriseMinutes >= 0 && sun.sunsetMinutes >= 0) {
        val next = if (nowMin < sun.sunriseMinutes) sun.sunriseMinutes
        else if (nowMin < sun.sunsetMinutes) sun.sunsetMinutes else sun.sunriseMinutes + 24 * 60
        val label = if (nowMin < sun.sunriseMinutes || nowMin >= sun.sunsetMinutes) stringResource(R.string.sun_to_sunrise) else stringResource(R.string.sun_to_sunset)
        "$label " + AstroMath.formatClock(next) + " (" + AstroMath.formatClock(next - nowMin) + ")"
    } else "--"

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.sun_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)) {
            // 位置输入
            Row(Modifier.fillMaxWidth()) {
                SunField(stringResource(R.string.sun_lat), latText, { latText = it }, Modifier.weight(1f))
                Spacer(Modifier.height(0.dp).padding(end = 6.dp))
                SunField(stringResource(R.string.sun_lon), lonText, { lonText = it }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (useAuto) stringResource(R.string.sun_auto_on) else stringResource(R.string.sun_auto_off),
                fontFamily = Mono, fontSize = 8.sp, color = if (useAuto) TacGreen else Gray,
                modifier = Modifier.clickable { useAuto = !useAuto },
            )
            Spacer(Modifier.height(8.dp))

            // 结果卡
            Surface(
                Modifier.fillMaxWidth(),
                shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                color = SurfaceBg,
                border = BorderStroke(1.dp, BorderDim),
            ) {
                Column(Modifier.padding(12.dp)) {
                    SunRow(stringResource(R.string.sun_rise), AstroMath.formatClock(sun?.sunriseMinutes ?: -1.0), Gold)
                    SunRow(stringResource(R.string.sun_set), AstroMath.formatClock(sun?.sunsetMinutes ?: -1.0), Warn)
                    SunRow(stringResource(R.string.sun_day_len), sun?.let { "%d:%02d".format((it.dayLengthMinutes / 60).toInt(), (it.dayLengthMinutes % 60).toInt()) } ?: "--", TacGreen)
                    SunRow(stringResource(R.string.sun_next), nextText, TacGreen)
                    Spacer(Modifier.height(4.dp))
                    SunRow(stringResource(R.string.sun_moon), AstroMath.phaseName(phase), Gold)
                    SunRow(stringResource(R.string.sun_illum), "%.0f%%".format(AstroMath.moonIllumination(phase) * 100), Gray)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.sun_note), fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 11.sp)
        }
    }
}

@Composable
private fun SunRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, fontFamily = Mono, fontSize = 8.sp, color = Gray, modifier = Modifier.weight(1f))
        Text(value, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun SunField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontFamily = Mono, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Gray)
        Spacer(Modifier.height(3.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = TextStyle(fontFamily = Mono, fontSize = 10.sp, color = White),
            cursorBrush = SolidColor(Gold),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .clip(CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp))
                .background(SurfaceAlt)
                .border(1.dp, BorderDim, CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}
