# 跨市场灰边机会矩阵 V10

日期：2026-09-04

## 0. 本轮筛选原则

本轮不以“功能新颖”为主，而同时要求：

1. 需求池足够大或处于明显增长期；
2. 已有真实付费/订阅/高客单证明；
3. 获客入口清楚：Google / Chrome Web Store / Google Play / 平台内场景 / 社区；
4. 成交触发明确：用户正在花钱、赚钱、丢钱、错过稀缺机会或承担封店/违规风险；
5. 能借现成平台、公开数据、官方 API、第三方服务商或用户现有登录态完成，不自建重运营供给；
6. 差异化不能只是“加 AI”；
7. 一人公司开发与维护可控；
8. Agnes 视为永久免费，但每 Key RPM <20；任一 Agnes 请求期间，全产品所有 Agnes 入口全局置灰。优先 0–1 次/业务动作的批处理；
9. 灰边方向优先做“提醒、评分、辅助决策、证据整理”，不做验证码绕过、账号接管、隐藏自动下单、反检测或虚假申诉。

---

## 1. 当前综合排序

| 排名 | 机会 | 推荐载体 | 需求 | 付费 | 获客 | 信息差/借鸡 | 开发难度 | 平台/法律寿命 | 结论 |
|---|---|---|---:|---:|---:|---:|---:|---:|---|
| 1 | Reseller Deal Radar（二手捡漏/倒卖进货雷达） | Chrome + Web，后续 App 通知 | 9.5 | 9.2 | 9.0 | 9.2 | 5→7.5 | 6.5 | 当前最值得验证 |
| 2 | Visa Slot Intelligence（签证预约空位情报） | Chrome + App | 9.0 | 9.2 | 9.3 | 8.0 | 4.5→6 | 7.0 | 高意图、强节点 |
| 3 | China Supplier / Trade Intelligence（中国供应商与贸易情报） | Chrome + Web | 8.5 | 9.5 | 8.5 | 9.7 | 4.5→7.5 | 8.0 | 长期最符合中国优势 |
| 4 | Post-Purchase Price Protection（买后降价追回） | Chrome + App | 8.5 | 7.5 | 8.7 | 8.2 | 4.5→7 | 9.0 | 国内成熟玩法可外输 |
| 5 | Cross-Market Listing Compliance（跨平台上架合规） | Chrome | 8.5 | 8.7 | 8.5 | 7.5 | 4→6.5 | 7.5 | 有钱但正迅速商品化 |
| 6 | Cashback / Card Stack Optimizer | Chrome | 10 | 8.5 | 9 | 8 | 5→8 | 8 | 已验证但巨头过强，找地区缝隙 |
| 7 | Subscription Renewal Radar China（中国自动续费雷达） | Android | 9 | 7.5 | 7.5 | 8.5 | 5→8 | 8.5 | 海外成熟模式内移观察 |
| 8 | Settlement / Unclaimed Money Finder | App + Chrome | 8.5 | 8 | 8.5 | 8 | 4→6 | 8 | 已快速变红海，非当前首选 |

IEEPA / CAPE 等关税退款继续作为“节点项目”，不计入永久产品排名。

---

## 2. #1 Reseller Deal Radar：把中国“闲鱼捡漏”工作流外输

### 为什么现在值得重点验证

国内闲鱼监控已经形成成熟玩法：关键词、价格区间、卖家信用、多渠道推送、秒级新货提醒。闲鱼监控助手公开宣传平均约 4 秒上新提醒，并称有 1000+ 专业商家使用。

海外同样痛点正在迅速产品化，但截至 2026 年仍然碎片：

- FBM Alert：Facebook Marketplace 监控，23 Chrome 用户；免费 20 分钟刷新，Pro $9.99/月、10 分钟刷新；
- FlipRadar：2026 年上线，Facebook Marketplace 新货/降价提醒；
- Crawlbench：专业 reseller / dealer 工具，$39 / $79 / $199 / $499+ 每月，包含多城市监控、市场 comps、成交速度和提醒；
- Vinted / Depop 也出现 $10 左右到数十英镑/月的监控、估值、AI 品相/假货判断工具。

这证明“更快看到低价货”不是普通效率工具，而是直接影响用户进货利润。

### 精品差异化：不要做普通关键词提醒

普通提醒已经很容易复制。首版应该围绕 **Profit Gate**：

用户定义：
- 品类 / 型号；
- 区域；
- 最大进货价；
- 目标转售平台；
- 最低利润 / ROI；
- 可接受品相。

系统只推送“预计利润超过门槛”的货：

- 当前售价；
- 近期市场参考价；
- 平台费用；
- 预计物流；
- 预计净利润；
- 预计周转速度；
- 卖家风险；
- 文字/图片中明显的品相问题。

Agnes 只在遇到陌生型号、模糊图片或复杂描述时按批次调用一次；常见型号和历史成交缓存，不对每次抓取调用。

### 获客

Chrome Web Store：
- facebook marketplace alerts
- marketplace monitor
- vinted alerts
- depop alerts
- reseller sourcing
- flipping tool

Google / Grok 工具页：
- Facebook Marketplace deal calculator
- PS5 flipping margin calculator
- used iPhone resale margin
- Vinted reseller calculator
- furniture flipping calculator

社区：Reddit / YouTube / Facebook reseller 群体。内容重点不是宣传插件，而是“某类货现在什么价值得买”。

### 成交

免费：1 个监控 + 慢刷新 / 本地试算。

用户第一次看到：
> 这个 listing 若按你设定的平台转卖，预计净赚 $83；同类过去 X 天中位报价 $Y。

再触发：多监控、多城市、更快提醒、历史 comps、Telegram/手机通知。

### 防绕开

用户可以学会判断一件货，但无法持续人工刷新 24 小时；真正壁垒是：
- 连续监控；
- 历史行情；
- 用户自己的 buy box；
- 多城市 / 多平台；
- 模型识别；
- 通知速度。

### 灰边与寿命

Facebook 明确把未经授权的自动 scraping 视为可能违反条款。不能把业务建立在反检测抓取上。优先顺序：
1. eBay：官方 Browse API 支持关键词/图片搜索，且官方 Inventory Refresh 能订阅价格、可用性等变化；
2. 有官方 API / feed 的二手平台；
3. 对必须依赖用户页面的站点，仅在用户本机、用户已登录会话里做保守刷新，并接受平台变化导致的维护风险；
4. 不做 CAPTCHA 绕过、账号池、自动购买。

因此 V1 最好先证明 **Deal Scoring + eBay/一类合法数据源**，再决定是否扩 Facebook/Vinted。

---

## 3. #2 Visa Slot Intelligence：高焦虑、高意图的“稀缺时间槽”市场

### 市场证明

- Check US Visa Slots Chrome：约 80,000 用户、4.6 分、1,100+评分；
- FindVisaSlots：5 日 Premium $9、30 日 $24、Agent / Power User $39+/月；
- 产品直接面向 F1/H1B/B1/B2 等，并依赖社区 slot 报告 + 用户自己的登录会话。

用户不是为了“方便”付费，而是因为：
- 开学；
- 入职；
- 出差；
- 婚礼/旅行；
- 身份/时间窗口。

错过一个提前 slot 的损失远高于几十美元订阅。

### 竞品优点

- 社区共享 slots，网络效应；
- Quick Login；
- 按日期范围和签证类型提醒；
- Agent 多客户方案；
- 已经教育用户付费。

### 差异化机会

不要自动抢号。做：

**Slot Confidence + Cooldown Guard**

- 不同城市/签证类型的 slot 可信度；
- “多少人多久前看到”；
- 当前登录检查次数；
- 风险冷却提醒，防止用户过度刷新导致 portal lock；
- 基于社区数据估计“哪个时段更值得守”；
- 手机 push / Telegram；
- 一键进入官方页面，最终由用户确认预约。

进一步可以把同一引擎复制到其它“官方预约槽”市场，但不同站点必须独立验证条款。

### 获客

- CWS：visa slots / us visa slots / earlier appointment；
- Google：`F1 visa slots [country]`, `H1B visa appointment [city]`；
- 留学/移民/工作签证社群；
- B2B：移民顾问/留学中介使用 Agent 版。

### 灰边

监控提醒处于规则边缘；自动预订、账号代持和过度请求风险明显更高。产品应该明确停在“情报 + 用户人工确认”。

---

## 4. #3 China Supplier / Trade Intelligence：最符合中国信息差的长期精品

### 市场证明

海外 B2B 数据愿意付高价：ImportGenius 当前美国贸易数据起价约 $199–$229/月，Pro 约 $399–$449/月，企业方案更高。

Chrome 上 Apollo B2B enrichment 已有 1,000,000 用户，说明“用户正在看一个公司网页时，侧边直接补充商业情报”的载体非常成熟。

中国供应商验证却仍较碎：
- 1688 官方采购插件已经覆盖大量基础采购/AI能力；
- 新的 1688 Supplier Intelligence 已经做 trust score、价格风险、2–5 家比较，Starter $29/月、Pro $79/月，但当前用户很少；
- Alibaba Company Background Check 等新插件只有个位数用户。

### 不做什么

- 不再做普通中文翻译；
- 不只给一个 82/100 供应商评分；
- 不销售或聚合个人隐私信息；
- 不假装官方企业征信。

### 精品定位

**China Supplier Deal Room / Supplier Evidence Layer**

用户在 Alibaba / 1688 页面点击一次：

1. 提取页面上的公司主体、商品、MOQ、阶梯价、规格；
2. 匹配公开企业主体和可合法使用的数据；
3. 展示成立时间、经营异常/变更、公开诉讼/行政信号（必须有来源）；
4. 加入公开贸易记录/出货轨迹（如未来有合法数据源）；
5. 计算 Landed Cost / MOQ 现金占用 / 目标平台利润；
6. 保存多个供应商，持续记录报价和关键条件变化；
7. 需要复杂中文语义时，一次 Agnes 批处理。

真正的不可绕开层不是“一次查公司”，而是供应商历史、报价历史、订单风险、物流/验货/贸易数据的持续档案。

### 借鸡生蛋

A：海外进口商 / Amazon/Shopify/TikTok Shop 卖家。

B：1688/Alibaba 中国供应商 + 第三方公开企业数据 + 货代/验货/采购服务。

我们做：判断 + 历史 + 匹配 + Lead 路由。

可进一步转介：验货、货代、采购代理、关务等；要透明披露任何 referral / affiliate 关系。

### 获客

- Chrome：alibaba supplier verification / 1688 supplier check / china supplier check；
- Web SEO：`is this 1688 supplier legit`, `1688 landed cost`, `Alibaba supplier background check`, `China factory verification`；
- YouTube / Reddit / Shopify seller communities；
- 中国 sourcing 内容的英文工具化。

---

## 5. #4 Post-Purchase Price Protection：把国内“自动保价”玩法外输

### 国内证明

购物党脚本累计安装约 30.9 万，并长期提供 365 天历史价格、同款比价、降价提醒和京东价格保护自动监控。国内消费者已经被教育成：不仅买前比价，买后也要盯保价。

### 海外相同痛点

海外主流价格追踪很大，但主要聚焦买前：Keepa Chrome 约 400 万用户。

另一方面，美国零售商仍存在明确的“买后自身降价可申请调整”政策，例如：
- Best Buy：在退换货窗口内，如果 Best Buy 自己降价，可主动申请匹配；
- Target：符合条件的商品可在购买后 14 天内申请自身降价匹配。

因此存在一个被 Keepa/Honey 类产品覆盖不充分的阶段：**我已经买了，现在还能不能拿回差价？**

### 产品

Chrome / App 记录用户刚买的高价值商品：
- 商店；
- 购买日期；
- 原价；
- 调价截止日；
- 当前价。

只在满足规则时通知：
> Best Buy 当前低 $46，仍在可申请窗口；这是官方申请入口和需要准备的信息。

不自动冒充用户联系客服，不伪造证明。

### 获客

- `best buy price adjustment after purchase`
- `target price drop refund`
- `price protection after purchase`
- `get refund when price drops`

Chrome 商店则主打 `Price Protection / Price Drop Refund`，与普通“买前 price tracker”区分。

### 变现难点

需求真实，但普通消费者的订阅意愿可能低于 reseller / visa。更适合：
- 免费入口 + affiliate（必须符合 Chrome affiliate 政策：明确披露、用户有直接利益、每次相关用户动作后才能加 affiliate）；
- Lifetime；
- 与信用卡 price protection / shopping reward 后续组合。

因此列为高价值验证，不立即大开发。

---

## 6. #5 Cross-Market Listing Compliance：国内跨境 ERP 能力外输，但必须避开平台原生商品化

国内跨境 ERP 已习惯给卖家做违禁词、平台规则检查、批量刊登等。

海外 2026 年刚密集出现 Chrome：
- Listing Lint：Amazon/TikTok Shop/Etsy/Meta 风险词和来源链接；
- ScanShield：TikTok Shop 200+ 规则 + AI、图片 claims、风险分、申诉包；收费 $14.99–$129/月。

这证明付费逻辑成立：避免产品下架、账号处罚和广告拒审。

但 TikTok Shop 已经上线官方 Video Pre-Check，能在发布前检测健康、误导、IP 等问题并给修改建议。平台原生能力会持续蚕食简单 scanner。

### 差异化

不做 TikTok-only“敏感词扫描”。

做 **Cross-Market Claim Compiler**：
- 同一个商品准备上 Amazon / TikTok / Meta / Etsy；
- 一次扫描标题、bullet、图片文字、视频口播；
- 每条风险必须引用当前平台政策来源；
- 输出“这个 claim 在 A 可以，在 B 风险高，在 C 必须加限定语”；
- 本地规则优先，复杂 rewrite 一次 Agnes。

适合保留为观察方向，不列前三。

---

## 7. #6 Cashback / Card Stack：巨大验证市场，但不宜正面打 Honey/Rakuten

Rakuten Chrome 约 300 万用户，覆盖 3500+ 商店；CardPointers 约 5 万用户，专门整理 Amex/Chase/Citi 等卡券和 rewards；Keepa 约 400 万。

这说明“购物页面上直接告诉我怎样省最多”拥有巨量市场。

国内也长期有返利网、什么值得买、慢慢买、购物党等成熟行为。

但是 Honey / Rakuten / Capital One Shopping 等已经占住美国通用市场。

### 可研究缝隙

- 加拿大 / 英国 / 澳大利亚的本地银行卡/优惠组合；
- 跨境购物的 cashback + coupon + card offer + FX fee；
- 明确让用户自己选择 affiliate 路径，透明显示我们可能获得佣金。

Chrome 官方要求 affiliate 必须显著披露，并且只有在用户明确动作且能获得直接利益时才能添加 code/link/cookie。因此不能做隐藏 cookie 劫持或替换原 affiliate。

结论：流量和商业模式都已验证，但竞争太强，只做国家/人群窄切口。

---

## 8. 国外成熟 → 国内仍有同痛点：Subscription Renewal Radar China

Rocket Money 已把“识别订阅 + 到期提醒 + 代取消”做成成熟付费金融工具，并称已替会员取消近 250 万份订阅。

中国同痛点明显存在：2026 年第一财经报道黑猫平台“自动续费”相关投诉超过 21.7 万条；北京市场监管还处罚过“一分钱试用”弱提示、默认捆绑自动续费、退订困难的案例。

### 中国可以怎样做而不依赖 Agnes 高频调用

Android 本地优先：
- 通知/短信/截图导入；
- 用户手工确认扣款商户；
- 识别重复周期；
- 到期前提醒；
- 维护“微信/支付宝/苹果/安卓商店/具体服务商取消路径”；
- 对疑似不合理续费生成维权资料清单，而不是自动伪造投诉。

### 最大问题

真正自动识别所有支付账户比美国 Plaid 模式难；微信/支付宝/银行数据接入是产品瓶颈。因此先作为“国外成熟模式内移”观察池，不作为当前海外主线。

---

## 9. Settlement / Unclaimed Money：真钱诱惑很强，但 2026 已快速红海

Google Play 当前已有多款 100K+ 下载：Collect、TrackOne、ClaimWise、Payout 等。Collect 有约 9.5K 评论并强制订阅，用户评论显示确实有人愿付，但也有人强烈反感先付费才能看到机会。

Chrome 也出现 Claim Chowder，按用户浏览网页/历史在本地匹配公开 settlement。

可见需求、付费和 viral 都成立，但已经高速商品化。

若以后重做，只考虑更窄的：
- business-only settlements / fee refunds；
- 某类平台商家；
- 与现有账单/商家 dashboard 的本地匹配。

不做普通消费者 settlement list 克隆。

---

## 10. “灰色产业”应该怎样转化成可长期产品

| 黑灰市场已经证明的需求 | 不做 | 可以做的长期镜像 |
|---|---|---|
| 二手抢货机器人 | CAPTCHA/反检测/自动买 | 新货监控 + 利润评分 + 人工购买 |
| 签证/医院抢号 | 自动占号、账号代持 | slot 情报 + 冷却保护 + 人工确认 |
| Coupon / affiliate 劫持 | 偷换 cookie / 隐藏 affiliate | 透明比较 + 用户主动激活返利 |
| 刷单/虚假评价 | 自动批量评价 | listing/广告合规、评价风险分析 |
| 退款欺诈 | 假证据/假申诉 | Seller recovery / chargeback defense |
| 账号/联系人爬取 | 绕过登录/批量隐私采集 | 公开 B2B 企业信息与供应商验证 |
| 票务/限量品 bot | 队列/验证码绕过 | restock / price / resale margin intelligence |

核心思路：黑灰产业很会找到“哪里有钱”，我们利用它做需求雷达，但把产品停在情报、判断和用户确认层。

---

## 11. 载体选择

### Chrome 优先

当需求发生在用户“正在浏览网页并准备做决定”的那一刻：
- Reseller Deal Radar
- China Supplier Intelligence
- Post-Purchase Price Protection
- Listing Compliance
- Cashback/Card Stack

### Google Play / Android 优先

需要移动原生能力、通知、长期后台或手机场景：
- Visa Slot companion / push
- Subscription Renewal Radar China
- 现有 AutoTap / Android Automation

### Web / Grok 优先

凡是可先用搜索工具验证的方向都先 Web：
- 每个平台/品类 resale margin calculator；
- visa slot country/city pages；
- 1688 landed cost / supplier check；
- price adjustment eligibility；
- transient refund/policy nodes。

Web 数据证明有搜索→使用→回访/CTA，再开发 Chrome/App。

---

## 12. Agnes Single Flight 统一规则

所有未来产品共用：

1. 用户点击任意 Agnes 功能；
2. 获取全局 `ai_lock`；
3. 所有 Agnes 依赖按钮/模块立即置灰；
4. 本地功能继续可用；
5. 把该业务所需数据一次打包调用；
6. 服务端从多账号 Key 池选择 RPM 最空闲的 Key；
7. 单 Key 不贴 20 RPM 上限运行，达到内部阈值则换 Key/队列；
8. 请求成功、失败或超时都释放 `ai_lock`；
9. 相同输入 hash 缓存，重复结果不再调用。

尤其 Reseller Radar 的正常监控绝不能每条 listing 调 Agnes。先规则/缓存/本地模型过滤到很小的候选，再让用户主动点击 AI 深析。

---

## 13. 下一阶段只验证前三名

### A. Reseller Deal Radar

先验证：
- eBay 官方 API 的可用覆盖与 refresh 机制；
- 20 个利润导向长尾词；
- 3 个高流通品类（例如手机/游戏机/工具或家具，最终由搜索量与 reseller 社区决定）；
- 一个 Web Profit Gate，先不依赖灰色抓取。

### B. Visa Slot Intelligence

先验证：
- 各国家 CWS / Google 搜索需求；
- 竞品差评中 portal lock、误报、延迟、账号隐私的频率；
- community-data 模式是否还能形成差异化；
- 不做自动 booking。

### C. China Supplier / Trade Intelligence

先验证：
- 20 个最接近付款/定金的关键词；
- 公开企业/贸易数据的合法可持续来源；
- 一个“供应商页 → Deal Room”的 Chrome prototype；
- 货代/验货/采购服务的 referral B 方。

只有这三条的获客/成交验证过关，才进入正式开发。