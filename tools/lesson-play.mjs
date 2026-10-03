// Play the phone build of Lesson in node, through its own buttons.
//
// The trace asks whether the two builds deal the same handover. This asks
// whether the file that ships is the game: whether the board draws, the nights
// run out, the keeper's belief matches the model's own answer, the report
// agrees with its own arithmetic, and the save round-trips.
//
// It also plays the game's central claim, which is the reason it exists as a
// check and not just a smoke test. A state only ever teaches its own corner, so
// three nights spent in three different corners must always be enough -- and
// three nights spent in the same corner must teach nothing at all. If either of
// those were false the game would still look exactly like this one.
//
// There is no browser here. The DOM is shimmed down to what the build uses:
// innerHTML, getElementById, querySelectorAll, class, dataset, onclick, click,
// disabled, localStorage. If the build ever reaches for something else, this
// stops working, which is the point.
//
//   node tools/lesson-play.mjs [web/lesson.html]

import fs from 'node:fs';
import vm from 'node:vm';

const file = process.argv[2] || 'web/lesson.html';
const html = fs.readFileSync(file, 'utf8');

// The content block is `const C = {...}` and NOTHING follows it on the same
// line: the audio synthesiser is spliced between it and the model marker, so a
// regex that expected `};` there stopped matching when the tap click landed.
// Braces are counted instead.
const cStart = html.indexOf('const C = ');
if (cStart < 0) { console.error('no content in ' + file); process.exit(1); }
let cDepth = 0, cEnd = -1;
for (let i = html.indexOf('{', cStart); i < html.length; i++) {
  if (html[i] === '{') cDepth++;
  else if (html[i] === '}') { cDepth--; if (cDepth === 0) { cEnd = i + 1; break; } }
}
if (cEnd < 0) { console.error('unbalanced content in ' + file); process.exit(1); }
const C = JSON.parse(html.slice(html.indexOf('{', cStart), cEnd));

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
    if (sel.startsWith('.')) {
      const cs = sel.slice(1).split('.');
      pred = t => cs.every(c => t.cls.includes(c));
    } else if (sel.includes('.')) {
      const p = sel.split('.');
      pred = t => t.tag === p[0] && p.slice(1).every(c => t.cls.includes(c));
    } else pred = t => t.tag === sel;
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

// The build deals a fresh handover from Date.now(), so the clock is the seed.
let clock = 1;
function boot() {
  app.innerHTML = '';
  const sandbox = {
    document, localStorage, console,
    Math, Date: { now: () => clock },
    JSON, String, Number, parseInt, parseFloat, Array, Object, RegExp, Error,
  };
  sandbox.globalThis = sandbox;
  vm.createContext(sandbox);
  vm.runInContext(source, sandbox, { filename: file });
  return sandbox;
}

const KEY = 'aside.lesson.handover';
/** A handover nobody has touched: no save, so the opening screen is the one drawn. */
function openAt(seed) { store.clear(); clock = seed; return boot(); }
function reload() { return boot(); }
function rows() { return app.querySelectorAll('.row'); }
function walkBtn() { return document.getElementById('walk'); }
function handBtn() { return document.getElementById('hand'); }
function againBtn() { return document.getElementById('again'); }

/** The model's own answer for the handover on screen, so the driver can play well. */
function modelFor(seed) {
  const ctx = { C, console };
  vm.createContext(ctx);
  vm.runInContext(modelMatch[1] + '\nthis.api = { pick, dealBoard, dealShift, quad, opens };', ctx);
  const { pick, dealBoard, dealShift, quad, opens } = ctx.api;
  return { rule: pick(seed, 1, 8), board: dealBoard(seed), shift: dealShift(seed), quad, opens };
}

function reportText() { return app.innerHTML; }

// ------------------------------------------------------------------- checks

// 1. The open screen.
openAt(1);
ok(app.innerHTML.includes('Lesson'), 'the open screen draws');
ok(app.innerHTML.includes('boiler'), 'and says where you are');
ok(app.innerHTML.includes('pressure is up at'), 'the opening states the thresholds');
ok(!app.innerHTML.includes('class="row'), 'but not the board, which is dealt after');

document.getElementById('go').click();
ok(rows().length === C.board, 'the teach screen shows ' + C.board + ' states on the board');
ok(app.innerHTML.includes(C.voice.tonightHead), 'and tonight');
ok(app.querySelectorAll('.tonight').length === C.shift, 'with ' + C.shift + ' states on the shift');
ok(app.innerHTML.includes(C.voice.watching), 'and the keeper has seen nothing yet');

// 2. The nights run out, and the button knows it.
for (let i = 0; i < C.shows; i++) {
  const b = walkBtn();
  ok(b && !b.disabled, 'night ' + (i + 1) + ' can be spent');
  b.click();
}
ok(!walkBtn(), 'after ' + C.shows + ' nights the walk button is gone');
ok(handBtn(), 'and the handover button is there instead');
ok(app.querySelectorAll('.row.done').length === C.shows, 'and ' + C.shows + ' states are marked shown');

// 3. The belief on screen is the model's own answer, not a second opinion.
{
  const m = modelFor(1);
  const shown = [];
  // Replay the same three nights the driver just spent: the first three rows.
  for (let i = 0; i < C.shows; i++) shown.push(i);
  let mask = 0;
  for (let r = 0; r < 8; r++) {
    let good = true;
    for (const i of shown) if (m.opens(r, m.board[i]) !== m.opens(m.rule, m.board[i])) { good = false; break; }
    if (good) mask |= 1 << r;
  }
  ok(app.innerHTML.includes(C.belief[mask]), 'the keeper says what the model says they believe');
  const n = (mask.toString(2).match(/1/g) || []).length;
  ok(app.innerHTML.includes(C.confidence[n]), 'and the count under it agrees');
}

// 4. The report, and its arithmetic.
handBtn().click();
ok(app.innerHTML.includes(C.voice.reportHead) || app.innerHTML.includes(C.voice.shiftHead),
  'the report draws');
ok(app.querySelectorAll('.shift').length === C.shift, 'with every state of the shift read back');
ok(app.querySelectorAll('.m').length === C.shift, 'and a mark against each');
ok(app.innerHTML.includes(C.voice.beliefHead), 'and what they believed');

// 5. The save round-trips, in the desktop's own format.
{
  const before = app.innerHTML;
  reload();
  ok(app.innerHTML === before, 'reloading comes back to the same screen, byte for byte');
  const saved = store.get(KEY).split('\n');
  ok(saved.length === 3, 'the save is the three lines the desktop writes');
  ok(Number(saved[0]) === 1, 'and it keeps the seed');
  ok(saved[2] === '1', 'and that they have taken the shift');
}

// 6. The central claim, played: three nights in three different corners always
//    teach the shift, and three nights in one corner teach nothing.
{
  let covered = 0, sameCorner = 0, sameCornerTaught = 0;
  for (let seed = 1; seed <= 120; seed++) {
    const m = modelFor(seed);
    const wanted = [];
    for (const x of m.shift) { const q = m.quad(m.quad === undefined ? x : x); }
    for (const x of m.shift) { const q = m.quad(x); if (wanted.indexOf(q) < 0) wanted.push(q); }

    // One state from each of the shift's corners.
    openAt(seed);
    document.getElementById('go').click();
    for (const q of wanted) {
      const i = m.board.findIndex((x, k) => m.quad(x) === q && !rows()[k].cls.includes('done'));
      rows()[i].click();
      walkBtn().click();
    }
    const actsRight = () => {
      // Read the marks off the report and check none of them is wrong.
      handBtn().click();
      return app.querySelectorAll('.m.no').length === 0;
    };
    if (actsRight()) covered++;

    // Three states that all sit in one corner. Only the two crowded corners
    // have three states on the board, so the check runs on those.
    const q0 = m.quad(m.board[0]);
    const same = [];
    for (let i = 0; i < m.board.length && same.length < C.shows; i++) {
      if (m.quad(m.board[i]) === q0) same.push(i);
    }
    if (same.length < C.shows) continue;
    openAt(seed);
    document.getElementById('go').click();
    for (const i of same) { rows()[i].click(); walkBtn().click(); }
    handBtn().click();
    if (app.querySelectorAll('.m.no').length === 0) sameCornerTaught++;
    sameCorner++;
  }
  ok(covered === 120, 'covering the shift\'s corners always teaches the shift (' + covered + '/120)');
  ok(sameCorner > 0, 'the crowded corners were exercised');
  ok(sameCornerTaught < sameCorner, 'and three nights in one corner do not ('
    + sameCornerTaught + '/' + sameCorner + ')');
  console.log('       one state per shift corner: safe ' + covered + '/' + sameCorner);
  console.log('       three states in one corner: safe ' + sameCornerTaught + '/' + sameCorner);
}

// 7. A redundant night says so, on the screen, while it is happening.
{
  const m = modelFor(7);
  const q0 = m.quad(m.board[0]);
  const same = [];
  for (let i = 0; i < m.board.length && same.length < 2; i++) {
    if (m.quad(m.board[i]) === q0) same.push(i);
  }
  ok(same.length === 2, 'seed 7 has two states in its first corner');
  openAt(7);
  document.getElementById('go').click();
  rows()[same[0]].click();
  walkBtn().click();
  rows()[same[1]].click();
  walkBtn().click();
  ok(app.innerHTML.includes(C.voice.nothingNew),
    'a second night in the same corner is called out as the same corner');
}

// 8. Another handover deals a different one.
{
  // Finish the handover first: the third night, then the shift.
  walkBtn().click();
  handBtn().click();
  ok(againBtn(), 'the report offers another handover');
  const before = store.get(KEY);
  againBtn().click();
  ok(app.innerHTML.includes('Lesson') && !app.innerHTML.includes(C.voice.shiftHead),
    'another handover goes back to the opening');
  ok(store.get(KEY) !== before, 'and writes a new one');
}

console.log('');
if (failures > 0) { console.log(failures + ' failed'); process.exit(1); }
console.log('the phone build is the game.');
