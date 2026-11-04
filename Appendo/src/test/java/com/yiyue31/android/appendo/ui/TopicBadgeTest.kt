package com.yiyue31.android.appendo.ui

import androidx.compose.ui.graphics.Color
import com.yiyue31.android.appendo.ui.theme.AppendoDarkScheme
import com.yiyue31.android.appendo.ui.theme.AppendoLightScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * TopicBadge 纯函数单测（设计修订二 §17.1 第一行 ①~④，计划 T-12）：纯 JVM，不引 Robolectric（TD-003）。
 *
 * - 双色板直接引用 Theme.kt internal 常量（同模块测试编译可见）；
 * - 对比度以 WCAG 相对亮度公式**独立复算**（不调 compose luminance()，与实现解耦）；
 * - 首字用例断言 host JDK BreakIterator 行为（R13/R14：老 ROM ICU 降级为已知边界）。
 */
class TopicBadgeTest {

    // ---------- ① hash 稳定性：同名恒同 index；floorMod 恒界内 [0,8) ----------

    @Test
    fun badgeIndex_sameNameStable_andAlwaysInRange() {
        // 同名恒同 index（String.hashCode 为 JVM 规范定值 ⇒ 同名同色、重启不变依据）
        assertEquals(badgeIndex("工作主题"), badgeIndex("工作主题"))
        assertEquals(badgeIndex("T-b"), badgeIndex("T-b"))
        assertEquals("空串（hash=0）也须稳定", badgeIndex(""), badgeIndex(""))

        // floorMod 恒界内：含确定性负散列（U+FFFF×5，31 进制累加溢出 ⇒ hash<0，覆盖负数取模分支）
        val negativeHashName = "￿".repeat(5)
        assertTrue("前置：该串 hashCode 应为负", negativeHashName.hashCode() < 0)
        val names = buildList {
            add(""); add("a"); add("A"); add("工作"); add("主题归档")
            add("😀全家"); add("🇨🇳"); add("𠀀罕"); add(negativeHashName)
            repeat(200) { add("topic-$it") }
            repeat(50) { add("￿-$it") }
        }
        names.forEach { name ->
            val index = badgeIndex(name)
            assertTrue("floorMod 恒界内 [0,8)：'$name' -> $index", index in 0 until 8)
        }
    }

    // ---------- ② 双色板成对断言：各派生 8 色，逐色 WCAG 复算所选文字色对比度 ≥ 4.5:1 ----------

    @Test
    fun contentColorFor_bothPalettes_everyColorContrastAtLeast45to1() {
        val palettes = listOf(
            "light" to deriveBadgePalette(AppendoLightScheme.primary, AppendoLightScheme.surface),
            "dark" to deriveBadgePalette(AppendoDarkScheme.primary, AppendoDarkScheme.surface)
        )
        for ((label, palette) in palettes) {
            assertEquals("[$label] 色板恒 8 色（45°×i 全环派生）", 8, palette.size)
            var minContrast = Float.MAX_VALUE
            for ((index, background) in palette.withIndex()) {
                val chosen = contentColorFor(background)
                val contrast = wcagContrastRatio(chosen, background)
                minContrast = minOf(minContrast, contrast)
                assertTrue(
                    "[$label·色$index] 所选文字色对比度 $contrast 须 ≥ 4.5:1（4.583 数学下界回归钉死）",
                    contrast >= 4.5f
                )
            }
            // 报告取证：实测最小值（gradle 测试 XML 的 system-out 可见）
            println("[TopicBadgeTest] $label 色板 8 色最小对比度 = $minContrast")
        }
    }

    // ---------- ③ 首字取法：grapheme cluster 用例集（host JDK 行为） ----------

    @Test
    fun firstGrapheme_clusterCases() {
        assertEquals("工", firstGrapheme("工作主题")) // 普通汉字
        assertEquals("N", firstGrapheme("Notes")) // 字母
        assertEquals("😀", firstGrapheme("😀主题")) // surrogate 对 emoji 整体一字
        assertEquals("👨‍👩‍👧", firstGrapheme("👨‍👩‍👧外出")) // ZWJ 序列整体一字
        assertEquals("🇨🇳", firstGrapheme("🇨🇳归档")) // 国旗旗对（regional indicator pair）整体一字
        assertEquals("𠀀", firstGrapheme("𠀀字典")) // BMP 外生僻字（U+20000）整体一字
        assertEquals("空名防御返回「？」", "？", firstGrapheme(""))
    }

    // ---------- ④ 派生确定性：同 (primary, surface) 参数对恒同色板 ----------

    @Test
    fun deriveBadgePalette_deterministic_sameParamsSamePalette() {
        val inputs = listOf(
            AppendoLightScheme.primary to AppendoLightScheme.surface,
            AppendoDarkScheme.primary to AppendoDarkScheme.surface
        )
        for ((primary, surface) in inputs) {
            assertEquals(
                "同参数对恒同色板（90 同名同色前提）",
                deriveBadgePalette(primary, surface),
                deriveBadgePalette(primary, surface)
            )
        }
    }

    // ---------- WCAG 独立复算（与实现解耦：不调 compose luminance()） ----------

    private fun wcagContrastRatio(a: Color, b: Color): Float {
        val lighter = maxOf(wcagRelativeLuminance(a), wcagRelativeLuminance(b))
        val darker = minOf(wcagRelativeLuminance(a), wcagRelativeLuminance(b))
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun wcagRelativeLuminance(color: Color): Float =
        0.2126f * linearizeSrgb(color.red) +
            0.7152f * linearizeSrgb(color.green) +
            0.0722f * linearizeSrgb(color.blue)

    private fun linearizeSrgb(component: Float): Float =
        if (component <= 0.04045f) component / 12.92f
        else ((component + 0.055f) / 1.055f).pow(2.4f)
}
