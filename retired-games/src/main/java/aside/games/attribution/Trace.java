package aside.games.attribution;

import java.util.ArrayList;
import java.util.List;

/**
 * Play the night ten thousand times, with policies instead of a player.
 *
 * The point is not to prove the game is winnable. It is to find out whether
 * the calls buy anything, and how much, before the writing is finished.
 * A scored game that cannot be played well is a slot machine with a theme.
 *
 * Run from the repository root:
 *   java -cp classes aside.games.attribution.Trace [nights]
 */
public final class Trace {

    public static void main(String[] args) {
        int nights = args.length > 0 ? Integer.parseInt(args[0]) : 20000;

        double[] a = new double[nights], b = new double[nights], c = new double[nights],
                d = new double[nights], e = new double[nights], f = new double[nights];
        double[] truth = new double[nights];
        double[] spread = new double[nights];

        for (int n = 0; n < nights; n++) {
            long seed = 1_000_003L * n + 17;
            a[n] = runAll(seed);
            b[n] = spikeAll(seed);
            c[n] = oracle(seed);
            d[n] = evenSpread(seed);
            e[n] = oneEach(seed);
            f[n] = callTheUncertain(seed);
            Attribution at = Attribution.of(seed);
            truth[n] = (double) at.trueLines() / Attribution.ITEMS;
            spread[n] = maxSpread(at);
        }

        System.out.println("nights: " + nights);
        System.out.println("lines true, mean          " + pct(mean(truth)));
        System.out.println();
        System.out.println("run everything            " + pct(mean(a)) + "   [" + pct(min(a)) + " .. " + pct(max(a)) + "]");
        System.out.println("spike everything          " + pct(mean(b)) + "   [" + pct(min(b)) + " .. " + pct(max(b)) + "]");
        System.out.println("oracle (knows the desk)   " + pct(mean(c)) + "   [" + pct(min(c)) + " .. " + pct(max(c)) + "]");
        System.out.println("one call each, as far as it goes" + pct(mean(e)) + "   [" + pct(min(e)) + " .. " + pct(max(e)) + "]");
        System.out.println("calls spread evenly       " + pct(mean(d)) + "   [" + pct(min(d)) + " .. " + pct(max(d)) + "]");
        System.out.println("calls on the least known  " + pct(mean(f)) + "   [" + pct(min(f)) + " .. " + pct(max(f)) + "]");
        System.out.println();
        System.out.println("best byline spread in one night (true lines, max-min): "
                + String.format("%.2f", mean(spread)));
    }

    // ------------------------------------------------------------ policies

    /** File everything. Scores the desk's own average. */
    static double runAll(long seed) {
        Attribution a = Attribution.of(seed);
        for (Attribution.Item it : a.items) a.file(it, true);
        return a.accuracy();
    }

    /** Kill everything. Scores one minus the desk's average. */
    static double spikeAll(long seed) {
        Attribution a = Attribution.of(seed);
        for (Attribution.Item it : a.items) a.file(it, false);
        return a.accuracy();
    }

    /** Knows every reliability. Runs the top half. The ceiling. */
    static double oracle(long seed) {
        Attribution a = Attribution.of(seed);
        for (Attribution.Item it : a.items) {
            a.file(it, a.stringers.get(it.stringer).reliability > 0.5);
        }
        return a.accuracy();
    }

    /**
     * One call on each stringer, and the seventh on the first. Then run a
     * stringer whose single called line came back true, and spike the rest.
     * This is the naive structural strategy: learn the desk, not the lines.
     */
    static double oneEach(long seed) {
        Attribution a = Attribution.of(seed);
        int[] called = new int[Attribution.STRINGERS];
        int[] trueSeen = new int[Attribution.STRINGERS];
        for (Attribution.Item it : a.items) {
            if (a.callsLeft <= 0) break;
            if (called[it.stringer] > 0) continue;
            if (a.call(it)) { called[it.stringer]++; if (it.calledTrue()) trueSeen[it.stringer]++; }
        }
        for (Attribution.Item it : a.items) {
            if (a.callsLeft <= 0) break;
            if (called[it.stringer] > 0) continue;
            if (a.call(it)) { called[it.stringer]++; if (it.calledTrue()) trueSeen[it.stringer]++; }
        }
        for (Attribution.Item it : a.items) {
            boolean run = called[it.stringer] == 0 || trueSeen[it.stringer] * 2 >= called[it.stringer];
            a.file(it, run);
        }
        return a.accuracy();
    }

    /**
     * Spread the calls as evenly as the desk allows -- one on each of
     * six, the seventh wherever a stringer has only one -- then run by the
     * observed rate. The difference from oneEach is small and worth seeing.
     */
    static double evenSpread(long seed) {
        Attribution a = Attribution.of(seed);
        int[] called = new int[Attribution.STRINGERS];
        int[] trueSeen = new int[Attribution.STRINGERS];
        for (int pass = 0; pass < 2; pass++) {
            for (Attribution.Item it : a.items) {
                if (a.callsLeft <= 0) break;
                if (called[it.stringer] > pass) continue;
                if (a.call(it)) { called[it.stringer]++; if (it.calledTrue()) trueSeen[it.stringer]++; }
            }
        }
        for (Attribution.Item it : a.items) {
            boolean run = called[it.stringer] == 0 || trueSeen[it.stringer] * 2 >= called[it.stringer];
            a.file(it, run);
        }
        return a.accuracy();
    }

    /**
     * Spend calls on the lines you are least able to place -- the stringers
     * you have called least -- and file each line as soon as its stringer has
     * a rate. This is the direct strategy: learn the line you are about to
     * run. It should be worse than the structural ones, and if it is not, the
     * game is not asking the question it says it is asking.
     */
    static double callTheUncertain(long seed) {
        Attribution a = Attribution.of(seed);
        int[] called = new int[Attribution.STRINGERS];
        int[] trueSeen = new int[Attribution.STRINGERS];
        List<Attribution.Item> pending = new ArrayList<>(a.items);
        while (a.callsLeft > 0 && !pending.isEmpty()) {
            Attribution.Item best = null;
            int bestN = Integer.MAX_VALUE;
            for (Attribution.Item it : pending) {
                if (called[it.stringer] < bestN) { bestN = called[it.stringer]; best = it; }
            }
            if (best == null) break;
            if (a.call(best)) { called[best.stringer]++; if (best.calledTrue()) trueSeen[best.stringer]++; }
            pending.remove(best);
        }
        for (Attribution.Item it : a.items) {
            boolean run = called[it.stringer] == 0 || trueSeen[it.stringer] * 2 >= called[it.stringer];
            a.file(it, run);
        }
        return a.accuracy();
    }

    // ------------------------------------------------------------- helpers

    /** The gap between the best and worst byline on one night, in true lines. */
    static double maxSpread(Attribution a) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int s = 0; s < Attribution.STRINGERS; s++) {
            int t = a.trueBy(s);
            lo = Math.min(lo, t);
            hi = Math.max(hi, t);
        }
        return hi - lo;
    }

    static double mean(double[] xs) {
        double s = 0;
        for (double x : xs) s += x;
        return s / xs.length;
    }

    static double min(double[] xs) {
        double m = Double.MAX_VALUE;
        for (double x : xs) m = Math.min(m, x);
        return m;
    }

    static double max(double[] xs) {
        double m = -Double.MAX_VALUE;
        for (double x : xs) m = Math.max(m, x);
        return m;
    }

    static String pct(double x) { return String.format("%.3f", x); }

    private Trace() {}
}
