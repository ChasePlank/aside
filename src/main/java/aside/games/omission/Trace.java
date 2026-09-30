package aside.games.omission;

import java.util.ArrayList;
import java.util.List;

/**
 * Dump the house, so the phone's house can be held against it.
 *
 * There is no balance to sweep here -- the outcome is exact, not probabilistic,
 * so there is nothing to average. What there is to check is that the two builds
 * deal the same list from the same seed, agree about which things were said,
 * and agree about what each of them remembers once the bag is closed. That last
 * one is the one worth having: what you remember is a comparison of two strings
 * per thing, and it is exactly the kind of arithmetic that agrees for a hundred
 * seeds and then disagrees on the hundred and first.
 *
 * The bag the trace fills is the one the rule implies -- the five heaviest
 * things nobody else knew -- so the trace also proves that the two builds agree
 * about which bag that is, which is the whole of the game.
 *
 * tools/omission-trace.mjs runs this and the phone's own deal over the same
 * seeds and compares every thing, every weight, every line, and every memory.
 *
 * Run from the repository root:
 *   java -cp classes aside.games.omission.Trace [seeds]
 */
public final class Trace {

    public static void main(String[] args) {
        int seeds = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        StringBuilder b = new StringBuilder();
        for (long seed = 0; seed < seeds; seed++) {
            Omission o = Omission.of(seed);
            b.append(seed);
            for (Omission.Detail d : o.list) {
                b.append('\t').append(d.id())
                 .append(':').append(d.matters())
                 .append(':').append(d.told() ? 1 : 0)
                 .append(':').append(d.assumed());
            }
            // The bag the rule implies: the five heaviest things nobody else
            // knew, in weight order with the index as the tiebreak, so both
            // builds have to agree about the order as well as the set.
            List<Integer> silent = new ArrayList<>();
            for (int i = 0; i < o.list.size(); i++) if (!o.list.get(i).told()) silent.add(i);
            silent.sort((x, y) -> o.list.get(y).matters() - o.list.get(x).matters() != 0
                    ? o.list.get(y).matters() - o.list.get(x).matters() : x - y);
            StringBuilder bag = new StringBuilder();
            for (int i = 0; i < Omission.SLOTS; i++) {
                o.keep(silent.get(i));
                bag.append(bag.length() > 0 ? "," : "").append(silent.get(i));
            }
            o.leave();
            b.append('\t').append(bag);
            b.append('\t').append(o.score());
            b.append('\t').append(o.best());
            b.append('\t').append(o.silentCount());
            for (int i = 0; i < o.list.size(); i++) {
                b.append('\t').append(o.right(i) ? 1 : 0);
            }
            b.append('\n');
        }
        System.out.print(b);
    }

    private Trace() { }
}
