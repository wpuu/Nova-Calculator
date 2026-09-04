# Nova Macro V21 — 动态目标与受控输入运行时门槛

日期：2026-09-04

## 本轮目标

在 V20 的多动作/响应式通过以后，继续验证两个真实后台常见问题：

1. 页面元素不是立即出现，而是 SPA/异步请求后延迟出现；
2. Shopify/React/Polaris 类受控输入框可能不接受简单 `element.value = x`。

---

## 1. 动态目标等待

旧逻辑：

- background 每步之间固定等待约 180ms；
- content script 立即 resolve；
- 当目标尚未渲染时容易提前 ABSTAIN。

新逻辑：

`content.js` 新增 `resolveWithWait()`：

- 默认每个 recorded step 保存 `timeoutMs: 5000`；
- 约每 120ms 重新读取当前真实 DOM；
- AUTO 一旦出现立即执行；
- 到 timeout 后才返回最终 AI_REVIEW / ABSTAIN；
- 已经安全展开过菜单后不在每轮重复 toggle 菜单。

### 真实 Chromium 测试

测试页面初始只有：

`Loading...`

约 850ms 后由 JS 动态插入：

`Export orders`

结果：

- resolver 实际等待：约 **843ms**
- 结果：`CLICKED`
- 页面 click handler 实际收到点击

结果：**PASS**。

这说明运行期不再依赖“某个固定 sleep 足够不够”，而是等待真实目标条件。

---

## 2. 受控输入写入

新增 `setNativeValue()`：

- INPUT 使用 `HTMLInputElement.prototype.value` native setter
- TEXTAREA 使用 `HTMLTextAreaElement.prototype.value`
- SELECT 使用 `HTMLSelectElement.prototype.value`
- 随后派发 bubbling + composed 的 `input`
- 再派发 `change`

### 真实 Chromium 测试

目标：Orders 搜索框。

写入：

`#1042`

测试页同时监听 `input` 与 `change`。

结果：

- 最终 value：`#1042`
- `input` handler 收到 `#1042`
- `change` handler 收到 `#1042`

结果：**PASS**。

---

## 3. 当前 runtime 结构

### 录制

- content script 捕获用户事件
- 立即发送 `NOVA_RECORD_STEP`
- service worker 串行写入 session
- 页面导航不再依赖 content script 内存保存整个宏

### 重放

- service worker 一次只派发一个 step
- content script 最多等待 target 到 timeout
- resolve 成功后执行
- 结果先回 service worker
- click 延迟到下一 task，降低导航先销毁消息上下文的概率
- navigation 后 service worker 重新注入并继续 replay index

### 跨站点

- 安装时不请求 `<all_urls>`
- Start Recording 时仅请求当前 origin
- 新 origin 缺权限：`needsSiteAccess=true`
- 用户主动 `Allow this site & resume`

---

## 4. 仍未证明

这些测试仍不是实际 Shopify Admin：

- React/Polaris 真正内部状态
- Shadow DOM/Web Components 深层交互
- iframe
- 虚拟化表格
- 同一动作真实 A/B rollout
- route transition + service worker 真扩展 E2E

因此 V21 只证明：

> 动态等待和 native setter 的底层策略在真实 Chromium DOM 中工作。

不证明整个 Shopify 宏已经可用。

---

## 5. 下一 Gate

必须在真正 unpacked Chrome Extension 环境验证：

1. Start Recording
2. Orders 页面记录输入/点击
3. 页面导航
4. service worker 自动恢复 recording
5. Stop 保存全部 steps，没有丢步
6. Replay
7. 跨 document 后继续下一 step
8. 异步目标出现后正常等待
9. 危险/歧义目标仍 fail closed

只有这关通过，才进入真实 Shopify 5-action 验证和 Agnes benchmark。
