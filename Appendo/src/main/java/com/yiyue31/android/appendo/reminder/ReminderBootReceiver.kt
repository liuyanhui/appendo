package com.yiyue31.android.appendo.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yiyue31.android.appendo.util.ReminderLogic

/**
 * 开机（及 app 更新后）重注册闹钟 + 补发已错过的提醒。
 *
 * - sidecar 在 CE 加密存储，BOOT_COMPLETED 于"首次解锁后"送达 → 这里读 sidecar 安全。
 * - 补发幂等：[ReminderLogic.advanceToBooted] 先置 fired=true 再返回，进程重启中途不会二次补发。
 * - 补发为"已过期"通知；未到点的重注册 setAlarmClock。
 */
class ReminderBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val store = ReminderStore.get(context)
        val now = System.currentTimeMillis()
        for ((ts, meta) in store.reminders.value) {
            val d = ReminderLogic.bootDecide(meta, now)
            if (d.newMeta != meta) store.set(ts, d.newMeta)
            if (d.fireMissed) {
                val content = findEntryContent(context, ts)
                if (content != null) {
                    NotificationHelper.show(context, ts, content, missed = true)
                } else {
                    AlarmScheduler.cancel(context, ts)
                    store.remove(ts)
                    continue
                }
            }
            if (d.rearm) AlarmScheduler.schedule(context, ts, d.newMeta.effectiveTrigger)
        }
    }
}
