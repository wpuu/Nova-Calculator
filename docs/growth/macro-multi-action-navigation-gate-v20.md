# Nova Macro V20 — 多动作、响应式与跨页面状态门槛

日期：2026-09-04

## 结论

本轮把 POC 从单一 `shopify.export_orders` 扩展到 4 类 Semantic Action：

1. `shopify.open_orders` — 导航/链接类
2. `shopify.search_orders` — 输入/搜索类
3. `shopify.filter_orders` — 筛选/菜单类
4. `shopify.export_orders` — 动作/响应式菜单类

目前结论不是“宏平台已可行”，而是：

> **语义动作 + Site Adapter 的方向继续成立；单纯 activeTab + 单页 content script 的第一版架构不成立，已经改成 service worker 持有录制/重放状态 + 用户按站点授权。**

---

## A. 已真实执行的 Chromium DOM 结果

### 1. 四类 Action 录制归类

基准元素生成 fingerprint 后，以 `admin.shopify.com` 作为站点上下文：

- open_orders：正确
- search_orders：正确
- filter_orders：正确
- export_orders：正确

结果：**4/4**。

### 2. 多动作 / hold-out 重放

第二轮真实 Chromium 回归覆盖：

- 英文/中文导航
- Orders 搜索框与页面内其他搜索框竞争
- Filter / 德文 Filtern
- Export 与 Export products 噪声
- 危险 Delete / Pay now 噪声
- 未写入别名表的 `Download CSV`
- 未写入别名表的 `Refine list`
- 未写入别名表的 `Customer purchases`
- 两个完全同名候选的歧义情况

中途发现并修复两个真实问题：

1. `Download CSV` 有稳定 `data-action=export` 与 Orders 上下文，但名称变化过大。原逻辑直接 ABSTAIN；修正为 **AI_REVIEW**，不降低 AUTO 阈值。
2. `Find a purchase` 搜索框因为包含 `purchase` 被危险词规则误伤。危险动作判断改为 **role-aware**：只有 button/menuitem 才进入 destructive action 判定。

修正后结果：

- Semantic Action 识别：**4/4**
- 重放/安全降级：**11/11**
- **错误 AUTO：0**

其中 hold-out 文案不是硬编码补答案，而是进入 AI_REVIEW/ABSTAIN 或使用已存在的稳定站点信号。

### 3. 响应式宽度

同一个 `shopify.export_orders` 页面：

- `420 / 599 / 600px`：直接 Export 隐藏，自动走 `More actions -> Export`
- `601 / 1024 / 1440px`：直接 Export 可见，直接执行

真实 Chromium 结果：**6/6**。

说明：

> viewport/响应式差异本身可以由 Semantic Action + Variant 路径吸收，不需要坐标同步。

---

## B. 本轮发现的 P0 架构缺陷：activeTab 不能支撑跨页面录制

Chrome `activeTab` 是临时站点权限。用户导航离开当前页面后权限会被撤销；页面导航也会销毁当前 content script 状态。

因此旧 POC：

`Popup -> executeScript -> content.js 内存 recording=true`

只能可靠支撑当前 document，不能支撑：

`Orders -> 打开订单 -> 新页面继续录制 -> 再返回/跳转`

这对真正网页宏属于 P0，不是边缘问题。

---

## C. 已实现的新架构

### Manifest

安装时仍然只请求：

- `activeTab`
- `scripting`
- `storage`

不请求：

- `<all_urls>`
- `tabs`
- `debugger`
- `cookies`
- `webRequest`

新增：

```json
"optional_host_permissions": ["http://*/*", "https://*/*"]
```

用户点击 Start Recording 时，只为 **当前 origin** 请求权限。

### Service Worker

新增 `background.js`，由后台持有：

- 当前录制 tab
- steps
- replay index
- replay in-flight 状态
- navigation waiting 状态
- `needsSiteAccess`

页面刷新/同域跳转完成后：

1. service worker 重新注入 matcher + content runtime
2. Recording 自动恢复
3. Replay 从后台保存的 index 继续

### 跨域

不申请全网永久权限。

若宏跳到新 origin：

1. 自动执行暂停
2. `session.needsSiteAccess = true`
3. UI 显示 `SITE_ACCESS_REQUIRED`
4. 用户主动点击 `Allow this site & resume`
5. 只授权当前 origin 后恢复

这会增加一次跨域操作，但明显优于安装时要求“读取和更改所有网站数据”。

### 步骤写入

步骤不再只存在 content script 内存。

每个捕获事件立即发送：

`NOVA_RECORD_STEP`

后台使用 `stepWriteQueue` 串行写 `chrome.storage.session`；Stop 前等待队列结束，避免快速连续操作导致 last-write-wins 丢步骤。

---

## D. Replay 机制变化

原 POC 一次在 content script 内循环所有步骤；页面一导航循环就死亡。

新版：

> **后台一次只派发一个 step。**

content script：

1. resolve Semantic Action
2. 若是危险/歧义则停止
3. 返回 step result
4. 点击动作延迟到下一 task，让结果尽量先回到 service worker

service worker：

1. 保存 replayIndex
2. 若可能导航则等待 document
3. 新 document 完成后重新注入
4. 执行下一步

因此正常宏运行仍然不需要 Agnes。

---

## E. Grok UI contract 已同步

`ui-contract.json` 升级到 v2。

新增表现状态：

`SITE_ACCESS_REQUIRED`

新增命令：

`ALLOW_CURRENT_SITE_AND_RESUME`

硬规则新增：

- 站点授权必须来自显式用户操作
- 安装时不得请求 `<all_urls>`
- 请求站点权限前 UI 必须解释原因

因此 Grok 后续生成 Side Panel 时不能把权限流程自己重新设计。

---

## F. CI 门禁

新增：

`tools/verify-macro-poc.mjs`

并接入现有 GitHub Actions。

门禁检查：

- MV3
- 必要最低权限
- 禁止 install-time `<all_urls>` / tabs / debugger / cookies / webRequest
- optional host permission 架构存在
- 4 个 Shopify Semantic Actions 存在
- 至少 4 个 hold-out cases
- JS 可解析
- 禁止 eval / new Function / 远程 importScripts
- UI contract 的安全/Agnes single-flight 规则没有被删
- content/background 中关键安全与跨页面状态标记存在

目的：以后 Grok 或人工修改 UI 时不能顺手改坏底层边界。

---

## G. 尚未证明

### 1. 真正 unpacked Chrome Extension E2E

当前容器 Chromium 可以做真实 DOM/交互测试，但 Debian Chromium + Xvfb/Playwright 的 `--load-extension` 测试仍没有可靠注册 unpacked extension。

本轮尝试结果是启动卡住；生成的 profile 中只看到内置 Web Store / PDF Viewer，未确认 Nova Extension 注册。

因此状态仍然是：

**Extension load = UNPROVEN**

不能因为 manifest/background/content 都已写好就记成通过。

### 2. 真实 Shopify

当前 HTML 是根据现代管理后台典型结构构造的 DOM variation，不是真实商户后台。

必须后续用真实 Shopify Admin 执行至少 5 个动作并做 hold-out。

### 3. Agnes

仍然没有真实 Agnes Key/代理接入当前工具环境。

Agnes repair benchmark = **UNPROVEN**。

---

## H. 下一硬门槛

按顺序：

1. 在真实 Chrome 环境加载 unpacked Extension
2. 真正执行 Record -> navigation -> continue recording -> Stop
3. 真正 Replay 跨 document
4. 真实 Shopify 5 个动作，不只 Export
5. 留出开发时没见过的 Shopify Variant 做 hold-out
6. 再接 Agnes，只处理 AI_REVIEW 候选选择
7. 上述通过后才交给 Grok 4.6 High 做 Side Panel 第一版

### Kill / downgrade rule

若真实 Shopify 条件下：

- Site Adapter 本地正确执行 < 90%，或
- 错误 AUTO > 0，或
- 每次小改版都需要按用户逐个修，无法共享修复，

则 Maintained Macro Marketplace 降级，不继续建设 Creator/Marketplace。
