package com.yiyue31.android.appendo.util

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import java.io.File

/**
 * FileBasedMarkdownFile 集成测试（T-008）：纯 JVM。
 *
 * 本环境 Robolectric 无法下载 android-all jar（SSL 握手失败），故改纯 JVM：
 * - Context 用 Mockito mock 占位（FileBasedMarkdownFile 不使用 context，仅存引用）。
 * - 文件操作用真实 java.io.File + 临时目录，atomicWrite 走真实 FS。
 *
 * 覆盖 atomicWrite、EntryParser 集成、毫秒时间戳、ZWSP 隔离、单调防碰撞、appendEntry/delete/update。
 */
class FileBasedMarkdownFileTest {

    private val context: Context = mock()
    private lateinit var file: File
    private lateinit var md: FileBasedMarkdownFile

    @Before
    fun setup() {
        file = File.createTempFile("appendo-test-", ".md")
        file.delete() // 起始不存在
        md = FileBasedMarkdownFile(context, file)
    }

    @After
    fun teardown() {
        file.delete()
    }

    @Test
    fun initHeader_createsHeader() {
        assertTrue(md.initHeader())
        assertEquals("# Appendo\n", md.readAll())
    }

    @Test
    fun append_writesMillisecondTimestampAndContent() {
        md.initHeader()
        assertTrue(md.append("hello"))
        val content = md.readAll()
        assertTrue("应含毫秒时间戳行", content.contains(Regex("## \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}")))
        assertTrue(content.contains("hello"))
        assertEquals(1, md.count())
    }

    @Test
    fun appendTwice_monotonicTimestamps() {
        md.initHeader()
        md.append("a")
        md.append("b")
        val entries = EntryParser.parse(md.readAll())
        assertEquals(2, entries.size)
        val t1 = EntryParser.parseTimestampToInstant(entries[0].rawTimestamp)!!
        val t2 = EntryParser.parseTimestampToInstant(entries[1].rawTimestamp)!!
        assertTrue("第二条时间戳应严格大于第一条（单调）", t2.isAfter(t1))
    }

    @Test
    fun appendEntry_preservesGivenTimestamp() {
        md.initHeader()
        md.appendEntry("2020-01-01 00:00:00.000", "archived")
        val entries = EntryParser.parse(md.readAll())
        assertEquals(1, entries.size)
        assertEquals("2020-01-01 00:00:00.000", entries[0].rawTimestamp)
    }

    @Test
    fun deleteEntry_removesTarget() {
        md.initHeader()
        md.appendEntry("2026-07-20 09:00:00.000", "first")
        md.appendEntry("2026-07-20 10:00:00.000", "second")
        assertTrue(md.deleteEntry("2026-07-20 09:00:00.000"))
        val entries = EntryParser.parse(md.readAll())
        assertEquals(1, entries.size)
        assertEquals("second", entries[0].content)
    }

    @Test
    fun deleteEntry_notFoundReturnsFalse() {
        md.initHeader()
        md.appendEntry("2026-07-20 09:00:00.000", "x")
        assertFalse(md.deleteEntry("1999-01-01 00:00:00.000"))
    }

    @Test
    fun updateEntry_replacesContentKeepsTimestamp() {
        md.initHeader()
        md.appendEntry("2026-07-20 09:00:00.000", "old")
        assertTrue(md.updateEntry("2026-07-20 09:00:00.000", "new"))
        val entries = EntryParser.parse(md.readAll())
        assertEquals(1, entries.size)
        assertEquals("2026-07-20 09:00:00.000", entries[0].rawTimestamp)
        assertEquals("new", entries[0].content)
    }

    @Test
    fun append_isolatesCollisionContent() {
        md.initHeader()
        md.append("## 2020-01-01 00:00:00") // 内容恰好是时间戳格式
        val entries = EntryParser.parse(md.readAll())
        assertEquals("不被误切为两条", 1, entries.size)
        assertEquals("## 2020-01-01 00:00:00", entries[0].content) // 还原原文
    }

    @Test
    fun clear_resetsToHeader() {
        md.initHeader()
        md.append("x")
        md.clear()
        assertEquals("# Appendo\n", md.readAll())
    }

    @Test
    fun atomicWrite_leavesNoOrphanTmp() {
        md.initHeader()
        md.append("x")
        val parent = file.parentFile!!
        val orphans = parent.listFiles { f -> f.name.startsWith(file.name + ".tmp") }
        assertTrue("不应残留 tmp 文件", orphans?.isEmpty() ?: true)
    }
}
