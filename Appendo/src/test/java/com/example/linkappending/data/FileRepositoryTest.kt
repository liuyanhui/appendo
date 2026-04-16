package com.example.linkappending.data

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FileRepositoryTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var repository: FileRepository

    @Before
    fun setUp() {
        mockContext = mock()
        mockPrefs = mock()
        mockEditor = mock()

        whenever(mockContext.getSharedPreferences("link_appending", Context.MODE_PRIVATE))
            .thenReturn(mockPrefs)
        whenever(mockPrefs.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putString(any(), any())).thenReturn(mockEditor)
        whenever(mockEditor.remove(any())).thenReturn(mockEditor)

        repository = FileRepository(mockContext)
    }

    // ============================================================
    // getFileUri 测试
    // ============================================================

    @Test
    fun `getFileUri returns null when no URI stored`() {
        whenever(mockPrefs.getString("file_uri", null)).thenReturn(null)

        val result = repository.getFileUri()

        assertEquals(null, result)
    }

    @Test
    fun `getFileUri returns stored URI`() {
        val expectedUri = "content://com.android.externalstorage.documents/document/primary%3Acollection.md"
        whenever(mockPrefs.getString("file_uri", null)).thenReturn(expectedUri)

        val result = repository.getFileUri()

        assertEquals(expectedUri, result)
    }

    // ============================================================
    // setFileUri 测试
    // ============================================================

    @Test
    fun `setFileUri persists URI to SharedPreferences`() {
        val uri = "content://com.android.externalstorage.documents/document/primary%3Anew.md"
        repository.setFileUri(uri)

        verify(mockEditor).putString("file_uri", uri)
        verify(mockEditor).apply()
    }

    // ============================================================
    // clearFileUri 测试
    // ============================================================

    @Test
    fun `clearFileUri removes URI from SharedPreferences`() {
        repository.clearFileUri()

        verify(mockEditor).remove("file_uri")
        verify(mockEditor).apply()
    }

    // ============================================================
    // 边界条件
    // ============================================================

    @Test
    fun `setFileUri overwrites previous URI`() {
        whenever(mockPrefs.getString("file_uri", null))
            .thenReturn("content://old-uri")

        assertEquals("content://old-uri", repository.getFileUri())

        val newUri = "content://new-uri"
        repository.setFileUri(newUri)

        verify(mockEditor).putString("file_uri", newUri)
    }

    @Test
    fun `setFileUri with empty string is allowed`() {
        repository.setFileUri("")

        verify(mockEditor).putString("file_uri", "")
    }

    @Test
    fun `getFileUri after clear returns null`() {
        whenever(mockPrefs.getString("file_uri", null)).thenReturn(null)

        repository.clearFileUri()
        val result = repository.getFileUri()

        assertEquals(null, result)
    }
}
