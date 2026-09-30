package aside.games.drift;

import java.util.List;

/**
 * Dump the deal, so the phone's deal can be held against it.
 *
 * The desktop has no balance to sweep here -- the score is exact, not
 * probabilistic, so there is nothing to average. What there is to check is that
 * the two builds deal the same copy from the same seed, which is the one thing
 * a ported model can get wrong in a way that never shows up on either side
 * alone. tools/drift-trace.mjs runs this and the phone's own deal over the same
 * seeds and compares every line.
 *
 * The format is one line per line of the log: seed, index, kind, and the text
 * that is showing now. Text is included on purpose -- a comparison of kinds
 * alone would pass while the two builds showed different sentences.
 *
 * Run from the repository root:
 *   java -cp classes aside.games.drift.Trace [seeds]
 */
public final class Trace {

    public static void main(String[] args) {
        int seeds = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        StringBuilder b = new StringBuilder();
        for (long seed = 0; seed < seeds; seed++) {
            List<Drift.Row> rows = Drift.of(seed).rows;
            for (Drift.Row r : rows) {
                b.append(seed).append('\t').append(r.index()).append('\t')
                 .append(r.kind()).append('\t').append(r.now()).append('\n');
            }
        }
        System.out.print(b);
    }

    private Trace() {}
}
