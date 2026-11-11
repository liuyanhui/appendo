package com.yiyue31.android.appendo.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.Toast
import com.yiyue31.android.appendo.data.ThemePreferences
import com.yiyue31.android.appendo.ui.theme.resolveDarkTheme

/**
 * Show a toast with centered text at the bottom of screen
 *
 * 反色药丸（2026-11-11 换机验证视觉裁定）：随应用实际主题（ThemePreferences 偏好＋系统夜间，
 * 与 Compose 层同解析规则）反转配色——浅色主题深底白字、深色主题浅底深字；近不透明（弃原
 * #CC000000 半透明——深色主题下与纯黑背景融于一体不显眼），尺寸略放大。主题不随文字内容变色。
 */
fun showToast(context: Context, message: String) {
    // T-18 验收仪表（2026-11-06）：toast 字面落 logcat（tag=APPENDO_TOAST），
    // 真机验收以日志为事实源（TD-024）；仅 debug 观测，无行为影响
    android.util.Log.i("APPENDO_TOAST", message)

    val isSystemNight =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    val dark = resolveDarkTheme(ThemePreferences.read(context), isSystemNight)

    val toast = Toast.makeText(context, "", Toast.LENGTH_SHORT)

    // Create a custom TextView for the toast with proper styling
    val textView = android.widget.TextView(context).apply {
        text = message
        setTextColor(if (dark) Color.parseColor("#FF1C1B1F") else Color.WHITE)
        textSize = 16f
        gravity = android.view.Gravity.CENTER

        // Add background with rounded corners
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(
                if (dark) Color.parseColor("#F2EDEFF4") else Color.parseColor("#F21C1B22")
            )
            cornerRadius = 28f
        }
        background = drawable

        setPadding(56, 28, 56, 28)
    }

    toast.view = textView
    // Don't set gravity - let it use default (bottom center)
    toast.show()
}
