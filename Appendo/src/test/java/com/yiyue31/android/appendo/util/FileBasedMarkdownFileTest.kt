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

    // ==================== TD-012 读失败=空串 守卫 ====================
    // 场景模拟：file 路径为目录（exists()=true、读取必抛异常）= 「读失败但文件存在」。

    @Test
    fun readAllWithStatus_emptyFile_reportsNotFailed() {
        file.createNewFile() // 真空文件：不是读失败
        val result = md.readAllWithStatus()
        assertEquals("", result.content)
        assertFalse(result.failed)
        assertFalse(result.recovered)
    }

    @Test
    fun readAllWithStatus_readFailure_flagsFailed() {
        file.mkdirs()
        val result = md.readAllWithStatus()
        assertTrue("读失败必须置 failed", result.failed)
    }

    @Test
    fun append_readFailure_abortsWithoutRewrite() {
        file.mkdirs()
        assertFalse("读失败必须中止，禁止 header+新条目重写", md.append("x"))
        assertTrue("不应产生任何写入痕迹", file.listFiles()!!.isEmpty())
    }

    @Test
    fun appendEntry_readFailure_aborts() {
        file.mkdirs()
        assertFalse(md.appendEntry("2026-01-01 00:00:00.000", "x"))
        assertTrue(file.listFiles()!!.isEmpty())
    }

    @Test
    fun initHeader_readFailure_doesNotOverwrite() {
        file.mkdirs()
        assertFalse("读失败不得当空文件覆写", md.initHeader())
        assertTrue(file.listFiles()!!.isEmpty())
    }

    @Test
    fun append_separatorShapeContent_roundTrips() { // TD-015：--- / # Appendo 行不丢
        md.initHeader()
        assertTrue(md.append("---\n# Appendo 备忘\n正文"))
        val entries = EntryParser.parse(md.readAll())
        assertEquals(1, entries.size)
        assertEquals("---\n# Appendo 备忘\n正文", entries[0].content)
    }

    @Test
    fun concurrentAppend_allEntriesPreserved_noInterleaving() { // TD-020①：全局锁并发回归
        md.initHeader()
        val threads = (1..2).map { t ->
            Thread {
                repeat(25) { md.append("线程$t-条目$it") }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        val entries = EntryParser.parse(md.readAll())
        assertEquals("50 条全部落盘（无丢失）", 50, entries.size)
        assertEquals("内容无重复/交错", 50, entries.map { it.content }.toSet().size)
        val instants = entries.map { EntryParser.parseTimestampToInstant(it.rawTimestamp) }
        assertTrue("时间戳全部可解析", instants.none { it == null })
        val ts = instants.filterNotNull()
        assertEquals("时间戳唯一（单调防碰撞跨线程也成立）", ts.size, ts.toSet().size)
        assertTrue("按文件序严格递增", ts.zipWithNext().all { (a, b) -> b.isAfter(a) })
    }

    @Test
    fun deleteEntry_duplicateTimestamp_removesFirst() { // TD-020②：首条命中语义
        md.initHeader()
        md.appendEntry("2026-08-31 09:00:00.000", "第一条")
        md.appendEntry("2026-08-31 09:00:00.000", "第二条")
        assertTrue(md.deleteEntry("2026-08-31 09:00:00.000"))
        val entries = EntryParser.parse(md.readAll())
        assertEquals(1, entries.size)
        assertEquals("删除的是第一条（findEntryBounds 首条命中）", "第二条", entries[0].content)
    }
}
