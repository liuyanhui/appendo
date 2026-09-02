# 技术债务追踪

> 记录项目中已知的技术债务，包括来源、影响和计划处理方式。

---

## TD-001: updateEntry/deleteEntry 条目定位逻辑重复

- **来源**: 笔记详情编辑功能（2026-06-03）
- **描述**: `FileBasedMarkdownFile`、`SafMarkdownFile` 以及测试辅助类中的 `updateEntry` 和 `deleteEntry` 共享约 35 行完全相同的条目定位算法（逐行扫描，时间戳正则匹配，以下一个 `## timestamp` 为边界）。
- **影响**: 三处代码需要同步维护，新增存储后端时需复制相同逻辑
- **建议修复**: 将条目定位逻辑提取到 `MarkdownFormatter` 或新建辅助类中，作为共享方法供所有实现调用
- **优先级**: 低（功能正确，不影响用户体验）

## TD-002: updateEntry 后条目间 `---` 分隔符丢失

- **来源**: 笔记详情编辑功能（2026-06-03）
- **描述**: 更新条目后，被更新条目与下一个条目之间的 `---` 分隔符会丢失。虽然 `parseMarkdownEntries` 跳过 `---` 行，功能不受影响，但文件格式与 `MarkdownFormatter.formatEntry()` 生成的原始格式略有偏差。
- **影响**: 多次编辑后文件格式与原始格式不一致，但解析完全正确
- **建议修复**: 在内容重建时保留原有的 `---` 分隔符结构
- **优先级**: 低（功能正确，仅外观差异）

---

## v1.1 数据完整性增强 — 遗留与更新（2026-07-21）

**已解决**：
- ✅ **TD-001**：updateEntry/deleteEntry 条目定位逻辑重复 → v1.1 由 `EntryParser`（findEntryBounds / buildDeleted / buildUpdated）收敛，4 份重复消除。

**需复核**：
- **TD-002**：updateEntry 后 `---` 分隔符——v1.1 改用 `EntryParser.buildUpdatedLines` 重建，行为可能与旧实现不同，需复核（低优先）。

**新增技术债**：
- **TD-003 Robolectric 不可用**：本环境 SSL 握手失败，无法下载 android-all jar。FileBased 测试改纯 JVM + `mock<Context>()`；SAF/UI 测试须 instrumented（真机）。影响：SAF 路径无 JVM 单测，依赖真机验证。
- **TD-004 CRLF/LF 混合**：用户既有数据为 CRLF、v1.1 新写入为 LF。delete/update 的 read-modify-write 会逐步规范化为 LF（`String.lines()` 兼容，无功能影响）；若要一次性统一可在 Phase B 处理。
- **TD-005 SAF PoC 待填**：`docs/background/saf-poc-spike.md` 的 provider `fd.sync()` 行为结论待真机填写（代码已防御性实现，不依赖结论）。
- **TD-006 SAF instrumented 测试待补**：需 FileProvider 造 `content://` URI 才能在 androidTest 测 SafMarkdownFile；当前依赖手动真机验证（已验写入 + 恢复）。
- **TD-007 parseMarkdownEntries 包装器**：MainScreen 的 `parseMarkdownEntries`（薄委托 EntryParser.parse）待移除，调用方迁直调，随 Phase B ViewModel 重构。
- **TD-008 onClick 主线程 I/O**：MainScreen 的 append/delete/update/clear/archive 等仍主线程同步 I/O（一次性用户操作，可容忍）；全量下放 `Dispatchers.IO` 随 Phase B。仅 refreshEntryCount（2s 轮询）已下放。
- **TD-009 .bak 文件驻留**：SafMarkdownFile.safAtomicWrite 每次写覆写 .bak 但不主动清理；私有目录常驻 1 个 .bak（被覆写，不累积多个）。低影响。

---

## v1.2.0 后架构梳理 — 新增（2026-08-31，整理 architecture.md 时核实代码发现）

- **TD-010 归档条目计数用严格秒级正则**：`ArchiveRepository.countEntries` 用 `MarkdownFormatter.getTimestampRegex()`（严格、不匹配毫秒），`MainActivity.countEntries` 内联同款正则——v1.1 起创建的归档在归档列表与恢复确认框显示"0 条"（恢复功能本身按 `EntryParser.parse` 宽松解析去重，不受影响，仅计数显示错）。修法：两处改用 `EntryParser.getTimestampRegex()`。v1.1 设计曾要求消灭全部 7 处内联严格正则，此处为残留。优先级：中（用户可见的错误显示）。
- **TD-011 MarkdownFormatter.formatEntry 死代码**：生产代码无调用方（仅 `EntryDeletionIntegrationTest` 当内容构造器用）；KDoc 仍称"仅 SafMarkdownFile 过渡期使用"（SafMarkdownFile v1.1 重写后已不调用）。可删方法并迁移测试构造，或至少修正注释。优先级：低。

---

## architecture.md 三角色评审 — 新增（2026-08-31，评审揭示的代码级风险，已如实记录进 architecture.md §8.1 K6–K9）

- **TD-012 读失败=空串的破坏链（K7，架构师发现）**：两实现读失败均吞为 `""` 且 `ReadResult` 无失败标志，上层把空当空文件——① `exists()` 吞异常→`initHeader` 用 FILE_HEADER 覆写已有文件；② `append` 见空→全量重写为 header+新条目（瞬时读失败即清空整库）；③ `refreshEntryCount` 解析空得 0 条→对账把**全部提醒判为孤儿 cancel+remove**（启动时 SAF 未挂载即抹光提醒）。修法方向：`ReadResult` 加失败标志 / 对账前对"空结果但文件应非空"加守卫。优先级：**高**。
- **TD-013 SAF 写路径绕过软恢复（K8，架构师发现）**：`append/appendEntry/deleteEntry/updateEntry/writeAll` 直读主文件（`readMainOnly`）不先 `ensureConsistent`，且 `safAtomicWrite` 把可能脏的 current 写进 `.bak` 销毁唯一好备份——崩溃后若下一个操作是写入（典型：分享追加），脏状态被固化。修法方向：写路径开头也调 `ensureConsistent`，或 `.bak` 只在上次确认成功后更新。优先级：中。
- **TD-014 应用更新丢闹钟（K9，架构师发现）**：manifest 仅 `BOOT_COMPLETED`、无 `MY_PACKAGE_REPLACED`；覆盖安装后闹钟全丢，`MainActivity` 启动只清孤儿不重注册——不重启提醒一直哑。`ReminderBootReceiver` KDoc 声称"app 更新后"重注册，与事实不符（注释待修）。修法方向：manifest 加 `MY_PACKAGE_REPLACED`，或启动时按 sidecar 重注册未 fired 项。优先级：中。
- **TD-015 解析层静默丢内容（K6，架构师+新程序员收敛）**：`EntryParser.parse` 跳过恰为 `---` / 以 `# Appendo` 开头的内容行、丢弃无内容条目、整段 `trim()`（分享入口亦 trim）——这些行写进了文件但视图里没有，**编辑保存（updateEntry）会把缺行版本写回，永久丢失**；「复制全部」与视图出口内容不一致。修法方向：`isolateLine` 的隔离范围扩展到这两类形态（写侧隔离+读侧还原），评估对既有文件的兼容；短期至少在 README 已知约束中声明。优先级：中-高（违背"格式保留"核心承诺）。

---

## v1.2.1 技术债批次 — 全部已解决（2026-08-31）

TD-010 ~ TD-015 全部修复并随 v1.2.1 发布（详见 CHANGELOG 1.2.1、`task-list.yaml` 各任务验收记录、`docs/architecture.md` §8.1）：

| TD | 修复方式 |
|----|---------|
| TD-010 | `ArchiveRepository`/`MainActivity` 计数改 `EntryParser.getTimestampRegex()`（宽松正则） |
| TD-011 | 删除 `MarkdownFormatter.formatEntry`/`TIMESTAMP_FORMAT`/严格正则；集成测试迁移至 `EntryParser.format` |
| TD-012 | `ReadResult` 增 `failed` 标志；两实现 initHeader/append 族读失败中止；`MainScreen.refreshEntryCount` 对账守卫 |
| TD-013 | `SafMarkdownFile` 写路径统一 `readMainForWrite`（先 `ensureConsistent` 再读） |
| TD-014 | manifest 增 `MY_PACKAGE_REPLACED`；新增 `reminder.rescheduleAll`（开机/更新/启动三入口共用，幂等） |
| TD-015 | 隔离谓词与解析跳过谓词共用 `EntryParser.isSkippableLine`（含 `---`/`# Appendo` 前缀形态）；旧条目不可回溯（README 已声明限制） |

**真机验收清单（用户执行，对应 architecture.md §12）**：
1. SAF 模式：写入/读回正常；杀进程模拟崩溃后重进 → 出现恢复提示且内容为上次良好状态（TD-012/013）
2. 覆盖安装更新（不重启）→ 等一条临近提醒 → 应正常响（TD-014）
3. 重启手机 → 错过提醒补发正常（回归验证 BOOT 路径）+ 提醒自检测试按钮正常
4. 追加含 `---` 与 `# Appendo` 行的内容 → 列表可见、编辑保存后不丢行（TD-015）
5. 复制全部/复制单条、分享、日历预填 → 粘贴到其他编辑器检查无零宽字符（TD-015 出口）
6. 归档后归档列表条数显示正确（TD-010）

**真机验收结果（2026-08-31，realme，v1.2.1）**：
- ✅ 覆盖更新（1.2.0 → 1.2.1）及后续两次 `adb install -r` 重装：应用启动正常、无崩溃（logcat 干净）、旧数据保留
- ✅ 分享接收写入正常；TD-015：新写入条目中 `---` / `# Appendo` 行在视图可见、编辑保存不丢行（用户确认）
- ✅ 提醒路径验证通过（用户确认；sidecar 无测试残留，符合设计）
- ✅ 归档详情文件名重复显示已修复（顶栏标题行内弱化展示一次，用户确认）——该问题为 v1.0 起遗留，非本批次回归
- ⛔ 未覆盖（**按四角色评审意见升格为待定向验收**，勿再"日常观察"）：重启后 BOOT 补发回归；SAF 模式崩溃恢复（须在写入窗口内 `adb shell am crash` 模拟，"限制后台杀进程"造不出窗口）

---

## architecture.md 四角色评审 — 新增（2026-08-31，架构师/工程师/测试/产品经理并行评审）

- **TD-016 删至 0 条 UI 滞留回归（K10，架构师发现，高优先）**：TD-012 的对账守卫 `newEntries.isEmpty() && entries.isNotEmpty()` 把"删除最后一条条目"也拦下——删除成功后读到合法空文件而内存仍非空，已删条目滞留列表直至重启（文件正确，与"已删除"Toast 矛盾）。修法：守卫改用"文件内容判据"（读失败/内容空白才跳过，header-only 是合法空），并把该决策抽成纯函数加单测（同时满足测试工程师"对账守卫纯函数化"建议）。附 §12 验收行"删除最后一条"。
- **TD-017 清空前归档兜底不设防（K11，架构师发现，中优先，待决策）**：`MainScreen.archiveFile` 忽略 `writeAll` 返回值，归档失败仍执行 `clear()` 且提示"已归档"——用户可能在无备份情况下清空。修法方向：clear 以归档成功为前置（阻塞式）or 文档降级承诺；涉及 UX 变更需产品确认。
- **TD-018 decideRecovery.shouldShowToast 三方矛盾（测试+架构师收敛，中优先）**：`EntryParser.decideRecovery` 的 `shouldShowToast` 字段无消费方（`ensureConsistent` 只用 `useBackup`），但 `EntryParserTest` 断言它为 true、KDoc 亦写"仅提示"——测试在保护不存在的规格。修法二选一：删字段+改测试+改 KDoc，或真接线 Toast。
- **TD-019 贪睡导致重复提醒系列漂移（架构师发现，低优先，待产品定位）**：重复提醒以 `effectiveTrigger` 排下一次，贪睡后整个系列永久后移（每天 9:00 贪睡 1h → 此后每天 10:00）。定位为有意取舍（文档+README 声明）或缺陷（改为锚定原 `triggerAt` 时刻），需拍板。
- **TD-020 测试盲区批次（测试工程师发现，中优先）**：① 全局锁零并发测试（纯 JVM 可行：双线程并发 append，断言条目数与时间戳无交错）；② 重复时间戳语义零覆盖（findEntryBounds 首条命中 + 不判孤儿）；③ `ReminderLogic.findMissed`/`advanceToBooted` 为死函数（生产走 bootDecide）但有 3 个测试供着——删函数或标注；④ atomicWrite rename 失败 fallback 分支无测试；⑤ 分享超长（>10k）Toast 文案为笼统"写入失败"，应改"内容过长"。优先级 ①>②，③④⑤ 顺手。

---

## v1.2.2 批次 — 结项（2026-09-02）

- ✅ **TD-016** 删至 0 条 UI 滞留 → `RefreshGuard` 纯函数（header-only 合法空放行）+ 5 用例；真机验收通过
- ✅ **TD-017** 清空兜底 → 归档失败阻止清空 +「已取消清空」提示
- ✅ **TD-018** decideRecovery 死规格 → 删 `shouldShowToast`，三方对齐
- ✅ **TD-019** 贪睡漂移 → 决策定位有意取舍（2026-09-01④），§5.2/README 声明
- ✅ **TD-020** 测试盲区 → ①并发回归 ②重复时间戳语义 ③死函数删除 ⑤超长文案 完成；④ atomicWrite fallback 注入式测试记 backlog（成本高于收益）
- ✅ **TD-021** SAF 授权失效静默空列表 → **受控复现确立根因**（realme 上 `takePersistableUriPermission` 未持久化 + 覆盖安装清授权；临时授权扛进程死亡）→ 修复：读失败+SAF 弹「数据文件访问已失效」对话框（重选/回退）+ 分享路径明示；**真机活体验收通过**（install -r 后弹窗、重选恢复）
- ✅ 归档详情残留提醒徽标（评审外真机发现，K13）→ readOnly 视图不渲染

**真机验收（2026-09-02，realme，v1.2.2）**：删至 0 条 ✅ / BOOT 重启补发 ✅ / 覆盖安装到点响 ✅（dumpsys + 锁屏通知双证据）/ 归档恢复去重 ✅ / SAF 失效对话框 ✅（活体）/ 出口干净度字符级检查 ⛔（唯一未做项，日常使用可补）

## 待决策（新登记）

- **TD-022 重启后提醒延迟、解锁后才补发（2026-09-02 用户报告）**：根因为 Android 架构边界——闹钟不跨重启（靠 BOOT 广播重注册），CE 加密使 `BOOT_COMPLETED` 在首次解锁后才送达（§5.3/自检页/README 均已声明，非回归）。改进选项：**A**. `LOCKED_BOOT_COMPLETED` + `directBootAware` 接收器 + 下次触发时间镜像到设备加密（DE）存储，锁屏期即可重注册（标准闹钟 App 做法，中成本：sidecar 需双写或提取最小快照）；**B**. 维持现状 + 现有披露。
  **决策（2026-09-02，用户拍板）：维持现状（B）。** 评估要点：① 完整方案不是加广播——锁屏期触发路径会连坐（读 Markdown 文件与写 sidecar 均在 CE 不可用，且读不到内容会触发"条目不存在"自愈**误删提醒**），须做 DE 镜像（含通知标题快照）+ `ReminderStore` 5 个写方法双写 + 解锁后 CE/DE 合并对账，约 200–300 行改动，估 0.5–1 天编码 + 半天真机锁屏重启验证（慢且本机重启即断 USB）；② 两个**不可控点**：realme 对 `LOCKED_BOOT_COMPLETED` 的送达时机、锁屏期通知可用性——本机已有非标准行为先例（SAF 授权持久化静默失败），投入后可能仍不达标；③ CE/DE 双写引入新的一致性债务。收益仅覆盖"重启且提醒落在锁屏窗口"的低频场景，现状有兜底（**不丢只延迟**：解锁补发 + 打开应用补发）。若未来重启使用模式变化，先做半小时 spike（只加广播打日志验证送达时机）再决策。
