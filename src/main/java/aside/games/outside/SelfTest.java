package aside.games.outside;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Headless checks for outside.
 *
 * The point of these is not coverage. It is that the dispatcher's rule IS
 * the game, so it has to be true independently of anything drawn. If "it
 * acts on the first thing it ever heard" were only true on screen, the game
 * would be a picture of a game.
 *
 * Run: java -cp classes aside.games.outside.SelfTest
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
        theRule();
        silenceIsTheOnlyEdit();
        theTruck();
        theSeventhDay();
        scoring();
        storage();
        playthrough();
        System.out.println((checks - failed) + "/" + checks + " checks passed"
                + (failed == 0 ? "" : "  --  " + failed + " FAILED"));
        if (failed > 0) System.exit(1);
    }

    static boolean legal(Outside.Concern c, String v) {
        return switch (c) {
            case CROSSING -> v.equals("ford") || v.equals("bridge") || v.equals("wait");
            case LOAD     -> v.equals("full") || v.equals("light") || v.equals("empty");
            case HOUR     -> v.equals("dawn") || v.equals("noon") || v.equals("dark");
        };
    }

    /** How many of the seven days a fact's value turns out to be right on. */
    static int scoreOf(Outside.Fact x) {
        int n = 0;
        for (Outside.Day d : Outside.DAYS) if (d.truth.get(x.concern).equals(x.value)) n++;
        return n;
    }

    static int bestScoreOf(List<Outside.Fact> xs) {
        int best = 0;
        for (Outside.Fact x : xs) best = Math.max(best, scoreOf(x));
        return best;
    }

    // -------------------------------------------------------------- content

    static void content() {
        eq(Outside.DAYS.size(), 7, "seven days");
        eq(Outside.DECISIONS, Outside.DAYS.size() * 3, "three decisions a day");

        int total = 0;
        for (Outside.Day d : Outside.DAYS) {
            eq(d.facts.size(), 4, "four things visible on " + d.heading);
            total += d.facts.size();
            ok(!d.scene.isBlank(), d.heading + " has a scene");
            ok(!d.title.isBlank(), d.heading + " has a title");

            // Every concern must be offered every day. If a day offered only
            // one concern, the player would have no choice about which frame
            // to spend, and the game would be a cutscene.
            for (Outside.Concern c : Outside.Concern.values()) {
                boolean offered = false;
                for (Outside.Fact x : d.facts) if (x.concern == c) offered = true;
                ok(offered, d.heading + " offers something about " + c.label);
                ok(legal(c, d.truth.get(c)), d.heading + " has a legal truth for " + c.label);
            }

            // Every day has to contain at least one thing that is true, or
            // the ridge would be lying to the player as well as to the room.
            boolean anyRight = false;
            for (Outside.Fact x : d.facts) {
                if (x.value.equals(d.truth.get(x.concern))) anyRight = true;
            }
            ok(anyRight, d.heading + " contains something that is true");
        }
        eq(total, 28, "twenty-eight things are visible across the week");

        // Ids are identity. A duplicate would make two different days' facts
        // the same object to the dispatcher.
        for (Outside.Day a : Outside.DAYS) {
            for (Outside.Fact x : a.facts) {
                for (Outside.Day b : Outside.DAYS) {
                    for (Outside.Fact y : b.facts) {
                        if (x != y) ok(!x.id.equals(y.id), "fact id unique: " + x.id);
                    }
                }
                ok(legal(x.concern, x.value), x.id + " has a legal value");
                ok(!x.words.isBlank(), x.id + " has words");
                eq(x.day, a.index + 1, x.id + " is stamped with its day");
                eq(Outside.fact(x.id), x, "lookup finds " + x.id);
                ok(scoreOf(x) < Outside.DAYS.size(), x.id + " does not stay true all week");
            }
        }

        // The world has to move, or there is nothing to be stale about.
        int changes = 0;
        for (int i = 1; i < Outside.DAYS.size(); i++) {
            for (Outside.Concern c : Outside.Concern.values()) {
                if (!Outside.DAYS.get(i).truth.get(c).equals(Outside.DAYS.get(i - 1).truth.get(c))) changes++;
            }
        }
        ok(changes >= 6, "the world moves during the week (" + changes + " changes)");

        // The seventh day is the one that has to hurt: the world recovers and
        // the dispatcher cannot.
        Outside.Day six = Outside.DAYS.get(5), seven = Outside.DAYS.get(6);
        for (Outside.Concern c : Outside.Concern.values()) {
            ok(!six.truth.get(c).equals(seven.truth.get(c)),
                    "the world changes on the last day: " + c.label);
        }

        // The shape of the temptation. The first day offers nothing that
        // lasts -- every one of its four things is true for at most three of
        // the seven days. The second day offers one thing that lasts four
        // days and one that lasts two, and from the ridge they look the same.
        ok(bestScoreOf(Outside.DAYS.get(0).facts) <= 3, "nothing on the first day lasts");
        eq(bestScoreOf(Outside.DAYS.get(1).facts), 4, "the second day offers something that lasts");
        eq(scoreOf(Outside.fact("d2a")), 4, "day two's lasting thing is the greasy road");
        eq(scoreOf(Outside.fact("d2d")), 2, "day two's other crossing report lasts two days");

        // Each of the three lasting values has a window of several days, so
        // a day spent on something else does not lose the option -- it only
        // pushes it later, which is the whole cost.
        for (String id : List.of("d2b", "d3a", "d4c", "d5c", "d6a")) {
            eq(Outside.fact(id).value, "wait", id + " is a crossing=wait report");
        }
        for (String id : List.of("d3c", "d4a", "d5b", "d6b")) {
            eq(Outside.fact(id).value, "light", id + " is a load=light report");
        }
        for (String id : List.of("d2a", "d5a", "d6c")) {
            eq(Outside.fact(id).value, "noon", id + " is a hour=noon report");
        }
    }

    // ----------------------------------------------------------- the rule

    static void theRule() {
        Outside o = new Outside();
        ok(o.earliest(Outside.Concern.CROSSING) == null, "it starts knowing nothing");
        ok(!o.route(0).ran, "it does not move on the first morning");

        Outside.Fact d1 = Outside.fact("d1a");   // crossing = ford
        Outside.Fact d3 = Outside.fact("d3a");   // crossing = wait
        o.report(d1);
        eq(o.earliest(Outside.Concern.CROSSING), d1, "the first thing it heard is the thing it holds");

        o.report(d3);
        eq(o.earliest(Outside.Concern.CROSSING), d1, "a later report cannot displace an earlier one");
        eq(o.heard.size(), 2, "it keeps both, in the order it heard them");
        eq(o.heard.get(0), d1, "the order is the order they arrived");
        eq(o.heard.get(1), d3, "the order is the order they arrived");

        // Reporting the same thing twice is not a way to make it louder.
        o.report(d1);
        eq(o.heard.size(), 2, "repeating yourself changes nothing");

        // And the decision it makes is the first one's, on every later day.
        for (int i = 1; i < Outside.DAYS.size(); i++) {
            Outside.Routing r = o.route(i);
            for (Outside.Decision dec : r.decisions) {
                if (dec.concern == Outside.Concern.CROSSING) {
                    eq(dec.did, "ford", "day " + (i + 1) + " still crosses at the ford");
                    eq(dec.fromDay, 1, "day " + (i + 1) + " learned it on day one");
                }
            }
        }

        // A concern it has never been told about is a concern it does nothing about.
        Outside.Routing r = o.route(0);
        for (Outside.Decision dec : r.decisions) {
            if (dec.concern != Outside.Concern.CROSSING) {
                eq(dec.did, null, "it does nothing about " + dec.concern.label);
                eq(dec.fromDay, 0, "and it learned that from nobody");
                ok(!dec.right(), "doing nothing about " + dec.concern.label + " is not a decision");
            }
        }
    }

    // -------------------------------------------- silence is the only edit

    static void silenceIsTheOnlyEdit() {
        // Say nothing about the hour for three days, then say something.
        // The thing said on the fourth day is the first thing it heard, so
        // it wins -- which is the whole reason silence is worth a day.
        Outside o = new Outside();
        o.report(Outside.fact("d1a"));      // crossing, so the truck can go
        o.report(Outside.fact("d3c"));      // load
        ok(o.earliest(Outside.Concern.HOUR) == null, "nothing said about the hour yet");

        Outside.Fact late = Outside.fact("d4d");     // hour = dark, offered on day four
        o.report(late);
        eq(o.earliest(Outside.Concern.HOUR), late, "a late report wins a concern that was left open");
        eq(o.route(3).decisions.get(2).fromDay, 4, "and it is dated to the day it arrived");

        // The same is not true of a concern already spoken about.
        Outside.Fact later = Outside.fact("d6c");    // hour = noon, offered on day six
        o.report(later);
        o.file();
        eq(o.earliest(Outside.Concern.HOUR), late, "a later report loses a concern already spoken about");
        ok(o.lastReportIgnored(later), "the game can tell that the report was ignored");
        ok(!o.lastReportIgnored(late), "and that an earlier one was not ignored");

        // Silence is always available, and it is never a report.
        Outside p = new Outside();
        p.report(null);
        eq(p.heard.size(), 0, "reporting nothing reports nothing");
    }

    // ------------------------------------------------------------- the truck

    static void theTruck() {
        // The truck only goes if it knows how to cross. A room that has been
        // told about the load and the hour and not the water does not move.
        Outside o = new Outside();
        o.report(Outside.fact("d1c"));   // load
        o.report(Outside.fact("d1d"));   // hour
        ok(!o.route(0).ran, "it does not move without knowing how to cross");
        eq(o.route(0).correct(), 0, "a day the truck does not run is a day of nothing");

        o.report(Outside.fact("d1b"));   // crossing
        ok(o.route(0).ran, "once it knows how to cross, it goes");
        ok(o.route(0).correct() > 0, "and the day is scored");

        // The day it is scored on is the day it acted, not the day it was told.
        Outside q = new Outside();
        q.report(Outside.fact("d1a"));   // crossing = ford, day one
        q.report(Outside.fact("d1c"));   // load = full, day one
        q.report(Outside.fact("d1d"));   // hour = dawn, day one
        eq(q.route(0).correct(), 3, "on day one, day one's facts are right");
        eq(q.route(6).correct(), 2, "on day seven, two of day one's three are still right");
        ok(!q.route(6).decisions.get(0).right(), "and the crossing is the one that is not");
    }

    // -------------------------------------------------------- the seventh day

    static void theSeventhDay() {
        // The seventh day is the one where the world recovers. If you have
        // said anything about a concern before, the last report cannot land.
        Outside o = new Outside();
        o.report(Outside.fact("d2b"));   // crossing = wait, day two
        o.report(Outside.fact("d3c"));   // load = light, day three
        o.report(Outside.fact("d4d"));   // hour = dark, day four

        Outside.Fact last = Outside.fact("d7a");   // crossing = bridge, day seven
        o.report(last);
        o.file();
        ok(o.lastReportIgnored(last), "the last report is ignored");
        eq(o.earliest(Outside.Concern.CROSSING).day, 2, "it still believes day two");

        Outside.Routing r = o.route(6);
        eq(r.correct(), 0, "on the last day it is wrong about everything");
        for (Outside.Decision dec : r.decisions) {
            ok(dec.fromDay < 7, "every belief on the last day is from an earlier day");
        }

        // But a concern held open all week does land on the last day. That is
        // the gamble, and it is a real one: you cannot know the weather on
        // day seven when you are standing on day one.
        Outside p = new Outside();
        p.report(Outside.fact("d1a"));   // crossing only; load and hour left open
        p.report(Outside.fact("d7c"));   // load = full, day seven
        eq(p.earliest(Outside.Concern.LOAD).day, 7, "a concern held open all week lands on the last day");
        eq(p.route(6).decisions.get(1).did, "full", "and it acts on it");
    }

    // -------------------------------------------------------------- scoring

    static void scoring() {
        int best = Outside.bestPossible();
        eq(best, 11, "the best week is eleven of twenty-one");
        ok(best < Outside.DECISIONS, "the week cannot be played perfectly");

        // The best week is not reachable by only saying things that are
        // already true. You have to report the weather that is coming, and
        // you have to do it before you can be sure of it.
        ok(!bestAllTrue(), "the best week requires saying something that is not yet true");

        // And the best week is not the week a player gets by saying the first
        // thing they see, which is the week the game is built to punish.
        ok(naiveScore() < best, "saying the first thing you see is worse than the best week");

        System.out.println("      best week: " + best + " of " + Outside.DECISIONS
                + ";  first-thing-every-day: " + naiveScore());

        // Scoring is additive over days, and a day is scored as it was acted.
        Outside o = new Outside();
        o.report(Outside.fact("d1a"));
        o.report(Outside.fact("d1c"));
        o.report(Outside.fact("d1d"));
        int sum = 0;
        for (int i = 0; i < Outside.DAYS.size(); i++) sum += o.route(i).correct();
        // Day one's facts are the world's facts on days one and two, and two
        // of the three come back on the last day. The five days in the middle
        // are the ones it cannot see.
        eq(sum, 8, "day one's facts are right for two days, then wrong for four, then half right");
        eq(o.correct, 0, "nothing is scored until the day is filed");
        o.file();
        eq(o.correct, 3, "filing scores the day");
        eq(o.ranDays, 1, "and counts the day the truck ran");
        o.next();
        o.file();
        eq(o.correct, 6, "the score accumulates");
        eq(o.ranDays, 2, "and so do the days the truck ran");
    }

    /** The week you get by reporting the first thing you see, every day. */
    static int naiveScore() {
        Outside o = new Outside();
        for (int i = 0; i < Outside.DAYS.size(); i++) {
            o.report(Outside.DAYS.get(i).facts.get(0));
            o.file();
            o.next();
        }
        return o.correct;
    }

    /** Is any best-scoring week made only of reports that were true that day? */
    static boolean bestAllTrue() {
        return search(0, new ArrayList<>(), Outside.bestPossible(), true);
    }

    static boolean search(int dayIndex, List<Outside.Fact> chosen, int target, boolean allTrue) {
        if (dayIndex >= Outside.DAYS.size()) {
            if (!allTrue) return false;
            Outside o = new Outside();
            o.heard.addAll(chosen);
            int n = 0;
            for (int i = 0; i < Outside.DAYS.size(); i++) n += o.route(i).correct();
            return n >= target;
        }
        if (search(dayIndex + 1, chosen, target, allTrue)) return true;
        for (Outside.Fact x : Outside.DAYS.get(dayIndex).facts) {
            chosen.add(x);
            boolean t = search(dayIndex + 1, chosen, target,
                    allTrue && x.value.equals(Outside.DAYS.get(dayIndex).truth.get(x.concern)));
            chosen.remove(chosen.size() - 1);
            if (t) return true;
        }
        return false;
    }

    // -------------------------------------------------------------- storage

    static void storage() throws Exception {
        Outside o = new Outside();
        o.report(Outside.fact("d2b"));
        o.report(Outside.fact("d3c"));
        o.file();
        o.next();

        String text = o.serialize();
        Outside back = Outside.deserialize(text);
        eq(back.heard.size(), 2, "both reports survive a round trip");
        eq(back.heard.get(0), Outside.fact("d2b"), "and in order");
        eq(back.heard.get(1), Outside.fact("d3c"), "and in order");
        eq(back.day, o.day, "the day survives");
        eq(back.correct, o.correct, "the score survives");
        eq(back.ranDays, o.ranDays, "the days the truck ran survive");
        eq(back.serialize(), text, "and it is stable under a second write");

        // A corrupt line is skipped, not fatal, and does not take the rest
        // of the log with it.
        Outside messy = Outside.deserialize(
                "# outside v1\n"
              + "day\t3\n"
              + "heard\tnonsense\n"
              + "heard\td2b\n"
              + "correct\tnot-a-number\n"
              + "garbage\n"
              + "heard\td3c\n"
              + "day\t99\n");
        eq(messy.heard.size(), 2, "a corrupt log keeps the lines it can read");
        eq(messy.day, Outside.DAYS.size(), "an out-of-range day is clamped");
        eq(messy.correct, 0, "a corrupt number falls back");

        // A finished week reopens finished, not on an empty ridge.
        Outside done = new Outside();
        done.finished = true;
        ok(Outside.deserialize(done.serialize()).finished, "a finished week stays finished");

        Path tmp = Files.createTempFile("outside", ".state");
        o.save(tmp);
        eq(Outside.load(tmp).heard.size(), 2, "it saves and loads from a file");
        Files.deleteIfExists(tmp);
        ok(Outside.load(Path.of("/tmp/definitely-not-here-outside")).heard.isEmpty(),
                "a missing save is a fresh week");
    }

    // ---------------------------------------------------------- playthrough

    static void playthrough() {
        // A player who says the first thing they see every day: the obvious
        // week, and the one the game is built to punish.
        Outside o = new Outside();
        for (int i = 0; i < Outside.DAYS.size(); i++) {
            o.report(Outside.DAYS.get(i).facts.get(0));
            o.file();
            o.next();
        }
        ok(o.finished, "seven days ends the week");
        eq(o.heard.size(), 7, "seven reports were made");
        ok(o.correct < Outside.bestPossible(), "the obvious week is not the best week");

        // The whole point, stated as a test: on the last day, not one of its
        // three beliefs is from the last day.
        Outside.Routing last = o.route(6);
        for (Outside.Decision dec : last.decisions) {
            ok(dec.fromDay < 7, "on the last day it is acting on day " + dec.fromDay);
        }

        // Reset clears everything, including the days the truck ran.
        o.heard.clear();
        o.day = 0;
        o.correct = 0;
        o.ranDays = 0;
        o.finished = false;
        eq(o.earliest(Outside.Concern.CROSSING), null, "reset clears what it holds");
        eq(o.route(0).correct(), 0, "reset clears the score");
        ok(!o.route(0).ran, "reset stops the truck");
    }
}
