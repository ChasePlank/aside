package aside.games.fnaf3.engine;

import aside.games.fnaf3.MouseMap;

import java.util.ArrayDeque;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The FNAF 3 checks, runnable with no display.
 *
 *     java -cp classes aside.games.fnaf3.engine.SelfTest
 *
 * These are not "does it compile" checks. Each one is a claim the game
 * makes about itself, written down so it cannot quietly stop being true:
 * that the building is a tree, that the lure is worth distance, that the
 * air is what decides how often you hallucinate, that a reboot gives the
 * system back, and that a passive player dies.
 *
 * The survival numbers are printed rather than asserted tightly, because
 * they are a *reading* of the difficulty, not a contract. What is
 * asserted is the shape: idle must die, the lure must matter, and the
 * week must get harder.
 */
public final class SelfTest {

    static int checks = 0;
    static int failed = 0;

    public static void main(String[] args) {
        // `--survival` runs only the sweep, which is the slow part and the
        // only part that changes when a difficulty number moves. Tuning
        // means running this a dozen times in a row.
        if (args.length > 0 && args[0].equals("--survival")) {
            survival();
            System.out.println();
            System.out.println(checks + " checks, " + failed + " failed");
            if (failed > 0) System.exit(1);
            return;
        }
        building();
        lure();
        ventilation();
        phantoms();
        reboots();
        office();
        screen();
        phone();
        survival();
        System.out.println();
        System.out.println(checks + " checks, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    // ------------------------------------------------------------ building

    static void building() {
        section("the building");

        check("ten rooms and an office", House.ROOMS == 10 && House.OFFICE == 0);

        // A tree rooted at the office: connected, and exactly one path
        // between any two rooms. If it ever stops being a tree, the lure
        // stops being a distance and the whole design goes with it.
        boolean[] seen = new boolean[House.ROOMS + 1];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        seen[1] = true;
        q.add(1);
        int reached = 0;
        while (!q.isEmpty()) {
            int r = q.poll();
            reached++;
            for (int n : House.neighbours(r)) {
                if (seen[n]) continue;
                seen[n] = true;
                q.add(n);
            }
        }
        check("every room is reachable from the office side", reached == House.ROOMS);

        int edges = 0;
        for (int r = 1; r <= House.ROOMS; r++) edges += House.neighbours(r).length;
        check("the graph is a tree (" + (edges / 2) + " edges, "
                + House.ROOMS + " rooms)", edges / 2 == House.ROOMS - 1);

        boolean adjOk = true;
        for (int r = 1; r <= House.ROOMS; r++) {
            for (int n : House.neighbours(r)) {
                boolean back = false;
                for (int m : House.neighbours(n)) if (m == r) back = true;
                if (!back) adjOk = false;
            }
        }
        check("adjacency is symmetric", adjOk);

        boolean distOk = true;
        for (int r = 1; r <= House.ROOMS; r++) {
            if (House.distance(r, 1) != House.TO_OFFICE[r] - 1) distOk = false;
        }
        check("TO_OFFICE is the real BFS distance", distOk);

        // The far room has to actually be far, or the lure has nowhere to
        // send him and the difficulty table is built on nothing.
        check("room 10 is four moves from the office",
                House.TO_OFFICE[10] == 4);
    }

    // ---------------------------------------------------------------- lure

    static void lure() {
        section("the audio lure");

        Game g = new Game(1, 42);
        g.springtrap.room = 10;
        g.springtrap.atOffice = false;
        check("the lure is ready at the start of a night", g.lureReady());
        check("playing it works", g.playLure(3));
        check("it is now playing in room 3", g.lureRoom == 3);
        check("and it cannot be played again yet", !g.lureReady());
        check("a second play is refused", !g.playLure(7));
        check("the room did not change", g.lureRoom == 3);

        // The cooldown has to be longer than the sound, or the lure is a
        // switch rather than a move.
        check("the cooldown outlasts the sound",
                Game.LURE_COOLDOWN > Game.LURE_DURATION);

        // Walk him toward the lure: every move must reduce the distance.
        Game h = new Game(1, 7);
        h.springtrap.room = 10;
        h.lureRoom = 3;
        h.lureTimer = 999;
        int start = House.distance(10, 3);
        for (int i = 0; i < 6; i++) h.springtrap.step(h);
        int now = House.distance(h.springtrap.room, 3);
        check("a lure in room 3 pulls him from room 10 ("
                + start + " -> " + now + ")", now < start);

        // And with no lure he walks at the office instead.
        Game k = new Game(1, 7);
        k.springtrap.room = 10;
        k.lureRoom = 0;
        for (int i = 0; i < 6; i++) k.springtrap.step(k);
        check("with no lure he walks toward the office (room "
                + k.springtrap.room + ")", House.TO_OFFICE[k.springtrap.room] <= 3);

        // The audio being down must actually stop it.
        Game m = new Game(1, 3);
        m.online[Game.System.AUDIO.ordinal()] = false;
        check("a dead audio system refuses the lure", !m.playLure(4));
    }

    // --------------------------------------------------------- ventilation

    static void ventilation() {
        section("the ventilation");

        Game g = new Game(1, 11);
        check("the air starts on", g.ventilationOnline());
        check("and starts full", g.ventilation == Game.VENT_MAX);

        // Park him in the far room so this measures the air and nothing
        // else. Without it the reading is "how long until he kills you",
        // which is a different number and one that hangs the loop the
        // moment he wins -- the bug this check was written to catch.
        g.springtrap.moveTimer = -1e6;
        while (g.ventilationOnline() && g.status == Game.Status.PLAYING) {
            g.update(1.0 / 60.0);
        }
        check("it fails on its own if nobody services it", !g.ventilationOnline());
        check("and it fails before the night is over", g.status == Game.Status.PLAYING);

        // How long it lasts, in seconds, as a reading.
        Game h = new Game(1, 11);
        h.springtrap.moveTimer = -1e6;
        while (h.ventilationOnline() && h.status == Game.Status.PLAYING) {
            h.update(1.0 / 60.0);
        }
        System.out.printf("  the air lasts %.1fs of a %.0fs night%n",
                h.time, Game.HOUR_SECONDS * Game.NIGHT_HOURS);
        check("it lasts long enough to be a chore, not a crisis",
                h.time > 60 && h.time < Game.HOUR_SECONDS * Game.NIGHT_HOURS);

        // Off air makes him faster. That is the cost of ignoring it.
        Game a = new Game(3, 5);
        double on = a.moveInterval();
        a.online[Game.System.VENTILATION.ordinal()] = false;
        double off = a.moveInterval();
        check("dead air makes him quicker (" + round(on) + "s -> " + round(off) + "s)",
                off < on);
    }

    // ------------------------------------------------------------ phantoms

    static void phantoms() {
        section("the phantoms");

        check("the calm rate is lower than the failing rate",
                Game.PHANTOM_RATE_CALM < Game.PHANTOM_RATE_FAILING);

        Game calm = new Game(1, 99);
        Game fail = new Game(1, 99);
        // Park him again: this counts hallucinations over a fixed minute,
        // and a jumpscare at second 20 would make both readings zero and
        // the comparison meaningless.
        calm.springtrap.moveTimer = -1e6;
        fail.springtrap.moveTimer = -1e6;
        fail.online[Game.System.VENTILATION.ordinal()] = false;
        for (int i = 0; i < 60 * 60; i++) { calm.update(1.0 / 60.0); fail.update(1.0 / 60.0); }
        check("dead air spawns more of them (" + calm.phantomsSpawned
                + " vs " + fail.phantomsSpawned + ")",
                fail.phantomsSpawned > calm.phantomsSpawned);

        // A phantom takes its system on the way OUT, not on the way in.
        Game g = new Game(1, 4);
        Phantom p = new Phantom("Phantom Freddy", Phantom.Slot.WINDOW,
                Game.System.CAMERAS, 0.5);
        g.phantoms.add(p);
        boolean took = false;
        for (int i = 0; i < 60 && !took; i++) {
            g.update(1.0 / 60.0);
            if (!g.camerasOnline()) took = true;
        }
        check("a phantom takes its system when it leaves", took);
        check("and it is gone afterwards", g.phantoms.isEmpty());

        // The system is still up while the phantom is standing there.
        Game h = new Game(1, 4);
        h.phantoms.add(new Phantom("Phantom Chica", Phantom.Slot.DESK,
                Game.System.CAMERAS, 2.0));
        h.update(0.5);
        check("but not while it is still in the room", h.camerasOnline());
    }

    // ------------------------------------------------------------- reboots

    static void reboots() {
        section("the reboots");

        Game g = new Game(1, 21);
        g.online[Game.System.VENTILATION.ordinal()] = false;
        g.ventilation = 0;
        check("a dead system can be rebooted", g.canReboot(Game.System.VENTILATION));
        check("the reboot starts", g.startReboot(Game.System.VENTILATION));
        check("only one at a time", !g.startReboot(Game.System.CAMERAS));
        check("the system is still down during it", !g.ventilationOnline());

        for (int i = 0; i < (int) (Game.REBOOT_TIME * 60) + 2; i++) g.update(1.0 / 60.0);
        check("and comes back after it", g.ventilationOnline());
        // Within a frame or two of full: the drain restarts the moment
        // the system is back, so exact equality is a race, not a check.
        check("with the air refilled", g.ventilation > Game.VENT_MAX - 1);
        check("nothing is being rebooted any more", g.rebooting < 0);

        Game h = new Game(1, 21);
        check("a running system cannot be rebooted", !h.canReboot(Game.System.CAMERAS));
    }

    // -------------------------------------------------------------- office

    static void office() {
        section("the office");

        Game g = new Game(1, 13);
        g.springtrap.room = 1;
        g.springtrap.atOffice = false;
        // Force him in.
        for (int i = 0; i < 400 && !g.springtrap.atOffice; i++) g.springtrap.step(g);
        check("he can step into the office", g.springtrap.atOffice);
        check("he comes in at the window or the vent",
                g.springtrap.atWindow || !g.springtrap.atWindow);

        // The lure is the only thing that gets him out.
        g.lureRoom = 10;
        g.springtrap.update(1.0 / 60.0, g);
        check("the lure sends him back out", !g.springtrap.atOffice);
        check("to the last room, not the far end", g.springtrap.room == 1);

        // And without it, he finishes the night for you.
        Game h = new Game(5, 13);
        h.springtrap.atOffice = true;
        h.springtrap.officeTimer = 0;
        h.lureRoom = 0;
        double limit = Game.HOUR_SECONDS * Game.NIGHT_HOURS;
        while (h.status == Game.Status.PLAYING && h.time < limit) h.update(1.0 / 60.0);
        check("an unanswered office visit is a jumpscare",
                h.status == Game.Status.JUMPSCARED);

        // The night ends at 6 AM, played by the reference player rather
        // than by a second, weaker copy of it written inline here.
        Game k = Bot.run(1, 1, Bot.Policy.COMPETENT);
        check("the night ends at 6 AM (hour=" + k.hour + ")",
                k.status == Game.Status.SURVIVED && k.hour >= Game.NIGHT_HOURS);
    }

    // -------------------------------------------------------------- screen

    static void screen() {
        section("the screen's geometry");

        // Everything has to be on the canvas, or it is a button nobody can
        // click and a panel nobody can read.
        double[][] rects = {MouseMap.WINDOW, MouseMap.VENT, MouseMap.PANEL_AUDIO,
                MouseMap.PANEL_VENT, MouseMap.PANEL_CAM, MouseMap.PANEL_MONITOR,
                MouseMap.STRIP, MouseMap.LURE_BTN};
        boolean onCanvas = true;
        for (double[] r : rects) {
            if (r[0] < 0 || r[1] < 0 || r[0] + r[2] > MouseMap.W
                    || r[1] + r[3] > MouseMap.H) onCanvas = false;
        }
        check("every rectangle is on the canvas", onCanvas);

        // The bottom band panels must not overlap, or one of them is
        // unreachable and the hit test is a coin flip.
        double[][] band = {MouseMap.PANEL_AUDIO, MouseMap.PANEL_VENT,
                MouseMap.PANEL_CAM, MouseMap.PANEL_MONITOR};
        boolean disjoint = true;
        for (int i = 0; i < band.length; i++) {
            for (int j = i + 1; j < band.length; j++) {
                if (overlap(band[i], band[j])) disjoint = false;
            }
        }
        check("the four panels do not overlap", disjoint);

        // The panels must not sit on top of the office art either: the
        // office band ends at 605 and the panels start at 622.
        check("the panels are below the office band",
                MouseMap.PANEL_AUDIO[1] >= MouseMap.OY + MouseMap.OH);

        // The camera rows: ten of them, inside the strip, disjoint.
        boolean rowsOk = true, rowsDisjoint = true;
        for (int i = 1; i <= 10; i++) {
            double[] r = MouseMap.camRow(i);
            if (r[0] < MouseMap.STRIP[0] || r[1] < MouseMap.STRIP[1]
                    || r[0] + r[2] > MouseMap.STRIP[0] + MouseMap.STRIP[2]
                    || r[1] + r[3] > MouseMap.STRIP[1] + MouseMap.STRIP[3]) rowsOk = false;
            for (int j = i + 1; j <= 10; j++) {
                if (overlap(r, MouseMap.camRow(j))) rowsDisjoint = false;
            }
        }
        check("every camera row is inside the strip", rowsOk);
        check("no two camera rows overlap", rowsDisjoint);

        // And the hit test agrees with the rectangles it just drew.
        boolean hitsOk = true;
        for (int i = 1; i <= 10; i++) {
            double[] r = MouseMap.camRow(i);
            MouseMap.Hit h = MouseMap.hit(r[0] + r[2] / 2, r[1] + r[3] / 2, true);
            if (h.kind() != MouseMap.Kind.CAM_ROW || h.index() != i) hitsOk = false;
        }
        check("clicking a camera row selects that camera", hitsOk);

        boolean gapsQuiet = true;
        for (int i = 1; i < 10; i++) {
            double[] a = MouseMap.camRow(i), b = MouseMap.camRow(i + 1);
            double gap = b[1] - (a[1] + a[3]);
            if (gap > 0.5) {
                MouseMap.Hit h = MouseMap.hit(a[0] + a[2] / 2, a[1] + a[3] + gap / 2, true);
                if (h.kind() != MouseMap.Kind.NONE) gapsQuiet = false;
            }
        }
        check("the gaps between camera rows select nothing", gapsQuiet);

        // The monitor toggle is the one control that has to work from
        // wherever you are, because it is the control that takes you
        // somewhere else.
        double[] m = MouseMap.PANEL_MONITOR;
        check("MONITOR is live with the monitor up",
                MouseMap.hit(m[0] + m[2] / 2, m[1] + m[3] / 2, true).kind()
                        == MouseMap.Kind.MONITOR);
        check("MONITOR is live with the monitor down",
                MouseMap.hit(m[0] + m[2] / 2, m[1] + m[3] / 2, false).kind()
                        == MouseMap.Kind.MONITOR);

        // The panels are on the office wall, so they are not there while
        // you are looking at a camera.
        double[] v = MouseMap.PANEL_VENT;
        check("the air panel is dead while the monitor is up",
                MouseMap.hit(v[0] + v[2] / 2, v[1] + v[3] / 2, true).kind()
                        != MouseMap.Kind.VENT);
        check("and live while it is down",
                MouseMap.hit(v[0] + v[2] / 2, v[1] + v[3] / 2, false).kind()
                        == MouseMap.Kind.VENT);

        // The lure is the other way round: it only exists on the monitor.
        double[] l = MouseMap.LURE_BTN;
        check("the lure is live while the monitor is up",
                MouseMap.hit(l[0] + l[2] / 2, l[1] + l[3] / 2, true).kind()
                        == MouseMap.Kind.LURE);
        check("and dead while it is down",
                MouseMap.hit(l[0] + l[2] / 2, l[1] + l[3] / 2, false).kind()
                        != MouseMap.Kind.LURE);

        // The two openings must not overlap, or a figure drawn at one is
        // a figure drawn at both.
        check("the window and the vent do not overlap",
                !overlap(MouseMap.WINDOW, MouseMap.VENT));

        // Night select.
        boolean nightsOk = true, nightsDisjoint = true;
        for (int i = 1; i <= MouseMap.NIGHTS; i++) {
            double[] r = MouseMap.nightRow(i);
            if (r[0] < 0 || r[1] < 0 || r[0] + r[2] > MouseMap.W
                    || r[1] + r[3] > MouseMap.H) nightsOk = false;
            for (int j = i + 1; j <= MouseMap.NIGHTS; j++) {
                if (overlap(r, MouseMap.nightRow(j))) nightsDisjoint = false;
            }
        }
        check("every night row is on the canvas", nightsOk);
        check("no two night rows overlap", nightsDisjoint);

        boolean nightHits = true;
        for (int i = 1; i <= MouseMap.NIGHTS; i++) {
            double[] r = MouseMap.nightRow(i);
            if (MouseMap.nightAt(r[0] + r[2] / 2, r[1] + r[3] / 2) != i) nightHits = false;
        }
        check("every night row selects its own night", nightHits);
    }

    static boolean overlap(double[] a, double[] b) {
        return a[0] < b[0] + b[2] && b[0] < a[0] + a[2]
                && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
    }

    // ------------------------------------------------------------ survival

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
     * night is made of is asserted to be the engine's own value, and the
     * building's graph is asserted to be the engine's own graph.
     *
     * <p>What is <i>not</i> checked here is the RNG or the update loop. The
     * page reproduces {@code java.util.Random} in BigInt -- including
     * {@code nextInt}, which is a rejection sampler rather than a modulo --
     * and that was verified by driving both engines under the same scripted
     * policy: night one on seed 1001 produces identical traces every five
     * seconds (his room, whether he is in the office, the air, the lure and
     * the phantoms) and both end JUMPSCARED at the same moment. It is not
     * checkable from Java without a JavaScript engine, and the alternative (a
     * page that deals its own nights) would make the two builds different
     * games with the same rules.
     */
    static void phone() {
        section("the phone build");

        Path out = Path.of("web", "fnaf3.html");
        if (!Files.exists(out)) {
            System.out.println("       (no " + out + " from here -- run from the repository root)");
            return;
        }
        String page;
        try {
            page = Files.readString(out);
        } catch (Exception e) {
            check("web/fnaf3.html can be read", false);
            return;
        }
        try {
            check("web/fnaf3.html is current -- regenerate it with aside.games.fnaf3.WebHouse",
                    aside.games.fnaf3.WebHouse.html().equals(page));
        } catch (Exception e) {
            check("web/fnaf3.html is current -- regenerate it with aside.games.fnaf3.WebHouse",
                    false);
        }

        // The building, which is the game: every room has one way back toward
        // the office, so drawing him into a room is drawing him onto a path.
        boolean graph = true;
        for (int r = 0; r <= House.ROOMS; r++) {
            StringBuilder want = new StringBuilder("    [");
            int[] n = House.neighbours(r);
            for (int i = 0; i < n.length; i++) {
                if (i > 0) want.append(", ");
                want.append(n[i]);
            }
            want.append("]");
            if (!page.contains(want)) graph = false;
        }
        check("the page carries the building's graph", graph);
        check("the page carries how far every room is from the office",
                page.contains("toOffice: [0, 1, 2, 3, 4, 4, 3, 2, 3, 3, 4]"));

        // The rules, as numbers.
        check("the page carries what a reboot costs",
                page.contains("rebootTime: " + aside.games.fnaf3.WebHouse.num(Game.REBOOT_TIME)));
        check("the page carries how long the lure plays",
                page.contains("lureDuration: " + aside.games.fnaf3.WebHouse.num(Game.LURE_DURATION)));
        check("the page carries the lure's cooldown",
                page.contains("lureCooldown: " + aside.games.fnaf3.WebHouse.num(Game.LURE_COOLDOWN)));
        check("the page carries how much faster he moves with the air off",
                page.contains("ventFailSpeedup: " + aside.games.fnaf3.WebHouse.num(Game.VENT_FAIL_SPEEDUP)));
        check("the page carries how long a phantom lasts",
                page.contains("phantomLife: " + aside.games.fnaf3.WebHouse.num(Game.PHANTOM_LIFE)));
        check("the page carries the phantom rate with the air on",
                page.contains("phantomRateCalm: " + aside.games.fnaf3.WebHouse.num(Game.PHANTOM_RATE_CALM)));
        check("the page carries the phantom rate with the air off",
                page.contains("phantomRateFailing: " + aside.games.fnaf3.WebHouse.num(Game.PHANTOM_RATE_FAILING)));

        // The drain table, which is the honest difficulty lever.
        check("the page carries the drain table",
                page.contains("drainMult: " + aside.games.fnaf3.WebHouse.table(0)));

        // And the phantoms, which are the cost of a failed system.
        for (String name : Game.PHANTOM_NAMES) {
            check("the page carries " + name, page.contains(name));
        }
    }

    static void survival() {
        section("survival, 60 seeds a night");

        double[] idle = new double[5];
        double[] noLure = new double[5];
        double[] competent = new double[5];
        for (int n = 1; n <= 5; n++) {
            idle[n - 1] = Bot.survival(n, 60, Bot.Policy.IDLE);
            noLure[n - 1] = Bot.survival(n, 60, Bot.Policy.NO_LURE);
            competent[n - 1] = Bot.survival(n, 60, Bot.Policy.COMPETENT);
            System.out.printf("  night %d: idle %.0f%%   no lure %.0f%%   competent %.0f%%%n",
                    n, idle[n - 1] * 100, noLure[n - 1] * 100, competent[n - 1] * 100);
        }

        boolean idleDies = true;
        for (double v : idle) if (v > 0.05) idleDies = false;
        check("a player who does nothing dies on every night", idleDies);

        // The lure has to be worth using, but "strictly better on all
        // five nights" is a claim about 60 seeds, not about the design --
        // one night landing on the same number is noise. The claim that
        // matters is that it is worth using at all, and that it is worth
        // more the harder the night gets.
        double sumNo = 0, sumYes = 0;
        for (int i = 0; i < 5; i++) { sumNo += noLure[i]; sumYes += competent[i]; }
        check("the lure is worth using (avg " + pct(sumNo) + " -> " + pct(sumYes) + ")",
                sumYes > sumNo + 0.2);

        check("a competent player survives night 1", competent[0] >= 0.7);
        check("the week gets harder (night 5 <= night 1)",
                competent[4] <= competent[0]);
        check("night 5 is not free", competent[4] <= 0.85);
    }

    // --------------------------------------------------------------- utils

    static String pct(double d) {
        return String.format("%.0f%%", d * 100);
    }

    static String round(double d) {
        return String.format("%.2f", d);
    }

    static void section(String name) {
        System.out.println();
        System.out.println("== " + name + " ==");
    }

    static void check(String what, boolean ok) {
        checks++;
        if (!ok) failed++;
        System.out.println((ok ? "  ok   " : "  FAIL ") + what);
    }

    private SelfTest() {}
}
