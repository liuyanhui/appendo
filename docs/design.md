# Appendo - 设计方案

## 修订记录

| 日期 | 变更人 | 变更摘要 |
|------|--------|---------|
| 2026-04-01 | — | 初始版本 |
| 2026-04-10 | Yiyue | 去除 BOM；Toast 改 Notification；新增分享和归档功能；写入加同步锁；条目计数改为按时间戳标题 |
| 2026-04-16 | Yiyue | 新增手动输入功能；新增内容预览功能；应用定位调整为速记应用 |
| 2026-04-16 | Yiyue | 优化文件存储：首次启动使用默认目录；归档自动生成文件名；支持混合存储模式 |
| 2026-04-17 | Yiyue | 新增归档管理功能；统一条目交互模式；实现归档恢复功能；修复删除索引bug；改用时间戳作为删除标识符 |
| 2026-04-18 | Yiyue | 提取 MarkdownFileOperations 接口和工厂模式；实现归档恢复去重；自适应图标矢量图；项目统一命名为 Appendo |

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
- 常量：`FILE_HEADER = "# Link Collection\n"`
- `formatEntry(content)` — 生成带时间戳的条目文本
- `getTimestampRegex()` — 返回时间戳正则

### UI 层

#### `LinkEntry`
- 数据类：`timestamp: String`、`content: String`

#### `parseMarkdownEntries(content)`
- 解析 Markdown 内容为 `List<LinkEntry>`
- 跳过文件头 `# Link Collection` 和分隔线 `---`
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
