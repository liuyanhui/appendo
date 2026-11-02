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

    // ---------- 旧数据一次性迁移（2026-09-01 决策③）----------

    @Test
    fun migrateLegacyIsolation_isolatesSandwichedSeparatorContentLine() {
        val content = "# Appendo\n\n---\n\n## 2026-08-30 09:00:00.000\n\n前言\n---\n后记\n\n---\n"
        val migrated = EntryParser.migrateLegacyIsolation(content)
        assertNotNull(migrated)
        assertTrue(migrated!!.contains("${EntryParser.ISOLATION_MARKER}---"))
        val entries = EntryParser.parse(migrated)
        assertEquals("迁移后该条目不再丢行", 1, entries.size)
        assertEquals("前言\n---\n后记", entries[0].content)
    }

    @Test
    fun migrateLegacyIsolation_isolatesMidFileHeaderShapeLine() {
        val content = "# Appendo\n\n---\n\n## 2026-08-30 09:00:00.000\n\n正文\n# Appendo 备忘\n结尾\n\n---\n"
        val migrated = EntryParser.migrateLegacyIsolation(content)
        assertNotNull(migrated)
        assertTrue(migrated!!.contains("${EntryParser.ISOLATION_MARKER}# Appendo 备忘"))
        assertEquals("正文\n# Appendo 备忘\n结尾", EntryParser.parse(migrated)[0].content)
    }

    @Test
    fun migrateLegacyIsolation_keepsStructuralSeparatorsUntouched() {
        // 真实模板结构（format 拼接产生的条目间分隔线）不是迁移对象
        val content = "# Appendo\n\n---\n\n## 2026-08-30 09:00:00.000\n\n正文\n\n---\n\n---\n\n## 2026-08-30 10:00:00.000\n\n第二条\n\n---\n"
        assertNull(EntryParser.migrateLegacyIsolation(content))
    }

    @Test
    fun migrateLegacyIsolation_idempotent() {
        val once = EntryParser.migrateLegacyIsolation(
            "# Appendo\n\n---\n\n## 2026-08-30 09:00:00.000\n\n前言\n---\n后记\n\n---\n"
        )!!
        assertNull("迁移产物再跑应返回 null", EntryParser.migrateLegacyIsolation(once))
    }

    @Test
    fun migrateLegacyIsolation_preservesTrailingNewline() {
        val migrated = EntryParser.migrateLegacyIsolation(
            "# Appendo\n\n---\n\n## 2026-08-30 09:00:00.000\n\n前言\n---\n后记\n\n---\n"
        )!!
        assertTrue("尾部换行不得丢失", migrated.endsWith("\n"))
    }

    @Test
    fun migrateLegacyIsolation_newFormatFile_returnsNull() {
        val content = "# Appendo\n\n---\n\n## 2026-08-30 09:00:00.000\n\n${EntryParser.ISOLATION_MARKER}---\n后记\n\n---\n"
        assertNull(EntryParser.migrateLegacyIsolation(content))
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
    fun findEntryBounds_duplicateTimestamp_hitsFirst() { // TD-020②：重复时间戳命中第一条
        val lines = listOf(
            "# Appendo", "", "---", "",
            "## 2026-08-31 09:00:00.000", "", "第一条", "", "---", "",
            "## 2026-08-31 09:00:00.000", "", "第二条", "", "---"
        )
        val idxs = lines.indices.filter { lines[it] == "## 2026-08-31 09:00:00.000" }
        assertEquals(2, idxs.size)
        val bounds = EntryParser.findEntryBounds(lines, "2026-08-31 09:00:00.000")
        assertNotNull(bounds)
        assertEquals("命中第一条的时间戳行", idxs[0], bounds!!.first)
        assertEquals("区间止于第二条的时间戳行", idxs[1], bounds.last + 1)
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
    }

    @Test
    fun decideRecovery_pendingWithBak_recovers() {
        val r = EntryParser.decideRecovery(pendingExists = true, bakExists = true)
        assertTrue(r.useBackup)
    }

    @Test
    fun decideRecovery_pendingWithoutBak_noRecovery() { // TD-018：仅清标记、不提示，与实现一致
        val r = EntryParser.decideRecovery(pendingExists = true, bakExists = false)
        assertFalse(r.useBackup)
    }

    // ---------- countEntries 公开纯函数（T-01 钉死：宽松正则计数，TD-010 防复发）----------

    @Test
    fun countEntries_pureSecondPrecision() {
        // 纯秒级（旧格式）3 条 → 3
        val content = "## 2026-01-01 09:00:00\na\n\n---\n\n" +
            "## 2026-01-02 09:00:00\nb\n\n---\n\n" +
            "## 2026-01-03 09:00:00\nc\n\n---\n"
        assertEquals(3, EntryParser.countEntries(content))
    }

    @Test
    fun countEntries_pureMillisecondVariants() {
        // 纯毫秒级 3 条 → 3：含 1/2/3 位毫秒变体（.1 / .12 / .123）
        val content = "## 2026-01-01 09:00:00.1\na\n" +
            "## 2026-01-02 09:00:00.12\nb\n" +
            "## 2026-01-03 09:00:00.123\nc\n"
        assertEquals(3, EntryParser.countEntries(content))
    }

    @Test
    fun countEntries_mixedSecondAndMillisecond() {
        // 秒/毫混合 5 条 → 5（TD-010：两种形态都数得到，不得收窄为单形态）
        val content = "## 2026-01-01 09:00:00\na\n" +
            "## 2026-01-02 09:00:00.123\nb\n" +
            "## 2026-01-03 09:00:00\nc\n" +
            "## 2026-01-04 09:00:00.5\nd\n" +
            "## 2026-01-05 09:00:00.25\ne\n"
        assertEquals(5, EntryParser.countEntries(content))
    }

    @Test
    fun countEntries_emptyAndStructuralOnly() {
        // 边界：空串 → 0；只有文件头/分隔线（无时间戳行）→ 0
        assertEquals(0, EntryParser.countEntries(""))
        assertEquals(0, EntryParser.countEntries("# Appendo\n\n---\n"))
    }

    @Test
    fun countEntries_ignoresZwspPrefixedIsolatedLine() {
        // 隔离行不计入：内容行恰好形如时间戳但带 ZWSP 前缀（写侧隔离产物）——
        // 宽松正则按行首 ^## 匹配，带前缀行不是边界；本例仅 1 条真实条目 → 1
        val content = "# Appendo\n\n---\n\n## 2026-07-20 14:00:00.000\n\n" +
            EntryParser.ISOLATION_MARKER + "## 2026-07-20 09:30:00\n\n---\n"
        assertEquals(1, EntryParser.countEntries(content))
    }

    // ---------- scanEntriesWithSpans 行区间（T-01 结构钉死：T-02 批量删除消费的区间语义）----------

    @Test
    fun scanEntriesWithSpans_spansCoverTimestampToNextTimestamp() {
        // 区间语义：startLine=条目时间戳行；endLineInclusive=下一时间戳行前一行（含条目间分隔线）或末行
        val content =
            "# Appendo\n\n---\n\n## 2026-07-20 09:30:00\n\n第一条\n\n---\n\n## 2026-07-20 10:00:00.456\n\n第二条\n\n---\n"
        val lines = content.lines()
        val scanned = EntryParser.scanEntriesWithSpans(content)

        assertEquals("条目集与 parse 一致（parse 是薄包装）", EntryParser.parse(content), scanned.map { it.entry })
        assertEquals(2, scanned.size)

        val firstTsIdx = lines.indexOf("## 2026-07-20 09:30:00")
        val secondTsIdx = lines.indexOf("## 2026-07-20 10:00:00.456")
        assertEquals(firstTsIdx, scanned[0].startLine)
        assertEquals("首条区间含条目间分隔线，止于第二条时间戳行前一行", secondTsIdx - 1, scanned[0].endLineInclusive)
        assertEquals(secondTsIdx, scanned[1].startLine)
        assertEquals("末条区间到文件末行", lines.size - 1, scanned[1].endLineInclusive)
    }

    @Test
    fun scanEntriesWithSpans_emptyEntryProducesNothing() {
        // 空内容区域不产出条目、无区间（与 parse「丢弃无内容条目」语义一致，设计 §6.3：
        // 不产出 key、不可被删除、原样保留）
        val content = "## 2026-07-20 09:30:00\n\n## 2026-07-20 10:00:00\n\n内容\n"
        val scanned = EntryParser.scanEntriesWithSpans(content)

        assertEquals(1, scanned.size)
        assertEquals("2026-07-20 10:00:00", scanned[0].entry.rawTimestamp)
        assertEquals("内容", scanned[0].entry.content) // trim 后内容
    }

    // ---------- buildContentWithoutEntries（T-02：设计 §10.2 第二行 ①~⑧）----------

    @Test
    fun buildContentWithoutEntries_sameTimestampDifferentContent_deletesOnlySelected() {
        // ① 同秒碰撞：同 ts 不同 content 两条只删所选（timestamp|content 组合键区分）
        val content =
            "# Appendo\n\n---\n\n## 2026-07-20 09:30:00\n\n甲\n\n---\n\n## 2026-07-20 09:30:00\n\n乙\n\n---\n"
        val entries = EntryParser.parse(content)
        assertEquals(2, entries.size)

        val (result, deleted) = EntryParser.buildContentWithoutEntries(
            content, setOf(EntryKey.of(entries[0])) // (ts, "甲")
        )
        assertEquals(1, deleted)
        assertEquals(
            "# Appendo\n\n---\n\n## 2026-07-20 09:30:00\n\n乙\n\n---\n",
            result
        )
    }

    @Test
    fun buildContentWithoutEntries_duplicateKey_deletesAllCountsEach() {
        // ② 同 key 双条完全重复条目：全部删除，实删计数含每一条（计 2）
        val content =
            "# Appendo\n\n---\n\n## 2026-07-20 09:30:00\n\n重复\n\n---\n\n## 2026-07-20 09:30:00\n\n重复\n\n---\n\n## 2026-07-20 10:00:00\n\n其他\n\n---\n"
        val (result, deleted) = EntryParser.buildContentWithoutEntries(
            content, setOf(EntryKey("2026-07-20 09:30:00", "重复"))
        )
        assertEquals("同 key 双条全删，各计 1", 2, deleted)
        assertEquals("# Appendo\n\n---\n\n## 2026-07-20 10:00:00\n\n其他\n\n---\n", result)
        assertEquals("非全删不归一化", 1, EntryParser.parse(result).size)
    }

    @Test
    fun buildContentWithoutEntries_firstLast_leadingSeparatorAndHeader() {
        // ③ 首条/末条/前导 --- 连带/文件头保留（紧凑布局：分隔线紧贴时间戳行，触发连带判定）
        val content = "# Appendo\n\n---\n## 2026-07-20 09:30:00\n首条\n---\n## 2026-07-20 10:00:00\n末条\n"

        // 删首条：前一行恰为分隔线 → 连带删除；文件头保留
        val (delFirst, n1) = EntryParser.buildContentWithoutEntries(
            content, setOf(EntryKey("2026-07-20 09:30:00", "首条"))
        )
        assertEquals(1, n1)
        assertEquals("# Appendo\n\n## 2026-07-20 10:00:00\n末条\n", delFirst)

        // 删末条：连带其前导分隔线；末条区间含末空行 → 结果无尾换行（lines() 语义，与既有 deleteEntry 同构）
        val (delLast, n2) = EntryParser.buildContentWithoutEntries(
            content, setOf(EntryKey("2026-07-20 10:00:00", "末条"))
        )
        assertEquals(1, n2)
        assertEquals("# Appendo\n\n---\n## 2026-07-20 09:30:00\n首条", delLast)
    }

    @Test
    fun buildContentWithoutEntries_isolatedShapeLines_preservedOrDeletedWithEntry() {
        // ④ 内容含 ---、# Appendo、时间戳形态行（ZWSP 隔离往返）：删别的条目时原样保留含 ZWSP；
        //    删含它们的条目时整段消失
        val zwsp = EntryParser.ISOLATION_MARKER
        val content = "# Appendo\n\n---\n\n## 2026-07-20 09:30:00.000\n\n" +
            "$zwsp---\n$zwsp# Appendo 备忘\n$zwsp## 2026-07-20 08:00:00\n正文\n\n---\n\n" +
            "## 2026-07-20 10:00:00.000\n\nsecond\n\n---\n"
        val entries = EntryParser.parse(content)
        assertEquals(2, entries.size)
        assertEquals("---\n# Appendo 备忘\n## 2026-07-20 08:00:00\n正文", entries[0].content)

        // 删第二条：首条含隔离行整段原样保留（含 ZWSP，逐字节）
        val (keepFirst, n1) = EntryParser.buildContentWithoutEntries(
            content, setOf(EntryKey.of(entries[1]))
        )
        assertEquals(1, n1)
        assertEquals(
            "# Appendo\n\n---\n\n## 2026-07-20 09:30:00.000\n\n" +
                "$zwsp---\n$zwsp# Appendo 备忘\n$zwsp## 2026-07-20 08:00:00\n正文\n\n---\n",
            keepFirst
        )

        // 删首条：隔离行随条目整段消失，无残留
        val (delFirst, n2) = EntryParser.buildContentWithoutEntries(
            content, setOf(EntryKey.of(entries[0]))
        )
        assertEquals(1, n2)
        assertEquals("# Appendo\n\n---\n\n## 2026-07-20 10:00:00.000\n\nsecond\n\n---\n", delFirst)
    }

    @Test
    fun buildContentWithoutEntries_emptyKeysOrNoHit_returnsOriginalVerbatim() {
        // ⑤ keys 空 / 无命中 → 原文逐字节不变（含 CRLF 也不归一）、返回 0
        val content = "# Appendo\r\n\r\n---\r\n\r\n## 2026-07-20 09:30:00\r\n\r\n内容\r\n\r\n---\r\n"

        val (emptyKeys, deleted1) = EntryParser.buildContentWithoutEntries(content, emptySet())
        assertEquals(0, deleted1)
        assertEquals("keys 空：原文逐字节不变", content, emptyKeys)

        val (noHit, deleted2) = EntryParser.buildContentWithoutEntries(
            content, setOf(EntryKey("1999-01-01 00:00:00", "不存在"))
        )
        assertEquals(0, deleted2)
        assertEquals("无命中：原文逐字节不变", content, noHit)
    }

    @Test
    fun buildContentWithoutEntries_crlfInput_normalizedToLfLikeDeleteEntryPath() {
        // ⑥-a CRLF 输入：命中删除后按 lines()→join("\n") 重构——CRLF 归一为 LF
        //    （与既有 deleteEntry 写路径同构，即与 parse 同源同行为）；尾换行经末空行元素保留
        val content =
            "# Appendo\r\n\r\n---\r\n\r\n## 2026-07-20 09:30:00\r\n\r\nfirst\r\n\r\n---\r\n\r\n## 2026-07-20 10:00:00\r\n\r\nsecond\r\n\r\n---\r\n"
        val (result, deleted) = EntryParser.buildContentWithoutEntries(
            content, setOf(EntryKey("2026-07-20 10:00:00", "second"))
        )
        assertEquals(1, deleted)
        assertEquals("# Appendo\n\n---\n\n## 2026-07-20 09:30:00\n\nfirst\n\n---\n", result)
    }

    @Test
    fun buildContentWithoutEntries_noTrailingNewline_resultHasNoTrailingNewline() {
        // ⑥-b 无尾换行：尾换行随 lines() 语义（无末空行元素 → 结果亦无尾换行）
        val content =
            "# Appendo\n\n---\n\n## 2026-07-20 09:30:00\n\nfirst\n\n---\n\n## 2026-07-20 10:00:00\n\nsecond"
        val (result, deleted) = EntryParser.buildContentWithoutEntries(
            content, setOf(EntryKey("2026-07-20 09:30:00", "first"))
        )
        assertEquals(1, deleted)
        assertEquals("# Appendo\n\n---\n\n## 2026-07-20 10:00:00\n\nsecond", result)
    }

    @Test
    fun buildContentWithoutEntries_legacyUnisolatedLines_matchByKeyDeleteBySpan() {
        // ⑥-c TD-015 修复前旧数据：内容区间内遗留未隔离 --- / # Appendo 行——
        //     匹配看 key（parse 跳过语义，key 的 content 不含遗留行）、删除看扫描行区间（遗留行随条目消失）
        val content =
            "# Appendo\n\n---\n\n## 2026-08-31 09:00:00.000\n\n前言\n---\n# Appendo 备忘\n后记\n\n---\n\n## 2026-08-31 10:00:00.000\n\n正常\n\n---\n"
        val entries = EntryParser.parse(content)
        assertEquals("parse 跳过遗留行（key 不含它们）", "前言\n后记", entries[0].content)

        val (result, deleted) = EntryParser.buildContentWithoutEntries(
            content, setOf(EntryKey.of(entries[0]))
        )
        assertEquals(1, deleted)
        assertEquals(
            "# Appendo\n\n---\n\n## 2026-08-31 10:00:00.000\n\n正常\n\n---\n",
            result
        )
        assertFalse("遗留 --- 行随条目消失", result.contains("前言"))
        assertFalse("遗留 # Appendo 行随条目消失", result.contains("# Appendo 备忘"))
    }

    @Test
    fun buildContentWithoutEntries_deleteAll_normalizesToFileHeader() {
        // ⑦ 全删归一化：输出恰为 FILE_HEADER（89(m)：主文件恰为 `# Appendo\n`——常量含尾换行），
        //    无残留 --- / 空行
        val content =
            "# Appendo\n\n---\n\n## 2026-07-20 09:30:00\n\n甲\n\n---\n\n## 2026-07-20 10:00:00.456\n\n乙\n\n---\n"
        val keys = EntryParser.parse(content).map { EntryKey.of(it) }.toSet()
        val (result, deleted) = EntryParser.buildContentWithoutEntries(content, keys)
        assertEquals(2, deleted)
        assertEquals(MarkdownFormatter.FILE_HEADER, result)
        assertEquals("# Appendo\n", result)
        assertFalse(result.contains("---"))
    }

    @Test
    fun buildContentWithoutEntries_emptyContentRegion_preservedVerbatim() {
        // ⑧ 空内容区域（时间戳行后无内容）：不产出 key、不可被删、原样保留（删的是别的条目）
        val content =
            "## 2026-07-20 09:30:00\n\n## 2026-07-20 10:00:00\n\n第二条\n\n---\n\n## 2026-07-20 11:00:00\n\n第三条\n"
        val (result, deleted) = EntryParser.buildContentWithoutEntries(
            content, setOf(EntryKey("2026-07-20 10:00:00", "第二条"))
        )
        assertEquals(1, deleted)
        assertEquals("## 2026-07-20 09:30:00\n\n## 2026-07-20 11:00:00\n\n第三条\n", result)
        assertEquals("空区域仍 parse 不到条目", 1, EntryParser.parse(result).size)
    }
}
