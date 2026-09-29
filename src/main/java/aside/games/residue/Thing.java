package aside.games.residue;

import java.util.List;

/**
 * The five things in the room, and what leaving each one means.
 *
 * The {@link Kind} is the whole game.
 *
 *   UNINTENTIONAL — evidence of a body. A cup says someone was here
 *   without meaning to say anything. It cannot lie, because it was never
 *   trying to say anything in the first place. These last.
 *
 *   DELIBERATE — a message. A message is about the writer: it wants to be
 *   read, and wanting is fragile. These decay fast.
 *
 *   STATE — not left at all, but set. The lamp is on or it is off, and it
 *   stays that way until somebody changes it. Only one exists at a time.
 *
 * The consequence is deliberate: a room that only ever receives messages
 * empties out, and a room that receives bodies slowly fills with the
 * furniture of them. That asymmetry is the design, not a bug.
 */
public enum Thing {

    CUP("cup", Kind.UNINTENTIONAL, 4, List.of(
            new Gesture("half", "A cup, half full. You drank from it and left the rest."),
            new Gesture("empty", "A cup, empty, set down where you were standing."),
            new Gesture("full", "A cup, filled and untouched. You poured it and did not drink."))),

    CHAIR("chair", Kind.UNINTENTIONAL, 5, List.of(
            new Gesture("pulled", "The chair, pulled out from the wall."),
            new Gesture("turned", "The chair, turned to face the window."),
            new Gesture("unmoved", "The chair, exactly as you found it. You sat on the floor."))),

    WINDOW("window", Kind.DELIBERATE, 2, List.of(
            new Gesture("mark", "A mark in the condensation. One line, drawn and left."),
            new Gesture("name", "Your name, written in the condensation."),
            new Gesture("hand", "A handprint, pressed flat and held there."))),

    LAMP("lamp", Kind.STATE, 99, List.of(
            new Gesture("on", "The lamp, on."),
            new Gesture("low", "The lamp, turned down to the lowest it goes."),
            new Gesture("off", "The lamp, off."))),

    BOOK("book", Kind.DELIBERATE, 3, List.of(
            new Gesture("ownpage", "The book, open at the page you stopped at."),
            new Gesture("firstpage", "The book, open at the first page."),
            new Gesture("held", "The book, closed, something holding a place inside.")));

    public enum Kind { UNINTENTIONAL, DELIBERATE, STATE }

    /** A way of leaving this thing, and the words for it as left. */
    public static final class Gesture {
        public final String id;
        public final String text;
        public Gesture(String id, String text) { this.id = id; this.text = text; }
    }

    public final String id;
    public final Kind kind;
    /** Visits until this is gone. At age == decay the trace has vanished. */
    public final int decay;
    public final List<Gesture> gestures;

    Thing(String id, Kind kind, int decay, List<Gesture> gestures) {
        this.id = id;
        this.kind = kind;
        this.decay = decay;
        this.gestures = gestures;
    }

    public Gesture gesture(String id) {
        for (Gesture g : gestures) if (g.id.equals(id)) return g;
        return null;
    }

    /**
     * What this thing looks like at a given age, or null once it is gone.
     *
     * The decay lines are written as evidence rather than loss. "You can
     * see that it was a mark" is not a description of damage — the fog is
     * proof that somebody else breathed in here after you left. Corruption
     * is how the room tells you it was not empty.
     */
    public String describe(String gestureId, int age) {
        Gesture g = gesture(gestureId);
        if (g == null) return null;
        if (age >= decay) return null;
        if (age == 0) return g.text;
        String suffix = decayText(age);
        return suffix == null ? g.text : g.text + " " + suffix;
    }

    String decayText(int age) {
        return switch (this) {
            case CUP -> switch (age) {
                case 1 -> "It has been sitting there a while.";
                case 2 -> "Dust has gathered in it.";
                case 3 -> "Someone has moved it to the shelf.";
                default -> null;
            };
            case CHAIR -> switch (age) {
                case 1 -> "It has not moved.";
                case 2 -> "Somebody has been in here since.";
                case 3 -> "It is back against the wall. Almost.";
                case 4 -> "You cannot tell any more whether it moved.";
                default -> null;
            };
            case WINDOW -> switch (age) {
                case 1 -> "It has fogged over. You can see that it was a mark.";
                default -> null;
            };
            case LAMP -> switch (age) {
                case 1 -> "Nobody has come to change it.";
                case 2 -> "It is still on. It has been on for a while now.";
                default -> null;
            };
            case BOOK -> switch (age) {
                case 1 -> "The page has curled.";
                case 2 -> "The pages have settled. Nobody has touched it in a while.";
                default -> null;
            };
        };
    }

    public static Thing byId(String id) {
        for (Thing t : values()) if (t.id.equals(id)) return t;
        return null;
    }
}
