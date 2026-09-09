(function (root) {
  'use strict';

  if (root.NovaMacroPoc) return;

  const matcher = root.NovaSemanticMatcher;
  if (!matcher) throw new Error('NovaSemanticMatcher must be injected first.');

  const state = {
    recording: false,
    listeners: [],
    pendingReview: null,
    reviewCounter: 0,
  };

  const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

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
      timeoutMs: 5000,
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
      timeoutMs: 5000,
      recordedAt: Date.now(),
    });
  };

  function setRecording(recording) {
    state.pendingReview = null;
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

  async function resolveWithWait(fingerprint, timeoutMs = 5000) {
    const startedAt = Date.now();
    let last = { decision: 'ABSTAIN', ranked: [] };
    let menuExpanded = false;

    while (Date.now() - startedAt <= timeoutMs) {
      last = menuExpanded
        ? matcher.decide(document, fingerprint, { useAdapter: true })
        : await matcher.resolveWithSafeMenu(document, fingerprint, { useAdapter: true });

      if (last.menuExpanded) menuExpanded = true;
      if (last.decision === 'AUTO' && last.target) {
        return { ...last, waitedMs: Date.now() - startedAt, menuExpanded };
      }
      if (last.decision === 'AI_REVIEW') {
        return { ...last, waitedMs: Date.now() - startedAt, menuExpanded };
      }

      await sleep(120);
    }

    return { ...last, waitedMs: Date.now() - startedAt, menuExpanded };
  }

  function setNativeValue(target, value) {
    const tag = target.tagName;
    const prototype = tag === 'TEXTAREA'
      ? HTMLTextAreaElement.prototype
      : tag === 'SELECT'
        ? HTMLSelectElement.prototype
        : HTMLInputElement.prototype;
    const descriptor = Object.getOwnPropertyDescriptor(prototype, 'value');
    if (descriptor?.set) descriptor.set.call(target, value);
    else target.value = value;
  }

  function safeFingerprintSummary(fp = {}) {
    return {
      semanticActionId: fp.semanticActionId || null,
      role: fp.role || null,
      names: Array.isArray(fp.names) ? fp.names.slice(0, 6) : [],
      context: Array.isArray(fp.context) ? fp.context.slice(0, 6) : [],
      attrs: fp.attrs && typeof fp.attrs === 'object' ? { ...fp.attrs } : {},
      hrefPath: fp.hrefPath || '',
      tag: fp.tag || '',
    };
  }

  function candidateSummary(candidate, id) {
    return {
      id,
      role: candidate.role || null,
      names: Array.isArray(candidate.names) ? candidate.names.slice(0, 6) : [],
      context: Array.isArray(candidate.context) ? candidate.context.slice(0, 6) : [],
      attrs: candidate.attrs && typeof candidate.attrs === 'object' ? { ...candidate.attrs } : {},
      hrefPath: candidate.hrefPath || '',
      tag: candidate.tag || '',
      score: Number(candidate.score || 0),
    };
  }

  function createPendingReview(step, index, resolved) {
    const ranked = (resolved.ranked || [])
      .filter((candidate) => candidate?.el && candidate.visible && candidate.enabled && !candidate.dangerous)
      .slice(0, 5);
    if (!ranked.length) return null;

    const reviewId = `review-${Date.now()}-${++state.reviewCounter}`;
    const candidates = new Map();
    const summaries = ranked.map((candidate, candidateIndex) => {
      const id = `candidate_${candidateIndex + 1}`;
      candidates.set(id, candidate.el);
      return candidateSummary(candidate, id);
    });

    state.pendingReview = {
      reviewId,
      index,
      step,
      candidates,
      createdAt: Date.now(),
    };

    return {
      reviewId,
      index,
      stepType: step.type,
      semanticActionId: step.fingerprint?.semanticActionId || null,
      original: safeFingerprintSummary(step.fingerprint),
      candidates: summaries,
      allowedCandidateIds: summaries.map((candidate) => candidate.id),
      policy: 'SELECT_LISTED_CANDIDATE_OR_ABSTAIN',
    };
  }

  async function executeResolvedTarget(step, index, target, meta = {}) {
    if (!target?.isConnected) return { ok: false, status: 'STALE_AI_REVIEW', index };
    const current = matcher.candidateRecord(target);
    if (current.dangerous || step.requiresConfirmation || step.fingerprint?.dangerous) {
      return { ok: false, status: 'REQUIRES_CONFIRMATION', index };
    }

    target.scrollIntoView?.({ block: 'center', inline: 'center' });

    if (step.type === 'click') {
      const mayNavigate =
        (target.tagName === 'A' && !!target.getAttribute('href')) ||
        !!target.getAttribute?.('formaction');
      const urlBefore = location.href;

      setTimeout(() => target.click(), 0);
      return {
        ok: true,
        status: 'CLICKED',
        index,
        waitedMs: meta.waitedMs || 0,
        menuExpanded: !!meta.menuExpanded,
        selectedCandidateId: meta.selectedCandidateId || null,
        mayNavigate: !!mayNavigate,
        urlBefore,
      };
    }

    if (step.type === 'input') {
      if (blockedInput(target)) return { ok: false, status: 'BLOCKED_SENSITIVE_INPUT', index };
      target.focus?.();
      setNativeValue(target, step.value);
      target.dispatchEvent(new Event('input', { bubbles: true, composed: true }));
      target.dispatchEvent(new Event('change', { bubbles: true, composed: true }));
      return {
        ok: true,
        status: 'INPUT_SET',
        index,
        waitedMs: meta.waitedMs || 0,
        selectedCandidateId: meta.selectedCandidateId || null,
        mayNavigate: false,
      };
    }

    return { ok: false, status: 'UNKNOWN_STEP', index };
  }

  async function replayStep(step, index) {
    state.pendingReview = null;
    if (step.type === 'blocked_sensitive_input') {
      return { ok: false, status: 'BLOCKED_SENSITIVE_INPUT', index };
    }
    if (step.requiresConfirmation || step.fingerprint?.dangerous) {
      return { ok: false, status: 'REQUIRES_CONFIRMATION', index };
    }

    const resolved = await resolveWithWait(step.fingerprint, step.timeoutMs || 5000);
    if (resolved.decision !== 'AUTO' || !resolved.target) {
      if (resolved.decision === 'AI_REVIEW') {
        const review = createPendingReview(step, index, resolved);
        if (review) {
          return {
            ok: false,
            status: 'AI_REVIEW',
            index,
            waitedMs: resolved.waitedMs,
            review,
            topScores: review.candidates.map((candidate) => candidate.score),
          };
        }
      }
      return {
        ok: false,
        status: 'ABSTAIN',
        index,
        waitedMs: resolved.waitedMs,
        topScores: (resolved.ranked || []).slice(0, 3).map((candidate) => candidate.score),
      };
    }

    return executeResolvedTarget(step, index, resolved.target, {
      waitedMs: resolved.waitedMs,
      menuExpanded: resolved.menuExpanded,
    });
  }

  async function applyReviewChoice(reviewId, candidateId) {
    const pending = state.pendingReview;
    if (!pending || pending.reviewId !== reviewId) {
      return { ok: false, status: 'STALE_AI_REVIEW' };
    }
    if (typeof candidateId !== 'string' || !pending.candidates.has(candidateId)) {
      return { ok: false, status: 'INVALID_AI_CANDIDATE', index: pending.index };
    }

    const target = pending.candidates.get(candidateId);
    state.pendingReview = null;
    return executeResolvedTarget(pending.step, pending.index, target, {
      selectedCandidateId: candidateId,
    });
  }

  chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    (async () => {
      if (message?.type === 'NOVA_SET_RECORDING') {
        return setRecording(!!message.recording);
      }
      if (message?.type === 'NOVA_EXECUTE_STEP') {
        return replayStep(message.step, message.index);
      }
      if (message?.type === 'NOVA_APPLY_REVIEW_CHOICE') {
        return applyReviewChoice(message.reviewId, message.candidateId);
      }
      if (message?.type === 'NOVA_CONTENT_STATE') {
        return {
          ok: true,
          recording: state.recording,
          url: location.href,
          pendingReviewId: state.pendingReview?.reviewId || null,
        };
      }
      return { ok: false, error: 'UNKNOWN_CONTENT_MESSAGE' };
    })().then(sendResponse).catch((error) => {
      sendResponse({ ok: false, error: error?.message || String(error) });
    });
    return true;
  });

  root.NovaMacroPoc = {
    setRecording,
    resolveWithWait,
    replayStep,
    applyReviewChoice,
    getState: () => ({
      recording: state.recording,
      url: location.href,
      pendingReviewId: state.pendingReview?.reviewId || null,
    }),
  };
})(globalThis);
