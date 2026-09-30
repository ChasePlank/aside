package aside.games.fnaf4.engine;

/**
 * The four places to look, and the shape of the room.
 *
 * This is the whole difference between FNAF 4 and the three before it. In
 * FNAF 1, 2 and 3 you sit at a desk and everything comes to you: the doors
 * are within reach, the cameras are a button, and the only question is
 * which of your hands to spend. Here the room has four sides and you have
 * <b>one body</b>, so the question is not which hand, it is where you are.
 *
 * <pre>
 *        LEFT DOOR
 *            |
 *   CLOSET -- BED -- RIGHT DOOR
 * </pre>
 *
 * The bed is the hub. That is not decoration: it is what makes the room a
 * room rather than a menu. From the bed every place is one hop away, and
 * from one place to another is always two, because you have to come back
 * through the middle. So the doors are cheap to reach and each other is
 * expensive, and the cost of a bad guess is a hop you did not have.
 */
public final class Room {

    public enum Where {
        BED("the bed"),
        LEFT("the left door"),
        RIGHT("the right door"),
        CLOSET("the closet");

        public final String label;

        Where(String label) { this.label = label; }
    }

    /** The station you start at, and the one you come back through. */
    public static final Where HUB = Where.BED;

    /**
     * How many hops between two places.
     *
     * Zero for the same place, one for anything through the bed, and two
     * for a spoke to a spoke. The whole difficulty of the room is in this
     * function, which is why it is a function and not a table.
     */
    public static int hops(Where from, Where to) {
        if (from == to) return 0;
        if (from == HUB || to == HUB) return 1;
        return 2;
    }

    /** The places, in the order the screen draws them. */
    public static final Where[] ALL = Where.values();

    private Room() {}
}
