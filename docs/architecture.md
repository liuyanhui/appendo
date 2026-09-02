# Appendo 架构文档

> **本文定位（维护规则，任何修订须遵守）**：本文面向**人类程序员**，服务三个用途——
> 1. **学习**：理解系统现状与设计理由（"是什么、为什么"）；
> 2. **评审（review）**：作为代码评审依据——用 §3/§4 的不变量与 §11 的清单核对改动；
> 3. **验收**：作为功能验收依据——用 §12 的验收手段逐项验证。
>
> 因此本文是"现在的系统长什么样"的**唯一权威来源**，人与 AI 共用（AI 不另设架构文档）；内容须与代码一致，不一致时以代码为准并**回头修订本文**；只写"是什么、为什么"，不写过程史（历史见 [design.md](./design.md)、[design/](./design/)、[specs.md](./specs.md)）；结论可核验（引用 `文件#符号`，不引行号）。

## 修订记录

| 日期 | 变更人 | 变更摘要 |
|------|--------|---------|
| 2026-08-31 | Yiyue + Claude | 初版：基于 v1.2.0 源码逐文件核实后的现状快照（此前架构知识散落在多份过期文档中，本文收敛为单一权威源） |
| 2026-08-31 | Yiyue + Claude | 三角色评审修订（架构师/资深工程师/新程序员并行评审）：修正 2 处事实错误与多处以偏概全；如实记录评审揭示的 4 项代码级风险（K6–K9，见 §8.1 与 debt-tracker TD-012~015） |
| 2026-08-31 | Yiyue + Claude | 按确认的文档目标重定位：面向人类的学习/review/验收三用途（规则固化到文首）；新增 §11 变更评审清单与 §12 验收手段 |
| 2026-08-31 | Yiyue + Claude | v1.2.1 技术债批次（TD-010~015）落地后同步：K1/K2/K6~K9 标注已修复，§3.1/§3.3/§4.2/§4.4/§5.3 更新为修复后现状 |
| 2026-08-31 | Yiyue + Claude | 四角色评审（架构师/工程师/测试/产品经理）：修正 v1.2.1 收尾漏改段落（§4.3 formatEntry 遗留描述、§8.3 双 action）、毫秒起始版本（1.0.2）、§3.3 不变量 2 与出口豁免行、§6.1 补 Receiver 主线程 I/O、贪睡漂移如实记录；§11 增 manifest/Intent-SP/文档同步清单组；§12 重写为可执行手段 + 执行状态；登记评审新发现（K10/TD-016 删至 0 条 UI 滞留回归等，TD-016~020） |
| 2026-09-01 | Yiyue + Claude | 四项产品决策落地（用户拍板 AAAA）：①输入侧去 trim（解析侧 trim 保留并在 §3.1/§9 声明边界）②allowBackup 拥抱云备份（§5/§8.3 口径更新）③旧数据首启一次性迁移（`migrateLegacyIsolation` + MainScreen 启动钩子 + 6 用例）④贪睡漂移定位有意取舍（§5.2）；§9 增四条 ADR |
| 2026-09-02 | Yiyue + Claude | v1.2.2 收尾：K10/K11 修复落地；真机定向验收五项通过并更新 §12 状态列；真机发现并修复 K12（SAF 授权失效静默，TD-021 受控复现+活体验收对话框自愈）与 K13（归档详情徽标）；重启提醒延迟登记 TD-022 待决策 |
| 2026-09-02 | Yiyue + Claude | TD-022 决策（用户拍板）：**维持现状**（解锁后重注册/补发），成本/风险评估与理由入 §5.3 注记、§9 ADR 与 debt-tracker |

---

## 1. 系统概览

appendo 是一个极简 Android 速记应用：通过系统分享或手动输入收集文字，全部追加到一个 Markdown 文件里；支持条目编辑/删除、归档、本地提醒、添加到日历。

核心设计判断：**Markdown 文件是唯一数据源（source of truth）**。没有数据库、没有 ORM——条目即文件里的文本块，时间戳标题行既是边界也是主键。整个系统的复杂度都源于把"一个人类可读的文本文件"当数据库用时必须回答的问题：

- 用户内容可能长得像条目边界 → 用零宽空格隔离（§3.3）
- 两个 Activity 会并发写 → 全局锁 + 原子写（§4）
- 文件可能被系统杀进程写到一半 → 原子写/软恢复（§4.2）
- 提醒不能写进文件（污染正文）→ sidecar 存储 + 与条目对账（§3.4、§5）

### 1.1 分层与依赖方向

```
┌─────────────────────────────────────────────────────────┐
│ Activity 层（入口；业务基本下沉到子包）                     │
│   MainActivity（Compose 导航宿主）                        │
│   ShareReceiverActivity（分享写入，无 UI）                │
├─────────────────────────────────────────────────────────┤
│ ui/（Compose 界面 + 状态持有）                            │
│   MainScreen · EntryListScreen · ArchiveListScreen …     │
├──────────────────────┬──────────────────────────────────┤
│ data/（偏好与文件管理） │ reminder/（提醒子系统，§5）        │
│   FileRepository      │   AlarmScheduler · Notification…  │
│   ArchiveRepository   │   ReminderAlarm/BootReceiver      │
│                      │   ReminderStore（sidecar 单例）     │
├──────────────────────┴──────────────────────────────────┤
│ util/（纯逻辑核心，不碰 Android UI，多数可纯 JVM 单测）      │
│   EntryParser（条目知识唯一收敛点）                        │
│   MarkdownFileOperations 接口 + FileBased/Saf 两实现      │
│   ReminderLogic / ReminderMetaCodec / ReminderText …     │
└─────────────────────────────────────────────────────────┘
```

依赖规则（实际代码遵循）：

- **util 不依赖 ui / data / reminder**（`EntryParser`、`ReminderLogic`、`ReminderMetaCodec`、`ReminderText`、`CalendarEntryMapper`、`DuplicateHintThrottle` 全是纯函数/纯数据；`FileBasedMarkdownFile`、`SafMarkdownFile` 仅依赖 `android.content` 与 `androidx.documentfile` 做 I/O）。
- ui / data / reminder → util 单向依赖。一条包间横向依赖要注意：`reminder` 会读 `data`（`findEntryContent`，ReminderAlarmReceiver.kt 顶层函数，直接构造 `FileRepository` 取当前文件）；另注意 util 内部被共享的除 `EntryParser` 外，还有 `MarkdownFormatter` 常量与 `ReminderMeta` 数据类型。
- `ui.MainScreen` 直接创建 `MarkdownFileOperations` 实例做读写——**没有 ViewModel、没有 Repository 中间层**。这是刻意保留的简单性（也是已知债务，见 §8 与 debt-tracker TD-008）。

### 1.2 双 Activity

| Activity | 职责 | 要点 |
|---|---|---|
| `MainActivity` | Compose 导航宿主（main → archive_list → archive_detail） | 提醒通知点按经 `onNewIntent`/`onCreate` 读 extra 传入深链时间戳 |
| `ShareReceiverActivity` | 接收 `ACTION_SEND`（text/plain），IO 协程里写入后 Toast + `finish()` | `noHistory` + `excludeFromRecents` + 空 taskAffinity，用户无感 |

两 Activity 同进程（manifest 无 `android:process`），这是 `FileOperationLock` 进程内锁有效的前提。

## 2. 模块地图

| 位置 | 职责 | 关键文件 |
|---|---|---|
| 根包 | 入口 | `MainActivity`（导航）、`ShareReceiverActivity`、`AppendoApplication`（建遗留通知渠道） |
| `util/` | **条目与文件的协议层** | `EntryParser`、`MarkdownFileOperations`（接口+锁）、`MarkdownFileFactory`、`FileBasedMarkdownFile`、`SafMarkdownFile`、`MarkdownFormatter`（仅常量：文件头/归档文件名/分隔形态）、`ParsedEntry`/`ReadResult`（含 `failed` 标志） |
| `util/`（提醒纯逻辑） | 提醒的纯函数 | `ReminderMeta`/`Recurrence`、`ReminderMetaCodec`、`ReminderLogic`、`ReminderText` |
| `util/`（其他） | 独立小工具 | `CalendarEntryMapper`+`CalendarLauncher`（添加到日历）、`DuplicateHintThrottle`（重复内容提示：追加内容与已有条目相同时 Toast「已有相同内容 N 条」；仅手动输入路径经 5 秒节流，分享路径不节流、每次报数） |
| `data/` | 存储偏好与归档管理 | `FileRepository`（SP 偏好 + URI 权限）、`ArchiveRepository`+`ArchiveFile` |
| `reminder/` | 提醒的 Android 侧 | `ReminderStore`（sidecar 单例）、`AlarmScheduler`、`NotificationHelper`、`ReminderAlarmReceiver`、`ReminderBootReceiver`、`ReminderIntents` |
| `ui/` | Compose 界面 | `MainScreen`（主界面+全部对话框）、`EntryListScreen`（可复用列表）、`ArchiveListScreen`、`ArchiveDetailScreen`、`ReminderTimePickerDialog`、`LinkEntry`、`AppColors`、`ToastUtils` |

**`EntryParser` 的地位**：所有"什么是一条条目"的知识（判定/解析/格式化/边界/时间戳单调/隔离标记/SAF 恢复判定）都收敛在这一个 object 里，纯函数。写侧（`format`）和读侧（`parse`）对称，两个存储实现共享同一套算法——v1.1 之前这套逻辑在两个实现里各复制一份，曾因此产生行为分叉。

## 3. 数据协议

这是本项目最重要的部分。改任何一条都可能破坏既有用户数据，先读这里再动 `EntryParser`。

### 3.1 Markdown 条目格式

文件头 + 条目块。一条条目由 `EntryParser.format` 写成：

```markdown
# Appendo

---

## 2026-08-12 14:30:00.123

用户内容（原样保留）

---

## 2026-08-12 15:00:01.456

另一条

---
```

（与 `EntryParser.format` 的确切输出逐字符一致：`\n---\n\n## {ts}\n\n{content}\n\n---\n`）

- 编码 UTF-8 无 BOM；内容**原样写入**（换行、缩进、符号不处理），唯一的**写侧**例外是 §3.3 的隔离标记。
- 条目边界 = **时间戳标题行**（不是 `---`，因为用户内容可能包含 `---`）。`---` 只是视觉分隔；删除条目时若前面紧跟 `---` 会连带删掉。
- **读侧的隔离与丢弃边界**：内容中恰为 `---` 或以 `# Appendo` 开头的行，写入侧（v1.2.1 起，TD-015）与时间戳行同样加 ZWSP 隔离、读侧还原——**编辑不再丢行**。旧条目由**首启一次性迁移**（`EntryParser.migrateLegacyIsolation`，2026-09-01 决策③）补隔离：覆盖"被内容夹住的 `---` 行"与"文件头之外的 `# Appendo` 行"；残余不可恢复的边界：已被旧格式当作边界拆分的**时间戳形态**内容行、条目末尾紧贴收尾分隔线的 `---`（保守规则宁漏勿误）。另外：`parse` 会丢弃无内容的条目、对整段内容 `trim()`——**trim 保留**（模板空行与用户首尾空白不可区分，显示层去除首尾空白行）；输入侧自 2026-09-01 起不 trim（决策①，分享内容首尾空白原样写入文件）。
- 历史文件（v1.0.x）的时间戳是秒级，新文件是毫秒级，两者混存合法（§3.2）。

### 3.2 时间戳

| 属性 | 值 | 说明 |
|---|---|---|
| 写入格式 | `yyyy-MM-dd HH:mm:ss.SSS`（Locale.US，系统默认时区，java.time） | 自 **1.0.2** 起新条目毫秒级（1.0.x 为秒级；specs 修订记录称该批次为"v1.1"系设计阶段代号，实际发布版本 1.0.2） |
| 解析正则（宽松） | `^## \d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}(\.\d{1,3})?$` | 同时匹配秒级旧条目与 0–3 位毫秒，**所有**解析/计数必须用它 |
| 角色 | 条目主键 | 删除/更新/去重都用完整时间戳串；**LazyColumn key 是 `"${timestamp}_$index"` 复合键**（原因见下"重复时间戳"） |
| 单调防碰撞 | `EntryParser.nextTimestamp`：反向扫描最后一条时间戳，新值 = `max(now, last+1ms)` | 同毫秒连续追加不重号；仅约束 `append()`（now 路径），`appendEntry` 允许插入旧时间戳（归档恢复） |
| UI 显示 | `EntryParser.displayTimestamp` 剥掉毫秒段 | 用户永远看不到 `.SSS` |

字符串降序 == 时间降序（零填充 ISO 风格），所以列表排序直接 `sortedByDescending { timestamp }`，恢复的旧条目自动回到历史位置。

**重复时间戳是真实存在的**：`appendEntry` 不查碰撞——归档恢复按 `timestamp|content` 去重，**同时间戳不同内容**的条目（典型：归档后编辑过该条再恢复）会被同时追加；v1.0.x 秒级旧数据同秒两条也可能重复。后果：

- `EntryParser.findEntryBounds` 的语义是**命中第一条**匹配——删除/更新重复时间戳条目时，操作的是第一条，不一定是用户点的那条；
- sidecar key = 时间戳——一条提醒会"覆盖"同时间戳的全部条目；
- LazyColumn 用复合 key 正是为了容忍它。严格唯一性只由 `append()`（now 路径）的单调时间戳保证。

### 3.3 内容隔离（ZWSP）与出口剥离——两条不变量

**问题**：用户收集的内容里可能有一行恰好是 `## 2026-01-01 10:00:00`，会被解析器误判成条目边界，导致条目被"劈开"。

**方案**：零宽空格 U+200B（`EntryParser.ISOLATION_MARKER`）。

> **不变量 1（写入隔离）**：任何写入路径（`format`/`buildUpdatedLines`）都会对"去掉前导 ZWSP 后恰好匹配时间戳行格式"的内容行加一个 ZWSP 前缀；已带前缀则不加（幂等，防 read-modify-write 重复叠加）。
>
> **不变量 2（出口剥离）**：任何"内容离开本应用"的出口（复制、分享、日历预填、提醒通知标题）必须剥离标记——统一走 `EntryParser.stripIsolationMarkers`（仅剥"行首 ZWSP + 紧跟三类隔离形态之一"的行；**非边界形态行**的用户 ZWSP 不动，但边界形态行前的多个前导 ZWSP 会被整体剥除）。读侧 `parse` 同样在收集内容时还原。

当前出口清单（新增出口时必须加进这张表）：

| 出口 | 剥离方式 |
|---|---|
| 复制全部 / 分享全部（`MainScreen`） | 显式走 `readAllForExternal()`（= readAll + `stripIsolationMarkers`） |
| 添加到日历（`CalendarEntryMapper.map`） | 显式 `stripIsolationMarkers` |
| 提醒通知标题/正文（`ReminderText.title`） | 显式 `stripIsolationMarkers` |
| 归档列表长按复制归档（`ArchiveListScreen`） | 显式 `stripIsolationMarkers` |
| **长按复制单条**（主列表 / 归档详情页） | **经 `parse` 产物天然干净**——`parse` 收集内容时已用 `restoreLine` 还原（与 `stripIsolationMarkers` 内部同一函数），无需再剥 |
| 打开文件（`MainScreen#openFile` → 外部查看器） | **有意不剥离**（原文含 ZWSP 直出）——外部编辑器/查看器场景按已知约束不受支持（§8.2）；新增出口勿仿效，除非同样声明为不支持场景 |

**内部读取用 `readAll()`（保留标记）**，不要拿 `readAllForExternal` 的结果去做解析。

**隔离形态清单（v1.2.1 起，TD-015）**：时间戳行、恰为 `---`、以 `# Appendo` 开头，共三类；**隔离谓词与解析跳过谓词共用 `EntryParser.isSkippableLine`，不可分叉**（这是 TD-015 的根因修复原则）。旧条目经首启一次性迁移补隔离（§3.1，残余边界见该节）。

已知边界：历史归档（v1.1 之前创建）无 ZWSP 保护；外部编辑器直编 `Appendo.md` 不受支持（复制/分享是内容离开应用的主路径，为其保真；README 已声明）。

### 3.4 提醒 sidecar

提醒元数据**不写进 Markdown**（保持正文干净），存独立 SharedPreferences `appendo_reminders`：

- key = `reminder_<条目完整时间戳>`（与 Markdown 条目同主键——删除条目即可联动删提醒）
- value = `ReminderMetaCodec` 定长 4 段串 `triggerAt|fired|snoozedUntil|recurrence`
- `triggerAt`/`snoozedUntil` 是 epoch 毫秒**绝对瞬时**（与时区无关；**禁止**从时间戳字符串反推比较）
- per-key 独立写（不做大 JSON blob 的读改写，避免竞态）
- `effectiveTrigger` = 贪睡过用 `snoozedUntil`，否则 `triggerAt`

设计取舍：sidecar 与文件必然出现不一致（条目被外部改掉、换 SAF 文件），所以子系统内置**对账**（§5.4）而非追求强一致。

### 3.5 归档文件

- 命名 `Appendo_yyyyMMdd_HHmmss.md`；**永远存应用私有外部目录** `getExternalFilesDir`（即使当前活动文件是 SAF 模式）。
- 识别规则：同目录下文件名以 `Appendo_` 开头、`.md` 结尾（`ArchiveRepository`）。
- 创建：走 `FileBasedMarkdownFile.writeAll`（享原子写+锁，与活动文件同格式，含 ZWSP）。
- 恢复（归档管理页左滑）：解析归档与当前文件，按 `timestamp|content` 组合键去重，对新增条目 `appendEntry(原时间戳, 内容)`——保留原时间是去重真正生效的前提。

### 3.6 SharedPreferences 清单

| 文件 | 键 | 用途 |
|---|---|---|
| `appendo` | `use_saf` / `file_uri` / `file_last_modified` | 存储模式、SAF URI、**逻辑 mtime**（§6.3） |
| `appendo_reminders` | `reminder_<ts>` | 提醒 sidecar（§3.4） |

> `appendo` 这个名字是历史沿革（更早叫 `link_appending`）；老设计文档里写的 `link_appending` 已过时。

## 4. 存储双模式与完整性

### 4.1 双模式

| | 默认模式 | SAF 模式 |
|---|---|---|
| 实现 | `FileBasedMarkdownFile` | `SafMarkdownFile` |
| 位置 | `getExternalFilesDir/Appendo.md` | 用户经 SAF 选择的外部文件（URI） |
| 进入方式 | 首次启动默认 / URI 失效自动回退 | 首启引导对话框或菜单"自定义目录" |
| 原子性 | **真原子**（temp+fsync+rename） | **软恢复**（.pending/.bak） |

构造：`MainScreen` 与 `findEntryContent`（ReminderAlarmReceiver.kt 顶层函数）经 `MarkdownFileFactory.create(context, useSAF, fileUri, defaultFile)`；**`ShareReceiverActivity` 因需先做 URI 有效性判定而直接实例化两个实现类——改工厂签名/新增存储模式时必须同步检查这里**。接口 `MarkdownFileOperations` 定义 `append/appendEntry/readAll/readAllWithStatus/readAllForExternal/writeAll/clear/exists/count/initHeader/deleteEntry/updateEntry`。两实现算法同源（都调 `EntryParser`），差异只在 I/O 原语。

SAF 模式回退：由两个入口各自检查 `isFileUriValid()`，失效即 `clearFileUri()`（先清偏好再释放权限，防中途崩溃永久丢 URI）后回默认文件——`MainScreen` 在启动 `LaunchedEffect` 里检查一次，`ShareReceiverActivity` 每次写入前检查。（`SafMarkdownFile.exists()` 捕获 SecurityException 只记日志返回 false，不单独触发回退。）首启时 `isFirstLaunch()` 为真会弹引导对话框建议把文件放外部存储（重装不丢数据）。

**SAF 授权失效自愈（v1.2.2，TD-021）**：实测（realme）`takePersistableUriPermission` 未能持久化 + 覆盖安装会清除授权，而 `isFileUriValid()` 此时常仍返回 true——`MainScreen.refreshEntryCount` 检测到"SAF 模式 + 读失败"时弹「数据文件访问已失效」对话框（[重选文件] 复用 `changeFileLauncher` / [回退默认] `clearFileUri`），不再静默空列表；`ShareReceiverActivity` 在 SAF 写失败时 Toast 明示"授权可能已失效，请打开 appendo 重选"，自动回退默认时提示"已保存到默认文件"（防数据无声分家）。

### 4.2 原子写语义

**默认模式（真原子）** `FileBasedMarkdownFile.atomicWrite`：
同目录临时文件（避免跨文件系统 rename EXDEV）→ `fd.sync()` → `renameTo`，rename 失败退化为直接覆写；写前清理上次崩溃残留的孤儿 tmp。任何时刻崩溃，文件要么是旧内容要么是新内容。

**SAF 模式（软恢复）** `SafMarkdownFile.safAtomicWrite`：SAF 无原子重命名能力，承诺降级为——

1. 写前（持锁）：当前内容备份到 `.bak` + 建 `.pending` 标记（均存应用私有目录，文件名按 URI 哈希派生，切换文件时清理防串档）
2. 写主 URI（`openFileDescriptor("wt")`，fsync 尽力而为、不依赖）
3. 成功删 `.pending`
4. **读前** `ensureConsistent()`（持锁）：`.pending` 残留 = 上次写未确认 → 用 `.bak` 覆写主文件，`readAllWithStatus` 返回 `recovered=true`（UI Toast 透明提示）；无 `.bak` 可恢复时仅删 `.pending`、不提示

> 语义：崩溃后回滚到上次良好状态。**最近一次未完成的写入可能丢失，但文件不会损坏。**（README 已声明）
>
> ⚠️ 曾有两个现实缺口（K8，**v1.2.1 已修复其一**）：写路径原先直读主文件、不先恢复，且把可能脏的内容写进 `.bak`（崩溃后若下一个操作是写入，脏状态被固化、好备份被污染）——现已改为写路径统一走 `readMainForWrite`（先 `ensureConsistent` 再读）。保留现状：恢复动作本身失败时删 `.pending` 防反复尝试，既未恢复也无提示（罕见退化分支）。

`clear()` 两种模式都**不走** .bak——直接写文件头。数据安全由"清空前自动归档"兜底（specs：清空必先备份），避免崩溃回滚把清空前的内容又还回来。两个如实记录的边界：**归档兜底目前不设防**——`archiveFile` 忽略 `writeAll` 的失败返回值，归档失败仍会清空并提示"已归档"（TD-017 待决策）；`clear()` 本身不消费残留 `.pending`——若 clear 与上次崩溃残留之间无任何读操作，下次读会用 `.bak` 回滚掉清空（当前 UI 流程启动必先读、实际不可达，记为已知时序边界）。

### 4.3 并发：FileOperationLock

`MarkdownFileOperations.kt` 里的全局 `object`。**所有读写**（append/appendEntry/delete/update/clear/writeAll/count/readAll/exists/initHeader，含 SAF 的 `ensureConsistent`）都 `synchronized(FileOperationLock)`。

- 为什么是全局对象而不是实例锁：`MainScreen` 与 `ShareReceiverActivity` 各自 new 实例，实例锁挡不住跨实例并发。
- 为什么读也要持锁：SAF 读前要做恢复判定（读本身有写副作用）；且 read-modify-write 语义要求读到的不是半写状态。

### 4.4 错误处理契约（读失败 = 空串，K7）

两个实现的 `readAll()` 在读失败时仍返回**空串**（兼容既有调用方），但 v1.2.1 起 `ReadResult` 增设 `failed` 标志，且破坏链已全部加守卫：

1. ~~`initHeader` 把空串当空文件 → 覆写已有文件~~ → **已守卫**：读失败返回 false，不写；
2. ~~`append` 见空 → 全量重写为 header+新条目~~ → **已守卫**：读失败中止返回 false（`appendEntry`/SAF 的 `writeAll`/`deleteEntry`/`updateEntry` 同）；
3. ~~对账把"解析得 0 条"当真空 → 误删全部提醒~~ → **已守卫**：`MainScreen.refreshEntryCount` 经 `RefreshGuard` 纯函数判定——读失败或"内容空白且解析为空且上轮非空"跳过；header-only 是合法空文件放行（曾因"空且上轮非空"一刀切产生删至 0 条 UI 滞留回归，K10/TD-016，v1.2.2 修复并有 `RefreshGuardTest` 固定）。

**纪律**：任何"读结果为空"的分支都应先怀疑读失败，而非真空文件；新增读侧消费方优先用 `readAllWithStatus` 的 `failed` 判断。

## 5. 提醒子系统（v1.1.0 一次性 + v1.2.0 重复）

设计目标：**本地运行**的"真提醒"——不走日历、应用自身不联网、appendo 自己发通知，点按深链回记录（数据可随 Android 系统云备份迁移，见 §8.3；2026-09-01 决策②）。与"添加到日历"（fire-and-forget 预填）并存，后者退为次要按钮。

### 5.1 组件

| 组件 | 层 | 职责 |
|---|---|---|
| `ReminderStore` | reminder | sidecar 单例；暴露 Compose `State<Map<ts, ReminderMeta>>`，**徽标刷新靠它、不靠文件轮询** |
| `AlarmScheduler` | reminder | `AlarmManager.setAlarmClock` 注册/取消；PendingIntent 一律 `FLAG_IMMUTABLE` + 显式组件 |
| `ReminderAlarmReceiver` | reminder | 到点发通知；处理贪睡/完成。`exported=false`、无 intent-filter（不可被外部 App 伪造） |
| `ReminderBootReceiver` | reminder | `BOOT_COMPLETED` 重注册 + 补发。`exported=true` + 过滤器 |
| `NotificationHelper` | reminder | `reminder` 渠道（IMPORTANCE_HIGH）；通知=深链+贪睡+完成 |
| `ReminderLogic` / `ReminderMetaCodec` / `ReminderText` | util | 纯函数：状态决策、编解码、通知文案 |
| `ReminderIntents` | reminder | Intent action / extra / requestCode 常量与计算 |

**为什么 `setAlarmClock` 而非 `setExactAndAllowWhileIdle`**：免 `SCHEDULE_EXACT_ALARM` 权限与设置跳转；免 Doze 9 分钟限速（贪睡/密集提醒不被合并）；"用户闹钟"语义在国产 ROM 上优先级更高、被杀概率更低。

### 5.2 单条提醒的状态机

```
设置：schedule(ts, triggerAt) + store.set(整对象)      ← 覆盖式：写全新对象，不字段合并
  │                                                    （否则残留 fired=true 会让开机补发误判已发）
  ▼
到点（AlarmReceiver.ACTION_FIRE）
  ├─ 一次性：发通知，fired=true（徽标消失）
  ├─ 重复：发通知，排下一次（effectiveTrigger + 1 天/7 天），fired 保持 false（徽标保留）
  └─ 条目已不存在：cancel + remove（自愈）
贪睡（通知 action，10 分 / 1 小时两档）：排 now+分钟，fired=false（徽标重现），snoozedUntil=next，撤通知
  ⚠️ 重复提醒以 effectiveTrigger 为基准排下一次——**贪睡会使整个系列永久漂移**（每天 9:00 贪睡 1 小时 → 此后每天 10:00）。语义定位：**有意取舍**（2026-09-01 决策④）——贪睡即用户对系列的重新锚点；README 向用户声明
完成（**仅通知 action**；详情页只有「取消提醒」，没有「完成」）：
  ├─ 重复：排下一次（保留系列），fired=false
  └─ 一次性：cancel + remove
取消（详情页"取消提醒"）：cancel + remove
设置前守卫：API 33+ 无 POST_NOTIFICATIONS → 先请求权限，拒绝则拦截（不让用户设哑提醒）
```

### 5.3 开机恢复（`ReminderBootReceiver`）

sidecar 在 CE 加密存储，`BOOT_COMPLETED` 于首次解锁后送达——所以**不用** `directBootAware`，代价是"开机后需解锁一次才补发"。（此取舍经成本/风险评估后**有意维持**：DE 镜像方案成本中高且 OEM 送达时机不可控，见 TD-022 决策记录，2026-09-02。）

**应用更新与 force-stop 的恢复（v1.2.1 起，原 K9 已修复）**：manifest 同时注册 `BOOT_COMPLETED` 与 `MY_PACKAGE_REPLACED`；应用启动时（`MainScreen` 初始化）另行兜底调用 `reminder.rescheduleAll`——与开机共用同一套幂等逻辑（重注册未触发 + 补发错过），覆盖"闹钟被清又等不到广播"的场景。

对每条提醒 `ReminderLogic.bootDecide`（纯函数，幂等）：

| 状态 | 处置 |
|---|---|
| 已 fired | 不动 |
| 未到点 | 重注册闹钟 |
| 已错过 + 一次性 | **先置 fired=true 持久化、再补发**"已过期"通知（进程中途重启不会二次发） |
| 已错过 + 重复 | 推进到 now 之后的下一次（`advanceToNextFuture`，跨夏令时用 Calendar 加日），**补发一次**后重排——关机跨多天只补最新一次，不刷屏 |

### 5.4 与条目的对账（孤儿清理）

条目主键 == sidecar 主键带来一个义务：文件可能在外部被改（换 SAF 文件、外部编辑），sidecar 里会残留"孤儿"。`MainScreen.refreshEntryCount` 每次刷新时做差集：

```
orphans = sidecarKeys − 当前解析出的条目时间戳集
orphans → AlarmScheduler.cancel + store.remove
```

同时这些路径**显式联动**：删除条目 → cancel+remove 该条；清空（先自动归档）→ cancel 全部 + removeAll。归档操作不动提醒（条目仍在文件里）。

### 5.5 自检（诚实边界）

菜单"提醒自检"：通知权限检查、电池白名单检查（可一键跳转）、自启动（无公开检测 API；提供「自启动设置」按钮跳转应用详情）+ **测试提醒按钮**（排 1 分钟后的 `setAlarmClock`，响 = 短时正证明）。

> 可靠性契约是"完成一次性设置后、可当场验证、对大多数用户可靠"，**不是 100%**：被杀的提醒接收器不运行、无痕迹，无法自动预测。应用更新/force-stop 后的闹钟恢复已由 `MY_PACKAGE_REPLACED` + 启动兜底覆盖（原 K9，v1.2.1 修复）。README 已向用户声明。

### 5.6 深链

通知点按 → `MainActivity`（`FLAG_ACTIVITY_NEW_TASK|CLEAR_TOP`）读 `EXTRA_ENTRY_TIMESTAMP` → `scrollTs` State → `MainScreen` 的 `LaunchedEffect` 等条目加载后 `animateScrollToItem`（只滚一次）。条目已被删则自然不滚（不弹错）。

## 6. 并发与线程模型（现状）

### 6.1 线程

- `ShareReceiverActivity`：全部文件 I/O 在 `lifecycleScope + Dispatchers.IO`。
- `MainScreen.refreshEntryCount`：读文件+解析在 `Dispatchers.IO`；随后的孤儿对账（AlarmManager cancel + SP 写，轻量）在协程主线程调度上执行。
- 仍在主线程同步 I/O 的用户操作：追加/删除/更新/清空/归档/复制/分享，以及**最重的归档恢复**（归档全文读取+解析+逐条 `appendEntry`，每条都是全文件原子写）——个人笔记体量下可容忍，属已知债务（TD-008，随 ViewModel 重构一并下放）。
- 归档列表加载、归档详情加载在 `withContext(Dispatchers.IO)`。
- **Broadcast Receiver 侧的主线程文件 I/O**：`rescheduleAll`（开机/更新/启动兜底）对每条已错过提醒调 `findEntryContent`（全文件读+解析，持锁），`ReminderAlarmReceiver.handleFire` 同样在主线程读——提醒多 + 文件大时与 UI 抢锁、逼近广播 10 秒限制（可后置 `goAsync` 或下放 IO）。
- 锁是 JVM 内置 `synchronized`，跨这些线程互斥有效。

### 6.2 徽标刷新为什么不受文件轮询管

提醒触发只改 sidecar、不动文件——所以 `ReminderStore` 做成**进程内单例 + Compose State**，Receiver 写完 `refresh()` 即刻驱动 UI，与文件层完全解耦。

### 6.3 列表刷新：2 秒轮询 + 逻辑 mtime

`MainScreen` 每 2 秒比对 `FileRepository.getFileLastModified()`——注意这是**存在 SP 里的逻辑时间戳**（应用每次写完调 `setFileLastModified(now)`），不是文件系统 mtime。效果：本应用各入口（分享接收、手动输入）的写入都会被轮询捕获；**外部编辑不会触发刷新**（与"不支持外部编辑"的立场一致）。该 mtime 由**各写点手工调用**维护（MainScreen 六处、ShareReceiverActivity、ArchiveListScreen 恢复各一处）——**新增写入路径必须补调**，否则列表不刷新。

## 7. 测试策略

| 层 | 现状 | 说明 |
|---|---|---|
| 纯 JVM 单测 | ✅ 主体 | `EntryParserTest`、`ReminderLogicTest`、`ReminderMetaCodecTest`、`ReminderTextTest`、`CalendarEntryMapperTest`、`DuplicateHintThrottleTest`、`FileBasedMarkdownFileTest`（mock Context）、`FileRepositoryFirstLaunchTest`、`MarkdownParserTest`（parseMarkdownEntries 薄包装，K5 对应）、`ArchiveRestoreDeduplicationTest`、`EntryDeletionIntegrationTest` |
| Robolectric | ⚠️ 依赖已声明，当前环境跑不了（android-all jar 下载失败，TD-003） | FileBased 路径用 mock Context 纯 JVM 覆盖 |
| Instrumented | ⚠️ androidTest 依赖已备、用例未写（TD-006） | SAF 路径（真 ContentResolver、授权失效）目前靠真机手动验证 |

原则：**新增逻辑优先落纯函数**（util 的 `object`），让它在纯 JVM 可测——这是 `EntryParser`/`ReminderLogic` 存在的直接原因。

```bash
./gradlew testDebugUnitTest   # 单元测试
./gradlew assembleDebug       # 构建
```

## 8. 已知问题与约束

### 8.1 代码级已知问题（v1.2.1 批次后）

| # | 问题 | 状态 |
|---|---|---|
| K1 | `ArchiveRepository`/`MainActivity` 归档计数用严格秒级正则，新建归档显示"0 条" | ✅ 已修复（v1.2.1，TD-010）：改 `EntryParser.getTimestampRegex()` |
| K2 | `MarkdownFormatter.formatEntry` 死代码 + 误导注释 | ✅ 已删除（v1.2.1，TD-011），连同 `TIMESTAMP_FORMAT` 与严格正则 |
| K3 | `lifecycle-viewmodel-compose` 已声明未使用（无 ViewModel） | 保留（Phase B 会用上） |
| K4 | `AppendoApplication` 创建的 `write_feedback` 渠道无使用方 | 遗留（低影响） |
| K5 | `MainScreen.parseMarkdownEntries` 薄包装（TD-007）、主线程 I/O（TD-008） | 保留（Phase B 一并处理） |
| K6 | 解析层静默丢内容（`---`/`# Appendo` 行编辑后丢失） | ✅ 已修复（v1.2.1，TD-015）：隔离谓词与跳过谓词共用 `isSkippableLine`；旧条目不可回溯（限制已声明） |
| K7 | 读失败=空串三条破坏链（覆写/清库/误杀提醒） | ✅ 已关闭（v1.2.1，TD-012）：`ReadResult.failed` + 存储层守卫 + 对账守卫（§4.4） |
| K8 | SAF 写路径不先恢复、脏内容污染 `.bak` | ✅ 已修复（v1.2.1，TD-013）：写路径统一 `readMainForWrite` 先恢复（§4.2） |
| K9 | 应用更新后闹钟全丢直至重启 | ✅ 已修复（v1.2.1，TD-014）：`MY_PACKAGE_REPLACED` + 启动兜底 `rescheduleAll`（§5.3） |
| K10 | ~~K7 对账守卫假阳性回归：删至 0 条后 UI 滞留已删条目~~ | ✅ 已修复（v1.2.2，TD-016）：守卫收敛 `RefreshGuard` 纯函数（header-only 合法空放行）；真机验收通过 |
| K11 | ~~清空前归档兜底不设防~~ | ✅ 已修复（v1.2.2，TD-017）：归档失败阻止清空并提示 |
| K12 | SAF 授权丢失（覆盖安装清授权；realme 实测 `takePersistableUriPermission` 未持久化）→ 静默空列表、分享数据"无声分家" | ✅ 已修复（v1.2.2，TD-021）：读失败+SAF 弹「数据文件访问已失效」对话框（重选/回退）；分享失效明示；真机活体验收 |
| K13 | 归档详情残留活动文件的提醒徽标（快照视图语义错位） | ✅ 已修复（v1.2.2，真机验收发现）：readOnly 视图不渲染徽标 |

其余技术债（CRLF 规范化、.bak 驻留、SAF PoC 结论待填等）见 [plans/debt-tracker.md](./plans/debt-tracker.md)。

### 8.2 对用户的约束声明（README 同步）

- 外部编辑器直编 `Appendo.md` 不支持（ZWSP 机制会被破坏；复制/分享出口已剥离标记）。
- SAF 模式为软恢复：崩溃丢最近一次写入、不损坏文件。
- 不建议降级安装（旧版读不了毫秒条目——数据仍在文件中，重装新版恢复显示；T-dgchk 已核实 v1.0.x 追加为纯 append 模式、不会重写文件）。
- 国产 ROM 需开自启动 + 电池白名单，提醒"可靠非 100%"；打开应用会自动补发已过期提醒（需解锁）；贪睡使重复系列整体后移（决策④）。
- 旧数据 `---`/`# Appendo` 行：v1.2.2 首启自动迁移修复（§3.1），残余边界极少数仍不可见。
- 重复时间戳（归档恢复+编辑组合场景）下删除/编辑作用于靠前的条目（§3.2）。
- 清空与切换数据文件会移除全部提醒（不可恢复，有确认弹窗）；恢复归档不恢复提醒。
- 单次分享上限 10,000 字符（拒绝并提示，不截断）。
- 多设备/云盘同步不支持（外部编辑不触发刷新 + 锁仅进程内，§6.3）。
- 换机与备份：数据随 Android 系统云备份迁移（决策②，README「换机与备份」小节）。

### 8.3 安全与隐私事实

- **攻击面**：仅两个 Activity exported（`MainActivity` MAIN/LAUNCHER；`ShareReceiverActivity` ACTION_SEND text/plain）。`ReminderAlarmReceiver` `exported=false` 且无 intent-filter——只能被应用自己的显式 PendingIntent 触发，外部 App 无法伪造提醒；`ReminderBootReceiver` `exported=true`，仅接收 `BOOT_COMPLETED` 与 `MY_PACKAGE_REPLACED` 两个系统保护广播。
- PendingIntent 一律 `FLAG_IMMUTABLE` + 显式组件（贪睡分钟数由接收器从 extra 自行计算，无需 MUTABLE）。
- 分享入口限长 10,000 字符（防 DoS）；超长被**拒绝写入**（不截断），Toast 为笼统的"写入失败"（表述待改进，见 debt-tracker TD-020 备注）。
- **hashCode 派生标识的碰撞面**（个人级概率极低，已知约束）：SAF 恢复文件名按 URI 哈希派生、闹钟/通知 id 按时间戳哈希派生——理论碰撞会导致恢复文件互串 / 通知互覆。
- **`allowBackup="true"`——已决策（2026-09-01，决策②）**：保持开启、拥抱云备份。`Appendo.md` 与 `appendo_reminders` 随 Android 云备份迁移/恢复；理由：默认模式数据在应用私有目录、卸载即失，云备份对用户是净收益。对用户的口径："应用自身不联网、无第三方 SDK"（README「换机与备份」小节落地）。

## 9. 设计决策索引（ADR）

| 决策 | 结论 | 为什么（一句话） | 详见 |
|---|---|---|---|
| 数据存储 | 单 Markdown 文件为唯一数据源 | 用户可读可迁移，符合"速记"定位；代价是协议复杂度（本文 §3） | specs |
| 条目主键 | 完整时间戳串（毫秒） | 索引会漂移；单调防碰撞只在 append 路径保证唯一，重复时间戳真实存在（§3.2） | design.md v1.1 节 |
| 内容隔离 | ZWSP 前缀，出口统一剥离 | 零侵入且可逆；保"内容原样"承诺 | design.md v1.1 节 |
| 原子写 | 默认真原子 / SAF 软恢复 | SAF 无原子重命名，诚实降级优于假装原子 | design.md v1.1 节 |
| 并发 | 进程内全局锁 | 双 Activity 同进程，锁即足够；无跨进程需求 | — |
| 提醒机制 | 自建 `setAlarmClock`（方案 C） | 本地私有、appendo 品牌通知、深链回记录；免权限免 Doze 限速 | design/local-reminder-design.md |
| 提醒存储 | sidecar（独立 SP，key=条目时间戳） | 不污染 Markdown 正文；主键对齐使联动/对账简单 | design/local-reminder-design.md |
| 重复提醒语义 | 开机错过只补发最新一次；"完成"保留系列 | 不刷屏；用户要的是"别丢下一次" | §5.3（v1.2.0 事后补记，无独立设计文档） |
| 日历集成 | `ACTION_INSERT` 预填拉起（方案 A） | 零权限不上云；定位为次要入口 | design/local-reminder-design.md §3 |
| 架构形态 | 无 ViewModel/Repository 中间层 | 单屏小应用，YAGNI；债务记录在案 | §8、TD-008 |
| 内容保真边界（2026-09-01①） | 输入侧不 trim（首尾空白原样入文件）；解析侧 trim 保留（模板空行与用户首尾空白不可区分，显示层去除）；空内容条目不保留 | 可避免的丢失全消除，不可区分处以显示层语义披露 | §3.1 |
| 隐私与备份（2026-09-01②） | `allowBackup=true` 拥抱云备份 | 私有目录卸载即失，备份是净收益；口径"应用自身不联网" | §8.3 |
| 旧数据迁移（2026-09-01③） | 首启一次性补隔离（`migrateLegacyIsolation`，保守规则宁漏勿误） | 拆"旧条目编辑即丢行"的雷 | §3.1 |
| 贪睡语义（2026-09-01④） | 系列随贪睡漂移（重锚点） | 符合"我贪睡了，以后就这个点"的直觉 | §5.2 |
| 重启锁屏期提醒（TD-022，2026-09-02） | 维持现状：解锁后重注册/补发（不丢只延迟） | DE 镜像方案成本中高 + OEM 送达不可控 + 双写新债，vs 低频场景已有兜底 | debt-tracker TD-022 |

## 10. 阅读地图

想改某类东西 → 先读哪：

| 想做什么 | 先读 | 再动 |
|---|---|---|
| 条目格式/解析/时间戳 | §3.1–3.2 | `EntryParser`（及 `EntryParserTest`） |
| 新增复制/分享类出口 | §3.3 不变量 2 | `stripIsolationMarkers` 接入点 |
| 存储后端行为 | §4 | `FileBasedMarkdownFile` / `SafMarkdownFile` |
| 提醒行为/修重复语义 | §5 | `ReminderLogic` + `ReminderAlarmReceiver` |
| 页面/交互 | §1、`ui/` 各文件头注释 | `MainScreen`（注意状态全在 `remember`，无 ViewModel） |
| 加新测试 | §7 | `src/test/` 对应包 |
| **review 一个改动** | §11 | 按改动面过评审清单 |
| **验收一个功能** | §12 | 单测 + 真机验证要点 |

## 11. 变更评审清单（review 用）

评审 PR / 自查时按改动面逐条核对。清单是下限不是上限——§3/§4 的不变量永远适用。

**动了数据协议（`EntryParser` / 条目格式 / 时间戳）**
- 宽松正则仍同时匹配秒级与毫秒级；写入仍固定 3 位毫秒
- 写侧隔离（不变量 1）与出口剥离（不变量 2）保持对称；新增出口已登记进 §3.3 出口表
- 单调性只约束 `append()` now 路径；`appendEntry` 仍允许插入旧时间戳
- 旧文件仍可解析：秒级条目、无 ZWSP 的历史归档、混合格式
- 有纯 JVM 单测；`architecture.md` §3 已同步修订

**动了存储层（`FileBasedMarkdownFile` / `SafMarkdownFile` / 工厂）**
- 新增读写方法持 `FileOperationLock`
- 读失败没有被当成空文件处理（对照 §4.4 三条破坏链，不要新增第四条）
- SAF 写路径若变，核对 `ensureConsistent` 与 `.bak` 写入时序（TD-013）
- `ShareReceiverActivity` 直接实例化的路径是否受影响（§4.1）

**动了提醒（`reminder/` + `ReminderLogic`）**
- 状态机分支与 §5.2 一致；开机/错过补发仍是"先持久化 fired=true 再发"
- 条目删除/清空/换文件的提醒联动（§5.4）未被破坏
- PendingIntent 一律 `FLAG_IMMUTABLE` + 显式组件；接收器 exported 配置未变
- 决策逻辑落在 `ReminderLogic` 纯函数且有单测

**动了 UI / 内容出口**
- 新的内容出口走 `stripIsolationMarkers` 或经 `parse` 产物（§3.3 出口表）
- 新写入路径补调 `setFileLastModified`（§6.3），否则列表不刷新
- 新增耗时操作不落在主线程同步 I/O（对照 §6.1 清单，只减不增）

**动了 manifest / 权限**
- exported 组件清单未意外扩大；receiver 的 intent-filter 变更同步核对 §8.3 攻击面描述
- 新增权限有明确用途；`allowBackup` 若变动，须先解决 §8.3 的未决策项

**动了 Intent 常量 / SP 键（事实上的持久化协议）**
- `ReminderIntents` 的 action / extra / requestCode：改 action 串会孤儿化已注册的 PendingIntent 与存量通知按钮
- SP 文件名与键（`appendo`、`appendo_reminders`、`reminder_` 前缀，§3.6）：改名即丢用户提醒/偏好

**任何改动（通用）**
- 按根 CLAUDE.md「任务完成检查」逐项过文档清单，**"改动面 → 本文回改小节"的映射显式过一遍**——§4.3/§8.3 曾因漏做此步而与代码脱节（四角色评审教训）

## 12. 验收手段（验收用）

> "状态"列如实记录最近一次执行情况；"未定向执行"≠ 通过。SAF 崩溃恢复与 BOOT 补发两项当前**未覆盖**（与 debt-tracker 记录一致），对刚重构过的路径应安排定向验收而非日常观察。

| 验收对象 | 手段 | 状态 |
|---|---|---|
| 单元回归 | `./gradlew testDebugUnitTest` 全绿（改协议/提醒必跑） | ✅ 2026-08-31（113 用例） |
| 构建 | `./gradlew assembleDebug` 成功 | ✅ 2026-08-31 |
| 默认模式数据完整性（真机） | 追加/删除/编辑后核对文件：debug 构建用 `adb shell run-as com.yiyue31.android.appendo cat /storage/emulated/0/Android/data/com.yiyue31.android.appendo/files/Appendo.md`（Android 11+ 文件管理器**无法**访问该目录，勿用"文件管理器查看"）；核对内容完整、无半写、无 BOM | ✅ 2026-08-31（写入+run-as 抽查） |
| SAF 模式（真机） | 切换外部文件 → 写入 → 读回一致。崩溃恢复需**在写入窗口内中断**才触发（"限制后台后杀进程"发生在后台化时、造不出窗口，勿用）；可在写入瞬间 `adb shell am crash com.yiyue31.android.appendo` 模拟 | 部分 ✅ 2026-09-02：授权丢失场景已活体复现并验证 TD-021 修复对话框；`am crash` 写入窗口崩溃恢复仍未做（TD-006 关联） |
| 提醒（真机） | ①「提醒自检」发测试提醒（约 1 分钟响 = 短时正证明）；②设 2 分钟提醒 → 重启 → **解锁时已过期**才补发"已过期"通知（早于到点解锁则表现为正常响）；③贪睡 10 分后再响；④每天重复次日再响 | ①②④ ✅ 2026-09-02（BOOT 重启补发用户验证；重启后延迟/解锁后补发为系统边界，TD-022 记录改进选项）；③ 未定向 |
| 覆盖安装（真机） | 设临近提醒 → `adb install -r` 重装（不重启）→ 提醒仍到点响（TD-014 的直接验收目标） | ✅ 2026-09-02（dumpsys 闹钟重注册 + 锁屏通知双证据） |
| 删除最后一条（TD-016 修复后） | 删除唯一条目 → 列表立即清空、计数归 0、与"已删除"Toast 一致 | ✅ 2026-09-02（用户验证：删空立即归零） |
| 出口干净度 | 复制全部/复制单条/分享/日历预填/通知标题 → 粘贴进**可检测 U+200B 的工具**（支持"显示不可见字符"的编辑器或在线 zero-width 检查器）比对无零宽字符 | ⛔ 未做字符级检查（视觉检查不充分） |
| 升级兼容 | 旧秒级数据装新版本条目可读、计数正确（默认模式需 adb 推送旧格式文件；SAF 模式可外部构造后选择） | ✅ 2026-08-31（1.2.0→1.2.1 数据保留） |
| 归档恢复去重 | 恢复含重复条目的归档 → 仅新增缺失条目、原时间戳落回历史位置、Toast 报"恢复 X 条 / 跳过 Y 条" | ✅ 2026-09-02（删空→恢复往返，用户验证） |

---

**维护约定**：涉及 §3 数据协议或 §4 完整性语义的代码改动，必须同步修订本文对应小节（这是"单一权威源"成立的前提）；纯 UI 调整可不更新。

**与源码树各包 CLAUDE.md 的关系**：`Appendo/src/main/java/.../` 下的包级 CLAUDE.md 是目录级导读（偏 AI 维护视角，含各自的已知问题提示）；与本文冲突时**以本文为准**，协议性改动应双向同步。
