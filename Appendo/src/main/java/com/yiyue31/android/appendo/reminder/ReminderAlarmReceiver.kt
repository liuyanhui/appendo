package com.yiyue31.android.appendo.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yiyue31.android.appendo.util.Recurrence
import com.yiyue31.android.appendo.util.ReminderLogic

/**
 * 到点接收器：发通知 / 处理贪睡 / 处理完成。
 * 显式组件、manifest exported=false、无 intent-filter（仅由 PendingIntent 触发，不可被外部伪造）。
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val ts = intent.getStringExtra(ReminderIntents.EXTRA_ENTRY_TIMESTAMP) ?: return
        when (intent.action) {
            ReminderIntents.ACTION_SNOOZE -> handleSnooze(context, ts, intent)
            ReminderIntents.ACTION_COMPLETE -> handleComplete(context, ts)
            else -> handleFire(context, ts)
        }
    }

    /**
     * 到点触发：发通知；重复提醒自动排下一次（fired 保持 false），一次性标记 fired。
     * 三态（设计 §20.1 修订，2026-11-13 评审）：NotFound（全库可读且无）→取消＋删 sidecar；
     * Unknown（有文件不可读且未命中）→不通知、不取消、不删（读失败≠不存在——条目可能就在
     * 不可读文件中，v1.4「miss 即清」会在瞬时 I/O 故障时毁掉存活条目的提醒）；循环提醒照常
     * 排下一次（系列不因单次读失败断链），一次性保留待下次 rescheduleAll 补发自愈（app 启动/开机）。
     */
    private fun handleFire(context: Context, ts: String) {
        if (ts == ReminderIntents.TEST_REMINDER_TS) {
            NotificationHelper.showTestNotification(context)
            return
        }
        val store = ReminderStore.get(context)
        val meta = store.get(ts)
        when (val outcome = EntryLocator.locateOutcome(context, ts)) {
            is LocateOutcome.Found -> {
                NotificationHelper.show(context, ts, outcome.content, missed = false)
                if (meta != null && meta.recurrence != Recurrence.NONE) {
                    val next = ReminderLogic.nextOccurrence(meta.effectiveTrigger, meta.recurrence)
                    AlarmScheduler.schedule(context, ts, next)
                    store.set(ts, meta.copy(triggerAt = next, fired = false, snoozedUntil = 0))
                } else {
                    store.update(ts) { it.copy(fired = true) }
                }
            }
            LocateOutcome.NotFound -> {
                AlarmScheduler.cancel(context, ts)
                store.remove(ts)
            }
            LocateOutcome.Unknown -> {
                if (meta != null && meta.recurrence != Recurrence.NONE) {
                    val next = ReminderLogic.nextOccurrence(meta.effectiveTrigger, meta.recurrence)
                    AlarmScheduler.schedule(context, ts, next)
                    store.set(ts, meta.copy(triggerAt = next, fired = false, snoozedUntil = 0))
                }
            }
        }
    }

    /** 贪睡：重排闹钟到 now+minutes，fired 复位（徽标重现），撤掉当前通知。 */
    private fun handleSnooze(context: Context, ts: String, intent: Intent) {
        val minutes = intent.getIntExtra(ReminderIntents.EXTRA_SNOOZE_MIN, 10)
        val next = ReminderLogic.computeSnoozeTrigger(System.currentTimeMillis(), minutes)
        AlarmScheduler.schedule(context, ts, next)
        ReminderStore.get(context).update(ts) { it.copy(fired = false, snoozedUntil = next) }
        NotificationHelper.cancel(context, ts)
    }

    /** 完成：重复提醒=排下一次（保留系列）；一次性=取消+删除。 */
    private fun handleComplete(context: Context, ts: String) {
        val store = ReminderStore.get(context)
        val meta = store.get(ts)
        if (meta != null && meta.recurrence != Recurrence.NONE) {
            val next = ReminderLogic.nextOccurrence(meta.effectiveTrigger, meta.recurrence)
            AlarmScheduler.schedule(context, ts, next)
            store.set(ts, meta.copy(triggerAt = next, fired = false, snoozedUntil = 0))
        } else {
            AlarmScheduler.cancel(context, ts)
            store.remove(ts)
        }
        NotificationHelper.cancel(context, ts)
    }
}
