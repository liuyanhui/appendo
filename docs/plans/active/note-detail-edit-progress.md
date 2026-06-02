# 笔记详情编辑功能 — 开发进度报告

> 日期: 2026-06-03
> 状态: 代码完成，待验证

---

## 一、功能概述

在笔记列表页（MainScreen）点击条目卡片后弹出对话框，展示完整内容并支持编辑。编辑后手动保存，时间戳保持不变。

## 二、已完成的工作

### 2.1 设计文档
- ✅ 设计文档已评审通过：`docs/design/note-detail-edit-design.md`
- ✅ Evaluator 评审 17 项全部通过，3 个低严重度问题已修复/记录

### 2.2 代码实现（5 个文件修改）

| 文件 | 变更 | 说明 |
|------|------|------|
| `util/MarkdownFileOperations.kt` | 接口新增 | `updateEntry(timestamp, newContent): Boolean` |
| `util/FileBasedMarkdownFile.kt` | 新增实现 | `updateEntry`：读-改-写，FileOperationLock 同步 |
| `util/SafMarkdownFile.kt` | 新增实现 | `updateEntry`：同上逻辑，使用 SAF API 写入 |
| `ui/EntryListScreen.kt` | 新增回调 | `onEntryClick: (LinkEntry) -> Unit`，readOnly 时不触发 |
| `ui/MainScreen.kt` | 新增对话框 | 编辑对话框 + 状态管理 + 保存逻辑 |

### 2.3 测试文件（1 个新增）

- `test/util/MarkdownFileUpdateTest.kt` — 9 个测试用例（单行/多行/不存在/首条/末条/空内容/特殊字符等）

### 2.4 目录文档（10 个新增）

5 个目录各新增 CLAUDE.md（内容）+ README.md（指向 CLAUDE.md）：
- `appendo/`（主包）、`data/`、`ui/`、`util/`、`test/`

## 三、待完成的工作（换电脑后继续）

### 3.1 必须完成

- [ ] **运行单元测试**：`./gradlew test`
- [ ] **真机/模拟器验证**（关键交互点）：
  - 点击条目 → 弹出编辑对话框
  - 修改内容 → 保存 → Toast "已保存" → 列表刷新
  - 不修改内容 → 保存 → 直接关闭
  - 清空内容 → 保存 → Toast "内容不能为空" + 对话框不关闭
  - 取消 → 对话框关闭，内容不变
  - 归档详情页点击条目 → 无反应（readOnly）
- [ ] **测试通过后提交代码**（如果测试失败需修复后重新提交）

### 3.2 文档更新评估（测试通过后执行）

根据 CLAUDE.md 要求，评估是否需要更新：
- [ ] `docs/specs.md` — 是否追加此功能的需求记录
- [ ] `docs/design.md` — 是否同步更新主设计文档
- [ ] `README.md` — 是否在功能列表中添加
- [ ] `CHANGELOG.md` — 是否记录版本变更

### 3.3 技术债务（低优先级，后续处理）

记录在 `docs/plans/debt-tracker.md`：
- TD-001: updateEntry/deleteEntry 条目定位逻辑重复（三处相同代码）
- TD-002: updateEntry 后条目间 `---` 分隔符丢失（解析不受影响）

## 四、设计决策记录

| 决策点 | 结论 | 理由 |
|--------|------|------|
| 编辑范围 | 仅修改 content | 保留原始记录时间 |
| 展示方式 | 弹窗对话框 | 与现有 UI 一致，不离开列表上下文 |
| 后端实现 | 新增 `updateEntry` 接口 | CRUD 齐全，原子操作 |
| 适用页面 | 仅主列表页 | 归档为历史快照 |
| 保存方式 | 手动保存 | 与"手动输入"对话框一致 |
| 条目边界 | 下一 `## timestamp` 行 | 与 deleteEntry 一致，避免内容中 `---` 干扰 |
| readOnly 行为 | 不触发点击事件 | 归档详情页保持只读 |

## 五、Git 提交记录

本次开发分批提交（最小粒度），commit 信息如下：

1. `docs: add directory-level documentation with CLAUDE.md and README.md`
2. `docs: add note detail/edit feature design document`
3. `feat: add updateEntry method to MarkdownFileOperations interface`
4. `feat: implement updateEntry in FileBasedMarkdownFile and SafMarkdownFile`
5. `feat: add entry click and edit dialog to note list`
6. `test: add MarkdownFileUpdateTest for updateEntry`
7. `docs: add tech debt tracker and execution plan`

## 六、关联文件索引

| 文件 | 说明 |
|------|------|
| `docs/design/note-detail-edit-design.md` | 功能设计文档 |
| `docs/directory-documentation-analysis.md` | 目录文档分析 |
| `docs/plans/active/note-detail-edit-plan.md` | 执行计划（含任务状态） |
| `docs/plans/debt-tracker.md` | 技术债务追踪 |
