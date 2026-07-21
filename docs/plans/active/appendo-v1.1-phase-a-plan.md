# Appendo v1.1 Phase A — 数据完整性增强 执行计划

> 日期: 2026-07-20
> 关联需求: `docs/specs.md`（需求 36–46）
> 关联设计: `docs/design.md`（v1.1 v3.1 章节）
> 执行方式: **方案 B**（手动 / Markdown，逐步执行 + 逐步验证）
> 目标版本: **1.1.0（versionCode 3）** — 最小 minor 增量（若改 patch 则 1.0.2）
> 状态: 待执行（等用户确认计划后开始 T-001）

---

## 执行原则

- **最小步伐**：每个任务 = 一个可独立验证的最小增量；完成即测；不过度捆绑。
- **实现与测试同任务**：每个实现任务含其测试，作为一个验证单元。
- **验证分级**（受当前环境无 Android SDK 约束）：
  - `[JVM]` 纯 JVM 单测——本地 `./gradlew test` 可跑
  - `[Robolectric]` 本地 JVM + Robolectric
  - `[instrumented]` 需模拟器/真机
  - 凡 `[requires-execution]` 在当前环境标 **"需本地执行"**，不阻塞流程推进（参照 note-detail-edit T-007 先例）。
- **依赖无环**；`read-only` 任务 ≤ 8 文件，禁全包 glob。
- **进度记录**：本文件「任务总览」表 + 「执行日志」节维护（不另建 progress 文件）。

---

## 任务总览

| 任务ID | 描述 | 验证类型 | 依赖 | 状态 |
|--------|------|---------|------|------|
| T-001 | 测试基建：build.gradle 加 Robolectric/androidTest 依赖与配置 | [requires-execution] 需本地 | — | pending |
| T-002 | EntryParser：isTimestampLine + 宽松正则 + parse（含 ZWSP 还原） | [JVM] | — | pending |
| T-003 | EntryParser：format（含 ZWSP 隔离）+ nextTimestamp（单调防碰撞）+ stripIsolationMarkers | [JVM] | T-002 | pending |
| T-004 | EntryParser：findEntryBounds + buildDeleted/buildUpdated + RecoveryDecider | [JVM] | T-002 | pending |
| T-005 | 数据模型分层：util/ParsedEntry + ui/LinkEntry(timestamp+timestampDisplay) | [read-only] | T-002 | pending |
| T-006 | MarkdownFileOperations 接口扩展：appendEntry、readAll→ReadResult、readAllForExternal + KDoc | [read-only] | T-005 | pending |
| T-007 | FileBasedMarkdownFile：atomicWrite（fsync/孤儿清理/rename fallback）+ readAll/count/initHeader/exists 持锁 | [Robolectric] 需本地 | T-006 | pending |
| T-008 | FileBasedMarkdownFile：append/appendEntry（单调 ts）+ delete/update/clear/initHeader 改造（走 EntryParser + atomicWrite） | [Robolectric] 需本地 | T-007, T-002..T-004 | pending |
| T-009 | SAF PoC spike：验证 fd.sync() 与 openOutputStream("wt") 行为（前置调研） | [instrumented] 需本地 | — | pending |
| T-010 | SafMarkdownFile：.pending/.bak + ensureConsistent + 读路径持锁 | [instrumented] 需本地 | T-006, T-009, T-004 | pending |
| T-011 | SafMarkdownFile：appendEntry + delete/update 改 EntryParser + exists() 修歧义 | [instrumented] 需本地 | T-010, T-002..T-004 | pending |
| T-012 | 零宽空格出口剥离：copyContent/shareContent 走 readAllForExternal | [read-only] + [JVM] | T-003, T-006 | pending |
| T-013 | archiveFile 重构走 writeAll + 归档恢复语义（appendEntry 原时间戳 + 透明计数 + 跳过清单 + 排序 i） | [read-only] + [JVM] | T-006, T-008, T-011 | pending |
| T-014 | 重复内容提示 Toast（5s 节流 + Clock 注入）+ 恢复 Toast（ReadResult.recovered） | [read-only] + [JVM] | T-006, T-008 | pending |
| T-015 | 顺手修复：exists() SecurityException 分流、MainScreen I/O 下放 Dispatchers.IO、删废弃 MarkdownFile.kt、删错误 thread-safe 注释 | [read-only] | T-008, T-011 | pending |
| T-016 | 迁移现有测试：MarkdownParserTest→EntryParserTest、改 import、废弃 TestFileBasedMarkdownFile 改 Robolectric | [JVM] 需本地 | T-002..T-004, T-006 | pending |
| T-017 | README（外部编辑器/SAF 软恢复/降级兼容）+ 版本号 1.1.0（versionCode 3）+ CHANGELOG | [read-only] | 全部实现任务 | pending |
| T-018 | 全量测试 + 真机验收（specs 44–46 验收场景） | [requires-execution] 需本地 | 全部 | pending |

---

## 任务详细定义

### T-001 测试基建
- 描述：build.gradle.kts 引入 Robolectric 与 androidTest 依赖、testInstrumentationRunner、testOptions
- 约束：仅改 `Appendo/build.gradle.kts`；不引入无关依赖
- 产出：build.gradle.kts 含 `testImplementation(robolectric:4.13)`、`testImplementation(androidx.test:core)`、`androidTestImplementation(androidx.test.ext:junit)`、`androidTestImplementation(ui-test-junit4)`、`testOptions.unitTests.isIncludeAndroidResources=true`、`defaultConfig.testInstrumentationRunner`
- 评估标准：
  - [ ] `./gradlew assembleDebugAndroidTest` 成功（依赖可解析）
  - [ ] `./gradlew test` 仍能跑现有 JVM 测试
- 评估方法：automated-test [requires-execution] **需本地执行**
- 依赖：—　状态：pending

### T-002 EntryParser：isTimestampLine + 宽松正则 + parse
- 描述：新建 `util/EntryParser.kt`（object），实现 `isTimestampLine(line)`、宽松正则 `^## \d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}(\.\d{1,3})?$`、`parse(content): List<ParsedEntry>`（含 ZWSP 还原逻辑：以 ZWSP 开头的行不当边界；去所有前导 ZWSP 后 isTimestampLine 则 while 剥离）
- 约束：纯函数不碰 I/O；新建 `util/ParsedEntry(rawTimestamp, content)`；ZWSP 常量 `ISOLATION_MARKER="​"`
- 产出：EntryParser.kt + ParsedEntry.kt + EntryParserTest（秒级/毫秒级/混合解析、ZWSP 还原、首行/中间/末行注入、CRLF+ZWSP、多 ZWSP）
- 评估标准：
  - [ ] 秒级与毫秒级时间戳行都能被 isTimestampLine 识别
  - [ ] parse 对含注入行的内容返回单条（不误切）
  - [ ] 用户自有 ZWSP（去标记后非时间戳）不被剥离
  - [ ] ParsedEntry.rawTimestamp 为完整毫秒串（新条目）或秒级串（旧条目）
- 评估方法：automated-test [JVM]
- 依赖：—　状态：pending

### T-003 EntryParser：format + nextTimestamp + stripIsolationMarkers
- 描述：实现 `format(timestamp, content)`（逐行扫描内容部分，仅"去前导 ZWSP 后 isTimestampLine 且原本不以 ZWSP 开头"的行加 ZWSP，幂等）、`nextTimestamp(content, now)`（反向扫描最后时间戳、Instant 比较、parse 失败 fallback）、`stripIsolationMarkers(content)`（仅剥离"行首 ZWSP+紧跟时间戳模式"，不全文清洗）
- 约束：纯函数；DateTimeFormatter `"yyyy-MM-dd HH:mm:ss.SSS"` Locale.US
- 产出：补 EntryParser.kt + 测试（format 幂等、write→read→write 往返一致、nextTimestamp 越界进位/时钟回拨/空文件/最后秒级、strip 不误伤用户 ZWSP）
- 评估标准：
  - [ ] format 幂等（已加 ZWSP 的行不再加）
  - [ ] format→parse 往返后 content 字节级一致
  - [ ] nextTimestamp 文件为空/仅 header 返回 now
  - [ ] nextTimestamp 时钟回拨返回 last+1ms
  - [ ] stripIsolationMarkers 只剥离"行首 ZWSP+时间戳"行，其余 ZWSP 保留
- 评估方法：automated-test [JVM]
- 依赖：T-002　状态：pending

### T-004 EntryParser：findEntryBounds + buildDeleted/buildUpdated + RecoveryDecider
- 描述：实现 `findEntryBounds(lines, timestamp): IntRange?`、`buildDeletedLines`、`buildUpdatedLines`、`RecoveryDecider(pendingExists, bakExists, mainContent, bakContent): RecoveryAction`（含 shouldShowToast）
- 约束：纯函数，不碰 I/O；delete/update 用首条匹配+区间（不靠唯一性短路）
- 产出：补 EntryParser.kt + 测试（边界定位、删除/更新重构、RecoveryDecider 各组合：.pending/无、.bak/无、主空/非空）
- 评估标准：
  - [ ] findEntryBounds 找首条匹配并返回到下一时间戳的区间
  - [ ] buildDeletedLines 删除后其余条目不变
  - [ ] buildUpdatedLines 保留原时间戳行、替换内容
  - [ ] RecoveryDecider 在 .pending 存在时返回"用 .bak 恢复 + shouldShowToast=true"
- 评估方法：automated-test [JVM]
- 依赖：T-002　状态：pending

### T-005 数据模型分层
- 描述：`util/ParsedEntry(rawTimestamp, content)`（T-002 已建则补）；`ui/LinkEntry(timestamp, timestampDisplay, content)` 从 MainScreen.kt 拆出，由 ParsedEntry 映射；timestampDisplay 格式 `yyyy-MM-dd HH:mm:ss`
- 约束：util 不依赖 ui；LinkEntry 仅 UI 用
- 产出：ParsedEntry.kt（util）、LinkEntry.kt（ui）、MainScreen 移除内联 LinkEntry 定义改 import
- 评估标准：
  - [ ] LinkEntry.timestamp 为完整毫秒串（key 用）
  - [ ] LinkEntry.timestampDisplay 剥离毫秒
  - [ ] util 层不 import ui 层
  - [ ] 现有 UI 引用 LinkEntry 处编译通过
- 评估方法：code-review [read-only]
- 依赖：T-002　状态：pending

### T-006 MarkdownFileOperations 接口扩展
- 描述：接口加 `appendEntry(timestamp: String, content: String): Boolean`、`readAllForExternal(): String`；`readAll()` 返回类型由 String 改 `ReadResult(content, recovered)`；KDoc 写明单调约束仅 append() now 路径、appendEntry 不强制单调、SAF 软恢复语义
- 约束：仅改 `util/MarkdownFileOperations.kt` 与新增 `ReadResult.kt`；不实现（实现属 T-007/T-008/T-010/T-011）
- 产出：MarkdownFileOperations.kt（接口扩展 + KDoc）+ ReadResult.kt
- 评估标准：
  - [ ] 接口含 appendEntry、readAllForExternal
  - [ ] readAll 返回 ReadResult
  - [ ] KDoc 说明单调约束范围与 SAF 软恢复
  - [ ] 未实现的类编译报错属预期（T-007+ 补齐）
- 评估方法：code-review [read-only]
- 依赖：T-005　状态：pending

### T-007 FileBasedMarkdownFile：atomicWrite + 读路径持锁
- 描述：实现 `atomicWrite(file, content)`（写 .tmp→fd.sync→renameTo→失败 fallback；同目录 createTempFile 防 EXDEV；孤儿 tmp 前缀清理）；readAll/count/initHeader/exists 全部 `synchronized(FileOperationLock)`
- 约束：仅改 `util/FileBasedMarkdownFile.kt`；伪代码见 design §6
- 产出：FileBasedMarkdownFile.kt 改造 + Robolectric 测试（atomicWrite 写中断后 tmp 残留不影响下次、rename 后内容正确、fsync 后掉电模拟、尾换行符一致性）
- 评估标准：
  - [ ] atomicWrite 崩溃后不残留半写主文件
  - [ ] 孤儿 tmp 被清理
  - [ ] readAll/count/initHeader/exists 持锁
  - [ ] rename 失败时走 fallback不丢数据
- 评估方法：automated-test [Robolectric] **需本地执行**
- 依赖：T-006　状态：pending

### T-008 FileBasedMarkdownFile：方法改造
- 描述：append/appendEntry（用 nextTimestamp 单调 ts，走 atomicWrite）、delete/update（改调 EntryParser.findEntryBounds/build*）、clear（不走 .bak，atomicWrite 写 FILE_HEADER）、initHeader（atomicWrite）、readAll 返回 ReadResult、readAllForExternal（readAll+stripIsolationMarkers）
- 约束：仅改 FileBasedMarkdownFile.kt；全程持锁
- 产出：FileBasedMarkdownFile.kt 完整改造 + 测试（append 单调、delete/update 命中正确条目、clear 不回滚、readAllForExternal 无 ZWSP、append/appendEntry 混合写入）
- 评估标准：
  - [ ] 连续 append 时间戳互异且单调
  - [ ] delete/update 命中目标条目（含同内容不同时间戳）
  - [ ] clear 崩溃后不还原清空前内容
  - [ ] readAllForExternal 输出无 ZWSP
- 评估方法：automated-test [Robolectric] **需本地执行**
- 依赖：T-007, T-002..T-004　状态：pending

### T-009 SAF PoC spike（前置调研）
- 描述：独立 spike 验证目标 provider（本地外存 + 用户实际云盘）的 `ParcelFileDescriptor.getFileDescriptor().sync()` 行为与 `openOutputStream("wt")` 截断窗口；结论决定云盘场景是否单独降级
- 约束：一次性调研脚本/日志，不并入主干代码；产出调研笔记
- 产出：`docs/background/saf-poc-spike.md`（调研结果：sync 可靠性、wt 截断语义、是否需云盘降级）
- 评估标准：
  - [ ] 记录本地外存 sync 行为
  - [ ] 记录至少一种云盘 provider sync 行为
  - [ ] 给出"是否云盘降级"结论
- 评估方法：manual [requires-execution] **需本地真机执行**
- 依赖：—（可与 T-002..T-008 并行）　状态：pending

### T-010 SafMarkdownFile：.pending/.bak + ensureConsistent + 持锁
- 描述：实现 SAF 软恢复——.pending+.bak 存私有目录（URI 哈希派生文件名、切文件清旧）；写前 readAll(主)→写 .bak+建 .pending；写后删 .pending；读前 ensureConsistent（调 RecoveryDecider，.pending 存在则 .bak 覆写主+删 .pending+返 ReadResult(recovered=true)）；读路径持锁
- 约束：仅改 `util/SafMarkdownFile.kt` + `data/FileRepository.kt`（.bak/.pending 私有目录管理、切文件清旧）；fd.sync 尽力不依赖
- 产出：SafMarkdownFile + FileRepository 改造 + instrumented 测试（.pending 存在→恢复、切 URI 清旧 .bak、.bak 也损坏→空+recovered）
- 评估标准：
  - [ ] .pending 存在时读返回 .bak 内容且 recovered=true
  - [ ] 切换 SAF URI 后旧 .bak 被清理
  - [ ] 恢复后 .pending 被清除、主文件被 .bak 覆写
  - [ ] 所有读写持锁
- 评估方法：automated-test [instrumented] **需本地真机执行**
- 依赖：T-006, T-009, T-004　状态：pending

### T-011 SafMarkdownFile：方法改造 + exists() 修歧义
- 描述：appendEntry/delete/update 改 EntryParser + 走 .pending/.bak 流程；clear 两模式都不走 .bak；exists() 区分 SecurityException（授权失效）vs 真不存在；readAll 返回 ReadResult、readAllForExternal
- 约束：仅改 SafMarkdownFile.kt
- 产出：SafMarkdownFile.kt 完整改造 + 测试（SAF append 单调、delete/update、clear 不回滚、授权失效 fallback、URI 失效→上层切默认）
- 评估标准：
  - [ ] SAF append 时间戳互异
  - [ ] exists() 对 SecurityException 标授权失效
  - [ ] URI 失效时上层切默认模式
  - [ ] readAllForExternal 无 ZWSP
- 评估方法：automated-test [instrumented] **需本地真机执行**
- 依赖：T-010, T-002..T-004　状态：pending

### T-012 零宽空格出口剥离
- 描述：MainScreen 的 copyContent/shareContent 改走 `readAllForExternal()`（替代直接 readAll）；验证出口内容无 ZWSP
- 约束：仅改 `ui/MainScreen.kt`（copyContent、shareContent）
- 产出：MainScreen.kt 改造 + JVM 测试（复制/分享内容断言无 `​`）
- 评估标准：
  - [ ] copyContent 走 readAllForExternal
  - [ ] shareContent 走 readAllForExternal
  - [ ] 出口内容不含 ZWSP
- 评估方法：code-review [read-only] + automated-test [JVM]
- 依赖：T-003, T-006　状态：pending

### T-013 archiveFile 重构 + 归档恢复语义
- 描述：MainScreen.archiveFile 重构走 `MarkdownFileOperations.writeAll`（不再直接 FileOutputStream，享 atomicWrite+锁+ZWSP，归档与活动文件同源同格式）；ArchiveListScreen 归档恢复改用 `appendEntry(归档原 timestamp, content)`；恢复结果 Toast `已恢复 X 条，跳过 Y 条`（Y=0 不显示后缀）；提供"查看跳过条目"入口；恢复的条目按时间戳排序回历史位置（决策 i）
- 约束：改 `ui/MainScreen.kt`（archiveFile）、`ui/ArchiveListScreen.kt`（恢复逻辑+提示+跳过清单+排序）
- 产出：MainScreen + ArchiveListScreen 改造 + JVM 测试（归档恢复去重 timestamp|content 生效、跳过计数、跳过清单内容、排序按时间戳）
- 评估标准：
  - [ ] archiveFile 经 writeAll（同源同格式）
  - [ ] 归档恢复保留原 timestamp
  - [ ] 重复条目被跳过且计数正确
  - [ ] 恢复的旧时间戳条目回到历史位置（非顶部）
- 评估方法：code-review [read-only] + automated-test [JVM]
- 依赖：T-006, T-008, T-011　状态：pending

### T-014 重复内容提示 + 恢复 Toast
- 描述：手动/分享追加后按 content 比对（忽略时间戳），命中 Toast `已追加（已有相同内容 N 条）`，5s 节流（Clock 接口注入 FakeClock 可测）；readAll 返回 ReadResult.recovered=true 时 Toast `检测到上次异常退出，已从备份恢复，请检查近期改动`
- 约束：改 MainScreen.kt；节流时间源抽 Clock
- 产出：MainScreen 改造 + JVM 测试（5s 内 3 次同内容 Toast 1 次、第 6 秒再触发、recovered→Toast）
- 评估标准：
  - [ ] 同内容 5s 内只提示一次
  - [ ] recovered=true 时触发恢复 Toast
  - [ ] Clock 可注入测试
- 评估方法：code-review [read-only] + automated-test [JVM]
- 依赖：T-006, T-008　状态：pending

### T-015 顺手修复
- 描述：exists() SecurityException 分流（T-011 已部分，此任务补上层调用方处理）；MainScreen 文件操作下放 Dispatchers.IO（防 append 变重致主线程 ANR）；删除废弃 `util/MarkdownFile.kt`；删除 MarkdownFormatter 错误的 thread-safe 注释
- 约束：跨 `ui/MainScreen.kt`、`util/MarkdownFile.kt`（删）、`util/MarkdownFormatter.kt`
- 产出：上述改动 + 确认无悬空引用（删 MarkdownFile.kt 后全仓库无 import）
- 评估标准：
  - [ ] refreshEntryCount 等文件 I/O 在 Dispatchers.IO
  - [ ] MarkdownFile.kt 已删且无引用
  - [ ] MarkdownFormatter 错误注释已删
- 评估方法：code-review [read-only]
- 依赖：T-008, T-011　状态：pending

### T-016 迁移现有测试
- 描述：MarkdownParserTest（10 处 parseMarkdownEntries）→ 迁移为 EntryParserTest 并扩充，旧文件删；ArchiveRestoreDeduplicationTest、EntryDeletionIntegrationTest 改 import 到 EntryParser；MarkdownFileDeleteTest/UpdateTest 内嵌 TestFileBasedMarkdownFile/TestUpdate 废弃，改 Robolectric 跑真 FileBasedMarkdownFile + 临时目录；接口加 appendEntry 后所有 test double 补占位
- 约束：仅改 `src/test/`；保持原有用例语义
- 产出：测试文件迁移 + 编译通过 + 现有用例通过
- 评估标准：
  - [ ] 所有测试文件编译通过（无 parseMarkdownEntries 残留引用）
  - [ ] 现有断言语义保留
  - [ ] TestFileBasedMarkdownFile 已废弃/移除
- 评估方法：automated-test [JVM] **需本地执行**
- 依赖：T-002..T-004, T-006　状态：pending

### T-017 README + 版本号 + CHANGELOG
- 描述：README 加"外部编辑器直编不支持""SAF 软恢复语义""降级兼容限制"；build.gradle.kts versionName=1.1.0、versionCode=3；CHANGELOG 加 1.1.0 条目
- 约束：改 README.md、build.gradle.kts、CHANGELOG.md
- 产出：三文件更新
- 评估标准：
  - [ ] README 含三条约束说明
  - [ ] versionName=1.1.0, versionCode=3
  - [ ] CHANGELOG 1.1.0 条目覆盖需求 36–46
- 评估方法：code-review [read-only]
- 依赖：全部实现任务　状态：pending

### T-018 全量测试 + 真机验收
- 描述：本地跑 `./gradlew test`（JVM+Robolectric）+ `./gradlew connectedAndroidTest`（instrumented）；真机验证 specs 44–46 验收场景（归档恢复追加、复制再追加往返、写中断恢复）+ 26 边界关键项
- 约束：不改代码（仅验证）；失败则回对应任务
- 产出：测试报告（通过/失败清单）
- 评估标准：
  - [ ] JVM+Robolectric 全绿
  - [ ] instrumented 全绿
  - [ ] specs 44/45/46 验收场景通过
- 评估方法：automated-test [requires-execution] **需本地真机执行**
- 依赖：全部　状态：pending

---

## 任务依赖关系

```
T-001 测试基建（独立，可最先）
T-002 EntryParser 基础 ── T-003 format/nextTimestamp/strip
│                 └── T-004 边界算法/RecoveryDecider
│                 └── T-005 ParsedEntry/LinkEntry ── T-006 接口扩展
│                                                  ├── T-007 FileBased atomicWrite+持锁 ── T-008 FileBased 方法
│                                                  ├── T-009 SAF PoC（并行）── T-010 SAF 恢复 ── T-011 SAF 方法
│                                                  ├── T-012 ZWSP 出口剥离
│                                                  ├── T-013 archiveFile+归档恢复（依赖 T-008,T-011）
│                                                  └── T-014 重复/恢复 Toast（依赖 T-008）
T-015 顺手修复（依赖 T-008,T-011）
T-016 迁移测试（依赖 T-002..T-004,T-006）
T-017 README/版本/CHANGELOG（依赖全部实现）
T-018 全量验收（依赖全部）
```

建议执行序：T-001 → T-002 → T-003 → T-004 → T-005 → T-006 → T-007 → T-008 →（T-009 spike 可并行）→ T-010 → T-011 → T-012 → T-013 → T-014 → T-015 → T-016 → T-017 → T-018

---

## 执行日志

### 2026-07-20 T-001 测试基建 — 实现完成（验证待本地）
- `build.gradle.kts`：defaultConfig 加 `testInstrumentationRunner`；android 块加 `testOptions.unitTests.isIncludeAndroidResources=true`；dependencies 加 Robolectric 4.13、androidx.test:core/ext-junit（testImplementation）、androidx.test:runner/rules/ext-junit、compose ui-test-junit4/ui-test-manifest（androidTestImplementation）。
- 验证：[requires-execution] **需本地** `./gradlew :Appendo:assembleDebugAndroidTest`（构建 androidTest APK）+ `./gradlew :Appendo:testDebugUnitTest`（JVM 单测；Android 模块的 JVM 单测任务是 `testDebugUnitTest`，聚合 `test` 不接收 `--tests`）。

### 2026-07-20 T-002 EntryParser 基础 — 实现完成（验证待本地）
- 新建 `util/ParsedEntry.kt`（rawTimestamp, content）、`util/EntryParser.kt`（ISOLATION_MARKER、宽松正则 getTimestampRegex/isTimestampLine、parse 含 ZWSP 还原 restoreLine）。
- 新建 `test/util/EntryParserTest.kt`（12 用例：秒/毫秒/混合识别、尾文本拒绝、ZWSP 前缀拒绝、注入行不切分、ZWSP 还原、用户自有 ZWSP 保留、多 ZWSP 剥离、空/header）。
- 验证：[JVM] **需本地** `./gradlew :Appendo:testDebugUnitTest --tests "com.yiyue31.android.appendo.util.EntryParserTest"`（或 `:Appendo:testDebugUnitTest` 跑全部 JVM 单测）。
- 注：现有 `MainScreen.parseMarkdownEntries` 暂保留，待 T-005/T-016 迁移；本任务未触碰生产调用方，零破坏性。

### 2026-07-20 T-002 验证 — 通过
- 本地 `./gradlew :Appendo:testDebugUnitTest`（EntryParserTest）：**BUILD SUCCESSFUL**，地基确认。

### 2026-07-20 T-003 + T-004 EntryParser 写侧/边界/恢复 — 实现完成（验证待本地）
- 扩展 `util/EntryParser.kt`：java.time 时间戳（timestampZone/timestampFormatter/读用宽松 formatter via DateTimeFormatterBuilder）、`format`（含 ZWSP 隔离 isolateContent/isolateLine 幂等）、`nextTimestamp`（反向扫描+Instant 比较+fallback）、`stripIsolationMarkers`、`findEntryBounds`/`buildDeletedLines`/`buildUpdatedLines`（边界算法，找首条+区间，不依赖唯一性）、`RecoveryAction`+`decideRecovery`（SAF 软恢复判定纯函数）。
- 扩展 `test/util/EntryParserTest.kt`：新增 format/幂等/往返、nextTimestamp 五例（空/旧/等/时钟回拨/无有效 last）、strip 两例、findEntryBounds 三例、buildDeleted/buildUpdated 三例、decideRecovery 三例。
- 验证：[JVM] **需本地** `./gradlew :Appendo:testDebugUnitTest --tests "com.yiyue31.android.appendo.util.EntryParserTest"`。

### 2026-07-20 T-003 + T-004 验证 — 通过（修正测试断言后）
- 首跑 2 个 findEntryBounds 用例红——是测试手数下标错（漏数空串），非函数 bug；改为 indexOf 动态算下标后 **BUILD SUCCESSFUL**，EntryParser 全套（~33）绿。

### 2026-07-20 T-005 数据模型分层 — 实现完成（验证待本地）
- 新建 `ui/LinkEntry.kt`：data class `LinkEntry(timestamp, content)` + **派生属性** `timestampDisplay`（剥离毫秒）+ companion `from(ParsedEntry)`。**构造签名不变（2 参）**，现有 16 处 `LinkEntry(ts,content)` 构造零改动（含 ArchiveRestoreDeduplicationTest）。
- `EntryParser` 加 `displayTimestamp(rawTimestamp)`。
- MainScreen：移除内联 LinkEntry 定义；`parseMarkdownEntries` 委托 `EntryParser.parse + LinkEntry.from`；编辑对话框时间戳改显 `timestampDisplay`。
- EntryListScreen 卡片时间戳改显 `timestampDisplay`。key/删除/更新仍用 `timestamp`（raw 唯一 key）。

### 2026-07-20 T-006 接口扩展 — 实现完成（验证待本地）
- 新建 `util/ReadResult.kt`（content, recovered）。
- `MarkdownFileOperations` 加 3 个**带默认实现**的方法（保持编译绿，FileBased/Saf 无需立即改）：`appendEntry`(默认→append)、`readAllForExternal`(默认→strip+readAll)、`readAllWithStatus`(默认→ReadResult(readAll,false))。
- ⚠️ **偏离 design v3.1（需你认可）**：design 写"readAll→ReadResult"，实现改为"**readAll 保持 String + 新增 readAllWithStatus**"。语义等价（无全局状态、recovery 经返回值），但避免 readAll 签名变更引发的 ~8 处涟漪（含 test double），利于增量开发与逐步验证。SAF 在 T-010 覆写 readAllWithStatus 返回 recovered=true。
- 验证：**需本地** `./gradlew :Appendo:testDebugUnitTest`（全量 JVM：确认编译 + 无回归 + EntryParser）。

### 2026-07-20 T-005 + T-006 验证 — 通过
- 全量 `./gradlew :Appendo:testDebugUnitTest`：BUILD SUCCESSFUL。LinkEntry 派生 timestampDisplay + 接口默认方法无回归。偏离（readAll 保持 String + readAllWithStatus）认可。

### 2026-07-20 T-007 FileBased atomicWrite + 读路径持锁 — 实现完成（验证待本地）
- 新增私有 `atomicWrite(content: ByteArray)`：同目录 createTempFile（防 EXDEV）→ `fd.sync` → `renameTo` → 失败 fallback 覆写；写前清理孤儿 tmp（`file.name + ".tmp"` 前缀）；调用方持锁。
- `readAll`/`count`/`exists`/`initHeader` 全部 `synchronized(FileOperationLock)`（修正评审 R2：原不持锁）。
- `count` 改用 `EntryParser.getTimestampRegex()`（宽松，数毫秒条目，修 P1-5）。
- `clear`/`initHeader`/`writeAll` 改走 `atomicWrite`（简单写入原子化）；`clear` 不走 .bak。
- append/deleteEntry/updateEntry 暂留旧逻辑（仍 synchronized），EntryParser 改造在 T-008。
- atomicWrite 专属 Robolectric 测试暂缓（需先确认 Robolectric 运行时在本环境可用），随 T-008 或专设任务补。
- 验证：**需本地** `./gradlew :Appendo:testDebugUnitTest`（确认编译 + 无回归；atomicWrite 经 initHeader/clear/writeAll 间接验证）。

### 2026-07-20 T-007 验证 — 通过
- 全量 JVM 单测 BUILD SUCCESSFUL；atomicWrite + 读路径持锁无回归。

### 2026-07-20 T-008 FileBased 写侧 EntryParser 改造 — 实现完成（验证待本地）
- append → `EntryParser.nextTimestamp`(单调毫秒) + `EntryParser.format`(ZWSP 隔离) + atomicWrite。
- 新增 `appendEntry(timestamp, content)` 覆写（真正保留时间戳，归档恢复用）+ 私有 `appendEntryInternal`。
- deleteEntry/updateEntry → `EntryParser.findEntryBounds` + `buildDeletedLines`/`buildUpdatedLines` + atomicWrite（各从 ~80 行缩到 ~10 行）。
- 移除不再使用的 BuildConfig import。
- 新增 Robolectric 集成测试 `FileBasedMarkdownFileTest`（10 用例，直测真类）：initHeader、append 毫秒时间戳、单调、appendEntry 保留 ts、delete/update、ZWSP 隔离、clear、无孤儿 tmp。
- 验证：**需本地** `./gradlew :Appendo:testDebugUnitTest`（含 Robolectric；首次运行下载 android-all jar，需网络。若 Robolectric 运行时报错而非代码错，转 instrumented）。

### 2026-07-20 T-008 验证（首次）— Robolectric 不可用（环境 SSL 问题，非代码）
- 10 个 FileBasedMarkdownFileTest 用例全失败，堆栈 `MavenArtifactFetcher → SSLHandshakeException → SunCertPathBuilderException`——Robolectric 无法从 Maven Central 下载 android-all jar（本环境 SSL 握手失败，疑似 `Invalid https.proxyPort` 代理配置所致）。其余 79 测试全过。
- **处置**：本环境 Robolectric 跑不了。FileBasedMarkdownFileTest 改纯 JVM + `mock<Context>()` 占位（context 未使用），文件走真实 java.io.File + 临时目录。
- **测试策略调整**（重要）：纯逻辑（EntryParser）+ FileBased 用纯 JVM；**SAF（ContentResolver）/UI 必须走 instrumented（真机/模拟器）**，本环境无法跑。SAF PoC（T-009）与 SAF 实现（T-010/T-011）的验证依赖真机。

### 2026-07-20 T-008 验证 — 通过（纯 JVM）
- FileBasedMarkdownFileTest 改纯 JVM + `mock<Context>()` 后 BUILD SUCCESSFUL（89 测试绿）。FileBased 默认模式写侧端到端验证通过。

### 2026-07-20 T-012 复制/分享出口剥离 — 实现完成
- MainScreen copyContent/shareContent 改用 `readAllForExternal`（出口剥离 ZWSP，specs 38/B2）。

### 2026-07-20 T-013 归档恢复语义 + archiveFile 重构 + 排序(i) — 实现完成（验证待本地）
- ArchiveListScreen 恢复改用 `appendEntry(原 timestamp, content)`（**B3 硬伤修复**：去重 key 真正生效）；透明计数 toast "已恢复 X 条，跳过 Y 条已存在"（Y=0 不显示后缀，specs 43）。
- 跳过清单弹窗（增强）暂缓；透明计数已满足 specs 43 必须。
- archiveFile 重构走 `writeAll`（CB6：享 atomicWrite + 锁 + 同源同格式 ZWSP），不再直接 FileOutputStream。
- 归档长按复制走 `stripIsolationMarkers`（出口干净）。
- EntryListScreen 显示 `entries.reversed()` → `sortedByDescending{timestamp}`（决策 i：恢复的旧时间戳条目回历史位置）。
- 验证：**需本地** `./gradlew :Appendo:testDebugUnitTest`（编译 + 无回归）。

### 2026-07-20 T-012 + T-013 验证 — 通过
- 全量 JVM 单测 BUILD SUCCESSFUL；出口剥离、归档恢复 appendEntry、排序(i) 无回归。

### 2026-07-20 T-014 重复内容提示 + 恢复 Toast — 实现完成（验证待本地）
- 新增 `util/DuplicateHintThrottle`（object，5s 节流，clock 可注入）+ `DuplicateHintThrottleTest`（5 用例：首次/窗口内/窗口边界/不同内容独立/3 次内 1 次）。
- MainScreen 手动输入追加：追加前按内容查重；命中且节流通过 → Toast "已追加（已有相同内容 N 条）"，否则 "内容已追加"（specs 42，非阻塞）。
- MainScreen refreshEntryCount：改 `readAllWithStatus`；recovered=true 时 Toast "已从备份恢复，请检查近期改动"（specs 46；默认模式恒 false，SAF T-010 覆写后生效）。
- ShareReceiverActivity：重构提取 markdownFile，追加后按内容查重 → Toast "Appendo已收到（已有相同内容 N 条）"（specs 42 分享路径）。
- 验证：**需本地** `./gradlew :Appendo:testDebugUnitTest`（编译 + DuplicateHintThrottleTest + 无回归）。

### 2026-07-21 T-014 验证 — 通过
- 全量 JVM 单测 BUILD SUCCESSFUL（含 DuplicateHintThrottleTest 5 用例）。

### 2026-07-21 T-015 清理 — 实现完成（验证待本地）
- 删除废弃 `util/MarkdownFile.kt`（grep 确认零外部引用，仅自身定义）。
- MainScreen.refreshEntryCount 文件 I/O + 解析下放 `Dispatchers.IO`（中-7；state 更新与 Toast 留主线程）。
- MarkdownFormatter.formatEntry 注释纠正（SimpleDateFormat 非线程安全；过渡期 SafMarkdownFile 用，T-011 后移除）。
- exists() SecurityException 分流属 SAF，随 T-011 做。
- onClick 处理器（append/delete 等）仍主线程：一次性用户操作可容忍；全量 IO 迁移随 Phase B ViewModel。
- 验证：**需本地** `./gradlew :Appendo:testDebugUnitTest`（编译 + 无回归）。

### 2026-07-21 T-015 验证 — 通过
- 全量 JVM 单测 BUILD SUCCESSFUL；删 MarkdownFile.kt + IO 下放 + 注释修正无回归。

### 2026-07-21 T-016 迁移现有测试 — 实现完成（验证待本地）
- 删除 `MarkdownFileDeleteTest.kt` + `MarkdownFileUpdateTest.kt`（含过时的 TestFileBasedMarkdownFile / TestUpdateFileBasedMarkdownFile double——平行实现，掩盖真类，P0-1）。覆盖已由 `FileBasedMarkdownFileTest`（真类，T-008）+ `EntryParserTest`（边界逻辑）取代且更优。
- 保留：MarkdownParserTest（parseMarkdownEntries 包装器仍被调用方使用，测试仍绿）、ArchiveRestoreDeduplicationTest（去重算法）、EntryDeletionIntegrationTest（真文件集成）、FileRepositoryFirstLaunchTest。
- parseMarkdownEntries 包装器暂保留（薄委托，无碍；调用方迁直调随 Phase B）。
- 验证：**需本地** `./gradlew :Appendo:testDebugUnitTest`（编译 + 剩余测试仍绿，测试数减少）。

### 2026-07-21 T-016 验证 — 通过
- 全量 JVM 单测 BUILD SUCCESSFUL（测试数减少，剩余全绿；double 已清除）。

### 2026-07-21 T-017 README + 版本 + CHANGELOG — 实现完成
- README 加「已知约束与数据安全说明」（外部编辑器不支持 / SAF 软恢复 / 不建议降级）+ 版本历史 1.1.0 行。
- CHANGELOG 加 1.1.0 条目（覆盖需求 36-46）。
- build.gradle.kts: versionCode=3, versionName="1.1.0"。
- 验证：文档/配置类，随 T-018 一并真机验收。

### 阶段小结（2026-07-21）
- **默认（FileBased）模式已全面 v1.1 化并通过 JVM 验证**：毫秒时间戳、ZWSP 隔离、单调防碰撞、原子写、EntryParser 化 delete/update、出口剥离、归档恢复 appendEntry、重复提示、恢复 Toast、IO 下放。
- **SAF 模式尚未升级**（仍用旧 SafMarkdownFile：秒级时间戳、旧 delete/update、无 .pending/.bak）。SAF 升级 = T-009/010/011，需真机验证。
- **待办（均需真机/模拟器）**：T-009（SAF PoC spike）、T-010（SAF .pending/.bak + ensureConsistent）、T-011（SAF appendEntry + exists 修歧义）、T-018（全量真机验收 specs 44-46）。

### 2026-07-21 T-017 验证 — 通过
- 全量 JVM 单测 BUILD SUCCESSFUL。

### 2026-07-21 T-009 + T-010 + T-011 SAF 编码 — 实现完成（验证待真机）
- T-009 SAF PoC spike：写成 `docs/background/saf-poc-spike.md`（待真机执行；代码已防御性实现，不依赖 PoC）。
- T-010 SafMarkdownFile 重写：`readAllWithStatus` 覆写（ensureConsistent + ReadResult）、readAll 委托、读路径全持锁、count 用 EntryParser 宽松正则。
- T-010 `.pending/.bak` 软恢复：recoveryFile（私有目录，URI 哈希派生）、ensureConsistent（EntryParser.decideRecovery）、safAtomicWrite（备份+标记+写+删标记）、writeMain（openFileDescriptor+sync，回退 openOutputStream）。
- T-011 SAF 方法 EntryParser 化：append/appendEntry（nextTimestamp+format）、delete/update（findEntryBounds+build*）、clear（不走 .bak 直接 writeMain）、initHeader/writeAll。
- T-011 exists() 区分 SecurityException（授权失效，单独记录）vs 真不存在。
- FileRepository：加 clearSafRecoveryFiles（按 URI 哈希前缀清理），saveFileUri/clearFileUri 切换时清旧恢复文件（防串档）。
- 验证：**需真机** instrumented 测试（SAF ContentResolver）+ 手动验收。JVM 单测应仍绿（SAF 改动不影响 JVM 测试，但需确认整体编译通过）。
- ⚠️ **SAF 代码未经真机验证**——提交后务必在真机/模拟器上验证 specs 44-46 + SAF 恢复（.pending 残留恢复、切文件清旧、授权失效降级）。

### 阶段总结（2026-07-21）
- **全部编码任务完成**（T-001 ~ T-017）。默认模式 JVM 验证通过；SAF 模式编码完成但待真机验证（T-018）。
- **唯一待办：T-018 全量真机验收**（specs 44/45/46 + 26 边界关键项 + SAF 恢复）。
- 注意：项目 CLAUDE.md「测试通过后才能提交」与「SAF 未真机验证」存在张力——提交前请知悉 SAF 路径未经真机测试。
