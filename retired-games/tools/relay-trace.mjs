// Hold the phone's rule against the desktop's, message by message.
//
// The phone build computes what a message is worth itself -- it has to, because
// the report depends on it -- and a ported rule is the one thing that can be
// wrong on both sides at once without either side noticing. This drives both
// over every message and every option and compares the counts, the worth, the
// total, the kept and lost labels, and every line the report can say at every
// score.
//
// The model block is delimited in the build (//__MODEL_BEGIN__ .. __END__) so
// it can be lifted out and run here without a DOM. If the build ever stops
// marking its model, this stops working, which is the point.
//
//   node tools/relay-trace.mjs [web/relay.html]

import fs from 'node:fs';
import vm from 'node:vm';
import { execFileSync } from 'node:child_process';

const file = process.argv[2] || 'web/relay.html';
const html = fs.readFileSync(file, 'utf8');

const contentMatch = html.match(/const C = ([\s\S]*?);\n\n\/\/__MODEL_BEGIN__/);
if (!contentMatch) { console.error('no content in ' + file); process.exit(1); }
const C = JSON.parse(contentMatch[1]);

const modelMatch = html.match(/\/\/__MODEL_BEGIN__([\s\S]*?)\/\/__MODEL_END__/);
if (!modelMatch) { console.error('no model block in ' + file); process.exit(1); }

// The phone's model reads `state`, so give it one that is not playing.
const ctx = { C, state: { choices: [] }, console };
vm.createContext(ctx);
vm.runInContext(modelMatch[1] + '\nthis.worth = worth; this.total = total;'
  + 'this.kept = kept; this.lost = lost;', ctx);
const { worth, total, kept, lost } = ctx;

// The desktop's answer, straight from the model.
const java = execFileSync('java', ['-cp', 'classes', 'aside.games.relay.Trace'],
  { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });

const want = new Map();
for (const line of java.split('\n')) {
  if (!line) continue;
  const parts = line.split('\t');
  want.set(parts[0] + ':' + parts[1], parts.slice(2));
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

// The option rows, in the order the trace printed them.
const optionRows = [];
for (const line of java.split('\n')) {
  if (!line.startsWith('opt\t')) continue;
  const p = line.split('\t');
  optionRows.push({ m: Number(p[1]), o: Number(p[2]), rest: p.slice(3) });
}
for (const row of optionRows) {
  const opt = C.messages[row.m].options[row.o];
  const mine = [
    String(opt.facts.length), String(opt.points.length),
    String(worth(row.m, row.o)), String(total(row.m, row.o)),
    kept(row.m, row.o).join(' | '), lost(row.m, row.o).join(' | '),
  ];
  hold('message ' + (row.m + 1) + ' option ' + (row.o + 1), mine.join('\t'), row.rest.join('\t'));
}

for (let s = 0; s <= C.max; s++) {
  hold('worth line at ' + s, C.worthLine[s], (want.get('worth:' + s) || [])[0]);
  hold('closing at ' + s, C.closing[s], (want.get('closing:' + s) || [])[0]);
}
for (let k = 0; k <= C.messages.length; k++) {
  hold('breaks line at ' + k, C.breaksLine[k], (want.get('breaks:' + k) || [])[0]);
}
for (let w = 0; w <= 2; w++) {
  hold('worth word at ' + w, C.worthWord[w], (want.get('word:' + w) || [])[0]);
}

console.log('messages compared: ' + C.messages.length);
console.log('things compared:   ' + compared);
console.log('disagreed:         ' + disagreed);
for (const e of examples) console.log('\n' + e);
if (disagreed > 0) process.exit(1);
console.log('\nboth builds carry the same night.');
