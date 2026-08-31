# Nova Google Play Internal Testing V1

## 目标

把 Nova 从“商业代码已完成”推进到 Google Play Internal Testing 的真实安装、真实购买、真实恢复和真机 AutoTap 验收。

本文是发布契约，不保存任何正式密码、API Key、keystore 或服务账号私钥。

## 1. 正式 Android 身份

开发构建默认仍使用：

- `com.wpuu.novacalculator.dev`

这个 `.dev` applicationId **禁止上传 Google Play**。

正式 applicationId 只在 Play Console 创建前最后冻结，通过同一个变量注入 Android 和 Gateway：

- `NOVA_ANDROID_PACKAGE_NAME`

Gradle 也支持本地 property：

- `-PnovaApplicationId=...`

不要为了改正式 applicationId 再编辑源码。

## 2. Release 构建强制配置

`bundleRelease` / `assembleRelease` 会先执行 `verifyNovaPlayReleaseConfig`。以下条件任何一个不满足都应失败：

1. `NOVA_ANDROID_PACKAGE_NAME` 是合法包名，且不以 `.dev` 结尾。
2. `NOVA_AI_GATEWAY_URL` 是 HTTPS Nova 自有 endpoint。
3. `NOVA_ANONYMOUS_SESSION_URL` 是 HTTPS Nova 自有 endpoint。
4. `NOVA_BILLING_URL` 是 HTTPS Nova 自有 endpoint。
5. `NOVA_PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER` 大于 0。
6. 上传签名四项全部存在且 keystore 文件真实存在。

上传签名变量：

- `NOVA_UPLOAD_KEYSTORE_PATH`
- `NOVA_UPLOAD_STORE_PASSWORD`
- `NOVA_UPLOAD_KEY_ALIAS`
- `NOVA_UPLOAD_KEY_PASSWORD`

正式密码和 keystore 不得提交 Git。

## 3. 正式 AAB 构建命令

环境变量配置完成后：

```bash
./gradlew :app:verifyNovaPlayReleaseConfig :app:bundleRelease --stacktrace --no-daemon
```

输出：

```text
app/build/outputs/bundle/release/app-release.aab
```

只有使用正式 upload key 构建的 AAB 才能上传 Play Console。

## 4. CI release AAB 的用途

商业 CI 会生成一次性临时 keystore，并使用专用 CI 包名构建 release AAB，用于提前发现：

- release-only 编译问题；
- R8 / ProGuard 问题；
- Android App Bundle 问题；
- release signing DSL / 配置问题；
- `.dev` 身份保护是否有效。

CI 验证包名：

```text
com.wpuu.novaautotap.ci
```

CI AAB **不得上传正式 Play app**，也不代表最终 applicationId 已冻结。

## 5. Google Play Billing 商品

首批商品 ID 已固定，创建后不要改代码中的 ID：

### Pro Lifetime

```text
nova_pro_lifetime
```

类型：one-time product / INAPP。

### AI Plus

```text
nova_ai_plus
```

类型：subscription / SUBS。

Base plans：

```text
monthly
annual
```

客户端只观察 Play purchase；最终权益必须经过 Nova Gateway 调 Google Play Developer API 验证。

## 6. Gateway 生产配置

至少需要：

- `NOVA_ANDROID_PACKAGE_NAME`
- `NOVA_SESSION_SIGNING_SECRETS`
- `NOVA_SESSION_SUBJECT_SECRET`
- `NOVA_QUOTA_REDIS_REST_URL`
- `NOVA_QUOTA_REDIS_REST_TOKEN`
- Play Integrity 服务账号/项目配置
- Google Play Android Publisher 权限对应的 Billing 服务账号配置

Billing 可使用专用变量：

- `NOVA_PLAY_BILLING_SERVICE_ACCOUNT_EMAIL`
- `NOVA_PLAY_BILLING_SERVICE_ACCOUNT_PRIVATE_KEY_B64`
- `NOVA_PLAY_BILLING_SERVICE_ACCOUNT_KEY_ID`（如部署使用）

如果复用 Play Integrity 服务账号，则该账号必须同时拥有 Play Console / Android Publisher 所需权限。

## 7. Growth Analytics 生产配置

Nova 自有产品漏斗使用现有 Redis，不接 Calculator++ 原 Firebase/GA。

可选配置：

- `NOVA_PRODUCT_EVENT_REDIS_KEY_PREFIX`，默认 `nova:product:v1`
- `NOVA_PRODUCT_EVENT_RETENTION_DAYS`，默认 `120`
- `NOVA_PRODUCT_EVENT_DAILY_SUBJECT_LIMIT`，默认 `500`
- `NOVA_PRODUCT_FUNNEL_ADMIN_TOKEN`：管理员只读聚合漏斗接口所需；必须仅存在服务端

接口：

- `/api/product-event`：客户端写隐私安全事件
- `/api/product-funnel`：管理员读取每日聚合 count / unique，不返回 raw subject/eventId/payload

## 8. Play Console 前置项

正式 Internal Testing 前必须完成：

- 冻结最终 applicationId；
- 创建 Play app；
- 开启 Play App Signing，并妥善保存 upload key；
- 创建 `nova_pro_lifetime`；
- 创建 `nova_ai_plus` + `monthly` / `annual`；
- 绑定 Play Integrity 对应 Cloud project；
- 服务账号获得 Android Publisher 所需权限；
- Accessibility declaration；
- App 内醒目无障碍披露和用户同意（代码已实现）；
- Data Safety；
- Privacy Policy；
- 支持邮箱；
- 商店图标、截图、英文 listing。

## 9. Internal Testing 真机验收矩阵

### Billing

- [ ] Free 安装后显示 Free。
- [ ] Pro Lifetime 显示真实本地化价格。
- [ ] Pro 购买成功后服务端验证并获得 Pro entitlement。
- [ ] 杀进程/重启后 Pro 权益恢复。
- [ ] 用户主动 Restore purchases 成功。
- [ ] 用户取消购买不授予权益。
- [ ] 退款/撤销后不继续授予 Pro。
- [ ] AI Plus monthly 真实购买和恢复。
- [ ] AI Plus annual 真实购买和恢复。
- [ ] AI Plus 处于允许权益的 grace 状态时行为符合后端策略。
- [ ] hold / expired / invalid purchase 不授予权益。

### AutoTap

- [ ] 横屏游戏两个圆圈覆盖完整屏幕宽度。
- [ ] 全屏/沉浸切换后不缩在左半屏。
- [ ] 显示尺寸/模式变化后旧点击立即停止，overlay 重新定位。
- [ ] 运行时两个圆圈和状态条不吞目标 App 触摸。
- [ ] Volume+ 只启动。
- [ ] Volume− 立即停止。
- [ ] 点击/触摸两个圆圈不会启动连点。
- [ ] 点击/触摸状态条不会启动连点。
- [ ] 停止后圆圈和状态条可拖动。
- [ ] 重启 App / 重连服务后 normalized position 保持。

建议至少覆盖：Pixel / Samsung / Huawei 或 Honor；若测试设备不足，先覆盖手头真实设备，再由首批测试用户补 OEM 数据。

## 10. Internal Testing 通过门槛

只有同时满足以下条件才进入 Closed Testing：

1. release AAB 使用正式 upload key 构建；
2. Play Integrity 可签发真实匿名 Nova session；
3. Pro 至少完成一次真实购买 -> 服务端验证 -> 重启恢复；
4. Restore purchases 成功；
5. AutoTap 横屏/全屏 P0 验收通过；
6. Growth 漏斗能看到 AutoTap entry -> disclosure -> overlay ready -> paywall -> verified purchase；
7. Accessibility / Data Safety / Privacy Policy 与实际行为一致；
8. 没有 provider API Key、上游模型名、供应商地址或服务账号私钥进入 APK。

## 11. 当前不做

- 不为了埋点重写已经稳定的 AccessibilityService 手势状态机。
- 不先购买广告流量再补 activation 数据。
- 不先做 Agnes AI Setup Assistant，再判断真实 setup 是否是主要流失点。
- 不创建多个高度相似 APK 分散商店权重。

当前优先级：

```text
release AAB 可重复构建
→ Play Console + 商品
→ Internal Testing 真机交易
→ AutoTap 真机 P0
→ 6 个 Custom Store Listing
→ 小流量验证
→ 再决定 Agnes / AI Plus 扩张
```
