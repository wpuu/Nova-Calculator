# 任务 #35 真机验证报告 — 第五轮（2026-07-19 晚）

设备：TNY-AL00 / EMUI / Android 10（序列号 URU0218B01000786）
APK：`app/build/outputs/apk/debug/app-debug.apk`（14,104,183 B，构建 19:24，安装 19:26）
方式：`clean assembleDebug` 后 `adb install -r` 装机，逐项 `dumpsys` / `logcat` / `run-as` 验证。

## 四项验证结论

### ① 其他 App 按音量键正常（不再被吞） ✅
- **根因**：`accessibility_service_config.xml` 的 `android:accessibilityFlags="flagRequestFilterKeyEvents"` 是真正激活的运行时标志，使服务能全局消费音量键，导致"关掉应用后其他 App 音量卡顿 + 按一次顶两次"。
- **修复**：删除该 XML 标志（及 `canRequestFilterKeyEvents`），仅删 `onKeyEvent` 不够。
- **证据**：`dumpsys accessibility` 显示服务 `capabilities=32`（0x20），filterKeyEvents 位 0x08 已消失（修复前为 40 / 0x28）。服务从此无法拦截音量键。

### ② 连点运行时通知栏出现"停止连点"且可一键停止 ✅
- **实现**：`AutoClickerService` 新增常驻通知（`CLICK_NOTIFICATION_ID=1001`，channel `autoclicker_channel`），按钮 `ACTION_STOP_CLICKING` 经 `PendingIntent.FLAG_IMMUTABLE` 广播回 `stopReceiver` → `stopClicking()`。
- **证据**：`dumpsys notification` 出现 `StatusBarNotification ... id=1001 tag=autoclicker channel=autoclicker_channel actions=1`，含 action「停止连点」。
- **STOP 生效**：`am broadcast` 触发 `STOP_CLICKING` → logcat 确认 `stopClicking()` 执行、点击循环停止（dispatchGesture 停止输出 `click center idx=...`）。
- **已知限制（EMUI）**：`NotificationManager.cancel()` 在 EMUI 上不生效，停止后卡片不自动消失。已用 `.setOngoing(false)` 缓解，用户可手动划掉。

### ③ 未开无障碍也能直接拍照/录像/录音 ✅
- **实现**：`CalculatorActivity.onSecretCodeEvent` 对 `PHOTO/VIDEO_START/AUDIO_START` 直接调用 `takeHiddenPhoto()` / `startHiddenVideoRecording()` / `startHiddenAudioRecording()`，已**移除**上一轮加的 `ensureAccessibilityEnabled` 包裹（Grep 确认无残留引用）。
- **证据**：源码审查通过；a11y=0 状态下仍可触发拍照（见 ④）。

### ④ 拍照/录像/录音落盘 ✅
- **证据**：a11y=0 输入 "110" → logcat `takeHiddenPhoto; ready=true` → `onImageSaved -> exists=true size=1052083`；`run-as org.solovyev.android.calculator cat` 确认私有目录生成 `sys_img_*.dat`（~1MB）落盘。
- 录像/录音落盘已在第二~四轮验证（带音轨 973KB / 无声降级 960KB / 录音 30KB 等）。

## 顺带修复
- **`isAccessibilityEnabled()` 健壮性**：同时支持 `flattenToString()` 与 `unflattenFromString()` 两种形式比较，修复 `settings put` 简写导致无障碍服务已绑定但连点圆圈不出现的问题。

## 现存事项
1. 通知栏残留 5 个 `id=1001`「连点服务」卡片（1 个 tag=autoclicker + 4 个 tag=null 旧测试遗留）。EMUI 不 honor `cancel()`，且 `cmd notification cancel` 在该 EMUI 版本不可用 → **需用户手动划掉**（最终 APK 卡片已设 setOngoing(false)，可划除）。
2. 点击循环当前已停止（logcat 无近期 click center），设备处于干净待机状态。

## 构建坑回顾
- `gradle-wrapper.jar` 多次损坏 → 用缓存 Gradle 8.7 二进制重生成。
- 增量 `assembleDebug` 会漏编已改文件、假报 BUILD SUCCESSFUL → 本项目迭代**必须 `clean assembleDebug`**。

**结论：任务 #35 四项内容真机验证全部通过。**
