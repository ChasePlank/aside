package aside.games.attribution;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Headless checks for attribution.
 *
 * The point of these is not coverage. It is that the rule IS the game, so it
 * has to be true independently of anything drawn. If "you cannot check the
 * claim, only who filed it" were only true on screen, the game would be a
 * picture of a game.
 *
 * The one check here that is not like the others is the gradient. A scored
 * game has to be scoreable: if a player who spreads five calls across the desk
 * did no better than one who files everything, the calls would be decoration.
 * So the test plays the night three ways and asserts the order.
 *
 * Run: java -cp classes aside.games.attribution.SelfTest
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
        theDeal();
        thePurse();
        theFiling();
        theArithmetic();
        theGradient();
        theProse();
        storage();
        thePhoneBuild();
        System.out.println((checks - failed) + "/" + checks + " checks passed"
                + (failed == 0 ? "" : "  --  " + failed + " FAILED"));
        if (failed > 0) System.exit(1);
    }

    // ---------------------------------------------------------------- shape

    static void shape() {
        eq(Attribution.STRINGERS, 6, "six bylines");
        eq(Attribution.ROUNDS, 6, "six rounds");
        eq(Attribution.PER_ROUND, 4, "four lines a round");
        eq(Attribution.ITEMS, 24, "twenty-four lines");
        eq(Attribution.CALLS, 5, "five calls");
        eq(Attribution.COPY.size(), Attribution.ITEMS, "there is one line of copy per item");
        eq(new HashSet<>(Attribution.COPY).size(), Attribution.ITEMS, "and no line of copy is used twice");
        eq(Attribution.DESK.length, Attribution.STRINGERS, "there is a name for every byline");
        eq(Attribution.RELIABILITY.length, Attribution.STRINGERS, "and a reliability for every byline");
        eq(Attribution.CALLS * Attribution.PER_ROUND, Attribution.ITEMS - 4,
                "five calls cannot cover a night of twenty-four");
        for (String[] d : Attribution.DESK) {
            ok(d[0] != null && !d[0].isBlank(), "every byline has a name");
            ok(d[1] != null && !d[1].isBlank(), "and a beat");
        }
        for (String line : Attribution.COPY) {
            ok(line.endsWith("."), "a line of copy is a sentence: " + line);
            ok(!line.startsWith(" "), "and it does not start with a space");
        }
        // The two middle reliabilities are the design: close enough that a
        // handful of calls cannot separate them, far enough from even that the
        // right answer is still worth finding.
        double mean = 0;
        for (double r : Attribution.RELIABILITY) mean += r;
        mean /= Attribution.RELIABILITY.length;
        ok(mean > 0.5 && mean < 0.6, "the desk averages a little over even (" + mean + ")");
        ok(Attribution.RELIABILITY[0] > 0.9 && Attribution.RELIABILITY[5] < 0.2,
                "and it runs from nearly always to nearly never");
    }

    // ----------------------------------------------------------------- deal

    static void theDeal() {
        for (int seed = 0; seed < 200; seed++) {
            Attribution a = Attribution.of(seed);
            eq(a.items.size(), Attribution.ITEMS, "seed " + seed + ": twenty-four lines");
            eq(a.stringers.size(), Attribution.STRINGERS, "seed " + seed + ": six bylines");
            eq(a.callsLeft, Attribution.CALLS, "seed " + seed + ": the purse is full at the start");
            ok(!a.finished(), "seed " + seed + ": the night is not over before it starts");
            eq(a.round(), 0, "seed " + seed + ": and it starts on the first round");

            int[] perStringer = new int[Attribution.STRINGERS];
            int[] perRound = new int[Attribution.ROUNDS];
            Set<Integer> reliabilities = new HashSet<>();
            for (int i = 0; i < a.stringers.size(); i++) {
                reliabilities.add((int) Math.round(a.stringers.get(i).reliability * 100));
            }
            eq(reliabilities.size(), Attribution.STRINGERS,
                    "seed " + seed + ": every byline gets a different reliability");

            for (int i = 0; i < a.items.size(); i++) {
                Attribution.Item it = a.items.get(i);
                eq(it.number, i + 1, "seed " + seed + ": line numbers run in order");
                eq(it.round, i / Attribution.PER_ROUND, "seed " + seed + ": round " + it.round + " is dealt in blocks");
                eq(it.text, Attribution.COPY.get(i), "seed " + seed + ": the copy is the copy, in order");
                ok(it.stringer >= 0 && it.stringer < Attribution.STRINGERS, "seed " + seed + ": the byline is on the desk");
                ok(!it.filed() && !it.calledIt(), "seed " + seed + ": nothing is filed or called yet");
                perStringer[it.stringer]++;
                perRound[it.round]++;
            }
            for (int s = 0; s < Attribution.STRINGERS; s++) {
                eq(perStringer[s], Attribution.PER_ROUND, "seed " + seed + ": byline " + s + " files four lines");
            }
            for (int r = 0; r < Attribution.ROUNDS; r++) {
                eq(perRound[r], Attribution.PER_ROUND, "seed " + seed + ": round " + r + " holds four lines");
            }
        }

        // Same seed, same night -- the save file is a seed, so this has to hold.
        for (int seed = 0; seed < 40; seed++) {
            Attribution x = Attribution.of(seed), y = Attribution.of(seed);
            for (int i = 0; i < Attribution.ITEMS; i++) {
                eq(x.items.get(i).truth, y.items.get(i).truth, "seed " + seed + ": the truth is dealt the same twice");
                eq(x.items.get(i).stringer, y.items.get(i).stringer, "seed " + seed + ": and so is the byline");
            }
        }

        // The reliabilities are the reliabilities: over enough nights a byline
        // files true about as often as its number says. Grouped by reliability
        // and not by desk position, because the whole point of the deal is that
        // a name carries a different number every night -- the only way to
        // check the numbers is to follow them across nights.
        int[] byRel = new int[Attribution.STRINGERS];
        int[] relTrue = new int[Attribution.STRINGERS];
        for (int seed = 0; seed < 600; seed++) {
            Attribution a = Attribution.of(seed);
            for (int s = 0; s < Attribution.STRINGERS; s++) {
                int idx = relIndex(a.stringers.get(s).reliability);
                byRel[idx] += a.dealtBy(s);
                relTrue[idx] += a.trueBy(s);
            }
        }
        for (int i = 0; i < Attribution.STRINGERS; i++) {
            double rate = (double) relTrue[i] / byRel[i];
            ok(Math.abs(rate - Attribution.RELIABILITY[i]) < 0.05,
                    "a byline dealt " + fmt(Attribution.RELIABILITY[i]) + " files true " + fmt(rate));
        }
    }

    static int relIndex(double r) {
        for (int i = 0; i < Attribution.RELIABILITY.length; i++) {
            if (Math.abs(Attribution.RELIABILITY[i] - r) < 1e-9) return i;
        }
        return -1;
    }

    // ---------------------------------------------------------------- purse

    static void thePurse() {
        Attribution a = Attribution.of(7);
        int spent = 0;
        for (Attribution.Item it : a.items) {
            if (a.callsLeft <= 0) break;
            ok(a.call(it), "a call is spent on an unfiled line");
            spent++;
            eq(it.called, it.truth ? 1 : 2, "and it tells the truth about that line");
            ok(!a.call(it), "the same line cannot be called twice");
        }
        eq(spent, Attribution.CALLS, "the purse runs out after five calls");
        eq(a.callsLeft, 0, "and it is empty");
        eq(a.callsMade(), Attribution.CALLS, "five calls were made");
        Attribution.Item spare = null;
        for (Attribution.Item it : a.items) if (!it.calledIt()) { spare = it; break; }
        ok(spare != null, "there are lines left uncalled");
        ok(!a.call(spare), "and an empty purse buys nothing");
        eq(spare.called, 0, "the line is still uncalled");

        // A filed line cannot be called: that is the whole tension of a round.
        Attribution b = Attribution.of(11);
        Attribution.Item first = b.items.get(0);
        b.file(first, true);
        ok(!b.call(first), "a filed line cannot be called");
        eq(b.callsLeft, Attribution.CALLS, "and no call was spent trying");
    }

    // --------------------------------------------------------------- filing

    static void theFiling() {
        Attribution a = Attribution.of(3);
        for (int r = 0; r < Attribution.ROUNDS; r++) {
            eq(a.round(), r, "round " + r + " is the round being filed");
            List<Attribution.Item> round = a.roundItems();
            eq(round.size(), Attribution.PER_ROUND, "round " + r + " shows four lines");
            for (int i = 0; i < round.size(); i++) {
                Attribution.Item it = round.get(i);
                ok(a.file(it, i % 2 == 0), "a line can be filed");
                ok(!a.file(it, true), "and not filed twice");
                if (i < round.size() - 1) {
                    eq(a.round(), r, "the round does not turn over until the last line is decided");
                }
            }
        }
        ok(a.finished(), "after six rounds the night is over");
        eq(a.round(), Attribution.ROUNDS, "and there is no round being filed");
        ok(a.roundItems().isEmpty(), "and nothing on the desk");
        eq(a.ran() + a.spiked(), Attribution.ITEMS, "every line was decided");
    }

    // ----------------------------------------------------------- arithmetic

    static void theArithmetic() {
        for (int seed = 0; seed < 300; seed++) {
            Attribution a = Attribution.of(seed);
            for (Attribution.Item it : a.items) a.file(it, it.number % 3 != 0);
            eq(a.ran() + a.spiked(), Attribution.ITEMS, "seed " + seed + ": run and spiked account for the night");
            eq(a.ranTrue() + a.ranFalse(), a.ran(), "seed " + seed + ": what was run splits into true and false");
            eq(a.spikedTrue() + a.spikedFalse(), a.spiked(), "seed " + seed + ": what was spiked does too");
            eq(a.trueLines(), a.ranTrue() + a.spikedTrue(), "seed " + seed + ": the true lines are all somewhere");
            eq(a.correct(), a.ranTrue() + a.spikedFalse(), "seed " + seed + ": correct is run-and-true plus spiked-and-false");
            ok(a.accuracy() >= 0 && a.accuracy() <= 1, "seed " + seed + ": accuracy is a fraction");
            eq(a.correct(), (int) Math.round(a.accuracy() * Attribution.ITEMS), "seed " + seed + ": and it is the count over twenty-four");
        }

        // A perfect night is reachable, and only by running every true line.
        Attribution perfect = Attribution.of(5);
        for (Attribution.Item it : perfect.items) perfect.file(it, it.truth);
        eq(perfect.correct(), Attribution.ITEMS, "a night filed on the truth scores twenty-four");
        eq(perfect.headline(), "A clean edition.", "and reads as a clean edition");
        Attribution worst = Attribution.of(5);
        for (Attribution.Item it : worst.items) worst.file(it, !it.truth);
        eq(worst.correct(), 0, "and a night filed against the truth scores nothing");
    }

    // ------------------------------------------------------------- gradient

    /**
     * The calls have to buy something. Three policies, over the same nights:
     * file everything, spike everything, and spread the calls across the desk
     * and file by what they said.
     */
    static void theGradient() {
        int nights = 3000;
        double runAll = 0, spikeAll = 0, spread = 0, oracle = 0;
        for (int n = 0; n < nights; n++) {
            long seed = 1_000_003L * n + 17;
            runAll += score(seed, false, false);
            spikeAll += score(seed, true, false);
            spread += score(seed, false, true);
            Attribution a = Attribution.of(seed);
            for (Attribution.Item it : a.items) {
                a.file(it, a.stringers.get(it.stringer).reliability > 0.5);
            }
            oracle += a.accuracy();
        }
        runAll /= nights;
        spikeAll /= nights;
        spread /= nights;
        oracle /= nights;

        ok(spread > runAll + 0.10, "spreading the calls beats filing everything ("
                + fmt(spread) + " against " + fmt(runAll) + ")");
        ok(runAll > spikeAll, "and filing everything beats killing everything ("
                + fmt(runAll) + " against " + fmt(spikeAll) + ")");
        ok(oracle > spread, "and knowing the desk beats five calls ("
                + fmt(oracle) + " against " + fmt(spread) + ")");
        ok(spread < 0.80, "five calls does not reach the ceiling (" + fmt(spread) + ")");
        ok(runAll > 0.50 && runAll < 0.60, "filing everything scores the desk's own average (" + fmt(runAll) + ")");
    }

    /** File everything, or spike everything, or spread the calls and file by rate. */
    static double score(long seed, boolean spikeAll, boolean spread) {
        Attribution a = Attribution.of(seed);
        if (!spread) {
            for (Attribution.Item it : a.items) a.file(it, !spikeAll);
            return a.accuracy();
        }
        int[] called = new int[Attribution.STRINGERS];
        int[] trueSeen = new int[Attribution.STRINGERS];
        for (Attribution.Item it : a.items) {
            if (a.callsLeft <= 0) break;
            if (called[it.stringer] > 0) continue;
            if (a.call(it)) { called[it.stringer]++; if (it.calledTrue()) trueSeen[it.stringer]++; }
        }
        for (Attribution.Item it : a.items) {
            a.file(it, called[it.stringer] == 0 || trueSeen[it.stringer] * 2 >= called[it.stringer]);
        }
        return a.accuracy();
    }

    // ---------------------------------------------------------------- prose

    static void theProse() {
        // The headline and the closing have to cover every score, and they have
        // to move in one direction as the score does.
        Set<String> headlines = new HashSet<>();
        for (int c = 0; c <= Attribution.ITEMS; c++) {
            String h = Attribution.headline(c);
            String k = Attribution.closing(c);
            ok(h != null && !h.isBlank(), "there is a headline for " + c);
            ok(k != null && !k.isBlank(), "and a closing for " + c);
            ok(!Character.isDigit(h.charAt(0)), "the headline does not start with a digit: " + h);
            ok(!Character.isDigit(k.charAt(0)), "the closing does not start with a digit");
            headlines.add(h);
        }
        ok(headlines.size() >= 4, "the headline has bands (" + headlines.size() + ")");
        eq(Attribution.headline(Attribution.ITEMS), "A clean edition.", "twenty-four is a clean edition");
        eq(Attribution.headline(0), "A bad edition.", "nothing is a bad edition");

        // The verdict is a template, and the desktop and the phone fill the
        // same one. Filling it must not leave a placeholder behind.
        for (int ran = 0; ran <= Attribution.ITEMS; ran += 4) {
            int ranTrue = ran / 2, spiked = Attribution.ITEMS - ran, spikedTrue = spiked / 2;
            String v = Attribution.verdict(ran, ranTrue, spiked, spikedTrue, ranTrue + (spiked - spikedTrue));
            ok(!v.contains("%"), "the verdict has no unfilled placeholder: " + v);
            ok(v.contains(String.valueOf(ran)), "the verdict states what was run");
            ok(v.contains(String.valueOf(spikedTrue)), "and what was killed that was true");
        }
        ok(Attribution.VERDICT_TEMPLATE.contains("%ran%"), "the template carries the run count");
        ok(Attribution.VERDICT_TEMPLATE.contains("%correct%"), "and the score");

        // The calls line names the bylines that went uncalled, and says so
        // differently when none did.
        Attribution a = Attribution.of(9);
        for (Attribution.Item it : a.items) a.file(it, true);
        ok(a.callsLine().contains("You made no calls"), "a night with no calls says so");
        ok(a.callsLine().contains("never called"), "and names the whole desk");
        Attribution b = Attribution.of(9);
        for (Attribution.Item it : b.items) {
            if (b.callsLeft > 0) b.call(it);
            b.file(it, true);
        }
        ok(b.callsLine().contains("You made five calls"), "five calls reads as five");
        ok(!b.callsLine().contains("%"), "the calls line has no unfilled placeholder");
        eq(Attribution.joinOr(List.of("A")), "A", "one name needs no joining");
        eq(Attribution.joinOr(List.of("A", "B")), "A or B", "two names join with or");
        eq(Attribution.joinOr(List.of("A", "B", "C")), "A, B or C", "three names keep the comma");
        eq(Attribution.joinOr(List.of()), "", "no names is nothing");
        eq(Attribution.word(0), "none", "zero has a word");
        eq(Attribution.word(5), "five", "and so does five");
        eq(Attribution.word(24), "twenty-four", "and twenty-four");

        // The opening has to say the purse, and the rules have to say the rest.
        String opening = String.join(" ", Attribution.OPENING);
        ok(opening.contains(Attribution.word(Attribution.CALLS) + " calls"),
                "the opening states the purse");
        ok(opening.contains("does not say which of it you checked"),
                "and states what the edition does not carry");
        String rules = "";
        for (String[] r : Attribution.RULES) rules += r[0] + " " + r[1] + " ";
        ok(rules.contains("call"), "the rules describe the call");
        ok(rules.contains("run") && rules.contains("spike"), "and both ways to file");
        ok(Attribution.STANDING.contains("Nobody downstream"), "the standing line stands");

        // The library line is prose too, and it is the one nobody re-reads.
        String blurb = new AttributionGame().blurb();
        ok(blurb.toLowerCase().contains(Attribution.word(Attribution.CALLS) + " calls"),
                "the library line states the purse: " + blurb);
        ok(blurb.toLowerCase().contains(Attribution.word(Attribution.ITEMS) + " lines"),
                "and the length of the night");

        // No sentence may carry a number the constants do not agree with. The
        // first render of the report said "Seven calls is not enough" to a
        // player holding five, which is the kind of bug a render finds and a
        // reading does not.
        String all = opening + " " + rules + " " + Attribution.STANDING;
        for (int c = 0; c <= Attribution.ITEMS; c++) all += " " + Attribution.closing(c);
        for (String stale : new String[]{"seven calls", "seven questions", "eight calls",
                "six calls", "four calls", "three calls"}) {
            if (stale.equals(Attribution.word(Attribution.CALLS) + " calls")) continue;
            ok(!all.toLowerCase().contains(stale), "no sentence still says \"" + stale + "\"");
        }
        ok(Attribution.closing(13).toLowerCase()
                        .contains(Attribution.word(Attribution.CALLS) + " calls"),
                "the ordinary closing states the purse it is talking about");
        ok(Attribution.closing(13).contains(
                        Attribution.cap(Attribution.word(Attribution.CALLS)) + " calls"),
                "and states it as a sentence, not mid-clause");
    }

    // --------------------------------------------------------------- storage

    static void storage() throws Exception {
        Path tmp = Files.createTempDirectory("attribution");
        Path f = tmp.resolve("attribution.state");
        Attribution a = Attribution.of(42);
        for (int i = 0; i < 9; i++) a.file(a.items.get(i), i % 2 == 0);
        a.call(a.items.get(12));
        a.call(a.items.get(13));
        a.save(f);

        Attribution back = Attribution.load(f);
        eq(back.seed, a.seed, "the seed survives the file");
        eq(back.callsLeft, a.callsLeft, "the purse survives");
        eq(back.round(), a.round(), "the round survives");
        for (int i = 0; i < Attribution.ITEMS; i++) {
            eq(back.items.get(i).filed, a.items.get(i).filed, "line " + i + " was filed the same way");
            eq(back.items.get(i).called, a.items.get(i).called, "line " + i + " was called the same way");
            eq(back.items.get(i).truth, a.items.get(i).truth, "line " + i + " is the same line");
        }
        eq(back.ran(), a.ran(), "and the night reads the same");
        eq(back.correct(), a.correct(), "including the score");

        // A missing file is a new night, not a crash.
        Attribution fresh = Attribution.load(tmp.resolve("nothing-here"));
        eq(fresh.callsLeft, Attribution.CALLS, "a missing save opens a full purse");
        eq(fresh.items.size(), Attribution.ITEMS, "and a full night");

        // A corrupt file is also a new night.
        Path bad = tmp.resolve("bad.state");
        Files.writeString(bad, "not a night at all\n");
        eq(Attribution.load(bad).items.size(), Attribution.ITEMS, "a corrupt save opens a new night");

        Files.deleteIfExists(f);
        Files.deleteIfExists(bad);
        Files.deleteIfExists(tmp);
    }

    // ----------------------------------------------------------- the phone

    static void thePhoneBuild() throws Exception {
        Path out = Path.of("web", "attribution.html");
        if (!Files.exists(out)) {
            ok(false, "the phone build exists at " + out);
            return;
        }
        String checkedIn = Files.readString(out);
        String generated = WebAttribution.html();
        // A generated file that has gone stale is worse than no file: it is a
        // second copy of the game quietly disagreeing with the first.
        ok(checkedIn.equals(generated), "the checked-in phone build is the one the generator writes"
                + " (checked in " + checkedIn.length() + " bytes, generated " + generated.length()
                + " -- run: java -cp classes aside.games.attribution.WebAttribution)");

        ok(generated.contains(WebAttribution.MARKER) == false, "the content marker was substituted");
        for (String line : Attribution.COPY) {
            ok(generated.contains(WebAttribution.str(line)), "the phone build carries the copy: " + line);
        }
        Attribution a = Attribution.of(1);
        for (Attribution.Stringer s : a.stringers) {
            ok(generated.contains(WebAttribution.str(s.name)), "the phone build carries the byline " + s.name);
            ok(generated.contains(WebAttribution.str(s.beat)), "and the beat " + s.beat);
        }
        for (double r : Attribution.RELIABILITY) {
            ok(generated.contains(String.valueOf(r)), "the phone build carries the reliability " + r);
        }
        for (String p : Attribution.OPENING) {
            ok(generated.contains(WebAttribution.str(p)), "the phone build carries the opening paragraph");
        }
        for (String[] r : Attribution.RULES) {
            ok(generated.contains(WebAttribution.str(r[1])), "the phone build carries the rule: " + r[1]);
        }
        for (String s : new String[]{
                Attribution.START_LINE, Attribution.START_BUTTON, Attribution.CALL_BTN,
                Attribution.THE_DESK, Attribution.WHO_YOU_CALLED, Attribution.NOBODY_CALLED,
                Attribution.WHO_FILES_HERE,
                Attribution.THE_EDITION, Attribution.FILED, Attribution.TRUE_OF, Attribution.CALLED,
                Attribution.NOT_CALLED, Attribution.RUN, Attribution.SPIKE, Attribution.NO_CALL,
                Attribution.WAS_TRUE, Attribution.WAS_FALSE, Attribution.ROUND_FILED,
                Attribution.NO_CALLS, Attribution.ALREADY_CALLED, Attribution.ALREADY_FILED,
                Attribution.AGAIN, Attribution.OR, Attribution.STANDING,
                Attribution.CALLS_LINE_ALL, Attribution.CALLS_LINE_NEVER,
                Attribution.VERDICT_TEMPLATE}) {
            ok(generated.contains(WebAttribution.str(s)), "the phone build carries: " + s);
        }
        // The lines that depend on a number go out as tables, so what has to be
        // present is every entry, including the singular that gets forgotten.
        for (int i = 0; i <= Attribution.CALLS; i++) {
            ok(generated.contains(WebAttribution.str(Attribution.callsLineMade(i))),
                    "the phone build carries the call count: " + Attribution.callsLineMade(i));
            ok(generated.contains(WebAttribution.str(Attribution.callsWhere(i))),
                    "and the calls-left readout for " + i);
        }
        for (int i = 0; i < Attribution.ROUNDS; i++) {
            ok(generated.contains(WebAttribution.str(Attribution.roundWhere(i))),
                    "and the round readout for " + i);
        }
        for (int c = 0; c <= Attribution.ITEMS; c++) {
            ok(generated.contains(WebAttribution.str(Attribution.headline(c))),
                    "the phone build carries the headline for " + c);
            ok(generated.contains(WebAttribution.str(Attribution.closing(c))),
                    "and the closing for " + c);
        }
        // The phone deals its own night, so it must be told the desk and not
        // the night: no byline assignment and no truth may be baked in.
        ok(generated.contains("function deal()"), "the phone deals its own night");
        ok(!generated.contains("\"truth\":["), "and is not handed a truth table");
        ok(generated.contains("\"reliabilities\":["), "but is handed the desk");
    }

    static String fmt(double x) { return String.format("%.3f", x); }
}
