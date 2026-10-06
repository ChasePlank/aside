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

/**
 * A JDK, NOT A JRE - and this is the twin of the same bug in tools/find-java.sh, found the same afternoon.
 *
 * The order used to be "whatever `java` is on PATH, and only look for a real JDK if nothing is on PATH at all".
 * `out/` is compiled by the JDK on this machine (class file version 71, Java 27 in the sandbox) and a bare system
 * runtime cannot load those classes, so the moment ANY thing put a JRE on PATH - installing maven for an
 * unrelated verification pulled in openjdk-17 - these three trace checks died with
 *
 *   UnsupportedClassVersionError: ... class file version 71.0, this Runtime only recognizes up to 61.0
 *
 * while a perfectly good JDK sat at /root/jdk-27+35. A system JRE on PATH is the NORMAL state of a normal machine,
 * which is what makes "check that it is on PATH" the wrong question. The right one is "is it a JDK", because the
 * classes it is being asked to run were built by one.
 *
 * Kept deliberately in step with find-java.sh: two implementations of one lookup is already one too many, and
 * having them disagree is worse than either being wrong.
 */
export function javaBin() {
  if (process.env.JAVA) return process.env.JAVA;
  const isJdk = (p) => existsSync(p) && existsSync(dirname(p) + '/javac');
  for (const c of ['/root/jdk-27+35/bin/java', '/usr/lib/jvm/default/bin/java']) {
    if (isJdk(c)) return c;
  }
  const globs = ['/root', process.env.HOME || '/root', '/usr/lib/jvm'];
  for (const g of globs) {
    for (const v of [27, 21, 17]) {
      for (const p of [`${g}/jdk-${v}/bin/java`, `${g}/jdk-${v}+35/bin/java`, `${g}/java-${v}-openjdk/bin/java`]) {
        if (isJdk(p)) return p;
      }
    }
  }
  // only now: a runtime with no compiler beside it is still better than nothing, and anything that needs to
  // COMPILE will say so itself
  try {
    execFileSync('java', ['-version'], { stdio: 'ignore' });
    return 'java';
  } catch { /* not on PATH either */ }
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
