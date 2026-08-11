package com.yiyue31.android.appendo.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * 用 [AlarmManager.setAlarmClock] 注册/取消闹钟。
 *
 * 选 setAlarmClock 而非 setExactAndAllowWhileIdle（评审 H1）：
 * - 免 `SCHEDULE_EXACT_ALARM` 权限与设置跳转；
 * - 免 Doze 9 分钟限速（贪睡/密集提醒不被合并延迟）；
 * - "用户闹钟"语义，OEM 高优先级保留，国产 ROM 被杀概率更低。
 *
 * PendingIntent 一律 FLAG_IMMUTABLE（API 31+ 必需）；显式组件指向 [ReminderAlarmReceiver]。
 */
object AlarmScheduler {

    private fun firePendingIntent(context: Context, ts: String): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderIntents.ACTION_FIRE
            putExtra(ReminderIntents.EXTRA_ENTRY_TIMESTAMP, ts)
        }
        return PendingIntent.getBroadcast(
            context,
            ReminderIntents.requestCodeFor(ts),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun schedule(context: Context, ts: String, triggerAt: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, null), firePendingIntent(context, ts))
    }

    fun cancel(context: Context, ts: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(firePendingIntent(context, ts))
    }
}
