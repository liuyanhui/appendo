# 工具层目录说明 (`util/`)

## 目录职责

工具层，封装文件 I/O 操作和 Markdown 格式化。采用策略模式 + 工厂模式实现双存储后端。

## 关键文件

| 文件 | 职责 | 设计角色 |
|------|------|---------|
| `EntryParser.kt` | **条目知识唯一收敛点（v1.1）**：parse/format/时间戳/边界算法/恢复判定/出口格式化（v1.3 `formatForExport`），纯函数 | 核心（v1.1 新） |
| `ParsedEntry.kt` / `ReadResult.kt` | util 纯数据（解析条目 / 读结果+恢复标志） | 数据类 |
| `RefreshGuard.kt` | 列表刷新守卫纯函数（v1.2.2）：读失败/疑似异常读取跳过刷新、header-only 合法空文件放行（TD-016 回归修复） | 工具 |
| `EntryKey.kt` | `timestamp|content` 条目唯一标识（v1.4）：寻址/去重/勾选三处共用 | 数据类 |
| `TopicMoveTransaction.kt` | 移入/移出/转移组合事务（v1.4 移入、v1.6 通用化三向——源/目标均可为 FileBased 或 SAF）：单次持锁先写目标后删源，`MoveResult` 六分类（含 `SameDocument` 防御护栏） | 核心（事务） |
| `DuplicateHintThrottle.kt` | 重复内容提示 5s 节流（进程内单例） | 工具 |
| `CalendarEntryMapper.kt` / `CalendarLauncher.kt` | 添加到日历（v1.0.3）：标题映射纯函数 + ACTION_INSERT 拉起日历 | 工具 |
| `MarkdownFileOperations.kt` | 文件操作接口 + 全局锁 + 默认方法（appendEntry / readAllForExternal / readAllWithStatus） | 策略接口 |
| `MarkdownFileFactory.kt` | 根据存储模式创建对应实现 | 工厂 |
| `FileBasedMarkdownFile.kt` | 默认文件存储：atomicWrite(temp+fsync+rename) + 读持锁 + EntryParser 化写侧 | 具体策略 A |
| `SafMarkdownFile.kt` | SAF 存储：.pending/.bak 软恢复 + EntryParser 化 + exists 修歧义 | 具体策略 B |
| `MarkdownFormatter.kt` | 文件级常量（FILE_HEADER、归档文件名格式、分隔形态 TD-015）；`formatEntry`/`TIMESTAMP_FORMAT`/严格正则已删（TD-010/011 收敛） | 工具类（v1.2.1 起仅常量） |

> **v1.1 变更**：`MarkdownFile.kt`（旧 SAF 实现）已删除；条目 parse/format/边界/恢复逻辑统一收敛到 `EntryParser`（此前在 FileBasedMarkdownFile / SafMarkdownFile 各 ~80 行 ×2 重复）。时间戳改毫秒级（java.time），写入原子化（默认真原子 / SAF 软恢复），内容零宽空格隔离。详见 `docs/design.md` v1.1 章节。

## 系统级机制 → 架构文档

本包涉及的系统级现状**只在 `docs/architecture.md` 维护**（分工规则见其文末），此处不复制，改前必读：

- 接口成员清单与全局锁 `FileOperationLock` → architecture.md §4.1/§4.3（接口成员以 `MarkdownFileOperations.kt` 为准）
- Markdown 条目格式、ZWSP 隔离两条不变量、出口剥离规则 → §3.1/§3.3
- 时间戳主键与单调防碰撞（为何不用索引）→ §3.2 与 §9 ADR
- 存储双模式、原子写/SAF 软恢复、URI 失效回退与授权自愈 → §4.1/§4.2

**模式速记**：策略模式（`MarkdownFileOperations` 双实现 FileBased/Saf——统一接口隔离 I/O 差异，双 Activity 并发入口）＋工厂（`MarkdownFileFactory`）；新增逻辑优先落纯函数（util `object`，纯 JVM 可测——`EntryParser` 存在的直接原因）。

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
CalendarEntryMapper → EntryParser (stripIsolationMarkers 出口剥离)
```

## 扩展指南

- **添加新存储后端**：实现 `MarkdownFileOperations` 接口，在 `MarkdownFileFactory.create()` 中添加分支
- **修改 Markdown 格式**：改 `EntryParser`（parse/format/正则/时间戳全部收敛在此），注意向后兼容（旧秒级文件仍需能解析）；同步修订 `docs/architecture.md` §3
- **添加新文件操作**：在 `MarkdownFileOperations` 接口添加方法，所有实现类需同步更新（读写都要持 `FileOperationLock`）
