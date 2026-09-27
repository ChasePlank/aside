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
                            if ("Chica".equals(a.name) && a.doorSide < 0) {
                                chicaOnLeft = String.format("night %d seed %d at t=%.1fs", night, seed, i * dt);
                            }
                        } else if (!here) {
                            logged[c] = false;
                        }
                    }

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
        if (graceCount > 0) {
            System.out.printf("door-kill grace: mean %.1fs, min %.1fs, max %.1fs over %d kills%n",
                    graceSum / graceCount, graceMin, graceMax, graceCount);
        } else {
            System.out.println("door-kill grace: no door kills in this sample");
        }
    }
}
