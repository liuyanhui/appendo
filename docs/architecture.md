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
- ui / data / reminder → util 单向依赖。两条横向依赖要注意：`reminder` 会读 `data`（`ReminderAlarmReceiver#findEntryContent` 直接构造 `FileRepository` 取当前文件）；util 内部被共享的除 `EntryParser` 外，还有 `MarkdownFormatter` 常量与 `ReminderMeta` 数据类型。
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
- **读侧的隔离与丢弃边界**：内容中恰为 `---` 或以 `# Appendo` 开头的行，写入侧（v1.2.1 起，TD-015）与时间戳行同样加 ZWSP 隔离、读侧还原——**编辑不再丢行**。限制：v1.2.1 之前写入的旧条目无隔离标记，这类行在其视图中仍不可见、编辑保存仍会移除（已发生的丢失无法回溯，README 已声明）；另外 `parse` 会丢弃无内容的条目、对整段内容 `trim()`（分享入口亦 `trim()`），此为现状。
- 历史文件（v1.0.x）的时间戳是秒级，新文件是毫秒级，两者混存合法（§3.2）。

### 3.2 时间戳

| 属性 | 值 | 说明 |
|---|---|---|
| 写入格式 | `yyyy-MM-dd HH:mm:ss.SSS`（Locale.US，系统默认时区，java.time） | 自 v1.2 条目起毫秒级 |
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
> **不变量 2（出口剥离）**：任何"内容离开本应用"的出口（复制、分享、日历预填、提醒通知标题）必须剥离标记——统一走 `EntryParser.stripIsolationMarkers`（仅剥"行首 ZWSP + 紧跟时间戳模式"的行，用户自有的其他 ZWSP 不动）。读侧 `parse` 同样在收集内容时还原。

当前出口清单（新增出口时必须加进这张表）：

| 出口 | 剥离方式 |
|---|---|
| 复制全部 / 分享全部（`MainScreen`） | 显式走 `readAllForExternal()`（= readAll + `stripIsolationMarkers`） |
| 添加到日历（`CalendarEntryMapper.map`） | 显式 `stripIsolationMarkers` |
| 提醒通知标题/正文（`ReminderText.title`） | 显式 `stripIsolationMarkers` |
| 归档列表长按复制归档（`ArchiveListScreen`） | 显式 `stripIsolationMarkers` |
| **长按复制单条**（主列表 / 归档详情页） | **经 `parse` 产物天然干净**——`parse` 收集内容时已用 `restoreLine` 还原（与 `stripIsolationMarkers` 内部同一函数），无需再剥 |

**内部读取用 `readAll()`（保留标记）**，不要拿 `readAllForExternal` 的结果去做解析。

**隔离形态清单（v1.2.1 起，TD-015）**：时间戳行、恰为 `---`、以 `# Appendo` 开头，共三类；**隔离谓词与解析跳过谓词共用 `EntryParser.isSkippableLine`，不可分叉**（这是 TD-015 的根因修复原则）。v1.2.1 前写入的旧条目无标记，不受保护（已知限制）。

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

构造：`MainScreen` 与 `ReminderAlarmReceiver#findEntryContent` 经 `MarkdownFileFactory.create(context, useSAF, fileUri, defaultFile)`；**`ShareReceiverActivity` 因需先做 URI 有效性判定而直接实例化两个实现类——改工厂签名/新增存储模式时必须同步检查这里**。接口 `MarkdownFileOperations` 定义 `append/appendEntry/readAll/readAllWithStatus/readAllForExternal/writeAll/clear/exists/count/initHeader/deleteEntry/updateEntry`。两实现算法同源（都调 `EntryParser`），差异只在 I/O 原语。

SAF 模式回退：由两个入口各自检查 `isFileUriValid()`，失效即 `clearFileUri()`（先清偏好再释放权限，防中途崩溃永久丢 URI）后回默认文件——`MainScreen` 在启动 `LaunchedEffect` 里检查一次，`ShareReceiverActivity` 每次写入前检查。（`SafMarkdownFile.exists()` 捕获 SecurityException 只记日志返回 false，不单独触发回退。）首启时 `isFirstLaunch()` 为真会弹引导对话框建议把文件放外部存储（重装不丢数据）。

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

`clear()` 两种模式都**不走** .bak——直接写文件头。数据安全由"清空前自动归档"兜底（specs：清空必先备份），避免崩溃回滚把清空前的内容又还回来。

### 4.3 并发：FileOperationLock

`MarkdownFileOperations.kt` 里的全局 `object`。**所有读写**（append/appendEntry/delete/update/clear/writeAll/count/readAll/exists/initHeader，含 SAF 的 `ensureConsistent`）都 `synchronized(FileOperationLock)`。

- 为什么是全局对象而不是实例锁：`MainScreen` 与 `ShareReceiverActivity` 各自 new 实例，实例锁挡不住跨实例并发。
- 为什么读也要持锁：SAF 读前要做恢复判定（读本身有写副作用）；且 read-modify-write 语义要求读到的不是半写状态。
- `MarkdownFormatter.formatEntry` 内的共享 `SimpleDateFormat` 非线程安全，历史上靠锁保护——该方法现已无生产调用方（§8 已知问题）。

### 4.4 错误处理契约（读失败 = 空串，K7）

两个实现的 `readAll()` 在读失败时仍返回**空串**（兼容既有调用方），但 v1.2.1 起 `ReadResult` 增设 `failed` 标志，且破坏链已全部加守卫：

1. ~~`initHeader` 把空串当空文件 → 覆写已有文件~~ → **已守卫**：读失败返回 false，不写；
2. ~~`append` 见空 → 全量重写为 header+新条目~~ → **已守卫**：读失败中止返回 false（`appendEntry`/SAF 的 `writeAll`/`deleteEntry`/`updateEntry` 同）；
3. ~~对账把"解析得 0 条"当真空 → 误删全部提醒~~ → **已守卫**：`MainScreen.refreshEntryCount` 在 `failed` 或"结果为空但上轮非空"时跳过刷新与对账。

**纪律**：任何"读结果为空"的分支都应先怀疑读失败，而非真空文件；新增读侧消费方优先用 `readAllWithStatus` 的 `failed` 判断。

## 5. 提醒子系统（v1.1.0 一次性 + v1.2.0 重复）

设计目标：**完全本地私有**的"真提醒"——不走日历、不上云、appendo 自己发通知，点按深链回记录。与"添加到日历"（fire-and-forget 预填）并存，后者退为次要按钮。

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
完成（**仅通知 action**；详情页只有「取消提醒」，没有「完成」）：
  ├─ 重复：排下一次（保留系列），fired=false
  └─ 一次性：cancel + remove
取消（详情页"取消提醒"）：cancel + remove
设置前守卫：API 33+ 无 POST_NOTIFICATIONS → 先请求权限，拒绝则拦截（不让用户设哑提醒）
```

### 5.3 开机恢复（`ReminderBootReceiver`）

sidecar 在 CE 加密存储，`BOOT_COMPLETED` 于首次解锁后送达——所以**不用** `directBootAware`，代价是"开机后需解锁一次才补发"。

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

菜单"提醒自检"：通知权限检查、电池白名单检查（可一键跳转）、自启动（无公开 API，仅文字引导）+ **测试提醒按钮**（排 1 分钟后的 `setAlarmClock`，响 = 短时正证明）。

> 可靠性契约是"完成一次性设置后、可当场验证、对大多数用户可靠"，**不是 100%**：被杀的提醒接收器不运行、无痕迹，无法自动预测。应用更新/force-stop 后的闹钟恢复已由 `MY_PACKAGE_REPLACED` + 启动兜底覆盖（原 K9，v1.2.1 修复）。README 已向用户声明。

### 5.6 深链

通知点按 → `MainActivity`（`FLAG_ACTIVITY_NEW_TASK|CLEAR_TOP`）读 `EXTRA_ENTRY_TIMESTAMP` → `scrollTs` State → `MainScreen` 的 `LaunchedEffect` 等条目加载后 `animateScrollToItem`（只滚一次）。条目已被删则自然不滚（不弹错）。

## 6. 并发与线程模型（现状）

### 6.1 线程

- `ShareReceiverActivity`：全部文件 I/O 在 `lifecycleScope + Dispatchers.IO`。
- `MainScreen.refreshEntryCount`：读文件+解析在 `Dispatchers.IO`；随后的孤儿对账（AlarmManager cancel + SP 写，轻量）在协程主线程调度上执行。
- 仍在主线程同步 I/O 的用户操作：追加/删除/更新/清空/归档/复制/分享，以及**最重的归档恢复**（归档全文读取+解析+逐条 `appendEntry`，每条都是全文件原子写）——个人笔记体量下可容忍，属已知债务（TD-008，随 ViewModel 重构一并下放）。
- 归档列表加载、归档详情加载在 `withContext(Dispatchers.IO)`。
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

其余技术债（CRLF 规范化、.bak 驻留、SAF PoC 结论待填等）见 [plans/debt-tracker.md](./plans/debt-tracker.md)。

### 8.2 对用户的约束声明（README 同步）

- 外部编辑器直编 `Appendo.md` 不支持（ZWSP 机制会被破坏；复制/分享出口已剥离标记）。
- SAF 模式为软恢复：崩溃丢最近一次写入、不损坏文件。
- 不建议降级安装（旧版严格正则读不了毫秒时间戳）。
- 国产 ROM 需开自启动 + 电池白名单，提醒"可靠非 100%"。

### 8.3 安全与隐私事实

- **攻击面**：仅两个 Activity exported（`MainActivity` MAIN/LAUNCHER；`ShareReceiverActivity` ACTION_SEND text/plain）。`ReminderAlarmReceiver` `exported=false` 且无 intent-filter——只能被应用自己的显式 PendingIntent 触发，外部 App 无法伪造提醒；`ReminderBootReceiver` `exported=true` 仅 `BOOT_COMPLETED`。
- PendingIntent 一律 `FLAG_IMMUTABLE` + 显式组件（贪睡分钟数由接收器从 extra 自行计算，无需 MUTABLE）。
- 分享入口限长 10,000 字符（防 DoS）。
- **`allowBackup="true"`（manifest 未显式关闭）**：`Appendo.md` 与 `appendo_reminders` 会被纳入 Android 云备份——与提醒"完全本地私有"的定位存在张力，属**未决策项**（若要严格本地需设 false，并评估用户换机迁移的影响）。

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
- 新增耗时操作核对线程（§6.1），别再往主线程 I/O 清单里加项

## 12. 验收手段（验收用）

| 验收对象 | 手段 |
|---|---|
| 单元回归 | `./gradlew testDebugUnitTest` 全绿（改协议/提醒必跑） |
| 构建 | `./gradlew assembleDebug` 成功 |
| 默认模式数据完整性（真机） | 追加/删除/编辑各操作后，用文件管理器查看 `Appendo.md`：内容完整、无半写状态、无 BOM |
| SAF 模式（真机） | 切换外部文件 → 写入 → 读回一致；开发选项里限制后台后杀进程模拟崩溃 → 重进应用出现"已从备份恢复"提示且内容为上次良好状态 |
| 提醒（真机） | 「提醒自检」发测试提醒（约 1 分钟响 = 短时正证明）；设 2 分钟提醒 → 重启 → 解锁后补发"已过期"通知；贪睡 10 分后再响；每天重复次日再响 |
| 出口干净度 | 复制全部/复制单条/分享/日历预填/通知标题，粘贴到任意编辑器检查无零宽字符（条件允许时 hex 查看） |
| 升级兼容 | 用旧版本数据（秒级时间戳的 `Appendo.md`）安装新版本，条目可读、计数正确 |

---

**维护约定**：涉及 §3 数据协议或 §4 完整性语义的代码改动，必须同步修订本文对应小节（这是"单一权威源"成立的前提）；纯 UI 调整可不更新。

**与源码树各包 CLAUDE.md 的关系**：`Appendo/src/main/java/.../` 下的包级 CLAUDE.md 是目录级导读（偏 AI 维护视角，含各自的已知问题提示）；与本文冲突时**以本文为准**，协议性改动应双向同步。
