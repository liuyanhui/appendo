# UI 层目录说明 (`ui/`)

## 目录职责

UI 层，包含所有 Compose 界面组件。采用 Jetpack Compose + Material 3 构建。

## 关键文件

| 文件 | 职责 | 复杂度 |
|------|------|--------|
| `MainScreen.kt` | 主页面：笔记列表、2x2 操作网格（手动输入/复制全部/分享全部/归档）、多选模式与移入主题、归档二选一对话框、输入/编辑/关于/外观选择对话框、首启存储引导、文件轮询刷新（归档编排与 `ArchiveOutcome` 已迁 `ArchiveFlow.kt`） | 高 |
| `EntryListScreen.kt` | 可复用笔记列表组件：条目卡片、滑动删除、长按复制、点击编辑、多选勾选；`headerSlot` 参数＝随列表滚动的头部槽（v1.6.1 工作区标题行消费，主宿主不传零变化） | 中 |
| `ArchiveListScreen.kt` | 归档管理页：归档卡片、滑动删除/追加恢复（去重）——v1.4 分家后仅整文件归档（主题移至 `TopicListScreen`）；`ArchiveEntryKind` 枚举定义留驻本文件 | 中 |
| `ArchiveDetailScreen.kt` | 归档/主题详情页：复用 EntryListScreen（只读模式），`kind=topic` 传「主题详情」标题与来源文件名 | 低 |
| `TopicListScreen.kt` | 主题独立页（v1.4，`topics` 路由）：主题卡片（彩色首字标）、页内新建（同名提示）、空主题右滑删除、左滑恢复 | 中 |
| `SwipeCard.kt` | 滑动手势骨架共用组件（v1.4）：offset/120dp 阈值/方向背景指示（右删红/左恢复绿）/禁删钳 0；平面卡（elevation 恒 0，v1.4 换机验证视觉裁定，层级靠页面底色分层）；`ArchiveCard`/`TopicCard` 为薄包装；`performVibration` internal 共享 | 中 |
| `TopicBadge.kt` | 主题首字标色板派生纯函数（v1.4）：`deriveBadgePalette`/`contentColorFor`/`badgeIndex`/`firstGrapheme`，全 JVM 可测 | 低 |
| `MultiSelect.kt` | 多选选中集维护纯函数（`retainedSelection`）＋多选态 contextual 顶栏 | 低 |
| `TopicMoveDialogs.kt` | 移入主题流程对话框：目标选择/新建命名/同名确认/确认（含失败重试态）/多选长按预览 | 中 |
| `EntryEditDialog.kt` / `EntryInputDialog.kt` | v1.6 无状态共享对话框（草稿提升宿主、onSave 单源、日历时序归宿主；MainScreen/工作区两消费方） | 低 |
| `EntrySaveOps.kt` | v1.6 共享写盘/出口小件（/simplify 收敛）：`tryEditSave`/`tryAppend`/`copyToClipboard`/`validationToast`——两宿主写盘与复制样板单源 | 低 |
| `ArchiveFlow.kt` | v1.6 归档共享编排（runArchive/ArchiveWordbook/ArchiveChoiceDialog/ArchiveOutcome/决策映射；主/题两消费方 onCompleted 差异化善后） | 中 |
| `MoveFlow.kt` | v1.6 三动词（移入/移出/转移）状态 holder＋MoveOutcomeMapping 全组合（guideSuffix 承载首次指路） | 中 |
| `TopicWorkspaceScreen.kt` | v1.6 主题工作区（编辑/删除/批量/新增/复制/分享/归档；与主界面同权；topic_workspace 路由）；v1.6.1 顶栏下方全宽标题行（随列表滚动＋titleCount 防闪计数） | 高 |
| `ArchiveRestore.kt` | 恢复确认对话框＋去重追加循环（自 ArchiveListScreen 迁移共用，归档/主题两路消费） | 中 |
| `Microcopy.kt` | 微文案常量源（v1.3.x 起）：追加/保存/复制反馈＋移入主题词族＋归档二选一词族 | 低 |
| `LinkEntry.kt` | UI 层条目模型：timestamp（完整时间戳串作 key）+ content + 派生 timestampDisplay | 低 |
| `AppColors.kt` | ~~全局颜色常量~~ **已删除（v1.3）**：迁移为 `ui/theme/Theme.kt` 语义色（`MaterialTheme.colorScheme.primary`/`.error` + `successColor` 扩展），详见 `theme/CLAUDE.md` | — |
| `ToastUtils.kt` | 自定义 Toast：反色药丸（v1.4 视觉裁定——随应用主题反转：浅色深底白字/深色浅底深字，近不透明；ThemePreferences 偏好＋系统夜间同 Compose 解析规则），兼容旧版 Android | 低 |
| `StateSavers.kt` | 状态保全 Saver 五枚（含编码原语 `NullableStringSaver`；另有 `TopicListScreen` 局部 `CreateStepSaver`）——**纯核心禁 import android.\***（JVM 假绿围栏），容量守卫确定性截断 | 低 |
| `SafeFocus.kt` | v1.6.1 对话框恢复开态防崩：requestFocusSafe（等一帧＋runCatching——重建恢复帧 FocusRequester 晚挂载竞态） | 低 |
| `theme/`（子包） | 主题基础设施（v1.3）：`AppendoTheme` 双色板、success 扩展色、`ThemeMode` 三态、状态栏同步——见 `theme/CLAUDE.md` | 中 |

## 页面入口

（触发方式导读；路由图与路径参数约定以 `docs/architecture.md` §1.2 为准，不在此复制。）

- MainScreen 顶栏「主题」（浏览态常驻含 0 条；多选态隐藏）→ TopicListScreen → 点击主题卡片 → TopicWorkspaceScreen（可编辑工作区）
- MainScreen 菜单「归档管理」→ ArchiveListScreen → 点击归档卡片 → ArchiveDetailScreen（只读）
- 网格「归档」（第四格）→ 二选一对话框：「归档并清空」／「只归档，保留列表」（不导航，入口唯一）
- 菜单其余项：「打开文件」→ 外部查看器；「自定义目录」→ SAF 选择器；「外观」→ 三态对话框；「关于」→ 对话框

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
| 删除条目 | 右滑条目卡片 | 弹出确认对话框，按时间戳删除 |
| 复制全部 | 点击"复制全部"按钮 | 复制完整文件内容到剪贴板（剥离 ZWSP） |
| 分享全部 | 点击"分享全部"按钮 | 调用系统分享（剥离 ZWSP） |
| 归档 | 点击网格"归档"按钮（第四格） | 弹出二选一：「归档并清空」＝先备份为归档文件再清空／「只归档，保留列表」＝仅生成归档副本、主列表不动（v1.4 起替代长按"清空"） |
| 移入主题 | 顶栏多选图标进入多选态，勾选后点"移入" | 组合事务先写主题文档后删源条目；失败对话框保持可重试；成功/去重计数透明 |
| 主题页 | 顶栏"主题"图标 | 主题独立页：点击看只读详情、长按复制全文、左滑恢复（去重追加）、空主题右滑删除（非空右滑零响应） |
| 手动输入 | 点击"手动输入"按钮 | 弹出输入对话框，追加到文件（可「添加到日历」——先追加后跳日历） |
| 添加到日历 | 详情对话框/输入对话框主按钮区 | ACTION_INSERT 单向预填系统日历（v1.5.0 起应用内提醒退役，specs 16.C；先保存/追加再跳日历） |
| 归档恢复 | 左滑归档卡片 | 按时间戳|内容去重后追加到当前文档 |
| 主题恢复 | 左滑主题卡片 | 同归档恢复（共用 ArchiveRestore），确认框注明"追加后主题文档中仍保留这些条目" |

## EntryListScreen 复用设计

`EntryListScreen` 是跨场景复用的列表组件，通过参数控制行为：

- **MainScreen 使用**：传入 `readOnly = false`，启用滑动删除与点击编辑；`listState` 参数由 MainScreen 传入
- **ArchiveDetailScreen 使用**：传入 `readOnly = true`，禁用滑动删除（标题＝主题名本身，「详情」二字冗余删除，2026-11-11 裁定；原 `sourceFileName` 列表头小字参数已退役）

## 文件刷新机制

2 秒轮询、SP 逻辑 mtime、写点清单等刷新语义是系统级现状，只在 `docs/architecture.md` §6.1/§6.3 维护（含写入刷新全景图）。UI 侧纪律：新增写入路径必须补调 `setFileLastModified`，否则列表不刷新。

## 依赖关系

```
MainScreen → data/FileRepository
           → data/TopicRepository（移入主题目标列表/新建/首次指路旗标）
           → util/MarkdownFileFactory, MarkdownFileOperations
           → util/MarkdownFormatter
           → util/FileBasedMarkdownFile, SafMarkdownFile
           → util/TopicMoveTransaction, EntryKey（移入主题组合事务）
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
