# 主题工作区功能补全（己批次＝v1.6.0）— 特性设计文档

> **修订记录**
>
> | 日期 | 变更人 | 变更摘要 |
> |---|---|---|
> | 2026-11-18 | Yiyue + Claude | 首次创建（草案） |
> | 2026-11-18 | Yiyue + Claude | **三视角设计评审修订（v2）**：架构/工程/测试 21 项必修全落——段A 等价口径重裁定（两处声明式行为修正）/MoveFlowHolder 草稿与指路旗标承载/归档解析尾部锚定含后缀形态＋同秒后缀主界面随批统一/词族对象 ArchiveWordbook/§8 补 MainActivity＋ArchiveListScreen/§7 九条全部新落标注/TC-ID 09x 段方案/新增定位去失实引用。**定稿（2026-11-18 用户确认「定稿」）**——test-cases v1.1 追加与执行计划启动 |

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

批次性质：**混合批次，硬性拆段**——段A 抽取重构（§2 §3 前半，独立提交先绿后进）→ 段B 工作区功能（§4~§6）→ 段C 收尾（§7 文档义务＋证据归档；**用例追加不在段C**——于设计确认后、计划制定前落 test-cases v1.1，见 §10）。

## 2. 对话框抽取契约（106）

### 2.1 组件签名（无状态渲染）

```
EntryEditDialog(          // 自 MainScreen 编辑对话框（:1026-1158）抽取
  entry: LinkEntry,           // 含 timestampDisplay（组件渲染时间戳行 labelSmall/primary）
  draft: String, onDraftChange: (String) -> Unit,
  onSave: () -> Unit,         // 宿主单一保存路径（空校验归属宿主）
  onRequestCalendar: () -> Unit, // 无参（草稿在宿主，specs 106② 带参形态据此偏差——设计声明）
  onDismiss: () -> Unit       // 点外/取消/系统返回三通道同路（关窗不清草稿；宿主收键盘）
)
EntryInputDialog(          // 自 MainScreen 输入对话框（:1196-1318）抽取
  draft: String, onDraftChange, onAppend: () -> Unit,
  onRequestCalendar: () -> Unit, onDismiss: () -> Unit
)
```

组件内零业务 remember（焦点/键盘/滚动等 UI 态除外：输入框自动聚焦弹键盘沿现状组件内自取 `LocalSoftwareKeyboardController`）。渲染面钉死：编辑框时间戳行＋占位符 `PLACEHOLDER_EDIT`＋minLines 5/10；输入框占位符**常量化**（现状直写「请输入要追加的内容」→ §7 词族）＋minLines 3/6；标题「编辑条目」/「手动输入内容」入 §7。

### 2.2 宿主草稿三件套（两宿主同构镜像）

`editDraft`＋`lastEditedTs`（换条目重置为原文/同条目保留）＋`inputDraft`；**禁止挂 reloadKey/filePath 键**。

### 2.3 宿主回调时序（钉死；含**两处声明式行为修正**）

- `onSave`：空校验（toast 保持开框）→ 内容未变（编辑）→ 关框清草稿；有变 → 写文件 → 成功 toast＋清草稿＋关框＋刷新；**失败 → toast「保存失败」＋保持开框不清草稿**（⚠ 行为修正 1：现状保存钮失败仍关框清草稿——修正依据＝2026-11-11 修订⑤「保存成功才清空」精神，随段A 落地，等价性断言按**新判据**书写）。
- `onRequestCalendar`：空校验 → 内容未变（编辑）→ 直接清草稿关框跳日历（沿现状，无 toast）→ 有变 → 先保存（失败保持开框 return）→ 清草稿＋关框＋`CalendarLauncher.launch`（失败独立 toast）。
- **消重**：宿主单一 `performSave(): Boolean` 两回调消费（日历钮＝performSave 成功后续跳）；**查重＋节流 helper＝输入场景保存路径专用**（现状仅保存钮有查重 :1279-1293——日历路径**不带查重**沿现状，防隐性扩散；⚠ 行为修正 2 的反面声明：统一 performSave 不给日历钮新增查重旁注）。
- 键盘收起归宿主 onDismiss/成功路径；组件不持有。

### 2.4 MoveFlow 迁移（106③，段A 首个独立提交）

- `MoveVerb` 补 `MOVE_IN`；`MoveOutcome` 增 `guideSuffix: String?`（首次指路后缀承载）；`MoveOutcomeMapping.map` 增参（verb=MOVE_IN 成功且宿主声明首次时由宿主拼 `firstMoveGuideSuffix`——**旗标消费归宿主**：MainScreen 沿现状 moved>0 时 `consumeFirstMoveGuide()`，防迁移静默丢失）。
- **Mapping MOVE_IN 组合三缺口**：失败族用移入词族（`TOAST_MOVE_FAILED`/`TOAST_MOVE_FAILED_SAF_REVOKED`/`toastMoveFailedTargetWrite`/`toastMoveFailedSourceDelete`——Microcopy 已落码）；`SourceDeleteFailed` 在 MOVE_IN 方向＝`keepRetryState=true＋refreshMain=true`（现状 :312-317 要求刷新＋保持重试——`fail()` 结构拆两形态）；`refreshMain` 语义改写＝「条目离开/进入主文件的方向」（MOVE_IN 成功同样 true）。
- MainScreen 移入状态机（:165-198/:267-339＋对话框栈 :1321-1397）→ 改持 `MoveFlowHolder`；topics 列表宿主自持（沿工作区先例）。
- **`MoveFlowHolder.close()` 改为不清 `newTopicInput`**（⚠ 行为修正 2：现状 MainScreen 关窗特意保留命名草稿 :187、Holder 清之 :70——迁移即回归；Holder 纠偏至「关窗不清、创建成功才清」，工作区随批对齐，均按修订⑤ 口径）。
- 等价口径（评审 E1 裁定）：段A＝「主宿主对话框/移入行为回归（含两处声明式修正的新判据）」＋当期全量 JVM 套件绿；**双宿主等价性断言组＝段B 门槛**（工作区宿主就位后执行）。门槛引用＝test-cases §0.3.1-1＋-2。

## 3. 归档二选一参数化（106 参数面）

```
runArchiveAction(
  sourceMd: MarkdownFileOperations,     // 主界面＝当前主文件；工作区＝FileBasedMarkdownFile(context, topicFile)
  archiveWordbook: ArchiveWordbook,     // 词族对象（评审裁定：单 lambda 表达不了四分支矩阵）
  onCompleted: (shouldClear: Boolean, outcome: ArchiveOutcome) -> Unit
)
data class ArchiveWordbook(
  val archivedAndCleared: (String) -> String,  // 主界面传归档文件名；主题侧传主题名
  val archivedOnly: (String) -> String
)   // Failed/NothingToArchive 字面复用既有常量
```

归档段＋清空段＋toast 全留共享编排；`onCompleted` 仅宿主善后——主界面＝resync 三态＋clearFileLastModified＋refreshEntryCount；工作区＝`reloadKey++`（**不触碰 fileRepository 三态与主文件 mtime 键**——主文件未动）。工作区归档不经多选态、无须 exitSelectionMode。二选一对话框本体抽 `ArchiveChoiceDialog` 共享组件（段B 首项——防第三份复制）。

## 4. 工作区功能（102）

### 4.1 编辑（102(a)）
点击条目 → `EntryEditDialog`；`updateEntry(ts, newContent)` 按 ts 命中**首条**（与主列表同语义）；成功 `reloadKey++`。

### 4.2 删除＋105 修复（102(b)）
右滑 → 确认框（error 红＋振动）→ `deleteEntries(setOf(EntryKey))` **同 key 全删** → `reloadKey++`；全删归一化纯文件头（clear()＝atomicWrite(FILE_HEADER) 与归一化同构，读码核实）；空态切新增引导句（TC-042 随批改判据）。105 随此消除（补传 `onEntrySwipeToDelete` 即指示与动作一致）。

### 4.3 批量删除（102(c)）
多选 bar 第三钮「删除」→ 确认框 `dlgBatchDeleteBody(n)` → `deleteEntries(selectedKeys)` → `reloadKey++`＋退出多选。

### 4.4 新增（102(d)）
顶栏「＋」→ `EntryInputDialog`；`appendReturningTimestamp`；重复提示非阻塞（共享 helper）；成功 toast `toastAppendedToTopic(name)`；顶栏计数 N+1。**新增后定位**：`appendReturningTimestamp` 返回 ts → `reloadKey++` → 载入完成后 `listState.scrollToItem(0)`（倒序新条恒 index 0；同协程顺序无竞态）——**设计自有增强**（specs 101(k) 无此要求，声明不冒引；如不要可一行去留）。

### 4.5 出口与顶栏（102(e)）
顶栏 actions＝「＋」（AddCircle）｜「复制全部」（TextButton）｜「分享」（Share 图标）；归档/清空收**溢出菜单**。`copyContent/shareContent` 提权 internal；**长按单条复制需接线**（现状工作区未传 `onEntryLongClick`＝有振动无复制——非零改动，§8 落 TopicWorkspaceScreen）。

### 4.6 排序（102(h)）
EntryListScreen 内部既有 `sortedByDescending` 两消费方天然同口径（读码核实 :117）；设计钉死口径＋v1.1 排序用例防回归（EntryListScreen 本身零改动）。

## 5. 主题归档件（102(g)/103）

### 5.1 命名与解析
- 文件名：`Appendo_<主题名>_<yyyyMMdd_HHmmss>.md`；同秒冲突 → `_<n>` 递增后缀（存在性检查，**上限 100 次→放弃返 Failed 不覆盖**——数据安全优先）。**后缀逻辑落 `FileRepository.getArchiveFile()` 单点、主界面随批统一**（消除两套归档命名行为；test-cases §9 同秒条随批改「已修复」）。
- 目录恒 `getArchiveDirectory()`（与整文件归档同目录——落 topics/ 则列表扫不到）。
- **解析器（尾部锚定，三类形态）**：
  - 旧式：`^Appendo_(\d{8}_\d{6})(?:_(\d+))?\.md$` → sourceTopic=null
  - 新式：`^Appendo_(.+)_(\d{8}_\d{6})(?:_(\d+))?\.md$` → sourceTopic=组1
  - 同秒后缀 `(?:_(\d+))?` 两式皆含（缺此则后缀件列表不可见、恢复失联——评审必修）；主题名可含下划线/纯数字，只能尾部锚定不能首段判式。
- `ArchiveFile` 增 `sourceTopic: String?`；归档卡副标题拼「来源：X」（旧式无标注）；`MainActivity` archive_detail 路由构造点同步（§8）。
- 恢复目的地＝当前主文件（README 注明）。

### 5.2 二选一框（工作区）
`ArchiveChoiceDialog`（§3）；正文一层明示作用域「将备份并清空主题『X』的 N 条」/「将备份主题『X』的 N 条（条目保留）」；TD-017 传承；0 条 → NothingToArchive 拦截。

## 6. 主题改名（104）

- `TopicRepository.renameTopic(topic, newName)`：校验链（**调用方排除自身**，TopicNameRules 零改动；**原名提交＝无操作成功关框**）＋锁内三步（先写新 created_ 键 commit 检查 → 同目录 renameTo → 删旧键；renameTo 失败回滚新键）。沿 §23.1 蓝图**剔除末步 invalidateAllTopics**（ts 缓存已随 R 批次退役）。
- **三窗口断言语义（评审钉死）**：①写新键后失败→回滚后 SP 无残留＋主题旧态完整；②rename 后删旧键前失败→列表以文件名为准（listTopics 扫文件）不现双卡，重试收敛；③删旧键后 commit 失败→键残留无害（文件已更名，旧键成孤儿不消费）。JVM 注入失败（主）＋真机中间态构造（run-as 改 XML＋文件名模拟②态，沿 TC-041 先例）。
- UI：主题卡左滑「改名」（`swipeLeftLabel` 参数既有）→ `RenameTopicDialog`（NewTopicNameDialog 预填变体）→ 成功 toast「已重命名」＋列表重载；**左滑「恢复」退役**（TopicListScreen 恢复消费＋`TOPIC_RESTORE_NOTE` 清理，§8）。
- 非空主题右滑钳 0 指路：`SwipeCard` 增禁用态回调 `onSwipeAttemptWhenDisabled`（钳 0 现状无事件上抛，须加）；「一次性」＝**会话级 var**（不新增 SP 键——首次尝试后本会话不再弹）。

## 7. 微文案增量（九条**全部新落**——评审核实 §24.1 定稿表从未落码）

| 常量/函数 | 字面（定稿） |
|---|---|
| `dlgTopicArchiveClearBody(n, topic)` | 将备份并清空主题「X」的 N 条。 |
| `dlgTopicArchiveOnlyBody(n, topic)` | 将备份主题「X」的 N 条（条目保留）。 |
| `toastTopicArchivedAndCleared(name)` | 已备份并清空主题「X」，可在归档管理查看 |
| `toastTopicArchived(name)` | 已备份主题「X」 |
| `TOAST_TOPIC_CLEAR_GUIDE` | 已清空，可删除空主题 |
| `TOAST_TOPIC_DELETE_GUIDE` | 清空主题后方可删除 |
| `toastAppendedToTopic(name)` | 已追加到「X」 |
| `dlgBatchDeleteBody(n)` | 将删除所选 N 条条目。 |
| `PLACEHOLDER_INPUT`（常量化） | 请输入要追加的内容 |
| DLG_TOPIC_RENAME 族/SWIPE_LABEL_RENAME | 16.B §24.1 字面照录 |

## 8. 文件改动面

| 文件 | 改动 | 段 |
|---|---|---|
| 新 `ui/EntryEditDialog.kt` `ui/EntryInputDialog.kt` | §2.1 抽取 | A |
| `ui/MoveFlow.kt` | MOVE_IN＋Mapping 三缺口＋guideSuffix＋close() 不清草稿 | A |
| `ui/MainScreen.kt` | 对话框迁出（−340 行）＋MoveFlowHolder 接线＋helper 消费；**行为修正 1/2 落点** | A |
| 新 `ui/ArchiveChoiceDialog.kt` | 二选一框共享组件 | B |
| `ui/TopicWorkspaceScreen.kt` | §4 全部＋§5.2＋onCompleted＋onEntryClick/onEntrySwipeToDelete/onEntryLongClick 接线（+350 行） | B |
| `ui/TopicListScreen.kt` | 左滑改改名＋恢复消费清理＋钳 0 指路 | B |
| `ui/TopicMoveDialogs.kt` | RenameTopicDialog＋批量删除框 | B |
| `ui/ArchiveListScreen.kt` | 归档卡「来源：X」标注（ArchiveFile.sourceTopic 消费） | B |
| `data/TopicRepository.kt` | renameTopic | B |
| `data/ArchiveRepository.kt` | 解析器尾部锚定三类形态＋sourceTopic | B |
| `data/FileRepository.kt` | getArchiveFile 主题名变体＋同秒后缀（主界面统一） | B |
| `MainActivity.kt` | archive_detail 路由 ArchiveFile 构造点同步（sourceTopic/新解析） | B |
| `ui/Microcopy.kt` | §7 词族＋改名族 | B |
| `ui/SwipeCard.kt` | onSwipeAttemptWhenDisabled 回调 | B |

EntryListScreen/TopicBadge/ArchiveRestore（除来源字段外）零改动。

## 9. 复用盘点（107）

| 重复点 | 处置 |
|---|---|
| MainScreen 移入流程 vs MoveFlowHolder | 段A 解决 |
| 对话框双份保存序列 | 段A 消重（§2.3 performSave） |
| 查重＋节流＋选句 helper | 段A 抽 helper（输入保存路径专用） |
| 时间戳解析双份（MainActivity/ArchiveRepository） | **拆分表述**：解析扩展＝段B 功能验收项（硬需求——不改则主题件不可见）；调用点归一＝等价顺手项（门槛四条全满足） |
| 主界面同秒覆盖 | 随批统一（§5.1 后缀单点，门槛四条全满足——评审两说取统一） |
| TD-039/040/042/043 | 维持 debt-tracker（TD-042 原判「顺手」撤回——门槛③消费方 ≥2 不满足；§4.6 为口径钉死无代码面） |

## 10. 测试映射（test-cases v1.1——设计确认后、计划制定前追加）

- **编号方案**：新启 **09x 段**（登记段义「工作区（二段）」）；双宿主等价性＝每条一行、判据列「同 TC-00X（宿主＝工作区）＋差异注记（目的地 toast 含主题名/刷新＝reloadKey）」——不复制 00x 判据正文。
- **段A 门槛**：既有 TC-002~006b/060/061（主宿主对话框，判据含两处行为修正新口径）＋TC-050（移入回归）＋当期全量 JVM 绿；**段B 门槛**：09x 双宿主等价组＋主题归档五用例（两路径/TD-017/命名与来源/同秒后缀件**可见可恢复**/两步删除全流程）＋只归档路径＋恢复目的地（P0 数据安全）＋工作区草稿×重载镜像＋长按复制＋编辑首条命中注记＋删除同 key 全删＋新增宿主侧（101(k) 存活子集）＋105 指示断言＋排序口径＋不迁移负向组（轮询不刷/入口不现）＋指路 toast（会话级语义）＋改名 101(m) **存活子集**（剔除提醒/深链子项）＋批量删除三钮。
- **JVM 项**：MOVE_IN 组合（含 SourceDeleteFailed 重试＋刷新）/renameTopic 三窗口注入/解析器三类形态＋主题名含下划线/纯数字边界/同秒后缀。
- **既有用例状态操作**：TC-042 判据切新增引导句；TC-043 判据扩第三钮；TC-006/006b/006c 标「本批升级 P0」；TC-033 判据加指路 toast；§9 同秒条改「已修复」；81 系存活清单＝(a)/(a2)(a3) 日历半项/(b2)/(b3)/(c)/(g)/(h)。
- **受影响面候选（计划任务拆分时钉死）**：TC-020/021/022（归档）、TC-030/032/033（改名/指路）、TC-040~050（工作区）、TC-060/061（抽取回归）、TC-006 系（红线）。

## 11. 风险与对策

| 风险 | 对策 |
|---|---|
| 等价性回归（340 行迁出） | 段A 独立提交＋§0.3.1-2 先绿后进＋段A/B 门槛分列（§10）＋两处行为修正显式声明按新判据 |
| 归档数据安全（覆盖/剔除/混排） | 尾部锚定三类解析＋后缀单点统一＋上限 100 不覆盖＋P0 用例含后缀件可见可恢复 |
| 混合批次归因 | 拆段硬排序（A→B→C）＋段间批次门槛（§0.3.1：本批 P0＋受影响面全过） |
