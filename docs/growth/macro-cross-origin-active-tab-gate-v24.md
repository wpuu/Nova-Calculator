# Nova Macro V24：跨域 activeTab 一次性恢复门禁通过

日期：2026-09-09
PR：#6（Draft，禁止 merge）

## 结论

Nova Macro 的默认跨域策略已从“请求持久 host permission 后继续”调整为更低权限的 **activeTab one-shot resume**：

1. 用户在 origin A 启动录制/重放；
2. Macro 导航到 origin B；
3. 原 activeTab 权限失效，Nova 必须暂停，`needsSiteAccess=true`；
4. 用户主动在 B 页面点击 Nova 扩展图标；
5. Chrome 因用户 extension action 为 B 当前页授予临时 activeTab；
6. 用户点击 `Resume once on this site`；
7. Service Worker 重新注入 runtime，继续录制/重放；
8. 不调用 `chrome.permissions.request()`，不留下持久 host permission。

无人值守的持久跨域授权继续 deferred，后续若实现必须作为单独、明确解释风险的能力。

## 真实执行证据

Chrome Macro ChatOps Run：`34295130964`
Job：`102289984696`
Result：SUCCESS

### 静态与状态机

- Macro POC guard：PASS
- 4 Shopify actions / 6 holdouts：PASS
- activeTab-first cross-origin contract：PASS
- Service Worker state machine：`8/8`

### Same-origin real MV3

`4/4`

- real MV3 load：PASS
- record → same-origin navigation → continue：PASS
- stop → persist 3 semantic steps：PASS
- replay → navigate → resume → input → export：PASS

### Cross-origin real MV3

`4/4`

1. `PASS recording pauses on origin B before resume`
2. `PASS one-time activeTab resume preserves all 3 recorded steps`
3. `PASS replay pauses before protected B-site steps`
4. `PASS one-time resume completes replay without persistent host access`

同时验证：

- 授权前，B 页面 Search input 仍为空，后续动作未偷跑；
- ReplayIndex 在跨域导航后保持 1，没有重复执行导航；
- Resume 后恢复 input + export 并完成；
- `chrome.permissions.contains({origins:['http://localhost/*']})` 最终仍为 false。

## 为什么这个方案优于默认持久授权

- 安装和正常使用继续遵循最小权限；
- 跨域必须由用户主动 extension action 触发；
- 不需要第一次跨域就出现额外永久 host permission 警告；
- 更适合 Chrome Web Store 信任与审核；
- 即使 Macro 模板来自第三方 Creator，也不能静默获得新站点访问；
- 代价是跨域 Macro 默认不能完全无人值守，这是 V1 有意接受的安全/信任取舍。

## 当前已证明边界

PROVEN：

- MV3 unpacked load on Chrome for Testing
- Popup/action → activeTab
- Record / Stop / Persist / Replay
- 同源 document navigation
- 跨 origin 权限撤销后 fail-closed
- 用户 one-shot resume
- at-most-once replay state machine
- controlled input native setter
- normal click/export

UNPROVEN：

- 真实已登录 Shopify Admin
- 真实 Polaris 灰度版本/多语言实际 DOM
- 第5个 Shopify Semantic Action
- 未见真实 Shopify 改版
- Agnes 2.5 Flash 真实 candidate selection / ABSTAIN
- Creator Marketplace
- Chrome Web Store production review

## 下一门禁

1. 依据当前 Shopify 官方产品与 Polaris资料扩到至少5个真实业务动作；
2. 建立 `shopify.open_order`，并验证订单列表→订单详情这类真实导航结构；
3. 继续保持 Wrong AUTO = 0；
4. 已知结构本地 AUTO，未知结构只能 AI_REVIEW/ABSTAIN；
5. 等真实 Shopify authenticated session 可用时，直接用同一 E2E harness 替换 fixture 页面，不改变验收标准。
