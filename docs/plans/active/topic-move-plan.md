# 主题分拣「移入主题」— 阶段3 执行计划

> **修订记录**
>
> | 日期 | 变更人 | 变更摘要 |
> |---|---|---|
> | 2026-11-01 | Claude（Generator） | 首次创建（**草案待评审**）：依定稿设计拆解 11 个串行任务（T-01~T-11）＋计划级 Evaluator 检查单＋89(a)~(p) 真机验收表（§10.1 注入配方照抄）＋风险注意事项＋开放问题 Q1/Q2 |
> | 2026-11-02 | Claude（Generator） | Evaluator 第 1 轮评估：13 项检查单 11 PASS/2 FAIL，3 必修＋6 可后置；按反馈修订（P1~P3 必修落 T-07/T-11：页面标题更名、displayName 小字、README 评估标准改按 §12 处置列；P4~P9 一并清理：计数口径统一 6 新增/12 修改、Q1/Q2 裁定同步进任务文本、§8.2 六条口径、交叉文件补 MainActivity、T-09 内存数值判据、R8 引用位置修正） |
>
> **状态：草案（待用户确认）**
>
> 依据（冲突时以先者为准）：
> 1. `docs/specs.md` 第 16 章（需求 82~89 含修订注，2026-10-30 用户确认）——验收基线
> 2. `docs/design/topic-move-design.md`（**定稿 2026-11-01，707 行**）——唯一技术权威源，本文任务拆分以其 §3（模块划分）/§6（接口）/§10（测试）/§11（验收五列表）/§12（风险 R1~R11）为直接输入
> 3. `CODINGRULES.md`（测试/常量规范）、项目 `CLAUDE.md`（任务定义格式、六阶段流程）
>
> 执行模式：Planner-Generator-Evaluator **严格串行**（同一时刻仅一个 subagent）；所有测试命令一律 `--max-workers=1`。

---

## 0. 概述与版本号建议

**概述**：本计划把「移入主题」定稿设计拆为 11 个可串行执行、可逐条验收的任务。依赖主线＝设计 §3 的分层依赖（util ← data ← ui）：先做 **EntryParser 同源重构**（行为零变化，为批量删除提供共享扫描地基），再补 **寻址原语与组合事务**（EntryKey / deleteEntries / TopicMoveTransaction），然后 **主题仓储**（TopicRepository），其上依次落 **多选 UI → 移入流程 UI → 分区列表**，随后 **微文案全套核对 → 全量单测与回归 → 真机验收 → 文档同步**。每个编码任务自带纯 JVM 单测（TD-003：Robolectric 不可用，沿 `FileBasedMarkdownFileTest` 的 mock Context + 真实临时目录先例）。

**版本号建议：v1.4.0（versionCode 11）——已裁定（2026-11-01，Q1，见 §6）**，理由：

- 本特性是**重大新功能**而非修补：新增存储子系统（`topics/` 目录协议）、新增 SP 文件（`appendo_topics`）、新增 6 个 Kotlin 源文件（设计 §3.1 表：EntryKey / TopicMoveTransaction / TopicRepository（`TopicNameRules` 同文件）/ TopicMoveDialogs / MultiSelect / ArchiveRestore）、新增多选交互面与「归档与主题」分区列表；
- 语义化版本下新功能应升 minor 位（1.3.1 → 1.4.0），与既有 1.3.0（新功能批）/1.3.1（微文案修补）的升位先例一致；
- T-11 按裁定值执行升位。

---

## 1. 任务依赖链总览

| 任务 | 一句话内容 | 硬依赖 | 产出性质 |
|---|---|---|---|
| T-01 | EntryParser 同源重构（parse-with-spans）＋ `countEntries` 公开 | — | 纯重构（行为零变化） |
| T-02 | `EntryKey` ＋ 接口原语 `deleteEntries`（两实现）＋ `buildContentWithoutEntries` | T-01 | util ＋单测 |
| T-03 | `TopicMoveTransaction`（MoveResult 五分类＋异常边界） | T-02 | util ＋单测 |
| T-04 | `TopicRepository`（命名校验 / createdAt SP / listTopics / deleteTopic） | T-01（countEntries） | data ＋单测 |
| T-05 | 多选模式 UI（入口 / retainedSelection / 顶栏 / 2x2 隐藏 / SAF 回退退出） | T-02（EntryKey） | ui ＋单测 |
| T-06 | 移入流程 UI（目标选择 / 新建指路 / 确认 / 失败重试 / 提醒联动） | T-03、T-04、T-05 | ui ＋Microcopy 增量 |
| T-07 | 「归档与主题」分区列表＋主题详情＋ArchiveRestore 抽取共用 | T-04（TopicRepository） | ui ＋迁移 |
| T-08 | Microcopy §9 全套常量核对＋grep 防线 | T-05、T-06、T-07 | 微文案收口 |
| T-09 | 全量单测＋编译＋§10.2 矩阵 / §10.4 回归代码级核对 | T-01~T-08 | 质量门 |
| T-10 | 真机验收 89(a)~(p)＋§10.4 人工回归（用户执行，TD-024） | T-09 | 验收记录 |
| T-11 | 文档同步（README R1~R11 / CHANGELOG / architecture.md / 版本号） | T-09（置于 T-10 后执行） | 文档 |

派发顺序＝T-01→T-11 线性串行（硬依赖为其子集）。说明两点：

- **MainScreen.kt 被 T-01/T-05/T-06/T-07 四个任务触碰**——严格按序执行、每任务收尾跑全量单测防交叉回归；
- **Microcopy 常量增量落盘规则**：T-05/T-06/T-07 各自把本任务消费的 §9 常量随任务落盘（CODINGRULES 禁止直写字面，不留待事后替换）；T-08 做全集核对与 grep 防线收口。

---

## 2. 任务列表（标准任务定义格式）

### T-01 EntryParser 同源重构＋countEntries 公开

- **任务ID**: T-01
- **描述**: 按设计 §6.3 路径 1 重构 `util/EntryParser.kt`：内部抽出**共享扫描函数**（一次遍历产出「条目列表＋每条目行区间」，parse-with-spans），公共 `parse` 保持签名不变、退化为丢弃行区间的薄包装；按 §3.2 将 `countEntries(content)` 公开为纯函数（宽松正则计数），并消重两处私有副本——`MainActivity.kt`（约 :153）与 `ArchiveRepository.countEntries`（约 :88）改调 `EntryParser.countEntries`（行为等价，纯消重）。
- **约束**:
  - 文件边界：仅 `util/EntryParser.kt`、`MainActivity.kt`（仅 countEntries 消重一处）、`data/ArchiveRepository.kt`（仅 countEntries 改调）、`Appendo/src/test/java/com/yiyue31/android/appendo/util/EntryParserTest.kt`（仅新增用例）；
  - **本任务行为零变化**：不实现 `buildContentWithoutEntries`（属 T-02）、不改 `parse` 公共签名与语义、不动 `format`/`formatForExport`；
  - 宽松正则计数形态不得收窄为秒级/毫秒级单形态（TD-010 防复发）。
- **任务产出**: 重构后的 `EntryParser.kt`（含内部共享扫描结构）＋两处消费方改调＋EntryParserTest 新增 countEntries 钉死用例。
- **评估标准**:
  - [ ] 既有测试**零修改**全绿（EntryParserTest 既有用例逐字未动；`./gradlew testDebugUnitTest --max-workers=1` 全量 0 失败）
  - [ ] 代码评审确认：`parse` 为共享扫描的薄包装，不存在第二套平行的条目边界逻辑
  - [ ] `countEntries` 为 `EntryParser` 公开纯函数；`grep -n "countEntries" Appendo/src/main/java/com/yiyue31/android/appendo/MainActivity.kt Appendo/src/main/java/com/yiyue31/android/appendo/data/ArchiveRepository.kt` 显示两处私有实现已删除、均改调 `EntryParser.countEntries`
  - [ ] 新增钉死用例覆盖三类计数：纯秒级、纯毫秒级、秒/毫混合（断言值逐条写明）
- **评估方法**: 编译＋全量单测（串行）＋grep＋代码评审。
- **评估结果**: （待评估）
- **当前轮次**: 0　**最大轮次**: 2　**状态**: 待执行

### T-02 EntryKey＋deleteEntries 接口原语

- **任务ID**: T-02
- **描述**: 新增 `util/EntryKey.kt`（§6.2：`data class EntryKey(timestamp, content)`＋`EntryKey.of(ParsedEntry)`，不提供 `asString()`）；`util/MarkdownFileOperations.kt` 接口新增 `deleteEntries(keys: Set<EntryKey>): Int?`（§6.3，KDoc 含「null ⟺ 操作未发生」语义）；`FileBasedMarkdownFile`（持锁＋一次 `atomicWrite`）与 `SafMarkdownFile`（持锁＋`readMainForWrite`＋`safAtomicWrite`，`.pending` 残留走既有软恢复）两实现同步实现；`EntryParser` 新增纯函数 `buildContentWithoutEntries(content, keys): Pair<String, Int>`——**消费 T-01 共享扫描的行区间**，不得另写平行算法。
- **约束**:
  - 文件边界：上述 4 个主文件＋`EntryParserTest.kt` / `FileBasedMarkdownFileTest.kt` 扩展；
  - 返回 `Int?`：失败返回 **null**（弃 -1 哨兵）；读失败中止不写（TD-012 纪律）；同 key 多条完全重复条目全部删除；
  - 分隔线判定必须引用 `MarkdownFormatter.SEPARATOR_LINE`——不复制 `buildDeletedLines` 硬编码 `"---"` 的既有瑕疵（EntryParser.kt:270），也**不顺手修改**该既有函数；
  - 语义钉死：空内容区域不产出 key、不可被删除、原样保留；TD-015 修复前旧数据「匹配看 key（parse 跳过语义）、删除看扫描行区间（遗留行随条目消失）」；全删归一化为纯 `FILE_HEADER`；CRLF / 无尾换行按 `lines()` 既有语义；
  - SAF 实现无 JVM 单测（TD-003）：与 FileBased 实现逐行对照评审＋真机 89(c)(d) 覆盖（T-10）；`ShareReceiverActivity` 不调用本方法不受影响（architecture.md §4.1 检查点）。
- **任务产出**: `EntryKey.kt` 新文件＋接口/两实现＋`buildContentWithoutEntries`＋两组测试扩展。
- **评估标准**:
  - [ ] `util/EntryKey.kt` 存在，含 `of(ParsedEntry)`，无 `asString()`；接口与两实现均有 `deleteEntries` 且 KDoc 含 null 语义
  - [ ] EntryParserTest 扩展覆盖设计 §10.2 第二行 ①~⑧ 全部：同秒碰撞只删所选 / 同 key 双条全删 / 首末条与前导 `---` 连带及文件头保留 / 内容含 `---`·`# Appendo`·时间戳形态行的 ZWSP 往返不误删不残留 / 空 keys 与无命中返回原文与 0 / CRLF·无尾换行·遗留未隔离行 / **全删输出恰为 `# Appendo\n`** / 空内容区域原样保留
  - [ ] FileBasedMarkdownFileTest 扩展：源目录 `setWritable(false)` / 源文件 `setReadable(false)` → `deleteEntries` 返回 **null 而非 0**
  - [ ] 代码评审确认：行算法仅消费共享扫描产物，无平行边界逻辑；分隔线引用 `SEPARATOR_LINE`
  - [ ] 全量单测 `--max-workers=1` 全绿
- **评估方法**: 编译＋单测＋grep（`SEPARATOR_LINE` 引用点）＋代码评审。
- **评估结果**: （待评估）
- **当前轮次**: 0　**最大轮次**: 2　**状态**: 待执行

### T-03 TopicMoveTransaction 组合事务

- **任务ID**: T-03
- **描述**: 新增 `util/TopicMoveTransaction.kt`（§6.4）：`MoveResult` 五分类 sealed interface（`Success(moved, skippedExisting, missingFromSource)` / `SourceReadFailed` / `TargetReadFailed` / `TargetWriteFailed` / `SourceDeleteFailed(moved, skippedExisting, missingFromSource)`，失败类不带 reason 载荷）＋ `move(source, target, keys)` 六步编排＋ `@VisibleForTesting` 故障注入参数 `faults: MoveFaults`（`FAIL_TARGET_WRITE` / `FAIL_SOURCE_DELETE`）；新增 `util/TopicMoveTransactionTest.kt`（设计 §10.2 第一行 ①~⑬）。
- **约束**:
  - 文件边界：仅新增上述两文件；
  - 纯 util 编排，不碰 Android UI；方法体**整体 `synchronized(FileOperationLock)`**（内置可重入，内部调用 source/target 既有方法不释放锁），禁止两段分离持锁（84(a)）；
  - **异常边界**：六个步骤各以 runCatching 包裹，IOException / RuntimeException 一切异常映射到所在段失败类；特别地 `FileBasedMarkdownFile.atomicWrite` 的 `createTempFile` 未捕获异常（FileBasedMarkdownFile.kt:143，目录只读时抛出）由 move 外层捕获映射 `TargetWriteFailed`——**勿改既有 atomicWrite 的异常边界**（89(c) 注入正落此分支，R11 不扩大改动面）；
  - 目标不存在视作空文档、以 `FILE_HEADER` 起步（懒创建兜底）；toAdd 按时间戳字符串升序拼接追加目标末尾、原时间戳原样；去重 key 与归档恢复同源（timestamp|content，parse 产物语义）；任一失败态不得出现「两边都没有」。
- **任务产出**: `TopicMoveTransaction.kt`＋`TopicMoveTransactionTest.kt`。
- **评估标准**:
  - [ ] 两文件存在；`MoveResult` 五分类与计数载荷口径与 §6.4 逐字一致（Success / SourceDeleteFailed 三字段齐全）
  - [ ] 测试覆盖 §10.2 第一行 ①~⑬ 全部 13 组，其中 ⑦ 并发终态判据＝双线程（move＋append）多轮后断言**终态一致**（弃时序断言）、⑪ 真实 IO 失败打在 `FileBasedMarkdownFile.deleteEntries` 上并断言 move 映射 `SourceDeleteFailed`、⑫ `N = moved + skipped + missing` 口径钉死、⑬ 秒/毫混合时间戳拼接序断言
  - [ ] 代码评审确认：move 方法体为单个 synchronized 块（grep `synchronized` 仅一处包裹全程）；六步骤各有 runCatching 映射
  - [ ] 全量单测 `--max-workers=1` 全绿
- **评估方法**: 编译＋单测＋grep＋代码评审（锁结构 / 异常映射 / 幂等收敛用例）。
- **评估结果**: （待评估）
- **当前轮次**: 0　**最大轮次**: 2　**状态**: 待执行

### T-04 TopicRepository 主题仓储

- **任务ID**: T-04
- **描述**: 新增 `data/TopicRepository.kt`（§6.5）：`TopicDocument` / `TopicCreateResult` / `TopicDeleteResult` / `TopicNameRules`（命名校验纯函数）＋ `topicsDir()`（baseDir 优先，null 兜底对齐 `FileRepository.getDefaultFile` 与 `ArchiveRepository.getArchiveDirectory` 先例，双候选目录合并扫描自愈）＋ `listTopics()`（createdAt 倒序，正向标识＝目录成员）＋ `findTopic`（大小写不敏感）＋ `createEmptyTopic`（校验链→`writeAll(FILE_HEADER)` 成功后才写 SP `created_<文件名含.md>`，**一律 `commit()`**）＋ `deleteTopic`（entryCount>0 拒绝；0 条先删文件成功后删 SP key）＋ createdAt 首见回写一次兜底（§5.2）；新增 `data/TopicRepositoryTest.kt`（§10.2 第三行 ①~⑧，**构造器注入 baseDir＝JUnit 临时目录，纯 JVM**）。
- **约束**:
  - 文件边界：仅新增上述两文件；
  - 命名校验顺序固定：空名→保留字（`Appendo` 全等 / `Appendo_` 前缀，均大小写不敏感）→非法字符（`/ \ : * ? " < > |`∪C0 控制）→长度（trim 后 ≤50）→同名检测（displayName 大小写不敏感）；
  - SP 存储：独立文件 `appendo_topics`，写入一律 `commit()`（TD-024 先例，对齐 ThemePreferences）；createdAt 只在创建/首见回写时写、移入永不更新（85(c) 排序不漂移）；条数实时读文件（`EntryParser.countEntries`）不入 SP；
  - `createEmptyTopic` 顺序约束：文件写失败不得残留 `created_` key（有 key 无文件＝幽灵主题）；
  - SP 的纯 JVM 可测性（Q2 裁定 2026-11-01，见 §6）：允许增设 `SharedPreferences` 注入参数（**默认参数取真实实例**，即 `context.getSharedPreferences("appendo_topics", …)`），不引入 Robolectric；执行时在任务产出中注明实际做法。
- **任务产出**: `TopicRepository.kt`＋`TopicRepositoryTest.kt`。
- **评估标准**:
  - [ ] 两文件存在；类型与函数签名与 §6.5＋Q2 裁定一致（`baseDir: File? = null` 构造器注入；允许 `SharedPreferences` 注入参数且默认参数取真实实例）
  - [ ] 测试覆盖 §10.2 第三行 ①~⑧ 全部：校验全分支（含 `appendo`/`APPENDO_` 变体、非法字符逐个、50 边界、同名大小写不敏感）/ 创建（.md 附加、FILE_HEADER 落盘、SP 写入）/ 列表 createdAt 倒序且**移入内容变更后 createdAt 与排序不变**/ SP key 丢失首见回写一次且二次读取不变 / 非空拒绝·空可删（文件＋SP key 同清）/ WriteFailed（baseDir 只读）**无 created_ key 残留** / deleteTopic Failed 分支 / 含秒级条目文档 entryCount 正确（TD-010）
  - [ ] `grep -n "apply()" Appendo/src/main/java/com/yiyue31/android/appendo/data/TopicRepository.kt` 零命中（全部 commit()）
  - [ ] 全量单测 `--max-workers=1` 全绿
- **评估方法**: 编译＋单测＋grep＋代码评审（校验顺序 / 顺序约束 / topicsDir 兜底）。
- **评估结果**: （待评估）
- **当前轮次**: 0　**最大轮次**: 2　**状态**: 待执行

### T-05 多选模式 UI

- **任务ID**: T-05
- **描述**: 新增 `ui/MultiSelect.kt`（`retainedSelection` 纯函数＋`LinkEntry.entryKey` 扩展＋多选态 contextual TopAppBar 组件）；改 `ui/MainScreen.kt`（多选态状态机与顶栏整体切换：关闭 ×＋「已选 N 条」＋「移入」TextButton；多选态隐藏 2x2 按钮网格与主标题副标题、禁用滑动删除、退出即恢复；浏览态顶栏多选入口图标——条目数为 0 时隐藏；`refreshEntryCount` 更新 entries 后接入 `retainedSelection`；SAF 失效对话框弹出时自动退出多选并清空选中集）；改 `ui/EntryListScreen.kt`（新增 `selectionMode` / `selectedKeys` / `onToggleSelection` / `onPreview` 参数，**均带默认值**，多选态点击＝打勾、长按＝预览回调）；新增 `ui/MultiSelectTest.kt`（§10.2 第四行 ①~③）。
- **约束**:
  - 文件边界：`MultiSelect.kt`（新）、`MainScreen.kt`、`EntryListScreen.kt`、`MultiSelectTest.kt`（新）＋`Microcopy.kt`（仅本任务消费的常量增量，见 T-08 说明）；
  - 入口＝`Icons.Default.CheckCircle`，`contentDescription = "多选"`（§7.1 方案 A）；浏览态既有交互（长按复制/滑动删除/点击编辑）零变化——多选态才接管；
  - 颜色全部经 `MaterialTheme.colorScheme`（勾选框 primary、选中卡片容器色 `surface → surfaceVariant`），零写死色、无新颜色 token（P6/P7）；辅助小字 `onSurfaceVariant`（P8）；勾选动效仅 `spring(Medium)` 缩放＋容器色轻变，无装饰动效（P4）；
  - 列表渲染勾选态按 key 查 Set，与 LazyColumn 复合 key 解耦（§7.3）；`EntryListScreen` 默认参数保证归档详情复用零改动；
  - **长按预览对话框本体由 T-06 交付**（§3.1 归属 `TopicMoveDialogs.kt`），本任务只交付 `onPreview` 回调接线（MainScreen 暂持空实现占位）。
- **任务产出**: `MultiSelect.kt`＋MainScreen/EntryListScreen 改动＋`MultiSelectTest.kt`＋Microcopy 增量。
- **评估标准**:
  - [ ] `ui/MultiSelect.kt` 与 `ui/MultiSelectTest.kt` 存在；测试覆盖三组：新条目插头部/中部后既有选中全保持、选中条目被删后剔除、空集/全保留边界——全绿
  - [ ] `EntryListScreen` 新参数均带默认值：既有调用点（MainScreen 浏览态、ArchiveDetailScreen）**零改动**编译通过
  - [ ] MainScreen 多选态五要素代码可指认：关闭 × 退出并清空 / 「已选 N 条」实时 / 「移入」N≥1 启用 / 2x2 网格与主副标题隐藏 / 滑动删除禁用；退出多选即全部恢复
  - [ ] SAF 失效对话框弹出→自动退出多选＋清空选中集（§7.2，代码路径评审）
  - [ ] 浏览态入口图标常驻且条目数 0 隐藏；`retainedSelection` 接入点在 entries 更新处（MainScreen.kt:239 附近）
  - [ ] `grep -n "Color(0x" ui/MultiSelect.kt` 零命中；全量单测 `--max-workers=1` 全绿
- **评估方法**: 编译＋单测＋grep＋代码走查（对照 §7.1/§7.2/§7.3 逐条）。
- **评估结果**: （待评估）
- **当前轮次**: 0　**最大轮次**: 2　**状态**: 待执行

### T-06 移入流程 UI

- **任务ID**: T-06
- **描述**: 新增 `ui/TopicMoveDialogs.kt`：对话框 1 目标选择（主题列表 createdAt 倒序＋末项「新建主题…」）、对话框 2 新建命名（OutlinedTextField 自动聚焦）、对话框 2b 同名指路（阻止并切换目标）、对话框 3 确认（三行正文＋K>0 提醒行＋执行中「移入中…」＋失败变「重试」保持打开）、多选长按预览对话框（时间戳小字＋全文滚动＋「复制」「关闭」，复刻编辑对话框同构布局）；改 `ui/MainScreen.kt` 接「移入」执行：`Dispatchers.IO` 调 `TopicMoveTransaction.move`、成功处置（关全部对话框＋退出多选＋toast 三态＋提醒联动＋`setFileLastModified`＋`refreshEntryCount()` 立即主动刷新）、失败处置（toast 原因附注＋对话框保持变「重试」＋保持勾选与目标；`SourceDeleteFailed` 例外同时触发刷新）；新建路径＝校验通过且无同名后、进确认框前 `createEmptyTopic`（取消留空文档，可删可复用）。
- **约束**:
  - 文件边界：`TopicMoveDialogs.kt`（新）、`MainScreen.kt`、`Microcopy.kt`（增量）；不直接改动 reminder 包（经既有 `AlarmScheduler.cancel`＋`ReminderStore.remove` 调用）；
  - **提醒联动严格按 §8.2 执行序（共 6 条）**：① 仅 `Success`（含 N=0）触发取消，一切失败态（含 SourceDeleteFailed）不取消；② K 在成功回调内**重算**；③ 同 ts 连坐防护——该 ts 条目未全部从源移走则不取消该提醒；④ 取消动作与删除条目联动同款；⑤ K>0 时 toast 追加「，已取消 K 条提醒」；⑥ 恢复不复活＝零代码保证，README 声明落 T-11（R1）；
  - 成功 toast 三态：M=0「已移入 N 条」/ M>0「已移入 N 条（跳过 M 条已存在）」/ N=0「M 条已在该主题中，已从当前列表移除」（§9.1 定稿字面）；
  - 确认正文含后果去向（84(d)：「移入后这些条目将从主列表消失，可在『归档与主题』的主题分区查看」）；「取消」关全部对话框但**保持多选态与选中集**；
  - 颜色：确认/创建/移入该主题＝primary、辅助小字 `onSurfaceVariant`、零写死色（P6/P7/P8）；预览「复制」出口经 parse 产物（无 ZWSP）；
  - 事务在 `Dispatchers.IO`（200 条＝2 次全文件写，不 ANR，89(p)）。
- **任务产出**: `TopicMoveDialogs.kt`＋MainScreen 移入执行接线＋Microcopy 增量常量。
- **评估标准**:
  - [ ] `ui/TopicMoveDialogs.kt` 存在，五个对话框（目标选择/新建/同名指路/确认含重试态/预览）齐备
  - [ ] §8.1 流程图逐节点可对照代码走查（含：新建校验失败 toast 且对话框保持、同名指路「移入该主题」直达确认「取消」回命名且输入保留、创建失败 toast 中止流程、确认「取消」保持多选与选中集）
  - [ ] §8.2 六条执行序逐条可指认（①~⑤ 在代码；⑥ 恢复不复活为零代码保证，由 T-11 R1 README 声明落实）；grep 确认 cancel/remove 仅出现在 Success 分支
  - [ ] 成功回调含 `setFileLastModified`＋`refreshEntryCount()`（§6.6 立即刷新）；`SourceDeleteFailed` 分支同样刷新、其余失败分支不刷新
  - [ ] 执行中按钮禁用＋字面「移入中…」；失败按钮字面「重试」
  - [ ] 本任务消费的 §9 常量已入 `Microcopy.kt` 且字面与 §9 定稿一致；全量单测 `--max-workers=1` 全绿
- **评估方法**: 编译＋单测＋grep＋代码走查（对照 §8.1/§8.2/§6.6/§9.1 逐条）。
- **评估结果**: （待评估）
- **当前轮次**: 0　**最大轮次**: 2　**状态**: 待执行

### T-07 「归档与主题」分区列表＋主题详情＋恢复共用抽取

- **任务ID**: T-07
- **描述**: 新增 `ui/ArchiveRestore.kt`：`RestoreConfirmDialog`＋去重追加循环**自 `ArchiveListScreen.kt:284-371` 原样迁移**为共用（归档/主题两路消费）；改 `ui/ArchiveListScreen.kt` 为「归档与主题」分区列表（§8.3：单 LazyColumn 两分区、**主题分区在前**、分区标题「主题文档」「归档」、空区不渲染、两区皆空整页空态「暂无归档与主题」/「归档与主题文件会显示在这里」；主题卡片复用 ArchiveCard 骨架＋「创建时间 · N 条」弱化小字、空主题副文案「空」；**非空主题滑动删除恒不触发（零响应）**、仅空主题启用右滑删除——error 红确认框「删除空主题」走 `TopicRepository.deleteTopic`；主题长按复制全文 `parse`＋`formatForExport`；恢复确认正文追加注明行「追加后主题文档中仍保留这些条目。」；`onArchiveClick` 签名扩展为携带 kind（如 `onFileClick: (File, ArchiveEntryKind) -> Unit`）；topics＋archives 合并加载 `Dispatchers.IO`）；改 `ui/ArchiveDetailScreen.kt`（新增 `screenTitle: String = "归档详情"` 可选参数，主题传「主题详情」）；改 `MainActivity.kt`（`archive_detail/{filePath}` 路由加可选 `kind` 查询参数，`AppendoNavHost` 消费点约 :110-116 改携带 kind 构造路由）；改 `ui/MainScreen.kt` 菜单「归档管理」→「归档与主题」（约 :415）；改 `ui/ArchiveListScreen.kt` 页面 TopAppBar 标题「归档管理」→「归档与主题」（:122 现硬编码，§8.3「标题同步」落点）；启用 `EntryListScreen.kt:76` 既有 `sourceFileName` 参数（现无调用方传值），由 `ArchiveDetailScreen` 在 kind=topic 时传 `displayName`（不含 .md）作文件名小字（§8.5 落点），归档详情不传、显示行为零变化。
- **约束**:
  - 文件边界：`ArchiveRestore.kt`（新）、`ArchiveListScreen.kt`、`ArchiveDetailScreen.kt`、`MainActivity.kt`、`MainScreen.kt`（仅菜单字面）＋`Microcopy.kt` 增量；
  - **迁移＝原样**：恢复对话框与去重循环逻辑逐行等价（等价性由 `ArchiveRestoreDeduplicationTest` 既有用例零修改全绿＋T-10 真机抽查保证）；迁出后 `ArchiveListScreen` 改为消费方；
  - 主题恢复 toast 复用第 15 章句式常量（`toastAppendedCount` / `toastAppendedCountSkipped`）；恢复后显式重载列表；
  - 颜色经 colorScheme：分区标题 `labelMedium`＋primary（组件级用途，P8 允许）、删除确认 error、卡片辅助小字 `onSurfaceVariant`；零写死色；
  - 归档区沿用 `Appendo_` 前缀判据（自身定义，不承担分区排除式判定）；主题正向标识＝`topics/` 目录成员（`TopicRepository.listTopics`）。
- **任务产出**: `ArchiveRestore.kt`＋四个文件改造＋Microcopy 增量。
- **评估标准**:
  - [ ] `ui/ArchiveRestore.kt` 存在；`ArchiveRestoreDeduplicationTest` **零修改**全绿（迁移等价性）
  - [ ] 分区结构五要素代码可指认：主题在前/归档在后/分区标题字面/空区不渲染/两区皆空整页空态文案
  - [ ] 非空主题滑动删除零响应（无确认框、无提示路径）；空主题右滑→error 红确认框→删除走 `deleteTopic`、失败 toast「删除失败」
  - [ ] 恢复确认正文含注明行；主题恢复 toast 经第 15 章既有常量（grep 无新直写）
  - [ ] `onFileClick` kind 签名落地且 MainActivity 消费点同步；路由 `archive_detail/{filePath}?kind=topic` 可达；归档详情默认标题「归档详情」不变
  - [ ] 页面标题同步：`grep -rn "归档管理" Appendo/src/main/java --include="*.kt"` **零命中**（菜单与 ArchiveListScreen 页面 TopAppBar 标题统一「归档与主题」）
  - [ ] 主题详情文件名小字：`grep -n "sourceFileName" ui/ArchiveDetailScreen.kt` 命中传参（kind=topic 传 `displayName`，不含 .md）；归档详情不传、显示行为零变化（89(h)）
  - [ ] `grep -n "归档与主题" ui/MainScreen.kt` 命中菜单项；全量单测 `--max-workers=1` 全绿
- **评估方法**: 编译＋单测＋grep＋代码走查（对照 §8.3/§8.4/§8.5）。
- **评估结果**: （待评估）
- **当前轮次**: 0　**最大轮次**: 2　**状态**: 待执行

### T-08 微文案全套核对（Microcopy §9 收口）

- **任务ID**: T-08
- **描述**: 对照设计 §9.1（11 行）/§9.2（17 行，含同行多常量）逐条核对 `ui/Microcopy.kt`：补齐 T-05~T-07 增量落盘后仍缺的常量/函数；数字口径注释（N = moved + skipped(+missing)、K 独立附注）；句式族对齐核对（「（跳过 M 条已存在）」对齐第 15 章、N=0 直陈句式、SAF 失效冒号句式对齐 `TOAST_APPEND_FAILED_SAF_REVOKED`）；grep 防线确认新 UI 代码零直写字面。
- **约束**: 文件边界：仅 `ui/Microcopy.kt`（＋若 grep 发现漏网直写，修正对应引用处）；字面以 §9 定稿为准不擅改；结构硬约束（动词唯一「移入」、附注句式族、颜色语义）不随字面调整而变。
- **任务产出**: §9 全套常量齐备的 `Microcopy.kt`＋对照核对结果。
- **评估标准**:
  - [ ] §9.1 全部 11 行＋§9.2 全部 17 行逐条在 `Microcopy.kt` 有对应常量/函数，**字面与 §9 定稿逐字一致**（评估时逐条对照打勾）
  - [ ] grep 防线零直写：`grep -rn "移入主题\|新建主题\|已选 \|删除空主题\|主题文档中仍保留\|已移入\|移入失败" Appendo/src/main/java --include="*.kt" | grep -v Microcopy.kt` → 仅 KDoc/注释命中（ui 层新增字面全部经 Microcopy）
  - [ ] 句式族三处对齐确认：M>0 跳过句式 / N=0 直陈 / SAF 冒号句式
  - [ ] 全量单测 `--max-workers=1` 全绿
- **评估方法**: §9 逐行对照表＋grep＋编译。
- **评估结果**: （待评估）
- **当前轮次**: 0　**最大轮次**: 2　**状态**: 待执行

### T-09 全量单测＋编译＋代码级回归核对

- **任务ID**: T-09
- **描述**: 执行全量单测与编译（§10.3 命令）；对照设计 §10.2 测试矩阵逐格核对覆盖完备性（未覆盖格须有明确去向指派）；对照 §10.4 既有行为回归清单做代码级核对（真机抽查部分移交 T-10）。
- **约束**: 测试命令固定串行：`./gradlew testDebugUnitTest --max-workers=1`；编译 `./gradlew assembleDebug`；不并行调度其他测试任务；**内存数值判据**：执行前 `free -m` 检查，available **< 600MB** 时停止后续编译/测试动作并报告等待（不强行执行、不叠加其他进程；释放内存后复测达标再执行）。
- **任务产出**: 测试与编译输出记录＋§10.2/§10.4 两张核对表（逐格结论）。
- **评估标准**:
  - [ ] `testDebugUnitTest --max-workers=1`：既有 142 条＋全部新增测试 **0 失败 0 错误 0 跳过**
  - [ ] `assembleDebug` BUILD SUCCESSFUL
  - [ ] §10.2 四行矩阵逐格核对：每个编号用例均存在且绿；唯一无 JVM 单测格＝SAF 源 `deleteEntries`，去向＝T-10 真机 89(c)(d)＋代码对照（明确标注）
  - [ ] §10.4 五行逐行核对：归档恢复迁移等价（单测绿）/ 整文件归档 countEntries 消重等价（T-01 钉死用例）/ 分享接收互斥（§10.2 ⑦ 终态用例）/ 提醒常规路径（`appendo_topics` 与 `appendo_reminders` 隔离，代码确认）/ 复制全部出口（`copyContent` 路径未被触碰，git diff 确认）
- **评估方法**: 命令输出留档＋两张核对表逐项勾选。
- **评估结果**: （待评估）
- **当前轮次**: 0　**最大轮次**: 2　**状态**: 待执行

### T-10 真机验收（89(a)~(p)＋§10.4 人工回归）

- **任务ID**: T-10
- **描述**: 用户在真机（realme）执行 §11 验收五列表 89(a)~(p) 全部 16 项＋§10.4 人工回归抽查（见本文 §4 真机验收表）。TD-024：**人工观察为准、日志/SP/文件为事实源**，不依赖自动化截图/点击；故障注入配方照抄 §10.1。
- **约束**: 执行者＝用户；每项按「构造方法→人工判据→文件/日志证据」三列留档；预检失效时按设计降级口径标注「单测覆盖＋日志佐证」；发现缺陷→缺陷清单回流对应编码任务在轮次内修复（不自行扩大任务范围）。
- **任务产出**: 真机验收表逐项勾选结果＋证据摘要＋（如有）缺陷清单。
- **评估标准**:
  - [ ] 89(a)~(p) 16 项逐项 PASS（或按降级口径标注并通过）
  - [ ] §10.4 人工回归抽查通过（恢复等价抽查 / 整文件归档 / 移入前后分享接收 / 提醒到点抽查）
  - [ ] 全部证据按表留档（run-as cat / ls / dumpsys alarm / SP xml 前后对照）
  - [ ] 缺陷项（如有）已回流并闭环
- **评估方法**: 验收表逐项核对＋证据复核。
- **评估结果**: （待评估）
- **当前轮次**: 0　**最大轮次**: 2　**状态**: 待执行

### T-11 文档同步＋版本号

- **任务ID**: T-11
- **描述**: 按设计 §3.3 文档同步义务与项目「任务完成检查」规则落盘：`README.md`（按 §12 处置列逐条落实＋需求 80(b) 注明义务随更名更新＋「换机与备份」小节补记 R5＋交互说明补记 R6/R7/R10）；`CHANGELOG.md`（v1.4.0 条目，Q1 裁定值）；`docs/architecture.md` **数据协议章节必须同步修订**（§2 模块地图新文件 / §3.5 主题文档协议与 `topics/` 目录 / §3.6 SP 清单增 `appendo_topics` / §3.3 出口表增「主题长按复制全文」行 / §6.3 新写盘点位 / §11 存储层·UI 清单核对）；`docs/plans/debt-tracker.md`（编码中产生的新债补登；TD-027~029 已登记不重登）；`Appendo/build.gradle.kts` 版本升级。
- **约束**: 文件边界：README.md、CHANGELOG.md、docs/architecture.md、docs/plans/debt-tracker.md、Appendo/build.gradle.kts（versionCode/versionName 两行）；architecture.md 修订为追加/更新对应小节，不删历史；版本号按裁定执行——v1.4.0（versionCode 11）（Q1 裁定 2026-11-01，见 §6）；真机验收产生的字面微调（如有）一并同步。
- **任务产出**: 四份文档修订＋版本号升级。
- **评估标准**:
  - [ ] README 按 §12 **处置列逐条落实**（十一行二值核对表：处置列要求与实际产出一致）——处置为 README 声明/注明的项（R1/R2/R3/R5/R6/R7/R10/R11）有对应声明文字；R8 不展开（核对设计 §7.3/§8.2 已注明即可，README 不写）；R4 核对 TD-027 已登记；R9 核对无新增路径要求（不产生 README 义务）；「归档」（立即执行）与「归档与主题」（跳转列表）区别已更新（R10/80(b)）
  - [ ] CHANGELOG 含本特性条目，版本号与 Q1 裁定一致
  - [ ] architecture.md 五处修订齐备：§2 / §3.5 / §3.6（`appendo_topics` 入 SP 清单）/ §3.3（主题长按复制全文出口行）/ §6.3（新写盘点）＋§11 清单核对
  - [ ] `grep -n "versionCode\|versionName" Appendo/build.gradle.kts` 显示 versionCode = 11 / versionName = "1.4.0"（Q1 裁定值）
  - [ ] debt-tracker 已核对（新债补登或注明无新债）
- **评估方法**: 文档逐节对照＋grep。
- **评估结果**: （待评估）
- **当前轮次**: 0　**最大轮次**: 2　**状态**: 待执行

---

## 3. Evaluator 检查单（计划级——评估本计划是否完备覆盖定稿设计）

1. **§3.1 六个新增文件**各有任务归属：EntryKey→T-02、TopicMoveTransaction→T-03、TopicRepository→T-04、TopicMoveDialogs→T-06、MultiSelect→T-05、ArchiveRestore→T-07（§3.1 表逐行核对）
2. **§3.2 十二个修改文件**逐行有任务指派：MarkdownFileOperations/FileBased/Saf/EntryParser→T-01/T-02；MainScreen→T-01/T-05/T-06/T-07；EntryListScreen→T-05；ArchiveListScreen→T-07；ArchiveDetailScreen→T-07；MainActivity→T-01/T-07；ArchiveRepository→T-01；Microcopy→T-05~T-08；README→T-11
3. **§3.3 文档同步义务**→T-11 全覆盖（含 architecture.md 数据协议强制修订）
4. **§5 数据模型**（模板沿用/原时间戳/createdAt SP/命名规则）→T-03、T-04 评估标准逐条可指认
5. **§6 接口设计**（6.1~6.6）→T-02/T-03/T-04 评估标准与约束逐条对应；§6.6 刷新联动→T-06 成功回调验收项
6. **§7 多选交互**（入口/状态机/选中集/预览）→T-05（预览本体在 T-06，分工已注明无缝隙）
7. **§8 UI**（8.1 流程/8.2 提醒联动/8.3 分区/8.4 恢复/8.5 详情/8.6 深色）→T-05/T-06/T-07 逐节对应
8. **§9 微文案**→T-08 逐行对照＋三处句式族对齐；常量增量落盘规则已定（T-05~T-07 随任务落，T-08 收口）
9. **§10 测试策略**：10.1 注入配方→本文 §4.1 照抄；10.2 四行矩阵→各任务评估标准逐编号对应＋T-09 完备性核对；10.3 命令→各任务约束；10.4 回归→T-09 代码级＋T-10 人工
10. **§11 验收五列表**→本文 §4.2 行动清单逐项提取（a~p 十六项齐）
11. **§12 风险 R1~R11**→T-11 按 §12 处置列逐条落实；R8 落点＝T-06 约束（§8.2 第 ③ 条连坐防护）＋设计 §7.3/§8.2 已注明（README 不展开）；R11 落点＝本文 §5 注意事项 1（不扩大改动面）
12. **计划质量**：每任务粒度≤半天；评估标准全部可验证（文件存在/测试绿/grep 命中/字面逐字），无「适当/合理」类模糊词；依赖无环、串行可行；每编码任务自带单测且 `--max-workers=1`
13. **与需求基线一致**：82~89（含修订注）逐条可在任务评估标准中找到落点；与 specs 冲突处以 specs 为准

---

## 4. 真机验收表（T-10 用，TD-024：人工为准、日志/SP/文件为事实源）

### 4.1 前置与故障注入配方（§10.1 照抄，勿凭记忆改写）

**前置数据**：默认模式主文件 ≥5 条测试条目；真机 realme；`adb` 可用；外部存储路径一律**绝对路径** `/storage/emulated/0/Android/data/com.yiyue31.android.appendo/files/…`。秒级历史时间戳条目（(a) 加强项）可用 run-as 向主文件手工追加秒级时间戳条目块构造（路径文件名以 `run-as ls` 实测为准）。

**89(c) 目标写入失败注入（chmod 只读目录受控复现）**：

1. **注入时点**：**选定/新建目标主题之后、确认「移入」之前**（此前注入会命中 `createEmptyTopic` 的 WriteFailed 分支，验不到事务写入段）；
2. **注入命令**：`adb shell run-as com.yiyue31.android.appendo chmod 500 /storage/emulated/0/Android/data/com.yiyue31.android.appendo/files/topics`（绝对路径＋对目录 chmod，非对文件）；
3. **预检**：`adb shell run-as com.yiyue31.android.appendo touch /storage/emulated/0/Android/data/com.yiyue31.android.appendo/files/topics/probe` 应报 `Permission denied`；若预检不生效（部分 ROM），**降级**＝该分支以单测注入参数覆盖＋真机 logcat 断言 `TopicMoveTransaction`/`FileBasedMarkdownFile` 失败日志，该项标注「单测覆盖＋日志佐证」；
4. 恢复：`chmod 700` 后一键重试应成功。

**89(d) 软中间态构造（确定性主证据）**：run-as 手工向主题文件追加该批条目块（主文件不动）→ App 内对同批执行移入 → 断言收敛；等效 In-App 构造＝(o) 同构造可连做；`am crash` 写入窗口仅**加分尝试**（不作为主证据）。

### 4.2 89(a)~(p) 行动清单（构造 / 人工判据 / 证据）

| 项 | 构造方法 | 人工判据（通过条件） | 文件/日志证据 |
|---|---|---|---|
| (a) | 造 3 条（含 1 条秒级更佳）→ 多选 3 → 新建「T-a」 | 主题详情 3 条、时间戳内容原样（秒级原样）；主列表少 3 条、计数同步 | `run-as cat` 主文件少 3 块；`cat …/files/topics/T-a.md` 与移入前源条目块逐字节比对 |
| (b) | 2 条移入 T-b → 主题恢复追加回 → 再选同 2 条＋1 新条移入 T-b | toast「已移入 1 条（跳过 2 条已存在）」；T-b 仅 +1 条；主列表清 3 条 | `cat …/topics/T-b.md` 3 块无重复 `timestamp\|content`；主文件无该 3 条 |
| (c) | §4.1 chmod 配方（选/建目标后、确认前注入＋probe 预检） | toast「移入失败（写入主题文档失败）」、对话框保持变「重试」；`chmod 700` 恢复后重试成功 | 注入前后主文件逐字节一致；重试后目标内容正确；（预检失效时）logcat 失败日志 |
| (d) | §4.1 run-as 手工向主题追加同批（主文件不动）→ App 内重试同批 | 反馈 N=0 直陈句式；主列表该批清空 | 重试前后主题条数不变（全 skipped）＋主文件无该批 |
| (e) | (a) 完成后左滑 T-a → 恢复 | 「已追加 N 条（跳过 M 条已存在）」；条目回归历史位置；确认框含「主题文档中仍保留这些条目」 | 主文件含恢复条目（原时间戳落回）；T-a.md 不变 |
| (f) | 长按 T-a 复制全文 → 粘贴 | 无 `##`/`---`/`# Appendo` 结构标记；条数齐、秒级行完整 | 粘贴至可显示不可见字符的编辑器/在线 zero-width 检查器：无 U+200B |
| (g) | 造 1 主题＋1 整文件归档；尝试命名 `appendo`、`Appendo_日记` | 分区标题「主题文档」「归档」、主题在前；条数与详情一致；保留字命名被拒且 toast 字面正确 | `run-as ls …/files/topics/` 与 `run-as ls …/files/` 与列表一一对应 |
| (h) | 退出多选后逐项复测长按复制/滑动删除/点击编辑；深色逐屏走查新界面；多选态长按预览（含多行/特殊行） | 既有交互不变；深色无不可读文字/浅色残块；预览全文＝条目原文、预览内「复制」可用 | 预览复制粘贴做 (f) 同款零宽检查 |
| (i) | 2 条各设 +2 分钟未触发提醒 → 移入；恢复主题后等过原触发时刻 | toast 含「，已取消 2 条提醒」；徽标即刻消失；恢复后提醒不复活 | `dumpsys alarm` 前后对照（PendingIntent 消失）＋`run-as cat shared_prefs/appendo_reminders.xml`（`reminder_<ts>` 消失） |
| (j) | 非空主题右滑（应零响应）→「恢复→清空→右滑删除」全流程；另建空主题直接右滑删 | 非空主题无可滑动反馈；全流程数据无损；空主题删除确认（标题红）→「已删除」 | `run-as ls …/files/topics/` 无该文件＋`appendo_topics.xml` 无对应 `created_` key |
| (k) | 依次尝试 `Appendo`/`appendO`/`APPENDO_`、同名 `t-k` 与 `T-K`、空名/纯空白、`a/b`、`a*b`、51 字符 | 变体与前缀全拒（统一字面）；同名弹指路→「移入该主题」直达确认；空/非法/超长 toast 正确且对话框保持 | `run-as ls …/files/topics/` 无非法/重复文件残留 |
| (l) | (a) 完成后主界面「复制全部」→ 粘贴 | 已移 3 条不在、其余完整、格式干净 | (f) 同款字符级零宽检查 |
| (m) | 全部条目多选（可配 (p) 200 条批）→ 移入 | 空状态引导文案＋计数归零；重开应用仍空 | `run-as cat` 主文件输出**恰为 `# Appendo\n`**（归一化断言） |
| (n) | 多选勾 2 条 → `adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "新条目" -n com.yiyue31.android.appendo/.ShareReceiverActivity` | 新条目插列表顶部、原 2 条勾选保持不错位、计数 +1 | logcat ShareReceiver 写入成功；主文件含新条 |
| (o) | 移入 N 条 → 主题恢复（追加回）→ 再移入同批（与 (d) 同构造可连做） | toast「N 条已在该主题中，已从当前列表移除」——成功语义无失败读感；主列表清空 | 主题条数不变＋主文件无该批 |
| (p) | `for i in $(seq 1 200); do adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "批量条目 $i" -n com.yiyue31.android.appendo/.ShareReceiverActivity; done`（间隔片刻防丢）→ 全选移入 | 「移入中…」可见、完成 toast「已移入 200 条」、无冻结/无 ANR 对话框 | `adb logcat -d \| grep -i "ANR in com.yiyue31.android.appendo"` 无记录；目标恰 200 条 |

### 4.3 §10.4 人工回归抽查（并入本表执行）

| 回归对象 | 抽查动作 | 通过判据 |
|---|---|---|
| 归档恢复迁移等价 | 同一测试归档文件恢复一次 | added/skipped 计数与 toast 字面与迁移前一致（`ArchiveRestoreDeduplicationTest` 真机互补面） |
| 整文件归档 | 菜单「归档」执行一次 | 归档文件生成/计数/toast 不变 |
| 分享接收互斥 | 移入前后各收一次分享 | 追加正常（89(n) 互补面） |
| 提醒常规路径 | 设提醒→到点响一次 | 触发正常（`appendo_topics` 不碰 `appendo_reminders`） |
| 复制全部格式化 | 即 4.2 (l) | 已含 |

---

## 5. 风险与执行注意事项

1. **R11 不扩大改动面**：`atomicWrite` rename 失败退化直接覆写的截断分支（FileBasedMarkdownFile.kt:151-155）为既有系统性风险——T-03 **勿修**既有 atomicWrite 异常边界（createTempFile 未捕获分支由 move 外层捕获映射），不顺手改 `buildDeletedLines` 硬编码 `"---"` 瑕疵与其他既有瑕疵；新发现的债登 debt-tracker（T-11）。
2. **TD-003 Robolectric 不可用**：全部新单测纯 JVM——FileBased 路径沿 `FileBasedMarkdownFileTest` 的 mock Context＋真实临时目录先例；TopicRepository 用构造器注入 baseDir＝JUnit 临时目录；SAF `deleteEntries` 无 JVM 单测，靠代码对照＋T-10 真机（与既有 SAF 验证策略一致）。
3. **TD-024 真机自动化不可靠**：T-10 人工为准；日志/SP/文件为事实源（`run-as cat/ls`、`dumpsys alarm`、SP xml 前后对照）；`am crash` 仅加分尝试。
4. **内存约束**：编译/全量测试警惕内存尖峰——执行重命令前看 `free -m`，全测固定 `--max-workers=1` 且不与其他重任务叠加；测试任务禁止并行调度 subagent（全局规则）。
5. **多任务交叉文件**：`MainScreen.kt`（T-01/T-05/T-06/T-07）与 `MainActivity.kt`（T-01 countEntries 消重 :153 / T-07 kind 路由 :110-116）——严格按序、每任务收尾全量单测；发现前驱任务缺陷时记录并交 Planner 判定，不越任务边界修。
6. **Microcopy 增量落盘规则**（见 §1 说明）：UI 任务当场落常量，T-08 只收口——避免「先直写后替换」的返工与遗漏。
7. **并行流互扰**：v1.3.x 微文案批次真机走查仍开放（另一未完流），与本流无关；勿动其判据与文件。
8. **预览对话框归属**：`onPreview` 接线在 T-05、对话框本体在 T-06（§3.1 文件归属），两任务评估标准已各自写明，防缝隙。

---

## 6. 开放问题与裁定记录

| # | 问题 | 倾向性方案 | 裁定结果 |
|---|---|---|---|
| Q1 | **版本号**：v1.4.0（versionCode 11）——重大新功能（新增 topics/ 存储子系统＋`appendo_topics` SP＋6 个新源文件＋多选/分区 UI 面），语义化 minor 升位，对齐 1.3.0 先例 | 采纳 v1.4.0；T-11 按此执行 | **2026-11-01 用户裁定：v1.4.0（versionCode 11）**——T-11 按裁定值执行 |
| Q2 | **TopicRepository SP 纯 JVM 可测性**：设计 §6.5 签名仅注入 baseDir；`appendo_topics` SP 断言（createdAt 写入/首见回写/key 不残留）纯 JVM 构造需注入口 | 允许（对齐 §6.5「构造器注入是显式依赖」精神，不引 Robolectric） | **2026-11-01 用户裁定：允许增设 `SharedPreferences` 注入参数（默认参数取真实实例，测试注入内存版），不引入 Robolectric**——T-04 按裁定执行 |

（当前无未裁定开放问题。）

---

## 7. 进度记录

- 2026-11-01：计划创建（Generator 草案，11 任务，待 Evaluator 评估＋用户确认后进入阶段4 编码）。
- 2026-11-01：开放问题 Q1（v1.4.0）/Q2（允许 SP 注入）用户裁定落盘；进入计划级 Evaluator 评估。
- 2026-11-02：Evaluator 第 1 轮评估（13 项检查单 11 PASS/2 FAIL）判定**不合格（修订后重评）**——3 必修：P1 T-07 缺页面 TopAppBar 标题更名落点（`ArchiveListScreen.kt:122`「归档管理」）、P2 T-11 评估标准与 §12 处置列矛盾（R8 不展开 / R4＝TD-027 / R9 无新路径）、P3 T-07 缺主题详情 displayName 小字实现位（`EntryListScreen.kt:76` `sourceFileName` 现无调用方传值）；6 可后置（P4~P9：计数口径统一 6 新增/12 修改、Q1/Q2 裁定同步进任务文本、§8.2 六条口径、交叉文件补 MainActivity、T-09 内存数值判据、R8 引用位置修正）。已按反馈全量修订（必修＋可后置一并清理，§6 裁定记录去重合并）。状态：草案（待用户确认）。
