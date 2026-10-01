// Drive the phone build's copy of the rule and print the night question by
// question.
//
// web/corroboration.html carries a second copy of
// aside.games.corroboration.Corroboration, because the rule cannot be resolved
// into a table -- what the player is doing is spending a budget of questions
// against answers that depend on the questions already spent. Two copies of a
// rule is the situation that produced the Inventory save-format bug: both
// builds were self-consistent and they disagreed about what the same save
// meant. This is what finds that, and it is the only thing that can.
//
// Usage, from the repository root:
//   node tools/corroboration-trace.mjs <seed> > /tmp/js.txt
//   java -cp classes aside.games.corroboration.Trace <seed> > /tmp/java.txt
//   diff /tmp/java.txt /tmp/js.txt && echo identical
//
// The model is pulled out of the generated file rather than imported, so the
// thing being tested is the thing that ships. The browser is stubbed just
// enough for the build's own render() to run; nothing here reads the DOM.

import fs from 'node:fs';

const FILE = 'web/corroboration.html';
const html = fs.readFileSync(FILE, 'utf8');
const m = html.match(/<script>([\s\S]*?)<\/script>/);
if (!m) throw new Error('no <script> in ' + FILE + ' -- run from the repository root');

const store = new Map();
globalThis.localStorage = {
  getItem: k => (store.has(k) ? store.get(k) : null),
  setItem: (k, v) => { store.set(k, String(v)); },
  removeItem: k => { store.delete(k); },
};
const stubEl = () => ({
  innerHTML: '', hidden: false, style: {}, offsetHeight: 0, onclick: null,
  querySelectorAll: () => [], querySelector: () => null, addEventListener: () => {},
});
globalThis.document = {
  getElementById: stubEl, querySelectorAll: () => [], addEventListener: () => {},
};
globalThis.window = { scrollTo: () => {} };

new Function(m[1])();

const M = globalThis.window.__corroboration;
if (!M) throw new Error('the build did not expose window.__corroboration');

const seed = Number(process.argv[2] || 0);
const c = M.newNight(seed);
const N = M.N;

const dump = c => {
  let s = 'left=' + c.left;
  s += ' asked=';
  for (let o = 0; o < 2; o++) for (let a = 0; a < N; a++) s += c.asked[o][a] ? 1 : 0;
  s += ' answer=';
  for (let o = 0; o < 2; o++) for (let a = 0; a < N; a++) s += c.answer[o][a] + ',';
  s += ' est=';
  for (let a = 0; a < N; a++) s += M.established(c, a) + ',';
  s += ' claim=';
  for (let o = 0; o < 2; o++) for (let a = 0; a < N; a++) s += M.claim(c, o, a) + ',';
  return s;
};

const out = ['seed ' + seed];
let q = 0;
for (let a = 0; a < N; a++) {
  M.check(c, 0, a);
  out.push('q' + (q++) + ' ' + dump(c));
  if (!M.decisive(c, 0, a)) {
    M.check(c, 1, a);
    out.push('q' + (q++) + ' ' + dump(c));
  }
  c.filed[a] = M.established(c, a);
}
c.filedDone = true;

out.push('filed ' + c.filed.join(' '));
out.push('truth ' + c.truth.join(' '));
out.push('verdict ' + c.truth.map((_, a) => M.verdict(c, a)).join(' '));
out.push('closing ' + M.closing(c));
process.stdout.write(out.join('\n') + '\n');
