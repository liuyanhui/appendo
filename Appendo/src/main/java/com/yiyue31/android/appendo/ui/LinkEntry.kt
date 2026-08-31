package com.yiyue31.android.appendo.ui

import com.yiyue31.android.appendo.util.EntryParser
import com.yiyue31.android.appendo.util.ParsedEntry

/**
 * UI 层条目模型（v1.1）。由 [ParsedEntry]（util 纯数据）映射而来。
 *
 * - [timestamp]：完整时间戳字符串（新条目毫秒级，旧条目秒级），作为条目唯一 key
 *   （删除/更新/去重均用它；LazyColumn key 用 "${timestamp}_$index" 复合键，容忍归档恢复等
 *   场景产生的重复时间戳）。**构造签名保持 2 参**，向后兼容。
 * - [timestampDisplay]：**派生属性**（剥离毫秒 `yyyy-MM-dd HH:mm:ss`）。UI 渲染只显这个，
 *   用户永远看不到 `.SSS`（specs 36 衍生，回应评审 B1）。
 * - [content]：还原后的用户原文（ZWSP 已剥离）。
 */
data class LinkEntry(
    val timestamp: String,
    val content: String
) {
    /** 显示用时间戳（剥离毫秒）。UI 渲染只显这个。 */
    val timestampDisplay: String = EntryParser.displayTimestamp(timestamp)

    companion object {
        /** 从 util 层 [ParsedEntry] 映射为 UI 层 LinkEntry。 */
        fun from(parsed: ParsedEntry): LinkEntry = LinkEntry(parsed.rawTimestamp, parsed.content)
    }
}
