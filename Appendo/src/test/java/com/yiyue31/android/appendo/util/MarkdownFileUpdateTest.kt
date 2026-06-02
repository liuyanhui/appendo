package com.yiyue31.android.appendo.util

import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import java.io.File

/**
 * Unit tests for updateEntry functionality using timestamp as identifier.
 *
 * Tests the following scenarios:
 * - Update single-line content
 * - Update multi-line content
 * - Update non-existent timestamp returns false
 * - Update does not affect other entries
 * - Update with empty string content
 * - Update content containing special Markdown characters ("---", "## ")
 */
class MarkdownFileUpdateTest {

    private lateinit var testFile: File
    private lateinit var markdownFile: TestUpdateFileBasedMarkdownFile

    @Before
    fun setup() {
        // Create temporary test file
        val tempDir = System.getProperty("java.io.tmpdir")
        testFile = File(tempDir, "test_update_${
            System.currentTimeMillis()
        }.md")

        // Initialize with sample content
        val sampleContent = """
# Appendo

---
## 2026-04-17 10:00:00

First entry content

---
## 2026-04-17 11:00:00

Second entry content

---
## 2026-04-17 12:00:00

Third entry content

---
""".trimIndent()

        testFile.writeText(sampleContent)
        // Use test implementation that doesn't require Context
        markdownFile = TestUpdateFileBasedMarkdownFile(testFile)
    }

    @After
    fun cleanup() {
        if (testFile.exists()) {
            testFile.delete()
        }
    }

    @Test
    fun testUpdateEntry_singleLineContent() {
        // Arrange
        val timestampToUpdate = "2026-04-17 11:00:00"
        val newContent = "Updated second entry"

        // Act
        val result = markdownFile.updateEntry(timestampToUpdate, newContent)

        // Assert
        assertTrue("Update should succeed", result)

        val content = testFile.readText()
        assertTrue("Updated content should exist", content.contains("Updated second entry"))
        assertFalse("Old content should not exist", content.contains("Second entry content"))
        assertTrue("First entry should still exist", content.contains("First entry content"))
        assertTrue("Third entry should still exist", content.contains("Third entry content"))
        assertEquals("Should have 3 entries", 3, markdownFile.count())
    }

    @Test
    fun testUpdateEntry_multiLineContent() {
        // Arrange
        val timestampToUpdate = "2026-04-17 10:00:00"
        val newContent = "Line 1\nLine 2\nLine 3"

        // Act
        val result = markdownFile.updateEntry(timestampToUpdate, newContent)

        // Assert
        assertTrue("Update should succeed", result)

        val content = testFile.readText()
        assertTrue("All new lines should exist", content.contains("Line 1"))
        assertTrue("All new lines should exist", content.contains("Line 2"))
        assertTrue("All new lines should exist", content.contains("Line 3"))
        assertFalse("Old content should not exist", content.contains("First entry content"))

        // Verify multi-line content is parsed correctly
        val entries = parseTestEntries(content)
        val updatedEntry = entries.find { it.first == "2026-04-17 10:00:00" }
        assertNotNull("Updated entry should be found", updatedEntry)
        assertEquals("Content should be multi-line", "Line 1\nLine 2\nLine 3", updatedEntry!!.second)
    }

    @Test
    fun testUpdateEntry_nonExistentTimestamp() {
        // Arrange
        val nonExistentTimestamp = "2099-12-31 23:59:59"
        val originalContent = testFile.readText()

        // Act
        val result = markdownFile.updateEntry(nonExistentTimestamp, "Should not be written")

        // Assert
        assertFalse("Update should fail for non-existent timestamp", result)

        val content = testFile.readText()
        assertEquals("File content should be unchanged", originalContent, content)
        assertEquals("Should still have 3 entries", 3, markdownFile.count())
    }

    @Test
    fun testUpdateEntry_otherEntriesUnaffected() {
        // Arrange
        val timestampToUpdate = "2026-04-17 11:00:00"
        val newContent = "New middle content"

        // Act
        val result = markdownFile.updateEntry(timestampToUpdate, newContent)

        // Assert
        assertTrue("Update should succeed", result)

        val content = testFile.readText()
        // Verify first entry is untouched
        assertTrue("First entry timestamp should exist", content.contains("2026-04-17 10:00:00"))
        assertTrue("First entry content should exist", content.contains("First entry content"))
        // Verify third entry is untouched
        assertTrue("Third entry timestamp should exist", content.contains("2026-04-17 12:00:00"))
        assertTrue("Third entry content should exist", content.contains("Third entry content"))

        // Verify entries parse correctly
        val entries = parseTestEntries(content)
        assertEquals("Should have 3 entries", 3, entries.size)
        assertEquals("First entry content unchanged", "First entry content", entries[0].second)
        assertEquals("Updated entry content", "New middle content", entries[1].second)
        assertEquals("Third entry content unchanged", "Third entry content", entries[2].second)
    }

    @Test
    fun testUpdateEntry_emptyContent() {
        // Arrange
        val timestampToUpdate = "2026-04-17 10:00:00"
        val newContent = ""

        // Act
        val result = markdownFile.updateEntry(timestampToUpdate, newContent)

        // Assert
        assertTrue("Update should succeed with empty content", result)

        val content = testFile.readText()
        assertTrue("Timestamp should still exist", content.contains("2026-04-17 10:00:00"))
        assertFalse("Old content should not exist", content.contains("First entry content"))
        assertTrue("Other entries should still exist", content.contains("Second entry content"))
    }

    @Test
    fun testUpdateEntry_contentWithMarkdownSeparator() {
        // Arrange - content containing "---" which should not be confused with entry separator
        val timestampToUpdate = "2026-04-17 11:00:00"
        val newContent = "Content with\n---\nseparator inside"

        // Act
        val result = markdownFile.updateEntry(timestampToUpdate, newContent)

        // Assert
        assertTrue("Update should succeed", result)

        val content = testFile.readText()
        assertTrue("New content should exist", content.contains("Content with"))
        assertTrue("Separator in content should exist", content.contains("separator inside"))
        assertTrue("First entry should still exist", content.contains("2026-04-17 10:00:00"))
        assertTrue("Third entry should still exist", content.contains("2026-04-17 12:00:00"))
        assertEquals("Should have 3 entries", 3, markdownFile.count())
    }

    @Test
    fun testUpdateEntry_contentWithTimestampPattern() {
        // Arrange - content containing "## " pattern which should not be confused with timestamp
        val timestampToUpdate = "2026-04-17 12:00:00"
        val newContent = "Heading in content\n## Fake 2026-01-01 00:00:00\nMore text"

        // Act
        val result = markdownFile.updateEntry(timestampToUpdate, newContent)

        // Assert
        assertTrue("Update should succeed", result)

        val content = testFile.readText()
        assertTrue("New content should exist", content.contains("Heading in content"))
        assertTrue("Fake timestamp in content should exist", content.contains("## Fake 2026-01-01 00:00:00"))
        assertTrue("First entry should still exist", content.contains("2026-04-17 10:00:00"))
        assertTrue("Second entry should still exist", content.contains("2026-04-17 11:00:00"))
        // Note: count() uses regex which only matches exact timestamp format,
        // so "## Fake..." won't match, but we verify the actual entries are intact
    }

    @Test
    fun testUpdateEntry_lastEntry() {
        // Arrange
        val timestampToUpdate = "2026-04-17 12:00:00"
        val newContent = "Updated last entry"

        // Act
        val result = markdownFile.updateEntry(timestampToUpdate, newContent)

        // Assert
        assertTrue("Update should succeed", result)

        val content = testFile.readText()
        assertTrue("Updated content should exist", content.contains("Updated last entry"))
        assertFalse("Old content should not exist", content.contains("Third entry content"))
        assertTrue("First entry should still exist", content.contains("First entry content"))
        assertEquals("Should have 3 entries", 3, markdownFile.count())
    }

    @Test
    fun testUpdateEntry_firstEntry() {
        // Arrange
        val timestampToUpdate = "2026-04-17 10:00:00"
        val newContent = "Updated first entry"

        // Act
        val result = markdownFile.updateEntry(timestampToUpdate, newContent)

        // Assert
        assertTrue("Update should succeed", result)

        val content = testFile.readText()
        assertTrue("Updated content should exist", content.contains("Updated first entry"))
        assertFalse("Old content should not exist", content.contains("First entry content"))
        assertTrue("Second entry should still exist", content.contains("Second entry content"))
        assertTrue("Third entry should still exist", content.contains("Third entry content"))
        assertEquals("Should have 3 entries", 3, markdownFile.count())
    }

    /**
     * Helper to parse entries from content for verification.
     * Returns list of (timestamp, content) pairs.
     */
    private fun parseTestEntries(content: String): List<Pair<String, String>> {
        val entries = mutableListOf<Pair<String, String>>()
        val lines = content.lines()
        var currentTimestamp = ""
        var currentContent = StringBuilder()

        for (line in lines) {
            when {
                MarkdownFormatter.getTimestampRegex().matches(line) -> {
                    if (currentTimestamp.isNotEmpty() && currentContent.isNotEmpty()) {
                        entries.add(Pair(currentTimestamp, currentContent.toString().trim()))
                    }
                    currentTimestamp = line.substring(3).trim()
                    currentContent = StringBuilder()
                }
                line.startsWith("# Appendo") || line == "---" -> {
                    // Skip
                }
                else -> {
                    if (currentContent.isNotEmpty()) {
                        currentContent.append("\n")
                    }
                    currentContent.append(line)
                }
            }
        }

        if (currentTimestamp.isNotEmpty() && currentContent.isNotEmpty()) {
            entries.add(Pair(currentTimestamp, currentContent.toString().trim()))
        }

        return entries
    }
}

/**
 * Test implementation of FileBasedMarkdownFile that doesn't require Context.
 * Used for unit testing updateEntry operations without Android dependencies.
 */
private class TestUpdateFileBasedMarkdownFile(private val file: File) : MarkdownFileOperations {
    private val lock = Any()

    override fun append(content: String): Boolean {
        synchronized(lock) {
            return try {
                val entry = MarkdownFormatter.formatEntry(content)
                file.appendText(entry)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    override fun readAll(): String {
        return try {
            file.readText()
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    override fun clear(): Boolean {
        synchronized(lock) {
            return try {
                file.writeText(MarkdownFormatter.FILE_HEADER)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    override fun exists(): Boolean {
        return file.exists()
    }

    override fun count(): Int {
        val content = readAll()
        return MarkdownFormatter.getTimestampRegex().findAll(content).count()
    }

    override fun initHeader(): Boolean {
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.createNewFile()
        }
        val content = readAll()
        if (content.isBlank()) {
            return try {
                file.writeText(MarkdownFormatter.FILE_HEADER)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
        return true
    }

    override fun deleteEntry(timestamp: String): Boolean {
        synchronized(lock) {
            return try {
                val content = readAll()
                val lines = content.lines().toMutableList()

                var targetStartIndex = -1
                var targetEndIndex = lines.size
                var currentTimestamp = ""

                for (i in lines.indices) {
                    val line = lines[i]
                    when {
                        line.matches(Regex("^## \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$")) -> {
                            if (currentTimestamp == timestamp && targetStartIndex >= 0) {
                                targetEndIndex = i
                                break
                            }
                            currentTimestamp = line.substring(3).trim()
                            if (currentTimestamp == timestamp) {
                                targetStartIndex = i
                            }
                        }
                    }
                }

                if (targetStartIndex < 0) {
                    return false
                }

                val deleteFrom = if (targetStartIndex > 0 && lines[targetStartIndex - 1].matches(Regex("^---$"))) {
                    targetStartIndex - 1
                } else {
                    targetStartIndex
                }

                lines.subList(deleteFrom, targetEndIndex).clear()
                file.writeText(lines.joinToString("\n"))
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    override fun updateEntry(timestamp: String, newContent: String): Boolean {
        synchronized(lock) {
            return try {
                val content = readAll()
                val lines = content.lines().toMutableList()

                // Find the entry with matching timestamp
                // Same boundary logic: use next "## YYYY-MM-DD HH:mm:ss" line as boundary
                var targetStartIndex = -1
                var targetEndIndex = lines.size
                var currentTimestamp = ""

                for (i in lines.indices) {
                    val line = lines[i]
                    when {
                        line.matches(Regex("^## \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$")) -> {
                            if (currentTimestamp == timestamp && targetStartIndex >= 0) {
                                targetEndIndex = i
                                break
                            }
                            currentTimestamp = line.substring(3).trim()
                            if (currentTimestamp == timestamp) {
                                targetStartIndex = i
                            }
                        }
                    }
                }

                if (targetStartIndex < 0) {
                    return false
                }

                // Build new content: keep timestamp line, replace content between timestamp and next entry
                val newLines = mutableListOf<String>()
                newLines.addAll(lines.subList(0, targetStartIndex))
                newLines.add(lines[targetStartIndex])
                newLines.add("")
                newLines.add(newContent)
                if (targetEndIndex < lines.size) {
                    newLines.add("")
                    newLines.addAll(lines.subList(targetEndIndex, lines.size))
                }

                file.writeText(newLines.joinToString("\n"))
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    override fun writeAll(content: String): Boolean {
        synchronized(lock) {
            return try {
                file.writeText(content)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}
