# 项目目录文档完善分析

> 日期: 2026-06-02
> 目的: 分析各目录的文档现状，识别需要补充的信息，以便后续 AI 维护和理解

---

## 分析结论

当前项目的高层文档（根目录 CLAUDE.md、CODINGRULES.md、README.md、docs/specs.md、docs/design.md）比较完善，但**各源码目录缺少包级别的说明文档**，导致 AI agent 在维护时需要反复阅读源码才能理解目录结构和设计意图。

### 最终方案（已确认）

采用 **CLAUDE.md + README.md** 双文件策略：

- **CLAUDE.md**：存放实际的目录说明内容（职责、设计模式、依赖关系、扩展指南）
- **README.md**：作为入口文件，内容仅指向对应的 CLAUDE.md

### 已完成文档的目录

| 目录 | CLAUDE.md | README.md |
|------|-----------|-----------|
| `appendo/`（主包） | ✅ | ✅ |
| `data/` | ✅ | ✅ |
| `ui/` | ✅ | ✅ |
| `util/` | ✅ | ✅ |
| `test/` | ✅ | ✅ |

---

## 各目录分析详情

### 1. 根目录 `/`

| 项目 | 状态 |
|------|------|
| 现有文档 | README.md, CLAUDE.md, CODINGRULES.md, CHANGELOG.md |
| 评估 | ✅ 文档完善，无需补充 |

### 2. 主包目录 `Appendo/src/main/java/.../appendo/`

| 项目 | 状态 |
|------|------|
| 现有文档 | 无 |
| 评估 | ⚠️ 需要补充 |

**应包含的信息：**
- 双 Activity 架构说明（MainActivity + ShareReceiverActivity）
- 为什么需要两个 Activity（ShareReceiverActivity 处理外部分享 Intent）
- Navigation Compose 路由结构概览
- 三个子目录的职责概述
- AppendoApplication 的作用

### 3. `data/` 目录

| 项目 | 状态 |
|------|------|
| 现有文档 | 无 |
| 评估 | ⚠️ 需要补充 |

**应包含的信息：**
- FileRepository：文件存储偏好管理和 URI 持久化
- ArchiveRepository：归档文件管理
- 双存储模式（SAF vs 默认文件存储）的切换逻辑
- SharedPreferences 命名约定（历史遗留为 "link_appending"）
- 线程安全注意事项（FileOperationLock）

### 4. `ui/` 目录

| 项目 | 状态 |
|------|------|
| 现有文档 | 无 |
| 评估 | ⚠️ 需要补充 |

**应包含的信息：**
- 导航流程：main → archive_list → archive_detail
- 各 Screen 组件的职责
- EntryListScreen 的复用设计（可跨上下文使用）
- Material 3 设计系统使用
- 交互模式说明（右滑删除、长按复制等）
- AppColors 的用途
- ToastUtils 的兼容性处理

### 5. `util/` 目录

| 项目 | 状态 |
|------|------|
| 现有文档 | 无 |
| 评估 | ⚠️ 需要补充 |

**应包含的信息：**
- 设计模式说明：
  - `MarkdownFileOperations` 接口定义操作契约
  - `MarkdownFileFactory` 工厂模式创建合适的实现
  - `FileBasedMarkdownFile` 和 `SafMarkdownFile` 策略模式
- 文件格式要求（UTF-8 无 BOM、Markdown 格式）
- 线程同步机制
- 回退机制（SAF 追加失败时的降级处理）
- 已废弃文件说明（MarkdownFile.kt 为旧版遗留）

### 6. `Appendo/src/test/` 目录

| 项目 | 状态 |
|------|------|
| 现有文档 | 无 |
| 评估 | ⚠️ 可选补充 |

**应包含的信息：**
- 测试目录组织方式（按模块分目录）
- 单元测试 vs 集成测试的划分
- Mock 使用模式
- 测试覆盖率要求（参见 CODINGRULES.md）

### 7. `docs/` 目录

| 项目 | 状态 |
|------|------|
| 现有文档 | specs.md, design.md, plans/PLANS.md |
| 评估 | ✅ 结构清晰，无需补充 |

### 8. `scripts/` 目录

| 项目 | 状态 |
|------|------|
| 现状 | 不存在 |
| 评估 | ℹ️ 当前不需要 |

**未来如需添加：** 构建脚本、APK 签名、CI/CD 流水线等

---

## 跨目录依赖关系

以下是 AI 维护时必须了解的跨目录依赖：

```
ui/MainScreen.kt ──依赖──→ data/FileRepository.kt ──依赖──→ util/MarkdownFileFactory.kt
                                      │                              │
                                      │                    ┌─────────┴──────────┐
                                      │                    ▼                    ▼
                                      │           util/FileBasedMarkdownFile  util/SafMarkdownFile
                                      │
ui/ArchiveListScreen.kt ──依赖──→ data/ArchiveRepository.kt
ui/ArchiveDetailScreen.kt ──依赖──→ data/ArchiveRepository.kt
```

**关键依赖链说明：**
1. UI 层通过 Repository 层访问数据，不直接操作文件
2. FileRepository 通过 MarkdownFileFactory 创建具体的文件操作实现
3. ArchiveRepository 独立管理归档文件，但也依赖 FileRepository 的存储配置

---

## 建议优先级

| 优先级 | 目录 | 理由 |
|--------|------|------|
| 🔴 高 | `util/` | 设计模式复杂，AI 最容易误改 |
| 🔴 高 | `data/` | 双存储模式不直观，线程安全关键 |
| 🟡 中 | `ui/` | 组件复用关系需要说明 |
| 🟡 中 | `appendo/`（主包） | 双 Activity 架构需要解释 |
| 🟢 低 | `test/` | 测试模式相对直观 |

---

## 完成状态

所有 5 个关键目录的 CLAUDE.md 和 README.md 已创建完成。
