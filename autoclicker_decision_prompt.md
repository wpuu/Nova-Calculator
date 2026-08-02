# Nova Calculator 连点辅助错误码自助修复 — 实施方案决策确认（请发给 luna）

你是 Android 方案评审。下面是需要你拍板的几个实施岔路。我已读源码核实根因与约束，请在每个决策点选一个并简述理由（≤3 行），最后对代码片段做 EMUI/Android13+ 安全把关。

## 项目背景
- 应用：Nova Calculator（Calculator++ 二开），包名 `org.solovyev.android.calculator`，当前 2.3.12 / versionCode 170。
- 功能：AccessibilityService 显示两个可拖动圆圈，点击圆圈中心后在两点间自动连点。
- 已确认根因（读源码核实）：错误码 6 在 **Android 13+ 必现**。原因：`AutoClickerService.initService()` 里三处 `registerReceiver` 用了 2 参数形式（L213/227/248），缺少 `RECEIVER_NOT_EXPORTED` 标志，系统抛 `IllegalArgumentException: Missing broadcast flag...`，被 `onServiceConnected()` 的 `catch (Throwable t)` 捕获并置 `lastFailure = FAILURE_SERVICE_CONNECT_FAILED`(6)。EMUI Android 10 测试机不强制该标志所以正常。工程 compileSdk=35、minSdk=21。
- 当前 UI 死路：诊断面板点击 code 2/3/4/6/7 全部走 `copyDiagnosticToClipboard`（“复制发给开发者”），用户无法自助。

## 必须保留的 ROM 兼容硬约束（任何方案都不得破坏）
1. API≥P 继续用 `TYPE_ACCESSIBILITY_OVERLAY`，**不新增 SYSTEM_ALERT_WINDOW**。
2. 连点手势须真实 1px 行程 `moveTo(x,y); lineTo(x+1,y+1)`，**禁止零长度 Path**（EMUI 会冻 InputDispatcher）。
3. `onInterrupt()` 必须 no-op，**不得调用 stopClicking()**。
4. 连点循环继续用盲 `handler.postDelayed`，**不依赖 GestureResultCallback**。
5. `granted = serviceConnected || isAccessibilityEnabled(this)` 必须保留。
6. 双圆圈原子添加+失败回滚、锁屏清意图、enabled 仅 service 写，均保留。

## 决策点（每项选一个）

**决策1【修复范围】**
- A. 只修根因：给三个 `registerReceiver` 加 `RECEIVER_NOT_EXPORTED` 标志（最小改动，约 1 个辅助方法 + 3 处调用）。预期 Android13+ 的 code 6 直接消失。
- B. 根因 + 防御性兜底：在 A 基础上，把 `initService()` 拆成独立步骤逐步 try/catch、receiver 幂等注册、加自动退避重试（上限 3 次，1→2→3s），使其他未知 ROM 的初始化异常也能自愈/提供重试。
你选 A 还是 A+B？理由？

**决策2【自动重试触发方式】（仅当决策1选 B 时生效）**
- A. 后台静默自动重试（用户无感，成功即显示圆圈）。
- B. 仅用户点“立即重试”才重试，不后台自动重试。
你选哪个？

**决策3【文案资源归属】**
- A. 把 7 条错误码文案和新增 UI 文案（立即重试 / 去无障碍设置 / 去悬浮窗设置 / 各码自助说明 / 复制成功提示）全部迁入 `strings.xml` 资源，`getFailureMessage` 改为带 `Context` 的签名。
- B. 保持 Java 硬编码（仅改写中文文案与 Toast），不新增 string key。
你选 A 还是 B？

**决策4【设置页新增入口】**
- A. 新增 3 个 Preference：`auto_clicker_retry`（立即重试，code 2/3/4/6/7 可见）、`auto_clicker_open_accessibility`（去无障碍设置，code 2/3/5/6/7 可见）、`auto_clicker_open_overlay`（去悬浮窗设置，仅 code 1 可见）。
- B. 只新增 1 个 `auto_clicker_retry`，其余动作（跳无障碍/悬浮窗）仍由诊断面板本体点击触发。
你选 A 还是 B？

**决策5【错误码 1 语义】（因 `TYPE_ACCESSIBILITY_OVERLAY` 实际不需要 SYSTEM_ALERT_WINDOW）**
- A. 保留跳 `MANAGE_OVERLAY_PERMISSION`，但文案加“若系统无此开关，请关闭并重新打开无障碍服务”兜底。
- B. code 1 不再跳悬浮窗设置，改为直接提示“重开无障碍服务”+ 立即重试。
你选 A 还是 B？

## 请安全把关的代码片段（来自我的 surgical 方案）
```java
// P0-A：API33+ 带标志注册，运行时分支避免 API<33 的 NoSuchMethodError
private void registerReceiverSafe(BroadcastReceiver r, IntentFilter f) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED);
    } else {
        registerReceiver(r, f);
    }
}

// P0-B：退避重试（复用现有主线程 handler）
private static final int MAX_INIT_RETRIES = 3;
private static final long INIT_RETRY_BASE_MS = 1000L;
private int initRetryCount = 0;
private final Runnable initRetryRunnable = new Runnable() {
    @Override public void run() {
        if (!serviceConnected) return;
        if (overlayReady) { initRetryCount = 0; return; }
        if (initRetryCount >= MAX_INIT_RETRIES) { initRetryCount = 0; return; }
        initRetryCount++;
        initService();
        if (!overlayReady && initRetryCount < MAX_INIT_RETRIES)
            handler.postDelayed(this, INIT_RETRY_BASE_MS * initRetryCount);
    }
};

// UI 分发（PreferencesFragment）
private void handleAutoClickerDiagnostic(int code) {
    switch (code) {
        case 1: openOverlaySettings(); break;
        case 5: openAccessibilitySettings(); break;
        case 2: case 3: case 6: case 7:
            requestAutoClickerReconcile(); openAccessibilitySettings(); break;
        case 4: requestAutoClickerReconcile(); showGeneralRecoveryDialog(); break;
    }
}
// requestAutoClickerReconcile 发显式 ACTION_RECONCILE 广播并 setPackage(getPackageName())
```
请确认以上在 Android 10 EMUI 与 Android 13+ 上都安全、是否违反上面 6 条硬约束；如有风险明确指出。

## 回复格式
请按“决策1: X；决策2: X；决策3: X；决策4: X；决策5: X；安全把关: …”简短回复。若需更完整上下文，可让对方把完整源码审查贴给你。
