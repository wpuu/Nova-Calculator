# 自动点击器最小代码修改 — 实施报告（2026-07-18）

> 依据上一轮只读审计（`audit_2026-07-18.md`）确认的 4 个问题 + 音量键日志增强，仅做最小代码修改，未做无关重构。修改前已逐文件核对实际源码，与审计描述一致。

## A. 修改概览与范围确认
- **范围**：`AutoClickerService.java`（P1）、`res/menu/main.xml`（P2）、`CalculatorActivity.java`（P2/P3/P4）、`VideoRecorderManager.kt`（P3/P4 就绪检查 + Java 互操作重载）。
- **新增/未做**：未新增任何审计范围外功能；未引入新的 `SYSTEM_ALERT_WINDOW` 依赖；未做全量重构。
- 修改前逐文件核对了实际代码（行号与审计一致，无矛盾），再执行修改。

## B. 实际代码与审计一致性核对
- 圆圈点击原用 `paramsArr[idx].x + circleSizePx/2`（偏移、偏上）→ 已确认。
- 高级设置菜单项 `menu_hidden_settings` 存在且可见 → 已确认。
- 110 照片：原在权限/就绪检查前无条件 `flashSecretRecordingUI()`（伪成功）→ 已确认。
- 112/113/114/115 快捷键：原 `onSecretCodeEvent` 对 AUDIO/VIDEO 等分支静默 return 或无动作 → 已确认。
- 构建环境：沙箱无 JDK、`gradle-wrapper.jar` 损坏、`services.gradle.org` 不可达 → 已确认，已用 `winget` 装 JDK 17 + 本地缓存 Gradle 8.7 绕过。

## C. 逐条修改说明

### P1 — 圆圈点击位置偏移
- 文件：`AutoClickerService.java`（`clickRunnable`，L505–530）。
- 改法：弃用 `paramsArr[idx].x + circleSizePx/2`；改用 `overlay.getLocationOnScreen(location)` 取真实屏幕坐标 + `width/2, height/2` 作为点击中心。
- 守卫：`overlayReady && overlay != null && overlay.isAttachedToWindow()` 且 `width>0 && height>0`，否则跳过本 tick（保持循环，不崩溃）。
- 依据：`dispatchGesture` 使用当前显示坐标，与 `getLocationOnScreen()` 同坐标系，无需手动密度/刘海校正。

### P2 — 高级设置可见 + 8888 唯一入口
- 文件：`res/menu/main.xml`（删除 `menu_hidden_settings` 项）；`CalculatorActivity.java`（`onMenuItemClick` 删除该分支；`onSecretCodeEvent` 的 SETTINGS 分支改用 `Intent.FLAG_ACTIVITY_SINGLE_TOP` 启动 SettingsActivity）。
- 未新增系统级全局按键监听；8888 仍为唯一入口（`secretCodeSettings="8888"` 保留）。

### P3 — 110 假拍照
- 文件：`CalculatorActivity.java` `takeHiddenPhoto`（L452–477）；`VideoRecorderManager.kt` `takeHiddenPhoto`（新增 `onNotReady`/`onFailed`；`imageCapture ?: run{onNotReady?.run(); return}`；`onError`→`onFailed?.run()`；`onCaptureInitiated.run()` 时再 `flashSecretRecordingUI()`）。
- 改法：先查 `isImageCaptureReady()`；未就绪则：已授权→`bindCamera(...){retryPendingAction()}` 一次，未授权→`cameraPermissionLauncher.launch(CAMERA)` 并 Toast「正在请求相机权限…」；真正就绪后才拍照，且成功反馈只在 `onCaptureInitiated` 回调触发，失败 Toast「拍照失败」。
- 修复点：移除「拍照前无条件闪 UI」的伪成功；补齐权限与 CameraX 就绪路径；失败可见。

### P4 — 112/113/114/115 快捷键失效
- 文件：`CalculatorActivity.java` `onSecretCodeEvent`（全部分支改为实际动作，无静默 return）；统一单槽 `pendingAction`（PENDING_NONE/PHOTO/VIDEO/AUDIO），`retryPendingAction()` 仅消费一次并重新派发；AUDIO_START 现走 `audioPermissionLauncher.launch(RECORD_AUDIO)` 请求麦克风权限。
- 视频：`startHiddenVideoRecording` 增加 `isVideoCaptureReady()` 检查 + `onNotReady` Toast「相机未就绪」；`recording != null` 时直接 return 防重入。

### P5（合规增强）— 音量键日志
- `AutoClickerService.java` `onKeyEvent`（VOLUME_DOWN）：加 `BuildConfig.DEBUG` 日志说明 best-effort，行为不变（`return true`）。

## D. 关键变更（文件:行号）
- `AutoClickerService.java`：L510/L520（`getLocationOnScreen`）、L523–525（DEBUG 日志）、L134/L549（`FLAG_REQUEST_FILTER_KEY_EVENTS` 仅音量键 best-effort）。
- `res/menu/main.xml`：删除 `menu_hidden_settings`（grep 0 匹配）。
- `CalculatorActivity.java`：L112–116（pendingAction 槽）、L120–130/L132–138（两个权限 launcher）、L401–450（`onSecretCodeEvent` + `retryPendingAction`）、L452–496（`takeHiddenPhoto`/`startHiddenAudioRecording`）、L370–398（`startHiddenVideoRecording`/`stop`）。
- `VideoRecorderManager.kt`：L26–44（3/4/5 参 `bindCamera` 重载）、L100–127（`startHiddenVideoRecording` onNotReady）、L137–179（`takeHiddenPhoto` onNotReady/onFailed）。

## E. 合规检查（对照约束）
- ✅ 未新增 `SYSTEM_ALERT_WINDOW` 依赖（仅 `AndroidManifest.xml` 既有声明 + 注释）。
- ✅ 标准权限流程（`ActivityResultContracts.RequestPermission`，每类单一 launcher）。
- ✅ 无完整 secret-code/photo/audio 内容日志；仅 `BuildConfig.DEBUG` 守卫的最小坐标/行为日志。
- ✅ `FLAG_REQUEST_FILTER_KEY_EVENTS` 维持 best-effort（仅音量键路径）。
- ✅ 单一 pending action 槽（复用，避免多个 boolean）。
- ✅ 无无限重试（bind/权限回调仅 retry 一次）。
- ✅ Activity 销毁后无回调（`isDestroyed()`/`isFinishing()` 守卫于所有回调入口）。

## F. 构建结果
- 命令（缓存 Gradle 8.7 + JDK 17，因 `gradle-wrapper.jar` 损坏且离线）：
  ```
  JAVA_HOME=/c/Users/Admin/AppData/Local/Programs/Microsoft/jdk-17.0.10.7-hotspot/
  GRADLE=/c/Users/Admin/.gradle/wrapper/dists/gradle-8.7-all/5dsutvqjvt53v782finjdccb40/gradle-8.7/bin/gradle
  "$GRADLE" assembleDebug --no-daemon
  ```
- 结果：**BUILD SUCCESSFUL**（38s，103 actionable tasks，9 executed，94 up-to-date）。
- APK：`E:/Nova Calculator/app/build/outputs/apk/debug/app-debug.apk`
- 首个错误（已修复）：4 处 Java 调 `bindCamera` 因 Kotlin 默认参数对 Java 不可见而编译失败（需要 5 参，找到 3/4 参）→ 在 `VideoRecorderManager.kt` 增加 3 参/4 参 Kotlin 重载转发到 5 参主函数，重建通过。

## G. 只读 grep 检查结果（全部通过）
- `menu_hidden_settings` → 0 匹配（已删除）✅
- `getLocationOnScreen` → 仅 `AutoClickerService` 新代码 ✅
- `paramsArr[].x` → 仅 `AutoClickerService` 布局/拖拽（合法，点击偏移已不再使用）✅
- `imageCapture ?: run{...return}` / `videoCapture ?: run{...return}` → 仅 `VideoRecorderManager.kt`（本次修改）✅
- `flashSecretRecordingUI` → 仅 L472（`onCaptureInitiated` 回调内）与 L553 定义 ✅
- `8888` / `secretCodeSettings` → 8888 入口完整保留 ✅
- `FLAG_REQUEST_FILTER_KEY_EVENTS` → 仅 `AutoClickerService` 音量键路径 ✅
- `SYSTEM_ALERT_WINDOW` → 仅既有 manifest 声明 + 注释，无新增依赖 ✅

## H. 未执行之事（严格遵守约束）
- 未运行 `adb install`/`uninstall`，未清除应用数据，未修改系统权限，未进行真机测试。
- 未新增任何只读审计范围外功能/重构。

## I. 真机验证待办（固定文本）
本次未执行真机验证；圆圈点击位置、权限弹窗时序、CameraX 无预览行为、相机占用、录像/录音文件落盘、8888 真机触发和不同 ROM 音量键行为仍需设备在线后验证。

## J. 交付物
- 修改文件清单（4 个）：`AutoClickerService.java`、`res/menu/main.xml`、`CalculatorActivity.java`、`VideoRecorderManager.kt`。
- 构建产物：`app-debug.apk`（路径见 F）。
- 本报告：`autoclicker_verify/implement_2026-07-18.md`。
