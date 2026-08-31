const DEFAULT_EFFECTIVE_DATE = '2026-08-31';

/**
 * Public privacy-policy page for the commercial Nova application.
 *
 * Publisher/contact values come from deployment configuration so personal/legal contact details do
 * not have to be committed to the public source repository. This route contains no server secrets.
 */
export function createPrivacyPolicyHandler(options = {}) {
  const publisherName = requiredText(options.publisherName, 'privacy publisher name', 200);
  const contactEmail = requiredEmail(options.contactEmail);
  const effectiveDate = validDate(options.effectiveDate ?? DEFAULT_EFFECTIVE_DATE);

  const html = renderPrivacyPolicy({ publisherName, contactEmail, effectiveDate });

  return async function privacyPolicy(request) {
    if (!(request instanceof Request) || request.method !== 'GET') {
      return new Response('Method Not Allowed', {
        status: 405,
        headers: {
          allow: 'GET',
          'cache-control': 'no-store',
          'x-content-type-options': 'nosniff',
        },
      });
    }
    return new Response(html, {
      status: 200,
      headers: {
        'content-type': 'text/html; charset=utf-8',
        'cache-control': 'public, max-age=300',
        'x-content-type-options': 'nosniff',
        'referrer-policy': 'no-referrer',
        'content-security-policy': "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'",
      },
    });
  };
}

export function privacyPolicyHandlerFromEnv(env = process.env) {
  return createPrivacyPolicyHandler({
    publisherName: env.NOVA_PRIVACY_PUBLISHER_NAME,
    contactEmail: env.NOVA_PRIVACY_CONTACT_EMAIL,
    effectiveDate: env.NOVA_PRIVACY_EFFECTIVE_DATE || DEFAULT_EFFECTIVE_DATE,
  });
}

function renderPrivacyPolicy({ publisherName, contactEmail, effectiveDate }) {
  const publisher = escapeHtml(publisherName);
  const email = escapeHtml(contactEmail);
  const date = escapeHtml(effectiveDate);
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Nova Calculator AI Privacy Policy</title>
<style>
body{font-family:system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;line-height:1.6;max-width:820px;margin:0 auto;padding:28px 20px;color:#1d1d1f;background:#fff}h1{font-size:2rem}h2{margin-top:2rem;font-size:1.2rem}code{background:#f5f5f7;padding:.1rem .3rem;border-radius:4px}a{color:#06c}small{color:#666}
</style>
</head>
<body>
<h1>Nova Calculator AI Privacy Policy</h1>
<p><small>Effective date: ${date}</small></p>
<p>This policy describes how ${publisher} handles information in the commercial Nova Calculator AI Android application.</p>

<h2>1. Local calculator data</h2>
<p>Ordinary calculator expressions, results, history, variables, functions and saved formulas are processed on your device unless you explicitly use a feature that requires network processing, such as Nova AI or purchase verification.</p>

<h2>2. Nova AI</h2>
<p>When you explicitly use an AI feature, Nova sends the math context needed for that request to the Nova Gateway. Depending on the feature, this can include the current expression and calculator result, a natural-language calculation request, a follow-up question, an error question, or a formula description.</p>
<p>The Nova Gateway may use a third-party AI inference processor to produce the requested explanation or math assistance. Nova keeps upstream AI API credentials server-side and does not embed them in the Android application.</p>
<p>AI request data may be processed and retained by Nova infrastructure or service providers for the time reasonably necessary to provide, secure and operate the service, subject to the production service configuration and applicable legal obligations. Nova does not use AI request content to sell personal or sensitive user data.</p>

<h2>3. Installation security and abuse prevention</h2>
<p>Nova may process an app-local installation identifier, Google Play Integrity tokens/verdicts, signed Nova session information, pseudonymous quota identifiers and rate-limit state. These are used for application security, fraud/abuse prevention, entitlement enforcement and service capacity management.</p>

<h2>4. Google Play purchases</h2>
<p>Nova uses Google Play Billing for paid digital features. The app may send the product id/type and Google Play purchase token to Nova's server, which verifies purchase state through Google Play Developer APIs before issuing Nova entitlements.</p>
<p>Nova does not receive your payment-card number, bank-account information or Google Play payment credentials. Google handles payment processing under Google's own terms and privacy practices.</p>

<h2>5. Underwater Camera</h2>
<p>Underwater Camera is an explicit tool. Camera permission is requested only after you enter the tool. Microphone permission is optional and requested only when you choose video with sound.</p>
<p>In the current commercial implementation, captured photos, videos and optional audio are saved locally through Android MediaStore. The Underwater Camera feature does not upload these captures to Nova servers.</p>

<h2>6. AutoTap and AccessibilityService</h2>
<p>AutoTap is an optional deterministic two-point click helper on supported Android versions. Before opening Android Accessibility settings, Nova displays a separate in-app disclosure and requires affirmative consent.</p>
<p>The AutoTap AccessibilityService receives limited window-change events, listens to the hardware volume keys while armed, and sends gestures to the two positions chosen by the user. It is configured with <code>canRetrieveWindowContent=false</code>, does not retrieve screen text through AccessibilityService, and does not allow AI to autonomously choose targets or execute Accessibility actions.</p>

<h2>7. Service providers and sharing</h2>
<p>Nova may transmit information to service providers when necessary for a feature you request or for security/payment operation. These can include Google Play / Play Integrity, infrastructure operating the Nova Gateway and quota systems, and the AI inference processor used when you invoke Nova AI.</p>
<p>Nova does not sell personal or sensitive user data.</p>

<h2>8. Security and retention</h2>
<p>Nova keeps upstream AI credentials, Redis credentials, Google service-account private keys and Nova signing secrets on the server rather than in the Android app. Nova sessions are signed and time-limited, and quota/security records use pseudonymous identifiers.</p>
<p>Information is retained only for as long as reasonably necessary for the relevant functionality, security, fraud prevention, operational requirements, dispute handling and legal obligations. Local calculator data and local media remain subject to your Android/device storage controls.</p>

<h2>9. Changes</h2>
<p>If Nova materially changes its collection, processing or sharing practices, this policy and relevant in-app disclosures will be updated with the applicable release.</p>

<h2>10. Contact</h2>
<p>Publisher: ${publisher}<br>Privacy contact: <a href="mailto:${email}">${email}</a></p>
</body>
</html>`;
}

function requiredText(value, name, maxLength) {
  const text = String(value ?? '').trim();
  if (!text || text.length > maxLength || /[\r\n<>]/.test(text)) {
    throw new Error(`${name} is invalid`);
  }
  return text;
}

function requiredEmail(value) {
  const email = String(value ?? '').trim().toLowerCase();
  if (email.length > 320 || !/^[^\s@<>]+@[^\s@<>]+\.[^\s@<>]+$/.test(email)) {
    throw new Error('privacy contact email is invalid');
  }
  return email;
}

function validDate(value) {
  const text = String(value ?? '').trim();
  if (!/^\d{4}-\d{2}-\d{2}$/.test(text) || Number.isNaN(Date.parse(`${text}T00:00:00Z`))) {
    throw new Error('privacy effective date must use YYYY-MM-DD');
  }
  return text;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}
