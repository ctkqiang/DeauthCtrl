package xin.ctkqiang.deauthctrl

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Wiki 生存指南端到端:主页网格滚动到入口 → 点开 → 章节列表可见。
 * 真机复现用户路径,任何运行时异常直接以测试失败暴露。
 */
@RunWith(AndroidJUnit4::class)
class WikiScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun wikiScreen_opensFromHomeAndShowsChapters() {
        val homeWiki = rule.activity.getString(R.string.home_wiki_name)
        val ch1Title = rule.activity.getString(R.string.wiki_ch1_title)

        // 等启动序列结束、主页懒加载网格出现
        rule.waitUntil(timeoutMillis = 45_000) {
            rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty()
        }

        // 滚动网格直到 wiki 卡片可见并点击
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(homeWiki))
        rule.onNode(hasText(homeWiki)).performClick()

        // 断言章节列表出现
        rule.waitUntil(timeoutMillis = 15_000) {
            rule.onAllNodes(hasText(ch1Title)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNode(hasText(ch1Title)).assertIsDisplayed()
    }
}
