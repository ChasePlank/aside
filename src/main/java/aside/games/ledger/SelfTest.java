package aside.games.ledger;

import java.util.List;

/**
 * Headless checks for Ledger.
 *
 * The point of these is not coverage. It is that the ledger's rules are the
 * game, so they have to be true independently of anything drawn -- if the
 * capacity rule or the reckoning schedule were only correct on screen, the
 * game would be a picture of a game.
 *
 * Run: java -cp classes aside.games.ledger.SelfTest
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

    public static void main(String[] args) {
        content();
        capacity();
        toggle();
        schedule();
        answering();
        roundTrip();
        playthrough();
        System.out.println((checks - failed) + "/" + checks + " checks passed"
                + (failed == 0 ? "" : "  --  " + failed + " FAILED"));
        if (failed > 0) System.exit(1);
    }

    static void content() {
        eq(Ledger.NIGHTS.size(), 6, "six nights");
        eq(Ledger.RECKONINGS.size(), 3, "three reckonings");
        eq(Ledger.RECKONING_AFTER.length, Ledger.RECKONINGS.size(), "one night per reckoning");
        int details = 0;
        for (Ledger.Night n : Ledger.NIGHTS) {
            eq(n.details.size(), 4, "four details on " + n.heading);
            details += n.details.size();
        }
        eq(details, 24, "twenty-four details in all");
        ok(details > Ledger.CAPACITY, "more is shown than can be kept");

        // Every reckoning must point at a detail that actually exists, and
        // at one that was shown before the question is asked -- otherwise
        // the question would be unanswerable by design rather than by loss.
        for (int i = 0; i < Ledger.RECKONINGS.size(); i++) {
            Ledger.Reckoning r = Ledger.RECKONINGS.get(i);
            ok(Ledger.detail(r.answerId) != null, "reckoning " + i + " answers a real detail");
            int nightOf = Ledger.nightOf(r.answerId);
            ok(nightOf >= 0 && nightOf <= Ledger.RECKONING_AFTER[i],
                    "reckoning " + i + " asks about a night already seen");
            ok(r.explanation != null && !r.explanation.isBlank(), "reckoning " + i + " explains itself");
        }

        // No detail id may repeat: ids are the identity of a line.
        for (int i = 0; i < Ledger.NIGHTS.size(); i++) {
            for (Ledger.Detail a : Ledger.NIGHTS.get(i).details) {
                for (int j = 0; j < Ledger.NIGHTS.size(); j++) {
                    for (Ledger.Detail b : Ledger.NIGHTS.get(j).details) {
                        if (a != b) ok(!a.id.equals(b.id), "detail id unique: " + a.id);
                    }
                }
            }
        }
    }

    static void capacity() {
        Ledger l = Ledger.fresh();
        eq(l.lines.size(), 0, "a fresh ledger is empty");
        eq(l.nextOut(), null, "nothing is pushed out while there is room");

        String[] ids = {"n1a", "n1b", "n1c", "n1d", "n2a"};
        for (String id : ids) l.record(id);
        eq(l.lines.size(), Ledger.CAPACITY, "the ledger fills to capacity");
        eq(l.lines.get(0), "n1a", "oldest line is first");

        eq(l.nextOut(), "n1a", "the oldest line is the one that would go");
        l.record("n2b");
        eq(l.lines.size(), Ledger.CAPACITY, "writing past capacity does not grow it");
        ok(!l.holds("n1a"), "the oldest line was pushed out");
        ok(l.holds("n2b"), "the new line is in");
        eq(l.lines.get(0), "n1b", "the next oldest is now first");

        // Re-recording something already held must not churn the ledger.
        l.record("n2b");
        eq(l.lines.size(), Ledger.CAPACITY, "recording a held line changes nothing");
        ok(l.holds("n1b"), "and pushes nothing out");
    }

    static void toggle() {
        Ledger l = Ledger.fresh();
        l.toggle("n3a");
        ok(l.holds("n3a"), "toggle writes in");
        l.toggle("n3a");
        ok(!l.holds("n3a"), "toggle crosses out");
        l.record("n3b");
        l.erase("n3b");
        eq(l.lines.size(), 0, "erase removes");
        l.erase("n3b");
        eq(l.lines.size(), 0, "erasing twice is harmless");
    }

    static void schedule() {
        Ledger l = Ledger.fresh();
        l.night = 1;
        ok(l.reckoningDue(), "a question follows night two");
        l.reck = 1;
        l.night = 2;
        ok(!l.reckoningDue(), "but not night three");
        l.night = 3;
        ok(l.reckoningDue(), "a question follows night four");
        l.reck = 2;
        l.night = 5;
        ok(l.reckoningDue(), "a question follows night six");
        l.reck = 3;
        l.night = 5;
        ok(!l.reckoningDue(), "no question once all three are asked");
        // And a question is asked exactly once: answering it moves reck on,
        // so the same night cannot ask twice.
        l.reck = 0; l.night = 1;
        ok(l.reckoningDue(), "night two asks once");
        l.reck = 1;
        ok(!l.reckoningDue(), "and does not ask again");
    }

    static void answering() {
        Ledger l = Ledger.fresh();
        l.night = 1;
        l.record("n1a");
        ok(l.answer("n1a"), "the right line is right");
        eq(l.correct, 1, "and is counted");
        eq(l.reck, 1, "and advances the questions");
        ok(l.reckoning() != null, "a second question is waiting");

        ok(!l.answer(null), "not knowing is not right");
        eq(l.correct, 1, "and is not counted");
        eq(l.reck, 2, "but still advances");

        ok(!l.answer("n1b"), "a wrong line is wrong");
        eq(l.correct, 1, "and is not counted");
        eq(l.reck, 3, "and advances");
        eq(l.reckoning(), null, "no questions left");
    }

    static void roundTrip() {
        Ledger l = Ledger.fresh();
        l.night = 3;
        l.reck = 1;
        l.correct = 1;
        l.record("n1a");
        l.record("n2b");
        l.closeNight();
        l.record("n4d");

        Ledger back = Ledger.deserialize(l.serialize());
        eq(back.night, l.night, "night survives the round trip");
        eq(back.reck, l.reck, "reck survives");
        eq(back.correct, l.correct, "correct survives");
        eq(back.lines, l.lines, "the ledger survives");
        eq(back.seen, l.seen, "what was seen survives");
        eq(back.finished, l.finished, "the finished flag survives");

        // A corrupt or unknown id must be dropped, not crash the game.
        Ledger dirty = Ledger.deserialize("# ledger v1\nnight\t2\nline\tnope\nline\tn1a\n");
        eq(dirty.lines, List.of("n1a"), "unknown lines are dropped");
        eq(dirty.night, 2, "known fields still load");

        Ledger empty = Ledger.deserialize("");
        eq(empty.night, 0, "an empty save is a fresh clerk");

        Ledger over = Ledger.deserialize("night\t99\nreck\t99\n");
        eq(over.night, Ledger.NIGHTS.size(), "an out-of-range night is clamped");
        eq(over.reck, Ledger.RECKONINGS.size(), "an out-of-range reck is clamped");
    }

    /** Play the game the way a careful clerk would, and the way a careless one would. */
    static void playthrough() {
        // Careful: keep the three answers and nothing else.
        Ledger l = Ledger.fresh();
        for (int n = 0; n < Ledger.NIGHTS.size(); n++) {
            l.night = n;
            l.closeNight();
            if (l.reckoningDue()) {
                l.record(Ledger.RECKONINGS.get(l.reck).answerId);
                ok(l.answer(Ledger.RECKONINGS.get(l.reck).answerId), "careful clerk answers question " + l.reck);
            }
        }
        eq(l.correct, 3, "a careful clerk gets all three");

        // Careless: keep only the last night's four details plus one.
        Ledger c = Ledger.fresh();
        for (int n = 0; n < Ledger.NIGHTS.size(); n++) {
            c.night = n;
            for (Ledger.Detail d : Ledger.NIGHTS.get(n).details) c.record(d.id);
            c.closeNight();
            if (c.reckoningDue()) {
                boolean ok = c.answer(null);
                ok(!ok, "a careless clerk cannot answer question " + c.reck);
            }
        }
        eq(c.correct, 0, "a careless clerk gets none");

        // The careless clerk's ledger is still full -- the game never
        // punishes you for keeping things, only for keeping the wrong ones.
        eq(c.lines.size(), Ledger.CAPACITY, "a full ledger stays full");
        ok(c.lost().size() > 0, "and a lot was let go");
    }

    private SelfTest() {}
}
