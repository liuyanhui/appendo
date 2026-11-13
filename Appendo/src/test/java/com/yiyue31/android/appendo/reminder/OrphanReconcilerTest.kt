package com.yiyue31.android.appendo.reminder

import android.content.Context
import com.yiyue31.android.appendo.data.TopicRepository
import com.yiyue31.android.appendo.util.EntryKey
import com.yiyue31.android.appendo.util.EntryParser
import com.yiyue31.android.appendo.util.FileBasedMarkdownFile
import com.yiyue31.android.appendo.util.MarkdownFileOperations
import com.yiyue31.android.appendo.util.MarkdownFormatter
import com.yiyue31.android.appendo.util.MoveResult
import com.yiyue31.android.appendo.util.ReadResult
import com.yiyue31.android.appendo.util.TopicMoveTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import java.io.File
import java.util.Collections

/**
 * OrphanReconciler 单测（戊批次 T-23，设计 §20.2/§25.1）：纯 JVM——三态判定（仅主题存活不杀/
 * 全库消失杀/并存不杀）、读失败 null、并发输出判据（reconcile×双向 move 多轮零误报＋终态一致，
 * 沿 ⑦ 先例弃时序断言）。
 *
 * 注入同 TopicRepositoryTest 先例：baseDir=TemporaryFolder＋内存 SP＋mock Context；
 * 主文件经 [FileBasedMarkdownFile]（FileBasedMarkdownFileTest 先例——context 不触达）。
 */
class OrphanReconcilerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = mock()
    private val prefs = TestPrefs()
    private lateinit var mainFile: File
    private lateinit var md: FileBasedMarkdownFile
    private lateinit var topicRepo: TopicRepository

    @Before
    fun setup() {
        mainFile = tmp.newFile("main.md")
        md = FileBasedMarkdownFile(context, mainFile)
        topicRepo = TopicRepository(context, tmp.root, prefs)
    }

    // ---------- helpers ----------

    private fun writeMain(vararg entries: Pair<String, String>) {
        md.writeAll(entries.fold(MarkdownFormatter.FILE_HEADER) { acc, (ts, text) ->
            acc + EntryParser.format(ts, text)
        })
    }

    private fun writeTopic(name: String, vararg entries: Pair<String, String>) {
        val dir = File(tmp.root, "topics").apply { mkdirs() }
        val f = File(dir, "$name.md")
        f.writeText(entries.fold(MarkdownFormatter.FILE_HEADER) { acc, (ts, text) ->
            acc + EntryParser.format(ts, text)
        })
    }

    /** EntryKey 构造（of 收 ParsedEntry——经 format/parse 往返取规范条目）。 */
    private fun keyOf(ts: String): EntryKey =
        EntryKey.of(EntryParser.parse(EntryParser.format(ts, "往返条目")).first())

    private val ts1 = "2026-01-01 10:00:00.000"
    private val ts2 = "2026-01-02 10:00:00.000"
    private val ts3 = "2026-01-03 10:00:00.000"

    // ---------- 三态判定（95(a)：判定域＝主文件＋全部主题） ----------

    @Test
    fun reconcile_entryOnlyInTopic_notOrphan() {
        writeMain(ts2 to "主文件独有")
        writeTopic("A", ts1 to "主题条目")
        val orphans = OrphanReconciler.reconcile(md, topicRepo, setOf(ts1))
        assertEquals("仅主题存活不杀（101(i)）", emptySet<String>(), orphans)
    }

    @Test
    fun reconcile_entryNowhere_killed() {
        writeMain(ts2 to "主文件独有")
        writeTopic("A", ts1 to "无关条目")
        val orphans = OrphanReconciler.reconcile(md, topicRepo, setOf(ts3))
        assertEquals("全库消失杀", setOf(ts3), orphans)
    }

    @Test
    fun reconcile_entryInMainAndTopic_notOrphan() {
        writeMain(ts1 to "主文件版")
        writeTopic("A", ts1 to "主题版")
        val orphans = OrphanReconciler.reconcile(md, topicRepo, setOf(ts1))
        assertEquals("主＋主题并存不杀", emptySet<String>(), orphans)
    }

    @Test
    fun reconcile_emptyReminderKeys_emptySet() {
        writeMain(ts1 to "条目")
        assertEquals("空 sidecar 键集＝无孤儿（零 I/O 早退）", emptySet<String>(), OrphanReconciler.reconcile(md, topicRepo, emptySet()))
    }

    // ---------- 读失败 → null 本轮跳过 ----------

    @Test
    fun reconcile_mainReadFailed_returnsNull() {
        writeMain(ts1 to "条目")
        writeTopic("A", ts2 to "主题条目")
        val broken = FailingReadMain
        assertNull("主文件读失败（failed 标志）→null 本轮整体跳过", OrphanReconciler.reconcile(broken, topicRepo, setOf(ts3)))
    }

    @Test
    fun reconcile_topicReadFailed_returnsNull() {
        // 读失败注入：POSIX setReadable(false)（Windows 的 JDK 读拦截不可靠——
        // TopicRepositoryTest allTopicTimestamps_readFailure 先例）
        org.junit.Assume.assumeTrue(!System.getProperty("os.name", "").lowercase().contains("windows"))
        writeMain(ts1 to "条目")
        val dir = File(tmp.root, "topics").apply { mkdirs() }
        val topicFile = File(dir, "B.md")
        topicFile.writeText(MarkdownFormatter.FILE_HEADER + EntryParser.format(ts2, "不可读主题"))
        topicFile.setReadable(false)
        try {
            assertNull("主题读失败→null 本轮整体跳过（读失败≠空集）", OrphanReconciler.reconcile(md, topicRepo, setOf(ts3)))
        } finally {
            topicFile.setReadable(true) // 恢复（勿留脏状态影响 fixture 清理）
        }
    }

    /** 主文件读失败假实现：readAllWithStatus 返回 failed=true（覆盖 SAF 授权失效/IO 故障形态）。 */
    private object FailingReadMain : MarkdownFileOperations {
        override fun append(content: String) = error("unused")
        override fun appendReturningTimestamp(content: String) = error("unused")
        override fun readAll() = error("unused")
        override val existsCheckDefinitive: Boolean = false
        override fun readAllWithStatus() = ReadResult(content = "", recovered = false, failed = true)
        override fun clear() = false
        override fun exists() = true
        override fun count() = 0
        override fun initHeader() = false
        override fun deleteEntry(timestamp: String) = false
        override fun deleteEntries(keys: Set<EntryKey>) = null
        override fun updateEntry(timestamp: String, newContent: String) = false
        override fun writeAll(content: String) = false
        override fun appendEntry(timestamp: String, content: String) = false
    }

    // ---------- 并发输出判据（§25.1：撕裂防护＝输出判据，弃时序断言） ----------

    /**
     * reconcile × 双向 move（main↔T 往返）多轮并发：断言 reconcile 每次输出**零误报**
     * （存活 ts 从不出现在孤儿集——条目无论在主文件还是主题，全库判定恒覆盖）＋终态一致
     * （往返收敛后条目回到主文件、reconcile 无孤儿）。
     */
    @Test
    fun reconcile_concurrentWithBidirectionalMoves_zeroFalsePositives_andFinalStateConsistent() {
        writeMain(ts1 to "往返条目", ts2 to "留守条目")
        writeTopic("T", ts3 to "主题固有")
        val topicMd = FileBasedMarkdownFile(context, File(tmp.root, "topics/T.md"))
        val reminderKeys = setOf(ts1, ts2, ts3)
        val allFalsePositives = Collections.synchronizedSet(mutableSetOf<String>())
        val rounds = 30

        val reconcileThread = Thread {
            repeat(rounds) {
                val orphans = OrphanReconciler.reconcile(md, topicRepo, reminderKeys)
                assertNotNull("读失败轮不得当空集用（本用例文件恒可读，防御断言）", orphans)
                // 零误报：三 ts 在全程任一时刻至少存在于主文件或主题之一（move 先写后删），
                // reconcile 单锁全库扫描必须看到这一不变量
                allFalsePositives.addAll(orphans!!)
            }
        }
        val moveThread = Thread {
            repeat(rounds) {
                val toTopic = TopicMoveTransaction.move(md, topicMd, setOf(keyOf(ts1)))
                val back = TopicMoveTransaction.move(topicMd, md, setOf(keyOf(ts1)))
                assertTrue("往返 move 均须成功（无故障注入）", toTopic is MoveResult.Success && back is MoveResult.Success)
            }
        }
        reconcileThread.start()
        moveThread.start()
        reconcileThread.join()
        moveThread.join()

        assertEquals("存活 ts 零误报（reconcile×双向 move 多轮）", emptySet<String>(), allFalsePositives)
        // 终态一致：往返收敛后 ts1 回主文件、全库三 ts 均在、无孤儿
        val finalOrphans = OrphanReconciler.reconcile(md, topicRepo, reminderKeys)
        assertEquals(emptySet<String>(), finalOrphans)
        assertTrue("终态 ts1 回主文件", EntryParser.parse(md.readAll()).any { it.rawTimestamp == ts1 })
    }
}

/** 内存 SP 假实现（沿 TopicRepositoryTest.InMemorySharedPreferences 先例，本文件内复制以保自包含）。 */
private class TestPrefs : android.content.SharedPreferences {
    private val map = HashMap<String, Any>()
    override fun getAll(): Map<String, *> = map
    override fun getString(key: String, defValue: String?): String? = map[key] as? String ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
    override fun getInt(key: String, defValue: Int): Int = map[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = map[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = map[key] as? Float ?: defValue
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = map[key] as? Set<String> ?: defValues
    override fun contains(key: String): Boolean = map.containsKey(key)
    override fun edit(): android.content.SharedPreferences.Editor = object : android.content.SharedPreferences.Editor {
        override fun putString(key: String, value: String?) = apply { if (value != null) map[key] = value else map.remove(key) }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { if (values != null) map[key] = values else map.remove(key) }
        override fun putInt(key: String, value: Int) = apply { map[key] = value }
        override fun putLong(key: String, value: Long) = apply { map[key] = value }
        override fun putFloat(key: String, value: Float) = apply { map[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { map[key] = value }
        override fun remove(key: String) = apply { map.remove(key) }
        override fun clear() = apply { map.clear() }
        override fun commit() = true
        override fun apply() = Unit
    }
    override fun registerOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) = Unit
}
