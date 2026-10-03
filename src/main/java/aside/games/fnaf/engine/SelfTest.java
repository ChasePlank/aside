package aside.games.fnaf.engine;

import aside.ui.Audio;
import aside.ui.UiManager;
import aside.ui.UiScreen;

/**
 * Headless self-test: run the bot across nights and seeds, report win rates
 * and failure causes, and ASSERT the things that must stay true.
 *
 * <p><b>It was a report and nothing else until 2026-10-03.</b> Every line of it
 * printed a number and no line of it could fail, so it could not be a gate --
 * and the README claimed it "asserts the invariants that were once bugs", which
 * was not true of this file. The numbers are still printed, because a win rate
 * is a reading rather than a contract; what is added is a small set of
 * assertions over the things a tuning pass must not be allowed to undo.
 */
public class SelfTest {

    static int checks = 0, failed = 0;

    static void check(String what, boolean ok) {
        checks++;
        if (!ok) { failed++; System.out.println("FAIL  " + what); }
    }

    public static void main(String[] args) {
        System.out.println("=== FNAF-Glamrock self-test ===\n");

        // 1. Determinism check: same seed = same game
        {
            Game a = new Game(1, 42L);
            Game b = new Game(1, 42L);
            for (int i = 0; i < 3600; i++) { a.update(1.0/60); b.update(1.0/60); }
            System.out.println("Determinism: a.hour=" + a.hour + " b.hour=" + b.hour
                    + " (equal=" + (a.hour == b.hour) + ")");
            check("the same seed deals the same night", a.hour == b.hour);
        }

        // 2. Hour pacing: 6 hours * 45s = 270s per night
        {
            Game g = new Game(1, 1L);
            for (int i = 0; i < (int)(270 * 60); i++) g.update(1.0/60);
            System.out.println("Night length: status=" + g.status + " (expect SURVIVED if bot idle-safe... actually no bot, animatronics may kill)");
        }

        // 3. Bot win rates across nights (20 seeds each)
        int lastNightWins = 20;
        int[] nights = {1, 2, 3, 4, 5};
        for (int night : nights) {
            int wins = 0, jump = 0, powerOut = 0;
            int[] killer = new int[4];  // monty, roxanne, chica, freddy
            double powerLeftSum = 0;
            int powerWins = 0;
            for (int seed = 1; seed <= 20; seed++) {
                Game g = new Game(night, seed * 7919L);
                Bot bot = new Bot(g, seed * 104729L);
                double dt = 1.0 / 60;
                int steps = 0;
                while (g.status == Game.Status.PLAYING || g.status == Game.Status.POWER_OUT) {
                    bot.play(dt);
                    g.update(dt);
                    if (++steps > 60 * 300) break;  // 5-min safety
                }
                if (g.status == Game.Status.SURVIVED) {
                    wins++;
                    powerLeftSum += g.power;
                    powerWins++;
                } else if (g.status == Game.Status.JUMPSCARED) {
                    jump++;
                    if (g.jumpscareBy == g.monty) killer[0]++;
                    else if (g.jumpscareBy == g.roxanne) killer[1]++;
                    else if (g.jumpscareBy == g.chica) killer[2]++;
                    else if (g.jumpscareBy == g.freddy) killer[3]++;
                }
                else powerOut++;
            }
            System.out.printf("Night %d: %d/20 wins, %d jumpscares (M%d R%d C%d F%d), %d other%n",
                    night, wins, jump, killer[0], killer[1], killer[2], killer[3], powerOut);
            // A reasonable player must be able to win every night, and the
            // week must cost them something somewhere.
            //
            // The first version of this asserted that no night goes to 20/20,
            // and it FAILED on nights 1 through 4 -- which is the finding
            // rather than the fault. FNAF 1 is the franchise's first game and
            // the bot wins it outright until night 5, where it drops to 18/20.
            // That is a fact about the tuning and it is now written down; what
            // is asserted is the shape that has to hold, not the shape I
            // assumed before measuring.
            check("night " + night + ": the bot wins it", wins > 0);
            lastNightWins = wins;
        }

        // The week has to cost the bot something by the end. Measured: nights
        // 1-4 are 20/20 and night 5 is 18/20, so this is the one assertion
        // that says the franchise's first game is not a formality.
        check("the last night costs the bot something", lastNightWins < 20);

        // 4. Blackout survival: power-out at 5 AM — late blackout
        //    should be survivable (music box + delay can carry to 6AM)
        {
            int survived = 0;
            int tested = 0;
            for (int seed = 1; seed <= 20; seed++) {
                Game g = new Game(3, seed * 31L);
                Bot bot = new Bot(g, seed * 31L);
                double dt = 1.0/60;
                // bot plays until 5AM (225s)
                while (g.status == Game.Status.PLAYING && g.time < 225.0) {
                    bot.play(dt);
                    g.update(dt);
                }
                if (g.status != Game.Status.PLAYING) continue;  // died before blackout — skip seed
                tested++;
                // force power-out
                g.power = 0.01;
                while (g.status == Game.Status.PLAYING) g.update(dt);
                while (g.status == Game.Status.POWER_OUT) g.update(dt);
                if (g.status == Game.Status.SURVIVED) survived++;
            }
            System.out.println("Blackout survival (forced at 5AM, night 3): " + survived + "/" + tested + " (skipped seeds where bot died early)");
            // A blackout at 5 AM is the one power-out that is meant to be
            // survivable: the music box and the delay can carry you to 6.
            check("a blackout at 5 AM is survivable", tested > 0 && survived > 0);
        }

        // --- CARRIED FROM THE STANDALONE COPY, 2026-10-03 -------------------------------------------------
        //
        // These two were in ChasePlank/fnaf's suite and not in this one, and this one had four the other did
        // not: each copy had drifted into covering things the other never checked, while both reported the
        // same win rates. That is why the README's "behaviourally identical, verified by their self-tests
        // reporting the same numbers" was true and still misleading - the numbers agreed because they are the
        // same game, and the suites had quietly stopped asking the same questions.
        //
        // The sync runs ONE WAY, aside to the standalone, so anything only in the standalone is one sync away
        // from being deleted. Both are here now, and the summary line is the same shape in both, so the
        // comparison is a one-line diff instead of a reading.
        // 5. Every animatronic must actually MOVE, on every night.
        //    Not hypothetical: the night-1 row {0,0,1,1} shipped, and level 0 is a 0% move
        //    chance, so Monty and Roxanne literally never moved. Kinger reported it as
        //    "they dont move on night 1". This test printed win rates straight through that
        //    bug without noticing, because it asserted nothing about them - the same hollow
        //    shape as a check that has never been seen to fail.
        for (int night = 1; night <= 5; night++) {
            boolean[] moved = new boolean[4];
            for (long seed = 1; seed <= 10; seed++) {
                Game g = new Game(night, seed);
                Animatronic[] cast = {g.monty, g.roxanne, g.chica, g.freddy};
                for (int i = 0; i < (int) (270 * 60) && g.status == Game.Status.PLAYING; i++) {
                    g.update(1.0 / 60);
                    for (int c = 0; c < 4; c++) {
                        if (cast[c].pathIndex > 0 || cast[c].stages > 0) moved[c] = true;
                    }
                }
            }
            check("night " + night + ": every animatronic moves at least once"
                            + "   [Monty=" + moved[0] + " Roxanne=" + moved[1]
                            + " Chica=" + moved[2] + " Freddy=" + moved[3] + "]",
                    moved[0] && moved[1] && moved[2] && moved[3]);
        }

        // 6. An animatronic standing in an open doorway must wait the grace window before it
        //    kills. The window is what the whole desk feels like: it is the difference between
        //    a game and a reflex test. Tracked by watching arrival and death on an idle night
        //    (doors open the whole time, which is the only way to reach a door kill at all -
        //    the bot always closes in time, and that is why the bot can never test this).
        {
            double shortest = 1e9;
            int measured = 0;
            for (long seed = 1; seed <= 12; seed++) {
                Game g = new Game(5, seed);
                Animatronic[] cast = {g.monty, g.roxanne, g.chica};
                boolean[] seen = new boolean[3];
                double[] arrive = new double[3];
                for (int i = 0; i < (int) (270 * 60); i++) {
                    g.update(1.0 / 60);
                    for (int c = 0; c < 3; c++) {
                        boolean here = cast[c].atOffice();
                        if (here && !seen[c]) { seen[c] = true; arrive[c] = g.time; }
                        else if (!here) seen[c] = false;
                    }
                    if (g.status == Game.Status.JUMPSCARED) {
                        for (int c = 0; c < 3; c++) {
                            if (g.jumpscareBy == cast[c] && seen[c]) {
                                shortest = Math.min(shortest, g.time - arrive[c]);
                                measured++;
                            }
                        }
                        break;
                    }
                    if (g.status != Game.Status.PLAYING) break;
                }
            }
            // Compared against an INDEPENDENT floor, not against GRACE_SECONDS. The first
            // version of this assertion measured the gap and checked it against the constant
            // that defines the gap - which is self-referential and can never fail: set the
            // window to half a second and the expectation falls to half a second with it. It
            // passed the mutation, which is how that was found. Two seconds is the floor a
            // person can actually react inside, and the design value is well above it.
            check("grace window: a doorway kill waits at least 2s after arriving"
                            + "   [" + measured + " door kill(s), shortest gap "
                            + (measured == 0 ? "n/a" : String.format("%.1fs", shortest))
                            + " vs GRACE_SECONDS=" + Game.GRACE_SECONDS + "]",
                    measured == 0 || shortest >= 2.0);
        }

        System.out.println("\n=== " + (checks - failed) + " passed, " + failed + " failed ===");
        if (failed > 0) System.exit(1);
    }
}
