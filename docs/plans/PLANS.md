# Appendo - 项目计划

## 修订记录

| 日期 | 变更人 | 变更摘要 |
|------|--------|---------|
| 2026-04-10 | Yiyue | 初始版本 |
| 2026-04-18 | Yiyue | 更新为实际完成状态 |

## 里程碑

| 阶段 | 内容 | 状态 |
|------|------|------|
| M1 | 项目脚手架搭建 | ✅ 完成 |
| M2 | 核心文件操作层 | ✅ 完成 |
| M3 | 分享接收功能 | ✅ 完成 |
| M4 | 主界面 UI | ✅ 完成 |
| M5 | 完整功能（归档/管理/恢复） | ✅ 完成 |
| M6 | 测试与验收 | ✅ 完成 |

## 任务拆分

### M1: 项目脚手架 ✅

| # | 任务 | 状态 |
|---|------|------|
| 1.1 | 创建 Android 项目（Kotlin + Compose，minSdk 26，targetSdk 34） | ✅ |
| 1.2 | 配置依赖（Compose BOM Material 3, Activity Compose, Navigation Compose） | ✅ |
| 1.3 | 建立 `src/` 目录结构：`ui/`, `data/`, `util/` | ✅ |

### M2: 核心文件操作层 ✅

| # | 任务 | 状态 |
|---|------|------|
| 2.1 | 定义 `MarkdownFileOperations` 接口（append, readAll, clear, exists, count, initHeader, deleteEntry, writeAll） | ✅ |
| 2.2 | 实现 `FileBasedMarkdownFile`（默认存储） | ✅ |
| 2.3 | 实现 `SafMarkdownFile`（SAF 存储，含追加降级） | ✅ |
| 2.4 | 实现 `MarkdownFileFactory` 工厂模式 | ✅ |
| 2.5 | 实现 `MarkdownFormatter` 共享格式化工具 | ✅ |
| 2.6 | 实现 `FileOperationLock` 全局同步锁 | ✅ |

### M3: 分享接收功能 ✅

| # | 任务 | 状态 |
|---|------|------|
| 3.1 | 创建 `ShareReceiverActivity`，配置 Intent Filter（ACTION_SEND, text/plain） | ✅ |
| 3.2 | 解析 Intent EXTRA_TEXT | ✅ |
| 3.3 | 内容安全校验（最大 10,000 字符） | ✅ |
| 3.4 | 支持 SAF 和默认文件双模式 | ✅ |

### M4: 主界面 UI ✅

| # | 任务 | 状态 |
|---|------|------|
| 4.1 | `MainScreen`：TopAppBar + 2x2 按钮网格 + 条目列表 | ✅ |
| 4.2 | 手动输入对话框（自动弹出键盘，多行输入） | ✅ |
| 4.3 | 复制全部 / 分享全部 / 清空（长按+备份） | ✅ |
| 4.4 | `EntryListScreen` 可复用组件（长按复制、右滑删除） | ✅ |
| 4.5 | 实时刷新（每 2 秒轮询文件修改时间） | ✅ |
| 4.6 | 菜单：归档、归档管理、打开文件、自定义目录、关于 | ✅ |

### M5: 归档与管理 ✅

| # | 任务 | 状态 |
|---|------|------|
| 5.1 | `ArchiveRepository`：归档文件列表、删除、内容读取 | ✅ |
| 5.2 | `ArchiveListScreen`：归档列表、左滑追加、右滑删除 | ✅ |
| 5.3 | `ArchiveDetailScreen`：归档详情（只读模式） | ✅ |
| 5.4 | 归档恢复去重（按 timestamp|content 组合键去重） | ✅ |
| 5.5 | Navigation Compose 页面导航 | ✅ |
| 5.6 | 自适应图标（矢量图前景 + 蓝色背景） | ✅ |

### M6: 测试与验收 ✅

| # | 任务 | 状态 |
|---|------|------|
| 6.1 | `MarkdownParserTest`：Markdown 解析测试 | ✅ |
| 6.2 | `MarkdownFileDeleteTest`：条目删除测试 | ✅ |
| 6.3 | `ArchiveRestoreDeduplicationTest`：归档恢复去重测试 | ✅ |
| 6.4 | `EntryDeletionIntegrationTest`：删除集成测试 | ✅ |

## 文件结构

```
src/main/java/com/yiyue31/android/appendo/
├── MainActivity.kt               # 主入口，Navigation 导航
├── ShareReceiverActivity.kt      # 分享接收入口
├── AppendoApplication.kt         # Application 初始化
├── ui/
│   ├── MainScreen.kt             # 主界面（含 LinkEntry、parseMarkdownEntries）
│   ├── EntryListScreen.kt        # 可复用条目列表组件
│   ├── ArchiveListScreen.kt      # 归档管理界面
│   ├── ArchiveDetailScreen.kt    # 归档详情界面
│   └── ToastUtils.kt             # Toast 兼容工具
├── data/
│   ├── FileRepository.kt         # 文件存储偏好和 URI 管理
│   └── ArchiveRepository.kt      # 归档文件管理
└── util/
    ├── MarkdownFileOperations.kt # 文件操作接口 + FileOperationLock
    ├── MarkdownFileFactory.kt    # 文件操作工厂
    ├── FileBasedMarkdownFile.kt  # 默认文件存储实现
    ├── SafMarkdownFile.kt        # SAF 文件存储实现
    └── MarkdownFormatter.kt      # Markdown 格式化工具

src/test/java/com/yiyue31/android/appendo/
├── integration/
│   └── EntryDeletionIntegrationTest.kt
├── ui/
│   ├── MarkdownParserTest.kt
│   └── ArchiveRestoreDeduplicationTest.kt
└── util/
    └── MarkdownFileDeleteTest.kt
```
