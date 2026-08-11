# Appendo

一个极简的 Android 速记应用，通过系统分享功能自动收集内容，或手动输入文字，将所有内容追加保存到 Markdown 文件中。

## 功能特性

- **分享收集** — 从任意应用分享链接或文本，自动追加保存
- **手动输入** — 在应用内直接输入文字内容，自动弹出键盘
- **内容预览** — 实时查看已收集的所有条目（按时间倒序）
- **单条删除** — 右滑删除单条内容，按时间戳精准定位
- **条目编辑** — 点击条目弹出编辑对话框，修改内容后手动保存，时间戳保持不变
- **添加到日历** — 将条目内容预填并拉起系统日历新建事件（时间与告警在日历内设置）
- **本地提醒** — 为条目设本地提醒，到点 appendo 自发通知（⏰），点按回到该条记录；支持贪睡、徽标、开机后补发
- **格式保留** — 保持原始内容格式（换行、缩进、符号等），不做任何处理
- **文件管理** — 默认自动创建文件，支持自定义外部存储目录
- **归档管理** — 归档旧内容、浏览归档、恢复归档到当前文档（自动去重）
- **快捷操作** — 一键复制内容、分享内容、清空文件（自动备份）
- **安全写入** — 全局同步锁防止并发冲突，UTF-8 编码无 BOM

## 条目格式

每条收集的内容使用以下格式：

```
---

## YYYY-MM-DD HH:mm:ss

{原始内容}

---
```

## 技术栈

- **语言**：Kotlin
- **UI**：Jetpack Compose (Material 3)
- **导航**：Navigation Compose
- **架构**：双 Activity + Compose Navigation
- **存储**：双模式 — 默认文件 + SAF (Storage Access Framework)
- **最低 SDK**：API 26 (Android 8.0)
- **目标 SDK**：API 34

## 使用方法

### 首次使用

1. 启动应用，自动创建默认文件
2. 开始收集内容

### 收集内容

**方式一：分享收集**
- 从任意应用（如浏览器、微信等）分享链接或文本
- 选择 "Appendo" 即可自动收集

**方式二：手动输入**
- 在主界面点击「手动输入」按钮
- 输入文字内容后点击「追加」即可保存

### 主要操作

- **查看内容** — 主界面实时显示所有已收集的条目
- **复制内容** — 将全部内容或单条内容复制到剪贴板
- **分享内容** — 将内容分享到其他应用
- **删除条目** — 右滑单条内容删除
- **编辑条目** — 点击条目弹出编辑对话框，修改内容后保存
- **设提醒** — 在条目详情中为该条设本地提醒（预设网格或自定义时间）
- **添加到日历** — 将条目内容预填到系统日历新建事件
- **清空文件** — 长按清空按钮（自动备份后清空）
- **归档** — 将当前内容备份到归档文件
- **归档管理** — 浏览、恢复、删除归档文件
- **自定义目录** — 切换到外部存储位置
- **提醒自检** — 检查通知权限/后台运行，发送测试提醒验证本机能否触发

## 构建项目

### 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Android SDK 34

### 构建步骤

```bash
# 克隆项目
git clone https://github.com/yiyue31/appendo.git
cd appendo

# 构建 APK
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug
```

## 项目结构

```
Appendo/
├── src/main/java/com/yiyue31/android/appendo/
│   ├── MainActivity.kt               # 主入口，Navigation 导航
│   ├── ShareReceiverActivity.kt      # 分享接收入口
│   ├── AppendoApplication.kt         # Application 初始化
│   ├── reminder/                     # 本地提醒（AlarmScheduler/通知/接收器/sidecar）
│   ├── data/
│   │   ├── FileRepository.kt         # 文件存储偏好和 URI 管理
│   │   └── ArchiveRepository.kt      # 归档文件管理
│   ├── ui/
│   │   ├── MainScreen.kt             # 主界面
│   │   ├── EntryListScreen.kt        # 可复用条目列表组件
│   │   ├── ArchiveListScreen.kt      # 归档管理界面
│   │   ├── ArchiveDetailScreen.kt    # 归档详情界面
│   │   ├── ReminderTimePickerDialog.kt # 提醒时间选择（微信式）
│   │   └── ToastUtils.kt             # Toast 兼容工具
│   └── util/
│       ├── MarkdownFileOperations.kt # 文件操作接口
│       ├── MarkdownFileFactory.kt    # 文件操作工厂
│       ├── FileBasedMarkdownFile.kt  # 默认文件存储实现
│       ├── SafMarkdownFile.kt        # SAF 文件存储实现
│       ├── MarkdownFormatter.kt      # Markdown 格式化工具
│       ├── CalendarEntryMapper.kt    # 添加到日历：标题映射（纯函数）
│       ├── CalendarLauncher.kt       # 添加到日历：拉起日历编辑器
│       ├── ReminderMeta.kt           # 提醒元数据
│       ├── ReminderMetaCodec.kt      # sidecar 编解码（纯函数）
│       ├── ReminderLogic.kt          # 提醒纯函数（对账/补发/贪睡）
│       └── ReminderText.kt           # 通知标题/时间标签（纯函数）
├── src/test/java/                    # 单元测试
└── build.gradle.kts                  # 构建配置
```

## 开发规范

本项目遵循 [CLAUDE.md](./CLAUDE.md) 中定义的开发流程和规范：

- 需求文档：[docs/specs.md](./docs/specs.md)
- 设计文档：[docs/design.md](./docs/design.md)
- 项目计划：[docs/plans/PLANS.md](./docs/plans/PLANS.md)

## 测试

```bash
# 运行单元测试
./gradlew testDebugUnitTest
```

## 已知约束与数据安全说明

- **外部编辑器直编 `Appendo.md` 不支持**：应用对内容做零宽空格隔离，防止用户内容中形如时间戳的行被误判为条目边界。直接用其他编辑器编辑文件可能破坏该机制；复制/分享功能已自动剥离这些标记，输出干净文本。
- **SAF（自定义外部目录）模式为"软恢复"**：SAF 无原子重命名能力，写入中途崩溃会从上次备份（.bak）回滚——**最近一次未完成的写入可能丢失，但文件不会损坏**。默认（应用私有目录）模式为真原子写入，无此限制。
- **本地提醒在国产 ROM 上需开自启动**：realme/小米等激进省电会杀后台闹钟；首次到「提醒自检」开电池白名单 + 系统设置允许自启动，否则重启后提醒可能不响。完成设置后可用「发送测试提醒」当场验证（可靠非 100%）。
- **不建议降级安装**：1.0.2 起新条目用毫秒级时间戳，旧版本（1.0.x）读不了。

## 版本历史

| 版本 | 日期 | 变更 |
|------|------|------|
| 1.1.0 | 2026-08-11 | 本地提醒（setAlarmClock + 通知 + 开机重注册 + 提醒自检 + 微信式时间选择） |
| 1.0.3 | 2026-08-10 | 添加到日历（拉起系统日历预填内容） |
| 1.0.2 | 2026-07-21 | 数据完整性增强：毫秒级时间戳、内容隔离、原子写入、归档恢复保留原时间戳、重复内容提示 |
| 1.0.1 | 2026-06-03 | 新增条目编辑功能（点击编辑、updateEntry 接口） |
| 1.0.0 | 2026-04-18 | 初始发布版本 |

## 作者

yiyue31

## 许可证

本项目仅供学习和个人使用。

---

**Appendo** — 极简速记，快速收集。
