// Hold the phone's handover against the desktop's, seed by seed.
//
// The phone build deals its own board and its own shift -- it has to, because
// the board is what the player chooses from and the shift is what they are
// choosing for. A ported model is the one thing that can be wrong on both sides
// at once without either side noticing.
//
// What is compared is not just the deal. The standing set is a set intersection
// over eight rules and four corners, and it is the number the whole game turns
// on: if the two builds disagree about what the student believes after the same
// three nights, then one of them is teaching a different lesson and neither
// would ever show it. So the trace drives both builds through the same three
// nights and compares the belief as well as the board.
//
// The model block is delimited in the build (//__MODEL_BEGIN__ .. __END__) so
// it can be lifted out and run here without a DOM. If the build ever stops
// marking its model, this stops working, which is the point.
//
//   node tools/lesson-trace.mjs [web/lesson.html] [seeds]

import fs from 'node:fs';
import vm from 'node:vm';
import { execFileSync } from 'node:child_process';
import { javaBin, classesDir } from './java.mjs';

const file = process.argv[2] || 'web/lesson.html';
const seeds = Number(process.argv[3] || 200);
const html = fs.readFileSync(file, 'utf8');

// The content block is `const C = {...}` and NOTHING follows it on the
// same line: the audio synthesiser is spliced between it and the model
// marker, so a regex that expected `};` there stopped matching when the
// tap click landed. Braces are counted instead.
const start = html.indexOf('const C = ');
if (start < 0) { console.error('no content in ' + file); process.exit(1); }
let depth = 0, end = -1;
for (let i = html.indexOf('{', start); i < html.length; i++) {
  if (html[i] === '{') depth++;
  else if (html[i] === '}') { depth--; if (depth === 0) { end = i + 1; break; } }
}
if (end < 0) { console.error('unbalanced content in ' + file); process.exit(1); }
const C = JSON.parse(html.slice(html.indexOf('{', start), end));

const modelMatch = html.match(/\/\/__MODEL_BEGIN__([\s\S]*?)\/\/__MODEL_END__/);
if (!modelMatch) { console.error('no model block in ' + file); process.exit(1); }

const ctx = { C, console };
vm.createContext(ctx);
vm.runInContext(modelMatch[1] + '\nthis.api = { pick, dealBoard, dealShift, quad, opens };', ctx);
const { pick, dealBoard, dealShift, quad, opens } = ctx.api;

// The desktop's answer, straight from the model.
const java = execFileSync(javaBin(), ['-cp', classesDir(), 'aside.games.lesson.Trace', String(seeds)],
  { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });

const want = new Map();
for (const line of java.split('\n')) {
  if (!line) continue;
  const p = line.split('\t');
  const board = [], shift = [];
  for (let i = 2; i < 14; i++) {
    const t = p[i].split(':').map(Number);
    board.push({ r: [t[0], t[1]], q: t[2] });
  }
  for (let i = 14; i < 17; i++) {
    const t = p[i].split(':').map(Number);
    shift.push({ r: [t[0], t[1]], q: t[2] });
  }
  want.set(p[0], {
    rule: Number(p[1]), board, shift,
    nights: p[17] ? p[17].split(',').map(Number) : [],
    mask: Number(p[18]), preferred: Number(p[19]), safe: Number(p[20]),
  });
}

let compared = 0, disagreed = 0, nightsCompared = 0, masksCompared = 0;
const examples = [];
const fail = (seed, what, got, expected) => {
  disagreed++;
  if (examples.length < 6) examples.push('seed ' + seed + '  ' + what
    + '\n  java  ' + expected + '\n  phone ' + got);
};

for (let seed = 0; seed < seeds; seed++) {
  const w = want.get(String(seed));
  if (!w) { fail(seed, 'missing from the desktop trace', '-', '-'); continue; }
  compared++;

  const rule = pick(seed, 1, 8);
  if (rule !== w.rule) fail(seed, 'rule', rule, w.rule);

  const board = dealBoard(seed);
  if (board.length !== w.board.length) fail(seed, 'board length', board.length, w.board.length);
  for (let i = 0; i < Math.min(board.length, w.board.length); i++) {
    if (board[i][0] !== w.board[i].r[0] || board[i][1] !== w.board[i].r[1]) {
      fail(seed, 'board state ' + i, board[i].join(':'), w.board[i].r.join(':'));
    }
    if (quad(board[i]) !== w.board[i].q) {
      fail(seed, 'board corner ' + i, quad(board[i]), w.board[i].q);
    }
  }

  const shift = dealShift(seed);
  if (shift.length !== w.shift.length) fail(seed, 'shift length', shift.length, w.shift.length);
  for (let i = 0; i < Math.min(shift.length, w.shift.length); i++) {
    if (shift[i][0] !== w.shift[i].r[0] || shift[i][1] !== w.shift[i].r[1]) {
      fail(seed, 'shift state ' + i, shift[i].join(':'), w.shift[i].r.join(':'));
    }
    if (quad(shift[i]) !== w.shift[i].q) {
      fail(seed, 'shift corner ' + i, quad(shift[i]), w.shift[i].q);
    }
  }

  // The same three nights the desktop spends: one state from each of the
  // shift's corners, in the order the corners come up.
  const wanted = [];
  for (const x of shift) { const q = quad(x); if (wanted.indexOf(q) < 0) wanted.push(q); }
  const shown = [];
  for (const q of wanted) {
    for (let i = 0; i < board.length; i++) {
      if (quad(board[i]) === q && shown.indexOf(i) < 0) { shown.push(i); break; }
    }
  }
  nightsCompared++;
  if (shown.join(',') !== w.nights.join(',')) {
    fail(seed, 'the nights spent', shown.join(','), w.nights.join(','));
  }

  let mask = 0;
  for (let r = 0; r < 8; r++) {
    let ok = true;
    for (const i of shown) {
      if (opens(r, board[i]) !== opens(rule, board[i])) { ok = false; break; }
    }
    if (ok) mask |= 1 << r;
  }
  masksCompared++;
  if (mask !== w.mask) fail(seed, 'the standing set', mask, w.mask);

  let preferred = rule;
  for (let r = 0; r < 8; r++) if (mask & (1 << r)) { preferred = r; break; }
  if (preferred !== w.preferred) fail(seed, 'the rule they act on', preferred, w.preferred);

  const safe = shift.every(x => opens(preferred, x) === opens(rule, x));
  if ((safe ? 1 : 0) !== w.safe) fail(seed, 'the handover', safe ? 1 : 0, w.safe);
}

console.log('seeds compared:  ' + seeds);
console.log('states compared: ' + (compared * (C.board + C.shift)));
console.log('nights compared: ' + nightsCompared);
console.log('beliefs compared:' + masksCompared);
console.log('disagreed:       ' + disagreed);
for (const e of examples) console.log('\n' + e);
if (disagreed > 0) process.exit(1);
console.log('\nboth builds deal the same handover, and teach the same lesson.');
