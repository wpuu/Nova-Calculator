# 连点辅助错误码自助修复 —— 请给我完整实施计划

> 说明：本应用源码较大，无法粘贴。以下所有事实均来自我对实际源码的核查，已浓缩为可直接设计的架构描述。请基于这些信息直接给出**可实施的完整方案（精确到文件 / 方法 / 代码片段 / 资源 key）**，无需我额外提供源码。

## 一、项目背景
- 应用：Nova Calculator（Calculator++ 二开版），包名 `org.solovyev.android.calculator`
- 当前版本：2.3.12，versionCode 170
- 构建：Gradle + Java/Kotlin 混编，compileSdk=35，minSdk=21，targetSdk=35
- 功能：AccessibilityService `AutoClickerService`，在屏幕上显示两个可拖动圆圈，点击圆圈中心后在两点间自动连点
- 窗口类型：API>=28 使用 `TYPE_ACCESSIBILITY_OVERLAY`，**严禁改成 SYSTEM_ALERT_WINDOW**

## 二、必须保留的 ROM 兼容硬约束（任何方案都不得破坏）
1. API>=P 继续用 `TYPE_ACCESSIBILITY_OVERLAY`，不新增 SYSTEM_ALERT_WINDOW 权限。
2. 连点手势必须是真实 1px 行程：`path.moveTo(x,y); path.lineTo(x+1,y+1);`，禁止零长度 Path（部分 EMUI 会卡死 InputDispatcher）。
3. `onInterrupt()` 必须 no-op，不得调用 stopClicking()。
4. 连点循环继续用盲 `handler.postDelayed` 调度，不依赖 GestureResultCallback。
5. 授权真值：`boolean granted = serviceConnected || isAccessibilityEnabled(this);`，不得改成只读 ENABLED_ACCESSIBILITY_SERVICES。
6. 若修改源码，必须递增 versionCode+1、versionName 末段+1（本次若实施应为 171 / 2.3.13）。

## 三、当前 7 个错误码的真实产生点（已读源码确认）
| 码 | 常量 | 数值 | 写入位置（方法） | 触发条件 |
|---|---|---|---|---|
| 0 | FAILURE_NONE | 0 | reconcileState 成功/关闭、watchdog overlayReady | 正常 |
| 1 | FAILURE_OVERLAY_PERMISSION | 1 | addOverlayAtomically() | SecurityException（加窗被拒） |
| 2 | FAILURE_BAD_TOKEN | 2 | addOverlayAtomically() | BadTokenException |
| 3 | FAILURE_INVALID_DISPLAY | 3 | addOverlayAtomically() | InvalidDisplayException |
| 4 | FAILURE_UNKNOWN | 4 | addOverlayAtomically() | 其他 RuntimeException |
| 5 | FAILURE_A11Y_OFF | 5 | noShowRunnable | !grantedNow \|\| !serviceConnected |
| 6 | FAILURE_SERVICE_CONNECT_FAILED | 6 | **onServiceConnected() catch** | initService() 抛 Throwable |
| 7 | FAILURE_TIMEOUT_NO_CIRCLES | 7 | noShowRunnable | 已授权已连接但 !overlayReady 且 lastFailure==NONE（看门狗 4s 超时） |

注：addOverlayAtomically() 内部已自行捕获加窗异常并映射 1/2/3/4，故 code 6 只来自 initService() 中**加窗之外**的初始化步骤。

## 四、核心问题（请重点解决）

### 问题 A：错误码 6 在 Android 13+ 设备上必现（已确认根因）
`AutoClickerService.initService()` 中 3 处广播注册使用了 **2 参数 `registerReceiver(receiver, filter)`**（分别是屏幕关闭、停止连点、RECONCILE 三个 receiver）。在 API>=33 设备上，Android 强制要求带 `RECEIVER_NOT_EXPORTED` / `RECEIVER_EXPORTED` 标志，否则直接抛：
```
java.lang.IllegalArgumentException: Missing broadcast flag for RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED
```
该异常在 initService() 第一个 registerReceiver 即抛出 → onServiceConnected 的 catch 捕获 → lastFailure=6 → 永久停摆，**且 reconcileReceiver 根本没注册，连"立即重试"广播都收不到**。
- 验证：华为 EMUI Android 10 不强制该标志 → 正常；另一台 Android 13+ 手机一启动就 code 6。现象完全吻合。
- 请确认此根因，并给出最小修复。

### 问题 B：其余 6 个码的 UI 是死路式交互
`PreferencesFragment.java` 诊断面板逻辑：
- 标题、`summary` 文案硬编码在 Java（`getFailureMessage(code)`），未用 strings.xml。
- 点击分发：code==1→跳 MANAGE_OVERLAY_PERMISSION；code==5→跳 ACTION_ACCESSIBILITY_SETTINGS；**code 2/3/4/6/7 全部只执行 `copyDiagnosticToClipboard(code)`**，Toast 文案为"已复制诊断信息，可粘贴发给开发者"。
- 即：除 1/5 外，用户遇到任何错误都只能"复制发给开发者"，没有任何自助路径 → 直接弃用功能。

### 现有可用的自愈机制（可复用，勿新增冲突）
- `AutoClickerService.ACTION_RECONCILE`：public static，显式 Intent（带 component 指向 AutoClickerService，并 `setPackage(packageName)` 防伪造），`reconcileReceiver` 在 initService 成功后注册，收到后 `handler.post(reconcileState)`。UI 用 `sendBroadcast(reconcileIntent)` 触发。
- 现有主线程 `handler`（Looper.getMainLooper()），可用于重试 postDelayed。
- `onSharedPreferenceChanged` 与 `onResume` 已能自动刷新开关、summary、面板可见性。

## 五、要求：为每个错误码设计 App 内自助路径
目标：**每个码都给用户在 App 内可执行的自助动作，禁止把"联系开发者 / 发送 logcat / 发截图 / 回报状态"作为唯一或主要出口**；复制诊断可作最后手段，但 Toast 须改为"诊断信息已复制，可用于系统排查或留存"。

请为每个码给出：新中文文案（短、明确、可执行）、一键动作、所需 Intent、是否需要"立即重试"、用户仍失败时的分步指引（重开无障碍 / 强制停止重启 App / 重启手机 / 清缓存但**不清数据**）。参考语义：
- 码1：系统拒绝窗口/叠加层；跳"显示在其他应用上层"（若系统有该开关），返回点"立即重试"；若系统无此开关则提示重开无障碍。注意本应用 API>=P 用 TYPE_ACCESSIBILITY_OVERLAY 多数设备不需该权限，文案勿承诺"开了悬浮窗就好"。
- 码2：窗口令牌暂时无效；"立即重试"；失败则重开无障碍/重启手机；必要时强制停止重开。
- 码3：当前显示区域不可用（分屏/投屏/旋转）；退出分屏投屏切主屏后"立即重试"；重开无障碍；重启。
- 码4：未知异常；"立即重试"；重开无障碍；强制停止重启；重启；清缓存（不清数据）；复制诊断仅作末项。
- 码5：无障碍未授权/未连接；跳 ACTION_ACCESSIBILITY_SETTINGS；返回点"立即重试"。
- 码6：初始化失败；**自动重试（带上限与退避）+ 立即重试 + 跳无障碍 + 提示重开服务 + 重启手机**；复制诊断仅末项。
- 码7：看门狗超时；"立即重试"；重开无障碍；退出分屏/投屏/第三方悬浮；检查后台/自启动限制；重启。

## 六、请产出以下交付物
1. **code 6 根因修复**：最小改动方案（新增 `registerReceiverSafe` 辅助方法、改 3 处调用），并确认 API<33 兼容性（避免 NoSuchMethodError）。
2. **初始化防御性拆分**（可选但建议）：initService() 拆独立步骤、单步失败不阻断整条、receiver 幂等注册（先 unregister 再 register + 标志位）、onDestroy 安全注销；动态 receiver 在 API>=33 用 `RECEIVER_NOT_EXPORTED`。
3. **自动重试机制**：失败后退避重试（建议上限 3 次、1s→2s→3s），onServiceConnected 再进入时取消旧 retry、reconcile 成功/onDestroy/onScreenOff 取消 retry、retry 期间不重复添加双圆圈、服务断开则停。请判断是否需要新增字段，及是否复用现有 `handler`。
4. **PreferencesFragment 改造**：重写诊断面板点击分发，为 code 2/3/4/6/7 增加"立即重试"（发 ACTION_RECONCILE）+ 跳无障碍；码4 保留复制诊断但非唯一；点击后刷新 enabled/lastFailure/summary/可见性；防重复点击去抖。
5. **XML / strings.xml 改动**：列出新增的 Preference key 与 string key 及中文默认文案（覆盖 auto_clicker_retry_now、auto_clicker_open_accessibility_settings、auto_clicker_open_overlay_settings、auto_clicker_reopen_accessibility_hint、auto_clicker_restart_app_hint、auto_clicker_restart_device_hint、auto_clicker_clear_cache_hint、auto_clicker_failure_1..7、auto_clicker_diagnostic_title、auto_clicker_diagnostic_copied 等）；说明 Preference 可见性如何按 code 控制；是否新增独立"立即重试"按钮 Preference。
6. **复制诊断功能重新定位**：复制内容仅含非隐私字段（错误码、SDK、厂商机型、serviceConnected/overlayReady/granted、最近异常类型摘要、重试次数），禁止相机/麦克风/图片/录音/其他 App 输入/个人隐私；Toast 改为"诊断信息已复制，可用于系统排查或留存"。
7. **ROM 兼容保护清单**：逐项确认上述 6 条硬约束不被破坏。
8. **版本号**：若方案涉及改源码，明确 versionCode 170→171、versionName 2.3.12→2.3.13。

## 七、请直接给出代码片段（示例骨架，供你确认/修正）
```java
// 注册兼容 API<33
private void registerReceiverSafe(BroadcastReceiver r, IntentFilter f) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED);
    } else {
        registerReceiver(r, f);
    }
}

// 退避重试（示意）
private static final int MAX_INIT_RETRIES = 3;
private static final long INIT_RETRY_BASE_MS = 1000L;
private int initRetryCount = 0;
private final Runnable initRetryRunnable = () -> {
    if (!serviceConnected || overlayReady) { initRetryCount = 0; return; }
    if (initRetryCount >= MAX_INIT_RETRIES) { initRetryCount = 0; return; }
    initRetryCount++;
    initService();
    if (!overlayReady && initRetryCount < MAX_INIT_RETRIES)
        handler.postDelayed(this, INIT_RETRY_BASE_MS * initRetryCount);
};
```

请确认上述根因是否成立、代码片段在 **Android 10 EMUI 与 Android 13+** 上是否安全（不违反第二节 6 条约束），并补齐全部交付物。我拿到方案后自行实施与真机验证。
