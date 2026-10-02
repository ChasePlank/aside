package aside.games.lesson;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Headless checks for lesson.
 *
 * The point of these is not coverage. It is that four things in this game are
 * written by the same hand and could quietly disagree with each other:
 *
 *   - the eight rules, which the prose promises are eight different rules;
 *   - the pools, which are filed by corner and could be filed wrong;
 *   - the deal, which promises the board has a state in every corner and the
 *     shift has three states in three different ones;
 *   - the student, whose belief is supposed to be a fact about the
 *     demonstrations rather than a guess about the student.
 *
 * The one that matters most is the last. The whole game rests on a claim a
 * reader cannot check from the outside: **three demonstrations in three
 * different corners always leave exactly two rules standing, and the corner
 * they disagree about is the one you did not show.** If that were false the
 * game would still look exactly like this one and would be a game of luck.
 *
 * Run: java -cp classes aside.games.lesson.SelfTest
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
        System.out.println("=== lesson self-test ===\n");

        System.out.println("--- the eight rules ---");
        eq(Lesson.ORDER.size(), 8, "there are eight rules");
        eq(new HashSet<>(Lesson.ORDER).size(), 8, "and no two of them are the same rule");
        // The claim the game rests on: no two rules agree everywhere, so a
        // corner always tells you something. Checked on the four corners, which
        // is the whole of the space -- every state is in one of them and every
        // rule is a function of which one.
        List<String> signatures = new ArrayList<>();
        for (Lesson.Rule r : Lesson.ORDER) {
            StringBuilder b = new StringBuilder();
            for (Lesson.Quadrant q : Lesson.Quadrant.values()) {
                b.append(Lesson.opens(r, Lesson.probe(q)) ? 'O' : 'S');
            }
            signatures.add(b.toString());
        }
        Set<String> distinct = new HashSet<>(signatures);
        eq(distinct.size(), 8, "no two rules agree on all four corners");
        for (int i = 0; i < signatures.size(); i++) {
            ok(signatures.get(i).length() == 4, "rule " + Lesson.ORDER.get(i) + " is a function of the corners");
        }
        // The complement pairs, which is the structure of the rule space and
        // the reason a demonstration always leaves exactly one of each pair.
        for (Lesson.Rule r : Lesson.ORDER) {
            int mates = 0;
            for (Lesson.Rule o : Lesson.ORDER) {
                boolean opposite = true;
                for (Lesson.Quadrant q : Lesson.Quadrant.values()) {
                    if (Lesson.opens(o, Lesson.probe(q)) == Lesson.opens(r, Lesson.probe(q))) {
                        opposite = false; break;
                    }
                }
                if (opposite) mates++;
            }
            eq(mates, 1, r + " has exactly one opposite");
        }

        System.out.println("\n--- the pools ---");
        for (Lesson.Quadrant q : Lesson.Quadrant.values()) {
            List<Lesson.Reading> pool = Lesson.BOARD_POOL.get(q);
            ok(pool != null && pool.size() >= 6, "the board pool for " + q + " is big enough");
            for (Lesson.Reading x : pool) {
                eq(x.quadrant(), q, "board pool " + q + " files " + Lesson.readingLine(x) + " correctly");
            }
            List<Lesson.Reading> sp = Lesson.SHIFT_POOL.get(q);
            ok(sp != null && sp.size() >= 4, "the shift pool for " + q + " is big enough");
            for (Lesson.Reading x : sp) {
                eq(x.quadrant(), q, "shift pool " + q + " files " + Lesson.readingLine(x) + " correctly");
            }
        }
        int counts = 0;
        for (int c : Lesson.BOARD_COUNT) counts += c;
        eq(counts, Lesson.BOARD, "the corner counts add up to the board");
        eq(Lesson.BOARD_COUNT.length, Lesson.Quadrant.values().length,
                "there is a count for every corner");
        for (int c : Lesson.BOARD_COUNT) ok(c >= 1, "every corner can be taught: no count is zero");

        // The near-threshold states are the ones a reader gets wrong. They are
        // in the pools on purpose, so check they are actually near.
        boolean near = false;
        for (Lesson.Quadrant q : Lesson.Quadrant.values()) {
            for (Lesson.Reading x : Lesson.BOARD_POOL.get(q)) {
                if (Math.abs(x.pressure() - Lesson.PRESSURE_UP) <= 3
                        || Math.abs(x.heat() - Lesson.HEAT_UP) <= 3) near = true;
            }
        }
        ok(near, "the board has states that sit on the threshold");

        System.out.println("\n--- the deal ---");
        int seeds = 2000;
        int badBoard = -1, badShift = -1, overlap = -1, dupBoard = -1;
        for (long seed = 0; seed < seeds; seed++) {
            Lesson l = Lesson.of(seed);
            if (l.board.size() != Lesson.BOARD) badBoard = (int) seed;
            if (new HashSet<>(l.board).size() != Lesson.BOARD) dupBoard = (int) seed;
            int[] byCorner = new int[4];
            for (Lesson.Reading x : l.board) byCorner[x.quadrant().ordinal()]++;
            for (int i = 0; i < 4; i++) {
                if (byCorner[i] != Lesson.BOARD_COUNT[i]) badBoard = (int) seed;
            }
            if (l.shift.size() != Lesson.SHIFT) badShift = (int) seed;
            Set<Lesson.Quadrant> qs = new HashSet<>();
            for (Lesson.Reading x : l.shift) qs.add(x.quadrant());
            if (qs.size() != Lesson.SHIFT) badShift = (int) seed;
            for (Lesson.Reading x : l.shift) if (l.board.contains(x)) overlap = (int) seed;
        }
        eq(badBoard, -1, "every seed deals a full board with the counts it promises");
        eq(dupBoard, -1, "and no state appears on the board twice");
        eq(badShift, -1, "every seed deals a shift of three states in three different corners");
        eq(overlap, -1, "and no state of the shift is on the board");
        // The board is shuffled, not laid out corner by corner. If it were, the
        // player would be handed the game's whole difficulty for free.
        int sorted = 0;
        for (long seed = 0; seed < 200; seed++) {
            Lesson l = Lesson.of(seed);
            boolean inOrder = true;
            for (int i = 1; i < l.board.size(); i++) {
                if (l.board.get(i).quadrant().ordinal() < l.board.get(i - 1).quadrant().ordinal()) {
                    inOrder = false; break;
                }
            }
            if (inOrder) sorted++;
        }
        ok(sorted < 20, "the board is shuffled rather than laid out corner by corner ("
                + sorted + " of 200 came out sorted)");

        System.out.println("\n--- what a night teaches ---");
        // The claim the whole game rests on. Show one state from each of the
        // shift's corners and the student must end up with exactly two rules
        // standing, disagreeing about the one corner you did not show -- and
        // the shift must come out right.
        int tooWide = -1, wrongCorner = -1, unsafe = -1, shownBad = -1, certain = 0;
        for (long seed = 0; seed < seeds; seed++) {
            Lesson l = Lesson.of(seed);
            Set<Lesson.Quadrant> wanted = new HashSet<>();
            for (Lesson.Reading x : l.shift) wanted.add(x.quadrant());
            for (Lesson.Quadrant q : wanted) {
                for (int i = 0; i < l.board.size(); i++) {
                    if (l.board.get(i).quadrant() == q && l.canShow(i)) { l.show(i); break; }
                }
            }
            if (l.shown.size() != Lesson.SHOWS) shownBad = (int) seed;
            // Three nights in three corners can leave one rule standing or two,
            // never more -- and never two that disagree about a corner you did
            // show. Which of the two it is depends on which corner you missed:
            // the pairs that agree on three corners and differ on the fourth
            // are exactly the ones that miss the heat-up or the heat-down
            // corner, so a player who never shows the pressure-down-and-cool
            // corner always teaches the whole rule and never knows it.
            if (l.standing().size() > 2) tooWide = (int) seed;
            if (l.certain()) certain++;
            List<Lesson.Quadrant> unsure = l.unsure();
            if (unsure.size() > 1) wrongCorner = (int) seed;
            else if (unsure.size() == 1) {
                Lesson.Quadrant missed = null;
                for (Lesson.Quadrant q : Lesson.Quadrant.values()) if (!wanted.contains(q)) missed = q;
                if (unsure.get(0) != missed) wrongCorner = (int) seed;
            }
            if (!l.safe()) unsafe = (int) seed;
        }
        eq(shownBad, -1, "one state per shift corner is three nights");
        eq(tooWide, -1, "and it never leaves more than two rules standing");
        eq(wrongCorner, -1, "and the corner they disagree about is the one you did not show");
        eq(unsafe, -1, "so the shift always comes out right");
        System.out.println("       covering the shift's three corners: certain "
                + round3((double) certain / seeds) + " of the time");
        ok(certain > 0, "three nights can teach the whole rule");
        ok(certain < seeds, "and usually cannot -- there is a corner left over");

        // The other half: the game is not free. A player who shows the first
        // three states on the board is not choosing at all.
        int naiveWins = 0, naiveCertain = 0;
        for (long seed = 0; seed < seeds; seed++) {
            Lesson l = Lesson.of(seed);
            for (int i = 0; i < Lesson.SHOWS; i++) l.show(i);
            if (l.safe()) naiveWins++;
            if (l.certain()) naiveCertain++;
        }
        double rate = (double) naiveWins / seeds;
        System.out.println("       showing the first three states: safe " + round3(rate)
                + ", certain " + round3((double) naiveCertain / seeds));
        ok(rate < 0.75, "showing the first three states is not good enough");
        ok(rate > 0.0, "and it is not always wrong either -- the board is not a trick");

        // A night spent in a corner they have already seen teaches nothing, and
        // the game has to know that, because the whole failure mode is that it
        // does not feel like a failure.
        int redundantMissed = -1, changedOnRedundant = -1;
        for (long seed = 0; seed < 500; seed++) {
            Lesson l = Lesson.of(seed);
            int first = -1, second = -1;
            for (int i = 0; i < l.board.size(); i++) {
                for (int j = i + 1; j < l.board.size(); j++) {
                    if (l.board.get(i).quadrant() == l.board.get(j).quadrant()) { first = i; second = j; }
                }
            }
            if (first < 0) continue;
            l.show(first);
            Set<Lesson.Rule> before = l.standing();
            l.show(second);
            if (!l.lastRedundant) redundantMissed = (int) seed;
            if (!l.standing().equals(before)) changedOnRedundant = (int) seed;
        }
        eq(redundantMissed, -1, "a second night in the same corner is flagged as redundant");
        eq(changedOnRedundant, -1, "and it does not move the student at all");

        System.out.println("\n--- the student ---");
        // The belief is a fact about the arithmetic. A build that let the
        // student sound certain with two rules standing would be lying to the
        // player about the one number the game turns on.
        for (int mask = 0; mask < 256; mask++) {
            List<Lesson.Rule> s = Lesson.maskToSet(mask);
            String said = Lesson.belief(s);
            if (s.size() == 1) {
                ok(said.contains(Lesson.whenOf(s.get(0))), "one rule standing names that rule");
                ok(said.contains("all of it"), "and says so plainly");
            } else if (!s.isEmpty()) {
                ok(said.contains("cannot tell which"), "more than one rule standing admits it (mask " + mask + ")");
                ok(!said.contains("all of it"), "and does not claim otherwise (mask " + mask + ")");
            }
        }
        eq(Lesson.confidence(1), "one rule fits", "one rule reads as one rule");
        eq(Lesson.confidence(2), "two rules fit", "two rules reads as two");
        eq(Lesson.confidence(4), "four rules fit", "four rules reads as four");
        // The verdict has to name a corner when it has one to name, and must
        // not fall over when it does not.
        for (Lesson.Quadrant q : Lesson.Quadrant.values()) {
            ok(Lesson.verdict(false, false, List.of(q)).contains(Lesson.cornerName(q)),
                    "an unsafe handover names " + q);
            ok(Lesson.verdict(true, false, List.of(q)).contains(Lesson.cornerName(q)),
                    "a safe but uncertain handover names " + q);
            ok(Lesson.cornerName(q).startsWith("when "), "a corner is named as a condition");
        }
        ok(Lesson.verdict(true, true, List.of()).contains("Every corner"),
                "a certain handover says so");
        ok(!Lesson.closing(true, true, false).equals(Lesson.closing(true, false, false)),
                "a certain handover closes differently from an uncertain one");
        ok(!Lesson.closing(true, false, false).equals(Lesson.closing(false, false, false)),
                "and a safe one closes differently from an unsafe one");
        ok(!Lesson.closing(true, true, true).equals(Lesson.closing(true, true, false)),
                "a wasted night is not the same as a spent one");

        System.out.println("\n--- the file ---");
        Path tmp = Files.createTempFile("lesson", ".state");
        Lesson saved = Lesson.of(4242);
        saved.show(3); saved.show(7);
        saved.save(tmp);
        Lesson back = Lesson.load(tmp);
        eq(back.seed, saved.seed, "the seed survives the round trip");
        eq(back.shown, saved.shown, "the nights spent survive");
        eq(back.rule, saved.rule, "and the machine is the same machine");
        eq(back.board, saved.board, "and the board is the same board");
        eq(back.shift, saved.shift, "and the shift is the same shift");
        Files.deleteIfExists(tmp);

        System.out.println("\n--- the phone build ---");
        Path out = Path.of("web", "lesson.html");
        if (!Files.exists(out)) {
            System.out.println("       (no " + out + " from here -- run from the repository root)");
        } else {
            ok(WebLesson.html().equals(Files.readString(out)),
                    "web/lesson.html is current -- regenerate it with aside.games.lesson.WebLesson");

        // The sound. These games use it for feedback rather than for a
        // mechanic -- the desktop plays choice_move and choice_select -- but a
        // tap that makes no sound on a page that is otherwise a still canvas
        // reads as a tap that did not land.
        ok(WebLesson.html().contains("function voice("),
                "the phone build carries the shared synthesiser");
        ok(WebLesson.html().contains("pointerdown"),
                "the phone build answers a tap with a click");
        }

        System.out.println("\n=== " + (checks - failed) + " passed, " + failed + " failed ===");
        if (failed > 0) System.exit(1);
    }

    static String round3(double d) { return String.valueOf(Math.round(d * 1000) / 1000.0); }

    private SelfTest() { }
}
