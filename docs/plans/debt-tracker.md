# 技术债务追踪

> 记录项目中已知的技术债务，包括来源、影响和计划处理方式。

---

## TD-001: updateEntry/deleteEntry 条目定位逻辑重复

- **来源**: 笔记详情编辑功能（2026-06-03）
- **描述**: `FileBasedMarkdownFile`、`SafMarkdownFile` 以及测试辅助类中的 `updateEntry` 和 `deleteEntry` 共享约 35 行完全相同的条目定位算法（逐行扫描，时间戳正则匹配，以下一个 `## timestamp` 为边界）。
- **影响**: 三处代码需要同步维护，新增存储后端时需复制相同逻辑
- **建议修复**: 将条目定位逻辑提取到 `MarkdownFormatter` 或新建辅助类中，作为共享方法供所有实现调用
- **优先级**: 低（功能正确，不影响用户体验）

## TD-002: updateEntry 后条目间 `---` 分隔符丢失

- **来源**: 笔记详情编辑功能（2026-06-03）
- **描述**: 更新条目后，被更新条目与下一个条目之间的 `---` 分隔符会丢失。虽然 `parseMarkdownEntries` 跳过 `---` 行，功能不受影响，但文件格式与 `MarkdownFormatter.formatEntry()` 生成的原始格式略有偏差。
- **影响**: 多次编辑后文件格式与原始格式不一致，但解析完全正确
- **建议修复**: 在内容重建时保留原有的 `---` 分隔符结构
- **优先级**: 低（功能正确，仅外观差异）

---

## v1.1 数据完整性增强 — 遗留与更新（2026-07-21）

**已解决**：
- ✅ **TD-001**：updateEntry/deleteEntry 条目定位逻辑重复 → v1.1 由 `EntryParser`（findEntryBounds / buildDeleted / buildUpdated）收敛，4 份重复消除。

**需复核**：
- **TD-002**：updateEntry 后 `---` 分隔符——v1.1 改用 `EntryParser.buildUpdatedLines` 重建，行为可能与旧实现不同，需复核（低优先）。

**新增技术债**：
- **TD-003 Robolectric 不可用**：本环境 SSL 握手失败，无法下载 android-all jar。FileBased 测试改纯 JVM + `mock<Context>()`；SAF/UI 测试须 instrumented（真机）。影响：SAF 路径无 JVM 单测，依赖真机验证。
- **TD-004 CRLF/LF 混合**：用户既有数据为 CRLF、v1.1 新写入为 LF。delete/update 的 read-modify-write 会逐步规范化为 LF（`String.lines()` 兼容，无功能影响）；若要一次性统一可在 Phase B 处理。
- **TD-005 SAF PoC 待填**：`docs/background/saf-poc-spike.md` 的 provider `fd.sync()` 行为结论待真机填写（代码已防御性实现，不依赖结论）。
- **TD-006 SAF instrumented 测试待补**：需 FileProvider 造 `content://` URI 才能在 androidTest 测 SafMarkdownFile；当前依赖手动真机验证（已验写入 + 恢复）。
- **TD-007 parseMarkdownEntries 包装器**：MainScreen 的 `parseMarkdownEntries`（薄委托 EntryParser.parse）待移除，调用方迁直调，随 Phase B ViewModel 重构。
- **TD-008 onClick 主线程 I/O**：MainScreen 的 append/delete/update/clear/archive 等仍主线程同步 I/O（一次性用户操作，可容忍）；全量下放 `Dispatchers.IO` 随 Phase B。仅 refreshEntryCount（2s 轮询）已下放。
- **TD-009 .bak 文件驻留**：SafMarkdownFile.safAtomicWrite 每次写覆写 .bak 但不主动清理；私有目录常驻 1 个 .bak（被覆写，不累积多个）。低影响。
