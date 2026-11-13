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
 * 读失败语义：主文件或某主题读失败→该文件按「无命中」处理（整体返 null 或跳过该主题）——
 * 沿 v1.4 fire 自愈口径（设计 §20.1「定位失败＝全库不存在」）；**对账路径的读失败保护
 * （读失败≠空集、本轮跳过）在 OrphanReconciler 承担**，不在此类。
 */
sealed interface EntryLocation {
    /** 条目在主文件。 */
    object MainFile : EntryLocation

    /** 条目仅在某个主题文档。 */
    data class Topic(val file: File) : EntryLocation
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

    // ==================== 生产入口（整体单锁） ====================

    /** 定位条目所在（95(c) 深链用）；全库无或读失败→null。 */
    fun locateEntry(context: Context, ts: String): EntryLocation? =
        locateEntryWithContent(context, ts)?.first

    /** 定位并取条目内容（95(b) 通知正文用——fire/补发共用）；全库无或读失败→null。 */
    fun locateEntryContent(context: Context, ts: String): String? =
        locateEntryWithContent(context, ts)?.second

    /** 单遍全库索引（补发循环先建后查，需求 95(b) 性能前提——替代逐条全量重读）。 */
    fun buildIndex(context: Context): Map<String, Pair<EntryLocation, String>> =
        synchronized(FileOperationLock) {
            buildIndexFromContents(readMainLocked(context), readTopicsLocked(context))
        }

    private fun locateEntryWithContent(context: Context, ts: String): Pair<EntryLocation, String>? =
        synchronized(FileOperationLock) {
            locateInContents(readMainLocked(context), readTopicsLocked(context), ts)
        }

    /** 主文件读取（锁内重入）：读失败→null（＝该文件无命中，见类 KDoc 读失败语义）。 */
    private fun readMainLocked(context: Context): String? {
        val md: MarkdownFileOperations = runCatching {
            val repo = FileRepository(context)
            MarkdownFileFactory.create(context, repo.isUsingSAF(), repo.getFileUri(), repo.getDefaultFile())
        }.getOrNull() ?: return null
        val result = runCatching { md.readAllWithStatus() }.getOrNull() ?: return null
        return if (result.failed) null else result.content
    }

    /** 各主题读取（锁内重入，listTopics() 序）：读失败的主题跳过（见类 KDoc 读失败语义）。 */
    private fun readTopicsLocked(context: Context): List<Pair<File, String>> =
        TopicRepository(context).listTopics().mapNotNull { topic ->
            runCatching { topic.file.readText() }.getOrNull()?.let { topic.file to it }
        }
}
