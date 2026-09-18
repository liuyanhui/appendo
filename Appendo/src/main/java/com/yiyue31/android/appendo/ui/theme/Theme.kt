package com.yiyue31.android.appendo.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 主题基础设施（v1.3，需求 48~52）。
 *
 * - [ThemeMode] 三态：跟随系统（默认）/ 浅色 / 深色
 * - [AppendoTheme] 按 mode 选浅/深 ColorScheme 并包一层 MaterialTheme；
 *   迁移后全部 UI 经 `MaterialTheme.colorScheme.*` / [successColor] 取色（原 `AppColors` 已删除）
 * - 浅色 = v1.2.2 现状延续（D1 决策：浅色不动）；深色 = M3 亮容器 + 深 onXxx 文字
 * - success 无 M3 槽位，经 [LocalSuccessColors] 扩展提供
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * 模式 → 是否深色。[isSystemInDark] 参数化以便 JVM 单测（SYSTEM 分支由 Compose 环境注入实际值）。
 */
fun resolveDarkTheme(mode: ThemeMode, isSystemInDark: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** success 扩展色（深色亮容器 + 深文字，与 primary/error 同模式）。 */
data class SuccessColors(val success: Color, val onSuccess: Color)

val LocalSuccessColors = compositionLocalOf { LightSuccess }

/** 访问器：与 `MaterialTheme.colorScheme` 同风格。 */
val MaterialTheme.successColor: Color
    @Composable get() = LocalSuccessColors.current.success

val MaterialTheme.onSuccessColor: Color
    @Composable get() = LocalSuccessColors.current.onSuccess

/**
 * 色板常量（CODINGRULES：颜色集中管理；与 res/values/colors.xml 的窗口色保持同步）。
 * 深色对比度经核算：容器色对 M3 深色 surface（#1C1B1F）约 8:1~10:1，onXxx 对容器 ≥4.5:1（WCAG AA）。
 */
private object Palette {
    // 浅色（现状延续，D1 决策：不动；onXxx 沿用 M3 浅色默认白——TD-023 已记录组件级 3:1 的取舍）
    val lightPrimary = Color(0xFF2196F3)
    val lightError = Color(0xFFEF5350)
    val lightSuccess = Color(0xFF4CAF50)

    // 深色（提亮变体 + 深 onXxx）
    val darkPrimary = Color(0xFF90CAF9)
    val darkOnPrimary = Color(0xFF0D2E4E)
    val darkError = Color(0xFFEF9A9A)
    val darkOnError = Color(0xFF4A0C10)
    val darkSuccess = Color(0xFFA5D6A7)
    val darkOnSuccess = Color(0xFF1B3D1F)
}

/** 双配色（v1.3 起对主题选择对话框的"预览按钮"开放引用——颜色唯一定义处仍是本文件）。 */
internal val AppendoLightScheme: ColorScheme = lightColorScheme(
    primary = Palette.lightPrimary,
    error = Palette.lightError
)

internal val AppendoDarkScheme: ColorScheme = darkColorScheme(
    primary = Palette.darkPrimary,
    onPrimary = Palette.darkOnPrimary,
    error = Palette.darkError,
    onError = Palette.darkOnError
)

private val LightSuccess = SuccessColors(Palette.lightSuccess, Color.White)
private val DarkSuccess = SuccessColors(Palette.darkSuccess, Palette.darkOnSuccess)

/**
 * 应用主题。[mode] 由 MainActivity 持有的 Compose State 提供，手动切换纯重组生效（无 Activity 重建）。
 */
@Composable
fun AppendoTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = resolveDarkTheme(mode, isSystemInDarkTheme())
    MaterialTheme(
        colorScheme = if (dark) AppendoDarkScheme else AppendoLightScheme
    ) {
        CompositionLocalProvider(
            LocalSuccessColors provides if (dark) DarkSuccess else LightSuccess
        ) {
            StatusBarAppearance(dark)
            content()
        }
    }
}

/** 状态栏底色（与 res/values/colors.xml 同值；targetSdk 34 下 statusBarColor 尚可用）。 */
private val LightStatusBar = Color(0xFFFFFFFF)
private val DarkStatusBar = Color(0xFF1C1B1F)

/**
 * 状态栏图标外观随主题同步（需求 51：浅色主题深色图标、深色主题浅色图标）。
 * XML 主题中的显式声明作冷启动兜底；此处覆盖手动切换（无重建）路径。幂等，重建路径重复设置无害。
 */
@Composable
private fun StatusBarAppearance(dark: Boolean) {
    val view = LocalView.current
    DisposableEffect(dark) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            window.statusBarColor = (if (dark) DarkStatusBar else LightStatusBar).toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !dark
        }
        onDispose { }
    }
}
