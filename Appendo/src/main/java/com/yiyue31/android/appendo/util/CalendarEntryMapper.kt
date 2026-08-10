package com.yiyue31.android.appendo.util

/**
 * 把一条 appendo 记录内容映射为"添加到日历"所需的标题/描述（纯函数，可单测）。
 *
 * 设计（评审采纳）：
 * - 标题取内容第一行（多行/纯链接友好），并经 [EntryParser.stripIsolationMarkers] 剥离 ZWSP 隔离标记；
 * - 完整原文（剥离 ZWSP）放入事件描述；
 * - 标题超长时截断，保证日历标题可读；
 * - 标题加 [TITLE_PREFIX] 前缀，便于在日历中辨识来自 appendo 的事件。
 *
 * 不含任何 Android 依赖，全部可在纯 JVM 单测中覆盖（与 [EntryParser] 同构）。
 */
object CalendarEntryMapper {

    /** 标题最大长度（含前缀）。日历标题过长会被系统截断，主动控制更稳。 */
    private const val TITLE_MAX_CHARS = 80

    /** 标题前缀：醒目 emoji，便于在日历中辨识来自 appendo 的事件。 */
    private const val TITLE_PREFIX = "🔵 "

    /** 映射结果。 */
    data class CalendarEntry(val title: String, val description: String)

    /**
     * @param rawContent 记录原始内容（可能多行、含 ZWSP 隔离标记）。
     * @return 日历事件标题（首行 + 前缀，已截断）与描述（完整原文，已剥离 ZWSP）。
     */
    fun map(rawContent: String): CalendarEntry {
        val clean = EntryParser.stripIsolationMarkers(rawContent).trim()
        val firstLine = clean.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        val baseTitle = if (firstLine.isNotEmpty()) firstLine else clean
        return CalendarEntry(
            title = truncate(TITLE_PREFIX + baseTitle, TITLE_MAX_CHARS),
            description = clean
        )
    }

    private fun truncate(s: String, max: Int): String =
        if (s.length <= max) s else s.substring(0, max - 1) + "…"
}
