# Macro Marketplace MV3 Distribution V15

## 关键纠正

“服务器修一个 Macro / Adapter，所有 Chrome 用户立即下载新逻辑并执行”不能作为默认架构。

Chrome Web Store Manifest V3 要求扩展逻辑可从提交包中审查。典型违规包括：
- 远程加载并执行 JS/WASM；
- eval 远程字符串；
- **把复杂命令伪装成 JSON/数据，再由扩展解释器执行**。

因此 Macro Marketplace 若把远程 DSL 当作任意执行逻辑，存在直接审核风险。

## 可行的三条通道

### A. Packaged Verified Actions（默认主通道）

扩展包内置固定执行原语与站点 Adapter：
- CLICK_SEMANTIC
- TYPE_FIELD
- WAIT_STATE
- OPEN_PAGE
- EXPORT_FILE
- READ_TABLE
- 等

官方/精品 Site Adapter 的逻辑跟随 Chrome Web Store 扩展版本发布。一次共享修复仍然能覆盖所有引用者，但传播方式是 **Extension auto-update**，不是服务器即时热更新。

远程服务器只保存：
- 兼容状态
- 健康度
- 版本元数据
- 是否启用某个已打包能力
- 参数/非逻辑数据

不能依赖远程配置改变核心执行逻辑。

优点：普通用户零额外开发者授权，审核路径最稳。
缺点：Adapter 修复受扩展更新/审核传播速度影响。

### B. User Scripts API（Creator / Marketplace 高级通道）

Chrome 官方 `chrome.userScripts` 专门允许执行用户提供、无法随扩展包一起发布的脚本。

Chrome 138+：用户必须在扩展详情页显式打开 `Allow User Scripts`。新安装默认关闭。

适合：
- 用户自己录制/生成的高级宏；
- 用户明确点击“安装这个 Creator Workflow”；
- 市场宏安装后作为用户选择的脚本注册。

产品必须明确：
1. 用户主动安装 Macro；
2. 明确显示它会访问哪些站点、做什么动作；
3. 高风险动作继续要求额外确认；
4. 未开启 Allow User Scripts 时给出清晰引导，不偷偷降级为远程解释器。

优点：最符合“创作者市场、远程下载后用户主动执行”的政策路径。
缺点：第一次使用有明显授权摩擦，可能降低普通用户转化。

### C. Debugger API（不作为首版默认）

`chrome.debugger` 属于 MV3 允许的远程逻辑豁免 API，可通过 CDP 操作页面，但需要 `debugger` 权限并产生明显权限/安全感知。

适合极高级、专业 RPA 用户；首版不建议把整个大众产品建立在 debugger 权限上。

## 对 Macro Marketplace 商业模型的影响

### 不是取消“同步修复”，而是分级

#### 标准站点 Action 修复
例如 `shopify.export_orders`：
- Adapter 代码打包在扩展；
- 修复 -> 发新 extension version；
- Chrome 自动更新后所有引用宏一起恢复。

仍然是一处修复覆盖大量宏，只是不是服务器秒级热修。

#### Creator Macro 修复
若走 User Scripts：
- Creator 发布新版；
- 用户已明确订阅/安装该 Macro；
- Nova 提示版本更新与变更内容；
- 用户允许的 User Scripts 通道更新。

这种模式最接近“应用市场”。

## 重新定义产品层级

### 普通模式（低摩擦）
使用 Nova 内置/已审核的 Semantic Actions；适合 Shopify/Amazon 等常见站点和高频工作流。

### Creator Mode（高能力）
用户开启 `Allow User Scripts`，可：
- 自己录制复杂宏；
- AI 优化；
- 安装第三方 Creator Macro；
- 分享/出售；
- 接受持续维护更新。

这样把 Chrome 授权摩擦限制在真正需要 Marketplace 高级能力的人群，不强迫所有普通用户开启。

## 对护城河的影响

护城河不应依赖“远程下发代码”，而应变成：
- Site Adapter 兼容库；
- Semantic Action 抽象；
- 不同 viewport/locale/权限 Variant；
- 大规模匿名成功/失败健康数据；
- Creator 声誉、版本历史与维护质量；
- Macro 依赖图（一个 Adapter 更新影响哪些宏）；
- AI Repair 的受约束候选数据。

这些仍然具备网络效应。

## P0 验证顺序更新

1. 先过 V14 本地 Semantic Action Live Gate。
2. 建一个最小 Chrome MV3 原型，只支持 packaged actions，不做远程 Macro Marketplace。
3. 单独做 User Scripts API 可用性/授权转化实验：安装后有多少用户愿意开启 `Allow User Scripts`。
4. 只有高级授权率和 Creator 需求成立，才开发交易市场。
5. `debugger` 暂不作为主路径。

## 当前判断

Macro Marketplace 仍值得研究，但难度从“浏览器录制器 + 市场”上调：
- 普通 Macro Engine：约 6~7/10
- Maintained Site Adapter：约 7.5/10
- 合规 Creator Marketplace：约 8~8.5/10

真正的 Gate 已不只是 Agnes 能力，而是三个同时成立：
1. 本地跨布局恢复足够高；
2. Chrome MV3 分发/授权摩擦可接受；
3. 用户愿意为持续维护的 Macro 付费或创作者愿意生产供给。
