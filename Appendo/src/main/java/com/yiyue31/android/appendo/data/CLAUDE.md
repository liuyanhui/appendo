# 数据层目录说明 (`data/`)

## 目录职责

数据层，管理文件存储偏好设置和归档文件操作。采用 Repository 模式隔离数据访问。

## 关键文件

| 文件 | 职责 |
|------|------|
| `FileRepository.kt` | 文件存储偏好管理、SAF URI 持久化、首次启动检测、SAF 恢复文件（.bak/.pending）清理 |
| `ArchiveRepository.kt` | 归档文件的增删查、时间戳解析、内容读取（`ArchiveFile` 数据类定义在此文件内，无独立文件） |

## 存储架构：双模式设计

应用支持两种文件存储模式，通过 `FileRepository` 管理：

```
FileRepository
  ├── SAF 模式 (useSAF = true)
  │   └── 通过 Storage Access Framework 操作用户选择的外部文件
  │   └── URI 持久化到 SharedPreferences
  │   └── 需要持久化 URI 权限 (takePersistableUriPermission)
  │
  └── 默认模式 (useSAF = false)
      └── 操作应用私有目录下的 Appendo.md
      └── 路径: getExternalFilesDir(null)/Appendo.md
```

### 模式切换与降级

- SAF URI 失效时自动降级为默认模式（`clearFileUri()`）
- 降级时先清除偏好再释放权限，防止竞态条件导致永久丢失 URI
- `isFirstLaunch()` 用于首次安装时引导用户选择外部存储

## SharedPreferences 说明

- 文件名：`"appendo"`
- 存储内容：`use_saf`（布尔）、`file_uri`（字符串）、`file_last_modified`（长整型）

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
