# 笔记条目详情查看与编辑功能 — 执行计划

> 日期: 2026-06-02
> 关联设计文档: `docs/design/note-detail-edit-design.md`
> 状态: 完成编码，待测试验证

---

## 任务总览

| 任务ID | 描述 | 状态 | 当前轮次 |
|--------|------|------|---------|
| T-001 | 在 MarkdownFileOperations 接口中新增 updateEntry 方法 | passed | 1 |
| T-002 | 在 FileBasedMarkdownFile 中实现 updateEntry | passed | 1 |
| T-003 | 在 SafMarkdownFile 中实现 updateEntry | passed | 1 |
| T-004 | 修改 EntryListScreen 新增 onEntryClick 回调 | passed | 1 |
| T-005 | 在 MainScreen 中新增详情/编辑对话框 | passed | 1 |
| T-006 | 新增 MarkdownFileUpdateTest 单元测试 | passed | 1 |
| T-007 | 运行所有测试验证功能完整性 | blocked | 1 |

---

## 任务详细定义

### T-001: 在 MarkdownFileOperations 接口中新增 updateEntry 方法

```
任务ID: T-001
描述: 在 MarkdownFileOperations 接口中新增 updateEntry(timestamp: String, newContent: String): Boolean 方法声明
约束:
  - 文件: /home/claude/project/appendo/Appendo/src/main/java/com/yiyue31/android/appendo/util/MarkdownFileOperations.kt
  - 仅添加接口方法声明和 KDoc 注释，不修改其他方法
  - 遵循现有的接口方法文档风格（KDoc 包含 @param、@return、线程安全说明）
任务产出: MarkdownFileOperations.kt 中新增 updateEntry 方法声明
评估标准:
  - [ ] 标准1: 接口中新增了 `fun updateEntry(timestamp: String, newContent: String): Boolean` 方法
  - [ ] 标准2: KDoc 注释包含 @param timestamp、@param newContent、@return 说明
  - [ ] 标准3: KDoc 注释说明线程安全性（使用 FileOperationLock）
  - [ ] 标准4: 未修改接口中已有的任何方法声明
评估方法: 阅读源码验证接口变更
当前轮次: 0
最大轮次: 3
状态: pending
```

### T-002: 在 FileBasedMarkdownFile 中实现 updateEntry

```
任务ID: T-002
描述: 在 FileBasedMarkdownFile 中实现 updateEntry 方法
约束:
  - 文件: /home/claude/project/appendo/Appendo/src/main/java/com/yiyue31/android/appendo/util/FileBasedMarkdownFile.kt
  - 必须在 synchronized(FileOperationLock) 内执行
  - 条目边界定位逻辑必须与 deleteEntry 保持一致：按行遍历，定位 "## {timestamp}" 行，向后读取到下一个 "## YYYY-MM-DD HH:mm:ss" 时间戳行或文件末尾
  - 不使用 "---" 作为边界（因为用户内容可能包含 "---"）
  - 替换内容时保留原时间戳行（"## {timestamp}"），仅替换时间戳行之后到下一个条目之前的内容
  - 未找到目标时间戳时返回 false，不修改文件
  - 调用 writeAll 写回全文件
  - 添加 override 修饰符
任务产出: FileBasedMarkdownFile.kt 中新增 updateEntry 方法实现
评估标准:
  - [ ] 标准1: 方法签名正确 override fun updateEntry(timestamp: String, newContent: String): Boolean
  - [ ] 标准2: 使用 synchronized(FileOperationLock) 包裹整个操作
  - [ ] 标准3: 条目边界定位使用与 deleteEntry 相同的逻辑（匹配 "## YYYY-MM-DD HH:mm:ss" 正则作为边界）
  - [ ] 标准4: 保留原时间戳行，仅替换内容部分
  - [ ] 标准5: 未找到时间戳时返回 false 且不修改文件
  - [ ] 标准6: 成功时调用 writeAll 写回并返回 true
  - [ ] 标准7: 未修改已有的其他方法
评估方法: 阅读源码验证实现逻辑
当前轮次: 0
最大轮次: 3
状态: pending
```

### T-003: 在 SafMarkdownFile 中实现 updateEntry

```
任务ID: T-003
描述: 在 SafMarkdownFile 中实现 updateEntry 方法
约束:
  - 文件: /home/claude/project/appendo/Appendo/src/main/java/com/yiyue31/android/appendo/util/SafMarkdownFile.kt
  - 实现逻辑与 T-002 (FileBasedMarkdownFile) 保持一致
  - 必须在 synchronized(FileOperationLock) 内执行
  - 条目边界定位逻辑与 deleteEntry 保持一致
  - 不使用 "---" 作为边界
  - 使用 SAF API (contentResolver.openOutputStream) 写回文件
  - 添加 override 修饰符
  - 注意：SafMarkdownFile 的 writeAll 使用 "wt" 模式打开输出流
任务产出: SafMarkdownFile.kt 中新增 updateEntry 方法实现
评估标准:
  - [ ] 标准1: 方法签名正确 override fun updateEntry(timestamp: String, newContent: String): Boolean
  - [ ] 标准2: 使用 synchronized(FileOperationLock) 包裹整个操作
  - [ ] 标准3: 条目边界定位逻辑与 FileBasedMarkdownFile.updateEntry 一致
  - [ ] 标准4: 保留原时间戳行，仅替换内容部分
  - [ ] 标准5: 未找到时间戳时返回 false 且不修改文件
  - [ ] 标准6: 使用 SAF API (contentResolver.openOutputStream(uri, "wt")) 写回
  - [ ] 标准7: 未修改已有的其他方法
评估方法: 阅读源码验证实现逻辑
当前轮次: 0
最大轮次: 3
状态: pending
```

### T-004: 修改 EntryListScreen 新增 onEntryClick 回调

```
任务ID: T-004
描述: 在 EntryListScreen 中新增 onEntryClick 回调参数，并在非只读模式下为卡片添加点击事件
约束:
  - 文件: /home/claude/project/appendo/Appendo/src/main/java/com/yiyue31/android/appendo/ui/EntryListScreen.kt
  - EntryListScreen 函数签名新增参数: onEntryClick: (LinkEntry) -> Unit = {}
  - readOnly=false 时: EntryCard 的 onClick 调用 onEntryClick(entry)
  - readOnly=true 时: onClick 保持空操作，不触发 onEntryClick
  - 传递完整的 LinkEntry 对象（包含 timestamp 和 content）
  - 需要将 onEntryClick 参数从 EntryListScreen 传递到 EntryCard
  - EntryCard 也需要新增 onEntryClick 回调参数
任务产出: EntryListScreen.kt 修改完成
评估标准:
  - [ ] 标准1: EntryListScreen 函数签名新增 onEntryClick: (LinkEntry) -> Unit = {} 参数
  - [ ] 标准2: EntryCard 新增 onEntryClick 回调参数
  - [ ] 标准3: readOnly=false 时，卡片 onClick 调用 onEntryClick(entry)
  - [ ] 标准4: readOnly=true 时，卡片 onClick 不调用 onEntryClick（保持空操作）
  - [ ] 标准5: 传递完整的 LinkEntry 对象
  - [ ] 标准6: 未修改已有的长按复制、滑动删除等功能
  - [ ] 标准7: 未引入硬编码字面量（遵循 CODINGRULES.md）
评估方法: 阅读源码验证变更
当前轮次: 0
最大轮次: 3
状态: pending
```

### T-005: 在 MainScreen 中新增详情/编辑对话框

```
任务ID: T-005
描述: 在 MainScreen 中新增状态变量和详情/编辑对话框，传递 onEntryClick 回调给 EntryListScreen
约束:
  - 文件: /home/claude/project/appendo/Appendo/src/main/java/com/yiyue31/android/appendo/ui/MainScreen.kt
  - 新增状态变量: showDetailDialog, selectedEntry (LinkEntry?), editContent
  - EntryListScreen 调用处新增 onEntryClick 回调
  - 新增 AlertDialog 详情/编辑对话框：
    - 标题: "编辑条目"
    - 内容区域使用 verticalScroll 修饰符包裹（防止长文本超出屏幕）
    - 显示时间戳（不可编辑，LabelSmall 样式，Primary 颜色）
    - OutlinedTextField: value=editContent, minLines=5, maxLines=10, placeholder="无内容"
    - 确认按钮 "保存": 空内容显示 Toast "内容不能为空" 且不关闭对话框
    - 取消按钮 "取消": 关闭对话框
  - 保存逻辑：
    1. editContent 为空 → showToast("内容不能为空")，不关闭对话框
    2. editContent 与原始 content 相同 → 直接关闭对话框
    3. 调用 mdFile.updateEntry → 成功: setFileLastModified + refreshEntryCount + showToast("已保存")
    4. 失败: showToast("保存失败")
    5. 关闭对话框
  - 所有字符串字面量硬编码可接受（与现有 MainScreen 中其他对话框风格一致）
  - 需要添加必要的 import: androidx.compose.foundation.rememberScrollState, androidx.compose.foundation.verticalScroll
任务产出: MainScreen.kt 修改完成
评估标准:
  - [ ] 标准1: 新增 showDetailDialog, selectedEntry, editContent 三个状态变量
  - [ ] 标准2: EntryListScreen 调用处新增 onEntryClick 回调
  - [ ] 标准3: 对话框标题为 "编辑条目"
  - [ ] 标准4: 内容区域使用 verticalScroll 包裹
  - [ ] 标准5: 时间戳显示为不可编辑，使用 LabelSmall 样式和 Primary 颜色
  - [ ] 标准6: OutlinedTextField 参数正确（value=editContent, minLines=5, maxLines=10, placeholder="无内容"）
  - [ ] 标准7: 空内容保存时显示 Toast "内容不能为空" 且不关闭对话框
  - [ ] 标准8: 无变化时直接关闭对话框，不调用 updateEntry
  - [ ] 标准9: 保存成功后调用 setFileLastModified + refreshEntryCount + showToast("已保存")
  - [ ] 标准10: 保存失败显示 Toast "保存失败"
  - [ ] 标准11: 保存成功或失败后关闭对话框
  - [ ] 标准12: 未修改已有的其他对话框和功能
评估方法: 阅读源码验证变更
当前轮次: 0
最大轮次: 5
状态: pending
```

### T-006: 新增 MarkdownFileUpdateTest 单元测试

```
任务ID: T-006
描述: 新增 MarkdownFileUpdateTest 单元测试文件，覆盖 updateEntry 功能的各种场景
约束:
  - 文件: /home/claude/project/appendo/Appendo/src/test/java/com/yiyue31/android/appendo/util/MarkdownFileUpdateTest.kt
  - 遵循 MarkdownFileDeleteTest 的测试模式（使用 TestFileBasedMarkdownFile）
  - TestFileBasedMarkdownFile 需要新增 updateEntry 实现（与 FileBasedMarkdownFile 逻辑一致）
  - 测试用例覆盖设计文档第 7 节的测试要点：
    1. 正常更新单行内容
    2. 正常更新多行内容（验证多行内容更新后解析仍然正确）
    3. 时间戳不存在时返回 false
    4. 更新后其他条目不受影响
    5. 内容为空字符串时的处理（接口层允许空内容覆盖）
    6. 内容包含特殊 Markdown 字符时的处理（如 "---"、"## " 等）
  - 不需要测试 SAF 模式（SAF 需要 Android Context，不适合纯 JVM 单元测试）
  - 不需要测试并发安全性（与 deleteEntry 使用相同的锁机制，属于集成测试范畴）
任务产出: MarkdownFileUpdateTest.kt 测试文件
评估标准:
  - [ ] 标准1: 文件位于正确的测试目录路径
  - [ ] 标准2: TestFileBasedMarkdownFile 新增 updateEntry 实现
  - [ ] 标准3: 包含正常更新单行内容的测试
  - [ ] 标准4: 包含正常更新多行内容的测试
  - [ ] 标准5: 包含时间戳不存在时返回 false 的测试
  - [ ] 标准6: 包含更新后其他条目不受影响的测试
  - [ ] 标准7: 包含内容包含特殊 Markdown 字符（"---"、"## "）的测试
  - [ ] 标准8: 测试代码遵循 Arrange-Act-Assert 模式
评估方法: 阅读测试源码验证覆盖范围
当前轮次: 0
最大轮次: 3
状态: pending
```

### T-007: 运行所有测试验证功能完整性

```
任务ID: T-007
描述: 运行项目所有单元测试，确认新增功能和已有功能均正常
约束:
  - 在项目根目录执行 ./gradlew test
  - 所有测试必须通过（包括已有的测试和新增的测试）
  - 如有测试失败，分析原因并记录
任务产出: 测试执行报告（全部通过 / 失败列表）
评估标准:
  - [ ] 标准1: ./gradlew test 执行成功
  - [ ] 标准2: 所有测试用例通过（0 failures）
  - [ ] 标准3: 新增的 MarkdownFileUpdateTest 中所有测试通过
  - [ ] 标准4: 已有的 MarkdownFileDeleteTest 中所有测试通过
  - [ ] 标准5: 已有的 MarkdownParserTest 中所有测试通过
评估方法: 查看 gradle 测试输出
当前轮次: 0
最大轮次: 3
状态: pending
```

---

## 任务依赖关系

```
T-001 (接口声明)
  ├── T-002 (FileBased 实现) ── 依赖 T-001
  ├── T-003 (SAF 实现) ── 依赖 T-001
  │     └── T-006 (单元测试) ── 依赖 T-002, T-003（接口）
  ├── T-004 (EntryListScreen) ── 无数据层依赖
  │     └── T-005 (MainScreen 对话框) ── 依赖 T-004, T-001
  │           └── T-007 (运行测试) ── 依赖 T-006
```

执行顺序: T-001 → T-002 → T-003 → T-004 → T-005 → T-006 → T-007

---

## 执行日志

### T-001: 接口新增 updateEntry — PASS (Round 1)
- Planner → Generator: 在 MarkdownFileOperations 接口中添加 updateEntry 方法声明及 KDoc
- Generator 产出: 在 deleteEntry 和 writeAll 之间添加了 `fun updateEntry(timestamp: String, newContent: String): Boolean`
- Evaluator 评估: 4/4 标准全部通过
- Planner 决策: 推进到 T-002

### T-002: FileBasedMarkdownFile 实现 updateEntry — PASS (Round 1)
- Planner → Generator: 在 FileBasedMarkdownFile 中实现 updateEntry，使用 synchronized(FileOperationLock)，边界逻辑与 deleteEntry 一致
- Generator 产出: 实现 updateEntry 方法，使用正则匹配 `^## \d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$` 作为边界
- Evaluator 评估: 7/7 标准全部通过
- Planner 决策: 推进到 T-003

### T-003: SafMarkdownFile 实现 updateEntry — PASS (Round 1)
- Planner → Generator: 在 SafMarkdownFile 中实现 updateEntry，逻辑与 FileBasedMarkdownFile 一致，使用 SAF API 写入
- Generator 产出: 实现与 FileBasedMarkdownFile 相同逻辑，使用 contentResolver.openOutputStream(uri, "wt") 写入
- Evaluator 评估: 7/7 标准全部通过
- Planner 决策: 推进到 T-004

### T-004: EntryListScreen 新增 onEntryClick — PASS (Round 1)
- Planner → Generator: 在 EntryListScreen 和 EntryCard 中新增 onEntryClick 回调，readOnly=true 时不触发
- Generator 产出: EntryListScreen 新增 onEntryClick 参数，EntryCard 新增 onClick 参数，readOnly=false 时调用 onClick()
- Evaluator 评估: 7/7 标准全部通过
- Planner 决策: 推进到 T-005

### T-005: MainScreen 详情/编辑对话框 — PASS (Round 1)
- Planner → Generator: 在 MainScreen 中新增状态变量、onEntryClick 回调和编辑对话框
- Generator 产出: 新增 showDetailDialog/selectedEntry/editContent 状态、onEntryClick 回调、AlertDialog（verticalScroll、空内容校验、保存逻辑）
- Evaluator 评估: 12/12 标准全部通过
- Planner 决策: 推进到 T-006

### T-006: MarkdownFileUpdateTest 单元测试 — PASS (Round 1)
- Planner → Generator: 创建 MarkdownFileUpdateTest.kt，覆盖单行/多行更新、不存在时间戳、特殊字符等场景
- Generator 产出: 9 个测试用例 + TestUpdateFileBasedMarkdownFile 实现（含 updateEntry）
- Evaluator 评估: 8/8 标准全部通过
- Planner 决策: 推进到 T-007

### T-007: 运行测试 — BLOCKED
- 原因: 当前环境未安装 Android SDK（ANDROID_HOME 未设置，local.properties 不存在），无法执行 `./gradlew test`
- 状态: 需要用户在本地开发环境执行测试验证
- 建议: 用户在本地运行 `./gradlew test` 确认所有测试通过
