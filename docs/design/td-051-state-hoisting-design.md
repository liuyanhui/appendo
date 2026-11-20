# TD-051 状态提升——配置变更重建下草稿/多选/移入流程保全设计

> **状态：定稿（修订二——三视角评审 26 项发现全落；2026-11-19 用户裁定委托「按照你的建议执行」确认，含 D3 改分立式/D2 含进程死亡两处评审分歧裁定，见 §7）**
>
> **修订记录**
> | 日期 | 变更人 | 摘要 |
> |---|---|---|
> | 2026-11-19 | Yiyue + Claude | 首次创建（草案）：四方案对比（推荐 B＝rememberSaveable＋纯函数 codec）＋双宿主保存面盘点＋MoveFlowHolder 快照/恢复＋陈旧边界＋容量守卫＋测试映射＋开放点 D1~D4 |
> | 2026-11-19 | Yiyue + Claude | **三视角评审（架构/工程/测试，并行，26 项发现）后修订二＝定稿**：3 必修簇全落——①executing 语义重推导（架构师读码：synchronized 块零挂起点，配置变更不能取消事务中段，事务跑完但结果被丢弃→executing 快照可能「撒谎」；恢复后按选中集存空重推导真实结局）②D3 改分立式（工程师实测论证：一体式需重写 40+ 读写点，分立式 remember→rememberSaveable 一字之差＋3 个自定义 Saver）③测试同步义务（受影响面 TC 清单＋10x 新段＋corruption 穷举＋假绿围栏）。应补全落：TopicListScreen 第四丢失面纳入/inputs 红线补全/runtime-saveable 显式声明/onSaveInstanceState 间隙/listState 作用域核验/容量守卫四元组指纹/精确插桩点/表述修正。D1~D4 裁定落 §7 |
> | 2026-11-19 | Yiyue + Claude | **实施修正（编码启动时）**：§3.5 容量守卫降级自「四元组指纹匹配」改「确定性截断保留子集」——Saver restore 时序（首次组合期，entries 未加载）使匹配方案不可行，两阶段接线引入全局 pending 面＝更劣债务；截断零新接线 JVM 直测。test-cases TC-106 判据随动（v1.2 行已注）。其余不变 |
>
> **权威输入**：debt-tracker TD-051；test-cases §9 已知边界行；specs 修订⑤（关窗不丢字）＋106 契约（草稿提升宿主/两宿主镜像/禁挂键）＋107 准入门槛；84(d) 幂等收敛。
>
> **范围冻结注**：登记债务偿还（N-4），不新增功能；D4 轻注＝意图澄清非需求变化（三视角一致）。

---

## 1. 问题与机理（读码实证）

**触发链**：MainActivity 无 `android:configChanges`（manifest 实证）→ uiMode 变更 → Activity 重建 → remember 级状态全丢。

**丢失面（评审后扩为四处）**：
1. **草稿三件套**：MainScreen `inputContent/editContent/lastEditedTs`（:141/150/152）；工作区 `inputDraft/editDraft/lastEditedTs`（:102/103/109）。
2. **多选态＋选中集**：两宿主 `selectionMode/selectedKeys`（MainScreen:155-156；工作区:96-97）。
3. **moveFlow**：MoveFlowHolder（MoveFlow.kt:31-83）＋宿主 `previewEntry`/`sameNameExisting`（后者重推导不保存）。
4. **主题页命名/改名草稿（工程师 F5 纳入）**：TopicListScreen `createStep/newTopicInput`（:100-101）＋`renameTarget/renameInput`（:95-96）——同属「用户工作流草稿」红线。**ArchiveListScreen 恢复确认框态（showRestoreDialog/restoreFile/restoreName）＝后置登记**（无草稿输入，丢失＝重滑一次，非红线；§6 已知限制）。

**连带机理（架构师 #1/#2 必修簇——executing 语义）**：
- `TopicMoveTransaction.move()` 全程在 `synchronized(FileOperationLock)` 内，**零挂起点**（readAllWithStatus/atomicWrite/parse 全同步，TopicMoveTransaction.kt:128-197 实证）→ **协程取消不能打断事务中段**；配置变更时事务**总是跑完**，但 `withContext` 返回后协程已取消 → `markExecuting(false)`/close/toast 永不执行＝**结果被丢弃**。
- 因此快照 `executing=true` 三种真相：(a) 事务未启动 (b) 事务在途 (c) **事务已完成**（markExecuting(false) 未及执行）。一律恢复为 failed 会对 (c) 发假失败信号（重试→「全部已存在跳过 N」——数据幂等正确但 UX 误导）。**§3.3 重推导方案解决**。
- 进程死亡则事务真被终止（OS 信号）→ 84(d) 软中间态可能 → 幂等重试收敛。
- ArchiveFlow 无 holder 状态（仅 FocusRequester 实证）：中断落「备份已写清空未做」＝既有安全语义（TD-017），仅宿主旗标保存。
- **天然保全（零工作量，架构师核验成立）**：rememberLazyListState 内部 saveable（滚动位置）；Navigation 2.7.7 回退栈＋路由参数框架级恢复。

## 2. 方案对比与选型（评审后维持 B）

| 方案 | 覆盖 | 架构冲击 | 判定 |
|---|---|---|---|
| A configChanges="uiMode" | 仅 uiMode | 主题无重建自适应需真机核验；长期维护面 | 弃（覆盖不完整） |
| **B rememberSaveable＋分立 Saver（定稿）** | 重建＋进程死亡 | 零新模式（106 原位升级） | **采纳** |
| C ViewModel 无 SSH | 仅重建 | 新模式＋作用域规则 | 弃（覆盖劣于 B） |
| C' VM＋SSH | 同 B | 新模式＋序列化同构 | 弃（被 B 支配） |

**依赖动作（架构师 #5）**：`runtime-saveable 1.6.8` 现经 compose-ui 传递在线（gradlew dependencies 实证）——**libs.versions.toml＋build.gradle.kts 增显式声明**（防未来 BOM 转可选静默断链）；`lifecycle-viewmodel-compose` 声明保留不删（**与架构师清理建议分歧**：navigation-compose 传递必需，显式声明＝版本钉住文档化，删除零收益徒增变更面——裁定保留）。零新依赖（显式化既有传递依赖非新增）。

## 3. 设计细节（分立式——D3 裁定）

### 3.1 接线形态与 Saver 面

**分立式**：各字段 `remember` → `rememberSaveable` **一字之差，读写代码零改动**（工程师 F1 实测论证：一体式需 40+ 读写点重构为 state.copy 模式，变更面与回归风险压倒其原子性收益）。
- String/Boolean/Int 字段：框架内置 Saver 直接覆盖（草稿/旗标/lastEditedTs 等）；
- **自定义 Saver 仅 3 个**（新文件 `ui/StateSavers.kt`）：
  1. `EntryKeySetSaver: Saver<Set<EntryKey>, ArrayList<String>>`——编码＝**按 (timestamp, content) 排序**（工程师 F3 确定性）后逐 key 长度前缀对；
  2. `LinkEntrySaver: Saver<LinkEntry?, ArrayList<String>>`——(ts, content) 长度前缀对＋null 标记；
  3. `MoveFlowHolderSaver: Saver<MoveFlowHolder, ArrayList<String>>`——save＝`holder.snapshot()` 现值读取（**闭包在保存时点读 holder 当前值**——架构师 #3 同步张力消解：分立形态下 holder 即 saveable 值本身，无第二快照管线）；restore＝`MoveFlowHolder.restored(snapshot)` 工厂。
- **编码原语**（StateSavers.kt 内纯函数）：版本头＋长度前缀字段序列 `["v1", len, raw, ...]`；**字段序纪律＝只追加末尾＋旧版默认值**（工程师 F2——分立 per-field key 下无跨字段漂移面；单 Saver 内字段序 KDoc 钉死）；任意用户文本无损（换行/分隔符/unicode/ZWSP/\u0000——长度按 String.length 计字符）。
- **红线（架构师 #6 补全）**：草稿/多选/flow 的 rememberSaveable **既无 key 也无 inputs**——两机制任一变更都丢弃已保存状态；禁挂 reloadKey/filePath 及任何随重载连带变化的参数（106 修订⑤同源）。工作区数据态 `remember(filePath)` 键控维持现状（切主题重置正确），**保全态不键控**。
- **JVM 假绿围栏（测试 F3）**：StateSavers.kt/MoveFlow 快照纯核心 **import 白名单＝kotlin.*/java.*（java.io.File 允许）；禁 android.***（Bundle/Parcelable/Uri/Log 一切）——文件头 `// JVM-TESTABLE: imports must stay in {kotlin.*, java.*}` 标记＋评审纪律；Bundle 适配由 rememberSaveable 框架承担（薄边真机验证）。isReturnDefaultValues=true 下 android API 静默默认值＝假绿陷阱，白名单是唯一围栏。

### 3.2 保存面（分立字段表）

**保存**（两宿主镜像＋主题页）：
| 组 | MainScreen | 工作区 | TopicListScreen |
|---|---|---|---|
| 草稿 | inputContent/editContent/lastEditedTs | inputDraft/editDraft/lastEditedTs | newTopicInput/renameInput |
| 多选 | selectionMode/selectedKeys | selectionMode/selectedKeys | — |
| 目标身份 | selectedEntry/previewEntry（LinkEntrySaver） | editEntry/previewEntry | createStep（enum 名）/renameTarget（TopicDocument→路径＋名＋createdAt 编码，entryCount 重推导） |
| 删除态 | showDeleteDialog/entryToDeleteTimestamp（嵌套 :690-692 同升级） | deleteTargetTs/showBatchDelete | — |
| 对话框旗标 | showInputDialog/showDetailDialog/showArchiveDialog | showInputDialog/showArchiveDialog | 命名/改名框开态随 createStep/renameTarget 派生 |
| flow | moveFlow（HolderSaver） | moveFlow（HolderSaver） | — |

**不保存**：entries/entryCount/loadFailed/reloadKey/moveTopics/topics/sameNameExisting（重载/重推导）；useSAF/fileUri/defaultFile/lastModified（repository/SP）；showMenu/showAboutDialog/showThemePicker/showOverflow（瞬态）；showSetupGuideDialog/showSafInvalidDialog（系统态——检查逻辑重触发，保存会呈陈旧框）；scrollAfterAdd（一次性信号）；executing（不原样恢复——§3.3）。
**嵌套删除态约束（测试 F2）**：MainScreen 删除态仅在 loadFailed=false 分支参与 composition——重建后若进读失败分支则自然不恢复＝**正确行为**（读失败无条目可删），声明不修。

### 3.3 MoveFlowHolder 快照/恢复＋executing 重推导（架构师必修簇）

```kotlin
internal data class MoveFlowSnapshot(
    val step: MoveFlowStep?, val verb: MoveVerb?,
    val targetPath: String?, val targetName: String?, val targetCreatedAt: Long, val targetEntryCount: Int,
    val newTopicInput: String, val failed: Boolean, val executing: Boolean
)
fun snapshot(): MoveFlowSnapshot
companion object { fun restored(s: MoveFlowSnapshot): MoveFlowHolder }
```
- restored：executing=true → **暂记 failed=true**（重试面貌）；newTopicInput 原样（关窗不丢字）；target entryCount＝快照值（**禁 0 占位**——F1-C1 教训：错误 0 比陈旧值更劣；首次 listTopics 刷新重推导对齐；陈旧中间态 ms 级用户不可见＝可接受，测试 F5 裁定）。
- **重推导纯函数（新 `MoveRestoreDecision`，JVM 可测）**：首次重载成功后，宿主调用——
```kotlin
internal object MoveRestoreDecision {
    /** restoredExecuting＝快照 executing；retainedKeysEmpty＝重载＋retainedSelection 过滤后选中集为空 */
    fun decide(restoredExecuting: Boolean, retainedKeysEmpty: Boolean): Action
    enum class Action { CLOSE_FLOW, KEEP_RETRY, NONE }
    // restoredExecuting && retainedKeysEmpty → CLOSE_FLOW（事务实际已完成——条目已不在源，
    //   列表已反映现实；静默关流程＋退多选，不发假失败信号）
    // restoredExecuting && !retainedKeysEmpty → KEEP_RETRY（未发生/部分完成——failed 重试，84(d) 幂等收敛）
    // !restoredExecuting → NONE（常规恢复，failed 原样）
}
```
- 宿主接线：重载成功分支 → retainedSelection 过滤 selectedKeys → 若快照 executing 曾为 true（restored 标记一次性消费）→ decide → CLOSE_FLOW 则 moveFlow.close()＋exitSelectionMode()。
- **窄窗口已知限制（工程师 F8）**：markExecuting(false) 之后、宿主清理之前的极窄窗口保存 → step=null 快照 → 流程不恢复但 selectedKeys 可能残留——列表已反映现实，用户可重新勾选操作；低概率可接受（§6）。

### 3.4 对话框恢复语义与陈旧校验（D1＝恢复开态）

- 用户工作流对话框恢复开态＋草稿（「继续刚才的事」——三视角一致）；系统态对话框不保存。
- **精确插桩（工程师 F6）**：
  - MainScreen：`LaunchedEffect(Unit)` 内 `refreshEntryCount()`（:410 附近）成功路径后——校验 selectedEntry/deleteTargetTs 存在性；
  - 工作区：`LaunchedEffect(filePath, reloadKey)`（:125-144）**成功分支**（entries 赋值后）——校验 editEntry/deleteTargetTs＋MoveRestoreDecision 消费；
  - 目标条目消失 → 关对应框清目标，**草稿保留**（lastEditedTs 归属规则不变）；selectedKeys 经既有 retainedSelection 过滤（零新逻辑）。
- **恢复时序**：rememberSaveable 恢复在首次组合期（早于 LaunchedEffect）——旗标先恢复开态、数据后就绪校验；加载中对话框呈草稿＋原目标身份（entries 未就绪不校验），就绪后校验闭环。

### 3.5 容量守卫（分立形态）

- Bundle 事务上限 ~500KB；**唯一大字段＝selectedKeys**（草稿现实有界但手动输入无上限——grep 实证仅分享路径 10000 限）；
- EntryKeySetSaver **自 cap 200KB**（留 300KB 给草稿＋框架开销）：超限降级＝**确定性截断保留子集**（排序序保留前 N 条可容者＋降级标记）——**2026-11-19 实施修正**：原四元组指纹匹配方案在 Saver restore 时序不可行（restore 发生在首次组合期，entries 尚未加载，匹配需两阶段接线＋全局 pending 面＝更劣债务）；截断保留部分选中（优于全丢）、零新接线、JVM 直测；宁丢部分选中不丢草稿不崩溃；
- 草稿全量保存（不截断——红线）；**极端长草稿（>200KB 单字段）＝已知限制**（现实笔记场景远不及；§6 声明）；
- 降级标记随快照（keysDowngraded）——恢复后选中集可能缩水，UI 如实呈现勾选态。

### 3.6 主题页接线（第四丢失面）

TopicListScreen：newTopicInput/renameInput（String 内置 Saver）＋createStep（enum→名 String Saver 或内置）＋renameTarget（TopicDocument 编码＝路径＋名＋createdAt；entryCount 恢复时重推导）——命名/改名框开态由 createStep/renameTarget 非空派生，恢复即开框＋草稿（D1 同口径）。

## 4. 测试映射

**JVM（新增）**：
- `StateSaversTest`：三 Saver round-trip 全态（空集/200 条集/超长内容/换行+分隔符+unicode+ZWSP+\u0000/null LinkEntry/flow 四 step×三 verb×failed×executing 组合）；**encode 确定性**（同快照两次 encode 逐字节相等——排序纪律）；**corruption 穷举（测试 F4，≥7 子项）**：版本不符/截断（奇数字段数）/长度 "0"（合法空串）vs 缺失/负长度 "-1"/字段级越界 vs 整体越界/非数字长度/多余字段——全部→安全默认不崩溃不臆造；容量守卫降级＋四元组指纹匹配/碰撞丢弃路径；
- `MoveFlowHolderRestoreTest`：snapshot/restored 全组合；executing→暂记 failed；newTopicInput 保留；entryCount 快照值非 0；
- `MoveRestoreDecisionTest`：2×2 矩阵全组合（CLOSE_FLOW/KEEP_RETRY/NONE）；
- **受影响面回归（测试 F6 必修，§0.3.1-1 批次门槛）**：既有 246 全绿＋TC-006/006b/006c（草稿族）/TC-043b（多选×并发）/TC-050（移入回归）真机复过；TC-006b 三通道判据**追加配置变更变体**（切换前开框→切换后框恢复开态→取消仍关窗留草稿）。

**真机（修复包装机后）**：
- **9 态矩阵（测试 F7）**：uiMode 切换×{输入框半字/编辑框半字/多选勾选/移入流程 CONFIRM 步/命名草稿（主题页）/归档二选一框（双宿主）/单删确认框/批删确认框/改名框}——草稿＋选中＋流程＋框态保全＋主题正确；
- **在途事务口径（测试 F8）**：事务毫秒级完成，人工切换不可可靠命中在途窗口——**executing→failed/重推导由 JVM 钉死；真机仅 CONFIRM 步切换（非在途）**；测试记录声明「JVM 替代论证」；
- **进程死亡配方（测试 F9）**：HOME 退后台 → `adb shell am kill com.yiyue31.android.appendo`（前台进程 am kill 不生效——必须先退后台；realme 若拒 → `kill -9 <pid>` 备选）→  launcher 冷启 → 恢复核验（Activity record 保留 saved state）；**「不保留活动」开发者选项＝Activity 销毁非进程死亡**——两路径分别验证（重建路径≠死亡路径）；
- 滚动位置/当前路由恢复核验；陈旧边界（死亡窗口外部删条目→关框留草稿）；容量降级（200 条×长内容选中→切换→指纹重建，部分丢弃如实）；
- **test-cases 同步（测试 F10 必修）**：§9 uiMode 行改写（「现已 rememberSaveable 保全（TD-051）——草稿/多选/对话框旗标/moveFlow 过重建不丢；进程死亡同机制覆盖」）；**新启 10x 段**（段义「配置变更与进程死亡恢复」——横切关注点跨双宿主，独立分段避免 00x/09x 重复；§0.1 段满另启规则）：TC-100(P0) uiMode×草稿（双宿主）/TC-101(P0) uiMode×多选/TC-102(P0) uiMode×moveFlow CONFIRM/TC-103(P1) 对话框堆叠/TC-104(P1) 进程死亡×草稿/TC-105(P0) 陈旧目标校验/TC-106(P1) 容量降级。

## 5. 改动面

| 文件 | 改动 |
|---|---|
| `ui/StateSavers.kt`（新） | 3 Saver＋编码原语＋容量守卫＋import 白名单标记 |
| `ui/MoveFlow.kt` | MoveFlowSnapshot＋snapshot()/restored()＋MoveRestoreDecision（或独立文件） |
| `ui/MainScreen.kt` | ~15 处 remember→rememberSaveable＋陈旧校验插桩（refreshEntryCount 成功后）＋MoveRestoreDecision 消费 |
| `ui/TopicWorkspaceScreen.kt` | ~12 处同构＋成功分支插桩 |
| `ui/TopicListScreen.kt` | 4 字段升级（第四丢失面） |
| `gradle/libs.versions.toml`＋`Appendo/build.gradle.kts` | runtime-saveable 显式声明 |
| `test/.../StateSaversTest.kt`＋`MoveFlowHolderRestoreTest.kt`＋`MoveRestoreDecisionTest.kt`（新） | §4 JVM 族 |

无 manifest 改动；零新依赖（显式化既有传递依赖）；**表述修正（工程师 F7）**：纯增量保全增强——对话框打开/关闭触发条件不变，仅增跨重建状态保持；**D1 恢复开态＝用户可感知增强**（CHANGELOG 可记「深浅切换不再丢失未保存内容与选中」）。

## 6. 风险与已知限制

1. Saver 框架薄边（Bundle 序列化）JVM 不可测——真机义务 §4；白名单围栏防假绿；
2. **onSaveInstanceState 时机间隙（架构师 #7）**：保存发生在 onStop 前——后台期间变更不被保存；进程死亡恢复态可能比最后真实态旧一级（Android 固有，陈旧校验兜底）；
3. **listState 键冲突核验（架构师 #8）＝非问题**：navigation-compose 每 NavBackStackEntry 独立 SaveableStateRegistry（per-entry 作用域），且两工作区实例不并存（无 workspace→workspace 导航）——无需 filePath 键控；
4. 极端大选集降级丢部分选中（§3.5）；极端长草稿（>200KB）保存失败风险（现实远不及）；
5. 在途事务窄窗口（§3.3 F8）；陈旧 entryCount ms 级中间态（测试 F5 可接受）；
6. ArchiveListScreen 恢复确认框态不保全（后置登记——无草稿，重滑即恢复）；
7. 进程死亡窗口外部改文件 → 陈旧校验关框留草稿（既有规则复用）。

## 7. 开放点裁定（2026-11-19 用户委托「按照你的建议执行」；评审分歧从技术正确路径）

- **D1＝恢复开态＋草稿**（三视角一致；「关窗不丢字」意图维度延伸——模态不断裂）。
- **D2＝含进程死亡**（工程师＋测试推荐纳入 vs 架构师推荐仅重建——**裁定纳入**：机制免费（同一 rememberSaveable）、完善优先于成本原则、真机增量 ~6 路径×30s≈3 分钟；架构师「冷启丢草稿是用户预期」论点记录在案，CHANGELOG 声明覆盖范围）。
- **D3＝分立式**（工程师实测论证推翻草案一体式推荐：40+ 读写点重构 vs 一字之差＋3 Saver；架构师两顾虑消解——恢复脉冲：rememberSaveable 恢复在首次组合同步完成（首帧前），无逐字段可见闪烁；容量守卫：唯一大字段 selectedKeys 自 cap 即可，无需全局快照会计。**裁定分立**，架构师分歧记录在案）。
- **D4＝轻注**（三视角一致：修订⑤就地注＝意图澄清非需求变化＋test-cases §9 改写＋10x 新段；不走完整需求循环）。

## 8. 需求/文档同步义务

- **specs 修订⑤就地注**（D4，随本定稿落盘）：草稿保全延伸至配置变更重建与进程死亡恢复（意图澄清）；
- **test-cases**：§9 uiMode 行改写＋10x 段 TC-100~106（设计定稿后、编码前追加——本节即义务清单）；
- debt-tracker TD-051 状态（编码完成后）；architecture.md UI 状态保全节；CHANGELOG 发布时落；
- 受影响面 TC 清单（TC-006/006b/006c/043b/050）入批次计划门禁（§0.3.1-1）。