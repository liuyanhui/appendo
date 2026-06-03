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
