package xin.ctkqiang.deauthctrl.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xin.ctkqiang.deauthctrl.R
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
import xin.ctkqiang.deauthctrl.ui.components.White
import xin.ctkqiang.deauthctrl.ui.components.hud

/**
 * 生存指南 —— 开源 wiki 内置页:野外应急 + 数字 OpSec 两大板块。
 * 全部文本走 strings_wiki.xml(英/中双语),配图为 drawable 矢量线稿 ——
 * Kotlin 中零硬编码内容,章节只引用资源 ID。
 */
@Composable
fun WikiScreen(back: () -> Unit, initialChapter: Int = -1) {
    val haptic = LocalHapticFeedback.current
    var tab by remember { mutableStateOf(0) }
    var open by remember { mutableStateOf<WikiChapter?>(null) }

    // 深链直达指定章节(combined 索引)
    LaunchedEffect(initialChapter) {
        if (initialChapter >= 0) {
            val all = FIELD_CHAPTERS + DIGITAL_CHAPTERS
            all.getOrNull(initialChapter)?.let { ch ->
                tab = if (initialChapter < FIELD_CHAPTERS.size) 0 else 1
                open = ch
            }
        }
    }

    // 章节详情:条件渲染独占整屏 —— 不能与列表并排(并排会被列表背景盖住,点开无反应)
    val chapter = open
    if (chapter != null) {
        WikiChapterView(chapter, onBack = { haptic.hud(); open = null })
        return
    }

    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(R.string.wiki_title), back)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SelectChip(tab == 0, stringResource(R.string.wiki_tab_field)) { haptic.hud(); tab = 0 }
                SelectChip(tab == 1, stringResource(R.string.wiki_tab_digital)) { haptic.hud(); tab = 1 }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (tab == 0) stringResource(R.string.wiki_section_field) else stringResource(R.string.wiki_section_digital),
                fontFamily = Mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (tab == 0) TacGreen else Gold,
            )
            Spacer(Modifier.height(6.dp))
            val chapters = if (tab == 0) FIELD_CHAPTERS else DIGITAL_CHAPTERS
            LazyColumn(Modifier.fillMaxSize()) {
                items(chapters) { ch ->
                    Surface(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { haptic.hud(); open = ch },
                        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                        color = SurfaceBg,
                        border = BorderStroke(1.dp, BorderDim),
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painterResource(ch.image),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(40.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(ch.title), fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(2.dp))
                                Text(stringResource(ch.summary), fontFamily = Sans, fontSize = 9.sp, color = Gray, lineHeight = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Text(">", fontFamily = Mono, fontSize = 12.sp, color = Gray)
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.wiki_note), fontFamily = Mono, fontSize = 7.5.sp, color = Gray.copy(alpha = 0.7f))
        }
    }
}

/** 章节详情:配图 + 正文段落 */
@Composable
private fun WikiChapterView(ch: WikiChapter, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Dark)) {
        TerminalHeader(stringResource(ch.title), onBack)
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)) {
            // 真实照片(公有领域/CC0,离线打包);无照片章节回退矢量插图
            if (ch.photo != null) {
                Image(
                    painterResource(ch.photo),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp))
                        .background(SurfaceAlt)
                        .border(1.dp, BorderDim, CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)),
                )
            } else {
                Image(
                    painterResource(ch.image),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 110.dp, max = 170.dp)
                        .clip(CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp))
                        .background(SurfaceAlt)
                        .border(1.dp, BorderDim, CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp))
                        .padding(12.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(ch.summary), fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Gold)
            Spacer(Modifier.height(6.dp))
            ch.body.forEach { pid ->
                Text(stringResource(pid), fontFamily = Sans, fontSize = 11.sp, color = White, lineHeight = 17.sp)
                Spacer(Modifier.height(8.dp))
            }

            // 操作步骤 —— 编号可执行流程
            Text(stringResource(R.string.wiki_sec_steps), fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TacGreen)
            Spacer(Modifier.height(4.dp))
            Surface(
                Modifier.fillMaxWidth(),
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = SurfaceBg,
                border = BorderStroke(1.dp, TacGreen.copy(alpha = 0.4f)),
            ) {
                Text(stringResource(ch.steps), fontFamily = Sans, fontSize = 10.5.sp, color = White, lineHeight = 16.sp, modifier = Modifier.padding(10.dp))
            }
            Spacer(Modifier.height(8.dp))

            // 关键警示 —— 保命红线
            Text(stringResource(R.string.wiki_sec_warn), fontFamily = Mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Danger)
            Spacer(Modifier.height(4.dp))
            Surface(
                Modifier.fillMaxWidth(),
                shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                color = Danger.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Danger.copy(alpha = 0.5f)),
            ) {
                Text(stringResource(ch.warn), fontFamily = Sans, fontSize = 10.5.sp, color = White, lineHeight = 16.sp, modifier = Modifier.padding(10.dp))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** 章节描述:全部字段为资源 ID,文本内容在 strings_wiki.xml;photo 为真实照片(缺图章节为 null) */
private data class WikiChapter(
    val title: Int,
    val summary: Int,
    val image: Int,
    val body: IntArray,
    val steps: Int,
    val warn: Int,
    val photo: Int? = null,
)

private val FIELD_CHAPTERS = listOf(
    WikiChapter(R.string.wiki_ch1_title, R.string.wiki_ch1_summary, R.drawable.wiki_img_comms, intArrayOf(R.string.wiki_ch1_p1, R.string.wiki_ch1_p2, R.string.wiki_ch1_p3), R.string.wiki_ch1_steps, R.string.wiki_ch1_warn, photo = R.drawable.wiki_photo_ch01),
    WikiChapter(R.string.wiki_ch2_title, R.string.wiki_ch2_summary, R.drawable.wiki_img_water, intArrayOf(R.string.wiki_ch2_p1, R.string.wiki_ch2_p2, R.string.wiki_ch2_p3), R.string.wiki_ch2_steps, R.string.wiki_ch2_warn),
    WikiChapter(R.string.wiki_ch3_title, R.string.wiki_ch3_summary, R.drawable.wiki_img_shelter, intArrayOf(R.string.wiki_ch3_p1, R.string.wiki_ch3_p2, R.string.wiki_ch3_p3), R.string.wiki_ch3_steps, R.string.wiki_ch3_warn),
    WikiChapter(R.string.wiki_ch4_title, R.string.wiki_ch4_summary, R.drawable.wiki_img_fire, intArrayOf(R.string.wiki_ch4_p1, R.string.wiki_ch4_p2, R.string.wiki_ch4_p3), R.string.wiki_ch4_steps, R.string.wiki_ch4_warn, photo = R.drawable.wiki_photo_ch04),
    WikiChapter(R.string.wiki_ch5_title, R.string.wiki_ch5_summary, R.drawable.wiki_img_aid, intArrayOf(R.string.wiki_ch5_p1, R.string.wiki_ch5_p2, R.string.wiki_ch5_p3), R.string.wiki_ch5_steps, R.string.wiki_ch5_warn),
    WikiChapter(R.string.wiki_ch6_title, R.string.wiki_ch6_summary, R.drawable.wiki_img_kit, intArrayOf(R.string.wiki_ch6_p1, R.string.wiki_ch6_p2, R.string.wiki_ch6_p3), R.string.wiki_ch6_steps, R.string.wiki_ch6_warn, photo = R.drawable.wiki_photo_ch06),
    WikiChapter(R.string.wiki_ch13_title, R.string.wiki_ch13_summary, R.drawable.wiki_img_trauma, intArrayOf(R.string.wiki_ch13_p1, R.string.wiki_ch13_p2, R.string.wiki_ch13_p3), R.string.wiki_ch13_steps, R.string.wiki_ch13_warn, photo = R.drawable.wiki_photo_ch13),
    WikiChapter(R.string.wiki_ch14_title, R.string.wiki_ch14_summary, R.drawable.wiki_img_cbrn, intArrayOf(R.string.wiki_ch14_p1, R.string.wiki_ch14_p2, R.string.wiki_ch14_p3), R.string.wiki_ch14_steps, R.string.wiki_ch14_warn, photo = R.drawable.wiki_photo_ch14),
    WikiChapter(R.string.wiki_ch15_title, R.string.wiki_ch15_summary, R.drawable.wiki_img_camo, intArrayOf(R.string.wiki_ch15_p1, R.string.wiki_ch15_p2, R.string.wiki_ch15_p3), R.string.wiki_ch15_steps, R.string.wiki_ch15_warn, photo = R.drawable.wiki_photo_ch15),
    WikiChapter(R.string.wiki_ch16_title, R.string.wiki_ch16_summary, R.drawable.wiki_img_food, intArrayOf(R.string.wiki_ch16_p1, R.string.wiki_ch16_p2, R.string.wiki_ch16_p3), R.string.wiki_ch16_steps, R.string.wiki_ch16_warn, photo = R.drawable.wiki_photo_ch16),
    WikiChapter(R.string.wiki_ch17_title, R.string.wiki_ch17_summary, R.drawable.wiki_img_nav, intArrayOf(R.string.wiki_ch17_p1, R.string.wiki_ch17_p2, R.string.wiki_ch17_p3), R.string.wiki_ch17_steps, R.string.wiki_ch17_warn, photo = R.drawable.wiki_photo_ch17),
    WikiChapter(R.string.wiki_ch18_title, R.string.wiki_ch18_summary, R.drawable.wiki_img_signal, intArrayOf(R.string.wiki_ch18_p1, R.string.wiki_ch18_p2, R.string.wiki_ch18_p3), R.string.wiki_ch18_steps, R.string.wiki_ch18_warn, photo = R.drawable.wiki_photo_ch18),
)

private val DIGITAL_CHAPTERS = listOf(
    WikiChapter(R.string.wiki_ch7_title, R.string.wiki_ch7_summary, R.drawable.wiki_img_tor, intArrayOf(R.string.wiki_ch7_p1, R.string.wiki_ch7_p2, R.string.wiki_ch7_p3), R.string.wiki_ch7_steps, R.string.wiki_ch7_warn),
    WikiChapter(R.string.wiki_ch8_title, R.string.wiki_ch8_summary, R.drawable.wiki_img_antifor, intArrayOf(R.string.wiki_ch8_p1, R.string.wiki_ch8_p2, R.string.wiki_ch8_p3), R.string.wiki_ch8_steps, R.string.wiki_ch8_warn, photo = R.drawable.wiki_photo_ch08),
    WikiChapter(R.string.wiki_ch9_title, R.string.wiki_ch9_summary, R.drawable.wiki_img_bug, intArrayOf(R.string.wiki_ch9_p1, R.string.wiki_ch9_p2, R.string.wiki_ch9_p3), R.string.wiki_ch9_steps, R.string.wiki_ch9_warn, photo = R.drawable.wiki_photo_ch09),
    WikiChapter(R.string.wiki_ch10_title, R.string.wiki_ch10_summary, R.drawable.wiki_img_auth, intArrayOf(R.string.wiki_ch10_p1, R.string.wiki_ch10_p2, R.string.wiki_ch10_p3), R.string.wiki_ch10_steps, R.string.wiki_ch10_warn, photo = R.drawable.wiki_photo_ch10),
    WikiChapter(R.string.wiki_ch11_title, R.string.wiki_ch11_summary, R.drawable.wiki_img_soceng, intArrayOf(R.string.wiki_ch11_p1, R.string.wiki_ch11_p2, R.string.wiki_ch11_p3), R.string.wiki_ch11_steps, R.string.wiki_ch11_warn, photo = R.drawable.wiki_photo_ch11),
    WikiChapter(R.string.wiki_ch12_title, R.string.wiki_ch12_summary, R.drawable.wiki_img_backup, intArrayOf(R.string.wiki_ch12_p1, R.string.wiki_ch12_p2, R.string.wiki_ch12_p3), R.string.wiki_ch12_steps, R.string.wiki_ch12_warn, photo = R.drawable.wiki_photo_ch12),
    WikiChapter(R.string.wiki_ch19_title, R.string.wiki_ch19_summary, R.drawable.wiki_img_commsres, intArrayOf(R.string.wiki_ch19_p1, R.string.wiki_ch19_p2, R.string.wiki_ch19_p3), R.string.wiki_ch19_steps, R.string.wiki_ch19_warn, photo = R.drawable.wiki_photo_ch19),
    WikiChapter(R.string.wiki_ch20_title, R.string.wiki_ch20_summary, R.drawable.wiki_img_privacy, intArrayOf(R.string.wiki_ch20_p1, R.string.wiki_ch20_p2, R.string.wiki_ch20_p3), R.string.wiki_ch20_steps, R.string.wiki_ch20_warn, photo = R.drawable.wiki_photo_ch20),
    WikiChapter(R.string.wiki_ch21_title, R.string.wiki_ch21_summary, R.drawable.wiki_img_power, intArrayOf(R.string.wiki_ch21_p1, R.string.wiki_ch21_p2, R.string.wiki_ch21_p3), R.string.wiki_ch21_steps, R.string.wiki_ch21_warn),
    WikiChapter(R.string.wiki_ch22_title, R.string.wiki_ch22_summary, R.drawable.wiki_img_psyche, intArrayOf(R.string.wiki_ch22_p1, R.string.wiki_ch22_p2, R.string.wiki_ch22_p3), R.string.wiki_ch22_steps, R.string.wiki_ch22_warn),
    WikiChapter(R.string.wiki_ch23_title, R.string.wiki_ch23_summary, R.drawable.wiki_img_medcard, intArrayOf(R.string.wiki_ch23_p1, R.string.wiki_ch23_p2, R.string.wiki_ch23_p3), R.string.wiki_ch23_steps, R.string.wiki_ch23_warn),
    WikiChapter(R.string.wiki_ch24_title, R.string.wiki_ch24_summary, R.drawable.wiki_img_bob, intArrayOf(R.string.wiki_ch24_p1, R.string.wiki_ch24_p2, R.string.wiki_ch24_p3), R.string.wiki_ch24_steps, R.string.wiki_ch24_warn, photo = R.drawable.wiki_photo_ch24),
)
