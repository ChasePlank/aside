// Hold the phone's deal against the desktop's, seed by seed.
//
// The phone build runs the deal itself -- it has to, because the record on the
// wall is what the player reads and it is twelve draws from a cumulative
// table, not a table. A ported model is the one thing that can be wrong on
// both sides at once without either side noticing, and a cumulative walk is
// exactly the arithmetic that agrees for a hundred seeds and then disagrees on
// the hundred and first.
//
// The model block is delimited in the build (//__MODEL_BEGIN__ .. __END__) so
// it can be lifted out and run here without a DOM. If the build ever stops
// marking its model, this stops working, which is the point.
//
//   node tools/interval-trace.mjs [web/interval.html] [seeds]

import fs from 'node:fs';
import vm from 'node:vm';
import { execFileSync } from 'node:child_process';

const file = process.argv[2] || 'web/interval.html';
const seeds = Number(process.argv[3] || 200);
const html = fs.readFileSync(file, 'utf8');

const contentMatch = html.match(/const C = ([\s\S]*?);\n\n\/\/__MODEL_BEGIN__/);
if (!contentMatch) { console.error('no content in ' + file); process.exit(1); }
const C = JSON.parse(contentMatch[1]);

const modelMatch = html.match(/\/\/__MODEL_BEGIN__([\s\S]*?)\/\/__MODEL_END__/);
if (!modelMatch) { console.error('no model block in ' + file); process.exit(1); }

const ctx = { C, console };
vm.createContext(ctx);
vm.runInContext(modelMatch[1] + '\nthis.deal = deal;', ctx);
const deal = ctx.deal;

// The desktop's answer, straight from the model.
const java = execFileSync('java', ['-cp', 'classes', 'aside.games.interval.Trace', String(seeds)],
  { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });

const want = new Map();
for (const line of java.split('\n')) {
  if (!line) continue;
  const parts = line.split('\t');
  want.set(parts[0], { arrival: Number(parts[1]), record: parts.slice(2).map(Number) });
}

let compared = 0, disagreed = 0;
const examples = [];
for (let seed = 0; seed < seeds; seed++) {
  const d = deal(seed);
  const w = want.get(String(seed));
  compared++;
  const same = w && w.arrival === d.arrival
    && w.record.length === d.record.length
    && w.record.every((r, i) => r === d.record[i]);
  if (!same) {
    disagreed++;
    if (examples.length < 5) examples.push('seed ' + seed
      + '\n  java  ship ' + (w ? w.arrival : '(missing)') + '  record ' + (w ? w.record.join(',') : '')
      + '\n  phone ship ' + d.arrival + '  record ' + d.record.join(','));
  }
}

console.log('seeds compared: ' + seeds);
console.log('draws compared: ' + (compared * (C.record + 1)));
console.log('disagreed:      ' + disagreed);
for (const e of examples) console.log('\n' + e);
if (disagreed > 0) process.exit(1);
console.log('\nboth builds deal the same season.');
