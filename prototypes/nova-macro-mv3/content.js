(function (root) {
  'use strict';

  if (root.NovaMacroPoc) return;

  const matcher = root.NovaSemanticMatcher;
  if (!matcher) throw new Error('NovaSemanticMatcher must be injected first.');

  const state = {
    recording: false,
    steps: [],
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

  const recordClick = (event) => {
    if (!state.recording || event.button !== 0) return;
    const el = event.target?.closest?.('button,a[href],input,[role="button"],[role="menuitem"],[role="link"],[aria-haspopup="menu"]');
    if (!el) return;
    const fp = semanticFingerprint(el);
    state.steps.push({
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
      state.steps.push({ type: 'blocked_sensitive_input', recordedAt: Date.now() });
      return;
    }
    state.steps.push({
      type: 'input',
      fingerprint: semanticFingerprint(el),
      value: el.value,
      recordedAt: Date.now(),
    });
  };

  async function startRecording() {
    if (state.recording) return { ok: true, alreadyRecording: true, steps: state.steps.length };
    state.recording = true;
    state.steps = [];
    register(document, 'click', recordClick, true);
    register(document, 'change', recordChange, true);
    return { ok: true, recording: true };
  }

  async function stopRecording() {
    state.recording = false;
    state.listeners.splice(0).forEach((remove) => remove());
    await chrome.storage.local.set({ novaMacroPocLast: state.steps });
    return { ok: true, recording: false, steps: state.steps };
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
      target.click();
      return { ok: true, status: 'CLICKED', index, menuExpanded: !!resolved.menuExpanded };
    }

    if (step.type === 'input') {
      if (blockedInput(target)) return { ok: false, status: 'BLOCKED_SENSITIVE_INPUT', index };
      target.focus?.();
      target.value = step.value;
      target.dispatchEvent(new Event('input', { bubbles: true }));
      target.dispatchEvent(new Event('change', { bubbles: true }));
      return { ok: true, status: 'INPUT_SET', index };
    }

    return { ok: false, status: 'UNKNOWN_STEP', index };
  }

  async function replayLast() {
    const stored = await chrome.storage.local.get('novaMacroPocLast');
    const steps = stored.novaMacroPocLast || [];
    const results = [];
    for (let index = 0; index < steps.length; index += 1) {
      const result = await replayStep(steps[index], index);
      results.push(result);
      if (!result.ok) return { ok: false, stoppedAt: index, results };
      await new Promise((resolve) => setTimeout(resolve, 50));
    }
    return { ok: true, results };
  }

  async function getState() {
    const stored = await chrome.storage.local.get('novaMacroPocLast');
    return {
      recording: state.recording,
      inMemorySteps: state.steps.length,
      savedSteps: (stored.novaMacroPocLast || []).length,
    };
  }

  root.NovaMacroPoc = { startRecording, stopRecording, replayLast, getState };
})(globalThis);
