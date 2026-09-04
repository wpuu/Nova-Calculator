# Nova Macro V22 — Service Worker 竞态与 At-Most-Once 执行门槛

日期：2026-09-04

## 结论

本轮没有继续增加宏功能，而是审计跨页面录制/重放状态机。发现并修复了 4 个会直接破坏真实自动化可靠性的 P0/P1 问题。

当前目标明确为：

> **宁可暂停、AI_REVIEW、要求重新授权，也不能因为恢复/重试机制重复点击真实网页。**

---

## 1. 修复：导航步骤不能在旧 document 上抢跑下一步

旧逻辑：

1. step 0 点击一个可能导航的链接；
2. service worker 收到 `mayNavigate=true`；
3. 700ms 后直接再次调用 `runReplayStep()`；
4. 如果新 document 还没有完成，step 1 可能在旧 DOM 上执行。

这对“Orders -> 打开订单 -> 在详情页执行下一步”是错误的。

### 新逻辑

content step result 增加：

- `urlBefore`
- `mayNavigate`

service worker 保存：

- `replayWaitingForDocument=true`
- `waitingFromUrl=urlBefore`

`runReplayStep()` 在 waiting 状态下直接拒绝继续派发。

恢复有两条路径：

### Full navigation

`tabs.onUpdated(status=complete)` -> 重新注入 runtime -> 清 waiting -> 下一 step。

### SPA navigation

每约 250ms 只读查询 `NOVA_CONTENT_STATE`；如果同一个 content runtime 报告 URL 已经从 `waitingFromUrl` 改变，则认为 SPA route 已切换，再执行下一 step。

如果 URL 没变化：继续等待，不执行下一步。

---

## 2. 修复：无关 `tabs.onUpdated(...complete)` 不能清掉 in-flight step

旧 `resumeAfterNavigation()` 在 REPLAYING 状态下遇到任何当前 tab 的 complete，都可能：

- `replayInFlight=false`
- 然后重新派发 step

如果这个 complete 不是当前宏步骤造成的真实导航，就有重复执行风险。

### 新逻辑

REPLAYING 时只有下面任一条件成立才进入恢复：

- `replayWaitingForDocument=true`
- `needsSiteAccess=true`

否则 complete 事件直接忽略。

因此正常执行中的 step 不会被 unrelated complete 打断或重复。

---

## 3. 修复：用户明确授权后 Replay 可能不继续

发现旧 `NOVA_RESUME_CURRENT` 有一个逻辑顺序错误：

1. `needsSiteAccess=true`
2. 用户点击 `Allow this site & resume`
3. resume handler 先把 `needsSiteAccess=false`
4. 再进入 `resumeAfterNavigation()`
5. REPLAYING 分支看到：既不 waiting，又不 needsSiteAccess
6. 直接 return

结果是：

> 用户明明完成授权，但 Replay 静默不继续。

### 新逻辑

`resumeCurrent()` 只更新：

- tabId
- originPattern
- error

保持 `needsSiteAccess=true`，直到 `resumeAfterNavigation()` 真正进入恢复路径并成功注入新页面后才清零。

---

## 4. 修复：执行消息不得自动重试

旧 `sendToTab()`：

`sendMessage失败 -> injectRuntime -> 再 sendMessage 一次`

对无副作用状态消息问题较小，但 `NOVA_EXECUTE_STEP` 可能包含真实：

- click
- input
- navigation

如果第一次网页动作已经发生、只是 response channel 出错，第二次自动重发可能导致：

- 双击
- 重复提交
- 重复输入
- 重复跳转

### 新规则

Replay step delivery = **at-most-once**。

运行期由 lifecycle 明确保证 content runtime 已注入：

- replay start 注入
- full navigation complete 后重新注入
- explicit site resume 后重新注入

真正派发 `NOVA_EXECUTE_STEP` 时只发一次。

如果发送失败：

- `replayInFlight=false`
- `needsSiteAccess=true`
- 暂停

不自动重试副作用动作。

---

## 5. 新增状态机评测

新增：

`tools/macro-background-state-eval.mjs`

覆盖 8 个确定性场景：

1. 40 个快速录制事件串行落盘，Stop 等待全部写完；
2. 非当前 tab 的录制事件忽略；
3. Recording 跨 document 后继续；
4. 普通 replay steps 每步只执行一次并完成；
5. AI_REVIEW 是 terminal，后续 step 不执行；
6. navigation 后 URL/document 未改变时下一 step 不抢跑；
7. unrelated complete 不重复正在执行的 step；
8. 新站点权限缺失时暂停，显式授权后继续。

该测试已接入：

`.github/workflows/android-debug-apk.yml`

与 `tools/verify-macro-poc.mjs` 同一步执行。

### 当前 CI 状态

GitHub connector 写入的新 commits 当前没有产生新的 Check Run；当前 HEAD 查询到 statuses 为空。

因此：

> **测试代码已写入 + 已接入 workflow，但不能标记为 CI passed。**

下次正常 workflow 触发时必须实际执行。

当前容器也无法 DNS 访问 github.com，本地 clone 失败原因是：

`Could not resolve host: github.com`

这属于执行环境限制，不等于测试通过或失败。

---

## 6. 静态门禁同步加强

`tools/verify-macro-poc.mjs` 现在还要求以下机制不能被后续 Grok/人工删除：

- `urlBefore`
- `NOVA_CONTENT_STATE`
- `replayWaitingForDocument`
- `waitingFromUrl`
- `probeNavigationProgress`
- unrelated complete fail-closed 条件
- service-worker state-machine evaluator 文件必须存在

---

## 7. 已通过 / 未通过边界

### 已实际 Chromium DOM 验证

- 4 类 Semantic Action 识别：4/4
- 多动作/hold-out replay 判断：11/11
- wrong AUTO：0
- 响应式宽度：6/6
- 延迟约 850ms 才出现目标：成功等待并点击
- native input setter：input/change 事件均触发

### 代码已实现但未真扩展 E2E

- service worker 持久状态
- 跨 document recording resume
- 跨 document replay resume
- per-origin optional permission
- site access pause/resume
- navigation race guard
- at-most-once execution

### 仍 UNPROVEN

1. 真实 desktop Chrome unpacked Extension load + E2E
2. 真实 Shopify Admin 5+ actions
3. hold-out 真实 Shopify rollout
4. Agnes 2.5 Flash candidate selection benchmark

---

## 8. Grok 4.6 High 状态

仍然 **不启动正式前端生成**。

原因：

Grok 的 UI contract 已经稳定到 v2，但真实 Extension E2E 尚未通过。现在做漂亮 Side Panel 仍会制造错误完成感。

触发 Grok 的顺序保持：

1. real Chrome unpacked load
2. real Record -> navigation -> continue -> Stop
3. real Replay -> navigation -> continue
4. 至少 3 类动作实跑
5. failure/site-permission states 实跑
6. 再用 Grok 4.6 High 生成 Side Panel

---

## 9. 下一硬 Gate

下一步不继续扩大 Site Action 数量。

优先级：

### P0

在能加载 unpacked extension 的真实 Chrome 环境做 E2E。

### P1

在真实 Shopify Admin 做 5 个动作 + 未见变体。

### P2

接真实 Agnes，测试 AI_REVIEW 只从真实候选 ID 中选择或 ABSTAIN。

在 P0/P1 前，不开发 Creator Marketplace / payment / cloud execution。
