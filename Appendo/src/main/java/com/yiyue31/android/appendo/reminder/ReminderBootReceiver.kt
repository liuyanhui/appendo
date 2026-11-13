package com.yiyue31.android.appendo.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yiyue31.android.appendo.util.ReminderLogic

/**
 * 重注册闹钟并补发已错过的提醒（幂等）。三个入口共用（TD-014）：
 * 开机（BOOT_COMPLETED）、应用覆盖更新（MY_PACKAGE_REPLACED）、应用启动兜底（MainScreen，
 * 覆盖 force-stop 后闹钟被清、又等不到任何广播的场景）。
 *
 * - sidecar 在 CE 加密存储，BOOT_COMPLETED 于"首次解锁后"送达 → 这里读 sidecar 安全。
 * - 补发幂等：[ReminderLogic.bootDecide] 先置 fired=true 再补发，进程重启中途不会二次发送。
 * - 补发为"已过期"通知；未到点的重注册 setAlarmClock。
 */
fun rescheduleAll(context: Context) {
    val store = ReminderStore.get(context)
    val now = System.currentTimeMillis()
    // 单遍 ts→(位置, 内容) 索引（T-22，需求 95(b) 性能前提）——替代逐条全量重读；
    // Receiver 受广播限时，一遍读全库后查表分发
    val index = EntryLocator.buildIndex(context)
    for ((ts, meta) in store.reminders.value) {
        val d = ReminderLogic.bootDecide(meta, now)
        if (d.newMeta != meta) store.set(ts, d.newMeta)
        if (d.fireMissed) {
            val content = index[ts]?.second
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

/** 开机 / 应用更新广播入口：转调 [rescheduleAll]。 */
class ReminderBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        rescheduleAll(context)
    }
}
