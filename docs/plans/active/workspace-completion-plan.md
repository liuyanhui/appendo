# 主题工作区功能补全（己批次＝v1.6.0）执行计划

> **修订记录**
> | 日期 | 变更人 | 摘要 |
> |---|---|---|
> | 2026-11-18 | Yiyue + Claude | 首次创建：权威输入＝specs 16.D（定稿）＋design/workspace-completion-design.md（定稿）＋test-cases v1.1（判据源）。段A/B/C 硬排序；每任务受影响面以 TC-ID 钉死（test-cases §0.3.1-1） |

## 任务清单（段A → 段B → 段C，段间全绿门槛）

### 段A 抽取重构（独立提交×2，先绿后进——§0.3.1-2）

**WA-1 MoveFlow 迁移**（首个独立提交）
- 内容：`MoveVerb.MOVE_IN`＋`MoveOutcome.guideSuffix`＋Mapping 补 MOVE_IN 组合（移入失败词族/SourceDeleteFailed 重试＋刷新/refreshMain 语义改写）＋`MoveFlowHolder.close()` 改不清 newTopicInput；MainScreen 移入状态机（:165-198/:267-339/对话框栈）迁 Holder＋指路旗标宿主消费。
- 边界：`MoveFlow.kt`、`MainScreen.kt`、`MoveOutcomeMappingTest`（扩 MOVE_IN 全组合）。
- 受影响面：TC-050（迁移回归＋指路不丢）；门槛＝当期全量 JVM 绿＋TC-050 真机走查。

**WA-2 对话框抽取**
- 内容：新 `EntryEditDialog`/`EntryInputDialog`（无状态，§2.1 渲染面钉死项全落：时间戳行/占位符常量化/标题字面）；MainScreen 改消费＋草稿三件套就位＋`performSave` 单源（行为修正 1：保存失败保持开框）＋查重节流 helper（输入保存路径专用）。
- 边界：新两文件＋`MainScreen.kt`＋`Microcopy.kt`（占位符/标题常量）＋新 helper 落 `TopicMoveDialogs.kt` 或独立文件。
- 受影响面：TC-002/003/004/005/006/006b（主宿主对话框，含新判据）/TC-060/061（日历时序）；门槛＝全量 JVM 绿＋上述真机走查——**全过方进段B**。

### 段B 工作区功能

**WB-1 归档参数化＋主题归档件**
- 内容：`ArchiveChoiceDialog` 共享件；`runArchiveAction` 参数化（ArchiveWordbook/onCompleted）；`FileRepository.getArchiveFile` 主题名变体＋同秒后缀单点（上限 100→Failed，主界面随批统一）；`ArchiveRepository` 解析器尾部锚定三类形态＋`ArchiveFile.sourceTopic`；`ArchiveListScreen` 来源标注；`MainActivity` 路由构造点；时间戳解析调用点归一。
- 受影响面：TC-020/021/022（回归）＋TC-097/097b/098/098b/099b。
- 边界：上述文件＋`Microcopy.kt`（归档词族）；JVM：解析器三类形态＋边界＋后缀件可见、getArchiveFile 同秒。

**WB-2 工作区编辑/删除/批量删除（含 105）**
- 内容：`onEntryClick`→`EntryEditDialog`（updateEntry 按 ts 首条）；`onEntrySwipeToDelete`→确认框→`deleteEntries` 同 key 全删（105 随解）；多选第三钮「删除」＋`dlgBatchDeleteBody` 框；空态切新增引导句。
- 受影响面：TC-090/090b/091b 草稿×重载（编辑腿）＋TC-093/093b＋TC-042（新判据）＋TC-043（三钮）。

**WB-3 新增/长按/出口/顶栏**
- 内容：顶栏「＋/复制全部/分享」＋溢出菜单（归档入口）；`EntryInputDialog` 接入（toastAppendedToTopic/定位顶部）；`onEntryLongClick` 复制接线；`copyContent/shareContent` 提权 internal。
- 受影响面：TC-091/091/091c/092/092b/094/094b/096。

**WB-4 改名＋左滑切换＋钳 0 指路**
- 内容：`TopicRepository.renameTopic`（三步锁内/回滚/原名无操作）；`RenameTopicDialog`；TopicListScreen 左滑恢复退役（消费＋TOPIC_RESTORE_NOTE 清理）；`SwipeCard.onSwipeAttemptWhenDisabled`＋会话级指路 toast。
- 受影响面：TC-030/032/033（新判据）＋TC-095/095b/096b（负向组在此一并验）。

### 段C 收尾

**WC-1 文档义务**：README（只读段/滑动差异二次变更/主题页功能行/85(a) 第三路径/归档来源与恢复目的地）；CHANGELOG v1.6.0 段；specs 85(a)/97(f) 就地注；各级 CLAUDE.md 评估（征同意）；debt-tracker（TD 状态）。
**WC-2 真机验收**：§8b 09x 全组＋受影响面全过＋TC-001 装机冒烟；证据归档本计划 §7；版本升位与发布另裁。

## §7 进度记录（执行时追加）

### WA-1 MoveFlow 迁移（2026-11-18，完成）

- **落盘**：MoveVerb.MOVE_IN 三动词全量；MoveOutcome 增 guideSuffix（指路后缀承载——旗标消费留宿主防丢失）；MoveOutcomeMapping 全组合（移入失败词族/SourceReadFailed SAF 变体/SourceDeleteFailed 重试＋refreshMain 组合/refreshMain 语义＝主文件增减方向）；MoveFlowHolder.close() 不再清命名草稿（修订⑤纠偏）；MainScreen ~130 行移入状态机＋对话框栈迁 Holder。新增 9 测试；全量 219/0/0/0。

### WA-2 对话框抽取（2026-11-18，完成）

- **落盘**：新 EntryEditDialog/EntryInputDialog 无状态共享组件（渲染面逐项钉死：时间戳行/占位符常量化/标题字面）；MainScreen 改写 performEditSaveCore/performAppendSave 单源消重（两按钮共用）；行为修正 1：保存失败保持开框不清草稿（修订⑤）；chooseAppendToast helper（查重＋节流两宿主共用——日历路径不带查重沿现状）；Microcopy 四常量落盘。全量 219/0/0/0。

- **段A 门禁状态**：全量 JVM 219 绿 ✓；TC-005/006/006b/050/060/061 真机回归未补（设备离线）。

### WB-1 归档参数化＋主题归档件（2026-11-18，完成）
- ArchiveFlow.kt 共享编排＋ArchiveWordbook＋ArchiveChoiceDialog；FileRepository 主题名变体＋同秒_n后缀（上限100不覆盖，主/题统一）；ArchiveRepository 尾部锚定三类解析＋sourceTopic＋archiveFileFor 单点；MainActivity 解析收敛；ArchiveListScreen 来源标注；9 测试。228 绿。

### WB-2 工作区编辑/删除/批量删除（含105）（2026-11-18，完成）
- onEntryClick→EntryEditDialog（ts首条/草稿镜像/失败保持开框）；右滑→error红振动确认→同key全删（105消除）；多选第三钮删除＋明示N＋退出多选。228 绿。

### WB-3 新增/出口/顶栏（2026-11-18，完成）
- 顶栏＋/复制全部/分享＋溢出归档；EntryInputDialog（toastAppendedToTopic＋重复旁注＋定位顶部）；长按复制；copy/share提权internal；归档接runArchive主题词族；空态切引导句。228 绿。

### WB-4 改名＋左滑切换＋钳0指路（2026-11-18，完成）
- renameTopic 三步锁内/createdAt保留/原名无操作/回滚；RenameTopicDialog；左滑恢复退役→改名；SwipeCard禁用尝试回调＋会话级指路；6 测试。234 绿。

- **段B 门禁状态**：全量 JVM 234 绿 ✓；09x 双宿主等价组＋主题归档组真机回归待设备解锁（WC-2）。{'}
