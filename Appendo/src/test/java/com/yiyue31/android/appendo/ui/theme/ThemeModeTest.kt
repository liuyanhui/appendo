package com.yiyue31.android.appendo.ui.theme

import org.junit.Assert.*
import org.junit.Test

/**
 * resolveDarkTheme 纯函数（需求 50）：模式 × 系统深浅 → 是否深色。
 */
class ThemeModeTest {

    @Test
    fun system_followsSystem() {
        assertTrue(resolveDarkTheme(ThemeMode.SYSTEM, isSystemInDark = true))
        assertFalse(resolveDarkTheme(ThemeMode.SYSTEM, isSystemInDark = false))
    }

    @Test
    fun light_alwaysLight() {
        assertFalse(resolveDarkTheme(ThemeMode.LIGHT, isSystemInDark = true))
        assertFalse(resolveDarkTheme(ThemeMode.LIGHT, isSystemInDark = false))
    }

    @Test
    fun dark_alwaysDark() {
        assertTrue(resolveDarkTheme(ThemeMode.DARK, isSystemInDark = true))
        assertTrue(resolveDarkTheme(ThemeMode.DARK, isSystemInDark = false))
    }
}
