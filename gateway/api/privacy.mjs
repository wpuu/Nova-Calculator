import { privacyPolicyHandlerFromEnv } from '../src/privacy-policy-page.mjs';

let handler = null;

export default {
  async fetch(request) {
    try {
      if (!handler) handler = privacyPolicyHandlerFromEnv();
      return await handler(request);
    } catch {
      return new Response('Privacy policy temporarily unavailable', {
        status: 503,
        headers: {
          'content-type': 'text/plain; charset=utf-8',
          'cache-control': 'no-store',
          'x-content-type-options': 'nosniff',
        },
      });
    }
  },
};
