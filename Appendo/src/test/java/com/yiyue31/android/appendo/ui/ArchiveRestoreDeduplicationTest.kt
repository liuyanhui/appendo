package com.yiyue31.android.appendo.ui

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for archive restore deduplication logic.
 *
 * Root cause of the crash: archive+restore creates duplicate entries with identical timestamps,
 * causing LazyColumn key collision in Compose.
 *
 * These tests verify:
 * 1. parseMarkdownEntries correctly parses content with duplicate entries
 * 2. Deduplication logic prevents duplicate entries from being written
 */
class ArchiveRestoreDeduplicationTest {

    // --- parseMarkdownEntries with duplicates ---

    @Test
    fun testParseEntries_duplicateEntries_parsedCorrectly() {
        val content = """
# Link Collection

---
## 2026-04-18 10:30:45

entry1

---
## 2026-04-18 10:31:20

entry2

---
## 2026-04-18 10:30:45

entry1

---
## 2026-04-18 10:31:20

entry2

---
""".trimIndent()

        val entries = parseMarkdownEntries(content)
        assertEquals("Should parse all 4 entries (including duplicates)", 4, entries.size)
        assertEquals("First entry timestamp", "2026-04-18 10:30:45", entries[0].timestamp)
        assertEquals("Second entry timestamp", "2026-04-18 10:31:20", entries[1].timestamp)
        assertEquals("Third is duplicate of first", "2026-04-18 10:30:45", entries[2].timestamp)
        assertEquals("Fourth is duplicate of second", "2026-04-18 10:31:20", entries[3].timestamp)
    }

    @Test
    fun testParseEntries_sameTimestampDifferentContent_parsedAsSeparate() {
        val content = """
# Link Collection

---
## 2026-04-18 10:30:45

content A

---
## 2026-04-18 10:30:45

content B

---
""".trimIndent()

        val entries = parseMarkdownEntries(content)
        assertEquals("Should parse 2 entries", 2, entries.size)
        assertEquals("First content", "content A", entries[0].content.trim())
        assertEquals("Second content", "content B", entries[1].content.trim())
    }

    // --- Deduplication logic ---

    @Test
    fun testDeduplication_identicalEntries_onlyKeptOnce() {
        val existingEntries = listOf(
            LinkEntry("2026-04-18 10:30:45", "entry1"),
            LinkEntry("2026-04-18 10:31:20", "entry2")
        )
        val archiveEntries = listOf(
            LinkEntry("2026-04-18 10:30:45", "entry1"),
            LinkEntry("2026-04-18 10:31:20", "entry2")
        )

        val existingKeys = existingEntries
            .map { "${it.timestamp}|${it.content}" }
            .toMutableSet()

        val toAppend = archiveEntries.filter { entry ->
            "${entry.timestamp}|${entry.content}" !in existingKeys
        }

        assertTrue("No entries should be appended (all duplicates)", toAppend.isEmpty())
    }

    @Test
    fun testDeduplication_partialOverlap_onlyNewOnesAppended() {
        val existingEntries = listOf(
            LinkEntry("2026-04-18 10:30:45", "entry1"),
            LinkEntry("2026-04-18 10:31:20", "entry2")
        )
        val archiveEntries = listOf(
            LinkEntry("2026-04-18 10:30:45", "entry1"),
            LinkEntry("2026-04-18 10:35:00", "entry3")
        )

        val existingKeys = existingEntries
            .map { "${it.timestamp}|${it.content}" }
            .toMutableSet()

        val toAppend = archiveEntries.filter { entry ->
            "${entry.timestamp}|${entry.content}" !in existingKeys
        }

        assertEquals("Only 1 new entry should be appended", 1, toAppend.size)
        assertEquals("The new entry should be entry3", "entry3", toAppend[0].content)
        assertEquals("The new entry timestamp", "2026-04-18 10:35:00", toAppend[0].timestamp)
    }

    @Test
    fun testDeduplication_allNew_allAppended() {
        val existingEntries = listOf(
            LinkEntry("2026-04-18 10:30:45", "entry1")
        )
        val archiveEntries = listOf(
            LinkEntry("2026-04-18 10:35:00", "entry2"),
            LinkEntry("2026-04-18 10:36:00", "entry3")
        )

        val existingKeys = existingEntries
            .map { "${it.timestamp}|${it.content}" }
            .toMutableSet()

        val toAppend = archiveEntries.filter { entry ->
            "${entry.timestamp}|${entry.content}" !in existingKeys
        }

        assertEquals("All 2 entries should be appended", 2, toAppend.size)
    }

    @Test
    fun testDeduplication_emptyExisting_allArchiveAppended() {
        val existingEntries = emptyList<LinkEntry>()
        val archiveEntries = listOf(
            LinkEntry("2026-04-18 10:30:45", "entry1"),
            LinkEntry("2026-04-18 10:31:20", "entry2")
        )

        val existingKeys = existingEntries
            .map { "${it.timestamp}|${it.content}" }
            .toMutableSet()

        val toAppend = archiveEntries.filter { entry ->
            "${entry.timestamp}|${entry.content}" !in existingKeys
        }

        assertEquals("All archive entries should be appended", 2, toAppend.size)
    }

    @Test
    fun testDeduplication_sameTimestampDifferentContent_notTreatedAsDuplicate() {
        val existingEntries = listOf(
            LinkEntry("2026-04-18 10:30:45", "entry1")
        )
        val archiveEntries = listOf(
            LinkEntry("2026-04-18 10:30:45", "entry1"),
            LinkEntry("2026-04-18 10:30:45", "different content")
        )

        val existingKeys = existingEntries
            .map { "${it.timestamp}|${it.content}" }
            .toMutableSet()

        val toAppend = archiveEntries.filter { entry ->
            "${entry.timestamp}|${entry.content}" !in existingKeys
        }

        assertEquals("Only the truly different entry should be appended", 1, toAppend.size)
        assertEquals("different content", toAppend[0].content)
    }

    // --- Full archive+restore scenario ---

    @Test
    fun testFullScenario_archiveThenRestore_noDuplicates() {
        // Simulate: user adds 2 entries, archives, then restores same archive

        // Step 1: Main file content (after adding 2 entries)
        val mainContent = """
# Link Collection

---
## 2026-04-18 10:30:45

entry1

---
## 2026-04-18 10:31:20

entry2

---
""".trimIndent()

        // Step 2: Archive has same content (archive copies but doesn't clear)
        val archiveContent = mainContent

        // Step 3: Parse both
        val existingEntries = parseMarkdownEntries(mainContent)
        val archiveEntries = parseMarkdownEntries(archiveContent)

        assertEquals("Main should have 2 entries", 2, existingEntries.size)
        assertEquals("Archive should have 2 entries", 2, archiveEntries.size)

        // Step 4: Deduplicate
        val existingKeys = existingEntries
            .map { "${it.timestamp}|${it.content}" }
            .toMutableSet()

        val toAppend = archiveEntries.filter { entry ->
            "${entry.timestamp}|${entry.content}" !in existingKeys
        }

        // Step 5: Verify no duplicates
        assertEquals("No entries should be appended after restore", 0, toAppend.size)
    }

    @Test
    fun testFullScenario_restoreOldArchive_mergesWithoutDuplicates() {
        // Main has 2 recent entries, archive has 1 old entry + 1 duplicate
        val mainContent = """
# Link Collection

---
## 2026-04-18 10:30:45

new entry

---
## 2026-04-18 10:31:20

another new entry

---
""".trimIndent()

        val archiveContent = """
# Link Collection

---
## 2026-04-17 08:00:00

old entry from archive

---
## 2026-04-18 10:30:45

new entry

---
""".trimIndent()

        val existingEntries = parseMarkdownEntries(mainContent)
        val archiveEntries = parseMarkdownEntries(archiveContent)

        val existingKeys = existingEntries
            .map { "${it.timestamp}|${it.content}" }
            .toMutableSet()

        val toAppend = archiveEntries.filter { entry ->
            "${entry.timestamp}|${entry.content}" !in existingKeys
        }

        assertEquals("Only 1 non-duplicate should be appended", 1, toAppend.size)
        assertEquals("old entry from archive", toAppend[0].content.trim())
    }
}
