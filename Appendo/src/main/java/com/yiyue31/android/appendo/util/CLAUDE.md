# 工具层目录说明 (`util/`)

## 目录职责

工具层，封装文件 I/O 操作和 Markdown 格式化。采用策略模式 + 工厂模式实现双存储后端。

## 关键文件

| 文件 | 职责 | 设计角色 |
|------|------|---------|
| `EntryParser.kt` | **条目知识唯一收敛点（v1.1）**：parse/format/时间戳/边界算法/恢复判定，纯函数 | 核心（v1.1 新） |
| `ParsedEntry.kt` / `ReadResult.kt` | util 纯数据（解析条目 / 读结果+恢复标志） | 数据类 |
| `DuplicateHintThrottle.kt` | 重复内容提示 5s 节流（进程内单例） | 工具 |
| `CalendarEntryMapper.kt` / `CalendarLauncher.kt` | 添加到日历（v1.0.3）：标题映射纯函数 + ACTION_INSERT 拉起日历 | 工具 |
| `ReminderMeta.kt` / `ReminderMetaCodec.kt` / `ReminderLogic.kt` / `ReminderText.kt` | 提醒纯逻辑（v1.1–v1.2）：元数据 / 编解码 / 状态决策 / 通知文案；Android 侧在 `reminder/` 包 | 纯函数 |
| `MarkdownFileOperations.kt` | 文件操作接口 + 全局锁 + 默认方法（appendEntry / readAllForExternal / readAllWithStatus） | 策略接口 |
| `MarkdownFileFactory.kt` | 根据存储模式创建对应实现 | 工厂 |
| `FileBasedMarkdownFile.kt` | 默认文件存储：atomicWrite(temp+fsync+rename) + 读持锁 + EntryParser 化写侧 | 具体策略 A |
| `SafMarkdownFile.kt` | SAF 存储：.pending/.bak 软恢复 + EntryParser 化 + exists 修歧义 | 具体策略 B |
| `MarkdownFormatter.kt` | 文件级常量（FILE_HEADER、归档文件名格式、分隔形态 TD-015）；`formatEntry`/`TIMESTAMP_FORMAT`/严格正则已删（TD-010/011 收敛） | 工具类（v1.2.1 起仅常量） |

> **v1.1 变更**：`MarkdownFile.kt`（旧 SAF 实现）已删除；条目 parse/format/边界/恢复逻辑统一收敛到 `EntryParser`（此前在 FileBasedMarkdownFile / SafMarkdownFile 各 ~80 行 ×2 重复）。时间戳改毫秒级（java.time），写入原子化（默认真原子 / SAF 软恢复），内容零宽空格隔离。详见 `docs/design.md` v1.1 章节。

## 设计模式

```
MarkdownFileOperations (接口)
  ├── append(content)       — 追加内容（nextTimestamp 自动加毫秒时间戳）
  ├── appendEntry(ts, content) — 追加并保留指定时间戳（归档恢复用）
  ├── readAll()             — 读取全部内容（含 ZWSP）
  ├── readAllWithStatus()   — 读取 + 恢复标志（SAF 软恢复时 recovered=true）
  ├── readAllForExternal()  — 读取并剥离 ZWSP（复制/分享出口专用）
  ├── writeAll(content)     — 覆写全部内容（不加时间戳）
  ├── clear()               — 清空（恢复到只有文件头，不走 .bak）
  ├── exists()              — 文件是否存在
  ├── count()               — 统计条目数（宽松正则，新旧格式都数得到）
  ├── initHeader()          — 初始化文件头
  ├── deleteEntry(timestamp) — 按时间戳删除条目
  └── updateEntry(timestamp, newContent) — 按时间戳替换内容（时间戳不变）

MarkdownFileFactory.create(context, useSAF, fileUri, defaultFile)
  │
  ├── useSAF=true  → SafMarkdownFile(context, fileUri)
  └── useSAF=false → FileBasedMarkdownFile(context, defaultFile)
```

### 为什么用策略模式？

- ShareReceiverActivity 和 MainScreen 可能同时访问文件
- 不同存储后端（默认文件 vs SAF）有完全不同的 I/O API
- 统一接口让上层代码无需关心底层实现

## 全局锁机制

```kotlin
// MarkdownFileOperations.kt 中定义
object FileOperationLock
```

- **为什么需要**：MainScreen 和 ShareReceiverActivity 创建各自独立的 `MarkdownFileOperations` 实例，实例级锁无法防止跨实例并发
- **使用方式**：所有文件写操作需要 `synchronized(FileOperationLock)` 包裹
- **影响范围（v1.1 起）**：**全部读写**——append/appendEntry/deleteEntry/updateEntry/clear/writeAll/count/initHeader，以及 readAll/readAllWithStatus/exists（SAF 读前要做恢复判定，且 read-modify-write 要求读到一致状态）

## Markdown 文件格式

```markdown
# Appendo

---

## 2026-06-02 14:30:00.123

用户输入的内容

---

## 2026-06-02 15:00:01.456

另一条内容

---
```

- 文件头：`# Appendo\n`（由 `MarkdownFormatter.FILE_HEADER` 定义）
- 条目分隔符：`---`
- 时间戳格式：写入 `## yyyy-MM-dd HH:mm:ss.SSS`（毫秒，`EntryParser.timestampFormatter`）；解析用宽松正则（`EntryParser.getTimestampRegex()`），兼容旧秒级条目
- 内容隔离：内容行恰好匹配时间戳格式时，写入侧加 ZWSP（U+200B）前缀；复制/分享等出口统一剥离（详见 `docs/architecture.md` §3.3）
- 编码：UTF-8，无 BOM

## 条目删除机制

使用**时间戳**而非索引作为删除标识符：
- 索引在插入/删除后会变化，可能导致误删
- 时间戳是每条记录的唯一标识，始终准确
- 支持连续删除和批量操作

## SAF 回退机制

`SafMarkdownFile` 的降级与回退（v1.1 重写后）：
1. 写主 URI：优先 `openFileDescriptor("wt")`（可 fsync），provider 不支持时回退 `openOutputStream("wt")`
2. URI 授权失效（`exists()` 抛 SecurityException）：由上层（ShareReceiverActivity / MainScreen 首启检查）`clearFileUri()` 后回退默认文件模式

## 依赖关系

```
MarkdownFileFactory → MarkdownFileOperations (接口)
                   → FileBasedMarkdownFile (具体实现)
                   → SafMarkdownFile (具体实现)

FileBasedMarkdownFile / SafMarkdownFile → EntryParser (parse/format/边界算法/时间戳)
                                       → MarkdownFormatter (FILE_HEADER 常量)

FileRepository (data层) → MarkdownFormatter (归档文件名格式)
MainScreen (ui层)       → MarkdownFileFactory, MarkdownFileOperations
ShareReceiverActivity   → FileBasedMarkdownFile, SafMarkdownFile (直接使用)
CalendarEntryMapper / ReminderText → EntryParser (stripIsolationMarkers 出口剥离)
```

## 扩展指南

- **添加新存储后端**：实现 `MarkdownFileOperations` 接口，在 `MarkdownFileFactory.create()` 中添加分支
- **修改 Markdown 格式**：改 `EntryParser`（parse/format/正则/时间戳全部收敛在此），注意向后兼容（旧秒级文件仍需能解析）；同步修订 `docs/architecture.md` §3
- **添加新文件操作**：在 `MarkdownFileOperations` 接口添加方法，所有实现类需同步更新（读写都要持 `FileOperationLock`）
