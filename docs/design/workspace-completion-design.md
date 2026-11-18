# 主题工作区功能补全（己批次＝v1.6.0）— 特性设计文档

> **修订记录**
>
> | 日期 | 变更人 | 变更摘要 |
> |---|---|---|
> | 2026-11-18 | Yiyue + Claude | 首次创建（草案——待三视角设计评审与用户确认）。权威输入＝specs 16.D（定稿 2026-11-18）＋ test-cases.md v1.0.1（验收判据源）＋ 16.D 三视角评审收敛裁定。引用惯例：行号为 v1.5.0 源码实况、随编码漂移以源码为准 |

## 1. 概述与需求映射

| specs 条文 | 本设计落点 |
|---|---|
| 102(a)(b)(c)(d)(f)(h) 编辑/删除/批量删除/新增/同权/排序 | §4 工作区功能 |
| 102(e) 出口＋顶栏三动作＋溢出菜单 | §4.5 |
| 102(g)＋103 归档二选一＋主题归档件＋批量清理 | §5 |
| 104 改名 | §6 |
| 105 右滑误导修复 | §4.2（随删除落地） |
| 106 抽取契约/MoveFlow 迁移/消重/参数面 | §2 §3 |
| 107 复用盘点 | §9 |

批次性质：**混合批次，硬性拆段**——段A 抽取重构（§2 §3，独立提交先绿后进）→ 段B 工作区功能（§4~§6）→ 段C 收尾（§7 文档＋§10 用例补录）。

## 2. 对话框抽取契约（106）

### 2.1 组件签名（无状态渲染）

```
EntryEditDialog(          // 自 MainScreen 编辑对话框（约 :1000-1180）抽取
  entry: LinkEntry,           // 目标条目（含原文）
  draft: String,              // 草稿——宿主持有
  onDraftChange: (String) -> Unit,
  onSave: () -> Unit,         // 宿主单一保存路径（空校验归属宿主；成功清草稿关框）
  onRequestCalendar: () -> Unit, // 宿主：75 先落盘时序（见 2.3）
  onDismiss: () -> Unit       // 点外/取消/系统返回三通道同路（关窗不清草稿）
)
EntryInputDialog(          // 自 MainScreen 输入对话框（约 :1230-1350）抽取
  draft: String, onDraftChange, onAppend: () -> Unit,
  onRequestCalendar: () -> Unit, onDismiss: () -> Unit
)
```

组件内零 `remember` 业务态（焦点/键盘等 UI 态除外）；按钮＝「保存」（主）/「添加到日历」（OutlinedButton）/「取消」。

### 2.2 宿主草稿三件套（两宿主同构镜像）

`editDraft: String`＋`lastEditedTs: String?`（换条目重置为原文/同条目保留）＋`inputDraft: String`。**纪律：草稿 remember 禁止挂 reloadKey/filePath 键**——工作区重载不得清草稿（修订⑤）。

### 2.3 宿主回调时序（钉死）

- `onSave`：空校验（isBlank→toast 保持开框）→ 内容未变（编辑场景）→ 直接关框清草稿；有变 → 写文件（失败 toast 保持开框）→ 成功 toast＋清草稿＋关框＋刷新。
- `onRequestCalendar`：空校验 → 有修改先保存（失败保持开框 return）→ 宿主清草稿＋关框＋`CalendarLauncher.launch`（失败独立 toast「未找到日历应用」，不回滚）。
- **消重**：现状两按钮各内联一份保存序列 → 收敛为宿主单一 `performSave(): Boolean`，两回调消费（日历钮＝performSave 成功后续跳）；「查重计数＋DuplicateHintThrottle＋toast 选句」抽 `shared` helper（两宿主共用）。

### 2.4 MoveFlow 迁移（106③，段A 首个独立提交）

- `MoveVerb` 补 `MOVE_IN`；`MoveOutcomeMapping` 补移入组合（复用 16.B §24 词族——N>0 toastMovedCount/Skipped、N=0 toastMovedAllExisting、失败 TOAST_MOVE_FAILED 族含 SAF 变体、SameDocument guard）——`MoveOutcomeMappingTest` 补全组合。
- MainScreen 移入流程状态机（moveFlowStep/moveTarget/moveExecuting/moveFailed＋executeMove 约 130 行＋对话框栈接线）→ 改持 `MoveFlowHolder`；工作区 MoveVerb 路径不变。
- 纪律：本提交**纯等价重构**（MainScreen 移入行为零变化）——等价性断言组（§10）＋既有 249→210 套件全绿方进段B。

## 3. 归档二选一参数化（106 参数面）

```
runArchiveAction(
  sourceMd: MarkdownFileOperations,      // 主界面＝当前主文件；工作区＝FileBasedMarkdownFile(topicFile)
  archiveTarget: File,                    // 见 §5.1 命名
  toastVariant: (name) -> String,         // 主题侧含主题名（目的地标识）
  onCompleted: (shouldClear: Boolean, outcome: ArchiveOutcome) -> Unit
  // 主界面实现：resync 三态＋clearFileLastModified＋refreshEntryCount
  // 工作区实现：reloadKey++（＋清空路径由 shouldClear 分支处理）
)
```

`archiveFile()`/`archiveActionFor()` 已近纯函数（读码核实），微调即可双消费。

## 4. 工作区功能（102）

### 4.1 编辑（102(a)）
点击条目 → `EntryEditDialog`（宿主草稿三件套）；`updateEntry(ts, newContent)` 按 ts 命中**首条**（与主列表同语义——16.B 97(b) 有意差异仅删除）；保存成功 `reloadKey++`。

### 4.2 删除＋105 修复（102(b)）
右滑 → 确认框（error 红＋振动，沿 22/29/78 口径）→ `deleteEntries(setOf(EntryKey))` **同 key 全删** → `reloadKey++`；全删后文件归一化纯文件头（既有保证）→ 空态切新增引导句（TC-042 口径随批对齐）。右滑指示与动作一致（105 随此消除）。

### 4.3 批量删除（102(c)）
多选 bar 第三钮「删除」→ 确认框明示 N（`dlgBatchDeleteBody`）→ `deleteEntries(selectedKeys)` → `reloadKey++`＋退出多选。

### 4.4 新增（102(d)）
顶栏「＋」→ `EntryInputDialog`；`appendReturningTimestamp(content)`；重复提示非阻塞（共享 helper）；成功 toast `toastAppendedToTopic(name)`；**新增后刷新并定位到新条**（101(k)/(w) 断言）＋顶栏计数 N+1。

### 4.5 出口与顶栏（102(e)）
顶栏 actions＝「＋」（AddCircle）｜「复制全部」（TextButton，统一命名）｜「分享」（Share 图标）——排序按使用习惯 复制>分享>＋？**不**：视觉序「＋ 复制全部 分享」不动手序，评审走查定；**归档/清空入口收溢出菜单**（DropdownMenu：「归档…」二选一框）。`copyContent/shareContent` 提权 internal（零改动复用，出口铁律继承）；长按单条复制＝EntryListScreen 既有。

### 4.6 排序（102(h)）
工作区 `EntryListScreen` 与主列表同口径：`sortedByDescending { it.timestamp }`（EntryListScreen 内部既有排序——读码核实主列表即此式，工作区当前同列表组件天然一致；设计钉死口径防回归）。

## 5. 主题归档件（102(g)/103）

### 5.1 命名与解析
- 文件名：`Appendo_<主题名>_<yyyyMMdd_HHmmss>.md`；同秒冲突 → 追加 `_1/_2` 递增后缀（存在性检查循环）。
- `ArchiveRepository` 解析器扩展：识别两类前缀（`Appendo_<ts>` 旧式＋`Appendo_<主题名>_<ts>` 新式）；归档卡显示「来源：主题名」（旧式无来源标注）。
- 恢复目的地＝当前主文件（RestoreConfirmDialog 既有语义，README 注明）。

### 5.2 二选一框（工作区）
复用归档二选一对话框形态；正文一层明示作用域「将备份并清空主题『X』的 N 条」/「将备份主题『X』的 N 条（条目保留）」；TD-017 传承（归档失败不清空）；0 条 → NothingToArchive 拦截。

## 6. 主题改名（104）

- `TopicRepository.renameTopic(topic, newName)`：校验链复用（排除自身）＋锁内三步（先写新 created_ 键→`file.renameTo`→删旧键；commit 检查；失败回滚新键）——沿 §23.1 蓝图（读码核实仍有效）。
- UI：主题卡左滑改「改名」（`swipeLeftLabel` 参数既有）→ `RenameTopicDialog`（预填当前名，NewTopicNameDialog 变体）→ 成功 toast「已重命名」＋列表重载；左滑「恢复」退役（99）。
- 非空主题右滑钳 0 → 一次性指路 toast（「清空主题后方可删除」——Microcopy 常量）。

## 7. 微文案增量（§24 族）

| 常量/函数 | 字面（草案，设计评审定稿） |
|---|---|
| `dlgTopicArchiveClearBody(n, topic)` | 将备份并清空主题「X」的 N 条。 |
| `dlgTopicArchiveOnlyBody(n, topic)` | 将备份主题「X」的 N 条（条目保留）。 |
| `toastTopicArchivedAndCleared(name)` | 已备份并清空主题「X」，可在归档管理查看 |
| `toastTopicArchived(name)` | 已备份主题「X」 |
| `TOAST_TOPIC_CLEAR_GUIDE` | 已清空，可删除空主题 |
| `TOAST_TOPIC_DELETE_GUIDE` | 清空主题后方可删除 |
| `toastAppendedToTopic(name)` | 已追加到「X」（16.B §24.1 既有） |
| `dlgBatchDeleteBody(n)` | 将删除所选 N 条条目。（16.B §24.1 既有） |
| `DLG_TOPIC_RENAME_TITLE` 等 | 16.B §24.1 既有 |

## 8. 文件改动面

| 文件 | 改动 |
|---|---|
| 新 `ui/EntryEditDialog.kt` `ui/EntryInputDialog.kt` | §2.1 抽取（段A） |
| `ui/MoveFlow.kt` | MOVE_IN＋Mapping 组合（段A） |
| `ui/MainScreen.kt` | 对话框迁出（约 −340 行）＋MoveFlowHolder 接线＋共享 helper 消费（段A）；其余零功能改动 |
| `ui/TopicWorkspaceScreen.kt` | §4 全部＋§5.2＋onCompleted（段B，约 +350 行） |
| `ui/TopicListScreen.kt` | 左滑改改名＋钳 0 指路（段B） |
| `ui/TopicMoveDialogs.kt` | RenameTopicDialog＋批量删除框（段B） |
| `data/TopicRepository.kt` | renameTopic（段B） |
| `data/ArchiveRepository.kt` | 解析器扩展＋来源标注（段B） |
| `data/FileRepository.kt` | getArchiveFile 变体（主题名＋同秒后缀，段B） |
| `ui/Microcopy.kt` | §7 词族（段B） |

## 9. 复用盘点（107——随本设计产出）

| 重复点（文件:位置） | 处置 |
|---|---|
| MainScreen 移入流程 vs MoveFlowHolder（:165-198/:267-339） | 本批段A 解决 |
| 对话框双份保存逻辑（编辑 :1071-1136／输入 :1241-1299） | 本批段A 消重（§2.3） |
| 查重＋节流＋toast 选句（输入对话框内联） | 本批段A 抽 helper |
| TD-039 内联「已删除」族／TD-040 副文案／TD-042 EntryListScreen 死包装＋阈值重复／TD-043 ArchiveCard 死参 | 不满足门槛①（与改动面不重合，除 TD-042 若 §4.6 触及 EntryListScreen 则顺手）——维持 debt-tracker |
| 时间戳解析散布（MainActivity.parseTimestampFromFileName/ArchiveRepository） | 门槛③消费方 2——与 §5.1 解析器扩展重合（门槛①）→ **顺手收敛**入 ArchiveRepository 单点 |

准入门槛四条见 specs 107；不满足者不扩。

## 10. 测试映射（test-cases v1.1——设计确认后、计划制定前追加）

按 specs 验收载体清单：两消费方等价性断言组（P0，81 系口径双宿主）；主题归档五用例（P0：两路径/TD-017/命名来源断言/同秒不覆盖/两步删除全流程）；只归档路径；工作区草稿×重载镜像；长按复制出口；README 改写断言；改名 101(m) 全套映射；批量删除三钮断言；MoveOutcomeMapping MOVE_IN 组合（JVM）；renameTopic 中间态（JVM）；ArchiveRepository 解析器两类前缀（JVM）；归档名同秒后缀（JVM）。TC-042 口径切新增引导句。

## 11. 风险与对策（架构师 Top3 收敛）

| 风险 | 对策 |
|---|---|
| 等价性回归（340 行迁出触及修订④⑤最脆弱面） | 段A 独立提交＋等价性断言组 P0＋先绿后进 |
| 归档数据安全（覆盖/剔除/混排） | §5.1 命名＋解析器同步扩展＋同秒后缀＋P0 用例 |
| 混合批次失败归因 | 拆段硬排序（A→B→C），段间全量绿门槛 |
