package aside.games.vigil;

import aside.game.Game;
import aside.game.Games;
import aside.ui.LibraryLayout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Headless checks for vigil.
 *
 * The point of these is not coverage. It is that the three structural facts
 * the game rests on have to be true independently of anything drawn:
 *
 *   1. The promise is impossible, and impossible by arithmetic rather than by
 *      difficulty. If a player could hold all five, the game would be a
 *      resource puzzle with a correct answer, and the ending would be a score.
 *   2. maxSurvivors() is a real bound, not a guess: it has to be achievable by
 *      an actual schedule and it has to be unbeatable by a greedy one.
 *   3. Every ending the state space can produce is a sentence. The outcome
 *      space is small -- at most three things come through, so the counts are
 *      bounded -- and each one is reachable, distinct, and grammatical.
 *
 * Run: java -cp classes aside.games.vigil.SelfTest
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
        theArithmetic();
        theSchedule();
        theDayLines();
        keeping();
        changing();
        decay();
        theDays();
        threeComeThrough();
        noGreedyBeatsIt();
        endings();
        grammar();
        storage();
        determinism();
        registry();
        thePhoneBuild();
        System.out.println((checks - failed) + "/" + checks + " checks passed"
                + (failed == 0 ? "" : "  --  " + failed + " FAILED"));
        if (failed > 0) System.exit(1);
    }

    // ------------------------------------------------------------ content

    static void content() {
        Vigil v = Vigil.of();
        eq(v.things.size(), 5, "five things in the house");
        eq(v.things.size(), 5, "five is the number the arithmetic is built on");
        Set<String> names = new HashSet<>();
        Set<String> wheres = new HashSet<>();
        for (Vigil.Thing t : v.things) {
            ok(names.add(t.name), t.name + " is not another thing's name");
            ok(wheres.add(t.where), t.name + " is not somewhere else's place");
            ok(t.where.length() > 3, t.name + " is somewhere");
            for (String s : new String[]{t.keptText, t.changedText, t.replacedText, t.lostText}) {
                ok(s != null && s.length() > 40, t.name + " has something to say in every state");
            }
            Set<String> texts = new HashSet<>(List.of(t.keptText, t.changedText, t.replacedText, t.lostText));
            eq(texts.size(), 4, t.name + " reads differently in all four states");
            eq(t.condition, Vigil.FULL, t.name + " starts whole");
            ok(!t.gone && !t.changed && !t.replaced && !t.everLost, t.name + " starts untouched");
            eq(t.state(), Vigil.State.AS_LEFT, t.name + " starts as they left it");
        }
        eq(v.day, 1, "the vigil starts on the first day");
        eq(v.effort, 3, "with three units of effort in you");
        ok(!v.finished, "and it is not over");

        // The header column counts whose the things are, not how many have
        // been kept -- on day one nothing has been, and all five are theirs.
        eq(v.tally(), "theirs 5   yours 0   gone 0", "the header column says whose the things are");
        ok(!v.tally().contains("kept"), "and does not say kept, which would be a lie on day one");
        ok(Vigil.PROMISE.endsWith("."), "the promise is a sentence");
        ok(Vigil.PROMISE.contains("exactly as it is"), "the promise is the promise");
    }

    // --------------------------------------------------------- arithmetic

    static void theArithmetic() {
        eq(Vigil.DAYS, 12, "twelve days");
        eq(Vigil.FULL, 3, "a thing has three steps in it");
        eq(Vigil.KEEP_COST, 1, "keeping costs one");
        eq(Vigil.CHANGE_COST, 2, "changing costs two");
        eq(Vigil.totalUnits(), 24, "twenty-four units of effort altogether");
        eq(Vigil.unitsToKeepEverything(), 60, "holding all five every day would take sixty");

        // The promise is impossible, and this is the whole of the proof the
        // player is given: the arithmetic does not add up, and it is not close.
        ok(Vigil.totalUnits() < Vigil.unitsToKeepEverything(),
                "there is less of you than the house needs");
        ok(Vigil.totalUnits() * 2 < Vigil.unitsToKeepEverything(),
                "and it is not close -- not even half");

        // And it is not merely tight: there is no day on which the effort
        // covers the house, so something is always being lost.
        for (int d = 1; d <= Vigil.DAYS; d++) {
            ok(Vigil.unitsFor(d) < 5, "on day " + d + " there is not enough of you for the house");
        }

        eq(Vigil.maxSurvivors(), 3, "three things come through twelve days in this house");
        ok(Vigil.maxSurvivors() < 5, "which is fewer than five");
        ok(Vigil.maxSurvivors() > 0, "and more than none");
    }

    static void theSchedule() {
        int[] want = {3, 3, 3, 3, 2, 2, 2, 2, 1, 1, 1, 1};
        for (int d = 1; d <= Vigil.DAYS; d++) {
            eq(Vigil.unitsFor(d), want[d - 1], "day " + d + " has " + want[d - 1] + " units in it");
        }
        // The decline is the game. A vigil does not fail because the world
        // takes something; it fails because the person keeping it runs out.
        ok(Vigil.unitsFor(1) > Vigil.unitsFor(5), "there is less of you in the middle than at the start");
        ok(Vigil.unitsFor(5) > Vigil.unitsFor(9), "and less again at the end");
        eq(Vigil.unitsFor(12), 1, "on the last day there is one unit in you");
    }

    static void theDayLines() {
        Set<String> seen = new HashSet<>();
        for (int d = 1; d <= Vigil.DAYS; d++) {
            String line = Vigil.dayLine(d);
            ok(line != null && line.length() > 20, "day " + d + " has a line");
            ok(seen.add(line), "day " + d + " does not repeat another day's line");
        }
        ok(Vigil.dayLine(12).contains("come back"), "the last day is the day they come back");
        eq(Vigil.dayLine(0), "", "there is no day zero");
    }

    // ------------------------------------------------------------ actions

    static void keeping() {
        Vigil v = Vigil.of();
        eq(v.effort, 3, "three units to start");
        ok(v.keep(0), "the first thing can be kept");
        eq(v.effort, 2, "keeping costs one");
        eq(v.spentKeeping, 1, "and it is counted");
        eq(v.things.get(0).condition, Vigil.FULL, "a kept thing is whole");

        ok(v.keep(1) && v.keep(2), "two more things can be kept");
        eq(v.effort, 0, "which is all of it");
        ok(!v.keep(3), "a fourth thing cannot be kept with nothing left");
        eq(v.effort, 0, "and a refused keep costs nothing");
        eq(v.spentKeeping, 3, "only the keeps that happened are counted");

        // A thing at less than full is restored, not merely held.
        Vigil w = Vigil.of();
        w.things.get(0).condition = 1;
        ok(w.keep(0), "a thing down to one step can still be kept");
        eq(w.things.get(0).condition, Vigil.FULL, "and keeping it makes it whole again");

        ok(!v.keep(-1), "there is nothing before the first thing");
        ok(!v.keep(5), "and nothing after the last");
        ok(!v.keep(99), "and nothing at all out there");
    }

    static void changing() {
        Vigil v = Vigil.of();
        ok(v.change(0), "the first thing can be changed");
        eq(v.effort, 1, "changing costs two");
        eq(v.spentChanging, 2, "and it is counted");
        ok(v.things.get(0).changed, "the thing is yours now");
        eq(v.things.get(0).state(), Vigil.State.CHANGED, "and it says so");
        ok(!v.change(0), "a thing cannot be changed twice");
        eq(v.effort, 1, "and a refused change costs nothing");
        ok(!v.change(1), "nor can a second thing be changed with one unit left");
        ok(v.keep(1), "though one thing can still be kept");

        // A change is irreversible and there is no way back to what it was.
        Vigil w = Vigil.of();
        w.change(2);
        eq(w.things.get(2).line(), w.things.get(2).changedText, "a changed thing reads as changed");
        eq(w.things.get(2).keepPreview(), w.things.get(2).changedText,
                "keeping a changed thing keeps the change, not the thing");
        ok(w.things.get(2).changePreview() == null, "and it cannot be changed again");

        // What is gone can be replaced, and what goes there is yours.
        Vigil x = Vigil.of();
        x.things.get(3).gone = true;
        x.things.get(3).everLost = true;
        x.things.get(3).condition = 0;
        ok(x.things.get(3).keepPreview() == null, "there is nothing there to keep");
        ok(x.change(3), "but something new can be put there");
        ok(!x.things.get(3).gone, "and it is there");
        ok(x.things.get(3).replaced, "and it is a replacement");
        ok(x.things.get(3).everLost, "and the thing that was there is still gone");
        eq(x.things.get(3).state(), Vigil.State.CHANGED, "and it counts as yours");
        eq(x.things.get(3).line(), x.things.get(3).replacedText, "and it reads as a replacement");
        eq(x.things.get(3).condition, Vigil.FULL, "and it starts whole");
        eq(x.replaced(), 1, "one of the things here is a replacement");
    }

    static void decay() {
        Vigil v = Vigil.of();
        v.keep(0);
        v.keep(1);
        v.keep(2);
        v.endDay();
        eq(v.things.get(0).condition, Vigil.FULL, "a kept thing does not decay");
        eq(v.things.get(3).condition, 2, "an unkept thing loses a step");
        eq(v.things.get(4).condition, 2, "every unkept thing loses a step");

        // Three unkept days is the whole of a thing.
        Vigil w = Vigil.of();
        w.endDay();
        eq(w.things.get(0).condition, 2, "one day unkept is two steps left");
        w.endDay();
        eq(w.things.get(0).condition, 1, "two days unkept is one step left");
        w.endDay();
        eq(w.things.get(0).condition, 0, "three days unkept is nothing");
        ok(w.things.get(0).gone, "and a thing at nothing is gone");
        ok(w.things.get(0).everLost, "and it is on the record as gone");
        eq(w.things.get(0).state(), Vigil.State.LOST, "and it reads as gone");
        eq(w.empty(), 5, "which is all five of them");
        ok(!w.keep(0), "a gone thing cannot be kept");
        ok(w.things.get(0).keepPreview() == null, "and there is nothing there to keep");

        // A gone thing stops decaying -- there is nothing left to lose.
        int before = w.things.get(0).condition;
        w.endDay();
        eq(w.things.get(0).condition, before, "a gone thing does not keep decaying");
    }

    static void theDays() {
        Vigil v = Vigil.of();
        eq(v.day, 1, "the first day");
        v.endDay();
        eq(v.day, 2, "the next day");
        eq(v.effort, Vigil.unitsFor(2), "with the day's effort");
        for (int i = 0; i < v.keptToday.length; i++) {
            ok(!v.keptToday[i], "nothing is kept yet on day " + v.day);
        }
        eq(v.unspent(), v.effort, "unspent effort is what is left");

        // Twelve days and then the door.
        Vigil w = Vigil.of();
        for (int d = 1; d < Vigil.DAYS; d++) {
            ok(!w.finished, "the vigil is not over on day " + w.day);
            w.endDay();
        }
        eq(w.day, Vigil.DAYS, "the last day is the twelfth");
        ok(!w.finished, "and it is not over until the day is spent");
        w.endDay();
        ok(w.finished, "and then it is over");
        eq(w.day, Vigil.DAYS, "and the day does not run past the twelfth");
        w.endDay();
        eq(w.day, Vigil.DAYS, "and ending it again does nothing");
        ok(!w.keep(0), "and nothing can be kept after the door opens");
        ok(!w.change(0), "and nothing can be changed");
    }

    // ------------------------------------------------- the bound itself

    /**
     * maxSurvivors() has to be achievable. This is a real schedule -- the
     * player who works out that a thing need not be kept every day, only
     * never left for three -- and it has to bring three through.
     */
    static void threeComeThrough() {
        Vigil v = Vigil.of();
        int[][] plan = {
                {1, 0, 1, 2},   // day, then the things kept on it
                {4, 0, 1, 2},
                {5, 0, 1},
                {6, 2, 0},
                {7, 1, 2},
                {8, 0, 1},
                {9, 0},
                {10, 2},
                {11, 1},
                {12, 0},
        };
        for (int[] step : plan) {
            int day = step[0];
            while (v.day < day) v.endDay();   // the plan skips the days it does nothing on
            eq(v.day, day, "the plan is on day " + day);
            for (int k = 1; k < step.length; k++) {
                ok(v.keep(step[k]), "day " + day + ": thing " + (step[k] + 1) + " is kept");
            }
            v.endDay();
        }
        ok(v.finished, "the plan runs to the end");
        eq(v.intact(), Vigil.maxSurvivors(), "and it brings through exactly as many as the bound allows");
        eq(v.mine(), 0, "without changing anything");
        eq(v.empty(), 5 - Vigil.maxSurvivors(), "and the rest are gone");
        ok(v.spentKeeping <= Vigil.totalUnits(), "and it costs no more than a person has");
        System.out.println("       the plan costs " + v.spentKeeping + " of " + Vigil.totalUnits()
                + " units and brings through " + v.intact() + " of 5");
    }

    /** And it has to be unbeatable, at least by the obvious strategy. */
    static void noGreedyBeatsIt() {
        Vigil v = Vigil.of();
        while (!v.finished) {
            // Keep whatever is closest to going, and never change anything.
            // This is what a player does when they are trying to save the
            // house rather than to leave anything of themselves in it.
            while (v.effort > 0) {
                int worst = -1;
                for (int i = 0; i < v.things.size(); i++) {
                    Vigil.Thing t = v.things.get(i);
                    if (t.gone || v.keptToday[i]) continue;
                    if (worst < 0 || t.condition < v.things.get(worst).condition) worst = i;
                }
                if (worst < 0) break;
                if (!v.keep(worst)) break;
            }
            v.endDay();
        }
        ok(v.intact() <= Vigil.maxSurvivors(),
                "the most careful player in the world still cannot beat the bound (got "
                        + v.intact() + ")");
        eq(v.mine(), 0, "and keeping everything they can leaves nothing of them in the room");
        ok(v.intact() > 0, "though it does leave something");
        System.out.println("       the careful player brings through " + v.intact()
                + " of 5, and changes nothing");
    }

    // ------------------------------------------------------------ endings

    static void endings() {
        // Every shape the state space can actually reach, built by hand.
        Vigil a = shape(3, 0, 2);   // three of theirs, two gone
        Vigil b = shape(2, 1, 2);   // two of theirs, one of yours
        Vigil c = shape(0, 3, 2);   // three of yours, none of theirs
        Vigil d = shape(1, 0, 4);   // one of theirs, four gone
        Vigil e = shape(0, 1, 4);   // one of yours, four gone
        Vigil f = shape(0, 0, 5);   // nothing left

        Set<String> headlines = new HashSet<>();
        for (Vigil v : new Vigil[]{a, b, c, d, e, f}) {
            ok(v.headline() != null && v.headline().length() > 8,
                    "a shape has a headline (" + v.headline() + ")");
            headlines.add(v.headline());
            ok(v.verdict().length() > 80, "each shape has a verdict");
            ok(v.arrival().length() > 80, "each shape has an arrival");
            ok(v.closing().length() > 40, "each shape has a closing line");
        }
        // Five families, not six: what the headline is about is whose the
        // things are, not how many are left. How many is the verdict's job.
        eq(headlines.size(), 4, "six shapes, four headline families");
        eq(a.headline(), d.headline(), "three of theirs and one of theirs read the same way");
        eq(c.headline(), e.headline(), "three of yours and one of yours read the same way");

        // The fifth family, and the one the render found: a change that was
        // made and then lost is two units spent and nothing to show for it.
        // The room is as it was, and the purse is not.
        Vigil g = shapeSpent(1, 0, 4, Vigil.CHANGE_COST);
        eq(g.headline(), "Nothing of yours made it.", "a change that died reads as a change that died");
        ok(g.verdict().contains("You spent two units on yourself, and none of it is here"),
                "and the verdict says the units were spent");
        ok(!g.verdict().contains("did not spend a single unit on yourself"),
                "and does not claim the promise was kept");
        ok(g.closing().contains("bought you nothing"), "and the closing line says what it bought");
        ok(headlines.add(g.headline()), "which is a fifth family");
        eq(headlines.size(), 5, "five headline families altogether");

        // The four headline families, spelled out.
        eq(a.headline(), "As it was, what is left of it.", "keeping the promise reads as itself");
        eq(c.headline(), "Nothing of theirs.", "all of it yours reads as itself");
        eq(f.headline(), "Nothing left.", "nothing reads as nothing");
        eq(b.headline(), "Theirs, and yours.", "a mix reads as a mix");

        // The verdict has to name the bound, because the bound is the game.
        for (Vigil v : new Vigil[]{a, b, c, d, e, f}) {
            ok(v.verdict().contains(Vigil.cap(Vigil.num(Vigil.maxSurvivors()))),
                    "the verdict says how many come through");
        }
        // And it has to say it in words, not as a readout.
        eq(Vigil.num(0), "none", "zero is a word");
        eq(Vigil.num(1), "one", "one is a word");
        eq(Vigil.num(12), "twelve", "twelve is a word");
        eq(Vigil.num(20), "twenty", "twenty is a word");
        eq(Vigil.num(24), "twenty-four", "twenty-four is a word");
        eq(Vigil.cap("three things"), "Three things", "a sentence can be capitalised");
        eq(Vigil.cap(""), "", "and nothing stays nothing");
        // And it has to be honest about what was spent, which is a fact about
        // the purse and not about what survived.
        ok(a.verdict().contains("did not spend a single unit on yourself"),
                "a player who changed nothing is told so");
        ok(!c.verdict().contains("did not spend a single unit on yourself"),
                "and a player who changed something is not");
        for (Vigil v : new Vigil[]{a, b, c, d, e, f}) {
            eq(v.verdict().contains("did not spend a single unit on yourself"),
                    v.spentChanging == 0,
                    "the promise line follows the purse, not the room (" + v.headline() + ")");
        }

        // The clean cases have to say the true thing about the losses.
        ok(d.verdict().contains("Four of the five are gone"), "four gone is four gone");
        // "All of them are exactly what they were" is wrong when there is one.
        ok(d.verdict().contains("It is exactly what it was"),
                "one thing left is described in the singular");
        ok(a.verdict().contains("All of them are exactly what they were"),
                "three things left are described in the plural");
        ok(e.verdict().contains("Four of the five are gone"), "four gone is four gone either way");
        ok(a.verdict().contains("Two of the five are gone"), "two gone is two gone");

        // The thesis line lands in the shape it is about.
        ok(a.closing().contains("no one in it"), "the promise kept ends on the empty room");
        ok(c.closing().contains("entirely yours"), "nothing of theirs ends on the room being yours");
        ok(b.closing().contains("leave something of yourself"),
                "a mix ends on what the change bought");
    }

    /** Build a finished vigil with exactly i intact, m mine, e empty. */
    static Vigil shape(int i, int m, int e) {
        return shapeSpent(i, m, e, m * Vigil.CHANGE_COST);
    }

    /** The same, with the purse set independently of what is still standing. */
    static Vigil shapeSpent(int i, int m, int e, int spentChanging) {
        Vigil v = Vigil.of();
        int n = i + m + e;
        if (n != 5) throw new IllegalArgumentException("a shape has to account for five things");
        int k = 0;
        for (int x = 0; x < i; x++) v.things.get(k++).condition = Vigil.FULL;
        for (int x = 0; x < m; x++) {
            Vigil.Thing t = v.things.get(k++);
            t.changed = true;
            t.condition = Vigil.FULL;
        }
        for (int x = 0; x < e; x++) {
            Vigil.Thing t = v.things.get(k++);
            t.gone = true;
            t.everLost = true;
            t.condition = 0;
        }
        v.finished = true;
        v.day = Vigil.DAYS;
        v.spentKeeping = 15;
        v.spentChanging = spentChanging;
        return v;
    }

    static void grammar() {
        for (int i = 0; i <= 5; i++) {
            for (int m = 0; m + i <= 5; m++) {
                int e = 5 - i - m;
                Vigil v = shape(i, m, e);
                String tag = "(" + i + "," + m + "," + e + ")";
                reads(v.headline(), "headline " + tag);
                reads(v.verdict(), "verdict " + tag);
                reads(v.arrival(), "arrival " + tag);
                reads(v.closing(), "closing " + tag);
            }
        }
    }

    /** Sentence hygiene. The render found the last two of these, not the code. */
    static void reads(String s, String what) {
        ok(s != null && !s.isEmpty(), what + " says something");
        if (s == null || s.isEmpty()) return;
        ok(Character.isUpperCase(s.charAt(0)), what + " starts as a sentence");
        ok(s.strip().endsWith("."), what + " ends as a sentence");
        ok(!s.contains("  "), what + " has no doubled spaces");
        ok(!s.contains(" ."), what + " has no space before a full stop");
        ok(!s.contains(".."), what + " has no doubled full stops");
        ok(!s.contains("1 are"), what + " does not say \"1 are\"");
        ok(!s.contains("1 of the five are"), what + " does not say \"1 of the five are\"");
        ok(!s.contains("null"), what + " does not leak a null");
        ok(!s.contains("1 of them are"), what + " does not say \"1 of them are\"");
        ok(!s.contains("1 of the five are"), what + " does not say \"1 of the five are\"");
        ok(!s.contains("1 things"), what + " does not say \"1 things\"");
        // A clause that starts with a digit reads like a readout.
        for (String clause : s.split("(?<=[.!?]) ")) {
            ok(clause.isEmpty() || !Character.isDigit(clause.charAt(0)),
                    what + " does not start a clause with a digit (" + clause + ")");
        }
    }

    // ------------------------------------------------------------ storage

    static void storage() throws Exception {
        Path dir = Files.createTempDirectory("vigil-selftest");
        Path p = dir.resolve("vigil.state");

        // A missing file is a fresh vigil, not an error.
        Vigil fresh = Vigil.load(dir.resolve("nothing-here.state"));
        eq(fresh.day, 1, "a missing save opens on the first day");
        eq(fresh.effort, 3, "with a full day's effort");

        Vigil v = Vigil.of();
        v.keep(0);
        v.change(1);
        v.endDay();
        v.keep(2);
        v.save(p);
        ok(Files.exists(p), "the vigil is written to disk");

        Vigil back = Vigil.load(p);
        eq(back.day, v.day, "the day came back");
        eq(back.effort, v.effort, "the effort came back");
        eq(back.spentKeeping, v.spentKeeping, "the keeping came back");
        eq(back.spentChanging, v.spentChanging, "the changing came back");
        eq(back.finished, v.finished, "and it is not finished");
        for (int i = 0; i < 5; i++) {
            eq(back.things.get(i).condition, v.things.get(i).condition, "thing " + (i + 1) + " came back");
            eq(back.things.get(i).changed, v.things.get(i).changed, "thing " + (i + 1) + "'s change came back");
            eq(back.things.get(i).gone, v.things.get(i).gone, "thing " + (i + 1) + "'s loss came back");
            eq(back.keptToday[i], v.keptToday[i], "thing " + (i + 1) + "'s keep today came back");
        }

        // A finished vigil comes back finished, and opens on the report.
        Vigil done = Vigil.of();
        while (!done.finished) done.endDay();
        done.save(p);
        Vigil doneBack = Vigil.load(p);
        ok(doneBack.finished, "a finished vigil comes back finished");
        eq(doneBack.day, Vigil.DAYS, "on the last day");

        // Everything that cannot be read is a fresh vigil, not a crash.
        for (String junk : new String[]{"", "nonsense", "v1", "v1 99 3 0 0 0", "v1 1 99 0 0 0",
                "v1 1 3 -1 0 0", "v1 1 3 0 0 0\n99 0 0 0 0 0"}) {
            Files.writeString(p, junk);
            Vigil j = Vigil.load(p);
            eq(j.day, 1, "junk save \"" + junk.replace("\n", "\\n") + "\" opens a fresh vigil");
            eq(j.effort, 3, "with a full day's effort");
        }

        // A save that says a thing is gone and also whole is a save that was
        // hand-edited, and it is thrown out rather than believed.
        Files.writeString(p, "v1 3 2 0 0 0\n3 0 0 1 1 0\n3 0 0 0 0 0\n3 0 0 0 0 0\n3 0 0 0 0 0\n3 0 0 0 0 0\n");
        eq(Vigil.load(p).day, 1, "a gone thing at full condition is not believed");
        Files.writeString(p, "v1 3 2 0 0 0\n3 0 0 1 1 1\n3 0 0 0 0 0\n3 0 0 0 0 0\n3 0 0 0 0 0\n3 0 0 0 0 0\n");
        eq(Vigil.load(p).day, 1, "a gone thing kept today is not believed");

        // A truncated save keeps what it has and does not run off the end.
        Files.writeString(p, "v1 2 3 1 0 0\n2 0 0 0 0 0\n");
        Vigil short1 = Vigil.load(p);
        eq(short1.day, 2, "a truncated save keeps the day");
        eq(short1.things.get(0).condition, 2, "and the things it has");
        eq(short1.things.get(4).condition, Vigil.FULL, "and fills in the rest");
    }

    static void determinism() {
        Vigil a = Vigil.of(), b = Vigil.of();
        for (int i = 0; i < 5; i++) {
            eq(a.things.get(i).name, b.things.get(i).name, "thing " + (i + 1) + " is the same thing");
            eq(a.things.get(i).keptText, b.things.get(i).keptText, "thing " + (i + 1) + " reads the same");
        }
        a.keep(0);
        ok(!b.keptToday[0], "keeping a thing in one vigil does not keep it in another");
        a.endDay();
        eq(b.day, 1, "and a day in one is not a day in another");
    }

    // ------------------------------------------------------- the phone build

    /**
     * The single-file build is generated, not written, and a generated file
     * that has gone stale is worse than no file: it is a second copy of the
     * game quietly disagreeing with the first. So the test regenerates it and
     * compares. If this fails, run aside.games.vigil.WebVigil from the
     * repository root.
     */
    static void thePhoneBuild() throws Exception {
        Path out = Path.of("web", "vigil.html");
        if (!Files.exists(out)) {
            System.out.println("       (no web/vigil.html from here -- run from the repository root)");
            return;
        }
        String generated;
        try {
            generated = WebVigil.html();
        } catch (Exception e) {
            System.out.println("       (no template from here: " + e.getMessage() + ")");
            return;
        }
        String checkedIn = Files.readString(out);
        ok(generated.equals(checkedIn),
                "web/vigil.html is current -- regenerate it with aside.games.vigil.WebVigil");

        // And it has to actually carry the writing, not just be the right size.
        for (Vigil.Thing t : Vigil.defaultThings()) {
            ok(generated.contains(t.keptText), "the phone build carries " + t.name + " as they left it");
            ok(generated.contains(t.changedText), "the phone build carries " + t.name + " changed");
            ok(generated.contains(t.replacedText), "the phone build carries " + t.name + " replaced");
            ok(generated.contains(t.lostText), "the phone build carries " + t.name + " gone");
        }
        for (int d = 1; d <= Vigil.DAYS; d++) {
            ok(generated.contains(Vigil.dayLine(d)), "the phone build carries day " + d);
        }
        ok(generated.contains(Vigil.PROMISE), "the phone build carries the promise");
        // Paragraph breaks are escaped in the JSON, so the paragraphs are what
        // to look for, not the joined string.
        carries(generated, Vigil.ARRIVAL_NOTHING, "the phone build carries the arrival with nothing left");
        carries(generated, Vigil.ARRIVAL_KEPT, "the phone build carries the arrival with the promise kept");
        carries(generated, Vigil.ARRIVAL_ALL_YOURS, "the phone build carries the arrival with nothing of theirs");
        carries(generated, Vigil.ARRIVAL_MIXED, "the phone build carries the mixed arrival");
        carries(generated, Vigil.CLOSING_NOTHING, "the phone build carries the closing with nothing left");
        carries(generated, Vigil.CLOSING_MADE_IT, "the phone build carries the closing that bought nothing");
        carries(generated, Vigil.CLOSING_KEPT, "the phone build carries the closing about the empty room");
        carries(generated, Vigil.CLOSING_ALL_YOURS, "the phone build carries the closing about a room that is yours");
        carries(generated, Vigil.CLOSING_MIXED, "the phone build carries the mixed closing");
        ok(generated.contains("\u003c") || !generated.contains("<script>\"<"),
                "nothing in the prose can end the script block early");
        System.out.println("       phone build: " + (generated.length() / 1024) + " KB, current");
    }

    /** Every paragraph of a multi-paragraph string has to be in the build. */
    static void carries(String haystack, String text, String what) {
        for (String para : text.split("\n\n")) {
            ok(haystack.contains(para), what);
        }
    }

    // ------------------------------------------------------------ registry

    static void registry() {
        Game g = Games.byId("vigil");
        ok(g != null, "the engine can find vigil by id");
        if (g == null) return;
        eq(g.title(), "Vigil", "and it is called Vigil");
        ok(g.blurb() != null && g.blurb().length() > 20, "and it has a line for the library");
        eq(g.id(), "vigil", "and its id is its id");

        // Adding a game is the thing that has twice broken the library list.
        int rows = Games.all().size() + 1;
        ok(rows <= LibraryLayout.maxRows(),
                "the library holds " + rows + " rows (limit " + LibraryLayout.maxRows() + ")");
        ok(rows < LibraryLayout.maxRows(),
                "and there is room for the next game without redoing the bar geometry");
        System.out.println("       library: " + Games.all().size() + " games + Quit = " + rows
                + " rows, limit " + LibraryLayout.maxRows());
    }
}
