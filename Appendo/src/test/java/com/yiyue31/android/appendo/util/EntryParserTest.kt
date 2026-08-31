package com.yiyue31.android.appendo.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * EntryParser 全量测试（T-002 读侧 + T-003 写侧/单调/剥离 + T-004 边界/恢复）。
 * 纯 JVM，不依赖 Android。
 */
class EntryParserTest {

    private val zwsp = EntryParser.ISOLATION_MARKER.first() // '​'

    // ---------- isTimestampLine（T-002）----------

    @Test
    fun isTimestampLine_matchesSecondPrecision() {
        assertTrue(EntryParser.isTimestampLine("## 2026-07-20 09:30:00"))
    }

    @Test
    fun isTimestampLine_matchesMillisecondPrecisionVariants() {
        assertTrue(EntryParser.isTimestampLine("## 2026-07-20 09:30:00.123"))
        assertTrue(EntryParser.isTimestampLine("## 2026-07-20 09:30:00.1"))
        assertTrue(EntryParser.isTimestampLine("## 2026-07-20 09:30:00.12"))
    }

    @Test
    fun isTimestampLine_rejectsTrailingText() {
        assertFalse(EntryParser.isTimestampLine("## 2026-07-20 09:30:00 系统启动"))
    }

    @Test
    fun isTimestampLine_rejectsZwspPrefixedLine() {
        assertFalse(EntryParser.isTimestampLine("${EntryParser.ISOLATION_MARKER}## 2026-07-20 09:30:00"))
    }

    @Test
    fun isTimestampLine_rejectsPlainAndStructuralLines() {
        assertFalse(EntryParser.isTimestampLine(""))
        assertFalse(EntryParser.isTimestampLine("一些内容"))
        assertFalse(EntryParser.isTimestampLine("# Appendo"))
        assertFalse(EntryParser.isTimestampLine("---"))
    }

    // ---------- parse：基础格式（T-002）----------

    @Test
    fun parse_secondPrecisionEntries() {
        val content = """
            # Appendo

            ---

            ## 2026-07-20 09:30:00

            第一条

            ---

            ## 2026-07-20 10:00:00

            第二条

            ---
        """.trimIndent()

        val entries = EntryParser.parse(content)
        assertEquals(2, entries.size)
        assertEquals("2026-07-20 09:30:00", entries[0].rawTimestamp)
        assertEquals("第一条", entries[0].content)
        assertEquals("2026-07-20 10:00:00", entries[1].rawTimestamp)
        assertEquals("第二条", entries[1].content)
    }

    @Test
    fun parse_millisecondPrecisionEntry() {
        val content = "# Appendo\n\n---\n\n## 2026-07-20 09:30:00.123\n\n第一条\n\n---\n"
        val entries = EntryParser.parse(content)
        assertEquals(1, entries.size)
        assertEquals("2026-07-20 09:30:00.123", entries[0].rawTimestamp)
        assertEquals("第一条", entries[0].content)
    }

    @Test
    fun parse_mixedSecondAndMillisecond() {
        val content =
            "# Appendo\n\n---\n\n## 2026-07-20 09:30:00\n\n旧条目\n\n---\n\n## 2026-07-20 10:00:00.456\n\n新条目\n\n---\n"
        val entries = EntryParser.parse(content)
        assertEquals(2, entries.size)
        assertEquals("2026-07-20 09:30:00", entries[0].rawTimestamp)
        assertEquals("2026-07-20 10:00:00.456", entries[1].rawTimestamp)
    }

    // ---------- parse：内容隔离 ZWSP（T-002）----------

    @Test
    fun parse_isolatedTimestampLineInContent_notSplit() {
        val content =
            "# Appendo\n\n---\n\n## 2026-07-20 14:00:00.000\n\n会议纪要：\n${EntryParser.ISOLATION_MARKER}## 2026-07-20 09:30:00\n讨论 roadmap\n\n---\n"
        val entries = EntryParser.parse(content)

        assertEquals(1, entries.size)
        assertEquals("2026-07-20 14:00:00.000", entries[0].rawTimestamp)
        assertTrue("应包含会议纪要", entries[0].content.contains("会议纪要："))
        assertTrue("注入行应被还原（无 ZWSP）", entries[0].content.contains("## 2026-07-20 09:30:00"))
        assertFalse("还原后内容不含 ZWSP", entries[0].content.contains(EntryParser.ISOLATION_MARKER))
        assertTrue(entries[0].content.contains("讨论 roadmap"))
    }

    @Test
    fun parse_restoresZwspIsolatedLine() {
        val content =
            "# Appendo\n\n---\n\n## 2026-07-20 14:00:00.000\n\n${EntryParser.ISOLATION_MARKER}## 2026-07-20 09:30:00\n\n---\n"
        val entries = EntryParser.parse(content)
        assertEquals(1, entries.size)
        assertEquals("## 2026-07-20 09:30:00", entries[0].content.trim())
    }

    @Test
    fun parse_preservesUserOwnedZwspOnNonTimestampLine() {
        val content =
            "# Appendo\n\n---\n\n## 2026-07-20 14:00:00.000\n\n${EntryParser.ISOLATION_MARKER}普通内容\n\n---\n"
        val entries = EntryParser.parse(content)
        assertEquals(1, entries.size)
        assertTrue(entries[0].content.contains("${EntryParser.ISOLATION_MARKER}普通内容"))
    }

    @Test
    fun parse_multipleLeadingZwspAllStripped() {
        val content =
            "# Appendo\n\n---\n\n## 2026-07-20 14:00:00.000\n\n${EntryParser.ISOLATION_MARKER}${EntryParser.ISOLATION_MARKER}## 2026-07-20 09:30:00\n\n---\n"
        val entries = EntryParser.parse(content)
        assertEquals(1, entries.size)
        assertEquals("## 2026-07-20 09:30:00", entries[0].content.trim())
    }

    @Test
    fun parse_emptyAndHeaderOnlyReturnEmpty() {
        assertEquals(emptyList<ParsedEntry>(), EntryParser.parse(""))
        assertEquals(emptyList<ParsedEntry>(), EntryParser.parse("# Appendo\n"))
    }

    // ---------- format / 隔离 / 往返（T-003）----------

    @Test
    fun format_buildsEntryBlockWithTimestamp() {
        val block = EntryParser.format("2026-07-20 09:30:00.123", "内容")
        assertEquals("\n---\n\n## 2026-07-20 09:30:00.123\n\n内容\n\n---\n", block)
    }

    @Test
    fun format_isolatesCollisionLineInContent() {
        val block = EntryParser.format("2026-07-20 09:30:00.123", "## 2026-07-20 08:00:00")
        assertTrue(block.contains("${EntryParser.ISOLATION_MARKER}## 2026-07-20 08:00:00"))
    }

    @Test
    fun format_isolationIdempotent() {
        // 已以 ZWSP 开头的行不再加（幂等）——只应有 1 个 ZWSP
        val content = "${EntryParser.ISOLATION_MARKER}## 2026-07-20 08:00:00"
        val block = EntryParser.format("2026-07-20 09:30:00.123", content)
        val isolatedLine = block.lineSequence().first { it.contains("2026-07-20 08:00:00") }
        assertEquals(1, isolatedLine.count { it == zwsp })
    }

    @Test
    fun format_parse_roundTrip_preservesContent() {
        val original = "第一条\n## 2026-07-20 08:00:00\n第三行"
        val block = EntryParser.format("2026-07-20 09:30:00.123", original)
        val parsed = EntryParser.parse(block)
        assertEquals(1, parsed.size)
        assertEquals(original, parsed[0].content)
    }

    // ---------- nextTimestamp 单调防碰撞（T-003）----------

    @Test
    fun nextTimestamp_emptyContent_returnsNowFormatted() {
        val now = Instant.parse("2026-07-20T09:30:00.000Z")
        assertEquals(EntryParser.formatTimestamp(now), EntryParser.nextTimestamp("", now))
    }

    @Test
    fun nextTimestamp_lastOlderThanNow_returnsNow() {
        val now = Instant.parse("2026-07-20T09:30:00.000Z")
        val last = now.minusMillis(500)
        val content = "# Appendo\n\n---\n\n## ${EntryParser.formatTimestamp(last)}\n\nx\n\n---\n"
        assertEquals(EntryParser.formatTimestamp(now), EntryParser.nextTimestamp(content, now))
    }

    @Test
    fun nextTimestamp_lastEqualsNow_returnsNowPlus1ms() {
        val now = Instant.parse("2026-07-20T09:30:00.000Z")
        val content = "# Appendo\n\n---\n\n## ${EntryParser.formatTimestamp(now)}\n\nx\n\n---\n"
        assertEquals(EntryParser.formatTimestamp(now.plusMillis(1)), EntryParser.nextTimestamp(content, now))
    }

    @Test
    fun nextTimestamp_clockSkew_lastNewerThanNow_returnsLastPlus1ms() {
        val now = Instant.parse("2026-07-20T09:30:00.000Z")
        val last = now.plusMillis(5) // 未来（时钟回拨）
        val content = "# Appendo\n\n---\n\n## ${EntryParser.formatTimestamp(last)}\n\nx\n\n---\n"
        assertEquals(
            EntryParser.formatTimestamp(last.plusMillis(1)),
            EntryParser.nextTimestamp(content, now)
        )
    }

    @Test
    fun nextTimestamp_noValidLastTimestamp_fallsBackToNow() {
        val now = Instant.parse("2026-07-20T09:30:00.000Z")
        val content = "# Appendo\n\n---\n\n## not-a-timestamp\n\nx\n\n---\n"
        assertEquals(EntryParser.formatTimestamp(now), EntryParser.nextTimestamp(content, now))
    }

    // ---------- stripIsolationMarkers（T-003）----------

    @Test
    fun stripIsolationMarkers_stripsZwspTimestampLines() {
        val content = "正常行\n${EntryParser.ISOLATION_MARKER}## 2026-07-20 09:30:00\n另一行"
        val stripped = EntryParser.stripIsolationMarkers(content)
        assertEquals("正常行\n## 2026-07-20 09:30:00\n另一行", stripped)
        assertFalse(stripped.contains(EntryParser.ISOLATION_MARKER))
    }

    @Test
    fun stripIsolationMarkers_preservesUserZwspOnNonTimestamp() {
        val content = "${EntryParser.ISOLATION_MARKER}用户内容"
        assertEquals(content, EntryParser.stripIsolationMarkers(content))
    }

    // ---------- 分隔形态隔离（TD-015）----------

    @Test
    fun isSkippableLine_matchesSeparatorAndHeaderShapes() {
        assertTrue(EntryParser.isSkippableLine("---"))
        assertTrue(EntryParser.isSkippableLine("# Appendo"))
        assertTrue(EntryParser.isSkippableLine("# Appendo 备忘"))
        assertFalse("四个连字符不在形态内", EntryParser.isSkippableLine("----"))
        assertFalse(EntryParser.isSkippableLine("普通内容"))
        assertFalse(EntryParser.isSkippableLine(""))
    }

    @Test
    fun format_isolatesSeparatorAndHeaderShapeLines() {
        val block = EntryParser.format("2026-08-31 09:00:00.000", "---\n# Appendo 备忘\n正常行")
        assertTrue(block.contains("${EntryParser.ISOLATION_MARKER}---"))
        assertTrue(block.contains("${EntryParser.ISOLATION_MARKER}# Appendo 备忘"))
        assertFalse("正常行不应被隔离", block.contains("${EntryParser.ISOLATION_MARKER}正常行"))
    }

    @Test
    fun format_parse_roundTrip_preservesSeparatorShapeLines() {
        val original = "---\n# Appendo 备忘\n## 2026-01-01 10:00:00\n末行"
        val parsed = EntryParser.parse(EntryParser.format("2026-08-31 09:00:00.000", original))
        assertEquals(1, parsed.size)
        assertEquals("往返不应丢行（TD-015）", original, parsed[0].content)
    }

    @Test
    fun format_isolationOfSeparatorShape_idempotent() {
        val once = EntryParser.format("2026-08-31 09:00:00.000", "---")
        val restored = EntryParser.parse(once)[0].content // parse 已还原
        val twice = EntryParser.format("2026-08-31 09:30:00.000", restored) // read-modify-write 再隔离
        val isolatedLines = twice.lineSequence().filter { it.startsWith(EntryParser.ISOLATION_MARKER) }.toList()
        assertEquals("只应有 1 个被隔离行", 1, isolatedLines.size)
        assertEquals("ZWSP 不叠加", 1, isolatedLines[0].count { it == zwsp })
    }

    @Test
    fun buildUpdatedLines_roundTrip_preservesSeparatorShapeLines() {
        // updateEntry 路径：新内容含分隔形态行 → 隔离写入 → 重新 parse 不丢行
        val lines = listOf("# Appendo", "", "---", "", "## 2026-08-31 09:00:00.000", "", "旧内容", "", "---")
        val bounds = EntryParser.findEntryBounds(lines, "2026-08-31 09:00:00.000")!!
        val newContent = "新内容\n---\n# Appendo 备忘"
        val updated = EntryParser.buildUpdatedLines(lines, bounds, newContent).joinToString("\n")
        assertTrue(updated.contains("${EntryParser.ISOLATION_MARKER}---"))
        val reparsed = EntryParser.parse(updated)
        assertEquals(1, reparsed.size)
        assertEquals("编辑不应丢行（TD-015）", newContent, reparsed[0].content)
    }

    @Test
    fun parse_legacyUnisolatedSeparatorLine_stillSkipped() {
        // 旧文件兼容：无 ZWSP 的 --- 行维持现状（跳过），条目数不变
        val content = "# Appendo\n\n---\n\n## 2026-08-31 09:00:00.000\n\n---\n内容\n\n---\n"
        val entries = EntryParser.parse(content)
        assertEquals(1, entries.size)
        assertEquals("内容", entries[0].content.trim())
    }

    @Test
    fun stripIsolationMarkers_stripsSeparatorShapeLines() {
        val content = "正常行\n${EntryParser.ISOLATION_MARKER}---\n${EntryParser.ISOLATION_MARKER}# Appendo 备忘\n另一行"
        val stripped = EntryParser.stripIsolationMarkers(content)
        assertEquals("正常行\n---\n# Appendo 备忘\n另一行", stripped)
    }

    @Test
    fun timestampRegex_countsMixedSecondAndMillisecondLines() { // TD-010：归档计数同源
        val content = "## 2020-01-01 10:00:00\na\n## 2021-01-01 10:00:00.123\nb\n## 2022-01-01 10:00:00.1\nc\n"
        assertEquals(3, EntryParser.getTimestampRegex().findAll(content).count())
    }

    // ---------- 边界算法（T-004）----------

    private val twoEntryLines = listOf(
        "# Appendo", "",
        "---", "",
        "## 2026-07-20 09:30:00.000", "",
        "第一条", "",
        "---", "",
        "## 2026-07-20 10:00:00.000", "",
        "第二条", "",
        "---"
    )

    @Test
    fun findEntryBounds_findsFirstMatch() {
        val bounds = EntryParser.findEntryBounds(twoEntryLines, "2026-07-20 09:30:00.000")
        assertNotNull(bounds)
        val tsIndex = twoEntryLines.indexOf("## 2026-07-20 09:30:00.000")
        assertEquals(tsIndex, bounds!!.first)
        // exclusive end (last+1) 应指向下一个时间戳行
        val nextTsIndex = twoEntryLines.indexOf("## 2026-07-20 10:00:00.000")
        assertEquals(nextTsIndex, bounds.last + 1)
    }

    @Test
    fun findEntryBounds_lastEntryEndsAtFileSize() {
        val bounds = EntryParser.findEntryBounds(twoEntryLines, "2026-07-20 10:00:00.000")
        assertNotNull(bounds)
        val tsIndex = twoEntryLines.indexOf("## 2026-07-20 10:00:00.000")
        assertEquals(tsIndex, bounds!!.first)
        assertEquals(twoEntryLines.size, bounds.last + 1) // exclusive end = size
    }

    @Test
    fun findEntryBounds_notFoundReturnsNull() {
        assertNull(EntryParser.findEntryBounds(twoEntryLines, "1999-01-01 00:00:00.000"))
    }

    @Test
    fun buildDeletedLines_removesTargetKeepsOthers() {
        val bounds = EntryParser.findEntryBounds(twoEntryLines, "2026-07-20 09:30:00.000")!!
        val joined = EntryParser.buildDeletedLines(twoEntryLines, bounds).joinToString("\n")
        assertFalse(joined.contains("第一条"))
        assertTrue(joined.contains("第二条"))
        assertTrue(joined.contains("## 2026-07-20 10:00:00.000"))
    }

    @Test
    fun buildUpdatedLines_replacesContentKeepsTimestamp() {
        val bounds = EntryParser.findEntryBounds(twoEntryLines, "2026-07-20 09:30:00.000")!!
        val joined = EntryParser.buildUpdatedLines(twoEntryLines, bounds, "新内容").joinToString("\n")
        assertTrue(joined.contains("## 2026-07-20 09:30:00.000")) // 时间戳保留
        assertTrue(joined.contains("新内容"))
        assertFalse(joined.contains("第一条"))
        assertTrue(joined.contains("第二条")) // 其他条目不受影响
    }

    @Test
    fun buildUpdatedLines_isolatesNewCollisionContent() {
        val lines = listOf(
            "# Appendo", "", "---", "",
            "## 2026-07-20 09:30:00.000", "", "x", "", "---"
        )
        val bounds = EntryParser.findEntryBounds(lines, "2026-07-20 09:30:00.000")!!
        val joined = EntryParser.buildUpdatedLines(lines, bounds, "## 2026-07-20 08:00:00").joinToString("\n")
        assertTrue(joined.contains("${EntryParser.ISOLATION_MARKER}## 2026-07-20 08:00:00"))
    }

    // ---------- decideRecovery（T-004）----------

    @Test
    fun decideRecovery_noPending_noRecovery() {
        val r = EntryParser.decideRecovery(pendingExists = false, bakExists = true)
        assertFalse(r.useBackup)
        assertFalse(r.shouldShowToast)
    }

    @Test
    fun decideRecovery_pendingWithBak_recoversAndToasts() {
        val r = EntryParser.decideRecovery(pendingExists = true, bakExists = true)
        assertTrue(r.useBackup)
        assertTrue(r.shouldShowToast)
    }

    @Test
    fun decideRecovery_pendingWithoutBak_toastsNoRecovery() {
        val r = EntryParser.decideRecovery(pendingExists = true, bakExists = false)
        assertFalse(r.useBackup)
        assertTrue(r.shouldShowToast)
    }
}
