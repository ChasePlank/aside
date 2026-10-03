#!/usr/bin/env node
//
// Load every phone build in a real browser and check that it BOOTS.
//
//   npm install          # once - puppeteer, a dev dependency
//   node tools/check-web.mjs
//
// WHY THIS EXISTS. web/ holds 21 builds. The gate checked that each one is *current* - regenerating it produces
// the same bytes - and nothing ever OPENED one. A file can be perfectly up to date and still be broken: on
// 2026-10-03 a story title containing the literal text `</script>` closed the export's script block early and
// the build came out as a page that does nothing. Freshness cannot catch that. Loading it can.
//
// THE SIGNAL IS THE BROWSER'S OWN ERROR CHANNEL, and getting there took three attempts worth recording, because
// the first two were checks that passed on the exact bug they were written for:
//
//   1. "the page renders more than N characters of text" - a broken build renders the LEAKED SCRIPT as text, so
//      it renders MORE than a healthy one (19,401 against 11,818). It passed.
//   2. A token count over the rendered text (looking for `function`, `=>`, `const`) - 64 healthy against 111
//      broken. Real, but too close to be a gate, and it would fire on a game whose prose mentions code.
//   3. What actually works: `pageerror`. One error on the broken build ("Invalid or unexpected token"), zero on
//      the healthy one, and it catches a script that throws at runtime as well as one that will not parse.
//
// Rendered output is a symptom. The error channel is the diagnosis.
import { readdirSync, mkdirSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const webDir = resolve(root, 'web');

let puppeteer;
try {
  puppeteer = (await import('puppeteer')).default;
} catch {
  console.error('puppeteer is not installed. Run:  npm install');
  process.exit(2);
}

const files = readdirSync(webDir).filter(f => f.endsWith('.html')).sort();
if (files.length === 0) {
  console.error('no builds found in web/');
  process.exit(2);
}

// Frames are kept for the same reason CheckGames keeps them: when this fails the next question is "what did it
// look like", and a run that threw the pictures away makes that a second job. They are also the only way to
// LOOK at the phone builds - these are what the public plays, and nothing had ever rendered one at phone size.
const shots = process.env.SHOTDIR || '/root/downloads/webshots';
mkdirSync(shots, { recursive: true });

const browser = await puppeteer.launch({ args: ['--no-sandbox'] });
let pass = 0;
const failed = [];

for (const f of files) {
  const page = await browser.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(String(e.message).split('\n')[0]));
  page.on('console', m => { if (m.type() === 'error') errors.push('console: ' + m.text().slice(0, 100)); });
  try {
    await page.goto('file://' + resolve(webDir, f), { waitUntil: 'load', timeout: 30000 });
    await new Promise(r => setTimeout(r, 1500));   // let the first frame run
  } catch (e) {
    errors.push('did not load: ' + String(e.message).split('\n')[0]);
  }
  try {
    // Phone-shaped, which is the point: the layout is responsive and a desktop-sized frame would show a version
    // of the page nobody sees.
    await page.setViewport({ width: 390, height: 700 });
    await new Promise(r => setTimeout(r, 400));
    await page.screenshot({ path: `${shots}/${f.replace('.html', '')}.png` });
  } catch { /* a screenshot that fails must not turn a booted build into a failure */ }
  await page.close();

  if (errors.length) {
    console.log(`  ${f.padEnd(20)} FAIL   ${errors[0]}`);
    failed.push(f);
  } else {
    console.log(`  ${f.padEnd(20)} ok     boots with no page errors`);
    pass++;
  }
}

await browser.close();
console.log();
console.log(`=== ${pass} web build(s) booted, ${failed.length} did not ===`);
if (failed.length) {
  console.log('did not boot: ' + failed.join(' '));
  process.exit(1);
}
