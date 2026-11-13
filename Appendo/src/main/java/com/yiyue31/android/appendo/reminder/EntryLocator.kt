package com.yiyue31.android.appendo.reminder

import android.content.Context
import com.yiyue31.android.appendo.data.FileRepository
import com.yiyue31.android.appendo.data.TopicRepository
import com.yiyue31.android.appendo.util.EntryParser
import com.yiyue31.android.appendo.util.FileOperationLock
import com.yiyue31.android.appendo.util.MarkdownFileFactory
import com.yiyue31.android.appendo.util.MarkdownFileOperations
import java.io.File

/**
 * 全库条目定位（戊批次 T-22，需求 95(b)(c)，设计 §20.1）——fire / 开机补发 / 深链三路同一解析函数族：
 * **先主文件、后主题兜底；同 ts 两处并存取主文件；多主题并存取 listTopics() 序（createdAt 倒序）
 * 首个＝确定性次序**。
 *
 * 分层：reminder 包（消费方 ReminderAlarmReceiver/ReminderBootReceiver/MainActivity 深链同域）；
 * 依赖 data（FileRepository/TopicRepository）沿 findEntryContent→FileRepository 既有边。
 *
 * 锁纪律：生产入口**整体单锁**（synchronized 可重入）——「主文件读＋各主题读」一次锁内完成，
 * 消除两读之间夹入移出事务的双 miss 误杀面（设计 §20.1/评审 A3）。**不消费 TopicRepository
 * ts 缓存**：本类需要条目内容（缓存只存 ts 集）且 fire/boot/深链为低频路径（缓存专属对账——
 * 设计 §20.1 分工裁定）。
 *
 * 读失败语义（设计 §20.1 修订，2026-11-13 code-review）：定位路径区分三态
 * [LocateOutcome]——**读失败≠不存在**：命中可读文件→Found；全库可读且无→NotFound（可安全
 * 取消/删除）；任一文件不可读且未命中→Unknown（该条目可能就在不可读文件中，消费方不得据此
 * 取消/删除）。深链 [locateEntry] 仍返 null（两种 miss 不区分——导航语义无 destructive 面）。
 * 对账路径的读失败保护（读失败≠空集、本轮跳过）在 OrphanReconciler 承担，不在此类。
 */
sealed interface EntryLocation {
    /** 条目在主文件。 */
    object MainFile : EntryLocation

    /** 条目仅在某个主题文档。 */
    data class Topic(val file: File) : EntryLocation
}

/** 定位结果三态（设计 §20.1 修订）：fire / 补发消费——仅 [NotFound] 允许取消提醒＋删 sidecar。 */
sealed interface LocateOutcome {
    data class Found(val location: EntryLocation, val content: String) : LocateOutcome

    /** 全库可读且无此 ts——条目确已不存在，可安全取消/删除。 */
    object NotFound : LocateOutcome

    /** 有文件不可读且未命中——条目可能在不可读文件中：不通知、不取消、不删（读失败≠不存在）。 */
    object Unknown : LocateOutcome
}

object EntryLocator {

    // ==================== 纯函数核心（无锁无 Context，JVM 直测） ====================

    /**
     * 单遍定位：主文件优先（[mainContent] 为 null＝不可读→跳过），主题按传入序兜底
     * （生产调用方传 listTopics() 序＝确定性次序）。命中返回 (位置, 条目内容)，全库无→null。
     */
    fun locateInContents(
        mainContent: String?,
        topicContents: List<Pair<File, String>>,
        ts: String
    ): Pair<EntryLocation, String>? {
        if (mainContent != null) {
            EntryParser.parse(mainContent).firstOrNull { it.rawTimestamp == ts }
                ?.let { return EntryLocation.MainFile to it.content }
        }
        for ((file, content) in topicContents) {
            EntryParser.parse(content).firstOrNull { it.rawTimestamp == ts }
                ?.let { return EntryLocation.Topic(file) to it.content }
        }
        return null
    }

    /**
     * 单遍建索引（开机补发用，需求 95(b)「ts→内容映射」）：先主题（同 ts 多主题取列表序首个）
     * 、主文件覆盖（主优先）——与 [locateInContents] 同源：同一输入下 `index[ts]` 与
     * `locateInContents(...)` 结果一致（测试钉死）。条目为短文本，boot 路径一次性内存换 I/O。
     */
    fun buildIndexFromContents(
        mainContent: String?,
        topicContents: List<Pair<File, String>>
    ): Map<String, Pair<EntryLocation, String>> {
        val index = LinkedHashMap<String, Pair<EntryLocation, String>>()
        for ((file, content) in topicContents) {
            for (entry in EntryParser.parse(content)) {
                index.putIfAbsent(entry.rawTimestamp, EntryLocation.Topic(file) to entry.content)
            }
        }
        if (mainContent != null) {
            for (entry in EntryParser.parse(mainContent)) {
                index[entry.rawTimestamp] = EntryLocation.MainFile to entry.content
            }
        }
        return index
    }

    /**
     * 三态判定纯函数（JVM 直测）：命中→[LocateOutcome.Found]；miss 且 [anyUnreadable]
     * （主文件或某主题不可读）→[LocateOutcome.Unknown]；miss 且全库可读→[LocateOutcome.NotFound]。
     */
    fun outcomeOf(
        mainContent: String?,
        topicContents: List<Pair<File, String>>,
        anyUnreadable: Boolean,
        ts: String
    ): LocateOutcome {
        val hit = locateInContents(mainContent, topicContents, ts)
        return when {
            hit != null -> LocateOutcome.Found(hit.first, hit.second)
            anyUnreadable -> LocateOutcome.Unknown
            else -> LocateOutcome.NotFound
        }
    }

    // ==================== 生产入口（整体单锁） ====================

    /** 三态定位（fire 消费，设计 §20.1 修订）：[LocateOutcome.Unknown]＝读失败未命中，不取消不删。 */
    fun locateOutcome(context: Context, ts: String): LocateOutcome =
        synchronized(FileOperationLock) {
            val s = readLibraryLocked(context)
            outcomeOf(s.mainContent, s.topicContents, s.anyUnreadable, ts)
        }

    /** 定位条目所在（95(c) 深链用）；全库无或读失败→null（两种 miss 不区分）。 */
    fun locateEntry(context: Context, ts: String): EntryLocation? =
        locateEntryWithContent(context, ts)?.first

    /**
     * 索引＋可读性快照（boot 补发消费，需求 95(b) 性能前提——单遍读全库后查表；设计 §20.1 修订）：
     * [LibraryIndex.anyUnreadable]=true 时索引对未命中 ts **不可信**（不可读文件中的条目全部缺席）
     * ——补发循环不得据 miss 取消/删除。
     */
    fun buildIndexChecked(context: Context): LibraryIndex =
        synchronized(FileOperationLock) {
            val s = readLibraryLocked(context)
            LibraryIndex(buildIndexFromContents(s.mainContent, s.topicContents), s.anyUnreadable)
        }

    /** 全库索引＋可读性标志（[buildIndexChecked] 产物）。 */
    data class LibraryIndex(
        val index: Map<String, Pair<EntryLocation, String>>,
        val anyUnreadable: Boolean
    )

    private fun locateEntryWithContent(context: Context, ts: String): Pair<EntryLocation, String>? =
        synchronized(FileOperationLock) {
            val s = readLibraryLocked(context)
            locateInContents(s.mainContent, s.topicContents, ts)
        }

    /** 全库快照（锁内一次读齐）：null＝该文件不可读（计入 [LibrarySnapshot.anyUnreadable]）。 */
    private class LibrarySnapshot(
        val mainContent: String?,
        val topicContents: List<Pair<File, String>>,
        val anyUnreadable: Boolean
    )

    /** 主文件＋各主题读取（锁内重入，listTopics() 序）：读失败的文件跳过并置 anyUnreadable。 */
    private fun readLibraryLocked(context: Context): LibrarySnapshot {
        var anyUnreadable = false
        val mainContent: String? = runCatching {
            val repo = FileRepository(context)
            val md: MarkdownFileOperations =
                MarkdownFileFactory.create(context, repo.isUsingSAF(), repo.getFileUri(), repo.getDefaultFile())
            val result = md.readAllWithStatus()
            if (result.failed) null else result.content
        }.getOrNull()
        if (mainContent == null) anyUnreadable = true
        val topicContents = ArrayList<Pair<File, String>>()
        for (topic in TopicRepository(context).listTopics()) {
            val content = runCatching { topic.file.readText() }.getOrNull()
            if (content != null) {
                topicContents.add(topic.file to content)
            } else {
                anyUnreadable = true
            }
        }
        return LibrarySnapshot(mainContent, topicContents, anyUnreadable)
    }
}
