package com.yiyue31.android.appendo.data

import android.content.Context
import android.content.SharedPreferences
import com.yiyue31.android.appendo.util.EntryParser
import com.yiyue31.android.appendo.util.FileBasedMarkdownFile
import com.yiyue31.android.appendo.util.MarkdownFormatter
import java.io.File

/**
 * 主题文档（设计 §5/§6.5）：`topics/` 目录成员即主题（正向标识，存储方案 A），
 * 文件格式与主文件/归档同构（FILE_HEADER + 条目模板，§5.1）。
 */
data class TopicDocument(
    /** topics/<name>.md */
    val file: File,
    /** 不含 .md（UI 显示） */
    val displayName: String,
    /** SP 持久化 epoch ms；key 丢失时首见以 file.lastModified() 回写一次（§5.2） */
    val createdAt: Long,
    /** 实时读文件计数（EntryParser.countEntries，宽松正则），不入 SP */
    val entryCount: Int
)

/** 创建结果（§6.5）。[NameExists] 携带既有主题 → 83(c) 同名指路。 */
sealed interface TopicCreateResult {
    data class Created(val file: File) : TopicCreateResult
    data class NameExists(val existing: TopicDocument) : TopicCreateResult
    object NameEmpty : TopicCreateResult
    object NameReserved : TopicCreateResult
    object NameIllegalChars : TopicCreateResult
    object NameTooLong : TopicCreateResult
    object WriteFailed : TopicCreateResult
}

/** 删除结果（§6.5，需求 85(a)）：非空拒绝；0 条可删；删文件失败 [Failed]（文件与 SP key 均保持）。 */
sealed interface TopicDeleteResult {
    object Deleted : TopicDeleteResult
    object NotEmpty : TopicDeleteResult
    object Failed : TopicDeleteResult
}

/** [TopicNameRules.validate] 结果（§5.3）。校验顺序固定：空名→保留字→非法字符→长度→同名检测。 */
sealed interface TopicNameValidation {
    object Empty : TopicNameValidation
    object Reserved : TopicNameValidation
    object IllegalChars : TopicNameValidation
    object TooLong : TopicNameValidation

    /** [displayName] = 已有主题的显示名（原样，非用户输入形态）。 */
    data class Exists(val displayName: String) : TopicNameValidation

    data class Ok(val trimmedName: String) : TopicNameValidation
}

/** 命名校验纯函数（§5.3 规则，纯 JVM 可测）。 */
object TopicNameRules {
    const val MAX_NAME_LENGTH = 50
    private const val RESERVED_EXACT = "Appendo"
    private const val RESERVED_PREFIX = "Appendo_"

    /** Windows 保留 ∪ Linux 非法（\0 由 C0 控制字符判定覆盖）——为 USB/云盘带到 Windows 侧留余地（§5.3）。 */
    private val ILLEGAL_CHARS = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

    /**
     * 校验顺序固定（前者先报）：空名→保留字→非法字符→长度→同名检测。
     * - 空名/纯空白：trim 后判空；[TopicNameValidation.Ok] 返回 trim 后名称（命名场景无保留空白诉求）
     * - 保留字：`Appendo` 全等 / `Appendo_` 前缀，均大小写不敏感
     * - 非法字符：[ILLEGAL_CHARS] ∪ C0 控制字符（Char.isISOControl）
     * - 长度：trim 后 ≤ [MAX_NAME_LENGTH]
     * - 同名：与已有 displayName 大小写不敏感比对（文件系统大小写敏感会造成 `AI.md`/`ai.md` 两文件，
     *   用户心智不敏感；与保留字校验同口径）
     */
    fun validate(rawName: String, existingDisplayNames: List<String>): TopicNameValidation {
        val trimmed = rawName.trim()
        if (trimmed.isEmpty()) return TopicNameValidation.Empty
        if (trimmed.equals(RESERVED_EXACT, ignoreCase = true) ||
            trimmed.startsWith(RESERVED_PREFIX, ignoreCase = true)
        ) {
            return TopicNameValidation.Reserved
        }
        if (trimmed.any { it in ILLEGAL_CHARS || it.isISOControl() }) return TopicNameValidation.IllegalChars
        if (trimmed.length > MAX_NAME_LENGTH) return TopicNameValidation.TooLong
        val clash = existingDisplayNames.firstOrNull { it.equals(trimmed, ignoreCase = true) }
        return clash?.let { TopicNameValidation.Exists(it) } ?: TopicNameValidation.Ok(trimmed)
    }
}

/**
 * 主题文档仓储（需求 83/85，设计 §6.5）：`topics/` 目录、列表（名称/创建时间/条数）、
 * 创建（含命名校验与同名检测）、空主题删除、创建时间元数据。
 *
 * - createdAt 元数据（§5.2）：独立 SP 文件 [TOPICS_PREFS]，key = `created_<文件名含.md>`，
 *   value = 创建时刻 epoch millis。**写入一律 `commit()`（禁异步 `apply`）**——TD-024：本机实测
 *   `apply` 延迟冻结（v1.3 主题持久化同因修复，对齐 `ThemePreferences` 先例）；createdAt
 *   写入低频（仅创建/首见回写），同步开销可接受。createdAt 仅创建/首见回写时写、移入永不更新
 *   （85(c)：排序稳定不随移入漂移）。
 * - 条数不入 SP，列表加载时实时读文件计数（`EntryParser.countEntries`，宽松正则）——与归档列表
 *   行为一致，避免第二份会过期的计数。
 * - 线程模型对齐 data 层现状（ArchiveRepository 先例）：本类非线程安全，跨实例文件写互斥由
 *   FileBasedMarkdownFile 内部的 FileOperationLock 承担。
 */
class TopicRepository(
    private val context: Context,
    /** 测试注入：直接指向临时目录（纯 JVM，无需 stub getExternalFilesDir——mock Context 的
     *  目录 stub 脆弱且不验证真实解析逻辑；构造器注入是显式依赖，§10.2 测试即用此口）。生产缺省 null。 */
    private val baseDir: File? = null,
    /** SP 注入参数（Q2 用户裁定 2026-11-01）：默认取真实实例；测试注入内存版假实现，不引入 Robolectric。 */
    private val prefs: SharedPreferences = context.getSharedPreferences(TOPICS_PREFS, Context.MODE_PRIVATE)
) {

    companion object {
        /** `appendo_topics` SP 文件名（独立于 `appendo`，§5.2：主题元数据整体生命周期独立、清理/迁移方便）。 */
        const val TOPICS_PREFS = "appendo_topics"
        private const val TOPICS_DIR_NAME = "topics"
        private const val FILE_SUFFIX = ".md"
        private const val KEY_CREATED_PREFIX = "created_"
        private const val CREATED_MISSING = -1L
    }

    /** SP key 形态：created_<文件名含.md>（§5.2）。 */
    private fun createdKey(fileName: String): String = KEY_CREATED_PREFIX + fileName

    /**
     * 主题根目录：baseDir 优先；否则 getExternalFilesDir(null) ?: context.filesDir——null 兜底
     * 对齐 FileRepository.getDefaultFile 与 ArchiveRepository.getArchiveDirectory 两处先例。
     *
     * 兜底切换会与归档/主文件「分家」（内外两个根目录，如外部存储一度不可用期间主题落在内部目录）：
     * [listTopics] 合并扫描两个候选目录自愈（外部目录优先、按 displayName 去重），分家期间主题
     * 不「消失」；新建/删除恒作用于本方法返回的单一路径。
     */
    fun topicsDir(): File =
        baseDir?.let { File(it, TOPICS_DIR_NAME) }
            ?: File(context.getExternalFilesDir(null) ?: context.filesDir, TOPICS_DIR_NAME)

    /** 候选主题目录（合并扫描用）：baseDir 注入时单一候选；生产双候选（外部目录优先、去重）。 */
    private fun candidateTopicDirs(): List<File> =
        if (baseDir != null) {
            listOf(topicsDir())
        } else {
            listOfNotNull(context.getExternalFilesDir(null), context.filesDir)
                .distinct()
                .map { File(it, TOPICS_DIR_NAME) }
        }

    /**
     * 全部主题（§6.5）：createdAt 倒序（新主题在前，§5.2 定稿）；正向标识 = `topics/` 目录成员
     * （.md 文件）；条数实时读文件（不入 SP）。文件存在但 SP created_ key 丢失（云备份部分恢复/
     * 异常）→ 本次观测以 file.lastModified() 回写 SP 一次（首见回写，此后冻结，排序稳定不漂移）。
     */
    fun listTopics(): List<TopicDocument> {
        val seen = HashSet<String>()
        val docs = ArrayList<TopicDocument>()
        for (dir in candidateTopicDirs()) {
            val files = dir.listFiles { f -> f.isFile && f.name.endsWith(FILE_SUFFIX) } ?: continue
            for (file in files.sortedBy { it.name }) {
                val displayName = file.name.removeSuffix(FILE_SUFFIX)
                if (displayName.isEmpty()) continue // 防御：跳过杂散 ".md" 空名文件
                if (!seen.add(displayName.lowercase())) continue // 外部候选优先，同名去重
                docs.add(documentOf(file, displayName))
            }
        }
        return docs.sortedByDescending { it.createdAt }
    }

    /** 按显示名查找（大小写不敏感，与同名检测同口径）。 */
    fun findTopic(displayName: String): TopicDocument? =
        listTopics().firstOrNull { it.displayName.equals(displayName, ignoreCase = true) }

    private fun documentOf(file: File, displayName: String): TopicDocument = TopicDocument(
        file = file,
        displayName = displayName,
        createdAt = createdAtOf(file),
        entryCount = try {
            EntryParser.countEntries(file.readText())
        } catch (e: Exception) {
            0 // 读失败按 0 计（对齐 ArchiveRepository.countEntries 先例）
        }
    )

    /**
     * createdAt（§5.2）：SP 有值直接用（冻结）；key 丢失 → 首见以 file.lastModified() 回写一次
     * 并 commit()，此后值冻结、移入永不更新（85(c) 排序不漂移——漂移来源是「每次重算」而非
     * 「固化一次」）。
     */
    private fun createdAtOf(file: File): Long {
        val key = createdKey(file.name)
        val stored = prefs.getLong(key, CREATED_MISSING)
        if (stored != CREATED_MISSING) return stored
        val fallback = file.lastModified()
        prefs.edit().putLong(key, fallback).commit()
        return fallback
    }

    /**
     * 创建空主题（§6.5）：[TopicNameRules] 校验链 → 通过后 `writeAll(FILE_HEADER)`
     * （FileBasedMarkdownFile 惯用法：atomicWrite 与主文件/归档同源，父目录自动创建）
     * **成功后才写 SP created_ key**——顺序约束：文件写失败不得残留 created_ key
     * （有 key 无文件 = 幽灵主题），失败返 [TopicCreateResult.WriteFailed]。
     *
     * 事务前创建空文档，列表即时可见、空可删（取消留空文档可删可复用，§6.5 创建时机取舍）。
     * writeAll 走 atomicWrite，其 `createTempFile` 位于 try 之外（目录只读时抛未捕获异常），
     * 故以 runCatching 一并映射失败（T-03 同款异常边界，勿改既有 atomicWrite）。
     */
    fun createEmptyTopic(rawName: String): TopicCreateResult {
        val topics = listTopics()
        return when (val v = TopicNameRules.validate(rawName, topics.map { it.displayName })) {
            TopicNameValidation.Empty -> TopicCreateResult.NameEmpty
            TopicNameValidation.Reserved -> TopicCreateResult.NameReserved
            TopicNameValidation.IllegalChars -> TopicCreateResult.NameIllegalChars
            TopicNameValidation.TooLong -> TopicCreateResult.NameTooLong
            is TopicNameValidation.Exists -> TopicCreateResult.NameExists(
                topics.first { it.displayName.equals(v.displayName, ignoreCase = true) }
            )
            is TopicNameValidation.Ok -> {
                val file = File(topicsDir(), v.trimmedName + FILE_SUFFIX)
                val md = FileBasedMarkdownFile(context, file)
                val writeOk = runCatching { md.writeAll(MarkdownFormatter.FILE_HEADER) }.getOrDefault(false)
                if (!writeOk) return TopicCreateResult.WriteFailed
                prefs.edit().putLong(createdKey(file.name), System.currentTimeMillis()).commit()
                TopicCreateResult.Created(file)
            }
        }
    }

    /**
     * 删除主题（§6.5，需求 85(a)）：entryCount 实时重算 >0 → [TopicDeleteResult.NotEmpty]
     * （非空禁删）；0 条先删文件、成功后删 SP created_ key（文件＋key 同清）；删文件失败 →
     * [TopicDeleteResult.Failed]（文件与 key 均保持）。读失败无法判空 → 保守拒绝
     * （[TopicDeleteResult.Failed]，不动任何状态）。
     */
    fun deleteTopic(topic: TopicDocument): TopicDeleteResult {
        val content = try {
            topic.file.readText()
        } catch (e: Exception) {
            return TopicDeleteResult.Failed
        }
        if (EntryParser.countEntries(content) > 0) return TopicDeleteResult.NotEmpty
        val deleted = try {
            topic.file.delete()
        } catch (e: Exception) {
            false
        }
        if (!deleted) return TopicDeleteResult.Failed
        prefs.edit().remove(createdKey(topic.file.name)).commit()
        return TopicDeleteResult.Deleted
    }
}
