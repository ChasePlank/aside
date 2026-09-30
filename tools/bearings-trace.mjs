// Drive the phone build's copy of the rule and print the voyage day by day.
//
// web/bearings.html carries a second copy of aside.games.bearings.Bearings,
// because the rule cannot be resolved into a table -- sixteen days, three
// choices a day, and a drift that moves at night. Two copies of a rule is the
// situation that produced the Inventory save-format bug: both builds were
// self-consistent and they disagreed about what the same save meant. This is
// what finds that, and it is the only thing that can.
//
// Usage, from the repository root:
//   node tools/bearings-trace.mjs <seed> [table|a|b|look] > /tmp/js.txt
//   java -cp classes aside.games.bearings.Trace <seed> [table|a|b|look] > /tmp/java.txt
//   diff /tmp/java.txt /tmp/js.txt && echo identical
//
// The model is pulled out of the generated file rather than imported, so the
// thing being tested is the thing that ships. The browser is stubbed just
// enough for the build's own render() to run; nothing here reads the DOM.

import fs from 'node:fs';

const FILE = 'web/bearings.html';
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

const seed = BigInt(process.argv[2]);
const policy = process.argv[3] || 'table';
const res = globalThis.window.__bearings.play(seed, policy);
process.stdout.write(res.states.join('\n') + '\n');
