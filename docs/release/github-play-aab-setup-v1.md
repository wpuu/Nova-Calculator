# GitHub Play AAB Setup V1

用于工作流：

```text
Build Play Internal AAB
```

文件：

```text
.github/workflows/android-play-internal-aab.yml
```

## GitHub Variables（公开配置，不是密码）

在仓库 Actions Variables 配置：

```text
NOVA_ANDROID_PACKAGE_NAME
NOVA_AI_GATEWAY_URL
NOVA_ANONYMOUS_SESSION_URL
NOVA_BILLING_URL
NOVA_PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER
```

说明：

- `NOVA_ANDROID_PACKAGE_NAME`：最终 Play applicationId；冻结后不要改。
- 三个 URL：Nova 自有 HTTPS endpoint，不是上游模型/provider URL。
- `NOVA_PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER`：Google Cloud project number，是公开标识，不是私钥。

## GitHub Secrets（敏感，禁止提交仓库）

```text
NOVA_UPLOAD_KEYSTORE_B64
NOVA_UPLOAD_STORE_PASSWORD
NOVA_UPLOAD_KEY_ALIAS
NOVA_UPLOAD_KEY_PASSWORD
```

### NOVA_UPLOAD_KEYSTORE_B64

这是 upload keystore 文件本身的 Base64 文本。

不要把：

- 原 `.jks` / `.keystore` 文件；
- Base64 文本；
- store password；
- key password

提交到 Git。

工作流运行时只在 GitHub runner 的临时目录还原 keystore，构建结束后 runner 销毁。

## 每次运行时输入

工作流只要求两项：

```text
version_code
version_name
```

### version_code

Google Play 每次上传必须递增。

项目默认编码规则：

```text
major*1,000,000 + minor*10,000 + patch*100 + build
```

### version_name

示例：

```text
0.2.0-alpha01
```

## 工作流会自动做什么

1. checkout + submodules；
2. JDK 17 / Node 22；
3. 适配当前 pinned plotter library；
4. commercial source guard；
5. 检查 Variables / Secrets 非空；
6. 临时还原 upload keystore；
7. gateway tests；
8. entitlement / AI / Billing / AutoTap / analytics Android tests；
9. `verifyNovaPlayReleaseConfig`；
10. `bundleRelease`；
11. 上传签名 AAB + SHA256 checksum 为 GitHub Actions artifact。

## 工作流明确不会做什么

- 不自动创建 Play Console app；
- 不自动创建商品；
- 不自动上传 Google Play；
- 不自动发布 Production；
- 不把 keystore 放进 artifact；
- 不把签名密码写入文件；
- 不需要 provider API Key 出现在 Android build 环境。

在 Internal Testing 验收稳定之前，保持“构建”和“上传 Play”分离更安全。

## 第一次正式运行前检查

- [ ] 最终 applicationId 已在 Play Console 确认。
- [ ] Play App Signing 已启用。
- [ ] upload key 已安全备份。
- [ ] `nova_pro_lifetime` 已创建。
- [ ] `nova_ai_plus` + monthly / annual 已创建。
- [ ] Play Integrity Cloud project 已绑定。
- [ ] Gateway 生产环境与同一 package name 一致。
- [ ] Android Publisher/Billing 服务账号权限已配置。
- [ ] Privacy Policy / Data Safety / Accessibility declaration 已准备。

## 安全规则

如果 upload key 丢失或正式包名配置错误，不要通过修改 APK 绕过保护。先停止发布并修复 Play Console / signing 配置。
