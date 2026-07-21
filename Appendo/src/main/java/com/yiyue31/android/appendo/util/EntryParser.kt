package com.yiyue31.android.appendo.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

/**
 * 条目知识的唯一收敛点（v1.1 数据完整性增强）。
 *
 * 职责：时间戳行判定、解析（含 ZWSP 还原）、格式化（含 ZWSP 隔离）、单调时间戳、
 * 出口剥离、边界算法、SAF 恢复判定。
 *
 * 设计要点：
 * - **纯函数、不碰 I/O**，全部可在纯 JVM 单测中覆盖。
 * - **宽松时间戳正则**：同时匹配旧秒级与新毫秒级（specs 37）。
 * - **内容隔离**：用户内容中恰好匹配时间戳标记格式的行，写入时（[format]）加
 *   [ISOLATION_MARKER] 前缀；读取时（[parse]）该行因前缀不匹配边界、被当作内容，
 *   并在收集时剥离前缀还原原文。用户自有的、非时间戳行的 ZWSP 不受影响（specs 38）。
 * - **单调防碰撞**：[nextTimestamp] 保证文件内时间戳严格递增唯一（仅 append() now 路径）。
 * - **边界算法不依赖唯一性**：[findEntryBounds] 按首条匹配 + 区间，保留"找首条"语义。
 *
 * 时间戳时区沿用原 SimpleDateFormat 的系统默认时区（[timestampZone]），保证新旧条目格式一致。
 */
object EntryParser {

    /** 内容隔离标记（零宽空格 U+200B）：仅加在"恰好匹配时间戳标记格式"的内容行首。 */
    const val ISOLATION_MARKER = "​"

    private const val TIMESTAMP_LINE_PREFIX = "## "

    // 单行宽松正则：同时匹配秒级（无毫秒）与毫秒级（.S / .SS / .SSS）。
    private val TIMESTAMP_LINE_REGEX =
        Regex("^## \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}(\\.\\d{1,3})?$")

    /** 时间戳时区（系统默认，与原 SimpleDateFormat 行为一致）。internal 供测试构造期望值。 */
    internal val timestampZone: ZoneId = ZoneId.systemDefault()

    /** 写入用，固定 3 位毫秒。internal 供测试构造期望值。 */
    internal val timestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    /** 读取用，兼容 0-3 位毫秒（旧秒级 + 新毫秒级 + 外部 1/2 位）。 */
    private val timestampReadFormatter: DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd HH:mm:ss")
        .appendFraction(ChronoField.MILLI_OF_SECOND, 0, 3, true)
        .toFormatter(Locale.US)

    /** 多行内容上使用的时间戳正则（MULTILINE），供 count() 等 findAll 场景。 */
    fun getTimestampRegex(): Regex =
        Regex("^## \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}(\\.\\d{1,3})?$", RegexOption.MULTILINE)

    /** 单行是否为时间戳标题行 `## YYYY-MM-DD HH:mm:ss[.SSS]`。带 [ISOLATION_MARKER] 前缀返回 false（视为内容）。 */
    fun isTimestampLine(line: String): Boolean = TIMESTAMP_LINE_REGEX.matches(line)

    // ==================== 读侧：parse ====================

    /**
     * 解析 Markdown 内容为条目列表。
     *
     * - 跳过文件头 `# Appendo` 与分隔线 `---`。
     * - 以时间戳行作为条目边界。
     * - 带 [ISOLATION_MARKER] 前缀的行不当边界（视为内容）；收集时若去掉所有前导
     *   [ISOLATION_MARKER] 后恰为时间戳行，则剥离前缀还原原文；用户自有的非时间戳行 ZWSP 保留。
     */
    fun parse(content: String): List<ParsedEntry> {
        val entries = mutableListOf<ParsedEntry>()
        var currentTimestamp = ""
        var currentContent = StringBuilder()

        for (line in content.lines()) {
            when {
                isTimestampLine(line) -> {
                    if (currentTimestamp.isNotEmpty() && currentContent.isNotEmpty()) {
                        entries.add(ParsedEntry(currentTimestamp, currentContent.toString().trim()))
                    }
                    currentTimestamp = line.removePrefix(TIMESTAMP_LINE_PREFIX).trim()
                    currentContent = StringBuilder()
                }
                line.startsWith("# Appendo") || line == "---" -> {
                    // 跳过文件头与分隔线
                }
                else -> {
                    if (currentContent.isNotEmpty()) currentContent.append("\n")
                    currentContent.append(restoreLine(line))
                }
            }
        }
        if (currentTimestamp.isNotEmpty() && currentContent.isNotEmpty()) {
            entries.add(ParsedEntry(currentTimestamp, currentContent.toString().trim()))
        }
        return entries
    }

    /** 还原被隔离的内容行：去所有前导 [ISOLATION_MARKER] 后若为时间戳行则剥离；否则原样保留用户 ZWSP。 */
    private fun restoreLine(line: String): String {
        if (!line.startsWith(ISOLATION_MARKER)) return line
        val stripped = line.dropWhile { it == ISOLATION_MARKER.first() }
        return if (isTimestampLine(stripped)) stripped else line
    }

    // ==================== 写侧：format / nextTimestamp / strip（T-003）====================

    /** 把 [Instant] 格式化为时间戳字符串（系统默认时区，固定 3 位毫秒）。 */
    internal fun formatTimestamp(instant: Instant): String =
        timestampFormatter.format(LocalDateTime.ofInstant(instant, timestampZone))

    /** 把时间戳字符串解析为 [Instant]；不匹配返回 null。 */
    internal fun parseTimestampToInstant(ts: String): Instant? =
        runCatching {
            LocalDateTime.parse(ts, timestampReadFormatter).atZone(timestampZone).toInstant()
        }.getOrNull()

    /** 把 raw 时间戳转为显示用（剥离毫秒段）：`yyyy-MM-dd HH:mm:ss`。秒级原样返回。 */
    fun displayTimestamp(rawTimestamp: String): String {
        val dot = rawTimestamp.indexOf('.')
        return if (dot >= 0) rawTimestamp.substring(0, dot) else rawTimestamp
    }

    /**
     * 构造一条 Markdown 条目块（写侧）。对内容做 ZWSP 隔离（仅冲突行），保留原格式。
     * [timestamp] 由调用方提供（append 传 [nextTimestamp]，appendEntry 传归档原时间戳）。
     */
    fun format(timestamp: String, content: String): String {
        val isolated = isolateContent(content)
        return "\n---\n\n## $timestamp\n\n$isolated\n\n---\n"
    }

    private fun isolateContent(content: String): String =
        content.lines().joinToString("\n") { isolateLine(it) }

    private fun isolateLine(line: String): String {
        // 幂等：已以 ZWSP 开头则不再加（防 read-modify-write 重复隔离）
        if (line.startsWith(ISOLATION_MARKER)) return line
        return if (isTimestampLine(line)) ISOLATION_MARKER + line else line
    }

    /**
     * 计算下一条目的时间戳，保证文件内严格递增唯一（单调防碰撞，仅 append() now 路径）。
     * 反向扫描最后一条时间戳；新 = max(now, last+1ms)；parse 失败/无时间戳则 fallback 为 now。
     */
    fun nextTimestamp(content: String, now: Instant = Instant.now()): String {
        val lastTs = content.lines().asReversed()
            .firstOrNull { isTimestampLine(it) }
            ?.removePrefix(TIMESTAMP_LINE_PREFIX)
            ?.trim()
        val base: Instant = lastTs?.let { parseTimestampToInstant(it) } ?: now.minusMillis(1)
        val next = if (now.isAfter(base)) now else base.plusMillis(1)
        return formatTimestamp(next)
    }

    /**
     * 剥离内容隔离标记，供"离开应用"的出口（复制/分享）使用。
     * 仅剥离"行首 ZWSP + 紧跟时间戳模式"的行；用户自有非时间戳 ZWSP 与正常内容保留。
     */
    fun stripIsolationMarkers(content: String): String =
        content.lines().joinToString("\n") { restoreLine(it) }

    // ==================== 边界算法 + 恢复判定（T-004）====================

    /**
     * 定位匹配 [timestamp] 的条目在 [lines] 中的行范围。
     * 返回 [IntRange]（闭区间 [first, last]），其 exclusive end = last + 1。
     * 按首条匹配 + 到下一时间戳行（或文件末）作为区间；未找到返回 null。
     * 不依赖唯一性——保留"找首条"语义，唯一性是优化而非正确性依赖。
     */
    fun findEntryBounds(lines: List<String>, timestamp: String): IntRange? {
        var startIndex = -1
        for (i in lines.indices) {
            val line = lines[i]
            if (isTimestampLine(line)) {
                if (startIndex >= 0) {
                    return startIndex until i // 下一时间戳行 = 当前条目结束
                }
                if (line.removePrefix(TIMESTAMP_LINE_PREFIX).trim() == timestamp) {
                    startIndex = i
                }
            }
        }
        return if (startIndex >= 0) startIndex until lines.size else null
    }

    /**
     * 删除 [bounds] 指定条目后的新行列表。若条目前一行是 `---` 分隔线则一并删除（与原 deleteEntry 一致）。
     */
    fun buildDeletedLines(lines: List<String>, bounds: IntRange): List<String> {
        val endIndex = bounds.last + 1 // exclusive
        val deleteFrom =
            if (bounds.first > 0 && lines[bounds.first - 1] == "---") bounds.first - 1 else bounds.first
        return lines.toMutableList().also { it.subList(deleteFrom, endIndex).clear() }
    }

    /**
     * 替换 [bounds] 指定条目的内容为 [newContent]（自动做 ZWSP 隔离，与 [format] 对称），保留原时间戳行。
     */
    fun buildUpdatedLines(lines: List<String>, bounds: IntRange, newContent: String): List<String> {
        val endIndex = bounds.last + 1 // exclusive
        val isolatedLines = isolateContent(newContent).lines()
        val result = mutableListOf<String>()
        result.addAll(lines.subList(0, bounds.first))
        result.add(lines[bounds.first]) // 时间戳行不变
        result.add("")
        result.addAll(isolatedLines)
        if (endIndex < lines.size) {
            result.add("")
            result.addAll(lines.subList(endIndex, lines.size))
        }
        return result
    }

    /** SAF 恢复判定结果。 */
    data class RecoveryAction(
        val useBackup: Boolean,
        val shouldShowToast: Boolean,
        val reason: String
    )

    /**
     * SAF 软恢复判定（纯函数）：.pending 存在表示上次写入未确认、主文件可能脏 → 用 .bak 恢复。
     * - 无 .pending：正常，不恢复、不提示。
     * - 有 .pending + 有 .bak：从 .bak 恢复 + 提示。
     * - 有 .pending + 无 .bak：无可恢复内容，仅提示。
     */
    fun decideRecovery(pendingExists: Boolean, bakExists: Boolean): RecoveryAction = when {
        !pendingExists -> RecoveryAction(useBackup = false, shouldShowToast = false, reason = "正常")
        bakExists -> RecoveryAction(useBackup = true, shouldShowToast = true, reason = "上次写入未完成，从备份恢复")
        else -> RecoveryAction(useBackup = false, shouldShowToast = true, reason = "上次写入未完成，但无备份可用")
    }
}
