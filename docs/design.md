# Appendo - 设计方案

## 修订记录

| 日期 | 变更人 | 变更摘要 |
|------|--------|---------|
| 2026-04-01 | — | 初始版本 |
| 2026-04-10 | Yiyue | 去除 BOM；Toast 改 Notification；新增分享和归档功能；写入加同步锁；条目计数改为按时间戳标题 |
| 2026-04-16 | Yiyue | 新增手动输入功能；新增内容预览功能；应用定位调整为速记应用 |
| 2026-04-16 | Yiyue | 优化文件存储：首次启动使用默认目录；归档自动生成文件名；支持混合存储模式 |
| 2026-04-17 | Yiyue | 新增归档管理功能；统一条目交互模式；实现归档恢复功能；修复删除索引bug；改用时间戳作为删除标识符 |

## 架构概述

极简速记应用，两个 Activity 协作完成功能。支持通过分享接收内容和手动输入两种方式收集。

## 组件设计

### Activity

#### `MainActivity`
- 主界面，Jetpack Compose 构建
- **首次启动**：自动在应用私有目录创建 `Appendo.md` 文件，无需用户选择
- 显示当前文件路径和状态信息
- 提供操作按钮：打开文件、复制内容、清空文件、分享内容
- 菜单提供：归档、更换文件功能

#### `ShareReceiverActivity`
- 接收其他应用分享内容的入口（`ACTION_SEND` Intent Filter）
- 解析 Intent 中的文本或 URI
- 静默追加写入 Markdown 文件后自动关闭
- 写入完成后通过 Notification 提示用户

### 工具类

#### `MarkdownFile`
- 职责：Markdown 文件的读写操作
- 依赖：SAF URI 和 ContentResolver
- 方法：
  - `append(content: String)` — 追加带时间戳和分隔线的条目（**加 synchronized 锁**）
  - `readAll(): String` — 读取文件全部内容
  - `clear()` — 清空文件内容
  - `exists(): Boolean` — 检查文件是否存在
  - `count(): Int` — 通过正则匹配 `## YYYY-MM-DD HH:mm:ss` 统计条目数

#### `LinkEntry`
- 职责：表示单条收集内容的实体类
- 属性：
  - `timestamp: String` — 时间戳
  - `content: String` — 原始内容

#### `MarkdownParser`
- 职责：解析 Markdown 文件内容为条目列表
- 方法：
  - `parseEntries(content: String): List<LinkEntry>` — 解析文件内容，返回条目列表（按时间正序）

#### `EntryListScreen`（可复用组件）
- 职责：统一的条目列表展示和交互组件
- 支持功能：
  - 显示条目列表（倒序）
  - 长按复制单条内容（带震动）
  - 右滑删除单条内容（带确认对话框）
  - 滑动时显示操作指示文字
- 参数：
  - `entries: List<LinkEntry>` — 条目列表
  - `entryCount: Int` — 条目总数
  - `sourceFileName: String?` — 源文件名（可选）
  - `readOnly: Boolean` — 是否只读模式
  - `onEntryLongClick: (String) -> Unit` — 长按回调
  - `onEntrySwipeToDelete: (Int) -> Unit` — 滑动删除回调（传入原始索引）

#### `ArchiveRepository`
- 职责：归档文件管理
- 方法：
  - `listArchiveFiles(): List<ArchiveFile>` — 列出所有归档文件
  - `deleteArchive(file: File): Boolean` — 删除指定归档文件
  - `formatTimestamp(date: Date): String` — 格式化时间戳

#### `ArchiveListScreen`
- 职责：归档管理界面
- 功能：
  - 显示所有归档文件列表
  - 点击查看归档详情
  - 长按复制归档全部内容
  - 右滑删除归档文件（带确认）
  - 左滑追加到当前文档（带确认）

#### `ArchiveDetailScreen`
- 职责：归档详情界面
- 功能：
  - 显示归档文件的所有条目
  - 使用 `EntryListScreen` 组件（只读模式）
  - 长按复制单条内容
  - 不支持删除操作

## 数据流

```
方式一（分享接收）：
其他应用分享 → ShareReceiverActivity → 解析 Intent (text/uri) →
追加到 Markdown 文件 → Notification 提示 → 关闭 Activity

方式二（手动输入）：
主界面点击「手动输入」→ 弹出输入对话框 → 用户输入内容 →
点击「追加」→ 追加到 Markdown 文件 → 刷新条目列表 → 关闭对话框
```

## 界面布局

```
┌──────────────────────────────┐
│  Appendo           [⋯菜单]    │  ← TopAppBar（菜单含归档）
├──────────────────────────────┤
│                              │
│  文件: collection.md         │  ← 状态信息
│  已收集: 42 条               │
│                              │
│  [一键复制]                  │  ← 操作按钮
│  [手动输入]                  │  ← 手动输入按钮
│  [清空] [分享]               │  ← 操作按钮
│                              │
│  ──────────────────────────  │
│                              │
│  已收集内容 (42)             │  ← 内容预览区域
│  ┌────────────────────────┐ │
│  │ 2026-04-16 14:30:25    │ │
│  │ 这是一条测试内容        │ │
│  └────────────────────────┘ │
│  ┌────────────────────────┐ │
│  │ 2026-04-16 10:15:00    │ │
│  │ https://example.com    │ │
│  └────────────────────────┘ │
│           ...               │
│                              │
│  ──────────────────────────  │
│                              │
│  使用说明                    │
│  从任意应用分享链接或文字     │
│  或点击「手动输入」添加内容   │
│                              │
└──────────────────────────────┘
```

**归档列表页面**：
```
┌──────────────────────────────┐
│  ← 归档管理                   │  ← TopAppBar
├──────────────────────────────┤
│                              │
│  共 3 个归档                  │  ← 统计信息
│                              │
│  ┌────────────────────────┐ │
│  │ [ℹ] Appendo_20260416  │ │  ← 归档卡片
│  │     2026-04-16 · 15 条  │ │     （左滑=追加，右滑=删除）
│  │                      [🗑] │ │
│  └────────────────────────┘ │
│  ┌────────────────────────┐ │
│  │ [ℹ] Appendo_20260415  │ │
│  │     2026-04-15 · 23 条  │ │
│  └────────────────────────┘ │
│  ┌────────────────────────┐ │
│  │ [ℹ] Appendo_20260414  │ │
│  │     2026-04-14 · 8 条   │ │
│  └────────────────────────┘ │
│                              │
└──────────────────────────────┘
```

**归档详情页面**：
```
┌──────────────────────────────┐
│  ← 归档详情                   │  ← TopAppBar
│  Appendo_20260416             │
├──────────────────────────────┤
│  Appendo_20260416             │  ← 源文件名
│  已收集 15 条                 │
│                              │
│  ┌────────────────────────┐ │
│  │ 2026-04-16 14:30:25    │ │  ← 条目卡片（只读）
│  │ 这是一条测试内容        │ │     （长按=复制）
│  └────────────────────────┘ │
│  ┌────────────────────────┐ │
│  │ 2026-04-16 10:15:00    │ │
│  │ https://example.com    │ │
│  └────────────────────────┘ │
│           ...               │
│                              │
└──────────────────────────────┘
```

**手动输入对话框**：
```
┌──────────────────────────────┐
│  手动输入内容        [×]      │
├──────────────────────────────┤
│                              │
│  ┌────────────────────────┐ │
│  │                        │ │
│  │  请输入要追加的内容     │ │  ← 多行输入框（3-6行）
│  │                        │ │
│  │                        │ │
│  └────────────────────────┘ │
│                              │
│        [取消]    [追加]      │
│                              │
└──────────────────────────────┘
```

## Intent Filter

```xml
<intent-filter>
    <action android:name="android.intent.action.SEND" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="text/plain" />
</intent-filter>
```

## 文件权限方案

- 使用 SAF (Storage Access Framework)，无需 `WRITE_EXTERNAL_STORAGE` 权限
- 通过 `ActivityResultContracts.CreateDocument` 让用户选择保存位置
- 持久化 URI 权限（`takePersistableUriPermission`）
- 使用 SharedPreferences 存储用户选择的文件 URI

## 关键实现细节

1. **分享接收** — 在 `ShareReceiverActivity.onCreate()` 中解析 `Intent.EXTRA_TEXT` 或 `Intent.EXTRA_STREAM`
2. **手动输入** — 使用 `AlertDialog` + `OutlinedTextField` 实现多行输入框，点击「追加」按钮后调用 `MarkdownFile.append()`
3. **文件写入** — 通过 ContentResolver 的 `openOutputStream` 写入，使用 `MODE_APPEND`；**写入操作加 `synchronized` 锁**防止并发冲突
4. **格式保持** — 收集到的文本内容直接原样写入，不做任何处理、过滤或转义。换行、括号、空格、缩进、特殊符号等全部保留
5. **UTF-8 编码** — 写入时使用 `OutputStreamWriter` 并显式指定 `Charsets.UTF_8`，无 BOM
6. **时间格式** — `SimpleDateFormat("yyyy-MM-dd HH:mm:ss")`
7. **内容解析** — 使用正则 `^## \d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$` 匹配时间戳标题行，提取每条的时间戳和内容，构建 `LinkEntry` 列表
8. **内容预览** — 使用 `LazyColumn` 显示条目列表，`entries.reversed()` 实现倒序排列（最新的在前），使用 `Card` 组件美化每条显示
9. **实时刷新** — 使用 `LaunchedEffect` 轮询检测文件修改时间变化（每2秒），变化时自动刷新条目列表
10. **清空确认** — 使用 AlertDialog 确认后执行
11. **剪贴板复制** — 使用 `ClipboardManager`，内容通过 `ClipData.newPlainText` 设置
12. **打开文件** — 使用 `Intent.ACTION_VIEW` 配合文件 URI
13. **分享内容** — 使用 `ACTION_SEND` + `Intent.EXTRA_TEXT`（文件全部内容）+ `Intent.createChooser`
14. **默认文件存储** — 首次启动时，应用自动在 `context.getExternalFilesDir(null)` 目录下创建 `Appendo.md` 文件，使用 `FileBasedMarkdownFile` 进行文件操作。用户无需手动选择文件，开箱即用
15. **归档功能** — 点击归档后，自动在当前文件所在目录创建新文件，文件名格式为 `Appendo_yyyyMMdd_HHmmss.md`。保留旧文件，自动切换到新文件，条目计数归零。无需用户选择文件名或位置
16. **文件存储模式** — 支持两种存储模式：
    - **默认模式**：使用应用私有存储目录（`getExternalFilesDir`），无需 SAF，使用 `FileBasedMarkdownFile`
    - **SAF 模式**：用户通过"更换文件"功能选择外部存储位置，使用 `SafMarkdownFile`
17. **写入反馈** — 使用 `NotificationManager` 发送 Notification，避免 Activity 快速关闭导致 Toast 不可见
18. **条目计数** — 通过正则 `^## \d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$` 匹配时间戳标题行统计
19. **条目删除标识符** — 使用时间戳作为删除标识符，而不是索引。原因是：
    - 索引会因为插入/删除操作而变化，导致错位
    - 时间戳是条目的唯一标识，永远准确
    - UI传递条目的时间戳，底层根据时间戳查找并删除对应条目
    - 支持连续删除和多条操作，不会出现索引混乱
20. **滑动手势检测** — 使用 `detectHorizontalDragGestures` 检测水平滑动，`offsetX > SWIPE_THRESHOLD_DP` 触发右滑，`offsetX < -SWIPE_THRESHOLD_DP` 触发左滑
21. **滑动视觉反馈** — 使用 `Box` + `background` 实现滑动时显示的背景层，右滑显示红色背景+"删除"文字，左滑显示绿色背景+"追加"文字
22. **长按震动反馈** — 使用 `Vibrator` + `VibrationEffect.createOneShot()` 提供触觉反馈，短震动（100ms）用于复制操作，长震动（200ms）用于删除操作
23. **归档文件命名** — 归档文件自动命名为 `Appendo_yyyyMMdd_HHmmss.md`，存储在应用私有目录的 `archives/` 子目录
24. **归档追加逻辑** — 追加归档内容时，解析归档文件内容，提取所有条目（跳过文件头和分隔线），追加到当前文档末尾
25. **确认对话框** — 使用 `AlertDialog` 实现删除和追加确认，标题颜色区分操作类型（删除=红色，追加=绿色）
26. **Navigation Compose** — 使用 `NavHost` + `composable` 实现页面导航，归档详情通过 `Uri.encode/decode` 传递文件路径

## 依赖

- Jetpack Compose BOM (Material 3)
- Activity Compose
- Navigation Compose（用于归档管理和详情页面导航）
- 无第三方依赖
