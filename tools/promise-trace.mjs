// Hold the phone's night against the desktop's, answer set by answer set.
//
// The phone build computes the night itself -- what is due and what the water
// takes both come out of the same four crossings -- and a ported rule is the one
// thing that can be wrong on both sides at once without either side noticing.
// This drives both over all 256 answer sets and all five nights and compares the
// load and the overage, and then compares every line the report can say.
//
// The model block is delimited in the build (//__MODEL_BEGIN__ .. __END__) so it
// can be lifted out and run here without a DOM. If the build ever stops marking
// its model, this stops working, which is the point.
//
//   node tools/promise-trace.mjs [web/promise.html]

import fs from 'node:fs';
import vm from 'node:vm';
import { execFileSync } from 'node:child_process';

const file = process.argv[2] || 'web/promise.html';
const html = fs.readFileSync(file, 'utf8');

const contentMatch = html.match(/const C = ([\s\S]*?);\n\n\/\/__MODEL_BEGIN__/);
if (!contentMatch) { console.error('no content in ' + file); process.exit(1); }
const C = JSON.parse(contentMatch[1]);

const modelMatch = html.match(/\/\/__MODEL_BEGIN__([\s\S]*?)\/\/__MODEL_END__/);
if (!modelMatch) { console.error('no model block in ' + file); process.exit(1); }

// The phone's model reads `state`, so give it one that is not playing.
const ctx = {
  C,
  state: { answers: new Array(C.asks.length).fill(-1), broken: new Array(C.asks.length).fill(false), night: 1, askAt: 0, reported: false },
  console,
};
vm.createContext(ctx);
vm.runInContext(modelMatch[1] + '\nthis.load = load; this.over = over; this.dueTonight = dueTonight;', ctx);
const { load, over, dueTonight } = ctx;

const java = execFileSync('java', ['-cp', 'classes', 'aside.games.promise.Trace'],
  { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });

const want = new Map();
for (const line of java.split('\n')) {
  if (!line) continue;
  const parts = line.split('\t');
  want.set(parts[0] + ':' + parts[1], parts.slice(2).join('\t'));
}

let compared = 0, disagreed = 0;
const examples = [];
function hold(name, mine, theirs) {
  compared++;
  if (mine === theirs) return;
  disagreed++;
  if (examples.length < 6) {
    examples.push(name + '\n  java  ' + JSON.stringify(theirs) + '\n  phone ' + JSON.stringify(mine));
  }
}

// The nights, in the order the trace printed them.
for (const line of java.split('\n')) {
  if (!line.startsWith('night\t')) continue;
  const p = line.split('\t');
  const mask = Number(p[1]), night = Number(p[2]);
  ctx.state.night = night;
  for (let i = 0; i < C.asks.length; i++) {
    ctx.state.answers[i] = (mask & (1 << i)) !== 0 ? 1 : 0;
    ctx.state.broken[i] = false;
  }
  hold('answer set ' + mask + ' night ' + night,
    String(load()) + '\t' + String(over()), p[3] + '\t' + p[4]);
}

// And the words. A line the phone says that the desktop would not is a
// divergence dressed up as a feature, so every one of them is compared.
for (let i = 0; i < C.asks.length; i++) hold('due line ' + i, C.dueLine[i], want.get('due:' + i));
for (let n = 1; n <= C.nights; n++) {
  hold('ordinal ' + n, C.ordinal[n], want.get('ordinal:' + n));
  hold('night label ' + n, C.nightLabel[n], want.get('label:' + n));
}
for (let c = 1; c <= 2; c++) hold('cost word ' + c, C.costWord[c], want.get('cost:' + c));
for (let l = 0; l <= 12; l++) {
  hold('load line ' + l, C.loadLine[l], want.get('load:' + l));
  hold('over line ' + l, C.overLine[l], want.get('over:' + l));
}
for (let k = 0; k <= C.asks.length; k++) hold('breaks line ' + k, C.breaksLine[k], want.get('breaks:' + k));
for (let s = -C.asks.length; s <= C.max; s++) {
  hold('worth line ' + s, C.worthLine[s - FLOOR_OF(C)], want.get('worth:' + s));
  hold('closing ' + s, C.closing[s - FLOOR_OF(C)], want.get('closing:' + s));
}
hold('the empty closing', C.closingEmpty, want.get('empty:0'));

function FLOOR_OF(c) { return -c.asks.length; }

console.log('answer sets compared: ' + (1 << C.asks.length));
console.log('nights compared:      ' + (1 << C.asks.length) * C.nights);
console.log('things compared:      ' + compared);
console.log('disagreed:            ' + disagreed);
for (const e of examples) console.log('\n' + e);
if (disagreed > 0) process.exit(1);
console.log('\nboth builds keep the same season.');
