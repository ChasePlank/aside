// Hold the phone's house against the desktop's, seed by seed.
//
// The phone build deals its own list -- it has to, because the list is what the
// player chooses from. A ported model is the one thing that can be wrong on
// both sides at once without either side noticing.
//
// What is compared is not just the deal. What you remember is a comparison of
// two strings per thing, and it is the number the whole game turns on: if the
// two builds disagree about what you come out of the house with, then one of
// them is telling a different story about memory and neither would ever show
// it. So the trace fills the same bag on both sides -- the five heaviest things
// nobody else knew, which is the bag the rule implies -- and compares the
// memory as well as the list.
//
// The model block is delimited in the build (//__MODEL_BEGIN__ .. __END__) so
// it can be lifted out and run here without a DOM. If the build ever stops
// marking its model, this stops working, which is the point.
//
//   node tools/omission-trace.mjs [web/omission.html] [seeds]

import fs from 'node:fs';
import vm from 'node:vm';
import { execFileSync } from 'node:child_process';

const file = process.argv[2] || 'web/omission.html';
const seeds = Number(process.argv[3] || 200);
const html = fs.readFileSync(file, 'utf8');

const contentMatch = html.match(/const C = ([\s\S]*?);\n\n\/\/__MODEL_BEGIN__/);
if (!contentMatch) { console.error('no content in ' + file); process.exit(1); }
const C = JSON.parse(contentMatch[1]);

const modelMatch = html.match(/\/\/__MODEL_BEGIN__([\s\S]*?)\/\/__MODEL_END__/);
if (!modelMatch) { console.error('no model block in ' + file); process.exit(1); }

const ctx = { C, console };
vm.createContext(ctx);
vm.runInContext(modelMatch[1] + '\nthis.api = { dealList, believed, right, scoreOf, totalOf,'
  + ' rightCount, silentCount, bestOf, heaviestLost, heaviestWasted, wastedSlots };', ctx);
const api = ctx.api;

// The desktop's answer, straight from the model.
const java = execFileSync('java', ['-cp', 'classes', 'aside.games.omission.Trace', String(seeds)],
  { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });

const want = new Map();
for (const line of java.split('\n')) {
  if (!line) continue;
  const p = line.split('\t');
  const list = [];
  for (let i = 1; i <= C.list; i++) {
    const t = p[i].split(':');
    list.push({ id: t[0], matters: Number(t[1]), told: t[2] === '1', assumed: t.slice(3).join(':') });
  }
  want.set(p[0], {
    list,
    bag: p[13] ? p[13].split(',').map(Number) : [],
    score: Number(p[14]), best: Number(p[15]), silent: Number(p[16]),
    right: p.slice(17, 17 + C.list).map(Number),
  });
}

let compared = 0, disagreed = 0, thingsCompared = 0, memoriesCompared = 0;
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

  const list = api.dealList(seed);
  if (list.length !== w.list.length) fail(seed, 'list length', list.length, w.list.length);
  for (let i = 0; i < Math.min(list.length, w.list.length); i++) {
    thingsCompared++;
    if (list[i].id !== w.list[i].id) fail(seed, 'thing ' + i, list[i].id, w.list[i].id);
    if (list[i].matters !== w.list[i].matters) fail(seed, 'weight of ' + list[i].id, list[i].matters, w.list[i].matters);
    if (list[i].told !== w.list[i].told) fail(seed, 'whether ' + list[i].id + ' was said', list[i].told, w.list[i].told);
    if (list[i].assumed !== w.list[i].assumed) fail(seed, 'the assumption for ' + list[i].id, list[i].assumed, w.list[i].assumed);
  }

  // The bag the rule implies, filled the same way on both sides.
  const silent = [];
  for (let i = 0; i < list.length; i++) if (!list[i].told) silent.push(i);
  silent.sort((x, y) => list[y].matters - list[x].matters !== 0
    ? list[y].matters - list[x].matters : x - y);
  const bag = silent.slice(0, C.slots);
  if (bag.join(',') !== w.bag.join(',')) fail(seed, 'the bag', bag.join(','), w.bag.join(','));

  memoriesCompared++;
  for (let i = 0; i < list.length; i++) {
    const r = api.right(list, bag, i) ? 1 : 0;
    if (r !== w.right[i]) fail(seed, 'what you remember about ' + list[i].id, r, w.right[i]);
    if (api.believed(list, bag, i) !== (w.right[i] ? list[i].text : list[i].assumed)) {
      fail(seed, 'the memory itself for ' + list[i].id,
        api.believed(list, bag, i), w.right[i] ? list[i].text : list[i].assumed);
    }
  }
  if (api.scoreOf(list, bag) !== w.score) fail(seed, 'the score', api.scoreOf(list, bag), w.score);
  if (api.bestOf(list) !== w.best) fail(seed, 'the best there was', api.bestOf(list), w.best);
  if (api.silentCount(list) !== w.silent) fail(seed, 'the silent count', api.silentCount(list), w.silent);
  if (api.totalOf(list) !== list.reduce((a, d) => a + d.matters, 0)) {
    fail(seed, 'the total', api.totalOf(list), list.reduce((a, d) => a + d.matters, 0));
  }
  // The bag is the five heaviest silent things, so it wastes nothing -- and it
  // loses something exactly when there were more silent things than slots,
  // which is every seed by construction.
  const lost = api.heaviestLost(list, bag), wasted = api.heaviestWasted(list, bag);
  if (wasted !== null) fail(seed, 'a bag of silent things wasted a slot', wasted.id, 'null');
  if (api.silentCount(list) > C.slots && lost === null) {
    fail(seed, 'a full bag of silent things lost nothing', 'null', 'something');
  }
  if (api.silentCount(list) <= C.slots && lost !== null) {
    fail(seed, 'a bag that covered every silent thing lost something', lost.id, 'null');
  }
}

console.log('seeds compared:   ' + seeds);
console.log('things compared:  ' + thingsCompared);
console.log('memories compared:' + memoriesCompared);
console.log('disagreed:        ' + disagreed);
for (const e of examples) console.log('\n' + e);
if (disagreed > 0) process.exit(1);
console.log('\nboth builds deal the same house, and remember the same things.');
