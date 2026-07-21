# 工具层目录说明 (`util/`)

## 目录职责

工具层，封装文件 I/O 操作和 Markdown 格式化。采用策略模式 + 工厂模式实现双存储后端。

## 关键文件

| 文件 | 职责 | 设计角色 |
|------|------|---------|
| `EntryParser.kt` | **条目知识唯一收敛点（v1.1）**：parse/format/时间戳/边界算法/恢复判定，纯函数 | 核心（v1.1 新） |
| `ParsedEntry.kt` / `ReadResult.kt` | util 纯数据（解析条目 / 读结果+恢复标志） | 数据类 |
| `DuplicateHintThrottle.kt` | 重复内容提示 5s 节流（进程内单例） | 工具 |
| `MarkdownFileOperations.kt` | 文件操作接口 + 全局锁 + 默认方法（appendEntry / readAllForExternal / readAllWithStatus） | 策略接口 |
| `MarkdownFileFactory.kt` | 根据存储模式创建对应实现 | 工厂 |
| `FileBasedMarkdownFile.kt` | 默认文件存储：atomicWrite(temp+fsync+rename) + 读持锁 + EntryParser 化写侧 | 具体策略 A |
| `SafMarkdownFile.kt` | SAF 存储：.pending/.bak 软恢复 + EntryParser 化 + exists 修歧义 | 具体策略 B |
| `MarkdownFormatter.kt` | 文件级常量（FILE_HEADER、归档文件名）；formatEntry 仅 SafMarkdownFile 过渡期用 | 工具类（v1.1 缩减） |

> **v1.1 变更**：`MarkdownFile.kt`（旧 SAF 实现）已删除；条目 parse/format/边界/恢复逻辑统一收敛到 `EntryParser`（此前在 FileBasedMarkdownFile / SafMarkdownFile 各 ~80 行 ×2 重复）。时间戳改毫秒级（java.time），写入原子化（默认真原子 / SAF 软恢复），内容零宽空格隔离。详见 `docs/design.md` v1.1 章节。

## 设计模式

```
MarkdownFileOperations (接口)
  ├── append(content)       — 追加内容（自动加时间戳）
  ├── readAll()             — 读取全部内容
  ├── writeAll(content)     — 覆写全部内容（不加时间戳）
  ├── clear()               — 清空（恢复到只有文件头）
  ├── exists()              — 文件是否存在
  ├── count()               — 统计条目数
  ├── initHeader()          — 初始化文件头
  └── deleteEntry(timestamp) — 按时间戳删除条目

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
- **影响范围**：`append()`、`clear()`、`deleteEntry()`、`writeAll()` 等修改操作

## Markdown 文件格式

```markdown
# Appendo

---

## 2026-06-02 14:30:00

用户输入的内容

---

## 2026-06-02 15:00:00

另一条内容

---
```

- 文件头：`# Appendo\n`（由 `MarkdownFormatter.FILE_HEADER` 定义）
- 条目分隔符：`---`
- 时间戳格式：`## yyyy-MM-dd HH:mm:ss`（由 `MarkdownFormatter.TIMESTAMP_FORMAT` 定义）
- 编码：UTF-8，无 BOM

## 条目删除机制

使用**时间戳**而非索引作为删除标识符：
- 索引在插入/删除后会变化，可能导致误删
- 时间戳是每条记录的唯一标识，始终准确
- 支持连续删除和批量操作

## SAF 回退机制

`SafMarkdownFile` 在 SAF 操作失败时的降级策略：
1. 首先尝试 SAF 方式追加
2. SAF 失败后尝试直接文件追加（fallback append）
3. 两次都失败则返回 false

## 依赖关系

```
MarkdownFileFactory → MarkdownFileOperations (接口)
                   → FileBasedMarkdownFile (具体实现)
                   → SafMarkdownFile (具体实现)

FileBasedMarkdownFile → MarkdownFormatter (格式化、正则)
SafMarkdownFile       → MarkdownFormatter (格式化、正则)

FileRepository (data层) → MarkdownFormatter (归档文件名格式)
MainScreen (ui层)       → MarkdownFileFactory, MarkdownFileOperations
ShareReceiverActivity   → FileBasedMarkdownFile, SafMarkdownFile (直接使用)
```

## 扩展指南

- **添加新存储后端**：实现 `MarkdownFileOperations` 接口，在 `MarkdownFileFactory.create()` 中添加分支
- **修改 Markdown 格式**：修改 `MarkdownFormatter` 中的常量和格式化方法，注意向后兼容（旧文件仍需能解析）
- **添加新文件操作**：在 `MarkdownFileOperations` 接口添加方法，所有实现类需同步更新
- **删除 MarkdownFile.kt**：已废弃，确认无引用后可安全删除
