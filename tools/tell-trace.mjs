// Hold the phone's house against the desktop's, seed by seed and night by night.
//
// The phone build runs the house itself -- it has to, because the counts on the
// floor are what the player reads and they are built up move by move, not
// looked up. A ported model is the one thing that can be wrong on both sides at
// once without either side noticing, and this one has three places to go wrong
// that a summary table would never show: the counter-based draw, the
// expectation rule's tie-break on recency, and the order the read is added up
// in. So this compares the *whole state* after every night, not the outcome.
//
// The policy is SAFE, because it is the only one that is fully deterministic:
// no rng, no threshold, no carried state, so both builds can replay it from the
// seed alone. If a policy ever needs randomness, this stops being able to
// compare anything, which is the point of picking this one.
//
// The model block is delimited in the build (//__MODEL_BEGIN__ .. __END__) so
// it can be lifted out and run here without a DOM. If the build ever stops
// marking its model, this stops working, which is the point.
//
//   node tools/tell-trace.mjs [web/tell.html] [seeds]

import fs from 'node:fs';
import vm from 'node:vm';
import { execFileSync } from 'node:child_process';

const file = process.argv[2] || 'web/tell.html';
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

// The model's state is a set of `let` bindings, so a snapshot taken at export
// time would go stale the moment boot() reassigns one. state() closes over the
// live bindings instead, and that is the only way to read the house from here.
const ctx = { C, console };
vm.createContext(ctx);
// The model block carries the audio synthesiser too, spliced in after
// the tap click landed. It is not part of the model and it references
// the page's own scope, so it is cut before the block is run.
const model = modelMatch[1].replace(/\/\*__AUDIO__\*\/[\s\S]*?(?=\n\/\/ The |\nfunction )/, '');
vm.runInContext(model + `
this.M = {
  boot, nextNight, move, expected, canMove, legal, stepDist, here, done, dist, idx,
  state: () => ({
    size, px, py, ex, ey, read, turn, won, caught,
    lamp: lamp.slice(), hist: hist.map(r => r.slice()), last: last.map(r => r.slice())
  })
};`, ctx);
const M = ctx.M;

// --- the SAFE policy, ported. It is the competent player: take a step that
// surprises the house if there is one, prefer the one that also goes
// somewhere, and fall back to the least-used direction when there is not.
function spread(legal) {
  const st = M.state();
  const i = M.here();
  let best = Infinity, out = legal[0];
  for (const d of legal) if (st.hist[i][d] < best) { best = st.hist[i][d]; out = d; }
  return out;
}
function straight(legal) {
  let best = Infinity, pick = [];
  for (const d of legal) {
    const dd = M.stepDist(d);
    if (dd < best) { best = dd; pick = [d]; }
    else if (dd === best) pick.push(d);
  }
  return pick.length === 1 ? pick[0] : spread(pick);
}
function safe(legal) {
  const exp = M.expected();
  const surprise = legal.filter(d => exp.indexOf(d) < 0);
  if (surprise.length === 0) return spread(legal);
  return straight(surprise);
}
function playSafe() {
  let guard = 0;
  while (!M.done() && guard++ < 500) {
    const legal = M.legal();
    if (legal.length === 0) return;
    M.move(safe(legal));
  }
}

// --- the desktop's answer, straight from the model.
const java = execFileSync('java', ['-cp', 'classes', 'aside.games.tell.Trace', 'dump', String(seeds)],
  { encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 });

const want = new Map();
for (const line of java.split('\n')) {
  if (!line) continue;
  const p = line.split('\t');
  want.set(p[0] + ':' + p[1], p);
}

let compared = 0, disagreed = 0;
const examples = [];
for (let seed = 0; seed < seeds; seed++) {
  M.boot(seed, 0);
  for (let night = 0; night < C.nights; night++) {
    playSafe();
    const st = M.state();
    const w = want.get(seed + ':' + night);
    compared++;
    const mine = [String(st.size), String(st.px), String(st.py), String(st.ex), String(st.ey),
      String(st.read), String(st.turn), st.won ? '1' : '0', st.caught ? '1' : '0',
      st.lamp.map(b => b ? 1 : 0).join(''),
      st.hist.flat().join(','), st.last.flat().join(',')].join('\t');
    const theirs = w ? w.slice(2).join('\t') : '(missing)';
    if (mine !== theirs) {
      disagreed++;
      if (examples.length < 3) {
        const a = mine.split('\t'), b = theirs.split('\t');
        const at = a.findIndex((v, i) => v !== b[i]);
        examples.push('seed ' + seed + ' night ' + (night + 1) + ': field ' + at
          + '\n  java  ' + String(b[at]).slice(0, 120)
          + '\n  phone ' + String(a[at]).slice(0, 120));
      }
    }
    if (night + 1 < C.nights) M.nextNight();
  }
}

console.log('seeds compared: ' + seeds);
console.log('nights compared: ' + compared);
console.log('disagreed:      ' + disagreed);
for (const e of examples) console.log('\n' + e);
if (disagreed > 0) process.exit(1);
console.log('\nboth builds deal the same house.');
