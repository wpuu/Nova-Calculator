# 连点辅助 / 隐蔽计算器 —— 当前方案与问题清单（待另一 AI 审核）

> 本文档分两部分：
> - **A 部分**：现状 + 4 个问题的根因分析（给你自己看的）。
> - **B 部分**：可直接复制、发给另一个 AI 审核的提示词（含全部上下文与约束）。
> 审核返回后，由我（WorkBuddy）按确认方案落地代码。

---

## A 部分：现状与问题根因分析

### 0. 上一轮已完成的修复（开关"关不掉"）—— 待你本地编译验证
- 根因：开关被绑定到 `auto_clicker_enabled`（生效态，仅服务能写），而不是 `auto_clicker_intent`（用户意图）。
- 改动（已写文件，未编译，本沙箱无 JDK）：
  - `res/xml/preferences_auto_clicker.xml`：Switch key `auto_clicker_enabled` → `auto_clicker_intent`
  - `preferences/PreferencesFragment.java`：switch 改绑 `intent`、`onPreferenceChange` 返回 `true`、listener 只改摘要
  - `SettingsActivity.kt`：`refreshAutoClickerSwitch()` 与拨动逻辑全部改读/写 `intent`；拨 ON 未授权时不再强制回弹
  - `autoclicker/AutoClickerService.java`：仅删除两处临时调试 Log
- 验证方式：`./gradlew assembleDebug` → `adb install -r ...` → 进连点辅助拨 OFF，确认开关落下、红蓝圆圈消失。

### 1. 问题①：两个圆圈"定时点击"的位置不在圆圈中间，而在圆圈外边的上边
**现象**：开启连点后，实际点击点偏到圆圈上方、圈外。

**涉及代码**：
- `AutoClickerService.java` L504-510（`clickRunnable` 内）：
  ```java
  int cx = paramsArr[idx].x + circleSizePx / 2;
  int cy = paramsArr[idx].y + circleSizePx / 2;
  dispatchClick(cx, cy);
  ```
- `AutoClickerService.java` L331-356 `createAndAddOverlay()`：window 的 `width/height` 被强制设为 `circleSizePx`（=26dp）。
- `res/layout/auto_clicker_circle.xml`：根 View 是 26dp 的 `ImageView`，`src=@drawable/ic_reticle_red/blue`。
- `AutoClickerReticle.java`：自定义 View 把圆环 + 中心点在 `(w/2, h/2)` 画出（看似居中）。

**候选根因（按可能性排序）**：
1. **手势坐标系与窗口坐标系原点不一致**：`dispatchGesture` 的坐标是屏幕坐标，但 `TYPE_ACCESSIBILITY_OVERLAY` 窗口的 `params.x/y`（gravity=TOP|START）在某些 ROM/有刘海/状态栏偏移下，与手势坐标存在偏移（尤其 Y 方向），导致点击点整体上移 → 正好表现为"圈上方"。
2. **`circleSizePx` ≠ 实际渲染尺寸**：窗口尺寸被设为 26dp，但若 `ic_reticle_*.png` 自带透明内边距、或 `scaleType=fitCenter` 缩放后可见圆环并不几何居中，则 `x+circleSizePx/2` 算出的点 ≠ 可见圆环中心。
3. **点击用了缓存的 `paramsArr[].x/y`，但窗口已被拖动/系统调整过**，与真实屏幕位置脱节。

**稳健修复方向**（推荐给审核 AI 采用）：点击时不依赖 `params.x + 尺寸/2` 推算，而是**在点击时刻用 `overlayViews[idx].getLocationOnScreen(int[])` 取真实屏幕坐标 + `view.getWidth()/2`、`view.getHeight()/2`** 算出圆心。这能一劳永逸消除上述三类不一致。

### 2. 问题②：高级设置不应可见，应仅在"任何情况下按 8888"才进入
**现象**：目前"高级设置"（即 `SettingsActivity` 控制台）有一处**可见入口**；用户要求彻底隐藏，只通过 8888 密令进入。

**涉及代码**：
- `CalculatorActivity.java` L291-293：选项菜单项 `R.id.menu_hidden_settings` → 直接 `startActivity(SettingsActivity)`（**可见入口，需删除/隐藏**）。
- `Keyboard.java` L328-332：`text.endsWith("8888")` 或 alt 码 → 发 `SETTINGS` 事件。
- `CalculatorActivity.java` L366-368：`SETTINGS` 事件 → `startActivity(SettingsActivity)`。
- `SettingsActivity.kt`：控制台本体（连点器、拍照密令、提取证据等）。

**需审核的设计点**：
- 删除 `menu_hidden_settings` 这个可见菜单项（或 `android:visible="false"`）。
- "任何情况下按 8888"：当前 8888 **只在计算器编辑器里逐位输入时触发**（Keyboard.onEditorChanged）。若要求"任何情况"（例如在结果显示态、或在 App 内其它界面）都能触发，需要把 8888 的捕获做成**全局**——最自然的是用已有的 `AutoClickerService`（AccessibilityService，已申请 `FLAG_REQUEST_FILTER_KEY_EVENTS`）在 `onKeyEvent` 里累积数字序列识别 8888（注意：a11y 未必能拿到全部数字键，需评估）；或在计算器主屏用全局文本监听。这点需审核 AI 给明确方案。

### 3. 问题③：110 确实让"后退键"暗 3 秒（像在拍照），但实际没拍照，提取证据时提示"沙盒为空"
**现象**：输入 110 → 有视觉反馈（擦除键变色/变暗约 2–3 秒，像快门）→ 但相册/沙盒里没有照片；点"提取证据"提示沙盒为空。

**根因（高置信）**：
- **视觉反馈与真实拍照解耦**：`CalculatorActivity.takeHiddenPhoto()`（L372-379）先 `triggerHapticFeedback(1)` + `flashSecretRecordingUI()`，**无条件先跑视觉**；之后才调 `VideoRecorderManager.takeHiddenPhoto()`。所以"暗 3 秒"一定会发生，与是否真拍到无关。
- **真实拍照静默失败**：`VideoRecorderManager.takeHiddenPhoto()`（L126-165）第一行 `val currentImageCapture = imageCapture ?: return`——`imageCapture` 为 null 就直接返回，**没有任何提示、不写文件**。
- **`imageCapture` 几乎必然为 null**：`CalculatorActivity.onCreate` L144-146 仅在**启动时已授予 CAMERA 权限**才调 `bindCamera()`；否则 `imageCapture` 永远不初始化。相机权限未授权/被拒/首次进入时，110 必然"假拍照"。
- **导出找不到文件**：`SettingsActivity.exportEvidenceSafely()`（L231-284）只列 `filesDir` 下 `*.dat`；既然没拍到，自然"沙盒为空"。

**修复方向**：
- 首次使用 110 前确保已申请并拿到 CAMERA 权限（若未授权，先请求权限并在授权后再 `bindCamera`，不要只在 onCreate 判一次）。
- `bindCamera` 失败 / `imageCapture` 为 null 时，给出 Toast 而非静默无操作；`takePicture` 的 `onError` 也要提示。
- 拍照成功回调里确认 `.dat` 文件确实写入 `filesDir`，再熄灭视觉反馈。

### 4. 问题④：110 之外的所有"快捷键"都没有效果
**现象**：除 110（有视觉）外，8888、112/113/114/115（录像/录音密令）、以及连点器的音量键等"全部没反应"。

**根因（分项）**：
- **112/113/114/115（录像/录音）**：与 110 同源——`VideoRecorderManager.startHiddenVideoRecording()`（L90-116）第一行 `val currentVideoCapture = videoCapture ?: return`，`videoCapture` 同样依赖 `bindCamera`（且需 RECORD_AUDIO 权限）。未授权/未绑定 → 静默返回，且这些路径**不调用 `flashSecretRecordingUI()`**，所以连 110 那种"暗一下"的反馈都没有 → 表现为"完全没效果"。
- **8888（SETTINGS）**：本应 `startActivity(SettingsActivity)`。若你测 8888"没效果"，可能因为它是**可见菜单已经能进**、或 8888 触发条件与你的输入场景不符（如你在结果显示态/非编辑器态输入）。需在审核中确认 8888 在计算器主屏逐位输入时是否真的发了 SETTINGS 事件（建议在 Keyboard 分发处加一次性日志验证）。
- **连点器音量键（VOLUME_DOWN）**：`AutoClickerService.onKeyEvent()`（L527-534）靠 `FLAG_REQUEST_FILTER_KEY_EVENTS`（L129-138 申请）。在华为 EMUI / 多数原生 ROM 上，**无障碍服务拿不到音量键事件**，导致"音量键开关连点"整条失效 → 也是"没效果"。

**修复方向**：
- 录像/录音与拍照统一：先确保权限 + `bindCamera` 成功再执行；失败要给可见反馈。
- 8888 进入控制台需与"问题②"一并处理（隐藏可见入口 + 确保 8888 在预期场景下能触发）。
- 音量键若确实拿不到（需真机验证），考虑改用计算器内 8888 之外的其它软触发，或在 `AutoClickerService` 里尝试拦截更多 key（受限于 ROM）。

---

## B 部分：可直接复制、发给另一个 AI 审核的提示词

> 复制下面 `===== 提示词开始 =====` 到 `===== 提示词结束 =====` 之间的全部内容即可。

===== 提示词开始 =====

你是一名资深 Android 工程师。请审核下面这个"隐蔽计算器 + 连点辅助无障碍服务"项目的 4 个已确认问题，并给出**可落地的代码修改方案**（精确到文件、方法、改动前后代码片段）。不要写完整文件，只给 surgical 改动。环境约束：项目用 Gradle + Java/Kotlin 混编，无特殊依赖，可正常 assembleDebug。

【项目背景】
包名 org.solovyev.android.calculator。连点辅助是一个 AccessibilityService（AutoClickerService），在屏幕上挂两个圆圈（红/蓝 reticle）做定时点击；还有一个"隐蔽计算器"外壳，通过计算器里输入数字密令触发隐藏功能（拍照110 / 录像112,113 / 录音114,115 / 进控制台8888）。

【问题1：连点圆圈"定时点击"点不中圆圈中心，偏到圆圈上方圈外】
- 当前点击坐标计算在 AutoClickerService.java 的 clickRunnable（约 L504-510）：
  cx = paramsArr[idx].x + circleSizePx / 2;  cy = paramsArr[idx].y + circleSizePx / 2;  dispatchClick(cx, cy);
  其中 circleSizePx = 26dp，窗口 width/height 被强制设为 circleSizePx。圆圈是 26dp 的 ImageView（src=ic_reticle_red/blue），自定义 View AutoClickerReticle 把圆环画在 (w/2,h/2)。
- 现象：实际点击点落在圆圈**上方、圈外**。
- 请分析"用 params.x + 尺寸/2 推算"为何会偏上，并给出稳健修复：建议改为点击时刻用 overlayViews[idx].getLocationOnScreen(int[]) 取真实屏幕坐标 + view.getWidth()/2、getHeight()/2 算圆心。请给具体改法，并说明是否还需处理 density/状态栏/刘海偏移。

【问题2：高级设置(SettingsActivity 控制台)不应可见，应仅在"任何情况下按 8888"才进入】
- 当前有两处进入方式：(a) CalculatorActivity 选项菜单项 menu_hidden_settings（约 L291-293）直接 startActivity(SettingsActivity) —— 这是可见入口，需删除/隐藏；(b) 在计算器编辑器逐位输入 8888 → Keyboard.java 发 SETTINGS 事件 → CalculatorActivity（约 L366-368）打开 SettingsActivity。
- 需求：①删除/隐藏可见菜单入口；②"任何情况下按 8888 才进入"——请评估：当前 8888 只在计算器编辑器输入时触发是否足够？若要求全局（App 内任意界面、甚至结果显示态）都能触发 8888，请设计最稳妥的实现（可复用已有 AutoClickerService 的 onKeyEvent + FLAG_REQUEST_FILTER_KEY_EVENTS 做数字序列累积识别，也请指出 ROM 兼容性风险；或给出更优方案）。

【问题3：110 让"后退/擦除键"变暗约 3 秒（像拍照），但实际没拍照，提取证据提示"沙盒为空"】
- takeHiddenPhoto()（CalculatorActivity 约 L372-379）先无条件跑 flashSecretRecordingUI()（变暗反馈，约 L444-467，延时 ~2-3 秒）+ 震动，之后才调 VideoRecorderManager.takeHiddenPhoto()。
- VideoRecorderManager.takeHiddenPhoto()（L126-165）第一行 `val currentImageCapture = imageCapture ?: return`——imageCapture 为 null 直接返回、不写文件、无提示。
- CalculatorActivity.onCreate（L144-146）仅在**启动时已授予 CAMERA 权限**才调 bindCamera()；未授权则 imageCapture 永远不初始化 → 110 必"假拍照"。
- 提取证据 exportEvidenceSafely()（SettingsActivity L231-284）只列 filesDir 下 *.dat；没拍到自然"沙盒为空"。
- 请给修复：①确保首次用 110 前已申请并拿到 CAMERA 权限，授权后再 bindCamera（不要只在 onCreate 判一次）；②imageCapture 为 null / bindCamera 失败 / takePicture onError 时要给 Toast 而非静默；③确认 .dat 真正写入 filesDir 再熄灭视觉反馈。给出 surgical 改动。

【问题4：110 之外的所有"快捷键"都没效果】
- 112/113/114/115（录像/录音）：VideoRecorderManager.startHiddenVideoRecording()（L90-116）`val currentVideoCapture = videoCapture ?: return`，同样依赖 bindCamera + RECORD_AUDIO 权限；未授权/未绑定 → 静默返回，且这些路径不调用 flashSecretRecordingUI()，所以连 110 那种视觉反馈都没有 → 表现为全无反应。
- 8888：应打开 SettingsActivity；若实测"没效果"需确认在计算器主屏逐位输入时是否真的发了 SETTINGS 事件（建议分发处加一次性日志验证），并与问题2一并修。
- 连点器音量键：AutoClickerService.onKeyEvent()（L527-534）靠 FLAG_REQUEST_FILTER_KEY_EVENTS（L129-138 申请）拦截 VOLUME_DOWN 切换连点；在华为 EMUI/多数原生 ROM 上无障碍服务拿不到音量键事件 → 整条失效。请评估是否值得保留该硬件键，或改用软触发。
- 请给统一修复：录像/录音与拍照一样先确保权限+bindCamera 成功再执行、失败可见反馈；并就 8888 与音量键给出明确处置建议。

【输出要求】
1. 对每个问题，先给"根因确认/存疑"，再给"具体改动（文件:行 + 改动前后代码）"。
2. 标明哪些需要真机验证、本机无法 100% 锁定的点。
3. 注意：AutoClickerService 是 AccessibilityService，连点圆圈用 TYPE_ACCESSIBILITY_OVERLAY（API28+，免 SYSTEM_ALERT_WINDOW）。任何改动不要破坏"免悬浮窗权限"这一前提。
4. 不要改动与这 4 个问题无关的逻辑。

===== 提示词结束 =====
