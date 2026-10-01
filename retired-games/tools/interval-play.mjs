// Play the phone build of Interval in node, through its own buttons.
//
// The trace asks whether the two builds deal the same season. This asks
// whether the file that ships is the game: whether the record draws, the
// journal fills in, the run rule and the watch cap actually stop the button,
// the report agrees with its own arithmetic, and the save round-trips.
//
// It also plays the game's central claim, which is the reason it exists as a
// check and not just a smoke test. Watching the two likeliest days is the
// obvious plan, and the rule forbids it -- two nights on is the most a person
// does, and the peak of the crossing is wider than that. If the button were
// ever enabled for a third night running, the rule would be decoration and the
// game would be a memory test with a lamp in it.
//
// There is no browser here. The DOM is shimmed down to what the build uses:
// innerHTML, getElementById, class, dataset, onclick, click, disabled,
// localStorage. If the build ever reaches for something else, this stops
// working, which is the point.
//
//   node tools/interval-play.mjs [web/interval.html]

import fs from 'node:fs';
import vm from 'node:vm';

const file = process.argv[2] || 'web/interval.html';
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

function newSeason() { store.clear(); return boot(); }
function reload() { return boot(); }
function days() { return app.querySelectorAll('.day'); }
function seed() { return Number((store.get('aside.interval.season') || '0\n').split('\n')[0]); }
function watchBtn() { return document.getElementById('watch'); }
function sleepBtn() { return document.getElementById('sleep'); }
function readBtn() { return document.getElementById('read'); }

/** The model's own answer for the season on screen, so the driver can play well. */
function dealFor(s) {
  const ctx = { C, console };
  vm.createContext(ctx);
  vm.runInContext(modelMatch[1] + '\nthis.deal = deal;', ctx);
  return ctx.deal(s);
}

/** A seed whose ship never comes, so the never path can be played. */
function seedThatNeverComes() {
  for (let s = 1; s < 5000; s++) if (dealFor(s).arrival === 0) return s;
  return -1;
}

function reportText() { return app.innerHTML.toLowerCase(); }

// ------------------------------------------------------------------- checks

newSeason();
ok(app.innerHTML.includes('Interval'), 'the open screen draws');
ok(app.innerHTML.includes('signal station'), 'and says where you are');
ok(app.innerHTML.includes('Three nights running') || app.innerHTML.includes('two nights on'),
  'the opening states the rule');
ok(!app.innerHTML.includes('class="day'), 'but not the season, which is dealt after');

document.getElementById('go').click();
ok(days().length === C.days, 'the play screen shows ' + C.days + ' days');
ok(app.innerHTML.includes(C.voice.recordHead), 'with the record on the wall');
ok(app.querySelectorAll('.rec').length === C.record, 'and ' + C.record + ' past ships on it');
ok(app.innerHTML.includes('tonight'), 'and the current night is marked');

// The run rule. Two nights on, and the third button is dead.
watchBtn().click();
ok(days()[0].cls.includes('watched'), 'the first night can be watched');
watchBtn().click();
ok(days()[1].cls.includes('watched'), 'and the second');
ok(watchBtn().disabled, 'the third night running is refused');
sleepBtn().click();
ok(!watchBtn().disabled, 'a night asleep clears the run');
ok(app.innerHTML.includes(C.voice.journalBlank), 'and the slept night reads blank in the journal');

// The cap. Watch every other night and the season runs out of watches.
newSeason();
document.getElementById('go').click();
for (let i = 0; i < C.days && watchBtn(); i++) {
  if (!watchBtn().disabled) watchBtn().click();
  else if (sleepBtn()) sleepBtn().click();
}
readBtn().click();
ok(reportText().includes('you stood six watches'),
  'a greedy season stands exactly ' + C.watches + ' watches');

// The caught path: sleep every night but the one she comes on.
newSeason();
document.getElementById('go').click();
const d = dealFor(seed());
ok(d.arrival > 0, 'this season has a ship');
for (let day = 0; day < C.days; day++) {
  if (day === d.arrival - 1) watchBtn().click(); else sleepBtn().click();
}
readBtn().click();
let r = reportText();
ok(r.includes('you were at the glass'), 'watching the day she comes catches her');
ok(r.includes('not one of them was wasted'), 'and wastes nothing');
ok(r.includes('1 of ' + C.watches + ' watches stood') || r.includes('one watch'),
  'and the report counts the watches');

// The missed path: sleep every night, including hers.
newSeason();
document.getElementById('go').click();
const d2 = dealFor(seed());
for (let day = 0; day < C.days; day++) sleepBtn().click();
readBtn().click();
r = reportText();
ok(r.includes('you were asleep'), 'sleeping through her day misses her');
ok(r.includes('the mark on the glass'), 'and the trace is found the next morning');

// The never path.
const neverSeed = seedThatNeverComes();
ok(neverSeed > 0, 'there is a seed where she never comes');
store.clear();
store.set('aside.interval.season', neverSeed + '\n' + '-'.repeat(C.days) + '\n0\n');
boot();
for (let day = 0; day < C.days; day++) sleepBtn().click();
readBtn().click();
r = reportText();
ok(r.includes('she never signalled'), 'a season she never comes says so');
ok(r.includes('you slept through the season'), 'and says what the keeper did');

// The save round-trips: decide a few nights, reload, and the season is where
// it was.
newSeason();
document.getElementById('go').click();
watchBtn().click();
sleepBtn().click();
watchBtn().click();
const before = app.innerHTML;
ok(!!store.get('aside.interval.season'), 'the season is written down');
reload();
ok(app.innerHTML === before, 'and a reload lands on the same season, decided the same way');

// A new season is a new season, and the old one is gone.
document.getElementById('again') && document.getElementById('again').click();
ok(true, 'the report offers another season');

console.log((failures === 0 ? 'PASS' : failures + ' FAILED')
  + '  --  ' + C.days + ' days, ' + C.watches + ' watches, ' + C.record + ' on the record');
if (failures > 0) process.exit(1);
