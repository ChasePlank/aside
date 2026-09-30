package aside.games.drift;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Headless checks for drift.
 *
 * The point of these is not coverage. It is that the answer key and the prose
 * are two different things written by the same hand, and the only way they
 * stay honest is if something checks them against each other. A rewording
 * that quietly changes a number is not a bug the game reports -- it is a bug
 * the game *is*. So every variant of every line declares the facts it states,
 * and this suite holds the declaration to the classification:
 *
 *   - a rewording must state exactly the facts its line states;
 *   - a change must state at least one of them differently;
 *   - and every row the generator deals must agree with its own kind.
 *
 * The last one is the end-to-end check. It is the one that would catch a
 * variant filed under `same` that is really a change, which is the failure
 * that would make the game unwinnable and look like nothing at all.
 *
 * Run: java -cp classes aside.games.drift.SelfTest
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

    public static void main(String[] args) throws Exception {
        System.out.println("=== drift self-test ===\n");

        System.out.println("--- the corpus ---");
        eq(Drift.LINES, Drift.NIGHTS * Drift.PER_NIGHT, "the log is four nights of four lines");
        eq(Drift.LOG.size(), Drift.LINES, "and every line is written down");
        eq(Drift.CHANGED + Drift.REWORDED <= Drift.LINES, true, "there is room for both kinds");

        int[] perNight = new int[Drift.NIGHTS + 1];
        Set<String> allTexts = new HashSet<>();
        for (int i = 0; i < Drift.LINES; i++) {
            Drift.Line ln = Drift.LOG.get(i);
            String where = "line " + (i + 1);
            ok(ln.night() >= 1 && ln.night() <= Drift.NIGHTS, where + " is on a night");
            perNight[ln.night()]++;
            ok(!ln.same().isEmpty(), where + " has at least one rewording");
            ok(!ln.changed().isEmpty(), where + " has at least one change");

            Map<String, String> base = Drift.facts(ln.base().facts());
            ok(!base.isEmpty(), where + " declares its facts");

            Set<String> texts = new HashSet<>();
            texts.add(ln.base().text());
            for (Drift.Variant v : ln.same()) {
                ok(!v.text().equals(ln.base().text()),
                        where + ": a rewording is not the line again");
                ok(texts.add(v.text()), where + ": no two variants read the same");
                ok(Drift.facts(v.facts()).keySet().equals(base.keySet()),
                        where + ": a rewording states the same facts, not more or fewer");
                eq(Drift.facts(v.facts()), base, where + ": a rewording states them the same way");
            }
            for (Drift.Variant v : ln.changed()) {
                ok(!v.text().equals(ln.base().text()),
                        where + ": a change is not the line again");
                ok(texts.add(v.text()), where + ": no two variants read the same");
                ok(Drift.facts(v.facts()).keySet().equals(base.keySet()),
                        where + ": a change alters a fact, it does not add or drop one");
                ok(!Drift.facts(v.facts()).equals(base),
                        where + ": a change states at least one fact differently");
            }
            for (String t : texts) ok(allTexts.add(t), where + ": every variant in the log is distinct");
        }
        for (int n = 1; n <= Drift.NIGHTS; n++)
            eq(perNight[n], Drift.PER_NIGHT, "night " + n + " has four lines");
        System.out.println("       " + Drift.LINES + " lines, "
                + Drift.LOG.stream().mapToInt(l -> l.same().size()).sum() + " rewordings, "
                + Drift.LOG.stream().mapToInt(l -> l.changed().size()).sum() + " changes declared");

        // A row is one line and the column is 540px of Arial 13. DriftScreen
        // clips a line that does not fit rather than wrapping it, because a
        // wrapped row is two rows and the list stops being a list. That makes
        // an over-long line a correctness bug and not a cosmetic one: half a
        // line is a difference that is not there, or a sameness that is not.
        //
        // This is a proxy, and it is written down as one. The real check is the
        // render -- all sixteen lines and all sixty-four variants fit at the
        // current width, which is how the number below was chosen. What this
        // catches is a later edit that writes a line twice as long.
        final int COLUMN_CHARS = 80;
        int longest = 0;
        String longestText = "";
        for (Drift.Line ln : Drift.LOG) {
            List<Drift.Variant> all = new ArrayList<>();
            all.add(ln.base());
            all.addAll(ln.same());
            all.addAll(ln.changed());
            for (Drift.Variant v : all) {
                if (v.text().length() > longest) { longest = v.text().length(); longestText = v.text(); }
            }
        }
        ok(longest <= COLUMN_CHARS,
                "no line is long enough to be clipped in its column (longest is "
                        + longest + " chars: \"" + longestText + "\")");

        System.out.println("\n--- the deal ---");
        // The count is exact by construction, and this is the check that says
        // so: no seed may produce four changes or six.
        boolean counts = true, kindsAgree = true, fromOwnLine = true, deterministic = true;
        int[] changedTimes = new int[Drift.LINES];
        for (long seed = 0; seed < 300; seed++) {
            Drift d = Drift.of(seed);
            int c = 0, r = 0, s = 0;
            for (Drift.Row row : d.rows) {
                if (row.kind() == Drift.Kind.CHANGED) { c++; changedTimes[row.index()]++; }
                else if (row.kind() == Drift.Kind.REWORDED) r++;
                else s++;

                Drift.Line ln = Drift.LOG.get(row.index());
                Map<String, String> then = Drift.facts(ln.base().facts());
                Map<String, String> now = null;
                for (Drift.Variant v : ln.same()) if (v.text().equals(row.now())) now = Drift.facts(v.facts());
                for (Drift.Variant v : ln.changed()) if (v.text().equals(row.now())) now = Drift.facts(v.facts());
                if (row.now().equals(ln.base().text())) now = then;
                if (now == null) { fromOwnLine = false; continue; }

                // The end-to-end check: the kind and the facts must agree.
                boolean differs = !now.equals(then);
                if (differs != (row.kind() == Drift.Kind.CHANGED)) kindsAgree = false;
            }
            if (c != Drift.CHANGED || r != Drift.REWORDED || s != Drift.LINES - Drift.CHANGED - Drift.REWORDED)
                counts = false;
            Drift again = Drift.of(seed);
            for (int i = 0; i < Drift.LINES; i++)
                if (!again.rows.get(i).now().equals(d.rows.get(i).now())
                        || again.rows.get(i).kind() != d.rows.get(i).kind()) deterministic = false;
        }
        ok(counts, "every seed deals exactly " + Drift.CHANGED + " changes and "
                + Drift.REWORDED + " rewordings");
        ok(deterministic, "the same seed deals the same night twice");
        ok(fromOwnLine, "every line reads as one of its own variants");
        ok(kindsAgree, "every row's kind agrees with the facts it states");
        int neverChanged = 0;
        for (int i = 0; i < Drift.LINES; i++) if (changedTimes[i] == 0) neverChanged++;
        eq(neverChanged, 0, "over 300 seeds, every line changes at least once");
        System.out.println("       over 300 seeds the least-changed line changed "
                + min(changedTimes) + " times, the most " + max(changedTimes));

        System.out.println("\n--- the scoring ---");
        Drift d = Drift.of(7);
        eq(d.score(), 0, "marking nothing scores nothing");
        for (int i = 0; i < Drift.LINES; i++) d.marked[i] = true;
        eq(d.score(), 0, "marking everything scores nothing");
        eq(d.falseAlarms(), Drift.LINES - Drift.CHANGED, "and wastes a mark on every line that held still");
        for (int i = 0; i < Drift.LINES; i++) d.marked[i] = false;
        for (Drift.Row row : d.rows) if (row.kind() == Drift.Kind.CHANGED) d.marked[row.index()] = true;
        eq(d.score(), Drift.CHANGED, "marking the changes scores all of them");
        eq(d.missed(), 0, "and misses none");
        // One wasted mark is one line of score, which is the whole incentive.
        d.marked[(d.rows.get(0).kind() == Drift.Kind.CHANGED
                ? d.rows.get(1).index() : d.rows.get(0).index())] = true;
        eq(d.score(), Drift.CHANGED - 1, "and one false mark costs exactly one");

        System.out.println("\n--- the file ---");
        Path tmp = Files.createTempFile("drift", ".state");
        Drift save = Drift.of(99);
        save.toggle(0); save.toggle(5); save.toggle(15);
        save.reported = true;
        save.save(tmp);
        Drift back = Drift.load(tmp);
        eq(back.seed, 99L, "the seed survives the file");
        eq(back.markedCount(), 3, "and so do the marks");
        ok(back.marked[0] && back.marked[5] && back.marked[15], "on the right lines");
        ok(back.reported, "and whether the report was filed");
        for (int i = 0; i < Drift.LINES; i++)
            eq(back.rows.get(i).now(), save.rows.get(i).now(), "line " + (i + 1) + " reads the same after a reload");
        Files.deleteIfExists(tmp);

        System.out.println("\n--- the prose ---");
        ok(Drift.closing(Drift.CHANGED).length() > 0, "there is a closing for a perfect report");
        ok(Drift.closing(0).length() > 0, "and one for a report that found nothing");
        Set<String> closings = new HashSet<>();
        for (int s = 0; s <= Drift.CHANGED; s++) closings.add(Drift.closing(s));
        eq(closings.size(), Drift.CHANGED + 1, "and a different one for every score");
        eq(Drift.word(Drift.CHANGED), "five", "the numbers are words");

        System.out.println("\n--- the phone build ---");
        Path web = Path.of("web", "drift.html");
        if (!Files.exists(web)) {
            System.out.println("       (no web/drift.html from here -- run from the repository root)");
        } else {
            String generated = WebDrift.html();
            ok(generated.equals(Files.readString(web)),
                    "web/drift.html is current -- regenerate it with aside.games.drift.WebDrift");
            // The phone is told the corpus and the voice, and nothing else. It
            // deals its own copy, so it must not be handed the answer key --
            // and the key is exactly the `facts` strings, which have no reason
            // to appear in a build that only ever compares two texts.
            ok(generated.contains("//__MODEL_BEGIN__"), "the build marks its model, so the trace can lift it");
            ok(generated.contains("function deal("), "the phone deals its own copy");
            ok(!generated.contains("gauge=") && !generated.contains("|watch="),
                    "and is not handed a single fact from the answer key");
            for (Drift.Line ln : Drift.LOG) {
                ok(generated.contains(ln.base().text()), "the build carries a line of the log");
                for (Drift.Variant v : ln.same()) ok(generated.contains(v.text()), "and a rewording of it");
                for (Drift.Variant v : ln.changed()) ok(generated.contains(v.text()), "and a change of it");
            }
            for (int s2 = 0; s2 <= Drift.CHANGED; s2++) {
                ok(generated.contains(Drift.foundLine(s2)), "the build carries the found line for " + s2);
                ok(generated.contains(Drift.worthLine(s2)), "and the worth line for " + s2);
                ok(generated.contains(Drift.closing(s2)), "and the closing for " + s2);
            }
            for (int n = 0; n <= Drift.LINES; n++) {
                ok(generated.contains(Drift.markedLine(n)), "the build carries the marked line for " + n);
                ok(generated.contains(Drift.falseLine(n)), "and the false line for " + n);
            }
            for (String p2 : Drift.OPENING) ok(generated.contains(p2), "and the opening");
        }

        System.out.println("\n=== " + checks + " checks, " + failed + " failed ===");
        if (failed > 0) System.exit(1);
    }

    static int min(int[] xs) {
        int m = Integer.MAX_VALUE;
        for (int x : xs) m = Math.min(m, x);
        return m;
    }

    static int max(int[] xs) {
        int m = Integer.MIN_VALUE;
        for (int x : xs) m = Math.max(m, x);
        return m;
    }
}
