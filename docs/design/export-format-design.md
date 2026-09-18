# 出口格式化（复制/分享导出纯文本） — 设计文档

> 日期: 2026-09-18
> 状态: ✅ 已确认（2026-09-18 用户确认需求与格式：秒级时间戳、单条复制不变、"其他按建议执行"）
> 需求: `docs/specs.md` 需求 68~73（v1.3）
> 规模注记：改动面小（一个纯函数 + 两个调用点 + 测试），经用户整体批准，执行计划并入本文 §4

---

## 1. 方案

在 **EntryParser**（条目知识唯一收敛点）新增出口格式化纯函数：

```kotlin
/** 出口格式化（需求 68~70）：每条"秒级时间戳行 + 空行 + 内容"，条目间空行分隔。 */
fun formatForExport(entries: List<ParsedEntry>): String =
    entries.joinToString("\n\n") { e ->
        displayTimestamp(e.timestamp) + "\n\n" + e.content
    }
```

复用已有语义，零新增颜色/格式常量：

| 需求语义 | 落点 | 现状 |
|---------|------|------|
| 秒级时间戳 | `displayTimestamp()` | 已有：剥毫秒 → `yyyy-MM-dd HH:mm:ss` |
| 内容去首尾空白行 | `parse()` 产物 | 已有：`content.trim()` |
| 无 ZWSP 隔离标记 | `parse()` 的 `restoreLine()` | 已有：隔离前缀剥离还原；用户自有 ZWSP 属原文、保留 |
| 顺序最旧在前 | `parse()` 产物顺序 | 已有（文件正序） |
| 不含 `##`/`---`/`# Appendo` | 格式化函数本身 | 天然不含 |

**出口走 parse 产物**＝满足 architecture §3.3 不变量 2（出口经 `stripIsolationMarkers` 或经 parse 产物）；`readAllForExternal()`（原文+字符级剥离）保留给其他潜在用途，本变更后无调用方。

## 2. 调用点改造（MainScreen.kt）

`copyContent` / `shareContent` 统一改为：

```kotlin
val entries = EntryParser.parse(mdFile.readAll())
if (entries.isEmpty()) { showToast(..., "暂无内容可复制/分享"); return }
val content = EntryParser.formatForExport(entries)
// 后续剪贴板 / ACTION_SEND 不变
```

空文件判断从 `!content.contains("## ")` 改为 `entries.isEmpty()`（原文检查随原文导出一并淘汰）。

## 3. 不动清单（需求 71/72）

- 长按复制单条（EntryListScreen）：仍复制 `entry.content`，无时间戳行
- 添加到日历（CalendarEntryMapper）、通知标题（ReminderText）：各自出口不变
- 文件存储格式、写入/删除/编辑、归档、提醒：全部不动

## 4. 执行清单（计划并入）

| # | 任务 | 状态 |
|---|------|------|
| E-1 | EntryParser 增加 `formatForExport` + KDoc | ✅ |
| E-2 | MainScreen `copyContent`/`shareContent` 改造 | ✅ |
| E-3 | 单测：混合时间戳秒级化 / trim / 隔离行还原 / 用户 ZWSP 保留 / CRLF / 多条目分隔形态 | ✅ |
| E-4 | 全量 JVM 测试 | ✅ |
| E-5 | 真机验收（需求 73：有道云+便签完整可见、微信干净、单条不变、空文件提示、零宽字符检查） | ✅ 2026-09-18 用户验证通过（接收端完整可见为核心项） |

## 5. 文档同步

- `architecture.md` §3.3 出口表：复制全部/分享全部改为"经 parse 产物格式化（v1.3 出口格式化）"；§2 模块地图 EntryParser 行补 formatForExport
- `README.md`：功能特性"快捷操作"行注明导出格式
- `CHANGELOG.md`：v1.3.0 条目
- `util/CLAUDE.md`：EntryParser 职责行补出口格式化
