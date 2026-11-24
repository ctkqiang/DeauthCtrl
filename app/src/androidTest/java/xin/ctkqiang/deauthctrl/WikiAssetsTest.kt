package xin.ctkqiang.deauthctrl

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Wiki 资源完整性 —— 用真实框架代码 inflate 全部配图与字符串:
 * 矢量 pathData 若有运行时解析问题(如非法路径命令),这里抛出与真机相同的异常。
 */
@RunWith(AndroidJUnit4::class)
class WikiAssetsTest {

    private val drawables = listOf(
        R.drawable.wiki_img_comms,
        R.drawable.wiki_img_water,
        R.drawable.wiki_img_shelter,
        R.drawable.wiki_img_fire,
        R.drawable.wiki_img_aid,
        R.drawable.wiki_img_kit,
        R.drawable.wiki_img_tor,
        R.drawable.wiki_img_antifor,
        R.drawable.wiki_img_bug,
        R.drawable.wiki_img_auth,
        R.drawable.wiki_img_soceng,
        R.drawable.wiki_img_backup,
        R.drawable.wiki_img_trauma,
        R.drawable.wiki_img_cbrn,
        R.drawable.wiki_img_camo,
        R.drawable.wiki_img_food,
        R.drawable.wiki_img_nav,
        R.drawable.wiki_img_signal,
        R.drawable.wiki_img_commsres,
        R.drawable.wiki_img_privacy,
        R.drawable.wiki_img_power,
        R.drawable.wiki_img_psyche,
        R.drawable.wiki_img_medcard,
        R.drawable.wiki_img_bob,
    )

    /** 真实照片(公有领域/CC0,离线打包;部分章节无照片用矢量兜底) */
    private val photos = listOf(
        R.drawable.wiki_photo_ch01,
        R.drawable.wiki_photo_ch04,
        R.drawable.wiki_photo_ch06,
        R.drawable.wiki_photo_ch08,
        R.drawable.wiki_photo_ch09,
        R.drawable.wiki_photo_ch10,
        R.drawable.wiki_photo_ch11,
        R.drawable.wiki_photo_ch12,
        R.drawable.wiki_photo_ch13,
        R.drawable.wiki_photo_ch14,
        R.drawable.wiki_photo_ch15,
        R.drawable.wiki_photo_ch16,
        R.drawable.wiki_photo_ch17,
        R.drawable.wiki_photo_ch18,
        R.drawable.wiki_photo_ch19,
        R.drawable.wiki_photo_ch20,
        R.drawable.wiki_photo_ch24,
    )

    private val strings = listOf(
        R.string.wiki_ch1_title, R.string.wiki_ch1_summary, R.string.wiki_ch1_p1, R.string.wiki_ch1_p2, R.string.wiki_ch1_p3, R.string.wiki_ch1_steps, R.string.wiki_ch1_warn,
        R.string.wiki_ch2_title, R.string.wiki_ch2_summary, R.string.wiki_ch2_p1, R.string.wiki_ch2_p2, R.string.wiki_ch2_p3, R.string.wiki_ch2_steps, R.string.wiki_ch2_warn,
        R.string.wiki_ch3_title, R.string.wiki_ch3_summary, R.string.wiki_ch3_p1, R.string.wiki_ch3_p2, R.string.wiki_ch3_p3, R.string.wiki_ch3_steps, R.string.wiki_ch3_warn,
        R.string.wiki_ch4_title, R.string.wiki_ch4_summary, R.string.wiki_ch4_p1, R.string.wiki_ch4_p2, R.string.wiki_ch4_p3, R.string.wiki_ch4_steps, R.string.wiki_ch4_warn,
        R.string.wiki_ch5_title, R.string.wiki_ch5_summary, R.string.wiki_ch5_p1, R.string.wiki_ch5_p2, R.string.wiki_ch5_p3, R.string.wiki_ch5_steps, R.string.wiki_ch5_warn,
        R.string.wiki_ch6_title, R.string.wiki_ch6_summary, R.string.wiki_ch6_p1, R.string.wiki_ch6_p2, R.string.wiki_ch6_p3, R.string.wiki_ch6_steps, R.string.wiki_ch6_warn,
        R.string.wiki_ch7_title, R.string.wiki_ch7_summary, R.string.wiki_ch7_p1, R.string.wiki_ch7_p2, R.string.wiki_ch7_p3, R.string.wiki_ch7_steps, R.string.wiki_ch7_warn,
        R.string.wiki_ch8_title, R.string.wiki_ch8_summary, R.string.wiki_ch8_p1, R.string.wiki_ch8_p2, R.string.wiki_ch8_p3, R.string.wiki_ch8_steps, R.string.wiki_ch8_warn,
        R.string.wiki_ch9_title, R.string.wiki_ch9_summary, R.string.wiki_ch9_p1, R.string.wiki_ch9_p2, R.string.wiki_ch9_p3, R.string.wiki_ch9_steps, R.string.wiki_ch9_warn,
        R.string.wiki_ch10_title, R.string.wiki_ch10_summary, R.string.wiki_ch10_p1, R.string.wiki_ch10_p2, R.string.wiki_ch10_p3, R.string.wiki_ch10_steps, R.string.wiki_ch10_warn,
        R.string.wiki_ch11_title, R.string.wiki_ch11_summary, R.string.wiki_ch11_p1, R.string.wiki_ch11_p2, R.string.wiki_ch11_p3, R.string.wiki_ch11_steps, R.string.wiki_ch11_warn,
        R.string.wiki_ch12_title, R.string.wiki_ch12_summary, R.string.wiki_ch12_p1, R.string.wiki_ch12_p2, R.string.wiki_ch12_p3, R.string.wiki_ch12_steps, R.string.wiki_ch12_warn,
        R.string.wiki_ch13_title, R.string.wiki_ch13_summary, R.string.wiki_ch13_p1, R.string.wiki_ch13_p2, R.string.wiki_ch13_p3, R.string.wiki_ch13_steps, R.string.wiki_ch13_warn,
        R.string.wiki_ch14_title, R.string.wiki_ch14_summary, R.string.wiki_ch14_p1, R.string.wiki_ch14_p2, R.string.wiki_ch14_p3, R.string.wiki_ch14_steps, R.string.wiki_ch14_warn,
        R.string.wiki_ch15_title, R.string.wiki_ch15_summary, R.string.wiki_ch15_p1, R.string.wiki_ch15_p2, R.string.wiki_ch15_p3, R.string.wiki_ch15_steps, R.string.wiki_ch15_warn,
        R.string.wiki_ch16_title, R.string.wiki_ch16_summary, R.string.wiki_ch16_p1, R.string.wiki_ch16_p2, R.string.wiki_ch16_p3, R.string.wiki_ch16_steps, R.string.wiki_ch16_warn,
        R.string.wiki_ch17_title, R.string.wiki_ch17_summary, R.string.wiki_ch17_p1, R.string.wiki_ch17_p2, R.string.wiki_ch17_p3, R.string.wiki_ch17_steps, R.string.wiki_ch17_warn,
        R.string.wiki_ch18_title, R.string.wiki_ch18_summary, R.string.wiki_ch18_p1, R.string.wiki_ch18_p2, R.string.wiki_ch18_p3, R.string.wiki_ch18_steps, R.string.wiki_ch18_warn,
        R.string.wiki_ch19_title, R.string.wiki_ch19_summary, R.string.wiki_ch19_p1, R.string.wiki_ch19_p2, R.string.wiki_ch19_p3, R.string.wiki_ch19_steps, R.string.wiki_ch19_warn,
        R.string.wiki_ch20_title, R.string.wiki_ch20_summary, R.string.wiki_ch20_p1, R.string.wiki_ch20_p2, R.string.wiki_ch20_p3, R.string.wiki_ch20_steps, R.string.wiki_ch20_warn,
        R.string.wiki_ch21_title, R.string.wiki_ch21_summary, R.string.wiki_ch21_p1, R.string.wiki_ch21_p2, R.string.wiki_ch21_p3, R.string.wiki_ch21_steps, R.string.wiki_ch21_warn,
        R.string.wiki_ch22_title, R.string.wiki_ch22_summary, R.string.wiki_ch22_p1, R.string.wiki_ch22_p2, R.string.wiki_ch22_p3, R.string.wiki_ch22_steps, R.string.wiki_ch22_warn,
        R.string.wiki_ch23_title, R.string.wiki_ch23_summary, R.string.wiki_ch23_p1, R.string.wiki_ch23_p2, R.string.wiki_ch23_p3, R.string.wiki_ch23_steps, R.string.wiki_ch23_warn,
        R.string.wiki_ch24_title, R.string.wiki_ch24_summary, R.string.wiki_ch24_p1, R.string.wiki_ch24_p2, R.string.wiki_ch24_p3, R.string.wiki_ch24_steps, R.string.wiki_ch24_warn,
    )

    @Test
    fun wikiDrawables_inflate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        drawables.forEach { id ->
            val d = context.getDrawable(id)
            assertNotNull("drawable 0x${id.toString(16)} failed to inflate", d)
        }
    }

    @Test
    fun wikiPhotos_inflate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        photos.forEach { id ->
            val d = context.getDrawable(id)
            assertNotNull("photo 0x${id.toString(16)} failed to inflate", d)
        }
    }

    @Test
    fun wikiStrings_resolve() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        strings.forEach { id ->
            val text = context.getString(id)
            assertNotNull("string 0x${id.toString(16)} unresolved", text)
            assert(text.isNotBlank()) { "string 0x${id.toString(16)} is blank" }
        }
    }
}
