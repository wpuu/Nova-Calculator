import fs from 'node:fs';

const file = process.argv[2] || 'docs/growth/fixtures/macro-semantic-variants-v1.json';
const data = JSON.parse(fs.readFileSync(file, 'utf8'));

const norm = (s) => (s ?? '').toString().toLowerCase().replace(/\s+/g, ' ').trim();
const intersects = (a = [], b = []) => {
  const A = new Set(a.map(norm));
  return b.map(norm).some((x) => A.has(x));
};
const attrMatch = (expected = {}, actual = {}) =>
  Object.entries(expected).some(([k, v]) => norm(actual[k]) === norm(v));

function score(fp, candidate) {
  let value = 0;
  if ((fp.roleFamily || []).includes(candidate.role)) value += 15;
  if (intersects(fp.names, candidate.names || [])) value += 25;
  if (attrMatch(fp.stableAttrs, candidate.attrs || {})) value += 30;
  if (intersects(fp.context, candidate.context || [])) value += 15;
  if (fp.kind && fp.kind === candidate.kind) value += 15;
  if (candidate.visible === false) value -= 35;
  if (candidate.enabled === false) value -= 25;
  if (candidate.dangerous) value -= 100;
  return value;
}

function decide(fp, candidates) {
  const ranked = candidates
    .map((candidate) => ({
      ...candidate,
      stableMatch: attrMatch(fp.stableAttrs, candidate.attrs || {}),
      score: score(fp, candidate),
    }))
    .sort((a, b) => b.score - a.score);

  const safeVisible = ranked.filter(
    (candidate) => !candidate.dangerous && candidate.visible !== false && candidate.enabled !== false,
  );

  const top = safeVisible[0];
  const second = safeVisible[1] || { score: -999 };

  if (!top) {
    const blocked = ranked.find((candidate) => !candidate.dangerous && candidate.score >= 55);
    return { decision: blocked ? 'AI_REVIEW' : 'ABSTAIN', ranked };
  }

  if (top.score < 55) return { decision: 'ABSTAIN', ranked };

  if (top.stableMatch && top.score >= 55 && top.score - second.score >= 10) {
    return { decision: 'AUTO', target: top.id, ranked };
  }

  if (top.score >= 70 && top.score - second.score >= 12) {
    return { decision: 'AUTO', target: top.id, ranked };
  }

  return { decision: 'AI_REVIEW', ranked };
}

let passed = 0;
for (const testCase of data.cases) {
  const result = decide(data.action, testCase.candidates);
  const ok = result.decision === testCase.expected;
  if (ok) passed += 1;

  console.log(
    `${ok ? 'PASS' : 'FAIL'}\t${testCase.id}\texpected=${testCase.expected}\tactual=${result.decision}\t${result.ranked
      .map((candidate) => `${candidate.id}:${candidate.score}`)
      .join(',')}`,
  );
}

const percentage = ((passed / data.cases.length) * 100).toFixed(1);
console.log(`RESULT ${passed}/${data.cases.length} ${percentage}%`);

if (passed !== data.cases.length) process.exitCode = 1;
