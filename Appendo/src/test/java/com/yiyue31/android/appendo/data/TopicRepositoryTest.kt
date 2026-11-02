package com.yiyue31.android.appendo.data

import android.content.Context
import android.content.SharedPreferences
import com.yiyue31.android.appendo.util.EntryParser
import com.yiyue31.android.appendo.util.MarkdownFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.io.RandomAccessFile

/**
 * TopicRepository 单测（设计 §10.2 第三行 ①~⑧，T-04）：纯 JVM。
 *
 * 注入方式（TD-003 / Q2 用户裁定 2026-11-01，不引入 Robolectric）：
 * - baseDir = JUnit TemporaryFolder 临时目录（构造器注入，不 stub getExternalFilesDir）；
 * - SharedPreferences = 手写内存版假实现（见文件末尾 [InMemorySharedPreferences]）；
 * - Context 沿 FileBasedMarkdownFileTest 先例 mock——baseDir 注入后目录解析不触达 mock，
 *   仅「topicsDir 兜底＋双候选合并扫描」用例显式 stub getExternalFilesDir/filesDir。
 *
 * Windows 注入适配（T-02/T-03 先例形态；具体手段按本机双 JVM 实证选定，POSIX 差异均注明）：
 * - ⑥ 写失败注入＝预建 topics 为「同名普通文件」占位父目录路径——atomicWrite 的 createTempFile
 *   对非目录父路径抛 IOException（双 JVM 实证必抛，POSIX 同理），无需平台守卫。原「目录只读/
 *   同名目录占位」配方在本机不可靠（NTFS 目录只读不阻断建文件；Gradle 所用 JBR 21 的 renameTo
 *   能以文件替换空目录）。
 * - ⑦ 删除失败注入＝RandomAccessFile("r") 持开句柄（java.io 打开不带 FILE_SHARE_DELETE，
 *   双 JVM 实证内核级阻删）；POSIX unlink 不受打开句柄影响 → assumeTrue(isWindows) 保护。
 */
class TopicRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = mock()
    private lateinit var prefs: InMemorySharedPreferences
    private lateinit var baseDir: File
    private lateinit var repo: TopicRepository

    @Before
    fun setup() {
        prefs = InMemorySharedPreferences()
        baseDir = tmp.root
        repo = TopicRepository(context, baseDir, prefs)
    }

    // ---------- helpers ----------

    private fun topicsDir(): File = File(baseDir, "topics")

    /** SP key 断言用（与实现的 created_<文件名含.md> 形态一致）。 */
    private fun createdKey(name: String): String = "created_$name.md"

    /** 手工放置主题文件（不经 createEmptyTopic，可控制内容与 mtime）。 */
    private fun putTopic(name: String, content: String, lastModified: Long = 0L): File {
        topicsDir().mkdirs()
        val f = File(topicsDir(), "$name.md")
        f.writeText(content)
        if (lastModified > 0L) f.setLastModified(lastModified)
        return f
    }

    private fun docWithOneEntry(): String =
        MarkdownFormatter.FILE_HEADER + EntryParser.format("2026-01-01 10:00:00.000", "内容")

    // ---------- ① 命名校验全分支 ----------

    @Test
    fun validate_allBranches_fixedOrder() {
        val existing = listOf("t-k")

        // 空名/纯空白（trim 后判空）
        assertEquals(TopicNameValidation.Empty, TopicNameRules.validate("", existing))
        assertEquals(TopicNameValidation.Empty, TopicNameRules.validate("   \t ", existing))

        // 保留字：Appendo 全等 / Appendo_ 前缀，均大小写不敏感（trim 后判定）
        for (reserved in listOf("Appendo", "appendo", "APPENDO", "  Appendo  ", "APPENDO_日记", "appendO_x")) {
            assertEquals("保留字变体应拒绝：$reserved",
                TopicNameValidation.Reserved, TopicNameRules.validate(reserved, existing))
        }

        // 非法字符逐个（内部嵌入：trim 只去两端，不会消化内部字符）
        for (c in listOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', '\u0000', '\n')) {
            assertEquals("非法字符应拒绝：U+%04X".format(c.code),
                TopicNameValidation.IllegalChars, TopicNameRules.validate("a${c}b", existing))
        }

        // 长度边界：50 字符 Ok（trim 后计）、51 拒绝
        assertEquals(TopicNameValidation.Ok("a".repeat(50)), TopicNameRules.validate("a".repeat(50), existing))
        assertEquals("长度按 trim 后计",
            TopicNameValidation.Ok("a".repeat(50)), TopicNameRules.validate("  ${"a".repeat(50)}  ", existing))
        assertEquals(TopicNameValidation.TooLong, TopicNameRules.validate("a".repeat(51), existing))

        // trim 语义：Ok 返回 trimmedName
        assertEquals(TopicNameValidation.Ok("abc"), TopicNameRules.validate("  abc\t", existing))

        // 同名大小写不敏感：t-k 存在时 T-K → Exists（携带已有显示名）
        assertEquals(TopicNameValidation.Exists("t-k"), TopicNameRules.validate("T-K", existing))
    }

    @Test
    fun createEmptyTopic_mapsValidation_toResults() {
        assertEquals(TopicCreateResult.NameEmpty, repo.createEmptyTopic("   "))
        assertEquals(TopicCreateResult.NameReserved, repo.createEmptyTopic("appendO_日记"))
        assertEquals(TopicNameValidation.IllegalChars, TopicNameRules.validate("a/b", emptyList()))
        assertEquals(TopicCreateResult.NameIllegalChars, repo.createEmptyTopic("a*b"))
        assertEquals(TopicCreateResult.NameTooLong, repo.createEmptyTopic("a".repeat(51)))

        assertTrue(repo.createEmptyTopic("t-k") is TopicCreateResult.Created)
        val exists = repo.createEmptyTopic("T-K")
        assertTrue("同名（大小写不敏感）应返回 NameExists", exists is TopicCreateResult.NameExists)
        assertEquals("t-k", (exists as TopicCreateResult.NameExists).existing.displayName)
        assertEquals("同名被拒后不得产生第二个文件", 1, repo.listTopics().size)
    }

    // ---------- ② 创建：.md 附加、FILE_HEADER 落盘、SP 写入 ----------

    @Test
    fun createEmptyTopic_appendsMd_writesHeaderAndSpKey() {
        val result = repo.createEmptyTopic("  T-b ")
        assertTrue(result is TopicCreateResult.Created)
        val file = File(topicsDir(), "T-b.md") // .md 落盘自动附加；名称已 trim；displayName 不含 .md
        assertEquals(file, (result as TopicCreateResult.Created).file)
        assertTrue(file.exists())
        assertEquals(MarkdownFormatter.FILE_HEADER, file.readText())
        assertTrue("SP 应写入 created_<文件名含.md>", prefs.contains(createdKey("T-b")))
        assertTrue("createdAt 应为约当前时刻", prefs.getLong(createdKey("T-b"), -1L) > 0L)
        assertEquals("T-b", repo.listTopics().single().displayName)
    }

    // ---------- ③ 列表 createdAt 倒序＋移入内容变更后不漂移（85(c) 回归） ----------

    @Test
    fun listTopics_createdAtDesc_andStableAcrossContentChange() {
        repo.createEmptyTopic("A")
        repo.createEmptyTopic("B")
        repo.createEmptyTopic("C")
        // 连续创建可能同毫秒 → 手工钉死 createdAt 保证断言确定性
        prefs.edit().putLong(createdKey("A"), 1000L).commit()
        prefs.edit().putLong(createdKey("B"), 2000L).commit()
        prefs.edit().putLong(createdKey("C"), 3000L).commit()

        assertEquals(listOf("C", "B", "A"), repo.listTopics().map { it.displayName })

        // 模拟移入：直接改写 A 文件内容并推进 mtime——createdAt 与排序不得漂移（85(c)）
        val a = File(topicsDir(), "A.md")
        a.writeText(docWithOneEntry())
        a.setLastModified(System.currentTimeMillis() + 123_456L)

        val again = repo.listTopics()
        assertEquals("排序不随内容变更漂移", listOf("C", "B", "A"), again.map { it.displayName })
        assertEquals("createdAt 冻结", 1000L, again.single { it.displayName == "A" }.createdAt)
        assertEquals("条数实时读文件", 1, again.single { it.displayName == "A" }.entryCount)
    }

    // ---------- ④ SP key 丢失 → 首见回写一次 ----------

    @Test
    fun listTopics_missingSpKey_backfillsOnceFromLastModified() {
        val f1 = putTopic("legacy1", MarkdownFormatter.FILE_HEADER, lastModified = 111_000L)
        putTopic("legacy2", MarkdownFormatter.FILE_HEADER, lastModified = 222_000L)

        // 首见：SP key 丢失 → 以 file.lastModified() 回写一次
        val first = repo.listTopics()
        assertEquals("createdAt 倒序", listOf("legacy2", "legacy1"), first.map { it.displayName })
        assertEquals(111_000L, prefs.getLong(createdKey("legacy1"), -1L))
        assertEquals(222_000L, prefs.getLong(createdKey("legacy2"), -1L))

        // 二次读取：即使文件 mtime 再变（移入改内容），值冻结、排序稳定
        f1.setLastModified(999_000L)
        val second = repo.listTopics()
        assertEquals(listOf("legacy2", "legacy1"), second.map { it.displayName })
        assertEquals(111_000L, second.single { it.displayName == "legacy1" }.createdAt)
        assertEquals(111_000L, prefs.getLong(createdKey("legacy1"), -1L))
    }

    // ---------- ⑤ 删除：非空拒绝／空可删（文件＋SP key 同清） ----------

    @Test
    fun deleteTopic_notEmptyRejected_emptyDeletesFileAndSpKey() {
        val full = putTopic("full", docWithOneEntry())
        val fullDoc = repo.findTopic("full")!! // findTopic 同样触发首见回写
        assertEquals(TopicDeleteResult.NotEmpty, repo.deleteTopic(fullDoc))
        assertTrue("非空拒绝后文件保持", full.exists())
        assertTrue("非空拒绝后 SP key 保持", prefs.contains(createdKey("full")))

        val empty = putTopic("empty", MarkdownFormatter.FILE_HEADER)
        val emptyDoc = repo.findTopic("empty")!!
        assertTrue(prefs.contains(createdKey("empty")))
        assertEquals(TopicDeleteResult.Deleted, repo.deleteTopic(emptyDoc))
        assertFalse("文件应删除", empty.exists())
        assertFalse("SP key 应同清", prefs.contains(createdKey("empty")))
    }

    // ---------- ⑥ WriteFailed → 无 created_ key 残留（顺序约束回归） ----------

    @Test
    fun createEmptyTopic_writeFailed_leavesNoCreatedKey() {
        // Windows 注入适配（T-02/T-03 先例形态，注入手段按本机双 JVM 实证更换）：
        // - 「baseDir 只读」在 NTFS 不可靠（目录只读不阻断建文件）；「预建同名目录占位」在 Gradle
        //   所用 JetBrains Runtime 21 上不可靠（实测该运行时 renameTo 能以文件替换空目录，注入失效）；
        // - 等价注入＝预建 topics 为「同名普通文件」占位父目录路径：FileBasedMarkdownFile.atomicWrite
        //   的 File.createTempFile(parent=非目录) 抛 IOException（Oracle 21.0.6 与 JBR 21.0.10 双 JVM
        //   实证必抛），该异常位于 atomicWrite try 之外、由 createEmptyTopic 的 runCatching 捕获——
        //   与目录只读时 createTempFile 抛异常（89(c) 真机注入分支）同一路径。
        // POSIX 差异注记：createTempFile 对非目录父路径同理必败，无需平台守卫。
        assertTrue("topics 路径被普通文件占位", File(baseDir, "topics").createNewFile())

        val result = repo.createEmptyTopic("blocked")

        assertTrue(result is TopicCreateResult.WriteFailed)
        assertFalse("文件写失败不得残留 created_ key（无幽灵主题）", prefs.contains(createdKey("blocked")))
        assertFalse("目标主题文件未创建", File(topicsDir(), "blocked.md").exists())
    }

    // ---------- ⑦ deleteTopic Failed 分支（文件与 SP 均保持） ----------

    @Test
    fun deleteTopic_deleteFails_keepsFileAndSpKey() {
        val f = putTopic("locked", MarkdownFormatter.FILE_HEADER, lastModified = 123_456L)
        repo.listTopics() // 首见回写 SP key
        assertTrue(prefs.contains(createdKey("locked")))

        // Windows 注入适配（T-02/T-03 先例形态，注入手段按本机双 JVM 实证更换）：
        // - setReadOnly 无效：本机 Oracle 21.0.6 与 JBR 21.0.10 的 File.delete() 对只读文件均
        //   「先清只读属性再删」（实测删成功），弃用；
        // - JBR 的流打开带 FILE_SHARE_DELETE，FileInputStream 也阻不了删；
        // - RandomAccessFile("r") 句柄（java.io 打开不带 FILE_SHARE_DELETE）是双 JVM 均稳定的
        //   内核级阻删点：持开句柄期间 File.delete() 返 false，关流即解锁。
        // POSIX 差异注记：POSIX unlink 不受打开句柄影响，此注入无效 → assumeTrue 保护。
        assumeTrue(
            "POSIX 上打开句柄不阻断 unlink，此注入仅 Windows 有效",
            System.getProperty("os.name", "").lowercase().contains("windows")
        )
        RandomAccessFile(f, "r").use { locked ->
            val result = repo.deleteTopic(repo.findTopic("locked")!!)
            assertEquals(TopicDeleteResult.Failed, result)
            assertTrue("失败后文件保持", f.exists())
            assertTrue("失败后 SP key 保持", prefs.contains(createdKey("locked")))
        } // use 关流解锁，TemporaryFolder 清理不受阻
    }

    // ---------- ⑧ 含秒级条目文档 entryCount 正确（TD-010 防复发） ----------

    @Test
    fun listTopics_entryCount_countsSecondLevelTimestamps() {
        putTopic(
            "mixed",
            MarkdownFormatter.FILE_HEADER +
                EntryParser.format("2026-01-01 10:00:00", "秒级条目") + // 秒级（无毫秒）
                EntryParser.format("2026-01-01 10:00:01.123", "毫秒条目") // 毫秒级
        )
        val doc = repo.findTopic("mixed")!!
        assertEquals("宽松正则须同时数到秒级与毫秒级", 2, doc.entryCount)
    }

    // ---------- topicsDir 兜底＋双候选目录合并扫描自愈（§6.5 KDoc 行为钉死） ----------

    @Test
    fun topicsDir_fallbackAndMergeScan_externalCandidateWins() {
        val externalRoot = tmp.newFolder("external-root")
        val internalRoot = tmp.newFolder("internal-root")
        whenever(context.getExternalFilesDir(null)).thenReturn(externalRoot)
        whenever(context.filesDir).thenReturn(internalRoot)
        val prodRepo = TopicRepository(context, baseDir = null, prefs = prefs)

        // baseDir=null：外部可用 → topicsDir = 外部/topics（兜底对齐 getDefaultFile/getArchiveDirectory 先例）
        assertEquals(File(externalRoot, "topics"), prodRepo.topicsDir())

        // 分家现场：外部 topics 有 E.md 与 dup.md，内部 topics 有 dup.md（同名）与 I.md
        File(externalRoot, "topics").mkdirs()
        File(externalRoot, "topics/E.md").writeText(MarkdownFormatter.FILE_HEADER)
        File(externalRoot, "topics/dup.md").writeText(docWithOneEntry())
        File(internalRoot, "topics").mkdirs()
        File(internalRoot, "topics/dup.md").writeText(docWithOneEntry())
        File(internalRoot, "topics/I.md").writeText(MarkdownFormatter.FILE_HEADER)

        val merged = prodRepo.listTopics()
        assertEquals("同名按 displayName 去重后恰 3 个", setOf("E", "dup", "I"), merged.map { it.displayName }.toSet())
        assertEquals("同名冲突外部目录优先", File(externalRoot, "topics/dup.md"), prodRepo.findTopic("dup")!!.file)

        // 外部不可用（分家反向）：topicsDir 兜底内部目录、合并扫描退化为内部单目录——主题不消失
        whenever(context.getExternalFilesDir(null)).thenReturn(null)
        assertEquals(File(internalRoot, "topics"), prodRepo.topicsDir())
        assertEquals(setOf("dup", "I"), prodRepo.listTopics().map { it.displayName }.toSet())
    }
}

/**
 * 手写内存版 SharedPreferences（Q2 用户裁定 2026-11-01：测试注入内存版假实现，不引入 Robolectric）。
 * 覆盖 TopicRepository 用到的 getLong/contains/putLong/remove/commit；其余成员按最简语义实现
 * （本套测试不触达）。非事务性（逐条即时生效）对本被测代码的断言无影响。
 */
private class InMemorySharedPreferences : SharedPreferences {

    private val values = HashMap<String, Any>()

    override fun getAll(): MutableMap<String, *> = HashMap(values)

    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        @Suppress("UNCHECKED_CAST")
        val stored = values[key] as? MutableSet<String>
        return stored ?: defValues
    }

    override fun getInt(key: String?, defValue: Int): Int = (values[key] as? Int) ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = (values[key] as? Long) ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = (values[key] as? Float) ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = (values[key] as? Boolean) ?: defValue

    override fun contains(key: String?): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class Editor : SharedPreferences.Editor {

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null && value != null) values[key] = value
            return this
        }

        override fun putStringSet(key: String?, strings: MutableSet<String>?): SharedPreferences.Editor {
            if (key != null && strings != null) this@InMemorySharedPreferences.values[key] = strings
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) values[key] = value
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) values[key] = value
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) values[key] = value
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) values[key] = value
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            values.remove(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            values.clear()
            return this
        }

        override fun commit(): Boolean = true

        override fun apply() {
            commit()
        }
    }
}
