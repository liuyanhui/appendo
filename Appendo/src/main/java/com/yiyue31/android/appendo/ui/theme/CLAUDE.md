# 主题包目录说明 (`ui/theme/`)

## 目录职责

主题基础设施（v1.3）：三态主题模式（跟随系统/浅色/深色）、浅/深两套 Material 3 ColorScheme、success 扩展色、状态栏外观同步。全部 UI 组件经此处取色。

## 关键文件

| 文件 | 职责 |
|------|------|
| `Theme.kt` | `ThemeMode` 枚举、`resolveDarkTheme` 纯函数、`AppendoTheme` 组合函数（双 scheme + CompositionLocal 扩展色 + 状态栏同步）、色板常量 `Palette` |

## 架构要点

- **责任分层**：Compose 全权负责界面配色；XML 主题（`values/themes.xml` + `values-night/`）只管冷启动首帧前的窗口背景/状态栏
- **三态**：`SYSTEM`（默认，`isSystemInDarkTheme()`）/ `LIGHT` / `DARK`；偏好存 `appendo` SP 的 `theme_mode` 键（`data/ThemePreferences`）
- **手动切换**：MainActivity 持有 `mutableStateOf<ThemeMode>`，改值纯重组生效，无 Activity 重建（导航栈/滚动位置不动）
- **跟随系统**：默认机制 = uiMode 配置变化触发 Activity 重建；滚动位置经 `rememberLazyListState` 恢复
- **色板**：浅色 = v1.2.2 现状延续（D1 决策不动，TD-023；**例外（v1.4 平面卡裁定两轮）：`background` 蓝调 `#E8EDF4`、`surface` 纯白 `#FFFFFF`**（三轮：中性灰显脏改蓝调，贴品牌蓝））；深色 = M3 亮容器 + 深 onXxx（WCAG AA ≥4.5:1；**例外（v1.4）：`background` `#000000`、`surface` 提亮 `#26252B`**）——卡片平面化视觉裁定（换机验证反馈，参考 iOS 分组列表）：卡片零投影，层级靠底色色调差；`surface` 覆盖联动卡片/MultiSelect 顶栏/外观预览
- **success 色**：M3 `ColorScheme` 无此槽位，经 `LocalSuccessColors` + `MaterialTheme.successColor`/`onSuccessColor` 扩展属性提供

## 取色规约（原 AppColors 已删除）

| 语义 | 浅色 | 深色 | 访问方式 |
|------|------|------|---------|
| Primary（蓝） | `#2196F3`/白 | `#90CAF9`/`#0D2E4E` | `MaterialTheme.colorScheme.primary` / `.onPrimary` |
| Danger→error（红） | `#EF5350`/白 | `#EF9A9A`/`#4A0C10` | `MaterialTheme.colorScheme.error` / `.onError` |
| Success（绿） | `#4CAF50`/白 | `#A5D6A7`/`#1B3D1F` | `MaterialTheme.successColor` / `.onSuccessColor` |
| 页面底色（v1.4 平面卡分层） | `#E8EDF4`（蓝调） | `#000000`（纯黑） | `MaterialTheme.colorScheme.background`——列表页 Scaffold/顶栏取此值；卡片零投影 |
| 卡片/容器色（v1.4 平面卡分层） | `#FFFFFF`（纯白） | `#26252B` | `MaterialTheme.colorScheme.surface`——卡片（SwipeCard/EntryCard）＋MultiSelect 顶栏＋外观预览同源 |

**彩色背景上的文字一律用对应 onXxx**（如滑动删除红底文字用 `onError`），深色下自动变深色文字。

## 依赖关系

```
ui/theme ← data/ThemePreferences（模式读写；ThemeMode 定义在本包）
MainActivity → AppendoTheme（setContent 包裹）+ ThemePreferences（冷启动 setTheme）
全部 ui/ 组件 → MaterialTheme.colorScheme / successColor 扩展
res/values(-night)/themes.xml、colors.xml ← 窗口/状态栏色（与 Palette 同值同步）
```

## 扩展指南

- **改品牌色**：只改 `Theme.kt` 的 `Palette` + `res/values/colors.xml` 同步状态栏色
- **加新语义色**：仿 SuccessColors 模式加 CompositionLocal + 扩展属性
- **加新主题模式**（如 AMOLED 纯黑）：扩 `ThemeMode` + 新 scheme + ThemePreferences 值
