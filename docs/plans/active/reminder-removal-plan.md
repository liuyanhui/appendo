# 自建提醒退役（R 批次）执行计划

> **修订记录**
> | 日期 | 变更人 | 摘要 |
> |---|---|---|
> | 2026-11-16 | Yiyue + Claude | 首次创建（定稿随设计同步——用户「按你的建议执行」授权）。权威输入＝specs 16.C（定稿，方案 A）＋`docs/design/reminder-removal-design.md`；取代戊批次 T-28~T-34 |

## 任务清单

### T-R1 拆除（单提交）
- **内容**：设计 §1 清单 13 生产文件＋5 测试文件删；§2 修改面摘除（Manifest 权限/receiver、Application 渠道、MainActivity 深链、MainScreen 七处、EntryListScreen 徽标、TopicWorkspaceScreen/TopicMoveDialogs K 行、TopicRepository ts 缓存、Microcopy 词族）；测试随删（TopicRepositoryTest 缓存 5 用例）。
- **边界**：不触 CalendarLauncher/CalendarEntryMapper（零改动复用）；不触 MoveFlow/MoveOutcomeMapping。
- **评估标准**：□全仓 grep `Reminder|AlarmScheduler|scrollTs|OrphanReconciler|EntryLocator` 源码零残留（tag 存档与 docs 除外）□全量绿 □装机冒烟（主列表/工作区/移出/转移/详情→添加到日历各一遍）。

### 进度（执行时追加）

- **T-R1 拆除（2026-11-16，代码完成；装机冒烟待设备重连）**：删 13 生产文件（reminder 包 8＋util 提醒族 4＋ReminderTimePickerDialog＋AppendoApplication 死渠道）＋5 测试文件；Manifest 三权限/双 receiver 删；MainActivity 深链全链删；MainScreen 自检/选择器/替换确认/徽标/对账/清空/删除联动/scheduleReminder/rescheduleAll 摘除，「添加到日历」升详情与输入框主按钮（保存先落盘语义保留）；EntryListScreen 徽标、TopicWorkspaceScreen 定位与 K 行、TopicRepository ts 缓存整段、Microcopy reminderKeepLine 摘除；测试删 39 用例（含 TopicRepositoryTest 缓存 5 例）。全量 **210/0/0/0**＋assembleDebug 通过＋源码 grep 零残留。git：−2157/+136。
- **T-R2 文档义务（2026-11-16，完成）**：README 五段改写＋v1.5.0 版本行；CHANGELOG v1.5.0 未发布段；architecture.md 提醒面全量退役改写（§1/§2/§3.4/§3.6/§5/§6/§7/§9＋修订记录行）；specs 第 13 章整章退役注；debt-tracker TD-049/050 关闭（TD-048 SameDocument 保留）。**CLAUDE.md 更新清单待用户同意**（见下）。

### T-R2 文档义务
- **内容**：设计 §5——README/CHANGELOG v1.5.0 段/architecture.md/specs 第 13 章就地退役注/debt-tracker（TD-049/050 关闭）。
- **评估标准**：□README 已知限制含「日历事件自管」「存量作废」两句 □各级 CLAUDE.md 更新清单列出征用户同意。

### T-R3 回归冒烟＋版本检查
- **内容**：真机走查一遍主路径；确认 v1.5.0 范围表述＝主题工作区化＋滑动文字修复＋提醒退役＋日历入口。
- **评估标准**：□走查过 □版本表述落 CHANGELOG。
