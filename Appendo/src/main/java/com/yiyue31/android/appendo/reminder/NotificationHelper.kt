package com.yiyue31.android.appendo.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.yiyue31.android.appendo.MainActivity
import com.yiyue31.android.appendo.R
import com.yiyue31.android.appendo.util.ReminderText

/**
 * 提醒通知构建：新建 `IMPORTANCE_HIGH`「提醒」渠道（不复用未用的 write_feedback）。
 * 通知含：点按深链回记录、贪睡（10 分 / 1 小时）、完成。
 */
object NotificationHelper {
    const val CHANNEL_ID = "reminder"
    private const val CHANNEL_NAME = "提醒"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "appendo 记录提醒"
                    enableVibration(true)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    /**
     * 发出提醒通知。
     * @param ts      记录时间戳（key）
     * @param content 记录原文
     * @param missed  是否为开机补发的"已过期"提醒
     */
    fun show(context: Context, ts: String, content: String, missed: Boolean) {
        ensureChannel(context)
        val titleBase = ReminderText.title(content)
        // ⏰ 放进标题文字（非 largeIcon）：弹出横幅/抽屉/状态栏提示均可见
        val title = if (missed) "⏰ 已过期：$titleBase" else "⏰ $titleBase"

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ReminderIntents.EXTRA_ENTRY_TIMESTAMP, ts)
        }
        val tapPi = PendingIntent.getActivity(
            context, ReminderIntents.requestCodeFor(ts), tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF2196F3.toInt())
            .setContentTitle(title)
            .setContentText(titleBase)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(tapPi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(snoozeAction(context, ts, 10, "稍后 10 分"))
            .addAction(snoozeAction(context, ts, 60, "稍后 1 小时"))
            .addAction(completeAction(context, ts))

        NotificationManagerCompat.from(context).notify(ReminderIntents.requestCodeFor(ts), builder.build())
    }

    fun cancel(context: Context, ts: String) {
        NotificationManagerCompat.from(context).cancel(ReminderIntents.requestCodeFor(ts))
    }

    /** 自检测试通知（验证 setAlarmClock 在本机能触发）。 */
    fun showTestNotification(context: Context) {
        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF2196F3.toInt())
            .setContentTitle("✅ 测试提醒已响")
            .setContentText("你的手机支持 appendo 本地提醒")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_TEST, builder.build())
    }

    private const val NOTIFICATION_ID_TEST = 79123

    private fun snoozeAction(context: Context, ts: String, minutes: Int, label: String): NotificationCompat.Action {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderIntents.ACTION_SNOOZE
            putExtra(ReminderIntents.EXTRA_ENTRY_TIMESTAMP, ts)
            putExtra(ReminderIntents.EXTRA_SNOOZE_MIN, minutes)
        }
        val pi = PendingIntent.getBroadcast(
            context, ReminderIntents.requestCodeForSnooze(ts, minutes), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Action.Builder(0, label, pi).build()
    }

    private fun completeAction(context: Context, ts: String): NotificationCompat.Action {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderIntents.ACTION_COMPLETE
            putExtra(ReminderIntents.EXTRA_ENTRY_TIMESTAMP, ts)
        }
        val pi = PendingIntent.getBroadcast(
            context, ReminderIntents.requestCodeForComplete(ts), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Action.Builder(0, "完成", pi).build()
    }
}
