# 数据层目录说明 (`data/`)

## 目录职责

数据层，管理文件存储偏好设置和归档文件操作。采用 Repository 模式隔离数据访问。

## 关键文件

| 文件 | 职责 |
|------|------|
| `FileRepository.kt` | 文件存储偏好管理、SAF URI 持久化、首次启动检测、SAF 恢复文件（.bak/.pending）清理；companion 持有 SP 文件名共享常量 `APPENDO_PREFS` |
| `ThemePreferences.kt` | 主题三态偏好（v1.3）：`theme_mode` 键读写（缺省/非法回退 SYSTEM）、`applyWindowTheme`（Activity 冷启动 setTheme） |
| `ArchiveRepository.kt` | 归档文件的增删查、时间戳解析（v1.6 尾部锚定三类形态＋sourceTopic＋archiveFileFor 单点）、内容读取（`ArchiveFile` 数据类定义在此文件内，无独立文件） |
| `TopicRepository.kt` | 主题文档管理（topics/ 目录、命名校验、创建/删除、createdAt 元数据、首次移入指路旗标）；v1.6 增 `renameTopic`（锁内三步/createdAt 保留/原名无操作/失败回滚） |
| `FileRepository.kt`（v1.6 归档命名） | `getArchiveFile(sourceTopic)` 主题名变体＋同秒 `_n` 递增后缀（上限 100 不覆盖，主/题统一） |

## 系统级机制 → 架构文档

双存储模式（SAF/默认）、URI 失效回退与授权自愈、原子写/软恢复语义、SharedPreferences 键清单均为系统级现状，**只在 `docs/architecture.md` 维护**（§4.1/§4.2/§3.6，分工规则见其文末），此处不复制。

## 线程安全

- `FileRepository` 和 `ArchiveRepository` 本身不是线程安全的
- 文件并发写入通过 `util/FileOperationLock`（全局锁）保护
- ShareReceiverActivity 在 IO 线程执行写入，通过 FileOperationLock 与 MainScreen 互斥

## 已知问题

- ✅ TD-010 已修复（v1.2.1）：`ArchiveRepository` 归档计数改用 `EntryParser.getTimestampRegex()`（宽松正则，秒/毫秒都数得到）。
- `file_last_modified` 是应用每次写完自维护的 SP 逻辑时间戳（非文件系统 mtime），用于 MainScreen 2 秒轮询；外部编辑不会触发刷新。

## 依赖关系

```
FileRepository → util/MarkdownFormatter (归档文件名格式)
              → Android SharedPreferences API
              → Android DocumentFile API (SAF URI 校验)

ArchiveRepository → util/MarkdownFormatter (时间戳正则、格式)
```

## 扩展指南

- **添加新的存储模式**：在 `FileRepository` 添加新模式的状态管理，在 `util/MarkdownFileFactory` 添加对应实现
- **添加新的数据源**：创建新的 Repository 类，遵循现有模式（构造函数接收 Context）
- **修改存储偏好**：所有偏好键定义在 `FileRepository.Companion` 中
