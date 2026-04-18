# Appendo

一个极简的 Android 速记应用，通过系统分享功能自动收集内容，或手动输入文字，将所有内容追加保存到 Markdown 文件中。

## 功能特性

- **分享收集** — 从任意应用分享链接或文本，自动追加保存
- **手动输入** — 在应用内直接输入文字内容
- **内容预览** — 实时查看已收集的所有条目
- **格式保留** — 保持原始内容格式（换行、缩进、符号等），不做任何处理
- **文件管理** — 用户自选保存位置，支持更换路径和归档
- **快捷操作** — 一键复制内容、分享内容、清空文件
- **条目统计** — 实时显示已收集条目数量
- **安全写入** — 使用同步锁防止并发冲突，UTF-8 编码无 BOM

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
- **架构**：极简双 Activity 架构
- **存储**：SAF (Storage Access Framework)
- **最低 SDK**：API 26 (Android 8.0)
- **目标 SDK**：API 34

## 使用方法

### 首次使用

1. 启动应用，选择或创建一个 Markdown 文件
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
- **复制内容** — 将全部内容复制到剪贴板
- **分享内容** — 将内容分享到其他应用
- **清空文件** — 清空所有条目，保留文件头
- **归档** — 创建新文件替换当前文件，旧文件保留在原位置

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
│   ├── MainActivity.kt           # 主界面
│   ├── ShareReceiverActivity.kt  # 分享接收入口
│   ├── util/
│   │   └── MarkdownFile.kt       # Markdown 文件操作
│   └── data/
│       └── FileRepository.kt     # 文件 URI 管理
├── src/test/java/                # 单元测试
└── build.gradle.kts              # 构建配置
```

## 开发规范

本项目遵循 [CLAUDE.md](./CLAUDE.md) 中定义的开发流程和规范：

- 需求文档：[docs/specs.md](./docs/specs.md)
- 设计文档：[docs/design.md](./docs/design.md)
- 项目计划：[docs/plans/PLANS.md](./docs/plans/PLANS.md)

## 测试

```bash
# 运行单元测试
./gradlew test

# 运行测试并生成覆盖率报告
./gradlew testDebugUnitTest
```

## 版本历史

| 版本 | 日期 | 变更 |
|------|------|------|
| 1.0.0 | 2026-04-16 | 初始版本 |

## 作者

yiyue31

## 许可证

本项目仅供学习和个人使用。

---

**Appendo** — 极简速记，快速收集。
