package aside.games.fnaf5.engine;

/**
 * The five rooms of the rental, in a line.
 *
 * This is the shape that makes FNAF 5 a different game from the four
 * before it. FNAF 1, 2 and 3 gave you a desk: the doors were within reach
 * and the cameras were a button, so the question was which hand to spend.
 * FNAF 4 gave you a room with four sides and one body, so the question was
 * where to stand. Here you have <b>a building</b> -- five rooms in a row,
 * and you can only be in one of them.
 *
 * <pre>
 *   PARTS/SERVICE -- BALLORA GALLERY -- CONTROL MODULE -- CIRCUS GALLERY -- FUNTIME AUDITORIUM
 *          0                1                2               3                 4
 * </pre>
 *
 * A line rather than a ring, and that is the whole reason the movement
 * costs anything. In a ring every room has two neighbours and no room is a
 * trap. In a line the two ends are dead ends: a player who runs to
 * PARTS/SERVICE to get away from something has one door left, and the
 * thing they were running from is standing in it.
 *
 * The other half of the shape is the rule that comes with it, and it lives
 * in {@link Game}: <b>you can never see the room you are in.</b> The camera
 * only reaches the rooms you are not standing in, so every decision to move
 * is a decision made on a picture of where you are going, taken from
 * somewhere else, and already a second old by the time you arrive.
 */
public final class Room {

    public enum Where {
        PARTS("Parts/Service", "PARTS/SERVICE"),
        BALLORA("Ballora Gallery", "BALLORA GALLERY"),
        CONTROL("Control Module", "CONTROL MODULE"),
        CIRCUS("Circus Gallery", "CIRCUS GALLERY"),
        AUDITORIUM("Funtime Auditorium", "FUNTIME AUDITORIUM");

        /** How the game says it in a sentence. */
        public final String label;
        /** How the strip says it, where there is no room for a sentence. */
        public final String tag;

        Where(String label, String tag) {
            this.label = label;
            this.tag = tag;
        }
    }

    /** Where the shift starts. The middle, so both ends are a real walk. */
    public static final Where START = Where.CONTROL;

    /** The rooms, in the order the building has them. */
    public static final Where[] ALL = Where.values();

    /** How many rooms there are. */
    public static final int COUNT = ALL.length;

    /** The position of a room in the line. */
    public static int index(Where w) {
        return w.ordinal();
    }

    /** The room at a position, or null if the position is off the end. */
    public static Where at(int i) {
        return i < 0 || i >= COUNT ? null : ALL[i];
    }

    /**
     * How many rooms apart two rooms are.
     *
     * Not "hops" the way FNAF 4's room counted them, because there is no
     * hub here: the building is a line, so the distance between two rooms
     * is just how far apart they are, and the walk is that many moves.
     */
    public static int distance(Where a, Where b) {
        return Math.abs(index(a) - index(b));
    }

    /** True when you can walk between them without passing through another. */
    public static boolean adjacent(Where a, Where b) {
        return distance(a, b) == 1;
    }

    /**
     * One move from {@code from} toward {@code to}.
     *
     * Returns {@code from} when it is already there, which is how a caller
     * can tell "arrived" from "still walking" without a second test.
     */
    public static Where stepToward(Where from, Where to) {
        if (from == null || to == null) return from;
        int d = Integer.compare(index(to), index(from));
        Where next = at(index(from) + d);
        return next == null ? from : next;
    }

    /**
     * One move from {@code from}, away from {@code away}.
     *
     * Used by the one thing in the building that can lose you. At an end of
     * the line there is nowhere further to go, so it stays -- which is the
     * correct behaviour and not a fallback: a thing that has walked to the
     * wall has walked as far from you as the building allows.
     */
    public static Where stepAway(Where from, Where away) {
        if (from == null) return null;
        int d = Integer.compare(index(from), index(away));
        // Standing in the same room as the thing she is walking away from
        // is the common case, not the edge case: she gives up while she is
        // in the room with you. A zero here has to become a direction, or
        // "walks off" is a no-op that resets her patience and leaves her
        // standing exactly where she was -- which is a bug that looks like
        // a working game until you read the trace.
        if (d == 0) d = index(from) * 2 < COUNT ? 1 : -1;
        Where next = at(index(from) + d);
        if (next == null) next = at(index(from) - d);
        return next == null ? from : next;
    }

    private Room() {}
}
