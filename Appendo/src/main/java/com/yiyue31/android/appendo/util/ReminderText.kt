package com.yiyue31.android.appendo.util

/**
 * 通知文本构建（纯）：标题取内容首行 + 剥 ZWSP，超长截断。
 * 通知来源图标已是 appendo，故标题不加前缀。
 */
object ReminderText {
    private const val TITLE_MAX = 80

    fun title(content: String): String {
        val clean = EntryParser.stripIsolationMarkers(content).trim()
        val firstLine = clean.lineSequence().firstOrNull { it.isNotBlank() } ?: ""
        val base = if (firstLine.isNotEmpty()) firstLine else clean
        return if (base.length <= TITLE_MAX) base else base.substring(0, TITLE_MAX - 1) + "…"
    }

    /** 把触发时刻格式化为简短标签：今天/明天 HH:mm，否则 MM-dd HH:mm。 */
    fun timeLabel(triggerAt: Long, now: Long = System.currentTimeMillis()): String {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val t = java.time.Instant.ofEpochMilli(triggerAt).atZone(zone)
        val hm = t.toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        return when (t.toLocalDate()) {
            today -> "今天 $hm"
            today.plusDays(1) -> "明天 $hm"
            else -> t.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))
        }
    }
}
