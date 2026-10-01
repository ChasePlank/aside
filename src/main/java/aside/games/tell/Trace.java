package aside.games.tell;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Play the five nights with policies instead of a player, and print what the
 * house does to each of them.
 *
 * The point is not to prove the game is winnable. It is to find out whether
 * spreading your moves actually buys anything, and how much, before the
 * writing is finished. A game whose whole claim is "the predictable player
 * dies" has to be able to show the difference between a player who leans on
 * the two directions that lead to the door and a player who spends all four --
 * and if it cannot, the claim is decoration.
 *
 * The policies, and what each one is for:
 *
 *   IDLE      never moves. The floor: it should die on every night, or the
 *             read is not a clock at all.
 *   STRAIGHT  always the step that shortens the distance to the door. The
 *             obvious way to play, and the way that leans hardest on two
 *             directions.
 *   HABIT     always the direction it expects, preferring one that also
 *             shortens the distance. This is a player walking into their own
 *             tell on purpose, and it should be the worst of the movers.
 *   SPREAD    always the direction it has used least, preferring one that also
 *             shortens the distance. The game's own answer to the house, with
 *             no regard for the door.
 *   SAFE      the legal direction that surprises it, preferring one that also
 *             shortens the distance; least-used when nothing surprises it.
 *             This is the competent player: it knows the rule and it still
 *             wants out.
 *
 * Run from the repository root:
 *   java -cp classes aside.games.tell.Trace [seeds]
 */
public final class Trace {

    public static final String[] NAMES = { "IDLE", "STRAIGHT", "HABIT", "SPREAD", "SAFE", "THRIFTY", "DETOUR", "OTHER" };

    public static void main(String[] args) {
        int seeds = args.length > 0 ? Integer.parseInt(args[0]) : 300;
        int[][] won = new int[NAMES.length][Tell.NIGHTS];
        int[][] readSum = new int[NAMES.length][Tell.NIGHTS];

        for (int p = 0; p < NAMES.length; p++) {
            for (long seed = 0; seed < seeds; seed++) {
                Tell t = Tell.of(seed);
                for (int night = 0; night < Tell.NIGHTS; night++) {
                    play(t, p, seed * 31 + night);
                    if (t.won) won[p][night]++;
                    readSum[p][night] += t.readPct();
                    if (night + 1 < Tell.NIGHTS) t.nextNight();
                }
            }
        }

        System.out.println("=== tell: " + seeds + " houses, five nights each ===\n");
        System.out.printf("%-9s", "policy");
        for (int n = 0; n < Tell.NIGHTS; n++) System.out.printf("  night%d", n + 1);
        System.out.println("   week");
        for (int p = 0; p < NAMES.length; p++) {
            System.out.printf("%-9s", NAMES[p]);
            int total = 0;
            for (int n = 0; n < Tell.NIGHTS; n++) {
                System.out.printf("  %5.0f%%", 100.0 * won[p][n] / seeds);
                if (won[p][n] == seeds) total++;
            }
            System.out.printf("   %d/5%n", total);
        }

        System.out.println("\nmean read at the end (lower is better)");
        System.out.printf("%-9s", "policy");
        for (int n = 0; n < Tell.NIGHTS; n++) System.out.printf("  night%d", n + 1);
        System.out.println();
        for (int p = 0; p < NAMES.length; p++) {
            System.out.printf("%-9s", NAMES[p]);
            for (int n = 0; n < Tell.NIGHTS; n++) {
                System.out.printf("  %6.1f", (double) readSum[p][n] / seeds);
            }
            System.out.println();
        }

        System.out.println("\nthe OTHER policy, by night");
        for (int n = 0; n < Tell.NIGHTS; n++) {
            int w = 0, reads = 0;
            for (long seed = 0; seed < seeds; seed++) {
                Tell t = Tell.of(seed);
                for (int k = 0; k < n; k++) t.nextNight();
                play(t, 7, seed * 31 + n);
                if (t.won) w++;
                reads += t.readPct();
            }
            System.out.printf("  night%d: %d/%d won, mean read %.1f%n", n + 1, w, seeds,
                    (double) reads / seeds);
        }

        System.out.println("\nthe detour policy, by the read it buys back at");
        System.out.printf("%-9s", "at");
        for (int n = 0; n < Tell.NIGHTS; n++) System.out.printf("  night%d", n + 1);
        System.out.println("   week");
        int bestAt = 45, bestWeek = -1;
        for (int at = 0; at <= 90; at += 10) {
            DETOUR_AT = at;
            int[] w = new int[Tell.NIGHTS];
            for (long seed = 0; seed < seeds; seed++) {
                Tell t = Tell.of(seed);
                for (int night = 0; night < Tell.NIGHTS; night++) {
                    play(t, 6, seed * 31 + night);
                    if (t.won) w[night]++;
                    if (night + 1 < Tell.NIGHTS) t.nextNight();
                }
            }
            System.out.printf("%-9d", at);
            int week = 0;
            for (int n = 0; n < Tell.NIGHTS; n++) {
                System.out.printf("  %5.0f%%", 100.0 * w[n] / seeds);
                if (w[n] == seeds) week++;
            }
            int sum = 0;
            for (int n = 0; n < Tell.NIGHTS; n++) sum += w[n];
            System.out.printf("   %d/5 (%d)%n", week, sum);
            if (sum > bestWeek) { bestWeek = sum; bestAt = at; }
        }
        DETOUR_AT = bestAt;
        System.out.println("best threshold: " + bestAt);

        System.out.println("\nwhere the SAFE player dies");
        int[] byRead = new int[Tell.NIGHTS];
        int[] byTurns = new int[Tell.NIGHTS];
        for (long seed = 0; seed < seeds; seed++) {
            Tell t = Tell.of(seed);
            for (int night = 0; night < Tell.NIGHTS; night++) {
                play(t, 4, seed * 31 + night);
                if (!t.won) {
                    if (t.read >= Tell.READ_MAX) byRead[night]++;
                    else byTurns[night]++;
                }
                if (night + 1 < Tell.NIGHTS) t.nextNight();
            }
        }
        for (int n = 0; n < Tell.NIGHTS; n++) {
            System.out.printf("  night%d: read %d, turns %d%n", n + 1, byRead[n], byTurns[n]);
        }
    }

    public static void play(Tell t, int policy, long rngSeed) {
        Random rng = new Random(rngSeed);
        int[] st = new int[2];
        int guard = 0;
        while (!t.done() && guard++ < 500) {
            List<Integer> legal = t.legal();
            if (legal.isEmpty()) return;
            int dir = switch (policy) {
                case 0 -> -1;
                case 1 -> straight(t, legal);
                case 2 -> habit(t, legal);
                case 3 -> spread(t, legal);
                case 4 -> safe(t, legal);
                case 5 -> thrifty(t, legal);
                case 6 -> detour(t, legal, st);
                default -> other(t, legal);
            };
            if (dir < 0) return;
            t.move(dir);
        }
    }

    /** The threshold the detour policy buys the read back at. */
    public static int DETOUR_AT = 45;

    /**
     * The competent player: push for the door, and when the read has got
     * expensive, spend two steps going the wrong way and back to dirty the
     * room -- which is the only thing that makes the next step through it
     * free. This is the strategy the game is about, and the thing a player has
     * to discover: a detour is not a mistake, it is the price of the next
     * room being cheap.
     */
    public static int detour(Tell t, List<Integer> legal, int[] st) {
        if (st[0] == 1) {
            // Coming back. Reverse of the step we just took.
            int back = (st[1] + 2) % Tell.DIRS;
            st[0] = 0;
            if (legal.contains(back)) return back;
        }
        List<Integer> prog = progress(t, legal);
        List<Integer> surprise = t.legalSurprises();
        for (int d : prog) if (surprise.contains(d)) return d;
        if (t.readPct() >= DETOUR_AT) {
            List<Integer> away = new ArrayList<>();
            for (int d : surprise) if (!prog.contains(d)) away.add(d);
            if (!away.isEmpty()) {
                int pick = straight(t, away);
                st[0] = 1;
                st[1] = pick;
                return pick;
            }
        }
        return prog.size() == 1 ? prog.get(0) : spread(t, prog);
    }

    /** The step that shortens the distance to the door; least-used breaks the tie. */
    public static int straight(Tell t, List<Integer> legal) {
        int best = Integer.MAX_VALUE;
        List<Integer> pick = new ArrayList<>();
        for (int d : legal) {
            int dist = t.stepDist(d);
            if (dist < best) { best = dist; pick.clear(); pick.add(d); }
            else if (dist == best) pick.add(d);
        }
        return pick.size() == 1 ? pick.get(0) : spread(t, pick);
    }

    /** The legal direction it has used least, from this room. */
    public static int spread(Tell t, List<Integer> legal) {
        int best = Integer.MAX_VALUE, out = legal.get(0);
        int i = t.here();
        for (int d : legal) {
            if (t.hist[i][d] < best) { best = t.hist[i][d]; out = d; }
        }
        return out;
    }

    /** Walk into the tell: do what it expects, preferring the way out. */
    public static int habit(Tell t, List<Integer> legal) {
        List<Integer> exp = new ArrayList<>();
        for (int d : t.expected()) if (legal.contains(d)) exp.add(d);
        if (exp.isEmpty()) return straight(t, legal);
        return straight(t, exp);
    }

    /** Surprise it if you can, and prefer the surprise that goes somewhere. */
    public static int safe(Tell t, List<Integer> legal) {
        List<Integer> surprise = t.legalSurprises();
        if (surprise.isEmpty()) return spread(t, legal);
        return straight(t, surprise);
    }

    /** The legal moves that shorten the distance to the door. */
    static List<Integer> progress(Tell t, List<Integer> legal) {
        int best = Integer.MAX_VALUE;
        List<Integer> pick = new ArrayList<>();
        for (int d : legal) {
            int dd = t.stepDist(d);
            if (dd < best) { best = dd; pick.clear(); pick.add(d); }
            else if (dd == best) pick.add(d);
        }
        return pick;
    }

    /**
     * Push for the door while the read is low, and spend a step buying it back
     * when it is not. This is the strategy the game is actually about: a
     * surprise is worth having, but only when the read has got expensive
     * enough to pay for it, because every step spent going the wrong way is a
     * step the house is still counting.
     */
    public static int thrifty(Tell t, List<Integer> legal) {
        List<Integer> surprise = t.legalSurprises();
        List<Integer> prog = progress(t, legal);
        for (int d : prog) if (surprise.contains(d)) return d;   // free money
        if (t.readPct() >= 45 && !surprise.isEmpty()) return straight(t, surprise);
        return prog.size() == 1 ? prog.get(0) : spread(t, prog);
    }

    /**
     * The game's own answer, played straight: take the way to the door the
     * house is not expecting. When both ways forward are covered -- which
     * happens on the last row or column -- take the hit and go.
     */
    public static int other(Tell t, List<Integer> legal) {
        List<Integer> prog = progress(t, legal);
        List<Integer> surprise = t.legalSurprises();
        for (int d : prog) if (surprise.contains(d)) return d;
        return prog.size() == 1 ? prog.get(0) : spread(t, prog);
    }

    private Trace() {}
}
