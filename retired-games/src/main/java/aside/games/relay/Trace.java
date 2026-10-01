package aside.games.relay;

/**
 * relay's model, as text, for the cross-build trace.
 *
 * The phone build has to compute the rule itself, so the rule is the one thing
 * that can be wrong on both sides at once without either side noticing. This
 * prints the desktop's answer -- every count, every worth, every kept and lost
 * label, and every line the report can say -- and tools/relay-trace.mjs holds
 * the phone's model against it.
 *
 *   java -cp classes aside.games.relay.Trace
 */
public final class Trace {

    public static void main(String[] args) {
        Relay r = new Relay();
        StringBuilder b = new StringBuilder();

        for (int m = 0; m < Relay.MESSAGES; m++) {
            for (int o = 0; o < Relay.OPTIONS; o++) {
                b.append("opt\t").append(m).append('\t').append(o).append('\t')
                 .append(r.carriedFacts(m, o)).append('\t')
                 .append(r.carriedPoints(m, o)).append('\t')
                 .append(r.worth(m, o)).append('\t')
                 .append(r.total(m, o)).append('\t')
                 .append(String.join(" | ", r.kept(m, o))).append('\t')
                 .append(String.join(" | ", r.lost(m, o))).append('\n');
            }
        }
        for (int s = 0; s <= Relay.MAX; s++) {
            b.append("worth\t").append(s).append('\t').append(Relay.worthLine(s)).append('\n');
            b.append("closing\t").append(s).append('\t').append(Relay.closing(s)).append('\n');
        }
        for (int k = 0; k <= Relay.MESSAGES; k++) {
            b.append("breaks\t").append(k).append('\t').append(Relay.breaksLine(k)).append('\n');
        }
        for (int w = 0; w <= Relay.PER_MESSAGE; w++) {
            b.append("word\t").append(w).append('\t').append(Relay.worthWord(w)).append('\n');
        }
        System.out.print(b);
    }

    private Trace() {}
}
