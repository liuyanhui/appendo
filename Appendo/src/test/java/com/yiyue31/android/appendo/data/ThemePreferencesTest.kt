package com.yiyue31.android.appendo.data

import android.content.Context
import android.content.SharedPreferences
import com.yiyue31.android.appendo.ui.theme.ThemeMode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

/**
 * 主题偏好（需求 48/49）：缺省回退 SYSTEM、三态读写往返、非法值回退、写入仅在显式调用时发生。
 * mock 方式与 FileRepositoryFirstLaunchTest 一致。
 */
class ThemePreferencesTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var prefsEditor: SharedPreferences.Editor
    private lateinit var context: Context

    /** SP 中当前存储的 theme_mode 值（null=键不存在）。 */
    private var storedValue: String? = null

    @Before
    fun setup() {
        storedValue = null

        prefsEditor = mock(SharedPreferences.Editor::class.java)
        `when`(prefsEditor.putString(anyString(), anyString())).thenAnswer { invocation ->
            storedValue = invocation.getArgument(1)
            prefsEditor
        }

        prefs = mock(SharedPreferences::class.java)
        `when`(prefs.edit()).thenReturn(prefsEditor)
        `when`(prefs.getString(eq("theme_mode"), isNull())).thenAnswer { storedValue }

        context = mock(Context::class.java)
        `when`(context.getSharedPreferences("appendo", Context.MODE_PRIVATE)).thenReturn(prefs)
    }

    @Test
    fun read_missingKey_fallsBackToSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemePreferences.read(context))
    }

    @Test
    fun readWrite_roundTrip_allThreeModes() {
        ThemeMode.entries.forEach { mode ->
            ThemePreferences.write(context, mode)
            assertEquals(mode, ThemePreferences.read(context))
        }
    }

    @Test
    fun read_illegalValue_fallsBackToSystem() {
        storedValue = "purple"
        assertEquals(ThemeMode.SYSTEM, ThemePreferences.read(context))
    }

    @Test
    fun read_explicitSystem_equalsUnset() {
        // 显式选"跟随系统"与从未选择行为无差异（需求 48：不可感知）
        ThemePreferences.write(context, ThemeMode.SYSTEM)
        assertEquals(ThemeMode.SYSTEM, ThemePreferences.read(context))
    }

    @Test
    fun write_explicitOnly_noInitializationWrite() {
        // 仅显式调用才写入：读路径不得产生任何写入（需求 48"未选择不持久化"）
        ThemePreferences.read(context)
        verify(prefsEditor, never()).putString(anyString(), anyString())
    }
}
