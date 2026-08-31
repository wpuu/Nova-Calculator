# Nova Android production release

Nova keeps ordinary CI and production signing intentionally separate.

## GitHub production environment

Create a GitHub Actions environment named `production`. If the repository plan supports it, add a required reviewer so production signing secrets are released only after approval.

Configure these **environment variables** (public configuration):

- `NOVA_AI_GATEWAY_URL` — `https://<nova-gateway-host>/api/ai`
- `NOVA_ANONYMOUS_SESSION_URL` — `https://<nova-gateway-host>/api/session`
- `NOVA_BILLING_URL` — `https://<nova-gateway-host>/api/billing`
- `NOVA_PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER` — positive Google Cloud project number linked to the Play app
- `NOVA_PRIVACY_CONTACT_VERIFIED` — set to the exact value `true` only after the production publisher has actually verified control of the public privacy-contact domain and mailbox used by `/api/privacy`
- `NOVA_UPLOAD_KEYSTORE_TYPE` — `JKS` (default) or `PKCS12`

`NOVA_PRIVACY_CONTACT_VERIFIED=true` is an operator release sign-off, not cryptographic proof. Do not set it merely to make CI pass. Keep it unset/false until the production privacy domain/mailbox has been verified outside the repository. The production workflow also downloads the live `/api/privacy` page and rejects unresolved release markers such as `Release Draft`, `before public release`, `verify the domain`, `TBD`, `placeholder` or `TODO`.

The three Nova URLs must use the same HTTPS origin. The production release guard rejects localhost/test hosts, credentials, query strings, fragments and unexpected API paths.

Configure these **environment secrets**:

- `NOVA_UPLOAD_KEYSTORE_B64` — base64 of the Google Play upload keystore file
- `NOVA_UPLOAD_STORE_PASSWORD`
- `NOVA_UPLOAD_KEY_ALIAS`
- `NOVA_UPLOAD_KEY_PASSWORD`

Do not store the upload keystore or passwords in the repository, Android resources, Gradle properties committed to Git, issue comments or workflow YAML.

## Building a signed bundle

Run the `Android Production AAB` workflow manually from `commercial/nova-ai-v1` (before merge) or `main` (after release integration). Supply a new `version_code` and the desired `version_name`.

The workflow:

1. verifies commercial-source, Android identity and Accessibility policy guards;
2. validates production Gateway/Billing/Play Integrity configuration;
3. requires explicit production privacy-contact verification and validates the live privacy page has no unresolved release markers;
4. runs Gateway, AI, entitlement and billing tests;
5. builds the release AAB;
6. decodes the upload keystore only into the ephemeral runner temp directory;
7. signs and verifies the AAB with `jarsigner`;
8. uploads only the signed AAB and SHA-256 checksum as a workflow artifact.

It **does not upload to Google Play automatically**. This keeps the first commercial releases under explicit human control while package identity, store listing, pricing and policy declarations are finalized.

## Important identity rule

Production application id is currently frozen in source as:

`com.wpuu.novacalculator`

Debug builds resolve to:

`com.wpuu.novacalculator.dev`

Do not create the Play app under a different package name without first making an explicit project-wide identity migration. Package name and signing history become long-lived release identities once the app is distributed.

## Play Integrity end-to-end validation

The ordinary GitHub Debug APK is intentionally built as `com.wpuu.novacalculator.dev`, so it is useful for application regression testing but **is not the final Play Integrity identity test** for the commercial app.

Before public release, validate the full Standard Play Integrity flow with a Google Play-distributed build that uses the production package `com.wpuu.novacalculator` and the production signing/Play configuration. Prefer a Google Play internal testing track for the final end-to-end check. Internal App Sharing may also be used only when it is deliberately configured for the same Play project and the resulting integrity behavior is verified rather than assumed.

The end-to-end acceptance path is:

`production Play build -> Standard Play Integrity token -> Nova /api/session -> server-side Google decodeIntegrityToken -> signed Nova session -> /api/ai`

Do not treat a successful local/debug APK test as evidence that this production identity chain is configured correctly.
