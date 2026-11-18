package com.yiyue31.android.appendo.data

import android.content.Context
import android.content.SharedPreferences
import com.yiyue31.android.appendo.util.MarkdownFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

/**
 * WB-1 归档命名/解析 JVM 测试：尾部锚定三类形态（旧式/新式/同秒后缀）＋主题名含下划线/纯数字
 * 边界＋后缀件可见可恢复＋getArchiveFile 同秒递增不覆盖（上限放弃）。
 */
class ArchiveNameParseTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = mock()

    private fun repo(): ArchiveRepository {
        whenever(context.getExternalFilesDir(null)).thenReturn(tmp.root)
        return ArchiveRepository(context)
    }

    private fun write(name: String): File {
        val f = File(tmp.root, name)
        f.writeText(MarkdownFormatter.FILE_HEADER + "\n## 2026-01-01 10:00:00.000\n\n内容\n")
        return f
    }

    @Test
    fun oldForm_sourceTopicNull() {
        val f = write("Appendo_20260417_143022.md")
        val a = repo().archiveFileFor(f)
        assertNotNull(a)
        assertNull(a!!.sourceTopic)
    }

    @Test
    fun newForm_sourceTopicExtracted() {
        val f = write("Appendo_购物清单_20260417_143022.md")
        val a = repo().archiveFileFor(f)
        assertNotNull(a)
        assertEquals("购物清单", a!!.sourceTopic)
    }

    @Test
    fun newForm_topicWithUnderscore_tailAnchored() {
        val f = write("Appendo_note_2024_20260417_143022.md")
        val a = repo().archiveFileFor(f)
        assertNotNull(a)
        assertEquals("note_2024", a!!.sourceTopic)
    }

    @Test
    fun newForm_numericTopic_notMisreadAsOld() {
        val f = write("Appendo_123_20260417_143022.md")
        val a = repo().archiveFileFor(f)
        assertNotNull(a)
        assertEquals("123", a!!.sourceTopic)
    }

    @Test
    fun suffixForm_oldAndNew_visible() {
        val old = write("Appendo_20260417_143022_1.md")
        val new = write("Appendo_购物_20260417_143022_2.md")
        val r = repo()
        assertNotNull("旧式后缀件不被剔除", r.archiveFileFor(old))
        val na = r.archiveFileFor(new)
        assertNotNull("新式后缀件不被剔除", na)
        assertEquals("购物", na!!.sourceTopic)
    }

    @Test
    fun listIncludesSuffixAndNewForms() {
        write("Appendo_20260417_143022.md")
        write("Appendo_购物_20260417_143022_1.md")
        val list = repo().listArchiveFiles()
        assertEquals(2, list.size)
        assertTrue(list.any { it.sourceTopic == "购物" })
    }

    @Test
    fun nonArchiveName_rejected() {
        val f = write("random.md")
        assertNull(repo().archiveFileFor(f))
    }

    @Test
    fun getArchiveFile_sameSecond_suffixNoOverwrite() {
        whenever(context.getExternalFilesDir(null)).thenReturn(tmp.root)
        whenever(context.getSharedPreferences(org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(mock<SharedPreferences>())
        val fr = FileRepository(context)
        val first = fr.getArchiveFile()
        assertNotNull(first)
        first!!.writeText("x") // 占位使第二件同秒冲突
        val second = fr.getArchiveFile()
        assertNotNull(second)
        assertTrue("同秒第二件带 _1 后缀", second!!.name.endsWith("_1.md"))
        assertTrue(first.name != second.name)
    }

    @Test
    fun getArchiveFile_topicVariant_namedWithTopic() {
        whenever(context.getExternalFilesDir(null)).thenReturn(tmp.root)
        whenever(context.getSharedPreferences(org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(mock<SharedPreferences>())
        val fr = FileRepository(context)
        val f = fr.getArchiveFile("购物清单")
        assertNotNull(f)
        assertTrue(f!!.name.startsWith("Appendo_购物清单_"))
    }
}
