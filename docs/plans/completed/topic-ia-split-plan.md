# 「移入主题」甲批次（主题 IA 分家＋归档清空合并）— 阶段3 执行计划

> **状态：已完成（2026-11-11 发布 v1.4.0）**——T-12~T-19 八任务零返工全闭环＋换机验证八轮修订（提交 5796b32）全过；v1.3.x 豁免结案（b58d648）；发布＝push＋tag v1.4.0＋GitHub Release（附 APK）。归档自 active/。

> **与原计划的关系**：本计划是 `topic-move-plan.md`（原批次 T-01~T-11）的**甲批次续篇**，原计划文件不改动（历史快照，其进度记录止于 2026-11-04 设计画布派出，此后进度记入本计划 §7）。衔接口径：原 **T-10 真机验收的 5 项缺口（89(c)(d)(i)(m)(p)）由本计划 T-18 吸收合并验收**；原 **T-11 文档同步义务（README/CHANGELOG/architecture.md/debt-tracker/版本号）由本计划 T-19 全量吸收**（含其评估结果中登记的 12 条遗留建议）。
>
> **修订记录**
>
> | 日期 | 变更人 | 变更摘要 |
> |---|---|---|
> | 2026-11-04 | Claude（Generator） | 首次创建（**草案待评审**）：依设计修订二 §14~§18 定稿拆解 8 个任务（T-12~T-19）＋计划级 Evaluator 检查单＋94(a)~(f)＋89(c)(d)(i)(m)(p) 真机验收表（§17.2/§17.3/§17.4 照抄）＋风险注意事项＋拆分决策记录（不新开裁定） |
> | 2026-11-04 | Claude（Evaluator→Generator 修订） | Evaluator 第 1 轮：11 项检查单 10 PASS/1 FAIL＋20 处锚点抽验 18 中；必修 2（P-1 T-15⑤ 补 `setFileLastModified`→`clearFileLastModified` 替换口径；P-2 BTN_CLEAR 行号勘误 :758/:1083→:753/:1079）＋后置 4 项一并清理（C-1 grep 全路径统一/C-2 T-16 零命中加 KDoc 豁免/C-3 R12 触碰计数口径对齐/C-4 注入先例引用拆明）。任务结构/依赖链/验收表/交接口径未动 |
>
> **状态：已定稿（2026-11-04 用户确认）——阶段4 编码中，自 T-12 起**
>
> 依据（冲突时以先者为准）：
> 1. `docs/specs.md` 16.A（条文 90~94＋需求 12/13/16/17/48/80(b) 就地注，2026-11-04 定稿）——验收基线
> 2. `docs/design/topic-move-design.md` **修订二 §14~§18（定稿 2026-11-04，:713~1004）**——唯一技术权威源，任务拆分以其 §14（改动面）/§15（交互）/§16（微文案）/§17（测试与验收）/§18（风险 R12~R18）为直接输入；§1~§13 为原批次已实现的历史定稿，与 §14~§18 冲突处以后者为准
> 3. `docs/plans/active/topic-move-plan.md`——格式样板＋执行模式修订口径（2026-11-02）＋T-09 质量门/T-10 真机表先例＋T-10 缺口与 T-11 义务交接口径
> 4. `CODINGRULES.md`（测试/常量规范）、项目 `CLAUDE.md`（任务定义格式、六阶段流程）
>
> 执行模式：Planner-Generator-Evaluator **流水线并行**（沿原计划 2026-11-02 用户指示修订后的口径）——**编码 Generator 保持串行**（共享工作区＋MainScreen.kt 等交叉文件＋逐任务全量测试门）；**只读核验（Planner）与文件不相交的下一任务 Generator 可流水线并行**（gradle 项目锁自然排队）。所有测试命令一律 `--max-workers=1`。

---

## 0. 概述与版本号说明

**概述**：本计划把设计修订二（主题一等公民 IA 分家＋归档清空合并，方案甲）拆为 8 个可串行执行、可逐条验收的任务。依赖主线：先做 **两组基础件**（T-12 TopicBadge 色板派生纯函数＋`consumeFirstMoveGuide` 旗标注入口；T-13 SwipeCard 手势骨架下沉共用——行为等价红线），随后 **UI 两任务串行**（T-14 主题独立页、T-15 MainScreen 归档清空合并——共改 MainScreen.kt），再 **分家与微文案收口**（T-16），过 **质量门**（T-17 全量单测＋assembleDebug＋archiveFile git diff 逐行核对），交 **真机合并验收**（T-18，用户执行，94 全项＋原 T-10 缺口 5 项），最后 **文档收尾**（T-19，原 T-11 义务全量＋甲新增义务）。数据层改动面＝§14.3 口径：data 目录仅新增一个与协议无关的旗标方法，事务与协议语义零改动；util/reminder 两目录零文件增删改（全任务边界红线）。

**版本号：v1.4.0（versionCode 11）——沿用原计划 Q1 裁定（2026-11-01 用户裁定）**。现状 `Appendo/build.gradle.kts` 仍为 1.3.1/versionCode 10（原 T-11 未执行、版本未发布），本批次 T-19 **一次性升位**、CHANGELOG 单条 v1.4.0 条目覆盖两批变更（移入主题主特性＋甲三条可感知变更），无二次升位问题。

---

## 1. 任务依赖链总览

| 任务 | 一句话内容 | 硬依赖 | 产出性质 |
|---|---|---|---|
| T-12 | TopicBadge 色板派生纯函数（双参 8 色 4.583）＋`TopicRepository.consumeFirstMoveGuide()`＋两组 JVM 测试 | — | ui 纯函数＋data 小增＋单测 |
| T-13 | SwipeCard 手势骨架下沉 `ui/SwipeCard.kt`（performVibration internal 三合一；恒真 AnimatedVisibility 丢弃）；ArchiveListScreen 改消费 | — | 纯重构（行为等价红线） |
| T-14 | 主题独立页 `ui/TopicListScreen.kt`＋`topics` 路由＋顶栏「主题」入口（MainScreen 顶栏区小改） | T-12、T-13 | ui 新页＋Microcopy 增量 |
| T-15 | 网格第四格「归档」二选一＋`archiveFile→ArchiveOutcome` 重构＋菜单改造＋首次指路接线＋决策映射测试 | T-12 | ui 重构＋**行为变更明示**＋单测 |
| T-16 | 归档页回退单列表（分家）＋TopicMoveDialogs:108 副文案对齐＋Microcopy §16 全量收口＋grep 防线 | T-13、T-15（串行链上居 T-14 后） | 分家收口＋微文案 |
| T-17 | 全量单测＋assembleDebug＋§17.1 测试义务核对＋archiveFile git diff 逐行核对清单＋§17.2 代码级核对 | T-12~T-16 | 质量门 |
| T-18 | 真机合并验收 94(a)~(f)＋89(c)(d)(i)(m)(p)（用户执行，TD-024） | T-17 | 验收记录 |
| T-19 | 文档收尾：原 T-11 义务全量＋甲新增（README §16.4 五项/CHANGELOG/architecture.md/debt-tracker 12 条遗留集中登记/版本 v1.4.0） | T-17（置于 T-18 后执行） | 文档 |

派发顺序＝T-12→T-19 线性串行（编码 Generator 串行）。并行机会与串行约束（沿执行模式口径）：

- **T-12 与 T-13 文件不相交**（T-12：TopicBadge.kt/TopicRepository.kt＋两测试；T-13：SwipeCard.kt/ArchiveListScreen/EntryListScreen/MainScreen）——T-12 进入 Planner 只读核验时 T-13 Generator 可开工（流水线首对）；
- **T-14 与 T-15 共改 `MainScreen.kt`**（T-14 顶栏 actions 区＋`onNavigateToTopics` 参数；T-15 网格/菜单/对话框/archiveFile/指路）——严格串行；
- **T-15 与 T-16 共改 `Microcopy.kt`**——严格串行；
- 每编码任务收尾跑全量单测（`--max-workers=1`）防交叉回归；发现前驱任务缺陷记录并交 Planner 判定，不越任务边界修。

---

## 2. 任务列表（标准任务定义格式）

### T-12 基础件：TopicBadge 色板派生＋consumeFirstMoveGuide

- **任务ID**: T-12
- **描述**: 新增 `ui/TopicBadge.kt`（§14.1，~80 行，全 JVM 可测）：`deriveBadgePalette(primary: Color, surface: Color)` 双参纯函数（§15.1 定稿：HSL 色相旋转 45°×i（i=0..7）、饱和度钳制 [0.42, 0.78]、明度固定带（浅色 0.36／深色 0.44）、判深浅用 `surface.luminance() ≥ 0.5` 不另传模式枚举；RGB↔HSL 纯 Kotlin 实现，不用 android.graphics）、`contentColorFor`（白/黑二选一取对比度较大者——两曲线交点即数学下界 4.583:1）、`badgeIndex(name) = floorMod(name.hashCode(), 8)`（全名散列）、`firstGrapheme(name)`（`java.text.BreakIterator.getCharacterInstance()` 首边界，空名防御返回「？」）；`data/TopicRepository.kt` 新增 `consumeFirstMoveGuide(): Boolean`（§14.3 A-1 改道：经 Q2 构造器注入的 `prefs`（:109）读 `first_move_guide_shown`，未展示过则置位 `commit()` 后返 true、已展示返 false；与 `created_*` 同 SP 文件 `appendo_topics` 不新建文件）；新增 `ui/TopicBadgeTest.kt`（§17.1 第一行 ①~④）＋扩展 `data/TopicRepositoryTest.kt`（§17.1 第二行）。
- **约束**:
  - 文件边界：`ui/TopicBadge.kt`（新）、`data/TopicRepository.kt`（仅新增方法）、`ui/TopicBadgeTest.kt`（新）、`data/TopicRepositoryTest.kt`（仅新增用例）——共 4 文件；
  - **既有方法与存储协议零改动**（§14.3「数据层零改动」口径：仅一个与协议无关的旗标方法）；util/reminder 零触碰；
  - 零写死色值（P7/R17 受控例外：色板运行时自 colorScheme 双参派生）；SP 写入一律 `commit()`（TD-024 纪律）；
  - 测试纯 JVM：TopicBadgeTest 直接引用 `AppendoLightScheme`/`AppendoDarkScheme`（Theme.kt internal，测试编译可见）；TopicRepositoryTest 沿 Q2 内存版假 SharedPreferences 先例，不引入 Robolectric（TD-003）；本任务无需权限注入（若实现中另需只读注入，沿既有 Windows 等价替换先例：路径占位＝T-04⑥ 同名文件占位、文件级 setWritable＝T-02）。
- **任务产出**: `TopicBadge.kt`＋`consumeFirstMoveGuide()`＋两组测试。
- **评估标准**:
  - [ ] `ui/TopicBadge.kt` 存在，四函数签名与 §14.1/§15.1 定稿一致（`deriveBadgePalette` 双参；`badgeIndex` 为 `floorMod(name.hashCode(), 8)`；`firstGrapheme` 用 BreakIterator；空名返「？」）
  - [ ] TopicBadgeTest 覆盖 §17.1 第一行 ①~④全绿：① hash 稳定性（同名恒同 index；floorMod 恒界内 [0,8)）② **双色板成对断言**——`(AppendoLightScheme.primary, AppendoLightScheme.surface)` 与 `(AppendoDarkScheme.primary, AppendoDarkScheme.surface)` 各派生 8 色，逐色以 WCAG 相对亮度公式复算 `contentColorFor` 所选文字色对比度 ≥ 4.5:1（4.583 数学下界回归钉死）③ 首字取法用例集：普通汉字/字母、surrogate 对 emoji（😀）、ZWJ 序列（👨‍👩‍👧）、国旗旗对、BMP 外生僻字（𠀀）、空串返「？」④ 派生确定性：同 (primary, surface) 参数对同色板
  - [ ] TopicRepositoryTest 扩展 §17.1 第二行全绿：① 未展示 → 置位并返 true（断言假 SP 已 commit `first_move_guide_shown=true`）② 已展示 → 返 false 且不再写 ③ 「N=0 不消耗不展示」＝调用方门控（`moved > 0` 方调 consume，§15.4）——以 `consumeFirstMoveGuide` KDoc 注明门控语义，断言落 T-15 M1 决策映射
  - [ ] `grep -n "apply()" Appendo/src/main/java/com/yiyue31/android/appendo/data/TopicRepository.kt` 零命中（新方法延续 commit 纪律）
  - [ ] `grep -n "android.graphics" Appendo/src/main/java/com/yiyue31/android/appendo/ui/TopicBadge.kt` 零命中（HSL 纯 Kotlin）
  - [ ] 全量单测 `--max-workers=1` 全绿（既有 200 条零修改＋新增全绿）
- **评估方法**: 编译＋单测＋grep＋代码评审（HSL 派生/明度带/散列/旗标读写）。
- **评估结果**: ✅ **通过（2026-11-04）**——Planner 独立核验 6 条全 PASS（评审对象＝提交 `bb2786f`，工作区隔离实证）：四函数签名逐项落实（Planner 自写双精度复算脚本验证 HSL 旋转/饱和度钳制/色相回绕/明度带判别——**不偷读 MaterialTheme**；对比度实测 light 5.475/dark 4.610 与精确数学 5.480/4.625 三方互证，量化差属 compose Color 半精度打包固有）、TopicBadgeTest ①~④实质断言（复算公式与 compose luminance 解耦）、仓储扩展两用例（WriteRecording 装饰器使「零写」可断言，类委托零语义改动）、双 grep 零命中、`--rerun` 206/0/0/0、恰 4 文件纯追加。四偏离全接受（行量＝转换器义务＋常量规范；grep 措辞＝正解；装饰器＝最小必要；IDE 警告不可 CLI 复现）。建议 3 条信息级（量化裕度知悉 T-17/IDE 警告登记 T-19/DONE 分支记录）。
- **当前轮次**: 1　**最大轮次**: 2　**状态**: ✅ 已完成

### T-13 SwipeCard 抽取（ArchiveCard 手势件下沉共用）

- **任务ID**: T-13
- **描述**: 新增 `ui/SwipeCard.kt`（§14.1，~110 行）：自 `ArchiveListScreen.kt` 的 `ArchiveCard`（:473 起）下沉滑动手势骨架——offset 状态、120dp 阈值（SWIPE_THRESHOLD_DP）、方向背景指示（「删除」error 红／「追加」successColor 绿＋onError/onSuccessColor 文字色，需求 51 深色注释随迁）、禁删钳 0（`swipeToDeleteEnabled` 参数）、首卡 elevation 动画（index==0 8dp）、行为回调注入；**指示字面与配色随骨架下沉、不参数化**（两页语义同构）；既有恒真 `AnimatedVisibility(visible = true)` 包装（:513）**丢弃**（A-6 后置项顺手落，列入机械重构范围）；**`performVibration` 随骨架下沉本文件为 internal 共享**（A-4/E-3 三方收敛）——三处旧私有拷贝删除改引用：`ArchiveListScreen.kt:628`（随手势段消失）、`EntryListScreen.kt:385`、`MainScreen.kt:1666`；`ArchiveListScreen` 的 `ArchiveCard` 退化为薄布局包装，**调用点零改动**（§14.1 A8 裁定）。
- **约束**:
  - 文件边界：`ui/SwipeCard.kt`（新）、`ui/ArchiveListScreen.kt`（手势段迁移＋薄包装）、`ui/EntryListScreen.kt`（仅 performVibration 引用）、`ui/MainScreen.kt`（仅 performVibration 引用）；
  - **行为等价红线**（R16）：阈值/钳制/指示字面/配色语义/elevation 动画零变化——等价性证据＝既有 200 测试零修改全绿＋机械重构 diff 清单（随任务产出留档，逐项列出手势段迁移行对）；不重构 EntryListScreen 的既有滑删件（其手势逻辑形态不同，仅消重振动函数）；
  - 主题分区（:210-267）本任务**不动**（属 T-16）；振动调用点在卡片调用方回调处，随骨架改引 internal 后编译等价。
- **任务产出**: `SwipeCard.kt`＋三文件消费改引＋机械重构 diff 清单。
- **评估标准**:
  - [ ] `ui/SwipeCard.kt` 存在；`grep -rn "private fun performVibration" Appendo/src/main/java --include="*.kt"` **零命中**（三处旧拷贝全消，改引 SwipeCard internal）
  - [ ] `grep -n "AnimatedVisibility" Appendo/src/main/java/com/yiyue31/android/appendo/ui/ArchiveListScreen.kt` 手势卡内恒真包装零命中（骨架丢弃；SwipeCard.kt 内无恒真包装）
  - [ ] `grep -n "SWIPE_THRESHOLD\|120" Appendo/src/main/java/com/yiyue31/android/appendo/ui/SwipeCard.kt` 阈值与迁移前一致（120dp 语义不变）
  - [ ] **既有 200 测试零修改全绿**：`git status Appendo/src/test/` 零改动＋`./gradlew testDebugUnitTest --max-workers=1` 0 失败
  - [ ] 机械重构 diff 清单留档并逐项可对（offset/阈值/指示字面/指示色/钳制/elevation 六件套逐项「迁移前后行对＋零语义变更」结论）
- **评估方法**: 编译＋全量单测＋grep＋diff 清单评审。
- **评估结果**: ✅ **通过（2026-11-04）**——Planner 独立核验 6 条全 PASS（评审对象＝提交 `91fdaaa`）：**六件套 token 级逐字一致**（去缩进 diff 两侧各 90 行 IDENTICAL——offset/阈值 120dp/指示字面/六色/禁删钳/elevation spring 全在列）、恒真 AnimatedVisibility 丢弃无语义损失（布尔重载恒真无进场动画实证）、振动三拷贝合一（旧拷贝逐字比对仅 tag/可见性差异；时长常量集中＝CODINGRULES:32 义务）、9 调用点文本未变同包解析、206 本机 `--rerun` 零失败、恰 4 文件。四偏离全裁定成立（①②③合规、④红线内正确）。**建议（T-19 登 debt-tracker）**：EntryListScreen:254 同款恒真死包装＋私有 SWIPE_THRESHOLD_DP 与 SwipeCard 同值重复——待 EntryCard 未来收敛时一并清理。
- **当前轮次**: 1　**最大轮次**: 2　**状态**: ✅ 已完成

### T-14 主题独立页（topics 路由＋顶栏入口）

- **任务ID**: T-14
- **描述**: 新增 `ui/TopicListScreen.kt`（§14.1/§15.1，~260 行）：签名 `TopicListScreen(fileRepository, onNavigateBack, onTopicClick: (File) -> Unit)`——顶栏（标题「主题」primary SemiBold 沿 ArchiveListScreen 先例＋返回箭头＋`Icons.Default.AddCircle`「新建主题」常驻含空态，B2）；列表 `LazyColumn` `listTopics()` 直供（createdAt 倒序＝85(c) 维持）、`LaunchedEffect(Unit)`＋`Dispatchers.IO` 加载（「加载中...」沿先例直写）、**操作后重载亦经 `Dispatchers.IO`**（E-4：不沿袭 ArchiveListScreen 既有主线程重载）；TopicCard＝`SwipeCard` 薄包装＋内容行（**40dp 彩色圆形首字标**：容器色＝`deriveBadgePalette(colorScheme.primary, colorScheme.surface)[badgeIndex(displayName)]`、字＝`firstGrapheme(displayName)` titleLarge SemiBold 居中、文字色＝`contentColorFor`；主题名 titleMedium Medium；小字 `topicCardSubtitle(count, timeLabel)`「N 条 · 创建时间」条数在前、onSurfaceVariant）；空主题小字＝`TOPIC_EMPTY_SUBTITLE`「空」；交互四槽：点击→`onTopicClick`（85(d) 只读限制随 90 平移）、长按→复制全文（parse＋formatForExport 出口铁律，「已复制全部内容」沿需求 79 既有常量）、左滑→恢复（`RestoreConfirmDialog` 零改动消费＋`TOPIC_RESTORE_NOTE`＋第 15 章句式 toast＋恢复后重载）、空主题（0 条）右滑→删除（`DLG_TOPIC_DELETE_TITLE` error 红框＋`dlgTopicDeleteBody`＋`deleteTopic`、失败 toast）、**非空主题右滑钳 0 零响应**（SwipeCard `swipeToDeleteEnabled=false`）；页级空态 `TOPIC_PAGE_EMPTY_TITLE/BODY`；页内新建状态机 `NONE→NAMING→SAME_NAME_PROMPT` 三态（沿移入流程状态机模式）：复用 `NewTopicNameDialog`（形态/聚焦/「创建」按钮零改动；**对话框 2b 指路不复用**）→ `createEmptyTopic`（83 修订校验链原子复用）；`Created`→关框＋重载＋`TOAST_TOPIC_CREATED`；`WriteFailed`→`TOAST_TOPIC_CREATE_FAILED` 框保持输入保留；`NameExists`→**C5 同名提示分支**（§15.1：`DLG_SAME_NAME_TITLE`＋`dlgTopicPageSameNameBody(x)`＋`BTN_GOT_IT` primary；点外/系统返回回命名框**输入保留**；**无「移入该主题」按钮**——94(b) 断言）。改 `ui/MainScreen.kt`：① 浏览态顶栏 actions 增「主题」入口图标（`Icons.Default.List`，contentDescription「主题」直写沿先例；排列 `[主题][多选][菜单]`；**0 条仍显示**、多选图标 0 条隐藏行为不变；**多选态随 contextual bar 整体隐藏**——P3-13，退出多选即恢复）；② 新增参数 `onNavigateToTopics: () -> Unit = {}`（默认值，未接线方零破坏）。改 `MainActivity.kt`：`composable("topics")` 路由＋main 调用点 `onNavigateToTopics` 接线＋`onTopicClick`→`archive_detail/{filePath}?kind=topic` 导航（沿 :111-118 构造式）。
- **约束**:
  - 文件边界：`TopicListScreen.kt`（新）、`MainScreen.kt`（仅顶栏 actions 区＋参数）、`MainActivity.kt`（仅 topics 路由＋接线）、`Microcopy.kt`（仅本任务消费增量：`TOPIC_PAGE_EMPTY_TITLE`/`TOPIC_PAGE_EMPTY_BODY`/`dlgTopicPageSameNameBody`/`BTN_GOT_IT`/`TOAST_TOPIC_CREATED`/`TOAST_TOPIC_CREATE_FAILED` 6 项＋`topicCardSubtitle` 签名与序改 §16.2）、`ArchiveListScreen.kt`（**仅 `topicCardSubtitle` 签名改的编译机械跟随 1 行**——设计 §16.2 明示唯一消费方＝新 TopicCard，旧调用行随 T-16 分家消亡，本任务仅保编译绿）；
  - 图标仅 material-icons-core 依赖面（List/AddCircle 均在 core 集，零新依赖）；颜色全经 colorScheme（首字标色板经 TopicBadge 派生＝R17 受控例外）＋successColor/onSuccessColor 滑动指示随 SwipeCard 既有；
  - `NewTopicNameDialog`/`RestoreConfirmDialog`/`ArchiveEntryKind` **零改动消费**（import 复用）；深色默认适配（需求 51）；`archive_detail` 路由与 kind 解析零改动（§14.2）。
- **任务产出**: `TopicListScreen.kt`＋MainScreen/MainActivity 接线＋Microcopy 增量。
- **评估标准**:
  - [ ] `ui/TopicListScreen.kt` 存在且签名与 §14.1 一致；`topics` 路由可达、主题卡点击进 `archive_detail?kind=topic`（代码走查路由构造）
  - [ ] §15.1 设计表逐行可指认：顶栏三件/列表加载与重载均 IO/TopicCard 三件套（40dp 首字标＋名＋小字）/交互四槽（点击·长按复制·左滑恢复·空删非空钳 0）/页级空态/新建三态含 C5 同名分支**无指路按钮**、取消回命名**输入保留**
  - [ ] MainScreen 顶栏：浏览态排列 `[主题][多选][菜单]` 且 0 条仍显示；多选态无主题入口（contextual bar 不含，退出即恢复）；`onNavigateToTopics` 带默认参数 `{}`
  - [ ] 首字标派生链完整：`grep -n "Color(0x" Appendo/src/main/java/com/yiyue31/android/appendo/ui/TopicListScreen.kt Appendo/src/main/java/com/yiyue31/android/appendo/ui/TopicBadge.kt` 零命中；小字「N 条 · 创建时间」**条数在前**（`topicCardSubtitle` 新序＋:108 之外无第二处旧序）
  - [ ] 本任务消费常量入 Microcopy 且字面与 §16.1 定稿**逐字一致**（6 项）；`grep -rn "暂无主题\|知道了\|已创建\|创建失败\|请换一个名称" Appendo/src/main/java --include="*.kt" | grep -v Microcopy.kt` → 仅 KDoc/注释命中
  - [ ] 全量单测 `--max-workers=1` 全绿（既有 200 零修改）
- **评估方法**: 编译＋单测＋grep＋代码走查（对照 §15.1 逐行/§16.1 逐字）。
- **评估结果**: ✅ **通过（2026-11-04）**——Planner 独立核验 7 条全 PASS（评审对象＝提交 `e84aea9`，工作区与提交一致实证）：签名/路由链与既有 archive_list 同一构造式、§15.1 七行逐行落点（reloadTopics 统一 IO 口四处重载全覆盖；C5 同名框独立实现无误引无指路按钮、输入保留成立；取色链页级派生完整）、顶栏排列与显隐（0 条常驻/多选态整体替换）、首字标零写死＋新序唯一（:108 有意未动属 T-16）、Microcopy 6 项全角标点逐字一致、`--rerun` 206/0/0/0 实测、恰 5 文件（loadFiles 有意未修遵守约束）。两偏离全接受（AutoMirrored.List＝弃用警告规避＋RTL 正确＋先例一致；448 行＝KDoc 密度）。**建议 2 条**：MainScreen:561 注释措辞（T-15 顺手修）／TopicListScreen:195 indexOf O(n²)（后续 itemsIndexed 对齐，非阻断）。
- **当前轮次**: 1　**最大轮次**: 2　**状态**: ✅ 已完成

### T-15 MainScreen 归档清空合并（X 方案）＋首次指路＋菜单改造

- **任务ID**: T-15
- **描述**: 改 `ui/MainScreen.kt` 七点（§14.1 ①~⑦）：① 第四格：旧清空 Box（:718-759，combinedClickable 长按＋红描边）→ **`OutlinedButton`「归档」**（`BTN_CELL_ARCHIVE`；与「复制全部/分享全部」同款形态：primary 0.5α 描边＋primary 文字、RoundedCornerShape(16dp)、56dp；**点击**触发）；长按语义、振动、`onLongClickLabel`、「长按以清空内容」提示 toast 全退役；② 触发守卫：主列表 0 条目点击 → `TOAST_NOTHING_TO_ARCHIVE`＋**不开对话框**（P9：消除旧路径双 toast 冗余）；③ 二选一对话框（§15.3 C6）：标题 `DLG_ARCHIVE_TITLE`（默认色）→ 正文 `dlgArchiveBody(n)`（N＝entryCount 实时值）→ 正文下方纵排「归档并清空」（`Button` 填充，containerColor＝error/contentColor＝onError，78(a)，全宽，**FocusRequester 默认焦点**——93 明令）与「只归档，保留列表」（`OutlinedButton` primary，78(b)，全宽）→ action 区「取消」（dismiss 槽）；④ `archiveFile`（:1847-1886）重构：返回值 `Boolean` → **`sealed ArchiveOutcome`**（`Archived(file)`/`NothingToArchive`/`Failed`；sealed 落 MainScreen.kt 顶层，A-2）；**writeAll 写盘段逐字节等价**（A-3/T-3：等价宣称限于写盘段）；内部 toast 全移除（「暂无内容可归档」「归档失败」「已归档到: 」「归档失败: $msg」四条）；**空内容分支行为变更**：现状返 true 继续清空 → `NothingToArchive` **不清空**＋调用方提示（入口守卫配套防呆改进，R15 陈旧窗口下可达）；**删除未使用的 `fileRepository` 参数**（E-1）；执行在 `Dispatchers.IO`（A-9）；⑤ 两路径编排按 §15.3 表：归档并清空＝`Archived`→`clear()`→重同步三状态（useSAF/fileUri/defaultFile）＋`clearFileLastModified()`（**替换口径**：原旧清空框 `setFileLastModified`（:1067）改为 `clearFileLastModified()`——两者同为驱动 2 秒轮询刷新的 SP 时间戳操作，清空后文件已空、清键语义更准；此替换为 T-17 git diff 清单中 :1067 删除行的解释口径）＋`refreshEntryCount()`（承接原计数清零）＋**取消全部提醒**（cancel 全部＋`removeAll()`，需求 63）＋`toastArchivedAndCleared(name)`；`clear()` 失败→`TOAST_CLEAR_FAILED_ARCHIVED`；只归档＝`Archived`→重同步三状态（`clearFileLastModified()` **丢弃**——主文件未动，理由注明）＋`toastArchivedTo(name)`；`Failed`→按路径 `TOAST_ARCHIVE_FAILED_CLEAR_KEPT`/`TOAST_ARCHIVE_FAILED`＋**均不得清空**（TD-017 传承）；`NothingToArchive`→两路径「暂无内容可归档」无动作；**两段各以 runCatching 包裹**（E-1：归档段异常→归 `Failed`；清空段异常→清空失败文案；catch-all「操作失败」:1075 退役）；**决策提为 MainScreen.kt 顶层 internal 纯函数**（`ArchiveOutcome × 路径 → toast 字面/是否 clear/是否取消提醒`——M1 ③）；对话框一次性失败不重试（沿旧清空框处置）；⑥ 菜单：删「归档」项（:575-587）、「归档与主题」→「归档管理」（:589）、「主题」→「外观」（:617）＋`ThemePickerDialog` 标题（:1710）同步「外观」（三态功能零变化，R18）；⑦ 首次指路（§15.4 U1）：`executeMove` 成功回调（**`moved > 0` 的 Success**）IO 段内经 MainScreen 既有 `topicRepository`（:193）直调 `consumeFirstMoveGuide()`；toast 拼接序固定：基础文案 → `removedRemindersSuffix(k)`（K>0 时）→ `firstMoveGuideSuffix`（首次时），**单 toast 原则**不弹第二条；N=0 成功（moved=0）**不调用 consume**；`dlgMoveConfirmBody` 第二行更正（§16.2：去向行 specs 92 逐字）。新增 `ui/ArchiveOutcomeMappingTest.kt`（§17.1 第三行 ①~⑤全组合）。
- **约束**:
  - 文件边界：`MainScreen.kt`、`Microcopy.kt`（本任务消费增量 12 项：`BTN_CELL_ARCHIVE`/`DLG_ARCHIVE_TITLE`/`dlgArchiveBody`/`BTN_ARCHIVE_AND_CLEAR`/`BTN_ARCHIVE_ONLY`/`toastArchivedTo`/`toastArchivedAndCleared`/`TOAST_ARCHIVE_FAILED`/`TOAST_ARCHIVE_FAILED_CLEAR_KEPT`/`TOAST_CLEAR_FAILED_ARCHIVED`/`TOAST_NOTHING_TO_ARCHIVE`/`firstMoveGuideSuffix`＋`dlgMoveConfirmBody` 修改＋`BTN_CLEAR` 删除）、`ui/ArchiveOutcomeMappingTest.kt`（新）；
  - 旧清空确认框（:1025-1088）整体退役（标题/正文/「操作失败」/「清空失败」直写随之消亡，§16.3）；`BTN_CLEAR` 两消费点（第四格 :753＋旧框 :1079，Evaluator 勘误后行号）随改造消亡后**常量一并删除**；
  - 决策映射纯函数＝ui 层纯函数 JVM 测试第三例（沿 MultiSelectTest/TopicMoveDialogsTest 先例）；**编排本身无 JVM 测试**（M1 边界）——语义保障＝写盘段等价＋决策测试＋94(d) 真机＋T-17 git diff 核对四件套；本任务 JVM 测试无需权限注入（如需沿 T-02 Windows 等价替换先例）。
- **任务产出**: MainScreen 七点改造＋Microcopy 增量＋`ArchiveOutcomeMappingTest.kt`。
- **评估标准**:
  - [ ] `grep -n "sealed" Appendo/src/main/java/com/yiyue31/android/appendo/ui/MainScreen.kt` 命中 `ArchiveOutcome` 三分类（顶层可见）；重构后 `archiveFile` 无 `fileRepository` 参数、方法体内无 `showToast` 调用
  - [ ] `ArchiveOutcomeMappingTest` 覆盖 §17.1 第三行 ①~⑤全组合全绿：`Archived`×归档并清空→`toastArchivedAndCleared`＋clear＋取消全部提醒／`Archived`×只归档→`toastArchivedTo`＋不 clear＋不动提醒／`Failed`×两路径→两失败字面＋均不 clear／`NothingToArchive`×两路径→「暂无内容可归档」＋均不 clear 无其他动作（**行为变更两路径钉死**）／清空段异常→`TOAST_CLEAR_FAILED_ARCHIVED` 映射
  - [ ] §15.3 两路径表逐行可指认（onComplete 等价动作五要素＋`clearFileLastModified` 丢弃理由注明）；runCatching 两段兜底可指认；「归档并清空」FocusRequester 默认焦点可指认
  - [ ] 退役 grep 零残留：`grep -rn "长按以清空\|长按清空\|已归档到:\|已取消清空\|操作失败\|归档失败:" Appendo/src/main/java --include="*.kt" | grep -v Microcopy.kt` → 零命中或仅 KDoc/注释；`grep -rn "BTN_CLEAR" Appendo/src/main/java` 零命中
  - [ ] 菜单三处：「归档」菜单项零命中（`归档」`项删除——`grep -n "text = { Text(\"归档\") }" Appendo/src/main/java/com/yiyue31/android/appendo/ui/MainScreen.kt` 零命中）；`grep -n "归档管理" Appendo/src/main/java/com/yiyue31/android/appendo/ui/MainScreen.kt` 命中菜单项；`grep -n "外观" Appendo/src/main/java/com/yiyue31/android/appendo/ui/MainScreen.kt` 命中菜单项＋ThemePickerDialog 标题两处
  - [ ] 首次指路：拼接序三元（基础→K>0 提醒→首次指路）可指认；`moved == 0` 分支无 consume 调用（代码评审）；单 toast（无第二条指路 toast 路径）
  - [ ] 本任务 12 项新增＋2 项修改入 Microcopy 字面与 §16.1/§16.2 定稿**逐字一致**（评估时逐条对照打勾）；全量单测 `--max-workers=1` 全绿（既有 200 零修改）
- **评估方法**: 编译＋单测＋grep＋代码走查（对照 §15.3 表/§15.4/§16.1§16.2 逐字）。
- **评估结果**: ✅ **通过（2026-11-05）**——Planner 从紧核验（编排方接管补全部分）7 条全 PASS（评审对象＝提交 `749bddd`）：**写盘段机械 diff 逐字节等价硬证据**（父子提交 archiveFile 全函数体 diff——签名与返回通道外零差异）、决策测试 ①~⑤ 实质断言、§15.3 表逐行落点（TD-017 代码评审＝clear 调用门不可达确认/替换口径注释在/两段 runCatching）、对话框形态与 0 条守卫、退役菜单全过、指路 moved>0 门控单 toast、213 绿经提交时 XML 时间戳核实（复跑被在途 T-16 阻断已注明）。**Microcopy 12 逐字比对全中**（Generator 遗产从紧核过）。**执行备注**：Generator 因 API 限流中断，Microcopy 为其遗产、MainScreen/测试为编排方接管补全（提交信息已注明）。**建议 2 条**：resyncArchiveState 全 outcome 执行（纯状态重读无副作用，T-17 diff 清单注记）／设计 §15.4 行号漂移（T-19 校准）。
- **当前轮次**: 1　**最大轮次**: 2　**状态**: ✅ 已完成

### T-16 分家与微文案收口（91＋§16 全量）

- **任务ID**: T-16
- **描述**: ① `ui/ArchiveListScreen.kt` 回退单列表（§15.2）：主题分区（:210-267）/主题删除对话框（:373-430）/topics 状态（:118）/`topicRepository`（:114）/`restoreKind`（:128、:436、:440）整体移除；页标题回「**归档管理**」（:151，需求 17 固化名）；整页空态改归档单态（`ARCHIVE_EMPTY_TITLE`「暂无归档」/`ARCHIVE_EMPTY_BODY`「归档文件会显示在这里」，:183 两区皆空判断简化）；签名 `onFileClick(File, kind)` 简化为 `onArchiveClick(File)`（kind 恒归档）；**`ArchiveEntryKind` 枚举留驻本文件**（包级可见不变——MainActivity/TopicListScreen 照旧 import，零迁移；KDoc「唯一产出源」句改写为主题页/归档页双源——A-5/E-5）；`MainActivity.kt` archive_list 调用点随签名简化（kind 分支删除）；② `ui/TopicMoveDialogs.kt:108` 副文案模板换序对齐（E-2）：`${formatTimestamp(...)} · ${entryCount} 条` → `**${entryCount} 条** · ${formatTimestamp(...)}`（与 `topicCardSubtitle` 同序、条数在前；**保持内联直写形态不变、不新增常量**）；③ `ui/Microcopy.kt` 全量收口：§16.1 新增逐条核对（19 行）、§16.2 修改核对（3 项）、§16.3 退役删除（`SECTION_TOPICS`/`SECTION_ARCHIVES`/`ARCHIVE_TOPIC_EMPTY_TITLE`/`ARCHIVE_TOPIC_EMPTY_BODY` 常量删除＋直写族消亡核对）＋grep 防线＋§16.4 句式族对齐核对（成功「已动词＋对象」逗号附注链式／失败「动词失败」附注／N·M·K 口径沿 §9.1）。
- **约束**:
  - 文件边界：`ArchiveListScreen.kt`、`MainActivity.kt`（仅 archive_list 调用点）、`TopicMoveDialogs.kt`（仅 :108 一行）、`Microcopy.kt`（退役＋查漏）；
  - `ArchiveRestore.kt`/`RestoreConfirmDialog`/`ArchiveRestoreDeduplicationTest` **零改动**（恢复共用不动摇）；归档页既有重载路径维持现状（E-4 修正仅施于新页，回退任务不扩面——如需修登 debt-tracker）；`archive_detail` 路由与 kind 解析零改动（§14.2）；归档区沿 `Appendo_` 前缀判据。
- **任务产出**: 分家后的 ArchiveListScreen＋MainActivity 调用点＋:108 对齐＋Microcopy 收口。
- **评估标准**:
  - [ ] `grep -n "TopicRepository\|restoreKind\|topics" Appendo/src/main/java/com/yiyue31/android/appendo/ui/ArchiveListScreen.kt` **代码区零命中**（分家干净；KDoc/注释豁免——`ArchiveEntryKind` KDoc 双源改写（A-5/E-5）含 topics 字样属预期）；`grep -n "归档管理" Appendo/src/main/java/com/yiyue31/android/appendo/ui/ArchiveListScreen.kt` 命中页标题
  - [ ] `grep -rn "归档与主题" Appendo/src/main/java --include="*.kt"` **零命中**（页标题/菜单/空态全回名）；`grep -rn "SECTION_TOPICS\|SECTION_ARCHIVES\|ARCHIVE_TOPIC_EMPTY" Appendo/src/main/java` 零命中（退役常量删除＋零引用）
  - [ ] TopicMoveDialogs.kt:108 为「N 条 · 创建时间」序（**条数在前**，代码评审逐字）
  - [ ] §16.1 全部 19 行新增逐条在 Microcopy.kt 有对应常量/函数且字面与定稿**逐字一致**（评估时逐条对照打勾）；§16.2 修改 3 项落实；§16.3 退役 6 行零残留
  - [ ] grep 防线零直写：`grep -rn "归档并清空\|只归档\|暂无归档\|暂无主题\|知道了\|可在顶栏" Appendo/src/main/java --include="*.kt" | grep -v Microcopy.kt` → 仅 KDoc/注释命中
  - [ ] `ArchiveRestoreDeduplicationTest` **零修改**全绿；全量单测 `--max-workers=1` 全绿（既有 200 零修改）
- **评估方法**: 编译＋单测＋grep＋§16 逐行对照表。
- **评估结果**: ✅ **通过（2026-11-05）**——Planner 独立核验 6 条全 PASS（评审对象＝提交 `1a64999`，本轮实跑复核）：分家零残留（grep 严于计划口径全文件零命中——KDoc 用大写 TopicListScreen 避开词根）、退役彻底（含 BTN_CLEAR 交叉复核零残留）、:108 恰 1 行换序、Microcopy **19/19 逐字一致**（全角标点全中）＋§16.2 三项落实、防线 7 命中全注释、213/0/0/0＋ArchiveRestoreDeduplicationTest 9/9 本轮实跑。恢复链归档路径逐语义等价（ArchiveRestore.kt 零改动铁证＋onDismiss/onConfirmed 逐字同）；MainActivity 归档路由与父提交 else 分支完全相同、kind 解析零改动。三偏离全接受。**建议 3 条归 T-19**：ArchiveCard `swipeToDeleteEnabled` 死参登 debt-tracker（新发现）／:75 KDoc 旧序失真顺手修／A8 措辞校准。
- **当前轮次**: 1　**最大轮次**: 2　**状态**: ✅ 已完成

### T-17 质量门（全量单测＋编译＋代码级核对）

- **任务ID**: T-17
- **描述**: 执行全量单测与编译；对照 §17.1 三张测试义务表逐要点核对覆盖完备性（未覆盖项须有明确去向指派）；**`archiveFile` 重构 git diff 逐行核对清单**（§17.1 M1 ⑤：写盘段零语义变更的逐行人工复核——toast 行删除、`fileRepository` 参数删除、返回通道改写、空内容分支行为变更逐项打勾，核对记录随产出留档）；§17.2 94(a)~(e) 代码级可核项逐项结论（真机判据移交 T-18）；§16 微文案终检。
- **约束**: 测试命令固定串行：`./gradlew testDebugUnitTest --max-workers=1`；编译 `./gradlew assembleDebug`；**内存预检**：执行前 `free -m`，available **< 600MB** 时停止并报告等待（T-09 先例）；不并行调度其他测试任务；flake 口径沿 T-04/T-09（复跑确认，仅确定性失败阻断——既有 flake 家族：nextTimestamp 亚毫秒重复 ts、concurrentAppend）。
- **任务产出**: 测试与编译输出记录＋§17.1 三表核对表＋archiveFile git diff 逐行核对清单＋§17.2 代码级核对表。
- **评估标准**:
  - [ ] `testDebugUnitTest --max-workers=1`：既有 200 条＋全部新增 **0 失败 0 错误 0 跳过**（新增逐任务对账：T-12/T-15 各自贡献）
  - [ ] `assembleDebug` BUILD SUCCESSFUL
  - [ ] §17.1 三表逐要点核对：TopicBadgeTest ①~④／TopicRepositoryTest 扩展 ①~③／ArchiveOutcomeMappingTest ①~⑤，每要点存在且绿；UI 组件（TopicCard/SwipeCard/二选一框）无 JVM 单测项（TD-003）明确标注去向＝T-18 真机走查
  - [ ] archiveFile git diff 逐行核对清单完成：写盘段逐字节等价逐项打勾（对照 T-15 前后版本），行为变更项（空内容分支）单列确认与 A-3/T-3 定稿一致
  - [ ] §17.2(a)~(e) 代码级可核项逐项结论（(a) 排列与显隐／(b) 同名分支无指路按钮与手势槽／(c) 菜单结构与回名／(d) 决策映射＋TD-017「Failed 分支先于 clear() 返回」顺序代码评审／(e) 拼接序与去向行字面）
- **评估方法**: 命令输出留档＋三张核对表逐项勾选。
- **评估结果**: ✅ **通过（2026-11-05，编排方执行）**——内存预检 1.98GB≥600MB；`testDebugUnitTest --max-workers=1 --rerun` 实跑 **213/0/0/0**（206 基线＋T-12 六＋T-15 七）；`assembleDebug` BUILD SUCCESSFUL。**§17.1 三表**：TopicBadgeTest ①~④（T-12 Planner 核）／TopicRepositoryTest 扩展（含 N=0 门控 KDoc，断言落 T-15 决策映射）／ArchiveOutcomeMappingTest ①~⑤（T-15 Planner 核）全在案且绿；UI 组件（TopicCard/SwipeCard/二选一框）无 JVM 单测→T-18 真机。**archiveFile git diff 逐行核对**＝T-15 Planner 机械 diff 硬证据（写盘段逐字节等价）＋本门注记：resyncArchiveState 对全 outcome 执行（纯状态重读无副作用，T-15 建议 1 收口）。**§17.2(a)~(e) 代码级**：(a) 排列显隐＝T-14 Planner✓ (b) 同名分支/手势槽＝T-14/T-13 Planner✓ (c) 菜单回名＝T-15/T-16✓ (d) 决策映射＋TD-017 clear 不可达＝T-15 Planner 代码评审✓ (e) 拼接序/去向行＝T-15 Planner✓。注：T-16 Planner 核验并行中，如出必修项则本门复核受影响面。
- **当前轮次**: 1　**最大轮次**: 2　**状态**: ✅ 已完成

### T-18 真机合并验收（94(a)~(f)＋89(c)(d)(i)(m)(p)，用户执行）

- **任务ID**: T-18
- **描述**: 用户在真机（realme）执行 §17.2 94(a)~(f) 全部 6 项＋原 T-10 缺口 89(c)(d)(i)(m)(p) 5 项（见本文 §4 真机验收表）。TD-024：**人工观察为准、日志/SP/文件为事实源**；注入配方照抄 §17.3（勿凭记忆改写）；模式分场与顺序链按 §17.4（94(e) 首次指路**最先**执行——旗标初始态）；备份清单含主题域两条新持久化面。
- **约束**: 执行者＝用户；每项按「构造方法→人工判据→文件/日志证据」三列留档；预检失效时按 §17.3 降级口径标注（「JVM 覆盖＋日志佐证」／「JVM＋代码评审＋logcat 三轨」）；发现缺陷→回流对应编码任务轮次内修复（不自行扩大任务范围）。
- **任务产出**: 真机验收表逐项勾选结果＋证据摘要＋（如有）缺陷清单。
- **评估标准**:
  - [ ] 94(a)~(f) 6 项逐项 PASS（或按 §17.3 降级口径标注并通过）
  - [ ] 89(c)(d)(i)(m)(p) 5 项逐项 PASS（**原 T-10 缺口闭环**）
  - [ ] 全部证据按表留档（run-as cat/ls、`dumpsys alarm` 前后对照＋对象名、SP xml 前后对照、SAF 路径 `adb shell cat`）
  - [ ] 前置备份执行＋验收后按份还原（主文件＋`appendo.xml`＋`files/topics/` 整体＋`appendo_topics.xml`）
  - [ ] 缺陷项（如有）已回流并闭环
- **评估方法**: 验收表逐项核对＋证据复核。
- **评估结果**: ✅ **通过（2026-11-05~06 执行，TD-024 人工判定＋日志/文件双事实源）**——
  **94(a)** ✅ 0 条冷启顶栏主题入口常驻＋排列 [主题][多选][菜单]（R1/R3 重开目验）；
  **94(b)** ✅ 同名提示分支无指路按钮（用户走查）／空主题 t-del 右滑红框删除（文件＋SP 双清实证）／非空右滑零响应／长按复制（用户目验）／首字标与卡片（R1 用户确认）；
  **94(c)** ✅ 分家走查（菜单无「归档」项/归档管理在/归档页仅归档）＋「外观」三态；
  **94(d)** ✅ 只归档（副本 28 条＋主文件 28 不变）＋归档并清空（副本 28＋主恰 `# Appendo` 字节级＋sidecar/闹钟双清＋空态）；**TD-017 降级收口**（realme FUSE chmod 全拒——JVM 决策测试＋T-15 Planner 代码评审双证据，§17.3 降级口径预立）；
  **94(e)** ✅ 去向行/首次指路后缀/二次无后缀（R1 用户目验）；深色走查记录于收尾问卷；
  **94(f)** 89(c)＝**冷启回退实证**（URI 失效真实发生：删主文件→冷启弹「选择存储位置」＋回退默认＋不崩溃，175 条备份字节级还原）＋JVM §10.2 ⑧（mid-session toast 未复现，省略说明）；89(d) ✅ **软中间态 toast 逐字节命中**（「2 条已在该主题中，已从当前列表移除」logcat 实录＋主 173/test 25 幂等）；89(i) ✅ 移入取消（sidecar 0）＋恢复不复活（过时不响）；89(m) ✅ 归一化字节级（R3）＋JVM ⑦（全选 200 不适用——无全选功能 TD-029，以 15 选/189 条全文件体量替代）；89(p) ✅ 15 选移入 189 条体量（「移入中…」目验＋无 ANR log＋完成反馈）；
  **执行备查**：200 条注入首引号 bug 全失→修正后 189/211（22 条系统丢弃，体量足够）＋3 条诊断；**toast 日志仪表**两提交（D 级被 realme 过滤→I 级 `APPENDO_TOAST` 实测可达，`52ef536`/`d6f397b`）；备份 `D:/tmp/appendo-t18-backup/`（含删除前快照 main-before-uri-test.md）。
- **当前轮次**: 1　**最大轮次**: 2　**状态**: ✅ 已完成（2026-11-06 收尾视觉问卷三项确认：同名提示文案正确含「知道了」无指路按钮＝是／深色无不读块＝否（无问题）／恢复注明行在＝是——**T-18 全闭环**）

### T-19 文档收尾（原 T-11 义务全量＋甲新增）

- **任务ID**: T-19
- **描述**: ① `README.md`：**原 T-11 义务**（原设计 §12 处置列 R1~R11 十一行逐条落实——R1 恢复不复活声明/R2/R3/R5/R6/R7 按 R10 改写后口径合并执行/R8 不展开仅核对设计已注明/R4 核对 TD-027 已登记/R9 核对无新增路径；**R10「归档」与「归档与主题」区别说明按 §16.4 ①改写口径执行**——80(b) 就地注）＋**甲新增五项**（§16.4：ⓐ 80(b) 区别说明改写——网格「归档」两选项（归档并清空／只归档保留列表）与「归档管理」的区别；ⓑ「外观」更名（48 注，可感知显式变更）；ⓒ 主题页与顶栏入口交互说明（90，含 80(a) 滑动手势差异注补主题页）；ⓓ 菜单「归档」项移除（93）；ⓔ 85(e)「复制全部」取舍注维持）；② `CHANGELOG.md`：v1.4.0 单条目覆盖两批（移入主题主特性＋甲三条可感知变更：「主题」→「外观」更名／第四格「清空」→「归档」二选一／主题独立页）；③ `docs/architecture.md`：原 T-11 五处（§2 模块地图新文件/§3.5 主题文档协议与 topics/ 目录/§3.6 SP 清单增 `appendo_topics`/§3.3 出口表增主题长按复制全文行/§6.3 新写盘点＋§11 清单核对）＋甲新增（§2 补 SwipeCard/TopicListScreen/TopicBadge 三新文件与 ArchiveOutcome/路由图增 topics/consumeFirstMoveGuide/§3.6 补 `first_move_guide_shown` 键/归档清空合并新出口与决策映射）——**数据协议与模块图强制同步修订**（CLAUDE.md 任务完成检查）；④ `docs/plans/debt-tracker.md`：**原批次 12 条遗留集中登记**（T-01 count() 表达式收敛/T-02 S1 组合用例/T-03 ⑦ 区分度变体/T-03 §6.4 行号漂移 :143→:161 顺手更正/T-04 nextTimestamp flake＋修法方向/T-06 concurrentAppend flake 补列/T-07 ui·CLAUDE.md:25（按甲终名「归档管理」核对，可能自解）/T-07 空主题详情「此归档为空」/T-07 空源 toast「归档为空」名词偏/T-07 legacy「已删除·复制失败」内联家族/T-08 :108 内联收敛（E-2 后重估）/T-09 test 目录 CLAUDE.md 滞后）＋甲批次新债（如有）；TD-027~029 已登记不重登；⑤ `Appendo/src/main/java/.../ui/CLAUDE.md:25` 陈旧清单核对清理；⑥ `Appendo/build.gradle.kts` 版本升级 versionCode 11/versionName "1.4.0"（Q1 裁定沿用，两批合并一次升位）。
- **约束**: 文件边界：README.md、CHANGELOG.md、docs/architecture.md、docs/plans/debt-tracker.md、ui/CLAUDE.md、Appendo/build.gradle.kts（版本两行）；architecture.md 追加/更新对应小节不删历史；specs/design 已定稿不再改动；真机验收产生的字面微调（如有）一并同步。
- **任务产出**: 五份文档修订＋版本号升级＋遗留登记。
- **评估标准**:
  - [ ] README：原 §12 处置列 R1~R11 十一行逐条落实（R10 按 §16.4 ①改写）＋甲新增五项齐备可指认
  - [ ] CHANGELOG 含 v1.4.0 条目：三条可感知变更＋移入主题主特性，版本号与 Q1 裁定一致
  - [ ] architecture.md 原五处＋甲新增齐备（模块图含 3 新文件＋ArchiveOutcome＋topics 路由＋consumeFirstMoveGuide＋`first_move_guide_shown` 键＋归档出口）
  - [ ] `grep -n "versionCode\|versionName" Appendo/build.gradle.kts` 显示 versionCode = 11 / versionName = "1.4.0"
  - [ ] debt-tracker：原 12 条遗留逐条登记或按甲终态标注处置（如 ui/CLAUDE.md:25 回名后核对结果）；甲新债（如有）补登
  - [ ] `grep -n "归档管理\|归档与主题" Appendo/src/main/java/com/yiyue31/android/appendo/ui/CLAUDE.md` 与甲终名一致（陈旧清单已清理）
- **评估方法**: 文档逐节对照＋grep。
- **评估结果**: ✅ **通过（2026-11-06）**——Evaluator 七条全 PASS 零必修（对象＝工作区 git diff 全量＋源码实况对照）：①R1~R11 逐条语义忠实＋甲五项齐（R10 区别说明 :78-79）；②CHANGELOG v1.4.0 与 Q1 裁定一致、字面与 Microcopy 10 处逐字全中；③architecture 原五处＋甲新增全落且与源码逐点实证（ArchiveOutcome/archiveActionFor/topics 路由/SP 三键/新写盘点），不删历史；④版本两行恰改（grep 实证 11·"1.4.0"）；⑤debt-tracker TD-030~047 逐条有源（行号全实证，TD-036 自解关闭成立）；⑥ui·CLAUDE.md「归档与主题」零命中、菜单六项与 MainScreen 字面一致；⑦边界恰 6 文件无越界、无 gradle 痕迹。偏离 a~d：a 部分属实（补 12 文件仍缺 5）／b 属实／c 内容真实／d 与裁定一致。**建议 4 条非阻断**（1 结构树缺 5 文件／2 版本历史缺 1.3.0·1.3.1 行／3 CHANGELOG 1.3.1 进度注过时／4 +71 口径确认无需动作）→ 建议 1~3 以收尾补丁处理。
- **当前轮次**: 1　**最大轮次**: 2　**状态**: ✅ 已完成（2026-11-06 轮次 1 一次通过；建议 1~2 补丁落盘、建议 3 核实微文案批次确未完成而不改；交付与计划记录随本提交落盘，甲批次 T-12~T-19 全闭环）

---

## 3. Evaluator 检查单（计划级——评估本计划是否完备覆盖定稿设计 §14~§18）

1. **§14.1 新增 5 文件**各有任务归属：TopicListScreen→T-14、SwipeCard→T-13、TopicBadge→T-12、TopicBadgeTest→T-12、ArchiveOutcomeMappingTest→T-15（表逐行核对）
2. **§14.1 修改文件**逐行有任务指派：MainScreen→T-13（振动引用）/T-14（顶栏＋参数）/T-15（①~⑦五点）；ArchiveListScreen→T-13（SwipeCard 消费）/T-14（1 行编译跟随）/T-16（分家）；MainActivity→T-14（topics 路由＋接线）/T-16（archive_list 签名）；Microcopy→T-14/T-15 增量落盘＋T-16 收口退役；README/CHANGELOG→T-19；TopicMoveDialogs→T-16（:108）；TopicRepository→T-12；util/reminder 零改动＝全任务边界红线
3. **§14.2 路由图**→T-14（topics 路由＋顶栏入口）＋T-15（菜单归档项移除/回名）＋T-16（archive_detail kind 缺省消费）；§14.3 数据层口径→T-12 约束（仅旗标方法，协议零改动）
4. **§15.1 主题页七行表**→T-14 评估标准逐行可指认（含 C5 同名分支/P3-13 多选显隐/E-4 重载走 IO）；§15.2 分家→T-16；§15.3 两路径表→T-15（含 C6 形态/A-2 onComplete 映射/A-3 空内容行为变更/E-1 runCatching/TD-017）；§15.4 指路→T-15（U1 拼接序/A-1 改道 consumeFirstMoveGuide/外观更名）
5. **§16.1 新增 19 行**→T-14 落 6/T-15 落 12/T-16 落 1（ARCHIVE_EMPTY_*）＋T-16 全量核对；**§16.2 修改 3 项**→T-14 1（topicCardSubtitle）/T-15 2（dlgMoveConfirmBody/ThemePickerDialog 标题）；**§16.3 退役 6 行**→T-15（BTN_CLEAR＋直写族）/T-16（SECTION_*/ARCHIVE_TOPIC_EMPTY_*）＋grep 防线；**§16.4**→T-19 五项义务＋T-16 句式族核对
6. **§17.1 三表测试义务**→T-12 两表/T-15 一表＋T-17 完备性核对；M1 可测边界四保障＋git diff 清单→T-15 约束＋T-17 评估标准
7. **§17.2 94(a)~(f)**→§4.2 行动清单逐项提取；**§17.3 配方**→§4.1 照抄（chmod/probe/会话中失效/TD-017＋两降级口径）；**§17.4 顺序链与备份**→T-18 约束＋§4.3/§4.4
8. **§18 R12~R18**→§5 注意事项逐条落点（R12→T-15/T-17、R13/R14→已知边界、R15→设计消除＋T-15、R16→T-13/T-18、R17→T-12 测试钉死、R18→T-19 文档）
9. **specs 16.A 逐条自检**：90~94＋需求 12/13/16/17/48/80(b) 就地注逐条可在任务评估标准中找到落点（对照设计章尾条文落点表）；数据层零改动口径与 §14.3 一致
10. **原计划衔接**：T-10 缺口 5 项→T-18（§4.3 行照抄＋§17.2(f) 证据替换）；T-11 义务全量→T-19（含 12 条遗留清单逐条）；版本 v1.4.0 一次升位
11. **计划质量**：每任务粒度≤半天；评估标准全部可验证（文件存在/测试绿/grep 命中/字面逐字），无「适当/合理」类模糊词；依赖无环、串行可行；每编码任务自带单测且 `--max-workers=1`；文件边界硬约束齐备

---

## 4. 真机验收表（T-18 用，TD-024：人工为准、日志/SP/文件为事实源）

### 4.1 前置与故障注入配方（§17.3 照抄，勿凭记忆改写）

**前置**：真机 realme；`adb` 可用（platform-tools 已补装，2026-11-03 记录）；外部存储路径一律绝对路径 `/storage/emulated/0/Android/data/com.yiyue31.android.appendo/files/…`。**模式分场**见 §4.4。

**89(c) 目标写入失败**——chmod 配方原样沿用（注入时点＝选定/新建目标后、确认「移入」前；主题页新建入口出现后目标可能预建，时点判定不变）：

```
adb shell run-as com.yiyue31.android.appendo chmod 500 /storage/emulated/0/Android/data/com.yiyue31.android.appendo/files/topics
adb shell run-as com.yiyue31.android.appendo touch /storage/emulated/0/Android/data/com.yiyue31.android.appendo/files/topics/probe
# 预检：probe 应 Permission denied；确认「移入」→ 断言 toast「移入失败（写入主题文档失败）」、对话框保持变「重试」、
# run-as cat 源文件逐字节原样；chmod 700 恢复后重试成功
```

**89(c) URI 失效场景**（T-1 修订：会话中失效构造，弃覆盖安装清授权法）——SAF 主文件、app 前台运行中删除底层文件：

```
adb shell rm /sdcard/<自定义目录>/Appendo.md
# probe（配方可达性前取证）：先验证 SafMarkdownFile.readAllWithStatus 对已删文档返回 failed——
#   logcat 断言失败分类 SourceReadFailed（事务源读失败通道，与 §10.2 ⑧ 同语义）
# → 立即多选移入 → 断言：失败反馈（静默回退/失效提示对话框，以实际 UI 为准）；
#   主列表条目不变（UI 判据）＋目标文件前后一致（干净失败零副作用——topics/ 私有目录 run-as cat 可达）
```

**降级口径（T-1）**：会话中失效法在个别 ROM 不触发（DocumentsProvider 缓存已删文件）时，降级取证＝JVM §10.2 ⑧＋真机以静默回退/失效对话框为取证对象（不硬拧 toast 字面）。

**89(d) 软中间态**——run-as 手工追加配方原样沿用：不执行移入、直接向 `topics/<T>.md` 追加该批条目块 → App 内移入同批 → N=0 直陈句式收敛；等效 In-App 构造＝89(o) 同款连做；`am crash` 仍为加分尝试。

**TD-017 顺序证明（94(d)）**——归档失败而清空「本可成功」的构造：

```
# 前提：主文件＝SAF 自定义目录（清空走 SAF，不受私有目录 chmod 影响）
adb shell run-as com.yiyue31.android.appendo chmod 500 /storage/emulated/0/Android/data/com.yiyue31.android.appendo/files
adb shell run-as com.yiyue31.android.appendo touch /storage/emulated/0/Android/data/com.yiyue31.android.appendo/files/probe
# 预检（T-2）：probe 应 Permission denied——chmod 生效的先行取证（个别 ROM run-as chmod 静默失效的门闸）
# 归档副本创建于该目录（files/ 根）→ writeAll 失败；网格「归档」→ 归档并清空 →
# 断言：toast「归档失败，未清空」；SAF 主文件内容不变（若未先备份即清空，此处已空）
adb shell run-as com.yiyue31.android.appendo chmod 700 /storage/emulated/0/Android/data/com.yiyue31.android.appendo/files
```

**降级口径（T-2）**：probe 未 Permission denied 时，顺序证明降级三轨——① JVM 决策测试（§17.1 M1：`Failed` → 不 clear）；② 代码评审取证「`Failed` 分支先于 `clear()` 返回」；③ logcat 归档失败日志（`Archive failed` 异常栈）为真机第二证据。

**不加生产钩子**（§10.1 裁定重申）：DEBUG 钩子/注入参数入口不新增；JVM 层既有 `MoveFaults` 注入即 89(c)(d) 单测主证据，真机受控复现为第二轨。

### 4.2 94(a)~(f) 行动清单（构造 / 人工判据 / 证据）

| 项 | 构造方法 | 人工判据（通过条件） | 文件/日志证据 |
|---|---|---|---|
| (a) 顶栏入口 | 主文件清空至 0 条冷启；再造 3 条进多选 | 0 条时主题图标在、进入正常；多选态无主题图标、退出恢复；排列 `[主题][多选][菜单]` | 真机人工（TD-024 截图不可靠）；`run-as cat` 主文件验 0 条态 |
| (b) 主题页 | 建主题 2 个（其一空）＋重启＋尝试同名/非法命名/长按复制；空主题右滑删、非空右滑、左滑恢复 | 首字标同名同色且重启不变、过 52 对比度；「N 条 · 创建时间」与详情一致；空主题「空」；页级空态含引导；新建校验链全分支 toast 正确；**同名分支＝提示无「移入该主题」按钮**、取消后输入保留；长按复制全文格式干净（同 89(f)：无 ##/---/# Appendo/零宽字符）；空主题右滑→红框删除确认（文件＋SP key 同清）；非空右滑钳 0 零响应；左滑恢复对话框含「仍保留」注明行＋去重 toast 计数正确 | JVM（§17.1 TopicBadgeTest 已钉死对比度/稳定性）＋真机走查＋粘贴字符级检查（零宽检查器）；`run-as ls files/topics/`＋`run-as cat shared_prefs/appendo_topics.xml`（created_ 键前后对照） |
| (c) 分家 | 造 1 主题＋1 整文件归档；打开菜单逐项走查 | 归档页仅归档文件、无分区标题；菜单无「归档」项、「归档管理」在且跳转正确；「外观」＝原「主题」三态（切换＋重启保持）；归档卡右删/左恢复仍可用（SwipeCard 重构回归） | 真机＋既有单测全绿门（T-17）；`run-as ls files/`（归档）与 `files/topics/`（主题）与两页列表一一对应 |
| (d) X 两路径 | 造 3 条＋1 条设 2 分钟提醒 → 网格「归档」分别走两路径；TD-017 配方见 §4.1 | **归档并清空**：新 `Appendo_*.md` 生成＋主文件恰 `# Appendo\n`＋计数 0＋空态（需求 20）＋提醒全消（徽标/闹钟/sidecar）；**只归档**：副本生成＋主列表/计数/提醒不变；**TD-017**：归档失败路径主文件逐字节不变＋toast「归档失败，未清空」 | 真机＋文件证据（`run-as ls/cat`、`dumpsys alarm` 前后对照＋**对象名**——本应用 PendingIntent 与 sidecar `reminder_<ts>` key 逐一消失，非仅看徽标；sidecar xml） |
| (e) 92 文案 | 打开移入确认对话框断字面；首次移入＋二次移入；深色逐屏走查主题页/二选一框/「外观」处 | 去向行 specs 逐字「移入后这些条目将从主列表消失，可在顶栏『主题』入口查看。」；首次成功 toast 含指路后缀、二次起无；深色无不可读文字/浅色残块（89(h) 口径） | 真机＋字面逐字节核对（T-08 手法）；旗标初始态构造见 §4.4 顺序链第 1 条 |
| (f) 89 缺口收口 | 见 §4.1（89(c)(d) 配方）＋§4.3（89(i)(m)(p) 行） | 89(c)(d)(i)(m)(p) 5 项逐项通过（或按降级口径标注） | JVM 既有用例＋真机受控复现双轨；**不新增生产注入钩子**；SAF 主文件证据用 `adb shell cat <SAF 路径>`（run-as 不可达 SAF 文档）、默认场用 `run-as cat` |

### 4.3 89(c)(d)(i)(m)(p) 行动清单（原 T-10 缺口，沿原计划 §4.2 对应行＋§17.2(f) 证据替换口径）

| 项 | 构造方法 | 人工判据（通过条件） | 文件/日志证据 |
|---|---|---|---|
| (c) | §4.1 chmod 配方（选/建目标后、确认前注入＋probe 预检） | toast「移入失败（写入主题文档失败）」、对话框保持变「重试」；`chmod 700` 恢复后重试成功 | 注入前后主文件逐字节一致（SAF 场 `adb shell cat`／默认场 `run-as cat`）；重试后目标内容正确；（预检失效时）logcat 失败日志＋JVM `MoveFaults` 用例佐证 |
| (d) | §4.1 run-as 手工向主题追加同批（主文件不动）→ App 内重试同批 | 反馈 N=0 直陈句式「M 条已在该主题中，已从当前列表移除」；主列表该批清空 | 重试前后主题条数不变（全 skipped）＋主文件无该批 |
| (i) | 2 条各设 +2 分钟未触发提醒 → 移入；恢复主题后等过原触发时刻 | toast 含「，已取消 2 条提醒」；徽标即刻消失；恢复后提醒不复活 | `dumpsys alarm` 前后对照（PendingIntent 消失）＋`run-as cat shared_prefs/appendo_reminders.xml`（`reminder_<ts>` 消失） |
| (m) | 全部条目多选（可配 (p) 200 条批）→ 移入 | 空状态引导文案＋计数归零；重开应用仍空 | `run-as cat` 主文件输出**恰为 `# Appendo\n`**（归一化断言；与 94(a) 0 条冷启连做见 §4.4） |
| (p) | `for i in $(seq 1 200); do adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "批量条目 $i" -n com.yiyue31.android.appendo/.ShareReceiverActivity; done`（间隔片刻防丢）→ 全选移入 | 「移入中…」可见、完成 toast「已移入 200 条」、无冻结/无 ANR 对话框 | `adb logcat -d \| grep -i "ANR in com.yiyue31.android.appendo"` 无记录；目标恰 200 条 |

### 4.4 模式分场、顺序链与备份清单（§17.4 照抄）

**模式分场**（两场互斥，按用例切换）：

| 场 | 主文件模式 | 服务用例 | 备注 |
|---|---|---|---|
| SAF 场 | 自定义目录（`use_saf=true`，沿 T-10 真机先例；本机现状即此场） | §4.1 TD-017（前提＝清空走 SAF 不受私有目录 chmod 影响）、URI 失效配方（前提＝SAF 主文件） | 89(i)(m)(p) 若在本场执行，证据命令按 §4.2(f) 行替换 |
| 默认场 | 私有目录 `Appendo.md` | 94(a)~(d) 及 89 常规项 | `run-as` 证据全量可达，文件取证最简 |

**顺序链**（状态前置依赖，乱序会破坏断言）：

1. **94(e) 首次指路须排在任何移入用例之前**——须单独造 `first_move_guide_shown=false` 初始态（全新安装/清数据天然满足；若顺序不可保：默认场 `run-as` 编辑 `appendo_topics.xml` 删该键、SAF 场删 SP 文件后重启重置）。否则先行移入用例置位旗标，指路后缀断言失效。
2. **94(a) 0 条冷启与 89(m) 全移入连做**：先清主文件至 0 条冷启验入口常驻 → 造条目 → 全选移入 → 主文件恰 `# Appendo\n`＋空态（89(m) 断言），一场两用。
3. 89(i)(m)(p) 与 94 各项无状态耦合，可插空执行（沿原计划 §4.2 行序）。

**前置备份清单**（沿 T-10 先例补齐；验收后按份还原）：主文件（SAF 路径 `adb shell cat` 拉取／默认场 `run-as cat`）＋既有 SP（`appendo.xml`：use_saf/file_uri/theme_mode 等）＋**`files/topics/` 目录整体（逐文件拉取）＋`appendo_topics.xml`（created_* 键与指路旗标）**——主题域两条新持久化面首次纳入验收基线。

---

## 5. 风险与执行注意事项

1. **R12 MainScreen 回归面（本批三任务触碰：T-13/T-14/T-15；计数口径与设计 R12 对齐——原批四度＋本批三度）**：本批五点联动（顶栏/网格/菜单/清空路径/移入 toast）——缓解＝`archiveFile` writeAll 写盘段逐字节等价（T-15）＋git diff 逐行核对清单（T-17）＋既有单测全绿门＋94(c)(d)(e) 真机清单（T-18）；网格其余三格与输入/编辑/提醒路径零触碰。
2. **R16 SwipeCard 抽取重构回归**：两页卡片＋禁删钳制分支——94(c) 真机抽查归档右删/左恢复＋94(b) 主题空删/非空钳 0；T-13 机械重构不改阈值/指示色语义（diff 清单留档）。
3. **R17 首字标色板与「零写死色」张力＋模式切换整体跳带**：派生逻辑属主题基础设施（受控例外）；「同名同色」在同主题模式内成立，跨模式整体换带有意为之——§17.1 测试钉死 4.583 下界，跳变可接受性由 94(e) 深色走查覆盖。
4. **R13/R14 BreakIterator 老 ICU 与生僻字 tofu**：API 26/27 ZWJ 序列可能拆分、部分 ROM 缺字——观感降级不作缺陷上报；JVM 测试断言 host JDK 行为，老 ROM 降级可接受（已知边界）。
5. **R15 toast 时序**：归并后每路径单条；首次指路为后缀拼接而非第二条；0 条守卫消除旧双 toast 冗余——残留双 toast 仅存于守卫后 entryCount 陈旧 ≤2s 轮询窗口，自愈。
6. **R18 「外观」更名＋菜单「归档」项移除的习惯迁移**：可感知显式变更，README/CHANGELOG 注明（T-19）；数据与行为语义零变化。
7. **TD-003 Robolectric 不可用**：新增测试全纯 JVM（TopicBadge 纯函数/假 SP/决策映射纯函数——沿 MultiSelectTest·TopicMoveDialogsTest·Q2 先例）；UI 组件走真机走查（T-18）。
8. **TD-024 真机自动化不可靠**：T-18 人工为准、日志/SP/文件为事实源；`am crash` 仅加分尝试；权限注入若在 JVM 层需要，沿 T-02 Windows 等价替换先例（mkdirs 占位/文件级 setWritable，chmod 语义在 NTFS 不可用）。
9. **交叉文件**：`MainScreen.kt` 三任务触碰（T-13 振动引用/T-14 顶栏/T-15 五点）、`Microcopy.kt` 三任务触碰（T-14/T-15 增量/T-16 收口）、`MainActivity.kt` 两任务触碰（T-14 topics/T-16 签名）——严格按序、每任务收尾全量单测；发现前驱任务缺陷记录交 Planner 判定，不越任务边界修。
10. **Microcopy 增量落盘规则**（沿原计划 T-05~T-08 先例）：UI 任务当场落本任务消费常量（CODINGRULES 禁直写），T-16 做全集核对＋退役＋grep 防线收口——避免「先直写后替换」返工。
11. **内存约束**：编译/全量测试前 `free -m` 预检（available <600MB 停止，T-09 先例）；全测固定 `--max-workers=1` 且不与其他重任务叠加；测试任务禁止并行调度 subagent。
12. **并行流水线口径**：仅「Planner 只读核验 × 文件不相交的下一 Generator」可并行（T-12/T-13 为首对）；gradle 项目锁自然排队；编码 Generator 任何时刻仅一个。

---

## 6. 开放问题与拆分决策记录

**开放问题：预计无**——设计修订二已定稿（2026-11-04 用户确认），specs 16.A 同日定稿；若执行中发现设计未覆盖的小决策，按设计 §14.1 改动面表落位并在任务产出中注明，不新开裁定。

**拆分决策记录**（依 §14.1 表落位，非新裁定）：

| # | 决策 | 依据 |
|---|---|---|
| D1 | `topicCardSubtitle` 签名/序改（§16.2）落 T-14（消费方任务）；`ArchiveListScreen` 旧调用行 1 行编译机械跟随（该行随 T-16 分家消亡） | §16.2「唯一消费方＝新 TopicCard」＋Microcopy 落盘规则（消费任务当场落） |
| D2 | MainScreen 改动切分线：T-13 仅 performVibration 引用、T-14 仅顶栏 actions 区＋`onNavigateToTopics` 参数、T-15 其余五点（网格/对话框/archiveFile/菜单/指路） | §14.1 MainScreen 行 ①~⑦ 逐项归位；共改文件串行约束 |
| D3 | Microcopy 增量落盘：T-14 落 6 项＋T-15 落 12 项＋T-16 落 `ARCHIVE_EMPTY_*`（消费方＝回退后归档页空态）；`BTN_CLEAR` 随 T-15 两消费点消亡一并删除；T-16 全量收口 | 原计划 T-05~T-08 先例＋§16.1/§16.3 消费方归属 |
| D4 | 版本号 v1.4.0/versionCode 11 沿用原 Q1 裁定，T-19 一次升位、CHANGELOG 单条目覆盖两批（1.3.1 未发布，无二次升位） | 原计划 §6 Q1＋`build.gradle.kts` 现状（1.3.1/10，原 T-11 未执行） |

（当前无未裁定开放问题。）

---

## 7. 进度记录

- 2026-11-04：计划创建（Generator 草案，8 任务 T-12~T-19，待计划级 Evaluator 评估＋用户确认后进入阶段4 编码）。
- 2026-11-04：Evaluator 第 1 轮评估（11 项 10 PASS/1 FAIL，必修 2＋后置 4）→ 修订全落（P-1/P-2/C-1~C-4）。状态：修订版（待用户确认）。
- 2026-11-04：**用户确认计划定稿**（三角色计划级评审经评估不需要——计划为已三评审设计的机械拆解，Evaluator 已覆盖计划级核验面；沿原批次先例）。阶段4 编码启动：T-12 开工。
- 2026-11-04：**T-12 完成**（提交 `bb2786f`，Planner 核验 6 条全 PASS，206/0/0/0 `--rerun` 实测）：TopicBadge 四函数＋consumeFirstMoveGuide 落盘；流水线首对启动（T-12 Planner 与 T-13 Generator 并行）。T-13 已提交 `91fdaaa` 待核验，T-14 进行中。
- 2026-11-04：**T-13 完成**（提交 `91fdaaa`，Planner 核验 6 条全 PASS——六件套 token 级逐字一致硬证据，零必修；1 建议＝EntryListScreen 死包装/常量重复→T-19 登记）。**T-14 完成**（提交 `e84aea9`，206 零修改全绿待核验；偏离 2＝AutoMirrored.List 图标（弃用警告规避）/448 行 KDoc 密度）。T-14 Planner 与 T-15 Generator 并行中。
- 2026-11-04：**T-14 核验闭环**（Planner 7 条全 PASS 零必修；两偏离接受；建议 2＝注释措辞 T-15 顺手修＋indexOf 优化非阻断）。T-15 Generator 进行中。
- 2026-11-04~05：**T-15 完成**（提交 `749bddd`）——Generator 撞 API 限流中断，**编排方接管补全**（MainScreen 七点＋决策测试）；T-15/T-16 双 agent 同限流窗口中断后于 11-05 续跑。**T-15 核验闭环**（Planner 从紧 7 条全 PASS 零必修；写盘段逐字节等价硬证据；2 建议归 T-17 注记/T-19 校准）。T-16 Generator 续跑中。
- 2026-11-05：**T-16 完成**（提交 `1a64999`：分家回退＋:108 换序＋Microcopy 收口；213 零修改全绿、ArchiveRestoreDeduplicationTest 9/9 等价性铁门）→ Planner 核验并行。**T-17 质量门通过**（编排方执行：--rerun 实跑 213/0/0/0＋assembleDebug 成功＋三表/逐行核对/diff 清单汇编各任务 Planner 硬证据）。**编码链闭环（T-12~T-17）→ 待 T-16 Planner 收尾后进 T-18 真机合并验收（用户执行）**。
- 2026-11-05：**T-16 核验闭环**（Planner 6 条全 PASS 零必修；19/19 逐字；3 建议归 T-19 含 ArchiveCard 死参登债）。**编码链 T-12~T-17 全闭环（六任务全部一次通过零返工）**。T-18 真机合并验收启动：装包＋备份（含 topics/ 与 appendo_topics.xml）→ 引导式逐项执行。
- 2026-11-05：验收中用户连续反馈（回红/清理不归档不采纳/移回刚需/提醒解耦/主题工作区化）→ **决策全景三视角评审（产品/架构/用户，收敛 C1~C7）→ 用户裁定**：戊＝主题工作区化立项（L1 提醒解耦＋移出/L2 编辑＋删除/L3 新增缓/乙排后）；**89(i) 照测**；**版本节奏 A（v1.4.0＝甲，戊＝v1.5.0）**；网格「归档」格回红已实现装机（`1a62d8d`）。T-18 继续全项验收。
- 2026-11-06：**T-18 真机合并验收完成**（全部受控项通过，详见 T-18 评估结果）：执行链＝R1 指路与卡片→R2 只归档→R3 归档并清空＋提醒全消＋0 条冷启→R4 提醒联动两半段→执行卡 7 步（189 条体量 15 选移入/同名提示/空主题删/非空钳 0/长按复制/深色/菜单）→URI 失效（真实发生＋冷启回退实证＋175 条还原）→89(d) 软中间态 toast 逐字节命中。**流程创新备查**：toast 日志仪表（APPENDO_TOAST）使文案断言日志化；chmod 注入在 realme FUSE 不可行（TD-017/89(c) 目标写按降级口径收口）；89(p)/(m) 因无全选功能以体量替代法收口。收尾视觉问卷 3 项待确认后 T-18 全闭环 → T-19。
- 2026-11-06：**T-19 启动**（用户口径：只编码不测试，验证换机进行；评估保留为文档对照＋grep）。**Generator 第 1 轮返回**：①~⑥ 六项产出齐（README 195→219 行 R1~R11＋五项落位／CHANGELOG v1.4.0 单条目字面对齐 Microcopy／architecture 495→512 行原五处＋甲新增／debt-tracker TD-030~047 共 18 条（12 原批次＋6 甲新债，TD-036 标已自解关闭）／ui·CLAUDE.md 导航重写＋同文件清理／版本两行 11·"1.4.0"），自验 grep 4 条全过、边界 6 文件无越界、未跑 gradle 未 commit。**疑问 4 项 Planner 裁定**：主包 CLAUDE.md 路由陈旧→维持 TD-047 不动；TD-044 漂移按实测 :185 登记→维持；architecture §12 单元回归行陈旧（133→213）→裁 Generator 补刷新；CHANGELOG +71（142→213 基线）→维持。**偏离 3 项受理**（README 结构树全量刷新／ui·CLAUDE.md 点名段外同源清理／architecture §4.2·§5.4·§6.1 现状同步）→ 交 Evaluator 一并核。Generator 补丁完成（§12 单元回归行就地刷新：213 用例/2026-11-06/三列格式不动），产出齐备 → **派 Evaluator（文档对照＋grep，不跑测试）**。
- 2026-11-06：**T-19 核验闭环**（Evaluator 七条全 PASS 零必修，轮次 1 一次通过；R1~R11 语义核/Microcopy 10 处逐字核/architecture 源码逐点实证/边界红线核全过）。建议 1~3（README 结构树补 5 文件／版本历史补 1.3.0·1.3.1 行／CHANGELOG 1.3.1 进度注收口）以 Generator 收尾补丁处理中 → 补丁返回后 Planner 统一提交。**甲批次 T-12~T-19 至此八任务全部闭环。**
- 2026-11-06：**T-19 收尾补丁闭环＋提交**：建议 1~2 落盘（README 结构树补 5 文件 :131/:141/:142/:146/:149；版本历史补 1.3.1/1.3.0 两行 :205-206）；建议 3 经核实**确属未完成**（v1.3.x 微文案批次真机走查 18 行仅部分执行、计划仍在 active/、§3 收尾动作均未发生）→ 该行不动，处置正确——**该批次为遗留未完事项，已向用户通报**。Planner 收尾核验过（7 文件恰集/版本两行/历史两行/5 文件 grep 全中）→ 交付与计划记录单 commit 提交。**甲批次八任务全闭环，v1.4.0 待用户换机验证后发布；计划归档 completed/ 待发布确认后执行。**
- 2026-11-11：**换机验证（Windows 机）走查反馈处理**：五重点路径 2/3/5 通过（外观更名/归档二选一/长按复制全文）；4（移入与提醒联动）补验说明后待复测；1（主题独立页）反馈三则——①卡片阴影观感缺陷（首卡 8dp 浮起＋2dp 投影在 12dp 列表间距下上下被邻卡遮蔽、仅左右可见，且 SwipeCard/EntryCard 两组件渲染路径不同致主题页与详情页风格不统一）→ **用户授权参考 Apple 设计语言裁定平面化**：两组件 elevation 恒 0、`index` 参数移除、`background` 底色分层（浅 #F2F2F7/深 #000000）、四列表页顶栏随底色、colors.xml/themes.xml 状态栏与冷启动窗口同步、顺手清 `archiveToDeleteIndex` 死状态；213 测试全绿＋装机。②主题详情条目不可编辑＝设计如此（戊批次 L2，TD-028）。③主题重命名本期不做（TD-027；非空主题当前无法改名也无法删，待戊批次）。设计文档修订记录 2026-11-11 行追加；CHANGELOG 1.4.0 可感知变更三条→四条。**待用户复走平面化观感＋补验 4 → 发布**。
- 2026-11-11：**换机验证视觉修订二轮（用户复走反馈）并装机**：①条目间距 12→8dp（三 LazyColumn）；②卡/底色差加大——浅 surface 显式覆盖纯白 #FFFFFF（弃 #FFFBFE 微紫）＋background 加深 #E8E8ED；深 surface 提亮 #26252B。surface 覆盖联动 MultiSelect 顶栏/外观预览；TopicBadge 派生签名随新 surface 自适应（对比度数学保证不变）。213/0/0/0 全绿＋装机。设计修订记录/CHANGELOG/architecture/theme·ui CLAUDE.md 色值同步。**另：用户提出「主题列表与详情页功能与主页一致」——即戊批次范围问题，「一致到什么程度」（是否推翻 L3 新增缓裁定）留 specs 16.B 起草时确认。待用户复走二轮观感＋补验 4 → 发布**。
- 2026-11-11：**换机验证视觉修订三轮并装机**：用户反馈「区别度不错，但灰底脏兮兮」→ 浅底 `#E8E8ED` 中性灰改**蓝调 `#E8EDF4`**（同明度对比度、贴品牌蓝 #2196F3，观感清透；仅动 lightBackground 一值＋colors.xml 同步，深色不动）。213/0/0/0 全绿＋装机。文档色值同步（theme·architecture·CHANGELOG）。**待用户复走三轮观感＋补验 4 → 发布**。
- 2026-11-11：**换机验证第 4 轮（话术与体验）并装机**：用户裁定颜色现状定稿（蓝调底）；功能 4（提醒联动）验通但话术两处不友好＋主题详情名字太小＋toast 不显眼——①移入词族贴用户模板「移入主题x条，提醒失效y条」（4 函数字面改，N/M/K 口径不变）；②主题详情标题「xxx主题详情」（sourceFileName 参数退役，EntryListScreen 同步清理）；③Toast 反色药丸（ThemePreferences＋系统夜间同 Compose 解析）。213/0/0/0 全绿＋装机；CHANGELOG/ui·CLAUDE.md/设计修订记录同步。**颜色四轮全部定稿。待用户复看话术/标题/toast → 发布**。
- 2026-11-11：**换机验证第 5 轮（话术精炼总原则）并装机**：用户裁定「详情」二字冗余＋总原则「所有话术精炼且准确表意」——①主题详情标题＝主题名本身；②确认正文两句并一句（去向括注保留）；③N=0 句精炼。specs 16.A 追加修订注（92/87/90 字面口径）。213/0/0/0 全绿＋装机（14:37 实测新鲜结果）。**待用户复看 → v1.3.x 裁定 → 发布**。
- 2026-11-11：**换机验证第 6 轮（弹窗精炼，用户建议经分析采纳）并装机**：纯信息单按钮弹窗去按钮——「关于」/主题页同名提示两处 `confirmButton = {}`（点外/系统返回双通道，回调同路径）；specs 修订注补 ④（C5 三通道→两通道）；预览框「复制/关闭」等多按钮框不动（关/取消与真动作并列有平衡作用，进一步精简留 16.B）。213 全绿＋装机。
- 2026-11-11：**换机验证第 7 轮（输入框草稿保全）并装机**：用户反馈「新建/修改点外不关」→ 核实点外关闭本就启用（键盘首 tap 被输入法吃＝Android 常态）；真缺陷＝dismiss/取消四处清空输入（误触点外丢整段）→ 关窗不丢字（草稿保留、保存才清空，沿 C5 先例）；specs 修订注 ⑤。213 全绿＋装机。
- 2026-11-11：**换机验证第 8 轮（草稿保全补全）**：用户实测「新建主题/编辑条目未生效」→ 第 7 轮只堵关窗清空、漏打开重置同族三处（主题页＋清输入/closeMoveFlow 清输入/编辑打开重置原文）→ 修复＋`lastEditedTs` 草稿归属标记（同条目沿用、换条目重置、保存清）。213 全绿；装机因设备断开暂候，重连即装。
- 2026-11-11：**换机验证复看通过（用户确认）**：第 8 轮草稿保全三处（编辑同条目沿用/换条目重置、主题页＋命名保留、移入流程命名保留）全过。**换机验证功能面全部收口（八轮：视觉×3＋话术×2＋弹窗×1＋草稿保全×2）**。剩余发布前事项：v1.3.x 走查裁定（覆盖/豁免）＋ commit。
- 2026-11-11：**v1.3.x 微文案批次豁免结案（用户裁定「1+2」）**：代码级核对替代（11 字面全中、isBlank 守卫 7 处、恢复框蓝、T-18 SAF 回退证据覆盖、第 11 行场景随 v1.4 清空退役作废、「已复制」直写已自解）＋TD-025 标已解决（草稿保全优于确认框）；CHANGELOG 1.3.1 注记收口；两文件归档 completed/。八轮修订单独成提交 5796b32；结案另行提交。
