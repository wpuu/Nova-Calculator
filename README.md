# Nova Calculator AI

Nova Calculator AI is an Android scientific calculator that keeps deterministic calculation at the center and adds contextual AI only when the user asks for help understanding or expressing a calculation.

The commercial product line is calculator-first: normal calculations remain usable without AI, and supported exact arithmetic is computed by the calculator engine rather than delegated to a language model.

## Core calculator and AI features

- Scientific calculator, history, variables, custom functions and graphing.
- **Explain current result** — ask why a calculation produced its result and receive a step-oriented explanation.
- **Natural-language calculation** — describe calculations such as discounts, tax, tips, splitting bills or other everyday math in words; Nova converts the intent into a calculator expression and validates the result with the deterministic engine where supported.
- **Contextual follow-up** — continue asking about the current expression/result without opening a generic chat experience.
- **Error explanation** — explain invalid or incomplete calculator expressions without silently changing them.
- **Formula assistant** — describe a reusable formula, review the generated candidate and save it for later use.

AI provider credentials, model routing and purchase verification remain server-side behind the Nova Gateway. Provider API keys are not embedded in the Android application.

## Explicit tools

### AutoTap

AutoTap is an optional, user-controlled two-point click helper available on Android 7.0 / API 24 and newer.

- The user explicitly enables it from Nova Tools.
- Nova presents a dedicated AccessibilityService disclosure and requires affirmative consent before opening Android Accessibility settings.
- Volume Up starts clicking and Volume Down stops it.
- The two target indicators are positioned by the user and the automation is deterministic.
- The AccessibilityService cannot retrieve window content (`canRetrieveWindowContent=false`). It does not read screen text, account data or typed content.
- AI does not choose click targets or autonomously operate AccessibilityService.

### Underwater Camera

Underwater Camera is a visible camera tool intended for waterproof cases where touch input is difficult.

- Camera access begins only after the user explicitly enters the tool and accepts the camera disclosure/permission flow.
- Volume Up takes photos; Volume Down starts/stops video.
- Microphone access is requested only when the user chooses video with sound; silent video remains available.
- Photos and videos are written to the device MediaStore (`Pictures/UnderwaterCamera` and `Movies/UnderwaterCamera`). Nova does not upload these captures as part of the camera feature.

## Commercial privacy and safety boundaries

The commercial branch intentionally does **not** include calculator-code-triggered hidden camera, microphone or video capture, covert background recording, or an evidence-export workflow. Sensitive device capabilities are exposed as explicit tools with user-visible permission flows.

Nova AI does not autonomously control AccessibilityService. AI math requests are routed through the Nova Gateway; upstream model credentials and infrastructure remain server-side.

See:

- [`docs/PLAY_DATA_SAFETY_BASELINE.md`](docs/PLAY_DATA_SAFETY_BASELINE.md) — current Play Console data-flow/declaration baseline.
- [`docs/PRIVACY_POLICY_DRAFT.md`](docs/PRIVACY_POLICY_DRAFT.md) — privacy-policy draft and remaining publication decisions.
- [`docs/NOVA_ANDROID_RELEASE.md`](docs/NOVA_ANDROID_RELEASE.md) — guarded production AAB workflow and release configuration.

## Build

Development builds use the isolated application id:

`com.wpuu.novacalculator.dev`

The candidate production application id is:

`com.wpuu.novacalculator`

Normal CI builds the commercial debug APK and performs an unsigned release-AAB preflight. Production signing is a separate, manually triggered GitHub Actions workflow protected by the `production` environment and never requires committing an upload keystore or password to the repository.

## License and attribution

Nova Calculator AI includes substantial code derived from **Calculator++ / android-calculatorpp**, originally developed by Sergey Solovyev (`serso` / `se.solovyev`). Original source-file copyright and Apache License 2.0 notices are retained where present.

The repository is distributed under the Apache License, Version 2.0. See [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE).
