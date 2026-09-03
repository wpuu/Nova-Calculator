# Nova Macro 下一阶段真实门槛 V17

日期：2026-09-04

## 当前状态

- 已有最小 MV3 代码原型；
- 已有真实 Chromium DOM synthetic 测试；
- 已知变体：Generic 9/18，Shopify Adapter 18/18；
- 尚未证明真实 Shopify Admin、真实 Chrome unpacked extension、Agnes。

## 下一步只做三件事

### 1. 扩展加载与真实录制链

必须在能真实注册 unpacked MV3 extension 的 Chrome 环境完成：

`Start -> click/input -> Stop -> storage -> Replay`

失败即先修基础工程，不继续扩功能。

### 2. 五个真实 Shopify Semantic Actions

不能只测试 Export Orders。候选动作应覆盖不同交互结构：

1. `shopify.export_orders`
2. `shopify.filter_orders_by_date`
3. `shopify.open_order`
4. `shopify.export_products` 或类似报表/导出动作
5. `shopify.navigate_section` / menu action

每个动作必须测试：

- 宽/窄 viewport；
- 缩放导致的 effective CSS width；
- direct button / menu path；
- locale 差异（有真实条件时）；
- permission / disabled；
- 第三方扩展噪声。

目标：本地层 >=90%，危险误点 0。

### 3. Hold-out + Agnes

Adapter 开发者不知道 hold-out 页面具体变体。

本地层不能判断时，只生成 5~10 个真实候选给 Agnes。

Agnes只允许输出：

- candidate id
- `ABSTAIN`

禁止模型生成任意 selector、任意 JS、任意点击路径。

## 不提前做

- Marketplace；
- 付费；
- 云执行；
- Creator经济；
- 远程可执行宏；
- 自动处理 CAPTCHA / 2FA；
- 自动支付/下单/删除。

## 为什么暂不继续横向加功能

当前最大的未知数已经不是“能否设计出更多功能”，而是：

> Site Adapter 是否能把真实网页差异压缩成可维护的少量 Semantic Actions。

如果真实 Shopify 五个动作都需要大量每用户特例，则 Macro Marketplace 应降级。

如果五个动作在真实环境能稳定 >=90%，才值得扩大到 Amazon / Etsy / 1688，并测试共享 Adapter 的维护网络效应。
