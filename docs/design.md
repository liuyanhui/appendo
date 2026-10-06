# Appendo - 设计方案

> **⚠️ 历史文档（设计变更史）**：本文主体停留在 v1.0 架构，v1.1 数据完整性增强以附录形式追加，与当前实现存在已知偏差（如 SharedPreferences 实际名为 `appendo` 而非 `link_appending`）。
> **当前架构请读 [architecture.md](./architecture.md)**（唯一权威的现状快照）。本文保留用于追溯设计决策的过程与理由，不再要求与现状一致。

## 修订记录

| 日期 | 变更人 | 变更摘要 |
|------|--------|---------|
| 2026-04-01 | — | 初始版本 |
| 2026-04-10 | Yiyue | 去除 BOM；Toast 改 Notification；新增分享和归档功能；写入加同步锁；条目计数改为按时间戳标题 |
| 2026-04-16 | Yiyue | 新增手动输入功能；新增内容预览功能；应用定位调整为速记应用 |
| 2026-04-16 | Yiyue | 优化文件存储：首次启动使用默认目录；归档自动生成文件名；支持混合存储模式 |
| 2026-04-17 | Yiyue | 新增归档管理功能；统一条目交互模式；实现归档恢复功能；修复删除索引bug；改用时间戳作为删除标识符 |
| 2026-04-18 | Yiyue | 提取 MarkdownFileOperations 接口和工厂模式；实现归档恢复去重；自适应图标矢量图；项目统一命名为 Appendo |
| 2026-07-20 | Yiyue | v1.1 数据完整性增强（v3.1，经三轮多角色评审 + 自查修订）：java.time 毫秒时间戳、正则集中化、零宽空格隔离+单一出口剥离、写入原子性(默认 fsync+rename / SAF .pending+.bak 软恢复+持锁)、EntryParser 收敛条目知识、ReadResult、appendEntry、测试金字塔 |
| 2026-10-06 | Yiyue | v1.3.x 微文案与交互一致性设计（对应 specs 第 15 章需求 74~81，需求已经"评审→修订→三角色复核"闭环）：统一术语表落地方案、Microcopy 常量源、需求 75"次级动作先落盘"时序与 appendReturningTimestamp 竞态消除、测试与文档同步方案 |

## 架构概述

极简速记应用，两个 Activity 协作完成功能。支持通过分享接收内容和手动输入两种方式收集。采用双存储模式（默认文件 + SAF），通过接口抽象和工厂模式统一文件操作。

## 组件设计

### Activity

#### `MainActivity`
- 主界面，Jetpack Compose 构建
- 内嵌 Navigation Compose 管理页面导航（主界面 → 归档列表 → 归档详情）
- 管理文件存储模式（默认/SAF）
- 功能：手动输入、复制全部、分享全部、清空（含备份）、归档、删除条目

#### `ShareReceiverActivity`
- 接收其他应用分享内容（`ACTION_SEND` Intent Filter，`text/plain`）
- 解析 Intent 中的文本
- 校验内容长度（最大 10,000 字符）
- 支持 SAF 和默认文件两种模式
- 写入完成后 Toast 提示，自动关闭（`noHistory=true`，不出现在最近任务列表）

#### `AppendApplication`
- Application 子类
- 初始化 Toast 兼容工具

### 数据层

#### `FileRepository`
- 职责：管理文件存储偏好设置和 URI 权限
- 存储方式：`SharedPreferences`（名称：`link_appending`）
- 关键方法：
  - `getDefaultFile(): File` — 获取默认文件（`getExternalFilesDir` 下的 `Appendo.md`）
  - `isUsingSAF(): Boolean` — 判断当前是否使用 SAF 模式
  - `saveFileUri(uri)` — 保存 SAF URI 并持久化权限
  - `clearFileUri()` — 清除 URI 并释放权限（先清偏好再释放，防止竞态）
  - `isFileUriValid(): Boolean` — 验证 SAF URI 是否仍然有效
  - `generateArchiveFilename(): String` — 生成归档文件名 `Appendo_yyyyMMdd_HHmmss.md`
  - `getFileLastModified() / setFileLastModified()` — 文件修改时间追踪（用于轮询刷新）

#### `ArchiveFile`
- 数据类：表示归档文件元信息
- 属性：`file: File`、`name: String`、`timestamp: Date`、`entryCount: Int`

#### `ArchiveRepository`
- 职责：归档文件管理
- 归档文件识别：文件名以 `Appendo_` 开头、`.md` 结尾
- 关键方法：
  - `listArchiveFiles(): List<ArchiveFile>` — 列出所有归档，按时间倒序
  - `getArchiveContent(file): String` — 读取归档内容
  - `deleteArchive(file): Boolean` — 删除归档
  - `formatTimestamp(date): String` — 格式化为 `yyyy-MM-dd HH:mm`

### 工具层

#### `MarkdownFileOperations`（接口）
- 定义文件操作的统一抽象
- 方法：`append`、`readAll`、`clear`、`exists`、`count`、`initHeader`、`deleteEntry(timestamp)`、`writeAll`
- `deleteEntry` 使用时间戳作为标识符（非索引），支持连续删除不冲突

#### `FileOperationLock`（全局锁对象）
- 跨实例同步锁，防止 `MainActivity` 和 `ShareReceiverActivity` 各自创建的文件操作实例并发冲突

#### `MarkdownFileFactory`
- 工厂类，根据存储模式创建对应的文件操作实例
- `create(context, useSAF, uri, file): MarkdownFileOperations`

#### `FileBasedMarkdownFile`
- 默认存储模式实现，使用 `java.io.File` 直接操作
- 写入使用 `FileOperationLock` 同步锁

#### `SafMarkdownFile`
- SAF 存储模式实现，通过 `ContentResolver` 操作
- `append` 失败时降级为先读后写

#### `MarkdownFormatter`
- 共享格式化工具
- 常量：`FILE_HEADER = "# Appendo\n"`
- `formatEntry(content)` — 生成带时间戳的条目文本
- `getTimestampRegex()` — 返回时间戳正则

### UI 层

#### `LinkEntry`
- 数据类：`timestamp: String`、`content: String`

#### `parseMarkdownEntries(content)`
- 解析 Markdown 内容为 `List<LinkEntry>`
- 跳过文件头 `# Appendo` 和分隔线 `---`
- 按时间戳标题 `## YYYY-MM-DD HH:mm:ss` 分割条目

#### `MainScreen`
- 主界面 Composable
- 布局：TopAppBar（标题+条目数+菜单）→ 2x2 按钮网格 → 条目列表
- 功能：手动输入、复制全部、分享全部、清空（长按+自动备份）、条目滑动删除、归档、打开文件、自定义目录、关于对话框
- 实时刷新：`LaunchedEffect` 每 2 秒轮询文件修改时间

#### `EntryListScreen`（可复用组件）
- 统一的条目列表展示和交互
- 参数：`entries`、`entryCount`、`sourceFileName`、`readOnly`、`onEntryLongClick`、`onEntrySwipeToDelete`
- `LazyColumn` 使用复合 key（`"${timestamp}_$index"`）防止重复 key
- 支持滑动删除（仅非只读模式）和长按复制

#### `ArchiveListScreen`
- 归档管理界面
- 功能：归档列表、点击查看详情、长按复制、右滑删除、左滑追加（带去重）
- 追加逻辑：解析归档和当前文件的条目，按 `timestamp|content` 组合去重，仅追加不重复的条目

#### `ArchiveDetailScreen`
- 归档详情界面，使用 `EntryListScreen`（只读模式）

#### `ToastUtils`
- Toast 兼容工具，适配不同 Android 版本

## 数据流

```
方式一（分享接收）：
其他应用分享 → ShareReceiverActivity → 解析 Intent (text/plain) →
校验长度 → 追加到 Markdown 文件 → Toast 提示 → 关闭 Activity

方式二（手动输入）：
主界面点击「手动输入」→ 弹出输入对话框 → 自动弹出键盘 →
用户输入内容 → 点击「追加」→ 追加到 Markdown 文件 → 刷新条目列表

方式三（归档恢复）：
归档列表左滑 → 确认追加 → 解析归档条目 → 与当前条目去重 →
追加不重复条目 → 更新文件修改时间 → 刷新列表

条目删除：
条目卡片右滑 → 确认删除 → 按时间戳定位条目 → 删除条目和分隔线 →
更新文件修改时间 → 刷新列表
```

## 界面布局

```
┌──────────────────────────────┐
│  Appendo           [⋯菜单]    │  ← TopAppBar
│  已收集 42 条                 │
├──────────────────────────────┤
│                              │
│  [手动输入]    [复制全部]      │  ← 2x2 按钮网格
│  [分享全部]    [清空]长按      │
│                              │
│  ──────────────────────────  │
│                              │
│  已收集 42 条                 │  ← EntryListScreen
│  ┌────────────────────────┐ │
│  │ 2026-04-18 14:30:25    │ │  ← 条目卡片
│  │ 这是一条测试内容        │ │     长按=复制，右滑=删除
│  └────────────────────────┘ │
│           ...               │
│                              │
└──────────────────────────────┘
```

**菜单下拉**：归档 | 归档管理 | 打开文件 | 自定义目录 | 关于

**归档列表页面**：
```
┌──────────────────────────────┐
│  ← 归档管理                   │
├──────────────────────────────┤
│  共 3 个归档                  │
│                              │
│  ┌────────────────────────┐ │
│  │ [ℹ] Appendo_20260418  │ │  ← 左滑=追加，右滑=删除
│  │     2026-04-18 · 15 条  │ │     长按=复制全部
│  └────────────────────────┘ │
│           ...               │
└──────────────────────────────┘
```

**归档详情页面**：
```
┌──────────────────────────────┐
│  ← 归档详情                   │
│  Appendo_20260418             │
├──────────────────────────────┤
│  Appendo_20260418             │  ← 只读 EntryListScreen
│  已收集 15 条                 │     长按=复制单条
│  ┌────────────────────────┐ │
│  │ 2026-04-18 14:30:25    │ │
│  │ 这是一条测试内容        │ │
│  └────────────────────────┘ │
│           ...               │
└──────────────────────────────┘
```

## 导航结构

```
MainActivity
  └── NavHost
       ├── composable("main") → MainScreen
       ├── composable("archiveList") → ArchiveListScreen
       └── composable("archiveDetail/{filePath}") → ArchiveDetailScreen
```

## 文件存储方案

| 模式 | 实现 | 触发条件 | 文件位置 |
|------|------|----------|----------|
| 默认 | `FileBasedMarkdownFile` | 首次启动自动创建 | `getExternalFilesDir(null)/Appendo.md` |
| SAF | `SafMarkdownFile` | 用户选择"自定义目录" | 用户自选的外部存储位置 |

- SAF 模式通过 `ActivityResultContracts.CreateDocument` 选择文件
- 持久化 URI 权限（`takePersistableUriPermission`）
- 使用 `SharedPreferences` 存储模式和 URI
- URI 失效时自动回退到默认模式

## 关键实现细节

1. **全局文件锁** — `FileOperationLock` 对象确保跨 Activity 的文件操作互斥
2. **时间戳删除** — 使用 `## YYYY-MM-DD HH:mm:ss` 时间戳作为删除标识符，非索引
3. **归档恢复去重** — 按 `timestamp|content` 组合键去重，防止重复条目导致 LazyColumn key 冲突
4. **条目解析** — 正则 `^## \d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$` 匹配时间戳标题行
5. **内容格式保持** — 原始文本直接写入，不做任何处理、过滤或转义
6. **UTF-8 编码无 BOM** — 显式指定 `Charsets.UTF_8`
7. **清空备份** — 清空操作前自动执行归档，确保数据安全
8. **轮询刷新** — `LaunchedEffect` + `delay(2000)` + `isActive` 检查，通过文件修改时间变化触发
9. **自适应图标** — XML Vector Drawable 前景（记事本+羽毛笔）+ 蓝色背景色（#1565C0），适配所有密度
10. **内容安全校验** — 分享接收限制内容最大 10,000 字符
11. **震动反馈** — `Vibrator` + `VibrationEffect`，兼容 API 26+ 和旧版本

## 项目文件结构

```
Appendo/
├── src/main/java/com/yiyue31/android/appendo/
│   ├── MainActivity.kt               # 主入口，Navigation 导航
│   ├── ShareReceiverActivity.kt      # 分享接收入口
│   ├── AppendoApplication.kt         # Application 初始化
│   ├── data/
│   │   ├── FileRepository.kt         # 文件存储偏好和 URI 管理
│   │   └── ArchiveRepository.kt      # 归档文件管理
│   ├── ui/
│   │   ├── MainScreen.kt             # 主界面（含 LinkEntry、parseMarkdownEntries）
│   │   ├── EntryListScreen.kt        # 可复用条目列表组件
│   │   ├── ArchiveListScreen.kt      # 归档管理界面
│   │   ├── ArchiveDetailScreen.kt    # 归档详情界面
│   │   └── ToastUtils.kt             # Toast 兼容工具
│   └── util/
│       ├── MarkdownFileOperations.kt # 文件操作接口 + FileOperationLock
│       ├── MarkdownFileFactory.kt    # 文件操作工厂
│       ├── FileBasedMarkdownFile.kt  # 默认文件存储实现
│       ├── SafMarkdownFile.kt        # SAF 文件存储实现
│       └── MarkdownFormatter.kt      # Markdown 格式化工具
├── src/main/res/
│   ├── drawable/
│   │   └── ic_launcher_foreground.xml  # 矢量图标前景
│   ├── mipmap-anydpi-v26/
│   │   ├── ic_launcher.xml             # 自适应图标
│   │   └── ic_launcher_round.xml       # 圆形自适应图标
│   ├── values/
│   │   ├── colors.xml                  # 图标背景色
│   │   ├── strings.xml                 # 字符串资源
│   │   └── themes.xml                  # 主题
│   └── mipmap-{hdpi,mdpi,...}/         # 各密度图标
├── src/test/java/com/yiyue31/android/appendo/
│   ├── integration/
│   │   └── EntryDeletionIntegrationTest.kt
│   ├── ui/
│   │   ├── MarkdownParserTest.kt
│   │   └── ArchiveRestoreDeduplicationTest.kt
│   └── util/
│       └── MarkdownFileDeleteTest.kt
└── build.gradle.kts
```

## 依赖

| 依赖 | 用途 |
|------|------|
| Compose BOM 2024.06.00 | Compose 版本统一管理 |
| Material 3 | UI 组件库 |
| Activity Compose 1.9.0 | Compose Activity 集成 |
| Lifecycle Compose 2.8.2 | 生命周期感知 |
| Navigation Compose 2.7.7 | 页面导航 |
| DocumentFile 1.0.1 | SAF 文件操作 |
| JUnit 4.13.2 | 单元测试 |
| Mockito 5.10.0 + mockito-kotlin 5.2.1 | 测试 Mock |

## 构建配置

| 配置项 | 值 |
|--------|-----|
| compileSdk | 34 |
| minSdk | 26 |
| targetSdk | 34 |
| JDK | 17 |
| Kotlin | 2.2.10 |
| AGP | 9.1.1 |
| ProGuard | Release 启用（`proguard-android-optimize.txt`） |

---

## v1.1 数据完整性增强设计（v3.1）

> 日期：2026-07-20　关联需求：specs 36–46　状态：设计定稿，待进入执行计划
> 经三轮多角色评审（round1 产品/架构/用户；round2 架构/资深工程师/测试）+ 结构化自查修订

### v2→v3.1 变更摘要（评审 + 自查落地）

| 评审项 | v3.1 处置 |
|--------|---------|
| CB1 recoveryOccurred 跨层 | readAll 改返 ReadResult(content,recovered)；废弃标志 |
| CB2 ZWSP 出口 | 单一 readAllForExternal()；剥离仅限"行首ZWSP+时间戳模式" |
| CB3 现有测试编译失败/平行实现 | 加测试迁移清单；废弃 TestFileBasedMarkdownFile；逻辑/I/O 分离 |
| CB4 测试基建未定 | 定金字塔(JVM/Robolectric/instrumented)+依赖增量 |
| CB5 单调性 vs appendEntry | 单调仅限 append() now 路径；appendEntry 不强制；KDoc |
| CB6 archiveFile 绕过抽象 | 归档走 MarkdownFileOperations；旧归档无ZWSP文档化 |
| CB7 SAF 复杂度 | 保留简化版 .pending/.bak + 实施前 PoC spike |
| R2/R9 读路径不持锁 | readAll/count/initHeader/exists 全 synchronized |
| R7 循环依赖 | 条目知识全收敛进 EntryParser；废弃 formatEntry |
| 风险2 atomicWrite 坑 | createTempFile(parent)、孤儿清理、renameTo fallback、fd.sync |
| 风险3 LinkEntry 字段 | timestamp 保持 String(毫秒串,作key)+timestampDisplay |
| 风险5 单调 ts | 反向扫描最后ts、Instant 比较、parse 失败 fallback |
| 风险6 clear() | 两模式都不走 .bak，直接 atomicWrite FILE_HEADER |
| 风险9 util/ui 分层 | util ParsedEntry + ui LinkEntry 映射 |
| P1-5 count() 正则 | 拆严格写入/宽松解析 |
| R10 内联正则 | 7 处（含 MainActivity.countEntries） |
| 自查A clear/.bak 两模式 | §6 明确 SAF 模式 clear 也跳 .bak |
| 自查B 恢复后排序 | §9 按 (i)：时间戳排序，旧条目回历史位置 |
| 自查C/D atomicWrite 伪代码 | §6 改 File(parent,…) + 孤儿命名前缀匹配 |
| 自查E M1 清空恢复往返 | §13 补测试 |
| 自查F 降级兼容 | §16 已知限制 |
| 自查G 尾换行符 | §13 补测试 |
| 自查H I/O 放大 | §16 声明为已接受权衡 |

### 概述
围绕 specs 需求 36–46，对 util 数据层做四项改造（唯一标识、格式兼容、内容隔离、原子写入）并抽取共享算法；UI 层仅追加"重复内容提示""归档恢复透明计数""恢复 Toast"三处轻量行为。改动限制在数据层 + 少量 UI，不触及 Phase B 的 ViewModel 重构。对应锁定决策：A(毫秒)、B①(零宽空格)、B-a(出口剥离+README)、原子写(默认真原子+SAF 软恢复)。

### 1. 条目唯一标识（specs 36）
- 弃用 `SimpleDateFormat`（非线程安全、Locale 不稳）。改 `java.time`（minSdk 26 原生可用）：`DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)`（immutable 线程安全）。删除 `MarkdownFormatter` 错误的 thread-safe 注释。
- **单调防碰撞（仅 append() 的 now 路径，CB5）**：append 已读全量，反向扫描取最后一条时间戳（非全量 parse）；新 ts = `max(now, last+1ms)`，按 `Instant` 比较；parse 失败（旧秒级/外部乱码）fallback 为 now。
```kotlin
fun nextTimestamp(content: String, now: Instant = Instant.now()): String {
    val lastTs = content.lineSequence().reversed()
        .firstOrNull { isTimestampLine(it) }?.removePrefix("## ")?.trim()
    val base = lastTs?.let { runCatching { parseToInstant(it) }.getOrNull() } ?: now.minusMillis(1)
    val next = if (now.isAfter(base)) now else base.plusMillis(1)
    return formatTimestamp(next)
}
```
- `appendEntry(timestamp, content)` 路径**不强制单调**（归档恢复插入旧 ts 合法），写入 `MarkdownFileOperations` 接口 KDoc。
- delete/update **不靠唯一性短路**：仍 `findEntryBounds` 找首条匹配 + 区间操作 + 越界保护（唯一性是优化而非正确性依赖）。

### 2. 时间戳格式兼容与正则集中化（specs 37）
- `getTimestampRegex()` 改宽松：`^## \d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}(\.\d{1,3})?$`（MULTILINE），同时匹配秒级/毫秒级。
- **count() 兼容（P1-5）**：解析用宽松正则，新旧条目都数得到；写入用 `formatTimestamp` 保证格式。
- 消灭 **7 处**内联正则（FileBased delete/update、Saf delete/update、MainActivity.countEntries、废弃 MarkdownFile.kt、MainScreen.parseMarkdownEntries 内部），统一用 `EntryParser.isTimestampLine()`。

### 3. 数据模型分层（util/ui 解耦；工程师风险9 + 风险3）
- `util/ParsedEntry(rawTimestamp: String, content: String)` —— 纯数据，EntryParser 产出。
- `ui/LinkEntry(timestamp: String, timestampDisplay: String, content: String)` —— 由 ParsedEntry 映射；`timestamp` 是完整毫秒串作 key，`timestampDisplay` 仅 UI 显示。
- `deleteEntry`/`updateEntry` 接口签名不变（接 String），靠宽松正则兼容秒/毫秒。

### 4. 显示层格式化（specs 36 衍生；B1）
- UI 所有渲染点（EntryListScreen 卡片、编辑对话框）只显 `timestampDisplay`（`yyyy-MM-dd HH:mm:ss`），用户永远看不到 `.SSS`。

### 5. 内容隔离——零宽空格（specs 38；B2）
- `EntryParser.ISOLATION_MARKER = "​"`。
- **写入**（EntryParser.format 内逐行扫描内容部分）：仅当某行去掉前导 ZWSP 后 `isTimestampLine()` 为真，**且该行原本不以 ZWSP 开头** → 加一个 ZWSP。幂等。
- **读取**（EntryParser.parse）：以 ZWSP 开头的行不匹配 `^## ` 当内容；收集时若去掉**所有**前导 ZWSP 后 `isTimestampLine()` → `while` 剥离全部前导 ZWSP 还原。用户自有 ZWSP 不受影响。
- **单一出口 `readAllForExternal(): String`（CB2）**：内部 = readAll + `stripIsolationMarkers`；`copyContent`/`shareContent` 全走它。`readAll()`（含 ZWSP）仅供 EntryParser 内部解析。
- **stripIsolationMarkers 范围（风险4）**：**仅剥离"行首 ZWSP + 紧跟时间戳模式"的行**，不做全文 ZWSP 清洗（保护用户合法 ZWSP）。
- **历史归档无 ZWSP（CB6）**：`EntryParser.parse` 视为合法输入照常解析，但不再有注入保护（文档化为已知限制，§16）。
- openFile / 外部编辑器直编：README 声明不支持（依用户习惯低频）。

### 6. 写入原子性（specs 39；B4 + CB7）
**默认模式 FileBasedMarkdownFile——持久化原子（伪代码，自查修订 C/D）：**
```kotlin
fun atomicWrite(file: File, content: ByteArray): Boolean = synchronized(FileOperationLock) {
    val parent = file.parentFile ?: return false
    if (!parent.exists()) parent.mkdirs()
    // 清孤儿 tmp：清理所有以 file.name + ".tmp" 开头的残留（上次崩溃遗留）
    parent.listFiles { f -> f.name.startsWith(file.name + ".tmp") }?.forEach { it.delete() }
    val tmp = File.createTempFile(file.name + ".tmp_", ".md", parent)  // 关键:同目录,避免跨文件系统 EXDEV
    try {
        FileOutputStream(tmp).use { it.write(content); it.fd.sync() }   // fsync
        if (!tmp.renameTo(file)) { file.writeBytes(content); tmp.delete() } // rename 失败 fallback
        true
    } catch (e: IOException) { tmp.delete(); false }
}
```
- delete/update/clear/writeAll/append/initHeader **全走 atomicWrite + 全程 synchronized**（R2/R9）。
- **`clear()` 两模式都不走 .bak（风险6 + 自查A）**：直接 atomicWrite 写 FILE_HEADER。数据安全由 specs 13"清空前自动归档"兜底，不靠 .bak；避免崩溃回滚还原清空前内容。

**SAF 模式 SafMarkdownFile——软恢复（CB7 选项 a，非零丢失）：**
- `.pending` + `.bak` 存**应用私有目录**（按 URI 哈希派生文件名，切文件清旧，防串档）。
- 写前（持锁）：readAll(主) → 写 `.bak` + 建 `.pending`。
- 写主 URI（`"wt"`）→ `ParcelFileDescriptor.getFileDescriptor().sync()` **尽力而为，不依赖**（云盘 provider 可能无效）。
- 写后（持锁）：删 `.pending`。
- **读前 `ensureConsistent()`（持锁，调 `RecoveryDecider` 纯函数）**：`.pending` 存在 → 主可能脏 → 用 `.bak` 覆写主 URI + 删 `.pending` + 返回 `ReadResult(recovered=true)`。
- **明示语义**（design + README）：SAF 非零丢失原子，崩溃回滚到上次良好状态，**最近一次未确认写入可能丢失，但不损坏文件**；与 specs 39 字面"原子性"为契约降级（specs 39 已追加澄清）。
- **实施前置：SAF PoC spike**（见 §15）。

**并发**：所有写路径 + ensureConsistent + readAll（恢复判定）统一 `synchronized(FileOperationLock)`；已验证两 Activity 同进程（AndroidManifest 无 `android:process`），锁有效跨 Activity。

### 7. EntryParser 收敛条目知识（util，纯函数；R7）
把"条目"所有知识收敛进 `EntryParser`（object）：`isTimestampLine`、`parse(content): List<ParsedEntry>`（含 ZWSP 还原）、`format(timestamp, content)`（含 ZWSP 隔离）、`findEntryBounds`、`buildDeletedLines`、`buildUpdatedLines`、`nextTimestamp`、`stripIsolationMarkers`、`ISOLATION_MARKER`、时间戳 formatter。
- **废弃 `MarkdownFormatter.formatEntry`**（消除循环依赖）；MarkdownFormatter 仅留文件级常量（FILE_HEADER、归档文件名格式）。
- `MainScreen.parseMarkdownEntries` 删除，改调 `EntryParser.parse`；`ArchiveRepository` 正则改 `isTimestampLine`。

### 8. ReadResult（CB1，替代 recoveryOccurred 标志）
- `readAll(): ReadResult(content: String, recovered: Boolean)`（数据类）。
- `readAllForExternal(): String` = readAll + stripIsolationMarkers。
- UI 拿 `ReadResult.recovered` 自行决定 Toast；**无跨层全局状态**，为 Phase B 的 Flow/Channel 通知留接入点。

### 9. 归档恢复语义（specs 43；B3 + CB6）
- `MarkdownFileOperations` 加 `appendEntry(timestamp, content): Boolean`；旧 `append(content)` 内部调 `appendEntry(nextTimestamp(...), content)`。
- 归档恢复改用 `appendEntry(归档原 timestamp, content)` → `timestamp|content` 去重真正生效。
- **`MainScreen.archiveFile` 重构（CB6）**：不再直接 FileOutputStream；改走 `MarkdownFileOperations.writeAll`，归档与活动文件**同源同格式**（享 atomicWrite + 锁 + ZWSP）。
- **显示排序（自查B，决策 i）**：恢复的条目保留原时间戳，**按时间戳排序**回到历史位置（与 specs 15"按时间倒序"一致），不临时置顶。
- Toast `已恢复 X 条，跳过 Y 条`；`Y=0` 不显示后缀；提供"查看跳过条目"入口（列表展示被跳过条目）。

### 10. 重复内容（specs 41/42；P1-2/P1-3）
- 手动/分享不去重，始终 `appendEntry(now, content)`（保真优先）。
- 追加后按 content 比对命中 → Toast `已追加（已有相同内容 N 条）`（先确认成功、再旁注重复）。
- **5 秒节流**：同 content 5 秒内只提示一次；时间源抽 `Clock` 接口注入 `FakeClock` 便于测试。

### 11. 顺手修复
- `SafMarkdownFile.exists()`：`SecurityException` → 视为授权失效（上层切默认模式）；其他异常按真不存在，防误 initHeader 覆盖。
- `file.tmp`/`.bak`/`.pending` 一律私有目录，不污染用户可见目录。
- `MainScreen` 文件操作下放 `Dispatchers.IO`（append 变重，防主线程 ANR 回归）。
- 删除废弃 `MarkdownFile.kt`（util/CLAUDE.md 已标废弃）。

### 12. 数据流
- 写：append/appendEntry → synchronized → readAll(持锁) → nextTimestamp(仅 append) → EntryParser.format(ZWSP) → atomicWrite(默认) / .pending+.bak(SAF)。
- 读：synchronized → ensureConsistent(SAF) → EntryParser.parse(ZWSP 还原) → ReadResult。
- 复制/分享：readAllForExternal → 剪贴板/Intent。
- 恢复：ReadResult.recovered=true → UI Toast。

### 13. 测试设计（CB3/CB4 + 边界清单）
**测试金字塔：**
| 层 | 范围 | 工具 | 依赖增量 |
|----|------|------|---------|
| 纯 JVM | EntryParser 全部纯函数、RecoveryDecider、nextTimestamp、去重计数、节流(Clock) | JUnit4+Mockito（已有） | 无 |
| Robolectric JVM | FileBasedMarkdownFile 真 atomicWrite（tmp+rename+fsync）、initHeader/clear/writeAll、exists() 歧义 | Robolectric | robolectric:4.13、androidx.test:core、`unitTests.isIncludeAndroidResources=true` |
| Instrumented | SafMarkdownFile 真 ContentResolver、SAF 授权失效、归档/备份原子写、UI 交互 | androidx.test + Compose UI Test | androidx.test.ext:junit、ui-test-junit4、testInstrumentationRunner |

**现有测试迁移清单（CB3，编码前置）：**
- `MarkdownParserTest`（10 处调 parseMarkdownEntries）→ 迁移为 `EntryParserTest`，旧文件删除。
- `ArchiveRestoreDeduplicationTest`、`EntryDeletionIntegrationTest` → 改 import 到 EntryParser。
- `MarkdownFileDeleteTest`/`MarkdownFileUpdateTest` 内嵌 `TestFileBasedMarkdownFile`/`TestUpdateFileBasedMarkdownFile` → **废弃**（平行实现掩盖生产代码）；这两个测试改为 Robolectric 跑真 FileBasedMarkdownFile + 临时目录。
- 接口加 appendEntry/readAll→ReadResult 后，所有 test double 同步补占位实现。

**必测边界：**
- 毫秒：越界进位(.999→.000)、时钟回拨、空文件、仅 header、最后一条秒级兼容、并发 50×50→2500 互异。
- ZWSP：首行/中间行/末行匹配时间戳、CRLF+ZWSP、多连续 ZWSP、行中 ZWSP、出口剥离后剪贴板无 ZWSP。
- 解析宽松：秒+毫秒混合、`.1/.12/.123` 三种位数、行尾空格。
- 原子恢复：主+.pending 内容不同、主+.bak+.pending 三者并存、.pending 0 字节、.bak 也损坏、切 URI 清旧 .bak、tmp 残留不影响下次。
- SAF 降级/授权：openOutputStream 返 null→fallback、抛 SecurityException→false、URI 失效→上层切默认。
- 混合写入：append(new)→appendEntry(old)→append(new) 三条 ts 互异。
- 节流：5 秒内 3 次同内容 Toast 1 次，第 6 秒再触发。
- **清空→自动备份归档→恢复往返（自查E，M1）**：清空触发自动归档 → 从该归档恢复 → 条目按原时间戳回到历史位置。
- **atomicWrite 后文件尾换行符一致性（自查G）**：覆写前后尾部结构（`---\n` vs `---`）测试固化。

### 14. 受影响文件清单
| 文件 | 改动 | Phase B 标注 |
|------|------|------------|
| util/EntryParser.kt（新） | 收敛全部条目知识（纯函数） | ViewModel 直接依赖 |
| util/MarkdownFormatter.kt | 缩为文件级常量；删 formatEntry 与错误注释 | — |
| util/MarkdownFileOperations.kt | 加 appendEntry；readAll→ReadResult；加 readAllForExternal | — |
| util/FileBasedMarkdownFile.kt | atomicWrite、EntryParser、appendEntry、读路径持锁 | — |
| util/SafMarkdownFile.kt | .pending/.bak+ensureConsistent+持锁、appendEntry、exists() 修歧义 | — |
| data/FileRepository.kt | .bak/.pending 私有目录管理、切文件清旧 | Phase B: Flow 通知 |
| ui/LinkEntry.kt（从 MainScreen 拆出） | timestamp+timestampDisplay+content | — |
| ui/MainScreen.kt | 删 parseMarkdownEntries；copy/share 走 readAllForExternal；重复 Toast；恢复 Toast；archiveFile 重构；I/O 下放 IO | Phase B: 迁 ViewModel |
| ui/ArchiveListScreen.kt | appendEntry 恢复+透明计数+跳过清单；恢复排序按时间戳 | — |
| MainActivity.kt | countEntries 改 isTimestampLine | — |
| README.md | 外部编辑器不支持、SAF 软恢复语义、降级兼容限制 | — |
| build.gradle.kts | 加 Robolectric + androidTest 依赖 | — |
| test/… | EntryParserTest、RecoveryDeciderTest、FileBasedAtomicWriteTest(Robolectric)、SafRecoveryInstrumentedTest、迁移现有测试 | — |
| util/MarkdownFile.kt | 删除（废弃） | — |

### 15. SAF PoC spike（CB7 实施前置任务）
编码 SAF 部分前，独立 spike 验证：
- 目标 provider（本地外存 + 用户实际可能用的云盘）的 `ParcelFileDescriptor.getFileDescriptor().sync()` 行为（是否抛异常/被忽略）。
- `openOutputStream("wt")` 的截断语义与窗口实测。
- 结论：若云盘 provider sync 不可靠，则对云盘场景单独降级（仅本地 .bak 兜底损坏、不依赖 sync），其余不变。

### 16. 已知约束与后续（debt-tracker）
- 外部编辑器直编 Appendo.md 不支持（README）。
- SAF 软恢复：崩溃回滚到 .bak，最近一次写入可能丢失但不损坏（README + specs 39 澄清）。
- 历史归档文件无 ZWSP 注入保护（已知限制）。
- **降级兼容（自查F）**：新版本写毫秒时间戳后，若安装回老版本 App，老版本的严格秒级正则读不了毫秒条目；无法控制旧版本，README 注明"不建议降级安装"。
- **I/O 放大（自查H）**：SAF 每次 .pending/.bak 使单次写入 I/O 放大 4-8 倍、append 改读全量；个人笔记文件体量下可接受，属已接受的完整性权衡。
- **轮询锁竞争（R11）**：Phase B 前，2 秒轮询 readAll 持锁与 append 抢锁存在轻度饥饿（大文件 + 高频分享时分享接收体感延迟）；Phase B 改 FileObserver 后消除。
- CRLF/孤立 `\r` 在 read-modify-write 中可能被静默规范化（历史问题，加测试固化现状）。
- 文件无界增长：Phase B 后考虑条目数阈值自动归档。
- exists()/hasEntries() 拆分、ViewModel 重构、FileObserver：Phase B。
- App 内搜索（ZWSP 不影响，parse 已剥离）：独立产品增强后续评估。

## v1.3.x 微文案与交互一致性设计（2026-10-06；2026-10-07 三角色评审修订）

> 对应需求：specs.md 第 15 章（74~81）。现状快照见 architecture.md（唯一权威源），本文按"设计变更史"定位记录本批技术方案。需求侧已闭环：多角色评审（产品/架构/用户）→ 修订 → 三角色复核（6 项必修确认落地）。设计侧经三角色评审（软件架构师/资深软件工程师/资深测试工程师，串行）修订：消除"定位失败"死分支、补 §4.5 实现约束与 pendingReminderTs 接线、§6 补替换标题色、§7 补 SAF 覆盖与代码级核对豁免、断言增强与 grep 防线。

### 1. 改动总览

纯 UI/文案层 + 2 处小逻辑 + 1 处接口增强，**无存储协议变更**。

| 需求 | 改动点 | 类型 |
|---|---|---|
| 74 | 9 处文案替换点（§2 映射表，以表为执行准绳） | 字符串 |
| 75 | 编辑对话框两次级按钮重写 + 手动输入路径补成功 toast + 时间戳竞态消除（§4） | 小逻辑 + 接口 |
| 76 | 手动输入确认按钮空白分支（§5） | 小逻辑 |
| 77 | 清空确认"确定"→"清空" | 字符串 |
| 78 | 4 处颜色（§6） | 颜色 |
| 79 | 复制全部 toast + 编辑 placeholder | 字符串 |
| S1 | 新建 `ui/Microcopy.kt` 常量源（§3） | 新文件 |
| S2 | architecture.md §8.3 一句过时表述修正（§8） | 文档 |

### 2. 文案字面映射（Generator 执行清单）

| 位置 | 现状 | 定稿 |
|---|---|---|
| MainScreen.kt:1014 | 保存 | 保存修改 |
| MainScreen.kt:1296 | 内容已追加 | 已追加 |
| MainScreen.kt:1233 | 已追加，未能定位新条目 | **删除该分支**（§4.5 新接口下"追加成功但拿不到 ts"不可达——null ⟺ 追加未发生，失败统一 toast"追加失败"；specs 74 第 8 条已加注记） |
| ArchiveListScreen.kt:341-342 | 已恢复 N 条 / 已恢复 N 条，跳过 M 条已存在 | 已追加 N 条 / 已追加 N 条（跳过 M 条已存在） |
| ShareReceiverActivity.kt:113 | 写入失败 | 追加失败 |
| ShareReceiverActivity.kt:111 | 写入失败：自定义目录授权可能已失效，请打开 appendo 重选文件 | 追加失败：自定义目录授权可能已失效，请打开 appendo 重选文件 |
| ShareReceiverActivity.kt:97 | 已保存到默认文件（自定义目录已失效，可打开 appendo 重选） | 已追加到默认文件（自定义目录已失效，可打开 appendo 重选） |
| ShareReceiverActivity.kt:94 | 内容过长（上限 N 字符），未保存 | 内容过长（上限 N 字符），未追加 |
| ShareReceiverActivity.kt:109 | 未找到可保存的内容 | 未找到可追加的内容 |
| MainScreen.kt:890 | 确定（清空确认，红） | 清空（红） |
| MainScreen.kt:1456 | 已复制到剪贴板 | 已复制全部内容 |
| MainScreen.kt:935 | 无内容（placeholder） | 请输入内容 |

### 3. 文案常量源（S1，应补项落地机制）

- 新建 `ui/Microcopy.kt`：`internal object Microcopy { ... }`，集中第 15 章涉及的按钮与反馈文案（如 `BTN_APPEND = "追加"`、`BTN_SAVE_CHANGES = "保存修改"`、`TOAST_APPENDED = "已追加"`、`TOAST_APPEND_FAILED = "追加失败"`、`TOAST_CONTENT_EMPTY = "内容不能为空"` 等）；按钮与 Toast 引用同一常量，编译期保证"按钮/成功/失败动词一致"（specs 74），给 81(c)(g) 提供防线。含参文案（如"已追加（已有相同内容 N 条）""内容过长（上限 N 字符）"）用 `const val` 模板或函数返回，Generator 自决
- **范围控制**：第 15 章涉及文案及其直接共用项 + 两处已达标同族文案一并入常量（评审补充）："已复制全部内容"（ArchiveListScreen.kt:206 与 MainScreen.kt:1456 改后同字面）、"已追加（已有相同内容 N 条）"（MainScreen.kt:1294）——否则"编译期保证一致"不闭合。"内容不能为空"在 §5 落地后共五处引用（现状四处：MainScreen.kt:959/985/1217/1251 + 新增确认按钮一处）共用一个常量；不做全量 ~50 条收敛（超出本批范围）
- 选型理由：项目单语言中文、无 localized 需求，Kotlin 常量 object 成本低于 strings.xml 且编译期引用（三角色评审 AR 建议采纳）

### 4. 需求 75"次级动作先落盘"实现方案

**4.1 编辑对话框"⏰ 设提醒"重写**（现 MainScreen.kt:939-954）：

```
onClick:
 1. editContent.isBlank() → toast 内容不能为空；return（框保持）      // 76a 补齐
 2. val ts = selectedEntry.timestamp（updateEntry 不变时间戳，需求 24）
 3. editContent != selectedEntry.content → updateEntry：
      失败 → toast 保存失败；return（框保持）                        // 75 保存失败不执行后续（return 必须在关框之前）
      成功 → setFileLastModified(now) + refreshEntryCount() + toast 已保存  // 技术前提1（4.6）
 4. pendingReminderTs = ts（先赋值！该状态与 selectedEntry 解耦——选择器/替换确认只读它，MainScreen.kt:1137/1154-1175；漏写会因 :1138 守卫静默不弹选择器）
 5. 关闭编辑对话框（showDetailDialog=false; selectedEntry=null; editContent=""）+ keyboardController?.hide()
 6. hasUnfired(ts) ? showOverwriteReminder=true : showReminderPicker=true  // 替换确认在保存后
```

**4.2 编辑对话框"添加到日历"同构**（现 :957-966，已有空校验保留）：空校验 → 有修改先保存（同 4.1 步 3，失败 return）→ 关框 + `keyboardController?.hide()` → `CalendarLauncher.launch`（失败 toast"未找到日历应用"，不回滚已保存内容——specs 75 独立反馈）。

**4.3 手动输入两次级动作**（现 :1214-1275，空校验现状已有、保留）：改用 `appendReturningTimestamp`（§4.5）——成功：toast"已追加"（**不带**需求 42 的重复旁注，旁注口径仅确认按钮路径）→ 清空输入/关框/hide → 开提醒选择器或跳日历；失败（null）：toast"追加失败"、框保持。原"定位新条目失败"分支（:1233）**删除**（不可达，见 §2 注）。

**4.4 无修改路径**：同样先关框再执行后续（与有修改路径的"离开编辑状态去设提醒"语义一致）。

**4.5 竞态消除（技术前提 2）**：`MarkdownFileOperations` 接口新增：

```kotlin
/** 追加内容并返回新条目时间戳（时间戳生成与写入在同一持锁事务内）。失败返回 null。 */
fun appendReturningTimestamp(content: String): String?
```

- **抽象方法、不给默认实现**（无法给出正确默认；全库仅 FileBased/Saf 两实现且无测试 fake，编译器强制两实现同步落地）
- **实现约束（评审补充，防 TOCTOU 残留与双读）**：方法体整体 `synchronized(FileOperationLock)`，时间戳基于**同一次持锁读取**的内容生成；改法为镜像现有 `append()` 五行结构（FileBasedMarkdownFile.kt:19-29 / SafMarkdownFile.kt:126-136 的 读→`EntryParser.nextTimestamp(content)`→appendEntryInternal），并把 `append()` 重构为 `appendReturningTimestamp(content) != null` 消除双份重复（JVM 锁可重入，正确性等价；避免经公共 appendEntry 造成二次全量读）
- SAF 失败语义已核实干净：`safAtomicWrite` 失败 → .pending 残留 → 返回 null，下次读经 ensureConsistent 从 .bak 回滚，"null = 未追加"自洽
- 手动输入+设提醒路径（4.3）改用该接口定位新条目 ts，**替代**现状 `parseMarkdownEntries(mdFile.readAll()).lastOrNull()?.timestamp`（MainScreen.kt:1225，append 与 readAll 两次独立持锁存在并发插入竞态）
- 旧 `append()` 保留调用方语义不变（分享接收、手动输入确认/日历路径），其 KDoc 加一句"需得知新条目时间戳的调用方请改用 appendReturningTimestamp"（防误用）
- 方案取舍：备选"调用侧预计算 max 时间戳"仍有 TOCTOU 窗口；本方案把生成收敛到锁内，彻底消除且为未来"写入即知标识"铺路

**4.6 刷新联动（技术前提 1）**：4.1/4.2 新增的 updateEntry 写盘点后调用 `fileRepository.setFileLastModified(System.currentTimeMillis())` + `refreshEntryCount()`（对照编辑确认按钮现状 :997-998 已有同样调用）。

### 5. 需求 76 实现

手动输入确认按钮（现 :1281-1308）重构首行：`inputContent.isBlank() → toast 内容不能为空 + return@TextButton（不关框）`；其余追加逻辑不变。

### 6. 需求 78 颜色

- MainScreen.kt:1166 替换按钮 **及 :1160"替换已有提醒？"对话框标题**：加 `color = MaterialTheme.colorScheme.error`（破坏性=红；现状四个破坏性对话框标题全红，仅此漏——评审补充，78(a) 字面要求）
- ArchiveListScreen.kt:295（恢复对话框标题）与 :356（追加按钮）：`successColor` → `MaterialTheme.colorScheme.primary`
- 滑动指示背景色不动（需求 22/28 已固化）

### 7. 测试方案

- **单测**（`./gradlew test --max-workers=1` 串行，仅默认实现可 JVM 测，SAF 无 instrumented 基建 TD-003）：`appendReturningTimestamp` 三用例，断言强度对照 FileBasedMarkdownFileTest 既有风格——①成功 round-trip：返回 ts == 末条 rawTimestamp **且**该条 content == 追加内容；②连续多次调用 ts 唯一（需求 36 语义，对照 :57 既有单调性用例）；③失败（`file.mkdirs()` 模拟读失败，先例 :145-170）返回 null **且无写入痕迹**（`file.listFiles()` 为空，对照 :152-156）。不设第四用例（空内容校验在上游，隔离已有覆盖）。FileBased 实现的 catch 日志与方法名对应
- **81 a~i**：真机人工走查（无 Compose UI 测试基建；TD-024：交互验收以人工为准）。**代码级核对豁免清单**（真机无廉价触发手段，与 h 同待遇）：(f) 失败分支（ShareReceiverActivity:111，真机手段均命中回退分支）、(f) 超长与无内容可用 adb am start 注入（走查清单第 10 步）、75 的两个失败子分支（4.1"保存失败"return 在关框之前；日历 launch false 分支现状已符合"不回滚"）
- **SAF 覆盖**（评审补充，堵唯一写路径缺口）：SAF 模式下重复走查 (b3)/(h) 各一次（走查清单第 15~16 步的 SAF 块顺带执行）；另由 Evaluator 代码级核对 SafMarkdownFile.appendReturningTimestamp 与默认实现同锁路径同构
- **grep 防线（Evaluator 检查单固化，零成本客观）**：①旧字面清零——`grep -rn "内容已追加\|已恢复\|写入失败\|已保存到默认文件\|未找到可保存\|已复制到剪贴板\|未能定位新条目" Appendo/src/main/java --include="*.kt"` 预期仅剩 KDoc/注释历史引述；②S1 落地核实——"已追加/保存修改/追加失败/内容不能为空"等新字面在 MainScreen/ShareReceiverActivity/ArchiveListScreen 中仅应经 `Microcopy` 常量出现，不应有裸字面量
- **回归**：既有 15 个测试文件全量跑通（真实交集仅 FileBasedMarkdownFileTest 与边缘的 CalendarEntryMapper/DuplicateHintThrottle）

### 8. 文档同步（并入 Generator 任务）

- README 交互说明：注明主列表/归档列表滑动差异（80a）、"归档/归档管理"区别（80b）
- architecture.md §8.3（:394）：过时句修正为"超长被拒绝写入（不截断），Toast 明确提示『内容过长（上限 N 字符），未追加』"，移除 TD-020 待改进备注
- CHANGELOG.md：v1.3.x 条目
- docs/plans/debt-tracker.md：TD-025/026 已登记（本批仅引用不处理）

### 9. 实施顺序（执行计划基础）

specs 的"批次"是**优先级/验收顺序**语义；编码按改动类型组织任务、一次完成（避免同文件字符串改两轮）：

- T1：`Microcopy.kt` + §2 全部文案替换（覆盖需求 74/77/79 字面，含 §3 两处达标文案入常量与 :1233 死分支删除）
- T2：需求 76 空校验（§5）
- T3：需求 75 对话框逻辑（§4.1~4.4、4.6，注意 4.1 步 4 的 pendingReminderTs 先赋值）
- T4：`appendReturningTimestamp`（§4.5 全部约束）+ 4.3 接线 + 单测（§7）
- T5：需求 78 颜色（§6，含 :1160 标题）
- T6：文档同步（§8）
- T7：全量单测（串行）+ Evaluator grep 防线
- 验收顺序按 specs：第一批条文（74~76）先行核对，再第二批（77~80），81 a~i 全量走查（走查清单见执行计划）
