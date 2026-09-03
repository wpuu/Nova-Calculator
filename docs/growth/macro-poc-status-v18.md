# Nova Macro POC 状态 V18

当前 HEAD：`13463a6c6098fb8027b9826d654d610a26074567`

## 已完成

- 从纯 JSON 规则模拟推进到真实 Chromium 144 DOM；
- 通用 matcher：9/18；
- Shopify Site Adapter：18/18（已知 synthetic 变体）；
- 无危险误点；歧义/权限/disabled 进入 `AI_REVIEW` / `ABSTAIN`；
- 新增最小 MV3 POC：`prototypes/nova-macro-mv3/`；
- 最小权限：`activeTab + scripting + storage`；
- 具备 record / save / replay 骨架；
- password / OTP 不录；
-危险动作不自动执行；
- 正常运行 0 Agnes；
- Agnes 仍未真实接入。

## 当前不能声称

- 不能声称真实 Shopify 18/18；
- 不能声称 unpacked extension 已在真实 Chrome 通过；
- 不能声称 Agnes 已验证；
- 不能声称 Marketplace 可行。

## 下一步唯一优先顺序

1. 真 Chrome 加载 POC；
2. 真实页面 record -> stop -> replay；
3. 五个真实 Shopify Semantic Actions；
4. hold-out 变体；
5. Agnes candidate-only 测试；
6. 通过后再讨论 Marketplace / Creator / 收费。
