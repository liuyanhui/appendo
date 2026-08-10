package com.yiyue31.android.appendo.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract

/**
 * 拉起系统日历的"新建事件"编辑器，预填标题/描述（A 方案：交由用户在日历中设时间与告警）。
 *
 * 不需要任何权限：写日历由被拉起的日历 App 用其自身权限完成；appendo 仅负责"预填 + 跳转"。
 *
 * 实现要点（评审采纳）：
 * - 用 ACTION_INSERT + [CalendarContract.Events.CONTENT_URI]（content://，规避 FileUriExposedException）；
 * - 不用 resolveActivity()（Android 11+ 包可见性会误判为"无日历"），改用 try/catch [ActivityNotFoundException]；
 * - 不加 FLAG_ACTIVITY_NEW_TASK（调用方传入的是 Activity context，与现有 shareContent/openFile 一致）；
 * - 预填 [CalendarContract.EXTRA_EVENT_BEGIN_TIME] 为"现在 + 1 小时"，给用户一个合理默认起点；
 * - 无法预填"告警/提醒"——CalendarContract 无此标准 extra，需用户在日历内手动加。
 *
 * 失败提示上抛给调用方（避免 util 反向依赖 ui 层的 showToast）。
 */
object CalendarLauncher {

    /** 默认事件开始时间偏移：现在 + 1 小时。 */
    private const val DEFAULT_START_OFFSET_MS = 60L * 60 * 1000

    /**
     * 拉起日历新建事件编辑器。
     *
     * @param context Activity context（来自 Compose 的 LocalContext）。
     * @param entry 已映射好的标题/描述。
     * @param beginTimeMillis 事件开始时间（epoch 毫秒）；<=0 表示"现在 + 1 小时"。
     * @return true 成功拉起；false 无可接住的日历 App（调用方应给出 Toast 提示）。
     */
    fun launch(
        context: Context,
        entry: CalendarEntryMapper.CalendarEntry,
        beginTimeMillis: Long = 0L
    ): Boolean {
        val begin = if (beginTimeMillis > 0) beginTimeMillis
        else System.currentTimeMillis() + DEFAULT_START_OFFSET_MS
        val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
            putExtra(CalendarContract.Events.TITLE, entry.title)
            putExtra(CalendarContract.Events.DESCRIPTION, entry.description)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}
