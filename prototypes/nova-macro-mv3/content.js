(function (root) {
  'use strict';

  if (root.NovaMacroPoc) return;

  const matcher = root.NovaSemanticMatcher;
  if (!matcher) throw new Error('NovaSemanticMatcher must be injected first.');

  const state = {
    recording: false,
    listeners: [],
  };

  const blockedInput = (el) => {
    const type = (el?.type || '').toLowerCase();
    const autocomplete = (el?.autocomplete || '').toLowerCase();
    return type === 'password' || autocomplete === 'one-time-code';
  };

  const register = (target, type, handler, options) => {
    target.addEventListener(type, handler, options);
    state.listeners.push(() => target.removeEventListener(type, handler, options));
  };

  const semanticFingerprint = (el) => {
    const fp = matcher.fingerprint(el, null);
    fp.semanticActionId = matcher.recognizeSemanticAction(location.hostname, fp);
    return fp;
  };

  const emitStep = (step) => {
    // Dispatch immediately from the capture phase so a following navigation does
    // not erase the in-page recorder state before the background owns the step.
    chrome.runtime.sendMessage({ type: 'NOVA_RECORD_STEP', step }).catch(() => {});
  };

  const recordClick = (event) => {
    if (!state.recording || event.button !== 0) return;
    const el = event.target?.closest?.(
      'button,a[href],input,[role="button"],[role="menuitem"],[role="link"],[aria-haspopup="menu"]',
    );
    if (!el) return;
    const fp = semanticFingerprint(el);
    emitStep({
      type: 'click',
      fingerprint: fp,
      requiresConfirmation: !!fp.dangerous,
      recordedAt: Date.now(),
    });
  };

  const recordChange = (event) => {
    if (!state.recording) return;
    const el = event.target;
    if (!el || !['INPUT', 'TEXTAREA', 'SELECT'].includes(el.tagName)) return;
    if (blockedInput(el)) {
      emitStep({ type: 'blocked_sensitive_input', recordedAt: Date.now() });
      return;
    }
    emitStep({
      type: 'input',
      fingerprint: semanticFingerprint(el),
      value: el.value,
      recordedAt: Date.now(),
    });
  };

  function setRecording(recording) {
    if (recording === state.recording) return { ok: true, recording };
    state.recording = recording;
    if (recording) {
      register(document, 'click', recordClick, true);
      register(document, 'change', recordChange, true);
    } else {
      state.listeners.splice(0).forEach((remove) => remove());
    }
    return { ok: true, recording };
  }

  async function replayStep(step, index) {
    if (step.type === 'blocked_sensitive_input') {
      return { ok: false, status: 'BLOCKED_SENSITIVE_INPUT', index };
    }
    if (step.requiresConfirmation || step.fingerprint?.dangerous) {
      return { ok: false, status: 'REQUIRES_CONFIRMATION', index };
    }

    const resolved = await matcher.resolveWithSafeMenu(document, step.fingerprint, { useAdapter: true });
    if (resolved.decision !== 'AUTO' || !resolved.target) {
      return {
        ok: false,
        status: resolved.decision === 'AI_REVIEW' ? 'AI_REVIEW' : 'ABSTAIN',
        index,
        topScores: (resolved.ranked || []).slice(0, 3).map((candidate) => candidate.score),
      };
    }

    const target = resolved.target;
    target.scrollIntoView?.({ block: 'center', inline: 'center' });

    if (step.type === 'click') {
      const mayNavigate =
        target.tagName === 'A' && !!target.getAttribute('href') ||
        !!target.getAttribute?.('formaction');

      // Reply to the service worker before a real navigation can destroy this
      // document/context. The action runs on the next task.
      setTimeout(() => target.click(), 0);
      return {
        ok: true,
        status: 'CLICKED',
        index,
        menuExpanded: !!resolved.menuExpanded,
        mayNavigate: !!mayNavigate,
      };
    }

    if (step.type === 'input') {
      if (blockedInput(target)) return { ok: false, status: 'BLOCKED_SENSITIVE_INPUT', index };
      target.focus?.();
      target.value = step.value;
      target.dispatchEvent(new Event('input', { bubbles: true }));
      target.dispatchEvent(new Event('change', { bubbles: true }));
      return { ok: true, status: 'INPUT_SET', index, mayNavigate: false };
    }

    return { ok: false, status: 'UNKNOWN_STEP', index };
  }

  chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    (async () => {
      if (message?.type === 'NOVA_SET_RECORDING') {
        return setRecording(!!message.recording);
      }
      if (message?.type === 'NOVA_EXECUTE_STEP') {
        return replayStep(message.step, message.index);
      }
      if (message?.type === 'NOVA_CONTENT_STATE') {
        return { ok: true, recording: state.recording };
      }
      return { ok: false, error: 'UNKNOWN_CONTENT_MESSAGE' };
    })().then(sendResponse).catch((error) => {
      sendResponse({ ok: false, error: error?.message || String(error) });
    });
    return true;
  });

  root.NovaMacroPoc = {
    setRecording,
    replayStep,
    getState: () => ({ recording: state.recording }),
  };
})(globalThis);
