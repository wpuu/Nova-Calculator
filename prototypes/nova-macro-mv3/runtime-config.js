(function (root) {
  'use strict';

  // Public runtime routing only. Never put provider keys, Nova signing secrets,
  // Google access tokens, or any other credential in this file.
  root.NovaMacroRuntimeConfig = Object.freeze({
    // Freeze to one exact HTTPS origin once the Preview Gateway is created.
    // Blank deliberately keeps Google/Nova browser bootstrap fail-closed.
    gatewayOrigin: '',
  });
})(globalThis);
