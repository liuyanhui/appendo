package com.yiyue31.android.appendo.reminder

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.yiyue31.android.appendo.util.ReminderMeta
import com.yiyue31.android.appendo.util.ReminderMetaCodec

/**
 * 提醒元数据 sidecar（SharedPreferences per-key 存储）。
 *
 * - key = "reminder_<记录时间戳>"；value = [ReminderMetaCodec] 编码串。
 * - 单例（[get]）：Receiver 与 UI 共享同一 [reminders] State，
 *   这样提醒触发（Receiver 改 sidecar）能立即刷新 UI 徽标，**不依赖** Markdown 文件轮询。
 *
 * 线程安全：SharedPreferences 自身线程安全；每条写各自原子；读全量用 getAll 快照。
 */
class ReminderStore private constructor(private val appContext: Context) {

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _reminders = mutableStateOf(loadAll())
    val reminders: State<Map<String, ReminderMeta>> = _reminders

    private fun storageKey(ts: String) = "$KEY_PREFIX$ts"

    private fun loadAll(): Map<String, ReminderMeta> {
        val map = mutableMapOf<String, ReminderMeta>()
        for ((k, v) in prefs.all) {
            if (!k.startsWith(KEY_PREFIX)) continue
            val ts = k.removePrefix(KEY_PREFIX)
            val encoded = v as? String ?: continue
            ReminderMetaCodec.decode(encoded)?.let { map[ts] = it }
        }
        return map
    }

    private fun refresh() {
        _reminders.value = loadAll()
    }

    fun get(ts: String): ReminderMeta? = _reminders.value[ts]

    /** 是否有未触发的提醒（徽标用）。 */
    fun hasUnfired(ts: String): Boolean = _reminders.value[ts]?.let { !it.fired } ?: false

    /** 全部提醒的记录时间戳 key（对账/清空用）。 */
    fun allKeys(): Set<String> = _reminders.value.keys

    /** 设置/覆盖一条提醒（写整对象，不复用旧字段——避免残留 fired=true）。 */
    fun set(ts: String, meta: ReminderMeta) {
        prefs.edit().putString(storageKey(ts), ReminderMetaCodec.encode(meta)).apply()
        refresh()
    }

    fun update(ts: String, transform: (ReminderMeta) -> ReminderMeta) {
        val current = loadAll()[ts] ?: return
        prefs.edit().putString(storageKey(ts), ReminderMetaCodec.encode(transform(current))).apply()
        refresh()
    }

    fun remove(ts: String) {
        prefs.edit().remove(storageKey(ts)).apply()
        refresh()
    }

    /** 清空所有提醒（clear()/归档联动用）。 */
    fun removeAll() {
        val ed = prefs.edit()
        for (k in prefs.all.keys) if (k.startsWith(KEY_PREFIX)) ed.remove(k)
        ed.apply()
        refresh()
    }

    companion object {
        private const val PREFS_NAME = "appendo_reminders"
        private const val KEY_PREFIX = "reminder_"

        @Volatile
        private var instance: ReminderStore? = null

        fun get(context: Context): ReminderStore =
            instance ?: synchronized(this) {
                instance ?: ReminderStore(context.applicationContext).also { instance = it }
            }
    }
}
