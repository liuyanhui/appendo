# 主题分拣（移入主题）— 阶段交接文档

> **修订记录**
> | 日期 | 变更人 | 摘要 |
> |---|---|---|
> | 2026-10-30 | Yiyue + Claude | 首次创建：需求（specs 第 16 章 82~89）已经用户确认，多角色评审 / 设计 / 计划 / 编码未开始 |
> | 2026-10-30 | Yiyue + Claude | 多角色评审完成（产品/架构/用户三视角串行，36 项发现→14 议题）＋修订经用户确认落盘（specs 82~89 修订注＋89 验收补 (i)~(p)＋需求 17/第 15 章指针注；4 项裁定：移入即取消提醒／非空禁删＋空可删／同名阻止并指路／入口更名「归档与主题」；TD-027~029 登记）；下一步＝阶段2 设计 |
> | 2026-11-01 | Yiyue + Claude | 阶段2 设计推进：Generator 草案落盘（`docs/design/topic-move-design.md`，627 行 13 章；推荐私有目录 `topics/` 存储＋单锁组合事务）；O1~O3 用户裁定（chmod 受控复现／句式族对齐＋N=0 专用／主题分区在前）；三视角设计评审完成（架构师/资深工程师/资深测试工程师，串行，40 项发现→4 必修＋约 14 应补）；修订方案已获用户批准，**待执行** |
> | 2026-11-01 | Yiyue + Claude | 设计修订落盘（4 必修＋应补全量，707 行）＋Planner 初核通过＋**设计定稿（用户确认）**：阶段2 完成；下一步＝阶段3 计划 |
> | 2026-11-02 | Yiyue + Claude | 阶段3 计划完成：Generator 草案（11 任务）→ Q1/Q2 用户裁定（v1.4.0/versionCode 11；允许 SP 注入）→ Evaluator 第 1 轮（3 必修＋6 清理）→ Generator 修订 → Planner 抽查通过；计划状态「草案（待用户确认）」。**用户指示换机继续**——本批为交接打包推送，新会话自「确认计划 → T-01 编码」恢复 |
> | 2026-11-02 | Yiyue + Claude | **用户确认计划定稿**——阶段3 闭环；阶段4 编码启动，T-01（EntryParser 同源重构）执行中 |
> | 2026-11-02 | Yiyue + Claude | **T-01 完成**（Generator＋Planner 核验 5 条全 PASS，149 用例 `--rerun` 复跑全绿）；下一任务 T-02（EntryKey＋deleteEntries＋buildContentWithoutEntries）。遗留建议：FileBased/Saf `count()` 收敛 → T-11 登 debt-tracker |
> | 2026-11-02 | Yiyue + Claude | **T-02 完成**（Planner 核验 6 条全 PASS，`--rerun` 164/0/0/0）：EntryKey＋`deleteEntries` 两实现＋`buildContentWithoutEntries`；权限注入 Windows 等价替换获认可。下一任务 T-03（TopicMoveTransaction）。遗留建议 S1（全删×空内容区域组合用例）→ T-09/T-11 |
> | 2026-11-02 | Yiyue + Claude | **T-03 完成**（Planner 核验 5 条全 PASS＋走查 a~f 相容，`--rerun` 177/0/0/0，⑦ 并发 3 次重复全绿）：`TopicMoveTransaction` 组合事务落盘。下一任务 T-04（TopicRepository）。遗留建议 2 条（⑦ 区分度变体→T-09；设计 §6.4 行号漂移→T-11 更正） |
> | 2026-11-02 | Yiyue + Claude | **T-04 完成**（Planner 核验 5 条全 PASS，`--rerun` 187/0/0/0）：`TopicRepository` 落盘；⑥⑦ 注入配方实证更换获认可；**确证既有 flake**（nextTimestamp 亚毫秒重复 ts，0/4 复现）→ T-11 登债＋T-09 复跑口径。**执行模式修订（用户指示）**：Planner 核验与文件不相交的下一 Generator 流水线并行；T-05 进行中 |
> | 2026-11-02 | Yiyue + Claude | **T-05 完成**（提交 `0fa9cab`，Planner 干净树核验 7 条全 PASS，194/0/0/0；三偏离全采纳）：多选 UI 落盘。T-06 Generator 已与 T-05 Planner 并行启动（流水线模式） |
> | 2026-11-02 | Yiyue + Claude | **T-06 完成**（提交 `0513877`，Planner 核验 6 条全 PASS＋六偏离采纳，200 复跑双绿）：移入流程 UI（五对话框＋执行接线＋提醒联动六条）落盘；4 建议分流（含 ArchiveDetailScreen「已复制」直写已补发 T-07 顺路消除）。T-07 Generator 已并行启动 |
> | 2026-11-02 | Yiyue + Claude | **T-07 完成**（提交 `160e87f`，Planner 核验 8 条全 PASS＋六偏离采纳，干净树 200/0/0/0）：「归档与主题」分区列表＋主题详情＋恢复共用抽取（等价性双保险：零修改 9/9＋逐字节 diff）。5 建议分流（CLAUDE.md 陈旧/空主题详情文案→T-10·T-11；空源 toast 名词＋legacy 内联→T-11 debt-tracker；聚合计数→用户裁定）。T-08 Generator 已并行启动 |
> | 2026-11-02 | Yiyue + Claude | **T-08 完成**（提交 `05bd031`，Planner 核验 7 条全 PASS，33 项逐字节零差异，200/0/0/0 独立复跑）：微文案收口（§9 零补齐＋N/M/K 口径 KDoc）。遗留 1 条（:108 内联）→T-11。T-09 质量门进行中 |
> | 2026-11-02 | Yiyue + Claude | **T-09 完成（质量门通过）**：全量 200/0/0/0＋assembleDebug 成功＋§10.2/§10.4 核对记录落盘计划 §8（核对记录新增节）。**阶段4 编码闭环**（T-01~T-09 全部一次通过零返工，200 测试）。下一步＝**T-10 真机验收（用户执行，89(a)~(p)＋§10.4 人工回归，计划 §4 验收表）**→ T-11 文档同步 |
> | 2026-11-03 | Yiyue + Claude | **T-10 快捷验收通过**（用户判定；含 SAF 源移入实测；(c)(d)(i)(m)(p) 5 缺口并入合并验收）。真机记录：platform-tools 补装、RMX3560、数据备份 `D:/tmp/appendo-t10-backup/`。**用户裁定「甲」＝主题一等公民 IA 分家**（85 分区裁定反转，移动式数据模型不变）——回阶段1；**另发现**：本机 adb 曾缺失（platform-tools 空壳，杀软嫌疑）、主文件 SAF 模式（`personal/Appendo.md`） |
> | 2026-11-04 | Yiyue + Claude | 设计画布 agent 派出（baoyu-design claude-design 技能，现状＋甲变体画板，`docs/design/topic-ia-canvas/`）。链路：挑方案→specs 追加→设计追加→甲批次→合并验收→T-11 |
> | 2026-11-04 | Yiyue + Claude | **甲批次阶段1~3 全闭环**：specs 16.A 三视角评审定稿（合并方案 X/「外观」消歧/N 条在前）→设计修订二（§14~§18）三视角评审 20 项全落定稿→执行计划 `topic-ia-split-plan.md`（T-12~T-19）Evaluator 核验＋用户确认定稿。**阶段4 编码启动（T-12）**——后续进度记入该计划 §7，本快照以 `topic-ia-split-plan.md` 为甲批次权威进度源 |
> | 2026-11-05 | Yiyue + Claude | **甲批次编码链 T-12~T-17 全闭环（六任务零返工，213 测试全绿）**——含 T-15 API 限流中断由编排方接管补全（从紧核验过）；T-18 真机合并验收进行中（备份 `D:/tmp/appendo-t18-backup/` 含 topics/＋SP；第 1 轮 94(e) 首次指路＋用户自由走查 PASS，受控项进行中）。**验收中裁定序列**：网格「归档」格回红（`1a62d8d` 已装机）／「清理不归档」不采纳／**戊＝主题工作区化立项**（决策全景三视角评审收敛 C1~C7：L1 提醒解耦＋移出、L2 主题内编辑＋删除、L3 新增缓、乙设置页排后；反转面 84(c)/85(d)；**版本节奏 A：v1.4.0＝甲现在收尾，戊＝v1.5.0**；89(i) 照测）。本批为交接打包推送 |
> | 2026-11-06 | Yiyue + Claude | **T-18 真机合并验收全闭环**（94(a)~(f)＋89(c)(d)(i)(m)(p) 全过；TD-017/89(c) 目标写按降级口径；89(d) toast 逐字节命中；URI 失效真实冷启回退实证＋175 条还原；收尾视觉三问全 PASS）。**流程备查**：toast 日志仪表 APPENDO_TOAST（I 级）；chmod 注入 realme FUSE 不可行；89(p)/(m) 无全选功能以 15 选/189 条体量替代法。**下一步＝T-19 文档收尾（README/CHANGELOG/architecture.md/debt-tracker/版本 v1.4.0·versionCode 11）→ 发布 → 戊批次（v1.5.0）自 specs 16.B 启动** |

---

## 0. 本文档用途与恢复方法

**用途**：支持**换机恢复**（另一台机器 clone/pull 后继续）与**冷启动恢复**（全新会话、无原会话记忆）。读完本文 + `docs/specs.md` 第 16 章即可继续工作，不依赖任何会话内上下文。

**恢复方法（新会话首条提示词模板）**：

```
读取 docs/plans/active/topic-move-handoff.md 与 docs/plans/active/topic-ia-split-plan.md（甲批次执行计划），
按交接文档「下一步」章节继续：T-18 真机合并验收进行中（真机在手、受控项自 94(d) 起继续，
注入配方照抄 topic-ia-split-plan.md §4.1）→ T-19 文档收尾（v1.4.0/versionCode 11）→ 发布
→ 戊批次（主题工作区化）自 specs 16.B 起走完整流程（用户已裁定编组与版本节奏 A）。
```

**权威文件优先级**（冲突时以先者为准）：
1. `docs/specs.md` 第 16 章（82~89＋**16.A 修订二 90~94**，均已用户确认；戊立项裁定见设计修订记录 2026-11-05 行，16.B 待起草）
2. `docs/design/topic-move-design.md`（§1~§13 原批定稿＋**§14~§18 修订二定稿 2026-11-04**；修订记录含戊裁定行）
3. `docs/plans/active/topic-ia-split-plan.md`（**甲批次权威进度源**：T-12~T-17 闭环记录＋T-18 验收表＋T-19 义务）；`topic-move-plan.md`（原批 T-01~T-11 历史快照，其 T-10 缺口/T-11 义务已由 T-18/T-19 吸收）
4. 本文（状态快照与裁定记录）

---

## 1. 状态快照（2026-11-05）

- **流程位置**：原批（移入主题 T-01~T-11）✅ 编码闭环＋快捷验收（5 缺口并入 T-18）→ **甲批次（IA 分家 T-12~T-19）：阶段1~4 ✅＋T-18 真机验收 ✅ 全闭环（2026-11-06）→ 当前＝T-19 文档收尾（进行中）→ 发布 v1.4.0 → 戊批次（主题工作区化＝v1.5.0）自 specs 16.B 启动**（编组 L1/L2/L3＋版本节奏 A 已裁定）。
- **git 状态**：2026-11-05 交接打包推送（提交链自 `0878ee2` 起：specs 16.A→设计修订二→甲计划→T-12~T-17→裁定序列→本文档）；甲批次权威进度源＝`topic-ia-split-plan.md` §7。
- **真机状态**：RMX3560 已连、装最新 debug 包（1.3.1/10，升位在 T-19）；SAF 模式主文件 `/sdcard/personal/Appendo.md`；**验收备份 `D:/tmp/appendo-t18-backup/`（主文件＋3 SP＋topics/）**；adb 需全路径 `C:/Users/liuya/AppData/Local/Android/Sdk/platform-tools/adb.exe`（PATH 无；platform-tools 曾空壳已于 11-03 补装）。
- **另一未完流**：v1.3.x 微文案批次真机走查仍开放（见 `v1.3.x-microcopy-handoff.md`），与本流无关，勿混淆。

### 需求要点（详见 specs 第 16 章，此处仅冷启动导航用）

主列表多选条目 →「移入主题」→ 移动式合并到本地自由命名的主题文档。

**用户裁定（2026-10-30，全部已落入条文）**：
1. 目标 = 手机**本地**主题文档（非远端）
2. **移动式**（原条目从当前文件移除）
3. **独立本地功能**，与「碎片直投生产线」（raw-pile/grill-me/appendo-inbox-sync）无关
4. 一批只移入**一个**文档（多目标 = 多批操作）
5. 功能名 **「移入主题」**
6. 主题文档**自由命名** + 归档管理列表**分区展示**

**Claude 三项推论性决定（用户 2026-10-30 明确同意）**：
1. 主题文档命名不允许以 `Appendo_` 开头（前缀保留给整文件归档，作分区判定规则，83/85）
2. SAF 模式崩溃中断允许"两边都有"软中间态，重试幂等收敛（84(d)，对齐需求 39 分级）
3. 「移入」作为第 15 章术语表**第四类写盘动作**：确认"移入" / 成功"已移入 N 条（M 条目标已存在）" / 失败"移入失败"（88）

**用户评审裁定（2026-10-30，多角色评审后，均已落入 specs 修订注）**：
1. 移入即取消该批条目未触发提醒＋成功文案注明；恢复回当前文档后提醒不复活（已知限制，README 声明）（84c）
2. 主题文档非空禁删＋空可删；重命名本期不做（TD-027）（85a）
3. 新建同名＝阻止并指路；保留字补主文件名 `Appendo`（大小写不敏感）（83 修订）
4. 入口更名「归档与主题」＋分区标题「主题文档」＋确认/成功文案写明去向（84d/85e）

**硬约束**（移动式不背叛需求 41）：84 原子性（先写后删，绝无"两边都没有"）+ 86 恢复路径（复用归档恢复交互）。

---

## 2. 下一步（新会话按序执行）

1. **T-18 真机合并验收（当前）**：受控项继续——94(d) 只归档→TD-017（chmod 注入配方照抄 `topic-ia-split-plan.md` §4.1）→归档并清空＋提醒全消＋94(a) 0 条冷启→89(m)＋89(i) 合并轮→89(c)(d) 注入轮→89(p) 批量→深色走查；TD-024 人工为准、证据命令按存储模式替换（SAF 主文件用 `adb shell cat`，私有目录用 run-as 绝对路径——`files/topics/` 外部目录 run-as 被拒时用 `adb shell ls/cat` 直读）。
2. **T-19 文档收尾**：README（原 §12 处置列＋甲新增五项）／CHANGELOG v1.4.0（两批合并单条目，**按甲口径**——戊为 v1.5.0）／architecture.md 五处＋甲增／debt-tracker（原批 12 条＋甲批新债：EntryListScreen 死包装/常量重复、ArchiveCard 死参、:75 KDoc、T-15 建议 2）／版本 versionCode 11/versionName "1.4.0"。
3. **发布 v1.4.0** 后开启**戊批次（主题工作区化＝v1.5.0）**：specs 16.B 起草（含 84(c)/85(d) 反转牵连清单、主题页「恢复」去留裁定）→ 多角色评审（用户既定流程）→ 设计修订三（多角色评审）→ 计划 → L1（提醒解耦原子单元＋移出）→ L2（编辑＋删除）→ L3/乙视观察。
4. 每任务/阶段完成更新 `topic-ia-split-plan.md` §7 与本文档快照。

---

## 3. 关键背景（冷启动必读）

- **真实样例**：`D:\liuyh\code\project\github\raw-pile\grill-me\appendo-inbox-sync\samples\Appendo_20261028_sample.md`（24 条，2026-06-30~10-27，混杂英语学习/AI 观察/链接）。**在另一仓库**——换机时 raw-pile 也要 clone/pull，否则样例不可得；不可将其拷入本仓库。
- **管线裁定**：本功能与 grill-me/appendo-inbox-sync（待裁决 P1~P3）**互相独立**；将来投递功能是否覆盖主题文档，届时另行裁定。两处「列表多选」交互可互相借鉴，归属不同仓库。
- **写作风格**：specs 追加式、历史条文不删除；文档头部修订记录必须同步。

---

## 4. 换机环境要点（2026-10-30 本机实测）

| 项 | 值 / 位置 |
|---|---|
| 本仓库路径 | `D:\liuyh\code\project\github\appendo`（旧机为 `/home/claude/project/appendo`，历史文档中该路径已过时） |
| JDK | 21.0.6 LTS |
| 内存 | **15.8GB** —— 旧机「~1.87GB、禁并行 subagent、警惕全量测试打满内存」约束**在本机不成立**，多角色评审可并行 |
| Android SDK | `%LOCALAPPDATA%\Android\Sdk`（build-tools / platforms / platform-tools 齐备） |
| Gradle | wrapper 9.3.1（华为云镜像，随仓库）；Maven 阿里云镜像已写入 `gradle.properties`（随仓库） |
| 测试命令 | 全量单测须 `--max-workers=1` 串行（旧机经验，新机未验证是否必需，保守沿用） |
| 真机 | realme；TD-024：人工验收为准，adb 截图/自动点击不可依赖 |

> 2026-11-02 注：T-09 的 600MB 内存判据为旧机（~1.87GB）口径；本表所述新机（15.8GB）一般不触发，保守沿用无害。测试串行 `--max-workers=1` 同理（新机未验证是否必需，保守沿用）。

---

## 5. 关键文件索引

| 文件 | 内容 |
|---|---|
| `docs/specs.md` 第 16 章 | 需求权威源：82~89（原批）＋**16.A 修订二 90~94（甲批次定稿）**＋六处就地注 |
| `docs/design/topic-move-design.md` | 设计权威源：§1~§13（原批）＋**§14~§18 修订二（甲）**；修订记录含戊立项裁定（2026-11-05） |
| `docs/plans/active/topic-ia-split-plan.md` | **甲批次权威进度源**（T-12~T-19 任务定义＋§4 真机验收表＋注入配方＋§17.4 顺序链/备份清单） |
| `docs/plans/active/topic-move-plan.md` | 原批 T-01~T-11 历史快照（含 §8 质量门核对记录；T-10/T-11 义务已转移） |
| `docs/design/topic-ia-canvas/` | 甲设计画布（12 画板＋用户评论；编辑器自生文件已 gitignore） |
| `docs/plans/debt-tracker.md` | 技术债登记（TD-027~029＋T-19 待登清单见计划） |
| `docs/specs.md` 第 15 章 / `ui/Microcopy.kt` | 术语表体系——「移入」第四类动作＋甲批次 §16 增量 |
| `D:/tmp/appendo-t18-backup/`（不入库） | T-18 验收前备份：主文件＋appendo/appendo_reminders/appendo_topics 三 SP＋topics/ |
| `docs/plans/active/v1.3.x-microcopy-handoff.md` | 另一未完流（真机走查），勿混淆 |
| raw-pile `grill-me/appendo-inbox-sync/`（samples/ specs.md decisions.md） | 样例与管线论证，独立项目 |
