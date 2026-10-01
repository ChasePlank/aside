package aside.games.interval;

import java.util.List;

/**
 * Dump the season, so the phone's season can be held against it.
 *
 * The desktop has no balance to sweep here -- the outcome is exact, not
 * probabilistic, so there is nothing to average. What there is to check is that
 * the two builds deal the same season from the same seed: the same record on
 * the wall, and the same day she comes. That is the one thing a ported model
 * can get wrong in a way that never shows up on either side alone, and the
 * record makes it worse than usual -- twelve draws from a cumulative table is
 * exactly the kind of arithmetic that agrees for a hundred seeds and then
 * disagrees on the hundred and first.
 *
 * tools/interval-trace.mjs runs this and the phone's own deal over the same
 * seeds and compares every draw.
 *
 * Run from the repository root:
 *   java -cp classes aside.games.interval.Trace [seeds]
 */
public final class Trace {

    public static void main(String[] args) {
        int seeds = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        StringBuilder b = new StringBuilder();
        for (long seed = 0; seed < seeds; seed++) {
            Interval it = Interval.of(seed);
            b.append(seed).append('\t').append(it.arrival);
            List<Integer> rec = it.record;
            for (int r : rec) b.append('\t').append(r);
            b.append('\n');
        }
        System.out.print(b);
    }

    private Trace() {}
}
