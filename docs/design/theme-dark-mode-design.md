# 主题（跟随系统 / 手动深浅切换） — 设计文档

> 日期: 2026-09-17
> 状态: ✅ 已确认（2026-09-17 用户定稿，D1 决策：方案 A）
> 需求: `docs/specs.md` 需求 48~56（v1.3）
> 现状架构: [../architecture.md](../architecture.md)

---

## 1. 功能概述

为应用提供三态主题：**跟随系统**（默认）/ **浅色** / **深色**。菜单"主题"项弹三选对话框，切换即时生效、重启保持；深色完整覆盖应用自身渲染的全部界面；冷启动窗口背景与所选主题一致。

现状基线：纯 Compose 应用，**无 MaterialTheme 包装**（UI 取 M3 默认浅色板）；XML 主题固定 `android:Theme.Material.Light.NoActionBar`；强调色硬编码于 `AppColors`（蓝/红/绿，5 个 UI 文件 30+ 处引用）。

## 2. 需求决策记录

| 决策点 | 结论 | 理由 |
|--------|------|------|
| 主题责任层 | **Compose 全权负责界面配色；XML 主题只管窗口背景/状态栏**（冷启动首帧） | 界面已是 Compose；XML 侧只解决"setContent 首帧之前"的窗口外观 |
| 三态模型 | `ThemeMode { SYSTEM, LIGHT, DARK }`，默认 SYSTEM | 一次实现覆盖"跟随系统"与"手动切换"两个原始诉求 |
| 偏好存储 | `appendo` SP 文件新键 `theme_mode`（`system`/`light`/`dark`，**缺省即 system，不写默认值**） | 复用现有 SP 文件（Application 启动路径已加载，冷启动读取零额外磁盘 IO）；"未选择不持久化"由"只在用户显式选择时写入"实现（需求 48） |
| 色板接入 | **语义色迁移**：`AppColors.Primary→colorScheme.primary`、`Danger→error`、`Success→自定义扩展色`，全部经 `MaterialTheme` 访问 | Material 惯用；未来换色只动 Theme.kt；顺带修复 `EntryListScreen.kt:256` 徽标误用基线紫的问题（接入后自动变品牌蓝） |
| 深色强调色 | M3 深色规范：**亮容器 + 深 onXxx 文字**（非白字） | 深底上白字对亮色容器对比不足 |
| 冷启动窗口背景 | 双变体 XML 主题（`Theme.Appendo` / `Theme.Appendo.Dark`）+ `values-night/` 覆盖 + Activity `onCreate`（`super` 前）按偏好 `setTheme` | values-night 只能覆盖跟随系统；手动模式须首帧前选对变体；framework 主题无需引入 AppCompat 依赖 |
| 状态栏图标 | XML 双变体显式 `windowLightStatusBar` 作冷启动兜底；运行期由 Compose `DisposableEffect(dark)` 同步 `WindowInsetsControllerCompat.isAppearanceLightStatusBars` | 单一事实源 = 当前生效 colorScheme；不依赖 framework 默认值（现状未显式声明） |
| 跟随系统实时性 | **默认 Activity 重建**（不处理 `configChanges`） | `uiMode` 变化触发重建 → `isSystemInDarkTheme()` 新值 + 资源系统重解析 night；滚动位置经 `rememberLazyListState`（saveable）恢复、导航栈经 NavController 内建 saveable 恢复；系统主题切换是罕见事件，打开的对话框丢失可接受。备选 `configChanges="uiMode"` 不重建方案复杂度高（须手动同步窗口背景/状态栏），不采用 |
| 手动切换即时性 | 纯 Compose State 驱动，**无 Activity 重建** | `themeMode` 为 MainActivity 持有的 `mutableStateOf`，菜单回调改值即重组；导航栈/滚动天然不动（需求 50/53） |
| ShareReceiverActivity | 同样 `setTheme` 逻辑 | 无 UI 但有窗口；SAF 模式 IO 期间窗口可见，深色系统下白闪违背需求 56 |
| 对比度门槛 | WCAG AA：正文文字 4.5:1，大字号与组件边界 3:1；**仅对深色新配色强制，浅色现状本期不动**（见 §6 决策点 D1） | 深色全新设计须达标；浅色现状 3.1:1 若强行达标将使 v1.3 变成"换色版本"，超出主题功能范围 |
| 控件样式 | 主题选择用三选对话框，选项**纵向整行按钮**（真机验收修正 2026-09-18：横排 chips 下"跟随系统"四字换行致按钮高度不齐；纵排任意字数/字号缩放均安全，且与提醒对话框"自定义日期时间…"整行按钮模式一致） | 与应用既有整行按钮风格一致 |

## 3. 模块设计

### 3.1 新增 `ui/theme/Theme.kt`

```kotlin
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun AppendoTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) AppendoDarkScheme else AppendoLightScheme,
        content = content
    )
}
```

- 两个 scheme 基于 M3 baseline `lightColorScheme()` / `darkColorScheme()` 定制品牌色（§4 色值表）
- **Success 色**：M3 `ColorScheme` 无 success 槽位，以 `CompositionLocal` 提供 `success/onSuccess` 扩展色，访问器形如 `MaterialTheme.successColor`（扩展属性），与 primary/error 同风格
- `AppColors.kt` 迁移后删除（或留 deprecated 注释指路 Theme.kt），30+ 处引用改为语义色访问——机械改动，无逻辑变化

### 3.2 偏好读写 `data/ThemePreferences.kt`

- 读写 `appendo` SP 的 `theme_mode` 键；解析失败/缺省回退 `SYSTEM`
- 写入仅在用户显式选择时发生（三态任一，包括显式选回"跟随系统"——显式表态与从未选择行为无差异、用户不可感知，写更简单一致）

### 3.3 XML 主题

```xml
<!-- values/themes.xml -->
<style name="Theme.Appendo" parent="android:Theme.Material.Light.NoActionBar">
    <item name="android:windowLightStatusBar">true</item>   <!-- 显式化，冷启动兜底 -->
</style>
<style name="Theme.Appendo.Dark" parent="android:Theme.Material.NoActionBar">
    <item name="android:windowBackground">@color/window_bg_dark</item>
    <item name="android:windowLightStatusBar">false</item>
</style>

<!-- values-night/themes.xml（跟随系统时资源系统自动选用） -->
<style name="Theme.Appendo" parent="Theme.Appendo.Dark" />
```

- `window_bg_dark = #1C1B1F`（M3 dark surface，与 Compose 深色 surface 同源）
- 状态栏底色与窗口背景同色系（具体 item 以真机现状截图校准，保证**浅色模式观感与现状一致**）

### 3.4 Activity 接线

**MainActivity**：

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    val themeMode = ThemePreferences.read(this)      // appendo SP，已内存缓存
    when (themeMode) {
        LIGHT -> setTheme(R.style.Theme_Appendo)
        DARK  -> setTheme(R.style.Theme_Appendo_Dark)
        SYSTEM -> Unit                               // 交给 values-night
    }
    super.onCreate(savedInstanceState)
    val modeState = mutableStateOf(themeMode)
    setContent {
        AppendoTheme(modeState.value) {
            AppendoNavHost(fileRepository, scrollTs, onThemeChange = { modeState.value = it; ThemePreferences.write(...) })
        }
    }
}
```

**ShareReceiverActivity**：仅 `setTheme` 段，无 UI 接线。

### 3.5 设置入口（`MainScreen`）

- 菜单新增"主题"（置于"关于"之前）→ 三选对话框（跟随系统/浅色/深色），选项纵向整行按钮、当前项高亮
- 确认即回调 `onThemeChange`（State 变更即时重组 + SP 写入），不关闭应用、不重建

### 3.6 运行期状态栏同步

`AppendoTheme` 内 `DisposableEffect(dark)`：`WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars = !dark`。手动切换（无重建）与重建路径（XML 兜底 + 重复设置幂等）双覆盖。

## 4. 色值表（实施时以对比度工具核算微调，门槛见 §2）

| 语义 | 浅色（现状延续） | 深色（M3 亮容器+深文字） |
|------|-----------------|------------------------|
| primary / onPrimary | `#2196F3` / 白（现状） | `#90CAF9` / 深蓝（≥4.5:1 目标色 `#0D2E4E` 附近） |
| error（Danger）/ onError | `#EF5350` / 白（现状） | `#EF9A9A` / 深红 |
| success / onSuccess | `#4CAF50` / 白（现状） | `#A5D6A7` / 深绿 |
| surface / onSurface 等 | M3 baseline 浅色板 | M3 baseline 深色板 |

特殊用途处理：

- **主按钮**（手动输入，蓝底白字）：迁移后用 `primary`/`onPrimary`——浅色观感不变；深色自动变亮底深字
- **描边按钮/文字色**（复制全部等）：`primary` 文字色——浅色不变，深色自动提亮
- **滑动删除/追加中间态**（红/绿背景 + 白字）：背景用容器色、文字用 onXxx——深色下自动亮底深字
- **Toast**：现有半透明黑样式两主题均可读，不动（需求 51 措辞已对齐）

## 5. 需求 → 方案对照

| 需求 | 方案落点 |
|------|---------|
| 48 三态设置 | §3.1 / §3.5 |
| 49 持久化 | §3.2 |
| 50 即时生效与实时跟随 | §2 手动=State 无重建；跟随=重建+saveable 恢复 |
| 51 适配范围 | 语义色迁移覆盖全部 Compose 界面；§3.3/§3.4 覆盖分享路径窗口背景；§3.6 状态栏 |
| 52 强调色可读性 | §4 深色达标；浅色见决策点 D1 |
| 53~56 验收 | §7 测试计划逐条对应 |

## 6. 待用户拍板的决策点

**D1：浅色强调色对比度不达标（实测约 3.1:1 < AA 正文 4.5:1）如何处理**

- **方案 A（已采纳，2026-09-17 用户拍板）**：深色新配色达标；浅色现状不动（避免 v1.3 被动变成"浅色换色版本"），将"浅色强调色对比度提升"记入 debt-tracker 作独立后续项
- ~~方案 B：浅色同步调深（如 primary → `#1976D2`，4.6:1），两主题同时达标，但存量用户浅色界面观感变化~~（未采纳）

## 7. 测试计划

**单元测试**（新增）：

- `ThemePreferences`：缺省回退 SYSTEM；三态读写往返；非法值回退
- `ThemeMode → dark` 映射纯函数（SYSTEM 分支由 isSystemInDarkTheme 注入布尔实现可测性）

**真机验收**（对应需求 53~56，逐条执行）：

1. 三态手动切换即时生效；重启保持；切换时主页滚动位置与导航栈不重置
2. 跟随系统：(a) 前台系统切换实时跟随 (b) 后台切换回前台（含提醒通知深链进入）主题正确 (c) 切回手动后不受系统影响
3. 深色走查清单：主页（含空状态）/ 归档列表 / 归档详情 / 全部对话框（手动输入、条目编辑、各确认、关于、提醒时间选择、提醒自检、首启存储引导、主题选择）/ 下拉菜单 / 滑动删除与追加中间态 / 状态栏图标深浅
4. 冷启动：三种模式下窗口背景正确、无可感知闪烁；深色系统下发起分享无白闪
5. 回归：浅色模式全量走查（观感与现状一致，重点状态栏）；提醒徽标颜色（由基线紫变品牌蓝，确认为预期改善）；复制/分享出口内容不受影响（不涉及，防呆检查）

## 8. 影响面清单

| 文件 | 变更 |
|------|------|
| `ui/theme/Theme.kt` | **新增**：AppendoTheme、双 scheme、success 扩展色、ThemeMode |
| `values-night/themes.xml` | **新增** |
| `values/colors.xml`、`values/themes.xml` | 修改：Dark 变体、显式状态栏项、window_bg_dark |
| `data/ThemePreferences.kt` | **新增** |
| `MainActivity.kt` | 修改：setTheme、modeState、AppendoTheme 包裹、回调 |
| `ShareReceiverActivity.kt` | 修改：setTheme |
| `ui/MainScreen.kt` | 修改：菜单"主题"+ 对话框；AppColors 引用迁移 |
| `ui/AppColors.kt` | 删除（迁移完成） |
| `ui/EntryListScreen.kt`、`ArchiveListScreen.kt`、`ArchiveDetailScreen.kt`、`ReminderTimePickerDialog.kt` | 修改：AppColors 引用迁移（30+ 处，机械） |

无新依赖；无数据协议变更（Markdown/ZWSP/sidecar 不动）。

## 9. 实施时同步项

- `docs/architecture.md`：§3.6 SP 清单加 `theme_mode`；UI 层补主题机制小节；变更评审清单过一遍
- `README.md`：主题功能说明 + 需求 48 要求的升级默认行为变更声明
- `CHANGELOG.md`：v1.3.0 条目（含升级声明）
- `docs/plans/debt-tracker.md`：D1 若选方案 A，登记浅色对比度债务
- `Appendo/src/main/java/.../ui/CLAUDE.md`：AppColors 行更新为 Theme.kt
