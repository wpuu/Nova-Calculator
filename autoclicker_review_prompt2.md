# 连点辅助（AutoClicker）重构后代码回归检查 —— 请另一个 AI 评审

你是 Android 无障碍/状态机方向的评审者。以下是已落地的一次重构的回归检查结果，请重点评审我标注的两个"明确缺陷"，并给出你的意见与最小修复建议。不要假设我之前的逻辑，请基于下面的代码与约束独立判断。

## 一、背景与状态模型（已落地）

Android 计算器 App 的"连点辅助"无障碍服务，原先把 `auto_clicker_enabled` 同时当作"用户意图"和"实际生效状态"，导致"先拨开关、后去授权"时圆圈永不出现的 bug。已重构为独立字段模型：

- `auto_clicker_intent`：用户意图，持久化。**写入方**：设置页 UI 拨动、`onScreenOff`（锁屏）。
- `auto_clicker_enabled`：实际生效状态，持久化。**唯一写入方**：`AutoClickerService`（通过 `setEffective()`）。设置页只能**镜像**它，不能直接写。
- `auto_clicker_last_failure`：失败码，诊断用。

服务内部状态机（`reconcileState()`）：
1. `intent == false` → 停点击、移除圆圈、清 effective；
2. 未授权 或 服务未连接 → 停点击、移除圆圈、清 effective；
3. 否则 `addOverlayAtomically()`；两个圆圈都成功才 `setEffective(true)`。
- `onServiceConnected()` 设 `serviceConnected=true` 后调 `reconcileState()`（授权完成后的唯一可靠触发点）。
- `onDestroy()` 清 effective、移除 View，**保留 intent**（便于重授权自动恢复）。
- `onScreenOff()` 清 intent + 清 effective（产品要求锁屏取消勾选）。
- `removeOverlayViewsOnly()` 只移除 View，**不写** intent/effective。
- `addOverlayAtomically()` 添加失败回滚已成功的圆圈并记录失败码。

约束（评审基准）：
- **`auto_clicker_enabled` 只能由 `AutoClickerService` 写入；**
- **两个设置入口（Java `PreferencesFragment`、Kotlin `SettingsActivity`）的开关都必须镜像 `effective`，而不是 `intent`。**

## 二、我发现的两个缺陷（请评审）

### 缺陷 D1：存在第二个 `enabled` 写入者
文件 `app/src/main/java/org/solovyev/android/calculator/CalculatorApplication.java`，第 193 行（位于 `onPreCreate()`，每次 App 冷启动执行）：

```java
// Reset auto clicker switch on app start — user requested it to be always off on start.
prefs.edit().putBoolean(Preferences.AutoClicker.enabled.getKey(), false).apply();
```

问题：
- 这行在 `AutoClickerService` 之外直接写入了 `auto_clicker_enabled`，违反了"enabled 仅由服务写"的不变量。
- 时序上它在 `onServiceConnected()→reconcileState()` 之前执行。若服务已经在运行、圆圈在屏，此行先把 `enabled` 写成 false 而圆圈仍在，会造成短暂的"圆圈在屏但开关显示 OFF"错配。
- `reconcileState()` 之后会把它覆盖回正确值，所以是"能自愈但违背约束且存在瞬时错配"。

我的建议修复：删除此行，effective 的初始置位完全交给 `AutoClickerService.onServiceConnected()→reconcileState()`。如果确实需要在冷启动时让服务重新评估，也应只依赖服务自己的 reconcile，而不是 App 层越权写 enabled。

### 缺陷 D2：Kotlin 设置页显示 `intent` 而非 `effective`
文件 `app/src/main/java/org/solovyev/android/calculator/SettingsActivity.kt`，第 119 行（开关初始化）：

```kotlin
// 开关：连点器启用/禁用。这里直接表达"用户意图"(auto_clicker_intent)；
// 实际生效状态(auto_clicker_enabled)由无障碍服务统一写入并驱动。
val switchEnabled = findViewById<Switch>(R.id.switchAutoClickerEnabled)
switchEnabled.isChecked = Preferences.AutoClicker.intent.getPreference(prefs)
```

问题：
- 该行把开关的**勾选态直接绑定到 `intent`**，而 `PreferencesFragment.java:442` 是把开关绑定到 `effective`（`enabled`）。两个入口不一致。
- 由于显示的是 intent：用户拨一下开关，`intent=true` 后开关立刻显示 ON，即使此时**尚未授权无障碍、圆圈根本没出现**。这违背了"开关只镜像 effective（圆圈是否真的挂出）"的约定，正是评审当初反复警告的"已勾选但未生效"错配。
- 该 Kotlin 页拨动时也只写 `intent`（第 122 行），不写 enabled，这部分是对的；问题仅在"显示"。

我的建议修复：与 `PreferencesFragment` 对齐，开关显示应由 `intent && 已授权 && 服务已连接 && overlayReady` 决定（即等价于 effective），或改为监听 `auto_clicker_enabled` 变化来刷新 `isChecked`。注意 Kotlin 页目前没有像 Fragment 那样订阅 `OnSharedPreferenceChangeListener`，需要补上监听或显式在 `onResume()` 重新读取 effective 刷新。

## 三、需要你评审的问题

1. **D1**：删除 `CalculatorApplication.java:193` 是否安全？会不会破坏用户"启动后默认关闭"的预期？还是应该保留一个更安全的等价做法（例如只清 intent、不清 enabled；或仅在该值确实与服务状态冲突时才交由服务 reconcile）？
2. **D2**：`SettingsActivity.kt` 的开关应如何最稳妥地镜像 effective？是 (a) 在 `onResume()` 重新读 `enabled` 刷新 `isChecked`，还是 (b) 注册 `SharedPreferences.OnSharedPreferenceChangeListener` 监听 `auto_clicker_enabled` 变化，还是 (c) 用 `intent && isAccessibilityEnabled(this)` 近似？请考虑 Kotlin 页没有现有 listener、且 serviceConnected/overlayReady 是服务内存态（UI 不可直接读）的实际情况。
3. 这两个缺陷是否会影响"先拨开关后授权→圆圈自动出现""锁屏清意图""撤销授权保留意图自动恢复"三个主流程的正确性？还是仅影响开关视觉瞬时显示？
4. 除 D1/D2 外，你是否发现其他会破坏状态机一致性的隐藏问题（例如：`onSharedPreferenceChanged` 只监听 intent/interval/duration，不监听 enabled，是否存在 UI 不刷新或重复触发；`removeOverlayViewsOnly` 在 `reconcileState` 中被多次调用是否幂等安全；Kotlin 页与 Fragment 页并存是否会造成两份独立 Switch 状态不同步）？
5. 请给出针对 D1、D2 的**最小改动补丁**（含文件、行号、改动前后的关键代码），要求不改变现有状态模型与"enabled 仅服务写"的约束。

请把你的评审结论按"同意/不同意 + 理由 + 最小补丁"的结构返回，我会据此实施。
