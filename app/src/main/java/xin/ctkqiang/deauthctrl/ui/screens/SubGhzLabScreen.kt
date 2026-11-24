package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.manager.SubGhzFile
import xin.ctkqiang.deauthctrl.ui.components.BorderDim
import xin.ctkqiang.deauthctrl.ui.components.Danger
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.SelectChip
import xin.ctkqiang.deauthctrl.ui.components.SurfaceAlt
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TacGreen
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.Warn
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.ui.components.hud
import java.io.File

/** Flipper 预设(公开固件预设名) */
private val PRESETS = listOf(
    "FuriHalSubGhzPresetOok650Async",
    "FuriHalSubGhzPresetOok270Async",
    "FuriHalSubGhzPreset2FSKDev238Async",
)

/**
 * Sub-GHz 实验室 —— Flipper .sub 文件查看/生成(格式互操作 + 学习)。
 * 手机无 Sub-GHz 射频硬件:文件经 Flipper / CC1101 硬件发射。
 */
@Composable
fun SubGhzLabScreen(back: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var tab by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.subghz_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SelectChip(tab == 0, stringResource(R.string.subghz_tab_view)) { haptic.hud(); tab = 0 }
                SelectChip(tab == 1, stringResource(R.string.subghz_tab_gen)) { haptic.hud(); tab = 1 }
            }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.subghz_note), fontFamily = Sans, fontSize = 8.sp, color = Gray.copy(alpha = 0.8f), lineHeight = 11.sp)
            Spacer(Modifier.height(6.dp))
            if (tab == 0) SubGhzViewer(onSaved = { message = context.getString(R.string.subghz_saved, it) })
            else SubGhzGenerator(onSaved = { message = context.getString(R.string.subghz_saved, it) })
            message?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, fontFamily = Mono, fontSize = 7.5.sp, color = Gray)
            }
        }
    }
}

/** 查看页:粘贴 .sub → 解析摘要 */
@Composable
private fun SubGhzViewer(onSaved: (String) -> Unit) {
    val haptic = LocalHapticFeedback.current
    var text by remember { mutableStateOf("") }
    val parsed = remember(text) { SubGhzFile.parse(text) }

    Column(Modifier.fillMaxSize()) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = TextStyle(fontFamily = Mono, fontSize = 9.sp, color = White),
            cursorBrush = SolidColor(Gold),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 220.dp)
                .clip(CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
                .background(SurfaceAlt)
                .border(1.dp, BorderDim, CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
                .padding(10.dp),
            decorationBox = { inner ->
                if (text.isEmpty()) {
                    Text(stringResource(R.string.subghz_ph_paste), fontFamily = Mono, fontSize = 9.sp, color = Gray.copy(alpha = 0.5f))
                }
                inner()
            },
        )
        Spacer(Modifier.height(8.dp))
        val p = parsed
        if (p == null) {
            Text(stringResource(R.string.subghz_parse_fail), fontFamily = Sans, fontSize = 9.sp, color = Danger)
        } else {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                InfoRow(stringResource(R.string.subghz_f_freq), "%.3f MHz".format(p.frequencyHz / 1e6), TacGreen)
                InfoRow(stringResource(R.string.subghz_f_band), SubGhzFile.bandLabel(p.frequencyHz), Warn)
                InfoRow(stringResource(R.string.subghz_f_proto), p.protocol, Gold)
                InfoRow(stringResource(R.string.subghz_f_preset), p.preset, Gray)
                if (p.rawData.isNotEmpty()) {
                    InfoRow(stringResource(R.string.subghz_f_samples), p.rawData.size.toString(), TacGreen)
                    Spacer(Modifier.height(4.dp))
                    Text(p.rawData.take(64).joinToString(" "), fontFamily = Mono, fontSize = 8.sp, color = Gray, lineHeight = 11.sp)
                }
                if (p.keyHex.isNotEmpty()) {
                    InfoRow(stringResource(R.string.subghz_f_bit), p.bit.toString(), Gold)
                    InfoRow(stringResource(R.string.subghz_f_key), p.keyHex, White)
                    InfoRow(stringResource(R.string.subghz_f_te), p.te.toString(), Gray)
                }
            }
        }
    }
}

/** 生成页:RAW 或 Princeton → 预览 + 保存 */
@Composable
private fun SubGhzGenerator(onSaved: (String) -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var mode by remember { mutableStateOf(0) }
    var freqText by remember { mutableStateOf("433.92") }
    var preset by remember { mutableStateOf(0) }
    var pattern by remember { mutableStateOf("1000 -1000 1000 -1000 1000 -1000") }
    var bitText by remember { mutableStateOf("24") }
    var keyText by remember { mutableStateOf("001E53B0") }
    var teText by remember { mutableStateOf("400") }
    var preview by remember { mutableStateOf<String?>(null) }

    val freqHz = remember(freqText) { (freqText.toFloatOrNull() ?: 0f).times(1e6).toLong() }

    fun generate(): String? {
        if (freqHz <= 0) return null
        return if (mode == 0) {
            val data = pattern.split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }
            if (data.isEmpty()) null else SubGhzFile.buildRaw(freqHz, PRESETS[preset], data)
        } else {
            val bit = bitText.toIntOrNull() ?: return null
            val te = teText.toIntOrNull() ?: return null
            if (keyText.isBlank()) null else SubGhzFile.buildPrinceton(freqHz, PRESETS[preset], bit, keyText, te)
        }
    }

    fun save() {
        val content = generate() ?: return
        val dir = File(context.getExternalFilesDir(null), "flipper")
        dir.mkdirs()
        val f = File(dir, "subghz_${System.currentTimeMillis() % 1000000}.sub")
        f.writeText(content)
        onSaved(f.absolutePath)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SelectChip(mode == 0, "RAW") { haptic.hud(); mode = 0 }
            SelectChip(mode == 1, "PRINCETON") { haptic.hud(); mode = 1 }
        }
        Spacer(Modifier.height(8.dp))
        LabeledField(stringResource(R.string.subghz_f_freq_mhz), freqText, { freqText = it })
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.subghz_band_hint, SubGhzFile.bandLabel(freqHz)),
            fontFamily = Mono, fontSize = 8.sp, color = Warn,
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.subghz_f_preset), fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Gray)
        Spacer(Modifier.height(3.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PRESETS.forEachIndexed { i, p ->
                Surface(
                    modifier = Modifier.clickable { haptic.hud(); preset = i },
                    shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                    color = if (preset == i) TacGreen.copy(alpha = 0.16f) else SurfaceAlt,
                    border = BorderStroke(1.dp, if (preset == i) TacGreen.copy(alpha = 0.7f) else BorderDim),
                ) {
                    Text(p.removePrefix("FuriHalSubGhzPreset"), fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (preset == i) TacGreen else White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (mode == 0) {
            LabeledField(stringResource(R.string.subghz_f_pattern), pattern, { pattern = it })
        } else {
            LabeledField(stringResource(R.string.subghz_f_bit), bitText, { bitText = it })
            Spacer(Modifier.height(6.dp))
            LabeledField(stringResource(R.string.subghz_f_key), keyText, { keyText = it })
            Spacer(Modifier.height(6.dp))
            LabeledField(stringResource(R.string.subghz_f_te), teText, { teText = it })
        }
        Spacer(Modifier.height(10.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().clickable {
                haptic.hud()
                preview = generate()
            },
            shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
            color = TacGreen.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, TacGreen.copy(alpha = 0.6f)),
        ) {
            Text(stringResource(R.string.subghz_btn_gen), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TacGreen, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
        }
        preview?.let { p ->
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.subghz_preview), fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Gold)
            Spacer(Modifier.height(3.dp))
            Text(p.take(400), fontFamily = Mono, fontSize = 8.sp, color = Gray, lineHeight = 11.sp)
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { haptic.hud(); save() },
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = Gold.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, Gold.copy(alpha = 0.6f)),
            ) {
                Text(stringResource(R.string.subghz_btn_save), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, fontFamily = Mono, fontSize = 8.sp, color = Gray, modifier = Modifier.weight(0.45f))
        Text(value, fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.weight(0.55f))
    }
}

@Composable
private fun LabeledField(label: String, value: String, onChange: (String) -> Unit) {
    Column {
        Text(label, fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Gray)
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
