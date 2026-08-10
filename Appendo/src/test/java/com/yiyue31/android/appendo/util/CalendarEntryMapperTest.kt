package com.yiyue31.android.appendo.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CalendarEntryMapper] 纯函数单测：多行、含 ZWSP、纯链接、超长截断等场景。
 */
class CalendarEntryMapperTest {

    @Test
    fun `single line content maps to prefixed title and same description`() {
        val entry = CalendarEntryMapper.map("买牛奶")
        assertEquals("🔵 买牛奶", entry.title)
        assertEquals("买牛奶", entry.description)
    }

    @Test
    fun `multi-line content uses first non-blank line as title and full text as description`() {
        val content = "第一行标题\n第二行详情\n第三行"
        val entry = CalendarEntryMapper.map(content)
        assertEquals("🔵 第一行标题", entry.title)
        assertEquals(content, entry.description)
    }

    @Test
    fun `leading blank lines are skipped for title`() {
        val entry = CalendarEntryMapper.map("\n\n实际首行\n第二行")
        assertEquals("🔵 实际首行", entry.title)
        assertEquals("实际首行\n第二行", entry.description)
    }

    @Test
    fun `timestamp-pattern line isolation markers are stripped`() {
        val marked = "${EntryParser.ISOLATION_MARKER}## 2026-01-01 12:00:00"
        val entry = CalendarEntryMapper.map(marked)
        assertEquals("🔵 ## 2026-01-01 12:00:00", entry.title)
        assertEquals("## 2026-01-01 12:00:00", entry.description)
    }

    @Test
    fun `pure url content maps cleanly`() {
        val url = "https://example.com/some/path?x=1"
        val entry = CalendarEntryMapper.map(url)
        assertEquals("🔵 $url", entry.title)
        assertEquals(url, entry.description)
    }

    @Test
    fun `overlong title is truncated with ellipsis and keeps prefix`() {
        val long = "A".repeat(200)
        val entry = CalendarEntryMapper.map(long)
        assertTrue("title should be truncated to <= 80", entry.title.length <= 80)
        assertTrue("title should keep prefix", entry.title.startsWith("🔵 "))
        assertTrue("title should end with ellipsis", entry.title.endsWith("…"))
        // 描述保留完整原文
        assertEquals(long, entry.description)
    }
}
