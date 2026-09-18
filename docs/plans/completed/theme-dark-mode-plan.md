# 主题功能（跟随系统 / 手动深浅切换） — 执行计划

> 日期: 2026-09-17
> 关联设计文档: `docs/design/theme-dark-mode-design.md`（已确认，D1=方案 A）
> 关联需求: `docs/specs.md` 需求 48~56（v1.3）
> 状态: ✅ 全部完成（2026-09-18 真机验收通过并归档；含两轮面板布局修正；同版本并入出口格式化特性，见 `docs/design/export-format-design.md`；真机发现 SP apply() 落盘冻结 → 改 commit()，测试基建约束记 TD-024）

---

## 任务总览

| 任务ID | 描述 | 依赖 | 状态 |
|--------|------|------|------|
| T-001 | 新增 `ui/theme/Theme.kt`：ThemeMode、AppendoTheme、双 colorScheme、success 扩展色 | — | ✅ passed |
| T-002 | XML 主题双变体 + `values-night/` + colors | — | ✅ passed |
| T-003 | 新增 `data/ThemePreferences.kt` | — | ✅ passed |
| T-004 | MainActivity 接线：setTheme + modeState + AppendoTheme 包裹 + 回调 | T-001, T-003 | ✅ passed |
| T-005 | ShareReceiverActivity setTheme | T-002, T-003 | ✅ passed |
| T-006 | AppColors 语义色迁移与删除（5 文件 40+ 处） | T-001 | ✅ passed（grep 零残留） |
| T-007 | MainScreen 菜单"主题" + 三选对话框 | T-004 | ✅ passed |
| T-008 | 单元测试：ThemePreferences + ThemeMode 映射 | T-003 | ✅ passed（5+3 用例） |
| T-009 | 全量 JVM 测试跑通 | T-001~T-008 | ✅ passed（BUILD SUCCESSFUL，15 套件） |
| T-010 | 真机验收（需求 53~56 + 回归） | T-009 | ✅ passed（adb 半自动走查 + 人工三态切换/杀进程持久化/深色分享路径；2026-09-18 用户确认） |
| T-011 | 文档同步（architecture/README/CHANGELOG/CLAUDE.md/CODINGRULES） | T-010 | ✅ passed（文档全部同步；本计划归档 completed/） |

---

## 任务详细定义

### T-001: 新增 `ui/theme/Theme.kt`

```
任务ID: T-001
描述: 创建主题基础设施：ThemeMode 枚举、AppendoTheme 组合函数、浅/深两套 ColorScheme、success/onSuccess 扩展色
约束:
  - 文件: Appendo/src/main/java/com/yiyue31/android/appendo/ui/theme/Theme.kt（新建 theme 子包，附 CLAUDE.md 说明，风格对齐其他子包）
  - 色值按设计文档 §4：浅色 primary #2196F3 / error #EF5350 / success #4CAF50（onXxx=白，现状延续）；
    深色 primary #90CAF9 / error #EF9A9A / success #A5D6A7（onXxx=深色，目标 ≥4.5:1，实施时用对比度工具核算微调）
  - 其余槽位用 M3 baseline lightColorScheme()/darkColorScheme() 默认值
  - success 经 CompositionLocal + MaterialTheme 扩展属性提供（访问形如 MaterialTheme.successColor）
  - AppendoTheme 内含 DisposableEffect(dark) 同步 WindowInsetsControllerCompat.isAppearanceLightStatusBars = !dark
  - 所有色值字面量集中在本文件常量（CODINGRULES 常量规范）
任务产出: Theme.kt（含 package-info 级 CLAUDE.md）
评估标准:
  - [ ] ThemeMode 三态；SYSTEM 分支走 isSystemInDarkTheme()
  - [ ] 双 scheme 按色值表定义，无散落字面量
  - [ ] success 扩展色可用且深浅两套正确切换
  - [ ] 状态栏图标随 dark 同步
评估方法: 源码阅读 + T-010 真机验证
当前轮次: 0 / 最大轮次: 3
状态: pending
```

### T-002: XML 主题双变体 + values-night

```
任务ID: T-002
描述: themes.xml 增加 Theme.Appendo.Dark 变体与显式状态栏项；新建 values-night/themes.xml；colors.xml 增加深色窗口背景色
约束:
  - values/themes.xml：Theme.Appendo 保持 Light parent，显式加 windowLightStatusBar=true；
    新增 Theme.Appendo.Dark（parent android:Theme.Material.NoActionBar，windowBackground=@color/window_bg_dark，windowLightStatusBar=false）
  - values-night/themes.xml：Theme.Appendo 以 Theme.Appendo.Dark 为 parent
  - values/colors.xml：新增 window_bg_dark #1C1B1F
  - 浅色模式观感与现状一致（状态栏相关 item 以真机现状截图校准，浅色不引入可感知变化）
  - AndroidManifest 不改（两 Activity 已引用 Theme.Appendo）
任务产出: 三个资源文件变更
评估标准:
  - [ ] values-night 资源存在且跟随系统时窗口背景正确
  - [ ] 浅色观感与 v1.2.2 一致（真机对比）
评估方法: T-010 真机冷启动检查
当前轮次: 0 / 最大轮次: 3
状态: pending
```

### T-003: 新增 `data/ThemePreferences.kt`

```
任务ID: T-003
描述: 主题偏好的读写：appendo SP 新键 theme_mode
约束:
  - 复用 "appendo" SP 文件（与 use_saf 等同文件，不新建文件）
  - 键常量与三态字符串常量集中本文件；缺省/非法值回退 SYSTEM
  - 仅在用户显式选择时写入（含显式选回"跟随系统"）；无"写入默认值"的初始化路径
  - 读方法须可在 Activity.onCreate 的 super 之前调用（同步、轻量）
任务产出: ThemePreferences.kt
评估标准:
  - [ ] 缺省返回 SYSTEM；三态读写往返一致；非法值回退 SYSTEM
评估方法: T-008 单元测试
当前轮次: 0 / 最大轮次: 3
状态: pending
```

### T-004: MainActivity 接线

```
任务ID: T-004
描述: 冷启动 setTheme、themeMode Compose State、AppendoTheme 包裹、主题变更回调透传
约束:
  - onCreate 读偏好：LIGHT→setTheme(R.style.Theme_Appendo)；DARK→setTheme(R.style.Theme_Appendo_Dark)；SYSTEM→不动（交给 values-night）；setTheme 在 super.onCreate 之前
  - modeState = mutableStateOf(初始值)；setContent { AppendoTheme(modeState.value) { AppendoNavHost(...) } }
  - onThemeChange 回调：更新 modeState + ThemePreferences.write；经 AppendoNavHost 传至 MainScreen
  - 不引入 Activity 重建；现有 scrollTs/onNewIntent 逻辑不动
任务产出: MainActivity.kt 变更
评估标准:
  - [ ] 手动模式冷启动首帧窗口背景正确（需求 56）
  - [ ] 运行中切主题无重建、即时生效（需求 50）
评估方法: 源码阅读 + T-010 真机
当前轮次: 0 / 最大轮次: 3
状态: pending
```

### T-005: ShareReceiverActivity setTheme

```
任务ID: T-005
描述: 分享接收 Activity 冷启动窗口背景与主题一致
约束:
  - 与 T-004 相同的 setTheme 三分支逻辑（可在 ThemePreferences 提供 helper 复用，避免双写）
  - 其余逻辑（IO 协程、finish）不动
任务产出: ShareReceiverActivity.kt 变更
评估标准:
  - [ ] 深色系统 + 跟随模式下发起分享无白闪（需求 56 分享路径）
评估方法: T-010 真机
当前轮次: 0 / 最大轮次: 3
状态: pending
```

### T-006: AppColors 语义色迁移与删除

```
任务ID: T-006
描述: 全部 AppColors 引用改为 MaterialTheme 语义色，删除 AppColors.kt
约束:
  - 映射：AppColors.Primary → MaterialTheme.colorScheme.primary；Danger → colorScheme.error；Success → MaterialTheme.successColor（T-001 扩展）；
    lightOnPrimary() → colorScheme.onPrimary
  - 特殊用途按设计 §4：滑动删除/追加中间态背景用容器色 + onXxx 文字色（深色下亮底深字）；
    主按钮 containerColor=primary、contentColor=onPrimary
  - 涉及文件：MainScreen.kt、EntryListScreen.kt、ArchiveListScreen.kt、ArchiveDetailScreen.kt、ReminderTimePickerDialog.kt（30+ 处）
  - 纯机械替换，不改任何逻辑/布局/文案；ToastUtils 不动
  - 预期行为变化点：EntryListScreen 徽标色由 M3 基线紫变品牌蓝（设计已确认为修复）
  - 删除 AppColors.kt，全局无残留引用
任务产出: 5 个 UI 文件变更 + AppColors.kt 删除
评估标准:
  - [ ] 编译通过、无 AppColors 残留引用（grep 验证）
  - [ ] 浅色模式下各处颜色与迁移前一致（除已知徽标修复点）
评估方法: grep + T-009 测试 + T-010 真机走查
当前轮次: 0 / 最大轮次: 3
状态: pending
```

### T-007: MainScreen 菜单"主题" + 三选对话框

```
任务ID: T-007
描述: 菜单新增"主题"项（"关于"之前），弹出三选 chips 对话框
约束:
  - 对话框风格对齐 ReminderTimePickerDialog（chips、当前项高亮）
  - 选中即调 onThemeChange（State 变更 + SP 写入），无需确认按钮二次确认
  - 文案："主题" / "跟随系统" / "浅色" / "深色"（中文直接字面量，随项目现状）
任务产出: MainScreen.kt 变更
评估标准:
  - [ ] 三态切换即时生效（需求 48/50）
  - [ ] 当前模式在对话框中高亮且与实际生效一致
评估方法: 源码阅读 + T-010 真机
当前轮次: 0 / 最大轮次: 3
状态: pending
```

### T-008: 单元测试

```
任务ID: T-008
描述: 新增 ThemePreferences 与 ThemeMode→dark 映射的单元测试
约束:
  - 放 Appendo/src/test/java/com/yiyue31/android/appendo/（对齐既有测试位置；CODINGRULES 的 tests/ 目录描述与现状不符，按现状执行）
  - ThemePreferences：缺省回退 SYSTEM / 三态往返 / 非法值回退 / 写入仅在显式调用时发生（SP mock 方式对齐既有 data 层测试先例）
  - ThemeMode 映射：SYSTEM(isDark 参数化) / LIGHT=false / DARK=true（纯函数化以便 JVM 测试）
  - 覆盖正常路径、边界、异常输入（CODINGRULES 测试规范）
任务产出: 测试文件 + 通过的运行记录
评估标准:
  - [ ] 新增测试全部通过
评估方法: gradle test 运行
当前轮次: 0 / 最大轮次: 3
状态: pending
```

### T-009: 全量 JVM 测试跑通

```
任务ID: T-009
描述: 运行全部单元测试，确认无回归
约束:
  - gradle test 全量通过（既有测试 + T-008 新增）
  - 失败项修复后方可进入 T-010
任务产出: 全量通过的测试运行记录
评估标准:
  - [ ] 全部测试通过
评估方法: gradle test
当前轮次: 0 / 最大轮次: 3
状态: pending
```

### T-010: 真机验收

```
任务ID: T-010
描述: 按设计 §7 真机验收清单逐条执行（需求 53~56 对应场景 + 回归）
约束:
  - 三态切换即时生效/重启保持/滚动与导航不重置
  - 跟随系统：前台实时 / 后台切回前台（含提醒通知深链）/ 切回手动后独立
  - 深色走查：主页（含空状态）/ 归档列表 / 归档详情 / 全部对话框（设计 §7 第 3 条清单）/ 菜单 / 滑动中间态 / 状态栏图标
  - 冷启动：三模式窗口背景正确无闪白；深色系统发起分享无白闪
  - 回归：浅色全量走查观感与 v1.2.2 一致（重点状态栏）；提醒徽标变品牌蓝确认为预期；复制/分享出口内容抽查（应不受影响，防呆）
  - 发现问题记录并回阶段循环（CLAUDE.md 阶段5）
任务产出: 验收记录（逐项通过/问题清单）
评估标准:
  - [ ] 需求 53~56 全部场景通过
  - [ ] 回归无问题或问题已记录处置
评估方法: 真机操作 + 截图
当前轮次: 0 / 最大轮次: 3
状态: pending
```

### T-011: 文档同步

```
任务ID: T-011
描述: 提交前完成全部文档同步（CLAUDE.md 任务完成检查清单）
约束:
  - docs/architecture.md：§3.6 SP 清单加 theme_mode；UI 层补主题机制小节；过一遍 §11 变更评审清单
  - README.md：主题功能说明 + 升级默认行为变更声明（需求 48）
  - CHANGELOG.md：v1.3.0 条目（含升级声明）
  - ui/CLAUDE.md：AppColors 行改为 Theme.kt；扩展指南同步
  - CODINGRULES.md：常量规范"颜色值统一放 AppColors"修订为指向 ui/theme/Theme.kt（属规范与实现对齐，需用户过目）
  - 本计划完成后移动到 docs/plans/completed/ 并记录完成日期
任务产出: 上述文档变更
评估标准:
  - [ ] 各文档与实现一致，无遗留 TODO
评估方法: 交叉阅读
当前轮次: 0 / 最大轮次: 3
状态: pending
```

---

## 风险与注意点

- **徽标颜色变化**：EntryListScreen:256 由基线紫变品牌蓝，验收时确认为预期改善（设计已记录）
- **浅色观感回归**是硬约束：T-002/T-006 任何"顺手优化"浅色的行为都违反 D1 决策
- **真机为 realme**：系统深浅切换入口与多窗口行为按该机型验收；TD-021 先例表明该机有非标准行为，冷启动检查多留一轮
- Robolectric 不可用（TD-003 债务）：主题相关验证依赖真机，JVM 测试只覆盖纯逻辑
