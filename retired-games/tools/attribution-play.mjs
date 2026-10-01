// Play the phone build of Attribution in node, through its own buttons.
//
// The desktop has a Trace; this is the phone's equivalent, and it is a
// different kind of check. Trace asks whether the game is balanced. This asks
// whether the file that ships is the game -- whether the buttons wire up, the
// rounds turn over, the save round-trips, and the edition that comes out the
// other end agrees with its own arithmetic.
//
// There is no browser here. The DOM is shimmed down to what the build uses:
// innerHTML, querySelector(All), getElementById, dataset, onclick, click,
// localStorage, scrollTo. If the build ever reaches for something else, this
// stops working, which is the point.
//
//   node tools/attribution-play.mjs [web/attribution.html] [nights]

import fs from 'node:fs';
import vm from 'node:vm';

const file = process.argv[2] || 'web/attribution.html';
const nights = Number(process.argv[3] || 200);
const html = fs.readFileSync(file, 'utf8');

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
    this.offsetHeight = 44;
    this.style = {};
    this._html = '';
    this._kids = [];
    this.parentElement = null;
  }
  set innerHTML(v) { this._html = String(v); this._kids = scan(this._html); }
  get innerHTML() { return this._html; }
  querySelector(sel) { return this._match(sel)[0] || null; }
  querySelectorAll(sel) { return this._match(sel); }
  _match(sel) {
    let pred;
    if (sel.startsWith('.')) { const c = sel.slice(1); pred = t => t.cls.includes(c); }
    else if (sel.includes('.')) { const parts = sel.split('.'); pred = t => t.tag === parts[0] && t.cls.includes(parts[1]); }
    else pred = t => t.tag === sel;
    return flatten(this._kids).filter(pred);
  }
  click() { if (this.onclick && !this.disabled) this.onclick(); }
}

/** The whole subtree, in document order -- querySelector is not shallow. */
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

/**
 * A tag-stack parser, because the build reads its own innerHTML back: the
 * driver has to be able to see a line's byline and its marks the same way a
 * player does. Attributes only would not be enough.
 */
function scan(src) {
  const roots = [];
  const stack = [];
  const re = /<(\/?)([a-zA-Z][a-zA-Z0-9]*)\b([^>]*?)(\/?)>/g;
  let m;
  while ((m = re.exec(src))) {
    const tag = m[2].toLowerCase();
    if (m[1] === '/') {
      const el = stack.pop();
      if (el) {
        el._html = src.slice(el._start, m.index);
        el._kids = scan(el._html);
      }
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
      el._html = '';
      el._kids = [];
      if (stack.length === 0) roots.push(el);
    } else {
      stack.push(el);
      if (stack.length === 1) roots.push(el);
    }
  }
  // Anything left open runs to the end of the string.
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
    // From the cached scan, not a fresh one: the build sets onclick on the
    // objects it gets back, so a fresh scan would hand back a stranger.
    return findById(app._kids, id) || findById(bar._kids, id);
  },
  addEventListener() {},
};

// -------------------------------------------------------------- the driver

let failures = 0;
function ok(cond, what) {
  if (!cond) { failures++; console.log('FAIL  ' + what); }
}

function boot() {
  app.innerHTML = '';
  bar.innerHTML = '';
  // A fresh context per night: the build declares its state with const, so
  // reusing one context would keep the previous night's desk alive.
  const sandbox = {
    document, localStorage, console,
    window: { scrollTo() {} },
    Math, Date, JSON, String, Number, parseInt, parseFloat, Array, Object, RegExp, Error,
  };
  sandbox.globalThis = sandbox;
  vm.createContext(sandbox);
  vm.runInContext(source, sandbox, { filename: file });
}

/** A fresh browser with nothing stored. */
function newNight() { store.clear(); boot(); }

/** The same browser, reloaded -- the save file survives, as it would on a phone. */
function reload() { boot(); }

function lines() { return app.querySelectorAll('.line'); }

function bylineOf(el) {
  const m = el.innerHTML.match(/class="by">\u2014 ([^,<]+),/);
  return m ? m[1].trim() : null;
}

function marksOf(el) {
  const m = el.innerHTML.match(/class="marks">([\s\S]*?)<\/div>/);
  return m ? m[1] : '';
}

/** One night, played the way a good player plays it: learn the desk, then file. */
function playNight() {
  document.getElementById('go').click();
  const seen = new Map();
  let guard = 0;
  while (guard++ < 200) {
    if (lines().length === 0) break;

    // Learn the desk: one call on each byline you have not called yet. Every
    // element is re-fetched after a click, because a redraw replaces them, and
    // the line is selected *before* the button is read -- the call button is
    // disabled or not depending on which line is selected.
    for (let i = 0; i < lines().length; i++) {
      lines()[i].click();
      const name = bylineOf(lines()[i]);
      const call = document.getElementById('c');
      if (call && !call.disabled && !seen.has(name)) {
        call.click();
        const mk = marksOf(lines()[i]);
        if (mk.includes('t-true')) seen.set(name, true);
        else if (mk.includes('t-false')) seen.set(name, false);
      }
    }

    // File the round. A filed line stays on screen, marked, so the one to
    // decide is the first that is not marked.
    const roundSize = lines().length;
    for (let k = 0; k < roundSize; k++) {
      const el = lines().find(e => !e.cls.includes('done'));
      if (!el) break;
      const name = bylineOf(el);
      el.click();
      const run = seen.get(name) !== false;
      document.getElementById(run ? 'r' : 's').click();
    }
  }
  return app.innerHTML;
}

// ------------------------------------------------------------------- checks

newNight();
ok(app.innerHTML.includes('Attribution'), 'the open screen draws');
ok(app.innerHTML.includes('Five calls'), 'the opening says how many calls');
ok(app.innerHTML.includes('WHO FILES HERE'), 'the open screen names the desk');
ok(app.innerHTML.includes('Halloran'), 'and every byline on it');
ok(!app.innerHTML.includes('class="line'), 'but not the night, which is dealt after');
ok(bar.innerHTML.includes('Take the desk'), 'the open screen offers the desk');

const scored = [];
for (let n = 0; n < nights; n++) {
  newNight();
  const report = playNight();
  const m = report.match(/(\d+) of the twenty-four came out right/);
  ok(!!m, 'night ' + n + ': the report states the score');
  if (m) scored.push(Number(m[1]));
  if (n === 0) {
    ok(/class="head/.test(report), 'the report has a headline');
    ok(report.includes('You ran'), 'the report has a verdict');
    ok(report.includes('Nobody downstream can tell'), 'the report has the standing line');
    ok((report.match(/<tr>/g) || []).length === 6, 'the report has six bylines');
    ok(/class="never"/.test(report), 'at least one byline went uncalled');
    ok(bar.innerHTML.includes('Start another night'), 'the report offers another night');
  }
}

const mean = scored.reduce((a, b) => a + b, 0) / scored.length;
ok(scored.every(s => s >= 0 && s <= 24), 'every score is between 0 and 24');
ok(mean > 15 && mean < 21, 'a spread player scores in the good band (mean ' + mean.toFixed(2) + ')');

// The save round-trips: play a round, reload, and the desk is where it was.
newNight();
document.getElementById('go').click();
for (const el of lines()) {
  el.click();
  document.getElementById('c').click();
  document.getElementById('s').click();
}
const before = app.innerHTML;
ok(!!store.get('aside.attribution.night'), 'the night is written down');
reload();
ok(app.innerHTML === before, 'and a reload lands on the same round, filed the same way');

console.log((failures === 0 ? 'PASS' : failures + ' FAILED')
  + '  --  ' + nights + ' nights played, mean score ' + mean.toFixed(2) + ' of 24');
if (failures > 0) process.exit(1);
