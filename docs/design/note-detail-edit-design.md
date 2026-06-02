# 笔记条目详情查看与编辑功能 — 设计文档

> 日期: 2026-06-02
> 状态: 待实现

---

## 1. 功能概述

在当前笔记列表页（MainScreen）中，用户点击条目卡片后弹出对话框，展示完整内容并支持编辑。编辑后手动保存，时间戳保持不变。

---

## 2. 需求决策记录

| 决策点 | 结论 | 理由 |
|--------|------|------|
| 编辑范围 | 仅修改 content，时间戳不变 | 用户只需修改文字内容，保留原始记录时间 |
| 展示方式 | 弹窗对话框（Dialog） | 与现有 UI 模式一致，实现轻量，不离开列表上下文 |
| 后端实现 | 新增 `updateEntry` 接口方法 | 语义清晰，CRUD 齐全，两步操作封装为原子操作 |
| 适用页面 | 仅主列表页可编辑 | 归档为历史快照，不应修改；主列表是活跃数据 |
| 保存方式 | 手动保存（点击"保存"按钮） | 与现有"手动输入"对话框交互一致，用户可控 |

---

## 3. 数据层设计

### 3.1 接口变更

**文件**: `util/MarkdownFileOperations.kt`

新增方法：

```kotlin
/**
 * Update the content of an existing entry identified by its timestamp.
 * The timestamp remains unchanged; only the content is replaced.
 *
 * Thread-safe: uses FileOperationLock for atomicity.
 *
 * @param timestamp The timestamp identifying the entry (format: "YYYY-MM-DD HH:mm:ss")
 * @param newContent The new content to replace the old content
 * @return true if successful, false if entry not found or write failed
 */
fun updateEntry(timestamp: String, newContent: String): Boolean
```

### 3.2 实现逻辑

`FileBasedMarkdownFile` 和 `SafMarkdownFile` 均需实现，逻辑相同：

```
synchronized(FileOperationLock) {
    1. 读取全文件内容 (readAll)
    2. 定位目标条目（与 deleteEntry 保持一致的边界定位方式）：
       - 按行遍历，定位 "## {timestamp}" 行
       - 向后读取到下一个 "## YYYY-MM-DD HH:mm:ss" 时间戳行或文件末尾
       - 该范围即为该条目的完整区域（含时间戳行和内容）
       - 注意：不使用 "---" 作为边界，因为用户内容可能包含 "---"
    3. 替换内容区域为 newContent（保留原时间戳行）
    4. 调用 writeAll 写回全文件
    5. 返回 true（成功）或 false（时间戳不存在）
}
```

**关键约束**：
- 必须在 `FileOperationLock` 内执行，防止与 ShareReceiverActivity 的并发写入冲突
- 条目格式需与 `MarkdownFormatter.formatEntry()` 保持一致
- 未找到目标时间戳时返回 false，不修改文件

---

## 4. UI 层设计

### 4.1 EntryListScreen 变更

**文件**: `ui/EntryListScreen.kt`

变更内容：
- 函数签名新增参数：`onEntryClick: (LinkEntry) -> Unit = {}`
- `readOnly = false` 时：`EntryCard` 的 `onClick` 回调改为调用 `onEntryClick(entry)`
- `readOnly = true` 时（归档详情页）：`onClick` 保持空操作，不触发 `onEntryClick`
- 传递完整的 `LinkEntry` 对象（包含 timestamp 和 content）

### 4.2 MainScreen 变更

**文件**: `ui/MainScreen.kt`

新增状态变量：

```kotlin
var showDetailDialog by remember { mutableStateOf(false) }
var selectedEntry by remember { mutableStateOf<LinkEntry?>(null) }
var editContent by remember { mutableStateOf("") }
```

EntryListScreen 调用处新增回调：

```kotlin
onEntryClick = { entry ->
    selectedEntry = entry
    editContent = entry.content
    showDetailDialog = true
}
```

### 4.3 详情/编辑对话框

对话框结构：

```
AlertDialog
├── 标题: "编辑条目"
├── 内容区域（使用 verticalScroll 包裹，防止长文本超出屏幕）:
│   ├── Text: 显示时间戳（不可编辑，LabelSmall 样式，Primary 颜色）
│   └── OutlinedTextField:
│       ├── value = editContent
│       ├── onValueChange = { editContent = it }
│       ├── minLines = 5
│       ├── maxLines = 10
│       └── placeholder = "无内容"
├── 确认按钮: "保存" → 调用 updateEntry + 刷新列表
└── 取消按钮: "取消" → 关闭对话框
```

保存逻辑：

```
1. 检查 editContent 是否为空
   → 空内容：showToast("内容不能为空")，不关闭对话框
2. 检查 editContent 是否与原始 content 相同（无变化则不操作）
   → 无变化：直接关闭对话框
3. 调用 mdFile.updateEntry(selectedEntry.timestamp, editContent)
4. 成功 → setFileLastModified + refreshEntryCount + showToast("已保存")
5. 失败 → showToast("保存失败")
6. 关闭对话框
```

---

## 5. 不修改的部分

| 模块 | 说明 |
|------|------|
| `data/FileRepository.kt` | 无需修改，不涉及存储偏好变更 |
| `data/ArchiveRepository.kt` | 无需修改，归档管理不受影响 |
| `data/ArchiveFile.kt` | 无需修改，数据模型不变 |
| `ui/ArchiveDetailScreen.kt` | 保持只读，不添加点击事件（readOnly=true 不触发 onEntryClick） |
| `ui/ArchiveListScreen.kt` | 不涉及，归档列表不变 |
| `ShareReceiverActivity.kt` | 不涉及，不需要修改 |
| `ui/AppColors.kt` | 不涉及，无新颜色需求 |
| `LinkEntry` 数据类 | 无需修改，无需新增字段 |
| `util/MarkdownFormatter.kt` | 无需修改，复用现有格式化逻辑 |
| `MainActivity.kt` | 无需修改，不涉及路由变更 |

---

## 6. 改动文件清单

| 文件 | 改动类型 | 改动描述 |
|------|---------|---------|
| `util/MarkdownFileOperations.kt` | 修改 | 接口新增 `updateEntry(timestamp, newContent)` |
| `util/FileBasedMarkdownFile.kt` | 修改 | 实现 `updateEntry` |
| `util/SafMarkdownFile.kt` | 修改 | 实现 `updateEntry` |
| `ui/EntryListScreen.kt` | 修改 | 新增 `onEntryClick` 回调参数，卡片添加点击事件 |
| `ui/MainScreen.kt` | 修改 | 新增详情/编辑对话框，传递 `onEntryClick` |
| `test/util/MarkdownFileUpdateTest.kt` | 新增 | `updateEntry` 功能单元测试 |

---

## 7. 测试要点

- `util/MarkdownFileDeleteTest.kt` 同级目录新增 `MarkdownFileUpdateTest.kt`
- 测试用例：
  - 正常更新单行内容
  - 正常更新多行内容（验证多行内容更新后解析仍然正确）
  - 时间戳不存在时返回 false
  - 更新后其他条目不受影响
  - 并发安全性（与 deleteEntry 交叉执行）
  - 内容为空字符串时的处理（接口层行为：允许空内容覆盖）
  - 内容包含特殊 Markdown 字符时的处理（如 `---`、`## ` 等）
  - SAF 模式下的更新
