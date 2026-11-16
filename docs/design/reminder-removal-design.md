# 自建提醒退役·系统日历替代 — 特性设计文档

> **修订记录**
>
> | 日期 | 变更人 | 变更摘要 |
> |---|---|---|
> | 2026-11-16 | Yiyue + Claude | 首次创建（定稿）：specs 16.C 的技术具体化——拆除清单/日历承接形态/存量处置（方案 A）/测试处置/文档义务/执行计划骨架。权威输入＝specs 16.C（定稿）；拆除前完整态＝tag `archive/self-built-reminders-final` |

## 1. 拆除清单（整文件删除，13 个生产文件＋5 个测试文件）

| 层 | 文件 | 说明 |
|---|---|---|
| reminder/ | AlarmScheduler / ReminderStore / NotificationHelper / ReminderAlarmReceiver / ReminderBootReceiver / ReminderIntents / EntryLocator / OrphanReconciler | 整包删除（含 LocateOutcome 三态与对账——16.C 退役范围） |
| util/ | ReminderLogic / ReminderMeta / ReminderMetaCodec / ReminderText | `Recurrence` 枚举随 ReminderMeta 删 |
| ui/ | ReminderTimePickerDialog | 时间选择器随功能退役 |
| test/ | ReminderLogicTest / ReminderMetaCodecTest / ReminderTextTest / EntryLocatorTest / OrphanReconcilerTest | 专属测试随删 |

## 2. 修改面（消费方摘除）

| 文件 | 改动 |
|---|---|
| `AndroidManifest.xml` | 删 RECEIVE_BOOT_COMPLETED / POST_NOTIFICATIONS / SCHEDULE_EXACT_ALARM（含 USE_EXACT_ALARM 如有）权限＋两个 receiver 注册 |
| `AppendoApplication.kt` | 遗留 `write_feedback` 渠道创建随通知体系退役删除（唯一消费方为通知；渠道残留无害但代码不再创建） |
| `MainActivity.kt` | scrollTs 状态/onNewIntent/深链 LaunchedEffect/onConsumeScrollTs 全删；`topic_workspace` 路由 scrollTs 参数删（topics 点击入口保留）；ReminderIntents 引用删 |
| `ui/MainScreen.kt` | ①菜单「提醒自检」项＋自检对话框＋通知权限 launcher 链全删；②详情对话框「设提醒/取消提醒」主按钮与 ⏰ 状态行删，**「添加到日历」升为主按钮区**（CalendarLauncher 既有调用前移）；③手动输入对话框设提醒次级动作删、添加到日历保留；④⏰徽标链（ReminderStore 订阅/reminderMap）删；⑤对账段/清空分支/删除回调的 OrphanReconciler 调用删（删除回调还原为无联动——提醒不存在则无联动面）；⑥executeMove invalidateTopic 调用删；⑦scrollToTs/scrolledTs 深链滚动段删 |
| `ui/EntryListScreen.kt` | reminderMap/reminderLabel/⏰ 小图标渲染删 |
| `ui/TopicWorkspaceScreen.kt` | scrollToTs 参数与定位 LaunchedEffect 删；确认框 reminderCount 计算（hasUnfired）删 |
| `ui/TopicMoveDialogs.kt` | MoveConfirmDialog reminderCount 参数与 reminderKeepLine 行删 |
| `ui/MoveFlow.kt` | 保留（移出/转移 Mapping 与提醒无关；useSAF 变体句保留） |
| `ui/Microcopy.kt` | 删：reminderKeepLine、提醒自检/时间选择器词族、TOAST_REMINDER 族；**保留**：toastSameTopicGuard（SameDocument 护栏仍在）、移出/转移词族、topBarSelectedCount |
| `data/TopicRepository.kt` | ts 缓存整段删（TopicTsEntry/topicTsCache/allTopicTimestampsLocked/invalidateTopic/invalidateAllTopics；消费方已亡）；listTopics/deleteTopic 持锁保留（事务撕裂防护仍需）；`mergedTopicFiles` 保留（listTopics 去重） |
| `test/TopicRepositoryTest.kt` | 缓存 5 用例删；mergedTopicFiles 2 用例保留；POSIX 读失败配方随 allTopicTimestampsLocked 删 |
| `test/MoveOutcomeMappingTest.kt` | 保留（无提醒耦合） |

## 3. 日历承接形态

- **入口**：详情对话框「添加到日历」升为主按钮区（原「设提醒」位）；手动输入对话框既有「添加到日历」次级动作保留。CalendarLauncher（ACTION_INSERT）/CalendarEntryMapper 零改动。
- **零日历权限**：维持单向写入；写入事件生命周期归日历，应用不读不删（README 已知限制注明）。
- **目的地标识**：日历事件标题沿用 CalendarEntryMapper 既有映射（首行摘要）。

## 4. 存量处置（specs 16.C 方案 A）

升级后 sidecar SP 文件（`appendo_reminders`）与已排闹钟**静默作废**：代码不再读写，文件残留无害（应用私有目录，卸载随清）；不弹提示、不迁移。README/CHANGELOG 醒目注明。

## 5. 文档义务（随拆除提交）

- README：功能概览/交互说明提醒段改写＋已知限制（「写入日历的事件需在日历中自行管理」「升级后既有应用内提醒不再生效」）；
- CHANGELOG v1.5.0（草稿段）：自建提醒退役条目；
- architecture.md：提醒相关章节（§5 联动/§6.1 补发行/§8 约束）删除改写；
- ui/util/data 各级 CLAUDE.md 提醒行：**待用户同意后改**（项目规则：CLAUDE.md 更新需用户同意）；
- specs 第 13 章 57~64 就地退役注；
- debt-tracker：TD-049/TD-050 随模块消亡关闭；TD-048（SameDocument 混合形态）保留。

## 6. 执行计划骨架（R 批次，取代戊批次 T-28~T-34）

- **T-R1** 拆除：清单§1 全删＋§2 摘除＋Manifest/Application——单提交，全量绿＋装机冒烟（主列表/工作区/移出/转移/日历入口各一遍）；
- **T-R2** 文档：§5 义务全落（CLAUDE.md 部分征同意）；
- **T-R3** 回归冒烟＋版本重定义检查（v1.5.0＝工作区化＋滑动修复＋日历入口；真机走查一次）。
