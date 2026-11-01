# 主题分拣「移入主题」— 特性设计文档

> **修订记录**
>
> | 日期 | 变更人 | 变更摘要 |
> |---|---|---|
> | 2026-10-31 | Yiyue + Claude | 首次创建（草案待评审）：需求 82~89（含 2026-10-30 评审修订注，已用户确认）的技术设计——存储位置三方案对比（推荐私有目录 `topics/` 子目录）、`timestamp\|content` 组合事务原语、多选交互形态、「归档与主题」分区列表、「移入」词族微文案、故障注入与测试策略、89(a)~(p) 验收映射 |
> | 2026-11-01 | Yiyue + Claude | 开放问题 O1~O3 用户裁定（均采纳推荐）：O1 验收注入＝`run-as chmod` 受控复现＋单测注入参数（89(c) 措辞按 89 尾注授权校正）；O2 成功文案＝M>0 对齐第 15 章「（跳过 M 条已存在）」句式族＋N=0 专用直陈句式；O3 分区排列＝主题分区在前 |
> | 2026-11-01 | Yiyue + Claude | 三视角设计评审修订（40 项发现→4 必修＋应补收敛），用户已批准：§10.1 注入配方重写（绝对路径／目录 chmod／probe 预检／注入时点／89(d) 确定性主证据）＋§6.4 异常边界（runCatching 映射、`createTempFile` 未捕获分支）；§5.2 createdAt 兜底改「首见回写一次」＋SP `commit()`；§6.3 删除算法与 `parse` 同源规约（共享扫描）＋全删归一化＋`deleteEntries` 弃哨兵改 null 语义；§11 验收表补「构造方法／人工观察点／日志与 SP 文件证据」三列；§6.4 rename-fallback 截断分支如实披露（新增 R11）；§7.2 多选态对浏览态的处置；§8.2 提醒联动三处修正（仅 Success 触发／执行时重算 K／同秒碰撞连坐防护）；§10.2 测试矩阵扩充＋TopicRepository 构造器注入 baseDir；§9 SAF 失败文案对齐实发常量句式；§6.6/§8.1 移入后立即主动刷新；§6.5 `topicsDir()` null 兜底与创建顺序约束；§3.2 补三处接线；§10.4 既有行为回归清单；若干措辞修正 |
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
