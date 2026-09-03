# Nova / Opportunity Factory — 精品竞品拆解与 Agnes 单飞控制 V7

Date: 2026-09-03

## 0. 本版硬规则

这版不再把“发现有需求”当成开发理由。每个候选方向必须同时回答：

1. 客户在哪里出现？
2. 客户什么时候产生强需求？
3. 我们通过什么入口截到客户？
4. 头部竞品为什么能成交、为什么能留存？
5. 头部竞品有哪些反复出现的缺点？
6. 我们能否做一个更窄、更精品、更容易理解的差异化产品？
7. 首版开发难度是否适合一人公司？
8. Agnes 是否能低频调用完成高价值工作？

Agnes 永远按免费规划；只把 RPM 当作硬约束。

---

## 1. Agnes API 全局单飞规则

### 1.1 前端规则

任何依赖 Agnes 的按钮被点击后：

- 立即原子设置 `globalAiBusy = true`；
- 当前按钮进入 loading；
- 页面内以及其它模块中所有 Agnes 按钮立即 disabled；
- disabled 按钮视觉置灰，并显示“AI处理中”；
- 不允许双击、连续点击、并行点击；
- 请求成功、失败或达到明确超时后，才在 finally 中释放 `globalAiBusy`；
- 普通本地计算、规则判断、导航、历史查看不受影响。

这是整个产品级锁，不是单按钮锁。

### 1.2 后端规则

用户会配置多条、不同账号下的 Agnes API Key。服务端负责池化：

- 每个 Key 独立 token bucket；
- 建议每 Key 稳态目标 12 RPM，调度硬阈值 15 RPM，不贴着 20 RPM 跑；
- 选择当前最空闲且健康的 Key；
- Key 达阈值时排队，不向同一 Key 硬冲；
- 遇到 429：该 Key cooldown，任务重新排队到其它可用 Key；
- 不因为有多个 Key 就允许同一用户连续点按钮；前端单飞规则保持不变；
- 同一输入可做 hash 去重与结果缓存，主要目的是减少 RPM；
- UI 层永远看不到真实 Key、模型名和供应商。

### 1.3 产品设计原则

优先做：

> 0 次调用完成筛选 / 规则判断 → 用户确认真正要处理时 → 1 次大调用输出结构化结果。

避免：

> OCR一次 → 翻译一次 → 分类一次 → 总结一次 → 再解释一次。

尽量一次请求打包全部上下文。

---

# 2. 精品候选 #1：Chargeback Evidence Cockpit

## 2.1 精品竞品

### Chargeflow

优势：

- Shopify 头部品牌之一；
- 100+ 支付/电商/业务工具集成；
- 自动搜集资料、生成个性化 evidence、自动提交；
- 还叠加 chargeback alert、fraud prevention；
- 声称网络覆盖 20,000+ merchants，网络数据本身形成护城河；
- Shopify App Store 当前数百条评价，证明渠道与需求均成熟。

缺点/公开投诉主题：

- 商户难以理解 alert / recovery 分别为什么收费；
- 存在用户投诉 duplicate alerts / 同订单重复计费；
- 有用户投诉 alert 最终仍进入 chargeback，却感觉仍被收费；
- 自动化越强，用户越担心自己不知道系统做了什么；
- 复杂配置仍大量依赖客服解释。

### Disputifier

优势：

- Chargeback fighting + Ethoca/CDRN alerts 一体；
- 很多好评的核心其实是人工 onboarding 与客服帮助；
- 可以设置 manual control，避免某些 delivered order 被无脑自动退款；
- 对商户来说“有人帮我盯着”是明确价值。

缺点/公开投诉主题：

- 费用/alert 费用理解困难；
- billing descriptor、MID 等配置专业度高；
- 用户高度依赖客服；
- 一旦用户失去对自动动作和收费的信任，负面体验很强。

### ChargePay

优势：

- Built for Shopify；
- 更年轻但目前评价很高；
- 深度 Shopify 集成；
- 能连接商户 email 自动补入客户沟通证据；
- 固定月费 + unlimited chargebacks 的结构更容易理解；
- 评论多次强调“比竞品更容易使用”。

缺点/潜在缺口：

- 产品仍然走“全自动 agent”方向；
- 商户仍必须把较多权限、邮件和业务数据交给系统；
- 目前评价规模还小，护城河主要是集成与服务，不是网络效应。

## 2.2 我们不应该做什么

不直接复制 Chargeflow：

- 不做 100+ integrations；
- 不做支付风险网络；
- 不做自动 refund / alert network；
- 首版不自动提交 dispute；
- 不做 24/7 人工客服团队。

那会把一人公司拖死。

## 2.3 差异化：Evidence Cockpit，不是 Autopilot

定位：

> “系统帮你把证据整理到最好，但最后一步永远由商户确认。”

核心体验：

1. Shopify App 安装；
2. 自动读取当前 open dispute 的订单、物流、退款、商品和客户基础数据；
3. 本地/规则引擎先给 `Evidence Readiness Score`，0 Agnes；
4. 明确列出：已有证据 / 缺失证据 / 冲突证据；
5. 用户点击 `Build Evidence Pack`；
6. 全部 Agnes 功能立即置灰；
7. 一次 Agnes 请求把所有资料整理为：
   - dispute facts；
   - timeline；
   - evidence index；
   - issuer-facing summary；
   - missing evidence warnings；
   - 每一句对应的数据来源；
8. 商户逐项确认；
9. 首版导出/复制到 Shopify，不自动提交。

### 真正差异化

不是“AI更强”，而是：

- **透明**：每条结论都标明来自订单、物流还是聊天；
- **可控**：绝不在商户没确认时自动退款或自动提交；
- **可审计**：保留 evidence version / source / timestamp；
- **不依赖客服才能看懂**：产品本身解释“为什么这一证据有用”；
- **一次案件 1 次 Agnes 为主，补资料后最多再调用 1 次**。

## 2.4 获客

### 第一入口：Shopify App Store

这是主要“借鸡生蛋”入口。

不和 251 个 fraud/chargeback app 泛拼 `chargeback`，而是卡更窄词：

- chargeback evidence
- dispute evidence
- chargeback response
- chargeback proof
- dispute packet

商店第一屏不写“AI Agent”，写：

> Know what evidence is missing before you submit.

### 第二入口：高意向 Google SEO

Grok 页面只做真实问题页：

- Shopify item not received chargeback evidence
- fraudulent transaction chargeback evidence
- subscription chargeback evidence
- duplicate transaction dispute evidence
- customer says product not received but tracking delivered

每页都提供规则 checklist / evidence checklist；到真正生成 evidence pack 时才调用 Agnes。

### 第三入口：竞品截流（事实型 SEO）

允许做：

- Chargeflow alternative for merchants who want manual approval
- Disputifier alternative with evidence preview
- Chargeflow duplicate alert fee explained

必须事实准确，不冒充竞品、不买假评价、不伪造比较。

## 2.5 成交

用户已经收到 dispute，是强触发，不需要教育。

最强成交页面不是功能列表，而是：

> “你这个案子现在缺 3 项证据；如果现在提交，风险较高。”

用户看到自己的真实 case 结果后，再触发导出/完整包/历史管理等付费动作。

## 2.6 不易被绕开

一次性 evidence 文案很容易被绕开；所以护城河要放在：

- Shopify 自动取数；
- reason-code/version 规则库；
- 历史 case outcome；
- evidence source trace；
- 重复 dispute 模板；
- 商户自己的 policy / shipping / refund 资料长期复用。

## 2.7 开发难度

- 纯网页手动上传 evidence：3.5/10
- Shopify 集成 + open dispute dashboard：6/10
- 自动提交：8/10
- alert network / fraud network：10/10

建议只做到 6/10 的精品窄版。

---

# 3. 精品候选 #2：FBA Recovery Second Opinion

## 3.1 精品竞品

### GETIDA

优势：

- Amazon Selling Partner Appstore 正式应用；
- 自动审计 FBA 差异；
- claim management 专人处理；
- 覆盖 lost/damaged inventory、inbound、fulfillment fee、weight/dimension；
- Proof of Delivery / document management；
- Seller Central notifications；
- “不追回不收费”，理解门槛低。

但当前 Amazon Appstore 评分约 1.8/5；公开差评主题高度集中：

- 最低收费 / 费用政策变化争议；
- 小额追回但费用更高；
- Amazon 后续找到库存、冲回 reimbursement 后，用户担心之前佣金没有同步逆转；
- case 跟进慢导致 claim window 过期；
- 25% success fee 对部分用户偏高；
- Amazon 已自动赔付很多基础 case，用户开始质疑哪些 claim 还值得支付 25%。

### Helium 10 Managed Refund Service / Refund Genie

优势：

- 与成熟 Amazon seller suite 深度绑定；
- 现有商家不需要再买另一套生态；
- hands-off Managed Refund Service；
- Refund Genie 可做偏 DIY 的识别；
- 多 marketplace 支持；
- claim 类型覆盖广。

缺点：

- 用户必须进入 Helium 10 整套生态；
- 对只想做“第二意见”的卖家过重；
- 套件复杂度高。

### sellerboard Money Back / Reimbursement Gap

优势：

- 低价 SaaS；
- 不只做 reimbursement，还做完整利润、COGS、PPC、库存；
- 新的 `Reimbursement Gap` 很关键：专门识别 Amazon 已经赔了，但赔付金额低于真实 COGS 的情况；
- 非常适合精细财务型卖家。

缺点：

- 功能很多，不是 reimbursement 专用；
- 用户仍需理解 Amazon 数据和 claim 流程；
- 对“不想学规则、只想知道哪里不对”的卖家仍不够直观。

## 3.2 差异化：不是追回服务，而是净回款审计器

定位：

> “Amazon / GETIDA / Helium 已经做过一遍以后，我们告诉你最后还有哪里不对。”

首版核心：

### A. Net Recovery Ledger

按 case 显示：

- Amazon reimbursed；
- later reversed/clawed back；
- third-party fee；
- final net recovered；
- unresolved balance。

用户真正关心的是最后净拿到多少，不是 gross reimbursement。

### B. Reversal Watch

专盯：

> 已经赔了 → Amazon 后来找回库存 → reimbursement reversal。

这是现有公开差评里非常明确的信任痛点。

### C. Underpayment / COGS Gap

借鉴 sellerboard 的优点：

> Amazon承认该赔，但赔少了多少？

### D. Claim Window Guard

不是帮用户不停开 case，而是显示：

- 还有多少天；
- 当前证据是否齐；
- 是否值得处理；
- 已经由 Amazon 自动赔付的 case 不重复骚扰。

## 3.3 获客

### 第一入口：Amazon Selling Partner Appstore

Amazon 官方本身允许通过 SP-API 创建并上架第三方 seller app。这是天然的卖家入口。

商店定位不要叫：

> FBA Reimbursement Service

而叫：

> Reimbursement Gap & Reversal Audit

### 第二入口：精准 SEO

Grok 重点生成真钱长尾：

- Amazon reimbursement reversed
- FBA reimbursement lower than COGS
- GETIDA reversal fee
- GETIDA alternative for small reimbursements
- Amazon reimbursement gap
- FBA claim window expired
- Amazon reimbursed then reversed

### 第三入口：竞品后市场

不是抢“第一次找追回服务”的用户，而是抢：

> “我已经在用 GETIDA / Helium / sellerboard，但我怀疑账不对。”

这是更明确的差异化入口。

## 3.4 成交

第一屏必须给用户一个与自己账相关的结果：

> `Potential net recovery gap: $1,248`

或者：

> `3 reimbursements were later reversed; 2 still appear unresolved.`

规则识别阶段尽量 0 Agnes。

用户点击单个复杂 case 的：

> Explain this case / Build follow-up package

才触发一次 Agnes。

## 3.5 不易被绕开

长期价值在：

- 持续 SP-API reconciliation；
- reimbursement/reversal 时间序列；
- COGS 历史；
- claim deadline；
- Amazon policy version；
- 外部 recovery service 的最终净结果对账。

用户自己学会一个 claim 的规则，不等于愿意每月手动做全账户 reconciliation。

## 3.6 开发难度

- CSV 报告导入 MVP：4/10
- SP-API 自动对账：7/10
- 自动 case submission：8.5/10

建议先做 CSV / report audit 验证，再决定是否上 SP-API。

---

# 4. 精品候选 #3：China Deal Inspector

## 4.1 精品竞品

### AliPrice / AiPrice

优势：

- Chrome Web Store 有数百条评分；
- 多语言；
- image search；
- 1688/Taobao/Amazon/AliExpress 等跨平台找同款；
- 图片/视频批量下载；
- 图片文字翻译；
- shopping cart export；
- browsing history；
- 很强的跨境卖家浏览器工作流。

其扩展条款还明确包含 advertising-link redirect，这说明其商业模式本身就很偏流量/导购。

### 1688 Dropshipping Pro

优势：

- Shopify 原生入口；
- sourcing + AI translation；
- product import；
- shipping / fulfillment；
- 用户评价反复强调节省 sourcing 时间、价格更低、人工 support 灵活；
- 有用户做到 100+ orders/day，说明不是只服务新手。

### Sup Dropshipping / SourcinBox

优势：

- “像有一个中国私人采购代理”；
- sourcing、订单同步、仓储、物流、品牌包装全部有人处理；
- 用户特别重视 dedicated human agent 和快速响应。

这说明采购市场真正的强价值之一不是“翻译”，而是“有人替我把中国端事情做掉”。

## 4.2 我们不能直接打它们的强项

一人公司不应该和 SourcinBox / Sup 比：

- 仓库；
- 物流团队；
- 客服；
- 品控；
- 每个客户一个 sourcing agent。

也不应该和 AliPrice 比 50 个浏览器小工具。

## 4.3 差异化：独立的“买前审计”，不是代理

定位：

> “Agent想让你下单；我们只负责告诉你这单值不值得下。”

核心产品：

### 一键 Deal Audit

用户在 1688 商品页点：

> Audit this deal

然后全局 Agnes 按钮置灰。

前端/扩展先本地收集：

- 标题；
- SKU/规格；
- MOQ；
- 阶梯价；
- 图片；
- 商品属性；
- 供应商公开信息；
- 用户目的国家和销售价格（如已保存）。

一次 Agnes 请求输出：

- 中文商业术语解释；
- MOQ / 混批 / 拿样 / 定制条件；
- 可能的隐藏成本；
- 需要问供应商的 5 个问题；
- 不确定项；
- landed-cost 输入缺口；
- “Buy / Need more info / Avoid for now” 仅作为决策辅助，不伪造事实。

### Compare 3 Suppliers

真正不可绕开的不是翻译，而是：

- 同款供应商横向比较；
- 历史价格；
- MOQ；
- 包装；
- 物流参数；
- 过去询价结果；
- 用户自己的目标毛利。

## 4.4 获客

### 第一入口：Chrome Web Store

这是最强入口，因为用户正在 1688 / Taobao 浏览时需求已经发生。

关键词：

- 1688 sourcing
- 1688 supplier check
- 1688 landed cost
- 1688 translator for sellers
- China sourcing assistant

### 第二入口：Grok 工具页

只做临门一脚的搜索：

- 1688 landed cost calculator
- 1688 hidden fees
- 1688 MOQ meaning
- 1688 vs Alibaba supplier
- how to check 1688 supplier
- 1688 sample order
- 1688 mixed batch meaning

页面规则计算尽量不调用 Agnes；用户上传具体产品截图或链接后才调用一次。

### 第三入口：Shopify App Store（后置）

如果验证用户明显集中在 Shopify 卖家，再把历史 supplier / landed cost / product margin 放进 Shopify。

## 4.5 成交

最强成交不是：

> AI会翻译中文。

而是：

> “这件商品看起来 ¥18.6，但按你的 MOQ、包装和已知运费输入，当前可确认成本已经到 ¥29.4；还有两项未确认。”

用户看到钱之后才会留下。

## 4.6 不易被绕开

必须逐渐积累：

- 用户自己的 supplier history；
- quote history；
- landed-cost model；
- 商品版本；
- 采购问答记录；
- 同款替代供应商；
- 目标销售平台和利润阈值。

否则只是一次性翻译器。

## 4.7 开发难度

- 纯网页截图 Deal Audit：3.5/10
- Chrome extension + DOM 提取：5/10
- Compare/history：6/10
- 真正供应商风险数据库：8+/10

建议从 3.5～5/10 精品版切入。

---

# 5. 候选 #4：Flight Claim Router

## 精品竞品

AirHelp：

- 28M+ customers helped；
- 多语言；
- end-to-end claim；
- affiliate 当前 15% commission，平均成功 claim payout 约 €75；
- 强品牌、强 SEO、强法律流程。

Compensair：

- no-win-no-fee；
- 约 25% 成功费；
- 150+ airlines / 60 countries 级别覆盖；
- 2分钟 eligibility check。

Skycop / AirAdvisor：

- 覆盖更多法域；
- fee 差异明显；
- 对旅客来说核心比较点是净到账、覆盖法域、处理速度、是否接复杂案。

## 差异化

我们不应自己做法律追款团队。

可以做：

> 一次填航班 → 规则引擎判断法域与大致资格 → 对比多个 claim provider 的覆盖与预估净到账 → 用户选择。

Agnes 基本 0～1 次。

## 获客问题

虽然成交很强，但 SEO 已经被成熟公司长期占据，因此“精品产品”容易做，“低成本获得大量流量”反而最难。

所以当前优先级低于 Chargeback / FBA / China Deal Inspector。

---

# 6. 当前精品机会排序

| 排名 | 精品方向 | 获客入口 | 差异化清晰度 | 开发难度 | Agnes频率 | 当前结论 |
|---|---|---|---:|---:|---:|---|
| 1 | Chargeback Evidence Cockpit | Shopify App Store + 高意向SEO | 9.2 | 6 | 1～2/Case | 最值得做产品验证 |
| 2 | FBA Recovery Second Opinion | Amazon Appstore + 竞品后市场SEO | 9.4 | 4→7 | 0～1/Case | 差评缺口非常清楚 |
| 3 | China Deal Inspector | Chrome Web Store + SEO | 9.0 | 3.5→5 | 1/Audit | 最符合中外信息差 |
| 4 | Flight Claim Router | Google Search | 7.5 | 4 | 0～1 | 成交强但流量竞争太重 |

AutoTap 继续作为现有 Android 流量资产，不因这份矩阵删除；但不再无目的增加功能。

---

# 7. 下一步研发顺序

不马上开发四个产品。

### P0：把 Agnes 全局单飞 / 多 Key 服务端调度做成通用基础件

未来所有 Nova/Opportunity Factory 产品共用。

### P1：Chargeback Evidence Cockpit 做最窄验证

只做：

- Shopify 单店；
- open dispute；
- evidence readiness；
- 1次 Agnes build pack；
- 用户确认；
- 不自动提交。

### P1 并行：FBA CSV Second Opinion

不等 SP-API：

- 用户上传 Amazon 导出报表；
- 本地/服务端规则对账；
- 显示 reversal / underpayment / deadline；
- 单 case 点击后 1次 Agnes explain。

### P1 并行：China Deal Inspector Web

不先写扩展：

- Grok 生成真实工具页；
- 上传截图/输入 1688 URL/文本；
- 一次 Agnes 输出 deal audit；
- 看真实使用数据再决定 Chrome extension。

---

# 8. 以后竞品研究的固定方法

每个候选都必须先抓：

- 头部 3～5 个产品；
- 官方功能/收费结构；
- App Store/Chrome/Shopify/Amazon 的真实评价；
- 1星和2星评价单独聚类；
- 5星评价也单独聚类，因为它告诉我们用户真正购买的不是宣传文案而是什么；
- 人工支持强度；
- 用户离开的原因；
- 获客入口；
- 是否存在一个“头部产品不愿意做，但小团队能做到”的窄切口。

只有当差异化能用一句话说清楚，才进入开发池。
