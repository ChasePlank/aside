package aside.games.fnaf9.engine;

/**
 * The two things in the halls tonight, and the one line that says what they
 * are to each other.
 *
 * <p>Every night of this game is a <b>pair</b>, and the pairing is a ramp of
 * its own. Night one is two walkers that hold together on a bad picture, so
 * the monitor can be trusted and the night teaches the controls. Night five
 * is two walkers that are <i>gone</i> the moment the picture is a second old,
 * so the monitor can only be trusted if you have been looking at it for less
 * than a second -- which is the whole skill, arriving as the last thing the
 * week asks for rather than the first.
 *
 * <p>{@link #left} and {@link #right} are which hall they walk, and it is
 * fixed for the night rather than rolled. A pair that swapped sides between
 * nights would be a pair whose numbers mean nothing; a pair that keeps its
 * sides is a pair you can learn, which is the only reason the week can get
 * harder in a way that is fair.
 */
public record Pair(Walker left, Walker right, String note) {

    /** The walker on a side. */
    public Walker unit(Feed.Side s) {
        return s == Feed.Side.LEFT ? left : right;
    }

    /** The five nights, in the order the office receives them. */
    public static Pair forNight(int night) {
        return switch (night) {
            case 1 -> new Pair(Walker.PHANTOM_FREDDY, Walker.PHANTOM_CHICA,
                    "Both of them stay on a bad picture. Learn the monitor here.");
            case 2 -> new Pair(Walker.PHANTOM_FOXY, Walker.PHANTOM_PUPPET,
                    "One of them is quick, and one of them will outwait you.");
            case 3 -> new Pair(Walker.SPRINGTRAP, Walker.SHADOW_BONNIE,
                    "One of them is already going. Watch it while it is there.");
            case 4 -> new Pair(Walker.NIGHTMARE, Walker.PLUSHTRAP,
                    "Neither of them stays on the screen for long.");
            default -> new Pair(Walker.SHADOW_FREDDY, Walker.GOLDEN_FREDDY,
                    "There is no picture of either of them. There is only a "
                            + "picture you had.");
        };
    }

    /** Every pair, for the dev hooks and the checks. */
    public static Pair[] all() {
        Pair[] out = new Pair[5];
        for (int n = 1; n <= 5; n++) out[n - 1] = forNight(n);
        return out;
    }
}
