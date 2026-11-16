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

### T-R2 文档义务
- **内容**：设计 §5——README/CHANGELOG v1.5.0 段/architecture.md/specs 第 13 章就地退役注/debt-tracker（TD-049/050 关闭）。
- **评估标准**：□README 已知限制含「日历事件自管」「存量作废」两句 □各级 CLAUDE.md 更新清单列出征用户同意。

### T-R3 回归冒烟＋版本检查
- **内容**：真机走查一遍主路径；确认 v1.5.0 范围表述＝主题工作区化＋滑动文字修复＋提醒退役＋日历入口。
- **评估标准**：□走查过 □版本表述落 CHANGELOG。
