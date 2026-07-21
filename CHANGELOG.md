# 1.0.2 - 2026-07-21

- 数据完整性增强：
  - 条目唯一标识改用毫秒级时间戳（java.time + 单调防碰撞）
  - 内容隔离：对恰好形如时间戳标记的内容行加零宽空格保护，防止被误判为条目边界；复制/分享自动剥离
  - 写入原子性：默认模式 temp+fsync+rename；SAF 模式 .pending+.bak 软恢复（已真机验证）
  - 归档恢复保留原时间戳（去重 key 生效），按时间戳排序
  - 重复内容非阻塞提示（5s 节流）；崩溃恢复透明提示
- UI 时间戳显示剥离毫秒（仅显到秒）
- 删除废弃的 MarkdownFile 类
- ⚠️ 数据格式变更：新条目用毫秒时间戳 + 内容零宽空格隔离标记；旧版本（1.0.x）读不了新格式，不建议降级安装

# 1.0.1 - 2026-06-03

- 新增条目编辑功能：点击条目弹出编辑对话框，修改内容后手动保存
- 新增 `updateEntry(timestamp, newContent)` 接口到 MarkdownFileOperations
- 在 FileBasedMarkdownFile 和 SafMarkdownFile 中实现 updateEntry
- 新增 MarkdownFileUpdateTest 单元测试（9 个用例）
- 修复 TestFileBasedMarkdownFile 缺少 updateEntry 实现的编译错误

# 1.0.0 - 2026-04-20

- 初始化 Appendo 快速笔记应用
- 支持创建、编辑、删除笔记条目
- 实现归档管理功能
- 添加 Markdown 编辑和预览支持
- 提供自适应图标和 Material 3 设计界面
