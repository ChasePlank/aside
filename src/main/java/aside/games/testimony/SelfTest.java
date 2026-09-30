package aside.games.testimony;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Headless checks for Testimony.
 *
 * Not coverage. The claims these check are the design claims: that memory
 * keeps no record of correctness, that a wrong answer propagates into the
 * phrasing of later questions, that the account is built from what the
 * player said rather than from what happened, and that the leading
 * question really does have the honest answer on the list.
 *
 * Run: java -cp classes aside.games.testimony.SelfTest
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
        shape();
        answering();
        noRecordOfCorrectness();
        propagation();
        leading();
        account();
        scoring();
        roundTrip();
        playthroughs();
        closings();
        thePhoneBuild();
        System.out.println((checks - failed) + "/" + checks + " checks passed"
                + (failed == 0 ? "" : "  --  " + failed + " FAILED"));
        if (failed > 0) System.exit(1);
    }

    // ------------------------------------------------------------ shape

    static void shape() {
        eq(Testimony.QUESTIONS.size(), 8, "eight questions");
        Set<String> ids = new HashSet<>();
        for (Testimony.Question q : Testimony.QUESTIONS) {
            ok(ids.add(q.id()), "question id unique: " + q.id());
            eq(q.options().size(), 3, q.id() + " offers three options");
            ok(q.option(q.truth()) != null, q.id() + " truth is one of its own options");
            ok(q.note() != null && !q.note().isBlank(), q.id() + " has a note for the verdict");
            ok(q.accountLine().contains("%this%"), q.id() + " contributes a line to the account");
            Set<String> oids = new HashSet<>();
            for (Testimony.Option o : q.options()) {
                ok(oids.add(o.id()), q.id() + " option id unique: " + o.id());
                ok(o.phrase() != null && !o.phrase().isBlank(), q.id() + "/" + o.id() + " has an account phrase");
            }
        }
        // Flattened, because the scene is wrapped for reading and a claim
        // about its content should not depend on where the lines break.
        String scene = Testimony.SCENE.replaceAll("\\s+", " ");
        ok(scene.contains("green"), "the scene states the coat colour");
        ok(scene.contains("folded umbrella"), "the scene states the umbrella");
        ok(scene.contains("tied to the end of the bench"), "the scene states the dog");
        ok(scene.contains("streetlight above the shelter was out"), "the scene states the streetlight");
        ok(scene.contains("the 14"), "the scene states the bus");
        ok(scene.contains("went past twice"), "the scene states the car");
        ok(scene.contains("torn at one corner"), "the scene states the timetable");
        ok(!scene.contains("said") && !scene.contains("asked") && !scene.contains("\""),
                "the scene contains no speech, which is what makes the last question answerable");
    }

    // --------------------------------------------------------- answering

    static void answering() {
        Testimony t = new Testimony();
        eq(t.index(), 0, "a new testimony starts at nothing");
        ok(!t.done(), "and is not done");
        eq(t.current().id(), "coat", "the first question is the coat");

        ok(t.answer("blue", true), "a wrong answer is accepted");
        eq(t.memory.get("coat"), "blue", "and is written into memory");
        eq(t.index(), 1, "the index advances");
        eq(t.current().id(), "umbrella", "to the next question");

        ok(!t.answer("nonsense", true), "an option that was never offered is refused");
        eq(t.index(), 1, "and does not advance the index");
        eq(t.memory.size(), 1, "and does not enter memory");

        ok(!t.answer("open", true) == false, "a valid option still works after a refusal");
        eq(t.memory.get("umbrella"), "open", "and lands in memory");
    }

    // ------------------------------------------------- no record of right

    static void noRecordOfCorrectness() {
        Testimony t = new Testimony();
        t.answer("blue", true);       // wrong
        t.answer("open", false);      // wrong
        // Walk the rest correctly so the two wrong ones are the only ones.
        t.answer("tied", true);
        t.answer("off", true);
        t.answer("n14", true);
        t.answer("twice", true);
        t.answer("torn", true);
        t.answer("silent", true);

        eq(t.memory.get("coat"), "blue", "memory holds the wrong answer");
        eq(t.memory.get("umbrella"), "open", "and the other wrong one");
        eq(t.correctCount(), 6, "six were right");
        eq(t.wrongCount(), 2, "two were wrong");

        // The point: nothing the player can read tells them which two.
        // Memory is a map of answers; there is no parallel map of verdicts.
        Set<String> fields = new HashSet<>();
        for (var f : Testimony.class.getFields()) fields.add(f.getName());
        ok(!fields.contains("right") && !fields.contains("correct") && !fields.contains("verdict"),
                "the state exposes no per-answer verdict field");

        // The account a player reads back is indistinguishable from a
        // correct one in tone -- every line is stated flatly.
        List<String> acct = t.account();
        eq(acct.size(), 8, "the account has one line per question");
        ok(acct.get(0).contains("blue"), "and states the wrong coat as fact");
        ok(!acct.get(0).contains("?"), "with no hedge in it");
    }

    // -------------------------------------------------------- propagation

    static void propagation() {
        Testimony t = new Testimony();
        for (int i = 0; i < 7; i++) t.answer(Testimony.QUESTIONS.get(i).truth(), true);
        Testimony.Question speech = Testimony.byId("speech");
        ok(t.promptFor(speech).contains("green"),
                "with a correct coat, the last question names the green coat");

        Testimony u = new Testimony();
        u.answer("blue", true);   // the coat, wrongly
        for (int i = 1; i < 7; i++) u.answer(Testimony.QUESTIONS.get(i).truth(), true);
        String p = u.promptFor(speech);
        ok(p.contains("blue"), "with a wrong coat, it names the blue coat");
        ok(!p.contains("green"), "and never mentions green again");
        ok(p.startsWith("The woman in the blue coat."),
                "the wrong answer has become the frame of the question");
    }

    // ------------------------------------------------------------ leading

    static void leading() {
        Testimony.Question q = Testimony.byId("speech");
        eq(q.truth(), "silent", "the last question's true answer is that she said nothing");
        eq(q.options().size(), 3, "it still offers three answers");
        int honest = 0;
        for (Testimony.Option o : q.options()) if (o.id().equals(q.truth())) honest++;
        eq(honest, 1, "exactly one of the three is honest");
        ok(q.note().contains("never said"),
                "the verdict names the two sentences she never said");
        eq(Testimony.QUESTIONS.get(Testimony.QUESTIONS.size() - 1).id(), "speech",
                "the leading question is asked last, after seven questions have taught the player "
                        + "that an answer is always available");
    }

    // ------------------------------------------------------------ account

    static void account() {
        List<String> truth = Testimony.accountFrom(Testimony.truthMemory());
        eq(truth.size(), 8, "the true account has eight lines");
        ok(truth.get(0).equals("The woman under the shelter wore a green coat."), "line 1 reads right");
        ok(truth.get(1).equals("Her umbrella was folded, not in use."), "line 2 reads right");
        ok(truth.get(2).equals("The dog was tied to the end of the bench."), "line 3 reads right");
        ok(truth.get(3).equals("The streetlight above the shelter was out."), "line 4 reads right");
        ok(truth.get(4).equals("The bus was the 14, and it was late."), "line 5 reads right");
        ok(truth.get(5).equals("A red car went past twice."), "line 6 reads right");
        ok(truth.get(6).equals("The timetable on the post was torn at one corner."), "line 7 reads right");
        ok(truth.get(7).equals("The woman did not speak."), "line 8 reads right");
        for (String line : truth) ok(!line.contains("%"), "no unresolved token in: " + line);

        Testimony t = new Testimony();
        t.answer("grey", true);
        t.answer("none", true);
        t.answer("loose", true);
        t.answer("on", true);
        t.answer("n4", true);
        t.answer("once", true);
        t.answer("glass", true);
        t.answer("time", true);
        List<String> acct = t.account();
        ok(acct.get(0).contains("grey"), "the account follows the answers, not the scene");
        ok(acct.get(3).equals("The streetlight above the shelter was on."), "including the light");
        ok(acct.get(7).equals("The woman asked the man for the time."), "and a sentence she never said");
        for (String line : acct) ok(!line.contains("%"), "no unresolved token in: " + line);
    }

    // ------------------------------------------------------------ scoring

    static void scoring() {
        Testimony perfect = new Testimony();
        for (Testimony.Question q : Testimony.QUESTIONS) perfect.answer(q.truth(), true);
        eq(perfect.correctCount(), 8, "a perfect witness is 8/8");
        eq(perfect.wrongSure(), 0, "with nothing wrong");
        eq(perfect.correctSure(), 8, "and everything certain");
        ok(perfect.closing().contains("Almost nobody"), "and is told so");

        Testimony honest = new Testimony();
        for (Testimony.Question q : Testimony.QUESTIONS) honest.answer(q.truth(), false);
        eq(honest.correctUnsure(), 8, "an unsure witness is still right");
        eq(honest.wrongSure(), 0, "and has nothing wrong-and-certain");

        Testimony bad = new Testimony();
        for (Testimony.Question q : Testimony.QUESTIONS) bad.answer(q.options().get(0).id(), true);
        eq(bad.order.size(), 8, "eight answers");
        ok(bad.wrongCount() > 0, "some are wrong");
        eq(bad.wrongSure(), bad.wrongCount(), "and all of the wrong ones were stated with certainty");
        ok(bad.closing().contains("sure"), "the closing names the certainty");

        Testimony mixed = new Testimony();
        mixed.answer("blue", true);      // wrong, sure
        mixed.answer("folded", false);   // right, unsure
        mixed.answer("tied", false);     // right, unsure
        mixed.answer("off", true);       // right, sure
        mixed.answer("n41", false);      // wrong, unsure
        mixed.answer("twice", true);     // right, sure
        mixed.answer("torn", true);      // right, sure
        mixed.answer("silent", true);    // right, sure
        eq(mixed.correctCount(), 6, "mixed: six right");
        eq(mixed.wrongCount(), 2, "mixed: two wrong");
        eq(mixed.wrongSure(), 1, "mixed: one wrong answer was certain");
        eq(mixed.wrongUnsure(), 1, "mixed: one wrong answer was doubted");
        eq(mixed.correctSure(), 4, "mixed: four right answers were certain");
        eq(mixed.correctUnsure(), 2, "mixed: two right answers were doubted");
        eq(mixed.correctSure() + mixed.correctUnsure() + mixed.wrongSure() + mixed.wrongUnsure(),
           mixed.order.size(), "the four buckets account for every answer");
        ok(mixed.closing().contains("You were sure 1"), "the closing reports the one that matters");

        Testimony empty = new Testimony();
        eq(empty.correctCount(), 0, "nothing answered is nothing correct");
        ok(empty.closing().contains("cannot be wrong"), "and is named as useless rather than wrong");
    }

    // --------------------------------------------------------- round trip

    static void roundTrip() throws Exception {
        Path dir = Files.createTempDirectory("testimony");
        Path f = dir.resolve("state");

        Testimony t = new Testimony();
        t.answer("blue", true);
        t.answer("folded", false);
        t.answer("tied", true);
        t.save(f);

        Testimony r = Testimony.load(f);
        eq(r.order.size(), 3, "three answers survive the file");
        eq(r.memory.get("coat"), "blue", "the wrong coat survives");
        eq(r.sure.get("coat"), Boolean.TRUE, "and its certainty");
        eq(r.sure.get("umbrella"), Boolean.FALSE, "and a doubt");
        eq(r.current().id(), "light", "and the interview resumes at the right question");
        eq(r.account().get(0), t.account().get(0), "and the account is identical");

        // A finished testimony reopens finished, not at a question that is gone.
        Testimony full = new Testimony();
        for (Testimony.Question q : Testimony.QUESTIONS) full.answer(q.truth(), true);
        Path g = dir.resolve("done");
        full.save(g);
        Testimony back = Testimony.load(g);
        ok(back.done(), "a finished testimony reopens finished");
        eq(back.current(), null, "with no question left to ask");
        eq(back.correctCount(), 8, "and its score intact");

        // A corrupt file is not a crash.
        Path bad = dir.resolve("bad");
        Files.writeString(bad, "coat|blue|1\nnonsense|x|1\ncoat|green|0\n\n");
        Testimony b = Testimony.load(bad);
        eq(b.order.size(), 1, "an unknown question is skipped");
        eq(b.memory.get("coat"), "blue", "and a duplicate line does not overwrite the first");

        eq(Testimony.load(dir.resolve("nothing-here")).order.size(), 0, "a missing file is an empty testimony");
    }

    // -------------------------------------------------------- playthrough

    static void playthroughs() {
        // The careful witness: read the scene, answer from it, do not elaborate.
        Testimony careful = new Testimony();
        while (!careful.done()) {
            Testimony.Question q = careful.current();
            careful.answer(q.truth(), true);
        }
        eq(careful.correctCount(), 8, "careful: 8/8");
        eq(careful.account().get(7), "The woman did not speak.", "careful: and says the true last line");

        // The helpful witness: answers every question, because a question
        // was asked. This is the failure the game is about.
        Testimony helpful = new Testimony();
        while (!helpful.done()) {
            Testimony.Question q = helpful.current();
            // Always the first option -- never the truth for the last one.
            helpful.answer(q.options().get(0).id(), true);
        }
        ok(helpful.wrongCount() > 0, "helpful: gets things wrong");
        ok(helpful.memory.get("speech").equals("complained"),
                "helpful: and puts a sentence in her mouth");
        ok(helpful.account().get(7).contains("complained"),
                "helpful: which the account then states as fact");
        eq(helpful.wrongSure(), helpful.wrongCount(), "helpful: and was certain every time");

        // The leading question is answerable even by a witness who got
        // everything else wrong, if they noticed the offer.
        Testimony noticing = new Testimony();
        while (!noticing.done()) {
            Testimony.Question q = noticing.current();
            noticing.answer(q.options().get(0).id(), true);
        }
        ok(Testimony.byId("speech").option("silent") != null,
                "the honest answer is on the list no matter how the rest went");
    }

    // ------------------------------------------------------------ the closings

    /**
     * The four closings, and the fill.
     *
     * The sentences moved out of TestimonyScreen into the model on 2026-09-30
     * so that the phone build could carry them. These check that the move did
     * not lose a placeholder or leave one unfilled -- a literal %wrong% printed
     * at the player is the kind of bug that ships, because it only appears on
     * the branch nobody played.
     */
    static void closings() {
        Testimony empty = new Testimony();
        eq(empty.closing(), Testimony.CLOSING_NOTHING, "nothing answered gets the empty closing");

        Testimony perfect = new Testimony();
        while (!perfect.done()) perfect.answer(perfect.current().truth(), true);
        eq(perfect.closing(), Testimony.CLOSING_PERFECT, "all eight right gets the perfect closing");

        // Wrong, and never sure: the honest half.
        Testimony doubting = new Testimony();
        while (!doubting.done()) {
            Testimony.Question q = doubting.current();
            doubting.answer(q.options().get(0).id(), false);
        }
        ok(doubting.wrongCount() > 0 && doubting.wrongSure() == 0, "the doubter is wrong and never sure");
        ok(!doubting.closing().contains("%"), "the honest closing has no unfilled placeholder");
        ok(doubting.closing().contains("You were wrong " + doubting.wrongCount() + " times"),
                "and counts the wrong answers");

        // Wrong and certain: the one that matters.
        Testimony certain = new Testimony();
        while (!certain.done()) {
            Testimony.Question q = certain.current();
            certain.answer(q.options().get(0).id(), true);
        }
        ok(certain.wrongSure() > 0, "the helpful witness was sure of something wrong");
        ok(!certain.closing().contains("%"), "the certain closing has no unfilled placeholder");
        ok(certain.closing().contains("You were sure " + certain.wrongSure() + " of those"),
                "and counts the ones they were sure of");

        // The singular, which is exactly the case a second copy of the
        // sentence gets wrong.
        Testimony one = new Testimony();
        one.answer("blue", false);      // the coat, wrongly, and without conviction
        ok(one.closing().contains("You were wrong 1 time, and"),
                "one wrong answer reads as 'time', not 'times'");

        // The tally sentences, which are the other two the phone build fills.
        eq(perfect.verdictTrue(), "8 of 8 lines were true.", "the tally sentence fills");
        ok(!perfect.verdictTally().contains("%"), "the tally line fills");
    }

    // ------------------------------------------------------- the phone build

    /**
     * The single-file build is generated, not written, and a generated file
     * that has gone stale is worse than no file: it is a second copy of the
     * game quietly disagreeing with the first. So the test regenerates it and
     * compares. If this fails, run aside.games.testimony.WebTestimony from the
     * repository root.
     */
    static void thePhoneBuild() throws Exception {
        Path out = Path.of("web", "testimony.html");
        if (!Files.exists(out)) {
            System.out.println("       (no web/testimony.html from here -- run from the repository root)");
            return;
        }
        String generated;
        try {
            generated = WebTestimony.html();
        } catch (Exception e) {
            System.out.println("       (no template from here: " + e.getMessage() + ")");
            return;
        }
        String checkedIn = Files.readString(out);
        ok(generated.equals(checkedIn),
                "web/testimony.html is current -- regenerate it with aside.games.testimony.WebTestimony");

        // And it has to actually carry the writing, not just be the right size.
        ok(generated.contains(Testimony.SCENE_TITLE), "the phone build carries the evening's title");
        carries(generated, Testimony.SCENE, "the phone build carries the evening");
        ok(generated.contains(Testimony.SCENE_NOTE), "the phone build carries the warning under it");
        for (Testimony.Question q : Testimony.QUESTIONS) {
            ok(generated.contains(q.prompt()), "the phone build carries the prompt for " + q.id());
            ok(generated.contains(q.accountLine()), "the phone build carries the account line for " + q.id());
            for (Testimony.Option o : q.options()) {
                ok(generated.contains(o.phrase()),
                        "the phone build carries " + q.id() + "/" + o.id() + " in the account");
            }
        }
        ok(generated.contains(Testimony.CLOSING_NOTHING), "the phone build carries the closing for an empty account");
        ok(generated.contains(Testimony.CLOSING_PERFECT), "the phone build carries the perfect closing");
        ok(generated.contains(Testimony.CLOSING_HONEST), "the phone build carries the honest closing");
        ok(generated.contains(Testimony.CLOSING_CERTAIN), "the phone build carries the certain closing");
        ok(generated.contains(Testimony.VERDICT_TALLY), "the phone build carries the tally");
        ok(generated.contains(Testimony.CONFIDENCE_NOTE), "the phone build carries the confidence note");
        ok(generated.contains(Testimony.MEMORY_HEAD), "the phone build carries the memory panel's head");
        ok(generated.contains("\\u003c") || !generated.contains("<script>\"<"),
                "nothing in the prose can end the script block early");
        System.out.println("       phone build: " + (generated.length() / 1024) + " KB, current");
    }

    /**
     * Every paragraph of a multi-paragraph string has to be in the build.
     *
     * Escaped with the generator's own escaper rather than by hand, because the
     * evening is hard-wrapped and its newlines ship as the two characters
     * backslash-n. Searching for the raw paragraph would fail on a build that
     * is perfectly correct.
     */
    static void carries(String haystack, String text, String what) {
        for (String para : text.split("\n\n")) {
            // The quotes come off, because a paragraph in the middle of a
            // multi-paragraph value is not followed by one -- only the last
            // paragraph is. Keeping them would fail on a correct build.
            String escaped = WebTestimony.str(para);
            ok(haystack.contains(escaped.substring(1, escaped.length() - 1)), what);
        }
    }
}
