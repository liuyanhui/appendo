# UI 层目录说明 (`ui/`)

## 目录职责

UI 层，包含所有 Compose 界面组件。采用 Jetpack Compose + Material 3 构建。

## 关键文件

| 文件 | 职责 | 复杂度 |
|------|------|--------|
| `MainScreen.kt` | 主页面：笔记列表、2x2 操作网格（手动输入/复制全部/分享全部/归档）、多选模式与移入主题、归档二选一对话框、输入/编辑/关于/外观选择对话框、提醒入口与自检、首启存储引导、文件轮询刷新；顶层 sealed `ArchiveOutcome`＋归档决策映射 internal 纯函数 | 高 |
| `EntryListScreen.kt` | 可复用笔记列表组件：条目卡片、滑动删除、长按复制、点击编辑、多选勾选、提醒徽标 | 中 |
| `ArchiveListScreen.kt` | 归档管理页：归档卡片、滑动删除/追加恢复（去重）——v1.4 分家后仅整文件归档（主题移至 `TopicListScreen`）；`ArchiveEntryKind` 枚举定义留驻本文件 | 中 |
| `ArchiveDetailScreen.kt` | 归档/主题详情页：复用 EntryListScreen（只读模式），`kind=topic` 传「主题详情」标题与来源文件名 | 低 |
| `TopicListScreen.kt` | 主题独立页（v1.4，`topics` 路由）：主题卡片（彩色首字标）、页内新建（同名提示）、空主题右滑删除、左滑恢复 | 中 |
| `SwipeCard.kt` | 滑动手势骨架共用组件（v1.4）：offset/120dp 阈值/方向背景指示（右删红/左恢复绿）/禁删钳 0/首卡 elevation；`ArchiveCard`/`TopicCard` 为薄包装；`performVibration` internal 共享 | 中 |
| `TopicBadge.kt` | 主题首字标色板派生纯函数（v1.4）：`deriveBadgePalette`/`contentColorFor`/`badgeIndex`/`firstGrapheme`，全 JVM 可测 | 低 |
| `MultiSelect.kt` | 多选选中集维护纯函数（`retainedSelection`）＋多选态 contextual 顶栏 | 低 |
| `TopicMoveDialogs.kt` | 移入主题流程对话框：目标选择/新建命名/同名确认/确认（含失败重试态）/多选长按预览 | 中 |
| `ArchiveRestore.kt` | 恢复确认对话框＋去重追加循环（自 ArchiveListScreen 迁移共用，归档/主题两路消费） | 中 |
| `Microcopy.kt` | 微文案常量源（v1.3.x 起）：追加/保存/复制反馈＋移入主题词族＋归档二选一词族 | 低 |
| `LinkEntry.kt` | UI 层条目模型：timestamp（完整时间戳串作 key）+ content + 派生 timestampDisplay | 低 |
| `ReminderTimePickerDialog.kt` | 提醒时间选择：预设 chips + 自定义日期时间 + 每天/每周重复 | 中 |
| `AppColors.kt` | ~~全局颜色常量~~ **已删除（v1.3）**：迁移为 `ui/theme/Theme.kt` 语义色（`MaterialTheme.colorScheme.primary`/`.error` + `successColor` 扩展），详见 `theme/CLAUDE.md` | — |
| `ToastUtils.kt` | 自定义 Toast：圆角半透明黑色背景，兼容旧版 Android；两种主题下均可读，不随主题变色 | 低 |
| `theme/`（子包） | 主题基础设施（v1.3）：`AppendoTheme` 双色板、success 扩展色、`ThemeMode` 三态、状态栏同步——见 `theme/CLAUDE.md` | 中 |

## 导航流程

```
MainScreen ("main")
  ├── 点击顶栏"主题"图标（浏览态常驻含 0 条；多选态随 contextual bar 隐藏）→ TopicListScreen ("topics")
  │                          └── 点击主题卡片 → ArchiveDetailScreen ("archive_detail/{filePath}?kind=topic"，主题详情)
  ├── 点击菜单"归档管理" → ArchiveListScreen ("archive_list")
  │                          └── 点击归档卡片 → ArchiveDetailScreen ("archive_detail/{filePath}"，kind 缺省＝归档)
  ├── 点击网格"归档"按钮（第四格）→ 弹出二选一对话框：「归档并清空」／「只归档，保留列表」（不导航；v1.4 起菜单"归档"项已移除，入口唯一化到网格）
  ├── 点击菜单"打开文件" → 调用外部查看器
  ├── 点击菜单"自定义目录" → SAF 文件选择器
  ├── 点击菜单"提醒自检" → 弹出自检对话框（权限/白名单/测试提醒）
  ├── 点击菜单"外观"（原"主题"，v1.4 更名；三态功能不变）→ 弹出外观选择对话框（跟随系统/浅色/深色）
  └── 点击菜单"关于" → 弹出对话框
```

## 数据模型

```kotlin
// LinkEntry.kt 中定义（v1.1 从 MainScreen 拆出），由 util/ParsedEntry 映射而来
data class LinkEntry(
    val timestamp: String,  // 完整时间戳串（新条目毫秒级 .SSS，旧条目秒级），条目唯一 key
    val content: String     // 还原后的用户原文（ZWSP 已剥离）
) {
    val timestampDisplay: String = EntryParser.displayTimestamp(timestamp) // UI 渲染只显这个
}
```

## 核心交互模式

| 操作 | 触发方式 | 行为 |
|------|---------|------|
| 复制单条 | 长按条目卡片 | 复制该条目内容到剪贴板，触感反馈 |
| 编辑条目 | 点击条目卡片 | 弹出编辑对话框，updateEntry 保存，时间戳不变 |
| 删除条目 | 右滑条目卡片 | 弹出确认对话框，按时间戳删除（联动取消该条提醒） |
| 复制全部 | 点击"复制全部"按钮 | 复制完整文件内容到剪贴板（剥离 ZWSP） |
| 分享全部 | 点击"分享全部"按钮 | 调用系统分享（剥离 ZWSP） |
| 归档 | 点击网格"归档"按钮（第四格） | 弹出二选一：「归档并清空」＝先备份为归档文件再清空＋取消全部提醒／「只归档，保留列表」＝仅生成归档副本、主列表不动（v1.4 起替代长按"清空"） |
| 移入主题 | 顶栏多选图标进入多选态，勾选后点"移入" | 组合事务先写主题文档后删源条目；失败对话框保持可重试；成功/去重/提醒取消计数透明 |
| 主题页 | 顶栏"主题"图标 | 主题独立页：点击看只读详情、长按复制全文、左滑恢复（去重追加）、空主题右滑删除（非空右滑零响应） |
| 手动输入 | 点击"手动输入"按钮 | 弹出输入对话框，追加到文件（可选设提醒/添加到日历） |
| 设提醒 | 详情对话框主按钮 | 时间选择（chips/自定义/每天每周重复），覆盖前确认 |
| 取消提醒 | 详情对话框（已有未触发提醒时） | 取消闹钟 + 删 sidecar |
| 提醒自检 | 菜单 | 通知权限/电池白名单检查 + 发送测试提醒 |
| 归档恢复 | 左滑归档卡片 | 按时间戳|内容去重后追加到当前文档 |
| 主题恢复 | 左滑主题卡片 | 同归档恢复（共用 ArchiveRestore），确认框注明"追加后主题文档中仍保留这些条目" |

## EntryListScreen 复用设计

`EntryListScreen` 是跨场景复用的列表组件，通过参数控制行为：

- **MainScreen 使用**：传入 `readOnly = false`，启用滑动删除与点击编辑；`listState` 参数由 MainScreen 传入（提醒通知深链滚动用）
- **ArchiveDetailScreen 使用**：传入 `readOnly = true`，禁用滑动删除
- `sourceFileName` 参数：可选，归档详情页显示来源文件名
- 卡片显示提醒徽标：订阅 `ReminderStore`（Compose State），提醒触发即刻刷新，**不走**文件轮询

## 文件刷新机制

MainScreen 使用轮询机制检测文件变更：
- 间隔：2 秒（`FILE_CHANGE_POLL_INTERVAL_MS`）
- 比较逻辑：`fileRepository.getFileLastModified()` 与本地缓存对比
- 触发写入后主动调用 `setFileLastModified()`，确保下轮轮询能检测到变化
- 注意：该 mtime 是应用自维护的 SP 逻辑时间戳（非文件系统 mtime），**外部编辑不会触发刷新**

## 依赖关系

```
MainScreen → data/FileRepository
           → data/TopicRepository（移入主题目标列表/新建/首次指路旗标）
           → util/MarkdownFileFactory, MarkdownFileOperations
           → util/MarkdownFormatter
           → util/FileBasedMarkdownFile, SafMarkdownFile
           → util/TopicMoveTransaction, EntryKey（移入主题组合事务）
           → reminder/AlarmScheduler, ReminderStore（设提醒/取消/孤儿对账）
           → util/CalendarEntryMapper, CalendarLauncher（添加到日历）

ArchiveListScreen → data/FileRepository, data/ArchiveRepository
                  → util/MarkdownFileFactory
                  → ui/EntryListScreen (共享组件), ui/SwipeCard

TopicListScreen → data/FileRepository, data/TopicRepository
                 → ui/SwipeCard（主题卡）, ui/TopicMoveDialogs（NewTopicNameDialog 复用）
                 → ui/ArchiveRestore（RestoreConfirmDialog 复用）, ui/TopicBadge（首字标）

ArchiveDetailScreen → ui/EntryListScreen (只读模式)
```

## 扩展指南

- **添加新页面**：创建新的 `@Composable` 函数，在 `MainActivity.AppendoNavHost` 注册路由
- **修改列表样式**：修改 `EntryListScreen` 的 `EntryCard` 组件
- **添加新交互**：在 `EntryListScreen` 添加新回调参数，注意 `readOnly` 模式下的行为
- **添加新按钮**：在 `MainScreen` 的按钮区域（2x2 Row 布局）中添加
- **修改颜色主题**：修改 `ui/theme/Theme.kt` 的 `Palette`（同步 `res/values/colors.xml` 状态栏色）；取色一律经 `MaterialTheme.colorScheme` / `successColor` 扩展（v1.3 起）
