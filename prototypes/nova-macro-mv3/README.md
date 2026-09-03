# Nova Macro Semantic POC

这是 V16 的最小 Chrome MV3 证明原型，不是可发布产品。

## 已实现

- `activeTab + scripting + storage` 最小权限；
- 用户主动开始/停止录制；
- click / 普通 input 记录 semantic fingerprint；
- password / one-time-code 不记录；
- Shopify `Orders -> Export` Semantic Action 识别；
- 本地高置信 semantic replay；
- responsive menu 安全展开后重找目标；
- 歧义 -> `AI_REVIEW`；
- 缺失/disabled -> `ABSTAIN`；
- Delete / Pay / Purchase 类动作不自动执行；
- 正常 replay 不调用 Agnes。

## 当前验证状态

- JSON synthetic gate：已通过前一轮矩阵；
- Chromium 144 真实 DOM synthetic gate：
  - generic matcher：9 / 18；
  - Shopify Site Adapter：18 / 18；
- 真实 Shopify Admin：未测试；
- unpacked Chrome extension 加载：当前容器 Chromium 环境无法可靠注册 `--load-extension`，因此 **未验证**；
- Agnes：未接入真实 Key，因此 **未验证**。

18 / 18 仅表示我们构造的已知 Shopify-like DOM 变体通过，不能外推成真实 Shopify 成功率。

## 下一门槛

1. 真 Chrome 环境加载 unpacked extension；
2. 真实网页完成 start -> record -> stop -> replay；
3. 至少 5 个 Shopify Semantic Actions；
4. viewport / zoom / locale / permission / responsive variant；
5. hold-out 未知变体；
6. 最后才接 Agnes 做受约束 candidate selection。

## 非目标

当前不做：

- Marketplace；
- 云浏览器；
- 远程下载可执行宏；
- CAPTCHA / 2FA；
- 自动支付/删除/下单；
- 任意远程 JavaScript 执行。
