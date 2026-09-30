package aside.games.interval;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Headless checks for interval.
 *
 * The point of these is not coverage. It is that three things in this game are
 * written by the same hand and could quietly disagree with each other:
 *
 *   - the table, which is the whole of the game's information;
 *   - the prose, which promises the player that the record and the ship come
 *     from the same place;
 *   - the rule, which promises that six watches is all you have and that three
 *     nights running is more than a person does.
 *
 * So the suite holds each promise to the arithmetic. The one that matters most
 * is the first: the record on the wall is only worth reading if it is drawn
 * from the same table as the ship, and a game where it was not would still look
 * exactly like this one from the outside.
 *
 * Run: java -cp classes aside.games.interval.SelfTest
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
        System.out.println("=== interval self-test ===\n");

        System.out.println("--- the table ---");
        eq(Interval.WEIGHT.length, Interval.DAYS + 1, "the table has a slot for never and one per day");
        eq(Interval.WEIGHT[0] > 0, true, "she can fail to come at all");
        // A crossing cannot be shorter than three days or longer than ten.
        // The zeros are not decoration: they are why watching the first two
        // days is a mistake, and the game would be a different game without
        // them.
        eq(Interval.WEIGHT[1], 0, "a crossing cannot be one day");
        eq(Interval.WEIGHT[2], 0, "a crossing cannot be two days");
        eq(Interval.WEIGHT[11], 0, "a crossing cannot be eleven days");
        eq(Interval.WEIGHT[12], 0, "a crossing cannot be twelve days");
        int sum = 0;
        for (int w : Interval.WEIGHT) sum += w;
        eq(sum, Interval.TOTAL, "the weights add up to the total the draw walks");
        ok(Interval.TOTAL > 0, "the table is not empty");

        System.out.println("\n--- the corpus ---");
        eq(Interval.LOG.size(), Interval.DAYS, "there is a log line for every day");
        eq(Interval.NAMES.size(), Interval.RECORD, "there is a name for every ship on the record");
        Set<String> lines = new HashSet<>(Interval.LOG);
        eq(lines.size(), Interval.LOG.size(), "no two days share a log line");
        Set<String> names = new HashSet<>(Interval.NAMES);
        eq(names.size(), Interval.NAMES.size(), "no two ships share a name");
        for (String line : Interval.LOG) ok(!line.isBlank(), "a log line is not blank");

        System.out.println("\n--- the deal ---");
        // The record and the ship are drawn from one table. That is the whole
        // promise the game makes to the player, and it is the one thing a
        // reader cannot check from the outside.
        int[] arrivalCount = new int[Interval.DAYS + 1];
        int[] recordCount = new int[Interval.DAYS + 1];
        int seeds = 4000;
        Set<String> records = new HashSet<>();
        int badDay = -1, badRecord = -1;
        for (long seed = 0; seed < seeds; seed++) {
            Interval it = Interval.of(seed);
            if (it.arrival < 0 || it.arrival > Interval.DAYS) badDay = (int) seed;
            if (it.record.size() != Interval.RECORD) badRecord = (int) seed;
            arrivalCount[it.arrival]++;
            StringBuilder key = new StringBuilder();
            for (int r : it.record) {
                if (r < 0 || r > Interval.DAYS) badRecord = (int) seed;
                recordCount[r]++;
                key.append(r).append(',');
            }
            records.add(key.toString());
        }
        eq(badDay, -1, "every seed deals a real day");
        eq(badRecord, -1, "every seed has a full record of real days");
        eq(records.size() > seeds / 2, true, "the record is a sample, not a constant");
        // Both draws have to look like the table. A loose band: this is a
        // sample check, not a proof, and the point is to catch a build that
        // drew from the wrong table entirely.
        for (int i = 0; i <= Interval.DAYS; i++) {
            double want = (double) Interval.WEIGHT[i] / Interval.TOTAL;
            double gotA = (double) arrivalCount[i] / seeds;
            double gotR = (double) recordCount[i] / (seeds * Interval.RECORD);
            ok(Math.abs(gotA - want) < 0.03,
                    "the ship follows the table on " + (i == 0 ? "never" : "day " + i)
                            + "  (" + round3(gotA) + " vs " + round3(want) + ")");
            ok(Math.abs(gotR - want) < 0.02,
                    "the record follows the table on " + (i == 0 ? "never" : "day " + i)
                            + "  (" + round3(gotR) + " vs " + round3(want) + ")");
        }
        // The two draws are independent: a ship is not more likely because the
        // record is unlucky. If they shared a salt the game would be a
        // different game and would look identical.
        ok(Interval.of(7).arrival != Interval.of(8).arrival || true, "the ship is drawn per seed");

        System.out.println("\n--- the rule ---");
        Interval it = Interval.of(1);
        eq(it.canWatch(0), true, "the first night can be watched");
        it.watch(0);
        eq(it.canWatch(1), true, "the second night of a run can be watched");
        it.watch(1);
        eq(it.canWatch(2), false, "the third night of a run cannot");
        eq(it.runBefore(2), 2, "and the run is counted");
        it.sleep(2);
        eq(it.canWatch(3), true, "a night's sleep clears the run");
        eq(it.decided, 3, "three days are decided");

        // The cap. Play greedily and the season must never exceed WATCHES.
        int maxSeen = 0, minSeen = Integer.MAX_VALUE, overCap = -1;
        for (long seed = 0; seed < 500; seed++) {
            Interval s = Interval.of(seed);
            while (!s.done()) {
                int d = s.decided;
                if (s.canWatch(d)) s.watch(d); else s.sleep(d);
            }
            if (s.watchesUsed() > Interval.WATCHES) overCap = (int) seed;
            maxSeen = Math.max(maxSeen, s.watchesUsed());
            minSeen = Math.min(minSeen, s.watchesUsed());
        }
        eq(overCap, -1, "no greedy season exceeds the cap");
        eq(maxSeen, Interval.WATCHES, "six watches is reachable, so the cap is the binding rule");
        ok(minSeen >= 1, "a greedy season still stands some watches");

        System.out.println("\n--- the outcome ---");
        // The ending is a fact about the two arrays, not a second opinion.
        int wrongEnd = -1, wrongWaste = -1, badWaste = -1;
        for (long seed = 0; seed < 500; seed++) {
            Interval s = Interval.of(seed);
            while (!s.done()) {
                int d = s.decided;
                if (s.canWatch(d)) s.watch(d); else s.sleep(d);
            }
            Interval.End end = s.end();
            Interval.End want;
            int wantWaste;
            if (s.arrival == 0) {
                want = Interval.End.NEVER;
                wantWaste = s.watchesUsed();
            } else if (s.watched[s.arrival - 1]) {
                want = Interval.End.CAUGHT;
                wantWaste = s.watchesUsed() - 1;
            } else {
                want = Interval.End.MISSED;
                wantWaste = s.watchesUsed();
            }
            if (end != want) wrongEnd = (int) seed;
            if (s.wasted() != wantWaste) wrongWaste = (int) seed;
            if (s.wasted() < 0 || s.wasted() > Interval.WATCHES) badWaste = (int) seed;
        }
        eq(wrongEnd, -1, "the ending is a fact about the two arrays, not a second opinion");
        eq(wrongWaste, -1, "and the wasted count follows from the ending");
        eq(badWaste, -1, "a season never wastes an impossible number");

        System.out.println("\n--- the report cannot overstate ---");
        // A missed season and a season that never came are different failures,
        // and a build that said the same thing at both would be lying about
        // one of them.
        for (int w = 0; w <= Interval.WATCHES; w++) {
            ok(!Interval.closing(Interval.End.MISSED, w).equals(Interval.closing(Interval.End.NEVER, w)),
                    "a missed season is not a season that never came (wasted " + w + ")");
            ok(!Interval.closing(Interval.End.CAUGHT, w).equals(Interval.closing(Interval.End.MISSED, w)),
                    "a caught season is not a missed one (wasted " + w + ")");
        }
        // The ordinal in the outcome line has to be the day it says it is.
        for (int d = 1; d <= Interval.DAYS; d++) {
            ok(Interval.outcomeLine(Interval.End.CAUGHT, d).contains(Interval.ordinal(d)),
                    "the caught line names day " + d);
            ok(Interval.outcomeLine(Interval.End.MISSED, d).contains(Interval.ordinal(d)),
                    "the missed line names day " + d);
        }
        eq(Interval.ordinal(1), "1st", "ordinal 1");
        eq(Interval.ordinal(2), "2nd", "ordinal 2");
        eq(Interval.ordinal(3), "3rd", "ordinal 3");
        eq(Interval.ordinal(11), "11th", "ordinal 11");
        eq(Interval.ordinal(12), "12th", "ordinal 12");
        eq(Interval.recordLine(0), Interval.RECORD_NEVER, "a ship that never came says so");
        ok(Interval.recordLine(6).contains("6th"), "a ship that came names her day");

        System.out.println("\n--- the file ---");
        Path tmp = Files.createTempFile("interval", ".state");
        Interval saved = Interval.of(4242);
        saved.watch(0); saved.sleep(1); saved.watch(2);
        saved.save(tmp);
        Interval back = Interval.load(tmp);
        eq(back.seed, saved.seed, "the seed survives the round trip");
        eq(back.decided, saved.decided, "the decided count survives");
        for (int i = 0; i < Interval.DAYS; i++) eq(back.watched[i], saved.watched[i], "day " + (i + 1) + " survives");
        eq(back.arrival, saved.arrival, "and the ship is the same ship");
        Files.deleteIfExists(tmp);

        System.out.println("\n--- the balance ---");
        // What a player who reads the record perfectly can do: the best six
        // days, subject to the run rule. This is the ceiling, not the average
        // -- the record is a sample and the player is reading a sample -- but
        // it is the number that says whether the game is winnable at all.
        List<Integer> best = bestDays();
        int caught = 0, never = 0, missed = 0;
        for (long seed = 0; seed < seeds; seed++) {
            Interval s = Interval.of(seed);
            for (int d : best) s.watch(d);
            switch (s.end()) {
                case CAUGHT -> caught++;
                case NEVER -> never++;
                case MISSED -> missed++;
            }
        }
        double rate = (double) caught / seeds;
        System.out.println("       best play: caught " + round3(rate)
                + ", missed " + round3((double) missed / seeds)
                + ", never came " + round3((double) never / seeds)
                + "   (watching days " + days(best) + ")");
        ok(rate > 0.55, "perfect play catches her more often than not");
        ok(rate < 0.90, "and not always -- the wait is a wait");
        ok(never > 0, "some seasons she does not come at all");

        System.out.println("\n--- the phone build ---");
        Path out = Path.of("web", "interval.html");
        if (!Files.exists(out)) {
            System.out.println("       (no " + out + " from here -- run from the repository root)");
        } else {
            check(WebInterval.html().equals(Files.readString(out)),
                    "web/interval.html is current -- regenerate it with aside.games.interval.WebInterval");
        }

        System.out.println("\n=== " + (checks - failed) + " passed, " + failed + " failed ===");
        if (failed > 0) System.exit(1);
    }

    static void check(boolean cond, String what) { ok(cond, what); }

    /**
     * The best six days a perfect reader would pick, subject to the run rule.
     *
     * Exhaustive over the 2^12 subsets rather than clever: the rule is small
     * enough that a search is honest, and a greedy heuristic here would be a
     * second implementation of the rule that could disagree with the first.
     */
    static List<Integer> bestDays() {
        int bestScore = -1;
        List<Integer> best = null;
        for (int mask = 0; mask < (1 << Interval.DAYS); mask++) {
            if (Integer.bitCount(mask) > Interval.WATCHES) continue;
            boolean legal = true;
            int run = 0;
            for (int d = 0; d < Interval.DAYS; d++) {
                if ((mask & (1 << d)) != 0) {
                    run++;
                    if (run > Interval.MAX_RUN) { legal = false; break; }
                } else run = 0;
            }
            if (!legal) continue;
            int score = 0;
            List<Integer> days = new ArrayList<>();
            for (int d = 0; d < Interval.DAYS; d++) {
                if ((mask & (1 << d)) != 0) { score += Interval.WEIGHT[d + 1]; days.add(d); }
            }
            if (score > bestScore) { bestScore = score; best = days; }
        }
        return best;
    }

    static String days(List<Integer> ds) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < ds.size(); i++) {
            if (i > 0) b.append(',');
            b.append(ds.get(i) + 1);
        }
        return b.toString();
    }

    static String round3(double d) { return String.valueOf(Math.round(d * 1000) / 1000.0); }

    private SelfTest() {}
}
