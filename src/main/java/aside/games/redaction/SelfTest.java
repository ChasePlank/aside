package aside.games.redaction;

import aside.game.Game;
import aside.game.Games;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * redaction, checked.
 *
 * The interesting checks here are exhaustive rather than illustrative, because
 * the whole state space is 2^16 = 65,536 filings and enumerating it costs
 * about a fifth of a second. A game with a small state space should be checked
 * over all of it; a check that tries three filings is a check that has decided
 * in advance which three matter.
 *
 * What that buys, in order of how much it matters:
 *
 *   - the earliest failure can never be buried, and that is a fact about the
 *     file rather than a difficulty setting, so it is asserted rather than
 *     described;
 *   - the frontier is (4 failures out, 6 bars), (3, 7), (2, 8), (1, 9) -- one
 *     more bar buys exactly one more buried failure -- and the four points
 *     give four different verdicts, so the trade is legible;
 *   - the finding table the phone build indexes is complete and every key it
 *     can reach is a key the desktop can reach.
 *
 * Run from the repository root:
 *   java -cp classes aside.games.redaction.SelfTest
 */
public final class SelfTest {

    static int passed = 0, failed = 0;

    static void ok(boolean cond, String what) {
        if (cond) { passed++; System.out.println("  ok   " + what); }
        else { failed++; System.out.println("  FAIL " + what); }
    }

    static void eq(Object a, Object b, String what) {
        ok(a == null ? b == null : a.equals(b), what + "  (" + a + " vs " + b + ")");
    }

    public static void main(String[] args) throws Exception {
        theFile();
        theReading();
        everyFiling();
        theFrontier();
        theFindingTable();
        theSaveFile();
        thePhoneBuild();
        registry();
        System.out.println();
        System.out.println(failed == 0
                ? "=== " + passed + " passed, 0 failed ==="
                : "=== " + passed + " passed, " + failed + " FAILED ===");
        if (failed > 0) System.exit(1);
    }

    // ------------------------------------------------------------- the file

    static void theFile() {
        System.out.println("--- the file ---");
        Redaction r = Redaction.of();
        eq(r.lines.size(), Redaction.LINES, "sixteen lines");
        boolean numbered = true;
        for (int i = 0; i < r.lines.size(); i++) {
            if (r.lines.get(i).number != i + 1) numbered = false;
        }
        ok(numbered, "numbered 1 to 16 in order, so the board's order is the file's order");

        int fails = 0, names = 0, refers = 0;
        for (Redaction.Line l : r.lines) {
            if (l.kind == Redaction.FAIL) fails++;
            if (l.kind == Redaction.NAME) names++;
            if (l.refers) refers++;
        }
        eq(fails, Redaction.FAILS_TOTAL, "four lines are the office's own record");
        eq(names, 1, "one line names the complainant");
        eq(refers, 3, "three lines refer to the complainant");

        // The trap, stated as a check. Line 3 is harmless on its face and is
        // the line that says a name exists; line 5 is the first failure and is
        // the fifth line, so it has only four lines in front of it.
        ok(r.lines.get(2).refers, "line 3 refers to the complainant, and it is the decoy");
        eq(r.lines.get(4).kind, Redaction.FAIL, "line 5 is the first failure");
        eq(r.lines.get(13).kind, Redaction.NAME, "line 14 names the complainant");

        ok(!r.anyWithheld(), "a new file withholds nothing");
        eq(r.withheld(), 0, "and says so");
        eq(r.released(), Redaction.LINES, "and releases everything");
    }

    // ---------------------------------------------------------- the reading

    static void theReading() {
        System.out.println("\n--- the board's reading ---");
        Redaction r = Redaction.of();
        r.lines.get(0).withheld = true;
        r.lines.get(3).withheld = true;
        r.lines.get(7).withheld = true;
        Redaction.Reading read = r.reading();
        eq(read.recovered.size(), 3, "three bars, three questions, three lines read");
        eq(read.recovered.get(0).number, 1, "read in file order: 1");
        eq(read.recovered.get(1).number, 4, "then 4");
        eq(read.recovered.get(2).number, 8, "then 8");
        eq(read.extra, 1, "and line 8 refers, so it bought the board another question");

        // The question that runs out exactly when a line buys another one is
        // the boundary the ported loop can get wrong, so it is pinned here as
        // well as in the exhaustive pass.
        Redaction r2 = Redaction.of();
        for (int ln : new int[]{1, 2, 4, 5, 6}) r2.lines.get(ln - 1).withheld = true;
        Redaction.Reading read2 = r2.reading();
        eq(read2.recovered.size(), 5, "five bars and five questions reads five lines");
        eq(read2.extra, 0, "and none of those five refers");

        Redaction r3 = Redaction.of();
        r3.lines.get(2).withheld = true;   // line 3, which refers
        eq(r3.reading().recovered.size(), 1, "one bar is one line read");
        eq(r3.reading().extra, 1, "and it refers, so it is one question plus one");

        // The extra question is not a bonus that keeps compounding: the loop
        // stops the moment the questions run out, so only a line that refers
        // and is REACHED buys anything. With everything withheld the board
        // reaches line 3 and stops at line 6 -- lines 8 and 13 are never
        // arrived at, and their questions are never spent.
        Redaction r4 = Redaction.of();
        for (Redaction.Line l : r4.lines) l.withheld = true;
        Redaction.Reading read4 = r4.reading();
        eq(read4.recovered.size(), Redaction.DIGS + 1,
                "withholding everything reads six, because line 3 refers and buys one");
        eq(read4.extra, 1, "and only line 3 was reached, so only one question was bought");
        eq(read4.recovered.get(read4.recovered.size() - 1).number, 6, "and the reading stops at line 6");
    }

    // ------------------------------------------------------- every filing

    static void everyFiling() {
        System.out.println("\n--- every filing (2^16) ---");
        Redaction r = Redaction.of();
        int n = r.lines.size();
        int total = 1 << n;

        int nameOut = 0;
        int minBarsNameSafe = Integer.MAX_VALUE;
        int minBarsNameSafeRecordClean = Integer.MAX_VALUE;
        int maxFailsBuried = 0;
        boolean earliestFailureAlwaysRead = true;
        boolean nameSafeNeedsSix = true;
        Set<Integer> reached = new LinkedHashSet<>();

        for (int mask = 0; mask < total; mask++) {
            for (int i = 0; i < n; i++) r.lines.get(i).withheld = (mask & (1 << i)) != 0;
            Redaction.Finding f = r.finding();
            if (f.nameOut) nameOut++;
            else {
                minBarsNameSafe = Math.min(minBarsNameSafe, f.withheld);
                if (f.failsOut == 0) minBarsNameSafeRecordClean = Math.min(minBarsNameSafeRecordClean, f.withheld);
            }
            maxFailsBuried = Math.max(maxFailsBuried, f.failsTotal - f.failsOut);
            reached.add(Redaction.findingKey(f.nameOut, f.failsOut, f.withheld));

            // Line 5 is the fifth line and the first failure: four lines in
            // front of it, and a bar is only buried by other bars. So it is
            // read in every one of the 65,536 filings -- and if it is not
            // withheld it is out anyway. Either way the board has it.
            if (f.failsOut < 1) earliestFailureAlwaysRead = false;

            // Keeping the name out means the name is not among the first five
            // questions, so there are at least five bars in front of it.
            if (!f.nameOut && f.withheld < 6) nameSafeNeedsSix = false;
        }

        ok(earliestFailureAlwaysRead,
                "the earliest failure is read in all " + total + " filings -- it cannot be buried");
        ok(nameSafeNeedsSix, "keeping the name out always costs at least six bars");
        eq(minBarsNameSafe, 6, "and six is reachable");
        eq(minBarsNameSafeRecordClean, Integer.MAX_VALUE,
                "a filing that keeps the name out AND the record clean does not exist");
        eq(maxFailsBuried, 3, "at most three of the four failures can be buried");

        System.out.println("       filings that name the complainant: " + nameOut
                + " of " + total + " (" + String.format("%.1f", 100.0 * nameOut / total) + "%)");
        System.out.println("       findings the filings actually reach: " + reached.size()
                + " of " + Redaction.findingKeys() + " keys in the table");
    }

    // ---------------------------------------------------------- the frontier

    static void theFrontier() {
        System.out.println("\n--- the frontier ---");
        Redaction r = Redaction.of();
        int n = r.lines.size();
        int total = 1 << n;

        // Fewest bars for each (name safe, failures out) pair.
        int[] fewest = new int[Redaction.FAILS_TOTAL + 1];
        java.util.Arrays.fill(fewest, Integer.MAX_VALUE);
        for (int mask = 0; mask < total; mask++) {
            for (int i = 0; i < n; i++) r.lines.get(i).withheld = (mask & (1 << i)) != 0;
            Redaction.Finding f = r.finding();
            if (!f.nameOut) fewest[f.failsOut] = Math.min(fewest[f.failsOut], f.withheld);
        }
        for (int k = 0; k <= Redaction.FAILS_TOTAL; k++) {
            System.out.println("       name safe, " + k + " of " + Redaction.FAILS_TOTAL
                    + " failures out: " + (fewest[k] == Integer.MAX_VALUE ? "unreachable" : fewest[k] + " bars"));
        }
        eq(fewest[4], 6, "four failures out costs six bars");
        eq(fewest[3], 7, "three costs seven");
        eq(fewest[2], 8, "two costs eight");
        eq(fewest[1], 9, "one costs nine");
        eq(fewest[0], Integer.MAX_VALUE, "and none is unreachable, because line 5 cannot be buried");

        // One more bar buys exactly one more buried failure, all the way along.
        boolean oneForOne = true;
        for (int k = 2; k <= 4; k++) if (fewest[k] != fewest[k - 1] - 1) oneForOne = false;
        ok(oneForOne, "each failure buried costs exactly one more bar");

        // The four points have to be four different things, or the trade is
        // invisible to the player who made it.
        Set<String> verdicts = new LinkedHashSet<>();
        Set<String> findings = new LinkedHashSet<>();
        for (int k = 1; k <= 4; k++) {
            verdicts.add(Redaction.verdict(false, k, fewest[k]));
            findings.add(Redaction.headline(false, k, fewest[k]) + " / "
                    + Redaction.recordLine(k) + " / " + Redaction.withholdingLine(fewest[k]));
        }
        eq(verdicts.size(), 4, "the four frontier points end on four different verdicts");
        eq(findings.size(), 4, "and on four different findings");

        // The trap, measured: a decoy that refers costs a question, so the
        // naive filing -- the name and four ordinary-looking decoys, one of
        // which is line 3 -- names the complainant.
        Redaction naive = Redaction.of();
        for (int ln : new int[]{1, 2, 3, 4, 14}) naive.lines.get(ln - 1).withheld = true;
        ok(naive.finding().nameOut, "the name and four decoys names the complainant if a decoy is line 3");
        Redaction careful = Redaction.of();
        for (int ln : new int[]{1, 2, 4, 6, 7, 14}) careful.lines.get(ln - 1).withheld = true;
        ok(!careful.finding().nameOut, "and the same filing with line 3 left alone does not");
    }

    // ------------------------------------------------------ the finding table

    static void theFindingTable() {
        System.out.println("\n--- the finding table the phone indexes ---");
        int keys = Redaction.findingKeys();
        eq(keys, 2 * (Redaction.FAILS_TOTAL + 1) * (Redaction.LINES + 1),
                "the table is nameOut x failures x withheld");
        boolean unique = true, complete = true;
        Set<Integer> seen = new LinkedHashSet<>();
        for (int nameOut = 0; nameOut < 2; nameOut++) {
            for (int fails = 0; fails <= Redaction.FAILS_TOTAL; fails++) {
                for (int held = 0; held <= Redaction.LINES; held++) {
                    int k = Redaction.findingKey(nameOut == 1, fails, held);
                    if (k < 0 || k >= keys || !seen.add(k)) unique = false;
                    for (String s : Redaction.findingEntry(nameOut == 1, fails, held)) {
                        if (s == null || s.isEmpty()) complete = false;
                    }
                }
            }
        }
        ok(unique, "every combination has its own key, and every key is in range");
        eq(seen.size(), keys, "and the keys cover the table exactly once");
        ok(complete, "and no entry in the table is empty");

        // The table is indexed along an axis, and a table can be indexed along
        // the wrong one with every entry still a real sentence -- the failure
        // Outside shipped. So the key is checked against the finding itself.
        Redaction r = Redaction.of();
        int n = r.lines.size();
        boolean keysAgree = true;
        for (int mask = 0; mask < (1 << n); mask += 97) {
            for (int i = 0; i < n; i++) r.lines.get(i).withheld = (mask & (1 << i)) != 0;
            Redaction.Finding f = r.finding();
            String[] e = Redaction.findingEntry(f.nameOut, f.failsOut, f.withheld);
            if (!e[0].equals(Redaction.headline(f))) keysAgree = false;
            if (!e[1].equals(Redaction.personLine(f.nameOut))) keysAgree = false;
            if (!e[2].equals(Redaction.recordLine(f.failsOut))) keysAgree = false;
            if (!e[3].equals(Redaction.withholdingLine(f.withheld))) keysAgree = false;
            if (!e[4].equals(Redaction.verdict(f))) keysAgree = false;
        }
        ok(keysAgree, "and the entry a filing looks up is the finding that filing reaches");
    }

    // ------------------------------------------------------------ the save

    static void theSaveFile() throws Exception {
        System.out.println("\n--- the save file ---");
        Path dir = Files.createTempDirectory("redaction-selftest");
        Path p = dir.resolve("redaction.state");
        Redaction r = Redaction.of();
        r.lines.get(0).withheld = true;
        r.lines.get(13).withheld = true;
        r.save(p);
        Redaction back = Redaction.load(p);
        eq(back.withheld(), 2, "two bars survive the round trip");
        ok(back.lines.get(0).withheld && back.lines.get(13).withheld, "and they are the right two");
        ok(!back.sent, "and the file is not yet sent");

        r.sent = true;
        r.save(p);
        ok(Redaction.load(p).sent, "and a sent file comes back sent");
        eq(Redaction.load(p).withheld(), 2, "with its bars intact");

        Path missing = dir.resolve("nothing-here");
        eq(Redaction.load(missing).withheld(), 0, "a file that was never saved is a fresh file");
        Files.deleteIfExists(p);
        Files.deleteIfExists(dir);
    }

    // ------------------------------------------------------- the phone build

    static void thePhoneBuild() throws Exception {
        System.out.println("\n--- the phone build ---");
        String generated = WebRedaction.html();
        Path checked = Path.of("web", "redaction.html");
        ok(Files.exists(checked), "web/redaction.html exists");
        ok(Files.readString(checked).equals(generated),
                "and is current -- regenerate it with aside.games.redaction.WebRedaction");

        // A generated file that has gone stale is worse than no file, and a
        // build missing a sentence is stale in the way that matters most.
        carries(generated, Redaction.PERSON_NAMED, "the finding about a named complainant");
        carries(generated, Redaction.PERSON_SAFE, "the finding about a complainant who is not named");
        carries(generated, Redaction.RECORD_CENSURED, "the finding about the office's record");
        carries(generated, Redaction.HELD_GUTTED, "the finding about a gutted file");
        carries(generated, Redaction.VERDICT_NAME_OUT, "the verdict about the pointer");
        carries(generated, Redaction.VERDICT_GUTTED, "the verdict about a different document");
        carries(generated, Redaction.STANDING, "the standing line");
        carries(generated, Redaction.CLOSING_TEMPLATE, "the closing, as a template");
        carries(generated, Redaction.OPENING.get(3), "the rule about a line that refers");

        // The one thing the build must NOT be given is the answer to a filing.
        // It is given the file and the reading rule, and it works the finding
        // out; the table it indexes is the report, not the game.
        ok(!generated.contains("\"withheld\":["),
                "and it is not handed a filing -- it is handed the file and the rule");

        int kb = generated.length() / 1024;
        System.out.println("       phone build: " + kb + " KB, " + Redaction.findingKeys()
                + " findings, " + Redaction.LINES + " lines");
    }

    static void carries(String haystack, String needle, String what) {
        ok(haystack.contains(needle), "the phone build carries " + what);
    }

    // ----------------------------------------------------------- the registry

    static void registry() {
        System.out.println("\n--- the registry ---");
        Game g = Games.byId("redaction");
        ok(g != null, "the engine can find redaction by id");
        if (g == null) return;
        eq(g.title(), "Redaction", "and it is called Redaction");
        ok(g.blurb() != null && g.blurb().length() > 20, "and it has a line for the library");
        eq(g.id(), "redaction", "and its id is its id");
        ok(g.blurb().contains("bar"), "and the line for the library says what the game is about");

        List<String> ids = new ArrayList<>();
        for (Game x : Games.all()) ids.add(x.id());
        eq(ids.size(), new LinkedHashSet<>(ids).size(), "no two games share an id");
        ok(ids.contains("redaction"), "and redaction is one of them");
    }
}
