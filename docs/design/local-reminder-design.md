# 本地提醒功能（方案 C：自建 AlarmManager） — 设计文档

> 日期: 2026-08-10
> 状态: ✅ Phase 1 已实现（v1.1.0，2026-08-11）；Phase 2 重复提醒已实现（v1.2.0，2026-08-12，**无独立详细设计文档**，事后现状记录见 architecture.md §5）
> 与实现的偏差（实现期漂移）：本文的 `ReminderSidecar`（util）实际落地为 `reminder/ReminderStore`（sidecar 单例，Compose State）+ `util/ReminderMetaCodec`（编解码）+ `util/ReminderLogic`（决策纯函数）；sidecar 由"单 JSON blob"改为 per-key 独立存储（避免读改写竞态）；文中引用的 `MainScreen.kt` 行号已失效
> 现状架构见 [../architecture.md](../architecture.md)（本文为历史设计记录）
> 关联: `docs/plans/reminder-plan.md`（任务进度，便于冷启动）

---

## 1. 功能概述

为 appendo 记录提供**本地原生提醒**：到点由 appendo 自己发通知（带 appendo 图标/品牌），点按回到该条记录，支持贪睡与（Phase 2）每日/每周重复。**完全本地私有**——不走日历、不上云。

与已上线的「添加到日历」（方案 A，`ACTION_INSERT` 拉起系统日历）并存：
- **A = 让这条记录进系统日历**（用户在日历里设时间与告警，fire-and-forget）。
- **C = 到点 appendo 本地提醒我**（appendo 拥有并跟踪提醒生命周期）。

C 是"真·提醒"；A 退为次要按钮（见 §7）。

---

## 2. 需求决策记录

| 决策点 | 结论 | 理由 |
|--------|------|------|
| 机制选型 | **C：自建 AlarmManager + 本 App 通知**（非 B 写日历 Provider，非 A 拉起日历） | 本地私有（B-可靠变体要上云，违背隐私内核）；appendo 品牌通知；点按可回记录 |
| 调度 API | **`AlarmManager.setAlarmClock`**（非 `setExactAndAllowWhileIdle`） | 免 `SCHEDULE_EXACT_ALARM` 权限与设置跳转；免 Doze 9 分钟限速（贪睡/密集提醒不被合并）；"用户闹钟"语义，OEM 高优先级保留，realme 上被杀概率更低 |
| 提醒基数 | 每条记录 1 个（覆盖式：重设即整体替换） | 模型简单；覆盖前弹确认 |
| 时间模型 | 一次性（Phase 1）；每日/每周重复（Phase 2） | 用户评审强烈要求"每天吃药/每周周报" |
| 时间输入 | chips（1h 后/今晚 8 点/明早 9 点/明天此时）+ Material3 日期时间选择器 | 速记 App 以快为先，chips 覆盖 80% |
| 开机恢复 | `RECEIVE_BOOT_COMPLETED` → 重注册全部未触发；错过的补发一次并标"已过期" | 提醒须跨重启存活 |
| 存储 | sidecar `SharedPreferences` JSON，key=记录时间戳 | 不污染 Markdown 正文；与 v1.1 数据完整性隔离一致 |
| 通知 | 新建 `IMPORTANCE_HIGH`「提醒」渠道；标题=内容首行（剥 ZWSP），展开=全文 | 现有 `write_feedback` 渠道未用且为 DEFAULT，不合适 |
| 点按 | 深链回 appendo 并滚到该记录（PendingIntent + nav `?ts=`） | A 的痛点：点按开日历回不到笔记 |
| 贪睡 | 通知 action：5/15/30 分/1 小时/明天 | 多档，非仅 +10 分 |
| 响后动作 | 通知 + 详情提供"完成/划掉" | 用户评审：不想响后只能手动删 |
| A 的定位 | **缩小为次要按钮**，C「设提醒」为主操作 | 避免 A、C 两个近义按钮并列致混淆/双响 |
| 验证 | 电池优化白名单自动检查+一键修；通知权限检查；**手动测试按钮（短时正证明）+ 重启验证（手动一次性）** | realme 杀后台是生死线；用户需自证"这台短时能响、重启后还能响" |
| 可靠性承诺 | "完成一次性设置后、可用测试提醒当场验证、对大多数用户可靠"，**不保证 100%** | 最激进 OEM 省电仍可能漏；诚实契约 |

---

## 3. 方案选型：为什么是 C

| 路 | 得到 | 代价 |
|---|---|---|
| **A 拉起日历**（已上线） | 零权限、不上云 | 不是真提醒：无跟踪/无徽标/点按进日历/手动两步 |
| **B 写日历 Provider** | 跟踪+徽标+去重；写到云日历则国产 ROM 可靠 | `WRITE_CALENDAR` 敏感权限；**内容上云**（违背隐私）；通知是日历的、点按进日历；B-本地日历同样有被杀风险 |
| **C 自建 setAlarmClock**（选定） | 完全本地私有；appendo 品牌通知；徽标/去重/贪睡/点按回记录；OEM 当"用户闹钟"高优先级 | 代码量最大；realme 需一次性自启动/电池白名单引导；不保证 100% |

诚实更正：此前"C vs B"曾把 B 描成"必上云"（稻草人，忽略了 B-本地日历）。但 B-本地日历在 realme 上**同样有"被杀/不响"问题**，而换 `setAlarmClock` 后 C 的可靠性反而可能**优于** B-本地。结论不变：**选 C**。

---

## 4. 架构与数据层

### 4.1 组件（新增 `reminder/` 包 + `util/` 纯逻辑）

| 组件 | 职责 |
|------|------|
| `ReminderSidecar`（util，纯） | SharedPreferences JSON ↔ `Map<String, ReminderMeta>`；对账、错过扫描等纯函数 |
| `AlarmScheduler`（reminder） | `setAlarmClock` 注册/按 key 取消；`FLAG_IMMUTABLE` 显式组件 PendingIntent |
| `ReminderBootReceiver`（reminder） | `BOOT_COMPLETED` → 重注册未触发 + 补发已错过（解锁后） |
| `ReminderAlarmReceiver`（reminder） | 到点 → 发通知；处理贪睡/完成 action |
| `NotificationHelper`（reminder） | 新建「提醒」`IMPORTANCE_HIGH` 渠道；构建通知（深链 + 贪睡 + 完成） |
| `ReminderText`（util，纯） | 通知标题构建：首行 + `EntryParser.stripIsolationMarkers`；贪睡/下一次触发计算（可单测） |

### 4.2 sidecar schema

```json
{
  "2026-08-10 14:30:00.123": {
    "triggerAt": 1754800000000,
    "fired": false,
    "snoozedUntil": 0,
    "recurrence": "NONE"
  }
}
```

- `key` = 记录时间戳（与 Markdown 条目同 key，删记录联动删 key）
- `triggerAt` = 绝对 epoch 毫秒（与时区无关；**禁止**从字符串 key 反推 epoch 比较）
- `recurrence` = `NONE`（Phase 1 恒为 NONE）/ `DAILY` / `WEEKLY`（Phase 2）

### 4.3 AlarmManager 用法

```kotlin
val info = AlarmManager.AlarmClockInfo(triggerAt, /*showIntent=*/ null)
alarmManager.setAlarmClock(info, pendingIntent)   // 免权限、免 Doze 限速
```

取消：`alarmManager.cancel(pendingIntent)`（同 PI 实例/等价 PI）。

---

## 5. 关键技术要点（评审已核实，实现必须遵守）

1. **`setAlarmClock` 取代 `setExactAndAllowWhileIdle`**：移除 `SCHEDULE_EXACT_ALARM` 及所有 `canScheduleExactAlarms()` 守卫；Doze 限速不再影响贪睡。
2. **PendingIntent 一律 `FLAG_IMMUTABLE`**（API 31+ 不加直接 `IllegalArgumentException`）。贪睡接收器自行从 extra 算 `now+分钟`，**不需要** `FLAG_MUTABLE`（MUTABLE 在通知 action 上是安全漏洞）。
3. **接收器暴露**：`ReminderAlarmReceiver` 用**显式组件** PendingIntent、`exported="false"`、**无 intent-filter**（否则任意 App 可伪造提醒）；`ReminderBootReceiver` `exported="true"` + `BOOT_COMPLETED` 过滤器 + 声明 `RECEIVE_BOOT_COMPLETED`。API 31+ 每个接收器必须显式 `android:exported` 否则装不上。
4. **`clear()` / 归档联动清闹钟**：当前 `MainScreen.kt:726` 的 `mdFile.clear()` 与归档后清空（`MainScreen.kt:719→726`）**不走 `deleteEntry`**，会泄漏所有闹钟。→ 清理逻辑下沉文件层（`clear()` 联动 cancel-all + 清 sidecar），或用统一写入 facade 包住所有变更。
5. **sidecar ↔ Markdown 对账**：2 秒轮询（`MainScreen.kt:249`）只比对文件 mtime 重解析，从不比对 sidecar。外部编辑/换 SAF 文件 → 孤儿。→ 每次 `refreshEntryCount()` 做 `sidecar键 − 已解析时间戳` 差集，cancel+remove 孤儿；读改写 JSON 加锁（`SharedPreferences.apply()` 对 RMW 非原子）。
6. **徽标走 sidecar StateFlow**：触发只改 sidecar、不动 Markdown 文件 mtime → 现有轮询不会刷新徽标。→ `ReminderSidecar` 暴露 `StateFlow<Map<String, ReminderMeta>>`，卡片订阅，**不**依赖文件轮询。
7. **深链**：nav `"main"` 加 `?ts={ts}`（仿 `archive_detail/{filePath}`）；**上提** `EntryListScreen` 内部的 `listState`（`EntryListScreen.kt:79`）到 `MainScreen` 才能调 `animateScrollToItem`；冷启从通知进来时列表异步为空，需 `LaunchedEffect(ts)` 等条目出现再滚；条目已被删/归档 → Toast「条目已不存在」，勿静默。
8. **开机补发只在"解锁一次后"**：sidecar 是 CE 加密存储（`MODE_PRIVATE`），`BOOT_COMPLETED` 在首次解锁后送达。→ **不**用 `directBootAware`/`LOCKED_BOOT_COMPLETED`；文档与 UI 须说明"开机后需解锁一次才会补发错过的提醒"。
9. **补发幂等**：boot 接收器内，对 `triggerAt <= now && !fired` 的项**先置 `fired=true` 并持久化，再发通知**（防进程重启中途二次发送）；`triggerAt > now` 的重注册。
10. **覆盖式重设写整对象**：重设写全新的 `{triggerAt:new, fired:false, snoozedUntil:0, recurrence}`，**不**字段合并（否则残留 `fired=true` 让 boot 路径误判已发）。
11. **`POST_NOTIFICATIONS` 拒绝则拦设**：API 33+ 无此权限 `notify()` 静默失败（含 boot 补发路径）。设提醒时若无通知权限 → 拦住并明示，**不让用户设哑提醒**。
12. **贪睡重注册**：接收器内用 `setAlarmClock` 再排（免权限）；取消旧 PI。

---

## 6. 验证方案（回应 D3："能不能当场确认"）

| 手段 | 能力 | 诚实边界 |
|------|------|----------|
| **手动测试按钮** | 排一个 ~1 分钟后的 `setAlarmClock` 测试闹钟，响了 = 此机**短时内能响**（覆盖：调度链路、前台触发、通知、`POST_NOTIFICATIONS`） | 仅**短时正证明**；**不证明**重启存活 / 隔夜长期存活（见下"完整验证"）。不响 = 被杀（沉默失败无信号），据"未响"开白名单后重试 |
| **电池优化自动检查** | `PowerManager.isIgnoringBatteryOptimizations(pkg)`；未加白名单 → 一键 `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`（realme 上最大杠杆，常顺带解决自启动） | — |
| **通知权限自动检查** | `areNotificationsEnabled()`；未开 → 拦设并引导 | — |
| **OEM 自启动** | **无法自动检测**（无公开 API），仅按机型给文字引导 | 不能确认是否已开 |

> **诚实上限**：自动检查只能查"可查的风险因子"（电池白名单、通知权限）；**无法自动预测"会不会响"**——被杀的提醒接收器不运行、无痕迹。
>
> **短时 vs 完整验证**（关键：测试按钮响了 ≠ 重启后还会响）：
> - **短时正证明** = 手动测试按钮（~1 分钟后响）。证明：调度链路、前台触发、通知、`POST_NOTIFICATIONS`。
> - **重启存活验证**（手动，一次性）：设一条 ~2 分钟后的真提醒 → 立即重启手机 → 解锁后看是否补发/到点响。证明：`BOOT_COMPLETED` 重注册、`setAlarmClock` 跨重启存活。
> - **隔夜长期存活**：无法可靠自测（依赖 OEM 长期不杀）；只能靠电池白名单 + 自启动引导降低概率，**不保证**。

---

## 7. UI 层设计

### 7.1 详情/编辑对话框（`MainScreen.kt`，现有 `showDetailDialog`）

```
AlertDialog "编辑条目"
├── 内容区（verticalScroll）
│   ├── Text: 时间戳
│   ├── OutlinedTextField: editContent（现有）
│   ├── [主] Button「设提醒」(C) → 打开时间选择（chips + 日期时间）
│   └── [次] TextButton「添加到日历」(A，缩小，icon+小字) → 现有 ACTION_INSERT
├── 保存 / 取消（现有）
```

- C「设提醒」为**主操作**（醒目 Button）；A 缩为**次要 TextButton**（D1）。
- 设提醒覆盖前弹「已有提醒（明天 09:00），替换？」（D 决策）。

### 7.2 时间选择（C 触发后）

chips：`1 小时后 / 今晚 8 点 / 明早 9 点 / 明天此时` + 「自定义」→ Material3 `DatePicker` + `TimePicker`（AndroidX，无新依赖）。过去时间拒绝。

### 7.3 徽标（`EntryListScreen` / `EntryCard`）

记录卡片右下小钟铃；订阅 `ReminderSidecar` StateFlow，有未触发提醒即显示。

### 7.4 通知（`NotificationHelper`）

- 渠道：`reminder`，`IMPORTANCE_HIGH`（声/振/锁屏 heads-up）。
- 内容：标题 = `ReminderText.title(content)`（首行 + 剥 ZWSP），展开 = 全文。
- action：贪睡（5/15/30 分/1h/明天）、完成。
- 点按：深链 `?ts=<timestamp>` → 滚到记录。
- 错过补发：标题/样式标「已过期」。

---

## 8. 不修改 / 与 A 的关系

| 模块 | 说明 |
|------|------|
| `CalendarEntryMapper` / `CalendarLauncher`（A） | 保留；A 仅在 UI 缩小为次要按钮，逻辑不变 |
| Markdown 文件格式 / `EntryParser` 解析 | 不变；复用 `stripIsolationMarkers` |
| `FileRepository` / `ArchiveRepository` | 不变（sidecar 独立 SharedPreferences） |
| 第三方依赖 | **不新增**（全 AndroidX / 平台 API） |

---

## 9. 改动文件清单

| 文件 | 类型 | 说明 |
|------|------|------|
| `util/ReminderSidecar.kt` | 新增 | sidecar JSON↔Map + 对账/错过纯函数 |
| `util/ReminderText.kt` | 新增 | 通知标题构建、贪睡/下一次触发计算（纯） |
| `reminder/AlarmScheduler.kt` | 新增 | `setAlarmClock` set/cancel（FLAG_IMMUTABLE） |
| `reminder/ReminderBootReceiver.kt` | 新增 | BOOT_COMPLETED 重注册 + 补发 |
| `reminder/ReminderAlarmReceiver.kt` | 新增 | 到点发通知 + 贪睡/完成 |
| `reminder/NotificationHelper.kt` | 新增 | 「提醒」渠道 + 通知构建 |
| `ui/MainScreen.kt` | 修改 | C 设提醒主按钮 + A 缩小；时间选择；深链 `?ts=` 接收 + 滚动 |
| `ui/EntryListScreen.kt` | 修改 | 上提 `listState`；徽标订阅 sidecar |
| `MainActivity.kt` | 修改 | nav `"main"` 加 `?ts=` 参数 |
| `AndroidManifest.xml` | 修改 | 新增 `RECEIVE_BOOT_COMPLETED`、`POST_NOTIFICATIONS`、`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`；注册 2 个 receiver |
| `util/MarkdownFileOperations.kt` + 两个实现 | 修改 | `clear()` 联动 cancel-all + 清 sidecar（下沉文件层） |
| `test/util/ReminderSidecarTest.kt` 等 | 新增 | sidecar、对账、错过补发、贪睡、标题构建纯函数单测 |

---

## 10. 测试要点（Robolectric 不可用，仅纯 JVM）

可单测的纯逻辑：
- `ReminderSidecar`：JSON↔Map 序列化、字段默认值。
- 对账：`reconcile(sidecarKeys, entryTimestamps)` → 孤儿差集。
- 错过扫描：`findMissed(map, now)` → 需补发项；`advanceToBooted(map, now)` → 先置 fired 的幂等规则。
- 贪睡：`computeSnoozeTrigger(now, minutes)`。
- 标题：`ReminderText.title(content)` → 首行 + 剥 ZWSP（多行/纯链接/超长）。
- 下一次触发（Phase 2 重复）：`nextOccurrence(triggerAt, DAILY/WEEKLY)`。

不可单测（仪器/手测）：`AlarmManager`、receiver、`NotificationManager`、PendingIntent、NavController。

---

## 11. 评审记录摘要（三种角色，2026-08-10）

- **工程师**：头号发现 = 改用 `setAlarmClock`（已采纳，§5-1）；并抓出 clear() 泄漏、sidecar 对账缺失、FLAG_IMMUTABLE、接收器暴露、徽标过期、深链三 blocker、开机补发幂等于 CE 存储、覆盖写整对象、POST_NOTIFICATIONS 静默等（均纳入 §5）。
- **产品经理**：可靠性当上线门槛（→ §6 验证套件）；A/C 共存混淆（→ D1 缩小 A）；权限墙（→ setAlarmClock 塌缩）；缺成功指标/kill-switch；NL 时间解析作差异化（→ 列为 v1.2 候选，不在本期）。
- **用户**：自启动弹窗看不懂、重要事信不过（→ §6 手动测试按钮 + 大白话文案）；要重复（→ D2 Phase 2）；要响后"完成"（→ §7）；错过提醒别硬弹（→ 标"已过期"）。

---

## 12. 风险与诚实承诺

1. **realme 可靠性**：即便 `setAlarmClock` + 电池白名单 + 自启动引导，最激进省电设置仍可能漏。承诺 = "完成一次性设置后、可用测试提醒当场验证、对大多数用户可靠"，**非 100%**。
2. **沉默失败不可测**：被杀的提醒无信号，只能靠手动测试按钮给正证明、靠用户据"未响"反推。
3. **复杂度**：Phase 1 约 6 个新类 + manifest/nav/UI 改动 + 单测；Phase 2 再加重复逻辑。建议分阶段，先验证核心能响再叠重复。

---

## 13. 分阶段

- **Phase 1（核心一次性提醒 + 验证）**：§4 组件、§5 全部要点、§6 验证套件、§7 UI（不含重复）。**先在 realme 上用测试按钮确认能响，再进 Phase 2。**
- **Phase 2（重复）**：sidecar `recurrence` 字段、每日/每周下一次触发计算、取消整条 vs 取消单次语义。**Phase 2 进入前需单独做详细设计 + 评审**（关机跨多天：补发一次还是跳过？取消单次 vs 整条？跨时区/夏令时？），当前文档此部分仅为粗粒度占位。

详见 `docs/plans/reminder-plan.md`。
