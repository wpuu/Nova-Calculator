# Nova Macro release configuration

Source under this directory stays fail-closed: no real Gateway origin or Google OAuth client ID is committed into the prototype.

When the release identity is frozen, build the configured extension from one public configuration set instead of editing `manifest.json` and `ai-review-client.js` by hand.

Required public values:

- `NOVA_MACRO_EXTENSION_ID` — stable 32-character Chrome extension ID.
- `NOVA_MACRO_GATEWAY_ORIGIN` — one exact routable HTTPS origin, with no wildcard, path, query or fragment.
- `NOVA_MACRO_GOOGLE_OAUTH_CLIENT_ID` — Google OAuth client created for that Chrome extension ID.

Build command:

```text
node tools/build-macro-release.mjs
```

The generator writes `dist/nova-macro-mv3` and atomically derives:

- manifest `oauth2.client_id`;
- OAuth scopes exactly `openid`;
- exactly one Gateway `host_permissions` origin;
- the matching `PUBLIC_GATEWAY_ORIGIN` used by browser-session and Macro review requests;
- `release-metadata.json` recording the public release identity.

The generator fails closed for wildcard/HTTP/path-bearing Gateway values, malformed extension IDs and malformed Google OAuth client IDs. It never accepts or writes provider keys, Nova signing secrets, Redis credentials or Google access tokens.

`node tools/macro-release-config-eval.mjs` is part of the owner-only `/run-macro-e2e` gate and must pass before packaging a release.
