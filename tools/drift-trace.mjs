// Hold the phone's deal against the desktop's, seed by seed.
//
// The phone build runs the deal itself -- it has to, because the night is not
// a table -- and a ported model is the one thing that can be wrong on both
// sides at once without either side noticing. This drives both over the same
// seeds and compares every line of every copy, including the text.
//
// The model block is delimited in the build (//__MODEL_BEGIN__ .. __END__) so
// it can be lifted out and run here without a DOM. If the build ever stops
// marking its model, this stops working, which is the point.
//
//   node tools/drift-trace.mjs [web/drift.html] [seeds]

import fs from 'node:fs';
import vm from 'node:vm';
import { execFileSync } from 'node:child_process';
import { javaBin, classesDir } from './java.mjs';

const file = process.argv[2] || 'web/drift.html';
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
vm.runInContext(modelMatch[1] + '\nthis.deal = deal;', ctx);
const deal = ctx.deal;

// The desktop's answer, straight from the model.
const java = execFileSync(javaBin(), ['-cp', classesDir(), 'aside.games.drift.Trace', String(seeds)],
  { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });

const want = new Map();
for (const line of java.split('\n')) {
  if (!line) continue;
  const [seed, index, kind, ...rest] = line.split('\t');
  want.set(seed + ':' + index, { kind, now: rest.join('\t') });
}

let compared = 0, disagreed = 0;
const examples = [];
for (let seed = 0; seed < seeds; seed++) {
  const rows = deal(seed);
  if (rows.length !== C.lines) { console.error('seed ' + seed + ' dealt ' + rows.length + ' lines'); process.exit(1); }
  for (const r of rows) {
    const w = want.get(seed + ':' + r.index);
    compared++;
    if (!w || w.kind !== r.kind || w.now !== r.now) {
      disagreed++;
      if (examples.length < 5) examples.push('seed ' + seed + ' line ' + (r.index + 1)
        + '\n  java  ' + (w ? w.kind + '  ' + w.now : '(missing)')
        + '\n  phone ' + r.kind + '  ' + r.now);
    }
  }
}

console.log('seeds compared: ' + seeds);
console.log('lines compared: ' + compared);
console.log('disagreed:      ' + disagreed);
for (const e of examples) console.log('\n' + e);
if (disagreed > 0) process.exit(1);
console.log('\nboth builds deal the same copy.');
