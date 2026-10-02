package aside.games.fnaf2.engine;

import aside.games.fnaf2.MouseMap;

import java.nio.file.Files;
import java.nio.file.Path;

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

    /** Do two rectangles share area? Touching edges do not count. */
    static boolean overlaps(double[] a, double[] b) {
        return a[0] < b[0] + b[2] && b[0] < a[0] + a[2]
            && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
    }

    /** An n x n grid of interior points of a rectangle, for "nowhere in
     *  here does X" -- a centre alone cannot tell a whole region from a
     *  point that happens to be right. */
    static java.util.List<double[]> grid(double[] r, int n) {
        java.util.List<double[]> pts = new java.util.ArrayList<>();
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= n; j++) {
                pts.add(new double[]{ r[0] + r[2] * i / (n + 1.0),
                                      r[1] + r[3] * j / (n + 1.0) });
            }
        }
        return pts;
    }

    /** Click a point, the way the screen does: resolve it, then apply it. */
    static void click(Game g, double[] p) {
        Clicks.apply(g, MouseMap.hit(p[0], p[1], g.cameraUp, g.maskOn, g.currentCam));
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

        // 12. The mouse map.
        //
        //     Pure geometry, so it can be checked without a display -- and it
        //     has to be, because the failure mode is invisible in a
        //     screenshot: a button drawn in one place and clickable in
        //     another looks perfectly fine in a picture.
        {
            check("MouseMap's music box camera is Game's",
                    MouseMap.MUSIC_BOX_CAM == Game.MUSIC_BOX_CAM);

            double[][] openings = { MouseMap.rect(MouseMap.HALL_SRC),
                                    MouseMap.rect(MouseMap.VENT_L_SRC),
                                    MouseMap.rect(MouseMap.VENT_R_SRC) };
            MouseMap.Kind[] kinds = { MouseMap.Kind.HALL, MouseMap.Kind.VENT_L,
                                      MouseMap.Kind.VENT_R };

            // Every opening answers for itself, over its whole area -- not
            // just at its centre, which is where a wrong rectangle still
            // happens to be right.
            boolean each = true;
            for (int i = 0; i < openings.length; i++) {
                for (double[] p : grid(openings[i], 7)) {
                    each &= MouseMap.hit(p[0], p[1], false, false, 1).kind() == kinds[i];
                }
            }
            check("every point of every opening answers for that opening", each);

            // Nothing overlaps anything: two regions that share area make a
            // click ambiguous, and the order in hit() then decides, silently.
            boolean disjoint = !overlaps(MouseMap.MASK_BTN, MouseMap.MONITOR_BTN);
            for (int i = 0; i < openings.length; i++) {
                for (int j = i + 1; j < openings.length; j++) {
                    disjoint &= !overlaps(openings[i], openings[j]);
                }
                disjoint &= !overlaps(openings[i], MouseMap.MASK_BTN);
                disjoint &= !overlaps(openings[i], MouseMap.MONITOR_BTN);
            }
            check("no opening overlaps another opening or a button", disjoint);

            double[] mb = MouseMap.centre(MouseMap.MASK_BTN);
            double[] nb = MouseMap.centre(MouseMap.MONITOR_BTN);
            check("the MASK button answers MASK",
                    MouseMap.hit(mb[0], mb[1], false, false, 1).kind() == MouseMap.Kind.MASK);
            check("the MONITOR button answers MONITOR",
                    MouseMap.hit(nb[0], nb[1], false, false, 1).kind() == MouseMap.Kind.MONITOR);

            // Both buttons are live in both views, at the same place. With
            // the monitor up they are the only way out of it, so a mouse
            // player who could not reach them would be stuck pressing SPACE.
            boolean bothViews = true;
            for (boolean up : new boolean[]{ false, true }) {
                bothViews &= MouseMap.hit(mb[0], mb[1], up, false, 1).kind() == MouseMap.Kind.MASK;
                bothViews &= MouseMap.hit(nb[0], nb[1], up, false, 1).kind()
                        == MouseMap.Kind.MONITOR;
            }
            check("both buttons are live with the monitor up and down", bothViews);

            // And the room itself is not a button. Corners, the clock, the
            // middle of the floor.
            double[][] dead = { {2, 2}, {1278, 2}, {2, 718}, {1278, 718},
                                {1250, 48}, {640, 100} };
            boolean quiet = true;
            for (double[] p : dead) {
                quiet &= MouseMap.hit(p[0], p[1], false, false, 1).kind() == MouseMap.Kind.NONE;
            }
            check("a click on the room itself does nothing", quiet);

            // The office is not reachable through the monitor. Sampled over
            // the openings rather than at their centres: the right vent's
            // centre is under the camera strip, which is the correct answer
            // and not the one being asked about here.
            boolean through = true;
            for (double[] r : openings) {
                for (double[] p : grid(r, 7)) {
                    MouseMap.Kind k = MouseMap.hit(p[0], p[1], true, false, 1).kind();
                    through &= k != MouseMap.Kind.HALL && k != MouseMap.Kind.VENT_L
                            && k != MouseMap.Kind.VENT_R;
                }
            }
            check("the office cannot be clicked through the monitor", through);

            // Every camera row selects its own camera, and no two overlap.
            boolean rows = true, rowsDisjoint = true;
            for (int cam = 1; cam <= MouseMap.MUSIC_BOX_CAM; cam++) {
                for (double[] p : grid(MouseMap.camRow(cam), 3)) {
                    MouseMap.Hit h = MouseMap.hit(p[0], p[1], true, false, 1);
                    rows &= h.kind() == MouseMap.Kind.CAM && h.cam() == cam;
                }
                for (int other = cam + 1; other <= MouseMap.MUSIC_BOX_CAM; other++) {
                    rowsDisjoint &= !overlaps(MouseMap.camRow(cam), MouseMap.camRow(other));
                }
            }
            check("every point of every camera row selects that camera", rows);
            check("no two camera rows overlap", rowsDisjoint);

            // The strip fits between the clock and the hint, and covers no
            // part of the music box bar.
            double[] lastRow = MouseMap.camRow(MouseMap.MUSIC_BOX_CAM);
            check("the camera strip fits between the clock and the hint",
                    MouseMap.CAM_STRIP_Y > 90 && lastRow[1] + lastRow[3] < 690);
            boolean stripClear = true;
            for (int cam = 1; cam <= MouseMap.MUSIC_BOX_CAM; cam++) {
                stripClear &= !overlaps(MouseMap.camRow(cam), MouseMap.WIND_BTN);
                stripClear &= !overlaps(MouseMap.camRow(cam), MouseMap.MASK_BTN);
                stripClear &= !overlaps(MouseMap.camRow(cam), MouseMap.MONITOR_BTN);
            }
            check("no camera row covers the music box bar or a button", stripClear);

            // The panel stops short of the button strip, so nothing on
            // CAM 11 is drawn over a button or clickable as one.
            check("the music box panel stops short of the buttons",
                    !overlaps(MouseMap.MUSIC_BOX_PANEL, MouseMap.MASK_BTN)
                            && !overlaps(MouseMap.MUSIC_BOX_PANEL, MouseMap.MONITOR_BTN));
            check("the wind bar is inside its own panel",
                    MouseMap.WIND_BTN[0] >= MouseMap.MUSIC_BOX_PANEL[0]
                            && MouseMap.WIND_BTN[1] >= MouseMap.MUSIC_BOX_PANEL[1]
                            && MouseMap.WIND_BTN[0] + MouseMap.WIND_BTN[2]
                                    <= MouseMap.MUSIC_BOX_PANEL[0] + MouseMap.MUSIC_BOX_PANEL[2]
                            && MouseMap.WIND_BTN[1] + MouseMap.WIND_BTN[3]
                                    <= MouseMap.MUSIC_BOX_PANEL[1] + MouseMap.MUSIC_BOX_PANEL[3]);

            // The bar winds on its own camera and nowhere else.
            double[] w = MouseMap.centre(MouseMap.WIND_BTN);
            check("the music box bar winds on CAM 11",
                    MouseMap.hit(w[0], w[1], true, false, MouseMap.MUSIC_BOX_CAM).kind()
                            == MouseMap.Kind.WIND);
            check("the music box bar is not there on CAM 1",
                    MouseMap.hit(w[0], w[1], true, false, 1).kind() == MouseMap.Kind.NONE);

            // Masked, every click is the way out.
            boolean out = true;
            for (double[] r : openings) {
                for (double[] p : grid(r, 3)) {
                    out &= MouseMap.hit(p[0], p[1], false, true, 1).kind() == MouseMap.Kind.MASK;
                }
            }
            check("any click while masked lowers the mask", out);

            // Everything clickable is on the canvas.
            boolean onCanvas = true;
            for (double[] r : new double[][]{ MouseMap.MASK_BTN, MouseMap.MONITOR_BTN,
                                              MouseMap.WIND_BTN, MouseMap.MUSIC_BOX_PANEL,
                                              MouseMap.camRow(1),
                                              MouseMap.camRow(MouseMap.MUSIC_BOX_CAM) }) {
                onCanvas &= r[0] >= 0 && r[1] >= 0
                        && r[0] + r[2] <= MouseMap.W && r[1] + r[3] <= MouseMap.H;
            }
            check("every clickable region is on the canvas", onCanvas);

            // The night rows: each one answers for itself, they do not
            // overlap, and the gaps between them answer for nobody. The
            // highlight is drawn from the same rectangle, so a row that
            // lights up is the row that starts.
            boolean nights = true, nightsDisjoint = true, gapsQuiet = true;
            for (int i = 0; i < MouseMap.NIGHTS; i++) {
                double[] c = MouseMap.centre(MouseMap.nightRow(i));
                nights &= MouseMap.nightAt(c[0], c[1]) == i;
                for (int j = i + 1; j < MouseMap.NIGHTS; j++) {
                    nightsDisjoint &= !overlaps(MouseMap.nightRow(i), MouseMap.nightRow(j));
                }
                if (i + 1 < MouseMap.NIGHTS) {
                    double gapY = MouseMap.nightRow(i)[1] + MouseMap.nightRow(i)[3] + 2;
                    gapsQuiet &= MouseMap.nightAt(c[0], gapY) == -1;
                }
            }
            check("every night row selects its own night", nights);
            check("no two night rows overlap", nightsDisjoint);
            check("the gaps between the night rows select nothing", gapsQuiet);
            check("a click above or below the night list selects nothing",
                    MouseMap.nightAt(MouseMap.W / 2.0, 60) == -1
                            && MouseMap.nightAt(MouseMap.W / 2.0, MouseMap.H - 60) == -1);
        }

        // 13. What a click does.
        //
        //     The geometry above says where a click lands; this says the
        //     landing does something. A screenshot cannot tell a click that
        //     did nothing from a click that was never made, so it has to be
        //     asserted here.
        {
            double[] hall = MouseMap.centre(MouseMap.rect(MouseMap.HALL_SRC));
            double[] vl   = MouseMap.centre(MouseMap.rect(MouseMap.VENT_L_SRC));
            double[] vr   = MouseMap.centre(MouseMap.rect(MouseMap.VENT_R_SRC));
            double[] mb   = MouseMap.centre(MouseMap.MASK_BTN);
            double[] nb   = MouseMap.centre(MouseMap.MONITOR_BTN);
            double[] w    = MouseMap.centre(MouseMap.WIND_BTN);
            double[] c5   = MouseMap.centre(MouseMap.camRow(5));

            // An opening toggles its own light, and only its own.
            Game g = new Game(1, 7L);
            click(g, hall);
            check("clicking the hall turns the hall light on", g.hallLightOn);
            click(g, vl);
            check("clicking the left vent moves the light and turns the hall off",
                    g.ventLLightOn && !g.hallLightOn);
            click(g, vr);
            check("clicking the right vent moves the light again",
                    g.ventRLightOn && !g.ventLLightOn);
            click(g, vr);
            check("clicking the lit opening again turns it off",
                    !g.hallLightOn && !g.ventLLightOn && !g.ventRLightOn);

            // The mask button wears it; anywhere at all takes it off.
            g = new Game(1, 7L);
            click(g, mb);
            check("clicking MASK wears the mask", g.maskOn);
            click(g, hall);
            check("clicking anywhere while masked takes it off", !g.maskOn);

            // The monitor button raises it, and a camera row selects.
            g = new Game(1, 7L);
            click(g, nb);
            check("clicking MONITOR raises the monitor", g.cameraUp);
            click(g, c5);
            check("clicking CAM 05 selects camera 5", g.currentCam == 5);
            click(g, nb);
            check("clicking the same button again lowers the monitor", !g.cameraUp);

            // The bar winds, and winds only where it is drawn.
            g = new Game(1, 7L);
            click(g, nb);
            click(g, MouseMap.centre(MouseMap.camRow(MouseMap.MUSIC_BOX_CAM)));
            click(g, w);
            check("clicking the bar on CAM 11 starts winding", g.winding);
            click(g, w);
            check("clicking the bar again stops winding", !g.winding);

            // ... and the flag cannot survive the camera being taken away.
            //     This is the one state the keyboard could never produce,
            //     because releasing W always cleared it.
            g = new Game(1, 7L);
            click(g, nb);
            click(g, MouseMap.centre(MouseMap.camRow(MouseMap.MUSIC_BOX_CAM)));
            click(g, w);
            click(g, nb);                       // monitor down
            click(g, nb);                       // and up again
            click(g, MouseMap.centre(MouseMap.camRow(MouseMap.MUSIC_BOX_CAM)));
            check("winding does not resume by itself after the monitor drops",
                    !g.winding);

            g = new Game(1, 7L);
            click(g, nb);
            click(g, MouseMap.centre(MouseMap.camRow(MouseMap.MUSIC_BOX_CAM)));
            click(g, w);
            click(g, mb);                       // mask on: camera goes down
            check("the mask stops the winding too", !g.winding && !g.cameraUp);

            // A click on the room does nothing at all.
            g = new Game(1, 7L);
            click(g, new double[]{ 640, 100 });
            check("a click on the room changes nothing",
                    !g.hallLightOn && !g.ventLLightOn && !g.ventRLightOn
                            && !g.maskOn && !g.cameraUp && !g.winding);

            // A click after the night is over is ignored, not queued.
            g = new Game(1, 7L);
            g.status = Game.Status.JUMPSCARED;
            click(g, mb);
            check("a click after the night is over does nothing", !g.maskOn);
        }

        // 14. The competent bot survives the week.
        //
        // This used to be a printout, and it read 40/40, 40/40, 40/40,
        // 30/40, 19/40 -- a game that looked unfair on its last two nights.
        // It was not: the bot's own light hold was 0.45s, so its rotation
        // (three openings plus the blind wind burst) took 2.35s while the
        // night 5 grace was 2.15s, and it lost threats it never had a chance
        // to see. The knob is 0.25 now and the week is clean. A printout
        // would have let that drift back; this is a check, so it cannot.
        //
        // The margin is printed too, because it is the ramp: the competent
        // bot wins every night, so the week's difficulty lives entirely in
        // how much slack is left between the rotation and the grace.
        System.out.println();
        {
            int runs = 40;
            // Worst case: a threat arrives just after its own opening was
            // checked and waits out the rest of the rotation, wind included.
            Bot probe = new Bot(new Game(1, 1L), 1L);
            double rotation = 3 * probe.lightHold + probe.windBurst;
            for (int night = 1; night <= 5; night++) {
                int wins = 0;
                for (int seed = 1; seed <= runs; seed++) {
                    Game g = new Game(night, seed * 7919L);
                    Bot bot = new Bot(g, seed * 104729L);
                    int steps = 0;
                    while (g.status == Game.Status.PLAYING && steps++ < 60 * 300) {
                        bot.play(1.0 / 60);
                        g.update(1.0 / 60);
                    }
                    if (g.status == Game.Status.SURVIVED) wins++;
                }
                double margin = new Game(night, 1L).openingGrace() - rotation;
                System.out.printf("  night %d: bot survived %d/%d, margin %.2fs%n",
                        night, wins, runs, margin);
                check("a competent bot survives night " + night
                        + " (" + wins + "/" + runs + ")", wins >= runs - 2);
                check("night " + night + " leaves the rotation room inside the grace"
                        + " (margin " + String.format("%.2f", margin) + "s)", margin > 0);
            }
        }

        // 15. Every arrival is announced, and the monitor says so.
        //
        // Both of these were written down as fixed and were not in the code.
        // The six path-walkers arrived in silence (only Foxy raised a cue),
        // and the monitor said nothing when they did -- which is exactly the
        // failure Chase reported from playtest: on the monitor, winding the
        // box, jumpscared by something he never had a chance to see. A check
        // that only ran Foxy would have missed it, so this one runs a walker.
        {
            // Night 6 puts aiLevel at 20, so the roll always succeeds and the
            // arrival is deterministic rather than a wait on a 10% chance.
            Game g = new Game(6, 7L);
            Animatronic walker = g.toyBonnie;
            walker.pathIndex = walker.path.length - 1;
            boolean cued = false;
            for (int i = 0; i < 60 * 60 && !cued; i++) {
                walker.update(1.0 / 60);
                if (g.drainCues().contains("at_door")) cued = true;
            }
            check("a path-walker's arrival raises at_door", cued);
            check("and it is standing in an opening", walker.atOpening());
            check("the office warning is up while it stands there",
                    g.someoneAtTheOffice());

            // The mask answers it, and the warning clears with it.
            g.maskOn = true;
            g.update(1.0 / 60);
            check("answering the opening clears the warning",
                    !g.someoneAtTheOffice());

            // Foxy counts too -- he is the one that kills you from a doorway
            // the monitor is covering.
            Game f = new Game(1, 7L);
            f.witheredFoxy.stages = 3;
            check("Foxy in the hall raises the warning", f.someoneAtTheOffice());

            check("an empty office does not warn",
                    !new Game(1, 7L).someoneAtTheOffice());
        }

        phone();

        System.out.printf("%n%d checks, %d failed%n", checks, failed);
        if (failed > 0) System.exit(1);
    }

    /**
     * The phone build.
     *
     * <p>Two different things, and they fail for different reasons.
     *
     * <p><b>Is it current?</b> A generated file that has gone stale is worse
     * than no file: it is a second copy of the game quietly disagreeing with
     * the first. So the page is regenerated and compared rather than
     * spot-checked -- the same check every other ported game carries.
     *
     * <p><b>Is it the same game?</b> A staleness check proves the file matches
     * its generator and says nothing about whether the generator is right. The
     * failure that would otherwise be silent is a rule that lives in the page
     * as a literal and has stopped matching the engine -- so every number the
     * night is made of is asserted to be the engine's own value, and the cast
     * and their paths are asserted to be the engine's own cast.
     *
     * <p>What is <i>not</i> checked here is the RNG or the update loop. The
     * page reproduces {@code java.util.Random} in BigInt -- including
     * {@code nextInt}, which is a rejection sampler rather than a modulo --
     * and that was verified by driving both engines under the same scripted
     * policy: night one on seed 1001 produces identical traces every five
     * seconds (the music box, all seven of the cast's rooms, Foxy's stage,
     * whether the lights are gone and whether the Puppet is coming) and both
     * end JUMPSCARED at the same moment. It is not checkable from Java without
     * a JavaScript engine, and the alternative (a page that deals its own
     * nights) would make the two builds different games with the same rules.
     */
    static void phone() {
        System.out.println();
        System.out.println("== the phone build ==");

        Path out = Path.of("web", "fnaf2.html");
        if (!Files.exists(out)) {
            System.out.println("       (no " + out + " from here -- run from the repository root)");
            return;
        }
        String page;
        try {
            page = Files.readString(out);
        } catch (Exception e) {
            check("web/fnaf2.html can be read", false);
            return;
        }
        try {
            check("web/fnaf2.html is current -- regenerate it with aside.games.fnaf2.WebPizzeria",
                    aside.games.fnaf2.WebPizzeria.html().equals(page));
        } catch (Exception e) {
            check("web/fnaf2.html is current -- regenerate it with aside.games.fnaf2.WebPizzeria",
                    false);
        }

        // The rules, as numbers. A port whose box drains at 1.9 rather than
        // 1.818 plays differently and looks identical.
        check("the page carries the box's capacity",
                page.contains("musicBoxMax: " + aside.games.fnaf2.WebPizzeria.num(Game.MUSIC_BOX_MAX)));
        check("the page carries how fast the box drains",
                page.contains("musicBoxDrain: " + aside.games.fnaf2.WebPizzeria.num(Game.MUSIC_BOX_DRAIN)));
        check("the page carries how fast the box winds",
                page.contains("musicBoxWind: " + aside.games.fnaf2.WebPizzeria.num(Game.MUSIC_BOX_WIND)));
        check("the page carries how long the Puppet takes",
                page.contains("puppetGrace: " + aside.games.fnaf2.WebPizzeria.num(Game.PUPPET_GRACE)));
        check("the page carries Foxy's hall window",
                page.contains("foxyHallWindow: " + aside.games.fnaf2.WebPizzeria.num(Game.FOXY_HALL_WINDOW)));
        check("the page carries how much light repels Foxy",
                page.contains("foxyRepelTime: " + aside.games.fnaf2.WebPizzeria.num(Game.FOXY_REPEL_TIME)));
        check("the page carries the opening grace",
                page.contains("openingGraceMax: " + aside.games.fnaf2.WebPizzeria.num(Game.OPENING_GRACE_MAX))
                        && page.contains("openingGraceMin: "
                                + aside.games.fnaf2.WebPizzeria.num(Game.OPENING_GRACE_MIN)));
        check("the page carries which camera the music box is on",
                page.contains("musicBoxCam: " + Game.MUSIC_BOX_CAM));
        check("the page carries which camera freezes Foxy",
                page.contains("coveCam: " + Game.COVE_CAM));

        // The drain table, which is the honest difficulty lever.
        StringBuilder drain = new StringBuilder("drainMult: [");
        for (int i = 0; i < Game.DRAIN_MULT.length; i++) {
            if (i > 0) drain.append(", ");
            drain.append(aside.games.fnaf2.WebPizzeria.num(Game.DRAIN_MULT[i]));
        }
        drain.append("]");
        check("the page carries the drain table", page.contains(drain));

        // And the cast, with the paths that are the whole of their movement.
        Game g = new Game(1, 1);
        for (Animatronic a : new Animatronic[]{g.toyFreddy, g.toyBonnie, g.toyChica,
                g.mangle, g.witheredBonnie, g.balloonBoy, g.witheredFoxy, g.puppet}) {
            StringBuilder want = new StringBuilder("name:\"" + a.name + "\", path: [");
            for (int j = 0; j < a.path.length; j++) {
                if (j > 0) want.append(", ");
                want.append(a.path[j]);
            }
            want.append("], opening:\"").append(a.opening.name())
                .append("\", answer:\"").append(a.answer.name())
                .append("\", lethal:").append(a.lethal);
            check("the page carries " + a.name + "'s path, opening and answer",
                    page.contains(want));
        }
    
        // The sound. This game's arrival cue is not decoration: the engine carries Chase's own
        // playtest note -- "I checked lights, went to wind the music box, and got
        // jumpscared. If the timer between entering and killing is 5 seconds, then they
        // arent visible." The grace is what makes an arrival survivable; the cue is what
        // makes it fair, and a silent port is an unfair one.
        check("the page carries the shared synthesiser",
                page.contains("function voice(") && page.contains("function sfx("));
        check("the page has a voice for the at_door cue",
                page.contains("case \"at_door\""));
        check("the page has a voice for the camera_down cue",
                page.contains("case \"camera_down\""));
        check("the page has a voice for the chime_6am cue",
                page.contains("case \"chime_6am\""));
        check("the page has a voice for the door_close cue",
                page.contains("case \"door_close\""));
        check("the page has a voice for the light_click cue",
                page.contains("case \"light_click\""));
        check("the page has a voice for the music_box cue",
                page.contains("case \"music_box\""));
        check("the page has a voice for the power_down cue",
                page.contains("case \"power_down\""));
        check("the page has a voice for the static cue",
                page.contains("case \"static\""));
}
}
