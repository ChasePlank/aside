#!/usr/bin/env node
//
// Put every phone build's frame on one page, so the whole shelf can be looked at at once.
//
//   npm install
//   node tools/check-web.mjs          # writes a frame per build
//   node tools/contact-sheet-web.mjs  # puts them together
//
// WHY. check-web.mjs answers "did it boot" - a yes/no about each build. That is not the same question as "does it
// look right", and when the artifact is a picture the second question is cheap to answer and the first is not a
// substitute. These are the builds the public plays, and until this existed nobody had rendered one at phone
// size. The frames are phone-shaped on purpose: the layout is responsive and a desktop-sized frame shows a
// version of the page nobody sees.
import { readdirSync, readFileSync, writeFileSync, existsSync } from 'node:fs';
import puppeteer from 'puppeteer';

const dir = process.env.SHOTDIR || '/root/downloads/webshots';
const out = process.env.SHEET || '/root/downloads/web-contact-sheet.png';
if (!existsSync(dir)) {
  console.error(`no frames in ${dir} - run tools/check-web.mjs first`);
  process.exit(2);
}
const files = readdirSync(dir).filter(f => f.endsWith('.png')).sort();
if (!files.length) { console.error(`no frames in ${dir}`); process.exit(2); }

const cells = files.map(f => {
  const b64 = readFileSync(`${dir}/${f}`).toString('base64');
  return `<figure><img src="data:image/png;base64,${b64}"><figcaption>${f.replace('.png', '')}</figcaption></figure>`;
}).join('');
writeFileSync('/tmp/aside-web-sheet.html', `<!doctype html><meta charset="utf-8"><style>
body{background:#0b0b10;margin:0;padding:14px;font:13px system-ui,sans-serif}
.grid{display:grid;grid-template-columns:repeat(7,1fr);gap:10px}
figure{margin:0}img{width:100%;display:block;border:1px solid #2a2a38;border-radius:4px}
figcaption{color:#9a9ab0;padding-top:4px;text-align:center}
h1{color:#e8e8ef;font-size:17px;margin:4px 0 12px}
</style><h1>the phone shelf, one frame each</h1><div class="grid">${cells}</div>`);

const b = await puppeteer.launch({ args: ['--no-sandbox'] });
const p = await b.newPage();
await p.setViewport({ width: 1700, height: 900 });
await p.goto('file:///tmp/aside-web-sheet.html', { waitUntil: 'load' });
await new Promise(r => setTimeout(r, 1500));
await p.screenshot({ path: out, fullPage: true });
await b.close();
console.log(`${files.length} frame(s) -> ${out}`);
