package com.yiyue31.android.appendo.util

/**
 * 单条 [ReminderMeta] 与其存储字符串之间的编解码（纯函数，可单测）。
 *
 * 存储格式（定长 4 段，`|` 分隔）：`triggerAt|fired|snoozedUntil|recurrence`
 * - 各段均无 `|`（long/bool/enum 名），round-trip 安全。
 * - sidecar 采用 SharedPreferences per-key 存储（key=`reminder_<timestamp>`），
 *   每条独立原子写，避免大 JSON blob 的读改写竞态。
 */
object ReminderMetaCodec {
    private const val SEP = "|"

    fun encode(m: ReminderMeta): String =
        "${m.triggerAt}$SEP${m.fired}$SEP${m.snoozedUntil}$SEP${m.recurrence.name}"

    fun decode(s: String): ReminderMeta? {
        val p = s.split(SEP)
        if (p.size != 4) return null
        val triggerAt = p[0].toLongOrNull() ?: return null
        val fired = p[1].toBooleanStrictOrNull() ?: return null
        val snoozedUntil = p[2].toLongOrNull() ?: return null
        val recurrence = runCatching { Recurrence.valueOf(p[3]) }.getOrNull() ?: return null
        return ReminderMeta(triggerAt, fired, snoozedUntil, recurrence)
    }
}
