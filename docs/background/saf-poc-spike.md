# SAF PoC Spike（T-009）

> 状态：**待真机/模拟器执行**。本环境无设备，无法运行。代码已按"防御性"实现（不依赖 PoC 结果），PoC 用于验证假设、决定云盘场景是否需单独降级。

## 目的

验证 SAF provider 的关键行为，确认 v1.1 SAF 软恢复（.pending/.bak）设计在真实 provider 上是否成立。

## 待验证问题

1. **`ParcelFileDescriptor.getFileDescriptor().sync()` 行为**：
   - 本地外部存储：是否正常 sync（无异常）？
   - 云盘 provider（Google Drive / OneDrive 等）：是否抛 `SyncFailedException`、被静默忽略、还是无实际效果？
2. **`openOutputStream(uri, "wt")` 截断语义**：是否就地截断+写（非原子替换）？崩溃窗口多大？
3. **`openFileDescriptor(uri, "wt")` 可用性**：各 provider 是否支持（用于 fsync）？

## 测试方法（真机）

- 写一个 instrumented test（androidTest）或手动操作：对**本地外部存储** + **至少一种云盘 provider**，分别：
  1. `openOutputStream("wt")` 写入已知内容，再读回，确认截断+写行为。
  2. `openFileDescriptor("wt")` 写入 + `fileDescriptor.sync()`，观察是否抛异常。
- 记录每个 provider 的结果于此文件。

## 决策标准

- **云盘 sync() 不可靠/抛异常**：当前代码已防御（writeMain 对 sync `try/catch` best-effort，不依赖；.bak 兜底损坏回滚）。**无需改动**。
- **openFileDescriptor 普遍不支持**：当前代码已回退 `openOutputStream`。**无需改动**。
- 若发现某 provider 的 `openOutputStream("wt")` 本身不保证截断（覆写残留）：需对该 provider 额外处理（先 truncate 再写，或文档为不支持）。

## 当前代码的防御性实现（不依赖 PoC 结果）

见 `util/SafMarkdownFile.kt`：
- `writeMain`：优先 `openFileDescriptor`+`sync`，失败/不支持回退 `openOutputStream`（均 try/catch）。
- `safAtomicWrite`：.pending + .bak 保证崩溃可回滚（不依赖 fsync 持久化）。
- `ensureConsistent`：纯函数 `EntryParser.decideRecovery` 决定恢复动作。

## 结论

（真机执行后填写）
