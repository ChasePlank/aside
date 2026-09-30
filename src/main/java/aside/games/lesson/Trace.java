package aside.games.lesson;

/**
 * Dump the handover, so the phone's handover can be held against it.
 *
 * The desktop has no balance to sweep here -- the outcome is exact, not
 * probabilistic, so there is nothing to average. What there is to check is that
 * the two builds deal the same board and the same shift from the same seed, and
 * that they agree about which rules are still standing after the same nights.
 * That second one is the one worth having: the standing set is a set
 * intersection over eight rules and four corners, which is exactly the kind of
 * arithmetic that agrees for a hundred seeds and then disagrees on the hundred
 * and first.
 *
 * tools/lesson-trace.mjs runs this and the phone's own deal over the same seeds
 * and compares every state, every corner, and every standing set.
 *
 * Run from the repository root:
 *   java -cp classes aside.games.lesson.Trace [seeds]
 */
public final class Trace {

    public static void main(String[] args) {
        int seeds = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        StringBuilder b = new StringBuilder();
        for (long seed = 0; seed < seeds; seed++) {
            Lesson l = Lesson.of(seed);
            b.append(seed).append('\t').append(l.rule.ordinal());
            for (Lesson.Reading x : l.board) {
                b.append('\t').append(x.pressure()).append(':').append(x.heat())
                        .append(':').append(x.quadrant().ordinal());
            }
            for (Lesson.Reading x : l.shift) {
                b.append('\t').append(x.pressure()).append(':').append(x.heat())
                        .append(':').append(x.quadrant().ordinal());
            }
            // The nights a player would spend if they read the shift and
            // covered it: one state from each of its corners. The standing set
            // after those three nights is what the two builds have to agree on.
            java.util.Set<Lesson.Quadrant> wanted = new java.util.LinkedHashSet<>();
            for (Lesson.Reading x : l.shift) wanted.add(x.quadrant());
            StringBuilder nights = new StringBuilder();
            for (Lesson.Quadrant q : wanted) {
                for (int i = 0; i < l.board.size(); i++) {
                    if (l.board.get(i).quadrant() == q && l.canShow(i)) {
                        l.show(i);
                        nights.append(nights.length() > 0 ? "," : "").append(i);
                        break;
                    }
                }
            }
            b.append('\t').append(nights);
            b.append('\t').append(Lesson.maskOf(new java.util.ArrayList<>(l.standing())));
            b.append('\t').append(l.preferred().ordinal());
            b.append('\t').append(l.safe() ? 1 : 0);
            b.append('\n');
        }
        System.out.print(b);
    }

    private Trace() { }
}
