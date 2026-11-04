package com.yiyue31.android.appendo.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import java.text.BreakIterator
import kotlin.math.abs

/**
 * 主题首字标纯函数集（设计修订二 §15.1「首字标规则」定稿：90＋U6＋52；测试义务 §17.1 第一行）。
 *
 * - 零写死色值（P7 字面满足／R17 受控例外）：色板运行时自 colorScheme 双参（primary, surface）
 *   派生；派生是纯函数 ⇒ 同名同色、重启不变（90）。「同名同色」在同主题模式内成立，
 *   明暗切换整体换带有意为之（R17 披露）。
 * - 全 JVM 可测：[Color] 为 compose 纯 Kotlin 值类，RGB↔HSL 纯 Kotlin 手写（不依赖系统图形库）。
 */

/** 色板大小，兼作 [badgeIndex] 散列模数（8 色板；不同名残余撞色允许，靠主题名区分，90）。 */
private const val BADGE_PALETTE_SIZE = 8

/** 相邻派生色的色相步进（360°÷8＝45°，i=0..7 全环覆盖，§15.1）。 */
private const val BADGE_HUE_STEP_DEGREES = 45f

/** 饱和度钳制区间下界（§15.1 定稿）。 */
private const val BADGE_SATURATION_MIN = 0.42f

/** 饱和度钳制区间上界（§15.1 定稿）。 */
private const val BADGE_SATURATION_MAX = 0.78f

/** 明度固定带·浅色主题（§15.1；判深浅不传模式枚举，用 surface 相对亮度，T-5 定稿）。 */
private const val BADGE_LIGHTNESS_LIGHT_THEME = 0.36f

/** 明度固定带·深色主题（§15.1）。 */
private const val BADGE_LIGHTNESS_DARK_THEME = 0.44f

/** surface 相对亮度 ≥ 0.5 视为浅色主题（明度带选择依据）。 */
private const val SURFACE_LIGHT_LUMINANCE_THRESHOLD = 0.5f

/** WCAG 对比度公式的相对亮度偏置：(L1+0.05)/(L2+0.05)。 */
private const val CONTRAST_LUMINANCE_OFFSET = 0.05f

/** 空名防御占位首字（§15.1 首字取法：空名正常不可达，防御返回「？」）。 */
private const val EMPTY_NAME_PLACEHOLDER = "？"

/**
 * 首字标色板派生（§15.1 定稿签名 `deriveBadgePalette(primary, surface)` 双参纯函数）：
 * 自 primary 取基础色相，按 45°×i（i=0..7）旋转派生 8 色；饱和度取 primary 的 HSL 饱和度
 * 并钳制到 [0.42, 0.78]；明度用固定带——浅色主题（[surface] 相对亮度 ≥ 0.5）0.36、深色 0.44。
 * 纯函数：同参数恒同色板（90「同名同色、重启不变」依据）。
 */
fun deriveBadgePalette(primary: Color, surface: Color): List<Color> {
    val (baseHue, baseSaturation, _) = rgbToHsl(primary.red, primary.green, primary.blue)
    val saturation = baseSaturation.coerceIn(BADGE_SATURATION_MIN, BADGE_SATURATION_MAX)
    val lightness =
        if (surface.luminance() >= SURFACE_LIGHT_LUMINANCE_THRESHOLD) BADGE_LIGHTNESS_LIGHT_THEME
        else BADGE_LIGHTNESS_DARK_THEME
    return List(BADGE_PALETTE_SIZE) { i ->
        val (r, g, b) = hslToRgb(baseHue + BADGE_HUE_STEP_DEGREES * i, saturation, lightness)
        Color(r, g, b)
    }
}

/**
 * 首字文字色（52 量化门槛：≥4.5:1）：白/黑二选一，取 WCAG 相对亮度对比度较大者。
 * 数学下界：两对比度曲线的交点在背景相对亮度 ≈ 0.179 处、交点值即 **4.583:1**——
 * 任意背景色所选文字色对比度恒 ≥ 4.5:1，无须逐色人工校验（§17.1 测试仍逐色钉死）。
 */
fun contentColorFor(background: Color): Color {
    val backgroundLuminance = background.luminance()
    val contrastWithWhite = (1f + CONTRAST_LUMINANCE_OFFSET) / (backgroundLuminance + CONTRAST_LUMINANCE_OFFSET)
    val contrastWithBlack = (backgroundLuminance + CONTRAST_LUMINANCE_OFFSET) / CONTRAST_LUMINANCE_OFFSET
    return if (contrastWithWhite >= contrastWithBlack) Color.White else Color.Black
}

/**
 * 主题名 → 色板下标（U6 定稿）：`floorMod(name.hashCode(), 8)` 全名单因子散列——
 * 同首字不同名均匀散开；String.hashCode 为 JVM 规范定值 ⇒ 同名恒同 index、重启不变。
 */
fun badgeIndex(name: String): Int = Math.floorMod(name.hashCode(), BADGE_PALETTE_SIZE)

/**
 * 用户可感知首字（90）：[BreakIterator.getCharacterInstance] 首边界＝grapheme cluster——
 * surrogate 对 emoji／ZWJ 序列／国旗旗对整体一字（JVM 测试断言 host JDK 行为；
 * 老 ROM ICU 降级为已披露边界 R13/R14，观感降级不作缺陷）。空名防御返回「？」。
 */
fun firstGrapheme(name: String): String {
    if (name.isEmpty()) return EMPTY_NAME_PLACEHOLDER
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(name)
    val start = iterator.first()
    val end = iterator.next()
    return if (end == BreakIterator.DONE) name.substring(start) else name.substring(start, end)
}

/** RGB(sRGB 非线性编码，[0,1]) → HSL：h∈[0,360)、s/l∈[0,1]；无彩色 h=0、s=0。纯 Kotlin 手写。 */
private fun rgbToHsl(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val lightness = (max + min) / 2f
    if (max == min) return Triple(0f, 0f, lightness)
    val delta = max - min
    val saturation = if (lightness > 0.5f) delta / (2f - max - min) else delta / (max + min)
    val hue = when (max) {
        r -> (g - b) / delta + if (g < b) 6f else 0f
        g -> (b - r) / delta + 2f
        else -> (r - g) / delta + 4f
    } * 60f
    return Triple(hue, saturation, lightness)
}

/** HSL → RGB（三分量 [0,1]，sRGB 非线性编码）：h 单位度，负值／≥360 自动回绕。纯 Kotlin 手写。 */
private fun hslToRgb(h: Float, s: Float, l: Float): Triple<Float, Float, Float> {
    val chroma = (1f - abs(2f * l - 1f)) * s
    val huePrime = (h % 360f + 360f) % 360f / 60f
    val secondary = chroma * (1f - abs(huePrime % 2f - 1f))
    val (r, g, b) = when {
        huePrime < 1f -> Triple(chroma, secondary, 0f)
        huePrime < 2f -> Triple(secondary, chroma, 0f)
        huePrime < 3f -> Triple(0f, chroma, secondary)
        huePrime < 4f -> Triple(0f, secondary, chroma)
        huePrime < 5f -> Triple(secondary, 0f, chroma)
        else -> Triple(chroma, 0f, secondary)
    }
    val offset = l - chroma / 2f
    return Triple(r + offset, g + offset, b + offset)
}
