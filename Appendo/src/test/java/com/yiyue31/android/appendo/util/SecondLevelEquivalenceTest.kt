package com.yiyue31.android.appendo.util

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import java.io.File

/**
 * 秒级旧条目等价类（test-cases backlog）：v1.4 前内容全为秒级（无 .SSS）。
 * 显示/编辑 ts 不变/同 ts 删靠前/同毫秒追加防碰撞/移出原样——JVM 直测。
 */
class SecondLevelEquivalenceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = mock()

    private fun md(name: String = "t.md"): FileBasedMarkdownFile {
        val f = File(tmp.root, name)
        f.writeText(MarkdownFormatter.FILE_HEADER)
        return FileBasedMarkdownFile(context, f)
    }

    private fun sec(ts: String, content: String) = EntryParser.format(ts, content)

    @Test
    fun secondLevel_parseAndUpdate_keepsTimestampNoMillis() {
        val m = md()
        m.writeAll(MarkdownFormatter.FILE_HEADER + sec("2026-01-01 10:00:00", "旧"))
        val parsed = EntryParser.parse(m.readAll())
        assertEquals("2026-01-01 10:00:00", parsed[0].rawTimestamp)
        assertTrue(m.updateEntry("2026-01-01 10:00:00", "新"))
        val after = EntryParser.parse(m.readAll())
        assertEquals("编辑不补毫秒", "2026-01-01 10:00:00", after[0].rawTimestamp)
        assertEquals("新", after[0].content)
    }

    @Test
    fun sameTimestamp_twoEntries_deleteRemovesFirstOnly() {
        val m = md()
        m.writeAll(MarkdownFormatter.FILE_HEADER + sec("2026-01-01 10:00:00", "甲") + sec("2026-01-01 10:00:00", "乙"))
        assertTrue(m.deleteEntry("2026-01-01 10:00:00"))
        val after = EntryParser.parse(m.readAll())
        assertEquals("同 ts 删靠前一条", 1, after.size)
        assertEquals("乙", after[0].content)
    }

    @Test
    fun consecutiveAppend_sameMillis_uniqueTimestamps() {
        val m = md()
        val a = m.appendReturningTimestamp("一")
        val b = m.appendReturningTimestamp("二")
        assertNotEquals("同毫秒连续追加 ts 唯一", a, b)
    }

    @Test
    fun moveOut_secondLevel_preservesTimestampAsIs() {
        val src = md("src.md")
        src.writeAll(MarkdownFormatter.FILE_HEADER + sec("2026-01-01 10:00:00", "旧"))
        val dst = md("dst.md")
        val key = EntryKey.of(EntryParser.parse(src.readAll())[0])
        val r = TopicMoveTransaction.move(src, dst, setOf(key))
        assertTrue(r is MoveResult.Success)
        val moved = EntryParser.parse(dst.readAll())
        assertEquals("移出原样不补毫秒", "2026-01-01 10:00:00", moved[0].rawTimestamp)
    }
}
