# 主题分拣「移入主题」— 特性设计文档

> **修订记录**
>
> | 日期 | 变更人 | 变更摘要 |
> |---|---|---|
> | 2026-10-31 | Yiyue + Claude | 首次创建（草案待评审）：需求 82~89（含 2026-10-30 评审修订注，已用户确认）的技术设计——存储位置三方案对比（推荐私有目录 `topics/` 子目录）、`timestamp|content` 组合事务原语、多选交互形态、「归档与主题」分区列表、「移入」词族微文案、故障注入与测试策略、89(a)~(p) 验收映射 |
> | 2026-11-01 | Yiyue + Claude | 开放问题 O1~O3 用户裁定（均采纳推荐）：O1 验收注入＝`run-as chmod` 受控复现＋单测注入参数（89(c) 措辞按 89 尾注授权校正）；O2 成功文案＝M>0 对齐第 15 章「（跳过 M 条已存在）」句式族＋N=0 专用直陈句式；O3 分区排列＝主题分区在前 |
>
> **状态：草案待评审（O1~O3 已裁定）**（六阶段流程之阶段2 产出；设计评审 → 修订 → 用户确认后进入阶段3 计划）
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
| P6 | 颜色语义（需求 78） | 「移入」为非破坏性 → 确认按钮 primary（需求 88 已归类）；删除空主题 = 破坏性 → error 红；勾选框 primary；滑动指示背景色规则不变（本特性主题卡片禁用滑动删除，见 §8.3） |
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

### 3.2 修改文件

| 文件 | 改动 |
|---|---|
| `util/MarkdownFileOperations.kt` | 接口新增 `deleteEntries(keys: Set<EntryKey>): Int`（§6.3） |
| `util/FileBasedMarkdownFile.kt` | 实现 `deleteEntries`（持锁 + 一次 `atomicWrite`） |
| `util/SafMarkdownFile.kt` | 实现 `deleteEntries`（持锁 + `readMainForWrite` + `safAtomicWrite`，失败 `.pending` 残留走既有软恢复） |
| `util/EntryParser.kt` | 新增纯函数 `buildContentWithoutEntries(content, keys): Pair<String, Int>`（批量删除行算法，§6.3） |
| `ui/MainScreen.kt` | 多选态状态机与顶栏切换；「移入」入口与执行（IO 协程）；提醒联动；`setFileLastModified` 补调；菜单「归档管理」→「归档与主题」 |
| `ui/EntryListScreen.kt` | 新增 `selectionMode` / `selectedKeys` / `onToggleSelection` / `onPreview` 参数（默认值保证归档详情复用零改动）；多选态禁用滑动删除、点击=打勾、长按=预览 |
| `ui/ArchiveListScreen.kt` | 改造为「归档与主题」分区列表（§8.3）：主题分区（正向标识 = `topics/` 目录）+ 归档分区；主题卡片交互差异化；主题恢复注明保留 |
| `ui/ArchiveDetailScreen.kt` | 新增可选参数 `screenTitle: String = "归档详情"`，主题详情传「主题详情」（§8.5） |
| `MainActivity.kt` | `archive_detail/{filePath}` 路由加可选 `kind` 查询参数（`archive`/`topic`） |
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

- 存储：独立 SP 文件 `appendo_topics`（避免混入有历史沿革的 `appendo` SP；主题元数据整体生命周期独立，清理 / 迁移方便）。key = `created_<文件名含.md>`，value = 创建时刻 epoch millis。云备份覆盖（决策②口径）。
- **时间列语义定稿：显示创建时间**（`yyyy-MM-dd HH:mm`，与归档列表 `formatTimestamp` 同格式）；**排序 = createdAt 倒序**（新主题在前，与归档列表"新归档在前"一致）。不显示"最近移入时间"——活动属性会引起排序与时间列不对应的困惑，且移入频度高时列表跳动违背"平静"（P4）；做减法（P9）。
- **兜底**：文件存在但 SP key 丢失（云备份部分恢复 / 异常）→ createdAt fallback 为 `file.lastModified()`，仅用于当次排序与显示，**不回写**（防漂移来源）。该文件后续移入不受影响（排序仍用 fallback 值，稳定）。
- 条目数：不入 SP，列表加载时实时读文件计数（宽松正则，复用 `ArchiveRepository.countEntries` 同款逻辑）——与归档列表行为一致，避免第二份会过期的计数。

### 5.3 命名规则（需求 83 修订 a/b/c 全量落实）

校验顺序（前者先报）：空名 → 保留字 → 非法字符 → 长度 → 同名检测。

| 规则 | 定义 | 反馈 |
|---|---|---|
| 空名 / 纯空白 | trim 后 `isEmpty()`（对齐需求 76 的 `isBlank` 语义；命名场景无"保留空白"诉求，先 trim） | toast「名称不能为空」，对话框保持 |
| 保留字 | `equals("Appendo", ignoreCase = true)` 或 `startsWith("Appendo_", ignoreCase = true)` | toast「不能用“Appendo”或“Appendo_”开头命名」 |
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
 */
data class EntryKey(val timestamp: String, val content: String) {
    fun asString(): String = "$timestamp|$content"
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
 * - 持 FileOperationLock；解析 → 一次原子写（不逐条写，200 条仅 1 次全文件写）。
 * - 同 key 多条完全重复条目（历史数据理论存在）全部删除（幂等彻底）。
 * - 读失败中止不写（TD-012 纪律：读失败 ≠ 空文件）。
 *
 * @return 实删条数；读失败或写失败返回 -1（调用方不得当 "0 条" 处理）。
 *         SAF 实现写失败时 .pending 残留，下次读经 ensureConsistent 从 .bak 回滚（既有软恢复）。
 */
fun deleteEntries(keys: Set<EntryKey>): Int
```

**EntryParser 新增纯函数**（两实现共享的行算法，写读同源原则）：

```kotlin
/**
 * 返回删除 [keys] 命中条目后的全文与实删条数（纯函数，可 JVM 单测）。
 * - 条目块定位沿用 findEntryBounds 的"时间戳行 = 边界"语义，但匹配条件升级为
 *   key 比对：时间戳行匹配 && 该条目区间内容（restoreLine 还原 + trim，与 parse 同语义）
 *   组成的 EntryKey ∈ keys——同秒不同内容的条目不误删。
 * - 删除区间含条目前紧邻的 `---` 分隔线（与 buildDeletedLines 一致，EntryParser.kt:267）。
 * - 保留文件头与未命中条目；文件头之外的行原样保留。
 */
fun buildContentWithoutEntries(content: String, keys: Set<EntryKey>): Pair<String, Int>
```

> 批量**追加**不新增接口方法：目标写入 = 目标全文 + `toAdd.sortedBy { it.timestamp }.joinToString("") { format(...) }` 后一次 `writeAll`——全部复用既有方法，接口面最小化。

### 6.4 组合事务（util/TopicMoveTransaction.kt）

```kotlin
/** 移入组合事务结果（需求 84(b) 失败语义精确化）。 */
sealed interface MoveResult {
    /** 成功：moved=写入目标数；skippedExisting=目标已存在跳过数（87 透明）；
     *  missingFromSource=所选但源中未找到数（正常恒 0，防御口径；N=moved+skipped+missing 可核对）。 */
    data class Success(val moved: Int, val skippedExisting: Int, val missingFromSource: Int) : MoveResult

    /** 源读取失败（典型：SAF 授权失效）。目标与源均未改动——干净失败，无任何中间态。 */
    data class SourceReadFailed(val failed: Boolean) : MoveResult

    /** 目标读取失败。源未改动（目标在私有目录，极罕见）。 */
    data class TargetReadFailed(val failed: Boolean) : MoveResult

    /** 写入段失败（84b）：目标可能已部分写入（不回滚——FileBased 真原子下通常保持原样），
     *  当前文件原样保留。可整体重试。 */
    data class TargetWriteFailed(val reason: String) : MoveResult

    /** 删除段失败（84b）：目标已写全、当前文件可能部分移除——"两边都有"软中间态；
     *  重试幂等收敛（87：目标已存在 → skipped，源再删）。 */
    data class SourceDeleteFailed(val moved: Int, val skippedExisting: Int) : MoveResult
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
| 源读（步骤 1） | 失败 → `SourceReadFailed`，零副作用 | 同左；授权失效场景上层叠加 TD-021 文案（§9） |
| 目标写（步骤 5，恒 FileBased） | `atomicWrite`（temp+fsync+rename）真原子：失败时目标通常保持原样，极端 rename-fallback 中断可能部分写入——**不回滚**，重试覆盖收敛 | 同左（目标恒 FileBased，无 SAF 目标分支） |
| 源删（步骤 6） | `atomicWrite` 真原子：成功即完成；失败 → `SourceDeleteFailed`（目标已写全，"两边都有"，重试收敛）；崩溃中断要么旧要么新（84(g)：中断允许"两边都有"，永不"两边都没有"） | `safAtomicWrite`：写失败 `.pending` 残留 → 下次读 `ensureConsistent` 从 `.bak` 回滚 = 回到删除前（"两边都有"）→ 重试收敛；与需求 39"SAF 软恢复"分级一致 |
| 幂等重试验证 | 二次 `move(同 keys)`：目标已有 → 全 skipped；`deleteEntries` 再删 → `Success(moved=0, skipped=N)`——UI 反馈 N=0 成功文案（§9），收敛完成 | 同左 |

### 6.5 主题仓储（data/TopicRepository.kt）

```kotlin
data class TopicDocument(
    val file: File,           // topics/<name>.md
    val displayName: String,  // 不含 .md（UI 显示）
    val createdAt: Long,      // SP 持久化 epoch ms；丢失 fallback file.lastModified()（不回写）
    val entryCount: Int       // 实时读文件计数（宽松正则）
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

class TopicRepository(private val context: Context) {
    fun topicsDir(): File                                       // getExternalFilesDir(null)/topics（不存在则 lazily mkdirs）
    fun listTopics(): List<TopicDocument>                       // createdAt 倒序；正向标识 = 目录成员
    fun findTopic(displayName: String): TopicDocument?         // 大小写不敏感
    fun createEmptyTopic(rawName: String): TopicCreateResult   // 校验 → writeAll(FILE_HEADER) + SP created_<file>（事务前创建空文档，列表即时可见、空可删）
    fun deleteTopic(topic: TopicDocument): TopicDeleteResult   // 85(a)：entryCount > 0 拒绝（NotEmpty）；0 条可删（文件 + SP key）
}
```

> **创建时机**：新建命名通过校验且（无同名）后、进入移入确认对话框前创建空文档——用户在确认框取消会留下一个空主题（0 条、可删、可复用）。取舍：不创建则取消零残留但事务需处理目标不存在（已兜底，`move` 支持懒创建）；选择"确认前创建"使列表即时可见、失败重试路径统一。代价（取消留空文档）可接受且可删（P5 诚实：所见即所建）。

### 6.6 新写盘点与刷新联动（需求 84(f)，对齐 75 技术前提）

| 写点 | mtime 联动 |
|---|---|
| 移入事务 `Success`（源被删改，无论 moved 多少） | MainScreen 回调调 `fileRepository.setFileLastModified(now)` → 2 秒轮询捕获（§6.3 现状机制），条目列表与计数刷新（需求 88 计数减少自动成立） |
| 目标创建 / 写入（topics/ 文件） | **不触发**主列表 mtime（不同文件）；「归档与主题」列表在导航进入时加载（现状 `LaunchedEffect(Unit)` 重建加载），主题恢复后显式 `loadArchives()`（对齐现状归档恢复回调） |
| 主题恢复（86，`appendEntry` 逐条） | 现状归档恢复已在added>0 时调 `setFileLastModified`（`ArchiveListScreen.kt:335-337`），主题恢复复用同一路径，无新增义务 |

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
   ├─ 列表项：卡片左缘叠加圆形勾选框（Checkbox，primary），点击卡片 = 打勾/取消勾选
   │    （82 修订 b：多选态点击不再打开编辑）
   ├─ 滑动删除手势禁用（多选态语义 = 选择；89(h) 退出后恢复）
   ├─ 长按 = 预览全文对话框（§7.4，82 修订 b 的"查看全文途径"）
   └─ 2 秒轮询照常（§7.3）
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

多选态长按条目 → 只读预览对话框（复用编辑对话框的滚动正文样式，无输入框）：时间戳小字（`onSurfaceVariant`，P8）+ 全文（`verticalScroll`）+ 底部「复制」（复制该条内容，出口经 parse 产物天然无 ZWSP）与「关闭」。选此形态而非 README 注明限制：实现成本低（对话框复用），补齐"多选态看不到全文"的可感知缺口；同时多选态长按的"复制单条"高频诉求由预览内「复制」承接。

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
  │    成功 → 关全部对话框 + 退出多选 + toast（§9）+ 提醒联动 + mtime 刷新
  │    失败 → toast「移入失败（原因附注）」，对话框 3 保持打开，确认按钮字面变「重试」（84(b)：
  │            保持勾选与目标选择，一键重试；重试走同一 move，幂等收敛）
  └─ 「取消」→ 关全部对话框，**保持多选态与选中集**（用户可换目标重来；所选主题文档若为本流程
       新建的空文档则保留——空主题可删、可复用，见 §6.5 取舍注明）
```

### 8.2 提醒联动执行序（84(c)）

确认执行成功（`Success` 或 `SourceDeleteFailed` 之外的失败不触发）后：

1. K = 所选 keys 中 `ReminderStore.hasUnfired(key.timestamp)` 的条数（**在打开确认对话框时计算**，用于正文第 3 行；执行时以同一集合为准）。
2. 成功回调里对这 K 条逐一 `AlarmScheduler.cancel(context, ts)` + `ReminderStore.get(context).remove(ts)`——与删除条目联动完全同款（`MainScreen.kt:678-680`）。
3. 成功 toast 追加附注「，已取消 K 条提醒」（K>0 时，§9）。
4. 恢复回当前文档提醒**不复活**：提醒已被删除，恢复是纯条目追加，无复活路径——零代码保证，README 声明（§12）。`missingFromSource` 条目的提醒由既有孤儿对账自动清理（§1.3）。

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
| 失败·源读（SAF 失效） | 移入失败（读取当前文件失败，自定义目录授权可能已失效） | `TOAST_MOVE_FAILED_SAF_REVOKED`（对齐 `TOAST_APPEND_FAILED_SAF_REVOKED` 句式） |
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

### 10.1 故障注入方法（89(c)(d)）——推荐：受控复现为主 + 单测注入参数

| 验收项 | 注入方法 | 推荐 |
|---|---|---|
| 89(c) 目标写入失败 | **受控复现**：`adb shell run-as com.yiyue31.android.appendo chmod 500 files/topics`（目标目录只读 → `atomicWrite` 失败）→ 验证当前文件原样、反馈「移入失败（写入主题文档失败）」→ 恢复 chmod 700 → 一键重试成功。specs 原文"存储满/URI 失效"在本设计下不可构造（目标非 SAF 无 URI；存储满需填满整机）——**验收措辞按 89 尾注校正**（开放问题 O1） | ✅ |
| 89(d) SAF 中断软中间态 | **受控复现**：SAF 主文件 + 移入执行窗口内 `adb shell am crash com.yiyue31.android.appendo`（architecture.md §12 已有的"写入窗口 am crash"手法）；或 TD-021 覆盖安装清授权构造删除段失败。崩溃后验证"两边都有"→ 重新移入同批 → 幂等收敛无重复 | ✅ |
| 单测层失败分支 | `TopicMoveTransaction.move` 增加 `@VisibleForTesting` 故障注入参数（`faults: MoveFaults = MoveFaults.NONE`，可选 `FAIL_TARGET_WRITE` / `FAIL_SOURCE_DELETE`）——纯 JVM 覆盖 84(b) 两类失败语义与幂等收敛，不依赖真机 | ✅ |
| DEBUG 钩子（生产代码内留开关） | 不采用：为验收在生产面留注入点违背"工具退后 / 做减法"，且上述手段已可覆盖全部验收分支 | 否决 |

### 10.2 单元测试要点（纯 JVM；`--max-workers=1` 串行执行；Robolectric 不可用 TD-003——FileBased 路径沿 `FileBasedMarkdownFileTest` 的 mock Context + 真实临时目录模式）

| 测试文件 | 要点 |
|---|---|
| `util/TopicMoveTransactionTest.kt` | ① 成功：目标按时间戳升序含所选、时间戳与内容原样（秒级条目原样）；② 目标部分已存在：不重复写入、源全删、moved/skipped 计数正确；③ N=0：全部 skipped、源全删、`Success(0, N, 0)`；④ 写入段注入失败：源原样（逐字节对比）、`TargetWriteFailed`；⑤ 删除段注入失败：目标已写全（"两边都有"）、`SourceDeleteFailed`；⑥ 幂等收敛：⑤ 态后重跑同 keys → `Success(0, N, 0)` 且源清空、目标无重复；⑦ 并发：事务持锁期间另一线程 `append` 被阻塞、事务完成后追加成功且不丢（84(a) 锁间隙验证）；⑧ 源读失败（文件不可读）→ `SourceReadFailed` 零副作用；⑨ 目标不存在（懒创建兜底）→ 以 FILE_HEADER 起步正常写入 |
| `util/EntryParserTest.kt` 扩展（`buildContentWithoutEntries`） | ① 同秒碰撞：同 ts 不同 content 两条只删所选；② 同 key 双条全删；③ 边界：首条 / 末条 / 前导 `---` 连带 / 文件头保留；④ 内容含 `---`、`# Appendo`、时间戳形态行（ZWSP 隔离往返不误删不残留）；⑤ keys 为空 / 无命中 → 原文不变、返回 0 |
| `data/TopicRepositoryTest.kt` | ① 命名校验全分支（空 / `Appendo` 与 `appendo_` 大小写变体 / 非法字符逐个 / 50 上限边界 / 同名大小写不敏感）；② 创建：`.md` 附加、FILE_HEADER 落盘、SP createdAt 写入；③ 列表：createdAt 倒序、**移入文件内容变更后 createdAt 与排序不变**（85(c) 漂移回归）；④ SP 丢失 fallback `lastModified` 且不回写；⑤ 删除：非空拒绝 / 空可删（文件 + SP key 同清） |
| `ui/MultiSelectTest.kt` | `retainedSelection`：① 新条目插入（头部 / 中部）后既有选中全保持；② 选中条目被删后从集合剔除；③ 空集 / 全保留边界 |

SAF 源的 `deleteEntries` 无 JVM 单测（TD-003 现状约束），靠 89(c)(d) 真机受控复现覆盖——与既有 SAF 路径验证策略一致（architecture.md §7）。

### 10.3 测试命令

```bash
./gradlew testDebugUnitTest --max-workers=1   # 串行（项目测试执行规则）
./gradlew assembleDebug
```

---

## 11. 验收映射（需求 89 (a)~(p) → 设计落点）

| 验收项 | 场景 | 设计落点 |
|---|---|---|
| (a) | 3 条移入新建：升序、原样、计数同步 | §6.4 move（升序拼接）、§6.6 mtime 联动 |
| (b) | 2 条已存在：不重复写、源全删、反馈 1/2 | §6.4 去重 + §9 `toastMovedCountSkipped` |
| (c) | 目标写入失败：源原样、可重试 | §6.4 `TargetWriteFailed` + §10.1 chmod 受控复现 + §8.1 重试态 |
| (d) | SAF 中断"两边都有"→ 幂等收敛 | §6.4 失败态表（SAF 行）+ §10.1 am crash |
| (e) | 误移恢复：去重 + 反馈 | §8.4 复用归档恢复 |
| (f) | 主题长按复制全文：格式化、无标记 | §8.3（`parse`+`formatForExport`，出口铁律） |
| (g) | 分区展示、条数正确、`Appendo_` 命名拒绝 | §8.3、§5.3 保留字 |
| (h) | 退出多选后交互不变、深色可读 | §7.2（多选态手势差异仅在多选态内）、§8.6 |
| (i) | 提醒联动：取消（闹钟 + sidecar）、附注、不复活 | §8.2 |
| (j) | 删除语义：非空禁删、空可删、恢复→清空→删除全流程 | §8.3、§6.5 `deleteTopic` |
| (k) | 命名：`Appendo` 变体拒、同名指路、空/非法拒 | §5.3、§8.1 对话框 2 |
| (l) | 移入后复制全部：已移条目不在、格式干净 | §6.6（源已删，出口自然干净）+ §8.3 出口复用 |
| (m) | 移入全部：仅剩文件头、空态与归零正常 | §6.4（deleteEntries 保留文件头）+ 现状空态机制不动 |
| (n) | 多选中新分享插入：选中集保持 | §7.3 `retainedSelection` |
| (o) | N=0：全部移除、反馈成功无歧义 | §6.4 ③ + §9 `toastMovedAllExisting` |
| (p) | 200 条不 ANR、可感知进行/完成 | §8.1（IO 协程 + 「移入中…」）+ §6.3（2 次全文件写） |

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
| R8 | 同 key（同 ts 同 content）双条历史数据：勾选态联动、移入一次清双条 | 理论边界（归档恢复去重使其实际不可达） | 设计注明（§7.3），README 不展开 |
| R9 | SAF 授权失效期间移入：源读失败 → 干净失败 + TD-021 句式提示；主界面既有失效对话框引导重选/回退 | 既有自愈机制覆盖 | §6.4 / §9，无需新路径 |
| R10 | 「归档」（立即执行）与「归档与主题」（跳转列表）的区别 | 既有 80(b) 注明义务随更名更新 | README |

---

## 13. 开放问题（须 Planner / 用户裁决，设计不自行裁定）

| # | 问题 | 倾向性方案 |
|---|---|---|
| O1 | **89(c) 验收措辞校正**：specs 原文"构造目标写入失败（存储满/URI 失效）"，但方案 A 下目标为私有目录文件——URI 失效不适用、存储满不可构造。建议按 89 尾注授权将验收措辞校正为"`run-as chmod` 只读目录受控复现 + 单测故障注入参数"（§10.1） | 接受 chmod 受控复现作为 89(c) 真机手段；单测已覆盖分支语义 |
| O2 | **N=0 成功文案句式**：specs 88 原文成功句式为「已移入 N 条（M 条目标已存在）」，本设计建议 (i) M>0 时对齐第 15 章句式族「（跳过 M 条已存在）」、(ii) N=0 时改直陈句式「M 条已在该主题中，已从当前列表移除」（87 修订"无歧义"要求下"已移入 0 条"有失败读感） | 按设计建议定稿（§9.1 已注明对齐理由）；若用户坚持 specs 原文字面，N=0 分支至少需单独句式，请裁定 |
| O3 | **分区排列顺序**：主题文档分区在前、归档分区在后（按使用频率：主题是活跃工作区、归档是历史快照） | 主题在前（设计推荐，低风险）；如用户心智上"归档为主、主题为辅"可对调，一行改动 |

其余决策点（D1 存储方案 A、D2 组合事务形状、D3 多选入口、空主题删除手势、失败重试对话框保持态、主题详情独立标题）均为设计定稿权内、已在正文给出优劣与推荐，评审时可推翻。

**裁定结果（2026-11-01，用户均采纳推荐）**：O1 ✅ 接受 `run-as chmod` 受控复现＋单测注入（89(c) 措辞按 89 尾注授权校正）；O2 ✅ M>0 对齐「（跳过 M 条已存在）」句式族、N=0 专用直陈句式；O3 ✅ 主题分区在前。§9.1/§10.1/§8.3 按此定稿，设计评审可继续挑战具体实现与字面。
