package com.yiyue31.android.appendo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * remindersToCancel（topic-move 设计 §8.2 ②③）单测：成功回调内 K 重算＋同 ts 连坐防护。
 * 纯函数（三集合入参），纯 JVM 可直接构造，无需 fixture（沿 MultiSelectTest 先例）。
 */
class TopicMoveDialogsTest {

    // --- ② K 重算：以执行时点 sidecar 快照为准（确认框 K 仅预览值）---

    @Test
    fun testRemindersToCancel_allSelectedUnfiredAndRemoved_allCancelled() {
        val toCancel = remindersToCancel(
            selectedTs = setOf("2026-11-01 10:00:00.000", "2026-11-01 11:00:00.000"),
            unfiredReminderTs = setOf("2026-11-01 10:00:00.000", "2026-11-01 11:00:00.000"),
            remainingSourceTs = emptySet()
        )
        assertEquals(
            "所选 ts 提醒未触发且已全部从源移走 → 全部取消",
            setOf("2026-11-01 10:00:00.000", "2026-11-01 11:00:00.000"),
            toCancel
        )
    }

    @Test
    fun testRemindersToCancel_reminderGoneDuringDialog_recomputed() {
        // 确认框停留期间提醒被取消/触发 → 执行时点重算后不在未触发集 → 不出现于结果（§8.2 ②）
        val toCancel = remindersToCancel(
            selectedTs = setOf("t1", "t2"),
            unfiredReminderTs = setOf("t1"), // t2 的提醒已不在（被取消或已触发）
            remainingSourceTs = emptySet()
        )
        assertEquals("K 按执行时点重算：只取消仍存在的未触发提醒", setOf("t1"), toCancel)
    }

    // --- ③ 同 ts 连坐防护：该 ts 条目未全部从源移走则不取消该提醒 ---

    @Test
    fun testRemindersToCancel_sameTsUnselectedEntryRemaining_reminderKept() {
        // 同秒碰撞：同 ts 的未选条目留在源 → 该 ts 提醒不取消（仍属于留在主列表的那条，R8）
        val toCancel = remindersToCancel(
            selectedTs = setOf("t1", "t2"),
            unfiredReminderTs = setOf("t1", "t2"),
            remainingSourceTs = setOf("t2")
        )
        assertEquals("同 ts 有未选条目留在源 → 只取消 t1，t2 提醒保留", setOf("t1"), toCancel)
    }

    @Test
    fun testRemindersToCancel_sourceReadFailed_conservativelyEmpty() {
        // 源读失败（remainingSourceTs=null）→ 保守不取消，遗漏交由既有孤儿对账兜底（§8.2 ③）
        val toCancel = remindersToCancel(
            selectedTs = setOf("t1"),
            unfiredReminderTs = setOf("t1"),
            remainingSourceTs = null
        )
        assertEquals("源读失败应保守返回空集", emptySet<String>(), toCancel)
    }

    // --- 边界 ---

    @Test
    fun testRemindersToCancel_noUnfiredIntersection_empty() {
        val toCancel = remindersToCancel(
            selectedTs = setOf("t1", "t2"),
            unfiredReminderTs = emptySet(),
            remainingSourceTs = emptySet()
        )
        assertEquals("无未触发提醒交集 → 空集", emptySet<String>(), toCancel)
    }

    @Test
    fun testRemindersToCancel_unselectedTsReminderUntouched() {
        // 未选条目的提醒不在 selectedTs → 移入不触达（对账归既有孤儿机制，非本流程职责）
        val toCancel = remindersToCancel(
            selectedTs = setOf("t1"),
            unfiredReminderTs = setOf("t1", "t2"),
            remainingSourceTs = emptySet()
        )
        assertEquals("只取消所选 ts 的提醒，未选条目提醒不动", setOf("t1"), toCancel)
    }
}
