package aside.games.promise;

/**
 * promise's model, as text, for the cross-build trace.
 *
 * The phone build has to compute the night itself -- what is due and what the
 * water takes both come out of the same four crossings -- so the arithmetic is
 * the one thing that can be wrong on both sides at once without either side
 * noticing. This prints the desktop's answer for every answer set and every
 * night, and every line the report can say, and tools/promise-trace.mjs holds
 * the phone's model against it.
 *
 *   java -cp classes aside.games.promise.Trace
 */
public final class Trace {

    public static void main(String[] args) {
        StringBuilder b = new StringBuilder();

        // Every answer set, every night: the load and the overage. This is the
        // whole rule, and it is the number the break screen turns on.
        for (int mask = 0; mask < (1 << Promise.ASKS); mask++) {
            for (int n = 1; n <= Promise.NIGHTS; n++) {
                Promise p = new Promise();
                p.night = n;
                for (int i = 0; i < Promise.ASKS; i++) {
                    p.answer[i] = (mask & (1 << i)) != 0 ? 1 : 0;
                }
                b.append("night\t").append(mask).append('\t').append(n).append('\t')
                 .append(p.load()).append('\t').append(p.over()).append('\n');
            }
        }

        for (int i = 0; i < Promise.ASKS; i++) {
            b.append("due\t").append(i).append('\t')
             .append(Promise.dueLine(Promise.REQUESTS.get(i))).append('\n');
        }
        for (int n = 1; n <= Promise.NIGHTS; n++) {
            b.append("ordinal\t").append(n).append('\t').append(Promise.ordinal(n)).append('\n');
            b.append("label\t").append(n).append('\t').append(Promise.nightLabel(n)).append('\n');
        }
        for (int c = 1; c <= 2; c++) {
            b.append("cost\t").append(c).append('\t').append(Promise.costWord(c)).append('\n');
        }
        for (int l = 0; l <= 12; l++) {
            b.append("load\t").append(l).append('\t').append(Promise.loadLine(l)).append('\n');
            b.append("over\t").append(l).append('\t').append(Promise.overLine(l)).append('\n');
        }
        for (int k = 0; k <= Promise.ASKS; k++) {
            b.append("breaks\t").append(k).append('\t').append(Promise.breaksLine(k)).append('\n');
        }
        for (int s = -Promise.ASKS; s <= Promise.MAX; s++) {
            b.append("worth\t").append(s).append('\t').append(Promise.worthLine(s)).append('\n');
            b.append("closing\t").append(s).append('\t').append(Promise.closing(s)).append('\n');
        }
        b.append("empty\t0\t").append(Promise.CLOSING_EMPTY).append('\n');

        System.out.print(b);
    }

    private Trace() {}
}
