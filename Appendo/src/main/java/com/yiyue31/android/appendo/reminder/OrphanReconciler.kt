package com.yiyue31.android.appendo.reminder

import com.yiyue31.android.appendo.data.TopicRepository
import com.yiyue31.android.appendo.util.EntryParser
import com.yiyue31.android.appendo.util.FileOperationLock
import com.yiyue31.android.appendo.util.MarkdownFileOperations
import com.yiyue31.android.appendo.util.ReminderLogic

/**
 * 全库孤儿提醒对账（戊批次 T-23，需求 95(a)，设计 §20.2）——判定域扩为主文件＋全部主题：
 * 仅当某 ts **全库**再无条目（主无＋所有主题无）才是真孤儿；任一主题仍有该 ts 则提醒保留
 * （101(i)；v1.4「主列表消失即杀」口径退役）。
 *
 * - **放 reminder 包**（设计 §19.2 评审 A5）：依赖 data 层 TopicRepository，util 不得依赖 data；
 * - 参数注入（JVM 可测，评审 T2）：[mainFile] 为主文件实现（FileBased 或 SAF 均可——经
 *   [MarkdownFileOperations] 接口），[topicRepo] 的候选目录由构造器注入控制；
 * - **单锁扫描**（§20.2 评审 A1）：单次 [FileOperationLock] 内完成「读主文件＋主题 ts 并集」
 *   （synchronized 可重入，allTopicTimestampsLocked 持锁进入）；**自读主文件、不接受调用方
 *   传入的 mainTs**——调用方（轮询）的展示读在锁外，复用其结果则「调用方读与锁内主题读」
 *   之间可夹入事务，撕裂面复发（评审 E8 的省读建议据此否决）；
 * - **读失败→null＝本轮整体跳过**（读失败≠空集，沿需求 39/K7 纪律）：主文件读失败（异常/
 *   failed 标志——含 SAF 授权失效）或任一需重读的主题文件失败均返 null，调用方不得做任何
 *   cancel/remove；
 * - 孤儿集返调用方，**cancel/remove 在锁外执行**（设计 §19.2——调度器调用不占文件锁）。
 *
 * 主题 ts 并集消费 [TopicRepository.allTopicTimestampsLocked]（mtime 自愈缓存——轮询高频
 * 路径主题侧零 I/O；统一失效规则第一道防线见 executeMove 成功分支 invalidate 调用）。
 */
object OrphanReconciler {

    /**
     * 对账一轮：返回 [reminderKeys] 中全库（主文件＋全部主题）已无条目存在的 ts 集。
     * null＝读失败，本轮跳过（调用方不动作）。空集＝无孤儿。
     */
    fun reconcile(
        mainFile: MarkdownFileOperations,
        topicRepo: TopicRepository,
        reminderKeys: Set<String>
    ): Set<String>? = synchronized(FileOperationLock) {
        if (reminderKeys.isEmpty()) return@synchronized emptySet<String>()
        val mainRead = runCatching { mainFile.readAllWithStatus() }.getOrNull()
            ?: return@synchronized null
        if (mainRead.failed) return@synchronized null
        val mainTs = runCatching {
            EntryParser.parse(mainRead.content).mapTo(mutableSetOf()) { it.rawTimestamp }
        }.getOrNull() ?: return@synchronized null
        val topicTs = topicRepo.allTopicTimestampsLocked() ?: return@synchronized null
        val existing = mainTs + topicTs.values.flatten()
        ReminderLogic.findOrphans(reminderKeys, existing)
    }
}
