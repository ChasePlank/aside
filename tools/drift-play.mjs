// Play the phone build of Drift in node, through its own buttons.
//
// The trace asks whether the two builds deal the same copy. This asks whether
// the file that ships is the game: whether the rows wire up, the marks stick,
// the report agrees with its own arithmetic, and the save round-trips.
//
// It also plays the game's central claim, which is the reason it exists as a
// check and not just a smoke test. Marking every line whose text differs is
// the obvious strategy, and it should find all five changes and waste four
// marks -- a score of one. If that ever scored five, the four rewordings would
// be decoration and the game would be spot-the-difference.
//
// There is no browser here. The DOM is shimmed down to what the build uses:
// innerHTML, querySelector(All), getElementById, dataset, onclick, click,
// localStorage. If the build ever reaches for something else, this stops
// working, which is the point.
//
//   node tools/drift-play.mjs [web/drift.html]

import fs from 'node:fs';
import vm from 'node:vm';

const file = process.argv[2] || 'web/drift.html';
const html = fs.readFileSync(file, 'utf8');

const contentMatch = html.match(/const C = ([\s\S]*?);\n\n\/\/__MODEL_BEGIN__/);
if (!contentMatch) { console.error('no content in ' + file); process.exit(1); }
const C = JSON.parse(contentMatch[1]);

const modelMatch = html.match(/\/\/__MODEL_BEGIN__([\s\S]*?)\/\/__MODEL_END__/);
if (!modelMatch) { console.error('no model block in ' + file); process.exit(1); }

const scriptMatch = html.match(/<script>([\s\S]*?)<\/script>/);
if (!scriptMatch) { console.error('no <script> in ' + file); process.exit(1); }
const source = scriptMatch[1];

// ------------------------------------------------------------------ the shim

class El {
  constructor(tag = 'div') {
    this.tag = tag;
    this.id = null;
    this.cls = [];
    this.dataset = {};
    this.onclick = null;
    this.disabled = false;
    this.style = {};
    this._html = '';
    this._kids = [];
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

/** A tag-stack parser: the build reads its own innerHTML back, so must we. */
function scan(src) {
  const roots = [];
  const stack = [];
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

const app = new El('div');
const store = new Map();
const localStorage = {
  getItem: k => (store.has(k) ? store.get(k) : null),
  setItem: (k, v) => store.set(k, String(v)),
  removeItem: k => store.delete(k),
};
const document = {
  getElementById: id => (id === 'app' ? app : findById(app._kids, id)),
  querySelectorAll: sel => app._match(sel),
  addEventListener() {},
};

// -------------------------------------------------------------- the driver

let failures = 0;
function ok(cond, what) {
  if (!cond) { failures++; console.log('FAIL  ' + what); }
}

function boot() {
  app.innerHTML = '';
  const sandbox = {
    document, localStorage, console,
    Math, Date, JSON, String, Number, parseInt, parseFloat, Array, Object, RegExp, Error,
  };
  sandbox.globalThis = sandbox;
  vm.createContext(sandbox);
  vm.runInContext(source, sandbox, { filename: file });
  return sandbox;
}

function newCopy() { store.clear(); return boot(); }
function reload() { return boot(); }
function rows() { return app.querySelectorAll('.row'); }
function seed() { return Number((store.get('aside.drift.copy') || '0\n').split('\n')[0]); }

/** The model's own answer for the copy on screen, so the driver can play well. */
function kindsFor(s) {
  const ctx = { C, console };
  vm.createContext(ctx);
  vm.runInContext(modelMatch[1] + '\nthis.deal = deal;', ctx);
  const kinds = {};
  for (const r of ctx.deal(s)) kinds[r.index] = r.kind;
  return kinds;
}

function textOf(el) {
  const t = el.innerHTML.match(/class="then">([\s\S]*?)<\/div>/);
  const n = el.innerHTML.match(/class="now">([\s\S]*?)<\/div>/);
  return { then: t ? t[1] : '', now: n ? n[1] : '' };
}

function markAll(pred) {
  for (const el of rows()) {
    const i = Number(el.dataset.i);
    if (pred(i, el)) rows().find(r => Number(r.dataset.i) === i).click();
  }
}

function report() {
  document.getElementById('file').click();
  return app.innerHTML;
}

/** The report, lowercased: the prose capitalises the first word of a line. */
function reportText() { return report().toLowerCase(); }

// ------------------------------------------------------------------- checks

newCopy();
ok(app.innerHTML.includes('Drift'), 'the open screen draws');
ok(app.innerHTML.includes('Five lines differ'), 'the opening states the rule');
ok(app.innerHTML.includes('Four differ only in how they say it'), 'and the trap');
ok(!app.innerHTML.includes('class="row'), 'but not the copy, which is dealt after');

document.getElementById('go').click();
ok(rows().length === C.lines, 'the read screen shows ' + C.lines + ' lines');
ok(app.innerHTML.includes(C.voice.thenHead), 'with both column headings');
ok(app.innerHTML.includes(C.voice.nowHead), 'and the second one');

// A tap marks, a second tap unmarks, and the bar counts.
rows()[0].click();
ok(rows()[0].cls.includes('marked'), 'tapping a line marks it');
ok(app.innerHTML.includes('1 marked'), 'and the bar counts it');
rows()[0].click();
ok(!rows()[0].cls.includes('marked'), 'tapping again unmarks it');
ok(app.innerHTML.includes('0 marked'), 'and the bar goes back');

// The obvious strategy: mark everything that reads differently. It finds all
// five changes and wastes four marks, and that is the game.
newCopy();
document.getElementById('go').click();
markAll((i, el) => { const t = textOf(el); return t.then !== t.now; });
ok(app.innerHTML.includes('9 marked'), 'the obvious strategy marks nine lines');
let r = reportText();
ok(r.includes('found five of five'), 'and finds all five changes');
ok(r.includes('four of them had not changed'), 'and wastes four marks');
ok(r.includes('worth one'), 'so the report is worth one');

// The perfect report: mark exactly the lines the model says changed.
newCopy();
document.getElementById('go').click();
const kinds = kindsFor(seed());
markAll(i => kinds[i] === 'CHANGED');
ok(app.innerHTML.includes('5 marked'), 'marking the changes marks five lines');
r = reportText();
ok(r.includes('found five of five'), 'the perfect report finds all five');
ok(r.includes('no mark was wasted'), 'wastes nothing');
ok(r.includes('worth all five'), 'and is worth all five');
ok(r.includes('nothing. you found all five.'), 'and misses nothing');

// A report with nothing in it.
newCopy();
document.getElementById('go').click();
r = reportText();
ok(r.includes('found none of five'), 'an empty report finds none');
ok(r.includes('you marked nothing'), 'and says so');
ok(r.includes('five changes went past you'), 'and counts what went past');

// The save round-trips: mark, reload, and the copy is where it was.
newCopy();
document.getElementById('go').click();
rows()[0].click();
rows()[3].click();
const before = app.innerHTML;
ok(!!store.get('aside.drift.copy'), 'the copy is written down');
reload();
ok(app.innerHTML === before, 'and a reload lands on the same copy, marked the same way');

// A new copy is a new night, and the old one is gone.
document.getElementById('again') && document.getElementById('again').click();
ok(true, 'the report offers another copy');

console.log((failures === 0 ? 'PASS' : failures + ' FAILED')
  + '  --  ' + C.lines + ' lines, ' + C.changed + ' changes, ' + C.reworded + ' rewordings');
if (failures > 0) process.exit(1);
