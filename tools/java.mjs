// Find the JDK and the compiled classes, for the tools that shell out to Java.
//
// THREE TRACES CALLED `execFileSync('java', ...)` AND `-cp classes`, and both assumptions are wrong on the machine
// these were written on: `java` is not on PATH (the toolchain lives under /root) and the build goes to `out/`.
// Every one of them died with `spawnSync java ENOENT`, and they were wired into run-suites.sh the same day, so
// the gate said so immediately - which is the only reason this was found at all.
//
// This is the third script in this repository to hardcode a toolchain that is not where it assumed. `mutate.sh`
// and `check-sheets.sh` were fixed one at a time; the shell ones still each carry their own lookup, and the next
// person to add a tool should use this shape rather than a fourth copy.
import { existsSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');

export function javaBin() {
  if (process.env.JAVA) return process.env.JAVA;
  try {
    execFileSync('java', ['-version'], { stdio: 'ignore' });
    return 'java';
  } catch { /* not on PATH */ }
  for (const c of ['/root/jdk-27+35/bin/java', '/usr/lib/jvm/default/bin/java']) {
    if (existsSync(c)) return c;
  }
  const globs = ['/root', process.env.HOME || '/root', '/usr/lib/jvm'];
  for (const g of globs) {
    for (const v of [27, 21, 17]) {
      for (const p of [`${g}/jdk-${v}/bin/java`, `${g}/jdk-${v}+35/bin/java`, `${g}/java-${v}-openjdk/bin/java`]) {
        if (existsSync(p)) return p;
      }
    }
  }
  console.error('no java found - set JAVA=/path/to/bin/java');
  process.exit(2);
}

/** Where the compiled classes are: `out/`, `classes/`, or CLASSES. */
export function classesDir() {
  if (process.env.CLASSES) return process.env.CLASSES;
  for (const d of ['out', 'classes']) {
    if (existsSync(resolve(ROOT, d))) return d;
  }
  console.error('no build found - run the build first (out/ or classes/), or set CLASSES=');
  process.exit(2);
}
