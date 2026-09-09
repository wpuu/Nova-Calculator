# Nova Macro release configuration

Source under this directory stays fail-closed: no real Gateway origin or Google OAuth client ID is committed into the prototype.

## Stable development identity

Real Google OAuth / Gateway / Agnes testing does **not** need to wait for the Chrome Web Store production item.

Nova has a development-only public-key identity stored at:

- `tools/fixtures/macro-dev-extension-identity-v1.json`
- stable development extension ID: `akeikbdfaioghooickhchdfkedcfcmgl`

Only the DER public key is stored. The temporary private key used to generate it was discarded and is not needed to load the unpacked extension. Chrome's manifest `key` makes the unpacked development extension keep the same ID across reloads/machines.

Once a Preview Gateway origin and a Google Chrome-extension OAuth client for the development ID exist, provide only:

- `NOVA_MACRO_GATEWAY_ORIGIN`
- `NOVA_MACRO_GOOGLE_OAUTH_CLIENT_ID`

Then run:

```text
node tools/build-macro-dev.mjs
```

The configured development package is written to `dist/nova-macro-dev`. Its metadata is explicitly marked `developmentOnly: true` so this identity cannot be mistaken for the future Web Store identity.

## Production / Web Store identity

When the production item is frozen, build the configured extension from one public configuration set instead of editing `manifest.json` and `ai-review-client.js` by hand.

Required public values:

- `NOVA_MACRO_EXTENSION_ID` — stable 32-character Chrome extension ID from the Web Store item.
- `NOVA_MACRO_EXTENSION_PUBLIC_KEY` — the corresponding public key. The builder derives the Chrome extension ID from this key and rejects any mismatch.
- `NOVA_MACRO_GATEWAY_ORIGIN` — one exact routable HTTPS origin, with no wildcard, path, query or fragment.
- `NOVA_MACRO_GOOGLE_OAUTH_CLIENT_ID` — Google OAuth client created for that Chrome extension ID.

Build command:

```text
node tools/build-macro-release.mjs
```

The generator writes `dist/nova-macro-mv3` and atomically derives:

- manifest `key` bound to the configured Extension ID;
- manifest `oauth2.client_id`;
- OAuth scopes exactly `openid`;
- exactly one Gateway `host_permissions` origin;
- the matching `PUBLIC_GATEWAY_ORIGIN` used by browser-session and Macro review requests;
- `release-metadata.json` recording the public release identity plus a public-key fingerprint.

The generator fails closed for public-key/Extension-ID mismatch, wildcard/HTTP/path-bearing Gateway values, malformed extension IDs and malformed Google OAuth client IDs. It never accepts or writes provider keys, Nova signing secrets, Redis credentials or Google access tokens.

`node tools/macro-release-config-eval.mjs` is part of the owner-only `/run-macro-e2e` gate and verifies both the stable development build and production-style release configuration before packaging.
