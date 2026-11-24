package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xin.ctkqiang.deauthctrl.R
import xin.ctkqiang.deauthctrl.ui.components.Dark
import xin.ctkqiang.deauthctrl.ui.components.Gold
import xin.ctkqiang.deauthctrl.ui.components.Gray
import xin.ctkqiang.deauthctrl.ui.components.Mono
import xin.ctkqiang.deauthctrl.ui.components.Sans
import xin.ctkqiang.deauthctrl.ui.components.SurfaceBg
import xin.ctkqiang.deauthctrl.ui.components.TerminalHeader
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.ui.components.hud

/**
 * 毛选思维框架 —— 7 个核心心智模型 + 10 条决策启发式 + 13 段原著选段。
 * 每个模型与启发式都标注出处篇目;选段为原文 + 出处 + 译文三行呈现,
 * 译文采用外文出版社通行译法。框架内容为战略方法论提炼,选段不增删一字。
 */
@Composable
fun MaoxuanScreen(back: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    var tab by remember { mutableStateOf(0) }

    // (标题, 正文, 出处)
    val models = listOf(
        Triple(R.string.ops_mx_m1, R.string.ops_mx_m1b, R.string.ops_mx_m1s),
        Triple(R.string.ops_mx_m2, R.string.ops_mx_m2b, R.string.ops_mx_m2s),
        Triple(R.string.ops_mx_m3, R.string.ops_mx_m3b, R.string.ops_mx_m3s),
        Triple(R.string.ops_mx_m4, R.string.ops_mx_m4b, R.string.ops_mx_m4s),
        Triple(R.string.ops_mx_m5, R.string.ops_mx_m5b, R.string.ops_mx_m5s),
        Triple(R.string.ops_mx_m6, R.string.ops_mx_m6b, R.string.ops_mx_m6s),
        Triple(R.string.ops_mx_m7, R.string.ops_mx_m7b, R.string.ops_mx_m7s),
    )
    val heuristics = listOf(
        Triple(R.string.ops_mx_h1, R.string.ops_mx_h1b, R.string.ops_mx_h1s),
        Triple(R.string.ops_mx_h2, R.string.ops_mx_h2b, R.string.ops_mx_h2s),
        Triple(R.string.ops_mx_h3, R.string.ops_mx_h3b, R.string.ops_mx_h3s),
        Triple(R.string.ops_mx_h4, R.string.ops_mx_h4b, R.string.ops_mx_h4s),
        Triple(R.string.ops_mx_h5, R.string.ops_mx_h5b, R.string.ops_mx_h5s),
        Triple(R.string.ops_mx_h6, R.string.ops_mx_h6b, R.string.ops_mx_h6s),
        Triple(R.string.ops_mx_h7, R.string.ops_mx_h7b, R.string.ops_mx_h7s),
        Triple(R.string.ops_mx_h8, R.string.ops_mx_h8b, R.string.ops_mx_h8s),
        Triple(R.string.ops_mx_h9, R.string.ops_mx_h9b, R.string.ops_mx_h9s),
        Triple(R.string.ops_mx_h10, R.string.ops_mx_h10b, R.string.ops_mx_h10s),
    )
    // (原文, 译文, 出处)
    val quotes = listOf(
        Triple(R.string.ops_mx_q1, R.string.ops_mx_q1_en, R.string.ops_mx_q1_src),
        Triple(R.string.ops_mx_q2, R.string.ops_mx_q2_en, R.string.ops_mx_q2_src),
        Triple(R.string.ops_mx_q3, R.string.ops_mx_q3_en, R.string.ops_mx_q3_src),
        Triple(R.string.ops_mx_q4, R.string.ops_mx_q4_en, R.string.ops_mx_q4_src),
        Triple(R.string.ops_mx_q5, R.string.ops_mx_q5_en, R.string.ops_mx_q5_src),
        Triple(R.string.ops_mx_q6, R.string.ops_mx_q6_en, R.string.ops_mx_q6_src),
        Triple(R.string.ops_mx_q7, R.string.ops_mx_q7_en, R.string.ops_mx_q7_src),
        Triple(R.string.ops_mx_q8, R.string.ops_mx_q8_en, R.string.ops_mx_q8_src),
        Triple(R.string.ops_mx_q9, R.string.ops_mx_q9_en, R.string.ops_mx_q9_src),
        Triple(R.string.ops_mx_q10, R.string.ops_mx_q10_en, R.string.ops_mx_q10_src),
        Triple(R.string.ops_mx_q11, R.string.ops_mx_q11_en, R.string.ops_mx_q11_src),
        Triple(R.string.ops_mx_q12, R.string.ops_mx_q12_en, R.string.ops_mx_q12_src),
        Triple(R.string.ops_mx_q13, R.string.ops_mx_q13_en, R.string.ops_mx_q13_src),
    )

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.ops_mx_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MxTab(stringResource(R.string.ops_mx_models), tab == 0, Modifier.weight(1f)) { haptic.hud(); tab = 0 }
                MxTab(stringResource(R.string.ops_mx_heur), tab == 1, Modifier.weight(1f)) { haptic.hud(); tab = 1 }
                MxTab(stringResource(R.string.ops_mx_excerpts), tab == 2, Modifier.weight(1f)) { haptic.hud(); tab = 2 }
            }
            Spacer(Modifier.height(5.dp))
            Text(stringResource(R.string.ops_mx_intro), fontFamily = Sans, fontSize = 8.sp, color = Gray, lineHeight = 12.sp)
            Spacer(Modifier.height(4.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                when (tab) {
                    0 -> items(models) { (t, b, s) -> ModelCard(t, b, s) }
                    1 -> items(heuristics) { (t, b, s) -> ModelCard(t, b, s) }
                    else -> items(quotes) { (q, en, src) -> QuoteCard(q, en, src) }
                }
            }
        }
    }
}

@Composable
private fun MxTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        label,
        fontFamily = Mono,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        color = if (selected) Gold else Gray,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 5.dp),
    )
}

/** 模型/启发式卡片:标题 + 正文 + 出处 */
@Composable
private fun ModelCard(title: Int, body: Int, src: Int) {
    Surface(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
        color = SurfaceBg,
        border = BorderStroke(1.dp, Gray.copy(alpha = 0.3f)),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(stringResource(title), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Gold)
            Spacer(Modifier.height(3.dp))
            Text(stringResource(body), fontFamily = Sans, fontSize = 9.5.sp, color = White, lineHeight = 14.sp)
            Spacer(Modifier.height(3.dp))
            Text("▸ " + stringResource(src), fontFamily = Mono, fontSize = 7.5.sp, color = Gray.copy(alpha = 0.8f))
        }
    }
}

/** 原著选段卡片:原文(不增删一字)+ 出处 + 译文 */
@Composable
private fun QuoteCard(quote: Int, translation: Int, src: Int) {
    Surface(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
        color = SurfaceBg,
        border = BorderStroke(1.dp, Gray.copy(alpha = 0.3f)),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text("「" + stringResource(quote) + "」", fontFamily = Sans, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Gold, lineHeight = 15.sp)
            Spacer(Modifier.height(3.dp))
            Text("▸ " + stringResource(src), fontFamily = Mono, fontSize = 7.5.sp, color = Gray.copy(alpha = 0.8f))
            Spacer(Modifier.height(3.dp))
            Text(stringResource(translation), fontFamily = Sans, fontSize = 9.sp, color = White.copy(alpha = 0.85f), lineHeight = 13.sp)
        }
    }
}
