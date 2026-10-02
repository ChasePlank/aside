package aside.games.fnaf.engine;

import java.util.HashMap;
import java.util.Map;

/**
 * FnafForensics - replay many games and record who appears where, and who kills whom.
 *
 * Written to settle a playtest report that could not be reproduced by reading code:
 * "chica showed up on the left and before i could even say 'oh chicas there' she
 * jumpscared me". Chica's doorSide is +1 (right), set once at construction, so the code
 * says that cannot happen - and five other mechanical explanations were checked and
 * ruled out (see the commit that added ShotSprites). So: stop reading, start counting.
 *
 * For every arrival it records (name, side). For every death it records the killer and
 * whether the power was already out, which is the difference between "she was at your
 * door" and "the blackout took you, and the blackout is Chica".
 */
public class FnafForensics {
    public static void main(String[] args) {
        double dt = 1.0 / 60.0;
        // per-night, per-animatronic: the difficulty curve should RISE with the night.
        // A flat or falling row is a mistyped constant, which is exactly how Chica's
        // 3.02 was found - so measure the curve rather than reading the table.
        int[][] arrivalsByNight = new int[6][4];
        double[][] firstArrival = new double[6][4];
        for (double[] row : firstArrival) java.util.Arrays.fill(row, -1);
        int freddySprints = 0;
        Map<String, Integer> arrivals = new HashMap<>();
        Map<String, Integer> kills = new HashMap<>();
        int blackoutKills = 0, normalKills = 0, wins = 0, games = 0;
        double graceSum = 0;
        int graceCount = 0;
        double graceMin = 1e9, graceMax = -1e9;
        String chicaOnLeft = null;

        for (int night = 1; night <= 5; night++) {
            for (long seed = 1; seed <= 40; seed++) {
                Game g = new Game(night, seed);
                Bot bot = new Bot(g, seed);
                games++;
                Animatronic[] cast = {g.monty, g.roxanne, g.chica, g.freddy};
                boolean[] logged = new boolean[cast.length];
                double[] arrivalTime = new double[cast.length];
                boolean wasPowerOut = false;
                boolean killed = false;

                for (int i = 0; i < (int) (300 * 60); i++) {
                    bot.play(dt);
                    g.update(dt);
                    if (g.status == Game.Status.POWER_OUT) wasPowerOut = true;

                    for (int c = 0; c < cast.length; c++) {
                        Animatronic a = cast[c];
                        boolean here = a.atOffice();
                        if (here && !logged[c]) {
                            logged[c] = true;
                            arrivalTime[c] = i * dt;
                            arrivals.merge(a.name + " side " + a.doorSide, 1, Integer::sum);
                            int ci = c;
                            arrivalsByNight[night - 1][ci]++;
                            if (firstArrival[night - 1][ci] < 0) firstArrival[night - 1][ci] = i * dt;
                            if ("Chica".equals(a.name) && a.doorSide < 0) {
                                chicaOnLeft = String.format("night %d seed %d at t=%.1fs", night, seed, i * dt);
                            }
                        } else if (!here) {
                            logged[c] = false;
                        }
                    }

                    // Freddy never calls atOffice() - he arrives through the Kid's
                    // Cove stages and a sprint - so the loop above is blind to him.
                    if (g.freddy.stages >= 3) freddySprints++;

                    if (g.status == Game.Status.JUMPSCARED && !killed) {
                        killed = true;
                        String who = g.jumpscareBy != null ? g.jumpscareBy.name : "?";
                        kills.merge(who, 1, Integer::sum);
                        if (wasPowerOut) blackoutKills++; else normalKills++;
                        if (g.jumpscareBy != null) {
                            int idx = cast[0] == g.jumpscareBy ? 0 : cast[1] == g.jumpscareBy ? 1
                                    : cast[2] == g.jumpscareBy ? 2 : 3;
                            if (logged[idx]) {   // still standing there: this was the door kill
                                double grace = i * dt - arrivalTime[idx];
                                graceSum += grace; graceCount++;
                                graceMin = Math.min(graceMin, grace);
                                graceMax = Math.max(graceMax, grace);
                            }
                        }
                        break;
                    }
                    if (g.status != Game.Status.PLAYING) break;
                }
                if (g.status == Game.Status.SURVIVED) wins++;
            }
        }

        System.out.println("games " + games + "  wins " + wins + "  kills " + (blackoutKills + normalKills));
        System.out.println("kills in the blackout: " + blackoutKills + "   at a door: " + normalKills);
        System.out.println("kills by: " + kills);
        System.out.println("arrivals by name and side: " + arrivals);
        System.out.println("CHICA AT THE LEFT DOOR: " + (chicaOnLeft == null ? "never" : chicaOnLeft));
        // ---- idle player sweep ------------------------------------------------
        // No bot: the doors stay open, which is the only way to reach the grace-window
        // kill at all (the bot always closes in time). It also answers whether Freddy's
        // flat line above is the game or the harness - the bot watches cam 3, and
        // watching his room is exactly what freezes him.
        System.out.println("\n=== idle player (no bot): doors open the whole time ===");
        for (int night = 1; night <= 5; night++) {
            int idleWins = 0, idleKills = 0, blackout = 0;
            double graceSum2 = 0;
            int graceN = 0;
            double firstSprintSum = 0;
            int sprintReached = 0;
            for (long seed = 1; seed <= 12; seed++) {
                Game g = new Game(night, seed);
                boolean[] logged = new boolean[3];
                double[] arr = new double[3];
                Animatronic[] three = {g.monty, g.roxanne, g.chica};
                boolean wasOut = false;
                boolean killed = false;
                for (int i = 0; i < (int) (300 * 60); i++) {
                    g.update(dt);
                    if (g.status == Game.Status.POWER_OUT) wasOut = true;
                    for (int c = 0; c < 3; c++) {
                        boolean here = three[c].atOffice();
                        if (here && !logged[c]) { logged[c] = true; arr[c] = i * dt; }
                        else if (!here) logged[c] = false;
                    }
                    if (g.freddy.stages >= 3 && firstSprintSum == 0 || g.freddy.stages >= 3) {
                        // count him reaching the sprint once per game
                    }
                    if (g.status == Game.Status.JUMPSCARED && !killed) {
                        killed = true; idleKills++;
                        if (wasOut) blackout++;
                        for (int c = 0; c < 3; c++) {
                            if (g.jumpscareBy == three[c] && logged[c]) {
                                graceSum2 += i * dt - arr[c]; graceN++;
                            }
                        }
                        break;
                    }
                    if (g.status != Game.Status.PLAYING) break;
                }
                if (g.status == Game.Status.SURVIVED) idleWins++;
                if (g.freddy.stages >= 3) sprintReached++;
            }
            System.out.printf("  night %d: wins %2d/12  deaths %2d (blackout %d)  grace %s  Freddy reached sprint %d/12%n",
                    night, idleWins, idleKills, blackout,
                    graceN == 0 ? "n/a" : String.format("%.1fs over %d", graceSum2 / graceN, graceN),
                    sprintReached);
        }

        System.out.println("\narrivals per night (Monty, Roxanne, Chica, Freddy-sprint):");
        String[] who = {"Monty", "Roxanne", "Chica", "Freddy"};
        for (int n = 0; n < 5; n++) {
            System.out.printf("  night %d: %5d %5d %5d   (%d sprint frames in 40 games)%n",
                    n + 1, arrivalsByNight[n][0], arrivalsByNight[n][1], arrivalsByNight[n][2],
                    n == 0 ? freddySprints : 0);
        }
        System.out.println("\nmean time to first arrival, per night (seconds):");
        for (int n = 0; n < 5; n++) {
            System.out.printf("  night %d:", n + 1);
            for (int c = 0; c < 3; c++) {
                System.out.printf("  %s %s", who[c],
                        firstArrival[n][c] < 0 ? "never" : String.format("%.0f", firstArrival[n][c]));
            }
            System.out.println();
        }

        if (graceCount > 0) {
            System.out.printf("door-kill grace: mean %.1fs, min %.1fs, max %.1fs over %d kills%n",
                    graceSum / graceCount, graceMin, graceMax, graceCount);
        } else {
            System.out.println("door-kill grace: no door kills in this sample");
        }
    }
}
