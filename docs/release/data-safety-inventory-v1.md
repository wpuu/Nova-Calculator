# Nova Data Safety Inventory V1

## 目的

这份文件记录 **当前代码实际处理的数据**，用于后续填写 Google Play Data Safety、Privacy Policy 和 Accessibility declaration。

它不是法律结论；Play Console 的最终选项必须按发布时界面和实际生产配置核对。但任何声明不得与本事实表冲突。

## 总原则

Nova 商业版当前设计：

- 不使用广告 ID。
- 不用硬件设备 ID 作为安装身份。
- 不上传 AutoTap 点击坐标。
- 不上传目标 App 的窗口文本或屏幕内容。
- 不上传用户文件、联系人或通讯录。
- 不把 Nova 产品事件写入原 Calculator++ Firebase/GA。
- Provider/model/API key/上游地址不进入客户端业务请求或产品分析。

## 1. 本地计算器

### 本地处理

普通计算器表达式和确定性计算结果默认在设备本地处理。

### 产品分析

产品漏斗事件 **不包含**：

- expression 原文；
- deterministic result；
- calculator history 内容。

## 2. AI Explain

只有用户主动调用 AI 功能时，客户端才会构造 Nova Gateway 请求。

当前 `AiGatewayRequest` 包含：

- requestId；
- operation；
- expression；
- deterministicResult；
- localeTag。

这些内容会发送到 Nova 自有 AI Gateway，以生成用户请求的 AI 解释。

客户端请求不包含：

- provider name；
- model name；
- provider API key；
- upstream provider URL。

Nova Gateway 可将完成请求所需内容转发给受控 AI inference provider。Privacy Policy 必须明确：**用户主动提交给 AI 功能的内容需要离开设备进行处理。**

不要把“普通本地计算器不上传表达式”误写成“Nova 永远不上传任何表达式”。AI Explain 是明确例外。

## 3. AutoTap / AccessibilityService

### 设备内使用

AccessibilityService 用于用户明确配置的确定性 AutoTap：

- 显示两个可拖动目标；
- 读取音量键用于开始/停止；
- dispatch user-defined tap gestures；
- 处理 display/configuration 变化；
- 维持 overlay 状态。

### 不上传

AutoTap 产品分析和 Gateway 当前不上传：

- 精确点击坐标；
- 目标 App 名称；
- 目标 App 窗口文本；
- 屏幕截图；
- AccessibilityEvent 内容；
- 用户在其他 App 中输入的文本。

产品分析只记录固定白名单事件，例如：

- AutoTap 设置入口；
- disclosure accepted；
- overlay ready；
- saved profile / loaded profile；
- paywall / purchase funnel；
- failure code。

`autotap_run_failed` 允许的诊断属性仅包括：

- failureCode；
- manufacturer。

## 4. App-local installation ID

Nova 会生成一个随机 UUID 作为 app-local installation ID。

特点：

- 使用 `UUID.randomUUID()`；
- 保存在 Nova 自己的 SharedPreferences；
- 与 IMEI、Android ID、MAC、广告 ID 等硬件/广告标识无关；
- 用于获得伪匿名 Nova session / 服务配额身份。

Gateway 不直接把原始 installation ID 当公开分析身份；服务端使用 secret 派生 pseudonymous subject。

## 5. Play Integrity

为了保护免费额度、Billing 和服务端资源，Nova 使用 Google Play Integrity。

流程包含：

- 客户端向 Google Play 取得 integrity proof/token；
- Nova Gateway 通过 Google Play 服务端能力验证；
- 校验 package / license / device-integrity 等当前策略要求。

正式 Data Safety / Privacy Policy 应把 Google Play Integrity 作为反滥用/安全基础设施处理，并以发布时 Google Play SDK 的实际声明为准。

## 6. Google Play Billing

客户端会观察当前 Play account 的 Nova 商品购买状态。

发送给 Nova Billing Gateway 的白名单数据：

- productId；
- productType；
- purchaseToken。

当前固定商品：

- `nova_pro_lifetime`
- `nova_ai_plus`

Gateway 使用 Google Play Developer API 验证 purchase token，并根据服务端结果签发 Nova entitlement session。

安全边界：

- 客户端 Purchase 不是最终权益真相；
- purchaseToken 不回显给客户端响应；
- 产品分析不保存 raw purchaseToken；
- `pro_purchase_verified` 来自服务端验证事实，不来自客户端自报。

## 7. Nova Product Analytics

### 客户端请求固定字段

产品事件只允许：

- eventId；
- event；
- eventVersion；
- occurredAtEpochMs；
- appVersion；
- sdk；
- 事件专用 allowlisted properties。

没有任意 `Map<String,Object>` 供业务代码随意塞数据。

### 服务端补充

服务端从已签名 Nova session 推导：

- pseudonymous subjectId；
- FREE / PRO_LIFETIME / AI_PLUS entitlement。

客户端不能自行声明自己的付费身份。

### Redis 保存

当前设计只保存：

- 每日 event + entitlement count；
- HyperLogLog 每日匿名 unique 数；
- eventId 短期去重键；
- pseudonymous subject/day rate-limit key。

默认聚合保留期：120 天，可由生产环境配置。

不保存 raw product-event payload。

## 8. 产品分析明确禁止字段

产品漏斗不得新增以下内容，除非重新做隐私/产品审核：

- calculator expression/result；
- AutoTap x/y 坐标；
- screenshot/image；
- target app/window title/text；
- clipboard content；
- contact/phone/email/address book；
- advertising ID；
- hardware device identifiers；
- raw purchase token；
- provider/model/API key/upstream URL。

## 9. 管理员 Growth Funnel

`/api/product-funnel` 是服务端管理员聚合读取接口。

只返回按日期、event、entitlement 的：

- count；
- unique users。

它不返回：

- pseudonymous subjectId；
- eventId；
- raw payload；
- purchaseToken；
- AI request content。

访问由服务端管理员 token 保护。

## 10. 需要在 Privacy Policy 明确说明的内容

正式发布前 Privacy Policy 至少应覆盖：

1. 普通计算器主要本地处理。
2. 用户主动使用 AI 功能时，提交内容会发送到 Nova 服务端，并可能由受控 AI provider 处理。
3. AutoTap 使用 AccessibilityService 的具体用途。
4. AutoTap 不用于读取/上传其他 App 的文本、截图或点击坐标到产品分析。
5. Google Play Integrity 用于反滥用和安全验证。
6. Google Play Billing purchase token 会发送到 Nova 服务端并由 Google Play API 验证。
7. Nova 使用随机 app-local installation ID / pseudonymous session，而不是广告 ID。
8. Nova 产品分析保存聚合事件统计，用于可靠性、激活、留存和付费转化。
9. 数据保留/删除与联系渠道必须按最终生产实现补齐。

## 11. Play Console 填写前必须再核对

在真正提交 Data Safety 前重新检查：

- shipping APK/AAB 最终依赖；
- Google Play Billing / Integrity SDK 当时的官方 Data Safety 指引；
- 是否启用了新的 AI 功能；
- 是否新增 crash/analytics SDK；
- Vercel/Redis/AI provider 的最终生产日志和保留设置；
- Privacy Policy 公网 URL；
- 支持/隐私联系邮箱。

如果代码或基础设施改变，本文件必须先更新，再改 Play 声明。
