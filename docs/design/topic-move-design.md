# 主题分拣「移入主题」— 特性设计文档

> **修订记录**
>
> | 日期 | 变更人 | 变更摘要 |
> |---|---|---|
> | 2026-10-31 | Yiyue + Claude | 首次创建（草案待评审）：需求 82~89（含 2026-10-30 评审修订注，已用户确认）的技术设计——存储位置三方案对比（推荐私有目录 `topics/` 子目录）、`timestamp\|content` 组合事务原语、多选交互形态、「归档与主题」分区列表、「移入」词族微文案、故障注入与测试策略、89(a)~(p) 验收映射 |
> | 2026-11-01 | Yiyue + Claude | 开放问题 O1~O3 用户裁定（均采纳推荐）：O1 验收注入＝`run-as chmod` 受控复现＋单测注入参数（89(c) 措辞按 89 尾注授权校正）；O2 成功文案＝M>0 对齐第 15 章「（跳过 M 条已存在）」句式族＋N=0 专用直陈句式；O3 分区排列＝主题分区在前 |
> | 2026-11-01 | Yiyue + Claude | 三视角设计评审修订（40 项发现→4 必修＋应补收敛），用户已批准：§10.1 注入配方重写（绝对路径／目录 chmod／probe 预检／注入时点／89(d) 确定性主证据）＋§6.4 异常边界（runCatching 映射、`createTempFile` 未捕获分支）；§5.2 createdAt 兜底改「首见回写一次」＋SP `commit()`；§6.3 删除算法与 `parse` 同源规约（共享扫描）＋全删归一化＋`deleteEntries` 弃哨兵改 null 语义；§11 验收表补「构造方法／人工观察点／日志与 SP 文件证据」三列；§6.4 rename-fallback 截断分支如实披露（新增 R11）；§7.2 多选态对浏览态的处置；§8.2 提醒联动三处修正（仅 Success 触发／执行时重算 K／同秒碰撞连坐防护）；§10.2 测试矩阵扩充＋TopicRepository 构造器注入 baseDir；§9 SAF 失败文案对齐实发常量句式；§6.6/§8.1 移入后立即主动刷新；§6.5 `topicsDir()` null 兜底与创建顺序约束；§3.2 补三处接线；§10.4 既有行为回归清单；若干措辞修正 |
> | 2026-11-04 | Yiyue + Claude | **修订二追加（§14~§18，方案甲，specs 16.A）**：主题一等公民 IA 分家＋归档清空合并——新增 `topics` 路由与主题独立页（彩色首字标＝colorScheme.primary 派生 8 色板＋黑白自适应文字、对比度 ≥4.5:1 数学保证；卡片「N 条 · 创建时间」条数在前）；顶栏「主题」入口（浏览态常驻含 0 条、多选态隐藏）；归档页回名「归档管理」（合并页退役、分区常量退役）；网格第四格「清空」→「归档」点击二选一（X 方案：「归档并清空」error 红默认焦点／「只归档，保留列表」primary；双 toast 归并单条；archiveFile 重构 ArchiveOutcome）；菜单「归档」项移除、「主题」→「外观」；92 去向行＋首次移入指路后缀；SwipeCard 手势骨架抽取共用；C5/C6/C8/U6/A6/A8/P3-13/U1/A4 逐项定稿；94(a)~(f) 验收映射＋89(c)(d) 收口配方；数据层零改动（data/util/reminder 零文件） |
> | 2026-11-04 | Yiyue + Claude | **修订二·三视角评审修订**（架构师/资深工程师/资深测试工程师三方；必修 A-1/E-1/T-1/T-2＋应补与后置项全落，仅动 §14~§18）：A-1 指路旗标改道 `TopicRepository.consumeFirstMoveGuide()`（弃 ui 层直写 SP，「数据层零改动」口径更正为「事务与协议语义零改动」）；E-1 两段 runCatching 异常兜底＋三处直写退役＋`archiveFile` 删未用参数；T-1 URI 失效配方改**会话中失效构造**（probe＋降级口径＋specs 94(f) 措辞随校正）；T-2 TD-017 补 probe 预检＋chmod 静默失效降级；A-4/E-3 `performVibration` 下沉 SwipeCard 共享；A-5/E-5 `restoreKind` 移除＋`ArchiveEntryKind` KDoc 改写；A-7/E-6/T-5 对比度下界 4.585→4.583＋R17 明暗跳变披露＋派生签名定稿 `(primary, surface)` 双参；A-2 onComplete 等价动作入表＋`ArchiveOutcome` 落点 MainScreen.kt 顶层；A-3/T-3「逐字节等价」限定 writeAll 写盘段＋空内容分支行为变更（`NothingToArchive` 不清空）＋M1 决策映射纯函数测试＋旗标 Q2 注入口测试；E-2 TopicMoveDialogs :108 副文案换序；E-4 主题页重载走 Dispatchers.IO；T-6 主题页手势三验收槽；T-7 89(i)(m)(p) 挂接＋证据命令分场；T-8 新增 §17.4（模式分场/顺序链/备份清单补齐）；A-6/A-9/T-9/T-10 后置项顺手落 |
> | 2026-11-05 | Yiyue | **用户裁定（T-18 验收中）**：①网格「归档」格颜色回 **error 红**（沿旧「清空」格视觉——§15.3 A6 中性色裁定反转；78 语义自洽：二选一含清空路径）；②「清理不归档」第三选项**不采纳**，二选一功能维持现状；③主题条目级操作立项**丙批次**（「移回」优先、单条删除后置观察——待甲验收后走完整流程） |
> | 2026-11-05 | Yiyue | **决策全景三视角评审（产品/架构/用户）后裁定——戊＝主题工作区化立项**（「主题不是归档，是归类；主题内条目与主列表同权」）：**L1＝提醒解耦（原子单元：联动取消退役/孤儿对账扩主题（P0）/通知取内容先主后主题（含开机补发同函数两愈）/深链跳所在处）＋移出（原丙「移回」更名，搬回语义不复用恢复交互；反向事务去 SAF exists() 捷径）；L2＝主题内编辑＋删除；L3＝新增（缓）；乙（设置页）排后、免确认缓议**。反转面＝84(c)/85(d)（牵连清单：88 附注/确认框前置告知/R1R3 README/86 恢复注明/89(i) 反向断言/CHANGELOG 口径——16.B 统一定稿）。**版本节奏 A：v1.4.0＝甲现在收尾发布，戊＝v1.5.0**；T-18 全项验收含 89(i)（不跳）；主题页「恢复」去留留 16.B。甲骨架全保留（三方收敛 C1~C7） |
>
> **状态：定稿（2026-11-01 用户确认）**（六阶段流程之阶段2 完成；阶段3 计划以本文为主要输入，执行时以本文为主要参考）
>
> 需求权威源：`docs/specs.md` 第 16 章（需求 82~89）。本文不重复需求条文，只做技术翻译；与需求冲突处以需求为准。

---

## 1. 概述与需求映射

### 1.1 一句话概述

主列表进入多选模式，勾选若干条目后「移入主题」：在一个**单次持 `FileOperationLock` 的组合事务**里，把所选条目（按 `timestamp|content` 寻址、原时间戳原样）先写入手机本地主题文档、后从当前文件移除；目标可选已有主题或自由命名新建。主题文档与整文件归档在「归档与主题」列表分区展示，支持只读查看、长按复制全文、左滑恢复（复制语义）。

### 1.2 需求 → 设计章节索引

| 需求 | 主题 | 设计落点 |
|---|---|---|
| 82（修订 a/b） | 多选模式、选中集与轮询共存、多选预览 | §7 |
| 83（修订 a/b/c） | 一批一文档、命名规则、同名指路 | §8.1、§8.2、§5.3 |
| 84（修订 a~g） | 先写后删原子性、组合事务、失败语义、提醒联动、去向告知、寻址原语、刷新联动 | §6（核心） |
| 85（修订 a~e） | 分区展示、正向标识、删除语义、时间列与排序、入口更名 | §4、§8.3 |
| 86（修订） | 复用归档恢复交互、"取回是复制"注明 | §8.4 |
| 87（修订） | 透明幂等去重、N=0 成功 | §6.4、§9 |
| 88（修订） | 「移入」第四类写盘动作、计数、颜色、深色 | §9、§8.6 |
| 89（尾注） | 故障注入方法、验收 (a)~(p) | §10、§11 |

### 1.3 现状基线（设计依赖的既有事实）

- **全局锁**：`FileOperationLock`（`MarkdownFileOperations.kt:12`），JVM 内置 `synchronized`，**可重入**——这是组合事务"外层一次持锁、内层调用既有方法"的可行性前提（§6.2）。
- **删除原语现状**：`deleteEntry(timestamp)` 仅按时间戳、命中首条（`EntryParser.findEntryBounds`，`EntryParser.kt:248`）——同秒碰撞下"所选条目全部移除"不成立，**须新增按 `timestamp|content` 的批量删除原语**（需求 84(e) 明示"所需删除原语缺失时由设计补齐"）。
- **去重先例**：归档恢复按 `"${timestamp}|${content}"` 组合键去重（`ArchiveListScreen.kt:316-333`），key 的 content 为 `parse` 产物（trim + ZWSP 还原后）——本设计沿用同一语义。
- **刷新机制**：SP 逻辑 mtime + 2 秒轮询（`MainScreen.kt:359-368`、`FileRepository.kt:158-166`），新写盘点须手工补调 `setFileLastModified`（architecture.md §6.3）。
- **提醒联动先例**：删除条目 → `AlarmScheduler.cancel` + `ReminderStore.remove`（`MainScreen.kt:678-680`）；孤儿对账在 `refreshEntryCount`（`MainScreen.kt:241-249`）。
- **归档存储先例**：整文件归档**永远存应用私有外部目录**（architecture.md §3.5），即使当前活动文件是 SAF 模式。

---

## 2. 设计原则（用户定调 2026-10-30，提炼为可执行条目）

**基调**：「工具退后，内容向前」——骨：Rams 极简（有用 / 诚实 / 不显眼 / 做减法，每屏一个主任务）；面：内容优先（条目正文为最强层级、时间戳弱化、留白与行距保证扫读）；用：平静反馈（成功轻反馈、失败才打断、动效只解释状态变化）。组件底座 Material 3（Compose），吸收 Apple 的圆角 / 层级 / 细腻轻动效；不仿 iOS 控件与导航、不用毛玻璃类重效果。

落地为以下约束，全部新 UI 须逐条对照：

| # | 原则 | 本特性的可执行落点 |
|---|---|---|
| P1 | 每屏一个主任务 | 多选是**独立模式**：进入后顶栏整体切换为选择语境（返回 × 退出 + 「已选 N 条」+「移入」），不与浏览态混排；主界面浏览态只增加一个多选图标入口 |
| P2 | 不显眼 | 多选入口 = 顶栏 1 个图标；无第五主按钮（2x2 按钮网格是需求 16 固化布局，不动）；不新增导航页 |
| P3 | 内容优先 | 目标选择列表：主题名为主文字、时间与条数为弱化小字（`onSurfaceVariant`）；确认对话框只报条数与去向，不罗列条目内容 |
| P4 | 平静反馈 | 成功 = 单条 Toast（数字透明可核对）；失败才保留对话框供重试；勾选动效仅解释状态变化（勾选框 spring + 卡片容器色轻变），无装饰性动效 |
| P5 | 诚实 | 去重跳过数、提醒取消数、N=0 语义、「取回是复制不是搬回」全部明示（需求 87/88/86）；失败态明示"重试安全" |
| P6 | 颜色语义（需求 78） | 「移入」为非破坏性 → 确认按钮 primary（需求 88 已归类）；删除空主题 = 破坏性 → error 红；勾选框 primary；滑动指示背景色规则不变（本特性**非空**主题卡片禁用滑动删除，空主题保留删除手势，与 §8.3 一致） |
| P7 | 深色适配（需求 51） | 全部新界面 / 对话框只取 `MaterialTheme.colorScheme` 语义色，零写死色；沿用 v1.3 深色双色板，自动适配 |
| P8 | 对比度（TD-023 教训） | 小号文字不用 primary 承载（浅色 `#2196F3` 对白底仅 3.1:1）——新 UI 中时间列 / 条数等辅助信息一律 `onSurfaceVariant`；primary 只用于按钮、标题等大字号或组件级用途 |
| P9 | 做减法 | 本期不做：主题重命名（TD-027）、单条移出（TD-028）、多选全选 / 筛选（TD-029）、主题文档外部直开（ZWSP 出口问题，README 声明） |

---

## 3. 模块划分

依赖方向遵守现状分层（util ← data ← ui，architecture.md §1.1）：新增的条目寻址 / 批量删除 / 组合事务全部落 util 纯逻辑（可纯 JVM 单测），主题目录与元数据管理落 data，多选与对话框落 ui。

### 3.1 新增文件

| 文件（`Appendo/src/main/java/com/yiyue31/android/appendo/`） | 层 | 职责 |
|---|---|---|
| `util/EntryKey.kt` | util | 条目唯一标识数据类（`timestamp` + `content`，84(e) 寻址与 87 去重共用） |
| `util/TopicMoveTransaction.kt` | util | 移入组合事务编排（单次持锁、先写后删、失败态分类），纯编排不碰 Android UI |
| `data/TopicRepository.kt` | data | 主题文档管理：`topics/` 目录、列表（名称 / 创建时间 / 条数）、创建（含命名校验与同名检测）、空主题删除、创建时间元数据（`appendo_topics` SP）；`TopicNameRules` 纯函数同文件 |
| `ui/TopicMoveDialogs.kt` | ui | 移入流程三个对话框：目标选择、新建命名、确认（含失败重试态）；多选长按预览对话框 |
| `ui/MultiSelect.kt` | ui | 多选选中集维护纯函数（`retainedSelection`）+ 多选态顶栏组件 |
| `ui/ArchiveRestore.kt` | ui | 恢复确认对话框与去重追加循环**自 `ArchiveListScreen` 原样迁移为共用**（`RestoreConfirmDialog` + 去重追加函数；归档 / 主题两路消费，迁移动机与等价性回归见 §8.4/§10.4） |

### 3.2 修改文件

| 文件 | 改动 |
|---|---|
| `util/MarkdownFileOperations.kt` | 接口新增 `deleteEntries(keys: Set<EntryKey>): Int?`（§6.3） |
| `util/FileBasedMarkdownFile.kt` | 实现 `deleteEntries`（持锁 + 一次 `atomicWrite`） |
| `util/SafMarkdownFile.kt` | 实现 `deleteEntries`（持锁 + `readMainForWrite` + `safAtomicWrite`，失败 `.pending` 残留走既有软恢复） |
| `util/EntryParser.kt` | ① 新增纯函数 `buildContentWithoutEntries(content, keys): Pair<String, Int>`（批量删除行算法，与 `parse` 共享扫描，§6.3）；② **`countEntries(content)` 公开为纯函数**（宽松正则计数）——条目知识的收敛点本就是 `EntryParser`，借此消重 `MainActivity.kt:153` 与 `ArchiveRepository.countEntries`（`ArchiveRepository.kt:88`）两处私有副本，主题列表（`TopicRepository`）共用第三处 |
| `ui/MainScreen.kt` | 多选态状态机与顶栏切换；「移入」入口与执行（IO 协程）；提醒联动；`setFileLastModified` 补调；菜单「归档管理」→「归档与主题」；私有 `countEntries` 改调 `EntryParser` |
| `ui/EntryListScreen.kt` | 新增 `selectionMode` / `selectedKeys` / `onToggleSelection` / `onPreview` 参数（默认值保证归档详情复用零改动）；多选态禁用滑动删除、点击=打勾、长按=预览 |
| `ui/ArchiveListScreen.kt` | 改造为「归档与主题」分区列表（§8.3）：主题分区（正向标识 = `topics/` 目录）+ 归档分区；主题卡片交互差异化；主题恢复注明保留；恢复对话框 / 去重循环迁出至 `ui/ArchiveRestore.kt`（本文件改为消费方）；**`onArchiveClick` 签名扩展为携带 kind**（如 `onFileClick: (File, ArchiveEntryKind) -> Unit`，归档 / 主题两路），`ArchiveListScreen` 的调用方同步更新 |
| `ui/ArchiveDetailScreen.kt` | 新增可选参数 `screenTitle: String = "归档详情"`，主题详情传「主题详情」（§8.5） |
| `MainActivity.kt` | `archive_detail/{filePath}` 路由加可选 `kind` 查询参数（`archive`/`topic`）；**消费点**：`AppendoNavHost` 中归档列表的点击回调改为携带 kind 并构造 `archive_detail/{filePath}?kind=` 路由（`MainActivity.kt:110-116`）；私有 `countEntries` 改调 `EntryParser` |
| `data/ArchiveRepository.kt` | `countEntries` 私有实现改调 `EntryParser.countEntries`（行为不变，纯消重） |
| `ui/Microcopy.kt` | 「移入」词族全部字面常量（§9） |
| `README.md` | 声明项汇总（§12），含需求 80(b) 注明义务更新 |

### 3.3 文档同步义务（编码阶段执行，CLAUDE.md「任务完成检查」）

- `docs/architecture.md`：§2 模块地图（新文件）、§3.5（主题文档协议与 `topics/` 目录）、§3.6 SP 清单（`appendo_topics`）、§3.3 出口表（主题长按复制全文）、§6.3 新写盘点位、§11 存储层 / UI 清单核对。
- `CHANGELOG.md`、`docs/plans/debt-tracker.md`（TD-027~029 已登记，无需新增；若编码中产生新债补登）。

---

## 4. 主题文档存储位置与分区正向标识（决策点 D1）

需求 85(b)：分区判定须有**正向标识**（专属子目录或命名约定）；存储位置设计阶段定，但须显式评估 TD-021（SAF 授权不耐久）与唯一副本耐久不对称（私有目录卸载即毁 vs 主文件 SAF 存活）；86 恢复链与 85 列表 / 详情复用成本（ArchiveRepository 是 `java.io.File` 耦合）。

### 4.1 候选方案

#### 方案 A：私有目录专属子目录 `topics/`（推荐）

主题文档统一存 `getExternalFilesDir(null)/topics/<用户命名>.md`，与主文件存储模式**无关**（主文件在 SAF 时主题仍在私有目录）。分区正向标识 = **目录成员关系**（`topics/` 下的 `.md` 即主题文档），非命名排除式判据。

| 维度 | 评估 |
|---|---|
| TD-021 SAF 授权不耐久 | **直接消除**：无 URI、无授权，不存在"授权失效后内容不可达" |
| 耐久不对称（卸载即毁 vs SAF 存活） | **存在但可控**：与整文件归档完全同级（归档现状即私有目录唯一副本，architecture.md §3.5）；`allowBackup=true`（决策②）下 `getExternalFilesDir` 与 SP 均随 Android 云备份迁移，卸载重装可恢复。README 披露："无云备份条件下卸载即失去主题文档（与归档一致）" |
| 85(b) 正向标识 | **最强形式**：目录即标识，`Appendo_*.md` 排除式判据退化为归档区自身的定义而非分区判定依赖；即便用户目录混入杂散 `.md` 也不误判 |
| 86 恢复链 / 85 复用成本 | **最低**：主题文档是 `java.io.File`，`ArchiveRepository` / `ArchiveListScreen` / `ArchiveDetailScreen` / 左滑恢复去重逻辑全部直接复用（仅加 `kind` 区分文案与手势开关） |
| 84(a) 组合事务复杂度 | **最低**：目标恒为 `FileBasedMarkdownFile`（真原子 `atomicWrite`），写入段无 SAF `.bak`/`.pending` 状态机；只有源可能为 SAF（删除段复用既有软恢复） |
| 新建交互 | 命名即建，无 SAF 选择器弹窗（P1/P2：工具退后） |
| 代价 | 用户不能用其他文件管理器直取主题文档——但"直接用其他软件打开文件"是低频场景（CLAUDE.md 用户使用习惯），且"复制全文 → 微信"主路径由 85 的长按复制覆盖 |

#### 方案 B：SAF 逐主题 URI（每主题一个外部文件）

每个主题文档由用户经 SAF 在外部位置创建，URI 逐个持久化。

| 维度 | 评估 |
|---|---|
| TD-021 | **风险 ×N**：realme 实测 `takePersistableUriPermission` 不持久化 + 覆盖安装清授权——每主题一个 URI 即每主题一个失效面；失效后数据在文件里但应用读不到（比"卸载即毁"更隐蔽：用户以为安全，实则不可达，还需逐主题"重选文件"修复） |
| 耐久 | 卸载不毁（最优），但以授权失效面扩大为代价，净收益存疑 |
| 正向标识 | 无目录 / 命名统一标识，需维护"URI → 主题"注册表（SP），注册表本身是新的不一致源（URI 在注册表、文件被外部改名 / 删除） |
| 复用成本 | **高**：ArchiveRepository 是 `java.io.File` 耦合，列表 / 详情 / 恢复全链需抽象存储后端；授权失效主题在列表中呈"幽灵条目"需专门交互 |
| 事务复杂度 | 目标写入也进 SAF 软恢复状态机，84(b) 失败语义组合翻倍（源 SAF × 目标 SAF） |
| 交互 | 每次新建主题弹一次系统 SAF 选择器——高频动作重工具化，违背 P1/P2 |

#### 方案 C：混合（默认私有子目录 + 可选"绑定外部位置"）

| 维度 | 评估 |
|---|---|
| 灵活性最高，但两套存储路径并存：分区判定双源、创建时间元数据双处、事务目标两种实现、授权失效只影响部分主题（用户无法理解为何有的主题丢了有的没丢） |
| 复杂度 ≈ A + B 之和，YAGNI；"做减法"（P9）直接排除。将来若确有外部持久需求，可在 A 的目录结构上叠加导出功能，不需现在预埋 |

### 4.2 结论与推荐

**推荐方案 A**，理由收敛：① 与归档同级的既有耐久取舍（一致性最高，用户心智模型统一——「归档与主题」同一入口、同一目录策略）；② TD-021 风险归零；③ 84(a) 组合事务目标恒真原子，失败语义最简；④ 86 恢复链与 85 分区列表复用成本最低；⑤ 云备份（决策②）为"卸载即毁"提供与归档一致的缓解。**耐久不对称按需求 85(b) 如实披露进 README（§12）**。

---

## 5. 数据模型

### 5.1 主题文档文件格式：完全沿用现状协议

| 项 | 决定 | 理由 |
|---|---|---|
| 条目模板 | 沿用需求 4 固定模板（`\n---\n\n## {ts}\n\n{content}\n\n---\n`，`EntryParser.format`） | 需求 83 明示沿用；`EntryParser` 写读两侧对称直接复用 |
| 文件头 | 沿用 `# Appendo`（`MarkdownFormatter.FILE_HEADER`） | 与主文件 / 归档同源同格式：`EntryParser.parse` 直接可用、ZWSP 隔离不变量 1 继续生效（主题内容同样可能含时间戳形态行）、`formatForExport` 出口（需求 85 长按复制）天然干净 |
| 时间戳 | **原时间戳原样**（秒级历史条目亦原样，需求 83/37）；追加到目标末尾按时间戳字符串**升序**（ISO 风格零填充，字符串升序 = 时间升序；秒 / 毫秒混合时前缀比较仍正确） | 与归档恢复 `appendEntry` 同语义；目标文档文件内追加序与显示序解耦（详情页 `sortedByDescending` 自动排序，`EntryListScreen.kt:114`） |
| 编码 | UTF-8 无 BOM | 现状协议 |
| 文件名 | `<用户命名>.md`；`.md` 落盘自动附加、UI 层隐藏（显示 `displayName` 不含后缀） | 需求 83 修订 (b) |
| ZWSP | 写入走 `EntryParser.format`（含隔离）；读取 / 导出走 `parse` / `formatForExport`（含还原） | 不变量 1/2 不受损；architecture.md §3.3 出口表新增"主题长按复制全文"行（§3.3 同步义务） |

**分区正向标识落盘形态**：**目录**（`topics/` 成员关系），不依赖文件名前缀，也不引入元数据行（元数据行会破坏"主题文档 = 纯条目文件"的协议同构，`parse` 需新跳过规则，收益为零）。

### 5.2 创建时间与列表元数据（85(c)：排序稳定不随移入漂移）

| 候选 | 评估 | 结论 |
|---|---|---|
| 文件系统 mtime / 创建时间 | 移入操作更新 mtime → 排序漂移（正是 85(c) 禁止的）；API 26 上出生时间不可靠 | 否决 |
| 文件名内嵌时间戳（归档式 `Appendo_yyyyMMdd_HHmmss`） | 与自由命名冲突 | 否决 |
| **SP 持久化 createdAt** | 创建时一次写入、永不更新；移入只改文件内容不动 SP → 排序天然稳定 | **采用** |

- 存储：独立 SP 文件 `appendo_topics`（避免混入有历史沿革的 `appendo` SP；主题元数据整体生命周期独立，清理 / 迁移方便）。key = `created_<文件名含.md>`，value = 创建时刻 epoch millis。云备份覆盖（决策②口径）。**写入一律用 `commit()`（同步落盘）而非 `apply()`**——TD-024：本机实测 `apply()` 延迟冻结（v1.3 主题持久化同因修复，对齐 `ThemePreferences` 先例）；createdAt 写入低频（仅建主题 / 首见回写），同步开销可接受。
- **时间列语义定稿：显示创建时间**（`yyyy-MM-dd HH:mm`，与归档列表 `formatTimestamp` 同格式）；**排序 = createdAt 倒序**（新主题在前，与归档列表"新归档在前"一致）。不显示"最近移入时间"——活动属性会引起排序与时间列不对应的困惑，且移入频度高时列表跳动违背"平静"（P4）；做减法（P9）。
- **兜底（首见回写一次）**：文件存在但 SP key 丢失（云备份部分恢复 / 异常）→ 首次观测到时以 `file.lastModified()` 作为 createdAt **回写 SP 一次**，此后与正常主题无异。漂移的来源是"每次重算"（`lastModified` 随文件写入变化），而非"固化一次"——首见回写后值即冻结，排序与显示稳定不漂移；云备份部分恢复场景下，各文件的"首见"时点不同但各自回写后排序即固定，不产生反复跳动。
- 条目数：不入 SP，列表加载时实时读文件计数（`EntryParser.countEntries`，宽松正则，§3.2 消重后三处共用）——与归档列表行为一致，避免第二份会过期的计数。

### 5.3 命名规则（需求 83 修订 a/b/c 全量落实）

校验顺序（前者先报）：空名 → 保留字 → 非法字符 → 长度 → 同名检测。

| 规则 | 定义 | 反馈 |
|---|---|---|
| 空名 / 纯空白 | trim 后 `isEmpty()`（对齐需求 76 的 `isBlank` 语义；命名场景无"保留空白"诉求，先 trim） | toast「名称不能为空」，对话框保持 |
| 保留字 | `equals("Appendo", ignoreCase = true)` 或 `startsWith("Appendo_", ignoreCase = true)` | toast「不能用“Appendo”命名，也不能以“Appendo_”开头」（与 §9.2 定稿字面统一） |
| 非法字符 | `/ \ : * ? " < > |` 及 C0 控制字符（`Char.isISOControl()`）。清单取 Windows 保留字符 ∪ Linux 非法（`/`、`\0`）——为将来经 USB / 云盘把文件带到 Windows 侧留余地，成本为零 | toast「名称不能包含 / \ : * ? “ < > \| 等字符」 |
| 长度上限 | trim 后 ≤ **50 字符**（255 字节 fs 上限内留足余量；中文 3 字节 / 字，50 字 ≈ 150 字节 + `.md`；同时保证列表单行可读） | toast「名称过长（上限 50 字符）」 |
| 同名检测 | 与已有主题 `displayName` **大小写不敏感**比对（文件系统大小写敏感会造成 `AI.md` 与 `ai.md` 两文件，用户心智不敏感；与保留字校验同口径） | **阻止创建并指路**：对话框「已存在同名主题“X”，是否移入该主题？」，确认 → 目标切到该主题进入移入确认；取消 → 回到命名输入（可改名） |

校验逻辑收敛为纯函数 `TopicNameRules.validate(name, existingDisplayNames)`（data 层、纯 JVM 可测，§6.5）。

---

## 6. 接口设计（决策点 D2：组合事务与批量原语）

### 6.1 总体形状

移入 = **一个 util 层编排对象 + 一个新单文件原语 + 一个新纯函数**，不引入新存储后端：

```
MainScreen（IO 协程）
  └─ TopicMoveTransaction.move(source, target, keys)     ← 全程单次 synchronized(FileOperationLock)
       ├─ 1. source.readAllWithStatus()   （重入锁；失败 → SourceReadFailed，源目标均未动）
       ├─ 2. EntryParser.parse → 按 keys 取权威条目集（内容以锁内读取为准，UI 只传 keys）
       ├─ 3. target.readAllWithStatus()   （重入锁；失败 → TargetReadFailed，源未动）
       ├─ 4. 去重计算：toAdd = 所选 − 目标已有；skipped = 所选 ∩ 目标已有（87）
       ├─ 5. target.writeAll(目标全文 + toAdd 升序 format 拼接)  （重入锁；失败 → TargetWriteFailed）
       └─ 6. source.deleteEntries(keys)   （新原语，重入锁；失败 → SourceDeleteFailed）
```

**为什么"单次持锁"成立**：`synchronized` 是 JVM 内置可重入锁——外层 `move` 持锁期间，内部对 `readAllWithStatus` / `writeAll` / `deleteEntries` 的调用是同一线程重入，**不释放、无锁间隙**；并发分享写入（`ShareReceiverActivity`，同进程另一实例）在整个事务期间被互斥。这满足需求 84(a)"禁止两段分离持锁"，且不用改动任何既有方法的加锁结构（`atomicWrite` 的"调用方持锁"约定也保持，`FileBasedMarkdownFile.kt:139`）。

**为什么 UI 只传 `keys` 不传条目**：条目内容以锁内源读取为准——消除"UI 快照 → 确认对话框停留期间内容被编辑 / 删除"的陈旧快照问题；已不在源的 key 计入 `missingFromSource`（正常恒 0，仅防御），数字口径仍可核对（88：所选 N = moved + skipped + missing）。

### 6.2 `EntryKey`（util/EntryKey.kt）

```kotlin
/**
 * 条目唯一标识（需求 84(e)/87）：timestamp|content 组合键。
 * content 语义 = EntryParser.parse 产物（trim + ZWSP 还原后），与归档恢复去重（ArchiveListScreen）同源，
 * 同秒碰撞的秒级历史条目由此可区分。
 * 注：不提供 asString() 字符串形态——UI 与事务一律类型化传递；字符串拼接 key 仅存在于既有归档恢复
 * 代码（ArchiveListScreen.kt:316-333），新代码不扩散该形态。
 */
data class EntryKey(val timestamp: String, val content: String) {
    companion object {
        fun of(entry: ParsedEntry): EntryKey = EntryKey(entry.rawTimestamp, entry.content)
    }
}
```

（`LinkEntry` 侧以扩展 `val LinkEntry.entryKey: EntryKey` 映射，放 ui 层 `MultiSelect.kt`。）

### 6.3 新删除原语与批量行算法

**接口新增**（`MarkdownFileOperations.kt`，两实现同步实现——`ShareReceiverActivity` 直接实例化两实现不受影响，它不调用本方法，architecture.md §4.1 检查点已过）：

```kotlin
/**
 * 批量删除指定条目（需求 84(e)：按 timestamp|content 寻址，补齐 deleteEntry 仅按时间戳的缺口）。
 *
 * - 持 FileOperationLock；共享扫描 → 一次原子写（不逐条写，200 条仅 1 次全文件写）。
 * - 同 key 多条完全重复条目（历史数据理论存在）全部删除（幂等彻底）。
 * - 读失败中止不写（TD-012 纪律：读失败 ≠ 空文件）。
 *
 * @return 实删条数；读失败或写失败返回 null——对齐 appendReturningTimestamp 的
 *         「null ⟺ 操作未发生」语义（弃 -1 哨兵：哨兵值是合法 Int，可能被下游当有效计数消费；
 *         null 类型上不可误用）。SAF 实现写失败时 .pending 残留，下次读经 ensureConsistent
 *         从 .bak 回滚（既有软恢复）。
 */
fun deleteEntries(keys: Set<EntryKey>): Int?
```

**EntryParser 新增纯函数**（两实现共享的行算法）：

```kotlin
/**
 * 返回删除 [keys] 命中条目后的全文与实删条数（纯函数，可 JVM 单测）。
 * 见下方「与 parse 的同源规约」——行分类与条目区间必须来自共享扫描，不得另写平行算法。
 */
fun buildContentWithoutEntries(content: String, keys: Set<EntryKey>): Pair<String, Int>
```

**与 `parse` 的同源规约（互逆性，TD-015 根因原则：保护范围与丢失范围必须由同一函数定义）**

删除算法定位"什么是一条条目、它占哪些行"，**不得是 `parse` 的平行重实现**（两套边界逻辑必然分叉——TD-015 的教训正是写侧隔离谓词与读侧跳过谓词分叉）。两实现路径二选一，**本设计选路径 1**：

| 路径 | 描述 | 评估 |
|---|---|---|
| **1（采用）** | 重构出**内部共享扫描函数**：一次遍历产出「条目列表 + 每条目的行区间」（parse-with-spans，作为 `EntryParser` 内部实现）；公共 `parse` 保持签名不变、退化为丢弃行区间的薄包装；`buildContentWithoutEntries` 消费**同一扫描产物**的行区间做删除 | 单一扫描定义边界，读 / 删零分叉；公共 API 不变（所有既有调用方零改动）；行区间是删除算法本就需要的精确输入 |
| 2（不采用） | 仅把行分类谓词（时间戳边界 / 隔离跳过 / restoreLine 三分支）收敛为共享内部函数，删除算法自行组装条目区间 | 谓词共享了，但**区间组装逻辑仍是两份**（条目从哪行开始、到哪行结束）——分叉面只缩小未消除，违背同源原则的本意 |

**语义定义（随同源规约一并定稿，含旧数据边界）**：

- **空内容区域**（时间戳行之后到下一边界无任何内容行）：与 `parse` 的"丢弃无内容条目"语义一致——**不产出 key、不可被删除、原样保留**（无 key 即无法寻址；与 `parse` 对无内容条目的忽略严格同构，不得出现"parse 看不见、删除却能删"的分叉）。
- **TD-015 修复前旧数据**（内容中间遗留**未隔离**的 `---` / `# Appendo` 前缀行）：`parse` 对这些行走跳过分支、**不进入 content**（`EntryParser.kt:92-94`），故 key 的 content 不含它们——**匹配按 parse 语义**（key 不含遗留行）；**删除按扫描行区间**（命中条目的整个区间含遗留行一并删除，遗留行随条目消失）。两句话合起来即"匹配看 key、删除看区间"，均由共享扫描定义。
- **分隔线引用**：删除区间"含条目前紧邻分隔线"的判定必须引用 `MarkdownFormatter.SEPARATOR_LINE`（`MarkdownFormatter.kt:12`）——**勿复制 `buildDeletedLines` 硬编码 `"---"` 的既有瑕疵**（`EntryParser.kt:270`；该瑕疵登记为规避项，不在本特性顺手改既有函数，避免扩大改动面）。
- **全删归一化**：删除后若剩余内容解析得 **0 条条目**，输出归一化为纯 `MarkdownFormatter.FILE_HEADER`（清除残留分隔线 / 空行）——保证需求 89(m) 的文件级断言（主文件恰为 `# Appendo\n`，空态与计数归零由既有机制接管）。归一化只发生在"删空"分支，非全删不做任何重排。
- 其余：保留文件头与未命中条目；文件头之外的行原样保留；CRLF 行 / 无尾换行文件按 `lines()` 既有语义处理（与 `parse` 同源即同行为，测试钉死，§10.2）。

> 批量**追加**不新增接口方法：目标写入 = 目标全文 + `toAdd.sortedBy { it.timestamp }.joinToString("") { format(...) }` 后一次 `writeAll`——全部复用既有方法，接口面最小化。

### 6.4 组合事务（util/TopicMoveTransaction.kt）

```kotlin
/** 移入组合事务结果（需求 84(b) 失败语义精确化）。
 *  失败类不带 reason 载荷：每类在 UI 映射唯一文案（§9），无消费方读 reason；
 *  计数载荷口径统一——凡能给出计数的态（Success / SourceDeleteFailed）三字段齐全，
 *  N（所选）= moved + skippedExisting + missingFromSource 恒可核对（88）。 */
sealed interface MoveResult {
    /** 成功：moved=写入目标数；skippedExisting=目标已存在跳过数（87 透明）；
     *  missingFromSource=所选但源中未找到数（正常恒 0，防御口径）。 */
    data class Success(val moved: Int, val skippedExisting: Int, val missingFromSource: Int) : MoveResult

    /** 源读取失败（典型：SAF 授权失效）。目标与源均未改动——干净失败，无任何中间态。 */
    object SourceReadFailed : MoveResult

    /** 目标读取失败。源未改动（目标在私有目录，极罕见）。 */
    object TargetReadFailed : MoveResult

    /** 写入段失败（84b）：目标可能已部分写入（不回滚），当前文件原样保留。可整体重试。 */
    object TargetWriteFailed : MoveResult

    /** 删除段失败（84b）：目标已写全、当前文件可能部分移除——"两边都有"软中间态；
     *  重试幂等收敛（87：目标已存在 → skipped，源再删）。计数口径与 Success 相同。 */
    data class SourceDeleteFailed(val moved: Int, val skippedExisting: Int, val missingFromSource: Int) : MoveResult
}

object TopicMoveTransaction {
    /**
     * 移入组合事务（需求 84(a)）：单次持 FileOperationLock，先写目标、后删源，全程无锁间隙。
     *
     * @param source 当前文件的 MarkdownFileOperations（FileBased 或 Saf）
     * @param target 主题文档，恒为 FileBasedMarkdownFile（topics/ 私有目录，方案 A）
     * @param keys   所选条目标识；条目内容以锁内源读取为准
     *
     * 实现约束：
     * - 方法体整体 synchronized(FileOperationLock)；对 source/target 既有方法的调用为可重入持锁；
     * - **异常边界**：六个步骤各以 runCatching 包裹，IOException / RuntimeException 等一切异常
     *   映射到所在段的失败类（源读段→SourceReadFailed；目标读段→TargetReadFailed；
     *   目标写段→TargetWriteFailed；源删段→SourceDeleteFailed）。特别地：
     *   `FileBasedMarkdownFile.atomicWrite` 的 `File.createTempFile` 位于 `return try` 之外
     *   （FileBasedMarkdownFile.kt:143），目标目录只读时抛**未捕获异常**——由 move 外层捕获并映射
     *   `TargetWriteFailed`（89(c) 注入正落在此分支），**勿依赖底层修复**（不改既有 atomicWrite
     *   的异常边界，控制本特性改动面）。
     * - 目标不存在（新建主题懒创建兜底）视作空文档，初始内容 = FILE_HEADER；
     * - toAdd 按时间戳字符串升序拼接追加到目标末尾（需求 83），原时间戳原样（appendEntry 语义）；
     * - 去重 key 与归档恢复同源（timestamp|content，parse 产物语义）；
     * - 任一失败态不得出现"两边都没有"（84 硬约束）。
     */
    fun move(source: MarkdownFileOperations, target: MarkdownFileOperations, keys: Set<EntryKey>): MoveResult
}
```

**两实现的事务边界与失败态**：

| 段 | FileBased 源（默认模式） | SAF 源 |
|---|---|---|
| 源读（步骤 1） | 失败 / 异常 → `SourceReadFailed`，零副作用 | 同左；授权失效场景上层叠加 TD-021 文案（§9） |
| 目标读（步骤 3，恒 FileBased） | 失败 / 异常 → `TargetReadFailed`，源未动 | 同左（目标恒 FileBased） |
| 目标写（步骤 5，恒 FileBased） | `atomicWrite`（temp+fsync+rename）真原子：常规失败（createTempFile / open / rename 均失败）目标保持原样——**不回滚**、可整体重试。**rename 失败退化直接覆写的分支**（`FileBasedMarkdownFile.kt:151-155` 既有路径）若中断，目标可能截断：重试收敛**仅对所选条目成立**（重试以锁内新快照重写目标），**目标原有条目尾部可能丢失**——此为 `atomicWrite` 退化路径的既有系统性风险（所有 FileBased 全文件写共有，非本特性新增），R11 如实披露 | 同左（目标恒 FileBased，无 SAF 目标分支） |
| 源删（步骤 6） | `atomicWrite` 真原子：成功即完成；失败 / 异常 → `SourceDeleteFailed`（目标已写全，"两边都有"，重试收敛）；崩溃中断要么旧要么新（84(g)：中断允许"两边都有"，永不"两边都没有"） | `safAtomicWrite`：写失败 `.pending` 残留 → 下次读 `ensureConsistent` 从 `.bak` 回滚 = 回到删除前（"两边都有"）→ 重试收敛；与需求 39"SAF 软恢复"分级一致 |
| 幂等重试验证 | 二次 `move(同 keys)`：目标已有 → 全 skipped；`deleteEntries` 再删 → `Success(moved=0, skipped=N)`——UI 反馈 N=0 成功文案（§9），收敛完成 | 同左 |

### 6.5 主题仓储（data/TopicRepository.kt）

```kotlin
data class TopicDocument(
    val file: File,           // topics/<name>.md
    val displayName: String,  // 不含 .md（UI 显示）
    val createdAt: Long,      // SP 持久化 epoch ms；key 丢失时首见以 file.lastModified() 回写一次（§5.2）
    val entryCount: Int       // 实时读文件计数（EntryParser.countEntries，宽松正则）
)

sealed interface TopicCreateResult {
    data class Created(val file: File) : TopicCreateResult
    data class NameExists(val existing: TopicDocument) : TopicCreateResult  // → 83(c) 指路
    object NameEmpty : TopicCreateResult
    object NameReserved : TopicCreateResult
    object NameIllegalChars : TopicCreateResult
    object NameTooLong : TopicCreateResult
    object WriteFailed : TopicCreateResult
}

sealed interface TopicDeleteResult { object Deleted : TopicDeleteResult; object NotEmpty : TopicDeleteResult; object Failed : TopicDeleteResult }

/** 命名校验纯函数（§5.3 规则，纯 JVM 可测）。 */
object TopicNameRules {
    const val MAX_NAME_LENGTH = 50
    fun validate(rawName: String, existingDisplayNames: List<String>): TopicNameValidation // → Empty/Reserved/IllegalChars/TooLong/Exists(name)/Ok(trimmedName)
}

class TopicRepository(
    private val context: Context,
    /** 测试注入：直接指向临时目录（纯 JVM，无需 stub getExternalFilesDir——mock Context 的
     *  目录 stub 脆弱且不验证真实解析逻辑；构造器注入是显式依赖，§10.2 测试即用此口）。生产缺省 null。 */
    private val baseDir: File? = null
) {
    /** 主题根目录：baseDir 优先；否则 getExternalFilesDir(null) ?: context.filesDir——null 兜底
     *  对齐 FileRepository.getDefaultFile（FileRepository.kt:31-36）与 ArchiveRepository.getArchiveDirectory
     *  （ArchiveRepository.kt:34-36）两处先例。兜底触发时主题与归档/主文件**分家**（内外两个根目录）：
     *  listTopics 合并扫描两个候选目录自愈（外部目录优先、按 displayName 去重），分家期间主题不"消失"。 */
    fun topicsDir(): File
    fun listTopics(): List<TopicDocument>                       // createdAt 倒序；正向标识 = 目录成员；双候选目录合并扫描（见 topicsDir 注）
    fun findTopic(displayName: String): TopicDocument?         // 大小写不敏感
    fun createEmptyTopic(rawName: String): TopicCreateResult   // 校验 → writeAll(FILE_HEADER) 成功后才写 SP created_<file>（commit()）——**顺序约束：文件写失败不得残留 created_ key**（有 key 无文件 = 幽灵主题）；事务前创建空文档，列表即时可见、空可删
    fun deleteTopic(topic: TopicDocument): TopicDeleteResult   // 85(a)：entryCount > 0 拒绝（NotEmpty）；0 条可删（文件 + SP key，先删文件成功后删 key）
}
```

> **创建时机**：新建命名通过校验且（无同名）后、进入移入确认对话框前创建空文档——用户在确认框取消会留下一个空主题（0 条、可删、可复用）。取舍：不创建则取消零残留但事务需处理目标不存在（已兜底，`move` 支持懒创建）；选择"确认前创建"使列表即时可见、失败重试路径统一。代价（取消留空文档）可接受且可删（P5 诚实：所见即所建）。

### 6.6 新写盘点与刷新联动（需求 84(f)，对齐 75 技术前提）

| 写点 | 刷新联动 |
|---|---|
| 移入事务 `Success`（含 N=0，源被删改，无论 moved 多少）与 `SourceDeleteFailed`（源可能已部分移除） | MainScreen 回调**立即** `fileRepository.setFileLastModified(now)` **＋ `refreshEntryCount()`**——对齐删除 / 编辑 / 归档恢复的现状"主动刷新＋mtime 双保险"先例（`MainScreen.kt:675-676` 等），**不等 2 秒轮询**（轮询只作兜底；`SourceDeleteFailed` 时部分移除的状态须即刻如实反映）。需求 88 计数减少随之即时成立 |
| 目标创建 / 写入（topics/ 文件） | **不触发**主列表 mtime（不同文件）；「归档与主题」列表在导航进入时加载（现状 `LaunchedEffect(Unit)` 重建加载），主题恢复后显式 `loadArchives()`（对齐现状归档恢复回调） |
| 主题恢复（86，`appendEntry` 逐条） | 现状归档恢复已在 added>0 时调 `setFileLastModified`（`ArchiveListScreen.kt:335-337`），主题恢复复用同一路径（迁移至 `ui/ArchiveRestore.kt`，§3.2），无新增义务 |

---

## 7. 多选交互形态（需求 82 + 修订 a/b）

### 7.1 入口（决策点 D3）

| 候选 | 评估 | 结论 |
|---|---|---|
| A. **顶栏多选图标**（浏览态 TopAppBar actions 增 1 个图标） | 不占长按复制 / 滑动删除 / 点击编辑（82 硬约束）；不动 2x2 按钮网格（需求 16 固化）；M3 惯例（Files / Keep 等列表应用多选入口在顶栏）；分拣是高频主路径，可见性好 | **推荐** |
| B. 长按条目进入多选 | **违反 82**：长按已被"复制"占用（需求 21） | 否决 |
| C. 菜单加「移入主题」项 | 不占手势，但入口埋深（菜单已 7 项）、高频动作低可见性，违背 P1 | 备选 |
| D. 底部第五按钮 | 破坏需求 16 的 2x2 固化布局 | 否决 |

推荐 A。图标 `Icons.Default.CheckCircle`，`contentDescription = "多选"`，浏览态常驻（条目数为 0 时隐藏——空列表无从选择）。

### 7.2 多选态状态机与勾选 UI

```
浏览态（现状交互原样：点击编辑 / 长按复制 / 右滑删除）
   │ 点顶栏多选图标
   ▼
多选态（TopAppBar 整体切换为 contextual bar）：
   ├─ NavigationIcon：关闭 ×（点击退出，清空选中集）
   ├─ 标题：「已选 N 条」（N=selectedKeys.size，实时）
   ├─ actions：「移入」TextButton（N ≥ 1 启用；primary）
   ├─ 浏览态 2x2 按钮网格（手动输入/复制全部/分享全部/清空）：**隐藏**（推荐）——多选态整屏
   │    只服务选择这一个主任务（P1）；备选=禁用置灰（保留布局锚点，但"可见不可用"传递挫败感，
   │    不取）。退出多选即恢复
   ├─ 顶栏主标题「Appendo / 已收集 N 条」副标题：**隐藏**——条目计数信息由「已选 N 条」承接，
   │    避免双计数并列；退出多选恢复
   ├─ 列表项：卡片左缘叠加圆形勾选框（Checkbox，primary），点击卡片 = 打勾/取消勾选
   │    （82 修订 b：多选态点击不再打开编辑）
   ├─ 滑动删除手势禁用（多选态语义 = 选择；89(h) 退出后恢复）
   ├─ 长按 = 预览全文对话框（§7.4，82 修订 b 的"查看全文途径"）
   ├─ 2 秒轮询照常（§7.3）
   └─ SAF 失效回退对话框（showSafInvalidDialog，TD-021）弹出时：**自动退出多选＋清空选中集**
        ——模态引导优先，源不可读时选择上下文已无意义；退出后走既有重选/回退路径，不新增分支
   │ 点「移入」→ §8.1 流程；事务成功 → toast + 自动退出多选
   │ 系统返回手势 / 关闭 × → 退出多选，清空选中集
```

勾选视觉：勾选框 `spring(stiffness = Medium)` 缩放入场 + 卡片容器色 `surface → surfaceVariant` 轻变（P4：动效只解释状态变化）；条目正文 / 时间戳层级不变（P3：内容优先，选择工具不抢正文层级）。全选 / 筛选本期不做（TD-029）。

### 7.3 选中集维护（82 修订 a：与 2 秒轮询 / 并发分享插入共存）

选中集 = `Set<EntryKey>`（`timestamp|content`，与寻址 / 去重同键——一处定义三处复用：勾选、事务寻址、目标去重）。

```kotlin
// ui/MultiSelect.kt —— 纯函数，可 JVM 单测
/** 轮询/插入刷新后保留仍存在的选中项：条目仍在 → 保持勾选；已被删除 → 剔除（不丢失、不错位）；
 *  新插入条目默认未选。 */
fun retainedSelection(selectedKeys: Set<EntryKey>, currentEntries: List<LinkEntry>): Set<EntryKey>
```

接入点：`refreshEntryCount` 更新 `entries` 后追加一行 `selectedKeys = retainedSelection(selectedKeys, entries)`（`MainScreen.kt:239` 附近）。列表渲染勾选态按 key 查 Set——与 `LazyColumn` 复合 key（`"${timestamp}_$index"`，容忍重复时间戳）解耦，条目插入 / 删除后勾选不错位。同 key 双条（同 ts 同 content，理论存在）勾选态联动——可接受，注明（§12）。

### 7.4 多选态长按预览（82 修订 b）

多选态长按条目 → 只读预览对话框（**复刻编辑对话框的同构布局**——编辑对话框是 `MainScreen` 内联私有组件、非可复用单元，不共享实现，只对齐视觉结构）：时间戳小字（`onSurfaceVariant`，P8）+ 全文（`verticalScroll`）+ 底部「复制」（复制该条内容，出口经 parse 产物天然无 ZWSP）与「关闭」。选此形态而非 README 注明限制：实现成本低，补齐"多选态看不到全文"的可感知缺口；同时多选态长按的"复制单条"高频诉求由预览内「复制」承接。

**验收落点**：并入 89(h)（§11(h) 行已同步）——判据增加两条：预览全文与条目原文逐字一致（含多行 / 含时间戳形态行的 ZWSP 还原）；预览内「复制」出口做字符级零宽检查（与 (f) 同手法）。

---

## 8. UI 设计

### 8.1 移入流程（目标选择 → 新建/同名 → 确认 → 执行）

```
多选态点「移入」
  ▼
[对话框 1] 目标选择（标题「移入主题」）
  ├─ 已有主题列表（createdAt 倒序；名称主文字 + 「yyyy-MM-dd HH:mm · N 条」弱化小字，P3/P8）
  ├─ 列表末项「新建主题…」
  │     ▼
  │  [对话框 2] 新建命名（标题「新建主题」）
  │  ├─ OutlinedTextField（placeholder「请输入主题名称」，自动聚焦弹键盘）
  │  ├─ 确认「创建」→ §5.3 校验链：
  │  │    空/保留字/非法/超长 → toast，对话框保持
  │  │    同名 → [对话框 2b] 指路：「已存在同名主题“X”，是否移入该主题？」
  │  │            「移入该主题」→ 目标 = 该主题，进入对话框 3
  │  │            「取消」→ 回到对话框 2（输入保留，可改名）
  │  │    通过 → createEmptyTopic（失败 → toast「移入失败（创建主题文档失败）」，流程中止）
  │  │            → 进入对话框 3
  │  └─ 「取消」→ 回到对话框 1
  └─ 点选已有主题 → 进入对话框 3
  ▼
[对话框 3] 确认（标题「移入主题」，需求 84(d)）
  ├─ 正文（§9 定稿字面）：
  │    将把 N 条移入主题「X」。
  │    移入后这些条目将从主列表消失，可在「归档与主题」的主题分区查看。
  │    （K>0 时追加一行）其中 K 条设有未触发提醒，移入后将取消。
  ├─ 确认按钮「移入」（primary，需求 78/88）→ 执行
  │    执行中：按钮禁用 + 字面「移入中…」（89(p) 可感知进行反馈；事务在 Dispatchers.IO，200 条 = 2 次全文件写，不 ANR）
  │    成功（Success，含 N=0）→ 关全部对话框 + 退出多选 + toast（§9）+ 提醒联动（§8.2）
  │            + setFileLastModified + refreshEntryCount()（立即主动刷新，不等 2 秒轮询，§6.6）
  │    失败 → toast「移入失败（原因附注）」，对话框 3 保持打开，确认按钮字面变「重试」（84(b)：
  │            保持勾选与目标选择，一键重试；重试走同一 move，幂等收敛）；
  │            SourceDeleteFailed 态例外地同时触发刷新（源可能已部分移除，UI 即刻如实反映，§6.6）
  │            其余失败态不刷新（源未动，无需刷新）
  └─ 「取消」→ 关全部对话框，**保持多选态与选中集**（用户可换目标重来；所选主题文档若为本流程
       新建的空文档则保留——空主题可删、可复用，见 §6.5 取舍注明）
```

### 8.2 提醒联动执行序（84(c)）

1. **触发条件——仅 `Success`（含 N=0 全部已存在）**：成功回调里才执行取消；**一切失败态均不取消**，包括 `SourceDeleteFailed`（"两边都有"下条目可能仍在主列表可见，此时取消会误杀可见条目的提醒）。失败态后续若条目真从源消失，由既有孤儿对账兜底清理（`refreshEntryCount` 差集，§1.3）——不重复设防。
2. **K 的计算时机**：确认对话框正文的 K 是**预览值**（打开对话框时计算）；**执行取消在成功回调内重算**（确认停留期间提醒可能被设置 / 取消 / 触发——重算保证 toast 附注数字与实际取消数一致，88"数字可核对"）。
3. **同秒碰撞连坐防护**：sidecar key = 时间戳，一条提醒覆盖**同 ts 的全部条目**（architecture.md §3.2）。取消前校验**该 ts 的条目是否已全部从源移走**（成功回调内以刷新后的源解析为准）：若同 ts 存在未被移走的条目（同秒碰撞、同 ts 不同 content 且未被选中），**不取消**该提醒——它仍属于留在主列表的那条；此边界在 R8 注明。
4. 取消动作与删除条目联动完全同款：`AlarmScheduler.cancel(context, ts)` + `ReminderStore.get(context).remove(ts)`（`MainScreen.kt:678-680`）。
5. 成功 toast 追加附注「，已取消 K 条提醒」（K>0 时，§9；K 为防护过滤后的实际取消数）。
6. 恢复回当前文档提醒**不复活**：提醒已被删除，恢复是纯条目追加，无复活路径——零代码保证，README 声明（§12）。`missingFromSource` 条目的提醒由既有孤儿对账自动清理（§1.3）。

### 8.3 「归档与主题」分区列表（需求 85）

改造 `ArchiveListScreen.kt`：

| 项 | 设计 |
|---|---|
| 入口与标题 | 菜单项更名「归档与主题」（`MainScreen.kt:415`）；页面 TopAppBar 标题同步；README 需求 80(b) 注明义务更新 |
| 结构 | 单 `LazyColumn` 两分区：**主题文档分区在前**（高频取用——"按主题拷去微信"是主场景）、归档分区在后。分区标题小字：「主题文档」「归档」（`labelMedium` + `primary`，分区标题属组件级用途、非正文，P8 允许）。某分区为空则该区不渲染（含标题）；两区皆空 → 整页空态（文案沿用归档空态改写：「暂无归档与主题」/「归档与主题文件会显示在这里」） |
| 主题卡片 | 复用 `ArchiveCard` 骨架（图标 + 名称 + 「创建时间 · N 条」弱化小字）；点击 → 详情（§8.5）；长按 → 复制全文（`parse` + `formatForExport`，与归档长按复制同款——需求 85 出口铁律：无隐形字符）；左滑 → 恢复（§8.4） |
| **右滑删除禁用**（85(a)） | 主题卡片 `onSwipeToDelete` 恒不触发（滑动手势对非空主题完全不响应——不是弹提示，是无可滑动反馈）；**仅空主题（0 条）启用右滑删除**，确认对话框标题「删除空主题」（error 红、按钮「删除」，78(a) 破坏性=红），正文「确定删除空主题“X”吗？此操作无法撤销。」。空主题卡片副文案显示「空」提示可删状态（可发现性：滑动手势不可见，副文案补位）。删除走 `TopicRepository.deleteTopic`（文件 + SP key），失败 toast「删除失败」 |
| 非空禁删 | 无删除入口（手势禁用）即无路径；官方清理路径 = 左滑恢复清空 → 空主题 → 删除（85(a) 全流程，89(j) 验收） |
| 时间列 / 排序 | §5.2 定稿：createdAt 倒序、显示创建时间、SP 持久化不漂移 |
| 正向标识 | §4/§5.1：`topics/` 目录成员（`TopicRepository.listTopics`），归档区沿用 `Appendo_` 前缀判据（此时它只是归档区自身定义，不承担分区排除式判定） |
| 列表数据加载 | `topics + archives` 合并加载（`Dispatchers.IO`，对齐现状 `loadArchives`）；恢复 / 删除操作后重载 |

### 8.4 主题恢复（需求 86：复用归档恢复交互 + "复制不是搬回"注明）

复用左滑恢复确认对话框与逐条 `appendEntry` 去重逻辑（`ArchiveListScreen.kt:284-371` 原样迁移为共用），主题路径差异仅两处：

1. 确认对话框正文追加注明行：「追加后主题文档中仍保留这些条目。」（86 修订定稿口径：恢复 = 复制语义）。
2. toast 复用第 15 章句式（`Microcopy.toastAppendedCount` / `toastAppendedCountSkipped`），与归档恢复同字面——动词「追加」正确（恢复是往当前文档**追加**，非"移入"的反向搬回）。

### 8.5 主题详情页（只读）

复用 `ArchiveDetailScreen` + `EntryListScreen(readOnly = true)`（长按复制单条可用、无编辑无删除——需求 85(d) 只读限制）。路由 `archive_detail/{filePath}?kind=topic`，`ArchiveDetailScreen` 新增 `screenTitle` 参数（默认「归档详情」不变，主题传「主题详情」），文件名小字展示 `displayName`（不含 .md）。归档详情行为零变化（89(h) 覆盖）。

### 8.6 深色适配与颜色（需求 51/78）

- 全部新组件只取 `MaterialTheme.colorScheme` / `successColor` 语义色，随 v1.3 双色板自动适配深浅（P7）；无新颜色 token、无写死色。
- 颜色归类（78）：「移入」「创建」「移入该主题」「追加」= primary；「删除空主题」= error；多选勾选框 = primary。
- TD-023 规避（P8）：主题卡片「时间 · 条数」、预览对话框时间戳、确认对话框去向说明等小字一律 `onSurfaceVariant`；primary 仅用于按钮 / 分区标题等组件级用途。
- 89(h) 深色走查覆盖：多选顶栏、勾选框与选中卡片容器色、三个移入对话框、同名指路、预览、分区列表与空主题删除确认。

---

## 9. 微文案定稿建议（需求 88 口子，全部入 `Microcopy.kt`）

「移入」词族为第 15 章术语表**第四类写盘动作**（specs 第 15 章指针注）：确认 / 成功 / 失败动词统一「移入」。

### 9.1 移入词族

| 场景 | 建议字面 | 常量 / 函数 |
|---|---|---|
| 确认按钮（对话框 3） | 移入 | `BTN_MOVE_IN` |
| 执行中按钮 | 移入中… | `BTN_MOVING` |
| 失败重试按钮 | 重试 | `BTN_RETRY` |
| 成功（M=0） | 已移入 N 条 | `toastMovedCount(n)` |
| 成功（M>0） | 已移入 N 条（跳过 M 条已存在） | `toastMovedCountSkipped(n, m)` |
| 成功（N=0，87 修订） | M 条已在该主题中，已从当前列表移除 | `toastMovedAllExisting(m)` |
| 成功附注（K>0，84(c)） | ，已取消 K 条提醒（与主文案拼接） | `removedRemindersSuffix(k)` |
| 失败（统一） | 移入失败 | `TOAST_MOVE_FAILED` |
| 失败·源读（SAF 失效） | 移入失败：自定义目录授权可能已失效，请打开 appendo 重选文件 | `TOAST_MOVE_FAILED_SAF_REVOKED`（对齐实发常量 `TOAST_APPEND_FAILED_SAF_REVOKED`（`Microcopy.kt:75`）的「动词失败：原因，行动指引」冒号句式，非括号附注） |
| 失败·写入段（84(b)） | 移入失败（写入主题文档失败） | `toastMoveFailedTargetWrite()` |
| 失败·删除段（84(b)） | 移入失败（主题已保存，从当前列表移除未完成，请重试） | `toastMoveFailedSourceDelete()`——**明示"重试安全"**（内容已在目标，重试只完成移除） |

**句式对齐说明（88 修订：对齐第 15 章或注明差异）**：成功附注采用「跳过 M 条已存在」——与第 15 章归档恢复「已追加 N 条（跳过 M 条已存在）」（`Microcopy.toastAppendedCountSkipped`）**完全同构**，差异仅在动词（追加→移入）；specs 88 原文「M 条目标已存在」的"目标"语义由对话框语境（刚选定主题「X」）承接，不牺牲透明度。N=0 分支不套用「已移入 0 条」——"0 条"读作失败/歧义（87 修订明令禁止），改为直陈事实句式。数字口径可核对：所选 N = moved + skipped（+ missing，恒 0 不显示）；K 为提醒取消数、独立附注（88 修订"全部有着落"满足）。

### 9.2 流程与校验文案

| 场景 | 建议字面 | 常量 / 函数 |
|---|---|---|
| 对话框 1/3 标题 | 移入主题 | `DLG_MOVE_TITLE` |
| 确认正文（84(d)） | 将把 N 条移入主题「X」。\n移入后这些条目将从主列表消失，可在「归档与主题」的主题分区查看。 | `dlgMoveConfirmBody(n, topic)` |
| 确认正文提醒行（K>0） | 其中 K 条设有未触发提醒，移入后将取消。 | `dlgMoveConfirmReminder(k)` |
| 新建项（列表末项） | 新建主题… | `ITEM_NEW_TOPIC` |
| 新建对话框标题 / placeholder | 新建主题 / 请输入主题名称 | `DLG_NEW_TOPIC_TITLE` / `PLACEHOLDER_TOPIC_NAME` |
| 新建确认按钮 | 创建 | `BTN_CREATE_TOPIC` |
| 命名·空 | 名称不能为空 | `TOAST_TOPIC_NAME_EMPTY`（对齐「内容不能为空」句式） |
| 命名·保留字 | 不能用“Appendo”命名，也不能以“Appendo_”开头 | `TOAST_TOPIC_NAME_RESERVED` |
| 命名·非法字符 | 名称不能包含 / \ : * ? “ < > \| 等字符 | `TOAST_TOPIC_NAME_ILLEGAL` |
| 命名·超长 | 名称过长（上限 50 字符） | `TOAST_TOPIC_NAME_TOO_LONG` |
| 同名指路（83 修订 c） | 标题「已存在同名主题」正文「已存在同名主题“X”，是否移入该主题？」按钮「移入该主题」/「取消」 | `DLG_SAME_NAME_TITLE` / `dlgSameNameBody(x)` / `BTN_MOVE_TO_EXISTING` |
| 创建文档失败 | 移入失败（创建主题文档失败） | `toastMoveFailedCreateTopic()` |
| 空主题删除 | 标题「删除空主题」正文「确定删除空主题“X”吗？此操作无法撤销。」按钮「删除」（红）/「取消」 | `DLG_TOPIC_DELETE_TITLE` / `dlgTopicDeleteBody(x)` |
| 主题恢复注明（86 修订） | 追加后主题文档中仍保留这些条目。 | `TOPIC_RESTORE_NOTE`（追加在恢复确认正文末） |
| 多选顶栏 | 已选 N 条 | `topBarSelectedCount(n)`；退出按钮 contentDescription「退出多选」 |
| 预览对话框 | 标题 = 条目时间戳；按钮「复制」「关闭」 | 复用 `TOAST_COPIED`（"已复制"，需求 79 句式） |
| 分区标题 | 主题文档 / 归档 | `SECTION_TOPICS` / `SECTION_ARCHIVES` |

以上字面为**设计定稿建议**，按需求 88 口子留设计评审微调空间；结构（动词唯一、附注句式族、颜色语义）为硬约束不随字面调整而变。

---

## 10. 故障注入与测试策略（需求 89 尾注）

### 10.1 故障注入方法（89(c)(d)）——受控复现为主 + 单测注入参数

**89(c) 目标写入失败（写入段）——chmod 只读目录受控复现**：

1. **注入时点**：**选定 / 新建目标主题之后、确认「移入」之前**。此前注入会命中 `createEmptyTopic` 的 `WriteFailed` 分支（新建路径）而非事务写入段，验不到目标语义。
2. **注入命令**：`adb shell run-as com.yiyue31.android.appendo chmod 500 /storage/emulated/0/Android/data/com.yiyue31.android.appendo/files/topics`
   - 必须**绝对路径**：run-as 的工作目录在内部存储 `/data/data/<pkg>`，相对路径 `files/topics` 会指向不存在的内部分区，chmod 打错目录（注入"假成功"）；
   - 必须**对目录 chmod** 而非对文件 chmod 400：`atomicWrite` 的 temp+rename 替换只检查**目录**写权限（POSIX rename 不检查被替换文件自身的权限），文件只读挡不住替换、也挡不住 `createTempFile` 之外的其他写入路径；目录 500（r-x）同时封死 createTempFile 与 rename。
3. **注入预检判据**：chmod 后执行 `adb shell run-as com.yiyue31.android.appendo touch /storage/emulated/0/Android/data/com.yiyue31.android.appendo/files/topics/probe`，**应报 `Permission denied`**；若预检不生效（部分 ROM 的 run-as 对外部存储 chmod 无效），**降级**：该分支以单测注入参数覆盖＋真机 logcat 断言失败日志（`TopicMoveTransaction`/`FileBasedMarkdownFile` 的 error 日志），真机项标记"单测覆盖＋日志佐证"。
4. 确认「移入」→ 断言：toast「移入失败（写入主题文档失败）」、对话框保持变「重试」、当前文件逐字节原样（§11(c) 证据）；`chmod 700` 恢复后一键重试成功。
5. specs 原文"存储满/URI 失效"在本设计下不可构造（目标非 SAF 无 URI；存储满需填满整机）——验收措辞已按 89 尾注授权校正（O1 裁定）。

**89(d) 软中间态「两边都有」→ 幂等收敛——确定性主证据**：

1. **run-as 手工构造中间态**：选定目标与条目后**不执行**移入，用 run-as 直接向主题文件追加该批条目块（模拟"写入段完成、删除段未发生"的崩溃现场：主文件不动、主题已含该批）；
2. App 内对**同批条目**执行移入 → 断言收敛（全部为文件级证据，不依赖崩溃时机）：主题文件条数**不变**（全部 skipped）、主文件该批**清空**、反馈 N=0 直陈句式（§9.1）——`run-as cat` 前后对照；
3. 等效的 In-App 构造：移入成功 → 主题恢复追加回主文件（即得"两边都有"）→ 再移入同批——与 89(o) 为同一构造，两验收项可连做；
4. `am crash` 写入窗口：**降为加分尝试**——事务仅 2 次全文件写、窗口毫秒级，architecture.md §12 记录该手法历史从未做成；不作为主证据，做成则额外记录。

**单测层失败分支**：`TopicMoveTransaction.move` 增加 `@VisibleForTesting` 故障注入参数（`faults: MoveFaults = MoveFaults.NONE`，可选 `FAIL_TARGET_WRITE` / `FAIL_SOURCE_DELETE`）——纯 JVM 覆盖 84(b) 两类失败语义与幂等收敛，不依赖真机（真机 chmod 预检失效时的兜底）。

**DEBUG 钩子（生产代码内留开关）**：不采用——为验收在生产面留注入点违背"工具退后 / 做减法"，且上述手段已可覆盖全部验收分支。

### 10.2 单元测试要点（纯 JVM；`--max-workers=1` 串行执行；Robolectric 不可用 TD-003——FileBased 路径沿 `FileBasedMarkdownFileTest` 的 mock Context + 真实临时目录模式）

| 测试文件 | 要点 |
|---|---|
| `util/TopicMoveTransactionTest.kt` | ① 成功：目标按时间戳升序含所选、时间戳与内容原样（秒级条目原样）；② 目标部分已存在：不重复写入、源全删、moved/skipped 计数正确；③ N=0：全部 skipped、源全删、`Success(0, N, 0)`；④ 写入段注入失败：源原样（逐字节对比）、`TargetWriteFailed`；⑤ 删除段注入失败：目标已写全（"两边都有"）、`SourceDeleteFailed`；⑥ 幂等收敛：⑤ 态后重跑同 keys → `Success(0, N, 0)` 且源清空、目标无重复；⑦ **并发终态判据**：双线程（move ＋ append）并发多轮后断言**终态一致**——源条目集 = 期望、目标无重复无丢失、并发 append 的条目全部在源（84(a) 互斥的终态证明；弃"持锁期间被阻塞"类时序断言——锁公平性/调度顺序非契约）；⑧ 源读失败（源文件 `setReadable(false)`）→ `SourceReadFailed` 零副作用；⑨ 目标不存在（懒创建兜底）→ 以 FILE_HEADER 起步正常写入；⑩ `TargetReadFailed`：目标文件 `setReadable(false)`（等价 chmod 000）→ 源未动；⑪ **真实 IO 失败打在 `FileBasedMarkdownFile.deleteEntries` 实现上**：源目录 `setWritable(false)` / 源文件 `setReadable(false)` → 断言返回 `null`（失败）而非 0 条、抛出的异常被 move 映射 `SourceDeleteFailed`；⑫ missing>0：keys 含源中不存在项 → `N = moved + skipped + missing` 口径钉死；⑬ 秒级 / 毫秒级混合时间戳的 toAdd 拼接序断言（字符串升序 = 时间升序，含同秒秒级/毫秒级对） |
| `util/EntryParserTest.kt` 扩展（`buildContentWithoutEntries` 与共享扫描） | ① 同秒碰撞：同 ts 不同 content 两条只删所选；② 同 key 双条全删；③ 边界：首条 / 末条 / 前导 `---` 连带 / 文件头保留；④ 内容含 `---`、`# Appendo`、时间戳形态行（ZWSP 隔离往返不误删不残留）；⑤ keys 为空 / 无命中 → 原文不变、返回 0；⑥ CRLF 行 / 无尾换行文件 / **遗留未隔离 `---`·`# Appendo` 行**（TD-015 修复前旧数据）：key 匹配按 parse 跳过语义、删除按扫描行区间（遗留行随条目删除）；⑦ **全删归一化**：删除全部条目 → 输出恰为 `FILE_HEADER`（无残留 `---`，89(m) 断言）；⑧ 空内容区域（时间戳行后无内容）：不产出 key、不被删除、原样保留 |
| `data/TopicRepositoryTest.kt` | **注入方式：构造器注入 `baseDir` = JUnit 临时目录（纯 JVM，不 mock Context，§6.5 签名）**。① 命名校验全分支（空 / `Appendo` 与 `appendo_` 大小写变体 / 非法字符逐个 / 50 上限边界 / 同名大小写不敏感）；② 创建：`.md` 附加、FILE_HEADER 落盘、SP createdAt 写入；③ 列表：createdAt 倒序、**移入文件内容变更后 createdAt 与排序不变**（85(c) 漂移回归）；④ SP key 丢失 → **首见回写一次**：首次 listTopics 后 SP 有值、二次读取值不变、排序稳定（§5.2）；⑤ 删除：非空拒绝 / 空可删（文件 + SP key 同清）；⑥ `createEmptyTopic` WriteFailed（baseDir 只读）→ **无 SP created_ key 残留**（顺序约束回归）；⑦ `deleteTopic` Failed 分支（删除失败 → `Failed`，文件与 SP 均保持）；⑧ **含秒级条目的主题文档 entryCount 计数正确**（TD-010 防复发——宽松正则不得回退为严格秒级/毫秒级单形态） |
| `ui/MultiSelectTest.kt` | `retainedSelection`：① 新条目插入（头部 / 中部）后既有选中全保持；② 选中条目被删后从集合剔除；③ 空集 / 全保留边界 |

SAF 源的 `deleteEntries` 无 JVM 单测（TD-003 现状约束），靠 89(c)(d) 真机受控复现覆盖——与既有 SAF 路径验证策略一致（architecture.md §7）。

### 10.3 测试命令

```bash
./gradlew testDebugUnitTest --max-workers=1   # 串行（项目测试执行规则）
./gradlew assembleDebug
```

### 10.4 既有行为回归清单（本特性改动面触及的既有路径，验收门槛＝既有单测全绿＋下列真机抽查）

| 回归对象 | 回归要点（动因） |
|---|---|
| 归档恢复迁移等价性 | 恢复对话框 / 去重循环迁至 `ui/ArchiveRestore.kt` 共用（§3.2）——同一测试归档文件在迁移前后恢复，added / skipped 计数与 toast 字面一致（`ArchiveRestoreDeduplicationTest` 既有用例全绿＋真机抽查一次） |
| 整文件归档 | 菜单「归档」→ 归档文件生成 / 计数 / toast 不变（`ArchiveRepository.countEntries` 改调 `EntryParser` 后行为等价，TD-010 防复发由 §10.2 ⑧ 钉死） |
| 分享接收（SAF 主文件时移入） | 移入事务持锁期间分享写入互斥不丢（§10.2 ⑦ 单测）＋真机：移入前后各收一次分享、追加正常；89(n) 的互补面 |
| 提醒常规路径 | 设置 / 触发 / 贪睡 / 开机恢复回归——`appendo_topics` SP 引入不碰 `appendo_reminders`；设提醒→到点响一次抽查 |
| 复制全部出口格式化 | 主文件「复制全部」v1.3 格式化语义不变（`copyContent` 路径未被本特性触碰，回归确认——89(l) 的互补面） |

---

## 11. 验收映射（需求 89 (a)~(p) → 设计落点＋双轨证据）

> 三列证据口径：**构造方法**（如何 deterministic 地造出场景）｜**人工观察点**（真机 UI 判据）｜**日志与 SP / 文件证据**（`adb shell run-as com.yiyue31.android.appendo …` 可复核的客观证据；外部存储一律绝对路径 `/storage/emulated/0/Android/data/com.yiyue31.android.appendo/files/…`，§10.1 注 2）。既有行为回归另见 §10.4。

| 项 | 场景（设计落点） | 构造方法 | 人工观察点 | 日志与 SP / 文件证据 |
|---|---|---|---|---|
| (a) | 3 条移入新建：升序、原样、计数同步（§6.4 升序拼接、§6.6 立即刷新） | 手动输入 / 分享造 3 条（含 1 条秒级旧时间戳条目更佳）→ 多选 3 条 → 新建「T-a」 | 主题详情 3 条、时间戳与内容原样（秒级原样）；主列表少 3 条、计数同步、无空态误显 | `run-as cat` 主文件（文件头在、少 3 块）；`cat …/files/topics/T-a.md` 与移入前源条目块逐字节比对 |
| (b) | 2 条已存在：不重复写、源全删、反馈 1/2（§6.4 去重＋§9） | 2 条移入 T-b → 主题恢复追加回主文件 → 再选同 2 条＋1 新条移入 T-b | toast「已移入 1 条（跳过 2 条已存在）」；T-b 详情仅 +1 条；主列表清 3 条 | `cat …/topics/T-b.md`：3 块、无重复 `timestamp\|content`；主文件无该 3 条 |
| (c) | 目标写入失败：源原样、可重试（§6.4 `TargetWriteFailed`＋§10.1 配方＋§8.1 重试态） | §10.1 chmod 配方（**选/建目标后、确认前**注入＋probe 预检） | toast「移入失败（写入主题文档失败）」、对话框保持变「重试」；`chmod 700` 恢复后重试成功且两边状态正确 | 注入前后 `run-as cat` 主文件逐字节一致（源原样）；重试后 `cat …/topics/` 目标内容正确；（预检失效时）logcat 断言 `atomicWrite`/`TopicMoveTransaction` 失败日志 |
| (d) | 中断"两边都有"→ 幂等收敛（§6.4 失败态表＋§10.1 确定性主证据） | §10.1：run-as 手工向主题文件追加该批（主文件不动）→ App 内重试同批；等效 In-App 构造见 (o)；`am crash` 为加分尝试 | 重试反馈 N=0 直陈句式；主列表该批清空 | **文件级断言**：重试前后 `cat …/topics/` 条数不变（skipped）、`cat` 主文件无该批；（加分）am crash 后同一断言 |
| (e) | 误移恢复：去重＋反馈（§8.4） | (a) 完成后左滑 T-a → 恢复 | 「已追加 N 条（跳过 M 条已存在）」；主列表条目回归历史位置；确认框含「主题文档中仍保留这些条目」注明 | `cat` 主文件含恢复条目（原时间戳落回）；`cat …/topics/T-a.md` 不变（复制语义） |
| (f) | 主题长按复制全文：格式化、无标记（§8.3 出口铁律） | 长按 T-a 复制 → 粘贴到目标 | 无 `##`/`---`/`# Appendo` 结构标记；秒级时间戳行＋内容完整、条数齐 | 粘贴至**可显示不可见字符的编辑器 / 在线 zero-width 检查器**：无 U+200B（字符级检查；对齐 architecture.md §12「出口干净度」未竟手法，本批一并补做） |
| (g) | 分区展示、条数正确、`Appendo_` 命名拒绝（§8.3、§5.3） | 造 1 主题＋1 整文件归档；尝试命名 `appendo`、`Appendo_日记` | 分区标题「主题文档」「归档」、排列序＝主题在前；条数与详情一致；两个保留字命名被拒且 toast 字面正确 | `run-as ls …/files/topics/` 与 `run-as ls …/files/`（归档）目录内容与列表一一对应 |
| (h) | 退出多选后交互不变、深色可读、预览正确（§7.2/§7.4/§8.6） | 退出多选后逐项复测长按复制 / 滑动删除 / 点击编辑；深色逐屏走查全部新界面；多选态长按预览（含多行 / 特殊行条目） | 既有交互行为不变；深色无不可读文字 / 浅色残块；预览全文＝条目原文、预览内「复制」可用 | 预览内复制 → 粘贴做 (f) 同款字符级零宽检查 |
| (i) | 提醒联动：取消（闹钟＋sidecar）、附注、不复活（§8.2） | 2 条各设 +2 分钟未触发提醒 → 移入；恢复主题后等过原触发时刻 | 成功 toast 含「，已取消 2 条提醒」；徽标即刻消失；恢复后提醒不复活（通知不响） | `adb shell dumpsys alarm` 移入前后对照（该条 PendingIntent 消失）＋`run-as cat shared_prefs/appendo_reminders.xml` 前后对照（`reminder_<ts>` key 消失） |
| (j) | 删除语义：非空禁删、空可删、恢复→清空→删除全流程（§8.3、§6.5） | 非空主题右滑（应无响应）→「恢复→清空→右滑删除」全流程；另建空主题直接右滑删 | 非空主题无可滑动反馈；全流程数据无损；空主题删除确认（标题红）→「已删除」 | `run-as ls …/files/topics/` 无该文件＋`run-as cat shared_prefs/appendo_topics.xml` 无对应 `created_` key |
| (k) | 命名：`Appendo` 变体拒、同名指路、空/非法拒（§5.3、§8.1） | 依次尝试 `Appendo`/`appendO`/`APPENDO_`、同名 `t-k` 与 `T-K`、空名 / 纯空白、`a/b`、`a*b`、51 字符 | 变体与前缀全拒（toast 统一字面）；同名弹指路对话框→「移入该主题」直达确认；空 / 非法 / 超长 toast 正确、对话框保持 | `run-as ls …/files/topics/` 无非法 / 重复主题文件残留 |
| (l) | 移入后复制全部：已移条目不在、格式干净（§6.6/§8.3） | (a) 完成后主界面「复制全部」→ 粘贴 | 已移 3 条不在、其余条目完整、格式干净 | (f) 同款字符级零宽检查 |
| (m) | 移入全部：仅剩文件头、空态与归零正常（§6.3 全删归一化） | 全部条目多选（可配合 (p) 的 200 条批）→ 移入某主题 | 空状态引导文案（需求 20）＋计数归零（需求 14）；重开应用仍空 | `run-as cat` 主文件输出**恰为 `# Appendo\n`**（归一化断言：无残留 `---` / 空行） |
| (n) | 多选中新分享插入：选中集保持（§7.3） | 多选勾 2 条 → `adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "新条目" -n com.yiyue31.android.appendo/.ShareReceiverActivity` | 新条目插入列表顶部、原 2 条勾选保持不错位、计数 +1 | （佐证）logcat ShareReceiver 写入成功日志；主文件 `cat` 含新条 |
| (o) | N=0：全部移除、反馈成功无歧义（§6.4/§9） | 构造＝移入 N 条 → 主题恢复（追加回）→ 再移入同批（与 (d) 同构造） | toast「N 条已在该主题中，已从当前列表移除」——成功语义、无失败读感；主列表清空 | `cat …/topics/`（条数不变）＋主文件无该批 |
| (p) | 200 条不 ANR、可感知进行 / 完成（§8.1/§6.3） | 构造：`for i in $(seq 1 200); do adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "批量条目 $i" -n com.yiyue31.android.appendo/.ShareReceiverActivity; done`（间隔片刻防丢）→ 全选移入 | 「移入中…」可见、完成 toast「已移入 200 条」、全程无冻结 / 无 ANR 对话框 | ANR 判据：`adb logcat -d \| grep -i "ANR in com.yiyue31.android.appendo"` 无记录（或 `adb shell dumpsys activity` 无 ANR 历史）；`cat …/topics/` 目标恰 200 条 |

---

## 12. 风险与已知限制（README 声明项汇总）

| # | 项 | 口径 | 处置 |
|---|---|---|---|
| R1 | 移入即取消提醒；恢复后不复活 | 已知限制 | README 声明（84(c)；确认对话框内也已前置告知） |
| R2 | 「复制全部」不再含已移条目；全景取用须按主题分别复制 | 有意取舍 | README 注明（85(e)） |
| R3 | 主题文档只读、不支持删除 / 移出单条；官方清理路径 = 恢复清空后删除 | 已知限制 | README 声明（85(d)）；单条移出登记 TD-028 |
| R4 | 主题文档重命名不支持 | 已知限制 | TD-027 |
| R5 | 主题文档存应用私有目录：无云备份条件下**卸载即失去**（与归档一致）；不经文件管理器直取；有 Android 云备份时随备份迁移（allowBackup=true，决策②） | 耐久不对称如实披露 | README「换机与备份」小节补记（85(b)） |
| R6 | 主题文档不经「打开文件」菜单 / 外部查看器暴露（ZWSP 直出问题，同 §3.3"打开文件"出口的既有约束） | 低频场景不支持 | README 交互说明补记；内容出口走应用内复制（85） |
| R7 | 多选无全选 / 筛选；多选态无长按复制（由预览内「复制」承接） | 有意取舍 | README 交互说明；TD-029 |
| R8 | 同 key（同 ts 同 content）双条历史数据：勾选态联动、移入一次清双条；**同 ts 不同 content 的未选条目仍在主列表时，该 ts 的提醒不取消**（§8.2 连坐防护——sidecar key=ts 覆盖同 ts 全部条目，提醒留给仍可见的那条） | 理论边界（同 key 双条经归档恢复去重实际不可达；同秒碰撞真实存在于 v1.0.x 秒级旧数据） | 设计注明（§7.3/§8.2），README 不展开 |
| R9 | SAF 授权失效期间移入：源读失败 → 干净失败 + TD-021 句式提示；主界面既有失效对话框引导重选/回退 | 既有自愈机制覆盖 | §6.4 / §9，无需新路径 |
| R10 | 「归档」（立即执行）与「归档与主题」（跳转列表）的区别 | 既有 80(b) 注明义务随更名更新 | README |
| R11 | `atomicWrite` rename 失败退化为直接覆写（`FileBasedMarkdownFile.kt:151-155` 既有路径）且覆写中断 → 目标文件截断：**重试仅保证所选条目收敛**（重试以锁内新快照重写目标），**目标原有条目尾部可能丢失**——既有系统性风险（所有 FileBased 全文件写共有，非本特性新增），触发需"rename 失败＋覆写中断"双重罕见条件 | 既有系统性风险如实披露（§6.4 失败态表） | 已知限制注明（README「已知约束」或随 TD 登记）；本特性不修复（勿扩大改动面），登记候选后续批次 |

---

## 13. 开放问题（须 Planner / 用户裁决，设计不自行裁定）

| # | 问题 | 倾向性方案 |
|---|---|---|
| O1 | **89(c) 验收措辞校正**：specs 原文"构造目标写入失败（存储满/URI 失效）"，但方案 A 下目标为私有目录文件——URI 失效不适用、存储满不可构造。建议按 89 尾注授权将验收措辞校正为"`run-as chmod` 只读目录受控复现 + 单测故障注入参数"（§10.1） | 接受 chmod 受控复现作为 89(c) 真机手段；单测已覆盖分支语义 |
| O2 | **N=0 成功文案句式**：specs 88 原文成功句式为「已移入 N 条（M 条目标已存在）」，本设计建议 (i) M>0 时对齐第 15 章句式族「（跳过 M 条已存在）」、(ii) N=0 时改直陈句式「M 条已在该主题中，已从当前列表移除」（87 修订"无歧义"要求下"已移入 0 条"有失败读感） | 按设计建议定稿（§9.1 已注明对齐理由）；若用户坚持 specs 原文字面，N=0 分支至少需单独句式，请裁定 |
| O3 | **分区排列顺序**：主题文档分区在前、归档分区在后（按使用频率：主题是活跃工作区、归档是历史快照） | 主题在前（设计推荐，低风险）；如用户心智上"归档为主、主题为辅"可对调，一行改动 |

其余决策点（D1 存储方案 A、D2 组合事务形状、D3 多选入口、空主题删除手势、失败重试对话框保持态、主题详情独立标题）均为设计定稿权内、已在正文给出优劣与推荐，评审时可推翻。

**裁定结果（2026-11-01，用户均采纳推荐）**：O1 ✅ 接受 `run-as chmod` 受控复现＋单测注入（89(c) 措辞按 89 尾注授权校正）；O2 ✅ M>0 对齐「（跳过 M 条已存在）」句式族、N=0 专用直陈句式；O3 ✅ 主题分区在前。§9.1/§10.1/§8.3 按此定稿，设计评审可继续挑战具体实现与字面。

---

## 14. 修订二·模块与改动面（主题一等公民 IA 分家＋归档清空合并，方案甲，2026-11-04）

> 权威输入：`docs/specs.md` 16.A 修订二定稿版（条文 90~94＋需求 12/13/16/17/48/80(b) 就地注），冲突以 specs 为准。三视角评审待设计项（C5/C6/C8/U6/A6/A8/P3-13/U1/A4）逐项收敛于 §15~§17，章尾附条文落点对照表。**2026-11-04 三视角评审修订（必修 A-1/E-1/T-1/T-2＋应补/后置项）已逐条落实于本章**（明细见修订记录行）。§1~§13 为合并页方案的历史定稿，与本章冲突处以本章为准（如 §8.3 分区列表由 §15.1/§15.2 取代）。

### 14.1 文件改动面

新增（`Appendo/src/main/java/com/yiyue31/android/appendo/`）：

| 文件 | 层 | 职责与量级 |
|---|---|---|
| `ui/TopicListScreen.kt` | ui | 主题独立页（~260 行）：签名 `TopicListScreen(fileRepository, onNavigateBack, onTopicClick: (File) -> Unit)`；列表（`TopicRepository.listTopics` 直供，createdAt 倒序）、TopicCard（彩色首字标）、页内新建状态机（复用 `NewTopicNameDialog`＋同名提示框）、页级空态、空主题删除与左滑恢复（零改动消费 `RestoreConfirmDialog`） |
| `ui/SwipeCard.kt` | ui | 滑动手势骨架（~110 行）：自 `ArchiveCard` 私有件下沉为共用组件（offset 状态/120dp 阈值/方向背景指示「删除」红「追加」绿/禁删钳 0/首卡 elevation 动画；行为回调注入；**指示字面与配色随骨架下沉、不参数化**——两页语义同构（删除红/恢复绿），参数化徒增接口面；既有恒真 `AnimatedVisibility` 包装随重构丢弃，列入机械重构范围——A-6 后置项顺手落），`ArchiveCard` 与 `TopicCard` 均为薄包装（A8 裁定见 §15.1）；**`performVibration` 随骨架下沉本文件为 internal 共享**（A-4/E-3 三方收敛：三消费方＝SwipeCard 内两包装卡＋`EntryListScreen`＋`MainScreen` 既有调用点；两旧私有拷贝 `EntryListScreen.kt:385`、`MainScreen.kt:1666` 删除改引用） |
| `ui/TopicBadge.kt` | ui | 首字标色板与纯函数（~80 行，全 JVM 可测，§17.1）：`deriveBadgePalette(primary: Color, surface: Color)`（**双参纯函数，T-5 定稿**：自 primary HSL 派生 8 色、判深浅用 `surface.luminance()`，RGB↔HSL 纯 Kotlin 实现不用 android.graphics）、`contentColorFor`（白/黑自适应）、`badgeIndex`（全名散列）、`firstGrapheme`（BreakIterator 取用户可感知首字） |
| 测试 `Appendo/src/test/.../ui/TopicBadgeTest.kt` | test | ~90 行（§17.1） |
| 测试 `Appendo/src/test/.../ui/ArchiveOutcomeMappingTest.kt` | test | M1 决策映射纯函数测试（~40 行，§17.1；A-3/T-3 定稿新增） |

修改：

| 文件 | 改动与量级 |
|---|---|
| `ui/MainScreen.kt` | 净 +20（+110/−90）：① 顶栏浏览态增「主题」入口图标（多选图标左侧，0 条仍显示）；② 新增参数 `onNavigateToTopics: () -> Unit = {}`；③ 第四格「清空」（Box＋combinedClickable 长按＋红描边，:718-759）→ `OutlinedButton`「归档」点击触发（与「复制全部/分享全部」同款形态）；④ 旧清空确认框（:1025-1088）→ 二选一对话框（§15.3）；⑤ `archiveFile`（:1847-1886）重构：内部 toast 移除、返回值 `Boolean` → `sealed ArchiveOutcome`（`Archived(file)`/`NothingToArchive`/`Failed`；sealed 类落 **MainScreen.kt 顶层**，A-2），**writeAll 写盘段逐字节等价**（A-3/T-3：等价宣称限于写盘段——空内容分支行为变更：现状返 true 继续清空 → 新 `NothingToArchive` **不清空**＋提示 toast，入口守卫配套的防呆改进，R15 陈旧窗口下可达），**删除未使用的 `fileRepository` 参数**（E-1）；⑥ 菜单：删「归档」项（:575-587）、「归档与主题」→「归档管理」（:589）、「主题」→「外观」（:617，含 `ThemePickerDialog` 标题 :1710）；⑦ `executeMove` 成功 toast 追加首次指路后缀（§15.4） |
| `ui/ArchiveListScreen.kt` | −150 行：主题分区（:210-267）/主题删除框（:373-430）/topics 状态与 `TopicRepository` 依赖移除；标题回「归档管理」（:151）；整页空态改归档单态；`ArchiveCard` 手势段改消费 `SwipeCard`（−60 行机械重构）；签名 `onFileClick(File, kind)` 简化为 `onArchiveClick(File)`（kind 恒归档；`ArchiveEntryKind` 枚举定义留在本文件——包级可见不变，MainActivity/TopicListScreen 照旧 import，零迁移；A-5/E-5：`restoreKind` 状态随分家移除（kind 恒归档化简的死代码），KDoc「唯一产出源」句改写为主题页/归档页双源——留驻决定不变） |
| `MainActivity.kt` | +15 行：`composable("topics")` 路由＋`onNavigateToTopics` 接线；`archive_list` 调用点随签名简化 |
| `ui/Microcopy.kt` | +28/−10 行（§16 定稿表） |
| `README.md` / `CHANGELOG.md` | §16.4 义务清单 |
| `ui/TopicMoveDialogs.kt` | 仅 :108 副文案模板换序对齐 `topicCardSubtitle`（「N 条 · 创建时间」条数在前，E-2；现状为时间在前的内联直写模板）；其余零改动（`NewTopicNameDialog` 原样复用＝90「复用 83 修订校验链」的落点） |
| `data/TopicRepository.kt` | ＋~10 行：`consumeFirstMoveGuide()`（A-1 改道，§14.3；`TopicRepositoryTest` 相应扩展见 §17.1）；既有方法与存储协议零改动 |
| `util/*` / `reminder/*` | **零改动** |

### 14.2 路由图（+topics）

```
main ──顶栏「主题」图标（浏览态常驻，90）──▶ topics ──点卡片──▶ archive_detail/{filePath}?kind=topic
  └──菜单「归档管理」（91 回名）──▶ archive_list ──点卡片──▶ archive_detail/{filePath}（kind 缺省＝归档）
```

`archive_detail` 路由与 `kind` 解析零改动（§8.5 既有实现直接服务两页）；菜单「归档」项移除（93 入口唯一化到网格）。

### 14.3 数据层改动面声明（「零改动」口径更正，A-1）

- **原草案「ui 层 `MoveGuidePrefs` 直写 `appendo_topics` SP」弃**（A-1：ui 直写 data 域 SP 文件违反分层——util ← data ← ui 依赖方向；该新增文件已从 §14.1 新增表移除，§18 原未登记此分层风险，改道后亦无需登记——风险不存在）。改道为 **`TopicRepository` 新增 `consumeFirstMoveGuide(): Boolean`**（~10 行：经 Q2 构造器注入的 `prefs` 读 `first_move_guide_shown`，未展示过则置位 `commit()` 后返回 true、已展示返 false；与 `created_*` 同 SP 文件 `appendo_topics`、不新建 SP 文件——主题域元数据集中；同 `commit()` 纪律 TD-024；Q2 注入口＝内存版假 SharedPreferences，JVM 可测，§17.1）。键仍是纯 UI 引导元数据，但读写通道归 data 层——分层干净且 SP 碎片化照旧避免。
- **「数据层零改动」口径更正**（specs 16.A 字面容纳）：data 目录**新增一个与协议无关的旗标方法**，**事务与协议语义零改动**——`topics/` 协议、`TopicRepository` 既有方法与 `created_*` 键、`TopicMoveTransaction`、`EntryKey`/`deleteEntries` 全部维持已实现语义；util/ reminder/ 两目录零文件增删改。

---

## 15. 修订二·交互设计

### 15.1 主题页与顶栏入口（90）

**顶栏入口（浏览态）**：actions 排列 `[主题][多选][菜单]`——主题图标在多选图标左侧（浏览导航类动作靠左、工具靠右）；浏览态常驻，**主文件 0 条目仍显示**（90），此时排列退化为 `[主题][菜单]`（多选图标现状 0 条隐藏行为不变）。图标＝`Icons.Default.List`（material-icons-core 依赖面内、零新依赖；语义更贴切的 Label 图标需新增 material-icons-extended 依赖，按 CLAUDE.md 新依赖须用户确认，本批不引入）。contentDescription「主题」（沿「多选」「菜单」直写先例）。

**多选态显隐（P3-13 定稿）**：**隐藏**——多选态顶栏整体切换为 contextual bar（§7.2 既有形态「已选 N 条」＋「移入」），浏览导航语境不存在，主题入口暂不可达，退出多选即恢复。理由：contextual bar 只服务选择这一个主任务（P1），塞入第三个动作稀释主任务；多选移入完成后自动退出多选，主题入口随即可达（与 §15.4 首次指路恰好衔接）。94(a) 断言此行为。

**主题页（`topics` 路由）**：

| 项 | 设计 |
|---|---|
| 顶栏 | 标题「主题」（primary SemiBold，沿 ArchiveListScreen 先例）；返回箭头；actions＝`Icons.Default.AddCircle`「新建主题」（常驻含空态；B2 裁定「有」） |
| 列表 | `LazyColumn`，`listTopics()` 直供（createdAt 倒序＝85(c) 维持）；进入加载（`LaunchedEffect(Unit)`＋`Dispatchers.IO`，「加载中...」沿先例直写）与操作后重载（**重载亦经 `Dispatchers.IO`**——E-4：修正 ArchiveListScreen 既有主线程重载路径，不沿袭） |
| TopicCard | `SwipeCard` 骨架＋内容行：**40dp 彩色圆形首字标**（容器色＝`topicBadgeColors(displayName)`、字＝`firstGrapheme(displayName)` titleLarge SemiBold 居中）＋主题名（titleMedium Medium）＋小字「**N 条 · yyyy-MM-dd HH:mm**」（条数在前，90；`onSurfaceVariant`，P8）。空主题小字＝「空」（`TOPIC_EMPTY_SUBTITLE` 平移，右滑删除可发现性补位） |
| 交互 | 点击 → 主题详情（`archive_detail?kind=topic`，85(d) 只读限制随 90 平移）；长按 → 复制全文（parse＋formatForExport 同款，出口铁律）；左滑 → 恢复（86，`RestoreConfirmDialog`＋`TOPIC_RESTORE_NOTE` 平移消费）；空主题（0 条）右滑 → 删除（85(a)，`DLG_TOPIC_DELETE_TITLE`/`dlgTopicDeleteBody` 平移消费；非空右滑钳 0 零响应——手势语义随 SwipeCard 参数化下沉） |
| 页级空态 | 无任何主题时：`TOPIC_PAGE_EMPTY_TITLE`（titleMedium/onSurfaceVariant）＋`TOPIC_PAGE_EMPTY_BODY`（bodyMedium 弱化）——文案含新建引导（90，字面见 §16.1） |
| 新建流程 | 点「新建主题」→ 复用 `NewTopicNameDialog`（形态/聚焦/「创建」按钮零改动）→ `createEmptyTopic`（83 修订校验链原子复用；各校验失败分支 toast 沿 §9.2 既有常量、框保持）；`Created` → 关框＋重载＋toast「已创建」；`WriteFailed` → toast「创建失败」、框保持（输入保留可重试）；`NameExists` → 同名提示分支（下条） |

**C5 同名分支定稿（94(b) 断言：提示非指路）**：主题页语境无移入动作，不沿用 §8.1 对话框 2b 的「是否移入该主题」指路。形态＝独立提示对话框，页内状态机 `NONE→NAMING→SAME_NAME_PROMPT` 三态（沿移入流程状态机模式；提示框替换命名框显示，关闭后回命名框且**输入保留**）：

- 标题＝`DLG_SAME_NAME_TITLE`「已存在同名主题」（与移入流程同字面同常量复用；默认色——提示类非破坏）；
- 正文＝`dlgTopicPageSameNameBody(x)`「已存在同名主题“X”，请换一个名称。」；
- 按钮「知道了」（primary）、点外/系统返回 → 均回命名框（输入保留，可改名）；
- **无「移入该主题」按钮**（此语境无移入可言，94(b) 断言点）。

**首字标规则（90＋U6＋52 定稿，全落 `ui/TopicBadge.kt` 纯函数）**：

| 规则 | 定稿 |
|---|---|
| 色板（90「固定色板」） | 运行时派生 8 色，签名定稿 **`deriveBadgePalette(primary: Color, surface: Color)` 双参纯函数**（T-5：判深浅用 `surface.luminance() ≥ 0.5`，不另传模式枚举）：HSL 色相旋转 45°×i（i=0..7）、饱和度钳制 [0.42, 0.78]、明度固定带（浅色 0.36／深色 0.44）——**零写死色值**（P7 字面满足），深浅随双色板自动切换；派生是纯函数 ⇒ 同名同色、重启不变（确定性，90；「同名同色」在**同主题模式内**成立，明暗切换整体换带见 R17 披露） |
| 散列（U6 定稿） | `badgeIndex(name) = floorMod(name.hashCode(), 8)`——**全名单因子散列**（非首字主导）：同首字不同名均匀散开；「首字＋名字双因子」统计上不改变均匀性、零收益，弃；8 色板下不同名残余撞色**允许**（90 明令），靠主题名区分 |
| 对比度（52 量化门槛由本设计定稿：文字 ≥4.5:1） | 首字文字色＝白/黑二选一，取对比度较大者——两对比度曲线交点即数学下界 **4.583:1**，任意容器色恒 ≥4.5:1，无须逐色人工校验（§17.1 测试仍钉死） |
| 首字取法（90：用户可感知字符、emoji 整体一字） | `firstGrapheme(name)`＝`java.text.BreakIterator.getCharacterInstance()` 首边界（grapheme cluster：surrogate 对/ZWJ 序列/国旗旗对整体一字）；空名防御返回「？」 |

**A8 裁定（TopicCard 组件策略）**：**抽公共 `SwipeCard` 骨架共用**，`ArchiveCard`/`TopicCard` 做薄布局包装。取舍：独立实现＝复制 ~60 行手势件（两份阈值/钳制/指示逻辑必然分叉，TD-015 同型隐患），抽取代价＝ArchiveListScreen 一次机械重构＋回归抽查（94(c)）。两处手势语义完全同构（右删/左恢复/禁删钳 0）、两页长期并存，抽取净收益为正——推荐并定稿抽取；R16 登记重构回归风险。

### 15.2 归档页回退与分家（91）

| 项 | 设计 |
|---|---|
| 合并页退役 | `ArchiveListScreen` 主题分区、主题删除对话框、topics 状态与 `TopicRepository` 依赖整体移除；页内仅整文件归档（`Appendo_*.md`）——85 主体「分区展示」反转后的单区列表，无分区标题 |
| 回名 | 页标题与菜单列表项均回「**归档管理**」（需求 17 固化名；85 修订注反转） |
| 空态（C8） | 归档为空 → `ARCHIVE_EMPTY_TITLE`「暂无归档」／`ARCHIVE_EMPTY_BODY`「归档文件会显示在这里」（沿合并前旧文案口径）；`ARCHIVE_TOPIC_EMPTY_*` 退役 |
| 详情与手势 | `onArchiveClick(File)` → `archive_detail/{filePath}`（kind 缺省＝归档，路由零改动）；归档卡右删/左恢复经 `SwipeCard` 重构后行为不变（94(c) 抽查） |
| 菜单 | 「归档」项移除（93 入口唯一化到网格）；「归档管理」保留（列表/恢复入口，93 明令） |
| 85(e) 尾句 | 「复制全部」不含已移条目取舍注维持（README，§16.4） |

### 15.3 网格「归档」二选一（93＝需求 16 修订；X 方案；需求 12/13 就地注落点）

**第四格（A6＋93 定稿）**：「清空（红色描边＋长按触发＋点击 toast 提示）」退役，改为 **`OutlinedButton`「归档」**——与「复制全部/分享全部」完全同款形态（primary 0.5α 描边＋primary 文字、RoundedCornerShape(16dp)、56dp），**点击**触发；长按语义、振动、`onLongClickLabel`、「长按以清空内容」提示 toast 全部退役。颜色归类（78）：归档＝备份非破坏 → primary（78(b)）；破坏性警示不消失而是**下沉到对话框内「归档并清空」按钮的 error 红**（78(a) 明列「清空」属破坏类）。

**触发守卫**：主列表 0 条目时点击第四格 → toast「暂无内容可归档」（既有字面常量化）＋**不开对话框**（空列表无可归档亦无可清空；同时消除旧路径 0 条时「暂无内容可归档＋已清空」双 toast 冗余，P9）。

**二选一对话框（C6 定稿）**：

- **形态＝正文区纵向两按钮并列**（一次点击直达；弃预选式——两路径是平级动作选择而非配置项，先选后确认二次点击冗余，P1/P4；弃横向三按钮排布——「取消＋两路径」横排在 320dp/分屏宽度下溢出风险高）。
- 结构：标题 `DLG_ARCHIVE_TITLE`「归档」（默认色）→ 正文 `dlgArchiveBody(n)`「将把已收集的 N 条全部备份为归档文件。」（N＝entryCount 实时值，88 数字透明口径）→ 正文下方纵排：
  - **「归档并清空」**：`Button` 填充样式（containerColor＝`colorScheme.error`、contentColor＝`onError`，78(a)），全宽；**默认焦点**——对话框打开经 FocusRequester 将初始焦点落于本按钮（触屏无感知差异；物理键盘 Enter 直达、TalkBack 首达，93 明令；该路径恒先备份，数据非破坏，不违背「破坏性动作不设默认焦点」惯例的精神）；
  - **「只归档，保留列表」**：`OutlinedButton`（primary 描边＋文字，78(b)），全宽；
  - action 区：「取消」（dismiss 槽；点外/系统返回同义）——取消键保留（93）。
- **两路径语义与反馈**（均经重构后的 `archiveFile → ArchiveOutcome` 组合；**writeAll 写盘段与现状逐字节等价**——A-3/T-3：等价宣称限于写盘段，空内容分支行为变更见下表 `NothingToArchive` 格；执行在 `Dispatchers.IO`——A-9：系对旧框主线程 IO 的改进，非沿袭；对话框为一次性动作、失败不设重试态——沿旧清空框处置；两段各以 `runCatching` 包裹的异常兜底见下表末行 E-1）：

| 路径 | 语义 | onComplete 等价动作（A-2：现状 `onComplete` lambda 在成功尾部调用，重构后按路径等价落位） | 成功反馈（C6 双 toast 归并定稿） | 失败反馈 |
|---|---|---|---|---|
| 归档并清空 | ＝原「清空」：`Archived` → `clear()` → setFileLastModified＋计数清零＋**取消全部提醒**（需求 63 联动保持：cancel 全部闹钟＋`removeAll()`）；需求 13 先备份后清空顺序不变 | 重同步三状态（`useSAF`/`fileUri`/`defaultFile`，沿现状 lambda 语义）＋`clearFileLastModified()`＋`refreshEntryCount()`（承接原 `entryCount = 0; entries = emptyList()` 计数清零）＋取消全部提醒 | **单条 toast**：`toastArchivedAndCleared(name)`「已归档到 Appendo_….md，已清空」——现状 `archiveFile` 内「已归档到: name」＋外层「已清空」两条串联**归并为一条**（逗号附注句式，同 `removedRemindersSuffix` 拼接先例；备份去向＋清空结果一句说完，信息零丢失，P4） | `Failed` → toast「归档失败，未清空」＋**不得清空**（TD-017 传承，94(d) 断言）；`clear()` 失败 → toast「清空失败（归档已保存）」（明示备份安全）；`NothingToArchive` → 「暂无内容可归档」**且不清空**（A-3/T-3 行为变更：现状此分支返 true 继续清空——入口守卫配套的防呆改进，R15 陈旧窗口下可达） |
| 只归档，保留列表 | ＝原菜单「归档」：`Archived` 即完成；主列表/计数/提醒不动（需求 12 快照语义） | 重同步三状态（`useSAF`/`fileUri`/`defaultFile`）；`clearFileLastModified()` **丢弃**——主文件未动、逻辑 mtime 无需失效（`remove` 不存在 key 本为无害 no-op，仍按语义正确性弃调——理由注明） | 单条 toast：`toastArchivedTo(name)`「已归档到 Appendo_….md」（现状字面去 ASCII 冒号后常量化） | `Failed` → toast「归档失败」；`NothingToArchive` → 「暂无内容可归档」无动作（同左行为变更，§17.1 决策映射两路径钉死） |
| 异常兜底（两路径共有，E-1） | 两段各以 `runCatching` 包裹：**归档段异常 → 归 `Failed`**（异常不上抛 UI，按路径映射既有失败文案）；**清空段异常 → 清空失败文案**（归档已保存语义）——不重蹈旧框 catch-all「操作失败」（:1075，§16.3 退役） | —（异常路径不触发 onComplete 等价动作） | — | 归档段异常 → 「归档失败，未清空」／「归档失败」（按路径）；清空段异常 → 「清空失败（归档已保存）」 |

### 15.4 移入去向文案与首次指路（92）

- **去向行更正**：`dlgMoveConfirmBody` 第二行改为 specs 92 逐字——「移入后这些条目将从主列表消失，可在顶栏『主题』入口查看。」（替换「…可在「归档与主题」的主题分区查看」，§16.2）。
- **首次指路 toast（U1 定稿）**：首次**有效移入**（`moved > 0` 的 `Success`）后，成功 toast 末尾**拼接后缀** `firstMoveGuideSuffix`「，可在顶栏『主题』查看」——不弹第二条 toast（单 toast 原则，P4）。拼接序固定：基础文案 → `removedRemindersSuffix(k)`（K>0 时）→ `firstMoveGuideSuffix`（首次时）。一次性旗标 `first_move_guide_shown` 在 `executeMove` 的 IO 段内经 `topicRepository.consumeFirstMoveGuide()` 读后置位一次完成（A-1 改道：MainScreen 既有 `topicRepository` 实例（:193）直调，未展示过则置位 `commit()` 并返回 true；Q2 注入口 JVM 可测，§17.1）；N=0 成功（moved=0）**不调用 consume**——不消耗旗标也不展示（无条目离开主列表，无指路必要；门控语义随 §17.1 M1 决策映射钉死）。
- **「外观」更名联动（92／需求 48 就地注）**：菜单项字面「主题」→「外观」＋`ThemePickerDialog` 标题同步「外观」（三态功能与持久化语义零变化；可感知显式变更——README/CHANGELOG 注明，§16.4）。

---

## 16. 修订二·微文案定稿表（新增/修改全落 `Microcopy.kt`；直写项注明落点）

### 16.1 新增

| 常量 / 函数 | 定稿字面 | 来源 |
|---|---|---|
| `BTN_CELL_ARCHIVE` | 归档 | 93 第四格 |
| `DLG_ARCHIVE_TITLE` | 归档 | 93 |
| `dlgArchiveBody(n)` | 将把已收集的 $n 条全部备份为归档文件。 | 93 正文 |
| `BTN_ARCHIVE_AND_CLEAR` | 归档并清空 | 93（specs 逐字） |
| `BTN_ARCHIVE_ONLY` | 只归档，保留列表 | 93（specs 逐字） |
| `toastArchivedTo(name)` | 已归档到 $name | 93 只归档成功（现状字面去「: 」常量化） |
| `toastArchivedAndCleared(name)` | 已归档到 $name，已清空 | 93 归档并清空成功（C6 归并） |
| `TOAST_ARCHIVE_FAILED` | 归档失败 | 93 只归档失败 |
| `TOAST_ARCHIVE_FAILED_CLEAR_KEPT` | 归档失败，未清空 | 93＋TD-017 |
| `TOAST_CLEAR_FAILED_ARCHIVED` | 清空失败（归档已保存） | 93 清空段失败 |
| `TOAST_NOTHING_TO_ARCHIVE` | 暂无内容可归档 | 93 守卫（现状字面常量化） |
| `TOPIC_PAGE_EMPTY_TITLE` | 暂无主题 | 90 页级空态 |
| `TOPIC_PAGE_EMPTY_BODY` | 点击右上角“＋”新建主题，或在主界面多选条目后「移入主题」 | 90 空态引导 |
| `dlgTopicPageSameNameBody(x)` | 已存在同名主题“$x”，请换一个名称。 | 90／C5 |
| `BTN_GOT_IT` | 知道了 | C5 提示框按钮 |
| `TOAST_TOPIC_CREATED` | 已创建 | 90 新建成功 |
| `TOAST_TOPIC_CREATE_FAILED` | 创建失败 | 90 新建失败 |
| `firstMoveGuideSuffix` | ，可在顶栏『主题』查看 | 92／U1 首次指路（拼接后缀） |
| `ARCHIVE_EMPTY_TITLE` / `ARCHIVE_EMPTY_BODY` | 暂无归档 / 归档文件会显示在这里 | 91／C8 归档页空态 |

直写项（沿各文件既有惯例不入 Microcopy——对齐「多选」「菜单」contentDescription 与页标题直写先例）：顶栏图标 contentDescription「主题」、主题页标题「主题」、新建按钮 contentDescription「新建主题」、菜单「归档管理」「外观」。

### 16.2 修改

| 常量 / 位置 | 变更 | 来源 |
|---|---|---|
| `dlgMoveConfirmBody` | 第二行 → 「移入后这些条目将从主列表消失，可在顶栏『主题』入口查看。」（specs 逐字） | 92 |
| `topicCardSubtitle` | 签名与序改为 `(count, timeLabel)` → 「$count 条 · $timeLabel」（**条数在前**；唯一消费方＝新 TopicCard） | 90 |
| `ThemePickerDialog` 标题（MainScreen 直写） | 「主题」→「外观」 | 92 |

### 16.3 退役

| 常量 / 字面 | 处置 | 因由 |
|---|---|---|
| `SECTION_TOPICS` / `SECTION_ARCHIVES` | 删除 | 91 分区退役（主题独立成页、归档单区无标题） |
| `ARCHIVE_TOPIC_EMPTY_TITLE` / `ARCHIVE_TOPIC_EMPTY_BODY` | 删除（`ARCHIVE_EMPTY_*` 接替） | 91／C8 两页空态分家 |
| `BTN_CLEAR` | 删除 | 93（第四格与旧清空框均退役，无消费方） |
| 直写「长按以清空内容」/「长按清空」 | 删除 | 93 点击触发 |
| 直写「已归档到: $name」/「已清空」/「已取消清空（归档失败）」/「归档失败: $msg」 | 删除（→ §16.1 归并/替代字面） | C6 双 toast 归并＋`ArchiveOutcome` 重构 |
| 直写「操作失败」（MainScreen:1075）／「清空失败」（:1069）／旧框标题「清空内容」（:1030）与正文（:1037） | 删除 | E-1 异常兜底归 `runCatching` 两段映射文案（catch-all「操作失败」退役）＋93 旧框整体退役 |

### 16.4 句式族对齐与文档义务

- **句式族**（向 §9.1 对齐）：成功＝「已动词＋对象」＋逗号附注链式（已归档到 X，已清空／已移入 N 条，已取消 K 条提醒，可在顶栏『主题』查看）；失败＝「动词失败」＋逗号/括号附注（归档失败，未清空／清空失败（归档已保存））；N/M/K 数字口径沿 §9.1 不变。
- **副文案序对齐（E-2）**：`TopicMoveDialogs` :108 移入目标列表副文案换序为「N 条 · 创建时间」（与 `topicCardSubtitle` 同序、条数在前）——两处消费同句式族；保持内联直写形态不变、不新增常量。
- **README 义务**：① 80(b) 区别说明改写——网格「归档」两选项（归档并清空／只归档，保留列表）与「归档管理」的区别（91 就地注）；② 「外观」更名（48 注，可感知显式变更）；③ 主题页与顶栏入口交互说明（90）；④ 菜单「归档」项移除（93）；⑤ 85(e)「复制全部」取舍注维持。**CHANGELOG 义务**：「主题」→「外观」更名、第四格「清空」→「归档」二选一、主题独立页三条可感知变更。

---

## 17. 修订二·测试与验收映射（94）

### 17.1 新增纯 JVM 测试义务（`--max-workers=1`，沿 §10.2 纪律）

| 测试文件 | 要点 |
|---|---|
| `ui/TopicBadgeTest.kt` | ① **hash 稳定性**：同名恒同 index；`floorMod` 恒界内；String.hashCode 为 JVM 规范定值（重启不变的依据）；② **对比度（T-5 签名）**：`(AppendoLightScheme.primary, AppendoLightScheme.surface)`／`(AppendoDarkScheme.primary, AppendoDarkScheme.surface)` **双色板 (primary, surface) 成对断言**——各派生 8 色，逐色以 WCAG 相对亮度公式复算，断言 `contentColorFor` 所选文字色对比度 ≥ 4.5:1（**4.583** 数学下界的回归钉死）；③ **首字取法**：普通汉字/字母；surrogate 对 emoji（😀）；ZWJ 序列（👨‍👩‍👧）整体一字；国旗旗对；BMP 外生僻字（𠀀）；空串防御返回「？」；④ **派生确定性**：同 (primary, surface) 参数对同色板（同名同色前提） |
| `data/TopicRepositoryTest.kt` 扩展（A-1 改道） | `consumeFirstMoveGuide()` 三条（Q2 注入口＝内存版假 SharedPreferences，不引入 Robolectric）：① 未展示 → 置位并返 true（断言假 SP 已 commit `first_move_guide_shown=true`）；② 已展示 → 返 false 且不再写；③ 「N=0 不消耗不展示」＝调用方门控（`moved > 0` 方调 consume，§15.4）——方法本身不含 N，该语义随下行 M1 决策映射钉死 |
| `ui/ArchiveOutcomeMappingTest.kt`（M1 决策映射纯函数，A-3/T-3） | 决策函数（MainScreen.kt 顶层 internal：`ArchiveOutcome × 路径 → toast 字面／是否 clear／是否取消提醒`）全组合钉死：① `Archived`×归档并清空 → `toastArchivedAndCleared`＋clear＋取消全部提醒；② `Archived`×只归档 → `toastArchivedTo`＋不 clear＋不动提醒；③ `Failed`×两路径 → 两失败字面＋均不 clear；④ `NothingToArchive`×两路径 → 「暂无内容可归档」＋**均不 clear、无其他动作**（行为变更两路径钉死）；⑤ 清空段异常 → 「清空失败（归档已保存）」（E-1 映射） |

**M1 两路径与 UI 层可测边界（A-3/T-3 修订）**：归档并清空／只归档的**编排**在 MainScreen 点击处理器（UI 层），编排本身无纯 JVM 测试——语义保障＝① `archiveFile` **writeAll 写盘段**重构前后逐字节等价（`ArchiveOutcome` 只改返回通道、去内部 toast；空内容分支行为变更为 A-3/T-3 定稿改进，不在等价范围内）；② `clear()`/`atomicWrite` 原语行为既有单测覆盖；③ **决策映射提为 MainScreen.kt 顶层 internal 纯函数并新增上表测试**（编排仍不可测，但「结果 → 反馈/副作用」决策可测；不动 util/data，零改动口径不破）；④ 94(d) 真机断言（含 TD-017 顺序配方，§17.3）；⑤ **质量门追加（T-10）：`archiveFile` 重构 git diff 逐行核对清单**——写盘段零语义变更的逐行人工复核（toast 行删除、`fileRepository` 参数删除、返回通道改写逐项打勾，核对记录随 PR 附上）。UI 组件（TopicCard/SwipeCard/二选一框）无 Robolectric（TD-003），走真机走查。

### 17.2 94(a)~(f) 逐项映射（构造／判据／证据层级）

| 项 | 构造 | 判据 | 证据层级 |
|---|---|---|---|
| (a) 顶栏入口 | 主文件清空至 0 条冷启；再造 3 条进多选 | 0 条时主题图标在、进入正常；多选态无主题图标、退出恢复；排列 `[主题][多选][菜单]` | 真机人工 |
| (b) 主题页 | 建主题 2 个（其一空）＋重启＋尝试同名/非法命名/长按复制 | 首字标同名同色且重启不变、过 52 对比度；「N 条 · 创建时间」与详情一致；空主题「空」；页级空态含引导；新建校验链全分支 toast 正确；**同名分支＝提示无「移入该主题」按钮**、取消后输入保留；长按复制全文格式干净（判据同 89(f)：无 ##/---/# Appendo/零宽字符）；**手势三槽（T-6）**：空主题右滑 → 红框删除确认走 `deleteTopic`（文件＋SP key 同清）；非空主题右滑钳 0 **零响应**；左滑恢复对话框含「仍保留」注明行（`TOPIC_RESTORE_NOTE`）＋去重 toast 计数正确 | JVM（§17.1）＋真机走查＋粘贴字符级检查 |
| (c) 分家 | 造 1 主题＋1 整文件归档；打开菜单逐项走查 | 归档页仅归档文件、无分区标题；菜单无「归档」项、「归档管理」在且跳转正确；「外观」＝原「主题」三态（切换＋重启保持）；归档卡右删/左恢复仍可用（SwipeCard 重构回归） | 真机＋既有单测全绿门 |
| (d) X 两路径 | 造 3 条＋1 条设 2 分钟提醒 → 网格「归档」分别走两路径；TD-017 见 §17.3 第三配方 | **归档并清空**：新 `Appendo_*.md` 生成＋主文件恰 `# Appendo\n`＋计数 0＋空态（需求 20）＋提醒全消（徽标/闹钟/sidecar）；**只归档**：副本生成＋主列表/计数/提醒不变；**TD-017**：归档失败路径主文件逐字节不变＋toast「归档失败，未清空」 | 真机＋文件证据（`run-as ls/cat`、`dumpsys alarm`、sidecar xml；提醒取证＝**前后对照＋对象名**（T-9）——`dumpsys alarm` 中本应用 PendingIntent 与 sidecar `reminder_<ts>` key 逐一消失，非仅看徽标 UI） |
| (e) 92 文案 | 打开对话框 3 断字面；首次移入＋二次移入；深色逐屏 | 去向行 specs 逐字；首次成功 toast 含指路后缀、二次起无；深色走查主题页/二选一框/「外观」处（89(h) 口径） | 真机＋字面逐字节核对（T-08 手法） |
| (f) 89(c)(d) 收口 | 见 §17.3；**89(i)(m)(p) 沿用计划 §4.2 对应行执行（T-7），证据命令按存储模式替换**——SAF 主文件用 `adb shell cat <SAF 路径>`（run-as 不可达 SAF 文档）、默认模式用 `run-as cat` | JVM 既有用例＋真机受控复现双轨；**不新增生产注入钩子** | JVM＋真机＋文件证据 |

### 17.3 89(c)(d) 受控复现配方（94(f)＝A4 执行方法；沿 §10.1 风格）

**89(c) 目标写入失败**——§10.1 chmod 配方**原样沿用**（绝对路径＋目录 chmod 500＋probe 预检；注入时点＝选定/新建目标后、确认「移入」前——主题页新建入口出现后目标可能预建，时点判定不变）：

```
adb shell run-as com.yiyue31.android.appendo chmod 500 /storage/emulated/0/Android/data/com.yiyue31.android.appendo/files/topics
adb shell run-as com.yiyue31.android.appendo touch /storage/emulated/0/Android/data/com.yiyue31.android.appendo/files/topics/probe
# 预检：probe 应 Permission denied；确认「移入」→ 断言 toast「移入失败（写入主题文档失败）」、对话框保持变「重试」、
# run-as cat 源文件逐字节原样；chmod 700 恢复后重试成功
```

**89(c) URI 失效场景**（specs 94(f) 点名可真机构造；T-1 修订：原「覆盖安装清授权」配方不可靠——`takePersistableUriPermission` 授权不随 `adb install -r` 一致丢失（TD-021 实况系个别现象而非可复现手段），改**会话中失效构造**）——SAF 主文件、app 前台运行中删除底层文件：

```
adb shell rm /sdcard/<自定义目录>/Appendo.md
# probe（配方可达性前取证）：先验证 SafMarkdownFile.readAllWithStatus 对已删文档返回 failed——
#   logcat 断言失败分类 SourceReadFailed（事务源读失败通道，与 §10.2 ⑧ 同语义）
# → 立即多选移入 → 断言：失败反馈（静默回退/失效提示对话框，以实际 UI 为准）；
#   主列表条目不变（UI 判据）＋目标文件前后一致（干净失败零副作用——topics/ 私有目录 run-as cat 可达）
```

**降级口径（T-1）**：会话中失效法在个别 ROM 上不触发（SAF DocumentsProvider 缓存已删文件）时，降级取证＝JVM §10.2 ⑧（源读失败 → `SourceReadFailed` 零副作用）＋真机以**静默回退/失效对话框**为取证对象（不硬拧 toast 字面）。**specs 94(f)「URI 失效场景可真机构造」措辞随本配方校正为会话中失效法（编排方已登记 specs 微调）。**

**89(d) 软中间态**——§10.1 run-as 手工追加配方**原样沿用**（不执行移入、直接向 `topics/<T>.md` 追加该批条目块 → App 内移入同批 → N=0 直陈句式收敛；等效 In-App 构造＝89(o) 同款连做；`am crash` 仍为加分尝试）。

**TD-017 顺序证明（94(d)）**——归档失败而清空「本可成功」的构造（顺序得证的关键：若顺序颠倒，清空会先得逞）：

```
# 前提：主文件＝SAF 自定义目录（清空走 SAF，不受私有目录 chmod 影响）
adb shell run-as com.yiyue31.android.appendo chmod 500 /storage/emulated/0/Android/data/com.yiyue31.android.appendo/files
adb shell run-as com.yiyue31.android.appendo touch /storage/emulated/0/Android/data/com.yiyue31.android.appendo/files/probe
# 预检（T-2）：probe 应 Permission denied——chmod 生效的先行取证（个别 ROM run-as chmod 静默失效的门闸）
# 归档副本创建于该目录（files/ 根）→ writeAll 失败；网格「归档」→ 归档并清空 →
# 断言：toast「归档失败，未清空」；SAF 主文件内容不变（若未先备份即清空，此处已空）
adb shell run-as com.yiyue31.android.appendo chmod 700 /storage/emulated/0/Android/data/com.yiyue31.android.appendo/files
```

**降级口径（T-2）**：probe 未 Permission denied（`run-as chmod` 静默失效）时，顺序证明降级三轨——① JVM 决策测试（§17.1 M1：`Failed` → 不 clear）；② 代码评审取证「`Failed` 分支先于 `clear()` 返回」（§15.3 两路径表顺序语义）；③ logcat 归档失败日志（`Archive failed` 异常栈）为真机第二证据。

**不加生产钩子**（重申 §10.1 裁定）：DEBUG 钩子/注入参数入口不新增；JVM 层既有 `MoveFaults` 注入（§10.2 ①~⑬）即 89(c)(d) 单测主证据，真机受控复现为第二轨。

### 17.4 执行顺序与状态前置（T-8）

**模式分场**（两场互斥，按用例切换）：

| 场 | 主文件模式 | 服务用例 | 备注 |
|---|---|---|---|
| SAF 场 | 自定义目录（`use_saf=true`，沿 T-10 真机先例） | §17.3 TD-017（前提＝清空走 SAF 不受私有目录 chmod 影响）、URI 失效配方（前提＝SAF 主文件） | 89(i)(m)(p) 若在本场执行，证据命令按 §17.2(f) 行替换 |
| 默认场 | 私有目录 `Appendo.md` | 94(a)~(d) 及 89 常规项 | `run-as` 证据全量可达，文件取证最简 |

**顺序链**（状态前置依赖，乱序会破坏断言）：

1. **94(e) 首次指路须排在任何移入用例之前**——须单独造 `first_move_guide_shown=false` 初始态（全新安装/清数据天然满足；若顺序不可保：默认场 `run-as` 编辑 `appendo_topics.xml` 删该键、SAF 场删 SP 文件后重启重置）。否则先行移入用例置位旗标，指路后缀断言失效。
2. **94(a) 0 条冷启与 89(m) 全移入连做**：先清主文件至 0 条冷启验入口常驻 → 造条目 → 全选移入 → 主文件恰 `# Appendo\n`＋空态（89(m) 断言），一场两用。
3. 89(i)(m)(p) 与 94 各项无状态耦合，可插空执行（沿计划 §4.2 行序，T-7）。

**真机前置备份清单（沿 T-10 先例补齐，T-8）**：主文件（SAF 路径 `adb shell cat` 拉取／默认场 `run-as cat`）＋既有 SP（`appendo.xml`：use_saf/file_uri/theme_mode 等）＋**`files/topics/` 目录整体（逐文件拉取）＋`appendo_topics.xml`（created_* 键与指路旗标）**——主题域两条新持久化面（文档目录＋元数据 SP）首次纳入验收基线，验收后按份还原。

---

## 18. 修订二·风险

| # | 项 | 口径与处置 |
|---|---|---|
| R12 | MainScreen 回归面（本批第四度触碰：顶栏/网格/菜单/清空路径/移入 toast 五点联动） | 缓解：`archiveFile` 重构保 **writeAll 写盘段**逐字节等价（空内容分支行为变更为 A-3/T-3 定稿改进）＋git diff 逐行核对清单（T-10，§17.1）；既有单测全绿门＋94(c)(d)(e) 真机清单；网格其余三格与输入/编辑/提醒路径零触碰 |
| R13 | BreakIterator 老 ICU：API 26/27 上 ZWJ emoji 序列可能拆分（UAX #29 GB11 需较新 ICU） | 观感降级（首字标取序列首段，仍为合法字符、不崩溃；色板/散列不受影响）；已知边界不作缺陷上报；JVM 测试断言 host JDK（新 ICU）行为，老 ROM 降级可接受 |
| R14 | 生僻字/CJK 扩展区字符在部分 ROM 系统字体缺字 → 首字标 tofu 方框 | 渲染依赖系统字体（同主列表正文约束）；已知边界不作缺陷；主题名在卡片正文完整显示 |
| R15 | toast 时序：归并后每路径单条；首次指路为后缀拼接而非第二条（无 ToastUtils 队列堆积）；0 条守卫消除旧「暂无内容可归档＋已清空」双条冗余 | 设计消除（§15.3/§15.4）；残留双 toast 仅存于 0 条守卫后 entryCount 陈旧的 ≤2s 轮询窗口内，自愈 |
| R16 | SwipeCard 抽取重构：两页卡片＋禁删钳制分支的回归风险 | 94(c) 真机抽查归档右删/左恢复＋主题空主题删/非空钳 0；机械重构不改阈值/指示色语义 |
| R17 | 首字标色板与 P7「零写死色」的字面张力；**主题模式切换时 8 色整体跳变**（明暗两套明度带——A-7/E-6/T-5 披露）：「同名同色」在**同主题模式内**成立，跨模式切换色板整体换带（有意为之：随双色板自动适配，非漂移） | 受控例外：色板运行时自 colorScheme (primary, surface) 派生（零写死色值、深浅自动）、文字黑白自适应有 **4.583:1** 数学下界、§17.1 测试钉死——派生逻辑属主题基础设施（与双色板同域），非组件内写死；跳变可接受性由 94(e) 深色走查覆盖 |
| R18 | 「外观」更名＋菜单「归档」项移除的既有用户习惯迁移 | 可感知显式变更，README/CHANGELOG 注明（92/48/80(b) 义务，§16.4）；数据与行为语义零变化 |

---

### 条文落点对照表（specs 16.A 逐条自检）

| 条文 | 落点 |
|---|---|
| 90 主题独立页与首页入口 | §14.1/§14.2（路由）、§15.1（入口/多选显隐 P3-13/卡片/空态 C8/新建含 C5 同名分支/首字标 U6＋52）、§16.1/§16.2、§17.2(a)(b) |
| 91 归档与主题分家 | §15.2、§16.3（退役常量 C8）、§16.4（80(b) 改写）、§17.2(c) |
| 92 移入去向文案与指路 | §15.4（去向行/首次指路 U1——A-1 改道后经 `TopicRepository.consumeFirstMoveGuide`／「外观」更名）、§16.2/§16.4、§17.1（旗标 JVM 测试）、§17.2(e)、§17.4（首次指路顺序前置） |
| 93 归档与清空合并（X） | §15.3（A6 第四格/触发/C6 形态焦点取消/两路径＋onComplete 等价动作 A-2/双 toast 归并/TD-017/菜单移除/E-1 异常兜底）、§16.1/§16.3、§17.1（M1 决策映射）、§17.2(d) |
| 94(a)~(f) 验收 | §17.1（JVM 义务）、§17.2（逐项）、§17.3（89(c)(d) 收口＝A4）、§17.4（执行顺序与状态前置 T-8） |
| 需求 12 注（只归档保留列表） | §15.3 两路径表·只归档行 |
| 需求 13 注（先备份后清空） | §15.3 两路径表·归档并清空行＋§17.3 TD-017 配方 |
| 需求 16 注（第四格改「归档」） | §15.3 第四格段 |
| 需求 17 注（菜单列表项） | §15.2 回名行 |
| 需求 48 注（「外观」更名） | §15.4 更名联动、§16.4 |
| 需求 80(b) 注（README 义务） | §16.4 义务① |
| 数据层零改动（口径更正 A-1：data 目录＋一个与协议无关的旗标方法，事务与协议语义零改动） | §14.3 |

> **修订二状态**：**定稿（2026-11-04 用户确认「按建议执行＋Roger that」）**——三视角评审修订全落（必修 A-1/E-1/T-1/T-2＋收敛/应补/后置共 20 项）；specs 94(f)「URI 失效」措辞随本定稿校正为会话中失效构造（见 §17.3）；阶段3 计划以 §14~§18 为直接输入。
