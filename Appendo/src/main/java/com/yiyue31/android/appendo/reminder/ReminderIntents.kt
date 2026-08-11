package com.yiyue31.android.appendo.reminder

/** 提醒相关的 Intent action / extra / requestCode 常量与计算。 */
object ReminderIntents {
    const val EXTRA_ENTRY_TIMESTAMP = "appendo.reminder.entry_ts"
    const val EXTRA_SNOOZE_MIN = "appendo.reminder.snooze_min"

    /** 自检测试提醒用的保留 timestamp（不对应真实条目）。 */
    const val TEST_REMINDER_TS = "__appendo_test__"

    const val ACTION_FIRE = "appendo.reminder.FIRE"
    const val ACTION_SNOOZE = "appendo.reminder.SNOOZE"
    const val ACTION_COMPLETE = "appendo.reminder.COMPLETE"

    /** 闹钟/通知 id：每条记录唯一（ts.hashCode；个人级冲突概率极低）。 */
    fun requestCodeFor(ts: String): Int = ts.hashCode()

    /** 贪睡 action 的 requestCode：按 (ts, 分钟) 区分（Intent extras 不参与 PI 身份，故用 requestCode 区分）。 */
    fun requestCodeForSnooze(ts: String, minutes: Int): Int = ts.hashCode() xor (minutes * 31)

    /** 完成 action 的 requestCode（与 fire 错开）。 */
    fun requestCodeForComplete(ts: String): Int = ts.hashCode() xor 0x43
}
