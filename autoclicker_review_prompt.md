# 审核任务：Android 计算器 App「连点辅助」无障碍服务——圆圈不出现的 Bug

你是一位资深 Android 工程师。请审阅下面这段自动连点（AutoClicker）功能的代码与问题描述，给出**根因确认、修复方案评审、边界风险、以及更优替代方案**。请用中文或英文回复均可。

---

## 1. 项目背景

- 一个 Android 计算器 App（`org.solovyev.android.calculator`），新增了「连点辅助」功能。
- 实现方式：一个 `AccessibilityService`（`AutoClickerService`）在收到开启指令后，用 `WindowManager.TYPE_APPLICATION_OVERLAY` 在屏幕上挂出**两个可拖动圆圈**，点击圆圈即在圆圈中心做自动点击（间隔/时长可调）。
- 开关 UI：设置页 `PreferencesFragment` 里一个 `SwitchPreferenceCompat`（`auto_clicker_enabled`）。
- 设计约束（来自产品方，必须保留）：
  - **勾选状态必须与实际圆圈一致**：只有「无障碍已授权 + 双圆圈已挂出」时，开关才应是勾选状态；不允许出现「已勾选但圆圈没出来」。
  - **锁屏时自动取消勾选**：锁屏要停止连点并把 `enabled` 写回 false。
  - **未授权时拨开关**：不应勾选，只弹系统无障碍设置页。

## 2. 复现的 Bug

用户报告：**勾选「连点辅助」、去系统设置打开无障碍授权后，两个圆圈不出现**；在好几台安卓手机上都复现。

典型用户操作顺序（最自然的操作）：
1. 打开 App → 设置 → 连点辅助 → 拨动开关为「开」。
2. 因为此时无障碍未授权，被跳转到系统无障碍设置页去授权。
3. 用户授权后返回 App。
4. **期望：圆圈自动出现、开关变为已勾选。实际：圆圈不出现，开关仍是关闭。**

## 3. 当前关键代码

### AutoClickerService.java（节选）

```java
// 服务绑定
@Override
protected void onServiceConnected() {
    super.onServiceConnected();
    DisplayMetrics dm = getResources().getDisplayMetrics();
    circleSizePx = (int) (CIRCLE_SIZE_DP * dm.density + 0.5f);
    screenW = dm.widthPixels; screenH = dm.heightPixels;
    preferences = PreferenceManager.getDefaultSharedPreferences(this);
    preferences.registerOnSharedPreferenceChangeListener(this);
    refreshParamsCache();
    requestKeyFiltering();
    registerScreenOffReceiver();
    updateOverlayState();
}

// 监听 SP 变化
@Override
public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
    if (Preferences.AutoClicker.enabled.getKey().equals(key)) {
        updateOverlayState();
    } else if (Preferences.AutoClicker.interval.getKey().equals(key)
            || Preferences.AutoClicker.duration.getKey().equals(key)) {
        refreshParamsCache();
    }
}

// 决定是否挂/摘圆圈：只判断 enabled，没判断无障碍是否真的授权
private void updateOverlayState() {
    boolean isEnabled = Preferences.AutoClicker.enabled.getPreference(preferences);
    if (isEnabled) {
        addOverlay();
    } else {
        removeOverlay();
        stopClicking();
    }
}

// 唯一写入 enabled 的地方（带防回环）
private void setFeatureEnabled(boolean on) {
    if (preferences == null) return;
    boolean cur = Preferences.AutoClicker.enabled.getPreference(preferences);
    if (cur != on) {
        preferences.edit().putBoolean(Preferences.AutoClicker.enabled.getKey(), on).apply();
    }
}

// 挂圆圈
@SuppressLint("ClickableViewAccessibility")
private void addOverlay() {
    if (overlayViews[0] != null) return;
    windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
    LayoutInflater inflater = LayoutInflater.from(this);
    int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;
    int baseY = Math.max(100, screenH - 400);
    overlayViews[0] = inflater.inflate(R.layout.auto_clicker_circle, null);
    paramsArr[0] = baseParams(type);
    paramsArr[0].width = circleSizePx; paramsArr[0].height = circleSizePx;
    paramsArr[0].x = 100; paramsArr[0].y = baseY;
    overlayViews[1] = inflater.inflate(R.layout.auto_clicker_circle, null);
    overlayViews[1].setBackgroundResource(R.drawable.bg_auto_clicker_circle_blue);
    paramsArr[1] = baseParams(type);
    paramsArr[1].width = circleSizePx; paramsArr[1].height = circleSizePx;
    paramsArr[1].x = Math.max(100, screenW - 200); paramsArr[1].y = baseY;
    boolean added = true;
    for (int i = 0; i < overlayViews.length; i++) {
        final int index = i;
        overlayViews[i].setOnTouchListener(makeTouchListener(index));
        try {
            windowManager.addView(overlayViews[i], paramsArr[i]);
        } catch (Exception ignored) {
            added = false; break;
        }
    }
    if (added) {
        setFeatureEnabled(true);
    } else {
        removeOverlay();   // 注意：这里会 setFeatureEnabled(false)，把 enabled 重置掉
    }
}

// 摘圆圈
private void removeOverlay() {
    setFeatureEnabled(false);   // 摘圆圈 = 写 enabled=false
    if (windowManager == null) return;
    for (int i = 0; i < overlayViews.length; i++) {
        if (overlayViews[i] != null) {
            try { windowManager.removeView(overlayViews[i]); } catch (Exception ignored) {}
            overlayViews[i] = null; paramsArr[i] = null;
        }
    }
}

// 锁屏接收器
private void registerScreenOffReceiver() {
    screenOffReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                stopClicking();
                removeOverlay();   // 锁屏 -> 摘圆圈 + enabled=false
            }
        }
    };
    registerReceiver(screenOffReceiver, new IntentFilter(Intent.ACTION.SCREEN_OFF));
}

// 判断无障碍是否真的授权
public static boolean isAccessibilityEnabled(Context context) {
    try {
        String enabled = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null || enabled.isEmpty()) return false;
        String component = context.getPackageName() + "/" + AutoClickerService.class.getName();
        return enabled.contains(component);
    } catch (Exception e) { return false; }
}
```

### PreferencesFragment.java（节选）

```java
// 开关变化监听
enabledPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean enable = (Boolean) newValue;
        if (enable) {
            // 未授权 -> 弹设置，返回 false（不持久化，开关保持关闭）
            if (AutoClickerService.isAccessibilityEnabled(getActivity())) {
                return true;
            }
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Exception ignored) {}
            return false;
        }
        return true;   // 关闭 -> 持久化 false
    }
});

// SP 变化 -> 仅镜像开关视觉（用 setPersistent(false/true) 包裹避免 fragment 成为 enabled 的第二写入者）
@Override
public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
    if (Preferences.AutoClicker.enabled.getKey().equals(key)) {
        final SwitchPreferenceCompat ep = (SwitchPreferenceCompat) findPreference(key);
        if (ep != null) {
            boolean on = Preferences.AutoClicker.enabled.getPreference(preferences);
            if (ep.isChecked() != on) {
                ep.setPersistent(false);
                ep.setChecked(on);
                ep.setPersistent(true);
            }
            updateAutoClickerEnabledSummary(ep);
        }
    }
}

// onResume 时把开关状态与真相对齐
private void reconcileAutoClickerState() {
    final SwitchPreferenceCompat ep =
            (SwitchPreferenceCompat) findPreference(Preferences.AutoClicker.enabled.getKey());
    if (ep == null) return;
    boolean spOn = Preferences.AutoClicker.enabled.getPreference(preferences);
    boolean granted = AutoClickerService.isAccessibilityEnabled(getActivity());
    ep.setPersistent(false);
    if (spOn && !granted) {
        // 未授权 -> 清 enabled=false 并强制关闭
        preferences.edit().putBoolean(Preferences.AutoClicker.enabled.getKey(), false).apply();
        ep.setChecked(false);
    } else {
        ep.setChecked(spOn);
    }
    ep.setPersistent(true);
    updateAutoClickerEnabledSummary(ep);
}
```

> 说明：`fragment` 注入的 `SharedPreferences` 是 `PreferenceManager.getDefaultSharedPreferences(application)`，`service` 里是 `PreferenceManager.getDefaultSharedPreferences(this)`，二者指向同一文件、同一进程，因此 `onSharedPreferenceChanged` 能跨组件触发（已确认，不是两个文件不同步的问题）。

## 4. 我的根因假设

**根因：触发逻辑有缺口。** 在「未授权时拨开关」的设计里，`onPreferenceChange` 返回 `false` 且**不持久化** `enabled`（保持 false），只把用户导去授权。但授权完成后：

- 服务 `onServiceConnected()` 读 `enabled`（此时仍是 false）→ `updateOverlayState()` 走 `removeOverlay()` 分支 → 不挂圆圈。
- `reconcileAutoClickerState()` 只把开关视觉对齐，**从不调用 `addOverlay`**，且在 `spOn && !granted` 时还把 `enabled` 写成 false。
- **没有任何代码路径在「无障碍授权完成后」主动根据已有意图重新挂出圆圈。**

结果：用户走完「拨开关→授权→返回」后，`enabled` 始终为 false，圆圈永不出现。只有用户**再次手动拨一次开关**（此时已授权，`onPreferenceChange` 返回 true 持久化 true，服务才 `addOverlay`）才会出来——但用户并不知道要再拨一次，所以表现为「好几台手机都不行」。

补充：若用户是「先授权、再拨开关」，代码分析显示圆圈能出现（授权状态下拨开关走 `return true` → 服务挂圆圈）。所以问题集中在「先拨开关后授权」这条自然流程。

## 5. 我提议的修复方案（意图模型，请评审）

核心思路：**把 `enabled` 定义为「用户想开连点辅助」的意图（intent），圆圈是否真的挂出 = `enabled && 无障碍已授权`。** 开关勾选态镜像「圆圈是否真的挂出」（满足产品方约束）。这样授权完成后能自动挂出，无需用户再拨一次。

具体改动（4 处）：

**(1) `onPreferenceChange`：** 未授权时**仍然持久化 `enabled=true`（记录意图）**，然后弹设置、返回 false（不让框架再写一次、视觉保持关闭）；授权时直接 `return true`。
```java
public boolean onPreferenceChange(Preference preference, Object newValue) {
    boolean enable = (Boolean) newValue;
    if (enable) {
        if (AutoClickerService.isAccessibilityEnabled(getActivity())) {
            return true; // 已授权：框架持久化 true，服务挂圆圈
        }
        // 未授权：记录意图，授权后由服务自动挂出；视觉保持关闭
        preferences.edit().putBoolean(Preferences.AutoClicker.enabled.getKey(), true).apply();
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception ignored) {}
        return false;
    }
    return true; // 关闭：持久化 false
}
```

**(2) `updateOverlayState`：** 增加「无障碍已授权」闸门，使服务绑定时能自动挂出。
```java
private void updateOverlayState() {
    boolean isEnabled = Preferences.AutoClicker.enabled.getPreference(preferences);
    boolean granted = isAccessibilityEnabled(this);
    if (isEnabled && granted) {
        addOverlay();
    } else {
        removeOverlay();
        stopClicking();
    }
}
```

**(3) 开关镜像改为 `checkboxOn = enabled && granted`**（fragment 的 `onSharedPreferenceChanged` 与 `reconcileAutoClickerState`）：未授权时即使 `enabled=true` 也显示关闭，满足「未授权不勾选」约束；授权后自动翻 ON。
```java
boolean active = Preferences.AutoClicker.enabled.getPreference(preferences)
                 && AutoClickerService.isAccessibilityEnabled(getActivity());
ep.setChecked(active);
```
`reconcileAutoClickerState` 中 `spOn && !granted` 分支**不再把 `enabled` 写成 false**（要保留意图），只把视觉设为关闭并提示「授权后自动开启」。

**(4) `addOverlay` 失败不能清意图：** 当前失败路径调 `removeOverlay()` 会 `setFeatureEnabled(false)` 把 `enabled` 清掉。应改为只摘 view、不清 `enabled`（意图保留），并提示「无法显示圆圈，请检查悬浮窗权限」。建议把「摘 view」与「清 enabled」拆成两个方法。

请重点评审以下边界/风险：
- A. `addOverlay` 失败时若不清 `enabled`，会不会导致「意图为 true 但圆圈永远挂不出」的卡死？如何提示用户？
- B. `SCREEN_OFF` 仍要 `enabled=false`（产品方要求锁屏取消勾选）——与「保留意图」是否冲突？是否应区分「锁屏」与「未授权」？
- C. 服务 `onDestroy`（用户在设置里关闭无障碍）时 `removeOverlay` 会清 `enabled`——是否合理？还是应保留意图以便下次授权自动恢复？
- D. 在 `onPreferenceChange` 里手动 `apply()` 后又 `return false`，与框架持久化是否有重复/竞态？
- E. `TYPE_APPLICATION_OVERLAY` 由 `AccessibilityService` 添加，是否在所有 Android 版本/厂商 ROM 上都不需要 `SYSTEM_ALERT_WINDOW` 权限？是否存在用户报告中「连已授权拨开关都不出圆圈」的设备级原因（悬浮窗权限/厂商限制）？
- F. 是否有其它隐藏 bug（如 `setFeatureEnabled` 的 no-op 分支、listener 弱引用被 GC、双圆圈布局/位置等）？

## 6. 需要你回答的问题

1. 是否认同我的根因判断？有无我遗漏的更根本问题？
2. 上述「意图模型」修复是否可行？有无更简洁/更稳健的方案（例如用独立 `pending_enable` 标志而非复用 `enabled`）？
3. 逐项评估 A–F 边界风险，给出推荐处理。
4. 若你来改，请给出落地到具体方法的伪代码或注意点，便于我（另一位工程师）直接实施。
