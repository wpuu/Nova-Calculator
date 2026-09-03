# Nova Macro：真实 DOM 原型与生死门槛 V16

日期：2026-09-04

## 结论

宏项目已经从 JSON 规则模拟推进到真实 Chromium DOM 执行，但仍未进入真实 Shopify Admin 验收。

当前最重要的新结果：

- 通用录制器（无 Site Adapter）：9 / 18 通过；
- 加 Shopify `shopify.export_orders` Site Adapter：18 / 18 通过；
- 测试运行在真实 Chromium 144 DOM、真实 `getComputedStyle` / `getBoundingClientRect` / click 事件环境；
- 测试页面仍为我们构造的 Shopify-like DOM，因此 **18/18 不能视为真实 Shopify 成功率**；
- 本轮没有出现危险候选被自动点击；歧义场景停在 `AI_REVIEW`，权限/disabled 场景停在 `ABSTAIN`。

这证明的不是“录一次就能适配所有网页”，而是：

> 通用 Recorder 只能作为底层；真正跨 viewport / locale / responsive menu / DOM churn 的能力需要 Site Adapter + Semantic Action。

---

## 真实 Chromium 测试矩阵

录制基线：英文宽屏 `Orders -> Export orders`。

变体包括：

1. 英文宽屏；
2. 英文窄屏，Export 折叠到 More actions；
3. 中文宽屏；
4. 中文窄屏；
5. 德文宽屏；
6. 德文窄屏；
7. 法文宽屏；
8. 法文窄屏；
9. DOM wrapper 层级变化；
10. 其他扩展插入 `Export products` 噪声按钮；
11. 目标 disabled；
12. 目标因权限不存在；
13. 两个同分 Export 候选；
14. 只有 aria-label；
15. Web Component / custom element 标签变化；
16. A/B 文案改成 `Export order data`；
17. 旧按钮隐藏，新按钮进入菜单；
18. 页面加入 Delete / Export products 危险与噪声候选。

### 第一轮真实 DOM

通用：8 / 18。

Adapter：12 / 18。

暴露问题：

- menuitem 与 button role 不同；
- custom element 导致 tag 信号消失；
- A/B 文案只靠录制原文无法稳定命中；
- 窄屏菜单路径虽然能展开，但展开后的 menuitem 分数差 1 分没有达到自动执行门槛。

### 修复

1. Semantic Action 支持目标 role family：`button` / `menuitem`；
2. 自动执行门槛从 66 调整为 62，但仍要求第一与第二候选至少 10 分差距；
3. Shopify Adapter 提供 locale alias 与 A/B alias；
4. 保留危险候选强惩罚；
5. 菜单展开只允许匹配已知安全 menu trigger；存在多个近似 menu trigger 时转 `AI_REVIEW`。

### 第二轮真实 DOM

- 通用：9 / 18；
- Shopify Adapter：18 / 18。

通用模式故意没有因为调低门槛而“变聪明”：跨语言、跨操作路径仍失败。这是正确结果，说明系统没有用过宽 fuzzy matching 假装鲁棒。

---

## 已实现最小 Chrome MV3 原型

目录：`prototypes/nova-macro-mv3/`

当前文件：

- `manifest.json`
- `semantic-matcher.js`
- `content.js`
- `popup.html`
- `popup.js`

### 权限

只申请：

- `activeTab`
- `scripting`
- `storage`

没有常驻 `<all_urls>` host permission。

### 当前 POC 能力

1. 用户主动点击扩展后注入本地代码；
2. `Start recording` 开始记录；
3. click 记录语义 fingerprint；
4. input/change 记录普通输入；
5. password / one-time-code 不记录；
6. Shopify 已知动作尝试识别 `shopify.export_orders`；
7. `Stop & save` 存储最后一个宏；
8. `Replay` 逐步语义恢复目标；
9. 高置信目标才自动执行；
10. 歧义 -> `AI_REVIEW`；
11. 找不到 / 权限不足 / disabled -> `ABSTAIN`；
12. Delete / Pay / Purchase 等危险动作 -> `REQUIRES_CONFIRMATION`，不自动执行；
13. 已知 responsive menu 可先安全展开菜单，再重找目标。

### 当前明确不支持

- 页面导航后的连续录制；
- iframe / shadow DOM 深层目标；
- drag/drop；
- 文件上传；
- CAPTCHA / 2FA 自动化；
- 云端执行；
- Creator Marketplace；
- User Scripts Marketplace；
- Agnes 真实调用；
- 真实 Shopify Admin 生产验收。

---

## 为什么 Site Adapter 是商业生死点

如果每个用户的宏只保存：

- 坐标；
- XPath；
- CSS selector；
- 原始英文文本；

那么不同 viewport、locale、A/B UI、responsive menu 会让维护成本接近用户数线性增长。

如果宏引用共享 Semantic Action：

`shopify.export_orders`

则可以把差异集中到：

- locale aliases；
- target role family；
- responsive menu path；
- A/B variants；
- stable attributes；
- context；
- safety rules。

未来同一个 Action 被 500 个宏引用时，修 Action 可以恢复 500 个宏，而不是修 500 次。

---

## 下一组 Gate（必须按顺序）

### Gate 1：真实 Shopify Admin

至少测试：

- 3 种 viewport；
- 3 种有效缩放 / effective CSS width；
- 至少 2 种 locale（能获得真实账号时）；
- 直接按钮与菜单路径；
- 用户权限差异；
- 至少 5 个真实 Semantic Actions，而不只 `export_orders`。

通过线：

- 本地层正确执行 >= 90%；
- 危险误点击 = 0；
- 不确定必须停在 `AI_REVIEW` / `ABSTAIN`。

### Gate 2：未知变体（hold-out）

制作测试集的人不能把所有变体规则提前写入 Adapter。

通过线：

- 已知变体 >= 95%；
- 未知但小改版本地直接恢复 >= 75%；
- 其余进入 AI / 人工，而不是误点；
- 错误自动点击 < 0.5%，危险错误 = 0。

### Gate 3：Agnes 受约束判断

只给 Agnes：

- 原 fingerprint；
- 5~10 个真实存在候选；
- 局部 DOM；
- 必要时一次截图。

只允许输出：

- candidate id；
- `ABSTAIN`。

通过线：

- 剩余歧义正确选择 >= 90%；
- 危险场景必须 100% ABSTAIN / 拒绝自动执行；
- 一次 case 一次请求。

### Gate 4：共享修复

构造 20 个宏，复用 3~5 个 Semantic Actions。

故意改变一个 Site Action 的 DOM / responsive path。

通过线：

- 只改一个 Adapter 后，大部分引用宏恢复；
- 不需要逐宏编辑；
- 失败可以按 Action / Variant 聚类。

---

## 项目判死条件

任何一项成立，应降级 Macro Marketplace：

1. 真实网页本地 Semantic Action 正确率长期低于 85%；
2. 为达到高成功率必须对每个用户保存大量专有例外；
3. 一个站点的小改版经常需要修改大量用户宏；
4. 安全阈值调高后错误点击仍不可接受；
5. Agnes 在候选选择任务中仍需要多轮反复才能达到可用率；
6. Chrome MV3 / Web Store 政策要求导致市场模板无法以可审核方式分发；
7. Creator 供给无法形成，最终所有 Macro 都需要我们自己维护。

---

## 当前判断

状态：`PROMISING BUT UNPROVEN`

比 V15 更积极的原因：

- 真实浏览器 DOM 已证明 Site Adapter 能显著提升鲁棒性；
- 失败模式以安全停止为主，而不是错误执行；
- 运行期仍可保持 0 Agnes；
- AI 可以继续被限制在创建/修复，而不是实时网页操作。

仍不能提高到正式开发优先级第一名的原因：

- 当前 18/18 是我们自己构造的已知变体；
- 真实 Shopify / Amazon / 1688 页面复杂度明显更高；
- Shadow DOM、iframe、动态虚拟列表、权限、插件冲突尚未验证；
- Marketplace 的真实付费需求仍需单独验证。
