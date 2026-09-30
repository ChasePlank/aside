package aside.games.residue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The room, and everything in it.
 *
 * This is the whole persistent world. It is deliberately small enough to
 * read in one sitting, because it is the thing the player is actually
 * playing against — not the objects, and not the text.
 *
 * The room holds {@link #CAPACITY} traces. When it is full, leaving
 * something displaces the oldest thing there. Nothing is ever deleted for
 * being old; it is deleted for being *pushed out*, which is a different
 * feeling and the one that is true.
 */
public final class Room {

    /**
     * How many things the room can hold at once.
     *
     * Three, not five. There are five things in the room, so a capacity of
     * five would mean nothing ever had to be given up and the choice of
     * what to leave would be free. At three, leaving something new costs
     * the oldest thing in the room, and the player has to decide what the
     * next person is allowed to find.
     */
    public static final int CAPACITY = 3;

    // -------------------------------------------------------------- the voice
    //
    // Every fixed line the game says lives here rather than in a screen.
    //
    // Not for tidiness: there are two builds now. The desktop screen draws
    // these and the phone build prints them, and a sentence kept in one of the
    // two is a sentence the other one does not have. Edit a line here and both
    // builds change together; edit it in a screen and they drift.
    //
    // The lines that vary with the room's state are the ones below that
    // arrival() chooses between. The rest are the beats.

    /** First visit: the room was already lived in before you got here. */
    public static final String ARRIVAL_FIRST =
            "You are not the first one in here. Somebody left the chair out, "
          + "and a book open, and did not come back for either.";

    /** Nothing is left. */
    public static final String ARRIVAL_EMPTY =
            "Nothing is here. Whatever was, it has finished. You arrive with nothing to answer.";

    /** Somebody left the lamp on. */
    public static final String ARRIVAL_LAMP_ON =
            "The lamp is on. Either somebody left it on for whoever came next, or forgot. "
          + "You cannot tell which, and that is the point.";

    /** Somebody left the lamp off. */
    public static final String ARRIVAL_LAMP_OFF =
            "The lamp is off. You stand in the doorway a moment before you touch anything.";

    /** Something of yours is here and has been here long enough to have started going. */
    public static final String ARRIVAL_MINE_AGING =
            "Something in here is yours, and you do not remember leaving it.";

    /** Something of yours is here from last time. */
    public static final String ARRIVAL_MINE =
            "Something in here is still yours from last time. It has already started to go.";

    /** Something is here and none of it is yours. */
    public static final String ARRIVAL_SOMEONE =
            "There is something in here that somebody left. You are not the first, "
          + "and the room is not surprised to see you.";

    /** The room is full and something has to go for anything to be left. */
    public static final String CLOSING_FULL =
            "The room is full. Nothing else fits without something going.";

    /** What is here when nothing has outlasted the gap. */
    public static final String NOTHING_SURVIVED = "Nothing has survived to now.";

    /** Told to the player when the thing they just left will not outlast the gap. */
    public static final String DEPARTURE_NOTHING_LEFT =
            "By the next visit there will be nothing left of it.";

    /** The beat heads. */
    public static final String HEAD_PICK = "Leave something.";
    public static final String HEAD_DEPARTURE = "You go.";

    /** Marked against a thing that is already in the room. */
    public static final String ALREADY_HERE = "already here \u2014 you would be changing it";

    /** "You are leaving the cup." -- per thing, so the two builds cannot word it differently. */
    public static String headLeaving(Thing t) {
        return "You are leaving the " + t.id + ".";
    }

    /** How many times anyone has been in here. Starts at 0; the first
     *  arrival makes it 1. */
    public int visits = 0;

    /** Everything currently in the room, oldest first. */
    public final List<Trace> traces = new ArrayList<>();

    // ---------------------------------------------------------------- life

    /**
     * Age the room by one visit and clear out whatever has finished
     * decaying. Called on arrival, before anything is shown, so the player
     * only ever sees the room as it is now — never the moment of a thing
     * disappearing.
     */
    public void advance() {
        visits++;
        for (Trace t : traces) t.age++;
        traces.removeIf(Trace::gone);
    }

    /**
     * Leave something.
     *
     * There is only one of each thing in the room, so leaving a thing that
     * is already here changes it rather than adding a second one — you
     * cannot have two chairs, and the lamp has one switch. Leaving a thing
     * that is NOT here displaces the oldest trace when the room is full.
     */
    public Trace leave(Thing thing, String gesture, boolean mine) {
        traces.removeIf(t -> t.thing == thing);
        if (traces.size() >= CAPACITY) traces.remove(0);
        Trace t = new Trace(thing, gesture, visits, mine, 0);
        traces.add(t);
        return t;
    }

    /** The trace of a given thing currently in the room, if any. */
    public Trace traceOf(Thing thing) {
        for (Trace t : traces) if (t.thing == thing) return t;
        return null;
    }

    public boolean isEmpty() { return traces.isEmpty(); }

    /**
     * A room that has already been lived in.
     *
     * A new room is never empty. Two things were left by somebody who is
     * not you and will never be explained: the chair pulled out from the
     * wall, and a book somebody was reading. They are at different ages on
     * purpose — one is nearly gone, one has just started to go — so the
     * first thing the room teaches is that things here are already in
     * motion before you arrive.
     */
    public static Room seeded() {
        Room r = new Room();
        r.traces.add(new Trace(Thing.CHAIR, "pulled", 0, false, 2));
        r.traces.add(new Trace(Thing.BOOK, "ownpage", 0, false, 1));
        return r;
    }

    /** Things not currently in the room. */
    public List<Thing> freeThings() {
        List<Thing> out = new ArrayList<>();
        for (Thing t : Thing.values()) if (traceOf(t) == null) out.add(t);
        return out;
    }

    // ------------------------------------------------------------- arrival

    /**
     * What the room does to you on the way in.
     *
     * This is the other direction of the passage: you are not the same
     * person who walks in as the one who walks out, and the room is what
     * changed you. It is generated from what is actually here, so an empty
     * room and a room someone lit are different arrivals.
     */
    public String arrival() {
        if (visits <= 1) return ARRIVAL_FIRST;
        if (traces.isEmpty()) return ARRIVAL_EMPTY;
        Trace lamp = traceOf(Thing.LAMP);
        if (lamp != null && lamp.gesture.equals("on")) return ARRIVAL_LAMP_ON;
        if (lamp != null && lamp.gesture.equals("off")) return ARRIVAL_LAMP_OFF;
        for (Trace t : traces) if (t.mine && t.age >= 2) return ARRIVAL_MINE_AGING;
        for (Trace t : traces) if (t.mine) return ARRIVAL_MINE;
        return ARRIVAL_SOMEONE;
    }

    /**
     * What the player is told about the room as they leave it: one sentence,
     * and it is the model's because both builds print it.
     *
     * The full case is the interesting one. A room at capacity does not say
     * "3 of 3 places taken" -- it says what that means, which is that the next
     * person's choice is already partly made for them.
     */
    public String closing() {
        return traces.size() >= CAPACITY
                ? CLOSING_FULL
                : traces.size() + " of " + CAPACITY + " places taken.";
    }

    // ------------------------------------------------------------- storage

    /**
     * The save format. One line per fact, tab-separated, human-readable.
     *
     * It is plain text on purpose: the room is a place the player might
     * want to open and look at, and a save file you can read is the
     * cheapest kind of honesty.
     */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("# residue room v1\n");
        sb.append("visits\t").append(visits).append('\n');
        for (Trace t : traces) {
            sb.append("trace\t").append(t.thing.id).append('\t').append(t.gesture)
              .append('\t').append(t.age).append('\t').append(t.mine ? "you" : "someone")
              .append('\t').append(t.bornVisit).append('\n');
        }
        return sb.toString();
    }

    public static Room deserialize(String data) {
        Room r = new Room();
        for (String line : data.split("\r?\n")) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] p = line.split("\t", -1);
            switch (p[0]) {
                case "visits" -> r.visits = parseInt(p[1], 0);
                case "trace" -> {
                    Thing thing = Thing.byId(p[1]);
                    if (thing == null) continue;          // unknown thing: drop it, do not crash
                    r.traces.add(new Trace(thing, p[2], parseInt(p[5], 0),
                            "you".equals(p[4]), parseInt(p[3], 0)));
                }
                default -> { }
            }
        }
        return r;
    }

    static int parseInt(String s, int dflt) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return dflt; }
    }

    public void save(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, serialize(), StandardCharsets.UTF_8);
    }

    public static Room load(Path file) throws IOException {
        if (!Files.exists(file)) return seeded();
        return deserialize(Files.readString(file, StandardCharsets.UTF_8));
    }
}
