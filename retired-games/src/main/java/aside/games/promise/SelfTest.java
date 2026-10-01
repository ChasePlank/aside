package aside.games.promise;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * promise's own checks.
 *
 * Run from the repository root:
 *   java -cp classes aside.games.promise.SelfTest
 *
 * The point of these is not that the code runs. It is that the SEASON is what
 * it claims to be: that every ask comes due after it is made, that the two
 * strategies the game is built on actually diverge, that six is really the most
 * anybody can keep, and that the trap -- saying yes to everything and then
 * choosing who to let down -- is a real outcome rather than a story the closing
 * text tells.
 */
public class SelfTest {

    static int pass = 0, fail = 0;

    static void check(String name, boolean ok) {
        if (ok) { pass++; System.out.println("  ok   " + name); }
        else { fail++; System.out.println("  FAIL " + name); }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== promise self-test ===\n");

        System.out.println("--- the shape ---");
        check("eight asks", Promise.REQUESTS.size() == Promise.ASKS);
        check("five nights of water", Promise.WATER.length == Promise.NIGHTS + 1);
        check("the water is indexed by night, with a hole at zero", Promise.WATER[0] == null);
        for (int i = 0; i < Promise.ASKS; i++) {
            Promise.Ask a = Promise.REQUESTS.get(i);
            String tag = "ask " + (i + 1) + " (" + a.who() + ")";
            check(tag + ": costs one or two crossings", a.cost() == 1 || a.cost() == 2);
            check(tag + ": is made on a night in 1..4",
                    Promise.ASKED_ON[i] >= 1 && Promise.ASKED_ON[i] <= 4);
            check(tag + ": comes due after it is made", a.due() > Promise.ASKED_ON[i]);
            check(tag + ": comes due on a night in 2..5", a.due() >= 2 && a.due() <= 5);
            check(tag + ": says something", !a.what().isBlank());
        }
        for (int i = 1; i < Promise.ASKS; i++) {
            check("ask " + (i + 1) + " is not made before ask " + i,
                    Promise.ASKED_ON[i] >= Promise.ASKED_ON[i - 1]);
        }
        for (int n = 1; n <= Promise.NIGHTS; n++) {
            check("the water on night " + n + " takes one or two",
                    Promise.WATER[n].cost() == 1 || Promise.WATER[n].cost() == 2);
            check("the water on night " + n + " says something", !Promise.WATER[n].what().isBlank());
        }

        System.out.println("\n--- every ask is reachable ---");
        for (int i = 0; i < Promise.ASKS; i++) {
            check("ask " + (i + 1) + " is put to the player",
                    Promise.ASKED_ON[i] >= 1 && Promise.ASKED_ON[i] <= 4);
        }

        System.out.println("\n--- the two strategies ---");
        int greedy = bestScore(allYes());
        int counting = bestScore(decline(5, 7));   // the schoolmaster, and Tess's second
        System.out.println("       say yes to all eight: " + greedy + " of " + Promise.MAX);
        System.out.println("       say no to the two that cannot fit: " + counting + " of " + Promise.MAX);
        check("saying yes to everything breaks promises", breaksFor(allYes()) > 0);
        check("counting breaks none", breaksFor(decline(5, 7)) == 0);
        check("counting beats saying yes to everything", counting > greedy);
        check("counting is a perfect season", counting == Promise.MAX);

        System.out.println("\n--- six is the ceiling ---");
        int best = -99, bestMask = -1;
        for (int mask = 0; mask < (1 << Promise.ASKS); mask++) {
            int s = bestScore(mask);
            if (s > best) { best = s; bestMask = mask; }
        }
        check("no answer set scores above " + Promise.MAX, best <= Promise.MAX);
        check("some answer set scores exactly " + Promise.MAX, best == Promise.MAX);
        System.out.println("       best over all 256 answer sets: " + best
                + "  (" + maskWords(bestMask) + ")");

        int mostKept = 0;
        for (int mask = 0; mask < (1 << Promise.ASKS); mask++) {
            mostKept = Math.max(mostKept, keptFor(mask));
        }
        check("six is the most anybody can keep", mostKept == Promise.MAX);
        System.out.println("       most kept over all 256 answer sets: " + mostKept);

        System.out.println("\n--- the water is never broken ---");
        // The whole rule in one check: on a night that is over, breaking every
        // promise still leaves the water standing. If this ever stops being
        // true, the game has stopped being about promises and started being
        // about arithmetic.
        for (int n = 1; n <= Promise.NIGHTS; n++) {
            Promise probe = new Promise();
            probe.night = n;
            for (int i = 0; i < Promise.ASKS; i++) {
                probe.answer[i] = Promise.REQUESTS.get(i).due() == n ? 1 : 0;
            }
            for (int i : probe.dueTonight()) probe.breakIt(i);
            check("night " + n + ": the water is still there after every break",
                    probe.load() == Promise.WATER[n].cost());
        }

        System.out.println("\n--- the season runs ---");
        Promise run = new Promise();
        int guard = 0;
        while (!run.reported && guard++ < 200) {
            if (run.asking()) run.answer(true);
            else {
                List<Integer> due = run.dueTonight();
                while (run.over() > 0 && !due.isEmpty()) {
                    run.breakIt(due.remove(due.size() - 1));
                }
                run.finishNight();
            }
        }
        check("a season played by saying yes to everything reaches the report", run.reported);
        check("and it broke something", run.breaks() > 0);
        check("and it did not break more than it made", run.breaks() <= run.kept() + run.breaks());
        System.out.println("       greedy season: kept " + run.kept() + ", broke " + run.breaks()
                + ", said no to " + run.declined() + "  \u2192  " + run.score());

        System.out.println("\n--- the words ---");
        check("a perfect season says so",
                Promise.closing(Promise.MAX).contains("Every promise you made"));
        check("a clean empty season is not the same as a perfect one",
                !Promise.CLOSING_EMPTY.equals(Promise.closing(Promise.MAX)));
        check("a season with breaks says so",
                Promise.closing(0).contains("let down"));
        check("a season under water says something else",
                Promise.closing(-2).contains("bad luck"));
        check("the score line names the ceiling",
                Promise.worthLine(4).endsWith("of " + Promise.MAX));
        check("no breaks reads as good news", Promise.breaksLine(0).startsWith("nothing"));
        check("one break is worded for a person", Promise.breaksLine(1).contains("one promise"));
        check("the cost of a promise is worded for a person",
                Promise.costWord(1).equals("one crossing") && Promise.costWord(2).equals("two crossings"));
        check("the nights are named, not numbered",
                Promise.ordinal(5).equals("fifth") && Promise.ordinal(2).equals("second"));
        check("a row says what happened to it",
                Promise.rowWord(1, false).equals(Promise.KEPT_HEAD)
                        && Promise.rowWord(1, true).equals(Promise.BROKEN_TAG)
                        && Promise.rowWord(0, false).equals(Promise.DECLINED_HEAD));

        System.out.println("\n--- the save ---");
        Path tmp = Files.createTempFile("promise", ".state");
        Promise saved = new Promise();
        saved.answer[0] = 1; saved.answer[1] = 0; saved.answer[2] = 1;
        saved.broken[2] = true;
        saved.night = 4; saved.askAt = 6;
        saved.save(tmp);
        Promise back = Promise.load(tmp);
        boolean same = true;
        for (int i = 0; i < Promise.ASKS; i++) {
            if (back.answer[i] != saved.answer[i] || back.broken[i] != saved.broken[i]) same = false;
        }
        check("the answers and the breaks survive a round trip", same);
        check("so does the night", back.night == 4);
        check("so does the ask cursor", back.askAt == 6);
        check("an unplayed season loads as unplayed",
                Promise.load(tmp.resolveSibling("nothing-here.state")).night == 1);
        Files.deleteIfExists(tmp);

        System.out.println("\n--- the phone build ---");
        Path out = Path.of("web", "promise.html");
        if (!Files.exists(out)) {
            System.out.println("       (no web/promise.html from here -- run from the repository root)");
        } else {
            String generated = WebPromise.html();
            check("web/promise.html is current -- regenerate it with aside.games.promise.WebPromise",
                    generated.equals(Files.readString(out)));
        }

        System.out.println("\n=== " + pass + " passed, " + fail + " failed ===");
        if (fail > 0) System.exit(1);
    }

    // ------------------------------------------------------------- the search

    static int allYes() { return (1 << Promise.ASKS) - 1; }

    /** The mask with the given asks declined and everything else promised. */
    static int decline(int... which) {
        int mask = allYes();
        for (int i : which) mask &= ~(1 << i);
        return mask;
    }

    /**
     * The best a player can do with this answer set.
     *
     * On a night that is over, the player breaks promises until it fits. Every
     * break costs one kept and one break, so the best play is the fewest breaks
     * that fit -- and since the costs are one and two, that is a small search.
     */
    static int bestScore(int mask) { return keptFor(mask) - breaksFor(mask); }

    static int keptFor(int mask) {
        int kept = 0;
        for (int n = 1; n <= Promise.NIGHTS; n++) {
            List<Integer> due = dueOn(mask, n);
            int broken = fewestBreaks(due, Promise.WATER[n].cost());
            kept += due.size() - broken;
        }
        return kept;
    }

    static int breaksFor(int mask) {
        int broken = 0;
        for (int n = 1; n <= Promise.NIGHTS; n++) {
            broken += fewestBreaks(dueOn(mask, n), Promise.WATER[n].cost());
        }
        return broken;
    }

    /** The asks promised by this mask that come due on this night. */
    static List<Integer> dueOn(int mask, int night) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < Promise.ASKS; i++) {
            if ((mask & (1 << i)) != 0 && Promise.REQUESTS.get(i).due() == night) out.add(i);
        }
        return out;
    }

    /** The fewest promises that have to go for the night to fit. */
    static int fewestBreaks(List<Integer> due, int water) {
        int total = water;
        for (int i : due) total += Promise.REQUESTS.get(i).cost();
        if (total <= Promise.CAPACITY) return 0;
        int best = Integer.MAX_VALUE;
        for (int sub = 0; sub < (1 << due.size()); sub++) {
            int left = water, n = 0;
            for (int k = 0; k < due.size(); k++) {
                if ((sub & (1 << k)) != 0) n++;
                else left += Promise.REQUESTS.get(due.get(k)).cost();
            }
            if (left <= Promise.CAPACITY && n < best) best = n;
        }
        return best;
    }

    static String maskWords(int mask) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Promise.ASKS; i++) {
            if ((mask & (1 << i)) != 0) {
                if (b.length() > 0) b.append(", ");
                b.append(Promise.REQUESTS.get(i).who());
            }
        }
        return b.length() == 0 ? "nobody" : b.toString();
    }
}
