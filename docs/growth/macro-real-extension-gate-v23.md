# Nova Macro V23：真实 MV3 扩展门禁通过

日期：2026-09-09
分支：`fix/commercial-autotap-market-fit-v2`
PR：#6（保持 Draft，不 merge）

## 结论

此前一直未被证明的“真实 unpacked Chrome Extension 能否完成跨 document 录制与重放”门禁，现已在 GitHub Actions 的 Chrome for Testing + Puppeteer 环境中真实通过。

这不是仅在 JSDOM、JSON fixture 或普通网页 DOM 中运行 matcher；测试实际加载了 Nova Macro MV3 扩展、service worker、popup 和 content runtime，并通过真实扩展 toolbar action 获得 `activeTab`。

## 证据

Chrome Macro ChatOps Run：`34294300661`
Job：`102287436873`
结果：success

### 静态/契约门禁

- Manifest V3：通过
- 4 个 Shopify Semantic Actions 存在：通过
- 6 个 hold-out fixture：通过
- 安装时不请求 `<all_urls>`：通过
- 保持 `activeTab + scripting + storage` 低权限架构：通过
- AI 不生成可执行 selector、危险动作确认、OTP/password 不持久化等 UI/安全契约：通过

### Service Worker 状态机

结果：`8/8`

1. 快速连续录制步骤串行写入，Stop 等待 pending writes：PASS
2. 其他 Tab 步骤不混入当前 Macro：PASS
3. 同一 Tab 跨 document 后继续录制：PASS
4. 普通 Replay 每一步 at-most-once 且最终完成：PASS
5. `AI_REVIEW` 为终止状态，后续步骤不继续：PASS
6. 导航步骤后不会在旧 document 抢跑下一步：PASS
7. 无关 `tabs.onUpdated(...complete)` 不会造成重复执行：PASS
8. 缺少站点权限时暂停，用户显式授权后继续：PASS

### 真实 Chrome for Testing MV3 E2E

结果：`4/4`

1. `PASS real MV3 load`
2. `PASS record -> same-origin navigation -> continue recording`
3. `PASS stop -> persist 3 semantic steps`
4. `PASS replay -> navigate -> resume -> input -> export`

真实 E2E 流程：

`/start`
→ 用户通过扩展 Popup 开始录制
→ 点击 `Orders`
→ 同源页面跳转 `/orders`
→ 输入 `#1042`
→ 点击 `Export orders`
→ Stop & Save
→ 保存 3 个 semantic steps
→ 回到 `/start`
→ Replay
→ 自动进入 `/orders`
→ 恢复输入 `#1042`
→ 自动点击 Export
→ Session = `COMPLETED`

因此，之前的 `UNPROVEN: unpacked MV3 extension load / cross-document record-replay` 状态可以正式改为 `PROVEN`。

## 仍然没有被证明的内容

以下项目严禁因为 V23 通过而写成“已完成”：

1. **真实 Shopify 登录后台**：尚未在真实商店/真实 Polaris Admin DOM 上执行。
2. **真实 Shopify 5 类动作**：当前只是基于 Shopify 语义构造与合成页面验证。
3. **真正未知的 Shopify 改版**：hold-out 仍是我们构造的未知变体，不是 Shopify 未来真实灰度版本。
4. **真实跨 origin 浏览器授权 E2E**：状态机 mock 已通过，但当前 Chrome 4/4 E2E 是 same-origin。
5. **Agnes 2.5 Flash 实际候选选择/ABSTAIN 能力**：尚无真实 API 结果。
6. **Chrome Web Store 生产包审核**：尚未提交商店审核。
7. **Marketplace / Creator / 付费**：全部继续 deferred。

## 下一门禁

### Gate A：真实跨 origin 权限链

使用两个不同 origin 测试：

- origin A 开始录制
- 点击跳到 origin B
- `activeTab` 被撤销后必须暂停
- 显示/进入 `SITE_ACCESS_REQUIRED`
- 用户显式点击 `Allow this site & resume`
- 继续录制并保存
- Replay 时再次验证 A → B → pause → grant → resume

标准：

- 错误执行 = 0
- 未授权站点执行 = 0
- 授权后丢步骤 = 0
- 重复执行 = 0

### Gate B：真实 Shopify 5 Actions

首批至少验证：

1. `shopify.open_orders`
2. `shopify.search_orders`
3. `shopify.filter_orders`
4. `shopify.export_orders`
5. `shopify.open_order` 或 `shopify.export_products`

标准：

- 本地层正确自动执行率目标 ≥90%
- Wrong AUTO = 0
- 无法确定时必须 `AI_REVIEW/ABSTAIN`
- 分辨率/窗口宽度/缩放/语言差异不能依赖固定坐标

### Gate C：Agnes 真实受约束修复

仅把本地程序已经找到的候选送给 Agnes：

- Agnes 只能返回 `candidate_id` 或 `ABSTAIN`
- 不允许生成可执行 selector
- 危险动作不进入自动修复
- 一次请求完成
- 任意 Agnes 请求期间全产品 Agnes 入口统一 disabled

## Grok 4.6 High

V23 后已经满足“真实扩展能运行、录制/重放闭环成立、至少三类动作骨架存在、失败状态明确”这些前置条件。

但正式精品 Side Panel 仍建议等 Gate A + Gate B 至少通过一轮后再生成，避免真实 Shopify 操作流改变 UI contract 后返工。

Grok 的职责保持不变：只消费 `ui-contract.json`，负责 UI/UX 外壳，不修改核心 matcher、Site Adapter、权限、安全规则、Agnes single-flight 和执行状态机。
