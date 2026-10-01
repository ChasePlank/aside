package aside.games.fnaf8.engine;

/**
 * The two things walking tonight, and the one line that says what they are
 * to each other.
 *
 * <p>Every night of this game is a <b>pair</b>, not a list. FNAF 5 gave you
 * three threats and asked which counter belonged to which. FNAF 6 gave you
 * one. FNAF 7 gave you one. FNAF 8 gives you <b>two, and the night is not
 * about either of them</b> -- it is about the distance between them, which
 * is the only number in the office that can kill you.
 *
 * <p>So the cast is not five units, it is five <i>relationships</i>, and the
 * note on each one is the reason those two are the two. Night one is the
 * bandmates, who share a stage and would share a doorway. Night five is the
 * two that came back, and the last night of the week is the one where the
 * pair has the least in common and therefore the most to say to each other.
 *
 * <p>{@link #left} and {@link #right} are which hall they walk up, and it is
 * fixed for the night rather than rolled. A pair that swapped sides between
 * nights would be a pair whose numbers mean nothing; a pair that keeps its
 * sides is a pair you can learn, which is the only reason the week can get
 * harder in a way that is fair.
 */
public record Pair(Unit left, Unit right, String note) {

    /** The unit on a side. */
    public Unit unit(Meeting.Side s) {
        return s == Meeting.Side.LEFT ? left : right;
    }

    /** The five nights, in the order the office receives them. */
    public static Pair forNight(int night) {
        return switch (night) {
            case 1 -> new Pair(Unit.BONNIE, Unit.CHICA,
                    "The two that share a stage.");
            case 2 -> new Pair(Unit.TOY_BONNIE, Unit.TOY_CHICA,
                    "The two that were built to replace them.");
            case 3 -> new Pair(Unit.FREDDY, Unit.FOXY,
                    "The two that never do.");
            case 4 -> new Pair(Unit.GOLDEN_FREDDY, Unit.PUPPET,
                    "The two that are already dead.");
            default -> new Pair(Unit.SPRINGTRAP, Unit.NIGHTMARE,
                    "The two that came back.");
        };
    }

    /** Every pair, for the dev hooks and the checks. */
    public static Pair[] all() {
        Pair[] out = new Pair[5];
        for (int n = 1; n <= 5; n++) out[n - 1] = forNight(n);
        return out;
    }
}
