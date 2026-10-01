package aside.games.tell;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Headless checks for tell.
 *
 * The point of these is not coverage. It is that three things in this game are
 * written by the same hand and could quietly disagree with each other:
 *
 *   - the expectation rule, which the prose describes out loud to the player;
 *   - the read arithmetic, which is the whole difficulty curve;
 *   - the claim the game makes about itself, which is that spreading your
 *     moves across all four directions is worth something.
 *
 * The last one is the one that matters. Every other check here is about
 * internal consistency, and a game can be perfectly consistent and still be
 * lying about the only thing it says. So the suite plays the five nights with
 * policies and asserts the order: a player who leans on the two directions
 * that lead to the door does worse than one who spends all four, and a player
 * who walks into the tell on purpose does worst of the movers. If that order
 * is not there, the house is not reading anybody and the game is a walk to a
 * corner.
 *
 * Run: java -cp classes aside.games.tell.SelfTest
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
        System.out.println("=== tell self-test ===\n");

        System.out.println("--- the house ---");
        for (int night = 0; night < Tell.NIGHTS; night++) {
            Tell t = Tell.of(12345L);
            for (int k = 0; k < night; k++) t.nextNight();
            eq(t.size, Tell.SIZE[night], "night " + (night + 1) + " has the size the table says");
            eq(t.dist(t.px, t.py), 2 * (t.size - 1), "the door is the opposite corner from the start");
            ok(t.px != t.ex || t.py != t.ey, "night " + (night + 1) + ": you do not start in the door");
            int lamps = 0;
            for (boolean b : t.lamp) if (b) lamps++;
            eq(lamps, Tell.LAMPS[night], "night " + (night + 1) + " lights the number of rooms the table says");
            ok(!t.lamp[t.idx(t.sx, t.sy)], "night " + (night + 1) + ": the room you start in is not lit");
            ok(!t.lamp[t.idx(t.ex, t.ey)], "night " + (night + 1) + ": the door is not lit");
        }

        System.out.println("\n--- the ramp ---");
        for (int n = 1; n < Tell.NIGHTS; n++) {
            ok(Tell.REACH[n] >= Tell.REACH[n - 1], "the house covers at least as many directions on night " + (n + 1));
            ok(Tell.BASE[n] - Tell.MISS[n] >= Tell.BASE[n - 1] - Tell.MISS[n - 1],
                    "the clock a perfect player is on does not get kinder on night " + (n + 1));
            ok(Tell.SIZE[n] >= Tell.SIZE[n - 1], "the house does not get smaller on night " + (n + 1));
            ok(Tell.HIT[n] >= Tell.HIT[n - 1], "being read does not get cheaper on night " + (n + 1));
        }
        // Every night has to end. The read alone will not do it -- a player
        // who is never read is on a clock of BASE - MISS a turn, and that is
        // deliberately negative, because the read floors at zero and the thing
        // that actually ends a night is the turn limit. So the limit has to be
        // a real number and it has to be more than the walk to the door.
        for (int n = 0; n < Tell.NIGHTS; n++) {
            ok(Tell.LIMIT[n] > 2 * (Tell.SIZE[n] - 1),
                    "night " + (n + 1) + ": the turn limit leaves room for the walk and a detour");
            ok(Tell.BASE[n] >= 1, "night " + (n + 1) + ": a turn costs something");
        }

        System.out.println("\n--- the expectation rule ---");
        Tell t = Tell.of(999L);
        // Before you have moved, it does not guess a habit -- it guesses the
        // door, and it guesses exactly one direction, whatever the reach is.
        eq(t.expected().size(), 1, "night 1 in a fresh room expects exactly one direction");
        int best = 9999;
        for (int d : t.legal()) best = Math.min(best, t.stepDist(d));
        eq(t.stepDist(t.expected().get(0)), best, "and it expects the way to the door");
        // A fresh room gets exactly one guess on every night, whatever the
        // reach is -- and the guess is the best way to the door. That single
        // guess is what leaves the other way forward free, which is the move
        // the whole game is played with.
        for (int night = 0; night < Tell.NIGHTS; night++) {
            Tell t2 = Tell.of(999L);
            for (int k = 0; k < night; k++) t2.nextNight();
            eq(t2.expected().size(), 1, "night " + (night + 1) + ": a fresh room gets one guess");
            int b = 9999;
            for (int d : t2.legal()) b = Math.min(b, t2.stepDist(d));
            eq(t2.stepDist(t2.expected().get(0)), b,
                    "night " + (night + 1) + ": and the guess is the best way to the door");
        }
        // Every direction is either expected or a surprise, and never both.
        for (int d = 0; d < Tell.DIRS; d++) {
            boolean e = t.expected().contains(d);
            boolean s = t.surprises().contains(d);
            ok(e != s, "direction " + d + " is either expected or a surprise, not both");
        }
        // It expects what you have used most, anywhere in the house.
        Tell u = Tell.of(4242L);
        u.px = 2; u.py = 2;
        u.move(3);   // west, out of 2,2
        u.move(1);   // back east, out of 1,2
        u.move(3);   // west again, out of 2,2
        eq(u.hist[u.idx(2, 2)][3], 2, "the house counted the two departures west");
        eq(u.hist[u.idx(1, 2)][1], 1, "and the one departure east, counted in the room it left");
        u.px = 2; u.py = 2;
        eq(u.expected().get(0), 3, "the house expects the door you use most from this room");
        // Reach is what it says: at reach 3 there is exactly one way left to
        // surprise it, and it is your least-used direction.
        Tell v = Tell.of(77L);
        for (int k = 0; k < 3; k++) v.nextNight();
        eq(v.reach(), 3, "night 4 covers three directions");
        int vi = v.here();
        v.hist[vi][0] = 3; v.hist[vi][1] = 2; v.hist[vi][2] = 1; v.hist[vi][3] = 0;
        eq(v.expected().size(), 3, "once it has a habit, reach 3 covers three directions");
        eq(v.surprises(), List.of(3), "and leaves exactly the least-used direction");
        // Reach 1 leaves three, which is the tutorial.
        Tell w = Tell.of(77L);
        eq(w.reach(), 1, "night 1 covers one direction");
        int wi = w.here();
        w.hist[wi][0] = 3; w.hist[wi][1] = 2; w.hist[wi][2] = 1; w.hist[wi][3] = 0;
        eq(w.expected(), List.of(0), "night 1 expects only the most-used direction");
        eq(w.surprises().size(), 3, "night 1 leaves three ways to surprise it");

        System.out.println("\n--- the read ---");
        Tell r = Tell.of(31337L);
        eq(r.read, 0, "the read starts at nothing");
        int base = r.base(), hit = r.hitCost(), miss = r.missGain();
        int surprise = r.legalSurprises().get(0);
        r.move(surprise);
        eq(r.read, Math.max(0, 0 - miss) + base + (r.lamp[r.here()] ? r.litCost() : 0),
                "surprising it costs it, and the turn still costs you");
        Tell r2 = Tell.of(31337L);
        r2.move(r2.expected().get(0));
        eq(r2.read, base + hit + (r2.lamp[r2.here()] ? r2.litCost() : 0),
                "going where it expects costs you the hit as well");
        ok(r2.read > r.read, "being read costs more than surprising it");
        // The floor is zero: you cannot bank credit by being unpredictable.
        Tell r3 = Tell.of(5L);
        for (int k = 0; k < 40 && !r3.done(); k++) {
            List<Integer> legal = r3.legal();
            if (legal.isEmpty()) break;
            r3.move(Trace.spread(r3, legal));
        }
        ok(r3.read >= 0, "the read never goes below zero");

        System.out.println("\n--- the claim the game makes about itself ---");
        int seeds = 200;
        int[] wins = new int[Trace.NAMES.length];
        for (int p = 0; p < Trace.NAMES.length; p++) {
            for (long seed = 0; seed < seeds; seed++) {
                Tell h = Tell.of(seed);
                for (int night = 0; night < Tell.NIGHTS; night++) {
                    Trace.play(h, p, seed * 31 + night);
                    if (h.won) wins[p]++;
                    if (night + 1 < Tell.NIGHTS) h.nextNight();
                }
            }
        }
        int total = seeds * Tell.NIGHTS;
        StringBuilder line = new StringBuilder("   ");
        for (int p = 0; p < Trace.NAMES.length; p++) {
            line.append(Trace.NAMES[p]).append(' ').append(wins[p]).append('/').append(total).append("   ");
        }
        System.out.println(line);
        eq(wins[0], 0, "a player who never moves is read every time");
        int other = Trace.NAMES.length - 1;
        ok(wins[other] > wins[1], "taking the way it is not expecting beats walking straight at the door");
        ok(wins[other] > wins[2], "taking the way it is not expecting beats doing what it expects");
        ok(wins[1] >= wins[2], "walking straight at the door is no worse than walking into the tell on purpose");
        ok(wins[other] > wins[0], "taking the way it is not expecting beats standing still");
        ok(wins[1] < seeds * Tell.NIGHTS / 2, "and walking straight at the door does not survive the week");

        System.out.println("\n--- the file ---");
        Path p = Files.createTempFile("tell", ".state");
        Tell s = Tell.of(24680L);
        for (int k = 0; k < 6 && !s.done(); k++) {
            List<Integer> legal = s.legal();
            if (legal.isEmpty()) break;
            s.move(Trace.safe(s, legal));
        }
        s.save(p);
        Tell back = Tell.load(p);
        eq(back.seed, s.seed, "the seed survives the file");
        eq(back.night, s.night, "the night survives the file");
        eq(back.px + "," + back.py, s.px + "," + s.py, "where you are survives the file");
        eq(back.read, s.read, "the read survives the file");
        eq(back.turn, s.turn, "the turn survives the file");
        eq(back.won, s.won, "the ending survives the file");
        eq(back.expected(), s.expected(), "the house's model of you survives the file");
        boolean counts = true, recency = true;
        for (int i = 0; i < s.size * s.size; i++) {
            for (int d = 0; d < Tell.DIRS; d++) {
                if (back.hist[i][d] != s.hist[i][d]) counts = false;
                if (back.last[i][d] != s.last[i][d]) recency = false;
            }
        }
        ok(counts, "the counts survive the file");
        ok(recency, "the recency survives the file");
        Files.deleteIfExists(p);

        System.out.println("\n--- the phone build ---");
        // A generated file that has gone stale is worse than no file: it is a
        // second copy of the game quietly disagreeing with the first. So this
        // regenerates it and compares, rather than spot-checking a sentence.
        Path out = Path.of("web", "tell.html");
        if (!Files.exists(out)) {
            System.out.println("       (no " + out + " from here -- run from the repository root)");
        } else {
            ok(WebTell.html().equals(Files.readString(out)),
                    "web/tell.html is current -- regenerate it with aside.games.tell.WebTell");
        }

        System.out.println("\n" + (failed == 0 ? "all " + checks + " checks passed"
                : failed + " of " + checks + " checks FAILED"));
        if (failed > 0) System.exit(1);
    }

    private SelfTest() {}
}
