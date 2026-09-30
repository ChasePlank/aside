package aside.games.residue;

import aside.game.Game;
import aside.game.Games;
import aside.ui.LibraryLayout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Headless checks for residue.
 *
 * Residue was ported in with "the 46 room assertions" and then those assertions
 * did not travel with it -- the ported package had no test of its own, so the
 * only thing keeping the room's rules true was that nobody had changed them.
 * This is that test, rebuilt, plus the two things the port made necessary:
 *
 *   1. The room's rules. Capacity three, a thing ages one step per visit, three
 *      unkept steps and it is gone, leaving something already here changes it
 *      rather than adding a second one, and a full room costs the oldest thing
 *      in it. If any of those stops being true the game is a different game.
 *   2. The prose is in the model, not in a screen. There are two builds now --
 *      the JavaFX screen and the phone build -- and a sentence kept in one of
 *      them is a sentence the other one does not have. Every line the phone
 *      build prints is asserted to come from Thing or Room.
 *   3. The phone build is current. It is generated, and a generated file that
 *      has gone stale is worse than no file: it is a second copy of the game
 *      quietly disagreeing with the first.
 *
 * Run: java -cp classes aside.games.residue.SelfTest
 */
public final class SelfTest {

    static int checks = 0, failed = 0;

    static void ok(boolean cond, String what) {
        checks++;
        if (!cond) { failed++; System.out.println("FAIL  " + what); }
    }

    static void eq(Object a, Object b, String what) {
        checks++;
        boolean same = a == null ? b == null : a.equals(b);
        if (!same) { failed++; System.out.println("FAIL  " + what + "  (got " + a + ", want " + b + ")"); }
    }

    public static void main(String[] args) throws Exception {
        theThings();
        theSeededRoom();
        capacityIsThree();
        ageing();
        leaving();
        decay();
        theArrivals();
        theClosing();
        theVoice();
        storage();
        determinism();
        registry();
        thePhoneBuild();
        System.out.println((checks - failed) + "/" + checks + " checks passed"
                + (failed == 0 ? "" : "  --  " + failed + " FAILED"));
        if (failed > 0) System.exit(1);
    }

    // -------------------------------------------------------------- content

    static void theThings() {
        Thing[] things = Thing.values();
        eq(things.length, 5, "five things in the room");
        Set<String> ids = new HashSet<>();
        for (Thing t : things) {
            ok(ids.add(t.id), t.id + " is not another thing's id");
            ok(t.kind != null, t.id + " is one of the three kinds of evidence");
            eq(t.gestures.size(), 3, t.id + " can be left three ways");
            Set<String> gids = new HashSet<>();
            for (Thing.Gesture g : t.gestures) {
                ok(gids.add(g.id), t.id + "/" + g.id + " is not one of its own gestures twice");
                // Short is fine -- "The lamp, on." is the whole of it. What is
                // not fine is a gesture with no words at all.
                ok(g.text != null && g.text.length() > 10, t.id + "/" + g.id + " has words for it");
            }
            ok(t.kindNote() != null && t.kindNote().length() > 40,
                    t.id + " says what kind of evidence it is");
            ok(Room.headLeaving(t).contains(t.id), "the beat head names the " + t.id);
        }
        // The asymmetry is the design: a body outlasts a message.
        ok(Thing.CUP.decay > Thing.WINDOW.decay, "a cup outlasts a mark in the window");
        ok(Thing.CHAIR.decay > Thing.BOOK.decay, "a chair outlasts a book left open");
        eq(Thing.LAMP.kind, Thing.Kind.STATE, "the lamp is set, not left");
        eq(Thing.LAMP.decay, 99, "and it does not decay, it waits to be changed");
    }

    static void theSeededRoom() {
        Room r = Room.seeded();
        eq(r.visits, 0, "a new room has not been visited");
        eq(r.traces.size(), 2, "and it is not empty -- somebody was here before you");
        Trace chair = r.traceOf(Thing.CHAIR);
        Trace book = r.traceOf(Thing.BOOK);
        ok(chair != null && book != null, "the chair and the book are what they left");
        eq(chair.gesture, "pulled", "the chair is pulled out from the wall");
        eq(book.gesture, "ownpage", "the book is open at the page they stopped at");
        ok(!chair.mine && !book.mine, "and neither of them is yours");
        // Different ages on purpose: one is nearly gone, one has just started
        // to go, so the first thing the room teaches is that things here were
        // already in motion before you arrived.
        ok(chair.age != book.age, "they are at different ages");
        ok(chair.age > book.age, "and the one that is nearly gone is the older");
    }

    static void capacityIsThree() {
        // Three, not five. Five things and five places would mean the choice of
        // what to leave is free. At three, leaving something new costs the
        // oldest thing in the room.
        eq(Room.CAPACITY, 3, "the room holds three things");
        ok(Room.CAPACITY < Thing.values().length, "and there are more things than places");
    }

    // --------------------------------------------------------------- ageing

    static void ageing() {
        Room r = Room.seeded();
        r.advance();
        eq(r.visits, 1, "walking in makes it one visit");
        eq(r.traceOf(Thing.CHAIR).age, 3, "the chair ages a step");
        eq(r.traceOf(Thing.BOOK).age, 2, "and so does the book");

        // The book was left at 1 with a decay of 3, so one more visit finishes
        // it. The chair was left at 2 with a decay of 5, so it has two visits
        // left in it. The two seeded things are at different ages on purpose,
        // and this is what that buys: the room loses something on the second
        // visit, before the player has done anything at all.
        r.advance();
        ok(r.traceOf(Thing.BOOK) == null, "the book is gone on the second visit");
        eq(r.traceOf(Thing.CHAIR).age, 4, "the chair is still here at four");
        r.advance();
        ok(r.traceOf(Thing.CHAIR) == null, "and is cleared out on arrival at five, not in front of you");
        eq(r.visits, 3, "three visits in");

        // The room empties if nobody keeps leaving anything, and that is not a
        // failure state -- it is the room after a long enough gap.
        ok(r.isEmpty(), "a room nobody comes back to empties out");
        eq(r.traces.size(), 0, "and holds nothing");
    }

    static void leaving() {
        Room r = Room.seeded();
        r.advance();

        Trace t = r.leave(Thing.CUP, "half", true);
        eq(t.age, 0, "what you leave is not yet old");
        eq(t.bornVisit, 1, "it was left on this visit");
        ok(t.mine, "and it is yours");
        eq(r.traces.size(), 3, "the room is now full");

        // Leaving a thing that is already here changes it rather than adding a
        // second one: you cannot have two chairs, and the lamp has one switch.
        r.leave(Thing.CHAIR, "turned", true);
        eq(r.traces.size(), 3, "leaving the chair again does not make a second chair");
        eq(r.traceOf(Thing.CHAIR).gesture, "turned", "it changes what the chair is doing");

        // Full: the oldest goes, and it goes because it was pushed out rather
        // than because it was old.
        List<Thing> before = new ArrayList<>();
        for (Trace x : r.traces) before.add(x.thing);
        Thing oldest = before.get(0);
        r.leave(Thing.WINDOW, "mark", true);
        eq(r.traces.size(), 3, "a full room stays full");
        ok(r.traceOf(oldest) == null, "and the oldest thing in it is the one that went");
        eq(r.traces.get(r.traces.size() - 1).thing, Thing.WINDOW, "the new thing is the newest");

        // Nothing is ever deleted for being old; it is deleted for being
        // pushed out, which is a different feeling and the one that is true.
        ok(r.traceOf(Thing.CHAIR) != null, "and the things that were not oldest are still here");
    }

    static void decay() {
        for (Thing t : Thing.values()) {
            for (Thing.Gesture g : t.gestures) {
                ok(t.describe(g.id, 0) != null, t.id + "/" + g.id + " is here when it is left");
                eq(t.describe(g.id, 0), g.text, t.id + "/" + g.id + " reads as itself at once");
                ok(t.describe(g.id, t.decay) == null, t.id + "/" + g.id + " is gone at its decay");
                ok(t.describe(g.id, t.decay + 5) == null, t.id + "/" + g.id + " stays gone");
                for (int age = 1; age < t.decay && age < 8; age++) {
                    String d = t.describe(g.id, age);
                    ok(d != null, t.id + "/" + g.id + " is still here at age " + age);
                    if (d != null) {
                        ok(d.startsWith(g.text), t.id + "/" + g.id + " at " + age + " is the same thing, older");
                    }
                }
            }
            ok(t.describe("no-such-gesture", 0) == null, t.id + " cannot be left a way it has not got");
        }
        // The decay lines are written as evidence rather than loss: the fog on
        // the window is not damage, it is proof that somebody else breathed in
        // here after you left.
        String fogged = Thing.WINDOW.describe("mark", 1);
        ok(fogged != null && fogged.contains("fogged"),
                "the mark in the window is described as disturbed, not as damaged");
    }

    // --------------------------------------------------------------- words

    static void theArrivals() {
        Set<String> seen = new HashSet<>();

        Room first = Room.seeded();
        first.advance();
        eq(first.arrival(), Room.ARRIVAL_FIRST, "the first visit says you are not the first");
        ok(seen.add(first.arrival()), "and it is its own line");

        Room empty = Room.seeded();
        for (int i = 0; i < 8; i++) empty.advance();
        ok(empty.isEmpty(), "a room can be empty");
        eq(empty.arrival(), Room.ARRIVAL_EMPTY, "and says so");
        ok(seen.add(empty.arrival()), "and that is its own line");

        Room on = Room.seeded();
        on.advance();
        on.leave(Thing.LAMP, "on", false);
        on.advance();
        eq(on.arrival(), Room.ARRIVAL_LAMP_ON, "a lamp left on is its own arrival");
        ok(seen.add(on.arrival()), "and it is its own line");

        Room off = Room.seeded();
        off.advance();
        off.leave(Thing.LAMP, "off", false);
        off.advance();
        eq(off.arrival(), Room.ARRIVAL_LAMP_OFF, "a lamp left off is its own arrival");
        ok(seen.add(off.arrival()), "and it is its own line");

        // Something of yours, gone enough that you do not recognise it.
        Room aging = Room.seeded();
        aging.advance();
        aging.leave(Thing.CUP, "half", true);
        aging.advance();
        aging.advance();
        eq(aging.arrival(), Room.ARRIVAL_MINE_AGING, "your own thing, older than you remember");
        ok(seen.add(aging.arrival()), "and it is its own line");

        // Something of yours, from last time.
        Room mine = Room.seeded();
        mine.advance();
        mine.leave(Thing.CUP, "half", true);
        mine.advance();
        eq(mine.arrival(), Room.ARRIVAL_MINE, "something of yours from last time");
        ok(seen.add(mine.arrival()), "and it is its own line");

        // Somebody else's, and none of it is yours.
        Room theirs = Room.seeded();
        theirs.advance();
        theirs.leave(Thing.CUP, "half", false);
        theirs.advance();
        eq(theirs.arrival(), Room.ARRIVAL_SOMEONE, "somebody else's, and not yours");
        ok(seen.add(theirs.arrival()), "and it is its own line");

        eq(seen.size(), 7, "seven arrivals, and no two of them say the same thing");
    }

    static void theClosing() {
        Room r = Room.seeded();
        r.advance();
        eq(r.closing(), "2 of 3 places taken.", "a room with room in it counts what is in it");
        r.leave(Thing.CUP, "half", true);
        eq(r.closing(), Room.CLOSING_FULL, "a full room says what full means");
        ok(Room.CLOSING_FULL.indexOf("of 3") < 0, "and it does not say '3 of 3', which is a readout");
    }

    /**
     * The prose is the model's.
     *
     * This is the check the port made necessary. There are two builds now, and
     * the phone build cannot read a sentence that lives in ResidueScreen -- so
     * every line it prints has to be reachable from Thing or Room. If somebody
     * puts a line back into the screen, the phone build silently loses it, and
     * this is the test that would have caught it.
     */
    static void theVoice() {
        String[] lines = {Room.ARRIVAL_FIRST, Room.ARRIVAL_EMPTY, Room.ARRIVAL_LAMP_ON,
                Room.ARRIVAL_LAMP_OFF, Room.ARRIVAL_MINE_AGING, Room.ARRIVAL_MINE,
                Room.ARRIVAL_SOMEONE, Room.CLOSING_FULL, Room.NOTHING_SURVIVED,
                Room.DEPARTURE_NOTHING_LEFT, Room.HEAD_PICK, Room.HEAD_DEPARTURE,
                Room.ALREADY_HERE};
        Set<String> distinct = new HashSet<>();
        for (String s : lines) {
            ok(s != null && !s.isBlank(), "every fixed line is a line: " + s);
            ok(distinct.add(s), "and no two of them are the same line");
        }
        // The screen must not be holding a second copy of any of them.
        String screen = screenSource();
        if (screen != null) {
            for (String s : lines) {
                ok(!screen.contains(s), "the screen does not hold its own copy of: " + s);
            }
            // The screen may *call* kindNote(); what it may not do is own one.
            ok(!screen.contains("String kindNote"), "and the kind notes are the model's, not the screen's");
        }
    }

    /** The screen's source, if this is being run from a checkout. */
    static String screenSource() {
        Path p = Path.of("src", "main", "java", "aside", "games", "residue", "ResidueScreen.java");
        try {
            return Files.exists(p) ? Files.readString(p) : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------- storage

    static void storage() {
        Room r = Room.seeded();
        r.advance();
        r.leave(Thing.CUP, "half", true);
        r.advance();
        r.leave(Thing.WINDOW, "name", true);

        String text = r.serialize();
        ok(text.startsWith("# residue room v1"), "the save says what it is");
        ok(text.contains("\t"), "and is plain text, tab separated, one fact per line");

        Room back = Room.deserialize(text);
        eq(back.visits, r.visits, "the visit count survives the round trip");
        eq(back.traces.size(), r.traces.size(), "so does everything in the room");
        for (int i = 0; i < r.traces.size(); i++) {
            Trace a = r.traces.get(i), b = back.traces.get(i);
            eq(b.thing, a.thing, "trace " + i + " is the same thing");
            eq(b.gesture, a.gesture, "trace " + i + " is the same gesture");
            eq(b.age, a.age, "trace " + i + " is the same age");
            eq(b.mine, a.mine, "trace " + i + " is still yours or still not");
            eq(b.bornVisit, a.bornVisit, "trace " + i + " remembers when it was left");
        }
        eq(back.serialize(), text, "and writing it out again gives the same file");

        // A save from a future version, or a corrupted one, must not crash the
        // room -- an unknown thing is dropped, not fatal.
        Room junk = Room.deserialize("# residue room v1\nvisits\t4\ntrace\tghost\tboo\t0\tyou\t4\n");
        eq(junk.visits, 4, "a save with a thing this build has never heard of still loads");
        eq(junk.traces.size(), 0, "and drops the thing it cannot place");
        eq(Room.deserialize("").visits, 0, "an empty save is an empty room");
    }

    static void determinism() {
        Room a = Room.seeded(), b = Room.seeded();
        for (Room r : List.of(a, b)) {
            r.advance();
            r.leave(Thing.CUP, "half", true);
            r.advance();
            r.leave(Thing.LAMP, "low", true);
            r.advance();
            r.leave(Thing.BOOK, "held", true);
        }
        eq(a.serialize(), b.serialize(), "the same visits give the same room");
        eq(a.arrival(), b.arrival(), "and the same arrival");
        // Two rooms are two rooms.
        a.advance();
        ok(!a.serialize().equals(b.serialize()), "and a visit in one is not a visit in the other");
    }

    // ------------------------------------------------------------ registry

    static void registry() {
        Game g = Games.byId("residue");
        ok(g != null, "the engine can find residue by id");
        if (g == null) return;
        eq(g.title(), "Residue", "and it is called Residue");
        ok(g.blurb() != null && g.blurb().length() > 20, "and it has a line for the library");

        int rows = Games.all().size() + 1;
        ok(rows <= LibraryLayout.maxRows(),
                "the library holds " + rows + " rows (limit " + LibraryLayout.maxRows() + ")");
    }

    // ------------------------------------------------------- the phone build

    /**
     * The single-file build is generated, not written, and a generated file
     * that has gone stale is worse than no file: it is a second copy of the
     * game quietly disagreeing with the first. So the test regenerates it and
     * compares. If this fails, run aside.games.residue.WebResidue from the
     * repository root.
     */
    static void thePhoneBuild() throws Exception {
        Path out = Path.of("web", "residue.html");
        if (!Files.exists(out)) {
            System.out.println("       (no web/residue.html from here -- run from the repository root)");
            return;
        }
        String generated;
        try {
            generated = WebResidue.html();
        } catch (Exception e) {
            System.out.println("       (no template from here: " + e.getMessage() + ")");
            return;
        }
        String checkedIn = Files.readString(out);
        ok(generated.equals(checkedIn),
                "web/residue.html is current -- regenerate it with aside.games.residue.WebResidue");

        // And it has to carry the writing, not just be the right size.
        for (Thing t : Thing.values()) {
            ok(generated.contains("\"" + t.id + "\""), "the phone build carries the " + t.id);
            ok(generated.contains(t.kindNote()), "the phone build says what kind of evidence the " + t.id + " is");
            ok(generated.contains(Room.headLeaving(t)), "the phone build names the " + t.id + " when you leave it");
            for (Thing.Gesture g : t.gestures) {
                ok(generated.contains(g.text), "the phone build carries the " + t.id + " " + g.id);
            }
            for (int age = 1; age < t.decay; age++) {
                String line = t.decayText(age);
                if (line != null) {
                    ok(generated.contains(line), "the phone build carries the " + t.id + " at age " + age);
                }
            }
        }
        for (Trace t : Room.seeded().traces) {
            ok(generated.contains("\"" + t.thing.id + "\""), "the phone build seeds the room with the " + t.thing.id);
        }
        ok(generated.contains(Room.ARRIVAL_FIRST), "the phone build carries the first arrival");
        ok(generated.contains(Room.ARRIVAL_EMPTY), "the phone build carries the empty arrival");
        ok(generated.contains(Room.ARRIVAL_LAMP_ON), "the phone build carries the lamp-on arrival");
        ok(generated.contains(Room.ARRIVAL_LAMP_OFF), "the phone build carries the lamp-off arrival");
        ok(generated.contains(Room.ARRIVAL_MINE_AGING), "the phone build carries the arrival that forgot its own");
        ok(generated.contains(Room.ARRIVAL_MINE), "the phone build carries the arrival with your own thing in it");
        ok(generated.contains(Room.ARRIVAL_SOMEONE), "the phone build carries the arrival with somebody else's");
        ok(generated.contains(Room.CLOSING_FULL), "the phone build carries the full-room closing");
        ok(generated.contains(Room.NOTHING_SURVIVED), "the phone build carries the empty room");
        ok(generated.contains(Room.DEPARTURE_NOTHING_LEFT), "the phone build carries the departure that leaves nothing");
        ok(generated.contains(Room.HEAD_PICK), "the phone build carries the beat where you choose");
        ok(generated.contains(Room.HEAD_DEPARTURE), "the phone build carries the beat where you go");
        ok(generated.contains(Room.ALREADY_HERE), "the phone build carries the note on a thing already here");
        ok(generated.contains("capacity"), "the phone build is told how many things the room holds");

        // The save format is the same one on both sides, so a room can be
        // carried between the desktop and the phone by copying six lines.
        ok(generated.contains("residue room v1"), "the phone build writes the same save file the desktop does");

        // The content is embedded in a script tag, so nothing in the prose may
        // be able to end the block early.
        ok(!generated.contains("</script>") || generated.indexOf("</script>") > generated.lastIndexOf("const C ="),
                "nothing in the prose can end the script block early");
        ok(generated.contains("\\u003c") || !generated.contains("const C = {\"<"),
                "and the JSON writer escapes what could");

        System.out.println("       phone build: " + (generated.length() / 1024) + " KB, current");
    }
}
