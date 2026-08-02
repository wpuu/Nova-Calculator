【任务】
为 Android 应用「Nova Calculator」的"连点辅助"(accessibility auto-clicker)功能，重新设计错误诊断与自助修复机制。
当前设计有严重缺陷：大部分错误码把用户导向"复制诊断信息发给开发者"，这等于死路，真实用户会直接弃用功能。
请给出一个可落地的重构方案（精确到要改的文件、方法、逻辑），目标是：每一个错误码都给用户一条**在 App 内就能自己解决**的路径（一键跳转到对应系统设置 / 具体的分步操作指引 / 或自动重试自愈），彻底去掉"联系开发者 / 把 logcat 发我"这类死路。

【背景】
- 「Nova Calculator」是 Calculator++（作者 se.solovyev，包名 org.solovyev.android.calculator）的二开版。当前版本 2.3.12（versionCode 170）。
- 功能"连点辅助"：通过 AccessibilityService（AutoClickerService）在屏幕上叠加两个可拖动的圆圈，点击圆圈在两点间自动连点（模拟点击）。
- 叠加层用 WindowManager，API≥28 用 TYPE_ACCESSIBILITY_OVERLAY（无需 SYSTEM_ALERT_WINDOW 权限）。

【当前错误码设计】（定义在 AutoClickerService.java）
- FAILURE_NONE = 0
- 1 = 缺悬浮窗权限（SecurityException）→ 当前可自助
- 2 = BadTokenException → 当前"复制发给开发者"
- 3 = InvalidDisplayException → 当前"复制发给开发者"
- 4 = 未知 RuntimeException → 当前"复制发给开发者"
- 5 = 无障碍未真正开启 / 服务未绑定 → 当前可自助（跳无障碍设置）
- 6 = onServiceConnected 中 initService() 抛 Throwable（服务初始化崩溃）→ 当前"把 logcat 报错发我"（最致命，用户另一台非 EMUI 手机正卡在这）
- 7 = 看门狗超时仍未显示圆圈 → 当前"回报此状态 + 截图"

【UI 现状】（PreferencesFragment.java + preferences_auto_clicker.xml）
- 开关下方有诊断面板 Preference `auto_clicker_diagnostics`，仅当 code∈[1,7] 时可见，标题"⚠ 连点辅助未正常工作 · 错误码 X"，summary 显示 getFailureMessage(code)。
- 点击面板的处理（prepareAutoClicker 中）：
  - code==1 → startActivity(ACTION_MANAGE_OVERLAY_PERMISSION, package:本应用)  ✅ 真自愈
  - code==5 → startActivity(ACTION_ACCESSIBILITY_SETTINGS)  ✅ 真自愈
  - 其他(code 2/3/4/6/7) → copyDiagnosticToClipboard(code)：复制到剪贴板 + Toast"已复制诊断信息，可粘贴发给开发者"  ❌ 死路
- getFailureMessage(code) 文案：code6 写"请把 logcat 中 AutoClickerService 的报错发我"；code7 写"请回报此状态，并附上系统「无障碍」页面截图"——都是"联系开发者"性质。

【必须保留的 ROM 兼容约束】（改动绝不能破坏这些，否则在 EMUI/华为真机 TNY-AL00, Android 10 上会冻屏或失效）
- 叠加层类型：API≥P 用 TYPE_ACCESSIBILITY_OVERLAY；不要回退到需要 SYSTEM_ALERT_WINDOW 的方案。
- 连点手势必须是真实 1px 行程：path.moveTo(x,y); path.lineTo(x+1,y+1)；零长度 path 在 EMUI 会卡死 InputDispatcher 冻住整机输入。
- onInterrupt() 必须是 no-op（EMUI 连续派发手势会正常回调它，原 stopClicking 会误杀循环）。
- 连点循环用盲 handler.postDelayed 调度，勿依赖 GestureResultCallback（EMUI 只回调一次）。
- reconcileState() 真值：granted = serviceConnected || isAccessibilityEnabled(this)（服务已连接即视为授权，勿死依赖 ENABLED_ACCESSIBILITY_SERVICES 安全字符串——OEM ROM 格式差异会导致圆圈不显示）。
- 版本号规则：每次改源码必须递增 app/build.gradle 的 versionCode +1、versionName 末段 +1。

【重点：错误码 6 的真因与自愈设计】
- 6 只在 onServiceConnected() 的 `try { initService(); } catch (Throwable t) { lastFailure = 6; setEffective(false); }` 里产生。
- initService() 里唯一会抛且不被 addOverlayAtomically 捕获的步骤是：getResources().getDisplayMetrics()、LayoutInflater.from(this)、PreferenceManager.getDefaultSharedPreferences、registerOnSharedPreferenceChangeListener、三个 registerReceiver（SCREEN_OFF / STOP / RECONCILE）、以及最后的 reconcileState()。
- addOverlayAtomically() 内部已 catch 所有 addView 异常（映射到 1/2/3/4），所以 6 一定来自上述"初始化步骤"，与叠加层 addView 无关。
- 常见触发（非 EMUI ROM）：(a) 系统在某些进程态下 getResources()/getSystemService 抛 NPE；(b) 服务被 ROM 快速重建，第二次 registerReceiver 抛 IllegalArgumentException: Receiver already registered；(c) 早期某些 ROM 的 NotificationManager/渠道创建时序问题。
- 请设计：
  1. 防御性初始化：把 initService() 的每一步拆开独立 try/catch，单步失败不连累整体；receiver 注册前先 try-unregister 防重复；让服务即使某步失败也尽量走到 reconcileState()。
  2. 优雅降级而非永久失败：若整体 init 仍失败，不要只设 6 就 setEffective(false) 完事；改为 (a) 记录 6，(b) 自动重试（postDelayed 重新 initService / reconcile），(c) 面板文案改为可操作指引而非"发 logcat"。
  3. 面板对 6 给出真自愈入口：点面板 → 跳 ACTION_ACCESSIBILITY_SETTINGS（引导用户"关闭并重新打开本应用的无障碍开关"，因为多数初始化崩溃靠重连服务即可恢复）+ 文案"若仍无效，请重启手机后重试；本应用会在无障碍重新连接时自动重试"。可附带一个"立即重试"按钮（发 ACTION_RECONCILE 广播）。

【对全部 7 个码的统一要求】
请给出每个码的"自助路径"设计：
- 1 → 已有一键跳悬浮窗权限（保留）。
- 2/3 → 系统限制类：文案给明确分步（"重启手机后重试；若仍失败，到 设置→应用→Nova Calculator→强制停止→再开无障碍"），并支持一键跳无障碍设置 + 一键"立即重试(reconcile)"。
- 4 → 未知：保留复制诊断的能力，但作为"最后手段"按钮，同时给出通用自查步骤（重启 / 重开无障碍 / 清缓存），而非唯一选项。
- 5 → 已有一键跳无障碍设置（保留）。
- 6 → 上述防御 + 重试 + 跳无障碍 + 立即重试（重点）。
- 7 → 超时：文案"无障碍已开但圆圈未出"，给分步（重开无障碍开关 / 重启 / 检查是否用了第三方桌面或手势导航冲突）+ 一键重试(reconcile)。

【交付物格式要求】
请输出一份**可直接实施的改动清单**，结构如下：
1. AutoClickerService.java 改动：具体方法名、如何改 initService 防御、如何加重试、getFailureMessage 各码文案（给出新中文文案，去掉"发我 logcat / 联系开发者"措辞，改为操作指引）、是否需要新常量/新方法。
2. PreferencesFragment.java 改动：diagnostics 面板点击逻辑如何扩展（每个 code 对应 跳转 / 重试 / 复制 的精确分支），是否需要新增"立即重试"Preference 或按钮、如何发 ACTION_RECONCILE 广播触发重试。
3. preferences_auto_clicker.xml（如有）/ strings.xml：需要新增哪些字符串（如"立即重试"按钮文案、各码新指引），给出 key 与默认文案。
4. 明确哪些现有行为（EMUI 正常路径）必须原样保留，避免回归。
5. 按优先级排序（P0 先解决 6 的自愈，再 2/3/4/7，1/5 已 OK 只需核对）。
6. 说明如何在真机（EMUI Android 10）与"另一台非 EMUI 手机"上验证每一条改动。

请只输出方案与代码级改动说明，不要写完整文件，我会在本地据此实现并构建。
