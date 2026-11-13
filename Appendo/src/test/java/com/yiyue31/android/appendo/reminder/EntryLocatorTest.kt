package com.yiyue31.android.appendo.reminder

import com.yiyue31.android.appendo.util.EntryParser
import com.yiyue31.android.appendo.util.MarkdownFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * EntryLocator 纯函数核心测试（戊批次 T-22，设计 §20.1/§25.1）：主优先/主题兜底/多主题确定性
 * 次序/全库 miss/buildIndex 同源。纯 JVM——生产入口（Context/SP/锁）不经此面，消费方接线由
 * 编译与 T-27 真机覆盖。
 */
class EntryLocatorTest {

    private fun contentWithEntry(ts: String, text: String = "内容"): String =
        MarkdownFormatter.FILE_HEADER + EntryParser.format(ts, text)

    private val mainLike = File("/main.md")
    private val topicA = File("/topics/a.md")
    private val topicB = File("/topics/b.md")

    // ---------- locateInContents ----------

    @Test
    fun locate_mainHasTs_returnsMainFile() {
        val result = EntryLocator.locateInContents(
            mainContent = contentWithEntry("2026-01-01 10:00:00.000"),
            topicContents = listOf(topicA to contentWithEntry("2026-01-02 10:00:00.000")),
            ts = "2026-01-01 10:00:00.000"
        )
        assertEquals(EntryLocation.MainFile to "内容", result)
    }

    @Test
    fun locate_sameTsInMainAndTopic_mainWins() {
        val ts = "2026-01-01 10:00:00.000"
        val result = EntryLocator.locateInContents(
            mainContent = contentWithEntry(ts, text = "主文件版"),
            topicContents = listOf(topicA to contentWithEntry(ts, text = "主题版")),
            ts = ts
        )
        assertEquals("同 ts 两处并存→主文件（需求 95(b)）", EntryLocation.MainFile to "主文件版", result)
    }

    @Test
    fun locate_onlyTopicHasTs_returnsThatTopic() {
        val result = EntryLocator.locateInContents(
            mainContent = contentWithEntry("2026-01-01 10:00:00.000"),
            topicContents = listOf(topicA to contentWithEntry("2026-01-02 10:00:00.000", text = "主题条目")),
            ts = "2026-01-02 10:00:00.000"
        )
        assertEquals(EntryLocation.Topic(topicA) to "主题条目", result)
    }

    @Test
    fun locate_sameTsInMultipleTopics_firstInListWins() {
        val ts = "2026-01-02 10:00:00.000"
        val result = EntryLocator.locateInContents(
            mainContent = null,
            topicContents = listOf(
                topicA to contentWithEntry(ts, text = "A 版"),
                topicB to contentWithEntry(ts, text = "B 版")
            ),
            ts = ts
        )
        assertEquals("多主题并存→列表序首个（生产＝createdAt 倒序——确定性次序）", EntryLocation.Topic(topicA) to "A 版", result)
    }

    @Test
    fun locate_mainUnreadable_fallsToTopics() {
        val result = EntryLocator.locateInContents(
            mainContent = null, // 主文件读失败→按无命中跳过（类 KDoc 读失败语义）
            topicContents = listOf(topicA to contentWithEntry("2026-01-02 10:00:00.000")),
            ts = "2026-01-02 10:00:00.000"
        )
        assertEquals(EntryLocation.Topic(topicA) to "内容", result)
    }

    @Test
    fun locate_nowhere_returnsNull() {
        assertNull(
            EntryLocator.locateInContents(
                mainContent = contentWithEntry("2026-01-01 10:00:00.000"),
                topicContents = listOf(topicA to contentWithEntry("2026-01-02 10:00:00.000")),
                ts = "2026-03-03 10:00:00.000"
            )
        )
    }

    // ---------- buildIndexFromContents ----------

    @Test
    fun buildIndex_mainOverwritesTopic_multiTopicFirstWins_containsAll() {
        val shared = "2026-01-02 10:00:00.000"
        val index = EntryLocator.buildIndexFromContents(
            mainContent = contentWithEntry("2026-01-01 10:00:00.000", text = "主独有") +
                EntryParser.format(shared, "主覆盖"),
            topicContents = listOf(
                topicA to contentWithEntry(shared, text = "A 版"),
                topicB to contentWithEntry(shared, text = "B 版") + EntryParser.format("2026-01-04 10:00:00.000", "B 独有")
            )
        )
        assertEquals("主覆盖主题", EntryLocation.MainFile to "主覆盖", index[shared])
        assertEquals(EntryLocation.MainFile to "主独有", index["2026-01-01 10:00:00.000"])
        assertEquals("主题独有条目在索引", EntryLocation.Topic(topicB) to "B 独有", index["2026-01-04 10:00:00.000"])
        assertEquals(3, index.size)
    }

    @Test
    fun buildIndex_sameSourceAsLocate_forEveryTs() {
        val main = contentWithEntry("2026-01-01 10:00:00.000", text = "m")
        val topics = listOf(
            topicA to contentWithEntry("2026-01-02 10:00:00.000", text = "a"),
            topicB to contentWithEntry("2026-01-02 10:00:00.000", text = "b")
        )
        val index = EntryLocator.buildIndexFromContents(main, topics)
        for (ts in listOf("2026-01-01 10:00:00.000", "2026-01-02 10:00:00.000")) {
            assertEquals(
                "index[ts] 与 locateInContents 同源（设计 §20.1）",
                EntryLocator.locateInContents(main, topics, ts),
                index[ts]
            )
        }
    }

    // ---------- outcomeOf（三态：设计 §20.1 修订 2026-11-13——读失败≠不存在） ----------

    @Test
    fun outcome_hit_returnsFound() {
        val outcome = EntryLocator.outcomeOf(
            mainContent = contentWithEntry("2026-01-01 10:00:00.000"),
            topicContents = listOf(topicA to contentWithEntry("2026-01-02 10:00:00.000")),
            anyUnreadable = true, // 命中可读文件即可——不可读文件不影响已命中的判定
            ts = "2026-01-01 10:00:00.000"
        )
        assertEquals(LocateOutcome.Found(EntryLocation.MainFile, "内容"), outcome)
    }

    @Test
    fun outcome_missAllReadable_returnsNotFound() {
        val outcome = EntryLocator.outcomeOf(
            mainContent = contentWithEntry("2026-01-01 10:00:00.000"),
            topicContents = listOf(topicA to contentWithEntry("2026-01-02 10:00:00.000")),
            anyUnreadable = false,
            ts = "2026-03-03 10:00:00.000"
        )
        assertEquals("全库可读且无→可安全取消/删除", LocateOutcome.NotFound, outcome)
    }

    @Test
    fun outcome_missWithUnreadable_returnsUnknown() {
        val outcome = EntryLocator.outcomeOf(
            mainContent = null, // 主文件不可读——条目可能就在其中
            topicContents = listOf(topicA to contentWithEntry("2026-01-02 10:00:00.000")),
            anyUnreadable = true,
            ts = "2026-03-03 10:00:00.000"
        )
        assertEquals("读失败≠不存在→消费方不得取消/删除", LocateOutcome.Unknown, outcome)
    }

    @Test
    fun outcome_hitInTopicDespiteUnreadableMain_returnsFound() {
        val outcome = EntryLocator.outcomeOf(
            mainContent = null,
            topicContents = listOf(topicA to contentWithEntry("2026-01-02 10:00:00.000", text = "主题条目")),
            anyUnreadable = true,
            ts = "2026-01-02 10:00:00.000"
        )
        assertEquals("主不可读但命中主题→仍 Found（不降级为 Unknown）", LocateOutcome.Found(EntryLocation.Topic(topicA), "主题条目"), outcome)
    }
}
