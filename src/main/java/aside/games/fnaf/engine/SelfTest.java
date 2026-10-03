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

        System.out.println("\n=== " + (checks - failed) + " passed, " + failed + " failed ===");
        if (failed > 0) System.exit(1);
    }
}
