package com.yiyue31.android.appendo.ui

import com.yiyue31.android.appendo.util.EntryKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * retainedSelection 选中集维护单测（topic-move 设计 §7.3 / §10.2 第四行 ①~③）。
 * LinkEntry 为纯数据类（timestampDisplay 派生自 EntryParser 纯函数），纯 JVM 可直接构造，无需 fixture。
 */
class MultiSelectTest {

    private fun entry(ts: String, content: String) = LinkEntry(ts, content)

    // --- ① 新条目插入（头部/中部）后既有选中全保持 ---

    @Test
    fun testRetainedSelection_insertAtHeadAndMiddle_existingSelectionAllKept() {
        val e1 = entry("2026-11-01 10:00:00", "旧条目1")
        val e2 = entry("2026-11-01 11:00:00", "旧条目2")
        val e3 = entry("2026-11-01 12:00:00", "旧条目3")
        val selected = setOf(e1.entryKey, e3.entryKey)

        // 并发分享插入（列表按时间戳降序，新条目在头部；中部插入旧时间戳恢复条目）
        val newHead = entry("2026-11-02 09:00:00", "新插入（头部）")
        val newMid = entry("2026-11-01 11:30:00", "新插入（中部）")
        val refreshed = listOf(newHead, e1, newMid, e2, e3)

        val retained = retainedSelection(selected, refreshed)

        assertEquals("新条目插入头部/中部后，既有选中应全保持", selected, retained)
    }

    @Test
    fun testRetainedSelection_newEntries_defaultUnselected() {
        val e1 = entry("2026-11-01 10:00:00", "旧条目1")
        val selected = setOf(e1.entryKey)

        val newHead = entry("2026-11-02 09:00:00", "新插入（头部）")
        val retained = retainedSelection(selected, listOf(newHead, e1))

        assertEquals("新插入条目默认未选（不进选中集）", 1, retained.size)
        assertTrue("选中集仍只含既有条目", e1.entryKey in retained)
    }

    // --- ② 选中条目被删后从集合剔除 ---

    @Test
    fun testRetainedSelection_selectedEntryDeleted_removedFromSet() {
        val e1 = entry("2026-11-01 10:00:00", "条目1")
        val e2 = entry("2026-11-01 11:00:00", "条目2")
        val e3 = entry("2026-11-01 12:00:00", "条目3")
        val selected = setOf(e1.entryKey, e2.entryKey, e3.entryKey)

        // e2 被删（滑动删除/外部编辑后的刷新视图）
        val retained = retainedSelection(selected, listOf(e3, e1))

        assertEquals("被删条目应从选中集剔除，其余保持", setOf(e1.entryKey, e3.entryKey), retained)
    }

    // --- ③ 空集/全保留边界 ---

    @Test
    fun testRetainedSelection_emptySelection_staysEmpty() {
        val entries = listOf(
            entry("2026-11-01 10:00:00", "条目1"),
            entry("2026-11-01 11:00:00", "条目2")
        )

        val retained = retainedSelection(emptySet(), entries)

        assertTrue("空选中集应保持为空", retained.isEmpty())
    }

    @Test
    fun testRetainedSelection_allSelectedAllPresent_allRetained() {
        val entries = listOf(
            entry("2026-11-01 10:00:00", "条目1"),
            entry("2026-11-01 11:00:00", "条目2"),
            entry("2026-11-01 11:00:00", "条目2") // 同 key 双条（§12：勾选态联动，可接受）
        )
        val selected = entries.map { it.entryKey }.toSet()

        val retained = retainedSelection(selected, entries)

        assertEquals("全部条目仍在列表时，选中集应原样保留", selected, retained)
    }

    @Test
    fun testRetainedSelection_allDeleted_becomesEmpty() {
        val selected = setOf(
            entry("2026-11-01 10:00:00", "条目1").entryKey,
            entry("2026-11-01 11:00:00", "条目2").entryKey
        )

        val retained = retainedSelection(selected, emptyList())

        assertTrue("全部被删后选中集应变空", retained.isEmpty())
    }

    // --- entryKey 扩展（§6.2 注：放 ui 层）---

    @Test
    fun testEntryKey_builtFromTimestampAndContent() {
        val e = entry("2026-11-01 10:00:00", "内容")

        assertEquals(
            "entryKey 应由 LinkEntry 的 timestamp/content 字段构造",
            EntryKey("2026-11-01 10:00:00", "内容"),
            e.entryKey
        )
    }
}
