package aside.games.ledger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    public static void main(String[] args) throws Exception {
        content();
        capacity();
        toggle();
        schedule();
        answering();
        roundTrip();
        playthrough();
        theVoice();
        thePhoneBuild();
        System.out.println((checks - failed) + "/" + checks + " checks passed"
                + (failed == 0 ? "" : "  --  " + failed + " FAILED"));
        if (failed > 0) System.exit(1);
    }

    /**
     * The prose is the model's.
     *
     * There are two builds now -- the JavaFX screen and the phone build -- and
     * a sentence kept in LedgerScreen is a sentence the phone build does not
     * have. Every fixed line has to be reachable from Ledger, and the screen
     * may not hold a second copy of any of them.
     */
    static void theVoice() {
        String[] lines = {Ledger.INSPECTOR_NOTE, Ledger.HAD_IT, Ledger.DID_NOT_HAVE_IT,
                Ledger.SAID_NOTHING, Ledger.CLOSES, Ledger.NOTHING, Ledger.I_DO_NOT_KNOW};
        Set<String> distinct = new HashSet<>();
        for (String s : lines) {
            ok(s != null && !s.isBlank(), "every fixed line is a line: " + s);
            ok(distinct.add(s), "and no two of them are the same line");
        }
        // The lines that depend on state are the model's too.
        for (int c = 0; c <= Ledger.RECKONINGS.size(); c++) {
            ok(Ledger.answeredCount(c).contains(String.valueOf(c)), "the count reads back score " + c);
            ok(Ledger.endNote(c) != null && Ledger.endNote(c).length() > 40, "score " + c + " closes on a line");
        }
        for (Ledger.Night n : Ledger.NIGHTS) {
            for (Ledger.Detail d : n.details) {
                ok(Ledger.answered(d.id).contains(d.text), "answering with " + d.id + " quotes it");
                ok(Ledger.costWarning(d.id).contains(d.text), "the cost of writing " + d.id + " names it");
            }
        }
        String screen = screenSource();
        if (screen != null) {
            // Look for the line as a string literal, not as a bare word:
            // "nothing" is a word this game uses in prose and in comments, and
            // a check that cannot tell a line from a word is a check that
            // fails for the wrong reason.
            for (String s : lines) {
                ok(!screen.contains("\"" + s + "\""), "the screen does not hold its own copy of: " + s);
            }
            ok(!screen.contains("String endNote") && !screen.contains("String answeredCount"),
                    "and the closing lines are the model's, not the screen's");
        }
    }

    /** A line of prose has to be in the build, in the form the build stores it. */
    static void carries(String generated, String text, String what) {
        ok(generated.contains(WebLedger.str(text)), what);
    }

    /** The screen's source, if this is being run from a checkout. */
    static String screenSource() {
        Path p = Path.of("src", "main", "java", "aside", "games", "ledger", "LedgerScreen.java");
        try {
            return Files.exists(p) ? Files.readString(p) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The single-file build is generated, not written, and a generated file
     * that has gone stale is worse than no file: it is a second copy of the
     * game quietly disagreeing with the first. So the test regenerates it and
     * compares. If this fails, run aside.games.ledger.WebLedger from the
     * repository root.
     */
    static void thePhoneBuild() throws Exception {
        Path out = Path.of("web", "ledger.html");
        if (!Files.exists(out)) {
            System.out.println("       (no web/ledger.html from here -- run from the repository root)");
            return;
        }
        String generated;
        try {
            generated = WebLedger.html();
        } catch (Exception e) {
            System.out.println("       (no template from here: " + e.getMessage() + ")");
            return;
        }
        ok(generated.equals(Files.readString(out)),
                "web/ledger.html is current -- regenerate it with aside.games.ledger.WebLedger");

        // And it has to carry the writing, not just be the right size. The
        // prose goes through the same JSON writer the build uses, because a
        // detail containing a quotation mark is escaped in the file and would
        // otherwise look absent -- which is how this check first failed.
        for (Ledger.Night n : Ledger.NIGHTS) {
            carries(generated, n.heading, "the phone build carries " + n.heading);
            carries(generated, n.title, "the phone build carries the title of " + n.heading);
            carries(generated, n.scene, "the phone build carries the scene of " + n.heading);
            for (Ledger.Detail d : n.details) {
                carries(generated, d.text, "the phone build carries " + d.id);
                carries(generated, Ledger.answered(d.id), "and what answering with " + d.id + " says");
                carries(generated, Ledger.costWarning(d.id), "and what writing " + d.id + " costs");
            }
        }
        for (Ledger.Reckoning r : Ledger.RECKONINGS) {
            carries(generated, r.question, "the phone build carries the question about " + r.answerId);
            carries(generated, r.explanation, "and the answer to it");
        }
        for (int c = 0; c <= Ledger.RECKONINGS.size(); c++) {
            carries(generated, Ledger.answeredCount(c), "the phone build carries the count for " + c);
            carries(generated, Ledger.endNote(c), "and the closing for " + c);
        }
        carries(generated, Ledger.INSPECTOR_NOTE, "the phone build carries the inspector's note");
        carries(generated, Ledger.HAD_IT, "the phone build carries the verdict when you had it");
        carries(generated, Ledger.DID_NOT_HAVE_IT, "the phone build carries the verdict when you did not");
        carries(generated, Ledger.SAID_NOTHING, "the phone build carries the answer that said nothing");
        carries(generated, Ledger.CLOSES, "the phone build carries the closing");
        carries(generated, Ledger.I_DO_NOT_KNOW, "the phone build carries the answer that is always there");
        ok(generated.contains("capacity"), "the phone build is told how many lines the ledger has");

        // The save format is the same one on both sides.
        ok(generated.contains("ledger v1"), "the phone build writes the same save file the desktop does");

        // The content is embedded in a script tag, so nothing in the prose may
        // be able to end the block early.
        ok(!generated.contains("</script>")
                        || generated.indexOf("</script>") > generated.lastIndexOf("const C ="),
                "nothing in the prose can end the script block early");

        System.out.println("       phone build: " + (generated.length() / 1024) + " KB, current");
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
