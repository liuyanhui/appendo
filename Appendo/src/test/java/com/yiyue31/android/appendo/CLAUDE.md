# 测试目录说明 (`test/`)

## 目录职责

单元测试和集成测试。按被测模块组织目录结构，与源码目录一一对应。

## 目录结构

```
test/java/com/yiyue31/android/appendo/
├── data/
│   ├── ArchiveNameParseTest.kt                 — 归档命名/解析（WB-1 尾部锚定、同秒 _n 不覆盖）
│   ├── FileRepositoryFirstLaunchTest.kt        — FileRepository 首次启动逻辑测试
│   ├── ThemePreferencesTest.kt                 — 主题三态偏好（缺省/非法回退 SYSTEM）
│   └── TopicRepositoryTest.kt                  — 主题文档管理（topics/ 增删改名）
├── integration/
│   └── EntryDeletionIntegrationTest.kt         — 条目删除端到端测试（真文件）
├── ui/
│   ├── ArchiveOutcomeMappingTest.kt            — 归档二选一决策映射全组合
│   ├── ArchiveRestoreDeduplicationTest.kt      — 归档恢复去重算法测试
│   ├── ChooseAppendToastTest.kt                — 重复内容追加 toast 旁注（防硬编码漂移）
│   ├── MarkdownParserTest.kt                   — parseMarkdownEntries 包装器测试
│   ├── MoveFlowHolderRestoreTest.kt            — 移入流程快照恢复（v1.6.1 N-4）
│   ├── MoveOutcomeMappingTest.kt               — 移入/移出/转移决策映射全组合
│   ├── MoveRestoreDecisionTest.kt              — 事务中断后恢复重推导（v1.6.1 N-4）
│   ├── MultiSelectTest.kt                      — 多选选中集维护纯函数
│   ├── StateSaversTest.kt                      — Saver round-trip/损坏穷举（v1.6.1 N-4）
│   ├── TopicBadgeTest.kt                       — 主题首字标色板（对比度独立复算）
│   └── theme/
│       └── ThemeModeTest.kt                    — resolveDarkTheme 纯函数（模式×系统深浅）
└── util/
    ├── CalendarEntryMapperTest.kt              — 添加到日历标题映射（v1.0.3）
    ├── DuplicateHintThrottleTest.kt            — 重复提示节流
    ├── EntryParserTest.kt                      — EntryParser 全量（读/写/边界/恢复）
    ├── ExportFormatTest.kt                     — 出口格式化 formatForExport（v1.3）
    ├── FileBasedMarkdownFileTest.kt            — FileBasedMarkdownFile 真类（mock Context + 真 FS）
    ├── RefreshGuardTest.kt                     — 列表刷新守卫（TD-016）
    ├── SecondLevelEquivalenceTest.kt           — 秒级旧条目等价类（v1.4 前无 .SSS）
    └── TopicMoveTransactionTest.kt             — 组合事务六步编排/失败态矩阵（T-03）
```

（当前 24 个测试类、270 用例基线；清单随批次增长，权威口径见 `docs/architecture.md` §7 与 §12。）

## 测试分类

| 目录 | 测试类型 | 说明 |
|------|---------|------|
| `data/` | 单元测试 | Repository 逻辑，需要 Mock Android Context |
| `integration/` | 集成测试 | 多模块协作场景，验证端到端流程 |
| `ui/` | 单元测试 | UI 逻辑（解析、去重），纯 JVM 可执行 |
| `util/` | 单元测试 | 文件操作逻辑 |

## 运行测试

```bash
./gradlew :Appendo:testDebugUnitTest            # JVM 单测（Android 模块的 JVM 单测任务）
# instrumented（SAF/UI，需真机）: ./gradlew :Appendo:connectedDebugAndroidTest
```

> 本环境 Robolectric 不可用（SSL 无法下载 android-all jar）。FileBased 测试用纯 JVM + `mock<Context>()`（context 未使用）+ 真实临时文件；SAF/UI 测试需 instrumented（真机）。

## 编码规范

- 测试覆盖率要求参见项目根目录 `CODINGRULES.md`
- Bug 修复必须附带回归测试
- 新功能开发应编写对应单元测试

## 扩展指南

- **添加新测试**：按被测模块放入对应子目录
- **新模块的测试**：创建与源码相同的包路径目录
- **Mock 使用**：Android Context 等 API 使用 Robolectric 或手动 Mock
