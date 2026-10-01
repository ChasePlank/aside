#!/usr/bin/env node
/*
 * Run the salvage bay's sweep against the PHONE's engine and print the
 * table, so it can be compared with the desktop's.
 *
 *   node tools/fnaf6-sweep.mjs
 *
 * WHY THIS EXISTS. web/fnaf6.html carries a transliteration of
 * aside.games.fnaf6.engine.Salvage, because a night is a clock and a budget
 * and a hidden coin flip and the phone has to step it itself. A
 * transliteration is a second copy of the rules, and a second copy of the
 * rules is a second copy that drifts -- silently, because both builds keep
 * working, they just stop being the same game. Nothing in the Java suite
 * can see that: SelfTest can check that the page is current and that its
 * art is there, and neither of those notices that the phone's unit now
 * advances half a second early.
 *
 * So the check is a measurement rather than a diff. This runs the same
 * policies over the same seeds through the phone's engine and prints the
 * same table aside.games.fnaf6.engine.Bot prints for the desktop's, and
 * SelfTest runs both and compares the strings. Two engines that disagree
 * about the week are two engines that disagree about the game.
 *
 * IT READS THE SHIPPED PAGE, not the template. The engine is cut out of
 * web/fnaf6.html between its own markers, so what is measured is what a
 * phone would run -- including anything the generator did to it on the way
 * through.
 *
 * The policies are transcribed from Bot.java, and the transcription is
 * deliberately literal: the point is to measure the ENGINE, so the player
 * has to be the same player on both sides. If this file's policies drift
 * from Bot.java the comparison fails loudly, which is the correct failure
 * -- a sweep that disagrees because the bot changed is a sweep that is
 * telling you to look at the bot.
 */

import fs from "node:fs";
import path from "node:path";

const ROOT = path.dirname(path.dirname(new URL(import.meta.url).pathname));
const PAGE = path.join(ROOT, "web", "fnaf6.html");
const SEEDS = 200;
const NIGHTS = 5;

const html = fs.readFileSync(PAGE, "utf8");
const open = html.indexOf("<script>");
const close = html.lastIndexOf("</script>");
if (open < 0 || close < 0) throw new Error("no <script> in " + PAGE);

const script = html.slice(open + "<script>".length, close);
const SOUND = "// --------------------------------------------------------------- the sound";
const cut = script.indexOf(SOUND);
if (cut < 0) throw new Error("the page has no sound section to cut the engine at");
const engine = script.slice(0, cut);

const bot = `
const LOOK = 1.20, LOOK_STARE = 1.60, LISTEN_WINDOW = 0.35, PATROL_EVERY = 24.0;
const PRO_EVERY = 18.0, PRO_SLOW = 26.0, PRO_IDLE = 40.0, SAME_BEFORE_IDLE = 2;
const RESTRAINT = 0.60, HOLD_OFF = 0.92;

function Bot(policy) {
  this.policy = policy; this.lastDrags = 0; this.count = 0; this.lookFor = 0;
  this.sinceLook = 0; this.countLooked = -1; this.lastSeen = -1; this.sameSeen = 0;
  this.looking = false;
}
Bot.prototype.step = function (s, dt) {
  if (s.status !== 'PLAYING') return;
  if (s.drags > this.lastDrags) { this.count += s.drags - this.lastDrags; this.lastDrags = s.drags; }
  if (s.revealed() && s.pose >= C.shockMin) { s.shock(); return; }
  switch (this.policy) {
    case 'IDLE': break;
    case 'LAMP_ON': if (!s.lit) s.toggleLamp(); break;
    case 'PANIC': if (s.time > 5.0 && s.shocks > 0) s.shock(); break;
    default: this.drive(s, dt);
  }
};
Bot.prototype.drive = function (s, dt) {
  if (s.lit) {
    this.lookFor += dt;
    const hold = this.policy === 'LISTEN' ? LOOK_STARE : LOOK;
    if (this.lookFor >= hold) {
      s.toggleLamp(); this.lookFor = 0; this.sinceLook = 0; this.looking = false;
    }
    return;
  }
  this.sinceLook += dt; this.lookFor = 0; this.looking = false;
  let start;
  if (this.policy === 'LISTEN') {
    start = s.dragAge <= LISTEN_WINDOW && this.sinceLook >= 1.0;
  } else if (this.policy === 'PATROL') {
    start = this.sinceLook >= PATROL_EVERY;
  } else {
    if (s.agitation >= HOLD_OFF) {
      start = false;
    } else {
      const dragDue = s.dragAge <= LISTEN_WINDOW && this.sinceLook >= 1.0;
      const every = this.sameSeen >= SAME_BEFORE_IDLE ? PRO_IDLE
        : s.agitation > RESTRAINT ? PRO_SLOW : PRO_EVERY;
      const hedgeDue = this.count < C.shockMin - 1
        && s.dragAge >= every * 0.6 && this.sinceLook >= every;
      start = dragDue || hedgeDue;
    }
  }
  if (start) { s.toggleLamp(); this.lookFor = 0; this.looking = true; }
};
Bot.prototype.correct = function (s) {
  if (!s.revealed()) return;
  this.count = s.pose;
  if (s.pose === this.lastSeen) this.sameSeen++;
  else { this.sameSeen = 0; this.lastSeen = s.pose; }
};

function play(policy, night, seed) {
  const s = new Salvage(night, seed);
  const b = new Bot(policy);
  const dt = 1.0 / 60.0;
  for (let i = 0; i < 60 * 400 && s.status === 'PLAYING'; i++) {
    s.update(dt); b.correct(s); b.step(s, dt);
  }
  return s.status;
}

function survival(policy, night, seeds) {
  let ok = 0;
  for (let i = 0; i < seeds; i++) {
    const st = play(policy, night, BigInt(1000 * night + i));
    if (st === 'SURVIVED' || st === 'DESTROYED') ok++;
  }
  return ok / seeds;
}

return { survival: survival };
`;

const { survival } = new Function(engine + bot)();

const lines = [];
for (const policy of ["IDLE", "PATROL", "LISTEN", "PRO"]) {
  const row = [];
  for (let n = 1; n <= NIGHTS; n++) {
    row.push(Math.round(survival(policy, n, SEEDS) * 100) + "%");
  }
  lines.push(policy + ": " + row.join(" "));
}
process.stdout.write(lines.join("\n") + "\n");
