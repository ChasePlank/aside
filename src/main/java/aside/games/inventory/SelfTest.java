package aside.games.inventory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Headless checks for inventory.
 *
 * The point of these is not coverage. It is that the two structural facts the
 * game rests on have to be true independently of anything drawn:
 *
 *   1. A set's two cards must be able to name the same use, and the offered
 *      names must always contain the true one. If either failed, the game
 *      would be unfair rather than merely under-informed, and no amount of
 *      looking at the screen would show it.
 *   2. No single key may be a winning strategy. If the true name sat in the
 *      same slot for most objects, a player could skip the reading entirely
 *      and still do well, which would make the whole exercise decorative.
 *
 * Run: java -cp classes aside.games.inventory.SelfTest
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
        content();
        theOfferedNames();
        theSets();
        theEvidence();
        theTrap();
        noKeyWins();
        writing();
        outcomes();
        theReport();
        storage();
        determinism();
        thePhoneBuild();
        System.out.println((checks - failed) + "/" + checks + " checks passed"
                + (failed == 0 ? "" : "  --  " + failed + " FAILED"));
        if (failed > 0) System.exit(1);
    }

    // ------------------------------------------------------------ content

    static void content() {
        Inventory inv = Inventory.of();
        eq(inv.things.size(), 8, "eight objects on the bench");
        ok(inv.written() == 0, "nothing is written before the first card");
        eq(inv.next, 0, "the bench starts at the first object");
        ok(!inv.finished(), "the bench is not clear before anything is written");

        Set<String> forms = new HashSet<>();
        Set<String> notes = new HashSet<>();
        for (int i = 0; i < inv.things.size(); i++) {
            Inventory.Thing t = inv.things.get(i);
            eq(t.number, i + 1, "object " + (i + 1) + " is numbered " + (i + 1));
            ok(t.form != null && t.form.length() > 30, "object " + t.number + " has a form to look at");
            ok(forms.add(t.form), "object " + t.number + " does not repeat another form");
            if (t.hasNote()) ok(notes.add(t.note), "object " + t.number + " does not repeat another note");
            ok(t.truth != null, "object " + t.number + " has a true use");
            ok(t.mate >= 0 && t.mate < inv.things.size(), "object " + t.number + " has a mate in the collection");
            ok(t.mate != i, "object " + t.number + " is not its own mate");
        }

        // The four uses in play, each on exactly two objects.
        int[] byUse = new int[Inventory.Use.values().length];
        for (Inventory.Thing t : inv.things) byUse[t.truth.ordinal()]++;
        for (Inventory.Use u : Inventory.Use.values()) {
            ok(byUse[u.ordinal()] % 2 == 0, u.label + " is on an even number of objects");
        }
        int inPlay = 0;
        for (Inventory.Use u : Inventory.Use.values()) if (byUse[u.ordinal()] > 0) inPlay++;
        eq(inPlay, 4, "four uses are in play");
    }

    static void theOfferedNames() {
        Inventory inv = Inventory.of();
        for (Inventory.Thing t : inv.things) {
            eq(t.offered.length, 3, "object " + t.number + " offers three names");
            Set<Inventory.Use> seen = new HashSet<>();
            for (Inventory.Use u : t.offered) {
                ok(u != null, "object " + t.number + " has no empty slot on its card");
                ok(seen.add(u), "object " + t.number + " does not offer " + u.label + " twice");
            }
            // The fairness rule: the right answer is always available.
            ok(t.slot(t.truth) >= 0, "object " + t.number + " offers the name that is true of it");
        }
    }

    static void theSets() {
        Inventory inv = Inventory.of();
        Set<Integer> seen = new HashSet<>();
        for (Inventory.Thing t : inv.things) {
            if (seen.contains(t.number)) continue;
            Inventory.Thing mate = inv.things.get(t.mate);
            seen.add(t.number);
            seen.add(mate.number);

            eq(mate.mate, t.number - 1, "object " + mate.number + " names object " + t.number + " back");
            eq(mate.truth, t.truth, "the set of " + t.number + " and " + mate.number + " was kept for one job");

            // The one inference the collection offers about itself: two cards
            // in a set must name the same use, so the true name has to be
            // among the names both cards offer.
            boolean shared = false;
            for (Inventory.Use u : t.offered) if (mate.slot(u) >= 0) shared = true;
            ok(shared, "the set of " + t.number + " and " + mate.number
                    + " has a name both cards can take");
            ok(mate.slot(t.truth) >= 0, "the true name of the set of " + t.number
                    + " and " + mate.number + " is on both cards");
        }
        eq(seen.size(), 8, "every object is in exactly one set");
    }

    static void theEvidence() {
        Inventory inv = Inventory.of();
        // Every object has to be readable from something in front of the
        // player: the object itself, or the note that was in the box with it.
        for (Inventory.Thing t : inv.things) {
            ok(!t.form.isEmpty() || t.hasNote(), "object " + t.number + " shows the player something");
        }
        // At least half the collection carries a note, or the notes stop
        // being evidence and become decoration.
        int noted = 0;
        for (Inventory.Thing t : inv.things) if (t.hasNote()) noted++;
        ok(noted >= 4, "at least half the objects came with a note (" + noted + ")");
        // And at least one object is readable from the object alone.
        int bare = 0;
        for (Inventory.Thing t : inv.things) if (!t.hasNote()) bare++;
        ok(bare >= 1, "at least one object has to be read off itself (" + bare + ")");
    }

    static void theTrap() {
        Inventory inv = Inventory.of();
        // The bucket: the note argues for a weight because that is what he
        // used it for, and the object argues for a vessel because that is
        // what it is. The object is right, and the player has to decide that.
        Inventory.Thing bucket = inv.things.get(7);
        eq(bucket.truth, Inventory.Use.VESSEL, "the bucket is a vessel");
        ok(bucket.slot(Inventory.Use.WEIGHT) >= 0, "the bucket's card offers a weight");
        ok(bucket.hasNote(), "the bucket's note is the trap and it has to be there");

        // The tin of oil: shaped like a vessel, kept for the lamp.
        Inventory.Thing tin = inv.things.get(6);
        eq(tin.truth, Inventory.Use.LAMP, "the tin of oil is a lamp");
        ok(tin.slot(Inventory.Use.VESSEL) >= 0, "the tin's card offers a vessel");

        // The pan: shaped like a lamp, kept for water.
        Inventory.Thing pan = inv.things.get(3);
        eq(pan.truth, Inventory.Use.VESSEL, "the pan is a vessel");
        ok(pan.slot(Inventory.Use.LAMP) >= 0, "the pan's card offers a lamp");

        // The one set that the collection solves by itself: the bowl and the
        // tin can only agree on the lamp, so a player who reads the catalogue
        // gets that pair without reading either object.
        Inventory.Thing bowl = inv.things.get(2);
        int shared = 0;
        for (Inventory.Use u : bowl.offered) if (tin.slot(u) >= 0) shared++;
        eq(shared, 1, "the bowl and the tin can only agree on one name");
    }

    static void noKeyWins() {
        Inventory inv = Inventory.of();
        int[] bySlot = new int[3];
        for (Inventory.Thing t : inv.things) bySlot[t.slot(t.truth)]++;
        for (int i = 0; i < 3; i++) {
            ok(bySlot[i] <= 3, "pressing " + (i + 1) + " every time wins at most three of eight ("
                    + bySlot[i] + ")");
            ok(bySlot[i] >= 1, "some object's true name is in slot " + (i + 1));
        }
        eq(bySlot[0] + bySlot[1] + bySlot[2], 8, "every true name is in one of the three slots");
    }

    // ------------------------------------------------------------ writing

    static void writing() {
        Inventory inv = Inventory.of();
        Inventory.Thing first = inv.current();
        eq(first.number, 1, "the first object is in front of you");
        ok(!first.named(), "its card is not written yet");

        ok(!inv.nameBySlot(0), "slot 0 is not a name");
        ok(!inv.nameBySlot(4), "slot 4 is not a name");
        ok(!first.named(), "a rejected name does not write a card");
        eq(inv.next, 0, "a rejected name does not move the bench on");

        ok(inv.nameBySlot(2), "slot 2 writes the card");
        ok(first.named(), "the card is written");
        eq(first.written, first.offered[1], "the card took the second name offered");
        eq(inv.next, 1, "the bench moves on");
        eq(inv.written(), 1, "one card written");

        // The bench normally cannot point at a written card, but the rule is
        // enforced in name() as well, because a card is written once.
        inv.next = 0;
        ok(!inv.name(Inventory.Use.LAMP), "a card cannot be written twice");
        eq(inv.next, 0, "and the bench does not move");
        eq(first.written, first.offered[1], "and the card still says what it said");
        inv.next = 1;

        // Write the rest, always taking the first name offered.
        while (!inv.finished()) ok(inv.nameBySlot(1), "the rest of the cards write");
        eq(inv.written(), 8, "all eight cards are written");
        ok(inv.finished(), "the bench is clear");
        ok(inv.current() == null, "there is nothing in front of you");
        ok(!inv.nameBySlot(1), "nothing can be written on a clear bench");

        // A perfect inventory.
        Inventory perfect = Inventory.of();
        for (int i = 0; i < 8; i++) perfect.name(perfect.things.get(i).truth);
        eq(perfect.right(), 8, "a perfect inventory gets all eight");
        eq(perfect.wrong(), 0, "and gets none wrong");
        ok(perfect.progress() == 1.0, "a perfect inventory is finished");
    }

    static void outcomes() {
        Inventory inv = Inventory.of();
        for (int i = 0; i < inv.things.size(); i++) {
            Inventory.Thing t = inv.things.get(i);
            Set<String> lines = new HashSet<>();
            for (Inventory.Use u : t.offered) {
                String line = Inventory.outcome(i, u);
                ok(line != null && line.length() > 20,
                        "object " + t.number + " used as " + u.label + " has something happen to it");
                ok(!line.equals("It was not used."),
                        "object " + t.number + " used as " + u.label + " has a line of its own");
                ok(lines.add(line), "object " + t.number + " used as " + u.label
                        + " does not read the same as another use");
                ok(!line.equals(Inventory.outcome(i, t.truth)) || u == t.truth,
                        "object " + t.number + " used as " + u.label + " is not the calm line");
            }
            // The calm line is the one for what the thing actually is.
            String calm = Inventory.outcome(i, t.truth);
            ok(calm.length() > 20, "object " + t.number + " has a line for being right");
            for (Inventory.Use u : t.offered) {
                if (u == t.truth) continue;
                ok(!calm.equals(Inventory.outcome(i, u)),
                        "object " + t.number + " used as " + u.label + " does not read as the calm line");
            }
        }

        // An unwritten card means the object never left the crate.
        ok(inv.outcomeFor(0).contains("crate"), "an unwritten card leaves the object in the crate");
        inv.nameBySlot(1);
        ok(!inv.outcomeFor(0).contains("crate"), "a written card is used");
        eq(inv.outcomeFor(0), Inventory.outcome(0, inv.things.get(0).written),
                "the report uses the card as written");
    }

    static void theReport() {
        Inventory perfect = Inventory.of();
        for (int i = 0; i < 8; i++) perfect.name(perfect.things.get(i).truth);
        ok(perfect.headline().contains("got what it came for"), "a perfect run reads as a perfect run");
        ok(perfect.closing().contains("Every card"), "a perfect run closes on the cards being right");
        ok(perfect.verdict().contains("eight"), "the verdict counts the cards");
        ok(perfect.verdict().contains("eight of them"), "the verdict counts the right ones");

        Inventory worst = Inventory.of();
        for (int i = 0; i < 8; i++) {
            Inventory.Thing t = worst.things.get(i);
            worst.name(t.offered[(t.slot(t.truth) + 1) % 3]);
        }
        eq(worst.right(), 0, "taking a wrong name every time gets none right");
        ok(worst.headline().contains("got what you wrote"), "a wrong run reads as a wrong run");
        ok(worst.closing().contains("Eight of the cards"), "a wrong run names how many were wrong");
        // And the sentence starts with a capital, because it starts a sentence.
        ok(Character.isUpperCase(worst.closing().charAt(0)), "the closing starts as a sentence");
        ok(Character.isUpperCase(perfect.verdict().charAt(0)), "the verdict starts as a sentence");

        // Every card appears in the report, written or not.
        for (int i = 0; i < worst.things.size(); i++) {
            ok(worst.outcomeFor(i) != null && worst.outcomeFor(i).length() > 10,
                    "card " + (i + 1) + " has an outcome in the report");
        }
    }

    // ------------------------------------------------------------ storage

    static void storage() throws Exception {
        Path dir = Files.createTempDirectory("inventory-selftest");
        Path p = dir.resolve("inventory.state");

        // A missing file is a fresh bench, not an error.
        Inventory fresh = Inventory.load(dir.resolve("nothing-here.state"));
        eq(fresh.written(), 0, "a missing save opens a fresh inventory");
        eq(fresh.next, 0, "and starts at the first object");

        Inventory inv = Inventory.of();
        inv.nameBySlot(3);
        inv.nameBySlot(1);
        inv.save(p);
        ok(Files.exists(p), "the bench is written to disk");

        Inventory back = Inventory.load(p);
        eq(back.written(), 2, "two cards came back");
        eq(back.next, 2, "the bench came back where it was left");
        eq(back.things.get(0).written, inv.things.get(0).written, "card 1 came back as written");
        eq(back.things.get(1).written, inv.things.get(1).written, "card 2 came back as written");
        ok(!back.finished(), "a half-written inventory is not finished");

        // A truncated save must not put the bench past the end.
        Files.writeString(p, "1,2\n");
        Inventory short1 = Inventory.load(p);
        eq(short1.written(), 2, "a truncated save keeps the cards it has");
        eq(short1.next, 2, "and puts the bench on the next object, not past it");
        ok(!short1.finished(), "a truncated save is not finished");

        Files.writeString(p, "");
        Inventory empty = Inventory.load(p);
        eq(empty.written(), 0, "an empty save opens a fresh inventory");
        eq(empty.next, 0, "and starts at the first object");

        Files.writeString(p, "-1,-1,-1,-1,-1,-1,-1,-1\n");
        Inventory none = Inventory.load(p);
        eq(none.written(), 0, "a save of nothing is nothing written");

        Files.writeString(p, "99\n");
        Inventory junk = Inventory.load(p);
        eq(junk.written(), 0, "a save with a use that does not exist is ignored");

        // A finished inventory comes back finished, and opens on the report.
        Inventory done = Inventory.of();
        for (int i = 0; i < 8; i++) done.name(done.things.get(i).truth);
        done.save(p);
        Inventory doneBack = Inventory.load(p);
        ok(doneBack.finished(), "a finished inventory comes back finished");
        eq(doneBack.right(), 8, "and comes back with all eight right");
    }

    static void determinism() {
        // The collection is the same every time. The game is the writing, not
        // the draw, so there is nothing to randomise and nothing to seed.
        Inventory a = Inventory.of(), b = Inventory.of();
        eq(a.things.size(), b.things.size(), "the collection is the same size every time");
        for (int i = 0; i < a.things.size(); i++) {
            eq(a.things.get(i).form, b.things.get(i).form, "object " + (i + 1) + " is the same object");
            eq(a.things.get(i).truth, b.things.get(i).truth, "object " + (i + 1) + " is for the same job");
            eq(a.things.get(i).mate, b.things.get(i).mate, "object " + (i + 1) + " is in the same set");
        }
        // And two inventories do not share their cards.
        a.nameBySlot(1);
        ok(!b.things.get(0).named(), "writing a card on one bench does not write it on another");
    }

    // ------------------------------------------------------- the phone build

    /** Every fixed line of prose the game says, in one list. */
    static List<String> fixedLines() {
        List<String> out = new ArrayList<>(List.of(
                Inventory.WORDMARK, Inventory.WHERE_OPEN, Inventory.WHERE_REPORT,
                Inventory.RULES_HEADING, Inventory.IN_FRONT, Inventory.THE_NOTE,
                Inventory.WHAT_DO_YOU_WRITE, Inventory.THE_CATALOGUE,
                Inventory.NOTHING_WRITTEN, Inventory.ALSO_READ_AS,
                Inventory.WHAT_YOU_WROTE, Inventory.WHAT_THE_SURVEY_DID,
                Inventory.NOTHING_ON_BENCH, Inventory.START_LINE,
                Inventory.START_BUTTON, Inventory.NO_CARD));
        // NOT_USED is deliberately absent: it is the model's default for a use
        // that is not on the card, and no card can name one, so the phone build
        // has no branch that could reach it.
        out.addAll(Inventory.OPENING);
        for (String[] r : Inventory.RULES) out.add(r[1]);
        // Slot 0 of both is blank on purpose -- the arrays are addressed by the
        // number itself -- so the lines start at 1.
        for (int i = 1; i <= 8; i++) {
            out.add(Inventory.cardWhere(i, 8));
            out.add(Inventory.writtenOf(i, 8));
        }
        return out;
    }

    /**
     * The prose is the model's.
     *
     * There are two builds now -- the JavaFX screen and the phone build -- and
     * a sentence kept in {@link InventoryScreen} is a sentence the phone build
     * does not have. Same rule as Testimony and Handoff.
     */
    static void theVoice() {
        Set<String> distinct = new HashSet<>();
        for (String s : fixedLines()) {
            ok(s != null && !s.isBlank(), "every fixed line is a line");
            ok(distinct.add(s), "no fixed line is written twice: " + s);
        }
        String screen = source("src/main/java/aside/games/inventory/InventoryScreen.java");
        if (screen == null) {
            System.out.println("       (no InventoryScreen.java from here -- run from the repository root)");
            return;
        }
        for (String s : fixedLines()) {
            // The wordmark is the one word the screen may also spell, because
            // the title is the game's name and not a sentence in it.
            if (s.equals(Inventory.WORDMARK)) continue;
            ok(!screen.contains("\"" + s + "\""),
                    "the screen does not hold its own copy of: " + s);
        }
    }

    /**
     * The single-file build is generated, not written, and a generated file
     * that has gone stale is worse than no file. Regenerate and compare; if
     * this fails, run aside.games.inventory.WebInventory from the root.
     */
    static void thePhoneBuild() throws Exception {
        theVoice();

        Path out = Path.of("web", "inventory.html");
        if (!Files.exists(out)) {
            System.out.println("       (no web/inventory.html from here -- run from the repository root)");
            return;
        }
        String generated;
        try {
            generated = WebInventory.html();
        } catch (Exception e) {
            System.out.println("       (no template from here: " + e.getMessage() + ")");
            return;
        }
        ok(generated.equals(Files.readString(out)),
                "web/inventory.html is current -- regenerate it with aside.games.inventory.WebInventory");

        for (String s : fixedLines()) {
            ok(generated.contains(WebInventory.str(s)), "the phone build carries: " + s);
        }
        for (Inventory.Use u : Inventory.Use.values()) {
            ok(generated.contains(WebInventory.str(u.label)),
                    "the phone build knows the word " + u.label);
            ok(generated.contains(WebInventory.str(u.does)),
                    "the phone build knows what " + u.label + " does");
        }
        Inventory inv = Inventory.of();
        for (int i = 0; i < inv.things.size(); i++) {
            Inventory.Thing t = inv.things.get(i);
            ok(generated.contains(WebInventory.str(t.form)),
                    "the phone build carries object " + t.number);
            if (t.hasNote()) {
                ok(generated.contains(WebInventory.str(t.note)),
                        "the phone build carries the note with object " + t.number);
            }
            for (Inventory.Use u : t.offered) {
                ok(generated.contains(WebInventory.str(Inventory.outcome(i, u))),
                        "the phone build carries what the survey did with " + t.number
                      + " named " + u.label);
            }
        }

        theTruthIsNotInIt(generated);
        theReportTables(generated);
    }

    /**
     * The one thing the phone build is not told.
     *
     * The game's subject is that a card is not a description of a thing. The
     * desktop model knows what each object is for and never shows it; this
     * build is not given it at all. It gets, per object, the three names, the
     * three outcomes and three bits saying which name was the true one -- so it
     * can say what the survey did and count how many cards were right, and it
     * cannot say what any object *is*.
     *
     * A check rather than a comment, because a future edit that adds `truth` to
     * the content would be a one-line change that nothing else would notice.
     */
    static void theTruthIsNotInIt(String generated) {
        ok(!generated.contains("\"truth\""), "the phone build has no truth field in it");
        ok(!generated.contains("\"mate\""), "and does not know which objects are in a set");

        // And what it does have is the answer bit, per offered name, and it
        // agrees with the model about which name that is.
        List<List<Integer>> said = saidBits(generated);
        eq(said.size(), 8, "the phone build has an answer bit for each object");
        Inventory inv = Inventory.of();
        for (int i = 0; i < Math.min(said.size(), inv.things.size()); i++) {
            Inventory.Thing t = inv.things.get(i);
            eq(said.get(i).size(), t.offered.length,
                    "object " + t.number + " has one bit per name on its card");
            for (int s = 0; s < Math.min(said.get(i).size(), t.offered.length); s++) {
                eq(said.get(i).get(s), t.offered[s] == t.truth ? 1 : 0,
                        "the phone build knows whether " + t.number + "'s name " + (s + 1)
                      + " was the true one");
            }
        }
    }

    /**
     * The report, read back out of the build and checked against the model.
     *
     * The three sentences the survey writes depend on nothing but how many
     * cards were right, so all nine answers of each are in the file and the
     * phone picks one. A table can be indexed along the wrong axis and every
     * entry can still be a real sentence -- that is the bug Outside shipped and
     * then found by driving both builds -- so this parses the tables and
     * compares them entry by entry rather than asking whether the file contains
     * the right sentences.
     */
    static void theReportTables(String generated) {
        int n = Inventory.of().things.size();
        List<String> headline = stringsOf(section(generated, "\"headline\":["));
        List<String> verdict = stringsOf(section(generated, "\"verdict\":["));
        List<String> closing = stringsOf(section(generated, "\"closing\":["));
        eq(headline.size(), n + 1, "the headline table covers every count");
        eq(verdict.size(), n + 1, "the verdict table covers every count");
        eq(closing.size(), n + 1, "the closing table covers every count");
        for (int r = 0; r <= n; r++) {
            if (r < headline.size()) {
                eq(headline.get(r), Inventory.headline(r, n),
                        "the phone build headlines " + r + " right the same way");
            }
            if (r < verdict.size()) {
                eq(verdict.get(r), Inventory.verdict(r, n),
                        "the phone build counts " + r + " right the same way");
            }
            if (r < closing.size()) {
                eq(closing.get(r), Inventory.closing(n - r, n),
                        "the phone build closes " + r + " right the same way");
            }
        }
    }

    /** The `said` arrays, in object order, read back out of the build. */
    static List<List<Integer>> saidBits(String generated) {
        List<List<Integer>> out = new ArrayList<>();
        int at = 0;
        while (true) {
            int k = generated.indexOf("\"said\":[", at);
            if (k < 0) break;
            int end = generated.indexOf(']', k);
            if (end < 0) break;
            List<Integer> bits = new ArrayList<>();
            for (String p : generated.substring(k + 8, end).split(",")) {
                if (!p.isBlank()) bits.add(Integer.parseInt(p.trim()));
            }
            out.add(bits);
            at = end;
        }
        return out;
    }

    /** The text between a marker and the first ']' after it. */
    static String section(String generated, String marker) {
        int at = generated.indexOf(marker);
        if (at < 0) throw new IllegalStateException("the build has no " + marker);
        int end = generated.indexOf(']', at);
        if (end < 0) throw new IllegalStateException(marker + " is never closed");
        return generated.substring(at + marker.length(), end);
    }

    /** The JSON strings in a fragment, unescaped, in order. */
    static List<String> stringsOf(String s) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) != '"') continue;
            StringBuilder b = new StringBuilder();
            i++;
            while (i < s.length() && s.charAt(i) != '"') {
                if (s.charAt(i) == '\\' && i + 1 < s.length()) {
                    char e = s.charAt(i + 1);
                    switch (e) {
                        case 'n' -> b.append('\n');
                        case 'r' -> b.append('\r');
                        case 't' -> b.append('\t');
                        case 'u' -> {
                            b.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                            i += 4;
                        }
                        default -> b.append(e);
                    }
                    i += 2;
                } else {
                    b.append(s.charAt(i));
                    i++;
                }
            }
            out.add(b.toString());
        }
        return out;
    }

    static String source(String path) {
        try {
            Path p = Path.of(path);
            return Files.exists(p) ? Files.readString(p) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
