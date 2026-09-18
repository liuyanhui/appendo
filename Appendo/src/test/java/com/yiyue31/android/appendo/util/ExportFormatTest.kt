package com.yiyue31.android.appendo.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 出口格式化（v1.3，specs 68~70）：formatForExport 的格式契约。
 * 秒级时间戳、无结构标记、内容 trim、隔离行还原、用户 ZWSP 保留、CRLF 归一。
 */
class ExportFormatTest {

    private val zwsp = '\u200B'  // Kotlin 转义：零宽空格 U+200B

    @Test
    fun mixedTimestamps_exportedAtSecondPrecision() {
        val md = "# Appendo\n\n---\n\n## 2026-08-12 14:30:00.123\n\n毫秒条目\n\n---\n\n---\n\n## 2026-08-12 15:00:01\n\n秒级条目\n\n---\n"
        val out = EntryParser.formatForExport(EntryParser.parse(md))
        assertEquals("2026-08-12 14:30:00\n\n毫秒条目\n\n2026-08-12 15:00:01\n\n秒级条目", out)
    }

    @Test
    fun noStructuralMarkers() {
        val md = "# Appendo\n\n---\n\n## 2026-08-12 14:30:00\n\n内容行\n\n---\n"
        val out = EntryParser.formatForExport(EntryParser.parse(md))
        assertFalse("不应含 ## 标记", out.contains("##"))
        assertFalse("不应含 --- 分隔线", out.contains("---"))
        assertFalse("不应含文件头", out.contains("# Appendo"))
    }

    @Test
    fun contentTrimmed_innerLineBreaksKept() {
        val md = "## 2026-08-12 14:30:00\n\n\n\n第一行\n第二行\n\n\n\n"
        val out = EntryParser.formatForExport(EntryParser.parse(md))
        assertEquals("2026-08-12 14:30:00\n\n第一行\n第二行", out)
    }

    @Test
    fun isolatedLineRestoredAsContent_userZwspKept() {
        val md = "## 2026-08-12 14:30:00\n\n${zwsp}## 2026-01-01 09:00:00\nabc${zwsp}def\n\n"
        val out = EntryParser.formatForExport(EntryParser.parse(md))
        // 隔离行还原为原文（无 ZWSP 前缀）；用户自有 ZWSP 是原文的一部分，保留
        assertEquals("2026-08-12 14:30:00\n\n## 2026-01-01 09:00:00\nabc${zwsp}def", out)
    }

    @Test
    fun crlfFile_normalizedToLf() {
        val md = "## 2026-08-12 14:30:00\r\n\r\nA\r\nB\r\n"
        val out = EntryParser.formatForExport(EntryParser.parse(md))
        assertEquals("2026-08-12 14:30:00\n\nA\nB", out)
    }

    @Test
    fun emptyContentEntries_droppedByParse() {
        val md = "## 2026-08-12 14:30:00\n\n## 2026-08-12 15:00:00\n\n有内容\n\n"
        val entries = EntryParser.parse(md)
        assertEquals(1, entries.size)
        assertEquals("2026-08-12 15:00:00\n\n有内容", EntryParser.formatForExport(entries))
    }
}
