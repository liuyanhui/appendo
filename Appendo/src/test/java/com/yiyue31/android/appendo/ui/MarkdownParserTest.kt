package com.yiyue31.android.appendo.ui

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for parseMarkdownEntries function.
 *
 * Tests the following scenarios:
 * - Parse valid entries
 * - Handle empty content
 * - Handle entries with empty content (no content after timestamp)
 * - Handle special characters in content
 * - Handle multi-line content
 */
class MarkdownParserTest {

    @Test
    fun testParseEntries_validContent() {
        // Arrange
        val content = """
# Appendo

---
## 2026-04-17 10:00:00

First entry

---
## 2026-04-17 11:00:00

Second entry

---
""".trimIndent()

        // Act
        val entries = parseMarkdownEntries(content)

        // Assert
        assertEquals("Should parse 2 entries", 2, entries.size)
        assertEquals("First entry timestamp", "2026-04-17 10:00:00", entries[0].timestamp)
        assertEquals("First entry content", "First entry", entries[0].content.trim())
        assertEquals("Second entry timestamp", "2026-04-17 11:00:00", entries[1].timestamp)
        assertEquals("Second entry content", "Second entry", entries[1].content.trim())
    }

    @Test
    fun testParseEntries_emptyContent() {
        // Arrange
        val content = "# Appendo\n\n---\n"

        // Act
        val entries = parseMarkdownEntries(content)

        // Assert
        assertEquals("Should parse 0 entries", 0, entries.size)
    }

    @Test
    fun testParseEntries_withEmptyEntries() {
        // Arrange - Entry with timestamp but no content
        val content = """
# Appendo

---
## 2026-04-17 10:00:00

First entry

---
## 2026-04-17 11:00:00


---
## 2026-04-17 12:00:00

Third entry

---
""".trimIndent()

        // Act
        val entries = parseMarkdownEntries(content)

        // Assert
        // Empty entry (no content) should be skipped
        assertEquals("Should parse only 2 entries (empty one skipped)", 2, entries.size)
        assertEquals("First entry timestamp", "2026-04-17 10:00:00", entries[0].timestamp)
        assertEquals("Last entry timestamp", "2026-04-17 12:00:00", entries[1].timestamp)
    }

    @Test
    fun testParseEntries_multiLineContent() {
        // Arrange
        val content = """
# Appendo

---
## 2026-04-17 10:00:00

Line 1
Line 2
Line 3

---
""".trimIndent()

        // Act
        val entries = parseMarkdownEntries(content)

        // Assert
        assertEquals("Should parse 1 entry", 1, entries.size)
        val expectedContent = "Line 1\nLine 2\nLine 3"
        assertEquals("Multi-line content should be preserved", expectedContent, entries[0].content.trim())
    }

    @Test
    fun testParseEntries_specialCharacters() {
        // Arrange
        val content = """
# Appendo

---
## 2026-04-17 10:00:00

Content with <special> & characters
![Image](url.jpg)

---
""".trimIndent()

        // Act
        val entries = parseMarkdownEntries(content)

        // Assert
        assertEquals("Should parse 1 entry", 1, entries.size)
        assertTrue("Should preserve special characters", entries[0].content.contains("<special>"))
        assertTrue("Should preserve markdown syntax", entries[0].content.contains("![Image]"))
    }

    @Test
    fun testParseEntries_chineseContent() {
        // Arrange
        val content = """
# Appendo

---
## 2026-04-17 10:00:00

这是一条中文内容
包含特殊字符：#、*、@

---
## 2026-04-17 11:00:00

Second entry with 混合 content

---
""".trimIndent()

        // Act
        val entries = parseMarkdownEntries(content)

        // Assert
        assertEquals("Should parse 2 entries", 2, entries.size)
        assertTrue("Should preserve Chinese characters", entries[0].content.contains("中文内容"))
        assertTrue("Should preserve mixed content", entries[1].content.contains("混合"))
    }

    @Test
    fun testParseEntries_urls() {
        // Arrange
        val content = """
# Appendo

---
## 2026-04-17 10:00:00

https://example.com/path?query=value

---
## 2026-04-17 11:00:00

Check out: https://github.com/user/repo

---
""".trimIndent()

        // Act
        val entries = parseMarkdownEntries(content)

        // Assert
        assertEquals("Should parse 2 entries", 2, entries.size)
        assertTrue("Should preserve URLs", entries[0].content.contains("https://example.com"))
        assertTrue("Should preserve URLs with text", entries[1].content.contains("https://github.com"))
    }

    @Test
    fun testParseEntries_preservesWhitespace() {
        // Arrange
        val content = """
# Appendo

---
## 2026-04-17 10:00:00

Indented content:
    - Item 1
    - Item 2

---
""".trimIndent()

        // Act
        val entries = parseMarkdownEntries(content)

        // Assert
        assertEquals("Should parse 1 entry", 1, entries.size)
        assertTrue("Should preserve indentation", entries[0].content.contains("    - Item 1"))
    }

    @Test
    fun testParseEntries_noHeader() {
        // Arrange - Content without file header
        val content = """
---
## 2026-04-17 10:00:00

Entry without header

---
""".trimIndent()

        // Act
        val entries = parseMarkdownEntries(content)

        // Assert
        assertEquals("Should parse 1 entry even without header", 1, entries.size)
        assertEquals("2026-04-17 10:00:00", entries[0].timestamp)
    }

    @Test
    fun testParseEntries_codeBlock() {
        // Arrange
        val content = """
# Appendo

---
## 2026-04-17 10:00:00

```
code block
with multiple lines
```

---
""".trimIndent()

        // Act
        val entries = parseMarkdownEntries(content)

        // Assert
        assertEquals("Should parse 1 entry", 1, entries.size)
        assertTrue("Should preserve code blocks", entries[0].content.contains("```"))
    }
}
