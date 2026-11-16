# 主题工作区化（戊批次＝v1.5.0）执行计划

> **修订记录**
> | 日期 | 变更人 | 摘要 |
> |---|---|---|
> | 2026-11-13 | Yiyue + Claude | 首次创建（草案）：阶段3 计划，T-20~T-32；骨架＝设计修订三 §25.4；含任务清单化与 101 断言摘要 |
> | 2026-11-13 | Yiyue + Claude | **三视角评审（架构/工程/测试）后修订（草案修订二）**：发现 27 项全采纳＋D1/D2 用户裁定「两案均采」。任务重排 **T-20~T-34**（T-25 拆两任务、kind 退役＋ArchiveDetail 修复独立为 T-30）；硬伤修正＝T-20 文件集补 MainScreen（SameDocument sealed-when 编译自洽）＋两实现 override、T-23 补删除回调全库判定（设计 §20.3 第四点漏承）＋Microcopy/TopicMoveDialogs、T-30 补 MainActivity；chmod 配方改 probe 预检＋JVM 降级口径（realme FUSE 先例）；invalidate 调用方双落（T-23 旧编排＋T-26 MoveFlow）；(t) 造数脚本提前 T-27 落库；格式升级＝每任务补边界红线＋评估标准（甲批先例四段式精简版）；§5 摘要补 (b)(i)(k)(l)(m)(r) 子句；§8 D1/D2 记裁定＋测试面断言落表。**待用户确认定稿 → T-20 起** |
| 2026-11-13 | Yiyue + Claude | **计划定稿（用户确认 "Confirm. Go ahead."）——阶段3 闭环，阶段4 编码启动**。执行模式＝Generator＋Planner 流水线；当前任务＝T-20（事务通用化） |

## 0. 用途与权威输入

- **权威输入**（冲突以先者为准）：`docs/specs.md` 16.B（95~101 定稿）→ `docs/design/topic-move-design.md` §19~§26（定稿，含 §19.0 导读）→ 本计划。
- **交付切分**（设计 §25.4，方案②）：**L1**（提醒解耦＋移出/转移＋工作区屏）→ **T-27 真机门槛全过** → **L2**（抽取四件套＋功能补全＋kind 退役）→ **L3**（新增）；**改名 L1 后与 L2/L3 并行**；乙/TD-029 不属本批。范围冻结线生效。
- **版本**：v1.5.0 / versionCode 12。**测试命令**：`./gradlew test --max-workers=1`（保守串行）；基线＝213 全绿＋各任务新增。
- **注入配方纪律**：故障注入配方一律照抄设计 §10.1/§17.3 原文（勿凭记忆改写——甲批 T-18 约束沿用）；chmod 类配方先 probe 预检（realme FUSE chmod 全拒在案，§4 降级口径）。

## 1. 任务总览

| 任务 | 层 | 内容（一句话） | 依赖 | 并行段 |
|---|---|---|---|---|
| T-20 | L1 | 事务通用化（existsCheckDefinitive＋SameDocument＋分派＋旧 when 补分支） | — | ∥T-21/T-24 |
| T-21 | L1 | TopicRepository ts 缓存（mtime 自愈）＋持锁读 | — | ∥T-20/T-24 |
| T-22 | L1 | EntryLocator＋两 Receiver 接线 | — | ∥T-20/T-21/T-24（实读全量不依赖 T-21 缓存，设计 §20.1 分工） |
| T-23 | L1 | OrphanReconciler＋MainScreen **四块**＋Microcopy/TopicMoveDialogs | T-21（缓存原语）；T-22 先行更顺（fire 已走 locator） | 与 T-22 流水线（文件不相交） |
| T-24 | L1 | 三组件参数化（SwipeCard/MultiSelect/EntryListScreen） | — | ∥T-20/T-21/T-22 |
| T-25 | L1 | 工作区骨架屏＋路由＋深链组合域 | T-22（locateEntry）、T-24（headerText/action 槽） | T-23 后可与 T-26 串行 |
| T-26 | L1 | 多选＋MoveFlow 新建＋移出/转移接线（含 D2 新建子流程） | T-20（事务）、T-23（reminderKeepLine）、T-25 | — |
| **T-27** | L1 | **质量门＋真机门槛（硬门禁）**＋(t) 造数脚本落库 | T-20~T-26 | — |
| T-28 | L2 | 抽取四件套（**独立提交**＋行为复测双门禁） | T-27 | ∥T-30（文件不相交） |
| T-29 | L2 | 工作区功能补全（编辑/删除/批量删除/97(f)/97(e) 出口） | T-28 | T-31 可并行 |
| T-30 | L2 | kind/ArchiveEntryKind 退役＋ArchiveDetailScreen 读路径修复（机械清理） | T-25（主题消费方已改道）；T-27 后 | ∥T-28 |
| T-31 | L3 | 工作区新增（目的地标识＋次级动作） | T-28（InputDialog 已抽） | 与 T-29 并行 |
| T-32 | 改名 | renameTopic＋左滑改改名＋恢复退役 | T-21/T-24＋L1 后 | 与 L2/L3 并行 |
| T-33 | 收尾 | 文档九处就地注＋四文档义务＋版本升位＋包级 CLAUDE.md | T-29~T-32 | — |
| T-34 | 收尾 | 合并验收＋发布门槛 | 全部 | — |

**执行模式**（v1.4 批次既定）：Generator 编码＋Planner 核验＋文件不相交流水线并行；每任务完成更新本计划 §7 与 handoff。

## 2. L1 任务定义（T-20~T-27）

### T-20 事务通用化
- **内容**：① 接口属性 `existsCheckDefinitive`（FileBased=true／Saf=false，KDoc 注明用途）；② SameDocument 护栏（引用相等或双 FileBased canonicalPath 相同→`MoveResult.SameDocument` 零副作用）；③ 步骤 3 按属性分派（SAF 目标不查 exists 直接 readAllWithStatus）；④ MoveFaults 扩 SAF 目标注入；⑤ **MainScreen 旧 executeMove 的 when 补 SameDocument 分支**（Kotlin 2.2 sealed 非穷尽＝编译错——「暂持旧编排」过渡态须 T-20 就地自洽；新分支映射 `TOAST_SAME_TOPIC_GUARD` 兜底）。
- **边界红线**：`MarkdownFileOperations.kt`、`FileBasedMarkdownFile.kt`、`SafMarkdownFile.kt`、`TopicMoveTransaction.kt`、`MainScreen.kt`（仅 when 补分支一处）＋`TopicMoveTransactionTest.kt`；与 T-21/T-24 文件零交集（读码核实）。
- **评估标准**：□SameDocument 两形态源目标零改动 □SAF 假实现（definitive=false）两分支可达 □移出六态 N/M 计数（MoveFaults）□转移=同事务断言 □MainScreen 新分支不改既有五态行为 □全量复跑绿。

### T-21 TopicRepository ts 缓存与持锁读
- **内容**：缓存条目 `(mtime, Set<ts>)`；`allTopicTimestampsLocked()` 锁内 stat 自愈（读失败整次返 null）；`invalidateTopic/invalidateAllTopics`（供调用方第一道防线）；`listTopics`/`deleteTopic` 读改持锁包装。
- **边界**：`TopicRepository.kt`＋`TopicRepositoryTest.kt`。
- **评估标准**：□缓存三态（命中/失效重读/读失败 null 传播）□自愈支线（`File.setLastModified` 钉 mtime，**禁依赖自然 mtime 差**——同粒度窗口假阴性纪律）□沿 TemporaryFolder＋InMemorySharedPreferences 注入先例 □全量绿。

### T-22 EntryLocator＋两 Receiver 接线
- **内容**：`EntryLocation` sealed＋核心纯函数（参数注入）＋生产入口**整体单锁**；buildIndex 含内容（ts→(位置,内容)）；`findEntryContent` 退役，fire/boot 改接；**不消费 ts 缓存**（锁内实读全量——设计 §20.1 分工裁定）。
- **边界**：新 `reminder/EntryLocator.kt`、`ReminderAlarmReceiver.kt`、`ReminderBootReceiver.kt`＋新测试；不触 MainScreen/TopicRepository。
- **评估标准**：□主优先/主题兜底 □多主题确定性次序（createdAt 倒序首个）□全库 miss null □buildIndex 与 locate 同源 □fire 自愈语义不变 □全量绿。

### T-23 OrphanReconciler＋MainScreen 四块＋文案两文件
- **内容**：① `reconcile(mainFile, topicRepo, reminderKeys)`（自读主文件、锁内并集、锁外 cancel/remove）；② MainScreen 对账段（:297-306）替换；③ 清空分支（:452-455）收窄（改调 reconcile）；④ executeMove 取消段（:347-369）删除＋**成功分支补 invalidate（源＋目标——统一失效规则第一道防线，设计 §20.2）**；⑤ **主列表删除回调改全库判定**（设计 §20.3 第四点：全库再无该 ts 才 cancel+remove——101(i) 依赖）；⑥ Microcopy：`reminderKeepLine` 落盘＋`removedRemindersSuffix` 删；⑦ TopicMoveDialogs：确认框 K 行改保留告知＋`remindersToCancel` 删。
- **边界**：新 `reminder/OrphanReconciler.kt`、`MainScreen.kt`（四块）、`Microcopy.kt`、`TopicMoveDialogs.kt`＋新测试。
- **评估标准**：□reconcile 三态判定（仅主题存活不杀/全库消失杀/并存不杀）□读失败 null 跳过 □并发输出判据（reconcile×双向 move 多轮零误报＋终态一致——⑦ 先例弃时序断言）□`removedRemindersSuffix`/`remindersToCancel` 全仓 grep 零残留 □主列表删除→主题内有同 ts 残留时提醒保留（JVM）□全量绿。

### T-24 三组件参数化
- **内容**：SwipeCard 左向 label（默认「追加」）／MultiSelectTopBar action 槽（默认单钮）／EntryListScreen `headerText`（默认现状字面）。
- **边界**：`SwipeCard.kt`、`MultiSelect.kt`、`EntryListScreen.kt`；**默认值保三消费方零改动**。
- **评估标准**：□归档侧「追加」/主屏单钮/「已收集 N 条」逐字不变 □94(c) 归档只读真机抽查（随 T-27）□全量绿。

### T-25 工作区骨架屏＋路由＋深链
- **内容**：`TopicWorkspaceScreen` 骨架＝只读列表（readOnly=false 徽标正向启用）＋顶栏（返回＋主题名＋「N 条」小字；**L1 期无出口/新增动作**——R26 子集）＋空态**中性句**＋读失败如实呈现（TOPIC_READ_FAILED/BTN_RETRY）；MainActivity：`topic_workspace/{filePath}?scrollTs` 路由＋`AppendoNavHost` 组合域深链（门控「目的地＝main」＋消费后清空 scrollTs）＋topics `onTopicClick` 改道；Microcopy：TOPIC_READ_FAILED/TOPIC_WORKSPACE_EMPTY_*（中性句字面）。
- **边界**：新 `TopicWorkspaceScreen.kt`、`MainActivity.kt`、`Microcopy.kt`（本任务子集）＋新屏测试（决策纯函数如有抽取）。
- **评估标准**：□101(a) 徽标可见/深链进工作区定位（本地 smoke＋JVM 可证面）□读失败不呈「空」□门控不过＝不跳不弹错 □archive_detail 归档路径零变化 □全量绿。**完成准则加本地 smoke：装 debug 包手点主题→进工作区→返回各一次**（路由/接线低级断裂当天暴露）。

### T-26 多选＋MoveFlow＋移出/转移接线
- **内容**：工作区多选（contextual bar action 槽**两钮**「移出到主列表」「移入其他主题」）；`MoveFlow.kt` **新建供工作区单消费**（holder＋编排参数化＋`MoveOutcomeMapping` **移出/转移组合**；MainScreen 暂持旧 executeMove——两步走）；TopicMoveDialogs：移出/转移确认变体（去向句分场景＋保留告知行 K>0）＋目标选择「排除集合」＋**D2：新建主题子流程**（整体复用移入对话框 2/2b 路径——NewTopicNameDialog＋创建后即为目标，**不得旁路新写**）；成功→invalidate(源＋目标)。
- **边界**：新 `MoveFlow.kt`、`TopicWorkspaceScreen.kt` 扩、`TopicMoveDialogs.kt` 扩、`Microcopy.kt`（按钮/确认/N=0 变体族）＋`MoveOutcomeMappingTest.kt`。
- **评估标准**：□Mapping 移出/转移×useSAF 全组合钉死（§25.1）□目标列表排除当前主题 □SameDocument 兜底 toast □D2 新建后即为目标＋转移断言同套 □升序追加（含主列表目标）□全量绿。

### T-27 L1 质量门＋真机门槛（硬门禁）
- **内容**：全量测试绿＋assembleDebug；**(t) 造数脚本落库**（run-as 直写 topics/*.md＋89(p) 分享循环——`scripts/` 或 `docs/background/`，可重放资产；提前落库顺带早证 R20 锁内吞吐）；真机门槛 §4 表一全过（配方 §5）。
- **评估标准**：□表一 11 行逐项过 □94(c) 归档抽查 □213＋新增全绿。**全过方启 T-28。**

## 3. L2/L3/改名/收尾任务定义（T-28~T-34）

### T-28 抽取四件套（独立提交＋双门禁）
- **内容**：EntryEditDialog（:1117-1302）/EntryInputDialog（:1456-1607）无状态化（**草稿 inputDraft/editDraft＋lastEditedTs 提升各宿主**）；ReminderFlowHost（:227-245 权限链路；**两屏各持一份实例**）；MoveFlow 统一迁入（executeMove 状态机 :174-208＋对话框栈接线；Mapping 补**移入**组合）；MainScreen 接线；git diff 逐行核对清单随提交。
- **边界**：新三共享件＋`MoveFlow.kt` 扩＋`MainScreen.kt`；不触工作区屏功能。
- **评估标准**：□81 系微文案验收（specs 16 需求 81(a)~(i)——真机走查归 T-27/T-34，JVM 面＝微文案模板测试）＋**2026-11-11 修订④⑤行为复测**（三通道关窗/两通道关闭/输入保留/草稿保全——specs 16.A 修订注④⑤，真机走查）全绿 □抽取提交独立（先于 T-29 合入）□全量绿。**双门禁：抽取提交全绿方进 T-29。**

### T-29 工作区功能补全
- **内容**：编辑（updateEntry 按 ts 首条＋次级动作经 ReminderFlowHost）／删除（deleteEntries 同 key 全删＋95(e) 全库联动）／批量删除（确认框 N 条——TopicMoveDialogs `dlgBatchDeleteBody`）／多选 bar 挂第三钮「删除」／97(f) 补遗（新增后刷新定位＋计数顶栏——断言挂 101(k)(w)）／97(e) 出口（copyContent/shareContent 提权 internal＋顶栏两动作——**D1：复制＝文字动作「复制」TextButton＋分享＝Share 图标**）。
- **边界**：`TopicWorkspaceScreen.kt` 扩、`TopicMoveDialogs.kt`（批量删除框）、`MainScreen.kt`（两函数提权）、`Microcopy.kt`。
- **评估标准**：□101(h)(i)(j)(v) JVM/真机锚点 □(k)(w) 计数断言 □(o) 出口干净（判据 73(e)）□全量绿。

### T-30 kind 退役＋ArchiveDetail 修复（机械清理）
- **内容**：MainActivity `archive_detail` 路由去 kind 参数化简（:24/:155/:168 三处引用清理）；`ArchiveEntryKind` 定义删除（ArchiveListScreen.kt:59-66）；ArchiveDetailScreen 回归归档专用＋读路径换 `readAllWithStatus`＋读失败如实呈现（写侧无落点注）。
- **边界**：`MainActivity.kt`、`ArchiveListScreen.kt`、`ArchiveDetailScreen.kt`；与 T-28 文件不相交可并行。
- **评估标准**：□`ArchiveEntryKind` 全仓 grep 零残留 □归档详情读失败真机验证（TOPIC_READ_FAILED）□94 归档路径回归 □全量绿。

### T-31 工作区新增（L3）
- **内容**：EntryInputDialog 接线（`appendReturningTimestamp`＋重复提示 DuplicateHintThrottle＋次级动作先落盘）；成功 toast `toastAppendedToTopic(name)`；空态切引导句（「点右上角＋」——L3 才有钮可指）。
- **边界**：`TopicWorkspaceScreen.kt` 扩、`Microcopy.kt`。
- **评估标准**：□101(k) 全量（毫秒 ts/重复提示/设提醒/目的地标识/**顶栏 N+1**）□主列表不受影响 □全量绿。

### T-32 改名＋恢复退役
- **内容**：`renameTopic`（锁内三步：写新键 commit→**同目录** renameTo→删旧键；①失败即中止；自身排除）；RenameTopicDialog 预填变体；TopicListScreen 左滑「恢复」退役→「改名」（SWIPE_LABEL_RENAME 经 SwipeCard label 参数）；`RestoreConfirmDialog` 主题侧消费移除（ArchiveRestore 归档侧保留）。
- **边界**：`TopicRepository.kt` 扩、`TopicListScreen.kt`、`TopicMoveDialogs.kt`、`Microcopy.kt`＋`TopicRepositoryTest` 扩。
- **评估标准**：□JVM 中间态两窗口（手工构造——§25.1）□A→a 自身排除改名成功 □renameTo 失败回滚 □101(m) 功能面（创建时间保留/排序不漂/首字标重算/提醒深链不受影响）□**94(b) 新手势槽（左滑＝改名）真机单验——不等 T-34** □101(n)③ □全量绿。

### T-33 文档收尾＋版本升位
- **内容**：README（两项限制改写＋清空切换句＋先主后主题注明＋三段重写＋可感知变更清单——**恢复退役醒目**）；CHANGELOG v1.5.0（含存量提醒不复活口径）；architecture.md §3.3/§3.5/§5.4/§6.1/§8.2；specs 九处就地注（84(c)/85(a)(d)/86/88/89(i)/58/62/63/64＋97 等价解释注）；**包级 CLAUDE.md 两份**（reminder/ 新增 EntryLocator/OrphanReconciler 行、ui/ 新文件行）；versionCode 12／versionName "1.5.0"。
- **边界**：四文档＋两包级 CLAUDE.md＋`build.gradle`。
- **评估标准**：□§24.4 文档义务逐项核对表随提交 □CHANGELOG 字面与 Microcopy 逐字核 □全量绿。

### T-34 合并验收＋发布门槛
- **内容**：§4 表二全量；**v1.4 APK 前置**（配方 C 用——GitHub Release v1.4.0 资产下载留档 `D:/tmp/`，T-27 前备妥）；发布门槛复测。
- **评估标准**：见表二；全过＝v1.5.0 可发布。

## 4. 真机验收表（RMX3560；TD-024 人工验收为准；toast 经 APPENDO_TOAST I 级；**注入配方照抄设计 §10.1/§17.3**）

**表一：L1 真机门槛（T-27，硬门禁）**

| 项 | 断言 | 配方/前置（chmod 类一律 probe 预检——realme FUSE chmod 全拒在案；**chmod 静默失效→降级＝JVM 注入轨为主证据**） |
|---|---|---|
| 101(a) | 移入后提醒存活：徽标工作区可见/到点触发/通知内容＝主题内条目/深链进工作区定位 | 常规造数 |
| 101(b) | 同 ts 两处并存：通知取主列表/深链跳主列表；删主列表副本后改取主题；**多主题确定性次序** | 配方 A（推荐）/B/C（§5） |
| 101(c) | 仅主题存活不误清（多次刷新/重启）；全库消失自动清；主题读失败本轮跳过 | (c) 真机轨＝probe→`run-as chmod 000 topics/T.md`→刷新→断言提醒不被清→**恢复 `chmod 700`**；FUSE 拒→JVM OrphanReconciler 读失败注入轨 |
| 101(d) | 主题内提醒开机补发（重复只补最新）；单遍映射无可感知变慢 | 跨点/改系统时间 |
| 101(e) | 移出原子性：SAF 主文件写失败→主题原样＋「移出失败」＋重试正确；「无法判定」≠「不存在」分离 | §17.3 T-1 会话中失效法（**完整命令在设计 §17.3，照抄**）；ROM 缓存降级→JVM 假实现轨 |
| 101(f)(g) | 移出幂等收敛；N=0 无歧义 | (f)＝probe→`chmod 500 topics`→移出→SourceDeleteFailed 两边都有→**恢复 `chmod 700`**→重试收敛；FUSE 拒→JVM MoveFaults 轨 |
| 101(l) | 转移：源删/目标升序/去重计数；失败重试；N=0 专用文案；**D2：经新建主题为目标的路径同断言** | 目标列表排除当前主题断言 |
| 101(q)(u) | 移出成功路径（89(a) 镜像）；源=目标护栏（列表排除＋事务拒绝） | — |
| 101(n)①② | 移入确认框无失效行/成功句无附注；K>0 三框保留告知行 | — |
| 89(i) 反向 | 新断言＝101(a) | — |
| 101(r) | **归档并清空后主题内提醒幸存并继续触发；切换/自定义主文件同口径**（两场景都验） | 切换＝归档管理换文件后回看主题提醒 |

**表二：合并验收（T-34）**：101(a)~(w) 全量（表一已验项复测关键路径；**(r) 显式两行：清空＋切换**）；第 13 章回归（57~67 逐条）；94(a)~(f) 复测（**94(b) 重基线：左滑＝改名；恢复断言仅归档侧**）；**(p) 深色走查全部新界面（含 D1 文字动作形态——文字对比度点名）＋大批量移出/转移不 ANR 有可感知反馈**；**(t) ≥10 主题×数十条：轮询刷新与开机补发双指标无可感知变慢（T-27 已落库脚本重放）**；(o) 出口干净。

## 5. 101(a)~(w) 断言摘要（specs 一句话版；与 specs 冲突以 specs 为准）

| # | 断言 | # | 断言 |
|---|---|---|---|
| a | 移入后提醒存活（徽标/触发/通知内容/深链定位） | n | ①失效行/附注退役②K>0 保留告知行③左滑恢复不再触发④README 已改写 |
| b | 同 ts 两处并存：先主后主题＋删主副本后改取主题＋**多主题确定性次序** | o | 工作区复制/分享全部无结构标记无零宽字符 |
| c | 孤儿对账扩全库（仅主题不误清/全库消失清/读失败跳过不误杀） | p | 深色走查全部新界面（**含文字动作**）；大批量移出/转移不 ANR 且**有可感知反馈** |
| d | 主题内提醒开机补发正确＋单遍映射 | q | 移出成功路径（主列表出现/时间戳原样/升序/计数同步） |
| e | 移出原子性＋SAF「无法判定」与「不存在」分离 | r | 清空联动收窄（清空后主题提醒幸存）＋**切换主文件同口径** |
| f | 移出幂等（两边都有→重试收敛） | s | 主题内提醒生命周期（替换红/取消/每天重复排期/贪睡锚点/无权限拦截） |
| g | 移出 N=0 成功无歧义 | t | 性能门槛（≥10 主题×数十条：轮询＋补发双指标无可感知变慢） |
| h | 主题内编辑（持久/ts 不变/提醒徽标保留/次级动作与主列表同行为） | u | 源=目标护栏（列表排除＋事务层拒绝） |
| i | 主题内删除（确认框红振动/**全库无残留才取消提醒、同 ts 他处残留则保留**/同 key 全删） | v | 主题内多选与批量删除（选中集保持/确认计数/长按复制不被占用） |
| j | 全删归一化（纯文件头/卡片「空」/右滑删可用） | w | 移出全部→详情空态→右滑删除全流程（**含顶栏计数归零**） |
| k | 主题内新增（落主题/主列表不动/毫秒 ts/重复提示/设提醒/**成功反馈含主题名**/顶栏 N+1） | — | — |
| l | 主题间转移（**源主题删除**/目标升序追加/去重计数/失败重试/N=0 专用文案） | — | — |
| m | 改名（创建时间保留/排序不漂/首字标重算/提醒深链不受影响/**同名与非法名被拒**/崩溃窗口重试后 createdAt 不重置） | — | — |

**101(b) 三配方**（设计 §25.2）：A＝run-as 向主题文件追加同 ts 同内容条目块（推荐；多主题＝第二主题＋直写 `appendo_topics.xml` 钉 createdAt 次序）；B＝chmod 软中间态（与 (f) 连做，probe＋降级口径见 §4）；C＝**v1.4 APK**（GitHub Release 资产留档）→移入→左滑恢复造存量→覆盖安装 v1.5.0（顺带断言存量不复活：到点不响、sidecar 无 key）。

## 6. 质量门与纪律

- 每任务：新增 JVM 测试随任务落、全量 `--max-workers=1` 复跑全绿；退役常量/函数全仓 grep 零残留。
- **三道硬门禁**：T-27 真机门槛全过方启 T-28；T-28 抽取提交独立合入＋行为复测全绿方进 T-29；T-34 发布门槛全过方发布。
- **范围冻结**：执行中新能力需求一律 debt-tracker/后续批次立项，不就地扩批。
- 既有验收不回退：94(c)/94(d) 每质量门抽查。

## 7. 进度记录（执行时追加）

### T-20 事务通用化（2026-11-13，完成）

- **落盘**：① `MarkdownFileOperations.existsCheckDefinitive`（KDoc 含分派依据）＋FileBased=true/Saf=false 两 override；② `MoveResult.SameDocument`＋move() 步骤 0 护栏（引用相等＋双 FileBased canonicalPath——`FileBasedMarkdownFile.file` 由 private 提为 internal 供比对，注明原因）；③ 步骤 3 按 definitive 分派（FileBased 维持 exists 分支/SAF 目标直接读）；④ KDoc 双处「目标恒 FileBased」句按通用化改写；⑤ MainScreen 旧 executeMove when 补 SameDocument 防御分支（不设重试态）。
- **测试**（TopicMoveTransactionTest 17 用例＝既有 13＋新增 4）：同实例护栏（逐字节零副作用）/同 canonicalPath 护栏/SAF 假目标跳过 exists 直接读成功（`SafLikeTarget.exists()` 抛异常＝「跳过」断言本体）/SAF 假目标读失败→TargetReadFailed 源未动。
- **评估标准自检**：SameDocument 两形态 ✓；SAF 假实现两分支 ✓；六态计数＝既有 ①~⑬ 注入族＋SameDocument 新增覆盖（移出/转移方向与移入同事务，双 FileBased 用例即其形态）✓；MainScreen 新分支仅防御 toast（真机回归随 T-27）✓；全量 217/0/0/0 ✓。
- **偏离记录**：① 边界微扩——`Microcopy.kt` 加 `TOAST_SAME_TOPIC_GUARD`（编译需要；设计 §24.1 已定义该常量，T-26 变体族照单复用）；② 计划「MoveFaults 扩 SAF 目标注入」未落——SAF 目标分支经接口属性由假实现注入覆盖，无需新增 MoveFaults 枚举（更少生产面）；③ FileBasedMarkdownFile.file 可见性 private→internal（护栏比对需要）。
- **核验**：Generator 自检（上述）；Planner 独立核验随下一任务流水线补做或用户抽查。

### T-21 TopicRepository ts 缓存与持锁读（2026-11-13，完成）

- **落盘**：① `TopicTsEntry(mtime, ts)`＋`topicTsCache`（访问一律 FileOperationLock 内，KDoc 注明）；② `allTopicTimestampsLocked(): Map<File, Set<String>>?`——锁内 stat 自愈（mtime 一致用缓存/不一致重读收编）、任一需重读文件失败→null 整次跳过（95(a) 前提 3，KDoc 点名不得沿用「读失败按 0」先例）、候选目录与 listTopics 同源；③ `invalidateTopic/invalidateAllTopics`（第一道防线，显式 `: Unit` 防私有类型外泄）；④ `listTopics`/`deleteTopic` 持锁化（撕裂防护，KDoc 注明 SP commit 持锁低频可接受）＋deleteTopic 缓存失效。
- **测试**（TopicRepositoryTest 17 用例＝既有 12＋新增 5）：缓存命中（mtime 钉同值改内容→返回旧集＝命中证明）/mtime 变自愈重读收编/显式失效强制重读（同 mtime）/多主题并集/读失败→null→恢复（POSIX 门控 assumeTrue，沿 T-02 先例注记 Windows 不可靠）。
- **评估标准自检**：缓存三态 ✓；自愈支线（setLastModified 钉造法＋绕过 invalidate 直改）✓；注入沿 TemporaryFolder＋InMemorySharedPreferences 先例 ✓；全量 222/0/0/0 ✓。
- **偏离记录**：无（边界内）。
- **同日新增**：用户裁定**自主执行原则**（问前先判是否真需决策；未跑偏自动执行；六层防偏离机制）——已入会话记忆。

### T-22 EntryLocator＋两 Receiver 接线（2026-11-13，完成）

- **落盘**：新 `reminder/EntryLocator.kt`——`EntryLocation` sealed（MainFile/Topic(file)）＋纯函数核心 `locateInContents`/`buildIndexFromContents`（主优先/多主题列表序首个/putIfAbsent＋主覆盖）＋生产入口**整体单锁**（`readMainLocked` 经 readAllWithStatus 读失败→null、`readTopicsLocked` 沿 listTopics 序读失败跳过——读失败语义沿 v1.4 fire 口径 KDoc 注明，对账保护归 OrphanReconciler）；**不消费 ts 缓存**（分工裁定 KDoc 注明）。`findEntryContent` 退役（含三个 unused import 清理），handleFire 改接 `locateEntryContent`；`rescheduleAll` 改单遍 `buildIndex` 查表（95(b) 性能前提）。
- **测试**（新 `EntryLocatorTest` 8 用例）：主命中/同 ts 并存主胜/仅主题命中/多主题列表序首个/主不可读落主题/全库 miss/索引覆盖与全量/`index[ts]` 与 locate 同源。
- **评估标准自检**：主优先✓；多主题确定性次序✓；miss null✓；buildIndex 与 locate 同源✓；fire 自愈语义不变（结构零改，null→cancel+remove 同 v1.4）✓；全量 230/0/0/0 ✓。
- **偏离记录**：设计未显式定义 locate 路径读失败语义——按「读失败＝该文件无命中」实现（沿 v1.4 fire 口径＋设计 §20.1「定位失败＝全库不存在」原文），KDoc 注明；如后续评审认为需「不确定」态再升级（登记观察，不阻塞）。

### T-20~T-22 code-review 补做（2026-11-13，完成；用户裁定「同意」后执行）

- **评审**：`/code-review 44d40f8^..b224d3d` 7 项发现——3 行为风险＋4 效率/一致性。
- **#1 采纳（设计修订）**：fire/boot「miss 即 cancel+remove」推翻→`LocateOutcome` 三态（Found/NotFound/Unknown）——任一文件不可读且未命中＝Unknown 不通知不取消不删（读失败≠不存在）；循环提醒照常排下一次防系列断链；boot `buildIndexChecked` 携带 anyUnreadable，miss 不可信时不取消；`locateEntryContent`/`buildIndex` 死接口退役；design §20.1 修订注＋修订记录落盘。
- **#2 采纳（缺陷）**：`mergedTopicFiles` 抽取（目录序→文件名序、displayName 大小写不敏感跨目录去重）——listTopics 与 allTopicTimestampsLocked 共用同文件集，分家期间对账不消费 UI 不可见的遮蔽文件。
- **#3 改判（原提案有误）**：「混合类型按 SameDocument 拒绝」不可行——SAF 主文件模式下移入/移出**本就是** Saf＋FileBased 混合，拒绝即断主流；SAF uri 不透明无法可靠判别名。落点＝SameDocument KDoc 局限注记＋TD-048（硬防护归 SAF 选文件入口）。
- **#4 采纳（顺手）**：rescheduleAll 空库短路＋索引懒建（首个 fireMissed 才读全库——MainScreen 每次组合/开机不再无条件全库 I/O）。
- **#7 采纳**：`TOAST_SAME_TOPIC_GUARD` const→`toastSameTopicGuard()` fun（评审「全为 fun」说法不确——文件本混用两形态，取参数化先例侧）。
- **#5/#6 登记**：TD-049（locate 路径 2N 双读，随 T-29 重估）/TD-050（mtime 毫秒粒度窗口，T-23 invalidate 落调用方后第一道防线生效）。
- **测试**：新增 6 用例（outcomeOf 四态：命中/可读 miss/不可读 miss/主不可读命中主题；mergedTopicFiles 两例：跨目录去重序＋与 listTopics 同源）——全量 **236/0/0/0**。
- **待真机**：fire/boot 三态行为 JVM 面只覆盖纯函数决策，Receiver 接线随 T-27 真机门槛回归。

### T-23 OrphanReconciler＋MainScreen 四块＋文案两文件（2026-11-13，完成）

- **落盘**：① 新 `reminder/OrphanReconciler.kt`——`reconcile(mainFile, topicRepo, reminderKeys): Set<String>?`（单锁内「自读主文件＋allTopicTimestampsLocked 并集」→findOrphans；读失败任一→null 本轮跳过；cancel/remove 锁外归调用方）；② MainScreen 对账段改调 reconcile（withContext(IO)，null 不动作）；③ 清空分支 63 收窄（cancel 全部→reconcile 全库判定；null 保守不清由轮询兜底）；④ executeMove 取消段删除＋`cancelledReminders`/toast 附注退役＋成功分支补 invalidateTopic(target＋FileBased 源侧)（统一失效规则第一道防线）；⑤ 主列表删除回调改 reconcile 单键全库判定（主题内同 ts 残留保留）；⑥ Microcopy：`reminderKeepLine(k)`「$k 条提醒将随条目保留。」落盘＋`removedRemindersSuffix`/`dlgMoveConfirmReminder` 删；⑦ TopicMoveDialogs：K 行改 `reminderKeepLine`＋`remindersToCancel` 纯函数删＋文件头 KDoc 同步。
- **测试**（新 `OrphanReconcilerTest` 7 用例；`TopicMoveDialogsTest`（remindersToCancel 专属 6 用例）随函数退役整文件删）：三态（仅主题存活不杀/全库消失杀/并存不杀）＋空键集早退＋主文件 failed 标志→null＋主题读失败 POSIX 门控→null＋并发输出判据（reconcile×双向 move 30 轮零误报＋终态一致）。
- **评估标准自检**：三态 ✓；读失败 null ✓；并发输出判据 ✓（沿 ⑦ 先例弃时序断言）；`removedRemindersSuffix`/`remindersToCancel` 源码 grep 零残留 ✓（design 命中均为历史表/§20.3 退役清单记录——append 纪律不删）；主列表删除→主题残留保留 JVM 面＝reconcile_entryOnlyInTopic ✓；全量 **237/0/0/2**（skip×2＝POSIX 门控读失败注入，Windows JDK 读拦截不可靠先例）✓。
- **偏离记录**：① `dlgMoveConfirmReminder`（「移入后 K 条提醒失效。」）随口径作废一并删（计划只点名两符号，但 K 行换 `reminderKeepLine` 后它成死文案）；② 详情对话框「取消提醒」按钮（手动取消，:1256 一带）不动——用户显式动作非删除联动，不在 §20.3 范围；③ 删除回调的 reconcile 在主线程 onClick 直调（沿该回调既有 deleteEntry 主线程 I/O 先例，不在此任务扩改动面）。
- **待真机**：对账/清空/删除三路径全库判定随 T-27 真机门槛回归（JVM 面已覆盖决策纯函数）。

### T-24 三组件参数化（2026-11-13，完成）

- **落盘**：① SwipeCard 增 `swipeLeftLabel: String = "追加"`（左向指示字面；「删除」与配色仍不参数化——右滑删除红全域同构）；② MultiSelectTopBar 增 `action: @Composable () -> Unit` 槽（缺省＝原「移入」TextButton 整块，onMoveIn 保留于缺省闭包）；③ EntryListScreen 增 `headerText: String = "已收集 $entryCount 条"`（默认值引用前参）。三默认值均＝现状字面，消费方零改动。
- **评估标准自检**：归档侧「追加」/主屏单钮/「已收集 N 条」逐字不变 ✓（默认值即原字面，编译通过＝三消费方未触）；94(c) 归档只读真机抽查随 T-27 ✓；全量 237/0/0/2 ✓。
- **偏离记录**：无（边界内——仅三组件文件，KDoc 同步更新「不参数化」旧句）。

### T-25 工作区骨架屏＋路由＋深链（2026-11-13，代码完成；**本地 smoke 待真机**）

- **落盘**：① 新 `ui/TopicWorkspaceScreen.kt`——顶栏（返回＋主题名 primary SemiBold＋`topicEntryCountLine`「N 条」labelSmall 小字，底色随页面）＋EntryListScreen（readOnly=false 徽标正向启用；L1 动作回调缺省空实现；headerText=""＝不渲染列表头——EntryListScreen 加 isNotBlank 守卫）；加载 LaunchedEffect＋IO 经 FileBasedMarkdownFile.readAllWithStatus；读失败如实呈现（TOPIC_READ_FAILED＋BTN_RETRY，reloadKey 重试）；空态 L1 中性句（TITLE 单行、BODY 留空）；深链 scrollToItem 定位（miss 不滚不弹错）；② MainActivity：`topic_workspace/{filePath}?scrollTs` 路由（ts 双侧 Uri.encode/decode——含空格点号）＋`AppendoNavHost` 组合域深链（LaunchedEffect(scrollTs)→IO locateEntry；MainFile 不动作（MainScreen 侧消费）；Topic→门控目的地＝main→navigate；null/Topic 态消费后清空 scrollTs——新增 onConsumeScrollTs 回调）＋topics onTopicClick 改道工作区（archive_detail kind=topic 路径 T-30 退役）；③ Microcopy：`topicEntryCountLine`/`TOPIC_READ_FAILED`/`TOPIC_WORKSPACE_EMPTY_TITLE`（BODY 留空 L1 中性——T-31 切终句）。
- **评估标准自检**：101(a) 徽标结构面 ✓（readOnly=false 派生）；读失败不呈「空」✓（failed→专用态）；门控不过＝不跳不弹错 ✓；archive_detail 归档路径零变化 ✓（未触该路由）；全量 **237/0/0/2** ✓；debug 包构建通过 ✓。
- **偏离记录**：① §22.1「headerText=null」落为空串＋isNotBlank 守卫（T-24 已定 String 非 null 参数形态）；② L1 空态 BODY 留空不渲染（设计只定终句，L1 中性句无字面——TITLE 单行即中性，登记 T-31 切句义务）；③ 顶栏「N 条」经 `topicEntryCountLine` 入 Microcopy（§24 表只画「N 条」字面，未定常量名）。
- **待真机**：完成准则「本地 smoke 装包手点主题→进工作区→返回」——本机无 adb 设备，APK 已构建（Appendo-debug.apk 2026-11-13 13:48），**待装机补做**；101(a) 断言随 T-27 门槛。

### T-26 多选＋MoveFlow＋移出/转移接线（2026-11-13，完成；真机回归随 T-27）

- **落盘**：① 新 `ui/MoveFlow.kt`——`MoveVerb`（MOVE_OUT/TRANSFER；移入随 T-28 补）＋`MoveFlowHolder`（step/verb/transferTarget/newTopicInput/executing/failed，start/goTo/selectTarget/markExecuting/markFailed/close）＋`MoveOutcomeMapping` 纯函数（MoveResult 全态×动词×useSAF→toast/重试保持/主列表刷新信号；SourceDeleteFailed 统一兜底句＋重试——§24 未定专用句）；② TopicWorkspaceScreen 扩：多选态（MultiSelectTopBar action 槽两钮「移出到主列表」「移入其他主题」）＋勾选切换/重载 retainedSelection＋长按预览（EntryPreviewDialog）＋对话框栈四步（CONFIRM 移出直达/SELECT_TARGET 排除当前主题/NEW_TOPIC 创建即目标/SAME_NAME 指路）＋执行编排（IO 内 move→成功 invalidate 源＋目标＋移出方向 setFileLastModified（A12）＋本页重载；失败重试保持；SameDocument 只提示）；③ TopicMoveDialogs：TopicSelectDialog `excludeName` 排除参数（大小写不敏感）＋MoveConfirmDialog `bodyText/titleText/confirmLabel` 变体（缺省＝移入现句零改动；移出场景 title/label＝「移出到主列表」）；④ Microcopy：§24.1 移出/转移族 10 常量/函数逐字落盘；⑤ MainActivity 传 fileRepository（移出刷新信号）。
- **测试**：新 `MoveOutcomeMappingTest` 12 用例（成功三态×两动词含转移 N=0 专用变体（E4 禁复用断言）/失败族×useSAF（SAF 变体仅移出 TargetReadFailed＋useSAF，转移恒不 SAF 变体含 true 防御）/SameDocument 防御零状态变更）——全量 **249/0/0/2**；assembleDebug 通过。
- **评估标准自检**：Mapping 全组合钉死 ✓；目标列表排除当前主题 ✓（excludeName＋事务层护栏第二道）；SameDocument 兜底 toast ✓；D2 新建后即为目标（复用 2/2b 路径零旁路）✓（真机断言 101(l) 随 T-27）；升序追加含主列表目标 ✓（事务 :144-147 零代码＋SafLikeTarget 用例既有覆盖）；全量绿 ✓。
- **偏离记录**：① 移出确认框 title/confirmLabel 复用 `BTN_MOVE_OUT` 字面作标题与按钮（§21.2 只定去向句分场景，未定移出框标题/按钮字面——零新字面）；② 工作区浏览态长按暂 no-op（浏览态长按复制/编辑出口＝T-29 范围，多选态长按预览已接）；③ SourceDeleteFailed 无专用「重试安全」句（§24 定稿表未定义），统一兜底＋重试保持。

### T-27 L1 质量门＋真机门槛（2026-11-13，自动化面完成；**人工项待验收**）

- **已完成（自动化/脚本轨，真机 RMX3560）**：
  - 全量单测 `--max-workers=1` 复跑 **249/0/0/2** ✓＋assembleDebug ✓＋装机 ✓；
  - **T-25 本地 smoke 补做** ✓：主题页→AI 工作区→返回（路由/接线零断裂；徽标可见）；
  - **(t) 造数脚本落库** ✓：`scripts/t27-seed-topics.sh`（可重放；seed/clean 两模式）——**配方 A 降级**：realme FUSE 拒 run-as 访问外部 Android/data（probe 在案），改经**内部 files/topics 候选目录**注入（candidateTopicDirs 双目录合并扫描天然消费；分家场景同款语义）；已执行 10 主题×30 条，主题页合并显示 11 个 ✓；
  - **读失败如实呈现真机轨** ✓：`run-as chmod 000 files/topics/seed1.md`（内部 ext4 非 FUSE，chmod 成功）→工作区呈「读取失败，请重试」＋重试钮→恢复 644→重试自愈载入 30 条（T-25 评估项真机落证；101(c) chmod 注入通道打通）；
  - **深链 101(a) 部分** ✓：冷启带 ts→直达所在主题工作区（seed5）＋scrollToItem 定位条目卡 ✓；**门控** ✓：工作区在栈顶时另一 ts 深链→丢弃不跳不弹错、滚动位置保持；
  - 截图证据：D:/tmp/t27_01~11（主机侧临时目录，随验收归档说明）。
- **待人工/跨点验收（TD-024 人工验收为准）**：
  - 101(a) 到点触发/通知内容＝主题内条目（需设提醒＋等到点或改系统时间）；
  - 101(b) 多主题确定性次序（配方 A 外部轨不可达——JVM EntryLocatorTest 已钉；B/C 人工）；
  - 101(c) 提醒不被误清断言（chmod 轨已通，需 sidecar 有 key 配合）；
  - 101(d) 开机补发（跨点/改时间）；101(e) SAF 会话中失效法（§17.3 照抄）；
  - 101(f)(g) chmod 500 topics→移出 SourceDeleteFailed→恢复→重试收敛（JVM MoveFaults 轨已钉，真机轨可选）；
  - 101(l)(q)(u) 移出/转移/护栏 UI 流程；101(n)①② 确认框文案走查；(t) 轮询/补发双指标体感。
- **已完成·真机驱动轮（2026-11-16，adb 驱动＋截图断言；截图 D:/tmp/t27_12~34）**：
  - **多选入口缺陷发现＋修复＋重验**：T-26 落的多选 bar 无进入路径（独立提交 fix）——修复后浏览态顶栏 CheckCircle ✓→contextual bar 两钮「移出到主列表」「移入其他主题」成型 ✓；
  - **101(q) 移出成功路径完整闭环 ✓**：勾选 2 条→确认框「将 2 条移出到主列表。」（dlgMoveOutConfirmBody 逐字；101(n)① 无失效行）→执行→toast「移出到主列表 2 条」→工作区计数 30→28→退出多选→主列表「已收集 4 条」、时间戳原样＋升序插入＋计数同步；
  - **101(u) ✓**：目标选择列表排除当前主题（seed8 不在列）；「新建主题…」入口在列；
  - **101(l) 主体 ✓**：转移确认框「将 2 条移入「seed7」，并从「seed8」移除。」（dlgTransferConfirmBody 逐字）→执行→toast「移入主题 2 条」（§24.2 复用）→源 seed8 26 条；
  - **数据清理 ✓**：主列表 2 条 seed 条目 UI 右滑删除恢复原 2 条（删除联动走 reconcile 全库判定，无异常）；seed 10 主题保留为 (t) 走查资产（clean 脚本随时可清）；
  - 驱动方法备忘：锁屏/通知栏会吃掉 input（mCurrentFocus 先查）；exec-out screencap 有缓存假象——改 shell 侧 screencap＋pull；uiautomator dump 需避开对话框过渡动画，坐标以 dump bounds 为准、顶栏钮用固定坐标。
- **待人工清单（更新）**：101(a) 到点触发/通知内容；(b) B/C 配方；(c) 提醒不误清（chmod 通道已通，需 sidecar 配合）；(d) 跨点补发；(e) SAF 会话中失效法；(f)(g) 真机轨可选（JVM 已钉）；**D2 新建主题为目标路径走查（自动化竞态放弃——对话框被 bounce 双击意外关闭两次，人工 1 分钟）**；(n)② K>0 保留告知行（需设提醒）；(t) 双指标体感。

- **人工验收轮（2026-11-16，用户走查）**：**1/2/3 通过**——①D2 新建主题为目标路径 ✓（101(l) D2 断言）；②设提醒→到点触发→点通知 ✓（101(a) 到点/通知内容/深链）；③提醒所在条目移入主题后照常触发 ✓（101(a) 主体：移入后提醒存活）。未验：(b) B/C、(c) sidecar 配合、(d) 跨点补发、(e) SAF 失效法、(f)(g) 真机轨（可选）、(n)② K>0、(t) 体感。
- **走查发现缺陷·滑动指示文字不可见（已修复）**：用户报告「左划/右划仅有颜色无文字」——核实为**对齐方向反了**的真 bug：右滑卡片右移露出左缘，指示文字却对齐 CenterEnd（右缘）＝被卡片本体永久盖住；EntryListScreen.EntryCard 与 SwipeCard 两处同病（归档/主题/主列表/工作区全页面命中）。修复＝对齐改到被露出侧（EntryCard CenterStart；SwipeCard 按方向分支取反）。真机验证：条目右滑「删除」✓、主题卡右滑「删除」✓（左滑「追加」同款路径）。全量 249/0/0/2＋装机。
- **门槛状态**：**未全过——T-28 未启**（剩余项均跨点/低频类：(b)(c)(d)(e)(n)②(t)；建议随日常使用自然覆盖或 T-34 合并验收时一并做）。

## 8. 开放决策点（已全部裁定）

| # | 决策点 | 裁定（2026-11-13 用户「两案均采」＋三视角一致支持） | 落点 |
|---|---|---|---|
| D1 | 工作区顶栏「复制全部」图标（icons-core 无 ContentCopy） | **文字动作**：「复制」TextButton＋分享 Share 图标（零新依赖） | T-29；(p) 深色走查点名文字动作形态 |
| D2 | 转移目标选择是否含「新建主题」子流程 | **含**（复用移入对话框 2/2b 路径，不得旁路新写） | T-26；101(l) 补「经新建主题为目标」断言 |

> **计划状态**：**定稿（2026-11-13 用户确认）**——三视角评审 27 项全采纳＋D1/D2 已裁定（两案均采）；编码自 T-20 起（T-20/T-21/T-22/T-24 文件不相交可并行段），进度记入 §7。
