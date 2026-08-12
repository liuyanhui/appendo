package com.yiyue31.android.appendo.util

/**
 * 提醒相关的纯函数（不碰 Android，全部可在纯 JVM 单测覆盖）。
 * 输入/输出均为 [Map] / [ReminderMeta]，与具体存储解耦。
 */
object ReminderLogic {

    /** 已错过且未触发的提醒 key：effectiveTrigger <= now 且 !fired。 */
    fun findMissed(reminders: Map<String, ReminderMeta>, now: Long): List<String> =
        reminders.filter { (_, m) -> !m.fired && m.effectiveTrigger <= now }.keys.toList()

    /** sidecar 中存在、但 Markdown 条目已不存在的孤儿 key（需 cancel 闹钟 + 删 sidecar）。 */
    fun findOrphans(sidecarKeys: Set<String>, existingEntryTimestamps: Set<String>): Set<String> =
        sidecarKeys - existingEntryTimestamps

    /** 贪睡后的触发时刻。 */
    fun computeSnoozeTrigger(now: Long, snoozeMinutes: Int): Long =
        now + snoozeMinutes.toLong() * 60_000L

    /**
     * 开机/启动补发决策（幂等）：
     * 对每个 !fired 且 effectiveTrigger <= now 的项 —— **先置 fired=true 再补发**，
     * 避免进程重启中途二次发送。未到点的项保持不变（由调用方重注册闹钟）。
     *
     * @return Pair(更新后的 map, 需补发的 key 列表)
     */
    fun advanceToBooted(
        reminders: Map<String, ReminderMeta>,
        now: Long
    ): Pair<Map<String, ReminderMeta>, List<String>> {
        val toFire = mutableListOf<String>()
        val updated = reminders.mapValues { (key, m) ->
            if (!m.fired && m.effectiveTrigger <= now) {
                toFire.add(key)
                m.copy(fired = true)
            } else {
                m
            }
        }
        return updated to toFire.toList()
    }

    /** 下一次触发时刻（保留 HH:mm:ss.SSS；DAILY +1 天，WEEKLY +7 天，用 Calendar 跨夏令时）。 */
    fun nextOccurrence(triggerAt: Long, recurrence: Recurrence): Long =
        java.util.Calendar.getInstance().apply {
            timeInMillis = triggerAt
            add(java.util.Calendar.DATE, if (recurrence == Recurrence.WEEKLY) 7 else 1)
        }.timeInMillis

    /** 把重复提醒推进到 now 之后的下一次触发（关机错过补发后跳过中间次数，不刷屏）。 */
    fun advanceToNextFuture(triggerAt: Long, recurrence: Recurrence, now: Long): Long {
        var t = triggerAt
        while (t <= now) t = nextOccurrence(t, recurrence)
        return t
    }

    /** 开机/启动后单条提醒的处置决策（纯）。 */
    data class BootDecision(val newMeta: ReminderMeta, val fireMissed: Boolean, val rearm: Boolean)

    fun bootDecide(meta: ReminderMeta, now: Long): BootDecision {
        if (meta.fired) return BootDecision(meta, fireMissed = false, rearm = false)
        val eff = meta.effectiveTrigger
        return if (eff <= now) {
            if (meta.recurrence == Recurrence.NONE) {
                BootDecision(meta.copy(fired = true), fireMissed = true, rearm = false)
            } else {
                val next = advanceToNextFuture(eff, meta.recurrence, now)
                BootDecision(meta.copy(triggerAt = next, snoozedUntil = 0), fireMissed = true, rearm = true)
            }
        } else {
            BootDecision(meta, fireMissed = false, rearm = true)
        }
    }
}
