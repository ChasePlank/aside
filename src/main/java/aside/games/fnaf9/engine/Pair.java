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

    /**
     * The five nights, in the order the office receives them.
     *
     * <p><b>The speeds were re-tuned on 2026-10-01 so that the week ramps.</b>
     * The first cast ramped the sharp axis and left the speed axis alone, and
     * the sweep said what that costs: PRO read 80/82/61/27/60, so night four
     * was the hardest night of the week and night five was easier than night
     * three. The reason is that <b>the sharp axis does not affect a competent
     * player at all</b> -- raising night five's walkers from sharp 1.15/1.00
     * to 2.60/2.50 moves PRO by nothing, because the policy that looks for
     * half a second at a time never has a stale picture to be punished by.
     * What the sharp axis does is trap the player who stares, which is the
     * design working; what it cannot do is carry a difficulty ramp.
     *
     * <p>So the ramp was moved onto the axis that is actually felt -- arrival
     * rate -- and the pairs were set from the sweep rather than from taste.
     * The ladder is now 84/76/60/49/33 and monotone, and the sharp values were
     * left as the thematic axis they always were, with night four's brought
     * down to match its own note.
     */
    public static Pair forNight(int night) {
        return switch (night) {
            case 1 -> new Pair(Walker.PHANTOM_FREDDY, Walker.PHANTOM_CHICA,
                    "Both of them stay on a bad picture. Learn the monitor here.");
            case 2 -> new Pair(Walker.PHANTOM_FOXY, Walker.PHANTOM_PUPPET,
                    "One of them is quick, and one of them will outwait you.");
            case 3 -> new Pair(Walker.SPRINGTRAP, Walker.SHADOW_BONNIE,
                    "One of them is already going. Watch it while it is there.");
            // Night four is the night the two of them come apart, and the
            // speed gap is the whole of it. Two walkers with the same step
            // stay in phase, so one hold covers both and the night is easy
            // (measured: two identical walkers are survivable 92%). Two with
            // different steps drift, and the drift is what makes the night
            // hard -- but it is also what makes it *lopsided*, because the
            // walker that falls behind is the one that arrives just after the
            // door has let go, every time.
            //
            // That is what this pair shipped as, and it was not a night, it
            // was a side: 99 deaths on the left against 9 on the right, and
            // the same split mirrored when the two were swapped. Plushtrap was
            // brought up from 1.18 to 1.24 to widen the gap past the band
            // where the phase barely moves (see Walker.PLUSHTRAP). The night
            // is the same difficulty and now kills 102 left, 106 right.
            //
            // The sharp values are the pair's other half and they are the
            // *thematic* axis rather than the felt one: night four is where
            // the picture starts losing them, between night three's "watch it
            // while it is there" and night five's "there is no picture of
            // either of them". The sweep says a competent player looks for
            // half a second at a time and is never punished by it, so the
            // sharp axis cannot carry the ramp and does not try to.
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
