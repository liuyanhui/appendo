package com.yiyue31.android.appendo.util

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import java.io.File

/**
 * Unit tests for deleteEntry functionality using timestamp as identifier.
 *
 * Tests the following scenarios:
 * - Delete single entry by timestamp
 * - Delete first entry
 * - Delete last entry
 * - Delete middle entry
 * - Delete non-existent timestamp
 * - Delete entries with empty content
 */
class MarkdownFileDeleteTest {

    private lateinit var testFile: File
    private lateinit var markdownFile: TestFileBasedMarkdownFile

    @Before
    fun setup() {
        // Create temporary test file
        val tempDir = System.getProperty("java.io.tmpdir")
        testFile = File(tempDir, "test_delete_${
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
        markdownFile = TestFileBasedMarkdownFile(testFile)
    }

    @After
    fun cleanup() {
        if (testFile.exists()) {
            testFile.delete()
        }
    }

    @Test
    fun testDeleteEntry_middleEntry() {
        // Arrange
        val timestampToDelete = "2026-04-17 11:00:00"

        // Act
        val result = markdownFile.deleteEntry(timestampToDelete)

        // Assert
        assertTrue("Delete should succeed", result)

        val content = testFile.readText()
        assertFalse("Deleted entry should not exist", content.contains(timestampToDelete))
        assertTrue("First entry should still exist", content.contains("2026-04-17 10:00:00"))
        assertTrue("Last entry should still exist", content.contains("2026-04-17 12:00:00"))

        // Count remaining entries
        val count = markdownFile.count()
        assertEquals("Should have 2 entries remaining", 2, count)
    }

    @Test
    fun testDeleteEntry_firstEntry() {
        // Arrange
        val timestampToDelete = "2026-04-17 10:00:00"

        // Act
        val result = markdownFile.deleteEntry(timestampToDelete)

        // Assert
        assertTrue("Delete should succeed", result)

        val content = testFile.readText()
        assertFalse("Deleted entry should not exist", content.contains(timestampToDelete))
        assertTrue("Second entry should still exist", content.contains("2026-04-17 11:00:00"))

        val count = markdownFile.count()
        assertEquals("Should have 2 entries remaining", 2, count)
    }

    @Test
    fun testDeleteEntry_lastEntry() {
        // Arrange
        val timestampToDelete = "2026-04-17 12:00:00"

        // Act
        val result = markdownFile.deleteEntry(timestampToDelete)

        // Assert
        assertTrue("Delete should succeed", result)

        val content = testFile.readText()
        assertFalse("Deleted entry should not exist", content.contains(timestampToDelete))
        assertTrue("First entry should still exist", content.contains("2026-04-17 10:00:00"))

        val count = markdownFile.count()
        assertEquals("Should have 2 entries remaining", 2, count)
    }

    @Test
    fun testDeleteEntry_nonExistentTimestamp() {
        // Arrange
        val nonExistentTimestamp = "2026-12-31 23:59:59"

        // Act
        val result = markdownFile.deleteEntry(nonExistentTimestamp)

        // Assert
        assertFalse("Delete should fail for non-existent timestamp", result)

        val count = markdownFile.count()
        assertEquals("All entries should still exist", 3, count)
    }

    @Test
    fun testDeleteEntry_consecutiveDeletes() {
        // Act - Delete two entries in sequence
        val result1 = markdownFile.deleteEntry("2026-04-17 10:00:00")
        val result2 = markdownFile.deleteEntry("2026-04-17 12:00:00")

        // Assert
        assertTrue("First delete should succeed", result1)
        assertTrue("Second delete should succeed", result2)

        val content = testFile.readText()
        assertFalse("First deleted entry should not exist", content.contains("2026-04-17 10:00:00"))
        assertFalse("Second deleted entry should not exist", content.contains("2026-04-17 12:00:00"))
        assertTrue("Remaining entry should still exist", content.contains("2026-04-17 11:00:00"))

        val count = markdownFile.count()
        assertEquals("Should have 1 entry remaining", 1, count)
    }

    @Test
    fun testDeleteEntry_withEmptyEntries() {
        // Arrange - Create file with empty entry (timestamp only, no content)
        val contentWithEmpty = """
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

        testFile.writeText(contentWithEmpty)

        // Act - Delete the entry with content
        val result = markdownFile.deleteEntry("2026-04-17 10:00:00")

        // Assert
        assertTrue("Delete should succeed", result)

        val content = testFile.readText()
        assertFalse("Deleted entry should not exist", content.contains("2026-04-17 10:00:00"))
        // Note: count() counts all timestamp lines (including empty ones)
        assertEquals("Should have 2 entries remaining (including empty one)", 2, markdownFile.count())
    }

    @Test
    fun testDeleteEntry_removesSeparator() {
        // Arrange
        val timestampToDelete = "2026-04-17 11:00:00"

        // Act
        val result = markdownFile.deleteEntry(timestampToDelete)

        // Assert
        assertTrue("Delete should succeed", result)

        val content = testFile.readText()
        // The separator before the deleted entry should also be removed
        val separatorCount = content.lines().count { it == "---" }
        assertEquals("Should have 2 separators remaining (header and one between entries)", 2, separatorCount)
    }

    @Test
    fun testDeleteEntry_onlyOneEntry() {
        // Arrange - File with only one entry
        val singleEntryContent = """
# Appendo

---
## 2026-04-17 10:00:00

Only entry

---
""".trimIndent()

        testFile.writeText(singleEntryContent)

        // Act
        val result = markdownFile.deleteEntry("2026-04-17 10:00:00")

        // Assert
        assertTrue("Delete should succeed", result)

        val content = testFile.readText()
        assertFalse("Entry should not exist", content.contains("2026-04-17 10:00:00"))
        assertFalse("Content should not exist", content.contains("Only entry"))
        assertEquals("Should have 0 entries", 0, markdownFile.count())
    }

    @Test
    fun testDeleteEntry_lastEntry_boundaryTest() {
        // Arrange - Create fresh file
        val freshContent = """
# Appendo

---
## 2026-04-17 10:00:00

Entry 1

---
## 2026-04-17 11:00:00

Entry 2

---
## 2026-04-17 12:00:00

Entry 3 (LAST)

---
""".trimIndent()

        testFile.writeText(freshContent)

        // Act - Delete the last entry
        val result = markdownFile.deleteEntry("2026-04-17 12:00:00")

        // Assert
        assertTrue("Delete should succeed", result)

        val content = testFile.readText()
        assertFalse("Last entry should be deleted", content.contains("2026-04-17 12:00:00"))
        assertFalse("Last entry content should be deleted", content.contains("Entry 3 (LAST)"))
        assertTrue("First entry should still exist", content.contains("2026-04-17 10:00:00"))
        assertTrue("Second entry should still exist", content.contains("2026-04-17 11:00:00"))
        assertEquals("Should have 2 entries remaining", 2, markdownFile.count())
    }
}

/**
 * Test implementation of FileBasedMarkdownFile that doesn't require Context.
 * Used for unit testing file operations without Android dependencies.
 */
private class TestFileBasedMarkdownFile(private val file: File) : MarkdownFileOperations {
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

                // Find the entry with matching timestamp
                var targetStartIndex = -1
                var targetEndIndex = lines.size
                var currentTimestamp = ""

                for (i in lines.indices) {
                    val line = lines[i]
                    when {
                        line.matches(Regex("^## \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$")) -> {
                            // Save previous entry if we found the target
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

                // Remove the entry (including its separator before it)
                val deleteFrom = if (targetStartIndex > 0 && lines[targetStartIndex - 1].matches(Regex("^---$"))) {
                    targetStartIndex - 1
                } else {
                    targetStartIndex
                }

                lines.subList(deleteFrom, targetEndIndex).clear()

                // Write back
                file.writeText(lines.joinToString("\n"))
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
