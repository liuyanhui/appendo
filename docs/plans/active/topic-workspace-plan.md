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

## 8. 开放决策点（已全部裁定）

| # | 决策点 | 裁定（2026-11-13 用户「两案均采」＋三视角一致支持） | 落点 |
|---|---|---|---|
| D1 | 工作区顶栏「复制全部」图标（icons-core 无 ContentCopy） | **文字动作**：「复制」TextButton＋分享 Share 图标（零新依赖） | T-29；(p) 深色走查点名文字动作形态 |
| D2 | 转移目标选择是否含「新建主题」子流程 | **含**（复用移入对话框 2/2b 路径，不得旁路新写） | T-26；101(l) 补「经新建主题为目标」断言 |

> **计划状态**：**定稿（2026-11-13 用户确认）**——三视角评审 27 项全采纳＋D1/D2 已裁定（两案均采）；编码自 T-20 起（T-20/T-21/T-22/T-24 文件不相交可并行段），进度记入 §7。
