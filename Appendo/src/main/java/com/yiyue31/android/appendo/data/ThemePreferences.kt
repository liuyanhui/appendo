package com.yiyue31.android.appendo.data

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.yiyue31.android.appendo.BuildConfig
import com.yiyue31.android.appendo.R
import com.yiyue31.android.appendo.ui.theme.ThemeMode

/**
 * 主题偏好（v1.3，需求 48/49）：`appendo` SP 文件的 `theme_mode` 键。
 *
 * - 三态：`system` / `light` / `dark`；缺省与非法值回退 [ThemeMode.SYSTEM]
 * - 仅在用户显式选择时写入（含显式选回"跟随系统"）；无初始化写入路径
 * - [read] 为同步轻量操作（`appendo` SP 在应用启动路径已加载、内存缓存命中），
 *   可在 Activity#onCreate 的 super 之前调用
 */
object ThemePreferences {

    private const val TAG = "ThemePreferences"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val VALUE_SYSTEM = "system"
    private const val VALUE_LIGHT = "light"
    private const val VALUE_DARK = "dark"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FileRepository.APPENDO_PREFS, Context.MODE_PRIVATE)

    fun read(context: Context): ThemeMode {
        val stored = prefs(context).getString(KEY_THEME_MODE, null)
        if (BuildConfig.DEBUG) android.util.Log.d(TAG, "read stored=$stored")
        return when (stored) {
            VALUE_LIGHT -> ThemeMode.LIGHT
            VALUE_DARK -> ThemeMode.DARK
            VALUE_SYSTEM, null -> ThemeMode.SYSTEM
            else -> ThemeMode.SYSTEM.also {
                android.util.Log.w(TAG, "Unknown theme_mode value: $stored, fallback to SYSTEM")
            }
        }
    }

    fun write(context: Context, mode: ThemeMode) {
        val value = when (mode) {
            ThemeMode.SYSTEM -> VALUE_SYSTEM
            ThemeMode.LIGHT -> VALUE_LIGHT
            ThemeMode.DARK -> VALUE_DARK
        }
        // commit()（同步落盘）而非 apply()：设置写入必须先于进程可能死亡完成（需求 49）。
        // 真机实证（2026-09-17，realme/ColorOS）：apply() 的异步落盘被系统延迟冻结，
        // 写后数分钟乃至进程死亡都不落盘；commit() 即时落盘。与项目其他 apply() 路径
        // 行为不同（彼处经受住历史真机验证），此处以实证为准。
        val ok = prefs(context).edit().putString(KEY_THEME_MODE, value).commit()
        if (BuildConfig.DEBUG) android.util.Log.d(TAG, "write value=$value commitOk=$ok")
    }

    /**
     * 冷启动窗口背景：按偏好选择 XML 主题变体（需求 56）。
     * 须在 Activity#onCreate 的 super 之前调用；SYSTEM 不动，交给 values-night 资源系统。
     */
    fun applyWindowTheme(activity: Activity) {
        when (read(activity)) {
            ThemeMode.LIGHT -> activity.setTheme(R.style.Theme_Appendo)
            ThemeMode.DARK -> activity.setTheme(R.style.Theme_Appendo_Dark)
            ThemeMode.SYSTEM -> Unit
        }
    }
}
