# 测试目录说明 (`test/`)

## 目录职责

单元测试和集成测试。按被测模块组织目录结构，与源码目录一一对应。

## 目录结构

```
test/java/com/yiyue31/android/appendo/
├── data/
│   └── FileRepositoryFirstLaunchTest.kt    — FileRepository 首次启动逻辑测试
├── integration/
│   └── EntryDeletionIntegrationTest.kt     — 条目删除端到端测试
├── ui/
│   ├── MarkdownParserTest.kt               — Markdown 解析逻辑测试
│   └── ArchiveRestoreDeduplicationTest.kt  — 归档恢复去重逻辑测试
└── util/
    └── MarkdownFileDeleteTest.kt           — 文件删除操作测试
```

## 测试分类

| 目录 | 测试类型 | 说明 |
|------|---------|------|
| `data/` | 单元测试 | Repository 逻辑，需要 Mock Android Context |
| `integration/` | 集成测试 | 多模块协作场景，验证端到端流程 |
| `ui/` | 单元测试 | UI 逻辑（解析、去重），纯 JVM 可执行 |
| `util/` | 单元测试 | 文件操作逻辑 |

## 运行测试

```bash
./gradlew test
```

## 编码规范

- 测试覆盖率要求参见项目根目录 `CODINGRULES.md`
- Bug 修复必须附带回归测试
- 新功能开发应编写对应单元测试

## 扩展指南

- **添加新测试**：按被测模块放入对应子目录
- **新模块的测试**：创建与源码相同的包路径目录
- **Mock 使用**：Android Context 等 API 使用 Robolectric 或手动 Mock
