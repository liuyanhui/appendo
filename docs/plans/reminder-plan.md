# 本地提醒功能（方案 C） — 执行计划与进度

> 关联设计: `docs/design/local-reminder-design.md`
> 用途: 记录任务进度，便于冷启动续作。续作时先读本文件 + 设计文档，按下表状态继续。

## 修订记录

| 日期 | 变更人 | 变更摘要 |
|------|--------|---------|
| 2026-08-10 | Yiyue + Claude | 初始版本：方案 C 定稿（含三角色评审修正），Phase 1/2 任务拆分，均未开工 |
| 2026-08-11 | Yiyue + Claude | Phase 1 完成（T1–T7 全部实现 + 编译 + 单测 + 真机安装）；版本 1.1.0 |

## 状态标记

- ⬜ 未开始
- 🔄 进行中
- ✅ 完成
- ⏸ 阻塞（注明原因）

## 里程碑

| 阶段 | 内容 | 状态 |
|------|------|------|
| P1 | 核心一次性提醒 + 验证套件（realme 上确认能响） | ✅ 完成（短时验证通过；重启需开自启动） |
| P2 | 重复提醒（每天/每周） | ⬜ 未开始（依赖 P1 真机验证通过） |
| P3 | 收尾：版本 1.0.3 → 1.1.0、CHANGELOG、提交推送 | ✅ 完成 |

---

## P1: 核心一次性提醒 + 验证 ⬜

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| 1.1 | `util/ReminderSidecar`：JSON↔Map、字段默认值 | ⬜ | 纯函数，可单测 |
| 1.2 | `util/ReminderText`：通知标题（首行+剥 ZWSP）、贪睡计算 | ⬜ | 纯函数，可单测 |
| 1.3 | `reminder/AlarmScheduler`：`setAlarmClock` set/cancel，`FLAG_IMMUTABLE` 显式 PI | ⬜ | 免 SCHEDULE_EXACT_ALARM |
| 1.4 | `reminder/NotificationHelper`：新建「提醒」`IMPORTANCE_HIGH` 渠道 + 通知构建（深链+贪睡+完成） | ⬜ | 不复用 write_feedback |
| 1.5 | `reminder/ReminderAlarmReceiver`：到点发通知；处理贪睡/完成（显式组件、`exported=false`、无 intent-filter） | ⬜ | |
| 1.6 | `reminder/ReminderBootReceiver`：`BOOT_COMPLETED` 重注册 + 补发（解锁后；先置 fired 再发，幂等） | ⬜ | `exported=true` + 过滤器 + RECEIVE_BOOT_COMPLETED |
| 1.7 | `AndroidManifest.xml`：加 `RECEIVE_BOOT_COMPLETED`、`POST_NOTIFICATIONS`、`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`；注册 2 receiver | ⬜ | 不加 WAKE_LOCK；不加 SCHEDULE_EXACT_ALARM |
| 1.8 | sidecar ↔ Markdown 对账：每次 `refreshEntryCount()` 删孤儿（加锁） | ⬜ | 设计 §5-5 |
| 1.9 | `clear()`/归档联动清闹钟：下沉文件层（cancel-all + 清 sidecar） | ⬜ | 设计 §5-4；现 clear() 在 MainScreen.kt:726 |
| 1.10 | 覆盖式重设写整对象；`POST_NOTIFICATIONS` 拒则拦设 | ⬜ | 设计 §5-10、§5-11 |
| 1.11 | 徽标：sidecar→`StateFlow`，`EntryCard` 订阅 | ⬜ | 不依赖文件轮询；设计 §5-6 |
| 1.12 | 深链：nav `"main"` 加 `?ts=`；上提 `listState`；等加载再滚；缺失 Toast | ⬜ | 设计 §5-7；listState 在 EntryListScreen.kt:79 |
| 1.13 | UI：详情弹窗 C「设提醒」主按钮（chips+日期时间选择器、覆盖确认）+ A 缩小为 TextButton | ⬜ | D1 |
| 1.14 | 验证套件：电池白名单 `isIgnoringBatteryOptimizations`+一键修；通知检查；**手动测试按钮** | ⬜ | D3 |
| 1.15 | 单测：sidecar、对账、错过补发、贪睡、标题构建 | ⬜ | 纯 JVM（Robolectric 不可用） |
| 1.16 | **真机验证（realme RMX3560）**：(a) 手动测试按钮确认**短时能响**；(b) **重启验证**——设 ~2 分钟提醒→重启→解锁后看是否补发/到点响；(c) 到点响、贪睡、点按回记录、徽标 | ⬜ | **P2 的前置门槛**；测试按钮 ≠ 重启存活，(a)(b) 都要做 |

---

## P2: 重复提醒（每天/每周） ⬜（依赖 P1 真机通过）

> **进入 P2 前先做**：重复的详细设计 + 单独评审（关机跨多天：补发一次还是跳过？取消单次 vs 整条？跨时区/夏令时？）。下表为粗粒度占位。

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| 2.1 | sidecar 加 `recurrence`（NONE/DAILY/WEEKLY） | ⬜ | |
| 2.2 | `ReminderText.nextOccurrence(triggerAt, recurrence)` 纯函数 + 单测 | ⬜ | 跨时区/夏令时 |
| 2.3 | 到点后按 recurrence 计算下一次并重注册（保留单次触发语义） | ⬜ | |
| 2.4 | UI 时间选择加「重复：无/每天/每周」 | ⬜ | |
| 2.5 | 取消语义：取消整条（删 key + cancel PI） | ⬜ | |
| 2.6 | 真机验证每日/每周触发 | ⬜ | |

---

## P3: 收尾 ⬜

| # | 任务 | 状态 | 备注 |
|---|------|------|------|
| 3.1 | 版本 1.0.3 → 1.1.0（versionCode 4 → 5）；CHANGELOG 条目 | ⬜ | minor（实质新模块） |
| 3.2 | 提交 + 推送 origin/main（沿用项目直推 main 惯例） | ⬜ | |
| 3.3 | 归档：本计划完成后移入 `docs/plans/completed/`（仿 v1.1 收尾） | ⬜ | |

---

## 计划新增文件结构

```
src/main/java/com/yiyue31/android/appendo/
├── reminder/
│   ├── AlarmScheduler.kt          # setAlarmClock set/cancel
│   ├── ReminderBootReceiver.kt    # BOOT_COMPLETED
│   ├── ReminderAlarmReceiver.kt   # 到点发通知 + 贪睡/完成
│   └── NotificationHelper.kt      # 提醒渠道 + 通知构建
├── util/
│   ├── ReminderSidecar.kt         # sidecar JSON↔Map + 对账/错过纯函数
│   └── ReminderText.kt            # 标题/贪睡/下一次触发（纯）
└── ui/
    ├── MainScreen.kt              # 改：设提醒主按钮 + A 缩小 + 深链
    └── EntryListScreen.kt         # 改：上提 listState + 徽标

src/test/java/com/yiyue31/android/appendo/util/
├── ReminderSidecarTest.kt
└── ReminderTextTest.kt
```

---

## 冷启动续作说明

1. 先读 `docs/design/local-reminder-design.md`（完整设计 + 评审记录 + 诚实承诺）。
2. 看本文件状态列：⬜ 未做、🔄 进行中（继续该项）、✅ 已完成（跳过）、⏸ 阻塞（先解阻塞）。
3. **关键前置**：P1 完成后必须先在 realme 真机用「手动测试按钮」确认 `setAlarmClock` 能响，再开 P2。
4. 已锁定的关键决策（勿反复）：调度用 `setAlarmClock`（非 setExactAndAllowWhileIdle）；sidecar 存 SharedPreferences（不碰 Markdown）；A 缩小为次要按钮、C 为主；v1 含每日/每周重复；可靠性承诺为"可靠非 100%"。
5. A（添加到日历）已上线（1.0.3），代码在 `util/CalendarEntryMapper.kt` / `CalendarLauncher.kt`，C 不改动其逻辑。
