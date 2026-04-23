package com.yiyue31.android.appendo.data

import android.content.Context
import android.content.SharedPreferences
import com.yiyue31.android.appendo.util.MarkdownFormatter
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.mockito.Mockito.*
import java.io.File

class FileRepositoryFirstLaunchTest {

    private lateinit var tempDir: File
    private lateinit var prefs: SharedPreferences
    private lateinit var prefsEditor: SharedPreferences.Editor
    private lateinit var context: Context
    private lateinit var repository: FileRepository

    @Before
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "appendo_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        prefsEditor = mock(SharedPreferences.Editor::class.java)
        `when`(prefsEditor.putBoolean(anyString(), anyBoolean())).thenReturn(prefsEditor)
        `when`(prefsEditor.putString(anyString(), anyString())).thenReturn(prefsEditor)
        `when`(prefsEditor.remove(anyString())).thenReturn(prefsEditor)
        `when`(prefsEditor.putLong(anyString(), anyLong())).thenReturn(prefsEditor)

        prefs = mock(SharedPreferences::class.java)
        `when`(prefs.edit()).thenReturn(prefsEditor)

        context = mock(Context::class.java)
        `when`(context.getSharedPreferences("appendo", Context.MODE_PRIVATE)).thenReturn(prefs)

        repository = FileRepository(context)
    }

    @After
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun isFirstLaunch_noFileNoSaf_returnsTrue() {
        // Default file does not exist, no SAF configured
        `when`(prefs.getBoolean("use_saf", false)).thenReturn(false)
        `when`(prefs.contains("file_uri")).thenReturn(false)
        `when`(context.getExternalFilesDir(null)).thenReturn(File(tempDir, "nonexistent"))

        assertTrue("Should be first launch when no file and no SAF", repository.isFirstLaunch())
    }

    @Test
    fun isFirstLaunch_headerOnlyFile_returnsTrue() {
        // File exists but only has header
        `when`(prefs.getBoolean("use_saf", false)).thenReturn(false)
        `when`(prefs.contains("file_uri")).thenReturn(false)
        `when`(context.getExternalFilesDir(null)).thenReturn(tempDir)

        val file = File(tempDir, "Appendo.md")
        file.writeText(MarkdownFormatter.FILE_HEADER)

        assertTrue("Should be first launch when file has only header", repository.isFirstLaunch())
    }

    @Test
    fun isFirstLaunch_emptyFile_returnsTrue() {
        `when`(prefs.getBoolean("use_saf", false)).thenReturn(false)
        `when`(prefs.contains("file_uri")).thenReturn(false)
        `when`(context.getExternalFilesDir(null)).thenReturn(tempDir)

        val file = File(tempDir, "Appendo.md")
        file.writeText("")

        assertTrue("Should be first launch when file is empty", repository.isFirstLaunch())
    }

    @Test
    fun isFirstLaunch_fileWithEntries_returnsFalse() {
        `when`(prefs.getBoolean("use_saf", false)).thenReturn(false)
        `when`(prefs.contains("file_uri")).thenReturn(false)
        `when`(context.getExternalFilesDir(null)).thenReturn(tempDir)

        val file = File(tempDir, "Appendo.md")
        file.writeText("# Appendo\n\n---\n## 2026-04-22 10:00:00\n\nSome content\n\n---\n")

        assertFalse("Should NOT be first launch when file has entries", repository.isFirstLaunch())
    }

    @Test
    fun isFirstLaunch_safModeEnabled_returnsFalse() {
        `when`(prefs.getBoolean("use_saf", false)).thenReturn(true)

        assertFalse("Should NOT be first launch when SAF is enabled", repository.isFirstLaunch())
    }

    @Test
    fun isFirstLaunch_hasFileUri_returnsFalse() {
        `when`(prefs.getBoolean("use_saf", false)).thenReturn(false)
        `when`(prefs.contains("file_uri")).thenReturn(true)

        assertFalse("Should NOT be first launch when file URI exists", repository.isFirstLaunch())
    }
}
