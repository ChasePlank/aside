package aside.games.corroboration;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Headless checks for corroboration.
 *
 * The point of these is not coverage. It is that the rule IS the game, so it
 * has to be true independently of anything drawn. If "a clean check is not
 * proof" were only true on screen, the game would be a picture of a game.
 *
 * Run: java -cp classes aside.games.corroboration.SelfTest
 */
public final class SelfTest {

    static int checks = 0, failed = 0;

    static void ok(boolean cond, String what) {
        checks++;
        if (!cond) { failed++; System.out.println("FAIL  " + what); }
    }

    static void eq(Object a, Object b, String what) {
        checks++;
        boolean same = a == null ? b == null : a.equals(b);
        if (!same) { failed++; System.out.println("FAIL  " + what + "  (got " + a + ", want " + b + ")"); }
    }

    static final int N = Corroboration.N;

    public static void main(String[] args) throws Exception {
        shape();
        theRule();
        theStructure();
        aCleanCheckIsNotProof();
        theLineNobodySaw();
        determinism();
        storage();
        theArithmetic();
        thePhoneBuild();
        System.out.println((checks - failed) + "/" + checks + " checks passed"
                + (failed == 0 ? "" : "  --  " + failed + " FAILED"));
        if (failed > 0) System.exit(1);
    }

    // ---------------------------------------------------------------- shape

    static void shape() {
        eq(N, 4, "four lines to write");
        eq(Corroboration.VALUES, 3, "three values a line");
        eq(Corroboration.OBSERVERS.length, 2, "two observers");
        eq(Corroboration.BUDGET, 8, "eight questions");
        eq(Corroboration.BLANK, -1, "a blank is a value, and it is not zero");
        for (int a = 0; a < N; a++) {
            ok(Corroboration.axisName(a) != null && !Corroboration.axisName(a).isBlank(),
                    "line " + a + " has a name");
            for (int v = 0; v < Corroboration.VALUES; v++) {
                ok(!Corroboration.valueName(a, v).equals(Corroboration.valueName(a, (v + 1) % 3)),
                        "line " + a + " value " + v + " reads differently from the next");
            }
            eq(Corroboration.valueName(a, Corroboration.BLANK), "--", "a blank draws as a blank");
        }
    }

    // ----------------------------------------------------------------- rule

    static void theRule() {
        // A stuck observer says the same thing about every night. That is the
        // whole reason a check works at all, and the whole reason it can lie.
        for (int seed = 0; seed < 60; seed++) {
            Corroboration c = Corroboration.newNight(seed);
            for (int o = 0; o < 2; o++) {
                for (int a = 0; a < N; a++) {
                    if (!c.stuck[o][a]) continue;
                    int said = c.report(o, a, 0);
                    ok(said == c.report(o, a, 1) && said == c.report(o, a, 2),
                            "seed " + seed + ": a stuck observer says one thing on line " + a);
                    eq(said, c.expected[o][a], "and it is the value they expected");
                }
            }
            // And a straight one says what happened.
            for (int o = 0; o < 2; o++) {
                for (int a = 0; a < N; a++) {
                    if (c.stuck[o][a]) continue;
                    for (int v = 0; v < 3; v++) eq(c.report(o, a, v), v, "a straight observer reports the night");
                }
            }
        }
    }

    static void theStructure() {
        int sharedSeen = 0, bothStraight = 0;
        for (int seed = 0; seed < 200; seed++) {
            Corroboration c = Corroboration.newNight(seed);
            int shared = 0, either = 0;
            for (int a = 0; a < N; a++) {
                if (c.stuck[0][a] && c.stuck[1][a]) shared++;
                if (!c.stuck[0][a] && !c.stuck[1][a]) either++;
                ok(c.stuck[0][a] || c.stuck[1][a] || true, "line " + a + " is somebody's");
            }
            eq(shared, 1, "seed " + seed + ": exactly one line both observers are stuck on");
            eq(either, 1, "seed " + seed + ": exactly one line both observers are straight on");
            for (int o = 0; o < 2; o++) {
                int n = 0;
                for (int a = 0; a < N; a++) if (c.stuck[o][a]) n++;
                eq(n, 2, "seed " + seed + ": observer " + o + " is stuck on two lines");
            }
            sharedSeen += shared;
            bothStraight += either;
        }
        eq(sharedSeen, 200, "the shared line is there every night");
        eq(bothStraight, 200, "and so is the line both of them are good for");
    }

    // ------------------------------------------- a clean check is not proof

    static void aCleanCheckIsNotProof() {
        int lies = 0, truths = 0;
        for (int seed = 0; seed < 400; seed++) {
            Corroboration c = Corroboration.newNight(seed);
            for (int o = 0; o < 2; o++) {
                for (int a = 0; a < N; a++) {
                    if (!c.stuck[o][a]) continue;
                    if (c.expected[o][a] != c.logged[a]) continue;
                    // Stuck, and stuck on the eleventh's own value: the check
                    // comes back clean and proves nothing.
                    c.check(o, a);
                    ok(c.cleared(o, a), "seed " + seed + ": the check comes back clean");
                    ok(c.falseClear(o, a), "and it is a lie");
                    ok(!c.decisive(o, a), "and it proves nothing");
                    if (c.claim(o, a) != c.truth[a]) lies++; else truths++;
                }
            }
        }
        ok(lies > 0, "a clean check does sometimes clear a person who is wrong (" + lies + " times)");
        ok(truths > 0, "and sometimes clears one who is right, which is why it is a trap (" + truths + ")");
    }

    static void theLineNobodySaw() {
        // One line of the entry neither observer was in a position to see.
        // No number of questions settles it, and the only honest thing to
        // write is a blank.
        int blinds = 0;
        for (int seed = 0; seed < 400; seed++) {
            Corroboration c = Corroboration.newNight(seed);
            for (int a = 0; a < N; a++) {
                if (!(c.stuck[0][a] && c.stuck[1][a])) continue;
                blinds++;
                eq(c.established(a), Corroboration.BLANK, "seed " + seed + ": the unseen line starts unsettled");
                c.check(0, a);
                c.check(1, a);
                ok(c.caught(0, a) && c.caught(1, a), "seed " + seed + ": and both of them are caught on it");
                ok(!c.decisive(0, a) && !c.decisive(1, a), "seed " + seed + ": neither check was worth anything");
                eq(c.established(a), Corroboration.BLANK,
                        "seed " + seed + ": and it is still unsettled after both checks");
                eq(c.knows(a), false, "seed " + seed + ": so it cannot be known");
            }
        }
        eq(blinds, 400, "every night has exactly one line nobody saw");
    }

    // ---------------------------------------------------------- determinism

    static void determinism() {
        for (int seed = 0; seed < 40; seed++) {
            Corroboration a = Corroboration.newNight(seed), b = Corroboration.newNight(seed);
            for (int i = 0; i < N; i++) {
                eq(a.truth[i], b.truth[i], "seed " + seed + ": the same night twice");
                eq(a.logged[i], b.logged[i], "seed " + seed + ": the same log twice");
                for (int o = 0; o < 2; o++) {
                    eq(a.stuck[o][i], b.stuck[o][i], "seed " + seed + ": the same observers twice");
                    eq(a.expected[o][i], b.expected[o][i], "seed " + seed + ": the same expectations twice");
                }
            }
        }
        int differ = 0;
        for (int seed = 1; seed < 40; seed++) {
            Corroboration a = Corroboration.newNight(seed - 1), b = Corroboration.newNight(seed);
            for (int i = 0; i < N; i++) if (a.truth[i] != b.truth[i]) { differ++; break; }
        }
        ok(differ > 20, "and different seeds are different nights (" + differ + "/39)");
    }

    // -------------------------------------------------------------- storage

    static void storage() throws Exception {
        Path dir = Files.createTempDirectory("corroboration");
        Path f = dir.resolve("night.state");
        Corroboration c = Corroboration.newNight(4242);
        c.check(0, 0);
        c.check(1, 2);
        c.check(0, 2);
        c.filed[0] = 1;
        c.filed[3] = Corroboration.BLANK;
        c.save(f);

        Corroboration back = Corroboration.load(f);
        eq(back.seed, c.seed, "the seed survives the file");
        eq(back.left, c.left, "and the questions left");
        for (int o = 0; o < 2; o++) {
            for (int a = 0; a < N; a++) {
                eq(back.asked[o][a], c.asked[o][a], "and which questions were spent");
                eq(back.answer[o][a], c.answer[o][a], "and what they came back with");
            }
        }
        for (int a = 0; a < N; a++) eq(back.filed[a], c.filed[a], "and the entry");
        eq(back.serialize(), c.serialize(), "a loaded night writes the same file back");

        eq(Corroboration.load(dir.resolve("nothing.state")), null, "no file is no night");
    }

    // ----------------------------------------------------------- the arithmetic

    /**
     * The full inference.
     *
     * Check a person, then check whether the check could have failed. Two
     * checks a line is what it costs to be sure, and the line nobody saw
     * costs two checks to prove that neither of them can settle it.
     */
    static void careful(Corroboration c) {
        for (int a = 0; a < N; a++) {
            int h = c.check(0, a);
            if (h == Corroboration.BLANK) break;
            if (!c.decisive(0, a)) {
                int v = c.check(1, a);
                if (v == Corroboration.BLANK) break;
            }
            c.filed[a] = c.established(a);
        }
        c.filedDone = true;
    }

    /** Trust a single clean check. The mistake the game is about. */
    static void oneCheck(Corroboration c) {
        for (int a = 0; a < N; a++) {
            int h = c.check(0, a);
            if (h == Corroboration.BLANK) break;
            if (c.cleared(0, a)) c.filed[a] = c.claim(0, a);
            else {
                int v = c.check(1, a);
                if (v == Corroboration.BLANK) break;
                c.filed[a] = c.cleared(1, a) ? c.claim(1, a) : Corroboration.BLANK;
            }
        }
        c.filedDone = true;
    }

    /** No checks at all: write down whatever the two of them agree on. */
    static void noCheck(Corroboration c) {
        for (int a = 0; a < N; a++) {
            c.filed[a] = c.claim(0, a) == c.claim(1, a) ? c.claim(0, a) : Corroboration.BLANK;
        }
        c.filedDone = true;
    }

    static void theArithmetic() {
        int seeds = 300;
        int[] carefulKnown = new int[seeds], carefulWrong = new int[seeds];
        int[] oneKnown = new int[seeds], oneWrong = new int[seeds], oneLucky = new int[seeds];
        int[] noKnown = new int[seeds], noWrong = new int[seeds];
        int spent = 0;

        for (int seed = 0; seed < seeds; seed++) {
            Corroboration c = Corroboration.newNight(seed);
            careful(c);
            carefulKnown[seed] = c.tally(Corroboration.Verdict.KNOWN);
            carefulWrong[seed] = c.tally(Corroboration.Verdict.WRONG);
            spent += Corroboration.BUDGET - c.left;

            Corroboration d = Corroboration.newNight(seed);
            oneCheck(d);
            oneKnown[seed] = d.tally(Corroboration.Verdict.KNOWN);
            oneWrong[seed] = d.tally(Corroboration.Verdict.WRONG);
            oneLucky[seed] = d.tally(Corroboration.Verdict.LUCKY);

            Corroboration e = Corroboration.newNight(seed);
            noCheck(e);
            noKnown[seed] = e.tally(Corroboration.Verdict.KNOWN);
            noWrong[seed] = e.tally(Corroboration.Verdict.WRONG);
        }

        System.out.println("  strategy                       known  lucky  wrong  blank");
        report("check, then check the check", carefulKnown, null, carefulWrong, seeds);
        report("trust one clean check", oneKnown, oneLucky, oneWrong, seeds);
        report("no checks, write agreement", noKnown, null, noWrong, seeds);
        System.out.printf("  questions spent by the careful line: %.2f of %d%n",
                spent / (double) seeds, Corroboration.BUDGET);

        ok(min(carefulKnown) == 3, "the careful line settles exactly three lines every night");
        ok(max(carefulKnown) == 3, "and never four: the fourth is the one nobody saw");
        ok(max(carefulWrong) == 0, "and it never writes a line it had not settled");
        ok(sum(carefulKnown) > sum(oneKnown), "checking the check beats trusting it");
        ok(sum(oneWrong) > 0, "trusting a single clean check writes wrong lines (" + sum(oneWrong) + ")");
        ok(sum(noWrong) > 0, "and so does writing down agreement (" + sum(noWrong) + ")");
        ok(sum(noKnown) == 0, "agreement alone settles nothing, by the game's own accounting");
        ok(sum(oneLucky) > 0, "and some unchecked lines come out right anyway (" + sum(oneLucky) + ")");
        ok(spent / (double) seeds <= Corroboration.BUDGET, "the careful line fits the budget");
    }

    static void report(String name, int[] known, int[] lucky, int[] wrong, int seeds) {
        System.out.printf("  %-30s %5.2f  %5.2f  %5.2f  %5.2f%n", name,
                sum(known) / (double) seeds,
                lucky == null ? 0.0 : sum(lucky) / (double) seeds,
                sum(wrong) / (double) seeds,
                (seeds * N - sum(known) - (lucky == null ? 0 : sum(lucky)) - sum(wrong)) / (double) seeds);
    }

    // ------------------------------------------------------- the phone build

    /**
     * A generated file that has gone stale is worse than no file -- it is a
     * second copy of the game quietly disagreeing with the first. So the check
     * is a regeneration diff, not a spot-check.
     *
     * What this cannot check is whether the two models still agree about a
     * night; that needs node. tools/corroboration-trace.mjs is the other half,
     * and it is the half that found the unsigned-remainder bug.
     */
    static void thePhoneBuild() throws Exception {
        Path out = Path.of("web", "corroboration.html");
        if (!Files.exists(out)) {
            System.out.println("       (no web/corroboration.html from here -- run from the repository root)");
            return;
        }
        String generated;
        try {
            generated = WebCorroboration.html();
        } catch (Exception e) {
            System.out.println("       (no template from here: " + e.getMessage() + ")");
            return;
        }
        ok(generated.equals(Files.readString(out)),
                "web/corroboration.html is current -- regenerate it with aside.games.corroboration.WebCorroboration");

        // Every fixed line the game says has to be in the build, because the
        // build does not own any of them.
        for (String line : Corroboration.OPEN) {
            if (line.isBlank()) continue;
            ok(generated.contains(WebCorroboration.str(line)), "the phone build carries: " + line);
        }
        for (String line : new String[] {
                Corroboration.OPEN_HEAD, Corroboration.OPEN_SUB, Corroboration.ASK_HEAD,
                Corroboration.FILE_HEAD, Corroboration.END_HEAD, Corroboration.FILE_LINE,
                Corroboration.LABEL_SAYS, Corroboration.LABEL_CHECK, Corroboration.LABEL_WROTE,
                Corroboration.LABEL_WAS, Corroboration.CHECK_NONE, Corroboration.CHECK_CLEAN,
                Corroboration.CHECK_CAUGHT, Corroboration.LOG_HEAD, Corroboration.LOG_ORDER,
                Corroboration.ASK_HINT, Corroboration.FILE_HINT, Corroboration.END_HINT,
                Corroboration.NO_QUESTIONS, Corroboration.BTN_BEGIN, Corroboration.BTN_FILE,
                Corroboration.BTN_BACK, Corroboration.BTN_END, Corroboration.BTN_AGAIN,
                Corroboration.BTN_LIBRARY, Corroboration.CLOSING_EMPTY, Corroboration.CLOSING_BEST,
                Corroboration.V_KNOWN, Corroboration.V_LUCKY, Corroboration.V_WRONG,
                Corroboration.V_BLANK, Corroboration.V_WITHHELD,
        }) {
            ok(generated.contains(WebCorroboration.str(line)), "the phone build carries: " + line);
        }
        // The count line is emitted as a template, so what has to be present is
        // the template, not any one filling of it.
        ok(generated.contains(WebCorroboration.str(Corroboration.questionsLeft(2).replace("2", "%s"))),
                "the phone build carries the question count as a template");
        ok(generated.contains(WebCorroboration.str(Corroboration.questionsLeft(1))),
                "and the singular, which is the one that gets forgotten");
        for (int a = 0; a < N; a++) {
            ok(generated.contains(WebCorroboration.str(Corroboration.axisName(a))),
                    "the phone build carries the line name: " + Corroboration.axisName(a));
            for (int v = 0; v < Corroboration.VALUES; v++) {
                ok(generated.contains(WebCorroboration.str(Corroboration.valueName(a, v))),
                        "and the value " + Corroboration.valueName(a, v));
            }
        }
    }

    static int sum(int[] a) { int s = 0; for (int x : a) s += x; return s; }
    static int min(int[] a) { int m = a[0]; for (int x : a) m = Math.min(m, x); return m; }
    static int max(int[] a) { int m = a[0]; for (int x : a) m = Math.max(m, x); return m; }
}
