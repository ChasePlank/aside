package aside.games.fnaf2.engine;

/**
 * Headless self-test for FNAF 2.
 *
 * A survival game is only fair if a player playing reasonably well can
 * win, and the bot is that reasonable player. But the bot cannot find
 * everything: it reads state directly and never has to *see* a threat,
 * so it cannot tell you whether a threat was visible, announced, or
 * reactable. Those are checked here by hand, in the one place a person
 * would notice them.
 */
public class SelfTest {

    static int checks = 0, failed = 0;

    static void check(String what, boolean ok) {
        checks++;
        if (!ok) failed++;
        System.out.printf("  %s %s%n", ok ? "ok  " : "FAIL", what);
    }

    public static void main(String[] args) {
        System.out.println("=== FNAF 2 self-test ===\n");

        // 1. A passive player dies. Night 3, no input at all.
        {
            Game g = new Game(3, 12345L);
            for (int i = 0; i < 60 * 300 && g.status == Game.Status.PLAYING; i++) {
                g.update(1.0 / 60);
            }
            check("a passive player dies on night 3 (got " + g.status
                    + " by " + (g.jumpscareBy == null ? "?" : g.jumpscareBy.name) + ")",
                    g.status == Game.Status.JUMPSCARED);
        }

        // 2. A competent bot survives night 1.
        {
            int wins = 0, runs = 60;
            int[] killer = new int[8];
            Animatronic[] cast = new Game(1, 1L).cast();
            for (int seed = 1; seed <= runs; seed++) {
                Game g = new Game(1, seed * 7919L);
                Bot bot = new Bot(g, seed * 104729L);
                double dt = 1.0 / 60;
                int steps = 0;
                while (g.status == Game.Status.PLAYING && steps++ < 60 * 300) {
                    bot.play(dt);
                    g.update(dt);
                }
                if (g.status == Game.Status.SURVIVED) wins++;
                else if (g.jumpscareBy != null) {
                    for (int i = 0; i < cast.length; i++) {
                        if (cast[i].name.equals(g.jumpscareBy.name)) killer[i]++;
                    }
                }
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < cast.length; i++) {
                if (killer[i] > 0) sb.append(" ").append(cast[i].name).append("=").append(killer[i]);
            }
            System.out.println("  bot night 1: " + wins + "/" + runs + " survived;" + sb);
            check("a competent bot survives night 1 (" + wins + "/" + runs + ")",
                    wins >= runs - 3);
        }

        // 3. The music box stays inside 0..100.
        {
            Game g = new Game(2, 999L);
            Bot bot = new Bot(g, 999L);
            boolean inRange = true;
            for (int i = 0; i < 60 * 300 && g.status == Game.Status.PLAYING; i++) {
                bot.play(1.0 / 60);
                g.update(1.0 / 60);
                if (g.musicBox < 0 || g.musicBox > Game.MUSIC_BOX_MAX) inRange = false;
            }
            check("the music box stays within 0..100", inRange);
        }

        // 4. Ignoring the music box summons the Puppet.
        {
            Game g = new Game(1, 7L);
            for (int i = 0; i < 60 * 300 && g.status == Game.Status.PLAYING; i++) {
                // answer openings by hand so nothing else can kill us
                g.maskOn = true;
                g.update(1.0 / 60);
            }
            check("ignoring the music box summons the Puppet (by "
                    + (g.jumpscareBy == null ? "?" : g.jumpscareBy.name) + ")",
                    g.status == Game.Status.JUMPSCARED && g.jumpscareBy == g.puppet);
        }

        // 5. The mask sends Toy Bonnie away.
        {
            Game g = new Game(1, 7L);
            g.toyBonnie.pathIndex = g.toyBonnie.path.length;   // standing in the right vent
            g.maskOn = true;
            for (int i = 0; i < 60; i++) g.update(1.0 / 60);
            check("the mask sends Toy Bonnie away",
                    !g.toyBonnie.atOpening() && g.status == Game.Status.PLAYING);
        }

        // 6. The mask does NOT fool Withered Foxy.
        {
            Game g = new Game(1, 7L);
            g.witheredFoxy.stages = 3;
            g.witheredFoxy.arrivalAnnounced = true;
            g.witheredFoxy.hallWindow = Game.FOXY_HALL_WINDOW;
            g.maskOn = true;
            for (int i = 0; i < 60 * 8 && g.status == Game.Status.PLAYING; i++) {
                g.update(1.0 / 60);
            }
            check("the mask does not fool Withered Foxy",
                    g.status == Game.Status.JUMPSCARED && g.jumpscareBy == g.witheredFoxy);
        }

        // 7. The hall light repels Withered Foxy -- and a flash is enough.
        {
            Game g = new Game(1, 7L);
            g.witheredFoxy.stages = 3;
            g.witheredFoxy.arrivalAnnounced = true;
            g.witheredFoxy.hallWindow = Game.FOXY_HALL_WINDOW;
            // A flash, not a hold: 0.3s of light, then off.
            for (int i = 0; i < 18; i++) { g.hallLightOn = true; g.update(1.0 / 60); }
            g.hallLightOn = false;
            for (int i = 0; i < 60 * 6 && g.status == Game.Status.PLAYING; i++) {
                g.update(1.0 / 60);
            }
            check("the hall light repels Withered Foxy (stages="
                    + g.witheredFoxy.stages + ", status=" + g.status + ")",
                    g.witheredFoxy.stages == 0 && g.status == Game.Status.PLAYING);
        }

        // 8. Balloon Boy disables the lights instead of killing.
        {
            Game g = new Game(1, 7L);
            g.balloonBoy.pathIndex = g.balloonBoy.path.length;
            for (int i = 0; i < 60 * 6 && g.status == Game.Status.PLAYING; i++) {
                g.update(1.0 / 60);
            }
            check("Balloon Boy disables the lights instead of killing (lights="
                    + g.lightsDisabled + ", status=" + g.status + ")",
                    g.lightsDisabled && g.status == Game.Status.PLAYING);
        }

        // 9. The night ends at 6 AM.
        {
            Game g = new Game(1, 7L);
            for (int i = 0; i < 60 * 300 && g.status == Game.Status.PLAYING; i++) {
                g.maskOn = true;                 // answer the mask openings
                g.hallLightOn = true;            // and repel Foxy
                g.musicBox = Game.MUSIC_BOX_MAX; // never let the box run out
                g.update(1.0 / 60);
            }
            check("the night ends at 6 AM (hour=" + g.hour + ")",
                    g.status == Game.Status.SURVIVED && g.hour >= Game.NIGHT_HOURS);
        }

        // 10. The mask costs you the camera and the lights.
        {
            Game g = new Game(1, 7L);
            g.cameraUp = true;
            g.hallLightOn = true;
            g.toggleMask();
            check("the mask takes the camera and every light with it",
                    g.maskOn && !g.cameraUp && !g.hallLightOn);
        }

        // 11. Balloon Boy's effect cannot fire while the mask is up.
        //     This is the ordering bug, asserted rather than remembered.
        {
            Game g = new Game(1, 7L);
            g.balloonBoy.pathIndex = g.balloonBoy.path.length;
            g.maskOn = true;
            for (int i = 0; i < 60 * 6; i++) { g.musicBox = Game.MUSIC_BOX_MAX; g.update(1.0 / 60); }
            check("the mask answers Balloon Boy before he takes the lights",
                    !g.lightsDisabled);
        }

        // 12. Bot survival across nights, for the record.
        System.out.println();
        for (int night = 1; night <= 5; night++) {
            int wins = 0;
            for (int seed = 1; seed <= 40; seed++) {
                Game g = new Game(night, seed * 7919L);
                Bot bot = new Bot(g, seed * 104729L);
                int steps = 0;
                while (g.status == Game.Status.PLAYING && steps++ < 60 * 300) {
                    bot.play(1.0 / 60);
                    g.update(1.0 / 60);
                }
                if (g.status == Game.Status.SURVIVED) wins++;
            }
            System.out.printf("  night %d: bot survived %d/40%n", night, wins);
        }

        System.out.printf("%n%d checks, %d failed%n", checks, failed);
        if (failed > 0) System.exit(1);
    }
}
