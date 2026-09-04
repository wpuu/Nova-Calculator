# Nova Macro — Grok 4.6 High 前端边界 V19

## 结论

Grok 4.6 High 适合生成 Nova Macro 的前端，但只负责可替换的 UI Shell，不负责自动化核心。

当前阶段不先追求视觉精品。先把真实录制/重放/语义恢复闭环跑通，再让 Grok 按稳定接口一次性生成 Side Panel / Popup / Marketing Web。

## Grok 可以负责

1. Chrome Extension Side Panel / Popup UI
2. 宏列表、步骤列表、状态展示
3. 录制中的视觉反馈
4. Replay 进度与失败位置
5. AI Review 候选选择界面
6. Dangerous Action 确认界面
7. Creator / Marketplace 的未来页面壳
8. Web SEO / landing pages
9. 多语言 UI 文案布局

## Grok 不允许负责

1. Semantic Matcher
2. Site Adapter / Semantic Action 实现
3. DOM 候选生成和排序算法
4. 危险动作判定
5. Password / OTP 保护
6. Chrome 权限模型
7. User Scripts / MV3 合规执行策略
8. Agnes Key、RPM、调度、Single Flight
9. 远程代码/DSL执行方案
10. 购买/授权安全边界

## 原则

前端只能向核心发送有限命令：

- START_RECORDING
- STOP_RECORDING
- SAVE_MACRO
- REPLAY_MACRO
- CANCEL_REPLAY
- REQUEST_AI_REVIEW
- CONFIRM_DANGEROUS_STEP
- REJECT_DANGEROUS_STEP
- SELECT_REPAIR_CANDIDATE
- ABSTAIN_REPAIR

不能提交任意 JavaScript、任意 selector 脚本或远程执行代码。

## UI 核心状态

- IDLE
- RECORDING
- SAVED
- REPLAYING
- COMPLETED
- AI_REVIEW
- REQUIRES_CONFIRMATION
- ABSTAIN
- BLOCKED_SENSITIVE_INPUT
- ERROR

## 第一版建议结构

### Side Panel 主界面

顶部：当前网站 + 当前宏名称 + Core 状态

主区域：
- Record / Stop
- 最近一次宏
- Step timeline
- Replay
- 失败时显示失败步骤和原因

### AI Review

只展示核心已经生成的 2–5 个真实候选：
- 候选文字
- role
- 所在区域
- 匹配分
- 可选截图/局部DOM摘要

用户只能：
- 选择候选
- ABSTAIN

Grok 不生成候选。

### Dangerous Confirmation

明确显示：
- 即将执行的动作
- 页面/目标
- 为什么被标记为高风险

默认拒绝；用户明确确认后仅执行当前一步。

## 视觉方向

目标不是“AI聊天机器人”。应该更像专业自动化工具：
- 状态非常清楚
- 失败位置非常清楚
- 每一步可追溯
- 高风险动作明显区分
- AI 是辅助修复，不是主界面

避免：
- 大面积聊天框
- 夸张 AI 动画
- 把所有功能放一个首页
- 用漂亮视觉掩盖不确定状态

## 何时正式让 Grok 出前端

必须至少满足：

1. 真正 Chrome 环境可以加载 MV3 原型；
2. Record -> Stop -> Replay 闭环能跑；
3. 至少 3 个不同结构动作能重放；
4. AI_REVIEW / ABSTAIN / REQUIRES_CONFIRMATION 状态已稳定；
5. UI Contract 不再频繁改变。

未满足前，只允许 Grok 生成低成本草图，不把视觉实现当正式代码。

## 后续前端拆分

### Extension
精品、少页面、强交互。

### Web
Grok 可大量生成真实工具页、教程页、Macro landing page；Web 不受 Chrome 发布槽位约束。

### Marketplace
最后做。只有 Creator 供给和宏维护价值被验证后再生成。
