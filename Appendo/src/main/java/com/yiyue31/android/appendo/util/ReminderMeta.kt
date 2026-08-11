package com.yiyue31.android.appendo.util

/** 重复类型（Phase 1 仅 NONE；DAILY/WEEKLY 留给 Phase 2）。 */
enum class Recurrence { NONE, DAILY, WEEKLY }

/**
 * 单条提醒的元数据（存 sidecar，不进 Markdown）。
 *
 * @param triggerAt    触发时刻（epoch 毫秒，绝对瞬时，与时区无关）
 * @param fired        是否已触发（开机补发幂等：补发前置 true）
 * @param snoozedUntil 贪睡后的触发时刻；0 表示未贪睡
 * @param recurrence   重复类型
 */
data class ReminderMeta(
    val triggerAt: Long,
    val fired: Boolean,
    val snoozedUntil: Long,
    val recurrence: Recurrence = Recurrence.NONE
) {
    /** 实际生效的触发时刻：贪睡过用 [snoozedUntil]，否则 [triggerAt]。 */
    val effectiveTrigger: Long
        get() = if (snoozedUntil > 0) snoozedUntil else triggerAt
}
