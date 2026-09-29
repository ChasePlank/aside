package aside.games.inventory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * inventory -- the model.
 *
 * A workshop, cleared for sale. Eight objects, one card each, one name per
 * card. The objects do not come back once they are shelved: after the card
 * is written, the card IS the object, as far as anyone downstream is
 * concerned.
 *
 * The rule the game is built on: a name is not a description, it is a claim
 * about what the thing can be used for. The survey uses every card exactly
 * as it is written, and the object does what it is, which is not always the
 * same thing. Nothing in the collection can correct the inventory, because
 * the inventory is the only part of it that can speak.
 *
 * Two structural facts hold the puzzle up, and SelfTest asserts both:
 *
 *   1. Things kept in sets were kept for one job, so a set's two cards must
 *      name the same use. That is the one inference the collection offers
 *      about itself.
 *   2. Every object's three offered names include the true one. The game is
 *      never unfair; it is only ever under-informed.
 *
 * Nothing here is drawn. The separation is the point: the rule can be tested
 * without a screen, which is what SelfTest does.
 */
public final class Inventory {

    /** What a thing can be used for. Six words; four of them are in play. */
    public enum Use {
        VESSEL("a vessel", "it holds"),
        BLADE("a blade", "it cuts"),
        MEASURE("a measure", "it measures"),
        LAMP("a lamp", "it lights"),
        WEIGHT("a weight", "it weighs"),
        SEAL("a seal", "it seals");

        public final String label;
        public final String does;

        Use(String label, String does) {
            this.label = label;
            this.does = does;
        }
    }

    /** One object, and the card written for it. */
    public static final class Thing {
        public final int number;        // 1-based, as it is read out
        public final String form;       // what is in front of you
        public final String note;       // what was in the box with it, "" if nothing
        public final Use truth;         // what it is for. Never shown.
        public final Use[] offered;     // the three names on the card
        public final int mate;          // the other half of the set, -1 if none

        public Use written;             // null until the card is written

        Thing(int number, String form, String note, Use truth, int mate, Use... offered) {
            this.number = number;
            this.form = form;
            this.note = note;
            this.truth = truth;
            this.mate = mate;
            this.offered = offered;
        }

        public boolean named() { return written != null; }
        public boolean right() { return written == truth; }
        public boolean hasNote() { return note != null && !note.isEmpty(); }

        public String label() { return written == null ? "" : written.label; }

        /** The readings you did not take, as a working card would keep them. */
        public String otherReadings() {
            StringBuilder sb = new StringBuilder();
            for (Use u : offered) {
                if (u == written) continue;
                if (sb.length() > 0) sb.append(", ");
                sb.append(u.label);
            }
            return sb.toString();
        }

        /** Index into {@link #offered} of a use, or -1. */
        public int slot(Use u) {
            for (int i = 0; i < offered.length; i++) if (offered[i] == u) return i;
            return -1;
        }
    }

    public final List<Thing> things = new ArrayList<>();

    /** Index of the object in front of you; things.size() when the bench is clear. */
    public int next;

    Inventory() { }

    // ---- the collection ---------------------------------------------------

    /**
     * The workshop, in the order the objects are shelved.
     *
     * Mates are deliberately not adjacent: (1,5), (2,6), (3,7), (4,8). The
     * player who writes four cards and only then notices that sets exist has
     * already spent four commitments, and that is the game.
     */
    public static Inventory of() {
        Inventory inv = new Inventory();

        // -- the steel and the handle: a blade --
        inv.add("A flat bar of grey steel, a hand long. One edge has been ground "
                        + "away to nothing; the other is left rough. The ground edge is "
                        + "bright and the rest of it is not.",
                "", Use.BLADE, 4,
                Use.MEASURE, Use.BLADE, Use.VESSEL);

        // -- the disc and the tube: a measure --
        inv.add("A brass disc in a wooden case. A needle swings freely on its face "
                        + "and the scale is worn off at both ends, so only the middle of it "
                        + "can be read.",
                "It was true once, before the salt got into it.",
                Use.MEASURE, 5,
                Use.VESSEL, Use.SEAL, Use.MEASURE);

        // -- the bowl and the tin: a lamp --
        inv.add("A squat glass bowl, blackened inside. A brass collar sits at the "
                        + "rim and a wick has burned down to nothing in it.",
                "It was never much good. I kept it for the light.",
                Use.LAMP, 6,
                Use.SEAL, Use.LAMP, Use.WEIGHT);

        // -- the pan and the bucket: a vessel --
        inv.add("A shallow copper pan, hammered, with a lip at one end. A patch of "
                        + "solder sits near the base and the inside is scoured bright.",
                "I carried the water up from the creek in this.",
                Use.VESSEL, 7,
                Use.LAMP, Use.SEAL, Use.VESSEL);

        // -- the handle --
        inv.add("A short handle of dark wood, split along the grain, with a slot cut "
                        + "clean across the end. The slot is bright inside, as if something "
                        + "has been in and out of it a great many times.",
                "Kept with the steel one. Same job.",
                Use.BLADE, 0,
                Use.BLADE, Use.MEASURE, Use.WEIGHT);

        // -- the tube --
        inv.add("A glass tube sealed at both ends, half full of something dark. "
                        + "Tipped, the dark thing slides and settles, and it settles the "
                        + "same way every time.",
                "Never knew what this one was for. It came with the brass one.",
                Use.MEASURE, 1,
                Use.MEASURE, Use.VESSEL, Use.BLADE);

        // -- the tin of oil --
        inv.add("A tin of oil, half full, with a hinged lid that no longer closes. "
                        + "The tin is dented and the seam has been soldered twice.",
                "Bought with the glass one, the same week.",
                Use.LAMP, 2,
                Use.VESSEL, Use.MEASURE, Use.LAMP);

        // -- the bucket. The note is the trap: he used it as a doorstop, and
        //    the note is the only thing on the card that argues for a weight.
        //    The object itself argues the other way, and the object is right.
        inv.add("A wooden bucket, staved and banded, with a rope handle. The bands "
                        + "are tight and the wood has swollen into them. The inside is dark "
                        + "and clean and the staves have been scraped.",
                "A heavy thing, and no use for it. I kept the door open with it.",
                Use.VESSEL, 3,
                Use.WEIGHT, Use.VESSEL, Use.LAMP);

        return inv;
    }

    void add(String form, String note, Use truth, int mate, Use... offered) {
        things.add(new Thing(things.size() + 1, form, note, truth, mate, offered));
    }

    // ---- the bench --------------------------------------------------------

    public boolean finished() { return next >= things.size(); }

    public Thing current() { return finished() ? null : things.get(next); }

    public int written() {
        int n = 0;
        for (Thing t : things) if (t.named()) n++;
        return n;
    }

    public int right() {
        int n = 0;
        for (Thing t : things) if (t.right()) n++;
        return n;
    }

    public int wrong() { return things.size() - right(); }

    /**
     * Write the card for the object in front of you.
     *
     * Returns false if there is nothing in front of you or the card is
     * already written -- a card is written once, and that is the rule the
     * whole game rests on.
     */
    public boolean name(Use u) {
        if (finished()) return false;
        Thing t = things.get(next);
        if (t.named()) return false;
        t.written = u;
        next++;
        return true;
    }

    /** Write the card by the number of the offered name, 1-based. */
    public boolean nameBySlot(int slot) {
        if (finished()) return false;
        Thing t = things.get(next);
        if (slot < 1 || slot > t.offered.length) return false;
        return name(t.offered[slot - 1]);
    }

    public double progress() { return (double) written() / things.size(); }

    // ---- what the survey did ----------------------------------------------

    /**
     * What happens when the survey uses a thing as {@code named}.
     *
     * Written per object rather than per use, because the interesting part is
     * never "wrong name" in the abstract -- it is this pan, filled with oil,
     * with the solder letting go.
     */
    public static String outcome(int index, Use named) {
        return switch (index) {
            case 0 -> switch (named) {
                case BLADE -> "It cut the cord, and the twine, and the sacking.";
                case VESSEL -> "Water was poured onto it. The water ran off the flat and onto the floor.";
                case MEASURE -> "It was laid along the plank and marked. It was straight, and that was all it was.";
                default -> "It was not used.";
            };
            case 1 -> switch (named) {
                case MEASURE -> "It was set against the core and read in the middle of the scale, where the marks still are.";
                case SEAL -> "It was pressed into the jar's mouth. The mouth was wider than the disc.";
                case VESSEL -> "It was set on the bench and filled. It held a spoonful, and then it did not.";
                default -> "It was not used.";
            };
            case 2 -> switch (named) {
                case LAMP -> "It was filled and lit. It burned all night and had to be put out at dawn.";
                case SEAL -> "It was set over the jar. The collar did not fit and the oil went over.";
                case WEIGHT -> "It was put on the papers to hold them down. The glass broke on the second day.";
                default -> "It was not used.";
            };
            case 3 -> switch (named) {
                case VESSEL -> "It carried the water up from the creek, twice a day, and did not lose any of it.";
                case LAMP -> "It was filled with oil and a wick floated in it. It burned, and the solder let go.";
                case SEAL -> "It was set over the tin. The lip was the wrong way round and the rain got in.";
                default -> "It was not used.";
            };
            case 4 -> switch (named) {
                case BLADE -> "A blade was fitted into the slot and it held.";
                case MEASURE -> "It was laid along the core and marked. There were no marks on it to read.";
                case WEIGHT -> "It was put on the corner of the map. It was not heavy enough.";
                default -> "It was not used.";
            };
            case 5 -> switch (named) {
                case MEASURE -> "It was laid on the bench and the bead was watched. The bead settled, and the bench was level.";
                case VESSEL -> "It was filled. There was no way in and no way out.";
                case BLADE -> "It was drawn across the cord. The glass broke and the cord was not cut.";
                default -> "It was not used.";
            };
            case 6 -> switch (named) {
                case LAMP -> "The oil was poured into the bowl and burned, and the bowl was filled again.";
                case VESSEL -> "It was filled with water. The lid would not close and it went over on the first slope.";
                case MEASURE -> "It was set on the scale. The scale said eleven pounds and nothing else.";
                default -> "It was not used.";
            };
            case 7 -> switch (named) {
                case VESSEL -> "It carried the water up from the creek whenever the pan was in use, and did not lose any of it.";
                case WEIGHT -> "It held the door. The water was carried in a hat.";
                case LAMP -> "It was filled with oil and lit. The oil burned, and the bucket burned with it.";
                default -> "It was not used.";
            };
            default -> "It was not used.";
        };
    }

    /** What the survey did with the card for thing {@code i}. */
    public String outcomeFor(int i) {
        Thing t = things.get(i);
        if (!t.named()) return "There was no card, so it stayed in the crate.";
        return outcome(i, t.written);
    }

    // ---- the report -------------------------------------------------------

    public String headline() {
        int r = right();
        int n = things.size();
        if (r == n) return "The survey got what it came for.";
        if (r >= n - 2) return "The survey got most of what it came for.";
        if (r >= n / 2) return "The survey got some of it.";
        return "The survey got what you wrote.";
    }

    public String verdict() {
        return Word(things.size()) + " cards, and " + word(right()) + " of them said what the "
                + "thing was. The survey used every card exactly as it was written, because "
                + "that is what a card is for.";
    }

    public String closing() {
        int w = wrong();
        if (w == 0) {
            return "Every card said what the thing was, and out of a dead man's workshop the "
                    + "survey got a collection that works. That is the whole of what an inventory "
                    + "can do, and it is more than it sounds like.";
        }
        return Word(w) + " of the cards said something else, and the survey used those too, "
                + "exactly as written. The objects did what they were. Nothing in the collection "
                + "could correct the inventory, because the inventory was the only part of it "
                + "that could speak.";
    }

    static String word(int n) {
        String[] w = {"no", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"};
        return n >= 0 && n < w.length ? w[n] : String.valueOf(n);
    }

    /** The same word at the head of a sentence. */
    static String Word(int n) {
        String w = word(n);
        return w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1);
    }

    // ---- storage ----------------------------------------------------------

    /**
     * The bench is saved, not the collection -- the collection is fixed. A
     * half-written inventory reopens on the next object, not on the premise.
     */
    public void save(Path p) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < things.size(); i++) {
            if (i > 0) sb.append(',');
            Thing t = things.get(i);
            sb.append(t.written == null ? -1 : t.written.ordinal());
        }
        sb.append('\n');
        Files.writeString(p, sb.toString(), StandardCharsets.UTF_8);
    }

    public static Inventory load(Path p) throws IOException {
        Inventory inv = of();
        if (p == null || !Files.exists(p)) return inv;
        List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
        if (lines.isEmpty()) return inv;
        String[] parts = lines.get(0).trim().split(",");
        for (int i = 0; i < inv.things.size() && i < parts.length; i++) {
            String s = parts[i].trim();
            if (s.isEmpty()) continue;
            int o;
            try { o = Integer.parseInt(s); } catch (NumberFormatException e) { continue; }
            if (o >= 0 && o < Use.values().length) inv.things.get(i).written = Use.values()[o];
        }
        // Derive the bench position rather than trusting a stored one, so a
        // truncated or hand-edited save cannot put the bench past the end.
        inv.next = inv.things.size();
        for (int i = 0; i < inv.things.size(); i++) {
            if (!inv.things.get(i).named()) { inv.next = i; break; }
        }
        return inv;
    }
}
