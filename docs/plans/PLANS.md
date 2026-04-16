# Link Appending - 项目计划

## 修订记录

| 日期 | 变更人 | 变更摘要 |
|------|--------|---------|
| 2026-04-10 | Yiyue | 初始版本 |

## 里程碑

| 阶段 | 内容 | 依赖 |
|------|------|------|
| M1 | 项目脚手架搭建 | 无 |
| M2 | 核心文件操作层 | M1 |
| M3 | 分享接收功能 | M2 |
| M4 | 主界面 UI | M2 |
| M5 | 完整功能（分享/归档/通知） | M3, M4 |
| M6 | 测试与验收 | M5 |

## 任务拆分

### M1: 项目脚手架

| # | 任务 | 验收标准 |
|---|------|---------|
| 1.1 | 创建 Android 项目（Kotlin + Compose，minSdk 26，targetSdk 34） | 项目可编译运行 |
| 1.2 | 配置依赖（Compose BOM Material 3, Activity Compose） | 构建成功 |
| 1.3 | 建立 `src/` 目录结构：`ui/`, `data/`, `util/` | 目录就位 |

### M2: 核心文件操作层（MarkdownFile）

| # | 任务 | 验收标准 |
|---|------|---------|
| 2.1 | 实现 `MarkdownFile` 类：SAF URI + ContentResolver 封装 | 类可实例化 |
| 2.2 | 实现 `append(content)` — 加 synchronized 锁，MODE_APPEND，降级覆写 | 追加写入正确，并发安全 |
| 2.3 | 实现 `readAll()` | 读取全部内容，UTF-8 无 BOM |
| 2.4 | 实现 `clear()` — 保留文件头 `# Link Collection` | 清空后只剩文件头 |
| 2.5 | 实现 `exists()` | 检测文件存在性和 URI 权限有效性 |
| 2.6 | 实现 `count()` — 正则匹配时间戳标题统计条目数 | 计数准确 |
| 2.7 | URI 权限管理：SharedPreferences 存储 URI，takePersistableUriPermission，releasePersistableUriPermission | 持久化和释放均正确 |

### M3: 分享接收功能（ShareReceiverActivity）

| # | 任务 | 验收标准 |
|---|------|---------|
| 3.1 | 创建 `ShareReceiverActivity`，配置 Intent Filter（ACTION_SEND, text/plain） | 显示在系统分享列表中 |
| 3.2 | 解析 Intent：EXTRA_TEXT 和 EXTRA_STREAM | 正确提取文本和 URI |
| 3.3 | 调用 MarkdownFile.append() 写入，格式：分隔线 + 时间戳标题 + 原始内容 | 文件中条目格式正确 |
| 3.4 | 写入完成后发送 Notification 提示 | Notification 显示成功 |
| 3.5 | 无文件时引导跳转 MainActivity 选择文件 | 流程通畅 |
| 3.6 | adb CLI 可用性验证：`adb shell am start` 触发写入 | 命令行操作成功 |

### M4: 主界面 UI（MainActivity）

| # | 任务 | 验收标准 |
|---|------|---------|
| 4.1 | 创建 MainActivity + Compose 主界面 | 界面渲染正常 |
| 4.2 | 首次启动引导：ActivityResultContracts.CreateDocument 选择/创建文件 | 文件创建成功，URI 持久化 |
| 4.3 | 状态展示：文件路径 + 条目计数 | 信息显示正确 |
| 4.4 | 打开文件按钮：ACTION_VIEW + 文件 URI | 外部应用打开文件 |
| 4.5 | 一键复制：ClipboardManager + ClipData | 内容复制到剪贴板 |
| 4.6 | 一键清理：AlertDialog 确认 + MarkdownFile.clear() | 清空后只剩文件头 |
| 4.7 | 文件丢失恢复：检测 exists()，失效时重新引导选择 | 恢复流程通畅 |

### M5: 完整功能

| # | 任务 | 验收标准 |
|---|------|---------|
| 5.1 | 分享内容按钮：ACTION_SEND + EXTRA_TEXT + createChooser | 分享到其他应用成功 |
| 5.2 | 归档功能：菜单选项 → CreateDocument 创建新文件 → 释放旧 URI → 切换 | 归档后写入新文件，旧文件不受影响 |
| 5.3 | Notification 渠道创建（API 26+ 要求） | 渠道注册成功 |

### M6: 测试与验收

| # | 任务 | 验收标准 |
|---|------|---------|
| 6.1 | MarkdownFile 单元测试：append、readAll、clear、count、并发写入 | 测试全部通过 |
| 6.2 | 端到端测试：从系统分享 → 写入 → 主界面显示 → 计数正确 | 完整流程通畅 |
| 6.3 | 边界测试：空文件、特殊字符内容、文件丢失、并发分享 | 异常情况不崩溃 |
| 6.4 | adb CLI 测试 | 命令行追加成功 |
| 6.5 | 对照需求文档逐项验收 | 全部需求满足 |

## 文件结构

```
src/main/java/com/example/linkappending/
├── MainActivity.kt
├── ShareReceiverActivity.kt
├── ui/
│   └── MainScreen.kt          # Compose 主界面
├── data/
│   └── FileRepository.kt      # URI 持久化（SharedPreferences）
└── util/
    └── MarkdownFile.kt        # 文件读写工具类

src/test/java/com/example/linkappending/
└── util/
    └── MarkdownFileTest.kt    # 单元测试
```
