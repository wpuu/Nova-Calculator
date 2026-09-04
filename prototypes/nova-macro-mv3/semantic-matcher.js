(function (root) {
  'use strict';

  const norm = (value) => (value ?? '').toString().toLowerCase().replace(/[\s\u00a0]+/g, ' ').trim();
  const tokenise = (value) => norm(value).split(/[^\p{L}\p{N}]+/u).filter(Boolean);
  const uniq = (arr) => [...new Set(arr.filter(Boolean).map(norm))];

  const ACTION_PACKS = {
    'shopify.open_orders': {
      hostSuffixes: ['shopify.com'],
      targetNames: ['orders', 'view orders', '订单', 'bestellungen', 'commandes'],
      contextNames: ['navigation', 'admin', 'main menu', '主导航', '导航'],
      targetRoles: ['link', 'button'],
      hrefPathSuffixes: ['/orders'],
    },
    'shopify.search_orders': {
      hostSuffixes: ['shopify.com'],
      targetNames: [
        'search orders', 'search by order number or customer', 'search',
        '搜索订单', '搜索', 'bestellungen suchen', 'suchen',
        'rechercher des commandes', 'rechercher',
      ],
      contextNames: ['orders', '订单', 'bestellungen', 'commandes'],
      targetRoles: ['textbox', 'searchbox'],
    },
    'shopify.filter_orders': {
      hostSuffixes: ['shopify.com'],
      targetNames: [
        'filter', 'filters', 'add filter', 'filter orders',
        '筛选', '筛选订单', '过滤', 'filter hinzufügen', 'filtern',
        'filtrer', 'ajouter un filtre',
      ],
      menuNames: [
        'filter', 'filters', 'add filter', '筛选', '过滤', 'filtern', 'filtrer',
      ],
      contextNames: ['orders', '订单', 'bestellungen', 'commandes'],
      targetRoles: ['button', 'menuitem'],
    },
    'shopify.export_orders': {
      hostSuffixes: ['shopify.com'],
      targetNames: [
        'export orders', 'export order data', 'export',
        '导出订单', '导出', 'exportieren', 'bestellungen exportieren',
        'exporter les commandes', 'exporter',
      ],
      menuNames: [
        'more actions', 'actions', '更多操作', '更多', 'mehr aktionen', 'aktionen',
        "plus d'actions", 'actions supplémentaires',
      ],
      contextNames: ['orders', '订单', 'bestellungen', 'commandes'],
      targetRoles: ['button', 'menuitem'],
    },
  };

  function inferredRole(el) {
    const explicit = norm(el.getAttribute?.('role'));
    if (explicit) return explicit;
    const tag = norm(el.tagName);
    if (tag === 'button') return 'button';
    if (tag === 'a' && el.hasAttribute('href')) return 'link';
    if (tag === 'input') {
      const type = norm(el.type);
      if (['submit', 'button'].includes(type)) return 'button';
      if (type === 'search') return 'searchbox';
      return 'textbox';
    }
    if (tag === 'select') return 'combobox';
    if (tag === 'textarea') return 'textbox';
    return '';
  }

  function accessibleNames(el) {
    const names = [];
    names.push(el.getAttribute?.('aria-label'));
    names.push(el.getAttribute?.('placeholder'));
    names.push(el.getAttribute?.('title'));
    names.push(el.getAttribute?.('alt'));
    if ('value' in el && ['button', 'submit'].includes(norm(el.type))) names.push(el.value);
    names.push(el.textContent);
    if (el.labels) for (const label of el.labels) names.push(label.textContent);
    return uniq(names);
  }

  function nearestContext(el) {
    const out = [];
    let current = el;
    for (let depth = 0; current && depth < 7; depth += 1, current = current.parentElement) {
      const label = current.getAttribute?.('aria-label');
      if (label) out.push(label);
      const heading = current.querySelector?.(':scope > h1, :scope > h2, :scope > h3, :scope > [role="heading"]');
      if (heading) out.push(heading.textContent);
      if (['MAIN', 'SECTION', 'FORM', 'NAV'].includes(current.tagName)) {
        out.push(current.getAttribute?.('data-section'));
        if (current.tagName === 'NAV') out.push('navigation');
      }
    }
    return uniq(out);
  }

  function hrefPath(el) {
    const raw = el.getAttribute?.('href');
    if (!raw) return '';
    try {
      const current = root.location?.href || '';
      const base = /^https?:/i.test(current) ? current : 'https://example.invalid/';
      return new URL(raw, base).pathname.toLowerCase();
    } catch {
      return '';
    }
  }

  function stableAttrs(el) {
    const allow = ['data-testid', 'data-test', 'data-action', 'name', 'type', 'aria-controls', 'aria-haspopup'];
    const attrs = {};
    for (const key of allow) {
      const value = el.getAttribute?.(key);
      if (value && value.length < 120) attrs[key] = norm(value);
    }
    const id = el.getAttribute?.('id');
    if (id && !/[0-9a-f]{8,}|\d{5,}/i.test(id)) attrs.id = norm(id);
    return attrs;
  }

  function isVisible(el) {
    const style = root.getComputedStyle ? root.getComputedStyle(el) : null;
    if (style && (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0')) return false;
    if (el.hidden) return false;
    const rect = el.getBoundingClientRect?.();
    return !rect || (rect.width > 0 && rect.height > 0);
  }

  function isEnabled(el) {
    return !el.disabled && el.getAttribute?.('aria-disabled') !== 'true';
  }

  function dangerScore(el) {
    const role = inferredRole(el);
    if (!['button', 'menuitem'].includes(role)) return 0;
    const text = norm(accessibleNames(el).join(' '));
    const destructive = [
      'delete', 'remove account', 'pay now', 'purchase', 'confirm payment',
      '删除', '付款', '支付', 'supprimer', 'löschen',
    ];
    return destructive.some((word) => text.includes(word)) ? 1 : 0;
  }

  function fingerprint(el, semanticActionId = null) {
    return {
      semanticActionId,
      role: inferredRole(el),
      names: accessibleNames(el),
      context: nearestContext(el),
      attrs: stableAttrs(el),
      hrefPath: hrefPath(el),
      tag: norm(el.tagName),
      dangerous: dangerScore(el) > 0,
    };
  }

  function overlap(a = [], b = []) {
    const A = new Set(a.map(norm));
    return b.map(norm).some((value) => A.has(value));
  }

  function fuzzyNameScore(expected, actual) {
    const expectedNorm = uniq(expected);
    const actualNorm = uniq(actual);
    if (overlap(expectedNorm, actualNorm)) return 34;
    let best = 0;
    for (const a of expectedNorm) {
      const aTokens = new Set(tokenise(a));
      for (const b of actualNorm) {
        const bTokens = new Set(tokenise(b));
        const intersection = [...aTokens].filter((token) => bTokens.has(token)).length;
        const union = new Set([...aTokens, ...bTokens]).size || 1;
        best = Math.max(best, intersection / union);
      }
    }
    if (best >= 0.66) return 22;
    if (best >= 0.5) return 12;
    return 0;
  }

  function attrScore(expected = {}, actual = {}) {
    let matches = 0;
    for (const [key, value] of Object.entries(expected)) {
      if (norm(actual[key]) === norm(value)) matches += 1;
    }
    return Math.min(30, matches * 15);
  }

  function candidateRecord(el) {
    return {
      el,
      role: inferredRole(el),
      names: accessibleNames(el),
      context: nearestContext(el),
      attrs: stableAttrs(el),
      hrefPath: hrefPath(el),
      tag: norm(el.tagName),
      visible: isVisible(el),
      enabled: isEnabled(el),
      dangerous: dangerScore(el) > 0,
    };
  }

  function pathScore(pack, candidate) {
    if (!pack?.hrefPathSuffixes?.length || !candidate.hrefPath) return 0;
    return pack.hrefPathSuffixes.some((suffix) => candidate.hrefPath.endsWith(norm(suffix))) ? 24 : 0;
  }

  function score(fp, candidate, pack = null) {
    let value = 0;
    const expectedNames = pack ? uniq([...fp.names, ...(pack.targetNames || [])]) : fp.names;
    const expectedContext = pack ? uniq([...fp.context, ...(pack.contextNames || [])]) : fp.context;
    const targetRoles = pack?.targetRoles || [fp.role];
    if (targetRoles.includes(candidate.role)) value += 16;
    value += fuzzyNameScore(expectedNames, candidate.names);
    value += attrScore(fp.attrs, candidate.attrs);
    value += pathScore(pack, candidate);
    if (overlap(expectedContext, candidate.context)) value += 14;
    if (fp.tag && fp.tag === candidate.tag) value += 6;
    if (!candidate.visible) value -= 50;
    if (!candidate.enabled) value -= 30;
    if (candidate.dangerous) value -= 200;
    return value;
  }

  function interactiveElements(doc) {
    return [...doc.querySelectorAll(
      'button,a[href],input,select,textarea,[role="button"],[role="menuitem"],[role="link"],[role="textbox"],[role="searchbox"],[aria-haspopup="menu"]',
    )];
  }

  function recognizeSemanticAction(hostname, fp) {
    const host = norm(hostname);
    const matches = [];
    for (const [id, pack] of Object.entries(ACTION_PACKS)) {
      if (!(pack.hostSuffixes || []).some((suffix) => host === suffix || host.endsWith(`.${suffix}`))) continue;
      const nameScore = fuzzyNameScore(pack.targetNames || [], fp.names || []);
      const contextHit = overlap(pack.contextNames || [], fp.context || []);
      const roleHit = (pack.targetRoles || []).includes(fp.role);
      const pathHit = pack.hrefPathSuffixes?.some((suffix) => fp.hrefPath?.endsWith(norm(suffix)));
      if (roleHit && ((nameScore >= 22 && (contextHit || id === 'shopify.open_orders')) || pathHit)) {
        matches.push({
          id,
          score: nameScore + (contextHit ? 20 : 0) + (roleHit ? 10 : 0) + (pathHit ? 24 : 0),
        });
      }
    }
    matches.sort((a, b) => b.score - a.score);
    if (!matches.length) return null;
    if (matches[1] && matches[0].score - matches[1].score < 8) return null;
    return matches[0].id;
  }

  function decide(doc, fp, { useAdapter = true } = {}) {
    const pack = useAdapter && fp.semanticActionId ? ACTION_PACKS[fp.semanticActionId] : null;
    const ranked = interactiveElements(doc)
      .map(candidateRecord)
      .map((candidate) => ({ ...candidate, score: score(fp, candidate, pack) }))
      .filter((candidate) => !candidate.dangerous)
      .sort((a, b) => b.score - a.score);

    const viable = ranked.filter((candidate) => candidate.visible && candidate.enabled);
    const top = viable[0];
    const second = viable[1] || { score: -999 };
    if (!top) return { decision: 'ABSTAIN', ranked };
    if (top.score >= 62 && top.score - second.score >= 10) {
      return { decision: 'AUTO', target: top.el, ranked };
    }
    if (pack && top.score >= 50) return { decision: 'AI_REVIEW', ranked };
    if (top.score < 55) return { decision: 'ABSTAIN', ranked };
    return { decision: 'AI_REVIEW', ranked };
  }

  function menuCandidates(doc, fp) {
    const pack = fp.semanticActionId ? ACTION_PACKS[fp.semanticActionId] : null;
    if (!pack?.menuNames?.length) return [];
    return interactiveElements(doc)
      .map(candidateRecord)
      .filter((candidate) => candidate.visible && candidate.enabled && !candidate.dangerous)
      .filter((candidate) => candidate.attrs['aria-haspopup'] === 'menu' || candidate.role === 'button')
      .map((candidate) => {
        const nameScore = fuzzyNameScore(pack.menuNames, candidate.names);
        const contextScore = overlap(pack.contextNames, candidate.context) ? 20 : 0;
        return { ...candidate, score: nameScore + contextScore };
      })
      .filter((candidate) => candidate.score >= 30)
      .sort((a, b) => b.score - a.score);
  }

  async function resolveWithSafeMenu(doc, fp, { useAdapter = true } = {}) {
    let result = decide(doc, fp, { useAdapter });
    if (result.decision !== 'ABSTAIN' || !useAdapter || !fp.semanticActionId) return result;

    const menus = menuCandidates(doc, fp);
    if (!menus.length) return result;
    if (menus.length > 1 && menus[0].score - menus[1].score < 8) {
      return { decision: 'AI_REVIEW', ranked: result.ranked, menus };
    }

    menus[0].el.click();
    await new Promise((resolve) => setTimeout(resolve, 0));
    result = decide(doc, fp, { useAdapter });
    return { ...result, menuExpanded: true };
  }

  root.NovaSemanticMatcher = {
    ACTION_PACKS,
    fingerprint,
    recognizeSemanticAction,
    candidateRecord,
    decide,
    resolveWithSafeMenu,
  };
})(globalThis);
