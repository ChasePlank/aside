package aside.games.bearings;

/**
 * Print a whole voyage, day by day, in the save format.
 *
 * This exists for one reason: the phone build carries a second copy of the
 * rule, because the rule cannot be resolved into a table the way Handoff's
 * night and Inventory's report were. Sixteen days, three choices a day, and a
 * drift that moves at night -- there is no table of that. Two copies of a rule
 * is exactly the situation that produced the Inventory save-format bug: both
 * builds were self-consistent and they disagreed about what the same save
 * meant.
 *
 * So this prints the Java model's state after every day, and
 * tools/bearings-trace.mjs prints the phone model's state after every day for
 * the same seed and the same policy, and the two outputs are diffed. If they
 * are not byte-identical the port has drifted, and the diff says on which day
 * and in which field.
 *
 * The policies are shared with the node side by name, and the pick table is
 * the same table in both files. That is a duplication too, and it is the
 * acceptable kind: it is a test input, not a rule, and if it drifts the trace
 * fails loudly instead of quietly agreeing.
 *
 * Run from the repository root:
 *   java -cp classes aside.games.bearings.Trace &lt;seed&gt; [table|a|b|look]
 */
public final class Trace {

    /** The same table as the one in web/bearings.html. A sighting falls back to A in cloud. */
    static final String[] PICKS = {
        "A", "B", "SIGHT", "A", "SIGHT", "B", "A", "B",
        "SIGHT", "A", "A", "SIGHT", "B", "A", "SIGHT", "B",
    };

    static Bearings.Pick pick(String policy, Bearings b) {
        String p = switch (policy) {
            case "a" -> "A";
            case "b" -> "B";
            case "look" -> b.canSight() ? "SIGHT" : (b.day % 2 == 0 ? "A" : "B");
            default -> PICKS[b.day % PICKS.length];
        };
        if (p.equals("SIGHT") && !b.canSight()) p = "A";
        return Bearings.Pick.valueOf(p);
    }

    public static void main(String[] args) {
        long seed = Long.parseLong(args[0]);
        String policy = args.length > 1 ? args[1] : "table";
        Bearings b = new Bearings(seed);
        StringBuilder out = new StringBuilder();
        out.append(b.serialize()).append('\n');
        while (!b.finished) {
            if (!b.choose(pick(policy, b))) break;
            out.append("--\n").append(b.serialize()).append('\n');
        }
        System.out.print(out);
    }

    private Trace() {}
}
