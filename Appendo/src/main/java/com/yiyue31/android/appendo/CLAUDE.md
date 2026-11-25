# 主包目录说明 (`com.yiyue31.android.appendo`)

## 目录职责

应用的入口层，包含 Activity 定义与 Navigation 路由。不包含业务逻辑。（Application 子类已随 v1.5.0 提醒子系统退役删除，无 Application 初始化。）

## 关键文件

| 文件 | 职责 |
|------|------|
| `MainActivity.kt` | 主 Activity，初始化 FileRepository 并通过 Navigation Compose 管理页面路由（main/topics/topic_workspace/archive_list/archive_detail） |
| `ShareReceiverActivity.kt` | 独立 Activity，处理外部分享 Intent（ACTION_SEND），IO 协程写入后自动关闭 |

## 架构要点

### 双 Activity 与导航

- **MainActivity**：主界面入口，经 `AppendoNavHost` 管理 Compose Navigation 路由（持有主题三态 State、冷启动窗口主题）
- **ShareReceiverActivity**：接收分享内容，后台线程写入后 `finish()`

为什么是两个 Activity、完整路由图、路径参数约定、归档命名与解析规则：见 `docs/architecture.md` §1.2/§3.5（系统级现状只在该文档维护——分工规则见其文末）。

### 业务子包

`data/`（存储偏好与归档）、`ui/`（Compose 界面）、`util/`（纯逻辑核心与文件协议）。提醒子系统已随 v1.5.0 退役（specs 16.C，系统日历承接）；各子包详见各自 CLAUDE.md；跨模块协作与数据协议见 `docs/architecture.md`。

## 依赖关系

```
MainActivity → data/FileRepository (创建实例并传入 Compose)
             → data/ThemePreferences (主题三态读写)
             → data/ArchiveRepository, data/ArchiveFile (归档元数据解析)
             → ui/MainScreen, ui/TopicListScreen, ui/TopicWorkspaceScreen,
               ui/ArchiveListScreen, ui/ArchiveDetailScreen
             → ui/theme/AppendoTheme, ui/theme/ThemeMode
             → util/EntryParser (路由兜底计数)

ShareReceiverActivity → data/FileRepository
                      → util/SafMarkdownFile, util/FileBasedMarkdownFile

（无 Application 子类——v1.5.0 随提醒子系统退役删除）
```

## 扩展指南

- **添加新页面**：在 `AppendoNavHost` 中添加新的 `composable` 路由
- **添加新的 Intent 入口**：创建新 Activity 并在 AndroidManifest.xml 注册 intent-filter
- **添加新导航参数**：使用 `navArgument` 定义，注意 URI 编码处理
