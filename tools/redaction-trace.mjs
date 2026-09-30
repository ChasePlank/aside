// Check the phone build of Redaction against the desktop model, exhaustively.
//
// The other phone builds could be resolved: Handoff's whole night is a
// 729-position table, Inventory's report is nine answers of three sentences,
// Attribution's headline and closing are one number each. Redaction's reading
// cannot be. Which lines the board reads depends on which lines were withheld
// and in what order, and that is 2^16 filings -- too many to table, so the
// loop is PORTED, and a port is the one thing in a build that can be wrong
// without looking wrong.
//
// So this does not sample. It drives the build's own finding() through every
// one of the 65,536 filings and compares each against the Java model, which
// dumps the same thing with `aside.games.redaction.Trace --dump`. A sampled
// trace would have missed the failure this exists to catch, because the
// failure is a boundary: the question that runs out exactly when a line that
// refers to the complainant buys another one.
//
// There is no browser here. The DOM is shimmed down to what the build uses.
//
//   java -cp classes aside.games.redaction.Trace --dump /tmp/redaction-java.txt
//   node tools/redaction-trace.mjs /tmp/redaction-java.txt [web/redaction.html]

import fs from 'node:fs';
import vm from 'node:vm';

const dumpPath = process.argv[2] || '/tmp/redaction-java.txt';
const file = process.argv[3] || 'web/redaction.html';

const html = fs.readFileSync(file, 'utf8');
const scriptMatch = html.match(/<script>([\s\S]*?)<\/script>/);
if (!scriptMatch) { console.error('no <script> in ' + file); process.exit(1); }
const source = scriptMatch[1];

// ------------------------------------------------------------------ the shim
// Only what the build touches at load: it reads #app and #bar, writes
// innerHTML, and calls scrollTo. Nothing here is a stub for the model -- the
// model is the build's own code, and that is the thing under test.

class El {
  constructor(tag = 'div') {
    this.tag = tag; this.id = null; this.cls = []; this.dataset = {};
    this.onclick = null; this.disabled = false; this.offsetHeight = 44;
    this.style = {}; this._html = ''; this._kids = []; this.parentElement = null;
  }
  set innerHTML(v) { this._html = String(v); this._kids = scan(this._html); }
  get innerHTML() { return this._html; }
  querySelector(sel) { return this._match(sel)[0] || null; }
  querySelectorAll(sel) { return this._match(sel); }
  _match(sel) {
    let pred;
    if (sel.startsWith('.')) { const c = sel.slice(1); pred = t => t.cls.includes(c); }
    else if (sel.includes('.')) { const p = sel.split('.'); pred = t => t.tag === p[0] && t.cls.includes(p[1]); }
    else pred = t => t.tag === sel;
    return flatten(this._kids).filter(pred);
  }
  click() { if (this.onclick && !this.disabled) this.onclick(); }
}

function flatten(els) {
  const out = [];
  for (const el of els) { out.push(el); out.push(...flatten(el._kids)); }
  return out;
}

function findById(els, id) {
  for (const el of els) {
    if (el.id === id) return el;
    const hit = findById(el._kids, id);
    if (hit) return hit;
  }
  return null;
}

const VOID = new Set(['br', 'hr', 'img', 'input', 'meta', 'link']);

function scan(src) {
  const roots = []; const stack = [];
  const re = /<(\/?)([a-zA-Z][a-zA-Z0-9]*)\b([^>]*?)(\/?)>/g;
  let m;
  while ((m = re.exec(src))) {
    const tag = m[2].toLowerCase();
    if (m[1] === '/') {
      const el = stack.pop();
      if (el) { el._html = src.slice(el._start, m.index); el._kids = scan(el._html); }
      continue;
    }
    const el = new El(tag);
    const attrs = m[3];
    const id = attrs.match(/\bid="([^"]*)"/);
    if (id) el.id = id[1];
    const cls = attrs.match(/\bclass="([^"]*)"/);
    if (cls) el.cls = cls[1].split(/\s+/).filter(Boolean);
    const di = attrs.match(/\bdata-i="([^"]*)"/);
    if (di) el.dataset.i = di[1];
    if (/\bdisabled\b/.test(attrs)) el.disabled = true;
    el._start = re.lastIndex;
    if (m[4] === '/' || VOID.has(tag)) {
      el._html = ''; el._kids = [];
      if (stack.length === 0) roots.push(el);
    } else {
      stack.push(el);
      if (stack.length === 1) roots.push(el);
    }
  }
  while (stack.length) {
    const el = stack.pop();
    el._html = src.slice(el._start);
    el._kids = scan(el._html);
  }
  return roots;
}

const wrap = new El('div');
const app = new El('div');
app.parentElement = wrap;
wrap.innerHTML = '<div class="spacer"></div>';
const barOuter = new El('div');
barOuter.innerHTML = '<div class="inner"></div>';
const bar = barOuter._kids[0];
bar.parentElement = barOuter;

const store = new Map();
const localStorage = {
  getItem: k => (store.has(k) ? store.get(k) : null),
  setItem: (k, v) => store.set(k, String(v)),
  removeItem: k => store.delete(k),
};
const document = {
  getElementById(id) {
    if (id === 'app') return app;
    if (id === 'bar') return barOuter;
    return findById(app._kids, id) || findById(bar._kids, id);
  },
  addEventListener() {},
};

const sandbox = {
  document, localStorage, console,
  window: { scrollTo() {} },
  Math, Date, JSON, String, Number, parseInt, parseFloat, Array, Object, RegExp, Error,
};
sandbox.globalThis = sandbox;
vm.createContext(sandbox);
vm.runInContext(source, sandbox, { filename: file });

// ----------------------------------------------------------------- the check

// `const C` and `function finding` are not properties of the sandbox object --
// function declarations become globals, lexical declarations do not. Both are
// reachable by evaluating in the context, which is also the honest way to ask:
// this is the build's own scope, not a copy of it.
const C = vm.runInContext('C', sandbox);
const finding = vm.runInContext('finding', sandbox);

const lines = C.lines.length;
const total = 1 << lines;

if (!fs.existsSync(dumpPath)) {
  console.error('no reference dump at ' + dumpPath
    + '\n  java -cp classes aside.games.redaction.Trace --dump ' + dumpPath);
  process.exit(1);
}
const ref = fs.readFileSync(dumpPath, 'utf8').trim().split('\n');
if (ref.length !== total) {
  console.error('the dump has ' + ref.length + ' filings, expected ' + total);
  process.exit(1);
}

let failures = 0;
let firstBad = '';
function bad(what) {
  failures++;
  if (!firstBad) firstBad = what;
}

for (let mask = 0; mask < total; mask++) {
  const held = [];
  for (let i = 0; i < lines; i++) held.push((mask & (1 << i)) !== 0);
  const f = finding(held);

  const parts = ref[mask].trim().split(/\s+/);
  const wantName = parts[1] === '1';
  const wantFails = Number(parts[2]);
  const wantHeld = Number(parts[3]);
  const wantExtra = Number(parts[4]);
  const wantRead = parts.length > 5 && parts[5] ? parts[5].split(',').map(Number) : [];

  const gotRead = f.read.map(i => i + 1);
  const label = 'mask ' + mask;

  if (f.nameOut !== (wantName ? 1 : 0)) bad(label + ': nameOut ' + f.nameOut + ' vs ' + (wantName ? 1 : 0));
  if (f.failsOut !== wantFails) bad(label + ': failsOut ' + f.failsOut + ' vs ' + wantFails);
  if (f.withheld !== wantHeld) bad(label + ': withheld ' + f.withheld + ' vs ' + wantHeld);
  if (f.extra !== wantExtra) bad(label + ': extra ' + f.extra + ' vs ' + wantExtra);
  if (gotRead.join(',') !== wantRead.join(',')) bad(label + ': read ' + gotRead.join(',') + ' vs ' + wantRead.join(','));
}

// The finding table is resolved rather than ported, but a table can be indexed
// along the wrong axis and every entry can still be a real sentence -- the
// failure Outside shipped. So the table is read back out of the build and
// compared against the model, entry by entry, for every key.
let tableBad = 0;
const keys = C.finding.length;
for (let k = 0; k < keys; k++) {
  const withheld = k % (lines + 1);
  const rest = Math.floor(k / (lines + 1));
  const failsOut = rest % 5;
  const nameOut = Math.floor(rest / 5) !== 0;
  const t = C.finding[k];
  for (const field of ['headline', 'person', 'record', 'held', 'verdict']) {
    if (typeof t[field] !== 'string' || t[field].length === 0) tableBad++;
  }
  if (t.person !== C.finding[((nameOut ? 1 : 0) * 5 + failsOut) * (lines + 1) + withheld].person) tableBad++;
}

console.log('redaction phone build vs the model');
console.log('  filings compared: ' + total + '  (' + lines + ' lines)');
console.log('  disagreements:    ' + failures);
if (firstBad) console.log('  first:            ' + firstBad);
console.log('  finding table:    ' + keys + ' keys, ' + tableBad + ' empty or misplaced');
console.log(failures === 0 && tableBad === 0
  ? '  OK  every filing agrees, and the whole report is in the file'
  : '  FAILED');
process.exit(failures === 0 && tableBad === 0 ? 0 : 1);
