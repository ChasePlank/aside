package aside.games.handoff;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Headless checks for Handoff.
 *
 * Not coverage. These check the design claims: that Bell's lines are
 * general and yours are specific, that following an order and judging the
 * situation are different actions, that the order you write the lines in
 * decides what he does, that a concern nothing applies to is a concern he
 * does nothing about, and that giving him Bell's four gives him a night
 * that is not Bell's.
 *
 * Run: java -cp classes aside.games.handoff.SelfTest
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
        if (!same) {
            failed++;
            System.out.println("FAIL  " + what + "  (got " + a + ", want " + b + ")");
        }
    }

    public static void main(String[] args) throws Exception {
        shape();
        signals();
        watching();
        breadth();
        succession();
        orderMatters();
        silence();
        persistence();
        playthroughs();
        thePhoneBuild();
        System.out.println((checks - failed) + "/" + checks + " checks passed"
                + (failed == 0 ? "" : "  --  " + failed + " FAILED"));
        if (failed > 0) System.exit(1);
    }

    // ------------------------------------------------------------ helpers

    static List<Handoff.Order> orders(String... ids) {
        List<Handoff.Order> out = new ArrayList<>();
        for (String id : ids) {
            Handoff.Order o = Handoff.orderById(id);
            if (o == null) throw new IllegalArgumentException("no order " + id);
            out.add(o);
        }
        return out;
    }

    static Handoff.Event eventFor(Handoff.Succession s, Handoff.Concern c) {
        for (Handoff.Event e : s.events) if (e.concern == c) return e;
        return null;
    }

    static Handoff played(int option) {
        Handoff h = new Handoff();
        while (h.phase == Handoff.Phase.WATCH) {
            h.choose(option);
            h.advance();
        }
        return h;
    }

    // -------------------------------------------------------------- shape

    static void shape() {
        eq(Handoff.WATCHES.size(), 5, "five watches");
        eq(Handoff.BELL.size(), 4, "Bell left four lines");
        eq(Handoff.ORDERS_HELD, 3, "the orders hold three lines");

        Set<String> watchTitles = new HashSet<>();
        for (Handoff.Watch w : Handoff.WATCHES) {
            ok(watchTitles.add(w.title), "watch title unique: " + w.title);
            eq(w.options.size(), 3, w.title + " offers three ways");
            Set<Handoff.Kind> kinds = new HashSet<>();
            for (Handoff.Option o : w.options) kinds.add(o.kind);
            eq(kinds.size(), 3, w.title + " offers follow, judge and hold");
            ok(w.scene != null && !w.scene.isBlank(), w.title + " has a scene");
        }

        // Every line has a distinct id, and every earned line is earned by
        // exactly one option somewhere.
        Set<String> ids = new HashSet<>();
        for (Handoff.Order o : Handoff.allOrders()) {
            ok(ids.add(o.id), "order id unique: " + o.id);
            ok(o.text != null && !o.text.isBlank(), o.id + " has words");
            ok(o.origin != null && !o.origin.isBlank(), o.id + " says where it came from");
        }
        eq(ids.size(), 9, "nine lines exist in total");

        Set<Handoff.Order> earnable = new HashSet<>();
        for (Handoff.Watch w : Handoff.WATCHES) {
            for (Handoff.Option o : w.options) {
                if (o.unlock == null) continue;
                ok(!o.unlock.bell, "you never earn one of Bell's lines");
                ok(earnable.add(o.unlock), "only one option earns " + o.unlock.id);
            }
        }
        eq(earnable.size(), 5, "five lines can be earned");

        // Every earned line is earned by a JUDGE, and the judge is never the
        // line Bell already wrote -- otherwise there would be nothing to learn.
        for (Handoff.Watch w : Handoff.WATCHES) {
            for (Handoff.Option o : w.options) {
                if (o.unlock == null) continue;
                eq(o.kind, Handoff.Kind.JUDGE, o.unlock.id + " is earned by judging");
            }
        }
    }

    static void signals() {
        for (Handoff.Order o : Handoff.allOrders()) {
            ok(!o.requires.isEmpty(), o.id + " names at least one signal");
            for (String r : o.requires) {
                ok(Handoff.signal(r) != null, o.id + " names a real signal: " + r);
            }
        }
        for (Handoff.Watch w : Handoff.WATCHES) {
            for (String s : w.signals) {
                ok(Handoff.signal(s) != null, w.title + " names a real signal: " + s);
            }
        }
        for (String s : Handoff.SUCCESSION_SIGNALS) {
            ok(Handoff.signal(s) != null, "his night names a real signal: " + s);
        }
    }

    // ------------------------------------------------------------ watching

    static void watching() {
        // Each watch raises exactly one of Bell's lines. That is what makes
        // the five watches a sequence rather than a pile.
        for (Handoff.Watch w : Handoff.WATCHES) {
            List<Handoff.Order> f = Handoff.firing(Handoff.BELL, w.signalSet());
            eq(f.size(), 1, w.title + " raises exactly one of Bell's lines");
        }

        Handoff h = new Handoff();
        eq(h.applying().id, "B1", "the first watch raises the wick");
        h.choose(1); h.advance();
        eq(h.applying().id, "B2", "the second watch raises the door");
        h.choose(1); h.advance();
        eq(h.applying().id, "B1", "the third watch raises the wick again");
        h.choose(1); h.advance();
        eq(h.applying().id, "B3", "the fourth watch raises the vessel");
        h.choose(1); h.advance();
        eq(h.applying().id, "B4", "the fifth watch raises the stair");

        // Following the line and doing what the night needs are different
        // actions on every watch. If they were ever the same, that watch
        // would not be a choice.
        for (Handoff.Watch w : Handoff.WATCHES) {
            Handoff.Option follow = null, judge = null, hold = null;
            for (Handoff.Option o : w.options) {
                if (o.kind == Handoff.Kind.FOLLOW) follow = o;
                if (o.kind == Handoff.Kind.JUDGE) judge = o;
                if (o.kind == Handoff.Kind.HOLD) hold = o;
            }
            ok(follow != null && judge != null && hold != null, w.title + " has all three");
            ok(!follow.text.equals(judge.text), w.title + ": obeying and judging differ");
        }

        // The second watch is the one where Bell's line is right, so obeying
        // it earns nothing. Without that watch the game would just be an
        // argument that old orders are wrong.
        Handoff.Option follow2 = null;
        for (Handoff.Option o : Handoff.WATCHES.get(1).options) {
            if (o.kind == Handoff.Kind.FOLLOW) follow2 = o;
        }
        ok(follow2 != null && follow2.unlock == null,
                "obeying on the second watch teaches nothing, because it was right");
    }

    static void breadth() {
        // The tradeoff in one line of arithmetic: Bell's lines are general,
        // yours are specific.
        for (Handoff.Order o : Handoff.BELL) {
            eq(o.breadth(), 1, o.id + " is a general line");
        }
        for (Handoff.Order o : Handoff.allOrders()) {
            if (o.bell) continue;
            eq(o.breadth(), 2, o.id + " is a line about one night");
        }

        // And the specificity bites: every earned line is narrower than the
        // Bell line it replaces.
        eq(Handoff.orderById("E1").breadth() > Handoff.orderById("B1").breadth(), true,
                "E1 is narrower than B1");
        eq(Handoff.orderById("E2").breadth() > Handoff.orderById("B2").breadth(), true,
                "E2 is narrower than B2");
        eq(Handoff.orderById("E4").breadth() > Handoff.orderById("B3").breadth(), true,
                "E4 is narrower than B3");
    }

    // ---------------------------------------------------------- succession

    static void succession() {
        Set<String> present = new java.util.LinkedHashSet<>(Handoff.SUCCESSION_SIGNALS);
        Set<Handoff.Concern> raised = Handoff.raisedConcerns(present);
        eq(raised.size(), 3, "his night raises three concerns");
        ok(raised.contains(Handoff.Concern.LAMP), "his night raises the lamp");
        ok(raised.contains(Handoff.Concern.DOOR), "his night raises the door");
        ok(raised.contains(Handoff.Concern.VESSEL), "his night raises the vessel");
        ok(!raised.contains(Handoff.Concern.STAIR), "his night does not raise the stair");

        // The best writing available: the two sharp lines plus the one of
        // Bell's that still holds. Three concerns, three lines, all right.
        Handoff.Succession best = Handoff.succeed(orders("E1", "E4", "B2"));
        eq(best.good, 3, "E1, E4, B2 meets everything");
        eq(best.bad, 0, "and nothing goes wrong");
        eq(best.unmet, 0, "and nothing is left unanswered");
        eq(best.raised, 3, "against three raised concerns");
        ok(!best.allBell, "that is not Bell's night");

        // Bell's four, cut to three, gives him Bell's night -- and Bell's
        // night is not his night.
        Handoff.Succession bells = Handoff.succeed(orders("B1", "B2", "B3"));
        ok(bells.allBell, "three of Bell's lines is still Bell's");
        ok(bells.closing.contains("Bell's night"), "and the game says so");
        eq(bells.good, 1, "only the door survives the drift");
        eq(bells.bad, 2, "the lamp and the vessel do not");

        // Bell's lines, individually, on his night.
        eq(eventFor(Handoff.succeed(orders("B1", "B2", "B3")), Handoff.Concern.LAMP).good,
                false, "B1 misfires on his night");
        eq(eventFor(Handoff.succeed(orders("B1", "B2", "B3")), Handoff.Concern.VESSEL).good,
                false, "B3 misfires on his night");
        eq(eventFor(Handoff.succeed(orders("B1", "B2", "B3")), Handoff.Concern.DOOR).good,
                true, "B2 holds on his night");

        // The trap: a player who judged well every watch has five sharp
        // lines and three slots, and the sharpest ones are about their own
        // nights. E2 and E5 never fire on his night at all.
        Handoff.Succession overfit = Handoff.succeed(orders("E2", "E3", "E5"));
        eq(overfit.good, 1, "overfitting to your own nights meets one thing");
        eq(overfit.unmet, 2, "and leaves two unanswered");

        // Every earned line that does fire on his night is right. The lines
        // you earn are lessons, not traps.
        for (String id : List.of("E1", "E3", "E4")) {
            Handoff.Succession s = Handoff.succeed(orders(id, "B2", "B3"));
            boolean anyGood = false;
            for (Handoff.Event e : s.events) {
                if (e.by != null && e.by.id.equals(id)) anyGood = e.good;
            }
            ok(anyGood, id + " is right when it fires");
        }

        // No line at all is a night where nothing happens, which is not the
        // same as a night where nothing needed doing.
        Handoff.Succession none = Handoff.succeed(new ArrayList<>());
        eq(none.good, 0, "no orders meets nothing");
        eq(none.unmet, 3, "and leaves all three unanswered");
        eq(none.events.size(), 3, "and says so three times");
    }

    static void orderMatters() {
        // He reads them in the order you wrote them. Same two lines, two
        // different nights.
        Handoff.Succession b1first = Handoff.succeed(orders("B1", "E1", "B2"));
        Handoff.Succession e1first = Handoff.succeed(orders("E1", "B1", "B2"));

        Handoff.Event lampA = null, lampB = null;
        for (Handoff.Event e : b1first.events) if (e.concern == Handoff.Concern.LAMP) lampA = e;
        for (Handoff.Event e : e1first.events) if (e.concern == Handoff.Concern.LAMP) lampB = e;

        ok(lampA != null && lampA.by != null && lampA.by.id.equals("B1"),
                "written first, the general line wins");
        ok(lampB != null && lampB.by != null && lampB.by.id.equals("E1"),
                "written first, the sharp line wins");
        eq(lampA.good, false, "and the general line is wrong on his night");
        eq(lampB.good, true, "and the sharp line is right");
        eq(b1first.good, 1, "order one scores one: the lamp is wrong and the vessel is unwritten");
        eq(e1first.good, 2, "order two scores two: the lamp is right and the vessel is unwritten");

        // One action per concern, no matter how many lines apply to it.
        int lampEvents = 0;
        for (Handoff.Event e : e1first.events) {
            if (e.concern == Handoff.Concern.LAMP) lampEvents++;
        }
        eq(lampEvents, 1, "he does one thing about the lamp, not two");
    }

    static void silence() {
        // A line that does not apply is not a line that does nothing -- it
        // is a line that was never read. E2 needs a west wind and his night
        // has none.
        Handoff.Succession s = Handoff.succeed(orders("E2", "B2", "E1"));
        boolean sawE2 = false;
        for (Handoff.Event e : s.events) {
            if (e.by != null && e.by.id.equals("E2")) sawE2 = true;
        }
        ok(!sawE2, "E2 never fires on a night with no west wind");
        eq(s.good, 2, "and B2 covers the door anyway");

        // E5 needs a dark stair and his night has no stair.
        Handoff.Succession s2 = Handoff.succeed(orders("E5", "B1", "B3"));
        boolean sawE5 = false;
        for (Handoff.Event e : s2.events) {
            if (e.by != null && e.by.id.equals("E5")) sawE5 = true;
        }
        ok(!sawE5, "E5 never fires on a night with no stair");
    }

    // --------------------------------------------------------- persistence

    static void persistence() throws Exception {
        Path dir = Files.createTempDirectory("handoff-test");
        Path f = dir.resolve("orders.state");

        Handoff h = new Handoff();
        h.choose(1); h.advance();
        h.choose(0); h.advance();
        h.choose(1); h.advance();
        h.choose(1); h.advance();
        h.choose(2); h.advance();
        eq(h.phase, Handoff.Phase.WRITING, "five watches ends in writing");
        eq(h.unlocked.size(), 3, "three lines earned along the way");

        ok(h.write(Handoff.orderById("E1")), "write E1");
        ok(h.write(Handoff.orderById("E4")), "write E4");
        ok(!h.write(Handoff.orderById("E1")), "cannot write the same line twice");
        ok(h.write(Handoff.orderById("B2")), "write B2");
        ok(!h.write(Handoff.orderById("B3")), "cannot write a fourth line");
        eq(h.written.size(), 3, "three lines written");
        h.unwrite();
        eq(h.written.size(), 2, "and one taken back");
        ok(!h.seal(), "cannot seal with two lines");
        h.write(Handoff.orderById("B2"));
        ok(h.seal(), "seals with three");
        eq(h.phase, Handoff.Phase.SUCCESSION, "and moves to his night");
        h.save(f);
        h.finish();
        h.save(f);

        Handoff back = Handoff.load(f);
        eq(back.phase, Handoff.Phase.DONE, "a finished game reopens finished");
        eq(back.written.size(), 3, "with the orders you left");
        eq(back.written.get(0).id, "E1", "in the order you wrote them");
        eq(back.written.get(2).id, "B2", "all three, in order");
        eq(back.unlocked.size(), 3, "and the lines you earned");

        // A save that says it is writing but already holds three lines is
        // really at the night after you.
        Files.writeString(f, "phase WRITING\nwatch 4\nwrote E1\nwrote E4\nwrote B2\n");
        eq(Handoff.load(f).phase, Handoff.Phase.SUCCESSION,
                "a full set of orders reopens at his night");

        // A corrupt record is skipped, not fatal.
        Files.writeString(f, "phase WATCH\nwatch not-a-number\nunlocked NOPE\nwrote E1\n"
                + "this line is rubbish\nwrote E1\n");
        Handoff rough = Handoff.load(f);
        eq(rough.watchIndex, 0, "a bad watch number leaves the watch alone");
        eq(rough.written.size(), 1, "a repeated order is written once");
        eq(rough.phase, Handoff.Phase.WATCH, "and the game still opens");

        Files.deleteIfExists(f);
        Files.deleteIfExists(dir);
    }

    // --------------------------------------------------------- playthroughs

    static void playthroughs() {
        // Judge every time: five sharp lines, three slots, and the pool is
        // nine. The squeeze is the game.
        Handoff judge = played(1);
        eq(judge.unlocked.size(), 5, "judging every watch earns five lines");
        eq(judge.pool().size(), 9, "so the pool is nine lines");
        eq(judge.taken.get(0), 1, "and the first watch was judged");

        // Follow every time: you learn nothing you did not already have.
        Handoff follow = played(0);
        eq(follow.unlocked.size(), 0, "obeying every watch earns nothing");
        eq(follow.pool().size(), 4, "so the pool is only Bell's four");

        // Hold every time: same, and the nights go badly.
        Handoff hold = played(2);
        eq(hold.unlocked.size(), 0, "waiting every watch earns nothing");

        // The best available ending, reached by judging the lamp and the
        // vessel and keeping Bell's line about the door.
        Handoff best = played(1);
        best.write(Handoff.orderById("E1"));
        best.write(Handoff.orderById("E4"));
        best.write(Handoff.orderById("B2"));
        best.seal();
        Handoff.Succession s = best.succession();
        eq(s.good, 3, "the best ending is reachable");
        ok(s.closing.contains("Nothing went wrong"), "and it says the true thing about it");

        // Reset puts it back.
        best.reset();
        eq(best.phase, Handoff.Phase.WATCH, "reset returns to the first watch");
        eq(best.written.size(), 0, "and clears the orders");
        eq(best.unlocked.size(), 0, "and the lines earned");
        eq(best.taken.size(), 0, "and the watches kept");
    }

    // ------------------------------------------------------- the phone build

    /** Every fixed line of prose the game says, in one list. */
    static List<String> fixedLines() {
        return new ArrayList<>(List.of(
                Handoff.WORDMARK, Handoff.SUBTITLE,
                Handoff.WHAT_YOU_CAN_SEE, Handoff.WHAT_YOU_DO, Handoff.THE_ORDERS,
                Handoff.KEPT_BEFORE, Handoff.APPLIES_TONIGHT,
                Handoff.TAG_AS_WRITTEN, Handoff.TAG_AS_NEEDED, Handoff.TAG_NOTHING,
                Handoff.YOU_LEARNED, Handoff.LEARNED_NOTHING_FOLLOW,
                Handoff.LEARNED_NOTHING_FOLLOW_2, Handoff.LEARNED_NOTHING_HOLD,
                Handoff.WRITING_HEADING, Handoff.WRITING_INTRO, Handoff.BELLS_FOUR,
                Handoff.WHAT_YOU_LEARNED, Handoff.WHAT_YOU_LEAVE, Handoff.WRITTEN,
                Handoff.LEARNED_NOTHING_EMPTY, Handoff.WHAT_HE_COULD_SEE,
                Handoff.WHAT_HE_DID, Handoff.BECAUSE_YOU_WROTE, Handoff.WHAT_YOU_LEFT_HIM,
                Handoff.READ_IN_THIS_ORDER, Handoff.THE_ORDERS_YOU_LEFT,
                Handoff.STANDING_ORDER, Handoff.VERDICT,
                Handoff.SUCCESSION_HEADING, Handoff.SUCCESSION_TITLE,
                Handoff.SUCCESSION_SCENE));
    }

    /**
     * The prose is the model's.
     *
     * There are two builds now -- the JavaFX screen and the phone build -- and
     * a sentence kept in {@link HandoffScreen} is a sentence the phone build
     * does not have. Every fixed line has to be reachable from Handoff, and the
     * screen may not hold a second copy of any of them. Same rule as Testimony;
     * this is the check that makes it a rule rather than an intention.
     */
    static void theVoice() {
        Set<String> distinct = new HashSet<>();
        for (String s : fixedLines()) {
            ok(s != null && !s.isBlank(), "every fixed line is a line");
            ok(distinct.add(s), "no fixed line is written twice: " + s);
        }
        String screen = source("src/main/java/aside/games/handoff/HandoffScreen.java");
        if (screen == null) {
            System.out.println("       (no HandoffScreen.java from here -- run from the repository root)");
            return;
        }
        for (String s : fixedLines()) {
            // The wordmark is the one word the screen may also spell, because
            // the title is the game's name and not a sentence in it.
            if (s.equals(Handoff.WORDMARK)) continue;
            ok(!screen.contains("\"" + s + "\""),
                    "the screen does not hold its own copy of: " + s);
        }
    }

    /**
     * The single-file build is generated, not written, and a generated file
     * that has gone stale is worse than no file: it is a second copy of the
     * game quietly disagreeing with the first. So the test regenerates it and
     * compares. If this fails, run aside.games.handoff.WebHandoff from the
     * repository root.
     */
    static void thePhoneBuild() throws Exception {
        theVoice();

        Path out = Path.of("web", "handoff.html");
        if (!Files.exists(out)) {
            System.out.println("       (no web/handoff.html from here -- run from the repository root)");
            return;
        }
        String generated;
        try {
            generated = WebHandoff.html();
        } catch (Exception e) {
            System.out.println("       (no template from here: " + e.getMessage() + ")");
            return;
        }
        ok(generated.equals(Files.readString(out)),
                "web/handoff.html is current -- regenerate it with aside.games.handoff.WebHandoff");

        // The sound. These games use it for feedback rather than for a
        // mechanic -- the desktop plays choice_move and choice_select -- but a
        // tap that makes no sound on a page that is otherwise a still canvas
        // reads as a tap that did not land.
        ok(generated.contains("function voice("),
                "the phone build carries the shared synthesiser");
        ok(generated.contains("pointerdown"),
                "the phone build answers a tap with a click");

        // And it has to carry the writing, not just be the right size. The
        // prose goes through the same JSON writer the build uses, because a
        // line containing a quotation mark is escaped in the file and would
        // otherwise look absent.
        for (String s : fixedLines()) {
            ok(generated.contains(WebHandoff.str(s)), "the phone build carries: " + s);
        }
        for (String id : Handoff.signalIds()) {
            ok(generated.contains(WebHandoff.str(id)), "the phone build knows the signal " + id);
            ok(generated.contains(WebHandoff.str(Handoff.signalText(id))),
                    "the phone build carries what " + id + " looks like");
        }
        for (Handoff.Order o : Handoff.allOrders()) {
            ok(generated.contains(WebHandoff.str(o.id)), "the phone build knows the line " + o.id);
            ok(generated.contains(WebHandoff.str(o.text)), "the phone build carries " + o.id);
            ok(generated.contains(WebHandoff.str(o.origin)),
                    "the phone build says where " + o.id + " came from");
        }
        for (Handoff.Watch w : Handoff.WATCHES) {
            ok(generated.contains(WebHandoff.str(w.heading)), "the phone build carries " + w.heading);
            ok(generated.contains(WebHandoff.str(w.title)), "the phone build carries its title");
            ok(generated.contains(WebHandoff.str(w.scene)), "the phone build carries its scene");
            for (Handoff.Option o : w.options) {
                ok(generated.contains(WebHandoff.str(o.text)),
                        "the phone build carries an option of " + w.heading);
                ok(generated.contains(WebHandoff.str(o.outcome)),
                        "the phone build carries what that option cost");
            }
        }
        for (String action : WebHandoff.actionKeys()) {
            ok(generated.contains(WebHandoff.str(Handoff.actText(action))),
                    "the phone build carries what " + action + " does on his night");
        }
        for (Handoff.Concern c : Handoff.Concern.values()) {
            ok(generated.contains(WebHandoff.str(Handoff.unmetText(c))),
                    "the phone build carries what it means when nothing answers " + c);
        }
        for (String closing : WebHandoff.distinctClosings()) {
            ok(generated.contains(WebHandoff.str(closing)),
                    "the phone build can close with: " + closing);
        }
        ok(Handoff.VERDICT.contains("%good%") && Handoff.VERDICT.contains("%raised%"),
                "the verdict is a template with both numbers in it");

        theNightTable(generated);
    }

    /**
     * The whole night, read back out of the build and checked against the model.
     *
     * This is the check the previous five phone builds could not make. Outside
     * resolved its ending into a table and the table was indexed along the
     * wrong axis -- every entry was still a real sentence, so nothing about the
     * file looked wrong, and it was found by driving both builds and diffing.
     * Here the table is the *simulation*, so the check is not a sample: every
     * set of orders a player can leave is in it, and every one of them is
     * compared field by field against {@link Handoff#succeed}. There is no set
     * of orders the phone can be wrong about, because there is no set of orders
     * the phone computes.
     */
    static void theNightTable(String generated) {
        List<String> closings = WebHandoff.distinctClosings();
        List<String> table = stringsOf(section(generated, "\"table\":["));
        eq(table.size(), WebHandoff.N * WebHandoff.N * WebHandoff.N,
                "the table holds every set of orders, in order");
        if (table.size() != WebHandoff.N * WebHandoff.N * WebHandoff.N) return;

        List<Handoff.Order> lines = Handoff.allOrders();
        for (int i = 0; i < WebHandoff.N; i++) {
            for (int j = 0; j < WebHandoff.N; j++) {
                for (int k = 0; k < WebHandoff.N; k++) {
                    Handoff.Order a = lines.get(i), b = lines.get(j), c = lines.get(k);
                    String got = table.get(i * WebHandoff.STRIDE + j * WebHandoff.N + k);
                    Handoff.Succession want = Handoff.succeed(List.of(a, b, c));
                    ok(sameNight(got, want, closings),
                            "his night on " + a.id + "/" + b.id + "/" + c.id
                          + " is the model's  (" + got + ")");
                }
            }
        }
    }

    /** One table entry, decoded and compared to the model's own answer. */
    static boolean sameNight(String entry, Handoff.Succession want, List<String> closings) {
        String[] parts = entry.split("\\|", -1);
        if (parts.length != 4) return false;
        try {
            if (Integer.parseInt(parts[0]) != want.good) return false;
            int ci = Integer.parseInt(parts[1]);
            if (ci < 0 || ci >= closings.size() || !closings.get(ci).equals(want.closing)) return false;
            if (Integer.parseInt(parts[2]) != want.raised) return false;
            String[] evs = parts[3].isEmpty() ? new String[0] : parts[3].split(";");
            if (evs.length != want.events.size()) return false;
            for (int e = 0; e < evs.length; e++) {
                String[] bits = evs[e].split(",", -1);
                if (bits.length != 2) return false;
                Handoff.Event wev = want.events.get(e);
                if (!bits[0].equals(wev.concern.name())) return false;
                if (!bits[1].equals(wev.by == null ? "-" : wev.by.id)) return false;
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
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
