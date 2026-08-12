package com.yiyue31.android.appendo.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yiyue31.android.appendo.data.FileRepository
import com.yiyue31.android.appendo.util.EntryParser
import com.yiyue31.android.appendo.util.MarkdownFileFactory
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

    /** 到点触发：发通知；重复提醒自动排下一次（fired 保持 false），一次性标记 fired。 */
    private fun handleFire(context: Context, ts: String) {
        if (ts == ReminderIntents.TEST_REMINDER_TS) {
            NotificationHelper.showTestNotification(context)
            return
        }
        val store = ReminderStore.get(context)
        val meta = store.get(ts)
        val content = findEntryContent(context, ts)
        if (content == null) {
            AlarmScheduler.cancel(context, ts)
            store.remove(ts)
            return
        }
        NotificationHelper.show(context, ts, content, missed = false)
        if (meta != null && meta.recurrence != Recurrence.NONE) {
            val next = ReminderLogic.nextOccurrence(meta.effectiveTrigger, meta.recurrence)
            AlarmScheduler.schedule(context, ts, next)
            store.set(ts, meta.copy(triggerAt = next, fired = false, snoozedUntil = 0))
        } else {
            store.update(ts) { it.copy(fired = true) }
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

/** 按 timestamp 从 Markdown 文件读回条目原文（供通知正文/补发用）。找不到返回 null。 */
internal fun findEntryContent(context: Context, ts: String): String? = try {
    val repo = FileRepository(context)
    val mdFile = MarkdownFileFactory.create(context, repo.isUsingSAF(), repo.getFileUri(), repo.getDefaultFile())
    EntryParser.parse(mdFile.readAll()).firstOrNull { it.rawTimestamp == ts }?.content
} catch (e: Exception) {
    null
}
