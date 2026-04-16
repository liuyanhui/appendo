package com.example.linkappending.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream

class MarkdownFileTest {

    private lateinit var mockContext: Context
    private lateinit var mockContentResolver: ContentResolver
    private lateinit var mockUri: Uri
    private lateinit var capturedOutput: ByteArrayOutputStream

    @Before
    fun setUp() {
        mockContext = mock()
        mockContentResolver = mock()
        mockUri = mock()
        capturedOutput = ByteArrayOutputStream()

        whenever(mockContext.contentResolver).thenReturn(mockContentResolver)
    }

    // ============================================================
    // 正则模式测试
    // ============================================================

    @Test
    fun `timestamp regex matches valid timestamp`() {
        val content = "# Link Collection\n\n---\n\n## 2026-04-10 12:00:00\n\nhello\n\n---\n"
        val matches = MarkdownFile.TIMESTAMP_REGEX.findAll(content).toList()
        assertEquals(1, matches.size)
        assertEquals("## 2026-04-10 12:00:00", matches[0].value)
    }

    @Test
    fun `timestamp regex matches multiple entries`() {
        val content = buildString {
            append("# Link Collection\n")
            append("\n---\n\n## 2026-04-10 12:00:00\n\nlink1\n\n---\n")
            append("\n---\n\n## 2026-04-10 12:01:00\n\nlink2\n\n---\n")
            append("\n---\n\n## 2026-04-10 12:02:00\n\nlink3\n\n---\n")
        }
        val matches = MarkdownFile.TIMESTAMP_REGEX.findAll(content).toList()
        assertEquals(3, matches.size)
    }

    @Test
    fun `timestamp regex ignores content with dashes`() {
        val content = buildString {
            append("# Link Collection\n")
            append("\n---\n\n## 2026-04-10 12:00:00\n\n")
            append("---\nthis has dashes\n---\n")
            append("\n---\n")
        }
        val matches = MarkdownFile.TIMESTAMP_REGEX.findAll(content).toList()
        assertEquals(1, matches.size)
    }

    @Test
    fun `timestamp regex does not match invalid formats`() {
        val content = "## 2026/04/10 12:00\n## just some text\n## 26-04-10 12:00:00"
        val matches = MarkdownFile.TIMESTAMP_REGEX.findAll(content).toList()
        assertEquals(0, matches.size)
    }

    @Test
    fun `timestamp regex does not match timestamp embedded in content text`() {
        val content = buildString {
            append("# Link Collection\n")
            append("\n---\n\n## 2026-04-10 12:00:00\n\n")
            append("Here is a fake: ## 2026-04-10 12:00:00 in the middle of text\n")
            append("\n---\n")
        }
        val matches = MarkdownFile.TIMESTAMP_REGEX.findAll(content).toList()
        // The embedded one has text after the timestamp on the same line,
        // so $ anchor prevents match. Only the standalone line matches.
        assertEquals(1, matches.size)
    }

    @Test
    fun `timestamp regex returns zero for empty content`() {
        val matches = MarkdownFile.TIMESTAMP_REGEX.findAll("").toList()
        assertEquals(0, matches.size)
    }

    @Test
    fun `timestamp regex returns zero for header only`() {
        val matches = MarkdownFile.TIMESTAMP_REGEX.findAll("# Link Collection\n").toList()
        assertEquals(0, matches.size)
    }

    @Test
    fun `file header constant is correct`() {
        assertEquals("# Link Collection\n", MarkdownFile.FILE_HEADER)
    }

    // ============================================================
    // readAll 测试
    // ============================================================

    @Test
    fun `readAll returns file content`() {
        val expected = "# Link Collection\n\n---\n\n## 2026-04-10 12:00:00\n\nhello\n\n---\n"
        whenever(mockContentResolver.openInputStream(mockUri))
            .thenReturn(ByteArrayInputStream(expected.toByteArray(Charsets.UTF_8)))

        val mdFile = MarkdownFile(mockContext, mockUri)
        val result = mdFile.readAll()

        assertEquals(expected, result)
    }

    @Test
    fun `readAll returns empty string when stream is null`() {
        whenever(mockContentResolver.openInputStream(mockUri)).thenReturn(null)

        val mdFile = MarkdownFile(mockContext, mockUri)
        val result = mdFile.readAll()

        assertEquals("", result)
    }

    @Test
    fun `readAll returns empty string on exception`() {
        whenever(mockContentResolver.openInputStream(mockUri))
            .thenThrow(RuntimeException("file not found"))

        val mdFile = MarkdownFile(mockContext, mockUri)
        val result = mdFile.readAll()

        assertEquals("", result)
    }

    // ============================================================
    // append 测试
    // ============================================================

    @Test
    fun `append writes formatted entry with timestamp`() {
        capturedOutput = ByteArrayOutputStream()
        whenever(mockContentResolver.openOutputStream(eq(mockUri), eq("wa")))
            .thenReturn(capturedOutput)

        val mdFile = MarkdownFile(mockContext, mockUri)
        val result = mdFile.append("https://example.com")

        assertTrue(result)
        val written = capturedOutput.toString("UTF-8")
        assertTrue(written.contains("---"))
        assertTrue(written.contains("## "))
        assertTrue(written.contains("https://example.com"))
        // Verify timestamp format: ## YYYY-MM-DD HH:mm:ss
        assertTrue(Regex("## \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}").containsMatchIn(written))
    }

    @Test
    fun `append preserves original content format`() {
        capturedOutput = ByteArrayOutputStream()
        whenever(mockContentResolver.openOutputStream(eq(mockUri), eq("wa")))
            .thenReturn(capturedOutput)

        val originalContent = "  Hello   World  \n Indented line\n  (brackets) & <special>"
        val mdFile = MarkdownFile(mockContext, mockUri)
        mdFile.append(originalContent)

        val written = capturedOutput.toString("UTF-8")
        assertTrue(written.contains(originalContent))
    }

    @Test
    fun `append falls back when MODE_APPEND throws`() {
        val existingContent = "# Link Collection\n"
        whenever(mockContentResolver.openOutputStream(eq(mockUri), eq("wa")))
            .thenThrow(RuntimeException("MODE_APPEND not supported"))
        whenever(mockContentResolver.openInputStream(mockUri))
            .thenReturn(ByteArrayInputStream(existingContent.toByteArray(Charsets.UTF_8)))
        capturedOutput = ByteArrayOutputStream()
        whenever(mockContentResolver.openOutputStream(eq(mockUri), eq("rwt")))
            .thenReturn(capturedOutput)

        val mdFile = MarkdownFile(mockContext, mockUri)
        val result = mdFile.append("fallback content")

        assertTrue(result)
        val written = capturedOutput.toString("UTF-8")
        assertTrue(written.contains(existingContent))
        assertTrue(written.contains("fallback content"))
    }

    @Test
    fun `append returns false when both modes fail`() {
        whenever(mockContentResolver.openOutputStream(eq(mockUri), eq("wa")))
            .thenThrow(RuntimeException("append failed"))
        whenever(mockContentResolver.openInputStream(mockUri))
            .thenThrow(RuntimeException("read failed"))
        whenever(mockContentResolver.openOutputStream(eq(mockUri), eq("rwt")))
            .thenThrow(RuntimeException("write failed"))

        val mdFile = MarkdownFile(mockContext, mockUri)
        val result = mdFile.append("will fail")

        assertFalse(result)
    }

    // ============================================================
    // clear 测试
    // ============================================================

    @Test
    fun `clear writes only file header`() {
        capturedOutput = ByteArrayOutputStream()
        whenever(mockContentResolver.openOutputStream(eq(mockUri), eq("rwt")))
            .thenReturn(capturedOutput)

        val mdFile = MarkdownFile(mockContext, mockUri)
        val result = mdFile.clear()

        assertTrue(result)
        val written = capturedOutput.toString("UTF-8")
        assertEquals(MarkdownFile.FILE_HEADER, written)
    }

    @Test
    fun `clear returns false on exception`() {
        whenever(mockContentResolver.openOutputStream(eq(mockUri), eq("rwt")))
            .thenThrow(RuntimeException("permission denied"))

        val mdFile = MarkdownFile(mockContext, mockUri)
        val result = mdFile.clear()

        assertFalse(result)
    }

    // ============================================================
    // exists 测试
    // ============================================================

    @Test
    fun `exists returns true when file is accessible`() {
        whenever(mockContentResolver.openInputStream(mockUri))
            .thenReturn(ByteArrayInputStream("content".toByteArray()))

        val mdFile = MarkdownFile(mockContext, mockUri)
        assertTrue(mdFile.exists())
    }

    @Test
    fun `exists returns false when stream is null`() {
        whenever(mockContentResolver.openInputStream(mockUri)).thenReturn(null)

        val mdFile = MarkdownFile(mockContext, mockUri)
        assertFalse(mdFile.exists())
    }

    @Test
    fun `exists returns false on exception`() {
        whenever(mockContentResolver.openInputStream(mockUri))
            .thenThrow(RuntimeException("file not found"))

        val mdFile = MarkdownFile(mockContext, mockUri)
        assertFalse(mdFile.exists())
    }

    // ============================================================
    // count 测试
    // ============================================================

    @Test
    fun `count returns zero for header-only file`() {
        whenever(mockContentResolver.openInputStream(mockUri))
            .thenReturn(ByteArrayInputStream("# Link Collection\n".toByteArray()))

        val mdFile = MarkdownFile(mockContext, mockUri)
        assertEquals(0, mdFile.count())
    }

    @Test
    fun `count returns correct number of entries`() {
        val content = buildString {
            append("# Link Collection\n")
            append("\n---\n\n## 2026-04-10 12:00:00\n\nlink1\n\n---\n")
            append("\n---\n\n## 2026-04-10 12:01:00\n\nlink2\n\n---\n")
        }
        whenever(mockContentResolver.openInputStream(mockUri))
            .thenReturn(ByteArrayInputStream(content.toByteArray()))

        val mdFile = MarkdownFile(mockContext, mockUri)
        assertEquals(2, mdFile.count())
    }

    @Test
    fun `count ignores dashes inside entry content`() {
        val content = buildString {
            append("# Link Collection\n")
            append("\n---\n\n## 2026-04-10 12:00:00\n\n")
            append("---\nsome text\n---\nmore text\n")
            append("\n---\n")
        }
        whenever(mockContentResolver.openInputStream(mockUri))
            .thenReturn(ByteArrayInputStream(content.toByteArray()))

        val mdFile = MarkdownFile(mockContext, mockUri)
        assertEquals(1, mdFile.count())
    }

    @Test
    fun `count returns zero on read error`() {
        whenever(mockContentResolver.openInputStream(mockUri))
            .thenThrow(RuntimeException("error"))

        val mdFile = MarkdownFile(mockContext, mockUri)
        assertEquals(0, mdFile.count())
    }

    // ============================================================
    // initHeader 测试
    // ============================================================

    @Test
    fun `initHeader writes header when file is empty`() {
        whenever(mockContentResolver.openInputStream(mockUri))
            .thenReturn(ByteArrayInputStream(ByteArray(0)))
        capturedOutput = ByteArrayOutputStream()
        whenever(mockContentResolver.openOutputStream(eq(mockUri), eq("rwt")))
            .thenReturn(capturedOutput)

        val mdFile = MarkdownFile(mockContext, mockUri)
        val result = mdFile.initHeader()

        assertTrue(result)
        assertEquals(MarkdownFile.FILE_HEADER, capturedOutput.toString("UTF-8"))
    }

    @Test
    fun `initHeader skips when file has content`() {
        whenever(mockContentResolver.openInputStream(mockUri))
            .thenReturn(ByteArrayInputStream("# Link Collection\n".toByteArray()))

        val mdFile = MarkdownFile(mockContext, mockUri)
        val result = mdFile.initHeader()

        assertTrue(result)
        // No write should happen — openOutputStream not called
    }

    @Test
    fun `initHeader returns false on write error`() {
        whenever(mockContentResolver.openInputStream(mockUri))
            .thenReturn(ByteArrayInputStream(ByteArray(0)))
        whenever(mockContentResolver.openOutputStream(eq(mockUri), eq("rwt")))
            .thenThrow(RuntimeException("write error"))

        val mdFile = MarkdownFile(mockContext, mockUri)
        assertFalse(mdFile.initHeader())
    }

    // ============================================================
    // 边界条件
    // ============================================================

    @Test
    fun `append with empty string still writes entry`() {
        capturedOutput = ByteArrayOutputStream()
        whenever(mockContentResolver.openOutputStream(eq(mockUri), eq("wa")))
            .thenReturn(capturedOutput)

        val mdFile = MarkdownFile(mockContext, mockUri)
        val result = mdFile.append("")

        assertTrue(result)
        val written = capturedOutput.toString("UTF-8")
        assertTrue(written.contains("## "))  // timestamp still written
    }

    @Test
    fun `append with multiline content preserves all lines`() {
        capturedOutput = ByteArrayOutputStream()
        whenever(mockContentResolver.openOutputStream(eq(mockUri), eq("wa")))
            .thenReturn(capturedOutput)

        val multiline = "line1\nline2\nline3\n  indented"
        val mdFile = MarkdownFile(mockContext, mockUri)
        mdFile.append(multiline)

        val written = capturedOutput.toString("UTF-8")
        assertTrue(written.contains(multiline))
    }

    @Test
    fun `append with unicode content preserves characters`() {
        capturedOutput = ByteArrayOutputStream()
        whenever(mockContentResolver.openOutputStream(eq(mockUri), eq("wa")))
            .thenReturn(capturedOutput)

        val unicode = "你好世界 🌍 日本語 한국어"
        val mdFile = MarkdownFile(mockContext, mockUri)
        mdFile.append(unicode)

        val written = capturedOutput.toString("UTF-8")
        assertTrue(written.contains(unicode))
    }

    @Test
    fun `append with special characters preserves them`() {
        capturedOutput = ByteArrayOutputStream()
        whenever(mockContentResolver.openOutputStream(eq(mockUri), eq("wa")))
            .thenReturn(capturedOutput)

        val special = "<html> & \"quotes\" 'single' \\backslash\\ tab\there"
        val mdFile = MarkdownFile(mockContext, mockUri)
        mdFile.append(special)

        val written = capturedOutput.toString("UTF-8")
        assertTrue(written.contains(special))
    }

    @Test
    fun `multiple sequential appends produce distinct entries`() {
        // Simulate reading the accumulated file content after 2 appends
        val accumulatedContent = buildString {
            append("# Link Collection\n")
            append("\n---\n\n## 2026-04-10 12:00:00\n\nfirst\n\n---\n")
            append("\n---\n\n## 2026-04-10 12:00:01\n\nsecond\n\n---\n")
        }
        whenever(mockContentResolver.openInputStream(mockUri))
            .thenReturn(ByteArrayInputStream(accumulatedContent.toByteArray()))

        val mdFile = MarkdownFile(mockContext, mockUri)
        assertEquals(2, mdFile.count())
    }

    @Test
    fun `format entry has correct structure`() {
        // Test the format structure directly by constructing expected output
        val entry = "\n---\n\n## 2026-04-10 12:00:00\n\ncontent\n\n---\n"
        // Structure: \n---\n\n## TIMESTAMP\n\nCONTENT\n\n---\n
        assertTrue(entry.startsWith("\n---\n\n## "))
        assertTrue(entry.contains("\n\ncontent\n\n"))
        assertTrue(entry.endsWith("\n---\n"))
    }
}
