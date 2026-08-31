# TD-015 内容行丢失修复方案（`---` / `# Appendo` 形态隔离扩展）

> 日期：2026-08-31
> 状态：✅ 已确认并实现（v1.2.1，用户 2026-08-31 确认方案；T-d15c 落地，真机验证通过）
> 关联：debt-tracker TD-015、architecture.md §3.1/§3.3/§8.1 K6、task-list.yaml T-d15b/T-d15c

## 1. 问题

`EntryParser.parse` 把恰为 `---` 或以 `# Appendo` 开头的**内容行**当分隔符跳过（另有整段 `trim()` 与空条目丢弃）。这些行写进了文件、但解析视图里没有——用户编辑该条目保存（updateEntry）后，缺行版本被写回，**这些行永久丢失**；「复制全部」走原文不受影响，造成两个出口内容不一致。

## 2. 核心设计原则：隔离谓词 == 解析跳过谓词

**隔离（写侧加 ZWSP）的判定必须与解析（读侧跳过）的判定是同一个函数**，从根上杜绝"保护范围与跳过范围分叉"。新增：

```kotlin
/** parse 会跳过的"分隔形态"内容行：恰为 --- 或以 # Appendo 开头（与 parse 的跳过分支共用，勿分叉）。 */
fun isSkippableLine(line: String): Boolean =
    line == SEPARATOR_LINE || line.startsWith(FILE_HEADER_PREFIX)
```

- `SEPARATOR_LINE = "---"`、`FILE_HEADER_PREFIX = "# Appendo"`（后者从 `MarkdownFormatter.FILE_HEADER` 派生，复用既有常量）
- `parse` 的 when 分支改调 `isSkippableLine`（单一来源）

## 3. 写侧：isolateLine 扩展（幂等规则不变）

```kotlin
private fun isolateLine(line: String): String {
    if (line.startsWith(ISOLATION_MARKER)) return line
    return if (isTimestampLine(line) || isSkippableLine(line)) ISOLATION_MARKER + line else line
}
```

`format` 与 `buildUpdatedLines` 经 `isolateContent` 自动生效，存储层与 UI 零改动。

## 4. 读侧：restoreLine 对应扩展

```kotlin
private fun restoreLine(line: String): String {
    if (!line.startsWith(ISOLATION_MARKER)) return line
    val stripped = line.dropWhile { it == ISOLATION_MARKER.first() }
    return if (isTimestampLine(stripped) || isSkippableLine(stripped)) stripped else line
}
```

`parse`（视图还原）与 `stripIsolationMarkers`（出口剥离）内部同一函数——**出口剥离范围随之自然扩展到三类形态，无需新增任何出口登记点**；用户自有的、非这三类形态行内的 ZWSP 不受影响。

## 5. 旧文件兼容结论

- **无 ZWSP 的旧文件**：这类行在视图中仍不可见（跳过行为不变）——保护只覆盖**新写入/新编辑**的内容，已发生的丢失无法回溯恢复。
- 旧条目编辑时仍是"所见即所得"（对话框里本就没有这些行）——不再有超出用户所见之外的静默丢失。
- 旧文件解析行为不变：不抛异常、条目数不变。
- 已知现状保留（不扩大修复范围，仅记录）：`startsWith("# Appendo")` 会连带匹配 `# Appendox` 等前缀更长的行；`"----"`（四个连字符）不在跳过/保护范围。两者现状即如此，保护与跳过保持一致即无分叉风险。

## 6. 测试边界清单（EntryParserTest 新增）

1. format→parse 往返：内容含 `---` 行、`# Appendo` 开头行，不丢行、不误切条目
2. 幂等：多次 read-modify-write（format→parse→再 format）ZWSP 不叠加
3. updateEntry 路径：编辑含此类行的条目后重新 parse，内容与原文一致
4. 旧格式兼容：无 ZWSP 文件解析行为与现状一致
5. 出口剥离：readAllForExternal/stripIsolationMarkers 对 `ZWSP+---`、`ZWSP+# Appendo` 行还原干净；非形态行的用户自有 ZWSP 保留
6. 混合：同一内容中时间戳形态 + 分隔形态 + 正常文本并存
7. 既有全部用例无回归

## 7. 影响面与风险

- 改动仅 `util/EntryParser.kt` + `EntryParserTest.kt`；architecture.md §3.1/§3.3/§8.1 K6 在收尾任务同步
- 风险：文件中 ZWSP 总量增加（每个形态行 +1 字符/3 字节 UTF-8）——出口已统一剥离，无泄漏路径；体积影响可忽略
