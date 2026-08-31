import test from 'node:test';
import assert from 'node:assert/strict';

import {
  createPrivacyPolicyHandler,
  privacyPolicyHandlerFromEnv,
} from '../src/privacy-policy-page.mjs';

test('privacy policy route returns public HTML without exposing server secrets', async () => {
  const handler = createPrivacyPolicyHandler({
    publisherName: 'Nova Calculator Studio',
    contactEmail: 'privacy@example.com',
    effectiveDate: '2026-08-31',
  });

  const response = await handler(new Request('https://nova.example/api/privacy'));
  assert.equal(response.status, 200);
  assert.match(response.headers.get('content-type'), /^text\/html/);
  assert.match(response.headers.get('content-security-policy'), /default-src 'none'/);
  const html = await response.text();
  assert.match(html, /Nova Calculator AI Privacy Policy/);
  assert.match(html, /Nova Calculator Studio/);
  assert.match(html, /privacy@example\.com/);
  assert.match(html, /canRetrieveWindowContent=false/);
  assert.match(html, /does not upload these captures/);
  assert.doesNotMatch(html, /NOVA_PROVIDER_KEYS|NOVA_QUOTA_REDIS|SERVICE_ACCOUNT_PRIVATE_KEY/);
});

test('privacy policy rejects non-GET requests and invalid public configuration', async () => {
  const handler = createPrivacyPolicyHandler({
    publisherName: 'Nova Calculator Studio',
    contactEmail: 'privacy@example.com',
    effectiveDate: '2026-08-31',
  });
  const response = await handler(new Request('https://nova.example/api/privacy', { method: 'POST' }));
  assert.equal(response.status, 405);
  assert.equal(response.headers.get('allow'), 'GET');

  assert.throws(
    () => createPrivacyPolicyHandler({
      publisherName: '<script>alert(1)</script>',
      contactEmail: 'privacy@example.com',
    }),
    /publisher name is invalid/,
  );
  assert.throws(
    () => createPrivacyPolicyHandler({
      publisherName: 'Nova Calculator Studio',
      contactEmail: 'not-an-email',
    }),
    /contact email is invalid/,
  );
});

test('privacy policy env factory requires deploy-time publisher identity and contact', async () => {
  const handler = privacyPolicyHandlerFromEnv({
    NOVA_PRIVACY_PUBLISHER_NAME: 'Nova Calculator Studio',
    NOVA_PRIVACY_CONTACT_EMAIL: 'privacy@example.com',
    NOVA_PRIVACY_EFFECTIVE_DATE: '2026-08-31',
  });
  assert.equal((await handler(new Request('https://nova.example/api/privacy'))).status, 200);

  assert.throws(
    () => privacyPolicyHandlerFromEnv({ NOVA_PRIVACY_CONTACT_EMAIL: 'privacy@example.com' }),
    /publisher name is invalid/,
  );
});
