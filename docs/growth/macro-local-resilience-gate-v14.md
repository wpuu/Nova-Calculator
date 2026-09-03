# Macro Local Resilience Gate V14

## 目标

验证“同一个网页动作能否跨窗口尺寸、缩放、语言、A/B UI、DOM/CSS 改版共享恢复”，而不是假设一处修复能覆盖所有用户。

本阶段**优先验证 0 Agnes 的本地层**。只有本地无法确定时才允许进入 Agnes 候选判断；正常宏运行不调用 Agnes。

## 为什么要先做这一步

传统坐标宏无法规模化：窗口大小、缩放、响应式布局一变化就失效。固定 CSS/XPath 同样脆弱。现代自动化的共同方向是优先使用用户可感知语义和多属性定位：ARIA role / accessible name / label / text / stable attribute / 上下文区域等；不把 DOM 深度、CSS class、屏幕坐标作为主身份。

Shopify 当前 Polaris 大量采用 Web Components 与 container-query 响应式布局，同一功能在不同宽度下可能直接显示、折叠进菜单或换容器；因此 Adapter 必须允许同一 Semantic Action 对应多个 UI Variant。

## 三层结构

### 1. Target Fingerprint
录制时不只保存 selector，而是保存：
- role family（button/menuitem 等）
- accessible name 与多语言 alias
- 稳定 data-* / action 属性（若存在）
- 所在业务区域（Orders / Products 等）
- 动作语义 kind（export/create/delete 等）
- 可见/可用状态
- 可选 DOM/ARIA snapshot 作为弱证据

不把坐标、DOM depth、CSS class 当主键。

### 2. Site Adapter / Semantic Action
例：`shopify.export_orders`。

它表达的是“在 Shopify 完成订单导出”，不是某个 selector。允许同时存在：
- 宽屏直接按钮
- 窄屏 More actions -> Export
- 不同语言
- A/B 新旧版
- 权限不同导致不可用/不存在

### 3. User Macro
用户宏引用 Semantic Action：
`打开 Orders -> 设日期 -> shopify.export_orders -> 等待下载`

站点改版优先修共享 Adapter，而不是逐个修改所有用户宏。

## 本地决策

候选元素由本地代码从当前真实 DOM 中生成，按多信号评分。原型权重：
- stable attribute：30
- accessible name / alias：25
- role family：15
- business context：15
- action kind：15

硬规则：
- `dangerous=true`：禁止自动选择
- 不可见/不可用：不能自动点击
- 分数过低：ABSTAIN
- 高分但候选接近：AI_REVIEW
- 高置信度且与第二名有足够差距：AUTO

Agnes 若进入流程，只能从 Nova 已确认存在的候选中选一个或 `ABSTAIN`，不能凭空生成可执行 selector。

## V14 合成基准

已建立 20 个 synthetic variants，覆盖：
- 1920 宽屏直接按钮
- 1024 窄屏折叠菜单
- 125% / 175% zoom
- 中文 / 德语 / 法语
- CSS class churn
- DOM wrapper depth churn
- 内部 Web Component 标签改变
- A/B 文案变化但 stable attr 保留
- 其他扩展插入相似按钮
- 两个同分候选 -> 必须 AI_REVIEW
- 旧按钮隐藏、新菜单项可见
- disabled 状态
- 权限不足
- 只有错误业务区域按钮
- 危险删除按钮
- 文案丢失但 stable attr 保留
- 上下文改名但 stable attr 保留

当前原型在这 20 个**人为已知**样例上达到 20/20。这个成绩只证明架构和决策门槛能表达预期行为，**不证明真实 Shopify 成功率**。

## 真正的生死门槛

### Gate A：Synthetic
- 本地层 known variants：>= 95%
- 危险误操作：0
- 模糊情况必须进入 AI_REVIEW/ABSTAIN，不能强猜

### Gate B：Live cross-layout（不调用 Agnes）
同一个真实动作至少测试：
- 3 个 viewport 档位
- 3 个 zoom 档位
- 至少 2 个 locale
- 至少 2 类账号权限/功能可见性（能取得测试账号时）
- 菜单展开/收起、滚动前后

目标：本地直接成功 >= 90%，错误动作 = 0。

### Gate C：未知改版
人为制造此前没有加入 alias/规则的新 DOM 变体：
- 本地层直接恢复 >= 75~80%
- 其余正确进入 AI_REVIEW/ABSTAIN
- 加 Agnes 候选选择后整体正确决策 >= 95%
- 危险误操作仍必须 0

### Gate D：共享更新价值
至少构造 20 个不同 User Macro，共用 3~5 个 Semantic Actions。修改一个 Site Adapter 后，验证所有引用宏无需逐个修改即可恢复。

如果 Gate B 本地层只能做到约 50~60%，说明维护成本仍会随用户数上升，Macro Marketplace 降级；如果本地层接近 90%，Agnes 只处理少量歧义，则该商业模型明显更可行。

## 与 Agnes RPM 的关系

- 正常执行：0 Agnes
- 本地高置信恢复：0 Agnes
- 创建/优化宏：用户主动点一次 -> 1 请求
- 模糊修复：用户主动点一次 -> 1 请求
- 任意 Agnes 请求进行时，全产品所有 Agnes 入口统一置灰
- 多账号 Key 仅服务端调度；单 Key 继续保留 RPM 安全余量

因此真正规模瓶颈应是 Adapter/兼容数据，而不是 Agnes RPM。

## 当前结论

“Shopify 一处修复覆盖多数用户”在工程上有条件成立，但条件是：

1. 修复对象必须是共享 Semantic Action / Site Adapter；
2. 页面尺寸差异被视为 UI Variant，而不是不同坐标脚本；
3. 本地多信号定位承担绝大多数匹配；
4. 对未知/危险情况宁可停手；
5. Agnes 只做候选消歧，不直接控制开放式网页 Agent。

下一步优先做 Live Gate，而不是继续扩 Marketplace 功能。