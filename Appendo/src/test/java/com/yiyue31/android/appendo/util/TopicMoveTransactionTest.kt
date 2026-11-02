package com.yiyue31.android.appendo.util

import android.content.Context
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.mockito.kotlin.mock
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * TopicMoveTransaction 组合事务测试（T-03，设计 §10.2 第一行 ①~⑬）：纯 JVM。
 *
 * 构造沿 FileBasedMarkdownFileTest 先例：mock Context（FileBasedMarkdownFile 不使用 context，
 * 仅存引用）＋真实临时目录文件，atomicWrite / deleteEntries / move 全走真实 FS 与真实全局锁。
 *
 * Windows 注入适配（T-02 已确立先例，照抄）：
 * - 读失败注入＝把路径占为目录（`file.mkdirs()`）而非 `setReadable(false)`——Windows 上
 *   File 权限 API 对 JDK 读取拦截不可靠，路径为目录时 FileInputStream 必抛异常；
 * - 写失败注入＝打**文件** `setWritable(false)`（目录只读在 NTFS 不阻断 createTempFile；
 *   renameTo 只读目标被拒 + fallback 直接覆写被拒 → atomicWrite false）。POSIX 上 rename
 *   可替换只读目标、此注入不生效——POSIX 差异与真机目录级 chmod（89(c)）另行覆盖。
 */
class TopicMoveTransactionTest {

    private val context: Context = mock()
    private lateinit var sourceFile: File
    private lateinit var targetFile: File
    private lateinit var source: FileBasedMarkdownFile
    private lateinit var target: FileBasedMarkdownFile

    @Before
    fun setup() {
        sourceFile = File.createTempFile("appendo-move-src-", ".md")
        sourceFile.delete() // 起始不存在
        source = FileBasedMarkdownFile(context, sourceFile)
        targetFile = File.createTempFile("appendo-move-tgt-", ".md")
        targetFile.delete() // 起始不存在
        target = FileBasedMarkdownFile(context, targetFile)
    }

    @After
    fun teardown() {
        sourceFile.delete() // 路径被占为目录的用例：删空目录
        targetFile.delete()
    }

    /** 造源文件：initHeader 后按给定 (ts, content) 追加，返回 parse 产物（权威条目集）。 */
    private fun buildSource(vararg entries: Pair<String, String>): List<ParsedEntry> {
        assertTrue(source.initHeader())
        entries.forEach { (ts, c) -> assertTrue(source.appendEntry(ts, c)) }
        return EntryParser.parse(source.readAll())
    }

    private fun keysOf(entries: List<ParsedEntry>): Set<EntryKey> =
        entries.map { EntryKey.of(it) }.toSet()

    // ==================== ① 成功：升序、时间戳与内容原样 ====================

    @Test
    fun move_success_targetAscending_originalTimestampsAndContentPreserved() {
        val sourceEntries = buildSource(
            "2026-07-20 10:00:00.000" to "later",
            "2020-01-01 00:00:00" to "seconds-level", // 秒级历史条目
            "2026-07-20 09:00:00.000" to "earlier"
        )
        assertTrue(target.initHeader())
        assertTrue(target.appendEntry("2019-01-01 00:00:00.000", "existing"))

        val result = TopicMoveTransaction.move(source, target, keysOf(sourceEntries))

        assertEquals(MoveResult.Success(moved = 3, skippedExisting = 0, missingFromSource = 0), result)
        val targetEntries = EntryParser.parse(target.readAll())
        assertEquals("原有 1 + 移入 3", 4, targetEntries.size)
        // 目标按时间戳升序含所选（字符串升序 = 时间升序）
        assertEquals(
            listOf(
                "2019-01-01 00:00:00.000",
                "2020-01-01 00:00:00",
                "2026-07-20 09:00:00.000",
                "2026-07-20 10:00:00.000"
            ),
            targetEntries.map { it.rawTimestamp }
        )
        // 时间戳与内容原样（秒级条目原样——不补 .000）
        val secondsEntry = targetEntries.first { it.rawTimestamp == "2020-01-01 00:00:00" }
        assertEquals("seconds-level", secondsEntry.content)
        assertEquals("源中所选全删（归一化为文件头）", 0, EntryParser.parse(source.readAll()).size)
    }

    // ==================== ② 目标部分已存在：不重复写、源全删、计数正确 ====================

    @Test
    fun move_targetPartiallyExists_noDuplicateWrite_sourceFullyDeleted() {
        val sourceEntries = buildSource(
            "2026-07-20 09:00:00.000" to "a",
            "2026-07-20 10:00:00.000" to "b",
            "2026-07-20 11:00:00.000" to "c"
        )
        assertTrue(target.initHeader())
        assertTrue(target.appendEntry("2026-07-20 09:00:00.000", "a")) // 已存在 2 条之一
        assertTrue(target.appendEntry("2026-07-20 10:00:00.000", "b")) // 之二

        val result = TopicMoveTransaction.move(source, target, keysOf(sourceEntries))

        assertEquals(MoveResult.Success(moved = 1, skippedExisting = 2, missingFromSource = 0), result)
        val targetEntries = EntryParser.parse(target.readAll())
        assertEquals("不重复写入：3 键各一条", 3, targetEntries.size)
        assertEquals("目标 key 无重复", 3, targetEntries.map { EntryKey.of(it) }.toSet().size)
        assertEquals("源全删", 0, EntryParser.parse(source.readAll()).size)
    }

    // ==================== ③ N=0：全部 skipped、源全删 ====================

    @Test
    fun move_allAlreadyInTarget_successNZero_sourceFullyDeleted() {
        val sourceEntries = buildSource(
            "2026-07-20 09:00:00.000" to "a",
            "2026-07-20 10:00:00.000" to "b"
        )
        assertTrue(target.initHeader())
        sourceEntries.forEach { assertTrue(target.appendEntry(it.rawTimestamp, it.content)) }
        val targetBefore = targetFile.readText()

        val result = TopicMoveTransaction.move(source, target, keysOf(sourceEntries))

        assertEquals("N=0 全 skipped 是 Success 语义（87：无失败读感）", MoveResult.Success(0, 2, 0), result)
        assertEquals("目标逐字节不变（toAdd 空＝免写）", targetBefore, targetFile.readText())
        assertEquals("源全删", 0, EntryParser.parse(source.readAll()).size)
    }

    // ==================== ④ 写入段注入失败：源逐字节原样 ====================

    @Test
    fun move_injectedTargetWriteFailure_sourceAndTargetByteIdentical() {
        buildSource("2026-07-20 09:00:00.000" to "a")
        assertTrue(target.initHeader())
        val sourceBefore = sourceFile.readBytes()
        val targetBefore = targetFile.readBytes()

        val result = TopicMoveTransaction.move(
            source, target,
            setOf(EntryKey("2026-07-20 09:00:00.000", "a")),
            MoveFaults.FAIL_TARGET_WRITE
        )

        assertEquals(MoveResult.TargetWriteFailed, result)
        assertArrayEquals("源原样（逐字节对比）", sourceBefore, sourceFile.readBytes())
        assertArrayEquals("目标不写", targetBefore, targetFile.readBytes())
    }

    // ==================== ⑤ 删除段注入失败：目标已写全（"两边都有"） ====================

    @Test
    fun move_injectedSourceDeleteFailure_targetFullyWritten_bothHaveEntries() {
        val sourceEntries = buildSource("2026-07-20 09:00:00.000" to "a")
        assertTrue(target.initHeader())

        val result = TopicMoveTransaction.move(source, target, keysOf(sourceEntries), MoveFaults.FAIL_SOURCE_DELETE)

        assertEquals(MoveResult.SourceDeleteFailed(moved = 1, skippedExisting = 0, missingFromSource = 0), result)
        assertEquals("目标已写全（两边都有）", 1, EntryParser.parse(target.readAll()).size)
        assertEquals("源未删（两边都有）", 1, EntryParser.parse(source.readAll()).size)
    }

    // ==================== ⑥ 幂等收敛：⑤ 态后重跑同 keys ====================

    @Test
    fun move_retryAfterSourceDeleteFailure_convergesIdempotently() {
        val sourceEntries = buildSource(
            "2026-07-20 09:00:00.000" to "a",
            "2026-07-20 10:00:00.000" to "b"
        )
        assertTrue(target.initHeader())
        val keys = keysOf(sourceEntries)
        assertEquals(
            MoveResult.SourceDeleteFailed(2, 0, 0),
            TopicMoveTransaction.move(source, target, keys, MoveFaults.FAIL_SOURCE_DELETE)
        )

        // ⑤ 态（两边都有）后重跑同 keys（无注入）→ 幂等收敛
        val retry = TopicMoveTransaction.move(source, target, keys)

        assertEquals(MoveResult.Success(0, 2, 0), retry)
        assertEquals("源清空", 0, EntryParser.parse(source.readAll()).size)
        val targetEntries = EntryParser.parse(target.readAll())
        assertEquals("目标无重复（2 键各一条）", 2, targetEntries.size)
        assertEquals(2, targetEntries.map { EntryKey.of(it) }.toSet().size)
    }

    // ==================== ⑦ 并发终态判据：move＋append 双线程多轮 ====================

    @Test
    fun move_concurrentWithAppend_finalStateConsistent() {
        val sourceEntries = buildSource(
            "2026-07-20 09:00:00.000" to "初始-1",
            "2026-07-20 10:00:00.000" to "初始-2",
            "2026-07-20 11:00:00.000" to "初始-3"
        )
        assertTrue(target.initHeader())
        val keys = keysOf(sourceEntries)
        val appendCount = 25
        val moveRounds = 8
        val errors = ConcurrentLinkedQueue<String>()

        val moveThread = Thread {
            repeat(moveRounds) {
                val r = TopicMoveTransaction.move(source, target, keys)
                // 每轮事务原子成立（84(a) 互斥）；轮内是 Success(3,0,0)/Success(0,·,·) 取决于交错
                // ——弃时序断言（锁公平性/调度顺序非契约），只记录失败
                if (r !is MoveResult.Success) errors.add("move 轮次失败: $r")
            }
        }
        val appendThread = Thread {
            repeat(appendCount) { i ->
                if (!source.append("并发条目$i")) errors.add("append $i 失败")
            }
        }
        moveThread.start()
        appendThread.start()
        moveThread.join()
        appendThread.join()

        assertTrue("并发期间无失败轮次：$errors", errors.isEmpty())

        // 终态一致（弃时序断言）：源条目集 = 期望（初始 3 条已移走、并发 append 的条目全部在源）
        val srcEntries = EntryParser.parse(source.readAll())
        assertEquals("并发 append 的条目全部在源", appendCount, srcEntries.size)
        assertEquals(
            "源条目集恰为 append 集（无丢失/无重复/初始条目已移走）",
            (0 until appendCount).map { "并发条目$it" }.toSet(),
            srcEntries.map { it.content }.toSet()
        )
        // 目标无重复无丢失：恰含初始 3 键
        val tgtEntries = EntryParser.parse(target.readAll())
        assertEquals("目标无重复", 3, tgtEntries.size)
        assertEquals("目标无丢失（恰含初始 3 键）", keys, tgtEntries.map { EntryKey.of(it) }.toSet())
    }

    // ==================== ⑧ 源读失败 → SourceReadFailed 零副作用 ====================

    @Test
    fun move_sourceReadFailure_cleanFailureZeroSideEffect() {
        buildSource("2026-07-20 09:00:00.000" to "a")
        assertTrue(target.initHeader())
        assertTrue(target.appendEntry("2019-01-01 00:00:00.000", "existing"))
        val targetBefore = targetFile.readBytes()
        // 读失败注入（Windows 适配，T-02 先例）：路径占为目录——读取必抛异常
        // （等价"源文件 setReadable(false)"意图；POSIX 差异见文件头注释）
        sourceFile.delete()
        assertTrue("读失败注入：源路径占为目录", sourceFile.mkdirs())

        val result = TopicMoveTransaction.move(source, target, setOf(EntryKey("2026-07-20 09:00:00.000", "a")))

        assertEquals(MoveResult.SourceReadFailed, result)
        assertArrayEquals("零副作用：目标逐字节不变", targetBefore, targetFile.readBytes())
        assertTrue("源（目录）无写入痕迹", sourceFile.listFiles()!!.isEmpty())
    }

    // ==================== ⑨ 目标不存在（懒创建兜底）→ FILE_HEADER 起步 ====================

    @Test
    fun move_targetNotExists_lazyCreateStartingFromFileHeader() {
        val sourceEntries = buildSource("2026-07-20 09:00:00.000" to "a")
        // target 不 initHeader——保持不存在，验懒创建兜底

        val result = TopicMoveTransaction.move(source, target, keysOf(sourceEntries))

        assertEquals(MoveResult.Success(1, 0, 0), result)
        val targetContent = target.readAll()
        assertTrue(
            "以 FILE_HEADER 起步",
            targetContent.startsWith(MarkdownFormatter.FILE_HEADER)
        )
        val targetEntries = EntryParser.parse(targetContent)
        assertEquals(1, targetEntries.size)
        assertEquals("2026-07-20 09:00:00.000", targetEntries[0].rawTimestamp)
        assertEquals("a", targetEntries[0].content)
    }

    // ==================== ⑩ 目标读失败 → TargetReadFailed 源未动 ====================

    @Test
    fun move_targetReadFailure_sourceUntouched() {
        buildSource("2026-07-20 09:00:00.000" to "a")
        val sourceBefore = sourceFile.readBytes()
        // 目标读失败注入（Windows 适配，同 ⑧ mkdirs 法；等价 chmod 000 / setReadable(false)）
        assertTrue("目标路径占为目录", targetFile.mkdirs())

        val result = TopicMoveTransaction.move(source, target, setOf(EntryKey("2026-07-20 09:00:00.000", "a")))

        assertEquals(MoveResult.TargetReadFailed, result)
        assertArrayEquals("源未动（逐字节对比）", sourceBefore, sourceFile.readBytes())
        assertTrue("目标（目录）无写入痕迹", targetFile.listFiles()!!.isEmpty())
    }

    // ==================== ⑪ 真实 IO 失败打在 deleteEntries 实现上（非注入） ====================

    @Test
    fun move_realIoFailureOnSourceDeleteEntries_mapsSourceDeleteFailed() {
        val sourceEntries = buildSource(
            "2026-07-20 09:00:00.000" to "a",
            "2026-07-20 10:00:00.000" to "b"
        )
        assertTrue(target.initHeader())
        val keys = keysOf(sourceEntries)
        // 真实 IO 失败（非 faults 注入）：源文件置只读——deleteEntries 内 atomicWrite
        // （renameTo 只读目标被拒 + fallback 直接覆写被拒）返 null（T-02 先例：Windows 打**文件**
        // 而非目录——目录只读在 NTFS 不阻断 createTempFile；POSIX 上 rename 可替换只读目标、
        // 注入不生效——真机 89(c) 目录级 chmod 另行覆盖）
        assumeTrue("本环境可将文件置只读", sourceFile.setWritable(false))
        try {
            // 直接打在实现上复核：deleteEntries 返回 null（失败）而非 0
            assertNull("deleteEntries 实现返 null（操作未发生）", source.deleteEntries(keys))
            // move 捕获该 null 并映射 SourceDeleteFailed
            val result = TopicMoveTransaction.move(source, target, keys)
            assertEquals(MoveResult.SourceDeleteFailed(moved = 2, skippedExisting = 0, missingFromSource = 0), result)
            assertEquals("两边都有：目标已写全", 2, EntryParser.parse(target.readAll()).size)
            assertEquals("源未删（写失败注入下删除未发生）", 2, EntryParser.parse(source.readAll()).size)
        } finally {
            sourceFile.setWritable(true) // 恢复权限（teardown 需可删）
        }
    }

    // ==================== ⑫ missing>0：N = moved + skipped + missing 口径钉死 ====================

    @Test
    fun move_keysIncludeMissingFromSource_countFormulaHolds() {
        val sourceEntries = buildSource(
            "2026-07-20 09:00:00.000" to "a",
            "2026-07-20 10:00:00.000" to "b"
        )
        assertTrue(target.initHeader())
        val ghost = EntryKey("1999-01-01 00:00:00.000", "ghost") // 源中不存在项
        val keys = keysOf(sourceEntries) + ghost

        val result = TopicMoveTransaction.move(source, target, keys)

        assertEquals(MoveResult.Success(moved = 2, skippedExisting = 0, missingFromSource = 1), result)
        val r = result as MoveResult.Success
        assertEquals(
            "N = moved + skipped + missing 口径钉死（88）",
            keys.size,
            r.moved + r.skippedExisting + r.missingFromSource
        )
        assertEquals("源实有条目全删（ghost 无从删）", 0, EntryParser.parse(source.readAll()).size)
        assertEquals("目标只收实有条目", 2, EntryParser.parse(target.readAll()).size)
    }

    // ==================== ⑬ 秒级/毫秒级混合时间戳的 toAdd 拼接序 ====================

    @Test
    fun move_mixedSecondMillisecondTimestamps_toAddConcatenatedInStringAscendingOrder() {
        val sourceEntries = buildSource(
            "2020-01-01 00:00:05.500" to "毫秒-五百",
            "2020-01-01 00:00:05" to "秒级-同秒",
            "2020-01-01 00:00:01" to "秒级-更早",
            "2020-01-01 00:00:05.000" to "毫秒-同秒零"
        )
        assertTrue(target.initHeader())

        val result = TopicMoveTransaction.move(source, target, keysOf(sourceEntries))

        assertEquals(MoveResult.Success(4, 0, 0), result)
        val targetEntries = EntryParser.parse(target.readAll())
        // 字符串升序 = 时间升序；同秒的秒级条目排在其 .000 毫秒形态之前（前缀更短者小）
        assertEquals(
            listOf(
                "2020-01-01 00:00:01",
                "2020-01-01 00:00:05",
                "2020-01-01 00:00:05.000",
                "2020-01-01 00:00:05.500"
            ),
            targetEntries.map { it.rawTimestamp }
        )
        assertEquals(
            listOf("秒级-更早", "秒级-同秒", "毫秒-同秒零", "毫秒-五百"),
            targetEntries.map { it.content }
        )
    }
}
