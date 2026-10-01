package aside.games.corroboration;

/**
 * Print a whole night, question by question, as a state dump.
 *
 * This exists for one reason: the phone build carries a second copy of the
 * rule, because the rule cannot be resolved into a table -- what the player is
 * doing is spending a budget of questions against answers that depend on the
 * questions already spent. Two copies of a rule is exactly the situation that
 * produced the Inventory save-format bug: both builds were self-consistent and
 * they disagreed about what the same save meant.
 *
 * So this prints the Java model's state after every question, and
 * tools/corroboration-trace.mjs prints the phone model's state after every
 * question for the same seed, and the two outputs are diffed. If they are not
 * byte-identical the port has drifted, and the diff says on which question and
 * in which field.
 *
 * Run from the repository root:
 *   java -cp classes aside.games.corroboration.Trace &lt;seed&gt;
 */
public final class Trace {

    public static void main(String[] args) {
        int seed = args.length > 0 ? Integer.parseInt(args[0]) : 0;
        Corroboration c = Corroboration.newNight(seed);

        StringBuilder b = new StringBuilder();
        b.append("seed ").append(seed).append('\n');

        // The same strategy as the node side: check a person, then check
        // whether the check could have failed. It is a test input, not a rule,
        // and if it drifts the trace fails loudly instead of quietly agreeing.
        int q = 0;
        for (int a = 0; a < Corroboration.N; a++) {
            c.check(0, a);
            b.append("q").append(q++).append(' ').append(dump(c)).append('\n');
            if (!c.decisive(0, a)) {
                c.check(1, a);
                b.append("q").append(q++).append(' ').append(dump(c)).append('\n');
            }
            c.filed[a] = c.established(a);
        }
        c.filedDone = true;

        b.append("filed");
        for (int a = 0; a < Corroboration.N; a++) b.append(' ').append(c.filed[a]);
        b.append('\n');
        b.append("truth");
        for (int a = 0; a < Corroboration.N; a++) b.append(' ').append(c.truth[a]);
        b.append('\n');
        b.append("verdict");
        for (int a = 0; a < Corroboration.N; a++) b.append(' ').append(c.verdict(a).name());
        b.append('\n');
        b.append("closing ").append(c.closing()).append('\n');
        System.out.print(b);
    }

    static String dump(Corroboration c) {
        StringBuilder b = new StringBuilder();
        b.append("left=").append(c.left);
        b.append(" asked=");
        for (int o = 0; o < 2; o++) for (int a = 0; a < Corroboration.N; a++) b.append(c.asked[o][a] ? 1 : 0);
        b.append(" answer=");
        for (int o = 0; o < 2; o++) for (int a = 0; a < Corroboration.N; a++) b.append(c.answer[o][a]).append(',');
        b.append(" est=");
        for (int a = 0; a < Corroboration.N; a++) b.append(c.established(a)).append(',');
        b.append(" claim=");
        for (int o = 0; o < 2; o++) for (int a = 0; a < Corroboration.N; a++) b.append(c.claim(o, a)).append(',');
        return b.toString();
    }

    private Trace() { }
}
