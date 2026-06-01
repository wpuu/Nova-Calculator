# 高级计算器 (隐蔽取证工具) - 完整项目重构方案

## 1. 项目概述
本项目基于开源应用 Nova Calculator 进行二次开发。表面上它是一个拥有完整功能的科学计算器，但其底层嵌入了一套高度隐蔽的“暗号触发系统”。用户可以通过在正常计算过程中输入特定数字序列，无缝触发静默拍照、录音、录像等高级取证功能，且绝不会破坏当前计算进度。

---

## 2. 核心架构与逻辑设计

### 2.1 隐蔽暗号触发引擎 (Keyboard.java)
- **触发机制**：抛弃了传统的“等于号触发”或“文本全匹配(equals)”，改为 **动态词尾拦截 (endsWith)**。
  - *原理*：无论当前计算器屏幕上是空白，还是正在进行 `1500 * 30` 的复杂计算，只要用户的最后几次按键拼成了暗号（例如 `110`），就会瞬间触发事件。这使得伪装操作极其自然。
- **防止误触机制**：
  - **后退键免疫**：判定 `e.newState.text.length() < e.oldState.text.length()` 时，直接 return，防止用户通过删除数字意外拼出暗号触发。
  - **防止历史恢复(启动)触发**：判定 `boolean isStartupLoad = e.oldState.text.length() == 0 && e.newState.text.length() > 1;`。当应用刚启动加载历史记录时（一次性塞入多个字符），拦截除了 `110` 以外的所有暗号。但如果是人工快速连按 `8888`，则不会被拦截。

### 2.2 一键紧急停止与状态指示 (CalculatorActivity.java & Keyboard.java)
- **UI 伪装与状态指示**：当触发 `112`（录像）或 `114`（录音）时，计算器原有的 **“后退/删除键”会变为灰色**，作为隐蔽的工作状态指示灯。
- **一键紧急停止 (Backspace 监听)**：
  - 在 `Keyboard.java` 中监听文本缩短事件，如果此时后台正在录音/录像，则向主线程发送 `VIDEO_STOP` 或 `AUDIO_STOP`。
  - **效果**：用户只需点一下灰色的后退键，不仅会正常删掉一个数字以掩人耳目，还会瞬间终止录制保存文件，并将后退键颜色恢复正常。

### 2.3 极致省电与常亮护航模式 (BaseActivity.java)
- **永不息屏**：为了保证随时可以输入暗号抓拍，强制为窗口添加 `FLAG_KEEP_SCREEN_ON` 权限，只要停留在计算器界面，手机永远不会锁屏。
- **自动极暗省电机制**：
  - 通过 `Handler` 监听 `onUserInteraction()`。
  - 如果用户超过 2 分钟没有任何操作，计算器会自动将当前窗口的 `LayoutParams.screenBrightness` 设置为极低值 `0.01f`。
  - 屏幕看起来像即将锁屏时的“暗屏”，把电量消耗降到最低；但只要手指一碰屏幕，立刻恢复正常亮度，且在此期间 100% 保持后台活跃和瞬间抓拍能力。

---

## 3. 硬件交互与权限兼容 (录音、录像模块)

### 3.1 强制静音与防崩溃护盾 (VideoRecorderManager.kt)
- **静音快门策略**：拍照前，系统会尝试将 `AudioManager.STREAM_SYSTEM` 音量强制设为 0。
- **防崩溃设计 (Critical)**：在华为及部分 Android 10+ 手机上，如果没有“勿扰模式”权限，强制修改系统音量会抛出 `SecurityException` 导致应用闪退。必须在修改音量的代码外层包裹 `try-catch`。如果静音失败，宁可保留系统提示音，也绝不能让应用崩溃。

### 3.2 媒体录制方案
- **拍照/录像 (CameraX)**：使用 AndroidX CameraX 绑定 `ProcessCameraProvider`。由于是隐秘录制，界面上提供了一个极小的或透明的 `PreviewView`（或者不渲染在可见视图层）。
- **录音 (MediaRecorder)**：使用 `MediaRecorder` 进行纯后台 AAC 格式录音。

---

## 4. 事件总线与依赖注入 (Otto Bus & Dagger)

- **Otto Bus 坑点修复 (AppModule.java)**：
  - 原版 Nova Calculator 使用 `new Bus(ThreadEnforcer.MAIN)` 或 `GeneratedHandlerFinder`，在代码修改或热重载后容易导致找不到 `@Subscribe` 订阅者，导致暗号无效。
  - **解决方案**：必须修改 `AppModule.AppBus`，改用反射机制查找订阅者，并将强制线程检查放宽：`new Bus(ThreadEnforcer.ANY, "app-bus")`，确保暗号事件 (`SecretCodeEvent`) 百分百能穿透传递给 `CalculatorActivity`。

---

## 5. 编译与打包清理

- **包名与图标 (AndroidManifest.xml)**：
  - 应用名称必须硬编码或统一在 `strings.xml` 中改为 `高级计算器`。
  - 删除冗余的启动 Activity 声明（如原版的 Launcher 别名），确保手机桌面只生成一个单一的、图标已被伪装的 “高级计算器” APP，杜绝双图标问题。
