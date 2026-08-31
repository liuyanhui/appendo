# 主包目录说明 (`com.yiyue31.android.appendo`)

## 目录职责

应用的入口层，包含 Activity 定义、Application 初始化和 Navigation 路由。不包含业务逻辑。

## 关键文件

| 文件 | 职责 |
|------|------|
| `MainActivity.kt` | 主 Activity，初始化 FileRepository 并通过 Navigation Compose 管理三个页面路由；接收提醒通知深链时间戳（`scrollTs`，经 `onNewIntent` 更新） |
| `ShareReceiverActivity.kt` | 独立 Activity，处理外部分享 Intent（ACTION_SEND），IO 协程写入后自动关闭 |
| `AppendoApplication.kt` | Application 子类，启动时创建遗留 `write_feedback` 通知渠道（提醒渠道由 `reminder/NotificationHelper` 按需创建） |

## 架构要点

### 双 Activity 设计

- **MainActivity**：主界面入口，通过 `AppendoNavHost` 管理 Compose Navigation 路由
- **ShareReceiverActivity**：接收其他应用的分享内容，独立运行在后台线程写入，完成后 `finish()`

为什么需要两个 Activity？
- 分享操作是独立的、无需 UI 的，使用独立 Activity 可以避免干扰主界面
- ShareReceiverActivity 不经过 Compose UI，直接操作文件，保证写入速度

### Navigation 路由结构

```
MainActivity
  └── AppendoNavHost (NavHost)
       ├── "main"            → MainScreen (主页，笔记列表)
       ├── "archive_list"    → ArchiveListScreen (归档列表)
       └── "archive_detail/{filePath}" → ArchiveDetailScreen (归档详情)
```

- 路由参数使用 `Uri.encode()`/`Uri.decode()` 处理文件路径
- 归档详情页通过解析文件名提取时间戳（格式：`Appendo_yyyyMMdd_HHmmss.md`）

### 业务子包

`data/`（存储偏好与归档）、`ui/`（Compose 界面）、`util/`（纯逻辑核心与文件协议）、`reminder/`（本地提醒的 Android 侧：闹钟调度/通知/接收器/sidecar 单例）。各子包详见各自 CLAUDE.md；跨模块协作与数据协议见 `docs/architecture.md`。

## 依赖关系

```
MainActivity → data/FileRepository (创建实例并传入 Compose)
             → ui/MainScreen, ui/ArchiveListScreen, ui/ArchiveDetailScreen

ShareReceiverActivity → data/FileRepository
                      → util/SafMarkdownFile, util/FileBasedMarkdownFile

AppendoApplication → 无业务依赖（仅初始化通知渠道）
```

## 扩展指南

- **添加新页面**：在 `AppendoNavHost` 中添加新的 `composable` 路由
- **添加新的 Intent 入口**：创建新 Activity 并在 AndroidManifest.xml 注册 intent-filter
- **添加新导航参数**：使用 `navArgument` 定义，注意 URI 编码处理
