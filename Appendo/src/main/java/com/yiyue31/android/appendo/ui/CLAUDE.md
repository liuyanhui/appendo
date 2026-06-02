# UI 层目录说明 (`ui/`)

## 目录职责

UI 层，包含所有 Compose 界面组件。采用 Jetpack Compose + Material 3 构建。

## 关键文件

| 文件 | 职责 | 复杂度 |
|------|------|--------|
| `MainScreen.kt` | 主页面：笔记列表、操作按钮、输入/清空/关于对话框、文件轮询刷新 | 高 |
| `EntryListScreen.kt` | 可复用笔记列表组件：条目卡片、滑动删除、长按复制 | 中 |
| `ArchiveListScreen.kt` | 归档列表页：归档卡片、滑动删除/追加恢复 | 中 |
| `ArchiveDetailScreen.kt` | 归档详情页：复用 EntryListScreen（只读模式） | 低 |
| `AppColors.kt` | 全局颜色常量：Primary(蓝)、Danger(红)、Success(绿) | 低 |
| `ToastUtils.kt` | 自定义 Toast：圆角半透明黑色背景，兼容旧版 Android | 低 |

## 导航流程

```
MainScreen ("main")
  ├── 点击菜单"归档管理" → ArchiveListScreen ("archive_list")
  │                          └── 点击归档卡片 → ArchiveDetailScreen ("archive_detail/{filePath}")
  ├── 点击菜单"归档" → 执行归档操作（不导航）
  ├── 点击菜单"打开文件" → 调用外部查看器
  ├── 点击菜单"自定义目录" → SAF 文件选择器
  └── 点击菜单"关于" → 弹出对话框
```

## 数据模型

```kotlin
// MainScreen.kt 中定义
data class LinkEntry(
    val timestamp: String,  // 格式: "yyyy-MM-dd HH:mm:ss"
    val content: String     // 用户输入的原始内容
)
```

## 核心交互模式

| 操作 | 触发方式 | 行为 |
|------|---------|------|
| 复制单条 | 长按条目卡片 | 复制该条目内容到剪贴板，触感反馈 |
| 删除条目 | 右滑条目卡片 | 弹出确认对话框，按时间戳删除 |
| 复制全部 | 点击"复制全部"按钮 | 复制完整文件内容到剪贴板 |
| 分享全部 | 点击"分享全部"按钮 | 调用系统分享 |
| 清空 | 长按"清空"按钮 | 自动备份后清空当前文件 |
| 手动输入 | 点击"手动输入"按钮 | 弹出输入对话框，追加到文件 |
| 归档恢复 | 左滑归档卡片 | 去重后追加到当前文档 |

## EntryListScreen 复用设计

`EntryListScreen` 是跨场景复用的列表组件，通过参数控制行为：

- **MainScreen 使用**：传入 `readOnly = false`，启用滑动删除
- **ArchiveDetailScreen 使用**：传入 `readOnly = true`，禁用滑动删除
- `sourceFileName` 参数：可选，归档详情页显示来源文件名

## 文件刷新机制

MainScreen 使用轮询机制检测文件变更：
- 间隔：2 秒（`FILE_CHANGE_POLL_INTERVAL_MS`）
- 比较逻辑：`fileRepository.getFileLastModified()` 与本地缓存对比
- 触发写入后主动调用 `setFileLastModified()`，确保下轮轮询能检测到变化

## 依赖关系

```
MainScreen → data/FileRepository
           → util/MarkdownFileFactory, MarkdownFileOperations
           → util/MarkdownFormatter
           → util/FileBasedMarkdownFile, SafMarkdownFile

ArchiveListScreen → data/FileRepository, data/ArchiveRepository
                  → util/MarkdownFileFactory
                  → ui/EntryListScreen (共享组件)

ArchiveDetailScreen → ui/EntryListScreen (只读模式)
```

## 扩展指南

- **添加新页面**：创建新的 `@Composable` 函数，在 `MainActivity.AppendoNavHost` 注册路由
- **修改列表样式**：修改 `EntryListScreen` 的 `EntryCard` 组件
- **添加新交互**：在 `EntryListScreen` 添加新回调参数，注意 `readOnly` 模式下的行为
- **添加新按钮**：在 `MainScreen` 的按钮区域（2x2 Row 布局）中添加
- **修改颜色主题**：修改 `AppColors.kt` 中的颜色值
